#!/usr/bin/env bash
# FFI inventory gate (memory-safety/native-cross lane, 03/10): every diagnostic
# code `FFIxxx` emitted by the compiler (a string literal in
# kof-compiler/src/main/java/) must appear in BOTH:
#   (a) the normative doc `docs/ffi-abi-structs.md` (+ PT) — the user-facing
#       contract (R6: every gap code has a documented meaning); and
#   (b) at least one test under `kof-compiler/src/test/java/` — the pin that
#       the diagnostic actually fires (no silent code, no untested contract).
# Reverse coverage: every `FFIxxx` referenced in the doc or in a test must be
# emitted in the source. An orphan code is either invented (doc without emit)
# or dead (emit without doc), both are drift.
# Modeled on scripts/check_matrix_inventory.sh (phase-6 unit 4, 02/10).
#
# Uso: scripts/check_ffi_inventory.sh            # rc!=0 em violação
#      scripts/check_ffi_inventory.sh --selftest # fixture plantada falha como esperado
set -u
cd "$(dirname "$0")/.."

SELF=0
[ "${1:-}" = "--selftest" ] && SELF=1

SRC_DIR=kof-compiler/src/main/java
TEST_DIR=kof-compiler/src/test/java
DOC_EN=docs/ffi-abi-structs.md
DOC_PT=docs/ffi-abi-structs.pt_BR.md

if [ "$SELF" = 1 ]; then
  src=$(mktemp); testf=$(mktemp); doc=$(mktemp); docpt=$(mktemp)
  # planted case: emitted FFI900 has NO doc row; FFI901 in doc but never emitted
  printf 'x="FFI900"; y="FFI901"; z="FFI001"\n' > "$src"
  printf 'assert("FFI001"); assert("FFI002"); assert("FFI902")\n' > "$testf"
  printf 'FFI001 FFI002 FFI901\n' > "$doc"
  printf 'FFI001 FFI002 FFI901\n' > "$docpt"
  codes=$(grep -rhoE '"FFI[0-9]{3}"' "$src" | tr -d '"' | sort -u)
  ok=1
  for c in $codes; do
    grep -qw "$c" "$doc" || { echo "selftest-a: $c emitted but NO $DOC_EN row"; ok=0; }
    grep -qw "$c" "$docpt" || { echo "selftest-a: $c emitted but NO $DOC_PT row"; ok=0; }
    grep -qw "$c" "$testf" || { echo "selftest-a: $c emitted but NO test pin"; ok=0; }
  done
  for c in $(grep -oE 'FFI[0-9]{3}' "$doc" | sort -u); do
    grep -q "\"$c\"" "$src" || { echo "selftest-a: $c in $DOC_EN but NEVER emitted"; ok=0; }
  done
  for c in $(grep -oE 'FFI[0-9]{3}' "$testf" | sort -u); do
    grep -q "\"$c\"" "$src" || { echo "selftest-a: $c pinned by a test but NEVER emitted"; ok=0; }
  done
  [ "$ok" = 1 ] && { echo "selftest FAILED to catch planted drift"; rm -f "$src" "$testf" "$doc" "$docpt"; exit 1; }
  echo "selftest-a: caught planted drift as expected (missing doc row / missing pin / orphan code)"
  rm -f "$src" "$testf" "$doc" "$docpt"
  exit 0
fi

ok=1
codes=$(grep -rhoE '"FFI[0-9]{3}"' "$SRC_DIR" 2>/dev/null | tr -d '"' | sort -u)
if [ -z "$codes" ]; then
  echo "FFI-INVENTORY: FAIL — no FFIxxx literal found in $SRC_DIR (grep pattern broken?)"
  exit 1
fi

# forward: every emitted code has a doc row (EN+PT) AND a test pin
for c in $codes; do
  [ -f "$DOC_EN" ] || { echo "FFI-INVENTORY: FAIL — $DOC_EN missing"; ok=0; continue; }
  [ -f "$DOC_PT" ] || { echo "FFI-INVENTORY: FAIL — $DOC_PT missing"; ok=0; continue; }
  grep -qw "$c" "$DOC_EN" || { echo "FFI-INVENTORY: FAIL — $c emitted in $SRC_DIR but NO row in $DOC_EN"; ok=0; }
  grep -qw "$c" "$DOC_PT" || { echo "FFI-INVENTORY: FAIL — $c emitted in $SRC_DIR but NO row in $DOC_PT"; ok=0; }
  grep -rlw "$c" "$TEST_DIR" >/dev/null 2>&1 || { echo "FFI-INVENTORY: FAIL — $c emitted but NOT pinned by any test under $TEST_DIR"; ok=0; }
done

# reverse: every code mentioned in the docs is actually emitted
for doc in "$DOC_EN" "$DOC_PT"; do
  [ -f "$doc" ] || continue
  for c in $(grep -oE 'FFI[0-9]{3}' "$doc" | sort -u); do
    grep -rq "\"$c\"" "$SRC_DIR" || { echo "FFI-INVENTORY: FAIL — $c in $doc but NEVER emitted in $SRC_DIR (stale or invented)"; ok=0; }
  done
done

if [ "$ok" = 1 ]; then
  echo "FFI-INVENTORY: OK — $(echo "$codes" | wc -l) FFIxxx codes emitted, doc'd EN+PT, pinned by tests (no drift)"
  exit 0
fi
exit 1
