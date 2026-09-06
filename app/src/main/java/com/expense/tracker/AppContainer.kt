package com.expense.tracker

import android.content.Context
import com.expense.tracker.data.db.AppDatabase
import com.expense.tracker.data.backup.BackupRepository
import com.expense.tracker.data.budget.BudgetCalculator
import com.expense.tracker.data.budget.BudgetPrefs
import com.expense.tracker.data.budget.BudgetStatus
import com.expense.tracker.data.model.Money
import com.expense.tracker.data.prefs.UserPrefs
import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.expense.tracker.data.reminder.ReminderPrefs
import com.expense.tracker.data.recurring.RecurringGenerator
import com.expense.tracker.data.subscription.SubscriptionPrefs
import com.expense.tracker.data.db.RecurringRuleDao
import com.expense.tracker.data.repo.ChatRepository
import com.expense.tracker.data.repo.ExpenseRepository
import com.expense.tracker.data.repo.LlmMutationApplier
import com.expense.tracker.data.repo.RoomTransactionRunner
import com.expense.tracker.llm.LlmClient
import com.expense.tracker.llm.LlmPrompt
import com.expense.tracker.llm.LlmResponseParser
import com.expense.tracker.llm.ChatLlmCoordinator
import com.expense.tracker.llm.AnalyticsInsightsParser
import com.expense.tracker.llm.BillImportResult
import com.expense.tracker.llm.importFromBillText
import com.expense.tracker.llm.AiAccessResolver
import com.expense.tracker.ocr.MlKitOcrRecognizer
import com.expense.tracker.ocr.OcrRecognizer
import com.expense.tracker.ui.chat.LlmResult
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first

class AppContainer(context: Context) {
    private val appCtx = context.applicationContext

    val db: AppDatabase by lazy { AppDatabase.get(appCtx) }
    val userPrefs: UserPrefs by lazy { UserPrefs.fromContext(appCtx) }
    val subscriptionPrefs: SubscriptionPrefs by lazy { SubscriptionPrefs.fromContext(appCtx) }
    val budgetPrefs: BudgetPrefs by lazy { BudgetPrefs.fromContext(appCtx) }
    val reminderPrefs: ReminderPrefs by lazy { ReminderPrefs.fromContext(appCtx) }
    val recurringDao: RecurringRuleDao by lazy { db.recurringRuleDao() }
    val expenseRepo: ExpenseRepository by lazy { ExpenseRepository(db.expenseDao()) }
    val chatRepo: ChatRepository by lazy { ChatRepository(db.chatDao()) }
    val backupRepo: BackupRepository by lazy { BackupRepository(db, userPrefs) }
    val llmClient: LlmClient by lazy {
        LlmClient(onSubscriptionUnauthorized = { subscriptionPrefs.clearSubscription() })
    }
    val ocrRecognizer: OcrRecognizer by lazy { MlKitOcrRecognizer() }
    private val aiAccessResolver: AiAccessResolver by lazy {
        AiAccessResolver(
            subscriptionAccess = { subscriptionPrefs.activeAccess() },
            subscriptionBaseUrl = BuildConfig.SUBSCRIPTION_API_BASE_URL,
        )
    }
    private val llmMutationApplier: LlmMutationApplier by lazy {
        LlmMutationApplier(expenseRepo, chatRepo, RoomTransactionRunner(db))
    }
    private val chatLlmCoordinator: ChatLlmCoordinator by lazy {
        ChatLlmCoordinator(
            expenseRepository = expenseRepo,
            chatRepository = chatRepo,
            requestJson = { text, prefs, systemPrompt, history ->
                val config = aiAccessResolver.resolve(prefs)
                llmClient.chatJson(
                    baseUrl = config.baseUrl,
                    apiKey = config.apiKey,
                    model = config.model,
                    userText = text,
                    systemPrompt = systemPrompt,
                    installationId = config.installationId,
                    history = history,
                )
            },
            applyPlan = llmMutationApplier::apply,
        )
    }

    /** Expense Agent：查询轮先执行本地工具注入提示词；模糊意图发一次轻量升级调用。 */
    private val expenseAgent: com.expense.tracker.agent.ExpenseAgent by lazy {
        val toolContext = com.expense.tracker.agent.AgentToolContext(
            expenseRepository = expenseRepo,
            budgetSnapshotProvider = { budgetPrefs.snapshot.first() },
        )
        val escalator: suspend (String) -> com.expense.tracker.agent.IntentEscalation? = { text ->
            val config = aiAccessResolver.resolve(userPrefs.snapshot.first())
            val raw = llmClient.chatJson(
                baseUrl = config.baseUrl,
                apiKey = config.apiKey,
                model = config.model,
                userText = text.take(200),
                systemPrompt = com.expense.tracker.agent.IntentEscalationPrompt.systemPrompt(),
                installationId = config.installationId,
                temperature = 0.0,
            )
            com.expense.tracker.agent.IntentEscalationParser.parse(raw)
        }
        com.expense.tracker.agent.ExpenseAgent(
            coordinator = chatLlmCoordinator,
            toolContext = toolContext,
            escalateIntent = escalator,
        )
    }

    val billImportHandler: suspend (String, UserPrefsSnapshot) -> BillImportResult = { ocrText, prefs ->
        val config = aiAccessResolver.resolve(prefs)
        importFromBillText(llmClient, config, ocrText)
    }

    /** 周期账单到期检查：App 启动与每日提醒时调用。 */
    val recurringRunner: suspend () -> Int = {
        RecurringGenerator.runOnce(recurringDao, expenseRepo)
    }

    /** 对话 LLM：Agent 先路由意图，记账/闲聊走原管线，查询轮注入本地工具结果。 */
    val llmHandler: suspend (String, UserPrefsSnapshot) -> LlmResult = expenseAgent::submit
    val llmConfirmationHandler: suspend (String) -> LlmResult = chatLlmCoordinator::confirm
    val llmCancellationHandler: (String) -> Unit = { chatLlmCoordinator.cancel(it) }

    /** 记账后的预算预警文案（达到 90% 或超支时非空）。 */
    val budgetWarningProvider: suspend () -> String? = suspend {
        val budget = budgetPrefs.snapshot.first()
        val monthStart = LocalDate.now(ZoneId.systemDefault()).withDayOfMonth(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val list = expenseRepo.observeInRange(monthStart, Long.MAX_VALUE).first()
        val spentByCategory = list
            .filter { it.deletedAt == null }
            .groupBy { it.categoryId }
            .mapValues { (_, items) -> items.sumOf { it.amountCents } }
        val overview = BudgetCalculator.overview(
            monthlyLimitCents = budget.monthlyLimitCents,
            categoryLimitsCents = budget.categoryLimitsCents,
            monthlySpentByCategory = spentByCategory,
        )
        val monthly = overview.monthly
        when {
            monthly == null -> null
            monthly.status == BudgetStatus.WARN ->
                "⚠️ 本月已用 ¥${Money.formatYuan(monthly.amountCents)}，达到预算 ${(monthly.percent * 100).toInt()}%，注意控制。"
            monthly.status == BudgetStatus.OVER ->
                "⚠️ 本月已超支 ¥${Money.formatYuan(monthly.amountCents - monthly.limitCents)}（预算 ¥${Money.formatYuan(monthly.limitCents)}）。"
            else -> null
        }
    }

    /** 智核分析：发送当前周期的消费数据提示词，返回洞察列表。 */
    val analyticsAnalyzer: suspend (String, UserPrefsSnapshot) -> List<String> = { prompt, prefs ->
        runCatching {
            val config = aiAccessResolver.resolve(prefs)
            val raw = llmClient.chatJson(
                baseUrl = config.baseUrl,
                apiKey = config.apiKey,
                model = config.model,
                userText = prompt,
                systemPrompt = LlmPrompt.analyticsSystemPrompt(),
                installationId = config.installationId,
            )
            AnalyticsInsightsParser.parse(raw)
        }.getOrElse { listOf("分析失败：${it.message ?: "未知错误"}") }
    }
}
