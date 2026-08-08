package com.expense.tracker.data.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.expense.tracker.data.model.Money
import com.expense.tracker.data.repo.ExpenseRepository
import com.expense.tracker.data.db.AppDatabase
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** 每日记账提醒 Worker：发送通知并续排次日。 */
class DailyReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : Worker(appContext, params) {

    override fun doWork(): Result {
        val context = applicationContext
        // 顺带跑周期账单到期检查（每日一次自然触发点）
        runCatching {
            kotlinx.coroutines.runBlocking {
                val db = AppDatabase.get(context)
                com.expense.tracker.data.recurring.RecurringGenerator.runOnce(
                    db.recurringRuleDao(),
                    ExpenseRepository(db.expenseDao()),
                )
            }
        }
        val prefs = ReminderPrefs.fromContext(context)
        val snapshot = runCatching { kotlinx.coroutines.runBlocking { prefs.snapshot.first() } }
            .getOrDefault(ReminderSnapshot())
        if (!snapshot.enabled) return Result.success()

        val message = buildMessage(context, snapshot)
        notify(context, message)
        ReminderScheduler.schedule(context, snapshot.hour, snapshot.minute)
        return Result.success()
    }

    private fun buildMessage(context: Context, snapshot: ReminderSnapshot): String {
        val db = AppDatabase.get(context)
        val repo = ExpenseRepository(db.expenseDao())
        val startOfDay = LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val today = kotlinx.coroutines.runBlocking {
            repo.observeInRange(startOfDay, Long.MAX_VALUE).first()
                .filter { it.deletedAt == null }
        }
        if (today.isEmpty()) return "今天还没有记账，花一分钟补记今天的支出吧"
        val total = today.sumOf { it.amountCents }
        return "今天已记 ${today.size} 笔，共 ¥${Money.formatYuan(total)}；有遗漏记得补上"
    }

    companion object {
        const val CHANNEL_ID = "daily_reminder"
        const val NOTIFICATION_ID = 3001

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "记账提醒",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "每日补记提醒"
                },
            )
        }

        fun notify(context: Context, message: String) {
            ensureChannel(context)
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val intent = Intent(context, com.expense.tracker.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Y.E cost · 记账提醒")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}
