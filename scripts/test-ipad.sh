#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
command -v xcodegen >/dev/null || { echo 'XcodeGen is required: brew install xcodegen' >&2; exit 1; }
xcodebuild -version
cd "$project_root/ios"
xcodegen generate --spec project.yml
# Prefer an explicit simulator destination, otherwise use an already installed
# iPad simulator. Do not download runtimes or change signing settings silently.
destination="${DESKINK_TEST_DESTINATION:-}"
if [[ -z "$destination" ]]; then
  simulator_id="$(xcrun simctl list devices available | sed -En '/iPad/s/.*\(([[:xdigit:]-]{36})\).*/\1/p' | sed -n '1p')"
  [[ -n "$simulator_id" ]] || { echo 'Install an iPad simulator runtime in Xcode, then rerun this script.' >&2; exit 1; }
  destination="platform=iOS Simulator,id=$simulator_id"
fi
xcodebuild -project DeskInk.xcodeproj -scheme EternalMonitor \
  -configuration Debug -destination "$destination" \
  -derivedDataPath "$project_root/DerivedData" \
  "-only-testing:${DESKINK_TEST_FILTER:-EternalMonitorTests}" CODE_SIGNING_ALLOWED=NO test
