# Half-second shape hold

Status: in-progress
Blocked by: none
Baseline: 95c175d (tablet 0.6.2)

The user requested approximately 0.5 seconds instead of one second before shape correction. Change both tablet platforms consistently. The corrected shape must appear while the pen remains down, before lifting. Retain the existing eight-screen-point/dp tremor allowance, movement-triggered timer reset, early-lift cancellation, recognition geometry and ordinary live ink delivery.

Use the already approved actual input/document/transmission seam in `docs/verification-plan.md`. Adjust the deterministic Android input test to assert ordinary ink at 499 ms and correction at 500 ms, then observe it fail on the existing one-second implementation before changing the deadline. Verify iPad's actual input path with the new deadline in macOS CI. No Windows or transport change is required.

Deliver tablet 0.6.3, update the connected iPad when available, and publish signed Android / unsigned iPad assets plus updated English/Korean/HTML instructions. Advance the Android update feed only after public asset verification.
