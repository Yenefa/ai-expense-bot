package com.expense.tracker.ui.analytics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.model.Category
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.softShadow

private val CategoryColors: Map<String, Color> = mapOf(
    "food"          to Color(0xFFFCD34D),
    "transport"     to Color(0xFF6B7280),
    "shopping"      to Color(0xFFEC4899),
    "drink"         to Color(0xFF7DD3FC),
    "entertainment" to Color(0xFFA78BFA),
    "housing"       to Color(0xFF34D399),
    "medical"       to Color(0xFFF87171),
    "investment"    to Color(0xFF14B8A6),
    "other"         to Color(0xFFD4D4D4),
)
private val FallbackColor = Color(0xFF9CA3AF)

/**
 * 分类占比清晰列表：抛弃甜甜圈/饼图，改为
 *  emoji + 分类名 + 占比条 + 百分比 + 金额，按金额降序。
 *  选中子周期时传入 [byCategory] 即可联动。
 */
@Composable
fun CategoryBreakdownList(
    byCategory: Map<String, Double>,
    modifier: Modifier = Modifier,
) {
    val total = byCategory.values.sum()
    if (total <= 0.0) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .softShadow(elevation = 2.dp, cornerRadius = 16.dp, spotAlpha = 0.06f)
                .clip(RoundedCornerShape(16.dp))
                .background(AppColors.CardBg)
                .padding(20.dp),
        ) {
            Text(
                "暂无分类数据",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextMuted,
            )
        }
        return
    }

    val ordered = Category.ALL.mapNotNull { c ->
        val v = byCategory[c.id]
        if (v == null || v <= 0.0) null
        else Triple(c, v, CategoryColors[c.id] ?: FallbackColor)
    }.sortedByDescending { it.second }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .softShadow(elevation = 2.dp, cornerRadius = 16.dp, spotAlpha = 0.06f)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.CardBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                "总支出",
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.TextSecondary,
            )
            Text(
                "¥%.2f".format(total),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
            )
        }
        Spacer(Modifier.height(2.dp))

        ordered.forEach { (cat, amount, color) ->
            val percent = (amount / total * 100).toFloat()
            val animatedPercent by animateFloatAsState(
                targetValue = percent,
                animationSpec = tween(durationMillis = 600),
                label = "bar-${cat.id}",
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${cat.emoji} ${cat.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.width(88.dp),
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(AppColors.ChipFill),
                ) {
                    Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
                        drawRoundRect(
                            color = color,
                            topLeft = androidx.compose.ui.geometry.Offset.Zero,
                            size = Size(size.width * animatedPercent / 100f, size.height),
                            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "%.1f%%".format(percent),
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.width(48.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "¥%.2f".format(amount),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.width(72.dp),
                )
            }
        }
    }
}
