package com.expense.tracker.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * v3.9.1：Qwen 结构化请求必须显式 `enable_thinking=false`（ExpenseBench：思考模式
 * 整笔全对 89.1% → 74.6%）；普通 CHAT 与其他供应商保持 null（字段缺省、沿用默认）。
 */
class LlmThinkingPolicyTest {

    @Test
    fun `Qwen 结构化请求显式关闭思考`() {
        assertThat(LlmThinkingPolicy.enableThinkingFor(structuredRequest = true, model = "qwen3.7-flash")).isFalse()
        assertThat(LlmThinkingPolicy.enableThinkingFor(structuredRequest = true, model = "qwen3.7-max-2026-06-08")).isFalse()
        assertThat(LlmThinkingPolicy.enableThinkingFor(structuredRequest = true, model = "QWEN3.7-PLUS")).isFalse()
    }

    @Test
    fun `Qwen 普通聊天保留供应商默认`() {
        assertThat(LlmThinkingPolicy.enableThinkingFor(structuredRequest = false, model = "qwen3.7-flash")).isNull()
    }

    @Test
    fun `非 Qwen 模型不发送思考字段`() {
        for (model in listOf("deepseek-chat", "gpt-4o-mini", "doubao-pro-32k", "hy3")) {
            assertThat(LlmThinkingPolicy.enableThinkingFor(structuredRequest = true, model = model)).isNull()
        }
    }
}
