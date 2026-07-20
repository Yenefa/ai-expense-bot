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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.expense.tracker.ui.theme.AppColors
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.chart.line.LineChart.LineSpec
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf

@Composable
fun LineChartView(counts: List<Int>, xLabels: List<String>, modifier: Modifier = Modifier) {
    // 全 0/空数据时不画空图，直接提示，避免"没生成"的困惑
    if (counts.none { it > 0 }) {
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
    LaunchedEffect(counts) {
        producer.setEntries(counts.mapIndexed { i, v -> entryOf(i.toFloat(), v.toFloat()) })
    }
    val xFormatter = AxisValueFormatter<AxisPosition.Horizontal.Bottom> { x, _ ->
        xLabels.getOrNull(x.toInt()) ?: ""
    }
    // 消费次数永远是整数（1, 2, 3 ...）-- 把小数部分截掉
    val yFormatter = AxisValueFormatter<AxisPosition.Vertical.Start> { y, _ ->
        y.toInt().toString()
    }
    // 最大值决定 Y 轴密度：少时一格一格，多时按比例稀疏
    val maxCount = counts.maxOrNull() ?: 0
    val yItemCount = when {
        maxCount <= 0 -> 2
        maxCount <= 5 -> maxCount + 1   // 0..maxCount 每个整数一条
        maxCount <= 10 -> 6
        else -> 5
    }
    Chart(
        modifier = modifier.fillMaxWidth().height(260.dp),
        // 显式 Accent 蓝，确保线在白底可见
        chart = lineChart(lines = listOf(LineSpec(AppColors.Accent.toArgb()))),
        chartModelProducer = producer,
        startAxis = rememberStartAxis(
            valueFormatter = yFormatter,
            itemPlacer = AxisItemPlacer.Vertical.default(maxItemCount = yItemCount),
        ),
        bottomAxis = rememberBottomAxis(
            valueFormatter = xFormatter,
            itemPlacer = AxisItemPlacer.Horizontal.default(spacing = if (xLabels.size > 12) 5 else 1),
        ),
    )
}
