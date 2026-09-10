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

/** analyze_expenses 的对比与预测结果（全部端侧确定性计算）。 */
data class PeriodTotals(val label: String, val totalCents: Long, val count: Int)

data class CategoryTrend(
    val categoryId: String,
    val currentCents: Long,
    val previousCents: Long,
) {
    val deltaCents: Long get() = currentCents - previousCents

    /** 环比百分比；上月为 0 时返回 null（防 +∞%，由调用方改写为"新增支出"）。 */
    fun percentLabel(): String? = when {
        previousCents <= 0L -> null
        currentCents <= 0L -> "-100%"
        else -> "%+.0f%%".format((currentCents - previousCents) * 100.0 / previousCents)
    }
}

data class MonthEndForecast(
    val projectedCents: Long,
    /** 外推依据，如 "daily_average"；LLM 必须把依据转述给用户。 */
    val basis: String,
    val elapsedDays: Int,
    val totalDays: Int,
)

data class AnalyzeToolResult(
    val current: PeriodTotals,
    val previous: PeriodTotals?,
    /** 非投资类趋势，按 |环比变化| 降序。 */
    val trends: List<CategoryTrend>,
    val forecast: MonthEndForecast?,
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

    /**
     * analyze_expenses(spec)：本期 vs 上期 + 分类趋势 + 月底 pace 预测。
     * 预测门槛：本期覆盖"现在"且已过 7 天（样本不足时宁可不预测，见 InsightPolicy 同款约束）。
     */
    suspend fun analyzeExpenses(
        context: AgentToolContext,
        spec: AgentPeriodSpec,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): AnalyzeToolResult {
        val currentRows = context.activeInRange(spec.fromMillis, spec.toMillis)
        val currentTotals = totalsOf(spec.label, currentRows)
        val previousSpec = AgentPeriodResolver.previousOf(spec, zone)
        val previousRows = context.activeInRange(previousSpec.fromMillis, previousSpec.toMillis)
        val previousTotals = totalsOf(previousSpec.label, previousRows)

        val currentByCategory = currentRows
            .filterNot { Category.byIdOrOther(it.categoryId).isInvestment }
            .groupBy { it.categoryId }
            .mapValues { (_, items) -> items.sumOf { it.amountCents } }
        val previousByCategory = previousRows
            .filterNot { Category.byIdOrOther(it.categoryId).isInvestment }
            .groupBy { it.categoryId }
            .mapValues { (_, items) -> items.sumOf { it.amountCents } }
        // 趋势集合 = 本期 ∪ 上期：上期有、本期清零的分类也必须出现，才能显示"已清零"。
        val trends = (currentByCategory.keys + previousByCategory.keys)
            .map { id ->
                CategoryTrend(
                    categoryId = id,
                    currentCents = currentByCategory[id] ?: 0L,
                    previousCents = previousByCategory[id] ?: 0L,
                )
            }
            .filter { it.currentCents > 0L || it.previousCents > 0L }
            .sortedByDescending { kotlin.math.abs(it.deltaCents) }
            .take(MAX_TREND_ROWS)

        // 预测与消费合计口径一致：投资类不计入（见 totalsOf / queryExpenses）。
        val forecast = forecastOf(spec, currentTotals.totalCents, nowMillis, zone)
        return AnalyzeToolResult(
            current = currentTotals,
            previous = previousTotals.takeIf { previousTotals.count > 0 },
            trends = trends,
            forecast = forecast,
        )
    }

    private fun totalsOf(label: String, rows: List<com.expense.tracker.data.db.ExpenseEntity>): PeriodTotals =
        PeriodTotals(
            label = label,
            totalCents = rows.filterNot { Category.byIdOrOther(it.categoryId).isInvestment }.sumOf { it.amountCents },
            count = rows.size,
        )

    private fun forecastOf(
        spec: AgentPeriodSpec,
        currentCents: Long,
        nowMillis: Long,
        zone: ZoneId,
    ): MonthEndForecast? {
        val lenDays = ((spec.toMillis - spec.fromMillis) / DAY_MS).toInt()
        if (lenDays < 25 || lenDays > 31) return null // 只对整月做预测
        val elapsedDays = (((nowMillis - spec.fromMillis) / DAY_MS).toInt() + 1)
        if (nowMillis !in spec.fromMillis until spec.toMillis || elapsedDays < MIN_FORECAST_DAYS) return null
        if (currentCents <= 0L) return null
        val dailyAvg = currentCents.toDouble() / elapsedDays
        return MonthEndForecast(
            projectedCents = (dailyAvg * lenDays).toLong(),
            basis = "daily_average",
            elapsedDays = elapsedDays,
            totalDays = lenDays,
        )
    }

    const val MAX_TREND_ROWS = 6
    const val MIN_FORECAST_DAYS = 7
    private const val DAY_MS = 86_400_000L
}
