package com.expense.tracker.agent

import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.repo.ExpenseRepository
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Test

class AgentToolsAnalyzeTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val dao = FakeExpenseDao()

    private fun context() = AgentToolContext(
        expenseRepository = ExpenseRepository(dao),
        budgetSnapshotProvider = { com.expense.tracker.data.budget.BudgetSnapshot() },
        zone = zone,
    )

    private fun entity(cents: Long, category: String, at: LocalDate) = ExpenseEntity(
        amountCents = cents,
        categoryId = category,
        note = "",
        occurredAt = at.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        createdAt = at.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
    )

    private fun monthSpec(): AgentPeriodSpec {
        val now = LocalDate.of(2026, 9, 9).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        return AgentPeriodResolver.defaultMonth(now, zone)
    }

    private suspend fun seed() {
        dao.insert(entity(3500, "food", LocalDate.of(2026, 9, 5)))
        dao.insert(entity(1800, "drink", LocalDate.of(2026, 9, 6)))
        dao.insert(entity(2000, "food", LocalDate.of(2026, 8, 10)))
        dao.insert(entity(100_000, "investment", LocalDate.of(2026, 8, 20)))
    }

    @Test
    fun `本期上期与分类趋势`() = runBlocking<Unit> {
        seed()
        val result = AgentTools.analyzeExpenses(context(), monthSpec(), epoch(2026, 9, 9), zone)
        assertThat(result.current.totalCents).isEqualTo(5300L)
        assertThat(result.current.count).isEqualTo(2)
        assertThat(result.previous).isNotNull()
        assertThat(result.previous!!.totalCents).isEqualTo(2000L)
        assertThat(result.previous!!.label).contains("2026-08")
        // 投资类不进趋势；按 |环比变化| 降序：drink +1800 > food +1500
        assertThat(result.trends.map { it.categoryId }).containsExactly("drink", "food").inOrder()
        val food = result.trends.first { it.categoryId == "food" }
        assertThat(food.percentLabel()).isEqualTo("+75%")
    }

    @Test
    fun `上月为零时环比返回null由提示词改写为新增支出`() = runBlocking<Unit> {
        seed()
        val result = AgentTools.analyzeExpenses(context(), monthSpec(), epoch(2026, 9, 9), zone)
        val drink = result.trends.first { it.categoryId == "drink" }
        assertThat(drink.percentLabel()).isNull()
        assertThat(drink.previousCents).isEqualTo(0L)
    }

    @Test
    fun `满7天数据才给月底预测`() = runBlocking<Unit> {
        seed()
        // 9月6日只过了6天 → 不预测
        val early = AgentTools.analyzeExpenses(context(), monthSpec(), epoch(2026, 9, 6), zone)
        assertThat(early.forecast).isNull()
        // 9月9日过了9天 → 日均 pace 外推 30 天
        val later = AgentTools.analyzeExpenses(context(), monthSpec(), epoch(2026, 9, 9), zone)
        assertThat(later.forecast).isNotNull()
        assertThat(later.forecast!!.basis).isEqualTo("daily_average")
        assertThat(later.forecast!!.elapsedDays).isEqualTo(9)
        assertThat(later.forecast!!.totalDays).isEqualTo(30)
        assertThat(later.forecast!!.projectedCents).isEqualTo(5300L * 30 / 9)
    }

    @Test
    fun `月底预测排除投资类`() = runBlocking<Unit> {
        dao.insert(entity(3500, "food", LocalDate.of(2026, 9, 5)))
        dao.insert(entity(100_000, "investment", LocalDate.of(2026, 9, 7)))
        val result = AgentTools.analyzeExpenses(context(), monthSpec(), epoch(2026, 9, 9), zone)
        assertThat(result.forecast).isNotNull()
        // 预测口径 = 消费合计（不含投资）：3500/9 * 30，而不是 (3500+100000)/9 * 30。
        assertThat(result.forecast!!.projectedCents).isEqualTo(3500L * 30 / 9)
    }

    @Test
    fun `上月有本月零的分类进入趋势并显示已清零`() = runBlocking<Unit> {
        dao.insert(entity(2000, "food", LocalDate.of(2026, 9, 3)))
        dao.insert(entity(1800, "drink", LocalDate.of(2026, 8, 10)))
        val result = AgentTools.analyzeExpenses(context(), monthSpec(), epoch(2026, 9, 9), zone)

        val drink = result.trends.first { it.categoryId == "drink" }
        assertThat(drink.currentCents).isEqualTo(0L)
        assertThat(drink.previousCents).isEqualTo(1800L)

        val rendered = AgentPrompts.toolResultBlock(
            QueryToolResult(
                periodLabel = "2026-09（本月）",
                filteredCategories = emptySet(),
                totalCents = 2000L,
                totalCount = 1,
                byCategory = emptyList(),
                investmentCents = 0L,
                topNotes = emptyList(),
                records = emptyList(),
                recordsTruncated = false,
            ),
            budget = null,
            analyze = result,
        )
        assertThat(rendered).contains("饮品")
        assertThat(rendered).contains("已清零")
    }

    @Test
    fun `储蓄节奏工具只统计本月非投资消费并上报名`() = runBlocking<Unit> {
        seed()
        val tools = mutableListOf<String>()
        val ctx = AgentToolContext(
            expenseRepository = ExpenseRepository(dao),
            budgetSnapshotProvider = { com.expense.tracker.data.budget.BudgetSnapshot() },
            zone = zone,
            onToolCall = { tools += it },
        )

        val pace = AgentTools.savingsPace(
            context = ctx,
            incomeCents = 800_000L,
            goalCents = 500_000L,
            nowMillis = epoch(2026, 9, 9),
            zone = zone,
        )!!

        assertThat(tools).containsExactly(AgentTools.TOOL_SAVINGS_PACE)
        // 9 月非投资 5300（投资 100000 与 8 月记录都不进窗口）
        assertThat(pace.spentCents).isEqualTo(5_300L)
        assertThat(pace.elapsedDays).isEqualTo(9)
        assertThat(pace.daysInMonth).isEqualTo(30)
        assertThat(pace.projectedSpendCents).isEqualTo(5_300L * 30 / 9)
        assertThat(pace.onTrack).isTrue()
    }

    @Test
    fun `储蓄节奏输入缺失不计算`() = runBlocking<Unit> {
        seed()
        assertThat(AgentTools.savingsPace(context(), 0L, 500_000L, epoch(2026, 9, 9), zone)).isNull()
        assertThat(AgentTools.savingsPace(context(), 800_000L, 0L, epoch(2026, 9, 9), zone)).isNull()
    }

    @Test
    fun `非整月区间不预测`() = runBlocking<Unit> {
        seed()
        val spec = AgentPeriodSpec(
            label = "最近 3 天",
            fromMillis = epoch(2026, 9, 6),
            toMillis = epoch(2026, 9, 10),
        )
        val result = AgentTools.analyzeExpenses(context(), spec, epoch(2026, 9, 9), zone)
        assertThat(result.forecast).isNull()
    }

    private fun epoch(y: Int, m: Int, d: Int): Long =
        LocalDate.of(y, m, d).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
}
