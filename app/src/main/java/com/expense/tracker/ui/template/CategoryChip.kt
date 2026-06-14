package com.expense.tracker.ui.template

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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
import com.expense.tracker.data.model.Category
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.softShadow

@Composable
fun CategoryChip(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) AppColors.TextPrimary else AppColors.Bg
    val fg = if (selected) Color.White else AppColors.TextPrimary
    val mod = if (selected) modifier else modifier.softShadow(elevation = 3.dp, cornerRadius = 18.dp, spotAlpha = 0.10f)
    Box(
        modifier = mod
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = "${category.emoji} ${category.displayName}",
            color = fg,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
