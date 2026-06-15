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

/**
 * LLM 提议的操作 — 已把 ActionMatch 中的 ISO 时间字符串解析成 epoch millis，
 * 把 categoryId 校准过（未知 → null，让 SQL 不限制 category 而非 fallback 到 "other"）。
 */
data class ParsedAction(
    val op: String,
    val matchCategoryId: String?,
    val matchAmount: Double?,
    val matchFromMillis: Long?,
    val matchToMillis: Long?,
    val matchNoteContains: String?,
    val patchAmount: Double?,
    val patchCategoryId: String?,
    val patchNote: String?,
    val aggregate: String?,
)

data class LlmParseResult(
    val reply: String,
    val expenses: List<ParsedExpense>,
    val actions: List<ParsedAction> = emptyList(),
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
        val parsedExpenses = payload.expenses
            .filter { it.amount > 0.0 }
            .map { item ->
                ParsedExpense(
                    amount = "%.2f".format(item.amount).toDouble(),
                    categoryId = Category.byId(item.category)?.id ?: "other",
                    note = item.note,
                    occurredAtMillis = item.occurredAt?.let(::parseOccurredAt),
                )
            }
        val parsedActions = payload.actions.mapNotNull(::parseAction)
        return LlmParseResult(reply = reply, expenses = parsedExpenses, actions = parsedActions)
    }

    private fun parseAction(a: LlmAction): ParsedAction? {
        if (a.op !in VALID_OPS) return null
        return ParsedAction(
            op = a.op,
            // 这里和 expense parsing 不同：未知 category 返回 null（= SQL 不限制 category）
            // 因为 fallback 到 "other" 会让"删掉昨天的咖啡"误删"昨天分类为 other 的支出"
            matchCategoryId = a.match?.category?.let { Category.byId(it)?.id },
            matchAmount = a.match?.amount?.takeIf { it > 0.0 },
            matchFromMillis = a.match?.dateFrom?.let(::parseOccurredAt),
            matchToMillis = a.match?.dateTo?.let(::parseOccurredAt),
            matchNoteContains = a.match?.noteContains?.takeIf { it.isNotBlank() },
            patchAmount = a.patch?.amount?.takeIf { it > 0.0 },
            patchCategoryId = a.patch?.category?.let { Category.byId(it)?.id },
            patchNote = a.patch?.note,
            aggregate = a.aggregate,
        )
    }

    private fun parseOccurredAt(iso: String): Long? = runCatching {
        LocalDateTime.parse(iso)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()

    private val VALID_OPS = setOf("delete", "update", "query")
}
