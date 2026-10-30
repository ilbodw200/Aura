#!/bin/bash
# signed: AURA_TEAM_ID=ABCDEFGHIJ bash Scripts/build-ipa.sh signed
# unsigned: bash Scripts/build-ipa.sh unsigned preview
# Second argument: full (default, builds Libbox) or preview (UI without VPN core).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
MODE="${1:-signed}"
VARIANT="${2:-full}"
case "$MODE" in signed|unsigned) ;; *) echo 'Usage: build-ipa.sh signed|unsigned full|preview' >&2; exit 2;; esac
case "$VARIANT" in full|preview) ;; *) echo 'Variant must be full or preview.' >&2; exit 2;; esac
if [ "$(uname -s)" != Darwin ] || ! command -v xcodebuild >/dev/null; then
  echo 'A Mac with full Xcode 26+ is required. This script cannot compile iOS on Windows.' >&2
  exit 1
fi
command -v xcodegen >/dev/null || { echo 'Install XcodeGen: brew install xcodegen' >&2; exit 1; }
TEAM="${AURA_TEAM_ID:-}"
if [ "$MODE" = signed ] && [[ ! "$TEAM" =~ ^[A-Z0-9]{10}$ ]]; then
  echo 'Set AURA_TEAM_ID to the 10-character Apple Developer Team ID. Sign in to Xcode first.' >&2
  exit 2
fi
if [ "$VARIANT" = full ]; then
  bash Scripts/bootstrap-core.sh
else
  bash Scripts/generate.sh
fi
# Unique output directories prevent accidentally returning a stale IPA after failure.
RUN="$(mktemp -d "$ROOT/build-ipa.XXXXXX")"
ARCHIVE="$RUN/Aura.xcarchive"
EXPORT="$RUN/export"
ARGS=(-project "$ROOT/Aura.xcodeproj" -scheme Aura -configuration Release -destination 'generic/platform=iOS' -archivePath "$ARCHIVE")
if [ "$MODE" = signed ]; then
  DEFAULT_BUNDLE="app.aura.preview.$(printf '%s' "$TEAM" | tr '[:upper:]' '[:lower:]')"
  BUNDLE="${AURA_BUNDLE_ID:-$DEFAULT_BUNDLE}"
  APP_GROUP="${AURA_APP_GROUP:-group.$BUNDLE}"
  xcodebuild archive "${ARGS[@]}" -allowProvisioningUpdates "DEVELOPMENT_TEAM=$TEAM" "AURA_BUNDLE_ID=$BUNDLE" "AURA_APP_GROUP=$APP_GROUP"
  /usr/libexec/PlistBuddy -c 'Add :method string debugging' "$RUN/ExportOptions.plist"
  /usr/libexec/PlistBuddy -c 'Add :signingStyle string automatic' "$RUN/ExportOptions.plist"
  /usr/libexec/PlistBuddy -c "Add :teamID string $TEAM" "$RUN/ExportOptions.plist"
  /usr/libexec/PlistBuddy -c 'Add :destination string export' "$RUN/ExportOptions.plist"
  xcodebuild -exportArchive -archivePath "$ARCHIVE" -exportPath "$EXPORT" -exportOptionsPlist "$RUN/ExportOptions.plist" -allowProvisioningUpdates
  test -f "$EXPORT/Aura.ipa" || { echo 'Xcode did not export Aura.ipa.' >&2; exit 1; }
  /usr/bin/codesign --verify --deep --strict "$ARCHIVE/Products/Applications/Aura.app"
  echo "Signed IPA: $EXPORT/Aura.ipa"
  echo 'Install using Xcode or Apple Configurator on a device permitted by the provisioning profile.'
else
  xcodebuild archive "${ARGS[@]}" CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO 'CODE_SIGN_IDENTITY='
  APP="$ARCHIVE/Products/Applications/Aura.app"
  test -f "$APP/Aura" || { echo 'No compiled iPhone app found in archive.' >&2; exit 1; }
  mkdir -p "$EXPORT/Payload"
  ditto "$APP" "$EXPORT/Payload/Aura.app"
  (cd "$EXPORT" && /usr/bin/zip -qry "Aura-$VARIANT-unsigned.ipa" Payload)
  /usr/bin/unzip -t "$EXPORT/Aura-$VARIANT-unsigned.ipa"
  echo "Unsigned IPA: $EXPORT/Aura-$VARIANT-unsigned.ipa"
  echo 'NOT INSTALLABLE AS IS. Signing with profiles that authorize App Groups and Packet Tunnel is required.'
fi
if [ "$VARIANT" = preview ]; then
  echo 'PREVIEW ONLY: this build has no VPN core. The interface can run after signing; proxy tunneling cannot.'
fi
