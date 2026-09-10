package com.expense.tracker.llm

import com.expense.tracker.data.db.ChatMessageDao
import com.expense.tracker.data.db.ChatMessageEntity
import com.expense.tracker.data.db.ExpenseDao
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.prefs.ThemeMode
import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.expense.tracker.data.repo.ChatRepository
import com.expense.tracker.data.repo.ExpenseRepository
import com.expense.tracker.data.repo.MutationApplyResult
import com.expense.tracker.ui.chat.LlmResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.ZoneId
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

class ChatLlmCoordinatorTest {
    @Test fun multiAddAppliesImmediatelyAndDeleteWaitsForConfirmationOnce() = runBlocking {
        val expenseDao = CoordinatorExpenseDao(listOf(
            ExpenseEntity(600L, "drink", "矿泉水", 1L, 1L, id = 7L),
        ))
        val chatDao = CoordinatorChatDao().apply {
            rows += ChatMessageEntity(
                role = "assistant",
                content = "已记录2笔",
                createdAt = 1L,
                relatedExpenseIdsCsv = "7",
                id = 1L,
            )
            rows += ChatMessageEntity("user", "金额是6和13", 2L, id = 2L)
        }
        var applyCalls = 0
        val coordinator = ChatLlmCoordinator(
            expenseRepository = ExpenseRepository(expenseDao),
            chatRepository = ChatRepository(chatDao),
            requestJson = { text, _, _, _, _ ->
                if (text.contains("删")) {
                    """{"reply":"删除候选","expenses":[],"actions":[{"action":"delete","expense_id":7}]}"""
                } else {
                    """{"reply":"已记2笔","expenses":[
                        {"amount":6,"category":"drink","note":"水1","occurred_at":null},
                        {"amount":13,"category":"drink","note":"水2","occurred_at":null}
                    ],"actions":[]}""".trimIndent()
                }
            },
            applyPlan = { plan ->
                applyCalls++
                if (plan.result.actions.isNotEmpty()) {
                    assertThat(plan.preview.count).isEqualTo(1)
                    MutationApplyResult(listOf(7L), listOf(7L), assistantMessageId = 2L)
                } else {
                    assertThat(plan.preview.count).isEqualTo(2)
                    MutationApplyResult(listOf(7L, 8L), listOf(7L, 8L), assistantMessageId = 1L)
                }
            },
            nowProvider = {
                LocalDateTime.of(2026, 8, 2, 22, 14)
                    .atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
            },
            zone = ZoneId.of("Asia/Shanghai"),
            tokenProvider = { "confirm-1" },
        )

        // 批量新增：按准则不再弹确认框，直接执行。
        val direct = coordinator.submit("金额是6和13", prefs()) as LlmResult.Ok
        assertThat(direct.expenseIds).isEqualTo(listOf(7L, 8L))
        assertThat(applyCalls).isEqualTo(1)

        // 删除：仍然必须确认，且确认令牌只能使用一次。
        val pending = coordinator.submit("把它们删掉", prefs())
        assertThat(pending).isInstanceOf(LlmResult.ConfirmationRequired::class.java)
        assertThat(applyCalls).isEqualTo(1)
        val token = (pending as LlmResult.ConfirmationRequired).token
        val confirmed = coordinator.confirm(token)
        assertThat(confirmed).isEqualTo(LlmResult.Ok("删除候选", listOf(7L), assistantPersisted = true))
        assertThat(applyCalls).isEqualTo(2)
        assertThat(coordinator.confirm(token)).isInstanceOf(LlmResult.Error::class.java)
        assertThat(applyCalls).isEqualTo(2)
    }

    @Test fun cancelDropsPendingPlanWithoutApplying() = runBlocking {
        var applyCalls = 0
        val expenseDao = CoordinatorExpenseDao(listOf(
            ExpenseEntity(600L, "drink", "矿泉水", 1L, 1L, id = 7L),
        ))
        val chatDao = CoordinatorChatDao().apply {
            rows += ChatMessageEntity(
                role = "assistant",
                content = "上一批",
                createdAt = 1L,
                relatedExpenseIdsCsv = "7",
                id = 1L,
            )
        }
        val coordinator = ChatLlmCoordinator(
            expenseRepository = ExpenseRepository(expenseDao),
            chatRepository = ChatRepository(chatDao),
            requestJson = { _, _, _, _, _ ->
                """{"reply":"删除候选","expenses":[],"actions":[{"action":"delete","expense_id":7}]}"""
            },
            applyPlan = {
                applyCalls++
                MutationApplyResult(emptyList(), emptyList(), assistantMessageId = 1L)
            },
            nowProvider = { 1_786_000_000_000L },
            tokenProvider = { "cancel-1" },
        )

        val pending = coordinator.submit("把它们删掉", prefs()) as LlmResult.ConfirmationRequired
        assertThat(coordinator.cancel(pending.token)).isTrue()
        assertThat(coordinator.confirm(pending.token)).isInstanceOf(LlmResult.Error::class.java)
        assertThat(applyCalls).isEqualTo(0)
    }

    @Test fun mutationSuccessClaimWithoutAnyMutationIsReplacedBySafeReply() = runBlocking {
        val coordinator = ChatLlmCoordinator(
            expenseRepository = ExpenseRepository(CoordinatorExpenseDao()),
            chatRepository = ChatRepository(CoordinatorChatDao()),
            requestJson = { _, _, _, _, _ ->
                """{"reply":"已将全部20条记录日期改好","expenses":[],"actions":[]}"""
            },
            applyPlan = { error("不应执行空计划") },
        )

        val result = coordinator.submit("把它们改到7月26号", prefs()) as LlmResult.Ok

        assertThat(result.replyText).contains("未修改")
        assertThat(result.expenseIds).isEmpty()
    }

    @Test fun coroutineCancellationIsNeverConvertedIntoChatError() {
        val coordinator = ChatLlmCoordinator(
            expenseRepository = ExpenseRepository(CoordinatorExpenseDao()),
            chatRepository = ChatRepository(CoordinatorChatDao()),
            requestJson = { _, _, _, _, _ -> throw CancellationException("cancelled") },
            applyPlan = { error("不应执行") },
        )

        assertThrows(CancellationException::class.java) {
            runBlocking { coordinator.submit("午饭35", prefs()) }
        }
    }

    @Test fun selfContainedMultiDateAddUsesNormalizedTextWithoutExistingRecordContext() {
        runBlocking {
            val existing = ExpenseEntity(
                amountCents = 7_600L,
                categoryId = "transport",
                note = "旧记录",
                occurredAt = 1L,
                createdAt = 1L,
                id = 99L,
            )
            val chatDao = CoordinatorChatDao().apply {
                rows += ChatMessageEntity("assistant", "上一轮回复", 1L, id = 1L)
                rows += ChatMessageEntity("user", ExpenseTextInterpreterTest.REPORTED_INPUT, 2L, id = 2L)
            }
            var requestedText = ""
            var requestedPrompt = ""
            var requestedHistory = emptyList<ChatMsg>()
            val coordinator = ChatLlmCoordinator(
                expenseRepository = ExpenseRepository(CoordinatorExpenseDao(listOf(existing))),
                chatRepository = ChatRepository(chatDao),
                requestJson = { text, _, prompt, history, _ ->
                    requestedText = text
                    requestedPrompt = prompt
                    requestedHistory = history
                    """{"reply":"未识别","expenses":[],"actions":[]}"""
                },
                applyPlan = { error("不应执行") },
                nowProvider = {
                    LocalDateTime.of(2026, 8, 3, 0, 0)
                        .atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
                },
                zone = ZoneId.of("Asia/Shanghai"),
            )

            coordinator.submit(ExpenseTextInterpreterTest.REPORTED_INPUT, prefs())

            assertThat(requestedText).contains("下午 16点，6元")
            assertThat(requestedPrompt).doesNotContain("99|")
            assertThat(requestedPrompt).doesNotContain("旧记录")
            assertThat(requestedHistory.map { it.content }).containsExactly("上一轮回复")
        }
    }

    @Test fun singleDateBatchRejectsAMissingModelExpenseBeforeApplying() = runBlocking {
        var applyCalls = 0
        val coordinator = ChatLlmCoordinator(
            expenseRepository = ExpenseRepository(CoordinatorExpenseDao()),
            chatRepository = ChatRepository(CoordinatorChatDao()),
            requestJson = { _, _, _, _, _ ->
                """{"reply":"只识别一笔","expenses":[
                    {"amount":6,"category":"food","note":"早餐","occurred_at":null}
                ],"actions":[]}""".trimIndent()
            },
            applyPlan = {
                applyCalls++
                MutationApplyResult(emptyList(), emptyList(), assistantMessageId = 1L)
            },
            nowProvider = {
                LocalDateTime.of(2026, 8, 3, 0, 0)
                    .atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
            },
            zone = ZoneId.of("Asia/Shanghai"),
        )

        val result = coordinator.submit("8月1日早餐6元，午饭13元", prefs())

        assertThat(result).isInstanceOf(LlmResult.Error::class.java)
        assertThat((result as LlmResult.Error).message).contains("笔数")
        assertThat(applyCalls).isEqualTo(0)
    }

    @Test fun explicitDateAndAmountInAnUpdateAppliesImmediately() = runBlocking {
        val expense = ExpenseEntity(
            amountCents = 600L,
            categoryId = "drink",
            note = "矿泉水",
            occurredAt = LocalDateTime.of(2026, 8, 2, 16, 0)
                .atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli(),
            createdAt = 1L,
            id = 7L,
        )
        var applyCalls = 0
        val coordinator = ChatLlmCoordinator(
            expenseRepository = ExpenseRepository(CoordinatorExpenseDao(listOf(expense))),
            chatRepository = ChatRepository(CoordinatorChatDao()),
            requestJson = { _, _, _, _, _ ->
                """{"reply":"候选修改","expenses":[],"actions":[
                    {"action":"update","expense_id":7,"amount":20}
                ]}""".trimIndent()
            },
            applyPlan = {
                applyCalls++
                MutationApplyResult(listOf(7L), listOf(7L), assistantMessageId = 1L)
            },
            nowProvider = {
                LocalDateTime.of(2026, 8, 3, 0, 0)
                    .atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
            },
            zone = ZoneId.of("Asia/Shanghai"),
            tokenProvider = { "update-1" },
        )

        val result = coordinator.submit("把8月2日的矿泉水改成20元", prefs())

        assertThat(result).isInstanceOf(LlmResult.Ok::class.java)
        assertThat(applyCalls).isEqualTo(1)
    }

    @Test fun queryTurnDropsModelMutationsAtCodeLevel() = runBlocking {
        var applyCalls = 0
        var capturedStructured: Boolean? = null
        val coordinator = ChatLlmCoordinator(
            expenseRepository = ExpenseRepository(CoordinatorExpenseDao()),
            chatRepository = ChatRepository(CoordinatorChatDao()),
            requestJson = { _, _, _, _, structured ->
                capturedStructured = structured
                """{"reply":"已记录35元","expenses":[
                    {"amount":35,"category":"food","note":"午饭","occurred_at":null}
                ],"actions":[]}""".trimIndent()
            },
            applyPlan = {
                applyCalls++
                MutationApplyResult(listOf(1L), listOf(1L), assistantMessageId = 1L)
            },
            nowProvider = { 1_786_000_000_000L },
        )

        val result = coordinator.submit(
            "这个月吃饭花了多少？",
            prefs(),
            ChatTurnContext(allowMutations = false, suppressMutationGuard = true, structuredRequest = true),
        )

        assertThat(capturedStructured).isTrue()
        val ok = result as LlmResult.Ok
        assertThat(ok.replyText).isEqualTo("已记录35元")
        assertThat(ok.expenseIds).isEmpty()
        // 若不拦截，本轮的 expenses 会走"新增直接执行"路径真实落库。
        assertThat(applyCalls).isEqualTo(0)
    }

    private fun prefs() = UserPrefsSnapshot(
        llmEnabled = true,
        baseUrl = "https://example.com/v1",
        apiKey = "secret",
        model = "hy3",
        themeMode = ThemeMode.SYSTEM,
    )
}

private class CoordinatorExpenseDao(initialRows: List<ExpenseEntity> = emptyList()) : ExpenseDao {
    private val rows = MutableStateFlow(initialRows)
    override suspend fun insert(expense: ExpenseEntity): Long = 1L
    override suspend fun insertAll(expenses: List<ExpenseEntity>) = expenses.mapIndexed { i, _ -> i + 1L }
    override suspend fun clearAll() { rows.value = emptyList() }
    override suspend fun update(expense: ExpenseEntity) = Unit
    override fun observeActive(): Flow<List<ExpenseEntity>> = rows
    override suspend fun getAllActiveOnce() = rows.value
    override suspend fun getAllOnce() = rows.value
    override suspend fun getById(id: Long) = rows.value.firstOrNull { it.id == id }
    override fun observeInRange(from: Long, to: Long): Flow<List<ExpenseEntity>> = flowOf(emptyList())
    override fun observeDeleted(): Flow<List<ExpenseEntity>> = flowOf(emptyList())
    override suspend fun softDeleteById(id: Long, deletedAtMillis: Long) = Unit
    override suspend fun restoreById(id: Long) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun purgeOlderThan(cutoffMillis: Long) = Unit
}

private class CoordinatorChatDao : ChatMessageDao {
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
