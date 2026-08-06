package com.expense.tracker.util

import com.expense.tracker.data.model.Period
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields

object TimeRanges {

    /** 返回 [from, to) 半开区间的 epoch millis：本周 / 本月 / 本年。 */
    fun rangeOf(period: Period, nowMillis: Long, zone: ZoneId): Pair<Long, Long> {
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        val (from, to) = when (period) {
            Period.Week -> {
                val mon = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                mon.atStartOfDay() to mon.plusWeeks(1).atStartOfDay()
            }
            Period.Month -> {
                val first = now.toLocalDate().withDayOfMonth(1)
                first.atStartOfDay() to first.plusMonths(1).atStartOfDay()
            }
            Period.Year -> {
                val first = LocalDate.of(now.year, 1, 1)
                first.atStartOfDay() to first.plusYears(1).atStartOfDay()
            }
        }
        return from.atZone(zone).toInstant().toEpochMilli() to
               to.atZone(zone).toInstant().toEpochMilli()
    }

    /** 给定起点 fromMillis 和某条记录的 occurredAt，落入哪个桶（0-based）。 */
    fun bucketIndex(period: Period, fromMillis: Long, occurredAt: Long, zone: ZoneId): Int {
        val from = LocalDateTime.ofInstant(Instant.ofEpochMilli(fromMillis), zone).toLocalDate()
        val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(occurredAt), zone).toLocalDate()
        return when (period) {
            Period.Week  -> ChronoUnit.DAYS.between(from, at).toInt()
            Period.Month -> at.dayOfMonth - from.dayOfMonth
            Period.Year  -> at.monthValue - from.monthValue
        }
    }

    /** X 轴标签。 */
    fun bucketLabels(period: Period, anchorMillis: Long, zone: ZoneId): List<String> {
        val (from, _) = rangeOf(period, anchorMillis, zone)
        val fromDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(from), zone).toLocalDate()
        return when (period) {
            Period.Week -> listOf("周一","周二","周三","周四","周五","周六","周日")
            Period.Month -> {
                val days = fromDate.lengthOfMonth()
                (1..days).map { it.toString() }
            }
            Period.Year -> (1..12).map { "${it}月" }
        }
    }

    fun bucketCount(period: Period, anchorMillis: Long, zone: ZoneId): Int =
        bucketLabels(period, anchorMillis, zone).size

    /** ISO 周所属年 + 第几周（跨年周归属正确，如 2025-12-29 属于 2026 第 1 周）。 */
    fun isoWeek(refMillis: Long, zone: ZoneId): Pair<Int, Int> {
        val d = LocalDateTime.ofInstant(Instant.ofEpochMilli(refMillis), zone).toLocalDate()
        val wf = WeekFields.ISO
        return d.get(wf.weekBasedYear()) to d.get(wf.weekOfWeekBasedYear())
    }

    /** 参考时段的可读标签，用于 UI 提示当前看的是哪个具体时段（如"2024 年 5 月"）。 */
    fun refLabel(period: Period, refMillis: Long, zone: ZoneId): String {
        val d = LocalDateTime.ofInstant(Instant.ofEpochMilli(refMillis), zone).toLocalDate()
        return when (period) {
            Period.Year -> "${d.year} 年"
            Period.Month -> "${d.year} 年 ${d.monthValue} 月"
            Period.Week -> {
                val (wby, week) = isoWeek(refMillis, zone)
                "$wby 年第 $week 周"
            }
        }
    }

    /** 翻页：从 refMillis 出发向前/向后移 delta 个 period，返回新时段的中段参考点（避免月末溢出）。 */
    fun shiftedRef(period: Period, refMillis: Long, delta: Int, zone: ZoneId): Long {
        val d = LocalDateTime.ofInstant(Instant.ofEpochMilli(refMillis), zone).toLocalDate()
        val newDate = when (period) {
            Period.Week -> d.plusWeeks(delta.toLong())
            Period.Month -> d.plusMonths(delta.toLong()).withDayOfMonth(15)
            Period.Year -> d.plusYears(delta.toLong()).withMonth(7).withDayOfMonth(1)
        }
        return newDate.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** 该月所有"周一归属该月"的周，返回各周的周一日期（周一在哪个月，那周就归哪个月）。 */
    fun weekMondaysOfMonth(year: Int, month: Int): List<LocalDate> {
        val first = LocalDate.of(year, month, 1)
        val last = first.withDayOfMonth(first.lengthOfMonth())
        val result = mutableListOf<LocalDate>()
        var d = first
        while (d.dayOfWeek != DayOfWeek.MONDAY) d = d.plusDays(1) // 跳到该月第一个周一
        while (d <= last) {
            result.add(d)
            d = d.plusWeeks(1)
        }
        return result
    }
}
