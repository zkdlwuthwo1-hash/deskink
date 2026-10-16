param(
    [string]$LogPath,
    [double]$MinimumFps = 50,
    [switch]$HostProbe
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$probe = $null
if ($HostProbe) {
    . (Join-Path $PSScriptRoot 'windows-env.ps1')
    $probePath = Join-Path $projectRoot 'target/debug/examples/stream_fps_probe.exe'
    if (-not (Test-Path -LiteralPath $probePath)) { throw 'Build first: cargo build -p eternal-host --example stream_fps_probe' }
    $localDir = Join-Path $projectRoot 'artifacts/local'
    [void](New-Item -ItemType Directory -Path $localDir -Force)
    $probeLog = Join-Path $localDir 'stream-fps-probe.log'
    $probeError = Join-Path $localDir 'stream-fps-probe-error.log'
    $probe = Start-Process -FilePath $probePath -ArgumentList "--minimum-fps=$MinimumFps" -WindowStyle Hidden -PassThru -RedirectStandardOutput $probeLog -RedirectStandardError $probeError
} elseif (-not $LogPath -or -not (Test-Path -LiteralPath $LogPath)) {
    throw 'Provide -LogPath for the running Windows DeskInk log, or use -HostProbe.'
}
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
$started = [DateTime]::UtcNow
$form = New-Object System.Windows.Forms.Form
$form.Text = 'DeskInk FPS measurement (8 seconds)'
$form.StartPosition = 'Manual'
$form.Location = New-Object System.Drawing.Point(40,40)
$form.ClientSize = New-Object System.Drawing.Size(420,220)
$form.TopMost = $true
$label = New-Object System.Windows.Forms.Label
$label.AutoSize = $true
$label.Text = 'Measuring moving-screen FPS. This window closes automatically.'
$label.Location = New-Object System.Drawing.Point(10,10)
$form.Controls.Add($label)
$panel = New-Object System.Windows.Forms.Panel
$panel.Size = New-Object System.Drawing.Size(80,80)
$panel.Location = New-Object System.Drawing.Point(0,65)
$panel.BackColor = [System.Drawing.Color]::Orange
$form.Controls.Add($panel)
$timer = New-Object System.Windows.Forms.Timer
$timer.Interval = 1
$script:stimulusTicks = 0
$clock = [Diagnostics.Stopwatch]::StartNew()
$timer.Add_Tick({
    if ($clock.Elapsed.TotalSeconds -ge 8) { $timer.Stop(); $form.Close(); return }
    $script:stimulusTicks += 1
    $panel.Left = [int](165 + 160 * [Math]::Sin($clock.Elapsed.TotalSeconds * 5))
})
$timer.Start()
try { [void]$form.ShowDialog() } finally { $timer.Dispose(); $form.Dispose() }
Write-Output ("Stimulus updates per second: {0:N1}" -f ($script:stimulusTicks / $clock.Elapsed.TotalSeconds))
if ($HostProbe) {
    if (-not $probe.WaitForExit(5000)) { Stop-Process -Id $probe.Id; throw 'Local capture probe did not finish.' }
    $probe.Refresh()
    Get-Content -Encoding UTF8 -LiteralPath $probeLog
    Get-Content -Encoding UTF8 -LiteralPath $probeError
    if ($probe.ExitCode -ne 0) { throw "Local capture probe failed ($($probe.ExitCode))." }
    return
}
$samples = @(Get-Content -Encoding UTF8 -LiteralPath $LogPath | ForEach-Object {
    if ($_ -match '^(\S+).*Receiver report .*decode_fps_x10=(\d+)') {
        $stamp = [DateTime]::Parse($Matches[1]).ToUniversalTime()
        if ($stamp -ge $started.AddSeconds(2) -and $stamp -le $started.AddSeconds(8)) { [double]$Matches[2] / 10 }
    }
})
if ($samples.Count -lt 4) { throw 'No usable iPad receiver reports; keep DeskInk open and connected.' }
$ordered = @($samples | Sort-Object)
$median = $ordered[[int][Math]::Floor($ordered.Count / 2)]
Write-Output ("Moving-screen receiver FPS: median={0:N1}, min={1:N1}, max={2:N1}, samples={3}" -f $median, $ordered[0], $ordered[-1], $ordered.Count)
if ($median -lt $MinimumFps) { throw "Low FPS reproduced: median $median < $MinimumFps" }
