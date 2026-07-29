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
    private fun fakeStore(initial: Preferences = mutablePreferencesOf()): DataStore<Preferences> {
        val state = MutableStateFlow(initial)
        return object : DataStore<Preferences> {
            override val data: Flow<Preferences> = state
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                val new = transform(state.value)
                state.value = new
                return new
            }
        }
    }

    @Test fun defaultsAreCorrect() = runBlocking {
        val prefs = UserPrefs(fakeStore())
        val s = prefs.snapshot.first()
        assertThat(s.llmEnabled).isFalse()
        assertThat(s.baseUrl).isEqualTo("https://api.openai.com/v1")
        assertThat(s.apiKey).isEmpty()
        assertThat(s.model).isEqualTo("gpt-4o-mini")
        assertThat(s.themeMode).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test fun setLlmEnabledPersists() = runBlocking {
        val prefs = UserPrefs(fakeStore())
        prefs.setLlmEnabled(true)
        assertThat(prefs.snapshot.first().llmEnabled).isTrue()
        prefs.setLlmEnabled(false)
        assertThat(prefs.snapshot.first().llmEnabled).isFalse()
    }

    @Test fun setApiConfigPersists() = runBlocking {
        val prefs = UserPrefs(fakeStore())
        prefs.setApiConfig(baseUrl = "https://x.com/v1", apiKey = "sk-1", model = "claude")
        val s = prefs.snapshot.first()
        assertThat(s.baseUrl).isEqualTo("https://x.com/v1")
        assertThat(s.apiKey).isEqualTo("sk-1")
        assertThat(s.model).isEqualTo("claude")
    }

    @Test fun themeModeDefaultsToSystem() = runBlocking {
        val prefs = UserPrefs(fakeStore())
        assertThat(prefs.snapshot.first().themeMode).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test fun setThemeModePersists() = runBlocking {
        val prefs = UserPrefs(fakeStore())
        prefs.setThemeMode(ThemeMode.DARK)
        assertThat(prefs.snapshot.first().themeMode).isEqualTo(ThemeMode.DARK)
        prefs.setThemeMode(ThemeMode.LIGHT)
        assertThat(prefs.snapshot.first().themeMode).isEqualTo(ThemeMode.LIGHT)
        prefs.setThemeMode(ThemeMode.SYSTEM)
        assertThat(prefs.snapshot.first().themeMode).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test fun themeModeRecoversFromCorruptedStoredValue() = runBlocking {
        val prefs = UserPrefs(fakeStore(mutablePreferencesOf(UserPrefs.THEME_MODE to "GARBAGE")))
        assertThat(prefs.snapshot.first().themeMode).isEqualTo(ThemeMode.SYSTEM)
    }
}
