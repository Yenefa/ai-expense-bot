package com.expense.tracker.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.expense.tracker.data.model.Category
import com.expense.tracker.ui.dock.InteractiveDock
import com.expense.tracker.ui.template.CategoryBubbleDialog
import com.expense.tracker.ui.template.TemplateCard
import com.expense.tracker.ui.theme.AppColors

@Composable
fun ChatScreen(
    vm: ChatViewModel,
    onOpenAnalytics: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.uiState.collectAsState()
    // 双击分类弹气泡的状态：null = 不显示
    var bubbleCategoryId by remember { mutableStateOf<String?>(null) }

    // 气泡打开时拦截系统返回键，只关气泡，绝不退出 App
    BackHandler(enabled = bubbleCategoryId != null) {
        bubbleCategoryId = null
    }

    Box(modifier = modifier.fillMaxSize().background(AppColors.Bg)) {
        Column(Modifier.fillMaxSize()) {
            TopBar()
            MessageList(
                messages = state.messages,
                thinking = state.thinking,
                streamingText = state.streamingText,
                pendingActions = state.pendingActions,
                onConfirmDelete = vm::confirmDelete,
                onConfirmUpdate = vm::confirmUpdate,
                onDismissAction = vm::dismissAction,
                modifier = Modifier.weight(1f),
            )
            AnimatedVisibility(
                visible = !state.llmEnabled,
                enter = fadeIn(animationSpec = tween(350)) +
                        expandVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessLow,
                            ),
                            expandFrom = Alignment.Top,
                        ) +
                        slideInVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessLow,
                            ),
                            initialOffsetY = { -it / 4 },
                        ) +
                        scaleIn(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessLow,
                            ),
                            initialScale = 0.92f,
                        ),
                exit = fadeOut(animationSpec = tween(200)) +
                        shrinkVertically(
                            animationSpec = tween(250),
                            shrinkTowards = Alignment.Top,
                        ) +
                        slideOutVertically(
                            animationSpec = tween(250),
                            targetOffsetY = { -it / 4 },
                        ) +
                        scaleOut(
                            animationSpec = tween(200),
                            targetScale = 0.95f,
                        ),
            ) {
                TemplateCard(
                    selectedCategoryId = state.selectedCategoryId,
                    onSelectCategory = vm::selectCategory,
                    onSubmit = vm::submitTemplate,
                    onDoubleClickCategory = { id ->
                        vm.selectCategory(id)
                        bubbleCategoryId = id
                    },
                )
            }
            InputBar(
                llmEnabled = state.llmEnabled,
                onToggleLlm = vm::toggleLlm,
                onSend = vm::submitFreeText,
                onPlusClick = { /* TODO: image upload */ },
                placeholder = if (state.llmEnabled) "随便说什么..." else "回复记账助手",
            )
            InteractiveDock(
                onAnalytics = onOpenAnalytics,
                onHistory = onOpenHistory,
                onSettings = onOpenSettings,
            )
        }

        // 气泡 Dialog 浮在最上层（fillMaxSize 内的 Box 顶层）
        CategoryBubbleDialog(
            visible = bubbleCategoryId != null,
            category = bubbleCategoryId?.let { Category.byId(it) },
            onDismiss = { bubbleCategoryId = null },
            onSwitchCategory = { id ->
                vm.selectCategory(id)
                bubbleCategoryId = id
            },
            onSubmit = { amount, note ->
                vm.submitTemplate(amount, note)
                bubbleCategoryId = null
            },
        )
    }
}
