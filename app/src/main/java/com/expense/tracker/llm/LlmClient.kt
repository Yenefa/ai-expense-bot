package com.expense.tracker.llm

import com.expense.tracker.data.subscription.INSTALLATION_HEADER
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SubscriptionUnauthorizedException : IllegalStateException(
    "AI 会员已失效，请重新兑换。",
)

class LlmClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val onSubscriptionUnauthorized: suspend () -> Unit = {},
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** HTTP 5xx：可重试的临时服务端错误；重试耗尽后映射为原有用户可见错误。 */
    private class RetryableServerException(val statusCode: Int) : Exception()

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
        enableThinking: Boolean? = null,
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
            enableThinking = enableThinking,
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

        try {
            executeWithRetry(requestBuilder.build(), installationId)
        } catch (e: SubscriptionUnauthorizedException) {
            // 401：先清理本地会员状态，再抛出类型化安全错误；该错误绝不重试。
            runCatching { onSubscriptionUnauthorized() }
            throw e
        }
    }

    /** 瞬时故障（IOException / HTTP 5xx）至多额外重试 [MAX_RETRIES] 次；协程已取消或 4xx 一律不重试。 */
    private suspend fun executeWithRetry(request: Request, installationId: String?): String {
        var retries = 0
        while (true) {
            try {
                return executeOnce(request, installationId)
            } catch (e: IOException) {
                // 若协程已取消，ensureActive 会抛 CancellationException，取消后不再发起重试。
                currentCoroutineContext().ensureActive()
                if (retries >= MAX_RETRIES) throw e
                retries++
                delay(RETRY_BACKOFF_MS)
            } catch (e: RetryableServerException) {
                currentCoroutineContext().ensureActive()
                if (retries >= MAX_RETRIES) error("AI 服务请求失败（HTTP ${e.statusCode}）。")
                retries++
                delay(RETRY_BACKOFF_MS)
            }
        }
    }

    /**
     * 异步执行单次请求：enqueue 回调 + suspendCancellableCoroutine。
     * 协程取消时通过 invokeOnCancellation 调用 call.cancel()，立即中止 HTTP 调用并释放连接。
     */
    private suspend fun executeOnce(request: Request, installationId: String?): String =
        suspendCancellableCoroutine<String> { continuation ->
            val httpCall = httpClient.newCall(request)
            continuation.invokeOnCancellation { httpCall.cancel() }
            httpCall.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isActive) return
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use { it.readChatContent(installationId) }
                    }
                    if (!continuation.isActive) return
                    result
                        .onSuccess { continuation.resume(it) }
                        .onFailure { continuation.resumeWithException(it) }
                }
            })
        }

    /** 成功解析 / HTTP 错误映射与加固前保持一致（仅 5xx 改为可重试信号）。 */
    private fun Response.readChatContent(installationId: String?): String {
        if (!isSuccessful) {
            when {
                code == 401 && installationId != null -> throw SubscriptionUnauthorizedException()
                code == 429 && installationId != null -> error("AI 使用频率过高，请稍后再试。")
                code in 500..599 -> throw RetryableServerException(code)
                else -> error("AI 服务请求失败（HTTP $code）。")
            }
        }
        val responseText = body?.let { readBoundedBody(it) }.orEmpty()
        val parsed = runCatching {
            json.decodeFromString(ChatCompletionResponse.serializer(), responseText)
        }.getOrElse { error("AI 服务响应格式无效。") }
        return parsed.choices.firstOrNull()?.message?.content
            ?: error("AI 服务响应为空。")
    }

    companion object {
        // 历史消息只保留最近 6 条且每条截断到 1200 字符：足以支撑分轮补金额和日期继承，
        // 同时大幅缩小请求体积，显著降低 AI 解析耗时。
        private const val MAX_HISTORY_MESSAGES = 6
        private const val MAX_MESSAGE_CHARS = 1_200

        // 文件此前无超时常量，按加固要求：连接 15s / 读 90s / 写 20s / 整次调用 120s。
        // callTimeout 兜底 connect+read，避免挂死端点长期占用线程。
        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val READ_TIMEOUT_SECONDS = 90L
        private const val WRITE_TIMEOUT_SECONDS = 20L
        private const val CALL_TIMEOUT_SECONDS = 120L

        // 响应体上限 2 MiB：LLM JSON 响应远小于该值，超限直接拒绝，避免无界 body 撑爆内存。
        internal const val MAX_RESPONSE_BYTES = 2L * 1024 * 1024
        private const val RESPONSE_TOO_LARGE_MESSAGE = "AI 服务响应过大，已拒绝。"

        // 瞬时故障重试：恰好额外重试 1 次，退避 400ms。
        private const val MAX_RETRIES = 1
        private const val RETRY_BACKOFF_MS = 400L

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        /**
         * 有界读取响应体：声明长度或实际长度超过 [MAX_RESPONSE_BYTES] 时抛出明确异常。
         * 最多向底层读取 MAX + 1 字节，未超限才读取全文，避免无界 body 撑爆内存。
         */
        internal fun readBoundedBody(body: ResponseBody): String {
            if (body.contentLength() > MAX_RESPONSE_BYTES) {
                throw IllegalStateException(RESPONSE_TOO_LARGE_MESSAGE)
            }
            val source = body.source()
            if (source.request(MAX_RESPONSE_BYTES + 1)) {
                throw IllegalStateException(RESPONSE_TOO_LARGE_MESSAGE)
            }
            return source.readUtf8()
        }
    }
}
