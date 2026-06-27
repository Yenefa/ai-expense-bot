package com.expense.tracker.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expense.tracker.data.model.Category

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

private const val HOLE_RATIO = 0.50f // 甜甜圈孔比例，越大孔越大

/**
 * 简化版甜甜圈图：单环显示分类占比
 * - 点击下方子周期标签（chips）切换查看某天的分类明细
 * - 点击"总览"回到整体
 * - 无内外环复杂操作
 */
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

    // 当前要展示的分类数据
    val (displayData, displayTotal) = if (selectedIndex != null && selectedIndex in subPeriods.indices) {
        val detail = subPeriods[selectedIndex]
        val data = Category.ALL.mapNotNull { c ->
            val v = detail.byCategory[c.id]
            if (v == null || v <= 0.0) null
            else Triple(c, v, CategoryColors[c.id] ?: FallbackColor)
        }
        val t = data.sumOf { (_, v, _) -> v }
        if (t <= 0.0) return
        data to t
    } else {
        val data = Category.ALL.mapNotNull { c ->
            val v = outerByCategory[c.id]
            if (v == null || v <= 0.0) null
            else Triple(c, v, CategoryColors[c.id] ?: FallbackColor)
        }
        data to total
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ===== 甜甜圈 Canvas（单环）=====
        Canvas(
            modifier = Modifier.size(200.dp),
        ) {
            val holeRadius = size.minDimension / 2f * HOLE_RATIO
            val center = Offset(size.width / 2f, size.height / 2f)

            // 绘制分类扇区
            var start = -90f
            displayData.forEach { (_, v, color) ->
                val sweep = (v / displayTotal * 360.0).toFloat()
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

            // 白色中心孔
            drawCircle(color = Color.White, radius = holeRadius, center = center)
        }

        Spacer(Modifier.height(8.dp))

        // ===== 图例：显示当前分类明细 =====
        val legendItems = displayData.map { (c, v, _) ->
            Triple(c.emoji, c.displayName, v)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            legendItems.forEach { (emoji, label, amount) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$emoji $label",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                    )
                    Text(
                        "¥${"%.0f".format(amount)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ===== 子周期标签行（可点击 chips）=====
        // 月视图标签太多（~30），用小号 chip 展示
        val isMonthView = subPeriodLabels.size > 15
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isMonthView) 2.dp else 4.dp),
        ) {
            // "总览" chip
            val isOverview = selectedIndex == null
            ChipLabel(
                text = "📊 总览",
                isSelected = isOverview,
                isSmall = isMonthView,
                onClick = { onSelectSubPeriod(null) },
            )

            // 每个子周期一个 chip
            subPeriodLabels.forEachIndexed { i, label ->
                val hasSpending = subPeriods.getOrNull(i)?.totalAmount?.takeIf { it > 0 } != null
                ChipLabel(
                    text = label,
                    isSelected = i == selectedIndex,
                    isSmall = isMonthView,
                    hasSpending = hasSpending,
                    onClick = { onSelectSubPeriod(if (i == selectedIndex) null else i) },
                )
            }
        }
    }
}

@Composable
private fun ChipLabel(
    text: String,
    isSelected: Boolean,
    isSmall: Boolean,
    hasSpending: Boolean = true,
    onClick: () -> Unit,
) {
    val bg = if (isSelected) Color(0xFF0D0D0D)
    else if (hasSpending) Color(0xFFF0F0F0)
    else Color(0xFFF9F9F9)

    val textColor = if (isSelected) Color.White
    else if (hasSpending) Color(0xFF0D0D0D)
    else Color(0xFFCCCCCC)

    val hPad = if (isSmall) 6.dp else 10.dp
    val height = if (isSmall) 28.dp else 34.dp
    val fontSize = if (isSmall) 11.sp else 13.sp

    Box(
        modifier = Modifier
            .height(height)
            .clip(RoundedCornerShape(if (isSmall) 6.dp else 8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = hPad),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = fontSize,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
            textAlign = TextAlign.Center,
        )
    }
}
