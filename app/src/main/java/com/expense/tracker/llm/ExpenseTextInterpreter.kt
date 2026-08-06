package com.expense.tracker.llm

import com.expense.tracker.data.model.Money
import java.time.LocalDate
import java.time.ZoneId

data class SourceExpenseHint(
    val amountCents: Long,
    val date: LocalDate,
)

data class ExpenseTextInterpretation(
    val normalizedText: String,
    val expenseHints: List<SourceExpenseHint>,
    val amountMentionCount: Int,
    val hasMultipleDates: Boolean,
) {
    val hasCompleteMultiDateHints: Boolean
        get() = hasMultipleDates && amountMentionCount > 0 && expenseHints.size == amountMentionCount
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
    private val amount = Regex("[¥￥]?\\s*(\\d+(?:\\.\\d{1,2})?)\\s*(?:元|块)")
    private val voiceTimeAmount = Regex(
        "(上午|中午|下午|晚上)\\s*(\\d{1,2})\\s*[.．]\\s*(\\d)\\s*元",
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
        val hints = amounts.mapNotNull { match ->
            val date = anchors.lastOrNull { it.first < match.range.first }?.second
                ?: return@mapNotNull null
            val cents = runCatching { Money.parseYuanToCents(match.groupValues[1]) }.getOrNull()
                ?: return@mapNotNull null
            SourceExpenseHint(cents, date)
        }
        return ExpenseTextInterpretation(
            normalizedText = normalized,
            expenseHints = hints,
            amountMentionCount = amounts.size,
            hasMultipleDates = anchors.map { it.second }.distinct().size > 1,
        )
    }

    private fun normalizeVoiceTimeAmount(text: String): String =
        voiceTimeAmount.replace(text) { match ->
            val hour = match.groupValues[2].toIntOrNull()
            if (hour == null || hour !in 1..23) {
                match.value
            } else {
                "${match.groupValues[1]} ${hour}点，${match.groupValues[3]}元"
            }
        }
}
