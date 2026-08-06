# 截图录账（Screenshot Bill Import）设计

- 日期: 2026-07-19
- 状态: 已批准，授权一路实现
- 关联: 复用刚修复的 `LlmResponseParser` 解析管线（根因 A 兜底）

## 概述
在记账 App 内，用户从相册选一张支付宝/微信账单页面截图，App 本机 OCR 出文字，喂给现有文本 LLM 解析成多笔 expense，经确认页编辑后批量入库。一张截图可含多笔交易，批量导入。

## 方案选型
**A. 端侧 OCR（ML Kit）+ 现有文本 LLM**（选定）
- 不用视觉模型（大统领无视觉端点）
- 全程在 App 内：选图 → 本机 OCR → 文本 LLM → 确认 → 入库
- 复用已有 `LlmResponseParser` 解析管线（含 v2.4 抽取兜底 + 根因 A 修复）

否决理由：
- B（视觉 LLM）：需视觉模型，大统领没有
- C（本地正则）：支付宝/微信版式各异且随 App 更新变，规则易碎；商户名→分类无法自动推断

## 架构与数据流
```
[+] 按钮 → PhotoPicker(相册选图) → image Uri
  → 解码 Bitmap(降采样) → ML Kit TextRecognition → OCR 文本
  → LlmPrompt.billImportPrompt(ocrText) → LlmClient.chatJson(文本LLM, 账单专用 system prompt)
  → LlmResponseParser.parse → LlmParseResult(expenses=[多笔])
  → BillImportScreen 确认/编辑 → ExpenseRepository 批量入库
  → chatRepo.appendAssistant("📸 已从截图导入 N 笔，合计 ¥XX")
```

### 新增组件（职责单一、可独立测）
- `OcrRecognizer`（interface）：输入 Bitmap/Uri，输出 OCR 文本。ML Kit 实现 + 测试 fake。
- `LlmPrompt.billImportPrompt(ocrText)`：账单解析 prompt，要求把每笔交易输出成 `{amount,category,note,occurred_at}`，整体包成 `{reply, expenses:[...]}`（与对话记账同 schema，复用 `parse`）。
- `importFromBillText(client, prefs, ocrText): BillImportResult`：顶层函数（仿 `analyzeInsights`），调 `chatJson` + `parse`，返回多笔 expense 供确认页用，**不直接入库**（区别于 `llmHandler` 即插即用）。
- `BillImportViewModel` + `BillImportScreen`：选图、识别、确认列表、批量入库。

### 复用（不重造）
- `LlmClient.chatJson`（已 open 可 fake）
- `LlmResponseParser.parse`（含 v2.4 抽取兜底 + 根因 A 兜底——OCR 文本偶尔让 LLM 加废话，正好兜住）
- `ExpenseRepository.add`、`Category`、聊天消息流
- 现有分类 chip、expense 编辑控件、`InsightsScreen` 转场、`BackHandler` 模式

## UI
### 入口（填 `+` 的 TODO，`ChatScreen.kt:127`）
- LLM 开启：点 `+` → 唤起系统 PhotoPicker（相册选图）。PhotoPicker 是系统选择器，**不需新权限**（非 `READ_MEDIA_IMAGES`），AndroidManifest 不变。
- LLM 关闭：点 `+` → toast "请先开启 🧠 再用截图记账"。

### 确认/编辑页 `BillImportScreen`（`SubScreen` 右滑入，同 `InsightsScreen` 转场）
- 顶部：返回 + "📸 截图记账" + 选中截图缩略图（参考用）
- 识别中：转圈 + "正在识别截图..."
- 结果：每笔一张卡片——✅ 勾选框（默认全选，可剔除）、金额（可改）、分类（点按切换，复用分类 chip）、商户/备注（可改）、时间（默认 OCR 出的，可改）
- 底部按钮："导入 N 笔 · ¥合计" → 批量入库
- 出错态："识别失败：…" + "重选图片"
- 入库后：pop 回聊天页，聊天里出助手消息

### 接线（MainActivity）
加 `SubScreen.BillImport`，从 `ChatScreen` 经 `onOpenBillImport` 回调进入，复用现有 `AnimatedVisibility` 右滑入转场。`BillImportViewModel` 持 `expenseRepo` / `chatRepo` / `userPrefs` / `llmClient` + `OcrRecognizer`，自己跑 OCR+LLM+入库+写聊天消息。

## 错误处理与边界
- LLM 关闭：点 `+` 提示开启，不进流程
- 选图取消：无操作返回
- 图片解码失败："图片读取失败，重选"
- OCR 返回空文本："没识别到文字，换张清晰的账单截图"
- LLM 调用失败（网络/鉴权/超时）："识别失败：\<message\>" + 重选
- LLM 返回 0 笔（parse 走兜底仍空）："没识别出交易，换张图或手动记"
- 部分交易识别错：确认页可剔除/编辑，用户把关
- 大图：OCR 前降采样，防 OOM
- 投资类：LLM 按 prompt 标 `investment`，复用现有"不计入消费分析"逻辑

## 测试策略（TDD）
### 可 JVM 单测
- `LlmPrompt.billImportPrompt`：纯函数。断言含 OCR 文本、要求 `{reply,expenses}` schema、含提取每笔交易的指令。
- `importFromBillText`：fake `LlmClient`。返回 `{reply,expenses:[多笔]}` → 解析出多笔；prose 包裹响应 → 复用 `parse` 兜底仍解析；LLM 抛异常 → `BillImportResult.Error`。
- `BillImportViewModel`：fake import handler + fake repo。状态流转（loading/result/error）、勾选剔除、批量入库、写聊天消息。

### 需真机/仪器（不写 JVM 单测）
- `OcrRecognizer` 的 ML Kit 实现（需设备）
- `BillImportScreen` UI（Compose）
- PhotoPicker 启动（Activity）

## 不做（YAGNI）
- 相机拍照入口（账单页是已有截图）
- 视觉模型路径
- 本地正则解析（C 方案）
- LLM 关闭时的离线本地解析兜底
- 多页/连续批量导入
