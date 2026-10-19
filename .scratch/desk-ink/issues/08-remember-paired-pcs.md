# Remember paired PCs

Status: in-progress
Review baseline: `a59543395dea7a47035f9a8237b144be8b7d6252`

The user expects a tablet to remember each of their two Windows PCs after one registration, including PC/app restarts. The six-digit code is for first registration or explicit forgetting, not every use.

Approved seams remain `docs/verification-plan.md` (connection/authentication and restart persistence) and `docs/android-plan.md` (Windows pairing and two-PC isolation).

- Persist per-tablet trust on Windows using the current user's encrypted storage, outside packages and the repository. Keep initial code pairing and failure rate limits.
- Retain two PC credentials independently on iPad and Android. Try saved credentials before prompting; a credential rejected by one PC must not delete the other PC's registration.
- Keep the per-run token used by existing ink-document isolation. Store trust across host restarts while issuing a fresh run token; replace only the successfully matched PC credential on the tablet. Retain the last presented credential on the PC until the new one is acknowledged, so a lost ACK or tablet save cannot force re-pairing.
- Keep ink clearing when Windows closes, independent of persistent registration. No wire-format change is required.
- Provide an explicit forget-registration action, update signed APK/unsigned IPA/Windows ZIP and the existing guide/download addresses, then verify public files.

Initial regression: `cargo test -p eternal-host --test remembered_pairing -- --nocapture` fails deterministically when a registered tablet presents its saved credential after `SharedControl` is recreated. The first build took four minutes; the cached command takes under two seconds. Current Windows startup has no durable trust store; the iPad USB store removes other entries at the shared `USB` address.
