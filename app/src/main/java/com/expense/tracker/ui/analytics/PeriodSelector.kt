package com.expense.tracker.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.model.Period
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.softShadow

@Composable
fun PeriodSelector(current: Period, onSelect: (Period) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(8.dp)
            .softShadow(elevation = 2.dp, cornerRadius = 22.dp, spotAlpha = 0.06f)
            .clip(RoundedCornerShape(22.dp))
            .background(AppColors.ChipFill)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Period.values().forEach { p ->
            val on = p == current
            val label = when (p) { Period.Week -> "周"; Period.Month -> "月"; Period.Year -> "年" }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (on) AppColors.Bg else Color.Transparent)
                    .pointerInput(p) { detectTapGestures(onTap = { onSelect(p) }) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text(
                    label,
                    color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
