package com.expense.tracker.data.repo

import com.expense.tracker.data.db.ExpenseDao
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.importer.ImportedExpense
import kotlinx.coroutines.flow.Flow

data class ExpenseImportSummary(
    val inserted: Int,
    val skippedDuplicates: Int,
)

data class ExpenseDraft(
    val amountCents: Long,
    val categoryId: String,
    val note: String,
    val occurredAt: Long,
)

class ExpenseRepository(private val dao: ExpenseDao) {

    /** 活跃记录（给聊天列表、分析、历史用） */
    fun observeActive(): Flow<List<ExpenseEntity>> = dao.observeActive()

    fun observeInRange(fromMillis: Long, toMillis: Long): Flow<List<ExpenseEntity>> =
        dao.observeInRange(fromMillis, toMillis)

    suspend fun addCents(amountCents: Long, categoryId: String, note: String, occurredAt: Long): Long {
        require(amountCents > 0L) { "金额必须大于 0" }
        val now = System.currentTimeMillis()
        return dao.insert(ExpenseEntity(
            amountCents = amountCents,
            categoryId = categoryId,
            note = note,
            occurredAt = occurredAt,
            createdAt = now,
        ))
    }

    /** 单次批量写入，供截图拆单等多笔导入使用。 */
    suspend fun addAllCents(rows: List<ExpenseDraft>): List<Long> {
        rows.forEach { require(it.amountCents > 0L) { "金额必须大于 0" } }
        val createdAt = System.currentTimeMillis()
        return dao.insertAll(rows.map { row ->
            ExpenseEntity(
                amountCents = row.amountCents,
                categoryId = row.categoryId,
                note = row.note,
                occurredAt = row.occurredAt,
                createdAt = createdAt,
            )
        })
    }

    /** 硬删除（彻底移除），v2.8 及以前的行为。 */
    suspend fun hardDelete(id: Long) = dao.deleteById(id)

    /** 软删除：标记时间戳，保留行。 */
    suspend fun softDelete(id: Long) = dao.softDeleteById(id, System.currentTimeMillis())

    /** 恢复软删除的记录。 */
    suspend fun restore(id: Long) = dao.restoreById(id)

    /** 冷删除记录列表（观察流）。 */
    fun observeDeleted(): Flow<List<ExpenseEntity>> = dao.observeDeleted()

    /** 彻底删除指定 id。 */
    suspend fun purge(id: Long) = dao.deleteById(id)

    /** 清理 N 天前软删除的过期记录。 */
    suspend fun purgeOlderThan(cutoffMillis: Long) = dao.purgeOlderThan(cutoffMillis)

    suspend fun update(expense: ExpenseEntity) = dao.update(expense)

    suspend fun getById(id: Long): ExpenseEntity? = dao.getById(id)

    /** 一次拉取全部活跃记录（给导出用）。 */
    suspend fun getAllActiveOnce(): List<ExpenseEntity> = dao.getAllActiveOnce()

    suspend fun importExpenses(rows: List<ImportedExpense>): ExpenseImportSummary {
        val seen = dao.getAllOnce().mapTo(HashSet()) { it.importKey() }
        val toInsert = ArrayList<ExpenseEntity>(rows.size)
        var skipped = 0

        rows.forEach { row ->
            val entity = ExpenseEntity(
                amountCents = row.amountCents,
                categoryId = row.categoryId,
                note = row.note,
                occurredAt = row.occurredAt,
                createdAt = row.createdAt,
            )
            if (seen.add(entity.importKey())) {
                toInsert += entity
            } else {
                skipped++
            }
        }

        if (toInsert.isNotEmpty()) dao.insertAll(toInsert)
        return ExpenseImportSummary(
            inserted = toInsert.size,
            skippedDuplicates = skipped,
        )
    }

    private data class ImportKey(
        val amountCents: Long,
        val categoryId: String,
        val note: String,
        val occurredAt: Long,
        val createdAt: Long,
    )

    private fun ExpenseEntity.importKey() = ImportKey(
        amountCents = amountCents,
        categoryId = categoryId,
        note = note,
        occurredAt = occurredAt,
        createdAt = createdAt,
    )
}
