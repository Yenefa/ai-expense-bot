package com.expense.tracker.ui.billimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.expense.tracker.data.repo.ChatRepository
import com.expense.tracker.data.repo.ExpenseRepository
import com.expense.tracker.llm.BillImportResult
import com.expense.tracker.llm.ParsedExpense
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BillImportPhase { Idle, Loading, Result, Error, Done }

data class EditableExpense(
    val amount: Double,
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
    val doneTotal: Double = 0.0,
)

class BillImportViewModel(
    private val importHandler: suspend (String, UserPrefsSnapshot) -> BillImportResult,
    private val expenseRepo: ExpenseRepository,
    private val chatRepo: ChatRepository,
) : ViewModel() {

    private val internal = MutableStateFlow(BillImportUiState())
    val uiState: StateFlow<BillImportUiState> = internal.asStateFlow()

    fun importFromText(ocrText: String, prefs: UserPrefsSnapshot) {
        internal.update { it.copy(phase = BillImportPhase.Loading, items = emptyList(), errorMessage = null) }
        viewModelScope.launch {
            when (val r = importHandler(ocrText, prefs)) {
                is BillImportResult.Ok -> {
                    val items = r.expenses.map { it.toEditable() }
                    internal.update { it.copy(phase = BillImportPhase.Result, items = items) }
                }
                is BillImportResult.Error -> internal.update {
                    it.copy(phase = BillImportPhase.Error, errorMessage = r.message)
                }
            }
        }
    }

    fun toggleSelected(index: Int) = internal.update { st ->
        st.copy(items = st.items.mapIndexed { i, e -> if (i == index) e.copy(selected = !e.selected) else e })
    }

    fun updateAmount(index: Int, amount: Double) = internal.update { st ->
        st.copy(items = st.items.mapIndexed { i, e -> if (i == index) e.copy(amount = amount) else e })
    }

    fun updateCategory(index: Int, categoryId: String) = internal.update { st ->
        st.copy(items = st.items.mapIndexed { i, e -> if (i == index) e.copy(categoryId = categoryId) else e })
    }

    fun updateNote(index: Int, note: String) = internal.update { st ->
        st.copy(items = st.items.mapIndexed { i, e -> if (i == index) e.copy(note = note) else e })
    }

    /** 重置为 Idle，供重新进入页面时清掉上次的 Done/Result 状态。 */
    fun reset() {
        internal.update { BillImportUiState() }
    }

    fun confirmImport() {
        val st = internal.value
        if (st.phase != BillImportPhase.Result) return
        val selected = st.items.filter { it.selected }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            selected.forEach { e ->
                expenseRepo.add(e.amount, e.categoryId, e.note, e.occurredAtMillis ?: now)
            }
            val total = selected.sumOf { it.amount }
            chatRepo.appendAssistant("📸 已从截图导入 ${selected.size} 笔，合计 ¥${"%.2f".format(total)}")
            internal.update { it.copy(phase = BillImportPhase.Done, doneCount = selected.size, doneTotal = total) }
        }
    }

    private fun ParsedExpense.toEditable() = EditableExpense(amount, categoryId, note, occurredAtMillis)
}
