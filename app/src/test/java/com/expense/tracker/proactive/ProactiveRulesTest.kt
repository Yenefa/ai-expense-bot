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
        categoryWeeks: List<CategoryWeeklySpending> = emptyList(),
        merchantWeeks: List<MerchantWeeklySpending> = emptyList(),
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
        categoryWeekSpends = categoryWeeks,
        merchantWeekSpends = merchantWeeks,
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
    fun `储蓄规则复用共享节奏计算并输出缺口事实`() {
        val alert = ProactiveRules.evaluate(
            inputs(limit = 0L, spent = 650_000L, income = 800_000L, savings = 200_000L),
        )!!
        assertThat(alert.facts["projected_spend_cents"]).isEqualTo(1_300_000L)
        assertThat(alert.facts["projected_leftover_cents"]).isEqualTo(-500_000L)
        assertThat(alert.facts["goal_gap_cents"]).isEqualTo(-700_000L)
        assertThat(alert.facts["remaining_days"]).isEqualTo(15L)
        // 已花 6500 > 收入 8000 - 目标 2000 → 剩余可支配为负，日均下限 0
        assertThat(alert.facts["remaining_spendable_cents"]).isEqualTo(-50_000L)
        assertThat(alert.facts["remaining_daily_cents"]).isEqualTo(0L)
    }

    @Test
    fun `总消费正常但单分类异常时按分类提醒`() {
        // 近 4 周：餐饮 50k/周 + 住房 200k/周；本周餐饮 90k、住房 200k
        // 总消费 250k → 290k（1.16x，不达 1.5x）；餐饮 50k → 90k（1.8x 且 +40）→ 分类级命中
        val alert = ProactiveRules.evaluate(
            inputs(
                limit = 0L,
                currentWeek = 290_000L,
                weeks = listOf(250_000L, 250_000L, 250_000L, 250_000L),
                categoryWeeks = listOf(
                    CategoryWeeklySpending("food", 90_000L, listOf(50_000L, 50_000L, 50_000L, 50_000L)),
                    CategoryWeeklySpending("housing", 200_000L, listOf(200_000L, 200_000L, 200_000L, 200_000L)),
                ),
            ),
        )!!
        assertThat(alert.type).isEqualTo(ProactiveAlertType.ANOMALOUS_SPENDING)
        assertThat(alert.deterministicCopy).contains("餐饮")
        assertThat(alert.facts["delta_cents"]).isEqualTo(40_000L)
        assertThat(alert.facts["baseline_cents"]).isEqualTo(50_000L)
    }

    @Test
    fun `分类异常同样受样本与阈值约束`() {
        // 只有 1 个非零样本 → 冷启动
        assertThat(
            ProactiveRules.evaluate(
                inputs(
                    limit = 0L,
                    currentWeek = 90_000L,
                    weeks = listOf(0L, 0L, 0L, 0L),
                    categoryWeeks = listOf(
                        CategoryWeeklySpending("food", 90_000L, listOf(50_000L, 0L, 0L, 0L)),
                    ),
                ),
            ),
        ).isNull()
        // 分类幅度不足 1.5x（70k vs 50k）
        assertThat(
            ProactiveRules.evaluate(
                inputs(
                    limit = 0L,
                    currentWeek = 70_000L,
                    weeks = listOf(0L, 0L, 0L, 0L),
                    categoryWeeks = listOf(
                        CategoryWeeklySpending("food", 70_000L, listOf(50_000L, 50_000L, 50_000L, 50_000L)),
                    ),
                ),
            ),
        ).isNull()
    }

    @Test
    fun `储蓄未达标文案包含缺口与剩余天数`() {
        val alert = ProactiveRules.evaluate(
            inputs(limit = 0L, spent = 300_000L, income = 800_000L, savings = 500_000L),
        )!!
        assertThat(alert.severity).isEqualTo(ProactiveSeverity.WARN)
        assertThat(alert.deterministicCopy).contains("还差 ¥3000.00")
        assertThat(alert.deterministicCopy).contains("剩余 15 天")
        // 已花 3000 > 收入 8000 - 目标 5000 → 静态剩余额度为 0，提示"最多还能花 ¥0.00"
        assertThat(alert.deterministicCopy).contains("最多还能花 ¥0.00（日均 ¥0.00）")
    }

    @Test
    fun `同时触发时按优先级只出一条`() {
        val alert = ProactiveRules.evaluate(
            inputs(spent = 210_000L, currentWeek = 500_000L, weeks = listOf(10_000L, 10_000L, 10_000L, 10_000L), income = 800_000L, savings = 200_000L),
        )!!
        assertThat(alert.type).isEqualTo(ProactiveAlertType.BUDGET_THRESHOLD)
    }

    @Test
    fun `总量与分类均正常时按商户基线发现异常`() {
        // 总量 250k → 290k（1.16x，不达 1.5x）；分类各项持平；单一商户 10k → 60k（6x 且 +50k）→ 商户级命中
        val alert = ProactiveRules.evaluate(
            inputs(
                limit = 0L,
                currentWeek = 290_000L,
                weeks = listOf(250_000L, 250_000L, 250_000L, 250_000L),
                categoryWeeks = listOf(
                    CategoryWeeklySpending("food", 90_000L, listOf(90_000L, 90_000L, 90_000L, 90_000L)),
                    CategoryWeeklySpending("housing", 200_000L, listOf(200_000L, 200_000L, 200_000L, 200_000L)),
                ),
                merchantWeeks = listOf(
                    MerchantWeeklySpending("星巴克", 60_000L, listOf(10_000L, 10_000L, 10_000L, 10_000L)),
                ),
            ),
        )!!
        assertThat(alert.type).isEqualTo(ProactiveAlertType.ANOMALOUS_SPENDING)
        assertThat(alert.deterministicCopy).contains("星巴克")
        assertThat(alert.facts["current_week_cents"]).isEqualTo(60_000L)
        assertThat(alert.facts["baseline_cents"]).isEqualTo(10_000L)
        assertThat(alert.facts["delta_cents"]).isEqualTo(50_000L)
    }

    @Test
    fun `分类异常优先于商户异常`() {
        // 餐饮 50k → 90k 已达分类级；同一轮商户 10k → 300k 偏离更大，仍应报分类级（层级：总量 > 分类 > 商户）
        val alert = ProactiveRules.evaluate(
            inputs(
                limit = 0L,
                currentWeek = 500_000L,
                weeks = listOf(500_000L, 500_000L, 500_000L, 500_000L),
                categoryWeeks = listOf(
                    CategoryWeeklySpending("food", 90_000L, listOf(50_000L, 50_000L, 50_000L, 50_000L)),
                ),
                merchantWeeks = listOf(
                    MerchantWeeklySpending("星巴克", 300_000L, listOf(10_000L, 10_000L, 10_000L, 10_000L)),
                ),
            ),
        )!!
        assertThat(alert.deterministicCopy).contains("餐饮")
        assertThat(alert.deterministicCopy).doesNotContain("星巴克")
        assertThat(alert.facts["delta_cents"]).isEqualTo(40_000L)
    }

    @Test
    fun `商户异常同样受样本与阈值约束`() {
        // 只有 3 个非零样本 → 冷启动
        assertThat(
            ProactiveRules.evaluate(
                inputs(
                    limit = 0L,
                    currentWeek = 60_000L,
                    weeks = listOf(0L, 0L, 0L, 0L),
                    merchantWeeks = listOf(
                        MerchantWeeklySpending("星巴克", 60_000L, listOf(10_000L, 10_000L, 10_000L, 0L)),
                    ),
                ),
            ),
        ).isNull()
        // 幅度不足 1.5x（14k vs 10k）
        assertThat(
            ProactiveRules.evaluate(
                inputs(
                    limit = 0L,
                    currentWeek = 14_000L,
                    weeks = listOf(0L, 0L, 0L, 0L),
                    merchantWeeks = listOf(
                        MerchantWeeklySpending("星巴克", 14_000L, listOf(10_000L, 10_000L, 10_000L, 10_000L)),
                    ),
                ),
            ),
        ).isNull()
    }
}
