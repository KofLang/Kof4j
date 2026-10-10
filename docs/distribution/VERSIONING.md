[English](VERSIONING.md) | [Português](VERSIONING.pt_BR.md)

# Kof Versioning

## Format

```text
MAJOR.MINOR.PATCH
```

The conceptual hierarchy:

```text
Major releases
    >
Major fixes
    >
Bugfixes
```

| Component | Meaning |
|-----------|-------------|
| `X` (MAJOR) | Major release |
| `Y` (MINOR) | Major fix / significant evolution |
| `Z` (PATCH) | Bugfix — the *little dot of shame* |

`PATCH` is affectionately called the **little dot of shame** because it
mainly represents:

- bugfix;
- fix;
- regression;
- small adjustments;
- small improvements without relevant architectural change.

## Current stage

Kof is on the **0.5.0 beta** line (active development branch `lab`,
`D-BRANCH-PIPELINE`; the 0.5.0 line was ratified on `beta-0.5.0`/`D-BRANCH-0.5.0`,
`SUPERSEDED` by the 28/09 cutover).
The committed version is `0.5.0-beta`; the previous published line was
`0.4.x-beta`.

The staging ladder is **Alpha → Beta → Release Candidate → Stable** (see
`release-naming.md`). The current phase is **Beta**; nothing is stable yet, and
breaking changes remain possible before 1.0 (always through a recorded MINOR
decision — see below).

## Single source of truth

The version lives in **a single file**: `VERSION` at the repository root.

```text
VERSION ──► scripts/bump-version.sh ──► pom.xml (<revision>)
                                     ──► kof-compiler/src/main/resources/dev/kof/version.properties
```

The pipeline automatically updates:

- compiler version;
- CLI version;
- runtime metadata;
- artifacts (jars);
- distribution package;
- GitHub Release;
- changelog.

**No version hardcoded in dozens of files** — that is a recipe for
inconsistency. If the version needs to change, change `VERSION` (or the
pipeline does it) and the rest follows.

## When the version changes

Classification and cut are **distinct decisions**. The normative policy is
**`D-VERSIONING-RELEASE`** (`docs/development/DECISIONS.md`); in summary:

- **Pre-1.0 PATCH:** only when the change adds or alters **no contracted public
  surface** (bugfix, security/regression, parity fix, internal refactor,
  CI/tooling/packaging, docs).
- **Pre-1.0 MINOR (mandatory):** any **new or altered contracted public
  surface** (new syntax/operator/observable semantics, public API or namespace,
  public command/flag, public stdlib capability, package/registry/interop
  contract, a target promoted to Supported/Stable) — with a recorded Decision
  ID.
- **Post-1.0:** strict SemVer — PATCH = backward-compatible fix, MINOR = new
  backward-compatible functionality, MAJOR = incompatible contract change.
- **Trigger ≠ cut:** `LAST_RELEASE..ACTIVE_BRANCH` crossing **100–150 commits**
  (or a security/critical event, or an explicit maintainer decision) opens a
  release **evaluation** — it never publishes a release by itself. The common
  eligibility gate lives in `D-VERSIONING-RELEASE`.
- The first `1.0.0` only exists when the `D-RELEASE-1.0` EXIT GATE is fully
  GREEN on the same candidate and no `D-1.0-EDGES` edge is open.

## Verification

`kof version` and `kof info` report the packaged version. CI verifies that
`VERSION`, `pom.xml` and the version resource agree before any build.
