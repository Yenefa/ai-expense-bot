package com.expense.tracker.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoDelete
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.model.Category
import com.expense.tracker.data.model.Money
import com.expense.tracker.data.repo.ExpenseRepository
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.iconBtnShadow
import com.expense.tracker.ui.theme.softShadow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 最近删除列表 — 展示所有 deletedAt 不为空的软删除记录。
 *
 * - 每条行：分类 + 备注 + 金额 + 删除时间，右端两个按钮：
 *   ♻ 恢复（置 deletedAt = null）| 🗑 彻底删除（DELETE）
 * - 顶部提示："X 条已删除，N 天后自动清理"（30 天默认）
 * - 恢复或彻底删除都用 shrinkVertically + fadeOut 动画移除该行
 */
@Composable
fun DeletedItemsScreen(repo: ExpenseRepository, onClose: () -> Unit) {
    val deleted by repo.observeDeleted().collectAsState(initial = emptyList())
    val autoPurgeDays = 30 // 默认 30 天
    val zone = remember { ZoneId.systemDefault() }
    val dateFmt = remember { DateTimeFormatter.ofPattern("M月d日 HH:mm") }
    val scope = rememberCoroutineScope()

    // 正在执行恢复/删除的行（用于动画移除）
    var pendingIds by remember { mutableStateOf(setOf<Long>()) }
    var pendingPurge by remember { mutableStateOf<ExpenseEntity?>(null) }

    Box(Modifier.fillMaxSize().background(AppColors.Bg)) {
        Column(Modifier.fillMaxSize()) {
            // 顶部
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .iconBtnShadow()
                        .clip(CircleShape)
                        .background(AppColors.CardBg)
                        .pointerInput(onClose) { detectTapGestures(onTap = { onClose() }) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = AppColors.TextPrimary) }
                Spacer(Modifier.size(12.dp))
                Text("最近删除", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
            }

            if (deleted.isEmpty()) {
                Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text("暂无已删除记录 🧹", color = AppColors.TextMuted, style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                Text(
                    "${deleted.size} 条已删除 · $autoPurgeDays 天后自动清理",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextMuted,
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(deleted, key = { it.id }) { item ->
                        AnimatedVisibility(
                            visible = item.id !in pendingIds,
                            exit = shrinkVertically(animationSpec = tween(280)) +
                                    fadeOut(animationSpec = tween(200)),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .softShadow(elevation = 2.dp, cornerRadius = 14.dp, spotAlpha = 0.06f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(AppColors.CardBg)
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // 分类 + 备注
                                val cat = Category.byIdOrOther(item.categoryId)
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(cat.emoji, style = MaterialTheme.typography.bodyLarge)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            cat.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = AppColors.TextPrimary,
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "¥${Money.formatYuan(item.amountCents)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AppColors.TextPrimary,
                                        )
                                    }
                                    if (item.note.isNotBlank()) {
                                        Text(
                                            item.note,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppColors.TextMuted,
                                        )
                                    }
                                    val deletedTime = Instant.ofEpochMilli(item.deletedAt!!).atZone(zone).format(dateFmt)
                                    Text(
                                        "删除于 $deletedTime",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppColors.TextMuted,
                                    )
                                }
                                // 恢复按钮
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF22C55E).copy(alpha = 0.12f))
                                        .clickable {
                                            pendingIds = pendingIds + item.id
                                            scope.launch { repo.restore(item.id) }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Outlined.Restore,
                                        contentDescription = "恢复",
                                        tint = Color(0xFF16A34A),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Spacer(Modifier.size(8.dp))
                                // 彻底删除按钮
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                                        .clickable {
                                            pendingPurge = item
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Outlined.AutoDelete,
                                        contentDescription = "彻底删除",
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val item = pendingPurge
        if (item != null) {
            val category = Category.byIdOrOther(item.categoryId)
            AlertDialog(
                onDismissRequest = { pendingPurge = null },
                title = { Text("永久删除账目？") },
                text = {
                    Text(
                        "${category.emoji} ${category.displayName} ¥${Money.formatYuan(item.amountCents)}" +
                            if (item.note.isBlank()) "\n删除后无法恢复。" else " · ${item.note}\n删除后无法恢复。",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingPurge = null
                            pendingIds = pendingIds + item.id
                            scope.launch {
                                runCatching { repo.purge(item.id) }
                                    .onFailure { pendingIds = pendingIds - item.id }
                            }
                        },
                    ) {
                        Text("永久删除", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingPurge = null }) { Text("取消") }
                },
            )
        }
    }
}
