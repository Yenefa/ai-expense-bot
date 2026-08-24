$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$providerPath = Join-Path $root "app\src\main\java\com\expense\tracker\widget\ExpenseWidgetProvider.kt"
$source = Get-Content -LiteralPath $providerPath -Raw -Encoding UTF8

if ($source -match "\brunBlocking\b") {
    throw "ExpenseWidgetProvider must not block the broadcast/main thread with runBlocking."
}
if ($source -match "ExpenseWidgetProvider\(\)\.onUpdate") {
    throw "requestRefresh must send an explicit broadcast instead of constructing the provider."
}
if ($source -notmatch "goAsync\(\)") {
    throw "ExpenseWidgetProvider must keep the broadcast alive with goAsync()."
}
if ($source -notmatch "Dispatchers\.IO") {
    throw "Widget storage reads must run on Dispatchers.IO."
}
if ($source -notmatch "sendBroadcast") {
    throw "requestRefresh must dispatch ACTION_APPWIDGET_UPDATE through Context.sendBroadcast."
}
if ($source -notmatch "private\s+suspend\s+fun\s+readSnapshot") {
    throw "Widget snapshot loading must be suspendable."
}
if ($source -notmatch "finally\s*\{\s*pendingResult\.finish\(\)") {
    throw "ExpenseWidgetProvider must always finish the pending broadcast result."
}
if ($source -notmatch "ACTION_APPWIDGET_UPDATE" -or $source -notmatch "EXTRA_APPWIDGET_IDS") {
    throw "Widget refresh broadcast must include the standard action and widget IDs."
}
if ($source -notmatch "withTimeout") {
    throw "Widget refresh must finish within the broadcast execution window."
}

Write-Output "PASS: widget refresh is asynchronous and broadcast-driven."
