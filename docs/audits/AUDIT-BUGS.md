[English](AUDIT-BUGS.md) | [Português](AUDIT-BUGS.md)

# AUDIT-BUGS — found / reproduced / fixed / pending (living; updated per cycle)

## Found + fixed THIS operation (so far: the pre-operation week is history in DOING/CHANGELOG)

- **The cross Handle return drop (15/11 P1 cycle, found + fixed):** the
  riscv/aarch64 ret switch in `NativeFfiCallRiscv.emitRiscv` had NO
  `case 'H'` — a `Handle` return fell to `default:` which returns WITHOUT
  the `sd a0, 0(sp)` operand push, so the cross face delivered NULL (the
  mirror of the x86 SIGSEGV bug the JVM/pcap cycle fixed in b7cfdc66e —
  the x86 got its `case 'H': break;` then; the shared cross text was left
  behind). REPRODUCED RED-first: `FfiCrossHandleE2ETest` measured
  `hread(h) → -1` (the null handle) on riscv64 under qemu BEFORE the fix,
  with the JVM + native x86-64 goldens already green. FIXED at the emitter
  root: `case 'H': break;` (the raw pointer in a0 goes to the operand block
  as-is; the aarch64 needs nothing — the same text serves it via the
  translator, no new mnemonic). PROVEN: the 4-way parity
  (JVM / x86-64 / riscv64 / aarch64 under qemu) byte-exact on a
  malloc/free lifecycle fixture. The lesson: every ret-switch `case` on the
  x86 emitter has to be mirrored in the shared cross emitter the same
  cycle — the two switches are one contract.

## Reproduced + FIXED earlier (the record for the P0 pattern)

- The copy-out regression (the by-value contract broken by my blanket
  copyback) — the issues/tooling lane measured, I fixed at the root (the
  ownership-borrowed opt-in; the by-value default restored) — RED-first
  measured, `FfiArrayE2ETest` 5/5 restored. The lesson recorded: the array
  copy-back is OPT-IN (`ownership borrowed`), never blanket.
- The catch-String null-bind flake (`NativeCatchableRuntimeFaultE2ETest`) —
  root-caused (the message-less fault bound null) and fixed with
  `KofInterpreterFrameCatchStringTest` 5/5 (the issues/tooling lane).
- The stale-red gate that hid 5 days (`check_portukof_tooling`) — the
  #1101 rename never synced the pattern; fixed + wired into the stability
  list (the desync can never hide from the cut verdict again).

## Reproduced + PENDING (the honest pending list, the P0/P1 queue)

| Bug | Reproduced? | The blocker |
|---|---|---|
| #1134 aarch64 SIGBUS (the misaligned sp) | statically (31 misaligned SP sites in the kept asm; the gdb frame pinned in the issue) | arm64 hardware or qemu-user+the SP-alignment assertion — NOT available on this host; the fix = the push/pop redesign in `NativeAarch64Translator` (the 615 `[sp, #N]` offsets). The mandate's question (the support-contract coherence) goes to the P0 cycle: the coherent options are (a) fix, (b) demote the aarch64 target from the supported surface, (c) document the limitation — (b)/(c) are maintainer decisions (the reclassification rule forbids (b) without her). |
| **P0 CYCLE (15/11): the #1134 COHERENCE EVALUATION + the x86 mirror audit (executed, no code change — the facts for the maintainer)** | | |
| THE TECHNICAL FACTS (re-verified in the asm at this tip): the defect is the PUSH/POP lowering emitting `sub sp, sp, #8; str x0, [sp]` — SP left 8-mod-16, and AArch64 hardware faults on SP-relative accesses when SP is not 16-aligned (the Arm ARM rule: SP must remain aligned; qemu-user does not enforce it, real silicon SIGBUSes). 31 sites in the exact repro + 615 `[sp, #N]` accesses in the same file (the push/pop redesign surface). | | |
| THE MIRROR AUDIT (executed): the x86-64 emitter is NOT exposed the same way — the SysV contract only requires 16-alignment AT CALL SITES, and the emitter carries the guards: the _start shim `movq %rsp,%rbx; andq $-16,%rsp; call fflush; movq %rbx,%rsp` (the measured 19/09 lesson, NativeMethodEmitter:495) and the FFI call site `movq %rsp,%rbx; andq $-16,%rsp` before the PLT call (NativeFfiCall:329). The riscv64 has NO SP-alignment requirement (the ISA) — the same 31 sites are legal there (the issue's own record). The exposure is AARCH64-SPECIFIC. | | |
| THE COHERENT OPTIONS (for the maintainer — (b) reclassification needs her per the no-reclassification rule; the mandate §9 asks the coherence, not the decision): (a) FIX — track the SP displacement symbolically in `NativeAarch64Translator`, rewrite the push/pop pairs to 16-byte slots with the insertion padding (the recorded resume route; provable only on arm64 hardware or with the SP-alignment check added to the qemu harness); (b) DEMOTE aarch64-real-hardware from the supported surface to qemu-only (the backend stays; the contract line changes — HER decision); (c) KEEP documented as the limitation (the current state: §1134 + the site/backend-parity rows name it) — coherent while aarch64-real is not promised as runnable-for-classes; the divergence appears the moment any doc promises real-hardware class construction. THE CURRENT DOCS' STATE (measured): backend-parity/roadmap name aarch64 as supported WITH qemu as the validation vehicle; the SIGBUS face is named in known-bugs §1134 + the issue + the site's backend rows — the contract as documented is coherent TODAY (the limitation is named everywhere the target is promised), and INCOHERENT the moment the docs promise real-hardware execution. | | |
| The cross copy-out (riscv64/aarch64) | FFI001 honest (the mirror pack exists in the riscv asm) | the mirror-slice work (the P1 cycle) |
| The pcap capture pointer-heavy | FFI001 honest | the `pkthdr**` out-param face (the Handle lifecycle LANDED — the JVM/x86 slice + the cross faces both proven) |
| ws/sse/TLS/path (JS + native) | the honest refusals named | the WEB002/WEB008 slices (the P1 cycles, large) |

## NOT reproducible on this host (the named environment limits)

- #1135 macOS (no Apple hardware), #1134's live crash (no arm64), the
  cross-gcc faces (no cross toolchains here — the CI runs them).
