package com.expense.tracker.ui.theme

import androidx.compose.ui.graphics.Color

data class AnalyticsChartColors(
    val label: Color,
    val guideline: Color,
    val axisLine: Color,
    val entities: List<Color>,
)

fun AppColorPalette.analyticsChartColors() = AnalyticsChartColors(
    label = TextSecondary,
    guideline = TextMuted.copy(alpha = 0.28f),
    axisLine = TextMuted.copy(alpha = 0.55f),
    entities = listOf(TextPrimary),
)
