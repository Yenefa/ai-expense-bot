package com.expense.tracker.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// === Chat completion 请求 ===
@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMsg>,
    val temperature: Double = 0.0,
    @SerialName("response_format") val responseFormat: ResponseFormat? = ResponseFormat("json_object"),
)
@Serializable data class ChatMsg(val role: String, val content: String)
@Serializable data class ResponseFormat(val type: String)

// === Chat completion 响应（仅取需要的字段） ===
@Serializable
data class ChatCompletionResponse(val choices: List<Choice>)
@Serializable data class Choice(val message: ChatMsg)

// === LLM 业务返回 ===
@Serializable
data class LlmExpensesPayload(val expenses: List<LlmExpenseItem> = emptyList())

@Serializable
data class LlmExpenseItem(
    val amount: Double,
    val category: String,
    val note: String = "",
    @SerialName("occurred_at") val occurredAt: String? = null,
)
