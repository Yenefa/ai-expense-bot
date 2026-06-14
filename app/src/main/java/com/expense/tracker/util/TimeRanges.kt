package com.expense.tracker.util

import com.expense.tracker.data.model.Period
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

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
}
