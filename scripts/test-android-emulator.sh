#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
collect() {
  mkdir -p "$root/artifacts/local/android-cloud"
  timeout 15s adb pull /sdcard/Android/data/com.deskink.android/files/previews "$root/artifacts/local/android-cloud/" || true
  timeout 15s adb logcat -d > "$root/artifacts/local/android-cloud/logcat.txt" || true
}
trap collect EXIT
cd "$root/android"
./gradlew :app:connectedDebugAndroidTest :app:assembleRelease --console=plain
