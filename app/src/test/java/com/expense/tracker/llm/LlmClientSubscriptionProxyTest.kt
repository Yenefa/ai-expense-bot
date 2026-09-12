package com.expense.tracker.llm

import com.expense.tracker.data.subscription.INSTALLATION_HEADER
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class LlmClientSubscriptionProxyTest {
    @Test fun requestIncludesBoundedHistoryBeforeCurrentMessageWithoutDuplicateCurrent() = runBlocking {
        var requestBody = ""
        val client = LlmClient(httpClient = fixtureClient { chain ->
            requestBody = chain.request().body!!.let { body ->
                val buffer = okio.Buffer()
                body.writeTo(buffer)
                buffer.readUtf8()
            }
            success(chain)
        })

        client.chatJson(
            baseUrl = "https://byok.example/v1",
            apiKey = "byok-key",
            model = "custom",
            userText = "金额是6和13",
            systemPrompt = "system",
            history = listOf(
                ChatMsg("user", "8月1日两瓶水"),
                ChatMsg("assistant", "请补金额"),
                ChatMsg("user", "金额是6和13"),
            ),
        )

        val payload = Json.decodeFromString(ChatCompletionRequest.serializer(), requestBody)
        assertThat(payload.messages.map { it.role })
            .containsExactly("system", "user", "assistant", "user").inOrder()
        assertThat(payload.messages.map { it.content })
            .containsExactly("system", "8月1日两瓶水", "请补金额", "金额是6和13").inOrder()
    }

    @Test fun subscriptionRequestSendsBearerAndInstallationHeader() = runBlocking {
        var authorization = ""
        var installation = ""
        val client = LlmClient(httpClient = fixtureClient { chain ->
            authorization = chain.request().header("Authorization").orEmpty()
            installation = chain.request().header(INSTALLATION_HEADER).orEmpty()
            success(chain)
        })

        client.chatJson(
            baseUrl = "https://proxy.example/ye-cost-api",
            apiKey = "opaque-token",
            model = "hy3",
            userText = "hello",
            systemPrompt = "system",
            installationId = INSTALLATION_ID,
        )

        assertThat(authorization).isEqualTo("Bearer opaque-token")
        assertThat(installation).isEqualTo(INSTALLATION_ID)
    }

    @Test fun byokRequestDoesNotSendInstallationHeader() = runBlocking {
        var installation: String? = "not-called"
        val client = LlmClient(httpClient = fixtureClient { chain ->
            installation = chain.request().header(INSTALLATION_HEADER)
            success(chain)
        })

        client.chatJson(
            baseUrl = "https://byok.example/v1",
            apiKey = "byok-key",
            model = "custom",
            userText = "hello",
            systemPrompt = "system",
        )

        assertThat(installation).isNull()
    }

    @Test fun subscription401ClearsLocalAccessAndThrowsTypedSafeError() {
        var clearCalls = 0
        var attempts = 0
        val client = LlmClient(
            httpClient = fixtureClient { chain ->
                attempts++
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(401)
                    .message("unauthorized")
                    .body("sensitive server text".toResponseBody("application/json".toMediaType()))
                    .build()
            },
            onSubscriptionUnauthorized = { clearCalls++ },
        )

        val error = assertThrows(SubscriptionUnauthorizedException::class.java) {
            runBlocking {
                client.chatJson(
                    baseUrl = "https://proxy.example/ye-cost-api",
                    apiKey = "opaque-token",
                    model = "hy3",
                    userText = "hello",
                    installationId = INSTALLATION_ID,
                )
            }
        }

        assertThat(clearCalls).isEqualTo(1)
        // 401 绝不重试：只应发生一次 HTTP 调用。
        assertThat(attempts).isEqualTo(1)
        assertThat(error.message).doesNotContain("sensitive server text")
        assertThat(error.message).doesNotContain("opaque-token")
    }

    // === 网络加固：超时 / 重试 / 取消 / 响应体上限 ===

    @Test fun defaultClientConfiguresHardenedTimeouts() {
        val client = LlmClient.defaultHttpClient()

        assertThat(client.connectTimeoutMillis).isEqualTo(15_000)
        assertThat(client.readTimeoutMillis).isEqualTo(90_000)
        assertThat(client.writeTimeoutMillis).isEqualTo(20_000)
        assertThat(client.callTimeoutMillis).isEqualTo(120_000)
    }

    @Test fun transientIOExceptionIsRetriedOnceThenSucceeds() = runBlocking {
        var attempts = 0
        val client = LlmClient(httpClient = fixtureClient { chain ->
            attempts++
            if (attempts == 1) throw IOException("网络瞬断")
            success(chain)
        })

        val content = client.chatJson(
            baseUrl = "https://byok.example/v1",
            apiKey = "byok-key",
            model = "custom",
            userText = "hello",
            systemPrompt = "system",
        )

        assertThat(attempts).isEqualTo(2)
        assertThat(content).isEqualTo("ok")
    }

    @Test fun http5xxIsRetriedOnceThenSucceeds() = runBlocking {
        var attempts = 0
        val client = LlmClient(httpClient = fixtureClient { chain ->
            attempts++
            if (attempts == 1) httpError(chain, 503) else success(chain)
        })

        val content = client.chatJson(
            baseUrl = "https://byok.example/v1",
            apiKey = "byok-key",
            model = "custom",
            userText = "hello",
            systemPrompt = "system",
        )

        assertThat(attempts).isEqualTo(2)
        assertThat(content).isEqualTo("ok")
    }

    @Test fun persistentHttp5xxFailsAfterSingleRetryWithMappedError() {
        var attempts = 0
        val client = LlmClient(httpClient = fixtureClient { chain ->
            attempts++
            httpError(chain, 500)
        })

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                client.chatJson(
                    baseUrl = "https://byok.example/v1",
                    apiKey = "byok-key",
                    model = "custom",
                    userText = "hello",
                    systemPrompt = "system",
                )
            }
        }

        assertThat(attempts).isEqualTo(2)
        assertThat(error.message).contains("HTTP 500")
    }

    @Test fun http4xxIsNotRetried() {
        var attempts = 0
        val client = LlmClient(httpClient = fixtureClient { chain ->
            attempts++
            httpError(chain, 400)
        })

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                client.chatJson(
                    baseUrl = "https://byok.example/v1",
                    apiKey = "byok-key",
                    model = "custom",
                    userText = "hello",
                    systemPrompt = "system",
                )
            }
        }

        assertThat(attempts).isEqualTo(1)
        assertThat(error.message).contains("HTTP 400")
    }

    @Test fun bodyWithinCapIsReadThrough() {
        val payload = """{"choices":[{"message":{"role":"assistant","content":"ok"}}]}"""

        val text = LlmClient.readBoundedBody(payload.toResponseBody("application/json".toMediaType()))

        assertThat(text).isEqualTo(payload)
    }

    @Test fun bodyExactlyAtCapIsAccepted() {
        val atCap = "x".repeat(LlmClient.MAX_RESPONSE_BYTES.toInt())

        val text = LlmClient.readBoundedBody(atCap.toResponseBody("application/json".toMediaType()))

        assertThat(text.length).isEqualTo(LlmClient.MAX_RESPONSE_BYTES.toInt())
    }

    @Test fun declaredOversizedBodyIsRejectedWithoutReading() {
        val oversized = "x".repeat((LlmClient.MAX_RESPONSE_BYTES + 1).toInt())

        val error = assertThrows(IllegalStateException::class.java) {
            LlmClient.readBoundedBody(oversized.toResponseBody("application/json".toMediaType()))
        }

        assertThat(error.message).contains("过大")
    }

    @Test fun unknownLengthOversizedBodyIsRejectedByBoundedRead() {
        val error = assertThrows(IllegalStateException::class.java) {
            LlmClient.readBoundedBody(unknownLengthBody((LlmClient.MAX_RESPONSE_BYTES + 1).toInt()))
        }

        assertThat(error.message).contains("过大")
    }

    @Test fun oversizedResponseFailsChatJsonWithoutRetry() {
        var attempts = 0
        val client = LlmClient(httpClient = fixtureClient { chain ->
            attempts++
            val oversized = "x".repeat((LlmClient.MAX_RESPONSE_BYTES + 1).toInt())
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(oversized.toResponseBody("application/json".toMediaType()))
                .build()
        })

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                client.chatJson(
                    baseUrl = "https://byok.example/v1",
                    apiKey = "byok-key",
                    model = "custom",
                    userText = "hello",
                    systemPrompt = "system",
                )
            }
        }

        assertThat(attempts).isEqualTo(1)
        assertThat(error.message).contains("过大")
    }

    @Test fun coroutineCancellationCancelsUnderlyingHttpCall() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val captured = AtomicReference<Call?>(null)
        val httpClient = OkHttpClient.Builder().addInterceptor { chain ->
            captured.set(chain.call())
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
            success(chain)
        }.build()
        val client = LlmClient(httpClient = httpClient)

        val job = launch(Dispatchers.Default) {
            client.chatJson(
                baseUrl = "https://byok.example/v1",
                apiKey = "byok-key",
                model = "custom",
                userText = "hello",
                systemPrompt = "system",
            )
        }

        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()

            job.cancel()
            job.join()

            assertThat(captured.get()?.isCanceled()).isTrue()
        } finally {
            release.countDown()
        }
    }

    private fun fixtureClient(block: (Interceptor.Chain) -> Response): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(Interceptor(block)).build()

    private fun success(chain: Interceptor.Chain): Response = Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(
            """{"choices":[{"message":{"role":"assistant","content":"ok"}}]}"""
                .toResponseBody("application/json".toMediaType()),
        )
        .build()

    private fun httpError(chain: Interceptor.Chain, code: Int): Response = Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("error")
        .body("""{"error":"$code"}""".toResponseBody("application/json".toMediaType()))
        .build()

    private fun unknownLengthBody(sizeBytes: Int): ResponseBody {
        val buffer = Buffer().apply { writeUtf8("x".repeat(sizeBytes)) }
        return object : ResponseBody() {
            override fun contentType() = "application/json".toMediaType()

            override fun contentLength(): Long = -1L

            override fun source(): BufferedSource = buffer
        }
    }

    companion object {
        private const val INSTALLATION_ID = "11111111-1111-4111-8111-111111111111"
    }
}
