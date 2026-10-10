#!/usr/bin/env bash
#
# codeql-gate-test.sh — prova local (sem rede) da logica do GATE 1 do
# scripts/codeql-gate.sh via um `gh` FAKE no PATH que devolve TSV canned.
#
# Contrato #555 (20/09): o gate falha APENAS por alerta novo fora do
# scripts/codeql-baseline.txt (id OU familia rule+path). CODEQL_GATE_SKIP
# exige motivo, vira registro visivel e e ignorado em CI. Bugs historicos
# que continuam provados aqui (licao 15/09):
#   (1) FALSO-VERDE: a LISTA omite o alerta recem-criado `state:null` — a
#       uniao por branch (`?state=open&ref=`) e obrigatoria.
#   (2) FALSO-RED: contar INSTANCIAS acusa aberto ate o que foi dismissado —
#       quem manda e o `state` do alerta.
#   (3) INCONCLUSIVO != verde: API fora nao pode imprimir "verdes".
#   (4) EG-2 (§10): lista vazia legitima (API OK) e GREEN, nao INCONCLUSIVO
#       (empty != unavailable); e o veredito e amarrado ao SHA analisado —
#       analise velha/ausente e INCONCLUSIVO, nunca green (stale analysis
#       cannot decide a new commit).
#
# Uso: scripts/tests/codeql-gate-test.sh   (exit 0 = todos os cenarios passam)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
GATE="scripts/codeql-gate.sh"
FAILED=0

pass() { echo "  ok  — $1"; }
fail() { echo "  FAIL— $1"; FAILED=1; }

# linha TSV no formato que o --jq do gate produz: num, state, dismissed, fixed, ref, rule, path:line
ROW_883=$'883\topen\t-\t-\trefs/heads/main\tjava/concatenated-command-line\tkof-compiler/src/test/java/dev/kof/compiler/ClassShapeChecksTest.java:171'
ROW_999=$'999\topen\t-\t-\trefs/heads/main\tjava/io-resource-leak\tkof-runtime/src/main/java/dev/kof/runtime/Novo.java:1'
ROW_NULL=$'998\tnull\t-\t-\trefs/heads/lab\tjava/relative-path-command\texamples/ForaBaseline.java:7'
# #604: alerta de OUTRA ferramenta de code scanning (SARIF do Debt Scout, nota C0).
# A API real so o devolve quando a consulta NAO filtra tool_name=CodeQL.
ROW_DEBT=$'1047\topen\t-\t-\trefs/heads/lab\tKOF-DEBT-SATD-001\tscripts/x.sh:18'
TIP_SHA="1111111111111111111111111111111111111111"
OLD_SHA="2222222222222222222222222222222222222222"

make_fake_gh() { # $1 = dir, $2 = modo
  local dir="$1" mode="$2"
  cat > "$dir/gh" <<EOF
#!/usr/bin/env bash
mode="$mode"
args="\$*"
if [ "\$mode" = "down" ]; then echo "API rate limit exceeded" >&2; exit 1; fi
case "\$args" in
  *"/code-scanning/alerts?per_page=100"*)
    # LISTA paginada: TSV ja no formato do --jq do gate.
    case "\$mode" in
      baseline) printf '%s\n' "$ROW_883" ;;
      novo)     printf '%s\n%s\n' "$ROW_883" "$ROW_999" ;;
      nullstate) printf '%s\n' "$ROW_883" ;;
      stale)    printf '%s\n' "$ROW_883" ;;
      noana)    printf '%s\n' "$ROW_883" ;;
      empty)    printf '' ;;
      othertool|othertool_ana)
        printf '%s\n' "$ROW_883"
        case "\$args" in *"tool_name=CodeQL"*) ;; *) printf '%s\n' "$ROW_DEBT" ;; esac ;;
    esac
    ;;
  *"alerts?ref=refs/heads/main&state=open"*)
    printf ''
    ;;
  *"alerts?ref=refs/heads/testing&state=open"*|*"alerts?ref=refs/heads/prerelease&state=open"*|*"alerts?ref=refs/heads/stable&state=open"*)
    printf ''
    ;;
  *"alerts?ref=refs/heads/lab&state=open"*)
    # uniao por branch: so o modo nullstate revela o #998 (a lista o omite).
    [ "\$mode" = "nullstate" ] && printf '998\n'
    case "\$mode:\$args" in othertool*:*tool_name=CodeQL*) ;; othertool*) printf '1047\n' ;; esac
    printf ''
    ;;
  *"/code-scanning/alerts/998"*)
    [ "\$mode" = "nullstate" ] && printf '%s\n' "$ROW_NULL"
    ;;
  *"/code-scanning/alerts/1047"*)
    case "\$mode" in othertool*) printf '%s\n' "$ROW_DEBT" ;; esac
    ;;
  *"/code-scanning/analyses?ref=refs/heads/"*)
    # EG-2: analise mais recente do branch. Modo 'stale' = SHA antigo != tip.
    # #604 'othertool_ana': a analise mais recente de QUALQUER ferramenta e a do
    # Debt Scout (no tip); a do CodeQL esta velha — so tool_name=CodeQL a revela.
    case "\$mode" in
      othertool_ana)
        case "\$args" in *"tool_name=CodeQL"*) printf '%s\n' "$OLD_SHA" ;; *) printf '%s\n' "$TIP_SHA" ;; esac ;;
      stale) printf '%s\n' "$OLD_SHA" ;;
      noana) : ;;
      *)     printf '%s\n' "$TIP_SHA" ;;
    esac
    ;;
  *"/branches/main"*|*"/branches/lab"*|*"/branches/testing"*|*"/branches/prerelease"*|*"/branches/stable"*)
    printf '%s\n' "$TIP_SHA"
    ;;
  *)
    printf ''
    ;;
esac
exit 0
EOF
  chmod +x "$dir/gh"
}

run_gate() { # $1=dir fake, $2...=env overrides
  # Hermetico quanto ao CI: sem GITHUB_ACTIONS explicito, o cenario roda como
  # um push LOCAL (onde o CODEQL_GATE_SKIP vale). O cenario 7 e que injeta
  # GITHUB_ACTIONS=true para provar que em CI o skip e IGNORADO — sem o
  # `env -u` a suite herdava o GITHUB_ACTIONS=true do runner e os cenarios
  # 5/6 mediam o caminho de CI, falhando (a suite so passava no host local).
  local dir="$1"; shift
  env -u GITHUB_ACTIONS PATH="$dir:$PATH" CODEQL_GATE_SKIP_LOG="$dir/skip.log" "$@" timeout 60 bash "$GATE" --fast 2>&1
}

TMP=$(mktemp -d)

echo "== cenario 1: alerta aberto DENTRO do baseline => green com contagem de tolerados =="
make_fake_gh "$TMP" baseline
out=$(run_gate "$TMP"); rc=$?
printf '%s' "$out" | grep -q "green — main: 0 novo" && pass "main green (883 tolerado)" || { fail "baseline nao foi tolerado"; printf '%s\n' "$out" | sed 's/^/      /'; }
printf '%s' "$out" | grep -q "tolerados no baseline: 1" && pass "tolerados contado (visivel, nao invisivel)" || fail "nao contou tolerados"
[ "$rc" = 0 ] && pass "exit 0" || fail "exit=$rc (esperado 0)"

echo "== cenario 2: alerta NOVO fora do baseline => RED apontando so o novo =="
make_fake_gh "$TMP" novo
out=$(run_gate "$TMP"); rc=$?
printf '%s' "$out" | grep -q "RED — 1 alerta(s) NOVO(s) sem baseline na branch main" && pass "acusa exatamente 1 novo" || { fail "contagem de novo errada"; printf '%s\n' "$out" | sed 's/^/      /'; }
printf '%s' "$out" | grep -q "#999 \[java/io-resource-leak\]" && pass "lista o #999 (rule certa)" || fail "novo nao identificado"
printf '%s' "$out" | grep -q "#883" && fail "listou o #883 (baseline vazou p/ RED)" || pass "baseline nao aparece na lista de novos"
[ "$rc" = 1 ] && pass "exit 1 (portao vermelho)" || fail "exit=$rc (esperado 1)"

echo "== cenario 3 (licao 15/09 preservada): null-state omitido da lista vira RED =="
make_fake_gh "$TMP" nullstate
out=$(run_gate "$TMP"); rc=$?
printf '%s' "$out" | grep -q "RED — 1 alerta(s) NOVO(s) sem baseline na branch lab" && pass "lab acusou o #998 null-state (uniao por branch funciona)" || { fail "falso-verde do null-state voltou"; printf '%s\n' "$out" | sed 's/^/      /'; }
[ "$rc" = 1 ] && pass "exit 1" || fail "exit=$rc (esperado 1)"

echo "== cenario 4: API fora => INCONCLUSIVO (exit 2), nunca 'verdes' =="
make_fake_gh "$TMP" down
out=$(run_gate "$TMP"); rc=$?
printf '%s' "$out" | grep -q "INCONCLUSIVO" && pass "reporta INCONCLUSIVO" || fail "nao reportou INCONCLUSIVO"
printf '%s' "$out" | grep -q "os dois gates verdes" && fail "imprimiu 'verdes' com API fora" || pass "nao imprime 'verdes' sem avaliar"
[ "$rc" = 2 ] && pass "exit 2" || fail "exit=$rc (esperado 2)"

echo "== cenario 5: CODEQL_GATE_SKIP=1 (sem motivo) NAO VALE — gate roda =="
make_fake_gh "$TMP" novo
out=$(run_gate "$TMP" CODEQL_GATE_SKIP=1); rc=$?
printf '%s' "$out" | grep -q "NAO VALE MAIS" && pass "skip '1' recusado com aviso" || { fail "skip '1' foi aceito (cultura reviveu)"; printf '%s\n' "$out" | sed 's/^/      /'; }
[ "$rc" = 1 ] && pass "gate avaliou mesmo com o skip invalido (rc 1)" || fail "exit=$rc (esperado 1)"

echo "== cenario 6: CODEQL_GATE_SKIP=\"<motivo>\" => pula, GRITA e DEIXA LOG =="
make_fake_gh "$TMP" novo
out=$(run_gate "$TMP" CODEQL_GATE_SKIP="teste do harness — nao e push real"); rc=$?
printf '%s' "$out" | grep -q "PULADO COM MOTIVO" && pass "banner visivel impresso" || fail "sem banner"
printf '%s' "$out" | grep -q "teste do harness" && pass "motivo ecoado no banner" || fail "motivo nao apareceu"
grep -q "teste do harness" "$TMP/skip.log" && pass "log de skips gravado" || { fail "skip.log nao gravado"; ls -la "$TMP"; }
[ "$rc" = 0 ] && pass "exit 0 (com rastro)" || fail "exit=$rc (esperado 0)"

echo "== cenario 7: em CI o skip e IGNORADO (a porta la e o scan) =="
make_fake_gh "$TMP" novo
out=$(run_gate "$TMP" CODEQL_GATE_SKIP="motivo qualquer" GITHUB_ACTIONS=true); rc=$?
printf '%s' "$out" | grep -q "ignorado em CI" && pass "CI nao respeita o skip" || { fail "skip valeu em CI"; printf '%s\n' "$out" | sed 's/^/      /'; }
[ "$rc" = 1 ] && pass "gate avaliou em CI (rc 1)" || fail "exit=$rc (esperado 1)"

echo "== cenario 8 (EG-2, criterio §10): API OK e SEM alertas != API fora => green, nao INCONCLUSIVO =="
make_fake_gh "$TMP" empty
out=$(run_gate "$TMP"); rc=$?
printf '%s' "$out" | grep -q "green — main: 0 novo" && pass "lista vazia legitima = green" || { fail "lista vazia virou NAO-AVALIADO (empty confundido com unavailable)"; printf '%s\n' "$out" | sed 's/^/      /'; }
printf '%s' "$out" | grep -q "NAO-AVALIADO" && fail "imprimiu NAO-AVALIADO com API OK" || pass "nao confundiu vazio com indisponivel"
[ "$rc" = 0 ] && pass "exit 0 (verde de verdade)" || fail "exit=$rc (esperado 0)"

echo "== cenario 9 (EG-2, criterio §10): analise VELHA != tip => INCONCLUSIVO (nao green, nao red) =="
make_fake_gh "$TMP" stale
out=$(run_gate "$TMP"); rc=$?
printf '%s' "$out" | grep -q "INCONCLUSIVO — main: analise stale" && pass "analise velha sinalizada" || { fail "stale nao sinalizado"; printf '%s\n' "$out" | sed 's/^/      /'; }
printf '%s' "$out" | grep -q "os dois gates verdes" && fail "imprimiu 'verdes' com analise velha" || pass "nao imprime 'verdes' com analise velha"
[ "$rc" = 2 ] && pass "exit 2 (nao bloqueia, mas nao certifica)" || fail "exit=$rc (esperado 2)"

echo "== cenario 10 (EG-2, criterio §10): SEM analise (analyses vazio) => INCONCLUSIVO =="
make_fake_gh "$TMP" noana
out=$(run_gate "$TMP"); rc=$?
printf '%s' "$out" | grep -q "analise no-analysis" && pass "ausencia de analise sinalizada" || { fail "no-analysis nao sinalizado"; printf '%s\n' "$out" | sed 's/^/      /'; }
[ "$rc" = 2 ] && pass "exit 2" || fail "exit=$rc (esperado 2)"

echo "== cenario 11 (#604): alerta de OUTRA ferramenta (SARIF do Debt Scout) nao conta como CodeQL =="
make_fake_gh "$TMP" othertool
out=$(run_gate "$TMP"); rc=$?
printf '%s' "$out" | grep -q "KOF-DEBT-SATD-001" && { fail "alerta nao-CodeQL entrou no gate (tool_name ausente)"; printf '%s\n' "$out" | sed 's/^/      /'; } || pass "nota do Debt Scout ignorada pelo gate do CodeQL"
printf '%s' "$out" | grep -q "green — lab: 0 novo" && pass "lab green (so o #883 do baseline conta)" || { fail "lab nao ficou green"; printf '%s\n' "$out" | sed 's/^/      /'; }
[ "$rc" = 0 ] && pass "exit 0" || fail "exit=$rc (esperado 0)"

echo "== cenario 12 (#604): analise mais nova de OUTRA ferramenta nao certifica o tip =="
make_fake_gh "$TMP" othertool_ana
out=$(run_gate "$TMP"); rc=$?
printf '%s' "$out" | grep -q "INCONCLUSIVO — main: analise stale" && pass "amarrado a analise do CodeQL (velha), nao a do Debt Scout" || { fail "a analise de outra ferramenta certificou o tip"; printf '%s\n' "$out" | sed 's/^/      /'; }
[ "$rc" = 2 ] && pass "exit 2 (nao certifica)" || fail "exit=$rc (esperado 2)"

echo "== cenario 13 (#605): nenhum 'gh api \"/...' (Git Bash/MSYS reescreve /repos/... como caminho de arquivo) =="
if grep -nE 'gh api "?/' "$GATE" >/dev/null; then
  fail "endpoint com barra inicial voltou (quebra no Windows/Git Bash):"; grep -nE 'gh api "?/' "$GATE" | sed 's/^/      /'
else
  pass "todos os endpoints sem barra inicial (portavel no Git Bash)"
fi

rm -rf "$TMP"

if [ "$FAILED" = 1 ]; then
  echo "== RESULTADO: FALHOU =="
  exit 1
fi
echo "== RESULTADO: todos os cenarios OK =="
