package com.expense.tracker.llm

import com.expense.tracker.data.model.Category
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.ZoneId

class LlmParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

data class ParsedExpense(
    val amount: Double,
    val categoryId: String,
    val note: String,
    val occurredAtMillis: Long?,
)

data class LlmParseResult(
    val reply: String,
    val expenses: List<ParsedExpense>,
)

object LlmResponseParser {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    fun parse(raw: String): LlmParseResult {
        val payload = try {
            json.decodeFromString(LlmExpensesPayload.serializer(), raw.trim())
        } catch (e: Exception) {
            throw LlmParseException("无法解析 LLM 输出为 JSON：${e.message}", e)
        }
        val reply = payload.reply.ifBlank { "已记录" }
        val parsed = payload.expenses
            .filter { it.amount > 0.0 }
            .map { item ->
                ParsedExpense(
                    amount = "%.2f".format(item.amount).toDouble(),
                    categoryId = Category.byId(item.category)?.id ?: "other",
                    note = item.note,
                    occurredAtMillis = item.occurredAt?.let(::parseOccurredAt),
                )
            }
        return LlmParseResult(reply = reply, expenses = parsed)
    }

    private fun parseOccurredAt(iso: String): Long? = runCatching {
        LocalDateTime.parse(iso)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()
}
