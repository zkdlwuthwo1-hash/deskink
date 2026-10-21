# Wi-Fi / LAN connection

Status: ready-for-human
Automated status: implementation, targeted regression checks, final iPad/Android CI and two-axis follow-up review complete
Packaging status: Windows ZIP, IPA and signed APK verified; all six published assets downloaded and hash-matched; Pages publication check pending
Human verification: physical tablet/radio latency, pen feel and Zoom checks not performed; requires devices
Baseline: 36a09eeb022b6ab951808a66dfdb7de2ca7bf0a9

## Requested outcome

Add local-network IP / Wi-Fi connection without changing the working USB behavior.
Windows remains the screen/annotation host; iPad and Android remain clients.
Do not promise zero radio latency. Avoid additional waiting and unbounded backlogs.

## Implementation scope

- Windows network access is opt-in; USB stays the default. Show PC address, UDP port 9876 and a QR for the iPad. QR contains an address only; first use still requires the existing six-digit pairing code.
- Use existing UDP video, pacing and congestion feedback. Do not change USB encoder, rendering, framing or queue behavior.
- Network ink is a separate acknowledged ordered channel. Apply in-order input immediately; recover duplicates, reordered packets and ordinary loss. Bound memory and stop an unresponsive input channel rather than replaying seconds of old handwriting.
- Require upgraded clients for network ink; keep old USB clients compatible.
- iPad supports QR/direct IP; Android supports direct IP. Existing PC pairing and monitor separation apply.
- Document trusted local networks, firewall setup, no port forwarding, upgrade steps, USB fallback and limits of automated latency checks. Publish updated source, packages and HTML only after relevant checks/review.

## Approved test seams

Continue docs/verification-plan.md (previously approved): connection input -> response/visible ink, duplicates/order/reconnect/size limits; existing USB tests remain regression checks. Also exercise actual UDP sockets and video payload delivery through start_sender. These catch protocol and queue regressions, not physical Wi-Fi, Pencil-to-pixel or Zoom latency.

## Wire contract

- HELLO2 FEATURE_RELIABLE_INK = 0x0010; host capability = 0x8000.
- UDP ink: `DI02` + existing version1 JSON envelope (maximum1024 JSON bytes).
- ACK: exactly20 bytes: `DA02`, little-endian session u32, stream epoch u32, highest contiguous applied sequence u64.
- Pending/reorder window: maximum256 events. Immediate first send; retry oldest missing events approximately every20ms. If oldest unacknowledged event reaches250ms, client ends the connection with a clear message; never silently forget an event and continue the same sequence.
- Receiver authenticates exact peer/session/epoch before accepting input. USB continues using DI01 without ACK, retry or buffering changes.

## Verification

See [wireless-verification.md](../../../docs/wireless-verification.md) for evidence and the physical-device checklist.

- Host integration: 20 checks passed; final transport library: 54 checks passed. The actual UDP frame-sequence regression failed with expected 1 / actual 7, then passed in the final unfiltered run.
- Rust core/wire: 20 + 55 passed. Packaged video/decode paths: 2 passed.
- Final iPad CI [39540343857](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39540343857): 190 passed, source `b90617a`, IPA 0.5.0 (15).
- Final Android CI [39540516892](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39540516892): 37 core + 11 View + 5 full-screen + 2 compact-window checks passed, lint 0 errors, source `b90617a`. Signed APK 0.5.0 (4) keeps the previous certificate; v2/v3 and zip alignment verified.
- Windows Release built from `71d1561`, packaged from clean `6fde20c`. ZIP 565-file manifest, allowed contents, clean-environment runtime setup and all six published asset downloads passed. Pages publication check remains pending.
- Both independent reviewers confirmed the LAN frame-ID P1 and pre-dispatch ink-queue P2 corrected, with no additional findings. The low-priority Android repeated USB/LAN branch advisory is deferred to preserve the working USB implementation.
- HTML passed sequential browser checks at 1440/820/390/320px, with additional wireless/deep-link visual checks at 320/390/1440px.
- No physical Wi-Fi/Pencil/S Pen/Zoom latency measurement has been performed. No zero-delay claim is made.
