# Arquitetura do KofNative

> **Kof 0.3.22-beta — Target separation + free-list GC + kof_db MySQL**

## Visão geral

KofNative é a extensão do compilador Kof para gerar binários nativos Linux x86-64. Não substitui o backend JVM — funciona em paralelo.

```
                   Kof Source (.kf)
                         │
                         ▼
                       Lexer
                         │
                         ▼
                       Parser
                         │
                         ▼
                        AST
                         │
                         ▼
                 Semantic Analysis
                         │
                         ▼
                    Kof IR (compartilhado) — intention->Kof->frontend->IR->backend->runtime
                     /       |       \
                    /         \
                   ▼           ▼
            JVM Backend    Native Backend
                   │           │
                   ▼           ▼
               .class       ELF .o
                   │           │
                   ▼           ▼
               javac/jar     ld → executável
```

## Componentes do compilador atual

### Backend-agnostic (reutilizáveis sem alteração)

| Componente | Arquivo | Status |
|------------|---------|--------|
| Token | `Token.java` | ✅ Reutilizável |
| TokenType | `TokenType.java` | ✅ Reutilizável |
| Lexer | `Lexer.java` | ✅ Reutilizável |
| AST | `AstNodes.java` | ✅ Reutilizável |
| Parser | `Parser.java` | ✅ Reutilizável |
| SourcePosition | `SourcePosition.java` | ✅ Reutilizável |
| Diagnostic | `Diagnostic.java` | ✅ Reutilizável |
| DiagnosticCollector | `DiagnosticCollector.java` | ✅ Reutilizável |
| CompilationResult | `CompilationResult.java` | ✅ Reutilizável |
| Type | `Type.java` | ✅ Reutilizável |
| SymbolTable | `SymbolTable.java` | ✅ Reutilizável |

### Backend-coupled (específicos de cada target)

| Componente | Arquivo | Target | Status |
|------------|---------|--------|--------|
| Backend | `Backend.java` | Ambos | ✅ Interface comum |
| Target | `Target.java` | Todos | ✅ Enum `JVM/NATIVE/NATIVE_RISCV64/NATIVE_AARCH64/JS/ANDROID` + `isNative()`/`nativeArch()` + `parseTarget native.risc/arm` |
| CompilerDriver | `CompilerDriver.java` | Ambos | ✅ Orquestrador parametrizado |
| JvmBackend | `JvmBackend.java` | JVM | ✅ Funcional via ASM |
| NativeBackend | `NativeBackend.java` | Nativo | ✅ Funcional via assembly |

## O pipeline de compilação

```text
Kof Source (.kf)
    │
    ▼
  Lexer → tokens
    │
    ▼
  Parser → AST
    │
    ▼
  IR (compartilhada)
    │
    ├──────────► JVM Backend → .class
    │
    └──────────► Native Backend → ELF
```

## Onde o NativeBackend se conecta

```
CompilerDriver.compile(sourceFile, outputDir, target)
  │
  ├─── target == JVM ──→ lowerToIR() → JvmBackend.emit()
  │
  └─── target == NATIVE → lowerToIR() → NativeBackend.emit()
```

### Mudanças implementadas em CompilerDriver

1. **Parâmetro `target`** no método `compile()`
2. **Interface `Backend`** para desacoplar
3. **Seleção baseada no target** — `new JvmBackend()` ou `new NativeBackend()`
4. **Helpers separados** — `toDescriptor()`, `toInternalName()`, `computeAccess()` ficam nos backends

### Estrutura atual

```java
// Backend.java (interface)
interface Backend {
    void emit(Object irModule, Path outputDir) throws IOException;
}

// CompilerDriver.java (modificado)
public CompilationResult compile(Path sourceFile, Path outputDir, Target target) {
    // lexer, parser, AST — inalterados
    // ...
    
    Backend backend = switch (target) {
        case JVM -> new JvmBackend();
        case NATIVE -> new NativeBackend();
    };
    
    var ir = lowerToIR(unit, diagnostics);
    backend.emit(ir, outputDir);
}
```

## O backend nativo atual

O backend nativo gera assembly x86-64, que é montado e linkado:

```text
Kof IR
    │
    ▼
NativeBackend.emit()
    │
    ▼
Assembly x86-64 (.s)
    │
    ▼
as → Objeto (.o)
    │
    ▼
ld → Executável (ELF)
```

### Exemplo de assembly gerado

```kf
main() = print("Hello")
```

```asm
.section .data
hello: .asciz "Hello"

.section .text
.globl _start
_start:
    mov $1, %rax
    mov $1, %rdi
    lea hello(%rip), %rsi
    mov $5, %rdx
    syscall
    
    mov $60, %rax
    xor %rdi, %rdi
    syscall
```

### Calling convention

O backend nativo usa a System V AMD64 ABI:

| Parânero | Registrador |
|----------|-------------|
| 1º Int/Long | %rdi |
| 2º Int/Long | %rsi |
| 3º Int/Long | %rdx |
| 4º Int/Long | %rcx |
| 5º Int/Long | %r8 |
| 6º Int/Long | %r9 |
| Float/Double | %xmm0-%xmm7 |
| Retorno | %rax (Int/Long), %xmm0 (Float/Double) |

## Riscos

| Risco | Impacto | Mitigação |
|-------|---------|-----------|
| Assembly manual complexo | Alto | Implementar incrementalmente |
| Calling convention incorreta | Alto | Testes exaustivos |
| Strings nativas diferentes de Java | Médio | Runtime mínimo em C |
| GC nativo precisa ser implementado | Alto | Começar com arena allocator |
| ABI Linux x86-64 precisa ser correta | Alto | Usar Linux syscalls diretos |

## Checklist de funcionalidade

- [x] Lexer funcional
- [x] Parser funcional
- [x] IR definida
- [x] Backend nativo funcional
- [x] CLI com --target=native
- [x] Records geram structs (free-list GC em 0.2.0)
- [x] Funções main funcionam
- [x] Strings funcionam
- [x] println funciona
- [x] Controle de fluxo
- [x] Classes com herança (virtual dispatch; `super.metodo()` = SUP001)
- [x] Exceptions (try/catch/finally + unwinding)
- [x] Generics (erasure; `Box<T>` com `T` primitivo/Boxed)
- [x] `spawn`/`await` via pthread (31/08 — CONC001 fechado)
- [x] Ponto flutuante XMM real (`vcvtsi2sd`/`mulsd`, dtoa via snprintf — FLT001/JSN001 fechados)
- [x] JSON objetos/records + arrays completos (JSN002/JSN003/JSN001 fechados)
- [x] SQLite nativo (link direto da `.so`)
- [ ] MySQL/MariaDB nativo (wire protocol: auth scramble SHA-1 feito; WIP)
- [ ] GC mark-sweep (hoje free-list `kof_free_head`)
- [ ] riscv64/aarch64 (codegen ainda x86_64 — placeholder via qemu)

## Próximo passo

[Opções de Backend →](backend-options.md)