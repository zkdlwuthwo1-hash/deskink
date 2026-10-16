param([ValidateRange(0, 32)][int]$Monitor = 0)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sdkBin = Join-Path $projectRoot '.tools/ffmpeg/ffmpeg-n7.1.5-12-g1fdbca85aa-win64-lgpl-shared-7.1/bin'
$app = Join-Path $projectRoot 'target/debug/deskink.exe'
if (-not (Test-Path -LiteralPath $app)) { throw 'Build DeskInk first with scripts/build-windows.ps1' }
if (-not (Test-Path -LiteralPath (Join-Path $sdkBin 'avcodec-61.dll'))) { throw 'The local LGPL FFmpeg runtime is missing.' }
$originalPath = $env:PATH
try {
    $env:PATH = $sdkBin + ';' + $env:PATH
    & $app --monitor $Monitor
    if ($LASTEXITCODE -ne 0) { throw "DeskInk exited with code $LASTEXITCODE" }
} finally {
    $env:PATH = $originalPath
}
