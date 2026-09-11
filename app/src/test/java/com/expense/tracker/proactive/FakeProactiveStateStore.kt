package com.expense.tracker.proactive

/** 测试/基准用的内存状态存储（含提醒中心历史）。 */
class FakeProactiveStateStore(
    private var current: ProactiveState = ProactiveState(),
) : ProactiveStateStore {

    var records = 0
        private set

    private val entries = mutableListOf<ProactiveAlertRecord>()

    override suspend fun state(): ProactiveState = current

    override suspend fun record(
        typeWire: String,
        severityWire: String,
        copy: String,
        nowMillis: Long,
        dayKey: String,
    ) {
        records++
        current = current.copy(
            lastAlertAtMillis = current.lastAlertAtMillis + (typeWire to nowMillis),
            lastSeverity = current.lastSeverity + (typeWire to severityWire),
            dayKey = dayKey,
            countToday = if (current.dayKey == dayKey) current.countToday + 1 else 1,
        )
        ProactiveAlertType.fromWire(typeWire)?.let { type ->
            ProactiveSeverity.fromWire(severityWire)?.let { severity ->
                entries += ProactiveAlertRecord(type, severity, copy, nowMillis)
            }
        }
    }

    override suspend fun history(): List<ProactiveAlertRecord> = entries.reversed()

    override suspend fun clearHistory() {
        entries.clear()
    }
}
