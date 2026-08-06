package com.expense.tracker.llm

import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AnalyticsInsightsAnalyzerTest {

    private val prefs = UserPrefsSnapshot(
        llmEnabled = true,
        baseUrl = "https://api.example.com/v1",
        apiKey = "test-key",
        model = "test-model",
    )

    @Test
    fun `uses analytics-specific system prompt asking for insights schema`() = runTest {
        val capturedSystemPrompts = mutableListOf<String>()
        val fakeClient = object : LlmClient() {
            override suspend fun chatJson(
                baseUrl: String,
                apiKey: String,
                model: String,
                userText: String,
                systemPrompt: String,
            ): String {
                capturedSystemPrompts += systemPrompt
                return """{"insights":["洞察一","洞察二"]}"""
            }
        }

        analyzeInsights(fakeClient, prefs, "请分析本周支出")

        // 必须真的把 systemPrompt 传下去了
        assertThat(capturedSystemPrompts).hasSize(1)
        val sys = capturedSystemPrompts.single()
        // 必须明确要求 insights schema
        assertThat(sys).contains("insights")
        // 绝不能再带记账助手的 reply/expenses/actions schema —— 那正是根因 B 的指令冲突来源
        assertThat(sys).doesNotContain("reply")
        assertThat(sys).doesNotContain("expenses")
        assertThat(sys).doesNotContain("actions")
    }

    @Test
    fun `extracts insights from prose-wrapped JSON response`() = runTest {
        val fakeClient = object : LlmClient() {
            override suspend fun chatJson(
                baseUrl: String,
                apiKey: String,
                model: String,
                userText: String,
                systemPrompt: String,
            ): String = """好的，这是你的消费洞察：{"insights": ["餐饮占比偏高", "建议减少外卖"]} 希望能帮到你"""
        }

        val result = analyzeInsights(fakeClient, prefs, "请分析本周支出")

        // 模型把 JSON 包在废话里时，必须抽取出来，而不是"分析失败"（根因 A）
        assertThat(result).containsExactly("餐饮占比偏高", "建议减少外卖")
    }
}
