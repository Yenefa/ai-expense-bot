package com.expense.tracker.proactive

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ProactiveGovernorTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDate.of(2026, 9, 16).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val today = "2026-09-16"

    private class FakeState(private var current: ProactiveState = ProactiveState()) : ProactiveStateStore {
        var records = 0
        override suspend fun state(): ProactiveState = current
        override suspend fun record(typeWire: String, severityWire: String, nowMillis: Long, dayKey: String) {
            records++
            current = current.copy(
                lastAlertAtMillis = current.lastAlertAtMillis + (typeWire to nowMillis),
                lastSeverity = current.lastSeverity + (typeWire to severityWire),
                dayKey = dayKey,
                countToday = if (current.dayKey == dayKey) current.countToday + 1 else 1,
            )
        }
    }

    private fun inputs(spent: Long = 185_000L) = ProactiveInputs(
        nowMillis = now,
        zone = zone,
        monthlyLimitCents = 200_000L,
        monthSpentCents = spent,
        monthRecordCount = 10,
        elapsedMonthDays = 16,
        daysInMonth = 30,
        currentWeekSpentCents = 0L,
        completedWeekSpendsCents = listOf(0L, 0L, 0L, 0L),
    )

    @Test
    fun `每日最多一条`() = runBlocking {
        val store = FakeState(ProactiveState(dayKey = today, countToday = 1))
        val gov = ProactiveGovernor(store, { ProactiveAlertType.entries.toSet() })
        assertThat(gov.evaluate(inputs())).isNull()
    }

    @Test
    fun `同类冷却期内不重复`() = runBlocking {
        val store = FakeState(
            ProactiveState(
                lastAlertAtMillis = mapOf(ProactiveAlertType.BUDGET_THRESHOLD.wire to now - 60_000L),
                lastSeverity = mapOf(ProactiveAlertType.BUDGET_THRESHOLD.wire to ProactiveSeverity.WARN.wire),
                dayKey = "2026-09-15",
                countToday = 1,
            ),
        )
        val gov = ProactiveGovernor(store, { ProactiveAlertType.entries.toSet() })
        assertThat(gov.evaluate(inputs())).isNull()
    }

    @Test
    fun `预算WARN升级OVER可突破冷却`() = runBlocking {
        val store = FakeState(
            ProactiveState(
                lastAlertAtMillis = mapOf(ProactiveAlertType.BUDGET_THRESHOLD.wire to now - 60_000L),
                lastSeverity = mapOf(ProactiveAlertType.BUDGET_THRESHOLD.wire to ProactiveSeverity.WARN.wire),
                dayKey = "2026-09-15",
                countToday = 1,
            ),
        )
        val gov = ProactiveGovernor(store, { ProactiveAlertType.entries.toSet() })
        val alert = gov.evaluate(inputs(spent = 210_000L))
        assertThat(alert?.severity).isEqualTo(ProactiveSeverity.OVER)
    }

    @Test
    fun `可关闭类型与总开关`() = runBlocking {
        val store = FakeState()
        val off = ProactiveGovernor(store, { emptySet() })
        assertThat(off.evaluate(inputs())).isNull()

        val budgetOff = ProactiveGovernor(store, { setOf(ProactiveAlertType.ANOMALOUS_SPENDING) })
        assertThat(budgetOff.evaluate(inputs())).isNull()
    }

    @Test
    fun `LLM只改文案且失败回退`() = runBlocking {
        var calls = 0
        val store = FakeState()
        val gov = ProactiveGovernor(
            store,
            { ProactiveAlertType.entries.toSet() },
            copywriter = { calls++; "本月预算快满了，注意节奏。" },
        )
        val alert = gov.evaluate(inputs())!!
        assertThat(alert.copy).isEqualTo("本月预算快满了，注意节奏。")
        assertThat(calls).isEqualTo(1)

        // 规则不触发时，copywriter 永远不该被调用
        val store2 = FakeState()
        val gov2 = ProactiveGovernor(store2, { ProactiveAlertType.entries.toSet() }, copywriter = { calls++; "x" })
        assertThat(gov2.evaluate(inputs(spent = 1_000L))).isNull()
        assertThat(calls).isEqualTo(1)

        // 超长/失败回退确定性文案
        val store3 = FakeState()
        val gov3 = ProactiveGovernor(store3, { ProactiveAlertType.entries.toSet() }, copywriter = { "长".repeat(200) })
        val fallback = gov3.evaluate(inputs())!!
        assertThat(fallback.copy).isEqualTo(fallback.deterministicCopy)
    }
}
