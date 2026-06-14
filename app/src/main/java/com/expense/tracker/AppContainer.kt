package com.expense.tracker

import android.content.Context
import com.expense.tracker.data.db.AppDatabase
import com.expense.tracker.data.prefs.UserPrefs

/**
 * 全局依赖容器。一切单例从这里获取，禁止再用 object 单例存状态。
 * 仓库 / LLM 客户端等会在后续任务中加入。
 */
class AppContainer(context: Context) {
    private val appCtx = context.applicationContext

    val db: AppDatabase by lazy { AppDatabase.get(appCtx) }
    val userPrefs: UserPrefs by lazy { UserPrefs.fromContext(appCtx) }
}
