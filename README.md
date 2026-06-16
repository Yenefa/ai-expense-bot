# 💰 记账助手 — AI 对话式记账 App

> ChatGPT 风格 · 本地 SQLite · 大模型辅助 · Android 原生

## ✨ 功能

### 🤖 智能记账
- **自然语言输入**：直接说"午饭35块，咖啡18"，AI 自动解析金额、分类、时间
- **通用对话**：闲聊、提问都支持，AI 只提取有用的支出信息
- **模板模式**：关闭 AI 后，选分类标签 + 输入金额，秒记账

### 🛠 智能操作（v1.2 新增）
- **删 / 改 / 查** 三种新能力：用自然语言操作已有支出
  - 「删掉昨天那笔咖啡」→ 弹卡片让你勾选 + 确认才真删
  - 「午饭那笔搞错了，应该是 40」→ 卡片预览修改 → 确认
  - 「我本月吃饭花了多少」→ 直接给聚合数字
- **确认卡片护栏**：所有破坏性操作必须用户在卡片上点确认才执行，LLM 提议 ≠ 自动执行
- **设计文档**：[docs/ark/llm-actions.md](docs/ark/llm-actions.md)

### 🗑️ 最近删除（v1.3 新增）
- 误删一笔？不慌 — 所有删除（滑动删除 / LLM 删除卡片）都走软删
- 设置菜单 → 最近删除：保留 30 天内可一键恢复
- 倒计时显示"剩 N 天"，3 天内变红警示
- 一键彻底清空支持，过期自动清理

### 📊 支出分析
- **周/月/年** 三周期切换
- **柱形图**：每日/每月/每月金额
- **折线图**：消费次数趋势
- **扇形图**：按分类占比
- **🧠 智核分析**：一键让 AI 分析你的消费习惯，给出 3-5 条洞察

### 🎯 历史明细
- 按日期分组，展开看当天每一笔
- 分类、金额、时间一目了然

### 🎨 精细交互
- **Interactive Dock**：底部浮动栏 + 弹簧弹跳动画
- **液态玻璃按钮**：🧠 开关靠光感区分开/关（不是机械开关）
- **系统返回键**：任何子页面返回都回到对话页，不会退出 App
- **ChatGPT 极简白底风格**：无边框，靠投影做层次

---

## 🏗 技术栈

| 层 | 技术 |
|---|---|
| 平台 | Android 8.0+ (minSdk 26) |
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 数据库 | Room (SQLite) |
| 偏好 | DataStore Preferences |
| 图表 | Vico 1.13 |
| 网络 | OkHttp 4.12 |
| 序列化 | kotlinx.serialization |
| LLM | OpenAI 兼容 API（支持 DeepSeek / 豆包 / 任何 OpenAI 兼容服务） |

---

## 📱 安装

### 方式一：直接安装 APK
从 [Releases](../../releases) 下载最新 `app-debug.apk`，传到手机安装。

> 安装前需要在系统设置里给文件管理器开「安装未知应用」权限。

### 方式二：自己编译
```bash
git clone https://github.com/sca331613-commits/ai-expense-bot.git
cd ai-expense-bot
# 在 local.properties 里配好你的 Android SDK 路径
./gradlew :app:assembleDebug
# APK 在 app/build/outputs/apk/debug/app-debug.apk
```

---

## ⚙️ 配置 LLM

1. 打开 App → 点底部 **⚙ 设置**
2. 进入 **🧠 LLM 设置**
3. 填写：
   - **Base URL**：API 地址（默认 OpenAI，DeepSeek 用户填 `https://api.deepseek.com/v1`）
   - **API Key**：你的 API 密钥
   - **Model**：模型名（如 `deepseek-chat`、`gpt-4o-mini`）
4. 点「保存」，回到对话页点亮 🧠，开始随便聊天

---

## 📂 项目结构

```
app/src/main/java/com/expense/tracker/
├── MainActivity.kt          # 入口 + 路由 + BackHandler
├── AppContainer.kt          # 手动 DI 容器
├── ui/
│   ├── theme/               # 配色 / 字体 / 投影
│   ├── chat/                # 对话页 (ChatScreen, ChatViewModel, ActionCard, MessageBubble...)
│   ├── dock/                # InteractiveDock + 弹簧动画
│   ├── template/            # 模板模式 (CategoryChip, AmountInput)
│   ├── liquidglass/         # 🧠 液态玻璃按钮
│   ├── analytics/           # 支出分析 + 智核分析
│   ├── history/             # 历史明细
│   └── settings/            # 设置菜单 + LLM 配置
├── data/
│   ├── action/              # PendingAction + Resolver（v1.2 LLM 操作流）
│   ├── db/                  # Room 实体 + DAO + Database
│   ├── prefs/               # DataStore 用户偏好
│   ├── repo/                # ExpenseRepository / ChatRepository
│   └── model/               # Category / Period
├── llm/                     # OpenAI 兼容客户端 + Prompt + 解析器 + ActionDto
└── util/                    # TimeRanges 时间区间计算

docs/
├── ark/                     # 架构与设计决策的"地标文档"（v1.2 起）
└── superpowers/             # 早期 plan / spec
```

---

## 🚧 版本

- **v1.0** (2026-06-15) — 首次发布：对话式记账、支出分析、Interactive Dock、LLM 接入
- **v1.1** (2026-06-15) — 通用 LLM 对话、智核分析、历史明细、设置菜单、Dock 精简、系统返回键
- **v1.2** (2026-06-15) — **LLM 智能操作**：删 / 改 / 查支出 + 确认卡片护栏；测试覆盖 31 → 50 ([docs/ark/llm-actions.md](docs/ark/llm-actions.md))
- **v1.3** (2026-06-16) — **最近删除 + 软删除架构**：所有删除路径走软删，30 天内可恢复；Room schema v1 → v2 平滑 Migration
- **v1.3.2** (2026-06-16) — 分类新增**投资 / 学习**；智核分析补充分类笔数；单条彻底删除增加二次确认

---

## 📄 许可

MIT License
