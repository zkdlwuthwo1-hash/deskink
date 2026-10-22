# Screenshot provenance

## 2026-10-22: login and IP walkthrough

The current HTML and both READMEs use the following screenshots. Captions distinguish
real rendered UI with example data from a live connection or completed Apple login.
No private account, password, authentication code, device owner or live LAN address
was used for these new images. Images are captured directly; no generative edits,
redactions or painted controls were applied to them.

### iloader 2.4.0

Source: [nab138/iloader v2.4.0](https://github.com/nab138/iloader/tree/902597564315442f00ab299dcc758ee0e13b8fa8),
commit `902597564315442f00ab299dcc758ee0e13b8fa8`. The original React App, AppleID,
Settings, Dropdown, Modal, styles and translations were run in an isolated browser
fixture. Tauri/native calls were replaced with memory-only example responses;
Apple authentication, device access and installation were not performed.

- `iloader-apple-login-guide.png`: original Apple ID login panel, empty email and
  password fields, Save Credentials and Login. It is a component-region capture
  of the running UI, not a reconstruction of the form.
- `iloader-apple-2fa-guide.png`: original Two-Factor Authentication modal opened
  by the local `2fa-required` fixture event. Verification Code is empty.
- `iloader-mac-server-guide.png`: original Anisette Server dropdown, open with
  Jkcoxson (Mac) selected and the 2.4.0 server options visible.
- `iloader-import-ipa-guide.png`: original full UI with fixture login
  `you@example.com`, device `Example iPad`, and Import IPA. These values are
  examples, not a completed real login or a connected physical device.

These are source UI captures, **not screenshots of the installed Windows Tauri
application**. The same original components and CSS render the controls. The guide
links to [iloader's official site](https://iloader.app/) and
[official repository](https://github.com/nab138/iloader). Code is under the
[MIT license, Copyright (c) 2025 nab138](https://github.com/nab138/iloader/blob/902597564315442f00ab299dcc758ee0e13b8fa8/LICENSE).
The iloader name and branding identify the documented third-party tool; they are
not DeskInk branding or an endorsement. See [research and version differences](../iloader-login-research.md).

### Windows LAN and QR

- `windows-lan-guide.png` (640×880): native DeskInk UI showing monitor selection,
  Pairing code, Allow Wi-Fi / LAN, PC ADDRESS, Port and QR code (iPad).
- `windows-qr-guide.png` (640×880): the real native QR popup.

The renderer uses `host/src/deskink_ui.rs` and unchanged `host/src/lan.rs` at
`c886e940e907e5a38317130d376ffee4dbc8ede0`. A local preview supplies example state:
IP `192.168.1.20`, port `9876`, pairing code `123456`, two remembered devices and
two example monitors. It does not read real accounts, network interfaces or host
sessions. The QR encodes only `eternaldisplay://192.168.1.20:9876`.

The native eframe screenshot output was converted losslessly from PPM to PNG;
decoded RGB hashes were checked equal. No resizing, crop, overlay or pixel edit
was performed. Preview checks also exercised monitor click/keyboard selection,
pairing copy and QR opening. This is UI verification, not wireless latency testing.

### Android IP and pairing

- `android-ip-guide.png` (960×600): original emulator screenshot of Connect over
  Wi-Fi / IP. The gray `192.168.1.20` is placeholder text in an empty field; the
  port field contains the default `9876`.
- `android-pairing-guide.png` (960×600): original Pair PC sheet. The six-digit
  field is empty and `6 digits` is placeholder text.

Both files are byte-for-byte copies from [Android run 24](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39559405006),
source `0fc3d0ced500631944c93b7f6ec879541ce7212a`, API 35 Pixel C emulator.
The compact scenario uses 640×400 dp, density 240 and font scale 1.5. Tests are
`WorkspaceUiTest.networkAddressAndPortStayReachableOnTheTablet` and
`WorkspaceUiTest.workspaceControlsRemainOutsideInkAndReachableInBothOrientations`.
They are native Android UI captures, not physical Galaxy Tab photographs.

### iPad IP connection

`ipad-ip-guide.png` is the unmodified `58038D32-8344-4D97-B967-1799574E455D.png`
attachment (`ipad-ip-guide`) from [iPad run 42](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39723276425),
source `43d3b18b569d46dc433e1ae51715b867d8dcf1d9`. It captures the actual app on
an iPad Pro 11-inch (M4) simulator, iOS 18.5 / Xcode 16.4, in landscape.

`ConnectScreenTests/testIPConnectionGuideScreenshot` launches the production
connection view with example `192.168.1.20:9876`, disables automatic reconnection
for the capture, and checks that the IP and port values, Connect and Scan QR code
controls are visible and reachable. The focused test passed with zero failures.
The source image is copied byte-for-byte without a crop or pixel edit. It does
not establish a physical iPad connection or measure network latency. The run's
IPA is a CI artifact only; no new tablet release was published for this guide.

## Earlier captures

Unmodified PNG attachments from [iPad build 12](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39118670760), source `62f7c57`.

- `ipad-workspace.png`: `9D816454-39E0-42B7-9232-7E6FCD030ED1.png`, `DeskInkViewportUITests/testControlsStayOutsideTheDrawingViewportInLandscape`, full simulator screen. Blue desktop and yellow marker are test content. The top event counter is diagnostic UI, not the production connection bar.
- `ipad-tools-preview.png`: `525CE5FD-0A8F-41BA-90CB-300EA9822E3F.png`, `DeskInkToolsTests/testToolbarRendersInBothIPadOrientations`, rendered SwiftUI toolbar with synthetic desktop backdrop. This illustrates the real toolbar controls; its surrounding layout is a component preview, not the complete connected app.

The HTML labels these as simulator/test previews. It does not present them as physical iPad or live Windows screenshots. The original bytes are embedded in the standalone HTML by `scripts/render-guide.py`.

## Guide illustrations

The HTML's opening Windows/tablet illustration and pairing-code diagram are HTML/CSS/SVG explanations, not app screenshots. They are labeled accordingly. The pairing example uses the illustrative code `123456`; it is not read from a running host and is not a live credential. Readers are instructed to use the current code shown in their own Windows DeskInk window.

Pairing labels and behavior are checked against `host/src/bin/deskink.rs`, `host/src/transport/session.rs`, `ios/EternalMonitor/App/PairingSheet.swift`, `ios/EternalMonitor/App/ConnectView.swift`, and the Android `MainActivity.java` / localized strings. In particular, the iPad submit button is **Pair iPad**, Android uses **PC 연결 / Pair PC** then **연결 / Connect**, and codes rotate after a successful code-based pairing. Windows saves tablet trust separately from its fresh per-run drawing identity, so registered tablets reconnect after restart. Updating from older versions requires one registration on each PC.

## Android

`android-streaming.png` is an unmodified screenshot from [Android run 9](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39190227883), source `a82ce6c`, API 35 Pixel C emulator. `StreamingTest` connects a protocol test peer, sends the shared 1280×720 H264 presentation fixture through the real app socket and MediaCodec, verifies TextureView output and a toolbar command, and draws the orange underline using native stylus events before capturing the emulator display. The circuit slide is synthetic test content, not a private desktop or physical Galaxy Tab capture. The layout, connection bar and icon dock are the production native Android views.

The same artifact contains welcome screens in both orientations, the color picker and pairing sheets, including a 640×400dp window at 1.5× system font size with the software keyboard visible. The example `482691` is illustrative and never submitted. The fixture source is `proto/testdata/deskink-workspace.svg`; it was rendered and encoded as test data only. No test encoder is included in the application.

## Archived iloader 2.3.9 explanation

`iloader-windows-annotated.png` is an explanatory image edited with the built-in imagegen tool from the user's iloader 2.3.9 screenshot on 2026-10-19. It is not an unmodified screenshot. The account sidebar, device owner/name and header were excluded; the original private screenshot is not committed or embedded. The edit highlights the Anisette Server selector with badge 1 and the Import IPA button with badge 2. The selected value, control labels and positions were visually checked against the supplied screenshot. Korean instructions are also provided as accessible HTML text.

The image-edit prompt requested: preserve the supplied dark UI and exact labels, exclude all account and device information, outline Anisette Server / Jkcoxson (Windows) and Import IPA in green with numbered markers, and add the Korean captions “로그인 후 Import IPA 클릭” and “이미 Jkcoxson (Windows)이면 그대로 진행하세요.” The generated image remains in `docs/images/` for older guide links. The current README and HTML no longer embed it.

The [2.3.9 server list](https://github.com/nab138/iloader/blob/aa9aa3779e15498180e0205158b7ff91536877c6/src/pages/Settings.tsx) offered Mac and Windows server types without filtering them to the client PC's operating system. This historical image showed one successful Windows-server setup, not a universal OS requirement. [iloader 2.4.0](https://github.com/nab138/iloader/releases/tag/v2.4.0) now defaults to Jkcoxson (Mac) and removes legacy Windows server choices. The current guide uses 2.4.0 source UI captures described below. See [login research](../iloader-login-research.md) for dated evidence.

## Apple Devices connection guide

`apple-devices-annotated.png` is an explanatory image edited with the built-in imagegen tool from the user's Apple Devices screenshot on 2026-10-19. It is not an unmodified screenshot. The device owner's name was replaced with the generic Korean label “내 iPad” in both visible locations, storage statistics were removed, and the lower backup/restore controls were excluded. The original private screenshot is not committed or embedded. The device entry, model header and annotation labels were visually checked against the supplied image. Korean instructions also appear as accessible HTML text.

The public asset is saved at `docs/images/apple-devices-annotated.png`; the standalone HTML embeds the reviewed bytes and supports enlargement. [Apple's connection instructions](https://support.apple.com/ko-kr/108643) support opening Apple Devices, connecting by USB, accepting Trust and checking the sidebar. The guide uses this as the first-time setup path while allowing PCs with working Apple USB components to skip additional installation. [iloader's official Windows instructions](https://iloader.app/) still list iTunes; a successful detection screenshot does not establish that Apple Devices replaces every iloader dependency.

Final edit prompt (built-in tool, no CLI fallback):

```text
Use case: precise-object-edit.
Asset type: explanatory image for a Korean Windows-to-iPad installation guide.
Input image 1 is the edit target: a real Apple Devices Windows screenshot supplied by the user.
Crop to the upper-left portion showing the title "Apple 기기", the sidebar device entry, and the top main device header with its iPad model. Exclude everything below the first small "소프트웨어" heading, including all backup, restore, update and synchronization controls. Exclude the far right empty area.
Privacy edit: replace the personal owner's device name in BOTH the left sidebar and the top header with the exact generic text "내 iPad". No original owner name may remain, and do not include storage statistics. Keep the recognizable Apple Devices UI, icons, light gray surfaces, layout, and existing app title and model label faithful to the original.
Add a green outline and a green numbered badge "1" around the sidebar iPad entry, and a green outline and badge "2" around the main device header. Do not cover the labels.
Below this cropped UI, add a clean white caption strip as an editorial annotation separate from the application, with two readable Korean lines:
"① 왼쪽 목록에 iPad가 보이면 연결 확인 완료"
"② 다음: iloader → Refresh Devices"
The output is a clear wide instructional screenshot edit, not a redesigned app or invented UI. No email, owner name, credentials, extra UI buttons, phone, or decoration. Opaque background.
```
