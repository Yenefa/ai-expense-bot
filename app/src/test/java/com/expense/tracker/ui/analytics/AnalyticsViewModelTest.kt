package com.expense.tracker.ui.analytics

import com.expense.tracker.data.db.ExpenseDao
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.model.Period
import com.expense.tracker.data.repo.ExpenseRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

private class FakeExpenseDaoForAnalytics : ExpenseDao {
    private val state = MutableStateFlow<List<ExpenseEntity>>(emptyList())
    private var seq = 0L

    fun insertRaw(entity: ExpenseEntity) {
        seq++
        state.value = state.value + entity.copy(id = seq)
    }

    override suspend fun insert(expense: ExpenseEntity): Long { seq++; state.value = state.value + expense.copy(id = seq); return seq }
    override suspend fun update(expense: ExpenseEntity) { state.value = state.value.map { if (it.id == expense.id) expense else it } }
    override fun observeActive(): Flow<List<ExpenseEntity>> = state
    override suspend fun getAllActiveOnce(): List<ExpenseEntity> = state.value.filter { it.deletedAt == null }
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
    override suspend fun deleteById(id: Long) { state.value = state.value.filterNot { it.id == id } }
    override suspend fun purgeOlderThan(cutoffMillis: Long) {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModelTest {
    @Before fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun teardown() = Dispatchers.resetMain()

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun millis(day: Int, hour: Int = 12): Long =
        LocalDate.of(2025, 6, day).atStartOfDay(zone).toInstant().toEpochMilli() + hour * 3600_000L

    @Test
    fun `aggregate creates subPeriods with per-day category breakdown for week`() = runTest {
        val dao = FakeExpenseDaoForAnalytics()
        val repo = ExpenseRepository(dao)
        // 周一（6/9）餐饮 30 元 + 交通 10 元；周二（6/10）购物 50 元
        dao.insertRaw(ExpenseEntity(amount = 30.0, categoryId = "food", note = "", occurredAt = millis(9), createdAt = millis(9)))
        dao.insertRaw(ExpenseEntity(amount = 10.0, categoryId = "transport", note = "", occurredAt = millis(9), createdAt = millis(9)))
        dao.insertRaw(ExpenseEntity(amount = 50.0, categoryId = "shopping", note = "", occurredAt = millis(10), createdAt = millis(10)))

        // 用周二的时刻触发 ViewModel，周期范围应是 6/9(周一) ~ 6/16
        val vm = AnalyticsViewModel(repo, zone, nowProvider = { millis(10) })
        val state = vm.uiState.first { it.subPeriods.isNotEmpty() }

        // subPeriods 应有 7 个元素（周一~周日）
        assertThat(state.subPeriods).hasSize(7)

        // 周一（索引 0）：总金额 40，food=30, transport=10
        val mon = state.subPeriods[0]
        assertThat(mon.totalAmount).isEqualTo(40.0)
        assertThat(mon.byCategory).containsEntry("food", 30.0)
        assertThat(mon.byCategory).containsEntry("transport", 10.0)

        // 周二（索引 1）：总金额 50，shopping=50
        val tue = state.subPeriods[1]
        assertThat(tue.totalAmount).isEqualTo(50.0)
        assertThat(tue.byCategory).containsEntry("shopping", 50.0)

        // 周三~周日：空
        (2 until 7).forEach { idx ->
            assertThat(state.subPeriods[idx].totalAmount).isEqualTo(0.0)
        }
    }

    @Test
    fun `selectSubPeriod updates index and null clears selection`() = runTest {
        val dao = FakeExpenseDaoForAnalytics()
        val repo = ExpenseRepository(dao)
        val now = millis(9)
        dao.insertRaw(ExpenseEntity(amount = 10.0, categoryId = "food", note = "", occurredAt = now, createdAt = now))

        val vm = AnalyticsViewModel(repo, zone, nowProvider = { now })
        vm.uiState.first { it.subPeriods.isNotEmpty() }

        // 初始为 null
        assertThat(vm.uiState.value.selectedSubPeriodIndex).isNull()

        // 选择索引 0
        vm.selectSubPeriod(0)
        assertThat(vm.uiState.value.selectedSubPeriodIndex).isEqualTo(0)

        // 选择索引 2
        vm.selectSubPeriod(2)
        assertThat(vm.uiState.value.selectedSubPeriodIndex).isEqualTo(2)

        // 清除选择
        vm.selectSubPeriod(null)
        assertThat(vm.uiState.value.selectedSubPeriodIndex).isNull()
    }

    @Test
    fun `switching period resets selectedSubPeriodIndex to null`() = runTest {
        val dao = FakeExpenseDaoForAnalytics()
        val repo = ExpenseRepository(dao)
        val now = millis(9)
        dao.insertRaw(ExpenseEntity(amount = 10.0, categoryId = "food", note = "", occurredAt = now, createdAt = now))

        val vm = AnalyticsViewModel(repo, zone, nowProvider = { millis(9) })
        vm.uiState.first { it.subPeriods.isNotEmpty() }

        vm.selectSubPeriod(0)
        assertThat(vm.uiState.value.selectedSubPeriodIndex).isEqualTo(0)

        // 切换周期应重置
        vm.selectPeriod(Period.Month)
        vm.uiState.first { it.period == Period.Month && it.subPeriods.isNotEmpty() }
        assertThat(vm.uiState.value.selectedSubPeriodIndex).isNull()
    }
}
