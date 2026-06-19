package com.expense.tracker.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// === Chat completion 请求 ===
@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMsg>,
    val temperature: Double = 0.0,
    @SerialName("response_format") val responseFormat: ResponseFormat? = null,
)
@Serializable data class ChatMsg(val role: String, val content: String)
@Serializable data class ResponseFormat(val type: String)

// === Chat completion 响应 ===
@Serializable
data class ChatCompletionResponse(val choices: List<Choice>)
@Serializable data class Choice(val message: ChatMsg)

// === LLM 业务返回（v2.9 扩展：新增 actions 字段） ===
@Serializable
data class LlmExpensesPayload(
    val reply: String = "",
    val expenses: List<LlmExpenseItem> = emptyList(),
    /** v2.9: LLM 可以对已有记录执行删除/修改操作 */
    val actions: List<LlmActionItem> = emptyList(),
)

@Serializable
data class LlmExpenseItem(
    val amount: Double,
    val category: String,
    val note: String = "",
    @SerialName("occurred_at") val occurredAt: String? = null,
)

@Serializable
data class LlmActionItem(
    /** "delete" | "update" */
    val action: String,
    /** 要操作的那笔 expense id（LLM 需从我们提供的最近记录列表中选） */
    @SerialName("expense_id") val expenseId: Long,
    /** update 时的新值（delete 时忽略） */
    val amount: Double? = null,
    val category: String? = null,
    val note: String? = null,
    @SerialName("occurred_at") val occurredAt: String? = null,
)

// === 智核分析返回 ===
@Serializable
data class AnalyticsInsightsPayload(val insights: List<String> = emptyList())

// === 解析后的动作 ===
sealed interface ParsedAction {
    /** 新增记账（传统流程） */
    data class Add(val amount: Double, val categoryId: String, val note: String, val occurredAtMillis: Long?) : ParsedAction
    /** 删除指定 expense */
    data class Delete(val expenseId: Long) : ParsedAction
    /** 更新指定 expense 的字段 */
    data class Update(val expenseId: Long, val amount: Double?, val categoryId: String?, val note: String?, val occurredAtMillis: Long?) : ParsedAction
}
