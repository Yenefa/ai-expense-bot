package com.expense.tracker.data.subscription

enum class SubscriptionStatus {
    INACTIVE,
    ACTIVE,
    EXPIRED,
}

data class SubscriptionSnapshot(
    val status: SubscriptionStatus,
    val expiresAtMillis: Long,
    val credentialAvailable: Boolean,
    val storageError: Boolean = false,
)

class SubscriptionAccess(
    val credential: String,
    val expiresAtMillis: Long,
    val installationId: String = "",
) {
    override fun toString(): String =
        "SubscriptionAccess(credential=<redacted>, installationId=<redacted>, expiresAtMillis=$expiresAtMillis)"
}

object SubscriptionConfig {
    const val MODEL = "hy3"
}

object SubscriptionPolicy {
    fun status(expiresAtMillis: Long, nowMillis: Long): SubscriptionStatus = when {
        expiresAtMillis <= 0L -> SubscriptionStatus.INACTIVE
        expiresAtMillis <= nowMillis -> SubscriptionStatus.EXPIRED
        else -> SubscriptionStatus.ACTIVE
    }
}
