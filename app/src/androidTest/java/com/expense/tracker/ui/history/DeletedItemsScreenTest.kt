package com.expense.tracker.ui.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.expense.tracker.data.db.ExpenseDao
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.repo.ExpenseRepository
import com.expense.tracker.ui.theme.AppTheme
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test

class DeletedItemsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun permanentDeleteRequiresExplicitConfirmation() {
        val dao = RecordingExpenseDao()
        compose.setContent {
            AppTheme { DeletedItemsScreen(ExpenseRepository(dao), onClose = {}) }
        }

        compose.onNodeWithContentDescription("彻底删除").performClick()

        assertThat(dao.permanentlyDeletedIds).isEmpty()
        compose.onNodeWithText("永久删除账目？").assertIsDisplayed()
        compose.onNodeWithText("永久删除").performClick()
        compose.waitUntil { dao.permanentlyDeletedIds.contains(7L) }
    }
}

private class RecordingExpenseDao : ExpenseDao {
    private val item = ExpenseEntity(
        amountCents = 1_250L,
        categoryId = "food",
        note = "午饭",
        occurredAt = 100L,
        createdAt = 101L,
        deletedAt = 102L,
        id = 7L,
    )
    private val deleted = MutableStateFlow(listOf(item))
    val permanentlyDeletedIds = mutableListOf<Long>()

    override suspend fun insert(expense: ExpenseEntity): Long = expense.id
    override suspend fun insertAll(expenses: List<ExpenseEntity>): List<Long> = expenses.map { it.id }
    override suspend fun clearAll() { deleted.value = emptyList() }
    override suspend fun update(expense: ExpenseEntity) = Unit
    override fun observeActive(): Flow<List<ExpenseEntity>> = flowOf(emptyList())
    override suspend fun getAllActiveOnce(): List<ExpenseEntity> = emptyList()
    override suspend fun getAllOnce(): List<ExpenseEntity> = deleted.value
    override suspend fun getById(id: Long): ExpenseEntity? = deleted.value.firstOrNull { it.id == id }
    override fun observeInRange(from: Long, to: Long): Flow<List<ExpenseEntity>> = flowOf(emptyList())
    override fun observeDeleted(): Flow<List<ExpenseEntity>> = deleted
    override suspend fun softDeleteById(id: Long, deletedAtMillis: Long) = Unit
    override suspend fun restoreById(id: Long) { deleted.value = deleted.value.filterNot { it.id == id } }
    override suspend fun deleteById(id: Long) {
        permanentlyDeletedIds += id
        deleted.value = deleted.value.filterNot { it.id == id }
    }
    override suspend fun purgeOlderThan(cutoffMillis: Long) = Unit
}
