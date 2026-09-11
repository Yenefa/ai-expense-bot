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
        assertThat(AgentRouter.route("那上个月呢", now, zone).route).isEqualTo(AgentRoute.QUERY)
        assertThat(AgentRouter.route("那这周呢", now, zone).route).isEqualTo(AgentRoute.QUERY)
        assertThat(AgentRouter.route("再看下饮品", now, zone).route).isEqualTo(AgentRoute.QUERY)
    }
}
