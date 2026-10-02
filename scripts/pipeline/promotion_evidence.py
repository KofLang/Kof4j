#!/usr/bin/env python3
"""promotion_evidence.py — 14.3 (#657): the collector that turns MEASUREMENTS
into the evidence `promotion_gate.py` consumes ("scripted proof, not opinion").

Pure and network-free: the workflow runs the suite (`scripts/safe-suite.sh`)
and counts issues (`gh issue list`); this module only maps those artifacts:

  * `Build + Tests` := verdict of a REAL full-suite run on the exact tip
    (rc of `scripts/stability-report.sh` against the stamped log — 0 GREEN,
    1 RED, 3 no-log). The suite run OVERRIDES any check-run with the same
    name: the dispatched tip is what gets certified, not an older CI run.
  * every other required check := conclusion of a matching check-run on the
    tip, or `NONE` when absent (the gate fails closed on NONE).
  * `--blocking-issues` / `--related-issues` are passed through so the final
    `gate-args` line documents the measured numbers the workflow must feed
    the gate (the defaults-0 path is exactly the opinion 14.3 forbids).

Usage:
    promotion_evidence.py --sha SHA [--check-runs TSV] [--suite-log FILE]
                          [--stability-cmd CMD ...] [--checks-out FILE]
                          [--gate-args-out FILE] [--report-out FILE]
    promotion_evidence.py --selftest
Exit: 0 = evidence built (a FAIL verdict is still evidence — the gate
blocks); 2 = usage error. Stdlib only.
"""
import argparse
import json
import shlex
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from promotion_gate import DEFAULT_CHECKS  # noqa: E402  (single source of the set)

PASS = "PASS"
FAIL = "FAIL"
NONE = "NONE"


def parse_check_runs(path):
    """TSV `name<TAB>conclusion` (from `gh api .../check-runs`) -> {name: status}."""
    hits = {}
    try:
        with open(path, encoding="utf-8") as fh:
            for line in fh:
                name, _, conclusion = line.rstrip("\n").partition("\t")
                if name.strip():
                    hits[name.strip()] = conclusion.strip() or NONE
    except FileNotFoundError:
        pass
    return hits


def suite_verdict(stability_cmd, suite_log, sha):
    """Run the canonical stability reporter; map its rc to a check status.
    rc 0=GREEN->PASS, 1=RED->FAIL, 3=no-log->NONE; any other rc fails closed."""
    if not suite_log:
        return NONE, "suite not run by this promotion (no --suite-log)"
    cmd = list(stability_cmd) + ["--suite-log", suite_log]
    if sha:
        cmd += ["--sha", sha]
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True)
    except OSError as exc:
        return FAIL, f"stability reporter unrunnable: {exc}"
    status = {0: PASS, 1: FAIL, 3: NONE}.get(proc.returncode, FAIL)
    note = (proc.stdout or proc.stderr or "").strip().splitlines()
    detail = note[-1] if note else f"rc={proc.returncode}"
    return status, f"suite on tip: {detail}"


def build(sha, check_runs_path=None, suite_log=None,
          stability_cmd=None):
    hits = parse_check_runs(check_runs_path) if check_runs_path else {}
    checks, notes = {}, []
    for required in DEFAULT_CHECKS:
        if required == "Build + Tests":
            status, note = suite_verdict(
                stability_cmd or ["scripts/stability-report.sh"], suite_log, sha)
            checks[required] = status
            notes.append(f"Build + Tests <- {note}")
            continue
        hit = next((c for n, c in hits.items() if required in n), None)
        checks[required] = hit if hit else NONE
        notes.append(f"{required} <- " + (f"check-run `{hit}`" if hit else "no check-run"))
    return checks, notes


def selftest():
    ok = True

    def check(name, cond):
        nonlocal ok
        print(f"  {'ok  ' if cond else 'FAIL'}— {name}")
        ok = ok and cond

    t = os.path.join(os.path.sep + "tmp" if os.sep == "/" else os.sep, "promotion_evidence_selftest")
    os.makedirs(t, exist_ok=True)
    runs = os.path.join(t, "runs.tsv")
    log = os.path.join(t, "suite.log"); open(log, "w").write("TOTAL: tests=1 failures=0 errors=0 skipped=0\n")
    with open(runs, "w", encoding="utf-8") as fh:
        fh.write("Native cross\tsuccess\n")
        fh.write("bots\tfailure\n")
    checks, _ = build("abc", check_runs_path=runs, suite_log=log,
                      stability_cmd=["bash", "-c", "exit 0"])
    check("check-runs map: hit=conclusion", checks["Native cross"] == "success")
    check("check-runs map: red stays red", checks["bots"] == "failure")
    check("missing check-run = NONE (fail closed)", checks["CodeQL Gate"] == NONE)
    check("suite green (rc0) -> Build+Tests PASS", checks["Build + Tests"] == PASS)

    checks, _ = build("abc", check_runs_path=runs, suite_log=log,
                      stability_cmd=["bash", "-c", "exit 1"])
    check("suite red (rc1) -> FAIL even if a stale check-run was green",
          checks["Build + Tests"] == FAIL)

    checks, _ = build("abc", check_runs_path=runs,
                      stability_cmd=["bash", "-c", "exit 3"])
    check("suite log not stamped (rc3) -> NONE (never false green)",
          checks["Build + Tests"] == NONE)

    checks, _ = build("abc", check_runs_path=runs, suite_log="/nonexistent/kof.log",
                      stability_cmd=["bash", "-c", "exit 3"])
    check("suite log missing -> NONE", checks["Build + Tests"] == NONE)

    checks, _ = build("abc", check_runs_path=runs, suite_log=log,
                      stability_cmd=["bash", "-c", "exit 7"])
    check("unexpected reporter rc fails closed (FAIL)", checks["Build + Tests"] == FAIL)

    checks, _ = build("abc", check_runs_path=None, suite_log=None,
                      stability_cmd=["bash", "-c", "exit 0"])
    check("suite not run -> NONE even when reporter would pass",
          checks["Build + Tests"] == NONE)

    import shutil
    if os.path.exists(t):
        shutil.rmtree(t)
    return ok


def main(argv=None):
    argv = sys.argv[1:] if argv is None else argv
    if "--selftest" in argv:
        ok = selftest()
        print("promotion_evidence --selftest:", "OK" if ok else "FAIL")
        return 0 if ok else 1
    p = argparse.ArgumentParser(add_help=True)
    p.add_argument("--sha", required=True)
    p.add_argument("--check-runs", default="")
    p.add_argument("--suite-log", default="")
    p.add_argument("--stability-cmd", default="scripts/stability-report.sh")
    p.add_argument("--blocking-issues", type=int, default=-1)
    p.add_argument("--related-issues", type=int, default=-1)
    p.add_argument("--checks-out", default="")
    p.add_argument("--gate-args-out", default="")
    p.add_argument("--report-out", default="")
    args = p.parse_args(argv)

    if args.blocking_issues < 0 or args.related_issues < 0:
        print("promotion_evidence: --blocking-issues/--related-issues sao obrigatorios "
              "(a promocao nao pode assumir 0)", file=sys.stderr)
        return 2

    stability_cmd = shlex.split(args.stability_cmd)
    checks, notes = build(args.sha, args.check_runs or None,
                          args.suite_log or None, stability_cmd)
    if args.checks_out:
        with open(args.checks_out, "w", encoding="utf-8") as fh:
            json.dump(checks, fh, indent=2)
    if args.gate_args_out:
        with open(args.gate_args_out, "w", encoding="utf-8") as fh:
            fh.write(f"{args.blocking_issues}\t{args.related_issues}\n")
    if args.report_out:
        with open(args.report_out, "a", encoding="utf-8") as fh:
            fh.write("### Promotion evidence (14.3)\n\n```\n")
            fh.write(f"tip: {args.sha}\n")
            for line in notes:
                fh.write(line + "\n")
            fh.write(f"blocking-issues: {args.blocking_issues}\n")
            fh.write(f"related-issues: {args.related_issues}\n")
            fh.write("```\n")
    for name in DEFAULT_CHECKS:
        print(f"{name}\t{checks[name]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
