package com.expense.tracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = AppColors.TextPrimary,
    onPrimary = Color.White,
    secondary = AppColors.Accent,
    background = AppColors.Bg,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.Bg,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.ChipFill,
    onSurfaceVariant = AppColors.TextSecondary,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightScheme,
        typography = AppTypography,
        content = content,
    )
}
