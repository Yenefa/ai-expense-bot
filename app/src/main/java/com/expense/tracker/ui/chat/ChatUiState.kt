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
    /** 输入框草稿 — 由 ViewModel 持有，跨 ChatScreen 重组（包括从子页面返回）不丢失。 */
    val inputDraft: String = "",
    /** 多笔或删改操作的本地确认门；确认前数据库不会发生变化。 */
    val pendingConfirmation: LlmResult.ConfirmationRequired? = null,
    /** 长期记忆提案确认门（v4 Memory Governance）；确认前不写记忆。 */
    val pendingMemory: LlmResult.MemoryProposalRequired? = null,
)
