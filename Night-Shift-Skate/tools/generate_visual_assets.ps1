param(
    [string]$SourceArtwork = (Join-Path $PSScriptRoot '../artwork/deck-print.png')
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$resourceRoot = Join-Path $PSScriptRoot '../src/main/resources/assets/night_shift_skate/textures'
$entityFolder = Join-Path $resourceRoot 'entity'
New-Item -ItemType Directory -Force -Path $entityFolder | Out-Null

function Hex([string]$value) {
    [Drawing.ColorTranslator]::FromHtml($value)
}

# Atlas layout: upper 64 pixels are the exact underside artwork. The lower half contains
# fixed material chips sampled by the procedural runtime mesh.
$atlas = [Drawing.Bitmap]::new(256, 256, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
$canvas = [Drawing.Graphics]::FromImage($atlas)
$canvas.Clear([Drawing.Color]::Transparent)
$canvas.CompositingMode = [Drawing.Drawing2D.CompositingMode]::SourceCopy
$canvas.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$artwork = [Drawing.Image]::FromFile((Resolve-Path -LiteralPath $SourceArtwork))
$canvas.DrawImage($artwork, [Drawing.Rectangle]::new(0, 0, 256, 64))

$chips = @('#242329', '#D7A774', '#686A72', '#A3A7AD', '#E7D3AE', '#B43D2E', '#C4C9CD', '#211F25')
for ($chip = 0; $chip -lt $chips.Count; $chip++) {
    $brush = [Drawing.SolidBrush]::new((Hex $chips[$chip]))
    $canvas.FillRectangle($brush, $chip * 32, 128, 32, 64)
    $brush.Dispose()
}

# Soft noise keeps the grip tape and cast aluminium from reading as perfectly flat.
$random = [Random]::new(1977)
for ($dot = 0; $dot -lt 460; $dot++) {
    $chip = if ($dot -lt 300) { 0 } else { 2 }
    $x = $chip * 32 + $random.Next(1, 31)
    $y = $random.Next(130, 190)
    $shade = if ($chip -eq 0) { $random.Next(31, 45) } else { $random.Next(96, 118) }
    $atlas.SetPixel($x, $y, [Drawing.Color]::FromArgb(255, $shade, $shade, $shade))
}
$atlas.Save((Join-Path $entityFolder 'night_shift.png'), [Drawing.Imaging.ImageFormat]::Png)
$artwork.Dispose()
$canvas.Dispose()
$atlas.Dispose()
