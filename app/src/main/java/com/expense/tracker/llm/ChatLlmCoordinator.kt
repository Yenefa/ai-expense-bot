package com.expense.tracker.llm

import com.expense.tracker.data.model.Category
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
    structuredRequest: Boolean,
) -> String

/**
 * Agent 层注入的单轮上下文：查询轮把本地工具结果拼进 system prompt，
 * 并放宽"虚假记账话术"替换（查询回复合法地包含"已记录 X 笔"等词）。
 */
data class ChatTurnContext(
    val systemPromptSuffix: String = "",
    val suppressMutationGuard: Boolean = false,
    /**
     * 查询轮只读开关。false 时本轮的 expenses/actions 在进入变更计划器之前就被丢弃，
     * 代码层保证模型输出任何内容都无法修改数据库（不依赖提示词自觉）。
     */
    val allowMutations: Boolean = true,
    /**
     * 本轮是否要求模型输出结构化 JSON。true 时对 Qwen 思考模型显式关闭思考
     * （见 [LlmThinkingPolicy]），普通 CHAT 保持 null 沿用供应商默认。
     */
    val structuredRequest: Boolean = false,
    /**
     * 已授权商户别名（CLASSIFICATION 读权限）：note 命中商户时覆盖模型分类。
     * 只在该轮明确授权时传入；默认空表示不读取任何记忆。
     */
    val classificationAliases: Map<String, String> = emptyMap(),
)

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

    suspend fun submit(
        text: String,
        prefs: UserPrefsSnapshot,
        turnContext: ChatTurnContext = ChatTurnContext(),
    ): LlmResult = runCatching {
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
            LlmPrompt.systemPrompt(now, contextRecords, lastBatchIds) + turnContext.systemPromptSuffix.withLeadingBreak(),
            history,
            turnContext.structuredRequest,
        )
        val parsed = LlmResponseParser.parse(raw)
        PrivacySafeLog.llmResponseParsed(parsed.expenses.size, parsed.actions.size)

        // 非 MUTATION 路径只读（查询轮 + v3.9.2 CHAT write firewall）：
        // 即使模型被诱导/幻觉返回 expenses/actions，也在进入变更计划器之前丢弃。
        if (!turnContext.allowMutations) {
            if (parsed.expenses.isNotEmpty() || parsed.actions.isNotEmpty()) {
                PrivacySafeLog.llmMutationsBlocked(parsed.expenses.size, parsed.actions.size)
            }
            return@runCatching LlmResult.Ok(
                replyText = if (turnContext.suppressMutationGuard) {
                    parsed.reply
                } else {
                    parsed.reply.withoutFalseMutationClaim()
                },
                expenseIds = emptyList(),
            )
        }

        // Memory 分类读权限的应用：用户确认过的商户别名覆盖模型分类。
        val parsedWithAliases = applyClassificationAliases(parsed, turnContext.classificationAliases)
        val sourceExpenseHints = interpreted.expenseHints
            .takeIf { parsedWithAliases.expenses.isNotEmpty() && interpreted.hasCompleteExpenseHints }
            .orEmpty()
        val plan = LlmMutationPlanner.create(
            result = parsedWithAliases,
            nowMillis = now,
            availableRecords = contextRecords,
            lastBatchIds = lastBatchIds,
            currentText = requestText,
            targetDate = targetDate,
            sourceExpenseHints = sourceExpenseHints,
            zone = zone,
        )

        when {
            plan.preview.count == 0 -> LlmResult.Ok(
                replyText = if (turnContext.suppressMutationGuard) {
                    parsed.reply
                } else {
                    parsed.reply.withoutFalseMutationClaim()
                },
                expenseIds = emptyList(),
            )
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

    private fun applyClassificationAliases(
        result: LlmParseResult,
        aliases: Map<String, String>,
    ): LlmParseResult {
        if (aliases.isEmpty() || result.expenses.isEmpty()) return result
        return result.copy(
            expenses = result.expenses.map { expense ->
                val mapped = aliases.entries.firstOrNull { (merchant, _) ->
                    merchant.isNotBlank() && expense.note.contains(merchant)
                }?.value
                if (mapped != null && mapped != expense.categoryId && Category.byId(mapped) != null) {
                    expense.copy(categoryId = mapped)
                } else {
                    expense
                }
            },
        )
    }

    private fun String.withoutFalseMutationClaim(): String =
        if (FALSE_MUTATION_CLAIM.containsMatchIn(this)) {
            "没有检测到可执行的账目变更，本次未修改。"
        } else {
            this
        }

    private fun String.withLeadingBreak(): String =
        if (isBlank()) this else "\n$this"

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
                "这笔|那笔|它们|他们|这些|那些|刚才|上一批|全部|全都|都改|都删|" +
                // v3.9.3：更正语义只有在路由已放行（上一轮记账 + 最近账目）时才会走到这里。
                "记错了|说错了|搞错了|不对|补充",
        )
        private val FALSE_MUTATION_CLAIM = Regex(
            "(?:已|成功).{0,20}(?:记录|记账|修改|删除|移动|改到|改好)|已记\\s*\\d*\\s*笔",
        )

        private fun needsExistingRecordContext(text: String): Boolean =
            EXISTING_RECORD_INTENT.containsMatchIn(text)
    }
}
