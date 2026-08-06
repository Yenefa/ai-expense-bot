param(
    [string]$ProjectRoot
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
}

$mainSourceRoot = Join-Path $ProjectRoot 'app\src\main'
$safeLoggerPath = Join-Path $ProjectRoot 'app\src\main\java\com\expense\tracker\util\PrivacySafeLog.kt'
$coordinatorPath = Join-Path $ProjectRoot 'app\src\main\java\com\expense\tracker\llm\ChatLlmCoordinator.kt'
$appGradlePath = Join-Path $ProjectRoot 'app\build.gradle.kts'

if (-not (Test-Path -LiteralPath $safeLoggerPath)) {
    throw 'Missing privacy-safe logger: app\src\main\java\com\expense\tracker\util\PrivacySafeLog.kt'
}

$safeLoggerResolved = (Resolve-Path -LiteralPath $safeLoggerPath).Path
$kotlinSources = Get-ChildItem -LiteralPath $mainSourceRoot -Recurse -File -Filter '*.kt'
foreach ($sourceFile in $kotlinSources) {
    $sourceText = Get-Content -LiteralPath $sourceFile.FullName -Raw -Encoding utf8
    if ($sourceFile.FullName -ne $safeLoggerResolved) {
        if ($sourceText -match 'import\s+android\.util\.Log' -or $sourceText -match '\bLog\.(d|i|v|w|e|wtf)\s*\(') {
            $relativePath = $sourceFile.FullName.Substring($ProjectRoot.Length + 1)
            throw "Direct Android logging is forbidden outside PrivacySafeLog.kt: $relativePath"
        }
    }
    if ($sourceText -match '\bprintln\s*\(' -or $sourceText -match '\bprintStackTrace\s*\(') {
        $relativePath = $sourceFile.FullName.Substring($ProjectRoot.Length + 1)
        throw "Console or stack-trace logging is forbidden in application source: $relativePath"
    }
}

$safeLoggerSource = Get-Content -LiteralPath $safeLoggerPath -Raw -Encoding utf8
$requiredLoggerFragments = @(
    'import android.util.Log',
    'import com.expense.tracker.BuildConfig',
    'fun llmRequestStarted(recentRecordCount: Int)',
    'fun llmResponseParsed(expenseCount: Int, actionCount: Int)',
    'fun llmRequestFailed()',
    'if (BuildConfig.DEBUG) runCatching { Log.d(TAG, message()) }'
)
foreach ($fragment in $requiredLoggerFragments) {
    if (-not $safeLoggerSource.Contains($fragment)) {
        throw "PrivacySafeLog.kt is missing required contract fragment: $fragment"
    }
}

if ($safeLoggerSource -match 'fun\s+llm\w+\s*\([^)]*(String|Throwable|Exception|ExpenseEntity|UserPrefs)') {
    throw 'Privacy-safe event methods must not accept sensitive strings, exceptions, entities, or preferences'
}

$appGradleSource = Get-Content -LiteralPath $appGradlePath -Raw -Encoding utf8
if (-not $appGradleSource.Contains('buildConfig = true')) {
    throw 'app/build.gradle.kts must enable BuildConfig for the Debug-only logging guard'
}

$coordinatorSource = Get-Content -LiteralPath $coordinatorPath -Raw -Encoding utf8
$requiredCallSites = @(
    'PrivacySafeLog.llmRequestStarted(contextRecords.size)',
    'PrivacySafeLog.llmResponseParsed(parsed.expenses.size, parsed.actions.size)',
    'PrivacySafeLog.llmRequestFailed()'
)
foreach ($fragment in $requiredCallSites) {
    if (-not $coordinatorSource.Contains($fragment)) {
        throw "ChatLlmCoordinator.kt is missing privacy-safe event call: $fragment"
    }
}

Write-Output 'Privacy logging contract: PASS'
