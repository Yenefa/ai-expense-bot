package com.expense.tracker.ui.analytics

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
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import com.expense.tracker.data.model.Period
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.iconBtnShadow
import com.expense.tracker.ui.theme.softShadow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    vm: AnalyticsViewModel,
    onBack: () -> Unit,
    onOpenInsights: () -> Unit,
) {
    val state by vm.uiState.collectAsState()
    var showSheet by remember { mutableStateOf(false) }

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
                // 右上角日历入口：点开弹时段翻页器，翻到任意时段看支出分析
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .iconBtnShadow()
                        .clip(CircleShape)
                        .background(AppColors.Bg)
                        .pointerInput(Unit) { detectTapGestures(onTap = { showSheet = true }) },
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

            // 当前看的参考时段（翻页后显示，如"2024 年 5 月"）
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

        // 时段翻页器：选粒度 + ◀▶ 翻到任意时段 + 回到本周/月/年
        if (showSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSheet = false },
                sheetState = rememberModalBottomSheetState(),
            ) {
                val periodName = when (state.period) {
                    Period.Week -> "周"
                    Period.Month -> "月"
                    Period.Year -> "年"
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("选择时段", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
                    Spacer(Modifier.height(16.dp))
                    PeriodSelector(
                        current = state.period,
                        onSelect = vm::selectPeriod,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .iconBtnShadow()
                                .clip(CircleShape)
                                .background(AppColors.Bg)
                                .clickable { vm.stepRef(-1) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Outlined.ChevronLeft, contentDescription = "上一$periodName", tint = AppColors.TextPrimary) }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                state.refLabel ?: "本$periodName",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.TextPrimary,
                            )
                            Text(
                                "点 ◀ ▶ 翻到任意时段",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.TextMuted,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .iconBtnShadow()
                                .clip(CircleShape)
                                .background(AppColors.Bg)
                                .clickable { vm.stepRef(1) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Outlined.ChevronRight, contentDescription = "下一$periodName", tint = AppColors.TextPrimary) }
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = { vm.selectPeriod(state.period) },
                            modifier = Modifier.weight(1f),
                        ) { Text("回到本$periodName") }
                        Button(
                            onClick = { showSheet = false },
                            modifier = Modifier.weight(1.4f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppColors.TextPrimary,
                                contentColor = Color.White,
                            ),
                        ) { Text("完成") }
                    }
                    Spacer(Modifier.height(28.dp))
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
