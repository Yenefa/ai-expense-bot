# 智能记账 App 实现计划 — Part 3: LLM 接入 + 支出分析

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **前置：** 必须先完成 Part 1 + Part 2

**Goal:** 接入 OpenAI 兼容 LLM 完成自然语言记账；侧边栏「支出分析」展示周/月/年三种图表

**Architecture:** OkHttp 直连 LLM；Vico 库画图；时间区间用 `java.time` 计算

**Tech Stack:** OkHttp 4.12, kotlinx.serialization, Vico 1.13, java.time

---

## 任务总览（Part 3: 7 个任务）

| # | 任务 |
|---|---|
| 16 | LLM 系统 prompt + 响应模型 |
| 17 | LlmResponseParser（含中文相对时间解析） |
| 18 | LlmClient（OkHttp 调用） |
| 19 | 接入 ChatViewModel |
| 20 | TimeRanges + AnalyticsViewModel |
| 21 | 三种图表 Composable |
| 22 | AnalyticsScreen + 抽屉接入 |

---

### Task 16: LLM Prompt + DTO

**Files:**
- Create: `app/src/main/java/com/expense/tracker/llm/LlmPrompt.kt`
- Create: `app/src/main/java/com/expense/tracker/llm/LlmDto.kt`

- [ ] **Step 1: 写 `LlmPrompt.kt`**

```kotlin
package com.expense.tracker.llm

import com.expense.tracker.data.model.Category

object LlmPrompt {
    fun systemPrompt(): String = buildString {
        appendLine("你是一个记账助手。用户会用自然语言描述支出，你要解析并以 JSON 返回。")
        appendLine("规则：")
        appendLine("1. 只输出 JSON，不要任何额外文字、解释、代码块标记。")
        appendLine("2. JSON 格式：")
        appendLine("""{"expenses":[{"amount": <number>, "category": <string>, "note": <string>, "occurred_at": <string|null>}]}""")
        appendLine("3. category 必须是以下之一：${Category.ALL.joinToString { it.id }}")
        appendLine("4. amount 单位是元（人民币），保留 2 位小数。")
        appendLine("5. occurred_at：")
        appendLine("   - 如果用户文本里没明说时间，必须返回 null（由客户端填当前时间）")
        appendLine("   - 如果用户说「昨天/前天/上周三/3 天前/2025-06-10」等，请输出 ISO 本地日期时间字符串如 \"2025-06-12T12:00:00\"")
        appendLine("6. 用户描述多笔支出时，每笔一个对象。")
        appendLine("7. 无法解析任何支出 → 返回 {\"expenses\":[]}")
        appendLine("分类对照：")
        Category.ALL.forEach { appendLine("  ${it.id} → ${it.emoji} ${it.displayName}") }
    }
}
```

- [ ] **Step 2: 写 `LlmDto.kt`**

```kotlin
package com.expense.tracker.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// === Chat completion 请求 ===
@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMsg>,
    val temperature: Double = 0.0,
    @SerialName("response_format") val responseFormat: ResponseFormat? = ResponseFormat("json_object"),
)
@Serializable data class ChatMsg(val role: String, val content: String)
@Serializable data class ResponseFormat(val type: String)

// === Chat completion 响应（仅取需要的字段） ===
@Serializable
data class ChatCompletionResponse(val choices: List<Choice>)
@Serializable data class Choice(val message: ChatMsg)

// === LLM 业务返回 ===
@Serializable
data class LlmExpensesPayload(val expenses: List<LlmExpenseItem> = emptyList())

@Serializable
data class LlmExpenseItem(
    val amount: Double,
    val category: String,
    val note: String = "",
    @SerialName("occurred_at") val occurredAt: String? = null,
)
```

- [ ] **Step 3: 编译**

Run: `gradle :app:compileDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/expense/tracker/llm/LlmPrompt.kt \
        app/src/main/java/com/expense/tracker/llm/LlmDto.kt
git commit -m "feat(llm): system prompt + dto for openai-compatible chat completion"
```

---

### Task 17: LlmResponseParser

**Files:**
- Create: `app/src/main/java/com/expense/tracker/llm/LlmResponseParser.kt`
- Create: `app/src/test/java/com/expense/tracker/llm/LlmResponseParserTest.kt`

- [ ] **Step 1: 写测试 `LlmResponseParserTest.kt`**

```kotlin
package com.expense.tracker.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class LlmResponseParserTest {

    @Test fun parseSinglePayload() {
        val raw = """{"expenses":[{"amount":35.0,"category":"food","note":"午饭","occurred_at":null}]}"""
        val items = LlmResponseParser.parse(raw)
        assertThat(items).hasSize(1)
        assertThat(items[0].categoryId).isEqualTo("food")
        assertThat(items[0].amount).isEqualTo(35.0)
        assertThat(items[0].occurredAtMillis).isNull()
    }

    @Test fun parseMultipleExpenses() {
        val raw = """{"expenses":[
          {"amount":35,"category":"food","note":"午饭","occurred_at":null},
          {"amount":18.5,"category":"drink","note":"咖啡","occurred_at":null}
        ]}"""
        val items = LlmResponseParser.parse(raw)
        assertThat(items).hasSize(2)
        assertThat(items.map { it.categoryId }).containsExactly("food", "drink").inOrder()
    }

    @Test fun parseIso8601OccurredAt() {
        val raw = """{"expenses":[{"amount":10,"category":"food","note":"","occurred_at":"2025-06-12T12:00:00"}]}"""
        val items = LlmResponseParser.parse(raw)
        val expected = LocalDateTime.of(2025, 6, 12, 12, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertThat(items[0].occurredAtMillis).isEqualTo(expected)
    }

    @Test fun unknownCategoryFallsBackToOther() {
        val raw = """{"expenses":[{"amount":1,"category":"weird","note":"","occurred_at":null}]}"""
        val items = LlmResponseParser.parse(raw)
        assertThat(items[0].categoryId).isEqualTo("other")
    }

    @Test fun nonPositiveAmountFiltered() {
        val raw = """{"expenses":[{"amount":0,"category":"food","note":"","occurred_at":null},
          {"amount":-5,"category":"food","note":"","occurred_at":null}]}"""
        val items = LlmResponseParser.parse(raw)
        assertThat(items).isEmpty()
    }

    @Test fun garbageThrowsParseException() {
        try {
            LlmResponseParser.parse("not json")
            assert(false) { "应当抛 LlmParseException" }
        } catch (_: LlmParseException) { /* ok */ }
    }

    @Test fun emptyExpensesArrayThrowsNoExpense() {
        try {
            LlmResponseParser.parse("""{"expenses":[]}""")
            assert(false)
        } catch (e: LlmParseException) {
            assertThat(e.message).contains("未识别到支出")
        }
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `gradle :app:testDebugUnitTest --tests "com.expense.tracker.llm.LlmResponseParserTest"`
Expected: FAIL — `Unresolved reference: LlmResponseParser`

- [ ] **Step 3: 写 `LlmResponseParser.kt`**

```kotlin
package com.expense.tracker.llm

import com.expense.tracker.data.model.Category
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.ZoneId

class LlmParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

data class ParsedExpense(
    val amount: Double,
    val categoryId: String,
    val note: String,
    val occurredAtMillis: Long?,
)

object LlmResponseParser {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    fun parse(raw: String): List<ParsedExpense> {
        val payload = try {
            json.decodeFromString(LlmExpensesPayload.serializer(), raw.trim())
        } catch (e: Exception) {
            throw LlmParseException("无法解析 LLM 输出为 JSON：${e.message}", e)
        }
        if (payload.expenses.isEmpty()) {
            throw LlmParseException("未识别到支出，请尝试更具体的描述或关闭 AI 用模板记账")
        }
        val parsed = payload.expenses
            .filter { it.amount > 0.0 }
            .map { item ->
                ParsedExpense(
                    amount = "%.2f".format(item.amount).toDouble(),
                    categoryId = Category.byId(item.category)?.id ?: "other",
                    note = item.note,
                    occurredAtMillis = item.occurredAt?.let(::parseOccurredAt),
                )
            }
        if (parsed.isEmpty()) {
            throw LlmParseException("LLM 返回的金额无效")
        }
        return parsed
    }

    private fun parseOccurredAt(iso: String): Long? = runCatching {
        LocalDateTime.parse(iso)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `gradle :app:testDebugUnitTest --tests "com.expense.tracker.llm.LlmResponseParserTest"`
Expected: 7 PASS

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/expense/tracker/llm/LlmResponseParser.kt \
        app/src/test/java/com/expense/tracker/llm/LlmResponseParserTest.kt
git commit -m "feat(llm): json response parser with iso datetime + category fallback"
```

---

### Task 18: LlmClient（OkHttp 调用）

**Files:**
- Create: `app/src/main/java/com/expense/tracker/llm/LlmClient.kt`

- [ ] **Step 1: 写 `LlmClient.kt`**

```kotlin
package com.expense.tracker.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class LlmClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** 调用 OpenAI 兼容 chat completions，返回 assistant message content（应为 JSON 字符串）。 */
    suspend fun chatJson(
        baseUrl: String,
        apiKey: String,
        model: String,
        userText: String,
    ): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "API Key 为空，请到设置中填写" }

        val req = ChatCompletionRequest(
            model = model,
            messages = listOf(
                ChatMsg(role = "system", content = LlmPrompt.systemPrompt()),
                ChatMsg(role = "user",   content = userText),
            ),
        )
        val body = json.encodeToString(ChatCompletionRequest.serializer(), req)
            .toRequestBody("application/json".toMediaType())

        val url = baseUrl.trimEnd('/') + "/chat/completions"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("LLM HTTP ${resp.code}: ${text.take(200)}")
            val parsed = runCatching {
                json.decodeFromString(ChatCompletionResponse.serializer(), text)
            }.getOrElse { error("LLM 响应解析失败：${it.message}") }
            parsed.choices.firstOrNull()?.message?.content
                ?: error("LLM 响应为空")
        }
    }

    companion object {
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
```

- [ ] **Step 2: 编译**

Run: `gradle :app:compileDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/expense/tracker/llm/LlmClient.kt
git commit -m "feat(llm): okhttp client for openai-compatible chat completions"
```

---

### Task 19: 接入 ChatViewModel + 设置页（最简）

**Files:**
- Modify: `app/src/main/java/com/expense/tracker/AppContainer.kt`
- Modify: `app/src/main/java/com/expense/tracker/MainActivity.kt`
- Create: `app/src/main/java/com/expense/tracker/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: 把 LlmClient 注入 AppContainer**

替换 `AppContainer.kt` 整个文件：

```kotlin
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
            // 写库
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
```

- [ ] **Step 2: 写 `SettingsScreen.kt`（最简表单）**

```kotlin
package com.expense.tracker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.prefs.UserPrefs
import com.expense.tracker.ui.theme.AppColors
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(prefs: UserPrefs, onClose: () -> Unit) {
    val snap by prefs.snapshot.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    var baseUrl by remember(snap) { mutableStateOf(snap?.baseUrl ?: "") }
    var apiKey by remember(snap) { mutableStateOf(snap?.apiKey ?: "") }
    var model by remember(snap) { mutableStateOf(snap?.model ?: "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Bg)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("LLM 设置", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
        Spacer(Modifier.height(4.dp))

        OutlinedTextField(
            value = baseUrl, onValueChange = { baseUrl = it },
            label = { Text("Base URL") },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = apiKey, onValueChange = { apiKey = it },
            label = { Text("API Key") },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = model, onValueChange = { model = it },
            label = { Text("Model") },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                scope.launch {
                    prefs.setApiConfig(baseUrl.trim(), apiKey.trim(), model.trim())
                    onClose()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.TextPrimary,
                contentColor = Color.White,
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("保存") }
    }
}
```

- [ ] **Step 3: 替换 `MainActivity.kt`，加入设置页路由 + LLM 接入**

```kotlin
package com.expense.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.expense.tracker.ui.chat.ChatScreen
import com.expense.tracker.ui.chat.ChatViewModel
import com.expense.tracker.ui.settings.SettingsScreen
import com.expense.tracker.ui.theme.AppTheme

class MainActivity : ComponentActivity() {

    private val container by lazy { (application as ExpenseApp).container }

    private val chatVm: ChatViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(
                expenseRepo = container.expenseRepo,
                chatRepo = container.chatRepo,
                userPrefs = container.userPrefs,
                llmHandler = container.llmHandler,
            ) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Chat) }
                when (val s = screen) {
                    Screen.Chat -> ChatScreen(
                        vm = chatVm,
                        onOpenAnalytics = { screen = Screen.Settings }, // 第一版菜单暂只到 settings
                    )
                    Screen.Settings -> SettingsScreen(
                        prefs = container.userPrefs,
                        onClose = { screen = Screen.Chat },
                    )
                }
            }
        }
    }

    private sealed interface Screen {
        data object Chat : Screen
        data object Settings : Screen
    }
}
```

> Task 22 会把分析页路由加进来，覆盖这里。

- [ ] **Step 4: 编译并安装**

Run: `gradle :app:assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 手动测试 LLM 流程**

1. 装 APK，开 🧠
2. 顶部 ☰ → 进设置 → 填 Base URL、API Key、Model → 保存
3. 输入「午饭35，咖啡18」→ ↑
4. 应看到 AI 回复「已为你记录 2 笔支出 ...」
5. 重启 App，记录还在；切回 OFF 再切回 ON 看看图标动画

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/expense/tracker/AppContainer.kt \
        app/src/main/java/com/expense/tracker/MainActivity.kt \
        app/src/main/java/com/expense/tracker/ui/settings/
git commit -m "feat(llm): integrate llm flow into chat viewmodel + minimal settings screen"
```

---

### Task 20: TimeRanges + AnalyticsViewModel

**Files:**
- Create: `app/src/main/java/com/expense/tracker/data/model/Period.kt`
- Create: `app/src/main/java/com/expense/tracker/util/TimeRanges.kt`
- Create: `app/src/main/java/com/expense/tracker/ui/analytics/AnalyticsViewModel.kt`
- Create: `app/src/test/java/com/expense/tracker/util/TimeRangesTest.kt`

- [ ] **Step 1: 写 `Period.kt`**

```kotlin
package com.expense.tracker.data.model

enum class Period { Week, Month, Year }
```

- [ ] **Step 2: 写测试 `TimeRangesTest.kt`**

```kotlin
package com.expense.tracker.util

import com.expense.tracker.data.model.Period
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TimeRangesTest {
    private val zone = ZoneId.systemDefault()
    private fun millis(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    @Test fun weekRangeForWednesday() {
        // 2025-06-11 (Wed)
        val now = millis(2025, 6, 11, 14, 30)
        val (from, to) = TimeRanges.rangeOf(Period.Week, now, zone)
        assertThat(from).isEqualTo(millis(2025, 6, 9, 0, 0))   // Mon
        assertThat(to).isEqualTo(millis(2025, 6, 16, 0, 0))    // next Mon (exclusive)
    }

    @Test fun monthRange() {
        val now = millis(2025, 6, 15, 10, 0)
        val (from, to) = TimeRanges.rangeOf(Period.Month, now, zone)
        assertThat(from).isEqualTo(millis(2025, 6, 1, 0, 0))
        assertThat(to).isEqualTo(millis(2025, 7, 1, 0, 0))
    }

    @Test fun yearRange() {
        val now = millis(2025, 6, 15, 10, 0)
        val (from, to) = TimeRanges.rangeOf(Period.Year, now, zone)
        assertThat(from).isEqualTo(millis(2025, 1, 1, 0, 0))
        assertThat(to).isEqualTo(millis(2026, 1, 1, 0, 0))
    }

    @Test fun bucketIndexForWeek() {
        val (from, _) = TimeRanges.rangeOf(Period.Week, millis(2025, 6, 11), zone)
        // 2025-06-09 周一 → idx 0；2025-06-12 周四 → idx 3
        assertThat(TimeRanges.bucketIndex(Period.Week, from, millis(2025, 6, 9, 12), zone)).isEqualTo(0)
        assertThat(TimeRanges.bucketIndex(Period.Week, from, millis(2025, 6, 12, 12), zone)).isEqualTo(3)
    }

    @Test fun bucketLabelsForMonth() {
        val labels = TimeRanges.bucketLabels(Period.Month, millis(2025, 2, 15), zone)
        assertThat(labels).hasSize(28) // 2025-02 has 28 days
        assertThat(labels.first()).isEqualTo("1")
        assertThat(labels.last()).isEqualTo("28")
    }

    @Test fun bucketLabelsForYear() {
        val labels = TimeRanges.bucketLabels(Period.Year, millis(2025, 1, 1), zone)
        assertThat(labels).containsExactly(
            "1月","2月","3月","4月","5月","6月","7月","8月","9月","10月","11月","12月"
        ).inOrder()
    }
}
```

- [ ] **Step 3: 跑测试确认失败**

Run: `gradle :app:testDebugUnitTest --tests "com.expense.tracker.util.TimeRangesTest"`
Expected: FAIL

- [ ] **Step 4: 写 `TimeRanges.kt`**

```kotlin
package com.expense.tracker.util

import com.expense.tracker.data.model.Period
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object TimeRanges {

    /** 返回 [from, to) 半开区间的 epoch millis，含本周/本月/本年。 */
    fun rangeOf(period: Period, nowMillis: Long, zone: ZoneId): Pair<Long, Long> {
        val now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
        val (from, to) = when (period) {
            Period.Week -> {
                val mon = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                mon.atStartOfDay() to mon.plusWeeks(1).atStartOfDay()
            }
            Period.Month -> {
                val first = now.toLocalDate().withDayOfMonth(1)
                first.atStartOfDay() to first.plusMonths(1).atStartOfDay()
            }
            Period.Year -> {
                val first = LocalDate.of(now.year, 1, 1)
                first.atStartOfDay() to first.plusYears(1).atStartOfDay()
            }
        }
        return from.atZone(zone).toInstant().toEpochMilli() to
               to.atZone(zone).toInstant().toEpochMilli()
    }

    /** 给定 fromMillis（区间起点）+ 一条记录的 occurredAt → 落入的桶 index。 */
    fun bucketIndex(period: Period, fromMillis: Long, occurredAt: Long, zone: ZoneId): Int {
        val from = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(fromMillis), zone).toLocalDate()
        val at = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(occurredAt), zone).toLocalDate()
        return when (period) {
            Period.Week  -> java.time.temporal.ChronoUnit.DAYS.between(from, at).toInt()
            Period.Month -> at.dayOfMonth - from.dayOfMonth
            Period.Year  -> at.monthValue - from.monthValue
        }
    }

    /** 用于图表 X 轴的标签。 */
    fun bucketLabels(period: Period, anchorMillis: Long, zone: ZoneId): List<String> {
        val (from, _) = rangeOf(period, anchorMillis, zone)
        val fromDate = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(from), zone).toLocalDate()
        return when (period) {
            Period.Week -> listOf("周一","周二","周三","周四","周五","周六","周日")
            Period.Month -> {
                val days = fromDate.lengthOfMonth()
                (1..days).map { it.toString() }
            }
            Period.Year -> (1..12).map { "${it}月" }
        }
    }

    fun bucketCount(period: Period, anchorMillis: Long, zone: ZoneId): Int =
        bucketLabels(period, anchorMillis, zone).size
}
```

- [ ] **Step 5: 跑测试确认通过**

Run: `gradle :app:testDebugUnitTest --tests "com.expense.tracker.util.TimeRangesTest"`
Expected: 6 PASS

- [ ] **Step 6: 写 `AnalyticsViewModel.kt`**

```kotlin
package com.expense.tracker.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.model.Category
import com.expense.tracker.data.model.Period
import com.expense.tracker.data.repo.ExpenseRepository
import com.expense.tracker.util.TimeRanges
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId

data class AnalyticsUiState(
    val period: Period = Period.Month,
    val barAmounts: List<Double> = emptyList(),    // 每桶总金额
    val lineCounts: List<Int> = emptyList(),       // 每桶笔数
    val pieByCategory: Map<String, Double> = emptyMap(),  // categoryId -> 金额
    val xLabels: List<String> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModel(
    private val repo: ExpenseRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val internal = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = internal.asStateFlow()

    private val periodTrigger = MutableStateFlow(Period.Month)

    init {
        viewModelScope.launch {
            periodTrigger
                .flatMapLatest { p ->
                    val (from, to) = TimeRanges.rangeOf(p, nowProvider(), zone)
                    val tagged = kotlinx.coroutines.flow.combine(
                        repo.observeInRange(from, to),
                        kotlinx.coroutines.flow.flowOf(Triple(p, from, to)),
                    ) { list, t -> t to list }
                    tagged
                }
                .collect { (triple, list) ->
                    val (p, from, _) = triple
                    internal.update { aggregate(p, from, list) }
                }
        }
    }

    fun selectPeriod(p: Period) {
        periodTrigger.value = p
    }

    private fun aggregate(p: Period, fromMillis: Long, list: List<ExpenseEntity>): AnalyticsUiState {
        val labels = TimeRanges.bucketLabels(p, fromMillis, zone)
        val n = labels.size
        val amounts = DoubleArray(n)
        val counts = IntArray(n)
        val byCat = HashMap<String, Double>()

        list.forEach { e ->
            val idx = TimeRanges.bucketIndex(p, fromMillis, e.occurredAt, zone)
            if (idx in 0 until n) {
                amounts[idx] += e.amount
                counts[idx] += 1
            }
            byCat[e.categoryId] = (byCat[e.categoryId] ?: 0.0) + e.amount
        }

        return AnalyticsUiState(
            period = p,
            barAmounts = amounts.toList(),
            lineCounts = counts.toList(),
            pieByCategory = byCat,
            xLabels = labels,
        )
    }
}
```

- [ ] **Step 7: 编译**

Run: `gradle :app:compileDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: 提交**

```bash
git add app/src/main/java/com/expense/tracker/data/model/Period.kt \
        app/src/main/java/com/expense/tracker/util/TimeRanges.kt \
        app/src/main/java/com/expense/tracker/ui/analytics/AnalyticsViewModel.kt \
        app/src/test/java/com/expense/tracker/util/TimeRangesTest.kt
git commit -m "feat(analytics): time-range bucketing + analytics viewmodel"
```

---

### Task 21: 三种图表 Composable

**Files:**
- Create: `app/src/main/java/com/expense/tracker/ui/analytics/BarChartView.kt`
- Create: `app/src/main/java/com/expense/tracker/ui/analytics/LineChartView.kt`
- Create: `app/src/main/java/com/expense/tracker/ui/analytics/PieChartView.kt`

- [ ] **Step 1: 写 `BarChartView.kt`（Vico 柱形图）**

```kotlin
package com.expense.tracker.ui.analytics

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf

@Composable
fun BarChartView(amounts: List<Double>, xLabels: List<String>, modifier: Modifier = Modifier) {
    val producer = remember { ChartEntryModelProducer() }
    LaunchedEffect(amounts) {
        producer.setEntries(amounts.mapIndexed { i, v -> entryOf(i.toFloat(), v.toFloat()) })
    }
    val xFormatter = AxisValueFormatter<AxisPosition.Horizontal.Bottom> { x, _ ->
        xLabels.getOrNull(x.toInt()) ?: ""
    }
    Chart(
        modifier = modifier.fillMaxWidth().height(220.dp),
        chart = columnChart(),
        chartModelProducer = producer,
        startAxis = rememberStartAxis(),
        bottomAxis = rememberBottomAxis(
            valueFormatter = xFormatter,
            itemPlacer = AxisItemPlacer.Horizontal.default(spacing = if (xLabels.size > 12) 5 else 1),
        ),
    )
}
```

- [ ] **Step 2: 写 `LineChartView.kt`（Vico 折线图）**

```kotlin
package com.expense.tracker.ui.analytics

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf

@Composable
fun LineChartView(counts: List<Int>, xLabels: List<String>, modifier: Modifier = Modifier) {
    val producer = remember { ChartEntryModelProducer() }
    LaunchedEffect(counts) {
        producer.setEntries(counts.mapIndexed { i, v -> entryOf(i.toFloat(), v.toFloat()) })
    }
    val xFormatter = AxisValueFormatter<AxisPosition.Horizontal.Bottom> { x, _ ->
        xLabels.getOrNull(x.toInt()) ?: ""
    }
    Chart(
        modifier = modifier.fillMaxWidth().height(220.dp),
        chart = lineChart(),
        chartModelProducer = producer,
        startAxis = rememberStartAxis(),
        bottomAxis = rememberBottomAxis(
            valueFormatter = xFormatter,
            itemPlacer = AxisItemPlacer.Horizontal.default(spacing = if (xLabels.size > 12) 5 else 1),
        ),
    )
}
```

- [ ] **Step 3: 写 `PieChartView.kt`（Vico 不直接支持饼图，自己用 Canvas 画）**

```kotlin
package com.expense.tracker.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.model.Category

private val PieColors = listOf(
    Color(0xFF0D0D0D), Color(0xFF505050), Color(0xFF808080),
    Color(0xFFB0B0B0), Color(0xFF0A84FF), Color(0xFF6E6E6E),
    Color(0xFF3D3D3D), Color(0xFFD4D4D4),
)

@Composable
fun PieChartView(byCategory: Map<String, Double>, modifier: Modifier = Modifier) {
    val total = byCategory.values.sum().takeIf { it > 0.0 } ?: 1.0
    val ordered = Category.ALL.mapIndexedNotNull { i, c ->
        val v = byCategory[c.id]
        if (v == null || v <= 0.0) null else Triple(c, v, PieColors[i % PieColors.size])
    }

    Row(
        modifier = modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(160.dp)) {
            var start = -90f
            ordered.forEach { (_, v, color) ->
                val sweep = (v / total * 360.0).toFloat()
                drawArc(
                    color = color,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height),
                )
                start += sweep
            }
        }
        Spacer(Modifier.width(20.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ordered.forEach { (c, v, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(10.dp)) { drawRect(color) }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${c.emoji} ${c.displayName}  ¥${"%.2f".format(v)}  (${"%.1f".format(v / total * 100)}%)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 4: 编译**

Run: `gradle :app:compileDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/expense/tracker/ui/analytics/BarChartView.kt \
        app/src/main/java/com/expense/tracker/ui/analytics/LineChartView.kt \
        app/src/main/java/com/expense/tracker/ui/analytics/PieChartView.kt
git commit -m "feat(analytics): bar / line (Vico) + custom canvas pie chart"
```

---

### Task 22: AnalyticsScreen + 抽屉接入

**Files:**
- Create: `app/src/main/java/com/expense/tracker/ui/analytics/PeriodSelector.kt`
- Create: `app/src/main/java/com/expense/tracker/ui/analytics/AnalyticsScreen.kt`
- Modify: `app/src/main/java/com/expense/tracker/MainActivity.kt`

- [ ] **Step 1: 写 `PeriodSelector.kt`**

```kotlin
package com.expense.tracker.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.model.Period
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.softShadow

@Composable
fun PeriodSelector(current: Period, onSelect: (Period) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(8.dp)
            .softShadow(elevation = 2.dp, cornerRadius = 22.dp, spotAlpha = 0.06f)
            .clip(RoundedCornerShape(22.dp))
            .background(AppColors.ChipFill)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Period.values().forEach { p ->
            val on = p == current
            val label = when (p) { Period.Week -> "周"; Period.Month -> "月"; Period.Year -> "年" }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (on) AppColors.Bg else Color.Transparent)
                    .pointerInput(p) { detectTapGestures(onTap = { onSelect(p) }) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text(
                    label,
                    color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
```

- [ ] **Step 2: 写 `AnalyticsScreen.kt`**

```kotlin
package com.expense.tracker.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.iconBtnShadow

@Composable
fun AnalyticsScreen(vm: AnalyticsViewModel, onBack: () -> Unit) {
    val state by vm.uiState.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Bg)
            .verticalScroll(rememberScrollState()),
    ) {
        // 顶部
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .iconBtnShadow()
                    .clip(CircleShape)
                    .background(AppColors.Bg)
                    .pointerInput(onBack) { detectTapGestures(onTap = { onBack() }) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = AppColors.TextPrimary) }
            Spacer(Modifier.size(12.dp))
            Text("支出分析", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
        }

        PeriodSelector(
            current = state.period,
            onSelect = vm::selectPeriod,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(20.dp))
        SectionTitle("📊 总支出（柱形图）")
        BarChartView(
            amounts = state.barAmounts,
            xLabels = state.xLabels,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(28.dp))
        SectionTitle("📈 消费次数（折线图）")
        LineChartView(
            counts = state.lineCounts,
            xLabels = state.xLabels,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(28.dp))
        SectionTitle("🥧 分类占比（扇形图）")
        PieChartView(
            byCategory = state.pieByCategory,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = AppColors.TextPrimary,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
```

- [ ] **Step 3: 修改 `MainActivity.kt`，加入 Analytics 路由 + 抽屉**

```kotlin
package com.expense.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.expense.tracker.ui.analytics.AnalyticsScreen
import com.expense.tracker.ui.analytics.AnalyticsViewModel
import com.expense.tracker.ui.chat.ChatScreen
import com.expense.tracker.ui.chat.ChatViewModel
import com.expense.tracker.ui.settings.SettingsScreen
import com.expense.tracker.ui.theme.AppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container by lazy { (application as ExpenseApp).container }

    private val chatVm: ChatViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(
                expenseRepo = container.expenseRepo,
                chatRepo = container.chatRepo,
                userPrefs = container.userPrefs,
                llmHandler = container.llmHandler,
            ) as T
        }
    }

    private val analyticsVm: AnalyticsViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AnalyticsViewModel(repo = container.expenseRepo) as T
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Chat) }
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            Text("菜单", modifier = Modifier.padding(16.dp))
                            NavigationDrawerItem(
                                label = { Text("支出分析") },
                                selected = screen == Screen.Analytics,
                                onClick = {
                                    screen = Screen.Analytics
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                            NavigationDrawerItem(
                                label = { Text("LLM 设置") },
                                selected = screen == Screen.Settings,
                                onClick = {
                                    screen = Screen.Settings
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }
                    },
                ) {
                    when (screen) {
                        Screen.Chat -> ChatScreen(
                            vm = chatVm,
                            onOpenAnalytics = { scope.launch { drawerState.open() } },
                        )
                        Screen.Analytics -> AnalyticsScreen(
                            vm = analyticsVm,
                            onBack = { screen = Screen.Chat },
                        )
                        Screen.Settings -> SettingsScreen(
                            prefs = container.userPrefs,
                            onClose = { screen = Screen.Chat },
                        )
                    }
                }
            }
        }
    }

    private sealed interface Screen {
        data object Chat : Screen
        data object Analytics : Screen
        data object Settings : Screen
    }
}
```

- [ ] **Step 4: 编译并安装**

Run: `gradle :app:installDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL + 装机

- [ ] **Step 5: 完整端到端验收**

1. 关闭 LLM 模式下，添加 5-10 笔不同分类的支出
2. 点 ☰ → 抽屉打开 → 「支出分析」
3. 周/月/年 切换 tab，三种图表都能正确切换
4. 柱形图 X 轴在「月」时是 1-30 号，「年」时是 1月-12月，「周」时是周一-周日
5. 折线图按笔数计算
6. 扇形图按分类占比，下方显示图例 + 金额 + 百分比
7. 返回箭头能回到对话页

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/expense/tracker/ui/analytics/PeriodSelector.kt \
        app/src/main/java/com/expense/tracker/ui/analytics/AnalyticsScreen.kt \
        app/src/main/java/com/expense/tracker/MainActivity.kt
git commit -m "feat(analytics): drawer-routed analytics screen with week/month/year"
```

---

## Part 3 完成验收

- ✅ 开 🧠 → 自然语言记账 → 写库 → AI 回复
- ✅ 设置页能配 base URL / API Key / Model
- ✅ ☰ 抽屉打开「支出分析」/「LLM 设置」
- ✅ 三种图表按周/月/年切换
- ✅ 单元测试 + UI 手动验证全过

---

## v1 总验收清单

| 来源 spec | 任务覆盖 | 状态 |
|---|---|---|
| SQLite 本地存储 | Task 5 | ✅ Room |
| 自动获取记账时间，文中指定时间为准 | Task 17 | ✅ LlmResponseParser |
| 主页面 ChatGPT 对话风格 | Task 10/11/13/15 | ✅ |
| 侧边栏 = 支出分析 | Task 22 | ✅ |
| 周/月/年 三种周期 | Task 20/22 | ✅ |
| 柱形图 Y=金额 X=日期 | Task 21 BarChartView | ✅ |
| 折线图 Y=次数 X=周期 | Task 21 LineChartView | ✅ |
| 扇形图 按消费类型 | Task 21 PieChartView | ✅ |
| Interactive Dock + spring 动画 | Task 15 | ✅ |
| 🧠 切换记忆永不自动改 | Task 6/9 | ✅ |
| ChatGPT 美术风格（白底无边框靠投影） | Task 3 配色 + 全部 UI | ✅ |
| 第一版 Android | 整个 plan | ✅ |
