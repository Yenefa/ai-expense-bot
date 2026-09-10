package com.expense.tracker.llm

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test

/** v3.9.1：结构化请求 body 必须真实携带 `enable_thinking:false`；缺省时不出现该字段。 */
class LlmClientThinkingTest {

    @Test
    fun `显式关闭思考写入请求体`() = runBlocking {
        var requestBody = ""
        val client = LlmClient(httpClient = captureClient { requestBody = it })

        client.chatJson(
            baseUrl = "https://api.example.com",
            apiKey = "k",
            model = "qwen3.7-flash",
            userText = "hi",
            systemPrompt = "s",
            enableThinking = false,
        )

        assertThat(requestBody).contains("\"enable_thinking\":false")
    }

    @Test
    fun `未显式指定时思考字段缺省`() = runBlocking {
        var requestBody = ""
        val client = LlmClient(httpClient = captureClient { requestBody = it })

        client.chatJson(
            baseUrl = "https://api.example.com",
            apiKey = "k",
            model = "deepseek-chat",
            userText = "hi",
            systemPrompt = "s",
        )

        assertThat(requestBody).doesNotContain("enable_thinking")
    }

    private fun captureClient(onBody: (String) -> Unit): OkHttpClient =
        OkHttpClient.Builder().addInterceptor { chain ->
            onBody(
                chain.request().body!!.let { body ->
                    val buffer = okio.Buffer()
                    body.writeTo(buffer)
                    buffer.readUtf8()
                },
            )
            mockSuccess(chain)
        }.build()

    private fun mockSuccess(chain: Interceptor.Chain): Response = Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("ok")
        .body("""{"choices":[{"message":{"role":"assistant","content":"ok"}}]}""".toResponseBody("application/json".toMediaType()))
        .build()
}
