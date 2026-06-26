package com.expense.tracker.ui.analytics

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
import com.expense.tracker.data.model.Period
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.iconBtnShadow
import com.expense.tracker.ui.theme.softShadow

@Composable
fun AnalyticsScreen(
    vm: AnalyticsViewModel,
    onBack: () -> Unit,
    onOpenInsights: () -> Unit,
) {
    val state by vm.uiState.collectAsState()
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
        }

        PeriodSelector(
            current = state.period,
            onSelect = vm::selectPeriod,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        // 智核分析按钮（点击跳转到独立页面）
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

        // 汇总卡片
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
        SectionTitle("🥧 分类占比（甜甜圈图）")
        DonutChartView(
            subPeriods = state.subPeriods,
            subPeriodLabels = state.xLabels,
            outerByCategory = state.pieByCategory,
            selectedIndex = state.selectedSubPeriodIndex,
            onSelectSubPeriod = vm::selectSubPeriod,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(40.dp))
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
