package com.expense.tracker.agent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IntentEscalationTest {

    @Test
    fun `解析纯净JSON`() {
        val result = IntentEscalationParser.parse(
            """{"intent":"analysis","requires_tools":true,"tool_candidates":["analyze_expenses"]}""",
        )
        assertThat(result).isNotNull()
        assertThat(result!!.isAnalysis).isTrue()
        assertThat(result.requiresTools).isTrue()
    }

    @Test
    fun `容忍JSON外的杂讯`() {
        val result = IntentEscalationParser.parse(
            """好的，我来判断：{"intent":"analysis","requires_tools":true} 以上。""",
        )
        assertThat(result).isNotNull()
        assertThat(result!!.isAnalysis).isTrue()
    }

    @Test
    fun `非法JSON返回null`() {
        assertThat(IntentEscalationParser.parse("这不是 JSON")).isNull()
        assertThat(IntentEscalationParser.parse("{broken")).isNull()
    }

    @Test
    fun `未知意图收敛为chat`() {
        val result = IntentEscalationParser.parse("""{"intent":"weather","requires_tools":true}""")
        assertThat(result).isNotNull()
        assertThat(result!!.intent).isEqualTo("chat")
        assertThat(result.requiresTools).isFalse()
    }

    @Test
    fun `升级触发判定`() {
        // 分析特征且不命中快路径 → 需要升级
        assertThat(AgentRouter.needsEscalation("我最近吃饭是不是有点多")).isTrue()
        assertThat(AgentRouter.needsEscalation("帮我看看最近消费情况")).isTrue()
        assertThat(AgentRouter.needsEscalation("花太多怎么办")).isTrue()
        // 含"花"+问号的句子已被快路径软规则抓住 → 不升级（省一次调用）
        assertThat(AgentRouter.needsEscalation("我最近是不是吃饭花多了，要不要减少外卖？")).isFalse()
        assertThat(AgentRouter.needsEscalation("这个月花了多少")).isFalse()
        assertThat(AgentRouter.needsEscalation("上个月一共花了多少")).isFalse()
        // 闲聊 / 记账 → 不升级
        assertThat(AgentRouter.needsEscalation("你好呀")).isFalse()
        assertThat(AgentRouter.needsEscalation("记一下午饭30块")).isFalse()
    }
}
