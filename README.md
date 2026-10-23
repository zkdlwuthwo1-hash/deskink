# DeskInk

**Turn your Windows screen into a whiteboard with iPad/Apple Pencil or an Android tablet/S Pen.**

**English** · [한국어](README.ko.md) · [Illustrated setup guide (한국어)](https://zkdlwuthwo1-hash.github.io/deskink/README.html)

Mirror a Windows monitor to your tablet over USB or your local Wi-Fi/LAN, then annotate it with pen, highlighter, eraser and laser. Pinch and pan on the tablet without changing the Windows view. Use your normal mouse and keyboard to control the PC.

## Features

- Annotate Windows with a pen, highlighter, eraser, laser pointer and disappearing ink.
- Draw a circle, ellipse, rectangle or line, then hold the pen still for one second to straighten it.
- Select ink with the lasso, move and resize it, and choose from 12 colors and three eraser sizes.
- Pinch and pan on the tablet only; the PC display stays at its original scale.
- Switch monitors using desktop shortcuts, with separate ink for each screen while DeskInk runs.
- Hold the S Pen button to erase, or double-tap a supported Apple Pencil to switch tools.
- Prefer USB automatically and reconnect to the remembered PC over LAN when USB is unavailable.
- Check for Windows and Android updates automatically and install them from the app when ready.
- Keep the existing USB connection, or enable local-network access and connect by QR code (iPad) or IP address (iPad/Android).

## Download

Use the links below for downloads and official installation pages. Get the **Windows ZIP, iPad IPA, iloader and Apple Devices on your PC**, and the **Android APK on your tablet**.

| Download | What to do |
| --- | --- |
| [**Download Windows app ZIP**](https://github.com/zkdlwuthwo1-hash/deskink/releases/download/v0.6.0/DeskInk-Windows-x64.zip?v=0.6.0) | **Start here.** Extract the entire ZIP and follow the Windows PC setup below. |
| [**Download Android app APK**](https://github.com/zkdlwuthwo1-hash/deskink/releases/download/v0.6.0/DeskInk-Android.apk?v=0.6.0) | Open on an Android 8+ tablet and tap **Install**. |
| [**Download iPad app IPA**](https://github.com/zkdlwuthwo1-hash/deskink/releases/download/v0.6.0/DeskInk-iPad-unsigned.ipa?v=0.6.0) | Import into iloader on your PC to install on iPad. |
| [**Download iloader Windows installer**](https://github.com/nab138/iloader/releases/latest/download/iloader-windows-x64.exe) | Run the EXE from the official publisher on your PC to prepare iPad installation. |
| [**Install Apple Devices · Microsoft Store**](https://apps.microsoft.com/detail/9np83lwlpz9k) | Prepare the first iPad USB connection and check detection. Skip additional setup if your PC already detects iPad correctly. |
| [**Download illustrated HTML guide**](https://github.com/zkdlwuthwo1-hash/deskink/releases/download/v0.6.0/README.html) | Open in a browser; screenshots are included in the file. |

**Update both Windows and your tablet to 0.6.0 for shape correction and lasso editing.** Both tablet platforms use the same `DeskInk.exe` on the PC. The new editing tools are disabled on older hosts; ordinary ink remains available.

**Keep the existing app installed when updating** to retain PC registrations and settings.

- **Windows 0.5.1+:** checks when launched. Open **App updates → Download update → Install and restart (clears ink)**. Pairing and desktop shortcuts are retained; restarting clears current ink. For an older version, extract the full latest ZIP over its existing folder once.
- **Android 0.6.0+:** tap the **update (down-arrow)** icon at the top right, then **Download update → Install update**. Checks run on launch and periodically while the app is open. On the first update, open the offered settings, allow installs from DeskInk, and return. Android requires confirmation before installing. **If you currently have 0.5.1 or another older APK, download 0.6.0 once and install it over the existing app.** Subsequent versions can be obtained inside DeskInk.
- **iPad:** continues to use free iloader installation. Download the new IPA on your PC, then **Import IPA with the same Apple account** over the existing app. This route has no automatic updates; renew the free signature every seven days as described below.

Install after your presentation. Long-session slowdown and monitor switching reported on some Galaxy tablets remain under investigation.

You need a recent Windows x64 PC, an iPad running iPadOS 17+ or an Android 8+ tablet, and a compatible pen. Use a data-capable USB cable for USB connection and iPad installation, or a trusted local network for Wi-Fi/LAN. **No Mac or development tools are needed.** Initial PC setup requires Internet access. [Latest release](https://github.com/zkdlwuthwo1-hash/deskink/releases/tag/v0.6.0) · [Official iloader instructions](https://iloader.app/)

## Set up your Windows PC first

**Prepare the Windows app first for both iPad and Galaxy Tab.** Complete these steps before installing the app on your tablet.

1. Download the **Windows app ZIP** above on your PC.
2. **Extract the entire ZIP** into a writable folder such as Documents. Do not run it from inside the ZIP; keep all extracted files together.
3. Run **`DeskInk.exe`** in that folder. Initial setup downloads and verifies about 60 MB of video components, so Internet access is needed. It also prepares about 8 MB of Android USB components **automatically in the background**. Later launches reuse these files. Android USB setup may take a moment; if it fails, iPad and wireless connections remain available. To retry Android USB setup, check your Internet connection, close Windows DeskInk completely and launch it again.
4. Once the Windows DeskInk window opens, follow the **[iPad](#install-on-ipad)** or **[Galaxy Tab / Android](#install-on-galaxy-tab--android)** steps below. Waiting for a connection is normal before the tablet app is installed.

**DeskInk - Screen 1 / Screen 2** desktop shortcuts are created automatically. For later use, open the desired screen's shortcut or **`DeskInk.exe`**. **Windows 0.5.3 no longer requires separate Android setup or shortcut-creation EXEs.** Keep the whole extracted folder together, including `bin`, `scripts`, `runtime` and `licenses`.

## Install on iPad

**Lightning iPads use the same IPA and installation steps.** iPadOS 17+ is required. Use a **data-capable** USB-A–Lightning cable for a USB-A PC port, or USB-C–Lightning for a USB-C port. [Apple's USB connection guide](https://support.apple.com/en-us/guide/ipad/ipad756c56a8/17.0/ipados/17.0). Apple Pencil (1st generation) does not support double-tap switching; use the eraser in the bottom toolbar. [Apple Pencil features](https://support.apple.com/en-us/guide/ipad/ipad63913965/ipados).

`Connection lost — USB disconnected` after pairing does not necessarily mean a bad cable. **Update the Windows app first**: Intel video encoder failures in versions before 0.4.4 also caused this message. If it persists, check iPad detection in Apple Devices and examine the matching `stdout` and `stderr` files in `%LOCALAPPDATA%\DeskInk\logs`. Redact device and connection details before posting logs publicly.

After [setting up your Windows PC](#set-up-your-windows-pc-first), follow this order: **check USB detection in Apple Devices → sign in to iloader → install the IPA → trust/Developer Mode → pair**. The [illustrated guide](https://zkdlwuthwo1-hash.github.io/deskink/README.html#apple-devices) follows the same order.

1. **Check iPad detection in Apple Devices.** On a Windows PC you are setting up for the first time, install and open [Apple Devices](https://apps.microsoft.com/detail/9np83lwlpz9k). Connect iPad with a data-capable USB cable, unlock it, accept **Trust This Computer**, and enter the **iPad passcode** if prompted. Continue when **iPad appears in the Apple Devices sidebar**, as marked below. If iloader and DeskInk already detect iPad correctly, no additional installation is needed; iTunes or another existing Apple setup may already provide the USB components. [Apple's instructions](https://support.apple.com/en-us/108643)

   <img src="docs/images/apple-devices-annotated.png" alt="Annotated Apple Devices example: check the iPad entry in the left sidebar and the device heading." width="720">

   *Annotated screenshot with private device details replaced. The numbered marks identify the detected iPad.*

2. **Open iloader and select the connected iPad.** Download and run the [iloader Windows installer](https://github.com/nab138/iloader/releases/latest/download/iloader-windows-x64.exe). Its Windows instructions require iTunes; follow the [official setup instructions](https://iloader.app/) if it reports missing Apple components. If Apple Devices detects iPad but iloader does not, use **Refresh Devices (Ctrl+R)** and reopen iloader if necessary.

   Check **Settings → Anisette Server**, shown below. **Jkcoxson (Mac) can be used on Windows; you do not need a Mac computer.** The name describes the server option. If an older Windows server option fails to connect or complete login, try **Jkcoxson (Mac)** and sign in again. Leave a working login unchanged. Available choices vary: iloader **2.4.0 defaults to Jkcoxson (Mac) and removes the old Windows server choices**. Changing servers does not fix a genuinely incorrect account/password or guarantee login. [Official release notes](https://github.com/nab138/iloader/releases/tag/v2.4.0)

   <img src="docs/images/iloader-mac-server-guide.png" alt="iloader settings with Jkcoxson (Mac) selected in Anisette Server." width="720">

   *iloader 2.4.0 UI preview. Button names and layout can differ by version/language.*

3. **Sign in with your own Apple account and complete Apple verification.** Enter your account in **Apple ID Email…**, enter its password in **Apple ID Password…**, and choose **Login**. If **Two-Factor Authentication** opens, tap **Allow** on your trusted Apple device, enter Apple's six-digit code in **Verification Code…**, then choose **Submit**. **Logged in as** and **Sign Out** indicate login is complete. This is **Apple's two-factor authentication code**, not DeskInk's PC pairing code. Never use the author's account. [Apple's verification instructions](https://support.apple.com/en-us/102606)

   <img src="docs/images/iloader-apple-login-guide.png" alt="iloader Apple ID Email and Apple ID Password fields with the Login button." width="720">

   <img src="docs/images/iloader-apple-2fa-guide.png" alt="iloader Two-Factor Authentication dialog with Verification Code field and Submit button." width="720">

   *iloader 2.4.0 UI previews using example data, not a live Apple login. Enter your own account and the code Apple sends you.*

4. **Install the downloaded IPA.** Choose **Import IPA / IPA 가져오기**, select `DeskInk-iPad-unsigned.ipa`, and wait while the **Sign & Install App** step completes. Selecting the IPA starts signing and installation. Keep iPad connected and unlocked until installation finishes. If iPad reports an untrusted developer, open **Settings → General → VPN & Device Management → Developer App → your Apple account → Trust**. Follow any confirmation or restart prompt.

   <img src="docs/images/iloader-import-ipa-guide.png" alt="iloader 2.4.0: Logged in as, selected iPad, Import IPA" width="720">

   The actual iloader 2.4.0 UI with example account/device data. Check **Logged in as** on the left and **Selected** above, then use **Import IPA** in the middle.

5. **Enable Developer Mode — required.** Open **Settings → Privacy & Security → Developer Mode**, turn it on, and tap **Restart**. After restarting, unlock iPad, tap **Turn On** in the confirmation, and enter the device passcode if requested. Then open DeskInk. If the menu is missing, install the IPA with iloader first, try opening DeskInk once, then reopen Settings. A school/work-managed device may restrict this setting. [Apple's instructions](https://developer.apple.com/documentation/xcode/enabling-developer-mode-on-a-device)
6. **Connect and pair.** Keep the Windows DeskInk app you prepared earlier open, leave USB connected, and open DeskInk on iPad. Enter the **six-digit code currently shown in Windows DeskInk**. Follow [Connect with the pairing code](#connect-with-the-pairing-code). For wireless use, first select the PC by [QR code or IP address](#connect-over-wi-fi--lan); the pairing code alone cannot find it.

## Install on Galaxy Tab / Android

Complete [Windows PC setup](#set-up-your-windows-pc-first) above first. For **Wi-Fi only**, install the APK in step 1 below, then follow [Connect over Wi-Fi / LAN](#connect-over-wi-fi--lan). **USB debugging is needed only for USB connections.** Use the same `DeskInk.exe` as iPad; Android USB components are prepared automatically. An older help message in the tablet app may name `DeskInk-Android.exe`; **use `DeskInk.exe` with Windows 0.5.3**. The following steps set up USB:

1. Tap **[Download Android APK](https://github.com/zkdlwuthwo1-hash/deskink/releases/download/v0.6.0/DeskInk-Android.apk?v=0.6.0) on your tablet**, open the downloaded `DeskInk-Android.apk`, and tap **Install**. Allow installation from the browser/file app when prompted. No Apple account, iloader or weekly renewal is needed.
2. Open **Settings → About tablet → Software information → tap Build number seven times**, then enter the device passcode. Enable **Developer options → USB debugging**. Menu names vary by device. [Samsung instructions](https://www.samsung.com/uk/support/mobile-devices/how-do-i-turn-on-the-developer-options-menu-on-my-samsung-galaxy-device/)
3. **Keep the Windows DeskInk app you prepared earlier open.** On first launch, allow a moment for the automatic download and verification of about 8 MB of Android USB components. There is no separate EXE to run or second Windows ZIP to download.
4. Connect a USB data cable and **allow USB debugging from your PC** on the tablet. Open DeskInk and enter the PC's six-digit pairing code. Connect only one Android tablet over USB at a time.
5. **Hold the S Pen button to erase; release to restore the previous tool.** Pens without a button can use the toolbar eraser. If Windows cannot detect the tablet, check [Samsung's official USB driver](https://developer.samsung.com/android-usb-driver).

After setup, use either **DeskInk - Screen 1 / Screen 2** desktop icon. Android does not use Apple Devices. Authorize only PCs you trust; USB debugging can be turned off when no longer needed. Install a newer APK with the same signing identity over the existing app to update. **Android 0.4.1** adds an icon dock, color and width previews, and a redesigned connection screen.

## Connect over Wi-Fi / LAN

**The current recommended combination is Windows and tablet app 0.6.0. Update any app older than 0.5.1 first.** First connect the PC and tablet to the **same router and reachable local network**. The PC can use Ethernet while the tablet uses Wi-Fi. This network connection and DeskInk's **Allow Wi-Fi / LAN** switch are separate: you need both. The switch permits tablet connections to DeskInk; it does not connect Windows to Wi-Fi. It starts off, and USB remains the default. Internet remote access is not supported.

1. Unplug USB and disconnect any tablet session. In Windows DeskInk, find the **Wi-Fi / LAN** card and enable **Allow Wi-Fi / LAN**. The checkbox can only be changed while disconnected. Read the PC's **IPv4 address** and **Port** shown there; the default port is **9876**. Check that the address belongs to the tablet's network, not a VPN/virtual adapter. Use **Refresh IP** after changing networks. **Use the current address shown on your PC, not an address in a screenshot.**

   <img src="docs/images/windows-lan-guide.png" alt="Windows DeskInk: Allow Wi-Fi / LAN enabled, PC ADDRESS, Port and QR code button below the separate pairing code." width="560">

   *Windows app UI captured with example data. Copy your own PC ADDRESS and Port to the tablet; `192.168.1.20` and pairing code `123456` here are examples.*

2. **iPad:** click **QR code (iPad)** in Windows, then use **Scan QR code** in iPad DeskInk. Alternatively, enter the Windows IPv4 address in **PC address** and its displayed port in **Port**, then tap **Connect**. Allow DeskInk's **Local Network** permission if prompted. The QR contains the connection address, not a pairing credential.

   <img src="docs/images/windows-qr-guide.png" alt="Windows QR code dialog for connecting iPad, with the PC address and port beneath it." width="560">

   *Open this dialog on your own PC and scan its QR. The example QR shown here is not your connection address.*

   <img src="docs/images/ipad-ip-guide.png" alt="iPad DeskInk: PC address, Port, Connect, Scan QR code" width="720">

   *Actual iPad simulator capture. Use **PC address and Port → Connect**, or **Scan QR code**. The entered `192.168.1.20` is sample data; replace it with the address shown on your Windows PC.*

3. **Android:** tap **Wi-Fi** to open **Connect over Wi-Fi / IP**, enter the Windows IPv4 address in **PC IP address** and the displayed port in **Port**, then tap **Connect**. Android uses direct IP entry; QR scanning is not included. Use QR/IP entry for this release rather than **Find PCs**.

   <img src="docs/images/android-ip-guide.png" alt="Android Connect over Wi-Fi / IP dialog with PC IP address, Port and Connect." width="720">

   *Actual Android app in an emulator test. The gray `192.168.1.20` is an input hint: enter your current Windows PC address there.*

4. On first use with a PC, enter its current **six-digit pairing code** as described below. Previously registered tablets keep their registration. The Windows picture should appear before you start writing.

Use **trusted local networks only**: the screen and pen traffic are not encrypted. Limit Windows Firewall permission to the extracted package's **`bin/deskink.exe`, UDP 9876, Private networks and the local subnet**; match the rule to the displayed port if it was changed. Do not disable the firewall or configure router port forwarding. Guest Wi-Fi, client isolation, different subnets or a VPN can prevent devices from reaching each other even when both have Internet access. If Windows shows a different address after reconnecting to the network, scan the new QR or enter the new address.

Wi-Fi conditions affect latency; zero delay cannot be guaranteed. Prefer a PC connected by Ethernet and a strong 5/6 GHz tablet signal. If writing stalls or the connection closes after packet loss, improve the local connection or return to USB. The wireless path avoids building a long backlog of old pen input. Automated protocol checks target delivery order, loss recovery and queue limits; **physical Wi-Fi latency, actual S Pen/Apple Pencil feel and Zoom presentation still need device testing**. [Illustrated wireless setup (한국어)](https://zkdlwuthwo1-hash.github.io/deskink/README.html#wireless)

Apple USB services and Android USB debugging are not needed for wireless streaming itself. iPad installation/renewal with iloader still uses the USB installation steps above. Keep the apps open while presenting. After one successful LAN connection, the app remembers that PC address. USB takes priority when its data connection is available; without USB, automatic connection uses the remembered LAN address. Reattaching USB switches back to the cable. Tap X/Disconnect to stop automatic reconnection; connect again when ready. A PC used only over USB needs its IP entered once before wireless fallback can work. If the PC address changes, enter it again. Switching transport establishes a fresh session and can briefly interrupt the picture.

## Connect with the pairing code

1. **Find the code on your PC:** open the Windows DeskInk window and look for **`Pairing code: six digits`** below the connection status. Restore the window from the taskbar if minimized.
2. **Open pairing on your tablet:** connect USB or select the PC via the [Wi-Fi/LAN steps](#connect-over-wi-fi--lan), then open DeskInk. On iPad, use **Pair with your PC**. On Android, use the automatic prompt or **Pair PC / PC 연결** on the welcome screen. While disconnected, you can also tap the connection status at the top left.
3. **Enter all six digits:** include any leading zero. Tap **Pair iPad** on iPad or **Connect / 연결** on Android. This is separate from Apple account two-factor authentication.
4. **Check the connection:** Windows appears on the tablet and the PC says **Tablet connected**. Pair once per PC; the tablet remembers two PCs and reconnects after Windows DeskInk restarts. The displayed code may change without affecting registered tablets.

<img src="docs/images/android-pairing-guide.png" alt="Android Pair PC: PC pairing code, Connect" width="720">

On Android, enter the current six-digit Windows DeskInk code under **PC pairing code**, then press **Connect**. This is an actual emulator capture with an empty field. On iPad, the submit button is **Pair iPad**.

**Updating from a version before 0.4.2:** update Windows and the tablet app, then enter the code once on each PC. After that, USB connection and opening DeskInk are enough. **Forget paired tablets** in the Windows app removes registration and requires a new code. Reinstalling/resetting app data or changing Windows users can also require registration again.

If a code is rejected, recheck the current digits in the PC window. For **Too many attempts**, wait one minute before retrying. Some iPad instructions still say EternalMonitor; find the code in Windows DeskInk. [Illustrated pairing and troubleshooting (한국어)](https://zkdlwuthwo1-hash.github.io/deskink/README.html#pairing)

## Use each time

1. Open DeskInk on the PC and tablet. Connect and unlock your tablet by **USB**, or use the [Wi-Fi/LAN steps](#connect-over-wi-fi--lan).
2. Choose **Screen 1 / Screen 2** in the Windows DeskInk window. Registered tablets connect automatically. Enter the code only when registering a new PC or after forgetting registration.
3. Draw with your pen. Choose tools from the **bottom toolbar**:

| Tool / gesture | Action |
| --- | --- |
| Pen / highlighter | Persistent ink; choose from **12 colors** and line widths. |
| Shapes | Enable **Shapes**, draw a circle, ellipse, rectangle or line in one stroke, and **hold the pen still at the endpoint for one second without lifting**. Unrecognized marks and early pen lifts retain ordinary ink. Color, width and opacity are preserved. |
| Lasso | Tap a shape or fully enclose several strokes with **Lasso**. Drag the selection to move it. **Corner handles preserve proportions; side handles resize one axis.** Each move or resize is one undo step. |
| Eraser | Choose **Width → Eraser size → Small / Medium / Large**. The size is remembered and also applies while holding the S Pen button. Touching a stroke or shape erases the whole object. The toolbar eraser returns to the previous tool on lift; supported Pencil double-tap also toggles eraser. |
| Laser / disappearing pen | Point without permanent ink / leave ink that fades after about two seconds. Hover requires compatible tablet and pen hardware. |
| Undo / redo | Correct recent changes on the current monitor. |
| Eye / trash | Hide/show ink / clear the current monitor's ink. Clear can be undone. |
| Two-finger pinch / drag | Zoom (1–4×) / pan **on the tablet only**. Tap **화면 맞춤** (Fit) to reset; on Android, use the four-corners icon at the top right. Fingers do not draw by default. |

**Desktop screen switching:** The first Windows launch automatically creates **DeskInk - Screen 1 / Screen 2** desktop icons. Anyone can double-click either icon to choose that display, without a terminal window. Stream Deck users may optionally register the same shortcuts as Open actions. Video reconnects briefly. Keep the extracted folder in the same location; to recreate the icons, open Windows Run (Win+R) and enter `"Full path to the DeskInk folder\DeskInk.exe" --create-shortcuts`, replacing the quoted path with your actual extracted location.

Ink stays separate for each monitor while the Windows host runs. **Closing Windows DeskInk clears all ink; there is no permanent save.** You can close iloader after installation. Keep DeskInk running and the selected USB or local-network connection available while presenting.

In Zoom, manually share the **entire monitor** you annotate and verify ink/pointers from another participant's view. Individual-window sharing may omit the overlay.

## Renew the iPad installation

A free Apple account signs development apps for **7 days**. Your Apple account does not expire and this is not an iloader trial. Before expiry, connect iPad to Windows and sign in with **the same account**, choose **Import IPA**, select **the same IPA**, and wait for the **Sign & Install App** step to finish. This renews the installation without a subscription. [Apple's free-account limits](https://developer.apple.com/help/account/basics/about-your-developer-account)

## If something does not work

- **“Different versions — update both” / “EternalMonitor” appears:** the PC and tablet cannot use the same connection protocol. Check for an app older than 0.5.1 and update it. Update both to 0.6.0 for shape correction and lasso editing. If the iPad app needs updating, import the new IPA into iloader and install it over DeskInk with the same Apple account. Reconnect using **Scan QR code** or the PC's **IP and port**.
- **Wi-Fi/LAN does not connect:** use the current Windows and tablet 0.6.0 downloads, confirm Windows **Allow Wi-Fi / LAN** is enabled, and the IP/port match the current PC display. Check iPad Local Network permission, the same reachable local network, guest/client isolation and Windows Firewall's Private-network/local-subnet permission. A pairing code alone cannot select the PC; enter the IP or scan its QR first. [Connection steps](#connect-over-wi-fi--lan)
- **Android USB is not detected:** run Windows `DeskInk.exe` and allow a moment for its first Android USB component download. Enable USB debugging, authorize this PC, and check the data cable/Samsung USB driver. If automatic setup failed, check your Internet connection, close Windows DeskInk completely and launch it again. Keep the tablet app open.
- **iPad is missing from the PC or iloader:** [check Apple Devices first](https://zkdlwuthwo1-hash.github.io/deskink/README.html#apple-devices). If iPad is absent from its sidebar, unlock iPad, confirm Trust, and try a data cable/another USB port. If Apple Devices sees iPad, use **Refresh Devices (Ctrl+R)** in iloader; reopen iloader if it is still missing. Follow the [official iloader setup instructions](https://iloader.app/) if it reports missing Apple components.
- **iloader server/login step fails:** check your Apple account and password first. For a server connection or authentication-flow failure, try **Settings → Anisette Server → Jkcoxson (Mac)** if available, then retry. It works as a server option on Windows too; no Mac is required. It is an alternative to try, not a guaranteed fix for every login error. Keep a working configuration unchanged.
- **Keeping Apple Devices open:** if DeskInk connects without opening it, continue as usual. Only keep it running/minimized if closing that app interrupts USB connectivity on your PC.
- **Developer cannot be trusted / Developer Mode required:** follow the **IPA installation/developer trust** and **Developer Mode** instructions above, including the confirmation after restart.
- **App stopped opening after a week:** renew it with iloader as described above.
- **Wrong monitor:** select Screen 1/2 and verify the actual screen. DeskInk numbering may differ from Windows Display Settings.
- **Windows fails to start:** keep the entire extracted folder together and allow the first-run download. Logs are at `%LOCALAPPDATA%/DeskInk/logs`. Do not publish pairing codes or private screen contents when reporting errors.
- **Windows reports unsupported session-local capture exclusion:** this is a Windows capture capability issue, not an Apple login or network setting. The required capability was unavailable on tested Windows 11 **22H2 (22621.4317)** and supported in a local check on **25H2 (26200.9711)**. Update Windows and recheck; this does not establish an exact minimum patch or guarantee every 25H2 installation. [Verification notes](docs/wireless-verification.md)

DeskInk supports **USB** and optional **Wi-Fi/LAN**. [Report an issue](https://github.com/zkdlwuthwo1-hash/deskink/issues) · [Compatibility and verification](docs/android-verification.md) · [Development guide](docs/development.md)

## Credits and licenses

Based on [EternalMonitor](https://github.com/sidebandstudio/EternalMonitor), commit `9888f37a5a457c76ec1d960dc9d9f1115304e6a9`. Its [MIT notice](third-party/EternalMonitor-LICENSE) is preserved. The ZIP includes dependency notices under `licenses/`; iPad notices are [here](ios/EternalMonitor/Resources/ThirdPartyNotices.txt), and the APK contains [Android notices](android/app/src/main/assets/ThirdPartyNotices.txt). Video components are downloaded separately from [BtbN/FFmpeg-Builds](https://github.com/BtbN/FFmpeg-Builds) on first launch. The Android setup helper downloads Google [Platform Tools](https://developer.android.com/tools/releases/platform-tools) separately and keeps its NOTICE.txt. Each third-party component retains its own license. A repository-wide license for newly added DeskInk code has not yet been assigned.
