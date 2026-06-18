package com.expense.tracker.ui.chat

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.expense.tracker.data.action.PendingAction
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
import kotlinx.coroutines.test.advanceUntilIdle
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

    private fun makeVm(
        initialLlm: Boolean = false,
        llmHandler: LlmHandler = LlmHandler { _, _ -> error("unused in template mode") },
    ): Triple<ChatViewModel, FakeExpenseDao, FakeChatDao> {
        val ed = FakeExpenseDao()
        val cd = FakeChatDao()
        val store = FakeStore(initialLlm)
        val prefs = UserPrefs(store, FakeSecurePrefs())
        val vm = ChatViewModel(
            expenseRepo = ExpenseRepository(ed),
            chatRepo = ChatRepository(cd),
            userPrefs = prefs,
            llmHandler = llmHandler,
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

    // === pendingActions 流转 ===

    @Test fun llmReturnsPendingDeleteAction_appearsInState() = runTest {
        val seedRow = ExpenseEntity(amount = 18.0, categoryId = "drink", note = "咖啡",
                                    occurredAt = 100L, createdAt = 100L, id = 7)
        val handler = LlmHandler { _, _ ->
            LlmResult.Ok(
                replyText = "准备删除", expenseId = null,
                pendingActions = listOf(PendingAction.Delete(id = "act-1", candidates = listOf(seedRow))),
            )
        }
        val (vm, _, _) = makeVm(initialLlm = true, llmHandler = handler)
        vm.submitFreeText("删掉那笔")
        // streamReply 里 delay 22ms+；advanceUntilIdle 让 runTest 的虚拟时钟跑完所有 delay
        advanceUntilIdle()
        assertThat(vm.uiState.value.pendingActions).hasSize(1)
        assertThat(vm.uiState.value.pendingActions[0].id).isEqualTo("act-1")
    }

    @Test fun confirmDelete_removesExpenseAndCard() = runTest {
        val seedRow = ExpenseEntity(amount = 18.0, categoryId = "drink", note = "咖啡",
                                    occurredAt = 100L, createdAt = 100L, id = 7)
        val handler = LlmHandler { _, _ ->
            LlmResult.Ok("准备删除", null,
                listOf(PendingAction.Delete("act-1", listOf(seedRow))))
        }
        val (vm, ed, _) = makeVm(initialLlm = true, llmHandler = handler)
        ed.seed(seedRow)
        vm.submitFreeText("删")
        advanceUntilIdle()
        vm.confirmDelete("act-1", setOf(7L))
        advanceUntilIdle()
        assertThat(ed.deleted).contains(7L)
        assertThat(vm.uiState.value.pendingActions).isEmpty()
    }

    @Test fun confirmUpdate_patchesExpenseAndCard() = runTest {
        val seedRow = ExpenseEntity(amount = 35.0, categoryId = "food", note = "",
                                    occurredAt = 100L, createdAt = 100L, id = 9)
        val handler = LlmHandler { _, _ ->
            LlmResult.Ok("改", null, listOf(
                PendingAction.Update(
                    id = "u-1", candidates = listOf(seedRow),
                    patchAmount = 40.0, patchCategoryId = null, patchNote = null,
                )
            ))
        }
        val (vm, ed, _) = makeVm(initialLlm = true, llmHandler = handler)
        ed.seed(seedRow)
        vm.submitFreeText("改成40")
        advanceUntilIdle()
        vm.confirmUpdate("u-1", setOf(9L))
        advanceUntilIdle()
        assertThat(ed.patches).contains(Patch(9L, 40.0, null, null))
        assertThat(vm.uiState.value.pendingActions).isEmpty()
    }

    @Test fun dismissAction_removesCardWithoutSideEffect() = runTest {
        val handler = LlmHandler { _, _ ->
            LlmResult.Ok("没找到", null, listOf(PendingAction.Empty("e-1", "找不到")))
        }
        val (vm, ed, _) = makeVm(initialLlm = true, llmHandler = handler)
        vm.submitFreeText("删")
        advanceUntilIdle()
        // 确保卡片真的进了 state，否则下一行的"移除"等于啥也没移除（误通过）
        assertThat(vm.uiState.value.pendingActions).hasSize(1)
        vm.dismissAction("e-1")
        assertThat(vm.uiState.value.pendingActions).isEmpty()
        assertThat(ed.deleted).isEmpty()
    }

    @Test fun confirmDeleteWithEmptySelection_doesNothing() = runTest {
        val seedRow = ExpenseEntity(amount = 18.0, categoryId = "drink", note = "",
                                    occurredAt = 100L, createdAt = 100L, id = 7)
        val handler = LlmHandler { _, _ ->
            LlmResult.Ok("?", null, listOf(PendingAction.Delete("act-1", listOf(seedRow))))
        }
        val (vm, ed, _) = makeVm(initialLlm = true, llmHandler = handler)
        ed.seed(seedRow)
        vm.submitFreeText("删")
        advanceUntilIdle()
        vm.confirmDelete("act-1", emptySet())
        advanceUntilIdle()
        // 卡片仍在；DB 没动
        assertThat(vm.uiState.value.pendingActions).hasSize(1)
        assertThat(ed.deleted).isEmpty()
    }
}

// 便于 lambda mock
private fun LlmHandler(impl: suspend (String, com.expense.tracker.data.prefs.UserPrefsSnapshot) -> LlmResult): LlmHandler = impl

private data class Patch(val id: Long, val amount: Double?, val cat: String?, val note: String?)

private class FakeExpenseDao : ExpenseDao {
    private val state = MutableStateFlow<List<ExpenseEntity>>(emptyList())
    val deleted = mutableListOf<Long>()
    val patches = mutableListOf<Patch>()
    fun snapshot(): List<ExpenseEntity> = state.value
    fun seed(row: ExpenseEntity) { state.value = state.value + row }
    override suspend fun insert(expense: ExpenseEntity): Long {
        val id = state.value.size + 1L
        state.value = state.value + expense.copy(id = id)
        return id
    }
    override fun observeAll(): Flow<List<ExpenseEntity>> = state
    override fun observeInRange(from: Long, to: Long): Flow<List<ExpenseEntity>> = flowOf(emptyList())
    // 软删 — 测试只关心 deleted 列表里有没有出现过此 id
    override suspend fun softDeleteById(id: Long, at: Long) { deleted += id }
    override suspend fun restoreById(id: Long) { }
    override suspend fun hardDeleteById(id: Long) { deleted += id }
    override suspend fun hardDeleteExpired(before: Long): Int = 0
    override suspend fun hardDeleteAllTrashed(): Int = 0
    override fun observeTrashed(): Flow<List<ExpenseEntity>> = flowOf(emptyList())
    override suspend fun findByMatch(
        category: String?, amount: Double?, from: Long?, to: Long?, noteSub: String?,
    ): List<ExpenseEntity> = emptyList()
    override suspend fun patchById(id: Long, amount: Double?, cat: String?, note: String?) {
        patches += Patch(id, amount, cat, note)
    }
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

private class FakeSecurePrefs : android.content.SharedPreferences {
    private val data = mutableMapOf<String, Any?>()
    override fun getString(key: String, defValue: String?) = (data[key] as? String) ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?) = error("unused")
    override fun getInt(key: String, defValue: Int) = (data[key] as? Int) ?: defValue
    override fun getLong(key: String, defValue: Long) = (data[key] as? Long) ?: defValue
    override fun getFloat(key: String, defValue: Float) = (data[key] as? Float) ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = (data[key] as? Boolean) ?: defValue
    override fun contains(key: String) = data.containsKey(key)
    override fun edit(): android.content.SharedPreferences.Editor = Ed()
    override fun registerOnSharedPreferenceChangeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun getAll(): MutableMap<String, *> = data.toMutableMap()
    private inner class Ed : android.content.SharedPreferences.Editor {
        private val p = mutableMapOf<String, Any?>()
        private val rm = mutableSetOf<String>()
        override fun putString(k: String, v: String?) = apply { p[k] = v }
        override fun putStringSet(k: String, v: MutableSet<String>?) = apply { p[k] = v }
        override fun putInt(k: String, v: Int) = apply { p[k] = v }
        override fun putLong(k: String, v: Long) = apply { p[k] = v }
        override fun putFloat(k: String, v: Float) = apply { p[k] = v }
        override fun putBoolean(k: String, v: Boolean) = apply { p[k] = v }
        override fun remove(k: String) = apply { rm += k }
        override fun clear() = apply { data.clear() }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() { rm.forEach { data.remove(it) }; p.forEach { (k, v) -> if (v == null) data.remove(k) else data[k] = v } }
    }
}
