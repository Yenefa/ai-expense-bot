package com.expense.tracker.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.expense.tracker.data.db.ChatMessageEntity
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
    onOpenBillImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    // 双击分类弹气泡的状态：null = 不显示
    var bubbleCategoryId by remember { mutableStateOf<String?>(null) }
    // 长按消息时弹出复制 sheet
    var actionSheetTarget by remember { mutableStateOf<ChatMessageEntity?>(null) }

    // 任意弹窗打开时拦截系统返回键，只关弹窗，不退出 App
    BackHandler(
        enabled = bubbleCategoryId != null || actionSheetTarget != null || state.pendingConfirmation != null,
    ) {
        when {
            state.pendingConfirmation != null -> vm.cancelPending()
            actionSheetTarget != null -> actionSheetTarget = null
            else -> bubbleCategoryId = null
        }
    }

    Box(modifier = modifier.fillMaxSize().background(AppColors.Bg)) {
        Column(Modifier.fillMaxSize()) {
            TopBar()
            MessageList(
                messages = state.messages,
                thinking = state.thinking,
                streamingText = state.streamingText,
                onLongPressMessage = { msg -> actionSheetTarget = msg },
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
                text = state.inputDraft,
                onTextChange = vm::updateInputDraft,
                llmEnabled = state.llmEnabled,
                onToggleLlm = vm::toggleLlm,
                onSend = vm::submitFreeText,
                onPlusClick = onOpenBillImport,
                placeholder = if (state.llmEnabled) "随便说什么..." else "回复 Y.E cost",
                enabled = !state.sending && state.pendingConfirmation == null,
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

        // 长按消息：弹出"复制"sheet（v2.7 起去掉编辑 — 真正的数据编辑在历史明细页）
        MessageActionSheet(
            message = actionSheetTarget,
            onDismiss = { actionSheetTarget = null },
            onCopy = {
                val msg = actionSheetTarget ?: return@MessageActionSheet
                copyToClipboard(context, msg.content)
                actionSheetTarget = null
            },
        )

        state.pendingConfirmation?.let { confirmation ->
            AlertDialog(
                onDismissRequest = vm::cancelPending,
                title = { Text(confirmation.preview.title) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(confirmation.preview.detail)
                        confirmation.preview.changeDetails.forEach { detail ->
                            Text("• $detail")
                        }
                        Text("请逐笔核对变更；确认前不会修改任何账目。")
                    }
                },
                confirmButton = {
                    TextButton(onClick = vm::confirmPending) { Text("确认执行") }
                },
                dismissButton = {
                    TextButton(onClick = vm::cancelPending) { Text("取消") }
                },
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText("message", text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}
