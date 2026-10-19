# Android lab preview verification — 2026-10-19

Scope: native Android app, Windows ADB USB bridge, ordinary desktop screen shortcuts, signed APK, portable ZIP and bilingual installation guide. Approved behavior is recorded in [android-plan.md](android-plan.md).

## Automated and local results

| Boundary | Result | What it establishes |
| --- | --- | --- |
| Java core | 19 tests passed | Momentary S Pen eraser state, shared wire vectors and commands, framing, pairing, stale monitor rejection, two-PC session isolation, frame recovery, ink history and inverse zoom coordinates |
| Android Views / Robolectric | 6 tests passed | Native stylus and palm events, two-finger zoom, Canvas pixels, toolbar commands and Activity socket lifecycle |
| Android API 35 emulator | 2 instrumentation tests passed | Actual MediaCodec output to a Surface and real app socket → decoded TextureView → toolbar command |
| Windows USB/session | 12 tests passed | Owned ADB forward, cleanup after async runtime shutdown, authenticated Android/iPad ink, monitor isolation and existing Apple USB framing |
| Rust ink core | 20 tests passed | Strict command decoding and shared JSON fixtures, stroke history, eraser, transient ink, pointer and malformed input rejection |
| Android lint / release build | Passed, 0 errors | APK compilation and resource checks; three informational warnings recommend newer **test-only** AndroidX versions |
| Signed APK | Verified | APK Signature Scheme v2/v3, one stable RSA signing identity, zip alignment; no debug signing key |
| Windows portable package | Passed | Extracted into a path with spaces/Korean, minimal system PATH, video runtime and Android runtime probes |
| First normal launch / desktop icons | Passed | Two actual desktop shortcuts created, each targets the package with `--monitor 0/1`; 1→2→1 selected the real monitors using one host; zero visible console-window events |
| Illustrated guide | Passed | 1440px desktop / 390px mobile layouts, all three embedded captures, no horizontal overflow, image dialog and Escape key |

The native emulator run is [39141000170](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39141000170), source `d9a0f2e`. Later frame-gap and packaging changes were checked locally. [Screenshot provenance](images/README.md) distinguishes emulator/test backgrounds from physical tablet or private Windows captures.

These checks do **not** establish a physical Galaxy Tab latency figure or Samsung's hardware event behavior. No physical Android tablet was available during this verification. The existing iPad build 13 IPA is reused unchanged from v0.3.0-lab.1; its binary is not rebuilt for this release.

## Review and corrections

Fixed review baseline: `2d9e14a...fb53059`; correction review: `fb53059..59eaacf`. The two axes below were reviewed independently, using the installed `code-review` skill. The requirements interview and TDD boundaries were already approved; `Kanban` and the exact `improve-codebase` alias were unavailable. Broad architecture changes were not inferred from the differently named architecture proposal skill.

### Standards

Four concrete findings were corrected: fixed 60fps reporting became measured decoder output and queue depth; an unanswered handshake now times out; codec work and external callbacks no longer hold the pen writer's shared lock; ADB removes its own forward even after Tokio has stopped. The follow-up reviewer found these resolved and no new major security, correctness or performance defect in the correction diff.

One advisory smell remains: the wire-report encoder has 14 numeric arguments. It is confined to the protocol adapter, and caller-side decoder statistics now have named fields. This was retained to avoid an unrelated protocol-interface redesign.

### Spec

Three findings were corrected: ordinary `begin` no longer carries the transient-only `lifetime_ms` field rejected by Windows; palm pointer transitions no longer reopen an active pen stroke; video consumer stalls no longer block ink enqueueing. The follow-up reviewer confirmed all three and found no new major defect in the correction diff.

Standards: four concrete findings resolved, one advisory item retained; Spec: three findings resolved, no outstanding finding in the reviewed correction scope.

## Red → green evidence

The added regressions were each run failing before their corresponding implementation change:

- `InkTest.ordinaryAndTemporaryStrokesMatchTheSharedWindowsCommandContract`: ordinary `begin` included an unknown field; now exactly matches the fixture also decoded by Rust.
- `InkCanvasTest.aPalmLandingAndLiftingDuringAStrokeDoesNotRestartThePenGesture`: additional finger contact reopened the pen; now one stroke ID reaches its matching end and clear.
- `UsbClientTest.penCommandsProgressWhileTheVideoConsumerIsBusy`: timed out while a consumer held the lock; now sends through the actual socket while that consumer remains stalled.
- `UsbClientTest.anUnresponsiveHandshakeReleasesThePortForTheNextUsbConnection`: an unanswered ACK blocked the only accept loop; now the next host is accepted.
- `UsbClientTest.receiverReportDoesNotClaimFramesWereDecodedWhenNoVideoWasShown`: reported 60fps with no decoded video; now reports zero.
- `FrameAssemblerTest.aWholeMissingDeltaFrameRequiresANewKeyframeBeforeVideoResumes`: accepted a dependent frame after a whole missing frame; now waits for a fresh keyframe.
- `InkDocumentTest.twoConnectedPcSessionsKeepSeparateMonitorInkWhenSwitchingBack`: returning to the first PC recreated an empty tablet board; now the two most recently connected PC sessions keep separate monitor documents while the Android app stays alive. A new Windows process token starts fresh.
- `owned_forward_is_removed_even_after_the_async_runtime_has_stopped`: left the forwarding port behind; now the bounded disconnect cleanup completes independently of that runtime.

## First physical Galaxy Tab check

1. Install the APK, enable USB debugging, authorize this Windows PC and enter DeskInk's six-digit pairing code.
2. Draw with pen and highlighter. Press the S Pen button during contact, erase, release it and continue drawing. Repeat after lifting the pen, hovering, touching with a palm and changing app focus.
3. Check erase/undo/redo/clear on both tablet and Windows. Switch Screen 1→2→1 and confirm each monitor retains only its own ink while the host stays open.
4. Pinch and pan with two fingers. Confirm Windows stays at its original scale and newly drawn points land at the intended Windows locations. Test landscape and portrait toolbar scrolling.
5. Compare tablet local ink and Windows ink with a high-frame-rate camera if measuring latency. Report the tablet model, OS, USB cable/port, PC/GPU and capture FPS; decoder FPS alone is not end-to-end pen latency.
6. Unplug/replug, sleep/wake and reopen the app. In Zoom, check the shared **whole monitor** from another participant's view.

Useful commands and signing-key handling are in [development.md](development.md). Public release files exclude pairing logs, device identifiers, account credentials and private signing material.

## Android 0.4.1 interface update — 2026-10-19

Scope: [UI ticket](../.scratch/desk-ink/issues/07-android-ui.md). The iPad toolbar is the visual reference: line icons, short labels, a rounded dock, selected-tool feedback and native color/width previews. Connection and pairing screens share the same dark palette and bundled Geist fonts. The header and dock remain outside the viewport. No transport or codec changes, WebView, background blur or perpetual animation are introduced.

Local verification: 19 core tests and 10 Android View tests passed; lint reports 0 errors and the same three existing test-dependency update suggestions. Release and instrumentation APKs compile. Four new behavior regressions were exercised red → green:

- The selected-tool state now follows S Pen button erasing and restoration.
- Drawing after hiding ink updates the subsequent visibility action correctly.
- Undo and redo reflect the connected document's available history.
- Pairing validation remains fully reachable in a 380×260px test window. The test waits for the dialog's `onShow` listener before attempting submission; without the scroll fix its visible-rectangle assertion fails.

### Standards

Independent review of `959311a...8fa0251` found two concrete layout defects: header captions were squeezed into a 48dp height, and the pairing form could not scroll above the keyboard. Both were corrected in `331cc26`: compact accessible header icons and content-sized dock buttons, plus scrollable forms with native action buttons outside the scroll area. Follow-up review found no new major regression or actionable code smell. Ordinary pen moves and hover do not refresh the dock unless the tool changes.

### Spec

Independent requirements review found two related gaps: short-window sheets and large-font captions. Both were corrected. S Pen restoration, selected-tool feedback, visibility/history and tablet-only zoom remain consistent with the approved requirements. Public APK delivery and actual emulator captures are verified separately from source review. Physical Galaxy Tab pen feel and latency remain device checks.

Standards: 2 findings resolved, 0 outstanding in the reviewed correction. Spec: 2 findings resolved, 0 outstanding in the reviewed correction.

The first screenshot pass exposed an observation gap: the IME capture ran before the keyboard finished appearing. The test now waits for actual IME visibility. That revealed Android's fullscreen input editor in the compact landscape window. Pairing now keeps its numeric keyboard inside the normal app context (`a82ce6c`), and the native check rejects fullscreen input mode. Independent Standards and Spec follow-ups over `331cc26..a82ce6c` found no actionable defect.

Final native run [39190227883](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39190227883), source `a82ce6c`, passed all 29 unit tests, three full-tablet instrumentation tests and the additional compact-window pass. Actual MediaCodec output, native stylus ink, toolbar commands, both orientations, large fonts and pairing with the keyboard visible were checked. The final captures were visually inspected; the connected workspace is embedded unchanged in the HTML guide. Guide checks passed at 1440, 820, 390 and 320px widths, including tabs, offline images, enlargement/focus return and the no-JavaScript fallback.

The release APK retains `com.deskink.android`, increases versionCode to `2` / versionName `0.4.1`, and passes v2/v3 signature and zip-alignment verification. Its signing certificate matches the original release (`d6839b84a1e2e019e546ecbef57747de5fa07483024828770bdd09c23bd1aa02`), allowing an update over the existing app. APK SHA-256: `cc277cf75daaeb524653726391b4ea771eb9e81bb417c44f15795d7af1c9b2b6`. Physical Galaxy Tab latency and S Pen feel have not been measured.

Native layout references: [Android edge-to-edge/inset handling](https://developer.android.com/develop/ui/views/layout/edge-to-edge), [accessible touch targets and labels](https://developer.android.com/guide/topics/ui/accessibility/apps). The disposable CI emulator suppresses the one-time system fullscreen tutorial using the same setting as [AOSP window-manager tests](https://android.googlesource.com/platform/frameworks/base/+/c8ae8f11ab345baae42288a725545d4d73af79b4/services/tests/wmtests/AndroidTest.xml); the shipping app does not change system settings.
