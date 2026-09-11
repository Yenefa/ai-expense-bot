package com.expense.tracker.proactive

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

/**
 * 主动提醒的系统通知投递（通知渠道独立于每日记账提醒）。
 * 治理器已放行并记录历史后才会调用；权限缺失时静默跳过（聊天内 🔔 与提醒中心仍可见）。
 */
object ProactiveNotifier {

    const val CHANNEL_ID = "proactive_insight"
    const val NOTIFICATION_ID = 3002

    /** 通知点击后直接打开提醒中心。 */
    const val EXTRA_OPEN_CENTER = "open_proactive_center"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "主动提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "预算临界 / 异常消费 / 储蓄目标偏离（规则触发后投递）"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            },
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** 投递一条提醒；返回是否真正进入通知栏。 */
    fun notify(context: Context, alert: ProactiveAlert): Boolean {
        ensureChannel(context)
        // 显式内联权限检查（lint MissingPermission 需要同方法内的 checkSelfPermission 守卫）
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val intent = Intent(context, com.expense.tracker.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_CENTER, true)
        }
        val pending = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // 隐私：正文含具体金额，锁屏只显示泛化文案（VISIBILITY_PRIVATE + publicVersion）。
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Y.E cost · 主动提醒")
            .setContentText("有一条新的消费提醒，解锁后查看")
            .setContentIntent(pending)
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Y.E cost · ${alert.type.typeLabel}")
            .setContentText(alert.copy)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.copy))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        return true
    }
}
