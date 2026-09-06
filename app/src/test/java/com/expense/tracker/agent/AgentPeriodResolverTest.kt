package com.expense.tracker.agent

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

class AgentPeriodResolverTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    // 2026-09-06 是周日
    private val now = LocalDate.of(2026, 9, 6).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun resolve(text: String): Pair<Long, Long>? =
        AgentPeriodResolver.resolve(text, now, zone)?.let { it.fromMillis to it.toMillis }

    private fun millis(date: String): Long =
        LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `相对词单日`() {
        val (from, to) = resolve("昨天午饭吃了啥")!!
        assertThat(from).isEqualTo(millis("2026-09-05"))
        assertThat(to).isEqualTo(millis("2026-09-06"))
    }

    @Test
    fun `今天单日`() {
        val (from, _) = resolve("今天花了多少")!!
        assertThat(from).isEqualTo(millis("2026-09-06"))
    }

    @Test
    fun `明确日期`() {
        val (from, to) = resolve("9月3日花了多少")!!
        assertThat(from).isEqualTo(millis("2026-09-03"))
        assertThat(to).isEqualTo(millis("2026-09-04"))
    }

    @Test
    fun `ISO 日期`() {
        val (from, _) = resolve("2026-08-15 消费")!!
        assertThat(from).isEqualTo(millis("2026-08-15"))
    }

    @Test
    fun `上周三`() {
        val (from, _) = resolve("上周三打车多少钱")!!
        assertThat(from).isEqualTo(millis("2026-08-26"))
    }

    @Test
    fun `这周五`() {
        val (from, _) = resolve("这周五吃了什么")!!
        assertThat(from).isEqualTo(millis("2026-09-04"))
    }

    @Test
    fun `N天前`() {
        val (from, _) = resolve("3天前买的水")!!
        assertThat(from).isEqualTo(millis("2026-09-03"))
    }

    @Test
    fun `上周区间`() {
        val (from, to) = resolve("上周一共花了多少")!!
        assertThat(from).isEqualTo(millis("2026-08-24"))
        assertThat(to).isEqualTo(millis("2026-08-31"))
    }

    @Test
    fun `本周区间`() {
        val (from, to) = resolve("本周花了多少")!!
        assertThat(from).isEqualTo(millis("2026-08-31"))
        assertThat(to).isEqualTo(millis("2026-09-07"))
    }

    @Test
    fun `指定月份`() {
        val (from, to) = resolve("8月花了多少")!!
        assertThat(from).isEqualTo(millis("2026-08-01"))
        assertThat(to).isEqualTo(millis("2026-09-01"))
    }

    @Test
    fun `未来月份归去年`() {
        val (from, _) = resolve("12月花了多少")!!
        assertThat(from).isEqualTo(millis("2025-12-01"))
    }

    @Test
    fun `上个月`() {
        val (from, to) = resolve("上个月花了多少")!!
        assertThat(from).isEqualTo(millis("2026-08-01"))
        assertThat(to).isEqualTo(millis("2026-09-01"))
    }

    @Test
    fun `今年区间`() {
        val (from, to) = resolve("今年一共消费多少")!!
        assertThat(from).isEqualTo(millis("2026-01-01"))
        assertThat(to).isEqualTo(millis("2027-01-01"))
    }

    @Test
    fun `最近N天`() {
        val (from, to) = resolve("最近7天花了多少")!!
        assertThat(from).isEqualTo(millis("2026-08-31"))
        assertThat(to).isEqualTo(millis("2026-09-07"))
    }

    @Test
    fun `裸最近默认三十天`() {
        val (from, to) = resolve("最近花了多少")!!
        assertThat(from).isEqualTo(millis("2026-08-08"))
        assertThat(to).isEqualTo(millis("2026-09-07"))
    }

    @Test
    fun `无时段返回null`() {
        assertThat(resolve("一共花了多少")).isNull()
        assertThat(AgentPeriodResolver.defaultMonth(now, zone).label).contains("2026-09")
    }
}
