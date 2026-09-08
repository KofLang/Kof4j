#!/usr/bin/env bash
# Gate REFACTOR-500: nenhuma classe acima de 500 linhas.
# Uso: scripts/check_500.sh [limite]   (padrão 500)
# Sai 1 se houver violação, listando os arquivos.
set -uo pipefail

LIMIT="${1:-500}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

violations=$(find "$ROOT"/kof-*/src/main/java -name '*.java' -exec wc -l {} + \
    | awk -v lim="$LIMIT" '$1 > lim && $2 != "total" {print $1"\t"$2}' \
    | sort -rn)

if [ -n "$violations" ]; then
    echo "check_500: FALHOU — classes acima de $LIMIT linhas:"
    printf '%s\n' "$violations" | awk -F'\t' '{printf "  %5d  %s\n", $1, $2}'
    exit 1
fi

echo "check_500: OK — nenhuma classe acima de $LIMIT linhas."
