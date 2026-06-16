package com.expense.tracker.ui.calendar

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
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
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.iconBtnShadow
import com.expense.tracker.ui.theme.softShadow

@Composable
fun CalendarScreen(vm: CalendarViewModel, onBack: () -> Unit) {
    val state by vm.uiState.collectAsState()

    Column(Modifier.fillMaxSize().background(AppColors.Bg)) {
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
            Spacer(Modifier.width(12.dp))
            Text("消费日历", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
            Spacer(Modifier.weight(1f))
            Text(
                "今天",
                color = AppColors.Accent,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { vm.jumpToday() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavIcon(onClick = vm::previous) { Icon(Icons.Outlined.KeyboardArrowLeft, contentDescription = "上一周期") }
            Spacer(Modifier.weight(1f))
            Text(state.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            NavIcon(onClick = vm::next) { Icon(Icons.Outlined.KeyboardArrowRight, contentDescription = "下一周期") }
        }

        ModeTabs(current = state.mode, onSelect = vm::selectMode)

        CalendarGrid(
            mode = state.mode,
            cells = state.cells,
            selectedKey = state.selectedKey,
            onSelect = vm::selectCell,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        DetailPanel(
            title = state.selectedTitle,
            items = state.selectedItems,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NavIcon(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun ModeTabs(current: CalendarMode, onSelect: (CalendarMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.ChipFill)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ModeTab("周", CalendarMode.Week, current, onSelect, Modifier.weight(1f))
        ModeTab("月", CalendarMode.Month, current, onSelect, Modifier.weight(1f))
        ModeTab("年", CalendarMode.Year, current, onSelect, Modifier.weight(1f))
    }
}

@Composable
private fun ModeTab(
    text: String,
    mode: CalendarMode,
    current: CalendarMode,
    onSelect: (CalendarMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = mode == current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) AppColors.Bg else Color.Transparent)
            .clickable { onSelect(mode) }
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) AppColors.TextPrimary else AppColors.TextSecondary)
    }
}

@Composable
private fun CalendarGrid(
    mode: CalendarMode,
    cells: List<CalendarCell>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val columns = when (mode) {
        CalendarMode.Week -> 7
        CalendarMode.Month -> 7
        CalendarMode.Year -> 3
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .softShadow(elevation = 2.dp, cornerRadius = 18.dp, spotAlpha = 0.06f)
            .clip(RoundedCornerShape(18.dp))
            .background(AppColors.Bg)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (mode == CalendarMode.Month) {
            WeekHeader()
            Spacer(Modifier.height(2.dp))
        }
        cells.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { cell ->
                    CalendarCellView(
                        cell = cell,
                        selected = cell.key == selectedKey,
                        mode = mode,
                        onClick = { onSelect(cell.key) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekHeader() {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        listOf("一", "二", "三", "四", "五", "六", "日").forEach {
            Text(
                it,
                modifier = Modifier.weight(1f),
                color = AppColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun CalendarCellView(
    cell: CalendarCell,
    selected: Boolean,
    mode: CalendarMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = when {
        selected -> AppColors.TextPrimary
        cell.isToday -> Color(0xFFE8F2FF)
        else -> Color.Transparent
    }
    val primary = when {
        selected -> Color.White
        !cell.inPrimaryRange -> AppColors.TextMuted.copy(alpha = 0.45f)
        else -> AppColors.TextPrimary
    }
    val secondary = when {
        selected -> Color.White.copy(alpha = 0.78f)
        !cell.inPrimaryRange -> AppColors.TextMuted.copy(alpha = 0.38f)
        else -> AppColors.TextSecondary
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = if (mode == CalendarMode.Year) 12.dp else 8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(cell.label, color = primary, fontWeight = if (cell.isToday || selected) FontWeight.SemiBold else FontWeight.Normal)
        if (cell.count > 0) {
            Spacer(Modifier.height(3.dp))
            Text("¥${shortAmount(cell.totalAmount)}", color = primary, style = MaterialTheme.typography.labelSmall)
            Text(cell.subLabel, color = secondary, style = MaterialTheme.typography.labelSmall)
        } else {
            Spacer(Modifier.height(18.dp))
        }
    }
}

private fun shortAmount(value: Double): String = when {
    value >= 10_000 -> "%.1fw".format(value / 10_000)
    value >= 1_000 -> "%.1fk".format(value / 1_000)
    else -> "%.0f".format(value)
}

@Composable
private fun DetailPanel(title: String, items: List<CalendarExpenseItem>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            if (title.isBlank()) "选择一个格子查看明细" else title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.TextPrimary,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        if (items.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("这一格没有消费记录", color = AppColors.TextMuted)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { item ->
                    DetailRow(item)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(item: CalendarExpenseItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.ChipFill)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(item.categoryEmoji)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(item.categoryName, color = AppColors.TextPrimary)
            if (item.note.isNotBlank()) Text(item.note, color = AppColors.TextMuted, style = MaterialTheme.typography.labelSmall)
        }
        Text(item.timeLabel, color = AppColors.TextMuted, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.width(8.dp))
        Text("¥%.2f".format(item.amount), color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
    }
}
