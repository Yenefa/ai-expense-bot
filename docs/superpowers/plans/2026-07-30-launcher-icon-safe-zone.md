# Launcher Icon Safe Zone Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a trial v3.5 APK whose full square notebook artwork remains visible inside Android launcher and splash-screen masks.

**Architecture:** Generate each density-specific adaptive foreground as a solid `#2B1A3B` square canvas with the supplied artwork centered at 43% of the canvas width. Keep the existing adaptive-icon XML and application manifest wiring unchanged.

**Tech Stack:** PowerShell, System.Drawing, Android adaptive icon resources, Gradle Wrapper, Android SDK build tools.

---

### Task 1: Add a reproducible safe-zone generator

**Files:**
- Create: `tools/generate-safe-launcher-icons.ps1`
- Modify: `app/src/main/res/drawable-*/ic_launcher_foreground.png`

- [ ] **Step 1: Add a PowerShell generator**

The script accepts the supplied source image and project resource directory, creates 108/162/216/324/432 pixel canvases, fills them with `#2B1A3B`, and draws the source image centered at 43% scale using high-quality bicubic interpolation.

- [ ] **Step 2: Generate all five resources**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File tools/generate-safe-launcher-icons.ps1 -SourceImage "C:\Users\fuker\Desktop\app\ChatGPT Image 2026年7月29日 23_13_35.png"
```

Expected: five PNG files with artwork widths of 46, 70, 93, 139, and 186 pixels.

- [ ] **Step 3: Inspect the mdpi preview**

Expected: the entire notebook image is centered with a deep-purple safety margin on every side.

### Task 2: Verify resources and build

**Files:**
- Verify: `app/src/main/res/drawable-*/ic_launcher_foreground.png`
- Produce: `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 1: Verify dimensions and center placement**

Inspect all generated PNGs with System.Drawing. Expected canvas dimensions are 108, 162, 216, 324, and 432 pixels; expected source-image bounding boxes are centered and do not exceed 43% of each canvas.

- [ ] **Step 2: Run unit tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --rerun-tasks --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Build the APK**

Run:

```powershell
.\gradlew.bat assembleDebug --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`.

### Task 3: Deliver and validate the trial APK

**Files:**
- Produce: `C:/Users/fuker/Desktop/app/codex change/ai-expense-bot-v3.5-logo-safezone-test-2026-07-30.apk`

- [ ] **Step 1: Copy the newly built APK**

Copy `app/build/outputs/apk/debug/app-debug.apk` to the trial output path.

- [ ] **Step 2: Validate package metadata and signature**

Run `aapt dump badging` and `apksigner verify --verbose --print-certs`.

Expected: package `com.expense.tracker`, version name `3.5`, launcher icon `res/mipmap-anydpi-v26/ic_launcher.xml`, and a valid v2 signature.

- [ ] **Step 3: Record SHA-256**

Run `Get-FileHash -Algorithm SHA256` on the trial APK and report the digest.
