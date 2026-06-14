package com.expense.tracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Normal),  // 金额数字
    displayMedium = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Normal), // ¥ 符号
    titleLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),  // 顶部标题
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),     // 聊天正文
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),    // 副文
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),   // 分类标签
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),  // 状态提示
)
