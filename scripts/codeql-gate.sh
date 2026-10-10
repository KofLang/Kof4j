#!/usr/bin/env bash
#
# codeql-gate.sh — simula os DOIS gates da aba "Security and quality" do
# GitHub ANTES do commit/push (diretiva da mantenedora, 15/09; registro em
# docs/development/DECISIONS.md §D-GATE).
#
# #555 (20/09) — NOVO CONTRATO: o gate nao e mais "zero alertas open ou
# skip generalizado". Ele falha APENAS por alerta NOVO (id/rule+path fora do
# scripts/codeql-baseline.txt). O baseline contem somente achados JA TRIADOS
# (dismissed com motivo real ou fix aguardando re-scan), cada linha com dono,
# data de revisao e o porque. CODEQL_GATE_SKIP agora exige um MOTIVO
# nao-vazio, grita um banner, deixa rastro em .git/codeql-gate-skips.log e e
# IGNORADO dentro de CI (a porta la e o proprio scan do codeql.yml).
#
#   GATE 1 (security/code-scanning): nenhum alerta CodeQL novo, aberto e fora
#     do baseline. Armadilha 15/09: o filtro `?state=open` da API engana —
#     alertas recem-criados retornam `state: null` e SO aparecem na UI; o gate
#     conta como open: state=="open" OU (state==null E sem fixed_at/dismissed_at).
#     EG-2 (§10, 20/09): a falha de API e rastreada em `api_ok`, NUNCA inferida
#     de lista vazia — uma lista legitimamente vazia (0 alertas) e GREEN, nao
#     INCONCLUSIVO (criterio "empty API != unavailable API"). O veredito e
#     AMARRADO AO SHA analisado: a analise mais recente do branch (analyses
#     API) e comparada com o tip (branches API); analise velha/ausente vira
#     INCONCLUSIVO (rc=2, nao bloqueia), nunca green (criterio "stale analysis
#     cannot decide a new commit").
#   GATE 2 (security/quality): build verde SEM stubs ECJ + check_500.sh (so
#     sem --fast). A suite completa continua sendo porta de merge (Q1/Q2).
#
# Uso: scripts/codeql-gate.sh [--fast]   (--fast: pula o build, so API+hooks)
# Exit: 0 = gates verdes; 1 = alerta novo/vermelho (NAO COMMITAR); 2 = API
#       indisponivel (INCONCLUSIVO — o CI e a porta real).
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"

REPO="${CODEQL_GATE_REPO:-KofLang/Kof4j}"
# #604: code scanning holds SARIF from MORE than one tool (the Debt Scout
# uploads note-level C0/C1 signals under /debt-scout/*). This gate judges
# CodeQL only — every alerts/analyses query filters by tool, otherwise a
# non-security note turns it RED and another tool's analysis can certify
# a tip that CodeQL never analyzed.
TOOL="CodeQL"
BASELINE="${CODEQL_BASELINE_FILE:-scripts/codeql-baseline.txt}"
BRANCHES=("main" "lab" "testing" "prerelease" "stable")
FAILED=0
GATE1_OK=1
GATE1_STALE=0
api_ok=1

# ── CODEQL_GATE_SKIP — escapamento visivel, nunca silencioso (R6) ──────────
# Formato novo: CODEQL_GATE_SKIP="<motivo>". Sem motivo, o skip NAO vale.
# Em CI (GITHUB_ACTIONS=true) o skip e IGNORADO: la quem decide e o scan.
skip_var="${CODEQL_GATE_SKIP:-}"
if [ -n "$skip_var" ]; then
  if [ "${GITHUB_ACTIONS:-}" = "true" ]; then
    echo "!! CODEQL_GATE_SKIP ignorado em CI (GITHUB_ACTIONS=true) — rodando o gate de verdade"
  elif [ "$skip_var" = "1" ] || [ "$skip_var" = "true" ]; then
    echo "!! CODEQL_GATE_SKIP='$skip_var' NAO VALE MAIS (portao #555): use CODEQL_GATE_SKIP=\"<motivo real de uma frase>\"."
    echo "!! Rodando o gate de qualquer jeito."
  else
    root="$(git rev-parse --show-toplevel)"
    logf="${CODEQL_GATE_SKIP_LOG:-$root/.git/codeql-gate-skips.log}"
    printf '%s\t%s\t%s\t%s\t%s\n' "$(date -u +%FT%TZ)" "$(git rev-parse --short HEAD 2>/dev/null || echo '?')" "$(git branch --show-current 2>/dev/null || echo '?')" "$(git config user.name 2>/dev/null || echo '?')" "$skip_var" >> "$logf"
    echo "########################################################################"
    echo "# !!! CODEQL GATE PULADO COM MOTIVO — registro deixado em:            #"
    echo "#     $logf"
    echo "# !!! motivo: $skip_var"
    echo "# !!! Um skip por push virou CULTURA (#555). Se voce esta aqui de novo,"
    echo "# !!! a resposta certa e fechar o alerta (fix ou dismiss justificado na"
    echo "# !!! API) ou adicionar TRIAGEM feita ao baseline — nunca pular de novo."
    echo "########################################################################"
    exit 0
  fi
fi

# ── baseline: tolerated = id OU (rule + glob de path) ──────────────────────
BASE_IDS=""
BASE_RULE_PATHS=""
if [ -f "$BASELINE" ]; then
  BASE_IDS=$(grep -v '^#' "$BASELINE" | awk -F'\t' 'NF>1 {print $1}' | grep -E '^[0-9]+$' || true)
  BASE_RULE_PATHS=$(grep -v '^#' "$BASELINE" | awk -F'\t' 'NF>1 {print $2"|"$3}' || true)
else
  echo "aviso: baseline nao encontrado em $BASELINE — TODO alerta open contara como novo"
fi

tolerated() { # $1=alert-num $2=rule $3=path
  local n="$1" rule="$2" path="$3" rp br bp
  printf '%s\n' "$BASE_IDS" | grep -qx -- "$n" && return 0
  while IFS='|' read -r br bp; do
    [ -z "$br" ] && continue
    if [ "$rule" = "$br" ]; then
      # shellcheck disable=SC2254
      case "$path" in $bp) return 0 ;; esac
    fi
  done <<< "$BASE_RULE_PATHS"
  return 1
}

echo "== GATE 1: CodeQL alerts (security/code-scanning) — baseline: $BASELINE =="
# VERDADE NO NIVEL DO ALERTA (licao 15/09, corrige falso RED de 412): contagem
# por INSTANCIA acusa open ate alerta dismissado. O estado vigente e o `state`
# do /alerts/{n}: "open" (sem dismissed_at/fixed_at) OU `null` recem-criado sem
# datas = aberto. O LIST pagina com atraso — unimos com `?state=open&ref=`.
# CUSTO (licao 15/09 ~18:40): NUNCA 1 GET/alerta x 731 (8min de hang); o list
# paginado vem com state/dismissed_at/fixed_at/most_recent_instance — 1 chamada
# resolve; 1 GET individual so para os `null` que o list escondeu.
ROWS=$(gh api "repos/$REPO/code-scanning/alerts?per_page=100&tool_name=$TOOL" --paginate \
  --jq '.[] | [(.number|tostring), (.state // "null"), (.dismissed_at // "-"), (.fixed_at // "-"), (.most_recent_instance.ref // "-"), (.rule.id), ((.most_recent_instance.location.path // "-") + ":" + ((.most_recent_instance.location.start_line // "-")|tostring))] | @tsv' 2>/dev/null) \
  || { echo "  [aviso] API indisponivel (rate limit?) — GATE 1 NAO verificado; o CI (codeql.yml) continua sendo a porta real"; api_ok=0; ROWS=""; }

if [ -n "$ROWS" ]; then
  for br in "${BRANCHES[@]}"; do
    nums=$(gh api "repos/$REPO/code-scanning/alerts?ref=refs/heads/$br&state=open&per_page=100&tool_name=$TOOL" --paginate --jq '.[].number' 2>/dev/null) || api_ok=0
    for n in $nums; do
      printf '%s\n' "$ROWS" | cut -f1 | grep -qx "$n" && continue
      extra=$(gh api "repos/$REPO/code-scanning/alerts/$n" \
        --jq '[(.number|tostring), (.state // "null"), (.dismissed_at // "-"), (.fixed_at // "-"), (.most_recent_instance.ref // "-"), .rule.id, ((.most_recent_instance.location.path // "-") + ":" + ((.most_recent_instance.location.start_line // "-")|tostring))] | @tsv' 2>/dev/null) || api_ok=0
      [ -n "$extra" ] && ROWS="$ROWS
$extra"
    done
  done
fi

for br in "${BRANCHES[@]}"; do
  if [ "$api_ok" = 0 ]; then
    echo "  NAO-AVALIADO — $br (API fora; nada foi verificado)"
    GATE1_OK=0
    continue
  fi
  new_n=0; tol_n=0; new_list=""
  while IFS=$'\t' read -r n st dis fix ref rule loc; do
    [ "$ref" = "refs/heads/$br" ] || continue
    isopen=0
    [ "$st" = "open" ] && isopen=1
    [ "$st" = "null" ] && [ "$dis" = "-" ] && [ "$fix" = "-" ] && isopen=1
    [ "$isopen" = 1 ] || continue
    path="${loc%:*}"
    if tolerated "$n" "$rule" "$path"; then
      tol_n=$((tol_n+1))
    else
      new_n=$((new_n+1))
      [ "$new_n" -le 25 ] && new_list="$new_list  #$n [$rule] $loc
"
    fi
  done <<< "$ROWS"
  # EG-2 (§10): bind the verdict to the ANALYZED SHA. A stale or absent
  # analysis cannot certify the current tip — that branch is INCONCLUSIVO
  # (rc=2, non-blocking, same contract as API-down), never green. A NEW alert
  # above still wins as RED. The analysis SHA comes from the code-scanning
  # analyses API; the tip from the branch API (one extra call per branch).
  sha_api=1
  ana=$(gh api "repos/$REPO/code-scanning/analyses?ref=refs/heads/$br&per_page=1&tool_name=$TOOL" \
    --jq '.[0].commit_sha' 2>/dev/null) || { sha_api=0; ana=""; }
  tip=$(gh api "repos/$REPO/branches/$br" --jq '.commit.sha' 2>/dev/null) || { sha_api=0; tip=""; }
  sha_state=current
  if [ "$sha_api" = 0 ]; then sha_state=unavailable
  elif [ -z "$ana" ]; then sha_state=no-analysis
  elif [ -n "$tip" ] && [ "$ana" != "$tip" ]; then sha_state=stale
  fi
  [ "$sha_state" = current ] || GATE1_STALE=1
  if [ "$new_n" -gt 0 ]; then
    echo "  RED — $new_n alerta(s) NOVO(s) sem baseline na branch $br (top 25):"
    printf '%s' "$new_list" | sed 's/^/  /'
    echo '    => feche na raiz (fix, ou dismiss justificado via POST /code-scanning/alerts/<n>)'
    echo '    => ou registre a TRIAGEM feita no baseline com dono+revisao (scripts/codeql-baseline.txt)'
    FAILED=1
  elif [ "$sha_state" != current ]; then
    echo "  INCONCLUSIVO — $br: analise $sha_state (analyzed ${ana:-<none>} vs tip ${tip:-?}); o veredito NAO certifica o tip"
  else
    echo "  green — $br: 0 novo (0 fora do baseline; tolerados no baseline: $tol_n; analysis == tip ${tip:0:7})"
  fi
done

if [ "${1:-}" != "--fast" ]; then
  echo "== GATE 2: build sem stubs ECJ (security/quality) =="
  if timeout 900 mvn -o -q -pl kof-compiler,kof-cli,kof-script,kof-c-compiler -am compile > /tmp/codeql-gate-build.log 2>&1; then
    stubs=$(for c in $(git diff --name-only HEAD~1 HEAD 2>/dev/null | grep 'src/main.*\.java$' | sed 's|src/main/java/||; s|\.java$||'); do
      find . -path "*/target/classes/${c}.class" 2>/dev/null
    done | head -50)
    bad=0
    for cls in $stubs; do
      if javap -c -p "$cls" 2>/dev/null | grep -q "Unresolved compilation"; then
        echo "  STUB ECJ: $cls"; bad=1
      fi
    done
    [ "$bad" = 1 ] && FAILED=1
    [ "$bad" = 0 ] && echo "  green — compile 4 modulos, 0 stubs nas classes tocadas"
  else
    echo "  RED — build falhou (ver /tmp/codeql-gate-build.log)"; FAILED=1
  fi
  echo "== GATE 2b: check_500 =="
  if ! scripts/check_500.sh > /tmp/codeql-gate-500.log 2>&1; then
    echo "  RED — check_500 falhou (tail abaixo)"; tail -5 /tmp/codeql-gate-500.log | sed 's/^/    /'; FAILED=1
  else
    echo "  green — check_500"
  fi
fi

if [ "$FAILED" = 1 ]; then
  echo "== RESULTADO: PORTAO VERMELHO — alerta novo sem baseline; nao commitar sem fechar o achado =="
  exit 1
fi
if [ "$GATE1_OK" = 0 ]; then
  echo "== RESULTADO: INCONCLUSIVO — GATE 1 nao avaliado (API fora); os demais gates passaram =="
  exit 2
fi
if [ "$GATE1_STALE" = 1 ]; then
  echo "== RESULTADO: INCONCLUSIVO — veredito NAO amarrado ao SHA do tip (analise velha/ausente); nao e green =="
  exit 2
fi
echo "== RESULTADO: os dois gates verdes =="
