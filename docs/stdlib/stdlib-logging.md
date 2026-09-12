# stdlib log — Logging Nativo do Kof

**Última atualização:** 3 de setembro de 2026
**Versão:** 0.2.6-beta  (840 testes, 03/09)
**Status:** implementado (Fase 4 do plano de independência do Spring) — JVM+Native (Native asm UTC `kof_log_*`, 27/08); JS `console.*` (LOG001 fechado 01/09)

---

## 1. Filosofia

> Logging é parte da plataforma Kof, não de um framework (SLF4J/Logback são
> interoperabilidade, nunca requisito).

## 2. API

```kof
log.debug("detail")
log.info("request started")
log.warn("slow response: " + ms)
log.error("failed: " + message)
```

Cada chamada aceita uma `String` (concatene com `+`). Formato da linha:

```
2026-08-23 12:09:22.715 INFO hello from kof
```

- `info`/`debug` → stdout
- `warn`/`error` → stderr

## 3. Níveis

Controlados pela variável de ambiente `KOF_LOG_LEVEL`
(`debug < info < warn < error < off`; default `info`).

| Nível | Mensagens exibidas |
|-------|--------------------|
| `debug` | debug, info, warn, error |
| `info` (default) | info, warn, error |
| `warn` | warn, error |
| `error` | error |
| `off` | nenhuma |

```bash
KOF_LOG_LEVEL=debug kof run app.kf
KOF_LOG_LEVEL=off kof run app.kf
```

## 4. Contexto web

Funciona dentro de handlers da stack web (mesmo runtime gerado):

```kof
app.get("/users") {
    log.info("users listed")
    return "[]"
}
```

## 5. Targets (0.2.6-beta)

| Target | Estado | Notas |
|--------|--------|-------|
| JVM | ✅ completo | `KofRuntime` gerado, JSON + correlation ID |
| Native x86_64 | ✅ completo (asm, 27/08) | `kof_log_*` asm próprio (data civil Hinnant, env scan), timestamp UTC; `KOF_LOG_JSON` sem efeito ainda |
| Native riscv64/aarch64 | ✅/placeholder | riscv64 `li a7`; aarch64 placeholder |
| JS | ✅ 01/09 (LOG001 fechado) | `kofLog*` console.* com `KOF_LOG_LEVEL`; warn→console.warn, error→console.error |

## 6. Testes

`KofLogE2ETest` 11 (JVM + JS, 01/09) + `NativeLogE2ETest` 7 (Native asm, 0.2.6-beta) —
nível default, debug visível com `KOF_LOG_LEVEL=debug`, supressão em
`error`, `off` silencioso, warn no stderr, log dentro de handler web, JSON
estruturado + correlation ID (JVM) e JS via `console.*`.

## 7. Arquitetura

```
Kof source (.kf) → KofLog (tabela compile-time)
   → SemanticAnalyzer (tipos) → CompilerDriver (KofCall kof_log_*)
   → JvmRuntime (gerado): nível + timestamp + stream
   → NativeRuntime (asm): kof_log_* + env scan + Hinnant date (NativeRuntime.java:1)
   → JsBackend (kof-runtime.mjs): kofLog* console.* + KOF_LOG_LEVEL
```

Evolução planejada (Fase 4 completa): structured logging JSON `KOF_LOG_JSON` no Native, correlation ID por request, contexto por tarefa.