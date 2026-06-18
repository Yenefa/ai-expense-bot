package com.expense.tracker.data.prefs

import android.content.SharedPreferences
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

    /** 内存 SharedPreferences fake — 真 EncryptedSharedPreferences 需要 Android Keystore，单测里跑不动。 */
    private fun fakeSecure(): SharedPreferences = InMemorySharedPreferences()

    @Test fun defaultsAreCorrect() = runBlocking {
        val prefs = UserPrefs(fakeStore(), fakeSecure())
        val s = prefs.snapshot.first()
        assertThat(s.llmEnabled).isFalse()
        assertThat(s.baseUrl).isEqualTo("https://api.openai.com/v1")
        assertThat(s.apiKey).isEmpty()
        assertThat(s.model).isEqualTo("gpt-4o-mini")
    }

    @Test fun setLlmEnabledPersists() = runBlocking {
        val prefs = UserPrefs(fakeStore(), fakeSecure())
        prefs.setLlmEnabled(true)
        assertThat(prefs.snapshot.first().llmEnabled).isTrue()
        prefs.setLlmEnabled(false)
        assertThat(prefs.snapshot.first().llmEnabled).isFalse()
    }

    @Test fun setApiConfigPersists() = runBlocking {
        val prefs = UserPrefs(fakeStore(), fakeSecure())
        prefs.setApiConfig(baseUrl = "https://x.com/v1", apiKey = "sk-1", model = "claude")
        val s = prefs.snapshot.first()
        assertThat(s.baseUrl).isEqualTo("https://x.com/v1")
        assertThat(s.apiKey).isEqualTo("sk-1")
        assertThat(s.model).isEqualTo("claude")
    }

    @Test fun apiKeyStoredInSecureNotDataStore() = runBlocking {
        val ds = fakeStore()
        val secure = fakeSecure()
        val prefs = UserPrefs(ds, secure)
        prefs.setApiConfig(baseUrl = "u", apiKey = "sk-secret", model = "m")
        // DataStore 里不应该出现 apiKey
        val raw = ds.data.first()
        assertThat(raw[UserPrefs.LEGACY_API_KEY]).isNull()
        // 加密存储里有
        assertThat(secure.getString("llm_api_key", null)).isEqualTo("sk-secret")
    }

    @Test fun legacyPlaintextKeyMigratesOnRead() = runBlocking {
        // 模拟旧版本：apiKey 还残留在 DataStore 明文里
        val seed = mutablePreferencesOf().toMutablePreferences().apply {
            this[UserPrefs.LEGACY_API_KEY] = "sk-legacy"
        }
        val ds = fakeStore(seed)
        val secure = fakeSecure()
        val prefs = UserPrefs(ds, secure)

        val s = prefs.snapshot.first()
        assertThat(s.apiKey).isEqualTo("sk-legacy")
        // 第二次读时 DataStore 已被清干净，加密存储里有
        prefs.snapshot.first()
        assertThat(ds.data.first()[UserPrefs.LEGACY_API_KEY]).isNull()
        assertThat(secure.getString("llm_api_key", null)).isEqualTo("sk-legacy")
    }
}

/**
 * 极简 SharedPreferences 内存实现，只覆盖 UserPrefs 实际用到的方法。
 * 不是通用 fake — listener / commit / contains 都没实现（用到会立刻报错暴露）。
 */
private class InMemorySharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()
    override fun getString(key: String, defValue: String?): String? = (data[key] as? String) ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?) = error("unused")
    override fun getInt(key: String, defValue: Int) = (data[key] as? Int) ?: defValue
    override fun getLong(key: String, defValue: Long) = (data[key] as? Long) ?: defValue
    override fun getFloat(key: String, defValue: Float) = (data[key] as? Float) ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = (data[key] as? Boolean) ?: defValue
    override fun contains(key: String) = data.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun getAll(): MutableMap<String, *> = data.toMutableMap()

    private inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearAll = false
        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { removals += key }
        override fun clear() = apply { clearAll = true }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clearAll) data.clear()
            removals.forEach { data.remove(it) }
            pending.forEach { (k, v) -> if (v == null) data.remove(k) else data[k] = v }
        }
    }
}
