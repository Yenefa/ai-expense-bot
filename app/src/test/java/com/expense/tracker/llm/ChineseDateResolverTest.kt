package com.expense.tracker.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ChineseDateResolverTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDateTime.of(2026, 8, 2, 22, 14)
        .atZone(zone).toInstant().toEpochMilli()

    @Test fun parsesChineseIsoAndRelativeDates() {
        assertThat(ChineseDateResolver.resolveExplicit("记到2026年8月1日", now, zone))
            .isEqualTo(LocalDate.of(2026, 8, 1))
        assertThat(ChineseDateResolver.resolveExplicit("全部记到8月1号", now, zone))
            .isEqualTo(LocalDate.of(2026, 8, 1))
        assertThat(ChineseDateResolver.resolveExplicit("改到2026-07-26", now, zone))
            .isEqualTo(LocalDate.of(2026, 7, 26))
        assertThat(ChineseDateResolver.resolveExplicit("昨天的午饭", now, zone))
            .isEqualTo(LocalDate.of(2026, 8, 1))
        assertThat(ChineseDateResolver.resolveExplicit("前天打车", now, zone))
            .isEqualTo(LocalDate.of(2026, 7, 31))
        assertThat(ChineseDateResolver.resolveExplicit("今天充话费", now, zone))
            .isEqualTo(LocalDate.of(2026, 8, 2))
    }

    @Test fun monthDayNearNewYearResolvesToRecentPastYear() {
        val januarySecond = LocalDateTime.of(2026, 1, 2, 9, 0)
            .atZone(zone).toInstant().toEpochMilli()

        assertThat(ChineseDateResolver.resolveExplicit("12月31日早餐", januarySecond, zone))
            .isEqualTo(LocalDate.of(2025, 12, 31))
    }

    @Test fun continuationInheritsMostRecentExplicitUserDate() {
        val resolved = ChineseDateResolver.resolveForMessage(
            currentText = "6，13，18，4，7，83，还有一笔7元",
            previousUserTextsNewestFirst = listOf(
                "这些分别是多少钱？",
                "8月1日下午有两瓶水、麻薯和两趟地铁",
            ),
            nowMillis = now,
            zone = zone,
        )

        assertThat(resolved).isEqualTo(LocalDate.of(2026, 8, 1))
    }

    @Test fun historicalRelativeDateUsesItsOwnMessageTimestamp() {
        val day1Noon = LocalDateTime.of(2026, 8, 1, 12, 0)
            .atZone(zone).toInstant().toEpochMilli()
        val day3Noon = LocalDateTime.of(2026, 8, 3, 12, 0)
            .atZone(zone).toInstant().toEpochMilli()

        val resolved = ChineseDateResolver.resolveForMessage(
            currentText = "还有一笔7元",
            previousUserMessagesNewestFirst = listOf("昨天午饭35" to day1Noon),
            nowMillis = day3Noon,
            zone = zone,
        )

        // Day1 说的“昨天”指 Day0（7月31日），不能按 Day3 的当前时间解析成 Day2（8月2日）。
        assertThat(resolved).isEqualTo(LocalDate.of(2026, 7, 31))
    }

    @Test fun legacyStringOverloadResolvesHistoryAgainstNowMillis() {
        val day3Noon = LocalDateTime.of(2026, 8, 3, 12, 0)
            .atZone(zone).toInstant().toEpochMilli()

        val resolved = ChineseDateResolver.resolveForMessage(
            currentText = "还有一笔7元",
            previousUserTextsNewestFirst = listOf("昨天午饭35"),
            nowMillis = day3Noon,
            zone = zone,
        )

        // 旧重载没有历史时间戳，只能沿用当前时间：Day3 的“昨天”= Day2（8月2日）。
        assertThat(resolved).isEqualTo(LocalDate.of(2026, 8, 2))
    }

    @Test fun independentMessageDoesNotInheritOldDate() {
        val resolved = ChineseDateResolver.resolveForMessage(
            currentText = "午饭35",
            previousUserTextsNewestFirst = listOf("8月1日买了两瓶水"),
            nowMillis = now,
            zone = zone,
        )

        assertThat(resolved).isNull()
    }

    @Test fun currentExplicitDateAlwaysWinsOverHistory() {
        val resolved = ChineseDateResolver.resolveForMessage(
            currentText = "今天充话费50",
            previousUserTextsNewestFirst = listOf("8月1日买了两瓶水"),
            nowMillis = now,
            zone = zone,
        )

        assertThat(resolved).isEqualTo(LocalDate.of(2026, 8, 2))
    }

    @Test fun mutationTargetDateAfterMoveKeywordWinsOverSourceDate() {
        val resolved = ChineseDateResolver.resolveForMessage(
            currentText = "把8月1日的全部记录改到7月26号",
            previousUserTextsNewestFirst = emptyList(),
            nowMillis = now,
            zone = zone,
        )

        assertThat(resolved).isEqualTo(LocalDate.of(2026, 7, 26))
    }

    @Test fun targetDateSurvivesLaterAmountEditMarkerAndSupportsGaiZhi() {
        assertThat(
            ChineseDateResolver.resolveExplicit("把这笔改到7月26号，金额改为50", now, zone),
        ).isEqualTo(LocalDate.of(2026, 7, 26))
        assertThat(
            ChineseDateResolver.resolveExplicit("把8月1日的账改至7月26日", now, zone),
        ).isEqualTo(LocalDate.of(2026, 7, 26))
    }

    @Test fun replacingDateKeepsOriginalTimeOfDay() {
        val original = LocalDateTime.of(2026, 8, 2, 22, 12, 34)
            .atZone(zone).toInstant().toEpochMilli()

        val replaced = ChineseDateResolver.replaceDateKeepingTime(
            originalMillis = original,
            targetDate = LocalDate.of(2026, 7, 26),
            zone = zone,
        )

        assertThat(
            LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(replaced), zone),
        ).isEqualTo(LocalDateTime.of(2026, 7, 26, 22, 12, 34))
    }
}
