package com.expense.tracker.data.finance

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SavingsPaceCalculatorTest {

    @Test
    fun `按日均外推并给出达标缺口`() {
        // 收入 8000，目标 5000，已过 15 天花 3000 → 预计 6000 → 结余 2000，差 3000
        val pace = SavingsPaceCalculator.compute(
            incomeCents = 800_000L,
            goalCents = 500_000L,
            spentCents = 300_000L,
            elapsedDays = 15,
            daysInMonth = 30,
        )!!

        assertThat(pace.projectedSpendCents).isEqualTo(600_000L)
        assertThat(pace.projectedLeftoverCents).isEqualTo(200_000L)
        assertThat(pace.goalGapCents).isEqualTo(-300_000L)
        assertThat(pace.remainingDays).isEqualTo(15)
        assertThat(pace.remainingSpendableCents).isEqualTo(0L) // 800000 - 500000 - 300000
        assertThat(pace.remainingDailyBudgetCents).isEqualTo(0L)
        assertThat(pace.onTrack).isFalse()
    }

    @Test
    fun `预计结余恰等于目标视为达标`() {
        // 收入 8000，目标 2000，已过15天花3000 → 预计6000 → 结余2000 == 目标
        val pace = SavingsPaceCalculator.compute(
            incomeCents = 800_000L,
            goalCents = 200_000L,
            spentCents = 300_000L,
            elapsedDays = 15,
            daysInMonth = 30,
        )!!

        assertThat(pace.projectedLeftoverCents).isEqualTo(200_000L)
        assertThat(pace.goalGapCents).isEqualTo(0L)
        assertThat(pace.onTrack).isTrue()
    }

    @Test
    fun `入不敷出与负可支配额度`() {
        val pace = SavingsPaceCalculator.compute(
            incomeCents = 800_000L,
            goalCents = 200_000L,
            spentCents = 650_000L,
            elapsedDays = 15,
            daysInMonth = 30,
        )!!

        assertThat(pace.projectedLeftoverCents).isEqualTo(-500_000L)
        assertThat(pace.remainingSpendableCents).isEqualTo(-50_000L)
        assertThat(pace.onTrack).isFalse()
    }

    @Test
    fun `输入非法返回null`() {
        assertThat(
            SavingsPaceCalculator.compute(0L, 200_000L, 100L, 15, 30),
        ).isNull()
        assertThat(
            SavingsPaceCalculator.compute(800_000L, 0L, 100L, 15, 30),
        ).isNull()
        assertThat(
            SavingsPaceCalculator.compute(800_000L, 200_000L, 100L, 0, 30),
        ).isNull()
        assertThat(
            SavingsPaceCalculator.compute(800_000L, 200_000L, 100L, 31, 30),
        ).isNull()
        assertThat(
            SavingsPaceCalculator.compute(800_000L, 200_000L, -1L, 15, 30),
        ).isNull()
    }

    @Test
    fun `剩余日均额度按剩余天数计算且不为负`() {
        // 收入 8000，目标 5000，已过 10 天花 2000 → 剩余 20 天、剩余可支配 1000 → 日均 50
        val pace = SavingsPaceCalculator.compute(800_000L, 500_000L, 200_000L, 10, 30)!!
        assertThat(pace.remainingDays).isEqualTo(20)
        assertThat(pace.remainingSpendableCents).isEqualTo(100_000L)
        assertThat(pace.remainingDailyBudgetCents).isEqualTo(5_000L)

        // 剩余可支配为负 → 日均下限 0，不出现负数
        val over = SavingsPaceCalculator.compute(800_000L, 500_000L, 400_000L, 10, 30)!!
        assertThat(over.remainingSpendableCents).isEqualTo(-100_000L)
        assertThat(over.remainingDailyBudgetCents).isEqualTo(0L)
    }
}
