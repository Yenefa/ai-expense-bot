package com.expense.tracker.agent

import com.expense.tracker.data.budget.BudgetEntry
import com.expense.tracker.data.budget.BudgetStatus
import com.expense.tracker.data.model.Money

/** 查询轮的 system prompt 片段：工具结果 + 回答规则。 */
object AgentPrompts {

    fun toolResultBlock(query: QueryToolResult, budget: BudgetToolResult?): String = buildString {
        appendLine("【查询结果 — 本地数据库实时计算，可信数据】")
        appendLine("时段：${query.periodLabel}")
        if (query.filteredCategories.isNotEmpty()) {
            appendLine(
                "分类过滤（用户提及）：${query.filteredCategories.joinToString("、") { AgentCategories.displayName(it) }}",
            )
        }
        appendLine("共 ${query.totalCount} 笔，消费合计 ¥${Money.formatYuan(query.totalCents)}")
        if (query.byCategory.isNotEmpty()) {
            appendLine(
                "按分类：" + query.byCategory.joinToString("；") { agg ->
                    "${AgentCategories.displayName(agg.categoryId)} ${agg.count} 笔 ¥${Money.formatYuan(agg.amountCents)}"
                },
            )
        }
        if (query.investmentCents > 0L) {
            appendLine("另：投资类 ¥${Money.formatYuan(query.investmentCents)} 不计入消费合计。")
        }
        if (query.topNotes.isNotEmpty()) {
            appendLine(
                "高频商户/备注：" + query.topNotes.joinToString("；") { note ->
                    "${note.note} ${note.count} 笔 ¥${Money.formatYuan(note.amountCents)}"
                },
            )
        }
        budget?.let { appendBudget(it) }
        if (query.records.isEmpty()) {
            appendLine("该时段没有匹配的消费记录。")
        } else {
            appendLine("明细（时间倒序，最多 ${AgentTools.MAX_RECORD_LINES} 条，格式 id|时间|分类|金额|备注）：")
            query.records.forEach { appendLine(it.render(java.time.ZoneId.systemDefault())) }
            if (query.recordsTruncated) {
                appendLine("（明细仅展示最近 ${query.records.size} 条，总计以笔数与合计为准）")
            }
        }
    }

    fun queryDirective(): String = buildString {
        appendLine()
        appendLine("=== 本轮是查询轮：用户在问数据，不是在记账 ===")
        appendLine("- 优先直接回答问题，所有金额、笔数、占比必须来自【查询结果】，禁止编造或口算出不同数字")
        appendLine("- expenses 与 actions 输出空数组；除非用户本轮明确报了新账目（金额+内容），才照常输出 expenses")
        appendLine("- 查询结果为空时如实说明该时段没有匹配记录，不要编造")
        appendLine("- reply ≤120 字，先给结论再给 1-2 个关键数字")
    }

    private fun StringBuilder.appendBudget(budget: BudgetToolResult) {
        appendLine("预算状态：")
        budget.monthly?.let { appendLine("月度总预算：${renderEntry(it)}，本月还剩 ${budget.daysLeftInMonth} 天") }
        budget.categories.forEach { appendLine("分类预算·${AgentCategories.displayName(it.label)}：${renderEntry(it)}") }
    }

    private fun renderEntry(entry: BudgetEntry): String = buildString {
        append("已用 ¥${Money.formatYuan(entry.amountCents)} / 预算 ¥${Money.formatYuan(entry.limitCents)}（${(entry.percent * 100).toInt()}%）")
        when (entry.status) {
            BudgetStatus.OVER -> append("，已超支 ¥${Money.formatYuan(entry.amountCents - entry.limitCents)}")
            BudgetStatus.WARN -> append("，接近上限")
            BudgetStatus.OK -> Unit
        }
    }
}
