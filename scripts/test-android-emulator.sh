#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
collect() {
  mkdir -p "$root/artifacts/local/android-cloud"
  timeout 20s adb pull /sdcard/Download/ "$root/artifacts/local/android-cloud/" || true
  timeout 15s adb logcat -d > "$root/artifacts/local/android-cloud/logcat.txt" || true
}
trap collect EXIT
cd "$root/android"
if [[ "${GITHUB_ACTIONS:-}" == "true" ]]; then
  adb shell settings put secure immersive_mode_confirmations confirmed
  adb shell settings put secure show_ime_with_hard_keyboard 1
fi
./gradlew :app:connectedDebugAndroidTest :app:assembleRelease --console=plain
# The Actions runner owns this disposable emulator. Also exercise a small
# landscape window, a large system font and the software keyboard.
if [[ "${GITHUB_ACTIONS:-}" == "true" ]]; then
  mkdir -p "$root/artifacts/local/android-cloud/tablet-results"
  cp -a app/build/outputs/androidTest-results/. "$root/artifacts/local/android-cloud/tablet-results/"
  adb shell wm size 960x600
  adb shell wm density 240
  adb shell settings put system font_scale 1.5
  ./gradlew :app:connectedDebugAndroidTest --console=plain \
    -Pandroid.testInstrumentationRunnerArguments.class=com.deskink.app.WorkspaceUiTest \
    -Pandroid.testInstrumentationRunnerArguments.captureSuffix=-compact
fi
