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

# Credentials and local prefs: excluded from cloud backup AND device transfer (all API levels).
$credentialExclusions = @(
    @{ domain = 'sharedpref'; path = 'secure_api_key.xml' },
    @{ domain = 'sharedpref'; path = 'secure_subscription_credential.xml' },
    @{ domain = 'file'; path = 'datastore/user_prefs.preferences_pb' },
    @{ domain = 'file'; path = 'datastore/subscription_prefs.preferences_pb' },
    @{ domain = 'file'; path = 'datastore/user_profile_prefs.preferences_pb' },
    @{ domain = 'file'; path = 'datastore/proactive_prefs.preferences_pb' }
)

# Financial data (v3.15.1): must never enter cloud backup; Android 12+ device transfer keeps it.
$financialExclusions = @(
    @{ domain = 'database'; path = 'expense.db' },
    @{ domain = 'database'; path = 'expense.db-wal' },
    @{ domain = 'database'; path = 'expense.db-shm' },
    @{ domain = 'database'; path = 'expense.db-journal' },
    @{ domain = 'file'; path = 'datastore/budget_prefs.preferences_pb' }
)

function Assert-Exclusions {
    param(
        [Parameter(Mandatory = $true)][object[]]$Nodes,
        [Parameter(Mandatory = $true)][string]$Scope,
        [Parameter(Mandatory = $true)][object[]]$Required
    )

    foreach ($item in $Required) {
        $found = @(@($Nodes) | Where-Object {
            $_.domain -eq $item.domain -and $_.path -eq $item.path
        })
        if ($found.Count -ne 1) {
            throw "$Scope must exclude domain=$($item.domain) path=$($item.path) exactly once"
        }
    }
}

# Reverse assertion: financial data must NOT creep back into device transfer.
function Assert-NotExcluded {
    param(
        [Parameter(Mandatory = $true)][object[]]$Nodes,
        [Parameter(Mandatory = $true)][string]$Scope,
        [Parameter(Mandatory = $true)][object[]]$Forbidden
    )

    foreach ($item in $Forbidden) {
        $found = @(@($Nodes) | Where-Object {
            $_.domain -eq $item.domain -and $_.path -eq $item.path
        })
        if ($found.Count -ne 0) {
            throw "$Scope must NOT exclude domain=$($item.domain) path=$($item.path) (financial data stays transferable)"
        }
    }
}

$legacyRules = Read-XmlFile 'app\src\main\res\xml\backup_rules.xml'
if ($legacyRules.DocumentElement.Name -ne 'full-backup-content') {
    throw 'backup_rules.xml root must be full-backup-content'
}
$legacyNodes = @($legacyRules.'full-backup-content'.exclude)
Assert-Exclusions -Nodes $legacyNodes -Scope 'Android 8-11 backup rules' -Required ($credentialExclusions + $financialExclusions)

$modernRules = Read-XmlFile 'app\src\main\res\xml\data_extraction_rules.xml'
if ($modernRules.DocumentElement.Name -ne 'data-extraction-rules') {
    throw 'data_extraction_rules.xml root must be data-extraction-rules'
}
$cloudNodes = @($modernRules.'data-extraction-rules'.'cloud-backup'.exclude)
$transferNodes = @($modernRules.'data-extraction-rules'.'device-transfer'.exclude)
Assert-Exclusions -Nodes $cloudNodes -Scope 'Android 12+ cloud backup rules' -Required ($credentialExclusions + $financialExclusions)
Assert-Exclusions -Nodes $transferNodes -Scope 'Android 12+ device transfer rules' -Required $credentialExclusions
Assert-NotExcluded -Nodes $transferNodes -Scope 'Android 12+ device transfer rules' -Forbidden $financialExclusions

Write-Output 'Backup rules (credentials + financial data): PASS'
