#!/usr/bin/env bash
#
# test-suite-profile.sh — Fase 1 (profiling) do plano de arquitetura de testes
# (`docs/development/test-architecture-plan.md`, `D-TEST-ARCHITECTURE-GO`).
#
# READ-ONLY: NAO executa a suite; le os relatorios Surefire (TEST-*.xml) ja
# presentes na arvore e ranqueia os testes/classes/modulos mais lentos, os
# totais e a duracao por modulo. Sem relatorio = nao certifica (rc 3) — nunca
# inventa numeros (mesma disciplina do `stability-report.sh`).
#
# Uso:
#   scripts/test-suite-profile.sh [--root DIR] [--top N] [--md FILE] [--quiet]
# rc: 0 medido · 2 uso invalido · 3 nenhum relatorio encontrado.
set -uo pipefail
export LC_ALL=C

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
TOP=20; MD=""; QUIET=0
while [ $# -gt 0 ]; do
  case "$1" in
    --root) ROOT="$2"; shift ;;
    --top) TOP="$2"; shift ;;
    --md) MD="$2"; shift ;;
    --quiet) QUIET=1 ;;
    *) echo "uso: $0 [--root DIR] [--top N] [--md FILE] [--quiet]" >&2; exit 2 ;;
  esac
  shift
done

# attr NAME TAG -> valor do atributo, tolerante a ordem e a atributos extras.
attr() { printf '%s' "$2" | sed -n "s/.* $1=\"\([^\"]*\)\".*/\1/p" | head -1; }

mapfile -t REPORTS < <(find "$ROOT" -path '*/target/surefire-reports/TEST-*.xml' 2>/dev/null | sort -u)
if [ "${#REPORTS[@]}" -eq 0 ]; then
  echo "SUITE-PROFILE: unknown — nenhum relatorio Surefire (TEST-*.xml) sob $ROOT" >&2
  exit 3
fi

TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
: > "$TMP/tests.tsv"; : > "$TMP/classes.tsv"; : > "$TMP/mods.tsv"
T=0; F=0; E=0; S=0; TIME="0"
for f in "${REPORTS[@]}"; do
  rel="${f#"$ROOT"/}"; rel="${rel#./}"
  mod="${rel%%/target/*}"
  suite="$(grep -m1 '<testsuite ' "$f")"
  [ -z "$suite" ] && continue
  t="$(attr tests "$suite")"; fa="$(attr failures "$suite")"; er="$(attr errors "$suite")"; sk="$(attr skipped "$suite")"
  ct="$(attr time "$suite")"; cn="$(attr name "$suite")"
  T=$(( T + ${t:-0} )); F=$(( F + ${fa:-0} )); E=$(( E + ${er:-0} )); S=$(( S + ${sk:-0} ))
  TIME="$(awk -v a="$TIME" -v b="${ct:-0}" 'BEGIN{printf "%.3f", a+b}')"
  printf '%s\t%s\t%s\n' "${ct:-0}" "${cn:-?}" "$mod" >> "$TMP/classes.tsv"
  printf '%s\t%s\n' "${ct:-0}" "$mod" >> "$TMP/mods.tsv"
  grep -oE '<testcase [^>]*>' "$f" | while IFS= read -r tc; do
    printf '%s\t%s\t%s\t%s\n' "$(attr time "$tc")" "$(attr classname "$tc")" "$(attr name "$tc")" "$mod"
  done >> "$TMP/tests.tsv"
done

NTESTS="$(wc -l < "$TMP/tests.tsv" | tr -d ' ')"
NCLASSES="$(wc -l < "$TMP/classes.tsv" | tr -d ' ')"
NMODS="$(cut -f4 "$TMP/tests.tsv" | sort -u | wc -l | tr -d ' ')"
[ "${NMODS:-0}" -eq 0 ] && NMODS="$(wc -l < "$TMP/mods.tsv" | tr -d ' ')"

emit_summary() {
  echo "SUITE-PROFILE: tests=$NTESTS failures=$F errors=$E skipped=$S time=${TIME}s modules=$NMODS classes=$NCLASSES reports=${#REPORTS[@]}"
  echo
  echo "TOP $TOP SLOWEST TESTS:"
  sort -k1,1rn "$TMP/tests.tsv" | head -n "$TOP" | awk -F'\t' '{printf "%2d. %9ss  %s#%s  [%s]\n", NR, $1, $2, $3, $4}'
  echo
  echo "TOP $TOP SLOWEST CLASSES:"
  sort -k1,1rn "$TMP/classes.tsv" | head -n "$TOP" | awk -F'\t' '{printf "%2d. %9ss  %s  [%s]\n", NR, $1, $2, $3}'
  echo
  echo "TIME BY MODULE:"
  awk -F'\t' '{s[$2]+=$1} END{for(m in s) printf "%.3f\t%s\n", s[m], m}' "$TMP/mods.tsv" \
    | sort -k1,1rn | awk -F'\t' '{printf "  %9ss  %s\n", $1, $2}'
}

emit_md() {
  echo "**Measured:** \`tests=$NTESTS failures=$F errors=$E skipped=$S time=${TIME}s\` over $NMODS module(s) / $NCLASSES class(es) / ${#REPORTS[@]} Surefire report(s)."
  echo
  echo "### $TOP slowest test cases"
  echo
  echo "| # | Time (s) | Test | Module |"
  echo "|---|---------:|------|--------|"
  sort -k1,1rn "$TMP/tests.tsv" | head -n "$TOP" | awk -F'\t' '{printf "| %d | %s | `%s#%s` | `%s` |\n", NR, $1, $2, $3, $4}'
  echo
  echo "### $TOP slowest classes"
  echo
  echo "| # | Time (s) | Class | Module |"
  echo "|---|---------:|-------|--------|"
  sort -k1,1rn "$TMP/classes.tsv" | head -n "$TOP" | awk -F'\t' '{printf "| %d | %s | `%s` | `%s` |\n", NR, $1, $2, $3}'
  echo
  echo "### Time by module"
  echo
  echo "| Time (s) | Module |"
  echo "|---------:|--------|"
  awk -F'\t' '{s[$2]+=$1} END{for(m in s) printf "%.3f\t%s\n", s[m], m}' "$TMP/mods.tsv" \
    | sort -k1,1rn | awk -F'\t' '{printf "| %s | `%s` |\n", $1, $2}'
}

if [ -n "$MD" ]; then
  emit_md > "$MD"
  [ "$QUIET" -eq 0 ] && echo "SUITE-PROFILE: gravado $MD"
else
  emit_summary
fi
