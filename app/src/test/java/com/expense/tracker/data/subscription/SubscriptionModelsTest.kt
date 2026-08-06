package com.expense.tracker.data.subscription

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SubscriptionModelsTest {
    @Test fun missingExpiryIsInactive() {
        assertThat(SubscriptionPolicy.status(expiresAtMillis = 0L, nowMillis = 1_000L))
            .isEqualTo(SubscriptionStatus.INACTIVE)
    }

    @Test fun futureExpiryIsActive() {
        assertThat(SubscriptionPolicy.status(expiresAtMillis = 2_000L, nowMillis = 1_000L))
            .isEqualTo(SubscriptionStatus.ACTIVE)
    }

    @Test fun reachedExpiryIsExpired() {
        assertThat(SubscriptionPolicy.status(expiresAtMillis = 1_000L, nowMillis = 1_000L))
            .isEqualTo(SubscriptionStatus.EXPIRED)
    }

    @Test fun subscriptionAccessToStringRedactsTokenAndInstallation() {
        val secret = "never-print-this"
        val installation = "11111111-1111-4111-8111-111111111111"

        val access = SubscriptionAccess(
            credential = secret,
            installationId = installation,
            expiresAtMillis = 123L,
        )

        assertThat(access.toString()).doesNotContain(secret)
        assertThat(access.toString()).doesNotContain(installation)
    }
}
