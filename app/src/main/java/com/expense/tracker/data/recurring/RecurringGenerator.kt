package com.expense.tracker.data.recurring

import com.expense.tracker.data.db.RecurringPeriodType
import com.expense.tracker.data.db.RecurringRuleEntity
import com.expense.tracker.data.repo.ExpenseRepository
import java.time.LocalDate
import java.time.ZoneId

/**
 * 周期账单生成：到期规则自动生成账目并推进下一周期。
 * 防重复：生成后立即推进 nextDueAt；长时间未打开只补最近一期，历史过期期次跳过。
 */
object RecurringGenerator {

    private const val MAX_ADVANCE_STEPS = 366

    /** 纯逻辑：规则的下一次到期点。当前到期日尚未到 nowMillis 时原样返回；已过期则推进到最近未来周期点。 */
    fun nextDueAfter(
        rule: RecurringRuleEntity,
        currentDueMillis: Long,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val current = java.time.Instant.ofEpochMilli(currentDueMillis).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()
        if (current > nowMillis) return current

        val period = rule.period
        var due = java.time.Instant.ofEpochMilli(currentDueMillis).atZone(zone).toLocalDate()
        var guard = 0
        while (guard < MAX_ADVANCE_STEPS) {
            due = advance(period, due, rule)
            if (due.atStartOfDay(zone).toInstant().toEpochMilli() > nowMillis) {
                return due.atStartOfDay(zone).toInstant().toEpochMilli()
            }
            guard++
        }
        // 极端情况下回退到可计算的最远安全点（兜底，防止死循环）
        return due.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    private fun advance(period: RecurringPeriodType, date: LocalDate, rule: RecurringRuleEntity): LocalDate =        when (period) {
            RecurringPeriodType.DAILY -> date.plusDays(1)
            RecurringPeriodType.WEEKLY -> {
                val target = rule.dayOfWeek.coerceIn(1, 7)
                var diff = (target - date.dayOfWeek.value + 7) % 7
                if (diff == 0) diff = 7
                date.plusDays(diff.toLong())
            }
            RecurringPeriodType.MONTHLY -> {
                val next = date.plusMonths(1)
                next.withDayOfMonth(rule.dayOfMonth.coerceIn(1, 31).coerceAtMost(next.lengthOfMonth()))
            }
            RecurringPeriodType.YEARLY -> {
                val next = date.plusYears(1)
                val month = rule.monthOfYear.coerceIn(1, 12)
                val withMonth = next.withMonth(month)
                withMonth.withDayOfMonth(rule.dayOfMonth.coerceIn(1, 31).coerceAtMost(withMonth.lengthOfMonth()))
            }
        }

    /** 执行一次到期检查：生成所有到期规则的最新一期账目并推进。返回生成笔数。 */
    suspend fun runOnce(
        ruleDao: com.expense.tracker.data.db.RecurringRuleDao,
        expenseRepository: ExpenseRepository,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int {
        val dueRules = ruleDao.getEnabledDue(nowMillis)
        var generated = 0
        dueRules.forEach { rule ->
            expenseRepository.addCents(
                amountCents = rule.amountCents,
                categoryId = rule.categoryId,
                note = rule.note,
                occurredAt = rule.nextDueAt,
            )
            ruleDao.update(rule.copy(nextDueAt = nextDueAfter(rule, rule.nextDueAt, nowMillis, zone)))
            generated++
        }
        return generated
    }
}
