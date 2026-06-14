package com.expense.tracker.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expense.tracker.ui.theme.AppColors

@Composable
fun TopBar(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 迷你 App 图标 — 蓝色圆底 + 白色 ¥
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(AppColors.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Text("¥", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "记账助手",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.weight(1f))
            // 右侧蓝色活跃指示点（大号，明显可见）
            Box(
                modifier = Modifier
                    .offset(y = (-2).dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(AppColors.Accent),
            )
        }
        Spacer(Modifier.height(10.dp))
        // 底部渐变装饰线 — 从左到右，蓝色渐变到透明
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
        ) {
            val w = size.width
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(AppColors.Accent, AppColors.Accent.copy(alpha = 0.1f)),
                    startX = 0f,
                    endX = w,
                ),
                start = Offset(0f, 0f),
                end = Offset(w, 0f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}
