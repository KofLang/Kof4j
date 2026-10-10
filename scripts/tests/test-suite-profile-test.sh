#!/usr/bin/env bash
#
# test-suite-profile-test.sh — prova local (sem maven, sem suite) do
# `scripts/test-suite-profile.sh` (Fase 1 do `test-architecture-plan`,
# `D-TEST-ARCHITECTURE-GO`). Le arvores FAKE de relatorios Surefire.
#
# RED-first: sem relatorio = rc 3 (nao inventa numero); com relatorio, os totais
# e o ranking dos mais lentos precisam bater byte a byte com a fixture.
#
# Uso: scripts/tests/test-suite-profile-test.sh   (exit 0 = todos os cenarios passam)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
PROFILE="scripts/test-suite-profile.sh"
FAILED=0
pass() { echo "  ok  — $1"; }
fail() { echo "  FAIL— $1"; FAILED=1; }
expect() { [ "$2" = "$3" ] && pass "$1 (rc=$3)" || fail "$1: esperado rc=$2, veio rc=$3"; }

TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT

# ── fixture: dois modulos, 3 testes, 2 classes ────────────────────────────
mkdir -p "$TMP/beta/target/surefire-reports" "$TMP/alpha/target/surefire-reports"
cat > "$TMP/alpha/target/surefire-reports/TEST-A.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite version="3.0.2" name="dev.kof.alpha.ATest" time="1.000" tests="2" errors="0" skipped="0" failures="0">
  <testcase name="a1" classname="dev.kof.alpha.ATest" time="0.600"/>
  <testcase name="a2" classname="dev.kof.alpha.ATest" time="0.400"/>
</testsuite>
XML
cat > "$TMP/beta/target/surefire-reports/TEST-B.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite version="3.0.2" name="dev.kof.beta.BTest" time="3.000" tests="1" errors="0" skipped="0" failures="0">
  <testcase name="b1" classname="dev.kof.beta.BTest" time="3.000"/>
</testsuite>
XML

# ── cenario 1: sem relatorio => rc 3 (nao certifica) ──────────────────────
mkdir -p "$TMP/empty"
out="$(bash "$PROFILE" --root "$TMP/empty" 2>&1)"; rc=$?
expect "arvore sem relatorios nao certifica" 3 "$rc"
grep -q "SUITE-PROFILE: unknown" <<<"$out" && pass "unknown emitido (sem relatorio)" || fail "nao emitiu unknown"

# ── cenario 2: totais medidos ─────────────────────────────────────────────
out="$(bash "$PROFILE" --root "$TMP" 2>&1)"; rc=$?
expect "arvore com relatorios mede" 0 "$rc"
grep -q "tests=3 failures=0 errors=0 skipped=0 time=4.000s" <<<"$out" \
  && pass "totais (3 testes / 4.000s)" || fail "totais errados: $(grep SUITE-PROFILE <<<"$out")"
grep -q "modules=2 classes=2 reports=2" <<<"$out" && pass "contagem de modulos/classes" || fail "contagem errada"

# ── cenario 3: ranking dos testes mais lentos (b1 primeiro) ───────────────
first="$(awk '/TOP .* SLOWEST TESTS:/{f=1;next} f&&/^ +1\./{print;exit}' <<<"$out")"
grep -q "beta.BTest#b1" <<<"$first" && pass "teste mais lento = b1" || fail "ranking de testes errado: $first"

# ── cenario 4: ranking de classes (BTest antes de ATest) ──────────────────
ctop="$(awk '/TOP .* SLOWEST CLASSES:/{f=1;next} f&&/^ +1\./{print;exit}' <<<"$out")"
grep -q "dev.kof.beta.BTest" <<<"$ctop" && pass "classe mais lenta = BTest" || fail "ranking de classes errado: $ctop"

# ── cenario 5: duracao por modulo (beta antes de alpha) ───────────────────
mods="$(awk '/TIME BY MODULE:/{f=1;next} f{print}' <<<"$out")"
[ "$(sed -n '1p' <<<"$mods")" = "$(printf '  %9ss  beta' 3.000)" ] \
  && pass "modulo mais lento = beta (3.000s)" || fail "duracao por modulo errada: $(sed -n '1p' <<<"$mods")"

# ── cenario 6: saida markdown ranqueada ───────────────────────────────────
bash "$PROFILE" --root "$TMP" --md "$TMP/out.md" --quiet >/dev/null 2>&1
grep -q "tests=3 failures=0 errors=0 skipped=0 time=4.000s" "$TMP/out.md" \
  && pass "markdown com resumo medido" || fail "markdown sem resumo"
grep -q '`dev.kof.beta.BTest#b1`' "$TMP/out.md" && pass "markdown com o teste mais lento" || fail "markdown sem b1"

if [ "$FAILED" = 1 ]; then
  echo "== RESULTADO: FALHOU =="
  exit 1
fi
echo "== RESULTADO: todos os cenarios OK =="
