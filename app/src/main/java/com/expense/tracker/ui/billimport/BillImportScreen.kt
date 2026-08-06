package com.expense.tracker.ui.billimport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.model.Category
import com.expense.tracker.data.model.Money
import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.expense.tracker.ocr.OcrRecognizer
import com.expense.tracker.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BillImportScreen(
    vm: BillImportViewModel,
    ocrRecognizer: OcrRecognizer,
    llmPrefs: UserPrefsSnapshot?,
    onBack: () -> Unit,
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var recognizing by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val prefs = llmPrefs
        if (prefs == null) {
            vm.showError("设置尚未加载，请稍后重试")
            return@rememberLauncherForActivityResult
        }
        recognizing = true
        scope.launch {
            runCatching {
                val bitmap = withContext(Dispatchers.IO) { readBitmap(context, uri) }
                try {
                    ocrRecognizer.recognize(bitmap)
                } finally {
                    bitmap.recycle()
                }
            }.onSuccess { text ->
                recognizing = false
                if (text.isBlank()) vm.showError("截图中没有识别到文字")
                else vm.importFromText(text, prefs)
            }.onFailure {
                recognizing = false
                vm.showError("图片识别失败：${it.message ?: "无法读取图片"}")
            }
        }
    }

    Box(Modifier.fillMaxSize().background(AppColors.Bg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.background(AppColors.CardBg, CircleShape)) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = AppColors.TextPrimary)
                }
                Spacer(Modifier.size(12.dp))
                Text("账单截图导入", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
            }
            Spacer(Modifier.size(20.dp))

            when {
                recognizing -> LoadingContent("正在识别截图…")
                state.phase == BillImportPhase.Loading -> LoadingContent("正在分析账单…")
                state.phase == BillImportPhase.Idle -> EmptyContent {
                    picker.launch("image/*")
                }
                state.phase == BillImportPhase.Result -> ResultContent(
                    state = state,
                    vm = vm,
                    onPickAgain = {
                        vm.reset()
                        picker.launch("image/*")
                    },
                )
                state.phase == BillImportPhase.Done -> DoneContent(
                    state = state,
                    onAgain = {
                        vm.reset()
                        picker.launch("image/*")
                    },
                    onBack = onBack,
                )
                else -> ErrorContent(
                    message = state.errorMessage ?: "识别失败",
                    onRetry = {
                        vm.reset()
                        picker.launch("image/*")
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyContent(onPick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.Image, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(56.dp))
        Spacer(Modifier.size(18.dp))
        Text("选择一张支付宝或微信账单截图", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.size(8.dp))
        Text("本地 OCR 识别文字，AI 拆分交易；导入前可以逐笔修改。", color = AppColors.TextSecondary)
        Spacer(Modifier.size(24.dp))
        PrimaryButton("选择账单截图", onPick)
    }
}

@Composable
private fun LoadingContent(label: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = AppColors.Accent)
        Spacer(Modifier.size(16.dp))
        Text(label, color = AppColors.TextPrimary)
    }
}

@Composable
private fun ColumnScope.ResultContent(state: BillImportUiState, vm: BillImportViewModel, onPickAgain: () -> Unit) {
    val selected = state.items.filter { it.selected && it.amountCents > 0L }
    Text("已识别 ${state.items.size} 笔，请核对后导入", color = AppColors.TextSecondary)
    Spacer(Modifier.size(12.dp))
    LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(state.items) { index, item ->
            ExpenseEditor(
                item = item,
                onToggle = { vm.toggleSelected(index) },
                onAmount = { vm.updateAmount(index, it) },
                onCategory = { vm.updateCategory(index, it) },
                onNote = { vm.updateNote(index, it) },
            )
        }
    }
    Spacer(Modifier.size(12.dp))
    Text(
        "将导入 ${selected.size} 笔，合计 ¥${Money.formatYuan(selected.sumOf { it.amountCents })}",
        color = AppColors.TextPrimary,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.size(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onPickAgain, modifier = Modifier.weight(1f)) { Text("换一张") }
        Button(
            onClick = vm::confirmImport,
            enabled = selected.isNotEmpty(),
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = Color.White),
        ) { Text("确认导入") }
    }
}

@Composable
private fun ExpenseEditor(
    item: EditableExpense,
    onToggle: () -> Unit,
    onAmount: (Long) -> Unit,
    onCategory: (String) -> Unit,
    onNote: (String) -> Unit,
) {
    var amountText by remember { mutableStateOf(Money.formatYuan(item.amountCents)) }
    var categoryOpen by remember { mutableStateOf(false) }
    val category = Category.byIdOrOther(item.categoryId)

    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = item.selected, onCheckedChange = { onToggle() })
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { value ->
                        if (value.matches(Regex("\\d{0,8}(\\.\\d{0,2})?"))) {
                            amountText = value
                            if (value.isBlank()) {
                                onAmount(0L)
                            } else {
                                onAmount(runCatching { Money.parseYuanToCents(value) }.getOrDefault(0L))
                            }
                        }
                    },
                    label = { Text("金额") },
                    prefix = { Text("¥") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.size(8.dp))
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppColors.ChipFill, RoundedCornerShape(12.dp))
                        .clickable { categoryOpen = true }
                        .padding(14.dp),
                ) {
                    Text("${category.emoji} ${category.displayName}", color = AppColors.TextPrimary)
                }
                DropdownMenu(expanded = categoryOpen, onDismissRequest = { categoryOpen = false }) {
                    Category.ALL.forEach { option ->
                        DropdownMenuItem(
                            text = { Text("${option.emoji} ${option.displayName}") },
                            onClick = {
                                onCategory(option.id)
                                categoryOpen = false
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(
                value = item.note,
                onValueChange = onNote,
                label = { Text("商户 / 备注") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DoneContent(state: BillImportUiState, onAgain: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("✅", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.size(12.dp))
        Text("已导入 ${state.doneCount} 笔", color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
        Text("合计 ¥${Money.formatYuan(state.doneTotalCents)}", color = AppColors.TextSecondary)
        Spacer(Modifier.size(24.dp))
        PrimaryButton("继续导入", onAgain)
        OutlinedButton(onClick = onBack) { Text("完成") }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("识别失败", color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(8.dp))
        Text(message, color = AppColors.TextSecondary)
        Spacer(Modifier.size(20.dp))
        PrimaryButton("重新选择截图", onRetry)
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = Color.White),
    ) { Text(label) }
}

private fun readBitmap(context: Context, uri: Uri): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    } ?: error("无法读取图片")
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "图片格式无效" }

    var sampleSize = 1
    while (
        bounds.outWidth / sampleSize > MAX_OCR_DIMENSION ||
        bounds.outHeight / sampleSize > MAX_OCR_DIMENSION ||
        (bounds.outWidth.toLong() / sampleSize) * (bounds.outHeight.toLong() / sampleSize) > MAX_OCR_PIXELS
    ) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, options)
    } ?: error("无法解码图片")
}

private const val MAX_OCR_DIMENSION = 8_192
private const val MAX_OCR_PIXELS = 8_000_000L
