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

## Package a release

```powershell
python scripts/render-guide.py
./scripts/build-windows.ps1 -Release
python scripts/package-windows.py --version v0.3.0-lab.1 --crt "C:/path/to/VC/Redist/MSVC/version/x64/Microsoft.VC143.CRT/vcruntime140.dll"
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
| `proto` | Shared wire protocol and fixtures |
| `scripts` | Build, test, package and documentation generation |

Guide screenshots are unmodified CI attachments from build 12, using test backgrounds rather than a personal desktop. See [screenshot provenance](images/README.md). Build 13 only adds the upstream license notice; the illustrated UI is unchanged.
