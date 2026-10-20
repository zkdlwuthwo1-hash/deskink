param([switch]$IncludeUpstreamVideoTests)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'windows-env.ps1')
Push-Location (Split-Path -Parent $PSScriptRoot)
$originalPath = $env:PATH
try {
    # Always test the actual distributed LGPL runtime before switching DLLs
    # for upstream tests; x264 in the GPL build can hide broken fallback paths.
    & cargo test -p eternal-host --lib encoder::input::tests
    if ($LASTEXITCODE -ne 0) { throw 'Packaged encoder input tests failed' }
    & cargo test -p eternal-host --test packaged_encoder
    if ($LASTEXITCODE -ne 0) { throw 'Packaged video/fallback tests failed' }
    if ($IncludeUpstreamVideoTests) {
        $testOnlyBin = Join-Path (Get-Location) '.tools/ffmpeg-gpl-tests-only/ffmpeg-n7.1.5-12-g1fdbca85aa-win64-gpl-shared-7.1/bin'
        if (-not (Test-Path (Join-Path $testOnlyBin 'avcodec-61.dll'))) { throw 'The separately verified test-only GPL SDK is required for upstream x264 tests.' }
        # Keep the application linked to the LGPL SDK; select x264-capable DLLs
        # in this test process only. Never copy these into an app package.
        $env:PATH = $testOnlyBin + ';' + $env:PATH
        & cargo test -p deskink-core -p eternal-wire -p eternal-host --lib --tests
    } else {
        & cargo test -p deskink-core -p eternal-wire
        if ($LASTEXITCODE -ne 0) { throw 'Core/protocol tests failed' }
        & cargo test -p eternal-host --test annotation_session --test usbmuxd --test android_usb --test remembered_pairing
        if ($LASTEXITCODE -ne 0) { throw 'Host input/USB tests failed' }
        & cargo test -p eternal-host --bin deskink
    }
    if ($LASTEXITCODE -ne 0) { throw 'Tests failed' }
} finally {
    $env:PATH = $originalPath
    Pop-Location
}
