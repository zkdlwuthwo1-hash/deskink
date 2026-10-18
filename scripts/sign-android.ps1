param(
    [string]$InputApk = (Join-Path $PSScriptRoot '../android/app/build/outputs/apk/release/app-release-unsigned.apk'),
    [Parameter(Mandatory=$true)][string]$OutputApk,
    [string]$BuildTools = (Join-Path $PSScriptRoot '../.tools/android-sdk/build-tools/35.0.0')
)
$ErrorActionPreference = 'Stop'
$inputFile = (Resolve-Path -LiteralPath $InputApk).Path
$outputFile = [IO.Path]::GetFullPath($OutputApk)
$keyDirectory = Join-Path $env:LOCALAPPDATA 'DeskInk/signing'
$keyFile = Join-Path $keyDirectory 'deskink-android.p12'
$passwordFile = Join-Path $keyDirectory 'password.dpapi'
$align = Join-Path $BuildTools 'zipalign.exe'
$signer = Join-Path $BuildTools 'apksigner.bat'
if (-not (Test-Path -LiteralPath $align) -or -not (Test-Path -LiteralPath $signer)) { throw 'Android build-tools 35.0.0 are required.' }
if ((Test-Path -LiteralPath $keyFile) -and -not (Test-Path -LiteralPath $passwordFile)) { throw 'The existing signing key password must be restored; do not replace the key.' }
[void](New-Item -ItemType Directory -Path $keyDirectory -Force)
$ownerSid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& icacls.exe $keyDirectory /inheritance:r /grant:r "*$($ownerSid):(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not restrict signing key directory permissions.' }
if (-not (Test-Path -LiteralPath $passwordFile)) {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $secure = ConvertTo-SecureString ([Convert]::ToBase64String($bytes)) -AsPlainText -Force
    ConvertFrom-SecureString $secure | Set-Content -LiteralPath $passwordFile -Encoding ASCII
}
$secret = Get-Content -LiteralPath $passwordFile -Raw | ConvertTo-SecureString
$credential = New-Object System.Management.Automation.PSCredential('deskink', $secret)
$oldPassword = $env:DESKINK_SIGNING_PASSWORD
$env:DESKINK_SIGNING_PASSWORD = $credential.GetNetworkCredential().Password
$aligned = $outputFile + '.aligned.tmp.apk'
try {
    if (-not (Test-Path -LiteralPath $keyFile)) {
        & keytool.exe -genkeypair -keystore $keyFile -storetype PKCS12 -alias deskink -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -validity 10000 -dname 'CN=DeskInk Android' -storepass:env DESKINK_SIGNING_PASSWORD -keypass:env DESKINK_SIGNING_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'Android signing key generation failed.' }
    }
    [void](New-Item -ItemType Directory -Path (Split-Path -Parent $outputFile) -Force)
    & $align -f -P 16 4 $inputFile $aligned
    if ($LASTEXITCODE -ne 0) { throw 'APK alignment failed.' }
    & $signer sign --ks $keyFile --ks-key-alias deskink --ks-pass env:DESKINK_SIGNING_PASSWORD --key-pass env:DESKINK_SIGNING_PASSWORD --out $outputFile $aligned
    if ($LASTEXITCODE -ne 0) { throw 'APK signing failed.' }
    & $signer verify --verbose --print-certs $outputFile
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    & $align -c -P 16 4 $outputFile
    if ($LASTEXITCODE -ne 0) { throw 'Signed APK alignment verification failed.' }
    Write-Output "Signed APK: $outputFile"
    Write-Output 'Private signing material remains in the current Windows account, outside the repository.'
} finally {
    $env:DESKINK_SIGNING_PASSWORD = $oldPassword
    if (Test-Path -LiteralPath $aligned) { Remove-Item -LiteralPath $aligned -Force }
}
