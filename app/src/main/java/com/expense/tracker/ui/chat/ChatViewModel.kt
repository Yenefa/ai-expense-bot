package com.expense.tracker.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expense.tracker.data.model.Category
import com.expense.tracker.data.model.Money
import com.expense.tracker.data.prefs.UserPrefs
import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.expense.tracker.data.repo.ChatRepository
import com.expense.tracker.data.repo.ExpenseRepository
import com.expense.tracker.llm.MutationPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** LLM 调用接口 — 输入用户文本 + 当前 prefs，返回助手要展示的文本。Part 3 任务再实现真实版本。 */
typealias LlmHandler = suspend (text: String, prefs: UserPrefsSnapshot) -> LlmResult
typealias LlmConfirmationHandler = suspend (token: String) -> LlmResult
typealias LlmCancellationHandler = (token: String) -> Unit

sealed interface LlmResult {
    data class Ok(
        val replyText: String,
        val expenseIds: List<Long> = emptyList(),
        /** 账目变更与助手批次消息已在同一数据库事务中提交。 */
        val assistantPersisted: Boolean = false,
    ) : LlmResult
    data class ConfirmationRequired(
        val token: String,
        val preview: MutationPreview,
    ) : LlmResult

    /** Memory v1：长期记忆提案待人类确认（确认前不写任何持久化存储）。 */
    data class MemoryProposalRequired(
        val token: String,
        val summary: String,
        val typeLabel: String,
    ) : LlmResult

    data class Error(val message: String) : LlmResult
}

class ChatViewModel(
    private val expenseRepo: ExpenseRepository,
    private val chatRepo: ChatRepository,
    private val userPrefs: UserPrefs,
    private val llmHandler: LlmHandler,
    private val confirmationHandler: LlmConfirmationHandler = { LlmResult.Error("确认已失效") },
    private val cancellationHandler: LlmCancellationHandler = {},
    private val memoryConfirmationHandler: LlmConfirmationHandler = { LlmResult.Error("确认已失效") },
    private val memoryCancellationHandler: LlmCancellationHandler = {},
    private val budgetWarningProvider: suspend () -> String? = { null },
    /** 主动洞察：规则判定 + 硬约束在 provider 内部完成；这里只负责展示文案。 */
    private val proactiveInsightProvider: suspend () -> com.expense.tracker.proactive.ProactiveAlert? = { null },
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

    /** 输入框草稿 — 由 ViewModel 持有，跨页面切换不丢。 */
    fun updateInputDraft(text: String) {
        internal.update { it.copy(inputDraft = text) }
    }

    fun toggleLlm() {
        viewModelScope.launch { userPrefs.setLlmEnabled(!internal.value.llmEnabled) }
    }

    /** 关闭 LLM 时的快速记账。 */
    fun submitTemplate(amountCents: Long, note: String = "") {
        if (amountCents <= 0L) return
        if (internal.value.sending || internal.value.pendingConfirmation != null || internal.value.pendingMemory != null) return
        val cat = Category.byIdOrOther(internal.value.selectedCategoryId)
        val trimmedNote = note.trim()
        internal.update { it.copy(sending = true) }
        viewModelScope.launch {
            val noteSuffix = if (trimmedNote.isNotEmpty()) " · $trimmedNote" else ""
            val formattedAmount = Money.formatYuan(amountCents)
            chatRepo.appendUser("[模板] ${cat.emoji} ${cat.displayName} ¥$formattedAmount$noteSuffix")
            val expenseId = expenseRepo.addCents(
                amountCents = amountCents,
                categoryId = cat.id,
                note = trimmedNote,
                occurredAt = System.currentTimeMillis(),
            )
            chatRepo.appendAssistant(
                text = "✅ 已记录 · ${cat.emoji} ${cat.displayName} ¥$formattedAmount$noteSuffix",
                relatedExpenseId = expenseId,
            )
            runCatching { budgetWarningProvider() }.getOrNull()?.let { warning ->
                chatRepo.appendAssistant(text = warning)
            }
            maybeEmitProactive()
            internal.update { it.copy(sending = false) }
        }
    }

    /** 自由文本输入：仅在 🧠 开启时调用 LLM；关闭时提示用户改用模板。 */
    fun submitFreeText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (internal.value.sending || internal.value.pendingConfirmation != null || internal.value.pendingMemory != null) return
        // 在启动协程前同步占用发送门，避免同一帧内的连续点击启动两个请求。
        internal.update { it.copy(sending = true, inputDraft = "") }
        viewModelScope.launch {
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
                .getOrElse {
                    if (it is CancellationException) throw it
                    LlmResult.Error("调用失败：${it.message ?: "未知错误"}")
                }
            // LLM 已返回，关闭思考态，开始流式打字
            internal.update { it.copy(thinking = false) }

            when (result) {
                is LlmResult.Ok -> {
                    appendFinalResult(result)
                }
                is LlmResult.ConfirmationRequired -> {
                    internal.update { it.copy(pendingConfirmation = result) }
                }
                is LlmResult.MemoryProposalRequired -> {
                    internal.update { it.copy(pendingMemory = result) }
                }
                is LlmResult.Error -> {
                    appendError(result)
                }
            }
            internal.update { it.copy(sending = false) }
        }
    }

    /** 确认写入长期记忆（唯一持久化入口由确认触发）。 */
    fun confirmPendingMemory() {
        val memory = internal.value.pendingMemory ?: return
        internal.update { it.copy(pendingMemory = null, sending = true, thinking = true) }
        viewModelScope.launch {
            val result = runCatching { memoryConfirmationHandler(memory.token) }
                .getOrElse {
                    if (it is CancellationException) throw it
                    LlmResult.Error(it.message ?: "保存失败，本次未记住。")
                }
            internal.update { it.copy(thinking = false) }
            when (result) {
                is LlmResult.Ok -> appendFinalResult(result)
                is LlmResult.Error -> appendError(result)
                else -> appendError(LlmResult.Error("确认状态异常，本次未记住。"))
            }
            internal.update { it.copy(sending = false) }
        }
    }

    /** 取消记忆提案：丢弃 token，不写任何存储。 */
    fun cancelPendingMemory() {
        val memory = internal.value.pendingMemory ?: return
        memoryCancellationHandler(memory.token)
        internal.update { it.copy(pendingMemory = null) }
        viewModelScope.launch { chatRepo.appendAssistant("已取消，这条信息不会被记住。") }
    }

    fun confirmPending() {
        val confirmation = internal.value.pendingConfirmation ?: return
        internal.update { it.copy(pendingConfirmation = null, sending = true, thinking = true) }
        viewModelScope.launch {
            val result = runCatching { confirmationHandler(confirmation.token) }
                .getOrElse {
                    if (it is CancellationException) throw it
                    LlmResult.Error(it.message ?: "执行失败，本次未修改。")
                }
            internal.update { it.copy(thinking = false) }
            when (result) {
                is LlmResult.Ok -> appendFinalResult(result)
                is LlmResult.Error -> appendError(result)
                else -> appendError(LlmResult.Error("确认状态异常，本次未修改。"))
            }
            internal.update { it.copy(sending = false) }
        }
    }

    fun cancelPending() {
        val confirmation = internal.value.pendingConfirmation ?: return
        cancellationHandler(confirmation.token)
        internal.update { it.copy(pendingConfirmation = null) }
        viewModelScope.launch { chatRepo.appendAssistant("已取消，本次未修改任何账目。") }
    }

    private suspend fun appendFinalResult(result: LlmResult.Ok) {
        if (result.assistantPersisted) {
            // 正式消息已与账目事务一起写入，避免再写一次或显示重复的流式气泡。
            internal.update { it.copy(streamingText = null) }
            if (result.expenseIds.isNotEmpty()) maybeEmitProactive()
            return
        }
        streamReply(result.replyText)
        // 先清流式状态，再写 DB；这样 DB 推回的正式 bubble 替换流式占位时不会重影
        internal.update { it.copy(streamingText = null) }
        chatRepo.appendAssistant(
            text = result.replyText,
            relatedExpenseIds = result.expenseIds,
        )
        if (result.expenseIds.isNotEmpty()) maybeEmitProactive()
    }

    /** 主动提醒只以规则结果为输入；此处不调用 LLM、不判断异常。 */
    private suspend fun maybeEmitProactive() {
        val alert = runCatching { proactiveInsightProvider() }.getOrNull() ?: return
        chatRepo.appendAssistant("🔔 ${alert.copy}")
    }

    private suspend fun appendError(result: LlmResult.Error) {
        val errText = "⚠️ ${result.message}"
        streamReply(errText)
        internal.update { it.copy(streamingText = null) }
        chatRepo.appendAssistant(errText)
    }

    /**
     * 逐字"打字"显示 LLM 回复 — 8ms/字符；空字符串直接跳过。
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
                '。', '！', '？', '.', '!', '?' -> 40L
                '，', '、', ',', ';' -> 25L
                else -> 8L
            }
            delay(perCharMs)
        }
    }
}
