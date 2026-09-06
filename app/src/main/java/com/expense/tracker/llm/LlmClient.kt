package com.expense.tracker.llm

import com.expense.tracker.data.subscription.INSTALLATION_HEADER
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class SubscriptionUnauthorizedException : IllegalStateException(
    "AI 会员已失效，请重新兑换。",
)

class LlmClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val onSubscriptionUnauthorized: suspend () -> Unit = {},
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** 调用 OpenAI 兼容 chat completions，返回 assistant message content。 */
    suspend fun chatJson(
        baseUrl: String,
        apiKey: String,
        model: String,
        userText: String,
        systemPrompt: String = LlmPrompt.systemPrompt(),
        installationId: String? = null,
        history: List<ChatMsg> = emptyList(),
        temperature: Double? = null,
    ): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "API Key 为空，请到设置中填写" }

        val boundedHistory = history
            .asSequence()
            .filter { it.role == "user" || it.role == "assistant" }
            .filter { it.content.isNotBlank() }
            .map { it.copy(content = it.content.take(MAX_MESSAGE_CHARS)) }
            .toList()
            .takeLast(MAX_HISTORY_MESSAGES)
            .toMutableList()
            .apply {
                if (lastOrNull()?.let { it.role == "user" && it.content == userText } == true) {
                    removeAt(lastIndex)
                }
            }
        val requestPayload = ChatCompletionRequest(
            model = model,
            messages = buildList {
                add(ChatMsg(role = "system", content = systemPrompt))
                addAll(boundedHistory)
                add(ChatMsg(role = "user", content = userText.take(MAX_MESSAGE_CHARS)))
            },
            temperature = temperature,
        )
        val body = json.encodeToString(ChatCompletionRequest.serializer(), requestPayload)
            .toRequestBody("application/json".toMediaType())
        val path = if (installationId == null) "/chat/completions" else "/v1/chat/completions"
        val requestBuilder = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
        if (installationId != null) {
            requestBuilder.addHeader(INSTALLATION_HEADER, installationId)
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 401 && installationId != null) {
                    runCatching { onSubscriptionUnauthorized() }
                    throw SubscriptionUnauthorizedException()
                }
                if (response.code == 429 && installationId != null) {
                    error("AI 使用频率过高，请稍后再试。")
                }
                error("AI 服务请求失败（HTTP ${response.code}）。")
            }
            val responseText = response.body?.string().orEmpty()
            val parsed = runCatching {
                json.decodeFromString(ChatCompletionResponse.serializer(), responseText)
            }.getOrElse { error("AI 服务响应格式无效。") }
            parsed.choices.firstOrNull()?.message?.content
                ?: error("AI 服务响应为空。")
        }
    }

    companion object {
        // 历史消息只保留最近 6 条且每条截断到 1200 字符：足以支撑分轮补金额和日期继承，
        // 同时大幅缩小请求体积，显著降低 AI 解析耗时。
        private const val MAX_HISTORY_MESSAGES = 6
        private const val MAX_MESSAGE_CHARS = 1_200

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
