package com.expense.tracker.memory

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

// 文件损坏时替换为空 Preferences，避免 facts 流因 CorruptionException 崩溃。
private val Context.userProfileDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_profile_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** 长期记忆（UserProfile）持久化接口；只有 MemoryGovernor 在用户确认/管理操作时写入。 */
interface UserProfileStore {
    val facts: Flow<List<MemoryFact>>
    suspend fun append(fact: MemoryFact)
    /** 整体覆盖：用户管理（修改/删除/清空）与备份恢复使用。 */
    suspend fun save(facts: List<MemoryFact>)
}

/** DataStore 实现：独立文件，排除系统备份（与现有隐私策略一致）。 */
class UserProfilePrefs(
    private val store: DataStore<Preferences>,
) : UserProfileStore {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val facts: Flow<List<MemoryFact>> = store.data.map { prefs ->
        prefs[FACTS_KEY]?.let(::decode).orEmpty()
    }.flowOn(Dispatchers.IO)

    override suspend fun append(fact: MemoryFact) {
        store.edit { prefs ->
            val raw = prefs[FACTS_KEY]
            val current = raw?.let(::decode)
            // 原始 JSON 存在但无法解码：保留原文并拒绝覆盖，避免静默清空既有长期记忆。
            if (raw != null && current == null) return@edit
            prefs[FACTS_KEY] = json.encodeToString(FACTS_SERIALIZER, current.orEmpty() + fact)
        }
    }

    override suspend fun save(facts: List<MemoryFact>) {
        store.edit { prefs ->
            val raw = prefs[FACTS_KEY]
            // 同样拒绝覆盖损坏数据，交由后续人工/备份恢复处理。
            if (raw != null && decode(raw) == null) return@edit
            prefs[FACTS_KEY] = json.encodeToString(FACTS_SERIALIZER, facts)
        }
    }

    private fun decode(raw: String): List<MemoryFact>? =
        runCatching { json.decodeFromString(FACTS_SERIALIZER, raw) }.getOrNull()

    companion object {
        private val FACTS_KEY = stringPreferencesKey("memory_facts_json")
        private val FACTS_SERIALIZER = ListSerializer(MemoryFact.serializer())

        fun create(context: Context): UserProfilePrefs =
            UserProfilePrefs(context.applicationContext.userProfileDataStore)
    }
}
