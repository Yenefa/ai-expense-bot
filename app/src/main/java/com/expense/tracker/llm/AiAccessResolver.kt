package com.expense.tracker.llm

import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.expense.tracker.data.subscription.SubscriptionAccess
import com.expense.tracker.data.subscription.SubscriptionConfig

enum class AiAccessSource {
    SUBSCRIPTION,
    BYOK,
}

class AiServiceConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val source: AiAccessSource,
    val installationId: String? = null,
) {
    override fun toString(): String =
        "AiServiceConfig(baseUrl=$baseUrl, apiKey=<redacted>, model=$model, " +
            "source=$source, installationId=<redacted>)"
}

class AiAccessUnavailableException : IllegalStateException(
    "AI 服务未配置，请兑换 AI 会员或在 LLM 设置中填写自己的 API Key",
)

class AiAccessResolver(
    private val subscriptionAccess: suspend () -> SubscriptionAccess?,
    private val subscriptionBaseUrl: String,
) {
    suspend fun resolve(byok: UserPrefsSnapshot): AiServiceConfig {
        subscriptionAccess()?.let { access ->
            return AiServiceConfig(
                baseUrl = subscriptionBaseUrl,
                apiKey = access.credential,
                model = SubscriptionConfig.MODEL,
                source = AiAccessSource.SUBSCRIPTION,
                installationId = access.installationId,
            )
        }

        if (byok.apiKey.isNotBlank()) {
            return AiServiceConfig(
                baseUrl = byok.baseUrl,
                apiKey = byok.apiKey,
                model = byok.model,
                source = AiAccessSource.BYOK,
                installationId = null,
            )
        }

        throw AiAccessUnavailableException()
    }
}
