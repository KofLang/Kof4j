# Opções de Backend Native

> **0.3.22-beta — Native free-list GC, Target separation (native.risc/arm), kof_db SQLite+MySQL**

## Status atual

O backend nativo já está implementado e funcional (0.3.22-beta). Ele gera assembly x86-64 / riscv64 / aarch64 diretamente (Target separation), com free-list GC no x86-64 e `kof_db` MySQL WIP, sem usar LLVM ou outras bibliotecas externas.

## Abordagem implementada: Assembly direto

### O que é

Gerar código x86-64 e arquivos ELF manualmente em Java, sem dependências externas.

### Como funciona

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

### Vantagens

- **Zero dependências** — não precisa de LLVM, JavaCPP ou qualquer biblioteca
- **Controle total** — sabemos exatamente o que está sendo gerado
- **Simplicidade** — assembly x86-64 é relativamente simples para operações básicas
- **Portabilidade** — funciona em qualquer Linux x86-64

### Desvantagens

- **Sem otimizações** — não temos register allocation, instruction scheduling, etc.
- **Código manual** — cada instrução precisa ser implementada à mão
- **Manutenção** — adicionar novas features requer trabalho manual

### O que ganhamos de graça

- Nada de uma biblioteca

### O que precisamos implementar

- ~50+ instruções x86-64 (já implementadas)
- Calling convention System V AMD64 (já implementada)
- String/data sections (já implementado)
- Syscalls Linux (já implementadas)

## Opções avaliadas (historial)

### 1. LLVM via JavaCPP (rejeitado)

**O que é:** Wrapper Java para o LLVM C API usando JavaCPP.

**Por que rejeitado:**
- Dependência de ~100MB
- Complexidade de integração
- O projeto queria zero dependências

### 2. Cranelift (rejeitado)

**O que é:** Framework de code generation em Rust.

**Por que rejeitado:**
- Não existe API Java madura
- Requer binding Java → Rust

### 3. Compilar para C (rejeitado)

**O que é:** Gerar código C, usar GCC/Clang como backend.

**Por que rejeitado:**
- Seria um transpiler, não um compilador nativo
- Não permite controle fino do código gerado

### 4. System Backend (escolhido)

**O que é:** Gerar código x86-64 e arquivos ELF manualmente em Java.

**Por que escolhido:**
- Zero dependências
- Controle total
- Implementação incremental possível
- Funciona para operações básicas

## Matriz comparativa (historial)

| Critério | LLVM/JavaCPP | Cranelift | System ELF | C transpiler |
|----------|:---:|:---:|:---:|:---:|
| Integração Java | 6 | 2 | 5 | 9 |
| Performance | 10 | 7 | 5 | 10 |
| Otimizações | 10 | 7 | 2 | 10 |
| Dependências | 6 | 8 | 10 | 5 |
| Manutenção | 7 | 3 | 3 | 8 |
| **Ponderado** | **7.6** | **4.4** | **5.0** | — |

## Decisão final

**System Backend (assembly direto)** é a escolha correta para o primeiro backend native.

**Motivo principal:** queremos provar que Kof pode gerar código nativo. Assembly direto minimiza dependências e maximiza o controle.

**Quando reconsiderar:**
- Se precisarmos de otimizações complexas → avaliar LLVM
- Se precisarmos de suporte a múltiplas arquiteturas → avaliar LLVM
- Se o código manual ficar incontrolável → avaliar LLVM

## O que precisamos implementar

### Já implementado

1. **Instruções básicas** — mov, add, sub, mul, div, cmp (+ FP XMM: `vcvtsi2sd`, `mulsd` — FLT001 fechado)
2. **Chamadas de função** — calling convention System V AMD64
3. **Strings** — string literals e operações
4. **Records** — structs com campos
5. **Funções** — declaração e chamada
6. **Syscalls Linux** — write, exit, open, read, close...
7. **Controle de fluxo, classes (herança/dispatch), exceptions (unwinding), generics (erasure)**
8. **`spawn`/`await` via pthread** (31/08 — CONC001 fechado), allocator thread-safe (futex)
9. **JSON completo** (objetos/records/arrays — JSN001/002/003 fechados) + **SQLite nativo**

### Em desenvolvimento

1. **MySQL/MariaDB nativo** — wire protocol sobre sockets (auth scramble SHA-1 feito)
2. **GC mark-sweep** — hoje free-list `kof_free_head`
3. **riscv64/aarch64** — codegen ainda x86_64 (placeholders via qemu)

## Exemplo de implementação

### Geração de assembly para Hello World

```kf
main() = print("Hello, World!")
```

```asm
.section .data
hello: .asciz "Hello, World!\n"

.section .text
.globl _start
_start:
    # write(1, hello, 14)
    mov $1, %rax
    mov $1, %rdi
    lea hello(%rip), %rsi
    mov $14, %rdx
    syscall
    
    # exit(0)
    mov $60, %rax
    xor %rdi, %rdi
    syscall
```

### Geração de assembly para records

```kf
record Point(Int x, Int y)
```

```asm
.section .text
.globl Point_init
Point_init:
    # Point(x, y)
    mov %rdi, 8(%rax)
    mov %rsi, 16(%rax)
    ret

.globl Point_x
Point_x:
    mov 8(%rax), %rax
    ret

.globl Point_y
Point_y:
    mov 16(%rax), %rax
    ret
```

## Dependências

### Runtime nativo (mínimo)

O backend nativo requer um runtime mínimo em C:

- `kof_alloc.c` — arena allocator
- `kof_string.c` — operações com strings
- `kof_io.c` — print, read
- `kof_runtime.c` — inicialização

Compilado como `.a` estático, linkado pelo `ld`.

## Próximo passo

[Roadmap →](roadmap.md)