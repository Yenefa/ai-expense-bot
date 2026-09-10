package com.expense.tracker.agent

import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.expense.tracker.llm.ChatLlmCoordinator
import com.expense.tracker.llm.ChatTurnContext
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
) {

    suspend fun submit(text: String, prefs: UserPrefsSnapshot): LlmResult {
        val decision = AgentRouter.route(text, nowProvider(), zone)
        return when {
            decision.route == AgentRoute.QUERY -> {
                onRouteResolved(AgentRoute.QUERY)
                submitQuery(text, prefs, decision)
            }
            decision.route == AgentRoute.MUTATION -> {
                onRouteResolved(AgentRoute.MUTATION)
                coordinator.submit(text, prefs, MUTATION_TURN_CONTEXT)
            }
            decision.route == AgentRoute.CHAT &&
                escalateIntent != null &&
                AgentRouter.needsEscalation(text) -> submitEscalated(text, prefs)
            else -> {
                onRouteResolved(AgentRoute.CHAT)
                coordinator.submit(text, prefs)
            }
        }
    }

    suspend fun confirm(token: String): LlmResult = coordinator.confirm(token)

    fun cancel(token: String): Boolean = coordinator.cancel(token)

    /** 升级路径：确认是分析意图才执行工具；升级失败或判为 record/chat 都回退原管线。 */
    private suspend fun submitEscalated(text: String, prefs: UserPrefsSnapshot): LlmResult {
        val escalation = runCatching { escalateIntent?.invoke(text) }.getOrNull()
        return when {
            escalation != null && escalation.isAnalysis && escalation.requiresTools -> {
                onRouteResolved(AgentRoute.QUERY)
                // 升级前是 CHAT 决策（时段/分类/预算都是空的），必须用原句重新解析，
                // 否则"我最近吃饭是不是花多了"会退化成"本月所有消费分析"。
                submitQuery(text, prefs, AgentRouter.queryDecision(text, nowProvider(), zone))
            }
            escalation?.intent == "record" -> {
                onRouteResolved(AgentRoute.MUTATION)
                coordinator.submit(text, prefs, MUTATION_TURN_CONTEXT)
            }
            else -> {
                onRouteResolved(AgentRoute.CHAT)
                coordinator.submit(text, prefs)
            }
        }
    }

    private suspend fun submitQuery(text: String, prefs: UserPrefsSnapshot, decision: AgentDecision): LlmResult {
        val now = nowProvider()
        val spec = decision.period ?: AgentPeriodResolver.defaultMonth(now, zone)
        val query = runCatching { AgentTools.queryExpenses(toolContext, spec, decision.categories, zone) }
            .getOrElse { error ->
                PrivacySafeLog.llmRequestFailed()
                return coordinator.submit(text, prefs)
            }
        val analyze = runCatching { AgentTools.analyzeExpenses(toolContext, spec, now, zone) }.getOrNull()
        val budget = if (decision.wantBudget) {
            runCatching { AgentTools.budgetStatus(toolContext, now, zone) }.getOrNull()
        } else {
            null
        }
        val suffix = AgentPrompts.toolResultBlock(query, budget, analyze) + AgentPrompts.queryDirective()
        return coordinator.submit(
            text = text,
            prefs = prefs,
            turnContext = ChatTurnContext(
                systemPromptSuffix = suffix,
                suppressMutationGuard = true,
                allowMutations = false,
                structuredRequest = true,
            ),
        )
    }

    private companion object {
        /** 记账/删改轮：要求结构化 JSON，Qwen 下显式关闭思考。 */
        val MUTATION_TURN_CONTEXT = ChatTurnContext(structuredRequest = true)
    }
}
