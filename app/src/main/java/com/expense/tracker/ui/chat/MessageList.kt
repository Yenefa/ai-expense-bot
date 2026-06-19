package com.expense.tracker.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.db.ChatMessageEntity
import com.expense.tracker.ui.theme.AppColors

@Composable
fun MessageList(
    messages: List<ChatMessageEntity>,
    thinking: Boolean = false,
    streamingText: String? = null,
    onLongPressMessage: (ChatMessageEntity) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // 关键修复 v2.5：listState 用 rememberSaveable 跨 AnimatedContent 重组持久化，
    // 返回 ChatScreen 时恢复到之前的滚动位置，避免"从顶滑到底"的冗余动画。
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    // hasComposedOnce: remember 每次 ChatScreen 重新 mount 都会重置为 false
    // 第一次组合时为 false → 不动画（瞬时定位到底，没有视觉冗余）
    // 后续因新消息/思考态/流式文字变化触发的 effect → 为 true → 动画到底（自然跟随）
    val hasComposedOnce = remember { booleanArrayOf(false) }

    LaunchedEffect(messages.size, thinking, streamingText?.length) {
        val totalItems = messages.size + (if (thinking || streamingText != null) 1 else 0)
        if (totalItems == 0) return@LaunchedEffect

        if (!hasComposedOnce[0]) {
            // 首次组合（含从子页面返回主页面的 remount）：直接瞬时定位到末尾，不动画
            listState.scrollToItem(totalItems - 1)
            hasComposedOnce[0] = true
        } else {
            // 真正有新内容时才动画
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        items(items = messages, key = { it.id }) { msg ->
            MessageBubble(message = msg, onLongPress = { onLongPressMessage(msg) })
        }
        // 流式字符（边收边显），优先于 thinking 显示
        if (streamingText != null) {
            item(key = "streaming") {
                StreamingBubble(text = streamingText)
            }
        } else if (thinking) {
            item(key = "thinking") {
                ThinkingIndicator()
            }
        }
    }
}

/** 流式打字气泡 — assistant 风格（无背景纯文本，跟正式 bubble 一致） */
@Composable
private fun StreamingBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(
            text = text,
            color = AppColors.TextPrimary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f, fill = false),
        )
        // 闪烁的光标
        BlinkingCursor(modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
    }
}

@Composable
private fun BlinkingCursor(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursor-alpha",
    )
    Box(
        modifier = modifier
            .size(width = 2.dp, height = 18.dp)
            .alpha(alpha)
            .background(AppColors.TextPrimary),
    )
}

/** 思考中三点跳动动画 — 三个圆点依次起伏。 */
@Composable
private fun ThinkingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 三个圆点用同一个 InfiniteTransition，但相位错开
        val transition = rememberInfiniteTransition(label = "thinking")
        repeat(3) { i ->
            val scale by transition.animateFloat(
                initialValue = 0.6f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                    // 通过 initialStartOffsetMillis 让三个点错开 200ms
                    initialStartOffset = androidx.compose.animation.core.StartOffset(
                        offsetMillis = i * 200,
                    ),
                ),
                label = "dot-scale-$i",
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(AppColors.TextSecondary),
            )
        }
    }
}
