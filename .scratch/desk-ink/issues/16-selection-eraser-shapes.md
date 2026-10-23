# More forgiving rectangles, selection controls and two eraser modes

Status: in progress

## User request

- Rectangles still fail recognition. Keep the 0.5-second correction before lift.
- Tapping a lasso selection should expose resize handles and a delete action.
- Offer stroke erasing and partial erasing. Keep three independent eraser sizes.

## Implementation decisions

- Use the already approved input/document/transmission seams in docs/verification-plan.md.
- Preserve normal pen delivery, USB/wireless transport and one-operation undo.
- Default to the existing stroke eraser; remember mode and size.
- Partial erasing removes only the touched area from freehand and corrected shapes.
- Selection deletion affects only selected ink and is undoable.

## Work

- [ ] Reproduce and fix imperfect rectangle recognition with negative circle/handwriting cases.
- [ ] Add selection deletion and visible selection controls on both tablets.
- [ ] Add partial erasing, mode picker, Windows synchronization and history tests.
- [ ] Build/test all affected platforms, review and update guide.
- [ ] Publish verified artifacts/update feeds and update the connected iPad if available.
