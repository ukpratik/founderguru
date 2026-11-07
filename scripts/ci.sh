#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Every CI gate (LLD §8). GitHub Actions runs exactly this script; run it locally before pushing.
set -euo pipefail
cd "$(dirname "$0")/.."

# Android SDK: ANDROID_HOME (CI), else local.properties (git-ignored), else the macOS default.
if [[ -z "${ANDROID_HOME:-}" && ! -f local.properties && -d "$HOME/Library/Android/sdk" ]]; then
  export ANDROID_HOME="$HOME/Library/Android/sdk"
fi

GRADLE=(./gradlew --stacktrace)
APK=app/build/outputs/apk/release/app-release-unsigned.apk
step() { printf '\n==> %s\n' "$*"; }

step "1/7 Unit tests (:core and :tools:feedcheck JVM, :app Robolectric)"
"${GRADLE[@]}" test

step "2/7 Room schema drift (NFR-012): the committed schema must equal the generated one"
# Compares the generated files with the index, so a staged (not yet committed) schema passes locally.
if ! git diff --quiet -- app/schemas || [[ -n "$(git ls-files --others --exclude-standard -- app/schemas)" ]]; then
  git status --short -- app/schemas
  echo "FAIL: app/schemas differs from the generated schema; commit it (and a migration once released)" >&2
  exit 1
fi

step "3/7 Android Lint (release, abortOnError, warnings as errors)"
"${GRADLE[@]}" lintRelease

step "4/7 Release build (R8 + resource shrinking, AboutLibraries strict licence gate)"
"${GRADLE[@]}" assembleRelease

step "5/7 Permission allowlist"
scripts/check-permissions.sh "$APK"

step "6/7 No Google proprietary classes (F-Droid)"
scripts/check-no-google.sh "$APK"

step "7/7 Size gate: release APK under 10 MB (NFR-013)"
scripts/check-size.sh "$APK" 10000000

printf '\nAll gates passed.\n'
