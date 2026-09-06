# Arquitetura do Compilador Kof

**Versão:** 0.3.0-beta · **Evidência:** `kof-compiler/src/main/java/dev/kof/compiler/`

Este documento descreve **como o compilador Kof implementa a linguagem**. Ele é
**normativo sobre a implementação**, não sobre a linguagem — as regras da
linguagem estão em [language-reference/](language-reference/). Se esta
arquitetura mudar (refatoração, novo backend), a **linguagem não muda**.

---

## 1. Visão geral

`text
Kof Language Specification  (docs/language-reference/)
        │
        │ implemented by
        ▼
Kof Compiler  (kof-compiler, Java 21 + ASM 9.8)
        │
        ├── Frontend
        │    ├── Lexer            (Lexer.java, 477L)
        │    ├── Parser           (Parser.java, 1975L)
        │    ├── AST              (AstNodes.java, 50 nós sealed)
        │    ├── Desugar          (CompilerDesugar.java)
        │    ├── Imports          (CompilerImports.java)
        │    └── Semantic Analysis (SemanticAnalyzer.java, 2293L)
        │         ├── SymbolTable (SymbolTable.java)
        │         ├── Type        (Type.java, 8 records)
        │         └── BuiltinTypes (BuiltinTypes.java)
        │
        ├── Middle-end
        │    ├── Lowering AST→IR  (CompilerDriver + *Lowerer.java)
        │    ├── IR               (IRNodes.java, 30 ops)
        │    └── Optimizer        (Optimizer.java + OptimizerConstantFold)
        │
        └── Backend (interface Backend.java)
             ├── JvmBackend       (ASM → .class)
             ├── NativeBackend    (asm x86_64/riscv64/aarch64 → ELF)
             └── JsBackend        (ESM → .mjs)
`

**Módulos Maven relacionados:**

| Módulo | Papel |
|---|---|
| `kof-compiler` | O compilador (frontend + middle-end + backends) |
| `kof-runtime` | Classes Java auxiliares (KofRuntime, KofHttp, KofDb, …) — **não** é usado pelo backend JVM (ver §5.1) |
| `kof-cli` | CLI `kof` (build/run/test/fmt/script/serve/debug/lsp) |
| `kof-script` | KofScript (`.ks`, REPL, `let` top-level) — **linguagem separada** |
| `kof-c-compiler` | Compilador de subconjunto C → Native — **não** consome a IR Kof |

---

## 2. Pipeline real (ordem exata)

`CompilerDriver.compileSources` (`:141-323`):

`text
1. Lexer.tokenize()                    → List<Token>
2. Parser.parse()                      → CompilationUnitNode (AST crua)
3. CompilerImports.expandKofImports()  → AST (imports de diretório resolvidos)
4. BuiltinTypes.registerEnum()         → registro global de enums
5. CompilerDesugar.desugarTests()      → AST (blocos test → harness)
6. CompilerDesugar.desugarApplication()→ AST (application → main embrulhado)
7. [ANDROID] appendAndroidHostIfNeeded → AST (+android-host.kf)
8. SemanticAnalyzer.analyze()          → AST + maps laterais (tipos, métodos)
   ├── preDeclareType                  (fase 1)
   ├── defineMembers                   (fase 2)
   ├── analyzeDeclaration              (fase 3, fixpoint ≤4 por classe)
   └── resolveMethodCalls              (fase 4, no-op efetivo)
9. [aborta se diagnostics.hasErrors()]
10. LabelId.reset()
11. lowerToIR()                        → IRModule
12. applySuperBridges()                → IRModule (bridges de override)
13. [if optimizeEnabled] Optimizer.optimize() → IRModule
14. selectBackend(target)              → Backend
15. backend.emit(irModule, outputDir, debugInfo)
16. [ANDROID] AndroidProjectWriter.write()
`

**Nenhum passo é "type checking" separado** — a checagem de tipos está
entrelaçada com a resolução de nomes dentro de `inferType` (passo 8). Ver
[language-reference/type-system.md](language-reference/type-system.md) §2.

---

## 3. Frontend

### 3.1 Lexer (`Lexer.java`)

Hand-written, single-pass, maximal munch com lookahead de 1–3 caracteres.
Produz `List<Token>`; cada `Token` tem `type, value, file, line, column,
offset, length`. Erros: `LEX001`–`LEX007`. A gramática léxica (o que a
linguagem define) está em
[language-reference/lexical-structure.md](language-reference/lexical-structure.md).

### 3.2 Parser (`Parser.java`)

Recursive descent com precedence-climbing para binários (`parseBinary(minPrec)`,
`:1298`). Lookahead arbitrário via `check`/`checkNext`/varreduras (`looksLike*`).
**Não** é LL(1) nem PEG nem gerado. Erros: `PARSE0xx`.

O parser produz uma AST **crua** — tipos são `String`, sem resolução. A
conversão para AST "final" (desugared) acontece no passo 5–6 do pipeline.

### 3.3 AST (`AstNodes.java`)

`sealed interface AstNode { SourcePosition position(); }` com 50 nós (53 records).
**Não há typed AST**: os nós não carregam tipo resolvido. Os tipos vivem em
`IdentityHashMap` laterais do analisador.

### 3.4 Desugaring (`CompilerDesugar.java`)

- `desugarTests`: blocos `test "nome" { … }` viram funções sintetizadas +
  harness de execução (modo `kof test`).
- `desugarApplication`: `application { onStart {…} onShutdown {…} }` vira
  funções sintetizadas que envolvem o `main` do usuário (prólogo/epílogo).

### 3.5 Imports (`CompilerImports.java`)

`expandKofImports(unit, moduleRoot, …)`: para cada `import a.b`, se `a/b/` é
**diretório** no module root, **puxa todos os `.kf` daquele diretório** para a
unidade (fixpoint ≤256 rodadas). É como arquivos do mesmo pacote se enxergam.

### 3.6 Análise semântica (`SemanticAnalyzer.java`)

Quatro fases (`analyze`, `:89-106`):

1. **`preDeclareType`** — cria `ClassSymbol` vazio por tipo; registra
   `knownClasses`; sintetiza `values()/valueOf()/name()` em enums.
2. **`defineMembers`** — preenche campos/métodos/construtores; accessors de
   record; `TypeParameterSymbol`.
3. **`analyzeDeclaration`** — analisa corpos; **fixpoint ≤4 passes por classe**
   (inferência de retorno `void→T` via `MethodSymbol.setReturnType`).
4. **`resolveMethodCalls`** — **no-op efetivo** (a resolução real já ocorreu
   eager em `inferType`). Vestígio estrutural.

**Maps laterais** (`:64-69`): `knownClasses`, `expressionTypes`,
`resolvedMethods`, `resolvedConstructors`, `classMemberScopes`.

**Símbolos** (`SymbolTable.java`): `ParameterSymbol`, `TypeParameterSymbol`,
`LocalVariableSymbol`, `FieldSymbol`, `MethodSymbol` (com `returnType`
mutável), `ConstructorSet`, `ConstructorSymbol`, `ClassSymbol`,
`FunctionSymbol` (nunca instanciada). `DispatchKind {INSTANCE, STATIC,
INTERFACE}`.

---

## 4. Middle-end

### 4.1 IR (`IRNodes.java`)

**Tipo de IR**: **máquina de pilha linear** — não three-address, não SSA, não
árvore. Javadoc do otimizador (`Optimizer.java:14-15`): *"The IR is a linear,
stack-based op stream with label ops; every backend consumes it in that same
order."*

**Hierarquia**:

`text
IRModule(name, classes, imports, sourceName)
 └─ IRClass(name, superName, interfaces, accessFlags, fields, methods,
            innerClasses, signature, typeId, annotations)
     ├─ IRField(name, type, accessFlags, initialValue, annotations)
     └─ IRMethod(name, returnType, parameterTypes, accessFlags,
                 thrownExceptions, basicBlocks, localVariables, debugInfo,
                 annotations, parameterAnnotations)
         └─ IRBasicBlock(index, operations)
             └─ KofOperation (sealed, 30 records)
`

**⚠️ Basic blocks são nominais**: o lowering sempre emite **exatamente um**
bloco por método (`new IRBasicBlock(0, ops)`); o otimizador achata e
re-empacota. A unidade real é a **lista plana de ops com labels**.

**Os 30 ops** (`IRNodes.java:99-252`):

| Grupo | Ops |
|---|---|
| Load/Store | `KofLoadLiteral`, `KofLoadLocal`, `KofStoreLocal`, `KofLoadField`, `KofStoreField`, `KofGetStatic`, `KofPutStatic` |
| Aritmética | `KofBinary` (18 ops), `KofUnary` (16 ops) |
| Controle | `KofLabel`, `KofJump`, `KofConditionalJump`, `KofReturn`, `KofReturnVoid` |
| Chamada | `KofCall` (com `KofCallKind {INSTANCE,STATIC,CONSTRUCTOR,FUNCTION,INTERFACE,SUPER}`) |
| Objeto | `KofNewObject`, `KofCheckCast`, `KofInstanceOf` |
| Array | `KofNewArray`, `KofArrayLoad`, `KofArrayStore`, `KofArrayLength` |
| Pilha | `KofDup`, `KofDupX1`, `KofDupX2`, `KofPop` |
| Exceção | `KofThrow`, `KofTryStart`, `KofTryEnd`, `KofCatchStart` |

**A IR é tipada**: a maioria dos ops carrega `Type`. O tipo tem papel funcional
no consumo (ex.: `KofConditionalJump.operandType` diz ao JVM qual `if_icmp*`
usar). **Não tem semântica formal própria** (sem grafo de dependência, sem
domínio de valores).

**Debug info**: `KofDebugInfo(IdentityHashMap<KofOperation, SourcePosition>)`
por método — a posição é registrada **antes** do backend, nunca sintetizada lá.

### 4.2 Lowering AST→IR (`CompilerDriver.java` + `*Lowerer.java`)

- **Statements** → `StatementLowerer.emitStatementInner` (`:14-477`).
- **Expressões** → `ExpressionLowerer.emitExpression` (`:13-978`).
- **Chamadas de método** → `ExpressionMethodCallLowerer.lower`.
- **Switch statement** → `SwitchStmtLowerer`; **switch expression** →
  `SwitchExprLowerer`.
- **Lambdas** → `lambdaClass` (`CompilerDriver.java`, método `lambdaClass`): classe
  sintética `Lambda<N>` (ou `LambdaTask<N>` para spawn) implementando interface
  sintética `kof/Function<N>_<mangled>`; capturas viram campos `private final`;
  capturas **mutadas** usam `Box<N>` (`BoxClassFactory`).
- **`spawn`** → instância de `LambdaTask<N>` + `KofCall(KofRuntime, "kof_spawn",
  …)` — **não há op dedicado de spawn na IR**.
- **`try/catch`** → marcadores de região na lista de ops (`KofTryStart` +
  `KofCatchStart` + labels), **não** exception table separada.
- **`throw "msg"` no JVM** → `new RuntimeException(msg)` + `athrow`; nos outros
  targets é `KofThrow()` direto.

### 4.3 Otimizações (`Optimizer.java`)

**Sempre ligadas por default** (`optimizeEnabled = true`, `:16`). Quatro passes
por método (`passes`, `:68-74`):

1. **`OptimizerConstantFold.constantFold`** — aritmética, comparações, unários,
   shifts com literais.
2. **`deadEffects`** — remove pares puros `push+pop`, `dup+pop`, etc.
3. **`reachability`** — código após `KofJump` incondicional sem label destino.
4. **`removeJumpToNext`** — `KofJump(L)` seguido de `KofLabel(L)` → remove o
   jump.

**Não há**: inlining, loop unrolling, LICM, register allocation, escape
analysis, devirtualization. O otimizador é **mínimo** — o trabalho pesado é
delegado ao JIT do target (JVM) ou ao `as`/`ld` (Native).

### 4.4 Transformações pós-IR

- **`applySuperBridges`** (`CompilerDriver.java`): gera métodos-ponte quando
  um override tem assinatura mais específica que a da super (semântica JVM de
  covariant return).
- **Classes sintéticas** anexadas ao módulo no fim do lowering:
  `classes.addAll(syntheticClasses)` (`:427`) — lambdas, boxes, SAM adapters,
  interfaces de função.

---

## 5. Backends

Interface (`Backend.java:6-12`): `void emit(IRModule, Path, boolean debugInfo)`.

Seleção (`CompilerDriver.selectBackend`, `:370-381`):

`java
case JVM            -> backendWithClasspath(new JvmBackend());
case NATIVE         -> new NativeBackend(Target.NATIVE);
case NATIVE_RISCV64 -> new NativeBackend(Target.NATIVE_RISCV64);
case NATIVE_AARCH64 -> new NativeBackend(Target.NATIVE_AARCH64);
case JS             -> new JsBackend();
case ANDROID        -> backendWithClasspath(new JvmBackend());
`

### 5.1 JVM (`JvmBackend.java`)

- **ASM 9.8** (`ClassWriter` com `COMPUTE_FRAMES | COMPUTE_MAXS`).
- **Saída**: um `.class` por classe + `KofRuntime` gerado/compilado na hora.
- **Runtime**: **não usa o módulo `kof-runtime/`** — `JvmRuntime.ensureCompiled`
  **gera `KofRuntime.java` no diretório de saída e o compila com `javac`**
  (`ToolProvider.getSystemJavaCompiler()`) em `dev/kof/runtime/`. O source é
  concatenação de blocos (`JvmRuntimeJson`, `JvmRuntimeUi`, `JvmRuntimeCore`,
  `JvmRuntimeIo`, `JvmWebRuntime`, `JvmMediaRuntime`, `JvmRuntimeWebServer`,
  `JvmRuntimeWebDispatch`, `JvmConfigRuntime`, `JvmCacheRuntime`,
  `JvmOrmRuntime`, `JvmTimeRuntime`, `JvmStringRuntime`, `JvmVkRuntime`
  condicional). **Link-por-uso**: só injeta o bloco se o programa usa a área.
- **`getCommonSuperClass`** é sobrescrito para caminhar a hierarquia externa
  (`android.*`) + JDK — "nunca um palpite que corrompa os frames".
- **Box/unbox**: `boxedClassNameFor`/`unboxMethodName` (`:61-101`).
- **Dispatch**: `KofCallKind` → opcode (`INSTANCE→INVOKEVIRTUAL`,
  `STATIC/FUNCTION→INVOKESTATIC`, `INTERFACE→INVOKEINTERFACE`,
  `SUPER/CONSTRUCTOR→INVOKESPECIAL`).

### 5.2 Native (`NativeBackend.java`, 8834L)

Um único arquivo `.s` + um binário ELF por módulo.

- **x86_64** (`Target.NATIVE`): asm gerado como **strings em `StringBuilder`**
  (`:243-261`). Máquina de pilha (`sp`=operandos, `s11`=frame pointer). Runtime
  em asm puro embutido (`NativeRuntime.generateRuntimeAssembly()`). `_start` →
  `SYS_exit_group (231)`.
- **riscv64** (`Target.NATIVE_RISCV64`): **lowering riscv64 real**
  (`emitRiscv`, `:1947`) — asm puro, raw syscalls, sem libc, binário estático.
  `clone(220)` para spawn (qemu-riscv64 8.2.2 não tem clone3), `nanosleep(101)`
  para sleep, `amoswap.w` para spinlock. `-mno-relax` no as + `--no-relax` no
  ld (evita gp-relaxation com gp=0).
- **aarch64** (`Target.NATIVE_AARCH64`): **tradução linha-a-linha do asm
  riscv64** (`translateRiscvToAarch64`, `:8200`). Não é lowering independente
  — é um tradutor ISA. `amoswap.w`→`swpal`, `amoadd.d`→`ldadd`, `fence`→`dmb
  ish`, `movz` quando `lsl #16`.

**⚠️ `docs/architecture.md` antigo chama riscv64/aarch64 de "placeholder
x86_64" — desatualizado** (SG-E1).

### 5.3 JS (`JsBackend.java`)

- **Saída**: um `.mjs` por módulo (ESM ES2022+) + `kof-runtime.mjs` +
  `index.html` + source map.
- **Estratégia**: a IR stack-based é convertida para uma **árvore JS**
  (`JsIr.java:12-14`: *"Kof IR is stack-based; the lowering converts the stack
  discipline into this tree-shaped JS AST"*).
- **Execução**: Node ou browser; `KofJsRunner` embute GraalJS para execução
  server-side.
- **Short-circuit `&&`/`||` desligado** (`ExpressionLowerer.java:147-148`) —
  SG-006.

### 5.4 Android (`Target.ANDROID`)

**Variante de JVM**, não backend separado:
- Backend: `JvmBackend` (mesmo bytecode).
- Diferencial: `appendAndroidHostIfNeeded` injeta `dev/kof/android-host.kf`
  (escrito **em Kof**, compilado pelo mesmo frontend) se o usuário não declara
  `MainActivity`.
- Pós-emit: `AndroidProjectWriter.write` gera `pom.xml`, `AndroidManifest.xml`,
  `assets/kof/` (KofJS), `libs/kof-app.jar`.

---

## 6. Targets (enum `Target.java`)

| Valor | Backend | Saída |
|---|---|---|
| `JVM` | JvmBackend | `.class` + `KofRuntime` |
| `NATIVE` | NativeBackend(NATIVE) | ELF x86_64 |
| `NATIVE_RISCV64` | NativeBackend(RISCV64) | ELF riscv64 estático |
| `NATIVE_AARCH64` | NativeBackend(AARCH64) | ELF aarch64 (traduzido) |
| `JS` | JsBackend | `.mjs` + runtime + html |
| `ANDROID` | JvmBackend + AndroidProjectWriter | bytecode + APK |

Aliases CLI (`KofCliSupport.parseTarget`): `jvm`, `native`,
`native.risc|riscv64|riscv`, `native.arm|aarch64|aarch`, `js`, `android`.

---

## 7. Terminologia — o que é o quê

| Termo | É | Não é |
|---|---|---|
| **Kof** | a linguagem (conjunto de regras) | o compilador, o binário |
| **Kof Compiler** | a implementação (`kof-compiler`) | a definição da linguagem |
| **Kof4J** | a linha JVM (backend + runtime) | uma linguagem separada |
| **KofNative** | o backend nativo (asm) | uma linguagem separada |
| **KofJS** | o backend JavaScript | uma linguagem separada |
| **Kof IR** | máquina de pilha linear tipada, 30 ops | uma segunda AST |
| **AST** | 50 nós sealed, tipos como String | typed AST |
| **KofScript** (`.ks`) | linguagem de script separada (top-level `let`) | Kof com `let` |
| **KofC** (`kof-c-compiler`) | compilador de subconjunto C → Native | backend da IR Kof |

**"A IR possui uma AST própria?"** — **Não.** A IR é uma **lista linear de
operações de máquina de pilha** com labels e marcadores de região de exceção.
Chamá-la de "AST" seria tecnicamente incorreto: não há hierarquia de nós, não
há recursão de subexpressões, não há árvore. O nome correto é **IR** (ou "op
stream"). A **árvore** existe só no frontend (AST) e, no caso do JS, dentro do
backend (`JsIr` — uma AST **do JS**, não do Kof).

---

## 8. Recomendações futuras (não implementadas)

Problemas arquiteturais reais encontrados na auditoria — **documentados, não
corrigidos** (regra 14):

1. **`resolveMethodCalls` é no-op** (`SemanticAnalyzer.resolveMethodCalls`) —
   percorre a AST sem fazer nada. Remover ou implementar.
2. **`FunctionSymbol` nunca é instanciada** (`SymbolTable.java:195`) — funções
   top-level são resolvidas por varredura da AST. Unificar com `MethodSymbol`.
3. **`WildcardType` nunca é construída** (`Type.java:38`) — remover ou
   implementar variance.
4. **Lowering re-inferi tudo** (`MethodCallTyper.java:27-34`) — o cache do
   analyzer é limpo a cada classe/passe, então `ExpressionTyper`/`MethodCallTyper`
   refazem o trabalho. Unificar em uma passada.
5. **Basic blocks nominais** — a IR tem `IRBasicBlock` mas sempre um bloco por
   método. Ou implementar blocos reais (necessário para otimizações sérias), ou
   remover o contêiner.
6. **`kof-runtime/` não é usado pelo backend JVM** — o runtime é gerado como
   string Java e compilado com `javac` no diretório de saída. Isso exige JDK
   completo em runtime. Alternativa: empacotar `KofRuntime.class` pré-compilado.
7. **`NativeBackend.java` tem 8834 linhas** — em refactor (REFACTOR-500 Fase 3).
8. **`CompilerDriver.java` tinha 400KB** — em refactor (F2.x em curso).
9. **Tradutor aarch64 é regex-based sobre strings de asm riscv** — frágil. Um
   lowering aarch64 direto seria mais robusto.
10. **Sem checagem de subtipagem** (`isAssignable` aceita `ClassType→ClassType`
    sempre) — a maior lacuna de segurança. Ver SG-009.
