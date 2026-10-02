[English](size-baseline.md) | [Português](size-baseline.pt_BR.md)

# D-SIZE-BUDGET — Phase 1 baseline (measurement only)

> Decision: `DECISIONS.md` `D-SIZE-BUDGET` (maintainer 01/10/2026, option A). This
> document + `size-baseline-2026-10-01.tsv` are the **Phase 1 deliverable**:
> observability only — **no behaviour, dependency, packaging or CI change**. It
> measures the weight of the distribution so a future decision (reductions,
> packaging experiments, a CI gate) has a baseline to reason from.

## How to reproduce

```
scripts/size/measure-size.sh --out /tmp/size.tsv   # regenerate the baseline
scripts/size/compare-size.sh OLD.tsv NEW.tsv       # diff two baselines
scripts/size/measure-size.sh --selftest            # guard-of-the-guard
```

The emitted TSV is `kind<TAB>name<TAB>value`:

| kind | meaning |
|---|---|
| `meta` | commit, branch, date, `uname`, `java -version` |
| `module` | each `kof-*/target/*.jar` (compiled module) |
| `dist` | the shaded CLI jar, the thin `original-` jar, and a packed `tar.gz` |
| `dep` | uncompressed bytes per **top-level package** inside the shaded jar, top 20 |
| `hello` | artifact bytes of a hello-world built by the CLI, per target |

A target whose toolchain is absent is emitted as `unavailable` (never a fake 0).

## Measured baseline — commit `766e1103b`, Linux 6.17 x86_64, OpenJDK 25.0.3 (01/10/2026)

| Artifact | Bytes | ~ |
|---|---:|---:|
| `dist shaded-cli` | 42 887 089 | 40.9 MiB |
| `dist packed-cli.tar.gz` | 39 213 159 | 37.4 MiB |
| `dist thin-cli` (`original-`) | 486 789 | 475 KiB |
| `module kof-compiler` | 2 696 491 | 2.6 MiB |
| `module kof-c-compiler` | 73 360 | 72 KiB |
| `module kof-runtime` | 61 708 | 60 KiB |
| `module kof-script` | 26 314 | 26 KiB |

The shaded CLI is ≈ **40.9 MiB**, and the thin jar (the CLI's own classes) is only
≈ **475 KiB** — so ≈ **40.4 MiB** is bundled third-party dependencies. Directional
attribution of the top packages (UNCOMPRESSED entry bytes, so they do not sum to
the compressed jar size): `com` ≈ 47.3 MB and `org` ≈ 42.7 MB (GraalJS/Truffle and
friends), `dev` ≈ 8.3 MB (KOF's own code), `META-INF` ≈ 0.29 MB.

Hello-world artifact bytes per target (built from the shaded CLI):

| Target | Bytes | ~ |
|---|---:|---:|
| jvm | 2 182 | 2.1 KiB |
| js | 22 186 | 21.7 KiB |
| native (x86-64) | 53 568 | 52.3 KiB |
| native.risc (riscv64) | 138 232 | 135 KiB |
| native.arm (aarch64) | 137 848 | 135 KiB |

## Reading rules (avoid the wrong conclusions)

- `dep` is **directional, not exact**: it attributes UNCOMPRESSED bytes per top
  package, so it over-counts relative to the compressed shaded jar and does not
  attribute transitively. Use it to rank gross contributors, not to account cents.
- The thin vs shaded gap is the **bundled dependencies**; the consolidation rule
  the front targets is "an optional capability has an optional cost" (an app that
  does not use PDF pays 0 for PDF) — measured later, not here.
- **No reduction is authorized by this document.** Phase 1 changes nothing; each
  reduction/packaging/CI-gate idea needs its own maintainer decision.

## Re-audit

Regenerate `scripts/size/measure-size.sh` at the tip and `compare-size.sh` against
this baseline; update this line in the same commit (a record, not a frozen opinion).
