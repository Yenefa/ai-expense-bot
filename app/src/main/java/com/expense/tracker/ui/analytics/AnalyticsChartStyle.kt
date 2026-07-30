package com.expense.tracker.ui.analytics

import androidx.compose.runtime.Composable
import com.expense.tracker.ui.theme.LocalAppColors
import com.expense.tracker.ui.theme.analyticsChartColors
import com.patrykandpatrick.vico.compose.m3.style.m3ChartStyle
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle

@Composable
fun ProvideAnalyticsChartStyle(content: @Composable () -> Unit) {
    val colors = LocalAppColors.current.analyticsChartColors()
    ProvideChartStyle(
        chartStyle = m3ChartStyle(
            axisLabelColor = colors.label,
            axisGuidelineColor = colors.guideline,
            axisLineColor = colors.axisLine,
            entityColors = colors.entities,
        ),
        content = content,
    )
}
