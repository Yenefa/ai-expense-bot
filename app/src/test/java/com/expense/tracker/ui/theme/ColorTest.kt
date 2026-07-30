package com.expense.tracker.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ColorTest {
    // 浅色板（原 AppColors 静态值迁至 LightAppColors）
    @Test fun lightBgIsWhite() = assertThat(LightAppColors.Bg).isEqualTo(Color(0xFFFFFFFF))
    @Test fun lightTextPrimaryIsAlmostBlack() = assertThat(LightAppColors.TextPrimary).isEqualTo(Color(0xFF0D0D0D))
    @Test fun lightTextSecondaryIsMidGray() = assertThat(LightAppColors.TextSecondary).isEqualTo(Color(0xFF5D5D5D))
    @Test fun lightTextMutedIsLightGray() = assertThat(LightAppColors.TextMuted).isEqualTo(Color(0xFF8E8E8E))
    @Test fun lightChipFillIsVeryLightGray() = assertThat(LightAppColors.ChipFill).isEqualTo(Color(0xFFF4F4F4))
    @Test fun lightAccentIsAppleBlue() = assertThat(LightAppColors.Accent).isEqualTo(Color(0xFF0A84FF))
    @Test fun lightCardMatchesWhiteSurface() =
        assertThat(LightAppColors.CardBg).isEqualTo(Color(0xFFFFFFFF))

    // 深色板：文字应变亮、背景变暗、卡片层比背景亮以保证层次
    @Test fun darkBgIsNotWhite() = assertThat(DarkAppColors.Bg).isNotEqualTo(Color(0xFFFFFFFF))
    @Test fun darkTextPrimaryIsLight() = assertThat(DarkAppColors.TextPrimary).isEqualTo(Color(0xFFF2F2F2))
    @Test fun darkChipFillBrighterThanBg() {
        assertThat(luma(DarkAppColors.ChipFill)).isGreaterThan(luma(DarkAppColors.Bg))
    }
    @Test fun darkCardIsBrighterThanPageBackground() {
        assertThat(luma(DarkAppColors.CardBg)).isGreaterThan(luma(DarkAppColors.Bg))
    }
    @Test fun darkPrimaryFillUsesDarkReadableForeground() {
        assertThat(DarkAppColors.Bg).isNotEqualTo(Color.White)
        assertThat(luma(DarkAppColors.TextPrimary) - luma(DarkAppColors.Bg)).isGreaterThan(0.7)
    }

    private fun luma(c: Color): Double = 0.299 * c.red + 0.587 * c.green + 0.114 * c.blue
}
