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

data class UserPrefsSnapshot(
    val llmEnabled: Boolean,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
)

class UserPrefs(private val store: DataStore<Preferences>) {

    companion object Keys {
        val LLM_ENABLED = booleanPreferencesKey("llm_enabled")
        val BASE_URL    = stringPreferencesKey("llm_base_url")
        val API_KEY     = stringPreferencesKey("llm_api_key")
        val MODEL       = stringPreferencesKey("llm_model")

        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_MODEL    = "gpt-4o-mini"

        fun fromContext(ctx: Context) = UserPrefs(ctx.applicationContext.appDataStore)
    }

    val snapshot: Flow<UserPrefsSnapshot> = store.data.map { p ->
        UserPrefsSnapshot(
            llmEnabled = p[LLM_ENABLED] ?: false,
            baseUrl = p[BASE_URL] ?: DEFAULT_BASE_URL,
            apiKey = p[API_KEY] ?: "",
            model = p[MODEL] ?: DEFAULT_MODEL,
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
}
