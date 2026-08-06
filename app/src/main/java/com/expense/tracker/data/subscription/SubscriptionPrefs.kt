package com.expense.tracker.data.subscription

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.expense.tracker.data.prefs.AndroidKeystoreApiKeyStorage
import com.expense.tracker.data.prefs.ApiKeyStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

private val Context.subscriptionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "subscription_prefs",
)

class SubscriptionPrefs(
    private val store: DataStore<Preferences>,
    private val tokenStorage: ApiKeyStorage,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    val snapshot: Flow<SubscriptionSnapshot> = store.data.map { preferences ->
        val versionIsCurrent = preferences[TOKEN_FORMAT_VERSION] == CURRENT_TOKEN_FORMAT_VERSION
        val expiresAt = if (versionIsCurrent) preferences[EXPIRES_AT] ?: 0L else 0L
        val tokenRead = if (versionIsCurrent) {
            runCatching { tokenStorage.read() }
        } else {
            Result.success("")
        }
        SubscriptionSnapshot(
            status = SubscriptionPolicy.status(expiresAt, clock()),
            expiresAtMillis = expiresAt,
            credentialAvailable = tokenRead.getOrNull().orEmpty().isNotEmpty(),
            storageError = tokenRead.isFailure,
        )
    }.flowOn(Dispatchers.IO)

    suspend fun installationId(): String = mutex.withLock {
        val current = store.data.first()
        current[INSTALLATION_ID]?.takeIf { it.isNotBlank() } ?: UUID.randomUUID()
            .toString()
            .also { generated ->
                store.edit { preferences -> preferences[INSTALLATION_ID] = generated }
            }
    }

    suspend fun activateServerToken(
        token: String,
        expiresAtMillis: Long,
    ): SubscriptionSnapshot = mutex.withLock {
        val normalized = token.trim()
        require(normalized.isNotEmpty()) { "订阅令牌不能为空" }
        require(expiresAtMillis > 0L) { "订阅到期时间无效" }
        val previousToken = withContext(Dispatchers.IO) { tokenStorage.read() }
        withContext(Dispatchers.IO) { tokenStorage.write(normalized) }
        try {
            store.edit { preferences ->
                preferences[EXPIRES_AT] = expiresAtMillis
                preferences[TOKEN_FORMAT_VERSION] = CURRENT_TOKEN_FORMAT_VERSION
                preferences.remove(REDEEMED_FINGERPRINTS)
            }
        } catch (error: Throwable) {
            runCatching {
                withContext(Dispatchers.IO) { tokenStorage.write(previousToken) }
            }
            throw error
        }
        SubscriptionSnapshot(
            status = SubscriptionStatus.ACTIVE,
            expiresAtMillis = expiresAtMillis,
            credentialAvailable = true,
        )
    }

    suspend fun currentToken(): String? = mutex.withLock {
        val preferences = store.data.first()
        if (preferences[TOKEN_FORMAT_VERSION] != CURRENT_TOKEN_FORMAT_VERSION) {
            clearSubscriptionLocked()
            return@withLock null
        }
        withContext(Dispatchers.IO) { tokenStorage.read() }.ifBlank { null }
    }

    suspend fun activeAccess(nowMillis: Long = clock()): SubscriptionAccess? = mutex.withLock {
        val preferences = store.data.first()
        if (preferences[TOKEN_FORMAT_VERSION] != CURRENT_TOKEN_FORMAT_VERSION) {
            clearSubscriptionLocked()
            return@withLock null
        }
        val expiresAt = preferences[EXPIRES_AT] ?: 0L
        if (SubscriptionPolicy.status(expiresAt, nowMillis) != SubscriptionStatus.ACTIVE) {
            clearSubscriptionLocked()
            return@withLock null
        }

        val token = withContext(Dispatchers.IO) { tokenStorage.read() }
        if (token.isEmpty()) return@withLock null
        var installationId = preferences[INSTALLATION_ID]
        if (installationId.isNullOrBlank()) {
            installationId = UUID.randomUUID().toString()
            val generated = installationId
            store.edit { mutable -> mutable[INSTALLATION_ID] = generated }
        }
        SubscriptionAccess(
            credential = token,
            installationId = installationId,
            expiresAtMillis = expiresAt,
        )
    }

    suspend fun clearSubscription() = mutex.withLock {
        clearSubscriptionLocked()
    }

    private suspend fun clearSubscriptionLocked() {
        withContext(Dispatchers.IO) { tokenStorage.write("") }
        store.edit { preferences ->
            preferences.remove(EXPIRES_AT)
            preferences.remove(TOKEN_FORMAT_VERSION)
            preferences.remove(REDEEMED_FINGERPRINTS)
        }
    }

    companion object {
        const val CURRENT_TOKEN_FORMAT_VERSION = 2

        val EXPIRES_AT = longPreferencesKey("subscription_expires_at")
        val INSTALLATION_ID = stringPreferencesKey("subscription_installation_id")
        val TOKEN_FORMAT_VERSION = intPreferencesKey("subscription_token_format_version")
        private val REDEEMED_FINGERPRINTS =
            stringSetPreferencesKey("redeemed_credential_fingerprints")

        fun fromContext(context: Context): SubscriptionPrefs {
            val appContext = context.applicationContext
            return SubscriptionPrefs(
                store = appContext.subscriptionDataStore,
                tokenStorage = AndroidKeystoreApiKeyStorage(
                    context = appContext,
                    keyAlias = "y_e_cost_subscription_credential_aes_v1",
                    preferencesName = "secure_subscription_credential",
                ),
            )
        }
    }
}
