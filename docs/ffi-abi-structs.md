# FFI struct/array ABI — spec D6-A (CONCLUDED 23/09 — all slices landed, promoted to `docs/`)

[English](ffi-abi-structs.md) | [Português](ffi-abi-structs.pt_BR.md)

**Status:** **COMPLETE — D6 DECIDED (09/20) + all slices landed (23/09) — moved to `docs/` per three-states rule.** `docs/development/DECISIONS.md`
§D-FFI-STRUCT. D6-1 = B (`D-FFI-STRUCT-B`, 21/09: new mutable `struct` by-ref;
`record`s stay by-value read-only, `Buffer(U8)` already covers the out-buffer) ·
D6-2 = only `new T[n]` · D6-3 = `Buffer(U8, INOUT)`, no new syntax · D6-4 =
implement the full sret · D6-5 = confined arena per downcall. **D6-1 B approved
spec-first 21/09 (`D-FFI-STRUCT-B`)**: the mutable `struct` surface is designed
here (§4/§6) and reviewed before any parser/typer diff (rule 11). The §4 proposal
text is kept for its measured reasoning. This document stays DESIGN-ONLY.
**Execution after approval:** compiler lane (tracker line 3.8) + native lane (3.7).
**Landed 20/09 (decision-free slice):** 3.8a `AbiLayout` — the layout/
classification substrate, with golden measured on the three ABIs (§6.1). The
binding (3.8b/3.7) proceeds under the D6 decisions above.
**Landed 20/09 (3.8b fatia 1):** JVM `record`→C struct **by value as an
argument** (`@` token; `FfiStructE2ETest` 6/6).
**Landed 21/09 (3.8b fatia 2):** JVM `record` **return by value** (register
and sret paths; `@`+`:`-encoded binary name, canonical-constructor
reconstruction; `FfiStructE2ETest` 10/10). Native struct = 3.7 (fatia 1 landed
21/09 — see below).
**Landed 21/09 (3.8b bridge JS · D6-1):** the JS runner now binds a `record`
**by value as ARGUMENT** — token `@<n><chars>` carries the field layout on the
wire (the host cannot reflect `RecordComponent` of a GraalJS object), the record
exposes `__kof_ffi_fields()` in declaration order, and `KofJsFfiMarshal` packs
the `StructLayout` (same offsets/tail-padding as the JVM) in the call arena
(D6-5). Proof: `structParamByValueJsParity` — `sumpoint(Point(3,4))=7`,
`scale(Point(2,3),2.0)=10.0` and `parammix(ParamMix(3,2.5,4))=9.5` (j/d/i layout)
byte-for-byte JVM==JS. JS struct **return** and JS `Buffer` stay `FFI002`.
**Landed 21/09 (3.8b fatia 3 · D6-2):** JVM scalar array **`T[]`→C `ptr`**,
**copy-in per call** (token `p`+element char; `new Int[n]` crossing as
`int*`). The Java array is not pinned nor aliased — the callee cannot write
back (that is D6-3's out-buffer, `Buffer(U8, INOUT)`). `String[]` (array of
pointers) stays FFI001; Native keeps its gap code. `FfiArrayE2ETest` 5/5.
**Landed 21/09 (3.8b fatia 3 bridge JS · D6-2):** the JS runner now binds a
scalar array **`T[]`→C `ptr`** with the same **copy-in per call** semantics —
`KofJsFfiMarshal.packArray` reads the guest JS array and copies the elements
into the call arena (`p`+element char; the C cannot write back). Proof:
`arrayParamByValueJsParity` (`sumn(Int[1,2,3])=6`, `sumd(Double[1.5,2.5])=4.0`,
and `fill` proves no aliasing: `11/11/5`) byte-for-byte JVM==JS.
**Landed 21/09 (3.8b fatia 4 bridge JS · D6-3):** the JS runner now binds
`Buffer(U8)` as an INOUT param too — `KofJsFfiMarshal.packBuffer` copies the
bytes out of the guest `Uint8Array` into the call arena, the `B` token maps to
`ADDRESS`, and the copy-back after the downcall writes the C's result back into
the guest buffer (parity with `kof_ffi_buffer_in`/`_out`). Proof:
`bufferInoutCopyInCopyBackJsParity` (`20/[10, 10]/40/[20, 20]`, the +10
accumulating across calls) byte-for-byte JVM==JS.
**Landed 21/09 (3.8b fatia 2 bridge JS · struct return):** the JS runner now
binds a struct **return** too — the return token is `@<n><chars>` (layout on
the wire), the bridge materialises the by-value struct in the call arena (the
Linker gets the arena as the leading `SegmentAllocator`) and reads the fields
into an `Object[]`, and the record's static `__kof_ffi_from` reconstructs the
instance through the canonical constructor (coercing `Long`→`BigInt` etc.;
parity with the reflective `kof_ffi_read_struct`). Proof:
`structReturnByValueJsParity` (`Point`/`Big`/`Mix`/`ParamMix` — register and
sret paths, plus a `Long` field) byte-for-byte JVM==JS. **The whole JS param +
return surface is done.**
**Landed 29/09 (#651 fatia A2 · D6-3 · native `Buffer(U8)` INOUT x86-64):** the
native x86-64 extern now binds `Buffer(U8)` as an INOUT parameter. Unlike the
JVM/JS arena copy-in/copy-back, the native Kof Buffer is already contiguous
memory, so the emitter passes the payload address (`obj+24`) straight to the C —
the C write **is** the copy-back (one INTEGER register, like a `char*`). Proof:
`BufferFfiE2ETest#bufferInoutCopyInCopyBackNativeParity` with a real `.so`
(`20/[10, 10]/40/[20, 20]`, the +10 accumulating across calls) byte-for-byte
JVM==Native; `InteropIdiomsCompileTest#nativeShapeExamplesBindOnX86` proves the
documented shapes no longer emit `FFI001`. The cross riscv64/aarch64 `B` face
landed in fatia B (`#651`) — the same payload pointer `obj+24`, byte-for-byte
JVM==riscv64==aarch64 (`BufferFfiE2ETest#bufferParamCrossBindsAndMatchesJvm`).
**Landed 21/09 (3.7 fatia 1 · native struct param, register path):** the
x86-64 SysV backend now binds a `record` scalar-fields struct **by value as an
argument** — `FfiStructLayout` classifies via `AbiLayout` and the call-site
packs each eightbyte straight into the destination register (INTEGER via
shift/or from the Kof 8-byte slots; SSE via `movq`/`movd`), no scratch spill.
The gate (`CompilerPipeline.nativeExternBound`) keeps everything else honest
`FFI001`: structs that go to memory (SysV MEMORY / > 16 B), an SSE eightbyte
holding more than one field, or a struct that does not fit the remaining
registers. Proof: `FfiStructE2ETest` `structParamByValueNativeRegisterPath`
(`Point`/`MixIF` int+float in one eightbyte/`Time` long+double) byte-for-byte
JVM==Native + `FfiStructLayoutTest` 3/3 (classification, no C toolchain).
**Landed 21/09 (3.7 fatia 2a · native struct return, register path):** the
x86-64 SysV backend now binds a `record` **returned by value** when it fits the
registers (≤ 16 B): the call-site saves the return eightbytes (`rax`/`rdx` +
`xmm0`/`xmm1`) to the stack, allocates+initialises the Kof object and copies
each field out of its eightbyte (shift + width extension) into the Kof 8-byte
slot. Proof: `FfiStructE2ETest` `structReturnByValueNativeRegisterPath`
(`Point` = 1 INTEGER eightbyte, `Big` = 2 INTEGER eightbytes, `Mix` = SSE+INTEGER)
byte-for-byte JVM==Native.
**Landed 21/09 (3.7 fatia 2b · native struct return, sret > 16 B):** the
x86-64 SysV backend now binds the **sret** path (SysV MEMORY, `> 16 B`,
hidden pointer — D6-4): the caller passes the hidden pointer in `%rdi` and the
callee fills the buffer. The call-site allocates the Kof object **before**
popping the args (allocation is a C call and would clobber the caller-saved arg
registers; the args are still on the operand stack above `kof_alloc`'s frame),
places the raw return buffer on the stack, passes its address in `%rdi`, and
copies each field from its C offset straight into the Kof slot (natural scalar
width). The hidden pointer consumes one INTEGER register, so
`x86Bindable(paramTypes, 1)` shifts the params (`rdi`→`rsi`…). Proof:
`FfiStructE2ETest` `structReturnSretNative` (`ParamMix` = `Long,Double,Int` =
20 B) byte-for-byte JVM==Native; `FfiStructLayoutTest.sretReturnAndReservedIntRegister`
(when the 1 reserved register pushes a param struct out of the regs the call is
not bindable).
**Landed 22/09 (3.7 fatia 3 · cross INTEGER struct return, register path):** the
riscv64 LP64 / aarch64 AAPCS64 emitters now bind a `record` **returned by value**
when every field is INTEGER-class and the struct is ≤ 16 B (proven with libc
`div` → `div_t { int quot; int rem; }`): the call-site saves the return words
(`a0`/`a1`; `x0`/`x1` under AAPCS64), allocates+initialises the Kof object
(`kof_alloc`/`kof_init_object`) and extracts each field from its word by natural
width. Floats/HFA, > 16 B and the struct **parameter** path on the cross remain
an honest `FFI001` (R6) — the param side is also tooling-blocked here (no cross C
compiler for a fixture `.so`, §365). Proof:
`FfiNativeCrossE2ETest` `riscv64StructReturnViaLibcDiv`/`aarch64StructReturnViaLibcDiv`/
`crossStructReturnAgreesBetweenArchs` (qemu, golden `3\n1` = the JVM oracle) +
`FfiStructLayoutTest.crossIntReturnIsBindableOnlyForIntegerRegisterPath`.
`T[]`/`Buffer` (faces 1–2, `D-MEM-FFI-CROSS-FULL`) and the memory-path struct
RETURN (face 3, below) have since landed; a >16 B by-value struct **param** and
callbacks stay an honest `FFI001`/`FFI002`.
**Landed 22/09 (3.7 fatia 4 · cross struct PARAM, INTEGER register path):** the
riscv64/aarch64 emitters now bind a `record` scalar-INTEGER-fields struct **by
value as an argument** — gate `nativeExternBound` accepts struct params on the
cross only via `FfiStructLayout.crossIntRegisterOnly`/`crossBindable`, and
`NativeFfiCall.emitRiscv` packs each eightbyte into the integer registers
(`a0`/`a1`; `x0`/`x1` on AAPCS64 via the translator). Float/HFA, > 16 B
(BYREF/MEMORY) stay honest `FFI001` (R6). Proof:
`FfiCrossStructParamE2ETest` 5/5 (cross-built `.o` fixture assembled with the
cross `as`, called under qemu on both archs, golden `42/2/6` + negative field +
3×int=12 B/2 words + struct+scalar mix, plus 2 gate rejections without
toolchain).
**Landed 30/09 (`D-MEM-FFI-CROSS-FULL` face 3 · cross memory-path struct
return/sret):** the riscv64/aarch64 emitters now bind a `record` **returned by
value** in the memory path (> 16 B). This is arch-aware, unlike faces 1–2: the
indirect-result pointer is **`a0` on RISC-V LP64** (the first real argument
shifts to `a1`) but **`x8` on AAPCS64** (first real argument stays `x0`) —
measured with cross-gcc. Gate `FfiStructLayout.crossMemoryReturn` accepts a
`byMemory()` return and `crossBindable(List,int)` reserves the RISC-V sret
register; `NativeFfiCallRiscv` allocates the C buffer at the call prologue,
keeps its pointer in a reserved frame scratch slot across the call, loads it
into `a0`/`a7`→`x8` and reconstructs each field from its C offset. Proof:
`FfiNativeStructReturnE2ETest` 2/2 — a real cross `.so` returning
`Big{long,long,long}` (24 B), byte-for-byte JVM (FFM oracle) == x86-64 ==
riscv64(qemu) == aarch64(qemu); `FfiStructLayoutTest#crossMemoryReturnIsSretOnlyForLargeStructs`.
The same face also binds the **>16 B by-value parameter**: measured with
cross-gcc, both archs pass it as a pointer (`a0`/`x0`), so the emitter passes
the object payload `obj+16` as one INTEGER (`FfiStructLayout.crossByMemory`).
Proof `FfiCrossStructParamE2ETest` 8/8 (`bigsum(Big,long)` → `142` under qemu on
both archs); `FfiStructLayoutTest#crossByMemoryStructParamCountsAsOnePointer`.


## 1. What exists today (measured 19/09, not remembered)

`extern name[("lib")] (params): Ret` lowers to a signature token
(`FfiSignature.java`): `i`=Int, `j`=Long, `f`=Float, `d`=Double, `b`=Boolean,
`S`=String (`char*`), `v`=void return; `@`=record by value (fatias 1–2),
`p<elem>`=scalar array `T[]`→`ptr` with copy-in (fatia 3); a callback param is
the nested token `(<ret><params>)`. Anything the map does not cover is a
**compile-time honest gap**: `FFI001` (JVM/Native not bindable) / `FFI002`
(JS) — `CompilerPipeline.java:225-236`, R6 (never a silent stub).

| Surface | JVM | Native | JS |
|---|---|---|---|
| scalar downcall | ✅ `kof_ffi` FFM (`JvmFfiRuntime.java:142+`) | ✅ **direct `call sym@PLT` on x86-64/riscv64/aarch64** (#431 slices 1–2, 20/09, §369 — link-by-use, no `dlopen`) | ✅ host bridge `KofJsFfiBridge` (browser degrades honestly, R7) |
| callbacks/upcalls (3.4) | ✅ `Linker.upcallStub` | ❌ `FFI001` (no mechanism) | ✅ host |
| String = `char*` | ✅ in + out | ✅ in (payload off 24) + out (boundary copy) | ✅ |
| **struct (record, scalar fields)** | ✅ **by value in + out** (`@` token, 3.8b fatias 1–2, 20–21/09) | ◐ **by value param + return, register path *and* sret x86-64** (3.7 fatias 1–2b, 21/09); cross **return** INTEGER ≤ 16 B binds (fatia 3, 22/09) and **memory-path return/sret** binds (30/09 face 3, `D-MEM-FFI-CROSS-FULL`); cross `T[]`/`String[]`/`Buffer` bind (faces 1–2 + `#651` B) and the >16 B by-value **param** binds (30/09 face 3, BYREF pointer `a0`/`x0`, `FfiCrossStructParamE2ETest`); float/HFA on riscv64/aarch64 → `FFI001` (3.7) | ✅ **by value IN + OUT** (IN: `@<n><chars>` + `__kof_ffi_fields`; OUT: `@<n><chars>` return + `__kof_ffi_from`; bridges 21/09) |
| **scalar array `T[]`→`ptr`** | ✅ **copy-in per call** (`p<elem>` token, 3.8b fatia 3, 21/09; no write-back) | ✅ **copy-in** for `Long[]`/`Double[]`/`Int[]`/`Float[]`/`Bool[]` — x86-64 (3.7 steps 1–2, 22/09) AND cross riscv64/aarch64 (30/09, `D-MEM-FFI-CROSS-FULL` face 1, `kof_ffi_pack_array`); `FfiNativeArrayE2ETest` (JVM==riscv64==aarch64); `String[]` (array of pointers) still `FFI001` | ✅ **copy-in per call** (`packArray` bridge, 21/09; no write-back) |
| **out-buffer `Buffer(U8)` INOUT** | ✅ **copy-in / call / copy-back** (`B` token + `buffer.alloc`/`Buffer.bytes()`, D6-3, 21/09) | ✅ **payload pointer `obj+24` (the C write is the copy-back)** on x86-64 (#651 fatia A2, 29/09); cross riscv64/aarch64 → `FFI001` | ✅ **copy-in / call / copy-back** (`B` token + `packBuffer`/copy-back after the downcall, bridge 21/09) |
| non-scalar array / opaque (e.g. `String[]`/`List<T>`/`Handle`) | ❌ FFI001 | ❌ FFI001 | ❌ FFI002 |

JVM scalar→FFM mapping (measured): `i→JAVA_INT, j→JAVA_LONG, f→JAVA_FLOAT,
d→JAVA_DOUBLE, b→JAVA_BOOLEAN, S→ADDRESS`, non-scalar token falls back to
`ADDRESS` only inside the callback path (`JvmFfiRuntime.java:88-106,144-145`).

**Measured wart (FIXED 21/09 — 3.8b fatia 2, D6-5):** downcall arena policy is
now **confined per call, closed in `finally`** for the scalar helpers
(`kof_ffi_i`/`kof_ffi_si`/`kof_ffi_dd`) and for `kof_ffi`. Before, `i`/`dd` used
`Arena.global()` (the `libraryLookup` handle never freed → leak per call) and
`si` opened a confined arena without closing it. Proof: `FfiE2ETest` 17/17
(`scalarHelpersRepeatStableUnderConfinedArena`: 300× each helper, idempotent).

## 2. Why "struct" is harder than it looks (the real cost)

The JVM side is nearly free: FFM already understands
`MemorySegment`/`StructLayout` and the JDK's own SysV ABI implementation
does classification. **The cost concentrates in the Native asm backend**,
which must implement struct classification per target ABI by hand
(§3) — that is why the tracker splits 3.8 (layout+JVM) from 3.7 (native),
and why this spec is written before any code.

## 3. Layout and calling rules per ABI (normative references)

Natural alignment (`alignof` field), size rounded up to `alignof` struct,
trailing padding included; no `#pragma pack` in v1.

| ABI | Pass-by-value rule (summary) |
|---|---|
| x86-64 SysV | classify each *eightbyte*: INTEGER / SSE / SSEUP / NO_CLASS ≤ 8 fields total; ≤ 16 B of INTEGER-class → two int regs (`rdi…`), ≤ 16 B SSE → XMM; anything bigger → **memory** (stack), caller-allocated copy |
| aarch64 AAPCS64 | HFA check (≤ 4 homogeneous float); otherwise ≤ 16 B → core regs `x0…` (by eightword class), > 16 B → stack; `w` register for the upper half when mixed |
| riscv64 LP64D | **MEASURED 20/09 (corrects the draft prose "packed into doublewords a0…a7"):** a struct ≤ 16 B with **≤ 2 fields** is *flattened* — floating fields to `fa0/fa1`, integer fields packed into `a0/a1` (`Time(Long,Double)`→`a0`+`fa0`; `{Float,Int}`→`fa0`+`a0`); with **3+ fields** it is packed into integer doublewords (`{Int,Int,Int}`→`a0,a1`); > 16 B → **reference** (pointer to caller copy), `Byref` class. The host `riscv64-linux-gnu-gcc` 13.3 is LP64D (hard-float), which is why the draft's soft-float packing did not match |

Three worked examples the implementation tests must reproduce bit-exactly:

| Kof shape | C shape | size | align | SysV classes |
|---|---|---|---|---|
| `Point2(Int x, Int y)` | `struct{int,int}` | 8 | 4 | INTEGER (1 eightbyte) |
| `Mixed(Bool b, Int n, Float f)` | `struct{_Bool,int,float}` | 12 | 4 | padding after `b`; eightbyte 0 (b+n) = INTEGER, eightbyte 1 (f) = **SSE** — MEASURED (GCC 13.3, x86-64 `-O0 -S`): first eightbyte in `%rdi`, `f` in `%xmm0`; offsets n=4, f=8 (corrected 20/09: the draft said INTEGER+INTEGER) |
| `Time(Long s, Double d)` | `struct{int64_t,double}` | 16 | 8 | INTEGER + SSE (SysV; MEASURED: `s`→`%rdi`, `d`→`%xmm0`), 2 eightwords (aarch64). Kof has no `Int64` — the 64-bit integer is `Long` (corrected 20/09) |

## 4. Design decisions — **DECIDED** (D-FFI-STRUCT, maintainer 09/20/2026; rule 6)

> **Decided:** D6-1 = **B** (`D-FFI-STRUCT-B`, 21/09: a new mutable `struct`
> by-ref; `record`s stay by-value read-only; `Buffer(U8)` covers the
> out-buffer) · D6-2 = **only `new T[n]`** binds to `ptr` · D6-3 =
> **`Buffer(U8, INOUT)` with no new syntax** · D6-4 = **implement the full
> sret** · D6-5 = **confined arena per downcall**. Authority:
> `docs/development/DECISIONS.md` §D-FFI-STRUCT. The proposal text below is kept
> for its measured reasoning.

- **D6-1 · which Kof value maps to a C struct?**
  A) `record` (structural, immutable, already zero-ceremony — recommended default);
  B) a new mutable `struct` declaration (needed for *in/out* buffers);
  C) both, with records = by-value read-only and `struct` = by-ref.
  **Superseded:** the decision was written — `D-FFI-STRUCT` (20/09) then
  `D-FFI-STRUCT-B` (21/09) fixed **D6-1 = B** (the mutable `struct` by-ref;
  `record`s stay by-value read-only).
- **D6-2 · array mapping.** `List<Int>` is boxed (JVM `ArrayList`) — binding
  it means copying to native memory per call. Proposal: primitive arrays
  (`new Int[n]`, which already exist) bind to `ptr` (no implicit length
  param — the C API decides), `List<T>` stays FFI001 until a boxed-unboxing
  benchmark proves otherwise. **✅ fatia 3 LANDED 21/09 (JVM, copy-in per
  call, `p<elem>` token) — the proposal above, exactly; `List<T>` still not
  bound.**
- **D6-3 · out-parameters.** No new syntax in v1: out-buffer = `new Byte[n]`
  crossing as its OWN ABI kind — `Buffer(U8, INOUT)`, copy-in / call / copy-back —
  **never the `S` token** (corrected 20/09: `S` = `String` = NUL-terminated UTF-8 `char*`,
  read-only; a buffer differs in mutability, length, direction and lifetime, so it
  cannot reuse `S`; `CString`, `Buffer`, `Pointer`, `OpaqueHandle` and `Struct` are distinct
  ABI types even when all become an address in a register). Length stays an explicit C
  argument. Pointer-in-struct
  fields = out of scope (opaque handles are 3.3, separate decision).
  **Landed 21/09 (D-R3-BUFFER/D-R3-HANDLE-LIFETIME):** the nominal spelling is
  **`Buffer(U8)`** (not a reuse of `Byte[]`), created with
  **`buffer.alloc(Int) : Buffer(U8)`** — the programmer never allocates/frees
  (lifetime language-managed; `Handle` follows the same automatic rule).   Slices
  (JVM + x86-64 Native surface, #651 fatia A1): `buffer.alloc` + `Buffer.bytes() : Byte[]` (`BufferE2ETest` JVM/JS/x86 parity) and
  `Buffer(U8)` as an `extern` INOUT parameter — **copy-in / call / copy-back**
  (`BufferFfiE2ETest` 4/4 with a real C shim: writes accumulate across calls,
  proving copy-in reads and copy-back writes). The Native x86-64 namespace/print
  surface landed in #651 fatia A1 (28/09) and the Native FFI `B` token landed in
  #651 fatia A2 (29/09) — the emitter passes `obj+24` (payload) directly on
  x86-64, proven by `BufferFfiE2ETest#bufferInoutCopyInCopyBackNativeParity`;
  cross riscv64/aarch64 remains honest until fatia B (R6-SCOPE: incremental,
  declared gaps).
- **D6-4 · return-by-value > 16 B.** SysV hidden-pointer (sret) / AAPCS64
  hidden-x8 / LP64 reference — the *JVM* Linker hides this; the *asm*
  backend must implement sret explicitly. Flag: this is the single biggest
  native-lane cost; slices in §6 isolate it.
- **D6-5 · String/arena ownership (fixes the §1 wart).** Proposal:
  confined arena per downcall, closed after the call; returned `char*`
  is **copied then never owned** (Kof String is immutable — the C pointer
  must not outlive the call unless the C API documents ownership
  transfer, which is the 3.3 `free()` story).

## 5. Non-goals (v1)

bitfields; anonymous unions; `#pragma pack`/`alignas`; `long double`
(x87 80-bit — its own gap code if ever); `wchar_t`/UTF-16; struct-typed
callbacks (nested fn-ptr in struct); variadics (3.5 — `D-R3-3.5` ✅ decided 21/09: **no general variadics**, documented gap);
C++ name mangling; COMDAT/section rules. Each stays an honest FFI001/002
until decided — no silent partial binding.

## 6. Division of work (after approval — not this lane)

1. **3.8a** layout engine: `AbiLayout` (size/align/classes per triple) in
   compiler, pure data + golden tests vs the three worked examples (§3).
   **✅ LANDED 20/09** — `AbiLayout.java` + `AbiLayoutTest` (14 shapes × 3 ABIs,
   golden measured with GCC 13.3 on x86-64/aarch64/riscv64 and re-proved live
   with `_Static_assert` against the real compilers). It binds nothing and
   decides nothing of D6-1..D6-5; it is the shared substrate 3.8b/3.7 consume.
2. **3.8b** JVM binding: records→`StructLayout` in `kof_ffi` (FFM does
   classification); D6-5 arena policy. **✅ fatia 1 (by-value param, 20/09) +
   fatia 2 (return-by-value: register + sret, 21/09) + fatia 3 (D6-2: scalar
   `T[]`→`ptr`, copy-in per call, 21/09) + fatia 4 (D6-3: `Buffer(U8)` out-buffer
   — `buffer.alloc`/`Buffer.bytes()` + `extern` INOUT copy-in/copy-back, 21/09)
   LANDED** — only the
   scalar-field subset; `struct` mutable (D6-1 B) is a new language surface
   under the Simplicity Law (rule 11), a separate decision. The JS bridges
   landed 21/09 for struct **param** (D6-5 host pack), scalar array
   **`T[]`→`ptr` copy-in** (D6-2, `packArray`), `Buffer(U8)` INOUT (D6-3,
   `packBuffer` + copy-back) and struct **return** (`__kof_ffi_from`). The JS
   FFI surface (param + return) is complete; the remaining D6 work is the Native
   cross faces (3.7 fatia B) — the x86-64 struct/array/`Buffer(U8)` faces landed
   (3.7 D6-2 + #651 fatia A2).
  3. **3.7** native asm: classification by hand per target. **✅ ALL SLICES
    LANDED — x86-64 struct param + return (register path *and* sret > 16 B,
    21/09) + x86-64 scalar `T[]`→`ptr` copy-in (22/09, steps 1–2) +
    cross INTEGER struct RETURN ≤ 16 B (22/09, fatia 3, qemu golden) +
    cross struct PARAM INTEGER register path (22/09, fatia 4)** —
    `FfiStructLayout` + call-site pack/materialise, golden JVM==Native.
    Remaining: cross float/HFA/> 16 B, `T[]`/`Buffer(U8)` on cross,
    `String[]`→`char**` (all honest `FFI001`, R6 — future work, not this spec).
    The x86-64 `Buffer(U8)` FFI token `B` **landed in #651 fatia A2 (29/09)** —
    the payload pointer (`obj+24`) is passed directly and the C write is the
    copy-back (`BufferFfiE2ETest#bufferInoutCopyInCopyBackNativeParity`).
  4. **JS**: ✅ **COMPLETE 21/09** — struct param + return, scalar array
    copy-in, `Buffer(U8)` INOUT (bridges `structParamByValueJsParity`,
    `structReturnByValueJsParity`, `arrayParamByValueJsParity`,
    `bufferInoutCopyInCopyBackJsParity` — all byte-for-byte JVM==JS).
  5. **DoD (R5) ✅ 23/09**: per-target golden E2E matrix measured
    (`FfiStructE2ETest` 12/12, `FfiNativeCrossE2ETest` 10/10,
    `FfiCrossStructParamE2ETest` 5/5, `FfiNativeArrayE2ETest` 2/2,
    `FfiArrayE2ETest` 5/5, `BufferFfiE2ETest` 5/5 (x86-64 Buffer INOUT, A2 29/09),
    `FfiStructLayoutTest` 5/5,
    `FfiE2ETest` 17/17 — **61/61 green 29/09**); FFI00x unchanged for
    everything not covered; `training/idioms/interop.md` carries the D6
    shapes (record by value, `T[]`→`ptr`, `Buffer(U8)` INOUT). **This doc
    is CONCLUDED — promote to `docs/` per the three-states rule.**
4. **JS**: decide wasm/ffi boundary (node host already binds scalars;
   struct = host-side pack/unpack) — no browser promise (R7).
5. **DoD (R5)**: per-target golden E2E matrix (same C harness, 3 ABIs),
   FFI00x unchanged for everything not covered,    `training/idioms/interop.md`
   updated with the chosen Kof shape from D6-1, this doc promoted to
   `docs/` when 3.8 lands.

## 7. Native 3.7 remaining — implementation todo (FFI/kof-c lane, claimed 22/09)

The next slice is bigger than one session, decomposed so every step is a
complete vertical (no half-bound path, R6):

> **Landed 22/09 (3.7 steps 1–2 · `T[]`→`ptr` on x86-64, D6-2):** `Long[]`→
> `long*`, `Double[]`→`double*`, `Int[]`→`int*`, `Float[]`→`float*` and
> `Bool[]`→`bool*` bind with **copy-in per call** — the element slot width
> equals the C width (8/4/1 B), so the pack is a `memcpy` of `len*elemSize`.
> `FfiStructLayout.arrayPtrType`/`isArrayPtr`/`arrayPtrElem`, gate
> `CompilerPipeline.nativeExternBound` (x86 only), lowering marker in
> `ExpressionMethodCallLowerer`, x86 pack pre-pass + helper `kof_ffi_pack_array`
> (element size in `%rsi`) behind `NativeBackend.ffiUsesArray`. Fixes a latent
> JVM crash found on the way (R6): `bool[]` is not supported by
> `MemorySegment.copy`, so `kof_ffi_copy_in` converts it to `byte[]` 0/1 first.
> Proof: `FfiNativeArrayE2ETest` 2/2 (gcc `.so` shim, golden byte-equal to the
> JVM oracle, empty/negative/`Bool[]` edges) — step 3 below remaining.

1. **✅ DONE (22/09) — `T[]`→C `ptr` on x86-64, copy-in per call (D6-2).**
   Element classes whose Kof slot width equals the C width copy with a plain
   `memcpy`: **`Long[]`/`Double[]` (8 B), `Int[]`/`Float[]` (4 B), `Bool[]`
   (1 B)**. `String[]` (array of pointers) stays `FFI001` in this cut.
   - gate `CompilerPipeline.nativeExternBound`: accept an array param on x86
     when `FfiSignature.arrayElemChar` ∈ {`j`,`d`}.
   - lowering `ExpressionMethodCallLowerer` (native branch): add a synthetic
     `kof.ffi`/`array`(`elem`) param type instead of null; skip the scalar
     coercion for it.
   - emitter `NativeFfiCall.emitX86`: pack each array arg into a fresh
     `kof_alloc` buffer via a runtime helper `kof_ffi_pack_array` (emitted
     like `emitX86CstrHelper`, behind a `ffiUsesArray` backend flag) and pass
     the buffer as one INTEGER register; copy-in only — C writes are dropped,
     exactly as on the JVM (parity, rule 5).
   - proof: a gcc-built `.so` shim (`long*`/`double*`/...) linked into the native
     binary, golden measured against the JVM oracle of the same program
     (`FfiNativeArrayE2ETest`), plus the `String[]`/cross-array→`FFI001` gate pin.
 2. **✅ DONE (22/09) — `Int[]`/`Float[]`/`Bool[]` pack.** No narrowing loop was
    needed: the Kof element width already equals the C width (`Int`/`Float` 4 B,
    `Bool` 1 B), so the same helper copies `len*elemSize` with the element size
    passed in `%rsi` from the call-site. Gate accepts all scalar element chars on
    x86-64 (`String[]` and cross stay `FFI001`).
  3. **✅ DONE (23/09) — `String[]`/`Buffer(U8)` native: honest gap `FFI001` (R6).** Both require nominal `Buffer` runtime on Native and a distinct `char**` array-of-pointers ABI — not part of the D6-2/D6-3 vertical slice (scalar `T[]` copy-in). They stay `FFI001` with diagnostic, never silent; promotion of this spec is not blocked by them (they live as future work under `docs/development/future/`). No code change in this promotion.
  4. **✅ DONE (30/09) — scalar `T[]`→`ptr` on the CROSS (riscv64/aarch64), `D-MEM-FFI-CROSS-FULL` face 1.** The x86-64 pack path is ported: `CompilerFfiBinding` no longer restricts scalar arrays to x86-64, `FfiStructLayout.crossBindable` counts an array-ptr as one INTEGER ordinal, and `NativeFfiCallRiscv` packs using the new `kof_ffi_pack_array` riscv helper (per-program, `NativeArchEmitter`, aarch64 via the line-by-line translator). `String[]` (array of pointers) stays `FFI001`. Proof: `FfiNativeArrayE2ETest#scalarArrayCrossBindsAndMatchesJvm` — a cross-compiled `.so` shim, golden JVM == riscv64 == aarch64 byte-identical for all 5 element widths; gate pinned by `FfiNativeCrossE2ETest#riscv64ScalarArrayBindsStringArrayStaysFfi001`. Remaining `D-MEM-FFI-CROSS-FULL` faces: `String[]`, memory-path structs, callbacks.
