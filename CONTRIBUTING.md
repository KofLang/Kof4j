[English](CONTRIBUTING.md) | [Português](CONTRIBUTING.pt_BR.md)

# 30 — Contributing

> **Current state, test counts and the active branch are catalogued in `docs/status.md` / `docs/backend-parity.md` — this file does not duplicate them.**

## Repository structure

```
kof/
├── kof-compiler/       ← core compiler (JVM/Native/JS + KofScript/KofC)
├── kof-cli/            ← CLI (build/run/script/c/test/bench/debug)
├── kof-script/         ← KofScript (let→KofScriptGlobals, repl, --watch)
├── kof-c-compiler/     ← KofC (C subset → native-only ELF)
├── kof-runtime/        ← native runtime (free-list GC)
├── docs/               ← internal documentation
├── learn/              ← this material (intention->Kof->frontend->IR->backend->runtime)
├── tests/              ← golden tests (16 golden + 9 integration)
├── pom.xml             ← Maven build (0.5.0-beta)
└── README.md
```

## Building

```bash
mvn clean package -DskipTests
```

## Running tests

```bash
mvn test
```

## Compiler structure

```
kof-compiler/src/main/java/dev/kof/compiler/
├── KofScript.java      ← KofScript eval/runFile/repl (let→Globals)
├── KofCCompiler.java   ← KofC C subset → ELF
├── KofFormatter.java   ← kof fmt (real parser, idempotent)
├── Lexer.java          ← hand-written lexer
├── Parser.java         ← recursive descent parser
├── AstNodes.java       ← AST nodes
├── SemanticAnalyzer.java ← semantic analysis/type checking
├── Type.java           ← type system
├── SymbolTable.java    ← symbol table
├── IRNodes.java        ← IR operations
├── Optimizer.java      ← IR optimization passes (always active)
├── CompilerDriver.java ← orchestrator
├── Backend.java        ← backend interface
├── Target.java         ← target enum
├── JvmBackend.java     ← JVM bytecode generation (ASM, V21)
├── JsBackend.java      ← ES Modules generation (KofJS)
├── NativeBackend.java  ← x86-64 assembly generation
├── Kof*.java           ← stdlib namespaces (KofWeb, KofHttp, KofSecurity,
│                        KofUi, KofDb, KofOrm, KofConfig, KofLog, KofCache...)
├── JvmRuntime.java     ← JVM runtime (generated KofRuntime)
├── Diagnostic.java     ← diagnostics
├── DiagnosticCollector.java
├── CompilationResult.java
├── Token.java          ← token representation
├── TokenType.java      ← token type enum
└── SourcePosition.java ← position in the code
```

## How to add a feature

### 1. Lexer

If the feature needs a new keyword or operator:

- Add the token in `TokenType.java`
- Add recognition in `Lexer.java`

### 2. Parser

If the feature needs new syntax:

- Add the AST node in `AstNodes.java`
- Add parsing in `Parser.java`

### 3. Lowering

If the feature needs to generate IR:

- Add IR operations in `IRNodes.java` (if needed)
- Add lowering in `CompilerDriver.java`

### 4. Backend

If the feature needs new instructions:

- For JVM: add the emission in `JvmBackend.java`
- For native: add the emission in `NativeBackend.java`

### 5. Tests

- Create a `.kf` file in `tests/golden/`
- Create a shell test that validates the output

## How to change the type checker

The type checker is `SemanticAnalyzer` (it runs between the parser and
lowering; type errors via `DiagnosticCollector`):

1. Add the rules in `SemanticAnalyzer.java`
2. Types and nullability (`String?`) live in `Type.java`
3. Target gaps emit a clear diagnostic (`HTTP002`, `WEB002`, `SECN00x`) — never silently

## How to change the JVM backend

The backend uses ASM. To add a new instruction:

1. Define the IR operation in `IRNodes.java`
2. Add the emission in `JvmBackend.emitOperation()`
3. Update `computeStack()` and `computeLocals()`

## How to change the native backend

The backend generates x86-64 assembly. To add a new instruction:

1. Define the IR operation in `IRNodes.java`
2. Add assembly generation in `NativeBackend.emitOperation()`
3. Consider the System V AMD64 calling convention

## How to create golden tests

1. Create a `.kf` file in `tests/golden/`
2. The compiler must generate a `.class` (JVM) or an executable (native)
3. Verify with `javap -v` that the bytecode is correct (JVM)
4. Test that the class loads and runs on the JVM
5. For native, test that the executable runs and produces the expected output

## How to update documentation

Whenever a feature changes:

1. Check whether `/learn` needs to be updated
2. Check whether `docs/` needs to be updated
3. Keep the documentation in sync with the code

## Rules for pull requests

1. One feature per PR
2. Tests for each feature
3. Updated documentation
4. No comments in the code
5. Code that compiles without warnings
6. An open issue containing the implementation plan for the feature in your PR.
## Current state of the project

The project's current state is catalogued in `docs/status.md`.

**Works today:**
- Complete frontend: lexer, parser, `SemanticAnalyzer` (type checking + nullability `String?`)
- Records, classes and interfaces + generics (erasure) + `map/filter/reduce` + `Map/Set` + real exceptions (JVM + Native unwinding)
- Functions with `main()`, lambdas with captures, `spawn`/`await` (JVM virtual threads, Native pthread — 31/08)
- Pattern matching (`case String s`, `Point(x,y)`) on JVM/Native/JS
- CLI with 26 commands (build, run, serve, check, test, script, repl, c, fmt, config, bench, profile, inspect, decompile, translate, compare, migrate, debug, info, lsp, install, deps, editor, new, init, version) — `--target=jvm|native|native.risc|native.arm|js|android`
- JVM backend via ASM — V21 bytecode, exception table, virtual threads
- Native backend — stable x86-64 ELF + riscv64/aarch64 full core (free-list GC + mark-sweep, spawn/pthread, FP XMM, complete JSON, SQLite, HTTP, DWARF debug info)
- KofJS — ES Modules via GraalJS (`kof.http` via Java HttpClient interop)
- KofScript (`let`→`KofScriptGlobals`, repl, --watch) + KofC (`kof c` native-only)
- stdlib: kof.io, kof.web, kof.http, kof.security, kof.db, kof.orm, kof.ui, kof.config, kof.log, kof.cache, kof.mq, kof.observability, kof.validation, kof.time, kof.scheduler, kof.process
- Tests: 3225 across the 4 modules (golden 16/16, integration 9/9)

**In development:**
- Native auto-collect on exhaustion (mark-sweep is implemented but manual — §260)
- Complete native MySQL/MariaDB (handshake/query/prepared)
- Android Phase 5+ (Phases 1-4 done: Maven/APK, label/permissions/--apk/--keystore,
  responsive WebView, --min-sdk/--target-sdk; pending: --aab, icon metadata)
- Multi-file modules (residual unified semantics)

**Planned:**
- Connection pooling, ORM outside the JVM
- OpenTelemetry export (W3C traceId/spanId + timed spans already exist)
- Native DWARF types (`DW_AT_type` slice — low_pc/locals already work)

## Next step

[Glossary →](learn/glossary.md)
