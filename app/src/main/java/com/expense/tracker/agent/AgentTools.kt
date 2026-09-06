package com.expense.tracker.agent

import com.expense.tracker.data.budget.BudgetCalculator
import com.expense.tracker.data.budget.BudgetSnapshot
import com.expense.tracker.data.model.Category
import com.expense.tracker.data.model.Money
import com.expense.tracker.data.repo.ExpenseRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first

/** Agent 工具的数据依赖：仓库 + 预算快照提供者（DataStore 在 JVM 测试里不可用，用 lambda 注入）。 */
class AgentToolContext(
    private val expenseRepository: ExpenseRepository,
    private val budgetSnapshotProvider: suspend () -> BudgetSnapshot,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun activeInRange(fromMillis: Long, toMillis: Long) =
        expenseRepository.observeInRange(fromMillis, toMillis).first().filter { it.deletedAt == null }

    suspend fun budgetSnapshot(): BudgetSnapshot = budgetSnapshotProvider()
}

data class CategoryAgg(val categoryId: String, val count: Int, val amountCents: Long)

data class NoteAgg(val note: String, val count: Int, val amountCents: Long)

data class RecordLine(
    val id: Long,
    val occurredAt: Long,
    val categoryId: String,
    val amountCents: Long,
    val note: String,
) {
    fun render(zone: ZoneId): String {
        val ts = DateTimeFormatter.ofPattern("MM-dd HH:mm")
            .format(Instant.ofEpochMilli(occurredAt).atZone(zone))
        val cat = Category.byIdOrOther(categoryId)
        return "$id|$ts|${cat.emoji}${cat.displayName}|¥${Money.formatYuan(amountCents)}|${note.ifBlank { "-" }}"
    }
}

/** query_expenses 工具结果：合计 + 分类聚合 + 商户聚合 + 明细行。 */
data class QueryToolResult(
    val periodLabel: String,
    val filteredCategories: Set<String>,
    /** 消费合计（不含投资类，与分析页口径一致）。 */
    val totalCents: Long,
    val totalCount: Int,
    /** 含投资类在内的分类聚合，金额降序。 */
    val byCategory: List<CategoryAgg>,
    val investmentCents: Long,
    val topNotes: List<NoteAgg>,
    val records: List<RecordLine>,
    val recordsTruncated: Boolean,
)

/** get_budget_status 工具结果。 */
data class BudgetToolResult(
    val monthly: com.expense.tracker.data.budget.BudgetEntry?,
    val categories: List<com.expense.tracker.data.budget.BudgetEntry>,
    val daysLeftInMonth: Int,
)

/**
 * Agent 本地工具集：查询与统计在端侧确定性完成，LLM 只负责把结果讲成人话，
 * 数字不可能被模型编造（对齐 privacy-first 定位）。
 */
object AgentTools {

    const val MAX_RECORD_LINES = 60
    const val MAX_NOTE_AGGS = 5

    /** query_expenses(spec, categories)：区间内记录 + 聚合。 */
    suspend fun queryExpenses(
        context: AgentToolContext,
        spec: AgentPeriodSpec,
        categories: Set<String>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): QueryToolResult {
        val rows = context.activeInRange(spec.fromMillis, spec.toMillis)
        val filtered = if (categories.isEmpty()) rows else rows.filter { it.categoryId in categories }
        val investmentCents = filtered
            .filter { Category.byIdOrOther(it.categoryId).isInvestment }
            .sumOf { it.amountCents }
        val consumptionCents = filtered
            .filterNot { Category.byIdOrOther(it.categoryId).isInvestment }
            .sumOf { it.amountCents }
        val byCategory = filtered
            .groupBy { it.categoryId }
            .map { (id, items) ->
                CategoryAgg(id, items.size, items.sumOf { it.amountCents })
            }
            .sortedByDescending { it.amountCents }
        val topNotes = filtered
            .filter { it.note.isNotBlank() }
            .groupBy { it.note.trim() }
            .map { (note, items) -> NoteAgg(note, items.size, items.sumOf { it.amountCents }) }
            .sortedByDescending { it.amountCents }
            .take(MAX_NOTE_AGGS)
        val sorted = filtered.sortedByDescending { it.occurredAt }
        return QueryToolResult(
            periodLabel = spec.label,
            filteredCategories = categories,
            totalCents = consumptionCents,
            totalCount = filtered.size,
            byCategory = byCategory,
            investmentCents = investmentCents,
            topNotes = topNotes,
            records = sorted.take(MAX_RECORD_LINES).map {
                RecordLine(it.id, it.occurredAt, it.categoryId, it.amountCents, it.note)
            },
            recordsTruncated = sorted.size > MAX_RECORD_LINES,
        )
    }

    /** get_budget_status()：本月预算 vs 实际支出。未设置任何预算返回 null。 */
    suspend fun budgetStatus(
        context: AgentToolContext,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): BudgetToolResult? {
        val snapshot = context.budgetSnapshot()
        if (snapshot.monthlyLimitCents <= 0L && snapshot.categoryLimitsCents.isEmpty()) return null
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val first = today.withDayOfMonth(1)
        val monthSpec = AgentPeriodSpec(
            label = "本月",
            fromMillis = first.atStartOfDay(zone).toInstant().toEpochMilli(),
            toMillis = first.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
        val spentByCategory = context.activeInRange(monthSpec.fromMillis, monthSpec.toMillis)
            .groupBy { it.categoryId }
            .mapValues { (_, items) -> items.sumOf { it.amountCents } }
        val overview = BudgetCalculator.overview(
            monthlyLimitCents = snapshot.monthlyLimitCents,
            categoryLimitsCents = snapshot.categoryLimitsCents,
            monthlySpentByCategory = spentByCategory,
        )
        return BudgetToolResult(
            monthly = overview.monthly,
            categories = overview.categories,
            daysLeftInMonth = today.lengthOfMonth() - today.dayOfMonth + 1,
        )
    }
}
