$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'windows-env.ps1')
$previousUsb = $env:ETERNAL_USB_DIRECT
$previousJobs = $env:CARGO_BUILD_JOBS
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    # Real UDP sockets, with physical USB discovery redirected to a closed test port.
    $env:ETERNAL_USB_DIRECT = '127.0.0.1:9'
    $env:CARGO_BUILD_JOBS = '1'
    & cargo test -j 1 -p eternal-host --test lan_ink --test lan_transport --test annotation_session --test remembered_pairing --test android_usb --test usbmuxd
    if ($LASTEXITCODE -ne 0) { throw 'LAN and USB regression tests failed' }
    & cargo test -j 1 -p eternal-host --lib transport::
    if ($LASTEXITCODE -ne 0) { throw 'Transport session, pacing or congestion tests failed' }
    & cargo test -j 1 -p deskink-core -p eternal-wire
    if ($LASTEXITCODE -ne 0) { throw 'Ink/protocol tests failed' }
} finally {
    $env:ETERNAL_USB_DIRECT = $previousUsb
    $env:CARGO_BUILD_JOBS = $previousJobs
    Pop-Location
}
