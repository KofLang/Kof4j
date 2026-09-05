# Visão Geral do Kof

Kof é uma linguagem compilada, estaticamente tipada e orientada a objetos, com alvos JVM, Native (x86_64, riscv64, aarch64) e KofJS (ES Modules), além de Android (Fase 1, APK via backend JVM), KofScript e KofC.

**Versão:** 0.2.6-beta (02 set 2026) — 810 testes (793 kof-compiler + 8 kof-script + 5 kof-c-compiler + 4 kof-cli, 0 falhas).

## Características Principais

- **Compilada** — o compilador emite bytecode JVM (via ASM), um binário ELF nativo (x86_64/riscv64/aarch64) ou ES Modules; não há interpretador
- **Estaticamente tipada** — erros de tipo detectados em compile-time; null safety `String?` + narrowing `if (x != null)` desde 0.2.6-beta
- **Multi-target** — o mesmo código roda em JVM, Native, JS, Native.risc, Native.arm, além de KofScript (JIT em memória) e KofC (subconjunto C → nativo)
- **Orientada a intenção** — não é um paradigma formal, mas a orientação a objetos
  levada ao extremo: o código expressa *o que* deve acontecer; a plataforma
  (linguagem + compilador + runtime + stdlib) decide *como*, por target. A cadeia
  é `intenção → Kof → compilador → backend`. Mecanismos nunca vazam para o código
  do usuário: `spawn f()` (não Thread), `app.get(...)` (não um container servlet),
  `Window`/`Button("+1", () -> ...)` (não WebView), `json.decode<User>(body)`
  (não um parser manual), `Palette.red` (não hex). Gaps são reportados em
  compile-time com códigos (`HTTP002`, `DB001`, `SCHED001`) — nunca silenciosamente.
  Ver `docs/philosophy.md`.
- **Boilerplate mínimo** — intenção sobre cerimônia (records, construtores primários, funções top-level)
- **Memória gerenciada** — free-list (thread-safe, futex) + `kof_gc_collect` mark-sweep conservador (Native, 27/08); auto-GC desligado — GC mark-sweep automático pendente
- **Sem palavra-chave `fun`** — funções são declaradas pelo nome (`main()`, `String f()`, `f(): String`)

## Pipeline de Compilação

```
Fonte (.kf / .ks / .c)
    ↓
Lexer → Tokens
    ↓
Parser → AST (PatternExpr, NullableType)
    ↓
Análise Semântica → AST tipado (isAssignable com Nullable, destructuring de record)
    ↓
Kof IR (agnóstico de backend, KofOperation)
    ↓
┌─────────┬──────────┬──────┬─────────┬────────┐
│  JVM    │ Native   │ JS   │ KofScript│ KofC  │
│  (ASM)  │ x86_64   │ ES   │ JIT      │ C→ELF │
│         │ riscv64* │      │ let/top  │       │
│         │ aarch64* │      │ level    │       │
└─────────┴──────────┴──────┴─────────┴────────┘
 * riscv64 real (02/09, asm puro, qemu); aarch64 placeholder
```

## Funcionalidades Atuais (0.2.6-beta)

| Funcionalidade | JVM | Native | JS | Notas |
|---------|-----|--------|----|-------|
| Classes, records, interfaces, herança, dispatch virtual | ✅ | ✅ | ✅ | super = SUP001 no Native |
| Construtores (`constructor(...)`, primário `class X(...)`) | ✅ | ✅ | ✅ | desde 0.0.5 |
| Funções (todas as formas, sem `fun`, corpo de expressão) | ✅ | ✅ | ✅ | |
| Enums (`enum Color { Red }` + values/valueOf/name + switch exaustivo SEM031) | ✅ | ✅ | ✅ | 3 targets |
| Lambdas com captura mutável (Box0) | ✅ | ✅ | ✅ | 0.2.6-beta |
| If-expressões `var x = if (c) a else b` | ✅ | ✅ | ✅ | |
| `List<T>` + `listOf` + `map/filter/reduce` | ✅ | ✅ | ✅ | higher-order 27/08 |
| `Map<K,V>` + `mapOf` (put/get/remove/contains/size/keys/values/clear/isEmpty) | ✅ | ✅ | ✅ | desde 0.1.0 |
| `Set<T>` + `setOf` (add/contains/remove/size/clear/isEmpty) | ✅ | ✅ | ✅ | desde 0.1.0 |
| `Box<T>` generics com `T` primitivo | ✅ | ✅ | ✅ | fix substituteTypeVariable 25/08 |
| Null safety `String?` / `Int?` + narrowing `if (x != null)` | ✅ | ✅ | ✅ | 0.2.6-beta |
| Pattern matching `case String s` + `instanceof`/`as` | ✅ | ✅ | ✅ | 0.2.6-beta |
| Record destructuring `case Point(x, y)` | ✅ | ✅ | ✅ | Parser fieldVars |
| Concorrência: `spawn` / `Handle<T>` / `await` | ✅ | ✅ (pthread, 31/08) | ✅ (sequencial) | CONC001 fechado; JS CONC003 parcial |
| Strings (`+`, `==`, indexOf, trim, split, ...) | ✅ | ✅ | ✅ | |
| Arrays (`new Int[n]`, `arr[i]`, `.length`) | ✅ | ✅ | ✅ | |
| Exceções `throw "msg"` / try/catch/finally | ✅ | ✅ | ✅ | unwinding Native |
| Generics (erasure) | ✅ | ✅ | ✅ | |
| JSON `json.encode` / `json.decode<T>` (objetos/records/arrays, FP) | ✅ | ✅ | ✅ | JSN001/002/003 fechados 31/08 |
| kof.io: `readFile`, `writeFile`, `readLine`, `File/Path/Directory` | ✅ | ✅ | ✅ | |
| kof.time: `now()` / `sleep()` | ✅ | ✅ | ✅ | |
| kof.http: `http.get/post/put/delete/patch/options/status` + `timeout/retry/circuit` | ✅ | HTTP002 | ✅ | JS via Java HttpClient 27/08; retry/circuit 30/08 |
| kof.cache: `cache.get/set/set_ttl/ttl/delete/clear` | ✅ | ✅ | ✅ | ConcurrentHashMap/Js Map |
| switch, instanceof, `as` | ✅ | ✅ | ✅ | |
| Servidor web (`web.app()` rotas/middleware/`status`/`headerSet` + `listenSecure` TLS + `app.ws` + `app.sse`) | ✅ | WEB001 | — | ws/sse 30/08 |
| kof.validation (13 predicados) | ✅ | ✅ | ✅ | |
| kof.security (passwords/crypto/jwt/secrets/auth + rateLimit/sessions/apiKeys) | ✅ | ✅ | ✅ | |
| kof.observability (health/readiness/liveness/counter/increment/gauge/requestId) | ✅ | ✅ | ✅ | |
| kof.db + SQLite nativo + handshake MySQL | ✅ | ✅ (auth scramble SHA-1 do MySQL feito) | DB001 | |
| KofScript `let` top-level + repl --watch --inspect | ✅ | ✅ | ✅ | KofScriptGlobals |
| KofC subconjunto C → ELF x86_64 | — | ✅ | — | nativo-only |

## Planejado / Indisponível (0.2.6-beta)

| Funcionalidade | Status |
|---------|--------|
| `Option<T>` genérico | Planejado — use `String?` |
| `Array literals {1, 2, 3}` | Indisponível — use `new Int[n]` / `listOf` |
| MySQL query/prepared completo no Native | Em andamento (handshake feito 27/08) |
| RISC-V/ARM codegen real | Placeholder (separação de target feita, as/ld+qemu) |
| Scheduler `every`/`at` no Native | SCHED001 (JVM/JS ✅) |
| GC mark-sweep automático no Native | Pendente (free-list + `kof_gc_collect` manuais; auto-GC desligado) |
| HTTP/2 no `kof.http` | Planejado (HTTP002 no Native) |
| Web stack no Native/JS (`web.app`) | WEB001 (JVM ✅) |

## O que o Kof NÃO é

- Java com outra sintaxe
- Um transpilador para Java
- Um clone do Spring
- Um framework
- Um interpretador
- Uma VM