# DeskInk 0.6.4 editing verification

Baseline: `bbaf448`. Spec: `.scratch/desk-ink/issues/16-selection-eraser-shapes.md` and the user's request for better rectangles, selection controls and stroke/partial erasing.

## Diagnosis and changes

- Representative boxes with a closing gap and with a 45-degree tilt failed the real shape recognizer. The minimal five-point fixtures failed with `:core:test --tests com.deskink.core.InkShapeTest` before their fixes. These are constructed regression cases, not recorded user Pencil traces.
- Ranked hypotheses were closure rejection, side-fitting tolerance and corner evidence. Changing closure only fixed the gap case; increasing the existing axis search to ±45 degrees fixed the tilt case. Circular shape closure, corner evidence, handwriting rejection and the 500 ms deadline remain intact. Shared shape fixtures exercise both tablet implementations.
- Selection uses its existing eight resize handles and gains a native, labeled delete button. Deletion batches the existing selection IDs, removes only those strokes and commits one undo operation.
- The partial eraser clips the stroke centerline against the swept circular eraser plus half the brush width. Disconnected contours retain the same stroke ID; renderers and hit tests do not bridge gaps. Color, opacity, width and undo history are retained. A remaining piece can still be selected and transformed.
- Windows advertises capability `0x0400` for selection deletion and partial erasing. Tablets do not send those commands to older hosts. The prior stroke eraser remains the default; mode and size are remembered. USB/video/network implementations are unchanged.

## Test evidence so far

- Windows selection deletion failed with `InvalidMessage`, then passed with selected-only delete/undo/redo.
- Windows and Android partial-middle tests failed before implementation, then passed with independently specified endpoints 100,188,212,300 for a radius-10 eraser on a width-4 line.
- Windows pixel test initially found alpha 255 in the erased gap, then passed with alpha 0 after contour rendering was updated. It also checks both remaining ends and undo through the cached raster.
- Android native bitmap checks verify the transparent gap; real MotionEvents verify S Pen button release, pen restoration, selection resize/delete/undo and the existing 500 ms timer. The native eraser dialog test checks mode persistence and sizes.
- Shared swept/repeated erasing fixtures cover multiple strokes, box sides, dots, repeated cuts and full removal. Unit/CI and release evidence will be recorded below after completion.

Physical Pencil/S Pen feel and real display latency are not measured by these tests. Wider rectangle acceptance does not guarantee recognition of every handwritten quadrilateral.

## Independent review and corrections

- Standards review: fixed Float/CGFloat input precision at the partial-erasing boundary; the shared fractional-dot case now specifies the same result and undo on all three platforms. Consolidated selection transaction batching within each platform.
- Spec review: preserved the old diagonal closure allowance for wide rectangles while adding the new gap allowance. Added a failing wide-box regression, then observed it pass after the max-of-both fix.
- Spec review: blocked partial erasing on older hosts now closes an active Android pen stroke and cancels its shape timer before rejecting input. A MotionEvent test failed before the change and passes afterward, checking that no line bridges the blocked movement. Both tablets show an update message for Pencil/S Pen activation, not only a disabled menu item.
- Both reviewers rechecked commit `d665402` and reported the original findings resolved. Their rechecks were read-only, separate from runtime tests.
- The first native Android run failed when an assertion raced OS focus delivery immediately after dismissing a picker. The test now waits up to five seconds for actual window focus and retains its original assertion and input/host-command checks. This changes test synchronization, not production focus handling.
- Windows debug verification encountered a rustc 1.98 incremental fingerprint compiler error after an interrupted build. Release compilation passed. Debug checks are rerun with incremental compilation disabled; no application code is changed for this compiler failure.

## Final validation

- Source `d665402`: Windows Release built successfully. `scripts/test-windows.ps1` passed 103 checks with incremental compilation disabled; three explicitly interactive desktop diagnostics were not run. The separate Windows cached-raster eraser-gap test passed.
- [iPad run 62](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39949761415) passed all 26 selected editing, viewport, tool, wire and latency tests and built 0.6.4 (21). These include the new fractional boundary fixture and native selection delete button/undo.
- [Android run 42](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39949765826) passed unit tests, lint, five native tablet video/input tests and two compact-window/font/keyboard tests. Release APK 0.6.4 (10) uses the existing certificate SHA-256 `d6839b84a1e2e019e546ecbef57747de5fa07483024828770bdd09c23bd1aa02`; v2/v3 signature and 16-KB alignment checks passed.
- HTML uses unedited native selection/eraser screenshots with documented provenance. Image decoding, enlargement, current release links, console checks and no horizontal overflow passed at 1440, 820, 390 and 320 pixels. The mobile capture was visually inspected.

Release downloads, update descriptors and local installation are verified separately after packaging.
