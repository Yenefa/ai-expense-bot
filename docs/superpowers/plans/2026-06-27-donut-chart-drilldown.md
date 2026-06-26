# 甜甜圈图层级钻取 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将分析页面的饼图改为甜甜圈图，支持按周期层级钻取（周→天、月→天、年→月）。

**Architecture:** 在现有 Canvas 自绘饼图基础上，改为内外环甜甜圈图。ViewModel 新增每个子周期的分类汇总数据，UI 通过点击内环切换外环显示内容。

**Tech Stack:** Kotlin + Jetpack Compose + Canvas API

---

## File Structure

| 文件 | 操作 | 职责 |
|------|------|------|
| `app/.../ui/analytics/PieChartView.kt` | 删除 | 被 DonutChartView 替代 |
| `app/.../ui/analytics/DonutChartView.kt` | 新建 | 甜甜圈图 Canvas 绘制 + 点击检测 |
| `app/.../ui/analytics/AnalyticsViewModel.kt` | 修改 | 新增 SubPeriodDetail、subPeriods 字段、aggregate 计算 |
| `app/.../ui/analytics/AnalyticsScreen.kt` | 修改 | 替换 PieChartView 为 DonutChartView |
| `app/.../ui/analytics/AnalyticsViewModelTest.kt` | 修改/新建 | 测试新增的 aggregate 逻辑 |

---

### Task 1: ViewModel — 新增 SubPeriodDetail 和 subPeriods 数据

**Files:**
- Modify: `app/src/main/java/com/expense/tracker/ui/analytics/AnalyticsViewModel.kt`

- [ ] **Step 1: 在 AnalyticsUiState 上方新增 SubPeriodDetail 数据类**

```kotlin
data class SubPeriodDetail(
    val totalAmount: Double,
    val byCategory: Map<String, Double>,
)
```

- [ ] **Step 2: 在 AnalyticsUiState 中新增字段**

```kotlin
data class AnalyticsUiState(
    // ... 现有字段不变 ...
    val subPeriods: List<SubPeriodDetail> = emptyList(),
    val selectedSubPeriodIndex: Int? = null,
)
```

- [ ] **Step 3: 在 ViewModel 中新增 selectSubPeriod 方法**

```kotlin
fun selectSubPeriod(index: Int?) {
    internal.update { it.copy(selectedSubPeriodIndex = index) }
}
```

- [ ] **Step 4: 修改 aggregate() 方法，计算每个子周期的分类汇总**

在 `aggregate()` 函数的 `consumptionList.forEach` 循环之后，添加子周期明细计算：

```kotlin
// 计算每个子周期的分类汇总
val subPeriodDetails = (0 until n).map { idx ->
    val filtered = consumptionList.filter { e ->
        val eIdx = TimeRanges.bucketIndex(p, fromMillis, e.occurredAt, zone)
        eIdx == idx
    }
    SubPeriodDetail(
        totalAmount = filtered.sumOf { it.amount },
        byCategory = filtered.groupBy({ it.categoryId }, { it.amount })
            .mapValues { (_, amounts) -> amounts.sum() },
    )
}
```

- [ ] **Step 5: 在 aggregate() 返回的 AnalyticsUiState 中加入 subPeriods**

```kotlin
return AnalyticsUiState(
    // ... 现有字段 ...
    subPeriods = subPeriodDetails,
    selectedSubPeriodIndex = null, // 切换周期时重置选择
)
```

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/expense/tracker/ui/analytics/AnalyticsViewModel.kt
git commit -m "feat(viewmodel): add sub-period category detail for donut drill-down"
```

---

### Task 2: 甜甜圈图组件 — DonutChartView

**Files:**
- Create: `app/src/main/java/com/expense/tracker/ui/analytics/DonutChartView.kt`
- Delete: `app/src/main/java/com/expense/tracker/ui/analytics/PieChartView.kt`

- [ ] **Step 1: 创建 DonutChartView.kt，实现内外环绘制**

```kotlin
package com.expense.tracker.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.model.Category
import kotlin.math.PI
import kotlin.math.atan2

// 按 Category.id 给每个分类指定固定色
private val CategoryColors: Map<String, Color> = mapOf(
    "food"          to Color(0xFFFCD34D),
    "transport"     to Color(0xFF6B7280),
    "shopping"     to Color(0xFFEC4899),
    "drink"         to Color(0xFF7DD3FC),
    "entertainment" to Color(0xFFA78BFA),
    "housing"       to Color(0xFF34D399),
    "medical"       to Color(0xFFF87171),
    "investment"    to Color(0xFF14B8A6),
    "other"         to Color(0xFFD4D4D4),
)
private val FallbackColor = Color(0xFF9CA3AF)

/** 子周期颜色调色板（内环用） */
private val SubPeriodColors = listOf(
    Color(0xFF60A5FA), // 蓝
    Color(0xFF34D399), // 绿
    Color(0xFFFBBF24), // 黄
    Color(0xFFF87171), // 红
    Color(0xFFA78BFA), // 紫
    Color(0xFFFB923C), // 橙
    Color(0xFF22D3EE), // 青
    Color(0xFFE879F9), // 粉紫
    Color(0xFFF472B6), // 粉
    Color(0xFF818CF8), // 靛蓝
    Color(0xFFFDE68A), // 浅黄
    Color(0xFF6EE7B7), // 浅绿
    Color(0xFFFCA5A5), // 浅红
    Color(0xFFC4B5FD), // 浅紫
    Color(0xFFFDBA74), // 浅橙
    Color(0xFF67E8F9), // 浅青
    Color(0xFFF0ABFC), // 浅粉紫
    Color(0xFFF9A8D4), // 浅粉
    Color(0xFFA5B4FC), // 浅靛蓝
    Color(0xFFD1FAE5), // 极浅绿
    Color(0xFFFEF3C7), // 极浅黄
    Color(0xFFFEE2E2), // 极浅红
    Color(0xFFE0E7FF), // 极浅靛蓝
    Color(0xFFCCFBF1), // 极浅青
    Color(0xFFFFF7ED), // 极浅橙
    Color(0xFFF5F3FF), // 极浅紫
    Color(0xFFFCE7F3), // 极浅粉
    Color(0xFFECFDF5), // 极浅绿
    Color(0xFFFFFBEB), // 极浅黄
    Color(0xFFFEF2F2), // 极浅红
    Color(0xFFEDE9FE), // 极浅紫
)

@Composable
fun DonutChartView(
    subPeriods: List<SubPeriodDetail>,
    subPeriodLabels: List<String>,
    outerByCategory: Map<String, Double>,
    selectedIndex: Int?,
    onSelectSubPeriod: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = outerByCategory.values.sum().takeIf { it > 0.0 } ?: return
    val ordered = Category.ALL.mapNotNull { c ->
        val v = outerByCategory[c.id]
        if (v == null || v <= 0.0) null
        else Triple(c, v, CategoryColors[c.id] ?: FallbackColor)
    }

    Row(
        modifier = modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 甜甜圈 Canvas
        Canvas(
            modifier = Modifier
                .size(180.dp)
                .pointerInput(subPeriods, selectedIndex) {
                    val innerRadiusRatio = 0.35f
                    val outerRadiusRatio = 0.48f
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val press = event.changes.firstOrNull() ?: break
                            if (!press.pressed) continue

                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val dx = press.position.x - cx
                            val dy = press.position.y - cy
                            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                            val maxR = size.minDimension / 2f

                            // 检测是否点击在中心区域（返回按钮）
                            val centerR = maxR * innerRadiusRatio * 0.5f
                            if (dist <= centerR) {
                                if (selectedIndex != null) {
                                    onSelectSubPeriod(null)
                                }
                                continue
                            }

                            // 检测是否点击在内环区域
                            val innerR = maxR * innerRadiusRatio
                            val outerR = maxR * outerRadiusRatio
                            if (dist in innerR..outerR && subPeriods.isNotEmpty()) {
                                val subTotal = subPeriods.sumOf { it.totalAmount }.takeIf { it > 0 } ?: 1.0
                                val angle = (atan2(dy, dx).toDouble() * 180 / PI + 90 + 360) % 360
                                var cumulative = 0.0
                                for (i in subPeriods.indices) {
                                    val sweep = subPeriods[i].totalAmount / subTotal * 360
                                    cumulative += sweep
                                    if (angle < cumulative) {
                                        onSelectSubPeriod(if (i == selectedIndex) null else i)
                                        break
                                    }
                                }
                            }
                        }
                    }
                },
        ) {
            val maxR = size.minDimension / 2f
            val innerR = maxR * 0.35f
            val outerR = maxR * 0.48f
            val center = Offset(size.width / 2f, size.height / 2f)

            // --- 绘制外环：分类占比 ---
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

            // 绘制内环白色遮罩（形成甜甜圈孔）
            drawCircle(
                color = Color.Transparent,
                radius = innerR,
                center = center,
            )
            // 用背景色覆盖中心区域（因为 Transparent 不能覆盖下层颜色）
            // 先画白色覆盖
            drawCircle(
                color = Color.White,
                radius = innerR * 1.02f,
                center = center,
            )

            // --- 绘制内环：子周期 ---
            if (subPeriods.isNotEmpty()) {
                val subTotal = subPeriods.sumOf { it.totalAmount }.takeIf { it > 0 } ?: 1.0
                var subStart = -90f
                subPeriods.forEachIndexed { i, detail ->
                    val sweep = (detail.totalAmount / subTotal * 360.0).toFloat()
                    // 子周期颜色（选中时高亮）
                    val isSelected = i == selectedIndex
                    val color = SubPeriodColors[i % SubPeriodColors.size]
                    val finalColor = if (isSelected) color else color.copy(alpha = 0.7f)

                    // 在内环区域绘制弧
                    drawArc(
                        color = finalColor,
                        startAngle = subStart,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = Offset(
                            center.x - innerR,
                            center.y - innerR,
                        ),
                        size = Size(innerR * 2, innerR * 2),
                    )
                    subStart += sweep
                }

                // 绘制最中心的白色小圆（返回按钮区域）
                val centerCircleR = innerR * 0.45f
                drawCircle(
                    color = Color(0xFFF5F5F5),
                    radius = centerCircleR,
                    center = center,
                )
                // 选中时显示 "←" 返回指示
                if (selectedIndex != null) {
                    drawCircle(
                        color = Color(0xFFE0E0E0),
                        radius = centerCircleR * 0.85f,
                        center = center,
                    )
                }
            }
        }

        Spacer(Modifier.width(20.dp))

        // --- 右侧图例 ---
        // 如果选中了某个子周期，显示该子周期的分类明细
        val displayData = if (selectedIndex != null && selectedIndex in subPeriods.indices) {
            val detail = subPeriods[selectedIndex]
            val subTotal = detail.totalAmount.takeIf { it > 0 } ?: return@Row
            Category.ALL.mapNotNull { c ->
                val v = detail.byCategory[c.id]
                if (v == null || v <= 0.0) null
                else Triple(c, v, CategoryColors[c.id] ?: FallbackColor)
            }
        } else {
            ordered
        }

        val displayTotal = displayData.sumOf { (_, v, _) -> v }.takeIf { it > 0 } ?: return@Row

        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // 显示当前子周期名称
            if (selectedIndex != null && selectedIndex in subPeriodLabels.indices) {
                Text(
                    "📌 ${subPeriodLabels[selectedIndex]}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray,
                )
            }
            displayData.forEach { (c, v, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(10.dp)) { drawRect(color) }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${c.emoji} ${c.displayName}  ¥${"%.2f".format(v)}  (${"%.1f".format(v / displayTotal * 100)}%)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: 删除旧的 PieChartView.kt**

```bash
rm app/src/main/java/com/expense/tracker/ui/analytics/PieChartView.kt
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/expense/tracker/ui/analytics/DonutChartView.kt
git add app/src/main/java/com/expense/tracker/ui/analytics/PieChartView.kt
git commit -m "feat(ui): add donut chart with ring drill-down, replace pie chart"
```

---

### Task 3: AnalyticsScreen — 集成甜甜圈图

**Files:**
- Modify: `app/src/main/java/com/expense/tracker/ui/analytics/AnalyticsScreen.kt`

- [ ] **Step 1: 替换 PieChartView 为 DonutChartView**

将文件末尾的 SectionTitle("🥧 分类占比（扇形图）") 部分改为：

```kotlin
Spacer(Modifier.height(28.dp))
SectionTitle("🥧 分类占比（甜甜圈图）")
DonutChartView(
    subPeriods = state.subPeriods,
    subPeriodLabels = state.xLabels,
    outerByCategory = state.pieByCategory,
    selectedIndex = state.selectedSubPeriodIndex,
    onSelectSubPeriod = vm::selectSubPeriod,
    modifier = Modifier.padding(horizontal = 16.dp),
)
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/expense/tracker/ui/analytics/AnalyticsScreen.kt
git commit -m "feat(screen): integrate donut chart with drill-down into analytics screen"
```

---

### Task 4: 测试 — ViewModel subPeriod 逻辑

**Files:**
- Modify: `app/src/test/java/com/expense/tracker/ui/chat/ChatViewModelTest.kt` 或新建测试文件

- [ ] **Step 1: 创建 AnalyticsViewModelTest.kt**

```kotlin
package com.expense.tracker.ui.analytics

import com.expense.tracker.data.db.ExpenseDao
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.model.Period
import com.expense.tracker.data.repo.ExpenseRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

private class FakeExpenseDaoForAnalytics : ExpenseDao {
    private val state = MutableStateFlow<List<ExpenseEntity>>(emptyList())
    private var seq = 0L

    /** 辅助方法：插入一条测试支出 */
    fun insertRaw(entity: ExpenseEntity) {
        seq++
        state.value = state.value + entity.copy(id = seq)
    }

    override suspend fun insert(expense: ExpenseEntity): Long { seq++; state.value = state.value + expense.copy(id = seq); return seq }
    override suspend fun update(expense: ExpenseEntity) { state.value = state.value.map { if (it.id == expense.id) expense else it } }
    override fun observeActive(): Flow<List<ExpenseEntity>> = state
    override suspend fun getAllActiveOnce(): List<ExpenseEntity> = state.value.filter { it.deletedAt == null }
    override suspend fun getById(id: Long): ExpenseEntity? = state.value.firstOrNull { it.id == id }
    override fun observeInRange(from: Long, to: Long): Flow<List<ExpenseEntity>> =
        flow { emit(state.value.filter { it.occurredAt in from until to && it.deletedAt == null }) }
    override fun observeDeleted(): Flow<List<ExpenseEntity>> =
        flow { emit(state.value.filter { it.deletedAt != null }) }
    override suspend fun softDeleteById(id: Long, deletedAtMillis: Long) {
        state.value = state.value.map { if (it.id == id) it.copy(deletedAt = deletedAtMillis) else it }
    }
    override suspend fun restoreById(id: Long) {
        state.value = state.value.map { if (it.id == id) it.copy(deletedAt = null) else it }
    }
    override suspend fun deleteById(id: Long) { state.value = state.value.filterNot { it.id == id } }
    override suspend fun purgeOlderThan(cutoffMillis: Long) {}
}

class AnalyticsViewModelTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    private fun millis(day: Int, hour: Int = 12): Long =
        LocalDate.of(2025, 6, day).atStartOfDay(zone).toInstant().toEpochMilli() + hour * 3600_000L

    @Test
    fun `aggregate creates subPeriods with per-day category breakdown for week`() = runBlocking {
        val dao = FakeExpenseDaoForAnalytics()
        val repo = ExpenseRepository(dao)
        // 周一（6/9）餐饮 30 元 + 交通 10 元；周二（6/10）购物 50 元
        val now = millis(9)
        dao.insertRaw(ExpenseEntity(amount = 30.0, categoryId = "food", note = "", occurredAt = millis(9), createdAt = now))
        dao.insertRaw(ExpenseEntity(amount = 10.0, categoryId = "transport", note = "", occurredAt = millis(9), createdAt = now))
        dao.insertRaw(ExpenseEntity(amount = 50.0, categoryId = "shopping", note = "", occurredAt = millis(10), createdAt = now))

        // 用周二的时刻触发 ViewModel，周期范围应是 6/9(周一) ~ 6/16
        val now = millis(10)
        val vm = AnalyticsViewModel(repo, zone, nowProvider = { now })
        val state = vm.uiState.first { it.subPeriods.isNotEmpty() }

        // subPeriods 应有 7 个元素（周一~周日）
        assertThat(state.subPeriods).hasSize(7)

        // 周一（索引 0）：总金额 40，food=30, transport=10
        val mon = state.subPeriods[0]
        assertThat(mon.totalAmount).isEqualTo(40.0)
        assertThat(mon.byCategory).containsEntry("food", 30.0)
        assertThat(mon.byCategory).containsEntry("transport", 10.0)

        // 周二（索引 1）：总金额 50，shopping=50
        val tue = state.subPeriods[1]
        assertThat(tue.totalAmount).isEqualTo(50.0)
        assertThat(tue.byCategory).containsEntry("shopping", 50.0)

        // 周三~周日：空
        assertThat(state.subPeriods[2].totalAmount).isEqualTo(0.0)
        assertThat(state.subPeriods[3].totalAmount).isEqualTo(0.0)
        assertThat(state.subPeriods[4].totalAmount).isEqualTo(0.0)
        assertThat(state.subPeriods[5].totalAmount).isEqualTo(0.0)
        assertThat(state.subPeriods[6].totalAmount).isEqualTo(0.0)
    }

    @Test
    fun `selectSubPeriod updates index and null clears selection`() = runBlocking {
        val dao = FakeExpenseDaoForAnalytics()
        val repo = ExpenseRepository(dao)
        val now = millis(9)
        dao.insertRaw(ExpenseEntity(amount = 10.0, categoryId = "food", note = "", occurredAt = now, createdAt = now))

        val vm = AnalyticsViewModel(repo, zone, nowProvider = { now })
        vm.uiState.first { it.subPeriods.isNotEmpty() }

        // 初始为 null
        assertThat(vm.uiState.value.selectedSubPeriodIndex).isNull()

        // 选择索引 0
        vm.selectSubPeriod(0)
        assertThat(vm.uiState.value.selectedSubPeriodIndex).isEqualTo(0)

        // 选择索引 2
        vm.selectSubPeriod(2)
        assertThat(vm.uiState.value.selectedSubPeriodIndex).isEqualTo(2)

        // 清除选择
        vm.selectSubPeriod(null)
        assertThat(vm.uiState.value.selectedSubPeriodIndex).isNull()
    }

    @Test
    fun `switching period resets selectedSubPeriodIndex to null`() = runBlocking {
        val dao = FakeExpenseDaoForAnalytics()
        val repo = ExpenseRepository(dao)
        val now = millis(9)
        dao.insertRaw(ExpenseEntity(amount = 10.0, categoryId = "food", note = "", occurredAt = now, createdAt = now))

        val vm = AnalyticsViewModel(repo, zone, nowProvider = { millis(9) })
        vm.uiState.first { it.subPeriods.isNotEmpty() }

        vm.selectSubPeriod(0)
        assertThat(vm.uiState.value.selectedSubPeriodIndex).isEqualTo(0)

        // 切换周期应重置
        vm.selectPeriod(Period.Month)
        vm.uiState.first { it.period == Period.Month && it.subPeriods.isNotEmpty() }
        assertThat(vm.uiState.value.selectedSubPeriodIndex).isNull()
    }
}
```

- [ ] **Step 2: 运行测试确认通过**

Run: `./gradlew test`
Expected: 测试全部通过

- [ ] **Step 3: 提交**

```bash
git add app/src/test/java/com/expense/tracker/ui/analytics/DonutChartViewModelTest.kt
git commit -m "test: add donut chart view model tests"
```

---

## 自检

### Spec 覆盖率
- ✅ 甜甜圈图替代饼图（Task 2）
- ✅ 内环显示子周期（Task 1 + Task 2）
- ✅ 外环显示分类明细（Task 2）
- ✅ 点击内环切换子周期（Task 2）
- ✅ 点击中心返回总览（Task 2）
- ✅ 周→天、月→天、年→月（TimeRanges 已有逻辑，无需改动）

### 类型一致性
- `SubPeriodDetail` 在 Task 1 定义，Task 2 和 Task 3 引用 — 一致
- `selectedSubPeriodIndex: Int?` 在 ViewModel 和 UiState 中一致
- `onSelectSubPeriod: (Int?) -> Unit` 在 DonutChartView 和 ViewModel 中一致

### 无占位符
- 所有代码块都有完整实现，无 TBD/TODO
- 测试文件中有完整的方法签名和注释
