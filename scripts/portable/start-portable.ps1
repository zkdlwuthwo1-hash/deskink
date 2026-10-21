param([ValidateRange(0, 32)][int]$Monitor = 0, [switch]$CheckOnly, [string]$UpdateReceipt)
$ErrorActionPreference = 'Stop'
$packageRoot = Split-Path -Parent $PSScriptRoot
$app = Join-Path $packageRoot 'bin/deskink.exe'
$runtimeDir = Join-Path $packageRoot 'runtime'
$logDir = Join-Path $env:LOCALAPPDATA 'DeskInk/logs'
$runtimeVersion = 'ffmpeg-n7.1.5-12-g1fdbca85aa-win64-lgpl-shared-7.1'
$runtimeHash = '0f376f96fb38554ccefb1b2ae9c7c6a7b351f0e60a372b38262c320e8392c5d0'
$runtimeUrl = "https://github.com/BtbN/FFmpeg-Builds/releases/download/autobuild-2026-07-31-14-10/$runtimeVersion.zip"
$dlls = @('avcodec-61.dll', 'avdevice-61.dll', 'avfilter-10.dll', 'avformat-61.dll', 'avutil-59.dll', 'swresample-5.dll', 'swscale-8.dll')
$launchLock = [System.Threading.Mutex]::new($false, 'Local\DeskInkLauncher')
$acquired = $false
$originalPath = $env:PATH
$temporaryZip = $null
try {
    # One first-run download even when several shortcuts are clicked together.
    $acquired = $launchLock.WaitOne(0)
    if (-not $acquired) {
        if ($UpdateReceipt) { throw 'Another DeskInk launcher is running. Update startup was not confirmed.' }
        exit 0
    }
    if (-not (Test-Path -LiteralPath $app)) { throw 'Extract the entire DeskInk ZIP before running DeskInk.exe.' }
    [void](New-Item -ItemType Directory -Path $logDir -Force)
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
    $outLog = Join-Path $logDir "$stamp-stdout.log"
    $errorLog = Join-Path $logDir "$stamp-stderr.log"
    $ready = (Test-Path -LiteralPath (Join-Path $runtimeDir 'verified.sha256'))
    foreach ($dll in $dlls) { $ready = $ready -and (Test-Path -LiteralPath (Join-Path $runtimeDir $dll)) }
    if ($ready) { $ready = ((Get-Content -LiteralPath (Join-Path $runtimeDir 'verified.sha256') -Raw).Trim() -eq $runtimeHash) }
    if (-not $ready) {
        # Obtain the unchanged LGPL runtime from its publisher. It is not
        # redistributed inside DeskInk's ZIP. Verify before extracting anything.
        [void](New-Item -ItemType Directory -Path $runtimeDir -Force)
        $temporaryZip = Join-Path $runtimeDir ([Guid]::NewGuid().ToString() + '.download')
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        $client = New-Object System.Net.WebClient
        try {
            if ($CheckOnly) {
                $client.DownloadFile($runtimeUrl, $temporaryZip)
            } else {
                Add-Type -AssemblyName System.Windows.Forms
                $form = New-Object System.Windows.Forms.Form
                $form.Text = 'DeskInk - First-time setup'
                $form.ClientSize = New-Object System.Drawing.Size(440, 130)
                $form.StartPosition = 'CenterScreen'
                $form.FormBorderStyle = 'FixedDialog'
                $form.MaximizeBox = $false
                $label = New-Object System.Windows.Forms.Label
                $label.Text = "Downloading video components (60 MB).`r`nThis is needed only on first launch. Internet is required."
                $label.SetBounds(20, 20, 400, 50)
                $bar = New-Object System.Windows.Forms.ProgressBar
                $bar.SetBounds(20, 82, 400, 24)
                $form.Controls.AddRange(@($label, $bar))
                $script:downloadError = $null
                $script:downloadFinished = $false
                $client.add_DownloadProgressChanged({ param($sender, $event) $bar.Value = $event.ProgressPercentage })
                $client.add_DownloadFileCompleted({
                    param($sender, $event)
                    $script:downloadError = $event.Error
                    $script:downloadFinished = -not $event.Cancelled
                    $form.Close()
                })
                $form.add_Shown({ $client.DownloadFileAsync([Uri]$runtimeUrl, $temporaryZip) })
                try { [void]$form.ShowDialog() } finally { $form.Dispose() }
                if (-not $script:downloadFinished) { $client.CancelAsync(); throw 'Setup cancelled. Run DeskInk.exe again when ready.' }
                if ($script:downloadError) { throw $script:downloadError }
            }
        } finally { $client.Dispose() }
        if ((Get-FileHash -LiteralPath $temporaryZip -Algorithm SHA256).Hash -ne $runtimeHash) {
            throw 'Video component checksum mismatch. Nothing was installed. Try again or report this error.'
        }
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $archive = [IO.Compression.ZipFile]::OpenRead($temporaryZip)
        try {
            foreach ($dll in $dlls) {
                $entry = $archive.GetEntry("$runtimeVersion/bin/$dll")
                if (-not $entry) { throw "Missing video component: $dll" }
                [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $runtimeDir $dll), $true)
            }
            $license = $archive.GetEntry("$runtimeVersion/LICENSE.txt")
            if (-not $license) { throw 'The runtime license is missing.' }
            [IO.Compression.ZipFileExtensions]::ExtractToFile($license, (Join-Path $runtimeDir 'LICENSE.txt'), $true)
        } finally { $archive.Dispose() }
        Set-Content -LiteralPath (Join-Path $runtimeDir 'verified.sha256') -Value $runtimeHash -Encoding ASCII
    }
    $env:PATH = $runtimeDir + ';' + $env:PATH
    if ($CheckOnly) {
        $probe = Start-Process -FilePath $app -ArgumentList '--list-monitors' -WorkingDirectory $packageRoot -WindowStyle Hidden -Wait -PassThru -RedirectStandardOutput $outLog -RedirectStandardError $errorLog
        if ($probe.ExitCode -ne 0) { throw "Runtime check failed ($($probe.ExitCode)). See $errorLog" }
        Write-Output 'DeskInk runtime check passed.'
        exit 0
    }
    $shortcutMarker = Join-Path $runtimeDir 'desktop-shortcuts-created'
    if (-not (Test-Path -LiteralPath $shortcutMarker)) {
        try {
            & (Join-Path $PSScriptRoot 'create-shortcuts.ps1') -Quiet
            Set-Content -LiteralPath $shortcutMarker -Value '1' -Encoding ASCII
        } catch {
            # A redirected or read-only Desktop must not prevent presentation.
            Add-Content -LiteralPath $errorLog -Value "Could not create desktop shortcuts: $($_.Exception.Message)"
        }
    }
    $running = Get-Process deskink -ErrorAction SilentlyContinue
    if ($UpdateReceipt -and $running) { throw 'Another DeskInk is running. Close it before installing the update.' }
    $arguments = if ($running) { "--monitor $Monitor --switch-only" } else { "--monitor $Monitor" }
    $process = Start-Process -FilePath $app -ArgumentList $arguments -WorkingDirectory $packageRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput $outLog -RedirectStandardError $errorLog
    $startupWait = if ($UpdateReceipt) { 2500 } else { 900 }
    if ($process.WaitForExit($startupWait) -and ($process.ExitCode -ne 0 -or $UpdateReceipt)) {
        throw "DeskInk could not start ($($process.ExitCode)). See $errorLog"
    }
    if ($UpdateReceipt) {
        @{process_id=$process.Id; path=$app} | ConvertTo-Json | Set-Content -LiteralPath $UpdateReceipt -Encoding UTF8
    }
} catch {
    if ($CheckOnly) { Write-Error $_; exit 1 }
    if ($UpdateReceipt) { [Console]::Error.WriteLine($_.Exception.Message); exit 1 }
    $notice = New-Object -ComObject WScript.Shell
    [void]$notice.Popup($_.Exception.Message, 0, 'DeskInk', 16)
    exit 1
} finally {
    $env:PATH = $originalPath
    # Only the exact temporary file created by this invocation is removed.
    if ($temporaryZip -and (Test-Path -LiteralPath $temporaryZip)) { Remove-Item -LiteralPath $temporaryZip -Force }
    if ($acquired) { $launchLock.ReleaseMutex() }
    $launchLock.Dispose()
}
