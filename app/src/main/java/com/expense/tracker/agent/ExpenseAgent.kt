package com.expense.tracker.agent

import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.expense.tracker.llm.ChatLlmCoordinator
import com.expense.tracker.llm.ChatTurnContext
import com.expense.tracker.ui.chat.LlmResult
import com.expense.tracker.util.PrivacySafeLog
import java.time.ZoneId

/**
 * Expense Agent（v3.8）：在 ChatLlmCoordinator 之前加一层路由。
 *
 * 查询轮先在端侧执行确定性工具（query_expenses / get_budget_status），
 * 把可信数据注入 system prompt，LLM 只负责组织语言 —— 数字不可能被编造；
 * 记账与闲聊轮保持既有变更计划管线，行为不变。
 */
class ExpenseAgent(
    private val coordinator: ChatLlmCoordinator,
    private val toolContext: AgentToolContext,
    private val nowProvider: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    suspend fun submit(text: String, prefs: UserPrefsSnapshot): LlmResult {
        val decision = AgentRouter.route(text, nowProvider(), zone)
        return when (decision.route) {
            AgentRoute.MUTATION, AgentRoute.CHAT -> coordinator.submit(text, prefs)
            AgentRoute.QUERY -> submitQuery(text, prefs, decision)
        }
    }

    suspend fun confirm(token: String): LlmResult = coordinator.confirm(token)

    fun cancel(token: String): Boolean = coordinator.cancel(token)

    private suspend fun submitQuery(text: String, prefs: UserPrefsSnapshot, decision: AgentDecision): LlmResult {
        val now = nowProvider()
        val spec = decision.period ?: AgentPeriodResolver.defaultMonth(now, zone)
        val query = runCatching { AgentTools.queryExpenses(toolContext, spec, decision.categories, zone) }
            .getOrElse { error ->
                PrivacySafeLog.llmRequestFailed()
                return coordinator.submit(text, prefs)
            }
        val budget = if (decision.wantBudget) {
            runCatching { AgentTools.budgetStatus(toolContext, now, zone) }.getOrNull()
        } else {
            null
        }
        val suffix = AgentPrompts.toolResultBlock(query, budget) + AgentPrompts.queryDirective()
        return coordinator.submit(
            text = text,
            prefs = prefs,
            turnContext = ChatTurnContext(
                systemPromptSuffix = suffix,
                suppressMutationGuard = true,
            ),
        )
    }
}
