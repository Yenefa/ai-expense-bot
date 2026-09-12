package com.expense.tracker.data.repo

import com.expense.tracker.data.db.ChatMessageDao
import com.expense.tracker.data.db.ChatMessageEntity
import com.expense.tracker.data.db.ExpenseDao
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.importer.ImportedExpense
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Test

private class FakeExpenseDao : ExpenseDao {
    private val state = MutableStateFlow<List<ExpenseEntity>>(emptyList())
    private var seq = 0L
    override suspend fun insert(expense: ExpenseEntity): Long {
        seq++
        state.value = state.value + expense.copy(id = seq)
        return seq
    }
    override suspend fun insertAll(expenses: List<ExpenseEntity>): List<Long> =
        expenses.map { insert(it) }
    override suspend fun clearAll() { state.value = emptyList() }
    override suspend fun update(expense: ExpenseEntity) {
        state.value = state.value.map { if (it.id == expense.id) expense else it }
    }
    override fun observeActive(): Flow<List<ExpenseEntity>> = state
    override suspend fun getAllActiveOnce(): List<ExpenseEntity> = state.value.filter { it.deletedAt == null }
    override suspend fun getAllOnce(): List<ExpenseEntity> = state.value
    override suspend fun getById(id: Long): ExpenseEntity? = state.value.firstOrNull { it.id == id }
    override fun observeInRange(from: Long, to: Long): Flow<List<ExpenseEntity>> =
        flow { emit(state.value.filter { it.occurredAt in from until to && it.deletedAt == null }) }
    override fun observeDeleted(): Flow<List<ExpenseEntity>> =
        flow { emit(state.value.filter { it.deletedAt != null }) }
    override suspend fun softDeleteById(id: Long, deletedAtMillis: Long) {
        state.value = state.value.map { if (it.id == id) it.copy(deletedAt = deletedAtMillis) else it }
    }
    override suspend fun restoreById(id: Long) {
        state.value = state.value.map { if (it.id == id) it.copy(deletedAt = null) else it }
    }
    override suspend fun deleteById(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }
    override suspend fun purgeOlderThan(cutoffMillis: Long) {}
}

private class FakeChatDao : ChatMessageDao {
    private val state = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    private var seq = 0L
    override suspend fun insert(msg: ChatMessageEntity): Long {
        seq++
        state.value = state.value + msg.copy(id = seq)
        return seq
    }
    override suspend fun insertAll(messages: List<ChatMessageEntity>): List<Long> =
        messages.map { insert(it) }
    override suspend fun update(msg: ChatMessageEntity) {
        state.value = state.value.map { if (it.id == msg.id) msg else it }
    }
    override fun observeAll(): Flow<List<ChatMessageEntity>> = state
    override suspend fun getAllOnce(): List<ChatMessageEntity> = state.value
    override suspend fun getRecent(limit: Int): List<ChatMessageEntity> = state.value.takeLast(limit)
    override suspend fun getById(id: Long): ChatMessageEntity? = state.value.firstOrNull { it.id == id }
    override suspend fun clearAll() { state.value = emptyList() }
}

class RepositoryTest {
    @Test fun expenseRepoAddPersists() = runBlocking {
        val repo = ExpenseRepository(FakeExpenseDao())
        val id = repo.addCents(amountCents = 3_500L, categoryId = "food", note = "午饭", occurredAt = 1L)
        assertThat(id).isGreaterThan(0L)
        val all = repo.observeActive().first()
        assertThat(all).hasSize(1)
        assertThat(all[0].amountCents).isEqualTo(3_500L)
    }

    @Test fun chatRepoAppendsBoth() = runBlocking {
        val repo = ChatRepository(FakeChatDao())
        repo.appendUser("hello", at = 100L)
        repo.appendAssistant("hi", at = 101L, relatedExpenseId = 7L)
        val all = repo.observeAll().first()
        assertThat(all).hasSize(2)
        assertThat(all[1].relatedExpenseId).isEqualTo(7L)
    }

    @Test fun chatRepoPersistsWholeExpenseBatchAndReadsBoundedHistory() = runBlocking {
        val repo = ChatRepository(FakeChatDao())
        repo.appendUser("第一条", at = 100L)
        repo.appendUser("第二条", at = 101L)
        repo.appendAssistant(
            text = "已记3笔",
            at = 102L,
            relatedExpenseIds = listOf(7L, 8L, 9L),
        )

        val recent = repo.getRecent(2)

        assertThat(recent.map { it.content }).containsExactly("第二条", "已记3笔").inOrder()
        assertThat(recent.last().relatedExpenseIds()).containsExactly(7L, 8L, 9L).inOrder()
        assertThat(recent.last().relatedExpenseId).isEqualTo(7L)
    }

    @Test fun expenseRepoBatchAddKeepsExactCents() = runBlocking {
        val repo = ExpenseRepository(FakeExpenseDao())

        val ids = repo.addAllCents(
            listOf(
                ExpenseDraft(1L, "other", "一分钱", 10L),
                ExpenseDraft(1_235L, "food", "午饭", 20L),
            )
        )

        assertThat(ids).hasSize(2)
        assertThat(repo.observeActive().first().map { it.amountCents })
            .containsExactly(1L, 1_235L).inOrder()
    }
    @Test fun expenseImportSkipsExistingAndWithinFileDuplicates() = runBlocking {
        val dao = FakeExpenseDao()
        dao.insert(
            ExpenseEntity(
                amountCents = 900L,
                categoryId = "drink",
                note = "菠萝百香果",
                occurredAt = 100L,
                createdAt = 101L,
            )
        )
        val repo = ExpenseRepository(dao)
        val duplicate = ImportedExpense(900L, "drink", "菠萝百香果", 100L, 101L)
        val newRow = ImportedExpense(3_500L, "food", "午饭", 200L, 201L)

        val result = repo.importExpenses(listOf(duplicate, newRow, newRow))

        assertThat(result.inserted).isEqualTo(1)
        assertThat(result.skippedDuplicates).isEqualTo(2)
        val all = repo.observeActive().first()
        assertThat(all).hasSize(2)
        assertThat(all.last().createdAt).isEqualTo(201L)
    }

    @Test fun expenseImportReinsertsAfterSoftDelete() = runBlocking {
        val dao = FakeExpenseDao()
        val repo = ExpenseRepository(dao)
        val row = ImportedExpense(3_500L, "food", "午饭", 200L, 201L)

        val first = repo.importExpenses(listOf(row))
        assertThat(first.inserted).isEqualTo(1)
        val insertedId = repo.getAllActiveOnce().single().id

        repo.softDelete(insertedId)

        val second = repo.importExpenses(listOf(row))

        assertThat(second.inserted).isEqualTo(1)
        assertThat(second.skippedDuplicates).isEqualTo(0)
        assertThat(repo.getAllActiveOnce()).hasSize(1)
    }
}
