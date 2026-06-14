package com.expense.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.expense.tracker.ui.chat.ChatScreen
import com.expense.tracker.ui.chat.ChatViewModel
import com.expense.tracker.ui.chat.LlmResult
import com.expense.tracker.ui.theme.AppTheme

class MainActivity : ComponentActivity() {

    private val chatVm: ChatViewModel by viewModels {
        val container = (application as ExpenseApp).container
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(
                expenseRepo = container.expenseRepo,
                chatRepo = container.chatRepo,
                userPrefs = container.userPrefs,
                llmHandler = { _: String, _: UserPrefsSnapshot ->
                    LlmResult.Error("LLM 客户端尚未接入，请关闭 🧠 用模板模式")
                },
            ) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                ChatScreen(vm = chatVm, onOpenAnalytics = { /* Part 3 实现 */ })
            }
        }
    }
}
