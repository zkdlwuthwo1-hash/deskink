$ErrorActionPreference = 'Stop'
$packageRoot = Split-Path -Parent $PSScriptRoot
$launcher = Join-Path $packageRoot 'DeskInk.exe'
if (-not (Test-Path -LiteralPath $launcher)) { throw 'Extract the entire DeskInk ZIP first.' }
$desktopFolder = [Environment]::GetFolderPath('Desktop')
$shortcutShell = New-Object -ComObject WScript.Shell
foreach ($index in 0, 1) {
    $link = $shortcutShell.CreateShortcut((Join-Path $desktopFolder "DeskInk - Screen $($index + 1).lnk"))
    $link.TargetPath = $launcher
    $link.Arguments = "--monitor $index"
    $link.WorkingDirectory = $packageRoot
    $link.IconLocation = (Join-Path $packageRoot 'bin/deskink.exe') + ',0'
    $link.Save()
}
[void]$shortcutShell.Popup('Created DeskInk - Screen 1 and Screen 2 on your Desktop. Add these to Stream Deck to switch monitors.', 0, 'DeskInk', 64)
