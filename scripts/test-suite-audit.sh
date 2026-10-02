#!/usr/bin/env bash
#
# test-suite-audit.sh — Fase 2 (quick wins: descoberta) do plano de arquitetura
# de testes (`docs/development/test-architecture-plan.md`, `D-TEST-ARCHITECTURE-GO`).
#
# READ-ONLY: varre as FONTES de teste (nunca executa a suite) atras dos alvos de
# quick win nomeados pelo plano: sites de `Thread.sleep`, classes de teste
# grandes e nomes de metodo de teste repetidos entre classes. Sem fonte de teste
# = nao certifica (rc 3).
#
# Uso:
#   scripts/test-suite-audit.sh [--root DIR] [--md FILE] [--top N] [--quiet]
#   scripts/test-suite-audit.sh [--root DIR] --keys FILE   # chaves maquina
#   scripts/test-suite-audit.sh [--root DIR] --citations [--cite-docs DIR]
#   scripts/test-suite-audit.sh [--root DIR] --dups          # nomes duplicados + classes
# rc: 0 medido · 2 uso invalido · 3 nenhuma fonte de teste.
set -uo pipefail
export LC_ALL=C

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
MD=""; TOP=20; QUIET=0; BIG=500; KEYS=""; CITATIONS=0; DOCS=""; DUPS=0
while [ $# -gt 0 ]; do
  case "$1" in
    --root) ROOT="$2"; shift ;;
    --md) MD="$2"; shift ;;
    --top) TOP="$2"; shift ;;
    --keys) KEYS="$2"; shift ;;
    --citations) CITATIONS=1 ;;
    --cite-docs) DOCS="$2"; shift ;;
    --dups) DUPS=1 ;;
    --quiet) QUIET=1 ;;
    *) echo "uso: $0 [--root DIR] [--md FILE] [--top N] [--keys FILE] [--citations] [--cite-docs DIR] [--dups] [--quiet]" >&2; exit 2 ;;
  esac
  shift
done

mapfile -t SRC < <(find "$ROOT" -path '*/src/test/java/*' -name '*.java' 2>/dev/null | sort -u)
if [ "${#SRC[@]}" -eq 0 ]; then
  echo "TEST-AUDIT: unknown — nenhuma fonte de teste (*/src/test/java/*.java) sob $ROOT" >&2
  exit 3
fi

TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT

grep -nHE 'Thread\.sleep|TimeUnit\.[A-Za-z]+\.sleep' "${SRC[@]}" 2>/dev/null | sed "s#^$ROOT/##" > "$TMP/sleeps.txt" || true
NSLEEP="$(wc -l < "$TMP/sleeps.txt" | tr -d ' ')"

for f in "${SRC[@]}"; do
  n="$(wc -l < "$f")"
  [ "$n" -ge "$BIG" ] && printf '%s\t%s\n' "$n" "${f#"$ROOT"/}"
done | sort -rn > "$TMP/big.txt"
NBIG="$(wc -l < "$TMP/big.txt" | tr -d ' ')"

: > "$TMP/dups.tsv"
for f in "${SRC[@]}"; do
  cls="${f##*/}"; cls="${cls%.java}"
  grep -oE 'void [A-Za-z0-9_]+ *\(' "$f" 2>/dev/null | sed -E 's/void ([A-Za-z0-9_]+) *\(/\1/' | sort -u | while IFS= read -r m; do
    printf '%s\t%s\n' "$m" "$cls"
  done
done >> "$TMP/dups.tsv"
awk -F'\t' '{c[$1]=c[$1]" "$2; n[$1]++} END{for(m in c) if(n[m]>=2) print n[m]"\t"m"\t"c[m]}' "$TMP/dups.tsv" | sort -rn > "$TMP/dupclus.txt"
NDUP="$(wc -l < "$TMP/dupclus.txt" | tr -d ' ')"

# Exposicao a citacoes: quantos arquivos de doc citam o nome de cada classe de
# teste oversized. Fase 3 (split) exige varredura de doc quando o nome aparece;
# `0` = split mais barato (nenhuma citacao a corrigir). Mede, nunca edita.
emit_citations() {
  local docs="${DOCS:-$ROOT/docs}"
  [ -d "$docs" ] || return 0
  while IFS=$'\t' read -r _ f; do
    local cls="${f##*/}"; cls="${cls%.java}"
    local c; c="$(grep -rlF --include='*.md' -- "$cls" "$docs" 2>/dev/null | wc -l | tr -d ' ')"
    printf '%s\t%s\n' "$c" "$f"
  done < "$TMP/big.txt" | sort -n -k1,1 -k2,2
}

emit_md() {
  echo "**Measured:** \`sleeps=$NSLEEP oversized(>=${BIG})=$NBIG duplicate-across-classes=$NDUP\` over ${#SRC[@]} test source(s)."
  echo
  echo "### \`Thread.sleep\` sites ($NSLEEP)"
  echo
  echo '```'
  head -n "$TOP" "$TMP/sleeps.txt"
  echo '```'
  echo
  echo "### Test classes >= $BIG lines ($NBIG)"
  echo
  echo "| Lines | File |"
  echo "|------:|------|"
  head -n "$TOP" "$TMP/big.txt" | awk -F'\t' '{printf "| %s | `%s` |\n", $1, $2}'
  echo
  echo "### Test method names in >= 2 classes ($NDUP)"
  echo
  echo "> Cross-target parity clusters are EXPECTED (same face compiled on every backend); this is a lead, not a defect count."
  echo
  echo "| Classes | Method | Where |"
  echo "--------:|--------|-------|"
  head -n "$TOP" "$TMP/dupclus.txt" | awk -F'\t' '{printf "| %s | `%s` |%s |\n", $1, $2, $3}'
  echo
  echo "### Oversized test classes — doc citation exposure"
  echo
  echo "> Phase 3 splitting a class whose name is cited in docs needs a citation sweep; \`0\` = cheapest to split (no doc rename/drift). Ordered cheapest-first."
  echo
  echo '```'
  emit_citations | awk -F'\t' '{printf "%3d  %s\n", $1, $2}'
  echo '```'
}

emit_keys() {
  {
    sed 's#:.*##' "$TMP/sleeps.txt" | sort -u | awk 'BEGIN{OFS="\t"}{print "sleep",$0}'
    cut -f2 "$TMP/big.txt" | sort -u | awk 'BEGIN{OFS="\t"}{print "oversized",$0}'
    cut -f2 "$TMP/dupclus.txt" | sort -u | awk 'BEGIN{OFS="\t"}{print "dupname",$0}'
  } | sort -u
}

if [ "$CITATIONS" -eq 1 ]; then
  echo "OVERSIZED — exposicao a citacoes de doc (0 = split sem varredura de doc):"
  emit_citations | awk -F'\t' '{printf "  %3d  %s\n", $1, $2}'
  exit 0
fi

if [ -n "$KEYS" ]; then
  emit_keys > "$KEYS"
  [ "$QUIET" -eq 0 ] && echo "TEST-AUDIT: chaves gravadas em $KEYS"
  exit 0
fi

# READ-ONLY: para dimensionar a Fase 5 (harness cross-target) — cada nome de
# metodo repetido com as classes que o declaram. Sem julgamento de "paridade".
if [ "$DUPS" -eq 1 ]; then
  echo "DUPLICATE METHOD NAMES across >=2 classes (name<TAB>count<TAB>classes):"
  awk -F'\t' '{printf "%s\t%s\t%s\n", $2, $1, $3}' "$TMP/dupclus.txt"
  exit 0
fi

if [ -n "$MD" ]; then
  emit_md > "$MD"
  [ "$QUIET" -eq 0 ] && echo "TEST-AUDIT: gravado $MD"
else
  echo "TEST-AUDIT: sleeps=$NSLEEP oversized(>=$BIG)=$NBIG duplicate-across-classes=$NDUP sources=${#SRC[@]}"
  echo
  echo "SLEEPS (top $TOP):"
  head -n "$TOP" "$TMP/sleeps.txt" | sed 's/^/  /'
  echo
  echo "OVERSIZED >= $BIG lines (top $TOP):"
  head -n "$TOP" "$TMP/big.txt" | awk -F'\t' '{printf "  %5d  %s\n", $1, $2}'
  echo
  echo "DUPLICATE TEST NAMES across >=2 classes (top $TOP; parity clusters are expected):"
  head -n "$TOP" "$TMP/dupclus.txt" | awk -F'\t' '{printf "  %2d  %s\n", $1, $2}'
fi
