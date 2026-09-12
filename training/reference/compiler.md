# Kof Compiler Reference

**Version:** 0.3.22-beta (Sep 2026) — 810 tests

## Compilation Pipeline

```
Source (.kf / .ks / .c)
    ↓
Lexer → Tokens
    ↓
Parser → AST (PatternExpr fieldVars, NullableType)
    ↓
Semantic Analysis → Typed AST (isAssignable com null narrowing, record destructuring)
    ↓
Kof IR (backend-agnostic) → Optimizer (constant folding, branch simplification)
    ↓
┌─────────┬──────────┬──────┬──────────┬──────┬─────────┐
│  JVM    │ Native   │ JS   │ KofScript│ KofC │ Android │
│  (ASM)  │ x86_64/  │ ES   │ JIT      │ C→ELF│ JVM→APK │
│         │ risc/arm*│      │          │      │ (Fase 1)│
└─────────┴──────────┴──────┴──────────┴──────┴─────────┘
```

* native.risc/arm placeholder

## CLI Commands

| Command | Description |
|---------|-------------|
| `kof build <dir> [--target jvm\|native\|native.risc\|native.arm\|js\|android] [--output <dir>] [--release] [--apk]` | Compile all .kf files |
| `kof run <file.kf\|dir> [--target jvm\|native\|native.risc\|native.arm\|js\|android] [args...]` | Compile and run (JVM/Native/JS/Android) |
| `kof serve <file.kf> [--port] [--host]` | Start HTTP server (web.app + API legada handle) |
| `kof check <file.kf\|dir>` | Type-check only |
| `kof test <file.kf\|dir> [--target jvm\|native\|js]` | Structured tests `test "nome" { }` nos 3 targets |
| `kof script <file.ks> [--watch] [--inspect]` | KofScript top-level let → KofScriptGlobals + JIT |
| `kof repl` | REPL incremental KofScript |
| `kof c <file.c> [-o outDir]` | KofC C subset → ELF x86_64 (nativo-only) |
| `kof fmt <file.kf\|dir>` | Formatter via parser real (KofFormatter), idempotente |
| `kof config gen <file.kf\|dir> [--output <arquivo>]` | Gera template `kof.config` a partir das chaves `config.*` do código |
| `kof bench [paths...] [--iterations N] [--quick] [--baseline <file>]` | Benchmark harness com baselines |
| `kof profile <file.kf> [--target ...]` | Execução + métricas (CPU, RSS, GC) |
| `kof inspect <file.kf> [--json]` | IR statistics (ops antes/depois do otimizador) |
| `kof debug <file.kf>` | DAP MVP no target JVM |
| `kof info [--json]` | Environment report |
| `kof install <dir>` | Instala este build como distribuição |
| `kof lsp` | Language Server (stdio, LSP 3.x) |
| `kof version` | Show version (0.3.22-beta) |

18 comandos. `kof fmt` e `kof config gen` implementados (0.3.22-beta).

Fixes 27/08:
- `CompilerDriver.expandKofImports` trata `import a.b.C` (arquivo) além de `a.b.*` (pasta) — projetos grandes com `a/b/C.kf` agora geram ambos os `.class`.
- `NativeRuntime` free-list GC (`kof_free_head`, `kof_gc_collect` mark-sweep; auto-GC desligado) + spawn/await via pthread (31/08).

## Backend Targets

### JVM
- Uses ASM for bytecode generation
- Targets Java 21
- Uses JVM's GC and memory management
- `kof.http` via `java.net.http.HttpClient`

### Native
- Generates x86_64 Linux ELF binaries
- Uses Linux syscalls directly (no libc)
- Free-list allocator thread-safe (lock futex) + `kof_gc_collect` mark-sweep conservador; auto-GC desligado (GC mark-sweep automático ainda pendente)
- Ponto flutuante em XMM real (FLT001 fechado 31/08); JSON completo de objetos/arrays (JSN001/002/003 fechados 31/08)
- `spawn`/`await` via pthread (CONC001 fechado 31/08)
- MySQL handshake com SHA-1 scramble (WIP)
- Runtime functions: kof_alloc, kof_free, kof_gc_*, kof_print, kof_string_*, kof_array_*, kof_list_*, kof_net_*, kof_db_mysql_scramble, etc.

### Native RISC-V / ARM
- Placeholder ELF via `riscv64-linux-gnu-as/ld` + qemu, `Target.NATIVE_RISCV64/AARCH64`

### JS
- ES Modules 2022+ via GraalJS
- `kof.http` via Java HttpClient interop
- `kof.cache`, `Map/Set`, `String?`, pattern record destructuring

### KofScript
- JIT in-memory, top-level `let`/`const` → `var`/`val` preprocess + `KofScriptGlobals`, evalCache 64 LRU

### KofC
- C subset (`int` globals, `void` funcs, `if`/`while`/`*(int*)`/`&`) → x86_64 via `as`/`ld`

### Android
- Fase 1: `AndroidProjectWriter` — saída JVM (bytecode) + KofJS para assets → APK de debug (aapt2/d8/apksigner via Maven; host Activity em Kof)
- Gaps: `AND001` spawn/await, `AND002` kof.web, `AND003` reflexão, `AND004` android.jar

## IR Operations

| Operation | Description |
|-----------|-------------|
| KofLoadLiteral | Load constant value |
| KofLoadLocal | Load local variable |
| KofStoreLocal | Store local variable |
| KofLoadField | Load object field |
| KofStoreField | Store object field |
| KofBinary | Binary arithmetic |
| KofUnary | Unary operation |
| KofCall | Method/function call (incl. map/filter/reduce, http) |
| KofNewObject | Create object |
| KofNewArray | Create array |
| KofArrayLoad | Array element read |
| KofArrayStore | Array element write |
| KofArrayLength | Array length |
| KofReturn | Return value |
| KofReturnVoid | Return void |
| KofThrow | Throw exception |
| KofLabel | Label marker |
| KofJump | Unconditional jump |
| KofConditionalJump | Conditional branch (pattern + null narrowing) |
