package com.expense.tracker

import android.content.Context
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

    /** 给 ChatViewModel 用的 LLM 流程实现。 */
    val llmHandler: suspend (String, UserPrefsSnapshot) -> LlmResult = { text, prefs ->
        runCatching {
            val raw = llmClient.chatJson(
                baseUrl = prefs.baseUrl,
                apiKey = prefs.apiKey,
                model = prefs.model,
                userText = text,
            )
            val items = LlmResponseParser.parse(raw)
            val now = System.currentTimeMillis()
            val ids = items.map { item ->
                expenseRepo.add(
                    amount = item.amount,
                    categoryId = item.categoryId,
                    note = item.note,
                    occurredAt = item.occurredAtMillis ?: now,
                )
            }
            val summary = items.joinToString(" · ") { item ->
                val cat = com.expense.tracker.data.model.Category.byIdOrOther(item.categoryId)
                "${cat.emoji} ${cat.displayName} ¥${"%.2f".format(item.amount)}"
            }
            val total = items.sumOf { it.amount }
            LlmResult.Ok(
                replyText = "已为你记录 ${items.size} 笔支出：\n\n$summary\n\n合计 ¥${"%.2f".format(total)}",
                expenseId = ids.firstOrNull(),
            )
        }.getOrElse { LlmResult.Error(it.message ?: "未知错误") }
    }
}
