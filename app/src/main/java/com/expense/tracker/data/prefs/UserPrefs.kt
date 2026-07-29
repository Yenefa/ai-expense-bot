package com.expense.tracker.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

/** 主题模式：浅色 / 深色 / 跟随系统。 */
enum class ThemeMode(val label: String, val emoji: String, val desc: String) {
    LIGHT("浅色", "☀️", "始终浅色"),
    DARK("深色", "🌙", "始终深色"),
    SYSTEM("跟随系统", "🌗", "随系统切换"),
}

data class UserPrefsSnapshot(
    val llmEnabled: Boolean,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val themeMode: ThemeMode,
)

class UserPrefs(private val store: DataStore<Preferences>) {

    companion object Keys {
        val LLM_ENABLED = booleanPreferencesKey("llm_enabled")
        val BASE_URL    = stringPreferencesKey("llm_base_url")
        val API_KEY     = stringPreferencesKey("llm_api_key")
        val MODEL       = stringPreferencesKey("llm_model")
        val THEME_MODE  = stringPreferencesKey("theme_mode")

        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_MODEL    = "gpt-4o-mini"
        val DEFAULT_THEME_MODE     = ThemeMode.SYSTEM

        fun fromContext(ctx: Context) = UserPrefs(ctx.applicationContext.appDataStore)
    }

    val snapshot: Flow<UserPrefsSnapshot> = store.data.map { p ->
        UserPrefsSnapshot(
            llmEnabled = p[LLM_ENABLED] ?: false,
            baseUrl = p[BASE_URL] ?: DEFAULT_BASE_URL,
            apiKey = p[API_KEY] ?: "",
            model = p[MODEL] ?: DEFAULT_MODEL,
            themeMode = p[THEME_MODE]?.let { name ->
                runCatching { ThemeMode.valueOf(name) }.getOrDefault(DEFAULT_THEME_MODE)
            } ?: DEFAULT_THEME_MODE,
        )
    }

    suspend fun setLlmEnabled(enabled: Boolean) {
        store.edit { it[LLM_ENABLED] = enabled }
    }

    suspend fun setApiConfig(baseUrl: String, apiKey: String, model: String) {
        store.edit {
            it[BASE_URL] = baseUrl
            it[API_KEY] = apiKey
            it[MODEL] = model
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME_MODE] = mode.name }
    }
}
