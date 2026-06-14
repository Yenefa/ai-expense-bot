package com.expense.tracker.ui.template

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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expense.tracker.data.model.Category
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.softShadow

/**
 * 双击分类后弹出的气泡 — 屏幕中央，放大显示选中类型，可输入"详细描述 + 金额"后提交。
 *
 * 动画：scaleIn(0.6 → 1.0, spring bouncy) + fadeIn，与模板卡 spring 风格一致。
 */
@Composable
fun CategoryBubbleDialog(
    visible: Boolean,
    category: Category?,
    onDismiss: () -> Unit,
    onSubmit: (amount: Double, note: String) -> Unit,
) {
    // 关键：缓存最后一次非空 category，避免退出动画期间 category=null 导致 !! 崩溃
    var cachedCategory by remember { mutableStateOf<Category?>(null) }
    LaunchedEffect(category) {
        if (category != null) cachedCategory = category
    }
    val displayCategory = cachedCategory ?: return

    AnimatedVisibility(
        visible = visible && category != null,
        enter = fadeIn(animationSpec = tween(200)) +
                scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    initialScale = 0.6f,
                ),
        exit = fadeOut(animationSpec = tween(180)) +
                scaleOut(animationSpec = tween(180), targetScale = 0.85f),
    ) {
        // 半透明遮罩 — 点击外部关闭
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
            // 阻止气泡内部点击穿透到遮罩
            Box(
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .softShadow(elevation = 12.dp, cornerRadius = 24.dp, spotAlpha = 0.18f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(AppColors.Bg)
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        onClick = { /* swallow — 阻止冒泡到遮罩 */ },
                    )
                    .padding(24.dp),
            ) {
                BubbleContent(
                    category = displayCategory,
                    onCancel = onDismiss,
                    onSubmit = onSubmit,
                )
            }
        }
    }
}

@Composable
private fun BubbleContent(
    category: Category,
    onCancel: () -> Unit,
    onSubmit: (amount: Double, note: String) -> Unit,
) {
    var note by remember(category.id) { mutableStateOf("") }
    var amountText by remember(category.id) { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 大号 emoji + 分类名
        Text(category.emoji, fontSize = 48.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = category.displayName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.TextPrimary,
            fontSize = 22.sp,
        )
        Spacer(Modifier.height(20.dp))

        // 备注输入框
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(AppColors.ChipFill)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = note,
                onValueChange = { note = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = AppColors.TextPrimary,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                ),
                cursorBrush = SolidColor(AppColors.TextPrimary),
                modifier = Modifier.fillMaxWidth(),
            )
            if (note.isEmpty()) {
                Text(
                    text = "详细描述（如：菠萝百香果）",
                    color = AppColors.TextMuted,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // 金额行：¥ 大号金额 + 提交按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "¥",
                style = MaterialTheme.typography.displayMedium,
                color = AppColors.TextPrimary,
            )
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = amountText,
                    onValueChange = { v -> if (v.matches(Regex("^\\d{0,7}(\\.\\d{0,2})?$"))) amountText = v },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = AppColors.TextPrimary,
                        fontSize = MaterialTheme.typography.displayLarge.fontSize,
                    ),
                    cursorBrush = SolidColor(AppColors.TextPrimary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (amountText.isEmpty()) {
                    Text(
                        text = "0",
                        color = AppColors.TextMuted,
                        style = MaterialTheme.typography.displayLarge,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(AppColors.TextPrimary)
                    .pointerInput(amountText, note) {
                        detectTapGestures(onTap = {
                            val v = amountText.toDoubleOrNull()
                            if (v != null && v > 0) {
                                onSubmit(v, note)
                            }
                        })
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.ArrowUpward, contentDescription = "提交", tint = Color.White)
            }
        }

        Spacer(Modifier.height(14.dp))
        // 取消按钮：1px 灰色边框 + 圆角 + clickable（更高级 + 可靠）
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .border(
                    width = 1.dp,
                    color = AppColors.TextSecondary.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(14.dp),
                )
                .clickable { onCancel() }
                .padding(horizontal = 28.dp, vertical = 10.dp),
        ) {
            Text(
                "取消",
                color = AppColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Suppress("unused")
private val placeholder: Modifier = Modifier.width(0.dp).height(0.dp)
