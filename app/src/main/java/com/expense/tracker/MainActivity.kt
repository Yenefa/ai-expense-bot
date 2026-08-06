package com.expense.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.expense.tracker.data.prefs.ThemeMode
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
import com.expense.tracker.ui.splash.BrandSplashScreen
import com.expense.tracker.ui.subscription.SubscriptionScreen
import com.expense.tracker.ui.subscription.SubscriptionViewModel
import com.expense.tracker.data.subscription.SubscriptionApiClient
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.AppTheme
import kotlinx.coroutines.delay

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
                confirmationHandler = container.llmConfirmationHandler,
                cancellationHandler = container.llmCancellationHandler,
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

    private val subscriptionVm: SubscriptionViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val apiClient = SubscriptionApiClient(BuildConfig.SUBSCRIPTION_API_BASE_URL)
                return SubscriptionViewModel(
                    prefs = container.subscriptionPrefs,
                    redeemer = { code, installationId ->
                        apiClient.redeem(code, installationId)
                    },
                    statusChecker = apiClient::status,
                    healthChecker = apiClient::health,
                    onActivated = { container.userPrefs.setLlmEnabled(true) },
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            var showBrandSplash by remember { mutableStateOf(savedInstanceState == null) }
            val splashAlpha = remember { Animatable(1f) }
            LaunchedEffect(showBrandSplash) {
                if (showBrandSplash) {
                    delay(BRAND_SPLASH_DURATION_MS)
                    splashAlpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(220),
                    )
                    showBrandSplash = false
                }
            }
            val prefs by container.userPrefs.snapshot.collectAsState(initial = null)
            val isDark = when (prefs?.themeMode ?: ThemeMode.SYSTEM) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            AppTheme(darkTheme = isDark) {
                if (showBrandSplash) {
                    BrandSplashScreen(
                        Modifier.graphicsLayer { alpha = splashAlpha.value },
                    )
                } else {
                    var screen by rememberSaveable { mutableStateOf(Screen.Chat) }
                    var subScreen by rememberSaveable { mutableStateOf<SubScreen?>(null) }

                // 系统返回键：非 Chat 时回到 Chat，而不是退出 App
                BackHandler(enabled = screen != Screen.Chat || subScreen != null) {
                    if (subScreen != null) subScreen = null
                    else screen = Screen.Chat
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppColors.Bg),
                ) {
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
                                onOpenBillImport = {
                                    billImportVm.reset()
                                    subScreen = SubScreen.BillImport
                                },
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
                                onOpenSubscription = { subScreen = SubScreen.Subscription },
                                onOpenLlmSettings = { subScreen = SubScreen.LlmSettings },
                                onOpenDataExport = { subScreen = SubScreen.DataExport },
                                onOpenDeletedItems = { subScreen = SubScreen.DeletedItems },
                                onOpenUserManual = { subScreen = SubScreen.UserManual },
                                prefs = container.userPrefs,
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = subScreen == SubScreen.Subscription,
                        enter = slideInHorizontally(
                            animationSpec = tween(320, easing = FastOutSlowInEasing),
                            initialOffsetX = { it },
                        ) + fadeIn(animationSpec = tween(160)),
                        exit = slideOutHorizontally(
                            animationSpec = tween(280, easing = FastOutSlowInEasing),
                            targetOffsetX = { it },
                        ) + fadeOut(animationSpec = tween(140)),
                    ) {
                        SubscriptionScreen(
                            vm = subscriptionVm,
                            onBack = { subScreen = null },
                        )
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
                            llmPrefs = prefs,
                            onBack = { subScreen = null },
                        )
                    }

                    // 账单截图 OCR 导入 sub-screen
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
                            llmPrefs = prefs,
                            onBack = { subScreen = null },
                        )
                    }

                }
                }
            }
        }
    }

    private enum class Screen(val rank: Int) {
        // rank 决定切换动画方向：高 rank 从右进，低 rank 从左进
        Chat(0),
        Analytics(1),
        History(1),
        Settings(1),
    }

    private enum class SubScreen {
        Subscription,
        LlmSettings,
        DataExport,
        DeletedItems,
        UserManual,
        Insights,
        BillImport,
    }
}

private const val BRAND_SPLASH_DURATION_MS = 850L
