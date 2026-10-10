#!/usr/bin/env bash
# §11 inventory gate (phase-6 unit 4, 02/10): every MemRule diagnostic code must
# appear in the §11 Safety Matrix rows of docs/spec/memory-safety.md (+PT), with
# EXACTLY ONE documented exception: MEM023 — model rule, no emission surface
# (spec prose: forbidden shape unconstructible; precedent O-03/D-MEMORY-CLEAR).
# A NEW MemRule code without a §11 row fails: the matrix is the fase-1 gate.
# Decision IDs (D-MEM030-BORROW-RUNTIME) are stripped first — they are not diagnostics.
set -u
cd "$(dirname "$0")/.."
RULES=kof-compiler/src/main/java/dev/kof/compiler/memory/MemRule.java
ok=1
codes=$(grep -oP '"MEM\d+"' "$RULES" | tr -d '"' | sort -u)
for spec in docs/spec/memory-safety.md docs/spec/memory-safety.pt_BR.md; do
  rows=$(sed -n '/^## 11\. /,/^## 12\. /p' "$spec" | sed 's/D-MEM[0-9]*//g' | grep -oP 'MEM\d+' | sort -u)
  for c in $codes; do
    case "$c" in
      MEM023) continue ;;  # model rule, no emission face (documented exception)
    esac
    if ! grep -qx "$c" <<<"$rows"; then
      echo "MATRIX-INVENTORY: FAIL — $c in MemRule.java has NO §11 row in $spec"; ok=0
    fi
  done
  for r in $rows; do
    if ! grep -q "\"$r\"" "$RULES"; then
      echo "MATRIX-INVENTORY: FAIL — §11 row $r in $spec is NOT in MemRule.java (stale or invented)"; ok=0
    fi
  done
done
[ "$ok" = 1 ] && echo "MATRIX-INVENTORY: OK — $(echo "$codes" | wc -l) MemRule codes × §11 rows EN+PT (1 documented model-rule exception)"
exit $((1 - ok))
