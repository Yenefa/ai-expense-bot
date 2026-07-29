package com.expense.tracker.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 应用色板：浅色 / 深色各一套。
 * 通过 [LocalAppColors] 在 [AppTheme] 中按深浅色注入，
 * [AppColors] 作为 @Composable 访问器委托读取，调用处无需感知当前主题。
 */
data class AppColorPalette(
    val Bg: Color,
    val TextPrimary: Color,
    val TextSecondary: Color,
    val TextMuted: Color,
    val ChipFill: Color,
    val Accent: Color,
    val DockBgSimulated: Color,
    val ErrorBg: Color,
)

/** 浅色色板（ChatGPT 白底极简）。 */
val LightAppColors = AppColorPalette(
    Bg = Color(0xFFFFFFFF),
    TextPrimary = Color(0xFF0D0D0D),
    TextSecondary = Color(0xFF5D5D5D),
    TextMuted = Color(0xFF8E8E8E),
    ChipFill = Color(0xFFF4F4F4),
    Accent = Color(0xFF0A84FF),
    DockBgSimulated = Color(0xFFD4D4D4),
    ErrorBg = Color(0xFFFFF5F5),
)

/** 深色色板（背景 #1C1C1E，卡片 #2C2C2E 拉开层次，文字 #F2F2F2 护眼）。 */
val DarkAppColors = AppColorPalette(
    Bg = Color(0xFF1C1C1E),
    TextPrimary = Color(0xFFF2F2F2),
    TextSecondary = Color(0xFFAEAEAE),
    TextMuted = Color(0xFF6E6E6E),
    ChipFill = Color(0xFF2C2C2E),
    Accent = Color(0xFF0A84FF),
    DockBgSimulated = Color(0xFF2C2C2E),
    ErrorBg = Color(0xFF2A1A1A),
)

val LocalAppColors = compositionLocalOf { LightAppColors }

/**
 * 兼容旧调用：保留 `AppColors.Bg` 式静态访问，实际委托到 [LocalAppColors]，
 * 深浅色切换时自动响应。仅可在 @Composable 上下文中读取。
 */
object AppColors {
    val Bg: Color
        @Composable get() = LocalAppColors.current.Bg

    val TextPrimary: Color
        @Composable get() = LocalAppColors.current.TextPrimary

    val TextSecondary: Color
        @Composable get() = LocalAppColors.current.TextSecondary

    val TextMuted: Color
        @Composable get() = LocalAppColors.current.TextMuted

    val ChipFill: Color
        @Composable get() = LocalAppColors.current.ChipFill

    val Accent: Color
        @Composable get() = LocalAppColors.current.Accent

    val DockBgSimulated: Color
        @Composable get() = LocalAppColors.current.DockBgSimulated

    val ErrorBg: Color
        @Composable get() = LocalAppColors.current.ErrorBg
}
