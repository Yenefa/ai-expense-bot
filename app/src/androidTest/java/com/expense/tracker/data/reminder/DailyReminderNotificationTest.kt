package com.expense.tracker.data.reminder

import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** 提醒通知冒烟：真实发送通知并断言通知栏出现。 */
@RunWith(AndroidJUnit4::class)
class DailyReminderNotificationTest {

    @Test
    fun notifyShowsInNotificationBar() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context: Context = instrumentation.targetContext
        // Android 13+ 需要通知运行时权限
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            android.Manifest.permission.POST_NOTIFICATIONS,
        )
        DailyReminderWorker.notify(context, "冒烟测试：记账提醒通知")

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val active = manager.activeNotifications
        val found = active.any { it.id == DailyReminderWorker.NOTIFICATION_ID }
        assertThat(found).isTrue()
    }
}
