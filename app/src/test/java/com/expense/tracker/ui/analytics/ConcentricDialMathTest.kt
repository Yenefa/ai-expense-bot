package com.expense.tracker.ui.analytics

import com.expense.tracker.data.model.Period
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ConcentricDialMathTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test fun nextValueWrapsMonth() {
        // 12 月 +1 → 1（循环 1..12）
        assertThat(ConcentricDialMath.nextValue(12, 1, 1, 12, wrap = true)).isEqualTo(1)
        // 1 月 -1 → 12
        assertThat(ConcentricDialMath.nextValue(1, -1, 1, 12, wrap = true)).isEqualTo(12)
        // 6 月 +3 → 9
        assertThat(ConcentricDialMath.nextValue(6, 3, 1, 12, wrap = true)).isEqualTo(9)
    }

    @Test fun nextValueClampsYear() {
        // 年不循环，限范围 2020..2027
        assertThat(ConcentricDialMath.nextValue(2020, -1, 2020, 2027, wrap = false)).isEqualTo(2020)
        assertThat(ConcentricDialMath.nextValue(2027, 1, 2020, 2027, wrap = false)).isEqualTo(2027)
        assertThat(ConcentricDialMath.nextValue(2024, 2, 2020, 2027, wrap = false)).isEqualTo(2026)
    }

    @Test fun weeksInYearMatchesIso() {
        // 2025: 1/1 周三 + 非闰年 → 52 周；2026: 1/1 周四 → 53 周
        assertThat(ConcentricDialMath.weeksInYear(2025)).isEqualTo(52)
        assertThat(ConcentricDialMath.weeksInYear(2026)).isEqualTo(53)
    }

    @Test fun stepsFromRotationFloors() {
        // 顺时针 1 步 ~0.21 弧度
        assertThat(ConcentricDialMath.stepsFromRotation(ConcentricDialMath.STEP_RADIANS)).isEqualTo(1)
        // 逆时针 -1 步
        assertThat(ConcentricDialMath.stepsFromRotation(-ConcentricDialMath.STEP_RADIANS)).isEqualTo(-1)
        // 不足一步 → 0
        assertThat(ConcentricDialMath.stepsFromRotation(0.05f)).isEqualTo(0)
    }

    @Test fun periodMapping() {
        assertThat(ConcentricDialMath.period(DialRing.Week)).isEqualTo(Period.Week)
        assertThat(ConcentricDialMath.period(DialRing.Month)).isEqualTo(Period.Month)
        assertThat(ConcentricDialMath.period(DialRing.Year)).isEqualTo(Period.Year)
    }

    @Test fun refMillisForWeekHitsMidweek() {
        // 2025 第 24 周：周一是 6/9，周四 6/12
        val ref = ConcentricDialMath.refMillis(DialRing.Week, 2025, 6, 24, zone)
        val expected = LocalDate.of(2025, 6, 12).atStartOfDay(zone).toInstant().toEpochMilli()
        assertThat(ref).isEqualTo(expected)
    }

    @Test fun refMillisForMonthHitsMidmonth() {
        val ref = ConcentricDialMath.refMillis(DialRing.Month, 2024, 5, 20, zone)
        val expected = LocalDate.of(2024, 5, 15).atStartOfDay(zone).toInstant().toEpochMilli()
        assertThat(ref).isEqualTo(expected)
    }

    @Test fun labelByRing() {
        assertThat(ConcentricDialMath.label(DialRing.Year, 2025, 6, 24)).isEqualTo("2025 年")
        assertThat(ConcentricDialMath.label(DialRing.Month, 2025, 6, 24)).isEqualTo("2025 年 6 月")
        assertThat(ConcentricDialMath.label(DialRing.Week, 2025, 6, 24)).isEqualTo("2025 年第 24 周")
    }
}
