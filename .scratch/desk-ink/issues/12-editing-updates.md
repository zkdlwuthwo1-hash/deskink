# Shape editing, eraser sizes and in-app updates

Status: ready-for-human
Blocked by: none for implementation/public delivery
Automated status: implementation, approved checks, Standards/Spec review and release verification complete
Human verification: physical tablet latency and Android system-installer confirmation remain pending
Baseline: d03632f

## Accepted scope

- One-second pen-down hold for circle/ellipse/rectangle/line, pen and highlighter, preserved brush, remembered bottom toggle.
- Lasso/tap selection, grouped movement and resizing with corner/side handles, one undo per edit.
- Twelve colors and three remembered eraser sizes.
- Free Windows/Android update path; iPad remains iloader. Existing Android installations need the first updater-enabled APK once.

## Evidence and remaining work

Implementation, red/green regressions and independent Standards/Spec review are recorded in [editing-update-verification.md](../../../docs/editing-update-verification.md). Native Android, iPad 205/205, real Windows-to-Android transport and isolated Windows updater checks passed. Public release 0.6.0 has six byte-verified assets, matching Windows/Android descriptors, and a successful real Android update check/download/hash verification. The real previous Windows ZIP also upgrades successfully in an isolated installation without launching or changing the user's running app.

Physical tablet latency, Android system-install confirmation and the unresolved Galaxy screen-switch report remain human/device checks. Do not mark the monitor bug fixed without a demonstrated cause and regression.
