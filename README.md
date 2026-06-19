# 💰 记账助手 — AI 对话式记账 App

> ChatGPT 风格 · 本地 SQLite · 大模型辅助 · Android 原生

[![Release](https://img.shields.io/github/v/release/sca331613-commits/ai-expense-bot)](../../releases)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin)](https://kotlinlang.org)

一款"像和 AI 聊天一样记账"的 Android 原生 App。所有数据**只存手机本地** SQLite，永不上云。

---

## ✨ 功能

### 🤖 智能记账（思考模式开 🧠）
- **自然语言输入**：「午饭35块，咖啡18」AI 自动拆分类、金额、时间
- **通用对话**：闲聊、问问题都支持，AI 只在文本里出现支出时才记账
- **流式打字动画**：思考中显示三点跳动，回复逐字出现 + 闪烁光标
- **时间感知**：每次请求都把当前时间注入 system prompt，"昨天/上周三/3 天前"等相对时间不会被瞎编

### ⚡ 模板模式（思考模式关）
- 关闭 🧠 后输入框被禁用（不能弹光标）
- 选分类标签 + 输入金额，1 秒记一笔
- **双击分类弹出居中气泡**：放大显示该分类，可填详细描述（如「菠萝百香果」），spring 弹性动画

### 📊 支出分析
- **周 / 月 / 年** 三周期切换
- **柱形图**：每天的总支出
- **折线图**：消费次数（Y 轴整数刻度，最小单位 1）
- **饼图**：分类占比，按业务语义着色（餐饮黄、饮品浅蓝、医疗红、住房绿…）
- **🧠 智核分析**：一键让 AI 分析消费习惯，给 3-5 条针对性洞察
- **💹 投资分类自动排除**：短线/股票/基金不计入消费图表（不会扭曲消费洞察），但历史明细保留

### 🎯 历史明细
- 按日期分组的卡片，展开看当天每一笔
- 大分类 emoji + 备注小字 + 时间 + 金额
- **向左滑动删除**：跟手 spring 动画，揭开/关闭灵敏度对称（轻轻一拨即可）

### 🎨 精细交互
- **页面切换横向滑动**：Chat ↔ 子页面 iOS 风 push/pop 动画（FastOutSlowInEasing）
- **Interactive Dock**：底部浮动栏 + 弹簧弹跳
- **液态玻璃 🧠 按钮**：靠光感区分开关
- **ChatGPT 白底极简**：无边框，纯投影做层次

---

## 🏗 技术栈

| 层 | 选型 |
|---|---|
| 平台 | Android 8.0+ (minSdk 26 / targetSdk 34) |
| 语言 | Kotlin 1.9 |
| UI | Jetpack Compose + Material 3 + Compose BOM 2024.02 |
| 数据库 | Room 2.6.1 (SQLite) |
| 偏好 | DataStore Preferences |
| 图表 | Vico 1.13 |
| 网络 | OkHttp 4.12 |
| 序列化 | kotlinx.serialization JSON |
| LLM | OpenAI 兼容 API（支持 DeepSeek / 豆包 / OpenAI / 任何 OpenAI 兼容服务） |
| DI | 手动 `AppContainer`（无 Hilt） |

---

## 📱 安装

### 方式一：直接装 APK（推荐）

1. 从 [Releases 页面](../../releases) 下载最新 `expense-tracker-vX.X.apk`
2. 传到手机安装
3. 安装前可能需要给浏览器/文件管理器开「安装未知应用」权限

> ⚠️ **重要：升级签名一致性**
> 本仓库的所有 APK 都是用同一个 debug keystore 签名的（指纹 SHA-256: `e5ac68a1...`）。
> 如果你之前装过**别的来源**的同包名 APK（比如其他工具/电脑构建的），由于签名不同会装不上，**必须先卸载旧版**。卸载会清除手机里的全部记账数据 — 想保数据请见下文「数据迁移」。

### 方式二：自己编译

```bash
git clone https://github.com/sca331613-commits/ai-expense-bot.git
cd ai-expense-bot
# 在 local.properties 里配好你的 Android SDK 路径，例如：
#   sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
./gradlew :app:assembleDebug
# 输出：app/build/outputs/apk/debug/app-debug.apk
```

JDK 要求：**JDK 17**（Android Studio 自带的 JBR 即可，`export JAVA_HOME=...AndroidStudio/jbr`）。

---

## ⚙️ 配置 LLM

1. 打开 App → 点底部 **⚙ 设置**
2. 进入 **🧠 LLM 设置**
3. 填写：
   - **Base URL**：API 地址
     - DeepSeek：`https://api.deepseek.com/v1`
     - OpenAI：`https://api.openai.com/v1`
     - 火山豆包：`https://ark.cn-beijing.volces.com/api/v3`
   - **API Key**：你的密钥
   - **Model**：模型名（`deepseek-chat` / `gpt-4o-mini` / `doubao-pro-32k` 等）
4. 保存返回，点亮 🧠，开始用自然语言记账

API Key **只存手机本地** DataStore，不上传任何第三方。

---

## 💾 数据迁移（保留历史记账升级到新版）

如果你的旧 APK 是用**不同的签名**打的（装不上新版），但又想保留手机里的记账数据，可以这样做：

```bash
# 1. 用 ADB 把旧 APP 的私有数据拉出来（debug 版可直接 run-as）
adb exec-out run-as com.expense.tracker tar c databases files > app-private.tar
tar -xf app-private.tar    # 得到 databases/expense.db 和 files/datastore/

# 2. 卸载旧版
adb uninstall com.expense.tracker

# 3. 装新版
adb install expense-tracker-vX.X.apk

# 4. 启动一次让 Room 建空表，再 force-stop
adb shell am start -n com.expense.tracker/.MainActivity
sleep 4
adb shell am force-stop com.expense.tracker

# 5. 用 sqlite3 把旧库的数据合并到新库（处理 schema 差异）
#    新版的 expenses 表无 deletedAt 列，把旧 deletedAt IS NULL 的导入即可

# 6. 把合并后的 DB 写回 App 私有目录
cat expense.db | adb exec-in run-as com.expense.tracker sh -c 'cat > databases/expense.db'
adb shell run-as com.expense.tracker chmod 660 databases/expense.db

# 7. （可选）恢复 LLM 配置
cat user_prefs.preferences_pb | adb exec-in run-as com.expense.tracker \
    sh -c 'mkdir -p files/datastore && cat > files/datastore/user_prefs.preferences_pb'
```

---

## 📂 项目结构

```
app/src/main/java/com/expense/tracker/
├── MainActivity.kt              # 入口 + 路由 + AnimatedContent 屏幕切换 + BackHandler
├── ExpenseApp.kt                # Application
├── AppContainer.kt              # 手动 DI（数据库、Repo、LLM Handler）
│
├── ui/
│   ├── theme/                   # 配色 / 字体 / Shadow modifier
│   ├── chat/                    # 对话页（ChatScreen, ViewModel, InputBar,
│   │                            #   MessageList, MessageBubble, TopBar）
│   ├── dock/                    # InteractiveDock + 弹簧动画
│   ├── template/                # 模板模式（CategoryChip, AmountInput,
│   │                            #   TemplateCard, CategoryBubbleDialog）
│   ├── liquidglass/             # 🧠 液态玻璃按钮
│   ├── analytics/               # 柱图 / 折线 / 饼图 / 智核分析
│   ├── history/                 # 历史明细 + 滑动删除
│   └── settings/                # 设置菜单 + LLM 配置
│
├── data/
│   ├── db/                      # Room Entity / DAO / AppDatabase
│   ├── prefs/                   # DataStore 用户偏好
│   ├── repo/                    # ExpenseRepository / ChatRepository
│   └── model/                   # Category / Period
│
├── llm/                         # OpenAI 兼容 HTTP 客户端 + Prompt 注入 + 解析器
└── util/                        # TimeRanges 时间区间计算
```

---

## 🗂 数据安全

| 数据 | 存放位置 | 升级时是否丢失 |
|---|---|---|
| 记账记录 | `/data/data/com.expense.tracker/databases/expense.db` | **同签名升级不丢**；签名不同/卸载会丢 |
| 聊天记录 | 同上（`chat_messages` 表） | 同上 |
| LLM 配置（API Key、模型） | `files/datastore/user_prefs.preferences_pb` | 同上 |
| 导出的 schema | `app/schemas/com.expense.tracker.data.db.AppDatabase/N.json`（git 跟踪） | - |

Schema 升级策略：`AppDatabase` 启用了 `fallbackToDestructiveMigration()` 作为兜底，但每个版本都导出 schema JSON 到 git，未来真要改 schema 必须写 `Migration` 而不是依赖兜底。

---

## 📅 版本历程

| 版本 | 内容 |
|---|---|
| v1.0 | 首次发布：对话式记账、支出分析、Interactive Dock、LLM 接入 |
| v1.1 | 通用 LLM 对话、智核分析、历史明细、设置菜单、系统返回键 |
| v1.2-v1.3 | 顶部栏 / App 图标多次重设计（最终：彩色笔记本+柱状图+金币图标） |
| v1.4 | 思考关时禁输入、双击分类弹气泡填备注、滑动删除明细、折线整数 Y 轴、饼图语义色 |
| v1.5-v1.6 | 修「点取消退出 App」的 NPE bug：缓存最后非空 category 防退出动画期间崩溃 |
| v1.7 | 关于页版本号动态读取、气泡内迷你切换条 + AnimatedContent crossfade |
| v1.8 | 主页分类切换动画（颜色 + scale spring）、删除框尺寸调优 |
| v1.9 | 删除框揭开/关闭灵敏度完全对称 |
| **v2.0** | **页面横向滑动 push/pop 动画**（Chat ↔ 子页面） |
| v2.1-v2.2 | LLM 时间注入（system prompt 加 `当前时间(基准)`，杜绝瞎编日期） |
| v2.3 | 思考三点动画 + LLM 回复逐字打字机 + 闪烁光标 |
| **v2.4** | **LLM 纯文本回复容错**（不再红色异常）+ **💹 投资分类**（自动排除消费分析） |

---

## 🤝 贡献 / 反馈

Issue / PR 欢迎。提 Issue 时请附：
- 触发步骤
- 期望行为 vs 实际行为
- App 版本（设置 → 关于记账助手 里能看到）
- 必要时附录屏 / 日志

---

## 📄 许可

MIT License — 自由 fork、二次开发、商用，但请保留原作者署名。
