# 29 — Internals do Compilador

> **Kof 0.3.22-beta — targets jvm/native/native.risc/native.arm/js/kofc — `intention->Kof->frontend->IR->backend->runtime`**

## Arquitetura

```
.kf source
  ↓ Lexer          (Lexer.java)
  ↓ Token stream
  ↓ Parser         (Parser.java)
  ↓ AST            (AstNodes.java)
  ↓ Type system    (Type.java)
  ↓ IR             (IRNodes.java)
  ↓ Optimizer      (Optimizer.java, sempre ativo)
  ↓ Backend        (JvmBackend.java ou NativeBackend.java)
  ↓ Output         (.class ou ELF)
```

Cada estágio tem uma responsabilidade clara.

O **otimizador de IR** (`Optimizer.java`) roda em todo build: constant
folding, branch simplification (condições constantes → jumps diretos), dead
stack effects, unreachable code elimination (com regiões try/catch
preservadas), jump-to-next elimination e identidades aritméticas. Debug
positions dos ops sobreviventes são preservados. `kof inspect` expõe as
estatísticas (ops antes/depois).

## Lexer (Lexer.java)

**Responsabilidade**: converter texto em tokens.

**Entrada**: string com o código fonte
**Saída**: lista de `Token`

O lexer é hand-written (escrito à mão, não gerado). Cada caractere é analisado sequencialmente.

Exemplo de tokens:
```
"record" → RECORD
"Point"  → IDENTIFIER
"("      → LPAREN
"Int"    → INT (tipo)
"x"      → IDENTIFIER
")"      → RPAREN
```

**Arquivo**: `kof-compiler/src/main/java/dev/kof/compiler/Lexer.java`

## Parser (Parser.java)

**Responsabilidade**: converter tokens em AST (Abstract Syntax Tree).

**Entrada**: lista de `Token`
**Saída**: `CompilationUnitNode`

O parser é um parser recursivo descendente. Cada regra da gramática é um método Java.

Exemplo:
```kf
record Point(Int x, Int y)
```

O parser reconhece:
- `record` → início de RecordDeclarationNode
- `Point` → nome
- `(` → início dos componentes
- `Int x` → RecordComponentNode
- `,` → separador
- `Int y` → RecordComponentNode
- `)` → fim dos componentes

**Arquivo**: `kof-compiler/src/main/java/dev/kof/compiler/Parser.java`

## AST (AstNodes.java)

**Responsabilidade**: representar a estrutura sintática do código.

A AST é uma árvore de nós. Cada nó representa uma construção da linguagem.

```
CompilationUnitNode
  └── RecordDeclarationNode
        ├── name: "Point"
        ├── components:
        │     ├── RecordComponentNode(type="Int", name="x")
        │     └── RecordComponentNode(type="Int", name="y")
        └── members: []
```

**Arquivo**: `kof-compiler/src/main/java/dev/kof/compiler/AstNodes.java`

## IR (IRNodes.java)

**Responsabilidade**: representação intermediária adequada para geração de código.

O IR é uma representação mais baixa que a AST. Cada operação IR mapeia diretamente para uma ou poucas instruções de baixo nível.

```
IRClass(name="Point", superName="java/lang/Record")
  ├── IRField(name="x", descriptor="I")
  ├── IRField(name="y", descriptor="I")
  ├── IRMethod(name="<init>", descriptor="(II)V")
  │     └── IRBasicBlock:
  │           ├── LoadLocal("LPoint;", 0)
  │           ├── InvokeSpecial("java/lang/Record", "<init>", "()V")
  │           ├── LoadLocal("LPoint;", 0)
  │           ├── LoadLocal("I", 1)
  │           ├── PutField("Point", "x", "I")
  │           └── ReturnVoid
  └── IRMethod(name="x", descriptor="()I")
        └── IRBasicBlock:
              ├── LoadLocal("LPoint;", 0)
              ├── GetField("Point", "x", "I")
              └── Return("I")
```

**Arquivo**: `kof-compiler/src/main/java/dev/kof/compiler/IRNodes.java`

## JVM Backend (JvmBackend.java)

**Responsabilidade**: converter IR em bytecode JVM usando ASM.

O backend usa a biblioteca ASM para gerar classes `.class` válidas.

Para cada `IRMethod`, ele:
1. Cria um `MethodVisitor`
2. Visita cada operação IR
3. Emite a instrução bytecode correspondente

**Arquivo**: `kof-compiler/src/main/java/dev/kof/compiler/JvmBackend.java`

## Native Backend (NativeBackend.java)

**Responsabilidade**: converter IR em código nativo x86-64.

O backend gera assembly x86-64, que é montado e linkado para criar um executável ELF.

Pipeline:
1. IR → Assembly x86-64
2. Assembly → Objeto (via `as`)
3. Objeto → Executável (via `ld`)

**Arquivo**: `kof-compiler/src/main/java/dev/kof/compiler/NativeBackend.java`

## Backend Interface (Backend.java)

**Responsabilidade**: abstrair diferentes backends de geração de código.

```java
interface Backend {
    void emit(Object irModule, Path outputDir) throws IOException;
}
```

Isso permite que o compilador suporte múltiplos targets sem acoplamento.

**Arquivo**: `kof-compiler/src/main/java/dev/kof/compiler/Backend.java`

## Target Enum (Target.java)

**Responsabilidade**: identificar o target de compilação (Target separation 0.2.0: `NATIVE` vs `NATIVE_RISCV64`/`NATIVE_AARCH64`, `JS`, `ANDROID`, `KofC` separado).

```java
enum Target {
    JVM,
    NATIVE,           // x86-64
    NATIVE_RISCV64,   // riscv64 (--target=native.risc)
    NATIVE_AARCH64,   // aarch64 (--target=native.arm)
    JS,
    ANDROID,
    // kofc = KofCCompiler (C subset → ELF x86-64 nativo-only, separado)
}
```

**Arquivo**: `kof-compiler/src/main/java/dev/kof/compiler/Target.java`

## CompilerDriver (CompilerDriver.java)

**Responsabilidade**: orquestrar todo o pipeline.

```
compile(sourceFile, outputDir, target):
  1. Ler arquivo
  2. Lexer → tokens
  3. Parser → AST
  4. Lowering → IR
  5. Selecionar backend baseado no target
  6. Backend → output
  7. Gravar arquivo
```

**Arquivo**: `kof-compiler/src/main/java/dev/kof/compiler/CompilerDriver.java`

## Diagnostics (Diagnostic.java, DiagnosticCollector.java)

**Responsabilidade**: coletar e reportar erros.

Erros apontam para a posição original no arquivo `.kf`:

```
error: type mismatch
  --> src/main/kf/User.kf:12:5
   |
12 |     name = 42
   |     ^^^^ expected String, found Int
```

**Arquivos**: `Diagnostic.java`, `DiagnosticCollector.java`

## Estado atual do compilador

| Componente | Status |
|------------|--------|
| Lexer | ✅ Completo (55+ keywords, `String?`, `let`/`const` alias para KofScript) |
| Parser | ✅ Funcional (records, classes, interfaces, funções, `case String s`, `Point(x,y)`, `String?`) |
| AST | ✅ Completo para constructs suportados |
| Type system | ✅ `String?` nullable, `List<T>` inference, imports `a.b.C` fix |
| Symbol table | ⚠️ Definido mas não usado completamente |
| IR | ✅ Definido, lowering funcional (`intention->Kof->frontend->IR->backend->runtime`) |
| Optimizer | ✅ Passes sempre ativos (constant folding, dead code, branch simplification) |
| JVM Backend | ✅ Funcional (via ASM, bytecode V21, exception table, virtual threads; ) |
| Native Backend | ✅ Funcional (x86-64 free-list GC + spawn/pthread + FP XMM; riscv/arm placeholders via qemu) |
| Diagnostics | ✅ Funcional |
| KofScript (`KofScriptGlobals`) | ✅ `let`/`const` topo, repl, --watch |
| KofC (`KofCCompiler`) | ✅ C subset → ELF nativo-only (`kof c`) |
| CLI | ✅ Funcional (18 comandos: build, run, serve, check, test, script, repl, c, fmt, config gen, bench, profile, inspect, debug, info, lsp, install, version) |

## Multiplatform architecture

A arquitetura do compilador foi projetada para suportar múltiplos backends:

```text
                    Kof Source (.kf / .ks / .c)
                          │
                          ▼
                        Lexer (let/const alias, String?)
                          │
                          ▼
                        Parser (case String s, Point(x,y))
                          │
                          ▼
                         AST (+ KofScriptGlobals, KofC AST)
                          │
                          ▼
                     Kof IR (compartilhado) — intention->Kof->frontend->IR->backend->runtime
                      /       |       \      \
                     /        |        \      \
                    ▼         ▼         ▼       ▼
             JVM Backend  Native(x86/riscv/arm)  JS   KofC
                    │           │               │     │
                    ▼           ▼               ▼     ▼
                .class       ELF .o          .mjs   ELF
                    │           │               │     │
                    ▼           ▼               ▼     ▼
                javac/jar     ld → executável  GraalJS  ld
```

O IR compartilhado permite que o mesmo código seja compilado para diferentes targets sem modificações.

## Próximo passo

[Contribuindo →](30-contributing.md)