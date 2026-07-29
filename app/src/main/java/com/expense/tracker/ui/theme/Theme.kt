package com.expense.tracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = LightAppColors.TextPrimary,
    onPrimary = Color.White,
    secondary = LightAppColors.Accent,
    background = LightAppColors.Bg,
    onBackground = LightAppColors.TextPrimary,
    surface = LightAppColors.Bg,
    onSurface = LightAppColors.TextPrimary,
    surfaceVariant = LightAppColors.ChipFill,
    onSurfaceVariant = LightAppColors.TextSecondary,
)

private val DarkScheme = darkColorScheme(
    primary = DarkAppColors.TextPrimary,
    onPrimary = DarkAppColors.Bg,
    secondary = DarkAppColors.Accent,
    background = DarkAppColors.Bg,
    onBackground = DarkAppColors.TextPrimary,
    surface = DarkAppColors.Bg,
    onSurface = DarkAppColors.TextPrimary,
    surfaceVariant = DarkAppColors.ChipFill,
    onSurfaceVariant = DarkAppColors.TextSecondary,
)

@Composable
fun AppTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkAppColors else LightAppColors
    CompositionLocalProvider(LocalAppColors provides palette) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = AppTypography,
            content = content,
        )
    }
}
