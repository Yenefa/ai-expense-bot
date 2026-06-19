package com.expense.tracker.data.repo

import com.expense.tracker.data.db.ChatMessageDao
import com.expense.tracker.data.db.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val dao: ChatMessageDao) {

    fun observeAll(): Flow<List<ChatMessageEntity>> = dao.observeAll()

    suspend fun appendUser(text: String, at: Long = System.currentTimeMillis()): Long =
        dao.insert(ChatMessageEntity(role = "user", content = text, createdAt = at))

    suspend fun appendAssistant(
        text: String,
        at: Long = System.currentTimeMillis(),
        relatedExpenseId: Long? = null,
    ): Long = dao.insert(ChatMessageEntity(
        role = "assistant",
        content = text,
        createdAt = at,
        relatedExpenseId = relatedExpenseId,
    ))

    suspend fun update(msg: ChatMessageEntity) = dao.update(msg)

    suspend fun getById(id: Long): ChatMessageEntity? = dao.getById(id)

    suspend fun getAllOnce(): List<ChatMessageEntity> = dao.getAllOnce()

    suspend fun clear() = dao.clearAll()
}
