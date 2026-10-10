#!/usr/bin/env bash
# check_known_bugs_status.sh — machine ledger of known-bugs section statuses.
#
# Why: counting OPEN bugs "by eye" has repeatedly produced wrong numbers in
# commits and DOING ticks (estimates of 40, 13, 12 that disagreed with each
# other). The STABILITY condition says "all bugs resolved" — that must be a
# MEASUREMENT, not an opinion. Multi-face sections (§272 style) and buried
# **Status** lines defeat naive one-line greps.
#
# Per-section classification (body scanned = first 60 lines after the header):
# A section is classified by the LAST status-bearing line (header suffix,
# **Status**, Fechamento/Resolution/Fix, or "— ✅|🟡|🔴") — chronological append
# wins, history-quotes (🔴 inside prose without a status prefix) do not.
#   closed  last status is ✅-based.   live  last status is 🟡/🔴/OPEN (multi-face
#           counts as live while ANY face is open).   unknown = docs-lane debt.
# Cross-check: OPEN set must be identical between known-bugs.md and .pt_BR.md
# (docs-lang.sh checks pairing, not per-section state).
#
# Usage:
#   scripts/check_known_bugs_status.sh            # report + EN×PT drift (rc!=0 on drift)
#   scripts/check_known_bugs_status.sh --selftest # planted fixture must classify right
#
# Exit codes: 0 healthy; 1 drift/unknown; 2 selftest failure.

set -u
REPO="$(cd "$(dirname "$0")/.." && pwd)"
EN="$REPO/docs/bugs-and-gaps/known-bugs.md"
PT="$REPO/docs/bugs-and-gaps/known-bugs.pt_BR.md"

classify() { # $1=file -> "NNN status" lines
  awk '
    function flush() {
      if (sid == "") return
      print num, (last == "" ? "unknown" : last)
    }
    /^[[:space:]]*#+ §[0-9]+/ {
      flush()
      sid++; n++
      hdr = $0; last = ""
      t = $0; sub(/^[[:space:]]*#*[[:space:]]*§/, "", t); gsub(/[^0-9].*/, "", t); num = t
      # status embedded in the header line itself (suffix after the dash)
      if ($0 ~ /✅/) last = "closed"
      else if ($0 ~ /🟡|🔴|🔓/) last = "live"
      next
    }
    sid != "" {
      # status-bearing lines only (chronological append wins)
      if ($0 ~ /\*\*[Ss]tatus\*\*|Estat/ || $0 ~ /^[>\- ]*\*\*(Status|Estado|Resolu|Fechamento|Resolution|Fix|✅|🟡|🔴|⚠️|CORRIGIDO|FECHADO|FIXED|RESOLVIDO)/ || $0 ~ /— *(✅|🟡|🔴)/ || $0 ~ /\*\*(✅|🟡|🔴|⚠️) /) {
        if (($0 ~ /✅/ || $0 ~ /🟢/) && $0 !~ /🟡|🔴/) last = "closed"
        else if ($0 ~ /🟡|🔴|🔓|OPEN|ABERTO|PARTIAL|PARCIAL/) last = "live"
      }
    }
    END { flush() }
  ' "$1"
}

compare_ledgers() { # $1=EN file  $2=PT file -> report on stdout; rc 0 healthy, 1 drift
  local EN_LEDGER PT_LEDGER EN_OPEN PT_OPEN EN_UNK PT_UNK
  local EN_ALL PT_ALL ONLY_EN ONLY_PT EN_DUP PT_DUP rc=0
  EN_LEDGER="$(classify "$1")"
  PT_LEDGER="$(classify "$2")"
  EN_OPEN="$(echo "$EN_LEDGER" | awk '$2=="live"{print $1}' | sort -n)"
  PT_OPEN="$(echo "$PT_LEDGER" | awk '$2=="live"{print $1}' | sort -n)"
  EN_UNK="$(echo "$EN_LEDGER" | awk '$2=="unknown"{print $1}' | sort -n | tr '\n' ' ')"
  PT_UNK="$(echo "$PT_LEDGER" | awk '$2=="unknown"{print $1}' | sort -n | tr '\n' ' ')"

  # duplicate-number check (collision fix 18/09: native copies of §266/§267 became §283/§284)
  EN_DUP="$(echo "$EN_LEDGER" | awk '{print $1}' | sort -n | uniq -d | tr '\n' ' ')"
  PT_DUP="$(echo "$PT_LEDGER" | awk '{print $1}' | sort -n | uniq -d | tr '\n' ' ')"
  if [[ -n "${EN_DUP// }" || -n "${PT_DUP// }" ]]; then
    echo "DUPLICATED SECTION NUMBERS: EN[$EN_DUP] PT[$PT_DUP]"; rc=1
  fi

  # §378 (20/09): the gate compared ONLY the open set, so a section born in a
  # single language — any status, e.g. a ✅ FIXED §376/§377 only in EN — passed
  # green forever (both open sets are empty for it and it is not "unknown").
  # Now the WHOLE section-number set is compared in BOTH directions.
  EN_ALL="$(echo "$EN_LEDGER" | awk '{print $1}' | LC_ALL=C sort)"
  PT_ALL="$(echo "$PT_LEDGER" | awk '{print $1}' | LC_ALL=C sort)"
  ONLY_EN="$(LC_ALL=C comm -23 <(printf '%s\n' "$EN_ALL") <(printf '%s\n' "$PT_ALL") | tr '\n' ' ')"
  ONLY_PT="$(LC_ALL=C comm -13 <(printf '%s\n' "$EN_ALL") <(printf '%s\n' "$PT_ALL") | tr '\n' ' ')"
  if [[ -n "${ONLY_EN// }" || -n "${ONLY_PT// }" ]]; then
    echo "DRIFT: section present in only one language — mirror it in both:"
    echo "  EN-only: [$ONLY_EN]  PT-only: [$ONLY_PT]"
    rc=1
  fi

  echo "EN open/partial ($(echo "$EN_OPEN" | grep -c .)): $(echo "$EN_OPEN" | tr '\n' ' ')"
  echo "PT open/partial ($(echo "$PT_OPEN" | grep -c .)): $(echo "$PT_OPEN" | tr '\n' ' ')"
  if [[ "$EN_OPEN" != "$PT_OPEN" ]]; then
    echo "DRIFT: EN×PT open-sets differ:"; diff <(echo "$EN_OPEN") <(echo "$PT_OPEN") | sed 's/^/  /'; rc=1
  fi
  if [[ -n "${EN_UNK// }" || -n "${PT_UNK// }" ]]; then
    echo "UNKNOWN (no readable status — docs-lane debt): EN[$EN_UNK] PT[$PT_UNK]"; rc=1
  fi
  [[ $rc -eq 0 ]] && echo "OK: statuses consistent EN×PT, no unknowns"
  return $rc
}

if [[ "${1:-}" == "--classify" ]]; then
  # modo máquina (ex.: check_doc_impact #656): classifica UM ledger arbitrário
  # sem rodar o cross-check EN×PT. Uso: --classify FILE
  [ -f "${2:-}" ] || { echo "usage: check_known_bugs_status.sh --classify FILE" >&2; exit 2; }
  classify "$2"
  exit 0
fi

if [[ "${1:-}" == "--selftest" ]]; then
  FIX="$(mktemp -d)"
  cat > "$FIX/t.md" <<'EOF'
## §900 — closed sample — ✅ FIXED 18/09
**Status:** ✅ FIXED 18/09. Proof: green.
## §901 — live sample
- **Status:** 🟡 OPEN 18/09. Not fixed.
## §902 — multiface sample
**Face (a)** — ✅ FIXED 17/09 done.
**Face (b)** — 🟡 OPEN (lane nat): pending.
## §903 — no status token
just prose here
## §904 — warning-face sample
- **✅ FIXED 16/09 (JS face):** done here.
- **⚠️ NATIVE face REMAINS OPEN (different lane):** not silently fixed.
## §905 — gap sample — 🔓 GAPS OPEN 19/09
- **Catalogued (Q7):** honest diagnostics landed; resolution pending.
  ## §906 — indented heading sample — ✅ FIXED 20/09
**Status:** ✅ FIXED 20/09. Leading whitespace before the heading must not hide it.
EOF
  OUT="$(classify "$FIX/t.md")"
  rm -rf "$FIX"
  echo "$OUT"
  echo "$OUT" | grep -qx "900 closed"      || { echo "SELFTEST FAIL: 900"; exit 2; }
  echo "$OUT" | grep -qx "901 live"        || { echo "SELFTEST FAIL: 901"; exit 2; }
  echo "$OUT" | grep -qx "902 live" || { echo "SELFTEST FAIL: 902"; exit 2; }
  echo "$OUT" | grep -qx "903 unknown"     || { echo "SELFTEST FAIL: 903"; exit 2; }
  echo "$OUT" | grep -qx "904 live"       || { echo "SELFTEST FAIL: 904"; exit 2; }
  echo "$OUT" | grep -qx "905 live"       || { echo "SELFTEST FAIL: 905"; exit 2; }
  echo "$OUT" | grep -qx "906 closed"      || { echo "SELFTEST FAIL: 906 (indented heading not seen)"; exit 2; }

  # §378 selftest: a section born in only ONE language must fail the gate
  # (the real case: §376/§377 FIXED only in EN passed green forever).
  FIX2="$(mktemp -d)"
  printf '## §910 — fixed only in EN — ✅ FIXED 20/09\n**Status:** ✅ FIXED 20/09.\n' > "$FIX2/en.md"
  printf '## §911 — fixed only in PT — ✅ CORRIGIDO 20/09\n**Status:** ✅ CORRIGIDO 20/09.\n' > "$FIX2/pt.md"
  if compare_ledgers "$FIX2/en.md" "$FIX2/pt.md" >/dev/null 2>&1; then
    echo "SELFTEST FAIL: §378 unilingue section not caught (EN-only §910 / PT-only §911)"; exit 2
  fi
  printf '## §910 — fixado em ambos — ✅ CORRIGIDO 20/09\n**Status:** ✅ CORRIGIDO 20/09.\n' > "$FIX2/pt.md"
  compare_ledgers "$FIX2/en.md" "$FIX2/pt.md" >/dev/null 2>&1 \
    || { echo "SELFTEST FAIL: §378 mirrored section still red"; exit 2; }
  rm -rf "$FIX2"
  echo "SELFTEST OK"
  exit 0
fi

compare_ledgers "$EN" "$PT"
exit $?
