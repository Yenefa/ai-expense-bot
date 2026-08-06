package com.expense.tracker.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
    val apiKeyError: Boolean = false,
)

class UserPrefs(
    private val store: DataStore<Preferences>,
    private val apiKeyStorage: ApiKeyStorage,
) {
    private val migrationMutex = Mutex()

    companion object Keys {
        val LLM_ENABLED = booleanPreferencesKey("llm_enabled")
        val BASE_URL    = stringPreferencesKey("llm_base_url")
        val API_KEY     = stringPreferencesKey("llm_api_key")
        val MODEL       = stringPreferencesKey("llm_model")
        val THEME_MODE  = stringPreferencesKey("theme_mode")

        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_MODEL    = "gpt-4o-mini"
        val DEFAULT_THEME_MODE     = ThemeMode.SYSTEM

        fun fromContext(ctx: Context): UserPrefs {
            val appContext = ctx.applicationContext
            return UserPrefs(
                store = appContext.appDataStore,
                apiKeyStorage = AndroidKeystoreApiKeyStorage(appContext),
            )
        }
    }

    val snapshot: Flow<UserPrefsSnapshot> = flow {
        var migrationFailed = !migrateLegacyApiKey()
        emitAll(store.data.map { p ->
            val secureRead = runCatching { apiKeyStorage.read() }
            val secureValue = secureRead.getOrNull().orEmpty()
            val legacyValue = p[API_KEY].orEmpty()
            if (secureRead.isSuccess && (secureValue.isNotEmpty() || legacyValue.isEmpty())) {
                migrationFailed = false
            }
            UserPrefsSnapshot(
                llmEnabled = p[LLM_ENABLED] ?: false,
                baseUrl = p[BASE_URL] ?: DEFAULT_BASE_URL,
                apiKey = secureValue.ifEmpty { legacyValue },
                model = p[MODEL] ?: DEFAULT_MODEL,
                themeMode = p[THEME_MODE]?.let { name ->
                    runCatching { ThemeMode.valueOf(name) }.getOrDefault(DEFAULT_THEME_MODE)
                } ?: DEFAULT_THEME_MODE,
                apiKeyError = migrationFailed || secureRead.isFailure,
            )
        })
    }.flowOn(Dispatchers.IO)

    suspend fun setLlmEnabled(enabled: Boolean) {
        store.edit { it[LLM_ENABLED] = enabled }
    }

    suspend fun setApiConfig(baseUrl: String, apiKey: String, model: String) {
        withContext(Dispatchers.IO) { apiKeyStorage.write(apiKey) }
        store.edit {
            it[BASE_URL] = baseUrl
            it.remove(API_KEY)
            it[MODEL] = model
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME_MODE] = mode.name }
    }

    /** 恢复备份中的非敏感设置；已有 API Key 始终保留。 */
    suspend fun restoreNonSecret(
        llmEnabled: Boolean,
        baseUrl: String,
        model: String,
        themeMode: ThemeMode,
    ) {
        store.edit {
            it[LLM_ENABLED] = llmEnabled
            it[BASE_URL] = baseUrl
            it[MODEL] = model
            it[THEME_MODE] = themeMode.name
        }
    }

    private suspend fun migrateLegacyApiKey(): Boolean = migrationMutex.withLock {
        val preferences = store.data.first()
        if (!preferences.contains(API_KEY)) return@withLock true

        runCatching {
            val legacyValue = preferences[API_KEY].orEmpty()
            val secureValue = apiKeyStorage.read()
            if (secureValue.isEmpty() && legacyValue.isNotEmpty()) {
                apiKeyStorage.write(legacyValue)
            }
            store.edit { it.remove(API_KEY) }
        }.isSuccess
    }
}
