package com.expense.tracker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.expense.tracker.data.prefs.ThemeMode
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.softShadow

/**
 * 主题模式选择对话框：浅色 / 深色 / 跟随系统。
 * 风格与设置菜单的 MenuRow 一致（emoji + 标题 + 副标题 + 右侧勾选）。
 */
@Composable
fun ThemePickerDialog(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .softShadow(elevation = 8.dp, cornerRadius = 20.dp, spotAlpha = 0.12f)
                .clip(RoundedCornerShape(20.dp))
                .background(AppColors.Bg)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "深色模式",
                style = MaterialTheme.typography.titleLarge,
                color = AppColors.TextPrimary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            ThemeMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelect(mode) }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(mode.emoji, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(mode.label, style = MaterialTheme.typography.bodyLarge, color = AppColors.TextPrimary)
                        Text(mode.desc, style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
                    }
                    if (mode == current) {
                        Icon(Icons.Outlined.Check, contentDescription = "已选中", tint = AppColors.Accent)
                    }
                }
            }
        }
    }
}
