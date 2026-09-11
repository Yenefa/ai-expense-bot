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
 * 意图路由（v3.8 Agent 层，v3.9.2 加固）：本地确定性规则，零成本、零延迟，
 * 决定消息走记账管线还是查询/闲聊。
 *
 * v3.9.2 写路径收敛：只有 MUTATION 可以写库；QUERY 与 CHAT 在协调器层被代码拒绝任何
 * expenses/actions（CHAT write firewall）。因此路由规则分三层：
 * 1. 明确的删改意图 → MUTATION；
 * 2. 查询（含后续询问）→ QUERY；
 * 3. 非支出语义（收入/负债/预算/估值/假设/否定/第三方…）→ CHAT（写防火墙保护）；
 *    其余带金额的支出语境 → MUTATION（含无元/块的裸金额）。
 */
object AgentRouter {

    fun route(text: String, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): AgentDecision {
        val normalized = text.trim()
        if (MUTATION_INTENT.containsMatchIn(normalized)) {
            return AgentDecision(AgentRoute.MUTATION)
        }
        val softQuery = QUERY_TOPIC.containsMatchIn(normalized) && QUESTION_TONE.containsMatchIn(normalized)
        if (STRONG_QUERY.containsMatchIn(normalized) || QUERY_FOLLOW_UP.containsMatchIn(normalized) || softQuery) {
            return queryDecision(normalized, nowMillis, zone)
        }
        // 非支出语义优先于裸金额识别：这些句子里的数字不是本笔消费。
        if (NON_EXPENSE_INTENT.containsMatchIn(normalized)) {
            return AgentDecision(AgentRoute.CHAT)
        }
        if (looksLikeBareExpense(normalized)) {
            return AgentDecision(AgentRoute.MUTATION)
        }
        val interpretation = ExpenseTextInterpreter.interpret(normalized, nowMillis, zone)
        return if (interpretation.amountMentionCount > 0) {
            AgentDecision(AgentRoute.MUTATION)
        } else {
            AgentDecision(AgentRoute.CHAT)
        }
    }

    /** 裸金额（无「元/块」后缀）+ 支出语境：午餐35、咖啡18、花了23 等。 */
    private fun looksLikeBareExpense(text: String): Boolean =
        BARE_NUMBER.containsMatchIn(text) &&
            (RECORD_VERB.containsMatchIn(text) || SPEND_VERB.containsMatchIn(text) || EXPENSE_TOPIC.containsMatchIn(text))

    /**
     * 从原句重新解析查询参数（时段 / 分类 / 预算）。Intent Escalation 把路由从 CHAT
     * 升级为分析时，必须重新跑这里，不能复用升级前的空 CHAT 决策。
     */
    fun queryDecision(text: String, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): AgentDecision {
        val normalized = text.trim()
        return AgentDecision(
            route = AgentRoute.QUERY,
            period = AgentPeriodResolver.resolve(normalized, nowMillis, zone)
                ?: AgentPeriodResolver.defaultMonth(nowMillis, zone),
            categories = AgentCategories.resolve(normalized),
            wantBudget = BUDGET_INTENT.containsMatchIn(normalized),
        )
    }

    private val MUTATION_INTENT = Regex(
        "删除|删掉|删了|取消|不要了|改成|改为|修改|更改|调整|移到|挪到|改到|记到|记成|" +
            "这笔|那笔|它们|他们|这些|那些|刚才|上一批",
    )

    private val STRONG_QUERY = Regex(
        "多少|几笔|几次|一共|总共|总计|合计|统计|汇总|平均|排行|最常|占比|分布|趋势|" +
            "明细|清单|流水|花在哪|花在什么|花哪|哪个花|消费记录|还剩|剩多少|超支|超预算|预算|对比|哪个多|哪些|" +
            "[再又]看下|再看看",
    )

    /** 回承上一轮查询的追问：「那上个月呢」「那这周呢」。 */
    private val QUERY_FOLLOW_UP = Regex("那(?:上|本|这|下)?(?:个)?(?:月|周|星期|年)呢")

    private val QUERY_TOPIC = Regex("花|消费|支出|用掉|开销")
    private val QUESTION_TONE = Regex("吗|呢|？|\\?")
    private val BUDGET_INTENT = Regex("预算|还剩|超支|超预算")

    /**
     * 非支出语义护栏（v3.9.2）：命中即走 CHAT，由 write firewall 保证不落库。
     * 只收编失败样本里反复出现的稳信号，避免误伤正常记账。
     */
    private val NON_EXPENSE_INTENT = Regex(
        "月薪|薪水|薪资|工资|奖金|年终奖|分红|到账|报销|退款|还款|借款|欠着|还欠|余额|存款|" +
            "预算|目标|计划|打算|如果|假如|要是|" +
            "值不值|值得|划算|太贵|好贵|真贵|贵了|标价|价格|降价|涨价|" +
            "没变|还是\\d|没有花|没花|不是\\d|别记|不要记|还没买|想买|考虑买|买不了|" +
            "我记得|看到|同事|朋友|他说|她说|网上说|群里|别人|听说|抢到|抢不到",
    )

    /** 记账动词：即使金额没有「元/块」也走结构化记账路径。 */
    private val RECORD_VERB = Regex("记账|记一笔|再记|补记|帮我记|记上|记一下|记两笔|记三笔")

    /** 花费动词。 */
    private val SPEND_VERB = Regex("花了|花掉|消费了|付了|支付了|买了|刚买|充值了|充了")

    /** 支出主题词。 */
    private val EXPENSE_TOPIC = Regex(
        "吃|饭|餐|外卖|宵夜|夜宵|咖啡|奶茶|饮料|水果|打车|出租|滴滴|地铁|公交|加油|停车|" +
            "高铁|火车|机票|房租|水电|物业|燃气|宽带|网费|话费|药|挂号|看病|课程|学费|" +
            "游戏|电影|门票|理发|剪发|充值|买|购|超市|日用品",
    )

    private val BARE_NUMBER = Regex("(?<![\\d.])-?\\d{1,7}(?:\\.\\d{1,2})?(?![\\d.])")

    /**
     * 升级触发判定：规则路由落到 CHAT，但文本带疑问/分析特征 → 发一次
     * Intent Escalation 轻量调用确认是否需要工具（deterministic first, probabilistic fallback）。
     */
    fun needsEscalation(text: String): Boolean {
        val normalized = text.trim()
        if (STRONG_QUERY.containsMatchIn(normalized)) return false
        if (QUERY_FOLLOW_UP.containsMatchIn(normalized)) return false
        if (QUERY_TOPIC.containsMatchIn(normalized) && QUESTION_TONE.containsMatchIn(normalized)) return false
        return ESCALATION_HINTS.containsMatchIn(normalized)
    }

    private val ESCALATION_HINTS = Regex(
        "是不是|要不要|该不该|怎么办|该怎么|正常吗|合理吗|值得吗|严重吗|怎么看|太多|花多|花太快|超了|超标|建议|帮我看看|帮我分析|分析一下",
    )
}
