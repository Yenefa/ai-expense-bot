package com.expense.tracker.ui.analytics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.iconBtnShadow
import com.expense.tracker.ui.theme.softShadow
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AnalyticsScreen(
    vm: AnalyticsViewModel,
    onBack: () -> Unit,
    onOpenInsights: () -> Unit,
) {
    val state by vm.uiState.collectAsState()
    var showPeriodSheet by remember { mutableStateOf(false) }
    var showDial by remember { mutableStateOf(false) }

    val today = remember { LocalDate.now() }
    val zone = remember { ZoneId.systemDefault() }
    // 年环用 ISO weekBasedYear，跨年周归属正确（如 2025-12-29 属 2026 第 1 周）
    val initialYear = remember { today.get(WeekFields.ISO.weekBasedYear()) }
    val initialWeek = remember { today.get(WeekFields.ISO.weekOfWeekBasedYear()) }

    Box(modifier = Modifier.fillMaxSize().background(AppColors.Bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Bg)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .iconBtnShadow()
                        .clip(CircleShape)
                        .background(AppColors.Bg)
                        .pointerInput(onBack) { detectTapGestures(onTap = { onBack() }) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = AppColors.TextPrimary) }
                Spacer(Modifier.size(12.dp))
                Text("支出分析", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
                Spacer(Modifier.weight(1f))
                // 右上角日历入口：短按=胶囊选粒度，长按=同心罗盘选具体值
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .iconBtnShadow()
                        .clip(CircleShape)
                        .background(AppColors.Bg)
                        .combinedClickable(
                            onClick = { showPeriodSheet = true },
                            onLongClick = { showDial = true },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = "时段选择", tint = AppColors.TextPrimary)
                }
            }

            PeriodSelector(
                current = state.period,
                onSelect = vm::selectPeriod,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            // 罗盘选具体值时显示当前参考时段
            state.refLabel?.let { label ->
                Spacer(Modifier.height(8.dp))
                Text(
                    "📅 $label",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.Accent,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onOpenInsights,
                    enabled = state.totalAmount > 0.0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.TextPrimary,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("🧠 智核分析")
                }
            }

            if (state.totalAmount > 0.0) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .softShadow(elevation = 2.dp, cornerRadius = 16.dp, spotAlpha = 0.06f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppColors.Bg)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StatItem("总支出", "¥%.2f".format(state.totalAmount))
                    StatItem("笔数", "${state.totalCount}")
                }
                Spacer(Modifier.height(16.dp))
            }

            SectionTitle("📊 总支出（柱形图）")
            BarChartView(
                amounts = state.barAmounts,
                xLabels = state.xLabels,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(28.dp))
            SectionTitle("📈 消费次数（折线图）")
            LineChartView(
                counts = state.lineCounts,
                xLabels = state.xLabels,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(28.dp))
            SectionTitle("🧾 分类占比")
            CategoryBreakdownList(
                byCategory = state.pieByCategory,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(40.dp))
        }

        // 长按：背景虚化 scrim
        AnimatedVisibility(
            visible = showDial,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        }

        // 长按：同心罗盘 overlay（弹性伸缩动效）
        AnimatedVisibility(
            visible = showDial,
            enter = scaleIn(initialScale = 0.6f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut(targetScale = 0.6f) + fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { showDial = false } }
                    .padding(vertical = 60.dp),
                contentAlignment = Alignment.Center,
            ) {
                ConcentricDialSelector(
                    initialYear = initialYear,
                    initialMonth = today.monthValue,
                    initialWeek = initialWeek,
                    zone = zone,
                    onSelect = { period, refMillis ->
                        vm.selectPeriodAndRef(period, refMillis)
                        showDial = false
                    },
                    onDismiss = { showDial = false },
                )
            }
        }

        // 短按：胶囊选粒度 BottomSheet
        if (showPeriodSheet) {
            ModalBottomSheet(
                onDismissRequest = { showPeriodSheet = false },
                sheetState = rememberModalBottomSheetState(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("选择粒度", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
                    Spacer(Modifier.height(16.dp))
                    PeriodSelector(
                        current = state.period,
                        onSelect = { p ->
                            vm.selectPeriod(p)
                            showPeriodSheet = false
                        },
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = AppColors.TextPrimary,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
