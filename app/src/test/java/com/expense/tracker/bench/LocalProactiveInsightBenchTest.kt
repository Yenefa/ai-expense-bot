package com.expense.tracker.bench

import com.expense.tracker.proactive.FakeProactiveStateStore
import com.expense.tracker.proactive.ProactiveAlertType
import com.expense.tracker.proactive.ProactiveGovernor
import com.expense.tracker.proactive.ProactiveInputs
import com.expense.tracker.proactive.ProactiveState
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * ProactiveInsightBench v1（纯本地，零网络）：
 * 规则决策 + 五类硬约束；LLM 文案层被替换为 stub（只记录调用，不参与判断）。
 */
class LocalProactiveInsightBenchTest {

    private val cases = ProactiveBenchDataset.load()
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDate.of(2026, 9, 16).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `数据集完整性`() {
        assertThat(cases).hasSize(38)
        assertThat(cases.map { it.id }.toSet()).hasSize(38)
        val byBucket = cases.groupBy { it.bucket }
        assertThat(byBucket.keys).containsExactlyElementsIn(ProactiveBenchDataset.BUCKETS)
        val wires = ProactiveAlertType.entries.map { it.wire }.toSet()
        cases.forEach { case ->
            assertThat(case.weeksCents).hasSize(4)
            case.enabled.forEach { assertThat(wires).contains(it) }
            case.stateLastAtType?.let { assertThat(wires).contains(it) }
            case.expectType?.let { assertThat(wires).contains(it) }
        }
    }

    @Test
    fun `主动提醒基准`() = runBlocking<Unit> {
        val observations = mutableMapOf<String, ProactiveInsightEvaluator.Observation>()
        val problems = mutableListOf<String>()

        cases.forEach { case ->
            val seededState = ProactiveState(
                lastAlertAtMillis = case.stateLastAtType
                    ?.let { mapOf(it to now - case.stateLastAtOffsetMs) }
                    .orEmpty(),
                lastSeverity = if (case.stateLastAtType != null && case.stateLastSeverity != null) {
                    mapOf(case.stateLastAtType to case.stateLastSeverity)
                } else {
                    emptyMap()
                },
                dayKey = case.stateDayKey,
                countToday = case.stateCountToday,
            )
            val store = FakeProactiveStateStore(seededState)
            val enabled = case.enabled.mapNotNull(ProactiveAlertType::fromWire).toSet()
            var copyCalls = 0
            val governor = ProactiveGovernor(
                stateStore = store,
                enabledProvider = { enabled },
                copywriter = { copyCalls++; null },
            )
            val alert = governor.evaluate(
                ProactiveInputs(
                    nowMillis = now,
                    zone = zone,
                    monthlyLimitCents = case.limitCents,
                    monthSpentCents = case.spentCents,
                    monthRecordCount = case.recordCount,
                    elapsedMonthDays = case.elapsedDays,
                    daysInMonth = case.daysInMonth,
                    currentWeekSpentCents = case.currentWeekCents,
                    completedWeekSpendsCents = case.weeksCents,
                    monthlyIncomeCents = case.income,
                    savingsGoalCents = case.savings,
                ),
            )

            observations[case.id] = ProactiveInsightEvaluator.Observation(
                fired = alert != null,
                type = alert?.type?.wire,
                severity = alert?.severity?.wire,
                copy = alert?.copy,
            )

            if (case.expectType != null && alert?.type?.wire != case.expectType) {
                problems += "${case.id}: 期望类型 ${case.expectType} 实际 ${alert?.type?.wire}"
            }
            if (case.expectSeverity != null && alert?.severity?.wire != case.expectSeverity) {
                problems += "${case.id}: 期望严重度 ${case.expectSeverity} 实际 ${alert?.severity?.wire}"
            }
            // 文案层只许在规则触发后调用，且失败自动回退确定性文案
            if (copyCalls != if (alert != null) 1 else 0) {
                problems += "${case.id}: copywriter 调用 $copyCalls 次与触发状态不一致"
            }
            if (alert != null && alert.copy.isBlank()) {
                problems += "${case.id}: 提醒文案为空"
            }
            // P2：放行的提醒必须落入提醒中心历史（且与最终文案一致）；未放行不得有历史
            val history = store.history()
            when {
                alert == null && history.isNotEmpty() -> problems += "${case.id}: 未触发却写入提醒历史"
                alert != null && history.size != 1 -> problems += "${case.id}: 触发但历史条数为 ${history.size}"
                alert != null && history.single().copy != alert.copy -> problems += "${case.id}: 历史文案与最终文案不一致"
            }
        }

        val report = ProactiveInsightEvaluator.Report(cases, observations)
        val markdown = report.toMarkdown(
            source = "本地规则引擎 + 治理约束（stub 文案层，零网络）",
            metadata = listOf(
                "dataset_sha256 = ${ProactiveBenchDataset.datasetSha256()}",
                "ran_at = ${OffsetDateTime.now()}",
                "硬约束：≥4 可比样本 / 冷启动不提醒 / 每日 1 条 / 同类冷却 / 可关闭",
            ),
        )
        val outFile = File(repoRoot(), "docs/proactive-insight-local-report.md")
        outFile.parentFile?.mkdirs()
        outFile.writeText(markdown)
        println(markdown)

        assertThat(problems).isEmpty()
        assertThat(report.falseAlertRate()).isEqualTo(0.0)
        assertThat(report.missedAlertRate()).isEqualTo(0.0)
        assertThat(report.duplicateAlertRate()).isEqualTo(0.0)
        assertThat(report.coldStartViolationRate()).isEqualTo(0.0)
        assertThat(report.notificationBudgetViolationRate()).isEqualTo(0.0)
        assertThat(report.copyCoverage()).isEqualTo(1.0)
    }

    private fun repoRoot(): File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").exists() }
        ?: File(System.getProperty("user.dir"))
}
