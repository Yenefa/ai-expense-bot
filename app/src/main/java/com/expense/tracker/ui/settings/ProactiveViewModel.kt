package com.expense.tracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expense.tracker.data.prefs.ProactivePrefs
import com.expense.tracker.proactive.ProactiveAlertType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProactiveViewModel(
    private val prefs: ProactivePrefs,
) : ViewModel() {

    val enabled: StateFlow<Set<ProactiveAlertType>> = prefs.enabledTypes
        .stateIn(viewModelScope, SharingStarted.Eagerly, ProactiveAlertType.entries.toSet())

    fun toggle(type: ProactiveAlertType, enabled: Boolean) {
        viewModelScope.launch { prefs.setEnabled(type, enabled) }
    }
}
