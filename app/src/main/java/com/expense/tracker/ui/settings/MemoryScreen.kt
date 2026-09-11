package com.expense.tracker.ui.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.model.Category
import com.expense.tracker.data.model.Money
import com.expense.tracker.memory.MemoryFact
import com.expense.tracker.memory.MemoryType
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.iconBtnShadow
import com.expense.tracker.ui.theme.softShadow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val createdFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** 我的记忆：查看 / 修改 / 删除 / 清空 + 来源与创建时间。 */
@Composable
fun MemoryScreen(vm: MemoryViewModel, onClose: () -> Unit) {
    val facts by vm.facts.collectAsState()
    var editing by remember { mutableStateOf<MemoryFact?>(null) }
    var clearing by remember { mutableStateOf(false) }

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
                Text("我的记忆", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
            }

            Spacer(Modifier.size(8.dp))
            Text(
                "这些是你确认过的长期信息；按任务授权读取（记账只读商户别名，分析才读收入/储蓄/常用分类）。",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
            )
            Spacer(Modifier.size(16.dp))

            if (facts.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().softShadow(cornerRadius = 16.dp).background(AppColors.CardBg)
                        .padding(20.dp),
                ) {
                    Text("还没有已确认的长期记忆。", color = AppColors.TextSecondary)
                }
            } else {
                facts.forEach { fact ->
                    MemoryCard(
                        fact = fact,
                        onEdit = { editing = fact },
                        onDelete = { vm.delete(fact.id) },
                    )
                    Spacer(Modifier.size(10.dp))
                }
                Spacer(Modifier.size(4.dp))
                TextButton(onClick = { clearing = true }) {
                    Text("清空全部", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        editing?.let { fact ->
            MemoryEditDialog(
                fact = fact,
                onDismiss = { editing = null },
                onSave = { updated -> vm.update(updated) },
            )
        }

        if (clearing) {
            AlertDialog(
                onDismissRequest = { clearing = false },
                title = { Text("清空全部记忆？") },
                text = { Text("所有已确认的长期信息将被删除，且之后不会再被 Agent 读取。") },
                confirmButton = {
                    TextButton(onClick = {
                        vm.clearAll()
                        clearing = false
                    }) { Text("清空") }
                },
                dismissButton = { TextButton(onClick = { clearing = false }) { Text("取消") } },
            )
        }
    }
}

@Composable
private fun MemoryCard(fact: MemoryFact, onEdit: () -> Unit, onDelete: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().softShadow(cornerRadius = 16.dp).background(AppColors.CardBg)
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fact.type.typeLabel, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
            Text(
                createdFmt.format(
                    Instant.ofEpochMilli(fact.createdAt).atZone(ZoneId.systemDefault()).toLocalDateTime(),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextSecondary,
            )
        }
        Spacer(Modifier.size(6.dp))
        Text(fact.summary(), style = MaterialTheme.typography.bodyLarge, color = AppColors.TextPrimary)
        if (fact.rawText.isNotBlank()) {
            Spacer(Modifier.size(4.dp))
            Text("来源：「${fact.rawText}」", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
        }
        Spacer(Modifier.size(6.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onEdit) { Text("编辑") }
            TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun MemoryEditDialog(
    fact: MemoryFact,
    onDismiss: () -> Unit,
    onSave: suspend (MemoryFact) -> Boolean,
) {
    val scope = rememberCoroutineScope()
    var amountText by remember {
        mutableStateOf(fact.amountCents?.let { Money.formatYuan(it) } ?: "")
    }
    var merchantText by remember { mutableStateOf(fact.merchant.orEmpty()) }
    var categoryId by remember { mutableStateOf(fact.categoryId ?: Category.ALL.first().id) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑${fact.type.typeLabel}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when (fact.type) {
                    MemoryType.MONTHLY_INCOME, MemoryType.SAVINGS_GOAL -> {
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = {
                                amountText = it
                                errorText = null
                            },
                            label = { Text("金额（元）") },
                            singleLine = true,
                        )
                    }
                    MemoryType.MERCHANT_ALIAS -> {
                        OutlinedTextField(
                            value = merchantText,
                            onValueChange = {
                                merchantText = it
                                errorText = null
                            },
                            label = { Text("商户") },
                            singleLine = true,
                        )
                        Spacer(Modifier.size(8.dp))
                        CategoryPicker(selected = categoryId, onSelect = {
                            categoryId = it
                            errorText = null
                        })
                    }
                    MemoryType.CATEGORY_PREFERENCE -> {
                        CategoryPicker(selected = categoryId, onSelect = {
                            categoryId = it
                            errorText = null
                        })
                    }
                }
                errorText?.let {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.size(8.dp))
                Text("来源：「${fact.rawText}」", style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val updated = when (fact.type) {
                    MemoryType.MONTHLY_INCOME, MemoryType.SAVINGS_GOAL -> {
                        val cents = runCatching { Money.parseYuanToCents(amountText.trim()) }.getOrNull()
                        if (cents == null || cents <= 0L) {
                            errorText = "金额无效"
                            return@TextButton
                        }
                        fact.copy(amountCents = cents)
                    }
                    MemoryType.MERCHANT_ALIAS -> fact.copy(merchant = merchantText.trim(), categoryId = categoryId)
                    MemoryType.CATEGORY_PREFERENCE -> fact.copy(categoryId = categoryId)
                }
                scope.launch {
                    if (onSave(updated)) {
                        onDismiss()
                    } else {
                        errorText = "保存失败，请检查输入"
                    }
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun CategoryPicker(selected: String, onSelect: (String) -> Unit) {
    Column {
        Text("分类", style = MaterialTheme.typography.labelMedium, color = AppColors.TextSecondary)
        Category.ALL.forEach { category ->
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelect(category.id) }
                    .padding(vertical = 8.dp, horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${category.emoji} ${category.displayName}", color = AppColors.TextPrimary)
                if (category.id == selected) {
                    Text("已选", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
