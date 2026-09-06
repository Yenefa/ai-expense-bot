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

/** 可复现性协议（docs/expensebench.md）：temperature 显式传 0.0 才上线，null 时字段必须缺省。 */
class LlmClientTemperatureTest {

    @Test
    fun `显式temperature写入请求体`() = runBlocking {
        var requestBody = ""
        val client = LlmClient(httpClient = captureClient { requestBody = it })

        client.chatJson(
            baseUrl = "https://api.example.com",
            apiKey = "k",
            model = "m",
            userText = "hi",
            systemPrompt = "s",
            temperature = 0.0,
        )

        assertThat(requestBody).contains("\"temperature\":0.0")
    }

    @Test
    fun `未传temperature时字段缺省`() = runBlocking {
        var requestBody = ""
        val client = LlmClient(httpClient = captureClient { requestBody = it })

        client.chatJson(
            baseUrl = "https://api.example.com",
            apiKey = "k",
            model = "m",
            userText = "hi",
            systemPrompt = "s",
        )

        assertThat(requestBody).doesNotContain("temperature")
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
