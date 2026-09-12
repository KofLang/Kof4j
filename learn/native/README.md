# Multiplatform — Uma Linguagem, Múltiplos Mundos

> **Kof 0.3.22-beta — set 2026 — targets jvm/native/native.risc/native.arm/js/kofc — `intention->Kof->frontend->IR->backend->runtime`**

## A visão

Kof não é apenas uma linguagem para a JVM. É uma linguagem que pode compilar para diferentes targets, mantendo a mesma sintaxe e semântica.

```text
                         KOF
                          │
                    Kof Compiler (frontend → IR)
                          │
                       Kof IR  — intention->Kof->frontend->IR->backend->runtime
                          │
          ┌───────────────┼────────────────┬───────────┐
          │               │                │           │
       Kof4J          KofNative         KofJS      KofScript/KofC
          │          ┌────┼────┐          │           │
          ▼          ▼    ▼    ▼          ▼           ▼
        JVM       x86-64 riscv arm    ES Module   Globals/C-ELF
       .class      ELF   ELF  ELF      .mjs        repl/kof c
```

## Os backends

### Kof4J (JVM)

O backend JVM gera bytecode `.class` que roda em qualquer JVM.

```kf
record Point(Int x, Int y)
```

```bash
kof build point.kf --target=jvm
# Gera: Point.class
```

**Vantagens:**
- Compatibilidade total com ecossistema Java
- Acesso a milhões de bibliotecas
- JIT compilation
- Garbage collection sofisticada
- Portabilidade (qualquer JVM)

### KofNative (Nativo: x86-64 / riscv64 / aarch64)

O backend nativo gera ELF x86-64 (`native`), riscv64 (`native.risc`) e aarch64 (`native.arm`). x86-64 é estável: free-list GC (`kof_free_head`, reuso `mmap`; mark-sweep pendente), `spawn`/`await` via pthread (31/08 — CONC001 fechado), ponto flutuante XMM real (`FLT001` fechado), JSON completo (objetos/records/arrays — JSN001/002/003 fechados), SQLite nativo e MySQL em progresso (wire protocol, auth scramble SHA-1). riscv/arm são placeholders (codegen ainda x86_64, cross via `as`/`ld` + qemu).

```kf
main() = print("Hello, World!")
```

```bash
kof build main.kf --target=native
# Gera: main (executável ELF)
./main
# Output: Hello, World!
```

**Vantagens:**
- Sem necessidade de JVM instalada
- Executável standalone
- Performance nativa
- Distribuição simples (apenas o binário)
- Ideal para ferramentas CLI e sistemas

### KofScript (0.3.22-beta)

`kof script` / `kof repl` — `let`/`const` no topo viram `KofScriptGlobals` persistentes, `--watch` re-executa; targets jvm/native/js.

```bash
kof run script.kf
# Executa diretamente sem compilar
```

**Vantagens:**
- Sem build step
- Execução imediata
- Ideal para automação e experimentos

### KofJS (JS) + KofC (kofc)

KofJS gera ES Modules via GraalJS (`kof.http` JVM+JS, HTTP002 Native). KofC (`kof c <file.c>`) compila subset C (`int` globals, `void` funcs, `if`/`while`/`*(int*)`/`&`) → ELF x86-64 nativo-only.

```bash
kof build app.kf --target=js   # ES Module
kof script app.ks --watch      # KofScript
kof c app.c --run               # KofC nativo-only
```

**Vantagens:**
- Mesma linguagem para backend e frontend
- Sem necessidade de aprender JavaScript
- Acesso ao DOM e APIs do navegador

## Como funciona

### Pipeline de compilação

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
  IR (compartilhado, intention->Kof->frontend->IR->backend->runtime)
    │
    ├──────────► JVM Backend → .class
    │
    ├──────────► Native Backend → ELF (x86-64 free-list / riscv / arm)
    │
    ├──────────► JS Backend → .mjs
    │
    ├──────────► KofScript → Globals+IR→backend
    │
    └──────────► KofC → ELF nativo-only
```

### IR compartilhada

A representação intermediária (IR) é compartilhada entre todos os backends. Isso permite:

1. **Mesma linguagem** — não existem dialetos para diferentes targets
2. **Mesma semântica** — o significado do código não muda
3. **Otimizações compartilhadas** — melhorias na IR beneficiam todos os backends
4. **Fácil adição de novos backends** — basta implementar a tradução IR → target

### Exemplo multiplatform

```kf
record Point(Int x, Int y)

main() {
    var p = Point(3, 7)
    println(p)
}
```

**JVM:**
```bash
kof build main.kf --target=jvm
java -cp . main
# Output: Point[x=3, y=7]
```

**Nativo:**
```bash
kof build main.kf --target=native
./main
# Output: Point[x=3, y=7]
```

**Mesmo código. Mesmo output. Targets diferentes.**

## Quando usar cada backend

| Backend | Use quando | Exemplos |
|---------|------------|----------|
| **JVM** | Precisa de ecossistema Java, bibliotecas, frameworks | APIs Spring, microserviços, aplicações corporativas |
| **Nativo** | Precisa de executável standalone, sem JVM | Ferramentas CLI, containers, sistemas, utilitários |
| **Script** | Precisa de execução rápida, sem build | Automação, scripts, prototipação |
| **JS** | Precisa de frontend web | Interfaces web, SPAs, PWAs |

## Status atual

| Backend | Status | Descrição |
|---------|--------|-----------|
| **JVM** | ✅ Funcional | Gera `.class` via ASM (bytecode V21, exception table, virtual threads) |
| **Nativo** | ✅ Funcional | Gera ELF x86-64 via assembly (free-list GC, spawn/pthread, FP XMM, SQLite); riscv/arm placeholders |
| **Script** | ✅ KofScript (let→Globals, repl, --watch) | Runtime interativo |
| **KofJS** | ✅ alpha | ES Modules via GraalJS embarcada; `kof.http` por interop Java HttpClient |
| **KofC** | ✅ nativo-only | C subset → ELF x86-64 |

## Arquitetura do compilador

```text
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
                     Kof IR (compartilhada)
                      /       \
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

## Documentação

- [Arquitetura do KofNative](architecture.md) — detalhes da arquitetura multiplatform
- [Opções de Backend](backend-options.md) — análise das opções de backend nativo
- [Roadmap](roadmap.md) — plano de desenvolvimento do backend nativo

## Próximo passo

[Arquitetura do KofNative →](architecture.md)