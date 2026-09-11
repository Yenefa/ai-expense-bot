package com.expense.tracker.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.expense.tracker.data.export.BackupData
import com.expense.tracker.data.export.DataExporter
import com.expense.tracker.data.importer.CsvExpenseImporter
import com.expense.tracker.data.importer.CsvImportResult
import com.expense.tracker.data.importer.PlatformCsvImporter
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
 * 数据导入与导出页 — 入口在 SettingsMenuScreen 的 "📁 数据导入与导出"。
 *
 * 提供三个选项：
 * - 导入 CSV：读取本应用导出的记账 CSV，预览后写入并跳过重复记录
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
    var busyLabel by remember { mutableStateOf("处理中…") }
    var pendingImport by remember { mutableStateOf<CsvImportResult?>(null) }
    var pendingBackup by remember { mutableStateOf<BackupData?>(null) }

    // 待导出的内容缓存 — 用户点了选项后，等系统文件选择器回调时把内容写入
    var pendingPayload by remember { mutableStateOf<String?>(null) }
    var lastSummary by remember { mutableStateOf<String?>(null) }

    val handleSaveResult: (Uri?) -> Unit = { uri ->
        val payload = pendingPayload
        pendingPayload = null
        if (uri == null || payload == null) {
            busy = false
        } else {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            os.write(payload.toByteArray(Charsets.UTF_8))
                        } ?: error("无法打开目标文件")
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
    }

    val backupSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { handleSaveResult(it) }

    val csvSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { handleSaveResult(it) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        busyLabel = "读取 CSV…"
        scope.launch {
            val parsed = runCatching {
                withContext(Dispatchers.IO) {
                    val csv = context.contentResolver.openInputStream(uri)?.use { input ->
                        readCsvText(input)
                    }
                        ?: error("无法读取所选文件")
                    if (PlatformCsvImporter.looksLikePlatformCsv(csv)) {
                        PlatformCsvImporter.parse(csv)
                    } else {
                        CsvExpenseImporter.parse(csv)
                    }
                }
            }
            busy = false
            parsed.onSuccess { pendingImport = it }
                .onFailure {
                    Toast.makeText(
                        context,
                        "导入失败：${it.message ?: "CSV 格式不正确"}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
        }
    }

    val backupImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        busyLabel = "校验完整备份…"
        scope.launch {
            val parsed = runCatching {
                withContext(Dispatchers.IO) {
                    val content = context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    }
                        ?: error("无法读取所选文件")
                    container.backupRepo.parseBackup(content)
                }
            }
            busy = false
            parsed.onSuccess { pendingBackup = it }
                .onFailure {
                    Toast.makeText(
                        context,
                        "备份校验失败：${it.message ?: "文件格式不正确"}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
        }
    }

    Box(Modifier.fillMaxSize().background(AppColors.Bg)) {
        Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
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
                        .background(AppColors.CardBg)
                        .pointerInput(onClose) { detectTapGestures(onTap = { onClose() }) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = AppColors.TextPrimary) }
                Spacer(Modifier.size(12.dp))
                Text("数据导入与导出", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
            }
            Spacer(Modifier.size(16.dp))

            Text(
                "从完整备份恢复全部本地数据，或用 CSV 导入导出账目。所有操作都在手机本地完成。",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
            )
            Spacer(Modifier.size(20.dp))

            DataOption(
                emoji = "♻️",
                title = "恢复完整备份",
                subtitle = "校验后覆盖当前账目、回收站、聊天、长期记忆和非敏感设置",
                enabled = !busy,
                onClick = {
                    backupImportLauncher.launch(
                        arrayOf("application/json", "text/json", "text/plain", "application/octet-stream"),
                    )
                },
            )
            Spacer(Modifier.size(10.dp))
            DataOption(
                emoji = "📥",
                title = "导入 CSV",
                subtitle = "支持微信 / 支付宝账单 CSV 与本应用导出 CSV，自动跳过重复账目",
                enabled = !busy,
                onClick = {
                    importLauncher.launch(
                        arrayOf("text/*", "application/csv", "application/vnd.ms-excel"),
                    )
                },
            )
            Spacer(Modifier.size(10.dp))
            DataOption(
                emoji = "🗂",
                title = "导出 JSON（完整）",
                subtitle = "包含全部账目（含回收站）、聊天、长期记忆和非敏感设置",
                enabled = !busy,
                onClick = {
                    busy = true
                    busyLabel = "准备导出…"
                    scope.launch {
                        val nowMs = System.currentTimeMillis()
                        val isoNow = Instant.ofEpochMilli(nowMs).toString()
                        val versionName = runCatching {
                            context.packageManager.getPackageInfo(context.packageName, 0).versionName
                        }.getOrNull() ?: "?"
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                container.backupRepo.createBackup(versionName, isoNow)
                            }
                        }
                        result.onSuccess { json ->
                            pendingPayload = json
                            lastSummary = "完整备份已导出"
                            backupSaveLauncher.launch("expense-tracker-backup-${defaultStamp(nowMs)}.json")
                        }.onFailure {
                            busy = false
                            Toast.makeText(
                                context,
                                "备份失败：${it.message ?: "无法读取本地数据"}",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                },
            )
            Spacer(Modifier.size(10.dp))
            DataOption(
                emoji = "📊",
                title = "导出 CSV（仅记账）",
                subtitle = "仅 expenses 表，常用于 Excel 打开",
                enabled = !busy,
                onClick = {
                    busy = true
                    busyLabel = "准备导出…"
                    scope.launch {
                        val expenses = container.expenseRepo.getAllActiveOnce()
                        val nowMs = System.currentTimeMillis()
                        val csv = DataExporter.toCsv(expenses)
                        pendingPayload = csv
                        lastSummary = "已导出 ${expenses.size} 笔记账（CSV）"
                        val name = "expense-tracker-export-${defaultStamp(nowMs)}.csv"
                        csvSaveLauncher.launch(name)
                    }
                },
            )

            Spacer(Modifier.size(20.dp))
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(busyLabel, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
                }
            }

            Spacer(Modifier.size(24.dp))
            Text(
                "提示：完整备份可恢复全部账目、回收站、聊天和长期记忆；API Key 不会写入明文文件。",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextMuted,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        val preview = pendingImport
        if (preview != null) {
            AlertDialog(
                onDismissRequest = { if (!busy) pendingImport = null },
                title = { Text("确认导入 CSV") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "可导入 ${preview.expenses.size} 笔记账",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        val from = preview.minOccurredAt
                        val to = preview.maxOccurredAt
                        if (from != null && to != null) {
                            Text(
                                "日期范围：${displayDate(from)} 至 ${displayDate(to)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppColors.TextSecondary,
                            )
                        }
                        Text(
                            if (preview.issues.isEmpty()) {
                                "未发现无效记录"
                            } else {
                                "另有 ${preview.issues.size} 条无效记录，将不会导入"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.TextSecondary,
                        )
                        Text(
                            "已有账目和文件内重复账目会自动跳过。",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.TextMuted,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = preview.expenses.isNotEmpty() && !busy,
                        onClick = {
                            pendingImport = null
                            busy = true
                            busyLabel = "正在导入…"
                            scope.launch {
                                val result = runCatching {
                                    withContext(Dispatchers.IO) {
                                        container.expenseRepo.importExpenses(preview.expenses)
                                    }
                                }
                                busy = false
                                result.onSuccess { summary ->
                                    val invalid = preview.issues.size
                                    val message = buildString {
                                        append("已导入 ${summary.inserted} 笔")
                                        if (summary.skippedDuplicates > 0) {
                                            append("，跳过 ${summary.skippedDuplicates} 笔重复账目")
                                        }
                                        if (invalid > 0) append("，忽略 $invalid 条无效记录")
                                    }
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                }.onFailure {
                                    Toast.makeText(
                                        context,
                                        "导入失败：${it.message ?: "无法写入记账数据"}",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        },
                    ) { Text("导入") }
                },
                dismissButton = {
                    TextButton(
                        enabled = !busy,
                        onClick = { pendingImport = null },
                    ) { Text("取消") }
                },
            )
        }

        val backup = pendingBackup
        if (backup != null) {
            AlertDialog(
                onDismissRequest = { if (!busy) pendingBackup = null },
                title = { Text("确认恢复完整备份") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${backup.expenses.size} 笔记账（含 ${backup.expenses.count { it.deletedAt != null }} 笔已删除）",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "${backup.chatMessages.size} 条聊天 · 来源版本 ${backup.sourceAppVersion}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.TextSecondary,
                        )
                        Text(
                            "恢复会覆盖当前全部账目、回收站、聊天、长期记忆和非敏感设置，无法撤销。当前设备上的 API Key 会保留。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            pendingBackup = null
                            busy = true
                            busyLabel = "正在恢复完整备份…"
                            scope.launch {
                                val result = runCatching {
                                    withContext(Dispatchers.IO) { container.backupRepo.restore(backup) }
                                }
                                busy = false
                                result.onSuccess {
                                    Toast.makeText(
                                        context,
                                        "恢复完成：${backup.expenses.size} 笔记账 / ${backup.chatMessages.size} 条聊天",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }.onFailure {
                                    Toast.makeText(
                                        context,
                                        "恢复失败：${it.message ?: "无法写入本地数据"}",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        },
                    ) { Text("覆盖并恢复", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(enabled = !busy, onClick = { pendingBackup = null }) {
                        Text("取消")
                    }
                },
            )
        }
    }
}

@Composable
private fun DataOption(
    emoji: String,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val rowColor = AppColors.CardBg
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

private fun displayDate(timeMs: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return fmt.format(Date(timeMs))
}

/** 读取 CSV 文本：优先 UTF-8（含 BOM），失败则按 GBK（微信导出常见编码）。 */
private fun readCsvText(input: java.io.InputStream): String {
    val bytes = input.readBytes()
    val utf8 = runCatching {
        val text = String(bytes, Charsets.UTF_8)
        if (text.contains('\uFFFD')) throw IllegalArgumentException("not utf8")
        text
    }.getOrNull()
    return utf8?.trimStart('\uFEFF')
        ?: String(bytes, java.nio.charset.Charset.forName("GBK")).trimStart('\uFEFF')
}
