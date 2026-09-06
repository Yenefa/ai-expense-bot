package com.expense.tracker.bench

import com.expense.tracker.llm.LlmClient
import com.expense.tracker.llm.LlmPrompt
import com.expense.tracker.llm.LlmResponseParser
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Test

/**
 * ExpenseBench LLM 评测（按需运行，不进常规 CI）：
 *
 * 设置环境变量后执行：
 * ```
 * EXPENSEBENCH_API_KEY=sk-xxx \
 * EXPENSEBENCH_BASE_URL=https://api.deepseek.com \
 * EXPENSEBENCH_MODEL=deepseek-chat \
 * ./gradlew :app:testDebugUnitTest --tests "com.expense.tracker.bench.LlmExpenseBenchTest"
 * ```
 * 结果写入 docs/expensebench-llm-report.md。全量 120 条约需 5-10 分钟。
 * EXPENSEBENCH_LIMIT 可先跑前 N 条冒烟。
 */
class LlmExpenseBenchTest {

    private val apiKey = System.getenv("EXPENSEBENCH_API_KEY").orEmpty()
    private val baseUrl = System.getenv("EXPENSEBENCH_BASE_URL") ?: "https://api.deepseek.com"
    private val model = System.getenv("EXPENSEBENCH_MODEL") ?: "deepseek-chat"
    private val limit = System.getenv("EXPENSEBENCH_LIMIT")?.toIntOrNull() ?: Int.MAX_VALUE

    @Test
    fun runBenchmark() {
        Assume.assumeTrue(
            "跳过：未设置 EXPENSEBENCH_API_KEY。ExpenseBench LLM 评测按需运行，设置环境变量后执行（见类注释）。",
            apiKey.isNotBlank(),
        )

        val cases = ExpenseBenchDataset.load().take(limit)
        val client = LlmClient()
        val systemPrompt = LlmPrompt.systemPrompt(ExpenseBenchDataset.benchNowMillis)
        // 可复现协议：固定 temperature=0.0，报告记录数据集与 prompt 哈希（见 docs/expensebench.md）
        val promptSha256 = ExpenseBenchDataset.sha256Hex(systemPrompt.toByteArray(Charsets.UTF_8))
        val ranAt = java.time.OffsetDateTime.now()
        val predictions = LinkedHashMap<String, List<com.expense.tracker.llm.ParsedExpense>>()
        var failures = 0

        cases.forEachIndexed { index, case ->
            val raw = runCatching {
                runBlocking {
                    client.chatJson(
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        model = model,
                        userText = case.text,
                        systemPrompt = systemPrompt,
                        temperature = 0.0,
                    )
                }
            }.getOrElse { error ->
                failures++
                System.err.println("[${case.id}] 请求失败：${error.message}")
                ""
            }
            val parsed = if (raw.isBlank()) {
                emptyList()
            } else {
                runCatching { LlmResponseParser.parse(raw).expenses }
                    .getOrElse { error ->
                        System.err.println("[${case.id}] 解析失败：${error.message}")
                        emptyList()
                    }
            }
            predictions[case.id] = parsed
            if ((index + 1) % 10 == 0) println("进度：${index + 1}/${cases.size}")
        }

        val report = ExpenseBenchEvaluator.evaluate(cases, predictions)
        val markdown = report.toMarkdown(
            model = "$model @ ${baseUrl.removePrefix("https://").removePrefix("http://")}",
            source = "LLM 实测（失败请求 $failures 次，全部按 0 笔计）",
            metadata = listOf(
                "temperature = 0.0（固定）",
                "dataset_sha256 = ${ExpenseBenchDataset.datasetSha256()}",
                "prompt_sha256 = $promptSha256",
                "ran_at = $ranAt",
                "复现：同哈希数据集 + 同 prompt + temperature=0 + 同模型快照 ⇒ 结果应一致（±供应商非确定性）",
            ),
        )
        val outFile = File(repoRoot(), "docs/expensebench-llm-report.md")
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
