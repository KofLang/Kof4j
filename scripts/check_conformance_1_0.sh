#!/usr/bin/env bash
# check_conformance_1_0.sh — the FORMAL CONFORMANCE GATE for the 1.0 surface
# (the exit-gate checklist's two measurable boxes: "golden byte parity where
# the contract requires it" and "100% of the items ... resolved, with full
# cross-target parity PROVEN (D-1.0-STABILITY-100)").
#
# THE CONTRACT LAYER (measures, never re-proves): the parity proofs already
# exist — `ConformanceMatrixTest` locks every matrix case (15 @Test families,
# 105 named cases), `tests/run-golden.sh` runs 90 cases × 4 targets, the
# conformance matrix (docs/bugs-and-gaps/conformance-matrix.md) records every
# cell with its known-bugs refs. What was MISSING is the gate that JOINS them:
#
#   1. ENUMERATE — every matrix row's Case column must name a case.
#   2. LOCKED   — every named case must be locked by `matrix("<name>")` in
#                 ConformanceMatrixTest (no dangling DONE cell).
#   3. RESIDUAL — every PARTIAL/UNSUPPORTED cell must carry a known-bugs or
#                 decision ref (never a bare residual: R6 named-or-nothing).
#   4. COVERAGE — the accounting printed: surface items / locked / residuals
#                 with refs / the golden-suite pointer.
#
# Fail-closed: a dangling DONE (a case named in the matrix with no locking
# test) or a residual cell with NO named reference fails rc=1 — the 1.0
# exit-gate checklist cannot be ticked on vibes.
#
# Usage:
#   scripts/check_conformance_1_0.sh              # real audit
#   scripts/check_conformance_1_0.sh --selftest   # planted fixtures (offline, has teeth)
#
# Exit codes: 0 conformant; 1 a dangling case or a bare residual (or the
#             matrix/lock files missing); 2 selftest failure.
set -uo pipefail
REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
MATRIX="$REPO_DIR/docs/bugs-and-gaps/conformance-matrix.md"
LOCK="kof-compiler/src/test/java/dev/kof/compiler/ConformanceMatrixTest.java"
KNOWN="$REPO_DIR/docs/bugs-and-gaps/known-bugs.md"
DECISIONS="$REPO_DIR/docs/development/DECISIONS.md"
DEFAULT_MATRIX="$MATRIX"; DEFAULT_LOCK="$LOCK"
DEFAULT_KNOWN="$KNOWN"; DEFAULT_DECISIONS="$DECISIONS"

# matrix_rows <matrix-file> -> prints "caseName|rowLineNo" for every row whose
# last column names a case (the `case` backticked name). The rows END in `|`,
# so awk's $NF is the EMPTY field after the final pipe — the case name is
# $(NF-1). The name grammar `[a-z][a-z_0-9]*` filters the header/separator
# rows (their $(NF-1) is prose like "Case (ConformanceMatrixTest)" or "---").
matrix_rows() {
    grep -E "^\| " "$1" \
        | awk -F'|' '{ n=$(NF-1); gsub(/[` ]/, "", n); if (n ~ /^[a-z][a-z_0-9]*$/) print n "|" NR }'
}

# locked_cases <test-file> -> every `matrix("<name>")` first argument
locked_cases() {
    grep -oE 'matrix\("[a-z_0-9]+"' "$1" | sed 's/matrix("//;s/"$//' | sort -u
}

# residual_lines <matrix-file> -> the row lines carrying PARTIAL/UNSUPPORTED
residual_lines() {
    grep -nE "\| *(DONE|PARTIAL|UNSUPPORTED) " "$1" \
        | grep -E "PARTIAL|UNSUPPORTED" | cut -d: -f1
}

evaluate() {
    # The env overrides resolve HERE per-invocation from the DEFAULT repo
    # paths (saved once at script load — the selftest plants different
    # fixtures per call, so the fallback can never be a previous fixture).
    MATRIX="${MATRIX_FILE:-$DEFAULT_MATRIX}"
    LOCK="${LOCK_FILE:-$DEFAULT_LOCK}"
    KNOWN="${KNOWN_FILE:-$DEFAULT_KNOWN}"
    DECISIONS="${DECISIONS_FILE:-$DEFAULT_DECISIONS}"
    local fail=0
    local locked done_cases dangling residuals bare_ref total_rows

    [ -f "$MATRIX" ] || { echo "conformance-1.0: FAIL — a matriz de conformidade ausente: $MATRIX"; return 1; }
    [ -f "$LOCK" ] || { echo "conformance-1.0: FAIL — o cadeado ausente: $LOCK"; return 1; }

    # 1+2. enumerate + cross-check against the locking test
    matrix_rows "$MATRIX" | cut -d'|' -f1 | sort -u > /tmp/conf10-matrix-cases.$$
    locked_cases "$LOCK" > /tmp/conf10-locked.$$
    done_cases=$(wc -l < /tmp/conf10-matrix-cases.$$)
    locked=$(wc -l < /tmp/conf10-locked.$$)
    dangling=$(comm -23 /tmp/conf10-matrix-cases.$$ /tmp/conf10-locked.$$ | wc -l)
    if [ "$dangling" -ne 0 ]; then
        echo "conformance-1.0: FAIL — $dangling caso(s) na matriz SEM teste que os prenda (DONE solto):"
        comm -23 /tmp/conf10-matrix-cases.$$ /tmp/conf10-locked.$$ | sed 's/^/  - /'
        fail=1
    fi
    rm -f /tmp/conf10-matrix-cases.$$ /tmp/conf10-locked.$$

    # 3. residuals must carry a named ref (a known-bugs id, a decision id, or
    #    an explicit refusal id like JSN004/D-NET-JS-V1 — R6 named-or-nothing)
    residual=$(residual_lines "$MATRIX")
    residuals=$(echo "$residual" | grep -c . )
    [ -n "$residual" ] || residuals=0
    bare_ref=0
    for ln in $residual; do
        [ -n "$ln" ] || continue
        row=$(sed -n "${ln}p" "$MATRIX")
        if ! echo "$row" | grep -qE "bug [0-9]+|[A-Z]{2,5}[0-9]{2,4}|D-[A-Z0-9-]+|D[0-9](-[AB])*|§[0-9]+|✅"; then
            echo "conformance-1.0: FAIL — célula residual (linha $ln) SEM ref nomeada (R6 named-or-nothing):"
            echo "  $(echo "$row" | cut -c1-140)"
            bare_ref=$((bare_ref + 1))
            fail=1
        fi
    done

    # 4. the coverage accounting (printed every time — the accounting IS the deliverable)
    total_rows=$(grep -cE "^\| " "$MATRIX")
    echo "conformance-1.0: cobertura — $done_cases caso(s) nomeado(s) na matriz, $locked preso(s) por ConformanceMatrixTest, $dangling solto(s); $residual linha(s) residual(is) PARTIAL/UNSUPPORTED ($bare_ref sem ref); $total_rows linhas de tabela no total"
    echo "conformance-1.0: paridade golden — tests/run-golden.sh (90 casos × jvm/native/js/script; o veredito executado vive no log carimbado da suíte; os 348 PASS medidos em 14/11 no registro)"
    echo "conformance-1.0: alvos fora das células — android (empacotamento JVM; AndroidInteropE2ETest) e wasm (subset escalar TIER 15.2) — as razões nomeadas na matriz"
    echo "conformance-1.0: D-1.0-STABILITY-100 — os três ledgers (development/, development/future/, bugs-and-gaps/) são resolvidos item a item fora deste gate (cada residual é uma linha viva dos ledgers); este gate prende a CAMADA DE PARIDADE da superfície"

    if [ "$fail" -ne 0 ]; then
        echo "conformance-1.0: FALHOU"
        return 1
    fi
    echo "conformance-1.0: OK — a superfície 1.0 enumerada, cada caso nomeado preso por teste, cada residual com ref nomeada (a camada de paridade da exit gate §8)"
    return 0
}

selftest() {
    local fail=0 out
    local ST; ST="$(mktemp -d)"
    local M="$ST/matrix.md"; local L="$ST/ConformanceMatrixTest.java"

    # 1) all green: every case locked, residuals referenced -> rc 0
    cat > "$M" <<'EOF'
| Feature | Expected | JVM | Native | Script | KofJS | Case (ConformanceMatrixTest) |
| int arithmetic | `-1` | DONE | DONE | DONE | DONE | `arith` |
| json decode map | `2` | DONE | PARTIAL (**JSN004**: refuses) | DONE | DONE | `jsonmap` |
EOF
    cat > "$L" <<'EOF'
class ConformanceMatrixTest {
    void a() { matrix("arith", "", ""); }
    void b() { matrix("jsonmap", "", ""); }
}
EOF
    out="$(MATRIX_FILE="$M" LOCK_FILE="$L" KNOWN_FILE="$ST/kb.md" DECISIONS_FILE="$ST/dec.md" evaluate 2>&1)"; local rc1=$?
    printf '%s\n' "$out" | grep -q "conformance-1.0: OK" || { echo "selftest: green case not OK"; echo "$out"; fail=1; }
    [ "$rc1" -eq 0 ] || { echo "selftest: green case rc expected 0, got $rc1"; fail=1; }

    # 2) a dangling DONE (a matrix case with no locking test) -> rc 1
    cat > "$L" <<'EOF'
class ConformanceMatrixTest {
    void a() { matrix("arith", "", ""); }
}
EOF
    out="$(MATRIX_FILE="$M" LOCK_FILE="$L" KNOWN_FILE="$ST/kb.md" DECISIONS_FILE="$ST/dec.md" evaluate 2>&1)"; local rc2=$?
    printf '%s\n' "$out" | grep -q "SEM teste que os prenda" || { echo "selftest: dangling not caught"; echo "$out"; fail=1; }
    [ "$rc2" -eq 1 ] || { echo "selftest: dangling rc expected 1, got $rc2"; fail=1; }

    # 3) a residual with NO named ref -> rc 1
    cat > "$M" <<'EOF'
| Feature | Expected | JVM | Native | Script | KofJS | Case (ConformanceMatrixTest) |
| int arithmetic | `-1` | DONE | DONE | DONE | DONE | `arith` |
| json decode map | `2` | DONE | PARTIAL (some divergence) | DONE | DONE | `jsonmap` |
EOF
    cat > "$L" <<'EOF'
class ConformanceMatrixTest {
    void a() { matrix("arith", "", ""); }
    void b() { matrix("jsonmap", "", ""); }
}
EOF
    out="$(MATRIX_FILE="$M" LOCK_FILE="$L" KNOWN_FILE="$ST/kb.md" DECISIONS_FILE="$ST/dec.md" evaluate 2>&1)"; local rc3=$?
    printf '%s\n' "$out" | grep -q "SEM ref nomeada" || { echo "selftest: bare residual not caught"; echo "$out"; fail=1; }
    [ "$rc3" -eq 1 ] || { echo "selftest: bare residual rc expected 1, got $rc3"; fail=1; }

    # 4) a missing matrix -> rc 1 (fail-closed)
    out="$(MATRIX_FILE="$ST/absent.md" LOCK_FILE="$L" evaluate 2>&1)"; local rc4=$?
    printf '%s\n' "$out" | grep -q "ausente" || { echo "selftest: missing matrix not caught"; echo "$out"; fail=1; }
    [ "$rc4" -eq 1 ] || { echo "selftest: missing matrix rc expected 1, got $rc4"; fail=1; }

    rm -rf "$ST"
    if [ "$fail" -eq 0 ]; then
        echo "selftest: OK (4 verdict cases: green, dangling DONE, bare residual, missing matrix)"
        return 0
    fi
    return 2
}

case "${1:-}" in
    --selftest) selftest; exit $? ;;
    "") evaluate; exit $? ;;
    *) echo "usage: $0 [--selftest]"; exit 2 ;;
esac
