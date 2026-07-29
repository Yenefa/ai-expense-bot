param(
    [Parameter(Mandatory = $true)]
    [string]$SourceImage,
    [string]$ResourcesDir,
    [ValidateRange(0.01, 1.0)]
    [double]$Scale = 0.43
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

if ([string]::IsNullOrWhiteSpace($ResourcesDir)) {
    $ResourcesDir = Join-Path $PSScriptRoot '..\app\src\main\res'
}

if (-not (Test-Path -LiteralPath $SourceImage)) {
    throw "Source image not found: $SourceImage"
}

$background = [System.Drawing.ColorTranslator]::FromHtml('#2B1A3B')
$targets = [ordered]@{
    'drawable-mdpi' = 108
    'drawable-hdpi' = 162
    'drawable-xhdpi' = 216
    'drawable-xxhdpi' = 324
    'drawable-xxxhdpi' = 432
}

$source = [System.Drawing.Bitmap]::FromFile($SourceImage)
try {
    if ($source.Width -ne $source.Height) {
        throw "Source image must be square, got $($source.Width)x$($source.Height)"
    }

    foreach ($target in $targets.GetEnumerator()) {
        $canvasSize = [int]$target.Value
        $artSize = [int][Math]::Round($canvasSize * $Scale)
        $offset = [int][Math]::Floor(($canvasSize - $artSize) / 2)
        $outputDir = Join-Path $ResourcesDir $target.Key
        $outputPath = Join-Path $outputDir 'ic_launcher_foreground.png'

        New-Item -ItemType Directory -Path $outputDir -Force | Out-Null

        $canvas = New-Object System.Drawing.Bitmap(
            $canvasSize,
            $canvasSize,
            [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
        )
        try {
            $graphics = [System.Drawing.Graphics]::FromImage($canvas)
            try {
                $graphics.Clear($background)
                $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceOver
                $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
                $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality

                $destination = New-Object System.Drawing.Rectangle($offset, $offset, $artSize, $artSize)
                $graphics.DrawImage(
                    $source,
                    $destination,
                    0,
                    0,
                    $source.Width,
                    $source.Height,
                    [System.Drawing.GraphicsUnit]::Pixel
                )
            }
            finally {
                $graphics.Dispose()
            }

            $canvas.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)
            Write-Output "$($target.Key): canvas=$canvasSize art=$artSize output=$outputPath"
        }
        finally {
            $canvas.Dispose()
        }
    }
}
finally {
    $source.Dispose()
}
