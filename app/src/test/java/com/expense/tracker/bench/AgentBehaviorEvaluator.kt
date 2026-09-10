package com.expense.tracker.bench

import java.time.Instant
import java.time.ZoneId

/** 观测到的一次最终变更（insert / update 的落地状态）。 */
data class ObservedItem(
    val kind: String,
    val amountCents: Long?,
    val categoryId: String?,
    val note: String = "",
    val occurredAtMillis: Long? = null,
)

/**
 * 单条用例的行为观测（由当地 harness 填充）：
 * - route：最终生效路由（含 Intent Escalation 结果）
 * - tools：实际调用的工具名
 * - appliedCount/appliedItems：已落库的变更
 * - pending/pendingCount：进入删除确认门、尚未落库的拟变更
 * - error：管线报错（若有）
 */
data class BehaviorObservation(
    val route: String? = null,
    val tools: List<String> = emptyList(),
    val appliedCount: Int = 0,
    val appliedItems: List<ObservedItem> = emptyList(),
    val pending: Boolean = false,
    val pendingCount: Int = 0,
    val activeCount: Int? = null,
    val error: String? = null,
) {
    /** 拟变更总数 = 已应用 + 待确认（口径见 docs/expensebench-v2.md）。 */
    val proposedCount: Int get() = appliedCount + pendingCount
}

data class BucketBehaviorStat(
    val bucket: String,
    val cases: Int,
    val routerScored: Int,
    val routerOk: Int,
    val queryExpected: Int,
    val queryHit: Int,
    val toolScored: Int,
    val toolOk: Int,
    val countScored: Int,
    val countOk: Int,
    val positiveExpected: Int,
    val positiveHit: Int,
    val noMutationCases: Int,
    val falseMutationCases: Int,
    val dateScored: Int,
    val dateOk: Int,
    val e2eOk: Int,
) {
    fun routerAccuracy(): Double = ratio(routerOk, routerScored)
    fun queryRecall(): Double = ratio(queryHit, queryExpected)
    fun toolAccuracy(): Double = ratio(toolOk, toolScored)
    fun countAccuracy(): Double = ratio(countOk, countScored)
    fun mutationPrecision(): Double = ratio(positiveHit, positiveHit + falseMutationCases)
    fun falseMutationRate(): Double = ratio(falseMutationCases, noMutationCases)
    fun dateAccuracy(): Double = ratio(dateOk, dateScored)
    fun e2eRate(): Double = ratio(e2eOk, cases)

    private fun ratio(part: Int, total: Int): Double = if (total == 0) Double.NaN else part.toDouble() / total
}

data class BehaviorReport(
    val buckets: List<BucketBehaviorStat>,
    val observations: Map<String, BehaviorObservation>,
) {
    fun overall(): BucketBehaviorStat = BucketBehaviorStat(
        bucket = "overall",
        cases = buckets.sumOf { it.cases },
        routerScored = buckets.sumOf { it.routerScored },
        routerOk = buckets.sumOf { it.routerOk },
        queryExpected = buckets.sumOf { it.queryExpected },
        queryHit = buckets.sumOf { it.queryHit },
        toolScored = buckets.sumOf { it.toolScored },
        toolOk = buckets.sumOf { it.toolOk },
        countScored = buckets.sumOf { it.countScored },
        countOk = buckets.sumOf { it.countOk },
        positiveExpected = buckets.sumOf { it.positiveExpected },
        positiveHit = buckets.sumOf { it.positiveHit },
        noMutationCases = buckets.sumOf { it.noMutationCases },
        falseMutationCases = buckets.sumOf { it.falseMutationCases },
        dateScored = buckets.sumOf { it.dateScored },
        dateOk = buckets.sumOf { it.dateOk },
        e2eOk = buckets.sumOf { it.e2eOk },
    )

    fun falseMutationRate(): Double = overall().falseMutationRate()

    fun toMarkdown(
        model: String,
        source: String,
        metadata: List<String> = emptyList(),
    ): String = buildString {
        val all = overall()
        appendLine("# ExpenseBench v2 — Agent 行为可靠性报告")
        appendLine()
        appendLine("- 数据集：`app/src/test/resources/expensebench/cases-v2.jsonl`，基准时刻 ${ExpenseBenchDataset.BENCH_NOW_ISO}")
        appendLine("- 被测对象：$model")
        appendLine("- 数据来源：$source")
        metadata.forEach { appendLine("- $it") }
        appendLine()
        appendLine("## 首要指标：False Mutation Rate")
        appendLine()
        appendLine("- **False Mutation Rate = ${pct(all.falseMutationRate())}**（${all.falseMutationCases}/${all.noMutationCases}，目标 0%）")
        appendLine("- 定义：期望零变更的用例中，实际产生拟变更（已落库 + 待确认）的比例")
        appendLine()
        appendLine("## 分桶指标")
        appendLine()
        appendLine("| 桶 | 条数 | Router | Query Recall | Mutation Precision | False Mutation | Tool Sel | Mut Count | Date Binding | E2E |")
        appendLine("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
        (buckets + all).forEach { stat ->
            appendLine(
                "| ${stat.bucket} | ${stat.cases} " +
                    "| ${pct(stat.routerAccuracy())} (${stat.routerOk}/${stat.routerScored}) " +
                    "| ${pct(stat.queryRecall())} (${stat.queryHit}/${stat.queryExpected}) " +
                    "| ${pct(stat.mutationPrecision())} " +
                    "| ${pct(stat.falseMutationRate())} (${stat.falseMutationCases}/${stat.noMutationCases}) " +
                    "| ${pct(stat.toolAccuracy())} (${stat.toolOk}/${stat.toolScored}) " +
                    "| ${pct(stat.countAccuracy())} (${stat.countOk}/${stat.countScored}) " +
                    "| ${pct(stat.dateAccuracy())} (${stat.dateOk}/${stat.dateScored}) " +
                    "| ${pct(stat.e2eRate())} (${stat.e2eOk}/${stat.cases}) |",
            )
        }
        appendLine()
        appendLine("口径：")
        appendLine("- Router = 生效路由（含 Escalation）与 expected_route 一致；只对声明了 gold 的用例计分")
        appendLine("- Query Recall = expected_route=QUERY 中实际走到 QUERY 的比例")
        appendLine("- Mutation Precision = 有正例期望且确实产生变更 /（有正例期望且产生变更 + 无期望却产生变更）")
        appendLine("- False Mutation = expected_mutation_count=0 的用例中 proposedCount>0（含待确认）")
        appendLine("- Tool Sel = expected_tools 集合精确匹配；Mut Count = proposedCount 与 gold 相等")
        appendLine("- Date Binding = expect 中带日期的条目，最终变更日期与 gold 相等（漏记按错计）")
        appendLine("- E2E = 该条所有声明的检查（路由/工具/笔数/确认门/活跃账目数/最终状态/无假变更）全部通过")
        appendLine()
        val failed = observations.filterValues { it.error != null }
        appendLine("请求失败/管线报错：${failed.size} 条" + if (failed.isEmpty()) "" else "（" + failed.keys.take(10).joinToString("、") + "）")
    }.trimEnd() + "\n"

    private fun pct(value: Double): String = if (value.isNaN()) "N/A" else "%.1f%%".format(value * 100)
}

/**
 * AgentBehaviorBench 评测器（纯函数）。gold 语义见 docs/expensebench-v2.md。
 */
object AgentBehaviorEvaluator {

    data class CaseResult(
        val routerScored: Boolean,
        val routerOk: Boolean,
        val queryExpected: Boolean,
        val queryHit: Boolean,
        val toolScored: Boolean,
        val toolOk: Boolean,
        val countScored: Boolean,
        val countOk: Boolean,
        val positiveExpected: Boolean,
        val positiveHit: Boolean,
        val noMutation: Boolean,
        val falseMutation: Boolean,
        val dateScored: Int,
        val dateOk: Int,
        val e2eOk: Boolean,
    )

    fun evaluate(
        cases: List<BehaviorCase>,
        observations: Map<String, BehaviorObservation>,
        zone: ZoneId = ExpenseBenchDataset.benchZone,
    ): BehaviorReport {
        val perBucket = cases.groupBy { it.bucket }
            .map { (bucket, bucketCases) ->
                val results = bucketCases.map { evalCase(it, observations[it.id] ?: BehaviorObservation(), zone) }
                BucketBehaviorStat(
                    bucket = bucket,
                    cases = results.size,
                    routerScored = results.count { it.routerScored },
                    routerOk = results.count { it.routerOk },
                    queryExpected = results.count { it.queryExpected },
                    queryHit = results.count { it.queryHit },
                    toolScored = results.count { it.toolScored },
                    toolOk = results.count { it.toolOk },
                    countScored = results.count { it.countScored },
                    countOk = results.count { it.countOk },
                    positiveExpected = results.count { it.positiveExpected },
                    positiveHit = results.count { it.positiveHit },
                    noMutationCases = results.count { it.noMutation },
                    falseMutationCases = results.count { it.falseMutation },
                    dateScored = results.sumOf { it.dateScored },
                    dateOk = results.sumOf { it.dateOk },
                    e2eOk = results.count { it.e2eOk },
                )
            }
            .sortedBy { it.bucket }
        return BehaviorReport(perBucket, observations)
    }

    private fun evalCase(case: BehaviorCase, obs: BehaviorObservation, zone: ZoneId): CaseResult {
        val expectedRoute = case.expected_route
        val routerScored = expectedRoute != null
        val routerOk = routerScored && obs.route == expectedRoute
        val queryExpected = expectedRoute == "QUERY"
        val queryHit = queryExpected && obs.route == "QUERY"
        val toolScored = case.expected_tools != null
        val toolOk = toolScored && obs.tools.toSet() == case.expected_tools!!.toSet()
        val countScored = case.expected_mutation_count != null
        val countOk = countScored && obs.proposedCount == case.expected_mutation_count
        val noMutation = case.expected_mutation_count == 0
        val falseMutation = noMutation && obs.proposedCount > 0
        val positiveExpected = (case.expected_mutation_count ?: 0) > 0
        val positiveHit = positiveExpected && obs.proposedCount > 0

        val matched = matchItems(case.expect, obs.appliedItems, zone)
        val matchedByExpect = matched.toMap()
        var dateScored = 0
        var dateOk = 0
        case.expect.forEach { exp ->
            if (exp.date != null) {
                dateScored++
                val item = matchedByExpect[exp]
                if (item != null && localDate(item, zone) == exp.date) dateOk++
            }
        }

        val expectOk = if (case.expect.isEmpty()) {
            obs.appliedItems.isEmpty()
        } else {
            obs.appliedItems.size == case.expect.size &&
                matched.size == case.expect.size &&
                matched.all { (exp, item) ->
                    item.amountCents == exp.amount_cents &&
                        item.categoryId == exp.category &&
                        (exp.date == null || localDate(item, zone) == exp.date) &&
                        exp.note_contains?.let { normalize(item.note).contains(normalize(it)) } != false
                }
        }
        val pendingOk = case.expected_pending == obs.pending
        val activeOk = case.expected_active_count == null || obs.activeCount == case.expected_active_count
        val e2eOk = (!routerScored || routerOk) && (!toolScored || toolOk) &&
            (!countScored || countOk) && pendingOk && activeOk && expectOk && !falseMutation

        return CaseResult(
            routerScored = routerScored,
            routerOk = routerOk,
            queryExpected = queryExpected,
            queryHit = queryHit,
            toolScored = toolScored,
            toolOk = toolOk,
            countScored = countScored,
            countOk = countOk,
            positiveExpected = positiveExpected,
            positiveHit = positiveHit,
            noMutation = noMutation,
            falseMutation = falseMutation,
            dateScored = dateScored,
            dateOk = dateOk,
            e2eOk = e2eOk,
        )
    }

    private fun matchItems(
        expect: List<BenchExpect>,
        observed: List<ObservedItem>,
        zone: ZoneId,
    ): Map<BenchExpect, ObservedItem> {
        val pool = observed.toMutableList()
        val matched = LinkedHashMap<BenchExpect, ObservedItem>()
        expect.forEach { exp ->
            val best = pool.indices.minByOrNull { index ->
                kotlin.math.abs((pool[index].amountCents ?: Long.MAX_VALUE).toDouble() - exp.amount_cents.toDouble())
            } ?: return@forEach
            matched[exp] = pool.removeAt(best)
        }
        return matched
    }

    private fun localDate(item: ObservedItem, zone: ZoneId): String? =
        item.occurredAtMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toString() }

    private fun normalize(text: String): String = text.lowercase().filterNot { it.isWhitespace() }
}
