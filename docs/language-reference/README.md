[English](README.md) | [Português](README.pt_BR.md)

# Kof Language Reference

**Specification version:** 0.6.7 · **Extracted from:** `kof-compiler` (initial extraction branch `beta-0.3.0`, 06/09/2026; current branch `beta-0.5.0`)

This is the **Kof language reference**. It describes *what a valid Kof program
is* and *what that program means* — regardless of how the current compiler
implements it.

> **Founding rule of this reference:** nothing here is invented. Every rule is
> extracted from the compiler source, from the tests, or from observable
> behavior verified by execution. Where the behavior could not be determined
> with confidence, the rule is marked **UNSPECIFIED**. An honest specification
> about what it *doesn't* know is worth more than a false and complete one.

---

## Language ≠ Compiler ≠ Target

These are three distinct levels, often confused in the project's old
documentation. This separation is the central purpose of this reference:

`text
Kof Language Specification  (this directory)
        │
        │ defines (what a valid program is and what it means)
        ▼
   Kof Language             (a set of rules, not a binary)
        │
        │ implemented by
        ▼
   Kof Compiler             (a specific implementation, in Java)
        │
        ├── Frontend (Lexer, Parser, AST, Semantic Analysis)
        ├── Middle-end (IR, Optimizations)
        └── Backends (JVM, Native, JS)
        │
        │ produces
        ▼
   Targets                  (JVM, Native x86_64/riscv64/aarch64, JS, Android)
`

- **Kof** is the *programming language*. It exists as a set of rules.
- **Kof Compiler** is *one implementation* of the language (the `kof-compiler`
  of this repository, written in Java). It is not the definition of the
  language.
- **Kof4J** is the *JVM backend/line* (bytecode via ASM). **KofNative** is the
  *native backend* (asm x86_64/riscv64/aarch64). **KofJS** is the *JavaScript
  backend* (ESM). They are **compilation targets**, not dialects of the
  language.

The conceptual intent is:

`text
same Kof language ──┬── JVM
                    ├── Native
                    └── JS
`

and **not** `Kof JVM` / `Kof Native` / `Kof JS` as semantically different
languages. When there is a real divergence between targets, it is recorded as
a *target limitation* or *target-dependent behavior* (see
[specification-status.md](specification-status.md) and
[specification-gaps.md](../bugs-and-gaps/specification-gaps.md)), never hidden.

---

## What each document answers

| Document | Question it answers |
|---|---|
| [lexical-structure.md](lexical-structure.md) | What are the valid tokens? (identifiers, literals, operators, comments, keywords) |
| [grammar.md](grammar.md) | What is the formal grammar? (lexical and syntactic EBNF, precedence, associativity) |
| [syntax.md](syntax.md) | How is each construct written? (concrete form, examples) |
| [types.md](types.md) | What types exist and how are they written? |
| [type-system.md](type-system.md) | What operations are valid? When is there a type error? What does the type system guarantee? |
| [expressions.md](expressions.md) | Semantics of each expression and operator. |
| [statements.md](statements.md) | Semantics of each statement and control flow. |
| [functions.md](functions.md) | Declaration, types, parameters, return, recursion, entry point. |
| [closures.md](closures.md) | Lambdas, function types, variable capture. |
| [classes.md](classes.md) | Classes, records, enums, interfaces, entities, inheritance, visibility. |
| [modules.md](modules.md) | Packages, imports, name resolution, compilation unit. |
| [semantics.md](semantics.md) | Execution model, evaluation order, scope, lifetime, errors. |
| [concurrency.md](concurrency.md) | Concurrency model: `spawn`/`await`, virtual threads (JVM), async/await (JS), pthread (Native). |
| [concurrency-memory-model.md](concurrency-memory-model.md) | Happens-before edges and memory-visibility guarantees (SG-020). |
| [specification-status.md](specification-status.md) | Classification of each feature (Stable/Experimental/…). |

The **compiler implementation** (pipeline, IR, optimizations, backends) has
its own document: [../compiler-architecture.md](../architecture/compiler-architecture.md).
Java internals, compiler classes, and implementation structures **do not
belong** to this reference — except when they are necessary to explain an
observable behavior of the language (in that case, the reference cites the
source file as evidence, not as definition).

---

## Status legend

Each rule may carry a label. The categories used in this reference are those
that make sense for the current state of Kof (beta):

| Label | Meaning |
|---|---|
| **Stable** | Behavior defined by the language, frozen (rule of frozen semantics 0.2.6-beta). Does not change without a version bump + migration. |
| **Experimental** | Implemented and testable, but subject to change. Not frozen. |
| **Implementation-defined** | The language does not fix the result; the current compiler decides. Another Kof compiler may legitimately diverge. |
| **Target-specific** | The observable behavior depends on the target (JVM/Native/JS). Documented as a difference, not hidden. |
| **Unspecified** | The language does not yet define this point. It is not "anything goes" — it is "the specification doesn't know yet". |
| **Planned** | A plan/document exists, but it is **not** implemented. It must never be used as if it existed. |

The **Unspecified** label is preferable to an invented rule. See
[specification-status.md](specification-status.md) for the per-feature
classification and [specification-gaps.md](../bugs-and-gaps/specification-gaps.md)
for the catalog of gaps (SG-00x) and divergences between documentation, code,
and tests.

---

## How this reference is verifiable

Every normative statement points to **evidence**:

- **Code** — `file.java:line` in `kof-compiler` (e.g., precedence in
  ExpressionParser (precedence)).
- **Test** — a test in the suite that demonstrates the rule (e.g.,
  `PackagesE2ETest`, `KofSwitchExprE2ETest`).
- **Execution** — behavior observed by running a program (used to
  distinguish "compiles" from "works"; marked as a *probe* when there is no
  dedicated test).

When code, test, and documentation diverge, the divergence is recorded in
[specification-gaps.md](../bugs-and-gaps/specification-gaps.md) — never
silently resolved in favor of one of the sources.

---

## Conformance (future possibility, not implemented)

A definition of conformance would be:

> A Kof compiler is **conformant to the specification** when it accepts all
> programs that the specification declares valid, rejects those it declares
> invalid (with the specified diagnostics), and produces for each valid
> program the meaning that the specification defines.

Today this **cannot be rigorously defined** because parts of the language
are still **Unspecified** or **Implementation-defined** (`private`/`protected`
on fields; `bool→numeric` coercion is implementation-defined; generics without
variance/bounds; `Map`/`Set` iteration order). Several former blockers are now
**resolved** — inheritance subtyping (`SEM021`), `val` reassignment (`SEM037`),
interface coverage (`SEM043`), abstract instantiation (`SEM041`).
The "Conformance" section of [specification-status.md](specification-status.md)
lists exactly what still prevents a rigorous definition. There is, for now,
no formal *conformance suite* — but the E2E tests per target are the embryo of
one, and each rule of this reference marks whether it has a test (evidence) or is
a candidate for a new conformance test.
