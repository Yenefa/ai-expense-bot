package com.expense.tracker.data.budget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

// 文件损坏时替换为空 Preferences，保证预算流仍可订阅。
private val Context.budgetDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "budget_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

data class BudgetSnapshot(
    val monthlyLimitCents: Long = 0L,
    val categoryLimitsCents: Map<String, Long> = emptyMap(),
)

/** 预算设置存储（独立 DataStore，不参与 API Key 迁移逻辑）。 */
class BudgetPrefs(
    private val store: DataStore<Preferences>,
) {
    val snapshot: Flow<BudgetSnapshot> = store.data.map { p ->
        BudgetSnapshot(
            monthlyLimitCents = p[Keys.MONTHLY_LIMIT] ?: 0L,
            categoryLimitsCents = p[Keys.CATEGORY_LIMITS].orEmpty()
                .mapNotNull { entry ->
                    val parts = entry.split(":")
                    if (parts.size == 2) {
                        val cents = parts[1].toLongOrNull()
                        if (cents != null && cents > 0L) parts[0] to cents else null
                    } else {
                        null
                    }
                }
                .toMap(),
        )
    }.flowOn(Dispatchers.IO)

    suspend fun save(monthlyLimitCents: Long, categoryLimitsCents: Map<String, Long>) {
        store.edit { p ->
            if (monthlyLimitCents > 0L) {
                p[Keys.MONTHLY_LIMIT] = monthlyLimitCents
            } else {
                p.remove(Keys.MONTHLY_LIMIT)
            }
            val entries = categoryLimitsCents
                .filterValues { it > 0L }
                .map { (id, cents) -> "$id:$cents" }
                .toSet()
            if (entries.isEmpty()) {
                p.remove(Keys.CATEGORY_LIMITS)
            } else {
                p[Keys.CATEGORY_LIMITS] = entries
            }
        }
    }

    suspend fun clearAll() {
        store.edit { p ->
            p.remove(Keys.MONTHLY_LIMIT)
            p.remove(Keys.CATEGORY_LIMITS)
        }
    }

    private object Keys {
        val MONTHLY_LIMIT = longPreferencesKey("monthly_budget_cents")
        val CATEGORY_LIMITS = stringSetPreferencesKey("category_budget_cents")
    }

    companion object {
        fun fromContext(context: Context): BudgetPrefs =
            BudgetPrefs(context.applicationContext.budgetDataStore)
    }
}
