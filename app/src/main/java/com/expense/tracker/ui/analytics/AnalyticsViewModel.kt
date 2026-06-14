package com.expense.tracker.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.model.Period
import com.expense.tracker.data.repo.ExpenseRepository
import com.expense.tracker.util.TimeRanges
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId

data class AnalyticsUiState(
    val period: Period = Period.Month,
    val barAmounts: List<Double> = emptyList(),
    val lineCounts: List<Int> = emptyList(),
    val pieByCategory: Map<String, Double> = emptyMap(),
    val xLabels: List<String> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModel(
    private val repo: ExpenseRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val internal = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = internal.asStateFlow()

    private val periodTrigger = MutableStateFlow(Period.Month)

    init {
        viewModelScope.launch {
            periodTrigger.flatMapLatest { p ->
                val (from, to) = TimeRanges.rangeOf(p, nowProvider(), zone)
                kotlinx.coroutines.flow.combine(
                    repo.observeInRange(from, to),
                    kotlinx.coroutines.flow.flowOf(Triple(p, from, to)),
                ) { list, t -> t to list }
            }.collect { (triple, list) ->
                val (p, from, _) = triple
                internal.update { aggregate(p, from, list) }
            }
        }
    }

    fun selectPeriod(p: Period) {
        periodTrigger.value = p
    }

    private fun aggregate(p: Period, fromMillis: Long, list: List<ExpenseEntity>): AnalyticsUiState {
        val labels = TimeRanges.bucketLabels(p, fromMillis, zone)
        val n = labels.size
        val amounts = DoubleArray(n)
        val counts = IntArray(n)
        val byCat = HashMap<String, Double>()

        list.forEach { e ->
            val idx = TimeRanges.bucketIndex(p, fromMillis, e.occurredAt, zone)
            if (idx in 0 until n) {
                amounts[idx] += e.amount
                counts[idx] += 1
            }
            byCat[e.categoryId] = (byCat[e.categoryId] ?: 0.0) + e.amount
        }

        return AnalyticsUiState(
            period = p,
            barAmounts = amounts.toList(),
            lineCounts = counts.toList(),
            pieByCategory = byCat,
            xLabels = labels,
        )
    }
}
