package com.expense.tracker.llm

import com.expense.tracker.data.model.Money
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class SourceExpenseHint(
    val amountCents: Long,
    val date: LocalDate,
    val time: LocalTime? = null,
)

data class ExpenseTextInterpretation(
    val normalizedText: String,
    val expenseHints: List<SourceExpenseHint>,
    val amountMentionCount: Int,
    val hasMultipleDates: Boolean,
) {
    val hasCompleteExpenseHints: Boolean
        get() = amountMentionCount > 0 && expenseHints.size == amountMentionCount
}

/**
 * Extracts only facts that the client can prove from the original text. The LLM still decides
 * category and note, while dates and amount cardinality stay guarded by deterministic hints.
 */
object ExpenseTextInterpreter {
    private val explicitDate = Regex(
        "(?:(?:\\d{4})\\s*年\\s*)?\\d{1,2}\\s*月\\s*\\d{1,2}\\s*[日号]|" +
            "(?<!\\d)\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}(?!\\d)|今天|昨天|前天",
    )
    /**
     * 支持的形式：16.6元 / 200块 / 23块5（23 元 5 角）/ 3块半（3 元 5 角）。
     * 分组：1=整数元，2=小数角分，3=「块」后的角数字，4=「块半」。
     */
    private val amount = Regex(
        "[¥￥]?\\s*(\\d+)(?:[.．](\\d{1,2}))?\\s*(?:元|块(?:\\s*(\\d)|\\s*(半))?)",
    )
    private val voiceTimeAmount = Regex(
        "(上午|中午|下午|晚上)\\s*(\\d{1,2})\\s*[.．]\\s*(\\d)\\s*元",
    )
    /** 语音时间简写后续若是标点/空白/结尾才安全；后接商品描述时 X.Y 应保留为金额。 */
    private const val VOICE_TIME_TAIL_PUNCTUATION = "，。；！？、,.!?;:：…"
    private val colonTime = Regex(
        "(?:(凌晨|早上|上午|中午|下午|傍晚|晚上)\\s*)?(?<!\\d)([01]?\\d|2[0-3])\\s*[:：]\\s*([0-5]\\d)(?!\\d)",
    )
    private val chineseClockTime = Regex(
        "(凌晨|早上|上午|中午|下午|傍晚|晚上)\\s*(\\d{1,2})\\s*[点时](?:\\s*(\\d{1,2})\\s*分|\\s*(半))?",
    )

    fun interpret(
        text: String,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ExpenseTextInterpretation {
        val normalized = normalizeVoiceTimeAmount(text)
        val anchors = explicitDate.findAll(normalized).mapNotNull { match ->
            ChineseDateResolver.resolveExplicit(match.value, nowMillis, zone)
                ?.let { match.range.first to it }
        }.toList()
        val amounts = amount.findAll(normalized).toList()
        val hints = amounts.mapIndexedNotNull { index, match ->
            val date = anchors.lastOrNull { it.first < match.range.first }?.second
                ?: return@mapIndexedNotNull null
            val cents = parseAmountCents(match) ?: return@mapIndexedNotNull null
            val segmentStart = amounts.getOrNull(index - 1)?.range?.last?.plus(1) ?: 0
            val segment = normalized.substring(segmentStart, match.range.first)
            SourceExpenseHint(cents, date, parseLastExplicitTime(segment))
        }
        return ExpenseTextInterpretation(
            normalizedText = normalized,
            expenseHints = hints,
            amountMentionCount = amounts.size,
            hasMultipleDates = anchors.map { it.second }.distinct().size > 1,
        )
    }

    /** 把 23块5 解析为 2350 分、3块半 解析为 350 分；解析失败返回 null。 */
    private fun parseAmountCents(match: MatchResult): Long? {
        val yuan = match.groupValues[1]
        val fraction = match.groupValues[2]
        val jiao = match.groupValues[3]
        val half = match.groupValues[4]
        return runCatching {
            when {
                fraction.isNotEmpty() -> Money.parseYuanToCents("$yuan.$fraction")
                jiao.isNotEmpty() -> Money.parseYuanToCents("$yuan.$jiao")
                half.isNotEmpty() -> Money.parseYuanToCents(yuan) + 50L
                else -> Money.parseYuanToCents(yuan)
            }
        }.getOrNull()
    }

    private fun normalizeVoiceTimeAmount(text: String): String =
        voiceTimeAmount.replace(text) { match ->
            val hour = match.groupValues[2].toIntOrNull()
            if (hour == null || hour !in 1..23 || hasItemSuffix(text, match.range.last)) {
                match.value
            } else {
                "${match.groupValues[1]} ${hour}点，${match.groupValues[3]}元"
            }
        }

    /**
     * 「今天下午3.5元咖啡」里 3.5 元是金额而不是「3 点 5 元」的语音简写：
     * 仅当 X.Y 元后面是标点或结尾时才允许改写；后接商品/描述文字时保留金额。
     */
    private fun hasItemSuffix(text: String, lastMatchedIndex: Int): Boolean {
        var index = lastMatchedIndex + 1
        while (index < text.length && text[index].isWhitespace()) index++
        if (index >= text.length) return false
        return text[index] !in VOICE_TIME_TAIL_PUNCTUATION
    }

    private fun parseLastExplicitTime(segment: String): LocalTime? {
        data class Candidate(val position: Int, val value: LocalTime?)

        val candidates = buildList {
            colonTime.findAll(segment).forEach { match ->
                add(
                    Candidate(
                        match.range.first,
                        localTime(
                            period = match.groupValues[1],
                            hour = match.groupValues[2].toIntOrNull(),
                            minute = match.groupValues[3].toIntOrNull(),
                        ),
                    ),
                )
            }
            chineseClockTime.findAll(segment).forEach { match ->
                val minute = when {
                    match.groupValues[4].isNotEmpty() -> 30
                    match.groupValues[3].isNotEmpty() -> match.groupValues[3].toIntOrNull()
                    else -> 0
                }
                add(
                    Candidate(
                        match.range.first,
                        localTime(
                            period = match.groupValues[1],
                            hour = match.groupValues[2].toIntOrNull(),
                            minute = minute,
                        ),
                    ),
                )
            }
        }
        return candidates.filter { it.value != null }.maxByOrNull { it.position }?.value
    }

    private fun localTime(period: String, hour: Int?, minute: Int?): LocalTime? {
        if (hour == null || minute == null || hour !in 0..23 || minute !in 0..59) return null
        val normalizedHour = when (period) {
            "凌晨", "早上", "上午" -> if (hour == 12) 0 else hour
            "中午" -> if (hour in 1..10) hour + 12 else hour
            "下午", "傍晚", "晚上" -> if (hour in 1..11) hour + 12 else hour
            else -> hour
        }
        return normalizedHour.takeIf { it in 0..23 }?.let { LocalTime.of(it, minute) }
    }
}
