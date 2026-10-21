# Stage is non-disruptive. Apply runs from its own job folder after the user
# requests restart. It never kills DeskInk or changes machine-wide settings.
param(
    [Parameter(Mandatory=$true)][ValidateSet('Stage', 'Apply')][string]$Mode,
    [string]$PackageRoot,
    [string]$DescriptorPath,
    [string]$ArchivePath,
    [string]$JobPath,
    [int]$WaitProcessId = 0,
    [ValidateRange(1, 60)][int]$WaitSeconds = 60,
    [switch]$SignalReady,
    [ValidateRange(0, 32)][int]$Monitor = 0,
    [switch]$NoLaunch,
    [switch]$Quiet
)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Read-Json([string]$Path) {
    return Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json
}

function Assert-NoReparse([string]$Path) {
    $item = Get-Item -LiteralPath $Path -Force
    while ($item) {
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'Update paths must not contain junctions or symbolic links.'
        }
        $item = $item.Parent
    }
}

function Assert-Tree([string]$Path) {
    Assert-NoReparse $Path
    # Check each level before descending, so a junction is never followed.
    foreach ($item in Get-ChildItem -LiteralPath $Path -Force) {
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'The installation contains a junction or symbolic link.'
        }
        if ($item.PSIsContainer) { Assert-Tree $item.FullName }
    }
}

function Safe-Relative([string]$Name) {
    if (-not $Name -or $Name.Contains('\') -or $Name.StartsWith('/') -or $Name.Contains(':')) {
        throw "Invalid package path: $Name"
    }
    foreach ($part in $Name.Split('/')) {
        if (-not $part -or $part -eq '.' -or $part -eq '..' -or $part -match '[<>:"|?*\x00-\x1f]' -or
            $part -match '[. ]$' -or $part -match '^(?i:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\.|$)') {
            throw "Invalid package path: $Name"
        }
    }
    return $Name
}

function Child-Path([string]$Root, [string]$Relative) {
    $safe = Safe-Relative $Relative
    $path = [IO.Path]::GetFullPath((Join-Path $Root $safe))
    $prefix = [IO.Path]::GetFullPath($Root).TrimEnd('\') + '\'
    if (-not $path.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Path escaped its containing folder.'
    }
    return $path
}

function Remove-JobChild([string]$Workspace, [string]$Relative) {
    $path = Child-Path $Workspace $Relative
    if (Test-Path -LiteralPath $path) {
        Assert-Tree $path
        Remove-Item -LiteralPath $path -Recurse -Force
    }
}

function Package-Identity([string]$Root) {
    $info = Read-Json (Join-Path $Root 'build-info.json')
    if ($info.version -notmatch '^v?\d+\.\d+\.\d+$' -or $info.source_commit -notmatch '^[a-fA-F0-9]{40}$') {
        throw 'This portable installation has no supported build identity.'
    }
    return $info
}

function Check-Descriptor($Descriptor) {
    if ($Descriptor.version -notmatch '^v?\d+\.\d+\.\d+$' -or
        $Descriptor.source_commit -notmatch '^[a-fA-F0-9]{40}$' -or
        $Descriptor.sha256 -notmatch '^[a-fA-F0-9]{64}$' -or
        $Descriptor.url -cnotmatch '^https://github\.com/zkdlwuthwo1-hash/deskink/releases/download/[A-Za-z0-9._-]+/DeskInk-Windows-x64\.zip$' -or
        [long]$Descriptor.size -le 0 -or [long]$Descriptor.size -gt 268435456) {
        throw 'Invalid DeskInk update descriptor.'
    }
}

function Check-Manifest([string]$Root, $Descriptor, [bool]$ExactFiles) {
    Assert-Tree $Root
    $info = Package-Identity $Root
    if ($info.version -ne $Descriptor.version -or $info.source_commit -ne $Descriptor.source_commit) {
        throw 'Downloaded package does not match the announced update.'
    }
    $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($property in $info.files.PSObject.Properties) {
        $relative = Safe-Relative $property.Name
        if ($relative -eq 'build-info.json' -or -not $seen.Add($relative) -or
            $property.Value -notmatch '^[a-fA-F0-9]{64}$') { throw 'Invalid package file manifest.' }
        $file = Child-Path $Root $relative
        if (-not (Test-Path -LiteralPath $file -PathType Leaf) -or
            (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $property.Value) {
            throw "Package file verification failed: $relative"
        }
    }
    foreach ($required in @('DeskInk.exe', 'bin/deskink.exe', 'scripts/start-portable.ps1', 'scripts/update-portable.ps1')) {
        if (-not $seen.Contains($required)) { throw "Required package file is missing: $required" }
    }
    if ($ExactFiles) {
        foreach ($file in Get-ChildItem -LiteralPath $Root -File -Recurse -Force) {
            $relative = $file.FullName.Substring($Root.Length + 1).Replace('\', '/')
            if ($relative -ne 'build-info.json' -and -not $seen.Contains($relative)) {
                throw "Unlisted package file: $relative"
            }
        }
    }
    return $info
}

function Download-Archive($Descriptor, [string]$Destination) {
    Add-Type -AssemblyName System.Net.Http
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $handler = [Net.Http.HttpClientHandler]::new()
    $handler.AllowAutoRedirect = $false
    $client = [Net.Http.HttpClient]::new($handler)
    $client.Timeout = [TimeSpan]::FromMinutes(5)
    $client.DefaultRequestHeaders.UserAgent.ParseAdd('DeskInk-Updater/1')
    $uri = [Uri]$Descriptor.url
    try {
        for ($redirect = 0; $redirect -lt 6; $redirect++) {
            if ($uri.Scheme -cne 'https' -or $uri.Port -ne 443 -or $uri.UserInfo -or
                $uri.Host -cnotin @('github.com', 'release-assets.githubusercontent.com')) {
                throw 'Update download redirected outside the trusted HTTPS hosts.'
            }
            $response = $client.GetAsync($uri, [Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
            try {
                if ([int]$response.StatusCode -in @(301, 302, 303, 307, 308)) {
                    $uri = [Uri]::new($uri, $response.Headers.Location)
                    continue
                }
                [void]$response.EnsureSuccessStatusCode()
                $input = $response.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
                $output = [IO.File]::Create($Destination)
                try {
                    $buffer = [byte[]]::new(65536)
                    $total = 0L
                    $deadline = [DateTime]::UtcNow.AddMinutes(5)
                    while ($true) {
                        $read = $input.ReadAsync($buffer, 0, $buffer.Length)
                        if (-not $read.Wait(30000)) { throw 'Update download stalled.' }
                        $count = $read.GetAwaiter().GetResult()
                        if ($count -eq 0) { break }
                        $total += $count
                        if ($total -gt [long]$Descriptor.size -or [DateTime]::UtcNow -gt $deadline) {
                            throw 'Update download exceeded its size or time limit.'
                        }
                        $output.Write($buffer, 0, $count)
                    }
                } finally { $output.Dispose(); $input.Dispose() }
                return
            } finally { $response.Dispose() }
        }
        throw 'Too many update redirects.'
    } finally { $client.Dispose(); $handler.Dispose() }
}

function Extract-Verified([string]$Archive, [string]$Destination, $Descriptor) {
    if ((Get-Item -LiteralPath $Archive).Length -ne [long]$Descriptor.size -or
        (Get-FileHash -LiteralPath $Archive -Algorithm SHA256).Hash -ne $Descriptor.sha256) {
        throw 'Update archive checksum or size mismatch; the installation was not changed.'
    }
    $zip = [IO.Compression.ZipFile]::OpenRead($Archive)
    try {
        $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
        $total = 0L
        if ($zip.Entries.Count -gt 10000) { throw 'Too many package entries.' }
        foreach ($entry in $zip.Entries) {
            if (-not $entry.FullName.StartsWith('DeskInk/', [StringComparison]::Ordinal) -or
                (($entry.ExternalAttributes -shr 16) -band 61440) -eq 40960) {
                throw 'Invalid package root or symbolic link.'
            }
            if ($entry.FullName.EndsWith('/')) {
                [void](Safe-Relative $entry.FullName.TrimEnd('/'))
                continue
            }
            $relative = Safe-Relative $entry.FullName.Substring(8)
            if (-not $seen.Add($relative)) { throw 'Duplicate package path.' }
            $total += $entry.Length
            if ($total -gt 1073741824) { throw 'Expanded package exceeds the size limit.' }
            $target = Child-Path $Destination $relative
            [void][IO.Directory]::CreateDirectory((Split-Path -Parent $target))
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $false)
        }
    } finally { $zip.Dispose() }
    return Check-Manifest $Destination $Descriptor $true
}

function Stage-Update {
    $root = [IO.Path]::GetFullPath($PackageRoot).TrimEnd('\')
    if (-not (Test-Path -LiteralPath (Join-Path $root 'DeskInk.exe') -PathType Leaf)) {
        throw 'Select an extracted DeskInk portable installation.'
    }
    Assert-Tree $root
    $current = Package-Identity $root
    $descriptor = Read-Json $DescriptorPath
    Check-Descriptor $descriptor
    $candidate = [Version]$descriptor.version.TrimStart('v')
    $installed = [Version]$current.version.TrimStart('v')
    if ($candidate -lt $installed -or
        ($candidate -eq $installed -and $descriptor.source_commit -eq $current.source_commit)) {
        throw 'This package is already installed or older; documentation-only changes do not require restart.'
    }
    $workspace = Join-Path (Split-Path -Parent $root) ('.DeskInk-update-' + [Guid]::NewGuid().ToString('N'))
    [void][IO.Directory]::CreateDirectory($workspace)
    try {
        $archive = Join-Path $workspace 'update.zip'
        if ($ArchivePath) { Copy-Item -LiteralPath $ArchivePath -Destination $archive }
        else { Download-Archive $descriptor $archive }
        $stage = Join-Path $workspace 'package'
        [void][IO.Directory]::CreateDirectory($stage)
        $new = Extract-Verified $archive $stage $descriptor
        Copy-Item -LiteralPath $PSCommandPath -Destination (Join-Path $workspace 'apply.ps1')
        $job = @{package_root=$root; descriptor=$descriptor; old_version=$current.version; old_source=$current.source_commit}
        $path = Join-Path $workspace 'job.json'
        $job | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $path -Encoding UTF8
        @{job_path=$path} | ConvertTo-Json -Compress
    } catch {
        # The exact newly created sibling is checked before any recursive removal.
        if ((Split-Path -Parent $workspace) -eq (Split-Path -Parent $root) -and
            (Split-Path -Leaf $workspace) -match '^\.DeskInk-update-[a-f0-9]{32}$') {
            Assert-Tree $workspace
            Remove-Item -LiteralPath $workspace -Recurse -Force
        }
        throw
    }
}

function Apply-Update {
    $jobFile = [IO.Path]::GetFullPath($JobPath)
    $workspace = Split-Path -Parent $jobFile
    Assert-Tree $workspace
    # The app was launched with its installation as cwd. Release both Windows'
    # native cwd handle and PowerShell's provider location before any rename.
    Set-Location -LiteralPath $workspace
    [Environment]::CurrentDirectory = $workspace
    $job = Read-Json $jobFile
    $root = [IO.Path]::GetFullPath($job.package_root).TrimEnd('\')
    if ((Split-Path -Parent $workspace) -ne (Split-Path -Parent $root) -or
        (Split-Path -Leaf $workspace) -notmatch '^\.DeskInk-update-[a-f0-9]{32}$') {
        throw 'Update job is outside its installation parent.'
    }
    Check-Descriptor $job.descriptor
    $stage = Child-Path $workspace 'package'
    $backup = Child-Path $workspace 'backup'
    $status = Child-Path $workspace 'status.json'
    $oldMoved = $false
    $newMoved = $false
    $readyPublished = $false
    try {
        $newInfo = Check-Manifest $stage $job.descriptor $false
        if ($SignalReady) {
            if ($WaitProcessId -le 0) { throw 'The update handshake requires a running host process.' }
            Assert-Tree $root
            $beforeExit = Package-Identity $root
            if ($beforeExit.version -ne $job.old_version -or $beforeExit.source_commit -ne $job.old_source) {
                throw 'The installation changed after this update was prepared.'
            }
            [IO.File]::WriteAllText((Child-Path $workspace 'ready'), 'ready')
            $readyPublished = $true
        }
        if ($WaitProcessId -gt 0) {
            $process = Get-Process -Id $WaitProcessId -ErrorAction SilentlyContinue
            if ($process -and -not $process.WaitForExit($WaitSeconds * 1000)) {
                throw 'DeskInk did not close in time. The installation was not changed.'
            }
        }
        Assert-Tree $root
        $current = Package-Identity $root
        if ($current.version -ne $job.old_version -or $current.source_commit -ne $job.old_source) {
            throw 'The installation changed after this update was prepared.'
        }
        $managed = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
        foreach ($property in $current.files.PSObject.Properties) { [void]$managed.Add((Safe-Relative $property.Name)) }
        [void]$managed.Add('build-info.json')
        $newManaged = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
        foreach ($property in $newInfo.files.PSObject.Properties) { [void]$newManaged.Add((Safe-Relative $property.Name)) }
        [void]$newManaged.Add('build-info.json')
        # Retrying after a locked folder must not restore stale copies of user
        # files from the previous attempt. Start with the verified files only.
        foreach ($file in Get-ChildItem -LiteralPath $stage -File -Recurse -Force) {
            $relative = $file.FullName.Substring($stage.Length + 1).Replace('\', '/')
            if (-not $newManaged.Contains($relative)) {
                $stale = Child-Path $stage $relative
                Remove-Item -LiteralPath $stale -Force
            }
        }
        # Copy user-owned content only after the host closes: files can change
        # while the download is running. Old managed files are not carried over.
        foreach ($file in Get-ChildItem -LiteralPath $root -File -Recurse -Force) {
            $relative = $file.FullName.Substring($root.Length + 1).Replace('\', '/')
            if (-not $managed.Contains($relative)) {
                if ($newManaged.Contains($relative)) { throw "Update conflicts with a user-owned file: $relative" }
                $destination = Child-Path $stage $relative
                if (Test-Path -LiteralPath $destination -PathType Container) { throw "Update conflicts with a user-owned file: $relative" }
                [void][IO.Directory]::CreateDirectory((Split-Path -Parent $destination))
                Copy-Item -LiteralPath $file.FullName -Destination $destination
            }
        }
        Move-Item -LiteralPath $root -Destination $backup
        $oldMoved = $true
        Move-Item -LiteralPath $stage -Destination $root
        $newMoved = $true
        if (-not $NoLaunch) {
            $receipt = Child-Path $workspace 'startup.json'
            $script = Join-Path $root 'scripts/start-portable.ps1'
            $shell = Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/powershell.exe'
            $arguments = @('-NoProfile', '-NonInteractive', '-Sta', '-ExecutionPolicy', 'Bypass', '-File', ('"' + $script + '"'),
                '-Monitor', $Monitor, '-UpdateReceipt', ('"' + $receipt + '"'))
            $started = Start-Process -FilePath $shell -ArgumentList $arguments -WorkingDirectory $root -WindowStyle Hidden -PassThru
            if (-not $started.WaitForExit(120000)) {
                # A live setup process may still be using the new folder. Keep
                # the backup rather than racing it with a rollback.
                $oldMoved = $false
                $newMoved = $false
                throw "Update startup is still running. The previous package is preserved in $backup."
            }
            if ($started.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $receipt -PathType Leaf)) {
                throw 'Updated host did not report a successful start.'
            }
        }
        # Once the new host is running, cleanup failure must not roll it back.
        $oldMoved = $false
        $newMoved = $false
        try {
            @{state='installed'; version=$job.descriptor.version} | ConvertTo-Json | Set-Content -LiteralPath $status -Encoding UTF8
            Remove-JobChild $workspace 'backup'
            $archive = Child-Path $workspace 'update.zip'
            if (Test-Path -LiteralPath $archive) { Remove-Item -LiteralPath $archive -Force }
        } catch { [Console]::Error.WriteLine("Update installed; old files retained in ${workspace}: $($_.Exception.Message)") }
        @{state='installed'; version=$job.descriptor.version} | ConvertTo-Json -Compress
    } catch {
        $reason = $_.Exception.Message
        # A readiness receipt must not outlive a failed/timed-out installer.
        # The controller can be minimized and consume the signal much later.
        $readyFile = Child-Path $workspace 'ready'
        if (Test-Path -LiteralPath $readyFile) { Remove-Item -LiteralPath $readyFile -Force -ErrorAction SilentlyContinue }
        try {
            if ($newMoved) { Move-Item -LiteralPath $root -Destination (Child-Path $workspace 'failed-package') }
            if ($oldMoved) { Move-Item -LiteralPath $backup -Destination $root }
        } catch { $reason += " Rollback needs recovery from ${backup}: $($_.Exception.Message)" }
        @{state='failed'; message=$reason; backup=$backup} | ConvertTo-Json | Set-Content -LiteralPath $status -Encoding UTF8
        if (-not $NoLaunch -and -not $Quiet -and (-not $SignalReady -or $readyPublished)) {
            $notice = New-Object -ComObject WScript.Shell
            [void]$notice.Popup("DeskInk update failed: $reason`nYour pairing and network settings were not changed.", 0, 'DeskInk update', 16)
        }
        throw $reason
    }
}

${updateMutex} = $null
$ownsUpdateMutex = $false
try {
    $rootForLock = if ($Mode -eq 'Stage') { $PackageRoot } else { (Read-Json $JobPath).package_root }
    $rootForLock = [IO.Path]::GetFullPath($rootForLock).TrimEnd('\').ToUpperInvariant()
    $hasher = [Security.Cryptography.SHA256]::Create()
    try { $key = [BitConverter]::ToString($hasher.ComputeHash([Text.Encoding]::UTF8.GetBytes($rootForLock))).Replace('-', '') }
    finally { $hasher.Dispose() }
    $updateMutex = [Threading.Mutex]::new($false, ('Local\DeskInkUpdate-' + $key))
    try { $ownsUpdateMutex = $updateMutex.WaitOne(0) }
    catch [Threading.AbandonedMutexException] { $ownsUpdateMutex = $true }
    if (-not $ownsUpdateMutex) { throw 'Another update is already working on this DeskInk folder.' }
    if ($Mode -eq 'Stage') { Stage-Update } else { Apply-Update }
} catch {
    [Console]::Error.WriteLine($_.Exception.Message)
    exit 1
} finally {
    if ($ownsUpdateMutex) { $updateMutex.ReleaseMutex() }
    if ($updateMutex) { $updateMutex.Dispose() }
}
