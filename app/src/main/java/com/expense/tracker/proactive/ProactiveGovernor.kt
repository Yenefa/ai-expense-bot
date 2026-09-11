package com.expense.tracker.proactive

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields

/** 已持久化的提醒状态：每日额度 + 同类冷却 + 上次严重度（hysteresis 用）。 */
data class ProactiveState(
    val lastAlertAtMillis: Map<String, Long> = emptyMap(),
    val lastSeverity: Map<String, String> = emptyMap(),
    val dayKey: String? = null,
    val countToday: Int = 0,
)

interface ProactiveStateStore {
    suspend fun state(): ProactiveState
    suspend fun record(typeWire: String, severityWire: String, nowMillis: Long, dayKey: String)
}

/** LLM 只允许改文案；返回 null/超长/失败都回退确定性文案。 */
typealias ProactiveCopywriter = suspend (ProactiveAlert) -> String?

/**
 * 主动提醒治理器（硬约束在这里，不在 LLM）：
 * - 每日最多 1 条
 * - 同类冷却：预算 24h、异常/储蓄 7d；预算 WARN→OVER 可立即升级（hysteresis）
 * - 类型可关闭
 * 规则不触发时 copywriter 永远不会被调用。
 */
class ProactiveGovernor(
    private val stateStore: ProactiveStateStore,
    private val enabledProvider: suspend () -> Set<ProactiveAlertType>,
    private val copywriter: ProactiveCopywriter? = null,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    suspend fun evaluate(inputs: ProactiveInputs): ProactiveAlert? {
        val enabled = enabledProvider()
        val decision = ProactiveRules.evaluate(inputs, enabled) ?: return null

        val state = stateStore.state()
        val today = dayKey(inputs.nowMillis, inputs.zone)
        if (state.dayKey == today && state.countToday >= MAX_ALERTS_PER_DAY) return null

        val lastAt = state.lastAlertAtMillis[decision.type.wire] ?: 0L
        val cooldown = cooldownMillis(decision.type)
        val escalated = decision.type == ProactiveAlertType.BUDGET_THRESHOLD &&
            decision.severity == ProactiveSeverity.OVER &&
            state.lastSeverity[decision.type.wire] != ProactiveSeverity.OVER.wire
        if (lastAt > 0L && inputs.nowMillis - lastAt < cooldown && !escalated) return null

        val copy = runCatching { copywriter?.invoke(decision) }.getOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() && it.length <= MAX_COPY_CHARS && '\n' !in it }
            ?: decision.deterministicCopy

        stateStore.record(decision.type.wire, decision.severity.wire, inputs.nowMillis, today)
        return decision.copy(copy = copy)
    }

    private fun cooldownMillis(type: ProactiveAlertType): Long = when (type) {
        ProactiveAlertType.BUDGET_THRESHOLD -> BUDGET_COOLDOWN_MS
        ProactiveAlertType.ANOMALOUS_SPENDING -> WEEKLY_COOLDOWN_MS
        ProactiveAlertType.SAVINGS_GOAL_DEVIATION -> WEEKLY_COOLDOWN_MS
    }

    private fun dayKey(nowMillis: Long, zone: ZoneId): String =
        LocalDate.ofInstant(Instant.ofEpochMilli(nowMillis), zone).toString()

    companion object {
        const val MAX_ALERTS_PER_DAY = 1
        const val MAX_COPY_CHARS = 120
        const val BUDGET_COOLDOWN_MS = 24 * 60 * 60_000L
        const val WEEKLY_COOLDOWN_MS = 7 * 24 * 60 * 60_000L

        /** 供输入构建器复用的 ISO 周键。 */
        fun isoWeekKey(date: LocalDate): String {
            val fields = WeekFields.ISO
            return "${date.get(fields.weekBasedYear())}-W${date.get(fields.weekOfWeekBasedYear())}"
        }
    }
}
