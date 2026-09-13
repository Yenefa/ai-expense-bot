package com.expense.tracker.ui.billimport

import com.expense.tracker.data.db.ChatMessageDao
import com.expense.tracker.data.db.ChatMessageEntity
import com.expense.tracker.data.db.ExpenseDao
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.prefs.ThemeMode
import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.expense.tracker.data.repo.ChatRepository
import com.expense.tracker.data.repo.ExpenseRepository
import com.expense.tracker.llm.AiAccessUnavailableException
import com.expense.tracker.llm.BillImportResult
import com.expense.tracker.llm.ParsedExpense
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BillImportViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    @Test fun repeatedConfirmOnlyInsertsOnce() = runTest(dispatcher) {
        val expenseDao = BillExpenseDao()
        val vm = BillImportViewModel(
            importHandler = { _, _ -> BillImportResult.Ok(
                expenses = listOf(ParsedExpense(1_234L, "food", "午饭", null)),
                reply = "ok",
            ) },
            expenseRepo = ExpenseRepository(expenseDao),
            chatRepo = ChatRepository(BillChatDao()),
        )
        val prefs = UserPrefsSnapshot(false, "", "", "", ThemeMode.SYSTEM)
        vm.importFromText("午饭 12.34", prefs)
        dispatcher.scheduler.runCurrent()

        vm.confirmImport()
        vm.confirmImport()
        dispatcher.scheduler.advanceUntilIdle()

        assertThat(expenseDao.rows).hasSize(1)
        assertThat(vm.uiState.value.phase).isEqualTo(BillImportPhase.Done)
    }

    @Test fun handlerFailureBecomesErrorStateWithoutInsertingExpenses() = runTest(dispatcher) {
        val expenseDao = BillExpenseDao()
        val vm = BillImportViewModel(
            // 模拟无会员且未配置 BYOK：handler 直接抛 AI 服务未配置异常。
            importHandler = { _, _ -> throw AiAccessUnavailableException() },
            expenseRepo = ExpenseRepository(expenseDao),
            chatRepo = ChatRepository(BillChatDao()),
        )
        val prefs = UserPrefsSnapshot(false, "", "", "", ThemeMode.SYSTEM)

        vm.importFromText("午饭 12.34", prefs)
        dispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.uiState.value.phase).isEqualTo(BillImportPhase.Error)
        assertThat(vm.uiState.value.errorMessage).contains("AI 服务未配置")
        assertThat(expenseDao.rows).isEmpty()
    }
}

private class BillExpenseDao : ExpenseDao {
    val rows = mutableListOf<ExpenseEntity>()
    override suspend fun insert(expense: ExpenseEntity): Long {
        rows += expense.copy(id = rows.size + 1L)
        return rows.size.toLong()
    }
    override suspend fun insertAll(expenses: List<ExpenseEntity>) = expenses.map { insert(it) }
    override suspend fun clearAll() = rows.clear()
    override suspend fun update(expense: ExpenseEntity) = Unit
    override fun observeActive(): Flow<List<ExpenseEntity>> = flowOf(rows)
    override suspend fun getAllActiveOnce() = rows.filter { it.deletedAt == null }
    override suspend fun getAllOnce() = rows.toList()
    override suspend fun getById(id: Long) = rows.firstOrNull { it.id == id }
    override fun observeInRange(from: Long, to: Long): Flow<List<ExpenseEntity>> = flowOf(emptyList())
    override fun observeDeleted(): Flow<List<ExpenseEntity>> = flowOf(emptyList())
    override suspend fun softDeleteById(id: Long, deletedAtMillis: Long) = Unit
    override suspend fun restoreById(id: Long) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun purgeOlderThan(cutoffMillis: Long) = Unit
}

private class BillChatDao : ChatMessageDao {
    override suspend fun insert(msg: ChatMessageEntity) = 1L
    override suspend fun insertAll(messages: List<ChatMessageEntity>) = messages.map { 1L }
    override suspend fun update(msg: ChatMessageEntity) = Unit
    override fun observeAll(): Flow<List<ChatMessageEntity>> = flowOf(emptyList())
    override suspend fun getAllOnce() = emptyList<ChatMessageEntity>()
    override suspend fun getRecent(limit: Int) = emptyList<ChatMessageEntity>()
    override suspend fun getById(id: Long): ChatMessageEntity? = null
    override suspend fun clearAll() = Unit
}
