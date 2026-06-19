package com.expense.tracker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.iconBtnShadow
import com.expense.tracker.ui.theme.softShadow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun SettingsMenuScreen(
    onClose: () -> Unit,
    onOpenLlmSettings: () -> Unit,
    onOpenDataExport: () -> Unit = {},
) {
    val snackbarHost = remember { SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = LocalContext.current
    // 从 PackageManager 读取真实 versionName，避免硬编码不同步
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        }.getOrDefault("?")
    }

    Box(Modifier.fillMaxSize().background(AppColors.Bg)) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            // 左上返回
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .iconBtnShadow()
                        .clip(CircleShape)
                        .background(AppColors.Bg)
                        .pointerInput(onClose) { detectTapGestures(onTap = { onClose() }) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = AppColors.TextPrimary) }
                Spacer(Modifier.size(12.dp))
                Text("设置", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
            }

            Spacer(Modifier.size(24.dp))

            MenuRow(emoji = "🧠", title = "LLM 设置", subtitle = "配置 API 地址、密钥和模型", onClick = onOpenLlmSettings)
            MenuRow(emoji = "🌗", title = "深色模式", subtitle = "即将上线", onClick = { /* TODO */ })
            MenuRow(emoji = "📊", title = "预算管理", subtitle = "即将上线", onClick = { /* TODO */ })
            MenuRow(emoji = "🔔", title = "智能提醒", subtitle = "即将上线", onClick = { /* TODO */ })
            MenuRow(emoji = "📁", title = "数据导出", subtitle = "JSON / CSV 本地导出", onClick = onOpenDataExport)
            MenuRow(emoji = "ℹ️", title = "关于记账助手", subtitle = "v$versionName · ChatGPT 风格 · 本地 SQLite", onClick = {
                scope.launch { snackbarHost.showSnackbar("记账助手 v$versionName — 对话式智能记账") }
            })

            Spacer(Modifier.weight(1f))
            Text(
                "© 2026 记账助手 · 数据仅存储在手机本地",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextMuted,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }
}

@Composable
private fun MenuRow(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    val enabled = subtitle != "即将上线"
    val fg = if (enabled) AppColors.TextPrimary else AppColors.TextMuted
    val bg = if (enabled) AppColors.Bg else AppColors.Bg

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(elevation = 1.dp, cornerRadius = 16.dp, spotAlpha = 0.04f)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = fg)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = if (enabled) AppColors.TextSecondary else AppColors.TextMuted)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = fg.copy(alpha = 0.4f))
    }
}
