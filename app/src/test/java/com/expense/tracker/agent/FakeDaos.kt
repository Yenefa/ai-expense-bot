package com.expense.tracker.agent

import com.expense.tracker.data.db.ChatMessageDao
import com.expense.tracker.data.db.ChatMessageEntity
import com.expense.tracker.data.db.ExpenseDao
import com.expense.tracker.data.db.ExpenseEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/** Agent 层 JVM 测试用的内存 DAO（与 RepositoryTest 的 Fake 保持同构）。 */
class FakeExpenseDao : ExpenseDao {
    val state = MutableStateFlow<List<ExpenseEntity>>(emptyList())
    private var seq = 0L

    override suspend fun insert(expense: ExpenseEntity): Long {
        seq++
        state.value = state.value + expense.copy(id = seq)
        return seq
    }

    override suspend fun insertAll(expenses: List<ExpenseEntity>): List<Long> = expenses.map { insert(it) }

    override suspend fun clearAll() {
        state.value = emptyList()
    }

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

class FakeChatDao : ChatMessageDao {
    private val state = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    private var seq = 0L

    override suspend fun insert(msg: ChatMessageEntity): Long {
        seq++
        state.value = state.value + msg.copy(id = seq)
        return seq
    }

    override suspend fun insertAll(messages: List<ChatMessageEntity>): List<Long> = messages.map { insert(it) }

    override suspend fun update(msg: ChatMessageEntity) {
        state.value = state.value.map { if (it.id == msg.id) msg else it }
    }

    override fun observeAll(): Flow<List<ChatMessageEntity>> = state

    override suspend fun getAllOnce(): List<ChatMessageEntity> = state.value

    override suspend fun getRecent(limit: Int): List<ChatMessageEntity> = state.value.takeLast(limit)

    override suspend fun getById(id: Long): ChatMessageEntity? = state.value.firstOrNull { it.id == id }

    override suspend fun clearAll() {
        state.value = emptyList()
    }
}
