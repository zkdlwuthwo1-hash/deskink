# DeskInk

**Turn your Windows screen into a whiteboard with iPad/Apple Pencil or an Android tablet/S Pen.**

**English** · [한국어](README.ko.md) · [Illustrated setup guide (한국어)](https://zkdlwuthwo1-hash.github.io/deskink/README.html)

Mirror a Windows monitor to your tablet over USB, then annotate it with pen, highlighter, eraser and laser. Pinch and pan on the tablet without changing the Windows view. Use your normal mouse and keyboard to control the PC.

## Features

- Annotate Windows with a pen, highlighter, eraser, laser pointer and disappearing ink.
- Pinch and pan on the tablet only; the PC display stays at its original scale.
- Switch monitors using desktop shortcuts, with separate ink for each screen while DeskInk runs.
- Hold the S Pen button to erase, or double-tap a supported Apple Pencil to switch tools.

## Download

Click a link below to download the file directly. Get the **Windows ZIP, iPad IPA and iloader on your PC**, and the **Android APK on your tablet**.

| Download | What to do |
| --- | --- |
| [**Download Windows app ZIP**](https://github.com/zkdlwuthwo1-hash/deskink/releases/download/v0.4.0-lab.1/DeskInk-Windows-x64.zip?v=0.4.3) | Extract the entire ZIP and follow the device-specific steps below. |
| [**Download Android app APK**](https://github.com/zkdlwuthwo1-hash/deskink/releases/download/v0.4.0-lab.1/DeskInk-Android.apk?v=0.4.2) | Open on an Android 8+ tablet and tap **Install**. |
| [**Download iPad app IPA**](https://github.com/zkdlwuthwo1-hash/deskink/releases/download/v0.4.0-lab.1/DeskInk-iPad-unsigned.ipa?v=0.4.2) | Import into iloader on your PC to install on iPad. |
| [**Download iloader Windows installer**](https://github.com/nab138/iloader/releases/latest/download/iloader-windows-x64.exe) | Run the EXE from the official publisher on your PC to prepare iPad installation. |
| [**Download illustrated HTML guide**](https://github.com/zkdlwuthwo1-hash/deskink/releases/download/v0.4.0-lab.1/README.html) | Open in a browser; screenshots are included in the file. |

**Windows 0.4.3:** fixes delayed PC ink when the settings window is minimized. Close Windows DeskInk, extract the entire new ZIP and launch it. Closing clears existing ink. iPad and Android users already on 0.4.2 do not need to reinstall their tablet app for this fix. Long-session slowdown affecting both devices remains under investigation.

You need a recent Windows x64 PC, an iPad running iPadOS 17+ or an Android 8+ tablet, a compatible pen and a data-capable USB cable. **No Mac or development tools are needed.** Initial PC setup requires Internet access. [All download files](https://github.com/zkdlwuthwo1-hash/deskink/releases/tag/v0.4.0-lab.1) · [Official iloader instructions](https://iloader.app/)

## Install on iPad

1. **Prepare the PC and iPad.** You need a Windows x64 PC, iPadOS 17+, a compatible Pencil and a data-capable USB cable. Connect the unlocked iPad and accept **Trust This Computer** if prompted. **The Apple Devices app itself is not mandatory.** If DeskInk already detects your iPad, no additional app installation or launch is needed. Otherwise, check the Apple USB drivers and device service supplied by iTunes or Apple Devices.
2. **Install DeskInk on Windows.** Extract the ZIP into a writable folder such as Documents. Run `DeskInk.exe` inside the extracted folder. The first launch downloads about 60 MB of video components from their publisher and verifies the checksum; Internet is required for this step. Later launches reuse them. Keep all files together.
3. **Install DeskInk on iPad.** Download and run the [iloader Windows installer](https://github.com/nab138/iloader/releases/latest/download/iloader-windows-x64.exe). Its Windows instructions require iTunes; follow the [official setup instructions](https://iloader.app/) if Apple components are missing. Select your connected iPad. The illustrated first-login example uses **Anisette Server → Jkcoxson (Windows)**; leave a working login's server unchanged. Sign in with **your own Apple account** and complete two-factor authentication. Use **Import IPA**, select the downloaded `DeskInk-iPad-unsigned.ipa`, then **Sign & Install App**. Wait for completion. [See the marked settings and installation buttons](https://zkdlwuthwo1-hash.github.io/deskink/README.html#iloader). Button labels can vary by iloader version/language.
4. **Trust the developer if asked.** On iPad, open **Settings → General → VPN & Device Management → Developer App → your Apple account → Trust**. Follow any confirmation or restart prompt. Never use the author's Apple account.
5. **Enable Developer Mode — required.** Open **Settings → Privacy & Security → Developer Mode**, turn it on, and tap **Restart**. After restarting, unlock iPad, tap **Turn On** in the confirmation, and enter the device passcode if requested. Then open DeskInk. If the menu is missing, install the IPA with iloader first, try opening DeskInk once, then reopen Settings. A school/work-managed device may restrict this setting. [Apple's instructions](https://developer.apple.com/documentation/xcode/enabling-developer-mode-on-a-device)

## Install on Galaxy Tab / Android

1. Tap **[Download Android APK](https://github.com/zkdlwuthwo1-hash/deskink/releases/download/v0.4.0-lab.1/DeskInk-Android.apk?v=0.4.2) on your tablet**, open the downloaded `DeskInk-Android.apk`, and tap **Install**. Allow installation from the browser/file app when prompted. No Apple account, iloader or weekly renewal is needed.
2. Open **Settings → About tablet → Software information → tap Build number seven times**, then enter the device passcode. Enable **Developer options → USB debugging**. Menu names vary by device. [Samsung instructions](https://www.samsung.com/uk/support/mobile-devices/how-do-i-turn-on-the-developer-options-menu-on-my-samsung-galaxy-device/)
3. Extract the entire Windows ZIP and run **`DeskInk-Android.exe` once**. It downloads and verifies Google's USB components (about 8 MB) and the video runtime (about 60 MB); Internet is needed for setup.
4. Connect a USB data cable and **allow USB debugging from your PC** on the tablet. Open DeskInk and enter the PC's six-digit pairing code. Connect only one Android tablet over USB at a time.
5. **Hold the S Pen button to erase; release to restore the previous tool.** Pens without a button can use the toolbar eraser. If Windows cannot detect the tablet, check [Samsung's official USB driver](https://developer.samsung.com/android-usb-driver).

After setup, use either **DeskInk - Screen 1 / Screen 2** desktop icon. Android does not use Apple Devices. Authorize only PCs you trust; USB debugging can be turned off when no longer needed. Install a newer APK with the same signing identity over the existing app to update. **Android 0.4.1** adds an icon dock, color and width previews, and a redesigned connection screen.

## Connect with the pairing code

1. **Find the code on your PC:** open the Windows DeskInk window and look for **`Pairing code: six digits`** below the connection status. Restore the window from the taskbar if minimized.
2. **Open pairing on your tablet:** connect USB and open DeskInk. On iPad, use **Pair with your PC**. On Android, use the automatic prompt or **Pair PC / PC 연결** on the welcome screen. While disconnected, you can also tap the connection status at the top left.
3. **Enter all six digits:** include any leading zero. Tap **Pair iPad** on iPad or **Connect / 연결** on Android. This is separate from Apple account two-factor authentication.
4. **Check the connection:** Windows appears on the tablet and the PC says **Tablet connected**. Pair once per PC; the tablet remembers two PCs and reconnects after Windows DeskInk restarts. The displayed code may change without affecting registered tablets.

**Updating from a version before 0.4.2:** update Windows and the tablet app, then enter the code once on each PC. After that, USB connection and opening DeskInk are enough. **Forget paired tablets** in the Windows app removes registration and requires a new code. Reinstalling/resetting app data or changing Windows users can also require registration again.

If a code is rejected, recheck the current digits in the PC window. For **Too many attempts**, wait one minute before retrying. Some iPad instructions still say EternalMonitor; find the code in Windows DeskInk. [Illustrated pairing and troubleshooting (한국어)](https://zkdlwuthwo1-hash.github.io/deskink/README.html#pairing)

## Use each time

1. Connect and unlock your tablet by **USB**, then open DeskInk on the PC and tablet.
2. Choose **Screen 1 / Screen 2** in the Windows DeskInk window. Registered tablets connect automatically. Enter the code only when registering a new PC or after forgetting registration.
3. Draw with your pen. Choose tools from the **bottom toolbar**:

| Tool / gesture | Action |
| --- | --- |
| Pen / highlighter | Persistent ink; choose color and width. |
| Eraser | Erase whole strokes. The toolbar eraser returns to the previous tool on pen lift. Hold the S Pen button for momentary erasing; release to return. Supported Pencil double-tap also toggles eraser. |
| Laser / disappearing pen | Point without permanent ink / leave ink that fades after about two seconds. Hover requires compatible tablet and pen hardware. |
| Undo / redo | Correct recent changes on the current monitor. |
| Eye / trash | Hide/show ink / clear the current monitor's ink. Clear can be undone. |
| Two-finger pinch / drag | Zoom (1–4×) / pan **on the tablet only**. Tap **화면 맞춤** (Fit) to reset; on Android, use the four-corners icon at the top right. Fingers do not draw by default. |

**Desktop screen switching:** The first Windows launch automatically creates **DeskInk - Screen 1 / Screen 2** desktop icons. Anyone can double-click either icon to choose that display, without a terminal window. Stream Deck users may optionally register the same shortcuts as Open actions. Video reconnects briefly. Keep the extracted folder in the same location; run `DeskInk-Shortcuts.exe` if the icons need to be recreated.

Ink stays separate for each monitor while the Windows host runs. **Closing Windows DeskInk clears all ink; there is no permanent save.** You can close iloader after installation. Keep DeskInk running and the USB cable connected while presenting.

In Zoom, manually share the **entire monitor** you annotate and verify ink/pointers from another participant's view. Individual-window sharing may omit the overlay.

## Renew the iPad installation

A free Apple account signs development apps for **7 days**. Your Apple account does not expire and this is not an iloader trial. Before expiry, connect iPad to Windows and use iloader with **the same account and IPA** to **Sign & Install App** again. This renews the installation without a subscription. [Apple's free-account limits](https://developer.apple.com/help/account/basics/about-your-developer-account)

## If something does not work

- **Android is not detected:** run `DeskInk-Android.exe`, enable USB debugging, authorize this PC, and check the data cable/Samsung USB driver. Keep the tablet app open.
- **iPad is not detected:** unlock it, confirm Trust, and try a data cable/another USB port. If it is still missing, check the Apple USB components supplied by iTunes or Apple Devices. [Apple device service troubleshooting](https://support.apple.com/en-us/102347)
- **Keeping Apple Devices open:** if DeskInk connects without opening it, continue as usual. Only keep it running/minimized if closing that app interrupts USB connectivity on your PC.
- **Developer cannot be trusted / Developer Mode required:** complete steps 4 and 5, including the confirmation after restart.
- **App stopped opening after a week:** renew it with iloader as described above.
- **Wrong monitor:** select Screen 1/2 and verify the actual screen. DeskInk numbering may differ from Windows Display Settings.
- **Windows fails to start:** keep the entire extracted folder together and allow the first-run download. Logs are at `%LOCALAPPDATA%/DeskInk/logs`. Do not publish pairing codes or private screen contents when reporting errors.

DeskInk currently connects over **USB**. [Report an issue](https://github.com/zkdlwuthwo1-hash/deskink/issues) · [Compatibility and verification](docs/android-verification.md) · [Development guide](docs/development.md)

## Credits and licenses

Based on [EternalMonitor](https://github.com/sidebandstudio/EternalMonitor), commit `9888f37a5a457c76ec1d960dc9d9f1115304e6a9`. Its [MIT notice](third-party/EternalMonitor-LICENSE) is preserved. The ZIP includes dependency notices under `licenses/`; iPad notices are [here](ios/EternalMonitor/Resources/ThirdPartyNotices.txt), and the APK contains [Android notices](android/app/src/main/assets/ThirdPartyNotices.txt). Video components are downloaded separately from [BtbN/FFmpeg-Builds](https://github.com/BtbN/FFmpeg-Builds) on first launch. The Android setup helper downloads Google [Platform Tools](https://developer.android.com/tools/releases/platform-tools) separately and keeps its NOTICE.txt. Each third-party component retains its own license. A repository-wide license for newly added DeskInk code has not yet been assigned.
