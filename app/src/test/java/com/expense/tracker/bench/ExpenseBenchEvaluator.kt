package com.expense.tracker.bench

import com.expense.tracker.llm.ParsedExpense
import java.time.LocalDate

/**
 * ExpenseBench 评测器（纯函数）：
 * 预测与预期按金额最近邻贪心配对，然后逐字段计分。
 * 指标口径见 docs/expensebench.md。
 */
object ExpenseBenchEvaluator {

    data class FieldMatch(
        val amountOk: Boolean,
        val categoryOk: Boolean,
        val dateOk: Boolean,
        /** 未标注商户时为 null（不计分）。 */
        val noteOk: Boolean?,
        val allOk: Boolean,
    )

    data class BucketStat(
        val bucket: String,
        val cases: Int,
        val expects: Int,
        val amountOk: Int,
        val categoryScored: Int,
        val categoryOk: Int,
        val dateScored: Int,
        val dateOk: Int,
        val noteScored: Int,
        val noteOk: Int,
        val fullOk: Int,
        val countOkCases: Int,
    ) {
        fun amountAccuracy(): Double = if (expects == 0) 0.0 else amountOk.toDouble() / expects
        fun categoryAccuracy(): Double = if (categoryScored == 0) 0.0 else categoryOk.toDouble() / categoryScored
        fun dateAccuracy(): Double = if (dateScored == 0) 0.0 else dateOk.toDouble() / dateScored
        fun noteAccuracy(): Double = if (noteScored == 0) 0.0 else noteOk.toDouble() / noteScored
        fun fullAccuracy(): Double = if (expects == 0) 0.0 else fullOk.toDouble() / expects
        fun countAccuracy(): Double = if (cases == 0) 0.0 else countOkCases.toDouble() / cases
    }

    data class BenchReport(
        val buckets: List<BucketStat>,
        val missingCases: List<String>,
        val extraRecords: Int,
    ) {
        val totalExpects: Int get() = buckets.sumOf { it.expects }
        val totalCases: Int get() = buckets.sumOf { it.cases }

        fun overall(): BucketStat = BucketStat(
            bucket = "overall",
            cases = totalCases,
            expects = totalExpects,
            amountOk = buckets.sumOf { it.amountOk },
            categoryScored = buckets.sumOf { it.categoryScored },
            categoryOk = buckets.sumOf { it.categoryOk },
            dateScored = buckets.sumOf { it.dateScored },
            dateOk = buckets.sumOf { it.dateOk },
            noteScored = buckets.sumOf { it.noteScored },
            noteOk = buckets.sumOf { it.noteOk },
            fullOk = buckets.sumOf { it.fullOk },
            countOkCases = buckets.sumOf { it.countOkCases },
        )

        fun toMarkdown(
            model: String,
            source: String,
            metadata: List<String> = emptyList(),
        ): String = buildString {
            appendLine("# ExpenseBench v1 — 提取精度报告")
            appendLine()
            appendLine("- 数据集：`${ExpenseBenchDataset.BENCH_NOW_ISO}` 为基准时刻，共 $totalCases 条 / $totalExpects 笔预期")
            appendLine("- 被测对象：$model")
            appendLine("- 数据来源：$source")
            metadata.forEach { appendLine("- $it") }
            appendLine()
            appendLine("| 桶 | 条数 | 笔数 | 金额 | 分类 | 日期 | 商户 | 整笔全对 | 笔数全对 |")
            appendLine("| --- | --- | --- | --- | --- | --- | --- | --- | --- |")
            (buckets + overall()).forEach { stat ->
                appendLine(
                    "| ${stat.bucket} | ${stat.cases} | ${stat.expects} " +
                        "| ${pct(stat.amountAccuracy())} | ${pct(stat.categoryAccuracy())} " +
                        "| ${pct(stat.dateAccuracy())} | ${pct(stat.noteAccuracy())} " +
                        "| ${pct(stat.fullAccuracy())} | ${pct(stat.countAccuracy())} |",
                )
            }
            appendLine()
            appendLine("口径：金额=分值精确相等；分类=分类 id 精确相等；日期=本地日历日相等（预期 null 时预测也必须为 null）；")
            appendLine("商户=预测备注包含标注子串（小写、去空白后比较，仅对标注条目计分）；整笔全对=该笔所有已标注字段全对。")
        }.trimEnd() + "\n"

        private fun pct(value: Double): String = "%.1f%%".format(value * 100)
    }

    /** predictions：caseId → LLM 解析出的费用列表（顺序即 LLM 输出顺序）。 */
    fun evaluate(
        cases: List<BenchCase>,
        predictions: Map<String, List<ParsedExpense>>,
        zone: java.time.ZoneId = ExpenseBenchDataset.benchZone,
    ): BenchReport {
        val grouped = cases.groupBy { it.bucket }
        val bucketStats = grouped.keys.sorted().map { bucket -> evalBucket(grouped.getValue(bucket), predictions, zone) }
        val missing = cases.filter { case ->
            predictions[case.id]?.size != case.expect.size
        }.map { it.id }
        val extra = cases.sumOf { case ->
            val preds = predictions[case.id].orEmpty()
            (preds.size - case.expect.size).coerceAtLeast(0)
        }
        return BenchReport(bucketStats, missing, extra)
    }

    private fun evalBucket(
        bucketCases: List<BenchCase>,
        predictions: Map<String, List<ParsedExpense>>,
        zone: java.time.ZoneId,
    ): BucketStat {
        var amountOk = 0
        var categoryScored = 0
        var categoryOk = 0
        var dateScored = 0
        var dateOk = 0
        var noteScored = 0
        var noteOkCount = 0
        var fullOk = 0
        var countOkCases = 0
        var expects = 0

        bucketCases.forEach { case ->
            val expected = case.expect
            val preds = predictions[case.id].orEmpty().toMutableList()
            expects += expected.size
            if (preds.size == expected.size) countOkCases++

            expected.forEach { exp ->
                val bestIndex = preds.indices.minByOrNull { index ->
                    kotlin.math.abs(preds[index].amountCents - exp.amount_cents)
                }
                val matched = bestIndex?.let { preds.removeAt(it) }
                if (matched == null) return@forEach

                val amountOkThis = matched.amountCents == exp.amount_cents
                if (amountOkThis) amountOk++

                val categoryOkThis = matched.categoryId == exp.category
                categoryScored++
                if (categoryOkThis) categoryOk++

                val expectedDate = exp.date?.let(LocalDate::parse)
                val predictedDate = matched.occurredAtMillis
                    ?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
                val dateOkThis = expectedDate == predictedDate
                dateScored++
                if (dateOkThis) dateOk++

                var noteOkThis: Boolean? = null
                if (exp.note_contains != null) {
                    noteScored++
                    noteOkThis = normalize(matched.note).contains(normalize(exp.note_contains))
                    if (noteOkThis) noteOkCount++
                }

                if (amountOkThis && categoryOkThis && dateOkThis && noteOkThis != false) fullOk++
            }
        }

        return BucketStat(
            bucket = bucketCases.firstOrNull()?.bucket ?: "unknown",
            cases = bucketCases.size,
            expects = expects,
            amountOk = amountOk,
            categoryScored = categoryScored,
            categoryOk = categoryOk,
            dateScored = dateScored,
            dateOk = dateOk,
            noteScored = noteScored,
            noteOk = noteOkCount,
            fullOk = fullOk,
            countOkCases = countOkCases,
        )
    }

    private fun normalize(text: String): String =
        text.lowercase().filterNot { it.isWhitespace() }
}
