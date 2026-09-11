package com.expense.tracker.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.iconBtnShadow
import com.expense.tracker.ui.theme.softShadow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 一天的聚合：金额 + 笔数。 */
data class DayCell(val date: LocalDate, val total: Double, val count: Int)

/**
 * 月历视图 — 纯 Compose，仿手机系统日历样式（方格）。
 *
 * 设计：
 * - 顶部：◀ "2026 年 6 月" ▶ 翻月
 * - 周一到周日表头
 * - 6 行 × 7 列方格，每格：日期数字 + 金额 + 笔数
 * - 当天高亮成黑色背景白字；选中的格子加边框
 * - 跨月份的日期用浅灰，提示"不属于当前月"
 *
 * 数据由 HistoryViewModel 按本地时区分组好后传进来。
 */
@Composable
fun MonthCalendarView(
    yearMonth: YearMonth,
    today: LocalDate,
    selected: LocalDate?,
    cellsByDate: Map<LocalDate, DayCell>,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // 顶部翻月行
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .iconBtnShadow()
                    .clip(CircleShape)
                    .background(AppColors.CardBg)
                    .clickable { onPrevMonth() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.ChevronLeft, contentDescription = "上月", tint = AppColors.TextPrimary, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.weight(1f))
            Text(
                yearMonth.format(DateTimeFormatter.ofPattern("yyyy 年 M 月", Locale.CHINA)),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .iconBtnShadow()
                    .clip(CircleShape)
                    .background(AppColors.CardBg)
                    .clickable { onNextMonth() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.ChevronRight, contentDescription = "下月", tint = AppColors.TextPrimary, modifier = Modifier.size(20.dp)) }
        }

        Spacer(Modifier.size(8.dp))

        // 周表头：一 二 三 四 五 六 日
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { d ->
                Box(
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        d,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextMuted,
                    )
                }
            }
        }

        // 月历方格 — 计算该月第一天是周几（周一为 1），向前补齐空白格
        val firstOfMonth = yearMonth.atDay(1)
        // ISO 周一=1 ... 周日=7；我们的列从周一开始 → 偏移 = dayOfWeek-1
        val leadingBlanks = firstOfMonth.dayOfWeek.value - DayOfWeek.MONDAY.value
        val daysInMonth = yearMonth.lengthOfMonth()
        val totalCells = leadingBlanks + daysInMonth
        val rows = (totalCells + 6) / 7  // 5 或 6 行

        Column(modifier = Modifier.fillMaxWidth()) {
            repeat(rows) { rowIdx ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    repeat(7) { colIdx ->
                        val cellIdx = rowIdx * 7 + colIdx
                        val dayOfMonth = cellIdx - leadingBlanks + 1
                        if (dayOfMonth in 1..daysInMonth) {
                            val date = yearMonth.atDay(dayOfMonth)
                            val cell = cellsByDate[date]
                            DayGrid(
                                date = date,
                                cell = cell,
                                isToday = date == today,
                                isSelected = date == selected,
                                onClick = { onSelectDate(date) },
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            // 空白占位（属于前月或后月，不显示）
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayGrid(
    date: LocalDate,
    cell: DayCell?,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasData = cell != null && cell.count > 0
    val bg = if (isToday) AppColors.TextPrimary else AppColors.CardBg
    val mainColor = if (isToday) AppColors.Bg else AppColors.TextPrimary
    val subColor = if (isToday) AppColors.Bg.copy(alpha = 0.85f) else AppColors.TextSecondary

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .softShadow(
                elevation = if (hasData || isToday) 2.dp else 0.5.dp,
                cornerRadius = 10.dp,
                spotAlpha = if (hasData || isToday) 0.08f else 0.03f,
            )
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick),
    ) {
        // 选中态：1.5dp 蓝色边框（用 Box 覆盖一层）
        if (isSelected && !isToday) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.Accent.copy(alpha = 0.10f)),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            // 日期数字
            Text(
                text = date.dayOfMonth.toString(),
                color = mainColor,
                fontSize = 13.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
            )
            // 金额（hasData 时显示）
            if (hasData) {
                Text(
                    text = "¥${formatAmount(cell!!.total)}",
                    color = mainColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                // 笔数小字
                Text(
                    text = "${cell.count} 笔",
                    color = subColor,
                    fontSize = 8.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 把金额简短显示：999 内显示原值；上千用 k；上万用 w。避免方格被撑爆。 */
private fun formatAmount(amount: Double): String = when {
    amount >= 10000 -> "%.1fw".format(java.util.Locale.US, amount / 10000)
    amount >= 1000 -> "%.1fk".format(java.util.Locale.US, amount / 1000)
    amount >= 100 -> "%.0f".format(java.util.Locale.US, amount)
    else -> "%.1f".format(java.util.Locale.US, amount)
}
