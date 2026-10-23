# Direct tool pickers and stationary shape recognition

Status: complete
Baseline: 92f9ec4

The user's iPad was verified as DeskInk 0.6.0 build 17 when the issue was diagnosed. Their Windows desktop installation has been updated to 0.6.0 and all 564 managed file hashes verified. Do not attribute this report to an old tablet build. The iPad is now updated to 0.6.1 build 18, confirmed separately through device metadata after iloader completed installation.

Accepted behavior:
- Eraser tap selects the tool and opens its three pointer sizes directly. Pen Width contains only pen widths.
- The iPad color picker shows the actual color to the left of each name, with the selected color indicated.
- Shape correction stays in the toolbar in both states. On is blue; off keeps the ordinary visible icon without a blue background.
- One-second hold must tolerate sub-pixel movement from a stationary pen without delaying ordinary ink.

Evidence:
- iPad UI red: run 39905727353, failure at the assertion that eraser tap exposes sizes.
- Android actual MotionEvent input red: 120 alternating 0.4-pixel samples after a straight stroke prevent correction. Tagged temporary probe confirms the hold timer fired with editing and shape correction enabled.
- Android input green: recognize the path through the last significant movement, keeping all ordinary ink samples and transmission unchanged.
- Android direct eraser picker test red then green. Width no longer contains eraser sizes.
- iPad hold red: run 39906615500. Green: run 39907447241, 206 unit tests and four native UI tests passed; both picker screenshots and the unchanged off-state shape icon visually checked.
- Android direct picker focus regression passed locally: a real next stylus contact emits erase_begin with radius 48, then lifts back to Pen. A native fixture compilation error was fixed, then run 39909582117 exposed a missing fixture heartbeat during screenshots. The capture showed the picker correctly; the test peer went silent long enough for the app's five-second disconnect. The fixture now sends periodic heartbeats like the real host. Review also caught the missing heartbeat timestamp and unbounded command waits; both were corrected in 829ecd7. Full CI run 39911219369 passed unit tests, lint, five native integration tests and two compact-window UI tests.
- Independent Standards and Spec reviews shared one Android dialog-focus finding and two subsequent native-fixture findings; all were fixed. Both reviewers verified the follow-up corrections with no remaining blocker in scope.
- Guide image decoding, enlargement, current links and no horizontal overflow passed at 1440/820/390/320px. Version 0.6.1 is public; all five release assets were downloaded anonymously and matched their local hashes and sizes. The production Android updater fetched the live feed, offered code 7 to code 6, verified the APK download and offered no update to code 7. Windows remains 0.6.0. Both the plain Pages URL and the existing guide=f335f2d URL matched the generated HTML hash.

Physical Pencil latency and the user's exact hand-drawn shape remain unmeasured; the synthetic reproduction proves the stationary-sample defect, not every possible shape recognition failure.
