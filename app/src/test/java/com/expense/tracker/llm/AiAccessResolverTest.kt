package com.expense.tracker.llm

import com.expense.tracker.data.prefs.ThemeMode
import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.expense.tracker.data.subscription.SubscriptionAccess
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test

class AiAccessResolverTest {
    @Test fun activeSubscriptionTakesPriorityOverByok() = runBlocking {
        val resolver = AiAccessResolver(
            subscriptionAccess = {
                SubscriptionAccess(
                    credential = "subscription-token",
                    expiresAtMillis = 9_000L,
                    installationId = "11111111-1111-4111-8111-111111111111",
                )
            },
            subscriptionBaseUrl = "https://proxy.example/ye-cost-api",
        )

        val config = resolver.resolve(byokSnapshot())

        assertThat(config.source).isEqualTo(AiAccessSource.SUBSCRIPTION)
        assertThat(config.baseUrl).isEqualTo("https://proxy.example/ye-cost-api")
        assertThat(config.model).isEqualTo("hy3")
        assertThat(config.apiKey).isEqualTo("subscription-token")
        assertThat(config.installationId)
            .isEqualTo("11111111-1111-4111-8111-111111111111")
    }

    @Test fun absentSubscriptionFallsBackToByok() = runBlocking {
        val resolver = AiAccessResolver(
            subscriptionAccess = { null },
            subscriptionBaseUrl = "https://proxy.example/ye-cost-api",
        )

        val config = resolver.resolve(byokSnapshot())

        assertThat(config.source).isEqualTo(AiAccessSource.BYOK)
        assertThat(config.baseUrl).isEqualTo("https://example.test/v1")
        assertThat(config.model).isEqualTo("test-model")
        assertThat(config.installationId).isNull()
    }

    @Test fun noSubscriptionAndNoByokFailsClearly() {
        val resolver = AiAccessResolver(
            subscriptionAccess = { null },
            subscriptionBaseUrl = "https://proxy.example/ye-cost-api",
        )

        val error = assertThrows(AiAccessUnavailableException::class.java) {
            runBlocking { resolver.resolve(byokSnapshot(apiKey = "")) }
        }

        assertThat(error).hasMessageThat().contains("AI 会员")
    }

    @Test fun configToStringRedactsCredential() = runBlocking {
        val secret = "do-not-print"
        val resolver = AiAccessResolver(
            subscriptionAccess = {
                SubscriptionAccess(
                    credential = secret,
                    expiresAtMillis = 9_000L,
                    installationId = "11111111-1111-4111-8111-111111111111",
                )
            },
            subscriptionBaseUrl = "https://proxy.example/ye-cost-api",
        )

        val config = resolver.resolve(byokSnapshot())

        assertThat(config.toString()).doesNotContain(secret)
        assertThat(config.toString()).doesNotContain("11111111-1111-4111-8111-111111111111")
    }

    private fun byokSnapshot(apiKey: String = "byok-secret") = UserPrefsSnapshot(
        llmEnabled = true,
        baseUrl = "https://example.test/v1",
        apiKey = apiKey,
        model = "test-model",
        themeMode = ThemeMode.SYSTEM,
    )
}
