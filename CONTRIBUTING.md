# 30 — Contribuindo

> **Kof 0.2.6-beta — 02 set 2026 — 810 testes — targets jvm/native/native.risc/native.arm/js/kofc**

## Estrutura do repositório

```
kof/
├── kof-compiler/       ← compilador core (JVM/Native/JS + KofScript/KofC)
├── kof-cli/            ← CLI (build/run/script/c/test/bench/debug)
├── kof-script/         ← KofScript (let→KofScriptGlobals, repl, --watch)
├── kof-c-compiler/     ← KofC (C subset → ELF nativo-only)
├── kof-runtime/        ← runtime nativo (free-list GC)
├── docs/               ← documentação interna
├── learn/              ← este material (intention->Kof->frontend->IR->backend->runtime)
├── tests/              ← testes golden (810)
├── pom.xml             ← build Maven (0.2.6-beta)
└── README.md
```

## Buildando

```bash
mvn clean package -DskipTests
```

## Rodando testes

```bash
mvn test
```

## Estrutura do compilador

```
kof-compiler/src/main/java/dev/kof/compiler/
├── KofScript.java      ← KofScript eval/runFile/repl (let→Globals)
├── KofCCompiler.java   ← KofC C subset → ELF
├── KofFormatter.java   ← kof fmt (parser real, idempotente)
├── Lexer.java          ← lexer hand-written
├── Parser.java         ← parser recursivo descendente
├── AstNodes.java       ← nós da AST
├── SemanticAnalyzer.java ← análise semântica/type checking
├── Type.java           ← sistema de tipos
├── SymbolTable.java    ← tabela de símbolos
├── IRNodes.java        ← operações IR
├── Optimizer.java      ← passes de otimização da IR (sempre ativos)
├── CompilerDriver.java ← orquestrador
├── Backend.java        ← interface de backend
├── Target.java         ← enum de targets
├── JvmBackend.java     ← geração de bytecode JVM (ASM, V21)
├── JsBackend.java      ← geração de ES Modules (KofJS)
├── NativeBackend.java  ← geração de assembly x86-64
├── Kof*.java           ← namespaces stdlib (KofWeb, KofHttp, KofSecurity,
│                        KofUi, KofDb, KofOrm, KofConfig, KofLog, KofCache...)
├── JvmRuntime.java     ← runtime JVM (KofRuntime gerado)
├── Diagnostic.java     ← diagnósticos
├── DiagnosticCollector.java
├── CompilationResult.java
├── Token.java          ← representação de token
├── TokenType.java      ← enum de tipos de token
└── SourcePosition.java ← posição no código
```

## Como adicionar uma feature

### 1. Lexer

Se a feature precisa de uma nova keyword ou operador:

- Adicione o token em `TokenType.java`
- Adicione o reconhecimento em `Lexer.java`

### 2. Parser

Se a feature precisa de nova sintaxe:

- Adicione o nó AST em `AstNodes.java`
- Adicione o parsing em `Parser.java`

### 3. Lowering

Se a feature precisa gerar IR:

- Adicione operações IR em `IRNodes.java` (se necessário)
- Adicione o lowering em `CompilerDriver.java`

### 4. Backend

Se a feature precisa de novas instruções:

- Para JVM: adicione o emission em `JvmBackend.java`
- Para nativo: adicione o emission em `NativeBackend.java`

### 5. Testes

- Crie um arquivo `.kf` em `tests/golden/`
- Crie um teste shell que valide o output

## Como alterar o type checker

O type checker é o `SemanticAnalyzer` (roda entre o parser e o lowering;
erros de tipo via `DiagnosticCollector`):

1. Adicione as regras em `SemanticAnalyzer.java`
2. Tipos e nullability (`String?`) vivem em `Type.java`
3. Gaps de target emitem diagnóstico claro (`HTTP002`, `WEB002`, `SECN00x`) — nunca silenciosamente

## Como alterar o backend JVM

O backend usa ASM. Para adicionar uma nova instrução:

1. Defina a operação IR em `IRNodes.java`
2. Adicione o emission em `JvmBackend.emitOperation()`
3. Atualize `computeStack()` e `computeLocals()`

## Como alterar o backend nativo

O backend gera assembly x86-64. Para adicionar uma nova instrução:

1. Defina a operação IR em `IRNodes.java`
2. Adicione a geração de assembly em `NativeBackend.emitOperation()`
3. Considere a calling convention System V AMD64

## Como criar testes golden

1. Crie um arquivo `.kf` em `tests/golden/`
2. O compilador deve gerar um `.class` (JVM) ou executável (nativo)
3. Verifique com `javap -v` que o bytecode está correto (JVM)
4. Teste que a classe carrega e executa na JVM
5. Para nativo, teste que o executável roda e produz o output esperado

## Como atualizar documentação

Sempre que uma feature mudar:

1. Verifique se `/learn` precisa ser atualizado
2. Verifique se `docs/` precisa ser atualizado
3. Mantenha a documentação sincronizada com o código

## Regras para pull requests

1. Uma feature por PR
2. Testes para cada feature
3. Documentação atualizada
4. Sem comentários no código
5. Código que compila sem warnings

## Estado atual do projeto

O projeto está em 0.2.6-beta (810 testes), funcional:

**Funciona hoje:**
- Frontend completo: lexer, parser, `SemanticAnalyzer` (type checking + nullability `String?`)
- Records, classes e interfaces + generics (erasure) + `map/filter/reduce` + `Map/Set` + exceptions reais (JVM + Native unwinding)
- Funções com `main()`, lambdas com capturas, `spawn`/`await` (JVM virtual threads, Native pthread — 31/08)
- Pattern matching (`case String s`, `Point(x,y)`) em JVM/Native/JS
- CLI com 18 comandos (build, run, serve, check, test, script, repl, c, fmt, config gen, bench, profile, inspect, debug, info, lsp, install, version) — `--target=jvm|native|native.risc|native.arm|js|android`
- Backend JVM via ASM — bytecode V21, exception table, virtual threads
- Backend Nativo — ELF x86-64 estável (free-list GC, spawn/pthread, FP XMM, JSON completo, SQLite) + riscv64/aarch64 placeholders
- KofJS — ES Modules via GraalJS (`kof.http` via Java HttpClient interop)
- KofScript (`let`→`KofScriptGlobals`, repl, --watch) + KofC (`kof c` nativo-only)
- stdlib: kof.io, kof.web, kof.http, kof.security, kof.db, kof.orm, kof.ui, kof.config, kof.log, kof.cache, kof.mq
- Testes: 810 (golden 16/16, integração 9/9)

**Em desenvolvimento:**
- GC mark-sweep no Native (hoje free-list)
- MySQL/MariaDB nativo completo (wire protocol: auth SHA-1 feito)
- Android Fase 2+ (hoje Fase 1: projeto Maven + APK, host Activity em Kof)
- Módulos multi-arquivo (semântica unificada residual)
- Scheduler nativo (SCHED001)

**Planejado:**
- Query DSL tipada, connection pooling, ORM fora do JVM
- Observabilidade (métricas, tracing)
- Debugger nativo (DWARF) e JS (source maps)

## Próximo passo

[Glossário →](learn/glossary.md)
