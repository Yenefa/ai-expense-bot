package com.expense.tracker.proactive

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.expense.tracker.data.budget.BudgetPrefs
import com.expense.tracker.data.db.AppDatabase
import com.expense.tracker.data.prefs.ProactivePrefs
import com.expense.tracker.data.repo.ExpenseRepository
import com.expense.tracker.memory.MemoryGovernor
import com.expense.tracker.memory.UserProfilePrefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * 每日主动提醒检查（后台）：App 不在前台时也能让提醒真正到达用户。
 *
 * - 复用 [ProactiveEngine] + [ProactiveGovernor] 的全部硬约束（每日 1 条 / 同类冷却 / 可关闭）；
 * - 后台只使用确定性文案（copywriter = null），不发起任何网络调用；
 * - 通知权限缺失时跳过评估（避免"消耗"当日额度却没有可见投递）；
 * - 周期由 [ProactiveScheduler] 的 PeriodicWorkRequest 驱动（WorkManager 持久化，重启自动恢复）。
 */
class ProactiveInsightWorker(
    appContext: Context,
    params: WorkerParameters,
) : Worker(appContext, params) {

    override fun doWork(): Result {
        val context = applicationContext
        if (!ProactiveNotifier.canPost(context)) return Result.success()

        val alert = runCatching {
            runBlocking { engine(context).evaluate() }
        }.getOrNull() ?: return Result.success()
        ProactiveNotifier.notify(context, alert)
        return Result.success()
    }

    private fun engine(context: Context): ProactiveEngine {
        val db = AppDatabase.get(context)
        val prefs = ProactivePrefs.create(context)
        val budgetPrefs = BudgetPrefs.fromContext(context)
        val memoryGovernor = MemoryGovernor(UserProfilePrefs.create(context))
        val governor = ProactiveGovernor(
            stateStore = prefs,
            enabledProvider = { prefs.enabledNow() },
            copywriter = null,
        )
        return ProactiveEngine(
            expenseRepository = ExpenseRepository(db.expenseDao()),
            budgetSnapshot = { budgetPrefs.snapshot.first() },
            memoryFacts = { memoryGovernor.snapshot() },
            governor = governor,
        )
    }
}
