package com.expense.tracker.ui.recurring

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.db.RecurringPeriodType
import com.expense.tracker.data.db.RecurringRuleEntity
import com.expense.tracker.data.model.Category
import com.expense.tracker.data.model.Money
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.iconBtnShadow
import com.expense.tracker.ui.theme.softShadow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RecurringScreen(
    vm: RecurringViewModel,
    onBack: () -> Unit,
) {
    val state by vm.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showForm by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            snackbar.showSnackbar(state.savedMessage)
            vm.dismissSaved()
        }
    }

    Box(Modifier.fillMaxSize().background(AppColors.Bg)) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
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
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = AppColors.TextPrimary)
                }
                Spacer(Modifier.size(12.dp))
                Text("周期账单", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "房租、会员订阅、工资等固定账单，到期自动记一笔，不会重复生成。",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
            )

            Spacer(Modifier.height(16.dp))
            if (state.rules.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    Text("还没有周期账单，点下方按钮添加", color = AppColors.TextMuted)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.rules.forEach { rule ->
                        RuleRow(
                            rule = rule,
                            onToggle = { vm.toggle(rule) },
                            onDelete = { vm.delete(rule) },
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            Button(
                onClick = { showForm = true },
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.TextPrimary, contentColor = AppColors.Bg),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("添加周期账单", fontWeight = FontWeight.SemiBold)
            }
        }

        if (showForm) {
            RecurringFormDialog(
                onDismiss = { showForm = false },
                onSave = { amount, categoryId, note, period, dayOfMonth, dayOfWeek, monthOfYear ->
                    vm.add(amount, categoryId, note, period, dayOfMonth, dayOfWeek, monthOfYear)
                    showForm = false
                },
            )
        }

        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
private fun RuleRow(
    rule: RecurringRuleEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val category = Category.byIdOrOther(rule.categoryId)
    val nextLabel = Instant.ofEpochMilli(rule.nextDueAt)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M月d日"))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(elevation = 1.dp, cornerRadius = 16.dp, spotAlpha = 0.04f)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.CardBg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${category.emoji}", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${category.displayName} · ¥${Money.formatYuan(rule.amountCents)}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (rule.enabled) AppColors.TextPrimary else AppColors.TextMuted,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${rule.period.label} · 下次 $nextLabel" + if (rule.note.isNotBlank()) " · ${rule.note}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextSecondary,
            )
        }
        Switch(checked = rule.enabled, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(6.dp))
        TextButton(onClick = onDelete) { Text("删除", color = AppColors.TextMuted) }
    }
}

@Composable
private fun RecurringFormDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, RecurringPeriodType, Int, Int, Int) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf(Category.ALL.first().id) }
    var period by remember { mutableStateOf(RecurringPeriodType.MONTHLY) }
    var dayOfMonthText by remember { mutableStateOf("1") }
    var dayOfWeek by remember { mutableStateOf(1) }
    var monthOfYear by remember { mutableStateOf(1) }
    var note by remember { mutableStateOf("") }
    var categoryOpen by remember { mutableStateOf(false) }
    var periodOpen by remember { mutableStateOf(false) }
    var dayOfWeekOpen by remember { mutableStateOf(false) }
    var monthOpen by remember { mutableStateOf(false) }

    val weekDays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val months = (1..12).map { "${it}月" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加周期账单") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { v -> if (v.matches(Regex("\\d{0,8}(\\.\\d{0,2})?"))) amount = v },
                    label = { Text("金额（元）") },
                    prefix = { Text("¥") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("分类", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary, modifier = Modifier.width(56.dp))
                    Box {
                        OutlinedButton(onClick = { categoryOpen = true }, shape = RoundedCornerShape(12.dp)) {
                            Text("${Category.byIdOrOther(categoryId).emoji} ${Category.byIdOrOther(categoryId).displayName}")
                        }
                        DropdownMenu(expanded = categoryOpen, onDismissRequest = { categoryOpen = false }) {
                            Category.ALL.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text("${option.emoji} ${option.displayName}") },
                                    onClick = { categoryId = option.id; categoryOpen = false },
                                )
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("周期", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary, modifier = Modifier.width(56.dp))
                    Box {
                        OutlinedButton(onClick = { periodOpen = true }, shape = RoundedCornerShape(12.dp)) {
                            Text(period.label)
                        }
                        DropdownMenu(expanded = periodOpen, onDismissRequest = { periodOpen = false }) {
                            RecurringPeriodType.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = { period = option; periodOpen = false },
                                )
                            }
                        }
                    }
                    when (period) {
                        RecurringPeriodType.WEEKLY -> {
                            Spacer(Modifier.width(10.dp))
                            Box {
                                OutlinedButton(onClick = { dayOfWeekOpen = true }, shape = RoundedCornerShape(12.dp)) {
                                    Text(weekDays[dayOfWeek - 1])
                                }
                                DropdownMenu(expanded = dayOfWeekOpen, onDismissRequest = { dayOfWeekOpen = false }) {
                                    weekDays.forEachIndexed { index, label ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = { dayOfWeek = index + 1; dayOfWeekOpen = false },
                                        )
                                    }
                                }
                            }
                        }
                        RecurringPeriodType.MONTHLY -> {
                            Spacer(Modifier.width(10.dp))
                            OutlinedTextField(
                                value = dayOfMonthText,
                                onValueChange = { v -> if (v.matches(Regex("\\d{0,2}"))) dayOfMonthText = v },
                                label = { Text("几号") },
                                singleLine = true,
                                modifier = Modifier.width(96.dp),
                            )
                        }
                        RecurringPeriodType.YEARLY -> {
                            Spacer(Modifier.width(10.dp))
                            Box {
                                OutlinedButton(onClick = { monthOpen = true }, shape = RoundedCornerShape(12.dp)) {
                                    Text(months[monthOfYear - 1])
                                }
                                DropdownMenu(expanded = monthOpen, onDismissRequest = { monthOpen = false }) {
                                    months.forEachIndexed { index, label ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = { monthOfYear = index + 1; monthOpen = false },
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            OutlinedTextField(
                                value = dayOfMonthText,
                                onValueChange = { v -> if (v.matches(Regex("\\d{0,2}"))) dayOfMonthText = v },
                                label = { Text("几号") },
                                singleLine = true,
                                modifier = Modifier.width(96.dp),
                            )
                        }
                        RecurringPeriodType.DAILY -> Unit
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（如：房租 / 会员）") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val day = dayOfMonthText.toIntOrNull()?.coerceIn(1, 31) ?: 1
                    onSave(amount, categoryId, note, period, day, dayOfWeek, monthOfYear)
                },
                enabled = amount.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
