package com.expense.tracker.ui.trash

import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.iconBtnShadow
import com.expense.tracker.ui.theme.softShadow

/**
 * 最近删除页面。
 *
 * 设计取舍：
 * - 不用滑动手势区分恢复/真删 — 用户已经在情绪不好（误删）的状态了，再做 mental model 切换不友好
 * - 改用每行两个明确按钮：[绿底·恢复] [灰底·彻底删除]
 * - 顶部有"全部清空"次要按钮（小字、警示色），点了走二次确认 Dialog
 * - 显示"还剩 N 天" — 让用户知道紧迫性
 */
@Composable
fun TrashScreen(vm: TrashViewModel, onBack: () -> Unit) {
    val state by vm.uiState.collectAsState()
    var showEmptyDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(AppColors.Bg),
    ) {
        // 顶栏
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
            Text("最近删除", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
            Spacer(Modifier.weight(1f))
            if (state.items.isNotEmpty()) {
                TextButton(onClick = { showEmptyDialog = true }) {
                    Text("全部清空", color = Color(0xFFE5484D), style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Text(
            "已删除的支出在此保留 ${TrashViewModel.TRASH_RETENTION_DAYS} 天，过期自动清除。",
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextMuted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        if (state.items.isEmpty()) {
            Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text("最近删除是空的 ✨", color = AppColors.TextMuted, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.items, key = { it.id }) { item ->
                    TrashRow(
                        item = item,
                        onRestore = { vm.restore(item.id) },
                        onHardDelete = { vm.hardDelete(item.id) },
                    )
                }
            }
        }
    }

    if (showEmptyDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyDialog = false },
            title = { Text("清空最近删除？") },
            text = { Text("将彻底删除 ${state.items.size} 笔记录，此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showEmptyDialog = false
                    vm.emptyTrash()
                }) { Text("彻底清空", color = Color(0xFFE5484D)) }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun TrashRow(
    item: TrashItem,
    onRestore: () -> Unit,
    onHardDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(elevation = 2.dp, cornerRadius = 16.dp, spotAlpha = 0.06f)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Bg)
            .padding(14.dp),
    ) {
        // 第一行：分类 + 金额
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.categoryEmoji, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Text(item.categoryName, style = MaterialTheme.typography.bodyLarge, color = AppColors.TextPrimary)
            if (item.note.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text("· ${item.note}", style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
            }
            Spacer(Modifier.weight(1f))
            Text("¥%.2f".format(item.amount),
                 style = MaterialTheme.typography.titleMedium,
                 fontWeight = FontWeight.SemiBold,
                 color = AppColors.TextPrimary)
        }
        // 第二行：原时间 / 删除时间 / 还剩
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("原时间 ${item.occurredAtLabel}",
                 style = MaterialTheme.typography.labelSmall, color = AppColors.TextMuted)
            Spacer(Modifier.width(10.dp))
            Text("· 删除于 ${item.deletedAtLabel}",
                 style = MaterialTheme.typography.labelSmall, color = AppColors.TextMuted)
            Spacer(Modifier.weight(1f))
            val urgent = item.daysUntilPurge <= 3
            Text("剩 ${item.daysUntilPurge} 天",
                 style = MaterialTheme.typography.labelSmall,
                 color = if (urgent) Color(0xFFE5484D) else AppColors.TextSecondary,
                 fontWeight = if (urgent) FontWeight.SemiBold else FontWeight.Normal)
        }
        // 第三行：操作按钮
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            ActionButton(
                label = "彻底删除",
                color = AppColors.TextSecondary,
                onClick = onHardDelete,
            )
            Spacer(Modifier.width(8.dp))
            ActionButton(
                label = "↻ 恢复",
                color = Color(0xFF22C55E),
                onClick = onRestore,
            )
        }
    }
}

@Composable
private fun ActionButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color)
            .pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium,
             fontWeight = FontWeight.Medium)
    }
}
