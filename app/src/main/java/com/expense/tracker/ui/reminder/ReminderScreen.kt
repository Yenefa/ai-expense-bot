package com.expense.tracker.ui.reminder

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.reminder.DailyReminderWorker
import com.expense.tracker.data.reminder.ReminderPrefs
import com.expense.tracker.data.reminder.ReminderScheduler
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.iconBtnShadow
import kotlinx.coroutines.launch

@Composable
fun ReminderScreen(
    prefs: ReminderPrefs,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snap by prefs.snapshot.collectAsState(initial = null)
    var userEdited by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(snap?.enabled ?: false) }
    var hour by remember { mutableStateOf(snap?.hour ?: 21) }
    var minute by remember { mutableStateOf(snap?.minute ?: 0) }
    var timeLabel by remember { mutableStateOf(snap?.timeLabel ?: "21:00") }
    var notice by remember { mutableStateOf("") }

    // 快照到达前先显示默认值；加载完成后若用户尚未手动修改，则同步为已保存的设置
    LaunchedEffect(snap) {
        snap?.let { s ->
            if (!userEdited) {
                enabled = s.enabled
                hour = s.hour
                minute = s.minute
                timeLabel = s.timeLabel
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notice = if (granted) "通知权限已开启" else "未授予通知权限，提醒将无法显示"
    }

    fun requestPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(Modifier.fillMaxSize().background(AppColors.Bg)) {
        Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .iconBtnShadow()
                        .clip(CircleShape)
                        .background(AppColors.CardBg)
                        .pointerInput(onBack) { detectTapGestures(onTap = { onBack() }) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = AppColors.TextPrimary)
                }
                Spacer(Modifier.size(12.dp))
                Text("记账提醒", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "每天在固定时间提醒你补记当天的账。提醒内容会带上今日已记笔数和金额。",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
            )

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("每日提醒", style = MaterialTheme.typography.titleMedium, color = AppColors.TextPrimary)
                    Text(
                        if (enabled) "开启 · 每天 $timeLabel" else "关闭",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextSecondary,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { checked ->
                        userEdited = true
                        enabled = checked
                        if (checked) requestPermissionIfNeeded()
                    },
                )
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, h, m ->
                            userEdited = true
                            hour = h
                            minute = m
                            timeLabel = "%02d:%02d".format(java.util.Locale.US, h, m)
                        },
                        hour,
                        minute,
                        true,
                    ).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.CardBg, contentColor = AppColors.TextPrimary),
                shape = RoundedCornerShape(14.dp),
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("选择提醒时间：$timeLabel", fontWeight = FontWeight.SemiBold) }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    scope.launch {
                        prefs.save(enabled, hour, minute)
                        if (enabled) {
                            ReminderScheduler.schedule(context, hour, minute)
                        } else {
                            ReminderScheduler.cancel(context)
                        }
                        notice = if (enabled) "提醒已开启（每天 $timeLabel）" else "提醒已关闭"
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.TextPrimary, contentColor = AppColors.Bg),
                shape = RoundedCornerShape(14.dp),
                enabled = snap != null,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("保存设置", fontWeight = FontWeight.SemiBold) }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    requestPermissionIfNeeded()
                    DailyReminderWorker.notify(context, "这是一条测试提醒：Y.E cost 提醒功能正常。")
                    notice = "测试通知已发送（通知栏可查看）"
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("立即发送测试提醒") }

            notice.let { if (it.isNotEmpty()) Spacer(Modifier.height(10.dp)) }
            notice.let { if (it.isNotEmpty()) Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.Accent) }

            Spacer(Modifier.height(20.dp))
            Text(
                "提醒通过系统通知发送，请允许通知权限；重启手机后提醒仍会自动恢复。",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextMuted,
            )
        }
    }
}
