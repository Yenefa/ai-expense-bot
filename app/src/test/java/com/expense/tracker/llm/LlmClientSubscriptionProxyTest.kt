package com.expense.tracker.llm

import com.expense.tracker.data.subscription.INSTALLATION_HEADER
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertThrows
import org.junit.Test

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
        val client = LlmClient(
            httpClient = fixtureClient { chain ->
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
        assertThat(error.message).doesNotContain("sensitive server text")
        assertThat(error.message).doesNotContain("opaque-token")
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

    companion object {
        private const val INSTALLATION_ID = "11111111-1111-4111-8111-111111111111"
    }
}
