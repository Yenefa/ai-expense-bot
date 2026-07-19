package com.expense.tracker

import android.content.Context
import android.util.Log
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
    val ocrRecognizer: com.expense.tracker.ocr.OcrRecognizer by lazy {
        com.expense.tracker.ocr.MlKitOcrRecognizer()
    }
    val billImportHandler: suspend (String, UserPrefsSnapshot) -> com.expense.tracker.llm.BillImportResult =
        { ocrText, prefs -> com.expense.tracker.llm.importFromBillText(llmClient, prefs, ocrText) }

    companion object { private const val TAG = "LLM" }

    /** 对话 LLM：新增记账 + 删除/修改已有记录（v3.0） */
    val llmHandler: suspend (String, UserPrefsSnapshot) -> LlmResult = { text, prefs ->
        runCatching {
            // 拉最近 3 天活跃记录注入提示词
            // to 用 now+24h 防止"今天的记录 occurredAt 在 now 之后"被排除
            val now = System.currentTimeMillis()
            val threeDaysAgo = now - 3 * 24 * 3600_000L
            val dayAhead = now + 24 * 3600_000L
            val recentByFlow = expenseRepo.observeInRange(threeDaysAgo, dayAhead)
            val recentRecords = recentByFlow.first()
            Log.d(TAG, "[Handler] userText=$text")
            Log.d(TAG, "[Handler] query range: from ${java.time.Instant.ofEpochMilli(threeDaysAgo)} to ${java.time.Instant.ofEpochMilli(dayAhead)}")
            Log.d(TAG, "[Handler] injected ${recentRecords.size} recent records (3-day window)")
            recentRecords.forEach {
                Log.d(TAG, "[Handler]   #${it.id} | ${it.categoryId} | ¥${"%.2f".format(it.amount)} | ${it.note}")
            }

            val raw = llmClient.chatJson(
                baseUrl = prefs.baseUrl,
                apiKey = prefs.apiKey,
                model = prefs.model,
                userText = text,
                systemPrompt = LlmPrompt.systemPrompt(now, recentRecords),
            )
            Log.d(TAG, "[Handler] LLM raw response (first 500 chars): ${raw.take(500)}")

            val result = LlmResponseParser.parse(raw)
            Log.d(TAG, "[Handler] parsed reply=${result.reply.take(100)} | expenses=${result.expenses.size} | actions=${result.actions.size}")
            result.actions.forEach { Log.d(TAG, "[Handler]   action=$it") }

            val ids = mutableListOf<Long>()

            // 1) 处理新增
            result.expenses.forEach { item ->
                ids += expenseRepo.add(
                    amount = item.amount,
                    categoryId = item.categoryId,
                    note = item.note,
                    occurredAt = item.occurredAtMillis ?: now,
                )
                Log.d(TAG, "[Handler] expense ADDED #${ids.last()} | ${item.categoryId} ¥${"%.2f".format(item.amount)}")
            }

            // 2) 处理 actions
            result.actions.forEach { action ->
                when (action) {
                    is ParsedAction.Delete -> {
                        expenseRepo.softDelete(action.expenseId)
                        Log.d(TAG, "[Handler] expense SOFT-DELETED #${action.expenseId}")
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
                                Log.d(TAG, "[Handler] expense UPDATED #${action.expenseId}")
                            }
                        } else {
                            Log.w(TAG, "[Handler] update target #${action.expenseId} not found in DB")
                        }
                    }
                    is ParsedAction.Add -> {}
                }
            }

            LlmResult.Ok(
                replyText = result.reply,
                expenseId = ids.firstOrNull(),
            )
        }.getOrElse {
            Log.e(TAG, "[Handler] FAILED: ${it.message}", it)
            LlmResult.Error(it.message ?: "未知错误")
        }
    }

    /** 智核分析：发送当前周期的消费数据提示词，返回洞察列表。 */
    val analyticsAnalyzer: suspend (String, UserPrefsSnapshot) -> List<String> = { prompt, prefs ->
        com.expense.tracker.llm.analyzeInsights(llmClient, prefs, prompt)
    }
}
