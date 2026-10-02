[English](kof-connector-ecosystem-plan.md) | [Português](kof-connector-ecosystem-plan.pt_BR.md)

# Kof Interoperability — Connector Ecosystem

**Status:** UNDER DEVELOPMENT — promoted `future/` → `docs/development/` by `D-CONNECTORS-GO` (maintainer 29/09/2026)
**Location:** `docs/development/kof-connector-ecosystem-plan.md`
**Nature:** architecture, contracts, dependencies, implementation strategy, promotion criteria
**Normative source:** `DECISIONS.md` §`D-CONNECTORS-GO` (DECIDED — promotion authorized)
**Main dependencies:** R3 / FFI-ABI (`docs/ffi-abi-structs.md`), the JVM interop path
(`ExternalClasspath`/`JdkReflectionResolver`), `kof.process`/`kof.shell`/`kof.ssh`,
KofJS, the Native backends, `kof.toml`/`kofdeps`
**Implementation status:** fatias 1–16 LANDED in pure-Kof `libs/interop/` (manifest reader → `InteropCore`, through `CAbiConnector` = the declarative C-ABI half, slice 16) — see §9. **Slice A (`foreign module` grammar) LANDED 01/10** (`foreign` enters the grammar as sugar over the existing FFI path; `ForeignModuleGrammarE2ETest` 5/5). Remaining: Slice B (interop error type) and the ABI-tier transcription (§9.16 Slice D) — rule-6 gated per `D-CONNECTORS`.

> **Fundamental rule.** This document describes a future architectural direction. It does
> **not** change the language, add keywords, create namespaces, or open an implementation
> track. Every syntax shown is an **intent form**; the definitive form belongs to the
> maintainer.
>
> **KOF-first (rule 10).** No external language, runtime or ABI is an oracle for Kof. The
> connector ecosystem is built from Kof's own type/ABI model; external research
> (ELF/Mach-O/PE, CPython C-API, JNI/FFM, `repr(C)`, …) contributes invariants and
> trade-offs, never syntax or semantics copied blindly.
>
> **Kof is not Java/Frankenstein (rules 8/45).** Interoperability happens **through Kof's
> type and API model**. Kof never embeds foreign syntax; the language stays Kof.

---

# 0. Objective and non-goals

## 0.1 Objective

Kof already works inside Java systems through improvised integrations. The need is real:

> **Kof must be able to enter existing systems without requiring the whole system to be
> rewritten in Kof.**

This plan turns those improvised integrations into an **official, consistent, documented
and extensible architecture**: an Interop Core plus connectors. It supports both directions:

```text
Existing System (Java, Python, Rust, C, C++, C#, Go, Kotlin, Swift, JavaScript/TypeScript, Ruby, PHP, Lua, Dart, Scala, R, Julia, COBOL, Pascal, Fortran, …)
        │
        ▼                    Kof            (Kof calls the foreign ecosystem)
   Kof Connector   ───────────────▶  foreign function / library
        │
        ▼                    Kof            (the foreign ecosystem calls Kof)
       Kof
```

Interoperability is **bidirectional whenever technically possible**.

## 0.2 Non-goals

* Not a "run a process and read stdout" layer as the primary mechanism (that already exists
  as `kof.process`; it is one connector, not the model).
* Not 30 independent connectors with their own marshalling/lifecycle/error rules.
* Not a foreign-syntax embedding ("Java syntax inside Kof", "Python syntax inside Kof").
* Not automatic mapping of every dynamic type of a foreign language into Kof's type system.
* Not a promise of ABI stability before compatibility tests exist.

---

# 1. Architectural principle — an Interop Core, not isolated connectors

Do not create dozens of fully independent connectors. First create the **common
infrastructure**, then make each connector a thin implementation of it.

```text
                     Kof Interop Core
                            │
           ┌────────────────┼────────────────┐
          ABI              API              FFI
           └────────────────┼────────────────┘
                            │
                     Connector SPI
                            │
   ┌──────────┬─────────┬───┴────┬──────────┬─────────┐
 Java      Python     Rust      C        C++      … others
```

The Core owns, once, what no connector may re-invent:

* type marshalling;
* lifecycle;
* ownership;
* memory management;
* error propagation;
* callbacks;
* symbol resolution;
* ABI metadata;
* versioning;
* diagnostics.

A connector implements only the **language-specific adapter** on top of this substrate.

---

# 2. What already exists (reuse — never duplicate)

Rule 54 requires an inventory before any code. The table below is the honest starting point;
the plan **evolves these mechanisms** instead of building a second implementation.

| Existing mechanism | Real anchor | Role in the ecosystem |
|---|---|---|
| FFI `extern` + signature codes | `docs/ffi-abi-structs.md`; `compiler/FfiSignature.java`; `compiler/CompilerFfiBinding.java` (`FFI001`/`FFI002`) | The ABI substrate the connectors build on |
| ABI layout engine | `compiler/AbiLayout.java` (SysV-x86_64 / AAPCS64 / RISCV64 size-align-argclass); `compiler/FfiStructLayout.java` | Calling-convention + struct/array layout — do **not** invent a second ABI |
| JVM FFI runtime (FFM) | `compiler/jvm/JvmFfiRuntime.java` (`SymbolLookup.libraryLookup`, downcall/upcall, struct read/write, buffer in/out) | JVM-side foreign calls |
| Native FFI runtime | `compiler/nat/NativeFfiCall.java`, `NativeFfiCallRiscv.java`; link policy `NativeAssembler.java` / `NativeCrossLink.java` | Native foreign calls (link-by-use, `call sym@PLT`) |
| JS FFI bridge | `kof-runtime/.../KofJsFfiBridge.java`, `KofJsFfiMarshal.java` | JS-host foreign calls |
| Java class resolution | `compiler/ExternalClasspath.java`, `compiler/JdkReflectionResolver.java`, `JavaLangProbe.java`, `ExternalCtorTyper.java` | The Java connector's compile-time resolver |
| Process/shell/ssh out-of-process interop | `KofProcess.java`, `KofShell.java`, `KofSsh.java`; `runtime/RuntimeProcess.java`, `RuntimeShell.java`; JS `KofJsProcessBridge`; `PROC001` honest gap | The "process" connector (stdout protocol), not the whole model |
| Runtime ABI contract | `docs/runtime/RUNTIME_ABI.md`, `ARRAY_MODEL.md`, `STRING_MODEL.md` (payload @24) | Object/array/string layout for handles and buffers |
| GC + allocator | `runtime/RuntimeGc.java` (mark/sweep), `runtime/RuntimeMemory.java` (`kof_alloc`/`kof_free`) | Ownership/lifetime support on Native |
| Package system | `KofProjectConfig.java` (`kof.toml`), `ProjectLocator.java`; `Deps.java`/`DepsRegistry.java` (`kofdeps`) | Connector manifest + distribution |
| Target selection | `Target.java`, `TargetMatrix.java`, `CompilerPipeline.java:193` | Which connector/runtime is valid per target |
| CLI dispatch | `kof-cli/.../Main.java:17` (`switch` on command); precedents `CmdNew.java`, `publish`/`Deps` subcommands | Where `kof connector init` lands |
| Interop reflection (X6) | `compiler/CompilerInterop.java`, `docs/type-system-extensions-plan.md`; `training/idioms/interop.md`, `learn/21-java-interoperability.md` | Existing surface-reflection entry point |
| Script engines as PROCESS connector (X2) | resources `dev/kof/interop-py-host.kf` / `interop-r-host.kf` (`KofPy`/`KofR`: typed faces + `callJson`, 2-line KOFOK/KOFERR protocol, `INTEROP004`/`005`/`006`) | The §5.5/§5.6 scripting connectors in their process form — LANDED 26/09 (X2); the embedding/CPython-C-API/R-C-API path remains unbuilt |

> **Conclusion:** the substrate (ABI, marshal helpers, per-target runtime, class
> resolution) largely exists. The missing piece is the **unifying layer**: the Core, the
> Connector SPI, the manifest, versioning, and the language-level ownership/error contracts.

---

# 3. The Interop Core

## 3.1 Interop type model (official ABI)

Define a single official representation for interoperable types, compatible with the targets
that already exist (JVM, C ABI/Native, JS; WASM when it exists). Types to cover:

| Kof concept | Interop kind | Notes |
|---|---|---|
| `Int`/`Long`/`Byte`/`Short` | integer | width/signedness explicit; SysV/AAPCS/RISCV64 via `AbiLayout` |
| `Float`/`Double` | floating point | IEEE-754; NaNs/inf preserved (cross rule 5) |
| `Bool` | boolean | C `_Bool`/int mapping per connector |
| `Char` | code point / width | declared per connector |
| `String` | string view | see §3.3 (UTF-8 / UTF-16 / NUL-terminated / length-based) |
| `Buffer` | byte buffer | `KofBuffer.java` already models `Buffer(U8)` |
| `List<T>` | array | pointer+length (borrowed or owned, §3.2) |
| `record`/class | struct/handle | see §3.4 |
| enum | integer/enum | |
| function | function pointer | see §3.5 |
| lambda/callback | callback | see §3.6 |
| opaque resource | opaque handle | `Handle` boxed `Long` precedent (`KofProcess.java`) |
| nullable | nullable/reference | explicit; never silent |
| error | result/error | see §3.7 |
| object from the foreign heap | object handle | GC/lifetime aware |

The model **must not** invent an ABI incompatible with the existing targets. `FfiSignature`
(char codes), `FfiStructLayout` (`kof.ffi/struct`, `kof.ffi/array`) and `AbiLayout` are the
starting representation; new kinds (handle, result, string view) extend it, and every
extension is a rule-6 decision.

## 3.2 Ownership and memory (the central contract)

For every crossing, state explicitly:

```text
who allocates?   who frees?   who owns?   when may it be freed?
who may mutate?  who keeps a reference?
```

Common, connector-independent concepts:

```text
owned      the receiver must free/release
borrowed   valid only for the duration of the call
shared     reference-counted / GC-managed on one side
opaque     never inspected; passed back to its owner
immutable  may not be mutated by the foreign side
mutable    may be mutated; contract stated
```

Rules:

* No connector invents its own ownership rule — the Core defines it and the connector
  declares which concepts it supports.
* When a language cannot express a concept (Rust borrows, Swift ARC, Python refcounts),
  use **handles/wrappers** with an explicit, documented lifetime, never a silent fallback.
* JVM: `Arena`-confined FFI allocations (D6-5) stay the JVM rule; Native: the Kof GC/allocator
  (`RuntimeGc`/`RuntimeMemory`) governs Kof-owned memory.
* Ownership/lifetime decisions that change Kof semantics are **rule 6** — plan, not silent edit.

## 3.3 Strings

Explicit interop for strings, with the cheapest safe view:

```text
Kof String
   ├── UTF-8 view          (C, Rust &str, Go string, …)
   ├── UTF-16 view         (JVM, JS, C#/CLR, Objective-C/NSString)
   └── native buffer       (mutable, NUL-terminated or length-based)
```

* Support UTF-8, UTF-16, NUL-terminated, length-based, immutable strings and mutable buffers.
* Avoid unnecessary copies when it is safe; when a copy is required, **the interop layer knows
  it** and documents it (§3.10).
* Kof's own string layout is normative (`docs/runtime/STRING_MODEL.md`); views are mapped onto it.

## 3.4 Structs and composite types

Allow interoperable representation without manual field-by-field conversion:

```text
Kof record User(Int id, String name)
        ↕
C struct  ·  Rust #[repr(C)] struct  ·  Java record/class
C# record  ·  Go struct  ·  Swift struct
```

* The real Kof form uses today's grammar: `record` (immutable) or a mutable `class` with
  `constructor(...)`. **No imported syntax.**
* Layout follows `AbiLayout`/`FfiStructLayout`; padding/alignment is part of the contract.
* Unsupported shapes (Rust enums with data, C++ classes, Go interfaces) fail with a clear
  diagnostic — never a guessed layout.

## 3.5 Functions, calls and the foreign module

Official mechanism to declare/import/export functions:

```text
foreign function → Kof symbol → Kof call
Kof function     → foreign symbol → Java/Python/Rust/C/…
```

Support: arguments, return, callbacks, variadics where supported, async where supported,
errors, lifecycle. (Note: variadic FFI is currently not supported — `DECISIONS.md`
§`D-R3-3.5`; a connector may declare it as a diagnosed gap.)

Introduce an official **foreign module** concept, independent of a specific connector:

```text
foreign module = library + symbols + types + functions + ownership + ABI
```

This is the abstraction the Core consumes and connectors populate.

## 3.6 Callbacks

Support, when technically possible, both directions:

```text
foreign library → callback → Kof function
Kof            → callback → foreign runtime
```

Mechanisms differ and are **not** pretended to be equal — one common abstraction plus
specific adapters: C function pointers, JVM interfaces (upcalls via FFM), Python callables,
Rust `extern "C"` functions, C# delegates, Go exported funcs, Swift closures, JS functions.
Thread affinity, re-entrancy and lifetime are part of the callback contract.

## 3.7 Errors and exceptions

A single common layer able to represent:

```text
success · failure · error code · error message · error payload
foreign exception · stack/context when available
```

Per-connector mappings, never losing a foreign error:

```text
Java exception    → Kof interop error
Rust Result::Err  → Kof interop error
C error code      → Kof interop error
```

A foreign error must never be silently swallowed (R6).

## 3.8 Library loading

A reusable loading abstraction:

```text
.so  ·  .dylib  ·  .dll   (+ static libraries where applicable)
```

* JVM/JS: `SymbolLookup.libraryLookup` (already in `JvmFfiRuntime`/`KofJsFfiBridge`).
* Native: link-by-use (`NativeAssembler`/`NativeCrossLink`); `dlopen`/`dlsym` reserved
  (today used only by the Vulkan path `RuntimeVk.java`/`VkChain64Loader.java`).
* The loader is Core infrastructure: one path, reused by every connector.

## 3.9 Versioning and ABI stability

Interop cannot depend on a language version alone. Track and validate:

```text
Kof version · Connector version · Foreign language version · Foreign runtime version
ABI version · Platform ABI · Compiler version
```

* Classify every interface as `stable`, `experimental` or `internal`.
* Do not promise ABI stability before compatibility tests exist.
* Compatibility tests must validate symbol names, calling convention, type layout,
  alignment, struct layout, binary compatibility and ownership.
* An unsupported combination must produce a **clear diagnostic** (R6).

## 3.10 No hidden costs

The interop API must make visible when there is: copy · conversion · allocation · runtime
crossing · thread switch · serialization · boxing · GC interaction. Convenient interop may
not mean invisible, unpredictable behavior. Every cost is documented and, where possible,
observable (diagnostic or metric).

---

# 4. Connector SPI and manifest

## 4.1 Connector SPI

Each connector implements only the adapters the Core does not provide, behind a stable
service interface (marshalling hooks, lifecycle, error mapping, callback bridging, symbol
resolution, ABI declaration). Adding a connector must **not** require changing the compiler
core; the compiler discovers it through the SPI.

## 4.2 Connector manifest

A declarative manifest, consistent with existing Kof conventions (`kof.toml`, `kofdeps`):

```text
name
language
version
abi
platforms
runtime
dependencies
capabilities
```

It declares languages, versions, targets, ABI, runtime requirements, libraries, supported
types, callback support and ownership model. The real file format must follow the existing
project/package format — not a new one invented here.

---

# 5. Connector catalogue

Grouped by **mechanism**, not by brand. Each entry states its route, its honest limits and
whether it is planned, evaluated or out of scope. The matrix values in §6 are **discovered
during implementation, never assumed**.

## 5.1 JVM family (one substrate, adapters where semantics diverge)

```text
Kof JVM Interop
   ├── Java
   ├── Kotlin
   └── Scala   (+ Groovy, Clojure evaluated)
```

* **Java (`kof-java-connector`) — first connector.** Reuses `ExternalClasspath`,
  `JdkReflectionResolver`, `JvmFfiRuntime`. Supports classes, methods, constructors, static
  methods, fields where appropriate, interfaces, callbacks, exceptions, arrays, primitives,
  objects, generics where a safe representation exists, JVM libraries, and Kof modules
  consumed by Java. Evaluate binding generation.
* **Kotlin (`kof-kotlin-connector`).** A thin adapter over the JVM substrate; keep Kotlin
  nullability/extension semantics explicit where they diverge from Java.
* **Scala (`kof-scala-connector`).** Do not reduce Scala to Java if that loses semantics
  (objects, traits, collections, Scala-specific types). Adapter, not alias.

## 5.2 C ABI family (the foundational native route)

* **C (`kof-c-connector`) — second connector, foundational.** Headers, C ABI, structs,
  pointers, arrays, strings, function pointers, callbacks, shared/static libraries
  (`.so`/`.a`/`.dll`/`.lib`/`.dylib` per target). Evaluate header→binding generation
  (`kof-c-compiler` already emits a C subset and is a cross target for fixtures).
* **C++ (`kof-cpp-connector`).** Not "C with classes": name mangling, ABI, namespaces,
  classes, ctors/dtors, templates, exceptions, STL, smart pointers. Prefer a **stable C ABI
  layer**; document cross-toolchain ABI limits honestly.
* **Rust (`kof-rust-connector`).** `extern`, `repr(C)`, ownership/borrowing, opaque handles,
  `Result`, panic boundaries, callbacks. Never expose arbitrary Rust types as if C ABI.
* **Zig (`kof-zig-connector`).** Close to C/system interop; C ABI, exported functions,
  structs, pointers, allocator interactions.
* **Go (`kof-go-connector`).** cgo, C ABI, exported functions, shared libraries, callbacks,
  goroutine/thread boundaries, memory ownership. Respect the Go runtime rules — never a
  third-party abstraction that violates them.
* **Nim / D (`kof-nim-connector`, evaluated).** Native interop mechanisms where sustainable.

## 5.3 Managed / .NET

* **C# (`kof-csharp-connector`).** .NET/CLR, P/Invoke, native interop, managed objects,
  delegates, exceptions, assemblies. Evaluate Kof→.NET and .NET→Kof.
* **F# / VB.NET** evaluated via the same substrate.

## 5.4 Apple

* **Swift (`kof-swift-connector`).** Swift/C interop, Swift ABI, Objective-C interop,
  structs, classes, closures, ARC, ownership; a documented C ABI layer when direct is unsafe.
* **Objective-C (`kof-objectivec-connector`).** Runtime, messaging, `NSObject`, blocks, ARC,
  C ABI, headers — for the existing Apple ecosystem.

## 5.5 Scripting / dynamic

* **Python (`kof-python-connector`).** CPython, Python C API, embedding, extension modules,
  native libraries, objects, callables, exceptions, buffers. Handle the Python runtime
  lifecycle and GIL explicitly — Python is **not** a plain native library. The **process** form
  landed 26/09 as the X2 `KofPy` engine (inventory row in §2); embedding/C-API remains unbuilt —
  that is the goal-state form of this bullet, not the landed one.
* **JavaScript / TypeScript (`kof-javascript-connector` / `kof-typescript-connector`).**
  KofJS already exists: make it an official integration (`Kof → KofJS → JS runtime`), not a
  duplicate. Future: `Kof → KofWasm → JS host`; the same Kof module should run in both when
  semantically compatible.
* **Ruby (`kof-ruby-connector`).** Ruby C API, native extensions, embedding, objects, exceptions.
* **PHP (`kof-php-connector`).** PHP extensions, Zend API, FFI, embedding, lifecycle — prefer
  stable/officially supported mechanisms.
* **Lua (`kof-lua-connector`).** Lua C API, userdata, tables, functions, callbacks, embedding —
  especially for embedded/scripting use.
* **Dart (`kof-dart-connector`).** Dart FFI, native extensions, isolates, callbacks, memory.

## 5.6 Scientific

* **Fortran (`kof-fortran-connector`).** `ISO_C_BINDING`, C ABI, arrays, numeric types,
  calling conventions, legacy libraries (HPC/scientific).
* **Julia (`kof-julia-connector`).** Julia C API, embedding, native libraries, arrays,
  callbacks, objects. Do not auto-map all dynamic Julia semantics.
* **R (`kof-r-connector`).** Embedding, native extensions, C interface, vectors, data frames,
  callbacks (scientific data). The **process** form landed 26/09 as the X2 `KofR` engine.
* **MATLAB/Octave (`kof-matlab-connector`, `kof-octave-connector`, evaluated).** Native
  interfaces, shared libraries, C ABI, extension APIs where officially sustainable.

## 5.7 Functional / runtime-specific

* **Haskell (`kof-haskell-connector`, when sustainable).** GHC FFI, C ABI, exported
  functions, runtime initialization, callbacks. Do not map lazy evaluation onto Kof's
  execution model.
* **Erlang/Elixir (`kof-erlang-connector`, `kof-elixir-connector`, evaluated).** BEAM NIFs,
  ports, native interfaces, message passing, process boundaries. Respect BEAM concurrency —
  do not turn Kof calls into synchronous calls if that breaks the platform's properties.
* **OCaml** evaluated via its C ABI.

## 5.8 Legacy

* **COBOL (`kof-cobol-connector`).** A **legacy integration case, treated seriously**: C ABI,
  native runtime, calling conventions, generated bindings, shared libraries, the specific
  COBOL compiler runtime. Goal: an existing COBOL system consumes Kof functionality — not a
  COBOL rewrite. Records/error codes map through the Core.
* **Pascal (`kof-pascal-connector`).** Free Pascal first (open-source baseline), then Object
  Pascal/Delphi where technically possible: C ABI, shared libraries, calling conventions,
  records, pointers, strings.

## 5.9 Extensibility

The list is a **direction**, not a 30-runtime demand (rule 55). First prove the architecture
with a few connectors; each new connector is a consequence of the architecture, not a new
workaround. The catalogue is grouped so new languages fall into an existing mechanism family.
A language/mechanism matrix (JVM · Native/Systems · .NET · Apple · Scripting · Scientific ·
Functional · Legacy) tracks candidates as they are evaluated.

---

# 6. Connector capability matrix

Create documentation with a matrix; **discover the values during implementation, never fill
them by assumption**:

```text
Language | Kof → Lang | Lang → Kof | ABI | Callbacks | Structs | Errors | Ownership
Java     | ...        | ...        | JVM | ...       | ...     | ...    | managed
C        | ...        | ...        | C   | ...       | ...     | ...    | manual
Rust     | ...        | ...        | C   | ...       | limited | Result | explicit
Python   | ...        | ...        | C/API | ...     | objects | exc.   | runtime
COBOL    | ...        | ...        | ABI | limited   | records | codes  | manual
```

Each connector implements only the subset its language permits; capacities that do not exist
are declared as honest gaps (`XXX00x`), never faked.

---

# 7. Header / binding generation

Evaluate automatic binding generation:

```text
C header         → Kof binding generator → Kof API
Rust/Java/.NET/Python metadata (later)
```

Do not implement all generators at once. Start with a language that has a **formal, stable
interface** — most likely C ABI. Bindings generated must be deterministic and covered by
golden tests; a generated binding that cannot be trusted is not delivered.

---

# 8. CLI — connector generator

Evaluate `kof connector init` (or the equivalent in the existing CLI shape):

```text
connector/
    manifest
    bindings
    runtime
    types
    tests
    docs
```

It generates a starting connector structure so the community can create connectors without
changing the compiler core. Placement is the existing `kof-cli` dispatch (`Main.java:17`),
following the `kof new` / `kof deps` subcommand precedent.

---

# 9. Phases (incremental)

| Phase | Scope | Exit proof |
|---|---|---|
| 1 · Interop Core | ABI model, foreign types, ownership, error model, handles, function calls, metadata, tests | Core documented + unit tests green; no connector needed |
| 2 · C ABI | C connector, dynamic libraries, structs, pointers, callbacks | Kof↔C round-trip both directions |
| 3 · JVM | Java, then Kotlin/Scala adapters | Java app calls a Kof module; Kof calls a Java library |
| 4 · Systems | Rust, C++, Zig, Go | each connector proves a real integration |
| 5 · Managed/scripting | Python, C#, JS/TS, Ruby, PHP, Lua | Python↔Kof and C#↔Kof round-trips |
| 6 · Scientific | Fortran, Julia, R, MATLAB/Octave | numeric array/record round-trip |
| 7 · Legacy | COBOL, Pascal/Delphi | a real legacy system consumes a Kof module |
| 8 · Functional/runtime | Haskell, Erlang/Elixir, OCaml, others | integration respecting the foreign runtime |

The order may change after technical analysis; phase 1 is the prerequisite of all others.

---


## 9.1 Promoted slice 1 (29/09/2026) — connector manifest reader

**State:** defined; implementation = pure-Kof library `libs/interop/` over the existing
`kof.toml` format (never a new format, §4.2), consumed by `ConnectorManifest(path)`:

- `name()` / `language()` / `version()` / `abi()` / `runtime()` → `String` (a required field absent
  throws an explicit `CONNECTOR: missing <field>` — R6, never silent);
- `platforms()` / `dependencies()` / `capabilities()` → `List<String>` (empty when absent);
- `hasCapability(String)` → `Bool`; `describe()` → one-line summary.

**How to finish:** land the library + a cross-target E2E (`ConnectorManifestE2ETest`) proving the
read + the missing-field diagnostic on JVM/Native x86-64/riscv64(qemu)/Script (JS inherits the
`TOML`/`IOJS001` file gap). **No compiler change** (library-first, `D-KOF-FIRST-IMPL`). This is the
declarative seam every connector shares; the Interop Core (Phase 1) and the C-ABI connector
(Phase 2) build on it.

---

## 9.2 Promoted slice 2 (29/09/2026) — manifest vocabulary validation

**State:** landed. `ConnectorManifest.validate()` checks every declared `capabilities` token against
the plan's cross-language test suite (§10: primitives/strings/arrays/structs/enums/pointers/
callbacks/errors/ownership/threads/async/opaque) and every `ownership` token against the Core's
vocabulary (§3.2: owned/borrowed/shared/opaque/immutable/mutable). An unknown token throws an
explicit `CONNECTOR: unknown capability <x>` / `CONNECTOR: unknown ownership <x>` — a connector never
silently declares a concept the Core does not define (R6). `knownCapabilities()`/`knownOwnership()`
expose the vocabulary. No compiler change.

---


## 9.3 Promoted slice 3 (29/09/2026) — interop type model

**State:** landed. `libs/interop/InteropType.kf` answers the plan §3.1 question in pure Kof: for a
Kof type, its **interop kind** (integer/float/boolean/char/string/buffer/array/struct-or-handle/
enum/function-pointer/callback/opaque/nullable/result/object-handle). `kindOf` returns null for a
non-interoperable type and `describe` throws `INTEROP: unsupported type <x>` (R6, never a guess);
`kinds()`/`isKnownKind` expose the vocabulary. No target-specific ABI widths (those stay with
`AbiLayout`/`FfiSignature`), no compiler change, and no file IO — so it is target-neutral and runs
on every target including JS (no gap).

---


## 9.4 Promoted slice 4 (29/09/2026) — connector catalogue

**State:** landed. `libs/interop/ConnectorCatalogue.kf` discovers the `*.toml` manifests in a
directory (`Directory(base).list()`) and exposes `count`/`names`/`find`/`has`; non-`.toml` entries are
ignored, and a malformed manifest throws its own explicit diagnostic (never a silent skip). Built on
`kof.io` `Directory`; JS inherits the `IOJS001` gap.

**Measured finding (compiler/generics — FIXED in #697):** a generic collection of a library type in
the SAME package failed to type when the declared annotation was present: `List<interop.ConnectorManifest>
xs = new List<interop.ConnectorManifest>()` and `List<ConnectorManifest> xs = listOf<ConnectorManifest>()`
yielded a spurious `SEM021` ("type mismatch") — the type-argument degraded to an empty-package /
literal-dotted name (`ClassType("", "interop.ConnectorManifest")`) while the other side was
package-qualified. Root cause: `SemNewExprTyper` applied the text-only `Type::of` to a builtin
collection ctor's type-args, and the `VarDeclStmt` declared type was only `qualifyDeep`-ed when it
contained a `'.'`. Both now route through the analyzer-aware qualification (#697, issue 697; proof
`SamePackageGenericArgTest` 6-shape matrix + JVM golden). The catalogue kept the `List<String>`
workaround during the gap; the redeclaration-style public API (`all(): List<ConnectorManifest>`) is
no longer blocked.

---


## 9.5 Promoted slice 5 (29/09/2026) — declared supported types, validated by the type model

**State:** landed. `interop.ConnectorManifest` now reads the optional `types = [...]` field (§4.2
"supported types") and `validate()` checks each name against the Core's interop type model
(`interop.InteropType.isKnownKind`, §3.1) — an unknown kind throws `CONNECTOR: unknown type <x>` (R6),
never accepted blindly. This links slice 1/2 (manifest) with slice 3 (type model).

**Cross-target proof:** `ConnectorManifestE2ETest` **10/10** (JVM golden + Script + Native x86-64 + the
JS `IOJS001` gap + the negatives). Fatia 5's validation face is proven on JVM/Script/JS/x86.

**Measured cross-target divergences (NOT this lane — recorded for the native lane):**
- riscv64: the manifest E2E runtime **SIGSEGVs (exit 139)** even for the read-only slice-1 probe,
  deterministically on tip `e7c4abd95` (which already contains the §540 fix). The riscv64 case was
  removed from `ConnectorManifestE2ETest` rather than hidden behind an assumption; no riscv64 claim
  is made for this library until the native divergence is resolved.
- native x86-64 link error observed nearby (`undefined reference to kof_bm_set` in `kof_alloc`),
  from the sibling §540/§542 runtime work — reported via `twin.md`, not touched here.

---


## 9.6 Promoted slice 6 (29/09/2026) — ownership / lifetime contract

**State:** landed. `interop.InteropOwnership` is the Core's single source of truth for the §3.2
ownership vocabulary (owned/borrowed/shared/opaque/immutable/mutable), its explicit contract and
lifetime (`contract`/`lifetime`/`describe`); an unknown concept throws
`INTEROP: unknown ownership <x>` (R6). `ConnectorManifest.knownOwnership()` now delegates to it
(no duplicated vocabulary). This is the Core's ownership model in pure Kof, target-neutral (no IO).

**Proof:** `InteropOwnershipE2ETest` **5/5** (JVM golden + Script + JS + Native x86-64 + the unknown
diagnostic), and `ConnectorManifestE2ETest` **10/10** after the delegation. No compiler change.

---


## 9.7 Promoted slice 7 (29/09/2026) — visible interop costs

**State:** landed. `interop.InteropCost` is the Core's vocabulary for the §3.10 visible costs
(copy/conversion/allocation/crossing/thread-switch/serialization/boxing/gc) with `describe`,
`summary` ("none" when empty) and `validate`; an unknown cost throws `INTEROP: unknown cost <x>`
(R6). It makes a crossing's cost explicit instead of invisible, per §3.10.

**Proof:** `InteropCostE2ETest` **5/5** (JVM golden + Script + JS + Native x86-64 + the unknown
diagnostic). Pure Kof, target-neutral, no compiler change.

---


## 9.8 Promoted slice 8 (29/09/2026) — interop string views

**State:** landed. `interop.InteropString` maps the §3.3 string views: Kof's own canonical string is
UTF-8 + NUL-terminated (`docs/runtime/STRING_MODEL.md`), UTF-16 hosts (JVM/JS/C#/ObjC) require a
conversion (a visible copy, §3.10) and UTF-8 hosts (C/Rust/Go) do not. `viewFor`/`conversionRequired`
are honest: an unknown host throws `INTEROP: unknown string host <x>` (R6).

**Proof:** `InteropStringE2ETest` **5/5** (JVM golden + Script + JS + Native x86-64 + the unknown
diagnostic). Pure Kof, target-neutral, no compiler change.

---


## 9.9 Promoted slice 9 (29/09/2026) — foreign module descriptor

**State:** landed. `interop.ForeignModule` models the §3.5 abstraction the Core consumes and
connectors populate: module identity (name, library, ABI) + declared symbols (Kof name → foreign name
+ signature) + the module ownership concept, validated by `InteropOwnership` (§3.2). It is pure Kof —
no `foreign module` grammar (rule 6, §13). Honest (R6): unknown ownership throws through
`InteropOwnership`; an undeclared symbol throws `FOREIGN: unknown symbol <x>`.

**Proof:** `ForeignModuleE2ETest` **6/6** (JVM golden + Script + JS + Native x86-64 + the two
negative diagnostics). No compiler change; target-neutral.

---


## 9.10 Promoted slice 10 (29/09/2026) — foreign library naming

**State:** landed. `interop.InteropLibrary` covers the §3.8 library-loading face expressible in pure
Kof: a logical library name maps to its concrete file name per format
(`lib<n>.so` / `lib<n>.dylib` / `<n>.dll`, plus static `lib<n>.a` / `<n>.lib`) and to candidate paths
under given roots. The key is the FORMAT SUFFIX itself (the plan's own list) — no OS vocabulary is
invented. Honest (R6): an unknown kind throws `INTEROP: unknown library kind <x>`.

**Proof:** `InteropLibraryE2ETest` **5/5** (JVM golden + Script + JS + Native x86-64 + the unknown
diagnostic). No compiler change; target-neutral.

---


## 9.11 Promoted slice 11 (29/09/2026) — stability tiers and ABI-compatibility aspects

**State:** landed. `interop.InteropCompatibility` supplies the §3.9 MECHANISM, not the policy: the
stability tiers (`stable`/`experimental`/`internal`), whether a tier promises stability (only
`stable`, and only once compatibility tests exist), and the aspects a compatibility test must
validate (symbol-names/calling-convention/type-layout/alignment/struct-layout/binary-compat/
ownership). The tier assignment + first stable ABI version are **decided** by `D-CONNECTORS` (§13); the concrete table is not yet transcribed (documentation gap).
Honest (R6): an unknown tier/aspect throws `INTEROP: unknown stability <x>` /
`INTEROP: unknown compatibility aspect <x>`.

**Proof:** `InteropCompatibilityE2ETest` **6/6** (JVM golden + Script + JS + Native x86-64 + two
negatives). No compiler change; target-neutral.

---


## 9.12 Promoted slice 12 (29/09/2026) — connector SPI hooks

**State:** landed. `interop.ConnectorSpi` names the §4.1 adapter hooks a connector may provide
(marshalling/lifecycle/error-mapping/callback-bridging/symbol-resolution/abi-declaration) — the stable
service interface behind which a connector implements only what the Core does not provide. The
connector manifest now declares them (`spi = [...]`, §4.2) and `validate()` checks each against the
Core vocabulary, so adding a connector never changes the compiler core. Honest (R6): an unknown hook
throws `INTEROP: unknown SPI hook <x>` (standalone) / `CONNECTOR: unknown SPI hook <x>` (manifest).

**Proof:** `ConnectorSpiE2ETest` **5/5** + `ConnectorManifestE2ETest` **9/9** (JVM golden + Script +
JS + Native x86-64 + negatives). No compiler change; target-neutral.

---


## 9.13 Promoted slice 13 (29/09/2026) — Interop Core facade + audit

**State:** landed. `interop.InteropCore` composes the §2 "unifying layer": given a directory of
connector manifests it enumerates them (`ConnectorCatalogue`), validates each (`ConnectorManifest`
covers capabilities/ownership/types/SPI) and produces a deterministic audit (sorted names, one line
per connector) — what a `kof connector` listing would print. Honest (R6): an invalid connector is
reported as `INVALID: <diagnostic>`, never hidden; a malformed manifest throws from the manifest
reader.

**Proof:** `InteropCoreE2ETest` **4/4** (JVM golden + Script + Native x86-64 + the JS `IOJS001` gap).
No compiler change.

---


## 9.14 Promoted slice 14 (29/09/2026) — manifest stability tier, validated by the Core

**State:** landed. `ConnectorManifest.stability()` reads the optional `stability` field (§3.9) and
`validate()` checks it against the Core tier vocabulary (`InteropCompatibility.isKnownTier`) — an
unknown tier throws `CONNECTOR: unknown stability <x>` (R6). This connects the manifest to the
compatibility mechanism; the policy of WHICH tier an interface gets stays a rule-6 decision (§13).

**Proof:** `ConnectorManifestE2ETest` **10/10** (JVM golden + Script + Native x86-64 + the JS
`IOJS001` gap + the negatives, incl. the new unknown-stability). No compiler change.

---


## 9.15 Promoted slice 15 (29/09/2026) — connector manifest template (generator)

**State:** landed. `interop.ConnectorTemplate` supplies the pure-Kof half of the §8 connector generator:
given a connector's identity and declarations it renders a canonical `kof.toml` manifest (§4.2) —
the existing format, never a new one. `render()` is target-neutral; the round-trip is the proof: the
template writes the file, `ConnectorManifest` reads it back and `validate()` passes.

**Proof:** `ConnectorTemplateE2ETest` **5/5** (render golden on JVM + Script + Native x86-64; a real
round-trip write→read→validate on JVM; the JS `IOJS001` gap). No compiler change.

---


## 9.17 Promoted slice 16 (29/09/2026) — C-ABI connector, declarative half (Slice C of §9.16)

**State:** landed. `interop.CAbiConnector` is the declarative half of the official second connector
(C ABI, `D-CONNECTORS`): it composes the landed Core pieces into one validated profile — the foreign
module (`ForeignModule`: library+symbols+ABI+ownership), the declared visible costs (`InteropCost`),
the supported interop types (`InteropType`) and the stability tier (`InteropCompatibility`) — with
`describe()` and validation. The runtime round-trip (dlopen/call) waits on the `foreign module`
grammar + interop error type compiler slices (§9.16 A/B). Pure Kof, no compiler change.

**Proof:** `CAbiConnectorE2ETest` **7/7** (JVM golden + Script + JS + Native x86-64 + three
negatives: unknown cost/type/symbol).

---

# 10. Testing

Each connector must have tests at multiple levels:

* **Unit** — type mapping, metadata, binding generation, ABI representation.
* **Integration** — `Kof → foreign` and `foreign → Kof`.
* **Runtime** — memory, callbacks, exceptions/errors, concurrency, lifecycle.
* **Compatibility** — supported versions and ABI combinations.
* **Negative** — incompatible ABI, incompatible type, invalid ownership, missing symbol,
  missing runtime (must produce a clear diagnostic, R6).

**Cross-language test suite.** A minimal set of operations every connector tests when
supported:

```text
primitive values · strings · arrays · structs · enums · pointers/handles
callbacks · errors · memory ownership · threads · async · opaque objects
```

Each connector implements the subset its language allows. The suite is the ecosystem's
golden corpus; compatibility and negative tests are gates, not extras.

---


**Landed Core suite (29/09/2026).** The pure-Kof Interop Core is covered by 13 cross-target E2E
classes, all green together (**72/72**): `ConnectorManifestE2ETest` (10), `ConnectorCatalogueE2ETest`
(4), `InteropCoreE2ETest` (4), `InteropTypeE2ETest` (5), `InteropOwnershipE2ETest` (5),
`InteropStringE2ETest` (5), `InteropCostE2ETest` (5), `InteropLibraryE2ETest` (5),
`InteropCompatibilityE2ETest` (6), `ConnectorSpiE2ETest` (5), `ForeignModuleE2ETest` (6), `ConnectorTemplateE2ETest` (5), `CAbiConnectorE2ETest` (7) — each runs
JVM golden + Script + Native x86-64 (+ JS where the library is target-neutral; the file-IO libraries
assert the JS `IOJS001` gap), plus the negative diagnostics.

---

# 11. Completion criteria

The initiative is functional when:

* an Interop Core exists;
* the ABI/type model is documented;
* ownership is defined;
* errors are defined;
* callbacks are supported where possible;
* connectors are independent of the compiler core;
* C ABI works;
* JVM interop works;
* at least one scripting runtime works;
* bidirectional tests exist;
* documentation lets real integrations be built;
* connectors can be added without arbitrarily modifying the compiler;
* incompatibilities produce clear diagnostics.

And above all:

> **an existing system can incorporate Kof modules without becoming a full Kof project.**

---

# 12. Implementation rules

Before changing any code (rule 54):

1. analyze the current architecture;
2. locate the existing FFI/ABI mechanisms;
3. locate JVM support;
4. locate KofJS;
5. locate Native;
6. locate library loading;
7. locate type representation;
8. locate memory management;
9. locate the module/package system;
10. locate existing linking mechanisms.

**Do not duplicate** mechanisms that already exist. If a capability partially exists, evolve
it into the Interop Core — never create a second implementation.

**Scope control (rule 55).** The language list is an architectural direction, not a demand to
implement all runtimes now. Prove the architecture with a few connectors, then add languages
incrementally. **A connector is never created to tick a checkbox** — it is ready only when a
real integration can be demonstrated (both directions when the language permits).

**Simplicity Law (rule 11).** Anything reaching the language surface must be extremely
simple, short, idiomatic and intent-representing. Interop convenience may not import foreign
ceremony into Kof.

---

# 13. Decisions (rule 6 — the maintainer decides)

**Resolved by `D-CONNECTORS` (maintainer, `D-FUTURE-BATCH-2809B`, 28/09/2026)** — the front is
authorized and its design questions are locked in `DECISIONS.md`:

* **D-CONNECTORS** — front authorized and ordered (Interop Core + SPI/manifest + catalogue).
* **Ownership vocabulary** — stays **internal** (no language surface). → the Core models it in
  pure Kof (`InteropOwnership`, §9.6); no language change needed.
* **Interop error model** — it **is a language type** (maintainer choice). → requires a compiler
  slice, not a library-only change.
* **`foreign module`** — **enters the grammar now** (maintainer's explicit choice, against the
  recommended defer). → requires a compiler slice; until then the Core models the module as a pure
  Kof descriptor (`ForeignModule`, §9.9) with no grammar.
* **ABI stability tiers + first stable version** — **defined** in `D-CONNECTORS`; the Core ships the
  mechanism (`InteropCompatibility`, §9.11) and the manifest carries the declared tier
  (`ConnectorManifest.stability`, §9.14). Honest note: `D-CONNECTORS` states they are **defined**,
  but the concrete tier table and first stable version are **not yet transcribed** in `DECISIONS.md`
  or here — a documentation gap to record before the ABI slice (never to be invented by an agent).
* **Official second connector after Java** — **C ABI** (Phase 2, §5.2).
* **Promotion roadmap** — `future/` → `docs/development/` **DONE 29/09/2026** (`D-CONNECTORS-GO`).

**Still to land (compiler/language slices, rule 6 records already in place):** the interop error
type on the language surface (Slice B). The `foreign module` **grammar construct is LANDED 01/10**
(Slice A, `ForeignModuleGrammarE2ETest` 5/5) — it enters the grammar as sugar over the existing FFI
path, not as a new ABI engine. Slice B still touches the type system and is a separate slice.

---


## 9.16 Measurement — the remaining authorized work, decomposed (29/09/2026)

`D-CONNECTORS` authorizes more than the pure-Kof Core: the interop **error type** (language), the
`foreign module` **grammar**, and the **C-ABI connector** as the official second connector. This is a
measure-first slice: it names the real anchors and splits the work, and implements nothing by itself.

**Real anchors (verified in-tree):** `FfiSignature.java`, `AbiLayout.java`, `FfiStructLayout.java`,
`CompilerFfiBinding.java`, `JvmFfiRuntime.java`, `nat/NativeFfiCall.java`, `TargetMatrix.java`.

* **Slice A — `foreign module` grammar** (compiler/frontend). **LANDED 01/10** (`Parser.parseForeignModule`,
  `ForeignModuleNode`): the block
  `foreign module libm { library "libm.so.6"; abi c; ownership borrowed; extern fmod(Double a, Double b): Double; … }`
  is sugar over the **existing** FFI path — it desugars to plain `extern` declarations sharing the
  module `library` (no new ABI engine, rule 54), so binding/ABI is exactly `CompilerFfiBinding`.
  `library` is required (else `PARSE097`); `ownership` is validated against the Core vocabulary
  (else `PARSE099`); `foreign`/`module` are contextual keywords that stay identifiers outside the
  header. Proof RED-first: `ForeignModuleGrammarE2ETest` **5/5** — a module calling real libm symbols
  (`fmod`/`sqrt`/`pow`) on the JVM, a per-extern library override, the two honest diagnostics, and
  the identifier retrocompat. Touches lexer/parser → the frontend; no ABI/runtime change.
* **Slice B — interop error type** (type system). The decision makes the interop error a language
  type. Smallest measurable step: the type + its mapping to existing throws/catch; proof = a foreign
  error surfaces as that type and is never swallowed (R6). Touches the type system → compiler lane.
* **Slice C — C-ABI connector, declarative half** (library-first; **LANDED 29/09 as promoted slice 16**, `libs/interop/CAbiConnector.kf`). Compose the landed
  Core pieces into the official C connector profile: `ForeignModule` (library+symbols+ABI+ownership)
  + `InteropCost` (declared visible costs) + `InteropCompatibility` (stability tier)
  + `InteropLibrary` (`.so`/`.dylib`/`.dll`/`.a`/`.lib`); `describe()`/validation; runtime round-trip
  waits on A/B. Pure Kof; no compiler change.
* **Slice D — ABI tier table** (documentation). The concrete tier table and first stable version are
  decided by `D-CONNECTORS` but not transcribed; record them before the ABI slice.

**Order:** D (doc, unblocks) → C (library-first, no compiler) → A → B (compiler slices). Slices A/B
are the first place this front touches the compiler; they are not library-only.

---

# 14. Relation to other plans

* `docs/ffi-abi-structs.md` — the ABI substrate; this plan consumes it, never redefines it.
* `docs/development/graphics-gaming-plan.md` — the named R9 exception (own engine);
  this plan supplies the FFI layer only for the non-engine surface.
* `docs/development/future/PLAN-BOOTSTRAP.md` — E4 requires FFI structs ratified; a mature
  Connector Core strengthens the bootstrap path.
* `docs/development/future/LEGACY_MIGRATION.md` / `TRANSLATOR.md` / `DECOMPILER.md` — the
  legacy front shares the COBOL/Pascal/Fortran legacy-integration motivation; deprioritized
  separately.
* `docs/bugs-and-gaps/ecosystem-coverage.md` §3.15 — existing interoperability coverage rows
  this plan may eventually feed.

---

# 15. Out of scope / non-promises

* No dates and no effort estimates — planning is not a queue.
* No language change is decided here; every needed change is filed as a normal, connector-
  independent gap and decided by the maintainer (rule 6).
* No promise of ABI stability before compatibility tests exist.
* No foreign syntax embedded in Kof; no "Frankenstein language".
* No automatic exposure of complex foreign types as if they were a stable ABI.
* No silent fallback, stub or hidden cost (R6/Q7): unsupported paths fail with a clear
  diagnostic.
