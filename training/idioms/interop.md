[English](interop.md) | [Português](interop.pt_BR.md)

# Idioms — Interop (JVM types and C FFI)

**Status:** partial (whitelist) · **Introduced:** 0.3.x (TIER 2.1) · **Updated:** 21/09 (D6 struct/array/out-buffer shapes LAND on the JVM — record by value in/out, scalar `T[]`→`ptr` copy-in, `Buffer(U8)` INOUT; see the FFI front in `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` 3.8b)

## What it is

Two surfaces, one rule: the platform already exists — do not rebuild it.
**(a)** JVM: any Java type on the classpath by qualified name. **(b)** C FFI:
`extern "<lib>" f(T): R` binds a native function (JVM via `java.lang.foreign`).

## Real API (measured in the compiler — 0.5.0-beta)

```kof
// (a) JVM interop — qualified name, no wrapper
var now = java.time.Instant.now()
println(now.toString())

// (b) C FFI — the JVM binds any SCALAR signature (R3 generalized 18/09):
extern "/lib/x86_64-linux-gnu/libm.so.6" cos(Double x): Double    // ok (1-arg)
extern "/lib/x86_64-linux-gnu/libc.so.6" atoi(String s): Int      // ok
extern "/lib/x86_64-linux-gnu/libm.so.6" fmod(Double a, Double b): Double  // ok — 1.5 measured
extern "/lib/x86_64-linux-gnu/libc.so.6" puts(String s): void     // ok — void binds
extern "/lib/x86_64-linux-gnu/libc.so.6" getenv(String n): String // ok — String return, "mel" measured
// The Kof function NAME is the C symbol (no alias syntax) — kof_fmod failed lookup, fmod works.
// Non-scalar types -> FFI001 compile-time diagnostic (JVM exception, 3.8b ✅ 20-21/09: a `record`
//   by value as arg/return and a scalar `T[]`->`ptr` BIND — FfiStructE2ETest 10/10, FfiArrayE2ETest 5/5).
// JS runner  -> SAME scalar ABI via KofJsFfiBridge (F2/F3 ✅ 18/09; FfiE2ETest 16/16) AND the D6
//   record/array/out-buffer shapes (R54/R55/R57/R58/R59 ✅ 21/09, JVM==JS); browser -> honest runtime
//   error (R7, no host); a genuinely unsupported shape (String[]/List/Handle) -> FFI002
// Native (x86-64/riscv64/aarch64) -> SAME scalar ABI binds DIRECT since #431 20/09 (§61 CLOSED, §369):
//   no dlopen — link-by-use of library() + call sym@PLT; String<->char* = UTF-8 payload at offset 24
//   (NULL->NULL); >=9 same-class args spill; String return = boundary copy (C buffer never freed);
//   riscv64/aarch64 glibc passes AND returns FP in fa0..fa7 (MEASURED under qemu — NOT ft0);
//   C stdio is flushed at exit; struct/array/callback/missing-library -> FFI001 at the decl line
//   Numeric arguments follow the ORDINARY Kof conversion rule (#549/§370 FIXED 20/09): `f(Float x)`
//     accepts `f(4.0 as Float)`, `f(4.0)` (Double->Float) and `f(4)` (Int->Float) with the SAME
//     result on JVM, Native and JS host; `sqrt(9)` (Int->Double slot) and `labs(i)` likewise.
//     What Kof does not convert (String/Bool in a numeric slot, Double->Int narrowing) is SEM014
//     at the call site — never bits reinterpreted by the slot class.

// (c) CALLBACKS (C2 ✅ + JS parity C3.2/C3.3 ✅, 18/09): a Kof function handed to C as a
// function pointer. Function-typed parameter + lambda at the call site;
// PRIMITIVE + String-arg callback ABI (synchronous, non-escaping):
extern "libcallback.so" kof_cb_add(Int a, Int b, (Int, Int) -> Int cb): Int
// call site — the lambda becomes the C function pointer (Linker.upcallStub):
kof_cb_add(20, 22, (x: Int, y: Int) -> x + y)   // 42 measured
kof_cb_mixed(3, 2.5, (i: Int, d: Double) -> i * d)  // mixed scalar ABI ok
kof_cb_slen("hello", (x: String) -> x.length())  // char* -> String arg (C3.4)
// JS (host runner) binds callbacks too (C3.2/C3.4 ✅ 18/09, byte-for-byte JVM~JS;
// browser = honest runtime degrade R7); struct/pointer-in-callback, a String
// RETURN, and callback-as-return -> FFI001 (JVM) — never a silent stub.

// (d) STRUCT / ARRAY / OUT-BUFFER (D6, JVM — 3.8b fatias 1–4, 20–21/09):
//   a `record` of scalar fields crosses BY VALUE (arg and return), a scalar
//   `T[]` crosses as a `ptr` with COPY-IN per call (read-only), and an
//   out-buffer is the nominal `Buffer(U8)` crossing INOUT (copy-in / call /
//   copy-back). A buffer is NEVER a reuse of `String`/`Byte[]` — distinct
//   ABI kinds (length, direction, mutability).
record Pt(Int x, Int y)
extern "libshapes.so" mkpt(Int x, Int y): Pt          // record by value (return; register or sret)
extern "libshapes.so" ptlen(Pt p): Int                // record by value (argument)
extern "libshapes.so" sumn(Int[] xs, Int n): Int      // scalar array -> ptr (copy-in; C never writes back)
extern "libshapes.so" fill(Buffer(U8) b, Int n): Int  // out-buffer INOUT (copy-back after the call)
// call site:
var xs = new Int[3]                                   // `new Int[n]` is the array surface (not `[...]`)
var b = buffer.alloc(4)                               // Buffer(U8) — lifetime is automatic (no malloc/free)
fill(b, 4)                                            // C writes into the buffer
println(b.bytes())                                    // Byte[] clone (read it back)
println(ptlen(Pt(1, 2)))                              // 2 (record passed by value)
// Native: record/array/buffer externs = FFI001 (honest gap, R6 — the Native
// struct/sret ABI is 3.7). JS binds the same D6 shapes since R54/R55/R57/R58/R59
// (byte-for-byte JVM==JS). The scalar ABI binds on every target.
```

## Reflection at the boundary — `interop.schema(R)` (X6, `D-INTEROP-REFLECT`)

A **read-only** view of a `record`'s structure, available **only at the interop
boundary** (Arrow/Parquet/ML schemas), so external data binds to Kof records
without hand-written mappers. It is a **compile-time intrinsic** — zero runtime
reflection, so the same output on every target (no `REF001` gap). Enabled by an
explicit `import kof.interop`; resolves to an immutable `List<Field>` with the
compiler-provided `record Field(String name, String type)`, in declaration order.

```kof
import kof.interop

record Order(String id, Double amount, Long qty)

main() {
    for (var f in interop.schema(Order)) {
        println(f.name() + ":" + f.type())   // id:String, amount:Double, qty:Long
    }
}
```

An invalid use is an honest diagnostic (R6), never silence: an unknown member of
`interop` → `INTEROP002`; wrong arity, a value, a class or an enum (not a
`record`) → `INTEROP001`. An `entity` counts as a record. It is **not** a
language foundation: no runtime metaprogramming, no dynamic dispatch, no write
path.

## BAD → GOOD

| ❌ BAD | ✅ GOOD | Why |
|---|---|---|
| binding a symbol under a different Kof name (`kof_fmod`) | the NAME is the C symbol (no alias syntax, measured 18/09) — bind `fmod`, wrap in a Kof fn for friendly names | multi-arg/`void`/`String`-return already bind since R3 18/09 — do NOT hand-emit bytecode to bypass the compiler |
| assuming the lib path is checked at compile time | treat missing lib/symbol as a **runtime** `kof_ffi_*` failure | the path resolves at runtime (`SymbolLookup`), not compile time |
| storing the callback pointer to call LATER (atexit/signal/async) | keep callbacks synchronous and non-escaping | escapantes exigem política de vida/GC-rooting (R12) — ficam `FFI001`, nunca stub pendurado |
| reimplementing sin/cos/strcmp in Kof | bind the system lib (any scalar shape since 18/09) | complexity belongs to the platform (iron rule 2) |
| assuming `library()` means the same thing on every target | on JVM/JS it is the dlopen path; on **Native** it is resolved **by basename at LINK time through the sysroot** (`libc.so.6` → `-l:libc.so.6`; an absolute HOST path is wrong-arch cross) | Native has no FFM: a bound `extern` is a `call sym@PLT` + link-by-use (#431 20/09, §369) |
| reusing `Byte[]`/`String` for a C out-buffer | declare the nominal **`Buffer(U8)`** in the `extern` and create it with `buffer.alloc(n)` (D-R3-BUFFER/D6-3) | an out-buffer is mutable and bidirectional (copy-in + copy-back); `T[]` is copy-in read-only and `String`/`char*` is read-only — distinct ABI kinds |
| hand-emitting bytecode for a struct/array/out-buffer call | declare the `record`/`new T[n]`/`Buffer(U8)` in the `extern`; the compiler classifies the ABI (`AbiLayout`) | complexity belongs to the compiler (iron rule 2); a hand-rolled ABI is a silent bug on the next target |
| hand-writing a per-record mapper/schema (field names + types duplicated in a string) | derive it from `interop.schema(R)` at the boundary | the compiler already knows the record structure — zero runtime reflection, identical output on the 4 targets |
| hand-rolling `process.spawn("python3","-c",...)` + manual JSON per call | `import kof.interop` + `var py = KofPy(source)` + `py.callInt("sq", listOf(5))` | the engine is stdlib (fatia 1 X2 26/09): typed result is the METHOD name, args are a homogeneous typed Kof list; RPC lines, spec quoting and traceback naming (`INTEROP004`/`INTEROP006`) belong to the platform — session = the source (definitions persist; mutated globals do not), and the face refuses with `INTEROP005` where the process runtime is unproven (cross §513, ANDROID/MCU) |

## See also

`docs/language-reference/syntax.md` (§FFI to C), `grammar.md`
(`extern-declaration`), `modules.md` §6; gaps `FFI001`/`FFI002`;
R3 landed: JVM arbitrary scalar (arity/void/String-return, 18/09) + JS host parity (3.6.F2/F3 ✅ 18/09) + **callbacks bind on JVM AND the JS host runner, byte-for-byte parity (C2 ✅ + C3.2/C3.3/C3.4 ✅ 18/09 — primitive + `String`-arg callbacks; `JvmFfiCallbackE2ETest` incl. `jvmAndJsCallbacksMatchByteForByte` and `stringCallbackArgsBindAndMatchJvmJs`)** + **D6 struct/array/out-buffer shapes on the JVM (3.8b fatias 1–4 ✅ 20–21/09: record by value in/out, scalar `T[]`→`ptr` copy-in, `Buffer(U8)` INOUT; `FfiStructE2ETest` 10/10, `FfiArrayE2ETest` 5/5, `BufferE2ETest` 4/4, `BufferFfiE2ETest` 4/4)** + **the same D6 shapes on the JS target (3.8b bridge ✅ 21/09 — R54 record arg, R55 scalar `T[]`→ptr copy-in, R57 `kof.buffer` namespace, R58 `Buffer(U8)` INOUT, R59 record return by value; byte-for-byte JVM==JS: `FfiStructE2ETest#structReturnByValueJsParity`, `FfiArrayE2ETest#arrayParamByValueJsParity`, `BufferE2ETest#allocAndBytesJsParity`, `BufferFfiE2ETest#bufferInoutCopyInCopyBackJsParity`)** + **the Native scalar ABI binds on all 3 archs (fatias 1–2 ✅ 20/09 — §369, §61 CLOSED: `FfiNativeE2ETest` 16/16 x86-64 + `FfiNativeCrossE2ETest` 6/6 riscv64×aarch64 byte-identical under qemu)**; **decided 21/09:** variadics = none (`D-R3-3.5`), opaque `Handle` + `Buffer(U8,INOUT)` (`D-R3-3.3` — Buffer landed, `Handle` waits on the RAII front). Remaining (cross-lane/later): Native struct/sret (3.7), callbacks/upcalls on Native (no mechanism — `FFI001`), `Handle` lifetimes (`future/scoped-resources-plan.md`).
