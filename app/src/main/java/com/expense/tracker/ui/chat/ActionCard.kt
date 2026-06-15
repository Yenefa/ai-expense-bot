package com.expense.tracker.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.action.PendingAction
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.model.Category
import com.expense.tracker.ui.theme.AppColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 渲染一张待用户确认的操作卡片。
 *
 * 4 种形态：
 *  - Delete   — 候选行 + 复选框，主按钮高亮"删除"
 *  - Update   — 候选行 + 复选框 + patch 预览（"金额→¥40"），主按钮"修改"
 *  - QueryResult — 一句聚合文本 + 单按钮"知道了"
 *  - Empty    — 一句失败文本 + 单按钮"知道了"
 */
@Composable
fun ActionCard(
    action: PendingAction,
    onConfirmDelete: (actionId: String, selected: Set<Long>) -> Unit,
    onConfirmUpdate: (actionId: String, selected: Set<Long>) -> Unit,
    onDismiss: (actionId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF8F9FA))
            .border(1.dp, Color(0xFFE0E2E5), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        when (action) {
            is PendingAction.Delete -> DeleteBody(action, onConfirmDelete, onDismiss)
            is PendingAction.Update -> UpdateBody(action, onConfirmUpdate, onDismiss)
            is PendingAction.QueryResult -> SimpleBody(action.text, action.id, onDismiss)
            is PendingAction.Empty -> SimpleBody(action.message, action.id, onDismiss)
        }
    }
}

@Composable
private fun DeleteBody(
    action: PendingAction.Delete,
    onConfirm: (String, Set<Long>) -> Unit,
    onDismiss: (String) -> Unit,
) {
    // 候选数 1 时默认勾选；多个时让用户主动选
    val initialSelection = if (action.candidates.size == 1) setOf(action.candidates[0].id) else emptySet()
    var selected by remember(action.id) { mutableStateOf(initialSelection) }

    Column {
        Text(
            text = "🗑️ 即将删除（${action.candidates.size} 笔候选）",
            color = AppColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(10.dp))
        action.candidates.forEach { row ->
            CandidateRow(
                row = row,
                checked = row.id in selected,
                onToggle = {
                    selected = if (row.id in selected) selected - row.id else selected + row.id
                },
            )
        }
        Spacer(Modifier.height(12.dp))
        ConfirmRow(
            actionId = action.id,
            primaryLabel = "删除",
            primaryEnabled = selected.isNotEmpty(),
            primaryColor = Color(0xFFE5484D),
            onConfirm = { onConfirm(action.id, selected) },
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun UpdateBody(
    action: PendingAction.Update,
    onConfirm: (String, Set<Long>) -> Unit,
    onDismiss: (String) -> Unit,
) {
    val initialSelection = if (action.candidates.size == 1) setOf(action.candidates[0].id) else emptySet()
    var selected by remember(action.id) { mutableStateOf(initialSelection) }

    val patchPreview = buildList {
        action.patchAmount?.let { add("金额 → ¥${"%.2f".format(it)}") }
        action.patchCategoryId?.let { Category.byId(it)?.let { c -> add("分类 → ${c.emoji}${c.displayName}") } }
        action.patchNote?.let { add("备注 → $it") }
    }.joinToString("，")

    Column {
        Text(
            text = "✏️ 即将修改（${action.candidates.size} 笔候选）",
            color = AppColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (patchPreview.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = patchPreview,
                color = AppColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(10.dp))
        action.candidates.forEach { row ->
            CandidateRow(
                row = row,
                checked = row.id in selected,
                onToggle = {
                    selected = if (row.id in selected) selected - row.id else selected + row.id
                },
            )
        }
        Spacer(Modifier.height(12.dp))
        ConfirmRow(
            actionId = action.id,
            primaryLabel = "修改",
            primaryEnabled = selected.isNotEmpty(),
            primaryColor = AppColors.Accent,
            onConfirm = { onConfirm(action.id, selected) },
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun SimpleBody(text: String, actionId: String, onDismiss: (String) -> Unit) {
    Column {
        Text(
            text = text,
            color = AppColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            CardButton(label = "知道了", color = AppColors.TextSecondary, onClick = { onDismiss(actionId) })
        }
    }
}

@Composable
private fun CandidateRow(row: ExpenseEntity, checked: Boolean, onToggle: () -> Unit) {
    val cat = Category.byIdOrOther(row.categoryId)
    val date = remember(row.occurredAt) {
        Instant.ofEpochMilli(row.occurredAt).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("M/d HH:mm"))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onToggle() }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked)
        Spacer(Modifier.width(10.dp))
        Text("${cat.emoji} ${cat.displayName}", color = AppColors.TextPrimary,
             style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        Text("¥${"%.2f".format(row.amount)}", color = AppColors.TextPrimary,
             fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        if (row.note.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text("· ${row.note}", color = AppColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.weight(1f))
        Text(date, color = AppColors.TextMuted, style = MaterialTheme.typography.bodySmall)
    }
}

/** 极简方框 checkbox — 不引入 Material Checkbox 的额外重量；自绘一个圈/勾。 */
@Composable
private fun Checkbox(checked: Boolean) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(if (checked) AppColors.Accent else Color.Transparent)
            .border(1.5.dp, if (checked) AppColors.Accent else AppColors.TextMuted, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Text("✓", color = Color.White, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ConfirmRow(
    actionId: String,
    primaryLabel: String,
    primaryEnabled: Boolean,
    primaryColor: Color,
    onConfirm: () -> Unit,
    onDismiss: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        CardButton(label = "取消", color = AppColors.TextSecondary, onClick = { onDismiss(actionId) })
        Spacer(Modifier.width(10.dp))
        CardButton(
            label = primaryLabel,
            color = if (primaryEnabled) primaryColor else AppColors.TextMuted,
            onClick = { if (primaryEnabled) onConfirm() },
        )
    }
}

@Composable
private fun CardButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium,
             fontWeight = FontWeight.Medium)
    }
}
