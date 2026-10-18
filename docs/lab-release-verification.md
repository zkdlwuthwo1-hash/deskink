# Lab preview verification — 2026-10-18

Release: `v0.3.0-lab.1`. This records packaging checks, not an end-to-end latency measurement.

- Windows release build completed for the host and native launcher. Streaming and ink rendering code are unchanged from the previously verified performance fix.
- Extracted the ZIP into a separate path containing spaces and Korean characters. Removed development paths and SDK variables from the launch environment.
- The interactive first-run helper downloaded the pinned publisher archive, verified SHA-256, extracted the seven runtime DLLs and retained its license. No video DLLs were bundled in the ZIP.
- `start-portable.ps1 -CheckOnly` loaded the packaged executable and enumerated the session's displays successfully. A cold start opened the packaged host; the process stayed responsive. Repeated launches and selection of monitor indices 1 and 0 succeeded. The visible-console diagnostic recorded zero console windows.
- `DeskInk-Shortcuts.exe` created both desktop shortcuts with the expected executable and monitor argument. Test-created shortcuts were removed after inspection; the user's pre-existing shortcuts were not replaced.
- The user reported working remotely with physical monitors possibly powered off. The checks above concern executable loading and session-level commands, not a visual check on powered physical displays. No new Pencil latency or physical monitor smoothness measurement is claimed.
- [iPad build 13](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39135010732), source `fadd258`, passed 164 unit tests and two UI tests and produced the unsigned device IPA. ZIP integrity, bundle identifier, build number, device platform and embedded upstream notice were checked locally. Installing build 13 on a physical iPad remains unverified.
- The standalone HTML was checked in Chromium at 1280×900 and 390×844. Both images loaded; no horizontal overflow or page JavaScript errors occurred. Image expansion, Escape-to-close and troubleshooting disclosure controls worked. Developer Mode and tool-description layouts were visually inspected at both sizes.

The package uses an explicit allowlist and does not include local logs, pairing data, Apple signing material or development tools. Rust notices come from locked package sources, supplemented by pinned upstream notices where the published crate omits them. Downloads are listed in `SHA256SUMS.txt` in the release.

Remaining real-device checks: installation/renewal with each tester's Apple account, their Windows/GPU/iPad combination, Pencil latency and the Zoom recipient's screen.
