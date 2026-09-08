# Semântica — Modelo de Execução

**Status:** Stable (exceto onde etiquetado) · **Evidência:** `CompilerDriver.java`, `StatementLowerer.java`, `ExpressionLowerer.java`, runtime por target

Este documento define **o significado de um programa Kof** — o que acontece
quando ele roda — de forma independente do backend.

---

## 1. Início e fim da execução

1. O programa começa no `main` (exatamente um por módulo — PKG002).
2. `main` é chamado com `String[]` (vazio se o programa não declara `args`).
3. Blocos `application { onStart }` rodam **antes** do corpo do `main` do
   usuário; `onShutdown` **depois** (desugaring, `CompilerDesugar.java:47`).
4. Statements de `main` executam **sequencialmente**, em ordem de fonte.
5. **Tarefas `spawn` pendentes são aguardadas antes do programa terminar**
   (join implícito — `SpawnStmt` javadoc, `AstNodes.java:358-363`).
6. O programa termina; o exit code é 0 (salvo exceção não capturada ou
   `assert` falho no harness de teste).

> **Unspecified**: se o `Int` retornado por `Int main()` vira exit code. O
> emit JVM ignora o retorno de `main` (é `void main` no bytecode). **SG-018.**

---

## 2. Ordem de avaliação

- **Esquerda para direita** nos operandos de binários (`emitExpression` emite
  `left` antes de `right`, `ExpressionLowerer.java:177-185`).
- **Arguments** de uma chamada são avaliados em ordem, esquerda→direita.
- **`&&`/`||`** são short-circuit (lado direito pode não ser avaliado) —
  **exceto no target JS**, onde o short-circuit é desligado (SG-006).
- **Encadeamento de postfix** (`a.b().c()[d]`) é avaliado da esquerda para a
  direita, receiver antes do membro.
- **Efeitos colaterais em atribuição**: o lado direito é avaliado antes de
  escrever no lado esquerdo.

---

## 3. Escopo e tempo de vida

- **Bloco** `{ … }` abre escopo; declarações vivem até o fim do bloco.
- **`var`/`val`** locais: tempo de vida do bloco. Sem heap-allocation de
  locais (são slots de frame), exceto quando capturados por lambda (aí vivem
  enquanto a lambda viver — snapshot ou Box).
- **Campos de objeto**: vivem enquanto o objeto viver.
- **Gerenciamento de memória**: **delegado ao target**.
  - JVM: GC do JVM.
  - Native: allocator próprio (free-list / bump atômico + `kof_gc_collect`
    mark-sweep em x86_64; riscv64: bump + sem GC completo — **Target-specific**).
  - JS: GC do engine.
  - **A linguagem não especifica** quando um objeto é coletado. **Unspecified**
    (intencional — é o GC do host).

---

## 4. Semântica de valor vs referência

- **Primitivos** (`bool byte short int long float double char`): valor.
- **`string`**: valor por conteúdo (imutável; `==` compara conteúdo).
- **`record`**: valor por conteúdo (`==` compara campo a campo).
- **`class`**: referência (identidade; `==` compara referência; campos mutáveis).
- **`enum`**: valor = nome (`String`).
- **Coleções** (`List/Map/Set`): referência (objeto mutável).
- Passagem a função: **por valor** (para referência, o valor é a referência —
  mutar o objeto é visível; reatribuir o parâmetro não é).

---

## 5. Exceções

- **Exceções são `String`** (`throw "msg"`). Não há classe de exceção.
- `throw` propaga para o `catch (String e)` mais próximo na pilha de chamada.
- `finally` executa sempre (inclusive durante propagação).
- Exceção não capturada:
  - JVM: `RuntimeException(msg)` → stack trace + exit ≠ 0.
  - Native: `kof_panic` → mensagem + exit ≠ 0.
  - JS: throw da string → não capturada → erro no runner.
  - **Target-specific** na representação, **Stable** no efeito (aborta com
    mensagem e exit ≠ 0).
- **Não há** checked exceptions, nem `throws` validado (a cláusula `throw T`
  é parseada mas **não checada** — SG-019).

---

## 6. Concorrência

- `spawn` cria uma **tarefa concorrente** (thread no JVM via virtual thread;
  `pthread_create` no Native x86_64; `clone(220)` no riscv64; `Promise`/worker
  no JS). **Implementation-defined** o mecanismo.
- `await h` **bloqueia** o chamador até `h` (Handle<T>) produzir valor.
- `awaitTimeout(h, ms)` lança exceção se expirar.
- `Channel<T>`: `send`/`receive` (buffered/unbuffered — **Unspecified** a
  capacidade default).
- **Não há** modelo de memória formalizado (atomicidade, visibilidade entre
  threads, happens-before). **Unspecified** (SG-020) — concorrência é
  "melhor esforço" delegada ao target.

---

## 7. Efeitos de entrada/saída

- `println` escreve na stdout (com newline). **UTF-8** nos 3 targets (verificado
  no sweep R6).
- `print` sem newline (se existir — **Unspecified**; `println` é o documentado).
- Ordem de `println` entre threads `spawn`: **não garantida** sem
  sincronização (no riscv64 foi feito `writev` atômico para reduzir interleave
  — **Implementation-defined**).

---

## 8. Determinismo

- Para um programa **sem concorrência e sem I/O**, o resultado é determinístico
  em todos os targets (mesma IR, mesma ordem de ops).
- **Aritmética de ponto flutuante** segue IEEE-754 do host — **pode divergir**
  em casos extremos entre targets (FLT001 documenta o estado). **Target-specific.**
- `hash`/ordem de `Map.keys`/`Set`: **Unspecified** (depende da estrutura do
  runtime do target — JVM usa `HashMap`, Native usa lista linear).

---

## 9. O que a linguagem NÃO define (resumo de Unspecified)

| Ponto | Estado |
|---|---|
| Quando objetos são coletados | Unspecified (GC do host) |
| Exit code de `Int main()` | Unspecified (SG-018) |
| Modelo de memória concorrente | Unspecified (SG-020) |
| Ordem de iteração de Map/Set | Unspecified |
| `throws` como contrato | parseado, não checado (SG-019) |
| Semântica de classes aninhadas | Unspecified (SG-016) |
| Sobrecarga de função top-level | Unspecified (SG-011) |
| Valor de `x++` como expressão | Implementation-defined |
| `<`/`>` em referências não-numéricas | não suportado |
