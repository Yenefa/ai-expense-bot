package com.expense.tracker.data.repo

import com.expense.tracker.data.db.ChatMessageDao
import com.expense.tracker.data.db.ChatMessageEntity
import com.expense.tracker.data.db.toExpenseIdsCsvOrNull
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val dao: ChatMessageDao) {

    fun observeAll(): Flow<List<ChatMessageEntity>> = dao.observeAll()

    suspend fun appendUser(text: String, at: Long = System.currentTimeMillis()): Long =
        dao.insert(ChatMessageEntity(role = "user", content = text, createdAt = at))

    suspend fun appendAssistant(
        text: String,
        at: Long = System.currentTimeMillis(),
        relatedExpenseId: Long? = null,
        relatedExpenseIds: List<Long> = emptyList(),
    ): Long = dao.insert(ChatMessageEntity(
        role = "assistant",
        content = text,
        createdAt = at,
        relatedExpenseId = relatedExpenseIds.firstOrNull() ?: relatedExpenseId,
        relatedExpenseIdsCsv = relatedExpenseIds.toExpenseIdsCsvOrNull(),
    ))

    suspend fun update(msg: ChatMessageEntity) = dao.update(msg)

    suspend fun getById(id: Long): ChatMessageEntity? = dao.getById(id)

    suspend fun getAllOnce(): List<ChatMessageEntity> = dao.getAllOnce()

    suspend fun getRecent(limit: Int): List<ChatMessageEntity> {
        require(limit in 1..50) { "聊天上下文条数必须在 1..50" }
        return dao.getRecent(limit)
    }

    suspend fun clear() = dao.clearAll()
}
