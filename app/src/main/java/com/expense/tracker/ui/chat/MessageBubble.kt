package com.expense.tracker.ui.chat

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.db.ChatMessageEntity
import com.expense.tracker.ui.theme.AppColors

/**
 * 聊天气泡。
 *
 * 交互：
 *  - 长按 → 复制 message.content 到剪贴板 + Toast 提示
 *  - 单击留空（避免误触触发跳转，未来需要时再扩展）
 *
 * 实现取舍：使用 Modifier.combinedClickable 而非 pointerInput(detectTapGestures)，
 * 是因为它支持 onLongClick 自带触感反馈 + 无障碍 announce。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(message: ChatMessageEntity, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copyAction = {
        clipboard.setText(AnnotatedString(message.content))
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
    }

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
                    .combinedClickable(onClick = {}, onLongClick = { copyAction() })
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
        Text(
            text = message.content,
            color = AppColors.TextPrimary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = { copyAction() }),
        )
    }
}
