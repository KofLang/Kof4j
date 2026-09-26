#!/usr/bin/env bash
# check_release_050_gate.sh — the 0.5.0 RELEASE GATE (D-RELEASE-0.5.0-GATE,
# 09/20/2026 maintainer directive). The 0.5.0 release (precondition for opening
# the 1.0 line, D-1.0-EDGES Q1) is cut only when all SEVEN conditions hold,
# each one MEASURED — never by eye.
#
#   1 parity       100% parity between targets (measured per-target matrix)
#   2 decisions    no pending decision that changes the surface
#                  (0 unresolved `[? MEL]` in the PROPOSAL AND 0 `State: OPEN`
#                   in DECISIONS.md — a spec/plan-first OPEN is direction-decided
#                   but plan-pending, so it is NEEDS-REVIEW, never a silent GREEN)
#   3 loose docs   all loose docs/development/*.md concluded and moved out —
#                  the in-flight OWNED plans still loose are allowlisted
#                  (D-RELEASE-0.5.0-SCOPE, maintainer 21/09/2026)
#   4 stability    full suite 0F/0E + 5/5 conformance matrix on the candidate
#   5 bug issues   0 OPEN GitHub issues that are a bug
#   6 edges        all edges closed (open 1.0-blocks = 0 + EG-1..EG-7 closed;
#                  EG-8 — the 1.0-line declaration/RC cut — is DECOUPLED from
#                  this gate per D-RELEASE-0.5.0-SCOPE)
#   7 bugs/gaps    nothing pending in known-bugs.md / specification-gaps.md
#
# Each condition is GREEN / RED / NEEDS-MEASURE / NEEDS-REVIEW / UNKNOWN.
# Exit: 0 = all GREEN; 1 = at least one RED; 2 = no RED but something
# inconclusive (NEEDS-*/UNKNOWN); 3 = a required data source is unavailable.
#
# RED is EXPECTED until the queue closes — the gate is the driver, not a
# blocker to work around.
#
# Offline / --selftest use data-source overrides (no network, no gh):
#   R050_OPEN_ISSUES_TSV  file "number<TAB>labels"
#   R050_KNOWN_BUGS_CMD   command printing the known-bugs ledger (default gate)
#   R050_EG_TSV           file "EG-N<TAB>state" (default: parse roadmap §24)
#   R050_EG_ROADMAP       roadmap file to parse for EG rows (default docs/development/roadmap.md)
#   R050_BLOCKS_CMD       command printing the release-blockers summary
#                         (default `bash scripts/check_release_blockers.sh --rc-gate`);
#                         when it cannot be parsed, edges is UNKNOWN, never "0 blocks"
#   R050_OPEN_BLOCKS      external measurement of open 1.0-blocks (skips the query)
#   R050_LOOSE_MD_FILE    file listing loose md basenames (default: ls)
#   R050_PENDING_FILE     file whose first line is the pending-decision count
#   R050_PARITY_FILE      file containing "PARITY: 100%" when parity holds
#   R050_MATRIX_CMD       command that runs the per-target matrix (default
#                         `bash scripts/target-matrix.sh`, §14/EG-5); set empty
#                         to disable the auto-measure and stay NEEDS-MEASURE
#   KOF_SUITE_LOG         path to a real suite log; when set, condition 4 is
#                         auto-measured by scripts/stability-report.sh (0F/0E)
#
# Usage: scripts/check_release_050_gate.sh [--selftest]
set -uo pipefail
cd "$(git rev-parse --show-toplevel 2>/dev/null || echo .)"

OPEN_ISSUES_TSV="${R050_OPEN_ISSUES_TSV:-}"
KNOWN_BUGS_CMD="${R050_KNOWN_BUGS_CMD:-bash scripts/check_known_bugs_status.sh}"
EG_TSV="${R050_EG_TSV:-}"
EG_ROADMAP="${R050_EG_ROADMAP:-docs/development/roadmap.md}"
BLOCKS_CMD="${R050_BLOCKS_CMD:-bash scripts/check_release_blockers.sh --rc-gate}"
LOOSE_MD_FILE="${R050_LOOSE_MD_FILE:-}"
PENDING_FILE="${R050_PENDING_FILE:-}"
DECISIONS_MD="${R050_DECISIONS_MD:-docs/development/DECISIONS.md}"
PARITY_FILE="${R050_PARITY_FILE:-}"
R050_STABILITY_FILE="${R050_STABILITY_FILE:-}"
R050_SPEC_GAPS_FILE="${R050_SPEC_GAPS_FILE:-}"

# docs that are living/meta by nature and stay in docs/development (the
# three-states rule keeps them there while the phase is open).
# tech-debt.md was KILLED 24/09 by the maintainer (debt measured zeroed:
# the 6 live §NNN all ✅ in known-bugs, size gate green) — tooling removed.
# The two ratified PROPOSALs were promoted out of development/ the same day
# (exit-gate -> docs/, versioning record -> docs/distribution/).
# X2 interop-engine-plan: in-flight OWNED plan (D-COMPLETE-FIRST item 2, claim
# lane compiler 9092 26/09) — same treatment as memory-safety-plan above; the
# 0.5.0 cut does not wait for a brand-new experimental front that just opened.
ALLOWLIST="DECISIONS.md DECISIONS.pt_BR.md README.md README.pt_BR.md roadmap.md roadmap.pt_BR.md release-beta-0.5.0-prep.md release-beta-0.5.0-prep.pt_BR.md memory-safety-plan.md memory-safety-plan.pt_BR.md interop-engine-plan.md interop-engine-plan.pt_BR.md"
# D-RELEASE-0.5.0-SCOPE (maintainer 21/09/2026): the in-flight OWNED plans
# still loose are allowlisted — 0.5.0 does not wait for db to conclude;
# each keeps its owner and stays tracked in the README queue.
# (IMPLEMENTATION-UNIVERSAL-PLATFORM concluded and moved to docs/architecture/
# on 21/09; type-system-extensions-plan concluded (X5+X6) and moved to docs/
# on 22/09; kof-c-cross concluded (C1–C4 + C3-residual) and moved to docs/ on
# 23/09 — none is loose, hence not listed here.)
# D-BAREMETAL-BOOT (maintainer 22/09/2026): PLAN-BAREMETAL-BOOT was promoted
# from future/ (bare-metal + ring0/ring1 scope) and allowlisted the same way.
# It CONCLUDED 25/09 (B-4 closed on riscv32) and moved to docs/ — no longer
# loose, hence no longer listed here.
ALLOWLIST="$ALLOWLIST db-parity-plan.md db-parity-plan.pt_BR.md"

# state per condition: GREEN|RED|NEEDS-MEASURE|NEEDS-REVIEW|UNKNOWN
declare -A STATE DETAIL

c_parity() {
  local report="$PARITY_FILE" provided=1
  if [ -z "$report" ]; then
    provided=0
    # auto-mede: roda o harness da matriz (§14/EG-5) e le a linha PARITY.
    # R050_MATRIX_CMD sobrescreve (vazio = desliga → NEEDS-MEASURE, uso offline).
    local cmd="${R050_MATRIX_CMD-bash scripts/target-matrix.sh}"
    if [ -z "$cmd" ] || [ ! -f scripts/target-matrix.sh ]; then
      STATE[parity]=NEEDS-MEASURE
      DETAIL[parity]="run the per-target matrix on the candidate (JVM/x86-64/riscv64/aarch64/JS/Script); divergence = bug or XXX00x gap"
      return
    fi
    report="$(mktemp)"
    $cmd > "$report" 2>&1 || true
  fi
  if grep -q "PARITY: 100%" "$report" 2>/dev/null; then
    STATE[parity]=GREEN; DETAIL[parity]="per-target matrix reports 100%"
  elif [ "$provided" -eq 1 ] || grep -q "PARITY: 0%" "$report" 2>/dev/null; then
    STATE[parity]=RED; DETAIL[parity]="matrix reports a divergence — see $report"
  else
    STATE[parity]=NEEDS-MEASURE
    local cause; cause="$(grep -m1 -E 'ARTEFATO VELHO|nao corresponde|SEM |sem ' "$report" 2>/dev/null | head -c 160)"
    DETAIL[parity]="matrix could not certify${cause:+ — $cause} (see $report)"
  fi
}

c_decisions() {
  if [ -n "$PENDING_FILE" ]; then
    # fonte ilegivel/sem linha de contagem NAO pode virar "0 decisoes" verde — UNKNOWN (R6/Q5).
    [ -r "$PENDING_FILE" ] || { STATE[decisions]=UNKNOWN; DETAIL[decisions]="pending-decision source unreadable: $PENDING_FILE"; return; }
    local head1; head1="$(head -1 "$PENDING_FILE" 2>/dev/null)"
    case "$head1" in
      *[0-9]*) : ;;
      *) STATE[decisions]=UNKNOWN; DETAIL[decisions]="pending-decision source has no count line: $PENDING_FILE"; return ;;
    esac
    local n; n="$(printf '%s' "$head1" | tr -dc '0-9')"; n="${n:-0}"
    if [ "$n" -eq 0 ]; then STATE[decisions]=GREEN; DETAIL[decisions]="no pending decision"
    else STATE[decisions]=RED; DETAIL[decisions]="$n pending decision(s)"; fi
    return
  fi
  if [ -d docs/development/decision-pending ]; then
    STATE[decisions]=RED; DETAIL[decisions]="decision-pending/ folder exists"
  else
    local prop=docs/PROPOSAL-1.0-EXIT-GATE.md
    # PROPOSAL ausente/ilegivel NAO pode virar "sem [? MEL]" verde — UNKNOWN (R6/Q5).
    [ -r "$prop" ] || { STATE[decisions]=UNKNOWN; DETAIL[decisions]="decision source unreadable: $prop"; return; }
    local open
    open="$(grep -rhoE '^\[[?] *MEL *\]' "$prop" 2>/dev/null | wc -l | tr -d ' ')"
    # DECISIONS.md com `State: OPEN` (spec/plan-first) NAO pode virar verde silencioso:
    # a direcao foi escolhida, mas o plano/review e trabalho pendente que muda a
    # superficie (condicao 2) — vira NEEDS-REVIEW, nunca GREEN (R6/Q5).
    [ -r "$DECISIONS_MD" ] || { STATE[decisions]=UNKNOWN; DETAIL[decisions]="decision source unreadable: $DECISIONS_MD"; return; }
    local open_state ids
    # Um heading combinado (`## D-A / D-B — ...`) nomeia DUAS decisoes: extrai
    # TODOS os tokens `D-*` do heading e deduplica (o antigo `$2` contava D-A 2x e
    # omitia o segundo id — 21/09).
    ids="$(awk '
      /^## /{ cur=""; n=split($0, t, /[^A-Za-z0-9-]+/);
              for (i=1;i<=n;i++) if (t[i] ~ /^D-[A-Za-z0-9-]+$/) cur=cur (cur?" ":"") t[i] }
      /(\*\*State:\*\*|\*\*Estado:\*\*) `(OPEN|ABERTO)/{ if (cur!="") print cur }
    ' "$DECISIONS_MD" 2>/dev/null | tr ' ' '\n' | grep -E '^D-[A-Za-z0-9-]+$' | sort -u | tr '\n' ' ')"
    open_state="$(printf '%s' "$ids" | wc -w | tr -d ' ')"
    if [ "${open:-0}" -eq 0 ] && [ "${open_state:-0}" -eq 0 ]; then
      STATE[decisions]=GREEN; DETAIL[decisions]="no pending decision (decision-pending/ extinct; no unresolved [? MEL] candidate; no State: OPEN)"
    elif [ "${open:-0}" -gt 0 ]; then
      STATE[decisions]=NEEDS-REVIEW
      DETAIL[decisions]="decision-pending/ extinct; $open unresolved [? MEL] candidate(s) in the PROPOSAL"
    else
      STATE[decisions]=NEEDS-REVIEW
      DETAIL[decisions]="$open_state State: OPEN decision(s) in DECISIONS.md pending plan/review (direction decided): ${ids% }"
    fi
  fi
}

# D-FULL-PARITY-050 (24/09): condicao 8 — o ledger de paridade total nao pode
# ter linha aberta. Ledger ausente/ilegivel = UNKNOWN, nunca GREEN (R6/Q5).
# Linha aberta = linha de tabela no bloco "Open rows" cujo primeiro campo e
# numerado (`| N |`); o bloco vai de "## Open rows" ate "## Closed".
c_full_parity() {
  local f="${R050_PARITY_GAPS_FILE:-docs/development/parity/PARITY-GAPS.md}"
  if [ ! -r "$f" ]; then
    STATE[full_parity]=UNKNOWN; DETAIL[full_parity]="parity ledger unreadable: $f"; return
  fi
  local n
  n="$(sed -n '/^## Open rows/,/^## Closed/p' "$f" | grep -cE '^\| *[0-9]+ *\|')"
  case "$n" in
    ''|*[!0-9]*) STATE[full_parity]=UNKNOWN; DETAIL[full_parity]="parity ledger unparsable (no Open rows table)"; return ;;
  esac
  if [ "$n" -eq 0 ]; then
    STATE[full_parity]=GREEN; DETAIL[full_parity]="full parity ledger: 0 open rows"
  else
    STATE[full_parity]=RED; DETAIL[full_parity]="$n open parity row(s) — D-FULL-PARITY-050 blocker"
  fi
}

c_loose_docs() {
  local list extra
  if [ -n "$LOOSE_MD_FILE" ]; then
    # fonte ilegivel NAO pode virar "nenhum doc" verde — UNKNOWN (R6/Q5).
    [ -r "$LOOSE_MD_FILE" ] || { STATE[loose_docs]=UNKNOWN; DETAIL[loose_docs]="loose-doc list unreadable: $LOOSE_MD_FILE"; return; }
    list="$(cat "$LOOSE_MD_FILE" 2>/dev/null)"
  else
    # docs/development/ ausente NAO pode virar "nada a concluir" verde — UNKNOWN (R6/Q5).
    [ -d docs/development ] || { STATE[loose_docs]=UNKNOWN; DETAIL[loose_docs]="docs/development/ unreadable"; return; }
    list="$(cd docs/development 2>/dev/null && ls *.md 2>/dev/null)"
  fi
  extra=""
  local f
  while IFS= read -r f; do
    [ -z "$f" ] && continue
    case " $ALLOWLIST " in *" $f "*) : ;; *) extra="$extra $f" ;; esac
  done <<< "$list"
  if [ -z "${extra// }" ]; then STATE[loose_docs]=GREEN; DETAIL[loose_docs]="no work doc left in docs/development/"
  else STATE[loose_docs]=RED; DETAIL[loose_docs]="conclude + move:$extra"; fi
}

c_stability() {
  if [ -n "$R050_STABILITY_FILE" ]; then
    if grep -q "STABILITY: GREEN" "$R050_STABILITY_FILE" 2>/dev/null; then
      STATE[stability]=GREEN; DETAIL[stability]="suite 0F/0E + 5/5 matrix recorded on the candidate"
    else
      STATE[stability]=RED; DETAIL[stability]="recorded stability report is not green"
    fi
    return
  fi
  # auto-mede do log de uma corrida REAL da suite (KOF_SUITE_LOG), sem re-rodar:
  # scripts/stability-report.sh le a ultima linha TOTAL e exige 0F/0E.
  if [ -n "${KOF_SUITE_LOG:-}" ] && [ -f scripts/stability-report.sh ]; then
    local report; report="$(mktemp)"
    bash scripts/stability-report.sh --suite-log "$KOF_SUITE_LOG" > "$report" 2>&1 || true
    if grep -q "STABILITY: GREEN" "$report"; then
      STATE[stability]=GREEN; DETAIL[stability]="suite log 0F/0E on the candidate ($KOF_SUITE_LOG)"
    elif grep -q "STABILITY: RED" "$report"; then
      STATE[stability]=RED; DETAIL[stability]="suite log has failures/errors ($KOF_SUITE_LOG)"
    else
      STATE[stability]=NEEDS-MEASURE; DETAIL[stability]="suite log unreadable (no TOTAL) — see $report"
    fi
    return
  fi
  STATE[stability]=NEEDS-MEASURE
  DETAIL[stability]="run scripts/safe-suite.sh (0F/0E) + the 5/5 conformance matrix on the candidate"
}

c_bug_issues() {
  local rows
  if [ -n "$OPEN_ISSUES_TSV" ]; then
    rows="$(cat "$OPEN_ISSUES_TSV" 2>/dev/null)"
  else
    eval "$(scripts/gh-as-agent.sh token 2>/dev/null)" || true
    # O rc da CONSULTA manda: uma falha transitoria de API (gh autenticado mas a
    # query morre) NAO pode virar "0 bugs" verde — UNKNOWN (R6/Q5). Lista vazia
    # legitima (rc=0, 0 issues) segue GREEN.
    if ! rows="$(gh issue list --repo KofLang/Kof4j --state open --limit 200 \
        --json number,labels --jq '.[] | "\(.number)\t\([.labels[].name]|join(","))"' 2>/dev/null)"; then
      STATE[bug_issues]=UNKNOWN; DETAIL[bug_issues]="gh unavailable/query failed — cannot enumerate open issues"
      return
    fi
  fi
  local bugs=""
  while IFS=$'\t' read -r num labels; do
    [ -z "$num" ] && continue
    case ",$labels," in *,bug,*) bugs="$bugs #$num" ;; esac
  done <<< "$rows"
  if [ -z "${bugs// }" ]; then STATE[bug_issues]=GREEN; DETAIL[bug_issues]="0 open bug issues"
  else STATE[bug_issues]=RED; DETAIL[bug_issues]="open bug issue(s):$bugs"; fi
}

c_edges() {
  # Criterion 6 counts the edge queue that gates the release: EG-1..EG-7 plus
  # the open `1.0-blocks` issues. EG-8 — the first 1.0 RC candidate + the
  # maintainer's "the 1.0 line is open" declaration — is DECOUPLED from the
  # 0.5.0 gate (D-RELEASE-0.5.0-SCOPE, maintainer 21/09/2026): it belongs to
  # the 1.0 line and only opens after EG-1..EG-7 close.
  local eg_open="" blocks=0 eg_rows=0
  if [ -n "$EG_TSV" ]; then
    [ -r "$EG_TSV" ] || { STATE[edges]=UNKNOWN; DETAIL[edges]="EG table unreadable: $EG_TSV"; return; }
    while IFS=$'\t' read -r eg st; do
      [ -z "$eg" ] && continue
      case "$eg" in EG-8) continue ;; esac
      eg_rows=$((eg_rows+1))
      case "$st" in *DONE*|*FEITO*) : ;; *) eg_open="$eg_open $eg" ;; esac
    done < "$EG_TSV"
    # tabela vazia/nao-lida NAO pode virar "sem aresta aberta" verde — UNKNOWN (R6/Q5).
    if [ "$eg_rows" -eq 0 ]; then
      STATE[edges]=UNKNOWN; DETAIL[edges]="EG table empty/unparsed: $EG_TSV"; return
    fi
    blocks="${R050_OPEN_BLOCKS:-0}"
  else
    local eg_all
    eg_all="$(awk -F'|' '/^\| *EG-[0-9]+ /{print}' "$EG_ROADMAP" 2>/dev/null)"
    eg_rows="$(printf '%s\n' "$eg_all" | grep -c 'EG-[0-9]' || true)"
    if [ "${eg_rows:-0}" -eq 0 ]; then
      STATE[edges]=UNKNOWN; DETAIL[edges]="roadmap EG table unreadable (no EG rows in $EG_ROADMAP)"; return
    fi
    eg_open="$(printf '%s\n' "$eg_all" | awk -F'|' '{ id=$2; gsub(/ /,"",id); if ($0 !~ /DONE|FEITO/ && id != "EG-8") print id }' | tr '\n' ' ')"
    local out bl
    if [ -n "${R050_OPEN_BLOCKS:-}" ]; then
      blocks="$R050_OPEN_BLOCKS"           # medicao externa (mesma forma do caminho EG_TSV)
    else
      eval "$(scripts/gh-as-agent.sh token 2>/dev/null)" || true
      out="$($BLOCKS_CMD 2>&1)"
      bl="$(printf '%s\n' "$out" | sed -n 's/.*-- \([0-9]*\) open 1.0-blocks.*/\1/p' | head -1)"
      # enumeracao que nao respondeu (gh/API fora) NAO pode virar "0 blocks"
      # verde — seria o mesmo falso-verde que o bug_issues proibe (R6/Q5).
      if [ -z "$bl" ]; then blocks="UNKNOWN"; else blocks="$bl"; fi
    fi
  fi
  if [ -n "${eg_open// }" ]; then
    STATE[edges]=RED; DETAIL[edges]="open edge(s):$eg_open; open 1.0-blocks: ${blocks:-?}"
  elif [ "${blocks:-UNKNOWN}" = "UNKNOWN" ]; then
    STATE[edges]=UNKNOWN; DETAIL[edges]="1.0-blocks query failed (gh/API unavailable) — cannot enumerate open blocks"
  elif [ "$blocks" -eq 0 ]; then
    STATE[edges]=GREEN; DETAIL[edges]="no open edge (EG-1..EG-7 closed; EG-8 decoupled per D-RELEASE-0.5.0-SCOPE; 0 open 1.0-blocks)"
  else
    STATE[edges]=RED; DETAIL[edges]="open edge(s): none; open 1.0-blocks: $blocks"
  fi
}

c_bugs_gaps() {
  local kb_out kb_n="" sg_n=0
  kb_out="$($KNOWN_BUGS_CMD 2>/dev/null)"
  kb_n="$(printf '%s\n' "$kb_out" | sed -n 's/^EN open\/partial (\([0-9]*\)).*/\1/p' | head -1)"
  # ledger ilegivel (comando falhou / saida inesperada) NAO pode virar "0
  # conhecidos" verde — UNKNOWN (R6/Q5), tal como a query de issues em c_bug_issues.
  if [ -z "$kb_n" ]; then
    STATE[bugs_gaps]=UNKNOWN
    DETAIL[bugs_gaps]="known-bugs ledger unreadable (no 'EN open/partial (N)' line) — cmd: $KNOWN_BUGS_CMD"
    return
  fi
  if [ -n "$R050_SPEC_GAPS_FILE" ]; then
    sg_n="$(grep -cE '🟡|🔴' "$R050_SPEC_GAPS_FILE" 2>/dev/null | tr -d ' ')"; sg_n="${sg_n:-0}"
  else
    sg_n="$(grep -cE '🟡|🔴' docs/bugs-and-gaps/specification-gaps.md 2>/dev/null | tr -d ' ')"; sg_n="${sg_n:-0}"
  fi
  if [ "$kb_n" -eq 0 ] && [ "$sg_n" -eq 0 ]; then
    STATE[bugs_gaps]=GREEN; DETAIL[bugs_gaps]="no live known-bug, 0 open spec gap"
  else
    STATE[bugs_gaps]=RED; DETAIL[bugs_gaps]="$kb_n live known-bug(s), $sg_n open spec gap(s)"
  fi
}

if [ "${1:-}" = "--selftest" ]; then
  T="$(mktemp -d)"
  trap 'rm -rf "$T"' EXIT
  fail() { echo "SELFTEST FAIL: $*" >&2; exit 2; }
  # decisions fixtures (hermetic): a DECIDED-only file and one with a State: OPEN
  printf '## D-OK — x\n\n**State:** `DECIDED`\n' > "$T/dec_ok"
  printf '## D-OPEN — x\n\n**State:** `OPEN — spec/plan first`\n' > "$T/dec_open"

  # clean fixture -> every measurable condition GREEN
  printf '100\tdocumentation,post-1.0\n' > "$T/issues"
  printf 'EG-1\tDONE\nEG-2\tDONE\n' > "$T/eg"
  printf 'PARITY: 100%%\n' > "$T/parity"
  printf 'STABILITY: GREEN\n' > "$T/stab"
  printf '0\n' > "$T/pending"
  printf 'DECISIONS.md\nroadmap.md\n' > "$T/loose"
  : > "$T/spec"
  printf '# ledger\n\n## Open rows\n\n| # | x |\n|---|---|\n\n## Closed\n' > "$T/pgaps"
  cat > "$T/kb" <<'EOF'
EN open/partial (0): 
PT open/partial (0): 
EOF
  R050_OPEN_ISSUES_TSV="$T/issues" R050_EG_TSV="$T/eg" R050_PARITY_FILE="$T/parity" \
  R050_STABILITY_FILE="$T/stab" \
  R050_PENDING_FILE="$T/pending" R050_LOOSE_MD_FILE="$T/loose" R050_SPEC_GAPS_FILE="$T/spec" \
  R050_KNOWN_BUGS_CMD="cat $T/kb" R050_OPEN_BLOCKS=0 R050_PARITY_GAPS_FILE="$T/pgaps" \
    bash "$0" > "$T/out"; rc=$?
  [ "$rc" -eq 0 ] || fail "clean fixture should be exit 0, got $rc"
  grep -q 'parity .*GREEN' "$T/out" || fail "clean parity not GREEN"
  grep -q 'full_parity .*GREEN' "$T/out" || fail "clean full_parity not GREEN"
  grep -q 'bugs_gaps .*GREEN' "$T/out" || fail "clean bugs_gaps not GREEN"

  # D-RELEASE-0.5.0-SCOPE (maintainer 21/09/2026): (a) an allowlisted
  # in-flight plan is NOT loose_docs RED; (b) an open EG-8 is NOT edges RED —
  # the 1.0-line declaration is decoupled from the 0.5.0 gate.
  printf 'DECISIONS.md\ndb-parity-plan.md\n' > "$T/loose"
  printf 'EG-1\tDONE\nEG-8\tOPEN\n' > "$T/eg"
  R050_OPEN_ISSUES_TSV="$T/issues" R050_EG_TSV="$T/eg" R050_PARITY_FILE="$T/parity" \
  R050_STABILITY_FILE="$T/stab" \
  R050_PENDING_FILE="$T/pending" R050_LOOSE_MD_FILE="$T/loose" R050_SPEC_GAPS_FILE="$T/spec" \
  R050_KNOWN_BUGS_CMD="cat $T/kb" R050_OPEN_BLOCKS=0 R050_PARITY_GAPS_FILE="$T/pgaps" \
    bash "$0" > "$T/out_scope"; rc=$?
  [ "$rc" -eq 0 ] || fail "scope fixture should be exit 0, got $rc"
  grep -q 'loose_docs .*GREEN' "$T/out_scope" || fail "allowlisted doc made loose_docs not GREEN"
  grep -q 'edges .*GREEN' "$T/out_scope" || fail "open EG-8 made edges not GREEN"

  # dirty fixture -> REDs on every condition that has data
  printf '561\tbug,1.0-blocks\n563\tbug,1.0-blocks\n566\tbug,1.0-outside\n' > "$T/issues"
  printf 'EG-1\tDONE\nEG-5\tOPEN\nEG-9\tOPEN\n' > "$T/eg"
  printf 'PARITY: 97%%\n' > "$T/parity"
  printf 'STABILITY: RED\n' > "$T/stab"
  printf '2\n' > "$T/pending"
  printf 'DECISIONS.md\nmakealive-plan.md\n' > "$T/loose"
  printf 'EN open/partial (19): 188 192\nPT open/partial (19): 188 192\n' > "$T/kb"
  printf '🟡\n' > "$T/spec"
  printf '# ledger\n\n## Open rows\n\n| 1 | x |\n|---|---|\n| 2 | y |\n\n## Closed\n' > "$T/pgaps2"
  R050_OPEN_ISSUES_TSV="$T/issues" R050_EG_TSV="$T/eg" R050_PARITY_FILE="$T/parity" \
  R050_STABILITY_FILE="$T/stab" \
  R050_PENDING_FILE="$T/pending" R050_LOOSE_MD_FILE="$T/loose" R050_SPEC_GAPS_FILE="$T/spec" \
  R050_KNOWN_BUGS_CMD="cat $T/kb" R050_OPEN_BLOCKS=1 \
    bash "$0" > "$T/out2"; rc=$?
  [ "$rc" -eq 1 ] || fail "dirty fixture should be exit 1, got $rc"
  grep -q 'parity .*RED' "$T/out2" || fail "dirty parity not RED"
  grep -q 'bug_issues .*RED' "$T/out2" || fail "dirty bug_issues not RED"
  grep -q 'edges .*RED' "$T/out2" || fail "dirty edges not RED"
  grep -q 'loose_docs .*RED' "$T/out2" || fail "dirty loose_docs not RED"
  grep -q 'full_parity .*RED' "$T/out2" || fail "dirty full_parity not RED"
  grep -q 'bugs_gaps .*RED' "$T/out2" || fail "dirty bugs_gaps not RED"

  # inconclusive fixture -> exit 2 (no RED, but NEEDS-*)
  printf '100\tdocumentation,post-1.0\n' > "$T/issues"
  printf 'EG-1\tDONE\n' > "$T/eg"
  : > "$T/pending"; printf 'DECISIONS.md\n' > "$T/loose"
  : > "$T/spec"
  printf 'EN open/partial (0):\nPT open/partial (0):\n' > "$T/kb"
  R050_OPEN_ISSUES_TSV="$T/issues" R050_EG_TSV="$T/eg" R050_LOOSE_MD_FILE="$T/loose" \
  R050_SPEC_GAPS_FILE="$T/spec" R050_DECISIONS_MD="$T/dec_ok" \
  R050_KNOWN_BUGS_CMD="cat $T/kb" R050_OPEN_BLOCKS=0 \
  R050_MATRIX_CMD=: KOF_SUITE_LOG= R050_PARITY_GAPS_FILE="$T/pgaps" \
    bash "$0" > "$T/out3"; rc=$?
  [ "$rc" -eq 2 ] || fail "inconclusive fixture should be exit 2, got $rc"
  grep -q 'NEEDS-MEASURE' "$T/out3" || fail "inconclusive run should surface NEEDS-MEASURE"

  # decisions: State: OPEN (direção decidida, plano pendente) nao pode ser GREEN
  # silencioso — vira NEEDS-REVIEW (condicao 2 mudou a superficie).
  printf '100\tdocumentation,post-1.0\n' > "$T/issues"
  printf 'EG-1\tDONE\n' > "$T/eg"
  : > "$T/spec"; printf 'DECISIONS.md\n' > "$T/loose"
  printf 'EN open/partial (0):\nPT open/partial (0):\n' > "$T/kb"
  R050_OPEN_ISSUES_TSV="$T/issues" R050_EG_TSV="$T/eg" R050_LOOSE_MD_FILE="$T/loose" \
  R050_SPEC_GAPS_FILE="$T/spec" R050_DECISIONS_MD="$T/dec_open" \
  R050_KNOWN_BUGS_CMD="cat $T/kb" R050_OPEN_BLOCKS=0 \
  R050_MATRIX_CMD=: KOF_SUITE_LOG= R050_PARITY_GAPS_FILE="$T/pgaps" \
    bash "$0" > "$T/out3b"; rc=$?
  [ "$rc" -eq 2 ] || fail "State: OPEN fixture should be exit 2, got $rc"
  grep -q 'decisions .*NEEDS-REVIEW' "$T/out3b" || fail "State: OPEN decisions should be NEEDS-REVIEW"
  grep -q 'D-OPEN' "$T/out3b" || fail "State: OPEN decisions detail should name D-OPEN"

  # heading combinado `D-A / D-B` nomeia DUAS decisoes (regressao 21/09): o parser
  # antigo (`$2`) contava D-A 2x num heading so e omitia D-B.
  printf '## D-A / D-B — x\n\n**State:** `OPEN — implementing`\n' > "$T/dec_two"
  R050_OPEN_ISSUES_TSV="$T/issues" R050_EG_TSV="$T/eg" R050_LOOSE_MD_FILE="$T/loose" \
  R050_SPEC_GAPS_FILE="$T/spec" R050_DECISIONS_MD="$T/dec_two" \
  R050_KNOWN_BUGS_CMD="cat $T/kb" R050_OPEN_BLOCKS=0 \
  R050_MATRIX_CMD=: KOF_SUITE_LOG= R050_PARITY_GAPS_FILE="$T/pgaps" \
    bash "$0" > "$T/out3c"; rc=$?
  grep -q '2 State: OPEN' "$T/out3c" || fail "combined heading should count 2 decisions"
  grep -q 'D-A' "$T/out3c" && grep -q 'D-B' "$T/out3c" \
    || fail "combined heading should name D-A and D-B"

  # edges: todos os EG fechados, mas a query de 1.0-blocks NAO responde.
  # Nao pode virar "0 blocks" verde (mesmo falso-verde que bug_issues proibe).
  printf '| EG-1 | x | DONE |\n| EG-2 | y | FEITO |\n' > "$T/rm"
  R050_OPEN_ISSUES_TSV="$T/issues" R050_EG_ROADMAP="$T/rm" \
  R050_LOOSE_MD_FILE="$T/loose" R050_SPEC_GAPS_FILE="$T/spec" R050_DECISIONS_MD="$T/dec_ok" \
  R050_KNOWN_BUGS_CMD="cat $T/kb" R050_BLOCKS_CMD="echo boom; exit 3" \
  R050_MATRIX_CMD=: KOF_SUITE_LOG= R050_PARITY_GAPS_FILE="$T/pgaps" \
    bash "$0" > "$T/out4" 2>/dev/null
  grep -q 'edges .*UNKNOWN' "$T/out4" || fail "EG fechado + blocks sem resposta devia ser UNKNOWN"
  # e quando a query responde 0, edges fica GREEN
  R050_OPEN_ISSUES_TSV="$T/issues" R050_EG_ROADMAP="$T/rm" \
  R050_LOOSE_MD_FILE="$T/loose" R050_SPEC_GAPS_FILE="$T/spec" R050_DECISIONS_MD="$T/dec_ok" \
  R050_KNOWN_BUGS_CMD="cat $T/kb" R050_BLOCKS_CMD='echo "-- 0 open 1.0-blocks"' \
  R050_MATRIX_CMD=: KOF_SUITE_LOG= R050_PARITY_GAPS_FILE="$T/pgaps" \
    bash "$0" > "$T/out5" 2>/dev/null
  grep -q 'edges .*GREEN' "$T/out5" || fail "EG fechado + 0 blocks devia ser GREEN"

  echo "SELFTEST OK"
  exit 0
fi

c_parity; c_full_parity; c_decisions; c_loose_docs; c_stability; c_bug_issues; c_edges; c_bugs_gaps

ORDER=(parity full_parity decisions loose_docs stability bug_issues edges bugs_gaps)
red=0; incon=0
echo "== 0.5.0 release gate (D-RELEASE-0.5.0-GATE) =="
for k in "${ORDER[@]}"; do
  printf '  %-11s %-13s %s\n' "$k" "${STATE[$k]}" "${DETAIL[$k]}"
  case "${STATE[$k]}" in
    RED) red=$((red+1)) ;;
    GREEN) : ;;
    *) incon=$((incon+1)) ;;
  esac
done
echo "  -- $red RED, $incon inconclusive of ${#ORDER[@]}"
if [ "$red" -gt 0 ]; then exit 1; fi
if [ "$incon" -gt 0 ]; then exit 2; fi
echo "  0.5.0 RELEASE GATE: GREEN — all seven conditions measured and holding"
exit 0
