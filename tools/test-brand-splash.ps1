param(
    [string]$ProjectRoot
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
}
else {
    $ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
}

function Read-ProjectText {
    param(
        [Parameter(Mandatory)]
        [string]$RelativePath
    )

    $path = Join-Path $ProjectRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Missing project file: $RelativePath"
    }

    [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
}

function Remove-BlockAndLineComments {
    param(
        [Parameter(Mandatory)]
        [string]$Text
    )

    $withoutBlockComments = [regex]::Replace(
        $Text,
        '/\*.*?\*/',
        '',
        [System.Text.RegularExpressions.RegexOptions]::Singleline
    )
    [regex]::Replace($withoutBlockComments, '//[^\r\n]*', '')
}

$colorsRelativePath = 'app\src\main\res\values\colors.xml'
[xml]$colors = Read-ProjectText $colorsRelativePath
$splashColors = @($colors.SelectNodes('/resources/color[@name="splash_background"]'))
if ($splashColors.Count -ne 1 -or $splashColors[0].InnerText -cne '#2B1A3B') {
    throw 'Expected splash_background color to be exactly #2B1A3B'
}

$themesRelativePath = 'app\src\main\res\values\themes.xml'
[xml]$themes = Read-ProjectText $themesRelativePath
$startingThemes = @($themes.SelectNodes('/resources/style[@name="Theme.ExpenseTracker.Starting"]'))
if (
    $startingThemes.Count -ne 1 -or
    $startingThemes[0].GetAttribute('parent') -cne 'Theme.SplashScreen'
) {
    throw 'Expected Theme.ExpenseTracker.Starting to inherit from Theme.SplashScreen'
}

$expectedThemeItems = [ordered]@{
    'windowSplashScreenBackground' = '@color/splash_background'
    'windowSplashScreenAnimatedIcon' = '@mipmap/ic_launcher'
    'windowSplashScreenIconBackgroundColor' = '@color/splash_background'
    'postSplashScreenTheme' = '@style/Theme.ExpenseTracker'
    'android:statusBarColor' = '@color/splash_background'
    'android:navigationBarColor' = '@color/splash_background'
    'android:windowLightStatusBar' = 'false'
    'android:windowLightNavigationBar' = 'false'
}

foreach ($expectedItem in $expectedThemeItems.GetEnumerator()) {
    $themeItems = @(
        $startingThemes[0].SelectNodes('item') |
            Where-Object { $_.GetAttribute('name') -ceq $expectedItem.Key }
    )
    if ($themeItems.Count -ne 1 -or $themeItems[0].InnerText -cne $expectedItem.Value) {
        throw "Expected theme item $($expectedItem.Key)=$($expectedItem.Value)"
    }
}

$manifestRelativePath = 'app\src\main\AndroidManifest.xml'
[xml]$manifest = Read-ProjectText $manifestRelativePath
$androidNamespace = 'http://schemas.android.com/apk/res/android'
$namespaceManager = [System.Xml.XmlNamespaceManager]::new($manifest.NameTable)
$namespaceManager.AddNamespace('android', $androidNamespace)
$applications = @($manifest.SelectNodes('/manifest/application', $namespaceManager))
if (
    $applications.Count -ne 1 -or
    $applications[0].GetAttribute('theme', $androidNamespace) -cne
        '@style/Theme.ExpenseTracker'
) {
    throw 'Expected application to use @style/Theme.ExpenseTracker'
}

$mainActivities = @(
    $manifest.SelectNodes(
        '/manifest/application/activity[@android:name=".MainActivity"]',
        $namespaceManager
    )
)
if (
    $mainActivities.Count -ne 1 -or
    $mainActivities[0].GetAttribute('theme', $androidNamespace) -cne
        '@style/Theme.ExpenseTracker.Starting'
) {
    throw 'Expected .MainActivity to use @style/Theme.ExpenseTracker.Starting'
}

$startingThemeAssignments = @(
    $manifest.SelectNodes(
        '//*[@android:theme="@style/Theme.ExpenseTracker.Starting"]',
        $namespaceManager
    )
)
if ($startingThemeAssignments.Count -ne 1) {
    throw 'Expected exactly one manifest assignment of @style/Theme.ExpenseTracker.Starting'
}

$mainActivityRelativePath = 'app\src\main\java\com\expense\tracker\MainActivity.kt'
$mainActivity = Read-ProjectText $mainActivityRelativePath
$mainActivityWithoutComments = Remove-BlockAndLineComments $mainActivity
$onCreateSplashPattern = (
    'override\s+fun\s+onCreate\s*' +
    '\(\s*savedInstanceState\s*:\s*Bundle\?\s*\)\s*\{\s*' +
    'installSplashScreen\(\)\s*' +
    'super\.onCreate\(savedInstanceState\)'
)
if (
    -not [regex]::IsMatch(
        $mainActivityWithoutComments,
        $onCreateSplashPattern,
        [System.Text.RegularExpressions.RegexOptions]::Singleline
    )
) {
    throw (
        'Expected onCreate to call installSplashScreen() immediately before ' +
        'super.onCreate(savedInstanceState)'
    )
}

$gradleRelativePath = 'app\build.gradle.kts'
$gradle = Read-ProjectText $gradleRelativePath
$gradleWithoutComments = Remove-BlockAndLineComments $gradle
$splashDependency = 'implementation("androidx.core:core-splashscreen:1.2.0")'
if (
    $gradleWithoutComments.IndexOf(
        $splashDependency,
        [System.StringComparison]::Ordinal
    ) -lt 0
) {
    throw "Expected app/build.gradle.kts to contain $splashDependency"
}

Write-Output 'Brand splash configuration: PASS'
