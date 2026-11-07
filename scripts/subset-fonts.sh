#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Rebuilds app/src/main/res/font/*.ttf (design-system §3, §6.1): static instances of the google/fonts variable fonts
# Newsreader and Inter (SIL OFL 1.1, texts in app/fonts/), Latin subset. Needs fonttools (pip install fonttools).
# The fonts are committed and bundled; the app never downloads a font and the build never runs this script.
# Usage: scripts/subset-fonts.sh <dir with Newsreader[opsz,wght].ttf, Newsreader-Italic[opsz,wght].ttf, Inter[opsz,wght].ttf>
# Sources: https://github.com/google/fonts/tree/main/ofl/newsreader and .../ofl/inter
set -euo pipefail
SRC=${1:?usage: $0 <source dir>}
OUT="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/res/font"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
# Basic Latin, Latin-1 (incl. £), General Punctuation, € and ₹ (design-system §3). Other characters use the system font.
U="U+0020-007E,U+00A0-00FF,U+2000-206F,U+20AC,U+20B9"
FEATURES='kern,liga,case,tnum'

subset() { # <instance> <output name>
  fonttools subset "$1" --unicodes="$U" --layout-features="$FEATURES" --name-IDs='*' --output-file="$OUT/$2.ttf"
}

# Newsreader at optical size 16 (design-system §3): Medium, SemiBold, Medium Italic.
for w in 500:medium 600:semibold; do
  fonttools varLib.instancer "$SRC/Newsreader[opsz,wght].ttf" wght="${w%%:*}" opsz=16 -o "$TMP/nr.ttf" -q
  subset "$TMP/nr.ttf" "newsreader_${w##*:}"
done
fonttools varLib.instancer "$SRC/Newsreader-Italic[opsz,wght].ttf" wght=500 opsz=16 -o "$TMP/nri.ttf" -q
subset "$TMP/nri.ttf" newsreader_medium_italic

# Inter at optical size 14 (text sizes): Regular, Medium, SemiBold, Bold.
for w in 400:regular 500:medium 600:semibold 700:bold; do
  fonttools varLib.instancer "$SRC/Inter[opsz,wght].ttf" wght="${w%%:*}" opsz=14 -o "$TMP/in.ttf" -q
  subset "$TMP/in.ttf" "inter_${w##*:}"
done
ls -l "$OUT"
