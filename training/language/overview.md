# Kof Overview

Kof is a compiled, statically-typed, object-oriented programming language targeting JVM, Native (x86_64, riscv64, aarch64) and KofJS (ES Modules), plus Android (Fase 1, APK via backend JVM), KofScript and KofC.

**Version:** 0.3.22-beta (Sep 2026) — 810 tests (793 kof-compiler + 8 kof-script + 5 kof-c-compiler + 4 kof-cli, 0 failures).

## Key Characteristics

- **Compiled** — the compiler emits JVM bytecode (via ASM), a native ELF binary (x86_64/riscv64/aarch64), or ES Modules; there is no interpreter
- **Statically typed** — type errors caught at compile time; null safety `String?` + narrowing `if (x != null)` since 0.2.6-beta
- **Multi-target** — same code runs on JVM, Native, JS, Native.risc, Native.arm, plus KofScript (JIT in-memory) and KofC (C subset → native)
- **Intent-oriented** — not a formal paradigm, but object orientation taken to
  its extreme: code expresses *what* should happen; the platform (language +
  compiler + runtime + stdlib) decides *how*, per target. The chain is
  `intent → Kof → compiler → backend`. Mechanisms never leak into user code:
  `spawn f()` (not Thread), `app.get(...)` (not a servlet container),
  `Window`/`Button("+1", () -> ...)` (not WebView), `json.decode<User>(body)`
  (not a manual parser),   `Palette.red` (not hex). Gaps are reported at
  compile time with codes (`HTTP002`, `DB001`, `SCHED001`) — never silently.
  See `docs/philosophy.md`.
- **Minimal boilerplate** — intent over ceremony (records, primary constructors, top-level functions)
- **Memory managed** — free-list (thread-safe, futex) + `kof_gc_collect` mark-sweep conservador (Native, 27/08); auto-GC desligado — GC mark-sweep automático pendente
- **No `fun` keyword** — functions are declared by name (`main()`, `String f()`, `f(): String`)

## Compilation Pipeline

```
Source (.kf / .ks / .c)
    ↓
Lexer → Tokens
    ↓
Parser → AST (PatternExpr, NullableType)
    ↓
Semantic Analysis → Typed AST (isAssignable com Nullable, record destructuring)
    ↓
Kof IR (backend-agnostic, KofOperation)
    ↓
┌─────────┬──────────┬──────┬─────────┬────────┐
│  JVM    │ Native   │ JS   │ KofScript│ KofC  │
│  (ASM)  │ x86_64   │ ES   │ JIT      │ C→ELF │
│         │ riscv64* │      │ let/top  │       │
│         │ aarch64* │      │ level    │       │
└─────────┴──────────┴──────┴─────────┴────────┘
 * riscv64 real (02/09, asm puro, qemu); aarch64 placeholder
```

## Current Features (0.3.22-beta)

| Feature | JVM | Native | JS | Notes |
|---------|-----|--------|----|-------|
| Classes, records, interfaces, inheritance, virtual dispatch | ✅ | ✅ | ✅ | super = SUP001 no Native |
| Constructors (`constructor(...)`, primary `class X(...)`) | ✅ | ✅ | ✅ | desde 0.0.5 |
| Functions (all forms, no `fun`, expression body) | ✅ | ✅ | ✅ | |
| Enums (`enum Color { Red }` + values/valueOf/name + exhaustive switch SEM031) | ✅ | ✅ | ✅ | 3 targets |
| Lambdas com captura mutável (Box0) | ✅ | ✅ | ✅ | desde 0.2.6-beta |
| If-expressions `var x = if (c) a else b` | ✅ | ✅ | ✅ | |
| `List<T>` + `listOf` + `map/filter/reduce` | ✅ | ✅ | ✅ | higher-order 27/08 |
| `Map<K,V>` + `mapOf` (put/get/remove/contains/size/keys/values/clear/isEmpty) | ✅ | ✅ | ✅ | desde 0.1.0 |
| `Set<T>` + `setOf` (add/contains/remove/size/clear/isEmpty) | ✅ | ✅ | ✅ | desde 0.1.0 |
| `Box<T>` generics com `T` primitivo | ✅ | ✅ | ✅ | fix substituteTypeVariable 25/08 |
| Null safety `String?` / `Int?` + narrowing `if (x != null)` | ✅ | ✅ | ✅ | desde 0.2.6-beta |
| Pattern matching `case String s` + `instanceof`/`as` | ✅ | ✅ | ✅ | desde 0.2.6-beta |
| Record destructuring `case Point(x, y)` | ✅ | ✅ | ✅ | Parser fieldVars |
| Concorrência: `spawn` / `Handle<T>` / `await` | ✅ | ✅ (pthread, 31/08) | ✅ (sequencial) | CONC001 fechado; JS CONC003 parcial |
| Strings (`+`, `==`, indexOf, trim, split, ...) | ✅ | ✅ | ✅ | |
| Arrays (`new Int[n]`, `arr[i]`, `.length`) | ✅ | ✅ | ✅ | |
| Exceptions `throw "msg"` / try/catch/finally | ✅ | ✅ | ✅ | Native unwinding |
| Generics (erasure) | ✅ | ✅ | ✅ | |
| JSON `json.encode` / `json.decode<T>` (objetos/records/arrays, FP) | ✅ | ✅ | ✅ | JSN001/002/003 fechados 31/08 |
| kof.io: `readFile`, `writeFile`, `readLine`, `File/Path/Directory` | ✅ | ✅ | ✅ | |
| kof.time: `now()` / `sleep()` | ✅ | ✅ | ✅ | |
| kof.http: `http.get/post/put/delete/patch/options/status` + `timeout/retry/circuit` | ✅ | HTTP002 | ✅ | JS via Java HttpClient 27/08; retry/circuit 30/08 |
| kof.cache: `cache.get/set/set_ttl/ttl/delete/clear` | ✅ | ✅ | ✅ | ConcurrentHashMap/Js Map |
| switch, instanceof, `as` | ✅ | ✅ | ✅ | |
| Web server (`web.app()` rotas/middleware/`status`/`headerSet` + `listenSecure` TLS + `app.ws` + `app.sse`) | ✅ | WEB001 | — | ws/sse 30/08 |
| kof.validation (13 predicados) | ✅ | ✅ | ✅ | |
| kof.security (passwords/crypto/jwt/secrets/auth + rateLimit/sessions/apiKeys) | ✅ | ✅ | ✅ | |
| kof.observability (health/readiness/liveness/counter/increment/gauge/requestId) | ✅ | ✅ | ✅ | |
| kof.db + SQLite nativo + MySQL handshake | ✅ | ✅ (MySQL auth scramble SHA-1 done) | DB001 | |
| KofScript `let` top-level + repl --watch --inspect | ✅ | ✅ | ✅ | KofScriptGlobals |
| KofC C subset → ELF x86_64 | — | ✅ | — | nativo-only |

## Planned / Unavailable (0.3.22-beta)

| Feature | Status |
|---------|--------|
| `Option<T>` genérico | Planned — use `String?` |
| `Array literals {1, 2, 3}` | Unavailable — use `new Int[n]` / `listOf` |
| MySQL query/prepared completo no Native | In progress (handshake done 27/08) |
| RISC-V/ARM codegen real | Placeholder (target separation done, as/ld+qemu) |
| Scheduler `every`/`at` no Native | SCHED001 (JVM/JS ✅) |
| GC mark-sweep automático no Native | Pendente (free-list + `kof_gc_collect` manuais; auto-GC desligado) |
| HTTP/2 no `kof.http` | Planned (HTTP002 no Native) |
| Web stack no Native/JS (`web.app`) | WEB001 (JVM ✅) |

## What Kof Is NOT

- Java with different syntax
- A transpiler to Java
- A Spring clone
- A framework
- An interpreter
- A VM
