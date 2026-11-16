[English](AUDIT-FINAL.md) | [Português](AUDIT-FINAL.md)

# AUDIT-FINAL — the verdict (LIVING: the operation is STARTING, not concluded)

**Current classification: PARTIALLY_COMPLETE (the operation's own start
state — the honest form; the mandate forbids declaring success early).**

## What is ALREADY true at the baseline

- The suite GREEN at the working tip (8216/0F/0E/1015 named skips, the stamp
  `e38619713`; the tip 2 docs-only commits past — the delta measured).
- CUT: GREEN for the 0.8.0 (the maintainer's merge pending).
- The plans inventory EXISTS and is reconciled at the face level
  (AUDIT-PLANS.md) — the deep per-requirement audits are the cycles ahead.
- The site divergence FOUND and PR'd (koflang.github.io#1).
- The copy-out regression (the one open defect this week) fixed at the root
  with the opt-in design + the RED-first proof.

## The P0 cycle (15/11): EXECUTED (the audit part; the fix part blocked)

- The #1134 coherence evaluation EXECUTED (AUDIT-BUGS): the technical facts
  re-verified in the asm at this tip (the `sub sp, sp, #8; str x0, [sp]`
  sites; the Arm ARM alignment rule; qemu-user does not enforce it), the
  x86 MIRROR AUDIT executed (the emitter is NOT exposed — the _start shim
  and the FFI call site both carry `andq $-16` guards, the 19/09 lesson;
  the exposure is aarch64-specific; riscv has no alignment requirement),
  and the THREE coherent options documented for the maintainer ((a) fix —
  blocked on arm64 hw, (b) demote aarch64-real — her decision, (c) keep
  documented — coherent TODAY as long as no doc promises real-hardware
  class construction). The docs' current state measured: coherent (the
  limitation named everywhere the target is promised).
- The FIX itself: BLOCKED (the arm64 hardware; the qemu harness lacks the
  SP-alignment enforcement that hides the defect — a qemu-harness alignment
  assertion is a possible validation vehicle, named for the fix cycle).

## The pending work (the honest list, the P0→P3 order)

- P0: the #1134 support-contract coherence evaluation (the maintainer
  decision required for the reclassification; the fix itself blocked on
  arm64 hw); the #1134-adjacent native-stability sweep.
- P1: the WEB002 residuals (the large WEB slices); the linalg gemm_
  FORTRAN-interface face (the row-major layout dance — now optional since
  cblas exists).
  DONE from this list: the cross copy-out mirror (b57ea33b9, the sibling
  cycle); the cross Handle faces (6b37c22ef — the 4-way parity proven,
  the ret-switch mirror bug found + fixed RED-first); the
  pointer-to-pointer out-param face (this cycle — the `pkthdr**` residual
  expressed as the borrowed `Handle[]` copy-out, the 4-way fixture parity +
  the real-libpcap offline probe 2/2; the bare-`Handle` `Type.of`
  resolution gap found + fixed on the way).
- P2: the ASan/Valgrind evaluation on the
  native FFI faces; the CodeQL API re-probe; the per-gate behavioral-anchor
  audit; the README/examples doc-truth spot audit.
  DONE from this list: the per-skip inventory (the §10 mechanical gate
  `check_skip_inventory.sh` — 722 sites / 722 named / 0 silent / 124
  families measured; the planted-fault selftest; wired into the stability
  gate; AUDIT-STABILITY's six-field family table).
- P3: #1155/#1157/#1162/#1163 (the planned scopes).

## The impediments (the real ones, named)

- No arm64 hardware / no cross-gcc / no macOS / no webkit on this host — the
  mandate §14 requires recording what was NOT executed: those validations
  are the CI's or other hosts'.
- The reclassification-for-gates is FORBIDDEN — the #1134 coherence question
  needs the maintainer.
