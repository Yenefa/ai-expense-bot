package com.expense.tracker.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

class UserPrefsTest {
    private class FakeStore(initial: Preferences = mutablePreferencesOf()) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        val current: Preferences get() = state.value

        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val new = transform(state.value)
            state.value = new
            return new
        }
    }

    private class FakeApiKeyStorage(
        var value: String = "",
        var writeFailure: RuntimeException? = null,
    ) : ApiKeyStorage {
        override fun read(): String = value
        override fun write(value: String) {
            writeFailure?.let { throw it }
            this.value = value
        }
    }

    @Test fun defaultsAreCorrect() = runBlocking {
        val prefs = UserPrefs(FakeStore(), FakeApiKeyStorage())
        val s = prefs.snapshot.first()
        assertThat(s.llmEnabled).isFalse()
        assertThat(s.baseUrl).isEqualTo("https://api.openai.com/v1")
        assertThat(s.apiKey).isEmpty()
        assertThat(s.model).isEqualTo("gpt-4o-mini")
        assertThat(s.themeMode).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test fun setLlmEnabledPersists() = runBlocking {
        val prefs = UserPrefs(FakeStore(), FakeApiKeyStorage())
        prefs.setLlmEnabled(true)
        assertThat(prefs.snapshot.first().llmEnabled).isTrue()
        prefs.setLlmEnabled(false)
        assertThat(prefs.snapshot.first().llmEnabled).isFalse()
    }

    @Test fun setApiConfigPersists() = runBlocking {
        val store = FakeStore()
        val apiKeyStorage = FakeApiKeyStorage()
        val prefs = UserPrefs(store, apiKeyStorage)
        prefs.setApiConfig(baseUrl = "https://x.com/v1", apiKey = "sk-1", model = "claude")
        val s = prefs.snapshot.first()
        assertThat(s.baseUrl).isEqualTo("https://x.com/v1")
        assertThat(s.apiKey).isEqualTo("sk-1")
        assertThat(s.model).isEqualTo("claude")
        assertThat(apiKeyStorage.value).isEqualTo("sk-1")
        assertThat(store.current[UserPrefs.API_KEY]).isNull()
    }

    @Test fun themeModeDefaultsToSystem() = runBlocking {
        val prefs = UserPrefs(FakeStore(), FakeApiKeyStorage())
        assertThat(prefs.snapshot.first().themeMode).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test fun setThemeModePersists() = runBlocking {
        val prefs = UserPrefs(FakeStore(), FakeApiKeyStorage())
        prefs.setThemeMode(ThemeMode.DARK)
        assertThat(prefs.snapshot.first().themeMode).isEqualTo(ThemeMode.DARK)
        prefs.setThemeMode(ThemeMode.LIGHT)
        assertThat(prefs.snapshot.first().themeMode).isEqualTo(ThemeMode.LIGHT)
        prefs.setThemeMode(ThemeMode.SYSTEM)
        assertThat(prefs.snapshot.first().themeMode).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test fun themeModeRecoversFromCorruptedStoredValue() = runBlocking {
        val prefs = UserPrefs(
            FakeStore(mutablePreferencesOf(UserPrefs.THEME_MODE to "GARBAGE")),
            FakeApiKeyStorage(),
        )
        assertThat(prefs.snapshot.first().themeMode).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test fun restoringNonSecretPreferencesPreservesApiKey() = runBlocking {
        val prefs = UserPrefs(FakeStore(), FakeApiKeyStorage())
        prefs.setApiConfig("https://current.example/v1", "current-secret", "current-model")

        prefs.restoreNonSecret(
            llmEnabled = true,
            baseUrl = "https://backup.example/v1",
            model = "backup-model",
            themeMode = ThemeMode.DARK,
        )

        val restored = prefs.snapshot.first()
        assertThat(restored.llmEnabled).isTrue()
        assertThat(restored.baseUrl).isEqualTo("https://backup.example/v1")
        assertThat(restored.model).isEqualTo("backup-model")
        assertThat(restored.themeMode).isEqualTo(ThemeMode.DARK)
        assertThat(restored.apiKey).isEqualTo("current-secret")
    }

    @Test fun legacyPlaintextApiKeyMigratesThenIsRemoved() = runBlocking {
        val store = FakeStore(mutablePreferencesOf(UserPrefs.API_KEY to "legacy-secret"))
        val apiKeyStorage = FakeApiKeyStorage()
        val prefs = UserPrefs(store, apiKeyStorage)

        val snapshot = prefs.snapshot.first()

        assertThat(snapshot.apiKey).isEqualTo("legacy-secret")
        assertThat(snapshot.apiKeyError).isFalse()
        assertThat(apiKeyStorage.value).isEqualTo("legacy-secret")
        assertThat(store.current[UserPrefs.API_KEY]).isNull()
    }

    @Test fun existingEncryptedApiKeyWinsAndRemovesStaleLegacyValue() = runBlocking {
        val store = FakeStore(mutablePreferencesOf(UserPrefs.API_KEY to "stale-legacy"))
        val apiKeyStorage = FakeApiKeyStorage(value = "encrypted-current")
        val prefs = UserPrefs(store, apiKeyStorage)

        val snapshot = prefs.snapshot.first()

        assertThat(snapshot.apiKey).isEqualTo("encrypted-current")
        assertThat(store.current[UserPrefs.API_KEY]).isNull()
    }

    @Test fun failedMigrationPreservesLegacyPlaintextAndReportsRecoverableError() = runBlocking {
        val store = FakeStore(mutablePreferencesOf(UserPrefs.API_KEY to "must-not-be-lost"))
        val apiKeyStorage = FakeApiKeyStorage(writeFailure = IllegalStateException("keystore unavailable"))
        val prefs = UserPrefs(store, apiKeyStorage)

        val snapshot = prefs.snapshot.first()

        assertThat(snapshot.apiKey).isEqualTo("must-not-be-lost")
        assertThat(snapshot.apiKeyError).isTrue()
        assertThat(store.current[UserPrefs.API_KEY]).isEqualTo("must-not-be-lost")
    }
}
