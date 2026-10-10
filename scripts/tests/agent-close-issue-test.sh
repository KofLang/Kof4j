#!/usr/bin/env bash
#
# agent-close-issue-test.sh — o worker só fecha issue pelo guard, e o guard só
# fecha com prova. `gh` é FAKE (grava CLOSE: em closes.log); nenhum write real.
#
#   C1  caminho feliz (MEDIUM) fecha
#   C2  sem evidência / issue errada = bloqueia
#   C3  manifesto stale = bloqueia
#   C4  sem verifier determinístico / não-PASS / stale = bloqueia
#   C5  HIGH sem verifier independente = BLOCK (T8); mesma sessão = BLOCK; independente PASS = fecha
#   C6  LOW não paga verifier independente (T9)
#   C7  design/ambiguidade não vira "fixed"
#   C8  fix não pushado = bloqueia
#   C9  identidade humana = bloqueia (só com allow explícito)
#   C10 --dry-run não chama gh issue close
#
# Uso: scripts/tests/agent-close-issue-test.sh   (exit 0 = todos passam)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
. scripts/tests/lib-agent-test.sh

EV="$REPO_ROOT/scripts/agent-evidence.sh"
VERIFY="$REPO_ROOT/scripts/agent-verify.sh"
CLOSE="$REPO_ROOT/scripts/agent-close-issue.sh"
ev() { bash "$EV" "$@"; }
closes() { grep -c '^CLOSE:' "$FAKE_GH_DIR/closes.log" 2>/dev/null || true; }
VD() { echo "$XDG_STATE_HOME/kof-agent/verifier/$ID"; }

setup() { # classificação, caminhos...
    local cls="$1"; shift
    mk_env; mk_repo
    git init -q --bare "$TMP/remote.git"
    ( cd "$REPO" && git remote add origin "$TMP/remote.git" && git push -q -u origin beta-0.4.0 )
    ( cd "$REPO" && for p in "$@"; do mkdir -p "$(dirname "$p")"; echo "$p" > "$p"; done && git add -A && git commit -q -m fix && git push -q origin beta-0.4.0 )
    export AGENT_VERIFY_CHECK500=true AGENT_VERIFY_DOCSLANG=true AGENT_VERIFY_STDLIB=true
    # §681: the doc_impact gate (#648, 5f914cce4) has no script in the fake
    # repo, exactly like the other repo-scripts, so it must be stubbed here too.
    export AGENT_VERIFY_DOCIMPACT=true
    export AGENT_IDENTITY_CMD='echo kof-agent-worker[bot]'
    unset AGENT_CLOSE_ALLOW_LOGIN
    ID="$(ev init --repo "$REPO" --issue 549 --classification "$cls" --base HEAD~1 --session ses_worker)"
    : > "$FAKE_GH_DIR/closes.log"
}
green() { ev run --repo "$REPO" --run-id "$ID" --label unit -- true >/dev/null; bash "$VERIFY" deterministic --repo "$REPO" --run-id "$ID" >/dev/null 2>&1; }
close() { bash "$CLOSE" 549 --repo "$REPO" --run-id "$ID" "$@"; }
independent() { # sessão verdict
    python3 - "$(VD)/verdict.json" "$(git -C "$REPO" rev-parse HEAD)" "$1" "$2" <<'PY'
import json, sys
path, sha, session, verdict = sys.argv[1:5]
json.dump({"sha": sha, "risk": "high", "verdict": verdict, "findings": [], "adversarial_commands": [],
           "verified_at": "2026-09-20T10:00:00-03:00", "verifier_session": session}, open(path, "w"))
PY
}
adv_all() { while IFS=$'\t' read -r d label _; do [ "$d" = "$1" ] && ev run --repo "$REPO" --run-id "$ID" --label "$label" --kind adversarial -- true >/dev/null; done < <(grep -v '^#' "$REPO_ROOT/scripts/agent-matrix.tsv"); }

echo "C1 — caminho feliz (MEDIUM)"
setup "BUG REAL" kof-compiler/src/main/java/dev/kof/compiler/BuiltinCallTyper.java
green
OUT="$(close)"; RC=$?
assert_eq 0 "$RC" "prova completa: fecha"
assert_eq 1 "$(closes)" "gh issue close chamado 1x"
assert_contains "$(cat "$FAKE_GH_DIR/closes.log")" "issue close 549" "fecha a issue certa"
assert_contains "$(cat "$FAKE_GH_DIR/closes.log")" "$(git -C "$REPO" rev-parse HEAD | cut -c1-12)" "comentário cita o SHA"

echo "C2 — sem evidência / issue errada"
setup "BUG REAL" kof-compiler/src/main/java/dev/kof/compiler/BuiltinCallTyper.java
green
bash "$CLOSE" 549 --repo "$REPO" --run-id inexistente >/dev/null 2>&1; RC=$?
assert_eq 1 "$RC" "run-id inexistente: bloqueia"
bash "$CLOSE" 550 --repo "$REPO" --run-id "$ID" >/dev/null 2>&1; RC=$?
assert_eq 1 "$RC" "evidência de outra issue: bloqueia"
assert_eq 0 "$(closes)" "nada foi fechado"

echo "C3 — manifesto stale"
setup "BUG REAL" kof-compiler/src/main/java/dev/kof/compiler/BuiltinCallTyper.java
green
( cd "$REPO" && echo mais > mais.txt && git add -A && git commit -q -m mais && git push -q origin beta-0.4.0 )
OUT="$(close)"; RC=$?
assert_eq 1 "$RC" "HEAD andou depois da evidência: bloqueia"
assert_contains "$OUT" "MANIFESTO_INVALIDO" "explica: manifesto inválido"
assert_eq 0 "$(closes)" "nada foi fechado"

echo "C4 — verifier determinístico"
setup "BUG REAL" kof-compiler/src/main/java/dev/kof/compiler/BuiltinCallTyper.java
ev run --repo "$REPO" --run-id "$ID" --label unit -- true >/dev/null
OUT="$(close)"; RC=$?
assert_eq 1 "$RC" "sem deterministic.json: bloqueia"
assert_contains "$OUT" "SEM_VERIFIER_DETERMINISTICO" "explica a falta"
export AGENT_VERIFY_CHECK500=false
bash "$VERIFY" deterministic --repo "$REPO" --run-id "$ID" >/dev/null 2>&1
OUT="$(close)"; RC=$?
assert_eq 1 "$RC" "verifier BLOCK: bloqueia"
assert_contains "$OUT" "VERIFIER_DETERMINISTICO_NAO_PASS" "explica: não PASS"

echo "C5 — HIGH exige verifier independente (T8)"
setup "BUG REAL" kof-compiler/src/main/java/dev/kof/compiler/FfiSignature.java
adv_all ffi-abi; green
OUT="$(close)"; RC=$?
assert_eq 1 "$RC" "HIGH sem verdict independente: BLOCK"
assert_contains "$OUT" "SEM_VERIFIER_INDEPENDENTE" "explica a falta"
independent ses_worker PASS
OUT="$(close)"; RC=$?
assert_eq 1 "$RC" "verifier na MESMA sessão do worker: BLOCK"
assert_contains "$OUT" "VERIFIER_NAO_INDEPENDENTE" "independência é checada"
independent "" PASS
assert_eq 1 "$(close >/dev/null 2>&1; echo $?)" "verifier sem sessão registrada: BLOCK"
independent ses_verifier BLOCK
assert_eq 1 "$(close >/dev/null 2>&1; echo $?)" "verifier independente BLOCK: bloqueia"
independent ses_verifier PASS
OUT="$(close)"; RC=$?
assert_eq 0 "$RC" "verifier independente PASS em outra sessão: fecha"
assert_eq 1 "$(closes)" "1 fechamento"

echo "C6 — LOW não paga verifier independente (T9)"
setup "BUG REAL" docs/nota.md
green
assert_eq false "$(bash "$REPO_ROOT/scripts/agent-risk.sh" --file docs/nota.md | sed -n 's/^independent_verifier_required=//p')" "low: independent_verifier_required=false"
OUT="$(close)"; RC=$?
assert_eq 0 "$RC" "LOW fecha sem verifier independente"

echo "C7 — design/ambiguidade não vira fixed"
setup "CONTRACT AMBIGUITY" kof-compiler/src/main/java/dev/kof/compiler/BuiltinCallTyper.java
green
OUT="$(close)"; RC=$?
assert_eq 1 "$RC" "CONTRACT AMBIGUITY: bloqueia mesmo com tudo verde"
assert_contains "$OUT" "DECISAO_PENDENTE" "decisão da mantenedora (regra 6)"
setup "DESIGN REQUEST" kof-compiler/src/main/java/dev/kof/compiler/BuiltinCallTyper.java
green
assert_eq 1 "$(close >/dev/null 2>&1; echo $?)" "DESIGN REQUEST: bloqueia"

echo "C8 — fix não pushado"
setup "BUG REAL" kof-compiler/src/main/java/dev/kof/compiler/BuiltinCallTyper.java
( cd "$REPO" && echo local > local.txt && git add -A && git commit -q -m local )
ID="$(ev init --repo "$REPO" --issue 549 --classification "BUG REAL" --base HEAD~1 --session ses_worker)"
green
OUT="$(close)"; RC=$?
assert_eq 1 "$RC" "commit só local: bloqueia"
assert_contains "$OUT" "FIX_NAO_PUSHADO" "explica: não está no remoto"

echo "C9 — identidade"
setup "BUG REAL" kof-compiler/src/main/java/dev/kof/compiler/BuiltinCallTyper.java
green
export AGENT_IDENTITY_CMD='echo melmonfre'
OUT="$(close)"; RC=$?
assert_eq 1 "$RC" "login humano: bloqueia"
assert_contains "$OUT" "IDENTIDADE_NAO_E_DO_WORKER" "explica a identidade"
export AGENT_CLOSE_ALLOW_LOGIN=melmonfre
assert_eq 0 "$(close >/dev/null 2>&1; echo $?)" "allow explícito libera"
export AGENT_IDENTITY_CMD='false'
unset AGENT_CLOSE_ALLOW_LOGIN
OUT="$(close)"; RC=$?
assert_eq 1 "$RC" "identidade indisponível: bloqueia"

echo "C10 — --dry-run"
setup "BUG REAL" kof-compiler/src/main/java/dev/kof/compiler/BuiltinCallTyper.java
green
OUT="$(close --dry-run)"; RC=$?
assert_eq 0 "$RC" "dry-run com prova completa: ok"
assert_contains "$OUT" "DRY-RUN" "avisa que é dry-run"
assert_eq 0 "$(closes)" "dry-run NÃO chama gh issue close"

finish
