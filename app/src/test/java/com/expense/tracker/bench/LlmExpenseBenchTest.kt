package com.expense.tracker.bench

import com.expense.tracker.llm.LlmClient
import com.expense.tracker.llm.LlmPrompt
import com.expense.tracker.llm.LlmResponseParser
import com.expense.tracker.llm.ParsedExpense
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.OffsetDateTime
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
 * ExpenseBench LLM 评测（按需运行，不进常规 CI）：
 *
 * ```
 * EXPENSEBENCH_API_KEY=sk-xxx \
 * EXPENSEBENCH_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1 \
 * EXPENSEBENCH_MODEL=qwen3.7-plus \
 * EXPENSEBENCH_CONCURRENCY=4 \
 * ./gradlew :app:testDebugUnitTest --tests "com.expense.tracker.bench.LlmExpenseBenchTest"
 * ```
 * 报告写 `docs/expensebench-llm-report-<model>.md`；EXPENSEBENCH_LIMIT 可先跑前 N 条冒烟。
 * 可复现协议见 docs/expensebench.md（temperature=0 固定 + 数据集/prompt 哈希入报告）。
 */
class LlmExpenseBenchTest {

    private val apiKey = System.getenv("EXPENSEBENCH_API_KEY").orEmpty()
    private val baseUrl = System.getenv("EXPENSEBENCH_BASE_URL") ?: "https://api.deepseek.com"
    private val model = System.getenv("EXPENSEBENCH_MODEL") ?: "deepseek-chat"
    private val limit = System.getenv("EXPENSEBENCH_LIMIT")?.toIntOrNull() ?: Int.MAX_VALUE
    private val concurrency = (System.getenv("EXPENSEBENCH_CONCURRENCY")?.toIntOrNull() ?: 4).coerceIn(1, 8)

    @Test
    fun runBenchmark() {
        Assume.assumeTrue(
            "跳过：未设置 EXPENSEBENCH_API_KEY。ExpenseBench LLM 评测按需运行，设置环境变量后执行（见类注释）。",
            apiKey.isNotBlank(),
        )

        val cases = ExpenseBenchDataset.load().take(limit)
        val client = LlmClient()
        val systemPrompt = LlmPrompt.systemPrompt(ExpenseBenchDataset.benchNowMillis)
        val promptSha256 = ExpenseBenchDataset.sha256Hex(systemPrompt.toByteArray(Charsets.UTF_8))
        val ranAt = OffsetDateTime.now()
        val predictions = ConcurrentHashMap<String, List<ParsedExpense>>()
        val failures = AtomicInteger(0)
        val done = AtomicInteger(0)

        runBlocking {
            val permits = Semaphore(concurrency)
            cases.map { case ->
                async(Dispatchers.IO) {
                    permits.withPermit {
                        val raw = runCatching {
                            client.chatJson(
                                baseUrl = baseUrl,
                                apiKey = apiKey,
                                model = model,
                                userText = case.text,
                                systemPrompt = systemPrompt,
                                temperature = 0.0,
                                enableThinking = false,
                            )
                        }.getOrElse { error ->
                            failures.incrementAndGet()
                            System.err.println("[${case.id}] 请求失败：${error.message}")
                            ""
                        }
                        predictions[case.id] = if (raw.isBlank()) {
                            emptyList()
                        } else {
                            runCatching { LlmResponseParser.parse(raw).expenses }
                                .getOrElse { error ->
                                    System.err.println("[${case.id}] 解析失败：${error.message}")
                                    emptyList()
                                }
                        }
                        val finished = done.incrementAndGet()
                        if (finished % 20 == 0 || finished == cases.size) println("进度：$finished/${cases.size}")
                    }
                }
            }.awaitAll()
        }

        val report = ExpenseBenchEvaluator.evaluate(cases, predictions)
        val markdown = report.toMarkdown(
            model = "$model @ ${baseUrl.removePrefix("https://").removePrefix("http://")}",
            source = "LLM 实测（失败请求 ${failures.get()} 次，全部按 0 笔计）",
            metadata = listOf(
                "temperature = 0.0（固定）",
                "enable_thinking = false（结构化短任务，长思维链只拖延迟不提精度）",
                "concurrency = $concurrency",
                "dataset_sha256 = ${ExpenseBenchDataset.datasetSha256()}",
                "prompt_sha256 = $promptSha256",
                "ran_at = $ranAt",
                "复现：同哈希数据集 + 同 prompt + temperature=0 + 同模型快照 ⇒ 结果应一致（±供应商非确定性）",
            ),
        )
        val safeModel = model.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val outFile = File(repoRoot(), "docs/expensebench-llm-report-$safeModel.md")
        outFile.parentFile?.mkdirs()
        outFile.writeText(markdown)
        println(markdown)

        // 不对准确率做硬断言：报告即交付物，阈值随模型换代调整。
        assertThat(predictions).hasSize(cases.size)
    }

    private fun repoRoot(): File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").exists() }
        ?: File(System.getProperty("user.dir"))
}
