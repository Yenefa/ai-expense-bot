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
}
