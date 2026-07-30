# Theme Card System Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every card-like component and selected control use matching light and dark theme colors while preserving layout, animation, and semantic action colors.

**Architecture:** Extend the application palette with one semantic `CardBg` color and wire it into Material `surface`. Replace page-background and hardcoded-white card fills with `CardBg`, then fix foreground colors for controls whose background is `TextPrimary`. Finish with three independent scans plus full regression and APK verification.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, JUnit 4, Google Truth, Gradle

---

## File Structure

- Modify `app/src/main/java/com/expense/tracker/ui/theme/Color.kt`: add `CardBg` to both palettes and expose it through `AppColors`.
- Modify `app/src/main/java/com/expense/tracker/ui/theme/Theme.kt`: map Material `surface` to `CardBg`.
- Modify `app/src/test/java/com/expense/tracker/ui/theme/ColorTest.kt`: verify light and dark card semantics and contrast.
- Modify analytics, settings, history, chat, template, and liquid-glass components listed below to consume semantic colors.

### Task 1: Add and test the card semantic color

**Files:**
- Modify: `app/src/test/java/com/expense/tracker/ui/theme/ColorTest.kt`
- Modify: `app/src/main/java/com/expense/tracker/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/expense/tracker/ui/theme/Theme.kt`

- [ ] **Step 1: Write failing palette tests**

Add to `ColorTest`:

```kotlin
@Test fun lightCardMatchesWhiteSurface() =
    assertThat(LightAppColors.CardBg).isEqualTo(Color(0xFFFFFFFF))

@Test fun darkCardIsBrighterThanPageBackground() {
    assertThat(luma(DarkAppColors.CardBg)).isGreaterThan(luma(DarkAppColors.Bg))
}

@Test fun darkPrimaryFillUsesDarkReadableForeground() {
    assertThat(DarkAppColors.Bg).isNotEqualTo(Color.White)
    assertThat(luma(DarkAppColors.TextPrimary) - luma(DarkAppColors.Bg)).isGreaterThan(0.7)
}
```

- [ ] **Step 2: Run the focused test to verify RED**

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest --tests 'com.expense.tracker.ui.theme.ColorTest' --console=plain
```

Expected: compilation fails because `CardBg` is unresolved.

- [ ] **Step 3: Add `CardBg` to the palette**

Update `AppColorPalette` and both palette instances:

```kotlin
data class AppColorPalette(
    val Bg: Color,
    val CardBg: Color,
    val TextPrimary: Color,
    val TextSecondary: Color,
    val TextMuted: Color,
    val ChipFill: Color,
    val Accent: Color,
    val DockBgSimulated: Color,
    val ErrorBg: Color,
)

val LightAppColors = AppColorPalette(
    Bg = Color(0xFFFFFFFF),
    CardBg = Color(0xFFFFFFFF),
    TextPrimary = Color(0xFF0D0D0D),
    TextSecondary = Color(0xFF5D5D5D),
    TextMuted = Color(0xFF8E8E8E),
    ChipFill = Color(0xFFF4F4F4),
    Accent = Color(0xFF0A84FF),
    DockBgSimulated = Color(0xFFD4D4D4),
    ErrorBg = Color(0xFFFFF5F5),
)

val DarkAppColors = AppColorPalette(
    Bg = Color(0xFF1C1C1E),
    CardBg = Color(0xFF2C2C2E),
    TextPrimary = Color(0xFFF2F2F2),
    TextSecondary = Color(0xFFAEAEAE),
    TextMuted = Color(0xFF6E6E6E),
    ChipFill = Color(0xFF2C2C2E),
    Accent = Color(0xFF0A84FF),
    DockBgSimulated = Color(0xFF2C2C2E),
    ErrorBg = Color(0xFF2A1A1A),
)
```

Expose it through `AppColors`:

```kotlin
val CardBg: Color
    @Composable get() = LocalAppColors.current.CardBg
```

- [ ] **Step 4: Map Material surfaces to card backgrounds**

In `Theme.kt`, set:

```kotlin
surface = LightAppColors.CardBg,
```

and:

```kotlin
surface = DarkAppColors.CardBg,
```

Keep `background` mapped to `Bg` and `surfaceVariant` mapped to `ChipFill`.

- [ ] **Step 5: Run the focused test to verify GREEN**

Run the command from Step 2.

Expected: all `ColorTest` cases pass.

- [ ] **Step 6: Commit the palette change**

```powershell
git add app/src/main/java/com/expense/tracker/ui/theme/Color.kt app/src/main/java/com/expense/tracker/ui/theme/Theme.kt app/src/test/java/com/expense/tracker/ui/theme/ColorTest.kt
git commit -m "feat: add semantic card colors"
```

### Task 2: Fix hardcoded and mismatched foreground colors

**Files:**
- Modify: `app/src/main/java/com/expense/tracker/ui/analytics/InsightsScreen.kt`
- Modify: `app/src/main/java/com/expense/tracker/ui/template/CategoryChip.kt`
- Modify: `app/src/main/java/com/expense/tracker/ui/history/ExpenseEditDialog.kt`
- Modify: `app/src/main/java/com/expense/tracker/ui/history/MonthCalendarView.kt`
- Modify: `app/src/main/java/com/expense/tracker/ui/liquidglass/LiquidGlassButton.kt`

- [ ] **Step 1: Fix the Insights card and strong-button foreground**

Apply these exact replacements in `InsightsScreen.kt`:

```kotlin
CardDefaults.cardColors(containerColor = Color.White)
```

becomes:

```kotlin
CardDefaults.cardColors(containerColor = AppColors.CardBg)
```

The insight-result card container `AppColors.Bg` also becomes `AppColors.CardBg`.

The analysis button:

```kotlin
containerColor = AppColors.TextPrimary,
contentColor = Color.White,
```

becomes:

```kotlin
containerColor = AppColors.TextPrimary,
contentColor = AppColors.Bg,
```

- [ ] **Step 2: Fix selected category foregrounds**

In `CategoryChip.kt`, set:

```kotlin
targetValue = if (selected) AppColors.TextPrimary else AppColors.ChipFill
```

for the background and:

```kotlin
targetValue = if (selected) AppColors.Bg else AppColors.TextPrimary
```

for the foreground.

In `ExpenseEditDialog.kt`, replace the selected text color:

```kotlin
color = if (selected) AppColors.Bg else AppColors.TextPrimary
```

- [ ] **Step 3: Fix calendar strong-fill foregrounds**

In `MonthCalendarView.kt`, use:

```kotlin
val bg = if (isToday) AppColors.TextPrimary else AppColors.CardBg
val mainColor = if (isToday) AppColors.Bg else AppColors.TextPrimary
val subColor = if (isToday) AppColors.Bg.copy(alpha = 0.85f) else AppColors.TextSecondary
```

The amount text uses `mainColor` instead of a separate `Color.White` branch.

- [ ] **Step 4: Theme the liquid-glass button**

Import `AppColors` and use:

```kotlin
.background(AppColors.TextPrimary.copy(alpha = bgAlpha))
```

for the button body:

```kotlin
color = AppColors.TextPrimary.copy(alpha = borderAlpha)
```

for the border, and:

```kotlin
.background(AppColors.Bg.copy(alpha = 0.7f))
```

for the highlight. Keep black shadow colors because they are shadows, not card content.

- [ ] **Step 5: Compile the high-risk fixes**

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat compileDebugKotlin --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/expense/tracker/ui/analytics/InsightsScreen.kt app/src/main/java/com/expense/tracker/ui/template/CategoryChip.kt app/src/main/java/com/expense/tracker/ui/history/ExpenseEditDialog.kt app/src/main/java/com/expense/tracker/ui/history/MonthCalendarView.kt app/src/main/java/com/expense/tracker/ui/liquidglass/LiquidGlassButton.kt
git commit -m "fix: match strong controls to both themes"
```

### Task 3: Apply `CardBg` to every raised card and overlay

**Files:**
- Modify: `app/src/main/java/com/expense/tracker/ui/analytics/AnalyticsScreen.kt`
- Modify: `app/src/main/java/com/expense/tracker/ui/analytics/CategoryBreakdownList.kt`
- Modify: `app/src/main/java/com/expense/tracker/ui/settings/SettingsMenuScreen.kt`
- Modify: `app/src/main/java/com/expense/tracker/ui/settings/DataExportScreen.kt`
- Modify: `app/src/main/java/com/expense/tracker/ui/settings/ThemePickerDialog.kt`
- Modify: `app/src/main/java/com/expense/tracker/ui/settings/UserManualScreen.kt`
- Modify: `app/src/main/java/com/expense/tracker/ui/chat/InputBar.kt`
- Modify: `app/src/main/java/com/expense/tracker/ui/chat/MessageActionSheet.kt`
- Modify: `app/src/main/java/com/expense/tracker/ui/history/HistoryScreen.kt`
- Modify: `app/src/main/java/com/expense/tracker/ui/history/DeletedItemsScreen.kt`
- Modify: `app/src/main/java/com/expense/tracker/ui/history/ExpenseEditDialog.kt`
- Modify: `app/src/main/java/com/expense/tracker/ui/template/CategoryBubbleDialog.kt`

- [ ] **Step 1: Replace analysis card surfaces**

Use `AppColors.CardBg` for:

- `AnalyticsScreen` summary card.
- Both empty and populated `CategoryBreakdownList` containers.
- `InsightsScreen` result and bottom-summary cards.

Do not replace page-root backgrounds.

- [ ] **Step 2: Replace settings and documentation surfaces**

Use `AppColors.CardBg` for:

- `SettingsMenuScreen.MenuRow`.
- `DataExportScreen.DataOption`.
- `ThemePickerDialog` dialog container.
- `UserManualScreen.ManualSection`.

Keep all full-screen roots on `AppColors.Bg`.

- [ ] **Step 3: Replace chat and dialog surfaces**

Use `AppColors.CardBg` for:

- `InputBar` rounded input container.
- `MessageActionSheet` bottom sheet.
- `ExpenseEditDialog` main dialog container.
- `CategoryBubbleDialog` main dialog container.

Keep black translucent backdrop overlays unchanged.

- [ ] **Step 4: Replace history surfaces**

Use `AppColors.CardBg` for:

- `HistoryScreen` date-group card.
- `DeletedItemsScreen` item card.
- Non-today `MonthCalendarView` cells.

Keep swipe-row opaque backgrounds on `AppColors.Bg` because they mask the red delete layer and are not raised cards.

- [ ] **Step 5: Compile all component replacements**

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat compileDebugKotlin --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/expense/tracker/ui
git commit -m "fix: unify raised card surfaces across themes"
```

### Task 4: Run three audits and deliver the APK

**Files:**
- Build: `app/build/outputs/apk/debug/app-debug.apk`
- Deliver: `C:\Users\fuker\Desktop\app\codex change\ai-expense-bot-v3.6-theme-card-audit-2026-07-30.apk`

- [ ] **Step 1: Hardcoded-color audit**

```powershell
rg -n "Color\.White|Color\.Black|Color\(0x" app/src/main/java/com/expense/tracker/ui
```

Expected remaining uses:

- white on blue send and red delete controls;
- black translucent modal backdrops and shadows;
- restore/delete/error colors;
- fixed analytics category colors.

No card container may use `Color.White`.

- [ ] **Step 2: Card-component audit**

```powershell
rg -n "Card\(|Surface\(|softShadow\(|background\(AppColors\.Bg\)|background\(AppColors\.CardBg\)" app/src/main/java/com/expense/tracker/ui
```

Review every result. Full-screen roots and delete-mask rows may use `Bg`; raised cards, dialog bodies, input containers, settings rows, and list cards must use `CardBg`.

- [ ] **Step 3: Semantic-pair audit**

```powershell
rg -n "background\(AppColors\.TextPrimary\)|containerColor = AppColors\.TextPrimary|Color\.White" app/src/main/java/com/expense/tracker/ui -C 3
```

Every `TextPrimary` fill must pair with `AppColors.Bg`. Exceptions are not allowed.

- [ ] **Step 4: Run all tests with the real CSV fixture and build**

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:CSV_IMPORT_FIXTURE='H:\VX\xwechat_files\wxid_3mf0rejn5stn22_e473\msg\file\2026-07\expense-tracker-export-20260726-075740.csv'
$env:CSV_IMPORT_EXPECTED_ROWS='128'
.\gradlew.bat testDebugUnitTest assembleDebug --console=plain
```

Expected: `BUILD SUCCESSFUL`, zero skipped/failing tests, and the real fixture parses 128 rows.

- [ ] **Step 5: Verify and copy**

Verify metadata and signature:

```powershell
& 'C:\Users\fuker\AppData\Local\Android\Sdk\build-tools\34.0.0\aapt.exe' dump badging 'app\build\outputs\apk\debug\app-debug.apk'
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
& 'C:\Users\fuker\AppData\Local\Android\Sdk\build-tools\34.0.0\apksigner.bat' verify --verbose --print-certs 'app\build\outputs\apk\debug\app-debug.apk'
```

Expected: package `com.expense.tracker`, version code `27`, version name `3.6`, `Verifies`, and v2 verification is true.

Copy and hash:

```powershell
$sourceApk='C:\Users\fuker\Desktop\app\account app\expense-tracker\app\build\outputs\apk\debug\app-debug.apk'
$desktopApk='C:\Users\fuker\Desktop\app\codex change\ai-expense-bot-v3.6-theme-card-audit-2026-07-30.apk'
$outputApk='C:\Users\fuker\Documents\Codex\2026-07-29\c-users-fuker-desktop-app-ai\outputs\ai-expense-bot-v3.6-theme-card-audit-2026-07-30.apk'
Copy-Item -LiteralPath $sourceApk -Destination $desktopApk -Force
Copy-Item -LiteralPath $sourceApk -Destination $outputApk -Force
Get-FileHash -Algorithm SHA256 $sourceApk,$desktopApk,$outputApk
```

Expected: all three SHA-256 values are identical.

- [ ] **Step 6: Confirm a clean repository**

```powershell
git diff --check
git status --short
git log -8 --oneline
```

Expected: no uncommitted changes and the implementation commits follow the design and plan commits.
