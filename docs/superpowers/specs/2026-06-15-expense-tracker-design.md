# 智能记账 App — 设计规约

**日期**: 2026-06-15
**状态**: 已批准 (用户确认 "先这样吧，制作app")
**版本**: v1 (第一版 MVP)

---

## 一、产品定位

一款「对话式」记账 App。主页面是 ChatGPT 风格的聊天界面，用户通过两种方式记账：
- 🧠 **大模型模式**：开启 AI 后自然语言输入（"午饭35块"），由 LLM 解析金额、分类、时间
- 📋 **模板模式**：关闭 AI 后通过分类按钮 + 金额输入框快速记账

仅支持「支出」记录，不含收入（第一版）。

## 二、技术栈

| 层 | 技术 |
|---|---|
| 平台 | Android (minSdk 26 / Android 8.0+) |
| 语言 | Kotlin |
| UI 框架 | Jetpack Compose + Material 3 |
| 数据库 | SQLite via Room |
| 异步 | Kotlin Coroutines + Flow |
| 依赖注入 | 手动构造（YAGNI，不引入 Hilt） |
| 图表 | Vico (Compose 原生图表库) |
| LLM | OpenAI 兼容 API（用户配置 base_url + api_key + model） |
| 测试 | JUnit4 + Truth + Compose UI Test |

## 三、核心数据模型

### 3.1 Expense（支出记录）

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | Long (PK, autoGenerate) | 主键 |
| `amount` | Double | 金额（元，保留 2 位小数） |
| `categoryId` | String | 关联 Category.id |
| `note` | String | 用户原始输入或备注 |
| `occurredAt` | Long (epoch millis) | 发生时间 |
| `createdAt` | Long (epoch millis) | 写入时间 |

### 3.2 Category（分类，固化在代码中，第一版）

固定 8 个：`food` 🍜餐饮、`transport` 🚗交通、`shopping` 🛒购物、`drink` ☕饮品、`entertainment` 🎮娱乐、`housing` 🏠住房、`medical` 💊医疗、`other` 📦其他

### 3.3 ChatMessage（对话历史）

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | Long (PK) | 主键 |
| `role` | String | "user" / "assistant" |
| `content` | String | 消息文本 |
| `relatedExpenseId` | Long? | 若是 AI 记账成功的回复，关联 Expense.id |
| `createdAt` | Long | 时间戳 |

### 3.4 UserPrefs（DataStore，键值对）

- `llm_enabled: Boolean`（默认 false）
- `llm_base_url: String`（默认 "https://api.openai.com/v1"）
- `llm_api_key: String`
- `llm_model: String`（默认 "gpt-4o-mini"）

## 四、UI 架构

### 4.1 整体布局

```
┌──────────────────────────────────┐
│  ☰        记账助手        ✏ ⋮    │  ← 顶部栏（白底+白圆按钮+投影）
├──────────────────────────────────┤
│                                   │
│             聊天区                 │
│  - 用户消息：浅灰胶囊（右对齐）      │
│  - AI 回复：纯文字（左对齐，无气泡） │
│                                   │
├──────────────────────────────────┤
│  [+] 输入框 [🧠] [蓝色发送 ↑]       │  ← 输入区（白胶囊+投影）
├──────────────────────────────────┤
│  [+]    [📊]    [📅]    [⚙]       │  ← 浮动 Dock（白胶囊+投影+弹簧）
└──────────────────────────────────┘
```

### 4.2 配色（严格遵循）

```
背景      #FFFFFF
文字主    #0D0D0D
文字次    #5D5D5D
文字弱    #8E8E8E
浅灰填充  #F4F4F4
蓝色强调  #0A84FF
（无边框，全部用 box-shadow）
```

### 4.3 投影规范

```
顶部小按钮: 0 1px 3px rgba(0,0,0,0.12)
输入框胶囊: 0 1px 4px rgba(0,0,0,0.10) + 0 0 0 1px rgba(0,0,0,0.04)
Dock:      0 2px 10px rgba(0,0,0,0.10) + 0 0 0 1px rgba(0,0,0,0.04)
卡片:      0 4px 20px rgba(0,0,0,0.12)
```

### 4.4 关键组件

#### 4.4.1 LiquidGlassButton（🧠 切换按钮）
- ON: `background = rgba(0,0,0,0.06)`, 白色椭圆高光，shadow 微发光
- OFF: `background = rgba(0,0,0,0.02)`, 图标 alpha 0.22, 无高光
- 点击切换状态，状态写入 DataStore，永远不自动变更

#### 4.4.2 InteractiveDock
- 浮在底部，4 个图标：➕（新建对话）、📊（分析）、📅（历史）、⚙（设置）
- 点击图标时该图标 spring 弹跳（scale 1.0 → 1.2 → 1.0），其它平滑过渡
- 长按显示标签 tooltip
- 用 `spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)` 实现弹簧

#### 4.4.3 TemplateCard（关闭 LLM 时）
- 分类标签（横向 wrap）：选中 = 黑底白字胶囊，未选 = 白底投影胶囊
- 大字号金额输入：`¥` 32sp + 数字 36sp，无边框，下方黑色圆形 ↑ 发送按钮

### 4.5 侧边栏

- 仅 1 项：「支出分析」
- 通过顶部 ☰ 按钮抽屉式展开

## 五、记账流程

### 5.1 开启 LLM 模式
1. 用户输入自由文本：`"午饭35，咖啡18"`
2. 调用 LLM API（OpenAI 兼容），传入系统 prompt 要求返回 JSON：
   ```json
   {
     "expenses": [
       {"amount": 35.0, "category": "food", "note": "午饭", "occurred_at": null},
       {"amount": 18.0, "category": "drink", "note": "咖啡", "occurred_at": null}
     ]
   }
   ```
3. `occurred_at` 为 ISO 字符串时按其解析；为 null 时用当前时间
4. 解析失败 → 显示错误提示「未能识别，请尝试关闭 AI 用模板记账」
5. 解析成功 → 写入 DB → AI 消息显示「已为你记录两笔支出: 🍜 餐饮 ¥35 · ☕ 饮品 ¥18」

### 5.2 关闭 LLM 模式
1. 用户点击分类标签（高亮选中态）
2. 在金额输入框输入数字 → 点击 ↑ 按钮
3. 直接写入 DB，分类用所选标签，时间用 `System.currentTimeMillis()`，note 留空
4. AI 消息显示「✅ 已记录 · 餐饮 ¥35.00」

### 5.3 时间解析规则
- LLM 模式：用户文本中含「昨天」「3天前」「上周三」「2025-06-10」等 → 解析为时间戳
- LLM 输出 `occurred_at` 字段，App 用 `java.time.LocalDateTime` 解析
- 解析失败或为 null → 用当前时间
- 模板模式：永远用当前时间

## 六、支出分析页面（侧边栏）

### 6.1 入口
顶部 ☰ → 抽屉打开 → 「支出分析」 → 进入分析页（全屏覆盖）

### 6.2 顶部周期切换器
3 个 tab：「周」「月」「年」（默认月）

### 6.3 三张图表（统一标准）

| 图 | X 轴 | Y 轴 | 数据 |
|---|---|---|---|
| 柱形图 | 日期（周：周一-周日；月：1-31号；年：1-12月） | 金额 | 各周期段总支出 |
| 折线图 | 周期（同上） | 消费次数 | 各段笔数 |
| 扇形图 | — | — | 当前周期内按 category 占比 |

### 6.4 周期定义
- 周：本周一 0:00 → 本周日 23:59:59
- 月：本月 1 号 → 本月最后一天
- 年：当年 1 月 1 日 → 12 月 31 日

## 七、文件结构

```
app/src/main/java/com/expense/tracker/
├─ MainActivity.kt                  # 单 Activity 入口
├─ ExpenseApp.kt                    # Application + 手动 DI 容器
├─ ui/
│  ├─ theme/
│  │  ├─ Color.kt                   # ChatGPT 配色
│  │  ├─ Theme.kt                   # MaterialTheme 包装
│  │  ├─ Type.kt                    # 字号系统
│  │  └─ Shadow.kt                  # 投影 Modifier
│  ├─ chat/
│  │  ├─ ChatScreen.kt              # 主对话页面
│  │  ├─ ChatViewModel.kt
│  │  ├─ MessageBubble.kt           # 用户/AI 消息
│  │  └─ InputBar.kt                # 输入栏（含 🧠 + 发送）
│  ├─ dock/
│  │  ├─ InteractiveDock.kt         # 底部浮动 Dock
│  │  └─ DockItem.kt                # 弹簧动画图标
│  ├─ liquidglass/
│  │  └─ LiquidGlassButton.kt       # 液态玻璃 ON/OFF
│  ├─ template/
│  │  ├─ TemplateCard.kt            # 关闭 LLM 时的分类+金额输入
│  │  ├─ CategoryChip.kt
│  │  └─ AmountInput.kt
│  └─ analytics/
│     ├─ AnalyticsScreen.kt         # 支出分析全屏页
│     ├─ AnalyticsViewModel.kt
│     ├─ PeriodSelector.kt          # 周/月/年 tab
│     ├─ BarChartView.kt            # 柱形图
│     ├─ LineChartView.kt           # 折线图
│     └─ PieChartView.kt            # 扇形图
├─ data/
│  ├─ db/
│  │  ├─ AppDatabase.kt             # Room database
│  │  ├─ ExpenseEntity.kt
│  │  ├─ ExpenseDao.kt
│  │  ├─ ChatMessageEntity.kt
│  │  └─ ChatMessageDao.kt
│  ├─ prefs/
│  │  └─ UserPrefs.kt               # DataStore 封装
│  ├─ repo/
│  │  ├─ ExpenseRepository.kt
│  │  └─ ChatRepository.kt
│  └─ model/
│     ├─ Category.kt                # 固定 8 分类 sealed
│     └─ Period.kt                  # Week/Month/Year
├─ llm/
│  ├─ LlmClient.kt                  # OpenAI 兼容 HTTP 调用
│  ├─ LlmPrompt.kt                  # 系统 prompt 模板
│  └─ LlmResponseParser.kt          # JSON → ParsedExpenses
└─ util/
   ├─ TimeRanges.kt                 # 周/月/年区间计算
   └─ DateParser.kt                 # 中文相对时间解析
```

## 八、不做的事（YAGNI）

- ❌ 收入记录（仅支出）
- ❌ 多账户/多用户
- ❌ 云同步、登录、注册
- ❌ 预算、提醒、通知
- ❌ 自定义分类（v1 锁定 8 个）
- ❌ 数据导出/导入
- ❌ iOS / 鸿蒙
- ❌ Hilt / Koin（手动 DI）
- ❌ MVI / Redux（直接 ViewModel + Flow）

## 九、验收标准（v1）

1. 能开/关 LLM 模式，关闭后重启 App 仍是关闭态
2. 关闭模式：选分类 + 输金额 → 点 ↑ → 数据库新增 1 条 + AI 消息出现
3. 开启模式：输入「午饭35」→ 调用 LLM → 数据库新增 1 条 + AI 消息出现
4. 抽屉「支出分析」可打开，三种图表能在「周/月/年」切换
5. Interactive Dock 4 个图标，点击有 spring 弹跳动画
6. UI 视觉与脑暴最终稿一致：白底、无边框、靠投影做层次、ChatGPT 美术风格
