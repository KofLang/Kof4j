#!/usr/bin/env bash
#
# doc-impact-test.sh — prova local de scripts/check_doc_impact.sh (#648).
# Fixtures SEM git: tokens + consumidores + mudancas num diretorio temporario.
# RED-first do mecanismo: o detector DEVE pegar o consumidor vivo esquecido e
# so fechar verde quando o mesmo diff toca o consumidor (ou waiver).
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$(dirname "$HERE")/.." && pwd)"
GATE="$REPO/scripts/check_doc_impact.sh"
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
fail() { echo "FAIL: $*"; exit 1; }
[ -x "$GATE" ] || [ -f "$GATE" ] || fail "gate ausente: $GATE"

mk_corpus() { # $1=dir
  mkdir -p "$1/docs/bugs-and-gaps" "$1/docs/development" "$1/learn" "$1/docs/tooling" "$1/docs/stdlib"
  printf '# ledger\n\n## §999 — face X — Status: ✅ FIXED 28/09\n\n## §998 — face Y\n' > "$1/docs/bugs-and-gaps/known-bugs.md"
  printf '| parity cell | the divergence is §999 |\n' > "$1/docs/backend-parity.md"
  printf '## D-FAKE-999 — decision\n\nbody\n' > "$1/docs/development/DECISIONS.md"
  printf 'see the plan at `docs/development/plan-moved.md` here\n' > "$1/learn/40-x.md"
  printf 'current text\n' > "$1/docs/tooling/PLAN-MOVED.md"
}

# ---- caso 1: § status mudou, consumidor vivo NAO atualizado -> FAIL -------
C="$T/c1"; mk_corpus "$C"
printf 'status\t§999\n' > "$C/tok"; printf 'docs/bugs-and-gaps/known-bugs.md\n' > "$C/ch"
if out="$(bash "$GATE" --corpus "$C" --tokens-file "$C/tok" --changed-file "$C/ch" --waivers /dev/null 2>&1)"; then
  fail "caso1: deveria FAIL (backend-parity.md cita §999 e nao foi tocado): $out"
fi
echo "$out" | grep -qE '§999 ->.*docs/backend-parity.md -> BLOCK' || fail "caso1 saida: $out"

# ---- caso 2: consumidor atualizado no mesmo diff -> PASS -------------------
printf 'docs/bugs-and-gaps/known-bugs.md\ndocs/backend-parity.md\n' > "$C/ch"
bash "$GATE" --corpus "$C" --tokens-file "$C/tok" --changed-file "$C/ch" --waivers /dev/null >/dev/null 2>&1 \
  || fail "caso2: deveria PASS com consumidor no diff"

# ---- caso 3: waiver historico (prefixo) -> PASS ----------------------------
printf '*\tdocs/\n' > "$C/wv"; printf 'docs/bugs-and-gaps/known-bugs.md\n' > "$C/ch"
bash "$GATE" --corpus "$C" --tokens-file "$C/tok" --changed-file "$C/ch" --waivers "$C/wv" >/dev/null 2>&1 \
  || fail "caso3: waiver '* docs/' deveria deixar passar"

# ---- caso 4: D-* novo com consumidor esquecido -> FAIL ---------------------
printf 'decision\tD-FAKE-999\n' > "$C/tok2"; printf 'docs/development/DECISIONS.md\n' > "$C/ch2"
printf 'aligned with D-FAKE-999 (must update when it changes)\n' >> "$C/docs/development/roadmap.md"
if bash "$GATE" --corpus "$C" --tokens-file "$C/tok2" --changed-file "$C/ch2" --waivers /dev/null >/dev/null 2>&1; then
  fail "caso4: deveria FAIL (roadmap cita D-FAKE-999 sem toque)"
fi

# ---- caso 5: documento movido; consumidor do caminho antigo -> FAIL; e o
#      waiver por token+consumidor especifico nao vale para outro consumidor --
printf 'moved\tdocs/development/plan-moved.md\n' > "$C/tok3"
printf 'docs/tooling/PLAN-MOVED.md\n' > "$C/ch3"
if out="$(bash "$GATE" --corpus "$C" --tokens-file "$C/tok3" --changed-file "$C/ch3" --waivers /dev/null 2>&1)"; then
  fail "caso5: deveria FAIL (learn/40-x.md aponta o caminho antigo): $out"
fi
printf 'docs/development/plan-moved.md\tlearn/40-x.md\n' > "$C/wv3"
bash "$GATE" --corpus "$C" --tokens-file "$C/tok3" --changed-file "$C/ch3" --waivers "$C/wv3" >/dev/null 2>&1 \
  || fail "caso5b: waiver especifico token->consumidor deveria PASS"

# ---- caso 6: sem tokens -> PASS trivial ------------------------------------
: > "$C/tok4"; printf 'README.md\n' > "$C/ch4"
bash "$GATE" --corpus "$C" --tokens-file "$C/tok4" --changed-file "$C/ch4" --waivers /dev/null >/dev/null 2>&1 \
  || fail "caso6: sem tokens deveria PASS"

# ---- caso 7: modo git real no repositorio (diff do proprio tip) ------------
bash "$GATE" >/dev/null 2>&1 || fail "caso7: gate no diff real do repositorio quebrou/FALHOU"


# ---- caso 9: modo GIT real (repo-fixture ISOLADO com guarda anti-contaminacao)
#      heading reescrito NAO dispara; mudanca de ESTADO dispara; SUPERSEDED bloqueia
G="$T/g9"; mkdir -p "$G/docs/bugs-and-gaps" "$G/docs/development"
git init -q "$G" || fail "caso9: git init falhou"
cd "$G" || fail "caso9: cd $G falhou"
[ "$(git rev-parse --show-toplevel)" = "$G" ] || fail "caso9: fixture nao e repo proprio — abortando antes de contaminar"
git config user.email t@t; git config user.name t
printf '# k\n\n## §123 — face antiga com palavras OPEN no titulo\n\n**Status:** 🔴 OPEN\n' > docs/bugs-and-gaps/known-bugs.md
printf '# d\n\n## D-SUPER-TEST — decisao\n\nbody\n' > docs/development/DECISIONS.md
printf 'uses D-SUPER-TEST today\n' > docs/consumer.md
printf '# README\n' > README.md
git add -A >/dev/null; git commit -qm init
# (a) reescrita de TITULO (contem 'OPEN') SEM tocar a linha de ESTADO -> sem tokens
sed -i 's/face antiga com palavras OPEN no titulo/face renomeada/' docs/bugs-and-gaps/known-bugs.md
git commit -qam title-rewrite
out="$(bash "$REPO/scripts/check_doc_impact.sh" --base HEAD~1 2>&1)"
echo "$out" | grep -q "PASS (no normative tokens" || fail "caso9a: reescrita de heading NAO deve disparar: $out"
# (b) mudanca de ESTADO dispara o § proprio
sed -i 's/🔴 OPEN/✅ FIXED/' docs/bugs-and-gaps/known-bugs.md
git commit -qam state-flip
out="$(bash "$REPO/scripts/check_doc_impact.sh" --base HEAD~1 2>&1)"
echo "$out" | grep -q '§123 ->' || fail "caso9b: mudanca de estado deve disparar token: $out"
# (c) SUPERSEDED com consumidor vivo nao tocado -> BLOCK
sed -i 's/## D-SUPER-TEST — decisao/## D-SUPER-TEST — decisao (SUPERSEDED by D-NEW-999)/' docs/development/DECISIONS.md
git commit -qam supersede
out="$(bash "$REPO/scripts/check_doc_impact.sh" --base HEAD~1 2>&1)"
echo "$out" | grep -q 'D-SUPER-TEST -> docs/consumer.md -> BLOCK' || fail "caso9c: SUPERSEDED deve bloquear consumidor: $out"
# (d) mesmo diff toca DECISIONS E o consumidor -> PASS
printf 'touch consumer\n' > docs/consumer.md
git add docs/consumer.md; git commit -qm touch-consumer
out="$(bash "$REPO/scripts/check_doc_impact.sh" --base HEAD~2 2>&1)"
echo "$out" | grep -q 'D-SUPER-TEST -> (nenhum consumidor pendente) -> PASS' || fail "caso9d: consumidor tocado deve PASS: $out"
cd "$REPO"

# ---- caso 10: doc MOVIDO — consumidor que cita por LINK RELATIVO tambem e pego
printf 'see [plano](../development/plan-moved.md)\n' > "$C/learn/41-rel.md"
printf 'moved\tdocs/development/plan-moved.md\n' > "$C/tok5"
printf 'docs/tooling/PLAN-MOVED.md\n' > "$C/ch5"
if out="$(bash "$GATE" --corpus "$C" --tokens-file "$C/tok5" --changed-file "$C/ch5" --waivers /dev/null 2>&1)"; then
  fail "caso10: link relativo nao deve PASS: $out"
fi
echo "$out" | grep -q 'learn/41-rel.md -> BLOCK\|-> .*learn/41-rel.md.*BLOCK' || fail "caso10: consumidor relativo deve BLOCK: $out"
printf 'docs/tooling/PLAN-MOVED.md\nlearn/40-x.md\nlearn/41-rel.md\n' > "$C/ch5"
bash "$GATE" --corpus "$C" --tokens-file "$C/tok5" --changed-file "$C/ch5" --waivers /dev/null >/dev/null 2>&1 \
  || fail "caso10b: todos os consumidores tocados deve PASS"

# ---- caso 11: fronteira de §NNN — §999 NAO deve pegar §9991 / §999.1 --------
D="$T/c11"; mk_corpus "$D"; printf 'mentions §9991 and §999.1 only\n' > "$D/docs/other.md"
printf 'status\t§999\n' > "$D/tok"; printf 'docs/bugs-and-gaps/known-bugs.md\n' > "$D/ch"
out="$(bash "$GATE" --corpus "$D" --tokens-file "$D/tok" --changed-file "$D/ch" --waivers /dev/null 2>&1 || true)"
echo "$out" | grep -q 'docs/other.md' && fail "caso11: §999 casou §9991/§999.1: $out"
echo "$out" | grep -q 'docs/backend-parity.md' || fail "caso11: consumidor §999 verdadeiro deve BLOCK: $out"

# ---- caso 12: waiver malformado (sem TAB) nao waiva nada ---------------------
printf '§999 docs/\n' > "$D/wv"
if out="$(bash "$GATE" --corpus "$D" --tokens-file "$D/tok" --changed-file "$D/ch" --waivers "$D/wv" 2>&1)"; then
  fail "caso12: waiver malformado nao pode verdejar: $out"
fi
printf '§999\tdocs/\n' > "$D/wv2"
bash "$GATE" --corpus "$D" --tokens-file "$D/tok" --changed-file "$D/ch" --waivers "$D/wv2" >/dev/null 2>&1 \
  || fail "caso12b: waiver valido (TAB) deve PASS"

# ---- caso 13: argumento desconhecido -> rc 2 (uso) ---------------------------
bash "$GATE" --nonsense >/dev/null 2>&1; rc=$?
[ "$rc" -eq 2 ] || fail "caso13: argumento desconhecido deve rc=2 (rc=$rc)"

# ---- caso 8: hook registrado no agent-verify (contrato #648) --------------
grep -q "doc_impact" "$REPO/scripts/agent-verify.sh" || fail "caso8: agent-verify sem hook doc_impact"

echo "doc-impact-test: VERDE (13 casos)"
