package com.expense.tracker.ui.subscription

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.subscription.SubscriptionStatus
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.iconBtnShadow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable
fun SubscriptionScreen(
    vm: SubscriptionViewModel,
    onBack: () -> Unit,
) {
    val state by vm.uiState.collectAsState()
    val active = state.status == SubscriptionStatus.ACTIVE
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val serverTimeMillis = state.serverTimeMillis
    val serverCheckedAtMillis = state.serverCheckedAtMillis
    val serverClockText = remember(serverTimeMillis, serverCheckedAtMillis, nowMillis) {
        if (serverTimeMillis == null || serverTimeMillis <= 0L || serverCheckedAtMillis <= 0L) {
            null
        } else {
            // 服务器时间 = 上次探测的服务器时间 + 本地流逝时长；能看到它在走，说明时间检测正常。
            formatClock(serverTimeMillis + (nowMillis - serverCheckedAtMillis))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Bg)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
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
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = AppColors.TextPrimary,
                )
            }
            Spacer(Modifier.size(12.dp))
            Text("AI 会员", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = AppColors.CardBg,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(if (active) "✨ AI 会员已生效" else "🎟 30 天测试订阅", style = MaterialTheme.typography.titleMedium, color = AppColors.TextPrimary)
                Text(
                    when (state.status) {
                        SubscriptionStatus.ACTIVE -> "有效期至 ${formatExpiry(state.expiresAtMillis)}"
                        SubscriptionStatus.EXPIRED -> "会员已到期，输入新的兑换凭证即可续期。"
                        SubscriptionStatus.INACTIVE -> "兑换后可使用 AI 记账、智核分析和 OCR 账单整理。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary,
                )
            }
        }

        OutlinedTextField(
            value = state.credentialInput,
            onValueChange = vm::updateCredential,
            label = { Text(if (active) "新的续期凭证" else "兑换凭证") },
            supportingText = { Text("凭证验证后由 Android Keystore 加密保存") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            enabled = !state.redeeming,
            modifier = Modifier.fillMaxWidth(),
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = AppColors.CardBg,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (state.serverReachable) ServerOkColor else ServerDownColor),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (state.serverReachable) "订阅服务器在线 · 腾讯云" else "订阅服务器未连接",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextPrimary,
                    )
                }
                Text(
                    if (serverClockText != null) {
                        "服务器时间 $serverClockText（每 30 秒自动检测）"
                    } else if (state.serverReachable) {
                        "等待服务器时间..."
                    } else {
                        "AI 会员需联网使用；兑换与 AI 请求走云端服务器。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary,
                )
                Text(
                    "本机时间 ${formatClock(nowMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextMuted,
                )
            }
        }

        state.errorMessage?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        state.successMessage?.let { message ->
            Text(message, color = Color(0xFF2E7D32), style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = vm::redeem,
            enabled = !state.redeeming,
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.TextPrimary,
                contentColor = AppColors.Bg,
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            if (state.redeeming) {
                CircularProgressIndicator(
                    color = AppColors.Bg,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Text(if (active) "兑换并续期 30 天" else "兑换 30 天")
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = AppColors.CardBg,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "每个兑换码只能使用一次；兑换成功后增加 30 天 AI 会员。AI 功能需要联网。",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextMuted,
                modifier = Modifier.padding(16.dp),
            )
        }

        Text(
            "会员生效后，回到首页点亮 🧠 即可使用。自定义 API Key 模式仍保留为备用。",
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextSecondary,
        )
    }
}

private fun formatExpiry(expiresAtMillis: Long): String = DateTimeFormatter
    .ofPattern("yyyy年M月d日 HH:mm")
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(expiresAtMillis))

private fun formatClock(millis: Long): String = DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH:mm:ss")
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(millis))

private val ServerOkColor = Color(0xFF2E7D32)
private val ServerDownColor = Color(0xFFB3261E)
