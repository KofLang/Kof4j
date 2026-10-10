[English](DECOMPILER.md) | [Português](DECOMPILER.pt_BR.md)

# DECOMPILER.md — Kof Decompiler (DEPRIORITIZED 15/09 → REOPENED 28/09, still in future/)

**Status:** **REOPENED by the maintainer (`D-DEPRIORITIZED-REOPEN`, 28/09 batch)** —
still plan-only here; promotion to current work is one-at-a-time per
`D-FUTURE-PROMOTION` (the
three-states rule). Code already in the repo stays (kof-cli `Decompile.java` +
decoders, `DecompileTest` 67/67) — only the *queue* stops here; the full
work-log below remains as the record for a future session. Implemented:
`Decompile.java` + bytecode decoders, Phases A–E with code; proof:
`DecompileTest` **67/67** green + `DecompilePostDominatorTest` 6/6
(re-measured 15/09 on tip `7b0bfbe0`; the header's 45/45 was the 22/08
baseline). The remaining gap
(measured 15/09: 1793 stubs on the 706-class corpus; the biggest one —
struct joins with computation/invokes in the test, 519 TRAPs of the `r14`
family — needs the post-dominator walker = a dedicated future session; the
local "stop/join" guards and the naive expression-fallback were tried and
REJECTED, steps 1–3a of the work-log below)
**Date:** August 22, 2026

---

## 1. Objective

Recover idiomatic Kof code from compiled artifacts.

First targets: `.class`, `.jar`, `.war`.

```text
JVM Class File
       ↓
Class File Parser
       ↓
Bytecode IR
       ↓
Control Flow Graph
       ↓
Type Recovery
       ↓
Data Flow Analysis
       ↓
Semantic Recovery
       ↓
Kof AST
       ↓
Kof Source
```

## 2. What It Is NOT

The decompiler **is not**:

- a "Java decompiler" that reconstructs the original Java source;
- a syntactic reconstruction of the lost source;
- a generator of "Java with Kof syntax".

The goal is **equivalent idiomatic Kof** — behavior and structure,
not the original form.

## 3. Priorities

1. Semantic equivalence (the observable behavior must be the same);
2. Readability (the result must be reviewable by humans);
3. Structure (classes, inheritance, interfaces, methods, fields);
4. Types (primitives, references, arrays, recoverable generics);
5. Control flow (branches, loops, switches, exception regions);
6. Calls and dependencies;
7. Exceptions;
8. Annotations and metadata when present.

## 4. Unrecoverable Information

Compiling is losing information. The decompiler must document what it cannot
recover:

- comments;
- local names (except when debug info exists);
- original syntactic structure;
- formatting;
- certain generic information (erasure);
- programmer intent;
- abstractions eliminated during compilation.

> Decompilation is recovery of **behavior and structure** from the
> available information — never of the original source.

## 5. Confidence

Each recovered construct carries a conceptual confidence level:

```text
Recovered exactly
Recovered with metadata
Inferred
Heuristic
Unknown
```

The platform never silently invents information to produce code
that "looks valid".

## 6. Implementation Phases

```text
Phase A  JVM Inspection          (.class/.jar + structural analysis)
Phase B  JVM Bytecode IR         (Class File → Bytecode IR)
Phase C  Control Flow Recovery   (basic blocks, branches, loops, switches, exception regions)
Phase D  Type Recovery           (primitives, references, arrays, generics, inheritance)
Phase E  Kof Decompiler          (generate Kof source)
```

> **State (08/09, `367d6c4`):** Phase D with generics ✅ — the parser reads the
> `Signature` attribute (JVMS 4.7.1/4.7.9.1) at the 3 levels (class, method,
> field) and `Type.fromJvmSignature` recovers `List<String>`,
> `Map<String, Integer>`, arrays, wildcards and type-variables. Fields and
> methods prefer the signature (EXACT) over the descriptor erased by erasure.
>
> **State (08/09, this commit):** Phase C advanced — `do-while` (bottom-
> tested loop) recovered in `kof decompile`. Self/backward back-edge in the
> cond block → `do { body } while (c)` (CONTINUATION direction, no inversion);
> body separate from the test → honest UNKNOWN stub. No more empty-body
> `while` with `return` inside (wrong code). Proof:
> `DecompileTest.bottomTestedLoopRecoversAsDoWhile` (17/17).
>
> **State (08/09, this commit):** Phase C — **shared join** guard
> (continue/&&/||/?:): re-entering an already emitted block that is not the
> open loop's header → refuse (honest stub). No more compilable wrong code.
> `DecompileTest` 20/20 (includes `diamondJoinShapesStayHonestStub` and the
> nested `recoversNestedWhileLoops` that keeps recovering).
>
> **State (09/09, this commit): robustness over REAL code (601 classes).**
> Running the decompiler over the compiled kof-compiler itself exposed 3
> parsing/length bugs that only show up in production bytecode (javac, not the
> test fixtures). Fixes (all with tests):
> 1. **CP tags 16/17 swapped** (`ClassFileParser`): JVMS 4.4 — `MethodType`
>    = tag 16 (u2), `Dynamic` = tag 17 (u2+u2). The parser had it backwards →
>    any class with MethodType/CondY desynchronized the whole constant pool
>    and CRASHED (`NumberFormatException "#378#513"`), killing the file.
>    601→0 crash: before only 1/601 decompiled WITHOUT crash; now 601/601.
> 2. **`length(0xba)=7` wrong** (`BytecodeReader`): invokedynamic is 5 bytes
>    (opcode + u2 + 2 zero, JVMS 4.9.3). With 7, the pc skipped the next
>    instruction (`areturn`) → the concat test passed by ACCIDENT (fallback
>    "end without return"). Fixed the operand capture (len==5&&0xba).
> 3. **`wide` drift** (`skipVariable`): `op == 0x84` was impossible (the op is
>    0xc4; 0x84 is the SUB-opcode) → `wide iinc` (6B) read as 3, shifting EVERY
>    following opcode. Now it reads the sub-opcode (0xc4,0x84 → 6 bytes; others → 4).
> + **Truncation marker**: an instruction that does not fit in the Code → Insn(-1)
>   → default decoder → null → honest stub. The tool NEVER throws on a
>   real `.class` (that was the path that became NumberFormatException/AIOOBE).
>
> Proof: `DecompileTest` 32/32 (+`invokedynamicIsFiveBytes`,
> `truncatedLastInstructionBecomesHonestStub`, `wideIincConsumesSixBytes`);
> measurement over the 601 classes: 601/601 decompile without exception, ~3306 methods,
> 1812 stub (recovery ~45%).

> **Phase E queue measured (09/09):** `blockerSink` in `BytecodeDecoder` (zero
> cost when null, offline use) counts which opcode brings recovery down
> over the 601 classes: `pop` 0x57 (347×), `instanceof` 0xc1 (165×),
> `checkcast` 0xc0 (137×), `new` 0xbb (126×, almost all is `isJdkClass`
> refusing due to R6), `astore_3`/arrays 0x4c (103×), `ifeq` 0x99 (102×).
> ROI attack: pop/instanceof/checkcast are the 3 largest. **Implemented and
> REVERTED the same day (R6 lesson):** emitted as `x instanceof T`/`(x as
> T)`, the bytecode's target class is DOMAIN (e.g.: `DiagnosticCollector`) and
> does not exist in the isolated `.kf` → `kof check` fails "Undefined variable or
> type" — **recovered code that does not compile** (the drift test: decompile →
> check over the 601 classes; 12+ files drifted). Recovery is only
> valid when the type name is already in scope (classes of the SAME recovered
> file) — it requires the DECOMPILER's multi-class passes (section 7: resolve
> imports/uses), not a patch in the decoder. `pop` alone also drifts: the
> heuristic "has parentheses = call" accepts arithmetic `(x + (y))`.
> Correct Phase E queue: first multi-class (type resolves), then
> pop/instanceof/checkcast (blocked by that one, not by themselves).
>
> ⚠️ **blockerSink caveat (09/09):** it counts each give-up of the linear
> **expression** path — but a method only becomes a stub when expression AND
> statements give up; stores/branches (0x3a/0x4c/0x99…) appear in the ranking
> even though they are handled by `emitLinear`. The ranking serves to FIND
> candidates, not to count stubs; the real queue = (a) unresolved domain
> names (multi-class §7) and (b) refused structural shapes (joins, Phase C).
>
> **DRIFT 69→5 (09/09, this commit):** the drift counter (decompile→check
> on the 100% recovered files) measured PRE-EXISTING ones that the new
> opcodes exposed. Cause #1 was semantic and single: `ldc` emitted the CP
> string RAW (`\b`, real newline, `"` → LEX002/LEX004/unexpected '\'). Escaped with
> the concat's canonical escape (`BytecodeConcat.escape`), the drift dropped 69→5.
> The remaining 5: 4× cross-file (class referenced in another file — exactly
> the multi-class passes of §7) + 1× wildcard `? extends` (its own gap).

> **State (09/09, this commit): §7 step 2 — same-package index.**
> `kof decompile <dir>` is now 2 passes (parse once, reuse — no
> re-parse): pass 1 builds `internalName → package` for the WHOLE tree; pass 2
> decompiles with the index in `BytecodeFrame` (per-method, no global static
> — 1-file mode has a null index = byte-identical to the previous). With the index,
> `instanceof`/`checkcast` of a DOMAIN class of the SAME package recover
> (`arg0 instanceof B`, `(arg0 as B)`); outside the index (another package, `Outer$Inner`,
> malformed CP) it remains an honest stub. Cross-package with import = step 3.
>
> Proof: controlled pair B/C (javac → tree → pair compiles, zero drift) +
> `DecompileTest.decompileTreeResolvesSamePackageInstanceofAndCast` and
> `decompileTreeStillStubsOutOfTreeDomainTypes` (refusal preserved) —
> 38/38. Corpus 613 classes: 1674→1638 stubs; textual invariant verified
> on the 613 `.kf` (36 domain instanceof/as emitted in code position,
> 100% with a sibling `.kf` in the same dir — zero drift by construction).
> Design decision: index via `BytecodeFrame` (existing context), not a global
> static (it would leak between files/tests in the same JVM) nor a new
> parameter in the ~10 decoder signatures.

> **State (09/09, this commit): §7 step 3 — cross-package imports.**
> `TreeScope` per file (index + current package + used imports; frames
> share — no global, 1-file mode intact with identical bytes,
> proven: single-file stubs 1674 = baseline). `instanceof`/`checkcast`
> cross-package resolve with an emitted `import`; `new` in expression-body
> records use; `extends`/`implements` likewise. Sanity rule: simple name
> globally unique (duplicated in 2+ packages → stub, even with a possible
> import — conservative; probe proved that `import` BREAKS THE TIE in the frontend,
> future relaxation documented).
>
> Proof: pair p/B+q/C (extends+instanceof+new) compiles together (zero drift) +
> `decompileTreeEmitsImportsForCrossPackageDomainRefs` and
> `decompileTreeRefusesAmbiguousSimpleNames` — 40/40 DecompileTest. Corpus:
> tree 1636 stubs, 7 files with import; textual invariant 36/36 names
> with a sibling `.kf`. Tree-check 613 files: 4 errors, all pre-existing
> wildcard `? extends` (its own gap) — ZERO SEM011/PKG (and identical without
> the imports: the frontend resolves non-ambiguous simple names module-wide; imports
> are explicit/idiomatic + disambiguators + robustness).
> Design decision: index in `BytecodeFrame` (existing context), collected
> during decode (an import unused by a stub body is harmless — probe:
> only an INEXISTENT import is a PKG006 error); `new` in statement-body remains a stub
> (statements do not handle 0xbb — its own Phase E gap); signatures
> (param/return/field cross-package) = step 4.

> **State (09/09, this commit): §7 step 4 — signature types.**
> Field/ctor-param/param/return cross-package record an import via
> `recordSignatureUses` (walker ClassType+args/Array/Nullable over
> `m.returnType`/`m.parameterTypes` and `fieldTypeTree` — preferred signature,
> descriptor as fallback; name emission UNCHANGED). The hook stays at the top
> of the method loop (the `<init>` `continue` skipped ctor-params — caught in
> review). Proof: pair p/B+q/C (field+ctor+param+return+new) compiles together +
> `decompileTreeEmitsImportsForSignatureTypes` (41/41 DecompileTest). Corpus:
> files-with-import 7→31; tree-check 614 = 4 pre-existing wildcard errors,
> zero SEM011. With steps 1–4, the "name does not resolve" class of drift died
> in the tree (only the `? extends` wildcard remains, its own gap, and slots — outside the
> decompiler).

> **State (09/09, this commit): Phase E — `new` in statement-body.**
> `emitLinear` did not handle 0xbb/0x59/0xb7 (only the linear path): any
> multi-statement body with `new B(...)` became a stub — 152 files with a stub
> contain domain `new`. Exact mirror of the linear path (marker `⟦new⟧`,
> dup-only-post-marker, `<init>` with argc+2 on the stack); JDK refuses (R6);
> cross-package records an import (frame already present). Proof: pair N/M
> (`var n = new N(x)` + field + return, same and cross-package) compiles +
> `recoversNewInStatementBody` (42/42 DecompileTest). Corpus: single
> 1674→1665 and tree 1636→1627 stubs; single drift 13→13 (zero new — baseline
> with the fix in stash, same harness with mirrored package; the 13 are wildcard
> + cross-file refs that the linear path already emitted). Refactor gate ≤500:
> `statementOp` in `BytecodeKofTypes` (Statements 496, KofTypes 182).
> Suite 1193+25+5+122 — ZERO failures. Next in queue: `anewarray` 0xbd
> (69×) and structural joins (Phase C).

> **State (09/09, this commit): Phase E — arrays (`anewarray`/accesses).**
> `anewarray` (0xbd) + `xaload`/`xastore`/`arraylength` ONLY in the statements-path
> (`statementOp`; linear refuses and falls into statements — same output, less
> code, no pressure on the gate). STRICT element (`String`/`Object`/domain;
> `Integer[]` refuses: `new Int[n]` is `int[]`, distinct semantics). Idioms:
> `new T[n]`, `a[i]`, `a[i] = v` (stmt), `a.length` (JVM/script probes).
> Proof: pair A (`new String[n]` + store/load/length) compiles +
> `recoversArrayCreateAndAccess` (43/43 DecompileTest). Corpus: single
> 1674→1658 stubs; drift 13→13 identical (zero new). Suite 1193+25+5+123 —
> ZERO failures. Unit incidents: (1) python without checking the
> start<end order DUPLICATED a region of BytecodeDecoder (698 lines) — reverted via
> `git checkout` (it only had the unit's new code) and the linear path was
> ABANDONED as unnecessary; (2) bug 71 recorded along the way (Kof
> `new Int[2][3]` → VerifyError — compiler lane, not touched);
> `multianewarray` refused (no valid form to recover).


> **State (13/09, owner = 192.168.100.17): Phase E — Java record → Kof `record`.**
> Queue re-measurement with PER-METHOD attribution (the 09/09 caveat confirmed
> in practice: the global blockerSink counted the expression path that the
> statements one recovers later). 216 of the corpus's 688 classes (31%) are
> records and the 3 synthetic bodies (`invokedynamic ObjectMethods` — the body
> does NOT exist in the bytecode) were the largest single source of stubs.
> Measured on the CLEAN TREE (`mvn -am compile`, 688 classes, `Med2`): baseline
> without recovery = **1856** stubs → pure records + EXACT type-params = **1660**
> (−196) → resolved `implements` = **1475** (−185). Total **−381 = 127 records × 3**
> (216 records; **89** still in skeleton). Each stage is zero-drift by
> construction (deviation → null → today's skeleton).
>
> - **Stage 1** (`BytecodeRecords.pureRecordComponents`): `Record` attribute
>   (parser already exposes it in `ir.attributes` — zero change in the shared
>   parser) + super = `java/lang/Record` + EXACT shape by bytecode
>   (ctor `aload_0;invokespecial;N×(aload_0;load;putfield f_i);return`,
>   equals/hashCode/toString = `invokedynamic` skeleton, accessor =
>   `aload_0;getfield f_i;ret` with descriptor matching the field), no extra method,
>   no reserved name (`val` → PARSE015).
> - **Type-params** (JVMS 4.7.9.1, form `Ident:Lclass;`): `record Gp<T>`
>   emits EXACT `<T>`; generic bound/interface-bound → REFUSES (skeleton).
> - **Stage 2** (`pureRecord(ir, scope)`): R7 probe — the Kof frontend wants
>   `record Name implements I(...)` (implements BEFORE the components; Java order
>   gives PARSE007). Interface = TOP class of the SAME package (resolves without
>   import) OR via `TreeScope.resolve` (cross-package emits `import`);
>   `Outer$Inner`/JDK/out-of-tree/ambiguous/scope-null (1-file) → skeleton.
>
> Proof: `DecompileTest` 55/55 (pure/generic/same-package/
> cross-package+import round-trips compile; extra-method/reserved/out-of-tree-interface
> → honest skeleton). Drift-check of the whole tree (`DriftCheck`:
> `decompileTree` 688 → `compileSources` batch): **stage-2 = 4 errors =
> baseline = 4 errors**, all pre-existing `? extends` wildcard in
> `ClassDeclarationNode` (non-record) — **zero new drift**, 62 files
> changed from skeleton to record. Records remaining (at the time): the **89** in
> skeleton — categorized in stage 3 below (out-of-tree interface
> for 1-file, `Outer$Inner`, class-signature with generic bound, static
> members). Phase E queue
> re-measured (Med2 post-unit): top of the remaining 1475 = store+branch
> structural (`astore` 0x4c/0x4d/0x4e/0x3a, loop joins = Phase C);
> instanceof/checkcast only handle when the tree resolves the name
> (statements-path without scope = still fall — the 09/09 hole).
>
> **Phase C — the REAL measured bottleneck (13/09, `StoreCat`, owner = 192.168.100.17):**
> categorize the drop PER METHOD (not last-opcode): the `struct()` of
> `BytecodeStatements` (line 206) REFUSES re-entrancy of a structural join →
> silent stub (no opcode recorded). This is **2452 methods** (the `ffffffff`/other
> cause of StoreCat) — by far the LARGEST bottleneck of the corpus, and it is
> 100% deliberate ("structured join recovery is future work"). The
> instanceof/STORE/branch counts (202+267+~240) are SYMPTOMS of the same
> join: the post-if block does not re-enter. **Attack plan (narrow sub-case
> first):** `if (cond) { then }` WITHOUT else with a join at the end (e.g.
> `CompilerTypeSupport.fieldOk`, `KofUi.themeColor`) — `struct` today handles
> if-then-else (line 271) but not pure if-then with fall-through to the post-block.
> Handle ONLY re-entrancy that is a join of a NON-loop if (header still refuses),
> emit `if (cond) { then }` and continue at `b.succ.get(0)`. **MANDATORY
> proof before commit (R6/Q0):** EXECUTING golden — the harness already
> exists and IS the model: `DecompileTest` lines 747-791 ("STRONG: compiling is not enough — it
> executes the 3 paths") does `java -cp <out> S` and compares stdout;
> replicate for the join sub-case (if-then without else: taken-branch and
> skipped-branch must give the SAME output in the .kf as in the original
> .class, on JVM; cross-target JS/Native/Script = diagnosed gap if it diverges).
> A wrongly recovered join is compilable but semantically WRONG = R6
> (worst possible bug); NEVER relax `struct` without the execution golden.
>
> **EXACT diagnosis of the sub-case (13/09, owner = 192.168.100.17 — ready for
> the next session to execute):** minimal fixture reproduced
> (`/tmp/opencode/join/J.java`, `g(int x)`, measured JVM oracle: g(6)=107,
> g(1)=101): `r = 100; if (x > 5) { r = r + x }` WITHOUT else + join that
> continues. TODAY's decompiler stubs `g` and already handles `h` (then ends in
> `return`) as an if-else-expression via the linear path. **CFG trace (why it
> stubs):** leaders {0,8,12}; B0[0,8) `if_icmple 12` → succ=[12,8]; B1[8,12)
> then-body → single succ=[12]; B2[12,…) join. In `struct`(B0): line 271
> opens `if`, calls `struct(B1)`; B1 has single succ 12 → line 280 **walks**
> to B2 and marks 12 as emitted (the join body falls INSIDE the `if`); returns and
> line 275 `struct(B2)` re-enters 12 → line 206 REFUSES (non-header join)
> → null → stub. **The fix is NOT 3 lines:** it requires a stop *boundary*
> — the then of an if-without-else must STOP at the join (not walk to it) and the join
> must be emitted ONCE as a sequel of the `if`. Path: parameter `Set<Integer>
> stop` (or `joinStop`) in `struct`/`emitLinear`-walker: when the next
> block is the `exitStart` of the current if-chain and has no other pred,
> emit `if (cond) { <then> }` (WITHOUT else) and continue `struct(exitStart)` once.
> Detect if-without-else: `then.succ==[exitStart]` && `exitStart` preds
> ⊆ {b, then}. **DO NOT touch:** guard with return/goto in then (linear path already
> handles), skip with extra pred (break/continue/&&/|| — keep refusal line
> 206), loop header (intact). File: only `BytecodeStatements.struct`
> (+ then's emitLinear walk) + `DecompileTest`. **MANDATORY Golden in the
> SAME commit (not just compile):** pattern `DecompileTest:747-791`
> (`java -cp <out> J` stdout == JVM oracle in BOTH paths — g(6)=107,
> g(1)=101) + drift-check of the whole tree (4=4) + 4-module suite; without the
> execution golden passing, do NOT commit (R6: wrong join = compilable
> but semantically wrong = worst bug). Budget: thread `stop` through all
> the struct recursions — an entire dedicated session, does not fit at the end of this one.
>
> **STEP 1 EXECUTED — pure if-then without else (13/09, owner = 192.168.100.17):**
> the diagnosis path worked, in a dedicated session: `stops` (Set) in
> `struct`, edge ONLY for `pureIfThen` (non-loop join, exact preds {if,then},
> then with single succ == join), emitting `if (cond) { then }` WITHOUT else and the
> sequel ONCE. `recoversIfThenJoinAndRunsIt` executes the decompiled .kf
> (oracle g(6)=107/g(1)=101; TDD: RED on the stub, GREEN with the fix). **Two traps
> measured BEFORE the commit:**
> 1. **Nesting becomes COMPILABLE WRONG CODE** if the edge also descends
>    into the else arm (variant with `withStop(exitStart)` in then+else): in
>    `if(a){if(b){..} seq1}else{seq2} seq3`, `seq3` was sucked INTO the
>    else. Reverted; the nesting stays an honest STUB and
>    `nestedIfWithoutElseStaysHonestStub` locks the refusal (R6: refusing > erring).
> 2. **`emitLinear` returns PARTIAL on branch** (case 0x99-0xa7/0xaa-0xb1/0xbf
>    → return): the prologue fallback (fused init+test block, where
>    `blockCondition` refuses) got a flow guard — prefix with a branch →
>    stub. That was what truly unblocked `g` (javac fuses `int r=100;` into the
>    test block; without the fallback the pure join catches NOTHING in the corpus).
> Measured (A/B stash, same tree 690 classes): stubs 1390 → **1387** (−3; the
> PURE fused-simple if-without-else is rare — `fieldOk`/`themeColor` have non-compatible
> shapes). DriftCheck = baseline 4; 4-module suite 1691/0/5-skip.
> **Phase C remainder:** nesting/loop/else joins require a real
> post-dominator (walker with single join emission + sequel outside the `if-else` —
> trap 1 shows that a naive edge corrupts; it is not a simple stop).
>
> **STEP 2a EXECUTED — LINEAR if-else with sequel + latent bug of
> `blockCondition` unblocked (13/09, owner = 192.168.100.17):** measured ROI
> (`Orient`): 1138 candidates `then.succ==else.succ==[P]`, `preds(P) =
> {then,else}` exact, P non-loop — the stop edge enters BOTH arms
> (shared copy; the join's owner emits it in the sequel) and trap 1 is
> impossible *by construction* here (preds(P) does not contain the if → P is not a
> branch target). `pureIfElse` at the same site as `pureIfThen`. **But descending
> this far revealed a serious LATENT BUG** (exactly the service Q4 asks for):
> `blockCondition` collected "up to 2 loads" from the test block WITHOUT requiring
> arity — in `if (i % 2 == 0) continue` the body `[iload i, iconst 2, irem,
> ifne]` became `if (i == 0)` (the `irem` ignored!) = COMPILABLE WRONG
> CODE. Before step 2a the method stubbed before reaching there and the
> bug was muffled; I proved it by EXECUTION (decompiled `0 0 1 3 6 10
> 15 21 28` vs oracle `0 0 1 1 4 4 9 9 16` — the historical guard
> `diamondJoinShapesStayHonestStub` caught it, and it is LAW). Root fix:
> exact arity (all insns of the test block must be pure loads →
> otherwise `null` = honest stub). The variant `i == 3` (without computation) now
> recovers as `if (v == 3) { } else { body }` — execution golden
> `recoversContinueAsEmptyThenJoinAndRunsIt` (contFor 0..6 = 0 0 1 3 3 7
> 12, increment in the join preserved in both paths). Stubs in the tree:
> 1387 → 1409 — the INCREASE is QUALITY: the "recoveries" that would be wrong
> code became an honest stub (Q5/R6); stub count is not a metonym for
> conformance when the alternant was wrong code. DriftCheck =
> baseline 4; DecompileTest 62/62.
>
> **STEP 2b EXECUTED — `ifnull`/`ifnonnull` narrowing (0xc6/0xc7) (13/09,
> owner = 192.168.100.17):** measured ROI (`NullTest`): **308** null tests over a
> PURE load (226 ifnull + 82 ifnonnull) stubbed only because `invCond`
> (`BytecodeCp`) mapped only 0x99-0xa4 — `blockCondition` returned null →
> stub. It is the language's **canonical idiom** (§Null safety: `if (x != null)`).
> Minimal fix: +2 lines in `invCond` (0xc6→`!= null`, 0xc7→`== null`;
> arity 1 falls naturally into step 2a's exact-arity `blockCondition`).
> `len()` becomes a ternary if-expression (the linear path already existed, only the
> mapping was missing); `nul()` becomes if-without-else (step 1). A/B same tree 692:
> 1412→**1402** (−10; the other ~298 have JOIN/computation in the body = bottleneck
> UNRELATED to narrowing). DriftCheck baseline (the `recoverExpression` —
> ternaries/elif — uses the SAME `invCond` and did not drift); golden
> `recoversNullNarrowAndRunsIt` (oracle 3/0/5/9, 4 paths). **Anti-facade
> (Q7):** I added AND REMOVED 0xc6/0xc7 from `contCond` (do-while) — the do-while
> dispatch only routes 0x99-0xa4, the mapping would be dead code
> (`do{}while(x==null)` would keep stubbing honestly). Recorded so as not to redo it.
>
> **STEP 3a ATTEMPTED AND REJECTED — expression fallback in `blockCondition`
> (13/09, owner = 192.168.100.17, negative hypothesis recorded):** measured ROI
> (`Arity1`/`Why0` on the REAL machine `linearReturn`): of the ~1990
> if-without-else candidates with a multi-insn prefix, 816 "evaluate to 1
> value" WITHOUT structure guards. I implemented the exposed machine (`machineRun` + flag `stopped`,
> byte-identical to `linearReturn` in the round-trip: DriftCheck = baseline 4) and the
> fallback with guards (no ret/throw in the prefix, exact final stack =
> arity, types I/L). **`diamondJoinShapesStayHonestStub` broke (63/1):**
> the fixture has a loop whose TEST has computation fused into the header by javac —
> recovering the cond without recovering the `for` increment emits a `while` that
> LOSES the increment = compilable wrong code, the exact trap 3. Step
> 2a's exact-arity is a **structure** guard, not only of the expression:
> a test with computation in a loop header SIGNALS a desugared `for`, whose body
> requires the post-dominator. Reverted the whole code (tree = `edc4728c`).
> Locked: ANY cond fallback needs a `!isLoopHeader` gate by
> construction, and even then only after the walker with post-dominator (the
> 404+160+228 "default@0xc0/0xc1/0x3a" of `Why0` = stores and checkcast on the
> path — a shape that is never just an expression).
>
> **Gate confirmation (2nd attempt, same session):** I re-applied the fallback
> WITH `if (isLoopHeader(entry)) return null` — and `diamondJoin...` BROKE
> AGAIN. The cond `i % 2 == 0` of the `for+continue` is not in the loop header: it lives
> in a NESTED block whose then/else join at the increment block (which carries
> the back-edge). Recovering it rebuilds the `for` as a `while` with the `i++` sucked
> INTO the else = wrong code (exactly what the diamond law locks).
> **Firm conclusion: no local guard suffices — the only path for a test
> with computation is the walker with post-dominator (real step 3).** The extraction of the
> machine (`machineRun`) is proven byte-identical (DriftCheck baseline 4) and
> is a prerequisite of step 3. **AUDIT 14/09 (lane docs/development, vs CODE,
> not memory): the statement above became STALE — the extraction LANDED in
> the tree at `158c174b` (13/09 19:52, "refactor(decompiler): extrai maquina
> de expressao do linearReturn"), AFTER this session's revert was written.
> Today `BytecodeDecoder.machineRun` is live and IS the body of
> `linearReturn` (BytecodeDecoder.java:73/97). Prerequisite of step 3:
> present and proven (the byte-identical DriftCheck=baseline-4 holds —
> linearReturn goes through it on every call).
>
> **Stage 3 (13/09, owner = 192.168.100.17): internal of the SAME package.**
> Reflective categorization of the 89 rejected (harness `RecCat`): **31** were
> only `implements Outer$Inner` of the same package (cluster `JsIr$*` with 43
> internals, `SymbolTable$*`); the rest = 52 shape-fail (real extra method),
> 4 static-field, 2 reserved. 13/09 probes in the frontend: SEM042 forbids a
> NESTED type, but a top name with `$` compiles — `record X$Y implements X$Z(...)`
> ✓, `record X(...) { extra() }` ✓, `static` in record ✓, compact
> constructor ✗ (PARSE018). And the decompiler already emits each internal as a TOP
> class of a sibling file (688/688 unique simple names, measured). `pureRecord`
> now accepts an internal when the package matches AND the internal is in the index
> (`TreeScope.inIndex` — partial tree without a sibling = honest skeleton);
> CROSS-package internal → skeleton (import `p.Outer$I` without probe). Corpus:
> 1475→**1382** stubs (−93 = 31 × 3 synthetic; **158** of 216 records
> recovered). Drift-check: current 4 errors = baseline 4 (zero drift — only
> `ClassDeclarationNode.kf x4` pre-existing wildcard). Proof: +2 tests
> (57/57 `DecompileTest`) — same-package internal round-trip compiles
> (marker interface; abstract interface requires an extra method = another gap),
> cross-package internal → honest skeleton. 58 records remain in skeleton
> (52 with an extra method — `record X(...) { body }` would be the key, but
> `RecExtra2` measures only 3 with ALL bodies recoverable today; 4 static
> field; 2 reserved) — low cost/benefit; the real Phase E queue now is
> the non-record path (1382 stubs): structural joins + `astore`/
> `istore` multi-stmt (Phase C) with the statements-path already scoped.
>
> **NEGATIVE Experiment (13/09, owner = 192.168.100.17 — documented so as not
> to redo):** accept `$` in the `TreeScope.resolve` regex (enable
> `instanceof`/`as` of an internal in an expression). Probes ✓
> (`x instanceof Box$Expr`/`as Box$Expr` compile), BUT **−9 stubs only**
> (1382→1373; the 238 "resolvable-domain" counted by `Why193` were not the
> real drop — what brings down e.g. `BuiltinTypes.isString` is `astore_1`+`ifeq`
> of a multi-stmt body = Phase C) and **real latent risk**: `arrayElementType`
> would start accepting an internal → `new Type$ClassType[n]` = PARSE041 in the
> frontend (the corpus drift-check did not catch it: no corpus file
> triggers anewarray-of-internal; another corpus does). Negative ROI + cross-corpus
> danger → REVERTED (clean working tree, HEAD = stage-3).

>
> **State (14/09, this commit, owner = 192.168.100.17 — lane docs/development
> EXCLUSIVE by maintainer's directive): Phase C STEP 3 prerequisite landed.**
> `PostDominator.java` — pure immediate-post-dominator pass (bit-set
> Cooper–Harvey–Kennedy dual: `pdom(b) = {b} ∪ ⋂ pdom(succ)`, terminal →
> `{b, EXIT}`, monotone intersection so it converges without an iteration
> order; deterministic — principle D-ENGINEERING: the standard compiler
> formulation, not reinvented). This is the locked prerequisite from the two
> STEP-3a rejections (13/09): a test-with-computation can only be recovered by
> the walker that consumes post-dominators, never by a local guard. Proof:
> `DecompilePostDominatorTest` 5/5 with hand-computed path-to-EXIT oracles
> (linear chain, if-then-else join, while-loop back-edge, nested if, fork-
> without-join). **No recovery output changed yet** — 63 `DecompileTest`
> untouched (re-run fresh 115.6s, green; the 87.43s number was a stale
> surefire report, caught and corrected — honesty over false green), corpus
> stub count unchanged by construction (the pass is not wired in).
> **NEXT in this doc (unit 2):** consume `immediatePostDom` in
> `BytecodeStatements.struct()` to recover the test-with-computation shapes
> that step 3a rejected (the `for+continue` whose cond lives in a nested
> block joining at the increment) — each recovery must keep
> `diamondJoinShapesStayHonestStub` green (the diamond law is binding).

>
> **State (14/09 ~16:25, this commit, owner = 192.168.100.17): ROI of the
> step-3 walker MEASURED (harness `/tmp/opencode/roi/dev/kof/cli/Roi.java`,
> throwaway in the package `dev.kof.cli` like Orient/Why0/StoreCat — practice
> of the lane: measure before writing).** Corpus REAL today
> (`kof-compiler/target/classes`): 699 classes (0 parse failures), 3899 methods,
> **2628 stubbed** (67%). Of these, **1098** have at least one shape
> "succ==2 block with `blockCondition==null` and computation in the test block"
> (test-block size 1..20 insns before the cond — fused init/store/irem of a
> `for`/`while`) — the upper bound of what the walker with `immediatePostDom`
> unlocks (many will still resist the law of the diamond; the real yield is
> reached slice by slice). ROI ≫ 30 → **decision: build the walker**. *(→
> superseded by the RE-MEASUREMENT below, 14/09 ~18:20: the proxy over-counted,
> the walker as scoped has no net-new target — read it before building.)*
> Unit 2 scope (locked by the measurement): consume `immediatePostDom` in the
> `cond == null` branch of `struct()` — recover test-with-computation ONLY
> when the join P = idom(then) = idom(else-path), P is NOT loop header and the
> back-edges of the arms do not cross P (the construction that makes trap 1
> impossible — criterion of step 2a extended to non-pure test); each slice
> keeps `diamondJoinShapesStayHonestStub` VERDE (binding law) and adds
> runtime golden (JVM oracle).

> unit. **CORRECTNESS PROOF added (unit 2a): `pathOracle` brute-force over the
> DEFINITION (X pdom b ⟺ every simple path b→terminal passes X) vs the
> fast bit-set pass on REAL corpus CFGs (300 classes of `kof-compiler/target
> classes`, blocks ≤40) — 6/6 green, 4.28s, zero divergence block by block.
> The walker can now consume `immediatePostDom` with the pass trusted.

> **RE-MEASURED (14/09 ~18:20, owner = 192.168.100.17): the "1098" above was a
> PROXY that over-counted — the walker has NO net-new target (harness
> `/tmp/opencode/w2b/dev/kof/cli/Roi2.java` + `Roi3.java`, throwaway
> package-private like Roi.java; the classifier is by the REAL cause of the
> stub, not the blockCondition shape).** The 1098 counted every
> "succ==2 block with `blockCondition==null`" without checking whether the
> method still decompiles via the prologue path added in unit 2a (`5c944709`:
> a fused `int x=…; if (x%3==0){}else{}` non-loop is ALREADY *shape*-recovered
> today — measured: `computed`/`cmp` emit `if (v1 == 0) { … } else { … }`
> BUT the emitted output is NOT COMPILABLE: the local's `var` is hoisted to its
> FIRST assignment which is INSIDE the then-branch, then read after the join →
> `SEM000 Undefined variable` (measured 19:50 via Runner2 on the recompiled
> HEAD — pre-existing 2a defect, catalogued §238, NOT a walker target). So 2a
> "recovers" only when the local is initialised BEFORE the if (the passing
> `E.java` test has `int r=1` pre-if). Splitting
> the 2642 stubs by the cause that actually makes `recoverStatements` return
> null, among those WITH a computed 2-succ test:
>
> | cause of the stub (measured, corpus 699 classes / 3899 methods / 2642 stubs) | count | whose lane |
> |---|---|---|
> | test has an `invoke`/`getfield`/`new` in its computation (`.equals`, `.size`, `String.join`…) | **646** | interop descriptor (§234/§224/§225) — **compiler lane**, NOT a CFG problem |
> | the computed test sits in a LOOP header (a `continue`/back-edge diamond) | **453** | **blocked by the binding diamond law** + Kof has no `continue` → rule 6 (contract), NOT an edit |
> | non-loop, prefix pure load/const/arith but an opcode `loadValue` misses (sipush/lcmp/ldc_w) | 8 (+2 store) | opcode-coverage gap, not the walker |
>
> **Verdict: the unit-2b walker as scoped (consume `immediatePostDom` to
> recover a non-pure test) is DISCARDED — measurement shows the non-loop
> fused case is already covered by 2a and the remaining computed-test stubs are
> either interop (646, §234) or the diamond-law/`continue` collision (453, rule
> 6).** The `PostDominator` + `pathOracle` (units 1/2a) stay as the trusted
> foundation (green, 6/6, no regression); they are simply not wired because
> there is nothing left in their scope that the law permits and 2a does not
> already do. Honest re-scope, NOT a silent drop: the 453 loop-diamond + 646
> interop faces are recorded here as the real (deferred/other-lane) work so the
> next agent does not re-pay the ROI archaeology.
>
> **NEXT STEP for this doc:** the decompiler's recoverable surface is
> effectively at its honest ceiling for structured shapes. The open decompiler
> work is now (a) the `continue`/`break`-in-`for` recovery IF the maintainer
> lifts the diamond law (rule 6 — needs a language `continue`, a contract
> decision, NOT this lane) and (b) opcode-coverage nits (sipush/lcmp in
> `loadValue`) which are a compiler-lane micro-fix, not a structural walker.
> Neither is autonomous-mode work in `docs/development/`. **UPDATE (14/09
> ~20:55, same session):** the 5th-red hunt that produced this re-measurement
> ALSO exposed a real in-lane defect — §236 (the ambiguous Bool-vs-Int
> comparison fold) FOUND & FIXED with a new recompile test (`comparisonReturn-
> RespectsBoolVsIntReturnType`, DecompileTest 64/64 green), and §238 (the 2a
> `pureIfElse` join emitting `var` inside the branch → non-compilable output)
> catalogued with a measured repro — §238 became the next autonomous unit (2c).
> **UPDATE 2 (14/09 ~21:35): §238 ✅ FIXED (unidade 2c)** — classe NOVA
> `StructWalker.hoistEscapingLocals` içar `var` default-init (0/0L/0.0 por
> opcode da store; fstore/astore → recusar p/ stub honesto) antes do `if`
> nos caminhos `pureIfElse`; 2 testes novos que FALHAM no código antigo
> (Q0-prova 2/2 red revertido) + executam com oracle JVM medido
> (`10/21/12`); DecompileTest 66/66, PostDom 6/6, kof-cli COMPLETO 251/251
> BUILD SUCCESS, lei do diamante VERDE, check_500 OK (537→547 TOLERADA,
> StructWalker 84 linhas). Remaining in the structural lane: the diamond law +
> `continue` face (453, rule 6 — maintainer decision) and the interop tests
> (646, §234 lane compiler) — the doc returns to a genuine stopping point.
> **UPDATE 3 (14/09 ~22:05, same unit): sipush face CLOSED** — `loadValue`
> mirrors `machineRun` (0x11 to short), safe only AFTER the §238 hoist
> (before it, a sipush-only fix turned stubs into non-compilable output —
> the 18:20 re-measure marked §238 as the prerequisite); `if (a == 30000)`
> now recovers COMPILABLE + runnable (`sipushConstantInTestIsRecoveredAnd
> Runs`, JVM oracle `1|2|2`); DecompileTest 67/67. Closing a PAST
> divergence, not a new shape. The structural lane is now exhausted.

## 7. Relationship with the Compiler

The decompiler feeds the existing pipeline:

```text
Legacy Semantic IR
        ↓
    Kof AST
        ↓
Kof Compiler (existing frontend)
        ↓
    Kof IR
        ↓
 JVM / Native
```

It does not duplicate the Kof frontend. The entry point is the **Kof AST**.
