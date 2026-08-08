package com.expense.tracker.data.budget

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BudgetCalculatorTest {

    @Test
    fun noBudgetMeansNoWarning() {
        val overview = BudgetCalculator.overview(0L, emptyMap(), mapOf("food" to 5_000L))
        assertThat(overview.monthly).isNull()
        assertThat(overview.categories).isEmpty()
        assertThat(overview.worstStatus).isNull()
    }

    @Test
    fun monthlyStatusThresholds() {
        assertThat(BudgetCalculator.statusOf(89_999L, 100_000L)).isEqualTo(BudgetStatus.OK)
        assertThat(BudgetCalculator.statusOf(90_000L, 100_000L)).isEqualTo(BudgetStatus.WARN)
        assertThat(BudgetCalculator.statusOf(99_999L, 100_000L)).isEqualTo(BudgetStatus.WARN)
        assertThat(BudgetCalculator.statusOf(100_000L, 100_000L)).isEqualTo(BudgetStatus.OVER)
        assertThat(BudgetCalculator.statusOf(120_000L, 100_000L)).isEqualTo(BudgetStatus.OVER)
    }

    @Test
    fun overviewCombinesMonthlyAndCategories() {
        val overview = BudgetCalculator.overview(
            monthlyLimitCents = 100_000L,
            categoryLimitsCents = mapOf("food" to 50_000L, "transport" to 30_000L, "other" to 0L),
            monthlySpentByCategory = mapOf("food" to 45_000L, "transport" to 31_000L),
        )

        assertThat(overview.monthly).isNotNull()
        assertThat(overview.monthly!!.percent).isWithin(0.0001).of(0.76)
        assertThat(overview.monthly!!.status).isEqualTo(BudgetStatus.OK)
        // 交通 103% 超支 → 整体红警
        assertThat(overview.categories).hasSize(2)
        assertThat(overview.categories[1].status).isEqualTo(BudgetStatus.OVER)
        assertThat(overview.worstStatus).isEqualTo(BudgetStatus.OVER)
    }

    @Test
    fun categoryAtNinetyPercentIsYellow() {
        val overview = BudgetCalculator.overview(
            0L,
            mapOf("food" to 10_000L),
            mapOf("food" to 9_000L),
        )
        assertThat(overview.categories.single().status).isEqualTo(BudgetStatus.WARN)
        assertThat(overview.worstStatus).isEqualTo(BudgetStatus.WARN)
    }
}
