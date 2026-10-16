#!/usr/bin/env bash
set -euo pipefail
[[ "$(uname -s)" == "Darwin" ]] || { echo 'Run this on the GitHub macOS runner (or a Mac), not Windows.' >&2; exit 1; }
project_root="$(cd "$(dirname "$0")/.." && pwd)"
command -v xcodegen >/dev/null || { echo 'XcodeGen is required.' >&2; exit 1; }
output_dir="$project_root/artifacts/local/ipad-cloud"
mkdir -p "$output_dir"
cd "$project_root/ios"
xcodegen generate --spec project.yml
xcodebuild -project DeskInk.xcodeproj -scheme EternalMonitor \
  -configuration Release -sdk iphoneos -destination 'generic/platform=iOS' \
  -derivedDataPath "$project_root/DerivedData-device" \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY='' build

app="$project_root/DerivedData-device/Build/Products/Release-iphoneos/DeskInk.app"
[[ -f "$app/DeskInk" ]] || { echo 'Device app executable is missing.' >&2; exit 1; }
[[ "$(/usr/libexec/PlistBuddy -c 'Print :DTPlatformName' "$app/Info.plist")" == "iphoneos" ]] || {
  echo 'Refusing to package a simulator app as an installable IPA.' >&2; exit 1;
}
xcrun lipo -verify_arch arm64 "$app/DeskInk"

# A fresh staging directory avoids accidentally packaging a previous build.
stage="$(mktemp -d "${TMPDIR:-/tmp}/deskink-ipa.XXXXXX")"
trap 'rm -rf -- "$stage"' EXIT
mkdir "$stage/Payload"
ditto "$app" "$stage/Payload/DeskInk.app"
ditto -c -k --keepParent "$stage/Payload" "$output_dir/DeskInk-unsigned.ipa"
unzip -tq "$output_dir/DeskInk-unsigned.ipa"
(
  cd "$output_dir"
  shasum -a 256 DeskInk-unsigned.ipa > DeskInk-unsigned.ipa.sha256
)
{
  echo 'Unsigned, unprovisioned iPad device build. Sign and install locally on Windows.'
  echo "Source: $(git -C "$project_root" rev-parse HEAD)"
  xcodebuild -version
  xcrun --sdk iphoneos --show-sdk-version
  echo 'Building successfully is not proof of real USB, Pencil latency, or Zoom behavior.'
} > "$output_dir/build-info.txt"
echo "Created $output_dir/DeskInk-unsigned.ipa"
