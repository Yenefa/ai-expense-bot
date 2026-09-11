package com.expense.tracker.data.backup

import androidx.room.withTransaction
import com.expense.tracker.data.db.AppDatabase
import com.expense.tracker.data.export.BackupData
import com.expense.tracker.data.export.BackupPreferences
import com.expense.tracker.data.export.DataExporter
import com.expense.tracker.data.prefs.UserPrefs
import com.expense.tracker.memory.UserProfileStore
import kotlinx.coroutines.flow.first

class BackupRepository(
    private val database: AppDatabase,
    private val userPrefs: UserPrefs,
    private val userProfileStore: UserProfileStore,
) {
    suspend fun createBackup(sourceAppVersion: String, exportedAtIso: String): String {
        val preferences = userPrefs.snapshot.first()
        val memoryFacts = userProfileStore.facts.first()
        val (expenses, chatMessages, recurringRules) = database.withTransaction {
            Triple(
                database.expenseDao().getAllOnce(),
                database.chatDao().getAllOnce(),
                database.recurringRuleDao().observeAll().first(),
            )
        }
        return DataExporter.toBackupJson(
            expenses = expenses,
            chatMessages = chatMessages,
            preferences = BackupPreferences(
                llmEnabled = preferences.llmEnabled,
                baseUrl = preferences.baseUrl,
                model = preferences.model,
                themeMode = preferences.themeMode,
            ),
            sourceAppVersion = sourceAppVersion,
            exportedAtIso = exportedAtIso,
            recurringRules = recurringRules,
            memoryFacts = memoryFacts,
        )
    }

    fun parseBackup(content: String): BackupData = DataExporter.parseBackup(content)

    suspend fun restore(backup: BackupData) {
        database.withTransaction {
            database.chatDao().clearAll()
            database.expenseDao().clearAll()
            if (backup.expenses.isNotEmpty()) database.expenseDao().insertAll(backup.expenses)
            if (backup.chatMessages.isNotEmpty()) database.chatDao().insertAll(backup.chatMessages)
            database.recurringRuleDao().clearAll()
            if (backup.recurringRules.isNotEmpty()) {
                database.recurringRuleDao().insertAll(backup.recurringRules)
            }
        }
        userPrefs.restoreNonSecret(
            llmEnabled = backup.preferences.llmEnabled,
            baseUrl = backup.preferences.baseUrl,
            model = backup.preferences.model,
            themeMode = backup.preferences.themeMode,
        )
        userProfileStore.save(backup.memoryFacts)
    }
}
