package com.expense.tracker.data.repo

import com.expense.tracker.data.db.ChatMessageDao
import com.expense.tracker.data.db.ChatMessageEntity
import com.expense.tracker.data.db.ExpenseDao
import com.expense.tracker.data.db.ExpenseEntity
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
    override fun observeAll(): Flow<List<ExpenseEntity>> = state
    override fun observeInRange(from: Long, to: Long): Flow<List<ExpenseEntity>> =
        flow { emit(state.value.filter { it.deletedAt == null && it.occurredAt in from until to }) }
    // 软删 — 跟真 DAO 行为一致
    override suspend fun softDeleteById(id: Long, at: Long) {
        state.value = state.value.map { if (it.id == id && it.deletedAt == null) it.copy(deletedAt = at) else it }
    }
    override suspend fun restoreById(id: Long) {
        state.value = state.value.map { if (it.id == id) it.copy(deletedAt = null) else it }
    }
    override suspend fun hardDeleteById(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }
    override suspend fun hardDeleteExpired(before: Long): Int {
        val before_count = state.value.size
        state.value = state.value.filterNot { row -> row.deletedAt?.let { it < before } == true }
        return before_count - state.value.size
    }
    override suspend fun hardDeleteAllTrashed(): Int {
        val before = state.value.size
        state.value = state.value.filterNot { it.deletedAt != null }
        return before - state.value.size
    }
    override fun observeTrashed(): Flow<List<ExpenseEntity>> =
        flow { emit(state.value.filter { it.deletedAt != null }.sortedByDescending { it.deletedAt }) }
    override suspend fun findByMatch(
        category: String?, amount: Double?, from: Long?, to: Long?, noteSub: String?,
    ): List<ExpenseEntity> = emptyList()
    override suspend fun patchById(id: Long, amount: Double?, cat: String?, note: String?) { }
}

private class FakeChatDao : ChatMessageDao {
    private val state = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    private var seq = 0L
    override suspend fun insert(msg: ChatMessageEntity): Long {
        seq++
        state.value = state.value + msg.copy(id = seq)
        return seq
    }
    override fun observeAll(): Flow<List<ChatMessageEntity>> = state
    override suspend fun clearAll() { state.value = emptyList() }
}

class RepositoryTest {
    @Test fun expenseRepoAddPersists() = runBlocking {
        val repo = ExpenseRepository(FakeExpenseDao())
        val id = repo.add(amount = 35.0, categoryId = "food", note = "午饭", occurredAt = 1L)
        assertThat(id).isGreaterThan(0L)
        val all = repo.observeAll().first()
        assertThat(all).hasSize(1)
        assertThat(all[0].amount).isEqualTo(35.0)
    }

    @Test fun chatRepoAppendsBoth() = runBlocking {
        val repo = ChatRepository(FakeChatDao())
        repo.appendUser("hello", at = 100L)
        repo.appendAssistant("hi", at = 101L, relatedExpenseId = 7L)
        val all = repo.observeAll().first()
        assertThat(all).hasSize(2)
        assertThat(all[1].relatedExpenseId).isEqualTo(7L)
    }
}
