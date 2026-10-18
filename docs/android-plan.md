# Android app plan — 2026-10-18

The user approved these TDD boundaries in this conversation:

1. Pen input: S Pen button held → eraser; release → previously selected tool. Includes buttons reported during hover, contact, cancellation and loss of focus. Device-specific Samsung input delivery still needs physical hardware.
2. Session: authenticated Windows pairing, reliable USB packets, video frames, monitor changes and separate ink/undo histories. Golden protocol vectors are shared with Rust and Swift; a live stream check complements unit tests.
3. Workspace UI: tools, two-finger zoom/pan on the tablet only, accurate Windows coordinates, and a toolbar outside the drawing area. Android instrumentation tests cover synthetic touch/stylus events; physical latency is a separate check.

Implementation choices: native Android Views/Canvas and MediaCodec, Java 17, Android 8/API 26 minimum, compile/target API 35. This avoids a WebView and retains Android's historical stylus samples, button state and hardware video decoder. No keyboard/mouse injection, account system or cloud relay is added.

USB: ADB forwarding from loopback on Windows to a loopback listener in the Android app, carrying the same bounded EMLINK framing and DeskInk commands used by iPad. Android requires one-time USB debugging authorization. The Windows host discovers authorized USB Android devices in parallel with its existing Apple USB discovery. Wi-Fi ADB devices are not automatically selected. Existing pairing remains mandatory.

Distribution: signed APK for Android, existing IPA for iPad, and an updated Windows portable ZIP. Keep the release signing key outside the repository and artifacts; public downloads contain only the signed app and public signature information. The iPad build is unchanged. Document installation and device-test limitations separately for each platform.

Order: pen button behavior → wire/media compatibility → ink/tools/monitor documents → native UI/decoder → Windows Android bridge → integration and emulator checks → signed release and bilingual installation guide.

Distribution clarification: first normal Windows launch creates Screen 1 and Screen 2 desktop shortcuts automatically. These are ordinary double-click shortcuts for everyone; Stream Deck registration is optional. Keep the shortcut repair executable for moved folders or deleted shortcuts.

Skills: the approved interview feeds `tdd` at the three seams above. Use `diagnosing-bugs` for reproducible failures and `code-review` for standards/spec checks after implementation. Installed `improve-codebase-architecture` is an architecture proposal/interview workflow, not an automatic cleanup command; avoid unsolicited broad redesign. `Kanban` and the exact alias `improve-codebase` are not installed; maintain the work sequence here.

Primary references: [Android stylus events](https://developer.android.com/reference/android/view/MotionEvent), [ADB USB and port forwarding](https://developer.android.com/tools/adb), [MediaCodec](https://developer.android.com/reference/android/media/MediaCodec), [AGP 8.9 compatibility](https://developer.android.com/build/releases/agp-8-9-0-release-notes).
