package com.expense.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.expense.tracker.ui.analytics.AnalyticsScreen
import com.expense.tracker.ui.analytics.AnalyticsViewModel
import com.expense.tracker.ui.analytics.InsightsScreen
import com.expense.tracker.ui.billimport.BillImportScreen
import com.expense.tracker.ui.billimport.BillImportViewModel
import com.expense.tracker.ui.chat.ChatScreen
import com.expense.tracker.ui.chat.ChatViewModel
import com.expense.tracker.ui.history.HistoryScreen
import com.expense.tracker.ui.history.HistoryViewModel
import com.expense.tracker.ui.history.DeletedItemsScreen
import com.expense.tracker.ui.settings.DataExportScreen
import com.expense.tracker.ui.settings.SettingsMenuScreen
import com.expense.tracker.ui.settings.SettingsScreen
import com.expense.tracker.ui.settings.UserManualScreen
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

    private val billImportVm: BillImportViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                BillImportViewModel(
                    importHandler = container.billImportHandler,
                    expenseRepo = container.expenseRepo,
                    chatRepo = container.chatRepo,
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

                // 主屏幕切换 — Chat ↔ 子页面（Analytics / History / Settings）
                // Chat 是 "根"（rank=0），其他子页面 rank=1
                // 进入子页面（rank 增加）：新页面从右滑入，旧页面向左滑出
                // 返回 Chat（rank 减少）：新页面（Chat）从左滑入，旧页面向右滑出
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        val forward = targetState.rank > initialState.rank
                        val duration = 320
                        if (forward) {
                            (slideInHorizontally(
                                animationSpec = tween(duration, easing = FastOutSlowInEasing),
                                initialOffsetX = { it },
                            ) + fadeIn(animationSpec = tween(duration / 2))) togetherWith
                            (slideOutHorizontally(
                                animationSpec = tween(duration, easing = FastOutSlowInEasing),
                                targetOffsetX = { -it / 4 },
                            ) + fadeOut(animationSpec = tween(duration / 2)))
                        } else {
                            (slideInHorizontally(
                                animationSpec = tween(duration, easing = FastOutSlowInEasing),
                                initialOffsetX = { -it / 4 },
                            ) + fadeIn(animationSpec = tween(duration / 2))) togetherWith
                            (slideOutHorizontally(
                                animationSpec = tween(duration, easing = FastOutSlowInEasing),
                                targetOffsetX = { it },
                            ) + fadeOut(animationSpec = tween(duration / 2)))
                        }
                    },
                    label = "screen-transition",
                ) { current ->
                    when (current) {
                        Screen.Chat -> ChatScreen(
                            vm = chatVm,
                            onOpenAnalytics = { screen = Screen.Analytics },
                            onOpenHistory = { screen = Screen.History },
                            onOpenSettings = { screen = Screen.Settings },
                            onOpenBillImport = { subScreen = SubScreen.BillImport },
                        )
                        Screen.Analytics -> AnalyticsScreen(
                            vm = analyticsVm,
                            onBack = { screen = Screen.Chat },
                            onOpenInsights = { subScreen = SubScreen.Insights },
                        )
                        Screen.History -> HistoryScreen(
                            vm = historyVm,
                            onBack = { screen = Screen.Chat },
                        )
                        Screen.Settings -> SettingsMenuScreen(
                            onClose = { screen = Screen.Chat },
                            onOpenLlmSettings = { subScreen = SubScreen.LlmSettings },
                            onOpenDataExport = { subScreen = SubScreen.DataExport },
                            onOpenDeletedItems = { subScreen = SubScreen.DeletedItems },
                            onOpenUserManual = { subScreen = SubScreen.UserManual },
                        )
                    }
                }

                // LLM 设置 sub-screen 从右侧滑入覆盖
                AnimatedVisibility(
                    visible = subScreen == SubScreen.LlmSettings,
                    enter = slideInHorizontally(
                        animationSpec = tween(320, easing = FastOutSlowInEasing),
                        initialOffsetX = { it },
                    ) + fadeIn(animationSpec = tween(160)),
                    exit = slideOutHorizontally(
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                        targetOffsetX = { it },
                    ) + fadeOut(animationSpec = tween(140)),
                ) {
                    SettingsScreen(
                        prefs = container.userPrefs,
                        onClose = { subScreen = null },
                    )
                }

                // 数据导出 sub-screen 同样从右侧滑入
                AnimatedVisibility(
                    visible = subScreen == SubScreen.DataExport,
                    enter = slideInHorizontally(
                        animationSpec = tween(320, easing = FastOutSlowInEasing),
                        initialOffsetX = { it },
                    ) + fadeIn(animationSpec = tween(160)),
                    exit = slideOutHorizontally(
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                        targetOffsetX = { it },
                    ) + fadeOut(animationSpec = tween(140)),
                ) {
                    DataExportScreen(onClose = { subScreen = null })
                }

                // 最近删除 sub-screen 从右侧滑入
                AnimatedVisibility(
                    visible = subScreen == SubScreen.DeletedItems,
                    enter = slideInHorizontally(
                        animationSpec = tween(320, easing = FastOutSlowInEasing),
                        initialOffsetX = { it },
                    ) + fadeIn(animationSpec = tween(160)),
                    exit = slideOutHorizontally(
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                        targetOffsetX = { it },
                    ) + fadeOut(animationSpec = tween(140)),
                ) {
                    DeletedItemsScreen(
                        repo = container.expenseRepo,
                        onClose = { subScreen = null },
                    )
                }

                // 软件说明书 sub-screen 从右侧滑入
                AnimatedVisibility(
                    visible = subScreen == SubScreen.UserManual,
                    enter = slideInHorizontally(
                        animationSpec = tween(320, easing = FastOutSlowInEasing),
                        initialOffsetX = { it },
                    ) + fadeIn(animationSpec = tween(160)),
                    exit = slideOutHorizontally(
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                        targetOffsetX = { it },
                    ) + fadeOut(animationSpec = tween(140)),
                ) {
                    UserManualScreen(onClose = { subScreen = null })
                }

                // 智核分析 sub-screen 从右侧滑入
                AnimatedVisibility(
                    visible = subScreen == SubScreen.Insights,
                    enter = slideInHorizontally(
                        animationSpec = tween(320, easing = FastOutSlowInEasing),
                        initialOffsetX = { it },
                    ) + fadeIn(animationSpec = tween(160)),
                    exit = slideOutHorizontally(
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                        targetOffsetX = { it },
                    ) + fadeOut(animationSpec = tween(140)),
                ) {
                    InsightsScreen(
                        vm = analyticsVm,
                        llmPrefs = llmPrefs,
                        onBack = { subScreen = null },
                    )
                }

                // 截图记账 sub-screen 从右侧滑入
                AnimatedVisibility(
                    visible = subScreen == SubScreen.BillImport,
                    enter = slideInHorizontally(
                        animationSpec = tween(320, easing = FastOutSlowInEasing),
                        initialOffsetX = { it },
                    ) + fadeIn(animationSpec = tween(160)),
                    exit = slideOutHorizontally(
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                        targetOffsetX = { it },
                    ) + fadeOut(animationSpec = tween(140)),
                ) {
                    BillImportScreen(
                        vm = billImportVm,
                        ocrRecognizer = container.ocrRecognizer,
                        llmPrefs = llmPrefs,
                        onBack = { subScreen = null },
                        onDone = { subScreen = null },
                    )
                }
            }
        }
    }

    private sealed interface Screen {
        // rank 决定切换动画方向：高 rank 从右进，低 rank 从左进
        val rank: Int
        data object Chat : Screen { override val rank = 0 }
        data object Analytics : Screen { override val rank = 1 }
        data object History : Screen { override val rank = 1 }
        data object Settings : Screen { override val rank = 1 }
    }

    private sealed interface SubScreen {
        data object LlmSettings : SubScreen
        data object DataExport : SubScreen
        data object DeletedItems : SubScreen
        data object UserManual : SubScreen
        data object Insights : SubScreen
        data object BillImport : SubScreen
    }
}
