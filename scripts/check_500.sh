#!/usr/bin/env bash
# Gate REFACTOR-500 (decisão da mantenedora, 13/09): alvo ≤500 linhas/classe;
# 500–599 é TOLERADO (dívida viva — o gate AVISA, não falha); ≥600 é CRÍTICO
# (falha o CI, refactor/split obrigatório antes do merge).
#
# MODO RATCHET (12/9, §140): dívida listada em
# scripts/check_500-baseline.txt nunca deve crescer dentro da faixa tolerada —
# crescimento é AVISADO nominalmente (é o refactor adiado indo em direção a
# 600). Classes ≥600 travadas no baseline são críticos avós: congeladas no
# número do dia (não podem crescer) e listadas como pendência obrigatória.
#   - PASSA quando a dívida diminui, mas AVISA (o split feito deve ser
#     removido do baseline: ./scripts/check_500.sh --update-baseline).
# Uso: scripts/check_500.sh [--update-baseline]
set -uo pipefail

LIMIT=500    # alvo da regra — acima disto é dívida (aviso)
CRITICAL=600 # 13/09: >=600 = crítico, refactor obrigatório (falha o build)
ROOT="${CHECK500_ROOT:-$(cd "$(dirname "$0")/.." && pwd)}"
BASELINE="${CHECK500_BASELINE:-$ROOT/scripts/check_500-baseline.txt}"

current=$(find "$ROOT"/kof-*/src/main/java -name '*.java' -exec wc -l {} + \
    | awk -v lim="$LIMIT" -v root="$ROOT/" '$1 > lim && $2 != "total" {gsub(root, "", $2); print $1"\t"$2}' \
    | sort -k2)

# Selftest (guarda do guarda): prova que o gate ainda distingue as tres faixas
# num sandbox, entao o CI nunca passa por acidente. Nao toca o repo real.
if [ "${1:-}" = "--selftest" ]; then
    T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
    mk() { mkdir -p "$(dirname "$1")"; awk -v n="$2" 'BEGIN{for(i=0;i<n;i++)print "// x"}' > "$1"; }
    run() { CHECK500_ROOT="$T" CHECK500_BASELINE="$T/base.txt" bash "$0" >/dev/null 2>&1; echo $?; }
    note() { printf '%s\t%s\n' "$1" "$2" > "$T/base.txt"; }
    : > "$T/base.txt"   # baseline vazio: o gate exige que o arquivo exista
    # 1. <= 500 linhas: verde, mesmo com baseline vazio
    mk "$T/kof-x/src/main/java/a/Ok.java" 480
    [ "$(run)" = 0 ] || { echo "SELFTEST FALHOU: <=500 devia passar"; exit 1; }
    # 2. avo critico congelado no baseline: verde (pendencia tolerada)
    mk "$T/kof-x/src/main/java/a/Big.java" 610
    if [ "$(run)" = 0 ]; then echo "SELFTEST FALHOU: critico novo (>=600) sem baseline devia falhar"; exit 1; fi
    note 610 kof-x/src/main/java/a/Big.java
    [ "$(run)" = 0 ] || { echo "SELFTEST FALHOU: avo critico congelado devia passar"; exit 1; }
    # 3. crescimento do avo critico: vermelho
    mk "$T/kof-x/src/main/java/a/Big.java" 640
    if [ "$(run)" = 0 ]; then echo "SELFTEST FALHOU: crescimento do avo critico devia falhar"; exit 1; fi
    echo "SELFTEST OK: <=500 verde; critico novo falha; avo congelado verde; crescimento do avo falha"
    exit 0
fi

if [ "${1:-}" = "--update-baseline" ]; then
    printf '%s\n' "$current" > "$BASELINE"
    echo "check_500: baseline atualizado ($(printf '%s\n' "$current" | grep -c . || true) dívidas)."
    exit 0
fi

if [ ! -f "$BASELINE" ]; then
    echo "check_500: FALHOU — baseline de dívida ausente ($BASELINE)."
    exit 1
fi

fail=0
note=""
while IFS=$'\t' read -r count file; do
    [ -z "$file" ] && continue
    base=$(awk -F'\t' -v f="$file" '$2==f {print $1}' "$BASELINE")
    if [ "$count" -ge "$CRITICAL" ]; then
        if [ -n "$base" ] && [ "$base" -ge "$CRITICAL" ]; then
            # avô crítico: congelado no número do baseline, não pode crescer
            if [ "$count" -gt "$base" ]; then
                echo "check_500: FALHOU — avô crítico $file cresceu $base -> $count (>= $CRITICAL): split obrigatório."
                fail=1
            else
                echo "check_500: avô crítico $file: $count linhas (>= $CRITICAL; congelado em $base, pendência de split planejado)."
            fi
        else
            # novo crítico OU baselizado <600 que cresceu até o crítico
            if [ -z "$base" ]; then
                echo "check_500: FALHOU — $file tem $count linhas (>= $CRITICAL): classe NOVA crítica, split antes do merge."
            else
                echo "check_500: FALHOU — $file tinha $base (< $CRITICAL) no baseline, agora $count (>= $CRITICAL): cruzou a linha vermelha, split obrigatório."
            fi
            fail=1
        fi
    elif [ -z "$base" ]; then
        echo "check_500: dívida tolerada (nova) — $file tem $count linhas (alvo $LIMIT; tolerado até $((CRITICAL-1)))."
    elif [ "$count" -gt "$base" ]; then
        echo "check_500: aviso — $file cresceu $base -> $count (tolerado até $((CRITICAL-1)); a $((CRITICAL-count)) linhas do crítico — planeje o split)."
    elif [ "$count" -lt "$base" ]; then
        note="$note\n    $file: $base -> $count (remova do baseline com --update-baseline)"
    fi
done <<< "$current"

while IFS=$'\t' read -r count file; do
    [ -z "$file" ] && continue
    if ! grep -qF "	$file" <<< "$current"; then
        note="$note\n    $file: $count -> 0 (split concluído! remova do baseline)"
    fi
done < <(grep -v '^#' "$BASELINE")

if [ "$fail" -ne 0 ]; then
    echo "check_500: FALHOU — classe crítica (>= $CRITICAL linhas); refactor/split antes do merge."
    exit 1
fi

if [ -n "$note" ]; then
    echo "check_500: OK (dívida diminuiu — atualize o baseline):"
    printf "$note\n"
else
    echo "check_500: OK — nenhuma classe crítica (>= $CRITICAL)."
fi
