param([switch]$Release)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'windows-env.ps1')
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    $buildArgs = @('build', '-p', 'eternal-host', '--bin', 'deskink')
    if ($Release) { $buildArgs += '--release' }
    & cargo @buildArgs
    if ($LASTEXITCODE -ne 0) { throw 'Windows build failed' }
} finally {
    Pop-Location
}
