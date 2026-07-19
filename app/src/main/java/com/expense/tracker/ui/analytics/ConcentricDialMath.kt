package com.expense.tracker.ui.analytics

import com.expense.tracker.data.model.Period
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields

/** 三层同心环罗盘的激活环：外环=周，中环=月，里环=年。 */
enum class DialRing { Week, Month, Year }

/**
 * 罗盘纯逻辑：旋转步进 → 值映射 → 参考时刻/标签计算。
 * 抽出来便于 JVM 单测，UI 层只负责手势与绘制。
 */
object ConcentricDialMath {
    /** 旋转步进：约 12°/步，一圈 30 步，手感适中。 */
    const val STEP_RADIANS: Float = (Math.PI / 15).toFloat()

    /** 某 ISO 周年（weekBasedYear）的总周数（52 或 53）。 */
    fun weeksInYear(year: Int): Int {
        val wf = WeekFields.ISO
        // 12/28 必落在该周年的最后一周
        return LocalDate.of(year, 12, 28).get(wf.weekOfWeekBasedYear())
    }

    /** 旋转步数 → 新值。wrap=true 循环（周/月），false 限范围（年）。 */
    fun nextValue(current: Int, deltaSteps: Int, min: Int, max: Int, wrap: Boolean): Int {
        if (deltaSteps == 0) return current
        val range = max - min + 1
        val raw = current + deltaSteps
        return if (wrap) {
            min + ((raw - min) % range + range) % range
        } else {
            raw.coerceIn(min, max)
        }
    }

    /** 旋转增量（弧度）→ 步数（向下取整，剩余留作累积）。 */
    fun stepsFromRotation(rotationDeltaRad: Float, stepRadians: Float = STEP_RADIANS): Int =
        (rotationDeltaRad / stepRadians).toInt()

    /** 激活环 → 对应粒度。 */
    fun period(ring: DialRing): Period = when (ring) {
        DialRing.Week -> Period.Week
        DialRing.Month -> Period.Month
        DialRing.Year -> Period.Year
    }

    /** 三环值 + 激活环 → 参考时刻 millis（取各粒度的中段日期，避免边界）。 */
    fun refMillis(ring: DialRing, year: Int, month: Int, week: Int, zone: ZoneId): Long {
        val d = when (ring) {
            DialRing.Year -> LocalDate.of(year, 7, 1) // 年中
            DialRing.Month -> LocalDate.of(year, month, 15) // 月中
            DialRing.Week -> {
                val wf = WeekFields.ISO
                // 第 week 周的周一 + 3 天 = 周四（周中）
                LocalDate.of(year, 1, 4)
                    .with(wf.weekBasedYear(), year.toLong())
                    .with(wf.weekOfWeekBasedYear(), week.toLong())
                    .with(wf.dayOfWeek(), 1L)
                    .plusDays(3)
            }
        }
        return d.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** 中心展示的时段标签。 */
    fun label(ring: DialRing, year: Int, month: Int, week: Int): String = when (ring) {
        DialRing.Year -> "$year 年"
        DialRing.Month -> "$year 年 $month 月"
        DialRing.Week -> "$year 年第 $week 周"
    }
}
