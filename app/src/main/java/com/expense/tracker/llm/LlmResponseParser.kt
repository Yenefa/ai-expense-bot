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
    /** v2.9: LLM 可以返回 actions 数组来删除/修改已有的 expense */
    val actions: List<ParsedAction> = emptyList(),
)

object LlmResponseParser {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    /**
     * 解析 LLM 输出。
     *
     * v2.9 扩展：除了 reply + expenses，还能解析 actions 字段。
     */
    fun parse(raw: String): LlmParseResult {
        val trimmed = raw.trim()

        // 1) 直接尝试整段解析
        runCatching { return parseStrict(trimmed) }

        // 2) 尝试从中间抽取第一个 {...} JSON 块
        val extracted = extractFirstJsonObject(trimmed)
        if (extracted != null) {
            runCatching { return parseStrict(extracted) }
        }

        // 3) 完全失败 → 当作纯文本闲聊回复
        return LlmParseResult(reply = trimmed.ifBlank { "（空回复）" }, expenses = emptyList())
    }

    /**
     * 解析智核分析的 LLM 输出为洞察列表。
     * 容忍模型在 JSON 外加废话 / ```json 代码块（对齐 parse() 的抽取兜底，根因 A）。
     * 完全无法解析时返回空列表，由调用方做 ifEmpty 兜底。
     */
    fun parseInsights(raw: String): List<String> {
        val trimmed = raw.trim()
        runCatching { return parseInsightsStrict(trimmed) }
        val extracted = extractFirstJsonObject(trimmed)
        if (extracted != null) {
            runCatching { return parseInsightsStrict(extracted) }
        }
        return emptyList()
    }

    private fun parseInsightsStrict(jsonText: String): List<String> =
        json.decodeFromString(AnalyticsInsightsPayload.serializer(), jsonText).insights

    private fun parseStrict(jsonText: String): LlmParseResult {
        val payload = json.decodeFromString(LlmExpensesPayload.serializer(), jsonText)
        val reply = payload.reply.ifBlank { "已记录" }

        // 解析新增
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

        // 解析 actions（v2.9）
        val actions = payload.actions.mapNotNull { a ->
            when (a.action) {
                "delete" -> ParsedAction.Delete(a.expenseId)
                "update" -> {
                    if (a.expenseId <= 0) return@mapNotNull null
                    val categoryId = a.category?.let { Category.byId(it)?.id }
                    ParsedAction.Update(
                        expenseId = a.expenseId,
                        amount = a.amount?.let { if (it > 0) it else null },
                        categoryId = categoryId,
                        note = a.note,
                        occurredAtMillis = a.occurredAt?.let(::parseOccurredAt),
                    )
                }
                else -> null
            }
        }

        return LlmParseResult(reply = reply, expenses = parsed, actions = actions)
    }

    /**
     * 在文本中找第一个 {...} 平衡 JSON 块（粗糙但够用：考虑字符串内的转义/引号）。
     * 找不到返回 null。
     */
    private fun extractFirstJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until text.length) {
            val c = text[i]
            if (escape) { escape = false; continue }
            if (c == '\\') { escape = true; continue }
            if (c == '"') { inString = !inString; continue }
            if (inString) continue
            when (c) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private fun parseOccurredAt(iso: String): Long? = runCatching {
        LocalDateTime.parse(iso)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()
}
