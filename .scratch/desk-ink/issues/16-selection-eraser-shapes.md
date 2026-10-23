# More forgiving rectangles, selection controls and two eraser modes

Status: completed

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

- [x] Reproduce and fix imperfect rectangle recognition with negative circle/handwriting cases.
- [x] Add selection deletion and visible selection controls on both tablets.
- [x] Add partial erasing, mode picker, Windows synchronization and history tests.
- [x] Build/test all affected platforms, review and update guide.
- [x] Publish verified artifacts/update feeds and update the connected iPad if available.

Release v0.6.4 has six anonymously downloaded, hash-verified assets. Windows and Android update descriptors point to those assets. The production Android updater offers code 10 to code 9, verifies the public APK, and offers no update to code 10. Windows portable Stage/Apply completed on the user's Desktop installation and all 564 managed hashes match; all four Screen/화면 shortcuts still point to its launcher. iloader reported Operation completed installing the verified 0.6.4 (21) IPA with the existing account. The iPad disconnected afterward, so its post-install version could not be independently queried; physical Pencil feel remains unverified. See docs/editing-064-verification.md for red/green evidence, separate reviews, native checks and delivery details.
