# Wi-Fi / LAN connection

Status: implementing
Blocked by: none for implementation; real tablet/radio latency verification requires devices
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

Pending implementation, red/green evidence, two-axis review, packaged builds and real-device checklist.
