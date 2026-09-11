package com.expense.tracker.agent

import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.expense.tracker.llm.ChatLlmCoordinator
import com.expense.tracker.llm.ChatTurnContext
import com.expense.tracker.memory.MemoryFact
import com.expense.tracker.memory.MemoryGovernor
import com.expense.tracker.memory.MemoryReadPolicy
import com.expense.tracker.memory.MemoryReadScope
import com.expense.tracker.memory.MemoryType
import com.expense.tracker.ui.chat.LlmResult
import com.expense.tracker.util.PrivacySafeLog
import java.time.ZoneId

/**
 * Expense Agent（v3.8+）：在 ChatLlmCoordinator 之前加一层两级路由。
 *
 * 读路径（查询/分析）：先在端侧执行确定性工具（query_expenses / analyze_expenses /
 * get_budget_status），把可信数据注入 system prompt，LLM 只负责组织语言；
 * 规则快路径不命中但文本带分析特征时，再走一次轻量 Intent Escalation 调用确认。
 * 写路径（记账/删改）：保持既有变更计划管线与确认门，行为不变。
 *
 * v3.9.3：持有「当前会话最近一次行为」的轻量上下文（非长期画像），用于条件更正、
 * 续记与 Query 回承；每轮结束后推进。
 */
class ExpenseAgent(
    private val coordinator: ChatLlmCoordinator,
    private val toolContext: AgentToolContext,
    /** Intent Escalation 调用器：返回 null 表示升级失败/未启用，回退聊天路径。 */
    private val escalateIntent: (suspend (String) -> IntentEscalation?)? = null,
    private val nowProvider: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
    /** Bench/监控观测点：每轮最终生效路由（含升级结果），生产默认 no-op。 */
    private val onRouteResolved: (AgentRoute) -> Unit = {},
    /** 会话起点上下文（测试/未来会话恢复用），默认空。 */
    initialConversationContext: ConversationActionContext = ConversationActionContext(),
    /** 长期记忆治理器（Memory v1）；null = 不启用提案。 */
    private val memoryGovernor: MemoryGovernor? = null,
) {
    private var conversationContext: ConversationActionContext = initialConversationContext
    private var lastTurn: TurnMeta? = null

    private data class TurnMeta(
        val route: AgentRoute,
        val period: AgentPeriodSpec?,
        val categories: Set<String>,
    )

    suspend fun submit(text: String, prefs: UserPrefsSnapshot): LlmResult {
        // Memory v1：本地检测 + 类型校验后的提案优先于全部 LLM 路径。
        // 本轮不调用模型、不写账目；且确认前不写记忆（提案只挂起）。
        memoryGovernor?.propose(text)?.let { proposal ->
            lastTurn = null
            return LlmResult.MemoryProposalRequired(proposal.token, proposal.summary, proposal.typeLabel)
        }
        val profile = scopedProfile()
        val decision = AgentRouter.route(
            text = text,
            nowMillis = nowProvider(),
            zone = zone,
            context = conversationContext,
            knownMerchants = knownMerchants(profile),
        )
        lastTurn = null
        val result = when {
            decision.route == AgentRoute.QUERY -> {
                lastTurn = TurnMeta(AgentRoute.QUERY, decision.period, decision.categories)
                onRouteResolved(AgentRoute.QUERY)
                submitQuery(text, prefs, decision)
            }
            decision.route == AgentRoute.MUTATION -> {
                lastTurn = TurnMeta(AgentRoute.MUTATION, null, emptySet())
                onRouteResolved(AgentRoute.MUTATION)
                coordinator.submit(text, prefs, classificationContext(profile))
            }
            decision.route == AgentRoute.CHAT &&
                escalateIntent != null &&
                AgentRouter.needsEscalation(text) -> submitEscalated(text, prefs)
            else -> {
                lastTurn = TurnMeta(AgentRoute.CHAT, null, emptySet())
                onRouteResolved(AgentRoute.CHAT)
                coordinator.submit(text, prefs, CHAT_TURN_CONTEXT)
            }
        }
        lastTurn?.let { turn ->
            conversationContext = conversationContext.recordTurn(turn.route, turn.period, turn.categories, result)
        }
        return result
    }

    suspend fun confirm(token: String): LlmResult = coordinator.confirm(token)

    fun cancel(token: String): Boolean = coordinator.cancel(token)

    /** 升级路径：确认是分析意图才执行工具；升级失败或判为 record/chat 都回退原管线。 */
    private suspend fun submitEscalated(text: String, prefs: UserPrefsSnapshot): LlmResult {
        val escalation = runCatching { escalateIntent?.invoke(text) }.getOrNull()
        return when {
            escalation != null && escalation.isAnalysis && escalation.requiresTools -> {
                // 升级前是 CHAT 决策（时段/分类/预算都是空的），必须用原句重新解析，
                // 否则"我最近吃饭是不是花多了"会退化成"本月所有消费分析"。
                val decision = AgentRouter.queryDecision(text, nowProvider(), zone, conversationContext)
                lastTurn = TurnMeta(AgentRoute.QUERY, decision.period, decision.categories)
                onRouteResolved(AgentRoute.QUERY)
                submitQuery(text, prefs, decision)
            }
            escalation?.intent == "record" -> {
                lastTurn = TurnMeta(AgentRoute.MUTATION, null, emptySet())
                onRouteResolved(AgentRoute.MUTATION)
                coordinator.submit(text, prefs, classificationContext(scopedProfile()))
            }
            else -> {
                lastTurn = TurnMeta(AgentRoute.CHAT, null, emptySet())
                onRouteResolved(AgentRoute.CHAT)
                coordinator.submit(text, prefs, CHAT_TURN_CONTEXT)
            }
        }
    }

    private suspend fun submitQuery(text: String, prefs: UserPrefsSnapshot, decision: AgentDecision): LlmResult {
        val now = nowProvider()
        val spec = decision.period ?: AgentPeriodResolver.defaultMonth(now, zone)
        val query = runCatching { AgentTools.queryExpenses(toolContext, spec, decision.categories, zone) }
            .getOrElse { error ->
                PrivacySafeLog.llmRequestFailed()
                // 工具失败也保持只读：路由已判为 QUERY，此处绝不能回退到可写上下文。
                return coordinator.submit(text, prefs, QUERY_TURN_CONTEXT)
            }
        val analyze = runCatching { AgentTools.analyzeExpenses(toolContext, spec, now, zone) }.getOrNull()
        val budget = if (decision.wantBudget) {
            runCatching { AgentTools.budgetStatus(toolContext, now, zone) }.getOrNull()
        } else {
            null
        }
        // 财务分析读权限：仅 QUERY 轮注入 income/savings/preference；其他轮次不读。
        val memoryBlock = MemoryReadPolicy.promptBlock(MemoryReadScope.FINANCIAL_ANALYSIS, scopedProfile()).orEmpty()
        val suffix = AgentPrompts.toolResultBlock(query, budget, analyze) + memoryBlock + AgentPrompts.queryDirective()
        return coordinator.submit(
            text = text,
            prefs = prefs,
            turnContext = QUERY_TURN_CONTEXT.copy(systemPromptSuffix = suffix),
        )
    }

    private suspend fun scopedProfile(): List<MemoryFact> =
        runCatching { memoryGovernor?.snapshot() }.getOrNull().orEmpty()

    private fun knownMerchants(profile: List<MemoryFact>): Set<String> =
        profile.asSequence()
            .filter { it.type == MemoryType.MERCHANT_ALIAS }
            .mapNotNull { it.merchant?.takeIf { name -> name.isNotBlank() } }
            .toSet()

    /** 记账轮：CLASSIFICATION 读权限（只注入商户别名）。 */
    private fun classificationContext(profile: List<MemoryFact>): ChatTurnContext {
        val aliases = profile.asSequence()
            .filter { it.type == MemoryType.MERCHANT_ALIAS }
            .mapNotNull { fact ->
                fact.merchant?.takeIf { it.isNotBlank() }?.let { it to fact.categoryId.orEmpty() }
            }
            .toMap()
        return MUTATION_TURN_CONTEXT.copy(
            systemPromptSuffix = MemoryReadPolicy.promptBlock(MemoryReadScope.CLASSIFICATION, profile).orEmpty(),
            classificationAliases = aliases,
        )
    }

    private companion object {
        /** 记账/删改轮：要求结构化 JSON，Qwen 下显式关闭思考；唯一允许写库的路径。 */
        val MUTATION_TURN_CONTEXT = ChatTurnContext(structuredRequest = true)

        /** 查询轮：只读 + 允许"已记录 X 笔"式查询回复。 */
        val QUERY_TURN_CONTEXT = ChatTurnContext(
            suppressMutationGuard = true,
            allowMutations = false,
            structuredRequest = true,
        )

        /** 闲聊轮：v3.9.2 CHAT write firewall，代码层拒绝任何写操作。 */
        val CHAT_TURN_CONTEXT = ChatTurnContext(allowMutations = false)
    }
}
