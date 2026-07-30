package com.expense.tracker.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AnalyticsChartColorsTest {
    @Test
    fun darkChartColorsRemainVisibleOnDarkBackground() {
        val colors = DarkAppColors.analyticsChartColors()

        assertThat(luma(colors.label) - luma(DarkAppColors.Bg)).isGreaterThan(0.35)
        assertThat(colors.guideline.alpha).isWithin(0.01f).of(0.28f)
        assertThat(colors.axisLine.alpha).isWithin(0.01f).of(0.55f)
        assertThat(colors.entities).containsExactly(DarkAppColors.TextPrimary)
    }

    @Test
    fun lightChartColorsKeepExistingMonochromeAppearance() {
        val colors = LightAppColors.analyticsChartColors()

        assertThat(colors.label).isEqualTo(LightAppColors.TextSecondary)
        assertThat(colors.entities).containsExactly(LightAppColors.TextPrimary)
    }

    private fun luma(color: Color): Double =
        0.299 * color.red + 0.587 * color.green + 0.114 * color.blue
}
