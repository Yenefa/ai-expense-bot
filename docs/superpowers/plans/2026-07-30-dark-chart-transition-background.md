# Dark Chart and Transition Background Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make analytics charts readable in dark mode and prevent the white app root from appearing behind horizontal page transitions.

**Architecture:** Add a pure theme-to-chart-color mapping that can be unit tested, then provide the resulting Vico Material 3 chart style around both charts. Wrap the existing navigation animation layer in a full-window themed background without changing navigation state, direction, or timing.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Vico 1.13.1, JUnit 4, Google Truth

---

## File Structure

- Create `app/src/main/java/com/expense/tracker/ui/theme/AnalyticsChartColors.kt`: maps the application palette to explicit chart colors.
- Create `app/src/test/java/com/expense/tracker/ui/theme/AnalyticsChartColorsTest.kt`: verifies dark and light chart contrast rules.
- Create `app/src/main/java/com/expense/tracker/ui/analytics/AnalyticsChartStyle.kt`: converts chart colors to Vico `m3ChartStyle` and provides it to chart content.
- Modify `app/src/main/java/com/expense/tracker/ui/analytics/BarChartView.kt`: render the existing chart inside the themed Vico style.
- Modify `app/src/main/java/com/expense/tracker/ui/analytics/LineChartView.kt`: render the existing chart inside the themed Vico style.
- Modify `app/src/main/java/com/expense/tracker/MainActivity.kt`: add the themed full-screen background behind all transitions.

### Task 1: Test and add analytics chart colors

**Files:**
- Create: `app/src/test/java/com/expense/tracker/ui/theme/AnalyticsChartColorsTest.kt`
- Create: `app/src/main/java/com/expense/tracker/ui/theme/AnalyticsChartColors.kt`

- [ ] **Step 1: Write the failing chart-color test**

```kotlin
package com.expense.tracker.ui.theme

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AnalyticsChartColorsTest {
    @Test
    fun darkChartColorsRemainVisibleOnDarkBackground() {
        val colors = DarkAppColors.analyticsChartColors()

        assertThat(luma(colors.label) - luma(DarkAppColors.Bg)).isGreaterThan(0.35)
        assertThat(colors.guideline.alpha).isWithin(0.01f).of(0.28f)
        assertThat(colors.axisLine.alpha).isWithin(0.01f).of(0.55f)
        assertThat(colors.entities).containsExactly(DarkAppColors.TextPrimary)
    }

    @Test
    fun lightChartColorsKeepExistingMonochromeAppearance() {
        val colors = LightAppColors.analyticsChartColors()

        assertThat(colors.label).isEqualTo(LightAppColors.TextSecondary)
        assertThat(colors.entities).containsExactly(LightAppColors.TextPrimary)
    }

    private fun luma(color: androidx.compose.ui.graphics.Color): Double =
        0.299 * color.red + 0.587 * color.green + 0.114 * color.blue
}
```

- [ ] **Step 2: Run the test to verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest --tests 'com.expense.tracker.ui.theme.AnalyticsChartColorsTest' --console=plain
```

Expected: compilation fails with unresolved references to `analyticsChartColors`.

- [ ] **Step 3: Add the minimal color mapping**

```kotlin
package com.expense.tracker.ui.theme

import androidx.compose.ui.graphics.Color

data class AnalyticsChartColors(
    val label: Color,
    val guideline: Color,
    val axisLine: Color,
    val entities: List<Color>,
)

fun AppColorPalette.analyticsChartColors() = AnalyticsChartColors(
    label = TextSecondary,
    guideline = TextMuted.copy(alpha = 0.28f),
    axisLine = TextMuted.copy(alpha = 0.55f),
    entities = listOf(TextPrimary),
)
```

- [ ] **Step 4: Run the focused test to verify GREEN**

Run the command from Step 2.

Expected: `AnalyticsChartColorsTest` passes with zero failures.

- [ ] **Step 5: Commit the tested theme mapping**

```powershell
git add app/src/main/java/com/expense/tracker/ui/theme/AnalyticsChartColors.kt app/src/test/java/com/expense/tracker/ui/theme/AnalyticsChartColorsTest.kt
git commit -m "test: define readable analytics chart colors"
```

### Task 2: Apply the theme to both Vico charts

**Files:**
- Create: `app/src/main/java/com/expense/tracker/ui/analytics/AnalyticsChartStyle.kt`
- Modify: `app/src/main/java/com/expense/tracker/ui/analytics/BarChartView.kt`
- Modify: `app/src/main/java/com/expense/tracker/ui/analytics/LineChartView.kt`

- [ ] **Step 1: Add a shared Vico style provider**

```kotlin
package com.expense.tracker.ui.analytics

import androidx.compose.runtime.Composable
import com.expense.tracker.ui.theme.LocalAppColors
import com.expense.tracker.ui.theme.analyticsChartColors
import com.patrykandpatrick.vico.compose.m3.style.m3ChartStyle
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle

@Composable
fun ProvideAnalyticsChartStyle(content: @Composable () -> Unit) {
    val colors = LocalAppColors.current.analyticsChartColors()
    ProvideChartStyle(
        chartStyle = m3ChartStyle(
            axisLabelColor = colors.label,
            axisGuidelineColor = colors.guideline,
            axisLineColor = colors.axisLine,
            entityColors = colors.entities,
        ),
        content = content,
    )
}
```

- [ ] **Step 2: Wrap the bar chart**

Replace the direct `Chart(...)` call in `BarChartView` with:

```kotlin
ProvideAnalyticsChartStyle {
    Chart(
        modifier = modifier.fillMaxWidth().height(260.dp),
        chart = columnChart(),
        chartModelProducer = producer,
        startAxis = rememberStartAxis(
            valueFormatter = yFormatter,
            itemPlacer = AxisItemPlacer.Vertical.default(maxItemCount = 4),
        ),
        bottomAxis = rememberBottomAxis(
            valueFormatter = xFormatter,
            itemPlacer = AxisItemPlacer.Horizontal.default(
                spacing = if (xLabels.size > 12) 5 else 1,
            ),
        ),
    )
}
```

- [ ] **Step 3: Wrap the line chart**

Replace the direct `Chart(...)` call in `LineChartView` with:

```kotlin
ProvideAnalyticsChartStyle {
    Chart(
        modifier = modifier.fillMaxWidth().height(260.dp),
        chart = lineChart(),
        chartModelProducer = producer,
        startAxis = rememberStartAxis(
            valueFormatter = yFormatter,
            itemPlacer = AxisItemPlacer.Vertical.default(maxItemCount = yItemCount),
        ),
        bottomAxis = rememberBottomAxis(
            valueFormatter = xFormatter,
            itemPlacer = AxisItemPlacer.Horizontal.default(
                spacing = if (xLabels.size > 12) 5 else 1,
            ),
        ),
    )
}
```

- [ ] **Step 4: Compile the Vico integration**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat compileDebugKotlin --console=plain
```

Expected: `BUILD SUCCESSFUL`; the named `m3ChartStyle` arguments are accepted by Vico 1.13.1.

- [ ] **Step 5: Commit the chart integration**

```powershell
git add app/src/main/java/com/expense/tracker/ui/analytics/AnalyticsChartStyle.kt app/src/main/java/com/expense/tracker/ui/analytics/BarChartView.kt app/src/main/java/com/expense/tracker/ui/analytics/LineChartView.kt
git commit -m "fix: theme analytics charts for dark mode"
```

### Task 3: Put the current theme behind page transitions

**Files:**
- Modify: `app/src/main/java/com/expense/tracker/MainActivity.kt`

- [ ] **Step 1: Add the root layout imports**

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.expense.tracker.ui.theme.AppColors
```

- [ ] **Step 2: Wrap the navigation layer**

Inside `AppTheme`, keep `screen`, `subScreen`, and `BackHandler` unchanged. Insert the following immediately after the `BackHandler` block and before `AnimatedContent`:

```kotlin
Box(
    modifier = Modifier
        .fillMaxSize()
        .background(AppColors.Bg),
) {
```

Insert the matching closing brace immediately after the final Insights `AnimatedVisibility` block and before the existing `AppTheme` closing brace:

```kotlin
}
```

All existing animation blocks remain inside this `Box`, so the exposed area during a slide equals the active theme background.

- [ ] **Step 3: Compile the root background change**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat compileDebugKotlin --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit the transition background fix**

```powershell
git add app/src/main/java/com/expense/tracker/MainActivity.kt
git commit -m "fix: theme the page transition background"
```

### Task 4: Verify and deliver the refreshed v3.6 APK

**Files:**
- Build output: `app/build/outputs/apk/debug/app-debug.apk`
- Deliver: `C:\Users\fuker\Desktop\app\codex change\ai-expense-bot-v3.6-dark-chart-fix-2026-07-30.apk`

- [ ] **Step 1: Run the full unit suite and build**

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:CSV_IMPORT_FIXTURE='H:\VX\xwechat_files\wxid_3mf0rejn5stn22_e473\msg\file\2026-07\expense-tracker-export-20260726-075740.csv'
$env:CSV_IMPORT_EXPECTED_ROWS='128'
.\gradlew.bat testDebugUnitTest assembleDebug --console=plain
```

Expected: `BUILD SUCCESSFUL`, including the 128-row CSV fixture test.

- [ ] **Step 2: Inspect package metadata**

```powershell
& 'C:\Users\fuker\AppData\Local\Android\Sdk\build-tools\34.0.0\aapt.exe' dump badging app\build\outputs\apk\debug\app-debug.apk
```

Expected: package `com.expense.tracker`, version code `27`, version name `3.6`.

- [ ] **Step 3: Copy and verify the APK**

Copy the same APK to the requested desktop folder and the Codex output folder. Verify both copies have the same SHA-256 hash and run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
& 'C:\Users\fuker\AppData\Local\Android\Sdk\build-tools\34.0.0\apksigner.bat' verify --verbose --print-certs 'C:\Users\fuker\Desktop\app\codex change\ai-expense-bot-v3.6-dark-chart-fix-2026-07-30.apk'
```

Expected: `Verifies`, APK Signature Scheme v2 is true, and both hashes match.

- [ ] **Step 4: Confirm repository state**

```powershell
git status --short
git log -5 --oneline
```

Expected: no uncommitted source changes; the three implementation commits follow the approved design and plan commits.
