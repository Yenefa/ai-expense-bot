package com.expense.tracker.bench

/**
 * ProactiveInsightBench 评测器（纯函数）。
 * 五个指标全部按"必须为 0"设计：误报、漏报、重复、冷启动、每日额度。
 */
object ProactiveInsightEvaluator {

    data class Observation(
        val fired: Boolean = false,
        val type: String? = null,
        val severity: String? = null,
        val copy: String? = null,
    )

    data class Report(val cases: List<ProactiveBenchCase>, val observations: Map<String, Observation>) {

        private val shouldAlert = cases.filter { it.bucket == ProactiveBenchDataset.SHOULD_ALERT }
        private val suppressed = cases.filter { it.bucket in SUPPRESSED_BUCKETS }
        private val cooldown = cases.filter { it.bucket == ProactiveBenchDataset.SUPPRESSED_COOLDOWN }
        private val coldStart = cases.filter { it.bucket == ProactiveBenchDataset.SUPPRESSED_COLD_START }
        private val dailyBudget = cases.filter { it.bucket == ProactiveBenchDataset.SUPPRESSED_DAILY_BUDGET }

        private fun fired(case: ProactiveBenchCase): Boolean = observations[case.id]?.fired == true

        fun missedAlerts(): List<ProactiveBenchCase> = shouldAlert.filterNot(::fired)

        fun falseAlerts(): List<ProactiveBenchCase> = suppressed.filter(::fired)

        fun duplicates(): List<ProactiveBenchCase> = cooldown.filter(::fired)

        fun coldStartViolations(): List<ProactiveBenchCase> = coldStart.filter(::fired)

        fun budgetViolations(): List<ProactiveBenchCase> = dailyBudget.filter(::fired)

        fun missedAlertRate(): Double = ratio(missedAlerts().size, shouldAlert.size)

        fun falseAlertRate(): Double = ratio(falseAlerts().size, suppressed.size)

        fun duplicateAlertRate(): Double = ratio(duplicates().size, cooldown.size)

        fun coldStartViolationRate(): Double = ratio(coldStartViolations().size, coldStart.size)

        fun notificationBudgetViolationRate(): Double = ratio(budgetViolations().size, dailyBudget.size)

        fun copyCoverage(): Double = ratio(shouldAlert.count { !observations[it.id]?.copy.isNullOrBlank() }, shouldAlert.size)

        fun toMarkdown(source: String, metadata: List<String> = emptyList()): String = buildString {
            appendLine("# ProactiveInsightBench — 主动提醒报告")
            appendLine()
            appendLine("- 数据集：`app/src/test/resources/expensebench/proactive-cases.jsonl`，共 ${cases.size} 条")
            appendLine("- 被测对象：确定性规则引擎 + 治理约束（stub 文案层；LLM 不参与决策，本地零网络）")
            appendLine("- 数据来源：$source")
            metadata.forEach { appendLine("- $it") }
            appendLine()
            appendLine("## 五个指标（目标全为 0）")
            appendLine()
            appendLine("- False Alert Rate = ${pct(falseAlertRate())}（${falseAlerts().size}/${suppressed.size}）")
            appendLine("- Missed Alert Rate = ${pct(missedAlertRate())}（${missedAlerts().size}/${shouldAlert.size}）")
            appendLine("- Duplicate Alert Rate = ${pct(duplicateAlertRate())}（${duplicates().size}/${cooldown.size}）")
            appendLine("- Cold-start Violation Rate = ${pct(coldStartViolationRate())}（${coldStartViolations().size}/${coldStart.size}）")
            appendLine("- Notification Budget Violation Rate = ${pct(notificationBudgetViolationRate())}（${budgetViolations().size}/${dailyBudget.size}）")
            appendLine("- 文案覆盖率（应提醒用例）= ${pct(copyCoverage())}")
            appendLine()
            appendLine("| 桶 | 条数 | 实际提醒 |")
            appendLine("| --- | --- | --- |")
            ProactiveBenchDataset.BUCKETS.forEach { bucket ->
                val list = cases.filter { it.bucket == bucket }
                appendLine("| $bucket | ${list.size} | ${list.count(::fired)} |")
            }
            appendLine()
            appendLine("口径：规则先决策，治理层再套硬约束（每日 1 条、同类冷却、可关闭）；文案层不参与是否提醒。")
        }.trimEnd() + "\n"

        private fun ratio(part: Int, total: Int): Double = if (total == 0) Double.NaN else part.toDouble() / total

        private fun pct(value: Double): String = if (value.isNaN()) "N/A" else "%.1f%%".format(value * 100)

        private companion object {
            val SUPPRESSED_BUCKETS = setOf(
                ProactiveBenchDataset.NO_TRIGGER,
                ProactiveBenchDataset.SUPPRESSED_COLD_START,
                ProactiveBenchDataset.SUPPRESSED_COOLDOWN,
                ProactiveBenchDataset.SUPPRESSED_DAILY_BUDGET,
                ProactiveBenchDataset.SUPPRESSED_DISABLED,
            )
        }
    }
}
