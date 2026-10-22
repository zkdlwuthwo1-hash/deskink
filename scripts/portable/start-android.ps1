param(
    [ValidateRange(0, 32)][int]$Monitor = 0,
    [switch]$CheckOnly,
    [switch]$PrepareOnly,
    [ValidateRange(1, 120)][int]$DownloadTimeoutSeconds = 60
)
$ErrorActionPreference = 'Stop'
$installRoot = Join-Path $env:LOCALAPPDATA 'DeskInk/android'
$toolsDir = Join-Path $installRoot 'platform-tools'
$archiveHash = '45f4d63113e895ebde0c90f194099a4676b6ac653bd28d54314a9e022bbc1a99'
$downloadUrl = 'https://dl.google.com/android/repository/platform-tools_r37.0.1-win.zip'
$files = @('adb.exe', 'AdbWinApi.dll', 'AdbWinUsbApi.dll', 'libwinpthread-1.dll', 'NOTICE.txt', 'source.properties')
$mutex = [Threading.Mutex]::new($false, 'Local\DeskInkAndroidSetup')
$acquired = $false
$temporary = $null
$staging = $null
$backup = $null
$published = $false

function Assert-SetupChild([string]$Path) {
    $resolved = [IO.Path]::GetFullPath($Path)
    $root = [IO.Path]::GetFullPath($installRoot).TrimEnd('\')
    if (-not [string]::Equals([IO.Path]::GetDirectoryName($resolved), $root, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Android setup path escaped its installation folder.'
    }
    if ((Test-Path -LiteralPath $resolved) -and ((Get-Item -LiteralPath $resolved -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw 'Android setup cannot replace a linked path.'
    }
    return $resolved
}

function Remove-SetupChild([string]$Path) {
    $resolved = Assert-SetupChild $Path
    if (Test-Path -LiteralPath $resolved -PathType Container) {
        # Setup owns flat staging/backup folders only. Do not follow links or
        # recursively remove unexpected user content.
        foreach ($file in Get-ChildItem -LiteralPath $resolved -Force) {
            if ($file.PSIsContainer -or ($file.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
                throw 'Unexpected content in the Android setup folder; it was retained.'
            }
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    } elseif (Test-Path -LiteralPath $resolved) {
        Remove-Item -LiteralPath $resolved -Force
    }
}

try {
    $acquired = $mutex.WaitOne(0)
    if (-not $acquired) { exit 0 }
    $marker = Join-Path $toolsDir 'verified.sha256'
    $ready = Test-Path -LiteralPath $marker
    foreach ($name in $files) { $ready = $ready -and (Test-Path -LiteralPath (Join-Path $toolsDir $name)) }
    if ($ready) { $ready = (Get-Content -LiteralPath $marker -Raw).Trim() -eq $archiveHash }
    if (-not $ready) {
        [void](New-Item -ItemType Directory -Path $installRoot -Force)
        $temporary = Join-Path $installRoot ([Guid]::NewGuid().ToString('N') + '.download')
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        $client = New-Object Net.WebClient
        try {
            if ($CheckOnly -or $PrepareOnly) {
                $download = $client.DownloadFileTaskAsync([Uri]$downloadUrl, $temporary)
                if (-not $download.Wait($DownloadTimeoutSeconds * 1000)) {
                    $client.CancelAsync()
                    throw 'Android USB setup download timed out. DeskInk will retry on its next full start.'
                }
                $download.GetAwaiter().GetResult()
            }
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
                if (-not $script:androidFinished) { $client.CancelAsync(); throw 'Android setup cancelled. Run DeskInk.exe again to retry.' }
                if ($script:androidError) { throw $script:androidError }
            }
        } finally { $client.Dispose() }
        if ((Get-FileHash -LiteralPath $temporary -Algorithm SHA256).Hash -ne $archiveHash) { throw 'Android component checksum mismatch. Nothing was installed.' }
        $staging = Assert-SetupChild (Join-Path $installRoot ([Guid]::NewGuid().ToString('N') + '.stage'))
        [void](New-Item -ItemType Directory -Path $staging)
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $archive = [IO.Compression.ZipFile]::OpenRead($temporary)
        try {
            foreach ($name in $files) {
                $entry = $archive.GetEntry("platform-tools/$name")
                if (-not $entry) { throw "Missing Android component: $name" }
                [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $staging $name), $false)
            }
        } finally { $archive.Dispose() }
        Set-Content -LiteralPath (Join-Path $staging 'verified.sha256') -Value $archiveHash -Encoding ASCII
        # The running host discovers adb.exe by its path. Publish the complete
        # directory only after every DLL and the marker are in place.
        $verifiedTools = Assert-SetupChild $toolsDir
        if (Test-Path -LiteralPath $verifiedTools) {
            $backup = Assert-SetupChild (Join-Path $installRoot ([Guid]::NewGuid().ToString('N') + '.previous'))
            Move-Item -LiteralPath $verifiedTools -Destination $backup
        }
        try {
            Move-Item -LiteralPath (Assert-SetupChild $staging) -Destination $verifiedTools
            $staging = $null
            $published = $true
        } catch {
            if ($backup -and -not (Test-Path -LiteralPath $verifiedTools)) {
                Move-Item -LiteralPath (Assert-SetupChild $backup) -Destination $verifiedTools
                $backup = $null
            }
            throw
        }
    }
    if ($CheckOnly) {
        $output = Join-Path $installRoot 'version.txt'
        $probe = Start-Process -FilePath (Join-Path $toolsDir 'adb.exe') -ArgumentList 'version' -WindowStyle Hidden -Wait -PassThru -RedirectStandardOutput $output
        if ($probe.ExitCode -ne 0) { throw 'Android USB runtime check failed.' }
        Write-Output 'DeskInk Android USB runtime check passed.'
    } elseif (-not $PrepareOnly) {
        & (Join-Path $PSScriptRoot 'start-portable.ps1') -Monitor $Monitor
    }
} catch {
    if ($CheckOnly -or $PrepareOnly) { [Console]::Error.WriteLine($_.Exception.Message); exit 1 }
    $notice = New-Object -ComObject WScript.Shell
    [void]$notice.Popup($_.Exception.Message, 0, 'DeskInk Android USB', 16)
    exit 1
} finally {
    $cleanupPaths = @($temporary, $staging)
    if ($published) { $cleanupPaths += $backup }
    foreach ($cleanup in $cleanupPaths) {
        if ($cleanup -and (Test-Path -LiteralPath $cleanup)) {
            try { Remove-SetupChild $cleanup }
            catch { [Console]::Error.WriteLine("Android setup retained ${cleanup}: $($_.Exception.Message)") }
        }
    }
    if ($acquired) { $mutex.ReleaseMutex() }
    $mutex.Dispose()
}
