package com.expense.tracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * ChatGPT 风格投影：边框靠 spread shadow + 主投影做层次。
 */
fun Modifier.softShadow(
    elevation: Dp = 4.dp,
    cornerRadius: Dp = 22.dp,
    ambientAlpha: Float = 0.04f,
    spotAlpha: Float = 0.10f,
): Modifier = this.shadow(
    elevation = elevation,
    shape = RoundedCornerShape(cornerRadius),
    ambientColor = Color.Black.copy(alpha = ambientAlpha),
    spotColor = Color.Black.copy(alpha = spotAlpha),
    clip = false,
)

fun Modifier.dockShadow() = softShadow(elevation = 10.dp, cornerRadius = 28.dp, spotAlpha = 0.10f)
fun Modifier.inputShadow() = softShadow(elevation = 4.dp, cornerRadius = 28.dp, spotAlpha = 0.10f)
fun Modifier.iconBtnShadow() = softShadow(elevation = 3.dp, cornerRadius = 18.dp, spotAlpha = 0.12f)
