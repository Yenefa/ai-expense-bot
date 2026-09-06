package com.expense.tracker.agent

import com.expense.tracker.llm.LlmResponseParser
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Intent Escalation（v3.9）：两级路由的升级层协议。
 *
 * 规则快路径（AgentRouter）不命中、但文本带疑问/分析特征时，
 * 才发一次轻量 LLM 调用产出该结构；记账协议（reply/expenses/actions）不被污染。
 */
@Serializable
data class IntentEscalation(
    /** "record" | "analysis" | "chat" */
    val intent: String = "chat",
    @SerialName("requires_tools") val requiresTools: Boolean = false,
    @SerialName("tool_candidates") val toolCandidates: List<String> = emptyList(),
) {
    val isAnalysis: Boolean get() = intent == "analysis"
}

object IntentEscalationParser {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    /** 任何解析失败都返回 null（调用方回退到普通聊天路径，绝不因升级层挂掉而丢消息）。 */
    fun parse(raw: String): IntentEscalation? {
        val candidate = LlmResponseParser.extractFirstJsonObject(raw.trim()) ?: return null
        return runCatching {
            val decoded = json.decodeFromString(IntentEscalation.serializer(), candidate)
            if (decoded.intent !in SUPPORTED_INTENTS) {
                decoded.copy(intent = "chat", requiresTools = false, toolCandidates = emptyList())
            } else {
                decoded
            }
        }.getOrNull()
    }

    private val SUPPORTED_INTENTS = setOf("record", "analysis", "chat")
}

object IntentEscalationPrompt {

    /** 极小输出（≤40 token），历史不注入：升级调用必须便宜。 */
    fun systemPrompt(): String = buildString {
        appendLine("你是意图分类器。你的唯一输出格式是 JSON：")
        appendLine("""{"intent":"analysis|record|chat","requires_tools":true|false,"tool_candidates":[]}""")
        appendLine("判断规则：")
        appendLine("- analysis：用户在询问/分析自己的消费数据、趋势、预算、是否花太多、给财务建议 —— 需要查询数据库才能回答 → requires_tools=true")
        appendLine("- record：用户在报告新支出，或要求删除/修改已有账目")
        appendLine("- chat：与个人消费数据无关的对话；不确定时选 chat")
        appendLine("不要输出 JSON 以外的任何文字。")
    }
}
