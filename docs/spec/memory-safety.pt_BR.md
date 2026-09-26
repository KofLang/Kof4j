[English](memory-safety.md) | [Português](memory-safety.pt_BR.md)

# Especificação de Segurança de Memória — Propriedade, Tempo de Vida, Empréstimo, Aliasing (D-MEMORY-SAFETY)

> **Status: Fase 2 EM DESENVOLVIMENTO** — Fase 1 FECHADA/aceita 25/09 (opção A); Fase 2
> destravada pela mantenedora 26/09 (chat: "fase 2 destravada"). `DECISIONS.md` §`D-MEMORY-SAFETY`.
> Baseado em `docs/spec/memory-safety-investigation.md` (Fase 0, FECHADA 25/09).
> Este documento formaliza o modelo de memória contra a superfície REAL do Kof.

---

## 1. Modelo Fundamental

### 1.1 O Contrato de Memória

Programas Kof executam em runtimes gerenciados (JVM, JS, Script) ou um runtime
freestanding com GC conservativo (Native x86-64, RV32I, Cortex-M3). O contrato
de memória é:

| Propriedade | Garantia |
|---|---|
| **Alocação** | Todo objeto/record/closure/array/box é alocado pelo runtime (`new` em JVM/JS, `kof_alloc` em Native). |
| **Desalocação** | Automática via GC (conservativo mark-sweep em Native; generacional na JVM; GC do navegador no JS). Nenhum `free`/`drop` visível ao usuário. |
| **Alcançabilidade** | Um objeto está vivo se alcançável a partir de raízes do GC (pilha, dados estáticos, GPRs no Native; raízes do GC na JVM/JS). |
| **Move/Copy** | Tipos primitivos (`bool`, `byte`, `short`, `int`, `long`, `float`, `double`, `char`) são **copiados** por valor. Todos os outros tipos (ClassType, FunctionType, Nullable, Array, List, Map, Set, Buffer, Handle, String) são **referência-semântica** — atribuição copia apenas o ponteiro. |
| **Mutabilidade** | Forçada no binding (`val` vs `var`) pela análise semântica (`SEM037`, `SEM038`, `SEM054`). O binding é congelado, não o objeto: `val l = listOf(1); l.add(2)` é legal. |
| **Nulabilidade** | `T?` é um tipo distinto. `val x: T? = null` legal. Desreferência sem estreitamento → `SEM049`. `x = null` literal → `SEM048` (proibido). Estreitamento flow-sensitive apenas em `x != null`. |

> **Não-objetivo**: nenhuma imutabilidade profunda, nenhum const transitivo,
> nenhum tipo linear/afim, nenhum borrow-checker à la Rust. O modelo entrega
> garantias através da superfície existente + fronteiras opt-in.

---

## 2. Propriedade (Ownership)

### 2.1 Definição

Todo objeto tem exatamente um **dono** em qualquer momento. O dono é o escopo
ou entidade responsável pelo fim da vida do objeto (desalocação).

| Tipo de Dono | Fim da Vida | Exemplos |
|---|---|---|
| **Escopo** | Fim do bloco/função onde o binding dono foi declarado | `val x = Objeto()` — dono é o bloco |
| **Raiz do GC** | Quando a raiz se torna inalcançável (conservativo no Native; preciso na JVM/JS) | Campos estáticos, slots de pilha, GPRs |
| **Container** | Quando o container é coletado / limpo | `List<T>` possui seus elementos; `Buffer` possui seu array de backing |
| **Closure** | Quando a closure é coletada | Variáveis capturadas (boxed se mutadas) |
| **FFI** | Explícito via `kof_ffi_release` / fechamento de arena | `Arena.ofConfined()` na JVM; buffers copiados no Native |

### 2.2 Regras de Propriedade

| Regra | Permitido | Proibido | Diagnóstico | Classificação |
|---|---|---|---|---|
| **O-01** Dono único | Cada objeto tem exatamente um escopo/container dono | Dois escopos reivindicando propriedade do mesmo objeto sem transferência | `MEM001` | Compile-time |
| **O-02** Transferência | Move explícito: `var a = b; b = null` (origem anulada) | Transferência implícita sem anular a origem explicitamente | `MEM002` | Compile-time |
| **O-03** Propriedade de container | `list.add(x)` → lista possui `x`; `list.clear()` libera | Container mantendo referência após clear sem anular elementos | `MEM003` | Compile-time |
| **O-04** Propriedade de closure | Closure possui suas capturas; capturas boxed são compartilhadas | Capturar variável un-boxed que escapa da closure | `MEM004` | Compile-time |
| **O-05** Propriedade FFI | Arena/buffer com tempo de vida explícito; sem transferência de propriedade implícita | Função FFI retornando ponteiro owned sem contrato explícito | `MEM005` | Compile-time + Runtime |

> **Nota**: Propriedade não é um qualificador de tipo no Kof. É uma propriedade
> semântica inferida da estrutura de escopo e transferências explícitas. O
> compilador rastreia propriedade para emitir diagnósticos nas fronteiras
> (FFI, spawn, mutação de container, fechar recurso).

---

## 3. Tempo de Vida (Lifetime)

### 3.1 Definição

O tempo de vida de um objeto é o intervalo da alocação à desalocação. No Kof,
tempos de vida são **gerenciados** pelo GC, não por chaves de escopo. A distinção
chave de Rust/C++: tempos de vida não fazem parte do sistema de tipos; são uma
propriedade de runtime tornada visível através de diagnósticos em pontos de
escape.

### 3.2 Regras de Tempo de Vida

| Regra | Permitido | Proibido | Diagnóstico | Classificação |
|---|---|---|---|---|
| **L-01** Sem dangling | GC garante nenhum ponteiro dangling (conservativo no Native) | Código do usuário criando ponteiros interiores que sobrevivem ao objeto | `MEM010` | Runtime (conservativo) |
| **L-02** Sem use-after-free | GC nunca recupera objetos alcançáveis | `kof_free` explícito + uso contínuo (apenas runtime) | `MEM011` | Runtime |
| **L-03** Sem double-free | `kof_free` chamado no máximo uma vez por alocação | `kof_free` chamado duas vezes no mesmo ponteiro | `MEM012` | Runtime |
| **L-04** Consciência de escape | Capturas de closure estendem tempo de vida; saída de escopo não encerra tempo de vida de objetos no heap | Retornar ponteiro interior de FFI sem anotação de tempo de vida | `MEM013` | Compile-time |
| **L-05** Tempo de vida de recurso | Handles de recurso (DB, Web, Arquivo, FFI) devem ser fechados explicitamente | Fechamento implícito na saída de escopo | `MEM014` | Compile-time |

> **Crítico**: Kof **não** tem semântica de destrutor (`Drop`/`__del__`).
> Limpeza de recurso é explícita (`close()`, `release()`, `close()`). O GC
> apenas recupera memória.

---

## 3. Empréstimo e Aliasing

### 3.1 Modelo de Empréstimo

Kof usa **aliasing compartilhado imutável** por padrão. Não existem empréstimos
exclusivos (`&mut`), nenhum borrow checker, nenhum parâmetro de tempo de vida.
O modelo:

- **Aliasing compartilhado é pervasivo e sempre permitido**: `var a = listOf(1); var b = a` — ambos alias da mesma lista.
- **Mutação através de um alias é visível em todos os aliases**: `b.add(2)` → `a` vê.
- **Nenhuma garantia de acesso exclusivo**: Sem `&mut`, nenhum borrow checker.
- **Segurança de mutação** é responsabilidade do programador nas fronteiras
  sensíveis a aliasing:
  - Chamadas FFI que escrevem através de buffer (`MEM020`)
  - Spawn com estado mutável compartilhado (`MEM021`)
  - Mutação de `List`/`Buffer` através de alias compartilhado (`MEM022`)

### 3.2 Regras de Empréstimo

| Regra | Permitido | Proibido | Diagnóstico | Classificação |
|---|---|---|---|---|
| **B-01** Aliasing compartilhado | `var a = listOf(1); var b = a` | — | — | — |
| **B-02** Mutação através de alias | `b.add(2)` visível em `a` | — | — | — |
| **B-03** Empréstimo FFI | `kof_ffi_call(ptr)` onde C pode escrever | Passar mesmo `Buffer` a duas chamadas FFI concorrentes sem sync | `MEM020` | Compile-time + Runtime |
| **B-04** Alias em spawn | `spawn { a.add(1) }` + `a.add(2)` no main | Mutação concorrente não sincronizada sem `join_all` | `MEM021` | Compile-time + Runtime |
| **B-06** Captura de closure | Por valor (snapshot imutável) ou boxed (mutado) | Capturar local mutável sem box quando escapa | `MEM023` | Compile-time |

> **Nota**: Sem empréstimo exclusivo não há garantia de ausência de data race
> no nível de tipo. Data races são prevenidos por disciplina + `join_all` +
> primitivas de sincronização explícitas (canais, futures). O compilador emite
> `MEM020`/`MEM021` nas fronteiras sensíveis a aliasing (stdlib/FFI/spawn).

---

## 4. Mutabilidade

### 4.1 Mutabilidade de Binding (val/var)

| Regra | Permitido | Proibido | Diagnóstico |
|---|---|---|---|
| **M-01** `val` congela binding | `val x = 1; x = 2` → `SEM037` | — | `SEM037` |
| **M-02** Escrita em componente de record | `val r = R(1); r.f = 2` → `SEM038` | — | `SEM038` |
| **M-03** Mutação de lista através de `val` | `val l = listOf(1); l.add(2)` | — | — |
| **M-04** Mutação de elemento de lista através de `val` | `val l = listOf(R(1)); l[0].f = 2` → `SEM054` | — | `SEM054` |

> **Sem imutabilidade profunda**: `val` congela o binding, não o grafo de objetos.
> É uma escolha de design deliberada (Lei da Simplicidade).

---

## 5. Move, Copy, Clone, Drop

| Conceito | Semântica Kof |
|---|---|
| **Move** | `var a = b; b = null` — transferência explícita, origem anulada. Não é operador da linguagem. |
| **Copy** | Tipos primitivos: sempre copy. Referências: apenas copy do ponteiro. Sem operador de deep copy. |
| **Clone** | `x.clone()` onde `x` implementa `Clone` (protocolo stdlib). Não automático. |
| **Drop/Destrutor** | **Não existe**. Nenhum destrutor, nenhum finalizador. Recursos: `close()` explícito. |

---

## 6. Escape e Captura

### 6.1 Captura de Closure

| Regra | Comportamento |
|---|---|
| **E-01** Captura por valor | Locais não mutados: capturados por valor (snapshot) |
| **E-02** Captura por box | Locais mutados: re-boxed em `Box` de heap compartilhado |
| **E-03** Escape | Closure possuindo capturas estende tempo de vida até a closure ser coletada |
| **E-03** Scanner de captura | Pré-pass marca locais mutados (`CompilerCaptureScanner.java`) — exaustividade requerida |

---

## 7. Fronteiras de Propriedade FFI

| Fronteira | JVM | Native |
|---|---|---|
| **String in** | Copy para JVM `String` | Copy para Kof String |
| **String out** | Copy de JVM String | Copy de C string (owned por Kof) |
| **Buffer in** | `Arena.ofConfined()` copy-in | Copy-in para Kof buffer |
| **Buffer out (INOUT)** | Copy-in + copy-back no close da arena | **Descartado no native** (divergência, `MEM005`) |
| **Array writes** | Copy-in + copy-back | **Descartado no native** (divergência documentada) |
| **Owned pointer return** | Copiado antes do close da arena | Copiado para Kof String/Buffer |
| **Transferência de propriedade** | `Arena.ofConfined()` lifetime explícito | `kof_ffi_release` explícito |

---

## 8. Concorrência e Memória

| Regra | Descrição |
|---|---|
| **C-01** Spawn compartilha referências | `spawn { use(a) }` — `a` compartilhado entre main e worker |
| **C-02** `join_all` | Garante nenhuma task órfã; nenhuma cópia na fronteira |
| **C-03** Data races | Possível se dois workers aliam mesmo objeto mutável sem sync |
| **C-03** Guards | `MEM021` no `spawn` com estado mutável compartilhado |
| **C-04** Pilhas de worker | **Nunca raízes do GC** no Native — collect desabilitado após primeiro `spawn` |

---

## 9. Ciclo de Vida de Recurso

| Recurso | Tempo de vida | Close obrigatório | Diagnóstico |
|---|---|---|---|
| `Web` server | Processo ou `kof_web_close` explícito | Sim | `MEM014` |
| `DB` conexão | Processo ou `kof_db_close` explícito | Sim | `MEM014` |
| `File` handle | Por chamada (arquivo inteiro) | Auto por chamada | — |
| `FFI` buffer | Arena explícita / `kof_ffi_release` | Sim | `MEM005` |

> Nenhum finalizer, nenhum `Cleaner`, nenhum finalizador. Vazar `close()` vaza o
> recurso de SO subjacente pelo tempo de vida do processo.

---

## 10. Nulabilidade × Propriedade

| Regra | Descrição |
|---|---|
| **N-01** `T?` nullable | Desreferência sem estreitamento → `SEM049` |
| **N-02** `= null` literal | Proibido → `SEM048` |
| **N-03** Estreitamento | Apenas `x != null` estreita; `||` não estreita |
| **N-04** Nullable primitivo | JVM/JS: boxed; Native: unwrapped com default 0 (divergência) |

---

## 11. Matriz de Segurança (Gate da Fase 1)

A matriz abaixo mapeia cada classe de bug ao seu mecanismo de prevenção:

| Classe de Bug | Prevenção | Diagnóstico | Backend |
|---|---|---|---|
| Use-after-free | GC (conservativo no Native) | `MEM011` (runtime) | Todos |
| Double-free | GC / único `kof_free` | `MEM012` (runtime) | Todos |
| Dangling reference | GC (conservativo) | `MEM010` (runtime) | Todos |
| Escape de tempo de vida inválido | Análise de escape nas fronteiras | `MEM013` (compile-time) | Todos |
| Use-after-move | Nulagem explícita na transferência | `MEM002` (compile-time) | Todos |
| Data race por aliasing mutável | Disciplina do programador + `MEM020/021` | `MEM020/021` (compile + runtime) | Todos |
| Null deref | Nulabilidade + estreitamento | `SEM049` (compile-time) | Todos |
| Resource leak | Close explícito | `MEM014` (compile-time) | Todos |
| Confusão de propriedade FFI | Arena/release explícito | `MEM005` (compile + runtime) | JVM + Native |
| Resource leak (DB/Web) | Close explícito | `MEM014` (compile-time) | Todos |

> **Verificação Lei da Simplicidade**: Nenhuma anotação de lifetime no código do
> usuário. Todas as regras disparam nas fronteiras de superfície existentes
> (FFI, spawn, mutação stdlib, close de recurso, atribuição com null explícito).

---

## 12. Roteiro de Implementação (Pós-Spec)

| Fase | Trabalho | Status |
|---|---|---|
| **0** Investigação | `docs/spec/memory-safety-investigation.md` | ✅ FECHADA 25/09 |
| **1** Especificação | `docs/spec/memory-safety.md` (este doc) | ✅ FECHADA 25/09 (aceita, opção A) |
| **2** Infraestrutura do compilador | Representações internas de Ownership/Lifetime/Borrow/Escape (`dev.kof.compiler.memory`) | 🔄 EM DESENVOLVIMENTO 26/09 |
| **3** Primeiras garantias | Use-after-move, dangling, escape, aliasing mutável | ⏳ AGUARDANDO |
| **4** Closures & async | Semântica de captura, fronteiras async | ⏳ AGUARDANDO |
| **5** Native & FFI | Ponteiro/alloc/free, tabela de propriedade C ABI | ⏳ AGUARDANDO |
| **6** Cross-target | Matriz de paridade JVM/JS/WASM | ⏳ AGUARDANDO |

---

## Apêndice: Paridade Cross-Target

| Regra | JVM | Native | JS | Script |
|---|---|---|---|---|
| GC alcançabilidade | Preciso | Conservativo | GC do navegador | Interpretador |
| `val`/`var` mutabilidade | Compile-time | Compile-time | Compile-time | Compile-time |
| Nulabilidade estreitamento | Sim | Sim | Sim | Sim |
| FFI string return | Copy (arena) | Copy (Kof-owned) | Copy (JS string) | Copy |
| FFI Buffer INOUT | Copy-in + copy-back | **Descartado** (divergência) | Copy-back | Copy-back |
| Worker stack roots | Sim (virtual threads) | **Nunca** (desabilitado após spawn) | N/A (event loop) | Pilha do interpretador |

---

*Fim da Especificação Fase 1. Gate: revisão da mantenedora deste documento contra
`docs/spec/memory-safety-investigation.md` e a superfície REAL do Kof.*