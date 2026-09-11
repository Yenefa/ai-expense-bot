package com.expense.tracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expense.tracker.data.prefs.ProactivePrefs
import com.expense.tracker.proactive.ProactiveAlertRecord
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 提醒中心：只读历史（新→旧）+ 清空；不触发任何规则评估或投递。 */
class ProactiveCenterViewModel(
    private val prefs: ProactivePrefs,
) : ViewModel() {

    val history: StateFlow<List<ProactiveAlertRecord>> = prefs.historyFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 未读数：设置页角标与提醒中心共用。 */
    val unreadCount: StateFlow<Int> = prefs.unreadCount
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun clear() {
        viewModelScope.launch { prefs.clearHistory() }
    }

    /** 进入提醒中心时调用：写入已读水位，未读数归零。 */
    fun markRead() {
        viewModelScope.launch { prefs.markHistoryRead() }
    }
}
