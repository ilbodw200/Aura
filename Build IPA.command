#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
echo 'AURA: signed iPhone IPA (requires Mac, Xcode 26, Apple Developer Team)'
if [ -z "${AURA_TEAM_ID:-}" ]; then
  read -r -p 'Apple Developer Team ID (10 characters): ' AURA_TEAM_ID
  export AURA_TEAM_ID
fi
echo '1 — Full build with VPN core (requires Go)'
echo '2 — Interface preview without VPN core'
read -r -p 'Choose 1 or 2: ' AURA_VARIANT_CHOICE
case "$AURA_VARIANT_CHOICE" in
  1) bash Scripts/build-ipa.sh signed full ;;
  2) bash Scripts/build-ipa.sh signed preview ;;
  *) echo 'Unknown option.' >&2; exit 2 ;;
esac
read -r -p 'Press Enter to close.' AURA_DONE
