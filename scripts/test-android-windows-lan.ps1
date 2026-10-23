param(
    [string]$DependencyCache,
    [switch]$SkipHostBuild
)

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (-not $DependencyCache) {
    $gradleCache = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
    $DependencyCache = Join-Path $gradleCache 'caches/modules-2/files-2.1'
}
$javaCompiler = (Get-Command javac -ErrorAction Stop).Source
$javaRuntime = (Get-Command java -ErrorAction Stop).Source
$dependencies = foreach ($coordinate in @('junit/junit/4.13.2', 'org.hamcrest/hamcrest-core/1.3', 'org.json/json/20240303')) {
    $jar = Get-ChildItem -LiteralPath (Join-Path $DependencyCache $coordinate) -Recurse -Filter '*.jar' |
        Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' } | Select-Object -First 1
    if (-not $jar) { throw "Missing cached dependency $coordinate. Run the Android core Gradle tests once to download it." }
    $jar.FullName
}

# Compile the actual platform-independent Android sources directly. This keeps
# the diagnostic independent of Gradle daemon, file watcher and drive-alias issues.
$previousEnvironment = @{}
foreach ($name in @('PATH', 'FFMPEG_DIR', 'LIBCLANG_PATH', 'ETERNAL_USB_DIRECT', 'DESKINK_INTEROP_READY')) {
    $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
$fixture = $null
$stopPath = $null
Push-Location $repository
try {
    . (Join-Path $PSScriptRoot 'windows-env.ps1')
    $env:ETERNAL_USB_DIRECT = '127.0.0.1:9'
    if (-not $SkipHostBuild) {
        & cargo build -p eternal-host --example android_lan_fixture --target-dir target --jobs 1
        if ($LASTEXITCODE -ne 0) { throw 'Windows LAN fixture build failed.' }
    }
    $executable = Join-Path $repository 'target/debug/examples/android_lan_fixture.exe'
    if (-not (Test-Path -LiteralPath $executable)) { throw 'Build the Windows LAN fixture first.' }

    $runDirectory = Join-Path $repository ('artifacts/local/android-windows-lan/' + [Guid]::NewGuid().ToString('N'))
    $classes = Join-Path $runDirectory 'classes'
    New-Item -ItemType Directory -Path $classes -Force | Out-Null
    $sources = @(Get-ChildItem -LiteralPath (Join-Path $repository 'android/core/src/main/java') -Recurse -Filter '*.java' |
        ForEach-Object FullName)
    $sources += Join-Path $repository 'android/core/src/test/java/com/deskink/core/WindowsLanInteropTest.java'
    $argumentsPath = Join-Path $runDirectory 'sources.txt'
    $sourceArguments = @($sources | ForEach-Object { '"' + $_.Replace('\', '/') + '"' })
    [IO.File]::WriteAllLines($argumentsPath, [string[]]$sourceArguments, [Text.UTF8Encoding]::new($false))
    $classPath = $dependencies -join ';'
    & $javaCompiler '-J-Dfile.encoding=UTF-8' -encoding UTF-8 --release 17 -cp $classPath -d $classes "@$argumentsPath"
    if ($LASTEXITCODE -ne 0) { throw 'Android LAN test compilation failed.' }

    # Compilation is complete before starting the fixture's bounded lifetime.
    $readyPath = Join-Path $runDirectory 'ready.json'
    $stopPath = Join-Path $runDirectory 'ready.stop'
    $fixture = Start-Process -FilePath $executable -ArgumentList ('"{0}"' -f $readyPath) -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $runDirectory 'host-stdout.log') `
        -RedirectStandardError (Join-Path $runDirectory 'host-stderr.log')
    # Retain the process handle before it exits: Windows PowerShell otherwise
    # sometimes returns a null ExitCode for a short-lived Start-Process child.
    $null = $fixture.Handle
    $deadline = [DateTime]::UtcNow.AddSeconds(5)
    while (-not (Test-Path -LiteralPath $readyPath)) {
        if ($fixture.HasExited) { throw "Windows LAN fixture exited. Logs: $runDirectory" }
        if ([DateTime]::UtcNow -ge $deadline) { throw "Windows LAN fixture did not become ready. Logs: $runDirectory" }
        Start-Sleep -Milliseconds 50
    }
    $env:DESKINK_INTEROP_READY = $readyPath
    & $javaRuntime -cp ($classes + ';' + $classPath) org.junit.runner.JUnitCore com.deskink.core.WindowsLanInteropTest |
        Tee-Object -FilePath (Join-Path $runDirectory 'junit.log')
    if ($LASTEXITCODE -ne 0) { throw "Android/Windows LAN interoperability failed. Logs: $runDirectory" }
    # A skipped test cannot clear the sentinel and make the host exit successfully.
    if (-not $fixture.WaitForExit(3000)) { throw 'Windows LAN fixture did not finish after the client test.' }
    if ($fixture.ExitCode -ne 0) { throw "Windows LAN fixture failed (exit $($fixture.ExitCode)). Logs: $runDirectory" }
    Write-Output "Fresh Android/Windows LAN pairing, video and ink passed. Evidence: $runDirectory"
} finally {
    if ($fixture -and -not $fixture.HasExited) {
        if ($stopPath) { [IO.File]::WriteAllText($stopPath, 'stop') }
        if (-not $fixture.WaitForExit(3000)) { $fixture.Kill() }
    }
    foreach ($name in $previousEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process')
    }
    Pop-Location
}
