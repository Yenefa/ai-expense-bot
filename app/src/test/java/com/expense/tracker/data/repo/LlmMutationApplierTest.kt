package com.expense.tracker.data.repo

import com.expense.tracker.data.db.ChatMessageDao
import com.expense.tracker.data.db.ChatMessageEntity
import com.expense.tracker.data.db.ExpenseDao
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.llm.LlmParseResult
import com.expense.tracker.llm.LlmMutationPlanner
import com.expense.tracker.llm.MutationConflictException
import com.expense.tracker.llm.ParsedAction
import com.expense.tracker.llm.ParsedExpense
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test

class LlmMutationApplierTest {
    @Test fun changedTargetBeforeConfirmationRejectsWholePlan() = runBlocking {
        val dao = AtomicExpenseDao().apply {
            rows += ExpenseEntity(500L, "food", "旧午饭", 10L, 11L, id = 7L)
            rows += ExpenseEntity(800L, "drink", "旧饮料", 12L, 13L, id = 8L)
        }
        val chatDao = AtomicChatDao()
        val transaction = SnapshotTransactionRunner(dao, chatDao)
        val applier = LlmMutationApplier(ExpenseRepository(dao), ChatRepository(chatDao), transaction)
        val plan = LlmMutationPlanner.create(
            result = LlmParseResult(
                reply = "候选",
                expenses = emptyList(),
                actions = listOf(
                    ParsedAction.Update(7L, 600L, null, null, null),
                    ParsedAction.Update(8L, 900L, null, null, null),
                ),
            ),
            nowMillis = 1_000L,
            availableRecords = dao.rows.toList(),
            lastBatchIds = emptyList(),
            currentText = "修改两笔",
            targetDate = null,
        )
        dao.rows.replaceAll { if (it.id == 8L) it.copy(note = "已被手动编辑") else it }

        assertThrows(MutationConflictException::class.java) {
            runBlocking { applier.apply(plan) }
        }

        assertThat(dao.rows.single { it.id == 7L }.amountCents).isEqualTo(500L)
        assertThat(dao.rows.single { it.id == 8L }.amountCents).isEqualTo(800L)
    }

    @Test fun secondWriteFailureRollsBackEntireResponse() = runBlocking {
        val dao = AtomicExpenseDao(failOnInsertNumber = 2)
        val chatDao = AtomicChatDao()
        val transaction = SnapshotTransactionRunner(dao, chatDao)
        val applier = LlmMutationApplier(ExpenseRepository(dao), ChatRepository(chatDao), transaction)
        val result = LlmParseResult(
            reply = "两笔",
            expenses = listOf(
                ParsedExpense(1_000L, "food", "第一笔", null),
                ParsedExpense(2_000L, "drink", "第二笔", null),
            ),
        )
        val plan = LlmMutationPlanner.create(
            result = result,
            nowMillis = 1_000L,
            availableRecords = emptyList(),
            lastBatchIds = emptyList(),
            currentText = "新增两笔",
            targetDate = null,
        )

        assertThrows(IllegalStateException::class.java) {
            runBlocking { applier.apply(plan) }
        }

        assertThat(transaction.calls).isEqualTo(1)
        assertThat(dao.rows).isEmpty()
    }

    @Test fun multipleUpdatesUseOneTransactionAndPersistAssistantBatch() = runBlocking {
        val dao = AtomicExpenseDao().apply {
            rows += ExpenseEntity(500L, "food", "旧午饭", 10L, 11L, id = 7L)
            rows += ExpenseEntity(800L, "drink", "旧饮料", 12L, 13L, id = 8L)
        }
        val chatDao = AtomicChatDao()
        val transaction = SnapshotTransactionRunner(dao, chatDao)
        val applier = LlmMutationApplier(ExpenseRepository(dao), ChatRepository(chatDao), transaction)
        val result = LlmParseResult(
            reply = "已处理",
            expenses = emptyList(),
            actions = listOf(
                ParsedAction.Update(7L, 678L, "food", "新午饭", 20L),
                ParsedAction.Update(8L, 900L, null, "新饮料", null),
            ),
        )
        val plan = LlmMutationPlanner.create(
            result = result,
            nowMillis = 1_000L,
            availableRecords = dao.rows.toList(),
            lastBatchIds = emptyList(),
            currentText = "修改午饭和饮料",
            targetDate = null,
        )

        val applied = applier.apply(plan)

        assertThat(transaction.calls).isEqualTo(1)
        assertThat(applied.insertedIds).isEmpty()
        assertThat(applied.affectedIds).containsExactly(7L, 8L).inOrder()
        assertThat(dao.rows.single { it.id == 7L }.amountCents).isEqualTo(678L)
        assertThat(dao.rows.single { it.id == 7L }.note).isEqualTo("新午饭")
        assertThat(dao.rows.single { it.id == 8L }.amountCents).isEqualTo(900L)
        assertThat(chatDao.rows).hasSize(1)
        assertThat(chatDao.rows.single().content).isEqualTo("已处理")
        assertThat(chatDao.rows.single().relatedExpenseIds()).containsExactly(7L, 8L).inOrder()
    }

    @Test fun assistantBatchWriteFailureRollsBackExpenseMutation() = runBlocking {
        val dao = AtomicExpenseDao()
        val chatDao = AtomicChatDao(failOnInsert = true)
        val transaction = SnapshotTransactionRunner(dao, chatDao)
        val applier = LlmMutationApplier(ExpenseRepository(dao), ChatRepository(chatDao), transaction)
        val plan = LlmMutationPlanner.create(
            result = LlmParseResult(
                reply = "已记1笔",
                expenses = listOf(ParsedExpense(1_000L, "food", "午饭", null)),
            ),
            nowMillis = 1_000L,
            availableRecords = emptyList(),
            lastBatchIds = emptyList(),
            currentText = "午饭10元",
            targetDate = null,
        )

        assertThrows(IllegalStateException::class.java) {
            runBlocking { applier.apply(plan) }
        }

        assertThat(dao.rows).isEmpty()
        assertThat(chatDao.rows).isEmpty()
    }
}

private class SnapshotTransactionRunner(
    private val dao: AtomicExpenseDao,
    private val chatDao: AtomicChatDao,
) : TransactionRunner {
    var calls = 0

    override suspend fun <T> run(block: suspend () -> T): T {
        calls++
        val before = dao.rows.toList()
        val chatBefore = chatDao.rows.toList()
        return try {
            block()
        } catch (error: Throwable) {
            dao.rows.clear()
            dao.rows.addAll(before)
            chatDao.rows.clear()
            chatDao.rows.addAll(chatBefore)
            throw error
        }
    }
}

private class AtomicChatDao(
    private val failOnInsert: Boolean = false,
) : ChatMessageDao {
    val rows = mutableListOf<ChatMessageEntity>()
    private val observed = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(msg: ChatMessageEntity): Long {
        if (failOnInsert) error("forced chat insert failure")
        val id = if (msg.id > 0L) msg.id else nextId++
        rows += msg.copy(id = id)
        observed.value = rows.toList()
        return id
    }
    override suspend fun insertAll(messages: List<ChatMessageEntity>): List<Long> = messages.map { insert(it) }
    override suspend fun update(msg: ChatMessageEntity) {
        rows.replaceAll { if (it.id == msg.id) msg else it }
        observed.value = rows.toList()
    }
    override fun observeAll(): Flow<List<ChatMessageEntity>> = observed
    override suspend fun getAllOnce(): List<ChatMessageEntity> = rows.toList()
    override suspend fun getRecent(limit: Int): List<ChatMessageEntity> = rows.takeLast(limit)
    override suspend fun getById(id: Long): ChatMessageEntity? = rows.firstOrNull { it.id == id }
    override suspend fun clearAll() {
        rows.clear()
        observed.value = emptyList()
    }
}

private class AtomicExpenseDao(
    private val failOnInsertNumber: Int? = null,
) : ExpenseDao {
    val rows = mutableListOf<ExpenseEntity>()
    private var insertCount = 0
    private var nextId = 100L

    override suspend fun insert(expense: ExpenseEntity): Long {
        insertCount++
        if (insertCount == failOnInsertNumber) error("forced insert failure")
        val id = if (expense.id > 0L) expense.id else nextId++
        rows += expense.copy(id = id)
        return id
    }

    override suspend fun insertAll(expenses: List<ExpenseEntity>): List<Long> = expenses.map { insert(it) }
    override suspend fun clearAll() = rows.clear()
    override suspend fun update(expense: ExpenseEntity) {
        rows.replaceAll { if (it.id == expense.id) expense else it }
    }
    override fun observeActive(): Flow<List<ExpenseEntity>> = flowOf(rows.filter { it.deletedAt == null })
    override suspend fun getAllActiveOnce() = rows.filter { it.deletedAt == null }
    override suspend fun getAllOnce() = rows.toList()
    override suspend fun getById(id: Long) = rows.firstOrNull { it.id == id }
    override fun observeInRange(from: Long, to: Long): Flow<List<ExpenseEntity>> = flowOf(emptyList())
    override fun observeDeleted(): Flow<List<ExpenseEntity>> = flowOf(rows.filter { it.deletedAt != null })
    override suspend fun softDeleteById(id: Long, deletedAtMillis: Long) {
        rows.replaceAll { if (it.id == id) it.copy(deletedAt = deletedAtMillis) else it }
    }
    override suspend fun restoreById(id: Long) {
        rows.replaceAll { if (it.id == id) it.copy(deletedAt = null) else it }
    }
    override suspend fun deleteById(id: Long) { rows.removeAll { it.id == id } }
    override suspend fun purgeOlderThan(cutoffMillis: Long) { rows.removeAll { (it.deletedAt ?: Long.MAX_VALUE) < cutoffMillis } }
}
