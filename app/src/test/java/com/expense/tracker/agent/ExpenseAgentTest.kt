package com.expense.tracker.agent

import com.expense.tracker.data.budget.BudgetSnapshot
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.expense.tracker.data.repo.ChatRepository
import com.expense.tracker.data.repo.ExpenseRepository
import com.expense.tracker.data.repo.MutationApplyResult
import com.expense.tracker.llm.ChatLlmCoordinator
import com.expense.tracker.ui.chat.LlmResult
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ExpenseAgentTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDate.of(2026, 9, 6).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private class CapturedRequest(
        val text: String,
        val systemPrompt: String,
    )

    private fun agent(
        dao: FakeExpenseDao,
        budget: BudgetSnapshot = BudgetSnapshot(),
        onCapture: (CapturedRequest) -> Unit,
        responseJson: String,
    ): ExpenseAgent {
        val chatDao = FakeChatDao()
        val coordinator = ChatLlmCoordinator(
            expenseRepository = ExpenseRepository(dao),
            chatRepository = ChatRepository(chatDao),
            requestJson = { text, _, systemPrompt, _ ->
                onCapture(CapturedRequest(text, systemPrompt))
                responseJson
            },
            applyPlan = { MutationApplyResult(insertedIds = emptyList(), affectedIds = emptyList(), assistantMessageId = 1L) },
            nowProvider = { now },
            zone = zone,
        )
        val toolContext = AgentToolContext(
            expenseRepository = ExpenseRepository(dao),
            budgetSnapshotProvider = { budget },
            zone = zone,
        )
        return ExpenseAgent(coordinator, toolContext, nowProvider = { now }, zone = zone)
    }

    private fun prefs() = UserPrefsSnapshot(true, "https://example.com", "key", "test-model", com.expense.tracker.data.prefs.ThemeMode.SYSTEM)

    private suspend fun seed(dao: FakeExpenseDao) {
        dao.insert(
            ExpenseEntity(
                amountCents = 3500,
                categoryId = "food",
                note = "午饭",
                occurredAt = LocalDate.of(2026, 9, 5).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
                createdAt = now,
            ),
        )
        dao.insert(
            ExpenseEntity(
                amountCents = 1800,
                categoryId = "drink",
                note = "瑞幸咖啡",
                occurredAt = LocalDate.of(2026, 9, 6).atTime(10, 0).atZone(zone).toInstant().toEpochMilli(),
                createdAt = now,
            ),
        )
    }

    @Test
    fun `查询轮注入工具结果且不触发记账话术替换`() = runBlocking<Unit> {
        val dao = FakeExpenseDao()
        seed(dao)
        val captures = mutableListOf<CapturedRequest>()
        val cannedReply = "本月消费合计已记录 2 笔，¥53.00，最大头是餐饮 ¥35.00。"
        val agent = agent(dao, onCapture = { captures.add(it) }, responseJson = "{\"reply\":\"$cannedReply\",\"expenses\":[],\"actions\":[]}")

        val result = agent.submit("这个月花了多少？", prefs())

        assertThat(captures).hasSize(1)
        val prompt = captures.single().systemPrompt
        assertThat(prompt).contains("【查询结果")
        assertThat(prompt).contains("2026-09")
        assertThat(prompt).contains("¥53.00")
        assertThat(prompt).contains("本轮是查询轮")
        val ok = result as LlmResult.Ok
        // 虚假记账话术守卫被关闭：查询回复合法地包含"已记录"字样，不能被替换
        assertThat(ok.replyText).isEqualTo(cannedReply)
        assertThat(ok.expenseIds).isEmpty()
    }

    @Test
    fun `查询轮注入预算工具结果`() = runBlocking<Unit> {
        val dao = FakeExpenseDao()
        seed(dao)
        val captures = mutableListOf<CapturedRequest>()
        val agent = agent(
            dao,
            budget = BudgetSnapshot(monthlyLimitCents = 200_000L),
            onCapture = { captures.add(it) },
            responseJson = "{\"reply\":\"预算充足\",\"expenses\":[],\"actions\":[]}",
        )

        agent.submit("这个月预算还剩多少", prefs())

        assertThat(captures.single().systemPrompt).contains("月度总预算：已用 ¥53.00 / 预算 ¥2000.00")
    }

    @Test
    fun `记账轮不注入工具结果`() = runBlocking<Unit> {
        val dao = FakeExpenseDao()
        seed(dao)
        val captures = mutableListOf<CapturedRequest>()
        val agent = agent(
            dao,
            onCapture = { captures.add(it) },
            responseJson = "{\"reply\":\"ok\",\"expenses\":[{\"amount\":23.5,\"category\":\"transport\",\"note\":\"打车\",\"occurred_at\":\"2026-09-06T18:00:00\"}],\"actions\":[]}",
        )

        val result = agent.submit("打车花了23块5", prefs())

        assertThat(captures.single().systemPrompt).doesNotContain("【查询结果")
        assertThat(result).isInstanceOf(LlmResult.Ok::class.java)
    }
}
