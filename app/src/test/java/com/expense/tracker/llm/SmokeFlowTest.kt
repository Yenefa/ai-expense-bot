package com.expense.tracker.llm

import com.expense.tracker.data.db.ChatMessageDao
import com.expense.tracker.data.db.ChatMessageEntity
import com.expense.tracker.data.db.ExpenseDao
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.prefs.ThemeMode
import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.expense.tracker.data.repo.ChatRepository
import com.expense.tracker.data.repo.ExpenseRepository
import com.expense.tracker.data.repo.LlmMutationApplier
import com.expense.tracker.data.repo.MutationApplyResult
import com.expense.tracker.data.repo.TransactionRunner
import com.expense.tracker.ui.chat.LlmResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 冒烟级端到端链路：模拟用户说一句话 → AI 解析 → 安全计划 → 事务落库。
 * 不需要模拟器即可覆盖真实用户最关键的三条路径。
 */
class SmokeFlowTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDateTime.of(2026, 8, 6, 12, 30)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    private fun coordinatorWith(
        expenseDao: SmokeExpenseDao,
        chatDao: SmokeChatDao,
        responseJson: String,
    ): ChatLlmCoordinator = ChatLlmCoordinator(
        expenseRepository = ExpenseRepository(expenseDao),
        chatRepository = ChatRepository(chatDao),
        requestJson = { _, _, _, _ -> responseJson },
        applyPlan = LlmMutationApplier(
            ExpenseRepository(expenseDao),
            ChatRepository(chatDao),
            TransactionlessRunner(),
        )::apply,
        nowProvider = { now },
        zone = zone,
        tokenProvider = { "smoke-token" },
    )

    @Test
    fun smokeUserSentenceAddsTwoExpensesOnTodayWithoutConfirmation() = runBlocking {
        val expenseDao = SmokeExpenseDao()
        val chatDao = SmokeChatDao()
        val coordinator = coordinatorWith(
            expenseDao, chatDao,
            """{"reply":"已记2笔","expenses":[
                {"amount":35,"category":"food","note":"午饭","occurred_at":null},
                {"amount":18,"category":"drink","note":"咖啡","occurred_at":null}
            ],"actions":[]}""",
        )

        val result = coordinator.submit("午饭 35，咖啡 18", prefs())

        assertThat(result).isInstanceOf(LlmResult.Ok::class.java)
        val rows = expenseDao.getAllActiveOnce()
        assertThat(rows.map { it.amountCents }).containsExactly(3_500L, 1_800L).inOrder()
        assertThat(rows.map { it.categoryId }).containsExactly("food", "drink").inOrder()
        // 用户没提日期：必须落在当前时间（当天），而不是模型带出的历史日期。
        rows.forEach {
            assertThat(Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate())
                .isEqualTo(LocalDate.of(2026, 8, 6))
        }
    }

    @Test
    fun smokeStaleModelDateDoesNotStickToYesterday() = runBlocking {
        val expenseDao = SmokeExpenseDao()
        val chatDao = SmokeChatDao()
        val staleMillis = LocalDateTime.of(2026, 8, 1, 9, 0)
            .atZone(zone).toInstant().toEpochMilli()
        val coordinator = coordinatorWith(
            expenseDao, chatDao,
            """{"reply":"已记1笔","expenses":[
                {"amount":6,"category":"drink","note":"水","occurred_at":"2026-08-01T09:00:00"}
            ],"actions":[]}""",
        )

        coordinator.submit("买水 6 块", prefs())

        val row = expenseDao.getAllActiveOnce().single()
        assertThat(Instant.ofEpochMilli(row.occurredAt).atZone(zone).toLocalDate())
            .isEqualTo(LocalDate.of(2026, 8, 6))
    }

    @Test
    fun smokeDeleteStillRequiresConfirmationAndSoftDeletes() = runBlocking {
        val expenseDao = SmokeExpenseDao(listOf(
            ExpenseEntity(600L, "drink", "矿泉水", now, 1L, id = 7L),
        ))
        val chatDao = SmokeChatDao().apply {
            rows += ChatMessageEntity(
                role = "assistant",
                content = "上一批",
                createdAt = 1L,
                relatedExpenseIdsCsv = "7",
                id = 1L,
            )
        }
        val coordinator = coordinatorWith(
            expenseDao, chatDao,
            """{"reply":"删除候选","expenses":[],"actions":[{"action":"delete","expense_id":7}]}""",
        )

        val pending = coordinator.submit("把这笔删掉", prefs())
        assertThat(pending).isInstanceOf(LlmResult.ConfirmationRequired::class.java)
        // 确认前账目还在
        assertThat(expenseDao.getAllActiveOnce()).hasSize(1)

        val confirmed = coordinator.confirm((pending as LlmResult.ConfirmationRequired).token)
        assertThat(confirmed).isInstanceOf(LlmResult.Ok::class.java)
        assertThat(expenseDao.getAllActiveOnce()).isEmpty()
        assertThat(expenseDao.getAllOnce()).hasSize(1)
    }

    private fun prefs() = UserPrefsSnapshot(
        llmEnabled = true,
        baseUrl = "https://example.com/v1",
        apiKey = "secret",
        model = "hy3",
        themeMode = ThemeMode.SYSTEM,
    )
}

/** 同步执行的事务 Runner（内存 DAO 无需真事务）。 */
private class TransactionlessRunner : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}

private class SmokeExpenseDao(initialRows: List<ExpenseEntity> = emptyList()) : ExpenseDao {
    private val rows = MutableStateFlow(initialRows)
    override suspend fun insert(expense: ExpenseEntity): Long {
        val nextId = (rows.value.maxOfOrNull { it.id } ?: 0L) + 1L
        rows.value = rows.value + expense.copy(id = nextId)
        return nextId
    }
    override suspend fun insertAll(expenses: List<ExpenseEntity>) = expenses.map { insert(it) }
    override suspend fun clearAll() { rows.value = emptyList() }
    override suspend fun update(expense: ExpenseEntity) {
        rows.value = rows.value.map { if (it.id == expense.id) expense else it }
    }
    override fun observeActive(): Flow<List<ExpenseEntity>> = rows
    override suspend fun getAllActiveOnce() = rows.value.filter { it.deletedAt == null }
    override suspend fun getAllOnce() = rows.value
    override suspend fun getById(id: Long) = rows.value.firstOrNull { it.id == id }
    override fun observeInRange(from: Long, to: Long): Flow<List<ExpenseEntity>> = flowOf(emptyList())
    override fun observeDeleted(): Flow<List<ExpenseEntity>> = flowOf(emptyList())
    override suspend fun softDeleteById(id: Long, deletedAtMillis: Long) {
        rows.value = rows.value.map { if (it.id == id) it.copy(deletedAt = deletedAtMillis) else it }
    }
    override suspend fun restoreById(id: Long) {
        rows.value = rows.value.map { if (it.id == id) it.copy(deletedAt = null) else it }
    }
    override suspend fun deleteById(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }
    override suspend fun purgeOlderThan(cutoffMillis: Long) = Unit
}

private class SmokeChatDao : ChatMessageDao {
    val rows = mutableListOf<ChatMessageEntity>()
    override suspend fun insert(msg: ChatMessageEntity): Long {
        rows += msg.copy(id = rows.size + 1L)
        return rows.last().id
    }
    override suspend fun insertAll(messages: List<ChatMessageEntity>) = messages.map { insert(it) }
    override suspend fun update(msg: ChatMessageEntity) = Unit
    override fun observeAll(): Flow<List<ChatMessageEntity>> = flowOf(rows)
    override suspend fun getAllOnce() = rows.toList()
    override suspend fun getRecent(limit: Int) = rows.takeLast(limit)
    override suspend fun getById(id: Long) = rows.firstOrNull { it.id == id }
    override suspend fun clearAll() = rows.clear()
}
