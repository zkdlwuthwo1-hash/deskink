# DeskInk development / 소스 빌드

End-user downloads and setup: [English](../README.md) · [한국어](../README.ko.md).

## Windows

Use the Rust MSVC toolchain in `rust-toolchain.toml`, Visual Studio C++ Build Tools with the Windows SDK, libclang and an FFmpeg 7.1 LGPL shared SDK. `scripts/windows-env.ps1` expects these local, Git-ignored paths:

```text
.tools/ffmpeg/ffmpeg-n7.1.5-12-g1fdbca85aa-win64-lgpl-shared-7.1/{include,lib,bin}
.tools/libclang/clang/native/libclang.dll
```

The SDK is from [BtbN/FFmpeg-Builds](https://github.com/BtbN/FFmpeg-Builds). Development dependencies are not installed automatically.

```powershell
./scripts/build-windows.ps1 -Release
./scripts/test-windows.ps1
./scripts/run-windows.ps1 -Release -Monitor 0
```

Executables: `target/monitor-switch/release/deskink.exe` and `deskink-launcher.exe`. The separate upstream `eternal-host` executable does not expose DeskInk's workflow. `scripts/install-desktop-shortcuts.ps1` creates development shortcuts bound to this checkout; the portable package uses its own helper.

## iPad

On Mac, install Xcode and XcodeGen, then:

```bash
bash scripts/build-ipad.sh
DESKINK_TEST_FILTER=EternalMonitorTests,EternalMonitorUITests/DeskInkViewportUITests bash scripts/test-ipad.sh
bash scripts/build-ipad-unsigned.sh
```

Alternatively run **Actions → Build iPad app → Run workflow** on your fork. The workflow uses macOS 15 / Xcode 16.4, tests the app and produces an unsigned device IPA. Artifacts are retained for three days; release assets are separate, durable downloads. No Apple credentials are required by the workflow. See [Windows-only build](windows-only-ipad.md) and [Mac signing](mac-build.md).

## Android / Galaxy Tab

Use JDK 17–21, Android SDK platform 35 and build-tools 35.0.0. The committed Gradle 8.11.1 wrapper checks the official distribution SHA-256. Set `ANDROID_HOME` or an ignored `android/local.properties` containing `sdk.dir`. No Mac is required.

```powershell
cd android
./gradlew.bat :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
# With an emulator or an authorized development device:
./gradlew.bat :app:connectedDebugAndroidTest
```

On Windows, keep the checkout and SDK paths ASCII-only. A non-ASCII checkout can compile but fail to load test classes in a Gradle worker. An alternative is `subst X: C:\path\to\checkout` **only if X: is unused**, set the SDK path through that alias, and run Gradle with `-p X:/android`. Remove only that mapping with `subst X: /D` after all builds/daemons stop. Do not delete the checkout.

Actions → **Build and test Android app** runs Java/Robolectric tests and real MediaCodec + USB protocol integration on an API 35 emulator. Its screenshots use a known test frame. This does not measure physical S Pen latency. See [Android verification](android-verification.md).

The release build is unsigned until explicitly signed:

```powershell
./scripts/sign-android.ps1 -OutputApk "$PWD/artifacts/local/releases/v0.4.0-lab.1/DeskInk-Android.apk"
```

The signing helper creates/reuses `%LOCALAPPDATA%/DeskInk/signing/deskink-android.p12`, with a per-user Windows DPAPI-encrypted password and restricted directory permissions. Only the signed APK goes into release assets; never upload the key or password file. Preserve this signing identity for future in-place updates. DPAPI protection is tied to the Windows account; back up signing material through a secure process, not a public repository. The script verifies both the APK signature and alignment using Android build-tools.

The Android app listens on loopback TCP 9877 while foregrounded. Windows discovers one USB device via `adb -d`, pins its serial and owns one ephemeral forwarding port. Every disconnect removes only that port. Protocol pairing is still mandatory; Wi-Fi ADB devices are not selected. The Android setup helper installs verified Google ADB tools separately, without changing global PATH or stopping another application's ADB server.

## Package a release

```powershell
python scripts/render-guide.py
./scripts/build-windows.ps1 -Release
python scripts/package-windows.py --version v0.4.0-lab.1 --crt "C:/path/to/VC/Redist/MSVC/version/x64/Microsoft.VC143.CRT/vcruntime140.dll"
```

Use the original redistributable x64 DLL, not a debug runtime. The script copies an explicit allowlist, includes locked dependency license texts, hashes all files, and writes the source commit to `build-info.json`. Package a clean checkout for release. It never bundles development tools, logs, credentials or FFmpeg binaries. The first-run helper fetches a pinned publisher archive and validates SHA-256 before extraction.

Verify the extracted ZIP from a path with spaces and non-ASCII characters, with development directories removed from PATH:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "path/to/DeskInk/scripts/start-portable.ps1" -CheckOnly
python scripts/check-launcher-window.py "path/to/DeskInk/DeskInk.exe"
```

The probe downloads the runtime when absent and enumerates real monitors. Also test a normal first launch, repeat launch and Screen 1/2 switching. Close only the test host before checking another package. Never upload runtime logs containing pairing codes.

## Source map

| Directory | Responsibility |
| --- | --- |
| `crates/ink-core` | Ink state, tools, undo/redo, wire commands |
| `crates/windows-platform` | Overlay, capture, monitor control, native launcher |
| `host` | Video streaming and DeskInk Windows entry point |
| `ios` | Native iPad app, Pencil input, Metal video |
| `android/core` | Shared Java protocol, ink, S Pen mode and viewport logic |
| `android/app` | Native Android Canvas, toolbar, stylus events and MediaCodec |
| `proto` | Shared wire protocol and fixtures |
| `scripts` | Build, test, package and documentation generation |

Guide screenshots are unmodified CI attachments from build 12, using test backgrounds rather than a personal desktop. See [screenshot provenance](images/README.md). Build 13 only adds the upstream license notice; the illustrated UI is unchanged.

Android screenshots come from the API 35 emulator run linked in the same provenance document. The Android APK does not change the existing iPad build 13 IPA.
