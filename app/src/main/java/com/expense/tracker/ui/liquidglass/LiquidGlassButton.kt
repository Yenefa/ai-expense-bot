package com.expense.tracker.ui.liquidglass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 🧠 液态玻璃按钮：ON 时高透白光；OFF 时几乎隐形。
 * 状态由父级控制（不在内部存），点击调 [onToggle]。
 */
@Composable
fun LiquidGlassButton(
    on: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgAlpha by animateFloatAsState(
        targetValue = if (on) 0.06f else 0.02f,
        animationSpec = tween(300),
        label = "bg",
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (on) 0.08f else 0.04f,
        animationSpec = tween(300),
        label = "border",
    )
    val shadowDp by animateFloatAsState(
        targetValue = if (on) 6f else 0f,
        animationSpec = tween(300),
        label = "shadow",
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (on) 1f else 0.22f,
        animationSpec = tween(300),
        label = "icon",
    )

    Box(
        modifier = modifier
            .size(34.dp)
            .shadow(
                shadowDp.dp, CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.06f),
                clip = false,
            )
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = bgAlpha))
            .pointerInput(on) { detectTapGestures(onTap = { onToggle() }) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(34.dp)) {
            drawCircle(
                color = Color.Black.copy(alpha = borderAlpha),
                radius = size.minDimension / 2 - 1.dp.toPx(),
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
        if (on) {
            Box(
                modifier = Modifier
                    .size(width = 12.dp, height = 4.dp)
                    .align(Alignment.TopStart)
                    .rotate(-25f)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.7f))
                    .alpha(0.85f),
            )
        }
        Text(
            text = "🧠",
            style = TextStyle(fontSize = 15.sp),
            modifier = Modifier.alpha(iconAlpha),
        )
    }
}
