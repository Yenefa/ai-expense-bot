package com.expense.tracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.expense.tracker.R
import com.expense.tracker.data.budget.BudgetCalculator
import com.expense.tracker.data.budget.BudgetPrefs
import com.expense.tracker.data.db.AppDatabase
import com.expense.tracker.data.model.Money
import com.expense.tracker.data.repo.ExpenseRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** 桌面组件：本月支出 + 预算进度 + 快捷记账入口。 */
class ExpenseWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        if (appWidgetIds.isEmpty()) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val snapshot = withTimeout(SNAPSHOT_TIMEOUT_MS) {
                    readSnapshot(context.applicationContext)
                }
                updateWidgets(context, appWidgetManager, appWidgetIds, snapshot)
            } catch (_: Exception) {
                // Keep the last rendered widget when storage is temporarily unavailable.
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun updateWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        snapshot: Triple<String, String, Int>,
    ) {
        val (amountText, budgetText, progress) = snapshot
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_expense)
            views.setTextViewText(R.id.widget_amount, amountText)
            views.setTextViewText(R.id.widget_budget_text, budgetText)
            views.setProgressBar(R.id.widget_budget_bar, 100, progress, false)
            val intent = Intent(context, com.expense.tracker.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_open, pending)
            views.setOnClickPendingIntent(R.id.widget_amount, pending)
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    private suspend fun readSnapshot(context: Context): Triple<String, String, Int> {
        val db = AppDatabase.get(context)
        val repo = ExpenseRepository(db.expenseDao())
        val budgetPrefs = BudgetPrefs.fromContext(context)
        val budget = budgetPrefs.snapshot.first()
        val zone = ZoneId.systemDefault()
        val monthStart = LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone)
            .toInstant().toEpochMilli()
        val list = repo.observeInRange(monthStart, Long.MAX_VALUE).first()
            .filter { it.deletedAt == null }
        val spent = list.sumOf { it.amountCents }
        val spentByCategory = list
            .groupBy { it.categoryId }
            .mapValues { (_, items) -> items.sumOf { it.amountCents } }
        val overview = BudgetCalculator.overview(
            monthlyLimitCents = budget.monthlyLimitCents,
            categoryLimitsCents = budget.categoryLimitsCents,
            monthlySpentByCategory = spentByCategory,
        )
        val amountText = "本月支出 ¥${Money.formatYuan(spent)}"
        val monthly = overview.monthly
        val budgetText = when {
            monthly == null -> "未设置预算"
            monthly.status == com.expense.tracker.data.budget.BudgetStatus.OVER ->
                "⚠️ 已超支 ¥${Money.formatYuan(monthly.amountCents - monthly.limitCents)}"
            else -> "预算已用 ${(monthly.percent * 100).toInt()}% · ¥${Money.formatYuan(monthly.amountCents)} / ¥${Money.formatYuan(monthly.limitCents)}"
        }
        val progress = ((monthly?.percent ?: 0.0) * 100).toInt().coerceIn(0, 100)
        return Triple(amountText, budgetText, progress)
    }

    companion object {
        private const val SNAPSHOT_TIMEOUT_MS = 8_000L

        fun requestRefresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ExpenseWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                context.sendBroadcast(
                    Intent(context, ExpenseWidgetProvider::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    },
                )
            }
        }
    }
}
