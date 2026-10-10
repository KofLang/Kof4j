#!/usr/bin/env python3
"""promotion_gate.py — the objective, auditable gate for one promotion step
of the branch pipeline (`D-QUALITY-PIPELINE-2609` / `D-BRANCH-PIPELINE`).

The state machine (`pipeline_state.py`) decides whether a transition is
STRUCTURALLY allowed. This gate decides whether it is EARNABLE: every required
check must be green, the observation window must be complete, and no blocking
issue may be attached. It is a pure function of its inputs (deterministic,
idempotent, no writes, no network) so the same job can be re-run safely.

    lab          -> testing    all required checks PASS
    testing      -> prerelease all required checks PASS + 0 blocking issues
    prerelease   -> stable     7-day window complete + 0 related issues + checks
    stable       -> release/x.y.z  all required checks PASS
    release/x.y.z-> tag        all required checks PASS + version not already tagged

Mandatory check set (fixed, enumerable — the ≥80% denominator was DROPPED per
the maintainer, every promotion is 100%): Build + Tests, Native cross, kof.io
multiplatform, Structural quality gates, CodeQL Gate, bots.

Usage:
    promotion_gate.py --from-stage testing --to-stage prerelease \
        --commit <sha> --version <v> [--checks <file.json>] \
        [--blocking-issues N] [--related-issues N] \
        [--promoted-at ISO] [--timestamp ISO] [--json]
    promotion_gate.py --selftest
Exit: 0 = PASSED, 1 = BLOCKED, 2 = invalid transition or usage error.
Stdlib only.
"""
import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pipeline_state import branch_state, entry_state, evaluate  # noqa: E402

OBSERVATION_DAYS = 7

# Each check maps to the CI job it certifies; the value is the human name.
DEFAULT_CHECKS = (
    "Build + Tests",
    "Native cross",
    "kof.io multiplatform",
    "Structural quality gates",
    "CodeQL Gate",
    "bots",
)

PASS = "PASS"
_PASS_TOKENS = (PASS, "GREEN", "OK", "SUCCESS")


def _parse_checks(path):
    """JSON array [{name,status}] or TSV/lines `name<TAB or :>status`."""
    with open(path, encoding="utf-8") as fh:
        raw = fh.read()
    try:
        data = json.loads(raw)
        if isinstance(data, list):
            return {str(d["name"]): str(d["status"]) for d in data}
        if isinstance(data, dict):
            return {str(k): str(v) for k, v in data.items()}
    except (ValueError, KeyError, TypeError):
        pass
    out = {}
    for line in raw.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        sep = "\t" if "\t" in line else ":"
        name, _, status = line.partition(sep)
        out[name.strip()] = status.strip()
    return out


def _days_between(start_iso, now_iso):
    """Whole days between two ISO-8601 timestamps, or None when unparsable."""
    import datetime

    def parse(s):
        return datetime.datetime.fromisoformat(s.replace("Z", "+00:00"))

    try:
        start, now = parse(start_iso), parse(now_iso)
    except (ValueError, AttributeError):
        return None
    return (now - start).days


def evaluate_gate(from_stage, to_stage, checks, *, version=None, commit=None,
                  blocking_issues=0, related_issues=0, promoted_at=None,
                  timestamp=None, observation_days=OBSERVATION_DAYS,
                  already_tagged=False):
    struct = evaluate(from_stage, to_stage)
    reasons = []
    status = PASS

    if not struct["allowed"]:
        reasons.append(struct["reason"])
        status = "BLOCKED"

    failing = sorted(
        name for name in DEFAULT_CHECKS
        if str(checks.get(name, "")).upper() not in _PASS_TOKENS
    )
    if failing:
        reasons.append("failing/unknown checks: " + ", ".join(failing))
        status = "BLOCKED"

    if to_stage in ("prerelease", "stable") and blocking_issues:
        reasons.append(f"blocking issue(s): {blocking_issues}")
        status = "BLOCKED"

    if struct["to_state"] == "STABLE":
        if related_issues:
            reasons.append(f"related issue(s) open in the pre-release window: {related_issues}")
            status = "BLOCKED"
        if not promoted_at or not timestamp:
            reasons.append("observation window unmeasurable (missing promoted-at/timestamp)")
            status = "BLOCKED"
        else:
            days = _days_between(promoted_at, timestamp)
            if days is None:
                reasons.append("observation window unparsable")
                status = "BLOCKED"
            elif days < observation_days:
                reasons.append(
                    f"observation incomplete: {days}/{observation_days} days"
                )
                status = "BLOCKED"

    if struct["to_state"] == "TAGGED" and already_tagged:
        reasons.append(f"version {version} already tagged (idempotent refusal)")
        status = "BLOCKED"

    return {
        "from_stage": from_stage,
        "to_stage": to_stage,
        "from_state": struct["from_state"],
        "to_state": struct["to_state"],
        "status": status,
        "checks": {name: str(checks.get(name, "NONE")).upper() for name in DEFAULT_CHECKS},
        "blocking_issues": blocking_issues,
        "related_issues": related_issues,
        "commit": commit,
        "version": version,
        "timestamp": timestamp,
        "promoted_at": promoted_at,
        "reasons": reasons,
        "action": "none" if status == PASS else "return_to: lab",
    }


def render(report):
    lines = [
        "Promotion Gate",
        "",
        f"from: {report['from_stage']} ({report['from_state']})",
        f"to:   {report['to_stage']} ({report['to_state']})",
        "",
        f"status: {report['status']}",
        "",
        "checks:",
    ]
    for name, st in report["checks"].items():
        lines.append(f"  {name}: {st}")
    lines += [
        "",
        f"commit: {report['commit']}",
        f"version: {report['version']}",
        f"timestamp: {report['timestamp']}",
        f"blocking_issues: {report['blocking_issues']}",
        f"related_issues: {report['related_issues']}",
    ]
    if report["reasons"]:
        lines += ["", "reason:"] + [f"  - {r}" for r in report["reasons"]]
    if report["status"] != PASS:
        lines += ["", f"action: {report['action']}"]
    return "\n".join(lines)


def _all_pass():
    return {name: PASS for name in DEFAULT_CHECKS}


def selftest():
    ok = True

    def check(name, cond):
        nonlocal ok
        print(f"  {'ok  ' if cond else 'FAIL'}— {name}")
        ok = ok and cond

    # valid + all green
    r = evaluate_gate("lab", "testing", _all_pass(), timestamp="2026-09-28T00:00:00Z")
    check("lab->testing all green = PASSED", r["status"] == PASS)

    # a single red blocks
    bad = _all_pass(); bad["Build + Tests"] = "FAIL"
    r = evaluate_gate("lab", "testing", bad)
    check("one failing check = BLOCKED", r["status"] == "BLOCKED")

    # missing check blocks (fail closed)
    r = evaluate_gate("lab", "testing", {})
    check("missing checks block (fail closed)", r["status"] == "BLOCKED")

    # bypass matrix is blocked by the state machine even with all-green checks
    for fb, tb in (("lab", "prerelease"), ("lab", "stable"), ("testing", "stable"),
                   ("prerelease", "release/1.0.0"), ("stable", "prerelease")):
        r = evaluate_gate(fb, tb, _all_pass(), timestamp="2026-09-28T00:00:00Z",
                          promoted_at="2026-09-01T00:00:00Z")
        check(f"bypass BLOCKED: {fb}->{tb}", r["status"] == "BLOCKED")

    # prerelease->stable: window must be complete and issue-free
    r = evaluate_gate("prerelease", "stable", _all_pass(),
                      promoted_at="2026-09-20T00:00:00Z", timestamp="2026-09-28T00:00:00Z")
    check("prerelease->stable after 8 days, no issues = PASSED", r["status"] == PASS)
    r = evaluate_gate("prerelease", "stable", _all_pass(),
                      promoted_at="2026-09-25T00:00:00Z", timestamp="2026-09-28T00:00:00Z")
    check("prerelease->stable at 3 days = BLOCKED", r["status"] == "BLOCKED")
    r = evaluate_gate("prerelease", "stable", _all_pass(), related_issues=1,
                      promoted_at="2026-09-20T00:00:00Z", timestamp="2026-09-28T00:00:00Z")
    check("prerelease->stable with a related issue = BLOCKED", r["status"] == "BLOCKED")

    # release->tag idempotency
    r = evaluate_gate("stable", "release/1.0.0", _all_pass(), version="1.0.0")
    check("stable->release all green = PASSED", r["status"] == PASS)
    r = evaluate_gate("release/1.0.0", "kof-1.0.0-linux-x86_64", _all_pass(),
                      version="1.0.0", already_tagged=True)
    check("duplicate tag refused (idempotent)", r["status"] == "BLOCKED")

    # render carries the audit fields
    text = render(evaluate_gate("lab", "testing", _all_pass(),
                                commit="abc", version="0.6.0",
                                timestamp="2026-09-28T00:00:00Z"))
    check("render names from/to/status", "from: lab (LAB)" in text and "status: PASS" in text)

    return ok


def main(argv=None):
    argv = sys.argv[1:] if argv is None else argv
    if "--selftest" in argv:
        ok = selftest()
        print("promotion_gate --selftest:", "OK" if ok else "FAIL")
        return 0 if ok else 1
    p = argparse.ArgumentParser(add_help=True)
    p.add_argument("--from-stage", required=True)
    p.add_argument("--to-stage", required=True)
    p.add_argument("--commit", default="")
    p.add_argument("--version", default="")
    p.add_argument("--checks", default="")
    p.add_argument("--blocking-issues", type=int, default=0)
    p.add_argument("--related-issues", type=int, default=0)
    p.add_argument("--promoted-at", default="")
    p.add_argument("--timestamp", default="")
    p.add_argument("--already-tagged", action="store_true")
    p.add_argument("--json", action="store_true")
    args = p.parse_args(argv)

    checks = _parse_checks(args.checks) if args.checks else {}
    report = evaluate_gate(
        args.from_stage, args.to_stage, checks, version=args.version,
        commit=args.commit, blocking_issues=args.blocking_issues,
        related_issues=args.related_issues, promoted_at=args.promoted_at,
        timestamp=args.timestamp, already_tagged=args.already_tagged,
    )
    print(json.dumps(report, indent=2, sort_keys=True) if args.json else render(report))
    return 0 if report["status"] == PASS else 1


if __name__ == "__main__":
    sys.exit(main())
