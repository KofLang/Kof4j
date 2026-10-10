#!/usr/bin/env bash
#
# check_test_hygiene.sh — ratchet de higiene de testes (Fase 2 do
# `test-architecture-plan`, `D-TEST-ARCHITECTURE-GO`).
#
# READ-ONLY sobre as FONTES de teste (nunca executa a suite, nunca toca o
# compilador): mede as chaves de violacao via `scripts/test-suite-audit.sh
# --keys` e RECUSA divida NOVA contra o baseline congelado. A divida existente
# fica congelada (ratchet); so melhora ou permanece — nunca cresce.
#
# Uso:
#   scripts/check_test_hygiene.sh [--root DIR] [--baseline FILE] [--quiet]
#   scripts/check_test_hygiene.sh [--root DIR] [--baseline FILE] --write-baseline
# rc: 0 ok · 1 divida nova · 2 uso invalido · 3 nao certificavel (sem fonte/baseline).
set -uo pipefail
export LC_ALL=C

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
BASELINE="$ROOT/scripts/test-hygiene-baseline.txt"
QUIET=0; WRITE=0
while [ $# -gt 0 ]; do
  case "$1" in
    --root) ROOT="$2"; shift ;;
    --baseline) BASELINE="$2"; shift ;;
    --write-baseline) WRITE=1 ;;
    --quiet) QUIET=1 ;;
    *) echo "uso: $0 [--root DIR] [--baseline FILE] [--quiet|--write-baseline]" >&2; exit 2 ;;
  esac
  shift
done

AUDIT="$ROOT/scripts/test-suite-audit.sh"
[ -f "$AUDIT" ] || AUDIT="scripts/test-suite-audit.sh"

TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
CUR="$TMP/current.keys"

if ! bash "$AUDIT" --root "$ROOT" --keys "$CUR" --quiet >/dev/null 2>&1; then
  echo "TEST-HYGIENE: unknown — auditoria nao certificavel (sem fonte de teste, ou $AUDIT ausente) sob $ROOT" >&2
  exit 3
fi
[ -s "$CUR" ] || { echo "TEST-HYGIENE: unknown — zero chaves medidas sob $ROOT" >&2; exit 3; }

if [ "$WRITE" -eq 1 ]; then
  {
    echo "# test-hygiene-baseline.txt — divida congelada do ratchet de higiene (Fase 2, D-TEST-ARCHITECTURE-GO)."
    echo "# Formato TSV: <tipo><TAB><chave>; tipo = sleep | oversized | dupname."
    echo "# Regenerar DELIBERADAMENTE so apos MELHORAR: scripts/check_test_hygiene.sh --write-baseline"
    echo "# Linhas com '#' e vazias sao ignoradas; a comparacao e por conjunto (sorted -u)."
    cat "$CUR"
  } > "$BASELINE"
  echo "TEST-HYGIENE: baseline gravado ($(grep -vc '^#' "$BASELINE") chaves) em ${BASELINE#"$ROOT"/}"
  exit 0
fi

[ -f "$BASELINE" ] || { echo "TEST-HYGIENE: unknown — baseline ausente ($BASELINE); gere com --write-baseline" >&2; exit 3; }
grep -v '^#' "$BASELINE" | grep -v '^$' | LC_ALL=C sort -u > "$TMP/baseline.keys"

NEW="$(comm -23 "$CUR" "$TMP/baseline.keys")"
GONE="$(comm -13 "$CUR" "$TMP/baseline.keys")"

if [ -n "$NEW" ]; then
  n="$(printf '%s\n' "$NEW" | wc -l | tr -d ' ')"
  echo "TEST-HYGIENE: FAIL — $n chave(s) de divida NOVA (ratchet: divida existente congelada, nao cresce):"
  printf '%s\n' "$NEW" | head -n 20 | awk -F'\t' '{printf "  %-10s %s\n", $1, $2}'
  [ "$n" -gt 20 ] && echo "  ... (+$((n - 20)))"
  exit 1
fi

if [ "$QUIET" -eq 0 ]; then
  g="$(printf '%s' "$GONE" | grep -c .)"
  echo "TEST-HYGIENE: OK — sem divida nova ($(wc -l < "$CUR" | tr -d ' ') chaves medidas, ${g} melhoria(s) vs baseline)"
  [ "$g" -gt 0 ] && echo "  (ratchet pode ser apertado: rode --write-baseline apos confirmar)"
fi
exit 0
