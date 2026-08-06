# 学习与创作消费类别设计

## 目标

新增一个普通消费类别「学习与创作」，让书籍、课程、电子元器件，以及 API、模型调用、云算力等费用能被清晰记录和统计。

## 分类定义

- 稳定标识：`education`
- 显示名称：`学习与创作`
- 图标：`📚`
- 属性：普通消费，不设置 `isInvestment`；会进入消费图表和智核分析。
- 归类范围：书籍、课程、纸质或电子学习资料、开发板、传感器、电子元器件、API/模型调用、云算力与其他直接服务于学习或创作的支出。

## 实现边界

`Category.ALL` 是唯一分类来源。新增该项后，手动记账、编辑、OCR 账单确认、CSV 导入/导出、历史页、统计图表和 LLM 分类提示词会自动共享该类别。

不修改 Room schema、已有记录、金额处理、OCR 识别依赖或 OCR-to-LLM 流程。旧数据仍按既有 category ID 读取；新 CSV/备份中的 `education` 可被正常导入。

## LLM 行为

LLM 提示词将明确要求：涉及书籍、课程、电子元器件、API、模型调用或云算力的支出优先使用 `category="education"`。类别清单仍由 `Category.ALL` 动态生成，避免重复维护。

## 验收与测试

- `Category.byId("education")` 返回名称「学习与创作」、图标 📚，且 `isInvestment=false`。
- LLM 解析 `category="education"` 时保留该分类；未知分类继续降级为 `other`。
- CSV 导入接受 `education`；导出保留 `education`。
- 分类存在于手动选择、账单确认和统计分类列表所依赖的 `Category.ALL`。
- 不影响 OCR 依赖、模型资源或原生 ABI。
