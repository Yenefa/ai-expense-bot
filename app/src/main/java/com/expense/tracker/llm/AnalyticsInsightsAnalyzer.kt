package com.expense.tracker.llm

import com.expense.tracker.data.prefs.UserPrefsSnapshot

/**
 * 智核分析：调用 LLM 生成消费洞察列表。
 *
 * 从 AppContainer.analyticsAnalyzer 抽出为顶层函数，便于注入 LlmClient 做单测
 * （AppContainer 构造需要 Android Context，无法在 JVM 单测里实例化）。
 */
suspend fun analyzeInsights(
    client: LlmClient,
    prefs: UserPrefsSnapshot,
    prompt: String,
): List<String> = runCatching {
    val raw = client.chatJson(
        baseUrl = prefs.baseUrl,
        apiKey = prefs.apiKey,
        model = prefs.model,
        userText = prompt,
        systemPrompt = LlmPrompt.analyticsSystemPrompt(),
    )
    LlmResponseParser.parseInsights(raw)
        .ifEmpty { listOf("暂无洞察，请再试一次。") }
}.getOrElse { listOf("分析失败：${it.message ?: "未知错误"}") }
