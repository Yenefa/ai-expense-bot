package com.expense.tracker.ui.billimport

import com.expense.tracker.data.db.ChatMessageDao
import com.expense.tracker.data.db.ChatMessageEntity
import com.expense.tracker.data.db.ExpenseDao
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.expense.tracker.data.repo.ChatRepository
import com.expense.tracker.data.repo.ExpenseRepository
import com.expense.tracker.llm.BillImportResult
import com.expense.tracker.llm.ParsedExpense
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BillImportViewModelTest {
    @Before fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun teardown() = Dispatchers.resetMain()

    private val prefs = UserPrefsSnapshot(true, "u", "k", "m")

    private fun makeVm(result: BillImportResult): Triple<BillImportViewModel, FakeExpenseDao, FakeChatDao> {
        val ed = FakeExpenseDao()
        val cd = FakeChatDao()
        val vm = BillImportViewModel(
            importHandler = { _, _ -> result },
            expenseRepo = ExpenseRepository(ed),
            chatRepo = ChatRepository(cd),
        )
        return Triple(vm, ed, cd)
    }

    @Test fun `importFromText Ok shows Result with all items selected`() = runTest {
        val (vm, _, _) = makeVm(BillImportResult.Ok(
            listOf(
                ParsedExpense(38.0, "drink", "星巴克", null),
                ParsedExpense(4.0, "transport", "地铁", null),
            ), "已识别2笔"))
        vm.importFromText("ocr", prefs)
        val s = vm.uiState.value
        assertThat(s.phase).isEqualTo(BillImportPhase.Result)
        assertThat(s.items).hasSize(2)
        assertThat(s.items.all { it.selected }).isTrue()
    }

    @Test fun `toggle and confirm inserts only selected and writes chat msg`() = runTest {
        val (vm, ed, cd) = makeVm(BillImportResult.Ok(
            listOf(
                ParsedExpense(38.0, "drink", "星巴克", null),
                ParsedExpense(4.0, "transport", "地铁", null),
            ), "ok"))
        vm.importFromText("ocr", prefs)
        vm.toggleSelected(1) // 剔除第二笔
        vm.confirmImport()
        assertThat(ed.snapshot()).hasSize(1)
        assertThat(ed.snapshot()[0].categoryId).isEqualTo("drink")
        assertThat(vm.uiState.value.phase).isEqualTo(BillImportPhase.Done)
        val msgs = cd.flow.first()
        assertThat(msgs.any { it.role == "assistant" && it.content.contains("1 笔") }).isTrue()
    }

    @Test fun `importFromText Error shows Error phase with message`() = runTest {
        val (vm, _, _) = makeVm(BillImportResult.Error("LLM HTTP 500"))
        vm.importFromText("ocr", prefs)
        assertThat(vm.uiState.value.phase).isEqualTo(BillImportPhase.Error)
        assertThat(vm.uiState.value.errorMessage).contains("500")
    }
}

private class FakeExpenseDao : ExpenseDao {
    private val state = MutableStateFlow<List<ExpenseEntity>>(emptyList())
    fun snapshot(): List<ExpenseEntity> = state.value
    override suspend fun insert(expense: ExpenseEntity): Long {
        val id = state.value.size + 1L
        state.value = state.value + expense.copy(id = id)
        return id
    }
    override suspend fun update(expense: ExpenseEntity) {
        state.value = state.value.map { if (it.id == expense.id) expense else it }
    }
    override fun observeActive(): Flow<List<ExpenseEntity>> = state
    override suspend fun getAllActiveOnce(): List<ExpenseEntity> = state.value.filter { it.deletedAt == null }
    override suspend fun getById(id: Long): ExpenseEntity? = state.value.firstOrNull { it.id == id }
    override fun observeInRange(from: Long, to: Long): Flow<List<ExpenseEntity>> = flowOf(emptyList())
    override fun observeDeleted(): Flow<List<ExpenseEntity>> = flowOf(emptyList())
    override suspend fun softDeleteById(id: Long, deletedAtMillis: Long) {}
    override suspend fun restoreById(id: Long) {}
    override suspend fun deleteById(id: Long) {}
    override suspend fun purgeOlderThan(cutoffMillis: Long) {}
}

private class FakeChatDao : ChatMessageDao {
    val flow = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    private var seq = 0L
    override suspend fun insert(msg: ChatMessageEntity): Long {
        seq++; flow.value = flow.value + msg.copy(id = seq); return seq
    }
    override suspend fun update(msg: ChatMessageEntity) {
        flow.value = flow.value.map { if (it.id == msg.id) msg else it }
    }
    override fun observeAll(): Flow<List<ChatMessageEntity>> = flow
    override suspend fun getAllOnce(): List<ChatMessageEntity> = flow.value
    override suspend fun getById(id: Long): ChatMessageEntity? = flow.value.firstOrNull { it.id == id }
    override suspend fun clearAll() { flow.value = emptyList() }
}
