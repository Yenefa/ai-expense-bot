package com.expense.tracker.bench

import com.expense.tracker.data.model.Category
import com.expense.tracker.llm.ExpenseTextInterpreter
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.LocalDate
import org.junit.Test

/**
 * 离线管道评测（无 API Key 也能跑）：
 * 用 ExpenseBench 数据集测端侧确定性层（ExpenseTextInterpreter + ChineseDateResolver）
 * 能锁住多少金额笔数与日期，作为 LLM 评测之外的第二个质量基线。
 */
class LocalPipelineBenchTest {

    private val cases = ExpenseBenchDataset.load()

    @Test
    fun `数据集完整性`() {
        assertThat(cases).hasSize(120)
        assertThat(cases.map { it.id }.toSet()).hasSize(120)
        cases.forEach { case ->
            assertThat(case.expect).isNotEmpty()
            case.expect.forEach { exp ->
                assertThat(Category.byId(exp.category)).isNotNull()
                exp.date?.let { assertThat(LocalDate.parse(it)).isNotNull() }
            }
        }
    }

    @Test
    fun `端侧日期提示精度`() {
        val zone = ExpenseBenchDataset.benchZone
        val datedCases = cases.filter { case -> case.expect.all { it.date != null } }

        var expectedEntries = 0
        var hintedEntries = 0
        var datePaired = 0
        var dateCorrect = 0
        var amountCorrect = 0
        val misses = mutableListOf<String>()

        datedCases.forEach { case ->
            val interpretation = ExpenseTextInterpreter.interpret(case.text, ExpenseBenchDataset.benchNowMillis, zone)
            val hints = interpretation.expenseHints
            expectedEntries += case.expect.size
            hintedEntries += hints.size
            if (hints.size == case.expect.size) {
                case.expect.forEachIndexed { index, exp ->
                    val hint = hints[index]
                    datePaired++
                    if (hint.date.toString() == exp.date) dateCorrect++ else misses += "${case.id}: hint=${hint.date} expect=${exp.date}"
                    if (hint.amountCents == exp.amount_cents) amountCorrect++
                }
            } else {
                misses += "${case.id}: hints=${hints.size} expect=${case.expect.size}"
            }
        }

        val report = buildString {
            appendLine("# ExpenseBench v1 — 端侧确定性管道报告（离线）")
            appendLine()
            appendLine("- 数据集基准时刻：${ExpenseBenchDataset.BENCH_NOW_ISO}")
            appendLine("- 带日期用例：${datedCases.size} 条，预期笔数 $expectedEntries")
            appendLine("- 日期提示覆盖率（完整用例口径）：$hintedEntries / $expectedEntries = ${percent(hintedEntries, expectedEntries)}")
            appendLine("  - 覆盖率低是设计使然：端侧提示只锁定「客户端可证明的事实」（金额带元/块且日期词明确），其余交给 LLM")
            appendLine("- 日期提示准确率（配对成功时）：$dateCorrect / $datePaired = ${percent(dateCorrect, datePaired)}")
            appendLine("- 金额提示准确率（配对成功时）：$amountCorrect / $datePaired = ${percent(amountCorrect, datePaired)}")
            appendLine()
            appendLine("说明：端侧提示锁日期与金额笔数，分类与商户由 LLM 决策；两者互补，口径见 docs/expensebench.md。")
            if (misses.isNotEmpty()) {
                appendLine()
                appendLine("未配对/未命中样例（前 20 条）：")
                misses.take(20).forEach { appendLine("- $it") }
            }
        }

        val outFile = File(repoRoot(), "docs/expensebench-local-report.md")
        outFile.parentFile?.mkdirs()
        outFile.writeText(report)
        println(report)

        assertThat(cases).isNotEmpty()
    }

    private fun percent(part: Int, total: Int): String =
        if (total == 0) "N/A" else "%.1f%%".format(part * 100.0 / total)

    private fun repoRoot(): File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").exists() }
        ?: File(System.getProperty("user.dir"))
}
