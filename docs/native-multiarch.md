[English](native-multiarch.md) | [Português](native-multiarch.pt_BR.md)

# Kof Native — Multi-Arch (RISC-V 64 and ARM64/AArch64)

> **✅ PROMOTED to `docs/` 19/09 (§5 step-8; maintainer order "assume
> native-multiarch e termina") — NATIVE002 CLOSED.** The five implementation
> faces are all closed: (1) cross GC mark-sweep G-0..G-6(a) DONE 15/09
> (x86 auto-collect G-6(a) 19/09; the worker-stack-scan face is catalogued,
> main-only contract); (2) DB001 + CONC001 cross CLOSED 15/09; (3) FP
> cross (FLT001, slice `RtB45`) + §107 record/nested CLOSED 19/09 ON ALL 3
> ARCHES (x86 `.rodata` recursive descriptor face (3) + cross port face (4):
> slice `B39` interprets the SAME descriptor grammar — 8=vtable toString via
> `jalr`, 9/10=nested containers, null→"null"; `NativeRiscv64E2ETest`/
> `NativeAarch64E2ETest` 46/46 each under qemu, golden = the measured JVM
> oracle; aarch64 inherits via the translator with the new `lhu`→`ldrh`);
> (4) per-arch columns CLOSED 19/09 — section "Native per-arch" in
> `docs/backend-parity.md`(+PT), 16 domains × 3 arches; (5) cross CI CLOSED
> 12/09 — job `cross-native` runs both E2E suites under qemu. **The remaining
> per-domain refusals are HONEST gap codes, not pending work of this doc:**
> SECN000 (crypto asm — R11 non-goal), JSN004 (`json.decode<Record>` in pure asm),
> RNG001 (cross rng),
> `kof.ui` (no cross port) — each diagnosed at compile time, never silent
> (R6), tracked in `docs/bugs-and-gaps/known-bugs.md` + the per-arch section
> of `docs/backend-parity.md`. The build-out history below is kept verbatim
> as the implementation record.

> **🔄 RE-AUDIT 12/09 (measured under REAL qemu on this host — NOT memory):**
> the 03/09 headers below are OUTDATED and this block is the source of the
> REAL state (AGENTS rule "audit doc against the code/tests, not against
> memory"; state-4 "doc contradicts the code" corrected). The 13 "core"
> faces of 03/09 are today ~30 cross tests (`NativeRiscv64E2ETest` 39/39 +
> `NativeAarch64E2ETest` 39/39, **executed**, 0 skip on this host). **CLOSED
> and RUNNING under qemu (byte-identical to the measured JVM), beyond the "core":**
> Map/Set, `println(<collection>)` (§107 — x86 `f3b3821c` + cross B39 12/09),
> higher-order `map/filter/reduce` (probe 12/09: `[2,4,6]`/`[2,3]` identical
> JVM), HTTP client (`riscv64HttpGetPostStatus`), `kof.net`, JSON
> encode/decode int/list/string, spawn/await (`clone`+`futex`), `time` ISO
> (add/diff), `math` Double (MATH001) + **`math.pow` S1b.2 (decision 7a 13/09:
> x86 via libm `pow@PLT` + `-lm`; riscv/aarch refuse with MATH001 — static
> cross link without libc, architecture decision rule 6; `KofMath.supportedOn`),
> `random`, `uuid`, multi-dim array
> (§113), UTF-16 String search (§43/§102/§111). **OPEN — HONEST refusal at
> compile-time (NEVER silent binary; rule R6 — the "exit 0 with no effect" stub
> of 03/09 no longer describes the state, unknown ops give a gap code):**
> `kof.db` → **DB001 CLOSED 15/09 on riscv64/aarch64** (link-by-use `libsqlite3` +
> runtime `kof_db_*` in slices `RtB46/RtB47`; `KofDbE2ETest.crossNativeSqliteRoundtrip`
> proves the full path under qemu on both arches; JS keeps `DB001`), `kof.security` crypto-heavy → **SECN000**, the
> higher-order concurrency `supervisor` → **CLOSED 19/09** (§129 cross port:
> EH chain is per-TID via `kof_exc_slots`, `OTP001` removed; `selectAny`/`done`/
> `poll`/`cancel`/`cancelled`/`awaitTimeout` themselves **CLOSED 15/09**: slice
> `RtB48` + spawn trampoline registering the cancel slot, TID real via
> gettid(178) recorded by the kernel on the clone ctid,
> `KofConcurrency2Test.crossNative*` under qemu both arches), UI (`kof.ui`) **without any
> cross port** (no riscv/aarch test), `json.decode<List<Record>>` → **JSN004**
> (pure asm has no reflection to materialize a record). **Honest consequence
> TODAY:** a program with collection/HTTP/net/scalar-JSON/spawn/time/math **truly
> runs** on riscv/aarch (the 03/09 phrase "does not execute the logic — exits 0 with no
> effect" is SUPERSEDED); what still does not run (DB/security/UI/record-decode)
> is diagnosed with a gap code at compile time, not silently.
> **Real remaining gap `NATIVE002`:** (1) cross GC mark-sweep (riscv is
> bump-pointer without collector — leak on long heap, non-crash) —
> **G-0 header-block + G-1 free-list/memstats + G-2 gc-list/dump + G-3
> conservative mark + G-4 sweep/collect DONE 15/09** (see the decomposition
> below); G-5 aarch64 satisfied by G-4 (both arches run the sweep proof);
> the collector (G-4) is what actually reclaims; (2) the
> DB001 cross face CLOSED 15/09 (SQLite; JS keeps DB001) and the CONC001
> helpers (selectAny/done/poll/cancel/cancelled/awaitTimeout) CLOSED 15/09
> and `channel<T>` send/receive CLOSED 23/09 (slice `RtB61`, §423) — remaining refusals: SECN000/JSN004,
> `kof.io` File/Path/Directory → **NAT006** and web-T1 `listen`/`route` →
> **NAT007** (§427, 21/09; honest compile-time refusal — the x86_64 runtimes
> were never ported to the cross) (OTP001 removed 19/09); (3) FP-collection on cross
> (FLT001 CLOSED 15/09 — slice `RtB45`; §107 record/nested **CLOSED 19/09 ON
> ALL 3 ARCHES** — x86 `.rodata` recursive descriptor (face (3) x86) + cross
> port (face (4)): slice `B39` now interprets the SAME descriptor grammar
> (0..7 legacy + 8=vtable toString via `jalr`, 9/10=nested containers, null→
> "null"), call-site emits the `.rodata` node (`NativePrintDescriptors`,
> shared with x86), aarch64 inherits via the translator (new `lhu`→`ldrh`);
> `NativeRiscv64E2ETest`/`NativeAarch64E2ETest` 46/46 each under qemu with
> the SAME JVM-oracle golden as x86 (incl. `[Point[x=1, y=2]]`, `{k=Point[x=7,
> y=8]}`, `[[[4]]]`, `"rec:" + Point(5,6)` — the concat face needed a
> `valueOf(record)` vtable branch in `NativeRiscvCrossOps`); (4) `backend-parity.md` per-arch columns
> **CLOSED 19/09** — section "Native per-arch (x86_64 · riscv64 · aarch64)"
> in `docs/backend-parity.md`(+PT); (5) cross CI does not exist (host-dependent toolchain) —
> **face (5) CLOSED 12/09**: job `cross-native` in `.github/workflows/ci.yml`
> installs `binutils-riscv64/aarch64-linux-gnu` + `qemu-user-static` and runs
> `NativeRiscv64E2ETest,NativeAarch64E2ETest` (they execute under qemu, do not skip —
> the job EXISTS to prove; binary names match `NativeArchEmitter:151
> -282`; local: riscv 39/39 + aarch 39/39 green on this host with qemu).
> **➕ Dynamic link ON DEMAND DONE 15/09** (directive "liga dinamicamente"):
> `NativeCrossLink` links libc (`-dynamic-linker … -lc`) only when the pruned
> runtime calls libc; the current 84 cross binaries stay static/portable.
> **➕ First production consumer DONE 15/09**: `RuntimeDtoa` ported to the cross
> runtime as slice `RtB45` (`snprintf`/`strtod`), closing **FLT001**. See §2.3.
> ~~This doc remains in `development/` (NATIVE002 does not close while (1)–(5)
> remain); when (1)–(5) reach zero → move to `docs/`.~~ **SUPERSEDED 19/09:
> (1)–(5) all closed (see the promotion block above) — doc moved to `docs/`.**
>
> **🪜 FACE (1) DECOMPOSITION — cross GC mark-sweep (12/09, queue for
> step-by-step execution — each step fits in a session and has its own proof):**
> riscv is pure bump (`amoadd.d` in `kof_alloc_ptr`, no flags/mark/free-list);
> the port is NOT copying the x86 RuntimeGc — the conservative scan requires a
> riscv stack-walk + roots in the section range. **⚠️ CORRECTED 12/09 (read the code,
> not memory) — the original decomposition was WRONG at G-1:** riscv has NO
> block header: riscv `kof_alloc` (`NativeRiscvAsmRt0.java:17-22`)
> returns the raw bump aligned to 16 and users write the OBJECT header
> (typeId @0, vtable @8, …) at OFFSET 0 of the returned pointer (e.g.:
> `string_from_literal`: `sw t0, 0(s3)`), whereas on x86 the GC lives in a block
> of 32B BEFORE the returned pointer (`RuntimeMemory.java:145-150` — size@0,
> free-next@8, gc-next@16, flags@24; `kof_free:205` reads `-32(%rdi)`). Porting the
> free-list without the header-block = the collector reads the typeId as size →
> corruption. That is why **G-0** comes first. Steps in order:
> **G-0 riscv header-block (DONE 12/09, dev session):** riscv `kof_alloc` reserves
> 32B BEFORE the pointer (total = 32+align16, return base+32; filling
> size/free_next/gc_next/flags) + honest OOM guard (`_kof_heap_end`, panic
> `out of memory` exit 1 — R6: the bump did NOT have a bounds-check and the header
> triples the consumption/block, so overflow became more likely). Proof:
> riscv suite 40/40 + aarch 40/40 under qemu (includes the pressure test
> `riscvHeapExhaustionPanicsHonest`/`aarch64HeapExhaustionPanicsHonest`,
> sabotage-without-guard = zero output FAIL) + x86 GC 3/3 + Artifact 6/6 +
> ratchet ≤500 OK (Rt0 with exactly 500; design prose lives here).
> **G-1 riscv free-list (DONE 15/09, dev session):** port of the x86
> free-block list ON TOP of the G-0 layout (header 32B:
> size/flags/gc-list/free-next). New slice `NativeRiscvAsmRtB42` holds
> `kof_alloc` (MOVED out of `Rt0`, which dropped 500→478 — the ratchet room),
> `kof_free` and `kof_memstats`; the alloc now first-fits the free list (LIFO,
> re-enqueue on alloc, `flags=0`) and only bumps on a miss; an `amoswap.w`
> spin-lock guards the shared free list + counters (main × spawn workers).
> `kof_free` is a 1:1 port of `RuntimeMemory.emitFree` (flags bit1 = in free
> list). `kof_memstats` prints `allocs`/`frees`/`live bytes` — the observation
> lever for the following steps. Proof (qemu riscv64 **and** aarch64, toolchain
> present, tests never skip): `NativeRiscvGcFreeListTest` assembles the
> PRODUCTION runtime (`RiscvSlices.renderRuntime()`) with a raw `_start` that
> alloc(64)→free→alloc(64) and asserts p2==p1 (reuse) + `allocs: 2`/`frees: 1`
> — 2/2 green; `NativeRiscvRuntimeSliceRegistryTest` 8/8 (concat still
> byte-identical); cross suites riscv 44 + aarch 44, only the pre-existing
> `CastSaturation` red; `ArtifactSizeTest` 6/6 (the internal lock/head are
> `.L`-local so the hello `.symtab` does NOT grow — was the one real
> regression found and fixed here); `KofGcE2ETest` 3/3 x86 untouched;
> `check_500` OK.
> ⚠️ **G-1 proof correction (15/09, doc-vs-reality — the plan's proof had no
> reachable path):** the 12/09 proposal said "wire free into the log nodes
> (RtB0, mirroring `RuntimeLog2:98`)" and "alloc/free/alloc reuses the slot".
> Counting in the source refutes BOTH halves: (a) the riscv log node
> (`NativeRiscvAsmRtB0:242`) writes label+msg **directly via `write()` and never
> allocates**, and observability uses fixed `.bss` slots — so there is NO node
> to free; (b) across the compiler **no riscv slice calls `kof_free`** (x86 has
> 4 real callers: `RuntimeChannel:132`, `RuntimeLog2:98`,
> `RuntimeObservability1:426`/`2:247`) and there is **no Kof-level free/GC API**,
> so a Kof E2E cannot exercise it. Conclusion: the riscv free-list is the
> correct x86-parity infrastructure, but on riscv it stayed **latent until G-4**
> (sweep feeds it) — the G-1 proof is therefore the raw asm harness, not a Kof
> program. Wiring free into the 57 existing alloc sites is a **G-4 concern**
> (they must free dead objects, which only the collector can identify), NOT
> G-1. **G-4 (15/09) closed this**: the collector now feeds the free-list and
> the ~260KB `.bss` leak is bounded by recycling (proof in the G-4 block below).
> **G-2 header flags/mark bits + GC list (DONE 15/09, dev session):** every
> block RESERVED from the bump now enters the global gc-list
> (`.Lkof_gc_head`, LIFO, `gc_next`@16, flags=0) inside the same slice
> `NativeRiscvAsmRtB42`; a free+realloc does NOT re-enter (the block never left
> the list). New `kof_gc_dump` prints one `gc <size> <flags>` line per block —
> the plan's "`KOF_GC_DEBUG` dump" (pure asm has no env trigger, so the lever is
> an explicit call; honest and testable). Proof (qemu riscv64 **and** aarch64,
> never skip): `NativeRiscvGcListTest` 4/4 — alloc(16/32/64) ⇒ dump
> `gc 96 0`/`gc 64 0`/`gc 48 0` (total = align16+32, LIFO); free+realloc ⇒ a
> single `gc 96 0`; sabotage (drop the link) = 4/4 red with empty output.
> Slice-registry 8/8 (concat still byte-identical), cross riscv 44 + aarch 44
> (only the pre-existing `CastSaturation` red), `ArtifactSizeTest` 6/6,
> `KofGcE2ETest` 3/3 x86 untouched, `check_500` OK.
> **G-3 conservative mark riscv (DONE 15/09, dev session):** port of
> `kof_gc_mark`/`kof_gc_try_mark`/`kof_gc_mark_transitive` in the new slice
> `NativeRiscvAsmRtB43` over the G-0 32B header and the G-2 gc-list. Stack
> roots = `sp..s11` (riscv fp; 4KB fallback like x86) with `s0-s11` spilled so
> register-held pointers are visible; static roots = the interval between the
> NEW riscv-only local labels `.Lkof_heap_root_start`/`.Lkof_heap_root_end`
> emitted by `NativeArchEmitter` around the program `.data` (sentinel `.quad 0`
> at the opening). **Correction to the plan:** the x86 `kof_heap_root_end`
> (S-5 bugfix queue) was NOT needed — riscv now emits its own `.L`-local
> markers (outside `.symtab`, the G-1 ArtifactSizeTest lesson) and the interval
> DELIBERATELY excludes the bump arena (`_kof_heap`, in `.bss`), unlike x86
> which scans to `_end` because there the heap is mmap'd. So G-3 advanced
> WITHOUT the shared prerequisite; the labels are declared program-side in
> `RiscvSlices.programSideLocals()`. Proof (qemu riscv64 **and** aarch64, never
> skip): `NativeRiscvGcMarkTest` 2/2 — `_start` allocs A(static root)→B(stack)
> →C(unreachable)→D(via field0 of A), calls `kof_gc_mark`, dumps: `gc 96 1`/
> `gc 96 0`/`gc 96 1`/`gc 96 1` (D/A reachable, C not); sabotage (drop the
> transitive field walk) = 2/2 red with D=0. G-1/G-2 harnesses and the
> slice-registry 8/8 still green; `ArtifactSizeTest` 6/6 (labels `.L`-local, no
> symtab bloat); `KofGcE2ETest` 3/3 x86 untouched; `check_500` OK.
> **G-4 sweep + collect DONE 15/09** — the free-list now receives the dead:
> `kof_gc_sweep` (mark==1 → clear bit0; mark==0 & !free → free-list + bit1 +
> counters) + `kof_gc_collect_now` (mark+sweep unconditional) +
> `kof_gc_collect` (tick-guarded, `tick & 4095`, called at `kof_alloc` ENTRY) +
> `kof_gc_tick` (new slice `NativeRiscvAsmRtB44`). The OOM path of `kof_alloc`
> (B42) also runs ONE `kof_gc_collect_now` and re-searches the free-list before
> panicking. **The x86 disables collect-inside-alloc on purpose**
> (`RuntimeMemory:122-131`: the free-block pointer lives in a register and the
> conservative mark misses it → double-use); **riscv is safe** because the
> value-stack IS the machine stack (`pushRiscv`: `addi sp,-8; sd`) and
> `kof_gc_mark` spills `s0-s11` — every live temporary is on the scanned stack.
> **Proof (qemu riscv64 AND aarch64, no skip):** `NativeRiscvGcSweepTest` 5/5 —
> (1) mark+sweep+dump requires `gc 96 0 / gc 96 2 / gc 96 0 / gc 96 0` +
> `frees: 1` (C recovered, D/A/B survive); (2) a 10000-alloc loop (arena
> 256KB ≈ 2730 blocks of 96B) COMPLETES because the collector recycles the dead
> (frees > 0); (3) **sabotage** (strip the collector hooks) = the same loop
> panics `out of memory` (non-vacuity). `ArtifactSizeTest` baseline updated
> 18→24 hello syms (the collector chain is now reachable from `kof_alloc` —
> the price of closing the leak); cross 44+44, G-1/G-2/G-3, registry 8/8,
> `KofGcE2ETest` 3/3 x86 untouched; `check_500` OK.
> **G-5 aarch64** — inherits everything via translator (the riscv directives/labels pass
> unscathed — same path as the S-4 prune; `amoadd.d`→`ldadd`, `amoswap.w`→`swpal` already
> translated, `NativeAarch64Translator.java:299`); gate: aarch suite under qemu +
> the G-4 leak test also on aarch. **G-4 ALREADY proved the inheritance for the
> sweep+collect** (`NativeRiscvGcSweepTest` runs both arches), so G-5 is
> effectively satisfied for the collector too; a dedicated G-5 aarch step is no
> longer a separate face.
> 
> **G-6 x86 (CLOSED 19/09 — frente 2 D-DEV-PRIORITY, §260; gatilho LIGADO — option (A) do §260, mantenedora autorizou e ordenou "assume native-multiarch e termina"; causas (1)+(2) CLOSED 16/09 por G-6b/parser):** the x86 collector
> exists and is correct (`kof_gc_mark`+`kof_gc_sweep`+`kof_gc_collect_now`), but
> the auto-collect **trigger** inside `kof_alloc` was measured UNSOUND for the
> x86 calling convention: the backend keeps temporaries in **caller-saved
> registers** at call sites (proved: `KofStringParseTest` red /
> `KofSupervisorE2ETest` exit 139 SIGSEGV with the trigger, green without; full
> measurements in `known-bugs.md §260`). riscv needed no stack map because its
> value-stack IS the machine stack (RtB44:15-20); x86 was BELIEVED to require
> the real "root map per frame" of the D-DEV-PRIORITY text. Two honest options
> (scope: the compiler lane):
> **19/09 correction — the root-map premise did NOT survive the §260 audit:**
> the reds were the ad-hoc trigger block (it clobbered r10-r15 across the
> collect+restart and the spawn-path register leak) plus a latent parser bug
> (r8/r9 leak, `e667791f`), not the calling convention per se. The shipped
> landing preserves every caller register at the collect depth, so no
> per-site spill and no stack-map table were needed. See the LANDED note
> below (acceptance matrix 1-5 all measured green).
> **G-6b (the root-cause-(1) half) DONE 16/09, trigger still OFF:** gdb-proved
> that with only the frame-current scan, live Strings in EXTERNAL frames (main's
> stack while a helper allocates) were invisible → sweep freed live memory
> (keep corruption, supervisor SIGSEGV 139). Fixed without the trigger: `_start`
> records `kof_main_stack_bottom` (entry rsp) and `kof_gc_mark` scans the whole
> thread stack to it (64MB cap + sp..sp+4096 fallback preserved for asm harness
> without `_start`); guard `NativeX86GcMarkScopeTest` 3/3 (Q0 red on the old
> mark). keep/supervisor went green even WITH the trigger; the remaining red is
> exactly cause (2) (`toFloat` path holds the live arg String in a caller-saved
> register at the `kof_alloc` call-site) → the trigger stays OFF (FP parity is
> freeze rule 5) until (a) lands.
>
> **LANDED 19/09 — shipped design (NOT (a) spill-per-live-ref, and (b) never
> needed):** the §260 audit proved cause-(2) was an ARTIFACT of the 16/09
> ad-hoc trigger block (incomplete register preservation around
> `collect_now`+search restart) plus a latent parser bug (r8/r9 leak, fixed
> `e667791f`). The landing therefore does the sound minimal thing at the
> COLLECTOR side, no per-site backend changes: `.Lkof_alloc_maybe_gc`
> (RuntimeMemory) fires `call kof_gc_collect_now` exactly ONCE per program
> (flag slot `8(%rsp)`), gated on `kof_spawn_count==0`, and restarts the
> free-list search from a NULL cursor (the "temporario solto" hazard that
> killed the 16/09 attempt is structurally gone); `kof_gc_collect_now`
> (RuntimeGc) blanket-spills ALL 15 GPRs around mark+sweep, so every
> call-site live reference IS on the stack the conservative mark scans
> (false-positive ints only over-retain — try_mark validates alignment +
> heap range + gc-list). `incq kof_spawn_count` moved to the ENTRY of
> `kof_spawn_handle_new` (it sat after `pthread_create`, leaving a window
> where a worker's first alloc could fire the trigger while only the
> un-scanned worker stack held its live result-box — measured:
> `spawnWorkerThrowIsolated*` lost `s1=42`→`0` before the hoist). Counter is
> CUMULATIVE (no decq): after any spawn begins, auto-collect stays off
> forever and the program behaves EXACTLY as before (mmap growth) — the
> worker-stack scan is the catalogued NEXT face, never silent.
> Acceptance matrix — all 5 measured green: (1) `KofGcE2ETest.gcAutoCollect
> FitsUnderMemoryCap` green (200k×`"s"+i` under `ulimit -v 256M`: exit 0 —
> §260(1) as an IN-REPO guard, was a one-off script); (2) the two §260 repros
> green (`KofStringParseTest` 6/8+2skip clean, `KofSupervisorE2ETest` 16/16 —
> no 139); (3) full suite **2348/0F/0E + 183 skips** — CORRECTED 19/09: the recorded
> "incl. cross under qemu" was WRONG (this host has no cross binutils; the
> ~183 cross tests SKIP here; CI `cross-native` is the referee) — trigger is
> x86-only, cross untouched anyway; (4) `ArtifactSizeTest.helloX86`
> re-baselined 44→**84 syms WITH CAUSE documented in the constant's comment**
> (collector link is the point of the feature; precedent riscv G-4 18→24) —
> BYTES stayed inside the +5% gate (37.320B baseline held, the feared +19.7%
> did not reproduce with the shipped form); (5) MT = old behavior + this
> face catalogued.
> - **(b) full stack-map / spill-per-live-ref:** NOT executed — measured
>   unnecessary for the main-only contract; they return as the required
>   machinery ONLY if the worker-stack-scan face (auto-collect with live
>   threads) is ever scheduled.
Each step: commit with the complete cross suite green + DOING.md on the line.
> Do NOT mix with S-5-x86/root_end (bugfix queue). G-0/G-1/G-2 move ahead
> without root_end; **G-3 also advanced** (emits its own riscv `.L`-local
> markers — did NOT need the x86 `kof_heap_root_end` of S-5); G-4 (sweep+
> collect) is next and does not depend on it either.
>
> **Beyond the GC (promoted to development 22/09):** the maintainer directive of
> 15/09 ("all native code must also talk directly to barebones — bootable code
> for microcontrollers, legacy and UEFI with Kof") is decomposed in
> `docs/PLAN-BAREMETAL-BOOT.md` (faces
> B-0…B-6: `kof_plat_*` HAL seam + freestanding profile + UEFI/BIOS/MCU + x86_64
> ring0/ring1) — **IN DEVELOPMENT** since 22/09 (`D-BAREMETAL-BOOT`, maintainer
> order). The MCU face depends on the collector (G-4/G-5) above.
>
> **Status:** `IN DEVELOPMENT (partial)` — **riscv64 + aarch64 with complete core (03/09)**: classes/arrays/List/strings/instanceof/switch/try-catch/FP/recursion in pure asm on both; advanced parity pending *(see re-audit 12/09 above — much of what was "pending" already runs under qemu; what remains has an honest gap code)*.
> **Version:** 0.2.6-beta · **Date:** 2026-09-03
> **Gap:** `NATIVE002` (riscv64 core ✅ 02/09; aarch64 core ✅ 03/09 via riscv→aarch64 translation; total x86 parity — JSON/DB/HTTP/concurrency/UI/net — pending on both).
> **Progress 03/09:** cross toolchain + qemu + **riscv64 + aarch64 codegen** (stack machine,
> `sp`=operands/`s11`/`x29`=frame pointer, model identical to x86_64) + **pure asm runtime** —
> `NativeRiscv64E2ETest 13/13` (`qemu-riscv64`) + `NativeAarch64E2ETest 13/13` (`qemu-aarch64`): println(String/Int), `var`, `if/else`,
> arithmetic/comparisons, **classes (virtual dispatch/fields/methods), arrays, List,
> switch, try/catch/throw, pattern matching (`switch String s`/`instanceof`/`as`),
> String methods, recursion**. See §2.3.
> **Decision (02/09):** runtime per arch **in pure assembly**, in the same style as x86_64
> (`NativeRuntime.generateRuntimeAssembly`) — **without C** ("Kof is Kof"; `kof-c-compiler`
> is another tool, not a runtime). The C compiled with cross gcc that was used on
> 02/09 as ABI validation was discarded: riscv64/aarch64 now emit pure asm
> runtime (bump allocator + raw syscalls `write`/`exit`, no PLT/libc) and link statically via `ld`, identical to the x86_64 model (no libc dependency).
> **Scope:** expand the `NativeBackend` (today `x86_64` in pure asm) to
> `riscv64` and `aarch64` Linux, preserving `frontend → Kof IR → backend` and
> `JVM/Native/JS` parity. This doc lives in `docs/` (not in `docs/future/`)
> because **there is already code in development** — it documents the real state and
> how to finish it.

## 1. Objective

Take the `NativeBackend` from single `x86_64` to multi-arch Linux without breaking
`KofPatternMatchingTest` 10/10 (`switch String s` / `instanceof` / `as` /
`checkcast`) and the `NativeE2ETest` suite.

It does not include macOS/Windows, advanced GC or complete native `kof.web` (see
"out of scope").

## 2. Real State (audit 01/09)

> **Rule of this folder:** `docs/` documents what **is in development**;
> `docs/future/` only what **is a future plan** (zero code). This item already has
> code, which is why it is here.

### 2.1 What IS ALREADY DONE (plumbing)

| Piece | State | Where |
|------|--------|------|
| Enum `Target.NATIVE_RISCV64` / `NATIVE_AARCH64` | ✅ | `Target.java` (values distinct from `NATIVE`; `NATIVE` remains = `x86_64`) |
| `Target.isNative()` covers the 3 natives | ✅ | `Target.java` |
| `Target.nativeArch()` → `x86_64`/`riscv64`/`aarch64` | ✅ | `Target.java` |
| CLI `native.risc`/`native.riscv64`/`native.riscv` → `NATIVE_RISCV64` | ✅ | `KofCliSupport.java:91` |
| CLI `native.arm`/`native.aarch64`/`native.aarch` → `NATIVE_AARCH64` | ✅ | `KofCliSupport.java:92` |
| `kof build`/`run` accept `native.risc`/`native.arm` | ✅ | `status.md:13-14` |
| Dispatch `emit()` → `emitRiscv`/`emitAarch64` | ✅ | `NativeBackend.java:210-215` |
| Cross toolchain invoked (as/ld + dynamic-linker + `-lc`) | ✅ | `NativeBackend.emitRiscv`/`emitAarch64` |
| Graceful fallback without toolchain (`keeping asm`) | ✅ | idem (try/catch `IOException`) |

**Practical consequence:** `kof build --target native.risc` **compiles and generates a
binary** (a stub that exits with `0`) — the toolchain/cross-as/ld pipeline already
works end to end.

### 2.2 What is NOT DONE YET (codegen — the real gap `NATIVE002`)

| Piece | State | Detail |
|------|--------|---------|
| **Real riscv64 lowering (core)** | ✅ complete 02/09 | `emitRiscv` emits the IR in asm: stack machine (`sp`=operand stack, `s11`=frame pointer, `ra`/`s11` saved in the frame — model identical to x86_64) + `.macro pop`; all core ops: literal/local/field/binary (int+FP+bitwise)/unary/condjump/jump/label/call (println/print/valueOf/String methods/collections/constructor/vtable virtual/FUNCTION/STATIC)/new_object/dup/pop/checkcast/instanceof/arrays/throw/try/catch/return. `NativeRiscv64E2ETest 13/13` |
| **Real aarch64 lowering (core)** | ✅ complete 03/09 | `emitAarch64` = **line-by-line translation of riscv64** (same model/lowering, ARMv8-A ISA: `sp`=stack/`x29`=frame pointer, `x30`/`x29` saved, `.macro pop` → `ldr`/`add`, `sp` already 16-aligned in `_start`, `str sp` via temp `x17`). `translateRiscvToAarch64` covers int+FP (`slt`/`sle`/`seqz`/`snez`/`sext.w`/`fcvt`/`fmv`/`fadd`/`feq`…), `andi`/`ori` via `movk x17`, `sd sp` via `mov x17,sp`. `NativeAarch64E2ETest 13/13` (`qemu-aarch64`) |
| Ops outside the riscv64/aarch64 core (JSON/DB/HTTP/concurrency/UI/net) | ❌ diagnostic `NATIVE002` | unknown ops emit the comment `# NATIVE002: op outside the happy path` (never a silent binary) |
| The 18 real `emit*` methods (x86_64) | ✅ | `emitBinary`/`emitOperation`/`emitMethod`/`emitConditionalJump`/vcall… — the complete path remains only on x86_64 |
| Extraction of `NativeBase` (common layout/`kof_alloc`/mangle) | ❌ does not exist | `NativeBackend` is still monolithic x86_64 (riscv/aarch64 reuse the same lowering via translation) |
| Runtime per arch (asm) | ✅ riscv64 + aarch64 core | `kof_alloc`(bump)/`kof_memcpy`/strings (literal/concat/equals/charAt/substring/contains/startsWith/endsWith/indexOf/toInt/length)/int-long-bool→string/print/objects (`init_object`/`instanceof`/super_table/vtables)/arrays (alloc/get/set/length+bounds)/List (new/add/get/set/size/contains/grow)/exceptions (`throw`/exc_chain/`null_error`/`bounds_error`) in **pure asm** riscv64 **and** aarch64 (raw syscalls, no libc; aarch64 via `translateRiscvToAarch64` — `adrp`+`add :lo12:`, `svc #0`, `and sp` skip, `str sp` via `x17`); `qemu-riscv64`/`qemu-aarch64` (see §2.3). |
| E2E `qemu` tests (aarch64/riscv64) | ✅ | `NativeRiscv64E2ETest` 42/42 + `NativeAarch64E2ETest` 42/42 (84 cross tests, measured by @Test + surefire 13/09) |
| CI with cross toolchains | ✅ exists (13/09) | job `cross-native` in `.github/workflows/ci.yml` (installs binutils-riscv64/aarch64 + qemu-user-static and runs the 2 suites; proved `success` in run 34732932745) |
| `backend-parity.md` columns per arch | ⚠️ partial | delta cited, separate `NATIVE_X86_64/AARCH64/RISCV64` columns pending |

**Practical consequence (SUPERSEDED — snapshot of 01/09):** it was valid for the original
plumbing stub; today (re-audit 12/09 at the top) riscv/aarch **execute
the logic** under qemu — 42+42 cross E2E tests, including real programs with
`println`/`instanceof`/`switch`/Map/Set/higher-order byte-identical to the JVM.
What remains honest in this table: `NativeBase` not extracted, per-arch columns
in `backend-parity.md` not separated, and the faces of ops outside the core
(JSON/DB/UI per specific arch).

### 2.3 Runtime in pure assembly per arch (decision 02/09)

**There is no C runtime in Kof.** The x86_64 native is pure asm end to end: the
runtime (`kof_alloc`, `kof_string_*`, `kof_instanceof`, …) is emitted in
assembly by `NativeRuntime.generateRuntimeAssembly()` and linked with
`ld -dynamic-linker /lib64/ld-linux-x86-64.so.2 -lc` — libc enters via PLT
(`printf`/`snprintf`), without compiled C. (The `kof-c-compiler` module is another
tool — reimplementation of sectorC — and is **not** a runtime.)

Decision for riscv64/aarch64: **same path** — runtime emitted in pure
asm per arch + `ld -dynamic-linker /lib/ld-linux-<arch>.so.1 -lc`. A C
compiled with cross gcc was used briefly (02/09) only to validate the
ABI/static linking on qemu; it was discarded from the architecture.

Installed toolchain (02/09, via `sudo apt`):
`binutils-riscv64-linux-gnu`, `binutils-aarch64-linux-gnu`, `qemu-user`,
`gcc-riscv64-linux-gnu`/`gcc-aarch64-linux-gnu` (debug),
`libc6-riscv64-cross`, `libc6-arm64-cross`.

Target pipeline (`emitRiscv`/`emitAarch64`):
```
Main.s  (program: kof_main + .data/.rodata sections)
      + riscv64/aarch64 asm runtime (emitted by NativeBackend)
   └─ <arch>-as → <arch>-ld -dynamic-linker /lib/ld-linux-<arch>.so.1 -lc
   └─ qemu-<arch> → expected output (exit 0)
```

**🔗 Dynamic link ON DEMAND (link-by-use) — DONE 15/09 (maintainer directive
"liga dinamicamente"):** the cross link *was* static (pure asm, no libc) even
though the 02/09 decision above always said dynamic. `NativeCrossLink` implements
the realignment: the binary stays **static** while the *pruned* runtime calls no
libc symbol; the moment a libc-dependent capability enters (double→string/FLT001
via `snprintf`/`strtod`, `kof.db` via `.so`, …) `NativeArchEmitter` switches to
`<arch>-ld --allow-shlib-undefined [--no-relax riscv] --sysroot=<s> -dynamic-linker
/lib/ld-linux-<arch>.so.1 -o <bin> <obj> -lc`. This preserves the portability of
the current 84 cross binaries (nothing changes without a libc consumer) and
unblocks the libc gaps on demand. `--allow-shlib-undefined` is required (the
sysroot `libc.so.6` references `GLIBC_PRIVATE` loader symbols; the runtime
resolves them). Sysroot resolution: `KOF_CROSS_SYSROOT` env → system install
(`/usr/<arch>-linux-gnu`, no `--sysroot`) → `/tmp/opencode/x` (this host) →
none (stays static + stderr, R6). Proven by `NativeCrossDynamicLinkTest` (5/5,
riscv64 **and** aarch64 under qemu: `snprintf`+`write` resolved at runtime; the
static-link sabotage of the same harness fails with undefined `snprintf`). The
first production consumer landed 15/09: `RuntimeDtoa` ported to the cross
runtime as slice `RtB45` (`kof_dtoa_format`/`kof_double_to_string`/
`kof_float_to_string`), closing **FLT001** — proven by `NativeRiscvDtoaTest`
(JVM oracle table on riscv64 **and** aarch64) plus the `nativeValueOfDoubleFloat`
E2E on both arches. aarch64 inherits automatically once the translator maps the
ABI aliases `fa0..fa7` → `d0..d7`.

Runtime details for riscv64/aarch64 (inc-0 02/09 + 03/09):
- allocation: **bump allocator + free-list** in `.bss` (no `mmap` — avoids
  problems with static qemu; x86_64 uses `mmap`+free-list, and riscv64/aarch64
  follow the model). G-1 (15/09) added the x86-style free-list + `kof_free` +
  `kof_memstats`; G-4 (15/09) closed the loop: `kof_gc_collect` runs at
  `kof_alloc` entry (tick 4096) and `kof_gc_sweep` feeds the free-list with the
  dead, so a long alloc loop recycles instead of exhausting the `.bss`.
- strings: layout **identical to x86_64** — `[typeId@0 i32][super@4 i32]
  [vtable@8 ptr][len@16 i32][data@24 …]` (`KOF_STRING_TYPE_ID=1`).
- output: raw syscall `write(1, …)` (`a7=64` riscv / `x8=64` arm) + `exit` (`a7/x8=93`) — **static** binary, no libc/PLT.
- aarch64: **mechanical translation** of the riscv64 runtime (`riscv2arm.py` validated + `translateRiscvToAarch64` in `NativeAarch64Translator.java:16`): `la`→`adrp`+`add :lo12:`, `ecall`→`svc #0`, `and sp` skip (sp already 16-aligned), `str sp` via `mov x17,sp`, `andi -16` via `movk x17`+`and`, `rem`→`sdiv`+`msub`, `slt/sle`→`cmp`+`cset`, FP `fcvt`→`scvtf`/`fmv`→`fmov`/`fadd`→`fadd`/`feq`→`fcmp`+`cset`.
- validation: `NativeRiscv64E2ETest 13/13` via `qemu-riscv64` + `NativeAarch64E2ETest 13/13` via `qemu-aarch64` (complete core).

What **remained** for the next increments:
- riscv64 + aarch64: Map/Set, higher-order (map/filter/reduce), JSON/DB/HTTP/concurrency/
  UI/net — total parity with x86_64 (same gap on both; today diagnostic `NATIVE002`).

## 3. Architecture (target)

> **Note:** the real implementation diverged from the original sketch (which proposed
> renaming to `NATIVE_X86_64` + `--arch` flag). The decision adopted was **distinct
> enum values** (`NATIVE` = x86_64, `NATIVE_RISCV64`, `NATIVE_AARCH64`) +
> **target name in the CLI** (`native.risc`/`native.arm`) — without the `--arch` flag and
> without renaming `NATIVE` (keeps compat). It follows the real decision.

```
Target enum:
  JVM, NATIVE (=x86_64), NATIVE_RISCV64, NATIVE_AARCH64, JS, ANDROID

IRModule → NativeBackend.emit (select by target):
  NATIVE          → x86_64 lowering (complete, 18 emit*)   [DONE]
  NATIVE_RISCV64  → emitRiscv   (complete core 02/09, 13/13) [DONE]
  NATIVE_AARCH64  → emitAarch64 (complete core 03/09, 13/13 via translation) [DONE]
  → (goal) extract NativeBase: ClassLayout, kof_alloc, mangle, resolveFieldOffset
```

`kof build --target native.risc|native.arm` (already works in dispatch).

## 4. Per-Arch Mapping (reference for the lowering)

| Aspect | x86_64 (current) | AArch64 | RISC-V 64 |
|---------|----------------|---------|-----------|
| **Assembler** | `as` GNU | `aarch64-linux-gnu-as` | `riscv64-linux-gnu-as` |
| **Linker** | `ld -dynamic-linker /lib64/ld-linux-x86-64.so.2 -lc` (x86_64 uses PLT/libc) | `aarch64-linux-gnu-ld` **static** (raw syscalls, no `-lc`) | `riscv64-linux-gnu-ld` **static** (raw syscalls, no `-lc`) |
| **Regs args** | `rdi rsi rdx rcx r8 r9` | `x0 x1 x2 x3 x4 x5` | `a0 a1 a2 a3 a4 a5` |
| **Regs temp** | `rax rcx rbx r10` | `x9 x10 x11 x12` | `t0 t1 t2 t3` |
| **Ret** | `rax` | `x0` | `a0` |
| **Stack** | `pushq %rax` | `str x0,[sp,#-16]!` | `addi sp,-16; sd a0,0(sp)` |
| **Call** | `call sym` | `bl sym` | `call sym`/`jal` |
| **Vcall** | `mov 8(%rax),%rbx; add $idx*8,%rbx; mov (%rbx),%rbx; call *%rbx` | `ldr x9,[x0,#8]; add x9,x9,#idx*8; ldr x9,[x9]; blr x9` | `ld t0,8(a0); addi t0,idx*8; ld t0,0(t0); jalr t0` |
| **Cmp/Jmp** | `cmpq %rax,%rcx; je L; jmp M` | `cmp x1,x0; b.eq L; b M` | `sub t0,a0,a1; beqz t0,L; j M` |
| **String header** | `24B [typeId@0][vtable@8][len@16]` | same | same |
| **Syscall exit** | `mov $60,%rax; xor %rdi,%rdi; syscall` | `mov x8,#93; mov x0,#0; svc #0` | `li a7,93; li a0,0; ecall` |

`kof_alloc`/`kof_instanceof`/`kof_string_*` per arch with `KOF_STRING_TYPE_ID=1`
constant.

## 5. How to Finish (step by step — reflects the plumbing that already exists)

> The plumbing (enum + CLI + dispatch + toolchain) **is already ready**. What is
> missing is the codegen. Incremental order, without breaking `x86_64`:

1. **Extract `NativeBase`** — move to a common class/interface:
   `getLayoutForType`/`sanitize`/`mangle`/`resolveFieldOffset`/`collectStrings`
   (today in `NativeBackend`). `NativeBackend` (x86_64) inherits and stays the same.
   → validate `mvn test -Dtest=CompilerDriverTest` (no `emit` changes).
   *Depends on: nothing. Does not change the x86_64 binary.*

2. **Runtime per arch** — move `kof_alloc`/`kof_instanceof`/`kof_string_*`
   to asm per arch (today inline in x86_64 `NativeRuntime`); the `emit` of each
   target includes the correct `.s` section. *Depends on 1.*

3. **Minimal `Riscv64Backend`** — replace the `emitRiscv` stub with real lowering
   of the happy path: `String`/`println`/`instanceof String` + `switch String s`
   + `checkcast` no-op, using the mappings of table §4.
   → `qemu-riscv64` running `hello` (test `assume` if `qemu`/`riscv64-as`
   are absent, like `NativeE2ETest`). *Depends on 1,2.*

4. **Minimal `Aarch64Backend`** — idem for aarch64 (`qemu-aarch64`). *Depends on 1,2.*

5. **Collections + classes** — `kof_list_*`/`kof_map_*`/`kof_set_*` and
   `kof_instanceof` for user classes (the `Dummy` used in
   `KofPatternMatchingTest`). *Depends on 3,4.*

6. **Multi-arch E2E tests** — `NativeRiscv64E2ETest` + `NativeAarch64E2ETest`
   (`@Tag("slow")`, `assume` for absent `qemu`+cross-as) running the same source
   that `KofPatternMatchingTest` runs on x86_64/JS. *Depends on 5.*

7. **CI** — `x86_64` toolchains always; `aarch64`/`riscv64` with
   `if: cross-available` (do not break the pipeline when the toolchain is missing).
   *Depends on 6.*

8. **Docs** — `backend-parity.md`: separate columns
   `NATIVE_X86_64`/`AARCH64`/`RISCV64`; remove `NATIVE002` when 6 is green.
   *Depends on 6.*

**Definition of done:** `var x:Object="hello"; switch(x){case String s: println(s)}`
compiles and runs **identical** on `x86_64`, `aarch64 (qemu)`, `riscv64 (qemu)` and
`JS` (`typeof==="string"`); `KofPatternMatchingTest` 10/10 per arch.

## 6. Risks and Mitigation

- **16-byte Stack ABI** (ARM/RISC-V require aligned `sp`) → use 16-byte
  `str/ld` pairs.
- **RIP vs PC-relative reloc**: x64 `leaq sym(%rip)` → ARM `adrp`+`add` /
  RISC-V `auipc`+`ld`.
- **Cross toolchain absent** → `assume` skip, do not fail `mvn test`.
- **QEMU slow** → `NativeE2ETest` only fast x86_64; aarch64/riscv64 in
  `@Tag("slow")`.
- **Silent divergence**: the current stub "passes" by generating a binary → ensure
  the `NATIVE002` gap is a **clear diagnostic** (not a binary that silences the
  logic) until the lowering exists.

## 7. Out of Scope (stay in `docs/future/` / other docs)

- Advanced `GC` mark-sweep, `float/double` on Native (`F2D`), native `kof.web`
  `listen`, `macOS` Mach-O / `Windows` PE.

  **`macOS` Mach-O is IN PROGRESS (issue #1135, `D-NATIVE-MACOS` — arm64 + x86-64).**
  The `kof_plat_*` seam (the 21 named platform functions) is the migration
  surface, and it is now RATCHETED: `scripts/check_macos_seam.sh` measures the
  system calls that still bypass the seam (x86-64 `syscall` 180 out-of-seam of
  203; riscv64 `ecall` 168 out-of-seam of 184), the ELF TLS sites
  (`%fs:/@tpoff`, 24) and the `futex` sites (15 — macOS uses
  `__ulock`/`os_unfair_lock`), and **fails the build if any of those numbers
  grows** (the baseline is `scripts/macos-seam-baseline.txt`; diminishing is
  always green; `--update-baseline` regenerates it; `--selftest` proves the
  gate still distinguishes equal/growth/improvement). The stages M-1..M-8
  (Mach-O emitter → libSystem seam → allocator/GC → exceptions → concurrency →
  …) consume these numbers as the BEFORE; M-7 needs a macOS runner.
  **M-1 LANDED 21/11 (#1135, D-NATIVE-MACOS):** the macOS target opens as a
  PROFILE (`NativeProfile.MACOS_ARM64`, `--profile macos`), not a new backend
  — the IR, the lowering and the `kof_plat_*` seam are untouched. The stage
  delivers the three faces of the M-1 row: (1) the **Mach-O directive
  emitter** (`nat/NativeMachO.java` — a textual pass over the whole assembly:
  the ELF-only `.type`/`.size` are dropped, the sections become
  `__TEXT,__text`/`__DATA,__data`/`__DATA,__bss`/`__TEXT,__const`, and the
  globals AND their references take the Mach-O `_` prefix — the mnemonic and
  the registers are never touched); (2) the **link line**
  (`NativeAssembler.macosLinkArgs` — `ld -lSystem -arch arm64` plus
  `-syslibroot <sdk>` when `KOF_MACOS_SDK_PATH` is set; no ELF
  `-dynamic-linker`, no GNU `-lc`); (3) the **honest refusal** on a host
  without the macOS toolchain (`NATIVE004` — this Linux fleet has the GNU
  `as`/`ld`, which do not speak Mach-O; the runner that proves the link is
  the M-7 stage). The proof of the stage is STRUCTURAL on this fleet (the
  translated text + the link line), exactly as the plan states; the `_main`
  entry and the libSystem seam bodies are the M-2 row.
  **M-1 CORRECTED 22/11 (#1135 — the M-1 emitter was BROKEN against the real
  assembly; measured, not assumed):** the M-1 proof was six small text tests
  (~10 lines each). The **real** assembly the backend emits is the whole
  runtime — **132 KB** — and against THAT text the M-1 emitter had five real
  defects, all found by running the **real Mach-O assembler** (the clang 18,
  provisioned by `scripts/provision-macho-toolchain.sh` →
  `~/.local/share/kof-macho`): (1) it **hung** — the operand scan restarted at
  the `%` of a REGISTER without consuming it, so every line with a register
  (`pushq %rbx`) looped forever: a real macOS compile would never have
  finished; (2) the **immediates were corrupted** (`movq $1` → `movq _$1`;
  the clang: "invalid operand for instruction"); (3) the **hex literals were
  corrupted** (`0x4f0c…` → `0_x4f0c…`; "unexpected token"); (4) the
  **mnemonic after a prefix** was prefixed (`lock cmpxchg` → `lock _cmpxchg`;
  "invalid instruction mnemonic"); (5) the **data reference** (`.quad
  Pair_toString`, the method table) took no prefix — the symbol would have
  been undefined. The emitter now scans TOKEN by token (the mnemonic and its
  prefixes, the registers, the immediates, the numbers, the strings and the
  comments are all copied verbatim; only the global symbol references take the
  `_` prefix), and the proof is **the real assembler accepting the real
  132 KB** — the whole runtime, minus the two ELF-only faces the macOS still
  lacks. The stage also gained the **named refusal `NATIVE005`** for those
  faces: the raw Linux syscalls (the M-2 row — the `kof_plat_*` seam over
  libSystem) and the ELF local-exec TLS (`%fs:…@tpoff` — the M-4 row) are
  counted in the emitted text and the profile refuses with the diagnostic
  naming both, instead of emitting a binary that fails at run time (R6).
  **M-2 SLICE 1 LANDED 22/11 (#1135 — the `kof_plat_*` seam over libSystem:
  the write/exit family; and the link line names the architecture of the
  EMITTER):** the macOS profile emitted the **host** text, and the host text
  speaks the raw Linux syscall ABI — the Darwin has no such thing, so a macOS
  binary could never have run. MEASURED on the real hello (the same 132 KB the
  M-1 correction proved against the real assembler): the **9** raw syscall
  sites are owned by `kof_plat_write`, `kof_plat_writev`, `kof_plat_exit`,
  `kof_plat_exit_group`, `kof_plat_thread_id`, `kof_plat_sync` and three
  `mmap` grows in the allocator (`.Lgrow_try`). THE FIRST FAMILY MIGRATED is
  write/exit: on the macOS profile the seam calls the **libSystem**
  (`call write`, `call writev`, `call exit` — the same SysV argument
  registers as the Linux syscall, `%rdi/%rsi/%rdx`), with the proven
  stack-alignment shape (`pushq %r15; movq %rsp, %r15; andq $-16, %rsp;
  call …; subq %rsp, %r15; addq %r15, %rsp; popq %r15`) because the libc
  uses SSE and the ABI wants `%rsp ≡ 0` at the call site. The Darwin has no
  `exit_group`, so both exits call `exit` (the process ends either way; the
  difference from the Linux `exit`=thread-only is recorded in the seam). THE
  RATCHET FELL from **9 to 5** raw syscalls: the migrated family is gone and
  what is left is the allocator (M-3) and the concurrency (M-5) — and the
  refusal `NATIVE005` now **names the owners** instead of counting blindly.
  **THE ARCHITECTURE OF THE LINK LINE (a measured contradiction, fixed):**
  the M-1 line said `-arch arm64` while the object the backend emits is
  **x86-64** (the M-1 proof assembled the real 132 KB as
  `x86_64-macos-none`) — a link that fails on the M-7 runner. The
  architecture now follows the **emitter** (`-arch x86_64`), and the profile
  constant lost its architecture claim (`NativeProfile.MACOS`, was
  `MACOS_ARM64`): a profile that names an architecture it does not emit is a
  lie. The **Apple silicon face (arm64) is the next row of #1135** — it
  needs the cross aarch64 emitter and its own Mach-O proof; on Apple silicon
  the x86-64 face runs under Rosetta 2. **THE DETECTOR WAS BLIND ON TWO
  FACES:** `NATIVE005` counted only the x86-64 `syscall` — on the aarch64
  (`svc #0`) and riscv64 (`ecall`) faces the refusal was silent; it now sees
  the three. **PROOF (executed):** `NativeMacOSSeamE2ETest` **8/8** —
  RED-first **6/6 failures** on the old code (the raw syscall inside the
  seam, the ratchet still at 9, the missing `_write`/`_exit` symbols, the
  `-arch arm64` link line, the architecture claim in the profile name, the
  blind detector on `ecall`) and GREEN after; the **real Mach-O assembler
  accepts the profile text** with the libSystem bodies (the object carries
  `_write`/`_exit` as undefined externals — what the libSystem link
  resolves); `NativeMacOSProfileE2ETest` 11/11; the native battery **810 run /
  0F / 0E / 81 skipped** (named environment skips); the gates rc=0.
  **M-2 SLICE 2 LANDED 22/11 (#1135 — the time/random/io families of the seam
  over libSystem, and the Darwin clock ids are the DARWIN ones):** the second
  batch of the seam now speaks the libSystem too, and one of its bodies would
  have been silently WRONG if the Linux constant had been copied. MEASURED on
  a real program (`time.sleep` + `time.now` + `random.hex(8)` — the fatias
  those faces pull): the raw Linux syscalls fell **20 → 16**, and the four
  families that left the Linux ABI are `kof_plat_time`, `kof_plat_time_mono`,
  `kof_plat_sleep` and `kof_plat_random` (plus `kof_plat_read`/`kof_plat_close`
  in the io family). **THE CLOCK IDS ARE THE DARWIN ONES (measured in the
  macOS SDK headers that the provisioned toolchain ships):**
  `CLOCK_REALTIME = 0` and **`CLOCK_MONOTONIC = 6`** — the Linux value is 1,
  which on Darwin is not the monotonic clock at all. The `struct timespec` of
  the Darwin has the SAME layout as the Linux (`tv_sec` at 0, `tv_nsec` at 8),
  so the runtime that reads `0(%rsp)`/`8(%rsp)` stays right. **THE RANDOM
  FAMILY USES THE DARWIN CSPRNG:** `getentropy` does **not exist** on Darwin
  (measured — it is absent from the SDK headers) and it caps at 256 bytes per
  call, which `random.hex(n)` would break; the body uses
  `arc4random_buf(buf, len)` (10.7+, the Darwin's own CSPRNG, no size cap)
  and returns the requested length, because the seam contract is
  `kof_plat_random(buf, len) -> n` with `n >= 0` = bytes written. The io
  family (`read`/`close`) migrated mechanically — the same SysV registers.
  **THE BODIES MOVED TO THEIR OWN CLASS** (`runtime/RuntimePlatMacos.java` —
  the same pattern as `RuntimeBios`/`RuntimeUefi`/`RuntimeBareRandom`), and
  `RuntimePlat` delegates per family. **WHAT IS STILL LINUX (measured on that
  program):** the 16 remaining raw syscalls are `kof_plat_thread_id` and
  `kof_plat_sync` (the concurrency face — M-5), the allocator's `.Lgrow_try`
  (M-3), and the runtime's OWN io (`.Lkof_read_line_loop`, `kof_read_file`,
  `kof_write_file`) plus `kof_sec_secret_get` — those BYPASS the seam, and
  routing them through it is the M-6 row. **PROOF (executed):**
  `NativeMacOSSeamE2ETest` **12/12** — RED-first **4 failures** on the old
  code (the raw syscall in the time family, the Linux clock id, the io family
  still on `syscall`, the missing `_clock_gettime`/`_arc4random_buf` symbols)
  and GREEN after; the **real Mach-O assembler accepts the time/random
  bodies** (the object carries `_clock_gettime` and `_arc4random_buf` as
  undefined externals); `NativeMacOSProfileE2ETest` 11/11; the native battery
  **814 run / 0F / 0E / 81 skipped**; the gates rc=0.
  **M-3 SLICE LANDED 22/11 (the allocator over the libSystem) — the
  `kof_plat_heap_grow` (the arena's 4 GiB → 64 MiB descent) now calls
  `mmap`/`munmap` from the libSystem, and the Darwin's mmap is NOT the Linux
  mmap in THREE points, all MEASURED in the SDK headers: (1) the 4th flag
  goes in `%rcx` (the SysV ABI — the Linux syscall put it in `%r10`); (2) the
  flags are DIFFERENT — `MAP_PRIVATE|MAP_ANON|MAP_NORESERVE` = `0x1042` on the
  Darwin vs the Linux `0x4022` (the Linux `MAP_ANONYMOUS` is 0x20 and its
  `MAP_NORESERVE` is 0x4000); (3) the error is `MAP_FAILED` = `-1` exactly
  (the Linux syscall returned a `-errno` in `[-4095,-1]`). The arena logic
  (the descent, the bitmap, the release of the failed bitmap) is the host's
  byte for byte — only the syscall became the libc call, with the proven
  stack-alignment shape. **THE RATCHET:** the raw Linux syscalls of the real
  hello fell **5 → 2** — what is left of the seam's x86-64 face is the
  concurrency (`kof_plat_thread_id`, `kof_plat_sync` — the M-5 row). **PROOF
  (executed):** `NativeMacOSAllocatorE2ETest` **5/5** — RED-first **5
  failures** on the old code (the body still on `syscall`, the Linux flags in
  `%r10`, the Linux `-errno` check, the ratchet still at 5, the missing
  `_mmap`/`_munmap` symbols) and GREEN after; the **real Mach-O assembler
  accepts the allocator body** (the object carries `_mmap`/`_munmap` as
  undefined externals); `NativeMacOSSeamE2ETest` 12/12;
  `NativeMacOSProfileE2ETest` 11/11; the native battery **819 run / 0F / 0E /
  81 skipped**; the gates rc=0. **THE BOUNDARY (honest):** the profile still
  REFUSES to emit a binary (`NATIVE005`) — the concurrency (M-5), the TLS
  (M-4) and the runtime's own io (M-6) still speak Linux, and the proof that
  a macOS binary RUNS is the M-7 runner.
  **M-6 SLICE 1 LANDED 22/11 (the runtime's own io through the seam) — the
  Native runtime's `read`/`write`/`close` now speak the seam instead of the
  raw Linux syscall, so the macOS profile can read and write through the
  libSystem.** The seam is the ONLY place where the runtime talks to the
  OS, and the io was speaking the Linux syscall ABI directly (the gate 2
  counted it as out-of-seam debt). 16 call sites moved
  (`RuntimeIo2`, `RuntimeIo3`, `RuntimeTime`: the stdin read, the file
  read/write, the append, the byte ranges). The host behaviour is
  byte-identical — the seam's host bodies ARE the same syscalls with the
  same registers — and the macOS profile now reaches the libc `read`/
  `write`/`close`. **THE `fstat` STAYS RAW ON PURPOSE (measured):** the
  syscall numbers are `0=read`, `1=write`, `3=close`, **`5=fstat`** — and
  the `struct stat` of the Darwin has a DIFFERENT layout than the Linux
  one, so the stat family is the NEXT M-6 row (its own seam body), not a
  read in disguise. (The first pass of this slice mapped the `5` to the
  read and the io suite caught it — 4 RED — before anything shipped.)
  **THE RATCHET:** the out-of-seam x86-64 count fell **180 → 164** and the
  baseline was regenerated deliberately after the improvement (the gate's
  documented flow; the selftest is 3/3). **PROOF (executed):**
  `NativeIoSeamE2ETest` **2/2** — RED-first **2 failures** on the old code
  (the io does not go through the seam) and GREEN after; the io family
  **92 run / 0F / 0E / 9 skipped** (`IoE2ETest` 26/0); the native battery
  **829 run / 0F / 0E / 82 skipped**; the gates rc=0.
  **THE MEASUREMENT FOR THE NEXT ROW (M-4, the TLS):** the Mach-O thread
  local access was read off the REAL compiler (the provisioned clang 18):
  `movq _x@TLVP(%rip), %rdi; callq *(%rdi); movq (%rax), %reg` plus the
  `__DATA,__thread_vars` descriptor (`__tlv_bootstrap`, 0, the init
  slot) — and the runtime's exception chain is ONE variable
  (`kof_exc_chain`) read/written at 22 sites (9 loads, 13 stores). The
  register audit already paid one dividend: at the store sites the
  `%rdi`/`%rax` hold LIVE values (the `String` argument, the result), so
  a naive seam call would clobber them — the M-4 bodies must preserve the
  caller-saved registers (the seam get/set shape) or the sites must be
  rewritten with the value in a register they can spare.
  **M-4 SLICE 1 LANDED 22/11 (the TLS through the seam — the chain's LOADS;
  and the macOS face is a pthread key, a PUBLIC API):** the runtime's
  exception chain (`kof_exc_chain`) got a get/set seam pair with a
  TRANSPARENT contract — the get returns the value in `%rax` and preserves
  every other register; the set takes `%rdi` and preserves everything
  (including `%rax`, because `pthread_setspecific` returns an int the caller
  does not expect). The **9 LOAD sites** moved (the parse-or-default family,
  the GC, the DB, the concurrency); the **13 STORE sites** are the next
  slice — the register audit measured that `%rdi`/`%rax` hold LIVE values
  there (the `String` argument, the result), so a naive seam call would
  clobber them. The **macOS face** uses `pthread_key_create`/
  `pthread_getspecific`/`pthread_setspecific` (measured in the SDK's
  pthread.h), with the key created at the **STARTUP** (`kof_plat_tls_init`,
  single-thread — no creation race). The alternative measured (the Mach-O
  TLV: `movq _x@TLVP(%rip), %rdi; callq *(%rdi)` + the
  `__DATA,__thread_vars` descriptor) was REJECTED: it would force the
  translator to rewrite every access and emit the descriptor (the `$` in
  the symbol names). **A LATENT BUG FIXED ON THE WAY:** the entry left the
  `main(String[] args)` array in `%rdi` and then called the thread-id seam —
  on macOS (a libc call) that clobbers `%rdi`, so the args would arrive
  garbage; the entry now re-materializes `%rdi` from the callee-saved `%rbx`
  right before the main call. **THE RATCHET:** the TLS sites fell **24 →
  17** (the baseline regenerated after the improvement). **PROOF
  (executed):** `NativeMacOSTlsE2ETest` **3/3** — RED-first **3 failures**
  (the seam pair missing; the macOS bodies missing; the `_pthread_*` symbols
  missing) and GREEN after; the native + exception battery **946 run / 0F /
  0E / 80 skipped**; the gates rc=0. **THE NEXT SLICE (M-4):** the 13
  stores (the value must reach a register the site can spare) + the
  emitter's try/catch machinery (2 sites inside the compiled method) — then
  the hello's last TLS face is gone and the `NATIVE005` refusal goes silent.
  **M-4 SLICE 2 LANDED 22/11 — THE TLS MIGRATION IS COMPLETE: the macOS
  profile has NO ELF-only face left, and the NATIVE005 refusal goes SILENT
  (the only refusal that remains is NATIVE004 = the missing macOS toolchain,
  the M-7 runner).** The remaining **18 sites** (14 in the runtime + 4 in the
  emitter's try/catch machinery) now go through the seam: the value reaches
  `%rdi` (with `%rdi`/`%rcx` pushed/popped at the site, and the `%rsp` stores
  using `leaq 16(%rsp)` — the original stack pointer after the two pushes), and
  the set body preserves EVERYTHING, so the `movq %rax, %rsp` that follows the
  unlink keeps working (the `%rax` base survives the call). **THE RATCHET:**
  the TLS sites fell **17 → 3** (the 3 are the seam's own Linux body), and the
  baseline was regenerated. **THE MILESTONE TEST:** `theMacosProfileHasNoElfTlsLeft`
  — the macOS profile's emitted text has **no `%fs:` and no `@tpoff`** at all
  (RED on the old code). The two refusal tests now assert the milestone: the
  NATIVE005 is GONE and only the NATIVE004 names the toolchain. **PROOF
  (executed):** the macOS family **40/0F**; the UEFI/Ring families **8/0F** and
  **5/0F** (the suite's mid-run failures were contamination — see the verdict
  record); the Troolean family is **3/0F** (the sibling lane's #1178 ROOT fix
  landed: the type was `Nullable(Nullable(Bool))`, not a design fork); the
  exception + native battery **946/0F**; the gates rc=0. **THE BOUNDARY
  (honest):** the macOS profile now compiles the hello cleanly up to the LINK
  — the link and the RUN need the M-7 runner (a real macos-14 host), and that
  is what NATIVE004 refuses without. **THE NEXT ROWS:** the "stat" family of
  the seam (the Darwin `struct stat`), the M-7 runner, the arm64 face (the
  cross aarch64 emitter + its Mach-O proof); then #1159 keep-alive HTTP/1.1.
  **M-6 SLICE 2 LANDED 22/11 (the NET family over the libSystem — and a REAL
  TRANSLATOR BUG the slice exposed: the character-literal immediates).** The
  six socket seam functions (`kof_plat_net_socket`/`connect`/`bind`/`listen`/
  `accept`/`send`) now call the libSystem on the macOS profile; the BSD socket
  ABI is the SAME as the Linux libc one, and the ONE difference is the 4th
  flag of the `sendto` (the Linux syscall puts it in `%r10`, the SysV ABI in
  `%rcx` — the body moves it). The bodies are TRANSPARENT (they preserve the
  caller-saved registers, so the runtime callers — written against the
  syscall — stay correct) and they live in their own class
  (`RuntimePlatMacosNet`) because the seam file crossed the 500-line gate;
  the TLS family moved to `RuntimePlatMacosTls` for the same reason (the
  gate is now rc=0 with all three under the critical band).
  **THE TRANSLATOR BUG (found by the real assembler, not by inspection):**
  a character-literal immediate (`$'h'`) was scanned as an operand — the `h`
  took the Mach-O `_` prefix (`$'_h'`) and the clang answered "single quote
  way too long". The kof.http runtime compares characters
  (`cmpb $'h', 0(%rbx)`), so the face appeared the moment the net slice
  entered the text; the fix copies `$'X'` (and the escaped `$'\n'`)
  verbatim. **PROOF (executed):** `NativeMacOSNetE2ETest` **3/3** — RED-first
  **3 failures** on the old code (the socket bodies were the raw syscalls;
  the sendto had no flag move; the char literal was corrupted) and GREEN
  after; `NativeMacOSProfileE2ETest` **12/12** (the new char-literal test RED
  on the old translator); the macOS family **36/0F**; the native + http/web
  battery **877 run / 1F** (the failure is the sibling lane's in-flight
  `NativeCrossMultidistroE2ETest` — a missing `image.Raster` import at the
  front end, untouched by this slice); the seam gate rc=0 (futex 13, TLS 3);
  `check_500` rc=0 after the splits; the changelog-ledger gate is red on the
  tip for the SIBLING lane's §1193/§1194/§1195 rows (no CHANGELOG entry yet
  — their bookkeeping, reported not touched).




  **M-5 SLICE 1 LANDED 22/11 (the concurrency face) — the port exposed a REAL
  DEFECT in the x86-64 runtime: the futex arguments were passed in the WRONG
  registers, so the "futex wait" was an EINVAL no-op (a wait that never
  sleeps) and the scheduler's wake woke the WRONG address.** THE CONTRACT of
  the seam is the futex ABI — `kof_plat_sync(uaddr=%rdi, op=%rsi, val=%rdx,
  timeout=%r10)` — declared the same way on the riscv face
  (`kof_plat_sync(uaddr=a0, op=a1, val=a2, timeout=a3)`). The repo already
  knew this class of bug: the "bug 50" note in `RuntimeChannel` says the old
  code called syscall 202 with `rdi=chan` (the wrong uaddr) and `rsi=&lock`
  (used as the op) — "undefined behaviour under contention (spawn)". That fix
  landed in `RuntimeChannel` and `RuntimeLock` but NOT in `kof_alloc` nor in
  the scheduler: their wait sites left the ADDRESS in `%rsi` (the op
  register) and a stale value in `%rdi`, and the scheduler's wake sites woke
  the JOB pointer instead of the lock. THE FIX uses the established pattern
  (`leaq <lock>(%rip), %rdi; xorl %esi, %esi` for the wait — plus restoring
  the address for the `cmpxchg` — and `leaq <lock>(%rip), %rdi` for the wake).
  **MEASURED on the real binary (strace):** the futex calls were
  `futex(0x40a780, FUTEX_WAKE, 0)` — and the kernel honours that val by
  waking ONE waiter (the wake returned 1 — the undocumented-but-measured
  behaviour, so the existing wake sites were right in effect); the wait sites
  never appeared with a valid op, because the (tiny) critical section is
  usually won by the spin. **THE MACOS THREAD ID:** the Darwin has no
  `gettid`; the public API is `pthread_threadid_np(NULL, &tid)` (measured in
  the SDK header, and `_pthread_threadid_np` is in the libSystem export
  list) — the body keeps the id slot in the stack and aligns the stack for
  the libc call. **THE RATCHET:** the raw Linux syscalls of the real hello
  fell **2 → 1** — only the futex (`kof_plat_sync`) is left in the seam's
  x86-64 face. **PROOF (executed):** `NativeConcurrencySeamE2ETest` **4/4** —
  RED-first **4 failures** on the old code (the broken contract at the
  `kof_alloc` wait; the macOS thread id still `gettid`; the missing
  `_pthread_threadid_np` symbol; the ratchet) and GREEN after; the contract
  proof is **structural and deterministic** (the test walks the emitted
  assembly and, at EVERY `kof_plat_sync` call site, requires the op to be a
  constant, the uaddr an address and the val a constant — no race involved);
  the concurrency family stays green (`KofConcurrency2Test` 50/0,
  `KofAwaitTest` 8/0, `SpawnAwaitBlockE2ETest` 5/0, `MakealiveReconcileE2ETest`
  1/0, `WorkflowPrimitivesE2ETest` 6/0); the native battery **823 run / 0F /
  0E / 81 skipped**; the gates rc=0. **THE NEXT SLICE:** the macOS sync body
  (the futex → `__ulock`/`os_unfair_lock`) — the ulock API is PRIVATE (it is
  NOT in the SDK headers, but libSystem exports `___ulock_wait`/`___ulock_wake`
  — measured in the libSystem export list), so its semantics need the XNU
  contract and a real runner (M-7) to be proven.
  **M-5 SLICE 2 LANDED 22/11 (the macOS sync over the Darwin ulock — and the
  seam's x86-64 face is now ALL libSystem):** the futex became the **ulock**
  API, and the mapping was MEASURED in the XNU source, not guessed —
  `bsd/sys/ulock.h` (`__ulock_wait(uint32_t op, void *addr, uint64_t value,
  uint32_t timeout_us)`, `__ulock_wake(uint32_t op, void *addr, uint64_t
  wake_value)`, `UL_COMPARE_AND_WAIT = 1`, `ULF_WAKE_ALL = 0x100`) and
  `bsd/kern/sys_ulock.c` (the wait does `copyin_atomic32` and only sleeps
  when `*addr == value` — the futex WAIT; the wake without `ULF_WAKE_ALL`
  calls `waitq_wakeup64_one`, with it `waitq_wakeup64_all`; `wake_value` is
  ignored for `COMPARE_AND_WAIT`). THE BODY: the futex command is the low 7
  bits of the op (`andl $0x7f` — the `FUTEX_PRIVATE` flag rides above and is
  irrelevant on Darwin), the WAIT maps to `__ulock_wait(1, uaddr, val, 0)`
  (timeout 0 = none, like the futex's NULL) and the Kof `futex WAKE(INT_MAX)`
  maps to `__ulock_wake(1 | ULF_WAKE_ALL, uaddr, 0)` while a smaller count
  wakes one. THE BOUNDARY (honest): the ulock API is **PRIVATE** — it is NOT
  in the SDK headers (measured), but libSystem EXPORTS
  `___ulock_wait`/`___ulock_wake` (the SDK export list, with the three
  underscores — the names the assembly carries) — so the wiring is proven by
  the text + the real assembler, and the running semantics by the M-7
  runner. **THE RATCHET:** the raw Linux syscalls of the real hello fell
  **1 → 0** — the seam's x86-64 face is ALL libSystem now, and the
  `NATIVE005` refusal names ONLY the TLS face (the M-4 row). **THE GATE:** the
  futex count in `check_macos_seam.sh` now EXCLUDES the seam (the seam is the
  migration surface, not the out-of-seam debt) — the number fell 15 → 13 and
  the baseline was regenerated deliberately after the improvement (the
  selftest is 3/3). **PROOF (executed):** `NativeConcurrencySeamE2ETest`
  **7/7** — RED-first **4 failures** (the macOS sync still `syscall 202`; the
  ratchet at 1; the missing `___ulock_*` symbols; the refusal still naming
  the syscall face) and GREEN after; the **real Mach-O assembler accepts the
  sync body** (the object carries `___ulock_wait`/`___ulock_wake` as
  undefined externals); the native battery **826 run / 0F / 0E / 81
  skipped**; the concurrency family unchanged (the host path is untouched);
  the gates rc=0 for this lane's surfaces (`check_release_blockers` is red on
  the tip because the new issues #1187–#1189 have no ledger rows yet — the
  triage lane's file, reported not touched).
- **Bare-metal / bootable** (microcontroller, legacy BIOS, UEFI) — maintainer
  directive 15/09 ("all native code must also talk directly to barebones"). The
  pure-asm riscv64/aarch64 runtime (no libc) is the natural base, but the emitters
  are hardwired to Linux `ecall`/`syscall` and a `_start` ABI, and there is no
  freestanding link profile. IN DEVELOPMENT since 22/09:
  `docs/PLAN-BAREMETAL-BOOT.md` (faces B-0…B-6).
