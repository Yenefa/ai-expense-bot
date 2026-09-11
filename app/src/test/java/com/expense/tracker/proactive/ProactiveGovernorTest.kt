package com.expense.tracker.proactive

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ProactiveGovernorTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDate.of(2026, 9, 16).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val today = "2026-09-16"

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
        val store = FakeProactiveStateStore(ProactiveState(dayKey = today, countToday = 1))
        val gov = ProactiveGovernor(store, { ProactiveAlertType.entries.toSet() })
        assertThat(gov.evaluate(inputs())).isNull()
    }

    @Test
    fun `同类冷却期内不重复`() = runBlocking {
        val store = FakeProactiveStateStore(
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
        val store = FakeProactiveStateStore(
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
        val store = FakeProactiveStateStore()
        val off = ProactiveGovernor(store, { emptySet() })
        assertThat(off.evaluate(inputs())).isNull()

        val budgetOff = ProactiveGovernor(store, { setOf(ProactiveAlertType.ANOMALOUS_SPENDING) })
        assertThat(budgetOff.evaluate(inputs())).isNull()
    }

    @Test
    fun `LLM只改文案且失败回退`() = runBlocking {
        var calls = 0
        val store = FakeProactiveStateStore()
        val gov = ProactiveGovernor(
            store,
            { ProactiveAlertType.entries.toSet() },
            copywriter = { calls++; "本月预算快满了，注意节奏。" },
        )
        val alert = gov.evaluate(inputs())!!
        assertThat(alert.copy).isEqualTo("本月预算快满了，注意节奏。")
        assertThat(calls).isEqualTo(1)

        // 规则不触发时，copywriter 永远不该被调用
        val store2 = FakeProactiveStateStore()
        val gov2 = ProactiveGovernor(store2, { ProactiveAlertType.entries.toSet() }, copywriter = { calls++; "x" })
        assertThat(gov2.evaluate(inputs(spent = 1_000L))).isNull()
        assertThat(calls).isEqualTo(1)

        // 超长/失败回退确定性文案
        val store3 = FakeProactiveStateStore()
        val gov3 = ProactiveGovernor(store3, { ProactiveAlertType.entries.toSet() }, copywriter = { "长".repeat(200) })
        val fallback = gov3.evaluate(inputs())!!
        assertThat(fallback.copy).isEqualTo(fallback.deterministicCopy)
    }

    @Test
    fun `放行的提醒落提醒中心历史抑制的不落`() = runBlocking {
        val store = FakeProactiveStateStore()
        val gov = ProactiveGovernor(store, { ProactiveAlertType.entries.toSet() }, copywriter = { "换个说法" })
        val alert = gov.evaluate(inputs())!!

        val history = store.history()
        assertThat(history).hasSize(1)
        assertThat(history.single().type).isEqualTo(alert.type)
        assertThat(history.single().copy).isEqualTo("换个说法")

        // 每日额度已用尽 → 不产出、不新增历史（历史与治理状态同点记录）
        assertThat(gov.evaluate(inputs(spent = 250_000L))).isNull()
        assertThat(store.history()).hasSize(1)
    }

    @Test
    fun `前后台并发时只有一个提交`() = runBlocking {
        val store = FakeProactiveStateStore()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        // 前台：copywriter（LLM 文案）期间挂起，制造"检查后、落库前"的最宽窗口
        val foreground = ProactiveGovernor(
            store,
            { ProactiveAlertType.entries.toSet() },
            copywriter = {
                entered.complete(Unit)
                release.await()
                "并发文案"
            },
        )
        val background = ProactiveGovernor(store, { ProactiveAlertType.entries.toSet() })

        val first = async { foreground.evaluate(inputs()) }
        entered.await()
        val second = async { background.evaluate(inputs()) }
        delay(50) // 让第二个评估完成"检查"（修复前会在此后抢先落库）
        release.complete(Unit)

        val results = listOf(first.await(), second.await())
        assertThat(results.count { it != null }).isEqualTo(1)
        assertThat(store.history()).hasSize(1)
    }
}
