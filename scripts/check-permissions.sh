#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Privacy gate (LLD §8, NFR-006): fails if a release APK's merged manifest requests a permission outside the allowlist.
# Usage: scripts/check-permissions.sh <apk>...
set -euo pipefail
ALLOWED=(
  android.permission.INTERNET
  android.permission.ACCESS_NETWORK_STATE
  android.permission.POST_NOTIFICATIONS
  android.permission.RECEIVE_BOOT_COMPLETED
  android.permission.WAKE_LOCK
)
# Added by AndroidX core to guard unexported receivers; a signature-level app-private permission, not a user one.
ALLOWED_SUFFIX=".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"

if [[ $# -eq 0 ]]; then
  echo "usage: $0 <apk>..." >&2
  exit 2
fi

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
AAPT2=$(ls -d "$SDK"/build-tools/*/aapt2 | sort -V | tail -1)
status=0
for apk in "$@"; do
  [[ -f "$apk" ]] || { echo "FAIL: no such APK: $apk" >&2; exit 2; }
  echo "Checking $apk"
  while read -r perm; do
    [[ -z "$perm" ]] && continue
    if [[ " ${ALLOWED[*]} " == *" $perm "* || "$perm" == *"$ALLOWED_SUFFIX" ]]; then
      echo "  ok    $perm"
    else
      echo "  BLOCK $perm"; status=1
    fi
  done < <("$AAPT2" dump permissions "$apk" | sed -n "s/^uses-permission: name='\([^']*\)'.*/\1/p")
done
exit $status
