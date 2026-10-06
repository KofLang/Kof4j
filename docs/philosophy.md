[English](philosophy.md) | [Português](philosophy.pt_BR.md)

# Kof Philosophy

**Last updated:** September 12, 2026
**Version:** 0.5.0-beta (7 targets; `VERSION` 0.5.0-beta)

---

## Central Principle

> The programmer should write the intention.
> The language and the runtime take care of the complexity.

Kof does not exist to be "yet another Java". Kof exists to solve problems that Java does not solve well — or that it solves only with complex frameworks.

---

## The "Paradigm" of Intention

> **Honest warning:** it is not a real paradigm. It does not exist in any
> paradigm catalog, it has no formal definition and nobody has published a
> paper about it. It is **object orientation taken to the extreme**: the code
> expresses *what* should happen, and the platform (language + compiler +
> runtime + stdlib) decides *how* — per target, per platform, by convention.

### The chain of intention

```text
intenção → Kof → compilador → backend
```

The programmer writes the intention in Kof. The compiler translates it to the single IR.
The backend (JVM, Native, KofJS) decides the mechanisms. No mechanism leaks
above the intention line.

### What intention looks like in practice

| Intention | Kof Code | What the platform decides |
|----------|-----------|---------------------------|
| "run this in parallel" | `spawn processar()` | JVM: virtual threads; Native: pthread (CONC001 closed) |
| "respond /users/:id" | `app.get("/users/:id") { ... }` | own HTTP server, no servlet container |
| "deserialize this" | `json.decode<User>(body)` | JSON engine + binding by type |
| "show a window with a button that sums" | `Window`, `Button("+1", () -> ...)` | KofJS renders in the native webview; JVM/Native are no-ops |
| "this color is red" | `Color(255, 0, 0)` | 32-bit Int; channels by bitwise in the compiler |
| "this is a test" | `assert(2 + 2 == 4)` | exit code, `kof test`, harness |
| "read this file" | `File("x.txt")` | backend IO (JVM/Native/JS) |

In none of these cases does the programmer write `Thread`, `HttpServer`,
`JsonParser`, `WebView`, `0xAARRGGBB` or `FileInputStream`.

### Why it is the extreme of OO — not a new paradigm

Object orientation already says: objects respond to messages; the *how* belongs
to the object. Intention radicalizes this contract in three leaps:

1. **From the object to the language** — it is not only the object that hides the how; the
   *language* hides entire infrastructure (concurrency, HTTP, IO, UI).
2. **From the runtime to the compiler** — part of the "how" is decided at
   compile-time (color channels by bitwise, handle packing, lambdas with
   captures as synthetic fields+constructor).
3. **From the code to the platform** — what is not the program's intention does
   not exist in the code. If it is essential to any program, it belongs to the
   stdlib; if it is essential to the language, it belongs to the compiler.

### The line between intention and mechanism

The practical rule: **if a programmer needs to know the mechanism to write the
intention, the design failed.** Examples of leakage that Kof rejects:

- `new Thread(...).start()` → rejected; write `spawn`.
- Annotations + container for HTTP → rejected; write `app.get(...)`.
- WebView/JavaFX in UI code → rejected; write `Window(...)`.
- Manual color conversion → rejected; write `Palette.red`.

### Honest limits of intention

Intention is single, but the backend cannot always realize it — and that is
**diagnosed at compile-time, with a gap code**, not silently:

- ~~`spawn` on Native → `CONC001`~~ — closed 31/08 (pthread)
- JSON of objects on Native → `JSN002`
- staticity on Native → documented no-op
- `kof.ui` on JVM/Native → no-op handles (rendering is KofJS)

The contract: the intention compiles on all targets; the target that cannot
execute it says so right away, with a code and documentation.

### Practical consequences

- **Anti-pattern:** writing Java inside Kof (`training/anti-patterns/
  java-like-code.md`) — it is leaking mechanism into the intention.
- **Idiom:** represent the domain, not the accidental implementation
  (`training/idioms/architecture.md`).
- **Multi-target:** the same code is the same intention; changing target does
  not change the code (only the realization).

---

## Architectural Principles

### 1. Simplicity by Default

The common case should be as simple as possible. If the programmer needs to write more than 3 lines for something common, something is wrong.

```kof
// Kof: simple
main() {
    println("Hello")
}

// Java equivalent:
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello");
    }
}
```

### 2. Zero Boilerplate

If the compiler can infer something, the programmer should not need to write it.

- Constructors: generated automatically when possible
- Getters/Setters: not needed (fields are directly accessible)
- toString: generated for records
- Equality: generated for records

### 3. Runtime Hides Complexity

The programmer should NOT know:
- malloc/free
- Pointers
- GC
- ABI
- Calling conventions
- Memory layout
- JVM details
- Native runtime details

```kof
var a = new Int[100]  // allocates, initializes, manages
var s = "Hello"       // allocates KofString
```

### 4. Same Code, Multiple Targets

The same Kof program should work semantically on JVM and Native. The programmer should not need to change their code to switch targets.

### 5. Memory is the Runtime's Responsibility

The programmer should NOT need to:
- Free memory manually
- Manage ownership
- Avoid memory leaks
- Know the object lifecycle

The runtime should solve this automatically.

### 6. Compiler Eliminates Classes of Problems

Errors that can be detected at compile-time should NOT exist at runtime:
- Incompatible types
- Nonexistent methods
- Nonexistent fields
- Wrong number of arguments

### 7. Convention > Configuration

If something can be solved by convention, it does not need configuration.

```kof
// By convention, main() is the entry point
main() {
    // ...
}

// By convention, the file name defines the module
```

### 8. Type Safety at Compile-time

Type errors should be caught before execution. The compiler should be rigorous.

### 9. Small APIs

Less is more. An API with 5 useful methods is better than one with 50 methods of which 40 are rarely used.

### 10. Language Solves, Framework Doesn't

If the language can solve a problem directly, do not create a framework for it.

| Problem | Framework Solution | Kof Solution |
|----------|------------------|-------------|
| HTTP routing | Spring MVC | ✅ `app.get("/users") { ... }` (implemented — `web.app()` on JVM) |
| Validation | Bean Validation | ✅ `kof.validation` (13 predicates, 3 targets) — the syntax `name: String required` is a **future proposal** |
| Serialization | Jackson | ✅ `json.encode/decode` (3 targets) |
| Configuration | application.properties | ✅ `config.int("server.port", 8080)` — the block `config { port = 8080 }` is a **future proposal** |

### 11. Don't Copy Java

Kof should not copy Java features just because they exist. Every feature should be questioned:

- "Does this solve a real problem?"
- "Is there a simpler way?"
- "Is the complexity worth it?"

### 12. Don't Require Infrastructure for Basic Features

Creating an HTTP server should not require:
- Spring Boot
- Tomcat
- Servlet container
- Configuration XML
- Annotations

It should be something like:
```kof
var app = web.app()
app.get("/users") { return users.all() }
```
(implemented on JVM — see `docs/stdlib/stdlib-web.md`)

### 13. Performance Without Sacrificing Ergonomics

The language should be ergonomic AND performant. It should not be necessary to write ugly code to get performance.

### 14. Native and JVM Share Semantics

The language semantics are single. The backends implement that semantics differently, but the observable behavior should be the same.

---

## Compatibility Contract (1.0)

> **Status:** the living 1.0 contract. Issue
> [#761](https://github.com/KofLang/Kof4j/issues/761) is **permanent and is
> never closed** (maintainer, 05/10) — it tracks these twenty mandates as the
> guarantee the first RC must honor.

Kof does not break user code to make the compiler's life easier. If the
compiler changed, it is the compiler's responsibility to keep understanding the
code it already understood.

1. **We do not break the language frontend.** Valid Kof code must remain valid Kof code in later versions.
2. **If something worked in a previous version and stopped working now, it is a compiler bug — never the user's.** Regressions are compiler defects.
3. **Every syntax change is incremental and never destructive.** New ways of writing code must coexist with the existing ways.
4. **Compatibility is a language feature.** Keeping existing code working is part of the compiler's contract.
5. **Internal compiler changes must not require user-code changes.** Parser, AST, IR, optimizer or backend refactors are the compiler's responsibility.
6. **The compiler must absorb complexity, not transfer it to the user.** If an internal change would require generalized Kof-code adaptation, the solution must first be sought inside the compiler.
7. **Valid Kof code must not depend on the compiler version to stay valid.** Compiler evolution must be transparent to existing programs.
8. **New features must coexist with existing features.** Adding a capability never justifies removing or invalidating a previous one.
9. **Deprecations must be explicit, gradual and documented.** No feature disappears without a clear deprecation process.
10. **Deprecation does not mean immediate breakage.** A deprecated feature keeps working for the project's defined compatibility period.
11. **Version changes are not a justification for breaking users.** No version, including a major one, may introduce avoidable breaks.
12. **Backend changes cannot break the language frontend.** JVM, Native, JS, WASM or any other target must preserve the same language contract.
13. **The same Kof code keeps the same semantics across targets whenever the language contract allows it.** Platform-specific differences are the backend's responsibility.
14. **A new version that rejects previously valid code has a regression until proven otherwise.** The change must be investigated as a bug before being accepted as a legitimate language change.
15. **Every fixed regression gets a non-regression test.** What broke once must have automated proof it will keep working.
16. **The compiler must preserve the semantics of existing code.** Parser, AST, IR, optimizer or codegen changes must not alter the meaning of valid programs.
17. **On a conflict between a new interpretation and legacy behavior, legacy behavior wins.** Compatibility takes precedence over implementation convenience.
18. **Optimizations must never alter the observable behavior of a valid program.** Performance is the compiler's responsibility and may not require user-code changes.
19. **The compiler must be more compatible with the user than with its own implementation.** If preserving compatibility requires extra compiler complexity, that complexity belongs to the compiler.
20. **The user must not have to track the compiler's implementation to keep their code working.** Kof evolves without requiring users to rewrite code merely because the compiler evolved.

---

## What Kof Is NOT

- It is not Java with another syntax
- It is not Kotlin 2
- It is not a transpiler to Java
- It is not an interpreter (the compiler is real: bytecode/ELF/ESM; the `KofInterpreter` of the KofScript target executes the SAME optimized frontend IR — parity by construction, not a disguise)
- It is not a language for scripts (although it can be used for that)
- It is not a language for the web (although it can be used for that)
- It is not markup in disguise: `kof.ui` does not take HTML tags, CSS classes/inline styles or `innerHTML` — the program declares the **intent** of the interface and the backend of each target renders it. What a widget set lacks becomes a Kof abstraction (or a maintainer decision), never imported syntax from a foreign stack

Kof is a general-purpose, compiled programming language with multiple backends.

---

## Distribution

Kof is not "a Java project you assemble" — it is **a language you
install**. The official package includes the compiler, CLI, runtime, stdlib,
tooling, editor support and a bundled OpenJDK 25 (Temurin 25, `release.yml`
with 2 jobs — `test-and-bump` → `package-and-release` — per platform
linux-x86_64/macos-arm64/windows-x86_64, `scripts/package.sh` PASS). The
installation does not depend on external Java, `JAVA_HOME` or SDKMAN. Build
`mvn test` 3225 (2762+50+7+406), golden 16/16, integration 9/9.

The user who installs Kof gets everything they need to develop,
compile, run and use the language tooling (26 commands:
`kof build, run, serve, check, test, script, repl, c, fmt, config, bench, profile, inspect, decompile, translate, compare, migrate, debug, info, lsp, install, deps, editor, init, new, version`).

---

## Kof + LLM

> **Human First, LLM Friendly by Consequence.**

Kof is not designed "for AI". The consistency of the design (less ceremony,
fewer files, fewer artificial abstractions, less configuration, more
intention) makes humans and LLMs understand the same language in the same
way. What is explicit to a person is explicit to a model — and vice versa.

The `training/` directory is an official part of that strategy: a structured
corpus so that models produce idiomatic Kof.

---

## Future Vision

Kof should evolve into a platform where:

1. **Backend APIs** are built in the language, not in frameworks
2. **Persistence** is part of the language, not of an ORM
3. **Security** is part of the language, not of a framework
4. **Observability** is part of the language, not of libraries
5. **Concurrency** is part of the language, not of APIs

The goal is that the complexity that today lives in Spring, Hibernate, and dozens of other libraries be solved by the Kof compiler and runtime.

And, at the limit, **Kof written in Kof** — not as a demonstration, but as
real architectural evolution.
