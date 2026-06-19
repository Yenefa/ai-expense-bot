package com.expense.tracker.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expense.tracker.data.db.ChatMessageEntity
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.model.Category
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.softShadow

/**
 * 编辑 user 消息（以及关联的 expense）的居中气泡 Dialog。
 * 沿用 CategoryBubbleDialog 的 spring 动画风格。
 *
 * - 没有关联 expense → 只能编辑 message.content
 * - 有关联 expense → 同时显示 amount / category / note 字段，保存时同步更新两表
 */
@Composable
fun MessageEditDialog(
    message: ChatMessageEntity?,
    linkedExpense: ExpenseEntity?,
    onDismiss: () -> Unit,
    onSave: (newContent: String, newExpense: ExpenseEntity?) -> Unit,
) {
    // 缓存最后一次非空 message，避免退出动画期间内容置空报错（沿用 v1.6 的修复模式）
    var cached by remember { mutableStateOf<Pair<ChatMessageEntity, ExpenseEntity?>?>(null) }
    LaunchedEffect(message, linkedExpense) {
        if (message != null) cached = message to linkedExpense
    }
    val display = cached ?: return
    val visible = message != null

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(200)) +
            scaleIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                initialScale = 0.7f,
            ),
        exit = fadeOut(animationSpec = tween(180)) +
            scaleOut(animationSpec = tween(180), targetScale = 0.85f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .softShadow(elevation = 12.dp, cornerRadius = 24.dp, spotAlpha = 0.18f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(AppColors.Bg)
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        onClick = { /* swallow */ },
                    )
                    .padding(20.dp),
            ) {
                EditDialogContent(
                    msg = display.first,
                    expense = display.second,
                    onCancel = onDismiss,
                    onSave = onSave,
                )
            }
        }
    }
}

@Composable
private fun EditDialogContent(
    msg: ChatMessageEntity,
    expense: ExpenseEntity?,
    onCancel: () -> Unit,
    onSave: (newContent: String, newExpense: ExpenseEntity?) -> Unit,
) {
    var content by remember(msg.id) { mutableStateOf(msg.content) }

    // 仅在有关联 expense 时初始化这些字段
    var amountText by remember(expense?.id) { mutableStateOf(expense?.amount?.toString() ?: "") }
    var noteText by remember(expense?.id) { mutableStateOf(expense?.note ?: "") }
    var categoryId by remember(expense?.id) { mutableStateOf(expense?.categoryId ?: "food") }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = if (expense != null) "编辑记账" else "编辑消息",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.TextPrimary,
            fontSize = 18.sp,
        )
        Spacer(Modifier.height(16.dp))

        // 消息内容字段
        Text("消息内容", style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(AppColors.ChipFill)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            BasicTextField(
                value = content,
                onValueChange = { content = it },
                textStyle = TextStyle(
                    color = AppColors.TextPrimary,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                ),
                cursorBrush = SolidColor(AppColors.TextPrimary),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (expense != null) {
            Spacer(Modifier.height(14.dp))
            Text("分类", style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
            Spacer(Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(items = Category.ALL, key = { it.id }) { c ->
                    val selected = c.id == categoryId
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) AppColors.TextPrimary else AppColors.ChipFill)
                            .clickable { categoryId = c.id }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = "${c.emoji} ${c.displayName}",
                            color = if (selected) Color.White else AppColors.TextPrimary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("金额", style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("¥", color = AppColors.TextPrimary, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.size(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.ChipFill)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    BasicTextField(
                        value = amountText,
                        onValueChange = { v ->
                            if (v.matches(Regex("^\\d{0,7}(\\.\\d{0,2})?$"))) amountText = v
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = AppColors.TextPrimary,
                            fontSize = MaterialTheme.typography.titleLarge.fontSize,
                        ),
                        cursorBrush = SolidColor(AppColors.TextPrimary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("备注", style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.ChipFill)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                BasicTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = AppColors.TextPrimary,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                    ),
                    cursorBrush = SolidColor(AppColors.TextPrimary),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (noteText.isEmpty()) {
                    Text(
                        text = "（可选）详细描述",
                        color = AppColors.TextMuted,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 取消按钮
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .border(
                        width = 1.dp,
                        color = AppColors.TextSecondary.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(14.dp),
                    )
                    .clickable { onCancel() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("取消", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            // 保存按钮
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppColors.TextPrimary)
                    .clickable {
                        val trimmedContent = content.trim()
                        if (trimmedContent.isEmpty()) return@clickable
                        val newExpense = if (expense != null) {
                            val newAmount = amountText.toDoubleOrNull() ?: expense.amount
                            if (newAmount <= 0.0) return@clickable
                            expense.copy(
                                amount = newAmount,
                                categoryId = categoryId,
                                note = noteText.trim(),
                            )
                        } else null
                        onSave(trimmedContent, newExpense)
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = "保存",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "保存",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
