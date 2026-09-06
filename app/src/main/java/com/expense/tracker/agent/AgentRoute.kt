package com.expense.tracker.agent

import com.expense.tracker.llm.ExpenseTextInterpreter
import java.time.ZoneId

/** Agent 对一条用户消息的处理路径。 */
enum class AgentRoute {
    /** 记账 / 删改：走既有 LLM 变更计划管线。 */
    MUTATION,

    /** 查询 / 统计：先本地执行查询工具，再把结果注入 system prompt。 */
    QUERY,

    /** 闲聊：走既有管线，不注入工具结果。 */
    CHAT,
}

data class AgentDecision(
    val route: AgentRoute,
    val period: AgentPeriodSpec? = null,
    /** 用户提及的分类过滤，空集 = 不过滤。 */
    val categories: Set<String> = emptySet(),
    val wantBudget: Boolean = false,
)

/**
 * 意图路由（v3.8 Agent 层）：本地确定性规则，零成本、零延迟，
 * 决定消息走记账管线还是先执行查询工具。
 * 误判兜底：QUERY 轮的 LLM 仍可输出 expenses/actions，记账不会因路由丢失。
 */
object AgentRouter {

    fun route(text: String, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): AgentDecision {
        val normalized = text.trim()
        if (MUTATION_INTENT.containsMatchIn(normalized)) {
            return AgentDecision(AgentRoute.MUTATION)
        }
        val softQuery = QUERY_TOPIC.containsMatchIn(normalized) && QUESTION_TONE.containsMatchIn(normalized)
        if (STRONG_QUERY.containsMatchIn(normalized) || softQuery) {
            return AgentDecision(
                route = AgentRoute.QUERY,
                period = AgentPeriodResolver.resolve(normalized, nowMillis, zone)
                    ?: AgentPeriodResolver.defaultMonth(nowMillis, zone),
                categories = AgentCategories.resolve(normalized),
                wantBudget = BUDGET_INTENT.containsMatchIn(normalized),
            )
        }
        val interpretation = ExpenseTextInterpreter.interpret(normalized, nowMillis, zone)
        return if (interpretation.amountMentionCount > 0) {
            AgentDecision(AgentRoute.MUTATION)
        } else {
            AgentDecision(AgentRoute.CHAT)
        }
    }

    private val MUTATION_INTENT = Regex(
        "删除|删掉|删了|取消|不要了|改成|改为|修改|更改|调整|移到|挪到|改到|" +
            "这笔|那笔|它们|他们|这些|那些|刚才|上一批",
    )

    private val STRONG_QUERY = Regex(
        "多少|几笔|几次|一共|总共|总计|合计|统计|汇总|平均|排行|最常|占比|分布|趋势|" +
            "明细|清单|流水|花在哪|花在什么|消费记录|还剩|剩多少|超支|超预算|预算|对比|哪个多|哪些",
    )

    private val QUERY_TOPIC = Regex("花|消费|支出|用掉|开销")
    private val QUESTION_TONE = Regex("吗|呢|？|\\?")
    private val BUDGET_INTENT = Regex("预算|还剩|超支|超预算")
}
