param(
    [string]$ProjectRoot
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
}

$mainActivityPath = Join-Path $ProjectRoot 'app\src\main\java\com\expense\tracker\MainActivity.kt'
$source = Get-Content -LiteralPath $mainActivityPath -Raw -Encoding utf8

$required = @(
    'import androidx.compose.runtime.saveable.rememberSaveable',
    'var screen by rememberSaveable { mutableStateOf(Screen.Chat) }',
    'var subScreen by rememberSaveable { mutableStateOf<SubScreen?>(null) }',
    'private enum class Screen(val rank: Int)',
    'Chat(0)',
    'Analytics(1)',
    'History(1)',
    'Settings(1)',
    'private enum class SubScreen',
    'Subscription,',
    'visible = subScreen == SubScreen.Subscription',
    'SubscriptionScreen('
)
foreach ($fragment in $required) {
    if (-not $source.Contains($fragment)) {
        throw "MainActivity navigation state contract is missing: $fragment"
    }
}

$settingsMenuPath = Join-Path $ProjectRoot 'app\src\main\java\com\expense\tracker\ui\settings\SettingsMenuScreen.kt'
$settingsMenuSource = Get-Content -LiteralPath $settingsMenuPath -Raw -Encoding utf8
foreach ($fragment in @('onOpenSubscription:', 'onClick = onOpenSubscription')) {
    if (-not $settingsMenuSource.Contains($fragment)) {
        throw "Settings subscription navigation contract is missing: $fragment"
    }
}

if ($source.Contains('var screen by remember {') -or $source.Contains('var subScreen by remember {')) {
    throw 'Navigation destinations must not use ordinary remember state'
}

Write-Output 'Navigation state restoration: PASS'
