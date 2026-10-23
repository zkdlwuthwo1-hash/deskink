# 0.6.3 half-second shape hold

The user shortened the requested endpoint pause from one second to approximately **0.5 seconds**. The corrected shape must still appear while the pen is touching, so the user can see it before lifting. Baseline: `95c175d` (tablet 0.6.2). Production change: `76040c6`.

## Change

- iPad's existing timer now waits 0.5 seconds; Android's existing callback waits 500 ms. No new recognizer, input buffering or network delay was added.
- Eight visible points/dp of tremor tolerance, meaningful-movement reset, tool/capability guards, early-lift cancellation, shape fitting and normal ink delivery are unchanged.
- Accessibility instructions, English/Korean READMEs and the self-contained HTML now say 0.5 seconds. iPad is 0.6.3 build 20; Android is 0.6.3 version code 9. Windows remains 0.6.0.

## Verification

The already approved input/document/transmission boundary was used. The Android MotionEvent rectangle test was changed first: no shape at 499 ms, a rectangle at 500 ms while the stroke remains unfinished. It failed on the one-second implementation (`a1d6b57`), then passed after the deadline change. The red XML is retained under ignored `artifacts/local/half-second-hold/android-red.xml`.

All **11 local Android canvas tests passed**, including small and subpixel tremor, a tilted rectangle, an early lift at 400 ms, disabled correction/older hosts, undo, next tool, palm handling and two-finger zoom. Lint and release assembly passed. Signing and alignment retained the existing Android certificate.

The initial shortened subpixel fixture failed because first window layout was still pending when timed samples began. The fixture now settles initial layout before setting its drawing bounds, matching the existing hand-tremor fixture. `d2ec3e2` changes the fixture and documentation only; no additional production change or relaxed deadline was needed. The first Linux run [39936966462](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39936966462) captured that same fixture failure.

Final Android CI [39937534762](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39937534762) passed **77 unit tests**, with one opt-in Windows interop test skipped, followed by lint/release assembly, **five native tablet tests** and **two compact-window tests**. Downloaded result files confirmed zero failures. This includes actual video decoding and USB/wireless input integration.

iPad CI [39936962075](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39936962075) passed **21 tests** and built the device IPA. The actual UIKit Pencil-callback test checks ordinary ink at a short pause, a rectangle within the new deadline allowance before lift, restored finger gestures after lift, and the next Pencil stroke. Timer tests use an absolute wait target to avoid accumulating separate sleep delays. IPA metadata was checked as 0.6.3 (20).

Review confirmed that the production diff changes only the two hold deadlines and accessibility hint text. Geometry, transport, Windows code, pen styles and toggle defaults did not change. Physical Pencil/S Pen latency has not been measured; the automated deadlines do not claim an exact wall-clock display guarantee under all device loads.

## Delivery

iloader reported installation complete on the connected iPad. A subsequent device metadata query confirmed **0.6.3 build 20** under the existing app identifier. Physical Pencil feel after the update was not measured.

[Release v0.6.3](https://github.com/zkdlwuthwo1-hash/deskink/releases/tag/v0.6.3) includes the signed APK, unsigned IPA, standalone HTML, Android signing note and SHA-256 manifest. All five public downloads matched local sizes and SHA-256 hashes. The Android update feed advances to code 9 only after that verification; Windows remains 0.6.0. The HTML's local anchors and embedded images passed validation.

The production `AppUpdate` class fetched the public feed, offered code 9 to code 8, downloaded and hash-verified the APK, and offered nothing to code 9. Both the normal Pages URL and the previously shared `README.html?guide=f335f2d` returned HTTP 200 and matched guide SHA-256 `11e87625a17b8195590fedc6a9f496613d209f3ee2dfb848ce73c14a25577efd`.
