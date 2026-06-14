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

private val PieColors = listOf(
    Color(0xFF0D0D0D), Color(0xFF505050), Color(0xFF808080),
    Color(0xFFB0B0B0), Color(0xFF0A84FF), Color(0xFF6E6E6E),
    Color(0xFF3D3D3D), Color(0xFFD4D4D4),
)

@Composable
fun PieChartView(byCategory: Map<String, Double>, modifier: Modifier = Modifier) {
    val total = byCategory.values.sum().takeIf { it > 0.0 } ?: 1.0
    val ordered = Category.ALL.mapIndexedNotNull { i, c ->
        val v = byCategory[c.id]
        if (v == null || v <= 0.0) null else Triple(c, v, PieColors[i % PieColors.size])
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
