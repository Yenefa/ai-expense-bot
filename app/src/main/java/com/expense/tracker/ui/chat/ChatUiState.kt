package com.expense.tracker.ui.chat

import com.expense.tracker.data.db.ChatMessageEntity

data class ChatUiState(
    val messages: List<ChatMessageEntity> = emptyList(),
    val llmEnabled: Boolean = false,
    val selectedCategoryId: String = "food",
    val sending: Boolean = false,
    /** LLM 正在思考（请求已发出但尚未收到回复） — UI 显示三点跳动动画。 */
    val thinking: Boolean = false,
    /** LLM 回复正在被逐字"打字"展示；非空时 UI 显示一个临时 assistant 气泡。 */
    val streamingText: String? = null,
)
