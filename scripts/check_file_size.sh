#!/usr/bin/env bash
# Enforces the "no god files" rule from AGENTS.md (ADR-9).
set -euo pipefail

LIMIT=${1:-600}
FAIL=0

while IFS= read -r -d '' f; do
  lines=$(wc -l < "$f")
  if [ "$lines" -gt "$LIMIT" ]; then
    echo "::error file=$f::$f has $lines lines (limit $LIMIT). Split it."
    FAIL=1
  fi
done < <(find app/src -type f \( -name '*.kt' -o -name '*.xml' \) -print0)

if [ "$FAIL" -eq 0 ]; then
  echo "File size guard passed: every source file is <= $LIMIT lines."
fi
exit "$FAIL"
