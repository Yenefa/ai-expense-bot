package com.expense.tracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.model.Category
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
    val dateLabel: String,    // "6月15日"
    val total: Double,
    val items: List<DisplayExpense>,
)

data class DisplayExpense(
    val id: Long,
    val amount: Double,
    val categoryEmoji: String,
    val categoryName: String,
    val note: String,
    val timeLabel: String,  // "14:30"
)

data class HistoryUiState(
    val groups: List<DailyGroup> = emptyList(),
)

class HistoryViewModel(private val repo: ExpenseRepository) : ViewModel() {

    private val internal = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = internal.asStateFlow()

    private val fmt = DateTimeFormatter.ofPattern("M月d日")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    private val zone = ZoneId.systemDefault()

    init {
        viewModelScope.launch {
            repo.observeAll().collect { list -> internal.update { aggregate(list) } }
        }
    }

    fun deleteExpense(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }

    private fun aggregate(list: List<ExpenseEntity>): HistoryUiState {
        val groups = list
            .sortedByDescending { it.occurredAt }
            .groupBy { e ->
                Instant.ofEpochMilli(e.occurredAt).atZone(zone).toLocalDate()
            }
            .map { (date, items) ->
                DailyGroup(
                    dateLabel = date.format(fmt),
                    total = items.sumOf { it.amount },
                    items = items.map { e ->
                        val cat = Category.byIdOrOther(e.categoryId)
                        DisplayExpense(
                            id = e.id,
                            amount = e.amount,
                            categoryEmoji = cat.emoji,
                            categoryName = cat.displayName,
                            note = e.note,
                            timeLabel = Instant.ofEpochMilli(e.occurredAt).atZone(zone).format(timeFmt),
                        )
                    },
                )
            }
        return HistoryUiState(groups = groups)
    }
}
