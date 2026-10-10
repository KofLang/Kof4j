#!/usr/bin/env bash
# check_plan_owners.sh — `D-PLAN-ONE-OWNER` (maintainer 02/10): every plan in
# docs/development/ carries a NAMED unique owner (`IP:PORTA`) in its header,
# and NO identity owns more than one plan. "this session"/"lane X" without
# IP:PORTA is INVALID (same absolute-identity law as `D-AGENT-IDENTITY-IPPORT`).
# Plans still awaiting a re-claim must say so explicitly ("SEM DONO / OPEN") —
# that is honest; a shared/silent owner is not.
# Uso: scripts/check_plan_owners.sh            # rc!=0 em violação
#      scripts/check_plan_owners.sh --selftest
set -u
cd "$(dirname "$0")/.."
exec python3 - "${1:-}" << 'PYEOF'
import re, sys, glob

SELF = len(sys.argv) > 1 and sys.argv[1] == "--selftest"
IPPORT = re.compile(r"\b(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}:\d{1,5})\b")

def owners_from(text):
    out = []
    for line in text.split("\n"):
        if re.match(r"^\*\*(?:Owner|Dono):\*\*|^owner:|^dona:", line):
            if re.search(r"SEM DONO|SEM DONOR|OPEN —|OPEN -|ABERTO", line):
                out.append("OPEN")
            else:
                ips = IPPORT.findall(line)
                if ips:
                    out.append(ips[0])
                elif "maintainer" in line.lower():
                    out.append("maintainer")
                else:
                    out.append("INVALID")
    return out

def scan(files):
    per = {}
    bad = []
    for f in files:
        try:
            text = open(f, encoding="utf-8").read()
        except FileNotFoundError:
            bad.append(f"{f}: arquivo ausente")
            continue
        o = owners_from(text)
        if not o:
            bad.append(f"{f}: sem campo Owner/Dono no cabeçalho")
            continue
        if "INVALID" in o:
            bad.append(f"{f}: owner sem IP:PORTA nem marcação SEM DONO/maintainer")
            continue
        chosen = [x for x in o if x not in ("OPEN",)][0] if [x for x in o if x not in ("OPEN",)] else "OPEN"
        if chosen in ("maintainer", "OPEN"):
            continue
        per.setdefault(chosen, []).append(f)
    for ip, fs in per.items():
        if len(fs) > 1:
            bad.append(f"owner {ip} em {len(fs)} planos ({', '.join(fs)}) — violação D-PLAN-ONE-OWNER")
    return bad

plan_files = sorted(
    [f for f in glob.glob("docs/development/*plan*.md") if ".pt_BR." not in f]
    + ["docs/architecture/IMPLEMENTATION-UNIVERSAL-PLATFORM.md"]
)

bad = scan(plan_files)

if SELF:
    import os, tempfile, shutil
    d = tempfile.mkdtemp(prefix="planowner-selftest-")
    try:
        a = os.path.join(d, "alpha-plan.md")
        b = os.path.join(d, "beta-plan.md")
        open(a, "w").write("# A\n\n**Owner:** 9.9.9.9:1 (lane x)\n")
        open(b, "w").write("# B\n\n**Owner:** 9.9.9.9:1 (lane x)\n")
        dup = scan([a, b])
        if not any("D-PLAN-ONE-OWNER" in x for x in dup):
            print("SELFTEST FALHOU: dono duplicado não foi pego"); sys.exit(1)
        open(b, "w").write("# B\n\n**Owner:** issues/tooling lane (this session)\n")
        inv = scan([a, b])
        if not any("sem IP:PORTA" in x for x in inv):
            print("SELFTEST FALHOU: owner anônimo não foi pego"); sys.exit(1)
        open(b, "w").write("# B\n")
        nof = scan([a, b])
        if not any("sem campo" in x for x in nof):
            print("SELFTEST FALHOU: plano sem Owner não foi pego"); sys.exit(1)
    finally:
        shutil.rmtree(d, ignore_errors=True)
    print("selftest OK (duplicado + anônimo + ausente capturados)")
    sys.exit(0)

if bad:
    for x in bad:
        print("VIOLACAO:", x)
    sys.exit(1)
n = len(plan_files)
print(f"OK: {n} planos, dono único nomeado ou SEM DONO explícito (D-PLAN-ONE-OWNER)")
PYEOF
