#!/usr/bin/env bash
#
# compare-size.sh — D-SIZE-BUDGET Phase 1: diff two baselines produced by
# measure-size.sh (kind<TAB>name<TAB>value). Prints one line per changed key:
#
#   kind<TAB>name<TAB>+delta      grew by delta
#   kind<TAB>name<TAB>-delta      shrank by delta
#   kind<TAB>name<TAB>+value (new) / -value (gone)
#
# Keys whose value is "unavailable" (absent toolchain) are reported once as a
# note, never treated as 0.
#
# Usage: scripts/size/compare-size.sh OLD.tsv NEW.tsv
set -uo pipefail

A="${1:-}"; B="${2:-}"
if [ -z "$A" ] || [ -z "$B" ] || [ ! -f "$A" ] || [ ! -f "$B" ]; then
    echo "uso: $0 OLD.tsv NEW.tsv" >&2; exit 2
fi

python3 - "$A" "$B" <<'PY'
import sys

def load(path):
    vals, unavail = {}, set()
    for line in open(path, encoding="utf-8"):
        line = line.rstrip("\n")
        if not line or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) < 3:
            continue
        kind, name, val = parts[0], parts[1], parts[2]
        key = f"{kind}\t{name}"
        if val == "unavailable":
            unavail.add(key)
            continue
        try:
            vals[key] = int(val)
        except ValueError:
            continue
    return vals, unavail

a, a_un = load(sys.argv[1])
b, b_un = load(sys.argv[2])
changed = 0
for key in sorted(set(a) | set(b)):
    if key in a and key in b:
        d = b[key] - a[key]
        if d != 0:
            print(f"{key}\t{'+' if d > 0 else ''}{d}")
            changed += 1
    elif key in b:
        print(f"{key}\t+{b[key]} (new)")
        changed += 1
    else:
        print(f"{key}\t-{a[key]} (gone)")
        changed += 1
for key in sorted(b_un - a_un):
    print(f"{key}\tunavailable (new)")
for key in sorted(a_un - b_un):
    print(f"{key}\tunavailable (gone)")
if changed == 0 and a_un == b_un:
    print("# sem mudanca de tamanho")
PY
