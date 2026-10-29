Add-Type -AssemblyName System.Drawing

function Process-LogoImage {
    param([string]$Path)

    $src = [System.Drawing.Bitmap]::FromFile($Path)
    $bmp = New-Object System.Drawing.Bitmap $src.Width, $src.Height, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.DrawImage($src, 0, 0, $src.Width, $src.Height)
    $g.Dispose()
    $src.Dispose()

    $w = $bmp.Width
    $h = $bmp.Height
    $visited = New-Object 'bool[]' ($w * $h)
    $queue = New-Object System.Collections.Generic.Queue[object]

    function Is-Background([int]$x, [int]$y) {
        $c = $bmp.GetPixel($x, $y)
        if ($c.A -lt 8) { return $true }
        return ($c.R -gt 235 -and $c.G -gt 235 -and $c.B -gt 235)
    }

    function Enqueue-IfBg([int]$x, [int]$y) {
        if ($x -lt 0 -or $y -lt 0 -or $x -ge $w -or $y -ge $h) { return }
        $p = $y * $w + $x
        if ($visited[$p]) { return }
        if (-not (Is-Background $x $y)) { return }
        $visited[$p] = $true
        $queue.Enqueue(@($x, $y))
    }

    for ($x = 0; $x -lt $w; $x++) {
        Enqueue-IfBg $x 0
        Enqueue-IfBg $x ($h - 1)
    }
    for ($y = 0; $y -lt $h; $y++) {
        Enqueue-IfBg 0 $y
        Enqueue-IfBg ($w - 1) $y
    }

    while ($queue.Count -gt 0) {
        $pt = $queue.Dequeue()
        $x = $pt[0]
        $y = $pt[1]
        $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
        Enqueue-IfBg ($x + 1) $y
        Enqueue-IfBg ($x - 1) $y
        Enqueue-IfBg $x ($y + 1)
        Enqueue-IfBg $x ($y - 1)
    }

    $minX = $w; $minY = $h; $maxX = 0; $maxY = 0
    for ($y = 0; $y -lt $h; $y++) {
        for ($x = 0; $x -lt $w; $x++) {
            if ($bmp.GetPixel($x, $y).A -gt 8) {
                if ($x -lt $minX) { $minX = $x }
                if ($y -lt $minY) { $minY = $y }
                if ($x -gt $maxX) { $maxX = $x }
                if ($y -gt $maxY) { $maxY = $y }
            }
        }
    }

    $cw = $maxX - $minX + 1
    $ch = $maxY - $minY + 1
    $cropped = New-Object System.Drawing.Bitmap $cw, $ch, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $cg = [System.Drawing.Graphics]::FromImage($cropped)
    $cg.DrawImage($bmp, 0, 0, (New-Object System.Drawing.Rectangle $minX, $minY, $cw, $ch), [System.Drawing.GraphicsUnit]::Pixel)
    $cg.Dispose()
    $bmp.Dispose()

    if (Test-Path $Path) { Remove-Item $Path -Force }
    $cropped.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    $cropped.Dispose()
    Write-Host "Updated $Path (${cw}x${ch})"
}

$root = Split-Path -Parent $PSScriptRoot
$targets = @(
    "$root\composeApp\src\commonMain\composeResources\drawable\app_logo.png",
    "$root\composeApp\src\androidMain\res\drawable\app_logo.png",
    "$root\composeApp\src\webMain\resources\favicon.png"
)

foreach ($file in $targets) {
    Process-LogoImage -Path $file
}
