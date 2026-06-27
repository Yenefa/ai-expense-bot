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

data class SubPeriodDetail(
    val totalAmount: Double,
    val byCategory: Map<String, Double>,
)

data class AnalyticsUiState(
    val period: Period = Period.Week,
    val barAmounts: List<Double> = emptyList(),
    val lineCounts: List<Int> = emptyList(),
    val pieByCategory: Map<String, Double> = emptyMap(),
    val xLabels: List<String> = emptyList(),
    val totalAmount: Double = 0.0,
    val totalCount: Int = 0,
    val insights: List<String> = emptyList(),
    val analyzing: Boolean = false,
    val subPeriods: List<SubPeriodDetail> = emptyList(),
    val selectedSubPeriodIndex: Int? = null,
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
    private val periodTrigger = MutableStateFlow(Period.Week)

    init {
        viewModelScope.launch {
            periodTrigger.flatMapLatest { p ->
                val now = nowProvider()
                val (from, to) = TimeRanges.rangeOf(p, now, zone)
                kotlinx.coroutines.flow.combine(
                    repo.observeInRange(from, to),
                    kotlinx.coroutines.flow.flowOf(Triple(p, from, now)),
                ) { list, t -> t to list }
            }.collect { (triple, list) ->
                val (p, from, now) = triple
                internal.update { aggregate(p, from, now, list) }
            }
        }
    }

    fun selectPeriod(p: Period) {
        periodTrigger.value = p
    }

    fun selectSubPeriod(index: Int?) {
        internal.update { it.copy(selectedSubPeriodIndex = index) }
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
        val top3 = state.pieByCategory.entries
            .sortedByDescending { it.value }
            .take(3)
            .map { (catId, v) ->
                val cat = Category.byId(catId)
                (if (cat != null) "${cat.emoji} ${cat.displayName}" else catId) to v
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

    private fun aggregate(p: Period, fromMillis: Long, nowMillis: Long, list: List<ExpenseEntity>): AnalyticsUiState {
        val labels = TimeRanges.bucketLabels(p, fromMillis, zone)
        val n = labels.size

        // 自动选择默认子周期：月→当天，年→当月，周→总览
        val nowDate = java.time.LocalDate.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
        val defaultSubIndex: Int? = when (p) {
            Period.Month -> {
                val today = nowDate.dayOfMonth - 1
                if (today in 0 until n) today else null
            }
            Period.Year -> {
                val thisMonth = nowDate.monthValue - 1
                if (thisMonth in 0 until n) thisMonth else null
            }
            Period.Week -> null
        }
        val amounts = DoubleArray(n)
        val counts = IntArray(n)
        val byCat = HashMap<String, Double>()

        // 投资类不计入消费分析图表（短线很快收回，不算真实开销）
        val consumptionList = list.filter { e ->
            !(Category.byId(e.categoryId)?.isInvestment ?: false)
        }

        consumptionList.forEach { e ->
            val idx = TimeRanges.bucketIndex(p, fromMillis, e.occurredAt, zone)
            if (idx in 0 until n) {
                amounts[idx] += e.amount
                counts[idx] += 1
            }
            byCat[e.categoryId] = (byCat[e.categoryId] ?: 0.0) + e.amount
        }

        // 单次遍历：按桶索引分组，避免 O(n*m) 重复计算 bucketIndex
        val bucketed = consumptionList.groupBy { e ->
            val idx = TimeRanges.bucketIndex(p, fromMillis, e.occurredAt, zone)
            if (idx in 0 until n) idx else -1
        }
        val subPeriodDetails = (0 until n).map { idx ->
            val entries = bucketed[idx] ?: emptyList()
            SubPeriodDetail(
                totalAmount = entries.sumOf { it.amount },
                byCategory = entries.groupBy({ it.categoryId }, { it.amount })
                    .mapValues { (_, amounts) -> amounts.sum() },
            )
        }

        return AnalyticsUiState(
            period = p,
            barAmounts = amounts.toList(),
            lineCounts = counts.toList(),
            pieByCategory = byCat,
            xLabels = labels,
            totalAmount = consumptionList.sumOf { it.amount },
            totalCount = consumptionList.size,
            insights = emptyList(), // 切换周期清除历史洞察
            subPeriods = subPeriodDetails,
            selectedSubPeriodIndex = defaultSubIndex,
        )
    }
}
