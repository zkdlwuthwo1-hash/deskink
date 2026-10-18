# DeskInk

**Turn your Windows screen into a whiteboard with iPad and Apple Pencil.**

**English** · [한국어](README.ko.md) · [Illustrated setup guide (한국어)](https://zkdlwuthwo1-hash.github.io/deskink/README.html)

Mirror a Windows monitor to iPad over USB, then annotate it with pen, highlighter, eraser and laser. Pinch and pan on iPad without changing the Windows view. Use your normal mouse and keyboard to control the PC.

## Download

[**Download the lab preview**](https://github.com/zkdlwuthwo1-hash/deskink/releases/tag/v0.3.0-lab.1)

| File | What to do |
| --- | --- |
| `DeskInk-Windows-x64.zip` | Extract the entire ZIP on Windows; run `DeskInk.exe`. |
| `DeskInk-iPad-unsigned.ipa` | Install on iPad using iloader and your own Apple account. |
| `README.html` | Download and open in a browser for instructions with UI captures. |
| `SHA256SUMS.txt` | Checksums of the download files. |

No development tools or Mac are needed to use these downloads. This is an early preview. Windows 11 build 26200, Radeon 740M and two 1080p displays were tested; other Windows versions/GPUs need verification. The capture API used to keep ink out of the video stream requires a recent Windows version.

## Install once

1. **Prepare the PC and iPad.** You need a Windows x64 PC, iPadOS 17+, a compatible Pencil and a data-capable USB cable. Install **Apple Devices** from the Microsoft Store, open it, connect the unlocked iPad and accept **Trust This Computer**. Keep Apple Devices open while using DeskInk.
2. **Install DeskInk on Windows.** Extract the ZIP into a writable folder such as Documents. Run `DeskInk.exe` inside the extracted folder. The first launch downloads about 60 MB of video components from their publisher and verifies the checksum; Internet is required for this step. Later launches reuse them. Keep all files together.
3. **Install DeskInk on iPad.** Download the Windows version of [iloader from its official site](https://iloader.app/). Its Windows instructions require iTunes; follow its prerequisite prompts if Apple components are missing. Select your connected iPad, sign in with **your own Apple account**, and complete two-factor authentication. Use **Import IPA**, select `DeskInk-iPad-unsigned.ipa`, then **Sign & Install App**. Wait for completion. Button labels can vary by iloader version/language.
4. **Trust the developer if asked.** On iPad, open **Settings → General → VPN & Device Management → Developer App → your Apple account → Trust**. Follow any confirmation or restart prompt. Never use the author's Apple account.
5. **Enable Developer Mode — required.** Open **Settings → Privacy & Security → Developer Mode**, turn it on, and tap **Restart**. After restarting, unlock iPad, tap **Turn On** in the confirmation, and enter the device passcode if requested. Then open DeskInk. If the menu is missing, install the IPA with iloader first, try opening DeskInk once, then reopen Settings. A school/work-managed device may restrict this setting. [Apple's instructions](https://developer.apple.com/documentation/xcode/enabling-developer-mode-on-a-device)

## Use each time

1. Connect iPad by **USB**, unlock it, and open Apple Devices and Windows DeskInk.
2. Choose **Screen 1 / Screen 2** in the Windows DeskInk window. Open DeskInk on iPad and enter the pairing code displayed on the PC. Restarting the Windows host requires pairing again.
3. Draw with Pencil. Choose tools from the **bottom toolbar**:

| Tool / gesture | Action |
| --- | --- |
| Pen / highlighter | Persistent ink; choose color and width. |
| Eraser | Erase whole strokes. Lift Pencil to return to the previous tool. Supported Pencil double-tap also toggles eraser. |
| Laser / disappearing pen | Point without permanent ink / leave ink that fades after about two seconds. Hover requires compatible iPad and Pencil hardware. |
| Undo / redo | Correct recent changes on the current monitor. |
| Eye / trash | Hide/show ink / clear the current monitor's ink. Clear can be undone. |
| Two-finger pinch / drag | Zoom (1–4×) / pan **on iPad only**. Tap **화면 맞춤** (Fit) to reset. Fingers do not draw by default. |

To switch monitors from the desktop or Stream Deck, run **`DeskInk-Shortcuts.exe` once**. It creates **DeskInk - Screen 1 / Screen 2** desktop shortcuts. Add those shortcuts to Stream Deck's Open action. They switch the running host without a terminal window; the video reconnects briefly. Keep the extracted folder in the same location.

Ink stays separate for each monitor while the Windows host runs. **Closing Windows DeskInk clears all ink; there is no permanent save.** You can close iloader after installation. Keep DeskInk running and the USB cable connected while presenting.

In Zoom, manually share the **entire monitor** you annotate and verify ink/pointers from another participant's view. Individual-window sharing may omit the overlay.

## Renew the iPad installation

A free Apple account signs development apps for **7 days**. Your Apple account does not expire and this is not an iloader trial. Before expiry, connect iPad to Windows and use iloader with **the same account and IPA** to **Sign & Install App** again. This renews the installation without a subscription. [Apple's free-account limits](https://developer.apple.com/help/account/basics/about-your-developer-account)

## If something does not work

- **iPad is not detected:** unlock it, confirm Trust, open Apple Devices, and try a data cable/another USB port.
- **Developer cannot be trusted / Developer Mode required:** complete steps 4 and 5, including the confirmation after restart.
- **App stopped opening after a week:** renew it with iloader as described above.
- **Wrong monitor:** select Screen 1/2 and verify the actual screen. DeskInk numbering may differ from Windows Display Settings.
- **Windows fails to start:** keep the entire extracted folder together and allow the first-run download. Logs are at `%LOCALAPPDATA%/DeskInk/logs`. Do not publish pairing codes or private screen contents when reporting errors.

Bluetooth, Wi-Fi and Ethernet pen connections are not implemented in this preview. End-to-end latency, every iPad model and the remote Zoom view are not covered by automated tests.

[Report an issue](https://github.com/zkdlwuthwo1-hash/deskink/issues) · [Build and test from source](docs/development.md) · [iPad cloud build](docs/windows-only-ipad.md)

## Credits and licenses

Based on [EternalMonitor](https://github.com/sidebandstudio/EternalMonitor), commit `9888f37a5a457c76ec1d960dc9d9f1115304e6a9`. Its [MIT notice](third-party/EternalMonitor-LICENSE) is preserved. The ZIP includes dependency notices under `licenses/`; iPad notices are [here](ios/EternalMonitor/Resources/ThirdPartyNotices.txt). Video components are downloaded separately from [BtbN/FFmpeg-Builds](https://github.com/BtbN/FFmpeg-Builds) on first launch. Each third-party component retains its own license. A repository-wide license for newly added DeskInk code has not yet been assigned.
