# Status do Projeto Kof

**Última atualização:** 3 de setembro de 2026
**Versão:** 0.3.0-beta

---

## Build

``` 
mvn clean package    → PASSA
mvn test             → 910 testes 889 kof-compiler +8 kof-script +5 kof-c-compiler +8 kof-cli, 0 falhas (03/09 SYN001 switch-expr + fix PKG005)
kof build            → PASS (--target jvm|native|js|native.risc|native.arm) [--release]
kof run              → PASS (jvm|native|js|native.risc|native.arm) [--release]
kof serve            → PASS (web.app() nativo + API legada handle())
kof check            → PASS
kof test             → PASS (suíte estruturada `test "nome" { }` nos 3 targets)
kof bench            → PASS (harness: compile, run, validate, métricas, baseline)
kof debug            → PASS (DAP MVP no target JVM)
kof info             → PASS
kof lsp              → PASS (hover/completion/references/rename + diagnostics reais)
kof install          → PASS
kof c                → PASS (KofCcompiler nativo-only C subset → ELF x86_64 via kof_c)
kof script           → PASS (KofScript top-level let → KofScriptGlobals, repl, --watch)
tests/run-golden.sh  → 16/16 (8 casos × jvm+native)
tests/run-integration.sh → 9/9 (CLI + serve + kof test)
scripts/package.sh   → PASS (layout dist + tar.gz/zip + SHA256SUMS + jars)
```

---

## 02/09 — Revisão da filosofia idiomática

- **`Set<T>` como tipo declarado no JVM**: descriptor `kof.Set` → `java/util/HashSet`
  (`NoClassDefFoundError: kof/Set` fechado); parser de membros de classe com
  retorno genérico (`Set<Int> foo()`, `List<String> bar()`).
- **Null-safety narrowing no JVM corrigido**: `if (s != null) { s.length }` /
  `s.substring(...)` emitiam `getfield "?".length`/`"".substring` (bytecode
  inválido); `if (x != null)` usava `if_icmp*` em referência. `mapOf(k1,v1,...)`
  infere o tipo do primeiro par. Forma prefixada `String? s = null` passa a
  parsear.
- **stdlib honesta**: `File.readText`/`readFile` → `String?` (Native devolve
  `null` em vez de encerrar); `File.size()` lança em vez do sentinela `-1`;
  `Map.get` → `V?` para valores de referência; `readLine()` → `String?`
  (`null` no EOF, JVM e Native).
- Ver `CHANGELOG.md` [0.2.6-beta] 02/09 e `docs/backend-parity.md`.

---

## Performance & Benchmarks (docs/performance.md)

- **Otimizador de IR** (`Optimizer.java`, sempre ativo): constant folding,
  branch simplification (condições constantes → jumps diretos), dead stack
  effects (push+pop, dup+pop, load/store round trips), unreachable code
  elimination (CFG reachability com regiões try/catch preservadas),
  jump-to-next elimination, identidades aritméticas (x+0, x*1, x/1, ...).
  Debug positions preservadas (ops sobreviventes).
- **Perfis debug/release**: `kof build|run --release` remove metadata de
  debug (SourceFile/LineNumberTable no JVM, source map no JS).
- **`kof bench`**: `kof bench [paths...] [--target jvm|native|js]
  [--iterations N] [--quick] [--baseline <file>] [--update-baseline <file>]
  [--threshold <ratio>] [--json] [--fail-on-regression]`.
  Compila → executa → valida stdout contra `expected.txt` → mede tempo
  (mediana) e RSS (Linux, `/usr/bin/time -v`) → compara baseline →
  sinaliza `PERFORMANCE REGRESSION`.
- **Estrutura `benchmarks/`**: 37 benchmarks em 17 categorias (micro,
  algorithms, collections, strings, math, objects, inheritance, interfaces,
  generics, json, io, concurrency, startup, memory, stress, applications).
- **Baselines**: `benchmarks/baselines/<target>-<version>.json` (33 jvm,
  29 native, 32 js).
- **CI**: `.github/workflows/benchmark.yml` — roda jvm+native com
  `--fail-on-regression --threshold 1.20`.
- `scripts/run-benchmarks.sh` — suite completa + atualização de baselines.
- Regra de features novas: docs/performance.md §40-§41 (Definition of Done
  inclui benchmark, stress, memory, resource e debug metadata).

### Correções de backend descobertas pelos benchmarks e E2E

| Bug | Correção |
|-----|----------|
| Chamadas via interface com retorno primitivo geravam descritor `Object` (`()Ljava/lang/Object` + `iadd` = bytecode inválido) | `analyzeInterface` agora define os symbols em `members()` (eram invisíveis ao `resolveInHierarchy`) |
| `l.get(i)`/`l.remove(i)`/`l.size`/`l.contains(...)` como statement não emitiam `KofPop` → stack desbalanceado em merge points (Frame.merge crash / VerifyError) | `hasReturnValue` cobre métodos de List que deixam valor |
| `if (long > long)` / `if (float > f)` / `if (double > d)` geravam `IF_ICMP` sobre não-ints (stack underflow) | `KofConditionalJump` ganhou `operandType`; JVM emite `LCMP`/`FCMPL`/`DCMPL` + jumps de 1 operando |
| `while (longExpr < intLiteral)` gerava `LCMP` sobre [long, int] (stack underflow) | shortcut de comparação faz widening dos operandos (`emitComparisonShortcut`) |
| JS: call com efeito descartada em statement com Pop (ex.: `users.remove(0)` silenciosamente não executava) | handler de `KofPop` no JsBackend preserva `JsCall`/`JsSequence` como statement |
| `Box<Int>` / `Box<T>` com `b.get()` retornando `T` imprimia `T` como `String` no Native → segfault `0x7` (`NativeE2ETest.execGenericClass`) | `CompilerDriver.inferExprType` substitui `T` via `substituteTypeVariable` (receiver `Box<Int>`); `println` nativo `valueOf(Int)` → `kof_int_to_string` (`CompilerDriver.java:3972,2257`) |
| `record Ponto` `hashCode()` reportava `SEM025` falso-positivo | `SemanticAnalyzer.java:1033` ignora `isObjectMethod(hashCode/equals/toString)` |
| **Regressão `dc849f6` (01/09):** `kof_list_add` sem `POP` no JVM (assumiu que o IR emitiria `KofPop`) → `hasReturnValue` trata `add` como void, então o boolean do `ArrayList.add` ficava na pilha → frame crash (`Index out of bounds`) em 15 testes | POP restaurado no emit `kof_list_add` + `hasReturnValue` blinda `add/push/append/set/clear/put` de coleção (nada de `KofPop` duplo) + `cache` só é namespace se não for local/param (`c7b23a1`…`7c6aca9` + POP `7c6aca9`) |
| **Surefire: `NativeDebugTest2/3/4/5` nunca rodavam na suíte** — o padrão default `*Test.java` não casa com `…Test2.java` (só `-Dtest` explícito os pegava) | `<includes>*Test*.java</includes>` no surefire do `kof-compiler` — suíte voltou a 752 |
| **`spawn { lambda c/ captura }` → `VerifyError`/`ClassFormatError`** (JVM) / valor errado (Native): o lowering `SpawnStmt` criava a lambda com `List.of()` (zero capturas), então o corpo resolvia a variável externa para `this` | `SpawnStmt` (JVM + Native) agora coleta via `collectCaptures(le, locals)` e emite o construtor da lambda com os loads das capturas (mesmo padrão do case genérico) — `SpawnE2ETest.spawnLambdaCapturesOuterLocal` |
| **`&&`/`||` sem short-circuit no JS**: o JsBackend emitia `KofBinaryOp.AND/OR` como `&`/`|` (bitwise), que avalia os DOIS lados → efeitos colaterais do lado de não eram executados | `&&`/`||` booleanos (operandType `bool`) agora viram `&&`/`||` JS (short-circuit nativo); `&`/`|` bitwise intacto — `KofJsE2ETest.logicalAndOrShortCircuit` + `bitwiseAndOrStillWorks` |
| **`Channel<T>` rejeitado como parâmetro de função**: o tipo do parâmetro saía `ClassType(package="")` e o `isChannel` exigia `kof.concurrent` → dispatch caía no genérico → bytecode inválido (JVM), `undefined reference Channel_receive` (Native), `c.receive()` inexistente (JS) | `Type.of`/`toType` tratam `Channel` como builtin (`kof.concurrent`, paridade com `List`); `JvmTypeMapper` mapeia `Channel` → `java/util/concurrent/LinkedBlockingQueue` (descritor+internalName) — `KofConcurrency2Test.channelAsFunctionParameter{Jvm,Native,Js}` |
| **`println`/`print` antes de `spawn` → SIGSEGV no Native** (`pthread_create`): a convenção args-by-stack (push) chegava 8 bytes desalinhada no site do `call pthread_create` (`rsp%16==8` vs `0` exigido pela ABI SysV) → glibc segfaultava em `pthread_attr_copy` escrevendo no frame | alinhamento de stack no C call: `andq $-16, %rsp` antes do `call pthread_create` em `kof_spawn_handle_new`, preservando `r15` (callee-saved) e restaurando o frame do caller — `SpawnE2ETest.nativePrintBeforeSpawnDoesNotSegfault` |
| **AES-GCM no JS ignorava tamper no ciphertext** (`SECN002`, 01/09): `kofSecB64Decode` tolerava tamanho não múltiplo de 4 (bits restantes descartados silenciosamente), então `decryptAesGcm(ct + "AA")` decodificava e o tag mismatch passava despercebido — divergente do `java.util.Base64` do JVM (que lança) | `kofSecB64Decode(s, strict)`: `strict=true` rejeita tamanho %4 ≠ 0; `decryptAesGcm` passa `strict=true` em `iv` e `ctTag`; JWT (b64-url sem padding) segue com `strict=false` — `KofSecurityTest.aesGcmJsRoundTrip` (tamper+chave errada) + paridade cross-target JVM↔JS |
| **OBS002: histogram/metrics no Native** (01/09): implementado em asm — bugs encontrados no smoke-test: (1) appender caía no fluxo principal após o seed (sem `jmp`) → crash em `kf_memcpy` com len lixeira; (2) loop de export com comparação invertida (`cmpq %idx, %len; jge` saía imediatamente) → string vazia; (3) `kf_free` clobbrou `%rsi` (fragmento) no meio do append → `kf_string_concat` com ptr corrompido; (4) `call` com `rsp%16==8` (ABI SysV) | store `.Lkof_obs_histograms` (32B: name+sum+count) + `kof_observability_metrics` via `kof_string_concat`; appender com `pushq` de alinhamento + fragmento em `%r10` (scratch); `cmpq %len, %idx` corrigido — `KofObservabilityTest.observabilityNative` (paridade de conteúdo com o JVM, byte-identical no smoke-test) |
| **`transaction {}` no Native dava link error** (`kf_db_transaction` não existia; o gate `KofDb.supportedOn` já liberava o Native) + rollback não desfazia (01/09): (1) a lambda não tinha `rdi` (=this, onde ficam as capturas) antes do `call *%rax` → lia `db` no campo errado; (2) `r12` (handle) clobberado pela lambda no caminho do throw; (3) KofStrings de BEGIN/COMMIT/ROLLBACK sem NUL final → `sqlite3_exec` lia "begin\x01" (erro ignorado) e o autocommit persistia os inserts | `kf_db_transaction` em asm: BEGIN via `kf_db_execute`, `movq %rbx, %rdi` (this) antes do invoke, COMMIT/ROLLBACK **re-carregam o handle do BSS** (`.Ldb_default_handle`, gravado no `connect`), re-throw via `kf_throw_string` (a chain aponta p/ o try externo); KofStrings com `.asciz` (NUL) — `KofDbE2ETest.nativeTransaction{Commits,RollsBackOnFailure}` |
| **MQ001: kof.mq no Native** (01/09): pub/sub + filas in-process implementadas em asm | store `.bss` (topics/queues/seq) + nodes 40B `[next, KofString*, KofList*, _, _]`; `kof_mq_find_topic`/`_queue` (busca por `kof_string_equals`, callee-saved `rbx/r12` pois `kf_string_equals` clobbra `rdi/rsi/rax`); `subscribe`/`push` criam o node na 1ª vez e `kof_list_add`; `publish` itera os subs com `kof_list_get` + invoke-com-arg (`rdi`=fn, `rsi`=msg); `unsubscribe` compara por **identidade** do objeto fn; `queue()` = `"mq-<n>"` (seq); `pop` remove head (`null` se vazio) — `KofMqE2ETest` 4/4 (JVM+Native+JS, paridade de output) |
| **`Set<T>`/`Map<K,V>` como campo/retorno de classe → `NoClassDefFoundError: kof/Set`** (01/09): dois bugs. (1) `JvmTypeMapper.classDescriptor`/`toInternalName` mapeavam só `List`→`ArrayList` e `Channel`→`LinkedBlockingQueue`, mas **não** `Set`/`Map` → o descriptor do campo/retorno ficava `Lkof/Set;`/`Lkof/Map;` (classe inexistente) enquanto o runtime real é `java.util.HashSet`/`HashMap`; (2) o parser de membro de classe (`Parser.parseClassMember`) só reconhecia `Type name(` para método (lookahead de 2 tokens), então retorno **genérico** `Set<Int> all(` caía no ramo de campo e quebrava em `(` (PARSE016/020-023/044) | `JvmTypeMapper`: `Set`→`Ljava/util/HashSet;`, `Map`→`Ljava/util/HashMap;` (desc + internalName); `Parser.parseClassMember`: novo ramo `isGenericReturnTypeAhead()` + `consumeGenericTypeArgs()` antes do fallback de campo (mesma forma do top-level `parseFunctionDeclaration`) — `KofMapSetTest.setMapAsFieldAndReturn` (3 targets, campo de classe + param de construtor + retorno de método) |

---

## Segurança (kof.security, docs/security.md)

- **`kof.security` implementado** (v1): `passwords`, `crypto`, `jwt`,
  `secrets`, `security`, `auth` — secure by default, gaps de target com
  diagnóstico claro em compile-time (SECN001/002/003).
- **JVM**: PBKDF2-HMAC-SHA256 (600k iterações), SHA-256/512, HMAC, AES-GCM,
  SecureRandom, JWT HS256 (sig/exp/iss/aud), env secrets, constant-time,
  redaction, contexto web `auth.*` (Bearer JWT).
- **Native**: SHA-256, SHA-512 e HMAC em assembly puro (x86-64, sem libc,
  FIPS 180-4 / RFC 2104 — valores idênticos ao JVM), PBKDF2-HMAC-SHA256,
  AES-GCM (round-trip E2E `aesGcmNativeRoundTrip`), JWT HS256, random via
  `getrandom`, secrets via `/proc/self/environ`, constant-time, redaction.
- **JS**: SHA-256/512 e HMAC em JS puro, PBKDF2 com delegação ao platform
  (runner embarcado), JWT, secrets, constant-time, AES-GCM (01/09, SECN002).
- **Testes**: `KofSecurityTest` — 27 testes (unit + E2E nos 3 targets +
  adversariais: tamper, expiração, confusão de algoritmo, token malformado,
  chave errada, issuer/audience).
- **Benchmarks**: `benchmarks/security/` (password-hash, jwt, hash-speed,
  aes-gcm).
- **Docs**: `docs/security.md` (auditoria + matriz + arquitetura + estado),
  `docs/stdlib.md`, `learn/36-security.md`, `training/language/security.md`,
  `training/examples/security.kf`.

---

## Database + ORM (kof.db / kof.orm)

### kof.db — persistência como parte da linguagem

```kof
main() {
    var db = db.connect("jdbc:h2:mem:app;DB_CLOSE_DELAY=-1")
    db.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, name VARCHAR)")
    var rows = db.query("SELECT * FROM users WHERE id = ?", 1)
    transaction {
        db.execute("INSERT INTO users VALUES (1, 'Mel')")
        db.execute("UPDATE users SET name = 'Melissa' WHERE id = 1")
    }
    db.close(db)
}
```

- **JVM**: JDBC idiomático (`db.connect`, `db.execute`, `db.query`,
  `query<T>` tipado por record/entity, credentials opcionais,
  `transaction {}` com commit/rollback real).
  - **Native**: SQLite via link direto da `.so` (sem driver JDBC) — roundtrip
    E2E real (`nativeSqliteRoundtrip`). **`transaction {}` com commit/rollback
    real** (01/09): `kof_db_transaction` em asm — BEGIN via `kof_db_execute`,
    invoca a lambda (vtable[0]=invoke, `rdi`=this p/ capturas), COMMIT no
    sucesso, ROLLBACK + re-throw no erro (EH `kof_exc_chain`/`kof_throw_string`
    — a exceção chega no handler com `%rdi` e a chain apontando p/ o try
    externo; conexão default = última aberta, paridade `KOF_DB_DEFAULT` do JVM).
    MySQL/MariaDB via wire protocol sobre
    sockets nativos: **handshake + auth scramble SHA-1 + auth-switch
    (mysql_native_password) + COM_QUERY + parse de resultset (coldefs + rows
    + EOF) + binds `?` (substituição de literal client-side, `nativeMysqlWireProtocol`
    — 31/08)**. Prepared statements via COM_STMT_PREPARE (binário) pendente.
- **JS**: reporta `DB001` (gap documentado).
- DSNs: `jdbc:*` (JVM), `sqlite:` (JVM/Native), `mongodb://` (ORM).

### kof.orm — o ORM da própria linguagem

```kof
entity User {
    id: Long generated
    name: String
    email: String unique
    age: Int
}

main() {
    var db = db.connect("jdbc:h2:mem:app;DB_CLOSE_DELAY=-1")
    orm.create<User>(db)                                  // DDL do schema
    orm.save(db, User(0, "Mel", "mel@kof.dev", 30))       // insert/update
    var u = orm.find<User>(db, 1)                         // PK
    var adultos = orm.where<User>(db, "age", 30)          // query por campo
    var veteranos = orm.where<User>(db, "age", ">", 30)   // operadores: > < >= <= != LIKE
    orm.saveAll<User>(db, l)                              // batch (upsert por PK)
    var pg = orm.page<User>(db, 20, 40)                   // paginação (limit, offset)
    println(orm.count<User>(db))
    orm.delete<User>(db, 1)
    orm.migrate(db, "add-phone", "ALTER TABLE user ADD phone VARCHAR")
}
```

- Schema declarado na linguagem (`entity`) — o compilador conhece campos,
  tipos e constraints em compile-time (nunca reflection para descobrir
  schema); `generated`, `unique`, PK não-numérica.
- Backends SQL: H2/SQLite/MySQL/MariaDB/PostgreSQL via JDBC (JVM).
- CRUD completo + consultas: `saveAll` (batch), `where` com operadores
- **Tipagem de coluna (P3-10)**: `where`/`where_op`/`count` com coluna literal
  que não é campo da entidade → `ORM003` em compile-time (JVM); coluna
  dinâmica (variável) segue liberada
  (`"="`, `">"`, `"<"`, `">="`, `"<="`, `"!="`...), `count` com filtro,
  `page` (limit/offset) e `deleteAll`.
- **MongoDB**: `save/find/all/where/delete/count` sobre o driver oficial via
  reflexão compatível (`Bson`/`Class`, sem ClientSession); teste E2E com
  container real (skip condicional; serviço Mongo no CI).
- Migrations versionadas: tabela `kof_migrations`, cada migração roda uma vez.
- Native/JS reportam `ORM001`.
 - Testes: `KofDbE2ETest` (9), `KofOrmE2ETest` (22; MariaDB/PostgreSQL/MongoDB
   com skip condicional quando o container não está no ar).
 - Docs: `docs/future/DATABASE_VISION.md` (níveis 0-4 implementados, incluindo
   o nível 3 = query DSL tipada `User.query(db){ where; orderBy; limit }` — 01/09).

---

## Infraestrutura de distribuição

- `VERSION` como fonte única; `<revision>` no Maven; `KofVersion` com
  `version.properties`; `scripts/bump-version.sh`.
- CLI: `build, run, serve, check, test, script, repl, c, fmt, config gen,
  bench, profile, inspect, debug, info, lsp, install, version, init`.
 - `kof lsp` — Language Server via stdio (initialize, didOpen/didChange/
   didClose → publishDiagnostics do frontend real, hover, completion,
   **references + rename** — word-boundary, single-file; `LspServerTest` 4/4).
- Launchers `bin/kof` (Unix) e `bin/kof.bat` (Windows) com JDK embutido
  (Temurin 21, Tooling API Level 21).
- `scripts/package.sh` — layout oficial de distribuição, `--jdk` para JDK
  embutido, SHA256SUMS.
- GitHub Actions: `ci.yml` (PR — testes, golden, integração, multiplatform)
  e `release.yml` (main → testes → bump → package 3 plataformas → changelog
  → GitHub Release).
- Editor support: `editor/kof.tmLanguage.json` (grammar TextMate).

---

## Targets

| Target | Backend | Execução | Status |
|--------|---------|----------|--------|
| `jvm` | `JvmBackend` (ASM) | bytecode V21, exception table, virtual threads | estável |
| `native` | `NativeBackend` (x86_64) | ELF x86_64, syscalls, free-list alloc, GC mark pending | estável |
| `native.risc` | `NativeBackend` (riscv64) | ELF riscv64 via `riscv64-linux-gnu-as/ld` + qemu (core+stdlib 02-05/09, 26/26 — ver `docs/native-multiarch.md`) | estável (core) |
| `native.arm` | `NativeBackend` (aarch64) | ELF aarch64 via `aarch64-linux-gnu-as/ld` + qemu (core+stdlib 03-05/09, 26/26 via tradução — ver `docs/native-multiarch.md`) | estável (core) |
| `js` | `JsBackend` + `KofJsRunner` | ES Modules via GraalJS, `kof.http` via `Java HttpClient` interop | alpha |
| `kofc` | `KofCcompiler` | C subset (`int` globals, `void` funcs, `if`/`while`/`*(int*)`/`&`) → nativo x86_64 | nativo-only |

O mesmo frontend e a mesma Kof IR alimentam os três backends.

---

## Estado da Linguagem

### Sintaxe de funções (sem `fun`)

```kof
main() { ... }                       // entry point, void implícito
String saudacao() { ... }            // retorno antes do nome
despedida(): String { ... }          // retorno após os parâmetros
void fazIsso() { ... }               // void explícito
Bool positivo(Int x) = x > 0         // expression body
```

### Features implementadas

| Feature | JVM | Native | KofJS |
|---------|-----|--------|-------|
| println / print | ✅ | ✅ | ✅ |
| variáveis, aritmética, bitwise, hex literals | ✅ | ✅ | ✅ |
| if/else, if-expr | ✅ | ✅ | ✅ |
| while, for, do-while, for-in, break/continue | ✅ | ✅ | ✅ |
| switch | ✅ | ✅ | ✅ |
| funções (todas as formas) | ✅ | ✅ | ✅ |
| classes, campos, métodos | ✅ | ✅ | ✅ |
| `constructor(...)` e primary `class X(...)` | ✅ | ✅ | ✅ |
| records (toString/equals/hashCode) | ✅ | ✅ | ✅ |
| herança, `super`, override, virtual dispatch | ✅ | ✅* | ✅ | Native: `super.metodo()` = SUP001 |
| interfaces | ✅ | ✅ | ✅ |
| generics por erasure | ✅ | ✅ | ✅ |
| lambdas `(x: Int) -> expr` + capturas | ✅ | ✅ | ✅ |
| exceptions reais (try/catch/finally + unwinding) | ✅ | ✅ | ✅ |
| `assert(cond[, msg])` | ✅ | ✅ | ✅ |
| `spawn` (concorrência, join implícito) | ✅ | ✅ (pthread, 31/08) | ✅ |
| strings (concat `+`, `==`, indexOf, trim, split...) | ✅ | ✅ | ✅ |
| arrays | ✅ | ✅ | ✅ |
| `List<T>`, `listOf`, `map/filter/reduce` | ✅ | ✅ | ✅ |
| `Box<T>` generics com `T` primitivo/Boxed (ex.: `Box<Int>`) | ✅ | ✅ | ✅ | 25/08 fix `substituteTypeVariable` |
| JSON encode/decode (objetos/records no JVM) + arrays nativos | ✅ | ✅ | ✅ |
| JSON decode `List<User>` (objetos aninhados) | ✅ | — | ✅ |
| kof.io (File/Path/Directory, readFile, writeFile) | ✅ | ✅ | ✅ |
| kof.time (now/sleep/interval) | ✅ | ✅ (now/sleep/**interval** — reusa o scheduler, SCHED001) | ✅ (now/sleep/**interval** — fila cooperativa bombeada por `time.sleep` no GraalJS; `setInterval` no browser/Node, TIME001 fechado 02/09) |
| kof.web (`web.app()`, rotas, middleware, WebSocket/SSE, `configure`/`stats`) | ✅ | — | — |
| kof.http (`http.get/post/put/delete/status` + `timeout/retry/circuit`) | ✅ | ✅ **HTTP002 fechado 03/09** (`NativeHttpRuntime` — HTTP/1.1 asm, IPv4; https → throw claro; retry/circuit no-op) | ✅ (27/08 JS via `Java HttpClient` interop; 30/08 retry/circuit paridade) |
| kof.config (env, arquivos, profiles, typed) | ✅ | ✅ (asm próprio) | ✅ |
| kof.mq (publish/subscribe/queue) | ✅ | ✅ (01/09, pub/sub + filas in-process, asm) | ✅ |
| kof.log (`log.info/warn/error/debug`) | ✅ | ✅ (asm; UTC, sem JSON) | LOG001 |
| kof.security (passwords, crypto, JWT, secrets) | ✅ | ✅ | ✅ |
| kof.db (JDBC, query<T>, transaction) + SQLite nativo | ✅ | ✅ (SQLite + transaction; MySQL WIP) | DB001 |
| kof.orm (entity, CRUD, where, migrate, MongoDB) | ✅ | ORM001 | ORM001 |
| String.toInt/toLong/toDouble/toFloat | ✅ | ✅ | ✅ |
| kof.ui (Color, Palette, Theme, Window) | ✅ | ✅ (JS render) | ✅ |
| default parameters em funções | ✅ | ✅ | ✅ |
| `readLine()` | ✅ | ✅ | ✅ |
| `KofCcompiler` C subset → nativo | — | ✅ (27/08) | — |
| `KofScript` top-level `var`/`val` → `KofScriptGlobals` (execução direta via `KofInterpreter`) | ✅ | ✅ | ✅ |

### Concorrência (`spawn`)

```kof
spawn processarFila()
spawn {
    println("background")
}
```

- JVM: virtual threads; o programa espera as tarefas (join implícito).
- Native: pthread_create + trampoline + `await`/pthread_join + allocator
  thread-safe (futex) + `done`/`poll`/`cancel`/`cancelled`/`selectAny` — ✅ 31/08 (CONC001 fechado).
- JS: concorrência real via `async`/`await`/`Promise` do GraalJS — `CONC003`
  **fechado de fato 03/09** (a marcação anterior `7402101` era sobre código
  morto no lowering, não a feature; `spawn`/`await`/`channel<T>()` agora
  deferem de verdade via microtask, `KofJsRunner` drena `kofActiveTasks` até
  todas as tasks terminarem — ver `docs/concurrency.md` seção 4,
  `docs/targets/KOFJS.md`).
- Zero API de plataforma exposta (Thread/Runnable são internos do runtime).
- Ver: `docs/concurrency.md`.

### HTTP (`kof serve`)

API legada (handler top-level):

```kof
handle(String method, String path, String body, String query, String headers): String {
    if (path == "/hello") {
        return "{\"msg\": \"hi\"}"
    }
    return null   // 404
}
```

Stack web nativa (Fase 1 — independência do Spring):

```kof
record User(String name, Int age)

main() {
    var app = web.app()
    app.use {
        if (header("x-auth") == "secret") {
            return null
        }
        return "{\"error\": \"unauthorized\"}"
    }
    app.get("/hello") {
        return "Hello from Kof"
    }
    app.get("/users/:id") {
        return "user " + param("id") + " q=" + query("name")
    }
    app.post("/user") {
        var user = json.decode<User>(body())
        return json.encode(user)
    }
    app.listen(8080)
}
```

- `web.app()` + rotas com lambda trailing; path params (`:id`), query,
  headers, body, `method()`, `path()`; middleware `app.use { ... }`.
- Engine HTTP gerado dentro do runtime do programa (sem servlet container,
  sem Spring); cada conexão em virtual thread.
- `app.configure(...)` / `app.stats(...)` (JVM, 04/09): connection cap,
  limites configuráveis e contadores SSE/WebSocket.
- `kof serve <file.kf>` detecta `main()` e executa apps `web.app()`;
  a API legada `handle(...)` continua funcionando.
- Ver: `docs/stdlib-web.md` e `KofWebE2ETest` (9 testes E2E com sockets reais).

### Media (`kof.media`) — arquivos, não strings

A linguagem NÃO transporta imagem/áudio como `String` gigante (nem base64
literal no fonte, nem data-URI colado à mão — o padrão que o
Kof-editor-theme-maker era forçado a adotar com `pageCss(): String` e
`kofPngData(): String`). O app trata o ARQUIVO:

```kof
main() {
    var app = web.app()
    app.serveDir("/img", "assets")      // GET /img/logo.png → bytes do disco, image/png
    app.get("/thumb") {
        var img = Image.open("assets/logo.png")   // javax.imageio
        img.saveAs("assets/thumb.jpg", "jpeg")
        return "w=" + img.width() + " h=" + img.height()
    }
    app.get("/rec") {
        var m = Mic.record(2)            // javax.sound.sampled (16kHz mono PCM)
        m.saveWav("assets/gravacao.wav")
        return "ms=" + m.durationMs()
    }
    app.get("/clip") {
        var v = Video.open("assets/clip.mp4")
        return "ms=" + v.durationMs() + " " + v.format()
    }
    app.serveDir("/media", "assets")      // Range 206 p/ <video> no browser
    app.listen(8080)
}
```

- **`Image`** (`ImageData`): `open` (PNG/JPEG/GIF/BMP), `width/height/format`,
  `save`, `saveAs(path, fmt)`, `bytes`/`bytesAs`, `dataUri` (opcional, em
  runtime — nunca literal no fonte), `close`.
- **`Audio`**: `openWav`/`saveWav` (WAV RIFF PCM 16-bit), `sampleRate`,
  `durationMs`, `pcmBytes`.
- **`Mic`**: `record(seconds)` do microfone padrão, `list()`.
- **`Video`**: `open` + metadados do container (`path/size/format/durationMs`,
  MP4/MOV lidos do box `mvhd`; outros containers → 0) + `bytes`/`close`.
  O app NÃO decodifica frames — sem lib externa no JVM (gap honesto); a API
  serve o arquivo (serveDir + Range) para o navegador reproduzir.
- **`app.serveDir(prefix, dir)`** (`web`): fallback de rotas dinâmicas —
  devolve o ARQUIVO em binário com content-type pela extensão (HTML/CSS/JS/
  imagens/áudio/**vídeo**/fontes/PDF...), `Cache-Control`, proteção contra
  path traversal e **Range requests** (`206 Partial Content` + `Content-Range`
  + `Accept-Ranges: bytes`, `416` para range inválido) — necessário para
  `<video>`/`<audio>` navegarem/seekarem no browser. Sem isso, o app só
  tinha `String` por rota → CSS/HTML/imagens viravam strings concatenadas e
  base64 colado no fonte.
- Caminhos relativos resolvem contra a raiz do projeto (`-Dkof.root`,
  definido pelo CLI `run`/`serve` como o diretório do `.kf`).
- **Targets**: JVM (javax.imageio + javax.sound; vídeo como container +
  streaming). **Gaps honestos**: decodificação de frames de vídeo (sem lib
  externa), câmera (MEDIA002), mic sem hardware (MEDIA003), paridade
  Native/JS (MEDIA001 — ART sem javax.imageio; app Android roda no WebView
  KofJS).
- Ver: `KofMediaE2ETest` (12 testes: serving binário byte-a-byte,
  content-type, traversal bloqueado, 404, dimensões reais, conversão
  PNG→JPEG, WAV info/copy, mic sem hardware, metadados de MP4, Range
  206/416/200).

### Configuração nativa (`kof.config`)

```kof
main() {
    var port = config.int("server.port", 8080)
    var url = config.str("database.url", "jdbc:h2:mem")
    var debug = config.bool("app.debug", false)
    var home = config.env("HOME")
    if (config.has("database.url")) { ... }
}
```

- Precedência: arquivo explícito (`KOF_CONFIG`) > env `KOF_<KEY>` >
  profile (`kof.<KOF_PROFILE>.config`) > arquivo padrão (`kof.config`).
- Tipagem em compile-time; valores ausentes/inválidos → default.
- Native: implementação asm própria completa — precedência total
  (KOF_CONFIG > env KOF_<KEY> > perfil > kof.config), typed com default
  em valor inválido, trim e comentários (`NativeConfigE2ETest`, 8 testes).
  JS reporta `CONF001`. Docs: `docs/stdlib-config.md`
  (`KofConfigE2ETest`, 8 E2E).

### Logging nativo (`kof.log`)

```kof
log.debug("detail")
log.info("request started")
log.warn("slow response")
log.error("failed: " + message)
```

- Formato `timestamp LEVEL mensagem`; info/debug → stdout, warn/error →
  stderr; nível via `KOF_LOG_LEVEL` (debug < info < warn < error < off).
- Funciona dentro de handlers web. **Native**: implementação asm própria
  (data civil Hinnant, env scan próprio) — timestamp UTC e `KOF_LOG_JSON`
  sem efeito por enquanto; JS reporta `LOG001`. Docs: `docs/stdlib-logging.md`
  (`KofLogE2ETest` 10 JVM + `NativeLogE2ETest` 7).

### Testes da linguagem (G6 — suíte estruturada)

```kof
test "soma simples" {
    assert(2 + 2 == 4)
}

test "string igual" {
    assert("kof" == "kof", "strings iguais")
}

main() { /* ignorado pelo kof test */ }
```

- `test "nome" { }` vira função em compile-time (desugar → `kof_test_N`);
  o runner é sintetizado pelo compilador — zero reflection.
- `kof test <file.kf|dir> [--target jvm|native|js]` reporta
  `PASS nome` / `FAIL nome: mensagem` + resumo; exit code ≠ 0 se houver
  falha. Cada teste roda isolado (try/catch por teste).
- Arquivos sem blocos `test` mantêm o contrato antigo (PASS/FAIL por
  exit code do programa inteiro).
- **process.exit(code)**: primitivo novo nos 3 targets (JVM System.exit,
  Native syscall, JS sentinel no KofJsRunner) — sem stack trace.
- G7 fechado: `jwt.*` tem entrada explícita na matriz de targets — Native
  reporta `SECN004` em compile-time (antes: erro de link silencioso).
- Ver: `learn/23-testing.md`, `StructuredTestE2ETest` (11 testes).

---

## Testes (962 = 941 kof-compiler + 8 kof-script + 5 kof-c-compiler + 8 kof-cli — medição real 05/09 pós-sweep NATIVE002-stdlib; suíte completa verde, 3 skips condicionais de toolchain/hardware)

| Suíte | Quantidade | Cobertura |
|-------|-----------|-----------|
| CompilerDriverTest | 190 | compilação, semântica, fases, isolamento |
| NativeE2ETest | 50 | execução real de binários nativos |
| KofJsE2ETest | 37 | execução real JS (GraalJS) + short-circuit `&&`/`||` vs bitwise |
| JvmE2ETest | 29 | execução real de bytecode JVM |
| KofSecurityTest | 25 | kof.security: senhas, crypto, JWT, secrets, adversariais |
| OptimizerTest | 21 | passes de otimização da IR |
| KofOrmE2ETest | 22 | kof.orm: entity, CRUD, where (+ORM003 validação de coluna tipada, P3-10), **Query DSL `User.query(db){ where; orderBy; limit }` (nível 3, ORM001)**, migrate, unique, MongoDB (3 skips condicional) |
| KofConcurrency2Test | 18 | spawn stmt/expr, selectAny, cancel/cancelled, done/poll, awaitTimeout, channel (+`Channel<T>` como parâmetro de função, 3 targets) |
| IoE2ETest | 16 | kof.io multiplatform (+ `readText`/`size` contratos honestos 02/09) |

| ComponentCoreE2ETest | 14 | kof.ui Component: view/onMount/onDispose |
| CoreRegressionE2ETest | 14 | regressões de uso real (BOM, toInt, ARITH001...) |
| JsonE2ETest | 14 | JSON JVM + Native |
| UiE2ETest | 14 | kof.ui: widgets, estilo, bindings, múltiplas janelas |
| AndroidInteropE2ETest | 11 | android: interop Java (external classpath) |
| KofConfigE2ETest | 11 | kof.config: env, arquivo, profiles, precedência, typed, CONF001 |
| KofWebWsE2ETest | 11 | WebSocket RFC 6455: handshake + frame + lifecycle |
| StructuredTestE2ETest | 11 | test "nome" {} nos 3 targets + process.exit |
| BackendParityTest | 10 | paridade JVM/Native/JS |
| KofLogE2ETest | 10 | kof.log JVM: níveis, stderr, off, JSON, correlation |
| KofPatternMatchingTest | 10 | switch case String s / Point(x,y) 3 targets |
| KofWebE2ETest | 10 | stack web nativa (web.app, rotas, JSON, middleware, `app.health` bypass) |
| ExceptionsE2ETest | 9 | try/catch/finally JVM + Native |
| KofDbE2ETest | 11 | kof.db: JDBC, query<T>, transaction, rollback, SQLite nativo, transaction Native (commit+rollback), DB001 |
| KofHttpServerTest | 8 | serve engine (sockets reais) |
| KofMediaE2ETest | 12 | kof.media + serveDir: Image/Audio/WAV/Video(MP4), Range 206/416, conteúdo binário (não base64) |
| NativeConfigE2ETest | 8 | kof.config Native (asm): precedência, typed, comentários |
| SpawnE2ETest | 8 | spawn (JVM/Native pthread/JS seq) + join implícito + **lambda c/ captura** + **println antes de spawn** + **`spawn→await→spawn`** (alinhamento de stack no `pthread_create`) |
| IdiomaticE2ETest | 7 | idiomas consolidados (chaining, primary ctor) |
| JsonCompleteE2ETest | 7 | JSON completo: Float/Double, arrays decode (JVM) |
| KofAwaitTest | 7 | spawn/await Handle<T> tipado (JVM) |
| KofWebSseE2ETest | 7 | SSE: sse.send/event/close (sockets reais) |
| KofWsFrameTest | 7 | frame codec RFC 6455: máscara, limites, ping/pong |
| NativeLogE2ETest | 7 | kof.log Native (asm): níveis, stderr, formato civil, off |
| IdiomaticCoreE2ETest | 6 | field initializers, \u810810, listOf<T>() |
| PackagesE2ETest | 6 | pacotes/módulos multi-arquivo (import a.b.C + moduleRoot do LCA, P1-4) |
| AssertE2ETest | 5 | assert JVM + Native |
| FloatingPointGapE2ETest | 5 | FP XMM: encode/decode/arrays (FLT001) |
| KofCacheE2ETest | 5 | suíte E2E/compilação |
| KofHigherOrderTest | 5 | funções de ordem superior (map/filter/reduce) |
| KofIntOverflowNativeTest | 5 | aritmética Int 32 bits no Native |
| KofTimeE2ETest | 5 | time now/sleep/interval (JVM/Native/**JS** — TIME001 fechado 02/09: fila cooperativa bombeada por `time.sleep` no GraalJS) |
| KofWebTlsTest | 5 | TLS/HTTPS: listenSecure + kof.http sobre TLS |
| KofObservabilityTest | 4 | health/metrics/histogram/requestId/traceId+spanId (W3C) (JVM/Native/JS) |
| FunctionSyntaxTest | 4 | formas de declaração de função |
| KofEnumSwitchTest | 4 | switch exaustivo sobre enum + SEM031 |
| KofEnumTest | 4 | enum: values/valueOf/name, SEM030, mapeamento JVM |
| KofHttpE2ETest | 4 | kof.http client (sockets reais, JVM + JS) |
| KofMqE2ETest | 4 | kof.mq publish/subscribe/queue (JVM+Native+JS — MQ001 fechado 01/09) |
| KofWebStreamE2ETest | 4 | WebSocket/SSE end-to-end (persistent-conn) |
| LambdaE2ETest | 4 | lambdas + if-expr |
| RouterE2ETest | 4 | kof.ui Router Fase 7: go/replace/back/forward |
| StdlibE2ETest | 4 | now/readFile/writeFile |
| KofJsBrowserE2ETest | 1 | **KofJS no browser real** (Chrome headless + HTTP + DOM) — kof.ui renderiza de verdade (pula se Chrome ausente) |
| KofJsSourceMapTest | 1 | **source map V3 do KofJS** (mappings VLQ reais, nível de linha: função gerada → linha Kof via `KofDebugInfo`; antes era stub `"mappings":""`) |
| ConfigGenTest | 3 | kof config gen: template kof.config do código |
| KofHttpResilienceE2ETest | 3 | kof.http timeout/retry/circuit (JVM + JS paridade) |
| KofMapSetTest | 10 | Map/Set 3 targets (asm próprio no Native) + `Set<T>`/`Map<K,V>` como campo/retorno de classe (JVM: `NoClassDefFoundError` → `HashSet`/`HashMap`; parse de método de classe c/ retorno genérico) + `Map.get` → `V?` (02/09) |
 | KofObservabilityTest | 5 | health/metrics/histogram/requestId/traceId+spanId (W3C) (JVM/Native/JS; Native histogram = gap OBS002) |

| KofSecurityG9Test | 3 | web security: rateLimit/session/apiKey |
| KofValidationTest | 3 | 13 predicados de validação (3 targets) |
| TetrisEasterEggTest | 3 | registro easter egg oculto |
| TuringCompleteE2ETest | 3 | completude de Turing (loops/while/recursão) |
| WindowE2ETest | 3 | Window: size, close-to-exit |
| DebugInfoE2ETest | 2 | SourceFile + LineNumberTable (JVM) |
| IRStatisticsTest | 2 | observer de IR + estatísticas de otimização |
| NativeDebugTest | 1 | harnesses de debug nativo |
| NativeDebugTest2 | 1 | harnesses de debug nativo (2) |
| NativeDebugTest3 | 1 | harnesses de debug nativo (3) |
| NativeDebugTest4 | 1 | harnesses de debug nativo (4) |
| NativeDebugTest5 | 1 | harnesses de debug nativo (5) |
 | NativeDwarfLineInfoTest | 1 | **DWARF nativo**: `.debug_line` real no binário (`objdump --dwarf=decodedline` → arquivo Kof + linha por instrução) |
| NullSafetyE2ETest | 7 | `String?` narrowing JVM + readLine EOF null (02/09) |
  | NativeRiscv64E2ETest | 26 | **riscv64 real (qemu)**: runtime em **asm puro** (raw syscalls, sem C; `as`+`ld` estático) — core (println, var, if/else, aritmética, classes, arrays, List, switch, try/catch, pattern matching, String methods, recursão) + **stdlib 05/09**: JSON (encode/decode incl. escalares int/long/bool/string), HTTP, spawn/await, cache, time.now, mq (queue/pub-sub), Map/Set, higher-order (map/filter/reduce), String.toInt, metrics `# TYPE`, FP (conversões; `println(double)`→FLT001), gates honestos DB001/SECN000/SCHED001/TIME001 |
  | NativeAarch64E2ETest | 26 | **aarch64 real (qemu)**: runtime em **asm puro** via tradução riscv→aarch64 (`translateRiscvToAarch64`), raw syscalls — mesmo core + stdlib do riscv64 (tradutor quote-aware p/ strings com `#`) |
 | **Total kof-compiler** | **823** | |
 | kof-script | 8 | KofScriptGlobals / repl / --watch |
 | kof-c-compiler | 5 | KofC C subset → ELF |
 | kof-cli | 4 | LSP references + rename (mock) |
 | **Total** | **840** (+31 skips condicionais: Mongo/MySQL/Postgres, windows/mac; conferir total no CI a cada release) | |
## Consolidação idiomática (guidelines 0.0.5)

Princípio: `intenção → Kof → compiler → backend` — nunca detalhes da
plataforma vazando para a linguagem.

| Guideline | Estado |
|-----------|--------|
| `User(...)` sem `new` (retrocompatível) | ✅ |
| Primary constructor `class User(String name)` | ✅ |
| `this` não obrigatório | ✅ |
| Field initializers aplicados no construtor | ✅ (0.0.5) |
| Resolução de métodos independente da ordem textual | ✅ |
| Escapes `\n` `\t` `\r` `\u810810` | ✅ (0.0.5) |
| `listOf<T>()` vazio preserva o tipo | ✅ (0.0.5) |
| `List<User>` + for-in tipado | ✅ |
| `++`/`--` em campos | ✅ |
| `return` nu em void | ✅ |
| lambdas com capturas | ✅ (sem testes dedicados ainda) |
| args CLI (`main(args)`) | ✅ |
| default parameters | ✅ |
| módulos multi-arquivo | ✅ (resolução unificada: import a.b.C + moduleRoot do LCA) |
| `Process` API | ✅ (`kof.process` + `kof_process_run`) |

Ver as guidelines completas no todo da sessão.

---

## Kof Debugger (em progresso)

Princípio: o programador depura **código Kof**, nunca o artefato do backend.

| Fase | Estado |
|------|--------|
| 1 — DebugInfo na IR (source location por op) | ✅ |
| 2 — JVM: SourceFile + LineNumberTable + LocalVariableTable | ✅ |
| 3 — `kof-debug` MVP (DAP over stdio + JDWP cru): launch, breakpoints por linha Kof, `stopped`, stack trace com funções/linhas Kof, continue, disconnect | ✅ |
| 4 — Kof Editor (breakpoints, toolbar, variables) | planejado |
| 5 — Native (DWARF) | ✅ parcial 02/09 (line info real: `.debug_line` via `.file`/`.loc` GAS — arquivo Kof + linha por instrução, `objdump --dwarf=decodedline`; `NativeDwarfLineInfoTest`. Variáveis locais/expressões e breakpoints DAP no nativo pendentes) |
| 6 — JS (source maps) | ✅ parcial 01/09 (source map V3 em nível de linha: função gerada → linha Kof, `KofJsSourceMapTest`; colunas/expressões pendentes) |
| 7 — Avançado: locals por frame, stepping, exception breakpoints, avaliação | planejado |

`kof debug app.kf` já abre uma sessão DAP funcional no target JVM:
a sessão compila com metadata de debug, lança o JVM com JDWP e responde a
`initialize` / `launch` / `setBreakpoints` / `configurationDone` /
`continue` / `threads` / `stackTrace` / `disconnect` — o breakpoint
para na linha Kof e o call stack mostra funções e linhas Kof.

Docs: `debugger-architecture.md`, `debugging.md`, `debug-adapter.md`,
`debugging-jvm.md`, `debugging-native.md`, `debugging-js.md`.

---

## Bugs Restantes (reais)

> **Lista completa com reprodução + correção sugerida: `docs/known-bugs.md`**
> (23+ bugs verificados 02/09 — rodada 3 de usuários: kof-ui reutiliza ID de
> widget após remove, lambda→lambda e lambda-em-lista invocados quebram, PKG005
> rejeita nomes iguais em pacotes diferentes, Native perde construtor de
> classe de outro pacote (undefined reference), ExternalClasspath não resolve
> superclasse fora dos entries).

1. ~~GC automático no Native~~ — ✅ sweep real 03/09 (`kof_gc_sweep` fechado);
   **auto-collect pendente**: safe-points exigidos (chamar de dentro de
   `kof_alloc` sem mapa de raízes = double-free). `kof_gc_collect_now`
   disponível pra uso explícito futuro
2. ~~`spawn` no Native: CONC001~~ — ✅ fechado 31/08: pthread_create + trampoline + await/pthread_join + allocator thread-safe (futex) + join implícito + `done`/`poll`/`cancel`/`cancelled`/`selectAny` (cancel cooperativo por TID + selectAny polling 1ms; `SemanticAnalyzer` desambigua `cancel(Handle<T>)→Bool` vs `scheduler.cancel(String)→VOID`)
   - ✅ ~~bug pré-existente SEPARADO: `spawn→await→spawn` SIGSEGV no 2º `pthread_create`~~ — **resolvido 01/09**: mesmo mecanismo do println-antes-do-spawn. O site do `call pthread_create` exige `rsp ≡ 0 (mod 16)` pela ABI SysV; após `pthread_join` (do `await`) a stack chegava 8 bytes desalinhada e a glibc segfaultava em `pthread_attr_copy`. Alinhamento de stack no C call (`andq $-16, %rsp` em `kof_spawn_handle_new`, preservando `r15` + frame do caller). `SpawnE2ETest.nativeSpawnAwaitSpawnDoesNotSegfault` (sem o fix: SIGSEGV 3/3; com: ok 3/3). **Nota**: alinhamento já tinha sido auditado "conforme ABI" e descartado como causa numa sessão anterior — a medição agora crava que o site do `call pthread_create` efetivamente chegava desalinhado nos casos com output/join antes do spawn.
3. ~~JSON de objetos/records no Native: JSN002~~ — ✅ fechado (composição compile-time)
4. ~~JSON Float/Double: JSN001~~ — ✅ fechado 31/08 (parser FP completo: fração+expoente, arrays Double[])
5. ~~JSON decode de arrays~~ — ✅ JSN003 fechado: Int[]/Long[]/Bool[]/String[]; JSN001 fechou Double[]/Float[] (31/08)
6. ~~Lambdas sem captura~~ — ✅ captura implementada (mutable via box `BoxN`; `Lambda0`/`Box0`)
7. ~~Generics `Box<T>` com println nativo~~ — ✅ 25/08 `Box<Int>`/`T` substituído + `kof_int_to_string`
8. ~~`SEM025` falso-positivo em `hashCode/equals/toString`~~ — ✅ `isObjectMethod` em 25/08
9. ~~`await`/join~~ — ✅ nos 3 targets (JVM virtual threads, JS sequencial, Native pthread)
10. ~~`kof fmt`: planned (P5)~~ — ✅ implementado: `kof fmt` via parser real
    (`KofFormatter`), idempotente (2c3e794)
11. ~~Map/Set~~ — ✅ `List.map/filter/reduce` + `Map/Set` JVM/Native/JS (26/08)
12. Pattern matching: ✅ `switch (x) { case String s: ... }` + `case Point(x,y)` em `Parser/Semantic/CompilerDriver` + `Native rbx→rcx` + `JS typeof` (27/08 `Point(x,y)` `JVM:30 Native:30 JS:30` `KofPatternMatchingTest 10/10` + `KofWebE2ETest 9/9`)
13. Null safety `String?`: ✅ básica `String?` `Int?` `?`-check em compile-time `Type.NullableType` `JvmBackend:110` `SemanticAnalyzer:1637` `isAssignable` `var s:String?=null` `s==null` `t="hello"` `jvm: null/hello native: null/hello js: null/hello` (27/08)
14. ~~Módulos multi-arquivo imports perdidos em projetos grandes~~ — ✅ 27/08 `CompilerDriver.java:243` `import a.b.C` file import `+` `a.b` dir import, `largeproj` `a/b/C.kf` `decls=2` `Main.class+a/b/C.class` ok
15. ~~`List.get` native~~ — ✅ verificado `listOf(1,2,3).get(1) → 2` nativo `kof_list_get` bounds OK (caso `List.of` era `listOf`)
16. Web: status codes/headers customizados por handler: ✅ `kof.web.status(201, body)` + `headerSet("X","y")` em `KofWeb.java:107` + `JvmWebRuntime.java:22` `KOF_WEB_STATUS/HEADERS` + `JvmRuntime.java:489` `kof_web_dispatch` `+wired` `kof_web_build` headers `+wired` `status_text 201 Created 202 Accepted` `JVM: 201/hellox 202/value` `KofWebE2ETest 9/9` (27/08)
17. ~~Web: kof.web nativo sem servidor~~ — ✅ fechado 03/09 WEB002 (T1-T4 `NativeWebRuntime.java`): accept loop HTTP/1.1 com parse de request-line, match literal de rotas, dispatch handler via trampolim vtable[0], body() da request; suíte `KofWebNativeE2ETest` 4/4. Pendente: path params `{id}`, `param()/query()/header()`, keep-alive (Connection: close por request), SSE/WS (WEB003/4), TLS (WEB002-secure).
18. ~~MySQL/MariaDB no Native: wire protocol~~ — ✅ 31/08: handshake + scramble SHA-1 + auth-switch + COM_QUERY + resultset (coldefs/rows/EOF) + **binds `?`** — e ✅ 03/09: **prepared statements binários (COM_STMT_PREPARE/EXECUTE)** reais. `kof_db_mysql_prepare`/`kof_db_mysql_exec`/`kof_db_mysql_prep_query` em `NativeDbPrepared.java` (módulo novo, ≤500 linhas): PREPARE (0x16) → OK + drena metadata (params coldefs + EOF, cols coldefs + EOF, capturando name+type), EXECUTE (0x17, null-bitmap + type pairs + valores crus Int 4B/8B, strings lenenc); parse de binary-rows no resultset. `db.execute`/`db.query` com binds usam o binário; fallback COM_QUERY substituição só se PREPARE falhar. Binds com aspas/SQL-injection intactos (sem escape manual). Validado contra MySQL 8.0 real (127.0.0.1:13306), strace confirma 0x16/0x17 na wire. `KofDbE2ETest` 12/12 (+ `nativeMysqlPreparedBinary`). (01/09 reverso; 03/09 resolvido com `NativeDbPrepared.java` ≤500 linhas).
19. ~~`kof_sec_secret_get` no Native~~ — ✅ resolvido: reescrito no padrão linear dos demais; segfault e fragmentos errados eliminados.
20. ~~Ponto flutuante no Native~~ — ✅ FLT001 fechado: FP é XMM real (`vcvtsi2sd`, `mulsd`); dtoa via snprintf alinhado; `kof_string_to_double` parse completo (fração+expoente).
21. ~~idem~~
22. riscv64/aarch64 **core completo** — ✅ **02/09 riscv64 + 03/09 aarch64 reais**: `Target.NATIVE_RISCV64`/`NATIVE_AARCH64` + CLI `native.risc`/`native.arm` + dispatch + **lowering real** (stack machine: riscv64 `sp`/`s11`/`ra`, aarch64 `sp`/`x29`/`x30` via tradução linha-a-linha) + **runtime em asm puro** (raw syscalls `write` 64 / `exit` 93, bump allocator, sem C — binários estáticos via `as`+`ld`; Kof é Kof) + qemu; `NativeRiscv64E2ETest 26/26` + `NativeAarch64E2ETest 26/26` (core: println String/Int, var, if/else, aritmética, classes virtual/fields, arrays, List, switch, try/catch/throw, pattern matching, String methods, recursão). **Paridade stdlib 05/09**: JSON (encode/decode escalares+listas), HTTP, spawn/await (clone+futex), cache, time.now, mq, Map/Set, higher-order, String.toInt, metrics `# TYPE`, FP-conversões — sweeps de paridade com **0 divergências** nos 3 targets. **Restante do NATIVE002**: DB (libsqlite3 exige libc → gate DB001), security (SHA/AES/JWT em asm → gate SECN000; R11: cripto caseira é non-goal), scheduler/time.interval (thread de timer em asm → gates SCHED001/TIME001), UI/net. **Estado real + como finalizar: `docs/native-multiarch.md`** (gap `NATIVE002`)
23. ~~`kof.cache` nativo: segfault em `set_ttl` (index `%rax` clobberado) + `get/ttl` (exp em `%rdi` clobberado) + `println(null)` segfault~~ — ✅ 30/08: registradores preservados (`%r14/%r13/%r15`), branch `jle` de expiração corrigido, `kof_print_string` guarda null, `find_slot` sobrescreve chave existente; `KofCacheE2ETest 5/5 x3 targets`
24. ~~`spawn`-statement (fire-and-forget) no Native não era juntado~~ — ✅ 01/09: o `kof_spawn` (stmt) criava a thread mas **não registrava** o handle na lista que `kof_spawn_join_all` percorre; e `join_all` só era emitido no bloco `!endsWithReturn`, que nunca roda para o main (o driver sempre fecha o main com `KofReturnVoid` → `endsWithReturn`). Resultado: o processo saía antes do worker imprimir. Fix: `kof_spawn` agora delega a `kof_spawn_result` (registra o handle) e `join_all` é emitido no **epílogo do return** de main (idempotente — limpa a lista). `SpawnE2ETest 4/4`
25. **`throw <não-String>` / `catch <não-String>` gera bytecode inválido no JVM** (documentado 02/09) — `throw 42` compila mas o `.class` falha no load (`ClassFormatError`, disfarçado de "JavaFX launcher error"). Exceções são Strings; o compilador deveria **rejeitar** `throw <não-String>` em compile-time. Reprodução + arquivos prováveis em **`docs/known-bugs.md` #1**.
26. **Captura mutável no Native: ler variável boxeada DENTRO da lambda após mutação EXTERNA produz lixo** (documentado 02/09) — `var f = (x) -> x + offset; offset = 20; f(5)` retorna ponteiro/offset no Native (JVM correto). A direção "lambda escreve" funciona. `NativeBackend.resolveFieldOffset` resolve o layout do box contra a classe da lambda (fallback HEADER_SIZE). Reprodução + arquivos em **`docs/known-bugs.md` #2**.

---

## Próximos Passos (ordem P1→P5)

**P1 — Linguagem (em progresso):**
1. ✅ `Map/Set` + `enum` + `await` + `List.map/filter/reduce` (JVM/Native/JS)
2. ✅ `Pattern matching` — `switch (x) { case String s: ... }` + `case Point(x,y)` `JVM/Native/JS` `30` `10/10`
3. ✅ `Nullability` `String?`/`Int?` + `?`-check `Type.NullableType` `jvm/native/js null/hello` (27/08 básica)
4. ✅ `Módulos multi-arquivo` — `kof build <dir>` com resolução unificada: `import a.b.C` file fix done + `moduleRoot` derivado do **menor ancestral comum** das fontes (3-arg `compileSources` resolve imports cross-diretório sem raiz explícita; `PackagesE2ETest` 6/6)

**P2 — Web completa (próxima listinha):**
5. ✅ Resposta rica `status(201, body)`/`headerSet("X","y")` `JVM` `201 Created 202 Accepted` `X-Custom/X-Test` `KofWebE2ETest 9/9` (27/08) **`Native WEB002 parcial` (03/09 — server 200+body, headers customizados ainda são pendência)** `JS stub`
6. ✅ `kof.cache` `get/set/set(key,v,ttl)/ttl/delete/clear` — ✅ JVM/Native/JS (30/08; fix nativo: clobber de `%rax/%rdi` em `set_ttl/get/ttl` + `println(null)` segfault; `KofCacheE2ETest 5/5 x3 targets`)
7. ✅ `WebSocket` `app.ws("/chat") { }` + `SSE` `sse.send/event/close` — ✅ JVM (30/08; PRs 14-17: persistent-conn/route-kinds, SSE, handshake RFC 6455, frame codec+máscara; `KofWebSseE2ETest 7/7` `KofWebWsE2ETest 11/11` `KofWsFrameTest 7/7`; hardening/limites/contadores 04/09 — `KofWebHardeningTest 6/6`)
8. ✅ `Scheduler` `every(ms) { }`/`at(cron) { }`/`cancel(id)` — ✅ JVM (`ScheduledExecutor`, 27/08) + JS (`setInterval`) + **Native SCHED001** (31/08: thread por job — trampoline `usleep` ms→us + `active` flag com futex — `cancel(id)` cooperativo; `KofConcurrency2Test` `schedulerEveryNative/Jvm`)
9. ✅ `kof.http` `timeout`/`retry`/`circuit breaker` — ✅ JVM+JS (30/08; retry repete em exceção+HTTP 5xx, circuito abre após N falhas por 30s com fail-fast, `circuit(0)` recupera; `KofHttpResilienceE2ETest 3/3` JVM+JS) — falta `HTTP/2`

**P3 — Data produção:**
10. ✅ Query DSL tipada (nível 3) — validação **tipada** de coluna em `orm.where<T>`/`where_op`/`count` (`ORM003`) + **sintaxe `User.query(db) { where age > 25; orderBy name desc; limit 10 }`** (01/09): o compilador baixa o bloco para `db.query<T>` (SQL preparada em compile-time a partir do schema da entidade, identificadores quotados, valores como binds `?`; múltiplos `where` → `AND`; colunas inexistentes → `ORM003`, where sem comparação / operador não suportado / >4 binds → `ORM004`; o lowering é agnóstico de target (emite o mesmo `db.queryN` no JVM e no Native) e o E2E roda no JVM (H2) — o workflow de entidade no Native segue `ORM001` — `KofOrmE2ETest` 22)
11. Connection pooling + `kof.db`/`kof.orm` fora do JVM (JS via WASM, Native ORM sobre SQLite)
12. ~~MySQL/MariaDB nativo (handshake+query)~~ — ✅ 31/08 (wire protocol: handshake+scramble+auth-switch+COM_QUERY+resultset); ✅ 03/09 **prepared statements binários** (COM_STMT_PREPARE/EXECUTE + parse de binary-rows; ver #18)

 **P4 — Observabilidade:**
 13. ✅ Métricas `histogram` + endpoint `/metrics` (Prometheus) — ✅ 01/09: `observability.histogram(name, value)` (sum+count) + `observability.metrics()` exportando counters/gauges/histograms em **text exposition format** (JVM + JS + **Native** — `OBS002` fechado: store asm 32B + export via `kof_string_concat`, paridade de conteúdo com o JVM). O app expõe via `app.get("/metrics") { return observability.metrics() }` — sem endpoint especial.
  14. ✅ Health `app.health("/health")` + tracing leve — ✅ 01/09 `app.health(path)` (built-in, responde `{"status":"UP","ready":true,"alive":true}` **antes dos middlewares** — sonda de load balancer não passa por auth); `observability.health()/readiness()/liveness()` (3 targets). **Tracing W3C**: `observability.traceId()` (32 hex) + `observability.spanId()` (16 hex) — IDs puros, sem store, **3 targets** (JVM `SecureRandom`, JS `Math.random`, Native `getrandom`); **spans com timing** `spanStart/spanEnd` (JSON {traceId, spanId, durationMicros}, 3 targets — 01/09) + **lifecycle** `application { onStart/onShutdown }` (desugar → prólogo/epílogo do main, 3 targets — 01/09); `KofObservabilityTest.tracingJvmNativeJs` + `spansWithTiming` + `applicationLifecycle*`. **OpenTelemetry** (export/propagação completa) pendente

 **P5 — DX:**
 15. ✅ `kof fmt` (parser real) + `kof init` + `REPL` — ✅ todos implementados (`Fmt.java`, `init` em `Main.java:694`, `repl` em `Main.java:839`); `fmt` idempotente
  16. ✅ LSP hover/completion/**references**/**rename** + Debugger Native DWARF/JS source maps + VS Code extension — LSP hover/completion ✅ + `textDocument/references` + `textDocument/rename` (word-boundary, single-file; `LspServerTest` 4/4). **JS source maps V3 (nível de linha) ✅ 01/09** (`KofJsSourceMapTest`); Native DWARF + VS Code pendentes

## Roadmap — Estado por Fase (31/08)

### Concluído — Disponível

- Compiler foundation — Lexer, Parser, AST, Type system foundation, Semantic analysis, Kof IR
- JVM backend; Native backend (x86_64); JS backend (GraalJS)
- classes, records, inheritance, interfaces, constructors (sobrecarga), exceptions, generics, collections, string operations, control flow
- `kof build`, `kof run`, `kof serve`, `kof test`, `kof debug` (MVP JVM, DAP sobre stdio), `kof bench` (37 benchmarks + baselines), `kof fmt` (parser real, idempotente)
- `kof.web` — rotas e middleware (JVM); WebSocket RFC 6455 + SSE nativo (JVM, 0.2.6-beta); TLS/HTTPS `web.listenSecure` (JVM); limites/observabilidade `configure`/`stats`
- `kof.db` — JDBC + SQLite nativo; `kof.orm` — entity, CRUD, migrate, MongoDB (JVM)
- `kof.log` nativo; `kof.config` (arquivo > env > profile, tipado, `${key}`, 3 targets); `kof.mq` pub/sub (JVM)
- cliente HTTP (JVM) + JS via `Java HttpClient` interop + **Native 03/09** (`NativeHttpRuntime.java` — HTTP/1.1 asm: parse URL, socket+connect, request parse, status; https throw; DNS↦127.0.0.1 fallback) + retry/circuit (3 targets, 30/08)
- `kof.security` v1 (JVM/Native/JS); web security G9 — rateLimit, sessões, API keys (3 targets)
- `kof.validation` (13 predicados, 3 targets); `kof.observability` (health/métricas/request IDs, 3 targets); `kof.ui` widgets com render KofJS
- `kof.process` execução de processos externos; `process.spawn` stdin/stdout vivos (F10, JVM/JS)
- **Concorrência**: `spawn`/`await` JVM (virtual threads) + **Native (pthread — CONC001 fechado 31/08)** + **Android (platform threads — AND001 fechado 31/08, ART sem virtual threads → fallback)** + JS sequencial; `done`/`poll` não-bloqueantes; `cancel`/`cancelled` cooperativo (JVM + Native por TID); `selectAny` (JVM + Native + JS); `awaitTimeout(r, ms)` — valor no prazo, exceção capturável no estouro (JVM + Native; JS sequencial = paridade); `channel<T>()` com `send`/`receive` (JVM LinkedBlockingQueue + Native FIFO futex + JS array); `scheduler.every/at/cancel` (JVM `ScheduledExecutor` + JS `setInterval` + **Native SCHED001**: thread por job com trampoline `usleep` ms→us + flag `active` futex) — `KofConcurrency2Test` 15/15, `SpawnE2ETest` 5/5
- **`kof.media` (31/08)** — gestão de arquivos multimídia sem base64 literal: `Image.open/save/saveAs/dataUri` (javax.imageio, PNG/JPEG/GIF/BMP), `Audio.openWav/saveWav` (WAV RIFF PCM 16-bit), `Mic.record` (javax.sound.sampled), `Video.open` (metadados do container MP4/MOV + streaming); `web` `app.serveDir(prefix, dir)` serve ARQUIVO do disco com content-type correto + **Range requests (206/416)** p/ vídeo navegável + proteção de path-traversal; raiz do app via `-Dkof.root` (CLI `run`/`serve`). Gaps: frames de vídeo (sem lib externa), câmera (MEDIA002), sem hardware de mic (MEDIA003), paridade Native/JS (MEDIA001) — `KofMediaE2ETest` 12/12
- **KofAndroid Fase 2 (31/08)** — `--apk` standalone (aapt2/d8/zipalign/apksigner direto do CLI) + release signing `--keystore/--storepass/--keypass/--alias` + label/permissões derivados do programa (`detectAppLabel`/`@Permissions`)
- enum nos 3 targets + switch exaustivo (SEM031); Map/Set nos 3 targets (COL001 fechado)
- otimizador de IR sempre ativo; pattern matching (switch com tipos + destructuring, 3 targets); null safety básica (`String?`, 3 targets); higher-order em coleções (map/filter/reduce, 3 targets); módulos multi-arquivo (`import a.b.C`)
- KofScript — top-level let/const (`KofScriptGlobals`, repl, `--watch`); KofC compiler — C subset → ELF x86_64 (`kof c`)
- LSP com hover/completion + diagnostics reais; widening de return
- Native GC — mark-sweep 03/09 ✅: `kof_gc_mark` (stack+bss conservador) + `kof_gc_sweep` (limpa morto para free-list; flag bit1 @24) + `kof_gc_collect_now` (chamada externa, explicit); **auto-collect desligado** em `kof_alloc` (necessita safe-points/mapas de raízes por frame — senão double-free detectado). `KofGcE2ETest` 3/3
- Ponto flutuante real no Native (FLT001 fechado 31/08 — XMM); JSON objetos/records no Native (JSN002 fechado) + arrays FP (JSN001/003)
- releases multiplataforma (2 jobs: `test-and-bump` → `package-and-release`; linux-x86_64 / macos-arm64 / windows-x86_64)

### Em desenvolvimento

- Standard Library (contratos em estabilização)
- Async / Concurrency: ~~JS async real sobre Promises (CONC003)~~ — ✅ 03/09 (`async`/`await`/`Promise` do GraalJS, coloração async por fixpoint no compilador, `KofJsRunner` drena a fila de microtasks — ver `docs/concurrency.md`); ~~Android `AND001`~~ — ✅ 31/08 (platform threads no ART, fallback quando `Thread.startVirtualThread` ausente); ~~bug pré-existente `spawn→await→spawn`~~ — ✅ resolvido 01/09 (alinhamento de stack no `pthread_create` — ver "Bugs Restantes" #2)
- ~~KofAndroid Fase 2~~ — ✅ 31/08 (`--apk` standalone + `--keystore` release signing + label/permissões derivados do programa)
- ~~`kof.media` residual (31/08)~~ — ✅ 31/08: **video** (`Video.open` + metadados do container + streaming) e **Range requests** (206/416) fechados; restam câmera (MEDIA002 — sem lib externa no JVM) e paridade Native/JS (MEDIA001 — ART sem javax.imageio; app Android roda no WebView KofJS)
- MySQL/MariaDB nativo — **wire protocol ✅ 31/08** (handshake + scramble SHA-1 + auth-switch + COM_QUERY + resultset; binds `?` via substituição client-side; `nativeMysqlWireProtocol`) + **prepared statements binários ✅ 03/09** (COM_STMT_PREPARE/EXECUTE + binary-rows, `NativeDbPrepared` — ver "Bugs Restantes" #18)
- `native.risc` (riscv64) + `native.arm` (aarch64) **core completo (02-03/09)** — plumbing + codegen/runtimes em asm puro + qemu, `NativeRiscv64E2ETest 26/26` + `NativeAarch64E2ETest 26/26` (core + stdlib 05/09: JSON/HTTP/spawn/cache/time/mq/Map/Set/higher-order/toInt/metrics; gates DB001/SECN000/SCHED001/TIME001) — **detalhe + como finalizar: `docs/native-multiarch.md`** (gap `NATIVE002`)
- Debugger — MVP JVM (DAP sobre stdio) + **JS source maps V3 em nível de linha (01/09)**; Native DWARF pendente
- KofJS — plataforma web no browser (ES Modules via GraalJS já em alpha)

### Planejado

- package manager (`kof init`, `kofdeps`, registry)
- complete language specification; conformance suite
- query DSL tipada para o ORM (`User.query { where age > 18 }`)
- full web platform (frontend declarativo + routing/forms/SSR)
- **gRPC no `kof.web`** (31/08) — comunicação RPC gRPC como primeira classe da plataforma web: `app.grpc { service ... }` (stubs a partir de `.proto`, server streaming + unary sobre HTTP/2 no JVM) + client `grpc.call(endpoint, method, msg)`; codegen `.proto` → IR; parity JVM primeiro (ver `docs/roadmap.md` § web)
- auto-hospedagem (compilador escrito em Kof)

Roadmap completo: `docs/roadmap.md`; execução: `docs/plan-platform-completion.md`
