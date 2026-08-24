package com.expense.tracker.llm

import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.expense.tracker.data.repo.ChatRepository
import com.expense.tracker.data.repo.ExpenseRepository
import com.expense.tracker.data.repo.MutationApplyResult
import com.expense.tracker.ui.chat.LlmResult
import com.expense.tracker.util.PrivacySafeLog
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException

typealias ChatJsonRequest = suspend (
    text: String,
    prefs: UserPrefsSnapshot,
    systemPrompt: String,
    history: List<ChatMsg>,
) -> String

class ChatLlmCoordinator(
    private val expenseRepository: ExpenseRepository,
    private val chatRepository: ChatRepository,
    private val requestJson: ChatJsonRequest,
    private val applyPlan: suspend (LlmMutationPlan) -> MutationApplyResult,
    private val nowProvider: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val tokenProvider: () -> String = { UUID.randomUUID().toString() },
) {
    private data class PendingPlan(val plan: LlmMutationPlan, val createdAt: Long)

    private val pending = LinkedHashMap<String, PendingPlan>()

    suspend fun submit(text: String, prefs: UserPrefsSnapshot): LlmResult = runCatching {
        val now = nowProvider()
        val interpreted = ExpenseTextInterpreter.interpret(text, now, zone)
        val requestText = interpreted.normalizedText
        val messages = chatRepository.getRecent(MAX_HISTORY_MESSAGES)
        val priorMessages = messages.dropTrailingCurrentUser(text)
        val history = priorMessages.mapNotNull { message ->
            message.role.takeIf { it == "user" || it == "assistant" }
                ?.let { ChatMsg(it, message.content) }
        }
        val previousUserTexts = priorMessages
            .asReversed()
            .filter { it.role == "user" }
            .map { it.content }
        val targetDate = if (interpreted.hasMultipleDates) {
            null
        } else {
            ChineseDateResolver.resolveForMessage(
                currentText = requestText,
                previousUserTextsNewestFirst = previousUserTexts,
                nowMillis = now,
                zone = zone,
            )
        }
        val lastBatchIds = priorMessages
            .asReversed()
            .firstOrNull { it.role == "assistant" && it.relatedExpenseIds().isNotEmpty() }
            ?.relatedExpenseIds()
            .orEmpty()
        val active = if (needsExistingRecordContext(requestText)) {
            expenseRepository.getAllActiveOnce()
        } else {
            emptyList()
        }
        val contextRecords = buildList {
            addAll(active.sortedByDescending { it.occurredAt }.take(MAX_RECORD_CONTEXT))
            addAll(active.filter { it.id in lastBatchIds })
        }.distinctBy { it.id }

        PrivacySafeLog.llmRequestStarted(contextRecords.size)
        val raw = requestJson(
            requestText,
            prefs,
            LlmPrompt.systemPrompt(now, contextRecords, lastBatchIds),
            history,
        )
        val parsed = LlmResponseParser.parse(raw)
        PrivacySafeLog.llmResponseParsed(parsed.expenses.size, parsed.actions.size)
        val sourceExpenseHints = interpreted.expenseHints
            .takeIf { parsed.expenses.isNotEmpty() && interpreted.hasCompleteExpenseHints }
            .orEmpty()
        val plan = LlmMutationPlanner.create(
            result = parsed,
            nowMillis = now,
            availableRecords = contextRecords,
            lastBatchIds = lastBatchIds,
            currentText = requestText,
            targetDate = targetDate,
            sourceExpenseHints = sourceExpenseHints,
            zone = zone,
        )

        when {
            plan.preview.count == 0 -> LlmResult.Ok(parsed.reply.withoutFalseMutationClaim(), emptyList())
            plan.requiresConfirmation -> {
                val token = tokenProvider()
                synchronized(pending) {
                    discardExpiredLocked(now)
                    while (pending.size >= MAX_PENDING_PLANS) {
                        pending.remove(pending.keys.first())
                    }
                    pending[token] = PendingPlan(plan, now)
                }
                LlmResult.ConfirmationRequired(token, plan.preview)
            }
            else -> applyPlan(plan).toOk(parsed.reply)
        }
    }.getOrElse { error ->
        if (error is CancellationException) throw error
        PrivacySafeLog.llmRequestFailed()
        LlmResult.Error(error.message ?: "未知错误")
    }

    suspend fun confirm(token: String): LlmResult {
        val now = nowProvider()
        val pendingPlan = synchronized(pending) {
            discardExpiredLocked(now)
            pending.remove(token)
        } ?: return LlmResult.Error("确认已失效，请重新发起操作。")

        return runCatching { applyPlan(pendingPlan.plan).toOk(pendingPlan.plan.result.reply) }
            .getOrElse {
                if (it is CancellationException) throw it
                LlmResult.Error(it.message ?: "执行失败，本次未修改。")
            }
    }

    fun cancel(token: String): Boolean = synchronized(pending) {
        discardExpiredLocked(nowProvider())
        pending.remove(token) != null
    }

    private fun discardExpiredLocked(now: Long) {
        pending.entries.removeAll { now - it.value.createdAt > PENDING_TTL_MS }
    }

    private fun MutationApplyResult.toOk(reply: String): LlmResult.Ok =
        LlmResult.Ok(
            replyText = reply,
            expenseIds = affectedIds.distinct(),
            assistantPersisted = true,
        )

    private fun String.withoutFalseMutationClaim(): String =
        if (FALSE_MUTATION_CLAIM.containsMatchIn(this)) {
            "没有检测到可执行的账目变更，本次未修改。"
        } else {
            this
        }

    private fun List<com.expense.tracker.data.db.ChatMessageEntity>.dropTrailingCurrentUser(
        currentText: String,
    ): List<com.expense.tracker.data.db.ChatMessageEntity> =
        if (lastOrNull()?.let { it.role == "user" && it.content == currentText } == true) dropLast(1) else this

    companion object {
        private const val MAX_HISTORY_MESSAGES = 12
        // 删改上下文只需最近 30 条，避免把 200 条塞进提示词拖慢解析速度。
        private const val MAX_RECORD_CONTEXT = 30
        private const val MAX_PENDING_PLANS = 5
        private const val PENDING_TTL_MS = 10 * 60_000L
        private val EXISTING_RECORD_INTENT = Regex(
            "删除|删掉|取消|不要了|改成|改为|修改|更改|调整|移到|挪到|改到|" +
                "这笔|那笔|它们|他们|这些|那些|刚才|上一批|全部|全都|都改|都删",
        )
        private val FALSE_MUTATION_CLAIM = Regex(
            "(?:已|成功).{0,20}(?:记录|记账|修改|删除|移动|改到|改好)|已记\\s*\\d*\\s*笔",
        )

        private fun needsExistingRecordContext(text: String): Boolean =
            EXISTING_RECORD_INTENT.containsMatchIn(text)
    }
}
