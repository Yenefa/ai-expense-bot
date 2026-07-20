package com.expense.tracker.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import com.expense.tracker.util.TimeRanges
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    vm: AnalyticsViewModel,
    onBack: () -> Unit,
    onOpenInsights: () -> Unit,
) {
    val state by vm.uiState.collectAsState()
    var showSheet by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }
    val zone = remember { ZoneId.systemDefault() }

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
                // 右上角日历入口：点开弹三级时段选择器（年 ▶ 月 ▶ 该月的周）
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .iconBtnShadow()
                        .clip(CircleShape)
                        .background(AppColors.Bg)
                        .pointerInput(Unit) { detectTapGestures(onTap = { showSheet = true }) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.CalendarMonth, contentDescription = "时段选择", tint = AppColors.TextPrimary) }
            }

            PeriodSelector(
                current = state.period,
                onSelect = vm::selectPeriod,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

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

        // 三级时段选择器：年 ◀▶ / 月 ◀▶ / 该月的周点选（周一归属月）
        if (showSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSheet = false },
                sheetState = rememberModalBottomSheetState(),
            ) {
                var selYear by remember { mutableStateOf(today.year) }
                var selMonth by remember { mutableStateOf(today.monthValue) }
                val mondays = remember(selYear, selMonth) { TimeRanges.weekMondaysOfMonth(selYear, selMonth) }
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
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.refLabel ?: "本$periodName",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.Accent,
                    )
                    Spacer(Modifier.height(14.dp))

                    // 年：翻年即看该年分析
                    PagerRow(label = "$selYear 年", onPrev = {
                        selYear--
                        vm.selectPeriodAndRef(Period.Year, LocalDate.of(selYear, 7, 1).atStartOfDay(zone).toInstant().toEpochMilli())
                    }, onNext = {
                        selYear++
                        vm.selectPeriodAndRef(Period.Year, LocalDate.of(selYear, 7, 1).atStartOfDay(zone).toInstant().toEpochMilli())
                    })
                    Spacer(Modifier.height(10.dp))

                    // 月：翻月即看该月分析
                    PagerRow(label = "$selMonth 月", onPrev = {
                        if (selMonth == 1) { selMonth = 12; selYear-- } else selMonth--
                        vm.selectPeriodAndRef(Period.Month, LocalDate.of(selYear, selMonth, 15).atStartOfDay(zone).toInstant().toEpochMilli())
                    }, onNext = {
                        if (selMonth == 12) { selMonth = 1; selYear++ } else selMonth++
                        vm.selectPeriodAndRef(Period.Month, LocalDate.of(selYear, selMonth, 15).atStartOfDay(zone).toInstant().toEpochMilli())
                    })
                    Spacer(Modifier.height(14.dp))

                    // 周：该月的周（周一在本月），点选看那周分析
                    Text("选周（周一在本月的周）", style = MaterialTheme.typography.labelMedium, color = AppColors.TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        mondays.forEach { monday ->
                            val sunday = monday.plusDays(6)
                            val label = "${monday.monthValue}/${monday.dayOfMonth}-${sunday.monthValue}/${sunday.dayOfMonth}"
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AppColors.ChipFill)
                                    .clickable {
                                        val ref = monday.plusDays(3).atStartOfDay(zone).toInstant().toEpochMilli()
                                        vm.selectPeriodAndRef(Period.Week, ref)
                                        showSheet = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) { Text(label, style = MaterialTheme.typography.bodySmall, color = AppColors.TextPrimary) }
                        }
                    }
                    Spacer(Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                selYear = today.year
                                selMonth = today.monthValue
                                vm.selectPeriod(state.period)
                            },
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
private fun PagerRow(label: String, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(AppColors.ChipFill).clickable(onClick = onPrev),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.ChevronLeft, contentDescription = "上一个", tint = AppColors.TextPrimary) }
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(AppColors.ChipFill).clickable(onClick = onNext),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.ChevronRight, contentDescription = "下一个", tint = AppColors.TextPrimary) }
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
