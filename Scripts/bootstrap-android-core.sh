#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUTPUT="$ROOT/Android/app/libs/libbox.aar"
mkdir -p "$ROOT/Android/app/libs"
if [ "${AURA_USE_CACHED_ANDROID_CORE:-0}" = 1 ] && [ -f "$OUTPUT" ]; then
  echo 'Using the versioned Android core cache.'
  exit 0
fi
CORE="$ROOT/Vendor/sing-box-android"
if [ ! -d "$CORE/.git" ]; then
  git clone --depth 1 --branch v1.11.15 https://github.com/SagerNet/sing-box.git "$CORE"
fi
cd "$CORE"
test "$(git describe --tags --exact-match)" = v1.11.15
python3 "$ROOT/CorePatch/apply.py" "$CORE"
go test ./common/dialer -run TestAura -count=1
go install github.com/sagernet/gomobile/cmd/gomobile@v0.1.4
go install github.com/sagernet/gomobile/cmd/gobind@v0.1.4
go run ./cmd/internal/build_libbox -target android -platform android/arm64,android/arm,android/amd64
cp libbox.aar "$OUTPUT"
echo 'Built Android Libbox: arm64-v8a, armeabi-v7a, x86_64.'
