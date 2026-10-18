# DeskInk

**Turn your Windows screen into a whiteboard with iPad/Apple Pencil or an Android tablet/S Pen.**

**English** · [한국어](README.ko.md) · [Illustrated setup guide (한국어)](https://zkdlwuthwo1-hash.github.io/deskink/README.html)

Mirror a Windows monitor to your tablet over USB, then annotate it with pen, highlighter, eraser and laser. Pinch and pan on the tablet without changing the Windows view. Use your normal mouse and keyboard to control the PC.

## Download

[**Download the lab preview**](https://github.com/zkdlwuthwo1-hash/deskink/releases/tag/v0.4.0-lab.1)

| File | What to do |
| --- | --- |
| `DeskInk-Windows-x64.zip` | Extract the entire ZIP on Windows; run `DeskInk.exe`. |
| `DeskInk-iPad-unsigned.ipa` | Install on iPad using iloader and your own Apple account. |
| `DeskInk-Android.apk` | Open on an Android 8+ tablet to install. |
| `README.html` | Download and open in a browser for instructions with UI captures. |
| `SHA256SUMS.txt` | Checksums of the download files. |

No development tools or Mac are needed to use these downloads. This is an early preview. Windows 11 build 26200, Radeon 740M and two 1080p displays were tested; other Windows versions/GPUs need verification. The capture API used to keep ink out of the video stream requires a recent Windows version.

## Install on iPad

1. **Prepare the PC and iPad.** You need a Windows x64 PC, iPadOS 17+, a compatible Pencil and a data-capable USB cable. Install **Apple Devices** from the Microsoft Store, open it, connect the unlocked iPad and accept **Trust This Computer**. Keep Apple Devices open while using DeskInk.
2. **Install DeskInk on Windows.** Extract the ZIP into a writable folder such as Documents. Run `DeskInk.exe` inside the extracted folder. The first launch downloads about 60 MB of video components from their publisher and verifies the checksum; Internet is required for this step. Later launches reuse them. Keep all files together.
3. **Install DeskInk on iPad.** Download the Windows version of [iloader from its official site](https://iloader.app/). Its Windows instructions require iTunes; follow its prerequisite prompts if Apple components are missing. Select your connected iPad, sign in with **your own Apple account**, and complete two-factor authentication. Use **Import IPA**, select `DeskInk-iPad-unsigned.ipa`, then **Sign & Install App**. Wait for completion. Button labels can vary by iloader version/language.
4. **Trust the developer if asked.** On iPad, open **Settings → General → VPN & Device Management → Developer App → your Apple account → Trust**. Follow any confirmation or restart prompt. Never use the author's Apple account.
5. **Enable Developer Mode — required.** Open **Settings → Privacy & Security → Developer Mode**, turn it on, and tap **Restart**. After restarting, unlock iPad, tap **Turn On** in the confirmation, and enter the device passcode if requested. Then open DeskInk. If the menu is missing, install the IPA with iloader first, try opening DeskInk once, then reopen Settings. A school/work-managed device may restrict this setting. [Apple's instructions](https://developer.apple.com/documentation/xcode/enabling-developer-mode-on-a-device)

## Install on Galaxy Tab / Android

1. Open **`DeskInk-Android.apk` on the tablet**. Allow installation from the browser/file app when prompted. No Apple account, iloader or weekly renewal is needed.
2. Open **Settings → About tablet → Software information → tap Build number seven times**, then enter the device passcode. Enable **Developer options → USB debugging**. Menu names vary by device. [Samsung instructions](https://www.samsung.com/uk/support/mobile-devices/how-do-i-turn-on-the-developer-options-menu-on-my-samsung-galaxy-device/)
3. Extract the entire Windows ZIP and run **`DeskInk-Android.exe` once**. It downloads and verifies Google's USB components (about 8 MB) and the video runtime (about 60 MB); Internet is needed for setup.
4. Connect a USB data cable and **allow USB debugging from your PC** on the tablet. Open DeskInk and enter the PC's six-digit pairing code. Connect only one Android tablet over USB at a time.
5. **Hold the S Pen button to erase; release to restore the previous tool.** Pens without a button can use the toolbar eraser. If Windows cannot detect the tablet, check [Samsung's official USB driver](https://developer.samsung.com/android-usb-driver).

After setup, use either **DeskInk - Screen 1 / Screen 2** desktop icon. Android does not use Apple Devices. Authorize only PCs you trust; USB debugging can be turned off when no longer needed. Install a newer APK with the same signing identity over the existing app to update.

Android behavior is checked automatically and in an emulator. **Real Galaxy Tab S Pen events, pen feel and end-to-end latency still require physical-device verification.**

## Use each time

1. Connect and unlock your tablet by **USB**, then open Windows DeskInk. iPad users also keep Apple Devices open.
2. Choose **Screen 1 / Screen 2** in the Windows DeskInk window. Open DeskInk on the tablet and enter the pairing code displayed on the PC. Restarting the Windows host requires pairing again.
3. Draw with your pen. Choose tools from the **bottom toolbar**:

| Tool / gesture | Action |
| --- | --- |
| Pen / highlighter | Persistent ink; choose color and width. |
| Eraser | Erase whole strokes. The toolbar eraser returns to the previous tool on pen lift. Hold the S Pen button for momentary erasing; release to return. Supported Pencil double-tap also toggles eraser. |
| Laser / disappearing pen | Point without permanent ink / leave ink that fades after about two seconds. Hover requires compatible tablet and pen hardware. |
| Undo / redo | Correct recent changes on the current monitor. |
| Eye / trash | Hide/show ink / clear the current monitor's ink. Clear can be undone. |
| Two-finger pinch / drag | Zoom (1–4×) / pan **on the tablet only**. Tap **화면 맞춤** (Fit) to reset. Fingers do not draw by default. |

**Desktop screen switching:** The first Windows launch automatically creates **DeskInk - Screen 1 / Screen 2** desktop icons. Anyone can double-click either icon to choose that display, without a terminal window. Stream Deck users may optionally register the same shortcuts as Open actions. Video reconnects briefly. Keep the extracted folder in the same location; run `DeskInk-Shortcuts.exe` if the icons need to be recreated.

Ink stays separate for each monitor while the Windows host runs. **Closing Windows DeskInk clears all ink; there is no permanent save.** You can close iloader after installation. Keep DeskInk running and the USB cable connected while presenting.

In Zoom, manually share the **entire monitor** you annotate and verify ink/pointers from another participant's view. Individual-window sharing may omit the overlay.

## Renew the iPad installation

A free Apple account signs development apps for **7 days**. Your Apple account does not expire and this is not an iloader trial. Before expiry, connect iPad to Windows and use iloader with **the same account and IPA** to **Sign & Install App** again. This renews the installation without a subscription. [Apple's free-account limits](https://developer.apple.com/help/account/basics/about-your-developer-account)

## If something does not work

- **Android is not detected:** run `DeskInk-Android.exe`, enable USB debugging, authorize this PC, and check the data cable/Samsung USB driver. Keep the tablet app open.
- **iPad is not detected:** unlock it, confirm Trust, open Apple Devices, and try a data cable/another USB port.
- **Developer cannot be trusted / Developer Mode required:** complete steps 4 and 5, including the confirmation after restart.
- **App stopped opening after a week:** renew it with iloader as described above.
- **Wrong monitor:** select Screen 1/2 and verify the actual screen. DeskInk numbering may differ from Windows Display Settings.
- **Windows fails to start:** keep the entire extracted folder together and allow the first-run download. Logs are at `%LOCALAPPDATA%/DeskInk/logs`. Do not publish pairing codes or private screen contents when reporting errors.

Bluetooth, Wi-Fi and Ethernet pen connections are not implemented in this preview. End-to-end latency, every tablet model and the remote Zoom view are not covered by automated tests.

[Report an issue](https://github.com/zkdlwuthwo1-hash/deskink/issues) · [Build and test from source](docs/development.md) · [iPad cloud build](docs/windows-only-ipad.md)

## Credits and licenses

Based on [EternalMonitor](https://github.com/sidebandstudio/EternalMonitor), commit `9888f37a5a457c76ec1d960dc9d9f1115304e6a9`. Its [MIT notice](third-party/EternalMonitor-LICENSE) is preserved. The ZIP includes dependency notices under `licenses/`; iPad notices are [here](ios/EternalMonitor/Resources/ThirdPartyNotices.txt), and the APK contains [Android notices](android/app/src/main/assets/ThirdPartyNotices.txt). Video components are downloaded separately from [BtbN/FFmpeg-Builds](https://github.com/BtbN/FFmpeg-Builds) on first launch. The Android setup helper downloads Google [Platform Tools](https://developer.android.com/tools/releases/platform-tools) separately and keeps its NOTICE.txt. Each third-party component retains its own license. A repository-wide license for newly added DeskInk code has not yet been assigned.
