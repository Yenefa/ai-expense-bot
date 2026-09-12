package com.expense.tracker.llm

import com.expense.tracker.agent.FakeChatDao
import com.expense.tracker.agent.FakeExpenseDao
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
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.ZoneId
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

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

    @Test fun chatTurnDropsModelMutationsAtCodeLevel() = runBlocking {
        var applyCalls = 0
        val coordinator = ChatLlmCoordinator(
            expenseRepository = ExpenseRepository(CoordinatorExpenseDao()),
            chatRepository = ChatRepository(CoordinatorChatDao()),
            requestJson = { _, _, _, _, _ ->
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

        // CHAT write firewall：闲聊轮即使模型幻觉输出账目，也不落库、也不展示"已记录"。
        val result = coordinator.submit("你好呀", prefs(), ChatTurnContext(allowMutations = false))

        val ok = result as LlmResult.Ok
        assertThat(ok.expenseIds).isEmpty()
        assertThat(ok.replyText).contains("未修改")
        assertThat(applyCalls).isEqualTo(0)
    }

    @Test fun followUpWordsLoadRecordedHistoryAndOnlyTheNewExpenseIsApplied() = runBlocking {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = LocalDateTime.of(2026, 9, 6, 12, 0).atZone(zone).toInstant().toEpochMilli()
        val seedAt = LocalDateTime.of(2026, 9, 6, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val dao = FakeExpenseDao()
        val seedId = dao.insert(
            ExpenseEntity(
                amountCents = 5_000L,
                categoryId = "transport",
                note = "打车",
                occurredAt = seedAt,
                createdAt = now,
            ),
        )
        val chatDao = FakeChatDao().apply {
            insert(ChatMessageEntity("user", "打车50", now - 2_000L))
            insert(ChatMessageEntity("assistant", "已记录 1 笔", now - 1_000L, relatedExpenseIdsCsv = seedId.toString()))
        }
        var requestedPrompt = ""
        var requestedHistory = emptyList<ChatMsg>()
        val coordinator = ChatLlmCoordinator(
            expenseRepository = ExpenseRepository(dao),
            chatRepository = ChatRepository(chatDao),
            requestJson = { _, _, prompt, history, _ ->
                requestedPrompt = prompt
                requestedHistory = history
                """{"reply":"已记奶茶","expenses":[{"amount":16,"category":"drink","note":"奶茶","occurred_at":null}],"actions":[]}"""
            },
            applyPlan = LlmMutationApplier(ExpenseRepository(dao), ChatRepository(chatDao), CoordinatorInlineRunner())::apply,
            nowProvider = { now },
            zone = zone,
        )

        // P0 回归（mt-02 场景）：上一轮「打车50」已记录，本轮「还有一杯奶茶16」只应新增奶茶一笔。
        val result = coordinator.submit("还有一杯奶茶16", prefs()) as LlmResult.Ok

        // 上下文触发词让已有账目进入提示词，模型能看到"历史已记录"的去重依据。
        assertThat(requestedPrompt).contains("不得再次提取")
        assertThat(requestedPrompt).contains("$seedId|${promptTime(seedAt)}")
        assertThat(requestedPrompt).contains("打车")
        assertThat(requestedHistory.map { it.content }).containsExactly("打车50", "已记录 1 笔").inOrder()
        assertThat(result.expenseIds).hasSize(1)
        val rows = dao.getAllActiveOnce()
        assertThat(rows.map { it.amountCents }).containsExactly(5_000L, 1_600L)
        assertThat(rows.count { it.amountCents == 5_000L }).isEqualTo(1)
    }

    @Test fun continuationTriggersLoadExistingRecordContextForAllMultiTurnCases() = runBlocking {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = LocalDateTime.of(2026, 9, 6, 12, 0).atZone(zone).toInstant().toEpochMilli()
        val existing = ExpenseEntity(
            amountCents = 5_000L,
            categoryId = "transport",
            note = "打车",
            occurredAt = LocalDateTime.of(2026, 9, 6, 9, 0).atZone(zone).toInstant().toEpochMilli(),
            createdAt = now,
            id = 7L,
        )
        listOf(
            "还有一杯奶茶16", // mt-02
            "昨天也买了支笔10块", // mt-03
            "再记一笔晚饭30", // mt-16
            "又买了一瓶水3块",
        ).forEach { text ->
            val chatDao = CoordinatorChatDao().apply {
                rows += ChatMessageEntity("user", "打车50", now - 2_000L, id = 1L)
                rows += ChatMessageEntity("assistant", "已记录 1 笔", now - 1_000L, relatedExpenseIdsCsv = "7", id = 2L)
            }
            var requestedPrompt = ""
            val coordinator = ChatLlmCoordinator(
                expenseRepository = ExpenseRepository(CoordinatorExpenseDao(listOf(existing))),
                chatRepository = ChatRepository(chatDao),
                requestJson = { _, _, prompt, _, _ ->
                    requestedPrompt = prompt
                    """{"reply":"ok","expenses":[],"actions":[]}"""
                },
                applyPlan = { error("不应执行空计划") },
                nowProvider = { now },
                zone = zone,
            )

            coordinator.submit(text, prefs())

            assertThat(requestedPrompt).contains("不得再次提取")
            assertThat(requestedPrompt).contains("7|${promptTime(existing.occurredAt)}")
        }
    }

    @Test fun onlyDateCorrectionOnLastBatchBindsTargetDateDeterministically() = runBlocking {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = LocalDateTime.of(2026, 9, 6, 12, 0).atZone(zone).toInstant().toEpochMilli()
        val seedAt = LocalDateTime.of(2026, 9, 6, 15, 0).atZone(zone).toInstant().toEpochMilli()
        val dao = FakeExpenseDao()
        val seedId = dao.insert(
            ExpenseEntity(
                amountCents = 1_800L,
                categoryId = "drink",
                note = "咖啡",
                occurredAt = seedAt,
                createdAt = now,
            ),
        )
        val chatDao = FakeChatDao().apply {
            insert(ChatMessageEntity("user", "下午咖啡18", now - 2_000L))
            insert(ChatMessageEntity("assistant", "已记录 1 笔", now - 1_000L, relatedExpenseIdsCsv = seedId.toString()))
        }
        var requestedPrompt = ""
        val coordinator = ChatLlmCoordinator(
            expenseRepository = ExpenseRepository(dao),
            chatRepository = ChatRepository(chatDao),
            requestJson = { _, _, prompt, _, _ ->
                requestedPrompt = prompt
                // P1 回归（mt-15 场景）：模型只产出 update（不带 occurred_at），日期绑定由客户端确定性完成。
                """{"reply":"那杯咖啡已改到前天","expenses":[],"actions":[{"action":"update","expense_id":$seedId}]}"""
            },
            applyPlan = LlmMutationApplier(ExpenseRepository(dao), ChatRepository(chatDao), CoordinatorInlineRunner())::apply,
            nowProvider = { now },
            zone = zone,
        )

        val result = coordinator.submit("刚才那杯咖啡记到前天", prefs())

        assertThat(result).isInstanceOf(LlmResult.Ok::class.java)
        assertThat(requestedPrompt).contains("必须输出 update 动作")
        assertThat(requestedPrompt).contains("$seedId|${promptTime(seedAt)}")
        val rows = dao.getAllActiveOnce()
        assertThat(rows).hasSize(1)
        val updated = Instant.ofEpochMilli(rows.single().occurredAt).atZone(zone)
        assertThat(updated.toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 4))
        assertThat(updated.toLocalTime()).isEqualTo(LocalTime.of(15, 0))
    }

    @Test fun continuationResolvesPreviousRelativeDateAgainstItsOwnTimestamp() = runBlocking {
        val zone = ZoneId.of("Asia/Shanghai")
        val day1Noon = LocalDateTime.of(2026, 8, 1, 12, 0).atZone(zone).toInstant().toEpochMilli()
        val now = LocalDateTime.of(2026, 8, 3, 12, 0).atZone(zone).toInstant().toEpochMilli()
        val chatDao = CoordinatorChatDao().apply {
            rows += ChatMessageEntity("user", "昨天午饭35", day1Noon, id = 1L)
            rows += ChatMessageEntity("assistant", "已记录 1 笔", day1Noon + 1_000L, id = 2L)
        }
        var capturedTargetDateLabel: String? = null
        val coordinator = ChatLlmCoordinator(
            expenseRepository = ExpenseRepository(CoordinatorExpenseDao()),
            chatRepository = ChatRepository(chatDao),
            requestJson = { _, _, _, _, _ ->
                """{"reply":"已记7元","expenses":[{"amount":7,"category":"food","note":"补充","occurred_at":null}],"actions":[]}"""
            },
            applyPlan = { plan ->
                capturedTargetDateLabel = plan.preview.targetDateLabel
                MutationApplyResult(listOf(3L), listOf(3L), assistantMessageId = 3L)
            },
            nowProvider = { now },
            zone = zone,
        )

        val result = coordinator.submit("还有一笔7元", prefs()) as LlmResult.Ok

        // Day1 的“昨天”按 Day1 解析为 7月31日，而不是按 Day3 解析成 8月2日。
        assertThat(capturedTargetDateLabel).isEqualTo("2026-07-31")
        assertThat(result.expenseIds).isEqualTo(listOf(3L))
    }

    /** LlmPrompt 渲染记录时间使用 JVM 默认时区（CI 为 UTC），断言需按运行环境动态计算。 */
    private fun promptTime(millis: Long): String =
        java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")
            .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    private fun prefs() = UserPrefsSnapshot(
        llmEnabled = true,
        baseUrl = "https://example.com/v1",
        apiKey = "secret",
        model = "hy3",
        themeMode = ThemeMode.SYSTEM,
    )
}

private class CoordinatorInlineRunner : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = block()
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
