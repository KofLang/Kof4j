[English](roadmap.md) | [Português](roadmap.pt_BR.md)

# Kof — Roadmap de Longo Prazo

**Última atualização:** 20 de setembro de 2026 (índice §0 "leia primeiro"
adicionado; branch ativa corrigida para `beta-0.5.0`/`D-BRANCH-0.5.0`). (antes:
15 de setembro de 2026 — §23 ganha 2.6 = fila D-NULL-INTENT N1→N4 [lane
compiler, decisão da mantenedora 15/09]; TIER 3–5 marcado DESPRIORIZADO pela
mantenedora 15/09 — trio de volta a `future/`). (antes: fusão de planos: §23 =
plano de implementação ÚNICO (ex-`ACTION_PLAN`+`IMPLEMENTATION_PLAN`); cluster de
migração consolidado — `LEGACY_IR`+`DIFFERENTIAL_TESTING` fundidos em
`LEGACY_MIGRATION.md`)
**Versão:** 0.5.0-beta (branch ativa `beta-0.5.0`)

---

## 0. Leia primeiro

Este documento é a **narrativa de longo prazo + o plano ordenado**. O que está
VIVO (agi sobre isto):

- **§23 — Plano de Implementação Consolidado (Tiers 0–12)**: a fila única.
- **§24 — KOF 1.0 EXIT GATE (EG-1..EG-10)**: a fila do gate de release
  (autoridade: `DECISIONS.md` §D-RELEASE-1.0 / §D-1.0-EDGES; detalhe em
  `PROPOSAL-1.0-EXIT-GATE.md`).
- **§22 — Universal Platform** (em desenvolvimento) e **§18 — Kof escrito em Kof
  (auto-hospedagem / NORTH STAR)**.

As seções **§1–§15 são a narrativa de longo prazo** (direção de design, não
fila); **§8/§9/§10/§11 não têm dono** e não são trabalho atual. Regra dos três
estados: implementado/decidido → `docs/`; pendente → `docs/development/`; só
plano → `docs/development/future/`.

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
[docs/targets/KOFANDROID.md](../targets/KOFANDROID.md).

Objetivos:
- mesmo código, mesma intenção: `Window(...)` abre um app de verdade;
- reuso do backend JVM (bytecode → dex) — não há codegen alternativo;
- `kof.ui` via WebView host sintetizado (mesma camada KofJS do desktop);
- interop direta com `android.*` via ExternalClasspath
  (`extends Activity`, `super.onCreate`, annotations androidx);
- gaps honestos por compile-time (`AND001..003`).

Estado atual: 🟡 Fases 1-4 implementadas — `kof build --target android` gera
projeto Maven (zero Java/Kotlin/Gradle) com host Activity EM KOF
(`android-host.kf`) compilada pelo próprio frontend; pipeline
d8/aapt2/apksigner via pom sem dependências. Fase 2: o manifest carrega
label/permissões; `--apk`/`--keystore` constroem o artefato direto. Fase 3:
WebView responsivo (`<meta viewport>` device-width + CSS de tela estreita +
`setUseWideViewPort`/`setLoadWithOverviewMode` no host). Fase 4:
`--min-sdk`/`--target-sdk` chegam ao `<uses-sdk>`, ao platform jar e ao
`d8 --min-api` (defaults 24/34). `kof.web` é gap de compile-time imposto
(`AND002`). Pendente (Fases 5+, sem dono): `--aab` (precisa de `bundletool`,
recusado honestamente hoje), metadado de ícone declarativo (decisão pendente).
Detalhes em [docs/targets/KOFANDROID.md](../targets/KOFANDROID.md).

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
mark-sweep implementado 03/09 + auto-collect sob exaustão ✅ 19/09 (D1-A, §260 FECHADO), memória devolvida no
`munmap` fallback; `spawn`/`await` via `pthread_create` + trampoline +
`pthread_join` com allocator thread-safe (futex) — 31/08; FP real em XMM —
FLT001; JSON objetos/records + arrays FP — JSN001/002/003; SQLite nativo `.so`
direto; MySQL wire x86-64 real (13 ORM faces, F2d1–F2d7 22/09)) + `native.risc` (riscv64: codegen real 02/09 — asm puro + qemu, NATIVE002
parcial) + `native.arm` (aarch64: herda do riscv via tradutor — `NativeArchEmitter.emitAarch64`, 39/39 E2E sob qemu) *(sincronizado 12/09: a linha "codegen ainda placeholder" apodreceu — `NativeAarch64E2ETest` executa sob qemu onde há toolchain; guard honesto pula em host sem cross)*

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

Estado atual: ✅ implementado: `kof script app.kf [--watch]` + `kof repl` (statements de topo → `main()`, `var`/`val` de topo → `KofScriptGlobals`; Windows SIGPIPE fix). **0.3.0-beta: execução direta por interpretação** — `KofInterpreter` roda a MESMA IR otimizada do frontend (sem emitir bytecode, sem fork de JVM; paridade por construção com o backend JVM, provada em teste). `KofCcompiler` (`kof c`) compila C subset → ELF x86_64 nativo (`int` globals, `void` funcs, `if`/`while`/`*(int*)`/`&`).

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
Faltam (sincronizado 12/09 contra `backend-parity.md` — a lista abaixo era a
de 31/08; HTTP002/MySQL/cross-codegen FECHARAM desde então):
~~HTTP client no Native (HTTP002)~~ ✅ fechado (Native HTTP/1.1 asm —
`backend-parity.md` §kof.http; https→throw declarado), RPC (gRPC — ver
abaixo), tracing (OpenTelemetry), web residual no Native/JS
(~~WEB002/WEB001~~ **base real nos dois**: server Native `KofWebNativeE2ETest`
4/4 + GraalJS HttpServer `bc577aa`; residual TLS/ws/sse/path-params),
~~MySQL nativo completo~~ ✅ wire protocol + prepared statements binários 03/09
(`KofDbE2E` `nativeMysqlWireProtocol`/`nativeMysqlPreparedBinary`),
~~RISC/ARM codegen~~ ✅ core completo (riscv64 real 02/09; aarch64 herda via
tradutor; 39+39 E2E sob qemu — faces de paridade avançada = NATIVE002),
GC mark-sweep (G-0 riscv ✅ `356f33b9`; decomposição G-1..G-5 em
`native-multiarch.md`).
(kof.mq pub/sub + queue = 3 targets — MQ001 fechado 01/09)
Ver `docs/development/DECISIONS.md` §D-SPRING (Fases 5-14).

**gRPC no `kof.web` (novo, 31/08 — planejado)**: comunicação gRPC como
primeira classe na plataforma web — `app.grpc { service ... }` com stubs
gerados a partir de `.proto`, server streaming + unary sobre HTTP/2 no JVM
(`io.grpc` via `kof.web`), e client `grpc.call(endpoint, method, msg)`.
Escopo: Fase web (mesma família de `app.ws`/`sse.*`); codegen `.proto` → IR
Kof; parity JVM primeiro, Native/JS depois.

### Concorrência — fila residual (atualizado 13/09 — era "0.2.6-beta, 31/08")

Estado 13/09: concorrência real **JVM** (virtual threads) + **Native**
(pthread, CONC001 fechado 31/08: spawn/await + `done`/`poll`/`cancel`/
`cancelled`/`selectAny` — cancel cooperativo por TID, selectAny por polling
1ms) + **JS** ✅ 03/09 (CONC003 fechado — stmt/expr/cancel/selectAny com
async/await/Promise reais) + **supervisão OTP** (`kof.supervisor`: 1ª fatia
11/09 núcleo JVM+Script, **S2-JVM 13/09** `startAll`/`lacoUnico` — ver
`docs/planning-otp-supervision.md`; **Native x86 ✅ 15/09** — §129 fechado via
DECISIONS §2 opção B; **riscv64/aarch64 ✅ 19/09** — §129 port cross (tabela de
cadeia por-TID `kof_exc_slots`, gate `OTP001` removido), então `kof.supervisor`
roda em todos os targets nativos. **JS ✅ 18/09 — §132 resolvido:** `time.sleep` virou um
ponto de await async cooperativo (o compilador colore async o método que o alcança,
`kofTimeSleep` devolve Promise, a bomba do host `KofJsRunner` a drena), então um worker
spawnado de dentro de outra task dispara e `OTP002` foi levantado — `kof.supervisor`
agora roda em JS com paridade. O SIGSEGV anterior de `spawn→await→spawn` (pilha
desalinhada no site do `pthread_create`) foi corrigido 01/09 com `andq $-16` em
`kof_spawn_handle_new`. Um defeito latente relacionado apareceu e foi corrigido
15/09 na mesma unidade do §129: `kof_await` não limpava o TID do handle após o
join, então o `kof_spawn_join_all` implícito no fim da `main` **dava double join**
em todo handle já awaited — SIGSEGV em `__pthread_clockjoin_ex` assim que o TCB era
reciclado (reproduzido no HEAD com 50 spawns + 50 awaits, 3/3 crash; limpo após o
fix).

| Item | Descrição | Prioridade |
|------|-----------|------------|
| ~~Unwrap de `ExecutionException`~~ | ✅ 31/08 — `kof_await` re-lança a causa original (JVM) | — |
| ~~`await` com timeout~~ | ✅ 31/08 — `awaitTimeout(r, ms)`: valor no prazo, exceção capturável via `try/catch` no estouro (JVM `Future.get(ms)` + Native polling 1ms com deadline; JS deadline-poll `kofAwaitTimeout` — CONC003) | — |
| ~~Cancelamento~~ | ✅ 31/08 — `cancel(r)`/`cancelled()` cooperativo via flag no handle (JVM + Native por TID) | — |
| ~~Espera múltipla~~ | ✅ 31/08 — `selectAny(h1, h2, ...)` → primeiro handle pronto (JVM + Native + JS) | — |
| ~~`done`/`poll`~~ | ✅ 31/08 — não-bloqueantes sobre o handle (JVM + Native) | — |
| ~~Port Native~~ | ✅ 31/08 — `pthread_create` + trampoline + `pthread_join` + allocator thread-safe (futex); join implícito (CONC001 fechado) | — |
| ~~Port JS~~ | ✅ 03/09 — spawn sobre Promise, await nativo via microtask (CONC003 fechado) | — |
| ~~Scheduler~~ | ✅ 31/08 — `every`/`cancel` JVM (`ScheduledExecutor`) + JS (`setInterval`) + **Native SCHED001** (thread por job, `usleep` ms→us + flag `active`, `cancel(id)` cooperativo); `at(cron)` real 17/09 (parser de 5 campos em UTC, JVM + JS) | `at(cron)` no Native → **CRON001** (recusa em compile-time — sem parser em asm) |
| ~~Canais tipados~~ | ✅ 31/08, bloqueio real no JS 03/09 — `channel<Int>()` com `send`/`receive` (JVM `LinkedBlockingQueue` bloqueante + Native FIFO futex + JS fila de resolvers pendentes) | — |

Critério de "100%": os três targets executando os mesmos programas
concorrentes com golden diff vazio (mesmo padrão da métrica 1 do plano).

### Linguagem — fila residual (P1/P2, atualizado 13/09)

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

Estado atual: ✅ implementado (v1, docs/stdlib/security.md)

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
- OAuth2/OIDC client (arquitetura preparada em docs/stdlib/security.md §2.3);
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
`kof.orm`, ver `docs/stdlib/DATABASE_VISION.md`): conexão idiomática
(JDBC no JVM; SQLite nativo via `.so`; MySQL handshake `kof_db_mysql_scramble` 27/08), SQL com prepared
statements, transactions, `entity` declarativo em compile-time, CRUD
(`create/save/find/all/where/delete/count`), `orm.where` por campo + operadores, `saveAll` batch, `page`/`count`/`deleteAll`,
migrations versionadas (`kof_migrations`) e MongoDB (driver oficial).
Faltam: query DSL tipada (`User.query { where age > 18 }`), connection
pooling, MySQL completo (query/prepared), `kof.db`/`kof.orm` fora do JVM (**JS `DB001` FECHADO 16/09** — nao-tipado no host GraalJS; tipado `query<T>` = `DB002` FECHADO 18/09; `kof.orm` = `ORM001` FECHADO 18/09 no JS, residual Native `ORM001`), NoSQL além do MongoDB.

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
`kof build|run --deps`); **dependências transitivas do POM ✅ 16/09** (resolvidas
delegando ao Maven via `pom.xml` temporário + `dependency:build-classpath`
(R9: o resolvedor de grafo do Maven já existe — nunca reimplementado); o fecho vai
para um `kofdeps.lock` portável (lista GAV) consumido por `resolve`/`build`/`run
--deps`; degradação honesta quando `mvn` não está no PATH — warning explícito,
nunca classpath truncado em silêncio; prova `DepsTransitiveTest` 10/10 incluindo
E2E com Maven real `jgrapht-core:1.4.0 → org.jheaps:jheaps:0.11`);
**registry ✅ 19/09 (D2-A, `DECISIONS.md` §D-POLL-19)**: GitHub Releases como host oficial — publish (`kof deploy --publish`: Release + `<name>-<v>.tar.gz` + `SHA256SUMS`) + pull (`owner/repo[@ver]` no `kofdeps`, soma verificada ANTES de instalar, cache `~/.kof/deps/kof/`, REG001–004 honestos).

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
- Documentação (`docs/stdlib/http.md`) ✅;
- Path parameters (`:id`), query, headers, middleware `app.use` ✅
  (stack `web.app()` — Fase 1 do plano Spring independence);
- WebSocket/SSE + hardening (`app.configure`/`app.stats`, connection cap,
  deadlines) ✅ JVM (30/08-04/09); JS/Native follow-up.

### Fase 4 — Security

- auth / authorization / JWT / OAuth/OIDC;
- sessions / policies / rate limiting;
- security defaults / audit.

> Auditoria do ecossistema: a matriz de cobertura, gaps (G1-G12),
> prioridades e estratégia vivem em `docs/bugs-and-gaps/ecosystem-coverage.md`.
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
Detalhes e tabela de tamanhos: `docs/audits/complexity-audit.md` → "Regra de
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

- distribuição autocontida (compiler, CLI, runtime, stdlib, tooling, editor support, JDK 25 embutido);
- OpenJDK embutido no pacote oficial (Temurin 25, tooling API level 21);
- versionamento centralizado (`VERSION` 0.5.0-beta → pom/properties via `scripts/bump-version.sh`);
- releases por 2 jobs (`release.yml`: `test-and-bump` exporta `bump_sha` → `package-and-release` checkeia o commit de bump + sanity check de versão) por push na `main`, por plataforma linux-x86_64 / macos-arm64 / windows-x86_64 (testes 819 → bump → package 3 plataformas → GitHub Release);
- `scripts/package.sh` PASS (layout dist + tar.gz/zip + SHA256SUMS + jars), golden 16/16, integration 9/9;
- editor support oficial: grammar TextMate + LSP (hover/completion + diagnostics reais) + `kof editor install` (VS Code/Neovim/Vim/Emacs/Geany/Nano + IntelliJ degrau-10 honesto 13/09: filetype XML + External Tools + README LSP4IJ, sem plugin — issue #1);
- `kof build/run/serve/check/test/script/repl/c/fmt/config/bench/profile/inspect/decompile/translate/compare/migrate/debug/info/lsp/install/deps/editor/init/new/version` PASS (26 comandos; `fmt` e `config gen` 31/08).

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
continue, disconnect). Fase 4 (Kof Editor) planejada; Fase 5 (Native DWARF) x86-64 POUSOU — `.debug_line`+`.debug_info`+`.debug_abbrev` ligadas por default, `--release` remove, travadas por `NativeDwarfLineInfoTest`/`NativeDwarfSubprogramTest`; a line table cross POUSOU 19/09 (`.file`/`.loc` no riscv64, repassados verbatim pela tradução aarch64 — `NativeDwarfCrossTest`); fatia 2 pousou 19/09 ~23:5x — DIEs CU/subprogram nos cross também (`NativeDwarf.Arch` frame_base: regx-x27 riscv / reg29 aarch; slots reais via `NativeDwarfCrossRegister`; ELF travado por `NativeDwarfCrossTest`+objdump na CI); residual reduzido 20/09: face gdb-Native ✅ (`kof debug --target native` — ELF+DWARF, gdb com `directory` da fonte, override `KOF_GDB`; `--break <linha>` adiciona uma sessao BATCH scriptavel (para na LINHA Kof + backtrace, amigavel a CI) e `--output <dir>` preserva o ELF; `KofDebugNativeTest` 7/7 — batch com gdb real + construcao via stub-gdb; js recusado honesto — o engine embutido não tem inspector); DAP<->gdb ✅ 20/09 (`kof debug --dap --target native`, `KofDebugNativeDapTest` 3/3 stub-MI — o editor fala DAP com o Native e vê o .kf); attach REAL 20/09 (X7-5, `81401629`): `--dap --attach <pid>` no JVM (cliente JDWP cru reconstruido sobre oráculos medidos byte-a-byte na JDK 25.0.4.1 — §376: IDSizes lê 5 não 6, `VM.Classes` substitui `ClassesBySignature` morto, clamp de `FrameCount`, forma real de `VariableTable`, guarda NATIVE_METHOD, envelope COMPOSITE; disconnect NÃO mata o debuggee — `KofDebugAttachTest`) e no Native (gdb `-p` via `KofGdbMi.attach`; launch/configurationDone no-op quando attached); o que resta = face JS (engine embutido sem inspector = gap honesto); Fase 6 (source maps JS) V3 pousada 01/09 (`KofJsSourceMapTest`); Fase 7 (avançado) planejada. Ver: `docs/debugging/debugger-architecture.md`,
`docs/debugging/debugging.md`, `docs/debugging/debug-adapter.md`.

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

- Documento central: `future/LEGACY_MIGRATION.md` (§4 = Legacy Semantic
  IR/Confidence; §8 = teste diferencial + migration report) —
  **DESPRIORIZADO pela mantenedora 15/09: o trio + work-logs voltaram para
  `future/`; o código em kof-cli fica, promoção exige decisão explícita dela**
- Componentes planejados: `kof inspect`, `kof decompile`, `kof translate`,
  `kof migrate`, `kof compare`
- Arquitetura: `Legacy Input → Legacy Semantic IR → Kof AST → Kof IR → Backend`
- Java é origem suportada, nunca representação intermediária obrigatória
- Documentos relacionados: `future/DECOMPILER.md`, `future/TRANSLATOR.md` (os antigos
  `LEGACY_IR.md` e `DIFFERENTIAL_TESTING.md` foram fundidos no central 13/09;
  `IMPLEMENTATION_PLAN.md`/`ACTION_PLAN.md` viraram o §23 deste roadmap)

**Não implementar nada desta seção antes da consolidação da linguagem,
compilador, runtime, stdlib e tooling.**

---

## 22. Plataforma Universal (em desenvolvimento — R12 sobreposto)

Visão de longo prazo — Kof como plataforma universal (uma linguagem para
aplicações **e** sistemas, infraestrutura, automação, dados, segurança e
ciência) **sem** destruir a simplicidade da linguagem.

- Documento central: `docs/architecture/IMPLEMENTATION-UNIVERSAL-PLATFORM.md`
  (arquitetura — **EM DESENVOLVIMENTO** desde 17/09/2026; promovido de
  `future/` por decisão da mantenedora, `DECISIONS.md` §D-UNIVERSAL)
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

> **Estado 17/09:** R1 ✅ FEITO (`5f1422c6` — gate `scripts/check_stdlib_boundary.sh` + ledger na CI, AGENTS invariante 1). R2–R12: fila aberta por D-UNIVERSAL; unidades de código seguem a ordem de valor do §23.
>
> **Portão R12 sobreposto em 17/09/2026** (`DECISIONS.md` §D-UNIVERSAL): a
> mantenedora autorizou abrir esta frente **com o SYSTEMS ainda em andamento**.
> O ponto de entrada é o Estágio 1 (consolidação SYSTEMS) + R1–R12; o Tier 6+
> (AUTOMATION/INFRA/DATA/…) mantém sua ordem no §23. Para **qualquer outra**
> frente, o R12 continua o default: não abrir `infra`/`data`/`sci` antes do
> SYSTEMS fechar (paridade de gaps, GC mark-sweep, package manager básico —
> §23 P0–P5).

---

## 23. Plano de Implementação Consolidado (Tiers 0–12)

> **Este é o ÚNICO plano de implementação ordenado do repo.** Funde
> `ACTION_PLAN.md` e `IMPLEMENTATION_PLAN.md` (apagados 13/09 — ~85% do
> conteúdo era a MESMA tabela de fases/tiers entre os dois, e as duas
> divergiam do código). Toda fase aqui move o doc correspondente de
> `future/`→`docs/` quando ganha código. Dificuldade: `E` fácil · `M`
> médio · `H` alto · `R` pesquisa.
>
> **Regra transversal (R12):** nenhum item de plano futuro é ação sobre o
> estado atual; frentes novas (AUTOMATION/DATA/SCI/BIO) não abrem antes do
> estágio SYSTEMS (§21/§22) fechar. Non-goals (§16/§22): sem macros abertas,
> type-classes, ownership, effect system, cripto caseira, reimplementar
> Arrow/BLAS/ML; sem "Kali em Kof"; sem motor SQL próprio.

### TIER 0 — Guardrails e processos (E, ≈ zero) ✅ 01/09

R1/R5/R6/R7/R9–R12 como invariantes (AGENTS.md + §22); convenção de gaps por
domínio (`INFRA00x`/`DATA00x`/`SCI00x`/`BIO00x`/`SECPQ`) + matriz de paridade;
tiers `stable`/`experimental` (`docs/backend-parity.md`).

### TIER 1 — Fechamento do estágio SYSTEMS (M–H, pré-requisito p/ Tiers 6+)

| # | Item | Estado medido (13/09) |
|---|------|----------------------|
| 1.1 | Gaps de paridade (`HTTP002`, resíduo web `WEB002`/`WEB003`/`WEB004`, ~~`CONC003`~~ ✅ 03/09, ~~`LOG001`~~ ✅ 01/09, ~~`MQ001`~~ ✅ 01/09, ~~`SCHED001`~~ ✅ 31/08, ~~`TIME001`~~ ✅ 02–05/09, ~~`SECN002`~~ ✅ 01/09, ~~`OBS002`~~ ✅ 01/09, `MEDIA`) | 🟡 em progresso — JS web server base ✅ 16/09 (WEB001 fechado; DB001 fechado); residual por `backend-parity.md` (HTTP002 https/TLS nativo, gap codes ws/sse, MEDIA) |
| 1.2 | GC mark-sweep automático no Native | 🟡 riscv `356f33b9` ✅; x86 decomposto G-1..G-5 (`native-multiarch.md`) |
| 1.3 | Query DSL tipada (`User.query {}`) | ✅ 01/09 (`KofOrmE2ETest`) |
| 1.4 | Package manager MVP (`kofdeps`) | ✅ `kof deps` + resolução Maven Central; **transitivos ✅ 16/09** (delegação ao Maven + `kofdeps.lock`, `DepsTransitiveTest` 10/10 incl. E2E com Maven real); **registry ✅ 19/09** (D2-A publish + pull 1.5.3-S2, `DepsRegistryTest` 6/6) |
| 1.5 | Tracing/OpenTelemetry + lifecycle `application{}` | 🟡 spans W3C + lifecycle ✅ 3 targets; **export OTel ✅ JVM/JS (`exportSpans()` → OTLP/JSON, `OBS003`); gap honesto no Native `OBS003`** |
| 1.6 | **Native → bare-metal/bootável com ring0/ring1** (microcontrolador, BIOS legado, UEFI, anéis de privilégio x86_64) — diretiva da mantenedora 15/09 | 🟢 **FEITO — FECHADO 25/09 (promovido de `future/` 22/09)** (`D-BAREMETAL-BOOT`, ordem da mantenedora; R12 sobreposto só para esta frente): costura HAL `kof_plat_*` + perfil freestanding, faces B-0…B-6 em `docs/PLAN-BAREMETAL-BOOT.md`. **LANDADO: B-0..B-3 + B-6** (BIOS legado roda o `main` Kof real end-to-end sob SeaBIOS; ring0/ring1 com prova de `#GP` + sabotagem da GDT). **Restante autorizado (`D-BAREMETAL-BODIES`, 24/09): corpos de plataforma B-5** — `kof_plat_time` no BIOS/UEFI (RTC/TSC), `sleep`, `mono`, `random` **LANDADOS 24/09**. **B-4 (MCU):** o codegen 32-bit **LANDOU** (emissores RV32I + Cortex-M3/Thumb-2; hello pela UART + reset path da vector table asserido — `NativeMcuE2ETest` 7/7, `NativeMcuArmE2ETest` 6/0), **B-4 FECHADO 25/09 no riscv32** (`D-BAREMETAL-MCU-GC` "and/or"): o coletor `native-multiarch.md` **G-4/G-5 foi portado para o MCU 32-bit** (B4-GC-1..4: alocação + mark conservador + sweep + prova long-running de reciclagem sob qemu; `NativeMcuGcRiscv32`/`NativeMcuGcRiscv32Sweep`, `NativeMcuGcTest` 8/0) e os corpos de `kof_plat_time` do MCU landaram (B4-TIME: recusa NOMEADA do wall + mono pelo contador `time`; `NativeMcuTimeRiscv32`, `NativeMcuTimeTest` 2/0). O plano `docs/PLAN-BAREMETAL-BOOT.md` foi movido para `docs/` (regra dos 3 estados). **Follow-ups rastreados:** (a) **FEITO 25/09** — o coletor/tempo foi **espelhado para Cortex-M3/Thumb-2** (`NativeMcuArmGc`/`NativeMcuArmGcSweep`/`NativeMcuArmTime`; `NativeMcuArmGcTest` 8/0 + `NativeMcuArmTimeTest` 2/0 sob `qemu-system-arm -M mps2-an385`; o contador mono acumula deltas do SysTick para nunca andar para trás no wrap de 2^24; `NativeMcuArmTime` está ligado ao `NativeMcuArm.renderAsm`); (b) **LANDADO 25/09 (fatia F — "spike mínimo" da mantenedora)**: `NativeMcuRiscv32.lowerMain` roda codegen real de pilha de avaliação — `listOf(1,2,3)` via `kof_list_new`/`kof_list_add` (lista growable de header estável), `.size` via `kof_list_size`, `println` de Int runtime via `kof_string_of_int` (decimal RV32I real — divisão longa binária u32/10, sem extensão M), locais (`KofLoad/KofStoreLocal` + `.Lmcu_locals`) e `KofDup`; os vermelhos E2E do tip estavam enraizados no próprio emissor MCU (o `println` de literal contava `+1` ALEM dos bytes da rodata → lixo; o `renderAsm` nunca emitia `.rodata` nem as âncoras `.Lkof_heap_root_*` → o link falhava e era engolido como `success=true` sem artefato — agora recusa `NATIVE002` alta); `mapOf`/loops continuam recusados honestamente. Prova: `NativeMcuE2ETest` **8/0** (novo `mcuSpikeListOfPrintlnSizeAndIntOverUart` — UART byte-exata `3\n42` sob qemu virt rv32) + `NativeMcuListTest` 4/0 + suíte `NativeMcu*` 38/0; split p/ ≤500: `NativeMcuListStringRiscv32` (222 linhas). Follow-up restante: espelhar a fatia para o emissor Cortex-M3. |

### TIER 2 — Fundações de compilador (M) — **status corrigido contra o código**

> A versão antiga marcava 2.1.5 e 2.2.2 como "✅"; **não são** (ver abaixo —
> auditado em HEAD 13/09, não de memória).

| # | Item | Estado REAL medido |
|---|------|--------------------|
| 2.1.1–2.1.3 | Sintaxe `extern` + type-check + gaps `FFI001`/`FFI002` (nunca drop silencioso) | ✅ `Parser.java:192` (PARSE090), `ExternalFunctionNode`, `FfiE2ETest` |
| 2.1.4 | Binding **JVM** (FFM `java.lang.foreign`) | ✅ **generalizado 18/09 (`.18`, R3):** qualquer assinatura escalar, aridade livre, retornos `void`/`String` — medidos `fmod`→1.5, `ldexp`→12.0, `strncmp`→-1, `puts(void)`, `getenv`→String (`syntax.md`) |
| 2.1.5 | Binding **Native** | ✅ **20/09 (#431 fatias 1–2, §369)** — o binário cru liga escalares **direto** (`call sym@PLT`, link-by-use), o que **substitui `dlopen`/`dlsym`**; §61 fechado. O segfault antigo do `dlopen` foi o motivo da troca, não um gap aberto |
| 2.1.6 | Marshalling struct/array | 🟡 **JVM ✅ 3.8b (20–21/09)**: `record` por valor como arg/retorno + `T[]` escalar→`ptr` (`FfiStructE2ETest` 10/10, `FfiArrayE2ETest` 5/5); **D6-3 out-buffer ✅ landado 21/09** (`Buffer`/`Buffer(U8,INOUT)`, `BufferE2ETest` 4/4 + `BufferFfiE2ETest` 4/4); **restam** = bridge de struct no JS, sret no Native (3.7). D6 ✅ decidido 20/09 |
| 2.1.7 | JS: gap `FFI002` | ✅ gap honesto + **paridade escalar FECHADA 18/09 (`d3598c2d`, fatias 3.6.F1–F3):** runner host liga via `KofJsFfiBridge`, `FfiE2ETest` 16/16 byte-for-byte JVM↔JS; browser = runtime R7; nao-escalar mantem `FFI002` |
| 2.2.1 | Inventário do codegen implícito (4 pontos: runtime `.source()`, `desugarTests`, `desugarApplication`, entity→record+schema) | ✅ os 4 existem (`CompilerPipeline:295-296`) |
| 2.2.2 | **Hook formal `CodegenStep`** | ✅ **LANDADO 21/09 (R4, `D-CODEGEN-STEP`)** — `CodegenStep`/`CodegenStepPipeline` (aditivo; registry vazio = identidade, zero mudança de comportamento; `CodegenStepPipelineTest` 6/6). O "✅" antigo de `d1c56bad` era sobre-claim da branch `planning-future`; o R4 é o landing real |
| 2.2.3 | Migrar DDL/runner p/ o hook formal | ✅ **IMPLEMENTADO 21/09 (`85779f20`, `D-DESUGAR-STEP`)** — medido 21/09 (`docs/architecture/codegen-step-2.2.3-assessment.pt_BR.md` +EN, movido pela regra dos 3 estados): **descompasso de fase** (DDL = lowering em `ExpressionOrmCallLowerer:70`; runner = desugar de AST; o hook roda na IR OTIMIZADA, `CompilerPipeline:332`). Opção B = um **registry `DesugarStep`/`DesugarStepPipeline` na fase de AST** (espelhando `CodegenStep`); os quatro desugars são registrados em `DesugarSteps.defaults()` e rodam de `CompilerPipeline:303` — livre de comportamento (regra 3; `DesugarStepPipelineTest` 7/7). O DDL do ORM permanece no lowering (não é candidato a registry). |
| 2.2.4 | Base de `infra "prod" {}` (codegen sobre records) | ✅ **POUSOU 21/09 (`D-MAKEALIVE-SYNTAX`, `966c86a4`)**: puro açúcar sobre `design()` (sem HCL; `infra` = IDENTIFICADOR, rebaixado p/ `design(): Infrastructure`) — prova `InfraSyntaxE2ETest`; o R4 ✅ era o hook |
| 2.3.1 | Constant-folding de constantes de domínio | ✅ `"a"+"b"→"ab"` (`OptimizerConstantFold:100`) |
| 2.3.2 | Detecção de ciclo no grafo `infra` em compile-time | ✅ **FECHADA 21/09 como runtime-only** (adendo a `D-MAKEALIVE-SYNTAX`, `5759b9bd`): a 2.2.4 é açúcar puro, então o compilador vê só chamadas genéricas — um grafo estático daria semântica própria ao bloco (§7/regra 11); a recusa em runtime da 3.1 nomeia os membros do ciclo |
| 2.4.1 | Scoped resources (RAII leve sobre `try/finally`) | 🟡 só design (`future/scoped-resources-plan.md`); sintaxe `using` gated por bump |
| 2.5 | Variance / sealed | ⏫ **SUPERSEDIDO 21/09 por §2.8.4** (`D-TYPE-VARIANCE`): `sealed` + variância abriram como as fatias **X5** (X5.1–X5.4 ✅ FEITO 21/09); o "adiar" antigo não vale mais |

#### 2.6 — Nullability por INTENÇÃO EXPLÍCITA (fila N1→N4 de DECISIONS §D-NULL-INTENT, 15/09)

**Decidido pela mantenedora em pessoa 15/09** (registro: `DECISIONS.md`
§D-NULL-INTENT, EN+PT — a "opção A" do §125 (dobra silenciosa `null→0`) está
REVOGADA). Lane: **compiler** (contrato nos 4 backends — não a lane docs).

| # | Passo | Escopo (uma linha) | Depende de |
|---|-------|--------------------|------------|
| 2.6.1 | **N1** — JVM+Script+JS: `Nullable(primitivo)` carrega null REAL | `T?` boxed em retorno/campo/slot nos 3 targets com tipo boxed; virar a célula `nullableprint` + as 3 paridades null-branch de `KofInterpreterParityTest` no MESMO commit do comportamento (regra 1) | — |
| 2.6.2 | **N2** — Native: null real via ABI de box tagged §104b-ii — **✅ FEITO 23/09 (face Native autorizada = print de referência `Object`)** | **`RuntimeErasureBox`** (`[MAGIC][tag][value]`, 24 B) + dispatch `kof_box_*` / `kof_unbox_*` strict+soft; x86 à mão + riscv à mão + aarch64 via tradutor. O esboço antigo de box `typeId=3` está **superseded — não criar como segundo ABI**; ver `docs/runtime/RUNTIME_ABI.md` §3.9. **POUSADO 23/09:** a face Native autorizada (pela Authorization do `D-NULL-INTENT`, 23/09) é o print polimórfico de referência `Object` — o `kof_box_to_string` agora decodifica o `type_id` de 4 bytes (offset 0, o discriminador do `kof_instanceof`), `type_id==1` (`String`) passa cru e qualquer outra referência faz tail-call em `kof_tostring_table[type_id]` (`.quad` denso emitido junto das classes, reusado das vtables; `0` = passthrough antigo). Sem segundo ABI. Prova: `NativeObjectBoxPrintE2ETest` 3/3 (oráculo JVM no teste, x86+riscv64+aarch64; fecha o §205 fatia 2). | §104b-ii / §205 fatia 2 dividem este ABI |
| 2.6.3 | **N3** — `== null` em NÃO-nullable: legal, constant-foldable, NUNCA diagnóstico | a intenção é a própria comparação; regra 2 (retrocompat): código existente que compara continua compilando | N1 |
| 2.6.4 | **N4** — auditar as faces restantes de null silencioso | map-miss `0` (SG-008), campo não-inicializado `0`, unbox-de-null `0` — cada um ganha decisão ou diagnóstico honesto (R6) | N1–N3 |
| 2.6.5 | **D-TROOL-1 (front-end) ✅ LANDADA 19/09** (`916b9fb7` core + `d61836eb` migracao/lei; `TrooleanLawE2ETest` 13/13, gate dirigido 8/8, goldens medidos JVM=Script=JS=Native-x86) — registrar `Troolean` (3 estados); `Nullable(Bool)` escrito pelo usuário → `SEM095` ("`Bool` tem exatamente dois valores — para true/false/desconhecido use `Troolean`"); `Troolean` não-instanciado = unknown; `!`/`&&`/`||` de Kleene + `== true/false`/`== null` + `println` + açúcar de condição `if (t)`≡`if (t == true)` — JVM+Script+JS via `runAll3`; migrar os 4 arquivos de teste com `Bool?` | DECISIONS §D-TROOL; prova `TrooleanLawE2ETest` + faces §306 migradas | — |
| 2.6.6 | **D-TROOL-2 (Native) ✅ 19/09 (medido)** | face de três estados no backend nativo — medir primeiro o comportamento de `Nullable(Bool)` lá (o front do PR #465 é a lane boxed-`T?`); entregar trabalho ou o diagnóstico honesto `NAT-TROOL001`, nunca fallback silencioso — MEDIDO: tabelas de Kleene IDENTICAS no Native-x86-64 (slot boxado §295/§306 reusado, zero edicao de backend; NAT-TROOL001 desnecessario; cross sob guarda qemu, CI verde) | D-TROOL-1, família 2.6.2 |
| 2.6.7 | **D-TROOL-3 (corpo+migração) ✅ 19/09** (`5f0757e8`) | `training/idioms` + `fake-idioms.md` (linha `Bool?` → Troolean), `docs/language-reference/types.md`, nota de migração no CHANGELOG (regra 1 do freeze — a classe do precedente #401), célula em `backend-parity.md` | D-TROOL-1 |

**Estado da fila (18/09 ~13:40):** PR **#438** (fork externo, `fix/278-d-null-intent-atomic`) **MESCLADO pela mantenedora em `250f6207`** (18/09 12:53) — N1 (JVM+Script+JS) landou: `Map.get` agora retorna `V?` para valores de referencia e o repro de chave ausente com valor primitivo roda limpo (medido `9`, jars frescos). A face PRESENTE `!= null` em valores primitivos continua QUEBRADA pos-merge (Int/Long/Double/Boolean → `NoSuchMethodError Object.valueOf(boxed)`; Float → CCE `Double→Float` no sitio do mapa) — catalogada como re-procedimento 2a do §294, dona `.22` cluster erasure/boxing, NAO reabertura do #438. N2 (Native) e I4/i1 permanecem fora de escopo/fila; lanes nao devem abrir frente paralela no mesmo contrato (regra 6 / um contrato, um PR). O PR tambem declara `Map.get/put/remove` em chave ausente (I7) — engolindo a face parqueada `#376`/`#409` carimbada pela mantenedora 18/09 — NAO abra frente N4 separada para essas celulas enquanto #438 estiver em revisao.

#### 2.7 — Value records / tipos de valor de primeira classe (fila de `D-VALUE-RECORD`, 16/09) — **FRONT ABERTA 19/09 (D7-A)**

**Decidido pela mantenedora 16/09** (registro: `DECISIONS.md` §D-VALUE-RECORD;
origem issue #275). Aditivo, retrocompatível. **Apenas planejado — não é
trabalho atual** (R12: frentes novas não abrem antes de o estágio SYSTEMS
fechar; lanes não devem atacar sem nova autorização).

**Plano de design:** [`future/value-records-plan.pt_BR.md`](future/value-records-plan.pt_BR.md) (zero código).

| # | Etapa | Escopo (uma linha) | Depende de |
|---|-------|--------------------|------------|
| 2.7.1 | **keyword `value` no front-end** | `value record Name(campos)` faz parse e tipagem como `record` + modificador `value`; sem semântica de identidade ainda | — |
| 2.7.2 | **ABI JVM** | mapeamento value/inline class (`invokevirtual` com semântica de valor, sem identidade `Object`) | 2.7.1 |
| 2.7.3 | **ABI Native** | passagem por valor (struct por valor / registradores) | 2.7.1 |
| 2.7.4 | **ABI JS** | objeto congelado comum (sem identidade) | 2.7.1 |
| 2.7.5 | **paridade + docs** | células de conformidade `valuerecord` + matriz de paridade + `training/` + `learn/` | 2.7.1–2.7.4 |

#### 2.8 — Fila aberta 21/09 (`DECISIONS.md` §D-CODEGEN-STEP/§D-R3-3.3/§D-R3-3.5/§D-TYPE-VARIANCE/§D-INTEROP-REFLECT)

**Decidido pela mantenedora 21/09** (múltipla escolha). D8–D12 do
`IMPLEMENTATION-UNIVERSAL-PLATFORM.md`. Aditivo; nada pousa sem prova. As duas
frentes de núcleo do sistema de tipos são **spec-first** (plano revisado antes
do código, regra 6).

| # | Passo | Escopo (uma linha) | Depende de |
|---|-------|--------------------|------------|
| 2.8.1 | **R4 `CodegenStep`** (`D-CODEGEN-STEP` = A) — ✅ **pousou 21/09** | hook de codegen INTERNO do compilador, sem sintaxe de usuário; destrava `infra "prod" {}` (3.2) + migração DDL/runner | — |
| 2.8.2 | **R3-3.3 handles/out-buffers** (`D-R3-3.3` = A) | `Handle` opaco nominal (não-aritmético) + `Buffer(U8, INOUT)` (== D6-3); pré-requisito dos Estágios 4–7 | R3 (2.1) |
| 2.8.3 | **R3-3.5 variadics** (`D-R3-3.5` = A) | SEM variadics gerais — caller passa `List`/`Array`/`Buffer`; gap documentado (R6/R7) | R3 (2.1) |
| 2.8.4 | **X5 variance + sealed** (`D-TYPE-VARIANCE` = C) | ✅ **FEITO 21–22/09** — `sealed`+`SEM080`; `switch` exaustivo+`SEM081`; variância declaration-site `out`/`in`+`SEM082`; projeção no sítio de uso `List<out T>`/`List<in T>`; X5.5 células de paridade (`sealedswitch`/`variance`/`useproj`) + docs/`training`; **plano CONCLUÍDO + movido para `docs/` 22/09** | ✅ |
| 2.8.5 | **X6 reflexão de interop** (`D-INTEROP-REFLECT` = **A, intrínseco de compile-time**) | ✅ **FEITO 21–22/09** — superfície congelada: `interop.schema(R)` → `List<Field>` imutável, zero reflexão em runtime, todos os alvos, só na fronteira; X6.0–X6.3 (intrínseco+dobra, diagnósticos `INTEROP001`/`INTEROP002`, célula `interopschema` + E2E binding Arrow/Parquet `InteropSchemaE2ETest` 18/18); **plano CONCLUÍDO + movido para `docs/` 22/09** | ✅ |
| 2.8.6 | **X2 motor de interop — Python/R** (`D-COMPLETE-FIRST` item 2) | 🔨 **EM DESENVOLVIMENTO — fatia 1 POUSADA 26/09** (lane compiler 9092): host `KofPy` em Kof sobre `process.spawn`+`json.decode<T>` — `var py = KofPy(src)` + `py.callInt/callDouble/callBool/callString(fn, listOf(...))`, goldens JVM≡x86≡JS≡SCRIPT medidos, `INTEROP004`/`INTEROP006`/`INTEROP005` nomeados (§513 trava o cross); fatia 2 = records↔JSON com dobra + motor R (fatia 3 cross com o §513) — plano `interop-engine-plan.md` (+PT) landing log | fatias 2–5 |

**Plano spec (X5 + X6):** [`type-system-extensions-plan.pt_BR.md`](../type-system-extensions-plan.pt_BR.md) — **APROVADO 21/09 (`D-TYPE-VARIANCE`/`D-INTEROP-REFLECT`)**; **CONCLUÍDO + MOVIDO para `docs/` 22/09** (todas as fatias X5.0–X5.5, X6.0–X6.3 com prova).

### TIER 3–5 — Plataforma de migração legado (Fases A–H) ✅ código+testes

`kof inspect/decompile/translate/compare/migrate` no CLI (`Main.java`);
Legacy Semantic IR com Confidence Model (5 níveis) + "nunca inventar". Prova
medida 15/09 em HEAD (`7b0bfbe0`, classes frescas): **Decompile 67 + PostDom 6,
Translate 61, Compare 7, Migrate 3** — todas verdes (os números de 13/09
57/33/6/3 estavam defasados; a então "1 célula vermelha"
`qualifiedLocalTypeTranslates` está VERDE desde que a lane `.22` a fechou).
Recuperação de corpo de método ainda parcial
(joins estruturais = Fase C; re-medido 15/09 com probe instrumentado:
1793 stubs, a maior família são prefixos de teste com computação/invokes —
519/566 TRAPs exigindo o walker de post-dominador; o número antigo "2452" do
StoreCat está defasado, o sub-caso de join if-then puro já está recuperado). O
histórico técnico detalhado vive em `future/LEGACY_MIGRATION.md` +
`future/DECOMPILER.md` (§7) — **não duplicar aqui**; esta tabela só dá a
ordem. **DESPRIORIZADO 15/09 (mantenedora): TIER 3–5 não é trabalho atual.**

### TIER 6–12 — Plataforma universal (arquitetura **EM DESENVOLVIMENTO** 17/09 — R12 sobreposto; regidos por `docs/architecture/IMPLEMENTATION-UNIVERSAL-PLATFORM.md`)

| Tier | Estágio | Escopo (uma linha) |
|------|---------|--------------------|
| 6 | AUTOMATION | `kof.workflow`/`batch`/`shell`/`ssh` — jobs como código Kof, nunca YAML/bash |
| 7 | INFRAESTRUTURA | `infra "prod" {}` (codegen, não HCL) + reconciliation loop — deps 1.4, 2.2 |
| 8 | DATA | `dataframe` tipado + Arrow/Parquet/estatística **por FFI** (wrapper, nunca reimplementar) — deps 2.1, pkg manager |
| 9 | SECURITY | S2 `Secret`/`KeyHandle` · S3 `keys.*` · S4 assimétrica · S5 **PQC** (`liboqs`, NIST) · S6 híbrido · S7 `secure.channel`; só FFI a lib auditada |
| 10 | SCIENTIFIC | BLAS/LAPACK/GPU/MPI **por FFI**; SIMD Native (pesquisa); deps 2.1, 2.4, 1.2 |
| 11 | BIO | `kof-bio` (pacote oficial): FASTA/FASTQ/VCF + alinhamento via FFI/CLI — deps 6, 8, 10 |
| 12 | UNIVERSAL | integração total + pkg manager maduro + LSP/debug/profiler por domínio; **teste final: o core da linguagem quase não cresceu** |
| — | **NORTH STAR (pós-12) — BOOTSTRAPPER** | o compilador Kof escrito em Kof; plano de design rascunhado `future/PLAN-BOOTSTRAP.md` (BS-1, `DECISIONS.md` §D-BOOTSTRAP, DECIDIDO 20/09, rascunho da `.18`); execução condicionada às E1–E6 do plano e ao exit gate 1.0 — R12 governa, nenhuma etapa é pulada por ele |

### Critical path (o que bloqueia o quê)

`Legacy-Class-File-Parser` → todos os Tiers 3–5 · `Decompiler-Structural` →
`Diff-Framework` → `Migration-Reports` · `2.1 FFI` → Tiers 8/9/10 (tudo por
FFI) · `2.2 codegen hook` → `infra`/gRPC stubs · **TIER 1 (SYSTEMS) fecha
antes de QUALQUER Tier 6+ (R12).**

### TIER 13 — Fila do ledger de dívida técnica (ABERTA 23/09, `D-TECHDEBT-23/09`; **LEDGER MORTO 24/09** — dívida medida zerada)

Fila ordenada dos vereditos de múltipla escolha da mantenedora 23/09. **24/09,
ordem da mantenedora: o ledger `tech-debt.md` está MORTO** — todo §NNN vivo
que ele rastreava foi re-medido ✅ no `known-bugs.md` (§205/§248/§271/§283/§423
23/09; §278 24/09) e o gate de tamanho `check_500` está verde; o ledger, o
contrato do scout em `technical-debt/` e a ferramenta `debt-scout` foram
removidos. A 13.6 segue abaixo como frente própria (nunca foi dívida do
ledger — é a spec de struct da FFI). Claim no `DOING.md` antes do código; um
item = um dono + uma prova.

| # | Item | Lane dona | Prova |
|---|---|---|---|
| 13.1 | §248 — default methods de interface no JS + Native (paridade) | compiler (**9092, ✅ FEITO 23/09**) | slot de vtable no Native + materialização no JS; `InterfaceDefaultMethodE2ETest` 7/7 nos 4 alvos |
| 13.2 | §271 — bridge methods nos impls de interface genérica (ABI de erasure) | compiler (**9092, ✅ FEITO 23/09**) | face JVM ✅ (§356 `c8d55a10`); face Native ✅ (§483) — `NoSuchMethodError` fechado, suíte verde |
| 13.3 | §278 — portar `kof.security` (`kof.security` ✅ **FEITO 23/09**, byte-parity Android=JVM; residual `kof.gpu` — FFM ausente no Android, exige decisão de design) | gaps-db | byte-parity do security provada; face gpu pendente |
| 13.4 | §423 — portar runtime `kof_channel_*` p/ riscv64/aarch64 | nat (**✅ FEITO 23/09, baremetal 9092**) | `kof_channel_*` no slice `RtB61`, gate `NAT005` removido; paridade qemu nas 2 archs (`BareCollectionPrimitiveArgE2ETest` 12/12; ledger §423 ✅ FIXED) |
| 13.5 | Lote de split: `NativeBackend` 603 + `CompilerPipeline` 588 + `RuntimeOrm7` 585 (behavior-preserving, precedente §442/§446) | 9092 (**✅ FEITO 23/09**) | `CompilerPipeline` ✅ 475; `RuntimeOrm7` ✅ split (`RuntimeOrm7` 34 + `RuntimeOrm7Setup` 265 + `RuntimeOrm7Fetch` 329); `NativeBackend` ✅ 547 (<600, faixa tolerada; baseline 571→547 atualizado) — `check_500.sh` rc=0, suíte verde |
| 13.6 | D6-1=B — frente `struct` mutável by-ref (spec-first, regra 11) | FFI | design §4/§6 revisado, depois diff parser/typer + testes |

## 24. KOF 1.0 EXIT GATE — estabilização dos contratos (RATIFICADO 20/09/2026, `DECISIONS.md` §D-RELEASE-1.0; arestas fechadas por `D-1.0-EDGES`)

Meta de desenvolvimento até o primeiro RC: **nenhum bug embarca, nenhuma aresta
fica aberta.** O texto normativo é `docs/PROPOSAL-1.0-EXIT-GATE.md`
(+par PT) §§8–20; a ordem de execução é a fila da §23 dele. Feito aqui (passo da
ratificação): itens 1–4 — branch ativa `beta-0.5.0` confirmada,
`DECISIONS.md`/`AGENTS.md` relidos, `D-RELEASE-1.0` registrado, EN/PT
sincronizados (doc promovido de `future/`, bloco de aprovação preenchido como
registro da ordem da mantenedora no chat).

Fila aberta (toda lane obedece; dono se declara no `DOING.md`):

| # | Item (ref do doc) | Prova de aceitação |
|---|---|---|
| EG-1 | Definir `release-blocker` **mecanicamente** (§11; Q3) | toda issue aberta classificada em exatamente uma de BLOCKS 1.0 / OUTSIDE 1.0 SURFACE / POST-1.0 / NOT A BUG / TRACKING (`tracking/contract`) via label+ledger; script lista violações; teste RED primeiro. **FEITO** `ea5d4dfe` + complemento da 5ª categoria |
| EG-2 | Implementar o gate mecânico (critérios §10; passos 6–7) | gate falha em fixtures de false-green/false-red plantados; veredito amarrado ao SHA analisado; análise velha não decide commit novo; uso de `CODEQL_GATE_SKIP` vira exceção com causa registrada, e morre. **FEITO 20/09** (proposta §10): `0d2a019d` fechou 6/8; a lane de estabilização fechou amarração ao SHA + vazio≠indisponível no `scripts/codeql-gate.sh`, RED-first (`scripts/tests/codeql-gate-test.sh` 10 cenários, registrado) |
| EG-3 | Pacote real testado **fora do repo** (§12; passo 9) | artefato do layout publicado roda o corpus E2E em diretório limpo (a lição da #550, pinada). **FEITO 20/09** `67c503db` (`scripts/test-package-outside-repo.sh`, PASS medido) |
| EG-4 | Validação ANTES/DEPOIS do gate (passo 8) | mesmo SHA medido antes/depois; nenhuma regressão nos pushes das lanes existentes. **FEITO** — evidenciado pelo EG-2 (vereditos amarrados ao SHA analisado, determinísticos; gate real medido antes/depois sem novo false-red nas lanes) |
| EG-5 | Matriz final de alvos (§13–§14; passo 10) | JVM / x86-64 / riscv64 / aarch64 / JS / Script **/ KofC / Android** verdes na MESMA candidata + paridade byte dos goldens onde o contrato exige; KofC e Android carregam cada um o seu gate (EG-9/EG-10). **Harness FEITO 20/09**: `scripts/target-matrix.sh` (um comando; 6 alvos core com paridade byte vs oráculo JVM, cross executa sob qemu quando presente, SKIP honesto→INCOMPLETE; kofc/android = DELEGATED EG-9/EG-10), RED-first `scripts/tests/target-matrix-test.sh` registrado; PASS medido nos seis alvos core. **Guarda de artefato fresco 20/09** (`jar_stale`): sem `--dist`, um `lib/kof.jar` da árvore anterior à fonte é recusado com causa nomeada (rc=3) em vez de medir um binário fantasma — o `PARITY: 0%` falso do cross por jar velho não pode mais ser lido como divergência. A rodada do dia do RC na candidata permanece |
| EG-6 | Fechar as ARESTAS abertas que pertencem à mantenedora (§21 Q2/Q7, candidatos §35) | **FEITO 20/09/2026 (`D-1.0-EDGES`)**: Q1 (linha 1.0 abre após o release 0.5.0 + EG-1..EG-7), Q2/Q7 (KofC + Android dentro da Stable 1.0, gates próprios), §35 (os nove reforços obrigatórios) |
| EG-7 | Sincronizar VERSION / docs / metadados (§16, nota §13, linha do site §35) | VERSION, `revision` do pom, version.properties empacotado, CHANGELOG, cabeçalho AGENTS, docs de release, site público, matriz de suporte — uma única declaração consistente. **Lado do repo FEITO** (bump `404d8be6`; docs de release sincronizados). **Lado do site adiado — propriedade da mantenedora** (ela atualiza o `koflang.github.io` em breve; chat 20/09) |
| EG-8 | Primeira candidata a RC 1.0 (passo 11) — SOMENTE quando EG-1…EG-7 fecharem | checklist §8 verde com evidência reproduzível no SHA da candidata + declaração explícita da Mel de que "a linha 1.0 abriu" (Q1) |
| EG-9 | Gate do KofC (gate próprio — `D-1.0-EDGES`) | KofC verde na candidata com evidência do gate próprio. **Mecanismo FEITO 20/09**: `scripts/test-kofc-gate.sh` — preflight nomeia o toolchain ausente (`as`+`ld`/`gcc`, JDK ≥ 25), compila E executa o corpus suportado (5 casos, ELF real rodado, stdout afirmado) e rejeita entrada malformada sem emitir binário (a classe do #485, R6/Q7); veredito amarrado ao SHA (`KOFC-GATE: PASS sha=…`). RED-first offline: `scripts/tests/test-kofc-gate-test.sh` (5 cenários, registrado). PASS medido em `8fa39ff9` |
| EG-10 | Gate do Android (gate próprio — `D-1.0-EDGES`) | Android verde na candidata com evidência do gate próprio (o CI roda o APK). **Mecanismo FEITO 20/09**: `scripts/test-android-gate.sh` — preflight honesto (JDK ≥ 25 + `jar`; `ANDROID_HOME` + build-tools COMPLETA ≥ 35 + uma plataforma `android-N/android.jar`); SDK ausente = SKIP honesto exit 3 nomeando o que falta (nunca verde falso, R6), e o CI `android.yml` roda o MESMO gate. Com SDK roda `kof build --target android --apk` (o pipeline standalone aapt2→d8→zip→zipalign→apksigner) e prova que o artefato é um zip real com `AndroidManifest.xml` + `classes.dex`; veredito amarrado ao SHA (`ANDROID-GATE: PASS sha=…`). RED-first offline: `scripts/tests/test-android-gate-test.sh` (6 cenários, registrado) |

Regras que amarram todo item: o gate §8 é um E entre todos os itens — um item em falta
trava o RC independentemente dos demais; por `D-1.0-STABILITY-100`
(20/09) nenhum 1.0.0 embarca enquanto QUALQUER item de `docs/development/`,
`docs/development/future/` ou `docs/bugs-and-gaps/` estiver aberto — 100%
resolvido, com paridade cross-target provada por medição; gaps ficam só na forma do §15 (FORA da
1.0 + honesto + documentado); o congelamento (§17) começa no primeiro RC e
congela a superfície, não a estabilização; RC→Stable sem regressão (§19). O
"teste final" do TIER 12 segue compatível: estabilização mexe em
estrutura/diagnóstico, nunca na superfície do núcleo.

**Gate de release 0.5.0 (`D-RELEASE-0.5.0-GATE`, 20/09/2026):** o release 0.5.0
(pré-condição para abrir a linha 1.0, Q1) só é cortado quando as sete condições
da mantenedora valerem, cada uma medida — paridade 100% entre alvos; nenhuma
decisão pendente; todos os `docs/development/*.md` soltos concluídos e movidos;
estabilidade total; 0 issues abertas de bug; todas as arestas fechadas; nada
pendente em bugs-and-gaps. Fila + estado atual:
`release-beta-0.5.0-prep.md` §"Gate de release". Mecanizado por
`scripts/check_release_050_gate.sh`.
