package com.expense.tracker.ui.analytics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expense.tracker.data.model.Period
import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.iconBtnShadow

private fun insightEmoji(index: Int): String = when (index % 5) {
    0 -> "💡"
    1 -> "📊"
    2 -> "⚠️"
    3 -> "✅"
    4 -> "🎯"
    else -> "💡"
}

@Composable
fun InsightsScreen(
    vm: AnalyticsViewModel,
    llmPrefs: UserPrefsSnapshot?,
    onBack: () -> Unit,
) {
    val state by vm.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Bg)
            .verticalScroll(rememberScrollState()),
    ) {
        // 顶部栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .iconBtnShadow()
                    .clip(CircleShape)
                    .background(AppColors.CardBg)
                    .pointerInput(onBack) { detectTapGestures(onTap = { onBack() }) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = AppColors.TextPrimary) }
            Spacer(Modifier.size(12.dp))
            Text("🧠 智核分析", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
        }

        // 周期切换
        PeriodSelector(
            current = state.period,
            onSelect = vm::selectPeriod,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        // 加载中
        AnimatedVisibility(
            visible = state.analyzing,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = AppColors.TextPrimary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "正在分析${when(state.period){Period.Week->"本周";Period.Month->"本月";Period.Year->"本年"}}消费习惯...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppColors.TextSecondary,
                    )
                }
            }
        }

        // 洞察结果
        if (!state.analyzing) {
            val isError = state.insights.size == 1 && (
                    state.insights.first().startsWith("分析失败") ||
                            state.insights.first().startsWith("请先配置")
                    )

            if (state.insights.isNotEmpty()) {
                if (isError) {
                    Spacer(Modifier.height(32.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = AppColors.ErrorBg),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("😵", fontSize = 36.sp)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                state.insights.first(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppColors.TextPrimary,
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { llmPrefs?.let { vm.requestInsights(it) } },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppColors.TextPrimary,
                                    contentColor = AppColors.Bg,
                                ),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("重新分析")
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(16.dp))
                    state.insights.forEachIndexed { index, insight ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(insightEmoji(index), fontSize = 22.sp)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    insight,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AppColors.TextPrimary,
                                    lineHeight = 22.sp,
                                )
                            }
                        }
                    }
                }
            } else if (state.totalAmount > 0.0) {
                Spacer(Modifier.height(60.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("🔍", fontSize = 48.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "点击下方按钮，AI 将分析你的消费习惯并给出针对性建议",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextSecondary,
                        lineHeight = 22.sp,
                    )
                }
            }
        }

        // 底部：周期摘要 + 分析按钮
        Spacer(Modifier.height(24.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    PeriodSummaryItem(
                        label = when (state.period) {
                            Period.Week -> "本周"
                            Period.Month -> "本月"
                            Period.Year -> "本年"
                        },
                        value = "¥${"%.2f".format(state.totalAmount)}",
                    )
                    PeriodSummaryItem(
                        label = "消费笔数",
                        value = "${state.totalCount} 笔",
                    )
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        val prefs = llmPrefs ?: return@Button
                        vm.requestInsights(prefs)
                    },
                    enabled = !state.analyzing && state.totalAmount > 0.0 && llmPrefs != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.TextPrimary,
                        contentColor = AppColors.Bg,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.analyzing) {
                        CircularProgressIndicator(
                            color = AppColors.Bg,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.analyzing) "分析中..." else "🧠 开始分析")
                }

                if (llmPrefs == null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "请先在设置页面配置 LLM API Key",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextSecondary,
                    )
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun PeriodSummaryItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.TextPrimary,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.TextSecondary,
        )
    }
}
