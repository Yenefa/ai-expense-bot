package com.expense.tracker.ui.chat

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.expense.tracker.data.db.ChatMessageDao
import com.expense.tracker.data.db.ChatMessageEntity
import com.expense.tracker.data.db.ExpenseDao
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.prefs.UserPrefs
import com.expense.tracker.data.repo.ChatRepository
import com.expense.tracker.data.repo.ExpenseRepository
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
class ChatViewModelTest {
    @Before fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun teardown() = Dispatchers.resetMain()

    private fun makeVm(initialLlm: Boolean = false): Triple<ChatViewModel, FakeExpenseDao, FakeChatDao> {
        val ed = FakeExpenseDao()
        val cd = FakeChatDao()
        val store = FakeStore(initialLlm)
        val prefs = UserPrefs(store)
        val vm = ChatViewModel(
            expenseRepo = ExpenseRepository(ed),
            chatRepo = ChatRepository(cd),
            userPrefs = prefs,
            llmHandler = { _, _ -> error("unused in template mode") },
        )
        return Triple(vm, ed, cd)
    }

    @Test fun submitTemplateAddsExpenseAndTwoMessages() = runTest {
        val (vm, ed, cd) = makeVm()
        vm.selectCategory("food")
        vm.submitTemplate(amount = 35.0)
        assertThat(ed.snapshot()).hasSize(1)
        assertThat(ed.snapshot()[0].amount).isEqualTo(35.0)
        val msgs = cd.flow.first()
        assertThat(msgs.map { it.role }).containsExactly("user", "assistant").inOrder()
        assertThat(msgs[1].content).contains("餐饮")
    }

    @Test fun submitTemplateRejectsNonPositive() = runTest {
        val (vm, ed, _) = makeVm()
        vm.submitTemplate(amount = 0.0)
        vm.submitTemplate(amount = -1.0)
        assertThat(ed.snapshot()).isEmpty()
    }

    @Test fun toggleLlmFlipsState() = runTest {
        val (vm, _, _) = makeVm(initialLlm = false)
        // initial state collected from flow takes a moment in real code; with UnconfinedTestDispatcher it's immediate
        assertThat(vm.uiState.value.llmEnabled).isFalse()
        vm.toggleLlm()
        assertThat(vm.uiState.value.llmEnabled).isTrue()
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
    override fun observeAll(): Flow<List<ExpenseEntity>> = state
    override fun observeInRange(from: Long, to: Long): Flow<List<ExpenseEntity>> = flowOf(emptyList())
    override suspend fun deleteById(id: Long) {}
}

private class FakeChatDao : ChatMessageDao {
    val flow = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    private var seq = 0L
    override suspend fun insert(msg: ChatMessageEntity): Long {
        seq++
        flow.value = flow.value + msg.copy(id = seq)
        return seq
    }
    override fun observeAll(): Flow<List<ChatMessageEntity>> = flow
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
