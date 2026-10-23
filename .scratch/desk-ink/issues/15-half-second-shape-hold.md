# Half-second shape hold

Status: completed
Blocked by: none
Baseline: 95c175d (tablet 0.6.2)

The user requested approximately 0.5 seconds instead of one second before shape correction. Change both tablet platforms consistently. The corrected shape must appear while the pen remains down, before lifting. Retain the existing eight-screen-point/dp tremor allowance, movement-triggered timer reset, early-lift cancellation, recognition geometry and ordinary live ink delivery.

Use the already approved actual input/document/transmission seam in `docs/verification-plan.md`. Adjust the deterministic Android input test to assert ordinary ink at 499 ms and correction at 500 ms, then observe it fail on the existing one-second implementation before changing the deadline. Verify iPad's actual input path with the new deadline in macOS CI. No Windows or transport change is required.

Deliver tablet 0.6.3, update the connected iPad when available, and publish signed Android / unsigned iPad assets plus updated English/Korean/HTML instructions. Advance the Android update feed only after public asset verification.

`a1d6b57` produced the expected failure at 500 ms. `76040c6` changes only the existing deadlines to 500 ms / 0.5 seconds and the matching hints/version metadata. All 11 local Android canvas tests, lint and release assembly passed after settling initial layout in the subpixel fixture (`d2ec3e2`). iPad CI 39936962075 passed 21 tests and produced the IPA. The connected iPad now reports 0.6.3 build 20 after installation. See `docs/shape-063-verification.md` for subsequent delivery evidence.

Final Android CI 39937534762 passed 77 unit tests (one opt-in interop test skipped), lint/build, five native tablet tests and two compact-window tests. Release v0.6.3 and all five public assets were verified, then the Android update feed advanced to code 9. The production updater check/download passed, and both the plain and previously shared guide URLs match the new HTML. The temporary X: build mapping was removed.
