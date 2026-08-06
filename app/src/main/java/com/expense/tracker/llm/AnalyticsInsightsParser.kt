package com.expense.tracker.llm

import kotlinx.serialization.json.Json

object AnalyticsInsightsParser {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    fun parse(raw: String): List<String> {
        val candidates = LlmResponseParser.extractJsonObjects(raw)
        require(candidates.isNotEmpty()) { "智核分析没有返回 JSON" }
        candidates.forEach { jsonText ->
            val insights = runCatching {
                json.decodeFromString(AnalyticsInsightsPayload.serializer(), jsonText).insights
            }.getOrNull()
                ?.map(String::trim)
                ?.filter(String::isNotEmpty)
                ?.distinct()
                ?.take(5)
                .orEmpty()
            if (insights.isNotEmpty()) return insights
        }
        throw IllegalArgumentException("智核分析没有返回有效洞察")
    }
}
