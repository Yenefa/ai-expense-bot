package com.expense.tracker.proactive

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * 主动提醒每日检查调度（PeriodicWorkRequest，周期 1 天）：
 * - 首次运行延迟到下一个 20:00（此时当日消费基本定型，仍留出补救时间）；
 * - `KEEP` 保证每次 App 启动重复调用不会重置周期；
 * - WorkManager 持久化任务，重启后自动恢复；治理层的"每日 1 条 / 冷却"负责实际节流。
 */
object ProactiveScheduler {

    const val WORK_NAME = "proactive-insight-daily"
    const val DEFAULT_HOUR = 20
    const val DEFAULT_MINUTE = 0

    fun ensureScheduled(context: Context, hour: Int = DEFAULT_HOUR, minute: Int = DEFAULT_MINUTE) {
        val now = LocalDateTime.now(ZoneId.systemDefault())
        var target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        val delayMillis = Duration.between(now, target).toMillis()

        val request = PeriodicWorkRequestBuilder<ProactiveInsightWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
