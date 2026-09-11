package com.expense.tracker.data.recurring

import com.expense.tracker.data.db.ExpenseDao
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.db.RecurringPeriodType
import com.expense.tracker.data.db.RecurringRuleDao
import com.expense.tracker.data.db.RecurringRuleEntity
import com.expense.tracker.data.repo.ExpenseRepository
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** 内存版周期规则 DAO（与 agent/FakeDaos.kt 的 Fake 同构），供 runOnce 测试使用。 */
private class FakeRecurringRuleDao : RecurringRuleDao {
    val state = MutableStateFlow<List<RecurringRuleEntity>>(emptyList())
    private var seq = 0L

    override suspend fun insert(rule: RecurringRuleEntity): Long {
        seq++
        state.value = state.value + rule.copy(id = seq)
        return seq
    }

    override suspend fun insertAll(rules: List<RecurringRuleEntity>) {
        rules.forEach { insert(it) }
    }

    override suspend fun clearAll() {
        state.value = emptyList()
    }

    override suspend fun update(rule: RecurringRuleEntity) {
        state.value = state.value.map { if (it.id == rule.id) rule else it }
    }

    override suspend fun delete(rule: RecurringRuleEntity) {
        state.value = state.value.filterNot { it.id == rule.id }
    }

    override fun observeAll(): Flow<List<RecurringRuleEntity>> = state

    override suspend fun getEnabledDue(nowMillis: Long): List<RecurringRuleEntity> =
        state.value.filter { it.enabled && it.nextDueAt <= nowMillis }

    override suspend fun getById(id: Long): RecurringRuleEntity? =
        state.value.firstOrNull { it.id == id }

    override suspend fun deleteById(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }
}

/** 内存版账目 DAO；onInsert 钩子用于制造“已读到到期规则、尚未落库”的并发窗口。 */
private class FakeExpenseDao : ExpenseDao {
    val state = MutableStateFlow<List<ExpenseEntity>>(emptyList())
    private var seq = 0L
    var onInsert: suspend () -> Unit = {}

    override suspend fun insert(expense: ExpenseEntity): Long {
        onInsert()
        seq++
        state.value = state.value + expense.copy(id = seq)
        return seq
    }

    override suspend fun insertAll(expenses: List<ExpenseEntity>): List<Long> =
        expenses.map { insert(it) }

    override suspend fun clearAll() {
        state.value = emptyList()
    }

    override suspend fun update(expense: ExpenseEntity) {
        state.value = state.value.map { if (it.id == expense.id) expense else it }
    }

    override fun observeActive(): Flow<List<ExpenseEntity>> = state

    override suspend fun getAllActiveOnce(): List<ExpenseEntity> =
        state.value.filter { it.deletedAt == null }

    override suspend fun getAllOnce(): List<ExpenseEntity> = state.value

    override suspend fun getById(id: Long): ExpenseEntity? = state.value.firstOrNull { it.id == id }

    override fun observeInRange(from: Long, to: Long): Flow<List<ExpenseEntity>> =
        flow { emit(state.value.filter { it.occurredAt in from until to && it.deletedAt == null }) }

    override fun observeDeleted(): Flow<List<ExpenseEntity>> =
        flow { emit(state.value.filter { it.deletedAt != null }) }

    override suspend fun softDeleteById(id: Long, deletedAtMillis: Long) {
        state.value = state.value.map { if (it.id == id) it.copy(deletedAt = deletedAtMillis) else it }
    }

    override suspend fun restoreById(id: Long) {
        state.value = state.value.map { if (it.id == id) it.copy(deletedAt = null) else it }
    }

    override suspend fun deleteById(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun purgeOlderThan(cutoffMillis: Long) {}
}

class RecurringGeneratorTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    private fun rule(
        periodType: RecurringPeriodType,
        dayOfMonth: Int = 1,
        dayOfWeek: Int = 1,
        monthOfYear: Int = 1,
        nextDueAt: Long,
        amountCents: Long = 100L,
    ) = RecurringRuleEntity(
        amountCents = amountCents,
        categoryId = "housing",
        note = "房租",
        periodType = periodType.name,
        dayOfMonth = dayOfMonth,
        dayOfWeek = dayOfWeek,
        monthOfYear = monthOfYear,
        nextDueAt = nextDueAt,
        createdAt = nextDueAt,
    )

    private fun day(y: Int, m: Int, d: Int): Long =
        LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun monthlyAdvancesToNextMonthKeepingDay() {
        val r = rule(RecurringPeriodType.MONTHLY, dayOfMonth = 15, nextDueAt = day(2026, 8, 15))
        // 已到期 10 天（8/25），应补最近一期 8/15 并推进到 9/15（跳过 9/15 前的所有）
        val next = RecurringGenerator.nextDueAfter(r, r.nextDueAt, day(2026, 8, 25), zone)
        assertThat(next).isEqualTo(day(2026, 9, 15))
    }

    @Test
    fun monthlyDay31FallsBackToMonthLastDay() {
        val r = rule(RecurringPeriodType.MONTHLY, dayOfMonth = 31, nextDueAt = day(2026, 1, 31))
        val next = RecurringGenerator.nextDueAfter(r, r.nextDueAt, day(2026, 1, 31), zone)
        // 2 月没有 31 日 → 2/28（2026 非闰年）
        assertThat(next).isEqualTo(day(2026, 2, 28))
    }

    @Test
    fun monthlyDay31DoesNotDriftAfterFebruaryClamp() {
        // 2 月钳位到 28 后，下一次必须回到规则日 31（3/31），而不是停在 3/28
        val r = rule(RecurringPeriodType.MONTHLY, dayOfMonth = 31, nextDueAt = day(2026, 2, 28))
        val next = RecurringGenerator.nextDueAfter(r, r.nextDueAt, day(2026, 2, 28), zone)
        assertThat(next).isEqualTo(day(2026, 3, 31))
    }

    @Test
    fun addMonthlyRuleUsesRuleDayInCurrentMonth() {
        // 添加路径（RecurringViewModel）以“今天零点”为基准：1/15 添加“每月 20 号”→ 首个到期 1/20
        val now = day(2026, 1, 15)
        val r = rule(RecurringPeriodType.MONTHLY, dayOfMonth = 20, nextDueAt = now)
        val firstDue = RecurringGenerator.nextDueAfter(r, now, now, zone)
        assertThat(firstDue).isEqualTo(day(2026, 1, 20))
    }

    @Test
    fun weeklyAdvancesToNextGivenWeekday() {
        // 2026-08-06 是周四，每周五到期（5=周五）
        val r = rule(RecurringPeriodType.WEEKLY, dayOfWeek = 5, nextDueAt = day(2026, 8, 7))
        val next = RecurringGenerator.nextDueAfter(r, r.nextDueAt, day(2026, 8, 6), zone)
        assertThat(next).isEqualTo(day(2026, 8, 7))
    }

    @Test
    fun yearlyAdvancesToNextYearKeepingMonthAndDay() {
        val r = rule(RecurringPeriodType.YEARLY, monthOfYear = 6, dayOfMonth = 20, nextDueAt = day(2025, 6, 20))
        val next = RecurringGenerator.nextDueAfter(r, r.nextDueAt, day(2026, 1, 1), zone)
        assertThat(next).isEqualTo(day(2026, 6, 20))
    }

    @Test
    fun addYearlyRuleLaterThisYearDoesNotSkipToNextYear() {
        // 3/15 添加“每年 12/1”→ 首个到期 2026-12-01，而不是 2027-12-01
        val now = day(2026, 3, 15)
        val r = rule(RecurringPeriodType.YEARLY, monthOfYear = 12, dayOfMonth = 1, nextDueAt = now)
        val firstDue = RecurringGenerator.nextDueAfter(r, now, now, zone)
        assertThat(firstDue).isEqualTo(day(2026, 12, 1))
    }

    @Test
    fun longGapOnlyAdvancesToNearestFutureDue() {
        // 房租每月 1 日，上次到期 7/1，现在 12/10：直接到 1/1（跳过 8-12 月）
        val r = rule(RecurringPeriodType.MONTHLY, dayOfMonth = 1, nextDueAt = day(2026, 7, 1))
        val next = RecurringGenerator.nextDueAfter(r, r.nextDueAt, day(2026, 12, 10), zone)
        assertThat(next).isEqualTo(day(2027, 1, 1))
    }

    @Test
    fun runOnceGeneratesEachDueSlotOnlyOnce() = runBlocking {
        val ruleDao = FakeRecurringRuleDao()
        val expenseDao = FakeExpenseDao()
        val repo = ExpenseRepository(expenseDao)
        val now = day(2026, 1, 15)
        ruleDao.insert(rule(RecurringPeriodType.MONTHLY, dayOfMonth = 15, nextDueAt = day(2026, 1, 15)))

        assertThat(RecurringGenerator.runOnce(ruleDao, repo, now, zone)).isEqualTo(1)
        // 同一到期点再次运行：nextDueAt 已推进到未来，不得重复生成
        assertThat(RecurringGenerator.runOnce(ruleDao, repo, now, zone)).isEqualTo(0)
        assertThat(expenseDao.state.value).hasSize(1)
        assertThat(ruleDao.state.value.single().nextDueAt).isEqualTo(day(2026, 2, 15))
    }

    @Test
    fun runOnceContinuesWhenOneRuleFails() = runBlocking {
        val ruleDao = FakeRecurringRuleDao()
        val expenseDao = FakeExpenseDao()
        val repo = ExpenseRepository(expenseDao)
        val now = day(2026, 1, 15)
        // 第一条金额非法（addCents require 失败），第二条正常：失败不能中断整批
        ruleDao.insert(
            rule(RecurringPeriodType.MONTHLY, dayOfMonth = 15, nextDueAt = day(2026, 1, 15), amountCents = 0L),
        )
        ruleDao.insert(
            rule(RecurringPeriodType.MONTHLY, dayOfMonth = 15, nextDueAt = day(2026, 1, 15), amountCents = 100L),
        )

        val generated = RecurringGenerator.runOnce(ruleDao, repo, now, zone)

        assertThat(generated).isEqualTo(1)
        assertThat(expenseDao.state.value).hasSize(1)
        assertThat(expenseDao.state.value.single().amountCents).isEqualTo(100L)
    }

    @Test
    fun concurrentRunOnceGeneratesExactlyOneExpense() = runBlocking {
        val ruleDao = FakeRecurringRuleDao()
        val expenseDao = FakeExpenseDao()
        val repo = ExpenseRepository(expenseDao)
        val now = day(2026, 1, 15)
        ruleDao.insert(rule(RecurringPeriodType.MONTHLY, dayOfMonth = 15, nextDueAt = day(2026, 1, 15)))

        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        // 挂起在“已读到到期规则、尚未落库”的窗口，模拟 App 启动与提醒 Worker 并发
        expenseDao.onInsert = {
            entered.complete(Unit)
            release.await()
        }

        val first = async { RecurringGenerator.runOnce(ruleDao, repo, now, zone) }
        entered.await()
        val second = async { RecurringGenerator.runOnce(ruleDao, repo, now, zone) }
        delay(50) // 让第二个调用完成“读到期规则”（无锁时会读到同一条并重复落库）
        release.complete(Unit)

        first.await()
        second.await()
        assertThat(expenseDao.state.value).hasSize(1)
    }
}
