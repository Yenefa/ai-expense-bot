package com.expense.tracker.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class LlmClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /**
     * 调用 OpenAI 兼容 chat completions，返回 assistant message content（应为 JSON 字符串）。
     *
     * @param history 已有对话历史（按时间正序，user/assistant 交替）。会被拼到 system 之后、当前
     *                user 之前。这是给 LLM 上下文记忆的关键 — 没有它，LLM 完全不知道"上一句"是什么。
     */
    suspend fun chatJson(
        baseUrl: String,
        apiKey: String,
        model: String,
        userText: String,
        history: List<ChatMsg> = emptyList(),
        systemPrompt: String = LlmPrompt.systemPrompt(),
    ): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "API Key 为空，请到设置中填写" }

        val messages = buildList {
            add(ChatMsg(role = "system", content = systemPrompt))
            addAll(history)
            add(ChatMsg(role = "user",   content = userText))
        }
        val req = ChatCompletionRequest(model = model, messages = messages)
        val body = json.encodeToString(ChatCompletionRequest.serializer(), req)
            .toRequestBody("application/json".toMediaType())
        val url = baseUrl.trimEnd('/') + "/chat/completions"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("LLM HTTP ${resp.code}: ${text.take(200)}")
            val parsed = runCatching {
                json.decodeFromString(ChatCompletionResponse.serializer(), text)
            }.getOrElse { error("LLM 响应解析失败：${it.message}") }
            val content = parsed.choices.firstOrNull()?.message?.content
                ?: error("LLM 响应为空")
            // logcat：方便用 `adb logcat -s LlmClient` 抓真实输入输出排查
            Log.i(TAG, "USER: $userText")
            Log.i(TAG, "RAW : $content")
            content
        }
    }

    companion object {
        private const val TAG = "LlmClient"
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
