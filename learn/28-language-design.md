# 28 — Design da Linguagem

> **Kof 0.3.22-beta — set 2026 — `intention->Kof->frontend->IR->backend->runtime`**

## Filosofia

Kof existe porque Java é uma das plataformas mais poderosas do mundo, mas exige uma quantidade absurda de código para expressar ideias simples.

A pergunta central de Kof é:

> "Estamos eliminando complexidade real ou apenas escondendo complexidade?"

Se estamos apenas escondendo complexidade, a feature precisa ser reconsiderada.

## O paradigma da intenção

> Não é um paradigma formal — é a orientação a objetos levada ao extremo.

A cadeia: **intenção → Kof → compilador → backend**. O programador escreve
*o que* quer; o compilador e o runtime decidem *como*, por target e por
convenção. O mecanismo nunca sobe para o código do usuário:

| Intenção | Você escreve | O mecanismo fica com |
|----------|--------------|----------------------|
| paralelismo | `spawn tarefa()` | virtual threads (JVM) / pthread (Native, 31/08) |
| HTTP | `app.get("/users/:id") { ... }` | servidor próprio, sem container |
| HTTP client | `http.get(url)` | `kof.http` JVM+JS (HTTP002 Native) |
| UI | `Window(...)`, `Button("+1", () -> ...)` | KofJS + webview nativo |
| JSON | `json.decode<User>(body)` | engine + binding por tipo |
| cor | `Palette.red` | Int 32-bit, canais por bitwise |
| nullable | `String?` | verificação em compile-time |
| pattern | `case String s` / `Point(x,y)` | instanceof+checkcast / field loads por backend |
| script | `let x = 5` no topo | `KofScriptGlobals` (repl --watch) |

A intenção compila em todos os alvos; o alvo que não consegue realizá-la
reporta em compile-time com código de gap (`HTTP002`, `DB001`, `WEB002`) — nunca
silenciosamente. Detalhes em `docs/philosophy.md`.

## A visão multiplatform

Kof não é apenas uma linguagem para a JVM. É uma linguagem que pode compilar para diferentes targets:

```text
                         KOF
                          │
                    Kof Compiler (frontend)
                          │
                       Kof IR
                          │
          ┌───────────────┼────────────────┬───────────┐
          │               │                │           │
       Kof4J          KofNative         KofJS      KofScript/KofC
          │          ┌────┼────┐          │           │
          ▼          ▼    ▼    ▼          ▼           ▼
        JVM       x86-64 riscv arm    ES Module   Globals/C-ELF
       .class      ELF   ELF  ELF      .mjs        repl/kof c
```

**A linguagem não muda. O target muda.**

Isso é uma decisão de design fundamental. A mesma fonte Kof pode gerar (0.3.22-beta):
- Bytecode JVM para aplicações que precisam do ecossistema Java
- Executáveis nativos x86-64 / riscv64 (`native.risc`) / aarch64 (`native.arm`) para ferramentas CLI e sistemas (Target separation)
- ES Modules para o navegador/webview via KofJS (ver [capítulo 37](37-kofjs.md))
- Execução direta via KofScript (`let`→`KofScriptGlobals`) e C via KofC (`kof c` nativo-only)

## Decisões de design

### Menos ceremony, não menos informação

```java
// Java: 40 linhas
public final class User {
    private final String name;
    public User(String name) { this.name = name; }
    public String name() { return name; }
    // equals, hashCode, toString...
}

// Kof: 1 linha
record User(String name)
```

A segunda forma gera exatamente a mesma coisa que a primeira. Não removemos informação — removemos repetição.

### A JVM é o runtime

Kof não inventa:
- garbage collector
- scheduler
- modelo de memória
- sistema de threads

A JVM já faz isso. Kof usa o que já existe.

Para o backend nativo, Kof usa:
- assembly x86-64 direto
- Linux syscall conventions
- Runtime mínimo em C

### Native runtime (0.2.0)

Native usa **free-list GC** (`kof_free_head`, reuso `mmap`, mark-sweep pendente), `spawn` via **pthread** (31/08 — `CONC001` fechado), ponto flutuante **XMM real** (`vcvtsi2sd`/`mulsd`, `FLT001` fechado) e JSON completo (objetos/records/arrays — `JSN001/002/003` fechados). `kof_db` traz **SQLite nativo** e MySQL em progresso (wire protocol, auth scramble SHA-1). Nada disso vaza para o código Kof — é `intention->Kof->frontend->IR->backend->runtime`.

### Compile-time > runtime

Se algo pode ser resolvido em compile-time, deve ser.

```kf
var user = User("Mel")
```

O compilador sabe que `user` é um `User`. Isso não precisa de reflection em runtime.

### Java interoperability é sagrada

Código Kof:
- chama código Java
- é chamado por código Java
- usa bibliotecas Java
- funciona com frameworks Java

Isso não é negociável.

### Um frontend, múltiplos backends

O compilador possui um frontend único que gera uma representação intermediária (IR). A partir dessa IR, diferentes backends podem transformar o mesmo programa:

```text
Kof Source
    │
    ▼
Lexer → Parser → AST → IR
    │
    ├──────────► JVM Backend → .class
    ├──────────► Native Backend → ELF
    └──────────► Script Backend → Runtime
```

Isso permite que a linguagem cresça sem se fragmentar.

## Sintaxe

### Records

Escolhemos `record` porque é o construto mais simples para dados imutáveis:

```kf
record Point(Int x, Int y)
```

### Modifiers

Modifiers são explícitos quando importantes:

```kf
public class User(String name) { ... }
private String password
static Int count
```

### Funções

Funções são declaradas sem palavra-chave — o nome vem primeiro. O tipo de
retorno pode ser prefixado (`String nome()`) ou sufixado (`nome(): String`):

```kf
main() = print("Hello")

dobro(Int x) = x * 2

somar(Int a, Int b): Int {
    return a + b
}
```

## Type System

Kof é fortemente e estaticamente tipado.

```kf
var nome = "Mel"     // tipo: String (inferido)
String nome = "Mel"  // tipo: String (explícito)
```

Ambos são estaticamente tipados. A inferência não muda isso.

## O que não fazemos

- Não criamos macros
- Não criamos metaclasses
- Não criamos macros em compile-time
- Não criamos VM própria (usamos a JVM)
- Não criamos runtime próprio (usamos o sistema operacional)

Cada feature precisa provar que vale a complexidade.

## Multiplatform philosophy

A filosofia multiplatform de Kof é baseada em três princípios:

1. **Um compilador, múltiplos targets** — o mesmo código fonte pode gerar código para diferentes plataformas
2. **A linguagem não muda** — não existem "dialetos" para diferentes targets
3. **O backend é uma decisão do compilador** — o desenvolvedor escolhe o target, não a linguagem

Isso permite que Kof seja usada para:
- Aplicações corporativas na JVM
- Ferramentas CLI nativas
- Scripts interativos
- Aplicações web via KofJS

## Próximo passo

[Internals do Compilador →](29-compiler-internals.md)