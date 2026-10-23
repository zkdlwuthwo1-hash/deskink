# 0.6.0 editing and update verification

Scope: the approved one-second shape hold, lasso/move/resize, twelve colors, three remembered eraser sizes, and free Windows/Android in-app updates. Source review baseline: `d03632f`; implementation through `e70dafc`. Guides and release descriptors follow that implementation.

## Automated evidence

- Rust shape/document tests and host annotation/LAN ink tests passed before the final Windows release build. Shapes retain brush attributes and group edits commit one undo record; invalid transforms are rejected without acknowledging them as applied.
- [Android run 39901516665](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39901516665): 54 core tests passed, one Windows-only interop test skipped on Linux, 18 Android View/installer tests passed, five native tablet tests and two additional compact-window tests passed. Lint has zero errors. Native tests use real sockets, MediaCodec/TextureView and stylus input, including a one-second shape hold, selection/move/resize and monitor rebinding before any ink event.
- Native Android captures of the new tools were visually inspected. The expanded dock displays all tools in the full landscape capture. The guide's update deep links, images and overflow checks passed at 1440, 820, 390 and 320px widths.
- iPad model/canvas/tool tests passed in the first full run. The fractional-edge regression was first reproduced in [39900779094](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39900779094), where the emitted transform produced Windows Float coordinates `-0.000015258789`, `1920.0002` and `1080.0001`. `wireAxis` now quantizes and pads the emitted transform; the same edge tests passed in [39901514113 attempt 1](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39901514113/attempts/1).
- That first full iPad run passed 204 of 205 tests; the existing USB-first connection test timed out before HELLO/first-picture processing, then decoded the picture. [Attempt 2](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39901514113/attempts/2) passed **205/205** on the same source and built the arm64 IPA 0.6.0 (17). This was a retry without a production USB change, not evidence of a USB bug fix.
- `scripts/test-android-windows-lan.ps1` rebuilt the real Windows fixture and passed fresh authenticated pairing, H.264 video, reliable ink and monitor rebinding against current Android Java sources. Its isolated Windows-only test was not skipped locally.
- The Windows portable updater's 12 real-script checks passed using isolated temporary folders. No current user installation or presentation was stopped for these checks.
- APK 0.6.0 has package `com.deskink.android`, versionCode `6`, minSdk `26`. v2/v3 signature and alignment checks passed. The certificate remains `d6839b84a1e2e019e546ecbef57747de5fa07483024828770bdd09c23bd1aa02`; upgrades retain the installed app's signing identity.

## Standards

The independent standards review initially identified unnamed Android selection modes; these were replaced with named gestures/handles. The final implementation review found **0 outstanding documented-standard violations and 0 material code smells**. Update metadata/download verification, Android package identity/system installation and the UI lifecycle are separated. Shape recognition runs at the hold deadline; normal pen input does not wait for it or trigger per-point SwiftUI updates.

## Spec

The independent requirements review identified two edge-transform issues: Android could emit an excessive resize factor or an invalid full-width move; iPad double-precision calculations could become out of bounds when Windows evaluated Float coordinates. These have targeted regressions and corrections. A final review also identified the missing Android update descriptor: `updates/android.json` now contains the actual signed release APK's version, exact size and hash. The public descriptor and release download have both been verified with the production update client.

Counts: **3 confirmed findings addressed; 0 additional confirmed implementation findings**. Public delivery verification and the device checks below remain separate from code review.

## Published release evidence

[DeskInk 0.6.0](https://github.com/zkdlwuthwo1-hash/deskink/releases/tag/v0.6.0) includes the Windows ZIP, Android APK, iPad IPA, standalone HTML, Android certificate information and SHA-256 checksums. All six public assets were downloaded without authentication and matched their local originals. The Windows package has 564 manifest entries, all verified, from clean source `d44778c`. The actual previous 0.5.3 ZIP was staged and upgraded to 0.6.0 in an isolated temporary installation with `-NoLaunch`; all new file hashes and an unmanaged user-file sentinel were preserved as expected.

The public [Windows descriptor](https://raw.githubusercontent.com/zkdlwuthwo1-hash/deskink/main/updates/windows.json) and [Android descriptor](https://raw.githubusercontent.com/zkdlwuthwo1-hash/deskink/main/updates/android.json) match the uploaded files. The unmodified production Android `AppUpdate` class fetched the public feed as installed versionCode 5, received code 6, followed the real HTTPS release redirect, downloaded and verified the full APK, then confirmed that installed code 6 receives no redundant update. This exercises real release delivery, not a substituted network service; it does not press the physical Android installer confirmation.

The [public illustrated guide](https://zkdlwuthwo1-hash.github.io/deskink/README.html), including the previously shared `?guide=f335f2d` URL, returns the new 9,487,218-byte standalone HTML with SHA-256 `119b635757e92495dd61f679839b576f37dd0e35da1dbcb9fbfa6709ab8654f6`, matching the packaged and locally reviewed file.

| Asset | SHA-256 |
| --- | --- |
| Windows ZIP | `2d1c8b4e5e4cbdd0daae1fef86bce24d9ef34810093d06e4a6577fd2ead7fc5b` |
| Android APK | `34e583b270c39803cc6c1e3fd9ed285a4de20fe457b4b1e5b65d1367d006f2f7` |
| iPad IPA | `4b30792f743aa41eaa9fd0a27ad27309759dd84540c99e532b0567339577f80e` |

## Known limits and unresolved report

- A user reported that Galaxy monitor changes were not visible until writing. One native test initially failed, but subsequent instrumented, uninstrumented and five-switch runs displayed the new monitor before any pen event. Actual Windows-to-Java transport rebinding also passed. There is no established cause or production decoder fix, so this release does **not** claim to fix that report. Physical Galaxy confirmation remains necessary.
- The in-app updater's descriptor, bounded download/hash, package identity/version/signature and narrow FileProvider installer intent are tested. Actual Android installer confirmation, return after permission settings/process recreation and preserved registrations across package replacement still need a device check. No silent installation is promised.
- Physical Pencil/S Pen feel, long-session latency, radio behavior and Zoom output were not measured. Successful automated tests do not establish zero latency.
- iPad remains an iloader installation. The updater does not bypass signing or provide iPad automatic updates.
