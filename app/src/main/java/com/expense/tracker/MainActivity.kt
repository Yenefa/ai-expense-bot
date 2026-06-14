package com.expense.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.expense.tracker.ui.chat.ChatScreen
import com.expense.tracker.ui.chat.ChatViewModel
import com.expense.tracker.ui.settings.SettingsScreen
import com.expense.tracker.ui.theme.AppTheme

class MainActivity : ComponentActivity() {

    private val container by lazy { (application as ExpenseApp).container }

    private val chatVm: ChatViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(
                expenseRepo = container.expenseRepo,
                chatRepo = container.chatRepo,
                userPrefs = container.userPrefs,
                llmHandler = container.llmHandler,
            ) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Chat) }
                when (screen) {
                    Screen.Chat -> ChatScreen(
                        vm = chatVm,
                        onOpenAnalytics = { screen = Screen.Settings }, // 暂用菜单跳到 settings；Task 22 改成 drawer
                    )
                    Screen.Settings -> SettingsScreen(
                        prefs = container.userPrefs,
                        onClose = { screen = Screen.Chat },
                    )
                }
            }
        }
    }

    private sealed interface Screen {
        data object Chat : Screen
        data object Settings : Screen
    }
}
