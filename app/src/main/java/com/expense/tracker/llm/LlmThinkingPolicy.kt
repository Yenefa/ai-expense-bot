package com.expense.tracker.llm

/**
 * 结构化请求的思考模式策略。
 *
 * ExpenseBench v1 实测（2026-09-06）：Qwen3.7 系列打开思考模式后，长思维链挤占输出预算
 * 导致结构化 JSON 被截断，整笔全对 89.1% → 74.6%、笔数全对 100% → 92.5%。因此对
 * MUTATION / QUERY / Intent Escalation / 智核分析 / 账单导入这类要求 JSON 输出的结构化请求，
 * 必须在 Qwen 模型上显式发送 `enable_thinking=false`。
 *
 * 其他供应商（DeepSeek / 豆包 / OpenAI / 订阅代理 hy3）不识别该字段，返回 null
 * 保持"字段缺省、沿用供应商默认"的既有行为，避免未知参数导致请求被拒。
 */
object LlmThinkingPolicy {

    fun enableThinkingFor(structuredRequest: Boolean, model: String): Boolean? =
        if (structuredRequest && model.contains("qwen", ignoreCase = true)) false else null
}
