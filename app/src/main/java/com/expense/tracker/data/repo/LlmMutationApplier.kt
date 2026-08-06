package com.expense.tracker.data.repo

import androidx.room.withTransaction
import com.expense.tracker.data.db.AppDatabase
import com.expense.tracker.data.model.Category
import com.expense.tracker.llm.LlmMutationPlan
import com.expense.tracker.llm.MutationConflictException
import com.expense.tracker.llm.ParsedAction

interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

class RoomTransactionRunner(
    private val database: AppDatabase,
) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T =
        database.withTransaction { block() }
}

class LlmMutationApplier(
    private val expenseRepository: ExpenseRepository,
    private val chatRepository: ChatRepository,
    private val transactionRunner: TransactionRunner,
) {
    suspend fun apply(plan: LlmMutationPlan): MutationApplyResult = transactionRunner.run {
        plan.targetSnapshots.forEach { (id, expected) ->
            val current = expenseRepository.getById(id)
            if (current == null || current.deletedAt != null || current != expected) {
                throw MutationConflictException("账目已发生变化，请重新发起操作。")
            }
        }

        val actionIds = plan.result.actions.mapNotNull { action ->
            when (action) {
                is ParsedAction.Delete -> action.expenseId
                is ParsedAction.Update -> action.expenseId
                is ParsedAction.Add -> null
            }
        }
        if (actionIds.toSet() != plan.targetSnapshots.keys || actionIds.size != actionIds.distinct().size) {
            throw MutationConflictException("待执行计划与账目快照不一致，请重新发起操作。")
        }

        val insertedIds = plan.result.expenses.map { item ->
            expenseRepository.addCents(
                amountCents = item.amountCents,
                categoryId = item.categoryId,
                note = item.note,
                occurredAt = item.occurredAtMillis
                    ?: throw MutationConflictException("待执行计划缺少明确日期。"),
            )
        }
        applyActions(plan.result.actions)
        val affectedIds = (insertedIds + actionIds).distinct()
        val assistantMessageId = chatRepository.appendAssistant(
            text = plan.result.reply,
            relatedExpenseIds = affectedIds,
        )
        MutationApplyResult(
            insertedIds = insertedIds,
            affectedIds = affectedIds,
            assistantMessageId = assistantMessageId,
        )
    }

    private suspend fun applyActions(actions: List<ParsedAction>) {
        actions.forEach { action ->
            when (action) {
                is ParsedAction.Delete -> expenseRepository.softDelete(action.expenseId)
                is ParsedAction.Update -> {
                    val existing = expenseRepository.getById(action.expenseId)
                        ?: throw MutationConflictException("账目已不存在，请重新发起操作。")
                    val categoryId = action.categoryId ?: existing.categoryId
                    if (Category.byId(categoryId) == null) {
                        throw MutationConflictException("待执行计划包含无效分类，本次未修改。")
                    }
                    expenseRepository.update(
                        existing.copy(
                            amountCents = action.amountCents ?: existing.amountCents,
                            categoryId = categoryId,
                            note = action.note ?: existing.note,
                            occurredAt = action.occurredAtMillis ?: existing.occurredAt,
                        ),
                    )
                }
                is ParsedAction.Add -> Unit
            }
        }
    }
}

data class MutationApplyResult(
    val insertedIds: List<Long>,
    val affectedIds: List<Long>,
    val assistantMessageId: Long,
)
