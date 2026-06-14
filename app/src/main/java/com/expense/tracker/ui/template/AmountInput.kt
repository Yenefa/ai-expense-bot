package com.expense.tracker.ui.template

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.expense.tracker.ui.theme.AppColors

@Composable
fun AmountInput(onSubmit: (Double) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    Row(
        modifier = modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "¥",
            style = MaterialTheme.typography.displayMedium,
            color = AppColors.TextPrimary,
        )
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = text,
                onValueChange = { v -> if (v.matches(Regex("^\\d{0,7}(\\.\\d{0,2})?$"))) text = v },
                singleLine = true,
                textStyle = TextStyle(
                    color = AppColors.TextPrimary,
                    fontSize = MaterialTheme.typography.displayLarge.fontSize,
                ),
                cursorBrush = SolidColor(AppColors.TextPrimary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            if (text.isEmpty()) {
                Text(
                    text = "0",
                    color = AppColors.TextMuted,
                    style = MaterialTheme.typography.displayLarge,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(AppColors.TextPrimary)
                .pointerInput(text) {
                    detectTapGestures(onTap = {
                        val v = text.toDoubleOrNull()
                        if (v != null && v > 0) {
                            onSubmit(v); text = ""
                        }
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.ArrowUpward,
                contentDescription = "提交",
                tint = Color.White,
            )
        }
    }
}
