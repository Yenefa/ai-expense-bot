<#
.SYNOPSIS
    Backup boundary contract check (thin wrapper).

.DESCRIPTION
    The single source of truth for this contract is the JVM test
    app/src/test/java/com/expense/tracker/privacy/BackupPolicyTest.kt, which runs in CI.
    This script only invokes that test so the historical
    ".\tools\test-api-key-backup-rules.ps1" entry point keeps working.
    Do NOT maintain a second copy of the expectations here.
#>
param(
    [string]$ProjectRoot
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
}

$isWindowsHost = ($env:OS -eq "Windows_NT") -or $IsWindows
$gradleWrapper = if ($isWindowsHost) { ".\gradlew.bat" } else { "./gradlew" }

Push-Location $ProjectRoot
try {
    & $gradleWrapper :app:testDebugUnitTest --tests "com.expense.tracker.privacy.BackupPolicyTest" --console=plain
    if ($LASTEXITCODE -ne 0) {
        throw "BackupPolicyTest failed (exit $LASTEXITCODE)"
    }
}
finally {
    Pop-Location
}

Write-Output 'Backup rules contract: PASS (BackupPolicyTest is the single source of truth)'
