#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Turns a :tools:feedcheck report into GitHub issues (NFR-014): one open issue per BROKEN source, titled
# "Feed health: <id>" and labelled feed-health; a comment when it is still broken; closed when the source is OK again.
# BLOCKED_FROM_CI sources (HTTP 403 and similar) never open an issue and leave any existing one untouched.
# Usage: scripts/feed-health-issues.sh build/feedcheck.json   (needs gh, jq, GH_TOKEN; RUN_URL optional)
set -euo pipefail
REPORT="${1:?path to feedcheck.json}"
RUN_URL="${RUN_URL:-}"
LABEL=feed-health

gh label create "$LABEL" --color D93F0B --description "Weekly feed-health job (NFR-014)" >/dev/null 2>&1 || true

open_issue() { # $1 = source id → issue number or empty
  gh issue list --label "$LABEL" --state open --search "\"Feed health: $1\" in:title" --json number,title \
    --jq ".[] | select(.title == \"Feed health: $1\") | .number" | head -n 1
}

jq -c '.[]' "$REPORT" | while read -r row; do
  id=$(jq -r .id <<<"$row"); name=$(jq -r .name <<<"$row"); status=$(jq -r .status <<<"$row")
  reason=$(jq -r .reason <<<"$row"); attempts=$(jq -r .attempts <<<"$row")
  number=$(open_issue "$id")
  case "$status" in
    BROKEN)
      body="**$name** (\`$id\`) failed the weekly feed check after $attempts attempt(s): $reason.

Before changing anything, confirm on a device (Settings › Sources shows the same status), because CI networks differ from phones. Fix or replace the source in \`app/src/main/assets/sources.json\` in the next release.
${RUN_URL:+
Run: $RUN_URL}"
      if [[ -n "$number" ]]; then
        gh issue comment "$number" --body "Still broken: $reason.${RUN_URL:+ Run: $RUN_URL}"
      else
        gh issue create --title "Feed health: $id" --label "$LABEL" --body "$body"
      fi
      ;;
    OK)
      if [[ -n "$number" ]]; then
        gh issue close "$number" --comment "Recovered: the weekly check passed.${RUN_URL:+ Run: $RUN_URL}"
      fi
      ;;
    BLOCKED_FROM_CI)
      echo "::notice title=Blocked from CI::$id: $reason (no issue opened)"
      ;;
  esac
done
