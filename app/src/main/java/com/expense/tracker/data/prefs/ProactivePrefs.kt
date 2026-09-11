package com.expense.tracker.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.expense.tracker.proactive.ProactiveAlertRecord
import com.expense.tracker.proactive.ProactiveAlertType
import com.expense.tracker.proactive.ProactiveSeverity
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

@Serializable
private data class ProactiveHistoryJson(
    val entries: List<ProactiveHistoryJsonEntry> = emptyList(),
)

@Serializable
private data class ProactiveHistoryJsonEntry(
    val type: String,
    val severity: String,
    val copy: String,
    @SerialName("created_at") val createdAt: Long,
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

    /** 提醒中心历史（新→旧），UI 直接订阅。 */
    val historyFlow: Flow<List<ProactiveAlertRecord>> = store.data
        .map { prefs -> parseHistory(prefs[HISTORY_KEY]).entries.reversed().mapNotNull { it.toRecord() } }
        .flowOn(Dispatchers.IO)

    /** 未读数：历史中 createdAt 晚于已读水位的条数（同一条 flow 内解析历史与水位）。 */
    val unreadCount: Flow<Int> = store.data
        .map { prefs ->
            val readWatermark = prefs[LAST_READ_AT_KEY] ?: 0L
            parseHistory(prefs[HISTORY_KEY]).entries.count { it.createdAt > readWatermark }
        }
        .flowOn(Dispatchers.IO)

    override suspend fun record(
        typeWire: String,
        severityWire: String,
        copy: String,
        nowMillis: Long,
        dayKey: String,
    ) {
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

            // 同一事务内落提醒中心历史；只保留最近 MAX_HISTORY 条。
            val history = parseHistory(prefs[HISTORY_KEY])
            val entries = (history.entries + ProactiveHistoryJsonEntry(
                type = typeWire,
                severity = severityWire,
                copy = copy,
                createdAt = nowMillis,
            )).takeLast(MAX_HISTORY)
            prefs[HISTORY_KEY] = json.encodeToString(ProactiveHistoryJson.serializer(), ProactiveHistoryJson(entries))
        }
    }

    override suspend fun history(): List<ProactiveAlertRecord> = historyFlow.first()

    /** 提醒中心已读：写入当前时刻作为已读水位，未读数随之归零。 */
    suspend fun markHistoryRead(atMillis: Long = System.currentTimeMillis()) {
        store.edit { prefs -> prefs[LAST_READ_AT_KEY] = atMillis }
    }

    override suspend fun clearHistory() {
        // 清空历史的同时重置已读水位：空历史没有未读。
        store.edit { prefs ->
            prefs.remove(HISTORY_KEY)
            prefs[LAST_READ_AT_KEY] = 0L
        }
    }

    private fun parseHistory(raw: String?): ProactiveHistoryJson =
        raw?.let { runCatching { json.decodeFromString(ProactiveHistoryJson.serializer(), it) }.getOrNull() }
            ?: ProactiveHistoryJson()

    private fun ProactiveHistoryJsonEntry.toRecord(): ProactiveAlertRecord? {
        val type = ProactiveAlertType.fromWire(type)
        val severity = ProactiveSeverity.fromWire(severity)
        if (type == null || severity == null || copy.isBlank()) return null
        return ProactiveAlertRecord(type = type, severity = severity, copy = copy, createdAtMillis = createdAt)
    }

    private fun keyFor(type: ProactiveAlertType) = booleanPreferencesKey("enabled_${type.wire}")

    companion object {
        private val STATE_KEY = stringPreferencesKey("alert_state_json")
        private val HISTORY_KEY = stringPreferencesKey("alert_history_json")
        private val LAST_READ_AT_KEY = longPreferencesKey("alert_last_read_at")
        const val MAX_HISTORY = 50

        fun create(context: Context): ProactivePrefs =
            ProactivePrefs(context.applicationContext.proactiveDataStore)
    }
}
