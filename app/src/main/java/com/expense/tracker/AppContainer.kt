package com.expense.tracker

import android.content.Context
import com.expense.tracker.data.action.PendingActionResolver
import com.expense.tracker.data.db.AppDatabase
import com.expense.tracker.data.prefs.UserPrefs
import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.expense.tracker.data.repo.ChatRepository
import com.expense.tracker.data.repo.ExpenseRepository
import com.expense.tracker.llm.ChatMsg
import com.expense.tracker.llm.LlmClient
import com.expense.tracker.llm.LlmPrompt
import com.expense.tracker.llm.LlmResponseParser
import com.expense.tracker.ui.chat.LlmResult
import kotlinx.coroutines.flow.first

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
     * 上下文注入策略（v1.2.2 修复"无记忆"问题）：
     *  1. 最近 N=10 条聊天作为 OpenAI messages 喂给 LLM —— 让它能记住"上一句你说了金额是 9 元"
     *  2. 最近 30 条支出作为 system prompt 的"快照" —— 让它能识别"那笔地铁/刚才的咖啡"等引用
     *
     * 没这两层注入，LLM 表现得像金鱼：每条消息都从零开始猜。
     *
     * Token 代价：10 条聊天 ≈ 500-1500 token；30 条支出 ≈ 600 token。可接受。
     *
     * 注意 actions 不会立刻写库；UI 拿到 [LlmResult.Ok.pendingActions] 后会渲染 ActionCard，
     * 用户点确认才会触发 [com.expense.tracker.ui.chat.ChatViewModel.confirmDelete] / confirmUpdate。
     */
    val llmHandler: suspend (String, UserPrefsSnapshot) -> LlmResult = { text, prefs ->
        runCatching {
            // 取最近 10 条聊天（不含当前 user 这一条 — 它会被 LlmClient 单独 append）
            val historyMsgs = chatRepo.observeAll().first().takeLast(10).map { msg ->
                ChatMsg(role = if (msg.role == "assistant") "assistant" else "user",
                        content = msg.content)
            }
            // 取最近 30 条支出快照（按时间倒序）
            val recentExpenses = expenseRepo.observeAll().first().take(30)
            val systemPrompt = LlmPrompt.systemPrompt(recentExpenses = recentExpenses)

            val raw = llmClient.chatJson(
                baseUrl = prefs.baseUrl,
                apiKey = prefs.apiKey,
                model = prefs.model,
                userText = text,
                history = historyMsgs,
                systemPrompt = systemPrompt,
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
                history = emptyList(),
                systemPrompt = com.expense.tracker.llm.LlmPrompt.analyticsSystemPrompt(),
            )
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; coerceInputValues = true }
            val cleaned = LlmResponseParser.extractJsonObject(raw)
            val insights = json.decodeFromString(
                com.expense.tracker.llm.AnalyticsInsightsPayload.serializer(), cleaned
            ).insights
            insights.ifEmpty { listOf("暂无洞察，请再试一次。") }
        }.getOrElse { listOf("分析失败：${it.message ?: "未知错误"}") }
    }
}
