package com.expense.tracker.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expense.tracker.data.budget.BudgetPrefs
import com.expense.tracker.data.budget.BudgetSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BudgetUiState(
    val snapshot: BudgetSnapshot = BudgetSnapshot(),
    val saved: Boolean = false,
    val savedMessage: String = "",
)

class BudgetViewModel(
    private val prefs: BudgetPrefs,
) : ViewModel() {
    private val internal = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = internal.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.snapshot.collect { snapshot ->
                internal.update { it.copy(snapshot = snapshot) }
            }
        }
    }

    /** 金额输入为空白 = 清除该预算。 */
    fun save(
        monthlyText: String,
        categoryTexts: Map<String, String>,
    ) {
        val monthly = parseYuan(monthlyText) ?: 0L
        val categories = categoryTexts
            .mapNotNull { (categoryId, text) ->
                parseYuan(text)?.let { categoryId to it }
            }
            .toMap()
        viewModelScope.launch {
            prefs.save(monthly, categories)
            internal.update {
                it.copy(
                    saved = true,
                    savedMessage = if (monthly > 0L || categories.isNotEmpty()) {
                        "预算已保存"
                    } else {
                        "预算已清除"
                    },
                )
            }
        }
    }

    fun dismissSaved() {
        internal.update { it.copy(saved = false) }
    }

    private fun parseYuan(text: String): Long? {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return 0L
        val cents = runCatching {
            com.expense.tracker.data.model.Money.parseYuanToCents(cleaned)
        }.getOrNull()
        return cents?.takeIf { it >= 0L }
    }
}
