#!/usr/bin/env bash
#
# test-test-hygiene-test.sh — prova local (sem maven, sem suite) do
# `scripts/check_test_hygiene.sh` (ratchet da Fase 2 do `test-architecture-plan`,
# `D-TEST-ARCHITECTURE-GO`). Le uma arvore FAKE de fontes de teste.
#
# RED-first: baseline gravado => OK; divida NOVA (sleep/oversized/dupname fora do
# baseline) => FAIL rc 1; baseline ausente ou sem fonte => rc 3.
#
# Uso: scripts/tests/test-test-hygiene-test.sh   (exit 0 = todos os cenarios passam)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
GATE="scripts/check_test_hygiene.sh"
FAILED=0
pass() { echo "  ok  — $1"; }
fail() { echo "  FAIL— $1"; FAILED=1; }
expect() { [ "$2" = "$3" ] && pass "$1 (rc=$3)" || fail "$1: esperado rc=$2, veio rc=$3"; }

TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
MOD="$TMP/mod"; BASE="$TMP/baseline.txt"
mkdir -p "$MOD/src/test/java/dev/x" "$MOD/src/test/java/dev/y"
cat > "$MOD/src/test/java/dev/x/ATest.java" <<'JAVA'
package dev.x;
class ATest {
    void shared() { Thread.sleep(1); }
    void aOnly() { }
}
JAVA
cat > "$MOD/src/test/java/dev/y/BTest.java" <<'JAVA'
package dev.y;
class BTest {
    void shared() { }
    void bOnly() { }
}
JAVA
BIG="$MOD/src/test/java/dev/y/BigTest.java"
{ echo "package dev.y;"; echo "class BigTest {"; echo "    void big() { }"; for i in $(seq 1 500); do echo "    // pad $i"; done; echo "}"; } > "$BIG"

# ── cenario 1: gravar baseline => rc 0 e 3 chaves (sleep/oversized/dupname) ──
out="$(bash "$GATE" --root "$MOD" --baseline "$BASE" --write-baseline 2>&1)"; rc=$?
expect "gravar baseline" 0 "$rc"
[ -f "$BASE" ] && pass "baseline existe" || fail "baseline nao foi criado"
n="$(grep -vc '^#' "$BASE" 2>/dev/null | tr -d ' ')"; expect "3 chaves congeladas" 3 "$n"
grep -qE '^sleep	.*ATest\.java$' "$BASE" && pass "chave sleep (ATest)" || fail "sem chave sleep"
grep -qE '^oversized	.*BigTest\.java$' "$BASE" && pass "chave oversized (BigTest)" || fail "sem chave oversized"
grep -qE '^dupname	shared$' "$BASE" && pass "chave dupname (shared)" || fail "sem chave dupname"

# ── cenario 2: contra o baseline, sem divida nova => rc 0 ────────────────────
out="$(bash "$GATE" --root "$MOD" --baseline "$BASE" 2>&1)"; rc=$?
expect "sem divida nova" 0 "$rc"
grep -q "TEST-HYGIENE: OK" <<<"$out" && pass "mensagem OK" || fail "sem mensagem OK"

# ── cenario 3: divida NOVA (sleep numa classe nova) => FAIL rc 1 ─────────────
cat > "$MOD/src/test/java/dev/x/NewTest.java" <<'JAVA'
package dev.x;
class NewTest {
    void fresh() { Thread.sleep(5); }
}
JAVA
out="$(bash "$GATE" --root "$MOD" --baseline "$BASE" 2>&1)"; rc=$?
expect "divida nova recusada" 1 "$rc"
grep -qE 'sleep' <<<"$out" && pass "tipo sleep reportado" || fail "tipo sleep ausente"
grep -q "NewTest.java" <<<"$out" && pass "arquivo novo reportado" || fail "arquivo novo nao reportado"
grep -q "shared\|BigTest" <<<"$out" && fail "divida congelada vazou no FAIL" || pass "so a divida nova no relatorio"

# ── cenario 4: regravar baseline => rc 0 (e agora 4 chaves) ──────────────────
bash "$GATE" --root "$MOD" --baseline "$BASE" --write-baseline >/dev/null 2>&1
n="$(grep -vc '^#' "$BASE" | tr -d ' ')"; expect "4 chaves apos regravar" 4 "$n"
out="$(bash "$GATE" --root "$MOD" --baseline "$BASE" 2>&1)"; rc=$?
expect "sem divida nova apos regravar" 0 "$rc"

# ── cenario 5: melhoria (remover a violacao) => rc 0, baseline pode apertar ──
rm -f "$MOD/src/test/java/dev/x/NewTest.java"
out="$(bash "$GATE" --root "$MOD" --baseline "$BASE" 2>&1)"; rc=$?
expect "melhoria nao falha" 0 "$rc"
grep -q "melhoria" <<<"$out" && pass "melhoria contada" || fail "melhoria nao contada"

# ── cenario 6: baseline ausente => rc 3 ─────────────────────────────────────
out="$(bash "$GATE" --root "$MOD" --baseline "$TMP/nope.txt" 2>&1)"; rc=$?
expect "baseline ausente nao certifica" 3 "$rc"
grep -q "baseline ausente" <<<"$out" && pass "diagnostico de baseline ausente" || fail "sem diagnostico"

# ── cenario 7: sem fonte de teste => rc 3 ───────────────────────────────────
mkdir -p "$TMP/empty"
out="$(bash "$GATE" --root "$TMP/empty" --baseline "$BASE" 2>&1)"; rc=$?
expect "arvore sem fontes nao certifica" 3 "$rc"

if [ "$FAILED" = 1 ]; then
  echo "== RESULTADO: FALHOU =="
  exit 1
fi
echo "== RESULTADO: todos os cenarios OK =="
