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
