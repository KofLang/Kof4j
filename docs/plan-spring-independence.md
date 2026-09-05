# Plano — Kof Spring Starter + Independência do Spring

**Última atualização:** 2 de setembro de 2026
**Versão:** 0.2.6-beta (810 testes; `kof.http` JVM+JS com retry/circuit; web ws/sse JVM; free-list + pthread spawn Native)

> Este documento transforma a especificação "Kof Spring Starter + Independência
> do Spring" em um plano executável por fases, com critérios de aceite e ordem
> de dependências.
>
> Regra absoluta: **Kof não existe para ser uma linguagem melhor para escrever
> Spring. Kof existe para ser uma linguagem completa. Spring é apenas uma das
> muitas coisas que Kof deve conseguir consumir.**

---

## 0. Arquitetura-alvo

```
                 ┌───────────────┐
                 │     Kof       │
                 │    stdlib     │
                 └───────┬───────┘
                         │
              ┌──────────┴──────────┐
              │                     │
              ▼                     ▼
       Kof Application       Spring Application
              │                     │
              ▼                     ▼
        Kof Runtime           Spring Runtime
              │                     │
              └──────────┬──────────┘
                         ▼
                        JVM
```

- O ecossistema Kof/stdlib deve conter solução nativa para toda capacidade
  fundamental (HTTP, JSON, DI, config, database, security, validation,
  logging, testing, concurrency, observability).
- Spring é interoperabilidade opcional, nunca fundação.
- Kof compila direto para bytecode JVM: `Kof → Kof compiler → JVM bytecode →
  Spring`. Nunca `Kof → Java source → javac → Spring`.

---

## 1. Princípios de decisão

Toda implementação futura deve responder:

> "Essa funcionalidade pertence à linguagem/stdlib Kof ou estamos simplesmente
> dependendo de uma abstração que o Spring já possui?"

Se for capacidade fundamental → solução Kof-native. Spring pode fornecer
implementação alternativa ou integração, nunca o requisito.

Stack Kof-native (mapeamento de referência, sem copiar APIs):

| Capacidade | Spring (referência) | Kof-native |
|------------|--------------------|------------|
| HTTP/routing | Spring MVC/WebFlux | `web.app()` + rotas ✅ (JVM; WebSocket/SSE 30/08) |
| JSON | Jackson | `json.encode/decode` (3 targets, 31/08) |
| DI | Spring Context | compile-time / runtime Kof |
| Config | Spring Configuration | `kof.config` tipado |
| Database | Spring Data/Hibernate | `db.query<T>` + `transaction {}` |
| Security | Spring Security | camada `kof.security` |
| Validation | jakarta.validation | validação nativa |
| Logging | SLF4J/Logback | `log.info()` estruturado |
| Scheduling | `@Scheduled` | `schedule/every/after` |
| Events | Spring Events/Kafka | filas/pub-sub nativos |
| Concurrency | executor/async | `spawn` + structured concurrency |
| Lifecycle | SpringApplication | `application { onStart/onShutdown }` |
| Testing | JUnit/Spring Test | `kof test` + suíte completa |
| Observability | Spring Actuator | métricas/health/tracing nativos |

---

## 2. Fases

### Fase 1 — Web nativa Kof ✅ (concluída em 23/08/2026)

Evoluir `kof serve` para a stack web completa. Critérios de aceite:

- [x] `web.app()` cria uma aplicação; `app.get("/hello") { return "Hello" }`
      registra rota com lambda trailing (bloco).
- [x] Path parameters: `/users/:id` + `param("id")`.
- [x] Query parameters: `query("name")`.
- [x] Headers: `header("x")`; corpo: `body()`.
- [x] JSON tipado nos handlers: `return json.encode(user)` e
      `json.decode<User>(body())`.
- [x] Middleware: `app.use { ... }` (null = continua, String = resposta curta).
- [x] `app.listen(port)`, `app.port()`, `app.close()` (graceful shutdown).
- [x] `kof serve <file.kf>` executa programas `web.app()`; API legada
      `handle(...)` continua funcionando.
- [x] Testes E2E (subprocesso + sockets reais) verdes; `mvn test` verde
      (810 em 0.2.6-beta; 459/459 na época incluindo os 9 de `KofWebE2ETest`; ver `docs/status.md:10-28`).
- [x] Docs: `docs/stdlib-web.md`, `docs/status.md`, `docs/http.md`, README.

Implementado em: `KofWeb` (tabela compile-time), `Parser` (lambda trailing),
`SemanticAnalyzer`/`CompilerDriver` (dispatch `web.*` + contexto de request),
`JvmRuntime` (engine HTTP gerado), CLI `kof serve` (detecção de `main()`).

Gaps documentados: target `native` sem servidor web (WEB002); target `js`
relata `WEB001`. Fechados desde então (27-30/08): status codes/headers
customizados (`status(201, body)`/`headerSet`), **WebSocket** `app.ws("/chat") { }`
(handshake RFC 6455 + frame codec com máscara — `KofWebWsE2ETest` 11/11) e
**SSE** `sse.send/event/close` (`KofWebSseE2ETest` 7/7), `KofRuntime.close`
(descriutores ws).

### Fase 2 — JSON nativo completo ✅ (concluída em 31/08/2026)

- [x] JVM: Float/Double em `json.encode`/`json.decode` (JSN001 fechado no JVM).
- [x] Decode de arrays (`Int[]`, `Long[]`, `Bool[]`, `String[]`, `Double[]`)
      no JVM (JSN003; `List<User>` já funcionava).
- [x] Native: encode/decode de objetos e records (JSN002 — composição em
      compile-time, 31/08).
- [x] Native: Float/Double (JSN001 — aritmética FP em XMM + parser
      fração/expoente, 31/08).
- [x] Testes de paridade JVM/Native (`JsonE2ETest` 14 + `JsonCompleteE2ETest` 7).
- [x] Jackson continua funcionando via interop (inalterado).

### Fase 3 — Configuração nativa (`kof.config`) ✅ (concluída em 23/08/2026)

- [x] Ambiente, arquivos de config, profiles, secrets, tipagem.
- [x] `config.int/str/bool/long` com default + `config.get/has/env`.
- [x] Precedência: arquivo explícito (`KOF_CONFIG`) > env `KOF_<KEY>` >
      profile (`kof.<KOF_PROFILE>.config`) > arquivo padrão (`kof.config`).
- [x] Suporte a environment variables.
- [x] Testes (`KofConfigE2ETest`, 8 E2E) + docs `docs/stdlib-config.md`.
- [x] Native/JS reportam `CONF001` em compile-time.

### Fase 4 — Logging + Observabilidade ✅ (parcial — logging concluído em 23/08/2026)

- [x] `log.info/warn/error/debug` com níveis (`KOF_LOG_LEVEL`), timestamp,
      info/debug → stdout, warn/error → stderr.
- [x] Structured logging (JSON via `KOF_LOG_JSON=1`) + correlation ID por
      request web (requestId no JSON).
- [x] Native: implementação asm própria (data civil Hinnant, env scan próprio;
      timestamp UTC) — `NativeLogE2ETest` 7.
- [x] Métricas/health: `kof.observability` (health/readiness/liveness,
      counter/increment/gauge, requestId/correlationId — 3 targets).
- [ ] Tracing hooks + endpoint `/metrics` (Prometheus) + `app.health` — planned.
- [x] Testes (`KofLogE2ETest`, 11 E2E JVM+JS + `NativeLogE2ETest` 7) + docs
      `docs/stdlib-logging.md`.
- [x] JS via `console.*` com `KOF_LOG_LEVEL` — LOG001 fechado 01/09.

### Fase 5 — Database + Transactions ✅ (concluída em 23/08/2026)

- [x] `db.connect/connect2/close`, `db.execute(handle, sql, args...)`,
      `db.query(handle, sql, args...)` (linhas como JSON) e
      `db.query<T>(...)` (bind tipado a records/classes via JDBC).
- [x] `transaction { ... }` com commit/rollback automáticos.
- [x] H2 em memória nos testes (dependência test-scope); JDBC por
      interoperabilidade JVM.
- [x] Native: SQLite via link direto da `.so` (roundtrip E2E); MySQL wire
      protocol em progresso (auth scramble SHA-1 + parse `user:pass@`, 31/08).
- [x] Native/JS reportam `DB001` em compile-time.
- [x] Testes (`KofDbE2ETest`, 9 E2E) + docs `docs/stdlib-database.md`.
- [x] Migrações versionadas via `kof.orm` (`orm.migrate`, tabela
      `kof_migrations`).
- [ ] Connection pooling — planned.

### Fase 5 — Database + Transactions

- [x] `db.query<T>("select ... where id = ?", id)` sobre JDBC (interop JVM).
- [x] `transaction { ... }` com commit/rollback automáticos.
- [ ] Connection pooling e configuração tipada.
- [ ] Hibernate suportado como backend opcional via interop (teste).
- [x] Testes + docs `docs/stdlib-database.md`.

### Fase 6 — Concurrency completa

- [x] `spawn` no Native (CONC001 fechado 31/08): `pthread_create` +
      trampoline + `pthread_join` + allocator thread-safe (futex), join
      implícito.
- [x] `await`/handles tipados `Handle<T>` com unboxing (JVM, 0.1.0); JS
      com async/await/Promise reais (stmt/expr; CONC003 fechado 03/09).
- [ ] Filas (`kof.concurrent.Queue`), canais, cancellation, timeouts.
- [ ] Structured concurrency e supervision sem expor Thread/Executor.
- [ ] Testes + docs `docs/stdlib-concurrency.md`.

### Fase 7 — Security nativa (v1 ✅ 25/08; docs/security.md)

- [x] Password hashing (PBKDF2-HMAC-SHA256 600k), JWT (HS256, exp/iss/aud),
      crypto (SHA-256/512, HMAC, AES-GCM) — JVM/Native (asm)/JS; gaps com
      diagnóstico (SECN00x).
- [x] Sessions, rate limiting, API keys (`security.sessionCreate/rateLimit/
      apiKeyGenerate` — 3 targets, G9).
- [x] CSRF, CORS, security headers (JVM).
- [x] TLS (`web.listenSecure` JVM — G12).
- [ ] Auth/authorization declarativa (hoje: `auth.*` no contexto web).
- [ ] Spring Security como alternativa de interop (teste), nunca requisito.
- [ ] Testes + docs `docs/stdlib-security.md`.

### Fase 8 — Validation + Scheduling + Events

- [x] Validação nativa sem jakarta.validation: `kof.validation` (13
      predicados nos 3 targets — `KofValidationTest`).
- [x] Parcial: `kof.time` `now/sleep` (3 targets) + `interval`/`every`/`at`
      JVM (`ScheduledExecutor`) + JS (`setInterval`) — 27/08; Native
      SCHED001.
- [x] `kof.mq` pub/sub + queue — 3 targets (JVM/JS; Native 01/09, MQ001 fechado).
- [ ] Backends Kafka/RabbitMQ/JMS/NATS opcionais.
- [ ] Testes + docs.

### Fase 9 — DI nativa + Application lifecycle

- [ ] Resolução de dependências em compile-time quando possível
      (`service UserService(UserRepository repository)` ou equivalente).
- [x] `application { onStart/onShutdown }` sem SpringApplication (01/09:
      construção de intenção desugarada em funções sintetizadas chamadas no
      prólogo/epílogo do main — JVM/Native/JS).
- [ ] Testes + docs (parcial: `KofObservabilityTest.applicationLifecycle*`).

### Fase 10 — Testing nativo completo

- [ ] `kof test` evoluído: unit, integration, HTTP tests, database tests,
      mocks/fakes, fixtures, property tests, benchmarks, stress.
- [ ] JUnit continua interoperável (teste).
- [ ] Testes + docs.

### Fase 11 — CLI completa

- [ ] `kof run/build/test/serve` consolidados; ferramentas futuras:
      database, migration, configuration, deployment, observability.
- [ ] `kofdeps` / `kof init` / `kof install` (dependency management).

### Fase 12 — Aplicação web completa sem Spring (teste obrigatório)

- [ ] Aplicação Kof com HTTP + routing + JSON + database + transactions +
      auth + authorization + validation + logging + metrics + tracing +
      concurrency + testing, sem nenhuma dependência Spring.
- [ ] Repositório de exemplo oficial (ex.: `examples/web-app`).

### Fase 13 — Spring Starter (`kof spring starter`)

Somente depois da Fase 12 (ou em paralelo sem criar dependência
arquitetural). Critérios de aceite:

- [ ] `kof spring starter` consulta metadata do Initializr oficial
      (https://start.spring.io/).
- [ ] Escolhe versões compatíveis e gera o projeto Spring.
- [ ] Integra o compilador Kof: source sets, classes Kof compiladas a
      bytecode (nunca Java source como backend).
- [ ] Mantém interoperabilidade JVM (controller/service/entity/DTO/security
      Kof consumidos pelo Spring).
- [ ] Testes de interoperabilidade: Kof Controller → Spring MVC → HTTP;
      Kof Service → Spring DI; Kof Entity → Hibernate; Kof DTO → Jackson;
      Kof security → Spring Security.
- [ ] Docs: `docs/spring.md`.

### Fase 14 — Documentação e Training

- [ ] README, docs/, learn/, training/ atualizados.
- [ ] `docs/spring.md`, `docs/stdlib-web.md`, `docs/stdlib-database.md`,
      `docs/stdlib-security.md`, `docs/stdlib-concurrency.md`.
- [ ] `training/` ensina primeiro Kof-native, depois Kof + Spring.
- [ ] Declaração explícita: "Spring é suportado pelo Kof, mas não é
      necessário para desenvolver aplicações Kof."

---

## 3. Regras estruturais permanentes

1. Nenhum componente da stdlib Kof pode depender de Spring.
2. Nenhum backend do compilador pode gerar Java source como passo
   intermediário obrigatório.
3. Capacidades fundamentais têm API Kof-native; Spring é alternativa.
4. O teste de independência (aplicação Kof sem Spring) é tão importante
   quanto o teste de interoperabilidade (Kof consumindo Spring).
5. Cada fase termina com testes verdes (`mvn test`) e documentação.
6. O roadmap existente (`docs/roadmap.md`) permanece a visão de longo prazo;
   este documento é o plano de execução da independência + starter.

---

## 4. Nota de operação: múltiplas sessões na mesma branch

Durante a execução da Fase 1 (23/08/2026), sessões paralelas trabalharam na
mesma branch (`feat/kofjs`) ao mesmo tempo. Isso causou conflitos reais que
foram resolvidos e devem ser esperados em sessões futuras:

- **Builds Maven concorrentes sobre os mesmos `target/`**: dois `mvn`
  simultâneos produzem erros transitórios (stale classpath, `cannot find
  symbol`, `surefire` silencioso). Mitigação: aguardar builds alheios
  terminarem ou testar em um clone isolado
  (`git clone` + `-Dmaven.repo.local=<dir>` dedicado).
- **`${revision}` no parent pom**: instalar a partir de um diretório
  sandbox sem a propriedade `<revision>` polui `~/.m2` com um descriptor
  quebrado (`kof-parent:pom:${revision}`). Limpar `~/.m2/repository/dev/kof`
  e reinstalar a partir da raiz do repositório.
- **Arquivos mid-edit**: `NativeRuntime.java` (faltava `}`), `JsBackend.java`
  (`writeHtmlEntry` referenciado antes de definido), `JvmRuntime.java`
  (template com escapes `\"` dentro de text blocks — em text block, para
  gerar `\"` no código-fonte é preciso escrever `\\"`). Todos foram
  corrigidos mecanicamente e documentados aqui.
- **Pegadinha de text block**: código de runtime embutido em
  `JvmRuntime.java`/`JsBackend.java` é gerado a partir de text blocks Java.
  Escapes de string (`\"`, `\n`) sofrem DUPLA interpretação. Escrever
  `\\"`/`\\n` no text block para produzir `\"`/`\n` no código gerado.
- **`pkill -f` suicida**: padrões que casam com a própria linha de comando
  do shell matam a sessão. Usar padrões distintos ou `kill` por PID.
- **Commit intercalado**: sessões podem commitar o trabalho umas das outras
  (`git add -A`). Sempre verificar `git status`/`git log` antes de assumir o
  estado da árvore; nunca rebasear sobre trabalho alheio em andamento.

Resultado: a Fase 1 foi validada em um clone isolado com repositório local
Maven dedicado, e o estado final da árvore de trabalho contém o trabalho das
duas frentes (web nativa + debugger/UI/security da sessão paralela).

### Segunda rodada (23/08/2026, Fases 3 e 4)

Conflitos adicionais encontrados e corrigidos durante a implementação de
`kof.config` e `kof.log`:

- **`hasRuntimeFn` perdeu `kof_ui_`/`kof_sec_`**: o commit da sessão paralela
  que introduziu config/log removeu as duas entradas → `NoClassDefFoundError
  kof/security/Security` e `kof/ui/Ui` em TODOS os programas JVM de
  security/UI (13 + 3 testes quebrados). Restauradas.
- **`collectCaptures` com `Set.of()` imutável**: a coleção de capturas de
  lambdas (feature nova da sessão paralela) passava `Set.of()` como conjunto
  de sombreamento e depois o mutava → `UnsupportedOperationException` em
  qualquer lambda com `var` interno (quebrou a stack web e o
  `json.decode<User>(body())` nos handlers). Corrigido para
  `new HashSet<>()`.
- **Descriptor de `kof_config_has`**: agrupada com funções de dois
  parâmetros → bytecode inválido (`(String;I)I`). Separada.
- **Keywords como nomes de método**: `config.int(...)` falhava no parser
  porque `int` é keyword. `parsePostfix` agora aceita keywords de tipo após
  `.` (ex.: `config.int`, `config.bool`, `config.long`).

Estado final desta rodada (histórico 23/08): 486 testes, 485 PASS, 1 em progresso na sessão
paralela (`defaultParameters` no target JS). **Atual 0.2.6-beta (27/08): `mvn test` 810 (793+8+5+4), golden 16/16, integration 9/9** — ver `docs/status.md:10-28`.