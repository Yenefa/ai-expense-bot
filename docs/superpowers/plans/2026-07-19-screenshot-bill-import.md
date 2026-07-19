# 截图录账（Screenshot Bill Import）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans (inline, 大统领授权一路执行). Steps use checkbox (`- [ ]`).

**Goal:** 用户从相册选支付宝/微信账单截图，App 本机 OCR + 现有文本 LLM 解析成多笔 expense，确认页编辑后批量入库。

**Architecture:** OCR（ML Kit，本机）出文本 -> `importFromBillText`（复用 `LlmResponseParser.parse`）-> `BillImportViewModel` 确认/编辑 -> 批量入库 + 写聊天消息。入口填 `InputBar` 的 `+` TODO。

**Tech Stack:** Kotlin + Compose + ML Kit text-recognition + ActivityResult PhotoPicker + 现有 OkHttp/kotlinx-serialization LLM 栈。

---

## File Structure

**Create:**
- `app/src/main/java/com/expense/tracker/llm/BillImport.kt` — `BillImportResult` + `importFromBillText()` 顶层函数
- `app/src/main/java/com/expense/tracker/ocr/OcrRecognizer.kt` — interface + `MlKitOcrRecognizer`
- `app/src/main/java/com/expense/tracker/ui/billimport/BillImportViewModel.kt` — 状态机 + 确认/编辑/入库
- `app/src/main/java/com/expense/tracker/ui/billimport/BillImportScreen.kt` — 选图/识别/确认 UI
- `app/src/test/java/com/expense/tracker/llm/BillImportTest.kt` — prompt + importFromBillText 单测
- `app/src/test/java/com/expense/tracker/ui/billimport/BillImportViewModelTest.kt` — VM 单测

**Modify:**
- `app/build.gradle.kts` — 加 ML Kit 依赖
- `app/src/main/java/com/expense/tracker/llm/LlmPrompt.kt` — 加 `billImportSystemPrompt()` + `billImportPrompt(ocrText)`
- `app/src/main/java/com/expense/tracker/AppContainer.kt` — 提供 `ocrRecognizer` + `billImportHandler`
- `app/src/main/java/com/expense/tracker/MainActivity.kt` — `SubScreen.BillImport`、PhotoPicker、`BillImportViewModel`、转场
- `app/src/main/java/com/expense/tracker/ui/chat/ChatScreen.kt` — 加 `onOpenBillImport`，`+` 接入
- `app/src/main/java/com/expense/tracker/ui/chat/InputBar.kt` — 无需改（`onPlusClick` 已存在）

---

## Task 1: 加 ML Kit 依赖

**Files:** Modify `app/build.gradle.kts`

- [ ] **Step 1: 加依赖**（在 dependencies 块 Networking 之后）
```kotlin
// OCR (截图录账)
implementation("com.google.mlkit:text-recognition:16.0.0")
```
- [ ] **Step 2: 验证编译**
Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL（首次会下 ML Kit 依赖）

---

## Task 2: LlmPrompt 账单解析 prompt（TDD）

**Files:** Modify `LlmPrompt.kt`；Test: `BillImportTest.kt`

- [ ] **Step 1: 写失败测试**（新建 `app/src/test/java/com/expense/tracker/llm/BillImportTest.kt`）
```kotlin
package com.expense.tracker.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BillImportTest {
    @Test fun `billImportSystemPrompt demands expenses schema and extraction, not actions`() {
        val sys = LlmPrompt.billImportSystemPrompt()
        assertThat(sys).contains("expenses")
        assertThat(sys).contains("交易")          // 提取每笔交易的指令
        assertThat(sys).doesNotContain("actions") // 账单导入不做删改
    }

    @Test fun `billImportPrompt embeds OCR text`() {
        val ocr = "2025-07-19 星巴克 ¥38.00\n2025-07-19 地铁 ¥4.00"
        val p = LlmPrompt.billImportPrompt(ocr)
        assertThat(p).contains("星巴克")
        assertThat(p).contains("地铁")
    }
}
```
- [ ] **Step 2: 跑测试看失败**
Run: `./gradlew :app:testDebugUnitTest --tests "com.expense.tracker.llm.BillImportTest" --console=plain`
Expected: FAIL（unresolved reference billImportSystemPrompt/billImportPrompt）
- [ ] **Step 3: 实现**（在 `LlmPrompt` object 末尾、`analyticsSystemPrompt()` 之后加）
```kotlin
    /** 账单截图导入专用 system prompt：从 OCR 文本提取每笔交易，输出 {reply,expenses}。不做删改。 */
    fun billImportSystemPrompt(): String = buildString {
        appendLine("你是账单解析助手。用户会给你一张支付账单截图的 OCR 文本，里面可能含多笔交易。")
        appendLine("你的唯一输出格式是 JSON：{\"reply\":\"...\",\"expenses\":[...]}。不要在 JSON 外加任何文字。")
        appendLine("把每一笔交易提取成一个 expense：{\"amount\":<数字>,\"category\":\"<分类>\",\"note\":\"<商户/对方名>\",\"occurred_at\":<ISO时间|null>}。")
        appendLine("分类只能从这些里选：${Category.ALL.joinToString { it.id }}")
        appendLine("规则：")
        appendLine("- 金额取实际支出金额（正数）；退款/不计入的行不要提取。")
        appendLine("- note 用商户名或交易对方；没明确分类的归 other。")
        appendLine("- occurred_at 按 OCR 文本里的日期时间算 ISO 本地时间；没有就 null。")
        appendLine("- 忽略标题、余额、合计、分页等非交易行。")
        appendLine("- reply 用一句话总结（≤30 字），如\"已识别 3 笔，合计 ¥XX\"。")
    }

    /** 账单导入 user prompt：把 OCR 文本喂给模型。 */
    fun billImportPrompt(ocrText: String): String = buildString {
        appendLine("以下是账单截图的 OCR 文本：")
        appendLine(ocrText)
        appendLine("请提取其中的所有交易。")
    }
```
- [ ] **Step 4: 跑测试看通过**
Run: 同 Step 2
Expected: PASS
- [ ] **Step 5: commit**（大统领 waived，跳过；记 checkpoint）

---

## Task 3: BillImportResult + importFromBillText（TDD）

**Files:** Create `BillImport.kt`；Test: 追加到 `BillImportTest.kt`

- [ ] **Step 1: 写失败测试**（追加到 `BillImportTest.kt`）
```kotlin
    private val prefs = com.expense.tracker.data.prefs.UserPrefsSnapshot(
        llmEnabled = true, baseUrl = "https://x", apiKey = "k", model = "m",
    )

    @Test fun `importFromBillText parses multiple expenses from clean JSON`() = kotlinx.coroutines.test.runTest {
        val fake = object : LlmClient() {
            override suspend fun chatJson(baseUrl: String, apiKey: String, model: String, userText: String, systemPrompt: String): String =
                """{"reply":"已识别2笔","expenses":[{"amount":38.0,"category":"drink","note":"星巴克","occurred_at":null},{"amount":4.0,"category":"transport","note":"地铁","occurred_at":null}]}"""
        }
        val r = importFromBillText(fake, prefs, "任意OCR文本")
        assertThat(r).isInstanceOf(BillImportResult.Ok::class.java)
        val ok = r as BillImportResult.Ok
        assertThat(ok.expenses).hasSize(2)
        assertThat(ok.expenses.map { it.categoryId }).containsExactly("drink", "transport")
    }

    @Test fun `importFromBillText extracts JSON from prose-wrapped response`() = runTest {
        val fake = object : LlmClient() {
            override suspend fun chatJson(baseUrl: String, apiKey: String, model: String, userText: String, systemPrompt: String): String =
                """好的，结果如下：{"reply":"已记","expenses":[{"amount":12,"category":"food","note":"午饭","occurred_at":null}]} 希望帮到你"""
        }
        val r = importFromBillText(fake, prefs, "x")
        val ok = r as BillImportResult.Ok
        assertThat(ok.expenses).hasSize(1)
        assertThat(ok.expenses[0].amount).isEqualTo(12.0)
    }

    @Test fun `importFromBillText returns Error when LLM throws`() = runTest {
        val fake = object : LlmClient() {
            override suspend fun chatJson(baseUrl: String, apiKey: String, model: String, userText: String, systemPrompt: String): String =
                error("LLM HTTP 500: boom")
        }
        val r = importFromBillText(fake, prefs, "x")
        assertThat(r).isInstanceOf(BillImportResult.Error::class.java)
        assertThat((r as BillImportResult.Error).message).contains("500")
    }
```
（文件顶部加 `import kotlinx.coroutines.test.runTest`）
- [ ] **Step 2: 跑测试看失败**
Run: 同上
Expected: FAIL（unresolved importFromBillText / BillImportResult）
- [ ] **Step 3: 实现**（新建 `app/src/main/java/com/expense/tracker/llm/BillImport.kt`）
```kotlin
package com.expense.tracker.llm

import com.expense.tracker.data.prefs.UserPrefsSnapshot

sealed interface BillImportResult {
    data class Ok(val expenses: List<ParsedExpense>, val reply: String) : BillImportResult
    data class Error(val message: String) : BillImportResult
}

/** 截图录账：把账单 OCR 文本喂给文本 LLM，解析出多笔 expense（不直接入库，交确认页）。 */
suspend fun importFromBillText(
    client: LlmClient,
    prefs: UserPrefsSnapshot,
    ocrText: String,
): BillImportResult = runCatching {
    val raw = client.chatJson(
        baseUrl = prefs.baseUrl,
        apiKey = prefs.apiKey,
        model = prefs.model,
        userText = LlmPrompt.billImportPrompt(ocrText),
        systemPrompt = LlmPrompt.billImportSystemPrompt(),
    )
    val parsed = LlmResponseParser.parse(raw)
    BillImportResult.Ok(parsed.expenses, parsed.reply)
}.getOrElse { BillImportResult.Error(it.message ?: "未知错误") }
```
- [ ] **Step 4: 跑测试看通过**
Run: 同上
Expected: PASS（3 个测试）
- [ ] **Step 5: checkpoint**

---

## Task 4: OcrRecognizer（interface + ML Kit 实现，不写 JVM 单测）

**Files:** Create `app/src/main/java/com/expense/tracker/ocr/OcrRecognizer.kt`

- [ ] **Step 1: 实现**
```kotlin
package com.expense.tracker.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

interface OcrRecognizer {
    suspend fun recognize(bitmap: Bitmap): String
}

class MlKitOcrRecognizer : OcrRecognizer {
    override suspend fun recognize(bitmap: Bitmap): String {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result -> cont.resume(result.text) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }
    }
}
```
- [ ] **Step 2: 验证编译**
Run: `./gradlew :app:compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL

---

## Task 5: BillImportViewModel（TDD）

**Files:** Create `BillImportViewModel.kt`；Test: `BillImportViewModelTest.kt`

- [ ] **Step 1: 写失败测试**（新建 `app/src/test/java/com/expense/tracker/ui/billimport/BillImportViewModelTest.kt`）
```kotlin
package com.expense.tracker.ui.billimport

import com.expense.tracker.data.db.ChatMessageEntity
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.expense.tracker.data.repo.ChatRepository
import com.expense.tracker.data.repo.ExpenseRepository
import com.expense.tracker.llm.BillImportResult
import com.expense.tracker.llm.ParsedExpense
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BillImportViewModelTest {
    @Before fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun teardown() = Dispatchers.resetMain()

    private val prefs = UserPrefsSnapshot(true, "u", "k", "m")

    private class FakeExpenseRepo : ExpenseRepository(FakeDao()) {
        val added = mutableListOf<Triple<Double, String, String>>()
        override suspend fun add(amount: Double, categoryId: String, note: String, occurredAt: Long): Long {
            added += Triple(amount, categoryId, note); return added.size.toLong()
        }
    }
    // 注：实际用 ExpenseRepository(dao) + FakeDao（仿 AnalyticsViewModelTest 的 FakeExpenseDaoForAnalytics）

    @Test fun `importFromText Ok shows Result with editable items`() = runTest {
        val vm = BillImportViewModel(
            importHandler = { _, _ -> BillImportResult.Ok(
                listOf(ParsedExpense(38.0, "drink", "星巴克", null), ParsedExpense(4.0, "transport", "地铁", null)),
                "已识别2笔") },
            expenseRepo = FakeExpenseRepo(),
            chatRepo = FakeChatRepo(),
            userPrefsSnapshot = prefs,
        )
        vm.importFromText("ocr")
        val s = vm.uiState.value
        assertThat(s.phase).isEqualTo(BillImportPhase.Result)
        assertThat(s.items).hasSize(2)
        assertThat(s.items.all { it.selected }).isTrue()
    }

    @Test fun `toggle and confirm inserts only selected and writes chat msg`() = runTest {
        val repo = FakeExpenseRepo()
        val chat = FakeChatRepo()
        val vm = BillImportViewModel(
            importHandler = { _, _ -> BillImportResult.Ok(
                listOf(ParsedExpense(38.0,"drink","星巴克",null), ParsedExpense(4.0,"transport","地铁",null)), "ok") },
            expenseRepo = repo, chatRepo = chat, userPrefsSnapshot = prefs,
        )
        vm.importFromText("ocr")
        vm.toggleSelected(1)              // 剔除第二笔
        vm.confirmImport()
        assertThat(repo.added).hasSize(1)
        assertThat(repo.added[0]).isEqualTo(Triple(38.0, "drink", "星巴克"))
        assertThat(vm.uiState.value.phase).isEqualTo(BillImportPhase.Done)
        assertThat(chat.appendedAssistant).hasSize(1)
    }

    @Test fun `importFromText Error shows Error phase`() = runTest {
        val vm = BillImportViewModel(
            importHandler = { _, _ -> BillImportResult.Error("LLM HTTP 500") },
            expenseRepo = FakeExpenseRepo(), chatRepo = FakeChatRepo(), userPrefsSnapshot = prefs,
        )
        vm.importFromText("ocr")
        assertThat(vm.uiState.value.phase).isEqualTo(BillImportPhase.Error)
        assertThat(vm.uiState.value.errorMessage).contains("500")
    }
}
```
（FakeDao / FakeChatRepo 仿现有测试里的 FakeExpenseDaoForAnalytics 与 ChatRepository 接口实现，存最小需要的方法。）
- [ ] **Step 2: 跑测试看失败**
Run: `./gradlew :app:testDebugUnitTest --tests "com.expense.tracker.ui.billimport.BillImportViewModelTest" --console=plain`
Expected: FAIL（unresolved BillImportViewModel 等）
- [ ] **Step 3: 实现**（新建 `BillImportViewModel.kt`）
```kotlin
package com.expense.tracker.ui.billimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.expense.tracker.data.repo.ChatRepository
import com.expense.tracker.data.repo.ExpenseRepository
import com.expense.tracker.llm.BillImportResult
import com.expense.tracker.llm.ParsedExpense
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BillImportPhase { Idle, Loading, Result, Error, Done }

data class EditableExpense(
    val amount: Double,
    val categoryId: String,
    val note: String,
    val occurredAtMillis: Long?,
    val selected: Boolean = true,
)

data class BillImportUiState(
    val phase: BillImportPhase = BillImportPhase.Idle,
    val items: List<EditableExpense> = emptyList(),
    val errorMessage: String? = null,
    val doneCount: Int = 0,
    val doneTotal: Double = 0.0,
)

class BillImportViewModel(
    private val importHandler: suspend (String, UserPrefsSnapshot) -> BillImportResult,
    private val expenseRepo: ExpenseRepository,
    private val chatRepo: ChatRepository,
    private val userPrefsSnapshot: UserPrefsSnapshot,
) : ViewModel() {

    private val internal = MutableStateFlow(BillImportUiState())
    val uiState: StateFlow<BillImportUiState> = internal.asStateFlow()

    fun importFromText(ocrText: String) {
        internal.update { it.copy(phase = BillImportPhase.Loading, items = emptyList(), errorMessage = null) }
        viewModelScope.launch {
            when (val r = importHandler(ocrText, userPrefsSnapshot)) {
                is BillImportResult.Ok -> {
                    val items = r.expenses.map { it.toEditable() }
                    internal.update { it.copy(phase = BillImportPhase.Result, items = items) }
                }
                is BillImportResult.Error -> internal.update {
                    it.copy(phase = BillImportPhase.Error, errorMessage = r.message)
                }
            }
        }
    }

    fun toggleSelected(index: Int) = internal.update { st ->
        st.copy(items = st.items.mapIndexed { i, e -> if (i == index) e.copy(selected = !e.selected) else e })
    }

    fun updateAmount(index: Int, amount: Double) = internal.update { st ->
        st.copy(items = st.items.mapIndexed { i, e -> if (i == index) e.copy(amount = amount) else e })
    }

    fun updateCategory(index: Int, categoryId: String) = internal.update { st ->
        st.copy(items = st.items.mapIndexed { i, e -> if (i == index) e.copy(categoryId = categoryId) else e })
    }

    fun updateNote(index: Int, note: String) = internal.update { st ->
        st.copy(items = st.items.mapIndexed { i, e -> if (i == index) e.copy(note = note) else e })
    }

    fun confirmImport() {
        val st = internal.value
        if (st.phase != BillImportPhase.Result) return
        val selected = st.items.filter { it.selected }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            selected.forEach { e ->
                expenseRepo.add(e.amount, e.categoryId, e.note, e.occurredAtMillis ?: now)
            }
            val total = selected.sumOf { it.amount }
            chatRepo.appendAssistant("📸 已从截图导入 ${selected.size} 笔，合计 ¥${"%.2f".format(total)}")
            internal.update { it.copy(phase = BillImportPhase.Done, doneCount = selected.size, doneTotal = total) }
        }
    }

    private fun ParsedExpense.toEditable() = EditableExpense(amount, categoryId, note, occurredAtMillis)
}
```
- [ ] **Step 4: 跑测试看通过**
Run: 同 Step 2
Expected: PASS（3 个测试）
- [ ] **Step 5: checkpoint**

---

## Task 6: BillImportScreen UI（不写 JVM 单测）

**Files:** Create `BillImportScreen.kt`

- [ ] **Step 1: 实现**（Composable：顶部返回+标题、Loading 转圈、Result 每笔卡片可勾选/改金额/分类/备注、底部"导入 N 笔·¥合计"、Error+重选、Done 触发 onDone）
  - 关键点：
    - `rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia())` 选图
    - 选图后用 `context.contentResolver` 解码 Bitmap（按 `opts.inSampleSize` 降采样防 OOM）
    - `rememberCoroutineScope().launch { val ocr = ocrRecognizer.recognize(bitmap); vm.importFromText(ocr) }`（OCR 异常 -> vm 显示 Error 或本地 toast）
    - `LaunchedEffect(state.phase)` 检测 `Done` -> `onDone()`
    - 分类切换复用 `Category.ALL` chip
- [ ] **Step 2: 验证编译**
Run: `./gradlew :app:compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL

---

## Task 7: 接线（MainActivity + ChatScreen）

**Files:** Modify `MainActivity.kt`, `ChatScreen.kt`

- [ ] **Step 1: AppContainer 提供 ocrRecognizer + billImportHandler**（Modify `AppContainer.kt`）
```kotlin
val ocrRecognizer: com.expense.tracker.ocr.OcrRecognizer by lazy {
    com.expense.tracker.ocr.MlKitOcrRecognizer()
}
val billImportHandler: suspend (String, UserPrefsSnapshot) -> com.expense.tracker.llm.BillImportResult =
    { ocrText, prefs -> com.expense.tracker.llm.importFromBillText(llmClient, prefs, ocrText) }
```
- [ ] **Step 2: ChatScreen 加 onOpenBillImport，+ 按钮接入**（Modify `ChatScreen.kt`）
  - 签名加 `onOpenBillImport: () -> Unit`
  - `InputBar(... onPlusClick = { if (state.llmEnabled) onOpenBillImport() else Toast.makeText(context, "请先开启 🧠 再用截图记账", Toast.LENGTH_SHORT).show() }, ...)`
- [ ] **Step 3: MainActivity 加 SubScreen.BillImport + VM + 转场**（Modify `MainActivity.kt`）
  - 加 `billImportVm` by viewModels（factory 注入 container.billImportHandler / expenseRepo / chatRepo / 当前 prefs snapshot）
  - `ChatScreen(... onOpenBillImport = { subScreen = SubScreen.BillImport })`
  - 加 `AnimatedVisibility(visible = subScreen == SubScreen.BillImport, ...同 InsightsScreen 转场)` 渲染 `BillImportScreen(vm = billImportVm, ocrRecognizer = container.ocrRecognizer, onBack = { subScreen = null }, onDone = { subScreen = null })`
  - `SubScreen` 加 `data object BillImport : SubScreen`
  - prefs snapshot 传入 VM：用 `container.userPrefs.snapshot` 的当前值（collectAsState 后传，或 VM 内部读 first）
- [ ] **Step 4: 验证编译**
Run: `./gradlew :app:compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL

---

## Task 8: 全量验证

- [ ] **Step 1: 跑全量单测**
Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:cleanTestDebugUnitTest :app:testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL，0 failures（含新增 BillImportTest 5 个 + BillImportViewModelTest 3 个，原 42 个无回归）
- [ ] **Step 2: 编译 debug APK 确认整体通过**
Run: `./gradlew :app:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL
- [ ] **Step 3: 报告 + 建议提交**（智核分析修复 + 截图录账 feature 分两个 commit）

---

## Self-Review

1. **Spec coverage**: 选型(A)、架构/数据流、UI、错误处理、测试策略、YAGNI 均有对应 Task。✓
2. **Placeholder scan**: Task 6/7 的 UI/接线是 Android 代码，给了关键 API 与结构，非占位符；实现时按现有 Screen 范式填。✓
3. **Type consistency**: `BillImportResult.Ok/Error`、`EditableExpense`、`BillImportPhase`、`importFromBillText(client,prefs,ocrText)`、`billImportHandler(ocrText,prefs)` 在各 Task 间一致。`ParsedExpense` 复用自 `LlmResponseParser`。✓

## 验证边界（老实说）
- JVM 单测覆盖：prompt、importFromBillText、BillImportViewModel 逻辑。
- 未覆盖（需真机）：ML Kit OCR 实际识别效果、PhotoPicker/Bitmap 解码、BillImportScreen UI 渲染、端到端选图->入库。需大统领装到机上验。
