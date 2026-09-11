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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
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
                "由本地规则判断是否提醒（至少 4 个可比样本、每日最多 1 条、同类冷却）；LLM 只负责措辞，无权决定或自行发送。",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
            )
            Spacer(Modifier.size(16.dp))

            AlertToggleRow(
                title = "预算临界",
                subtitle = "本月预算用满 90% 或超支时提醒",
                checked = ProactiveAlertType.BUDGET_THRESHOLD in enabled,
                onToggle = { vm.toggle(ProactiveAlertType.BUDGET_THRESHOLD, it) },
            )
            AlertToggleRow(
                title = "异常消费",
                subtitle = "本周明显高于近 4 周个人基线时提醒",
                checked = ProactiveAlertType.ANOMALOUS_SPENDING in enabled,
                onToggle = { vm.toggle(ProactiveAlertType.ANOMALOUS_SPENDING, it) },
            )
            AlertToggleRow(
                title = "储蓄目标偏离",
                subtitle = "结合月收入与储蓄目标，按本月节奏推算偏离时提醒",
                checked = ProactiveAlertType.SAVINGS_GOAL_DEVIATION in enabled,
                onToggle = { vm.toggle(ProactiveAlertType.SAVINGS_GOAL_DEVIATION, it) },
            )
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
