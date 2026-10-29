Add-Type -AssemblyName System.Drawing

function Process-LogoImage {
    param(
        [string]$SourcePath,
        [string]$DestPath
    )

    $src = [System.Drawing.Bitmap]::FromFile($SourcePath)
    $w = $src.Width
    $h = $src.Height
    $bmp = New-Object System.Drawing.Bitmap $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.Clear([System.Drawing.Color]::FromArgb(0, 0, 0, 0))
    $g.DrawImage($src, 0, 0, $w, $h)
    $g.Dispose()
    $src.Dispose()

    $rect = New-Object System.Drawing.Rectangle 0, 0, $w, $h
    $data = $bmp.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadWrite, $bmp.PixelFormat)
    $stride = $data.Stride
    $len = $stride * $h
    $bytes = New-Object byte[] $len
    [Runtime.InteropServices.Marshal]::Copy($data.Scan0, $bytes, 0, $len)

    $visited = New-Object bool[] ($w * $h)
    $q = New-Object System.Collections.Generic.Queue[int]

    function Is-Background([int]$x, [int]$y) {
        $i = $y * $stride + $x * 4
        if (($i + 3) -ge $len) { return $false }
        $b = $bytes[$i]
        $g = $bytes[$i + 1]
        $r = $bytes[$i + 2]
        $a = $bytes[$i + 3]
        if ($a -lt 8) { return $true }
        $light = ($r -gt 235 -and $g -gt 235 -and $b -gt 235)
        $dark = ($r -lt 28 -and $g -lt 28 -and $b -lt 28)
        return $light -or $dark
    }

    function Enqueue-IfBg([int]$x, [int]$y) {
        if ($x -lt 0 -or $y -lt 0 -or $x -ge $w -or $y -ge $h) { return }
        $p = $y * $w + $x
        if ($visited[$p]) { return }
        if (-not (Is-Background $x $y)) { return }
        $visited[$p] = $true
        $q.Enqueue($p)
    }

    for ($x = 0; $x -lt $w; $x++) {
        Enqueue-IfBg $x 0
        Enqueue-IfBg $x ($h - 1)
    }
    for ($y = 0; $y -lt $h; $y++) {
        Enqueue-IfBg 0 $y
        Enqueue-IfBg ($w - 1) $y
    }

    while ($q.Count -gt 0) {
        $p = $q.Dequeue()
        $x = $p % $w
        $y = [int][math]::Floor($p / $w)
        $i = $y * $stride + $x * 4
        if (($i + 3) -lt $len) { $bytes[$i + 3] = 0 }
        Enqueue-IfBg ($x + 1) $y
        Enqueue-IfBg ($x - 1) $y
        Enqueue-IfBg $x ($y + 1)
        Enqueue-IfBg $x ($y - 1)
    }

    [Runtime.InteropServices.Marshal]::Copy($bytes, 0, $data.Scan0, $len)
    $bmp.UnlockBits($data)

    $minX = $w; $minY = $h; $maxX = 0; $maxY = 0
    for ($y = 0; $y -lt $h; $y++) {
        for ($x = 0; $x -lt $w; $x++) {
            $i = $y * $stride + $x * 4
            if (($i + 3) -lt $len -and $bytes[$i + 3] -gt 8) {
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

    $dir = Split-Path -Parent $DestPath
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    if (Test-Path $DestPath) { Remove-Item $DestPath -Force }
    $cropped.Save($DestPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $cropped.Dispose()
    Write-Host "OK $DestPath (${cw}x${ch})"
}

$root = Split-Path -Parent $PSScriptRoot
$asset = "C:\Users\pdlnc\.cursor\projects\c-Users-pdlnc-AndroidStudioProjects-Diplom\assets\c__Users_pdlnc_AppData_Roaming_Cursor_User_workspaceStorage_73fde8c847aab2c1bcaafa957c6639fc_images_image-09c58295-07ca-4175-80cb-bfe5656b9b31.png"
$targets = @(
    (Join-Path $root "composeApp\src\commonMain\composeResources\drawable\app_logo.png"),
    (Join-Path $root "composeApp\src\androidMain\res\drawable\app_logo.png"),
    (Join-Path $root "composeApp\src\webMain\resources\favicon.png")
)

foreach ($dest in $targets) {
    Process-LogoImage -SourcePath $asset -DestPath $dest
}
