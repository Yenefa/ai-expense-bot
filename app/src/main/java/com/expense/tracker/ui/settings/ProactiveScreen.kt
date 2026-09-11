package com.expense.tracker.ui.settings

import android.Manifest
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.expense.tracker.proactive.ProactiveAlertType
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.iconBtnShadow
import com.expense.tracker.ui.theme.softShadow

/** 主动提醒开关：规则决定是否提醒，这里只让用户逐类关闭。 */
@Composable
fun ProactiveScreen(vm: ProactiveViewModel, onClose: () -> Unit) {
    val enabled by vm.enabled.collectAsState()
    val context = LocalContext.current
    var notice by remember { mutableStateOf("") }
    var permissionGranted by remember {
        mutableStateOf(com.expense.tracker.proactive.ProactiveNotifier.canPost(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        notice = if (granted) "通知权限已开启" else "未授予通知权限：提醒仍会显示在聊天与提醒中心，但不会发系统通知"
    }

    fun requestPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(Modifier.fillMaxSize().background(AppColors.Bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                Text("主动提醒", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
            }

            Spacer(Modifier.size(8.dp))
            Text(
                "由本地规则判断是否提醒（至少 4 个可比样本、每日最多 1 条、同类冷却）；LLM 只负责措辞，无权决定或自行发送。" +
                    "提醒会写入聊天与提醒中心，并在权限允许时发送系统通知（每日 20:00 后台检查）。",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
            )
            Spacer(Modifier.size(16.dp))

            // 默认全开但没有通知权限时，系统通知会静默失效——这里给出明确的获取路径。
            if (enabled.isNotEmpty() && !permissionGranted) {
                Row(
                    Modifier.fillMaxWidth()
                        .softShadow(cornerRadius = 16.dp)
                        .background(AppColors.CardBg)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("系统通知未开启", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Spacer(Modifier.size(2.dp))
                        Text(
                            "提醒仍会出现在聊天与提醒中心；开启通知后可在后台送达。",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary,
                        )
                    }
                    androidx.compose.material3.OutlinedButton(
                        onClick = { requestPermissionIfNeeded() },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    ) { Text("开启") }
                }
                Spacer(Modifier.size(12.dp))
            }

            fun applyToggle(type: ProactiveAlertType, checked: Boolean) {
                vm.toggle(type, checked)
                if (checked) requestPermissionIfNeeded()
            }

            AlertToggleRow(
                title = "预算临界",
                subtitle = "本月预算用满 90% 或超支时提醒",
                checked = ProactiveAlertType.BUDGET_THRESHOLD in enabled,
                onToggle = { applyToggle(ProactiveAlertType.BUDGET_THRESHOLD, it) },
            )
            AlertToggleRow(
                title = "异常消费",
                subtitle = "本周明显高于近 4 周个人基线时提醒",
                checked = ProactiveAlertType.ANOMALOUS_SPENDING in enabled,
                onToggle = { applyToggle(ProactiveAlertType.ANOMALOUS_SPENDING, it) },
            )
            AlertToggleRow(
                title = "储蓄目标偏离",
                subtitle = "结合月收入与储蓄目标，按本月节奏推算偏离时提醒",
                checked = ProactiveAlertType.SAVINGS_GOAL_DEVIATION in enabled,
                onToggle = { applyToggle(ProactiveAlertType.SAVINGS_GOAL_DEVIATION, it) },
            )

            if (notice.isNotEmpty()) {
                Spacer(Modifier.size(6.dp))
                Text(notice, style = MaterialTheme.typography.bodySmall, color = AppColors.Accent)
            }
        }
    }
}

@Composable
private fun AlertToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .softShadow(cornerRadius = 16.dp)
            .background(AppColors.CardBg)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
            Spacer(Modifier.size(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
    Spacer(Modifier.size(10.dp))
}
