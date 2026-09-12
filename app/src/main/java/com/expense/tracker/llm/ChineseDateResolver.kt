package com.expense.tracker.llm

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object ChineseDateResolver {
    private val chineseDate = Regex(
        "(?:(\\d{4})\\s*年\\s*)?(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日号]",
    )
    private val isoDate = Regex("(?<!\\d)(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})(?!\\d)")
    private val mutationTargetMarker = Regex("(?:改|修改|移|挪|记|放|调整)(?:到|为|成|在|至)")
    private val continuationWords = Regex(
        "它们|他们|这些|那些|那批|这批|刚才|上一批|上面|前面|全部|都改|都记|分别|金额|还有|然后|就刚才|那一堆",
    )
    private val amountContinuation = Regex("^[\\s¥￥]?[+-]?\\d+(?:[.,，、\\s]+\\d+)+(?:.*)?$")

    fun resolveExplicit(
        text: String,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): LocalDate? {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val targetMarkers = mutationTargetMarker.findAll(text).toList()
        if (targetMarkers.isNotEmpty()) {
            targetMarkers.forEachIndexed { index, marker ->
                val segmentStart = marker.range.last + 1
                val segmentEnd = targetMarkers.getOrNull(index + 1)?.range?.first ?: text.length
                parseDate(text.substring(segmentStart, segmentEnd), today)?.let { return it }
            }
            // 存在修改目标标记却没有目标日期时，不能把标记前的来源日期误当成目标日期。
            return null
        }
        return parseDate(text, today)
    }

    private fun parseDate(text: String, today: LocalDate): LocalDate? {

        isoDate.find(text)?.let { match ->
            return validDate(
                match.groupValues[1].toInt(),
                match.groupValues[2].toInt(),
                match.groupValues[3].toInt(),
            )
        }

        chineseDate.find(text)?.let { match ->
            val explicitYear = match.groupValues[1].toIntOrNull()
            val month = match.groupValues[2].toInt()
            val day = match.groupValues[3].toInt()
            if (explicitYear != null) return validDate(explicitYear, month, day)

            val currentYearCandidate = validDate(today.year, month, day) ?: return null
            return if (currentYearCandidate.isAfter(today.plusDays(1))) {
                validDate(today.year - 1, month, day)
            } else {
                currentYearCandidate
            }
        }

        return when {
            text.contains("前天") -> today.minusDays(2)
            text.contains("昨天") -> today.minusDays(1)
            text.contains("今天") -> today
            else -> null
        }
    }

    /**
     * 续记消息的日期解析：当前文本始终以 [nowMillis] 为基准；
     * 历史文本则各自以消息发送时间（Pair.second）为基准，
     * 避免把 Day1 说的“昨天”错误地对齐到 Day3 的当前时间。
     */
    @JvmName("resolveForMessagesWithTimestamps")
    fun resolveForMessage(
        currentText: String,
        previousUserMessagesNewestFirst: List<Pair<String, Long>>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): LocalDate? {
        resolveExplicit(currentText, nowMillis, zone)?.let { return it }
        if (!looksLikeContinuation(currentText)) return null
        return previousUserMessagesNewestFirst
            .asSequence()
            .take(MAX_CONTEXT_MESSAGES)
            .mapNotNull { (text, createdAtMillis) -> resolveExplicit(text, createdAtMillis, zone) }
            .firstOrNull()
    }

    /** 兼容旧调用方：历史文本没有时间戳时，统一按 [nowMillis] 解析（保持原有语义）。 */
    fun resolveForMessage(
        currentText: String,
        previousUserTextsNewestFirst: List<String>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): LocalDate? = resolveForMessage(
        currentText = currentText,
        previousUserMessagesNewestFirst = previousUserTextsNewestFirst.map { it to nowMillis },
        nowMillis = nowMillis,
        zone = zone,
    )

    fun looksLikeContinuation(text: String): Boolean {
        val normalized = text.trim()
        if (normalized.isEmpty()) return false
        return continuationWords.containsMatchIn(normalized) ||
            amountContinuation.matches(normalized) ||
            normalized.startsWith("补充")
    }

    fun replaceDateKeepingTime(
        originalMillis: Long,
        targetDate: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val originalTime = Instant.ofEpochMilli(originalMillis).atZone(zone).toLocalTime()
        return targetDate.atTime(originalTime).atZone(zone).toInstant().toEpochMilli()
    }

    private fun validDate(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate.of(year, month, day) }.getOrNull()

    private const val MAX_CONTEXT_MESSAGES = 8
}
