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
import com.expense.tracker.llm.LlmThinkingPolicy
import com.expense.tracker.llm.ChatLlmCoordinator
import com.expense.tracker.llm.AnalyticsInsightsParser
import com.expense.tracker.llm.BillImportResult
import com.expense.tracker.llm.importFromBillText
import com.expense.tracker.llm.AiAccessResolver
import com.expense.tracker.memory.MemoryGovernor
import com.expense.tracker.memory.UserProfilePrefs
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
    /** 长期记忆（UserProfile）：独立 DataStore；写入口只有 MemoryGovernor.confirm/用户管理操作。 */
    val userProfileStore: UserProfilePrefs by lazy { UserProfilePrefs.create(appCtx) }
    val memoryGovernor: MemoryGovernor by lazy { MemoryGovernor(userProfileStore) }

    /** 主动提醒：规则决策 + 硬约束；LLM 只允许改文案。 */
    val proactivePrefs: com.expense.tracker.data.prefs.ProactivePrefs by lazy {
        com.expense.tracker.data.prefs.ProactivePrefs.create(appCtx)
    }
    private val proactiveGovernor: com.expense.tracker.proactive.ProactiveGovernor by lazy {
        com.expense.tracker.proactive.ProactiveGovernor(
            stateStore = proactivePrefs,
            enabledProvider = { proactivePrefs.enabledNow() },
            copywriter = { alert ->
                runCatching {
                    val snapshot = userPrefs.snapshot.first()
                    if (!snapshot.llmEnabled) return@runCatching null
                    val config = aiAccessResolver.resolve(snapshot)
                    llmClient.chatJson(
                        baseUrl = config.baseUrl,
                        apiKey = config.apiKey,
                        model = config.model,
                        userText = com.expense.tracker.proactive.ProactiveCopyPrompt.userText(alert),
                        systemPrompt = com.expense.tracker.proactive.ProactiveCopyPrompt.systemPrompt(),
                        installationId = config.installationId,
                        temperature = 0.0,
                        enableThinking = LlmThinkingPolicy.enableThinkingFor(true, config.model),
                    ).trim()
                }.getOrNull()
            },
        )
    }
    val subscriptionPrefs: SubscriptionPrefs by lazy { SubscriptionPrefs.fromContext(appCtx) }
    val budgetPrefs: BudgetPrefs by lazy { BudgetPrefs.fromContext(appCtx) }
    val reminderPrefs: ReminderPrefs by lazy { ReminderPrefs.fromContext(appCtx) }
    val recurringDao: RecurringRuleDao by lazy { db.recurringRuleDao() }
    val expenseRepo: ExpenseRepository by lazy { ExpenseRepository(db.expenseDao()) }
    val chatRepo: ChatRepository by lazy { ChatRepository(db.chatDao()) }
    val backupRepo: BackupRepository by lazy { BackupRepository(db, userPrefs, userProfileStore) }
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
            requestJson = { text, prefs, systemPrompt, history, structuredRequest ->
                val config = aiAccessResolver.resolve(prefs)
                llmClient.chatJson(
                    baseUrl = config.baseUrl,
                    apiKey = config.apiKey,
                    model = config.model,
                    userText = text,
                    systemPrompt = systemPrompt,
                    installationId = config.installationId,
                    history = history,
                    enableThinking = LlmThinkingPolicy.enableThinkingFor(structuredRequest, config.model),
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
                enableThinking = LlmThinkingPolicy.enableThinkingFor(structuredRequest = true, model = config.model),
            )
            com.expense.tracker.agent.IntentEscalationParser.parse(raw)
        }
        com.expense.tracker.agent.ExpenseAgent(
            coordinator = chatLlmCoordinator,
            toolContext = toolContext,
            escalateIntent = escalator,
            memoryGovernor = memoryGovernor,
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

    /** 长期记忆确认：唯一的 persist 触发点（人类确认）。 */
    val llmMemoryConfirmationHandler: suspend (String) -> LlmResult = { token ->
        memoryGovernor.confirm(token)?.let { fact ->
            LlmResult.Ok(replyText = "✅ 已记住：${fact.summary()}")
        } ?: LlmResult.Error("这条记忆确认已失效，请重新说一次。")
    }
    val llmMemoryCancellationHandler: (String) -> Unit = { memoryGovernor.cancel(it) }

    /** 主动提醒输入构建（账目 + 预算 + 已授权财务记忆），前台/后台共用。 */
    private val proactiveEngine: com.expense.tracker.proactive.ProactiveEngine by lazy {
        com.expense.tracker.proactive.ProactiveEngine(
            expenseRepository = expenseRepo,
            budgetSnapshot = { budgetPrefs.snapshot.first() },
            memoryFacts = { memoryGovernor.snapshot() },
            governor = proactiveGovernor,
        )
    }

    /**
     * 主动洞察：确定性规则决定是否提醒（预算/异常/储蓄），每日最多 1 条；
     * LLM 仅可能改写文案，失败自动回退确定性文案。放行即写入提醒中心历史。
     */
    val proactiveInsightProvider: suspend () -> com.expense.tracker.proactive.ProactiveAlert? = {
        runCatching { proactiveEngine.evaluate() }.getOrNull()
    }

    /** 系统通知投递（聊天内 🔔 之外的真正 proactive 渠道）；权限缺失时静默跳过。 */
    val proactiveAlertNotifier: (com.expense.tracker.proactive.ProactiveAlert) -> Unit = { alert ->
        runCatching { com.expense.tracker.proactive.ProactiveNotifier.notify(appCtx, alert) }
        Unit
    }

    /**
     * App 启动检查一次主动提醒：规则放行则写聊天 🔔 + 投递通知（历史由治理器记录）。
     * 每日额度 / 同类冷却由治理器兜底，重复启动不会重复提醒。
     */
    val proactiveStartupCheck: suspend () -> Unit = {
        runCatching {
            proactiveEngine.evaluate()?.let { alert ->
                chatRepo.appendAssistant("🔔 ${alert.copy}")
                proactiveAlertNotifier(alert)
            }
        }
        Unit
    }

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
                enableThinking = LlmThinkingPolicy.enableThinkingFor(structuredRequest = true, model = config.model),
            )
            AnalyticsInsightsParser.parse(raw)
        }.getOrElse { listOf("分析失败：${it.message ?: "未知错误"}") }
    }
}
