param(
    [string]$ResourcesDir
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

if ([string]::IsNullOrWhiteSpace($ResourcesDir)) {
    $ResourcesDir = Join-Path $PSScriptRoot '..\app\src\main\res'
}

$background = [System.Drawing.ColorTranslator]::FromHtml('#2B1A3B')
$scale = 0.43
$targets = [ordered]@{
    'drawable-mdpi' = 108
    'drawable-hdpi' = 162
    'drawable-xhdpi' = 216
    'drawable-xxhdpi' = 324
    'drawable-xxxhdpi' = 432
}

foreach ($target in $targets.GetEnumerator()) {
    $path = Join-Path $ResourcesDir "$($target.Key)\ic_launcher_foreground.png"
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Missing launcher resource: $path"
    }

    $bitmap = [System.Drawing.Bitmap]::FromFile($path)
    try {
        $canvasSize = [int]$target.Value
        if ($bitmap.Width -ne $canvasSize -or $bitmap.Height -ne $canvasSize) {
            throw "$($target.Key): expected ${canvasSize}x${canvasSize}, got $($bitmap.Width)x$($bitmap.Height)"
        }

        $artSize = [int][Math]::Round($canvasSize * $scale)
        $left = [int][Math]::Floor(($canvasSize - $artSize) / 2)
        $top = $left
        $right = $left + $artSize
        $bottom = $top + $artSize
        $insideHasArtwork = $false

        for ($y = 0; $y -lt $canvasSize; $y++) {
            for ($x = 0; $x -lt $canvasSize; $x++) {
                $pixel = $bitmap.GetPixel($x, $y)
                $isBackground = (
                    $pixel.R -eq $background.R -and
                    $pixel.G -eq $background.G -and
                    $pixel.B -eq $background.B
                )
                $isInside = $x -ge $left -and $x -lt $right -and $y -ge $top -and $y -lt $bottom

                if (-not $isInside -and -not $isBackground) {
                    throw "$($target.Key): artwork escapes the centered 43% safe box at ($x,$y)"
                }
                if ($isInside -and -not $isBackground) {
                    $insideHasArtwork = $true
                }
            }
        }

        if (-not $insideHasArtwork) {
            throw "$($target.Key): centered safe box contains no artwork"
        }

        Write-Output "$($target.Key): PASS canvas=$canvasSize art=$artSize"
    }
    finally {
        $bitmap.Dispose()
    }
}
