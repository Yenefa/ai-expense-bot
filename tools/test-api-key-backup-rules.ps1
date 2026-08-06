param(
    [string]$ProjectRoot
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
}

function Read-XmlFile {
    param([Parameter(Mandatory = $true)][string]$RelativePath)

    $path = Join-Path $ProjectRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Missing backup configuration: $RelativePath"
    }
    return [xml](Get-Content -LiteralPath $path -Raw -Encoding utf8)
}

$manifest = Read-XmlFile 'app\src\main\AndroidManifest.xml'
$androidNamespace = 'http://schemas.android.com/apk/res/android'
$application = $manifest.manifest.application
$expectedManifest = @{
    'allowBackup' = 'true'
    'fullBackupContent' = '@xml/backup_rules'
    'dataExtractionRules' = '@xml/data_extraction_rules'
}
foreach ($entry in $expectedManifest.GetEnumerator()) {
    if ($application.GetAttribute($entry.Key, $androidNamespace) -ne $entry.Value) {
        throw "Manifest application android:$($entry.Key) must be $($entry.Value)"
    }
}

function Assert-Exclusions {
    param(
        [Parameter(Mandatory = $true)][object[]]$Nodes,
        [Parameter(Mandatory = $true)][string]$Scope
    )

    $required = @(
        @{ domain = 'sharedpref'; path = 'secure_api_key.xml' },
        @{ domain = 'sharedpref'; path = 'secure_subscription_credential.xml' },
        @{ domain = 'file'; path = 'datastore/user_prefs.preferences_pb' },
        @{ domain = 'file'; path = 'datastore/subscription_prefs.preferences_pb' }
    )
    foreach ($item in $required) {
        $found = @(@($Nodes) | Where-Object {
            $_.domain -eq $item.domain -and $_.path -eq $item.path
        })
        if ($found.Count -ne 1) {
            throw "$Scope must exclude domain=$($item.domain) path=$($item.path) exactly once"
        }
    }
}

$legacyRules = Read-XmlFile 'app\src\main\res\xml\backup_rules.xml'
if ($legacyRules.DocumentElement.Name -ne 'full-backup-content') {
    throw 'backup_rules.xml root must be full-backup-content'
}
Assert-Exclusions -Nodes @($legacyRules.'full-backup-content'.exclude) -Scope 'Android 8-11 backup rules'

$modernRules = Read-XmlFile 'app\src\main\res\xml\data_extraction_rules.xml'
if ($modernRules.DocumentElement.Name -ne 'data-extraction-rules') {
    throw 'data_extraction_rules.xml root must be data-extraction-rules'
}
Assert-Exclusions -Nodes @($modernRules.'data-extraction-rules'.'cloud-backup'.exclude) -Scope 'Android 12+ cloud backup rules'
Assert-Exclusions -Nodes @($modernRules.'data-extraction-rules'.'device-transfer'.exclude) -Scope 'Android 12+ device transfer rules'

Write-Output 'API Key backup rules: PASS'
