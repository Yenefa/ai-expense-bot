package com.expense.tracker.ui.chat

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.expense.tracker.data.db.ChatMessageDao
import com.expense.tracker.data.db.ChatMessageEntity
import com.expense.tracker.data.db.ExpenseDao
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.prefs.UserPrefs
import com.expense.tracker.data.prefs.ApiKeyStorage
import com.expense.tracker.data.repo.ChatRepository
import com.expense.tracker.data.repo.ExpenseRepository
import com.expense.tracker.llm.MutationPreview
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private lateinit var dispatcher: TestDispatcher

    @Before fun setup() {
        dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }
    @After fun teardown() = Dispatchers.resetMain()

    private fun makeVm(
        initialLlm: Boolean = false,
        llmHandler: LlmHandler = { _, _ -> error("unused in template mode") },
        confirmationHandler: LlmConfirmationHandler = { error("unused confirmation") },
        cancellationHandler: LlmCancellationHandler = {},
    ): Triple<ChatViewModel, FakeExpenseDao, FakeChatDao> {
        val ed = FakeExpenseDao()
        val cd = FakeChatDao()
        val store = FakeStore(initialLlm)
        val prefs = UserPrefs(store, MemoryApiKeyStorage())
        val vm = ChatViewModel(
            expenseRepo = ExpenseRepository(ed),
            chatRepo = ChatRepository(cd),
            userPrefs = prefs,
            llmHandler = llmHandler,
            confirmationHandler = confirmationHandler,
            cancellationHandler = cancellationHandler,
        )
        return Triple(vm, ed, cd)
    }

    @Test fun submitTemplateAddsExpenseAndTwoMessages() = runTest(dispatcher) {
        val (vm, ed, cd) = makeVm()
        vm.selectCategory("food")
        vm.submitTemplate(amountCents = 3_500L)
        assertThat(ed.snapshot()).hasSize(1)
        assertThat(ed.snapshot()[0].amountCents).isEqualTo(3_500L)
        val msgs = cd.flow.first()
        assertThat(msgs.map { it.role }).containsExactly("user", "assistant").inOrder()
        assertThat(msgs[1].content).contains("餐饮")
    }

    @Test fun submitTemplateRejectsNonPositive() = runTest(dispatcher) {
        val (vm, ed, _) = makeVm()
        vm.submitTemplate(amountCents = 0L)
        vm.submitTemplate(amountCents = -1L)
        assertThat(ed.snapshot()).isEmpty()
    }

    @Test fun toggleLlmFlipsState() = runTest(dispatcher) {
        val (vm, _, _) = makeVm(initialLlm = false)
        // initial state collected from flow takes a moment in real code; with UnconfinedTestDispatcher it's immediate
        assertThat(vm.uiState.value.llmEnabled).isFalse()
        vm.toggleLlm()
        assertThat(vm.uiState.first { it.llmEnabled }.llmEnabled).isTrue()
    }

    @Test fun rapidDoubleSubmitStartsOnlyOneLlmRequest() = runTest(dispatcher) {
        val releaseRequest = CompletableDeferred<Unit>()
        val requestStarted = CompletableDeferred<Unit>()
        var llmCalls = 0
        val (vm, _, chat) = makeVm(
            initialLlm = true,
            llmHandler = { _, _ ->
                llmCalls++
                requestStarted.complete(Unit)
                releaseRequest.await()
                LlmResult.Ok("已记")
            },
        )

        vm.uiState.first { it.llmEnabled }
        vm.submitFreeText("午饭35")
        vm.submitFreeText("午饭35")

        chat.flow.first { messages -> messages.count { it.role == "user" && it.content == "午饭35" } == 1 }
        requestStarted.await()
        assertThat(llmCalls).isEqualTo(1)
        assertThat(chat.flow.value.count { it.role == "user" && it.content == "午饭35" }).isEqualTo(1)

        releaseRequest.complete(Unit)
        chat.flow.first { it.lastOrNull()?.content == "已记" }
    }

    @Test fun pendingMutationConfirmsAndStoresWholeAffectedBatch() = runTest(dispatcher) {
        var confirmationCalls = 0
        var llmCalls = 0
        val preview = MutationPreview("确认修改账目", "共2笔", 2, 1_900L, listOf("2026-08-02"), "2026-08-01")
        val (vm, _, chat) = makeVm(
            initialLlm = true,
            llmHandler = { _, _ ->
                llmCalls++
                LlmResult.ConfirmationRequired("token-1", preview)
            },
            confirmationHandler = {
                confirmationCalls++
                LlmResult.Ok("已修改2笔", listOf(7L, 8L))
            },
        )

        vm.uiState.first { it.llmEnabled }
        vm.submitFreeText("把它们改到8月1日")
        val pendingState = vm.uiState.first { it.pendingConfirmation != null }
        assertThat(llmCalls).isEqualTo(1)
        assertThat(chat.flow.value.map { it.content }).contains("把它们改到8月1日")
        assertThat(pendingState.pendingConfirmation?.token).isEqualTo("token-1")
        assertThat(confirmationCalls).isEqualTo(0)

        vm.confirmPending()
        chat.flow.first { it.lastOrNull()?.relatedExpenseIds() == listOf(7L, 8L) }

        assertThat(confirmationCalls).isEqualTo(1)
        assertThat(vm.uiState.value.pendingConfirmation).isNull()
        assertThat(chat.flow.value.last().relatedExpenseIds()).containsExactly(7L, 8L).inOrder()
    }

    @Test fun cancellingPendingMutationNeverCallsConfirmation() = runTest(dispatcher) {
        var cancelledToken = ""
        var confirmationCalls = 0
        val preview = MutationPreview("确认批量记账", "共2笔", 2, 300L, emptyList(), null)
        val (vm, _, chat) = makeVm(
            initialLlm = true,
            llmHandler = { _, _ -> LlmResult.ConfirmationRequired("token-2", preview) },
            confirmationHandler = {
                confirmationCalls++
                LlmResult.Error("不应调用")
            },
            cancellationHandler = { cancelledToken = it },
        )

        vm.uiState.first { it.llmEnabled }
        vm.submitFreeText("两笔")
        vm.uiState.first { it.pendingConfirmation != null }
        vm.cancelPending()
        chat.flow.first { it.lastOrNull()?.content?.contains("已取消") == true }

        assertThat(cancelledToken).isEqualTo("token-2")
        assertThat(confirmationCalls).isEqualTo(0)
        assertThat(chat.flow.value.last().content).contains("已取消")
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
        seq++
        flow.value = flow.value + msg.copy(id = seq)
        return seq
    }
    override suspend fun insertAll(messages: List<ChatMessageEntity>): List<Long> =
        messages.map { insert(it) }
    override suspend fun update(msg: ChatMessageEntity) {
        flow.value = flow.value.map { if (it.id == msg.id) msg else it }
    }
    override fun observeAll(): Flow<List<ChatMessageEntity>> = flow
    override suspend fun getAllOnce(): List<ChatMessageEntity> = flow.value
    override suspend fun getRecent(limit: Int): List<ChatMessageEntity> = flow.value.takeLast(limit)
    override suspend fun getById(id: Long): ChatMessageEntity? = flow.value.firstOrNull { it.id == id }
    override suspend fun clearAll() { flow.value = emptyList() }
}

private class FakeStore(initialLlm: Boolean) : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(
        mutablePreferencesOf().toMutablePreferences().apply {
            this[UserPrefs.LLM_ENABLED] = initialLlm
        }
    )
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}

private class MemoryApiKeyStorage : ApiKeyStorage {
    private var value = ""
    override fun read(): String = value
    override fun write(value: String) { this.value = value }
}
