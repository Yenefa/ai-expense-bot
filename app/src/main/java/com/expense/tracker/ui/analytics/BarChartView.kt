package com.expense.tracker.ui.analytics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.expense.tracker.ui.theme.AppColors
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf

@Composable
fun BarChartView(amounts: List<Double>, xLabels: List<String>, modifier: Modifier = Modifier) {
    // 全 0/空数据时不画空图，直接提示
    if (amounts.none { it > 0.0 }) {
        Box(
            modifier = modifier.fillMaxWidth().height(260.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "该时段暂无消费记录",
                color = AppColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }
    val producer = remember { ChartEntryModelProducer() }
    LaunchedEffect(amounts) {
        producer.setEntries(amounts.mapIndexed { i, v -> entryOf(i.toFloat(), v.toFloat()) })
    }
    val xFormatter = AxisValueFormatter<AxisPosition.Horizontal.Bottom> { x, _ ->
        xLabels.getOrNull(x.toInt()) ?: ""
    }
    val yFormatter = AxisValueFormatter<AxisPosition.Vertical.Start> { y, _ ->
        "¥" + y.toInt().toString()
    }
    Chart(
        modifier = modifier.fillMaxWidth().height(260.dp),
        chart = columnChart(),
        chartModelProducer = producer,
        startAxis = rememberStartAxis(
            valueFormatter = yFormatter,
            itemPlacer = AxisItemPlacer.Vertical.default(maxItemCount = 4),
        ),
        bottomAxis = rememberBottomAxis(
            valueFormatter = xFormatter,
            itemPlacer = AxisItemPlacer.Horizontal.default(spacing = if (xLabels.size > 12) 5 else 1),
        ),
    )
}
