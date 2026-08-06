param(
    [string]$ProjectRoot,
    [string]$ApkPath
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
}

$jwtPattern = 'eyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}'
$bearerJwtPattern = 'Bearer\s+eyJ[A-Za-z0-9_-]{20,}'
$directAiGatewayPattern = 'api\.tcloudbasegateway\.com/v1/ai/cloudbase'
$sourceRoots = @(
    (Join-Path $ProjectRoot 'app\src'),
    (Join-Path $ProjectRoot 'docs'),
    (Join-Path $ProjectRoot 'tools')
)

foreach ($root in $sourceRoots) {
    if (-not (Test-Path -LiteralPath $root)) { continue }
    Get-ChildItem -LiteralPath $root -Recurse -File |
        Where-Object { $_.FullName -ne $PSCommandPath } |
        ForEach-Object {
            $text = Get-Content -LiteralPath $_.FullName -Raw -Encoding utf8 -ErrorAction SilentlyContinue
            if ($null -ne $text -and ($text -match $jwtPattern -or $text -match $bearerJwtPattern)) {
                throw "Credential-shaped literal found in source: $($_.FullName)"
            }
        }
}

$mainSource = Join-Path $ProjectRoot 'app\src\main'
Get-ChildItem -LiteralPath $mainSource -Recurse -File | ForEach-Object {
    $text = Get-Content -LiteralPath $_.FullName -Raw -Encoding utf8 -ErrorAction SilentlyContinue
    if ($null -ne $text -and $text -match $directAiGatewayPattern) {
        throw "Direct CloudBase AI gateway found in Android source: $($_.FullName)"
    }
}

$buildPath = Join-Path $ProjectRoot 'app\build.gradle.kts'
$buildText = Get-Content -LiteralPath $buildPath -Raw -Encoding utf8
if (-not $buildText.Contains('YE_COST_SUBSCRIPTION_API_BASE_URL')) {
    throw 'Server-side subscription endpoint build property is missing'
}
if (-not $buildText.Contains('SUBSCRIPTION_API_BASE_URL')) {
    throw 'Subscription BuildConfig field is missing'
}

if (-not [string]::IsNullOrWhiteSpace($ApkPath)) {
    $resolvedApk = (Resolve-Path -LiteralPath $ApkPath).Path
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($resolvedApk)
    try {
        foreach ($entry in $archive.Entries) {
            if ($entry.Length -le 0 -or $entry.Length -gt 80MB) { continue }
            $stream = $entry.Open()
            try {
                $reader = [System.IO.StreamReader]::new(
                    $stream,
                    [System.Text.Encoding]::GetEncoding(28591),
                    $false
                )
                try {
                    $content = $reader.ReadToEnd()
                    if (
                        $content -match $jwtPattern -or
                        $content -match $bearerJwtPattern -or
                        $content -match $directAiGatewayPattern
                    ) {
                        throw "Credential-shaped literal found in APK entry: $($entry.FullName)"
                    }
                }
                finally {
                    $reader.Dispose()
                }
            }
            finally {
                $stream.Dispose()
            }
        }
    }
    finally {
        $archive.Dispose()
    }
}

Write-Output 'Subscription credential scan: PASS'
