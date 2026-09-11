package com.expense.tracker.proactive

/** 测试/基准用的内存状态存储。 */
class FakeProactiveStateStore(
    private var current: ProactiveState = ProactiveState(),
) : ProactiveStateStore {

    var records = 0
        private set

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
