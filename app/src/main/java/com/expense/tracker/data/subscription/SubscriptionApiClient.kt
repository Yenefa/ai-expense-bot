package com.expense.tracker.data.subscription

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

const val INSTALLATION_HEADER = "X-YE-Cost-Installation-Id"

@Serializable
data class RedeemRequest(
    val redemptionCode: String,
    val installationId: String,
)

@Serializable
data class RedeemResponse(
    val status: String,
    val accessToken: String,
    val expiresAtMillis: Long,
)

@Serializable
data class SubscriptionStatusResponse(
    val status: String,
    val expiresAtMillis: Long,
)

@Serializable
data class ServerHealthResponse(
    val status: String = "",
    val version: String = "",
    val serverTime: Long = 0L,
)

@Serializable
private data class SubscriptionErrorEnvelope(
    val error: SubscriptionErrorBody? = null,
)

@Serializable
private data class SubscriptionErrorBody(
    val code: String = "REQUEST_FAILED",
)

class SubscriptionApiException(
    val code: String,
    val httpStatus: Int? = null,
) : IllegalStateException(code)

class SubscriptionApiClient(
    private val baseUrl: String,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    suspend fun redeem(
        redemptionCode: String,
        installationId: String,
    ): RedeemResponse = withContext(Dispatchers.IO) {
        val payload = RedeemRequest(
            redemptionCode = redemptionCode.trim(),
            installationId = installationId,
        )
        val request = Request.Builder()
            .url(endpoint("v1/subscriptions/redeem"))
            .post(
                json.encodeToString(RedeemRequest.serializer(), payload)
                    .toRequestBody(JSON_MEDIA_TYPE),
            )
            .build()

        execute(request) { responseText ->
            runCatching {
                json.decodeFromString(RedeemResponse.serializer(), responseText)
            }.getOrElse { throw SubscriptionApiException("RESPONSE_INVALID") }
                .also { response ->
                    if (
                        response.status != "ACTIVE"
                        || response.accessToken.isBlank()
                        || response.expiresAtMillis <= clock()
                    ) {
                        throw SubscriptionApiException("RESPONSE_INVALID")
                    }
                }
        }
    }

    suspend fun status(
        accessToken: String,
        installationId: String,
    ): SubscriptionStatusResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint("v1/subscriptions/status"))
            .header("Authorization", "Bearer $accessToken")
            .header(INSTALLATION_HEADER, installationId)
            .get()
            .build()

        execute(request) { responseText ->
            runCatching {
                json.decodeFromString(SubscriptionStatusResponse.serializer(), responseText)
            }.getOrElse { throw SubscriptionApiException("RESPONSE_INVALID") }
                .also { response ->
                    if (response.status != "ACTIVE" || response.expiresAtMillis <= clock()) {
                        throw SubscriptionApiException("RESPONSE_INVALID")
                    }
                }
        }
    }

    /** 探测订阅服务器是否在线，并返回服务器时间（用于核对 app 时间是否在走）。 */
    suspend fun health(): ServerHealthResponse? = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) return@withContext null
        runCatching {
            val request = Request.Builder()
                .url(endpoint("health"))
                .get()
                .build()
            execute(request) { responseText ->
                runCatching {
                    json.decodeFromString(ServerHealthResponse.serializer(), responseText)
                }.getOrNull()?.takeIf { it.status == "ok" }
            }
        }.getOrNull()
    }

    private fun endpoint(path: String): String = "${baseUrl.trimEnd('/')}/$path"

    private fun <T> execute(request: Request, parse: (String) -> T): T {
        httpClient.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val code = runCatching {
                    json.decodeFromString(SubscriptionErrorEnvelope.serializer(), responseText)
                        .error
                        ?.code
                }.getOrNull().orEmpty().ifEmpty { "HTTP_${response.code}" }
                throw SubscriptionApiException(code = code, httpStatus = response.code)
            }
            return parse(responseText)
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
