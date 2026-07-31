# Y.E cost White Brand Splash Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the black-corner purple launch screen with a restrained pure-white Y.E cost brand splash and apply the new brand name consistently.

**Architecture:** Keep AndroidX SplashScreen for the OS launch handoff, but render it as a white screen with a transparent icon. Add a focused Compose `BrandSplashScreen` overlay in `MainActivity` that crops and scales the existing logo, displays the name and slogan, then fades away without changing navigation state.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX Core SplashScreen, Android resource XML, PowerShell contract tests, Gradle.

---

### Task 1: Define the failing splash and branding contract

**Files:**
- Modify: `tools/test-brand-splash.ps1`

- [ ] Assert `splash_background=#FFFFFF`, the starting theme uses `@drawable/splash_blank_icon`, and light system-bar flags are true.
- [ ] Assert `strings.xml` contains `app_name=Y.E cost` and `brand_slogan=记下日常，看见生活`.
- [ ] Assert `BrandSplashScreen.kt` contains the white surface, rounded clipping, 1.42 scale, brand name and slogan resources.
- [ ] Assert `MainActivity.kt` displays the brand overlay and removes it after the minimum display interval.
- [ ] Run `powershell -ExecutionPolicy Bypass -File tools/test-brand-splash.ps1`; expect failure against the old purple implementation.

### Task 2: Implement the pure-white system and Compose splash

**Files:**
- Create: `app/src/main/res/drawable/splash_blank_icon.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/java/com/expense/tracker/ui/splash/BrandSplashScreen.kt`
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/expense/tracker/MainActivity.kt`

- [ ] Add a transparent vector drawable for the OS splash icon.
- [ ] Set the launch background and system bars to white with dark icons.
- [ ] Implement a full-screen white Compose brand page with a 220 dp rounded logo viewport, 1.42 image scale, near-black title and neutral-gray slogan.
- [ ] Overlay the brand page on the existing app, keep it for 850 ms, and fade it out over 220 ms.
- [ ] Run the splash contract again; expect `Brand splash configuration: PASS`.

### Task 3: Apply the Y.E cost name consistently

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/expense/tracker/ui/chat/TopBar.kt`
- Modify: `app/src/main/java/com/expense/tracker/ui/chat/ChatScreen.kt`
- Modify: `app/src/main/java/com/expense/tracker/ui/settings/SettingsMenuScreen.kt`
- Modify: `app/src/main/java/com/expense/tracker/ui/settings/UserManualScreen.kt`
- Modify: `app/src/main/java/com/expense/tracker/llm/LlmPrompt.kt`
- Modify: `README.md`

- [ ] Replace active user-facing old-name strings with `Y.E cost` while keeping `applicationId=com.expense.tracker` unchanged.
- [ ] Run `rg -n "记账助手" app/src/main README.md`; expect no matches.

### Task 4: Verify and package

**Files:**
- Output: `app/build/outputs/apk/debug/app-debug.apk`
- Output: `C:/Users/fuker/Desktop/app/codex change/ai-expense-bot-v3.6-ye-cost-white-splash-2026-07-31.apk`

- [ ] Run `gradlew.bat testDebugUnitTest compileDebugAndroidTestKotlin assembleDebug --rerun-tasks`; expect `BUILD SUCCESSFUL`.
- [ ] Run `git diff --check`; expect exit code 0.
- [ ] Verify APK badging reports package `com.expense.tracker`, versionName `3.6`, versionCode `27`.
- [ ] Verify APK Signature Scheme v2 and calculate SHA-256.
- [ ] Copy the freshly built APK to the required `codex change` folder and report the exact path, hash, tests, and lack of connected-device visual verification.
