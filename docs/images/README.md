# Screenshot provenance

Unmodified PNG attachments from [iPad build 12](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39118670760), source `62f7c57`.

- `ipad-workspace.png`: `9D816454-39E0-42B7-9232-7E6FCD030ED1.png`, `DeskInkViewportUITests/testControlsStayOutsideTheDrawingViewportInLandscape`, full simulator screen. Blue desktop and yellow marker are test content. The top event counter is diagnostic UI, not the production connection bar.
- `ipad-tools-preview.png`: `525CE5FD-0A8F-41BA-90CB-300EA9822E3F.png`, `DeskInkToolsTests/testToolbarRendersInBothIPadOrientations`, rendered SwiftUI toolbar with synthetic desktop backdrop. This illustrates the real toolbar controls; its surrounding layout is a component preview, not the complete connected app.

The HTML labels these as simulator/test previews. It does not present them as physical iPad or live Windows screenshots. The original bytes are embedded in the standalone HTML by `scripts/render-guide.py`.

## Guide illustrations

The HTML's opening Windows/tablet illustration and pairing-code diagram are HTML/CSS/SVG explanations, not app screenshots. They are labeled accordingly. The pairing example uses the illustrative code `482691`; it is not read from a running host and is not a live credential. Readers are instructed to use the current code shown in their own Windows DeskInk window.

Pairing labels and behavior are checked against `host/src/bin/deskink.rs`, `host/src/transport/session.rs`, `ios/EternalMonitor/App/PairingSheet.swift`, `ios/EternalMonitor/App/ConnectView.swift`, and the Android `MainActivity.java` / localized strings. In particular, the iPad submit button is **Pair iPad**, Android uses **PC 연결 / Pair PC** then **연결 / Connect**, and codes rotate after a successful code-based pairing. Windows saves tablet trust separately from its fresh per-run drawing identity, so registered tablets reconnect after restart. Updating from older versions requires one registration on each PC.

## Android

`android-streaming.png` is an unmodified screenshot from [Android run 9](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39190227883), source `a82ce6c`, API 35 Pixel C emulator. `StreamingTest` connects a protocol test peer, sends the shared 1280×720 H264 presentation fixture through the real app socket and MediaCodec, verifies TextureView output and a toolbar command, and draws the orange underline using native stylus events before capturing the emulator display. The circuit slide is synthetic test content, not a private desktop or physical Galaxy Tab capture. The layout, connection bar and icon dock are the production native Android views.

The same artifact contains welcome screens in both orientations, the color picker and pairing sheets, including a 640×400dp window at 1.5× system font size with the software keyboard visible. The example `482691` is illustrative and never submitted. The fixture source is `proto/testdata/deskink-workspace.svg`; it was rendered and encoded as test data only. No test encoder is included in the application.

## iloader installation guide

`iloader-windows-annotated.png` is an explanatory image edited with the built-in imagegen tool from the user's iloader 2.3.9 screenshot on 2026-10-19. It is not an unmodified screenshot. The account sidebar, device owner/name and header were excluded; the original private screenshot is not committed or embedded. The edit highlights the Anisette Server selector with badge 1 and the Import IPA button with badge 2. The selected value, control labels and positions were visually checked against the supplied screenshot. Korean instructions are also provided as accessible HTML text.

The image-edit prompt requested: preserve the supplied dark UI and exact labels, exclude all account and device information, outline Anisette Server / Jkcoxson (Windows) and Import IPA in green with numbered markers, and add the Korean captions “로그인 후 Import IPA 클릭” and “이미 Jkcoxson (Windows)이면 그대로 진행하세요.” The generated image was copied into `docs/images/`; HTML embeds these reviewed bytes and supports enlargement.

The [official server list](https://github.com/nab138/iloader/blob/main/src/pages/Settings.tsx) offers Mac and Windows server types without filtering them to the client PC's operating system. The guide presents the user's successful Jkcoxson (Windows) configuration as an example, not a universal OS requirement. A normally working login does not require changing the server. The [official site](https://iloader.app/) describes IPA import and the authentication process.

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
