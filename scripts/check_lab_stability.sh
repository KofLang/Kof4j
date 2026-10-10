#!/usr/bin/env bash
# check_lab_stability.sh — the mechanical face of D-LAB-STABILITY / D-RELEASE-CADENCE.
#
# A minor is cut from a STABLE `lab` only: RC-gate green (0 OPEN 1.0-blocks), every
# structural gate green, AND a fresh suite report that is actually green. This script
# aggregates those conditions into one verdict so a cut cannot proceed on a partial
# "looks fine". There was NO such gate before (grep of scripts/ + .github/ for
# D-LAB-STABILITY / D-RELEASE-CADENCE returned zero hits) — this is that missing gate.
#
# Honest by construction: an absent suite report is `SUITE: NAO-AVALIADO`, which forces
# SLIPS — never a silent green (Q5 no-false-green).
#
# Usage:
#   scripts/check_lab_stability.sh                    # real aggregation
#   scripts/check_lab_stability.sh --selftest         # planted fixtures (offline, has teeth)
#
# Test hooks (env, same contract style as check_release_blockers.sh):
#   LAB_STABILITY_RC_GATE_CMD  stands in for `check_release_blockers.sh --rc-gate` (rc only)
#   LAB_STABILITY_GATE_CMD     "sh -c" template run once per gate name in $1, rc only
#   LAB_STABILITY_SUITE_REPORT path to a suite report file (Maven summary OR a
#                               stamped safe-suite.sh log)
#   LAB_STABILITY_EXPECT_SHA   expected SHA for a stamped safe-suite log
#                               (default: HEAD)
#
# Exit codes: 0 CUT:GREEN; 1 CUT:SLIPS; 2 selftest failure.
set -u
REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"

# Structural gates that must all be rc=0 before a cut (fast, deterministic, no network
# except release-blockers which is handled by the rc-gate below).
STRUCTURAL_GATES="check_changelog_ledger check_known_bugs_status check_ledger_anchors \
check_live_records check_owner_identity check_plan_owners check_test_hygiene \
check_stdlib_boundary check_workflow_pins check_doc_refs check_doc_impact check_500"

# verdict_from <rc_gate> <gates_ok> <suite_state> -> prints CUT:GREEN|CUT:SLIPS + reason, rc 0/1
#   suite_state: GREEN | SLIPS | NAO-AVALIADO
verdict() {
  local rcg="$1" gok="$2" suite="$3" slips=0 reasons=""
  if [ "$rcg" -ne 0 ]; then slips=1; reasons="$reasons rc-gate($rcg)"; fi
  if [ "$gok" -ne 0 ]; then slips=1; reasons="$reasons structural-gates"; fi
  case "$suite" in
    GREEN) ;;
    NAO-AVALIADO) slips=1; reasons="$reasons suite-nao-avaliado" ;;
    *) slips=1; reasons="$reasons suite-fail" ;;
  esac
  if [ "$slips" -eq 0 ]; then echo "CUT: GREEN"; return 0; fi
  echo "CUT: SLIPS —$reasons"; return 1
}

# parse_suite <file> -> GREEN if a fresh full-suite report shows 0 failures/0 errors; else SLIPS/NAO-AVALIADO
#
# Two accepted producer formats (both are the repo's OWN real artifacts):
#   (a) a Maven reactor log — `Tests run: N, Failures: F, Errors: E, ...` lines +
#       `BUILD SUCCESS` (the historical contract);
#   (b) a `scripts/safe-suite.sh` log — stamped with `SUITE-SHA:`/`SUITE-DIRTY:`
#       BEFORE the run and summarized by `TOTAL: tests=N failures=F errors=E`.
#       This is the format `scripts/stability-report.sh` already trusts; the
#       aggregate gate MUST accept it too (it is the repo's stamped runner) AND
#       it carries provenance, so (b) additionally requires SUITE-DIRTY=0 and
#       SUITE-SHA == HEAD (or $LAB_STABILITY_EXPECT_SHA) — a log from another
#       commit or a dirty tree is not evidence for this tip (never a false green).
parse_suite() {
  local f="${1:-}"
  if [ -z "$f" ] || [ ! -f "$f" ]; then echo "NAO-AVALIADO"; return; fi
  # (b) stamped safe-suite log
  if grep -q "^SUITE-SHA:" "$f" && grep -q "^TOTAL:" "$f"; then
    local want dirty got f_err
    want="${LAB_STABILITY_EXPECT_SHA:-$(git -C "$REPO_DIR" rev-parse HEAD 2>/dev/null || echo unknown)}"
    dirty="$(grep -m1 "^SUITE-DIRTY:" "$f" | awk '{print $2}')"
    got="$(grep -m1 "^SUITE-SHA:" "$f" | awk '{print $2}')"
    [ "$dirty" = "0" ] || { echo "SLIPS"; return; }
    [ "$got" = "$want" ] || { echo "SLIPS"; return; }
    f_err="$(grep -m1 "^TOTAL:" "$f" | awk '{f=0; e=0; for(i=1;i<=NF;i++){ if($i ~ /^failures=/){split($i,a,"="); f=a[2]} if($i ~ /^errors=/){split($i,a,"="); e=a[2]} } print f+e }')"
    [ "${f_err:-NA}" = "NA" ] && { echo "SLIPS"; return; }
    if [ "${f_err:-1}" -eq 0 ]; then echo "GREEN"; else echo "SLIPS"; fi
    return
  fi
  # (a) Maven summary
  local m_err
  m_err="$(grep -oE "Failures: [0-9]+, Errors: [0-9]+" "$f" | awk -F'[ ,]+' '{tf+=$2; te+=$4} END{print tf+te}')"
  if [ "${m_err:-NA}" = "NA" ]; then echo "SLIPS"; return; fi
  if [ "${m_err:-1}" -eq 0 ] && grep -q "BUILD SUCCESS" "$f"; then echo "GREEN"; else echo "SLIPS"; fi
}

run_rc_gate() {
  if [ -n "${LAB_STABILITY_RC_GATE_CMD:-}" ]; then bash -c "$LAB_STABILITY_RC_GATE_CMD" >/dev/null 2>&1; return $?; fi
  bash "$REPO_DIR/scripts/check_release_blockers.sh" --rc-gate >/dev/null 2>&1; return $?
}

run_gate() { # <gate-name> -> rc of the structural gate
  local g="$1"
  if [ -n "${LAB_STABILITY_GATE_CMD:-}" ]; then
    # the hook runs as: bash -c "$LAB_STABILITY_GATE_CMD" _ <gate> ; $1 == gate name
    bash -c "$LAB_STABILITY_GATE_CMD" _ "$g" >/dev/null 2>&1; return $?
  fi
  bash "$REPO_DIR/scripts/$g.sh" >/dev/null 2>&1; return $?
}

evaluate() { # prints verdict, sets exit via return
  local rcg=0 rc_gates=0 g suite_state
  run_rc_gate; rcg=$?
  for g in $STRUCTURAL_GATES; do run_gate "$g" || rc_gates=1; done
  suite_state="$(parse_suite "${LAB_STABILITY_SUITE_REPORT:-}")"
  echo "  rc-gate(0-open-1.0-blocks): rc=$rcg"
  echo "  structural-gates(all rc=0): $([ "$rc_gates" -eq 0 ] && echo OK || echo FAIL)"
  echo "  suite-report: $suite_state ${LAB_STABILITY_SUITE_REPORT:-<none>}"
  verdict "$rcg" "$rc_gates" "$suite_state"
}

selftest() {
  local fail=0 out
  # 1) all green + green suite report -> CUT: GREEN rc 0
  local rep; rep="$(mktemp)"; printf 'Tests run: 2800, Failures: 0, Errors: 0\n[INFO] BUILD SUCCESS\n' > "$rep"
  out="$(LAB_STABILITY_RC_GATE_CMD='exit 0' LAB_STABILITY_GATE_CMD='exit 0' LAB_STABILITY_SUITE_REPORT="$rep" evaluate 2>&1)"; local rc1=$?
  printf '%s\n' "$out" | grep -q "CUT: GREEN" || { echo "selftest: green case not GREEN"; echo "$out"; fail=1; }
  [ "$rc1" -eq 0 ] || { echo "selftest: green case rc expected 0, got $rc1"; fail=1; }
  # 2) one structural gate fails -> SLIPS
  out="$(LAB_STABILITY_RC_GATE_CMD='exit 0' LAB_STABILITY_GATE_CMD='[ "$1" = check_500 ] && exit 1 || exit 0' LAB_STABILITY_SUITE_REPORT="$rep" evaluate 2>&1)"; local rc2=$?
  printf '%s\n' "$out" | grep -q "CUT: SLIPS —.*structural-gates" || { echo "selftest: gate-fail not SLIPS"; echo "$out"; fail=1; }
  [ "$rc2" -eq 1 ] || { echo "selftest: gate-fail rc expected 1, got $rc2"; fail=1; }
  # 3) rc-gate red (1.0-blocks open) -> SLIPS
  out="$(LAB_STABILITY_RC_GATE_CMD='exit 4' LAB_STABILITY_GATE_CMD='exit 0' LAB_STABILITY_SUITE_REPORT="$rep" evaluate 2>&1)"; local rc3=$?
  printf '%s\n' "$out" | grep -q "CUT: SLIPS —.*rc-gate" || { echo "selftest: rc-gate-red not SLIPS"; echo "$out"; fail=1; }
  # 4) NO suite report -> NAO-AVALIADO -> SLIPS (never a silent green)
  out="$(LAB_STABILITY_RC_GATE_CMD='exit 0' LAB_STABILITY_GATE_CMD='exit 0' evaluate 2>&1)"; local rc4=$?
  printf '%s\n' "$out" | grep -q "suite-nao-avaliado" || { echo "selftest: absent suite not NAO-AVALIADO-SLIPS"; echo "$out"; fail=1; }
  [ "$rc4" -eq 1 ] || { echo "selftest: absent-suite rc expected 1, got $rc4"; fail=1; }
  # 5) suite report with a planted failure -> SLIPS even though gates green
  local bad; bad="$(mktemp)"; printf 'Tests run: 2800, Failures: 2, Errors: 0\n[INFO] BUILD FAILURE\n' > "$bad"
  out="$(LAB_STABILITY_RC_GATE_CMD='exit 0' LAB_STABILITY_GATE_CMD='exit 0' LAB_STABILITY_SUITE_REPORT="$bad" evaluate 2>&1)"; local rc5=$?
  printf '%s\n' "$out" | grep -q "CUT: SLIPS —.*suite-fail" || { echo "selftest: planted suite-failure not SLIPS"; echo "$out"; fail=1; }
  # 6) stamped safe-suite log (0F/0E, DIRTY=0, matching SHA) -> CUT: GREEN
  local safe_green; safe_green="$(mktemp)"
  printf 'SUITE-SHA: 111122223333\nSUITE-DIRTY: 0\nTOTAL: tests=5000 failures=0 errors=0 skipped=10\n' > "$safe_green"
  out="$(LAB_STABILITY_RC_GATE_CMD='exit 0' LAB_STABILITY_GATE_CMD='exit 0' LAB_STABILITY_EXPECT_SHA='111122223333' LAB_STABILITY_SUITE_REPORT="$safe_green" evaluate 2>&1)"; local rc6=$?
  printf '%s\n' "$out" | grep -q "CUT: GREEN" || { echo "selftest: safe-suite green case not GREEN"; echo "$out"; fail=1; }
  [ "$rc6" -eq 0 ] || { echo "selftest: safe-suite green case expected rc 0, got $rc6"; fail=1; }
  # 7) stamped safe-suite log with dirty tree (SUITE-DIRTY != 0) -> SLIPS (never a false green)
  local safe_dirty; safe_dirty="$(mktemp)"
  printf 'SUITE-SHA: 111122223333\nSUITE-DIRTY: 2\nTOTAL: tests=5000 failures=0 errors=0 skipped=10\n' > "$safe_dirty"
  out="$(LAB_STABILITY_RC_GATE_CMD='exit 0' LAB_STABILITY_GATE_CMD='exit 0' LAB_STABILITY_EXPECT_SHA='111122223333' LAB_STABILITY_SUITE_REPORT="$safe_dirty" evaluate 2>&1)"; local rc7=$?
  printf '%s\n' "$out" | grep -q "CUT: SLIPS —.*suite-fail" || { echo "selftest: dirty safe-suite log not SLIPS"; echo "$out"; fail=1; }
  [ "$rc7" -eq 1 ] || { echo "selftest: dirty safe-suite log expected rc 1, got $rc7"; fail=1; }
  # 8) stamped safe-suite log with stale SHA -> SLIPS
  out="$(LAB_STABILITY_RC_GATE_CMD='exit 0' LAB_STABILITY_GATE_CMD='exit 0' LAB_STABILITY_EXPECT_SHA='999988887777' LAB_STABILITY_SUITE_REPORT="$safe_green" evaluate 2>&1)"; local rc8=$?
  printf '%s\n' "$out" | grep -q "CUT: SLIPS —.*suite-fail" || { echo "selftest: stale-sha safe-suite log not SLIPS"; echo "$out"; fail=1; }
  [ "$rc8" -eq 1 ] || { echo "selftest: stale-sha safe-suite log expected rc 1, got $rc8"; fail=1; }
  rm -f "$rep" "$bad" "$safe_green" "$safe_dirty"
  if [ "$fail" -eq 0 ]; then echo "selftest: OK (8 verdict cases incl. safe-suite provenance)"; return 0; fi
  return 2
}

case "${1:-}" in
  --selftest) selftest; exit $? ;;
  "") evaluate; exit $? ;;
  *) echo "usage: $0 [--selftest]"; exit 2 ;;
esac
