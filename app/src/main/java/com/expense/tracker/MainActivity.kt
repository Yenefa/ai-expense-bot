package com.expense.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.expense.tracker.ui.trash.TrashScreen
import com.expense.tracker.ui.trash.TrashViewModel

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

    private val trashVm: TrashViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TrashViewModel(repo = container.expenseRepo) as T
        }
    }

    /**
     * 路由架构（v1.2.1）：
     *
     * 早期方案用 AnimatedContent 在 Chat ↔ 子页之间整页切换，问题是切换 320ms 内
     * 新旧两个全屏树同时存活，每个都带 softShadow / LazyColumn / 手势检测器，
     * 中低端机 GPU 撑不住两份离屏合成 + 平移，返回时掉帧明显。
     *
     * 改为：Chat 是**始终存在的根**，所有子页（Analytics / History / Settings / LlmSettings）
     * 都是 AnimatedVisibility 的浮起覆盖层。返回时只销毁子页一层，Chat 完全不参与动画，
     * 流畅度等同于 iOS pop。代价：失去 Chat "从左滑入"的视觉效果，但更接近 iOS 标准。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                var screen by remember { mutableStateOf<Subscreen?>(null) }
                var llmSettingsOpen by remember { mutableStateOf(false) }
                var trashOpen by remember { mutableStateOf(false) }
                val llmPrefs by container.userPrefs.snapshot.collectAsState(initial = null)

                // 系统返回键：层层退出 — 最深的二级页先关
                BackHandler(enabled = screen != null || llmSettingsOpen || trashOpen) {
                    when {
                        llmSettingsOpen -> llmSettingsOpen = false
                        trashOpen       -> trashOpen = false
                        screen != null  -> screen = null
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // 根：Chat 永远存在
                    ChatScreen(
                        vm = chatVm,
                        onOpenAnalytics = { screen = Subscreen.Analytics },
                        onOpenHistory   = { screen = Subscreen.History },
                        onOpenSettings  = { screen = Subscreen.Settings },
                    )

                    // 覆盖层 — Analytics
                    AnimatedVisibility(
                        visible = screen == Subscreen.Analytics,
                        enter = subscreenEnter(),
                        exit  = subscreenExit(),
                    ) {
                        AnalyticsScreen(
                            vm = analyticsVm,
                            onBack = { screen = null },
                            llmPrefs = llmPrefs,
                        )
                    }

                    // 覆盖层 — History
                    AnimatedVisibility(
                        visible = screen == Subscreen.History,
                        enter = subscreenEnter(),
                        exit  = subscreenExit(),
                    ) {
                        HistoryScreen(
                            vm = historyVm,
                            onBack = { screen = null },
                        )
                    }

                    // 覆盖层 — Settings 菜单
                    AnimatedVisibility(
                        visible = screen == Subscreen.Settings,
                        enter = subscreenEnter(),
                        exit  = subscreenExit(),
                    ) {
                        SettingsMenuScreen(
                            onClose = { screen = null },
                            onOpenLlmSettings = { llmSettingsOpen = true },
                            onOpenTrash = { trashOpen = true },
                        )
                    }

                    // 覆盖层 — LLM 设置（嵌在 Settings 之上的二级页）
                    AnimatedVisibility(
                        visible = llmSettingsOpen,
                        enter = subscreenEnter(),
                        exit  = subscreenExit(),
                    ) {
                        SettingsScreen(
                            prefs = container.userPrefs,
                            onClose = { llmSettingsOpen = false },
                        )
                    }

                    // 覆盖层 — 最近删除（嵌在 Settings 之上的二级页）
                    AnimatedVisibility(
                        visible = trashOpen,
                        enter = subscreenEnter(),
                        exit  = subscreenExit(),
                    ) {
                        TrashScreen(
                            vm = trashVm,
                            onBack = { trashOpen = false },
                        )
                    }
                }
            }
        }
    }

    private sealed interface Subscreen {
        data object Analytics : Subscreen
        data object History   : Subscreen
        data object Settings  : Subscreen
    }
}

// 子页统一从右滑入，从右滑出 — iOS push/pop 风格
private fun subscreenEnter() = slideInHorizontally(
    animationSpec = tween(280, easing = FastOutSlowInEasing),
    initialOffsetX = { it },
) + fadeIn(animationSpec = tween(140))

private fun subscreenExit() = slideOutHorizontally(
    animationSpec = tween(240, easing = FastOutSlowInEasing),
    targetOffsetX = { it },
) + fadeOut(animationSpec = tween(120))
