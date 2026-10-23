# 0.6.2 hold-to-correct verification

Scope: correct a recognizable shape while the pen remains down for approximately one second, tolerate small endpoint tremor, and stop slightly tilted rectangles becoming ellipses. Review baseline: `d25eeb5`; production fix: `6771043`. iPad is 0.6.2 build 19; Android is 0.6.2 version code 8. Windows remains 0.6.0.

## Reproduction and change

After restarting, the user confirmed that pen input worked again. A read-only iPad screenshot showed Shapes off; the relevant stored setting agreed. The user enabled Shapes and confirmed correction, then reported excessive hold time and rectangles becoming circles. This explains the disabled correction state, **not** the earlier persistent loss of pen input, whose cause remains unproven.

Two smaller failures were reproduced through the real canvas input boundary before changing production code:

- Small endpoint movements repeatedly reset the one-second timer. The deadline stays at 1,000 ms; movement allowance is now eight visible points on iPad / dp on Android, converted through display and viewport scale. Raw ink still renders and transmits immediately. Recognition uses the movement prefix, excluding dense stationary tail samples.
- A square rotated ten degrees was classified as an ellipse. Rectangle matching now also checks modestly rotated axes before considering an ellipse. Output remains an upright rectangle within the original bounds, so the existing wire format and Windows renderer are compatible.

Correction updates the displayed document and sends the shape command **before lift**. Tests assert this while contact is still active; lifting afterward completes the contact without waiting to recognize the shape. Existing cancellation, styles, undo and next-stroke behavior are retained. USB, network, capture and Windows source were not changed.

## Tests

- iPad red: [39926263346](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39926263346). Both `testSmallHandTremorDoesNotRestartTheOneSecondHold` and `testSlightlyTiltedRectangleMustNotBecomeAnEllipse` failed on the baseline. Six other selected tests passed.
- iPad green and IPA: [39927811414](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39927811414), **21 tests passed** across editing, tools, viewport and wire behavior. This includes actual UIKit Pencil callbacks and shape assertions before pen-up. IPA metadata is 0.6.2 (19).
- Android red: actual MotionEvent tests reproduced the same two failures. The tremor fixture initially allowed its first Activity layout to occur during drawing. The fixture now settles that layout before input; restoring the original production threshold still failed, and the new threshold passed. This avoids mistaking a test-only coordinate jump for hand movement.
- Android focused green: **11 actual input tests** and both shape test methods passed. Shared fixtures exercise **135 cases per platform**: nine paths, five rotations and three scales. Rectangles include rounded and uneven corners; circles, ovals, triangles, open boxes and writing-like paths guard against false rectangles. Previously unrecognized tilted ovals may remain ordinary ink, but may not become rectangles.
- Android Linux unit/lint: [39928891139](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39928891139), **77 tests passed**, one opt-in Windows interop test skipped, lint passed. The separate full local Windows run hit the existing Robolectric FileProvider path failure in `UpdateInstallerTest`; the same test passed on Linux. The updater was not modified or its test disabled.
- Android native integration, first attempt: all **five tablet video/input/UI tests passed**, including real-socket USB shape/erase commands and wireless monitor video changes. The second, compact-screen run passed the network-dialog test but failed `Connect must remain above the keyboard` in the pairing layout test. This failure is not a shape-recognition result.
- Android rerun, same source: [attempt 2](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39928891139/attempts/2) **passed the complete workflow**. Downloaded XML reports confirm **five native tablet tests and two compact-window tests passed**. No code or assertions were changed between attempts. The first compact-keyboard assertion is recorded as intermittent validation behavior, not claimed fixed by this release.
- Local Android release assembly, APK alignment and signing passed. The existing signing certificate was retained: SHA-256 `d6839b84a1e2e019e546ecbef57747de5fa07483024828770bdd09c23bd1aa02`.

Temporary shape-hold logging was removed. Diagnostic artifacts remain under ignored `artifacts/local/rectangle-hold/`; account preference files were removed after checking the single relevant boolean.

## Standards review

Independent review against repository instructions reported **zero documented standard violations and zero material code smells**. Tests use the approved input/document/transmission boundary and share geometry fixtures. No unrelated module redesign was introduced.

One non-blocking observation: the iPad tremor test uses short real run-loop waits, so unusually slow CI scheduling could make its early-deadline assertion flaky. The current run passed; this is not a production timing claim.

## Spec review

Independent requirements review reported **zero violations**. It checked screen-space hold tolerance, the unchanged one-second deadline, correction during contact, tilt classification, cancellation, styling, ordinary ink and next-stroke behavior. Additional analytical ellipse/circle comparisons found no newly classified rectangles in the sampled cases.

## Physical-device and release status

The IPA and signed APK are prepared. iloader reported installation complete, and a subsequent device metadata query confirmed **0.6.2 build 19** on the connected iPad, replacing the existing app under the same bundle identifier. Public download/update-feed checks are recorded here when completed. HTML and both READMEs explain enabling the blue Shapes toggle, holding the endpoint without lifting, and lifting only after seeing the corrected shape.

A subsequent optional developer-tool launch request failed with `deviceprocesscontrolservice` code 2. Installation is verified by device metadata, but opening the app and performing a real Pencil stroke after this update were not verified by that request. No additional device settings were changed.

No physical Pencil/S Pen latency measurement or exact recorded user stroke was available. Automated results do not establish that every hand-drawn shape will be recognized. The earlier persistent pen-input interruption and separate long-session latency reports remain unproven and are not claimed fixed by 0.6.2.
