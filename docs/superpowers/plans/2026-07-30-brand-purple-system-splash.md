# Brand Purple System Splash Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Android-generated white launch background with the existing `#2B1A3B` brand purple while preserving the current 70% safe-zone icon and avoiding a second slogan screen.

**Architecture:** Add an AndroidX SplashScreen starting theme that owns only `MainActivity` startup, then hands off to the existing application theme. Use a static PowerShell contract test to verify the dependency, XML resources, manifest wiring, and `installSplashScreen()` ordering before building the APK.

**Tech Stack:** Android 14 / API 34, Kotlin, Jetpack Compose, AndroidX Core SplashScreen 1.2.0, Gradle, PowerShell

---

## File map

- Create `tools/test-brand-splash.ps1`: static startup configuration contract.
- Create `app/src/main/res/values/colors.xml`: single shared brand splash color.
- Modify `app/src/main/res/values/themes.xml`: add the isolated starting theme.
- Modify `app/src/main/AndroidManifest.xml`: apply the starting theme to `MainActivity`.
- Modify `app/src/main/java/com/expense/tracker/MainActivity.kt`: install SplashScreen before Activity creation.
- Modify `app/build.gradle.kts`: add the stable AndroidX Core SplashScreen dependency.

No launcher bitmap, Compose screen, database, import, chart, or theme-palette file changes.

### Task 1: Define the startup configuration contract

**Files:**
- Create: `tools/test-brand-splash.ps1`
- Inspect: `app/src/main/res/drawable/ic_launcher_background.xml`

- [ ] **Step 1: Add the failing static test**

Create `tools/test-brand-splash.ps1`:

```powershell
param(
    [string]$ProjectRoot
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
}

function Read-ProjectText {
    param([Parameter(Mandatory = $true)][string]$RelativePath)

    $path = Join-Path $ProjectRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Missing project file: $RelativePath"
    }
    return Get-Content -LiteralPath $path -Raw -Encoding utf8
}

$colorsXml = [xml](Read-ProjectText 'app\src\main\res\values\colors.xml')
$splashColor = @($colorsXml.resources.color) |
    Where-Object { $_.name -eq 'splash_background' } |
    Select-Object -First 1
if ($null -eq $splashColor -or $splashColor.'#text' -ne '#2B1A3B') {
    throw 'splash_background must be #2B1A3B'
}

$themesXml = [xml](Read-ProjectText 'app\src\main\res\values\themes.xml')
$startingTheme = @($themesXml.resources.style) |
    Where-Object { $_.name -eq 'Theme.ExpenseTracker.Starting' } |
    Select-Object -First 1
if ($null -eq $startingTheme) {
    throw 'Missing Theme.ExpenseTracker.Starting'
}
if ($startingTheme.parent -ne 'Theme.SplashScreen') {
    throw 'Starting theme must inherit Theme.SplashScreen'
}

$themeItems = @{}
foreach ($item in @($startingTheme.item)) {
    $themeItems[$item.name] = $item.'#text'
}

$expectedThemeItems = @{
    'windowSplashScreenBackground' = '@color/splash_background'
    'windowSplashScreenAnimatedIcon' = '@mipmap/ic_launcher'
    'windowSplashScreenIconBackgroundColor' = '@color/splash_background'
    'postSplashScreenTheme' = '@style/Theme.ExpenseTracker'
    'android:statusBarColor' = '@color/splash_background'
    'android:navigationBarColor' = '@color/splash_background'
    'android:windowLightStatusBar' = 'false'
    'android:windowLightNavigationBar' = 'false'
}
foreach ($entry in $expectedThemeItems.GetEnumerator()) {
    if ($themeItems[$entry.Key] -ne $entry.Value) {
        throw "Starting theme item $($entry.Key) must be $($entry.Value)"
    }
}

$manifestXml = [xml](Read-ProjectText 'app\src\main\AndroidManifest.xml')
$androidNamespace = 'http://schemas.android.com/apk/res/android'
$mainActivity = @($manifestXml.manifest.application.activity) |
    Where-Object { $_.GetAttribute('name', $androidNamespace) -eq '.MainActivity' } |
    Select-Object -First 1
if ($null -eq $mainActivity) {
    throw 'Missing MainActivity manifest entry'
}
if ($mainActivity.GetAttribute('theme', $androidNamespace) -ne '@style/Theme.ExpenseTracker.Starting') {
    throw 'MainActivity must use Theme.ExpenseTracker.Starting'
}

$mainActivitySource = Read-ProjectText 'app\src\main\java\com\expense\tracker\MainActivity.kt'
$installIndex = $mainActivitySource.IndexOf('installSplashScreen()')
$superIndex = $mainActivitySource.IndexOf('super.onCreate(savedInstanceState)')
if ($installIndex -lt 0) {
    throw 'MainActivity must call installSplashScreen()'
}
if ($superIndex -lt 0 -or $installIndex -gt $superIndex) {
    throw 'installSplashScreen() must run before super.onCreate()'
}

$gradleSource = Read-ProjectText 'app\build.gradle.kts'
if (-not $gradleSource.Contains('implementation("androidx.core:core-splashscreen:1.2.0")')) {
    throw 'Missing AndroidX Core SplashScreen 1.2.0 dependency'
}

Write-Output 'Brand splash configuration: PASS'
```

- [ ] **Step 2: Run the contract and verify RED**

Run:

```powershell
.\tools\test-brand-splash.ps1
```

Expected: FAIL with `Missing project file: app\src\main\res\values\colors.xml`.

- [ ] **Step 3: Commit the failing contract**

```powershell
git add -- tools/test-brand-splash.ps1
git commit -m "test: define brand splash configuration"
```

### Task 2: Add the brand SplashScreen theme

**Files:**
- Create: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/expense/tracker/MainActivity.kt:3-7,80-82`
- Modify: `app/build.gradle.kts:36-50`
- Test: `tools/test-brand-splash.ps1`

- [ ] **Step 1: Add the brand color**

Create `app/src/main/res/values/colors.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="splash_background">#2B1A3B</color>
</resources>
```

- [ ] **Step 2: Add the isolated starting theme**

Replace `app/src/main/res/values/themes.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.ExpenseTracker" parent="android:Theme.Material.Light.NoActionBar" />

    <style name="Theme.ExpenseTracker.Starting" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">@color/splash_background</item>
        <item name="windowSplashScreenAnimatedIcon">@mipmap/ic_launcher</item>
        <item name="windowSplashScreenIconBackgroundColor">@color/splash_background</item>
        <item name="postSplashScreenTheme">@style/Theme.ExpenseTracker</item>
        <item name="android:statusBarColor">@color/splash_background</item>
        <item name="android:navigationBarColor">@color/splash_background</item>
        <item name="android:windowLightStatusBar">false</item>
        <item name="android:windowLightNavigationBar">false</item>
    </style>
</resources>
```

- [ ] **Step 3: Apply the starting theme only to MainActivity**

Update the `MainActivity` entry in `app/src/main/AndroidManifest.xml`:

```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:theme="@style/Theme.ExpenseTracker.Starting">
```

Leave the `<application android:theme="@style/Theme.ExpenseTracker">` assignment unchanged.

- [ ] **Step 4: Install SplashScreen before Activity creation**

Add this import to `MainActivity.kt`:

```kotlin
import androidx.core.splashscreen.installSplashScreen
```

Update `onCreate`:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    setContent {
```

- [ ] **Step 5: Add the verified stable dependency**

Add under the existing Activity dependency in `app/build.gradle.kts`:

```kotlin
implementation("androidx.core:core-splashscreen:1.2.0")
```

The stable version is verified against the AndroidX Core release page dated July 2026.

- [ ] **Step 6: Run the contract and verify GREEN**

Run:

```powershell
.\tools\test-brand-splash.ps1
```

Expected: `Brand splash configuration: PASS`.

- [ ] **Step 7: Verify the launcher safe zone remains unchanged**

Run:

```powershell
.\tools\test-launcher-icon-safe-zone.ps1
```

Expected: five `PASS` lines for mdpi through xxxhdpi.

- [ ] **Step 8: Compile resources and Kotlin**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat compileDebugKotlin --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit the implementation**

```powershell
git add -- app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/java/com/expense/tracker/MainActivity.kt app/src/main/res/values/colors.xml app/src/main/res/values/themes.xml
git commit -m "feat: add brand purple system splash"
```

### Task 3: Run full regression and package verification

**Files:**
- Verify: `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 1: Run full tests with the real CSV fixture and build**

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:CSV_IMPORT_FIXTURE='H:\VX\xwechat_files\wxid_3mf0rejn5stn22_e473\msg\file\2026-07\expense-tracker-export-20260726-075740.csv'
$env:CSV_IMPORT_EXPECTED_ROWS='128'
.\gradlew.bat testDebugUnitTest assembleDebug --console=plain
```

Expected: `BUILD SUCCESSFUL`, zero test failures, zero errors, zero skipped tests, and the user fixture test executes.

- [ ] **Step 2: Verify APK identity**

```powershell
$aaptTool = Get-ChildItem -Path "$env:LOCALAPPDATA\Android\Sdk\build-tools\*\aapt.exe" |
    Sort-Object FullName |
    Select-Object -Last 1
& $aaptTool.FullName dump badging 'app\build\outputs\apk\debug\app-debug.apk' |
    Select-Object -First 3
```

Expected:

```text
package: name='com.expense.tracker' versionCode='27' versionName='3.6'
sdkVersion:'26'
targetSdkVersion:'34'
```

- [ ] **Step 3: Verify APK v2 signature**

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$signTool = Get-ChildItem -Path "$env:LOCALAPPDATA\Android\Sdk\build-tools\*\apksigner.bat" |
    Sort-Object FullName |
    Select-Object -Last 1
& $signTool.FullName verify --verbose --print-certs 'app\build\outputs\apk\debug\app-debug.apk'
```

Expected: `Verified using v2 scheme (APK Signature Scheme v2): true`.

### Task 4: Deliver the v3.6 APK

**Files:**
- Source: `app/build/outputs/apk/debug/app-debug.apk`
- Create: `C:\Users\fuker\Desktop\app\codex change\ai-expense-bot-v3.6-purple-splash-2026-07-30.apk`
- Create: `C:\Users\fuker\Documents\Codex\2026-07-29\c-users-fuker-desktop-app-ai\outputs\ai-expense-bot-v3.6-purple-splash-2026-07-30.apk`

- [ ] **Step 1: Copy the verified APK to both delivery locations**

```powershell
$sourceApk = 'C:\Users\fuker\Desktop\app\account app\expense-tracker\app\build\outputs\apk\debug\app-debug.apk'
$artifactName = 'ai-expense-bot-v3.6-purple-splash-2026-07-30.apk'
$desktopDir = 'C:\Users\fuker\Desktop\app\codex change'
$outputDir = 'C:\Users\fuker\Documents\Codex\2026-07-29\c-users-fuker-desktop-app-ai\outputs'
New-Item -ItemType Directory -Path $desktopDir -Force | Out-Null
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
Copy-Item -LiteralPath $sourceApk -Destination (Join-Path $desktopDir $artifactName) -Force
Copy-Item -LiteralPath $sourceApk -Destination (Join-Path $outputDir $artifactName) -Force
```

- [ ] **Step 2: Verify all three SHA-256 hashes match**

```powershell
$pathsToCheck = @(
    $sourceApk,
    (Join-Path $desktopDir $artifactName),
    (Join-Path $outputDir $artifactName)
)
Get-FileHash -Algorithm SHA256 -LiteralPath $pathsToCheck
```

Expected: all three hash values are identical.

- [ ] **Step 3: Record the device-test limitation**

Run:

```powershell
$adbTool = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $adbTool devices -l
```

If no device serial is listed, report that the APK configuration and build are verified but the exact OEM splash rendering still requires installation on the user's phone.
