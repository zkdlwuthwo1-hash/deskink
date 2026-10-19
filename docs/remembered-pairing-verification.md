# Remember paired PCs — 0.4.2 verification

The tablet now remembers two Windows PCs across app and PC restarts. A changing six-digit code does not invalidate a registered tablet. Update both Windows and the tablet app, then register once on each PC. Explicitly forgetting registration still requires a new code. Windows exit still clears ink, independently of registration.

Review baseline: `a59543395dea7a47035f9a8237b144be8b7d6252`. Final app source: `97118a7` (Android and Windows package); iPad source: `d8ad36b` (the following commit changes only Android). Task: [remember paired PCs](../.scratch/desk-ink/issues/08-remember-paired-pcs.md).

## Cause and correction

Windows previously created only a per-run credential. iPad stored a single PC under the shared USB address, and Android could retain two credentials for the same PC after rotation. The correction persists per-tablet trust with Windows current-user DPAPI and stores two independent PC registrations on each tablet. An optional, backward-compatible PC identity in the accepted handshake distinguishes a re-registered PC from the other PC.

Run credentials still rotate to preserve existing ink-document isolation. Windows retains the last presented credential until the tablet demonstrates successful replacement. Android promotes a replacement only after an atomic private-file write succeeds; activity generations share a storage lock. iPad commits both records together to Keychain. Corrupt existing registration data is preserved rather than silently overwritten. Android reports a load error; iPad preserves the data but may still show pairing because its connection layer suppresses the initial read error.

## Red → green evidence

- Windows: recreating the host and presenting the saved tablet credential initially returned Unauthorized. Four regression tests now cover restart, independent PC/device authorization, explicit forgetting, damaged storage, and lost replacement acknowledgements.
- iPad: [native red run](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39198132471) reproduced one stored PC instead of two. Tests now cover both PCs, migration, rotation, code re-registration, failed writes, corrupted storage, automatic candidate selection, rate limits, and stale acknowledgements.
- Android: real-socket regressions reproduced PC B being displaced by PC A rotation and by re-registration. Both pass. A failed durable write retains the last saved credential for the next connection. Real-file tests cover migration, atomic replacement and corrupt contents, including empty comma-separated entries (observed red before correction).

## Validation

- Windows standard ink/protocol/USB/annotation/launcher suite passed during implementation. After the handshake extension, all 55 wire/golden tests and 10 affected host integration tests passed. The optimized Windows build succeeded.
- Android: 26 core tests, 10 app tests and release lint pass locally. An existing one-second reconnect deadline failed once; its isolated rerun, the full local suite and cloud unit tests passed without changing the test or transport behavior. This single timing failure was not reproduced.
- iPad: [final native run 39200563502](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39200563502) passed all 171 tests on Xcode 16.4 / iPadOS 18.5 simulator and built the unsigned device IPA. IPA metadata is `com.deskink.personal`, version `0.4.2`, build `14`.
- Android native integration: final [run 39200955715](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39200955715) passed three tablet tests (USB-to-rendered-video/host commands, real MediaCodec decoding, orientation/toolbar reachability), plus the compact-window/large-font/keyboard UI rerun. The signed APK preserves every tested payload entry, is version `0.4.2`/code `3`, and retains the existing signing certificate with verified v2/v3 signatures.
- Windows ZIP: all 565 manifest entries match their hashes; packaged executable and HTML match local sources. The manifest records a clean `97118a7` checkout. Extracted-package runtime check passes with the verified LGPL runtime. The existing two desktop shortcuts launch/switch without visible console windows, and the updated local host is running.
- Guide: desktop/tablet/mobile layouts, no horizontal overflow, offline assets, keyboard tabs/deep links, image viewer focus, JavaScript-disabled fallback and retry behavior pass the existing browser checks.

## Standards

Independent review identified five findings across iterations: re-registration evicting the other PC, promotion before durable save, activity save/load races, damaged-file fallback, and trailing empty entries in an existing file. All were corrected and reviewed. Final unresolved documented-standard findings: **0**; additional judgement-call code smells: **0**.

## Spec

Independent review identified three findings across iterations: re-registration evicting the other PC, promotion before durable save, and activity save/load races. All were corrected and reviewed. Final unresolved spec findings: **0**. The iPad read-error UI limitation above is explicitly retained rather than described as user-visible recovery.

Review totals: Standards 5 resolved / 0 remaining; Spec 3 resolved / 0 remaining. Neither axis has an outstanding highest-severity finding.

## Distribution and remaining device checks

Windows ZIP, unsigned iPad IPA, signed Android APK, standalone HTML, SHA-256 checksums and public signing details were uploaded to the existing release. Release URLs retain the `v0.4.0-lab.1` path so previously shared download links continue to work. All six GitHub asset digests match local files. Public GET downloads of all three apps and the HTML match byte-for-byte. Pages finished deploying `97118a7`; both the newly versioned URL and the previously shared `README.html?guide=f335f2d` return the updated HTML.

No physical iPad or Galaxy Tab reconnection was observed in this change. Device checks remain: update both apps, register PC A and PC B once, close/reopen Windows DeskInk, reconnect each PC without a code, and verify pen/eraser and monitor switching. Simulator/emulator evidence does not establish physical USB/Pencil/S Pen latency or Zoom capture behavior.
