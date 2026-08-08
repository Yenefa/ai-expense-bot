package com.expense.tracker.data.recurring

import com.expense.tracker.data.db.RecurringPeriodType
import com.expense.tracker.data.db.RecurringRuleEntity
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RecurringGeneratorTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    private fun rule(
        periodType: RecurringPeriodType,
        dayOfMonth: Int = 1,
        dayOfWeek: Int = 1,
        monthOfYear: Int = 1,
        nextDueAt: Long,
    ) = RecurringRuleEntity(
        amountCents = 100L,
        categoryId = "housing",
        note = "房租",
        periodType = periodType.name,
        dayOfMonth = dayOfMonth,
        dayOfWeek = dayOfWeek,
        monthOfYear = monthOfYear,
        nextDueAt = nextDueAt,
        createdAt = nextDueAt,
    )

    private fun day(y: Int, m: Int, d: Int): Long =
        LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun monthlyAdvancesToNextMonthKeepingDay() {
        val r = rule(RecurringPeriodType.MONTHLY, dayOfMonth = 15, nextDueAt = day(2026, 8, 15))
        // 已到期 10 天（8/25），应补最近一期 8/15 并推进到 9/15（跳过 9/15 前的所有）
        val next = RecurringGenerator.nextDueAfter(r, r.nextDueAt, day(2026, 8, 25), zone)
        assertThat(next).isEqualTo(day(2026, 9, 15))
    }

    @Test
    fun monthlyDay31FallsBackToMonthLastDay() {
        val r = rule(RecurringPeriodType.MONTHLY, dayOfMonth = 31, nextDueAt = day(2026, 1, 31))
        val next = RecurringGenerator.nextDueAfter(r, r.nextDueAt, day(2026, 1, 31), zone)
        // 2 月没有 31 日 → 2/28（2026 非闰年）
        assertThat(next).isEqualTo(day(2026, 2, 28))
    }

    @Test
    fun weeklyAdvancesToNextGivenWeekday() {
        // 2026-08-06 是周四，每周五到期（5=周五）
        val r = rule(RecurringPeriodType.WEEKLY, dayOfWeek = 5, nextDueAt = day(2026, 8, 7))
        val next = RecurringGenerator.nextDueAfter(r, r.nextDueAt, day(2026, 8, 6), zone)
        assertThat(next).isEqualTo(day(2026, 8, 7))
    }

    @Test
    fun yearlyAdvancesToNextYearKeepingMonthAndDay() {
        val r = rule(RecurringPeriodType.YEARLY, monthOfYear = 6, dayOfMonth = 20, nextDueAt = day(2025, 6, 20))
        val next = RecurringGenerator.nextDueAfter(r, r.nextDueAt, day(2026, 1, 1), zone)
        assertThat(next).isEqualTo(day(2026, 6, 20))
    }

    @Test
    fun longGapOnlyAdvancesToNearestFutureDue() {
        // 房租每月 1 日，上次到期 7/1，现在 12/10：直接到 1/1（跳过 8-12 月）
        val r = rule(RecurringPeriodType.MONTHLY, dayOfMonth = 1, nextDueAt = day(2026, 7, 1))
        val next = RecurringGenerator.nextDueAfter(r, r.nextDueAt, day(2026, 12, 10), zone)
        assertThat(next).isEqualTo(day(2027, 1, 1))
    }
}
