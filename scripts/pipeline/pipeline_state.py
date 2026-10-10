#!/usr/bin/env python3
"""pipeline_state.py — the branch pipeline as an EXPLICIT state machine
(`D-QUALITY-PIPELINE-2609`, `D-BRANCH-PIPELINE`).

The pipeline is not a naming convention: promotion is a transition between
named states, and any transition not in `ALLOWED` is BLOCKED. The machine
prefers to fail closed — an unknown branch, an unknown state, or a no-op is
never "allowed", it is refused.

    LAB → TESTING → PRERELEASE → PRERELEASE_OBSERVATION
                                        → STABLE → RELEASE → TAGGED

Failure on any non-lab stage does not create a parallel flow; it returns the
work to the single development entry point:

    TESTING|PRERELEASE|PRERELEASE_OBSERVATION|STABLE|RELEASE → BLOCKED → LAB

Branch → state (the state a branch holds once promoted to):

    lab              LAB
    testing          TESTING
    prerelease       PRERELEASE_OBSERVATION  (entering prerelease starts the window)
    stable           STABLE
    release/x.y.z    RELEASE
    refs/tags/kof-*  TAGGED

Usage:
    pipeline_state.py branch-state <branch>
    pipeline_state.py transition <from-state> <to-state>
    pipeline_state.py promote <from-branch> <to-branch> [--json]
    pipeline_state.py --selftest
Exit: 0 = allowed/valid, 1 = blocked/invalid, 2 = usage/parse error.
Stdlib only. Deterministic (no clock, no network).
"""
import json
import re
import sys

STATES = (
    "LAB",
    "TESTING",
    "PRERELEASE",
    "PRERELEASE_OBSERVATION",
    "STABLE",
    "RELEASE",
    "TAGGED",
    "BLOCKED",
)

# Allowed forward/rollback transitions. Rollback from any promoted stage goes
# to BLOCKED, and BLOCKED only ever returns to LAB (never sideways).
ALLOWED = {
    "LAB": ("TESTING",),
    "TESTING": ("PRERELEASE", "BLOCKED"),
    "PRERELEASE": ("PRERELEASE_OBSERVATION", "BLOCKED"),
    "PRERELEASE_OBSERVATION": ("STABLE", "BLOCKED"),
    "STABLE": ("RELEASE", "BLOCKED"),
    "RELEASE": ("TAGGED", "BLOCKED"),
    "BLOCKED": ("LAB",),
    "TAGGED": (),
}

# held = the state a branch carries (source of a promotion); entry = the state
# its incoming transition targets. They differ for `prerelease`: entering it is
# PRERELEASE (the pre-release is cut), then it settles into the observation
# window. A source is judged by held, a target by entry.
_BRANCH_HELD = {
    "lab": "LAB",
    "testing": "TESTING",
    "prerelease": "PRERELEASE_OBSERVATION",
    "stable": "STABLE",
}
_BRANCH_ENTRY = {
    "lab": "LAB",
    "testing": "TESTING",
    "prerelease": "PRERELEASE",
    "stable": "STABLE",
}
_RELEASE_RE = re.compile(r"^release/[0-9]+\.[0-9]+\.[0-9]+$")
_TAG_RE = re.compile(r"^refs/tags/kof-")


def _branch_kind(branch):
    """(held_state, entry_state) for a branch/ref, or (None, None) when it
    cannot be determined safely (fail closed; never guess)."""
    if branch is None:
        return (None, None)
    b = branch.strip()
    if b in _BRANCH_HELD:
        return (_BRANCH_HELD[b], _BRANCH_ENTRY[b])
    if _RELEASE_RE.match(b):
        return ("RELEASE", "RELEASE")
    if _TAG_RE.match(b) or b.startswith("kof-"):
        return ("TAGGED", "TAGGED")
    return (None, None)


def branch_state(branch):
    """Held state of a branch/ref. None when unknown (fail closed)."""
    return _branch_kind(branch)[0]


def entry_state(branch):
    """State a promotion INTO this branch targets. None when unknown."""
    return _branch_kind(branch)[1]


def normalize_state(name):
    if name is None:
        return None
    return name.strip().upper().replace("-", "_")


def transition_allowed(from_state, to_state):
    f = normalize_state(from_state)
    t = normalize_state(to_state)
    if f not in ALLOWED or t not in STATES:
        return False
    return t in ALLOWED[f]


def promotion_allowed(from_branch, to_branch):
    """Promotion between two branches/tags. Refused (False) on any unknown
    endpoint, same-state no-op, or transition not in `ALLOWED`. The source is
    judged by its held state, the target by its entry state."""
    fs = branch_state(from_branch)
    ts = entry_state(to_branch)
    if fs is None or ts is None:
        return False
    return transition_allowed(fs, ts)


def evaluate(from_branch, to_branch):
    fs = branch_state(from_branch)
    ts = entry_state(to_branch)
    if fs is None:
        return {
            "allowed": False,
            "from_branch": from_branch,
            "to_branch": to_branch,
            "from_state": None,
            "to_state": ts,
            "reason": f"unknown source '{from_branch}' (fail closed)",
        }
    if ts is None:
        return {
            "allowed": False,
            "from_branch": from_branch,
            "to_branch": to_branch,
            "from_state": fs,
            "to_state": None,
            "reason": f"unknown target '{to_branch}' (fail closed)",
        }
    if branch_state(from_branch) == branch_state(to_branch):
        return {
            "allowed": False,
            "from_branch": from_branch,
            "to_branch": to_branch,
            "from_state": fs,
            "to_state": ts,
            "reason": f"no-op: both ends are {fs}",
        }
    ok = transition_allowed(fs, ts)
    return {
        "allowed": ok,
        "from_branch": from_branch,
        "to_branch": to_branch,
        "from_state": fs,
        "to_state": ts,
        "reason": (
            f"{fs} -> {ts} is an allowed promotion"
            if ok
            else f"{fs} -> {ts} is NOT an allowed transition (fail closed)"
        ),
    }


def selftest():
    ok = True

    def check(name, cond):
        nonlocal ok
        print(f"  {'ok  ' if cond else 'FAIL'}— {name}")
        ok = ok and cond

    # The exact valid promotions of the pipeline.
    for fb, tb in (
        ("lab", "testing"),
        ("testing", "prerelease"),
        ("prerelease", "stable"),
        ("stable", "release/1.0.0"),
        ("release/1.0.0", "kof-1.0.0-linux-x86_64"),
    ):
        check(f"allowed: {fb} -> {tb}", promotion_allowed(fb, tb))

    # The bypass matrix from the contract — every one MUST be blocked.
    for fb, tb in (
        ("lab", "prerelease"),
        ("lab", "stable"),
        ("testing", "stable"),
        ("prerelease", "release/1.0.0"),
        ("lab", "release/1.0.0"),
        ("stable", "prerelease"),
        ("testing", "lab"),
        ("stable", "lab"),
    ):
        check(f"BLOCKED: {fb} -> {tb}", not promotion_allowed(fb, tb))

    check("no-op blocked (lab -> lab)", not promotion_allowed("lab", "lab"))
    check("unknown source fails closed", not promotion_allowed("beta-0.5.0", "testing"))
    check("unknown target fails closed", not promotion_allowed("lab", "beta-0.5.0"))
    check("release pattern strict (release/x blocked)",
          branch_state("release/x") is None)
    check("release 0.5.0 maps to RELEASE",
          branch_state("release/0.5.0") == "RELEASE")
    check("tag maps to TAGGED",
          branch_state("kof-1.0.0-linux-x86_64") == "TAGGED")
    check("failure returns to LAB (BLOCKED -> LAB)",
          transition_allowed("BLOCKED", "LAB"))

    # evaluate() carries the machine-readable reason for the audit log.
    ev = evaluate("lab", "stable")
    check("evaluate(lab,stable) blocked with from/to states",
          ev["allowed"] is False and ev["from_state"] == "LAB"
          and ev["to_state"] == "STABLE")

    return ok


def main(argv):
    if "--selftest" in argv:
        ok = selftest()
        print("pipeline_state --selftest:", "OK" if ok else "FAIL")
        return 0 if ok else 1
    if not argv:
        print(__doc__.strip())
        return 2
    cmd = argv[0]
    if cmd == "branch-state" and len(argv) == 2:
        st = branch_state(argv[1])
        if st is None:
            print(f"unknown branch: {argv[1]} (fail closed)", file=sys.stderr)
            return 1
        print(st)
        return 0
    if cmd == "transition" and len(argv) == 3:
        if transition_allowed(argv[1], argv[2]):
            print(f"{normalize_state(argv[1])} -> {normalize_state(argv[2])}: allowed")
            return 0
        print(f"{argv[1]} -> {argv[2]}: BLOCKED", file=sys.stderr)
        return 1
    if cmd == "promote" and len(argv) >= 3:
        ev = evaluate(argv[1], argv[2])
        if "--json" in argv:
            print(json.dumps(ev, indent=2, sort_keys=True))
        else:
            status = "allowed" if ev["allowed"] else "BLOCKED"
            print(f"promote {argv[1]} -> {argv[2]}: {status} ({ev['reason']})")
        return 0 if ev["allowed"] else 1
    print(__doc__.strip(), file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
