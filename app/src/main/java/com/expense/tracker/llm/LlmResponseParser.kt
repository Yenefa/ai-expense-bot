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

    /**
     * 解析 LLM 输出。
     *
     * 容错策略：
     * 1. 如果整段是合法 JSON 对象 → 按预期 schema 解析
     * 2. 如果整段不是 JSON，但能从中提取出第一个 {...} 块 → 用提取出的 JSON 解析
     * 3. 如果完全没 JSON（模型直接返回纯文本闲聊）→ 把整段当作 reply，expenses 为空
     *
     * 这样即使模型偶尔不遵守 system prompt，用户也不会看到红色报错。
     */
    fun parse(raw: String): LlmParseResult {
        val trimmed = raw.trim()

        // 1) 直接尝试整段解析
        runCatching {
            return parseStrict(trimmed)
        }

        // 2) 尝试从中间抽取第一个 {...} JSON 块
        val extracted = extractFirstJsonObject(trimmed)
        if (extracted != null) {
            runCatching {
                return parseStrict(extracted)
            }
        }

        // 3) 完全失败 → 当作纯文本闲聊回复
        return LlmParseResult(reply = trimmed.ifBlank { "（空回复）" }, expenses = emptyList())
    }

    private fun parseStrict(jsonText: String): LlmParseResult {
        val payload = json.decodeFromString(LlmExpensesPayload.serializer(), jsonText)
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
