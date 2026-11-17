# aarch64 SP alignment plan — the push/pop redesign (issue #1134)

[English](aarch64-sp-alignment-plan.md) | [Português](aarch64-sp-alignment-plan.pt_BR.md)

status: CONCLUDED + PROMOTED to docs/ 17/11 (all four slices landed with executed proof; #1134 CLOSED 16/11 by implementation — the maintainer's no-honest-limits criterion; the permanent gate `NativeAarch64SpAlignmentE2ETest` lives in the suite, 55T/0F/1 named skip measured 17/11)
owner: docs/lab `192.168.100.14:0` (flatt CLI, no opencode server on this host; port n/a)
issue: #1134 (`1.0-blocks`, the maintainer's 16/11 no-honest-limits order)
target: NATIVE_AARCH64 on REAL hardware (the criterion's proof vehicle: the
static SP-invariant assertion + qemu parity — arm64 hw stays unavailable on
this host, recorded per the mandate §14)

## The measured defect

AArch64 hardware faults (`SIGBUS`) on SP-relative accesses when SP is not
16-byte aligned. The riscv64 code generator (whose text the aarch64 backend
translates line-by-line) pushes operands with `addi sp, sp, -8` + `sd X, 0(sp)`
— an 8-byte stride, legal on riscv64. Every ODD push count leaves SP at
8-mod-16: the issue's repro died at `str x0, [sp]` with SP = 8-mod-16
(measured in gdb). qemu-user does not enforce SP alignment, which is why the
cross E2E never caught it.

## The measured root cause (why a text peephole alone fails)

The operand block is read back by NONZERO sp-relative offsets in THREE
consumers, all hardcoded to the 8-byte stride:

1. `NativeFfiCallRiscv.emitRiscv` — the FFI cross calls: the args read at
   `8·(n−1−i)(t0)`, the spill region `addi t3, t0, 8n−8ns−16`, the saved
   restore slot `sd t2, 8ns+8(sp)` / `ld sp, 8ns+8(sp)`, the return push
   `addi sp, sp, −8`.
2. `NativeRiscvCrossOps.emitCrossArgLoads` — the 9+-arg generic calls: the
   register-arg reads at `8·(count−1−r)(sp)` and the stack-arg mirror
   (`ld t0, 8·(2·sa−1−k)(sp)` → `sd t0, 8·k(sp)`, the CALLEE keeps the SysV
   8-byte stride — only the OPERAND block widens).
3. `NativeRiscvCrossOps.emitCrossStackCleanup` — the post-call cleanup
   `8·(count+stackArgs)`.

The pure stack machine (the generated code) accesses operands ONLY at
`0(sp)` — offset-0 pushes/pops are stride-agnostic. The consumers above are
the entire coupling surface.

## The failed attempts (preserved so they are never repeated)

1. **The delta-tracking text aligner** (the post-pass adjusting `[sp,#N]`
   offsets by a running delta): 13-face regression in the wider battery —
   the offset-adjust model was wrong AND the tail-detection bug silently
   skipped every rewrite (the `[sp]` form yields an EMPTY tail, not `]`).
2. **The widening-only text aligner** (sub/add sp → the next multiple of 16,
   no offset adjustment): the plain faces measured 52/55 = the 3F baseline
   (the aarch64 spawn env-fails), but the wider battery regressed 22F→35F —
   the three consumers above still read the 8-byte stride. REVERTED (never
   ship a regression).

Lesson: the widening and the stride consumers must change TOGETHER — staged
below, not as a big-bang.

## The staged slices (each with its proof vehicle)

### Slice 1 — the stride parameterization (the cross-call emitters)

A single constant (`slotStride`: 8 for NATIVE_RISCV64, 16 for
NATIVE_AARCH64) parameterizes the block arithmetic in the three consumers:
the arg reads `slotStride·(n−1−i)`, the spill region
`slotStride·n − slotStride·ns − 16`, the restore slot `slotStride·ns + 8`,
the cleanup `slotStride·(count+stackArgs)`, the stack-arg mirror reads
`slotStride·(2·sa−1−k)` (the WRITES stay `8·k` — the callee's SysV stride is
8 and unchanged).

**Proof:** the riscv64 text must be BYTE-IDENTICAL (stride 8 — zero riscv
risk, the diff proves it); the aarch64 text compiles and the plain faces
(the non-FFI ones) stay at the 3F baseline.

### Slice 2 — the widening post-pass (the aarch64 path only)

`NativeAarch64SpAligner` (recreated, the widening-only form — the measured
attempt 2): every `sub/add sp, sp, #N` with N%16≠0 → the next multiple of
16; the `pop` macro call-sites inlined with the widened `add`. NO offset
adjustment (the slice-1 parameterization carries the arithmetic).

**Proof:** the SP-invariant checker on the emitted aarch64 asm (every
sub/add sp a multiple of 16 — the mechanical form measured RED with 60
violations pre-fix); the aarch64 suite at the 3F baseline.

**The bisect finding (17/11):** the dims mirror was initially UNCONDITIONAL —
it broke the riscv64 multi-dim (the 16-stride reads over the 8-wide riscv
slots; measured 0/OOB). FIXED: the mirror is GATED BY TARGET (aarch64 only;
the riscv64 keeps the original emission — byte-identical to the pre-plan
output, proven 2/3/0/7 green). The coverage gap this exposed: NO riscv64
multi-dim E2E existed — the face added
(`MathDNativeE2ETest.multiDimParityOnBothCrosses`, both cross targets
byte-exact).

### Slice 3 — the proof (the issue's criterion)

- The SP-invariant assertion: the static checker as the JUnit gate
  (`NativeAarch64SpAlignmentE2ETest` — the repro compiled and asserted).
- The FFI cross battery: the previously-regressed 13 faces
  (`FfiCrossArrayCopyOutE2ETest`, `FfiCrossHfaReturnE2ETest`,
  `FfiNativeCrossE2ETest`, `CrossPeepholeEffectTest`, the 9+-arg faces) ALL
  back at their baseline.
- The issue's repro (`class Box`) printing `42` under qemu.
- `MathDNativeE2ETest` + `MathDParityE2ETest` byte-exact (no regression in
  the fresh MATH-D faces).

### Slice 4 — the close

The conformance/backend-parity rows, known-bugs, #1134 CLOSED by
implementation (the maintainer's no-honest-limits criterion), the CHANGELOG
EN+PT.

## The rollback

The aligner is ONE file wired at ONE point (`NativeArchEmitter`, after the
peephole) — the revert is one wire. The stride parameterization defaults to
8 (the riscv64 text unchanged), so the rollback restores the exact pre-plan
aarch64 output.

## Non-goals

- The trig §621 refusal (a separate decision).
- arm64 real-hardware validation (this host has none — recorded; the static
  assertion is the criterion's named alternative).
