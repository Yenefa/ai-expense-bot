package com.expense.tracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expense.tracker.memory.MemoryFact
import com.expense.tracker.memory.MemoryGovernor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Memory 管理：查看 / 修改 / 删除 / 清空（用户行为，不需要提案确认门）。 */
class MemoryViewModel(
    private val governor: MemoryGovernor,
) : ViewModel() {

    val facts: StateFlow<List<MemoryFact>> = governor.facts
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 用户管理：修改单条；返回是否保存成功，UI 据此决定是否关闭弹窗。 */
    suspend fun update(fact: MemoryFact): Boolean = governor.update(fact)

    fun delete(id: String) {
        viewModelScope.launch { governor.delete(id) }
    }

    fun clearAll() {
        viewModelScope.launch { governor.clear() }
    }
}
