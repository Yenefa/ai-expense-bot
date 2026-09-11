package com.expense.tracker.llm

import com.expense.tracker.data.model.Category
import com.expense.tracker.data.model.Money
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

class LlmParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

data class ParsedExpense(
    val amountCents: Long,
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

    /** 账目负载的顶层字段；候选块至少要含一个，否则视为无关 JSON 跳过。 */
    private val payloadFields = setOf("reply", "expenses", "actions")

    /**
     * 解析 LLM 输出。
     *
     * v2.9 扩展：除了 reply + expenses，还能解析 actions 字段。
     */
    fun parse(raw: String): LlmParseResult {
        val trimmed = raw.trim()

        // 1) 直接尝试整段解析
        runCatching { parseStrict(trimmed) }.getOrNull()?.let { return it }

        // 2) 尝试从中间抽取 {...} JSON 块。
        //    跳过不含任何账目字段的无关 JSON（如 {"foo":1}），逐个尝试；
        //    单个候选失败不放弃，记住第一个错误，全部失败后才抛出。
        val candidates = extractJsonObjects(trimmed)
        var firstError: Exception? = null
        for (candidate in candidates) {
            if (!candidate.containsLlmPayloadField()) continue
            try {
                return parseStrict(candidate)
            } catch (error: Exception) {
                if (firstError == null) firstError = error
            }
        }
        firstError?.let { error ->
            throw if (error is LlmParseException) error
            else LlmParseException("AI 返回的账目 JSON 无效，本次未执行。", error)
        }

        // 3) 完全失败 → 当作纯文本闲聊回复
        return LlmParseResult(reply = trimmed.ifBlank { "（空回复）" }, expenses = emptyList())
    }

    private fun String.containsLlmPayloadField(): Boolean {
        val obj = runCatching { json.parseToJsonElement(this) }.getOrNull() as? JsonObject ?: return false
        return payloadFields.any { it in obj }
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
            .map { item ->
                val amountCents = item.amount.toAmountCentsOrNull()
                    ?: throw LlmParseException("AI 返回了无效金额，本次未执行。")
                ParsedExpense(
                    amountCents = amountCents,
                    categoryId = Category.byId(item.category)?.id ?: "other",
                    note = item.note,
                    occurredAtMillis = item.occurredAt?.let { occurredAt ->
                        parseOccurredAt(occurredAt)
                            ?: throw LlmParseException("无法解析交易时间：$occurredAt")
                    },
                )
            }

        // 解析 actions（v2.9）
        val actions = payload.actions.map { a ->
            if (a.expenseId <= 0L) {
                throw LlmParseException("AI 返回了无效账目 ID，本次未执行。")
            }
            when (a.action) {
                "delete" -> ParsedAction.Delete(a.expenseId)
                "update" -> {
                    val categoryId = a.category?.let {
                        Category.byId(it)?.id
                            ?: throw LlmParseException("AI 返回了无效分类，本次未执行。")
                    }
                    val amountCents = a.amount?.let {
                        it.toAmountCentsOrNull()
                            ?: throw LlmParseException("AI 返回了无效金额，本次未执行。")
                    }
                    ParsedAction.Update(
                        expenseId = a.expenseId,
                        amountCents = amountCents,
                        categoryId = categoryId,
                        note = a.note,
                        occurredAtMillis = a.occurredAt?.let { occurredAt ->
                            parseOccurredAt(occurredAt)
                                ?: throw LlmParseException("无法解析交易时间：$occurredAt")
                        },
                    )
                }
                else -> throw LlmParseException("AI 返回了不支持的操作，本次未执行。")
            }
        }

        return LlmParseResult(reply = reply, expenses = parsed, actions = actions)
    }

    /**
     * OCR 账单导入的容错入口：一张截图可能混入小计、优惠等坏行，只跳过坏行并保留其他有效交易。
     * 聊天删改绝不能走这里；聊天必须使用 [parse] 的整批严格校验。
     */
    internal fun parseBillImportJsonObject(jsonText: String): LlmParseResult {
        val payload = json.decodeFromString(LlmExpensesPayload.serializer(), jsonText)
        val expenses = payload.expenses.mapNotNull { item ->
            val amountCents = item.amount.toAmountCentsOrNull() ?: return@mapNotNull null
            val occurredAtMillis = item.occurredAt?.let { parseOccurredAt(it) ?: return@mapNotNull null }
            ParsedExpense(
                amountCents = amountCents,
                categoryId = Category.byId(item.category)?.id ?: "other",
                note = item.note,
                occurredAtMillis = occurredAtMillis,
            )
        }
        return LlmParseResult(
            reply = payload.reply.ifBlank { "识别完成" },
            expenses = expenses,
        )
    }

    private fun JsonElement.toAmountCentsOrNull(): Long? {
        val primitive = this as? JsonPrimitive ?: return null
        return runCatching { Money.parseYuanToCents(primitive.content) }.getOrNull()
    }

    /**
     * 在文本中找第一个 {...} 平衡 JSON 块（粗糙但够用：考虑字符串内的转义/引号）。
     * 找不到返回 null。
     */
    internal fun extractFirstJsonObject(text: String): String? = extractJsonObjects(text).firstOrNull()

    /** 返回文本中所有顶层、括号平衡的 JSON 对象。 */
    internal fun extractJsonObjects(text: String): List<String> {
        val objects = mutableListOf<String>()
        var start = -1
        var depth = 0
        var inString = false
        var escape = false
        for (i in text.indices) {
            val c = text[i]
            if (escape) { escape = false; continue }
            if (inString && c == '\\') { escape = true; continue }
            if (c == '"') { inString = !inString; continue }
            if (inString) continue
            when (c) {
                '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                '}' -> {
                    if (depth == 0) continue
                    depth--
                    if (depth == 0 && start >= 0) {
                        objects += text.substring(start, i + 1)
                        start = -1
                    }
                }
            }
        }
        return objects
    }

    private fun parseOccurredAt(iso: String): Long? =
        runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching {
                LocalDateTime.parse(iso)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrNull()
            ?: runCatching {
                // v3.9.2：模型可能只回日期（"2026-09-05"），按本地 00:00 落到当天，而不是整批拒绝。
                LocalDate.parse(iso).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrNull()
}
