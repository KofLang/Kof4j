#!/usr/bin/env bash
# doing-prepend.sh — prepend a record to DOING.md / DOING.pt_BR.md SAFELY.
#
# WHY THIS EXISTS (19/11/2026, measured TWICE in the same day): a one-liner
# like `open(f,"w").write(NEW + open(f).read())` TRUNCATES the file before
# the read argument is evaluated — Python evaluates the `open(f,"w")` object
# FIRST (that call truncates), and only then the argument reads the now
# EMPTY file. It cost 5517/3542 lines of DOING history twice.
#
# This script does it in the safe order: READ fully, WRITE fully.
#
# Uso:
#   scripts/doing-prepend.sh DOING.md  /tmp/record-en.md
#   scripts/doing-prepend.sh DOING.pt_BR.md /tmp/record-pt.md
#
# The record file is prepended (its content + the original file).
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

TARGET="${1:?usage: doing-prepend.sh <DOING file> <record file>}"
RECORD="${2:?usage: doing-prepend.sh <DOING file> <record file>}"

[ -f "$TARGET" ] || { echo "doing-prepend: target not found: $TARGET" >&2; exit 1; }
[ -f "$RECORD" ] || { echo "doing-prepend: record not found: $RECORD" >&2; exit 1; }

TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

# READ first (the original content), then compose, then write.
cat "$TARGET" > "$TMP"
cat "$RECORD" "$TMP" > "$TARGET"
rm -f "$TMP"

echo "doing-prepend: $TARGET -> $(wc -l < "$TARGET") lines"
