package com.expense.tracker.ui.analytics

import androidx.compose.animation.core.Spring.StiffnessMediumLow
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.model.Category
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

private val CategoryColors: Map<String, Color> = mapOf(
    "food"          to Color(0xFFFCD34D),
    "transport"     to Color(0xFF6B7280),
    "shopping"     to Color(0xFFEC4899),
    "drink"         to Color(0xFF7DD3FC),
    "entertainment" to Color(0xFFA78BFA),
    "housing"       to Color(0xFF34D399),
    "medical"       to Color(0xFFF87171),
    "investment"    to Color(0xFF14B8A6),
    "other"         to Color(0xFFD4D4D4),
)
private val FallbackColor = Color(0xFF9CA3AF)

private val SubPeriodColors = listOf(
    Color(0xFF60A5FA), Color(0xFF34D399), Color(0xFFFBBF24), Color(0xFFF87171),
    Color(0xFFA78BFA), Color(0xFFFB923C), Color(0xFF22D3EE), Color(0xFFE879F9),
    Color(0xFFF472B6), Color(0xFF818CF8), Color(0xFFFDE68A), Color(0xFF6EE7B7),
    Color(0xFFFCA5A5), Color(0xFFC4B5FD), Color(0xFFFDBA74), Color(0xFF67E8F9),
    Color(0xFFF0ABFC), Color(0xFFF9A8D4), Color(0xFFA5B4FC), Color(0xFFD1FAE5),
    Color(0xFFFEF3C7), Color(0xFFFEE2E2), Color(0xFFE0E7FF), Color(0xFFCCFBF1),
    Color(0xFFFFF7ED), Color(0xFFF5F3FF), Color(0xFFFCE7F3), Color(0xFFECFDF5),
    Color(0xFFFFFBEB), Color(0xFFFEF2F2), Color(0xFFEDE9FE),
)

private data class LegendEntry(
    val emoji: String,
    val label: String,
    val amount: Double,
    val color: Color,
)

// 几何常量 — 与实际绘制区域精确对齐
private const val INNER_RADIUS_RATIO = 0.35f       // 内环外半径 = 0.35 * maxR
private const val CENTER_BUTTON_RATIO = 0.45f       // 中心按钮半径 = 0.45 * innerR = 0.1575 * maxR
private const val HOLE_OVERSCAN = 1.02f             // 白色遮罩略大于 innerR 以防边缘锯齿

// 点击检测常量（比绘制区域略大 2px，方便触摸）
private const val HIT_CENTER_RATIO = 0.20f          // 中心返回区：dist <= 0.20 * maxR
private const val HIT_INNER_START = 0.20f           // 内环起始：dist >= 0.20 * maxR
private const val HIT_INNER_END = 0.38f             // 内环结束：dist <= 0.38 * maxR

@Composable
fun DonutChartView(
    subPeriods: List<SubPeriodDetail>,
    subPeriodLabels: List<String>,
    outerByCategory: Map<String, Double>,
    selectedIndex: Int?,
    onSelectSubPeriod: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = outerByCategory.values.sum().takeIf { it > 0.0 } ?: return
    val ordered = Category.ALL.mapNotNull { c ->
        val v = outerByCategory[c.id]
        if (v == null || v <= 0.0) null
        else Triple(c, v, CategoryColors[c.id] ?: FallbackColor)
    }

    // 选中高亮动画（描边宽度淡入）
    val isAnythingSelected = selectedIndex != null
    val highlightAlpha by animateFloatAsState(
        targetValue = if (isAnythingSelected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = StiffnessMediumLow),
        label = "highlightAlpha",
    )

    Row(
        modifier = modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ===== 甜甜圈 Canvas =====
        Canvas(
            modifier = Modifier
                .size(180.dp)
                .pointerInput(subPeriods, selectedIndex) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val press = event.changes.firstOrNull() ?: break
                            if (!press.pressed) continue

                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val dx = press.position.x - cx
                            val dy = press.position.y - cy
                            val dist = sqrt(dx * dx + dy * dy)
                            val maxR = minOf(size.width, size.height) / 2f

                            // 中心返回区
                            if (dist <= maxR * HIT_CENTER_RATIO) {
                                if (selectedIndex != null) {
                                    onSelectSubPeriod(null)
                                }
                                continue
                            }

                            // 内环（子周期）点击
                            val innerStart = maxR * HIT_INNER_START
                            val innerEnd = maxR * HIT_INNER_END
                            if (dist in innerStart..innerEnd && subPeriods.isNotEmpty()) {
                                val subTotal = subPeriods.sumOf { it.totalAmount }.takeIf { it > 0 } ?: 1.0
                                // 将 Canvas 坐标映射到角度：atan2 返回 -PI~PI，转成 0~360
                                // Canvas 的 0° 在 3 点钟方向且顺时针，我们起始是 -90°（12 点钟方向）
                                val angle = (atan2(dy, dx).toDouble() * 180 / PI + 90 + 360) % 360
                                var cumulative = 0.0
                                for (i in subPeriods.indices) {
                                    val sweep = subPeriods[i].totalAmount / subTotal * 360
                                    cumulative += sweep
                                    if (angle < cumulative) {
                                        onSelectSubPeriod(if (i == selectedIndex) null else i)
                                        break
                                    }
                                }
                            }
                        }
                    }
                },
        ) {
            val maxR = size.minDimension / 2f
            val innerR = maxR * INNER_RADIUS_RATIO
            val center = Offset(size.width / 2f, size.height / 2f)

            // ---- 外环：分类占比 ----
            var start = -90f
            ordered.forEach { (_, v, color) ->
                val sweep = (v / total * 360.0).toFloat()
                drawArc(
                    color = color,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height),
                )
                start += sweep
            }

            // 白色甜甜圈孔
            drawCircle(
                color = Color.White,
                radius = innerR * HOLE_OVERSCAN,
                center = center,
            )

            // ---- 内环：子周期分布 ----
            if (subPeriods.isNotEmpty()) {
                val subTotal = subPeriods.sumOf { it.totalAmount }.takeIf { it > 0 } ?: 1.0
                var subStart = -90f

                subPeriods.forEachIndexed { i, detail ->
                    val sweep = (detail.totalAmount / subTotal * 360.0).toFloat()
                    val isSelected = i == selectedIndex
                    val color = SubPeriodColors[i % SubPeriodColors.size]

                    // 未选中时的透明度
                    val alpha = when {
                        isSelected -> 1f
                        selectedIndex == null -> 0.7f  // 无选择，全部正常
                        else -> 0.35f                    // 有别的选中，降低透明度
                    }

                    drawArc(
                        color = color.copy(alpha = alpha),
                        startAngle = subStart,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = Offset(center.x - innerR, center.y - innerR),
                        size = Size(innerR * 2, innerR * 2),
                    )

                    // 选中扇区：绘制高亮描边
                    if (isSelected) {
                        val strokeW = 3.dp.toPx() * highlightAlpha
                        if (strokeW > 0.5f) {
                            drawArc(
                                color = color,
                                startAngle = subStart,
                                sweepAngle = sweep,
                                useCenter = true,
                                topLeft = Offset(center.x - innerR, center.y - innerR),
                                size = Size(innerR * 2, innerR * 2),
                                style = Stroke(width = strokeW),
                            )
                        }
                    }

                    subStart += sweep
                }

                // ---- 中心返回按钮 ----
                val centerCircleR = innerR * CENTER_BUTTON_RATIO
                drawCircle(
                    color = Color(0xFFF0F0F0),
                    radius = centerCircleR,
                    center = center,
                )
                // 外圈阴影
                drawCircle(
                    color = Color(0xFFDDDDDD),
                    radius = centerCircleR,
                    center = center,
                    style = Stroke(width = 1.dp.toPx()),
                )

                // 绘制 ← 返回箭头
                val arrowS = centerCircleR * 0.45f
                val arrowPath = Path().apply {
                    moveTo(center.x + arrowS, center.y - arrowS * 0.5f)
                    lineTo(center.x - arrowS * 0.3f, center.y)
                    lineTo(center.x + arrowS, center.y + arrowS * 0.5f)
                }
                drawPath(
                    path = arrowPath,
                    color = Color(0xFF888888),
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )

            }
        }

        Spacer(Modifier.width(20.dp))

        // ===== 右侧图例 =====
        val displayEntries = if (selectedIndex != null && selectedIndex in subPeriods.indices) {
            // 选中子周期 → 显示该子周期的完整分类明细
            val detail = subPeriods[selectedIndex]
            if (detail.totalAmount <= 0.0) return@Row
            Category.ALL.mapNotNull { c ->
                val v = detail.byCategory[c.id]
                if (v == null || v <= 0.0) null
                else LegendEntry(c.emoji, c.displayName, v, CategoryColors[c.id] ?: FallbackColor)
            }
        } else {
            // 未选中 → 只显示 Top-5 + "其他"
            val top5 = ordered.take(5).map { (c, v, color) ->
                LegendEntry(c.emoji, c.displayName, v, color)
            }
            val otherSum = ordered.drop(5).sumOf { (_, v, _) -> v }
            if (otherSum > 0 && ordered.size > 5) {
                top5 + LegendEntry("📦", "其他", otherSum, FallbackColor)
            } else {
                top5
            }
        }

        val displayTotal = displayEntries.sumOf { it.amount }.takeIf { it > 0 } ?: return@Row

        Column(
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // 子周期标题
            if (selectedIndex != null && selectedIndex in subPeriodLabels.indices) {
                Text(
                    "📌 ${subPeriodLabels[selectedIndex]}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray,
                )
            }

            displayEntries.forEach { entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(10.dp)) { drawRect(entry.color) }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${entry.emoji} ${entry.label}  ¥${"%.2f".format(entry.amount)}  (${"%.1f".format(entry.amount / displayTotal * 100)}%)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selectedIndex != null) Color.Black else
                            MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
