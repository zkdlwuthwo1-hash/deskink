# Publishing updates

Windows and Android query the public `main/updates/` descriptors. Uploading a release alone does not notify installed apps: publish the matching descriptors after the assets are available.

1. Increase Android `versionCode`/`versionName` and iPad version/build. Run the approved checks and review the changes.
2. Build Windows with `scripts/build-windows.ps1 -Release`, package a clean source commit using `scripts/package-windows.py`, and record its `build-info.json`. Sign Android with `scripts/sign-android.ps1` using the existing protected key; never replace that key. Obtain the unsigned iPad IPA from the macOS workflow.
3. Create a GitHub release with `DeskInk-Windows-x64.zip`, `DeskInk-Android.apk`, `DeskInk-iPad-unsigned.ipa`, `README.html`, and SHA-256 checksums. Verify the downloaded bytes and Android certificate against the local files before announcing the release.
4. Update `updates/windows.json` with the ZIP version, packaged source commit, exact download URL, byte size and SHA-256. Update `updates/android.json` with APK `version_code`, `version`, exact release URL, byte size and SHA-256. The Android URL must be `https://github.com/zkdlwuthwo1-hash/deskink/releases/download/v{version}/DeskInk-Android.apk`.
5. Publish the descriptors and illustrated guides to `main`. Check the public raw descriptors and GitHub Pages. Exercise the production Android `AppUpdate.check`/download path with an older version number, then confirm that the current version is not offered its own update.

Keep release URLs immutable. Do not put an unsigned/debug APK in the Android update descriptor. Both desktop and Android installation remain user-triggered. iPad continues to require iloader with the same Apple account and free-signature renewal; this feed does not update iPad apps.
