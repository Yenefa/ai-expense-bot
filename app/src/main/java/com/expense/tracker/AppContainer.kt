package com.expense.tracker

import android.content.Context
import com.expense.tracker.data.action.PendingActionResolver
import com.expense.tracker.data.db.AppDatabase
import com.expense.tracker.data.prefs.UserPrefs
import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.expense.tracker.data.repo.ChatRepository
import com.expense.tracker.data.repo.ExpenseRepository
import com.expense.tracker.llm.LlmClient
import com.expense.tracker.llm.LlmResponseParser
import com.expense.tracker.ui.chat.LlmResult

class AppContainer(context: Context) {
    private val appCtx = context.applicationContext

    val db: AppDatabase by lazy { AppDatabase.get(appCtx) }
    val userPrefs: UserPrefs by lazy { UserPrefs.fromContext(appCtx) }
    val expenseRepo: ExpenseRepository by lazy { ExpenseRepository(db.expenseDao()) }
    val chatRepo: ChatRepository by lazy { ChatRepository(db.chatDao()) }
    val llmClient: LlmClient by lazy { LlmClient() }
    val pendingActionResolver: PendingActionResolver by lazy { PendingActionResolver(expenseRepo) }

    /**
     * 对话 LLM：返回 reply（始终展示）+ 写新增支出 + 把"删/改/查"提议解析成待确认卡片。
     *
     * 注意 actions 不会立刻写库；UI 拿到 [LlmResult.Ok.pendingActions] 后会渲染 ActionCard，
     * 用户点确认才会触发 [com.expense.tracker.ui.chat.ChatViewModel.confirmDelete] / confirmUpdate。
     */
    val llmHandler: suspend (String, UserPrefsSnapshot) -> LlmResult = { text, prefs ->
        runCatching {
            val raw = llmClient.chatJson(
                baseUrl = prefs.baseUrl,
                apiKey = prefs.apiKey,
                model = prefs.model,
                userText = text,
            )
            val result = LlmResponseParser.parse(raw)
            val now = System.currentTimeMillis()
            val ids = mutableListOf<Long>()
            result.expenses.forEach { item ->
                ids += expenseRepo.add(
                    amount = item.amount,
                    categoryId = item.categoryId,
                    note = item.note,
                    occurredAt = item.occurredAtMillis ?: now,
                )
            }
            val pending = result.actions.mapNotNull { pendingActionResolver.resolve(it) }
            LlmResult.Ok(
                replyText = result.reply,
                expenseId = ids.firstOrNull(),
                pendingActions = pending,
            )
        }.getOrElse { LlmResult.Error(it.message ?: "未知错误") }
    }

    /** 智核分析：发送当前周期的消费数据提示词，返回洞察列表。 */
    val analyticsAnalyzer: suspend (String, UserPrefsSnapshot) -> List<String> = { prompt, prefs ->
        runCatching {
            val raw = llmClient.chatJson(
                baseUrl = prefs.baseUrl,
                apiKey = prefs.apiKey,
                model = prefs.model,
                userText = prompt,
            )
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; coerceInputValues = true }
            val insights = json.decodeFromString(
                com.expense.tracker.llm.AnalyticsInsightsPayload.serializer(), raw.trim()
            ).insights
            insights.ifEmpty { listOf("暂无洞察，请再试一次。") }
        }.getOrElse { listOf("分析失败：${it.message ?: "未知错误"}") }
    }
}
