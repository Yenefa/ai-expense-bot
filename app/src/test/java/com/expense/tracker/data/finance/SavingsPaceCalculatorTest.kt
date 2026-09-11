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
    fun `达标日均上限目标高于收入时为0`() {
        assertThat(SavingsPaceCalculator.requiredDailySpendCents(800_000L, 200_000L, 30))
            .isEqualTo(20_000L)
        assertThat(SavingsPaceCalculator.requiredDailySpendCents(100_000L, 200_000L, 30))
            .isEqualTo(0L)
    }
}
