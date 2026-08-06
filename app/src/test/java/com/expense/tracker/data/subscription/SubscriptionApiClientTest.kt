package com.expense.tracker.data.subscription

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertThrows
import org.junit.Test

class SubscriptionApiClientTest {
    @Test fun redeemPostsOnlyCodeAndInstallationAndParsesServerExpiry() = runBlocking {
        val interceptor = RecordingInterceptor(
            code = 200,
            body = """{"status":"ACTIVE","accessToken":"opaque-token","expiresAtMillis":9000}""",
        )
        val client = client(interceptor)

        val response = client.redeem(
            redemptionCode = "  ye30-abcd-2345-wxyz  ",
            installationId = INSTALLATION_ID,
        )

        assertThat(response.accessToken).isEqualTo("opaque-token")
        assertThat(response.expiresAtMillis).isEqualTo(9_000L)
        assertThat(interceptor.request.url.toString())
            .isEqualTo("https://server.example/base/v1/subscriptions/redeem")
        val body = interceptor.request.body!!.let { requestBody ->
            okio.Buffer().also(requestBody::writeTo).readUtf8()
        }
        assertThat(body).contains("\"redemptionCode\":\"ye30-abcd-2345-wxyz\"")
        assertThat(body).contains("\"installationId\":\"$INSTALLATION_ID\"")
        assertThat(body).doesNotContain("requestId")
        assertThat(body).doesNotContain("currentAccessToken")
    }

    @Test fun statusSendsBearerTokenAndInstallationHeader() = runBlocking {
        val interceptor = RecordingInterceptor(
            code = 200,
            body = """{"status":"ACTIVE","expiresAtMillis":9000}""",
        )
        val client = client(interceptor)

        val response = client.status("opaque-token", INSTALLATION_ID)

        assertThat(response.expiresAtMillis).isEqualTo(9_000L)
        assertThat(interceptor.request.header("Authorization")).isEqualTo("Bearer opaque-token")
        assertThat(interceptor.request.header(INSTALLATION_HEADER)).isEqualTo(INSTALLATION_ID)
    }

    @Test fun mapsInvalidOrUsedCodeWithoutLeakingResponseBody() {
        val interceptor = RecordingInterceptor(
            code = 400,
            body = """{"error":{"code":"CODE_INVALID_OR_USED","detail":"secret database text"}}""",
        )
        val error = assertThrows(SubscriptionApiException::class.java) {
            runBlocking { client(interceptor).redeem("bad", INSTALLATION_ID) }
        }

        assertThat(error.code).isEqualTo("CODE_INVALID_OR_USED")
        assertThat(error.message).doesNotContain("secret database text")
    }

    @Test fun malformedSuccessBodyFailsWithStableSafeCode() {
        val interceptor = RecordingInterceptor(code = 200, body = "not-json-sensitive")
        val error = assertThrows(SubscriptionApiException::class.java) {
            runBlocking { client(interceptor).redeem("bad", INSTALLATION_ID) }
        }

        assertThat(error.code).isEqualTo("RESPONSE_INVALID")
        assertThat(error.message).doesNotContain("not-json-sensitive")
    }

    @Test fun expiredSuccessBodyFailsWithStableSafeCode() {
        val interceptor = RecordingInterceptor(
            code = 200,
            body = """{"status":"ACTIVE","accessToken":"opaque-token","expiresAtMillis":1000}""",
        )
        val error = assertThrows(SubscriptionApiException::class.java) {
            runBlocking { client(interceptor).redeem("bad", INSTALLATION_ID) }
        }

        assertThat(error.code).isEqualTo("RESPONSE_INVALID")
    }

    private fun client(interceptor: RecordingInterceptor) = SubscriptionApiClient(
        baseUrl = "https://server.example/base/",
        httpClient = OkHttpClient.Builder().addInterceptor(interceptor).build(),
        clock = { 1_000L },
    )

    private class RecordingInterceptor(
        private val code: Int,
        private val body: String,
    ) : Interceptor {
        lateinit var request: Request

        override fun intercept(chain: Interceptor.Chain): Response {
            request = chain.request()
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("fixture")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    companion object {
        private const val INSTALLATION_ID = "11111111-1111-4111-8111-111111111111"
    }
}
