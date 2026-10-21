[English](stdlib-logging.md) | [Português](stdlib-logging.pt_BR.md)

# stdlib log — Kof Native Logging

**Last updated:** September 3, 2026
**Version:** 0.6.7 (test counts in `docs/status.md`)
**Status:** implemented (Phase 4 of the Spring independence plan) — JVM+Native (Native asm UTC `kof_log_*`, 27/08); JS `console.*` (LOG001 closed 01/09)

---

## 1. Philosophy

> Logging is part of the Kof platform, not of a framework (SLF4J/Logback are
> interoperability, never a requirement).

## 2. API

```kof
log.debug("detail")
log.info("request started")
log.warn("slow response: " + ms)
log.error("failed: " + message)
```

Each call accepts a `String` (concatenate with `+`). Line format:

```
2026-08-23 12:09:22.715 INFO hello from kof
```

- `info`/`debug` → stdout
- `warn`/`error` → stderr

## 3. Levels

Controlled by the `KOF_LOG_LEVEL` environment variable
(`debug < info < warn < error < off`; default `info`).

| Level | Messages displayed |
|-------|--------------------|
| `debug` | debug, info, warn, error |
| `info` (default) | info, warn, error |
| `warn` | warn, error |
| `error` | error |
| `off` | none |

```bash
KOF_LOG_LEVEL=debug kof run app.kf
KOF_LOG_LEVEL=off kof run app.kf
```

## 4. Web context

Works inside handlers of the web stack (same generated runtime):

```kof
app.get("/users") {
    log.info("users listed")
    return "[]"
}
```

## 5. Targets (0.6.0)

| Target | Status | Notes |
|--------|--------|-------|
| JVM | ✅ complete | `KofRuntime` generated, JSON + correlation ID |
| Native x86_64 | ✅ complete (asm, 27/08) | own `kof_log_*` asm (Hinnant civil date, env scan), UTC timestamp; `KOF_LOG_JSON` has no effect yet |
| Native riscv64/aarch64 | ✅ riscv64 / inherits on aarch64 | riscv64 `li a7`; aarch64 inherits the riscv `kof_log_*` runtime via the line-by-line translator (no dedicated aarch64 log test — the general aarch64 core is complete since 03/09; the old "aarch64 placeholder" was 0.2.6-era) |
| JS | ✅ 01/09 (LOG001 closed) | `kofLog*` console.* with `KOF_LOG_LEVEL`; warn→console.warn, error→console.error |

## 6. Tests

`KofLogE2ETest` 11 (JVM + JS, 01/09) + `NativeLogE2ETest` 7 (Native asm, since 0.2.6-beta) —
default level, debug visible with `KOF_LOG_LEVEL=debug`, suppression at
`error`, silent `off`, warn on stderr, log inside a web handler, structured JSON
+ correlation ID (JVM) and JS via `console.*`.

## 7. Architecture

```
Kof source (.kf) → KofLog (compile-time table)
   → SemanticAnalyzer (types) → CompilerDriver (KofCall kof_log_*)
   → JvmRuntime (generated): level + timestamp + stream
   → NativeRuntime (asm): kof_log_* + env scan + Hinnant date (NativeRuntime.java:1)
   → JsBackend (kof-runtime.mjs): kofLog* console.* + KOF_LOG_LEVEL
```

Planned evolution (complete Phase 4): structured JSON logging `KOF_LOG_JSON` in Native, correlation ID per request, context per task.
