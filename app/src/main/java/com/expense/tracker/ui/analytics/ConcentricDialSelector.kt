package com.expense.tracker.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.model.Period
import com.expense.tracker.ui.theme.AppColors
import java.time.ZoneId
import kotlin.math.min

/**
 * 三层同心环罗盘：外环=周，中环=月，里环=年。
 * - 旋转改变"激活环"的值；点环切换激活环。
 * - 激活环 = 选定粒度，三环当前值组合出参考时段。
 * - 确定后回调 onSelect(period, refMillis)。
 */
@Composable
fun ConcentricDialSelector(
    initialYear: Int,
    initialMonth: Int,
    initialWeek: Int,
    zone: ZoneId,
    onSelect: (period: Period, refMillis: Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var year by remember { mutableStateOf(initialYear) }
    var month by remember { mutableStateOf(initialMonth) }
    var week by remember { mutableStateOf(initialWeek) }
    var activeRing by remember { mutableStateOf(DialRing.Month) }
    val minYear = initialYear - 5
    val maxYear = initialYear + 1

    val density = LocalDensity.current
    val boxPx = with(density) { 300.dp.toPx() }
    val rOuter = boxPx * 0.46f // 周（最外）
    val rMid = boxPx * 0.33f   // 月
    val rInner = boxPx * 0.20f // 年（最里）
    val thickness = boxPx * 0.085f

    var accumRotation by remember { mutableStateOf(0f) }

    fun applySteps(steps: Int) {
        when (activeRing) {
            DialRing.Week -> {
                val maxW = ConcentricDialMath.weeksInYear(year)
                week = ConcentricDialMath.nextValue(week, steps, 1, maxW, wrap = true)
            }
            DialRing.Month -> month = ConcentricDialMath.nextValue(month, steps, 1, 12, wrap = true)
            DialRing.Year -> year = ConcentricDialMath.nextValue(year, steps, minYear, maxYear, wrap = false)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .size(300.dp)
                    .pointerInput(activeRing) {
                        detectTransformGestures { _, _, _, rotation ->
                            accumRotation += rotation
                            val steps = (accumRotation / ConcentricDialMath.STEP_RADIANS).toInt()
                            if (steps != 0) {
                                accumRotation -= steps * ConcentricDialMath.STEP_RADIANS
                                applySteps(steps)
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val dist = (offset - center).getDistance()
                            activeRing = when {
                                dist > rMid + thickness / 2f -> DialRing.Week
                                dist > rInner + thickness / 2f -> DialRing.Month
                                else -> DialRing.Year
                            }
                        }
                    },
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                drawRing(center, rOuter, thickness, DialRing.Week, activeRing, week.toString(), min(size.width, size.height))
                drawRing(center, rMid, thickness, DialRing.Month, activeRing, month.toString(), min(size.width, size.height))
                drawRing(center, rInner, thickness, DialRing.Year, activeRing, year.toString(), min(size.width, size.height))
            }
        }

        Text(
            ConcentricDialMath.label(activeRing, year, month, week),
            style = MaterialTheme.typography.titleLarge,
            color = AppColors.TextPrimary,
        )
        Text(
            "外环=周 · 中环=月 · 里环=年　点环切换，旋转选值",
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.TextMuted,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            ) { Text("取消") }
            Button(
                onClick = {
                    val ref = ConcentricDialMath.refMillis(activeRing, year, month, week, zone)
                    onSelect(ConcentricDialMath.period(activeRing), ref)
                },
                modifier = Modifier.weight(1.6f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.TextPrimary,
                    contentColor = Color.White,
                ),
            ) { Text("查看") }
        }
    }
}

/** 画一个环：背景描边 + 激活高亮 + 12 点位置标当前值数字。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRing(
    center: Offset,
    radius: Float,
    thickness: Float,
    ring: DialRing,
    active: DialRing,
    valueText: String,
    @Suppress("UNUSED_PARAMETER") minDim: Float,
) {
    val accent = AppColors.Accent
    val isActive = ring == active
    drawCircle(
        color = accent.copy(alpha = if (isActive) 0.20f else 0.10f),
        radius = radius,
        center = center,
        style = Stroke(width = thickness),
    )
    if (isActive) {
        drawCircle(
            color = accent.copy(alpha = 0.95f),
            radius = radius,
            center = center,
            style = Stroke(width = thickness * 0.32f),
        )
    }
    // 12 点位置标数字
    val top = Offset(center.x, center.y - radius)
    drawIntoCanvas {
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 38f
            color = (if (isActive) accent else AppColors.TextSecondary).toArgb()
            isFakeBoldText = isActive
        }
        it.nativeCanvas.drawText(valueText, top.x, top.y + paint.textSize / 3f, paint)
    }
}
