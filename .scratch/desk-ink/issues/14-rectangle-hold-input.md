# Rectangle hold and unexpected loss of pen input

Status: needs-info
Blocked by: whether a fresh Pencil contact resumes drawing after the failure; the reported physical-device failure is not reproduced yet.
Baseline: d25eeb5 (tablet 0.6.1)

The user reports that a rectangle does not correct despite holding its endpoint. They confirmed drawing all four sides in one continuous stroke without lifting. They also report that pen functionality disappears and the app seems to switch to finger-touch mode. Do not explain this away as four separate strokes or claim the earlier jitter fix resolves this report.

The user clarified that pen strokes stop, while finger pan/zoom still works. They did not report finger drawing or a changed toolbar selection. The usage instructions were given: enable the blue Shapes toggle, use Pen or Highlighter, draw one closed stroke and hold still for approximately one second without extra pressure.

Independent checks completed:
- The local Windows process is the updated Desktop/DeskInk/bin/deskink.exe, PID 29692, monitor 0.
- The existing real Android input test `InkCanvasTest.aClosedPenStrokeBecomesAShapeOnlyAfterOneSecondOfHolding` was rerun and passed. It verifies the closed rectangle is still ordinary ink at 999 ms and becomes a rectangle at 1000 ms. This ideal input does not reproduce the reported failure.
- The actual iPad canvas input test `DeskInkEditingTests.testHoldingThenSelectingMovingAndResizingUsesTheActualCanvasInput` and the other editing tests were rerun in [39918885611](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39918885611); all six passed.
- The new UIKit Pencil-callback test draws a slightly uneven closed rectangle through touchesBegan/touchesMoved, holds it, lifts, and draws the next stroke inside the real viewport. It also checks finger panning is suppressed during Pencil contact and restored on lift. It passed in 1.213 seconds in the same run. This tested sequence does not reproduce the physical-device failure.
- A read-only capture of the connected iPad returned an entirely black screen, so it did not establish the current app state. No UI input was sent to the physical device.

No production code was changed. Do not change recognition thresholds or the freeze-on-correction behavior until a failing input/interaction loop distinguishes them from lost connection, tool selection or a cancelled gesture. The installed 0.6.1 builds and public release remain unchanged.

An asynchronous question is pending: after fully lifting the Pencil and touching down again, does a new line draw? This distinguishes one-contact shape freezing from input remaining disabled across contacts. Do not sleep/poll for that answer. The independent CI work is complete and its log is saved under `artifacts/local/rectangle-hold/ipad-callback-tests.log`.

Next: use the user's clarification to reproduce the combined rectangle/pen symptom through the agreed input or connection seam; capture red before fixing. If the exact drawing cannot be reproduced, obtain a short recording or targeted input trace rather than treating an unrelated synthetic failure as this bug. The diagnostics skill requires a red-capable loop before root-cause hypotheses or a production fix; no root cause has yet been established for this report.
