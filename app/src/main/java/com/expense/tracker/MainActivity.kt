package com.expense.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.launch

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

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Chat) }
                var subScreen by remember { mutableStateOf<SubScreen?>(null) }
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val llmPrefs by container.userPrefs.snapshot.collectAsState(initial = null)

                // 系统返回键：非 Chat 时返回 Chat 而不是退出 App
                BackHandler(enabled = screen != Screen.Chat || subScreen != null) {
                    if (subScreen != null) {
                        subScreen = null
                    } else {
                        screen = Screen.Chat
                    }
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            Text("菜单", modifier = Modifier.padding(16.dp))
                            NavigationDrawerItem(
                                label = { Text("支出分析") },
                                selected = screen == Screen.Analytics,
                                onClick = {
                                    screen = Screen.Analytics
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }
                    },
                ) {
                    when {
                        subScreen == SubScreen.LlmSettings -> SettingsScreen(
                            prefs = container.userPrefs,
                            onClose = { subScreen = null },
                        )
                        screen == Screen.Chat -> ChatScreen(
                            vm = chatVm,
                            onOpenAnalytics = { scope.launch { drawerState.open() } },
                            onOpenHistory = { screen = Screen.History },
                            onOpenSettings = { screen = Screen.Settings },
                        )
                        screen == Screen.Analytics -> AnalyticsScreen(
                            vm = analyticsVm,
                            onBack = { screen = Screen.Chat },
                            llmPrefs = llmPrefs,
                        )
                        screen == Screen.History -> HistoryScreen(
                            vm = historyVm,
                            onBack = { screen = Screen.Chat },
                        )
                        screen == Screen.Settings -> SettingsMenuScreen(
                            onClose = { screen = Screen.Chat },
                            onOpenLlmSettings = { subScreen = SubScreen.LlmSettings },
                        )
                    }
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
