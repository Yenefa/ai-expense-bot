package com.expense.tracker.proactive

import com.expense.tracker.agent.FakeExpenseDao
import com.expense.tracker.data.budget.BudgetSnapshot
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.repo.ExpenseRepository
import com.expense.tracker.memory.MemoryFact
import com.expense.tracker.memory.MemoryType
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * ProactiveEngine 输入构建 + 治理（前台/后台共用）：
 * 验证时间窗口（本月/本周/近 4 周）、投资类排除、记忆读权限与历史落库。
 */
class ProactiveEngineTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val dao = FakeExpenseDao()
    private val store = FakeProactiveStateStore()

    private suspend fun insert(cents: Long, category: String, date: LocalDate) {
        val at = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        dao.insert(
            ExpenseEntity(
                amountCents = cents,
                categoryId = category,
                note = "",
                occurredAt = at,
                createdAt = at,
            ),
        )
    }

    private fun engine(
        nowDate: LocalDate,
        budget: BudgetSnapshot = BudgetSnapshot(),
        facts: List<MemoryFact> = emptyList(),
    ) = ProactiveEngine(
        expenseRepository = ExpenseRepository(dao),
        budgetSnapshot = { budget },
        memoryFacts = { facts },
        governor = ProactiveGovernor(store, { ProactiveAlertType.entries.toSet() }),
        zone = zone,
        nowProvider = { nowDate.atTime(12, 0).atZone(zone).toInstant().toEpochMilli() },
    )

    @Test
    fun `本周异常按周一至周日窗口与近4周基线计算`() = runBlocking<Unit> {
        // 2026-09-16 为周三；近 4 个完整周各 50,000，本周（09-14 起）90,000
        insert(50_000L, "food", LocalDate.of(2026, 8, 19))
        insert(50_000L, "food", LocalDate.of(2026, 8, 26))
        insert(50_000L, "food", LocalDate.of(2026, 9, 2))
        insert(50_000L, "food", LocalDate.of(2026, 9, 9))
        insert(90_000L, "food", LocalDate.of(2026, 9, 15))

        val alert = engine(LocalDate.of(2026, 9, 16)).evaluate()!!

        assertThat(alert.type).isEqualTo(ProactiveAlertType.ANOMALOUS_SPENDING)
        assertThat(alert.facts["current_week_cents"]).isEqualTo(90_000L)
        assertThat(alert.facts["baseline_cents"]).isEqualTo(50_000L)
        assertThat(store.history()).hasSize(1)
        assertThat(store.history().single().copy).isEqualTo(alert.copy)
    }

    @Test
    fun `预算口径排除投资类且要求4个样本`() = runBlocking<Unit> {
        insert(30_000L, "food", LocalDate.of(2026, 9, 2))
        insert(30_000L, "drink", LocalDate.of(2026, 9, 5))
        insert(20_000L, "food", LocalDate.of(2026, 9, 10))
        insert(10_000L, "transport", LocalDate.of(2026, 9, 12))
        insert(5_000_000L, "investment", LocalDate.of(2026, 9, 4))

        val alert = engine(
            nowDate = LocalDate.of(2026, 9, 16),
            budget = BudgetSnapshot(monthlyLimitCents = 100_000L),
        ).evaluate()!!

        // 90,000 / 100,000 = 90% → WARN；若投资类计入会直接超支（OVER）
        assertThat(alert.type).isEqualTo(ProactiveAlertType.BUDGET_THRESHOLD)
        assertThat(alert.severity).isEqualTo(ProactiveSeverity.WARN)
        assertThat(alert.facts["spent_cents"]).isEqualTo(90_000L)
    }

    @Test
    fun `未来月份账目不计入本月预算与笔数`() = runBlocking<Unit> {
        insert(30_000L, "food", LocalDate.of(2026, 9, 2))
        insert(30_000L, "drink", LocalDate.of(2026, 9, 5))
        insert(20_000L, "food", LocalDate.of(2026, 9, 10))
        insert(10_000L, "transport", LocalDate.of(2026, 9, 12))
        // 未来月份（JSON 恢复/CSV 导入可能带入）不得进入本月窗口
        insert(999_999L, "food", LocalDate.of(2026, 10, 2))

        val alert = engine(
            nowDate = LocalDate.of(2026, 9, 16),
            budget = BudgetSnapshot(monthlyLimitCents = 100_000L),
        ).evaluate()!!

        assertThat(alert.severity).isEqualTo(ProactiveSeverity.WARN)
        assertThat(alert.facts["spent_cents"]).isEqualTo(90_000L)
    }

    @Test
    fun `储蓄节奏读取已确认画像并排除投资消费`() = runBlocking<Unit> {
        insert(100_000L, "food", LocalDate.of(2026, 9, 3))
        insert(200_000L, "food", LocalDate.of(2026, 9, 10))
        insert(300_000L, "drink", LocalDate.of(2026, 9, 15))
        insert(50_000L, "food", LocalDate.of(2026, 9, 15))
        insert(5_000_000L, "investment", LocalDate.of(2026, 9, 4))

        val facts = listOf(
            MemoryFact(type = MemoryType.MONTHLY_INCOME, amountCents = 800_000L),
            MemoryFact(type = MemoryType.SAVINGS_GOAL, amountCents = 200_000L),
            MemoryFact(type = MemoryType.MERCHANT_ALIAS, merchant = "瑞幸", categoryId = "drink"),
        )
        val alert = engine(LocalDate.of(2026, 9, 16), facts = facts).evaluate()!!

        // 消费 650,000（不含投资）→ 预计 650000/16*30 = 1218750 → 结余 -418750 → OVER
        assertThat(alert.type).isEqualTo(ProactiveAlertType.SAVINGS_GOAL_DEVIATION)
        assertThat(alert.severity).isEqualTo(ProactiveSeverity.OVER)
        assertThat(alert.facts["projected_spend_cents"]).isEqualTo(1_218_750L)
        assertThat(alert.facts["projected_leftover_cents"]).isEqualTo(-418_750L)
    }

    @Test
    fun `无记忆时储蓄规则不触发且不写历史`() = runBlocking<Unit> {
        insert(100_000L, "food", LocalDate.of(2026, 9, 3))
        insert(200_000L, "food", LocalDate.of(2026, 9, 10))
        insert(300_000L, "drink", LocalDate.of(2026, 9, 15))
        insert(50_000L, "food", LocalDate.of(2026, 9, 15))

        val alert = engine(LocalDate.of(2026, 9, 16)).evaluate()

        assertThat(alert).isNull()
        assertThat(store.history()).isEmpty()
    }
}
