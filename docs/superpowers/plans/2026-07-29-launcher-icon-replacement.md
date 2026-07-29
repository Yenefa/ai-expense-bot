# Launcher Icon Replacement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a verified v3.5 APK whose phone launcher icon uses the supplied purple notebook image.

**Architecture:** Keep the existing Android adaptive-icon XML entry points and provide the supplied artwork as density-specific foreground PNGs. Use the artwork edge color as the adaptive background so OEM masks remain visually continuous.

**Tech Stack:** Android Gradle Plugin, Kotlin/Compose application, Android adaptive icon resources, Gradle Wrapper, Android SDK build tools.

---

### Task 1: Confirm launcher resource wiring

**Files:**
- Inspect: `app/src/main/AndroidManifest.xml`
- Inspect: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Inspect: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`

- [ ] **Step 1: Verify the manifest references both launcher entry points**

Run:

```powershell
rg -n 'android:(icon|roundIcon)' app/src/main/AndroidManifest.xml
```

Expected: `android:icon="@mipmap/ic_launcher"` and `android:roundIcon="@mipmap/ic_launcher_round"`.

- [ ] **Step 2: Verify both adaptive icons use the same foreground and background**

Run:

```powershell
rg -n 'background|foreground' app/src/main/res/mipmap-anydpi-v26
```

Expected: both files reference `@drawable/ic_launcher_background` and `@drawable/ic_launcher_foreground`.

### Task 2: Verify supplied artwork adaptation

**Files:**
- Source: `C:/Users/fuker/Desktop/app/ChatGPT Image 2026年7月29日 23_13_35.png`
- Verify: `app/src/main/res/drawable-mdpi/ic_launcher_foreground.png`
- Verify: `app/src/main/res/drawable-hdpi/ic_launcher_foreground.png`
- Verify: `app/src/main/res/drawable-xhdpi/ic_launcher_foreground.png`
- Verify: `app/src/main/res/drawable-xxhdpi/ic_launcher_foreground.png`
- Verify: `app/src/main/res/drawable-xxxhdpi/ic_launcher_foreground.png`
- Verify: `app/src/main/res/drawable/ic_launcher_background.xml`

- [ ] **Step 1: Check the input is a square PNG**

Run a System.Drawing inspection and expect `1254 x 1254`.

- [ ] **Step 2: Check all foreground resource dimensions**

Run a System.Drawing inspection and expect `108`, `162`, `216`, `324`, and `432` square pixels for mdpi through xxxhdpi.

- [ ] **Step 3: Visually inspect the mdpi foreground**

Expected: the complete purple notebook artwork is present, centered, and not distorted.

- [ ] **Step 4: Check the adaptive background color**

Run:

```powershell
rg -n '#2B1A3B' app/src/main/res/drawable/ic_launcher_background.xml
```

Expected: one vector fill using `#2B1A3B`.

### Task 3: Build and validate the APK

**Files:**
- Build from: project working tree
- Produce: `C:/Users/fuker/Desktop/app/ai-expense-bot-v3.5-logo-2026-07-29.apk`

- [ ] **Step 1: Run unit tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Build the Debug APK**

Run:

```powershell
.\gradlew.bat assembleDebug
```

Expected: `BUILD SUCCESSFUL` and `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 3: Copy the built APK to the requested deliverable location**

Copy `app/build/outputs/apk/debug/app-debug.apk` to `C:/Users/fuker/Desktop/app/ai-expense-bot-v3.5-logo-2026-07-29.apk`.

- [ ] **Step 4: Verify metadata and signature**

Run `aapt dump badging` and `apksigner verify --verbose --print-certs`.

Expected: package `com.expense.tracker`, version name `3.5`, SDK 26–34, adaptive launcher resource present, and APK signature verification succeeds.

- [ ] **Step 5: Record checksum**

Run:

```powershell
Get-FileHash -Algorithm SHA256 C:/Users/fuker/Desktop/app/ai-expense-bot-v3.5-logo-2026-07-29.apk
```

Expected: one SHA-256 digest for the final deliverable.
