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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.prefs.ThemeMode
import com.expense.tracker.data.prefs.UserPrefs
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.iconBtnShadow
import com.expense.tracker.ui.theme.softShadow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun SettingsMenuScreen(
    onClose: () -> Unit,
    onOpenSubscription: () -> Unit,
    onOpenLlmSettings: () -> Unit,
    onOpenDataExport: () -> Unit = {},
    onOpenDeletedItems: () -> Unit = {},
    onOpenUserManual: () -> Unit = {},
    onOpenBudget: () -> Unit = {},
    onOpenReminder: () -> Unit = {},
    onOpenRecurring: () -> Unit = {},
    prefs: UserPrefs,
) {
    val snackbarHost = remember { SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = LocalContext.current
    val snap by prefs.snapshot.collectAsState(initial = null)
    val themeMode = snap?.themeMode ?: ThemeMode.SYSTEM
    var showThemePicker by remember { mutableStateOf(false) }
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
                        .background(AppColors.CardBg)
                        .pointerInput(onClose) { detectTapGestures(onTap = { onClose() }) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = AppColors.TextPrimary) }
                Spacer(Modifier.size(12.dp))
                Text("设置", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
            }

            Spacer(Modifier.size(24.dp))

            MenuRow(emoji = "✨", title = "AI 会员", subtitle = "兑换 30 天测试订阅", onClick = onOpenSubscription)
            MenuRow(emoji = "🧠", title = "自定义 LLM", subtitle = "使用自己的 API 地址、密钥和模型", onClick = onOpenLlmSettings)
            MenuRow(emoji = "🌗", title = "深色模式", subtitle = themeMode.label, onClick = { showThemePicker = true })
            MenuRow(emoji = "📊", title = "预算管理", subtitle = "月度/分类预算 · 90% 预警与超支提醒", onClick = onOpenBudget)
            MenuRow(emoji = "🔔", title = "智能提醒", subtitle = "每日补记提醒 · 通知栏提醒", onClick = onOpenReminder)
            MenuRow(emoji = "🔁", title = "周期账单", subtitle = "房租 / 订阅 / 工资自动生成", onClick = onOpenRecurring)
            MenuRow(
                emoji = "📁",
                title = "数据导入与导出",
                subtitle = "CSV 导入 · JSON / CSV 本地导出",
                onClick = onOpenDataExport,
            )
            MenuRow(emoji = "🗑", title = "最近删除", subtitle = "30 天内可恢复的已删除记录", onClick = onOpenDeletedItems)
            MenuRow(emoji = "📖", title = "软件说明书", subtitle = "了解所有功能与交互细节", onClick = onOpenUserManual)
            MenuRow(emoji = "ℹ️", title = "关于 Y.E cost", subtitle = "v$versionName · ChatGPT 风格 · 本地 SQLite", onClick = {
                scope.launch { snackbarHost.showSnackbar("Y.E cost v$versionName — 对话式智能记账") }
            })

            Spacer(Modifier.weight(1f))
            Text(
                "© 2026 Y.E cost · 数据仅存储在手机本地",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextMuted,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        if (showThemePicker) {
            ThemePickerDialog(
                current = themeMode,
                onSelect = { mode ->
                    scope.launch { prefs.setThemeMode(mode) }
                    showThemePicker = false
                },
                onDismiss = { showThemePicker = false },
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
    val bg = AppColors.CardBg

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
