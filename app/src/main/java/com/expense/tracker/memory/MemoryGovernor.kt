package com.expense.tracker.memory

import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Memory 治理器：**唯一的长期记忆写入口是 `confirm`（人类确认）**。
 *
 * 流程：propose（检测 + 类型校验 + 挂起）→ 预览 → confirm → persist；cancel 丢弃。
 * LLM 不持有任何写接口，因此"模型从闲聊里偷偷写长期记忆"在结构上不可能发生。
 */
class MemoryGovernor(
    private val store: UserProfileStore,
    private val nowProvider: () -> Long = System::currentTimeMillis,
    private val tokenProvider: () -> String = { UUID.randomUUID().toString() },
) {
    private data class Pending(val fact: MemoryFact, val createdAt: Long)

    private val pending = LinkedHashMap<String, Pending>()

    /** 已确认的长期记忆（只读）。 */
    val facts: Flow<List<MemoryFact>> = store.facts

    fun propose(text: String): MemoryProposal? {
        val draft = MemoryProposalDetector.detect(text) ?: return null
        if (!MemoryTypeValidator.validate(draft)) return null
        val now = nowProvider()
        val fact = MemoryFact(
            type = draft.type,
            amountCents = draft.amountCents,
            merchant = draft.merchant,
            categoryId = draft.categoryId,
            rawText = draft.rawText.take(MAX_RAW_TEXT_CHARS),
            createdAt = now,
            id = tokenProvider(),
        )
        val token = tokenProvider()
        synchronized(pending) {
            discardExpiredLocked(now)
            while (pending.size >= MAX_PENDING) {
                pending.remove(pending.keys.first())
            }
            pending[token] = Pending(fact, now)
        }
        return MemoryProposal(token, fact)
    }

    /** 人类确认：唯一允许 persist 的路径；token 一次性。 */
    suspend fun confirm(token: String): MemoryFact? {
        val now = nowProvider()
        val confirmed = synchronized(pending) {
            discardExpiredLocked(now)
            pending.remove(token)
        } ?: return null
        store.append(confirmed.fact)
        return confirmed.fact
    }

    fun cancel(token: String): Boolean = synchronized(pending) {
        discardExpiredLocked(nowProvider())
        pending.remove(token) != null
    }

    fun pendingCount(): Int = synchronized(pending) {
        discardExpiredLocked(nowProvider())
        pending.size
    }

    private fun discardExpiredLocked(now: Long) {
        pending.entries.removeAll { now - it.value.createdAt > PENDING_TTL_MS }
    }

    companion object {
        const val MAX_PENDING = 5
        const val PENDING_TTL_MS = 10 * 60_000L
        const val MAX_RAW_TEXT_CHARS = 120
    }
}
