#!/usr/bin/env bash
#
# agent-verify-test.sh — verifier DETERMINÍSTICO: exige a prova proporcional ao
# risco e nunca transforma NOT_RUN em PASS.
#
#   V1  LOW (docs) sem testes = PASS; gates aplicáveis rodam
#   V2  MEDIUM: testes PASS no SHA = PASS; sem testes = BLOCK
#   V3  gate estrutural falhando (check_500) = BLOCK
#   V4  HIGH/FFI: matriz adversarial completa = PASS; faltando labels = BLOCK
#   V5  HIGH/Native: cross-target sem registro = BLOCK; NOT_RUN = INCOMPLETE (3); tudo PASS = PASS
#   V6  manifesto STALE = BLOCK
#   V7  distribuição/CLI: exige package smoke PASS, FORA do repo (família #550)
#   V8  --risk só sobe
#   V9  deterministic.json gravado; o verifier não toca o repo
#
# Uso: scripts/tests/agent-verify-test.sh   (exit 0 = todos passam)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
. scripts/tests/lib-agent-test.sh

EV="$REPO_ROOT/scripts/agent-evidence.sh"
VERIFY="$REPO_ROOT/scripts/agent-verify.sh"
MATRIX="$REPO_ROOT/scripts/agent-matrix.tsv"
ev() { bash "$EV" "$@"; }
det() { bash "$VERIFY" deterministic --repo "$REPO" --run-id "$ID" "$@"; }
rc_det() { det "$@" >/dev/null 2>&1; echo $?; }
run_ok() { ev run --repo "$REPO" --run-id "$ID" --label "$1" ${2:+--kind "$2"} -- true >/dev/null; }
setup() { # caminhos alterados...
    mk_env; mk_repo
    ( cd "$REPO" && for p in "$@"; do mkdir -p "$(dirname "$p")"; echo "$p" > "$p"; done && git add -A && git commit -q -m change )
    export AGENT_VERIFY_CHECK500=true AGENT_VERIFY_DOCSLANG=true AGENT_VERIFY_STDLIB=true AGENT_VERIFY_DOCIMPACT=true
    ID="$(ev init --repo "$REPO" --issue 549 --classification "BUG REAL" --base HEAD~1 --session ses_w)"
    DJ="$XDG_STATE_HOME/kof-agent/verifier/$ID/deterministic.json"
    EJ="$XDG_STATE_HOME/kof-agent/verifier/$ID/evidence.json"
}
adversarial_all() { # domínio -> roda 1 comando adversarial PASS por label da matriz
    local dom="$1" label
    while IFS=$'\t' read -r d label _; do
        [ "$d" = "$dom" ] && run_ok "$label" adversarial
    done < <(grep -v '^#' "$MATRIX")
}

echo "V1 — LOW docs sem testes = PASS"
setup docs/nota.md
OUT="$(det)"; RC=$?
assert_eq 0 "$RC" "docs isolados: PASS"
assert_contains "$OUT" "risk=low" "risco low"

echo "V2 — MEDIUM"
setup kof-compiler/src/main/java/dev/kof/compiler/BuiltinCallTyper.java
OUT="$(det)"; RC=$?
assert_eq 1 "$RC" "medium sem testes = BLOCK"
assert_contains "$OUT" "SEM_TESTES" "diagnostica SEM_TESTES"
run_ok unit
assert_eq 0 "$(rc_det)" "medium com teste PASS no SHA = PASS"

echo "V3 — gate estrutural falhando"
setup kof-compiler/src/main/java/dev/kof/compiler/BuiltinCallTyper.java
run_ok unit
export AGENT_VERIFY_CHECK500=false
OUT="$(det)"; RC=$?
assert_eq 1 "$RC" "check_500 falhando = BLOCK"
assert_contains "$OUT" "GATE_FALHOU: check_500" "aponta o gate"

echo "V4 — HIGH/FFI: matriz adversarial"
setup kof-compiler/src/main/java/dev/kof/compiler/FfiSignature.java
run_ok unit
OUT="$(det)"; RC=$?
assert_eq 1 "$RC" "HIGH sem matriz = BLOCK"
assert_contains "$OUT" "MATRIZ_ADVERSARIAL_FALTA: ffi-abi/literal-vs-variable" "lista os labels que faltam"
assert_contains "$OUT" "ffi-abi/fp-specials" "inclui NaN/Inf/-0.0"
assert_contains "$OUT" "ffi-abi/jvm-oracle-vs-native" "inclui oráculo JVM x Native"
adversarial_all ffi-abi
OUT="$(det)"; RC=$?
assert_eq 0 "$RC" "matriz completa = PASS"
assert_contains "$OUT" "risk=high" "risco high preservado"
assert_eq true "$(python3 -c "import json;print(str(json.load(open('$DJ'))['independent_verifier_required']).lower())")" "sinaliza verifier independente"

echo "V5 — HIGH/Native: cross-target"
setup kof-compiler/src/main/java/dev/kof/compiler/nat/NativeFfiCall.java
run_ok unit
OUT="$(det)"; RC=$?
assert_eq 1 "$RC" "cross_target sem registro = BLOCK"
assert_contains "$OUT" "CROSS_TARGET_SEM_REGISTRO: x86" "exige x86"
run_ok x86 cross; run_ok riscv64 cross
ev mark --repo "$REPO" --run-id "$ID" --name aarch64 --status NOT_RUN --reason "sem qemu-aarch64 neste host" >/dev/null
OUT="$(det)"; RC=$?
assert_eq 3 "$RC" "NOT_RUN = INCOMPLETE (3), nunca PASS"
assert_contains "$OUT" "INCOMPLETE: NOT_RUN: cross_target.aarch64" "NOT_RUN explícito"
setup kof-compiler/src/main/java/dev/kof/compiler/nat/NativeFfiCall.java
run_ok unit; run_ok x86 cross; run_ok riscv64 cross; run_ok aarch64 cross
assert_eq 0 "$(rc_det)" "x86 + riscv64 + aarch64 PASS = PASS"

echo "V6 — STALE"
setup kof-compiler/src/main/java/dev/kof/compiler/BuiltinCallTyper.java
run_ok unit
( cd "$REPO" && echo mais > mais.txt && git add -A && git commit -q -m mais )
OUT="$(det)"; RC=$?
assert_eq 1 "$RC" "HEAD andou = BLOCK"
assert_contains "$OUT" "STALE" "diagnostica STALE"

echo "V7 — distribuição/CLI exige package smoke FORA do repo"
setup kof-cli/src/main/java/dev/kof/cli/Profile.java
run_ok unit
OUT="$(det)"; RC=$?
assert_eq 1 "$RC" "CLI tocada sem smoke = BLOCK"
assert_contains "$OUT" "SEM_PACKAGE_SMOKE" "diagnostica a falta do smoke"
ev run --repo "$REPO" --run-id "$ID" --label smoke-dentro --kind smoke -- true >/dev/null
OUT="$(det)"; RC=$?
assert_eq 1 "$RC" "smoke DENTRO do repo = BLOCK"
assert_contains "$OUT" "PACKAGE_SMOKE_DENTRO_DO_REPO" "família #550: fora do diretório favorável"
mkdir -p "$TMP/fora"
ev run --repo "$REPO" --run-id "$ID" --label smoke-fora --kind smoke --cwd "$TMP/fora" -- true >/dev/null
assert_eq 0 "$(rc_det)" "smoke PASS fora do repo = PASS"

echo "V8 — --risk só sobe"
setup docs/nota.md
assert_eq 0 "$(rc_det)" "LOW = PASS"
assert_eq 1 "$(rc_det --risk high)" "--risk high sobe e exige matriz (BLOCK)"
setup kof-compiler/src/main/java/dev/kof/compiler/nat/NativeFfiCall.java
run_ok unit; run_ok x86 cross; run_ok riscv64 cross; run_ok aarch64 cross
assert_eq 0 "$(rc_det --risk low)" "--risk low NÃO rebaixa"
assert_contains "$(det --risk low)" "risk=high" "continua high"

echo "V9 — deterministic.json e o repo intocado"
setup docs/nota.md
det >/dev/null
assert_eq "$(git -C "$REPO" rev-parse HEAD)" "$(python3 -c "import json;print(json.load(open('$DJ'))['sha'])")" "deterministic.json com o SHA"
assert_eq PASS "$(python3 -c "import json;print(json.load(open('$DJ'))['verdict'])")" "verdict gravado"
assert_eq "" "$(git -C "$REPO" status --porcelain)" "o verifier não modificou o repositório"

# --- verifier INDEPENDENTE (só HIGH) ---------------------------------------------------------
ind() { bash "$VERIFY" independent --repo "$REPO" --run-id "$ID" "$@"; }
vcalls() { [ -f "$FAKE_GH_DIR/verifier.calls" ] && wc -l < "$FAKE_GH_DIR/verifier.calls" || echo 0; }
vjson() { python3 -c "import json;d=json.load(open('$XDG_STATE_HOME/kof-agent/verifier/$ID/verdict.json'));print($1)"; }
fake_verifier() {
    cat > "$TMP/bin/fake-verifier.sh" <<'EOF'
#!/usr/bin/env bash
# FAKE do verifier independente: conta chamadas e escreve verdict.json conforme o env.
echo x >> "$FAKE_GH_DIR/verifier.calls"
[ -n "${FAKE_MUTATE_FILE:-}" ] && echo hacked > "$FAKE_MUTATE_FILE"
[ -n "${FAKE_NOWRITE:-}" ] && exit "${FAKE_VRC:-0}"
python3 - <<'PY'
import json, os
json.dump({"sha": os.environ.get("FAKE_SHA") or os.environ["VERIFIER_SHA"], "risk": "high",
           "verdict": os.environ.get("FAKE_VERDICT", "PASS"), "findings": [], "adversarial_commands": [],
           "verified_at": "2026-09-20T10:00:00-03:00",
           "verifier_session": os.environ["FAKE_SESSION"] if "FAKE_SESSION" in os.environ else "ses_verifier"},
          open(os.environ["VERIFIER_OUTPUT"], "w"))
PY
exit "${FAKE_VRC:-0}"
EOF
    chmod +x "$TMP/bin/fake-verifier.sh"
    export AGENT_VERIFIER_CMD="$TMP/bin/fake-verifier.sh"
    unset FAKE_MUTATE_FILE FAKE_NOWRITE FAKE_VRC FAKE_SHA FAKE_VERDICT FAKE_SESSION
}
high_ready() { # HIGH/FFI com determinístico PASS
    setup kof-compiler/src/main/java/dev/kof/compiler/FfiSignature.java
    run_ok unit; adversarial_all ffi-abi
    det >/dev/null 2>&1
}

echo "V10 — LOW/MEDIUM não pagam verifier independente"
setup docs/nota.md; fake_verifier
det >/dev/null 2>&1
OUT="$(ind)"; RC=$?
assert_eq 0 "$RC" "LOW: ok"
assert_contains "$OUT" "NÃO exigido" "explica que não é exigido"
assert_eq 0 "$(vcalls)" "nenhuma chamada ao verifier (custo de modelo = 0)"

echo "V11 — sem integração configurada = NEEDS_MAINTAINER (sem fingir independência)"
high_ready; unset AGENT_VERIFIER_CMD
OUT="$(ind)"; RC=$?
assert_eq 4 "$RC" "sem AGENT_VERIFIER_CMD: NEEDS_MAINTAINER (exit 4)"
assert_eq NEEDS_MAINTAINER "$(vjson "d['verdict']")" "verdict.json = NEEDS_MAINTAINER"
assert_eq None "$(vjson "d['verifier_session']")" "sem sessão: independência NÃO afirmada"
PK="$XDG_STATE_HOME/kof-agent/verifier/$ID/package"
assert_contains "$(cat "$PK/brief.md")" "hipótese incorreta" "brief traz a pergunta central"
assert_contains "$(cat "$PK/brief.md")" "issue #549" "brief traz o requisito"
[ -s "$PK/diff.patch" ] && pass "diff congelado presente" || fail "sem diff.patch"
assert_eq "$(git -C "$REPO" rev-parse HEAD)" "$(cat "$PK/SHA" | tr -d '\n')" "SHA congelado"
assert_eq "" "$(ls "$PK" | grep -iE 'notes|reason|worker' || true)" "pacote NÃO inclui raciocínio do worker"

echo "V12 — verifier em sessão própria: PASS"
high_ready; fake_verifier
OUT="$(ind)"; RC=$?
assert_eq 0 "$RC" "PASS independente (exit 0)"
assert_eq PASS "$(vjson "d['verdict']")" "verdict PASS gravado"
assert_eq 1 "$(vcalls)" "1 chamada (só HIGH paga o 2º modelo)"

echo "V13 — mesma sessão do worker não é independente"
high_ready; fake_verifier; export FAKE_SESSION=ses_w
OUT="$(ind)"; RC=$?
assert_eq 1 "$RC" "sessão igual à do worker: BLOCK"
assert_contains "$(vjson "d['findings'][0]['reason']")" "NAO_INDEPENDENTE" "diagnostica a falta de independência"
export FAKE_SESSION=""
OUT="$(ind)"; RC=$?
assert_eq 1 "$RC" "sem sessão registrada: BLOCK"

echo "V14 — o verifier não pode tocar a produção"
high_ready; fake_verifier; export FAKE_MUTATE_FILE="$REPO/injetado.txt"
OUT="$(ind)"; RC=$?
assert_eq 1 "$RC" "verifier alterou o repo: BLOCK"
assert_contains "$OUT" "VERIFIER_MODIFICOU_PRODUCAO" "diagnostica a violação"

echo "V15 — veredito com SHA errado / valor inválido"
high_ready; fake_verifier; export FAKE_SHA=0000000000000000000000000000000000000000
assert_eq 1 "$(ind >/dev/null 2>&1; echo $?)" "verificou outro SHA: BLOCK"
high_ready; fake_verifier; export FAKE_VERDICT=talvez
assert_eq 1 "$(ind >/dev/null 2>&1; echo $?)" "veredito inválido: BLOCK"

echo "V16 — prova vermelha não gasta modelo"
setup kof-compiler/src/main/java/dev/kof/compiler/FfiSignature.java
run_ok unit; fake_verifier
det >/dev/null 2>&1                                      # BLOCK: matriz adversarial ausente
OUT="$(ind)"; RC=$?
assert_eq 1 "$RC" "determinístico não-PASS: independente recusa"
assert_eq 0 "$(vcalls)" "0 chamadas ao modelo"

echo "V17 — verifier que falha / BLOCK / NEEDS_MAINTAINER"
high_ready; fake_verifier; export FAKE_NOWRITE=1 FAKE_VRC=1
assert_eq 4 "$(ind >/dev/null 2>&1; echo $?)" "verifier falhou sem veredito: NEEDS_MAINTAINER (4)"
high_ready; fake_verifier; export FAKE_VERDICT=BLOCK
assert_eq 1 "$(ind >/dev/null 2>&1; echo $?)" "verifier independente BLOCK: exit 1"
high_ready; fake_verifier; export FAKE_VERDICT=NEEDS_MAINTAINER
assert_eq 4 "$(ind >/dev/null 2>&1; echo $?)" "verifier NEEDS_MAINTAINER: exit 4"

echo "V18 — manifesto de evidencia ilegivel = BLOCK (nunca false PASS) [#655]"
setup docs/nota.md
printf '{ nao-e-json' > "$EJ"                   # existe p/ o bash, python nao consegue ler (cenario do path MSYS/corrupcao)
OUT="$(det 2>&1)"; RC=$?
assert_eq 1 "$RC" "manifesto ilegivel: BLOCK (exit 1), nunca PASS"
assert_contains "$OUT" "EVIDENCE_MANIFEST_ILEGIVEL" "diagnostica a falha fechada do manifesto"

echo "V19 — sid do verifier com formato falso nao prova independencia [#659 wrapper; #664]"
high_ready; fake_verifier; export FAKE_SESSION=PENDING
OUT="$(ind)"; RC=$?
assert_eq 1 "$RC" "sid sem prefixo de sessao real: BLOCK"
assert_contains "$(vjson "d['findings'][0]['reason']")" "NAO_INDEPENDENTE_FORMATO" "diagnostica o formato falso do sid"

finish
