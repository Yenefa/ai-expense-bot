package com.expense.tracker.ui.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.expense.tracker.proactive.ProactiveAlertRecord
import com.expense.tracker.proactive.ProactiveSeverity
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.iconBtnShadow
import com.expense.tracker.ui.theme.softShadow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 提醒中心（P2）：只展示已被治理器放行并投递过的提醒，不重新评估规则。 */
@Composable
fun ProactiveCenterScreen(vm: ProactiveCenterViewModel, onClose: () -> Unit) {
    val history by vm.history.collectAsState()

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
                ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = AppColors.TextPrimary) }
                Spacer(Modifier.size(12.dp))
                Text("提醒中心", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
            }

            Spacer(Modifier.size(8.dp))
            Text(
                "只记录规则已放行的提醒（最多保留最近 ${com.expense.tracker.data.prefs.ProactivePrefs.MAX_HISTORY} 条，含最终文案）；" +
                    "后台通知与聊天内 🔔 共用同一份治理结果。",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
            )
            Spacer(Modifier.size(16.dp))

            if (history.isEmpty()) {
                Text(
                    "还没有提醒记录。规则触发（预算临界 / 异常消费 / 储蓄偏离）并放行后会出现在这里。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextMuted,
                )
            } else {
                history.forEach { record -> AlertHistoryCard(record) }
                Spacer(Modifier.size(6.dp))
                OutlinedButton(
                    onClick = { vm.clear() },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("清空提醒记录") }
            }
        }
    }
}

@Composable
private fun AlertHistoryCard(record: ProactiveAlertRecord) {
    Column(
        Modifier.fillMaxWidth()
            .softShadow(cornerRadius = 16.dp)
            .background(AppColors.CardBg)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                record.type.typeLabel,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
            )
            Text(
                severityLabel(record.severity),
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.Accent,
            )
        }
        Spacer(Modifier.size(4.dp))
        Text(record.copy, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextPrimary)
        Spacer(Modifier.size(6.dp))
        Text(
            TIME_FORMAT.format(Instant.ofEpochMilli(record.createdAtMillis).atZone(ZoneId.systemDefault())),
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.TextMuted,
        )
    }
    Spacer(Modifier.size(10.dp))
}

private fun severityLabel(severity: ProactiveSeverity): String = when (severity) {
    ProactiveSeverity.WARN -> "预警"
    ProactiveSeverity.OVER -> "超限"
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
