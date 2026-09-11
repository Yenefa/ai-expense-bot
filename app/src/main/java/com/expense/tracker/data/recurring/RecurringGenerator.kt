package com.expense.tracker.data.recurring

import com.expense.tracker.data.db.RecurringPeriodType
import com.expense.tracker.data.db.RecurringRuleEntity
import com.expense.tracker.data.repo.ExpenseRepository
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 周期账单生成：到期规则自动生成账目并推进下一周期。
 * 防重复：生成后立即推进 nextDueAt；长时间未打开只补最近一期，历史过期期次跳过。
 * 并发安全：runOnce 全程持有进程级互斥锁，App 启动协程与每日提醒 Worker 不会重复生成。
 */
object RecurringGenerator {

    private const val MAX_ADVANCE_STEPS = 366

    /** 进程级互斥锁：把“读到到期规则 → 生成账目 → 推进到期点”整段串行化，消除并发重复入账。 */
    private val runMutex = Mutex()

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

    /** 严格晚于 date 的下一个周期点，完全由规则字段推导，保证月底钳位（31→2/28）后不漂移。 */
    private fun advance(period: RecurringPeriodType, date: LocalDate, rule: RecurringRuleEntity): LocalDate =
        when (period) {
            RecurringPeriodType.DAILY -> date.plusDays(1)
            RecurringPeriodType.WEEKLY -> {
                val target = rule.dayOfWeek.coerceIn(1, 7)
                var diff = (target - date.dayOfWeek.value + 7) % 7
                if (diff == 0) diff = 7
                date.plusDays(diff.toLong())
            }
            RecurringPeriodType.MONTHLY -> {
                // 本月目标日（超出当月天数取月末）；不晚于基准日才滚到下个月并重新钳位。
                // 例：dayOfMonth=31 时 1/31 → 2/28 → 3/31，而不是固定在 28 号。
                val thisCycle = targetDate(date.year, date.monthValue, rule.dayOfMonth)
                if (thisCycle > date) {
                    thisCycle
                } else {
                    val nextMonth = date.plusMonths(1)
                    targetDate(nextMonth.year, nextMonth.monthValue, rule.dayOfMonth)
                }
            }
            RecurringPeriodType.YEARLY -> {
                // 今年的目标月日仍晚于基准日时用今年，否则才滚到下一年（闰日同理钳位）。
                val thisYear = targetDate(date.year, rule.monthOfYear, rule.dayOfMonth)
                if (thisYear > date) thisYear else targetDate(date.year + 1, rule.monthOfYear, rule.dayOfMonth)
            }
        }

    /** 指定年月的目标日：day 超出当月天数时取当月最后一天（如平年 2 月的 29/30/31 日 → 2/28）。 */
    private fun targetDate(year: Int, month: Int, day: Int): LocalDate {
        val safeMonth = month.coerceIn(1, 12)
        val safeDay = day.coerceIn(1, 31).coerceAtMost(YearMonth.of(year, safeMonth).lengthOfMonth())
        return LocalDate.of(year, safeMonth, safeDay)
    }

    /** 执行一次到期检查：生成所有到期规则的最新一期账目并推进。返回生成笔数。 */
    suspend fun runOnce(
        ruleDao: com.expense.tracker.data.db.RecurringRuleDao,
        expenseRepository: ExpenseRepository,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int = runMutex.withLock {
        val dueRules = ruleDao.getEnabledDue(nowMillis)
        var generated = 0
        dueRules.forEach { rule ->
            // 单条规则失败（金额非法、写库异常等）不能中断整批，跳过并继续处理其余规则
            runCatching {
                expenseRepository.addCents(
                    amountCents = rule.amountCents,
                    categoryId = rule.categoryId,
                    note = rule.note,
                    occurredAt = rule.nextDueAt,
                )
                ruleDao.update(rule.copy(nextDueAt = nextDueAfter(rule, rule.nextDueAt, nowMillis, zone)))
            }.onSuccess { generated++ }
        }
        generated
    }
}
