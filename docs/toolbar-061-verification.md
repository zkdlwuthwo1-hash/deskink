# 0.6.1 tablet toolbar and shape-hold verification

Scope: direct eraser pointer-size selection, pen-only Width, left-hand color swatches, an always-visible blue on/off shape button, and reliable one-second shape holds. Review baseline: `92f9ec4`; mobile changes through `93c87b1`, native fixture corrections through `829ecd7`. Windows remains 0.6.0.

## Diagnosis and regression evidence

The reported iPad was already on 0.6.0 build 17. Its installed app metadata was checked; this was not explained away as an old tablet version.

Both native input paths recorded sub-pixel samples during a stationary hold. Those samples added travel distance and could make the shape recognizer reject a straight line. A temporary Android probe established that the one-second deadline fired with the feature enabled. The fix recognizes only the stroke prefix through the last significant movement, using the existing three-screen-pixel hold threshold. All raw ink samples still render and transmit immediately. Recognition still runs only at the deadline; no network, encoder or Windows capture path changed. Temporary probes were removed.

- Android actual MotionEvent red: a straight stroke followed by 120 alternating 0.4-pixel samples failed to correct. The same input test passed after the prefix fix.
- iPad red: [run 39906615500](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39906615500), `testStationaryPenCorrectsALineDespiteSubPixelHoldSamples`, expected a line and got no shape.
- iPad UI red: [run 39905727353](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39905727353), tapping Eraser did not expose the three sizes.
- iPad green: [run 39907447241](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39907447241), **206 unit tests and four native UI tests passed**. This also verifies width-only pen choices, remembered eraser size, twelve colors, the same hittable shape button in both states, and pinch/pan/fit without ink.
- Android view regression explicitly delivers the dialog's focus-loss event, selects Large, dismisses the picker, and sends the next stylus contact. It checks an `erase_begin` with radius 48 and restoration of Pen on lift. The focused local test passed.
- Android full green: [run 39911219369](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39911219369), unit tests and lint passed, followed by **five native video/input/UI tests and two compact-window UI tests**. The real-socket fixture verified a shape command, lasso movement, the visible off-state shape button, direct eraser selection, and the next `erase_begin` with radius 48.

During native test validation, one compilation error was corrected. The next run showed the three-size dialog in its actual capture, then failed to find it after the test host stopped sending packets for five seconds. The test peer now sends periodic real protocol heartbeats while screenshots and user interactions run. Follow-up review caught a missing eight-byte timestamp in the fixture heartbeat and unbounded command-wait loops. Both were corrected in `829ecd7`: the heartbeat contains the timestamp followed by the configuration, and clear/shape/erase waits have monotonic deadlines and explicit failure assertions. The application's disconnect timeout was not changed.

## Standards

The independent documented-standards review found **0 documented violations and 0 material code smells**. It identified a lifecycle interaction in the new Android picker: opening a dialog cancels the canvas's momentary eraser state. That finding was corrected by arming the eraser after dismissal while connected. General focus-loss cancellation remains in place for pen-button safety. Both reviewers also checked the two subsequent native-fixture corrections described above and found those issues resolved, with no remaining blocker within that scope.

## Spec

The independent requirements review found the same Android focus issue; it was one shared finding, not two independent defects. The native regression now checks the actual next erase command, in addition to the picker labels. The iPad captured screens were visually reviewed: three size circles, left-hand swatches with selected checkmarks, and a visible ordinary-color icon when shape correction is off.

## Delivery and limits

The connected physical iPad was updated through iloader. Its installation completed and its app metadata separately confirmed **0.6.1 build 18**. The existing Windows installation had already been updated to 0.6.0 and all 564 managed file hashes verified.

The HTML guide uses unmodified new iPad UI captures with fixture captions. Image decoding, enlargement, release-link versions and absence of horizontal overflow passed at 1440, 820, 390 and 320px widths. Desktop and mobile captures were visually inspected.

Physical Pencil/S Pen feel and the exact hand-drawn shape that triggered the report remain unmeasured. These regressions establish the stationary-sample defect and tested tool behavior; they do not prove every shape will be recognized or promise zero latency. The separate long-session latency and Galaxy monitor-switch report remains outside this fix.
