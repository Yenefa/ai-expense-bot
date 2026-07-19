package com.expense.tracker.ui.billimport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expense.tracker.data.model.Category
import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.expense.tracker.ocr.OcrRecognizer
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.iconBtnShadow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun BillImportScreen(
    vm: BillImportViewModel,
    ocrRecognizer: OcrRecognizer,
    llmPrefs: UserPrefsSnapshot?,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val state by vm.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val prefs = llmPrefs ?: return@rememberLauncherForActivityResult
        scope.launch {
            val bitmap = runCatching { decodeSampledBitmap(context, uri) }.getOrNull()
            if (bitmap == null) return@launch
            val ocrText = runCatching { ocrRecognizer.recognize(bitmap) }.getOrElse { "" }
            vm.importFromText(ocrText, prefs)
        }
    }

    // 进入即唤起选图；若重进且上次停在 Done，先重置再选图，避免卡在终态
    LaunchedEffect(Unit) {
        when (state.phase) {
            BillImportPhase.Done -> {
                vm.reset()
                pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            BillImportPhase.Idle ->
                pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            else -> {}
        }
    }
    // 完成即返回
    LaunchedEffect(state.phase) {
        if (state.phase == BillImportPhase.Done) onDone()
    }

    Column(
        modifier = Modifier.fillMaxSize().background(AppColors.Bg).verticalScroll(rememberScrollState()),
    ) {
        // 顶部栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(36.dp).iconBtnShadow().clip(CircleShape).background(AppColors.Bg)
                    .pointerInput(onBack) { detectTapGestures(onTap = { onBack() }) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = AppColors.TextPrimary) }
            Spacer(Modifier.size(12.dp))
            Text("📸 截图记账", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
        }

        when (state.phase) {
            BillImportPhase.Idle -> {
                Spacer(Modifier.height(80.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("🖼️", fontSize = 48.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("选择一张支付宝/微信账单截图，AI 自动识别每笔交易",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextSecondary,
                        lineHeight = 22.sp)
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.TextPrimary, contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("选择截图") }
                }
            }
            BillImportPhase.Loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AppColors.TextPrimary, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("正在识别截图...", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            BillImportPhase.Result -> {
                Spacer(Modifier.height(8.dp))
                state.items.forEachIndexed { index, item ->
                    EditableExpenseCard(
                        index = index,
                        item = item,
                        onToggle = vm::toggleSelected,
                        onAmountChange = vm::updateAmount,
                        onCategoryChange = vm::updateCategory,
                        onNoteChange = vm::updateNote,
                    )
                }
                val selected = state.items.filter { it.selected }
                val total = selected.sumOf { it.amount }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = vm::confirmImport,
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.TextPrimary, contentColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("导入 ${selected.size} 笔 · ¥${"%.2f".format(total)}") }
            }
            BillImportPhase.Error -> {
                Spacer(Modifier.height(32.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5F5)),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("😵", fontSize = 36.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(state.errorMessage ?: "识别失败", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.TextPrimary, contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("重选图片") }
                    }
                }
            }
            BillImportPhase.Done -> {
                Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                    Text("✅ 已导入 ${state.doneCount} 笔，合计 ¥${"%.2f".format(state.doneTotal)}",
                        color = AppColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun EditableExpenseCard(
    index: Int,
    item: EditableExpense,
    onToggle: (Int) -> Unit,
    onAmountChange: (Int, Double) -> Unit,
    onCategoryChange: (Int, String) -> Unit,
    onNoteChange: (Int, String) -> Unit,
) {
    // 本地文本态，避免直接绑 VM 导致的光标跳动（列表静态，按 index 记忆即可）
    var amountText by remember(index) { mutableStateOf("%.2f".format(item.amount)) }
    var noteText by remember(index) { mutableStateOf(item.note) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (item.selected) Color.White else Color(0xFFF2F2F4)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(24.dp).clip(CircleShape)
                        .background(if (item.selected) AppColors.Accent else Color(0xFFD8D8DD))
                        .pointerInput(index) { detectTapGestures(onTap = { onToggle(index) }) },
                    contentAlignment = Alignment.Center,
                ) { if (item.selected) Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(8.dp))
                Text("¥${"%.2f".format(item.amount)}", style = MaterialTheme.typography.titleMedium,
                    color = if (item.selected) AppColors.TextPrimary else AppColors.TextMuted, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            // 金额
            OutlinedTextField(
                value = amountText,
                onValueChange = { v ->
                    amountText = v
                    v.toDoubleOrNull()?.let { onAmountChange(index, it) }
                },
                label = { Text("金额") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            // 备注/商户
            OutlinedTextField(
                value = noteText,
                onValueChange = { v -> noteText = v; onNoteChange(index, v) },
                label = { Text("备注/商户") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            // 分类
            Text("分类", style = MaterialTheme.typography.labelMedium, color = AppColors.TextSecondary)
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Category.ALL.forEach { cat ->
                    val selected = item.categoryId == cat.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) AppColors.Accent else Color(0xFFEFEFF2))
                            .pointerInput(cat.id) { detectTapGestures(onTap = { onCategoryChange(index, cat.id) }) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) { Text("${cat.emoji} ${cat.displayName}", color = if (selected) Color.White else AppColors.TextPrimary, fontSize = 13.sp) }
                }
            }
            // 时间（只读展示；v1 不在确认页编辑，可在历史明细页改）
            item.occurredAtMillis?.let { ms ->
                Spacer(Modifier.height(8.dp))
                val ts = runCatching {
                    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                }.getOrDefault("")
                if (ts.isNotEmpty()) {
                    Text("⏱ $ts", style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
                }
            }
        }
    }
}

/** 按需降采样解码，防大图 OOM。 */
private fun decodeSampledBitmap(context: Context, uri: Uri): Bitmap {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    val targetMax = 2000
    var sample = 1
    while ((opts.outWidth / sample) > targetMax || (opts.outHeight / sample) > targetMax) sample *= 2
    val opts2 = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts2) }
        ?: error("无法读取图片")
}
