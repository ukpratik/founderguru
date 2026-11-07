#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# F-Droid gate (LLD §8, US-013): no Google Play services, Firebase or Play libraries in the release APK.
# Usage: scripts/check-no-google.sh <apk>
set -euo pipefail
APK="${1:?path to .apk}"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
DEXDUMP=$(ls -d "$SDK"/build-tools/*/dexdump | sort -V | tail -1)
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
unzip -q -o "$APK" 'classes*.dex' -d "$WORK"
hits=0
for dex in "$WORK"/classes*.dex; do
  n=$("$DEXDUMP" "$dex" | grep -cE "Class descriptor *: 'Lcom/google/(android/gms|firebase|android/play)/" || true)
  hits=$((hits + n))
done
echo "Google proprietary classes: $hits"
(( hits == 0 )) || { echo "FAIL: proprietary Google classes found" >&2; exit 1; }
