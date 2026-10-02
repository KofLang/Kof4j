[English](modules.md) | [Português](modules.pt_BR.md)

# Modules, Packages and Imports

**Status:** Stable (except where labeled) · **Evidence:** `Parser.parsePackage`/`parseImports`, `CompilerImports.java`, `MemberResolver.qualifyViaImports`, `CompilerTypes.java:48-138`

---

## 1. Compilation unit

The compilation unit is **one `.kf` file** (or `.ks` in KofScript). The
parser produces a `CompilationUnitNode(packageName, imports, declarations)`.

- **`package` is optional**; absent → package `""` (the "default" package, the class goes
  to `Default/Main`).
- **`import` comes after `package`, before the declarations.**
- **There is no** `module` keyword, nor `namespace`, nor a separate module file.
  Kof's "module" is the **root directory** passed to the compiler (module root),
  used to expand directory imports.

---

## 2. Packages

`ebnf
package-declaration = "package" , identifier , { "." , identifier } , [ ";" ]
`

`kof
package com.dev.app
`

- Dotted name, namespace semantics (maps to a JVM package).
- **There are no** package visibility directives besides `public`/`private`/
  `protected` per member.

---

## 3. Imports

`ebnf
import-declaration = "import" , ( "*" | import-path ) , [ ";" ]
import-path = identifier , { "." , identifier } , [ ".*" ]
`

`kof
import com.dev.NodeUI          // specific class
import com.dev.*               // package wildcard
import kof.json                // stdlib module
`

### 3.1 What an import does

1. **Simple name qualification**: `qualifyViaImports` resolves a simple name
   (without `.`/`<`/`[]`) through the **first** non-wildcard import that ends in
   `.<name>` (`MemberResolver.qualifyViaImports`).
   - **`import a.b.*` wildcards do NOT qualify simple names** (:57) — they only
     bring the declarations into scope (item 3.2).
   - An **ambiguous** import (two imports with the same simple name) → **does not guess**:
     the type is preserved without qualification (`simpleNamePackage` returns `null`,
     `CompilerTypes.java:102-122`). **Stable** (the anti-guess rule of bug 32).
2. **Type-arguments are qualified recursively** (`qualifyDeep`,
   `CompilerTypes.java:48-94`): `List<NodeUI>` with `import com.dev.NodeUI`
   resolves to `List<com.dev.NodeUI>` (bug 32). The simple name of the arg resolved
   by imports → classes of the module.
3. **Directory expansion** (`CompilerImports.expandKofImports`,
   called in `CompilerDriver.java`, method `compileSources`): `import a.b` where `a/b/` is a directory in the module
   root **pulls all the `.kf` of that directory** into the unit (fixpoint ≤256
   rounds). This is how separate files of the same package see each other.

### 3.2 Transitive imports and collisions

- Importing a package that imports another **re-exposes** the declarations (a
  transitive import is not a collision — PKG005 fixed).
- Two `main()` in files of the same module → **`PKG002`** (*probe*: "module
  has 2 main() functions; expected exactly one").

---

## 4. Name resolution (order)

For an identifier `x` (see [type-system.md](type-system.md) §6):

`text
local scope (parent chain)
  → args in main
  → unqualified enum constant
  → member of the current class (resolveInHierarchy BFS)
  → types/classes of the module (knownClasses, preDeclareType phase)
  → imports (qualifyViaImports)
  → builtin namespaces (json, process, KofWeb, …)
  → otherwise SEM011
`

- **There is no** `import static`, nor renaming (`import a.b as C`), nor
  `export`/re-export.
- **There is no** package-wildcard resolution for a simple name (item 3.1).

---

## 5. Standard library (`kof.*`)

The stdlib is a set of **namespaces** accessible via `import kof.<area>` and
used through a global object (`json.encode`, `http.get`, …). The namespaces
recognized by the analyzer (`SemExpressionTyper`/`MemberResolver`, builtin namespace list):

`text
json  process  KofWeb  KofConfig  KofCache  KofGpu  KofDb  KofOrm
KofLog  KofSecurity  KofValidation  KofObservability  KofHttp  KofMq
KofTime  KofScheduler  KofTetris  KofMedia  KofImage  KofUi  Theme
rng
`

Plus the **builtin namespace** `rng` — a seedable deterministic PRNG for
property-based testing (`rng.seed(Int)`, `rng.int(Int)`, `rng.boolean()`,
`rng.double()`, `rng.string(Int, String)`; `KofRng`/`KofStd`, no `kof.*` class,
no import). Same seed ⇒ same sequence on every backend; JVM+JS+NATIVE
(x86_64) today — cross riscv64/aarch64 and ANDROID rejected with `RNG001`
(R6, X8 slice 3 pending). See `learn/39-stdlib.md` §rng and
`training/idioms/stdlib.md` §rng.

Each area has its own document in `docs/stdlib*.md` (not duplicated here). The
**language** defines that these names exist and how they resolve; the
**library** defines the signatures. **Experimental** as a surface (changes between versions).

---

## 6. Interop with the target

- **JVM**: Java types are accessible by qualified name (`java.util.Date`)
  when on the classpath (`ExternalClasspath.resolveMethod`, `:1535-1549`).
  **Target-specific.**
- **C FFI (`extern "<lib>" f(T): R`)** — direct binding to native libraries
  (JVM, `java.lang.foreign`). **Measured surface 18/09 (0.5.0-beta)**: the JVM
  binds **any signature composed of the scalar set** `{Int, Long, Float, Double,
  Boolean, String}` in **every parameter position (arbitrary arity, ≥0)** and any
  of those as the **return**, plus **`void` return** (via `kof_ffi_void`, result
   discarded as a statement); a `String` return reads back the native `char*`
   (`MemorySegment.getString`). **Proof depth:** `Int`/`Long`/`Double`/`String`/`void`
   are exercised end-to-end against libc/libm (`FfiE2ETest`, incl. `Long` proved
   `atol`→`labs` since Kof has no `long` literal); `Float`/`Boolean` map to the correct
   FFM layout + Kof `Type` on the same generic downcall path, locked by
   `FfiSignatureTest` (libc offers no clean, deterministic `float`/`_Bool` call site
   reachable from Kof source). One runtime helper `kof_ffi(lib, name, sig,
   Object[])` (FFM downcall; `sig` encodes the layout) replaced the old
   `kof_ffi_i`/`_si`/`_dd` trio; gate `CompilerPipeline.isExternBound`. **JS parity
   (slice 3.6, 18/09)**: the SAME scalar ABI binds on the JS target through a host FFM
   bridge `KofJsFfiBridge` (identical downcall to `kof_ffi`) reached via
   `extern`→`kofFfi`→`kof_platform.ffi` on the GraalJS/node runner — `FfiE2ETest`
   asserts byte-for-byte JVM↔JS equality (abs/atoi/sqrt/pow/`atol`→`labs` Long/strstr/
   srand void); a browser has no `kof_platform.ffi` host so an extern call throws an
    honest **runtime** error (R7, the same degrade as `kof.io`), and a **non-scalar**
    signature (array/struct/pointer) still emits `FFI002` at compile time.
     **Callbacks/upcalls (slice 3.4, 18/09)**: an `extern` with a **function-typed
     parameter** binds on the **JVM and the JS host runner** — the Kof function value
     becomes a real C function pointer via `Linker.upcallStub` (`JvmFfiCallbackE2ETest`
     runs `(x,y)->x+y` through C callbacks across Int/Long/Double/mixed ABIs →
     `42/42/6.0/7.5`, byte-for-byte JVM↔JS via `jvmAndJsCallbacksMatchByteForByte`); the
     contract is **synchronous/non-escaping** (the stub is scoped to the call's arena) and
     the callback ABI covers **primitives + `String` arguments** (3.4-C3.4, `b120945c`: a `char*` handed by C is read into a Kof `String` at the upcall boundary, JVM↔JS byte-for-byte) — a struct/pointer argument inside a callback, or
     a `String`/callback return, stays an honest `FFI001`/`FFI002`. On JS the compiled function value
     is a `Lambda…` **object** (not a native arrow), so the runner bridge calls
     `fn.getMember("invoke").execute(...)`. A browser has no host → honest runtime degrade
      (R7). Still **partially** bound — the **JVM struct/array/out-buffer slice landed 20–21/09 (3.8b)**: a `record` by value as an argument **and** as a return, a scalar `T[]`→`ptr` with call-scoped copy-in, and an out-buffer `Buffer(U8)` INOUT (copy-in / call / copy-back, created with `buffer.alloc`; `FfiStructE2ETest` 10/10, `FfiArrayE2ETest` 5/5, `BufferFfiE2ETest` 4/4). The remaining faces stay honest `FFI001` at compile time (never a silent stub, R6): out-buffers/out-params on **Native/JS** (`Buffer(U8,INOUT)` bound on the JVM per `D-R3-3.3` ✅ decided 21/09), struct/array on **Native** and the JS struct bridge, and Native callbacks; variadics = `D-R3-3.5` (✅ option A: no general variadics, documented gap); D6 ✅ decided 20/09. **Native binds the scalar ABI DIRECT on
     x86-64/riscv64/aarch64 (#431 slices 1–2, 20/09, §369)** — `library()` link-by-use +
     `call sym@PLT`, no `dlopen` (§61 closed); on Native, non-scalar signatures, callbacks and a
     missing `library()` stay `FFI001` at the declaration line. A missing lib/symbol fails at **runtime** with
     a `kof_ffi` exception naming `lib::symbol` (stack trace, not a surgical
       message). Remaining R3 slices (handle lifetime `D-R3-3.3`, Native struct/array + callbacks, JS struct bridge; variadics closed as a documented gap; **JVM struct/array + out-buffer 3.8b + JVM/JS scalar+callback parity + Native scalar closed**)
    in
   `docs/architecture/IMPLEMENTATION-UNIVERSAL-PLATFORM.md` (use-case #431).

- **Native/JS**: there is no interop with host types the same way. **Unspecified.**
- **Annotations** (`@Name`, `@JsonFormat`) are interop metadata emitted in the
  JVM bytecode. **Target-specific** (only JVM preserves them).

---

## 7. Files and extension

- **`.kf`** — Kof (compilable to all targets).
- **`.ks`** — KofScript: **pure Kof executed directly** (no `let`/`const`/
  `async`/`fn` — it is not JavaScript). The wrapper only adds the script model
  (top-level `var`/`val` → `KofScriptGlobals`; statements → `main()`).
- **There is no** separate header/source, nor `.kfi`, nor preprocessor.
