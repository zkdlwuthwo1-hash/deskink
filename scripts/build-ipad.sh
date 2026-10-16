#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
command -v xcodegen >/dev/null || { echo 'XcodeGen is required: brew install xcodegen' >&2; exit 1; }
xcodebuild -version
cd "$project_root/ios"
xcodegen generate --spec project.yml
xcodebuild -project DeskInk.xcodeproj -scheme EternalMonitor \
  -sdk iphonesimulator -configuration Debug \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath "$project_root/DerivedData" \
  CODE_SIGNING_ALLOWED=NO build
