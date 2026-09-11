package com.expense.tracker.ui.history

import com.expense.tracker.agent.FakeExpenseDao
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.repo.ExpenseRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    @Before fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun teardown() = Dispatchers.resetMain()

    // ViewModel 内部用 ZoneId.systemDefault() 聚合，测试用同一时区构造时间戳，跨机器确定
    private val zone = ZoneId.systemDefault()

    private fun millisAt(date: LocalDate, hour: Int = 12): Long =
        date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `cross-year same month-day groups expose distinct keys`() = runTest {
        val dao = FakeExpenseDao()
        val older = millisAt(LocalDate.of(2025, 6, 15))
        val newer = millisAt(LocalDate.of(2026, 6, 15))
        dao.state.value = listOf(
            ExpenseEntity(amountCents = 1_000L, categoryId = "food", note = "", occurredAt = older, createdAt = older),
            ExpenseEntity(amountCents = 2_000L, categoryId = "food", note = "", occurredAt = newer, createdAt = newer),
        )

        val vm = HistoryViewModel(ExpenseRepository(dao))
        val state = vm.uiState.first { it.groups.size == 2 }

        // 展示文案相同（都是 6月15日），但 key 必须含年份且互不相同，否则 LazyColumn 会因重复 key 崩溃
        assertThat(state.groups.map { it.dateLabel })
            .containsExactly("6月15日", "6月15日")
        assertThat(state.groups.map { it.dateIso })
            .containsExactly("2026-06-15", "2025-06-15").inOrder()
        assertThat(state.groups.map { it.dateIso }.toSet()).hasSize(2)
    }
}
