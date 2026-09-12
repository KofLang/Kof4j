# Roadmap KofNative

> **0.3.22-beta — set 2026 — free-list GC done, Target separation done, MySQL via kof_db WIP**

## Princípios

1. **Não quebrar Kof4J** — toda mudança deve ser validada contra o backend JVM
2. **Incremental** — cada milestone é pequeno, testável, rollbackável
3. **Correto primeiro** — não otimizar antes de funcionar
4. **Documentado** — cada milestone tem documentação e testes

## Status atual

O backend nativo já está funcional. Aqui está o que já foi implementado:

### ✅ Concluído

- **Milestone 0 — Baseline**: Todos os testes JVM passando
- **Milestone 1 — Target Abstraction**: Enum `Target`, interface `Backend`, CLI com `--target`
- **Milestone 2 — Native Backend Skeleton**: Estrutura do NativeBackend
- **Milestone 3 — Native Hello World**: ELF x86-64 que imprime "Hello World"
- **Milestone 4 — Primitive Values**: Int, Long, Float, Double, Bool, Char
- **Milestone 5 — Functions**: Declaração e chamada de funções
- **Milestone 6 — Strings**: String literals e operações básicas
- **Milestone 7 — Control Flow**: If/else, while, for ✅
- **Milestone 8 — Arrays**: Arrays nativos ✅
- **Milestone 9 — Value Types / Structs**: Records como structs nativos ✅
- **Milestone 10 — Objects**: Classes com herança e dispatch ✅
- **Milestone 11 — Exceptions**: Try/catch nativo (unwinding) ✅
- **Milestone 12 — Generics**: Erasure (como JVM; `Box<T>` com `T` primitivo) ✅
- **Milestone 13 — Target separation**: `NATIVE_RISCV64/AARCH64` + `parseTarget native.risc/arm` ✅
- **Milestone 14 — Free-list GC**: `kof_free_head` reuso `mmap` ✅
- **Milestone 15 — kof_db**: SQLite via `.so` ✅
- **Milestone 16 — `spawn`/`await` via pthread** (31/08 — CONC001 fechado): trampoline + join implícito + allocator thread-safe (futex) ✅
- **Milestone 17 — FP real (XMM)** (31/08 — FLT001/JSN001 fechados): `vcvtsi2sd`/`mulsd`, dtoa via snprintf ✅
- **Milestone 18 — JSON completo**: objetos/records + arrays (JSN002/JSN003/JSN001 fechados) ✅

### 🔄 Em desenvolvimento

- **MySQL/MariaDB nativo**: wire protocol sobre sockets (auth scramble SHA-1 feito; falta handshake completo, query e prepared statements)
- **GC mark-sweep**: pendente (memória devolvida hoje só no `munmap` fallback)
- **riscv64/aarch64**: codegen ainda x86_64 (placeholders via qemu)

## Milestones detalhados

### Milestone 0 — Baseline ✅

**Objetivo:** Garantir que tudo funciona antes de modificar.

**Ações:**
- [x] Executar todos os testes existentes
- [x] Compilar todos os exemplos
- [x] Validar records (x()=3, y()=7)
- [x] Registrar estado atual como baseline
- [x] Criar branch `feature/native`

**Critério de sucesso:** zero regressions, build limpo.

---

### Milestone 1 — Target Abstraction ✅

**Objetivo:** Introduzir a abstração mínima para distinguir JVM/Native.

**Mudanças:**
- [x] Criar enum `Target { JVM, NATIVE }`
- [x] Criar interface `Backend`
- [x] Parametrizar `CompilerDriver.compile()` com target
- [x] Default continua sendo JVM

**Arquivos novos:**
- `Target.java`
- `Backend.java`

**Arquivos modificados:**
- `CompilerDriver.java` — parametrizar compile(), extrair interface
- `Main.java` (CLI) — adicionar flag `--target`

**Testes:**
- [x] Todos os testes JVM passam (regression)
- [x] `--target jvm` gera o mesmo output que antes
- [x] `--target native` gera executável

**Critério de sucesso:** zero regressions, target flag funcional.

---

### Milestone 2 — Native Backend Skeleton ✅

**Objetivo:** Criar a estrutura do NativeBackend sem gerar código.

**Mudanças:**
- [x] Criar `NativeBackend implements Backend`
- [x] Implementar `emit()` vazio
- [x] NativeBackend.emit() retorna erro "not yet implemented"

**Arquivos novos:**
- `NativeBackend.java`

**Testes:**
- [x] JVM continua funcionando
- [x] NativeBackend aceita IR e retorna erro claro
- [x] CLI `--target native` mostra mensagem apropriada

**Critério de sucesso:** arquitetura validada, zero regressions.

---

### Milestone 3 — Native Hello World ✅

**Objetivo:** Gerar um ELF x86-64 que imprime "Hello World".

**Requisitos:**
- [x] Gerar assembly x86-64
- [x] Montar com `as`
- [x] Linkar com `ld`
- [x] Gerar ELF válido

**Input:**
```kof
main() = print("Hello World")
```

**Output:**
```
$ ./hello
Hello World
```

**Arquivos modificados:**
- `NativeBackend.java` — implementar emission via assembly

**Testes:**
- [x] JVM continua funcionando
- [x] Native gera ELF válido
- [x] Executável roda e imprime "Hello World"

**Critério de sucesso:** hello world nativo sem JVM.

---

### Milestone 4 — Primitive Values ✅

**Objetivo:** Suportar valores primitivos nativos.

**Features:**
- [x] Int (32-bit)
- [x] Long (64-bit)
- [x] Float (32-bit)
- [x] Double (64-bit)
- [x] Bool (1-bit, extended to i32)
- [x] Char (16-bit)

**Exemplo:**
```kof
main() {
    var x = 42
    var y = 3.14
    var z = true
}
```

**Mapeamento tipos:**
| Kof | x86-64 |
|-----|--------|
| Int | %edi, %esi, etc. |
| Long | %rdi, %rsi, etc. |
| Float | %xmm0, %xmm1, etc. |
| Double | %xmm0, %xmm1, etc. |
| Bool | zero-extended to i32 |

**Testes:**
- [x] Parser: tipos reconhecidos
- [x] IR: operações com tipos corretos
- [x] Native: valores passados corretamente
- [x] JVM: regressão zero

**Critério de sucesso:** primitivos funcionam em ambos os backends.

---

### Milestone 5 — Functions ✅

**Objetivo:** Suportar declaração e chamada de funções.

**Features:**
- [x] Funções com retorno
- [x] Parâmetros
- [x] Chamada de função
- [x] Call convention System V AMD64

**Exemplo:**
```kof
add(Int a, Int b): Int {
    return a + b
}

main() {
    var result = add(3, 4)
    print(result)
}
```

**Mapeamento calling convention:**
| Parânero | Register |
|----------|----------|
| 1º Int/Long | %rdi |
| 2º Int/Long | %rsi |
| 3º Int/Long | %rdx |
| 4º Int/Long | %rcx |
| 5º Int/Long | %r8 |
| 6º Int/Long | %r9 |
| Float/Double | %xmm0-%xmm7 |
| Retorno | %rax (Int/Long), %xmm0 (Float/Double) |

**Testes:**
- [x] Funções com 0, 1, 2, 3+ parâmetros
- [x] Retorno de todos os tipos
- [x] Nested calls
- [x] JVM regressão

**Critério de sucesso:** funções nativas funcionam.

---

### Milestone 6 — Strings ✅

**Objetivo:** Suportar strings nativas.

**Decisão de design:** strings nativas são diferentes de java.lang.String.

**Representação:**
```
struct String {
    i64 length;
    i8* data;      // UTF-8
}
```

**Runtime mínimo:**
- `kof_string_create(const char* data, i64 length)` — aloca string
- `kof_string_print(String* s)` — imprime
- `kof_string_concat(String* a, String* b)` — concatena

**Exemplo:**
```kof
main() {
    var name = "World"
    print("Hello, " + name)
}
```

**Testes:**
- [x] String literal → objeto String
- [x] Concatenação
- [x] Print
- [x] JVM regressão

**Critério de sucesso:** strings funcionam nativamente.

---

### Milestone 7 — Control Flow ✅

**Objetivo:** Suportar if/else, while, for.

**Features:**
- [x] If/else com branching
- [x] While loop
- [x] For loop
- [x] Comparisons (incluindo long/float/double)

**Exemplo:**
```kof
main() {
    var i = 0
    while (i < 10) {
        print(i)
        i = i + 1
    }
}
```

**Critério de sucesso:** controle de fluxo nativo funcional.

---

### Milestone 8 — Arrays ✅

**Objetivo:** Suportar arrays nativos.

**Representação:**
```
struct Array {
    i64 length;
    i8* data;      // dados brutos
}
```

**Testes:**
- [x] Criação de array
- [x] Acesso por índice
- [x] Array length
- [x] JVM regressão

---

### Milestone 9 — Value Types / Structs ✅

**Objetivo:** Records como structs nativos.

**Representação:**
```kof
record Point(Int x, Int y)
```

Gera:
```
struct Point {
    i32 x;
    i32 y;
}
```

**Alocação:**
- Stack allocation para tamanhos conhecidos
- Heap allocation via runtime

**Testes:**
- [x] Criação de struct
- [x] Acesso a campos
- [x] Passagem por valor
- [x] JVM regressão

---

### Milestone 10 — Objects ✅

**Objetivo:** Classes com herança e dispatch.

**Features:**
- [x] Object layout + virtual dispatch
- [x] Field access
- [x] Constructors
- [ ] `super.metodo()` contra classes do classpath (SUP001)

**Exemplo:**
```kof
class Animal(String nome) {
    falar(): String {
        return nome
    }
}

class Cachorro(String raca) extends Animal {
    override falar(): String {
        return nome + " late"
    }
}
```

**Dispatch:** vtable para virtual dispatch.

**Critério de sucesso:** polimorfismo nativo funcional.

---

### Milestone 11 — Exceptions ✅

**Objetivo:** Suportar try/catch nativo.

**Mecanismo:** implementação manual (sem LLVM) — unwinding próprio,
`try/catch/finally` com cleanup.

**Testes:**
- [x] Throw/catch
- [x] Finally
- [x] Stack unwinding
- [x] JVM regressão

---

### Milestone 12 — Generics ✅

**Objetivo:** Suportar generics nativos.

**Estratégia:** erasure (idêntica ao JVM), com `T` primitivo/Boxed
substituído em compile-time (`Box<Int>` → `Int`).

```kof
class Box<T>(T value) {
    get(): T = value
}
```

**Testes:**
- [x] Tipos genéricos básicos
- [x] Múltiplas instanciações
- [x] `Box<T>` com `T` primitivo (`Box<Int>` + `println` nativo)
- [x] JVM regressão

---

## Dependências

### Runtime nativo (mínimo)

Módulo `kof-runtime` com:
- `kof_alloc.c` — arena allocator
- `kof_string.c` — string operations
- `kof_io.c` — print, read
- `kof_runtime.c` — initialization

Compilado como `.a` estático, linkado pelo `ld`.

## Plano de testes

```
tests/
├── jvm/
│   ├── records/        ← testes existentes
│   ├── classes/
│   └── regression/     ← TODOS devem passar
├── native/
│   ├── hello/
│   ├── primitives/
│   ├── functions/
│   ├── strings/
│   ├── control-flow/
│   ├── arrays/
│   ├── structs/
│   ├── objects/
│   ├── exceptions/
│   └── generics/
└── regression/
    ├── jvm-and-native/ ← testes que validam ambos
    └── jvm-only/       ← testes específicos JVM
```

**Regra:** toda mudança no type system ou AST roda testes de JVM e Native.

---

## Riscos e mitigações

| Risco | Milestone | Mitigação |
|-------|-----------|-----------|
| Assembly manual complexo | 7+ | Implementar incrementalmente |
| Calling convention incorreta | 5 | Testes exaustivos com muitos parâmetros |
| Strings nativas diferentes de Java | 6 | Documentar claramente, não misturar |
| GC precisa ser implementado | 9+ | Começar com arena, evoluir para tracing GC |
| Exceptions nativas complexas | 11 | Implementação manual, não usar LLVM |
| Regressão JVM | Todos | Testes de regressão obrigatórios |

---

## Timeline estimada

| Milestone | Status | Esforço |
|-----------|--------|---------|
| 0 — Baseline | ✅ Concluído | 0.5 dia |
| 1 — Target Abstraction | ✅ Concluído | 1 dia |
| 2 — Backend Skeleton | ✅ Concluído | 1 dia |
| 3 — Hello World | ✅ Concluído | 3-5 dias |
| 4 — Primitives | ✅ Concluído | 2-3 dias |
| 5 — Functions | ✅ Concluído | 3-5 dias |
| 6 — Strings | ✅ Concluído | 3-5 dias |
| 7 — Control Flow | ✅ Concluído | 3-5 dias |
| 8 — Arrays | ✅ Concluído | 2-3 dias |
| 9 — Value Types | ✅ Concluído | 5-7 dias |
| 10 — Objects | ✅ Concluído | 7-10 dias |
| 11 — Exceptions | ✅ Concluído | 5-7 dias |
| 12 — Generics | ✅ Concluído | 5-7 dias |
| 13 — Target separation | ✅ Concluído | — |
| 14 — Free-list GC | ✅ Concluído | — |
| 15 — kof_db SQLite | ✅ Concluído | — |
| 16 — spawn/await pthread | ✅ Concluído (31/08) | — |
| 17 — FP real (XMM) | ✅ Concluído (31/08) | — |
| 18 — JSON completo | ✅ Concluído (31/08) | — |

**Em desenvolvimento:** MySQL nativo completo, GC mark-sweep, riscv64/aarch64 (codegen).

---

## Próximo passo

[Arquitetura do KofNative →](architecture.md)