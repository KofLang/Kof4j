# Kof — Roadmap de Longo Prazo

**Última atualização:** 2 de setembro de 2026
**Versão:** 0.2.6-beta

---

## Filosofia

Kof deve simplificar radicalmente o desenvolvimento moderno sem sacrificar poder, performance, segurança ou interoperabilidade.

Princípios:

- abstrair complexidade recorrente;
- manter o código extremamente curto e legível;
- oferecer APIs nativas da linguagem/runtime;
- manter compatibilidade com o ecossistema Java existente;
- evitar reinventar bibliotecas Java apenas por estética;
- permitir que Kof ofereça uma experiência moderna sem obrigar o usuário a depender de frameworks externos;
- colocar complexidade na implementação/runtime/compiler, e não no código da aplicação;
- preservar liberdade arquitetural;
- permitir monólitos, modularização e posteriormente microserviços sem reescrever a aplicação inteira.

---

## 1. Targets da Plataforma

### KofAndroid — Android

Kof compilado para aplicativos Android (APK/AAB). Design completo em
[docs/targets/KOFANDROID.md](targets/KOFANDROID.md).

Objetivos:
- mesmo código, mesma intenção: `Window(...)` abre um app de verdade;
- reuso do backend JVM (bytecode → dex) — não há codegen alternativo;
- `kof.ui` via WebView host sintetizado (mesma camada KofJS do desktop);
- interop direta com `android.*` via ExternalClasspath
  (`extends Activity`, `super.onCreate`, annotations androidx);
- gaps honestos por compile-time (`AND001..003`).

Estado atual: 🟡 Fase 1 implementada — `kof build --target android` gera
projeto Maven (zero Java/Kotlin/Gradle) com host Activity EM KOF
(`android-host.kf`) compilada pelo próprio frontend; pipeline
d8/aapt2/apksigner via pom sem dependências.

### Kof4J — JVM

Kof compilado para JVM/bytecode.

Objetivos:
- máxima compatibilidade com Java;
- acesso a bibliotecas Java;
- compatibilidade com Maven/ecossistema existente;
- execução como JAR;
- possibilidade de utilizar frameworks legados como Spring, Hibernate etc.;
- backend principal durante a consolidação inicial.

Estado atual: ✅ estável (JVM V21, ASM, virtual threads, 819 testes 02/09;
web stack nativa com WebSocket/SSE, limites/contadores e `kof.http`
retry/circuit — 30/08-04/09)

### KofNative — Binário Nativo

Kof compilado diretamente para código nativo/binário.

Objetivos:
- ELF/PE/Mach-O conforme plataforma;
- baixo consumo;
- startup extremamente rápido;
- possibilidade de servidores sem JVM;
- runtime Kof nativo;
- reutilização da mesma semântica da linguagem;
- mesma aplicação podendo ser compilada para JVM ou Native.

Estado atual: ✅ estável x86_64 (free-list `kof_free_head` com reuso mmap; GC
mark-sweep pendente — auto-GC desativado após hang, memória devolvida só no
`munmap` fallback; `spawn`/`await` via `pthread_create` + trampoline +
`pthread_join` com allocator thread-safe (futex) — 31/08; FP real em XMM —
FLT001; JSON objetos/records + arrays FP — JSN001/002/003; SQLite nativo `.so`
direto; MySQL wire protocol WIP) + `native.risc` (riscv64: codegen real 02/09 — asm puro + qemu, NATIVE002
parcial) + `native.arm` (aarch64: toolchain + qemu; codegen ainda placeholder)

### KofJS — Web

Kof executando no lado servidor/compilando para aplicações web.

KofJS NÃO deve ser tratado simplesmente como "Kof que vira JavaScript".

A visão é gerar frontend moderno de forma declarativa e minimalista:

```kof
page Home {
    column {
        text("Olá")
        button("Entrar") {
            login()
        }
    }
}
```

A intenção é semelhante à filosofia do Flutter:
- UI declarativa;
- componentes;
- composição;
- estado;
- eventos;
- layouts;
- pouca verbosidade;
- geração otimizada de HTML/CSS/JS.

Estado atual: 🟡 alpha — pipeline `.kf → Kof IR → KofJS → .mjs` funcional com
execução na engine JS embarcada do próprio Kof (sem Node.js). Classes,
herança, List `map/filter/reduce`, String API, JSON, exceções, pattern matching
`case String s` + `Point(x,y)`, `String?` básica, `kof.time`/`kof.io`/`kof.http` (via `Java HttpClient` interop + fetch fallback; retry/circuit em paridade com o JVM — 30/08; scheduler via `setInterval` — 27/08; `spawn`/`await`/`channel<T>()` com concorrência real via async/await/Promise — CONC003 fechado 03/09) e `kof run
--target=js` funcionam. A plataforma web (HTML/CSS/JS, browser) é a próxima
fase. Ver: `docs/targets/KOFJS.md`.

### KofScript — Execução Direta

Runtime para executar código Kof diretamente.

Comando planejado:

```
kof run arquivo.kf
```

A implementação interna poderá evoluir para interpretação, compilação incremental, JIT ou execução híbrida, mas a decisão será tomada posteriormente com base em benchmarks.

Estado atual: ✅ implementado (0.2.6-beta): `kof script app.kf [--watch]` + `kof repl` (top-level `let` → `KofScriptGlobals`; Windows SIGPIPE fix). `KofCcompiler` (`kof c`) compila C subset → ELF x86_64 nativo (`int` globals, `void` funcs, `if`/`while`/`*(int*)`/`&`).

---

## 2. Princípio Multi-Target

A linguagem deve possuir uma semântica única:

```
Source
  ↓
Lexer
  ↓
Parser
  ↓
AST
  ↓
Type System
  ↓
Symbol Resolution
  ↓
Semantic Model
  ↓
Kof IR
  ├── Kof4J Backend
  ├── KofNative Backend
  ├── KofJS Backend
  └── KofScript Runtime
```

A Kof IR deve permanecer independente de JVM, ASM, JavaScript ou código nativo.

Backends são responsáveis por transformar a representação semântica em sua plataforma.

Estado atual: ✅ arquitetura definida e parcialmente implementada

---

## 3. Kof como Plataforma de Backend

A visão de longo prazo é permitir construir backends modernos sem Spring.

Não reimplementar Spring. Em vez disso, transformar capacidades recorrentes em primitivas do Kof Runtime.

Objetivos futuros:
- HTTP / REST / WebSocket / SSE (WebSocket/SSE + hardening JVM concluídos 04/09; JS/Native follow-up);
- HTTP client;
- JSON;
- RPC;
- eventos / filas / pub/sub;
- concorrência / async;
- cache;
- configuração;
- observabilidade / logging / métricas / tracing;
- health checks / graceful shutdown;
- validation / serialization / scheduling.

Exemplo conceitual:

```kof
api "/users" {
    get "/{id}" {
        return User.find(id)
    }
    post "/" {
        return User.create(input())
    }
}
```

Estado atual: 🟡 parcial — HTTP/rotas (`kof.web` + TLS `listenSecure` +
**WebSocket `app.ws`** + **SSE `sse.*`** — 30/08, JVM; hardening
`app.configure`/`app.stats` — 04/09), JSON (completo nos 3
targets, 31/08), configuração (`kof.config` asm Native), logging (`kof.log`
asm Native), segurança (`kof.security` + G9), **cache (`kof.cache`, 3
targets — 30/08)**, **`kof.http` retry/circuit breaker (JVM+JS — 30/08)**,
concorrência (`spawn` + `await`/`Handle<T>` — JVM virtual threads, Native
pthread 31/08, JS sequencial), `List map/filter/reduce`, `Box<T>`, pattern
matching e `String?` implementados (0.2.6-beta).
Faltam: HTTP client no Native (HTTP002), RPC (gRPC — ver abaixo),
tracing (OpenTelemetry), web no Native/JS (WEB002/WEB001),
MySQL nativo completo, RISC/ARM codegen, GC mark-sweep.
(kof.mq pub/sub + queue = 3 targets — MQ001 fechado 01/09)
Ver `docs/plan-spring-independence.md` (Fases 5-14).

**gRPC no `kof.web` (novo, 31/08 — planejado)**: comunicação gRPC como
primeira classe na plataforma web — `app.grpc { service ... }` com stubs
gerados a partir de `.proto`, server streaming + unary sobre HTTP/2 no JVM
(`io.grpc` via `kof.web`), e client `grpc.call(endpoint, method, msg)`.
Escopo: Fase web (mesma família de `app.ws`/`sse.*`); codegen `.proto` → IR
Kof; parity JVM primeiro, Native/JS depois.

### Concorrência — fila residual (0.2.6-beta, 31/08)

Estado 0.2.6-beta: concorrência real **JVM** (virtual threads) + **Native**
(pthread, CONC001 fechado 31/08: spawn/await + `done`/`poll`/`cancel`/
`cancelled`/`selectAny` — cancel cooperativo por TID, selectAny por polling
1ms) + **JS** ✅ 03/09 (CONC003 fechado — stmt/expr/cancel/selectAny com
async/await/Promise reais). ⚠️ Bug pré-existente separado: `spawn→await→spawn`
corrompe a pilha da main (SIGSEGV no próximo `pthread_create`); reproduz sem
o feature de cancel/select (suspeito: `pthread_join` no `kof_await`).

| Item | Descrição | Prioridade |
|------|-----------|------------|
| ~~Unwrap de `ExecutionException`~~ | ✅ 31/08 — `kof_await` re-lança a causa original (JVM) | — |
| ~~`await` com timeout~~ | ✅ 31/08 — `awaitTimeout(r, ms)`: valor no prazo, exceção capturável via `try/catch` no estouro (JVM `Future.get(ms)` + Native polling 1ms com deadline; JS sequencial = paridade) | — |
| ~~Cancelamento~~ | ✅ 31/08 — `cancel(r)`/`cancelled()` cooperativo via flag no handle (JVM + Native por TID) | — |
| ~~Espera múltipla~~ | ✅ 31/08 — `selectAny(h1, h2, ...)` → primeiro handle pronto (JVM + Native + JS) | — |
| ~~`done`/`poll`~~ | ✅ 31/08 — não-bloqueantes sobre o handle (JVM + Native) | — |
| ~~Port Native~~ | ✅ 31/08 — `pthread_create` + trampoline + `pthread_join` + allocator thread-safe (futex); join implícito (CONC001 fechado) | — |
| ~~Port JS~~ | ✅ 03/09 — spawn sobre Promise, await nativo via microtask (CONC003 fechado) | — |
| ~~Scheduler/cron~~ | ✅ 31/08 — `every`/`at` JVM (`ScheduledExecutor`) + JS (`setInterval`) + **Native SCHED001** (thread por job, `usleep` ms→us + flag `active`, `cancel(id)` cooperativo) | — |
| ~~Canais tipados~~ | ✅ 31/08, bloqueio real no JS 03/09 — `channel<Int>()` com `send`/`receive` (JVM `LinkedBlockingQueue` bloqueante + Native FIFO futex + JS fila de resolvers pendentes) | — |

Critério de "100%": os três targets executando os mesmos programas
concorrentes com golden diff vazio (mesmo padrão da métrica 1 do plano).

### Linguagem — fila residual (P1/P2, 0.2.6-beta)

| Item | Status | Plano |
|------|--------|-------|
| pattern matching | ✅ 0.2.6-beta — `switch (x) { case String s: ... }` + `case Point(x,y)` em JVM/Native/JS | guards e destructuring aninhado pendentes |
| null safety | ✅ 0.2.6-beta — `String?` básica com `?`-check em compile-time; **sem Option no core** | checks avançados pendentes |
| higher-order em coleções | ✅ 0.2.6-beta — `List map/filter/reduce` em JVM/Native/JS | `Map/Set` já ✅ 0.1.0 |
| módulos multi-arquivo | ✅ 0.2.6-beta — `import a.b.C` file handling fix (`CompilerDriver.java:243`) para projetos grandes (`a/b/C.kf`) | semântica unificada de visibilidade/import residual |

---

## 4. Segurança Nativa

Camada de segurança própria do Kof, inspirada em necessidades resolvidas por Spring Security, mas NÃO como cópia.

Objetivos:
- authentication / authorization;
- JWT / OAuth/OIDC;
- sessions / roles / permissions;
- security policies / CSRF / CORS;
- secure headers / rate limiting;
- input validation / password hashing;
- audit logging / API security.

A filosofia deve ser declarativa e segura por padrão:

```kof
security {
    auth jwt
    route "/admin" requires role("admin")
    route "/users" requires auth
    rate "/login" 10/minute
}
```

Estado atual: ✅ implementado (v1, docs/security.md)

**Implementado (0.2.6-beta, inclui 0.0.5):**
- `kof.security` com API idiomática: `passwords`, `crypto`, `jwt`,
  `secrets`, `security`, `auth` + G9 (`rateLimit`, `sessionCreate`, `apiKeyGenerate`).
- Password hashing PBKDF2-HMAC-SHA256 (600k iterações, salt, constant-time,
  formato versionado).
- Crypto: SHA-256/512, HMAC, AES-GCM, random seguro — JVM, Native (asm, `kof_db_mysql_scramble` para MySQL) e JS.
- JWT HS256 (alg fixo — sem confusão de algoritmo), exp/iss/aud.
- Secrets por env + redação para logs; comparison constant-time; free-list Native.
- Web auth middleware (`auth.authenticated()`, `auth.hasRole(...)`).
- Gaps de target com diagnóstico claro (SECN001/002/003/004, HTTP002).

**Pendente:**
- OAuth2/OIDC client (arquitetura preparada em docs/security.md §2.3);
- audit logging;
- integração com database (planejado).

*(sessions, rate limiting e API keys fechadas em G9 — 3 targets;
JWT/passwords/SHA-512/AES-GCM no Native fechados em asm — G10.)*

---

## 5. Data / ORM / Hibernate

A visão não é substituir Hibernate à força. Kof deve manter Java interoperability e permitir `import org.hibernate.Session`.

Mas deve existir futuramente uma camada de dados nativa:

- SQL / NoSQL / transactions / connection pools;
- migrations / repositories / query APIs;
- PostgreSQL / MySQL / SQLite / MongoDB.

Experiência conceitual:

```kof
entity User {
    id: Long
    name: String
    email: String
}

User.find(id)
User.findAll()
User.save(user)

User.query {
    where age > 18
    orderBy name
}

transaction {
    user.save()
    account.update()
}

sql """
    SELECT * FROM users WHERE active = true
"""
```

Princípio: "Abstração quando ajuda, SQL quando precisa."

Estado atual: 🟡 parcial — **nível 0-2 e 4 implementados** (`kof.db` +
`kof.orm`, ver `docs/DATABASE_VISION.md`): conexão idiomática
(JDBC no JVM; SQLite nativo via `.so`; MySQL handshake `kof_db_mysql_scramble` 27/08), SQL com prepared
statements, transactions, `entity` declarativo em compile-time, CRUD
(`create/save/find/all/where/delete/count`), `orm.where` por campo + operadores, `saveAll` batch, `page`/`count`/`deleteAll`,
migrations versionadas (`kof_migrations`) e MongoDB (driver oficial).
Faltam: query DSL tipada (`User.query { where age > 18 }`), connection
pooling, MySQL completo (query/prepared), kof.db/kof.orm fora do JVM (DB001/ORM001 JS), NoSQL além do MongoDB.

---

## 6. Dependency Management

O usuário não deveria precisar editar `pom.xml` diretamente.

Comandos futuros:

```
kof init
kof install lombok
kof remove lombok
kof update
```

Arquivo próprio da linguagem (`kofdeps`). Para Kof4J, o sistema poderá gerar `pom.xml` temporário em memória durante o build e utilizar Maven para resolução/download.

Estado atual: 🟡 MVP 01/09 — `kof deps init/add/remove/list/resolve` (arquivo
`kofdeps`, resolução Maven Central → `~/.kof/deps`, classpath via
`kof build|run --deps`); dependências transitivas do POM e registry pendentes.

---

## 7. Java Interoperability

A compatibilidade Java é requisito estratégico. Kof deve conseguir utilizar classes, métodos, interfaces, bibliotecas, annotations, Maven artifacts e frameworks legados Java.

A existência de APIs nativas do Kof NÃO deve quebrar essa capacidade.

Regra: "Legado continua funcionando. Kof oferece uma experiência melhor por cima."

Estado atual: ✅ funcional (records, classes, constructors, methods, fields)

---

## 8. Frontend

API de UI declarativa inspirada conceitualmente em Flutter.

Objetivos:
- componentes / composição / layout;
- estado / eventos / routing;
- forms / validation;
- responsive design / accessibility;
- animation / theming.

```kof
column {
    text("Hello")
    button("Click") { action() }
}
button.color = red
button.alignment = center
button.size = 10
```

O KofJS deverá gerar:

```
output/
├── index.html
├── assets/
├── app.js
└── app.css
```

Estado atual: ❌ não implementado

---

## 9. Frontend + Backend no Mesmo Projeto

Um mesmo projeto Kof pode conter backend e frontend. O compilador deve entender os contextos através da estrutura/declarações do projeto.

Shared models/types poderão futuramente ser utilizados nos dois lados.

Estado atual: ❌ não implementado

---

## 10. Arquitetura de Aplicação

Kof não deve impor MVC, Clean Architecture ou Hexagonal Architecture. Deve permitir todas.

```kof
// Simples
main() {
    get "/users" { return User.all() }
}

// Modular
app/
├── domain/
├── application/
├── infrastructure/
└── api/
```

Princípio: "A linguagem fornece primitivas; a arquitetura é escolha do desenvolvedor."

Estado atual: ❌ não implementado

---

## 11. Monólito → Microserviços

A meta é permitir evolução sem reescrita:

```
monolith → modular monolith → services → microservices
```

Compilação `kof build` pode gerar `app.jar` ou `app` nativo. Posteriormente o mesmo projeto pode ser particionado.

Estado atual: ❌ não implementado

---

## 12. Performance

Kof deve permitir implementar aplicações rápidas, eficientes, escaláveis, com baixo consumo e startup rápido.

Regras:
- compile-time > runtime magic;
- type information > reflection;
- generated code > runtime discovery;
- explicit semantics > hidden framework behavior.

Estado atual: ✅ JVM funcional, Native funcional

---

## 13. Observabilidade

APIs nativas para log, metric, trace, health, audit. Integração com OpenTelemetry.

Estado atual: 🟡 parcial — `kof.log` com níveis (JVM: JSON estruturado +
correlation ID; Native: asm, UTC — JS `console.*` 01/09) e `kof.observability`
(health/readiness/liveness, counter/increment/gauge, requestId/
correlationId — 3 targets). Faltam: histogram + endpoint `/metrics`
(Prometheus), tracing/OpenTelemetry e `app.health("/health")`.

---

## 14. Standard Library / Runtime

Progressivamente:

```
kof-runtime / kof-http / kof-json / kof-data /
kof-security / kof-concurrency / kof-io / kof-ui
```

Mas NÃO criar dezenas de módulos prematuramente. Primeiro definir contratos, tipos e arquitetura.

Estado atual: 🟡 em progresso — já existem como namespaces da stdlib (0.2.6-beta):
`kof.web` (JVM, `kof.http` JVM+JS), `kof.io`, `kof.time`, `kof.config` (JVM+Native free-list), `kof.log` (JVM+Native),
`kof.security` (3 targets, G9), `kof.db` + `kof.orm` (JVM; SQLite native + `kof_db_mysql_scramble`), `kof.validation`/`kof.observability`/`kof.mq` (3 targets),
`kof.process`, `kof.ui` + `KofScript`/`KofCcompiler`. A organização em módulos separados virá depois dos
contratos estabilizarem.

---

## 15. Roadmap por Fases

### Fase 0 — Consolidação Atual ✅

- parser;
- type system;
- symbol resolution;
- semantic model;
- Kof IR;
- JVM backend;
- Native backend (concluído).

### Fase F — Runtime + Object Model ✅

- auditoria do runtime atual ✅
- Kof Runtime ABI definida ✅
- Object Model definido ✅
- ClassLayout / FieldLayout centralizados ✅
- NativeRuntime (kof_alloc, kof_panic, etc.) ✅
- NativeBackend refatorado (heap alloc, constructors, KofDup) ✅
- **Fase F.1 — String Model:** ✅
  - BuiltinTypes.STRING centralizado ✅
  - KofString layout (type_id, flags, length, UTF-8 data) ✅
  - kof_string_from_literal ✅
  - kof_string_length ✅
  - kof_string_concat ✅
  - kof_string_equals ✅
  - kof_print_string / kof_println_string ✅
  - NativeBackend usa KofString para literals ✅
  - STRING_MODEL.md documentado ✅
- **Fase F.2 — Array Model:** ✅
  - ArrayType no Type System ✅
  - NewArrayExpr + ArrayAccessExpr no AST ✅
  - Parser: new Type[size], expr[expr], expr.length ✅
  - SemanticAnalyzer: type checking de arrays ✅
  - CompilerDriver: lowering para KofNewArray/KofArrayLoad/KofArrayStore/KofArrayLength ✅
  - NativeRuntime: kof_array_alloc, kof_array_length, kof_array_get, kof_array_set ✅
  - NativeBackend: lowering completo das operações de array ✅
  - JVM Backend: NEWARRAY/IALOAD/IASTORE/ARRAYLENGTH ✅
  - ARRAY_MODEL.md documentado ✅
  - 25 novos testes (criação, acesso, length, long, string, loop, argumento, retorno, vazio) ✅
- **Fase F.3 — Inheritance:** ✅
  - SemanticAnalyzer: resolveInHierarchy() caminha cadeia de superclasses ✅
  - ClassLayout: buildWithSuper() inclui fields herdados ✅
  - NativeBackend: allClassesMap para resolver superclasses ✅
  - CompilerDriver: super(args) com argumentos, findSuperClass() ✅
  - Constructor chaining com super(args) explícito ✅
  - Acesso a fields e métodos herdados ✅
  - Herança de 3 níveis ✅
  - INHERITANCE_MODEL.md documentado ✅
  - 20 novos testes (subclasse, fields herdados, methods herdados, constructor chaining, 3 níveis) ✅
- **Fase F.4 — Virtual Dispatch:** ✅
  - Object header estendido: 8 → 16 bytes (type_id + flags + method_table_ptr) ✅
  - Method tables geradas por classe ✅
  - kof_init_object para inicializar header ✅
  - Virtual dispatch via vtable no NativeBackend ✅
  - JVM usa INVOKEVIRTUAL nativo ✅
  - Parser: suporte a `ClassName varName = value` ✅
  - CompilerDriver: NewExpr no inferExprType ✅
  - VIRTUAL_DISPATCH.md documentado ✅
  - 11 novos testes (override, polymorphism, 3 níveis, slots) ✅
- **Fase F.5 — Interfaces:** ✅
  - KofCallKind.INTERFACE na IR ✅
  - Parser: interface declaration + implements ✅
  - SemanticAnalyzer: isInterfaceType(), resolveInHierarchy() caminha interfaces ✅
  - CompilerDriver: define KofCallKind.INTERFACE para chamadas via interface ✅
  - JvmBackend: INVOKEINTERFACE ✅
  - NativeBackend: dispatch via vtable para interfaces ✅
  - INTERFACES_MODEL.md documentado ✅
  - 13 novos testes ✅
- **Fase F.6 — Exceptions/Runtime Errors:** ✅
  - AST: ThrowStmt, TryStmt, CatchClause ✅
  - Parser: try/catch/finally ✅
  - IR: KofThrow ✅
  - JvmBackend: ATHROW ✅
  - NativeBackend: kof_panic para throw ✅
  - Runtime errors: kof_null_error, kof_bounds_error ✅
  - EXCEPTIONS_MODEL.md documentado ✅
  - 14 novos testes ✅
- **Fase F.7 — Memory Management:** ✅
  - kof_alloc com tracking de alocações ✅
  - kof_free (no-op, documentado) ✅
  - kof_memstats para debug ✅
  - MEMORY_MODEL.md documentado ✅
> **Atualizado (0.2.6-beta):** interfaces (F.5), exceptions reais (F.6, JVM +
> Native unwinding) e memory management (free-list `kof_free_head` + `kof_gc_collect` 27/08; `mmap` + reuso) estão implementados.

### Fase 1 — Core

- runtime (consolidação);
- standard types;
- collections;
- IO;
- errors/exceptions;
- concurrency;
- serialization.

### Fase 2 — Developer Experience

- `kof init` / `kofdeps` / `kof install` / `kof remove`;
- `kof update` / `kof check` / `kof fmt` / `kof test` / `kof clean`;
- REPL / LSP.

### Fase 3 — Web Platform (`kof serve`)

- syscalls de rede no NativeRuntime (socket, bind, listen, accept, read, write, close) ✅;
- `kof serve` command no CLI ✅;
- KofHttpServer (thread pool, Content-Length, query, headers, 404/500) ✅;
- `kof serve` com handlers top-level (`handle(...)`) ✅;
- JSON serialization (`json.encode`/`json.decode`) ✅;
- 8 testes E2E in-process (sockets reais) ✅;
- Documentação (`docs/http.md`) ✅;
- Path parameters (`:id`), query, headers, middleware `app.use` ✅
  (stack `web.app()` — Fase 1 do plano Spring independence);
- WebSocket/SSE + hardening (`app.configure`/`app.stats`, connection cap,
  deadlines) ✅ JVM (30/08-04/09); JS/Native follow-up.

### Fase 4 — Security

- auth / authorization / JWT / OAuth/OIDC;
- sessions / policies / rate limiting;
- security defaults / audit.

> Auditoria do ecossistema: a matriz de cobertura, gaps (G1-G12),
> prioridades e estratégia vivem em `docs/ecosystem-coverage.md`.
> Ordem de implementação P0: diagnóstico de target (G7) → `kof.test`
> estruturado (G6) → `kof.config` (G3) → `kof.http` client (G2) →
> `kof.database` (G1) → validation (G4) → observability (G5) →
> scheduling (G8) → security Native (G10) → web security (G9, G12).

### Fase 5 — KofJS

- frontend / declarative UI / components;
- state / routing / forms / SSR;
- HTML/CSS/JS generation.

### Fase 6 — KofScript

- direct execution / fast startup;
- REPL / incremental execution / scripting APIs.

### Fase 7 — Native Completo

- full language support / native runtime;
- networking / database / security;
- production server support.

### Fase 8 — Maturidade da Plataforma

- distributed systems / service discovery;
- messaging / RPC;
- observability / deployment / cloud integrations.

### Fase 9 — Refactor Interno: regra de 500 linhas por classe

> **Registrado 02/09/2026.** Regra de arquitetura: nenhuma classe pode
> ultrapassar **500 linhas**. Violações atuais obrigam refactor geral:

- `NativeRuntime.java` (~17.300 — assembly embutido) → módulos por domínio
  (`native/asm/*.s` ou classes `NativeRuntime*` por área);
- `CompilerDriver.java` (~8.200) → extrair helpers por área;
- `JsBackend.java` (~5.400) → separar emitter do runtime embutido;
- `Parser.java` / `SemanticAnalyzer.java` / `JvmBackend.java` → sub-parsers.

Critério de aceite: `cloc`/`wc -l` por classe — nenhuma acima de 500.
Detalhes e tabela de tamanhos: `docs/complexity-audit.md` → "Regra de
arquitetura — limite de 500 linhas por classe".

---

## 16. Não Fazer

- não copiar Spring;
- não copiar Hibernate;
- não criar um framework monolítico gigante;
- não adicionar annotations para tudo;
- não depender de reflection quando compile-time for suficiente;
- não acoplar o core à JVM;
- não criar APIs específicas de um backend dentro da linguagem;
- não sacrificar Java interoperability;
- não implementar features gigantes antes de consolidar o core;
- não transformar cada problema em um novo módulo;
- não adicionar complexidade só porque outras linguagens fazem assim.

---

## 17. Distribuição e Tooling (0.2.6-beta)

O Kof é uma plataforma distribuível, não apenas um JAR:

- distribuição autocontida (compiler, CLI, runtime, stdlib, tooling, editor support, JDK 21 embutido);
- OpenJDK embutido no pacote oficial (Temurin 21, Tooling API Level 21);
- versionamento centralizado (`VERSION` 0.2.6-beta → pom/properties via `scripts/bump-version.sh`);
- releases por 2 jobs (`release.yml`: `test-and-bump` exporta `bump_sha` → `package-and-release` checkeia o commit de bump + sanity check de versão) por push na `main`, por plataforma linux-x86_64 / macos-arm64 / windows-x86_64 (testes 819 → bump → package 3 plataformas → GitHub Release);
- `scripts/package.sh` PASS (layout dist + tar.gz/zip + SHA256SUMS + jars), golden 16/16, integration 9/9;
- editor support oficial: grammar TextMate + LSP (hover/completion + diagnostics reais);
- `kof build/run/serve/check/test/script/repl/c/fmt/config/bench/profile/inspect/debug/info/lsp/install/version` PASS (18 comandos; `fmt` e `config gen` 31/08).

Referências: `docs/distribution/`, `docs/tooling/`.

---

## 18. Kof Escrito em Kof (auto-hospedagem)

Planejado desde já como evolução arquitetural real, não demonstração.

Pré-requisitos antes da migração:

- generics; collections; exceptions;
- stdlib; filesystem; strings; concurrency; HTTP;
- tooling; expressividade suficiente da linguagem.

O compilador atual permanece arquiteturalmente preparado para a migração
(frontend único alimentando compiler, LSP, formatter e diagnostics), mas a
migração **não** deve ser tentada prematuramente.

---

## 19. Kof + LLM

Kof é *Human First, LLM Friendly by Consequence*:

- menos ceremony; menos arquivos; menos abstrações artificiais;
- menos configuração; mais intenção.

A consistência do design faz com que humanos e LLMs entendam a mesma
linguagem da mesma forma. O diretório `training/` é parte oficial dessa
estratégia.

---

## 19.5 Kof Debugger (componente oficial de tooling)

Debugging de primeira classe: o programador depura **código Kof**,
independentemente do target. Fases 1-3 implementadas: DebugInfo na IR com
source location por op, JVM LineNumberTable/SourceFile/LocalVariableTable
gerados e **`kof debug` MVP funcional** (DAP over stdio + JDWP cru: launch,
breakpoints por linha Kof, `stopped`, stack trace com funções/linhas Kof,
continue, disconnect). Fases 4-7 (Kof Editor, Native DWARF, JS source
maps, avançado) planejadas. Ver: `docs/debugger-architecture.md`,
`docs/debugging.md`, `docs/debug-adapter.md`.

## 20. Princípios de Design

1. Simplicidade primeiro.
2. Legibilidade primeiro.
3. Compile-time sempre que possível.
4. Runtime pequeno e previsível.
5. Segurança por padrão.
6. Performance mensurável.
7. Interoperabilidade sem compromisso.
8. Abstrações nativas para problemas recorrentes.
9. Escape hatches sempre disponíveis.
10. Uma linguagem, múltiplos targets.
11. Monólito e microserviços devem ser escolhas arquiteturais, não limitações da linguagem.
12. O código deve expressar intenção, não infraestrutura.
13. Kof deve esconder complexidade sem esconder poder.
14. Compatibilidade com legado é feature.
15. Nenhuma decisão futura deve quebrar o core agnostic da linguagem.

---

## 21. Legacy Migration Platform (plano futuro)

Iniciativa de longo prazo para analisar, recuperar, traduzir e modernizar
sistemas legados para Kof — **fora do escopo 0.0.x**.

- Documento central: `LEGACY_MIGRATION.md`
- Componentes planejados: `kof inspect`, `kof decompile`, `kof translate`,
  `kof migrate`, `kof compare`
- Arquitetura: `Legacy Input → Legacy Semantic IR → Kof AST → Kof IR → Backend`
- Java é origem suportada, nunca representação intermediária obrigatória
- Documentos relacionados: `DECOMPILER.md`, `TRANSLATOR.md`, `LEGACY_IR.md`,
  `DIFFERENTIAL_TESTING.md`

**Não implementar nada desta seção antes da consolidação da linguagem,
compilador, runtime, stdlib e tooling.**

---

## 22. Plataforma Universal (plano futuro)

Visão de longo prazo — Kof como plataforma universal (uma linguagem para
aplicações **e** sistemas, infraestrutura, automação, dados, segurança e
ciência) **sem** destruir a simplicidade da linguagem.

- Documento central: `docs/future/PLAN-UNIVERSAL-PLATFORM.md` (arquitetura,
  **não** ordem de implementação)
- Estágios por capacidade/maturidade: `FOUNDATION ✅` → `SYSTEMS` (em
  andamento) → `AUTOMATION` → `INFRAESTRUTURA` → `DATA` → `SECURITY` →
  `SCIENTIFIC` → `BIO` → `UNIVERSAL`
- Mecanismo de expansão: **stdlib como tabelas de dispatch em compile-time** +
  FFI/interop + pacotes oficiais — nunca novo target, nunca linguagem nova
- Invariantes (R1–R12): fronteira core/plataforma, interop-first, escopo
  honesto por target (JVM-first/Native/JS-web), nunca silencioso por domínio
  (`INFRA00x`/`DATA00x`/`SCI00x`/`BIO00x`/`SECPQ`), tiers de estabilidade
  (`stable`/`experimental`), core pequeno e estável, segurança defesa primeiro,
  correto/determinístico em ciência
- Non-goals permanentes: sem macros abertas/type-classes/annotations/ownership/
  effect system; sem cripto caseira; sem reimplementar Arrow/BLAS/ML/
  alinhadores; sem "Kali em Kof"; sem target por domínio; sem motor SQL próprio

**Não implementar nada desta seção antes do estágio SYSTEMS fechar**
(paridade de gaps, GC mark-sweep, package manager básico — ver
`docs/plan-platform-completion.md` P0–P5).
