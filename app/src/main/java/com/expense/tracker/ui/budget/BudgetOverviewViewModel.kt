package com.expense.tracker.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expense.tracker.data.budget.BudgetCalculator
import com.expense.tracker.data.budget.BudgetOverview
import com.expense.tracker.data.budget.BudgetPrefs
import com.expense.tracker.data.repo.ExpenseRepository
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class BudgetOverviewUiState(
    val overview: BudgetOverview? = null,
    val monthLabel: String = "",
)

/** 分析页预算卡片：本月支出（按分类聚合）与预算实时对比。 */
class BudgetOverviewViewModel(
    private val budgetPrefs: BudgetPrefs,
    private val repo: ExpenseRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
    private val internal = MutableStateFlow(BudgetOverviewUiState())
    val uiState: StateFlow<BudgetOverviewUiState> = internal.asStateFlow()

    init {
        viewModelScope.launch {
            val monthStart = monthStartMillis()
            combine(
                budgetPrefs.snapshot,
                repo.observeInRange(monthStart, Long.MAX_VALUE),
            ) { budget, list ->
                val spentByCategory = list
                    .filter { it.deletedAt == null }
                    .groupBy { it.categoryId }
                    .mapValues { (_, items) -> items.sumOf { it.amountCents } }
                BudgetCalculator.overview(
                    monthlyLimitCents = budget.monthlyLimitCents,
                    categoryLimitsCents = budget.categoryLimitsCents,
                    monthlySpentByCategory = spentByCategory,
                ) to budget
            }.collect { (overview, _) ->
                internal.value = BudgetOverviewUiState(
                    overview = overview,
                    monthLabel = LocalDate.now(zone).format(DateTimeFormatter.ofPattern("M月")) + "已用",
                )
            }
        }
    }

    private fun monthStartMillis(): Long =
        LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
}
