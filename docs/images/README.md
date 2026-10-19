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
