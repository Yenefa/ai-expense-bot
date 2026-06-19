package com.expense.tracker.data.export

import com.expense.tracker.data.db.ChatMessageEntity
import com.expense.tracker.data.db.ExpenseEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 纯函数数据导出器 — 不持有任何 Android 依赖，方便单元测试。
 *
 * - JSON：版本号 + 时间戳 + 全部 expenses + 全部 chat_messages
 * - CSV：仅 expenses 表，便于 Excel 打开
 *
 * 不导出 LLM API Key（在 DataStore 里），那是敏感数据，用户应单独导出。
 */
object DataExporter {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    @Serializable
    data class ExportPayload(
        val version: String,
        val exportedAt: String,
        val expenses: List<ExpenseDto>,
        val chatMessages: List<ChatMessageDto>,
    )

    @Serializable
    data class ExpenseDto(
        val id: Long,
        val amount: Double,
        val categoryId: String,
        val note: String,
        val occurredAt: Long,
        val createdAt: Long,
    )

    @Serializable
    data class ChatMessageDto(
        val id: Long,
        val role: String,
        val content: String,
        val createdAt: Long,
        val relatedExpenseId: Long?,
    )

    fun toJson(
        expenses: List<ExpenseEntity>,
        chatMessages: List<ChatMessageEntity>,
        version: String,
        exportedAtIso: String,
    ): String {
        val payload = ExportPayload(
            version = version,
            exportedAt = exportedAtIso,
            expenses = expenses.map {
                ExpenseDto(
                    id = it.id,
                    amount = it.amount,
                    categoryId = it.categoryId,
                    note = it.note,
                    occurredAt = it.occurredAt,
                    createdAt = it.createdAt,
                )
            },
            chatMessages = chatMessages.map {
                ChatMessageDto(
                    id = it.id,
                    role = it.role,
                    content = it.content,
                    createdAt = it.createdAt,
                    relatedExpenseId = it.relatedExpenseId,
                )
            },
        )
        return json.encodeToString(payload)
    }

    /**
     * 导出 expenses 为 CSV。
     * - 头部：id,amount,categoryId,note,occurredAt,createdAt
     * - 转义规则：包含逗号/引号/换行的字段用双引号包裹，内部双引号写两个
     * - occurredAt / createdAt 直接用毫秒（用户在 Excel 里好处理，也避免时区问题）
     */
    fun toCsv(expenses: List<ExpenseEntity>): String {
        val header = "id,amount,categoryId,note,occurredAt,createdAt"
        val rows = expenses.joinToString("\n") { e ->
            listOf(
                e.id.toString(),
                e.amount.toString(),
                csvEscape(e.categoryId),
                csvEscape(e.note),
                e.occurredAt.toString(),
                e.createdAt.toString(),
            ).joinToString(",")
        }
        return if (rows.isEmpty()) header else "$header\n$rows"
    }

    private fun csvEscape(s: String): String {
        val needsQuote = s.contains(',') || s.contains('"') || s.contains('\n') || s.contains('\r')
        return if (!needsQuote) s
        else "\"" + s.replace("\"", "\"\"") + "\""
    }
}
