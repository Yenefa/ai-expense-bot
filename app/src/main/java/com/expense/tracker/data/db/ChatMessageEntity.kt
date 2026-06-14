package com.expense.tracker.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    val role: String,
    val content: String,
    val createdAt: Long,
    val relatedExpenseId: Long? = null,
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
)
