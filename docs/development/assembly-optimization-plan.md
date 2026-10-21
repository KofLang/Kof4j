[English](assembly-optimization-plan.md) | [Português](assembly-optimization-plan.pt_BR.md)

# Cross-Target Assembly Optimization

**Status:** UNDER DEVELOPMENT — promoted from `future/` to `docs/development/` 13/10 (`D-FUTURE-PROMOTION` + `D-ASSEMBLY-OPT-GO`; lane docs/lab `192.168.15.130:9093`). **Current slice: Phase E — 23 ISA slices landed across all three native targets (x86-64, AArch64, RISC-V 64); the register-mapped local model is LIVE in the native x86-64 codegen (`D-CODEGEN-REWRITE`, flag ON by default).** The §7 (ISA-specific optimizations) bullet lists for x86-64/AArch64/RISC-V are now essentially EXHAUSTED — every candidate remaining was MEASURED low-value (1-2 sites per sample) or requires cross-region liveness the text peephole cannot supply safely, so no further §7 slice was forced. The larger remaining faces are **Phase D (target lowering — formalize the IR-op → machine-instruction declaration per backend; the x86 emitter already exhaustively handles all 37 IR ops with a throwing default, so this is a formalization, not a correctness gap)** and the **multi-file JS-decompiler robustness** work that unblocks §3 O2 (`D-JS-ROBUST`, fix path recorded). Phase A (structured-optimizer layer over the EXISTING linear IR optimizer) and Phase B (constant/copy propagation + dead-store elimination, wired into the production `Optimizer` with a measured instruction reduction) are complete; Phase C slices 1–6 add CFG-based liveness, the interference graph, a deterministic greedy allocator, loop-weighted spill cost, the per-ABI caller/callee-saved classification and the spill-materialization plan; §18 adds the IR metrics counter; §20 determinism is verified by `OptimizerDeterminismTest` (the production optimizer is deterministic — same input, same output — and idempotent — a fixed point — and `x * 4` reaches the §12 shift in production). Phase F (benchmark suite) first slice = `OptimizerBenchmarkE2ETest` — a cross-target benchmark harness covering ten §17/Phase-F categories (arithmetic, loops, branches, calls, constants, bitwise, arrays, structs/records, register pressure, memory) on the JVM AND native x86-64; FFI is exercised by the dedicated FFI E2E tests and native FFI remains the diagnosed `FFI001` gap, so it is not yet a cross-target benchmark category: each fixture compiles to the JVM and all three native targets (x86-64, aarch64, riscv64 via qemu), runs on every available target (asserting the program output — the correctness half, cross-target parity), and reports the production optimizer's instruction/load/store deltas (the measured half, e.g. `constants` 80→72 instr, stores 11→9) and the native ELF size (§18 metric); timing is printed as an OBSERVATION only, never asserted (§17: smaller assembly is not necessarily faster). §17 = `OptimizerMetrics.java` — the before/after comparison harness §17 requires ("each relevant optimization must have … before/after comparison"): it runs the production `Optimizer` on a method's op stream and reports the `IrMetrics` before/after (instruction/load/store deltas), so an optimization's effect is MEASURED, not cosmetic; proof `OptimizerMetricsTest` 3/3. **Phase C slice 7 (WIRING) = `LivenessDeadStores.java`** — the materialization that CONSUMES `LivenessAnalysis`: a store is dead when its slot is not live after it (the stored value is never read), removed together with a pure producer; WIRED into the production `Optimizer` (after the Phase B passes). This is the §21 "reduction in artificial loads/stores" criterion — the liveness pass removes a never-read store the Phase B overwrite-only rule cannot; validated on a broad fast suite (268/269, incl. `CoreRegressionE2ETest` 104/104, all image + switch suites) with only my own Phase B count assertion adjusted upward (the pipeline now folds harder). **§709 (found+fixed 13/10 by lane compiler/JVM/native `192.168.15.30:9092`, `cce555c00`):** the pass shipped a bug — it removed the initialization of a local read ONLY in a `catch`/`finally` handler, because `LivenessAnalysis`'s exception edge relied on `blockOf(KofTryStart.handlerLabel)`, which is `-1` (the handler is the `KofCatchStart` op, not a `KofLabel`); the CFG now maps the handler (`KofCatchStart` is a block leader, `KofTryStart` a boundary) + . **§712 (found+fixed 14/10 by the same lane, `2d571f8f4`):** the SAME pass dead-stored a `finally` that reads a local the try/catch assigns — a SECOND exception-region edge case (after §709's catch). Two instances is a pattern, so the pass was HARDENED: `LivenessDeadStores` now removes NOTHING from a method that has any `KofTryStart`/`KofCatchStart` (conservative by construction; exception-heavy methods lose the optimization, correctness is not negotiable). Proof `LivenessDeadStoresTest` 7/7 (incl. the new `aMethodWithATryRemovesNothing`). The durable lesson stands: my unit test missed §709 because it built an IDEALIZED IR (`new KofLabel(handler)`) the real lowering never emits — the durable lesson: build analysis tests from REAL lowered IR, or prove with a compile-and-run E2E. The register COLORING is now applied inside the native x86-64 codegen (D-CODEGEN-REWRITE, flag ON by default) — see the D-CODEGEN-REWRITE slices below. §12 = `StrengthReduction.java` — a multiplication by a power of two becomes a left shift, ONLY where it is exactly equivalent (int/long two's complement; never floating point, never division — `x / 2` rounds toward zero while `x >> 1` rounds toward −∞); registered in `PassPipeline.standard()` AND WIRED into the production `Optimizer` (after folding, so a folded literal still reaches it); the shift amount is ALWAYS an int literal even for a long value (JVM `lshl`/`ishl` take (long,int)/(int,int) — a long shift literal is a VerifyError, §167); proof `StrengthReductionTest` 7/7 + validated on the broad JVM/JS suite (CoreRegression 104/104) and a native x86 run (`10L * 8L` → `80`). The linear `Optimizer.java` keeps its own constant folding + dead stack-effect elimination + reachability + jump-to-next on a FLATTENED op list (`IRBasicBlock` is a single flat block today); the Phase A foundation (control-flow graph / basic-block partition / successor edges), the Phase B passes and the Phase C analyses sit alongside it, zero behavior change for existing backends.
**How to finish:** Phase A (CFG + virtual registers + use/effect analysis + pass pipeline), then Phase B (basic optimizations) → Phase C (register allocation) → Phase D (target lowering) → Phase E (ISA) → Phase F (benchmarks); each phase's criterion per §21. The per-target ABI is a scope decision before Phase D (R7 honest scope).
**Location:** `docs/development/assembly-optimization-plan.md`
**Nature:** architecture, contracts, implementation strategy and promotion criteria
**Scope:** Kof Native backend on multiple ISAs
**Targets:** x86_64, aarch64, riscv64 and future Native backends
**Principle:** prefer optimizing before the ISA-specific assembly whenever possible; specialize to the backend only when the architecture requires it.

last: promoted 13/10 (`D-FUTURE-PROMOTION`; the only `future/` plan whose gate `D-ASSEMBLY-OPT-GO` is already decided, no new syntax, additive compiler-internal)
doing: Phase A COMPLETE 13/10 + Phase B COMPLETE 13/10 + Phase C slice 1 (liveness) LANDED 13/10 — Phase A landed all six §21 items: `ControlFlowGraph.java` (blocks + successor edges), `EffectAnalysis.java` (effect + local def/use), `VirtualRegisters.java` ((slot,type) numbering), `PassPipeline.java` + `IrPass.java` (ordered, fixpoint, deterministic). Phase B slices 1–3 (`ConstantPropagation`, `CopyPropagation`, `DeadCodeElimination`) are WIRED into the production `Optimizer.passes()` (after constant folding, before dead stack-effect elimination); three compiler-internal invariants were measured (RED-first): (1) `KofStatementIf` is a control boundary (folding a condition orphaned the JS structural-reconstruction marker — `KofJsE2ETest` 38/38 after); (2) an immediate `store(slot); load(slot)` read-back stays verbatim (the backends pattern-match on it — `#switch`/`#recv`; disabling the guard broke `SwitchLongDoubleSupportE2ETest` 3/5); (3) constant propagation emits a DISTINCT literal instance per rewrite site (sharing one instance made identity-based dead-store elimination delete a surviving read with a dead producer — `Raster.kf` → ASM frame crash). Measured reduction: a dead store drops 2 ops (`ds1` 68→66), two drop 6 (`ds2` 74→68); proof `OptimizerPhaseBReductionTest` 3/3. Phase C slice 1 = `LivenessAnalysis.java` — backward dataflow (`liveOut(b)=∪liveIn(s)`, `liveIn(b)=use∪(liveOut\def)`) over the Phase A CFG, with a conservative exception edge (try→handler); per-block `liveIn`/`liveOut` + per-op `liveAfter`; deterministic; ADDITIVE (pure read, no behavior change); proof `LivenessAnalysisTest` 7/7. Phase C slice 2 = `InterferenceGraph.java` — two slots interfere when one is live after the other is defined (conservative: never omits an edge; extra edges only cost optimality, never correctness); deterministic node order (§20); also exposes copy-relation coalescing hints (`coalesceHints`); every def'd slot is a node even without edges; ADDITIVE; proof `InterferenceGraphTest` 7/7. Phase C slice 3 = `RegisterAllocator.java` — deterministic greedy graph coloring (descending degree, ties by ascending slot index, §20): each node takes the lowest colour unused by its neighbours; a node with no free colour within the register budget SPILLS (colour -1). Never assigns two interfering slots the same register (always correct, at worst sub-optimal); colours are abstract register numbers (ISA mapping is Phase D/E). ADDITIVE (nothing consumes it yet); proof `RegisterAllocatorTest` 6/6. Phase C slice 4 = `SpillCost.java` + cost-driven spill selection — a slot's spill cost is its use count weighted by loop nesting depth (a use inside a loop is paid per iteration), derived from the CFG's back edges; when a node has no free colour the allocator spills the cheapest node among it and its coloured neighbours (a lower-cost neighbour is preferred over spilling the current node). Proof `SpillCostTest` 5/5. Phase C slice 5 = `CallingConvention.java` — caller/callee-saved register classification per ABI (x86-64 SysV, AAPCS64, RISC-V LP64D), keyed on the existing `AbiLayout.Abi` enum; exposes the integer argument registers and the volatile/preserved sets the allocator consults (a value live across a call cannot stay in a caller-saved register). Pure ABI data; proof `CallingConventionTest` 5/5. Phase C slice 6 = `SpillPlanner.java` — given the allocation's spill set, computes (as pure data) which ops need a reload inserted before them and which need a store-back after them; the bridge between the allocation decision and the (non-additive) codegen wiring. Nothing consumes it yet; proof `SpillPlannerTest` 4/4. §18 metrics = `IrMetrics.java` — a pure counter over the op stream (instruction/load/store/branch/call/return counts) plus the allocation's spill and register-usage figures, so an optimization's effect is MEASURED rather than asserted; proof `IrMetricsTest` 4/4. **This closes the Phase C analysis/decision layer** (liveness + virtual registers + allocation + spill cost/selection + caller/callee-saved) ADDITIVELY; the remaining Phase C work is materialization — wiring the allocator into codegen to actually reduce artificial loads/stores (a codegen change, not additive; the point where Phase C meets Phase D)
next: DECISIONS TAKEN (maintainer 13/10) — (1) `D-CODEGEN-REWRITE`: full rewrite, x86-64 SysV ABI first, the RegisterAllocator becomes the codegen's local model; (2) `D-JS-ROBUST`: make the JS backend robust to optimized control flow (fix plan recorded: teach JsControlFlowParser the jump-to-join stack merge); (3) future/ promotion pending the maintainer naming the plan. NEXT SLICE = start `D-CODEGEN-REWRITE` additively: formalize the x86-64 SysV register model over the existing `CallingConvention`/`AbiLayout` (register classes + a local→register mapping behind a flag), keeping the frame-slot path green until parity. **D-CODEGEN-REWRITE slice 1 = `X86_64RegisterFile.java`** — the x86-64 System V register file as pure data: the two register CLASSES (INTEGER/SSE), allocatable registers per class (callee-saved first, rsp/rbp excluded), argument/return registers, and `classOf(Type)` (float/double → SSE); consistent with `CallingConvention.SYSV_X86_64`; ADDITIVE (binds nothing); proof `X86_64RegisterFileTest` 8/8. **D-CODEGEN-REWRITE slice 2 = `RegisterAssignment.java`** — resolves the allocator's abstract colours to real x86-64 registers per class (colour N → the Nth allocatable register of the slot's class); a spilled slot, an unknown type, or a colour beyond the class's register count goes to memory; ADDITIVE; proof `RegisterAssignmentTest` 9/9 (now GC-safe: only int/bool are register-placeable; a reference stays in the frame). **D-CODEGEN-REWRITE slice 3 = `RegisterPlanner.java`** — the per-method driver chaining liveness→interference→allocator→assignment into a `Placement` (the single entry point the codegen rewrite calls); ADDITIVE; proof `RegisterPlannerTest` 4/4. **D-CODEGEN-REWRITE slice 4 = call-awareness primitives** — `LivenessAnalysis.liveAcrossCalls` (slots live immediately after a `KofCall`) + `RegisterAllocator.allocate(..., callCrossing, calleeSavedCount)` (a call-crossing slot is confined to the callee-saved colours `0..k-1` — the front of the class's allocatable list — or spills; a caller-saved placement would let a call clobber it) + `X86_64RegisterFile.calleeSavedCount`; ADDITIVE (nothing consumes them yet); proof `CallAwareAllocationTest` 4/4 + `X86_64RegisterFileTest` 10/10. **slice 5 (prerequisite) = `Placement.calleeSavedUsed()`** — the callee-saved registers a method's placement USES (the ones the prologue must save / the epilogue restore; an unused callee-saved register needs no save); proof `RegisterPlannerTest` 6/6. **slice 6 (prerequisite) = `RegisterPlanner.eligibleForRegisterLocals(method)`** — the conservative eligibility gate for the switch-over: only methods whose locals are ALL INTEGER-class (int/bool/ref) and use no double-width `long`/`double` qualify; SSE/double-width/`this`-not-INTEGER methods stay on the frame-slot path. Proof `RegisterPlannerTest` 9/9. **Fatia 7 design (reconnaissance, NOT yet implemented):** the emission switch-over has two measured subtleties that must be handled before it is safe. (1) **GC root visibility** — the native GC is conservative and scans the FRAME; a REFERENCE held in a register would be invisible to it, so only int/bool locals may be register-placed and reference locals must stay in the frame (the allocator's register set must be restricted per class, and eligibility refined). (2) **Prologue staging** — args arrive in fixed ABI registers; moving them straight to their assigned register risks the parallel-move problem (one arg's destination clobbering another's source), so params must be staged through their frame slot first (prologue unchanged), then copied frame→register. Epilogue: the used callee-saved registers (rbx/r12–r15; `Placement.calleeSavedUsed`) are saved in the prologue and restored at EVERY `KofReturn`/`KofReturnVoid` site before `movq %rbp,%rsp` (they are read back from their frame save slots, since `movq %rbp,%rsp` discards the stack). Scratch registers confirmed free: the emitter uses only %rax/%rcx/%rdx/%rdi/%rsi/%r8/%r9/%r10/%r11 (+%rip/%rsp/%rbp), never rbx/r12–r15. (3) **Frame extension** — the callee-saved save slots must live INSIDE the allocated frame: the caller extends the frame by `regs.size()*8` and places them right after the locals (`-(localsBytes + (i+1)*8)`); an offset beyond the frame would write below `%rsp` and corrupt the stack. The fragment layer (`NativeRegisterLocalAsm`, proof `NativeRegisterLocalAsmTest` 5/5) emits the prologue-save/epilogue-restore/load/store text with the offsets supplied by the caller, so the caller owns this correctness. **Fatia 9 (emission switch-over) IMPLEMENTED, gated OFF:** `NativeRegisterLocalSupport` + `NativeRegisterLocalAsm` wire the fragments into `NativeMethodEmitter` for ELIGIBLE int/bool-only methods — prologue saves the used callee-saved registers (frame extended by their slots) and stages params through the frame then copies them to their registers (a reference `this`/ref param is never register-placed, so it is skipped); `KofLoadLocal`/`KofStoreLocal` of a placed slot push/pop its register; every return/epilogue restores the callee-saved set. Gated by the `kof.native.register.locals` system property (default OFF), so production is UNCHANGED. Proven: with the flag ON, `NativeRegisterLocalsE2ETest` 2/2 + a 136/136 native batch (NativeE2ETest 68, casts, overflow, divide-by-zero, config, erasure, array-bounds) pass; with the flag OFF, the same suites pass (production path intact). Two bugs found+fixed while wiring: (a) register names needed the AT&T `%` prefix (`X86_64RegisterFile` stores bare names); (b) the prologue must copy slot 0 too — it is the first PARAM for a static method (only `this` in an instance method is slot 0, and a reference is never register-placed). The flag is now ON BY DEFAULT (production) — validated on the full native x86-64 batch (224/224, incl. try-handler-leak, casts, overflow, divide-by-zero, config, erasure, array-bounds) + aarch64 55/55 + riscv64 58/58; `-Dkof.native.register.locals=false` is an escape hatch back to the frame-slot path. **Eligibility EXTENDED:** the gate no longer excludes methods with double-width (long/float/double) or reference locals — `placeable` already keeps those in the frame, so any method qualifies and only its int/bool locals are register-placed (a mixed int+double method places the int, keeps the double in the frame). Validated on the full native x86-64 batch (229/229, incl. `SwitchLongDoubleSupportE2ETest`). **References stay in the FRAME (measured):** I probed register-placing references and a live String in `rbx` survived 50000 allocations in a SIMPLE method (the collector spills callee-saved registers before marking) — but the full batch then showed METHODS WITH try/catch/finally CRASH (SIGSEGV, `NativeTryHandlerLeakE2ETest` + `NativeErasureBoxE2ETest`) when references are register-placed: the spill is not a sufficient GC guarantee across every path. So `placeable` remains int/bool-only — references stay in the frame. (The int/bool-only eligibility extension above IS safe and stays.) **ROOT CAUSE + FIX (corrected 14/10):** the reference crash was NOT an interference-graph problem — it was the `KofCatchStart` EMISSION: it stored the caught exception to the local's FRAME slot unconditionally, so a REGISTER-PLACED caught variable (only a reference can be both caught and register-placed) read a stale value → SIGSEGV. FIX: `KofCatchStart` is now PLACEMENT-AWARE (stores to the register when the slot is register-placed). With that fix, exception methods are eligible again — the previous cycle's exclusion was OVER-CONSERVATIVE (with references framed, exception methods were always safe; the crash only appeared when references were ALSO placeable). Net state: `eligibleForRegisterLocals` = true (any method), `placeable` = int/bool-only (references stay framed — still MEASURED unsafe: `NativeE2ETest.nativeLambdaMutableCapture` crashes when references are placeable, because lambdas do not preserve a register-placed captured reference). Validated on the full native x86-64 batch 225/225. **PARAMS now placed (14/10, found by measuring the emitted asm):** MEASURED that `RegisterPlanner.plan` produced NO placement for the common `f(a, b) { ... }` shape — the interference graph only creates nodes for `KofStoreLocal`-defined slots, and PARAMS are ABI-defined (they arrive in registers, never stored), so the register model was INERT for params (the majority of real methods). FIX: `RegisterPlanner.plan` adds the param slots via `InterferenceGraph.withParams` (params are live from entry, so two params live at entry interfere and get DISTINCT registers); this required `LivenessAnalysis.blockCount()`. Two bugs found+fixed while enabling it: (a) the emitter's `paramSlotMax` counts slot 0 as `this` even for a STATIC method, so it over-counted by one and the prologue copied an extra slot, CLOBBERING a live register-placed param (SIGSEGV in `NativeErasureBoxE2ETest.erasureBoxNativeX86`); the prologue now uses the REAL param-slot count. Validated: full native x86-64 batch 252/252 + cross (aarch64/riscv64) 113/113. Proof `RegisterPlannerTest` 12/12 (new `twoParamsArePlacedInDistinctRegisters`). (`Av1CoeffsE2ETest.av1CoeffsOnNativeX86` fails with a native array-bounds error BOTH with the flag ON and OFF — PRE-EXISTING, independent of this change.); the JS-robust slice is the alternative to unblock §3 O2. **D-JS-ROBUST reconnaissance (measured 14/10, `D-JS-ROBUST` fix path NARROWED):** I investigated wiring `JumpThreading` (the §3 O2 structural pass) by making the JS reconstruction robust. Finding: it is NOT one blocker but TWO, both in the JS DECOMPILER's exact-shape pattern matchers (the JS backend is a decompiler that reconstructs if/while/switch from the exact jump targets/labels). (1) A CONDITIONAL jump retarget breaks `JsControlFlowParser` — the parser requires a `KofConditionalJump` immediately followed by `Label(cj.trueLabel())` (`JsExpressionStatementParser`), which threading breaks. (2) Threading ONLY UNCONDITIONAL jumps fixes blocker (1) — `KofJsE2ETest` 38/38 + `CoreRegressionE2ETest` 104/104 stay green — but the SWITCH reconstruction (`JsSwitchParser`) then breaks on a nested switch (`KofSwitchExprE2ETest.nestedJs` → `KofJS: store with empty stack`): it derives `switchEnd` and body labels from the exact `KofJump` targets, so a retargeted jump makes the linear scan consume a different op set and desync the stack. CONCLUSION: wiring `JumpThreading` needs BOTH the if/loop parser AND the switch parser made trampoline-aware — a multi-file change in the JS decompiler, not a bounded slice; shipped nothing rather than a regression (Q5/zero-regression). The fix path is precise: teach `JsControlFlowParser` AND `JsSwitchParser` to resolve a retargeted target through the trampoline map (or have `JumpThreading` also collapse the now-unreachable trampoline labels), then wire the unconditional-only form. **D-JS-ROBUST narrowed further (measured 14/10):** I implemented the second option above — `JumpThreading` now ALSO removes an orphaned trampoline (`Label(L); Jump(M)` with no remaining referent and no fall-through into it), guarded so a fall-through entry keeps it. Measured result: (1) threading CONDITIONAL jumps still breaks `KofJsE2ETest.execBreakContinue` (the `JsExpressionStatementParser` exact-shape requirement) — so the pass now defaults to UNCONDITIONAL ONLY (`new JumpThreading(true)` opts in), and with that `KofJsE2ETest` 38/38 is green as the earlier reconnaissance predicted; (2) the orphaned-trampoline removal does **NOT** fix the switch — `KofSwitchExprE2ETest.nestedJs` still desyncs (`KofJS: store with empty stack`), so the blocker is NOT the leftover trampoline label but the retargeted jump target itself. CONCLUSION: `D-JS-ROBUST` is now ONE file, not two — `JsControlFlowParser` is satisfied by the unconditional-only form, and only `JsSwitchParser` still derives `switchEnd`/body labels from exact jump targets. The wiring was REVERTED (Q5: `Optimizer` unchanged); the improved additive pass + its 9 unit tests remain (`anOrphanedTrampolineIsRemoved`, `aTrampolineReachedByFallThroughIsKept`, `aConditionalJumpIsNotThreadedByDefault`). **CORRECTION (measured 14/10, same session — my own conclusion above was WRONG):** I instrumented `JsSwitchParser.parseSwitchStatement` and it is **NEVER ENTERED** for `SRC_NESTED_JS` — the nested switch EXPRESSION does not lower to a `#switch` statement at all. The failing stream is an ordinary IF-expression chain (`CJ NE(0,1); Label(0); <then>; Label(1); <else>; Label(2)` then `StoreLocal` of the result), and the desync is in the if-expression MERGE: with threading both arms jump to the same final label, and after consuming it the expression value is gone (`KofJS: store with empty stack`). Two hypotheses were TESTED and REJECTED: (a) making `switchStructLabels` cleanup reference-safe (only remove labels this switch added) — still fails; (b) consuming only the derived `switchEnd` instead of any following label — still fails. Both edits were REVERTED. So the remaining `D-JS-ROBUST` work is in the IF-EXPRESSION handling (`JsControlFlowParser`/`JsExpressionStatementParser`), NOT `JsSwitchParser` — the next investigation must start from the merge-point consumption in the if-expression path, not from switch structure. The wiring stays REVERTED (Q5: `Optimizer` unchanged); the improved additive pass + its 9 unit tests remain (`anOrphanedTrampolineIsRemoved`, `aTrampolineReachedByFallThroughIsKept`, `aConditionalJumpIsNotThreadedByDefault`).
state: active
owner: `192.168.15.130:9093` (lane docs/lab)

---

## 1. Goal

Establish an optimization strategy for the Kof Native backend capable of producing small, efficient and predictable assembly on different architectures without turning each backend into an independent compiler.

The goal is not merely to emit fewer instructions.

The goal is to preserve the intent of the program through the pipeline:

```text
Kof
 ↓
AST
 ↓
Kof IR
 ↓
semantic analysis
 ↓
ISA-independent optimizations
 ↓
lowering
 ↓
ISA-specific optimizations
 ↓
register allocation
 ↓
instruction selection
 ↓
assembly
```

The same Kof construct must have an intermediate representation rich enough to allow common optimizations, while each backend can exploit features of the destination architecture.

---

# 2. Principles

## 2.1 Assembly is not the primary optimization level

Kof should not rely on transforming already-emitted assembly as its main optimization mechanism.

Preferred:

```text
AST
 ↓
IR
 ↓
optimized IR
 ↓
backend
 ↓
assembly
```

and not:

```text
AST
 ↓
bad assembly
 ↓
assembly optimizer
 ↓
less bad assembly
```

The final assembly may have peephole optimization, but that should be a last layer.

---

## 2.2 Optimization should be cross-target by default

A transformation should be implemented only once when its validity is independent of the architecture.

Examples:

* constant folding;
* constant propagation;
* dead-code elimination;
* unreachable-code elimination;
* common subexpression elimination;
* algebraic simplification;
* temporary elimination;
* copy propagation;
* branch reduction;
* effect analysis;
* elimination of redundant operations.

The architecture should only participate when the transformation depends on the ISA, ABI or memory model.

---

## 2.3 The backend should not know the whole language semantics

The backend should receive a representation already sufficiently reduced.

The backend should answer:

> “How do I express this operation on this ISA?”

and not:

> “What did the program mean?”

This keeps the border:

```text
Kof IR
   │
   │ semantics
   ▼
Optimizer
   │
   │ reduced operation
   ▼
Target Lowering
   │
   ▼
ISA
```

---

# 3. Optimization layers

The pipeline must be divided into explicit layers.

## O1 — semantic optimizations

Architecture-independent.

Examples:

```text
1 + 2
↓
3
```

```text
x * 0
↓
0
```

```text
if (false) {
    ...
}
↓
remove block
```

```text
x = y
return x
↓
return y
```

These transformations should operate on the IR.

---

## O2 — structural optimizations

Transform control flow and the representation of operations.

Includes:

* basic block simplification;
* branch folding;
* block merging;
* unreachable block removal;
* jump threading;
* loop simplification;
* loop invariant code motion;
* strength reduction;
* induction variable simplification.

Example:

```text
if (condition) {
    return 10;
} else {
    return 20;
}
```

must reach the backend already represented as a minimal flow. **§3 O2 jump threading WIRED (14/10):** `JumpThreading` is now LIVE in the production `Optimizer.passes()` (between algebraic simplification and dead-effect elimination), unconditional jumps only. Two guards make it safe, both found by MEASURING the JS decompiler's failure: (1) only a trampoline entered PURELY by jumps is threaded — when the previous op falls into it, the `Label(L); Jump(M)` is a structural JOIN (the merge of an if-expression) and retargeting the jumps that enter it makes their target look like a region EXIT; (2) conditional jumps are not retargeted at all (the `JsExpressionStatementParser` requires a `KofConditionalJump` whose target is the very next label — `new JumpThreading(true)` opts in for non-JS consumers). Plus the orphaned-trampoline removal from the previous slice. This UNBLOCKS `D-JS-ROBUST` without touching the JS backend: the earlier conclusion that the JS decompiler needed multi-file changes was wrong — the pass just had to avoid rewriting the shapes the decompiler depends on. Proof: `JumpThreadingTest` 11/11 (incl. `aTrampolineReachedByFallThroughIsNotThreaded`, PROVEN RED with the guard removed → `nestedJs` fails `KofJS: store with empty stack`) + `KofJsE2ETest` 38/38 + `KofSwitchExprE2ETest` 32/32 + `CoreRegressionE2ETest` 104/104 + broad backend batch 408/408 (cross riscv64/aarch64, register locals, peepholes, DWARF, determinism).

---

# 4. O3 — memory optimizations

Kof should reduce unnecessary loads and stores before ISA-specific lowering.

Example:

```text
store x
load x
```

when no operation is able to change `x` between the two points:

```text
value already available
```

The backend should not need to emit:

```asm
store
load
```

and then try to eliminate both instructions afterward.

The analysis should happen on the IR.

---

# 5. O4 — register allocation

Kof should have an explicit register-allocation step.

Goal:

```text
IR temporaries
       ↓
virtual registers
       ↓
register allocation
       ↓
physical registers
```

The initial implementation may use a simple, deterministic strategy.

Later these may be introduced:

* liveness analysis;
* interference graph;
* linear scan;
* graph coloring;
* spill heuristics;
* register class constraints;
* caller/callee-saved awareness.

The choice of strategy should not be part of the language contract.

---

# 6. O5 — target lowering

After the cross-target optimizations, abstract operations must be converted into ISA-specific operations.

Example:

```text
ADD
```

may become:

```text
x86_64 → add
aarch64 → add
riscv64 → add
```

But some operations may require completely different sequences.

The lowering should encapsulate those differences.

---

# 7. O6 — ISA-specific optimizations

Each backend may have its own optimizations.

## x86_64

Possible optimizations:

* `lea` for suitable arithmetic operations;
* choice between `mov`, `lea` and combined operations;
* efficient use of flags;
* reduction of instructions for constants;
* selection of lower-cost instructions;
* future SSE/AVX usage once supported.

Conceptual example:

```text
a + b * 4
```

may be expressed as:

```asm
lea rax, [rdi + rsi*4]
```

when the situation allows.

---

## AArch64

Possible optimizations:

* use of immediate forms;
* addressing modes;
* shift + arithmetic combinations;
* appropriate choice among `add`, `sub`, `lsl`, etc.;
* exploitation of temporary registers;
* sign-extension instructions folded into the memory access.

Conceptual example:

```text
load 32-bit signed value
extend to 64-bit
```

may be expressed directly with a load instruction using proper extension when available.

---

## RISC-V 64

Possible optimizations:

* use of immediate forms;
* reduction of constant-materialization instructions;
* addressing sequences;
* appropriate choice among `addi`, `slli`, `add`, etc.;
* use of available extensions when supported by the target;
* explicit control of sequences affected by linker relaxation.

The backend should not assume that an optimization valid on x86 exists on RISC-V.

---

# 8. Constant folding

Constant expressions must be resolved before assembly generation.

Example:

```kof
var x = 10 * 20
```

should result in:

```text
x = 200
```

and not:

```asm
mov ...
mov ...
imul ...
```

The optimization should happen on the IR.

---

# 9. Constant propagation

When a value is known:

```text
x = 10
y = x + 2
```

it must become:

```text
y = 12
```

unless an effect prevents the propagation.

---

# 10. Dead code elimination

Code whose result has no observers must be removed.

Example:

```text
x = expensive()
return 42
```

If `x` is never used and `expensive()` has no observable effects:

```text
return 42
```

The effect analysis must be explicit.

Kof must not remove calls that may:

* mutate observable memory;
* perform I/O;
* mutate global state;
* touch FFI;
* trigger effects defined by the runtime.

---

# 11. Copy propagation

Sequences like:

```text
a = b
c = a
```

may be reduced to:

```text
c = b
```

This also lowers register pressure.

---

# 12. Strength reduction

Costly operations may be replaced when semantically equivalent.

Example:

```text
x * 2
```

may become:

```text
x << 1
```

when the type semantics allow it.

But the transformation must not be applied blindly.

The real cost of the operation depends on the ISA and the context.

---

# 13. Peephole optimization

A small final step may observe specific instruction sequences.

Example:

```asm
mov rax, rbx
mov rcx, rax
```

may be reduced to:

```asm
mov rcx, rbx
```

when safe.

Peephole optimization should remain small.

It must not become a second implementation of the compiler.

---

# 14. ABI and optimization

The ABI is part of the backend contract.

Optimizations must never incorrectly change:

* argument registers;
* return registers;
* caller-saved;
* callee-saved;
* stack alignment;
* parameter layout;
* struct layout;
* return rules;
* external calling conventions.

Especially for FFI:

```text
Kof
 ↓
ABI lowering
 ↓
C / foreign function
```

must produce exactly the convention expected by the target.

An optimization that breaks the ABI is incorrect even if it appears to produce better assembly. **§14 stack alignment LANDED (14/10):** "Optimizations must never incorrectly change … stack alignment" was NOT held: MEASURED (a `%rsp`-delta simulation over the emitted asm) that generated method bodies called with `%rsp % 16 == 8` — an odd operand push in flight — at every call site under an odd-push span, e.g. `main` after `leaq System.out; pushq %rax` then three calls. SysV requires 16-byte alignment at `call`; a callee using aligned SSE (glibc `pthread_create`) faults — the class already recorded in §253. New `NativeX86StackAlign` (a text pass, run LAST in the peephole after every push/pop fold decides the final stack shape) inserts `subq $8,%rsp` before and `addq $8,%rsp` after each misaligned call. **A stack-passed-argument hazard was found and fixed during the landing:** a 7+-arg call pushes its stack args just before the `call` (arg7 at `0(%rsp)`), so padding between them makes the callee read the padding word as arg7 — `Av1CoeffsE2ETest` went out-of-bounds; the `subq` is now inserted BELOW those pushes (only the emitter's `pushq -N(%rbp)` form is treated as a stack arg). MEASURED on a real program: generated methods 6 misaligned → 0. Proof `NativeX86StackAlignTest` 5/5 (incl. the stack-arg placement) + `Av1CoeffsE2ETest` + `NativeE2ETest` 68/68 + `KofTimeE2ETest` + `CoreRegressionE2ETest` 104/104 + broad batch 539/539. **§14 invariants GUARDED mechanically (14/10):** the §14 fixes were each proved once by measurement; `NativeAbiInvariantsTest` now makes them properties a future optimization cannot silently break: it compiles a program exercising early returns, loops, try/catch, 7-argument calls and a record, then asserts over the emitted x86-64 that (1) every `call` in every generated method is 16-byte aligned, and (2) every generated method that SAVES a callee-saved register (`rbx`/`r12`–`r15`) also restores it. Measured: all shapes currently hold (callee-saved balanced through early-return, loop and exception paths). PROVEN RED for (1): with `NativeX86StackAlign.alignCalls` removed, `Default_Main_rec_I` reports `[-8, -8]`. Proof `NativeAbiInvariantsTest` 2/2 + broad batch 324/324. **§14/§15 padding layouts LANDED (14/10):** every golden shape in `AbiLayoutTest` was composed of 4- and 8-byte fields, so NONE exercised the alignment padding a 2-byte `short`/Kof-`char` introduces — the exact place where an emitted offset can silently diverge from the C layout FFI observes. Added four measured shapes (`PadCL` char/long/char = 24 B, `PadICD` int/char/double = 16 B with a REAL SSE eightbyte, `PadCCC` three chars + int, `PadCI` char/int), each verified against gcc 13.3 x86-64 via the existing `_Static_assert` cross-check (`sizeof`/`_Alignof`/`offsetof`) — which passed, so the engine's offsets match the real compiler on the padding paths. The aarch64/riscv64 cross-check columns are the engine's own values (those compilers are absent here) and are re-proved live wherever the toolchains exist. ALSO FIXED a test-only landmine found by running the suite without the shell env: `NativeAbiInvariantsTest` needed the emitted `.s`, but `NativeArchEmitter` only honoured the `KOF_KEEP_ASM` ENVIRONMENT variable, so the guard failed with `NoSuchFileException` when the env was absent. The emitter now honours the SYSTEM PROPERTY too, and the test sets it itself — the guard no longer depends on how the suite is launched. Proof `AbiLayoutTest` 3/3 + FFI/layout/native batch 209/209. **§14/§15 FFI padding END-TO-END (14/10):** the declared layout was covered, but nothing asserted the EMITTED FFI offsets for a padding shape. Added `FfiStructPaddingE2ETest` (own class; `FfiStructE2ETest` was at the 500-line boundary): a C shim `struct PB { _Bool a; int b; }` (C places `b` at offset 4, struct 8 B) plus `PBB {_Bool,_Bool,_Bool,int}` (8 B, `d` at 4) called BY VALUE. NATIVE x86-64 is CORRECT — prints the shim's own arithmetic exactly (107 and 5101), matching the C `offsetof`/`sizeof` values measured with gcc. **The JVM/FFM path REFUSES this shape** at run time (`INTEROP010 … Invalid alignment constraint for member layout: i4`): the JVM struct token is only `@`, so the runtime derives the layout leniently from the record with no padding between `i1` and `i4`, and `MemoryLayout.structLayout` rejects it. That is catalogued as **§762** (any record with a 1-byte field before a wider one cannot cross FFI by value on the JVM; it is correct on native, and the refusal is LOUD, never a wrong result). The test asserts BOTH halves, so the day §762 is fixed the JVM half fails and forces the catalogue update. **§762 FIXED (20/10, same lane):** the JVM/FFM derived layout now inserts INTERIOR padding. Three sites had the same missing-padding assumption — `JvmFfiRuntime.kof_ffi_struct_layout_of` (explicit interior `paddingLayout` per C alignment + the trailing one), the struct field WRITER/READER (`PathElement.groupElement(k)` stops being field k once padding members exist; both now use a `kof_ffi_field_offset` helper with the same rule), and the JS mirror (`KofJsFfiBridge.structLayout` + a new `fieldOffset`, used by `KofJsFfiMarshal` and `readStruct`). The JVM now matches NATIVE byte-for-byte on `PB`/`PBB`. PROVEN RED (interior gap zeroed → `Target offset 1 is incompatible with alignment constraint 4`). Proof `FfiStructPaddingE2ETest` 1/1 (JVM golden + JVM↔Native parity) + `FfiStructE2ETest` 14/14 + broad FFI/native batch 217/217. Proof `FfiStructPaddingE2ETest` 1/1 + `FfiStructE2ETest` 14/14 + `AbiLayoutTest` 3/3 + broad FFI/native batch 205/205.

---

# 15. Structs and layout

Optimizations must preserve the layout defined by the ABI contract.

For:

```c
struct Pair {
    int a;
    int b;
};
```

the backend must keep:

```text
a = 32 bits
b = 32 bits
total = 64 bits
```

Optimizations may eliminate redundant loads/stores but must not change the layout observable via FFI.

---

# 16. Cross-target equivalence

The same Kof program should produce semantically equivalent results on:

```text
x86_64
aarch64
riscv64
```

The assembly does not need to be identical.

It is necessary that:

```text
results
observable effects
ABI
layout
semantics
```

are equivalent.

So:

```text
same IR
   ↓
┌──────────┬──────────┬──────────┐
x86_64     AArch64    RISC-V
 ↓           ↓          ↓
asm A       asm B      asm C
```

is expected.

---

# 17. Benchmark-driven optimization

No ISA-specific optimization may be promoted just because “it looks better in assembly”.

Each relevant optimization must have:

1. minimal case;
2. expected assembly or expected properties;
3. benchmark;
4. before/after comparison;
5. correctness validation;
6. coverage on the supported targets.

The goal is to avoid cosmetic optimizations.

Smaller assembly does not necessarily mean faster code.

---

# 18. Metrics

The backend should track, when possible:

* `.text` size;
* instruction count;
* load count;
* store count;
* branch count;
* spill count;
* call count;
* register usage;
* runtime;
* final ELF size.

The metric should be chosen according to the optimization’s goal. **§18 asm-level metrics LANDED (14/10):** §18 also lists counters for the EMITTED backend (instruction count, loads, stores, branches, calls) — `IrMetrics` only covers the IR stream, so the ISA peepholes (§7) had no way to be measured on the text they rewrite. New `AsmMetrics` counts an emitted x86-64 method body: labels, directives (`.loc`/`.file`) and comments are not instructions; only the MOV family is classified by memory operand (a `lea` does not dereference, so it is never a "load"). Measured on a real loop method (`s = s + a*2 + b - c` with continue/break): **instructions=45 loads=9 stores=12 branches=6 calls=0 returns=1**. `AsmMetricsTest` 5/5. Additive and read-only — it never changes what is emitted.

---

# 19. Debuggability

The optimizations should not completely destroy debuggability.

When debugging is enabled, the backend should preserve enough information to relate:

```text
Kof source
    ↓
IR
    ↓
machine instruction
```

Optimizations may remove variables or merge operations, but that should be represented correctly in future DWARF information. **§19 DWARF register locations LANDED (14/10):** the register-locals model (`D-CODEGEN-REWRITE`) keeps an int/bool local in a physical register for the whole function, but `NativeDwarf` described EVERY local as frame-relative (`DW_OP_fbreg slotOffset`). For a register-placed local that frame slot is never written after the prologue, so the debug info told a debugger to read a stale slot — §19's "represented correctly" violated. `NativeDwarf.Local` now carries an optional DWARF register number and emits `DW_OP_regN` (0x50+N, SysV numbering rbx=3 … r15=15) for a placed local, keeping `DW_OP_fbreg` for a framed one; `NativeMethodEmitter` passes the placement it already computes. `locationBytes` was extracted so the encoding is unit-testable. Proof `NativeDwarfLocalLocationTest` 5/5 + all DWARF suites (`NativeDwarfSubprogramTest`, `NativeDwarfLineInfoTest`, `NativeDwarfCrossTest`) 16/16 + broad native batch 220/220 (incl. cross + register locals).

---

# 20. Determinism

Assembly generation must be deterministic.

Given:

```text
same source
same target
same flags
same compiler version
```

the result must be reproducible. **§20 artifact determinism LANDED (14/10):** `OptimizerDeterminismTest` only proves the OPTIMIZER is deterministic (same ops in, same ops out). §20 claims the emitted result is reproducible, so new `NativeAsmDeterminismTest` compiles the same source twice into two SEPARATE output directories and asserts the executables are byte-identical — an end-to-end check that also covers the §7 ISA peepholes and the label counter (both only visible after emission). Two shapes (loop+branch+arithmetic; branch-heavy classify) both reproduce exactly. Proof `NativeAsmDeterminismTest` 2/2.

This helps:

* tests;
* debugging;
* assembly comparison;
* benchmarks;
* releases;
* regression investigation.

---

# 21. Implementation phases

## Phase A — infrastructure

* define the optimizer contracts;
* define the basic-block representation;
* define virtual registers;
* define use analysis;
* define effect analysis;
* define the pass pipeline.

**Criterion:** no existing backend may lose behavior.

---

## Phase B — basic optimizations

Implement:

* constant folding;
* constant propagation;
* copy propagation;
* dead-code elimination;
* unreachable-code elimination;
* basic block simplification.

**Criterion:** measurable reduction in instructions on representative fixtures.

---

## Phase C — register allocation

Implement:

* liveness;
* virtual registers;
* allocation;
* spills;
* caller/callee-saved handling.

**Criterion:** reduction in artificial loads/stores and intact ABI on all targets.

---

## Phase D — target lowering

Formalize:

```text
Kof IR operation
       ↓
Target lowering
       ↓
Machine instruction
```

Each backend must explicitly declare which operations it supports.

**Phase D slice 1 LANDED (14/10):** `NativeOpSupport` — the native x86-64 backend's op-support declaration as explicit DATA (`Set<Class<?>> X86_64`, all 37 IR ops); the emitter's `switch` is its materialization and its `default` now asserts the op is NOT declared (R6: never silent). `NativeOpSupportTest` guards the contract three ways: (1) the declaration must EQUAL the emitter's handled ops (drift in either direction fails); (2) it must be COMPLETE against every `KofOperation` in the compiler — a NEW op fails here, a deliberate decision point, instead of a runtime `UnsupportedOperationException` in the field; (3) PROVEN RED on a deliberately-removed op (`KofUnary`) before restore.  **Phase D slice 2 LANDED (14/10):** the CROSS backend declaration — `NativeOpSupport.RISCV64_CROSS` (= `X86_64` minus `KofFunctionAddress`, which the cross lowering never materializes; higher-order calls go through `KofCall`) + `supportsCross`. `NativeOpSupportTest` extends its drift guard to the cross emitter (`theCrossDeclarationMatchesTheCrossEmitterExactly`). AND a real Q7 fix: the cross emitter's `default` SILENTLY appended a `# NATIVE002` comment for an unhit op — a compile that "succeeds" with a wrong binary (violating "no silent fallback"). It now THROWS `IllegalStateException(\"NATIVE002: …\")`, which `CompilerPipeline` turns into a NATIVE002 diagnostic (never an artifact that pretends to run). Verified no real op hits it: the full cross suite (`NativeRiscv64E2ETest` 58/58 + `NativeAarch64E2ETest` 55/55) stays green; full native batch 483/483. **Phase D consistency audit (14/10):** after making the cross emitter refuse, I audited every native emitter's `default` for silent fallbacks. The MCU backends (`NativeMcuRiscv32`/`NativeMcuArm`) already refuse correctly (their `default` calls `unsupported(...)` → NATIVE002), so the x86-64 emitter (throws `UnsupportedOperationException`), the cross emitter (now throws `IllegalStateException`), and the MCU backends are now UNIFORM: declare supported ops, refuse the rest with a diagnosed code. The remaining `default -> sb.append(...)` cases in the tree were checked and are NOT codegen silent-skips (they are WAT-text formatting, JSON dispatch, or fully-enumerated switches over a closed 6-value `KofComparison` whose default is unreachable). **Phase D slice 3 LANDED (14/10):** the Q7 refusal is now GUARDED by a test — `NativeRiscvCrossRefusesTest` calls the cross emitter with `KofFunctionAddress` (the one op not in `RISCV64_CROSS`) and asserts it THROWS `NATIVE002` and emits nothing; PROVEN RED against the old silent-comment behavior before restore. Full native batch 446/446. **Phase D slice 4 LANDED (14/10):** a SECOND Q7 silent-fallback found+fixed in the cross emitter — the LITERAL `default` silently emitted a comment AND pushed `0` (a wrong-value fallback, worse than the op case: a zero leaves the compile "succeeding" with a wrong binary). It now THROWS `IllegalStateException("NATIVE002: …")`. `NativeRiscvCrossLiteralRefusesTest` guards it (probes with a `char`-typed literal, which Kof lowers as int so is not a supported literal kind here) — PROVEN RED against the old silent-zero behavior before restore. Verified no real literal hits it (full cross suite 113/113); full native batch 445/445. **Phase D slice 5 LANDED (14/10):** third Q7 silent fallback — `NativeAarch64Translator`'s `UNHANDLED` path passed an unknown riscv mnemonic through to the aarch64 assembler verbatim (only a stderr WARN). That is a silent-miscompile risk: a same-named aarch64 instruction with different semantics would assemble and run wrong. All labels/directives/comments/empty lines are handled earlier, so the fallback is by definition an unknown op — it now THROWS `IllegalStateException("NATIVE002: …")`. `NativeAarch64TranslatorRefusesTest` guards it (unknown mnemonic refused + labels/directives still pass) — PROVEN RED against the old passthrough before restore. Confirmed dead in practice: cross+aarch64+broad batch 230/230, nothing hits it. **Phase E slice 25 LANDED (14/10, found by re-measuring the asm):** x86_64 §7 "choice between mov, lea and combined operations" — `lea{l,q} OP, %{e,r}ax` + `movq %rax, %rN` folds into `lea{l,q} OP, %rN` (the lea writes the callee's destination directly, dropping the move). Safe because a lea has no side effects but writing its destination (no flags) and reads its operand BEFORE writing the destination in the same instruction, so a destination that also appears in the operand is fine. MEASURED: 93 compute-in-rax-then-move sites across a sample, 13 of them lea pairs (the newest folds leave the lea result in rax, which a following move then re-materializes). Proof `NativeX86PeepholeTest` 47/47 + the full native batch 373/373 + cross + broad sanity 339/339. **Phase E slice 26 LANDED (14/10):** x86_64 §7 "efficient use of flags" — a flag-only test immediately after an `and/or/xor/test` of the SAME full register (`andl $m, %eax; testl %eax, %eax` or `; cmpl $0, %eax`) is REDUNDANT and dropped: and/or/xor/test already set SF/ZF/PF from that register and clear CF/OF exactly as `test %R,%R` does. ONLY and/or/xor/test qualify — `add`/`sub` set CF (carry/borrow) differently from `test` (CF=0), so they are NOT folded (a following `jb`/`jae` would change meaning). MEASURED: 37 sites in a sample. Proof `NativeX86PeepholeTest` 51/51 + the full native batch 527/527 + cross + broad sanity 343/343. **Phase E slice 27 LANDED (14/10):** x86_64 §7 "lower-cost instruction selection" — `jcc X` + `jmp Y` where `X` is the label IMMEDIATELY after the `jmp` means "if cc go to X (fall through), else to Y", which is exactly `j!cc Y`. The pass inverts the condition and drops the `jmp` — one instruction fewer per site. Full x86 condition-code inversion table; guarded against `X == Y` and indirect (`jmp *reg`) targets. MEASURED: 188 sites in a sample. Proof `NativeX86PeepholeTest` 55/55 + the full native batch 563/563 + cross + broad sanity 347/347. **REFACTOR-500 (14/10):** `NativeX86Peephole` reached 556 lines (near the 600 gate), so the branch/flag folds (jump-to-next, jcc-inversion, leave, cmp-zero→test, redundant-flag-test) were extracted to a new `NativeX86BranchFold`; the peephole delegates. `NativeX86Peephole` 556 → 373 lines, `NativeX86BranchFold` 198 — both well under the gate; behavior-preserving (55/55 + full native batch 567/567).

---

## Phase E — ISA optimizations

Add specific optimizations:

```text
x86_64
AArch64
RISC-V
```

without contaminating the IR with unnecessary details of a single architecture.

**Phase E slice 1 LANDED (14/10):** x86_64 §7 "reduction of instructions for constants" — an immediate that fits in a sign-extended imm32 is emitted as a single `pushq $imm` instead of `movq $imm, %rax; pushq %rax` (2 instructions → 1). Applies to `int` (always 32-bit) and to a `long` only when it round-trips through `int` (a wide long keeps the two-instruction path, since `pushq` sign-extends). Proof `NativeConstEmissionTest` 5/5 (int, negative int, small long, wide long, bool) + the full native x86-64 batch 225/225. This is native-only (the JS/JVM backends are unaffected) and needs no IR change — the plan's "without contaminating the IR" requirement. **Phase E slice 2 LANDED (14/10):** x86_64 §7 "reduction of instructions" — the return-site `pushq %rax` + `popq %rax` no-op pair is collapsed: the return value is the last value pushed, and when that push was `pushq %rax` (a binary/call/frame-load result — rax already holds it) the pop is redundant, so both are dropped; any other producer (a register-placed local's `pushq %rbx`, a literal's `pushq $imm`) still needs the pop into rax. The helper skips trailing `.loc` debug lines (a source position sits between the push and the return). Proof `NativeReturnPeepholeTest` 4/4 + the full native x86-64 batch 234/234. Extracted to `NativeReturnEmit` to keep `NativeMethodEmitter` under the 500-line gate (633 → 588; `dwarfKindOf` also moved to `NativeDwarf`). **Phase E slice 3 LANDED (14/10):** x86_64 §7 "reduction of instructions for constants" — a CONSTANT shift count uses the immediate form `shl{l,q} $N, %{e,r}ax` instead of round-tripping the count through `%rcx` (`pushq $N; popq %rcx; shl %cl`); the count is masked (31 for int, 63 for long) to match the JVM/hardware semantics exactly, and a computed count keeps the `%cl` form. Proof `NativeShiftImmediateTest` 4/4 + the full native x86-64 batch 234/234. **Phase E slice 4 LANDED (14/10):** x86_64 §7 "reduction of instructions for constants" — a CONSTANT right operand of ADD/SUB/MUL/AND/OR/XOR uses the immediate form `op{l,q} $N, %{e,r}ax` (or `imul{l,q} $N, %{e,r}ax`) instead of round-tripping it through `%rcx`. The constant is the LAST value pushed (the RHS); for the non-commutative SUB that is exactly the operand the immediate form expects, so the result is identical — a constant FIRST operand (`100 - x`) is NOT on top and correctly keeps the `%rcx` form (verified). Proof `NativeArithImmediateTest` 5/5 + the full native x86-64 batch 238/238. **Phase E slice 5 LANDED (14/10):** x86_64 §7 "reduction of instructions for constants" — a CONSTANT right operand of a COMPARISON (EQ/NE/LT/LE/GT/GE) uses the immediate form `cmp{l,q} $N, %{e,r}ax` instead of round-tripping it through `%rcx`; `x == 0`/`x != 0` use the canonical `test{l,q} %eax, %eax`. Proof `NativeCompareImmediateTest` 6/6 + the full native x86-64 batch 243/243. **REFACTOR-500 (14/10):** `NativeX86Arith` reached 592 lines (the §7 items above grew it), so the SSE float/double binary arithmetic was extracted to a new `NativeX86SseArith` (the immediate-form helpers stay in `NativeX86Arith`). `NativeX86Arith` 592 → 417 lines; behavior-preserving (full native batch 249/249 + `SwitchLongDoubleSupportE2ETest` + `FloatCompoundAssignE2ETest` + cross all green). **Phase E slice 6 LANDED (14/10):** x86_64 §7 "reduction of instructions for constants" — a CONSTANT right operand of an INTEGER conditional jump (the branch form of a comparison) uses the immediate form `cmp{l,q} $N, %{e,r}ax` (or the canonical `test{l,q} %eax, %eax` for `== 0`/`!= 0`) instead of popping it into `%rcx`; a computed operand keeps the register compare. Proof `NativeCondJumpImmediateTest` 3/3 + the full native x86-64 batch 249/249. (The `constantPushedOnTop`/`dropLastNonLocLine` helpers moved to package-private in `NativeX86Arith` and are shared.) **Phase E slice 7 LANDED (14/10):** x86_64 §7 "lower-cost instruction selection" — a text peephole (`NativeX86Peephole`) over each emitted method body collapses the stack machine's adjacent `pushq %X; popq %Y` pair into `movq %X, %Y` (or drops both when `X == Y`), one instruction instead of two, no stack round-trip. It only matches register-register pairs (an immediate/memory push is left alone) and only within the method's own emitted region (a `.loc` between the pair does not block it). Applied at the end of `emitMethod`. Measured: a loop body `s = s + a*2 + b` loses ~6 stack round-trips per iteration. Proof `NativeX86PeepholeTest` 6/6 + the full native x86-64 batch 252/252 + cross + JVM/JS/switch. **Phase E slice 8 LANDED (14/10):** the peephole also collapses an IMMEDIATE push (`pushq $N; popq %Y` → `movq $N, %Y`, exact: the immediate fits a sign-extended imm32 by construction — see `emitLoadLiteral`), while a memory push is left alone. A local initialized to a constant (`var s = 0`) now emits one `movq $0, %reg` instead of a stack round-trip. Proof `NativeX86PeepholeTest` 7/7 + the full native x86-64 batch 259/259. **Phase E slice 9 LANDED (14/10):** the peephole also collapses a push/pop SPANNING exactly one `movq` (`pushq %A; movq %B, %C; popq %D` → `movq %A, %D; movq %B, %C`) — the shape of a binary op whose two operands are both register values. Strict guards keep it correct: the middle move must not read or write `%D` (clobber hazard). This saves an instruction on EVERY two-register binary op. A bug found+fixed while landing it: dropping the push before the pop shifted the pop's index and deleted the wrong line (SIGSEGV in `NativeE2ETest.execListGrow`); the pop is now removed first. Proof `NativeX86PeepholeTest` 10/10 (incl. the spanned self-pop regression) + the full native x86-64 batch 262/262. **Phase E slice 10 LANDED (14/10):** the peephole also folds a scratch load into its op: `movq %Y, %rcx` + `addl %ecx, %eax` → `addl %Y32, %eax` (the op reads only the low 32 of rcx, which the movq set to exactly Y — exact; the result and flags are unchanged). This removes one instruction per two-register binary op (`+ - * & | ^` and integer compares). Strict guard: only when `%rcx`/`%ecx`/`%cl` appears in the method SOLELY as such a scratch load immediately followed by a substitutable op — a shift (count must stay in `%cl`) or an rcx store to a register-placed local disables the whole fold. Proof `NativeX86PeepholeTest` 14/14 + the full native x86-64 batch 266/266. **Phase E slice 11 LANDED (14/10):** the scratch fold also covers the flag-only DESTINATION of a compare (`movq %Y, %rcx; cmpl %eax, %ecx` → `cmpl %eax, %Y32`) and skips intervening independent `movq %Z, %rax` operand loads, so the loop-condition shape (`i < n` → `cmpl %eax, %r13d`) loses both scratch loads. Substitution uses the operand's WIDTH (32/64). Proof `NativeX86PeepholeTest` 16/16 + the full native x86-64 batch 268/268. **Phase E slice 12 LANDED (14/10):** x86_64 §7 "lea for suitable arithmetic operations" — `movq %X, %rax` + `shl{l,q} $N, %{e,r}ax` (N=1/2/3, a multiply by 2/4/8, what `StrengthReduction` produces) folds into a single `lea` that scales X directly (`leal (%ebx,%ebx), %eax`, `leal 0(,%ebx,4), %eax`, `leal 0(,%ebx,8), %eax`). One instruction instead of two; scale >8 has no lea form and is left alone. Proof `NativeX86PeepholeTest` 21/21 + the full native x86-64 batch 273/273. **Phase E slice 13 LANDED (14/10):** x86_64 §7 "efficient use of flags" — `movq %X, %rax` immediately followed by a compare/test that reads `%eax/%rax` folds the materialization away: `cmpl $2, %eax` → `cmpl $2, %X32`, `testl %eax, %eax` → `testl %X32, %X32`. Safe because the compare reads only rax's low 32 (or full 64), which the movq set to exactly X, and everything after a compare reads the FLAGS or overwrites rax (setcc+movzbl), never its value. Common in branch conditions (`if (n < 2)`). Proof `NativeX86PeepholeTest` 26/26 + the full native x86-64 batch 273/273. **Phase E slice 14 LANDED (14/10):** x86_64 §7 "choice between mov, lea and combined operations" — `movq %X, %rax` + `add{l,q} %Y, %{e,r}ax` (or `add $imm`) folds into a single `lea` computing the sum without a materialization: `leal (%ebx,%r12d), %eax`, `leal 7(%ebx), %eax` (64-bit: `leaq`). Safe because Kof's add result is a value in rax and its FLAGS are unused (comparisons are explicit `cmp`, never a flag-consuming jcc after an add), so the flags-agnostic `lea` is equivalent. So `a + b` now emits ONE instruction. Proof `NativeX86PeepholeTest` 29/29 + the full native x86-64 batch 281/281. **Phase E slice 15 LANDED (14/10):** x86_64 §7 lea for suitable arithmetic operations — a multiply by 3/5/9 folds into a `lea` with a scaled index: `imull $3` becomes `leal (%ebx,%ebx,2), %eax`, `$5` becomes `leal (%ebx,%ebx,4), %eax`, `$9` becomes `leal (%ebx,%ebx,8), %eax`. Other constants (7, 11, ...) have no base+index*scale form and stay `imull`. Proof `NativeX86PeepholeTest` 32/32 + the full native x86-64 batch 284/284. **Phase E slice 16 LANDED (14/10):** x86_64 §7 choice between mov/lea/combined — an immediate add folds into an existing `lea` displacement: `leal (%ebx,%ebx,2), %eax; addl $1, %eax` becomes `leal 1(%ebx,%ebx,2), %eax` (and `leal (%ebx), %eax; addl $7` becomes `leal 7(%ebx), %eax`). Safe: the lea has no side effects but writing rax and the immediate add's flags are unused. A lea with an existing displacement is left alone (unambiguous match). Proof `NativeX86PeepholeTest` 36/36 + the full native x86-64 batch 284/284. **REFACTOR-500 (14/10):** the lea folds (shift/mul→lea, add→lea, add-into-displacement) were extracted to a new `NativeX86LeaFold` (the file had reached 584 lines, near the 600 gate). `NativeX86Peephole` 584 → 357 lines; behavior-preserving (full native batch 288/288). **Phase E slice 17 LANDED (14/10):** x86_64 §7 lower-cost instruction selection — `cmp{l,q} $0, %reg` is rewritten to `test{l,q} %reg, %reg` (identical FLAGS, shorter encoding, no immediate). Proof `NativeX86PeepholeTest` 40/40 + the full native x86-64 batch 288/288. **Phase E slice 18 LANDED (14/10):** x86_64 §7 lower-cost instruction selection — `movq %rbp, %rsp` + `popq %rbp` (the frame teardown) is rewritten to the single `leave` instruction (same effect, one byte shorter). Proof `NativeX86PeepholeTest` 42/42 + the full native x86-64 batch 292/292. **Phase E slice 19 LANDED (14/10):** x86_64 §7 lower-cost instruction selection — an unconditional `jmp L` immediately followed by the label `L:` (only `.loc` between) jumps to the very next instruction, so the jump is dropped. Proof `NativeX86PeepholeTest` 44/44 + the full native x86-64 batch 296/296. **Phase E slice 20 LANDED (14/10):** AArch64 §7 "use of immediate forms" — `andi`/`ori` now emit the DIRECT bitmask immediate (`and x9, x9, #-16`, `orr x22, x22, #2`) instead of always `mov x17, #imm` + `op x9, x9, x17`. Gated by `NativeAarch64Helpers.isLogicalImm`, VERIFIED against the real assembler over a measured accept/reject table (0 and -1 are rejected; `0xFF00`/`0xAA..AA`/`0x00FF00FF00FF00FF` accepted — the naive run-of-ones rule was wrong). Proof `NativeAarch64LogicalImmTest` 2/2 + `NativeAarch64E2ETest` 55/55 (qemu) + the native batch 283/283. **Phase E slice 21 LANDED (14/10):** AArch64 §7 lower-cost instruction selection — a text peephole (`NativeAarch64Peephole`, applied after the riscv→aarch64 translation) collapses the riscv stack machine's push+pop triple `sub sp,sp,#8; str X,[sp]; pop Y` into `mov Y, X` (or drops it when `X == Y`). MEASURED: 29 of 99 push sequences in a sample reduce. Proof `NativeAarch64PeepholeTest` 5/5 + `NativeAarch64E2ETest` 55/55 (qemu) + cross + JVM/JS/switch. **Phase E slice 22 LANDED (14/10):** RISC-V 64 §7 lower-cost instruction selection — a text peephole (`NativeRiscvPeephole`, applied to the riscv64 program text before linking AND before the riscv→aarch64 translation) collapses the stack machine's push+pop triple `addi sp,sp,-8; sd X,0(sp); pop Y` into `mv Y, X` (or drops it when `X == Y`). MEASURED: 30 of 82 push sequences in a sample reduce. Because aarch64 is translated FROM this riscv text, this benefits BOTH cross targets. Proof `NativeRiscvPeepholeTest` 5/5 + riscv 58/58 + aarch64 55/55 (qemu) + cross. **Phase E slice 23 LANDED (14/10):** RISC-V 64 §7 lower-cost instruction selection — the riscv peephole also drops `mv X, X` self-moves (a no-op; the riscv stack machine can emit one after a fold). Proof `NativeRiscvPeepholeTest` 7/7 + riscv 58/58 + aarch64 55/55 (qemu) + cross. **Phase E slice 24 LANDED (14/10):** AArch64 §7 lower-cost instruction selection — the aarch64 peephole also drops `mov X, X` self-moves (a no-op). Proof `NativeAarch64PeepholeTest` 7/7 + aarch64 55/55 (qemu) + cross + JVM/JS/switch. A parse bug (the fixed column offset assumed `shll`, one char shorter than `imull`) was caught and fixed by finding the `$`.

---

## Phase F — benchmark suite

Create a cross-target suite containing:

* arithmetic;
* loops;
* branches;
* calls;
* structs;
* arrays;
* memory;
* FFI;
* bitwise operations;
* constants;
* cases with high register pressure.

Each benchmark should run on the available targets.

---

# 22. Promotion criteria

An optimization may leave `future/` when:

* it has a clear specification;
* it has an implementation;
* it has correctness tests;
* it has cross-target coverage when applicable;
* it does not change ABI contracts;
* it does not introduce target-dependent behavior in cross-target code;
* it has a benchmark when there is a performance impact;
* it does not significantly degrade another target without explicit justification;
* it has documentation of the expected behavior.

An optimization that improves x86_64 but degrades AArch64 and RISC-V should not be treated as universal.

It must be explicitly classified as target-specific.

---

# 23. Rule of thumb

The backend should prefer:

```text
correct semantics
    ↓
good IR
    ↓
general optimization
    ↓
correct lowering
    ↓
specific optimization
    ↓
efficient assembly
```

and avoid:

```text
naively emitted assembly
    ↓
hundreds of patches
    ↓
infinite peephole
    ↓
unmaintainable backend
```

---

# 24. Expected outcome

The final architecture must let a single Kof implementation produce proper native code for multiple architectures:

```text
                     Kof
                      │
                      ▼
                     IR
                      │
             ┌────────┴────────┐
             │                 │
       cross-target       semantic
       optimizations      analysis
             │                 │
             └────────┬────────┘
                      ▼
                Target Lowering
                      │
        ┌─────────────┼─────────────┐
        ▼             ▼             ▼
     x86_64         AArch64       RISC-V
        │             │             │
        ▼             ▼             ▼
      ASM           ASM           ASM
        │             │             │
        └─────────────┼─────────────┘
                      ▼
                   linker
                      │
                      ▼
                 executable
```

Kof remains responsible for its own native generation. C, LLVM or other languages/toolchains are not required as mandatory intermediaries.

External tools may continue to exist at the system boundary for assembler, linker, debugging or interop, but they **do not define the Kof compiler’s architecture**.

---

# 25. Out of initial scope

This plan does not initially require:

* automatic SIMD;
* auto-vectorization;
* PGO;
* JIT;
* superoptimization;
* advanced instruction scheduling;
* speculative optimization;
* aggressive floating-point optimization;
* per-microarchitecture code generation;
* replacement of the system assembler/linker.

These capabilities may be added later when evidence of need exists.

---

## Desired end state

The Native backend should produce assembly that is:

**correct, small, predictable, ABI-compatible, cross-target and competitive**, without sacrificing Kof’s architectural simplicity.

The fundamental rule stands:

> **Kof does not need to produce the most complex assembly possible. It needs to produce the necessary assembly, correctly and without accidental complexity.**
