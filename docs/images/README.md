# Screenshot provenance

Unmodified PNG attachments from [iPad build 12](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39118670760), source `62f7c57`.

- `ipad-workspace.png`: `9D816454-39E0-42B7-9232-7E6FCD030ED1.png`, `DeskInkViewportUITests/testControlsStayOutsideTheDrawingViewportInLandscape`, full simulator screen. Blue desktop and yellow marker are test content. The top event counter is diagnostic UI, not the production connection bar.
- `ipad-tools-preview.png`: `525CE5FD-0A8F-41BA-90CB-300EA9822E3F.png`, `DeskInkToolsTests/testToolbarRendersInBothIPadOrientations`, rendered SwiftUI toolbar with synthetic desktop backdrop. This illustrates the real toolbar controls; its surrounding layout is a component preview, not the complete connected app.

The HTML labels these as simulator/test previews. It does not present them as physical iPad or live Windows screenshots. The original bytes are embedded in the standalone HTML by `scripts/render-guide.py`.

## Guide illustrations

The HTML's opening Windows/tablet illustration and pairing-code diagram are HTML/CSS/SVG explanations, not app screenshots. They are labeled accordingly. The pairing example uses the illustrative code `482691`; it is not read from a running host and is not a live credential. Readers are instructed to use the current code shown in their own Windows DeskInk window.

Pairing labels and behavior are checked against `host/src/bin/deskink.rs`, `host/src/transport/session.rs`, `ios/EternalMonitor/App/PairingSheet.swift`, `ios/EternalMonitor/App/ConnectView.swift`, and the Android `MainActivity.java` / localized strings. In particular, the iPad submit button is **Pair iPad**, Android uses **PC 연결 / Pair PC** then **연결 / Connect**, and codes rotate after a successful code-based pairing. The host's session token is recreated on restart, so restarting Windows DeskInk requires pairing again.

## Android

`android-streaming.png` is an unmodified screenshot from [Android run 9](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39190227883), source `a82ce6c`, API 35 Pixel C emulator. `StreamingTest` connects a protocol test peer, sends the shared 1280×720 H264 presentation fixture through the real app socket and MediaCodec, verifies TextureView output and a toolbar command, and draws the orange underline using native stylus events before capturing the emulator display. The circuit slide is synthetic test content, not a private desktop or physical Galaxy Tab capture. The layout, connection bar and icon dock are the production native Android views.

The same artifact contains welcome screens in both orientations, the color picker and pairing sheets, including a 640×400dp window at 1.5× system font size with the software keyboard visible. The example `482691` is illustrative and never submitted. The fixture source is `proto/testdata/deskink-workspace.svg`; it was rendered and encoded as test data only. No test encoder is included in the application.
