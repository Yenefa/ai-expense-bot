package com.expense.tracker.bench

import com.expense.tracker.agent.AgentRoute
import com.expense.tracker.agent.AgentToolContext
import com.expense.tracker.agent.ConversationActionContext
import com.expense.tracker.agent.ExpenseAgent
import com.expense.tracker.agent.FakeChatDao
import com.expense.tracker.agent.FakeExpenseDao
import com.expense.tracker.agent.IntentEscalationParser
import com.expense.tracker.agent.IntentEscalationPrompt
import com.expense.tracker.data.budget.BudgetSnapshot
import com.expense.tracker.data.db.ChatMessageEntity
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.prefs.ThemeMode
import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.expense.tracker.data.repo.ChatRepository
import com.expense.tracker.data.repo.ExpenseRepository
import com.expense.tracker.data.repo.LlmMutationApplier
import com.expense.tracker.data.repo.TransactionRunner
import com.expense.tracker.llm.ChatLlmCoordinator
import com.expense.tracker.llm.LlmClient
import com.expense.tracker.llm.LlmMutationPlan
import com.expense.tracker.llm.LlmPrompt
import com.expense.tracker.llm.LlmThinkingPolicy
import com.expense.tracker.llm.ParsedAction
import com.expense.tracker.ui.chat.LlmResult
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.junit.Assume
import org.junit.Test

/**
 * ExpenseBench v2 LLM 端到端行为评测（按需运行，不进常规 CI）：
 * 跑真实模型 + 真实 Agent 管线（Router → Escalation → Tools → Planner → Applier），
 * 在内存数据库上测：路由 / 工具 / 变更笔数 / 删除确认门 / 最终账目状态。
 *
 * ```
 * EXPENSEBENCH_API_KEY=sk-xxx \
 * EXPENSEBENCH_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1 \
 * EXPENSEBENCH_MODEL=qwen3.7-flash \
 * EXPENSEBENCH_CONCURRENCY=4 \
 * ./gradlew :app:testDebugUnitTest --tests "com.expense.tracker.bench.LlmAgentBehaviorBenchTest"
 * ```
 * 报告写 `docs/expensebench-v2-llm-report-<model>.md`；EXPENSEBENCH_LIMIT 可先冒烟。
 * 首要指标 False Mutation Rate（目标 0%）；协议见 docs/expensebench-v2.md。
 */
class LlmAgentBehaviorBenchTest {

    private val apiKey = System.getenv("EXPENSEBENCH_API_KEY").orEmpty()
    private val baseUrl = System.getenv("EXPENSEBENCH_BASE_URL") ?: "https://api.deepseek.com"
    private val model = System.getenv("EXPENSEBENCH_MODEL") ?: "deepseek-chat"
    private val limit = System.getenv("EXPENSEBENCH_LIMIT")?.toIntOrNull() ?: Int.MAX_VALUE
    private val concurrency = (System.getenv("EXPENSEBENCH_CONCURRENCY")?.toIntOrNull() ?: 4).coerceIn(1, 8)

    private class InlineRunner : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }

    @Test
    fun runBenchmark() {
        Assume.assumeTrue(
            "跳过：未设置 EXPENSEBENCH_API_KEY。AgentBehaviorBench 按需运行，设置环境变量后执行（见类注释）。",
            apiKey.isNotBlank(),
        )

        val cases = AgentBehaviorDataset.load().take(limit)
        val client = LlmClient()
        val ranAt = OffsetDateTime.now()
        val observations = ConcurrentHashMap<String, BehaviorObservation>()
        val failures = AtomicInteger(0)
        val done = AtomicInteger(0)

        runBlocking {
            val permits = Semaphore(concurrency)
            cases.map { case ->
                async(Dispatchers.IO) {
                    permits.withPermit {
                        val observation = runCatching { runCase(case, client, prefs()) }
                            .getOrElse { error ->
                                failures.incrementAndGet()
                                System.err.println("[${case.id}] 运行失败：${error.message}")
                                BehaviorObservation(error = error.message ?: "unknown")
                            }
                        observations[case.id] = observation
                        val finished = done.incrementAndGet()
                        if (finished % 20 == 0 || finished == cases.size) println("进度：$finished/${cases.size}")
                    }
                }
            }.awaitAll()
        }

        val report = AgentBehaviorEvaluator.evaluate(cases, observations)
        val failureLines = cases.mapNotNull { case ->
            val observation = observations[case.id] ?: return@mapNotNull null
            val reasons = AgentBehaviorEvaluator.describeFailures(case, observation)
            if (reasons.isEmpty()) {
                null
            } else {
                "${case.id} [${case.bucket}] 「${case.text.take(24)}」：${reasons.joinToString("；")}"
            }
        }
        val dataPromptSha = ExpenseBenchDataset.sha256Hex(
            LlmPrompt.systemPrompt(ExpenseBenchDataset.benchNowMillis).toByteArray(Charsets.UTF_8),
        )
        val escalationPromptSha = ExpenseBenchDataset.sha256Hex(
            IntentEscalationPrompt.systemPrompt().toByteArray(Charsets.UTF_8),
        )
        val markdown = report.toMarkdown(
            model = "$model @ ${baseUrl.removePrefix("https://").removePrefix("http://")}",
            source = "LLM 端到端实测（真实 Router/Escalation/Tools/Planner/Applier + 内存数据库；运行失败 ${failures.get()} 条按无路由/无变更计）",
            metadata = listOf(
                "temperature = 0.0（固定）",
                "enable_thinking = false（Qwen 结构化路径；其他模型缺省）",
                "concurrency = $concurrency",
                "dataset_sha256 = ${AgentBehaviorDataset.datasetSha256()}",
                "system_prompt_sha256 = $dataPromptSha",
                "escalation_prompt_sha256 = $escalationPromptSha",
                "ran_at = $ranAt",
                "复现：同哈希数据 + 同 prompt + temperature=0 + 同模型快照 ⇒ 结果应一致（±供应商非确定性）",
            ),
            caseFailureLines = failureLines,
        )
        val safeModel = model.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val outFile = File(repoRoot(), "docs/expensebench-v2-llm-report-$safeModel.md")
        outFile.parentFile?.mkdirs()
        outFile.writeText(markdown)
        println(markdown)

        // 阈值随模型换代调整；报告即交付物。这里只保证每条都有观测。
        assertThat(observations).hasSize(cases.size)
    }

    private suspend fun runCase(case: BehaviorCase, client: LlmClient, prefs: UserPrefsSnapshot): BehaviorObservation {
        val zone = ExpenseBenchDataset.benchZone
        val now = ExpenseBenchDataset.benchNowMillis
        val expenseDao = FakeExpenseDao()
        val chatDao = FakeChatDao()

        val seedIds = case.seed_expenses.map { seed ->
            expenseDao.insert(
                ExpenseEntity(
                    amountCents = seed.amount_cents,
                    categoryId = seed.category,
                    note = seed.note,
                    occurredAt = LocalDate.parse(seed.date).atTime(LocalTime.parse(seed.time))
                        .atZone(zone).toInstant().toEpochMilli(),
                    createdAt = now,
                ),
            )
        }
        case.history.forEachIndexed { index, turn ->
            chatDao.insert(
                ChatMessageEntity(
                    role = turn.role,
                    content = turn.content,
                    createdAt = now - (case.history.size - index) * 1_000L,
                    relatedExpenseIdsCsv = if (turn.link_seed && seedIds.isNotEmpty()) seedIds.joinToString(",") else null,
                ),
            )
        }

        var route: AgentRoute? = null
        val tools = mutableListOf<String>()
        var appliedPlan: LlmMutationPlan? = null
        val initialContext = ConversationActionContext(
            previousRoute = case.previous_route?.let(AgentRoute::valueOf),
            recentExpenseIds = seedIds,
            previousMutationBatch = seedIds,
        )
        val applier = LlmMutationApplier(ExpenseRepository(expenseDao), ChatRepository(chatDao), InlineRunner())
        val coordinator = ChatLlmCoordinator(
            expenseRepository = ExpenseRepository(expenseDao),
            chatRepository = ChatRepository(chatDao),
            requestJson = { text, _, systemPrompt, history, structuredRequest ->
                client.chatJson(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    model = model,
                    userText = text,
                    systemPrompt = systemPrompt,
                    history = history,
                    temperature = 0.0,
                    enableThinking = LlmThinkingPolicy.enableThinkingFor(structuredRequest, model),
                )
            },
            applyPlan = { plan ->
                appliedPlan = plan
                applier.apply(plan)
            },
            nowProvider = { now },
            zone = zone,
        )
        val toolContext = AgentToolContext(
            expenseRepository = ExpenseRepository(expenseDao),
            budgetSnapshotProvider = { BudgetSnapshot() },
            zone = zone,
            onToolCall = { tools += it },
        )
        val agent = ExpenseAgent(
            coordinator = coordinator,
            toolContext = toolContext,
            escalateIntent = { text ->
                val raw = client.chatJson(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    model = model,
                    userText = text.take(200),
                    systemPrompt = IntentEscalationPrompt.systemPrompt(),
                    temperature = 0.0,
                    enableThinking = LlmThinkingPolicy.enableThinkingFor(structuredRequest = true, model = model),
                )
                IntentEscalationParser.parse(raw)
            },
            nowProvider = { now },
            zone = zone,
            onRouteResolved = { route = it },
            initialConversationContext = initialContext,
        )

        val result = agent.submit(case.text, prefs)
        val appliedCount = appliedPlan?.let { it.result.expenses.size + it.result.actions.size } ?: 0
        return BehaviorObservation(
            route = route?.name,
            tools = tools.distinct(),
            appliedCount = appliedCount,
            appliedItems = appliedPlan?.let { planToItems(it) }.orEmpty(),
            pending = result is LlmResult.ConfirmationRequired,
            pendingCount = (result as? LlmResult.ConfirmationRequired)?.preview?.count ?: 0,
            activeCount = expenseDao.getAllActiveOnce().size,
            error = (result as? LlmResult.Error)?.message,
        )
    }

    /** 把已应用计划映射为"最终状态"观测（insert / update；delete 只计笔数）。 */
    private fun planToItems(plan: LlmMutationPlan): List<ObservedItem> {
        val inserts = plan.result.expenses.map { item ->
            ObservedItem(
                kind = "insert",
                amountCents = item.amountCents,
                categoryId = item.categoryId,
                note = item.note,
                occurredAtMillis = item.occurredAtMillis,
            )
        }
        val updates = plan.result.actions.mapNotNull { action ->
            if (action !is ParsedAction.Update) return@mapNotNull null
            val before = plan.targetSnapshots.getValue(action.expenseId)
            ObservedItem(
                kind = "update",
                amountCents = action.amountCents ?: before.amountCents,
                categoryId = action.categoryId ?: before.categoryId,
                note = action.note ?: before.note,
                occurredAtMillis = action.occurredAtMillis ?: before.occurredAt,
            )
        }
        return inserts + updates
    }

    private fun prefs() = UserPrefsSnapshot(
        llmEnabled = true,
        baseUrl = baseUrl,
        apiKey = apiKey,
        model = model,
        themeMode = ThemeMode.SYSTEM,
    )

    private fun repoRoot(): File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").exists() }
        ?: File(System.getProperty("user.dir"))
}
