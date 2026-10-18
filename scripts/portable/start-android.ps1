param([ValidateRange(0, 32)][int]$Monitor = 0, [switch]$CheckOnly)
$ErrorActionPreference = 'Stop'
$installRoot = Join-Path $env:LOCALAPPDATA 'DeskInk/android'
$toolsDir = Join-Path $installRoot 'platform-tools'
$archiveHash = '45f4d63113e895ebde0c90f194099a4676b6ac653bd28d54314a9e022bbc1a99'
$downloadUrl = 'https://dl.google.com/android/repository/platform-tools_r37.0.1-win.zip'
$files = @('adb.exe', 'AdbWinApi.dll', 'AdbWinUsbApi.dll', 'libwinpthread-1.dll', 'NOTICE.txt', 'source.properties')
$mutex = [Threading.Mutex]::new($false, 'Local\DeskInkAndroidSetup')
$acquired = $false
$temporary = $null
try {
    $acquired = $mutex.WaitOne(0)
    if (-not $acquired) { exit 0 }
    $marker = Join-Path $toolsDir 'verified.sha256'
    $ready = Test-Path -LiteralPath $marker
    foreach ($name in $files) { $ready = $ready -and (Test-Path -LiteralPath (Join-Path $toolsDir $name)) }
    if ($ready) { $ready = (Get-Content -LiteralPath $marker -Raw).Trim() -eq $archiveHash }
    if (-not $ready) {
        [void](New-Item -ItemType Directory -Path $toolsDir -Force)
        $temporary = Join-Path $installRoot ([Guid]::NewGuid().ToString('N') + '.download')
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        $client = New-Object Net.WebClient
        try {
            if ($CheckOnly) { $client.DownloadFile($downloadUrl, $temporary) }
            else {
                Add-Type -AssemblyName System.Windows.Forms
                $form = New-Object Windows.Forms.Form
                $form.Text = 'DeskInk - Android USB setup'
                $form.ClientSize = New-Object Drawing.Size(460, 140)
                $form.StartPosition = 'CenterScreen'
                $form.FormBorderStyle = 'FixedDialog'
                $form.MaximizeBox = $false
                $label = New-Object Windows.Forms.Label
                $label.Text = "Downloading Android USB components from Google (8 MB).`r`nThis is needed only once."
                $label.SetBounds(20, 20, 420, 50)
                $bar = New-Object Windows.Forms.ProgressBar
                $bar.SetBounds(20, 85, 420, 24)
                $form.Controls.AddRange(@($label, $bar))
                $script:androidFinished = $false
                $script:androidError = $null
                $client.add_DownloadProgressChanged({ param($sender, $event) $bar.Value = $event.ProgressPercentage })
                $client.add_DownloadFileCompleted({
                    param($sender, $event)
                    $script:androidFinished = -not $event.Cancelled
                    $script:androidError = $event.Error
                    $form.Close()
                })
                $form.add_Shown({ $client.DownloadFileAsync([Uri]$downloadUrl, $temporary) })
                try { [void]$form.ShowDialog() } finally { $form.Dispose() }
                if (-not $script:androidFinished) { $client.CancelAsync(); throw 'Android setup cancelled. Run DeskInk-Android.exe to retry.' }
                if ($script:androidError) { throw $script:androidError }
            }
        } finally { $client.Dispose() }
        if ((Get-FileHash -LiteralPath $temporary -Algorithm SHA256).Hash -ne $archiveHash) { throw 'Android component checksum mismatch. Nothing was installed.' }
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $archive = [IO.Compression.ZipFile]::OpenRead($temporary)
        try {
            foreach ($name in $files) {
                $entry = $archive.GetEntry("platform-tools/$name")
                if (-not $entry) { throw "Missing Android component: $name" }
                [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $toolsDir $name), $true)
            }
        } finally { $archive.Dispose() }
        Set-Content -LiteralPath $marker -Value $archiveHash -Encoding ASCII
    }
    if ($CheckOnly) {
        $output = Join-Path $installRoot 'version.txt'
        $probe = Start-Process -FilePath (Join-Path $toolsDir 'adb.exe') -ArgumentList 'version' -WindowStyle Hidden -Wait -PassThru -RedirectStandardOutput $output
        if ($probe.ExitCode -ne 0) { throw 'Android USB runtime check failed.' }
        Write-Output 'DeskInk Android USB runtime check passed.'
    } else {
        & (Join-Path $PSScriptRoot 'start-portable.ps1') -Monitor $Monitor
    }
} catch {
    if ($CheckOnly) { Write-Error $_; exit 1 }
    $notice = New-Object -ComObject WScript.Shell
    [void]$notice.Popup($_.Exception.Message, 0, 'DeskInk Android USB', 16)
    exit 1
} finally {
    if ($temporary -and (Test-Path -LiteralPath $temporary)) { Remove-Item -LiteralPath $temporary -Force }
    if ($acquired) { $mutex.ReleaseMutex() }
    $mutex.Dispose()
}
