#!/usr/bin/env bash
# pipeline-state-test.sh — prova a máquina de estados do pipeline de branches
# (D-QUALITY-PIPELINE-2609 / D-BRANCH-PIPELINE). Hermético (sem rede): só o
# módulo puro scripts/pipeline/pipeline_state.py. O contrato que este teste
# protege: (1) as transições válidas passam; (2) a matriz de bypass do
# contrato é TODA bloqueada; (3) falha é sempre fail-closed (branch/estado
# desconhecido NUNCA é "allowed", nem um no-op).
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
M="scripts/pipeline/pipeline_state.py"
FAILED=0
pass() { echo "  ok  — $1"; }
fail() { echo "  FAIL— $1"; FAILED=1; }

# expect <desc> <rc-esperado> <comando...>
expect() {
    local desc="$1" want="$2"; shift 2
    python3 "$M" "$@" >/dev/null 2>&1; local rc=$?
    [ "$rc" = "$want" ] && pass "$desc (rc=$rc)" || fail "$desc (esperado rc=$want, veio rc=$rc)"
}

echo "== selftest embutido (matriz válida + bypass + fail-closed) =="
python3 "$M" --selftest >/dev/null 2>&1 && pass "selftest OK" || fail "selftest embutido falhou"

echo "== promoções válidas (rc 0) =="
expect "lab -> testing"          0 promote lab testing
expect "testing -> prerelease"   0 promote testing prerelease
expect "prerelease -> stable"    0 promote prerelease stable
expect "stable -> release/1.0.0" 0 promote stable release/1.0.0
expect "release -> tag"          0 promote release/1.0.0 kof-1.0.0-linux-x86_64

echo "== bypass do contrato (rc 1 = BLOCKED) =="
expect "lab -> prerelease"       1 promote lab prerelease
expect "lab -> stable"           1 promote lab stable
expect "testing -> stable"       1 promote testing stable
expect "prerelease -> release"   1 promote prerelease release/1.0.0
expect "lab -> release"          1 promote lab release/1.0.0
expect "stable -> prerelease"    1 promote stable prerelease

echo "== fail-closed =="
expect "branch desconhecida (beta-0.5.0)" 1 promote beta-0.5.0 testing
expect "no-op lab -> lab"                 1 promote lab lab
expect "release/x inválido"               1 promote stable release/x

echo "== CLI informativo =="
st="$(python3 "$M" branch-state prerelease 2>/dev/null)"
[ "$st" = "PRERELEASE_OBSERVATION" ] && pass "branch-state prerelease = $st" || fail "branch-state prerelease = '$st'"
python3 "$M" branch-state beta-0.5.0 >/dev/null 2>&1 && fail "branch-state aceitou branch desconhecida" || pass "branch-state recusa branch desconhecida (rc 1)"
out="$(python3 "$M" promote lab stable --json 2>/dev/null)"
printf '%s' "$out" | grep -q '"allowed": false' && pass "JSON de auditoria marca allowed=false" || fail "JSON não marcou allowed=false"

[ "$FAILED" = 0 ] && echo "== RESULTADO: todos os cenários OK ==" || { echo "== RESULTADO: FALHOU =="; exit 1; }
