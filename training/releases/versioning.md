[English](versioning.md) | [Português](versioning.pt_BR.md)

# Versioning and Releases

Facts about Kof's versioning and release model. Use it to answer
questions about versions, releases and the publishing process.

**Version:** 0.5.0-beta (Sep 2026)

## Version format

```text
MAJOR.MINOR.PATCH[-suffix]
```

- `X` — Major release.
- `Y` — Major fix / significant evolution.
- `Z` — Bugfix — the "little dot of shame" (fixes, regressions, small
  adjustments without relevant architectural change).
- `-beta` / `-alpha` / `-rc` — stage.

## Current stage

- Kof is at `0.5.0-beta` (active development branch `lab`, per `D-BRANCH-PIPELINE`; Sep 2026).
- Evolution: `0.0.5-alpha` → `0.1.0` → `0.2.6-beta` → Beta → Release Candidate → Stable.
- The component version (compiler/runtime/stdlib) is `0.2.0`; the `-beta`
  suffix belongs to the release.
- Targets: `jvm` / `native` / `native.risc` / `native.arm` / `js` / `kofc` + `KofScript`.

## Single source of truth

- The version lives in the `VERSION` file at the repository root (`0.5.0-beta`).
- `scripts/bump-version.sh` syncs `VERSION` → `pom.xml` (`<revision>`)
  → `kof-compiler/src/main/resources/dev/kof/version.properties` (`kof.version` follows the `revision`).
- The pipeline automatically updates: compiler, CLI, runtime, artifacts,
  package, GitHub Release, changelog.
- Do not edit versions manually in several files.

 ## Automatic release (CI/CD) — 2 jobs (test-and-bump → package-and-release)

 Every commit on `main`:

 ```text
 commit → CI (gate) → test-and-bump (mvn package + golden + integration + bump + push of the commit, exports bump_sha)
       → package-and-release (checks out the BUMP COMMIT via ref: bump_sha; 3-runner matrix; VERSION sanity check; package --jdk; validates the artifact; GitHub Release per platform)
 ```

 - `main` never points to a state that does not compile.
 - The release only happens if `mvn clean package`, `tests/run-golden.sh` and
   `tests/run-integration.sh` pass.
 - The `package-and-release` job **checks out the bump commit** (not the
   trigger one) — without this the package would ship with the previous
   version; there is a sanity check that the checkout's `VERSION` == the
   release version.
 - PR workflow: build + tests + static checks.
 - Push workflow on main: 2 jobs — `test-and-bump` (bump + push +
   exports SHA) and `package-and-release` (matrix: linux-x86_64,
   windows-x86_64, **macos-arm64**), artifact validation, changelog,
   GitHub Release per platform with embedded JDK 25.

 ## Artifacts

 ```text
 kof-0.5.0-beta-linux-x86_64.tar.gz
 kof-0.5.0-beta-windows-x86_64.zip
 kof-0.5.0-beta-macos-arm64.tar.gz
 kof-cli-0.5.0-beta.jar
 SHA256SUMS
 ```

Each package contains compiler, CLI, runtime, stdlib, tooling, editor support and
embedded JDK (Temurin 25; tooling API level 21).

## Changelog

- `CHANGELOG.md` kept in the repository, with the marker `<!-- NEXT-RELEASE -->`
  where the pipeline inserts the next section.
- `scripts/changelog.sh` groups commits since the last tag by the convention:
  `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `build:`, `tooling:`.

## Tags

- Tags follow `kof-<version>` (e.g.: `kof-0.5.0-beta`).
- The bump commit uses `[skip ci]` so it does not re-trigger the pipeline.

## Important rules

- In Beta: every commit on main generates the next Beta version.
- Consistency check: CI compares `VERSION`, `pom.xml` and the packaged resource
  (`mvn package` validates).
- The `package-and-release` job checks out the **bump commit** (via
  `ref: bump_sha`) — never the trigger commit, so the package carries the
  new version; there is a `VERSION` sanity check before packaging.
