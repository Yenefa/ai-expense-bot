package com.expense.tracker.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
                            Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                                day.items.forEach { item ->
                                    SwipeToDeleteRow(
                                        key = item.id,
                                        onDelete = { vm.deleteExpense(item.id) },
                                    ) {
                                        ExpenseRow(item)
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

@Composable
private fun ExpenseRow(item: DisplayExpense) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.Bg)
            .padding(horizontal = 8.dp, vertical = 8.dp),
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

/**
 * 向左滑动揭开右侧红色删除按钮；松手未到达阈值则弹回；到达阈值则进入"待确认"展开态。
 * 点击红色按钮触发删除动画（高度收缩 + 淡出 + spring）。
 *
 * 设计：
 * - 红色背景与前景内容大小完全一致（用 matchParentSize），不会比记录大
 * - 向左拖动灵敏度 ×2，手指轻轻一划就能打开
 * - 揭开宽度 = 屏幕的 1/3（适中，不会盖太多记录）
 * - 双向跟手：揭开后向右拖能滑回去关闭
 */
@Composable
private fun SwipeToDeleteRow(
    key: Long,
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val rowFullWidthDp = configuration.screenWidthDp.dp - 32.dp  // 减去日卡片左右 padding
    // 揭开宽度 = 行宽的 1/3（v1.7 是 1/2，缩小 1/3 → ×2/3）
    val deleteWidthDp = rowFullWidthDp / 3
    val deleteWidthPx = with(density) { deleteWidthDp.toPx() }

    var offsetTarget by remember(key) { mutableStateOf(0.dp) }
    var visible by remember(key) { mutableStateOf(true) }
    val animatedOffset by animateDpAsState(
        targetValue = offsetTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "swipe-offset",
    )

    AnimatedVisibility(
        visible = visible,
        exit = shrinkVertically(animationSpec = tween(280)) +
                androidx.compose.animation.fadeOut(animationSpec = tween(220)),
    ) {
        // Box 让红色背景通过 matchParentSize 自动等于前景内容高度
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        ) {
            // 1. 红色删除按钮先布局（在底层），matchParentSize 跟随后面前景的高度
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    modifier = Modifier
                        .width(deleteWidthDp)
                        .fillMaxHeight()
                        .background(Color(0xFFEF4444))
                        .pointerInput(key) {
                            detectTapGestures(onTap = {
                                visible = false
                                onDelete()
                            })
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "删除",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "删除",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            // 2. 前景内容（在红色上方），跟手左拉揭开下面的红色
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(x = animatedOffset)
                    .background(AppColors.Bg)  // 不透明背景，遮住下方红色
                    .pointerInput(key) {
                        // 记录拖动起始 offset，用于判断"已揭开 → 向右拖"
                        var dragStartOffsetPx = 0f
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragStartOffsetPx = with(density) { animatedOffset.toPx() }
                            },
                            onHorizontalDrag = { _, dx ->
                                // 灵敏度 ×2 — 同样手势距离，offset 翻倍
                                val newPx = (with(density) { animatedOffset.toPx() } + dx * 2f)
                                    .coerceIn(-deleteWidthPx * 1.2f, 0f)
                                offsetTarget = with(density) { newPx.toDp() }
                            },
                            onDragEnd = {
                                val curPx = with(density) { offsetTarget.toPx() }
                                val wasOpen = dragStartOffsetPx <= -deleteWidthPx * 0.9f
                                offsetTarget = when {
                                    // 已揭开状态下向右拖任意距离 → 直接关闭
                                    wasOpen && curPx > dragStartOffsetPx + 8f -> 0.dp
                                    // 未揭开状态下，超过 1/3 阈值 → 打开
                                    !wasOpen && curPx < -deleteWidthPx / 3 -> -deleteWidthDp
                                    // 已揭开但用户没拖动太多 → 保持打开
                                    wasOpen -> -deleteWidthDp
                                    // 其他情况 → 关闭
                                    else -> 0.dp
                                }
                            },
                            onDragCancel = { offsetTarget = 0.dp },
                        )
                    },
            ) {
                content()
            }
        }
    }
}

// (使用 androidx.compose.foundation.layout.offset)
