package com.expense.tracker.bench

import com.expense.tracker.llm.ParsedExpense
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

class ExpenseBenchEvaluatorTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun caseOf(id: String, bucket: String, expects: List<BenchExpect>) =
        BenchCase(id, bucket, "text", expects)

    private fun expense(cents: Long, category: String, date: String?, note: String = "") =
        ParsedExpense(
            amountCents = cents,
            categoryId = category,
            note = note,
            occurredAtMillis = date?.let {
                LocalDate.parse(it).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
            },
        )

    @Test
    fun `完美预测全部满分`() {
        val cases = listOf(
            caseOf("t1", "basic", listOf(BenchExpect(3500, "food", "2026-09-05", "瑞幸"))),
        )
        val predictions = mapOf("t1" to listOf(expense(3500, "food", "2026-09-05", "瑞幸咖啡")))
        val report = ExpenseBenchEvaluator.evaluate(cases, predictions, zone)
        val overall = report.overall()
        assertThat(overall.amountAccuracy()).isEqualTo(1.0)
        assertThat(overall.categoryAccuracy()).isEqualTo(1.0)
        assertThat(overall.dateAccuracy()).isEqualTo(1.0)
        assertThat(overall.noteAccuracy()).isEqualTo(1.0)
        assertThat(overall.fullAccuracy()).isEqualTo(1.0)
        assertThat(overall.countAccuracy()).isEqualTo(1.0)
    }

    @Test
    fun `漏记与多记降低金额准确率且计入笔数失配`() {
        val cases = listOf(
            caseOf("t1", "multi", listOf(BenchExpect(3500, "food", null, null), BenchExpect(1800, "drink", null, null))),
        )
        val predictions = mapOf("t1" to listOf(expense(3500, "food", null)))
        val report = ExpenseBenchEvaluator.evaluate(cases, predictions, zone)
        val overall = report.overall()
        assertThat(overall.amountAccuracy()).isWithin(1e-9).of(0.5)
        assertThat(overall.countAccuracy()).isWithin(1e-9).of(0.0)
        assertThat(report.extraRecords).isEqualTo(0)
    }

    @Test
    fun `多记记录计入extra`() {
        val cases = listOf(
            caseOf("t1", "basic", listOf(BenchExpect(3500, "food", null, null))),
        )
        val predictions = mapOf(
            "t1" to listOf(expense(3500, "food", null), expense(999, "other", null)),
        )
        val report = ExpenseBenchEvaluator.evaluate(cases, predictions, zone)
        assertThat(report.extraRecords).isEqualTo(1)
    }

    @Test
    fun `预期null日期时预测出日期算错`() {
        val cases = listOf(
            caseOf("t1", "basic", listOf(BenchExpect(3500, "food", null, null))),
        )
        val predictions = mapOf("t1" to listOf(expense(3500, "food", "2026-09-05")))
        val report = ExpenseBenchEvaluator.evaluate(cases, predictions, zone)
        assertThat(report.overall().dateAccuracy()).isWithin(1e-9).of(0.0)
    }

    @Test
    fun `未标注商户不计商户分`() {
        val cases = listOf(
            caseOf("t1", "basic", listOf(BenchExpect(3500, "food", null, null))),
        )
        val predictions = mapOf("t1" to listOf(expense(3500, "food", null)))
        val report = ExpenseBenchEvaluator.evaluate(cases, predictions, zone)
        assertThat(report.overall().noteAccuracy()).isEqualTo(0.0)
        assertThat(report.overall().fullAccuracy()).isEqualTo(1.0)
    }
}
