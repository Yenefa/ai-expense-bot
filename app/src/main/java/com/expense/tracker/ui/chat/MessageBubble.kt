package com.expense.tracker.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.db.ChatMessageEntity
import com.expense.tracker.ui.theme.AppColors

@Composable
fun MessageBubble(
    message: ChatMessageEntity,
    onLongPress: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (message.role == "user") {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(AppColors.ChipFill)
                    .pointerInput(message.id) {
                        // 长按打开复制/编辑菜单（仅 user 消息可触发，但 assistant 复制也允许）
                        detectTapGestures(onLongPress = { onLongPress() })
                    }
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            ) {
                Text(
                    text = message.content,
                    color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    } else {
        // assistant 消息：长按只能复制，不能编辑（保留 LLM 回复真实性）
        Text(
            text = message.content,
            color = AppColors.TextPrimary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = modifier
                .fillMaxWidth()
                .pointerInput(message.id) {
                    detectTapGestures(onLongPress = { onLongPress() })
                },
        )
    }
}
