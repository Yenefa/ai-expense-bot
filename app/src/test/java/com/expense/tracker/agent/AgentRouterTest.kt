package com.expense.tracker.agent

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import org.junit.Test

class AgentRouterTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = localMillis(2026, 9, 6, 12, 0)

    private fun localMillis(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        java.time.LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `删除与修改意图走记账管线`() {
        assertThat(AgentRouter.route("那杯咖啡不要了", now, zone).route).isEqualTo(AgentRoute.MUTATION)
        assertThat(AgentRouter.route("把晚饭改成10块", now, zone).route).isEqualTo(AgentRoute.MUTATION)
        assertThat(AgentRouter.route("删除这笔", now, zone).route).isEqualTo(AgentRoute.MUTATION)
        assertThat(AgentRouter.route("刚才那批全部取消", now, zone).route).isEqualTo(AgentRoute.MUTATION)
    }

    @Test
    fun `指代词的纯查询句不再误入写路径`() {
        // v3.9.4 路由顺序修复：查询信号先于指代词/「刚才」判定，纯查询只读零风险
        assertThat(AgentRouter.route("这些一共花了多少", now, zone).route).isEqualTo(AgentRoute.QUERY)
        assertThat(AgentRouter.route("刚才那批多少钱", now, zone).route).isEqualTo(AgentRoute.QUERY)
    }

    @Test
    fun `指代词加显式写动词仍走写路径`() {
        assertThat(AgentRouter.route("把刚才那笔删了", now, zone).route).isEqualTo(AgentRoute.MUTATION)
        assertThat(AgentRouter.route("这笔改成20", now, zone).route).isEqualTo(AgentRoute.MUTATION)
    }

    @Test
    fun `带金额的口述走记账管线`() {
        assertThat(AgentRouter.route("打车花了23块5", now, zone).route).isEqualTo(AgentRoute.MUTATION)
        assertThat(AgentRouter.route("昨天午饭35块 咖啡18元", now, zone).route).isEqualTo(AgentRoute.MUTATION)
    }

    @Test
    fun `统计问题走查询并解析时段与分类`() {
        val decision = AgentRouter.route("这个月吃饭花了多少？", now, zone)
        assertThat(decision.route).isEqualTo(AgentRoute.QUERY)
        assertThat(decision.period?.label).contains("2026-09")
        assertThat(decision.categories).containsExactly("food")
    }

    @Test
    fun `上个月查询解析为八月`() {
        val decision = AgentRouter.route("上个月一共花了多少", now, zone)
        assertThat(decision.route).isEqualTo(AgentRoute.QUERY)
        assertThat(decision.period?.label).contains("2026-08")
    }

    @Test
    fun `交通分类查询`() {
        val decision = AgentRouter.route("上周打车花了多少钱", now, zone)
        assertThat(decision.route).isEqualTo(AgentRoute.QUERY)
        assertThat(decision.categories).containsExactly("transport")
        assertThat(decision.period?.label).contains("上周")
    }

    @Test
    fun `预算问题要求预算工具`() {
        val decision = AgentRouter.route("这个月预算还剩多少", now, zone)
        assertThat(decision.route).isEqualTo(AgentRoute.QUERY)
        assertThat(decision.wantBudget).isTrue()
    }

    @Test
    fun `分类不限时段查询`() {
        val decision = AgentRouter.route("咖啡一共花了多少", now, zone)
        assertThat(decision.route).isEqualTo(AgentRoute.QUERY)
        assertThat(decision.categories).contains("drink")
    }

    @Test
    fun `闲聊不触发查询`() {
        assertThat(AgentRouter.route("你好呀", now, zone).route).isEqualTo(AgentRoute.CHAT)
        assertThat(AgentRouter.route("谢谢你", now, zone).route).isEqualTo(AgentRoute.CHAT)
        assertThat(AgentRouter.route("今天天气怎么样", now, zone).route).isEqualTo(AgentRoute.CHAT)
    }

    @Test
    fun `记账句子即使带问号也不走查询`() {
        // 口述记账优先于软查询信号（花+吗）
        assertThat(AgentRouter.route("记一下今天午饭35块对吗", now, zone).route).isEqualTo(AgentRoute.MUTATION)
    }

    @Test
    fun `裸金额支出走记账管线`() {
        listOf(
            "午饭35",
            "今天咖啡18",
            "刚买了杯奶茶16",
            "再记一笔：打车23",
            "补记昨天的午饭35",
            "记账：晚饭45",
            "花了23",
        ).forEach { text ->
            assertThat(AgentRouter.route(text, now, zone).route).isEqualTo(AgentRoute.MUTATION)
        }
    }

    @Test
    fun `非支出语句走闲聊由写防火墙兜底`() {
        listOf(
            "我月薪8000",
            "年终奖3万到账了",
            "房租还是2500，没变",
            "信用卡还欠着12000",
            "昨天看到一双鞋，标价899",
            "想买那个3000的包，但太贵了",
            "同事说他花了2万买电脑",
            "我没有花35啊",
            "不是18，别记",
            "目标：每天控制在50以内",
            "计划下个月买台5000的电脑",
            "我还没买呢，先看看",
        ).forEach { text ->
            assertThat(AgentRouter.route(text, now, zone).route).isEqualTo(AgentRoute.CHAT)
        }
    }

    @Test
    fun `查询召回覆盖花哪与追问`() {
        assertThat(AgentRouter.route("我的钱都花哪了", now, zone).route).isEqualTo(AgentRoute.QUERY)
        assertThat(AgentRouter.route("上个月和这个月比，哪个花得多", now, zone).route).isEqualTo(AgentRoute.QUERY)
        val queryContext = ConversationActionContext(previousRoute = AgentRoute.QUERY)
        assertThat(AgentRouter.route("那上个月呢", now, zone, queryContext).route).isEqualTo(AgentRoute.QUERY)
        assertThat(AgentRouter.route("那这周呢", now, zone, queryContext).route).isEqualTo(AgentRoute.QUERY)
        assertThat(AgentRouter.route("再看下饮品", now, zone, queryContext).route).isEqualTo(AgentRoute.QUERY)
        // 回承是条件规则：没有上一轮 QUERY 时不是查询
        assertThat(AgentRouter.route("那上个月呢", now, zone).route).isEqualTo(AgentRoute.CHAT)
    }

    @Test
    fun `花钱分析问句直接走查询`() {
        assertThat(AgentRouter.route("我最近吃饭是不是花多了", now, zone).route).isEqualTo(AgentRoute.QUERY)
        assertThat(AgentRouter.route("我是不是乱花钱了", now, zone).route).isEqualTo(AgentRoute.QUERY)
    }

    @Test
    fun `条件更正上一轮记账且有最近账目才升为改账`() {
        val recordContext = ConversationActionContext(
            previousRoute = AgentRoute.MUTATION,
            recentExpenseIds = listOf(7L),
            previousMutationBatch = listOf(7L),
        )
        listOf(
            "记错了，是53",
            "上一条说错了，那杯瑞幸是16不是18",
            "不对，是32",
            "补充一下，其实是40",
        ).forEach { text ->
            assertThat(AgentRouter.route(text, now, zone, recordContext).route).isEqualTo(AgentRoute.MUTATION)
        }

        // 反例 1：上一轮记账但没有最近账目
        val noIds = recordContext.copy(recentExpenseIds = emptyList())
        assertThat(AgentRouter.route("记错了，是53", now, zone, noIds).route).isEqualTo(AgentRoute.CHAT)
        // 反例 2：上一轮是查询 —— "你这个分析不对" 不是改账
        val queryContext = ConversationActionContext(
            previousRoute = AgentRoute.QUERY,
            recentExpenseIds = listOf(7L),
        )
        assertThat(AgentRouter.route("你这个分析不对", now, zone, queryContext).route).isEqualTo(AgentRoute.CHAT)
        // 反例 3：无任何会话上下文
        assertThat(AgentRouter.route("不对，是32", now, zone).route).isEqualTo(AgentRoute.CHAT)
        assertThat(AgentRouter.route("补充一下，其实是40", now, zone).route).isEqualTo(AgentRoute.CHAT)
    }

    @Test
    fun `条件续记也是35仅在上一轮记账语境后生效`() {
        val recordContext = ConversationActionContext(previousRoute = AgentRoute.MUTATION)
        assertThat(AgentRouter.route("今天也是35", now, zone, recordContext).route).isEqualTo(AgentRoute.MUTATION)
        assertThat(AgentRouter.route("今天也是35", now, zone).route).isEqualTo(AgentRoute.CHAT)
        val queryContext = ConversationActionContext(previousRoute = AgentRoute.QUERY)
        assertThat(AgentRouter.route("今天也是35", now, zone, queryContext).route).isEqualTo(AgentRoute.CHAT)
    }

    @Test
    fun `Query回承只覆盖时段与分类`() {
        val queryContext = ConversationActionContext(
            previousRoute = AgentRoute.QUERY,
            previousQueryPeriod = AgentPeriodResolver.defaultMonth(now, zone),
            previousCategories = setOf("food"),
        )

        val periodOverride = AgentRouter.route("那上个月呢", now, zone, queryContext)
        assertThat(periodOverride.route).isEqualTo(AgentRoute.QUERY)
        assertThat(periodOverride.period?.label).contains("2026-08")

        val categoryOverride = AgentRouter.route("再看下饮品", now, zone, queryContext)
        assertThat(categoryOverride.route).isEqualTo(AgentRoute.QUERY)
        assertThat(categoryOverride.categories).containsExactly("drink")
        // period 继承上一轮（本月）
        assertThat(categoryOverride.period?.label).contains("2026-09")
    }
}
