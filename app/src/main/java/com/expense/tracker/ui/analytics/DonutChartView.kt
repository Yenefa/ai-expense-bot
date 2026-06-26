package com.expense.tracker.ui.analytics

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.model.Category
import kotlin.math.PI
import kotlin.math.atan2

// 按 Category.id 给每个分类指定固定色
private val CategoryColors: Map<String, Color> = mapOf(
    "food"          to Color(0xFFFCD34D), // 餐饮 — 黄色
    "transport"     to Color(0xFF6B7280), // 交通 — 灰
    "shopping"     to Color(0xFFEC4899),  // 购物 — 粉
    "drink"         to Color(0xFF7DD3FC), // 饮品 — 浅蓝
    "entertainment" to Color(0xFFA78BFA), // 娱乐 — 紫
    "housing"       to Color(0xFF34D399), // 住房 — 绿
    "medical"       to Color(0xFFF87171), // 医疗 — 红
    "investment"    to Color(0xFF14B8A6), // 投资 — 青绿
    "other"         to Color(0xFFD4D4D4), // 其他 — 浅灰
)
private val FallbackColor = Color(0xFF9CA3AF)

/** 子周期颜色调色板（内环用），最多支持 31 色 */
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

    Row(
        modifier = modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 甜甜圈 Canvas
        Canvas(
            modifier = Modifier
                .size(180.dp)
                .pointerInput(subPeriods, selectedIndex) {
                    val innerRadiusRatio = 0.35f
                    val outerRadiusRatio = 0.48f
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val press = event.changes.firstOrNull() ?: break
                            if (!press.pressed) continue

                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val dx = press.position.x - cx
                            val dy = press.position.y - cy
                            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                            val maxR = size.minDimension / 2f

                            // 检测是否点击在中心区域（返回按钮）
                            val centerR = maxR * innerRadiusRatio * 0.5f
                            if (dist <= centerR) {
                                if (selectedIndex != null) {
                                    onSelectSubPeriod(null)
                                }
                                continue
                            }

                            // 检测是否点击在内环区域
                            val innerR = maxR * innerRadiusRatio
                            val outerR = maxR * outerRadiusRatio
                            if (dist in innerR..outerR && subPeriods.isNotEmpty()) {
                                val subTotal = subPeriods.sumOf { it.totalAmount }.takeIf { it > 0 } ?: 1.0
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
            val innerR = maxR * 0.35f
            val center = Offset(size.width / 2f, size.height / 2f)

            // --- 绘制外环：分类占比 ---
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

            // 用白色覆盖中心区域形成甜甜圈孔
            drawCircle(
                color = Color.White,
                radius = innerR * 1.02f,
                center = center,
            )

            // --- 绘制内环：子周期 ---
            if (subPeriods.isNotEmpty()) {
                val subTotal = subPeriods.sumOf { it.totalAmount }.takeIf { it > 0 } ?: 1.0
                var subStart = -90f
                subPeriods.forEachIndexed { i, detail ->
                    val sweep = (detail.totalAmount / subTotal * 360.0).toFloat()
                    val isSelected = i == selectedIndex
                    val color = SubPeriodColors[i % SubPeriodColors.size]
                    val finalColor = if (isSelected) color else color.copy(alpha = 0.7f)

                    drawArc(
                        color = finalColor,
                        startAngle = subStart,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = Offset(
                            center.x - innerR,
                            center.y - innerR,
                        ),
                        size = Size(innerR * 2, innerR * 2),
                    )
                    subStart += sweep
                }

                // 绘制最中心的返回按钮区域
                val centerCircleR = innerR * 0.45f
                drawCircle(
                    color = Color(0xFFF5F5F5),
                    radius = centerCircleR,
                    center = center,
                )
                if (selectedIndex != null) {
                    drawCircle(
                        color = Color(0xFFE0E0E0),
                        radius = centerCircleR * 0.85f,
                        center = center,
                    )
                }
            }
        }

        Spacer(Modifier.width(20.dp))

        // --- 右侧图例 ---
        val displayData = if (selectedIndex != null && selectedIndex in subPeriods.indices) {
            val detail = subPeriods[selectedIndex]
            val subTotal = detail.totalAmount.takeIf { it > 0 } ?: return@Row
            Category.ALL.mapNotNull { c ->
                val v = detail.byCategory[c.id]
                if (v == null || v <= 0.0) null
                else Triple(c, v, CategoryColors[c.id] ?: FallbackColor)
            }
        } else {
            ordered
        }

        val displayTotal = displayData.sumOf { (_, v, _) -> v }.takeIf { it > 0 } ?: return@Row

        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (selectedIndex != null && selectedIndex in subPeriodLabels.indices) {
                Text(
                    "📌 ${subPeriodLabels[selectedIndex]}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray,
                )
            }
            displayData.forEach { (c, v, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(10.dp)) { drawRect(color) }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${c.emoji} ${c.displayName}  ¥${"%.2f".format(v)}  (${"%.1f".format(v / displayTotal * 100)}%)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
