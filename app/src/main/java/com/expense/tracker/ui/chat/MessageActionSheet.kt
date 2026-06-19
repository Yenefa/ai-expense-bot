package com.expense.tracker.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.db.ChatMessageEntity
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.softShadow

/**
 * 长按消息后弹出的底部操作 sheet — 当前只有"复制"。
 *
 * 编辑消息已废弃（v2.7 起）：聊天里的消息只是"对话存档"，真正的数据编辑应直接在
 * 历史明细页对 expense 行操作 → ExpenseEditDialog。
 *
 * 动画：从底部 spring 弹入，半透明遮罩。
 */
@Composable
fun MessageActionSheet(
    message: ChatMessageEntity?,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
) {
    val visible = message != null
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(180)) +
            slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                initialOffsetY = { it },
            ),
        exit = fadeOut(animationSpec = tween(150)) +
            slideOutVertically(
                animationSpec = tween(180),
                targetOffsetY = { it },
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .softShadow(elevation = 12.dp, cornerRadius = 22.dp, spotAlpha = 0.16f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(AppColors.Bg)
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        onClick = { /* swallow */ },
                    )
                    .padding(vertical = 8.dp),
            ) {
                ActionRow(
                    icon = Icons.Outlined.ContentCopy,
                    label = "复制",
                    onClick = onCopy,
                )
                Spacer(Modifier.size(4.dp))
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = AppColors.TextPrimary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            color = AppColors.TextPrimary,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.width(0.dp))
    }
}
