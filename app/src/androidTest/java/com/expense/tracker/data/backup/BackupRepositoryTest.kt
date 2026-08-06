package com.expense.tracker.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.expense.tracker.data.db.AppDatabase
import com.expense.tracker.data.db.ChatMessageEntity
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.prefs.ThemeMode
import com.expense.tracker.data.prefs.UserPrefs
import com.expense.tracker.data.prefs.ApiKeyStorage
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var prefs: UserPrefs
    private lateinit var repository: BackupRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        prefs = UserPrefs(InMemoryPreferencesStore(), InMemoryApiKeyStorage())
        repository = BackupRepository(db, prefs)
    }

    @After fun tearDown() = db.close()

    @Test fun restoreReplacesDatabasePreservesIdsAndRestoresNonSecretPreferences() = runBlocking {
        db.expenseDao().insert(ExpenseEntity(1_250L, "food", "午饭", 100L, 101L, deletedAt = 102L, id = 7L))
        db.chatDao().insert(ChatMessageEntity("assistant", "已记录", 103L, relatedExpenseId = 7L, id = 8L))
        prefs.setApiConfig("https://backup.example/v1", "backup-secret", "backup-model")
        prefs.setLlmEnabled(true)
        prefs.setThemeMode(ThemeMode.DARK)
        val backupJson = repository.createBackup("3.6", "2026-07-31T10:00:00Z")

        db.expenseDao().insert(ExpenseEntity(9_900L, "shopping", "后来数据", 200L, 201L, id = 99L))
        db.chatDao().insert(ChatMessageEntity("user", "后来消息", 202L, id = 100L))
        prefs.setApiConfig("https://current.example/v1", "current-secret", "current-model")
        prefs.setLlmEnabled(false)
        prefs.setThemeMode(ThemeMode.LIGHT)

        repository.restore(repository.parseBackup(backupJson))

        assertThat(db.expenseDao().getAllOnce()).containsExactly(
            ExpenseEntity(1_250L, "food", "午饭", 100L, 101L, deletedAt = 102L, id = 7L),
        )
        assertThat(db.chatDao().getAllOnce()).containsExactly(
            ChatMessageEntity("assistant", "已记录", 103L, relatedExpenseId = 7L, id = 8L),
        )
        val restoredPrefs = prefs.snapshot.first()
        assertThat(restoredPrefs.llmEnabled).isTrue()
        assertThat(restoredPrefs.baseUrl).isEqualTo("https://backup.example/v1")
        assertThat(restoredPrefs.model).isEqualTo("backup-model")
        assertThat(restoredPrefs.themeMode).isEqualTo(ThemeMode.DARK)
        assertThat(restoredPrefs.apiKey).isEqualTo("current-secret")
    }
}

private class InMemoryPreferencesStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

private class InMemoryApiKeyStorage : ApiKeyStorage {
    private var value = ""
    override fun read(): String = value
    override fun write(value: String) { this.value = value }
}
