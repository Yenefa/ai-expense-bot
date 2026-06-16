package com.expense.tracker

import android.app.Application
import android.util.Log
import com.expense.tracker.ui.trash.TrashViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class ExpenseApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        purgeExpiredTrash()
    }

    /**
     * App 启动跑一次：清理超过 30 天的软删记录。
     * 失败不影响 App 启动 — purgeExpired 抛错时只 Log，不传播。
     */
    private fun purgeExpiredTrash() {
        appScope.launch {
            runCatching {
                val cutoff = System.currentTimeMillis() -
                    TimeUnit.DAYS.toMillis(TrashViewModel.TRASH_RETENTION_DAYS.toLong())
                val purged = container.expenseRepo.purgeExpired(cutoff)
                if (purged > 0) Log.i("ExpenseApp", "purged $purged expired trash records")
            }.onFailure { Log.w("ExpenseApp", "purgeExpired failed", it) }
        }
    }
}
