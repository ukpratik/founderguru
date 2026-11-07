#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# NFR-013 size gate: the release APK must stay under the limit (10 MB).
# Usage: scripts/check-size.sh <apk> [limit-bytes]
set -euo pipefail
APK="${1:?path to .apk}"
LIMIT="${2:-10000000}"
SIZE=$(wc -c < "$APK" | tr -d ' ')
echo "Release APK: $SIZE bytes (limit $LIMIT)"
(( SIZE <= LIMIT )) || { echo "FAIL: APK over the NFR-013 budget" >&2; exit 1; }
echo "OK"
