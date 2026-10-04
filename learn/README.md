[English](README.md) | [Português](README.pt_BR.md)

# Learn Kof

Welcome to the Kof learning path.

Kof is a programming language compiled for multiple platforms, strongly typed, object-oriented, with support for inheritance, virtual dispatch, interfaces, exceptions, a native web server and a UI platform (kof.ui) that renders in a native webview via KofJS.

## What Kof is today

* Complete compiler (Lexer → Parser → AST → Type System → IR → JVM/Native/Native.risc/Native.arm/KofJS/KofC) — `intention->Kof->frontend->IR->backend->runtime`
* Classes, records, interfaces, inheritance, virtual dispatch
* Strings, arrays, exceptions, JSON, List\<T\> + `map/filter/reduce`, Map/Set, lambdas with captures, `String?`, pattern `case String s` + `Point(x,y)`
* Web server via `kof serve` + native web stack (`web.app()`)
* Native runtime x86-64 + riscv64/aarch64 (free-list GC `kof_free_head` + mark-sweep) + SQLite + MySQL via `kof_db`
* **KofJS** target: ES Modules (GraalJS) + `kof.http` JVM+JS + Target separation (`jvm/native/native.risc/native.arm/js/kofc`)
* **kof.ui**: Window, Label, Button (actions), Input, Column/Row, View+Style —
  rendering in a native webview (WebKitGTK)
* Official distribution (embedded JDK, tooling, editor support)
* CLI (26 commands): build, run, serve, check, test, script, repl, c, fmt, config gen, bench, profile, inspect, decompile, translate, compare, migrate, debug, info, lsp, install, deps, editor, new, init, version — `kof script` (`let`→`KofScriptGlobals`, repl, --watch), `kof c` (native-only C subset), `kof fmt` (real parser, idempotent — 31/08)
* kof.io: File, Path, Directory (JVM + Native) + kof.http (JVM + Native + JS; https/DNS still gaps)
* 

## Who it is for

- **Beginners** — start with chapter 00 and follow the order
- **Java developers** — start with the introduction and go straight to what is different
- **Contributors** — read Language Design and Compiler Internals

## Structure

```
00 — Introduction
01 — Installation (official distribution)
02 — First Program
03 — Language Fundamentals
...
30 — Contributing
31 — Distribution
32 — CLI and Tooling
33 — Versioning and Releases
34 — Filesystem (kof.io)
35 — kof.ui (colors, widgets, windows)
36 — Security (kof.security)
37 — KofJS (the Web path)
38 — Editors (kof editor)
39 — Universal Standard Library
```

## Index

| # | Chapter |
|---|----------|
| 00 | [Introduction](00-introduction.md) |
| 01 | [Installation](01-installation.md) |
| 02 | [First Program](02-first-program.md) |
| 03 | [Language Fundamentals](03-language-basics.md) |
| 04 | [Variables and Types](04-variables-and-types.md) |
| 05 | [Control Flow](05-control-flow.md) |
| 06 | [Functions](06-functions.md) |
| 07 | [Classes and Objects](07-classes-and-objects.md) |
| 08 | [Properties](08-properties.md) |
| 09 | [Interfaces](09-interfaces.md) |
| 10 | [Inheritance](10-inheritance.md) |
| 11 | [Generics](11-generics.md) |
| 12 | [Collections](12-collections.md) |
| 13 | [Nullability](13-nullability.md) |
| 14 | [Exceptions](14-exceptions.md) |
| 15 | [Pattern Matching](15-pattern-matching.md) |
| 16 | [Lambdas](16-lambdas.md) |
| 17 | [Functional Programming](17-functional-programming.md) |
| 18 | [Concurrency](18-concurrency.md) |
| 19 | [Packages and Modules](19-packages-and-modules.md) |
| 20 | [Annotations](20-annotations.md) |
| 21 | [Java Interoperability](21-java-interoperability.md) |
| 22 | [JVM](22-jvm.md) |
| 23 | [Testing](23-testing.md) |
| 24 | [Build Tools](24-build-tools.md) |
| 25 | [Spring](25-spring.md) |
| 26 | [Real Application](26-real-world-application.md) |
| 27 | [Best Practices](27-best-practices.md) |
| 28 | [Language Design](28-language-design.md) |
| 29 | [Compiler Internals](29-compiler-internals.md) |
| 30 | [Contributing](30-contributing.md) |
| 31 | [Distribution](31-distribution.md) |
| 32 | [CLI and Tooling](32-cli-tooling.md) |
| 33 | [Versioning and Releases](33-versioning-releases.md) |
| 34 | [Filesystem (kof.io)](34-file-system.md) |
| 35 | [kof.ui — Colors, Widgets and Windows](35-kof-ui.md) |
| 35 | [UI and Styling](35-ui-and-styling.md) |
| 36 | [Security (kof.security)](36-security.md) |
| 37 | [KofJS — the Web path](37-kofjs.md) |
| 38 | [Editors — kof editor](38-editors.md) |
| 39 | [Universal Standard Library](39-stdlib.md) |
| 40 | [Low Level — FFI, Native Profiles and Bare Metal](40-low-level.md) |
| 41 | [Android](41-android.md) |
| — | [Standard Library — per namespace](stdlib/README.md) |
| — | [Native — Multiplatform](native/README.md) |

## Recommended order

```
00 → 01 → 02 → 03 → 04 → 05 → 06 → 07 → 08 → 09 → 10
→ 11 → 12 → 13 → 14 → 15 → 16 → 17 → 18 → 19 → 20
→ 21 → 22 → 23 → 24 → 25 → 26 → 27
```

## For LLMs

See also `training/` for a structured corpus of Kof knowledge.

## Current state

| Chapter | Topic | Status |
|----------|--------|--------|
| 00 | Introduction | ✅ |
| 01 | Installation | ✅ |
| 02 | First Program | ✅ |
| 03 | Fundamentals | ✅ |
| 04 | Variables and Types | ✅ |
| 05 | Control Flow | ✅ |
| 06 | Functions | ✅ |
| 07 | Classes and Objects | ✅ |
| 08 | Properties (direct fields, no getters/setters) | ✅ (rewritten 02/09 — direct field; `class X(...)` = record) |
| 09 | Interfaces | ✅ |
| 10 | Inheritance | ✅ |
| 11 | Generics (erasure) | ✅ |
| 12 | Collections (List/Map/Set + map/filter/reduce) | ✅ |
| 13 | Nullability | ✅ Basic (`String?`) |
| 14 | Exceptions | ✅ (JVM + Native unwinding) |
| 15 | Pattern Matching | ✅ (`case String s` + `Point(x,y)`) |
| 16 | Lambdas | ✅ (with captures) |
| 17 | Functional Programming | ✅ (`map/filter/reduce`) |
| 18 | Concurrency (spawn) | ✅ (JVM virtual threads; Native pthread 31/08; JS event-loop 03/09) |
| 19 | Packages and Modules | ✅ (`a.b.C` fix) |
| 20 | Annotations | Implemented (JVM/KofJS) |
| 21 | Java Interop | Partial (compatible JVM bytecode; direct Java call functional) |
| 22 | JVM | ✅ |
| 23 | Testing (kof test + assert + structured suite) | ✅ |
| 24 | Build Tools | Partial (`kof build`/`kof test` native; Maven/Gradle plugin planned) |
| 25 | Spring | Planned (`kof.web` + `kof_db` already cover the case without Spring) |
| 26 | Real Application | Planned with Spring; path without Spring via `kof.web`/`kof.orm` |
| 27 | Best Practices | ✅ |
| 28 | Language Design | ✅ |
| 29 | Compiler Internals | ✅ |
| 30 | Contributing | ✅ |
| Glossary | Glossary | ✅ |
| Multiplatform | Native | ✅ |
| 36 | Security (kof.security) | ✅ (JVM/Native/JS; gaps SECN00x) |
| 35 | kof.ui (widgets, windows, webview) | ✅ (JS render; JVM/Native no-ops) |
| 37 | KofJS (Web path) | ✅ (alpha) |
| 39 | Standard Library (math/strings/encoding/uuid/validation/time) | ✅ (4 targets; gates FLT/NAT-STR01) |

Kof is in a consolidation phase. The compiler is functional with JVM,
Native (x86-64 free-list), Native.risc, Native.arm, KofJS and KofC backends (0.5.0-beta).

**Tests:** 805

**What works today (0.5.0-beta — Sep 2026 — `jvm/native/native.risc/native.arm/js/kofc`):**
- Complete frontend (lexer, parser, type system, semantics) — `intention->Kof->frontend->IR->backend->runtime`
- Six targets: JVM (ASM), Native x86-64 (free-list GC), Native.risc, Native.arm, KofJS (GraalJS) and KofC (native-only C subset)
- Classes, records, inheritance, interfaces, virtual dispatch, generics (erasure), `a.b.C` imports fix (largeproj)
- Functions (without `fun`), lambdas with captures, if-expr, switch with `case String s` + `Point(x,y)` destructuring, `String?`, for-in
- Real exceptions (JVM + Native unwinding), `assert`, `spawn` (JVM virtual threads, Native pthread — 31/08; JS event-loop — CONC003 03/09)
- Strings (complete API), arrays, `List<T>` + `map/filter/reduce`, `Map<K,V>`/`Set<T>`, JSON, kof.io, kof.time, `kof.http` (JVM+Native+JS), `kof_db` (SQLite+MySQL WIP)
- `KofScript` (top-level `var`/`val` → `KofScriptGlobals` — no `let`/`const`, JS sugar removed 06/09 — `kof script --repl`, `--watch`), `KofC` (`kof c <file.c>` native-only)
- CLI (26 commands): `build, run, serve, check, test, script, repl, c, fmt, config gen, bench, profile, inspect, decompile, translate, compare, migrate, debug, info, lsp, install, deps, editor, new, init, version` + `--target=jvm|native|native.risc|native.arm|js|android`
- `kof serve` (native `web.app()` + legacy `handle()` API; each connection in a virtual thread), `kof test` (`test "nome" {}` suite on the 3 targets), `kof bench`/`kof profile`/`kof inspect`/`kof debug`
- Official distribution (embedded Temurin 25, package, CI/release) — Target separation (`Target.NATIVE_RISCV64/AARCH64`)


**What is planned / real gaps:**
- Target gaps: HTTP002 (https + real DNS on Native — http/1.1 works 03/09; timeout/retry/circuit silent → §259), ~~SCHED001 (scheduler Native)~~ ✅ 31/08, ~~PROC001 (process.spawn Native)~~ ✅ 26/09 (x86-64/riscv64/aarch64; só MCU/riscv32), ~~DB001 (db in JS)~~ ✅ 16/09, ORM001 (native/JS ORM), WEB002 (TLS on native web — server base 03/09), AND00x (Android Phase 2+) — ~~CONC003 (real async in JS)~~ ✅ 03/09
- GC auto-collect on exhaustion on Native (mark-sweep landed 09/03; auto-collect needs safe-points — §260)
- Complete native MySQL/MariaDB (wire protocol: SHA-1 auth scramble done; handshake, query and prepared statements missing)
- `when` guards in pattern matching, deep flow analysis for `String?`
- Complete hover/completion in the LSP, native Debugger (DWARF) and JS (source maps)
