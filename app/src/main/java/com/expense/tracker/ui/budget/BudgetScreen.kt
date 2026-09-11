package com.expense.tracker.ui.budget

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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.model.Category
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.iconBtnShadow

@Composable
fun BudgetScreen(
    vm: BudgetViewModel,
    onBack: () -> Unit,
) {
    val state by vm.uiState.collectAsState()
    val snapshot = state.snapshot
    val snackbar = remember { SnackbarHostState() }

    var monthlyText by remember { mutableStateOf(formatCents(snapshot.monthlyLimitCents)) }
    var categoryTexts by remember {
        mutableStateOf(Category.ALL.associate { it.id to formatCents(snapshot.categoryLimitsCents[it.id] ?: 0L) })
    }
    LaunchedEffect(snapshot) {
        monthlyText = formatCents(snapshot.monthlyLimitCents)
        categoryTexts = Category.ALL.associate {
            it.id to formatCents(snapshot.categoryLimitsCents[it.id] ?: 0L)
        }
    }
    LaunchedEffect(state.saved) {
        if (state.saved) {
            snackbar.showSnackbar(state.savedMessage)
            vm.dismissSaved()
        }
    }

    Box(Modifier.fillMaxSize().background(AppColors.Bg)) {
        Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
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
                Text("预算管理", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "设置月度总预算和分类预算。本月支出达到预算 90% 显示黄色预警，超支显示红色提醒。",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
            )

            Spacer(Modifier.height(20.dp))
            Text("月度总预算", style = MaterialTheme.typography.titleMedium, color = AppColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = monthlyText,
                onValueChange = { v -> if (v.matches(Regex("\\d{0,8}(\\.\\d{0,2})?"))) monthlyText = v },
                label = { Text("每月预算（元）") },
                prefix = { Text("¥") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))
            Text("分类预算", style = MaterialTheme.typography.titleMedium, color = AppColors.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("留空表示该分类不设预算", style = MaterialTheme.typography.labelSmall, color = AppColors.TextMuted)
            Spacer(Modifier.height(8.dp))

            Category.ALL.forEach { category ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${category.emoji} ${category.displayName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = categoryTexts[category.id].orEmpty(),
                        onValueChange = { v ->
                            if (v.matches(Regex("\\d{0,8}(\\.\\d{0,2})?"))) {
                                categoryTexts = categoryTexts + (category.id to v)
                            }
                        },
                        prefix = { Text("¥") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.width(140.dp),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { vm.save(monthlyText, categoryTexts) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.TextPrimary,
                    contentColor = AppColors.Bg,
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("保存预算", fontWeight = FontWeight.SemiBold) }

            Spacer(Modifier.height(12.dp))
            Text(
                "预算只统计支出；月初自动按本月消费重新计算进度。",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextMuted,
            )
        }

        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

private fun formatCents(cents: Long): String =
    if (cents <= 0L) "" else "%.2f".format(cents / 100.0).trimEnd('0').trimEnd('.')
