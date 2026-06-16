package com.expense.tracker.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.model.Category
import com.expense.tracker.data.repo.ExpenseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

data class TrashItem(
    val id: Long,
    val amount: Double,
    val categoryEmoji: String,
    val categoryName: String,
    val note: String,
    val occurredAtLabel: String,    // "6/15 20:00"
    val deletedAtLabel: String,     // "刚刚 / 2小时前 / 6/14"
    val daysUntilPurge: Int,        // 还剩多少天被真删，0 = 今天
)

data class TrashUiState(
    val items: List<TrashItem> = emptyList(),
)

class TrashViewModel(
    private val repo: ExpenseRepository,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val internal = MutableStateFlow(TrashUiState())
    val uiState: StateFlow<TrashUiState> = internal.asStateFlow()

    private val occurredFmt = DateTimeFormatter.ofPattern("M/d HH:mm")
    private val deletedFallbackFmt = DateTimeFormatter.ofPattern("M月d日")
    private val zone = ZoneId.systemDefault()

    init {
        viewModelScope.launch {
            repo.observeTrashed().collect { list ->
                internal.update { it.copy(items = list.map(::toItem)) }
            }
        }
    }

    fun restore(id: Long) {
        viewModelScope.launch { repo.restore(id) }
    }

    fun hardDelete(id: Long) {
        viewModelScope.launch { repo.hardDelete(id) }
    }

    fun emptyTrash() {
        viewModelScope.launch { repo.emptyTrash() }
    }

    private fun toItem(e: ExpenseEntity): TrashItem {
        val cat = Category.byIdOrOther(e.categoryId)
        val now = nowProvider()
        val deletedAt = e.deletedAt ?: now
        val ageMs = now - deletedAt
        val deletedLabel = when {
            ageMs < TimeUnit.MINUTES.toMillis(1) -> "刚刚"
            ageMs < TimeUnit.HOURS.toMillis(1)   -> "${TimeUnit.MILLISECONDS.toMinutes(ageMs)} 分钟前"
            ageMs < TimeUnit.DAYS.toMillis(1)    -> "${TimeUnit.MILLISECONDS.toHours(ageMs)} 小时前"
            ageMs < TimeUnit.DAYS.toMillis(7)    -> "${TimeUnit.MILLISECONDS.toDays(ageMs)} 天前"
            else -> Instant.ofEpochMilli(deletedAt).atZone(zone).format(deletedFallbackFmt)
        }
        // 30 天保留期 — 与 ExpenseApp 启动清理一致
        val purgeAt = deletedAt + TimeUnit.DAYS.toMillis(TRASH_RETENTION_DAYS.toLong())
        val daysLeft = ((purgeAt - now) / TimeUnit.DAYS.toMillis(1)).toInt().coerceAtLeast(0)
        return TrashItem(
            id = e.id,
            amount = e.amount,
            categoryEmoji = cat.emoji,
            categoryName = cat.displayName,
            note = e.note,
            occurredAtLabel = Instant.ofEpochMilli(e.occurredAt).atZone(zone).format(occurredFmt),
            deletedAtLabel = deletedLabel,
            daysUntilPurge = daysLeft,
        )
    }

    companion object {
        const val TRASH_RETENTION_DAYS = 30
    }
}
