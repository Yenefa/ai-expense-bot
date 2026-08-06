package com.expense.tracker.data.subscription

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.expense.tracker.data.prefs.ApiKeyStorage
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

class SubscriptionPrefsTest {
    @Test fun installationIdIsStableUuidStoredWithoutDeviceIdentifiers() = runBlocking {
        val store = FakeStore()
        val prefs = SubscriptionPrefs(store, MemoryTokenStorage())

        val first = prefs.installationId()
        val second = prefs.installationId()

        assertThat(first).isEqualTo(second)
        assertThat(first).matches("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
        assertThat(store.current[SubscriptionPrefs.INSTALLATION_ID]).isEqualTo(first)
    }

    @Test fun serverActivationStoresTokenOnlyInKeystoreStorage() = runBlocking {
        val store = FakeStore()
        val tokenStorage = MemoryTokenStorage()
        val prefs = SubscriptionPrefs(store, tokenStorage)
        val installationId = prefs.installationId()

        val snapshot = prefs.activateServerToken("opaque-token", expiresAtMillis = 9_000L)

        assertThat(snapshot.status).isEqualTo(SubscriptionStatus.ACTIVE)
        assertThat(snapshot.expiresAtMillis).isEqualTo(9_000L)
        assertThat(tokenStorage.value).isEqualTo("opaque-token")
        assertThat(store.current[SubscriptionPrefs.EXPIRES_AT]).isEqualTo(9_000L)
        assertThat(store.current[SubscriptionPrefs.TOKEN_FORMAT_VERSION])
            .isEqualTo(SubscriptionPrefs.CURRENT_TOKEN_FORMAT_VERSION)
        assertThat(store.current[SubscriptionPrefs.INSTALLATION_ID]).isEqualTo(installationId)
        assertThat(store.current.asMap().values.joinToString("|")).doesNotContain("opaque-token")
    }

    @Test fun activeAccessReturnsTokenInstallationAndServerExpiry() = runBlocking {
        val prefs = SubscriptionPrefs(FakeStore(), MemoryTokenStorage())
        val installationId = prefs.installationId()
        prefs.activateServerToken("opaque-token", expiresAtMillis = 9_000L)

        val access = prefs.activeAccess(nowMillis = 8_999L)

        assertThat(access).isNotNull()
        assertThat(access!!.credential).isEqualTo("opaque-token")
        assertThat(access.installationId).isEqualTo(installationId)
        assertThat(access.expiresAtMillis).isEqualTo(9_000L)
    }

    @Test fun currentTokenReturnsOnlyVersionTwoServerToken() = runBlocking {
        val prefs = SubscriptionPrefs(FakeStore(), MemoryTokenStorage())
        assertThat(prefs.currentToken()).isNull()

        prefs.activateServerToken("opaque-token", expiresAtMillis = 9_000L)

        assertThat(prefs.currentToken()).isEqualTo("opaque-token")
    }

    @Test fun legacyDirectApiCredentialIsClearedInsteadOfUsedAsServerToken() = runBlocking {
        val store = FakeStore(
            mutablePreferencesOf(SubscriptionPrefs.EXPIRES_AT to 9_000L),
        )
        val tokenStorage = MemoryTokenStorage("legacy-direct-api-key")
        val prefs = SubscriptionPrefs(store, tokenStorage)

        val access = prefs.activeAccess(nowMillis = 1_000L)

        assertThat(access).isNull()
        assertThat(tokenStorage.value).isEmpty()
        assertThat(store.current[SubscriptionPrefs.EXPIRES_AT]).isNull()
        assertThat(prefs.snapshot.first().status).isEqualTo(SubscriptionStatus.INACTIVE)
    }

    @Test fun expiryClearsServerTokenAndLocalEntitlement() = runBlocking {
        val store = FakeStore()
        val tokenStorage = MemoryTokenStorage()
        val prefs = SubscriptionPrefs(store, tokenStorage)
        prefs.activateServerToken("opaque-token", expiresAtMillis = 9_000L)

        val access = prefs.activeAccess(nowMillis = 9_000L)

        assertThat(access).isNull()
        assertThat(tokenStorage.value).isEmpty()
        assertThat(store.current[SubscriptionPrefs.EXPIRES_AT]).isNull()
    }

    @Test fun clearSubscriptionPreservesInstallationId() = runBlocking {
        val store = FakeStore()
        val tokenStorage = MemoryTokenStorage()
        val prefs = SubscriptionPrefs(store, tokenStorage)
        val installationId = prefs.installationId()
        prefs.activateServerToken("opaque-token", expiresAtMillis = 9_000L)

        prefs.clearSubscription()

        assertThat(tokenStorage.value).isEmpty()
        assertThat(store.current[SubscriptionPrefs.EXPIRES_AT]).isNull()
        assertThat(store.current[SubscriptionPrefs.TOKEN_FORMAT_VERSION]).isNull()
        assertThat(store.current[SubscriptionPrefs.INSTALLATION_ID]).isEqualTo(installationId)
    }

    @Test fun snapshotNeverExposesToken() = runBlocking {
        val prefs = SubscriptionPrefs(FakeStore(), MemoryTokenStorage())
        prefs.activateServerToken("never-print-token", expiresAtMillis = 9_000L)

        val snapshot = prefs.snapshot.first()

        assertThat(snapshot.toString()).doesNotContain("never-print-token")
        assertThat(snapshot.credentialAvailable).isTrue()
    }
}

private class FakeStore(initial: Preferences = mutablePreferencesOf()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    val current: Preferences get() = state.value

    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

private class MemoryTokenStorage(var value: String = "") : ApiKeyStorage {
    override fun read(): String = value
    override fun write(value: String) {
        this.value = value
    }
}
