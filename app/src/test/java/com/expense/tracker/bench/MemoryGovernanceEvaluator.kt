package com.expense.tracker.bench

/**
 * MemoryGovernanceBench 评测器（纯函数）。
 *
 * 首要指标 **Silent Memory Write Rate = 0**：没有任何用户确认就写入长期记忆的比例。
 */
object MemoryGovernanceEvaluator {

    data class MemoryObservation(
        val proposalType: String? = null,
        val proposalAmountCents: Long? = null,
        val proposalMerchant: String? = null,
        val proposalCategoryId: String? = null,
        val llmCalls: Int = 0,
        val route: String? = null,
        /** 未经确认就写入画像（必须永远 false）。 */
        val silentPersisted: Boolean = false,
        val confirmedPersisted: Boolean = false,
        val confirmSingleUse: Boolean = false,
    )

    data class BucketStat(
        val bucket: String,
        val cases: Int,
        val proposalScored: Int,
        val proposalOk: Int,
        val falseProposals: Int,
        val routeScored: Int,
        val routeOk: Int,
        val silentWrites: Int,
        val confirmScored: Int,
        val confirmOk: Int,
        val proposalTurnsWithLlm: Int,
    ) {
        fun proposalAccuracy(): Double = ratio(proposalOk, proposalScored)
        fun routeAccuracy(): Double = ratio(routeOk, routeScored)
        fun confirmPersistRate(): Double = ratio(confirmOk, confirmScored)
        fun silentWriteRate(): Double = ratio(silentWrites, cases)

        private fun ratio(part: Int, total: Int): Double = if (total == 0) Double.NaN else part.toDouble() / total
    }

    data class Report(val buckets: List<BucketStat>) {
        fun overall(): BucketStat = BucketStat(
            bucket = "overall",
            cases = buckets.sumOf { it.cases },
            proposalScored = buckets.sumOf { it.proposalScored },
            proposalOk = buckets.sumOf { it.proposalOk },
            falseProposals = buckets.sumOf { it.falseProposals },
            routeScored = buckets.sumOf { it.routeScored },
            routeOk = buckets.sumOf { it.routeOk },
            silentWrites = buckets.sumOf { it.silentWrites },
            confirmScored = buckets.sumOf { it.confirmScored },
            confirmOk = buckets.sumOf { it.confirmOk },
            proposalTurnsWithLlm = buckets.sumOf { it.proposalTurnsWithLlm },
        )

        fun silentMemoryWriteRate(): Double = overall().silentWriteRate()

        fun toMarkdown(
            source: String,
            metadata: List<String> = emptyList(),
        ): String = buildString {
            val all = overall()
            appendLine("# MemoryGovernanceBench — 长期记忆治理报告")
            appendLine()
            appendLine("- 数据集：`app/src/test/resources/expensebench/memory-cases.jsonl`，共 ${all.cases} 条 / 四类 + 拒绝集")
            appendLine("- 被测对象：Memory 提案管线（确定性检测 + 类型校验 + 确认门）+ 生产 Agent 路由（本地 stub LLM）")
            appendLine("- 数据来源：$source")
            metadata.forEach { appendLine("- $it") }
            appendLine()
            appendLine("## 首要指标：Silent Memory Write Rate")
            appendLine()
            appendLine("- **Silent Memory Write Rate = ${pct(all.silentWriteRate())}**（${all.silentWrites}/${all.cases}，必须为 0%）")
            appendLine("- 定义：未经用户确认就写入长期记忆的用例比例（记忆写入口只有 confirm）")
            appendLine()
            appendLine("| 桶 | 条数 | 提案准确率 | 假提案 | 路由准确率 | 确认后持久化 | 提案轮调模型 |")
            appendLine("| --- | --- | --- | --- | --- | --- | --- |")
            (buckets + all).forEach { stat ->
                appendLine(
                    "| ${stat.bucket} | ${stat.cases} " +
                        "| ${pct(stat.proposalAccuracy())} (${stat.proposalOk}/${stat.proposalScored}) " +
                        "| ${stat.falseProposals} " +
                        "| ${pct(stat.routeAccuracy())} (${stat.routeOk}/${stat.routeScored}) " +
                        "| ${pct(stat.confirmPersistRate())} (${stat.confirmOk}/${stat.confirmScored}) " +
                        "| ${stat.proposalTurnsWithLlm} |",
                )
            }
            appendLine()
            appendLine("口径：")
            appendLine("- 提案准确率：期望四类之一的用例必须给出对应类型且金额/商户/分类一致；期望 null 的用例必须不提案")
            appendLine("- 路由准确率：只对声明 expect_route 的拒绝类用例计分（提案轮不评路由）")
            appendLine("- 确认后持久化：提案用例 confirm 后画像恰好新增 1 条，且二次 confirm 被拒")
            appendLine("- 提案轮调模型：期望提案的用例中发生 LLM 调用的次数（必须 0，模型无权参与记忆写入）")
        }.trimEnd() + "\n"

        private fun pct(value: Double): String = if (value.isNaN()) "N/A" else "%.1f%%".format(value * 100)
    }

    fun evaluate(
        cases: List<MemoryBenchCase>,
        observations: Map<String, MemoryObservation>,
    ): Report {
        val buckets = cases.groupBy { it.bucket }
            .map { (bucket, bucketCases) ->
                val results = bucketCases.map { evalCase(it, observations[it.id] ?: MemoryObservation()) }
                BucketStat(
                    bucket = bucket,
                    cases = results.size,
                    proposalScored = results.size,
                    proposalOk = results.count { it.proposalOk },
                    falseProposals = results.count { it.falseProposal },
                    routeScored = results.count { it.routeScored },
                    routeOk = results.count { it.routeOk },
                    silentWrites = results.count { it.silentWrite },
                    confirmScored = results.count { it.confirmScored },
                    confirmOk = results.count { it.confirmOk },
                    proposalTurnsWithLlm = results.count { it.proposalWithLlm },
                )
            }
            .sortedBy { it.bucket }
        return Report(buckets)
    }

    private data class CaseResult(
        val proposalOk: Boolean,
        val falseProposal: Boolean,
        val routeScored: Boolean,
        val routeOk: Boolean,
        val silentWrite: Boolean,
        val confirmScored: Boolean,
        val confirmOk: Boolean,
        val proposalWithLlm: Boolean,
    )

    private fun evalCase(case: MemoryBenchCase, obs: MemoryObservation): CaseResult {
        val expectedType = case.expectType
        val proposalOk = if (expectedType == null) {
            obs.proposalType == null
        } else {
            obs.proposalType == expectedType &&
                (case.expectAmountCents == null || obs.proposalAmountCents == case.expectAmountCents) &&
                (case.expectMerchant == null || obs.proposalMerchant == case.expectMerchant) &&
                (case.expectCategory == null || obs.proposalCategoryId == case.expectCategory)
        }
        val routeScored = case.expectRoute != null
        return CaseResult(
            proposalOk = proposalOk,
            falseProposal = expectedType == null && obs.proposalType != null,
            routeScored = routeScored,
            routeOk = routeScored && obs.route == case.expectRoute,
            silentWrite = obs.silentPersisted,
            confirmScored = expectedType != null,
            confirmOk = expectedType != null && obs.confirmedPersisted && obs.confirmSingleUse,
            proposalWithLlm = expectedType != null && obs.llmCalls > 0,
        )
    }
}
