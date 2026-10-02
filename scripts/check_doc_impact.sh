#!/usr/bin/env bash
#
# check_doc_impact.sh — #648: uma mudança NORMATIVA (§NNN que troca de estado,
# decisão D-*, documento movido) não pode fechar com consumidor documental vivo
# desatualizado. Para cada token do diff, lista os arquivos `.md` que o citam e
# exige que CADA consumidor tenha sido tocado pelo MESMO diff (ou waiver).
#
# Uso real (manifesto do agent-verify / CI do candidato):
#   scripts/check_doc_impact.sh [--base SHA] [--head SHA]
# Saída fixture (testes):
#   scripts/check_doc_impact.sh --corpus DIR --changed-file F --tokens-file F
# Tokens (file: `kind<TAB>token`):
#   status  §NNN     — CLASSIFICACAO de estado de §NNN mudou (before x after via
#                      scripts/check_known_bugs_status.sh --classify — o classificador
#                      canonico; reescrita de heading NAO dispara, #656)
#   decision D-NAME  — cabeçalho `## D-NAME` mudou em DECISIONS
#   moved   <path>   — documento renomeado/movido (caminho ANTIGO; consumidores
#                      por caminho literal OU link relativo/basename, #656)
# Waivers (DOC_IMPACT_WAIVERS, default scripts/doc-impact-waivers.txt):
#   `<token|*><TAB><prefixo-do-consumidor>`  (linha malformada NAO waiva nada)
# Saída: `token -> consumidores -> PASS|BLOCK` por token (contrato #648/#656); exit 1 se qualquer BLOCK.
# História imutável (CHANGELOG/DOING/history) é waiveada por padrão — registro
# não é consumidor vivo (regra "State, not history").
set -uo pipefail
GATE_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
KB_CLASS="$GATE_DIR/check_known_bugs_status.sh"

BASE=""; HEADR="HEAD"; CHANGED=""; TOKENS=""; CORPUS="."; SELFTEST=0
WAIVERS="${DOC_IMPACT_WAIVERS:-scripts/doc-impact-waivers.txt}"
while [ $# -gt 0 ]; do case "$1" in
  --base) BASE="${2:-}"; shift 2;;
  --head) HEADR="${2:-}"; shift 2;;
  --changed-file) CHANGED="${2:-}"; shift 2;;
  --tokens-file) TOKENS="${2:-}"; shift 2;;
  --corpus) CORPUS="${2:-}"; shift 2;;
  --waivers) WAIVERS="${2:-}"; shift 2;;
  --selftest) SELFTEST=1; shift;;
  -h|--help) sed -n '2,20p' "$0"; exit 0;;
  *) echo "check_doc_impact: argumento desconhecido: $1" >&2; exit 2;;
esac; done

# --selftest: guarda-da-guarda. Prova, com fixtures plantadas, que o gate
# DISPARA quando um consumidor vivo cita o token mudado e NAO foi tocado no
# mesmo diff, e que ele PASSA quando o consumidor foi tocado (ou waivado).
# Roda no modo fixture (--corpus/--changed-file/--tokens-file) — nao usa git.
if [ "$SELFTEST" -eq 1 ]; then
  ST="$(mktemp -d)"; trap 'rm -rf "$ST"' EXIT
  mkdir -p "$ST/corpus"
  printf '# consumer\nconsumes §907 here\n' > "$ST/corpus/consumer.md"
  printf 'status\t§907\n' > "$ST/tokens.tsv"
  # 1) consumidor NAO tocado -> BLOCK (o defeito que o gate existe para pegar)
  : > "$ST/changed-empty"
  if bash "$0" --corpus "$ST/corpus" --changed-file "$ST/changed-empty" \
       --tokens-file "$ST/tokens.tsv" --waivers /dev/null >/dev/null 2>&1; then
    echo "SELFTEST FAIL: consumidor vivo nao atualizado passou (deveria BLOCK)"; exit 2
  fi
  # 2) consumidor tocado no MESMO diff -> PASS
  printf 'consumer.md\n' > "$ST/changed-ok"
  bash "$0" --corpus "$ST/corpus" --changed-file "$ST/changed-ok" \
    --tokens-file "$ST/tokens.tsv" --waivers /dev/null >/dev/null 2>&1 \
    || { echo "SELFTEST FAIL: consumidor atualizado ainda BLOCK (falso vermelho)"; exit 2; }
  # 3) diff sem nenhum token normativo -> PASS (nao inventa bloqueio)
  printf 'status\t§999\n' > "$ST/tokens-none.tsv"
  bash "$0" --corpus "$ST/corpus" --changed-file "$ST/changed-empty" \
    --tokens-file "$ST/tokens-none.tsv" --waivers /dev/null >/dev/null 2>&1 \
    || { echo "SELFTEST FAIL: §999 sem consumidor bloqueou (falso vermelho)"; exit 2; }
  echo "SELFTEST OK"
  exit 0
fi

status_tokens_from() { # $1=caminho-relativo-do-ledger — estado BEFORE x AFTER pelo classificador canonico
  local f="$1" b="$TMP/kb-b" a="$TMP/kb-a"
  : > "$b"; : > "$a"
  git show "$BASE:$f" > "$b" 2>/dev/null || true
  git show "$HEADR:$f" > "$a" 2>/dev/null || true
  { [ -s "$b" ] || [ -s "$a" ]; } || return 0
  bash "$KB_CLASS" --classify "$b" 2>/dev/null | sed 's/^/§/' | sort -u > "$TMP/sb"
  bash "$KB_CLASS" --classify "$a" 2>/dev/null | sed 's/^/§/' | sort -u > "$TMP/sa"
  { comm -23 "$TMP/sb" "$TMP/sa"; comm -13 "$TMP/sb" "$TMP/sa"; } \
    | sed -nE 's/^§([0-9]+) .*/status\t§\1/p'
}
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
CH="$TMP/changed"; : > "$CH"

if [ -z "$TOKENS" ]; then
  # modo real: deriva do diff git
  [ -n "$BASE" ] || BASE="$(git merge-base HEAD origin/lab 2>/dev/null || git rev-parse HEAD~1 2>/dev/null || echo '')"
  if [ -z "$BASE" ]; then echo "PASS (sem base para diff — nada a medir)"; exit 0; fi
  git diff --name-only "$BASE".."$HEADR" > "$CH" 2>/dev/null
  : > "$TMP/tokens"
  for f in docs/bugs-and-gaps/known-bugs.md docs/bugs-and-gaps/known-bugs.pt_BR.md; do
    status_tokens_from "$f"
  done >> "$TMP/tokens"
  git diff -U0 "$BASE".."$HEADR" -- docs/development/DECISIONS.md docs/development/DECISIONS.pt_BR.md \
    | grep -E '^[+-]' | grep -vE '^(\+\+\+|---)' | grep -E 'D-[A-Z0-9._-]+' \
    | awk '/SUPERSEDED|SUBSTITUIDA|SUBSTITUÍDA/{while (match($0,/D-[A-Z0-9._-]+/)) {print "superseded\t" substr($0,RSTART,RLENGTH); $0=substr($0,RSTART+RLENGTH)} next} /^[+-]## D-/{if (match($0,/D-[A-Z0-9._-]+/)) print "decision\t" substr($0,RSTART,RLENGTH)}' | sort -u >> "$TMP/tokens"
  git diff --name-status -M "$BASE".."$HEADR" | grep -E '^R[0-9]*\b' | while IFS=$'\t' read -r st old new; do
    printf 'moved\t%s\n' "$old"
  done >> "$TMP/tokens"
  TOKENS="$TMP/tokens"
else
  : > "$CH"  # modo fixture: --changed-file já popula abaixo
fi
[ -n "$CHANGED" ] && [ -f "$CHANGED" ] && cat "$CHANGED" >> "$CH"
[ -n "$TOKENS" ] && [ -f "$TOKENS" ] || TOKENS=/dev/null
sort -u "$TOKENS" -o "$TMP/tokens.u" 2>/dev/null || true
[ -f "$TMP/tokens.u" ] && TOKENS="$TMP/tokens.u"
sort -u "$CH" -o "$CH"

is_changed() { grep -qxF "$1" "$CH"; }
waived() { # token consumidor
  local tok="$1" con="$2" pre
  [ -f "$WAIVERS" ] || return 1
  while read -r pre; do
    [ -n "$pre" ] || continue
    case "$con" in "$pre"*) return 0;; esac
  done < <(awk -F'\t' -v t="$tok" '$1==t || $1=="*" {print $2}' "$WAIVERS" | grep -v '^#')
  return 1
}

consumers_for() { # kind token
  local kind="$1" tok="$2" base
  if [ "$kind" = "status" ]; then
    # §NNN casa o NUMERO INTEIRO: §12 NAO pega §123/§12.3 (lacuna 2b do #656)
    base="$(grep -rlE --include='*.md' -e "${tok}([^0-9.]|\$)" "$CORPUS" 2>/dev/null \
      | grep -v '^\.git/' | grep -v '/target/')"
  else
    base="$(grep -rlF --include='*.md' -e "$tok" "$CORPUS" 2>/dev/null \
      | grep -v '^\.git/' | grep -v '/target/')"
  fi
  if [ "$kind" = "moved" ]; then
    # consumidor tambem e quem cita so o basename (link relativo `](../old.md)`, #656)
    base="$base
$(grep -rlF --include='*.md' -e "$(basename "$tok")" "$CORPUS" 2>/dev/null \
      | grep -v '^\.git/' | grep -v '/target/')"
  fi
  printf '%s\n' "$base" | sed "s|^\./||; s|^$CORPUS/||" | grep -v '^$' || true
}

RC=0; N=0
while IFS=$'\t' read -r kind tok; do
  [ -n "$tok" ] || continue
  N=$((N+1))
  # origem do token não é consumidor de si mesma
  # A fonte do token nao e consumidora de si mesma; learn/ e training/
  # sao consumidores (foram exatamente eles que apodreceram no #648).
  SRC_SKIP='^docs/bugs-and-gaps/known-bugs\.(md|pt_BR\.md)|^docs/development/DECISIONS\.(md|pt_BR\.md)'
  [ "$kind" = "decision" ] && SRC_SKIP='^docs/development/DECISIONS\.(md|pt_BR\.md)'
  [ "$kind" = "moved" ] && SRC_SKIP='^$'
  STALE=""
  while read -r f; do
    [ -n "$f" ] || continue
    printf '%s' "$f" | grep -qE "$SRC_SKIP" && continue
    is_changed "$f" && continue
    waived "$tok" "$f" && continue
    STALE="$STALE $f"
  done < <(consumers_for "$kind" "$tok" | sort -u)
  if [ -n "$STALE" ]; then
    printf '%s ->%s -> BLOCK (consumidor vivo nao atualizado no mesmo diff)\n' "$tok" "$STALE"
    RC=1
  else
    printf '%s -> (nenhum consumidor pendente) -> PASS\n' "$tok"
  fi
done < "$TOKENS"
[ "$N" -eq 0 ] && echo "PASS (no normative tokens in diff)"
exit "$RC"
