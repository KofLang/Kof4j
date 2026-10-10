[English](README.md) | [Português](README.pt_BR.md)

# Full Parity — the ABSOLUTE rule of the 0.5.0 release

> **Maintainer, 24/09: THE ABSOLUTE RULE FOR ANY PLAN IS FULL PARITY.**
> Every Kof surface works on EVERY target — JVM/Script, Native x86-64,
> Native riscv64/aarch64, JS — with byte/golden parity against the JVM
> oracle. An honest gap code is the tracker, never the end state. The
> 0.5.0 release does not cut while the ledger has open rows.

## Files

- [`PARITY-GAPS.md`](PARITY-GAPS.md) — **the blocker ledger**: every measured
  partial-parity row (surface × target × gap code × owner lane). The 0.5.0
  machine gate `check_release_050_gate.sh` was **retired** (`D-RELEASE-0.5.0-CLOSED`, 28/09);
  the live loose-set authority is now `scripts/check_live_records.sh`, and
  release promotion is governed by `docs/development/quality-pipeline.md`
  (`D-QUALITY-PIPELINE-2609`) — any open row still means the next promotion is
  NOT green.

## How a row closes

See the "Definition of done" in the ledger: compile + golden/E2E byte parity
+ docs tables updated in the same commit + row removed in the same commit.
