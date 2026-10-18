# Android lab preview verification — 2026-10-19

Scope: native Android app, Windows ADB USB bridge, ordinary desktop screen shortcuts, signed APK, portable ZIP and bilingual installation guide. Approved behavior is recorded in [android-plan.md](android-plan.md).

## Automated and local results

| Boundary | Result | What it establishes |
| --- | --- | --- |
| Java core | 18 tests passed | Momentary S Pen eraser state, shared wire vectors and commands, framing, pairing, stale monitor rejection, frame recovery, ink history and inverse zoom coordinates |
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
- `owned_forward_is_removed_even_after_the_async_runtime_has_stopped`: left the forwarding port behind; now the bounded disconnect cleanup completes independently of that runtime.

## First physical Galaxy Tab check

1. Install the APK, enable USB debugging, authorize this Windows PC and enter DeskInk's six-digit pairing code.
2. Draw with pen and highlighter. Press the S Pen button during contact, erase, release it and continue drawing. Repeat after lifting the pen, hovering, touching with a palm and changing app focus.
3. Check erase/undo/redo/clear on both tablet and Windows. Switch Screen 1→2→1 and confirm each monitor retains only its own ink while the host stays open.
4. Pinch and pan with two fingers. Confirm Windows stays at its original scale and newly drawn points land at the intended Windows locations. Test landscape and portrait toolbar scrolling.
5. Compare tablet local ink and Windows ink with a high-frame-rate camera if measuring latency. Report the tablet model, OS, USB cable/port, PC/GPU and capture FPS; decoder FPS alone is not end-to-end pen latency.
6. Unplug/replug, sleep/wake and reopen the app. In Zoom, check the shared **whole monitor** from another participant's view.

Useful commands and signing-key handling are in [development.md](development.md). Public release files exclude pairing logs, device identifiers, account credentials and private signing material.
