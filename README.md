# DeskInk

**Turn your Windows screen into a whiteboard with an iPad and Apple Pencil.**

**English** · [한국어](README.ko.md)

DeskInk mirrors a Windows monitor to your iPad over USB. Draw on the iPad and your strokes appear in a transparent overlay on the PC. Use it to explain slides, papers, code, and diagrams while controlling the PC with your normal mouse and keyboard.

> **Development preview.** This repository contains source and build instructions. A ready-to-install Windows release and a DeskInk TestFlight invitation have not been published yet. The current iPad build requires personal signing before installation.

## Features

| Feature | Current behavior |
| --- | --- |
| Drawing tools | Pen, highlighter, stroke eraser, laser pointer, and disappearing ink |
| Editing | Color and width selection, undo/redo, clear current screen, and hide/show ink |
| Pencil shortcuts | Double-tap on supported models; lifting the eraser returns to the previous tool |
| iPad view | Pinch to zoom from 1× to 4× and pan with two fingers; Windows keeps its original scale |
| Toolbar | Separate control area below the video so tools do not cover the writing surface |
| Multiple monitors | Switch from Windows controls, desktop shortcuts, or Stream Deck; separate ink and undo history for each monitor |
| Presentations | Overlay intended for full-monitor sharing in Zoom or similar apps; select screen sharing yourself |

The overlay passes mouse input through to the application beneath it. Finger drawing is off by default; two-finger gestures move the iPad view. Pencil hover depends on the iPad and Pencil hardware.

## Requirements and limitations

- **Windows x64:** validated on Windows 11 build 26200 with a Radeon 740M and two 1920×1080 monitors. Other PCs and GPU families still need testing. Capture requires Windows support for excluding the ink overlay from this app's own capture session; older Windows versions are not guaranteed to work.
- **iPadOS 17 or newer**, a compatible Pencil, a USB data cable, and Apple's **Apple Devices** software on Windows. Accept **Trust This Computer** on the iPad.
- The DeskInk annotation workflow currently uses **USB**. Bluetooth, Wi-Fi, and Ethernet annotation are not implemented.
- Switching monitors briefly restarts the stream. It preserves each monitor's ink while the Windows app remains open.
- Ink is stored in memory. **Closing the Windows app clears it.** Durable drawings, saved selection between separate PCs, and restoration after the iPad app terminates are not implemented.
- USB pairing uses the code displayed by the Windows host. Restarting the host requires pairing again.
- Actual Pencil-to-PC latency, compatibility across iPads, and the picture received by a Zoom participant still require physical-device testing. FPS and internal render timings are not end-to-end latency measurements.

## Build the Windows host

Clone the repository and run commands from its root:

```powershell
git clone https://github.com/zkdlwuthwo1-hash/deskink.git
cd deskink
```

Install the Rust MSVC toolchain pinned in [rust-toolchain.toml](rust-toolchain.toml), Visual Studio C++ Build Tools with a Windows SDK, FFmpeg 7.1 **LGPL shared** headers/import libraries/runtime DLLs, and libclang. The scripts currently expect these local paths:

```text
.tools/
  ffmpeg/ffmpeg-n7.1.5-12-g1fdbca85aa-win64-lgpl-shared-7.1/
    include/  lib/  bin/
  libclang/clang/native/libclang.dll
```

These dependencies are **not included in Git**, and there is no automatic dependency installer yet. The FFmpeg SDK used for development comes from [BtbN/FFmpeg-Builds](https://github.com/BtbN/FFmpeg-Builds). Check [scripts/windows-env.ps1](scripts/windows-env.ps1) when preparing the environment. The build/run/launcher scripts use the same fixed FFmpeg directory.

```powershell
./scripts/build-windows.ps1 -Release
./scripts/run-windows.ps1 -Release -Monitor 0
```

The output is `target/monitor-switch/release/deskink.exe`, with a companion `deskink-launcher.exe`. Run **deskink**, the annotation entry point; the retained upstream `eternal-host` application has a different feature set.

For two-monitor desktop shortcuts:

```powershell
./scripts/install-desktop-shortcuts.ps1
```

This creates `DeskInk - 화면 1` and `DeskInk - 화면 2` on the current user's Desktop. Add those shortcuts to Stream Deck's Open action. They select a monitor in the running app without opening a terminal. Monitor arguments are zero-based (`0`, `1`); confirm the selected display on your PC.

## Build and install the iPad app

**With a Mac:** install Xcode, an iPad simulator runtime, and XcodeGen, then run:

```bash
brew install xcodegen
bash scripts/build-ipad.sh
bash scripts/test-ipad.sh
```

Open the generated `ios/DeskInk.xcodeproj`, select the `EternalMonitor` target, and choose your own Team under Signing & Capabilities. Use a bundle identifier available to your account, then run on your connected iPad. The displayed app name is **DeskInk**; some internal target names are retained from the upstream project.

**Without a local Mac:** fork the repository and manually run **Actions → Build iPad app → Run workflow**. The macOS job tests the app and produces an **unsigned** `DeskInk-unsigned.ipa` in the `DeskInk-iPad-…` artifact. Sign and install it locally with your own account using an iPad sideloading tool. Downloading the IPA alone does not install it. Artifacts are retained for three days. The workflow does not require Apple credentials.

The current workflow was validated with Xcode 16.4. It is for development builds, not a configured TestFlight upload pipeline. For Korean walkthroughs, see [Windows-only setup](docs/windows-only-ipad.md) and [Mac setup](docs/mac-build.md).

## First connection

1. Open Apple Devices on Windows, connect the unlocked iPad with a USB data cable, and accept device trust if prompted.
2. Start the Windows host and choose the monitor you want to annotate.
3. Open DeskInk on the iPad and enter the pairing code from the Windows host.
4. Draw a short line, erase it, and try undo. Pinch to zoom and check that the Windows view does not zoom.
5. For a presentation, share the intended **whole monitor** and check from another participant's view that both ink and pointer are visible.

## Sharing with a lab or team

The intended release arrangement is a **Windows ZIP on GitHub Releases plus an iPad TestFlight invitation**. The Windows package still needs to bundle its runtime dependencies and notices and be checked on a PC without development tools.

For an initial hands-on test, each tester can sign the unsigned IPA with their own Apple account. Free Personal Team provisioning expires after **7 days** and requires renewal. [Apple account limitations](https://developer.apple.com/help/account/basics/about-your-developer-account)

For a smoother beta, the publisher can use **TestFlight**. Friends install the free TestFlight app and accept an invitation; they do not need your signing credentials. The first external build needs Apple's beta review, and each build is available for up to **90 days**. This requires App Store Connect, distribution signing, and a toolchain meeting Apple's current submission requirements. [TestFlight overview](https://developer.apple.com/help/app-store-connect/test-a-beta-version/testflight-overview/)

Apple Developer Program membership for the publisher is **US$99 per year**, with local pricing varying by region. TestFlight is a beta channel; App Store distribution is the later route for ongoing general use. [Membership information](https://developer.apple.com/help/account/membership/program-enrollment/)

## Tests and project structure

```powershell
# Windows: core/protocol, annotation, USB, and host tests
./scripts/test-windows.ps1
```

```bash
# Mac: unit tests and the self-contained viewport UI tests
DESKINK_TEST_FILTER=EternalMonitorTests,EternalMonitorUITests/DeskInkViewportUITests \
  bash scripts/test-ipad.sh
```

The [build 12 validation run](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39118670760) passed 164 iPad unit tests, two UI tests, and an unsigned device build. Windows regression checks cover ink editing, overlay pixels, monitor switching, and input contention. Physical-device checks remain separate from automated results.

| Path | Purpose |
| --- | --- |
| `crates/ink-core/` | Stroke state, tools, undo/redo, and input protocol |
| `crates/windows-platform/` | Windows overlay, capture, monitor control, and launcher |
| `host/` | Rust streaming host and the DeskInk entry point |
| `ios/` | Swift/SwiftUI iPad app, Pencil input, and Metal renderer |
| `proto/` | Shared streaming protocol and fixtures |
| `scripts/` | Build, test, run, and shortcut helpers |

[Requirements](docs/requirements.md) · [Verification plan](docs/verification-plan.md) · [Development history](docs/first-stroke-status.md) · [Issues](https://github.com/zkdlwuthwo1-hash/deskink/issues)

Most detailed development notes are in Korean and include dated results from the original development machine. For bug reports, include app versions, Windows/iPadOS versions, GPU, monitor resolution, and reproduction steps. Remove pairing codes, tokens, and personal screen content from attachments.

## Attribution and licenses

DeskInk builds on [EternalMonitor](https://github.com/sidebandstudio/EternalMonitor), imported at commit `9888f37a5a457c76ec1d960dc9d9f1115304e6a9`. Its [MIT copyright and license](third-party/EternalMonitor-LICENSE) are preserved. See [third-party notes](third-party/README.md), the [Geist font license](host/assets/fonts/OFL.txt), and [iPad dependency notices](ios/EternalMonitor/Resources/ThirdPartyNotices.txt).

Dependencies retain their respective licenses; the upstream MIT notice does not cover every bundled component. A repository-wide reuse license for the DeskInk-specific additions has not yet been selected.
