package com.expense.tracker.proactive

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 已持久化的提醒状态：每日额度 + 同类冷却 + 上次严重度（hysteresis 用）。 */
data class ProactiveState(
    val lastAlertAtMillis: Map<String, Long> = emptyMap(),
    val lastSeverity: Map<String, String> = emptyMap(),
    val dayKey: String? = null,
    val countToday: Int = 0,
)

interface ProactiveStateStore {
    suspend fun state(): ProactiveState

    /**
     * 记录一次真正放行的提醒（治理状态 + 提醒中心历史）。
     * [copy] 是最终展示文案（LLM 改写成功则是改写后的），历史记录不得晚于投递。
     */
    suspend fun record(typeWire: String, severityWire: String, copy: String, nowMillis: Long, dayKey: String)

    /** 提醒中心历史，新→旧；默认无历史（测试/最小实现可忽略）。 */
    suspend fun history(): List<ProactiveAlertRecord> = emptyList()

    /** 清空提醒中心历史（不影响治理状态与冷却）。 */
    suspend fun clearHistory() = Unit
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
) {

    suspend fun evaluate(inputs: ProactiveInputs): ProactiveAlert? {
        val enabled = enabledProvider()
        val decision = ProactiveRules.evaluate(inputs, enabled) ?: return null
        // 检查与落库必须原子：前台聊天轮与后台 Worker 各自持有实例，否则可能同时通过
        // "每日 1 条 / 同类冷却"检查后各自 record + 投递（TOCTOU，文案调用还会拉大窗口）。
        return COMMIT_LOCK.withLock { commit(decision, inputs) }
    }

    private suspend fun commit(decision: ProactiveAlert, inputs: ProactiveInputs): ProactiveAlert? {
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

        // 历史（提醒中心）与治理状态同点落库：凡是放行的提醒都可查，投递失败也不会重复触发。
        stateStore.record(decision.type.wire, decision.severity.wire, copy, inputs.nowMillis, today)
        return decision.copy(copy = copy)
    }

    private fun cooldownMillis(type: ProactiveAlertType): Long = when (type) {
        ProactiveAlertType.BUDGET_THRESHOLD -> BUDGET_COOLDOWN_MS
        ProactiveAlertType.ANOMALOUS_SPENDING -> WEEKLY_COOLDOWN_MS
        ProactiveAlertType.SAVINGS_GOAL_DEVIATION -> WEEKLY_COOLDOWN_MS
    }

    private fun dayKey(nowMillis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().toString()

    companion object {
        /** 进程级提交互斥：前台（聊天轮）与后台（Worker）共用同一进程内的治理实例。 */
        private val COMMIT_LOCK = Mutex()

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
