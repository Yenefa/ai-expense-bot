package com.expense.tracker.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ColorTest {
    @Test fun bgIsWhite() = assertThat(AppColors.Bg).isEqualTo(Color(0xFFFFFFFF))
    @Test fun textPrimaryIsAlmostBlack() = assertThat(AppColors.TextPrimary).isEqualTo(Color(0xFF0D0D0D))
    @Test fun textSecondaryIsMidGray() = assertThat(AppColors.TextSecondary).isEqualTo(Color(0xFF5D5D5D))
    @Test fun textMutedIsLightGray() = assertThat(AppColors.TextMuted).isEqualTo(Color(0xFF8E8E8E))
    @Test fun chipFillIsVeryLightGray() = assertThat(AppColors.ChipFill).isEqualTo(Color(0xFFF4F4F4))
    @Test fun accentIsAppleBlue() = assertThat(AppColors.Accent).isEqualTo(Color(0xFF0A84FF))
}
