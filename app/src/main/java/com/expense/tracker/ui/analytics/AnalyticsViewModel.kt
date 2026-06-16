package com.expense.tracker.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.model.Category
import com.expense.tracker.data.model.Period
import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.expense.tracker.data.repo.ExpenseRepository
import com.expense.tracker.llm.LlmPrompt
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
    val categoryCounts: Map<String, Int> = emptyMap(),
    val xLabels: List<String> = emptyList(),
    val totalAmount: Double = 0.0,
    val totalCount: Int = 0,
    val insights: List<String> = emptyList(),
    val analyzing: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModel(
    private val repo: ExpenseRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val onRequestInsights: (suspend (String, UserPrefsSnapshot) -> List<String>)? = null,
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

    fun requestInsights(prefs: UserPrefsSnapshot) {
        val state = internal.value
        if (state.analyzing || state.totalAmount <= 0.0) return
        val handler = onRequestInsights ?: run {
            // 本地 fallback
            internal.update { it.copy(insights = listOf("请先配置 LLM API Key → 设置页面"), analyzing = false) }
            return
        }
        internal.update { it.copy(analyzing = true, insights = emptyList()) }

        // 构建 Top 3 分类
        val categoryCounts = state.categoryCounts
        val top3 = state.pieByCategory.entries
            .sortedByDescending { it.value }
            .take(3)
            .map { (catId, v) ->
                val c = Category.byId(catId)
                val label = c?.let { "${it.emoji}${it.displayName}" } ?: catId
                Triple(label, v, categoryCounts[catId] ?: 0)
            }
        val periodName = when (state.period) {
            Period.Week -> "本周"
            Period.Month -> "本月"
            Period.Year -> "本年"
        }
        val prompt = LlmPrompt.analyticsPrompt(
            periodName = periodName,
            totalAmount = state.totalAmount,
            count = state.totalCount,
            topCategories = top3,
        )
        viewModelScope.launch {
            val insights = handler(prompt, prefs)
            internal.update { it.copy(analyzing = false, insights = insights) }
        }
    }

    private fun aggregate(p: Period, fromMillis: Long, list: List<ExpenseEntity>): AnalyticsUiState {
        val labels = TimeRanges.bucketLabels(p, fromMillis, zone)
        val n = labels.size
        val amounts = DoubleArray(n)
        val counts = IntArray(n)
        val byCat = HashMap<String, Double>()
        val countByCat = HashMap<String, Int>()

        list.forEach { e ->
            val idx = TimeRanges.bucketIndex(p, fromMillis, e.occurredAt, zone)
            if (idx in 0 until n) {
                amounts[idx] += e.amount
                counts[idx] += 1
            }
            byCat[e.categoryId] = (byCat[e.categoryId] ?: 0.0) + e.amount
            countByCat[e.categoryId] = (countByCat[e.categoryId] ?: 0) + 1
        }

        return AnalyticsUiState(
            period = p,
            barAmounts = amounts.toList(),
            lineCounts = counts.toList(),
            pieByCategory = byCat,
            categoryCounts = countByCat,
            xLabels = labels,
            totalAmount = list.sumOf { it.amount },
            totalCount = list.size,
            insights = emptyList(), // 切换周期清除历史洞察
        )
    }
}
