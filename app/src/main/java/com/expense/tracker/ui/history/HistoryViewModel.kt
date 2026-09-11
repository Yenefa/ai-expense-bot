package com.expense.tracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.model.Category
import com.expense.tracker.data.model.Money
import com.expense.tracker.data.repo.ExpenseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class DailyGroup(
    val dateIso: String,      // "2026-06-15"，含年份，跨年唯一：仅用于 key，不用于展示
    val dateLabel: String,    // "6月15日"，仅用于展示
    val total: Double,
    val items: List<DisplayExpense>,
)

data class DisplayExpense(
    val id: Long,
    val amountCents: Long,
    val categoryId: String,
    val categoryEmoji: String,
    val categoryName: String,
    val note: String,
    val occurredAt: Long,
    val createdAt: Long,
    val timeLabel: String,  // "14:30"
)

data class HistoryUiState(
    val groups: List<DailyGroup> = emptyList(),
    /** 按本地时区聚合每天的金额+笔数，供日历视图使用 */
    val cellsByDate: Map<java.time.LocalDate, DayCell> = emptyMap(),
)

class HistoryViewModel(private val repo: ExpenseRepository) : ViewModel() {

    private val internal = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = internal.asStateFlow()

    private val fmt = DateTimeFormatter.ofPattern("M月d日")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    private val zone = ZoneId.systemDefault()

    init {
        viewModelScope.launch {
            repo.observeActive().collect { list -> internal.update { aggregate(list) } }
        }
    }

    /** v2.9: 软删除 — 标记 deletedAt，在最近删除列表可恢复 */
    fun deleteExpense(id: Long) {
        viewModelScope.launch { repo.softDelete(id) }
    }

    /** 把 DisplayExpense 还原为完整的 ExpenseEntity，传给编辑弹窗。 */
    fun toEntity(d: DisplayExpense): ExpenseEntity = ExpenseEntity(
        amountCents = d.amountCents,
        categoryId = d.categoryId,
        note = d.note,
        occurredAt = d.occurredAt,
        createdAt = d.createdAt,
        id = d.id,
    )

    /** 保存编辑：UPDATE expenses 表的对应 id 行，不会新建记录。 */
    fun updateExpense(updated: ExpenseEntity) {
        viewModelScope.launch { repo.update(updated) }
    }

    private fun aggregate(list: List<ExpenseEntity>): HistoryUiState {
        // 按本地日期 group 一次，重用给 list/calendar 两套 UI
        val byDate = list.groupBy { e ->
            Instant.ofEpochMilli(e.occurredAt).atZone(zone).toLocalDate()
        }

        val groups = byDate.entries
            .sortedByDescending { it.key }
            .map { (date, items) ->
                DailyGroup(
                    dateIso = date.toString(),
                    dateLabel = date.format(fmt),
                    total = Money.centsToYuan(items.sumOf { it.amountCents }),
                    items = items.sortedByDescending { it.occurredAt }.map { e ->
                        val cat = Category.byIdOrOther(e.categoryId)
                        DisplayExpense(
                            id = e.id,
                            amountCents = e.amountCents,
                            categoryId = e.categoryId,
                            categoryEmoji = cat.emoji,
                            categoryName = cat.displayName,
                            note = e.note,
                            occurredAt = e.occurredAt,
                            createdAt = e.createdAt,
                            timeLabel = Instant.ofEpochMilli(e.occurredAt).atZone(zone).format(timeFmt),
                        )
                    },
                )
            }

        val cellsByDate = byDate.mapValues { (date, items) ->
            DayCell(date = date, total = Money.centsToYuan(items.sumOf { it.amountCents }), count = items.size)
        }

        return HistoryUiState(groups = groups, cellsByDate = cellsByDate)
    }
}
