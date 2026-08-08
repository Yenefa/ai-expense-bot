package com.expense.tracker.data.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** 每日提醒调度：计算到下次提醒时刻的延迟，安排一次性任务并在执行后自续。 */
object ReminderScheduler {

    const val WORK_NAME = "daily-reminder"

    fun schedule(context: Context, hour: Int, minute: Int) {
        val now = LocalDateTime.now(ZoneId.systemDefault())
        var target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }
        val delayMillis = java.time.Duration.between(now, target).toMillis()

        val request = OneTimeWorkRequestBuilder<DailyReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun isDueTodayForTest(hour: Int, minute: Int, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault())
        val target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        return !target.isAfter(now) && now.minusMinutes(5).isBefore(target)
    }

    /** 下一自然月 1 日 00:00（预算/周期账单共用语义的测试辅助）。 */
    fun nextMonthStartMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        val today = LocalDate.now(ZoneId.systemDefault())
        return today.plusMonths(1).withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
    }
}
