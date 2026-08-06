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
if ($splashColors.Count -ne 1 -or $splashColors[0].InnerText -cne '#FFFFFF') {
    throw 'Expected splash_background color to be exactly #FFFFFF'
}

$themesRelativePath = 'app\src\main\res\values\themes.xml'
[xml]$themes = Read-ProjectText $themesRelativePath
$startingThemes = @($themes.SelectNodes('/resources/style[@name="Theme.ExpenseTracker.Starting"]'))
if (
    $startingThemes.Count -ne 1 -or
    $startingThemes[0].GetAttribute('parent') -cne 'Theme.SplashScreen.IconBackground'
) {
    throw (
        'Expected Theme.ExpenseTracker.Starting to inherit from ' +
        'Theme.SplashScreen.IconBackground'
    )
}

$expectedThemeItems = [ordered]@{
    'windowSplashScreenBackground' = '@color/splash_background'
    'windowSplashScreenAnimatedIcon' = '@drawable/splash_blank_icon'
    'windowSplashScreenIconBackgroundColor' = '@color/splash_background'
    'postSplashScreenTheme' = '@style/Theme.ExpenseTracker'
    'android:statusBarColor' = '@color/splash_background'
    'android:navigationBarColor' = '@color/splash_background'
    'android:windowLightStatusBar' = 'true'
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
$mainActivityWithoutComments = $mainActivityWithoutComments -replace '\r\n?', "`n"
$requiredSplashImport = (
    'import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen'
)
$activeImports = @(
    [regex]::Matches(
        $mainActivityWithoutComments,
        '(?m)^[ \t]*import[ \t]+[^\r\n]+$'
    ) |
        ForEach-Object { $_.Value.Trim() }
)
if ($activeImports -cnotcontains $requiredSplashImport) {
    throw "Expected active Kotlin import: $requiredSplashImport"
}

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
$splashDependency = 'implementation("androidx.core:core-splashscreen:1.0.1")'
if (
    $gradleWithoutComments.IndexOf(
        $splashDependency,
        [System.StringComparison]::Ordinal
    ) -lt 0
) {
    throw "Expected app/build.gradle.kts to contain $splashDependency"
}

# windowLightNavigationBar was introduced in API 27. Keeping it out of the
# base values resource prevents Android 8.0 (API 26) from resolving an
# unsupported framework attribute during splash theme inflation.
$baseNavigationBarItems = @(
    $startingThemes[0].SelectNodes('item') |
        Where-Object { $_.GetAttribute('name') -ceq 'android:windowLightNavigationBar' }
)
if ($baseNavigationBarItems.Count -ne 0) {
    throw 'Expected android:windowLightNavigationBar to be absent from base theme'
}

[xml]$api27Themes = Read-ProjectText 'app\src\main\res\values-v27\themes.xml'
$api27NavigationBarItems = @(
    $api27Themes.SelectNodes('/resources/style[@name="Theme.ExpenseTracker.Starting"]/item') |
        Where-Object { $_.GetAttribute('name') -ceq 'android:windowLightNavigationBar' }
)
if ($api27NavigationBarItems.Count -ne 1 -or $api27NavigationBarItems[0].InnerText -cne 'true') {
    throw 'Expected API 27 theme item android:windowLightNavigationBar=true'
}

$stringsRelativePath = 'app\src\main\res\values\strings.xml'
[xml]$strings = Read-ProjectText $stringsRelativePath
$expectedStrings = [ordered]@{
    'app_name' = 'Y.E cost'
    'brand_slogan' = '记下日常，看见生活'
}
foreach ($expectedString in $expectedStrings.GetEnumerator()) {
    $nodes = @(
        $strings.SelectNodes('/resources/string') |
            Where-Object { $_.GetAttribute('name') -ceq $expectedString.Key }
    )
    if ($nodes.Count -ne 1 -or $nodes[0].InnerText -cne $expectedString.Value) {
        throw "Expected string $($expectedString.Key)=$($expectedString.Value)"
    }
}

$blankIcon = Read-ProjectText 'app\src\main\res\drawable\splash_blank_icon.xml'
if ($blankIcon -notmatch '#00000000') {
    throw 'Expected splash_blank_icon to be transparent'
}

$brandSplash = Read-ProjectText (
    'app\src\main\java\com\expense\tracker\ui\splash\BrandSplashScreen.kt'
)
$brandSplashTokens = @(
    'Color.White',
    'RoundedCornerShape(48.dp)',
    '.scale(1.42f)',
    'R.string.app_name',
    'R.string.brand_slogan',
    'Color(0xFF211D24)',
    'Color(0xFF77717A)',
    'DisposableEffect(view)',
    'window.statusBarColor = android.graphics.Color.WHITE',
    'window.navigationBarColor = android.graphics.Color.WHITE',
    'isAppearanceLightStatusBars = true',
    'isAppearanceLightNavigationBars = true'
)
foreach ($token in $brandSplashTokens) {
    if ($brandSplash.IndexOf($token, [System.StringComparison]::Ordinal) -lt 0) {
        throw "Expected BrandSplashScreen token: $token"
    }
}

$mainActivityTokens = @(
    'savedInstanceState == null',
    'delay(BRAND_SPLASH_DURATION_MS)',
    'private const val BRAND_SPLASH_DURATION_MS = 850L',
    'splashAlpha.animateTo(',
    'targetValue = 0f',
    'animationSpec = tween(220)',
    'if (showBrandSplash)',
    'BrandSplashScreen(',
    'Modifier.graphicsLayer { alpha = splashAlpha.value }'
)
foreach ($token in $mainActivityTokens) {
    if ($mainActivity.IndexOf($token, [System.StringComparison]::Ordinal) -lt 0) {
        throw "Expected MainActivity brand splash token: $token"
    }
}

$exclusiveSplashPattern = (
    'if\s*\(showBrandSplash\)\s*\{.*?' +
    'BrandSplashScreen\s*\(.*?\)\s*\}\s*else\s*\{'
)
if (-not [regex]::IsMatch(
    $mainActivity,
    $exclusiveSplashPattern,
    [System.Text.RegularExpressions.RegexOptions]::Singleline
)) {
    throw 'Expected brand splash and app UI to be mutually exclusive compositions'
}

$activeBrandPaths = @(
    (Join-Path $ProjectRoot 'app\src\main'),
    (Join-Path $ProjectRoot 'README.md')
)
foreach ($activeBrandPath in $activeBrandPaths) {
    $files = if (Test-Path -LiteralPath $activeBrandPath -PathType Container) {
        Get-ChildItem -LiteralPath $activeBrandPath -File -Recurse
    }
    else {
        Get-Item -LiteralPath $activeBrandPath
    }
    foreach ($file in $files) {
        if ([System.IO.File]::ReadAllText($file.FullName).Contains('记账助手')) {
            throw "Found stale app name in $($file.FullName)"
        }
    }
}

Write-Output 'Brand splash configuration: PASS'
