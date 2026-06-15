package com.expense.tracker.data.action

import com.expense.tracker.data.db.ExpenseDao
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.repo.ExpenseRepository
import com.expense.tracker.llm.ParsedAction
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PendingActionResolverTest {

    @Test fun deleteWithCandidatesReturnsDelete() = runTest {
        val dao = FakeMatchDao(
            ExpenseEntity(amount = 18.0, categoryId = "drink", note = "咖啡",
                          occurredAt = 1_000L, createdAt = 1_000L, id = 1),
        )
        val resolver = PendingActionResolver(ExpenseRepository(dao))
        val action = parsedDelete(category = "drink", amount = 18.0)
        val pending = resolver.resolve(action)
        assertThat(pending).isInstanceOf(PendingAction.Delete::class.java)
        assertThat((pending as PendingAction.Delete).candidates).hasSize(1)
    }

    @Test fun deleteWithNoCandidatesReturnsEmpty() = runTest {
        val resolver = PendingActionResolver(ExpenseRepository(FakeMatchDao()))
        val pending = resolver.resolve(parsedDelete(category = "drink"))
        assertThat(pending).isInstanceOf(PendingAction.Empty::class.java)
    }

    @Test fun updateWithEmptyPatchReturnsEmpty() = runTest {
        val dao = FakeMatchDao(
            ExpenseEntity(amount = 35.0, categoryId = "food", note = "",
                          occurredAt = 1L, createdAt = 1L, id = 1),
        )
        val resolver = PendingActionResolver(ExpenseRepository(dao))
        val action = ParsedAction(
            op = "update",
            matchCategoryId = "food", matchAmount = 35.0,
            matchFromMillis = null, matchToMillis = null, matchNoteContains = null,
            patchAmount = null, patchCategoryId = null, patchNote = null,
            aggregate = null,
        )
        val pending = resolver.resolve(action)
        assertThat(pending).isInstanceOf(PendingAction.Empty::class.java)
    }

    @Test fun updateWithPatchReturnsUpdate() = runTest {
        val dao = FakeMatchDao(
            ExpenseEntity(amount = 35.0, categoryId = "food", note = "",
                          occurredAt = 1L, createdAt = 1L, id = 1),
        )
        val resolver = PendingActionResolver(ExpenseRepository(dao))
        val action = ParsedAction(
            op = "update",
            matchCategoryId = "food", matchAmount = 35.0,
            matchFromMillis = null, matchToMillis = null, matchNoteContains = null,
            patchAmount = 40.0, patchCategoryId = null, patchNote = null,
            aggregate = null,
        )
        val pending = resolver.resolve(action) as PendingAction.Update
        assertThat(pending.patchAmount).isEqualTo(40.0)
        assertThat(pending.candidates).hasSize(1)
    }

    @Test fun querySumRendersText() = runTest {
        val dao = FakeMatchDao(
            ExpenseEntity(amount = 35.0, categoryId = "food", note = "", occurredAt = 1L, createdAt = 1L, id = 1),
            ExpenseEntity(amount = 22.5, categoryId = "food", note = "", occurredAt = 2L, createdAt = 2L, id = 2),
        )
        val resolver = PendingActionResolver(ExpenseRepository(dao))
        val action = ParsedAction(
            op = "query",
            matchCategoryId = "food", matchAmount = null,
            matchFromMillis = null, matchToMillis = null, matchNoteContains = null,
            patchAmount = null, patchCategoryId = null, patchNote = null,
            aggregate = "sum",
        )
        val pending = resolver.resolve(action) as PendingAction.QueryResult
        assertThat(pending.text).contains("57.50")
        assertThat(pending.text).contains("2 笔")
    }

    @Test fun unknownOpReturnsNull() = runTest {
        val resolver = PendingActionResolver(ExpenseRepository(FakeMatchDao()))
        val action = ParsedAction(
            op = "burn-everything",
            matchCategoryId = null, matchAmount = null,
            matchFromMillis = null, matchToMillis = null, matchNoteContains = null,
            patchAmount = null, patchCategoryId = null, patchNote = null,
            aggregate = null,
        )
        assertThat(resolver.resolve(action)).isNull()
    }

    private fun parsedDelete(category: String? = null, amount: Double? = null) = ParsedAction(
        op = "delete",
        matchCategoryId = category, matchAmount = amount,
        matchFromMillis = null, matchToMillis = null, matchNoteContains = null,
        patchAmount = null, patchCategoryId = null, patchNote = null,
        aggregate = null,
    )
}

/**
 * 单测里只用 findByMatch；其它 DAO 方法用错误抛 — 验证我们没意外调到。
 *
 * findByMatch 模拟了真实 SQL 行为：null 参数 = 不限制；amount 用 epsilon 比；
 * 这不是为了完全等价 SQLite，只是覆盖测试用例。
 */
private class FakeMatchDao(vararg rows: ExpenseEntity) : ExpenseDao {
    private val data = rows.toList()
    override suspend fun findByMatch(
        category: String?, amount: Double?, from: Long?, to: Long?, noteSub: String?,
    ): List<ExpenseEntity> = data.filter {
        (category == null || it.categoryId == category) &&
        (amount   == null || kotlin.math.abs(it.amount - amount) < 0.01) &&
        (from     == null || it.occurredAt >= from) &&
        (to       == null || it.occurredAt < to) &&
        (noteSub  == null || it.note.contains(noteSub))
    }
    override suspend fun insert(expense: ExpenseEntity): Long = error("unused")
    override fun observeAll(): Flow<List<ExpenseEntity>> = flowOf(data)
    override fun observeInRange(from: Long, to: Long): Flow<List<ExpenseEntity>> = flowOf(data)
    override suspend fun deleteById(id: Long) = error("unused")
    override suspend fun patchById(id: Long, amount: Double?, cat: String?, note: String?) = error("unused")
}
