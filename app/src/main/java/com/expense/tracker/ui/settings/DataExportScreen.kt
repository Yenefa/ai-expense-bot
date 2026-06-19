package com.expense.tracker.ui.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.expense.tracker.ExpenseApp
import com.expense.tracker.data.export.DataExporter
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.iconBtnShadow
import com.expense.tracker.ui.theme.softShadow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

/**
 * 数据导出页 — 入口在 SettingsMenuScreen 的 "📁 数据导出"。
 *
 * 提供两个选项：
 * - JSON：完整数据（expenses + chat_messages），后续可用于跨设备迁移
 * - CSV：仅 expenses 表，便于 Excel 处理
 *
 * 用 ActivityResultContracts.CreateDocument 让用户选保存位置（系统文件选择器）。
 */
@Composable
fun DataExportScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val container = remember { (context.applicationContext as ExpenseApp).container }
    var busy by remember { mutableStateOf(false) }

    // 待导出的内容缓存 — 用户点了选项后，等系统文件选择器回调时把内容写入
    var pendingPayload by remember { mutableStateOf<Pair<String, String>?>(null) } // (mime, content)
    var lastSummary by remember { mutableStateOf<String?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri: Uri? ->
        val payload = pendingPayload
        pendingPayload = null
        if (uri == null || payload == null) {
            busy = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(payload.second.toByteArray(Charsets.UTF_8))
                    }
                }.isSuccess
            }
            busy = false
            Toast.makeText(
                context,
                if (ok) (lastSummary ?: "导出完成") else "导出失败：无法写入文件",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    Box(Modifier.fillMaxSize().background(AppColors.Bg)) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            // 顶部栏
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
                Text("数据导出", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
            }
            Spacer(Modifier.size(16.dp))

            Text(
                "将本地记账数据导出为文件。所有数据不会上传到任何云端，仅写入你选择的本地位置。",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
            )
            Spacer(Modifier.size(20.dp))

            ExportOption(
                emoji = "🗂",
                title = "导出 JSON（完整）",
                subtitle = "包含全部记账 + 聊天消息，便于跨设备迁移",
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val expenses = container.expenseRepo.getAllOnce()
                        val chats = container.chatRepo.getAllOnce()
                        val nowMs = System.currentTimeMillis()
                        val isoNow = Instant.ofEpochMilli(nowMs).toString()
                        val versionName = runCatching {
                            context.packageManager.getPackageInfo(context.packageName, 0).versionName
                        }.getOrNull() ?: "?"
                        val json = DataExporter.toJson(expenses, chats, versionName, isoNow)
                        pendingPayload = "application/json" to json
                        lastSummary = "已导出 ${expenses.size} 笔记账 / ${chats.size} 条聊天"
                        val name = "expense-tracker-export-${defaultStamp(nowMs)}.json"
                        saveLauncher.launch(name)
                    }
                },
            )
            Spacer(Modifier.size(10.dp))
            ExportOption(
                emoji = "📊",
                title = "导出 CSV（仅记账）",
                subtitle = "仅 expenses 表，常用于 Excel 打开",
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val expenses = container.expenseRepo.getAllOnce()
                        val nowMs = System.currentTimeMillis()
                        val csv = DataExporter.toCsv(expenses)
                        pendingPayload = "text/csv" to csv
                        lastSummary = "已导出 ${expenses.size} 笔记账（CSV）"
                        val name = "expense-tracker-export-${defaultStamp(nowMs)}.csv"
                        saveLauncher.launch(name)
                    }
                },
            )

            Spacer(Modifier.size(20.dp))
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("导出中…", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
                }
            }

            Spacer(Modifier.weight(1f))
            Text(
                "提示：API Key 等敏感配置不会被导出。",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextMuted,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun ExportOption(
    emoji: String,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val rowColor = if (enabled) AppColors.Bg else AppColors.Bg
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(elevation = 2.dp, cornerRadius = 16.dp, spotAlpha = 0.06f)
            .clip(RoundedCornerShape(16.dp))
            .background(rowColor)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) AppColors.TextPrimary else AppColors.TextMuted,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextSecondary,
            )
        }
    }
}

private fun defaultStamp(nowMs: Long): String {
    val fmt = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    return fmt.format(Date(nowMs))
}
