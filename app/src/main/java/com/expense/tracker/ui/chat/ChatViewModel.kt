package com.expense.tracker.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expense.tracker.data.model.Category
import com.expense.tracker.data.prefs.UserPrefs
import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.expense.tracker.data.repo.ChatRepository
import com.expense.tracker.data.repo.ExpenseRepository
import kotlinx.coroutines.delay
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
    fun submitTemplate(amount: Double, note: String = "") {
        if (amount <= 0.0) return
        val cat = Category.byIdOrOther(internal.value.selectedCategoryId)
        val trimmedNote = note.trim()
        viewModelScope.launch {
            internal.update { it.copy(sending = true) }
            val noteSuffix = if (trimmedNote.isNotEmpty()) " · $trimmedNote" else ""
            chatRepo.appendUser("[模板] ${cat.emoji} ${cat.displayName} ¥${"%.2f".format(amount)}$noteSuffix")
            val expenseId = expenseRepo.add(
                amount = amount,
                categoryId = cat.id,
                note = trimmedNote,
                occurredAt = System.currentTimeMillis(),
            )
            chatRepo.appendAssistant(
                text = "✅ 已记录 · ${cat.emoji} ${cat.displayName} ¥${"%.2f".format(amount)}$noteSuffix",
                relatedExpenseId = expenseId,
            )
            internal.update { it.copy(sending = false) }
        }
    }

    /** 自由文本输入：仅在 🧠 开启时调用 LLM；关闭时提示用户改用模板。 */
    fun submitFreeText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            internal.update { it.copy(sending = true) }
            chatRepo.appendUser(trimmed)
            val prefs = userPrefs.snapshot.first()
            if (!prefs.llmEnabled) {
                chatRepo.appendAssistant("⚠️ 大模型已关闭，请点亮 🧠 后再用自然语言记账，或直接选下方分类标签。")
                internal.update { it.copy(sending = false) }
                return@launch
            }

            // 思考态 — UI 显示三点跳动动画
            internal.update { it.copy(thinking = true) }
            val result = runCatching { llmHandler(trimmed, prefs) }
                .getOrElse { LlmResult.Error("调用失败：${it.message ?: "未知错误"}") }
            // LLM 已返回，关闭思考态，开始流式打字
            internal.update { it.copy(thinking = false) }

            when (result) {
                is LlmResult.Ok -> {
                    streamReply(result.replyText)
                    // 先清流式状态，再写 DB；这样 DB 推回的正式 bubble 替换流式占位时不会重影
                    internal.update { it.copy(streamingText = null) }
                    chatRepo.appendAssistant(result.replyText, relatedExpenseId = result.expenseId)
                }
                is LlmResult.Error -> {
                    val errText = "⚠️ ${result.message}"
                    streamReply(errText)
                    internal.update { it.copy(streamingText = null) }
                    chatRepo.appendAssistant(errText)
                }
            }
            internal.update { it.copy(sending = false) }
        }
    }

    /**
     * 逐字"打字"显示 LLM 回复 — 25ms/字符；空字符串直接跳过。
     * 完成后调用方负责清理 streamingText 状态（写入 DB 后清空）。
     */
    private suspend fun streamReply(fullText: String) {
        if (fullText.isEmpty()) return
        val sb = StringBuilder()
        fullText.forEach { ch ->
            sb.append(ch)
            internal.update { it.copy(streamingText = sb.toString()) }
            // 标点稍微停顿更有节奏感
            val perCharMs = when (ch) {
                '。', '！', '？', '.', '!', '?' -> 80L
                '，', '、', ',', ';' -> 50L
                else -> 22L
            }
            delay(perCharMs)
        }
    }
}
