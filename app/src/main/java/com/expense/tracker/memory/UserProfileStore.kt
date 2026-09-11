package com.expense.tracker.memory

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.userProfileDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_profile_prefs")

/** 长期记忆（UserProfile）持久化接口；只有 MemoryGovernor 在用户确认后调用 append。 */
interface UserProfileStore {
    val facts: Flow<List<MemoryFact>>
    suspend fun append(fact: MemoryFact)
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
            val current = prefs[FACTS_KEY]?.let(::decode).orEmpty()
            prefs[FACTS_KEY] = json.encodeToString(FACTS_SERIALIZER, current + fact)
        }
    }

    private fun decode(raw: String): List<MemoryFact> =
        runCatching { json.decodeFromString(FACTS_SERIALIZER, raw) }.getOrElse { emptyList() }

    companion object {
        private val FACTS_KEY = stringPreferencesKey("memory_facts_json")
        private val FACTS_SERIALIZER = ListSerializer(MemoryFact.serializer())

        fun create(context: Context): UserProfilePrefs =
            UserProfilePrefs(context.applicationContext.userProfileDataStore)
    }
}
