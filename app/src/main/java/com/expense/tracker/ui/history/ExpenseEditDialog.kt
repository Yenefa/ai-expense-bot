package com.expense.tracker.ui.history

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.model.Category
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.softShadow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 直接编辑 expense 行（金额 / 分类 / 备注 / 日期时间）。
 * 保存时调 onSave(updated)，UPDATE 原行 — 真正影响到数据库的消费记录。
 *
 * v2.7 起：替代以前在聊天里改消息的"假编辑"。
 */
@Composable
fun ExpenseEditDialog(
    expense: ExpenseEntity?,
    onDismiss: () -> Unit,
    onSave: (ExpenseEntity) -> Unit,
) {
    // 缓存最后一次非空 expense，避免退出动画期间内容置空报错
    var cached by remember { mutableStateOf<ExpenseEntity?>(null) }
    LaunchedEffect(expense) {
        if (expense != null) cached = expense
    }
    val display = cached ?: return
    val visible = expense != null

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
                    .padding(horizontal = 24.dp)
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
                EditContent(
                    original = display,
                    onCancel = onDismiss,
                    onSave = onSave,
                )
            }
        }
    }
}

@Composable
private fun EditContent(
    original: ExpenseEntity,
    onCancel: () -> Unit,
    onSave: (ExpenseEntity) -> Unit,
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }

    // remember(original.id) 确保切换不同 expense 时重新初始化
    var amountText by remember(original.id) { mutableStateOf("%.2f".format(original.amount)) }
    var categoryId by remember(original.id) { mutableStateOf(original.categoryId) }
    var noteText by remember(original.id) { mutableStateOf(original.note) }
    var occurredAt by remember(original.id) { mutableStateOf(original.occurredAt) }

    val dateFmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val occurredLocal = LocalDateTime.ofInstant(Instant.ofEpochMilli(occurredAt), zone)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "编辑记账",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.TextPrimary,
            fontSize = 18.sp,
        )
        Spacer(Modifier.height(16.dp))

        // —— 分类 ——
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
                        color = if (selected) AppColors.Bg else AppColors.TextPrimary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // —— 金额 ——
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

        // —— 备注 ——
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
        Spacer(Modifier.height(14.dp))

        // —— 日期 + 时间 ——
        Text("发生时间", style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 日期按钮
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.ChipFill)
                    .clickable {
                        val date = occurredLocal.toLocalDate()
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val newDate = LocalDate.of(year, month + 1, dayOfMonth)
                                val newDateTime = LocalDateTime.of(newDate, occurredLocal.toLocalTime())
                                occurredAt = newDateTime.atZone(zone).toInstant().toEpochMilli()
                            },
                            date.year, date.monthValue - 1, date.dayOfMonth,
                        ).show()
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    occurredLocal.format(dateFmt),
                    color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            // 时间按钮
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.ChipFill)
                    .clickable {
                        val time = occurredLocal.toLocalTime()
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                val newTime = LocalTime.of(hour, minute)
                                val newDateTime = LocalDateTime.of(occurredLocal.toLocalDate(), newTime)
                                occurredAt = newDateTime.atZone(zone).toInstant().toEpochMilli()
                            },
                            time.hour, time.minute, true,
                        ).show()
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    occurredLocal.format(timeFmt),
                    color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // —— 取消 / 保存 ——
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppColors.TextPrimary)
                    .clickable {
                        val newAmount = amountText.toDoubleOrNull() ?: return@clickable
                        if (newAmount <= 0.0) return@clickable
                        onSave(
                            original.copy(
                                amount = newAmount,
                                categoryId = categoryId,
                                note = noteText.trim(),
                                occurredAt = occurredAt,
                            )
                        )
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = "保存",
                        tint = AppColors.Bg,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "保存",
                        color = AppColors.Bg,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
