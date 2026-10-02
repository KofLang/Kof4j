[English](LEGACY_MIGRATION.md) | [Português](LEGACY_MIGRATION.pt_BR.md)

# LEGACY_MIGRATION.md — Legacy Software Migration Platform

**Status:** **REOPENED by the maintainer (`D-DEPRIORITIZED-REOPEN`, 28/09 batch)** —
still plan-only here; promotion to current work is one-at-a-time per
`D-FUTURE-PROMOTION`. The code already
in the repo stays. — **central and single doc of the migration
platform** (dropped from `future/` on 12/09; MERGED `LEGACY_IR.md` into §4 and
`DIFFERENTIAL_TESTING.md` into §8 on 13/09 — duplicated concepts, zero
unique content; technical work-logs live in `DECOMPILER.md`/`TRANSLATOR.md`).
The platform exists: `kof inspect/decompile/translate/compare/migrate`
registered in `Main.java` (test count = single source in
`roadmap.md` §23 TIER 3–5; `inspect` is the IR stats command, with no test
class of its own). Method body recovery is still partial — the traceable
report exposes it honestly)
**Scope:** migration platform (implementation started in 0.3.x)
**Created:** August 22, 2026 · **Last consolidation:** 13/09/2026

---

## 1. Vision

Kof is not just a language for creating new software.

The long-term vision is a platform capable of **analyzing, recovering,
translating and modernizing legacy systems to Kof**:

> Preserve the functionality of legacy software while we modernize its
> implementation to Kof.

The platform must work both with available source code and with systems where
the original code was lost — using bytecode, binaries, metadata, build
artifacts and observable behavior as sources of information.

**This documentation is architecture + implementation state (§3 has the real
table: commands ✅ in the CLI, honest partial coverage).**

---

## 2. Fundamental Principle

The platform does not assume `Legacy → Java → Kof`.

When possible, it uses the direct path:

```text
Legacy
   ↓
Legacy Semantic IR
   ↓
Kof AST
   ↓
Kof IR
```

For JVM:

```text
JVM Bytecode
     ↓
Bytecode Analysis
     ↓
Legacy Semantic IR
     ↓
Kof AST
```

Java can be a **supported origin** (via translator), but never a
**mandatory intermediate representation**. This avoids an artificial Java
generation step between the legacy artifact and Kof.

---

## 3. Planned Components

| Command | Purpose | Status |
|---------|-----------|--------|
| `kof inspect <input>` | Structural analysis of `.class`/`.jar`/binaries | ✅ `Inspect.java` (Main.java:25) |
| `kof decompile <input>` | Recovery of Kof code from compiled artifacts | ✅ `Decompile.java` (Main.java:26; partial body → honest stub) |
| `kof translate <input>` | Source-code migration (first target: Java → Kof) | ✅ `Translate.java` (Main.java:27; Java subset) |
| `kof migrate <input>` | Full migration with report | ✅ `Migrate.java` (Main.java:29; traceable report) |
| `kof compare <legacy> <kof>` | Differential testing between systems | ✅ `Compare.java` (Main.java:28; stdout/exit/stderr) |

**All commands exist in the CLI** (verified 12/09 — `Main.java:25-29`;
test count = single source in `roadmap.md` §23 TIER 3–5). What remains
in development is the
**coverage** of recovery (complex method bodies → honest UNKNOWN stub;
translator's Java subset).

### 3.1 `kof inspect` (implemented)

Analysis of existing systems. Responsibilities:
identify format, platform, version; analyze dependencies; identify
classes, methods, interfaces, fields, types; identify external calls,
reflection, dynamic loading, JNI/FFM/native calls; identify metadata,
debug information, serialization, resources; estimate recoverability.

Conceptual output (illustrative values — no metric is real without defined
implementation and methodology):

```text
Classes:              1842
Methods:              17391
Reflection:           detected
Native calls:         detected
Debug metadata:       partial

Recoverability:
Types                  HIGH
Control Flow           HIGH
Method Signatures      HIGH
Local Names            LOW
Comments               NONE
```

### 3.2 `kof decompiler` (implemented)

Intended for recovering Kof code from `.class`/`.jar`/`.war`.

Pipeline:

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

The decompiler prioritizes: semantic equivalence, readability, structure, types,
control flow, calls, inheritance, interfaces, recoverable generics,
exceptions, annotations, metadata.

**It does not try to artificially reconstruct the original Java.** The goal is
to produce **equivalent idiomatic Kof**, not to pretend the original source
was recovered.

### 3.3 `kof translate` (implemented)

Source-code migration (first target: Java → Kof).

```text
Java Source
     ↓
Java Parser
     ↓
Java AST
     ↓
Java Semantic Model
     ↓
Translation IR
     ↓
Kof AST
     ↓
Kof Source
```

The translator **does not work by textual substitution** (`public → ...`).
It understands the program's semantic structure.

Planned progressive support: classes, interfaces, inheritance, generics,
overloads, constructors, exceptions, annotations, records, enums, lambdas,
nested classes, anonymous classes, static initialization, access modifiers,
Java standard library, external library calls.

---

## 4. Legacy Semantic IR (was `LEGACY_IR.md` — MERGED here 13/09)

The Legacy Semantic IR is the intermediate representation between the origin
format and the Kof AST. It exists so that each adapter (JVM bytecode, Java,
COBOL, etc.) produces the SAME semantic representation — allowing the
rest of the pipeline (decompiler, translator, differential testing) to be
independent of the origin.

Concepts represented: types, functions, methods, fields, inheritance,
interfaces, calls, control flow, exceptions, memory/external operations,
constants, data flow, metadata, dynamic behavior and **unknown operations** —
unknown information stays `UnknownType`/`UnknownCall`/`UnknownField`/
`UnknownBehavior`, never fabricated code "that looks valid".

### 4.1 Confidence Model (implemented — `Confidence.java`, 5 levels)

```text
Recovered exactly        — observed directly in the artifact
Recovered with metadata  — observed + metadata (debug info, signatures)
Inferred                 — derived from analysis (data flow, types)
Heuristic                — plausible, based on heuristics
Unknown                  — unrecoverable (becomes an honest stub in the .kf)
```

Each recovered element carries the level; the tool always distinguishes
**observed** from **inferred**.

### 4.2 Source Mapping

The IR preserves the relations `Legacy Source ↕ Legacy Semantic IR ↕ Kof AST ↕
Kof Source ↕ Kof IR` — the basis of diagnostics, auditing, comparison and the
traceable report of `kof migrate` (§8.1).

### 4.3 Implemented state (measured, never from memory)

- Phase B/C/D (JVM): `BytecodeReader/Decoder/Statements/Frame` +
  `Type.fromJvmDescriptor`/`fromJvmSignature` (`Signature` attribute, JVMS
  4.7.9.1 — generics/wildcards/type-variables, `367d6c4`); proof
  `ClassFileE2ETest.genericSignatureRecovery` + `DecompileTest` (live count
  in `roadmap.md` §23 TIER 3–5).
- Recovery of **other platforms** (Native/JS/binaries): not started —
  it is Phase I (§9), gated by R12.

### 4.4 Unrecoverable Information (was §5/`LEGACY_IR.md` §3)

Compilation is a transformation with loss. After `Source → Compiler → Bytecode`
the following disappear: comments, local names (without debug info), original
syntactic structure, formatting, generic information (erasure), programmer
intent and eliminated abstractions. **Decompilation is not recovery of the
original source** — it is recovery of behavior and structure; the
unrecoverable is EXPLICIT (UNKNOWN stub), never invented.

---

## 5. Relationship with the Kof Compiler

The platform does not duplicate existing components. It reuses:
Kof Lexer, Kof Parser, Kof AST, Kof Type System, Kof Semantic Model,
Kof IR, Kof Backend.

```text
              ┌───────────────┐
              │ Kof Source    │
              └───────┬───────┘
                      ↓
                 Kof Frontend
                      │
Legacy ───────────────┤
                      │
Java ─────────────────┤
                      │
JVM Bytecode ─────────┤
                      ↓
              Legacy Semantic IR
                      ↓
                   Kof AST
                      ↓
                  Kof Compiler
                      ↓
                  Kof IR
                      ↓
               JVM / Native
```

The migration tool is a **natural extension of the compiler**,
not a second independent compiler.

## 6. Security and Legality

The platform is a **software engineering tool**. The user must
have adequate authorization and rights over the software analyzed.

The platform does not promise to circumvent: DRM, copy protection, access
controls, security mechanisms, licensing.

Focus: preservation, interoperability and authorized modernization.

---

## 7. Future Formats

The architecture prepares format adapters beyond the JVM:

```text
Source / Binary
       ↓
Legacy Adapter
       ↓
Legacy Semantic IR
       ↓
Kof
```

Potential examples (do NOT implement at the beginning): COBOL, PL/I, Assembly,
legacy binaries, proprietary bytecode, custom VMs.

The goal is to prevent the project from becoming conceptually stuck to the JVM.

---

## 8. Differential Testing (was `DIFFERENTIAL_TESTING.md` — MERGED here 13/09)

Validates that a migration preserves behavior: same input vector in the
original program and in Kof, comparing **observable outputs**.

- **What to compare (`kof compare`, implemented):** stdout, stderr, exit code
  (`Compare.java`, `--stdin`/`--arg`; proof `CompareTest` 6/6). Beyond comparable
  stdout — typed exceptions, return values, files, DB mutations,
  protocols, side effects — is what remains pending in Phase G.
- **Divergence classification:** equivalent · divergent within scope
  · divergent outside scope · undefined behavior.
- **Acceptance criterion for a critical system:**
  `compile + static analysis + behavioral testing + differential testing +
  manual review + migration report` — "it compiled" is not enough.
- **Systems without source code:** when the source was lost, the observable
  behavior of the original binary is the source of truth (binary + metadata +
  dependencies + configuration + database + observed behavior). This is
  **software archaeology**, not trivial conversion.

### 8.1 Migration Report (`kof migrate`, Phase H)

Report with migration traceability. Conceptual structure (the numbers
below are ILLUSTRATIVE — no metric is real without defined implementation and
methodology):

```text
Kof Migration Report

Input:     legacy-application.jar
Output:    kof-application/

Recovered:     94.2%        ← % of units WITHOUT an UNKNOWN stub
Warnings:      17
Unrecoverable: 3
Manual review: 12 locations

Behavioral tests:
    183 passed
    2 divergent
```

---

## 9. Implementation Order

Do not start by trying to support all legacy systems.

```text
Phase A  JVM Inspection          (.class/.jar + structural analysis)
Phase B  JVM Bytecode IR         (Class File → Bytecode IR)
Phase C  Control Flow Recovery   (basic blocks, branches, loops, switches, exception regions)
Phase D  Type Recovery           (primitives, references, arrays, generics, inheritance)
Phase E  Kof Decompiler          (generate Kof source)
Phase F  Java Translator         (Java Source → Kof)
Phase G  Differential Testing    (Legacy vs Kof)
Phase H  Migration Reports       (complete reports)
Phase I  Additional Frontends    (COBOL, PL/I, Assembly, proprietary formats)
```

Before implementing: define the architecture, validate with small prototypes,
and only then turn the prototypes into official components.

---

## 10. Success Criterion

The initiative is successful when a real legacy system produces:

```text
Legacy System
      ↓
Analysis
      ↓
Recoverable Semantics
      ↓
Kof Implementation
      ↓
Behavioral Verification
      ↓
Modern Deployment
```

with: traceability, diagnostics, limitations report, differential tests,
human review, readable Kof code, native/JVM compilation, compatible behavior
within the defined scope.

**Central question:**

> Are we recovering real behavior or merely fabricating code that
> looks plausible?

If the tool cannot distinguish these two things, the migration is not
reliable.

---

## 11. Related Documentation

- `DECOMPILER.md` — technical work-log of body recovery (Phase E/§7:
  pipeline, structural joins, records, drift-check on the corpus)
- `TRANSLATOR.md` — Java → Kof subset (Phase F; active owner of the translator lane)
- ~~`LEGACY_IR.md`~~ → §4 of this doc (MERGED 13/09 — concepts+status; there
  was no unique content beyond the decompiler work-log)
- ~~`DIFFERENTIAL_TESTING.md`~~ → §8 of this doc (MERGED 13/09)
- `roadmap.md` §23 (TIER 3–5) — order/priorities with measured status
- `docs/audits/PLANNING-FUTURE-AUDIT.md` — planned×actual audit
