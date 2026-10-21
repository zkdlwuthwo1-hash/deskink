# DeskInk

**Turn your Windows screen into a whiteboard with iPad/Apple Pencil or an Android tablet/S Pen.**

**English** · [한국어](README.ko.md) · [Illustrated setup guide (한국어)](https://zkdlwuthwo1-hash.github.io/deskink/README.html)

Mirror a Windows monitor to your tablet over USB or your local Wi-Fi/LAN, then annotate it with pen, highlighter, eraser and laser. Pinch and pan on the tablet without changing the Windows view. Use your normal mouse and keyboard to control the PC.

## Features

- Annotate Windows with a pen, highlighter, eraser, laser pointer and disappearing ink.
- Pinch and pan on the tablet only; the PC display stays at its original scale.
- Switch monitors using desktop shortcuts, with separate ink for each screen while DeskInk runs.
- Hold the S Pen button to erase, or double-tap a supported Apple Pencil to switch tools.
- Prefer USB automatically and reconnect to the remembered PC over LAN when USB is unavailable.
- Check for Windows updates automatically and install them from the app when ready.
- Keep the existing USB connection, or enable local-network access and connect by QR code (iPad) or IP address (iPad/Android).

## Download

Use the links below for downloads and official installation pages. Get the **Windows ZIP, iPad IPA, iloader and Apple Devices on your PC**, and the **Android APK on your tablet**.

| Download | What to do |
| --- | --- |
| [**Download Windows app ZIP**](https://github.com/zkdlwuthwo1-hash/deskink/releases/download/v0.5.1/DeskInk-Windows-x64.zip?v=0.5.1) | Extract the entire ZIP and follow the device-specific steps below. |
| [**Download Android app APK**](https://github.com/zkdlwuthwo1-hash/deskink/releases/download/v0.5.1/DeskInk-Android.apk?v=0.5.1) | Open on an Android 8+ tablet and tap **Install**. |
| [**Download iPad app IPA**](https://github.com/zkdlwuthwo1-hash/deskink/releases/download/v0.5.1/DeskInk-iPad-unsigned.ipa?v=0.5.1) | Import into iloader on your PC to install on iPad. |
| [**Download iloader Windows installer**](https://github.com/nab138/iloader/releases/latest/download/iloader-windows-x64.exe) | Run the EXE from the official publisher on your PC to prepare iPad installation. |
| [**Install Apple Devices · Microsoft Store**](https://apps.microsoft.com/detail/9np83lwlpz9k) | Prepare the first iPad USB connection and check detection. Skip additional setup if your PC already detects iPad correctly. |
| [**Download illustrated HTML guide**](https://github.com/zkdlwuthwo1-hash/deskink/releases/download/v0.5.1/README.html) | Open in a browser; screenshots are included in the file. |

**0.5.1 adds USB-first automatic connection and Windows in-app updates.** Update both the Windows and tablet apps for the new connection behavior. If you use an older Windows version, close it once and extract the entire new ZIP over the existing DeskInk folder, keeping its location so your shortcuts still work. Launch that folder's DeskInk.exe. Later Windows updates are checked automatically: choose **Download update**, then **Install and restart** when ready. Pairing information and desktop shortcuts are kept. Restarting clears current ink. iPad still updates through iloader, and Android by installing the new APK over the existing app. Long-session slowdown affecting both devices remains under investigation.

You need a recent Windows x64 PC, an iPad running iPadOS 17+ or an Android 8+ tablet, and a compatible pen. Use a data-capable USB cable for USB connection and iPad installation, or a trusted local network for Wi-Fi/LAN. **No Mac or development tools are needed.** Initial PC setup requires Internet access. [All download files](https://github.com/zkdlwuthwo1-hash/deskink/releases/tag/v0.5.1) · [Official iloader instructions](https://iloader.app/)

## Install on iPad

**Lightning iPads use the same IPA and installation steps.** iPadOS 17+ is required. Use a **data-capable** USB-A–Lightning cable for a USB-A PC port, or USB-C–Lightning for a USB-C port. [Apple's USB connection guide](https://support.apple.com/en-us/guide/ipad/ipad756c56a8/17.0/ipados/17.0). Apple Pencil (1st generation) does not support double-tap switching; use the eraser in the bottom toolbar. [Apple Pencil features](https://support.apple.com/en-us/guide/ipad/ipad63913965/ipados).

`Connection lost — USB disconnected` after pairing does not necessarily mean a bad cable. **Update the Windows app first**: Intel video encoder failures in versions before 0.4.4 also caused this message. If it persists, check iPad detection in Apple Devices and examine the matching `stdout` and `stderr` files in `%LOCALAPPDATA%\DeskInk\logs`. Redact device and connection details before posting logs publicly.

1. **Check iPad detection in Apple Devices.** On a Windows PC you are setting up for the first time, install and open [Apple Devices](https://apps.microsoft.com/detail/9np83lwlpz9k). Connect iPad with a data-capable USB cable, unlock it, accept **Trust This Computer**, and enter the device passcode if prompted. Confirm that **iPad appears in the Apple Devices sidebar**. [See the marked connection example](https://zkdlwuthwo1-hash.github.io/deskink/README.html#apple-devices). If iloader and DeskInk already detect iPad correctly, no additional installation is needed; iTunes or another existing Apple setup may already provide the USB components. [Apple's instructions](https://support.apple.com/en-us/108643)
2. **Install DeskInk on Windows.** Extract the ZIP into a writable folder such as Documents. Run `DeskInk.exe` inside the extracted folder. The first launch downloads about 60 MB of video components from their publisher and verifies the checksum; Internet is required for this step. Later launches reuse them. Keep all files together.
3. **Install DeskInk on iPad.** Download and run the [iloader Windows installer](https://github.com/nab138/iloader/releases/latest/download/iloader-windows-x64.exe). Its Windows instructions require iTunes; follow the [official setup instructions](https://iloader.app/) if Apple components are missing. Select your connected iPad. The illustrated first-login example uses **Anisette Server → Jkcoxson (Windows)**; leave a working login's server unchanged. Sign in with **your own Apple account** and complete two-factor authentication. Use **Import IPA**, select the downloaded `DeskInk-iPad-unsigned.ipa`, then **Sign & Install App**. Wait for completion. [See the marked settings and installation buttons](https://zkdlwuthwo1-hash.github.io/deskink/README.html#iloader). Button labels can vary by iloader version/language.
4. **Trust the developer if asked.** On iPad, open **Settings → General → VPN & Device Management → Developer App → your Apple account → Trust**. Follow any confirmation or restart prompt. Never use the author's Apple account.
5. **Enable Developer Mode — required.** Open **Settings → Privacy & Security → Developer Mode**, turn it on, and tap **Restart**. After restarting, unlock iPad, tap **Turn On** in the confirmation, and enter the device passcode if requested. Then open DeskInk. If the menu is missing, install the IPA with iloader first, try opening DeskInk once, then reopen Settings. A school/work-managed device may restrict this setting. [Apple's instructions](https://developer.apple.com/documentation/xcode/enabling-developer-mode-on-a-device)

## Install on Galaxy Tab / Android

For **Wi-Fi only**, install the APK, extract the Windows ZIP, run `DeskInk.exe`, and follow [Connect over Wi-Fi / LAN](#connect-over-wi-fi--lan). USB debugging, Android USB components and `DeskInk-Android.exe` are only needed for **USB**. The following steps set up USB:

1. Tap **[Download Android APK](https://github.com/zkdlwuthwo1-hash/deskink/releases/download/v0.5.1/DeskInk-Android.apk?v=0.5.1) on your tablet**, open the downloaded `DeskInk-Android.apk`, and tap **Install**. Allow installation from the browser/file app when prompted. No Apple account, iloader or weekly renewal is needed.
2. Open **Settings → About tablet → Software information → tap Build number seven times**, then enter the device passcode. Enable **Developer options → USB debugging**. Menu names vary by device. [Samsung instructions](https://www.samsung.com/uk/support/mobile-devices/how-do-i-turn-on-the-developer-options-menu-on-my-samsung-galaxy-device/)
3. Extract the entire Windows ZIP and run **`DeskInk-Android.exe` once**. It downloads and verifies Google's USB components (about 8 MB) and the video runtime (about 60 MB); Internet is needed for setup.
4. Connect a USB data cable and **allow USB debugging from your PC** on the tablet. Open DeskInk and enter the PC's six-digit pairing code. Connect only one Android tablet over USB at a time.
5. **Hold the S Pen button to erase; release to restore the previous tool.** Pens without a button can use the toolbar eraser. If Windows cannot detect the tablet, check [Samsung's official USB driver](https://developer.samsung.com/android-usb-driver).

After setup, use either **DeskInk - Screen 1 / Screen 2** desktop icon. Android does not use Apple Devices. Authorize only PCs you trust; USB debugging can be turned off when no longer needed. Install a newer APK with the same signing identity over the existing app to update. **Android 0.4.1** adds an icon dock, color and width previews, and a redesigned connection screen.

## Connect over Wi-Fi / LAN

**Update Windows and your tablet to 0.5.1 first.** USB remains the default; network access starts off. The PC can use Ethernet while the tablet uses Wi-Fi on the **same router and reachable local network**. Internet remote access is not supported.

1. Unplug USB and disconnect any tablet session. In Windows DeskInk, expand **Wi-Fi / LAN** and enable **Allow Wi-Fi / LAN**. The checkbox can only be changed while disconnected. Read the PC's **IPv4 address** and **Port** shown there; the default port is **9876**. Check that the address belongs to the tablet's network, not a VPN/virtual adapter. Use **Refresh IP** after changing networks.
2. **iPad:** click **QR code (iPad)** in Windows, then use **Scan QR code** in iPad DeskInk. Alternatively, enter the Windows address in **PC address** and the displayed number in **Port**, then tap **Connect**. Allow DeskInk's **Local Network** permission if prompted. The QR contains the connection address, not a pairing credential.
3. **Android:** use the **Wi-Fi / LAN** connection option, enter the same PC IP address and port, and connect. Android uses direct IP entry; QR scanning is not included. Use QR/IP entry for this release rather than **Find PCs**.
4. On first use with a PC, enter its current **six-digit pairing code** as described below. Previously registered tablets keep their registration. The Windows picture should appear before you start writing.

Use **trusted local networks only**: the screen and pen traffic are not encrypted. Limit Windows Firewall permission to the extracted package's **`bin/deskink.exe`, UDP 9876, Private networks and the local subnet**; match the rule to the displayed port if it was changed. Do not disable the firewall or configure router port forwarding. Guest Wi-Fi, client isolation, different subnets or a VPN can prevent devices from reaching each other even when both have Internet access. If Windows shows a different address after reconnecting to the network, scan the new QR or enter the new address.

Wi-Fi conditions affect latency; zero delay cannot be guaranteed. Prefer a PC connected by Ethernet and a strong 5/6 GHz tablet signal. If writing stalls or the connection closes after packet loss, improve the local connection or return to USB. The wireless path avoids building a long backlog of old pen input. Automated protocol checks target delivery order, loss recovery and queue limits; **physical Wi-Fi latency, actual S Pen/Apple Pencil feel and Zoom presentation still need device testing**. [Illustrated wireless setup (한국어)](https://zkdlwuthwo1-hash.github.io/deskink/README.html#wireless)

Apple USB services and Android USB debugging are not needed for wireless streaming itself. iPad installation/renewal with iloader still uses the USB installation steps above. Keep the apps open while presenting. After one successful LAN connection, the app remembers that PC address. USB takes priority when its data connection is available; without USB, automatic connection uses the remembered LAN address. Reattaching USB switches back to the cable. Tap X/Disconnect to stop automatic reconnection; connect again when ready. A PC used only over USB needs its IP entered once before wireless fallback can work. If the PC address changes, enter it again. Switching transport establishes a fresh session and can briefly interrupt the picture.

## Connect with the pairing code

1. **Find the code on your PC:** open the Windows DeskInk window and look for **`Pairing code: six digits`** below the connection status. Restore the window from the taskbar if minimized.
2. **Open pairing on your tablet:** connect USB or select the PC via the [Wi-Fi/LAN steps](#connect-over-wi-fi--lan), then open DeskInk. On iPad, use **Pair with your PC**. On Android, use the automatic prompt or **Pair PC / PC 연결** on the welcome screen. While disconnected, you can also tap the connection status at the top left.
3. **Enter all six digits:** include any leading zero. Tap **Pair iPad** on iPad or **Connect / 연결** on Android. This is separate from Apple account two-factor authentication.
4. **Check the connection:** Windows appears on the tablet and the PC says **Tablet connected**. Pair once per PC; the tablet remembers two PCs and reconnects after Windows DeskInk restarts. The displayed code may change without affecting registered tablets.

**Updating from a version before 0.4.2:** update Windows and the tablet app, then enter the code once on each PC. After that, USB connection and opening DeskInk are enough. **Forget paired tablets** in the Windows app removes registration and requires a new code. Reinstalling/resetting app data or changing Windows users can also require registration again.

If a code is rejected, recheck the current digits in the PC window. For **Too many attempts**, wait one minute before retrying. Some iPad instructions still say EternalMonitor; find the code in Windows DeskInk. [Illustrated pairing and troubleshooting (한국어)](https://zkdlwuthwo1-hash.github.io/deskink/README.html#pairing)

## Use each time

1. Open DeskInk on the PC and tablet. Connect and unlock your tablet by **USB**, or use the [Wi-Fi/LAN steps](#connect-over-wi-fi--lan).
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

Ink stays separate for each monitor while the Windows host runs. **Closing Windows DeskInk clears all ink; there is no permanent save.** You can close iloader after installation. Keep DeskInk running and the selected USB or local-network connection available while presenting.

In Zoom, manually share the **entire monitor** you annotate and verify ink/pointers from another participant's view. Individual-window sharing may omit the overlay.

## Renew the iPad installation

A free Apple account signs development apps for **7 days**. Your Apple account does not expire and this is not an iloader trial. Before expiry, connect iPad to Windows and use iloader with **the same account and IPA** to **Sign & Install App** again. This renews the installation without a subscription. [Apple's free-account limits](https://developer.apple.com/help/account/basics/about-your-developer-account)

## If something does not work

- **“Different versions — update both” / “EternalMonitor” appears:** the PC and tablet cannot use the same connection protocol. Install the latest Windows package and tablet app; updating only Windows is not enough. For iPad, import the new IPA into iloader and install it over DeskInk with the same Apple account. Reconnect using **Scan QR code** or the PC's **IP and port**.
- **Wi-Fi/LAN does not connect:** confirm both apps are 0.5.1, Windows **Allow Wi-Fi / LAN** is enabled, and the IP/port match the current PC display. Check iPad Local Network permission, the same reachable local network, guest/client isolation and Windows Firewall's Private-network/local-subnet permission. A pairing code alone cannot select the PC; enter the IP or scan its QR first. [Connection steps](#connect-over-wi-fi--lan)
- **Android is not detected:** run `DeskInk-Android.exe`, enable USB debugging, authorize this PC, and check the data cable/Samsung USB driver. Keep the tablet app open.
- **iPad is missing from the PC or iloader:** [check Apple Devices first](https://zkdlwuthwo1-hash.github.io/deskink/README.html#apple-devices). If iPad is absent from its sidebar, unlock iPad, confirm Trust, and try a data cable/another USB port. If Apple Devices sees iPad, use **Refresh Devices (Ctrl+R)** in iloader; reopen iloader if it is still missing. Follow the [official iloader setup instructions](https://iloader.app/) if it reports missing Apple components.
- **Keeping Apple Devices open:** if DeskInk connects without opening it, continue as usual. Only keep it running/minimized if closing that app interrupts USB connectivity on your PC.
- **Developer cannot be trusted / Developer Mode required:** complete steps 4 and 5, including the confirmation after restart.
- **App stopped opening after a week:** renew it with iloader as described above.
- **Wrong monitor:** select Screen 1/2 and verify the actual screen. DeskInk numbering may differ from Windows Display Settings.
- **Windows fails to start:** keep the entire extracted folder together and allow the first-run download. Logs are at `%LOCALAPPDATA%/DeskInk/logs`. Do not publish pairing codes or private screen contents when reporting errors.

DeskInk supports **USB** and optional **Wi-Fi/LAN**. [Report an issue](https://github.com/zkdlwuthwo1-hash/deskink/issues) · [Compatibility and verification](docs/android-verification.md) · [Development guide](docs/development.md)

## Credits and licenses

Based on [EternalMonitor](https://github.com/sidebandstudio/EternalMonitor), commit `9888f37a5a457c76ec1d960dc9d9f1115304e6a9`. Its [MIT notice](third-party/EternalMonitor-LICENSE) is preserved. The ZIP includes dependency notices under `licenses/`; iPad notices are [here](ios/EternalMonitor/Resources/ThirdPartyNotices.txt), and the APK contains [Android notices](android/app/src/main/assets/ThirdPartyNotices.txt). Video components are downloaded separately from [BtbN/FFmpeg-Builds](https://github.com/BtbN/FFmpeg-Builds) on first launch. The Android setup helper downloads Google [Platform Tools](https://developer.android.com/tools/releases/platform-tools) separately and keeps its NOTICE.txt. Each third-party component retains its own license. A repository-wide license for newly added DeskInk code has not yet been assigned.
