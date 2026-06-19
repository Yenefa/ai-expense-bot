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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.expense.tracker.data.db.ChatMessageEntity
import com.expense.tracker.data.db.ExpenseEntity
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
    val context = LocalContext.current
    // 双击分类弹气泡的状态：null = 不显示
    var bubbleCategoryId by remember { mutableStateOf<String?>(null) }
    // 长按消息时弹出"复制/编辑"action sheet
    var actionSheetTarget by remember { mutableStateOf<ChatMessageEntity?>(null) }
    // 编辑消息 + 关联 expense 的弹窗状态
    var editTarget by remember { mutableStateOf<Pair<ChatMessageEntity, ExpenseEntity?>?>(null) }

    // 气泡或 sheet/编辑弹窗打开时拦截系统返回键，只关弹窗，不退出 App
    BackHandler(enabled = bubbleCategoryId != null || actionSheetTarget != null || editTarget != null) {
        when {
            editTarget != null -> editTarget = null
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

        // 长按消息：弹出复制/编辑 action sheet
        MessageActionSheet(
            message = actionSheetTarget,
            onDismiss = { actionSheetTarget = null },
            onCopy = {
                val msg = actionSheetTarget ?: return@MessageActionSheet
                copyToClipboard(context, msg.content)
                actionSheetTarget = null
            },
            onEdit = {
                val msg = actionSheetTarget ?: return@MessageActionSheet
                actionSheetTarget = null
                vm.loadEditTarget(msg) { loaded, expense ->
                    editTarget = loaded to expense
                }
            },
        )

        // 编辑消息 + 关联 expense 的弹窗
        MessageEditDialog(
            message = editTarget?.first,
            linkedExpense = editTarget?.second,
            onDismiss = { editTarget = null },
            onSave = { newContent, newExpense ->
                editTarget?.let { vm.saveEdit(it.first, newContent, newExpense) }
                editTarget = null
                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
            },
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText("message", text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}
