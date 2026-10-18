param([switch]$Release)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'windows-env.ps1')
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    $buildArgs = @('build', '-p', 'eternal-host', '-p', 'deskink-windows', '--bin', 'deskink', '--bin', 'deskink-launcher')
    # Keep the updated executable separate from the original running version.
    # The desktop launcher and run-windows.ps1 use the same release location.
    if ($Release) { $buildArgs += @('--release', '--target-dir', 'target/monitor-switch') }
    & cargo @buildArgs
    if ($LASTEXITCODE -ne 0) { throw 'Windows build failed' }
} finally {
    Pop-Location
}
