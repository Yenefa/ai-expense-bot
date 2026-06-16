package com.expense.tracker.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.model.Category
import com.expense.tracker.data.repo.ExpenseRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

enum class CalendarMode { Week, Month, Year }

data class CalendarCell(
    val key: String,
    val label: String,
    val subLabel: String = "",
    val fromMillis: Long,
    val toMillis: Long,
    val totalAmount: Double,
    val count: Int,
    val inPrimaryRange: Boolean = true,
    val isToday: Boolean = false,
)

data class CalendarExpenseItem(
    val id: Long,
    val amount: Double,
    val categoryEmoji: String,
    val categoryName: String,
    val note: String,
    val timeLabel: String,
)

data class CalendarUiState(
    val mode: CalendarMode = CalendarMode.Month,
    val anchorDate: LocalDate = LocalDate.now(),
    val title: String = "",
    val cells: List<CalendarCell> = emptyList(),
    val selectedKey: String? = null,
    val selectedTitle: String = "",
    val selectedItems: List<CalendarExpenseItem> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val repo: ExpenseRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val todayProvider: () -> LocalDate = { LocalDate.now(zone) },
) : ViewModel() {

    private val trigger = MutableStateFlow(CalendarMode.Month to todayProvider())
    private val internal = MutableStateFlow(CalendarUiState(anchorDate = todayProvider()))
    val uiState: StateFlow<CalendarUiState> = internal.asStateFlow()

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    init {
        viewModelScope.launch {
            trigger.flatMapLatest { (mode, anchor) ->
                val range = visibleRange(mode, anchor)
                repo.observeInRange(range.first, range.second).map { list -> Triple(mode, anchor, list) }
            }.collect { (mode, anchor, list) ->
                internal.update { buildStateWithCache(mode, anchor, list, keepSelected = it.selectedKey) }
            }
        }
    }

    fun selectMode(mode: CalendarMode) {
        val (_, anchor) = trigger.value
        trigger.value = mode to anchor
    }

    fun previous() {
        val (mode, anchor) = trigger.value
        trigger.value = mode to when (mode) {
            CalendarMode.Week -> anchor.minusWeeks(1)
            CalendarMode.Month -> anchor.minusMonths(1)
            CalendarMode.Year -> anchor.minusYears(1)
        }
    }

    fun next() {
        val (mode, anchor) = trigger.value
        trigger.value = mode to when (mode) {
            CalendarMode.Week -> anchor.plusWeeks(1)
            CalendarMode.Month -> anchor.plusMonths(1)
            CalendarMode.Year -> anchor.plusYears(1)
        }
    }

    fun jumpToday() {
        trigger.value = trigger.value.first to todayProvider()
    }

    fun selectCell(key: String) {
        val state = internal.value
        val cell = state.cells.firstOrNull { it.key == key } ?: return
        internal.update {
            it.copy(
                selectedKey = key,
                selectedTitle = cellTitle(it.mode, cell),
                selectedItems = selectedItems(cell),
            )
        }
    }

    private fun buildState(
        mode: CalendarMode,
        anchor: LocalDate,
        list: List<ExpenseEntity>,
        keepSelected: String?,
    ): CalendarUiState {
        val cells = when (mode) {
            CalendarMode.Week -> weekCells(anchor, list)
            CalendarMode.Month -> monthCells(anchor, list)
            CalendarMode.Year -> yearCells(anchor, list)
        }
        val selected = keepSelected?.takeIf { key -> cells.any { it.key == key } }
            ?: cells.firstOrNull { it.isToday }?.key
            ?: cells.firstOrNull { it.count > 0 }?.key
        val selectedCell = selected?.let { key -> cells.first { it.key == key } }
        return CalendarUiState(
            mode = mode,
            anchorDate = anchor,
            title = title(mode, anchor),
            cells = cells,
            selectedKey = selected,
            selectedTitle = selectedCell?.let { cellTitle(mode, it) }.orEmpty(),
            selectedItems = selectedCell?.let(::selectedItems).orEmpty(),
        )
    }

    private fun weekCells(anchor: LocalDate, rows: List<ExpenseEntity>): List<CalendarCell> {
        val monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val labels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        return (0..6).map { i -> dayCell(monday.plusDays(i.toLong()), rows, labels[i]) }
    }

    private fun monthCells(anchor: LocalDate, rows: List<ExpenseEntity>): List<CalendarCell> {
        val first = anchor.withDayOfMonth(1)
        val gridStart = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val month = anchor.monthValue
        return (0 until 42).map { i ->
            val date = gridStart.plusDays(i.toLong())
            dayCell(date, rows, date.dayOfMonth.toString()).copy(inPrimaryRange = date.monthValue == month)
        }
    }

    private fun yearCells(anchor: LocalDate, rows: List<ExpenseEntity>): List<CalendarCell> {
        return (1..12).map { m ->
            val first = LocalDate.of(anchor.year, m, 1)
            val next = first.plusMonths(1)
            val from = first.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = next.atStartOfDay(zone).toInstant().toEpochMilli()
            val subset = rows.filter { it.occurredAt in from until to }
            CalendarCell(
                key = "${anchor.year}-$m",
                label = "${m}月",
                subLabel = "${subset.size}笔",
                fromMillis = from,
                toMillis = to,
                totalAmount = subset.sumOf { it.amount },
                count = subset.size,
                isToday = todayProvider().year == anchor.year && todayProvider().monthValue == m,
            )
        }
    }

    private fun dayCell(date: LocalDate, rows: List<ExpenseEntity>, label: String): CalendarCell {
        val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val subset = rows.filter { it.occurredAt in from until to }
        return CalendarCell(
            key = date.toString(),
            label = label,
            subLabel = if (subset.isEmpty()) "" else "${subset.size}笔",
            fromMillis = from,
            toMillis = to,
            totalAmount = subset.sumOf { it.amount },
            count = subset.size,
            isToday = date == todayProvider(),
        )
    }

    private fun visibleRange(mode: CalendarMode, anchor: LocalDate): Pair<Long, Long> {
        val fromDate = when (mode) {
            CalendarMode.Week -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            CalendarMode.Month -> anchor.withDayOfMonth(1)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            CalendarMode.Year -> LocalDate.of(anchor.year, 1, 1)
        }
        val toDate = when (mode) {
            CalendarMode.Week -> fromDate.plusWeeks(1)
            CalendarMode.Month -> fromDate.plusDays(42)
            CalendarMode.Year -> fromDate.plusYears(1)
        }
        return fromDate.atStartOfDay(zone).toInstant().toEpochMilli() to
            toDate.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    private fun title(mode: CalendarMode, anchor: LocalDate): String = when (mode) {
        CalendarMode.Week -> {
            val start = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val end = start.plusDays(6)
            "${start.monthValue}/${start.dayOfMonth} - ${end.monthValue}/${end.dayOfMonth}"
        }
        CalendarMode.Month -> "${anchor.year}年${anchor.monthValue}月"
        CalendarMode.Year -> "${anchor.year}年"
    }

    private fun cellTitle(mode: CalendarMode, cell: CalendarCell): String = when (mode) {
        CalendarMode.Year -> cell.label
        else -> cell.label + if (cell.subLabel.isNotBlank()) " · ${cell.subLabel}" else ""
    }

    private fun selectedItems(cell: CalendarCell): List<CalendarExpenseItem> {
        val currentRows = repoSnapshotCache
        return currentRows.filter { it.occurredAt in cell.fromMillis until cell.toMillis }
            .sortedByDescending { it.occurredAt }
            .map { e ->
                val cat = Category.byIdOrOther(e.categoryId)
                CalendarExpenseItem(
                    id = e.id,
                    amount = e.amount,
                    categoryEmoji = cat.emoji,
                    categoryName = cat.displayName,
                    note = e.note,
                    timeLabel = Instant.ofEpochMilli(e.occurredAt).atZone(zone).format(timeFmt),
                )
            }
    }

    private var repoSnapshotCache: List<ExpenseEntity> = emptyList()

    private fun buildStateWithCache(mode: CalendarMode, anchor: LocalDate, list: List<ExpenseEntity>, keepSelected: String?): CalendarUiState {
        repoSnapshotCache = list
        return buildState(mode, anchor, list, keepSelected)
    }
}
