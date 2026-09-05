# Estado Atual do Projeto Kof

**Última atualização:** 2 de setembro de 2026
**Versão:** 0.2.6-beta

---

## Resumo Executivo

Kof é uma linguagem compilada para múltiplos targets (JVM, Native, Web, Script).

O projeto possui um **frontend completo** (lexer + parser + AST + symbol table + semantic + type checking), uma **IR backend-agnóstica** e **sete targets**: JVM (bytecode via ASM), Native (ELF x86-64, syscalls, sem libc obrigatória), Native riscv64/aarch64 (toolchain + qemu), KofJS (ES Modules na engine GraalJS embarcada), KofScript (REPL), KofC (C subset → ELF) e Android (Fase 1).

**Fases C, D, E CONCLUÍDAS**: Type System, IR generalizada, NativeBackend ELF.

**Fase F CONCLUÍDA**: String model, Array model, Inheritance, Virtual Dispatch, Interfaces, Exceptions, Memory (mmap, sem GC).

**Pipeline 0.0.5 CONCLUÍDO**: JSON parity JVM/Native, exceptions reais no JVM, sintaxe de funções sem `fun`, serve/LSP/check/install/info, distribuição oficial.

**Plataforma 0.0.7-0.1.0 (25/08)**: kof.ui (widgets + webview nativo via KofJS), kof.db (JDBC idiomático JVM + SQLite nativo via .so + MySQL wire protocol com `kof_db_mysql_scramble`), kof.orm (`entity` declarativo + CRUD/where/migrate + MongoDB), logging estruturado (JSON, correlation ID), JSON completo (Float/Double, arrays), conversões String→numérico, ARITH001, BOM UTF-8, generics `Box<T>` com `T` primitivo fixo (`NativeE2ETest` 50/50; `substituteTypeVariable` `CompilerDriver.java:3972`), `SEM025` sem falso-positivo em `hashCode/equals/toString`.

**0.2.6-beta (27/08)**: Targets separados `native`/`native.risc`/`native.arm` (`Target.java:1`); riscv64 toolchain `riscv64-linux-gnu-as` + `.option arch,rv64g` + `li a7 214/64/93`; Native free-list (`kof_free_head`) + `kof_gc_collect`; pattern matching `switch case String s` + record destructuring `Point(x,y)` em JVM/Native/JS; `String?` null safety básica; `KofScript` top-level `let` → `KofScriptGlobals`; `KofCcompiler` (`kof c`) C subset → ELF x86_64; `kof.http` JVM+JS (GraalJS `Java HttpClient`); `List map/filter/reduce`; bugs: large-project `import a.b.C` (`CompilerDriver.java:243`), `List.get`/`listOf`, Windows SIGPIPE; VERSION 0.2.6-beta; `mvn test` 810 (793+8+5+4), golden 16/16, integration 9/9.

**0.2.6-beta (30-31/08)**: Native `spawn`/`await` real (`pthread_create` + trampoline + `pthread_join` + allocator thread-safe com futex — CONC001 fechado); FP real em XMM (`vcvtsi2sd`/`mulsd`, dtoa via `snprintf` — FLT001); JSON objetos/records + arrays no Native (Int/Long/Bool/String/Double — JSN001/JSN002/JSN003); SQLite nativo via `.so` direto; MySQL wire protocol em progresso (auth scramble SHA-1 + parse `user:pass@`); JVM `WebSocket` (`app.ws`, handshake RFC 6455 + frame codec com máscara) e `SSE` (`sse.send/event/close`) via `kof.web`; `kof.http` retry/circuit breaker JVM+JS (`KOF_HTTP_RETRIES`/`KOF_HTTP_TRIPS`/`KOF_HTTP_FAILURES`/`KOF_HTTP_OPEN_UNTIL`, janela 30s, fail-fast); `kof.cache` corrigido no Native (clobber de registradores `%rax`/`%rdi`); UI Fase 7 Router (`go/replace/back/forward/param/current/depth` — real em JS, no-op JVM); `KofRuntime.close` + descritores ws; `kof fmt` e `kof config gen` implementados; pipeline de release 2 jobs (`test-and-bump` → `package-and-release` com sanity de versão) × 3 plataformas.

---

## Build Status

| Verificação | Resultado |
|-------------|-----------|
| `mvn clean package` | ✅ PASSA |
| `mvn test` | ✅ PASSA (810 testes: 793 kof-compiler +8 kof-script +5 kof-c-compiler +4 kof-cli, 0 falhas); `NativeE2ETest` 50/50, `JvmE2ETest` 29/29, `KofJsE2ETest` 35/35, `KofCCompilerTest` 5/5, `KofHttpE2ETest` 4/4, `KofCacheE2ETest` 5/5 (x3 targets), `KofWebWsE2ETest` 11/11, `KofWebSseE2ETest` 7/7 |
| `kof build` | ✅ PASSA (`--target jvm|native|native.risc|native.arm|js` [--release]; `android` em Fase 1) |
| `kof run` | ✅ PASSA (jvm|native|native.risc|native.arm|js) [--release] |
| `kof serve` | ✅ PASSA (web.app() nativo + API legada handle()) |
| `kof check` | ✅ PASSA |
| `kof test` | ✅ PASSA (suíte `test "nome" { }` nos 3 targets) |
| `kof bench` | ✅ PASSA (harness: compile, run, validate, métricas, baseline) |
| `kof debug` | ✅ PASSA (DAP MVP no JVM) |
| `kof info` | ✅ PASSA |
| `kof lsp` | ✅ PASSA (hover/completion + diagnostics reais) |
| `kof install` | ✅ PASSA |
| `kof c` | ✅ PASSA (KofCcompiler native-only C subset → ELF x86_64 via `kof_c`) |
| `kof script` | ✅ PASSA (top-level `let` → `KofScriptGlobals`, repl, --watch; Windows SIGPIPE fix) |
| `tests/run-golden.sh` | ✅ 16/16 (8 casos × jvm+native) |
| `tests/run-integration.sh` | ✅ 9/9 (CLI + serve + kof test) |
| `scripts/package.sh` | ✅ PASSA (layout dist + tar.gz/zip + SHA256SUMS + jars) |

---

## O que FUNCIONA de ponta a ponta

### Sintaxe de funções (sem `fun`)

```kf
main() { ... }                       // entry point, void implícito
String saudacao() { ... }            // retorno antes do nome
despedida(): String { ... }          // retorno após os parâmetros
void fazIsso() { ... }               // void explícito
Bool positivo(Int x) = x > 0         // expression body
int dobro(int x) { ... }             // primitivos em qualquer caixa
```

### Records

```kf
record Point(Int x, Int y)
main() {
    var p = Point(3, 7)
    println(p)                       // Ponto[x=3, y=7] (toString no JVM)
    println(p.x() == q.x())
}
```

Gera `.class` válido (construtor, accessors, toString/equals/hashCode no JVM) e binário ELF x86-64 no Native.

### Classes

```kf
class User(String name, Int age) {
    greeting(): String {
        return "Hello " + name
    }
}
```

O construtor record-style (`class X(...)`) gera um **record** — componentes,
construtor e accessors (`u.name()`); a leitura `u.name` também vira accessor;
**escrita** `u.name = "x"` NÃO (imutável, verificado 02/09). Para estado
mutável, use campos explícitos + `constructor(...)`. `User(...)` e
`new User(...)` são equivalentes (`new` é retrocompatível). Inicializadores de
campo rodam em todos os construtores (JVM, Native, KofJS). Herança, virtual
dispatch e interfaces funcionam.

### JSON

```kf
json.encode(42)                      // "42"
json.encode(user)                    // {"name":"Mel","age":30} (JVM: objetos/records)
json.encode(listOf(1, 2, 3))         // [1,2,3]
var u = json.decode<User>("{\"name\": \"Ana\", \"age\": 25}")
var l = json.decode<List<Int>>("[1, 2, 3]")
```

JVM + Native parity para int/long/bool/string/list/array. Objetos/records: JVM
(reflection) + Native (composição em compile-time, 31/08 — JSN002). Arrays de
`Float`/`Double` no Native: 31/08 (JSN001).

### Exceptions (JVM — reais)

```kf
try {
    throw "boom"
} catch (String e) {
    println("caught: " + e)
} finally {
    println("finally")
}
```

Exception table real + StackMapTable. `throw "msg"` wrap em RuntimeException; `catch (String e)` unwrap. `finally` roda em todos os caminhos (normal, capturado, propagado). Native: unwinding real pela cadeia de frames (`kof_throw_string`) — try/catch/finally nos 3 targets.

### HTTP (`kof serve`)

```kf
handle(String method, String path, String body): String {
    if (path == "/hello") {
        return "{\"msg\": \"hi\"}"
    }
    return "{\"msg\": \"not found\"}"
}
```

Handlers top-level (static), Content-Type automático, `--port`/`--host`, graceful shutdown.

### HTTP moderno (`web.app()` — stack web nativa)

```kf
var app = web.app()
app.get("/users/:id") {
    var user = User(param("id"))
    json.encode(user)
}
app.listen(8080)
```

Rotas com path params (`:id`), query, headers, middleware `app.use { }`,
Content-Type automático (JSON), 404/500, concorrência com virtual threads,
`status(201, body)`/`headerSet("X","y")` (27/08), **WebSocket** `app.ws("/chat") { }`
(handshake RFC 6455 + frame codec com máscara) e **SSE** `sse.send/event/close`
(30/08, JVM). `kof serve <file.kf>` detecta `main()` e executa apps `web.app()`.
E2E com sockets reais: `KofWebE2ETest` (9), `KofWebWsE2ETest` (11),
`KofWebSseE2ETest` (7), `KofWsFrameTest` (7). Ver `docs/stdlib-web.md`.

### kof.config e kof.log

```kf
config.str("database.url", "jdbc:h2:mem:test")   // arquivo > env > profile > default
log.info("servindo na porta 8080")               // debug/info/warn/error, níveis
```

`kof.config` (typed: str/int/long/bool, env/has; Native em asm próprio com
precedência total — `KOF_CONFIG` > env `KOF_<KEY>` > profile > `kof.config`)
e `kof.log` (níveis, off, warn→stderr; Native em asm próprio, UTC; JS
console.* com `KOF_LOG_LEVEL` — LOG001 fechado 01/09) — JVM/Native/JS.
Logging estruturado em JSON com correlation ID no
JVM. Testes: `KofConfigE2ETest` (8), `NativeConfigE2ETest` (8), `KofLogE2ETest`
(11, incl. JS), `NativeLogE2ETest` (7).

### kof.db e kof.orm — persistência nativa

```kf
entity User {
    id: Long generated
    name: String
    email: String unique
}

main() {
    var db = db.connect("jdbc:h2:mem:app;DB_CLOSE_DELAY=-1")
    orm.create<User>(db)
    orm.save(db, User(0, "Mel", "mel@kof.dev"))
    var u = orm.find<User>(db, 1)
}
```

- `kof.db`: JDBC idiomático (connect/execute/query/query<T>/transaction) no
  JVM; **SQLite nativo** via link direto da `.so`; MySQL/MariaDB por wire
  protocol sobre sockets nativos (WIP — auth scramble SHA-1 + parse
  `user:pass@`, 31/08); JS reporta DB001.
- `kof.orm`: schema na linguagem (`entity`, compile-time), CRUD completo
  (`create/save/find/all/where/delete/count`), migrations versionadas
  (`orm.migrate`), constraints (`generated`, `unique`), PK não-numérica.
  **MongoDB** suportado (driver oficial via reflexão compatível). Native/JS
  reportam ORM001.
- Testes: `KofDbE2ETest` (9), `KofOrmE2ETest` (10, incluindo MongoDB E2E).
- Ver `docs/future/DATABASE_VISION.md`.

### Feedback de uso real (kof-calculator-lab)

- UTF-8 BOM inicial tolerado pelo Lexer (editores Windows).
- `String.toInt()/toLong()/toDouble()/toFloat()` como funções do runtime.
- ARITH001: divisão/resto por zero **constante** rejeitada em compile-time
  (inteiros; float/double produzem Infinity/NaN e não são diagnosticados).
- `--help` nos subcomandos `kof run/build/serve/check`.

### kof.ui (plataforma de UI)

`Color` (RGBA 32-bit), `Theme` (light/dark), `Palette.*`, widgets
`Window`/`Label`/`Button`/`Input`/`Column`/`Row`/`View`/`Style` — renderização
**KofJS**: `kof run --target=js` abre o app interativo no webview nativo
(`bin/kof-webview`, WebKitGTK embutido; módulos ES sobre `file://` habilitados
via `webkit_settings_set_allow_file_access_from_file_urls`). Ações de botão
por lambdas com capturas; fechar a janela encerra o programa. JVM/Native:
handles no-ops.

---

## O que está implementado

### Type System

| Feature | Status |
|---------|--------|
| `Type.java` | ✅ PrimitiveType, ClassType, TypeVariable, ArrayType, WildcardType |
| `SymbolTable.java` | ✅ Scopes encadeados, resolução em hierarquia |
| `SemanticAnalyzer.java` | ✅ Métodos, constructors, fields, locals, generics por erasure; 25/08 `SEM025` ignora `hashCode/equals/toString` |
| Type checking | ✅ Assignability, larguras primitivas, arg types; 25/08 `Box<T>` `T→Int` via `substituteTypeVariable` |

### IR Lowering

| Feature | Status |
|---------|--------|
| Records, classes, interfaces, herança | ✅ |
| Funções top-level (todas as formas) | ✅ |
| Métodos, construtores, `super` | ✅ |
| `var`/`val`, `return` | ✅ |
| `if`/`else`, `while`, `for`, `do-while`, `switch`, `break`/`continue` | ✅ |
| `try`/`catch`/`finally` + `throw` | ✅ (JVM real; Native panic) |
| Expressões binárias, unárias, bitwise | ✅ |
| Arrays, List\<T\>, generics | ✅ (25/08 `Box<T>` `T` primitivo/Boxed + `println` nativo `kof_int_to_string`) |
| JSON, strings (API completa), `instanceof`/`as` | ✅ |

### Segurança (kof.security — docs/security.md)

| Feature | Status |
|---------|--------|
| `passwords.hash/verify/needsRehash` | ✅ JVM/JS (PBKDF2-HMAC-SHA256 600k); Native ✅ PBKDF2/SHA-512/JWT/AES-GCM asm (G10 fechado 25/08) |
| `crypto.sha256/sha512/hmacSha256` | ✅ JVM/Native (asm)/JS — valores idênticos |
| `crypto.aesGcm` | ✅ JVM/Native (asm) |
| `crypto.randomHex/randomInt` | ✅ JVM (SecureRandom)/Native (getrandom)/JS |
| `jwt.create/verify` (HS256, exp/iss/aud) | ✅ JVM/Native (asm)/JS |
| `secrets.get/redact` | ✅ JVM/Native (/proc/self/environ)/JS |
| `security.constantTimeEquals` | ✅ 3 targets |
| `security.csrf*/corsAllowed/headers` | ✅ JVM |
| `auth.*` (contexto web Bearer JWT) | ✅ JVM |
| `security.rateLimit/session/apiKey` | ✅ 3 targets (G9 `KofSecurityG9Test` 3/3) |
| Gaps por target | ✅ Diagnostics claros SECN001/002/003/004 |
| `KofSecurityTest` | ✅ 22 testes (unit + E2E + adversariais) + `KofSecurityG9Test` 3/3 |
| `benchmarks/security/` | ✅ password-hash, jwt, hash-speed, aes-gcm |

### Backend JVM (ASM)

| Feature | Status |
|---------|--------|
| Bytecode V21 direto, COMPUTE_FRAMES | ✅ |
| Exception table + StackMapTable | ✅ |
| Records com atributo Record + toString/equals/hashCode | ✅ |
| Virtual dispatch, interfaces | ✅ |
| Erasure boxing (`kof_box`/`kof_unbox`) | ✅ |
| JSON helper `dev.kof.runtime.KofJson` (gerado via javac) | ✅ |
| List = java.util.ArrayList | ✅ |

### Backend Native (x86-64)

| Feature | Status |
|---------|--------|
| Stack machine real sobre a IR | ✅ |
| System V AMD64 ABI, ELF via `as`+`ld` | ✅ |
| Heap via mmap (`kof_alloc`) | ✅ |
| Vtables, dispatch virtual e de interface | ✅ |
| Strings, arrays, lists, JSON em assembly | ✅ |
| Syscalls de rede (`kof_net_*`) emitidos (API futura) | ✅ |

### CLI

| Feature | Status |
|---------|--------|
| `kof build` (jvm/native/js + risc/arm), `kof run` | ✅ |
| `kof serve`, `kof check`, `kof info [--json]` | ✅ |
| `kof lsp` (LSP mínimo com frontend real) | ✅ |
| `kof install`, `kof version` | ✅ |
| `kof bench` (37 benchmarks, mediana+RSS+baseline), `kof profile` | ✅ |
| `kof inspect` (IR) | ✅ |
| `kof debug <file.kf>` (DAP + JDWP cru; breakpoints por linha Kof, stack) | ✅ |
| `kof fmt` (parser real, idempotente), `kof config gen` | ✅ (31/08) |

---

## O que NÃO está implementado (residual 0.2.6-beta)

### Language Features
- Null safety completo (`String?` básico ✅ 27/08, checks avançados planned)
- Pattern matching avançado (destructuring aninhado, guards — básico `case String s` + `Point(x,y)` ✅ 27/08)
- Annotations genéricas, Reflection

### Type System
- Overload resolution completo
- Variance / bounds avançados
- Sealed types

### Backends
- KofJS — alpha (GraalJS embarcado): `while(true)`, `try/finally`, `switch` pattern, `listOf map/filter/reduce`, `kof.http` via `Java HttpClient` (+ retry/circuit paridade JVM, 30/08), decode de objetos — parity JVM/Native/JS; UI via webview nativo; `spawn`/`await`/`channel<T>()` com concorrência real (async/await/Promise, CONC003 fechado 03/09)
- KofScript — ✅ `KofScript` top-level `let` → `KofScriptGlobals` + REPL + `--watch` (Windows SIGPIPE fix 27/08)
- KofC — ✅ `KofCcompiler` C subset native-only (`kof c`) → ELF x86_64 (while/if/deref `&`/`*(int*)`)
- Native riscv64 — **codegen real (02/09)** — stack machine riscv64 + runtime em asm puro (raw syscalls, sem C), `NativeRiscv64E2ETest 4/4` via qemu (`NATIVE002` parcial); aarch64 placeholder (target separation done)

### Runtime
- GC automático Native — free-list `kof_free_head` (reuso `mmap`, 27/08); GC mark-sweep pendente; auto-GC desativado após hang (memória devolvida só no `munmap` fallback)
- ~~JSON Float/Double: JSN001~~ — ✅ fechado 31/08 (encode/decode/arrays FP XMM + parser fracionário/expoente)
- ~~Ponto flutuante Native: sem SSE real (FLT001)~~ — ✅ fechado: aritmética FP é XMM; JSON FP fechado no JSN001

### Security (kof.security — docs/security.md)
- v1 + G9 implementado (3 targets).
- Pendente: OAuth2/OIDC client, audit logging, JWT/passwords fora do JVM completos (SECN001/004 em progresso).

### Database (docs/future/DATABASE_VISION.md)
- Nível 0 (conexão + SQL), 2 (ORM básico) e 4 (migrations) implementados; SQLite nativo + MySQL handshake `kof_db_mysql_scramble` 27/08.
- Pendente: nível 3 query DSL tipada `User.query { where ... }`, connection pooling, MySQL completo (query/prepared), kof.db/kof.orm fora do JVM (DB001/ORM001 — JS).

### Plataforma (gaps — docs/ecosystem-coverage.md §4)
- Todos P0 originais fechados em 0.1.0; 0.2.0 fecha pattern matching, null safety básica, kof.http JS, free-list GC, target separation, KofScriptGlobals, KofCcompiler; 0.2.6-beta (30-31/08) fecha WebSocket/SSE (JVM), `kof.cache` (3 targets), `kof.http` retry/circuit (JVM+JS) e `spawn` Native (CONC001).
- Residual: scheduler Native (SCHED001), `kof.http` Native (HTTP002), tracing, MySQL nativo (prepared), RISC/ARM codegen, GC mark-sweep.

### Tooling
- `kof fmt` ✅ (parser real, idempotente — 31/08); `kof init` ainda planejado
- LSP hover/completion/rename + Debugger Native DWARF/JS source maps (P5)

---

## Arquitetura (0.2.6-beta — 7 targets: jvm, native x86_64, native.risc, native.arm, js, kofc, android)

```text
Source (.kf)
  ↓ Lexer
  ↓ Parser
  ↓ AST
  ↓ Symbol Resolution
  ↓ Semantic Analysis
  ↓ Type Checking
  ↓ Kof IR (backend-agnostic + KofDebugInfo)
  ↓ Optimizer
  ├── JVM Backend (ASM) → .class V21 (virtual threads, KofRuntime com web/ws/sse/cache)
  ├── Native Backend (x86-64, free-list + kof_gc_collect, pthread spawn, FP XMM)
  ├── Native riscv64 (native.risc — codegen real 02/09, asm puro + qemu)
  ├── Native aarch64 (native.arm — toolchain + placeholder x86_64 via qemu)
  ├── JS Backend (GraalJS, kof.http via HttpClient, retry/circuit)
  ├── KofC Backend (C subset → native)
  ├── KofScript (let → KofScriptGlobals)
  └── Android (bytecode JVM → projeto Maven + APK; host Activity em Kof, Fase 1)
```

| Módulo | Estado |
|--------|--------|
| kof-compiler | Funcional (~10k LOC) |
| kof-cli | Funcional (18 comandos: build, run, serve, check, test, script, repl, c, fmt, config, bench, profile, inspect, debug, info, lsp, install, version) |
| kof-runtime | Estrutura criada (runtime nativa embutida no NativeBackend; KofJson no JVM) |

| Métrica | Valor (0.2.6-beta 31/08) |
|---------|--------------------------|
| Testes JUnit | 810 (793 kof-compiler +8 kof-script +5 kof-c-compiler +4 kof-cli, 0 falhas) |
| E2E JVM | 29 |
| E2E Native (x86_64) | 50 |
| E2E JS (KofJS) | 35 (+ kof.http JS) |
| E2E KofScript | 8 |
| E2E KofCcompiler | 5 |
| E2E JSON | 14 + 7 (completo) |
| E2E Exceptions | 9 |
| E2E HTTP/Web | 8 + 9 (TLS 5) + http 4 (JVM+JS) + ws 11 + sse 7 + frame 7 + resilience 3 (30/08) + cache 5 (x3 targets) |
| E2E kof.io | 15 |
| E2E UI | 14 + 3 (Window) |
| E2E kof.db / kof.orm | 8 + 16 (SQLite native + MySQL scramble) |
| E2E kof.security + G9 | 22 + 3 |
| Golden | 16/16 (8 casos × jvm+native) |
| Integration | 9/9 |
| Benchmarks | 37 em 17 categorias |
| Targets | jvm stable, native x86_64 stable (free-list + pthread spawn), native.risc/native.arm (toolchain + placeholder via qemu), js alpha, kofc native-only, android Fase 1 |