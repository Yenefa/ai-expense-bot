package com.expense.tracker.agent

import com.expense.tracker.ui.chat.LlmResult

/**
 * 轻量会话行为上下文（v3.9.3）：只记录"当前会话最近一次行为"，**不是长期用户画像**。
 *
 * 用途（全部是条件触发，不新增裸关键词规则）：
 * - 更正语义：上一轮是记账且存在最近账目时，「记错了/说错了/不对/补充一下」才升为 MUTATION；
 * - 续记：「今天也是35」只在上一轮是明确消费语境时才算记账；
 * - Query 回承：「那上个月呢/那这周呢/再看下饮品」继承上一轮 QUERY intent，只覆盖 period/category。
 */
data class ConversationActionContext(
    val previousRoute: AgentRoute? = null,
    /** 最近记账批次涉及的账目 id（累积最近若干笔，更正类请求的门控条件）。 */
    val recentExpenseIds: List<Long> = emptyList(),
    /** 最近一次记账批次本身（预留：与近期累计区分）。 */
    val previousMutationBatch: List<Long> = emptyList(),
    /** 最近一次 QUERY 的时段，用于追问回承。 */
    val previousQueryPeriod: AgentPeriodSpec? = null,
    /** 最近一次 QUERY 的分类过滤，用于追问回承。 */
    val previousCategories: Set<String> = emptySet(),
) {
    /** 一轮结束后推进会话状态；只有 MUTATION 会更新账目 id 集合。 */
    fun recordTurn(
        route: AgentRoute,
        period: AgentPeriodSpec?,
        categories: Set<String>,
        result: LlmResult,
    ): ConversationActionContext = when (route) {
        AgentRoute.MUTATION -> {
            val affected = (result as? LlmResult.Ok)?.expenseIds.orEmpty()
            copy(
                previousRoute = AgentRoute.MUTATION,
                recentExpenseIds = (recentExpenseIds + affected).distinct().takeLast(MAX_RECENT_IDS),
                previousMutationBatch = affected.ifEmpty { previousMutationBatch },
            )
        }
        AgentRoute.QUERY -> copy(
            previousRoute = AgentRoute.QUERY,
            previousQueryPeriod = period ?: previousQueryPeriod,
            previousCategories = categories,
        )
        AgentRoute.CHAT -> copy(previousRoute = AgentRoute.CHAT)
    }

    companion object {
        const val MAX_RECENT_IDS = 20
    }
}
