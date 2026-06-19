package com.expense.tracker

import android.content.Context
import com.expense.tracker.data.db.AppDatabase
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.model.Category
import com.expense.tracker.data.prefs.UserPrefs
import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.expense.tracker.data.repo.ChatRepository
import com.expense.tracker.data.repo.ExpenseRepository
import com.expense.tracker.llm.LlmClient
import com.expense.tracker.llm.LlmPrompt
import com.expense.tracker.llm.LlmResponseParser
import com.expense.tracker.llm.ParsedAction
import com.expense.tracker.ui.chat.LlmResult
import kotlinx.coroutines.flow.first

class AppContainer(context: Context) {
    private val appCtx = context.applicationContext

    val db: AppDatabase by lazy { AppDatabase.get(appCtx) }
    val userPrefs: UserPrefs by lazy { UserPrefs.fromContext(appCtx) }
    val expenseRepo: ExpenseRepository by lazy { ExpenseRepository(db.expenseDao()) }
    val chatRepo: ChatRepository by lazy { ChatRepository(db.chatDao()) }
    val llmClient: LlmClient by lazy { LlmClient() }

    /** 对话 LLM：新增记账 + 删除/修改已有记录（v2.9） */
    val llmHandler: suspend (String, UserPrefsSnapshot) -> LlmResult = { text, prefs ->
        runCatching {
            // 拉最近 3 天活跃记录注入提示词，数量小、精确度高、LLM 更容易匹配
            val now = System.currentTimeMillis()
            val threeDaysAgo = now - 3 * 24 * 3600_000L
            val recentByFlow = expenseRepo.observeInRange(threeDaysAgo, now)
            // 取一次 snapshot
            val recentRecords = recentByFlow.first()
            val raw = llmClient.chatJson(
                baseUrl = prefs.baseUrl,
                apiKey = prefs.apiKey,
                model = prefs.model,
                userText = text,
                systemPrompt = LlmPrompt.systemPrompt(now, recentRecords),
            )
            val result = LlmResponseParser.parse(raw)

            val ids = mutableListOf<Long>()

            // 1) 处理新增
            result.expenses.forEach { item ->
                ids += expenseRepo.add(
                    amount = item.amount,
                    categoryId = item.categoryId,
                    note = item.note,
                    occurredAt = item.occurredAtMillis ?: now,
                )
            }

            // 2) 处理 actions（删除/修改）
            result.actions.forEach { action ->
                when (action) {
                    is ParsedAction.Delete -> {
                        expenseRepo.softDelete(action.expenseId)
                    }
                    is ParsedAction.Update -> {
                        val existing = expenseRepo.getById(action.expenseId)
                        if (existing != null) {
                            val catId = action.categoryId ?: existing.categoryId
                            if (Category.byId(catId) != null) {
                                expenseRepo.update(existing.copy(
                                    amount = action.amount ?: existing.amount,
                                    categoryId = catId,
                                    note = action.note ?: existing.note,
                                    occurredAt = action.occurredAtMillis ?: existing.occurredAt,
                                ))
                            }
                        }
                    }
                    is ParsedAction.Add -> { /* not used in actions, only in expenses */ }
                }
            }

            LlmResult.Ok(
                replyText = result.reply,
                expenseId = ids.firstOrNull(),
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
