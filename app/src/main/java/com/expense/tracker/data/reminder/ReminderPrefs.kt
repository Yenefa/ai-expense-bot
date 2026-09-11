package com.expense.tracker.data.reminder

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

private val Context.reminderDataStore: DataStore<Preferences> by preferencesDataStore(name = "reminder_prefs")

data class ReminderSnapshot(
    val enabled: Boolean = false,
    val hour: Int = 21,
    val minute: Int = 0,
) {
    val timeLabel: String get() = "%02d:%02d".format(java.util.Locale.US, hour, minute)
}

/** 每日记账提醒设置。 */
class ReminderPrefs(
    private val store: DataStore<Preferences>,
) {
    val snapshot: Flow<ReminderSnapshot> = store.data.map { p ->
        ReminderSnapshot(
            enabled = p[Keys.ENABLED] ?: false,
            hour = (p[Keys.HOUR] ?: 21).coerceIn(0, 23),
            minute = (p[Keys.MINUTE] ?: 0).coerceIn(0, 59),
        )
    }.flowOn(Dispatchers.IO)

    suspend fun save(enabled: Boolean, hour: Int, minute: Int) {
        store.edit { p ->
            p[Keys.ENABLED] = enabled
            p[Keys.HOUR] = hour.coerceIn(0, 23)
            p[Keys.MINUTE] = minute.coerceIn(0, 59)
        }
    }

    private object Keys {
        val ENABLED = booleanPreferencesKey("reminder_enabled")
        val HOUR = intPreferencesKey("reminder_hour")
        val MINUTE = intPreferencesKey("reminder_minute")
    }

    companion object {
        fun fromContext(context: Context): ReminderPrefs =
            ReminderPrefs(context.applicationContext.reminderDataStore)
    }
}
