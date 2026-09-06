package com.expense.tracker.agent

import com.expense.tracker.data.budget.BudgetSnapshot
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Test

class AgentToolsTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val dao = FakeExpenseDao()

    private fun millis(date: String, hour: Int = 12): Long =
        LocalDate.parse(date).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun context(budget: BudgetSnapshot = BudgetSnapshot()) =
        AgentToolContext(
            expenseRepository = com.expense.tracker.data.repo.ExpenseRepository(dao),
            budgetSnapshotProvider = { budget },
            zone = zone,
        )

    private fun monthSpec(y: Int, m: Int): AgentPeriodSpec {
        val now = LocalDate.of(y, m, 15).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        return AgentPeriodResolver.defaultMonth(now, zone)
    }

    private suspend fun seed() {
        dao.insert(entity(1800, "drink", "瑞幸咖啡", millis("2026-09-06", 10)))
        dao.insert(entity(3500, "food", "午饭", millis("2026-09-05", 12)))
        dao.insert(entity(2350, "transport", "滴滴打车", millis("2026-08-20", 18)))
        dao.insert(entity(100_000, "investment", "基金定投", millis("2026-08-25", 9)))
        dao.insert(entity(9999, "other", "已删除的记录", millis("2026-09-04", 8)).copy(deletedAt = 1L))
    }

    private fun entity(cents: Long, category: String, note: String, at: Long) =
        com.expense.tracker.data.db.ExpenseEntity(
            amountCents = cents,
            categoryId = category,
            note = note,
            occurredAt = at,
            createdAt = at,
        )

    @Test
    fun `查询月度合计与分类聚合`() = runBlocking<Unit> {
        seed()
        val result = AgentTools.queryExpenses(context(), monthSpec(2026, 9), emptySet(), zone)
        assertThat(result.totalCount).isEqualTo(2)
        assertThat(result.totalCents).isEqualTo(5300L)
        assertThat(result.byCategory.map { it.categoryId }).containsExactly("food", "drink").inOrder()
        assertThat(result.topNotes.first().note).isEqualTo("午饭")
        assertThat(result.records).hasSize(2)
        assertThat(result.records.first().note).isEqualTo("瑞幸咖啡")
        assertThat(result.recordsTruncated).isFalse()
    }

    @Test
    fun `投资不计入消费合计但单列`() = runBlocking<Unit> {
        seed()
        val result = AgentTools.queryExpenses(context(), monthSpec(2026, 8), emptySet(), zone)
        assertThat(result.totalCents).isEqualTo(2350L)
        assertThat(result.investmentCents).isEqualTo(100_000L)
        assertThat(result.byCategory.map { it.categoryId }).containsExactly("investment", "transport").inOrder()
    }

    @Test
    fun `分类过滤`() = runBlocking<Unit> {
        seed()
        val result = AgentTools.queryExpenses(context(), monthSpec(2026, 9), setOf("drink"), zone)
        assertThat(result.totalCount).isEqualTo(1)
        assertThat(result.totalCents).isEqualTo(1800L)
        assertThat(result.filteredCategories).containsExactly("drink")
    }

    @Test
    fun `预算状态`() = runBlocking<Unit> {
        seed()
        val budget = BudgetSnapshot(
            monthlyLimitCents = 200_000L,
            categoryLimitsCents = mapOf("food" to 50_000L),
        )
        val now = LocalDate.of(2026, 9, 6).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val result = AgentTools.budgetStatus(context(budget), now, zone)!!
        assertThat(result.monthly!!.limitCents).isEqualTo(200_000L)
        assertThat(result.monthly!!.amountCents).isEqualTo(5300L)
        assertThat(result.categories.first().categoryIdOrLabel()).isEqualTo("food")
        assertThat(result.daysLeftInMonth).isEqualTo(25)
    }

    @Test
    fun `未设置预算返回null`() = runBlocking<Unit> {
        seed()
        val now = LocalDate.of(2026, 9, 6).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        assertThat(AgentTools.budgetStatus(context(), now, zone)).isNull()
    }

    private fun com.expense.tracker.data.budget.BudgetEntry.categoryIdOrLabel(): String = label
}
