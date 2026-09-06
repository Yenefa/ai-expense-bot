package com.expense.tracker.agent

import com.expense.tracker.llm.ChineseDateResolver
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** 查询工具的时段参数：[fromMillis, toMillis) 半开区间。 */
data class AgentPeriodSpec(
    val label: String,
    val fromMillis: Long,
    val toMillis: Long,
)

/**
 * 把自然语言时段解析为本地区间。单日优先于跨天区间，明确日期优先于相对词。
 */
object AgentPeriodResolver {

    private val dayPattern = Regex("(?:(\\d{4})\\s*年\\s*)?(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日号]")
    private val weekdayPattern = Regex("(上|这|本)个?(?:周|星期)([一二三四五六日天])(?![一共总合计])")
    private val daysAgoPattern = Regex("(\\d+)\\s*天前")
    private val monthPattern = Regex("(?:(\\d{4})\\s*年\\s*)?(\\d{1,2})\\s*月(?!\\s*[\\d一二三四五六七八九十]?\\s*[日号])")
    private val recentDaysPattern = Regex("(?:最近|近|过去)\\s*(\\d{1,3})\\s*天")
    private val weekdayNames = mapOf(
        "一" to DayOfWeek.MONDAY,
        "二" to DayOfWeek.TUESDAY,
        "三" to DayOfWeek.WEDNESDAY,
        "四" to DayOfWeek.THURSDAY,
        "五" to DayOfWeek.FRIDAY,
        "六" to DayOfWeek.SATURDAY,
        "日" to DayOfWeek.SUNDAY,
        "天" to DayOfWeek.SUNDAY,
    )
    private val dayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd（EEE）")
    private val monthFmt = DateTimeFormatter.ofPattern("yyyy-MM")
    private val dayRangeFmt = DateTimeFormatter.ofPattern("MM-dd")

    fun resolve(text: String, nowMillis: Long, zone: ZoneId): AgentPeriodSpec? {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()

        ChineseDateResolver.resolveExplicit(text, nowMillis, zone)?.let { date ->
            return singleDay(date, zone)
        }
        daysAgoPattern.find(text)?.let { match ->
            val days = match.groupValues[1].toIntOrNull() ?: return@let
            if (days in 1..365) return singleDay(today.minusDays(days.toLong()), zone)
        }
        weekdayPattern.find(text)?.let { match ->
            val target = weekdayNames[match.groupValues[2]] ?: return@let
            val weekShift = if (match.groupValues[1] == "上") 1 else 0
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(weekShift.toLong())
            return singleDay(monday.plusDays((target.value - 1).toLong()), zone)
        }

        if (Regex("本周|这周|这一周|本星期|这个星期").containsMatchIn(text)) {
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return span(monday, monday.plusWeeks(1), "本周", zone)
        }
        if (Regex("上周|上个星期|上一个星期").containsMatchIn(text)) {
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
            return span(monday, monday.plusWeeks(1), "上周", zone)
        }

        monthPattern.find(text)?.let { match ->
            val explicitYear = match.groupValues[1].toIntOrNull()
            val month = match.groupValues[2].toIntOrNull() ?: return@let
            if (month !in 1..12) return@let
            // 没写年份且月份在当前月之后（如 9 月问"12月"）→ 按最近一个过去的该月（去年）
            val year = explicitYear ?: if (month > today.monthValue) today.year - 1 else today.year
            val first = LocalDate.of(year, month, 1)
            return span(first, first.plusMonths(1), "${monthFmt.format(first)}（指定月）", zone)
        }

        if (Regex("本月|这个月|当月|这个月份").containsMatchIn(text)) {
            return defaultMonth(nowMillis, zone)
        }
        if (Regex("上个月|上月|上一个个月").containsMatchIn(text)) {
            val first = today.withDayOfMonth(1).minusMonths(1)
            return span(first, first.plusMonths(1), "${monthFmt.format(first)}（上个月）", zone)
        }
        if (Regex("今年|本年").containsMatchIn(text)) {
            val first = LocalDate.of(today.year, 1, 1)
            return span(first, first.plusYears(1), "${today.year} 年（今年）", zone)
        }
        if (Regex("去年|上一年").containsMatchIn(text)) {
            val first = LocalDate.of(today.year - 1, 1, 1)
            return span(first, first.plusYears(1), "${today.year - 1} 年（去年）", zone)
        }
        recentDaysPattern.find(text)?.let { match ->
            val days = match.groupValues[1].toIntOrNull() ?: return@let
            if (days in 1..365) {
                return span(today.minusDays(days.toLong() - 1), today.plusDays(1), "最近 $days 天", zone)
            }
        }
        if (text.contains("最近") || text.contains(" lately")) {
            return span(today.minusDays(29), today.plusDays(1), "最近 30 天", zone)
        }
        return null
    }

    /** 查询轮没提时段时的兜底：本月。 */
    fun defaultMonth(nowMillis: Long, zone: ZoneId): AgentPeriodSpec {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val first = today.withDayOfMonth(1)
        return span(first, first.plusMonths(1), "${monthFmt.format(first)}（本月）", zone)
    }

    private fun singleDay(date: LocalDate, zone: ZoneId): AgentPeriodSpec =
        span(date, date.plusDays(1), "${dayFmt.format(date)}（单日）", zone)

    private fun span(from: LocalDate, toExclusive: LocalDate, label: String, zone: ZoneId): AgentPeriodSpec =
        AgentPeriodSpec(
            label = label,
            fromMillis = from.atStartOfDay(zone).toInstant().toEpochMilli(),
            toMillis = toExclusive.atStartOfDay(zone).toInstant().toEpochMilli(),
        )

    /** 与 [spec] 紧邻其前的上一期区间（环比基准）：日历对齐优先，避免 30/31 天错位。 */
    fun previousOf(spec: AgentPeriodSpec, zone: ZoneId): AgentPeriodSpec {
        val fromDate = Instant.ofEpochMilli(spec.fromMillis).atZone(zone).toLocalDate()
        val toDate = Instant.ofEpochMilli(spec.toMillis).atZone(zone).toLocalDate()

        // 整月：上个月 1 号到本月 1 号
        if (fromDate.dayOfMonth == 1 && toDate.dayOfMonth == 1 && fromDate.plusMonths(1) == toDate) {
            val prev = fromDate.minusMonths(1)
            return AgentPeriodSpec(
                label = "上一期（${monthFmt.format(prev)}）",
                fromMillis = prev.atStartOfDay(zone).toInstant().toEpochMilli(),
                toMillis = fromDate.atStartOfDay(zone).toInstant().toEpochMilli(),
            )
        }
        // 整周：上周一到本周一
        if (fromDate.dayOfWeek == DayOfWeek.MONDAY && toDate.dayOfWeek == DayOfWeek.MONDAY &&
            fromDate.plusWeeks(1) == toDate
        ) {
            val prev = fromDate.minusWeeks(1)
            return AgentPeriodSpec(
                label = "上一期（${dayRangeFmt.format(prev)} ~ ${dayRangeFmt.format(toDate.minusDays(1))}）",
                fromMillis = prev.atStartOfDay(zone).toInstant().toEpochMilli(),
                toMillis = fromDate.atStartOfDay(zone).toInstant().toEpochMilli(),
            )
        }
        // 单日
        if (fromDate.plusDays(1) == toDate) {
            val prev = fromDate.minusDays(1)
            return AgentPeriodSpec(
                label = "上一期（${dayRangeFmt.format(prev)}）",
                fromMillis = prev.atStartOfDay(zone).toInstant().toEpochMilli(),
                toMillis = fromDate.atStartOfDay(zone).toInstant().toEpochMilli(),
            )
        }
        // 兜底：等长回退
        return AgentPeriodSpec(
            label = "上一期（${dayRangeFmt.format(fromDate.minusDays(toDate.toEpochDay() - fromDate.toEpochDay()))} ~ ${dayRangeFmt.format(fromDate.minusDays(1))}）",
            fromMillis = spec.fromMillis - (spec.toMillis - spec.fromMillis),
            toMillis = spec.fromMillis,
        )
    }
}
