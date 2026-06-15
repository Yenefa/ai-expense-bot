# LLM 操作支出 — 协议与确认流

**版本**：v1.2 · **日期**：2026-06-15 · **状态**：已实现，待真机验证

---

## 1. 目标

让 LLM 不只能「新增」支出，还能「删除 / 修改 / 查询」 — 但所有破坏性操作必须由用户在 UI 卡片上点确认才执行。

---

## 2. 核心设计取舍

### 取舍 1：LLM 输出"匹配条件"，不输出 expense.id

```
用户："删掉昨天那笔咖啡"
       ↓
LLM 输出：{op:"delete", match:{category:"drink", date_from:"...", date_to:"..."}}
       ↓
App 在本地 SQLite 用 SQL 查候选
```

**为什么不让 LLM 直接出 id**：用户从来不会说"删掉 id=17"，只会说"昨天那笔咖啡"。让 LLM 出 id 等于让它编造数据。

**代价**：要处理 0/1/多 三种候选数的 UI 分支。但这本来就是「确认 UI」的题中之义。

### 取舍 2：所有破坏性操作走"提议 → 卡片预览 → 用户确认"

LLM 是不可靠的。卡片确认 UI 是唯一的护栏，不是冗余 — 是 SLA。

可选的"30 秒撤销"方案被否决，原因：用户没看到提示就过期了，等于真删；卡片是"必须看见 + 必须主动按"。

### 取舍 3：actions 与 expenses 在同一个 JSON 顶层

```json
{
  "reply": "...",
  "expenses": [...],   ← 老协议，立刻 INSERT，无需确认
  "actions":  [...]    ← 新协议，走确认流
}
```

**为什么不合并**：保持 `expenses` 的语义不变（无破坏性 + 立即执行）。混入 `actions` 后老代码全部要改。这条产出 v1.1 的零回归。

### 取舍 4：Action match 字段全 nullable，patch 字段也全 nullable

```kotlin
data class ActionMatch(
    val category: String? = null,
    val amount: Double? = null,
    val dateFrom: String? = null,
    val dateTo: String? = null,
    val noteContains: String? = null,
)
```

SQL 直接 `(:x IS NULL OR col = :x)` 翻译，让"未指定"语义贯穿到底。

**特别注意**：未知 category 在 `expenses` 里 fallback 到 `"other"`（合理兜底，必须新增到某个分类），但在 `actions.match` 里返回 `null`（= SQL 不限制）。原因：LLM 万一把"咖啡"识别成未知 category，"删未知 → fallback other"会变成"删 other 类的所有记录"，破坏性极强。两边语义不对称是有意的。

---

## 3. 架构分层

```
┌─────────────────────────────────────────────────────────┐
│ UI 层                                                    │
│  - ChatScreen                                           │
│  - MessageList (LazyColumn 末尾渲染 ActionCard)         │
│  - ActionCard.kt (Delete/Update/QueryResult/Empty 4 种) │
└────────────────────────────┬────────────────────────────┘
                             │ pendingActions / confirm/dismiss
┌────────────────────────────▼────────────────────────────┐
│ ChatViewModel                                           │
│  - submitFreeText() 调 llmHandler                       │
│  - confirmDelete / confirmUpdate / dismissAction        │
└────────────────────────────┬────────────────────────────┘
                             │ LlmResult.Ok(reply, expenseId, pendingActions)
┌────────────────────────────▼────────────────────────────┐
│ AppContainer.llmHandler (闭包形式的 UseCase)            │
│  1. LlmClient.chatJson  → raw JSON                      │
│  2. LlmResponseParser.parse → expenses + actions        │
│  3. expenseRepo.add(...)  ← expenses 立刻写库            │
│  4. PendingActionResolver.resolve(action) ← 解析候选    │
└────┬──────────────────────────────────────┬────────────┘
     │                                       │
┌────▼─────────────────────────┐  ┌─────────▼──────────────┐
│ LlmResponseParser            │  │ PendingActionResolver  │
│  - 把 ISO 时间 → epoch ms    │  │  - 调 dao.findByMatch  │
│  - 校准 category 字段        │  │  - 0/1/多 → Empty/卡片 │
│  - takeIf 把无效条件 → null  │  │  - query 渲染聚合文本  │
└──────────────────────────────┘  └────────────────────────┘
                                              │
                                  ┌───────────▼──────────┐
                                  │ ExpenseDao           │
                                  │  - findByMatch (LIMIT 50)│
                                  │  - patchById (COALESCE)│
                                  └──────────────────────┘
```

---

## 4. 协议示例

### 4.1 删除

用户：「删掉昨天买的咖啡」

LLM 输出：
```json
{
  "reply": "好的，准备删除昨天那笔饮品支出，确认吗？",
  "expenses": [],
  "actions": [{
    "op": "delete",
    "match": {
      "category": "drink",
      "amount": null,
      "date_from": "2026-06-14T00:00:00",
      "date_to":   "2026-06-15T00:00:00",
      "note_contains": "咖啡"
    }
  }]
}
```

### 4.2 修改

用户：「午饭那笔搞错了，应该是 40 不是 35」

LLM 输出：
```json
{
  "reply": "好，把午饭从 35 改到 40。",
  "expenses": [],
  "actions": [{
    "op": "update",
    "match": {"category": "food", "amount": 35.0,
              "date_from": "2026-06-15T00:00:00", "date_to": "2026-06-16T00:00:00"},
    "patch": {"amount": 40.0, "category": null, "note": null}
  }]
}
```

### 4.3 查询

用户：「我本月吃饭花了多少」

LLM 输出：
```json
{
  "reply": "查一下本月餐饮总额。",
  "expenses": [],
  "actions": [{
    "op": "query",
    "match": {"category": "food",
              "date_from": "2026-06-01T00:00:00", "date_to": "2026-07-01T00:00:00"},
    "aggregate": "sum"
  }]
}
```

---

## 5. SQL 兜底

```sql
-- ExpenseDao.findByMatch 长这样（核心 5 行）：
SELECT * FROM expenses
WHERE (:category IS NULL OR categoryId = :category)
  AND (:amount   IS NULL OR ABS(amount - :amount) < 0.01)
  AND (:from     IS NULL OR occurredAt >= :from)
  AND (:to       IS NULL OR occurredAt <  :to)
  AND (:noteSub  IS NULL OR note LIKE '%' || :noteSub || '%')
ORDER BY occurredAt DESC LIMIT 50;
```

**`LIMIT 50` 防御**：万一 LLM 给空 match，全表 50 条上限，UI 也撑得住。

**`ABS(... - :amount) < 0.01`**：浮点直接 `=` 在 SQLite 上偶尔挂；epsilon 比较稳。

**`COALESCE` 模式 patch**：null 参数 = 不改该字段，由 SQL 守住，不依赖应用层 if 分支。

---

## 6. 候选数三态 UI

| 候选数 | 状态 | UI 行为 |
|---|---|---|
| 0  | `PendingAction.Empty` | 显示"🤔 没找到匹配的支出"，单按钮"知道了" |
| 1  | `PendingAction.Delete` / `Update` | **默认勾选**该笔，主按钮高亮 |
| ≥2 | `PendingAction.Delete` / `Update` | 用户**必须主动勾选**才能确认（防误操作） |

`query` 永远不进卡片选择流程，直接渲染聚合文本即可。

---

## 7. 测试矩阵

| 模块 | 测试文件 | 用例数 |
|---|---|---|
| 协议解析 | `LlmResponseParserTest` | 16（旧 8 + 新 8） |
| 候选解析 | `PendingActionResolverTest` | 6 |
| ViewModel 流转 | `ChatViewModelTest` | 8（旧 3 + 新 5） |

关键测试技巧：`runTest { ... advanceUntilIdle() }` — `viewModelScope.launch` 内的 `delay()` 不会被 `UnconfinedTestDispatcher` 自动跳过，必须显式让虚拟时钟前进，否则状态断言会撞上"还没写进去"的空 state。

---

## 8. 故意没做的事（防 scope creep）

- ❌ Function calling / 多轮 tool use — 跟当前需求无关，DeepSeek/豆包兼容性差
- ❌ Streaming SSE — 与新功能无关
- ❌ 通用 ActionExecutor / Command Pattern 抽象 — `confirmAction` 一个 when 分支足够
- ❌ "批量撤销 / 操作历史" — 用户没要
- ❌ 改 Room schema — 不需要
- ❌ EncryptedSharedPreferences — 是另一类风险，独立处理
- ❌ ChatGPT 风格的"AI 判官"二次校验 LLM — 卡片确认就是护栏，再加层是过度防御

---

## 9. 已知风险与未来工作

| 风险 | 缓解 | 何时处理 |
|---|---|---|
| LLM 给的 `match` 太宽（如只填 category）→ 候选 50 条堆满卡片 | 当前 LIMIT 50；UI 没分页 | 真机看到具体频率再决定 |
| 老协议兼容：用户安装了 v1.1 后升 v1.2，LLM 返回带 actions → 老解析器忽略 | 协议 forward-compat，无影响 | 已规避 |
| LLM 返回带 markdown ```json``` 围栏 → parse 抛异常 | 当前未 strip | 单独 PR 修 |
| API key 明文存 DataStore | EncryptedSharedPreferences | 单独 PR 修 |

---

## 10. 文件地图

```
app/src/main/java/com/expense/tracker/
├── llm/
│   ├── ActionDto.kt              ← LlmAction / ActionMatch / ActionPatch
│   ├── LlmDto.kt                 ← LlmExpensesPayload 加 actions 字段
│   ├── LlmResponseParser.kt      ← ParsedAction + parseAction()
│   └── LlmPrompt.kt              ← system prompt 追加【操作能力】节
├── data/
│   ├── action/                   ← 本特性唯一的新业务模块
│   │   ├── PendingAction.kt
│   │   └── PendingActionResolver.kt
│   ├── db/ExpenseDao.kt          ← findByMatch + patchById
│   └── repo/ExpenseRepository.kt ← 暴露上述方法
├── ui/chat/
│   ├── ActionCard.kt             ← 卡片 Composable
│   ├── ChatViewModel.kt          ← confirmDelete/confirmUpdate/dismissAction
│   ├── ChatUiState.kt            ← pendingActions 字段
│   └── MessageList.kt            ← LazyColumn 末尾渲染 pendingActions
└── AppContainer.kt               ← 注入 PendingActionResolver
```
