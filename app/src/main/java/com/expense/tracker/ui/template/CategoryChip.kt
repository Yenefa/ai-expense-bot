package com.expense.tracker.ui.template

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.model.Category
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.softShadow

@Composable
fun CategoryChip(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // 背景色平滑过渡：次级填充 ↔ 强前景
    val bg by animateColorAsState(
        targetValue = if (selected) AppColors.TextPrimary else AppColors.ChipFill,
        animationSpec = tween(durationMillis = 280),
        label = "chip-bg",
    )
    // 文字色始终与背景形成深浅主题对应的对比
    val fg by animateColorAsState(
        targetValue = if (selected) AppColors.Bg else AppColors.TextPrimary,
        animationSpec = tween(durationMillis = 280),
        label = "chip-fg",
    )
    // 选中时轻微放大 1.06 倍，spring 弹性
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "chip-scale",
    )

    Box(
        modifier = modifier
            .scale(scale)
            .softShadow(
                elevation = if (selected) 1.dp else 3.dp,
                cornerRadius = 18.dp,
                spotAlpha = if (selected) 0.04f else 0.10f,
            )
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .pointerInput(onClick, onDoubleClick) {
                detectTapGestures(
                    onTap = { onClick() },
                    onDoubleTap = { onDoubleClick() },
                )
            }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = "${category.emoji} ${category.displayName}",
            color = fg,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
