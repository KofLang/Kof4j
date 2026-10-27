[English](wasm-wasi-plan.md) | [Português](wasm-wasi-plan.pt_BR.md)

**Owner:** ⏹ **ORPHANED 15/10 (audit) — the claim `192.168.15.101:9092` is abandoned: the last plan work is 09/10 and every commit from that lane since 10/10 is release-protocol/native-bug work, NOT this plan; the maintainer-ordered unit 15.4 (the frontend-default flip) is NOT in the code (measured, `AGENTS.md` `last:`). Per `D-PLAN-ONE-OWNER` the front is RELEASED to SEM DONO — any lane re-claims it in DOING first. History: promoted by `D-WEB-WASI-DEFAULT-0710` (0.6.0 GATE, #776); 15.1+15.2+15.3-slice1+15.3b+15.3c-sliceA+15.3c-sliceB+15.3d-inc1+15.3d-inc2-A/B/C1+C2/E landed by that lane.**

# Kof WASM & WASI — implementation specification (UNDER DEVELOPMENT: emission landed, 15.4 flip pending)

last: 15.3d-increment2-AUDIT-BOOL-IF LANDED 22/10 by the same docs/lab lane `192.168.15.130:9093` (diagnosis only, nothing shipped): TWO real pre-existing defects measured and catalogued. (1) `if` on a BOOL-typed value emits an INVALID MODULE — `var b = 1 > 2` + `if (b) {...}` fails at the host (`values remaining on stack at end of block`) while `if (1 > 2)` and `if (n > 3)` work, so the condition must be a Bool VARIABLE or a string comparison; proven pre-existing by stashing and re-running. Three fixes were attempted and each moved the failure to a DIFFERENT site, which means the malformed shape is produced in MORE THAN ONE lowering path — it needs a full trace of every `KofConditionalJump` producer before another patch. (2) Fractional `Double` printing has no cheap path: the obvious heuristic (scale by 10^k until integral) was tested against `Double.toString` over **200 000** generated values and mismatched **71 690 (36 %)** — Schubfach is genuinely required, and shipping the heuristic would have been mass silent divergence. Both reverted; battery 45/45. Before it: 15.3d-increment2-DOUBLE-EXP-BOUND LANDED 22/10 by the same docs/lab lane `192.168.15.130:9093`: a SECOND silent-wrong-value defect in my own Double increment, found by probing the subset BOUNDARY rather than its interior. `println(10000000.0)` printed `10000000.0` while the JVM prints `1.0E7` — the JVM switches to exponent form at |v| >= 10^7 (measured: 9999999.0 plain, 10000000.0 -> 1.0E7, 0.0001 -> 1.0E-4). Fix: trap when |t| >= 10000000 so the exponent range refuses instead of printing a string the JVM never produces. Two opcode errors were caught while wiring the guard, both by running rather than reading: `0x59`/`0x53` are the i64 SIGNED comparisons in this codebase's table (an unsigned pick made every negative trap), and a stale-class run made the guard look ineffective until the module was regenerated. Verified by direct probe: exit 134 (trap) for 10000000.0, plain output for 9999999.0 and -9999999.0. Battery 45/45. Before it: 15.3d-increment2-DOUBLE-NEGZERO LANDED 22/10 by the same docs/lab lane `192.168.15.130:9093`: I probed the Double increment I had landed EARLIER THE SAME CYCLE instead of assuming it complete, and found a SILENT wrong value: `println(-0.0)` printed `0.0` where the JVM prints `-0.0` (rule 5, not a refusal). Cause: `i64.trunc_f64_s` maps `-0.0` to the INTEGER `0`, so the sign is lost before `kof.intToStr` and the exactness check still passes — the trap never fires. Fix: read the sign bit WITHOUT truncating (`i64.reinterpret_f64 < 0`) and prefix `-` ONLY when the truncated value is 0, so `-1.0` does not become `--1.0`; `integralDoublePrintsLikeTheJvmAndFractionalTraps` now covers `-0.0`/`1.0`/`-1.0`, PROVEN RED by removing the sign handling; battery 45/45. Before it: 15.3d-increment2-CONCAT-NUMBER LANDED 22/10 by the same docs/lab lane `192.168.15.130:9093`: `"text" + <number|bool>` now works on WASI. MEASURED defect: `"n=" + n` refused `WASM002` while `"a" + "b"` was fine. Cause: the concat path calls `String.valueOf(operand)`, and for WASI the lowering BOXED the primitive first (the generic path), so `valueOf(Object)` matched no stringification branch. WASI has no generic box — like Native (#773) it stringifies by the REAL type, so it now takes the raw-primitive path (no box) and `valueOf` resolves to `kof.intToStr` / `kof.strBool` / `kof.strChar`. Int, Long, literal, variable and Bool all tested. `WasmWasiStringSearchE2ETest#concatenatingNumbersWithStringsMatchesTheJvm` byte-equal to the JVM oracle on 5 shapes (plus `"a"+"b"` as the non-regression control), PROVEN RED by reverting the WASI branch; battery 45/45. Before it: 15.3d-increment2-DOUBLE-INC1 LANDED 22/10 by the same docs/lab lane `192.168.15.130:9093`: the `Double` face of unit 15.3 got its FIRST increment, and its scope was chosen by measurement rather than optimism. The full `Double.toString` needs the Schubfach shortest-round-trip algorithm (`RuntimeDtoaSchubfach` + Core/Double/Float, 500+ asm lines) to match the JDK byte-for-byte — a multi-cycle port. This increment therefore supports exactly the subset where a plain decimal rendering IS the JDK output: INTEGRAL values (`2.0` -> `2.0`, `100.0`, `-3.0`, `0.0`), implemented as `f64 -> i64.trunc_f64_s`, an exactness check `v == convert_i64_s(t)`, then the already-proven `kof.intToStr` plus `.0` appended with `kof.strAppendByte`. Anything with a fractional part TRAPS (`unreachable`) instead of printing a silently different string — the same "trap honesto" the backend already uses for out-of-bounds. `WasmWasiStringSearchE2ETest#integralDoublePrintsLikeTheJvmAndFractionalTraps` covers BOTH halves of the contract (JVM-oracle parity on 4 integral shapes AND a non-zero exit for `1.5`), PROVEN RED by removing the dispatch; battery 44/44. Before it: 15.3d-increment2-SLICE-F-FACE2O LANDED 22/10 by the same docs/lab lane `192.168.15.130:9093`: the `Char`-in-a-variable defect I diagnosed last cycle is FIXED at its real root, and the root was not what I thought. Last cycle I concluded the problem was a println fast path that could not distinguish `println(charAt(0))` from `println(c.toString())`. Measuring the wider surface showed that was too narrow: **`println(c)` alone is also broken** when `c` is a Char VARIABLE (`println('k')` worked, and that is why it hid so long). The actual cause is in `WasmScalarOps.mapType`: `char` was declared as a TYPE_I32 LOCAL SLOT, while a Kof `Char` travels as **i64** on the stack and both `kof.writeChar` and `kof.strChar` take i64 — so any Char routed through a variable produced `type mismatch: expected i32, found i64` and an invalid module. `char` now maps to TYPE_I64. That also makes `c.toString()`, `String.valueOf(c)` and a `charAt`-derived Char in a variable work, with JVM-oracle parity. `WasmWasiStringSearchE2ETest#charInAVariablePrintsAndConvertsLikeTheJvm` covers the six shapes, PROVEN RED by restoring the i32 mapping; battery 43/43. Before it: 15.3d-increment2-SLICE-F-FACE2N LANDED 22/10 by the same docs/lab lane `192.168.15.130:9093`: sweep of the WHOLE landed String surface for the class "overload the lowering does not consume compiles and emits an INVALID MODULE". Two instances found and fixed, both of which had `compile(...).success() == true` and failed only at the HOST: `startsWith(str, fromIndex)` (a third i64 operand the 2-handle helper never pops) and `replace(Char, Char)` (two i64 Char operands where two i32 handles are expected). Both now refuse `WASM002` naming the missing face. The sweep also CONFIRMED the neighbouring faces are sound (`substring(1,3)`, `substring(i)`, `charAt`, `trim`, `isEmpty`, `equals`, `concat`, `lastIndexOf(str,from)` all run correctly; `toUpperCase`/`toInt`/`equalsIgnoreCase`/`compareToIgnoreCase` refuse; `split` refuses SEM028). `WasmWasiStringSearchE2ETest#unimplementedOverloadsRefuseInsteadOfEmittingAnInvalidModule` pins both, PROVEN RED by removing the `startsWith` guard; battery 42/42. Before it: 15.3d-increment2-SLICE-F-FACE2M LANDED 22/10 by the same docs/lab lane `192.168.15.130:9093`: `String.contains(t)` = `indexOf(t) >= 0` — a pure REUSE face that delegates to the search proven in 2e/2l instead of having its own implementation, so there is exactly one place where substring matching lives. `WasmWasiStringSearchE2ETest#containsMatchesTheJvmOracle` byte-equal to the JVM oracle on 5 faces (present, absent, EMPTY needle = true, non-ASCII, needle longer than haystack), PROVEN RED by removing the routing; battery 41/41. Before it: 15.3d-increment2-SLICE-F-FACE2L LANDED 22/10 by the same docs/lab lane `192.168.15.130:9093`: the `fromIndex` overloads — the face I reverted last cycle — are DONE. The blocker was operand order, and the fix is to stop fighting it: the frontend stack is `[receiver, str, from]`, which is EXACTLY the signature `(a, b, from)`, so `kof.strIndexOf` became 3-arg (no reordering at all) and `lastIndexOf` became its own 3-arg function. The two contracts are genuinely different and both were measured: `indexOf(str,from)` = SMALLEST match with unit index `>= from` (from past the end = -1), while `lastIndexOf(str,from)` = LARGEST match with index `<= from` with `from` CLAMPED (`(…,99)` = 4, not -1). The 1-arg forms push `from = 0` / `from = Long.MAX_VALUE` respectively, so they share the same functions with no special path. `WasmWasiStringSearchE2ETest#indexOfAndLastIndexOfWithFromIndexMatchTheJvmOracle` byte-equal to the JVM oracle on 9 faces, PROVEN RED by reverting to the refusal; battery 40/40. The previous cycle's honesty test (`…RefusesHonestlyInsteadOfSilentlyIgnoringIt`) was replaced by the real implementation, not deleted-and-forgotten. Before it: 15.3d-increment2-SLICE-F-FACE2K LANDED 22/10 by the same docs/lab lane `192.168.15.130:9093`: `String.replace(String,String)`. The non-empty target is EXACT in BYTES (UTF-8 prefix-free), non-overlapping left-to-right; the EMPTY target gets its own path because the JVM inserts before every CODE UNIT and at the end (`"ab".replace("","-")` = `-a-b-`), which counts units rather than bytes. Two new reusable runtime pieces came out of it: `kof.strAlloc`/`kof.strAppend`/`kof.strAppendByte` (growable bump-heap handles — the same substrate collections will need). `WasmWasiStringSearchE2ETest#replaceMatchesTheJvmOracle` byte-equal to the JVM oracle on 7 faces (repeated target, non-ASCII target AND replacement, absent target, non-overlapping repeat, empty target, target==whole string), PROVEN RED by removing the routing; battery 39/39. Two defects caught by RUNNING: `kof.strAlloc` set the header to `n` so the first append doubled it (leading NULs in the output), and a stack-order error where the i64 wrap was pushed before a `local.get`. ALSO MEASURED this cycle: `throw`/`try` are NOT in the WASI subset (`KofTryStart` refuses `WASM002`), so `toInt`/`toDouble` CANNOT be done cheaply — their JVM contract throws on bad input. Before it: 15.3d-increment2-SLICE-F-FACE2J LANDED 22/10 by the same docs/lab lane `192.168.15.130:9093`: `String.compareTo()`. Lexicographic over UTF-16 CODE UNITS — the first differing-unit difference (`"café".compareTo("cafz")` = 111, since é is U+00E9 and z is U+007A), or `la-lb` when one is a prefix, 0 when equal. Implemented by REUSING the proven `kof.strCharAt` (surrogate halves) and `kof.strLen` rather than re-implementing a byte scan — the semantics are of UNITS, and `charAt` already carries that mapping. This is the payoff of the previous four faces: it needed no new byte-scanning logic, only a flat loop with one exit block, and it passed on the second measurement with no branch-depth surprises. `WasmWasiStringSearchE2ETest#compareToMatchesTheJvmOracle` byte-equal to the JVM oracle on 9 faces (first-unit difference, equal, both prefix orders, identical non-ASCII, and the `"café"`/`"cafz"` = 111 case that a byte-compare would get wrong), PROVEN RED by removing the routing; battery 38/38. Search tests split into `WasmWasiStringSearchE2ETest` for `check_test_hygiene`. Before it: 15.3d-increment2-SLICE-F-FACE2I LANDED 22/10 by the same docs/lab lane `192.168.15.130:9093`: `String.lastIndexOf()` — the face I had attempted and reverted last cycle. It landed only after restructuring the SEARCH itself: `strMatchAt` (does b match at byte p?) and `strUnitsBefore` (how many UTF-16 units in the first p bytes?) are now SEPARATE single-loop functions in `wasm/WasmStrSearch.java`, so `kof.strIndexOf(a,b,last)` is a flat scan that only asks yes/no and calls one helper — there is no nested `IF` and therefore no `br`-depth arithmetic, which was the actual cause of every earlier failure. `WasmWasiStringE2ETest#lastIndexOfMatchesTheJvmOracle` byte-equal to the JVM oracle on 9 faces (repeated pattern -> last hit, absent -1, empty needle -> length, non-ASCII, needle longer than haystack, single-match control, and `indexOf` still right beside it), PROVEN RED by removing the routing; battery 37/37. Two more opcode/type defects were caught by measurement: `0xAD` is `i64.extend32_s` (the opcode needed is `0xAC`), and two local-type lists had their i32/i64 slots out of order. Before it: 15.3d-increment2-SLICE-F-FACE2H LANDED 22/10 by the same docs/lab lane `192.168.15.130:9093`: `String.trim()`. Exact as a BYTE operation for the same reason `startsWith` is: the JVM trims code units `<= U+0020`, and in UTF-8 every such code unit is a SINGLE byte `<= 0x20` while every multi-byte sequence starts with a byte `>= 0xC2` — so the trimmed byte range is the trimmed code-unit range, with no code-unit walk. `WasmWasiStringE2ETest#trimMatchesTheJvmOracle` byte-equal to the JVM oracle on 9 faces (both ends, unpadded, all-spaces to empty, TAB, newline, non-ASCII with and without padding, and an INTERNAL space that must survive), PROVEN RED by removing the routing; battery 24/24. Before it: 15.3d-increment2-SLICE-F-FACE2G LANDED 22/10 by the same docs/lab lane `192.168.15.130:9093` — the face deferred three times is DONE: `charAt` now returns the correct SURROGATE HALF (`x\uD83D\uDE00y` -> 120 / 55357 / 56832 / 121, exactly the JVM) and `println(Char)` encodes UTF-8 (é = 2 bytes, € = 3, lone surrogate = `?`). The fix that finally worked is STRUCTURAL: classify the sequence by the LEADER BYTE (not by the already-decoded value, which for a 4-byte leader can still look small), run the whole decode inside the loop, and do the half selection FLAT after the loop (`low` iff `wu == 2 && u == k0+1`, `high` iff `wu == 2 && u == k0`, else `cp`) so there is no branch-depth arithmetic at all. Two opcode/mask defects were caught by measuring numeric values, never by compiling: `(c & 0xC0) == 0xC0` also matches a 4-byte leader, so the 2-byte test must mask `0xE0`; and `0x5A` is `i64.ge_u`, not `le_s`. `WasmWasiStringE2ETest#charAtReturnsBothSurrogateHalvesAndNonAsciiCharsPrintAsUtf8` byte-equal to the JVM oracle, PROVEN RED by reintroducing the mask bug; battery 23/23. `WasmCharOut` extracted for `check_500` (`WasmStdoutRuntime` back to 503). Before it: 15.3d-increment2-SLICE-F-FACE2F LANDED 22/10 by the same docs/lab lane `192.168.15.130:9093`: `String.charAt()` / `substring()` on WASI. These are the faces that need the real UTF-16-code-unit -> UTF-8-BYTE index mapping, now factored as `kof.strByteOff` (advances `i` by the FULL sequence width, and `k` by 2 for a surrogate pair). `substring(i)` keeps the Native `-1` sentinel for "to the end" (a `0` would be a legitimate index). `WasmWasiStringE2ETest#stringCharAtAndSubstringMatchTheJvmOracle` byte-equal to the JVM oracle on 10 faces including `"café".substring(3)` = é and `"a€b".substring(1, 2)` = €, PROVEN RED by removing the routing; battery 22/22. Bugs caught by RUNNING: a `block` never opened for the `br` target, a byte-at-a-time advance that stopped MID multibyte character, a missing `-1` sentinel branch, an off-by-one local-type list, and a leaked `lastPushWide`. Helpers live in `wasm/WasmStrSlice.java` (separate from `WasmStrInspect` for `check_500`). Before it: 15.3d-increment2-SLICE-F-FACE2E LANDED 22/10 by the same docs/lab lane `192.168.15.130:9093`: `String.indexOf()` on WASI. The MATCH is a byte search (valid by the same prefix-free property as `startsWith`), but the returned INDEX is a UTF-16 code-unit index — so the helper counts the code units of the prefix `[0,p)`, with the same 4-byte-leader = 2-units rule as `length`. `WasmWasiStringE2ETest#stringIndexOfReturnsUtf16CodeUnitIndex` byte-equal to the JVM oracle on 8 faces including `"x😀y".indexOf("y")` = 3 (byte offset would be 5), PROVEN RED by removing the routing; battery 21/21. Three defects were caught by RUNNING: missing `+4` length-header offset in the compare, a wrong outer-loop branch depth (`2` instead of `1` from inside the `if`), and a missing surrogate rule in the prefix count. Before it: 15.3d-increment2-SLICE-F-FACE2D LANDED 22/10 by the same docs/lab lane `192.168.15.130:9093`: `String.startsWith()` / `endsWith()` on WASI, compared as BYTE prefix/suffix — exact because UTF-8 is PREFIX-FREE (no valid sequence is a prefix of another), so a byte prefix/suffix corresponds to exactly the same UTF-16 code-unit prefix/suffix. That is what lets these two skip the code-unit walk `charAt` would need. Two real defects were found by RUNNING the module: the compare read `[a+i]` instead of `[a+4+i]` (skipping the length header; `"abc".startsWith("abc")` was false), and the length guard was inverted (`lb <= la` instead of `la >= lb`). `WasmWasiStringE2ETest#stringStartsWithAndEndsWithMatchTheJvmOracle` byte-equal to the JVM oracle on 11 faces, PROVEN RED by removing the branches; battery 20/20 (12 na familia de string). The new helpers live in a NEW `wasm/WasmStrInspect.java` and the routing in `wasm/WasmStrCalls.java` because `WasmStdoutRuntime` and `WasmLowering` crossed the `check_500` 600-line red line (now 529 / 562). Before it: 15.3d-increment2-SLICE-F-FACE2C LANDED 22/10 by the same docs/lab lane `192.168.15.130:9093`: `String.isEmpty()` (exact via the byte count — zero UTF-8 bytes <=> zero code units), `String.equals()` and `String.concat()` now route to the ALREADY-proven `kof.strEq` / `kof.strConcat`. Chosen because their semantics are exact with existing helpers; `charAt`/`substring`/`startsWith`/`endsWith`/`indexOf` are DELIBERATELY left out — they index/compare by UTF-16 code unit over a UTF-8 handle and need the same byte-walking treatment as `length()`. `WasmWasiE2ETest#stringIsEmptyEqualsAndConcatMatchTheJvmOracle` byte-equal to the JVM oracle on 8 faces (empty/non-empty, equals true/false/self, concat, concat chain, concat-then-length on non-ASCII), PROVEN RED by removing the branches; battery 19/19, 0 skips. Before it: 15.3d-increment2-SLICE-F-FACE2B LANDED 22/10 by the same docs/lab lane `192.168.15.130:9093`: `String.length()` on WASI, counting UTF-16 CODE UNITS (the Kof/JVM semantics) instead of reading the handle's UTF-8 BYTE count — the trap that would have diverged silently on every non-ASCII string (JVM `\"café\".length()` = 4, bytes = 5; an emoji is 4 bytes but 2 code units). New `kof.strLen` walks the bytes classifying LEAD bytes only (UTF-8 is self-synchronising) and advances 1 unit for ASCII/2-byte/3-byte leaders and 2 for a 4-byte leader; continuation bytes do not count. `WasmWasiE2ETest#stringLengthCountsUtf16CodeUnitsLikeTheJvm` byte-equal to the JVM oracle on 6 faces (ASCII / 2-byte / 3-byte / 4-byte surrogate pair / empty / post-concat), PROVEN RED by removing the lowering branch; battery 18/18, 0 skips. Before it (still same cycle): 15.3d-increment2-SLICE-F-FACE1 LANDED 22/10 by the docs/lab lane `192.168.15.130:9093` (the plan was ORPHANED — the `.101:9092` claim abandoned, last plan work 09/10; re-claimed in DOING per `D-PLAN-ONE-OWNER`): `String ==` / `!=` now lower to the EXISTING proven-green `kof.strEq` content helper, so the most basic string comparison no longer refuses `WASM002`. The frontend already lowers the operator to `kof_string_equals(a,b)` (with `!=` adding `== 0` after it) — the WASI lowering simply had no destination for that call. Registered the helper for the string-equals case too (`kofStrEq` was reachable only via records before). `WasmWasiE2ETest#stringEqualityMatchesTheJvmOracle` byte-equal to the JVM oracle on 9 faces (equal/prefix/different/`!=` both ways/literal-vs-literal/empty/empty-vs-empty/concat-result), PROVEN RED by removing the lowering branch; `WasmWasiE2ETest` 9/9 + `WasmScalarE2ETest` 3/3 + `WasmTargetGateE2ETest` 5/5 = 17/17. MEASURED before choosing the slice: the plan's own named next face (`Double` in `toString` / `Double.toString`) needs a full Schubfach dtoa transcribed into hand-built wasm bytecode (`RuntimeDtoaSchubfach` + `...Core/Double/Float`, 500+ asm lines) — recorded as the reason it is NOT this slice. Before it: 15.3d-increment2-SLICE-E LANDED 09/10 (explicit `p.equals(q)` on a known-record owner routes to the SAME inline content fold as `==` — no virtual dispatch needed, contract §262 explicit-equals == content-equality; `WasmWasiE2ETest` 13/13 with `explicitRecordEqualsMatchesTheJvmOracle` byte-equal JVM oracle 5 faces, battery 31/31, zero record refusals left; repaired external damage committed on the tip: conflict markers in DOING EN+PT + known-bugs.md, and the cross-GC entry renumbered §640->§643 in PT/CHANGELOG where it collided with the gpu §640); before it 15.3d-increment2-SLICE-D LANDED 09/10 (nested-record fields = i32 handles on the bump heap; the alloc saves/restores the in-progress constructor receiver via `nestR[depth]` — objIdx corruption between alloc and stores was the measured root cause; `toString`/`==`/`!=` recurse with one scratch slot per depth; `WasmRecordCtor` split keeps `check_500` rc=0 (`WasmLowering` 644→545); E2E `nestedRecordFieldsMatchTheJvmOracle` byte-equal to the JVM oracle (8 faces), battery 31/31, full suite skipped by maintainer order; explicit `.equals()` keeps `WASM002` honest) — plan PROMOTED `docs/development/` → `docs/` at the lane cut by maintainer order 09/10 (slices E/F/G + 15.4 stay open inside the doc); before it 15.3d-increment2-SLICE-C2 LANDED 09/10 (record `==`/`!=`/`== null` CONTENT equality = opaque `kofRecordEq` + inline single-block field fold, byte-equal JVM oracle 12/12 faces, battery 30/30, `WasmPrintCode` split keeps `check_500` rc=0);
doing: 15.3 continues — **slice F face 1 LANDED 22/10** (`String ==`/`!=` via the existing `kof.strEq`; the operator's `kof_string_equals` call now has a WASI destination); slice F face 2 (`Double` fields in `toString` + `Double.toString` parity) is the NEXT face and is LARGE — it needs a Schubfach dtoa emitted as wasm bytecode; slice G: collections (`kof.list/map/set` on the bump heap); then 15.4 flip LAST.
next: GC-handle runtime (records/collections) → 15.4 frontend-default flip LAST (only at full parity; `D-LAB-STABILITY` keeps the cut gated by #776).
location: docs/wasm-wasi-plan.md
state: UNDER DEVELOPMENT

> **✅ LANDED 26/10 — `String.toUpperCase`/`toLowerCase` now evaluate on WASI (lane `192.168.100.23:0`, scheduled flatt CLI round).** The case fold Unicode with the SAME semantics as the native backend — D-STR-UNICODE (27/09, row 11): SIMPLE per-code-unit fold (`Character.toUpperCase/toLowerCase` per code point), the BMP mappings (0x80..0xFFFF, ~1.2k entries) in an embedded table generated from the JDK itself, ASCII folded inline, astral code points (4-byte UTF-8) passing through untouched. The WASI runtime gains `kof.strCaseUp`/`kof.strCaseLo` (the per-position mapper: UTF-8 decode, binary search over the table, astral pass) and `kof.strUpper`/`kof.strLower` (two passes: the exact output byte length — simple mappings CAN change the byte length, e.g. U+0130 → i — then the fill via `strAlloc`/`strAppendByte`); the tables ride the data cursor (the heap starts after them). Proven byte-identical to the JVM oracle on ASCII, accented Latin (café→CAFÉ, naïve), Greek (ΣΟΣ↔σος), mixed alphanumerics and astral pass-through (✅). The full-case-mapping exceptions (ß→SS, İ→i̇, final sigma) stay OUTSIDE the ratified per-code-unit scope — WASI behaves exactly like the NATIVE on them (the frontend does not diverge between backends). The new face `stringCaseFoldMatchesTheJvmOracle` (in `WasmWasiFlowE2ETest`); `Wasm*` battery **49/49**; the flow/IfExpr regression **48/48**.

> **✅ LANDED 26/10 — the `if`-EXPRESSION JOIN now evaluates on WASI (lane `192.168.100.23:0`, scheduled flatt CLI round).** The same single-temporary machine as the logical chains: when both arms converge on the SAME type (`bts.get(0).equals(bts.get(1))`, or equal physical types), each arm STORES the result to a temporary (`$kif`) and the join LOADS it, so no value crosses a state boundary; the JS target keeps its own stack-flow path (its parser folds the store-free shape — the same sensitivity measured for the troolean in 19/09). Probed: `var m = if (flag) 1 else 2`, String/Bool arms, nested if-expressions and the non-taken arm — all byte-equal to the JVM oracle; the new `WasmWasiE2ETest#ifExpressionJoinMatchesTheJvmOracle` face pins it (wasm-tools validate + wasmtime vs the JVM); `Wasm*` battery **48/48**; the condition/IfExpr regression batch **46/46** (incl. `IfExprNarrowedPrimitiveJoinE2ETest`, which caught the JS face on the first run — fixed by the gate). **REMAINING divergence (recorded, next slice):** heterogeneous arms (`if (c) 1 else "x"`, boxed join) still refuse on WASI — the temp needs the boxed LUB type. **ENVIRONMENT NOTE:** the dependabot bumps that landed on 26/10 (surefire 3.6.1, mariadb-java-client 4.0.0) are NOT in this host's offline `~/.m2`, so local offline runs require a temporary pom pin back (3.6.0 / 3.5.10); the poms were restored before this commit — an online `mvn` run (or the bumps' artifacts) is needed for the plain `mvn -o` flow.

> **✅ LANDED 26/10 — the LOGICAL CHAINS (`&&`/`||`) now evaluate on WASI (lane `192.168.100.23:0`, scheduled flatt CLI round).** The previous slice fixed the width of the synthetic zeros; this slice fixes the JOIN: the chain's result crossed a state boundary on the stack, and the state machine does not transport the stack between states — so `x > 0 && y > 0`, `while (i < n && j < m)`, `x == 1 || x == 2` and `b1 && b2` all emitted invalid modules (the honest WASM002 refusal added last slice was a net, not a feature). Fix: the regular chain lowering (`ExpressionBinaryLowerer`) now writes each arm's result to a **temporary local** and the join LOADS it — the same single-temporary machine the troolean (`Bool?`) path already used — so every block boundary starts with an empty stack and the WASI state machine is satisfied; the JVM/native/JS backends get the same, behavior-identical IR (the JS target keeps its own chain path). Short-circuit is PRESERVED and proven at runtime: `false && 1/z == 1` and `true || 1/z == 1` with `z = 0` do not trap because the right side is not evaluated. Proof: 11 shapes byte-equal to the JVM oracle (bool `&&`/`||`, comparison `&&`/`||`, compound `while`, mixed precedence `false && true || true`, chain as a value, nested three-way chains with `!`); the new `WasmWasiE2ETest#logicalChainsMatchTheJvmOracle` face (wasm-tools validate + wasmtime run vs the JVM); `Wasm*` battery **47/47**; the condition/loop batch **48/48**; the `refuseLiveStack` honesty net stays for the remaining cross-block joins (the `if`-EXPRESSION join is the next slice — the same temporary machine applies). The full compiler module suite shows only the pre-existing `FfiNativeE2ETest` failure (verified by stash).

> **✅ FIXED 26/10 — the BOOL-VARIABLE condition defect (the `AUDIT-BOOL-IF` measured bug of 22/10; lane `192.168.100.23:0`, scheduled flatt CLI round).** The defect was a family with one root: bool values are **i32** on WASI while the synthetic zeros of the truthiness lowering were typed **INT** (i64). Three measured faces, three fixes: (1) **the typed zero** — `emitTruthinessJump` now types the synthetic zero by the CONDITION (`BOOL` → i32 literal), the do-while/for paths route through the same helper, and the `&&`/`||` chain types its synthetic 0/1 by the expression (`b1 && b2` no longer mismatched); (2) **the leaked width state** — `ctx.lastPush` survived across statements (`var flag = true` + `var i = 0` made the `0` inherit "bool" and emit i32.const into the i64 slot — the second invalid-module face) — the reset is now per BLOCK plus clears at every consumer (store, conditional jump); (3) **cross-block live values** — the `&&` chain carries the right operand across a state boundary and the state machine cannot transport the stack, which emitted an INVALID module — the subset now refuses honestly (`WASM002`, no artifact, Q7) instead. Proof: the new `WasmWasiE2ETest` faces — `boolVariableConditionsMatchTheJvmOracle` (if/else, if, while(flag), do-while with a bool comparison, `!d`, and a BOOL-returning call as condition — wasm-tools validate + byte-equal to the JVM oracle) and `andOverBoolVariablesIsAnHonestRefusal` (WASM002 + no artifact); `Wasm*` battery **45/45**; the condition/loop batch **48/48** (`IfExpr*`, `JsLoop*`, `KofJsLogicalShortCircuit`, `NullableBool*`, `JsDoWhile*`). Full compiler module suite: the only failures are `FfiNativeE2ETest` (2 faces, native exit 139) — **verified pre-existing by stash** (fails without this change; FFI/native, not this fix's scope).

> **State (07/10): UNDER DEVELOPMENT — promoted by the maintainer's own order**
> (`D-WEB-WASI-DEFAULT-0710`); **zero code at promotion day — that was 07/10.**
> **MEASURED 21/10: units 15.1, 15.2, 15.3 (slices A–E) and 15.3d LANDED** — the
> `wasm/` backend is 15 source files and `kof build --target=wasi --output X`
> really emits `Default/Main.wasm`. Only the **15.4 frontend-default flip** is
> still pending. The plan is a
> **GATE for the 0.6.0 cut** (issue #776): Kof's web target becomes **WASI by
> default**, desktop frontend likewise; the JS/`kofjs` target **continues to
> exist** when explicitly specified; the **language surface does not change**;
> **total behavior parity** and **zero regression** are mandatory. The plan is
> OPEN and unowned — any free lane claims it in DOING first (`D-PLAN-ONE-OWNER`).
> Every statement below stays marked **CURRENT** (measured in the code),
> **PLANNED** (proposed here) or **TBD / DECISION REQUIRED** (legacy markers —
> the 28/09 `D-WASM-01..09` block already resolved the technical ones).
>
> **Decision update (28/09/2026):** the technical questions this spec left
> TBD/DECISION REQUIRED were **resolved** by the maintainer in `DECISIONS.md`
> §`D-WASM-GO` (`D-WASM-01..09`: direct backend, `Int`=i64, thrown-string
> unwinding, handles+handle table, native mark-sweep GC design, explicit
> closure env, WASI preview1, wasmtime first, cooperative concurrency). The
> spec has since been PROMOTED (07/10, this doc lives in `docs/`, not
> `future/`) and its emission units have landed as measured above; do not read
> the inline TBD markers as still-open decisions.
>
> **Sibling docs:** `qrcode-wasm-plan.md` PART 2 (the frontend strategy: JS ×
> WASM coexistence — this spec is its implementation detail for the compiler
> side); `DECOMPILER.md` / `TRANSLATOR.md` (reverse direction: WASM→Kof — a
> future differential oracle for this backend, §26).

## 1. Objective

**PLANNED:** add `WASM` and `WASI` as official Kof targets:

```text
Kof source → Kof compiler → Kof IR → WASM backend → .wasm
```

A Kof developer writes Kof and selects WebAssembly as target — no
JavaScript, no C, no intermediate language. WASM is a **first-class
backend**, not a transpilation pipeline. **WASI is the same backend plus a
system-interface layer** (§2).

## 2. WASM × WASI — two layers, one backend

```text
WASM ─ WebAssembly module; execution controlled by a host (browser,
       wasmtime, embedder). No ambient rights.
WASI ─ WASM + system interface: filesystem, stdin/stdout/stderr, clocks,
       random, args/env — capabilities granted by the WASI host.
```

Two execution shapes (PLANNED):

```text
Kof → WASM module → host                (browser: Web APIs via bridge)
Kof → WASM module → WASI interface → WASI runtime   (server/CLI/edge)
```

WASI is **not** "WASM for servers": it is a different capability layer over
the same module format. The backend must treat the difference as *which
imports the module declares*, not two compilers.

## 3. Current architecture (measured 19/09 — the ground this spec stands on)

**CURRENT** facts this plan must respect (verified in the tree):

| Component | Real state (19/09) |
|---|---|
| Target enum | **SUPERSEDED 21/10:** `dev.kof.compiler.Target` DOES now have `WASM` and `WASI` (added by unit 15.1); the row above described the pre-15.1 state. A **backend** role exists for both — `kof build --target=wasm\|wasi` emit real modules (verified, see `backend-parity.md`). The **frontend** role is what is still missing (unit 15.4) |
| Honest rejection | `TargetMatrix` maps the strings `wasm`/`kofwebasm` → gap **`WASM001`**, but ONLY for the FRONTEND role ("cannot be a frontend yet — emission exists (units 15.2/15.3) but the frontend/deploy surface and the default flip arrive with unit 15.4"). As a BUILD backend it no longer refuses. Original wording:  a diagnostic, never a silent fallback |
| Pipeline | `CompilerPipeline.compile(driver, sourceFile, outputDir, Target)` — one frontend (Lexer→Parser→AST→semantic analysis) into per-target lowering/codegen; `SCRIPT` interprets the **shared IR** (`CompilerPipeline.interpret`) — proof the IR is backend-agnostic enough to host a new backend |
| JS backend | `dev.kof.compiler.js` (`JsBackend`, `JsArtifactWriter`, `JsCallEmitter`, `JsClassEmitter`, …) — text-emitting reference for a new `wasm` package |
| Native backend | `dev.kof.compiler.nativex8664` (+ `riscv/`, `aarch64/` siblings) — the **closest relative of a WASM backend**: own allocator, own object layout, hand-rolled runtime; reuse analysis in §7 |
| GC | Native mark-sweep is **in development** (roadmap: G-0 riscv ✅ `356f33b9`, G-1..G-5 decomposition) — WASM GC depends on it (§9) |
| Capability system | stdlib classes gate per target via `supportedOn` + `XXX00x` gap codes (`DomainGapCodesTest` pins) + the matrix in `docs/backend-parity.md` — WASM joins as a new column, not a new mechanism (§14) |
| KofUI | D-UI-SCOPE (ratified 19/09, `backend-parity.md`): kof.ui rule-updates are authored for **KofJS today** and **Kof WebASM when `Target.WASM` exists** — never JVM/Native (§17) |
| CLI | targets parsed in `kof-cli` (`BenchDiscovery`/`CmdBuild` map `jvm|native|js`…); `--target` is the selection point (§23) |
| Dev host | **no** `wasmtime`/`wasmer`/`wat2wasm`/`wasm-opt` on the dev host (measured 19/09) — future tests need toolchain guards (`assumeTrue`, never silent skip) |

Not assumed: that the diagram in §4 already exists in exactly that shape —
AST, semantic analysis and the per-target dispatch DO exist (above); the
"IR" is today the lowering input shared by JVM/native/script paths, whose
formality is a **Phase 0** subject (§31), not a given.

## 4. Future architecture (PLANNED)

```text
                          Kof
                           │
                   Frontend + IR (CURRENT: parse/typer/lowering pipeline)
                           │
      ┌──────────┬──────────┼──────────┬──────────┬──────────┐
      ▼          ▼          ▼          ▼          ▼          ▼
    JVM       Native        JS       WASM       WASI      Script
                                    │           │
                                 Browser      WASI
                                  host        runtime
```

Shared: frontend, typer, capability registry, gap codes, conformance
harness. Specific: the `wasm` emitter, a wasm runtime prelude, the host
bridge (browser) or WASI imports (wasi). WASM and WASI **share the backend
and differ only in declared imports + runtime bindings**.

## 5. Architectural decision (main requirement)

**Decision:** `Kof → WASM` is direct compilation through the compiler's own
infrastructure.

**Context:** WASM can be reached via several pipelines; the choice fixes
ownership of correctness.

**Options:** (a) direct backend from Kof IR; (b) Kof→JS→WASM (transpile
chain); (c) Kof→C→WASM (third-party toolchain in the middle).

**Chosen direction (PLANNED):** (a).

**Reason:** (b)/(c) make an external tool the semantic oracle — every bug in
it becomes a Kof bug, the output is uncontrollable, and the frozen
semantics (`==` content, String-exceptions, spawn/await, GC) end up defined
by someone else. (a) reuses the real components: `CompilerPipeline` dispatch,
the typer/lowering split already proven target-agnostic by `SCRIPT`, the
`js/` package as the shape-reference for a backend package, `TargetMatrix`
for the capability column.

**Consequences:** Kof owns the WASM correctness surface (validation +
fuzzing obligations, §26–27); a wasm runtime must exist (§7); **DECISION
REQUIRED** from the maintainer to freeze this as official (rule 6).

## 6. Target registry (15.1+15.2 LANDED 07/10 — enum + matrix + CLI parse + SCALAR emission)

```bash
kof build --target=wasm   # 15.2: emits the scalar subset; outside it: WASM002 honest rejection
kof build --target=wasi   # LANDED: emits Main.wasm (measured 21/10 — `--output` yields Default/Main.wasm)
kof check --target=wasm   # LANDED: `checked 1 file(s) — no errors` (measured 21/10)
kof run --target=wasi     # REFUSED: `kof run` has no WASI host and says so (Q7; use `kof build` + a runtime)
```

**Implementation order honesty:** each command appears in the CLI only with
its phase (§31); until then `WASM001` remains the answer. Registry touch
points (measured): `Target.java` enum + `TargetMatrix` + `kof-cli` target
parsing — **enum change = frozen-surface adjacent → rule 6 approval**.

**Slice 15.1 plumbing LANDED (06/10, lane compiler/JVM/native `192.168.15.30:9092`):**
the honest gap now covers the WASI spellings too — `TargetMatrix.frontendGapFor`
recognizes `wasm32`, `wasm32-wasi`, `wasi`, `wasi-preview1`, `wasip1`, `kofwasi`
(besides the existing `wasm`/`kofwasm`/`kofwebasm`/`kofwebassembly`/`webassembly`)
and both CLI paths (`--frontend=wasi`/`kof.toml` via `TargetMatrix.parse`, legacy
`--target=wasi` via `KofCliSupport.parseTarget`) refuse with `WASM001`, never the
generic "unknown target". **CORRECTED 21/10:** that refusal is the FRONTEND role
only — `--target=wasi` as a BUILD backend stopped refusing at unit 15.3 and now
emits `Main.wasm` (measured; runs under `node:wasi`, see `backend-parity.md`). The message now names the promoted plan + issue #776 +
`D-WEB-WASI-DEFAULT-0710`. Additive plumbing only — **no `Target` enum value
yet** (that is the frozen-surface-adjacent step, rule 6, deferred to the codegen
phase). Proof: `TargetMatrixTest.wasiSpellingsAreHonestGap` +
`SelectTargetsTest.legacyWasiTargetFlagIsHonestGap` (both RED pre-slice).

**Slice 15.2 LANDED (07/10, lane `192.168.15.101:9092`):** first emitting
backend for `wasm` — `dev/kof/compiler/wasm/WasmBackend` writes a direct
binary WebAssembly module (`WasmBinary` sections, `WasmInstr` encodings)
exporting every top-level static SCALAR function (`Int/Long/Double/Bool/
Char`; `Int=i64` per D-WASM-02) of `Default/Main`; control flow lowers
through the IR block graph into a `loop $dispatch` + `$pc` dispatcher
(explicit `br` depth — forward-only fallthrough was measured and rejected);
direct calls resolve by name at serialization. The subset boundary is a
HONEST refusal, never a silent skip: `CompilerPipeline` catches
`WasmUnsupportedException` → `WASM002` naming this plan + unit + #776 and
writes NO artifacts (Q7). `main`/IO/strings/collections/records = 15.3+
(D-WASM-03..06). WAT emission stays PLANNED (this section's `--emit=wat`
row predates the slice; the binary is the 15.2 product). Proof:
`WasmScalarE2ETest` 3/3 — emission+validation via `wasm-tools` 1.261.0,
execution via `wasmtime` v49.0.2 (`scripts/provision-wasmtime.sh`, pinned
checksums, host-gated by `assumeTrue`), JVM oracle parity for `add`,
`collatz(27)=111`, `fib(10)=55`; refusal case proves no partial module.
`WasmTargetGateE2ETest` re-pinned to the new truth (wasm IS backend; WASI
still `WASM001`; out-of-subset program → `WASM002`).

**Slice 15.3-stdout LANDED (07/10, lane `192.168.15.101:9092`):**
`Target.WASI` is now an EMITTING WASI-preview1 backend: `main` compiles to
the exported `_start` (command mode), every scalar `println` writes through
the imported `wasi_snapshot_preview1.fd_write`; `Int`/`Long` print via the
emitted `kof.writeInt` itoa helper, `Bool` via `kof.writeBool`. Out-of-slice
programs (string literals, `args`, records/collections) refuse `WASM002`
naming this plan + #776, writing NO artifacts (Q7; proven by
`WasmWasiE2ETest` + `WasmTargetGateE2ETest`). Proof: `WasmWasiE2ETest` 3/3 —
module carries the preview1 imports + `_start` export, validates with
`wasm-tools` 1.261.0, executes under `wasmtime` v49.0.2 with stdout EXACTLY
equal to the JVM oracle of the same source (negative/zero/call-result ints,
bools), exit 0; the `WasmScalarE2ETest` 3/3 (15.2) and re-pinned
`WasmTargetGateE2ETest` 5/5 (WASI IS backend; frontend gap stays `WASM001`
until 15.4) stay green; `kof deploy --target wasi` honestly refuses `WASM001`
(module emits, deploy archive/runtime host not yet — `SelectTargetsTest`/
`CmdDeployTest` unchanged-green). `println(String)`, records, collections,
`args` and the GC-handle runtime land in the NEXT 15.3 slices (GC-handle rule of the plan).

**Slice 15.3b LANDED (07/10, lane `192.168.15.101:9092`):** `println(String)` of a
string LITERAL now emits: the bytes go to a wasm DATA segment (pool based at 1024,
`+1` byte reserved per string for the newline, 4-aligned), the println intercept
pushes `(addr,len)` and the emitted `kof.writeString` helper writes them through
`fd_write` with the trailing `\n`. Proof: `WasmWasiE2ETest` 3/3 — mixed scalar+string
main prints EXACTLY the JVM-oracle stdout (`oi`, `hello kof` added) under wasmtime
v49.0.2, module validates with `wasm-tools`; out-of-slice strings (concat
`var s = "a" + "b"`, `println(args)`) refuse `WASM002` naming plan + #776 with NO
artifacts (Q7); `WasmTargetGateE2ETest` 5/5 re-pinned (its refuse case moved from
string-literal — now EMITTED — to `args`); `WasmScalarE2ETest` 3/3 stays green. String
VARIABLES/concat/records/collections need the heap (D-WASM handle-table shape of the
plan §8/§9) — 15.3c+ (`args`) then GC slices. NOTE 07/10: the 4-module suite is
`4877 run / 3 F` — JavaFX env (documented) + `Av1CoeffsE2ETest` aarch64/riscv64 =
EXTERNAL regression catalogued as `known-bugs` **§625** (bisect lands on `a2f69d2f7`
§620 cross-arg shift; reproduced at remote tip `45d839322` WITHOUT any WASI-lane code;
 owner lane `192.168.15.30:9092`; NOT touched by this lane — collision rule) — **FIXED
 08/10** by that lane (cross/x86 prologue zeroes slots above `paramSlotMax`, `PrologueSlotInitTest`).


**Slice 15.3c-sliceA LANDED (08/10, lane `192.168.15.101:9092`):** String VARIABLES
and CONCATENATION now emit — a `String` value becomes an i32 HANDLE into a bump
heap (`global 0`, mutable i32 initialized `16384`; `[len][bytes]\n` layout, the
`+1` newline reserved at emit time so `println` never re-allocates). Three emitted
helpers drive it: `kof.strLit(addr,len)` copies a DATA-segment literal into a fresh
heap block and returns its handle; `kof.strConcat(a,b)` bump-allocates `la+lb`,
copies both payloads (`copyLoop` — a `block`+`loop` with `br_if`/`br`, `i32.load8_u`
align 0, byte store `addr` pushed before `value`) and returns the joined handle;
`kof.writeStr(handle)` writes `[h]+1` bytes through `fd_write` (reused for every
`println` of a non-literal string). The println intercept pushes `(addr,len)` for
a literal and calls `kof.strLit` for `kof_string_concat`/`String` variables, so
`var s = "a" + "b"; println(s)` and `println("x" + "y" + "z")` print `ab`/`xyz`.
Proof: `WasmWasiE2ETest` 3/3 — the mixed scalar+string+concat main prints EXACTLY
the JVM-oracle stdout (`3/-42/0/55/true/false/14/oi/hello kof/ab/xyz`) under
wasmtime v49.0.2, module validates with `wasm-tools`, exit 0; `WasmTargetGateE2ETest`
5/5 + `WasmScalarE2ETest` 3/3 + `TargetMatrixTest` 10/10 green. `println(args)`/
`args[0]`/records/collections still refuse `WASM002` naming plan + #776 with NO
artifacts (Q7). At sliceA time `args` still refused; it landed the SAME DAY as
15.3c-sliceB below (records/collections remain, then 15.4 flip LAST). MEASURE 08/10: the full 4-module suite at tip `898bc50ab` + this slice = 8F + 2 flakes, ALL external/environmental and stash-proven independent of the WASI lane: §625 `Av1CoeffsE2ETest` (2F, FIXED 08/10 by lane `.30:9092` — zero stale prologue slots) + §627 `KofTestingE2ETest` float-assert (2F, FIXED 08/10 by lane `.30:9092` — interpreter `EQ/NE` + cross `feq.s`) + §628 `JvmLauncherDiagnosticE2ETest` (3F deterministic at the CLEAN tip — the §554 `ExternalArgTighten` breaks the pipe fixtures, owner compiler/interop) + JavaFX env (1F) + `InteropTimeoutE2ETest` load flakes (2F, GREEN isolated). Live queue 3->5 (5->3 after the §625+§627 fixes).


**Slice 15.3c-sliceB LANDED (08/10, lane `192.168.15.101:9092`):** `args` — a
program that touches `args` gets a `kof.readArgs` prologue in `_start`: WASI-preview1
`args_sizes_get(&argc@48,&argv_buf_size@52)` then bump-allocates the pointer TABLE
(`argc*4`) and the NUL byte BUFFER (`sz`) on `global 0`, calls `args_get`, and converts
every `argv[i+1]` (argv[0] = program name, DROPPED for JVM parity) into a heap
KofString handle `[len][bytes]\n` (strlen + `copyInto`), returning an array handle
`[count][handle...]`. `args.length` lowers to `i32.load` of `[arr]` + `i64.extend_i32_s`
(Int = i64, D-WASM-02); `args[i]` lowers to `i32.wrap_i64` of the index plus an
EXPLICIT bounds check (`count <= idx` via `i32.le_u` -> `unreachable`) — out-of-range
is a deterministic trap, NEVER a garbage read (the exception model is D-WASM-03, still
TBD); `println(args[i])` and `args[i] + "x"` ride the 15.3c-sliceA String runtime
(`kof.writeStr`/`kof.strConcat`). Proof: `WasmWasiE2ETest` 5/5 under wasmtime v49.0.2 —
`argsLengthIndexAndConcatMatchTheJvmOracle` compiles the SAME source for JVM and WASI,
runs both with `alpha beta` and asserts byte-equal stdout (`2/alpha/beta/alpha-beta`);
`emptyArgsLengthMatchesJvmAndOobIndexTraps` pins `args.length == 0` with no argv and
asserts a NON-ZERO wasmtime exit for `args[7]` (explicit `unreachable`, measured exit
134); the refusal face re-pinned: `println(args)` (array format) and `for (a in args)`
still `WASM002` naming plan + #776 with NO artifacts. Gate hygiene: the split forced by
`check_500` landed WITH this slice — `WasmBackend` 629 -> 204 (module assembly only),
lowering/dispatcher/Ctx extracted to `WasmLowering` (447), `kof.readArgs` + `copyInto`
live in `WasmArgsRuntime` (192), `WasmStdoutRuntime` 503 -> 329; pure code motion, WASI
battery green after. MEASURE 08/10 (post-split + post-§625-fix tree): full 4-module suite = kof-compiler 4898 run/5F + kof-cli 593/3F — §627 2F + §628 3F = 5F deterministic EXTERNAL (other lanes) + JavaFX-env 1F + InteropTimeout load flakes 2F = 3F environmental; §625 GREEN post the `.30` prologue fix (`6572e6367`, Av1Coeffs 6/6 re-measured here); RingPrivilege GREEN this pass; ZERO WASI failures, battery 23/23 + PrologueSlotInit 2/2.

**Slice 15.3d-increment1 LANDED (08/10, lane `192.168.15.101:9092`):** RECORD ALLOCATION + INT/LONG FIELD ACCESS — a record value is a bump-heap block whose layout is `ClassLayout.build(IRClass)` (the SAME source Native uses: header + 8-byte slots), the receiver is an `i32` handle in the local slot (`lowerMethod` maps a record-typed local/param to `i32`); `WasmLowering` gains `KofNewObject` (bump `global 0` by `totalSize`, stash the handle in a scratch `obj` local, push it), `KofDup` (no-op — the handle the constructor left on the wasm value stack is reused), the record `<init>` (`CONSTRUCTOR` call: pops the argument values in reverse field order into a scratch `v` i64 local and `i64.store`s each at `obj + fieldOffset`), and `KofLoadField` (receiver `i32` handle + `fieldOffset` → `i64.load`). `WasmInstr.Mem` gains the real i64 memory opcodes `LOAD64=0x29`/`STORE64=0x37` (align 3) — the smallest mechanism, not a per-feature hack. **Scope honesty (Q7/R6):** only `Int`/`Long` fields (i64 on the stack) are built; a record with a `String`/`Bool`/`Char`/`Double`/nested-record field, `println(record)` (`toString`), `record == record` (`equals`) and record-in-`+` all require INSTANCE-method lowering (not yet present) and keep refusing `WASM002` naming plan + #776 with NO artifacts — never a silently-invalid module. Proof: `WasmWasiE2ETest` 7/7 under wasmtime v49.0.2 — `recordAllocationAndIntFieldAccessMatchTheJvmOracle` compiles `record Point(Int x, Int y) { var p = Point(1,2); println(p.x); println(p.y) }` for JVM + WASI and asserts byte-equal stdout (`1/2`); `recordToStringAndEqualityStillRefuseHonestly` pins the three still-open faces (toString / equality / a String-field record) to `WASM002` + no-artifacts. NEXT (increment 2): the `toString`/`equals`/record-concat faces land with instance-method lowering; then collections, then 15.4 flip LAST.
**Slice 15.3d-increment2-SLICE-A LANDED (09/10, lane `192.168.15.101:9092`):** `String.valueOf(Int|Long)` AS A VALUE (variable / inside `+`) — `WasmStdoutRuntime.kofIntToStr` formats digits in the SAME scratch window the proven-green `kof.writeInt` uses, then bump-allocates a `[len][digits]` heap handle with the `kof.strLit` pattern (`copyLoop`, +5 block) and returns the handle; `WasmLowering` routes valueOf-by-owner `String` that is NOT immediately `println` to `kof.intToStr` (Int|Long), treats a String receiver as IDENTITY (the JVM `+` desugar re-applies `valueOf` on an existing handle — measured in the emitted `_start`), and refuses everything else `WASM002` with NO artifacts (Q7); `WasmBackend` registers the helper with the string runtime. Proof: `WasmWasiE2ETest` 8/8 under wasmtime v49.0.2 — `stringValuesOfVariablesAndConcatMatchTheJvmOracle` (JVM+WASI same source, byte-equal `3 / -42 / 7! / x100`); WASI battery 26/26. NEXT: slice B = record field widths (Bool/Char/String-handle = i32 slots, Double = f64), slice C = inline `toString`/`equals`/record-concat via `kof.strEq`.
**Slice 15.3d-increment2-SLICE-B LANDED (09/10, lane `192.168.15.101:9092`):** RECORD FIELD WIDTHS + char/bool print parity — `WasmStdoutRuntime.kofWriteChar` now receives the i64 the 15.2 `emitLiteral` actually pushes for `Char` and wraps to an i32 local (the pre-existing `println(char)` module was INVALID — a latent false-green now pinned by `charAndBoolPrintMatchTheJvmOracle`); `WasmLowering` dispatches record `<init>` stores and `KofLoadField` loads by width: Int/Long/Char = i64 (`Mem.STORE64/LOAD64`), Bool/String-handle = i32 (`Mem.STORE/LOAD`), Double = f64 (`Mem.STORE_F64/LOAD_F64` = 0x39/0x2b align 3, new in `WasmInstr.Mem`) with per-function scratch `v32`/`vf64`; `operandTypeBefore` answers the field type so `println(recordField)` registers the right writer; `isStringField` recognizes record `String` fields; `usesStringOps` registers the string runtime for programs holding string literals/handles without any printed string (`Pair("ab", 7)` alone used to encode an unresolved `kof.strLit`); nested-record fields still refuse `WASM002` NO artifacts (`record-string-field` refusal flipped green; `record-nested-field` pinned). Proof: `WasmWasiE2ETest` 10/10 (`charAndBoolPrintMatchTheJvmOracle`, `recordFieldWidthsMatchTheJvmOracle` byte-equal JVM oracle under wasmtime v49.0.2); WASI battery 28/28. NEXT: slice C = inline `toString`/`equals`/record-concat via `kof.strEq`.
**Slice 15.3d-increment2-SLICE-C2 LANDED (09/10, lane `192.168.15.101:9092`):** RECORD `==`/`!=` CONTENT EQUALITY — the frontend `RecordEqualityLowerer` WASI arm desugars to the SAME opaque `kofRecordEq(L,R)` call the JS arm already uses (the plan's own precedent: "one single function call is an opaque expression for all backends"; the loop/`$pc` linearization never gets the cross-block merge it could not model, so it never HAS to). `WasmRecordCode.emitEquals` folds the fields INLINE in one straight-line block — accumulator locals `eqAcc`(handle L)/`eqEq`(handle R)/`eqAnd`(AND-fold) + `eqCmp`/`eqDbl` scratch, every `if` void-bodied so no value ever crosses a label; null semantics = `java.util.Objects.equals` (both null→1, one null→0, else field-wise content); widths: Int/Long/Char `i64.eq`, Bool `i32.eq`, String fields via NEW `kof.strEq` (length check + byte-scan loop over the bump-heap blocks), Double via `i64.reinterpret_f64`+`i64.eq` = JVM `Double.equals` bits (+0.0/-0.0/NaN parity); `!=` (EQ-vs-0 desugar) and the null-0 literal are intercepted as `i32` when the producer is the fold; `record == null` (reference branch of the desugar) = handle-vs-0 `i32.eq/ne`; the explicit `p.equals(q)` still refuses `WASM002` — instance-method dispatch is a separate slice. Bugs the byte-parity caught (Q0/Q5, fixed same commit): wasm `br` to a LOOP label is a CONTINUE — the strEq scan exits at depth 2 (the enclosing `if`), not 1 (infinite loop measured); the fold accumulator doubling as the L handle got clobbered by the seed — roles split `eqAcc`/`eqAnd`; `i32.and` opcode (0x71, an earlier 0x7c was `i64.add`). Gate hygiene: `WasmPrintCode` extracted from `WasmLowering` (622->593, `check_500` rc=0). Proof: `WasmWasiE2ETest` **12/12** under wasmtime v49.0.2 — `recordEqualityMatchesTheJvmOracle` same source JVM+WASI byte-equal across `Point`/`Pair`(String content-not-identity)/`Flag`(Bool/Char)/`Dbl`(bits)/`== null`/`!= null`/`!=`/`!`; `record-equality` refusal FLIPPED GREEN; refusal test renamed+re-pinned to `record-explicit-equals` + `record-nested-field`. WASI battery **30/30**. NEXT: nested-record fields + `Double`/`Double.toString` in record `toString`; then collections; then 15.4 flip LAST.

**Slice 15.3d-increment2-SLICE-C1 LANDED (09/10, lane `192.168.15.101:9092`):** RECORD `toString`/PRINT/CONCAT INLINE — new `wasm/WasmRecordCode.emitToString` expands the synthesized instance method AT THE CALL SITE (the backend has no virtual dispatch yet — the same shape decision as `CompilerRecordSupport`/`RecordEqualityLowerer`, the JVM oracle): `Name[f1=v1, f2=v2]` built from bump-heap handles (`kof.strLit`/`kof.strConcat`), Int/Long via `kof.intToStr`, Bool via new `kof.strBool`, Char via new `kof.strChar`, a String field reuses its own handle; `WasmLowering` routes `println(record)`, `record.toString()`, `String.valueOf(record)` and record-inside-`+` through it (operand type via `operandTypeBefore` over `ctx.flat`), `KofCheckCast` becomes a no-op and wide values stored into `Object` box-slots wrap `i64->i32`; `WasmBackend` closure-skips `toString`/`equals` on record owners, admits `recOk` println types and registers the string runtime + heap when `usesRecords`. **Scope honesty (Q7/R6):** `record == record` keeps refusing `WASM002` with NO artifacts — measured: the JVM `==` desugar merges the field-compare through CONTROL FLOW across labels (ternary + `i64` literals into an `i32` merge), which the loop/`$pc` linearization does not model; the never-wired `emitEquals`/`kof.strEq` scaffolding was DELETED (dead code is a stub, Q7 — it returns in slice C2 WITH the merge model). Gate hygiene: `WasmTypeOracle` extracted from `WasmLowering` (726 -> 569 tolerated; oracle/inference responsibility), `WasmStdoutRuntime` 477 under target. Proof: `WasmWasiE2ETest` **11/11** under wasmtime v49.0.2 — `recordToStringAndConcatMatchTheJvmOracle` (same source JVM+WASI byte-equal `Point[x=1, y=2]` / `v=Point[x=1, y=2]` / `Pair[a=ab, n=7]` / `Flag[c=k, ok=true]`), `recordToStringAndEqualityStillRefuseHonestly` re-pinned to the two still-open faces (equality + nested fields), `charAndBoolPrintMatchTheJvmOracle` stays green (a wrong println wrap added during work was caught by it). WASI battery **29/29**; `check_500` rc=0. NEXT: slice C2 = record `==` with the cross-block merge model; then nested-record fields + Double; then collections; then 15.4 flip LAST.

**Amendment (07/10, lane `192.168.15.101:9092`, 15.1-COMPLETE):** the enum
step was NOT deferred — roadmap TIER 15 defines 15.1 ITSELF as enum+plumbing
(maintainer order `D-WEB-WASI-DEFAULT-0710`, the order that promoted this
plan). `Target.WASM`/`Target.WASI` now EXIST: the canonical bare `wasm`/`wasi`
PARSE as real targets end-to-end, and the honest emitting refusal (WASM001,
naming this plan + unit 15.2 + #776, writing NO artifacts, never a JVM
fallback) lives in `TargetMatrix.validate`/`CompilerPipeline.lowerAndEmit`
(`WasmTargetGateE2ETest` 5/5). The long solecisms keep `.30`'s string gap
verbatim; both `.30` tests were re-pinned to this truth (solecism coverage
preserved).

## 7. WASM backend spec (PLANNED)

Module sections to emit: `Type, Import, Function, Table, Memory, Global,
Export, Start, Element, Code, Data`.

Mapping (Kof concept → IR → WASM):

| Kof concept | WASM representation | Status |
|---|---|---|
| function | `func` + type signature | PLANNED |
| local | `local` declaration | PLANNED |
| `Int` | `i32`/`i64` | **TBD** — which width matches Kof's `Int` overflow semantics (must equal JVM/Native golden, never JS double) |
| `Double` | `f64` | PLANNED |
| `Bool` | `i32` 0/1 | PLANNED |
| control flow | `block`/`loop`/`if`/`br`/`br_if`/`br_table` | PLANNED |
| exception (Kof's String-exception model) | no native mechanism → runtime convention (thrown-string global + `br` unwinding or host `throw`) | **DECISION REQUIRED** — frozen semantics, must map to observable behavior identical to other targets |
| call | `call` / `call_indirect` | PLANNED |

**TBD:** multi-value returns, tail-call, reference types vs i32 handles,
import/export naming ABI. Do not fix these before Phase 0.

## 8. Runtime WASM

Evaluation against the Native runtime (its code is the reuse candidate):

| Area | Reuse path | Status |
|---|---|---|
| allocator | native bump/mark-sweep design → wasm linear-memory allocator | needs adaptation (§9) |
| strings | `KofString` layout + content `==` | PLANNED reuse of layout, new memory substrate |
| arrays/objects | header (type/klass/size/fields) | PLANNED — layout must fit wasm addressing |
| runtime errors / panic (String-exception) | `kf_throw` model → wasm convention | DECISION REQUIRED (§7) |
| GC | see §9 | dependency |
| ABI | calling convention between Kof runtime fns and wasm functions | **DECISION REQUIRED** — Phase 0 deliverable |

Object representation in wasm (i32 indices into a handle table vs raw
linear-memory offsets) — **TBD**, Phase 0.

## 9. Linear memory

Model: `Kof object → Kof runtime → WASM linear memory (one per module)`.

Investigation points (no choices made): allocator strategy; alignment;
object layout; pointer representation (`i32` offset — wasm has no 64-bit
linear pointers pre-Memory64); growth (`memory.grow` + relocation problem —
growing invalidates offsets, ties directly to the GC/handle decision);
free; root tracking. **RESOLVED 28/09:** `D-WASM-GO` chose
**handles + a handle table** with the native mark-sweep GC design (`D-WASM-04`,
`D-WASM-05`) — the text above described the pre-decision state.

**WASM must not create a second memory-management semantics independent of
Kof's** (freeze rule 5): the same program must observe the same
allocation/reuse behavior class proven by the Native GC golden tests.

## 10. GC

**CURRENT:** Native mark-sweep is in development (G-0 riscv ✅, G-1..G-5,
roadmap). **PLANNED dependency:** WASM consumes the SAME design — safe
points, root maps, object references, allocation/collection metadata —
re-expressed over linear memory. Explicit: **WASM is blocked on the GC
work reaching x86 stable** (mark-sweep currently disabled on the main native
path per roadmap). If a phase ships before that, it ships **without GC**
(arena/bump only) and says so in the capability matrix — no hidden
"eventually collected" claim.

## 11. Strings

`String` in WASM follows the **existing Kof spec** (content `==` is frozen;
UTF-8/UTF-16 choice is governed by `docs/stdlib` strings spec + JVM/Native
golds — wasm follows, never diverges). To cover in Phase 0: length model,
allocation, concatenation (compile-time constant folding + runtime
`kf_str_concat`), comparison (content, byte-identical results across
targets), indexing/slicing (frozen semantics — see JVM `charAt`/`[]`),
host interop (JS strings are UTF-16, WASI bytes are UTF-8 — conversion lives
in the bridge, not in the String layout).

## 12. Records, objects, classes, dispatch

Records (immutable), mutable classes (fields + constructor), inheritance
and virtual dispatch map to **Kof semantics first**: accessor calls, vtable
or `call_indirect` + function-table — representation TBD, **not** borrowed
from JS prototypes or class syntax. The wasm module may use tables for
dispatch; that is an implementation choice that must preserve observable
behavior (golden cross-target).

## 13. Closures

Capture model (value vs reference) is frozen behavior (CURRENT: JVM/Native
differ-none by the `collectCaptures` fix era; `spawn`-lambda capture is
tested). WASM needs: environment object (heap-allocated in linear memory),
captured-variable access, lifetime tied to the closure object (GC
dependency!), function reference = (code-index, env) pair — `call_indirect`
cannot carry env alone: **curried `invoke(f, env)` convention, DECISION
REQUIRED**. Nested closures share the design.

**Risk section:** closures are the highest-risk wasm item precisely because
they couple function references × environment allocation × GC (§9–10).
Phase 3 lands them only after GC + function-table are green.

## 14. WASI spec

Capabilities to specify (per chosen WASI version): `stdin/stdout/stderr`,
`args`, `environ`, `clock`, `random`, `filesystem (preopened dirs)`.
**WASI version: TBD** — the spec evolves (preview1 vs preview2/component
model); the implementation decision must re-check the state of the WASI
project at its time, recorded here as open, not guessed. **DECISION
REQUIRED.**

Rule unchanged from WASM §7: WASI = which imports the module declares; same
backend, different import set (`wasi:cli`, `wasi:filesystem`, … names TBD
with the version).

## 15. Capabilities (join the CURRENT system, not a new one)

Mechanism that exists today: per-stdlib-call `supportedOn` + gap code +
`docs/backend-parity.md` matrix + `DomainGapCodesTest` pins. **PLANNED:**
add `WASM`/`WASI` columns when `Target.WASM` exists; until then the column
is the whole-target `WASM001` (already honest).

Illustrative matrix (**`?` = nothing invented; most cells are TBD until
Phase 1–4 measure them**):

```text
                JVM   Native   JS   WASM   WASI
filesystem       ✓      ✓      -    ?      ?     (JS - = measured current; WASM/WASI = TBD)
stdout/println   ✓      ✓      ✓    ?      ?
clock/random     ✓      ✓      ✓    ?      ?
networking       ✓      ✓      ~    -?     ?     (browser fetch vs wasi-sockets = different, §21)
browser/DOM      -      -      ✓    ?      -
```

**Architectural rule (CURRENT, freeze 5/R6):** an API unavailable on a
target must produce an **explicit compile-time diagnostic** (`XXX001`, like
`WASM001`/`PROC001`/`FFI001` do today). **No silent stubs, ever.**

## 16. Browser WASM

```text
Kof → WASM → Browser host → Web APIs
```

Distinct from KofJS: **KofJS = JavaScript target** (CURRENT: text emitter +
browser E2E with Chrome headless); **KofWASM = WebAssembly target**
(PLANNED: compiled module + JS glue host). The bridge (generated glue, PLANNED)
covers DOM, events, `fetch`, storage, canvas, console — **the wasm module
cannot touch DOM directly**; everything crosses the host boundary. The glue
is *host integration*, not part of the language.

## 17. JavaScript interop

`Kof WASM → JS host` needs: string crossing (UTF-16↔module bytes), numeric
boundaries, callbacks, object handles. Principle: **JS is a capability/host
integration when needed — not a mandatory dependency of running Kof WASM**
(a WASI program must run with zero JS). WASM core and browser-specific
host integration stay separated (different artifact sets, §31 phases).

## 18. KofUI

**CURRENT decision (D-UI-SCOPE):** kof.ui rules are authored for KofJS
today and for **Kof WebASM when `Target.WASM` exists**; never JVM/Native.
**PLANNED shape:**

```text
KofUI
 ├── shared abstraction (CURRENT: the kof.ui API surface — intent, not HTML)
 ├── KofJS implementation (CURRENT)
 └── KofWASM host implementation (PLANNED)
```

Same conceptual API, different backend/host implementation — **no API
duplication, no second UI language** (rule 9/philosophy: intent declared in
Kof, rendering belongs to the platform).

## 19. WASI for applications

Future use cases — **documented, none promised for the first release**:
CLI tools, servers, edge functions, plugins/sandboxes, embedded apps. WASI
lets a Kof program compiled to `.wasm` run outside the browser on
compatible hosts, under capability grants — see security §29.

## 20. Host and runtime

`.wasm` is a module — execution **always** needs a host. Decisions
registered, not invented (all **TBD**): official development runtime;
supported WASI runtime(s); runtime discovery (`kof run --target=wasi`
behavior when none is installed → honest diagnostic, never a silent
fallback); no permanent coupling of Kof to one external runtime without an
architectural decision. Current host reality (measured): no WASI runtime
installed → CI guards must `assumeTrue` until this lands.

## 21. Concurrency

Frozen semantics: `spawn`/`await`/`Handle<T>`/channels (freeze 6). WASM
reality to analyze (no assumptions): browser/main wasm has **no preemption**
— native threads are a feature (wasi-threads is deprecated; the modern
direction is shared-everything proposals — **TBD**). Possible honest
outcomes include: cooperative scheduler on the module; spawn mapped to
Web Workers (browser) with channel-over-postMessage bridge (DECISION
REQUIRED); WASI threading deferred with `SCHED00x`-style diagnostic. **The
Native/JVM pthread model must not be assumed portable.** Until each choice
is made, the capability cell is `?`/`-` with diagnostic — R6.

## 22. Networking

Four different worlds — document separately, relate to the CURRENT
`kof.http`/`kof.db` per-target states (`backend-parity.md`): browser-WASM
(`fetch` via host glue), WASI networking (`wasi-sockets`, version-TBD),
Native (CURRENT), JVM (CURRENT). Gaps recorded explicitly; **no fake API
covering an absent capability** — a call that cannot be served gets
`NET001`-family diagnostic on that target.

## 23. Modules / imports

`import foo` (Kof's own import system, CURRENT) stays Kof regardless of
target — **no wasm-specific module system**. Open questions for Phase 0
(linking, module boundaries, exports, dead-code elimination, dependency
packaging, runtime dependencies): how a multi-file Kof program becomes one
module vs several with `import`s between them; the packaging answer should
converge with the official package system (roadmap §23, CURRENT gap
`PKG001`).

## 24. CLI & build system

**PLANNED** (proposal only — until Phase N ships, `WASM001` stands):
target selection (`--target=wasm|wasi`), output (`.wasm` artifact + glue),
runtime flags, linking, host configuration, capabilities. Touch points
measured: `kof-cli` target parsing + `CompilerPipeline.compile` dispatch.
Do not invent flags beyond the existing `--target` pattern.

## 25. WAT / debugging

**PLANNED:** `kof build --target=wasm --emit=wat` as backend dev/debug tool
(the CURRENT precedent is `--emit-asm` on Native — same spirit). Future:
debug info (source-level mapping; the KofJS **source map V3** work is the
cross-reference for what a wasm DWARF/name-section story would need). No
implementation now.

## 26. Conformance

Principle: **semantic equivalence across targets when capabilities are
equivalent; never identical binaries.** **CURRENT** harness reality:
per-target E2E/golden classes + `docs/bugs-and-gaps/conformance-matrix.md`
(not a `tests/conformance/` tree — do not invent one; extend the existing
mechanism). **PLANNED** addition: wasm/wasi target suites following the same
golden style, plus the **differential oracle opportunity**: the WASM→Kof
`DECOMPILER.md` plan, if both exist, closes a round-trip loop (wasm emitted →
decompiled → recompiled → same behavior).

## 27. Test strategy

- **Compiler:** AST→IR→WASM structural tests (golden `.wat` snapshots).
- **Validation:** every generated module passes a wasm validator (binary
  well-formedness ≠ correctness — validation is the floor).
- **Runtime:** generated module executes under the chosen host (§20) →
  stdout/exit-code goldens.
- **WASI:** program behavior under a WASI host with preopened dirs.
- **Cross-target:** same program → JVM/Native/JS/WASM(/WASI) where the
  capability cell allows; divergence = bug or `XXX001` diagnostic, never
  silence (freeze 5).
- **Guards:** missing host/toolchain → `assumeTrue` skip + recorded gap
  (the 19/09 host has no wasm tooling — this is the honest-skip CURRENT
  pattern, R6).

## 28. Fuzzing

Property-based/fuzz focus on the emitter's correctness surface: control
flow, **stack typing** (wasm is stack-typed — the deepest new bug class for
this compiler), function signatures, locals, closures, memory ops, strings,
object layout. Relation to the CURRENT backend-correctness problem (§25 of
the test-architecture plan; fuzzing is already in the roadmap queue):
**generating a `.wasm` proves nothing — generating a *semantically valid,
deterministically executing* module is the bar.**

## 29. Performance

Future benchmarks (no promises): startup, function calls, integer/float
arithmetic, loops, arrays, strings, allocation, JSON — then compare
JVM/Native/JS/WASM/WASI. **CURRENT precedent:** the repo's benchmark
harness (roadmap 1.4/1.5, native benchmark matrix) shows the measurement
style; wasm joins as a column, with the same honesty rules (never compare
against a stub).

## 30. Security

Sandbox is wasm's default posture, but the spec locks: capability-based
host boundaries; memory isolation inside the linear memory (bounds);
filesystem access **only** through granted WASI preopens; network **only**
through host capability. **A Kof→WASI program must never obtain arbitrary OS
access without passing through the host's capability gate** — mirrors
CURRENT `FFI001` (native dynamic FFI rejected) posture: heavy footguns stay
gated with diagnostics.

## 31. Dependencies / prerequisites

Justified by code analysis (not padded):

| Dependency | Why | State |
|---|---|---|
| IR formality | a new backend needs a stable lowering input (today shared with native/script) | Phase 0 subject |
| Native GC (mark-sweep G-1..G-5) | §9–10: wasm memory story reuses it | in development |
| Runtime ABI (wasm) | closures/objects/errors all sit on it | **RESOLVED 28/09** — `D-WASM-GO` (`D-WASM-01..09`): direct backend, `Int`=i64, thrown-string unwinding, handles+handle table, explicit closure env |
| Capability system (supportedOn + gap codes) | §15: the join point | CURRENT |
| KofUI D-UI-SCOPE | §18: the frontend contract exists, wasm side waits | CURRENT decision |
| WASI runtime decision | §20 | **RESOLVED 28/09** — `D-WASM-GO`: WASI preview1, **wasmtime first** (the plan's own banner notes these markers are not still-open decisions) |
| Conformance harness style | §26 | CURRENT (extend) |

Module system & packaging: related to `PKG001` but **not** a hard blocker
(single-module output sidesteps it in Phases 1–4) — listed as soft.

## 32. Future phases

**Status MEASURED 21/10** (the plan's `doing:` header tracks the slices): phases
1 and 4 are PARTLY landed — `wasm/` is 15 source files, `kof build --target=wasm|wasi`
emit real modules, and the module RUNS under a WASI host (`WasmWasiNodeHostTest`
2/2, no skips). The subset is scalar-only: records/collections/exceptions/lambdas
are refused with `WASM002`, which is the boundary the table below describes as
phases 2–3. The **15.4 frontend flip** (the last row's precondition) is still
pending, so this plan is NOT concluded.

| Phase | Objective | Depends on | Components touched | Tests | Done when |
|---|---|---|---|---|---|
| 0 | Architecture/ABI spec lock | decisions §5,8,13 | TargetMatrix docs, IR notes | none | maintainer signs DECISIONS.md entries |
| 1 | Minimal backend | Phase 0 | `dev.kof.compiler.wasm` (new pkg), `Target.WASM` enum (rule 6), CLI | compile+validate `.wat` goldens | hello-world module runs in chosen host |
| 2 | Runtime + memory | GC direction | prelude (alloc/strings/objects) | unit + validation | strings/ints correct |
| 3 | Language parity | 1–2 | control flow, records, classes, closures (§13) | cross-target goldens | language suite green where capabilities allow |
| 4 | WASI | runtime decision | import set, `Target.WASI` (rule 6) | wasi-host behavior tests | files/clock/random/args goldens |
| 5 | Browser host | 3 | glue generator | browser E2E (Chrome-headless precedent) | same-source JS-vs-WASM parity demo |
| 6 | KofUI integration | 5 + D-UI-SCOPE | shared abstraction × wasm host impl | UI E2E | one kof.ui app → both backends |
| 7 | Conformance | 1–6 | matrix columns, fuzzing rig | §26–28 suites | matrix green-or-diagnosed; §32 gates below |

Each phase = a full vertical cut (Q7): ships complete-with-diagnostic,
never facade-complete.

## 33. Release gates (for the 15.4 flip AND a cut — the doc was already promoted 07/10)

**Measured 21/10:** this doc moved `future/` → `docs/` on 07/10, so the gates
below no longer govern the move — they govern the **15.4 frontend-default flip**
and any release cut that depends on WASI. Status of each, where measurable here:

- [x] real backend (not glue to another compiler) — **15 source files** in `wasm/`,
      emitting real `.wasm` (`kof build --target=wasi --output X` → `Default/Main.wasm`)
- [ ] every emitted module passes a validator — NOT VERIFIED HERE (`wasm-tools`
      absent in this environment; the E2E test skips honestly)
- [ ] every emitted module passes a validator
- [ ] runtime: memory + strings work
- [ ] language parity (records/objects/closures/control flow) on green goldens
- [ ] WASI at the chosen version with capability gates
- [ ] capabilities matrix columns WASM/WASI filled with ✓/~/-/diagnosed (no `?` left unexplained)
      — MEASURED NOT DONE: `backend-parity.md` still has only
      `| Feature | JVM | Native | KofJS | Notes |`; no WASM/WASI column exists
- [ ] execution tests + conformance + fuzzing rig green (toolchain guards honest)
- [ ] docs + training synchronized (EN/PT)
- [ ] CodeQL/CI not degraded by the new surface

## 34. Roadmap dependencies

Real relations (see §31 table): **roadmap §23** package system (`PKG001`,
soft), **Native GC** G-1..G-5 (hard for Phase 2+), **D-UI-SCOPE** (Phase 6
contract), **IMPLEMENTATION-UNIVERSAL-PLATFORM** R6/R7 (honest gaps +
per-target scope — governs the whole matrix), R12 (this stays future until
the maintainer promotes it), **rule 6** (enum/ABI/frozen-semantics touches
need her decision), the **test-architecture plan** (fuzzing/property
testing infrastructure feeds §28), and `qrcode-wasm-plan` PART 2 (frontend
coexistence strategy this spec implements compiler-side).

## 35. Decision registry (all open — none invented)

```text
D-WASM-01  WASM as first-class direct backend (vs transpile chains)   DECISION REQUIRED (§5)
D-WASM-02  Int width mapping i32/i64 + overflow parity               TBD (§7)
D-WASM-03  Exception model on stack-typed wasm                       DECISION REQUIRED (§7)
D-WASM-04  object handles vs raw offsets; growth/relocation           TBD (§9)
D-WASM-05  GC strategy (reuse native design?)                         TBD (§10)
D-WASM-06  closure env + curried invoke convention                   DECISION REQUIRED (§13)
D-WASM-07  WASI version                                               TBD (§14)
D-WASM-08  dev runtime / supported WASI runtimes / discovery          TBD (§20)
D-WASM-09  concurrency model (workers? cooperative? diagnostic-only)  DECISION REQUIRED (§21)
D-WASM-10  Target enum addition (frozen-surface adjacent)             rule 6 (§6)
```

## 36. Validation performed for this document (19/09)

- files live in `docs/` since the 07/10 promotion (EN + PT pair; they lived in
  `docs/development/future/` when this section was written 19/09)
- all CURRENT claims measured in code (`Target.java`, `TargetMatrix.java`,
  `CompilerPipeline.java`, `js/` package, `backend-parity.md` D-UI-SCOPE,
  roadmap GC lines, host toolchain check) ✓
- no future feature described as existing. **SUPERSEDED 21/10:** the parenthetical
  that used to read "wasm today = `WASM001` rejection" is no longer true — units
  15.1-15.3d landed and `build --target=wasm|wasi` emit real modules; only the
  15.4 frontend flip remains future
- no code was changed ✓; internal links resolve ✓; TBDs reviewed ✓.

**No implementation performed. Documentation only.**
