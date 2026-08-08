package com.expense.tracker.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.budget.BudgetCalculator
import com.expense.tracker.data.budget.BudgetEntry
import com.expense.tracker.data.budget.BudgetStatus
import com.expense.tracker.data.model.Category
import com.expense.tracker.data.model.Money
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.softShadow

private val WarnColor = Color(0xFFE6A23C)
private val OverColor = Color(0xFFE54D42)

@Composable
fun BudgetCard(
    vm: BudgetOverviewViewModel,
    modifier: Modifier = Modifier,
) {
    val state by vm.uiState.collectAsState()
    val overview = state.overview
    val monthly = overview?.monthly
    val categories = overview?.categories.orEmpty()
    if (monthly == null && categories.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .softShadow(elevation = 2.dp, cornerRadius = 16.dp, spotAlpha = 0.06f)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.CardBg)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📊 本月预算", style = MaterialTheme.typography.titleMedium, color = AppColors.TextPrimary)
            Spacer(Modifier.weight(1f))
            if (overview?.worstStatus == BudgetStatus.OVER) {
                Text("⚠️ 已超支", style = MaterialTheme.typography.labelMedium, color = OverColor)
            } else if (overview?.worstStatus == BudgetStatus.WARN) {
                Text("⚠️ 接近上限", style = MaterialTheme.typography.labelMedium, color = WarnColor)
            } else {
                Text(state.monthLabel, style = MaterialTheme.typography.labelMedium, color = AppColors.TextMuted)
            }
        }

        monthly?.let { entry ->
            Spacer(Modifier.height(10.dp))
            BudgetProgressRow(entry, title = "总预算")
        }

        categories.take(4).forEach { entry ->
            Spacer(Modifier.height(8.dp))
            BudgetProgressRow(entry, title = "${entry.label.let { Category.byId(it)?.emoji ?: "" }} ${entry.label.let { Category.byId(it)?.displayName ?: it }}")
        }
        if (categories.size > 4) {
            Spacer(Modifier.height(6.dp))
            Text(
                "另有 ${categories.size - 4} 个分类设了预算",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextMuted,
            )
        }
    }
}

@Composable
private fun BudgetProgressRow(entry: BudgetEntry, title: String) {
    val barColor = when (entry.status) {
        BudgetStatus.OVER -> OverColor
        BudgetStatus.WARN -> WarnColor
        BudgetStatus.OK -> AppColors.Accent
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
            Spacer(Modifier.weight(1f))
            Text(
                "¥${Money.formatYuan(entry.amountCents)} / ¥${Money.formatYuan(entry.limitCents)}（${(entry.percent * 100).toInt()}%）",
                style = MaterialTheme.typography.labelSmall,
                color = if (entry.status == BudgetStatus.OK) AppColors.TextSecondary else barColor,
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { entry.percent.toFloat().coerceIn(0f, 1f) },
            color = barColor,
            trackColor = AppColors.ChipFill,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
        )
    }
}
