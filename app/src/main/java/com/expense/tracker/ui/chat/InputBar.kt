package com.expense.tracker.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.expense.tracker.ui.liquidglass.LiquidGlassButton
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.inputShadow

@Composable
fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    llmEnabled: Boolean,
    onToggleLlm: () -> Unit,
    onSend: (String) -> Unit,
    onPlusClick: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .inputShadow()
            .clip(RoundedCornerShape(28.dp))
            .background(AppColors.Bg)
            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .pointerInput(onPlusClick) { detectTapGestures(onTap = { onPlusClick() }) },
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "新增", tint = AppColors.TextPrimary)
        }
        Box(modifier = Modifier.weight(1f)) {
            if (llmEnabled) {
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = AppColors.TextPrimary,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                    ),
                    cursorBrush = SolidColor(AppColors.TextPrimary),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (text.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = AppColors.TextMuted,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                // 关闭思考模式时禁用输入：占位文本 + 不响应点击 + 不显示光标
                Text(
                    text = placeholder,
                    color = AppColors.TextMuted,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        LiquidGlassButton(on = llmEnabled, onToggle = onToggleLlm)
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (llmEnabled) AppColors.Accent else AppColors.Accent.copy(alpha = 0.35f))
                .pointerInput(text, llmEnabled) {
                    detectTapGestures(onTap = {
                        if (llmEnabled && text.isNotBlank()) {
                            onSend(text)
                        }
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.ArrowUpward, contentDescription = "发送", tint = Color.White)
        }
    }
}
