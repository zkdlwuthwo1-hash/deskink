$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$launcher = Join-Path $projectRoot 'target/monitor-switch/release/deskink-launcher.exe'
if (-not (Test-Path -LiteralPath $launcher)) { throw 'Build the Release launcher first.' }
$desktopFolder = [Environment]::GetFolderPath('Desktop')
$shortcutShell = New-Object -ComObject WScript.Shell
foreach ($monitorIndex in 0,1) {
    $shortcutName = 'DeskInk - ' + [char]0xD654 + [char]0xBA74 + ' ' + ($monitorIndex + 1) + '.lnk'
    $shortcut = $shortcutShell.CreateShortcut((Join-Path $desktopFolder $shortcutName))
    $shortcut.TargetPath = $launcher
    $shortcut.Arguments = '--monitor ' + $monitorIndex + ' --project "' + $projectRoot + '"'
    $shortcut.WorkingDirectory = $projectRoot
    $shortcut.WindowStyle = 1
    $shortcut.Save()
}
