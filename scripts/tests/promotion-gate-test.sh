#!/usr/bin/env bash
# promotion-gate-test.sh — prova o Promotion Gate (D-QUALITY-PIPELINE-2609).
# Hermético (sem rede/gh): só o módulo puro scripts/pipeline/promotion_gate.py.
# Contrato: 100% dos checks obrigatórios verdes (≥80% foi dropado); a matriz de
# bypass é bloqueada mesmo com tudo verde; a janela de observação de 7 dias e a
# ausência de issues relacionadas condicionam prerelease->stable; a criação de
# tag é idempotente (versão já tagueada = BLOCKED).
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
G="scripts/pipeline/promotion_gate.py"
FAILED=0
pass() { echo "  ok  — $1"; }
fail() { echo "  FAIL— $1"; FAILED=1; }

T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
cat > "$T/all-pass.tsv" <<'EOF'
Build + Tests	PASS
Native cross	PASS
kof.io multiplatform	PASS
Structural quality gates	PASS
CodeQL Gate	PASS
bots	PASS
EOF
cat > "$T/one-red.json" <<'EOF'
[{"name":"Build + Tests","status":"FAIL"},{"name":"Native cross","status":"PASS"},
 {"name":"kof.io multiplatform","status":"PASS"},{"name":"Structural quality gates","status":"PASS"},
 {"name":"CodeQL Gate","status":"PASS"},{"name":"bots","status":"PASS"}]
EOF

echo "== selftest embutido =="
python3 "$G" --selftest >/dev/null 2>&1 && pass "selftest OK" || fail "selftest embutido falhou"

echo "== caminho feliz / bloqueio (rc 0 vs 1) =="
python3 "$G" --from-stage lab --to-stage testing --commit c1 --version 0.6.0 \
  --timestamp 2026-09-28T00:00:00Z --checks "$T/all-pass.tsv" >/dev/null 2>&1 \
  && pass "lab->testing tudo verde = PASSED (rc 0)" || fail "lab->testing deveria passar"
python3 "$G" --from-stage lab --to-stage testing --checks "$T/one-red.json" >/dev/null 2>&1 \
  && fail "um check vermelho deveria bloquear" || pass "um check vermelho = BLOCKED (rc 1)"
python3 "$G" --from-stage lab --to-stage testing >/dev/null 2>&1 \
  && fail "checks ausentes deveriam bloquear (fail closed)" || pass "checks ausentes = BLOCKED (fail closed)"

echo "== matriz de bypass bloqueada mesmo com tudo verde =="
for pair in "lab prerelease" "lab stable" "testing stable" "prerelease release/1.0.0" "stable prerelease"; do
  set -- $pair
  python3 "$G" --from-stage "$1" --to-stage "$2" --checks "$T/all-pass.tsv" \
    --promoted-at 2026-09-01T00:00:00Z --timestamp 2026-09-28T00:00:00Z >/dev/null 2>&1 \
    && fail "bypass $1->$2 deveria ser BLOCKED" || pass "bypass $1->$2 = BLOCKED"
done

echo "== janela de observação (7 dias) =="
python3 "$G" --from-stage prerelease --to-stage stable --checks "$T/all-pass.tsv" \
  --promoted-at 2026-09-20T00:00:00Z --timestamp 2026-09-28T00:00:00Z >/dev/null 2>&1 \
  && pass "prerelease->stable após 8 dias = PASSED" || fail "8 dias deveria passar"
python3 "$G" --from-stage prerelease --to-stage stable --checks "$T/all-pass.tsv" \
  --promoted-at 2026-09-25T00:00:00Z --timestamp 2026-09-28T00:00:00Z >/dev/null 2>&1 \
  && fail "3 dias deveria bloquear" || pass "prerelease->stable com 3 dias = BLOCKED"
python3 "$G" --from-stage prerelease --to-stage stable --checks "$T/all-pass.tsv" --related-issues 1 \
  --promoted-at 2026-09-20T00:00:00Z --timestamp 2026-09-28T00:00:00Z >/dev/null 2>&1 \
  && fail "issue relacionada deveria bloquear" || pass "issue relacionada na janela = BLOCKED"

echo "== tag idempotente =="
python3 "$G" --from-stage release/1.0.0 --to-stage kof-1.0.0-linux-x86_64 --checks "$T/all-pass.tsv" \
  --version 1.0.0 --already-tagged >/dev/null 2>&1 \
  && fail "tag duplicada deveria bloquear" || pass "versão já tagueada = BLOCKED (idempotente)"

echo "== relatório carrega os campos de auditoria =="
out="$(python3 "$G" --from-stage testing --to-stage prerelease --commit deadbeef --version 0.6.0 \
  --timestamp 2026-09-28T00:00:00Z --checks "$T/all-pass.tsv" 2>/dev/null)"
printf '%s' "$out" | grep -q 'from: testing (TESTING)' && printf '%s' "$out" | grep -q 'status: PASS' \
  && pass "render traz from/to/status" || fail "render faltando campos"

echo "== promotion_evidence (14.3 — scripted proof, nunca opinion) =="
E="scripts/pipeline/promotion_evidence.py"
python3 "$E" --selftest >/dev/null 2>&1 && pass "evidence selftest OK" || fail "evidence selftest falhou"
printf 'Native cross\tsuccess\nkof.io multiplatform\tsuccess\nStructural quality gates\tsuccess\nCodeQL Gate\tsuccess\nbots\tsuccess\n' > "$T/hits.tsv"
printf 'TOTAL: tests=1 failures=0 errors=0 skipped=0\n' > "$T/suite.log"
printf '#!/bin/sh\nexit 0\n' > "$T/st-ok"; printf '#!/bin/sh\nexit 1\n' > "$T/st-red"; chmod +x "$T/st-ok" "$T/st-red"
python3 "$E" --sha deadbeef --check-runs "$T/hits.tsv" --suite-log "$T/suite.log" \
  --stability-cmd "$T/st-ok" --blocking-issues 0 --related-issues 0 \
  --checks-out "$T/ev.json" >/dev/null 2>&1 && pass "evidence build (suite verde) OK" || fail "evidence build falhou"
python3 "$G" --from-stage lab --to-stage testing --checks "$T/ev.json" \
  --timestamp 2026-09-28T00:00:00Z >/dev/null 2>&1 \
  && pass "suite verde + checks verdes = lab->testing EARNABLE (14.3)" || fail "promocao deveria ser earnable com suite verde"
python3 "$E" --sha deadbeef --check-runs "$T/hits.tsv" --suite-log "$T/suite.log" \
  --stability-cmd "$T/st-red" --blocking-issues 0 --related-issues 0 \
  --checks-out "$T/ev-red.json" >/dev/null 2>&1
python3 "$G" --from-stage lab --to-stage testing --checks "$T/ev-red.json" >/dev/null 2>&1 \
  && fail "suite RED nao pode ser earnable" || pass "suite RED = BLOCKED (a medicao manda)"
python3 "$E" --sha deadbeef --check-runs "$T/hits.tsv" --blocking-issues 0 --related-issues 0 \
  --checks-out "$T/ev-none.json" >/dev/null 2>&1
python3 "$G" --from-stage lab --to-stage testing --checks "$T/ev-none.json" >/dev/null 2>&1 \
  && fail "suite NAO rodada nao pode ser earnable" || pass "suite nao rodada = BLOCKED (fail closed)"
python3 "$G" --from-stage testing --to-stage prerelease --checks "$T/ev.json" \
  --blocking-issues 2 --timestamp 2026-09-28T00:00:00Z >/dev/null 2>&1 \
  && fail "2 issues abertas deveriam bloquear" || pass "blocking-issues medidos = BLOCKED"
python3 "$E" --sha x --stability-cmd "$T/st-ok" >/dev/null 2>&1 \
  && fail "omitir as contagens deveria rc=2 (opinion proibida)" || pass "contagens obrigatorias (rc 2 sem elas)"
out="$(python3 "$G" --from-stage prerelease --to-stage stable --checks "$T/all-pass.tsv" --related-issues 0 \
  --promoted-at 2026-09-01T00:00:00Z --timestamp 2026-09-28T00:00:00Z 2>/dev/null)"
printf '%s' "$out" | grep -q "related_issues: 0" && pass "render traz related_issues (auditoria)" || fail "render sem related_issues"

[ "$FAILED" = 0 ] && echo "== RESULTADO: todos os cenários OK ==" || { echo "== RESULTADO: FALHOU =="; exit 1; }
