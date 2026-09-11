package com.expense.tracker.bench

/**
 * MemoryConsumptionBench 评测器（纯函数）。
 *
 * 三个核心指标：
 * - **Unauthorized Memory Read Rate = 0**：本轮路由未授权的记忆类型绝不出现在 prompt
 * - Correct Memory Application：授权类型出现在 prompt；别名用例最终账目分类正确
 * - **Deleted Memory Reuse Rate = 0**：被用户删除的记忆不得再出现在任何路径
 */
object MemoryConsumptionEvaluator {

    data class Observation(
        val prompts: List<String> = emptyList(),
        val route: String? = null,
        val finalCategory: String? = null,
    )

    data class BucketStat(
        val bucket: String,
        val cases: Int,
        val memoryCases: Int,
        val unauthorizedReads: Int,
        val applicationScored: Int,
        val applicationOk: Int,
        val deletedCases: Int,
        val deletedReuse: Int,
        val routeScored: Int,
        val routeOk: Int,
    ) {
        fun unauthorizedReadRate(): Double = ratio(unauthorizedReads, cases)
        fun applicationRate(): Double = ratio(applicationOk, applicationScored)
        fun deletedReuseRate(): Double = ratio(deletedReuse, deletedCases)
        fun routeAccuracy(): Double = ratio(routeOk, routeScored)

        private fun ratio(part: Int, total: Int): Double = if (total == 0) Double.NaN else part.toDouble() / total
    }

    data class Report(val buckets: List<BucketStat>) {
        fun overall(): BucketStat = BucketStat(
            bucket = "overall",
            cases = buckets.sumOf { it.cases },
            memoryCases = buckets.sumOf { it.memoryCases },
            unauthorizedReads = buckets.sumOf { it.unauthorizedReads },
            applicationScored = buckets.sumOf { it.applicationScored },
            applicationOk = buckets.sumOf { it.applicationOk },
            deletedCases = buckets.sumOf { it.deletedCases },
            deletedReuse = buckets.sumOf { it.deletedReuse },
            routeScored = buckets.sumOf { it.routeScored },
            routeOk = buckets.sumOf { it.routeOk },
        )

        fun toMarkdown(source: String, metadata: List<String> = emptyList()): String = buildString {
            val all = overall()
            appendLine("# MemoryConsumptionBench — 长期记忆消费报告")
            appendLine()
            appendLine("- 数据集：`app/src/test/resources/expensebench/memory-consumption-cases.jsonl`，共 ${all.cases} 条")
            appendLine("- 被测对象：真实 Agent + MemoryReadScope 授权注入 + 商户别名确定性应用（stub LLM，零网络）")
            appendLine("- 数据来源：$source")
            metadata.forEach { appendLine("- $it") }
            appendLine()
            appendLine("## 核心指标")
            appendLine()
            appendLine("- **Unauthorized Memory Read Rate = ${pct(all.unauthorizedReadRate())}**（${all.unauthorizedReads}/${all.cases}，必须 0%）")
            appendLine("- **Deleted Memory Reuse Rate = ${pct(all.deletedReuseRate())}**（${all.deletedReuse}/${all.deletedCases}，必须 0%）")
            appendLine("- Correct Memory Application = ${pct(all.applicationRate())}（${all.applicationOk}/${all.applicationScored}）")
            appendLine()
            appendLine("| 桶 | 条数 | 越权读取 | 正确应用 | 删除后复用 | 路由 |")
            appendLine("| --- | --- | --- | --- | --- | --- |")
            (buckets + all).forEach { stat ->
                appendLine(
                    "| ${stat.bucket} | ${stat.cases} " +
                        "| ${stat.unauthorizedReads}/${stat.cases} " +
                        "| ${pct(stat.applicationRate())} (${stat.applicationOk}/${stat.applicationScored}) " +
                        "| ${stat.deletedReuse}/${stat.deletedCases} " +
                        "| ${pct(stat.routeAccuracy())} (${stat.routeOk}/${stat.routeScored}) |",
                )
            }
            appendLine()
            appendLine("口径：")
            appendLine("- 授权范围：MUTATION→商户别名；QUERY→月收入/储蓄目标/常用分类；CHAT→无；未授权类型的标记不得出现在 prompt")
            appendLine("- 正确应用：预期读取类型的标记出现；别名用例最终账目分类等于 gold")
            appendLine("- 删除后复用：被删除类型的标记禁止出现；被删除别名不得再覆盖分类")
        }.trimEnd() + "\n"

        private fun pct(value: Double): String = if (value.isNaN()) "N/A" else "%.1f%%".format(value * 100)
    }

    fun evaluate(
        cases: List<MemoryConsumptionCase>,
        observations: Map<String, Observation>,
    ): Report {
        val buckets = cases.groupBy { it.bucket }
            .map { (bucket, bucketCases) ->
                val results = bucketCases.map { evalCase(it, observations[it.id] ?: Observation()) }
                BucketStat(
                    bucket = bucket,
                    cases = results.size,
                    memoryCases = results.count { it.hasMemory },
                    unauthorizedReads = results.count { it.unauthorizedRead },
                    applicationScored = results.count { it.applicationScored },
                    applicationOk = results.count { it.applicationOk },
                    deletedCases = results.count { it.deletedCase },
                    deletedReuse = results.count { it.deletedReuse },
                    routeScored = results.count { it.routeScored },
                    routeOk = results.count { it.routeOk },
                )
            }
            .sortedBy { it.bucket }
        return Report(buckets)
    }

    private data class CaseResult(
        val hasMemory: Boolean,
        val unauthorizedRead: Boolean,
        val applicationScored: Boolean,
        val applicationOk: Boolean,
        val deletedCase: Boolean,
        val deletedReuse: Boolean,
        val routeScored: Boolean,
        val routeOk: Boolean,
    )

    private fun evalCase(case: MemoryConsumptionCase, obs: Observation): CaseResult {
        val prompt = obs.prompts.joinToString("\n")
        val deletedTypes = case.deleteTypes.mapNotNull(MemoryTypeWire::from).toSet()
        val effective = case.memory.filter { MemoryTypeWire.from(it.type) !in deletedTypes }
        val allowed = allowedTypes(case.expectRoute) + case.expectReads.mapNotNull(MemoryTypeWire::from)

        val unauthorized = if (effective.isEmpty()) {
            // 无任何有效记忆时，prompt 里不该出现授权块
            prompt.contains("已授权记忆")
        } else {
            effective
                .filter { MemoryTypeWire.from(it.type) !in allowed }
                .any { prompt.contains(markerOf(it)) }
        }
        val expectedPresent = case.expectReads.mapNotNull(MemoryTypeWire::from).all { type ->
            effective.any { MemoryTypeWire.from(it.type) == type && prompt.contains(markerOf(it)) }
        }
        val applicationScored = case.expectReads.isNotEmpty() || case.expectFinalCategory != null
        val applicationOk = applicationScored && expectedPresent &&
            (case.expectFinalCategory == null || obs.finalCategory == case.expectFinalCategory)

        val deletedMarkers = case.memory.filter { MemoryTypeWire.from(it.type) in deletedTypes }
        val deletedReuse = deletedMarkers.any { deleted ->
            if (prompt.contains(markerOf(deleted))) {
                true
            } else {
                // 被删除的别名不得再覆盖分类
                MemoryTypeWire.from(deleted.type) == MemoryTypeWire.MERCHANT_ALIAS &&
                    case.expectFinalCategory != null &&
                    obs.finalCategory == deleted.categoryId
            }
        }

        return CaseResult(
            hasMemory = case.memory.isNotEmpty(),
            unauthorizedRead = unauthorized,
            applicationScored = applicationScored,
            applicationOk = applicationOk,
            deletedCase = case.deleteTypes.isNotEmpty(),
            deletedReuse = deletedReuse,
            routeScored = case.expectRoute != null,
            routeOk = case.expectRoute == null || obs.route == case.expectRoute,
        )
    }

    private enum class MemoryTypeWire(val wire: String) {
        MONTHLY_INCOME("monthly_income"),
        SAVINGS_GOAL("savings_goal"),
        MERCHANT_ALIAS("merchant_alias"),
        CATEGORY_PREFERENCE("category_preference");

        companion object {
            fun from(value: String): MemoryTypeWire? = entries.firstOrNull { it.wire == value }
        }
    }

    private fun allowedTypes(route: String?): Set<MemoryTypeWire> = when (route) {
        "MUTATION" -> setOf(MemoryTypeWire.MERCHANT_ALIAS)
        "QUERY" -> setOf(
            MemoryTypeWire.MONTHLY_INCOME,
            MemoryTypeWire.SAVINGS_GOAL,
            MemoryTypeWire.CATEGORY_PREFERENCE,
        )
        else -> emptySet()
    }

    private fun markerOf(fact: ConsumptionSeedFact): String = when (MemoryTypeWire.from(fact.type)) {
        MemoryTypeWire.MONTHLY_INCOME -> "月收入"
        MemoryTypeWire.SAVINGS_GOAL -> "储蓄目标"
        MemoryTypeWire.CATEGORY_PREFERENCE -> "常用分类"
        MemoryTypeWire.MERCHANT_ALIAS -> fact.merchant ?: "商户别名"
        null -> fact.rawText
    }
}
