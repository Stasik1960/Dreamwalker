param([string]$PreviewPath)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$assetRoot = Join-Path $PSScriptRoot '../src/main/resources/assets/night_shift_skate'
$output = Join-Path $assetRoot 'textures/gui'
New-Item -ItemType Directory -Path $output -Force | Out-Null

function Tone([string]$Hex) {
    [Drawing.ColorTranslator]::FromHtml($Hex)
}

function Polygon-Path([float[]]$Coordinates) {
    $points = for ($i = 0; $i -lt $Coordinates.Length; $i += 2) {
        [Drawing.PointF]::new($Coordinates[$i], $Coordinates[$i + 1])
    }
    $shape = [Drawing.Drawing2D.GraphicsPath]::new()
    $shape.AddPolygon([Drawing.PointF[]]$points)
    $shape
}

function New-Plate([int]$Width, [int]$Height, [float[]]$Outline, [string]$Name, [string]$Top, [string]$Bottom) {
    $image = [Drawing.Bitmap]::new($Width * 4, $Height * 4, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $canvas = [Drawing.Graphics]::FromImage($image)
    $canvas.ScaleTransform(4, 4)
    $canvas.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $shape = Polygon-Path $Outline
    $fill = [Drawing.Drawing2D.LinearGradientBrush]::new([Drawing.PointF]::new(0, 0),
        [Drawing.PointF]::new(0, $Height), (Tone $Top), (Tone $Bottom))
    $shadow = [Drawing.Pen]::new([Drawing.Color]::FromArgb(205, 6, 3, 8), 3.5)
    $bevel = [Drawing.Pen]::new((Tone '#8f7052'), 0.85)
    $etch = [Drawing.Pen]::new((Tone '#614b42'), 0.50)
    $ornament = [Drawing.Pen]::new((Tone '#a68a65'), 0.63)
    $gold = [Drawing.SolidBrush]::new((Tone '#bd9c71'))
    $garnet = [Drawing.SolidBrush]::new((Tone '#842e44'))

    $canvas.DrawPath($shadow, $shape)
    $canvas.FillPath($fill, $shape)
    $state = $canvas.Save()
    $canvas.SetClip($shape)
    $noise = [Random]::new(1997)
    $speckle = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(19, 219, 191, 147))
    for ($i = 0; $i -lt 520; $i++) {
        $canvas.FillEllipse($speckle, [float]($noise.NextDouble() * $Width), [float]($noise.NextDouble() * $Height),
            [float](0.15 + $noise.NextDouble() * .67), [float](0.15 + $noise.NextDouble() * .46))
    }
    $canvas.Restore($state)
    $speckle.Dispose()

    $canvas.DrawPath($bevel, $shape)
    $state = $canvas.Save()
    $canvas.TranslateTransform(3, 3)
    $canvas.ScaleTransform(($Width - 6) / [float]$Width, ($Height - 6) / [float]$Height)
    $canvas.DrawPath($etch, $shape)
    $canvas.Restore($state)

    foreach ($mirrored in @($false, $true)) {
        $side = $canvas.Save()
        if ($mirrored) {
            $canvas.TranslateTransform($Width, 0)
            $canvas.ScaleTransform(-1, 1)
        }
        $mid = [float]($Height * .47)
        $canvas.DrawBezier($ornament, 8, $mid, 16, ($mid - 2), 14, ($mid - 11), 26, ($mid - 13))
        $canvas.DrawBezier($etch, 8, $mid, 16, ($mid + 2), 14, ($mid + 11), 26, ($mid + 13))
        $canvas.DrawBezier($ornament, 16, $mid, 21, ($mid - 2), 27, ($mid - 9), 33, ($mid - 7))
        $canvas.DrawBezier($ornament, 16, $mid, 21, ($mid + 2), 27, ($mid + 9), 33, ($mid + 7))
        $spike = Polygon-Path @(9,$mid, 12,($mid-2), 15,$mid, 12,($mid+2))
        $canvas.FillPath($gold, $spike)
        $spike.Dispose()
        $canvas.Restore($side)
    }

    if ($Name -eq 'score_panel.png') {
        $canvas.DrawLine($etch, 34, 39, 174, 39)
        $canvas.DrawLine($etch, 104, 12, 104, 34)
        $seal = Polygon-Path @(100,24, 104,19, 108,24, 104,29)
        $canvas.FillPath($garnet, $seal)
        $canvas.DrawPath($ornament, $seal)
        $seal.Dispose()
    } else {
        $canvas.DrawLine($etch, 57, 13, 57, 35)
    }

    $image.Save((Join-Path $output $Name), [Drawing.Imaging.ImageFormat]::Png)
    foreach ($resource in @($fill, $shadow, $bevel, $etch, $ornament, $gold, $garnet, $shape, $canvas, $image)) {
        $resource.Dispose()
    }
}

New-Plate 208 62 @(3,28, 12,19, 22,18, 27,10, 39,8, 74,8, 88,4, 120,4, 134,8, 169,8, 181,10,
    186,18, 196,19, 205,28, 198,40, 188,42, 183,52, 170,55, 148,55, 143,59, 65,59, 60,55, 38,55, 25,52,
    20,42, 10,40) 'score_panel.png' '#221e21' '#210d14'
New-Plate 144 48 @(3,22, 13,15, 22,14, 29,7, 48,7, 56,3, 88,3, 96,7, 115,7, 122,14, 131,15, 141,22,
    133,32, 123,33, 116,40, 97,40, 88,45, 56,45, 47,40, 28,40, 21,33, 11,32) 'rank_plate.png' '#282025' '#2d0e19'

if ($PreviewPath) {
    $fonts = [Drawing.Text.PrivateFontCollection]::new()
    $fonts.AddFontFile((Join-Path $assetRoot 'font/cormorant_garamond_bold.ttf'))
    $preview = [Drawing.Bitmap]::new(1040, 510)
    $canvas = [Drawing.Graphics]::FromImage($preview)
    $canvas.Clear((Tone '#161419'))
    $canvas.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $canvas.TextRenderingHint = [Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $canvas.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $frame = [Drawing.Image]::FromFile((Join-Path $output 'score_panel.png'))
    $award = [Drawing.Image]::FromFile((Join-Path $output 'rank_plate.png'))
    $canvas.DrawImage($frame, 104, 54, 832, 248)
    $canvas.DrawImage($award, 232, 306, 576, 192)

    function Write-Sample([string]$Value, [int]$X, [int]$Y, [int]$Size, [string]$Hex) {
        $font = [Drawing.Font]::new($fonts.Families[0], $Size, [Drawing.FontStyle]::Bold, [Drawing.GraphicsUnit]::Pixel)
        $brush = [Drawing.SolidBrush]::new((Tone $Hex))
        $align = [Drawing.StringFormat]::new()
        $align.Alignment = [Drawing.StringAlignment]::Center
        $canvas.DrawString($Value, $font, $brush, [Drawing.PointF]::new($X, $Y), $align)
        $align.Dispose()
        $brush.Dispose()
        $font.Dispose()
    }

    Write-Sample 'СЧЁТ' 332 94 30 '#c5ad8c'
    Write-Sample 'КОМБО' 708 94 30 '#c5ad8c'
    Write-Sample '12 480' 332 133 68 '#f1dfbf'
    Write-Sample '+1 680' 708 133 68 '#f1dfbf'
    Write-Sample '3,6 с' 520 212 31 '#d3b7a1'
    $rim = [Drawing.SolidBrush]::new((Tone '#755345'))
    $track = [Drawing.SolidBrush]::new((Tone '#1f1119'))
    $blood = [Drawing.Drawing2D.LinearGradientBrush]::new([Drawing.Rectangle]::new(324,258,282,12),
        (Tone '#d25c5b'), (Tone '#7b263d'), 90.0)
    $canvas.FillRectangle($rim, 320, 254, 400, 20)
    $canvas.FillRectangle($track, 324, 258, 392, 12)
    $canvas.FillRectangle($blood, 324, 258, 282, 12)
    Write-Sample 'S' 399 342 100 '#e7a477'
    Write-Sample '+1 680' 616 345 68 '#f1dfbf'
    Write-Sample 'ЗАЧТЕНО' 616 418 30 '#bd9b80'
    $preview.Save($PreviewPath, [Drawing.Imaging.ImageFormat]::Png)
    foreach ($resource in @($blood, $track, $rim, $award, $frame, $canvas, $preview, $fonts)) { $resource.Dispose() }
}
