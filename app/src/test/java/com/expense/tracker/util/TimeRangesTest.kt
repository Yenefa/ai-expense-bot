package com.expense.tracker.util

import com.expense.tracker.data.model.Period
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class TimeRangesTest {
    private val zone = ZoneId.systemDefault()
    private fun millis(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    @Test fun weekRangeForWednesday() {
        val now = millis(2025, 6, 11, 14, 30)
        val (from, to) = TimeRanges.rangeOf(Period.Week, now, zone)
        assertThat(from).isEqualTo(millis(2025, 6, 9, 0, 0))
        assertThat(to).isEqualTo(millis(2025, 6, 16, 0, 0))
    }

    @Test fun monthRange() {
        val now = millis(2025, 6, 15, 10, 0)
        val (from, to) = TimeRanges.rangeOf(Period.Month, now, zone)
        assertThat(from).isEqualTo(millis(2025, 6, 1, 0, 0))
        assertThat(to).isEqualTo(millis(2025, 7, 1, 0, 0))
    }

    @Test fun yearRange() {
        val now = millis(2025, 6, 15, 10, 0)
        val (from, to) = TimeRanges.rangeOf(Period.Year, now, zone)
        assertThat(from).isEqualTo(millis(2025, 1, 1, 0, 0))
        assertThat(to).isEqualTo(millis(2026, 1, 1, 0, 0))
    }

    @Test fun bucketIndexForWeek() {
        val (from, _) = TimeRanges.rangeOf(Period.Week, millis(2025, 6, 11), zone)
        assertThat(TimeRanges.bucketIndex(Period.Week, from, millis(2025, 6, 9, 12), zone)).isEqualTo(0)
        assertThat(TimeRanges.bucketIndex(Period.Week, from, millis(2025, 6, 12, 12), zone)).isEqualTo(3)
    }

    @Test fun bucketLabelsForMonth() {
        val labels = TimeRanges.bucketLabels(Period.Month, millis(2025, 2, 15), zone)
        assertThat(labels).hasSize(28)
        assertThat(labels.first()).isEqualTo("1")
        assertThat(labels.last()).isEqualTo("28")
    }

    @Test fun bucketLabelsForYear() {
        val labels = TimeRanges.bucketLabels(Period.Year, millis(2025, 1, 1), zone)
        assertThat(labels).containsExactly(
            "1月","2月","3月","4月","5月","6月","7月","8月","9月","10月","11月","12月"
        ).inOrder()
    }

    @Test fun refLabelForYear() {
        assertThat(TimeRanges.refLabel(Period.Year, millis(2024, 6, 15), zone)).isEqualTo("2024 年")
    }

    @Test fun refLabelForMonth() {
        assertThat(TimeRanges.refLabel(Period.Month, millis(2024, 5, 15), zone)).isEqualTo("2024 年 5 月")
    }

    @Test fun refLabelForWeek() {
        // 2025-06-09 周一，ISO 第 24 周
        assertThat(TimeRanges.refLabel(Period.Week, millis(2025, 6, 9), zone)).isEqualTo("2025 年第 24 周")
    }

    @Test fun isoWeekCrossYear() {
        // 2025-12-29 周一，属于 ISO 2026 第 1 周
        val (wby, week) = TimeRanges.isoWeek(millis(2025, 12, 29), zone)
        assertThat(wby).isEqualTo(2026)
        assertThat(week).isEqualTo(1)
    }

    @Test fun rangeOfHonorsArbitraryRefMillis() {
        val (from, to) = TimeRanges.rangeOf(Period.Month, millis(2024, 5, 15), zone)
        assertThat(from).isEqualTo(millis(2024, 5, 1))
        assertThat(to).isEqualTo(millis(2024, 6, 1))
    }

    @Test fun shiftedRefPrevMonth() {
        // 2024-05-15 上一月 -> 2024-04-15
        assertThat(TimeRanges.shiftedRef(Period.Month, millis(2024, 5, 15), -1, zone)).isEqualTo(millis(2024, 4, 15))
    }

    @Test fun shiftedRefNextYear() {
        // 2024-07-01 下一年 -> 2025-07-01
        assertThat(TimeRanges.shiftedRef(Period.Year, millis(2024, 7, 1), 1, zone)).isEqualTo(millis(2025, 7, 1))
    }

    @Test fun shiftedRefPrevWeek() {
        // 2025-06-12（周四）上一周 -> 2025-06-05（周四）
        assertThat(TimeRanges.shiftedRef(Period.Week, millis(2025, 6, 12), -1, zone)).isEqualTo(millis(2025, 6, 5))
    }

    @Test fun weekMondaysOfMonthReturnsMondaysInMonth() {
        // 2025-06-01 是周日，第一个周一 6/2；周一归属：6/2,6/9,6/16,6/23,6/30
        val mondays = TimeRanges.weekMondaysOfMonth(2025, 6)
        assertThat(mondays).hasSize(5)
        assertThat(mondays.first()).isEqualTo(LocalDate.of(2025, 6, 2))
        assertThat(mondays.last()).isEqualTo(LocalDate.of(2025, 6, 30))
    }

    @Test fun weekMondaysSkipsFirstDaysWhenMonthStartsAfterMonday() {
        // 2025-05-01 周四，第一个周一 5/5；1-4 号所在周周一在上月，不归本月
        val mondays = TimeRanges.weekMondaysOfMonth(2025, 5)
        assertThat(mondays.first()).isEqualTo(LocalDate.of(2025, 5, 5))
        assertThat(mondays.last()).isEqualTo(LocalDate.of(2025, 5, 26))
    }
}
