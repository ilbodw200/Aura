#!/bin/bash
set -euo pipefail
# Run on a Mac with Xcode and Go. Do not use an unpinned binary from a mirror.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
command -v xcodebuild >/dev/null || { echo 'Xcode is required.' >&2; exit 1; }
command -v go >/dev/null || { echo 'Go is required: brew install go' >&2; exit 1; }
command -v xcodegen >/dev/null || { echo 'XcodeGen is required: brew install xcodegen' >&2; exit 1; }
mkdir -p "$ROOT/Vendor"
CORE="$ROOT/Vendor/sing-box"
if [ ! -d "$CORE/.git" ]; then
  git clone --depth 1 --branch v1.11.15 https://github.com/SagerNet/sing-box.git "$CORE"
fi
cd "$CORE"
test "$(git describe --tags --exact-match)" = v1.11.15 || { echo 'Unexpected core checkout.' >&2; exit 1; }
go install github.com/sagernet/gomobile/cmd/gomobile@v0.1.4
go install github.com/sagernet/gomobile/cmd/gobind@v0.1.4
go run ./cmd/internal/build_libbox -target apple -platform ios,iossimulator
ditto "$CORE/Libbox.xcframework" "$ROOT/Vendor/Libbox.xcframework"
cd "$ROOT"
xcodegen generate --spec project-core.yml
echo 'Core project generated. Open Aura.xcodeproj, set your team and App Group, then build on an iPhone.'
