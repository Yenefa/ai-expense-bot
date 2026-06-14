package com.expense.tracker.ui.chat

import com.expense.tracker.data.db.ChatMessageEntity

data class ChatUiState(
    val messages: List<ChatMessageEntity> = emptyList(),
    val llmEnabled: Boolean = false,
    val selectedCategoryId: String = "food",
    val sending: Boolean = false,
)
