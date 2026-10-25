#!/usr/bin/env bash
# check_plan_index.sh — a linha de índice §1 do `docs/development/README.md`+PT de um
# plano cujos blocos de fatia são prepend (mais novo primeiro) tem que citar a fatia
# MAIS NOVA do corpo do plano. Motivo (#769, lane docs/lab, 12-13/10): a linha 0f
# (`image-vision-plan`) ficou atrás do corpo do plano em QUATRO ciclos consecutivos
# (2a→2d→2e→4a→4b; depois 3ai→3aj→3al→3an) porque a lane image-vision pousa ~2 fatias
# por ciclo e a curadoria da linha não acompanhava. Cada deriva foi uma correção manual
# de doc-truth; este gate tranca a fonte.
#
# ESCOPO CONSERVADOR (evita falso-positivo): só os planos cujo CORPO ordena as fatias
# do mais novo para o mais antigo (blocos prepend) E cuja linha usa o marcador de span
# "1x–<token> LANDED/ LANDADAS". Hoje só `image-vision-plan` satisfaz isso de forma
# mecânica — os outros planos (entity-history, memory-safety, qrcode-wasm, wasm-wasi)
# têm prosa heterogênea (tokens "3x-edge", "15.3d-inc2", "Phase 2 slices 2a", ordem não
# estritamente prepend), então um parser ingênuo os quebraria; ficam FORA de propósito
# até que a lane dona padronize — um gate ruidoso seria pior que a deriva.
#
# Fonte da verdade = o CORPO do plano (o primeiro bloco de fatia, que a lane prepend).
# Uso: scripts/check_plan_index.sh   (rc=0 ok / rc=1 deriva)
set -u
cd "$(dirname "$0")/.."

ok=1

# image-vision: blocos "AVIF slice <tok> LANDED|FIXED", body prepend (topo = mais novo).
# A fatia mais nova pode ser FIXED (ex. 3ap = o fix do §689), não só LANDED — casar os dois
# (blind spot corrigido 13/10: só LANDED deixava o gate cego a um fix recente).
PLAN=docs/image-vision-plan.md
TOK=$(grep -oE "AVIF slice [0-9]+[a-z]* (LANDED|FIXED)" "$PLAN" | head -1 | grep -oE "[0-9]+[a-z]*")
if [ -z "$TOK" ]; then
  echo "PLAN-INDEX: SKIP — $PLAN sem bloco 'AVIF slice <tok> LANDED|FIXED' (formato mudou?)"
else
  # EN row 0f: "slices 1b–<tok> LANDED"; PT row 0f: "fatias 1b–<tok> LANDADAS".
  for spec in "docs/development/README.md:1b–${TOK} LANDED:EN" \
              "docs/development/README.pt_BR.md:1b–${TOK} LANDADAS:PT"; do
    f="${spec%%:*}"; rest="${spec#*:}"; needle="${rest%:*}"; lang="${rest##*:}"
    row=$(grep -m1 '^| 0f |' "$f" || true)
    if [ -z "$row" ]; then
      echo "PLAN-INDEX: FAIL — $f sem linha '| 0f |'"
      ok=0
    elif ! printf '%s' "$row" | grep -qF -- "$needle"; then
      want=$(printf '%s' "$row" | grep -oE "(slices|fatias) 1b[–-][0-9]+[a-z]*" | head -1)
      echo "PLAN-INDEX: FAIL — $f ($lang) linha 0f diz '$want' mas o corpo do plano tem a fatia MAIS NOVA '$TOK' (esperava '$needle')"
      ok=0
    fi
  done
  [ "$ok" = 1 ] && echo "PLAN-INDEX: OK — linha 0f cita a fatia mais nova do plano ('$TOK') EN+PT"
fi

exit $((1 - ok))
