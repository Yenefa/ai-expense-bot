package com.expense.tracker.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.expense.tracker.proactive.ProactiveAlertType
import com.expense.tracker.proactive.ProactiveState
import com.expense.tracker.proactive.ProactiveStateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.proactiveDataStore: DataStore<Preferences> by preferencesDataStore(name = "proactive_prefs")

@Serializable
private data class ProactiveStateJson(
    @SerialName("last_at") val lastAt: Map<String, Long> = emptyMap(),
    @SerialName("last_severity") val lastSeverity: Map<String, String> = emptyMap(),
    @SerialName("day_key") val dayKey: String? = null,
    @SerialName("count_today") val countToday: Int = 0,
)

/** 主动提醒的开关与运行状态（独立 DataStore，排除系统备份与迁移）。 */
class ProactivePrefs(
    private val store: DataStore<Preferences>,
) : ProactiveStateStore {

    private val json = Json { ignoreUnknownKeys = true }

    /** 已开启的提醒类型（默认全部开启；用户可逐类关闭）。 */
    val enabledTypes: Flow<Set<ProactiveAlertType>> = store.data.map { prefs ->
        ProactiveAlertType.entries.filter { prefs[keyFor(it)] ?: true }.toSet()
    }.flowOn(Dispatchers.IO)

    suspend fun enabledNow(): Set<ProactiveAlertType> = enabledTypes.first()

    suspend fun setEnabled(type: ProactiveAlertType, enabled: Boolean) {
        store.edit { prefs -> prefs[keyFor(type)] = enabled }
    }

    override suspend fun state(): ProactiveState {
        val raw = store.data.first()[STATE_KEY] ?: return ProactiveState()
        return runCatching { json.decodeFromString(ProactiveStateJson.serializer(), raw) }
            .getOrElse { ProactiveStateJson() }
            .let {
                ProactiveState(
                    lastAlertAtMillis = it.lastAt,
                    lastSeverity = it.lastSeverity,
                    dayKey = it.dayKey,
                    countToday = it.countToday,
                )
            }
    }

    override suspend fun record(typeWire: String, severityWire: String, nowMillis: Long, dayKey: String) {
        store.edit { prefs ->
            val current = prefs[STATE_KEY]
                ?.let { raw -> runCatching { json.decodeFromString(ProactiveStateJson.serializer(), raw) }.getOrNull() }
                ?: ProactiveStateJson()
            val countToday = if (current.dayKey == dayKey) current.countToday + 1 else 1
            val next = current.copy(
                lastAt = current.lastAt + (typeWire to nowMillis),
                lastSeverity = current.lastSeverity + (typeWire to severityWire),
                dayKey = dayKey,
                countToday = countToday,
            )
            prefs[STATE_KEY] = json.encodeToString(ProactiveStateJson.serializer(), next)
        }
    }

    private fun keyFor(type: ProactiveAlertType) = booleanPreferencesKey("enabled_${type.wire}")

    companion object {
        private val STATE_KEY = stringPreferencesKey("alert_state_json")

        fun create(context: Context): ProactivePrefs =
            ProactivePrefs(context.applicationContext.proactiveDataStore)
    }
}
