package com.expense.tracker.data.action

import com.expense.tracker.data.model.Category
import com.expense.tracker.data.repo.ExpenseRepository
import com.expense.tracker.llm.ParsedAction
import java.util.UUID

/**
 * 把 LLM parser 给出的 [ParsedAction]（结构化条件）翻译成 UI 要展示的 [PendingAction]（含候选行）。
 *
 * 这是唯一连接"协议层"和"UI 层"的业务模块 — 故意做成 stateless object 风格的 class，
 * 便于在 ViewModel 注入 + 单测里 mock。
 */
class PendingActionResolver(private val repo: ExpenseRepository) {

    /** 候选数 0 时返回 [PendingAction.Empty]，从不返回 null（除非 op 不识别）。 */
    suspend fun resolve(action: ParsedAction): PendingAction? {
        return when (action.op) {
            "delete" -> resolveDelete(action)
            "update" -> resolveUpdate(action)
            "query"  -> resolveQuery(action)
            else -> null
        }
    }

    private suspend fun resolveDelete(a: ParsedAction): PendingAction {
        val candidates = repo.findByMatch(
            category = a.matchCategoryId,
            amount = a.matchAmount,
            from = a.matchFromMillis,
            to = a.matchToMillis,
            noteSub = a.matchNoteContains,
        )
        return if (candidates.isEmpty()) {
            PendingAction.Empty(uuid(), "🤔 没找到匹配的支出，要不要换个说法？")
        } else {
            PendingAction.Delete(uuid(), candidates)
        }
    }

    private suspend fun resolveUpdate(a: ParsedAction): PendingAction {
        // patch 全空时直接判失败 — 没有任何字段要改的 update 是无效操作
        if (a.patchAmount == null && a.patchCategoryId == null && a.patchNote == null) {
            return PendingAction.Empty(uuid(), "⚠️ 没说要改成什么，请再描述一下。")
        }
        val candidates = repo.findByMatch(
            category = a.matchCategoryId,
            amount = a.matchAmount,
            from = a.matchFromMillis,
            to = a.matchToMillis,
            noteSub = a.matchNoteContains,
        )
        return if (candidates.isEmpty()) {
            PendingAction.Empty(uuid(), "🤔 没找到要修改的支出。")
        } else {
            PendingAction.Update(
                id = uuid(),
                candidates = candidates,
                patchAmount = a.patchAmount,
                patchCategoryId = a.patchCategoryId,
                patchNote = a.patchNote,
            )
        }
    }

    private suspend fun resolveQuery(a: ParsedAction): PendingAction {
        val rows = repo.findByMatch(
            category = a.matchCategoryId,
            amount = a.matchAmount,
            from = a.matchFromMillis,
            to = a.matchToMillis,
            noteSub = a.matchNoteContains,
        )
        val total = rows.sumOf { it.amount }
        val count = rows.size
        val catLabel = a.matchCategoryId?.let {
            Category.byId(it)?.let { c -> "${c.emoji}${c.displayName}" }
        }
        val text = when (a.aggregate) {
            "count" -> "📊 ${catLabel ?: "全部"} 共 $count 笔"
            else    -> "📊 ${catLabel ?: "全部"} 共 ¥${"%.2f".format(total)}（$count 笔）"
        }
        return PendingAction.QueryResult(uuid(), text)
    }

    private fun uuid(): String = UUID.randomUUID().toString()
}
