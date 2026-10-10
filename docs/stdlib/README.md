[English](README.md) | [Português](README.pt_BR.md)

# Standard Library — Proposal

**Last updated:** September 16, 2026
> **Updated (0.5.0-beta):** the stdlib is largely implemented on the 3
> targets (JVM/Native/JS) — `kof.core`, `kof.collections`, `kof.io`,
> `kof.time`, `kof.json` (FP + full arrays on Native, 31/08),
> `kof.http` (client + resilience JVM+JS), `kof.web` (`web.app()` +
> WebSocket/SSE JVM), `kof.db`/`kof.orm`, `kof.security`, `kof.config`,
> `kof.logging`, `kof.observability`, `kof.mq`, `kof.cache`,
> `kof.scheduler`, `kof.validation`, `kof.test`, `kof.ui` (Color/Theme/
> Palette + widgets). **This page is the original plan; the current state, the
> module matrix and the architecture live in `docs/stdlib/stdlib.md`** (source of
> reference). The table below is the original plan, with the current status.

**Status:** largely implemented (0.5.0-beta; see `docs/stdlib/stdlib.md`)

---

## Philosophy

> If it is essential to any program, it belongs to the platform.

The standard library must be:
- Minimal
- Coherent
- Without external dependencies
- Available on all backends

---

## Proposed Modules

### kof.core

Basic types and operations.

```
String.length()
String.charAt(index)
String.substring(start, end)
String.concat(other)
String.equals(other)
String.contains(other)
String.startsWith(prefix)
String.endsWith(suffix)
String.trim()
String.toLowerCase()
String.toUpperCase()
String.indexOf(other)
String.split(delimiter)
```

### kof.io

Basic input/output.

```
println(value)
print(value)
input() → String
```

### kof.time

Date and time.

```
time.now()                 // epoch millis
time.todayIso()            // "2026-10-04"
time.addDays(iso, 7)       // ISO in, ISO out
time.diffDays(a, b)
time.sleep(ms)
```

### kof.json

JSON serialization.

```
json.encode(obj)
json.decode(str, Type)
```

### kof.sql

Database access (future).

```
users.find(1)
users.where(User.age > 18)
```

### kof.http

HTTP client (future).

```
http.get("https://api.example.com/users")
http.post("https://api.example.com/users", data)
```

### kof.concurrent

Concurrency — `spawn` implemented (JVM, virtual threads).

```kof
spawn processarFila()
spawn { ... }
```

`await`/task result: implemented — `val r = spawn f()` + `await r` (3 targets). See `docs/language-reference/concurrency.md`.

### kof.test

Tests — `assert(cond[, "msg"])` + `kof test <file.kf|dir>` implemented.

```kof
main() {
    assert(2 + 2 == 4)
}
```

Structured suite (`test "soma" { ... }`): implemented on the 3 targets (`StructuredTestE2ETest` 11/11).

---

## Priority

| Module | Priority | Status |
|--------|-----------|--------|
| kof.core | High | Implemented (String ops, println, types) |
| kof.io | High | Implemented (File/Path/Directory) |
| kof.web | High | Implemented (`web.app()` + ws/sse) |
| kof.http | High | Implemented (`kof serve` + client) |
| kof.json | Medium | Implemented (`json.encode`/`decode`) |
| kof.time | Medium | Implemented (`now()`, `sleep`, `interval`) |
| kof.concurrent | High | Implemented (`spawn`/`await`/`channel<T>`, 3 targets) |
| kof.test | High | Implemented (`assert` + `test "name"` + `kof test`) |
| kof.sql | High | Implemented as `kof.db`/`kof.orm` (MySQL wire x86-64 real, ORM 13 faces 22/09) — module name in the plan |

---

## Principles

1. **Minimum necessary** — do not create libraries nobody uses
2. **Coherence** — APIs must follow consistent patterns
3. **Backend-agnostic** — same API on JVM and Native
4. **No dependencies** — the standard library does not depend on external libraries
5. **Evolution** — APIs can be extended without breaking existing code
