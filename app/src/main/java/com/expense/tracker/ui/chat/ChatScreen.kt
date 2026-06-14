package com.expense.tracker.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.expense.tracker.ui.dock.InteractiveDock
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
    Box(modifier = modifier.fillMaxSize().background(AppColors.Bg)) {
        Column(Modifier.fillMaxSize()) {
            TopBar()
            MessageList(messages = state.messages, modifier = Modifier.weight(1f))
            AnimatedVisibility(
                visible = !state.llmEnabled,
                enter = fadeIn(animationSpec = tween(250)) +
                        expandVertically(
                            animationSpec = tween(300, easing = FastOutSlowInEasing),
                            expandFrom = Alignment.Top,
                        ) +
                        slideInVertically(
                            animationSpec = tween(300, easing = FastOutSlowInEasing),
                            initialOffsetY = { -it / 3 },
                        ),
                exit = fadeOut(animationSpec = tween(200)) +
                        shrinkVertically(
                            animationSpec = tween(250, easing = FastOutSlowInEasing),
                            shrinkTowards = Alignment.Top,
                        ) +
                        slideOutVertically(
                            animationSpec = tween(250, easing = FastOutSlowInEasing),
                            targetOffsetY = { -it / 3 },
                        ),
            ) {
                TemplateCard(
                    selectedCategoryId = state.selectedCategoryId,
                    onSelectCategory = vm::selectCategory,
                    onSubmit = vm::submitTemplate,
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
    }
}
