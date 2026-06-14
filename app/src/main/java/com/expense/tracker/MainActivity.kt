package com.expense.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.expense.tracker.ui.analytics.AnalyticsScreen
import com.expense.tracker.ui.analytics.AnalyticsViewModel
import com.expense.tracker.ui.chat.ChatScreen
import com.expense.tracker.ui.chat.ChatViewModel
import com.expense.tracker.ui.history.HistoryScreen
import com.expense.tracker.ui.history.HistoryViewModel
import com.expense.tracker.ui.settings.SettingsMenuScreen
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

    private val analyticsVm: AnalyticsViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AnalyticsViewModel(
                    repo = container.expenseRepo,
                    onRequestInsights = container.analyticsAnalyzer,
                ) as T
        }
    }

    private val historyVm: HistoryViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HistoryViewModel(repo = container.expenseRepo) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Chat) }
                var subScreen by remember { mutableStateOf<SubScreen?>(null) }
                val llmPrefs by container.userPrefs.snapshot.collectAsState(initial = null)

                // 系统返回键：非 Chat 时回到 Chat，而不是退出 App
                BackHandler(enabled = screen != Screen.Chat || subScreen != null) {
                    if (subScreen != null) subScreen = null
                    else screen = Screen.Chat
                }

                // LLM 设置 sub-screen 覆盖在最上层
                if (subScreen == SubScreen.LlmSettings) {
                    SettingsScreen(
                        prefs = container.userPrefs,
                        onClose = { subScreen = null },
                    )
                    return@AppTheme
                }

                when (screen) {
                    Screen.Chat -> ChatScreen(
                        vm = chatVm,
                        onOpenAnalytics = { screen = Screen.Analytics },
                        onOpenHistory = { screen = Screen.History },
                        onOpenSettings = { screen = Screen.Settings },
                    )
                    Screen.Analytics -> AnalyticsScreen(
                        vm = analyticsVm,
                        onBack = { screen = Screen.Chat },
                        llmPrefs = llmPrefs,
                    )
                    Screen.History -> HistoryScreen(
                        vm = historyVm,
                        onBack = { screen = Screen.Chat },
                    )
                    Screen.Settings -> SettingsMenuScreen(
                        onClose = { screen = Screen.Chat },
                        onOpenLlmSettings = { subScreen = SubScreen.LlmSettings },
                    )
                }
            }
        }
    }

    private sealed interface Screen {
        data object Chat : Screen
        data object Analytics : Screen
        data object History : Screen
        data object Settings : Screen
    }

    private sealed interface SubScreen {
        data object LlmSettings : SubScreen
    }
}
