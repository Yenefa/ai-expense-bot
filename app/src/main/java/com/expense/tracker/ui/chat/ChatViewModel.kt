package com.expense.tracker.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expense.tracker.data.model.Category
import com.expense.tracker.data.prefs.UserPrefs
import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.expense.tracker.data.repo.ChatRepository
import com.expense.tracker.data.repo.ExpenseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** LLM 调用接口 — 输入用户文本 + 当前 prefs，返回助手要展示的文本。Part 3 任务再实现真实版本。 */
typealias LlmHandler = suspend (text: String, prefs: UserPrefsSnapshot) -> LlmResult

sealed interface LlmResult {
    data class Ok(val replyText: String, val expenseId: Long?) : LlmResult
    data class Error(val message: String) : LlmResult
}

class ChatViewModel(
    private val expenseRepo: ExpenseRepository,
    private val chatRepo: ChatRepository,
    private val userPrefs: UserPrefs,
    private val llmHandler: LlmHandler,
) : ViewModel() {

    private val internal = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = internal.asStateFlow()

    init {
        viewModelScope.launch {
            combine(chatRepo.observeAll(), userPrefs.snapshot) { msgs, p ->
                msgs to p.llmEnabled
            }.collect { (msgs, enabled) ->
                internal.update { it.copy(messages = msgs, llmEnabled = enabled) }
            }
        }
    }

    fun selectCategory(id: String) {
        if (Category.byId(id) == null) return
        internal.update { it.copy(selectedCategoryId = id) }
    }

    fun toggleLlm() {
        viewModelScope.launch { userPrefs.setLlmEnabled(!internal.value.llmEnabled) }
    }

    /** 关闭 LLM 时的快速记账。 */
    fun submitTemplate(amount: Double) {
        if (amount <= 0.0) return
        val cat = Category.byIdOrOther(internal.value.selectedCategoryId)
        viewModelScope.launch {
            internal.update { it.copy(sending = true) }
            chatRepo.appendUser("[模板] ${cat.emoji} ${cat.displayName} ¥${"%.2f".format(amount)}")
            val expenseId = expenseRepo.add(
                amount = amount,
                categoryId = cat.id,
                note = "",
                occurredAt = System.currentTimeMillis(),
            )
            chatRepo.appendAssistant(
                text = "✅ 已记录 · ${cat.emoji} ${cat.displayName} ¥${"%.2f".format(amount)}",
                relatedExpenseId = expenseId,
            )
            internal.update { it.copy(sending = false) }
        }
    }

    /** 开启 LLM 时的自由文本输入。 */
    fun submitFreeText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            internal.update { it.copy(sending = true) }
            chatRepo.appendUser(trimmed)
            val prefs = userPrefs.snapshot.first()
            val result = runCatching { llmHandler(trimmed, prefs) }
                .getOrElse { LlmResult.Error("调用失败：${it.message ?: "未知错误"}") }
            when (result) {
                is LlmResult.Ok -> chatRepo.appendAssistant(result.replyText, relatedExpenseId = result.expenseId)
                is LlmResult.Error -> chatRepo.appendAssistant("⚠️ ${result.message}")
            }
            internal.update { it.copy(sending = false) }
        }
    }
}
