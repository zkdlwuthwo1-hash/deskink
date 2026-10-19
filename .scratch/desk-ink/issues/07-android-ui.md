# Android workspace design parity

Status: in-progress
Blocked by: none
Review baseline: `959311aa725d69fc115496b3f0810b2ce80525a3`

The user asked to improve the Galaxy Tab UI to the quality of the existing iPad app. Keep native Android Views and the existing USB, video, S Pen and zoom behavior.

## Result to deliver

- A compact rounded drawing dock with consistent line icons, short labels, a clear selected tool, visible current color and line width, and accessible 48dp or larger targets.
- A separate compact connection header and fit control. Controls stay outside the drawing viewport in landscape, portrait and narrow windows; smaller windows can scroll the dock.
- A styled USB welcome screen, six-digit pairing form, color swatches and width previews. Korean and English labels remain available.
- Tool selection reflects momentary S Pen erasing and returns to the previous tool on release. Visibility and history controls reflect the actual drawing state.
- No continuous decorative animation, blur, WebView or change to the transport/codec hot path.
- An update APK signed by the existing release identity, updated public download and actual emulator screenshots in the guide.

## Verification

Use the previously approved `docs/android-plan.md` seams: native toolbar interactions, stylus events, tablet-only zoom and session integration. Add behavior regressions one at a time, red before green; do not test decorative constants. Run existing core/app tests and Android lint. Capture actual Android UI for welcome, pairing, connected workspace and narrow/portrait layouts. Real Galaxy Tab hardware latency and S Pen feel remain a physical-device check.

Skills: `tdd` at the approved seams; `code-review` for independent standards/spec review against the baseline above. The previously requested `design-taste-frontend-v1` informs visual consistency only; its web stack is unsuitable for this native Android app. Use the iPad toolbar as the visual reference and prioritize low latency over decorative motion.
