param([ValidateRange(0, 32)][int]$Monitor = 0)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$app = Join-Path $projectRoot 'target/release/deskink.exe'
$sdkBin = Join-Path $projectRoot '.tools/ffmpeg/ffmpeg-n7.1.5-12-g1fdbca85aa-win64-lgpl-shared-7.1/bin'
$notice = New-Object -ComObject WScript.Shell
$launchLock = [System.Threading.Mutex]::new($false, 'Local\DeskInkLauncher')
$acquired = $false
$originalPath = $env:PATH
try {
    $acquired = $launchLock.WaitOne(0)
    if (-not $acquired) { return }
    if (Get-Process deskink -ErrorAction SilentlyContinue) {
        [void]$notice.Popup('DeskInk is already running. To switch displays, close it first (current ink will be cleared), then open the other shortcut.', 0, 'DeskInk', 64)
        return
    }
    if (-not (Test-Path -LiteralPath $app)) { throw 'Build DeskInk with scripts/build-windows.ps1 -Release first.' }
    if (-not (Test-Path -LiteralPath (Join-Path $sdkBin 'avcodec-61.dll'))) { throw 'The local LGPL FFmpeg runtime is missing.' }
    $localDir = Join-Path $projectRoot 'artifacts/local'
    [void](New-Item -ItemType Directory -Path $localDir -Force)
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
    $outLog = Join-Path $localDir "deskink-$stamp-stdout.log"
    $errorLog = Join-Path $localDir "deskink-$stamp-stderr.log"
    $env:PATH = $sdkBin + ';' + $env:PATH
    $appProcess = Start-Process -FilePath $app -ArgumentList "--monitor $Monitor" -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput $outLog -RedirectStandardError $errorLog
    $null = $appProcess.Handle
    Start-Sleep -Milliseconds 750
    $appProcess.Refresh()
    if ($appProcess.HasExited) { throw "DeskInk could not start. Details: $errorLog" }
    @{ pid = $appProcess.Id; executable = $app; stdout = $outLog; stderr = $errorLog; monitor = $Monitor } |
        ConvertTo-Json | Set-Content (Join-Path $localDir 'deskink-active.json') -Encoding UTF8
} catch {
    [void]$notice.Popup($_.Exception.Message, 0, 'DeskInk', 16)
    exit 1
} finally {
    $env:PATH = $originalPath
    if ($acquired) { $launchLock.ReleaseMutex() }
    $launchLock.Dispose()
}
