$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sdkRoot = Join-Path $projectRoot '.tools\ffmpeg\ffmpeg-n7.1.5-12-g1fdbca85aa-win64-lgpl-shared-7.1'
$clangRoot = Join-Path $projectRoot '.tools\libclang\clang\native'
$cargoBin = Join-Path $env:USERPROFILE '.cargo\bin'
foreach ($required in @((Join-Path $sdkRoot 'include\libavcodec\avcodec.h'), (Join-Path $clangRoot 'libclang.dll'), (Join-Path $cargoBin 'cargo.exe'))) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Missing build dependency: $required" }
}
$env:FFMPEG_DIR = $sdkRoot
$env:LIBCLANG_PATH = $clangRoot
$env:PATH = (Join-Path $sdkRoot 'bin') + ';' + $cargoBin + ';' + $env:PATH
