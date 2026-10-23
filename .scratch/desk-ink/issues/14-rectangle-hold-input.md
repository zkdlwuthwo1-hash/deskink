# Rectangle hold and unexpected loss of pen input

Status: in-progress
Blocked by: none for recognition and hold improvements; the earlier persistent pen-input failure remains unproven.
Baseline: d25eeb5 (tablet 0.6.1)

The user reports that a rectangle does not correct despite holding its endpoint. They confirmed drawing all four sides in one continuous stroke without lifting. They also report that pen functionality disappears and the app seems to switch to finger-touch mode. Do not explain this away as four separate strokes or claim the earlier jitter fix resolves this report.

The user clarified that pen strokes stop, while finger pan/zoom still works. They did not report finger drawing or a changed toolbar selection. The usage instructions were given: enable the blue Shapes toggle, use Pen or Highlighter, draw one closed stroke and hold still for approximately one second without extra pressure.

Independent checks completed:
- The local Windows process is the updated Desktop/DeskInk/bin/deskink.exe, PID 29692, monitor 0.
- The existing real Android input test `InkCanvasTest.aClosedPenStrokeBecomesAShapeOnlyAfterOneSecondOfHolding` was rerun and passed. It verifies the closed rectangle is still ordinary ink at 999 ms and becomes a rectangle at 1000 ms. This ideal input does not reproduce the reported failure.
- The actual iPad canvas input test `DeskInkEditingTests.testHoldingThenSelectingMovingAndResizingUsesTheActualCanvasInput` and the other editing tests were rerun in [39918885611](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39918885611); all six passed.
- The new UIKit Pencil-callback test draws a slightly uneven closed rectangle through touchesBegan/touchesMoved, holds it, lifts, and draws the next stroke inside the real viewport. It also checks finger panning is suppressed during Pencil contact and restored on lift. It passed in 1.213 seconds in the same run. This tested sequence does not reproduce the physical-device failure.
- A read-only capture of the connected iPad returned an entirely black screen, so it did not establish the current app state. No UI input was sent to the physical device.

At this initial stage no production code was changed. Recognition thresholds and freeze-on-correction behavior were left alone until a failing input/interaction loop distinguished them from lost connection, tool selection or a cancelled gesture. The installed 0.6.1 builds and public release were unchanged at that point.

The user has now clarified: the pen does not work at all, while finger taps can select toolbar tools. Treat this as persistent loss of pen input, not merely the expected freeze of a corrected shape during one contact. The user has not yet compared pen input with the tablet's built-in notes app.

A second read-only screenshot, explicitly targeting the connected iPad after a second Apple device appeared, also returned an entirely black image (`artifacts/local/rectangle-hold/ipad-pen-unresponsive.png`). It provides no evidence of the app's selected tool, foreground state, or connection state. Do not infer a cause from that image. No app restart, device input, production edit or new installation was performed.

The earlier Notes comparison is no longer needed for the current working pen: the user restarted and confirmed pen input recovered. Its root cause remains unproven. The independent CI log is saved under `artifacts/local/rectangle-hold/ipad-callback-tests.log`.

## Subsequent physical-device evidence

After reopening, `artifacts/local/rectangle-hold/ipad-after-reopen.png` showed live USB video, ordinary ink and a white Shapes toggle. Reading only the relevant app preference confirmed `deskink.shape-correction=false`; the temporary full preference file was removed. The user enabled correction and confirmed it changes shapes.

The user then reported two remaining issues: the hold feels much longer than one second, and rectangles sometimes become circles. They requested approximately one second and better recognition. The screenshot contains tilted, uneven boxes; it is not a timestamped Pencil trace.

## New reproduction

- Android's actual MotionEvent input test `InkCanvasTest.aSmallHandTremorDoesNotRestartTheOneSecondHold` fails on the baseline: +/-4 dp endpoint movement repeatedly resets the one-second hold. At 1000 ms no shape command is sent. Red XML: `artifacts/local/rectangle-hold/tremor-red.xml`.
- A diagnostic probe of the production recognizer reports a mathematical square rotated ten degrees as `ellipse`; zero, two and four degrees report `rectangle`. Six and eight degrees are rejected. The probe is `artifacts/local/rectangle-hold/ShapeProbe.java`. This is a constructed minimal reproduction of the reported classification, not a captured physical-device trace.
- Matching real canvas input tests are added for the tilted rectangle and hold tremor on both platforms. The public input/document/transmission seam was already approved in `docs/verification-plan.md`.

## Ranked predictions shown to the user

1. Small hand movement restarts the hold: changing only the screen-space movement allowance should produce a correction at one second while ordinary movement still postpones it.
2. Tilt prevents rectangle matching: aligning the same square's sides before comparing geometry should keep it a rectangle while leaving circles/ovals as ellipses.
3. Dense stationary samples corrupt the geometry: comparing the movement prefix with its stationary tail would change the result. Existing subpixel tests and movement-prefix handling already cover this; do not discard live ink samples.

Keep the deadline at 1000 ms; tolerate small endpoint tremor rather than shortening the requested hold. Retain pen/highlighter styling, original raw ink during drawing, one undo step, lift cancellation, next-stroke input, and the existing USB/network paths. Do not claim enabling Shapes fixes the earlier persistent input interruption.

## 0.6.2 implementation and delivery

The user explicitly confirmed that the corrected shape must appear while the pen is still touching, so they can see it before lifting. Tests now check the actual document and outgoing shape command before pen-up.

`6771043` increases only the screen-space hold allowance to eight points/dp, keeping the one-second deadline, and recognizes modestly tilted rectangle sides before ellipse matching. Both reproduced bugs went red before the fix and green afterward. The corrected Android tremor fixture settles initial window layout before drawing; `tremor-red-stable-layout.xml` is the authoritative red result. Shared geometry fixtures cover 135 cases per platform.

The connected iPad was updated through iloader; device metadata confirms 0.6.2 (19). The signed Android 0.6.2 APK uses the existing certificate. Full evidence, separate standards/spec reviews and release status are in `docs/shape-062-verification.md`.

The earlier persistent loss of pen input remains unproven and is not marked fixed. The user recovered ordinary pen input by reopening the app.
