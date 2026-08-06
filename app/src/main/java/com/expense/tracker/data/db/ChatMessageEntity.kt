package com.expense.tracker.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    val role: String,
    val content: String,
    val createdAt: Long,
    val relatedExpenseId: Long? = null,
    /** 一次 AI 操作涉及的完整账目批次。旧数据为空时回退到 relatedExpenseId。 */
    val relatedExpenseIdsCsv: String? = null,
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
) {
    fun relatedExpenseIds(): List<Long> {
        val parsed = relatedExpenseIdsCsv
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.filter { it > 0L }
            ?.distinct()
            .orEmpty()
        return if (parsed.isNotEmpty()) parsed else listOfNotNull(relatedExpenseId?.takeIf { it > 0L })
    }
}

internal fun List<Long>.toExpenseIdsCsvOrNull(): String? =
    distinct().takeIf { ids -> ids.isNotEmpty() && ids.all { it > 0L } }?.joinToString(",")
