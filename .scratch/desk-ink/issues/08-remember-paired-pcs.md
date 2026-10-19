# Remember paired PCs

Status: in-progress
Blocked by: none
Review baseline: `a59543395dea7a47035f9a8237b144be8b7d6252`

The user expects a tablet to remember each of their two Windows PCs after one registration, including PC/app restarts. The six-digit code is for first registration or explicit forgetting, not every use.

Approved seams remain `docs/verification-plan.md` (connection/authentication and restart persistence) and `docs/android-plan.md` (Windows pairing and two-PC isolation).

- Persist per-tablet trust on Windows using the current user's encrypted storage, outside packages and the repository. Keep initial code pairing and failure rate limits.
- Retain two PC credentials independently on iPad and Android. Try saved credentials before prompting; a credential rejected by one PC must not delete the other PC's registration.
- Keep the per-run token used by existing ink-document isolation. Store trust across host restarts while issuing a fresh run token; replace only the successfully matched PC credential on the tablet. Retain the last presented credential on the PC until the new one is acknowledged, so a lost ACK or tablet save cannot force re-pairing.
- Keep ink clearing when Windows closes, independent of persistent registration. An optional `DIPC` + 16-byte PC identity tail in HELLO_ACK identifies a PC even after explicit forgetting. Legacy clients ignore the tail and legacy ACKs default to no identity. Only an accepted handshake may replace a saved PC record.
- Provide an explicit forget-registration action, update signed APK/unsigned IPA/Windows ZIP and the existing guide/download addresses, then verify public files.

Initial regression: `cargo test -p eternal-host --test remembered_pairing -- --nocapture` fails deterministically when a registered tablet presents its saved credential after `SharedControl` is recreated. The first build took four minutes; the cached command takes under two seconds. Current Windows startup has no durable trust store; the iPad USB store removes other entries at the shared `USB` address.

TDD evidence:
- Windows: the full HELLO/ACK restart regression failed with Unauthorized, then passed with the DPAPI trust store. Four focused tests and the standard Windows suite pass, including wrong-PC/device rejection, forgetting registration, damaged storage and lost replacement ACKs.
- Android: the real socket two-PC regression failed because rotating PC A displaced PC B, then passed after replacing only the presented credential. App recreation and rate-limited candidate selection also pass. Core/app tests, lint and release assembly pass locally.
- iPad: [native red run 39198132471](https://github.com/zkdlwuthwo1-hash/deskink/actions/runs/39198132471) reproduced one saved PC instead of two. The fix stores both credentials together, migrates the old record and tries both before prompting; full native tests/build are pending in run 39198915553.
- Native Android integration is pending in run 39198585547. Release packaging/public verification remain pending. Physical tablet reconnection has not been observed.

Independent Standards and Spec review both found the same two edge cases: code re-registration could evict the other PC, and Android could promote a credential before storage succeeded. The real-socket re-registration test reproduced the former, then passed with stable PC identity. Android now atomically writes a private app file on its USB reader thread and promotes candidates only after success; it migrates the old preferences without relying on their in-memory write cache. iPad stores both PC identities and credentials in one Keychain item. The original no-wire-change assumption was revised because a rotating credential cannot identify an explicitly forgotten PC.
