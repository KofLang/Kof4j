#!/usr/bin/env bash
#
# agent-evidence-test.sh — o manifesto de evidência é DADO verificável, não
# frase de commit: SHA, comandos reais, exit code, log, NOT_RUN explícito.
#
#   E1  init: schema, SHA, branch, issue, risco calculado, arquivos alterados
#   E2  run: grava exit code REAL, sha_tested, log; falha = FAIL e devolve o rc
#   E3  resumo do Maven é parseado; exit 0 com Failures>0 NÃO é PASS
#   E4  validate ok quando tudo passou no SHA
#   E5  manifesto STALE (HEAD andou) é rejeitado
#   E6  NOT_RUN exige motivo e bloqueia a validação (nunca vira PASS)
#   E7  árvore suja no init é rejeitada
#   E8  sem testes registrados é rejeitado
#   E9  `mark PASS` é proibido (PASS só vem de comando executado)
#   E10 estado fora do repo (XDG), não em /tmp
#   E11 verdict do worker
#
# Uso: scripts/tests/agent-evidence-test.sh   (exit 0 = todos passam)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
. scripts/tests/lib-agent-test.sh

EV="$REPO_ROOT/scripts/agent-evidence.sh"
ev() { bash "$EV" "$@"; }
field() { python3 -c "import json,sys; d=json.load(open('$1')); print(eval(sys.argv[1]))" "$2"; }
setup_ffi() { # repo com um commit que toca FFI
    mk_env; mk_repo
    ( cd "$REPO" && mkdir -p kof-compiler/src/main/java/dev/kof/compiler && echo x > kof-compiler/src/main/java/dev/kof/compiler/FfiSignature.java \
        && git add -A && git commit -q -m "fix ffi" )
    ID="$(ev init --repo "$REPO" --issue 549 --classification "BUG REAL" --base HEAD~1 --session ses_w)"
    EJ="$XDG_STATE_HOME/kof-agent/verifier/$ID/evidence.json"
}

echo "E1 — init"
setup_ffi
HEAD_SHA="$(git -C "$REPO" rev-parse HEAD)"
assert_eq 1 "$(field "$EJ" "d['schema']")" "schema 1"
assert_eq "$HEAD_SHA" "$(field "$EJ" "d['head_sha']")" "head_sha = HEAD real"
assert_eq beta-0.4.0 "$(field "$EJ" "d['branch']")" "branch"
assert_eq 549 "$(field "$EJ" "d['issue']")" "issue"
assert_eq high "$(field "$EJ" "d['risk']")" "risco calculado a partir dos arquivos (FFI)"
assert_eq False "$(field "$EJ" "d['dirty']")" "árvore limpa"
assert_contains "$(field "$EJ" "d['changed_files']")" "FfiSignature.java" "changed_files"
assert_eq ses_w "$(field "$EJ" "d['worker_session']")" "sessão do worker registrada (base da independência)"

echo "E2 — run grava exit code real"
setup_ffi
ev run --repo "$REPO" --run-id "$ID" --label ok-cmd -- bash -c 'echo hello; exit 0' >/dev/null; RC_OK=$?
ev run --repo "$REPO" --run-id "$ID" --label bad-cmd -- bash -c 'echo boom; exit 7' >/dev/null; RC_BAD=$?
assert_eq 0 "$RC_OK" "run devolve o rc do comando (0)"
assert_eq 7 "$RC_BAD" "run devolve o rc do comando (7)"
assert_eq PASS "$(field "$EJ" "d['tests'][0]['status']")" "exit 0 = PASS"
assert_eq FAIL "$(field "$EJ" "d['tests'][1]['status']")" "exit 7 = FAIL"
assert_eq 7 "$(field "$EJ" "d['tests'][1]['exit_code']")" "exit_code real registrado"
assert_eq "$(git -C "$REPO" rev-parse HEAD)" "$(field "$EJ" "d['tests'][0]['sha_tested']")" "sha_tested = SHA em que rodou"
assert_contains "$(cat "$(field "$EJ" "d['tests'][0]['log_path']")")" "hello" "log persistido"

echo "E3 — resumo do Maven (failure.ignore não vira PASS)"
setup_ffi
ev run --repo "$REPO" --run-id "$ID" --label green -- bash -c 'echo "[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 1"' >/dev/null
ev run --repo "$REPO" --run-id "$ID" --label ignored-failure -- bash -c 'echo "[ERROR] Tests run: 5, Failures: 1, Errors: 0, Skipped: 0"; exit 0' >/dev/null
assert_eq 12 "$(field "$EJ" "d['tests'][0]['executed_tests']")" "executed_tests parseado"
assert_eq 1 "$(field "$EJ" "d['tests'][0]['skips']")" "skips parseado"
assert_eq FAIL "$(field "$EJ" "d['tests'][1]['status']")" "exit 0 com Failures>0 = FAIL (failure.ignore não engana)"

echo "E4 — validate ok"
setup_ffi
ev run --repo "$REPO" --run-id "$ID" --label t1 -- true >/dev/null
OUT="$(ev validate --repo "$REPO" --run-id "$ID")"; RC=$?
assert_eq 0 "$RC" "evidência válida"
assert_contains "$OUT" "OK" "mensagem OK"

echo "E5 — manifesto stale"
setup_ffi
ev run --repo "$REPO" --run-id "$ID" --label t1 -- true >/dev/null
( cd "$REPO" && echo z > z.txt && git add -A && git commit -q -m mais )
OUT="$(ev validate --repo "$REPO" --run-id "$ID")"; RC=$?
assert_eq 3 "$RC" "HEAD andou: inválido (3)"
assert_contains "$OUT" "STALE" "diagnostica STALE"

echo "E6 — NOT_RUN"
setup_ffi
ev run --repo "$REPO" --run-id "$ID" --label t1 -- true >/dev/null
ev mark --repo "$REPO" --run-id "$ID" --name riscv64 --status NOT_RUN >/dev/null 2>&1; RC=$?
assert_eq 2 "$RC" "NOT_RUN sem --reason é recusado"
ev mark --repo "$REPO" --run-id "$ID" --name riscv64 --status NOT_RUN --reason "sem qemu neste host" >/dev/null
OUT="$(ev validate --repo "$REPO" --run-id "$ID")"; RC=$?
assert_eq 3 "$RC" "NOT_RUN bloqueia a validação"
assert_contains "$OUT" "NOT_RUN: cross_target.riscv64" "NOT_RUN visível e nunca PASS"

echo "E7 — árvore suja no init"
mk_env; mk_repo
echo "sujo" > "$REPO/wip.txt"
ID="$(ev init --repo "$REPO" --issue 1 --classification x)"
EJ="$XDG_STATE_HOME/kof-agent/verifier/$ID/evidence.json"
ev run --repo "$REPO" --run-id "$ID" --label t -- true >/dev/null
OUT="$(ev validate --repo "$REPO" --run-id "$ID")"; RC=$?
assert_eq 3 "$RC" "dirty no init: inválido"
assert_contains "$OUT" "DIRTY" "diagnostica DIRTY"

echo "E8 — sem testes"
setup_ffi
OUT="$(ev validate --repo "$REPO" --run-id "$ID")"; RC=$?
assert_eq 3 "$RC" "nenhum comando registrado: inválido"
assert_contains "$OUT" "SEM_TESTES" "diagnostica SEM_TESTES"

echo "E9 — mark PASS é proibido"
setup_ffi
ev mark --repo "$REPO" --run-id "$ID" --name x86 --status PASS >/dev/null 2>&1; RC=$?
assert_eq 2 "$RC" "mark PASS recusado"
ev run --repo "$REPO" --run-id "$ID" --kind cross --label riscv64 -- true >/dev/null
assert_eq PASS "$(field "$EJ" "d['cross_target']['riscv64']['status']")" "PASS de cross só via comando executado"

echo "E10 — estado fora do repo e do /tmp (default = ~/.local/state)"
setup_ffi
case "$EJ" in "$XDG_STATE_HOME"/kof-agent/verifier/*) pass "sob XDG_STATE_HOME/kof-agent/verifier";; *) fail "caminho inesperado: $EJ";; esac
[ -d "$XDG_STATE_HOME/kof-agent/verifier/$ID/logs" ] && pass "diretório de logs" || fail "sem logs/"
( unset XDG_STATE_HOME; export HOME="$TMP/homex"; mkdir -p "$HOME"
  ID2="$(bash "$EV" init --repo "$REPO" --issue 2 --classification x)"
  [ -f "$HOME/.local/state/kof-agent/verifier/$ID2/evidence.json" ] ) \
    && pass "sem XDG_STATE_HOME: default ~/.local/state/kof-agent (não /tmp)" || fail "default fora de ~/.local/state"

echo "E11 — verdict do worker"
setup_ffi
ev verdict --repo "$REPO" --run-id "$ID" --worker pass >/dev/null
assert_eq pass "$(field "$EJ" "d['worker_verdict']")" "worker_verdict registrado"
ev verdict --repo "$REPO" --run-id "$ID" --worker talvez >/dev/null 2>&1; RC=$?
assert_eq 2 "$RC" "valor inválido recusado"

echo "E12 — janela por-COMMIT --commits [#659 limitacao apontada pelo verifier; #664]"
mk_env; mk_repo
( cd "$REPO" && echo a > A.java && git add -A && git commit -q -m c1 \
    && echo b > B.md && git add -A && git commit -q -m c2 \
    && echo c > C.java && git add -A && git commit -q -m c3 )
ID="$(ev init --repo "$REPO" --issue 549 --classification "BUG REAL" --session ses_w --commits HEAD~1,HEAD)"
EJ="$XDG_STATE_HOME/kof-agent/verifier/$ID/evidence.json"
CF="$(field "$EJ" "d['changed_files']")"
assert_contains "$CF" "B.md" "janela inclui commit listado"
assert_contains "$CF" "C.java" "janela inclui HEAD listado"
case "$CF" in *A.java*) fail "janela deve EXCLUIR commit nao listado";; *) pass "janela exclui commit nao listado";; esac
assert_eq "$(git -C "$REPO" rev-parse HEAD~2)" "$(field "$EJ" "d['base_sha']")" "base = pai do primeiro listado"
RC_BADBASE="$(ev init --repo "$REPO" --issue 3 --classification x --commits HEAD~1 >/dev/null 2>&1; echo $?)"
assert_eq 2 "$RC_BADBASE" "ultimo listado != HEAD: rejeitado"
RC_BADSHA="$(ev init --repo "$REPO" --issue 3 --classification x --commits deadbeef,HEAD >/dev/null 2>&1; echo $?)"
assert_eq 2 "$RC_BADSHA" "commit inexistente: rejeitado"

finish
