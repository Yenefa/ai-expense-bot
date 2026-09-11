package com.expense.tracker.proactive

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.ZoneId

class ProactiveRulesTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun inputs(
        limit: Long = 200_000L,
        spent: Long = 0L,
        records: Int = 10,
        elapsedDays: Int = 15,
        daysInMonth: Int = 30,
        currentWeek: Long = 0L,
        weeks: List<Long> = listOf(0L, 0L, 0L, 0L),
        income: Long? = null,
        savings: Long? = null,
    ) = ProactiveInputs(
        nowMillis = 1_786_000_000_000L,
        zone = zone,
        monthlyLimitCents = limit,
        monthSpentCents = spent,
        monthRecordCount = records,
        elapsedMonthDays = elapsedDays,
        daysInMonth = daysInMonth,
        currentWeekSpentCents = currentWeek,
        completedWeekSpendsCents = weeks,
        monthlyIncomeCents = income,
        savingsGoalCents = savings,
    )

    @Test
    fun `预算90%与超支分别告警`() {
        val warn = ProactiveRules.evaluate(inputs(spent = 185_000L))!!
        assertThat(warn.type).isEqualTo(ProactiveAlertType.BUDGET_THRESHOLD)
        assertThat(warn.severity).isEqualTo(ProactiveSeverity.WARN)

        val over = ProactiveRules.evaluate(inputs(spent = 210_000L))!!
        assertThat(over.severity).isEqualTo(ProactiveSeverity.OVER)
        assertThat(over.deterministicCopy).contains("超支")
    }

    @Test
    fun `预算冷启动不提醒`() {
        assertThat(ProactiveRules.evaluate(inputs(limit = 0L, spent = 300_000L))).isNull()
        assertThat(ProactiveRules.evaluate(inputs(spent = 300_000L, records = 3))).isNull()
        assertThat(ProactiveRules.evaluate(inputs(spent = 100_000L))).isNull()
    }

    @Test
    fun `异常消费需要4个可比样本且达到阈值`() {
        val weeks = listOf(50_000L, 60_000L, 40_000L, 50_000L)
        // baseline=50000, 阈值=75000/差值>=10000
        val alert = ProactiveRules.evaluate(inputs(limit = 0L, currentWeek = 90_000L, weeks = weeks))!!
        assertThat(alert.type).isEqualTo(ProactiveAlertType.ANOMALOUS_SPENDING)
        assertThat(alert.deterministicCopy).contains("近 4 周平均")

        // 只有 3 个样本 → 冷启动，不提醒
        assertThat(ProactiveRules.evaluate(inputs(limit = 0L, currentWeek = 90_000L, weeks = listOf(50_000L, 60_000L, 40_000L, 0L)))).isNull()
        // 差值不足 ¥100（baseline=50000，当前 70000 → 1.4x 未达 1.5x）
        assertThat(ProactiveRules.evaluate(inputs(limit = 0L, currentWeek = 70_000L, weeks = weeks))).isNull()
    }

    @Test
    fun `储蓄偏离结合收入储蓄与本月节奏`() {
        // income 8000, 已过15天花6500 → 预计 13000 → 结余 -5000 → OVER
        val over = ProactiveRules.evaluate(inputs(limit = 0L, spent = 650_000L, income = 800_000L, savings = 200_000L))!!
        assertThat(over.type).isEqualTo(ProactiveAlertType.SAVINGS_GOAL_DEVIATION)
        assertThat(over.severity).isEqualTo(ProactiveSeverity.OVER)

        // income 8000, 已过15天花3000 → 预计 6000 → 结余 2000 = goal → 不提醒
        assertThat(ProactiveRules.evaluate(inputs(limit = 0L, spent = 300_000L, income = 800_000L, savings = 200_000L))).isNull()

        // 缺少记忆 → 不提醒
        assertThat(ProactiveRules.evaluate(inputs(limit = 0L, spent = 650_000L))).isNull()
    }

    @Test
    fun `同时触发时按优先级只出一条`() {
        val alert = ProactiveRules.evaluate(
            inputs(spent = 210_000L, currentWeek = 500_000L, weeks = listOf(10_000L, 10_000L, 10_000L, 10_000L), income = 800_000L, savings = 200_000L),
        )!!
        assertThat(alert.type).isEqualTo(ProactiveAlertType.BUDGET_THRESHOLD)
    }
}
