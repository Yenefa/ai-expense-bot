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
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.model.Category

// 按 Category.id 给每个分类指定固定色 — 颜色和真实业务语义对应
private val CategoryColors: Map<String, Color> = mapOf(
    "food"          to Color(0xFFFCD34D), // 餐饮 — 黄
    "transport"     to Color(0xFF2563EB), // 交通 — 蓝（和投资/学习拉开）
    "shopping"      to Color(0xFFEC4899), // 购物 — 粉
    "drink"         to Color(0xFF7DD3FC), // 饮品 — 浅蓝
    "entertainment" to Color(0xFFA78BFA), // 娱乐 — 紫
    "housing"       to Color(0xFF34D399), // 住房 — 绿
    "medical"       to Color(0xFFF87171), // 医疗 — 红
    "investment"    to Color(0xFFF97316), // 投资 — 橙
    "learning"      to Color(0xFF14B8A6), // 学习 — 青绿
    "other"         to Color(0xFFD4D4D4), // 其他 — 浅灰
)
private val FallbackColor = Color(0xFF9CA3AF)

@Composable
fun PieChartView(byCategory: Map<String, Double>, modifier: Modifier = Modifier) {
    val total = byCategory.values.sum().takeIf { it > 0.0 } ?: 1.0
    val ordered = Category.ALL.mapNotNull { c ->
        val v = byCategory[c.id]
        if (v == null || v <= 0.0) null
        else Triple(c, v, CategoryColors[c.id] ?: FallbackColor)
    }

    Row(
        modifier = modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(160.dp)) {
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
        }
        Spacer(Modifier.width(20.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ordered.forEach { (c, v, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(10.dp)) { drawRect(color) }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${c.emoji} ${c.displayName}  ¥${"%.2f".format(v)}  (${"%.1f".format(v / total * 100)}%)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
