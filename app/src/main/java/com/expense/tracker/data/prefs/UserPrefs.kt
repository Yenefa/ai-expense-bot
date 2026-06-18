package com.expense.tracker.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

data class UserPrefsSnapshot(
    val llmEnabled: Boolean,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
)

/**
 * 用户偏好。
 *
 * 安全设计：
 *  - **API Key** 走 [EncryptedSharedPreferences]（Android Keystore 主密钥 + AES-GCM 加密），
 *    避免明文落盘 — root 设备 / 备份导出 / debug attach 都无法直接读到原文。
 *  - 其它非敏感字段（llmEnabled / baseUrl / model）继续 DataStore Preferences。
 *
 * 兼容旧版本：旧 v1.4.2 之前 API Key 明文存在 DataStore。每次调用 [snapshot] 时，
 * 若发现 DataStore 仍残留 apiKey 字段则迁移到加密存储并清空原值，用户无感。
 */
class UserPrefs(
    private val store: DataStore<Preferences>,
    private val secureStore: SharedPreferences,
) {

    companion object Keys {
        val LLM_ENABLED = booleanPreferencesKey("llm_enabled")
        val BASE_URL    = stringPreferencesKey("llm_base_url")
        // Legacy: 旧版本明文 key 残留位，迁移后清空
        val LEGACY_API_KEY = stringPreferencesKey("llm_api_key")
        val MODEL       = stringPreferencesKey("llm_model")

        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_MODEL    = "gpt-4o-mini"

        private const val SECURE_PREFS_FILE = "user_prefs_secure"
        private const val SECURE_KEY_API_KEY = "llm_api_key"

        fun fromContext(ctx: Context): UserPrefs {
            val app = ctx.applicationContext
            val masterKey = MasterKey.Builder(app)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val secure = EncryptedSharedPreferences.create(
                app,
                SECURE_PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            return UserPrefs(app.appDataStore, secure)
        }
    }

    val snapshot: Flow<UserPrefsSnapshot> = store.data.map { p ->
        // 一次性迁移：旧版 DataStore 还有明文 apiKey → 搬到加密存储 → 清空原值
        val legacy = p[LEGACY_API_KEY]
        if (!legacy.isNullOrEmpty()) {
            secureStore.edit().putString(SECURE_KEY_API_KEY, legacy).apply()
            store.edit { it.remove(LEGACY_API_KEY) }
        }
        UserPrefsSnapshot(
            llmEnabled = p[LLM_ENABLED] ?: false,
            baseUrl = p[BASE_URL] ?: DEFAULT_BASE_URL,
            apiKey = secureStore.getString(SECURE_KEY_API_KEY, null).orEmpty(),
            model = p[MODEL] ?: DEFAULT_MODEL,
        )
    }

    suspend fun setLlmEnabled(enabled: Boolean) {
        store.edit { it[LLM_ENABLED] = enabled }
    }

    suspend fun setApiConfig(baseUrl: String, apiKey: String, model: String) {
        store.edit {
            it[BASE_URL] = baseUrl
            it[MODEL] = model
            // 旧 key 字段不再写入；如果残留也顺手清掉
            it.remove(LEGACY_API_KEY)
        }
        secureStore.edit().putString(SECURE_KEY_API_KEY, apiKey).apply()
    }
}
