package com.expense.tracker.proactive

import com.expense.tracker.data.budget.BudgetSnapshot
import com.expense.tracker.data.model.Category
import com.expense.tracker.data.repo.ExpenseRepository
import com.expense.tracker.memory.MemoryFact
import com.expense.tracker.memory.MemoryReadPolicy
import com.expense.tracker.memory.MemoryReadScope
import com.expense.tracker.memory.MemoryType
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.first

/**
 * 主动提醒的输入构建 + 治理评估（前台展示与后台通知共用同一实现）。
 *
 * 只读数据源：
 * - 账目（本月至今日 / 本周 / 近 4 个完整周，均排除投资类）；
 * - 预算快照；
 * - 已授权的财务记忆（FINANCIAL_ANALYSIS：月收入 / 储蓄目标）。
 *
 * 是否提醒仍由 [ProactiveGovernor] 决定；本类不做任何投递。
 */
class ProactiveEngine(
    private val expenseRepository: ExpenseRepository,
    private val budgetSnapshot: suspend () -> BudgetSnapshot,
    private val memoryFacts: suspend () -> List<MemoryFact>,
    private val governor: ProactiveGovernor,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val nowProvider: () -> Long = System::currentTimeMillis,
) {

    suspend fun evaluate(): ProactiveAlert? {
        val now = nowProvider()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val monthStart = today.withDayOfMonth(1)
        val monthEnd = monthStart.plusMonths(1)
        // 月度窗口上界 = 下月初：未来日期的账目（JSON 恢复/CSV 导入可带入）不得计入本月。
        val monthRecords = activeConsumption(
            monthStart.atStartOfDay(zone).toInstant().toEpochMilli(),
            monthEnd.atStartOfDay(zone).toInstant().toEpochMilli(),
        )
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val currentWeekRows = activeConsumption(
            weekStart.atStartOfDay(zone).toInstant().toEpochMilli(),
            today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
        val completedWeekRows = (4 downTo 1).map { back ->
            val start = weekStart.minusWeeks(back.toLong())
            activeConsumption(
                start.atStartOfDay(zone).toInstant().toEpochMilli(),
                start.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            )
        }
        val currentByCategory = currentWeekRows.groupBy { it.categoryId }
            .mapValues { (_, rows) -> rows.sumOf { it.amountCents } }
        val completedByCategory = completedWeekRows.map { rows ->
            rows.groupBy { it.categoryId }.mapValues { (_, items) -> items.sumOf { it.amountCents } }
        }
        // 分类级基线：总消费被其他分类抵消时仍能发现单一分类异常。
        val categoryWeekSpends = (currentByCategory.keys + completedByCategory.flatMap { it.keys })
            .map { id ->
                CategoryWeeklySpending(
                    categoryId = id,
                    currentWeekCents = currentByCategory[id] ?: 0L,
                    completedWeeksCents = completedByCategory.map { it[id] ?: 0L },
                )
            }
        // 商户级基线：总量与分类都被摊平时，仍能发现单一商户（note）异常。
        val currentByNote = currentWeekRows.filter { it.note.isNotBlank() }
            .groupBy { it.note.trim() }
            .mapValues { (_, rows) -> rows.sumOf { it.amountCents } }
        val completedByNote = completedWeekRows.map { rows ->
            rows.filter { it.note.isNotBlank() }
                .groupBy { it.note.trim() }
                .mapValues { (_, items) -> items.sumOf { it.amountCents } }
        }
        val merchantWeekSpends = (currentByNote.keys + completedByNote.flatMap { it.keys })
            .map { note ->
                MerchantWeeklySpending(
                    note = note,
                    currentWeekCents = currentByNote[note] ?: 0L,
                    completedWeeksCents = completedByNote.map { it[note] ?: 0L },
                )
            }
        val currentWeekSpent = currentWeekRows.sumOf { it.amountCents }
        val completedWeeks = completedWeekRows.map { rows -> rows.sumOf { it.amountCents } }
        val facts = MemoryReadPolicy.filter(MemoryReadScope.FINANCIAL_ANALYSIS, memoryFacts())
        val inputs = ProactiveInputs(
            nowMillis = now,
            zone = zone,
            monthlyLimitCents = budgetSnapshot().monthlyLimitCents,
            monthSpentCents = monthRecords.sumOf { it.amountCents },
            monthRecordCount = monthRecords.size,
            elapsedMonthDays = today.dayOfMonth,
            daysInMonth = today.lengthOfMonth(),
            currentWeekSpentCents = currentWeekSpent,
            completedWeekSpendsCents = completedWeeks,
            categoryWeekSpends = categoryWeekSpends,
            merchantWeekSpends = merchantWeekSpends,
            monthlyIncomeCents = facts.firstOrNull { it.type == MemoryType.MONTHLY_INCOME }?.amountCents,
            savingsGoalCents = facts.firstOrNull { it.type == MemoryType.SAVINGS_GOAL }?.amountCents,
        )
        return governor.evaluate(inputs)
    }

    private suspend fun activeConsumption(fromMillis: Long, toMillis: Long) =
        expenseRepository.observeInRange(fromMillis, toMillis).first()
            .filter { it.deletedAt == null && !Category.byIdOrOther(it.categoryId).isInvestment }
}
