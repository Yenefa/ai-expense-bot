package com.expense.tracker.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expense.tracker.data.db.RecurringPeriodType
import com.expense.tracker.data.db.RecurringRuleDao
import com.expense.tracker.data.db.RecurringRuleEntity
import com.expense.tracker.data.recurring.RecurringGenerator
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecurringUiState(
    val rules: List<RecurringRuleEntity> = emptyList(),
    val saved: Boolean = false,
    val savedMessage: String = "",
)

class RecurringViewModel(
    private val dao: RecurringRuleDao,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val internal = MutableStateFlow(RecurringUiState())
    val uiState: StateFlow<RecurringUiState> = internal.asStateFlow()

    init {
        viewModelScope.launch {
            dao.observeAll().collect { rules ->
                internal.update { it.copy(rules = rules) }
            }
        }
    }

    fun add(
        amountText: String,
        categoryId: String,
        note: String,
        periodType: RecurringPeriodType,
        dayOfMonth: Int,
        dayOfWeek: Int,
        monthOfYear: Int,
    ) {
        val cents = runCatching { com.expense.tracker.data.model.Money.parseYuanToCents(amountText.trim()) }
            .getOrNull() ?: return
        if (cents <= 0L) return
        val now = System.currentTimeMillis()
        val baseRule = RecurringRuleEntity(
            amountCents = cents,
            categoryId = categoryId,
            note = note.trim(),
            periodType = periodType.name,
            dayOfMonth = dayOfMonth,
            dayOfWeek = dayOfWeek,
            monthOfYear = monthOfYear,
            nextDueAt = now,
            createdAt = now,
        )
        viewModelScope.launch {
            // 首次到期点 = 从规则字段推导的、今天之后的第一个周期点（基准为今天零点，不再拿“昨天”当基准）
            val today = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
            val firstDue = RecurringGenerator.nextDueAfter(baseRule, today, today, zone)
            dao.insert(baseRule.copy(nextDueAt = firstDue))
            internal.update { it.copy(saved = true, savedMessage = "周期账单已添加") }
        }
    }

    fun toggle(rule: RecurringRuleEntity) {
        viewModelScope.launch { dao.update(rule.copy(enabled = !rule.enabled)) }
    }

    fun delete(rule: RecurringRuleEntity) {
        viewModelScope.launch {
            dao.delete(rule)
            internal.update { it.copy(saved = true, savedMessage = "已删除周期账单") }
        }
    }

    fun dismissSaved() {
        internal.update { it.copy(saved = false) }
    }
}
