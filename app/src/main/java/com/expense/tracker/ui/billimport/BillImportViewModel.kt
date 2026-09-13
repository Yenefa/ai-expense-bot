package com.expense.tracker.ui.billimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expense.tracker.data.model.Money
import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.expense.tracker.data.repo.ChatRepository
import com.expense.tracker.data.repo.ExpenseDraft
import com.expense.tracker.data.repo.ExpenseRepository
import com.expense.tracker.llm.BillImportResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BillImportPhase { Idle, Loading, Result, Error, Done }

data class EditableExpense(
    val amountCents: Long,
    val categoryId: String,
    val note: String,
    val occurredAtMillis: Long?,
    val selected: Boolean = true,
)

data class BillImportUiState(
    val phase: BillImportPhase = BillImportPhase.Idle,
    val items: List<EditableExpense> = emptyList(),
    val errorMessage: String? = null,
    val doneCount: Int = 0,
    val doneTotalCents: Long = 0L,
)

class BillImportViewModel(
    private val importHandler: suspend (String, UserPrefsSnapshot) -> BillImportResult,
    private val expenseRepo: ExpenseRepository,
    private val chatRepo: ChatRepository,
) : ViewModel() {
    private val internal = MutableStateFlow(BillImportUiState())
    val uiState: StateFlow<BillImportUiState> = internal.asStateFlow()

    fun importFromText(ocrText: String, prefs: UserPrefsSnapshot) {
        internal.value = BillImportUiState(phase = BillImportPhase.Loading)
        viewModelScope.launch {
            // 双保险：即使注入的 handler 违反约定抛出异常（如 AI 服务未配置），
            // 也只把状态切到 Error，不让异常逃出 viewModelScope。
            val result = runCatching { importHandler(ocrText, prefs) }
                .getOrElse { error ->
                    if (error is CancellationException) throw error
                    BillImportResult.Error("导入失败：${error.message ?: "未知错误"}")
                }
            when (result) {
                is BillImportResult.Ok -> {
                    val items = result.expenses.mapNotNull {
                        val amountCents = it.amountCents.takeIf { cents -> cents > 0L }
                            ?: return@mapNotNull null
                        EditableExpense(
                            amountCents = amountCents,
                            categoryId = it.categoryId,
                            note = it.note,
                            occurredAtMillis = it.occurredAtMillis,
                        )
                    }
                    if (items.isEmpty()) showError("没有从截图中识别到可导入的交易")
                    else internal.value = BillImportUiState(
                            phase = BillImportPhase.Result,
                            items = items,
                        )
                }
                is BillImportResult.Error -> showError(result.message)
            }
        }
    }

    fun showError(message: String) {
        internal.update { it.copy(phase = BillImportPhase.Error, errorMessage = message) }
    }

    fun toggleSelected(index: Int) = updateItem(index) { it.copy(selected = !it.selected) }
    fun updateAmount(index: Int, amountCents: Long) = updateItem(index) { it.copy(amountCents = amountCents) }
    fun updateCategory(index: Int, categoryId: String) = updateItem(index) { it.copy(categoryId = categoryId) }
    fun updateNote(index: Int, note: String) = updateItem(index) { it.copy(note = note) }

    fun reset() {
        internal.value = BillImportUiState()
    }

    fun confirmImport() {
        val state = internal.value
        if (state.phase != BillImportPhase.Result) return
        val selected = state.items.filter { it.selected && it.amountCents > 0L }
        if (selected.isEmpty()) {
            showError("请至少选择一笔有效交易")
            return
        }
        val loading = state.copy(phase = BillImportPhase.Loading, errorMessage = null)
        if (!internal.compareAndSet(state, loading)) return
        viewModelScope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                expenseRepo.addAllCents(selected.map { item ->
                    ExpenseDraft(
                        amountCents = item.amountCents,
                        categoryId = item.categoryId,
                        note = item.note,
                        occurredAt = item.occurredAtMillis ?: now,
                    )
                })
                val totalCents = selected.sumOf { it.amountCents }
                runCatching { chatRepo.appendAssistant(
                    "📸 已从截图导入 ${selected.size} 笔，合计 ¥${Money.formatYuan(totalCents)}",
                ) }
                internal.value = BillImportUiState(
                    phase = BillImportPhase.Done,
                    doneCount = selected.size,
                    doneTotalCents = totalCents,
                )
            }.onFailure { showError("导入失败：${it.message ?: "无法写入账目"}") }
        }
    }

    private fun updateItem(index: Int, transform: (EditableExpense) -> EditableExpense) {
        internal.update { state ->
            state.copy(items = state.items.mapIndexed { i, item -> if (i == index) transform(item) else item })
        }
    }
}
