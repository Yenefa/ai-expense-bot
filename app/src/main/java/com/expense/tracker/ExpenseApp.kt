package com.expense.tracker

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ExpenseApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 周期账单到期检查：启动即运行，不阻塞 UI
        appScope.launch {
            runCatching { container.recurringRunner() }
        }
        // 主动提醒：启动检查一次（治理器每日额度/冷却保证不重复）
        appScope.launch {
            runCatching { container.proactiveStartupCheck() }
        }
        // 主动提醒：每日 20:00 后台检查（周期任务持久化，重启后自动恢复；KEEP 不重置周期）
        appScope.launch {
            runCatching { com.expense.tracker.proactive.ProactiveScheduler.ensureScheduled(this@ExpenseApp) }
        }
    }
}
