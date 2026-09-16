[English](conformance-matrix.md) | [Português](conformance-matrix.pt_BR.md)

# Conformance Matrix — Feature × Target (Phase 9, platform plan)

> **Created:** 07/09/2026 · **Owner:** KOFSCRIPT lane (fixes-for-kofagent)
> **Plan:** Phase 9 (ex-PLATFORM-PLAN; decision recorded in `docs/development/DECISIONS.md` §D-PLATFORM) ·
> **Roadmap-audit:** line 12 "Conformance Suite — NOT STARTED (BackendParityTest is a proxy)" + P4 queue.
>
> **Rule:** each cell is locked by a test in `ConformanceMatrixTest`
> (kof-compiler/src/test). **DONE** = the 4 targets agree with the expected
> output. **PARTIAL** = divergence with a bug recorded in
> `docs/bugs-and-gaps/known-bugs.md` (ref in the cell). **UNSUPPORTED** = honest
> gap (R6: diagnostic, never a silent stub).
>
> Targets: **JVM** (compiled bytecode), **Native** (x86_64 ELF; riscv64/
> aarch64 via qemu = follow-up), **Script** (IR interpreter — the direct
> execution target), **KofJS** (GraalJS). Deterministic cases only:
> (a) language without side effects; (b) concurrency with order guaranteed
> by `await`/FIFO (batch 3). What has NON-guaranteed order (fire-and-forget
> without `await`) and what depends on real time (sleep/interval) stay in
> `KofConcurrency2Test`/`KofTimeE2ETest`/`KofMqE2ETest` with loose
> assertions — they do not enter the matrix.

## Matrix (batch 1 — core language)

| Feature | Expected output | JVM | Native | Script | KofJS | Case (ConformanceMatrixTest) |
|---|---|---|---|---|---|---|
| int arithmetic + overflow | `-2147483648` / `-1` / `1` | DONE | DONE | DONE | DONE | `arith` |
| long div/mod | `3333333333` / `4` | DONE | DONE | DONE | DONE | `longdiv` |
| numeric conversion in primitive `n.toInt()/toLong()/toDouble()/toFloat()` (§89, decision 3a) | `true` / `3` / `-2` / `5` / `2.5` | DONE | DONE (out-of-range residual §181 ✅ 13/09) | DONE | DONE (out-of-range residual §181 ✅ 13/09) | `numconv` |
| `Double %` (mod of variables; + NaN/±Inf) | `1.5` / `1.0` / `0.5` / `-1.5` / `NaN` | DONE | DONE (bug 146 ✅ 12/09 `718ae5cf` — `NativeX86Arith` emits the real fmod) | DONE | PARTIAL (test excludes js; §146 shape `JsBackend`) | `doublemod` |
| cast `d as Int` / `L as Int` / `66 as Char` | `9` / `70000` / `66` | DONE | DONE | DONE | DONE | `cast` |
| float println | `0.3333333333333333` / `5.0` / `3.5` | DONE | DONE (bug 44 ✅ 10/09 x86) | DONE | PARTIAL (doc: `5` vs `5.0`) | `floatprint` |
| double println shortest-repr + scientific | `0.30000000000000004` / `1.0E7` / `1.0E-5` / `33.333333333333336` / `0.33333334` / `1.0E20` / `NaN` / `0.001` / `1.0E-4` / `3.4028235E38` / `-0.0` | DONE | DONE (bug §180 ✅ 14/09 x86_64 — `kof_dtoa` loop `%.*e`+`strtod` for the shortest round-trip + Java reformat; Float keeps its own form; **FLT001 CLOSED on riscv/aarch 15/09** — the same `kof_dtoa` ported as slice `RtB45` consumes libc `snprintf`/`strtod` via the on-demand dynamic link, `NativeRiscvDtoaTest` oracle both arches) | DONE | PARTIAL (doc: `Number.toString` does not emit `.0`/scientific at the JDK threshold) | `doubleprint` |
| infinity/NaN println+String.valueOf | `Infinity` / `-Infinity` / `NaN` | DONE | DONE (bug 44 residual ✅ 11/09 x86) | DONE | PARTIAL (doc: `5` vs `5.0`) | `infinityprint` |
| String.equals(non-String) → false | `true` / `false` / `false` / `false` | DONE | DONE (bug 100 ✅ 11/09 x86 — was SIGSEGV/empty) | DONE | DONE (fold `false`) | `equalsfold` |
| indexOf/lastIndexOf/startsWith `from` | `-1` / `3` / `-1` / `2` / `true` / `false` | DONE | DONE (bug 102 ✅ 11/09 x86 — was ignored) | DONE | DONE (native) | `searchfrom` |
| record in collection (contains/set/map/toString by content) | `true` / `true` / `7` / `[Point[x=1, y=2]]` | DONE | PARTIAL (was LINK_FAIL §104b-i ✅ FIXED 11/09; what BLOCKS NOW is §104b-ii — equals/hashCode by CONTENT of record in native asm storage, bugfixer lane, LARGE unit; vtable/link already ok) | DONE (bug 104a ✅ 11/09 — KofObj without override → identity) | DONE (bug 104c ✅ 11/09 — `kofValEq` content for record via synthetic `.equals`; Map/Set/List lookup by content) | `objmethods` |
| `println(collection)` container format | `[1, 2]` / `[a, b]` / `[1.5, 2.25]` / `{k=1}` / `[1]` / `[Point[..], Point[..]]` / `[[1], [2]]` | DONE | PARTIAL (**bug 107 FIXED 12/09** — was raw pointer/vtable `-1` garbage; `kof_{list,set,map}_to_string` + compile-time tag on the 3 native targets, JVM golden byte-identical for int/string/bool/long/char scalars and Map/empty; record/nested=`?` remain HONEST until §104b-ii and FP-collection on cross now WORKS (tags 4/5 → `kof_double_to_string`/`kof_float_to_string`, FLT001 closed 15/09); `Native{,Riscv64,Aarch64}E2ETest#*CollectionPrint*`) | DONE | DONE (bug 107-JS ✅ 11/09 — `kofFormat` mirrors ArrayList/HashMap/HashSet.toString) | `collprint` |
| non-record class: `equals`/`==` by identity | `false` / `true` / `false` / `true` | DONE | DONE (bug 104b-i ✅ 11/09 — was LINK_FAIL: inherited `Object.equals` without symbol; identity synthesis) | DONE | DONE (native JS) | `classequals` |
| `map.get` with primitive value (unbox guard) | `true`/`true`/`8`/`9000000001`/`true`/`97`/`false` | DONE (bug 109 ✅ 11/09 — was JVM CRASH: `Boolean.intValue()Z` → `NoSuchMethodError`; `unboxMethodName` only handled ClassType; §104b-ii JVM 11/09: char stored `Integer` but unbox called nonexistent `charValue()/()C` — unbox now consistent with the box) | DONE (bug 104b-ii ✅ 11/09 — was SIGSEGV/`a`: `println(char-in-collection)`; `ExpressionPrintLowerer` mapped char→Int only for raw CHAR, never `Nullable(CHAR)`; cast `as Char` pinned `Unknown` in `mapOf` — SemExpressionTyper now mirrors the ExpressionTyper repair) | DONE | DONE (`d*2`→predicate `d>1.0` to avoid colliding with floatprint §44) | `mapgetprim` |
| signed zero (`-0.0` literal/negated/folded) | `0.0` / `-0.0` / `-0.0` / `-0.0` / `-0.0` / `true` | DONE (bug 110 ✅ 11/09 — was `0.0`: `emitLoadDouble/Float` collapsed -0.0 into `DCONST_0` via IEEE `value == 0.0`) | DONE (raw bits guard always present) | DONE | PARTIAL (doc §44: JS `String(-0.0)` = `0` without `.0`) | `negzero` |
| `split` removes trailing-empties (Java, not JS) + `substring(0,0)` | `1` / `0` / `2` / `1` / `3` / `0` / `llo` / `0` | DONE (Java oracle) | DONE (bug 111 ✅ 11/09 x86 — was `2`/`3`/`3`; trim in `.Lkof_split_done`; substring sentinel 0→-1) | DONE | DONE (bug 111 ✅ 11/09 — was trailing-preserve JS; helper `kofSplit`) | `strsplit` |
| `put`/`remove` return prev (null-safe for primitive) + `set.add` | `false/true/3/true/false/1/2/2/0/0` | DONE (bug 112 ✅ 11/09 — was **VerifyError** in put and **NPE** in remove-miss; `emitPrevValueUnbox` guard) | DONE (bug 112 ✅ 11/09 — remove-miss **SIGSEGV**: `kof_map_remove` miss route gave 3 popq for 5 pushq; unbox guard already existed) | DONE (bug 112 ✅ 11/09 — `s.add` already-present returned **true** (add+contains) and NPE on remove-miss; `prevOrDefault` + real `HashSet.add`) | DONE (bug 112-JS ✅ 11/09 — was `null`: absent prev wrapped in `?? default` in the map handler + `KofPop` extended to preserve the wrapped side-effect) | `mapmutret` |
| string unicode length/charAt | `4` / `233` / `café!` | DONE | DONE (bug 43 ✅ 10/09 x86) | DONE | DONE | `unicode` |
| string unicode astral (surrogate pair) | `4` / `55357` / `56832` / `98` | DONE | DONE (bug 43 ✅ 10/09 x86) | DONE | DONE | `unicode-astral` |
| string unicode substring (code units, well-formed boundaries) | `afé` / `é` / `😀` / `3` / `b` | DONE | DONE (bug 43 ✅ 10/09 x86) | DONE | DONE | `unicode-substring` |
| string unicode indexOf/lastIndexOf (code units) | `6` / `-1` / `4` / `3` | DONE | DONE (bug 43 ✅ 10/09 x86) | DONE | DONE | `unicode-indexof` |
| string ops split/toLowerCase/trim | `4` / `hello world` / `x\|` | DONE | DONE | DONE | DONE | `strops` |
| `String.isEmpty()` (+ compound-trim, `!isEmpty` in if) | `false` / `false` / `true` / `ok` | DONE (bug 145 ✅ 12/09 `718ae5cf` — `isEmpty` in the String registry) | DONE | DONE | DONE | `strisempty` |
| map put/get/size | `1` / `2` | DONE | DONE | DONE | DONE | `map` |
| `Set.remove` by value (String at index 0 + Int) | correct dedup + `true`/`false`/size | DONE | DONE (bug 129 ✅ 11/09 — was silent-corruption: `kof_set_remove` passed the TAG (r13) as index to `kof_list_remove`, erasing the neighbor) | DONE | DONE | `setdedup` |
| `Map<Int,V>` put/get/remove + get-miss `null` | `um`/`dois`/`2`/`um`/`null` | DONE | DONE (bug 123 ✅ 11/09 — was **SIGSEGV**: `kof_map_find` with `kof_string_equals` on an Int key → pointer; key tag in header off 40, mirroring the Set) | DONE (bug 124 ✅ 11/09 — was NPE "value is null": `println` of the miss lowered `valueOf(Unknown)`, the `invokeExternal` scorer tied `valueOf(char[])`/`valueOf(Object)` and picked the array) | DONE | `mapint` |
| wrong key as query ARG (Map/Set/List) | `null`/`false`/`false`/`0` | DONE | DONE (bug 126 ✅ partial 11/09 — was SIGSEGV on the wrong-type arg; tag is a CONJUNCTION elem×arg: String-equals only when both are String, otherwise raw cmpq = miss like the JVM) | DONE | DONE (bug 127 ✅ 12/09 — get-miss of a primitive value wraps `?? default` in lowering; Bool-miss now `false`, not `0`) | `wrongkey` |
| `println(f())` w/ `T? f()` null + `f()==null` direct (primitive) + null branch if/switch (`Int? f() = if(c) x else null`) + nullable `"a" + f()` | `null` / `true` / `null` / `6` / `true` ×3 + `false`(map-miss) + `true` + `7/null/null` + `null` + `anull/nullb` | DONE (D-NULL-INTENT/N1 ✅ 15-16/09, e04f10ff — supersedes bug 125/opção A, REVOKED: `Nullable(primitive)` is now genuinely boxed (`Ljava/lang/Integer;` etc.) and carries real `null` instead of collapsing to the primitive default; `== null`/`!= null` is a real reference compare; map-miss (`mapOf(...).get(k)`) stays unchanged at `false`, SG-008/frozen, distinguished by call-shape not type) | PARTIAL (N2 not implemented yet — Native still collapses `Nullable(primitive)` to the primitive default, pre-N1 behavior; tracked as a separate queue item, not a regression) | DONE (D-NULL-INTENT/N1 ✅ 15-16/09 — same shared IR fix as JVM, interpreter already null-tolerant by construction) | DONE (D-NULL-INTENT/N1 ✅ 15-16/09 — same shared IR fix as JVM; bug 139 fix from 12/09 unaffected) | `nullableprint` |
| list empty/isEmpty/contains | `true` / `0` / `false` | DONE | DONE | DONE | DONE | `emptylist` |
| blessed widening §126 on pinned List write (`listOf(1L).add(3)` / `.set`) | `3` / `4` / `3` | DONE (bug 143 ✅ 12/09 — was VerifyError: the store box used the PINNED type over a raw width-1 arg; `coerceStoreWiden` applies the §121/array-store to collection) | DONE (bug 143 ✅ 12/09 — native heap already 8-byte, I2L is a semantic no-op; printed correctly) | DONE (bug 143 ✅ 12/09 — already gave `[1,2,3]`; now 4/4 identical) | DONE (bug 143 ✅ 12/09) | `collwiden` |
| widening in the VALUE of a pinned `Map.put` (`mapOf(_,Long).put(_,Int)`) | `2` / `1` | DONE (bug 143 ✅ 12/09 — was VerifyError/CCE on get; coercion + paramTypes adjusted to the pinned type) | DONE (bug 143 ✅ 12/09 + bug 142 ✅ 12/09 — native widening was already a no-op, but `put` as a statement discarded the prev `Long` with a 16-byte POP2 over 1 pushed qword → stomped local `m` = SIGSEGV; native POP2 now discards 1 qword) | DONE (bug 143 ✅ 12/09) | DONE (bug 143 ✅ 12/09) | `mapwiden` |
| empty `mapOf()` + 1st `put` of `Long` value (pin-aligns, §157) | `9000000001` / `1` | DONE | DONE | DONE | DONE | `mapputlong` |
| `Long`/`Double` expression discard (native POP2) | `2` / `false` / `false` | DONE | DONE (bug 142 ✅ 12/09 — was SIGSEGV: `m.put(_,2L)` statement and `d==null`/`x==null`; POP2 inherited from the JVM discarded 2 qwords on a stack where every value is 1 qword) | DONE | DONE | `longdiscard` |
| `null == null` / `!=` | `true` / `false` | DONE | DONE | DONE | DONE | `nulleq` |
| if-expr null short-circuit | `iguais` / `nao-ne` | DONE | DONE | DONE | DONE | `nulleqshortcut` |
| set dedup/contains | `3` / `true` / `false` | DONE | DONE | DONE | DONE | `setdedup` |
| nested if-expression | `small` | DONE | DONE | DONE | DONE | `nestedif` |
| switch-expression `case ->` | `three` | DONE | DONE | DONE | DONE | `switchexpr` |
| heterogeneous if-expr Int/String (issue #57) | `1` | DONE | DONE | DONE | DONE (bug 69 fixed) | `ifexpr-heterogeneous-direct` |
| heterogeneous if-expr Int/String, false branch (§205) | `s` | DONE | DONE | DONE | DONE | `ifexpr-heterogeneous-direct-else` |
| heterogeneous switch-expr Int/String (issue #57) | `1` | DONE | DONE | DONE | DONE (bug 69 fixed) | `switchexpr-heterogeneous-direct` |
| heterogeneous switch-expr Int/String, false branch (§205) | `s` | DONE | DONE | DONE | DONE | `switchexpr-heterogeneous-direct-else` |
| heterogeneous switch-expr multi-arms (§205) | `2` | DONE | DONE | DONE | DONE | `switchexpr-heterogeneous-multi` |
| heterogeneous switch-expr multi-arms, default (§205) | `d` | DONE | DONE | DONE | DONE | `switchexpr-heterogeneous-multi-default` |
| heterogeneous if-expr Int/Long (§70, join crash) | `1` | DONE | DONE | DONE | DONE (bug 69 fixed) | `ifexpr-intlong-direct` |
| heterogeneous if-expr Long/Double (§70) | `2` | DONE | DONE | DONE | DONE (bug 69 fixed) | `ifexpr-longdouble-direct` |
| `if` with `throw` branch + `else` (method epilogue after the if) | `else` / `after` | DONE | DONE | DONE | DONE (bug 147 ✅ 12/09 `718ae5cf` — `JsIfThrowElse` isolates the epilogue) | `ifthrowelse` |
| heterogeneous if-expr Int/null (§70) | `1` | DONE | DONE | DONE | DONE (bug 69 fixed) | `ifexpr-intnull-direct` |
| for-in + break/continue | `4` | DONE | DONE | DONE | DONE | `breakcont` |
| record `==` content + toString + accessor | `true` / `P[x=1, y=2]` / `1` | DONE | DONE | DONE | DONE | `record` |
| record `hashCode()` equal | `true` | DONE | DONE (bug 42 Native fixed) | DONE | DONE (bug 42 JS fixed `1ecfb3d`) | `recordhash` |
| record with String field `==` by content (null-safe) | `true` / `false` / `true` / `false` | DONE | DONE (bug 114 ✅ 11/09 Native — was **pointer** (`S("ab")==S("ab")` false); String field now via `kof_string_equals`; nested record field/hash-ref/collection remain §104b-ii) | DONE | DONE | `recordstrfield` |
| lambda filter/map/reduce | `90` | DONE | DONE | DONE | DONE | `lambdachain` |
| function type as generic argument `List<(Int) -> Int>` (§155) | `6` | DONE | DONE | DONE | DONE | `fntypegeneric` |
| heterogeneous list of lambdas (same signature, §156) | `10` / `6` | DONE | DONE | DONE | DONE | `lambdalisthet` |
| cast to function type `x as () -> Int` (§127-JVM) | `true` | DONE | DONE | DONE | DONE | `castfn` |
| class method overload by signature (§131, decision 10a; instance only — the STATIC-overload face is NOT covered: call omitted from IR, §227/#235 open) | `42` / `7` | DONE | DONE | DONE | DONE | `methodoverload` |
| method overload with SAME arity and different types (§131-residual) | `42` / `abab` | DONE | DONE | DONE | DONE | `methodoverloadtype` |
| wide parameter (`Long`) not first in the interpreter (§163) | `10000000002` / `10000000007` / `10000000000` / `10000000005` | DONE | DONE | DONE | DONE | `wideparams` |
| mutable capture lambda | `3` | DONE | DONE | DONE | DONE | `lambdacapture` |
| 2D/3D array: alloc + length + store/load + zero-fill | `60`/`3`/`2`/`3`/`0`/`7`/`2`/`2`/`9`/`0` | DONE | DONE (bug 113 ✅ 11/09 x86 — `new Int[a][b]` allocated NOTHING: `KofNewMultiArray` fell into `default->{}` → SIGSEGV; now recursive `kof_multi_alloc`; riscv/aarch faces ✅ 11/09 — slice B37 + cross routing, JVM golden under qemu) | DONE (B37, port 0.3.0→0.4.0 ✅) | DONE (translator, ✅) | `array2d` |
| store `Int` in `Long[]` slot (widening, 1-D and 2-D) | `9` / `3` / `0` | DONE (bug 121 ✅ 11/09 — was **frame crash** in `COMPUTE_FRAMES`: the conversion block of `ExpressionAssignmentLowerer` was an `if {}` that only commented the promise, never emitted `I2L`) | DONE | DONE | DONE | `arrlongstore` |
| store in `Byte[]`/`Short[]` OUT of range (signed 8/16-bit narrowing) — §184 | `-126` / `4464` | DONE | DONE | DONE | DONE (bug §184 ✅ 15/09 — `kofArraySet` now narrows: kind byte→i2b, short→i2s; `b[0]=130`→`-126`) | `narrowarr` |
| store into `Char[]`/`Bool[]` element — §185 | `65` / `66` / `true` / `false` | DONE | DONE | PARTIAL (bug §185 — interpreter: `coerceFor` returns `Integer`; `Array.set(char[]/boolean[],…)` throws `argument type mismatch`, exit 1) | DONE | `chararr` |
| `Char[]` out of range (`c[0]=70000`, `c[1]=-1`) in 1-D and 2-D + control `Short[] -1` — §187 | `4464` / `65535` / `4464` / `65535` / `-1` | DONE | DONE | PARTIAL (bug §185 — crash on the `Char[]` store) | DONE (bug §187 ✅ 15/09 JS face — `kofArraySet` kind char→`& 0xFFFF`; `c[0]=70000`→`4464`, `c[1]=-1`→`65535`) | `charnarrow` |
| `static` initializer of CONSTANT expression (`-1`, `2+3`, `-7L`, `-1.5`, `"a"+"b"`, `!false`) — §186 ✅ 13/09 | `-1` / `5` / `-7` / `-1.5` / `ab` / `true` / `7` | DONE | DONE | DONE | DONE | `staticinit` |
| static field + bump | `1` / `2` / `2` | DONE | DONE (bug 41 fixed 07/09) | DONE | DONE | `staticfield` |
| static field `+=` | `2` / `4` / `4` | DONE | DONE (bug 41) | DONE | DONE | `staticpluseq` |
| concat string+num (order) | `n=42` / `3x` / `x12` | DONE | DONE | DONE | DONE | `concat` |
| boolean logic + comparison | `false` / `true` / `false` / `true` | DONE | DONE | DONE | DONE | `boollogic` |
| bitwise & \|\| ^ << >> | `2` / `7` / `5` / `16` / `64` | DONE | DONE | DONE | DONE | `bitwise` |
| bitwise/shift with mixed `Long` + 64-bit overflow (§167 ✅ 13/09) | `1` / `7` / `6` / `5` / `4294967295` / `320` / `2` / `-9223372036854775808` / `705032704` … | DONE | DONE | DONE | DONE | `bitwise` (extended) |
| increment `++`/`--`/compound in `Long`/`Double`/`Float` + array element (§173 ✅ 13/09) | `2` / `3` / `2` / `2.5` / `3.5` / `2.5` / `2.5` / `6` / `33` / `35` / `1.25` / `-9223372036854775808` / `8` / `9` / `39` / `8` / `8` / `7` | DONE | DONE | DONE | DONE | `increment` |
| compound shift `<<=`/`>>=`/`>>>=` with wide RHS (§172 ✅ 13/09) | `24` / `6` / `2147483644` / … / `1099511627776` | DONE | DONE | DONE | DONE | `compound-shift` |
| stdlib kof.math (S1: clamp/abs/sign/min/max/isEven/isOdd/isZero + `==true`/`==false` §93) | `10` / `0` / `7` / `-1` / `3` / `8` / `true` / `false` / `true` / `true` / `false` | DONE | DONE | DONE | DONE | `stdmath` |
| stdlib kof.math (S1b: sqrt — first Double; Bool comparisons, NaN in <0 = IEEE; riscv/aarch = B32 `fsqrt.d`, MATH001 closed 11/09; §94 closed 13/09 — interp now IEEE) | `true` / `true` / `true` / `true` / `false` / `true` | DONE | DONE | DONE | DONE | `stdsqrt` |
| stdlib kof.math (S1b.1: lerp/percentage/isInteger/isDecimal — pure Double, SSE2; deterministic subset, NaN only in the compiled ones via KofMathTest; riscv/aarch = B32, MATH001 closed 11/09) | `true` ×15 | DONE | DONE | DONE | DONE | `stdmathdouble` |
| stdlib kof.math (S1b.2: `pow` — first libm in native x86 `pow@PLT` + `-lm`; JVM/JS `Math.pow`; riscv/aarch = MATH001, static link without libc) | `true` ×10 | DONE | DONE | DONE | DONE | `stdmathpow` |
| stdlib kof.math (S1b.3: `roundTo(value, decimals)` — half-away-from-zero by deterministic decimal scaling, no libm (`p=10^\|d\|` by repeated multiply → byte-identical 5 targets); decimals may be negative (rounds to tens/hundreds); Bool via == bug 44; cross-arch = B32 under qemu in KofMathTest.roundToCrossArch; **arithmetic contract**: `2.675` → `2.68`) | `true` ×10 | DONE | DONE | DONE | DONE | `stdmathround` |
| stdlib kof.math (S13a: `parseInt`/`parseLong`/`parseDouble` — namespace facade over the EXISTING runtime fns `kof_string_to_*` in the 4 backends (rule 2, zero new runtime); JDK contract with trim, invalid/overflow throws; Long > 2^53 proves real Long after §81 BigInt; Double via == Bool bug 44; cross-arch = B30/B31, byte-identical golden under qemu in KofMathTest.parseCrossArch) | `42` / `-7` / `13` / `0` / `-2147483648` / `9007199254740993` / `-9223372036854775807` / `true` ×3 / `T1`–`T5` | DONE | DONE (narrow coverage — hex-float/`d`-suffix rejected, bug 82 boundary) | DONE | DONE (narrow coverage — hex-float/`d`-suffix rejected) | `stdmathparse` |
| stdlib kof.math (S13b: `parse*OrDefault` — briefing §43: parse failure RETURNS the default, never throws; backends = JVM try/catch, JS wrapper, x86 wrapper w/ handler in exc_chain, riscv B41 + aarch translator; Int literal in Long param proves I2L widening of KofStd; `""` line of Double outside the golden = §169; **cross riscv/aarch HANGS on the 1st `parseDoubleOrDefault` after a throwing OrDefault = §192 (2-line repro + loop PC in known-bugs; DONE below = JVM/x86/JS/Script)**) | `42` / `-1` / `7` / `15` / `3` / `9007199254740993` / `-5` / `8` / `true` ×3 | DONE | DONE | DONE | DONE | `stdmathparseord` |
| stdlib kof.strings (S2a: isAlpha/isNumeric/isAlphaNumeric/isAscii/isUpper/isLower/count + `==true` §93) | `true` / `false` / `false` / `true` / `false` / `false` / `true` / `false` / `true` / `true` / `true` / `false` / `true` / `false` / `2` / `1` / `true` | DONE | DONE | DONE | DONE | `stdstrings` |
| stdlib kof.strings (S2b: capitalize/reverse/repeat/truncate/pad — ASCII) | `Hello world` / `1abc` / `321cba` / `kayak` / `ababab` / `hello` / `abc` / `007` / `ab---` | DONE | DONE | DONE | DONE | `stdstrings2b` |
| stdlib kof.validation (S12/S12b: formatCpf/formatCep/formatCnpj — BR punctuation, lenient face; formatPis does NOT enter — ambiguous mask = decision) | `529.982.247-25` / `123` (no-op) / `01310-100` / `34.546.401/0001-63` | DONE | DONE | DONE | DONE | `formatBr*`/`formatCnpj*` (KofValidationTest; riscv/aarch under qemu, assert) |
| stdlib kof.strings (S11: uncapitalize — mirror of capitalize, ASCII) | `hello World` / `hELLO` / `1abc` / `hello` | DONE | DONE | DONE | DONE | `uncapitalizeAllTargets` (KofStringsTest; riscv B7 + aarch under qemu) |
| stdlib kof.strings (S2b.4: toCamelCase/toPascalCase/toSnakeCase/toKebabCase/slugify — word-split HTTPServer/XMLParser) | `http_server` / `xml_parser` / `helloWorld` / `HelloWorld` / `hello-world` / `hello-world-42` | DONE | DONE | DONE | DONE | `stdstrings2b4` |
| stdlib kof.validation BR (S5: isCpf/isCnpj/isCep/isPis + S12c isNis — arithmetic weights, mod-11 by subtraction; + `==true`/`==false` §93) | `true` / `false` / `true` / `false` / `true` / `false` / `true` / `false` / `true` / `true` / `false` / `false` / `false` / `true` / `true` | DONE | DONE | DONE | DONE | `stdvalidation` |
| stdlib kof.validation network (S6a: isIpv4/isMac/isPort — dotted-quad without leading zero; MAC 6 hex sep : or - consistent; port 1..65535) | `true` / `false` / `false` / `true` / `false` / `true` / `false` | DONE | DONE | DONE | DONE | `stdvalidationnet` |
| stdlib kof.validation Luhn (S6b: isCreditCard — extracted digits, 12..19, Luhn sum %10; 20+ digits => false) | `true` / `true` / `true` / `false` / `false` / `false` | DONE | DONE | DONE | DONE | `stdluhn` |
| stdlib kof.validation IPv6 (S6b.3: isIpv6 — RFC 5952 subset; '::' at most once; no mixed form/zone) | `true` / `true` / `true` / `false` / `false` / `false` | DONE | DONE | DONE | DONE | `stdipv6` |
| stdlib kof.net (S8: 6 URI fields v1 + query* facade) | `https\|host.io\|8443\|/p\|q\|f` / `/only/path\|onlyquery` / `a%20b%26c%3D1` / `a b&c=1` | DONE | DONE | DONE | DONE | `stdnet` |
| stdlib kof.strings unescapeHtml (S3.1b: 5 named + &#DDD;/&#xHH;→UTF-8; other & LITERAL; 0/surrogate/overflow LITERAL) | `a&b` / `<x>` / `café` / `☃` / `&&` / `&notreal;` | DONE | DONE | DONE | DONE | `stdunescape` |
| stdlib kof.strings whitespace (S3.2: removeWhitespace/normalizeWhitespace — WS=9..13+32; >=128 non-WS; collapse to 1 space) | `abc\|Caféé` / `a b\|a b` / `\|[]` | DONE | DONE | DONE | DONE | `stdws` |
| stdlib kof.strings escapeHtml (S3.1: 5 entities; >=128 copy; null/"" => original) | `a&lt;b&gt;&amp;&quot;&#39;c` / `Café &amp; ç` / `&amp;amp;lt;` / `&lt;a href=&quot;u&quot;&gt;y&lt;/a&gt;` | DONE | DONE | DONE | DONE | `stdescape` |
| stdlib kof.strings escapeJson (S3.1c: JSON literal body RFC 8259 — backslash doubles, quotes escape, ctrl 2-char/backslash-u, rest copy; null/"" => original; golden 5 backends in KofStringsTest) | `plain` / `quote \" inside` / `back\\\\slash` / `a\\u0001b` | DONE | DONE | DONE | DONE | KofStringsTest |
| stdlib kof.validation domain (S6c: isDomain — RFC 1123 labels, TLD>=2 letters, >=2 labels; v1 without trailing dot/IDN) | `true` / `true` / `false` / `false` / `false` / `false` | DONE | DONE | DONE | DONE | `stddomain` |
| stdlib kof.time (S7: isLeapYear/daysInMonth/dayOfWeek/daysBetween — civil calendar; year<1 or >9999 or nonexistent date => false/0; day 1=Mon..7=Sun) | `true` / `false` / `true` / `false` / `29` / `28` / `30` / `0` / `4` / `3` / `0` / `60` / `-60` / `0` | DONE | DONE | DONE | DONE | `stdtime` |
| stdlib kof.* (S10–S12b, S3b-ext, S7-ext: kof-script × compiled JVM parity — random facade, BR format, uncapitalize, isUuid, isWeekend) | (contract asserts + golden; non-deterministic only via facade) | DONE | DONE | — | — | `KofScriptStdlibParityTest` (kof-script, 5) |
| stdlib kof.uuid (S3b-ext: isUuid — shape RFC 4122, 8-4-4-4-12 hex, hyphens 8/13/18/23; without checking version/variant) | `true` / `true`(upper) / `false`(no hyphen/size/g/empty) | DONE | DONE | DONE | DONE | `isUuid*` (KofUuidTest; riscv/aarch assert under qemu) |
| stdlib kof.time (S7-ext: isWeekend — dayOfWeek>=6, wrapper in the 5 targets; invalid date => false) | `true`(Sat) / `false`(Wed) / `false`(invalid) | DONE | DONE | DONE | DONE | `calendar*` (KofTimeE2ETest; riscv/aarch assert under qemu) |
| stdlib kof.time (S7a/b/c: addDays/diffDays on ISO String date — strict parse YYYY-MM-DD, invalid => ""/0; JVM/java.time + JS civil algorithm without Date + x86 asm `RuntimeTimeIso` + riscv/aarch **B33** (TIME002 closed 11/09)) ⁴ | `2024-02-29` / `2023-03-01` / `2025-01-01` / `2023-12-31` / `''` / `''` / `60` / `-60` / `0` | DONE | DONE ⁴ | DONE | DONE | `stdtime2` |
| stdlib kof.time (S7e: todayIso/formatDateIso/isToday — UTC-only (D1); zero-DSL format, invalidity => "" (D4); isToday = equality w/ UTC date of now() (D5); JVM/java.time + JS civil + x86 `RuntimeTimeIso` + riscv/aarch **B33-ext**; todayIso by format — day rolls over) | `10` / `2026-09-13` / `2024-02-29` / `''`×5 / `false`×2 / `3`/`4`/`2`/`2` | DONE | DONE | DONE | DONE | `stdtime3` |
| stdlib kof.time (S7f: hoursBetween(y,m,d,H,y,m,d,H) — symmetric floor (D3: truncated to zero, consistent daysBetween); invalid date/hour outside 0..23 => 0; no float (FLT001); JVM + JS civil + x86 generic emit 7+ args FIXED + riscv **B33-ext**; aarch translator) | `26` / `-26` / `23` / `1` / `0`×3 / `24` / `0` / `8760` | DONE | DONE | DONE | DONE | `stdtime4` |
| stdlib kof.time (S7g: parseDateIso(STR) -> Int serial daysFromEpoch — strict parse YYYY-MM-DD, invalid ⇒ 0 (D4); SAME serial as hoursBetween/daysBetween (recomposition closes); JVM + JS civil + x86 `.Lka_parse2`/`.Lkd_epoch` + riscv **B33-ext**; aarch translator) | `0` / `20709` / `19782` / `-719162` / `2932896` / `0`×4 / `20709` | DONE | DONE | DONE | DONE | `stdtime5` |
| STRICT ISO parse rejects field with sign (`+999`/`+1`) — §182 ✅ FIXED 13/09 ("strict" contract declared in S7a/S7g; JVM/Script digit by digit, JS single strict helper; Native was the reference) | `0` / `0` / `0` / `20454` / `` / `0` | DONE | DONE (reference) | DONE | DONE | `parseisostrict` |
| stdlib kof.time (S7h: tzOffsetSeconds() — HOST timezone as explicit getter (D1); JVM `ZoneId`/JS `getTimezoneOffset` inverted (parity by JVM oracle on host); **Native = honest gap TIME003** — refuses with diagnostic (R6); non-deterministic across hosts, fixed cell JVM oracle) ⁵ | `0` (mod 60) / `true` / `<oracle>` | DONE | PARTIAL ⁵ | DONE | DONE | `stdtime6` |
| cast Double/Float as Int/Long OUT of range/NaN/Inf — §181 ✅ FIXED 13/09 (JLS 5.1.3 saturation: NaN ⇒ 0, >MAX ⇒ MAX, <MIN ⇒ MIN; x86 guard ucomisd NaN-1st + clamp; JS helpers `kofD2I/kofD2L` (registerRuntime); riscv feq NaN-check + clamp; aarch translator) | `2147483647` / `0`×2 / `2147483647` / `-2147483648` / `9223372036854775807` / `true` / `100` / `-100` / `2147483647` / `-1` | DONE | DONE | DONE | DONE | `castrange` |
| stdlib kof.encoding (S4: hex + base64 + url + base64url — UTF-8 by bytes) | `4869` / `Hi` / `636166c3a9` / `café` / `TWFu` / `café` / `a%20b` / `café` / `ZmImTy0-Zg` / `fb&O->f` / `E` | DONE | DONE² | DONE | DONE | `stdenc` |

> ¹ **STRN001 CLOSED 09/09:** joinWords ported to riscv64 (slice B15) + aarch64
> (same translated asm) — byte-by-byte parity with x86_64 proven by diff of the
> oracle golden on qemu (16 vectors, incl. UTF-8 delimiters `>=128`).
> `KofStringsTest.wordConvertersClosedOnCrossArch`.

> ⁵ **TIME003 (13/09, D-STDLIB D1):** `tzOffsetSeconds` = HOST timezone —
> JVM/Script/KofJS DONE (parity by JVM oracle on host); **Native (x86/
> riscv/aarch) PARTIAL by design**: without `TZ`//etc/localtime in asm,
> implementing it would be accidental parity (D1 forbids it). The NATIVE
> backend REFUSES with diagnostic `TIME003` (R6 — honest gap, never a "faked
> 0"). Closure = TZ//etc/localtime parser in asm (own scope, general
> queue).
CLOSED 11/09 (riscv64/aarch64)**: `addDays`/`diffDays` run on the 5 targets —
> JVM/Script/JS + native **x86** (`RuntimeTimeIso`) + riscv64/aarch64 (slice
> **B33**: `.Lu8_parse2`/`.Lu8_civil`/`.Lu8_put*` 1:1 port of the x86 spec reusing
> `kdv_valid`/`kdv_epoch` from B14; aarch via translator). Proof:
> `KofTimeE2ETest.timeAddDaysDiffDaysJvmShapeAndCrossArch` (execution golden) +
> `timeAddDaysDiffDaysCompilesOnAllTargets` (always-green compile gate, without qemu) — byte-identical
> golden (9 lines) under qemu-riscv64 + qemu-aarch64, inverting the
> old TIME002 gate (NET001 precedent: x86 closes first, cross later).
> riscv LESSONS from the port: `call` overwrites `ra` (jalr, not stack) — helper
> that ends in `call h; ret` must do a **tail-jmp** `j h`; and `kdv_valid`
> clobbers `s0` (daysInMonth) — no live value in `s0` between calls.
> ³ **NET001 CLOSED 09/09:** `net.*` runs on the 3 natives — x86 (RuntimeUri) +
> riscv64 (slice B24) + aarch64 (same translated asm); byte-by-byte parity
> in the 17 oracle vectors (`KofNetTest.netOnCrossArch`, qemu).

> ² `encoding.hex*`/`encoding.url*` (B10/B11) and `encoding.base64*`/`base64Url*`
> (B23, **ENC002 closed 09/09** — riscv port with arithmetic alphabet + tolerant
> decode, single spec from x86/JVM/JS; `KofEncodingTest.base64RunsOnCrossArch`
> proves riscv+aarch under qemu) run on the 3 natives + JVM + JS + Script.
| stdlib kof.uuid (S3b: v4 — non-deterministic, NO matrix case) | shape `xxxxxxxx-xxxx-4xxx-[89ab]xxx-xxxxxxxxxxxx` | ✅ assert | ✅ assert¹ | ✅ | ✅ assert (+riscv/aarch qemu) | `KofUuidTest` 4/4 |
| stdlib kof.uuid (S3b.1: isUuid — shape predicate 8-4-4-4-12, lower/upper hex, version/variant not checked; riscv/aarch = slice B25, UUID001 closed in the beta→main merge 10/09) | `true` / `true` / `false` / `false` / `false` / `false` / `true` | DONE | DONE | DONE | DONE | `stduuidform` |
| stdlib kof.uuid (S3b.2: v7 — RFC 9562 time-ordered, NO matrix case; riscv/aarch = UUID002) | shape `xxxxxxxx-xxxx-7xxx-[89ab]xxx-xxxxxxxxxxxx` | ✅ assert | ✅ assert | ✅ | ✅ assert | `KofUuidTest` (v7) |
| stdlib kof.random (S10/S10a/S10b — non-deterministic, NO matrix case) | contract `0<=randomInt(b)<b` / `randomBoolean∈{0,1}` / `randomString: len==n, chars∈alphabet` + main face `double∈[0,1)` / `hex: 2n chars, n<=0→null (JVM/JS; x86 →""` pre-existing from the crypto lane) + lenient edges (`b<=0→0`) | ✅ assert | ✅ assert | ✅ | ✅ assert (+riscv/aarch qemu) | `KofRandomTest` 12/12 |

> `random.*` does not enter the equality matrix (entropy — same reason as uuid):
> parity proven by CONTRACT ASSERTS on the 5 targets (JVM SecureRandom, JS
> kof_platform/crypto, x86/riscv/aarch getrandom(2)). **S10a 09/09** (beta):
> `randomInt`/`randomBoolean`. **S10b 09/09** (beta): `randomString(n,
> alphabet)` (lenient edge `""`). **S10 10/09** (main, fix §92): `double/
> boolean/int/hex` — `double` reached riscv/aarch in slice B27
> (fcvt.d.l/fdiv + ucvtf/fld translator), closing FLT001 for the random
> family. The two faces coexist in the dispatch (additive retrocompat).
> `randomBytes`/`randomChoice` were S10c (Array/object return
> without precedent in the dispatch layer — DD-STDLIB-01): **DECIDED 13/09
> (option 6a) + IMPLEMENTED in this unit** — `random.randomBytesHex(n)->String`
> (additive alias of `random.hex`, same runtime fn `kof_random_hex`, 5 targets,
> `KofRandomTest.randomBytesHex{Jvm,Js,Native}`); binary `randomBytes`
> remains RESERVED (does not enter); choice = idiom
> `l[random.randomInt(l.size)]` (learn/39 + training/idioms).

> `uuid.v4()` does not enter the equality matrix (entropy): parity proven by
> SHAPE ASSERTS on the 3 testable targets (JVM/Native-x86/JS: length=36,
> hyphens at 8/13/18/23, digit 14='4', digit 19∈{8,9,a,b}, uniqueness of 2
> draws; **riscv64/aarch64 SECN000 CLOSED 09/09** — getrandom(2) via ecall
> (syscall 278, probe on both qemu) in riscv slice B25 + aarch translator;
> KofUuidTest.uuidV4CrossArch runs the shape+uniqueness under qemu on both).
> ¹ variant by MASK on the 5 backends (b[8]=(b[8]&0x3f)|0x80 ⇒ char ∈
> {8,9,a,b}) — x86 parity fixed 09/09 with the closing of SECN000 (before
> it fixed '8', an RFC subset with divergent distribution — rule 5).
>
> `uuid.v7()` (RFC 9562 time-ordered, S3b.2): parity proven by shape
> assertions (length=36, hyphens at 8/13/18/23, digit 14='7', variant 10xx
> digit 19∈{8,9,a,b}, timestamp monotonicity and uniqueness) on JVM,
> Native x86 and JS. UUID002 gate (R6 — never silent) active on riscv64 and aarch64.

> **S2b ASCII:** `capitalize` uses the SAME rule on the 4 targets (byte 0 `a-z`→`A-Z`).
> `reverse` is byte-reverse on Native and UTF-16/UTF-8 on the others — they coincide in ASCII
> (case `stdstrings2b`). Non-ASCII cases: **NAT-STR01** (native UTF-8 gap,
> `plan-stdlib-expansion.md` §5; **registration section:** `known-bugs.md` §161)
> — they do not enter the matrix until fixed (R5/R6).
> **NAT-STR01 extension (10/09, String sweep part 2):** the INSTANCE methods
> `"café".toUpperCase()`/`"CAFÉ".toLowerCase()` are **ASCII-only on
> x86_64** (`RuntimeStringOps` only does ±0x20 on `a-z`/`A-Z`; é→`É` is not touched)
> while JVM/interpreter do full Unicode case-fold ("café"→"CAFÉ").
> Measured 13/09 (4-target parity): JVM/Script/JS `CAFÉ`/`café` × Native x86
> `CAFé`/`cafÉ`. R5 parity broken in a reference method
> (`type-system.md:290`). Latin-1 is
> feasible (é/É have 2 bytes in UTF-8 → length preserved); scripts beyond
> Latin-1 need a Unicode table (multi-session). NOT locked in the matrix until the
> port; smallest repro `sw2b.kf`.
| deep recursion (fact 10) | `3628800` | DONE | DONE | DONE | DONE | `recursion` |
| list add/set/remove | `99` / `4` / `2` / `3` | DONE | DONE | DONE | DONE | `listops` |
| map keys() + iteration | `6` | DONE | DONE | DONE | DONE | `mapiter` |

## Matrix (batch 2 — errors/null/JSON)

| Feature | Expected output | JVM | Native | Script | KofJS | Case |
|---|---|---|---|---|---|---|
| try/catch throw-as-String | `caught:not found: x` / `after` | DONE | DONE | DONE | DONE | `trycatch` |
| try/catch/finally (without throw) | `in` / `fin` / `after` | DONE | DONE | DONE | DONE | `trycatchfin` |
| throw propagating to outer catch | `got:kaboom` | DONE | DONE | DONE | DONE | `throwprop` |
| nested try | `caught-inner:inner` / `end` | DONE | DONE | DONE | DONE (fix 07/09) | `nestedtry` |
| re-throw inside catch | `outer:re:x` / `end` | DONE | DONE | DONE | DONE (bug 52 — collateral fix of bug 45, `c727fee`) | `catchrethrow` |
| null-safety narrowing (`!= null`) | `val=1` / `null-ok` | DONE | DONE | DONE | DONE | `nullnarrow` |
| json.encode int/string/bool | `42` / `"oi"` / `true` | DONE | DONE | DONE | DONE | `jsonenc-int` |
| json.encode list | `[1,2,3]` | DONE | DONE | DONE | DONE | `jsonenc-list` |
| json.encode record | `{"x":1,"y":2}` | DONE | DONE | DONE | DONE | `jsonenc-record` |
| json.encode Map (keys SORTED — §106, decision 2b) | `{"a":1,"b":2}` | DONE | DONE | DONE | DONE | `jsonenc-map` |
| json.decode int/string/bool | `7` / `oi` / `true` | DONE | DONE | DONE | DONE | `jsondec-int` |
| json.decode list of primitive | `3` / `2` | DONE | DONE | DONE | DONE | `jsondec-list` |
| json.decode record | `1` / `2` | DONE | DONE | DONE (fix 07/09) | DONE | `jsondec-record` |
| json.decode list of record | `2` / `2` | DONE | PARTIAL (bug 48 ✅ 09/09 → honest gap **JSN004**: `ExpressionJsonCallLowerer` refuses `List<Record>` on Native at compile time, never a garbage-stub; R6) | DONE (fix 07/09) | DONE | `jsondec-recordlist` |
| json.decode map of record | `2` / `Magician` | DONE (fix #103.1) | PARTIAL (**JSN004**: `ExpressionJsonCallLowerer` refuses `Map<String,T>` on Native at compile time — before it was link-fail `kof_json_decode_Map` nonexistent; R6) | DONE (fix #103.1) | DONE (fix #103.1) | `jsondec-map` |
| json.decode map of string | `2` / `y` | DONE (fix #103.1) | PARTIAL (**JSN004**: same as above) | DONE (fix #103.1) | DONE (fix #103.1) | `jsondec-mapscalar` |

> **Fix 07/09 (interpreter lane):** `json.decode<Record>` in the interpreter
> gave exit 1 + stderr only `Point` (R6) — the generated method `kof_json_decode_Point`
> does `Class.forName`, but in the interpreter the Kof class is `KofObj`. Fixed
> in `KofInterpreterRuntime.decodeKofValue` (mirrors `encodeKof`). The case
> `jsondec-record` went from PARTIAL(Script) → DONE.

## Matrix (batch 3 — DETERMINISTIC concurrency)

> Cases of `spawn`/`await`/`channel` where the order is guaranteed (await
> blocks; FIFO on the same thread). **Fire-and-forget** without `await` is
> NON-deterministic by design (scheduling order) and stays in
> `KofConcurrency2Test` with loose assertions — it does not enter the matrix.

| Feature | Expected output | JVM | Native | Script | KofJS | Case |
|---|---|---|---|---|---|---|
| `spawn fn` + `await` (result) | `42` | DONE | DONE | DONE | DONE | `spawnawait-fn` |
| `spawn { return ... }` (lambda-literal with return, bug 46) + `await` | `42` | DONE | DONE | DONE | DONE | `spawnexpr-return` |
| 2 handles: each `await` returns ITS OWN | `2` / `11` | DONE | DONE | DONE (race fix 07/09) | DONE | `spawnawait-two` |
| same-thread channel (FIFO Int+String) | `s=11` / `ab` | DONE | DONE | DONE | DONE | `channel-samethread` |
| channel send-in-spawn + receive | `42` | DONE | DONE (bug 50 fix 09/09) | DONE | DONE | `channel-spawn` |
| channel 2 sends in spawn + 2 receives | `1` / `2` | DONE | DONE (bug 50 fix 09/09) | DONE | DONE | `channel-spawn-two` |

> **Race fix 07/09 (interpreter lane):** `KofInterpreter.lastReturned` was a
> SINGLE instance field overwritten by each `KofReturn`; with 2 concurrent
> `spawn` (virtual threads) the `await` of handle 2 could read the return of
> handle 1 (reproduced 3/120: `11|11`/`2|2`). Fix: the return lives in
> `KofInterpreterFrame.Frame.returnValue` (per-invocation/per-thread). Proof
> `KofScriptTest.concurrentAwaitReturnsOwnTaskResult` (25 tasks × 8 runs).
> **Bug 51** (state leak of a reused `CompilerDriver` → broken Native
> link) discovered during batch 3: the test uses a fresh driver per
> case (like the CLI — 1 process/compilation).

## Targets outside the matrix (Android / WebAssembly)

The matrix covers the 4 program execution targets (JVM/Native/Script/KofJS).
Two targets named in the platform do **not** enter the cells — each for a
different reason, both honest (R6):

- **`android`** — it is not an execution backend: it is **packaging of the
  entire app**. It compiles in the JVM pipeline and produces an APK via the official
  SDK (d8 → aapt2 → zip → zipalign → apksigner; see `CmdBuild.runApkPipeline`).
  The **language×target** conformance matrix already applies to the JVM bytecode
  that the APK packages; what Android adds is packaging toolchain, not
  semantics. Environment requirement: `ANDROID_HOME` + build-tools 34 — without the
  SDK the CLI reports the error (never simulates the APK). Compilation on the target is
  covered by `AndroidInteropE2ETest` (JVM semantics in `Target.ANDROID`);
  the APK pipeline itself requires the SDK and has no E2E in the suite.

- **`wasm` / `kofwebassembly`** — **WASM001: does not exist yet**. There is no
  `Target.WASM`; `TargetMatrix.frontendGapFor` maps the requested names
  (`wasm`, `kofwasm`, `kofwebasm`, `kofwebassembly`, `webassembly`) to the gap
  **WASM001**, planned in Phase 6 of the platform plan
  (`docs/development/DECISIONS.md` §D-PLATFORM). The two CLI paths
  diagnose the same: `--frontend=wasm`/`kof.toml` →
  `TargetMatrix.parse` with the gap; `--target=wasm` (legacy flag) → the same
  message via `KofCliSupport.parseTarget`. It never compiles as JVM by
  mistake. Proof: `TargetMatrixTest.wasmGapMessagePointsToRealPlanPath` +
  `SelectTargetsTest.wasmFrontendIsHonestGap`.

## Method notes

- **Oracle:** the expected output is that of the **documented behavior**
  (corpus `training/`/`learn/` + `docs/backend-parity.md`), not "what the
  JVM prints" — where the JVM diverges from the document, it is a JVM bug, not
  a target bug.
- **Native x86_64** is the "reference runtime" of Native; riscv64/aarch64
  inherit via `translateRiscvToAarch64` and are covered by
  `NativeRiscv64E2ETest`/`NativeAarch64E2ETest` (42/42 each on 13/09, under qemu) — the follow-up
  of this matrix is to extend the cases here to qemu.
- **Script = JVM in the interpreter** (`runFile(f, Target.JVM)`/`SCRIPT`):
  by construction it runs the SAME optimized frontend IR — Script×compiled-JVM
  divergence is a bug in the interpreter OR in the lowering (both
  have their own parity gate: `KofInterpreterParityTest` 22/22).
- **Crossing out a cell** = edit the matrix + commit; the test is the proof, the
  matrix is the index (R6: the test fails before the doc diverges).
