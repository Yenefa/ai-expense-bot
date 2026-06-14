package com.expense.tracker.ui.chat

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.db.ChatMessageEntity
import com.expense.tracker.ui.theme.AppColors

@Composable
fun MessageBubble(message: ChatMessageEntity, modifier: Modifier = Modifier) {
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
            modifier = modifier.fillMaxWidth(),
        )
    }
}
