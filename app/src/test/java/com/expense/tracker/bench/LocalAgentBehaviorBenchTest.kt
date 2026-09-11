package com.expense.tracker.bench

import com.expense.tracker.agent.AgentRoute
import com.expense.tracker.agent.AgentRouter
import com.expense.tracker.agent.AgentToolContext
import com.expense.tracker.agent.ConversationActionContext
import com.expense.tracker.agent.ExpenseAgent
import com.expense.tracker.agent.FakeChatDao
import com.expense.tracker.agent.FakeExpenseDao
import com.expense.tracker.data.budget.BudgetSnapshot
import com.expense.tracker.data.model.Category
import com.expense.tracker.data.prefs.ThemeMode
import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.expense.tracker.data.repo.ChatRepository
import com.expense.tracker.data.repo.ExpenseRepository
import com.expense.tracker.data.repo.LlmMutationApplier
import com.expense.tracker.data.repo.MutationApplyResult
import com.expense.tracker.data.repo.TransactionRunner
import com.expense.tracker.llm.ChatLlmCoordinator
import com.expense.tracker.llm.ExpenseTextInterpreter
import com.expense.tracker.ui.chat.LlmResult
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * ExpenseBench v2 离线层：数据集完整性 + 前置路由/工具基线 + 多日期逐笔绑定回归。
 *
 * 不调用 LLM 的确定性部分在这里守住；False Mutation Rate 等端到端指标由
 * `LlmAgentBehaviorBenchTest` 用真实模型跑（按需，env-gated）。
 */
class LocalAgentBehaviorBenchTest {

    private val cases = AgentBehaviorDataset.load()
    private val zone = ExpenseBenchDataset.benchZone
    private val now = ExpenseBenchDataset.benchNowMillis

    private fun prefs() = UserPrefsSnapshot(
        llmEnabled = true,
        baseUrl = "https://example.com/v1",
        apiKey = "bench",
        model = "bench",
        themeMode = ThemeMode.SYSTEM,
    )

    private class InlineRunner : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }

    private fun scriptedJson(expect: List<BenchExpect>): String = buildString {
        append("""{"reply":"ok","expenses":[""")
        expect.forEachIndexed { index, exp ->
            if (index > 0) append(",")
            append(
                """{"amount":"${"%.2f".format(exp.amount_cents / 100.0)}","category":"${exp.category}",""" +
                    """"note":"","occurred_at":null}""",
            )
        }
        append("""],"actions":[]}""")
    }

    /** 按数据集 gold 模拟上一轮会话状态（更正/续记门控、Query 回承）。 */
    private fun contextOf(case: BehaviorCase): ConversationActionContext {
        val seedIds = case.seed_expenses.indices.map { (it + 1).toLong() }
        return ConversationActionContext(
            previousRoute = case.previous_route?.let(AgentRoute::valueOf),
            recentExpenseIds = seedIds,
            previousMutationBatch = seedIds,
        )
    }

    private fun emptyAgent(
        dao: FakeExpenseDao,
        context: ConversationActionContext = ConversationActionContext(),
        onTool: (String) -> Unit = {},
        onRoute: (AgentRoute) -> Unit = {},
    ): ExpenseAgent {
        val coordinator = ChatLlmCoordinator(
            expenseRepository = ExpenseRepository(dao),
            chatRepository = ChatRepository(FakeChatDao()),
            requestJson = { _, _, _, _, _ -> """{"reply":"ok","expenses":[],"actions":[]}""" },
            applyPlan = { MutationApplyResult(emptyList(), emptyList(), 1L) },
            nowProvider = { now },
            zone = zone,
        )
        return ExpenseAgent(
            coordinator = coordinator,
            toolContext = AgentToolContext(ExpenseRepository(dao), { BudgetSnapshot() }, zone, onTool),
            escalateIntent = null,
            nowProvider = { now },
            zone = zone,
            onRouteResolved = onRoute,
            initialConversationContext = context,
        )
    }

    private suspend fun runScripted(dao: FakeExpenseDao, text: String, response: String): LlmResult {
        val applier = LlmMutationApplier(ExpenseRepository(dao), ChatRepository(FakeChatDao()), InlineRunner())
        val coordinator = ChatLlmCoordinator(
            expenseRepository = ExpenseRepository(dao),
            chatRepository = ChatRepository(FakeChatDao()),
            requestJson = { _, _, _, _, _ -> response },
            applyPlan = applier::apply,
            nowProvider = { now },
            zone = zone,
        )
        val agent = ExpenseAgent(
            coordinator = coordinator,
            toolContext = AgentToolContext(ExpenseRepository(dao), { BudgetSnapshot() }, zone),
            escalateIntent = null,
            nowProvider = { now },
            zone = zone,
        )
        return agent.submit(text, prefs())
    }

    @Test
    fun `数据集完整性`() {
        assertThat(cases).hasSize(110)
        assertThat(cases.map { it.id }.toSet()).hasSize(110)
        val byBucket = cases.groupBy { it.bucket }
        assertThat(byBucket.keys).containsExactlyElementsIn(AgentBehaviorDataset.BUCKETS)
        assertThat(byBucket.getValue(AgentBehaviorDataset.NEGATIVE_FP)).hasSize(30)
        assertThat(byBucket.getValue(AgentBehaviorDataset.MULTI_TEMPORAL)).hasSize(30)
        assertThat(byBucket.getValue(AgentBehaviorDataset.ROUTER_AMBIGUOUS)).hasSize(30)
        assertThat(byBucket.getValue(AgentBehaviorDataset.MULTI_TURN)).hasSize(20)

        val routes = setOf("MUTATION", "QUERY", "CHAT")
        val tools = setOf("query_expenses", "analyze_expenses", "get_budget_status")
        cases.forEach { case ->
            case.expected_route?.let { assertThat(routes).contains(it) }
            case.previous_route?.let { assertThat(routes).contains(it) }
            case.expected_tools?.forEach { assertThat(tools).contains(it) }
            case.expect.forEach { exp ->
                assertThat(Category.byId(exp.category)).isNotNull()
                exp.date?.let { assertThat(LocalDate.parse(it)).isNotNull() }
            }
            case.seed_expenses.forEach { seed ->
                assertThat(Category.byId(seed.category)).isNotNull()
                assertThat(LocalDate.parse(seed.date)).isNotNull()
                LocalTime.parse(seed.time)
            }
            if (case.expected_mutation_count == 0) {
                assertThat(case.expect).isEmpty()
                assertThat(case.expected_pending).isFalse()
            }
            if (case.expected_pending) {
                assertThat(case.expected_mutation_count ?: 0).isAtLeast(1)
            }
            if (case.bucket == AgentBehaviorDataset.MULTI_TEMPORAL) {
                assertThat(case.expected_route).isEqualTo("MUTATION")
                assertThat(case.expect).isNotEmpty()
            }
            if (case.bucket == AgentBehaviorDataset.NEGATIVE_FP) {
                assertThat(case.expected_mutation_count).isEqualTo(0)
            }
        }
    }

    @Test
    fun `离线路由与工具基线并输出报告`() = runBlocking<Unit> {
        val scored = cases.filter { it.expected_route != null }
        val routeMisses = mutableListOf<String>()
        scored.forEach { case ->
            val actual = AgentRouter.route(case.text, now, zone, contextOf(case)).route.name
            if (actual != case.expected_route) {
                val suffix = case.note?.let { " — $it" }.orEmpty()
                routeMisses += "${case.id}: expect=${case.expected_route} actual=$actual$suffix"
            }
        }
        val queryExpected = cases.filter { it.expected_route == "QUERY" }
        val queryFrontHit = queryExpected.count {
            AgentRouter.route(it.text, now, zone, contextOf(it)).route == AgentRoute.QUERY
        }

        val toolChecked = mutableListOf<Pair<String, Boolean>>()
        cases.filter { it.expected_tools != null }.forEach { case ->
            if (AgentRouter.route(case.text, now, zone, contextOf(case)).route != AgentRoute.QUERY) return@forEach
            val tools = linkedSetOf<String>()
            emptyAgent(FakeExpenseDao(), context = contextOf(case), onTool = { tools += it })
                .submit(case.text, prefs())
            toolChecked += case.id to (tools.toSet() == case.expected_tools!!.toSet())
        }
        val toolMismatches = toolChecked.filterNot { it.second }
        assertThat(toolMismatches).isEmpty()

        // v3.9.3 本地验收门槛：路由 ≥78/80，Query 前置召回 16/16
        assertThat(scored.size - routeMisses.size).isAtLeast(78)
        assertThat(queryFrontHit).isEqualTo(queryExpected.size)

        val multi = cases.filter { it.bucket == AgentBehaviorDataset.MULTI_TEMPORAL }
        val hintCovered = multi.count { case ->
            val interpretation = ExpenseTextInterpreter.interpret(case.text, now, zone)
            interpretation.expenseHints.size == case.expect.size &&
                interpretation.expenseHints.map { it.amountCents } == case.expect.map { it.amount_cents }
        }

        val report = buildString {
            appendLine("# ExpenseBench v2 — 离线行为基线报告（无 LLM）")
            appendLine()
            appendLine("- 数据集：`app/src/test/resources/expensebench/cases-v2.jsonl`，共 ${cases.size} 条 / 基准时刻 ${ExpenseBenchDataset.BENCH_NOW_ISO}")
            appendLine("- 覆盖：前置路由（不含 Escalation 的确定性层；多轮用例按 gold `previous_route` 模拟会话上下文）、工具选择、多日期端侧提示覆盖率")
            appendLine("- 多日期端侧提示完整覆盖：$hintCovered/${multi.size}（其余金额无元/块，交由 LLM 端 Date Binding）")
            appendLine()
            appendLine("## 前置路由（expected_route 已标注的 ${scored.size} 条）")
            appendLine()
            appendLine("- 准确率：${scored.size - routeMisses.size}/${scored.size}")
            appendLine("- Query 前置召回：$queryFrontHit/${queryExpected.size}（升级路径依赖 LLM，不计入本离线口径）")
            appendLine("- 未命中：")
            if (routeMisses.isEmpty()) appendLine("  - 无") else routeMisses.forEach { appendLine("  - $it") }
            appendLine()
            appendLine("## 工具选择（前置路由=QUERY 的 ${toolChecked.size} 条，集合精确匹配）")
            appendLine()
            appendLine("- 准确率：${toolChecked.size - toolMismatches.size}/${toolChecked.size}")
            if (toolMismatches.isNotEmpty()) {
                toolMismatches.forEach { appendLine("  - ${it.first}") }
            }
            appendLine()
            appendLine("说明：端到端 False Mutation Rate / 路由升级 / 多轮指代由 `LlmAgentBehaviorBenchTest` 用真实模型评测（按需运行）。")
        }

        val outFile = File(repoRoot(), "docs/expensebench-v2-local-report.md")
        outFile.parentFile?.mkdirs()
        outFile.writeText(report)
        println(report)

        // 离线只做确定性硬断言（工具选择），路由缺口以报告呈现、不设阈值。
        assertThat(toolChecked).isNotEmpty()
    }

    @Test
    fun `多日期逐笔绑定回归`() = runBlocking<Unit> {
        val multi = cases.filter { it.bucket == AgentBehaviorDataset.MULTI_TEMPORAL }
        var covered = 0
        val failures = mutableListOf<String>()

        multi.forEach { case ->
            val interpretation = ExpenseTextInterpreter.interpret(case.text, now, zone)
            // 只有"每条期望都有端侧日期+金额提示"的用例才在离线层验证绑定；
            // 部分金额没有 元/块 的用例留给 LLM 端的 Date Binding Accuracy。
            if (interpretation.expenseHints.size != case.expect.size ||
                interpretation.expenseHints.map { it.amountCents } != case.expect.map { it.amount_cents }
            ) {
                return@forEach
            }
            covered++

            val dao = FakeExpenseDao()
            val result = runScripted(dao, case.text, scriptedJson(case.expect))
            if (result is LlmResult.Error) {
                failures += "${case.id}: ${result.message}"
                return@forEach
            }
            val rows = dao.getAllActiveOnce()
            if (rows.size != case.expect.size) {
                failures += "${case.id}: rows=${rows.size} expect=${case.expect.size}"
                return@forEach
            }
            val byAmount = rows.groupBy { it.amountCents }
            for (exp in case.expect) {
                val row = byAmount[exp.amount_cents]?.firstOrNull()
                if (row == null) {
                    failures += "${case.id}: missing amount=${exp.amount_cents}"
                    continue
                }
                val date = Instant.ofEpochMilli(row.occurredAt).atZone(zone).toLocalDate().toString()
                if (exp.date != null && date != exp.date) {
                    failures += "${case.id}: amount=${exp.amount_cents} date=$date expect=${exp.date}"
                }
            }
        }

        assertThat(covered).isAtLeast(10)
        assertThat(failures).isEmpty()
    }

    private fun repoRoot(): File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").exists() }
        ?: File(System.getProperty("user.dir"))
}
