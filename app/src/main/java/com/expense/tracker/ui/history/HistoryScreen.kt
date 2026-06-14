package com.expense.tracker.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.iconBtnShadow
import com.expense.tracker.ui.theme.softShadow

@Composable
fun HistoryScreen(vm: HistoryViewModel, onBack: () -> Unit) {
    val state by vm.uiState.collectAsState()
    val expandedDays = remember { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier = Modifier.fillMaxSize().background(AppColors.Bg),
    ) {
        // 顶部
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .iconBtnShadow()
                    .clip(CircleShape)
                    .background(AppColors.Bg)
                    .pointerInput(onBack) { detectTapGestures(onTap = { onBack() }) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = AppColors.TextPrimary) }
            Spacer(Modifier.size(12.dp))
            Text("历史明细", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
        }

        if (state.groups.isEmpty()) {
            Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text("还没有记录，去记一笔吧 📝", color = AppColors.TextMuted, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.groups, key = { it.dateLabel }) { day ->
                    val expanded = expandedDays[day.dateLabel] ?: false
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .softShadow(elevation = 2.dp, cornerRadius = 16.dp, spotAlpha = 0.06f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppColors.Bg)
                            .clickable { expandedDays[day.dateLabel] = !expanded },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(day.dateLabel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("¥", style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
                                Text(
                                    "%.2f".format(day.total),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = AppColors.TextPrimary,
                                )
                            }
                        }
                        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                                day.items.forEach { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(item.categoryEmoji, style = MaterialTheme.typography.bodyLarge)
                                            Spacer(Modifier.width(8.dp))
                                            Column {
                                                Text(item.categoryName, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextPrimary)
                                                if (item.note.isNotBlank()) {
                                                    Text(item.note, style = MaterialTheme.typography.labelSmall, color = AppColors.TextMuted)
                                                }
                                            }
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(item.timeLabel, style = MaterialTheme.typography.labelSmall, color = AppColors.TextMuted)
                                            Spacer(Modifier.width(8.dp))
                                            Text("¥%.2f".format(item.amount), style = MaterialTheme.typography.bodyMedium, color = AppColors.TextPrimary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
