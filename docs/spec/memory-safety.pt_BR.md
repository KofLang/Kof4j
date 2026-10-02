[English](memory-safety.md) | [Português](memory-safety.pt_BR.md)

# Especificação de Segurança de Memória — Propriedade, Tempo de Vida, Empréstimo, Aliasing (D-MEMORY-SAFETY)

> **Status: Fase 2 FECHADA 26/09** (fatias 1–4, `dev.kof.compiler.memory`, teste do modelo 8/8,
> BT success `9bcddfe90`) — **Fase 3 DESTRAVADA 26/09**: o pedido de decisão O-02×N-02
> foi resolvido por `D-COMPLETE-FIRST` (re-expressar O-02 sem literal null — passe de
> análise + emissão nos 4 alvos como um pacote completo). Fase 1 FECHADA/aceita 25/09 (opção A); Fase 2
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
| **FFI** | JVM/JS: a arena confined fecha no fim da chamada; Native: sem superfície de liberação hoje (recusa `FFI001`, rota #651) | `Arena.ofConfined()` na JVM; ponte do host por chamada no JS |

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
| **B-06** Captura de closure | Por valor (snapshot imutável) ou boxed (mutado) | Capturar local mutável sem box quando escapa | `MEM023` | Compile-time (inconstruível — ver nota 28/09) |

> **Nota**: Sem empréstimo exclusivo não há garantia de ausência de data race
> no nível de tipo. Data races são prevenidos por disciplina + `join_all` +
> primitivas de sincronização explícitas (canais, futures). O compilador emite
> `MEM020`/`MEM021` nas fronteiras sensíveis a aliasing (stdlib/FFI/spawn).

> **B-04 escalar (28/09, #660/`D-MEM021-SCALAR`):** `MEM021` cobre também a
> captura ESCALAR — o pai reatribuindo/incrementando um local capturado após o
> `spawn`, sem `await`/`join_all` entre, é ERROR de compilação (a escrita do
> worker força o box de representação, então pai e worker compartilham o slot;
> corrida silenciosa medida `202`/`101` antes do fix). Captura só-leitura segue
> silenciosa (por valor, sem box). `MemorySafetyE2ETest` 46/46.

> **Verificação B-06 (28/09, #658/#659):** o lowering caixa toda captura mutada por construcao (`mutatedCapturedNames` → `CapturedVarBox`), entao a forma proibida (captura mutada sem caixa escapando) e inconstruivel — `MEM023` NAO tem face de compilacao hoje (precedente O-03/`D-MEMORY-CLEAR`: a garantia e provada por teste, nenhum diagnostico inventado). Travado pelas baterias de 4 alvos: `LambdaE2ETest` (faces de closure) + `SpawnE2ETest` (faces async/retorno, INCLUINDO captura MUTADA (`spawn { n = n + 1; return n * 2 }` → `44`) e visibilidade filho→pai via join (`println(await h); println(n)` → `44/22`), adicionadas 28/09 depois que o verifier independente mediu que captura read-only nunca exercita a caixa: o JVM rebaixa `LambdaTask0.<init>(I)`, por valor; `44` sozinho passaria ate num snapshot por valor).

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

## 7. Fronteiras de Propriedade FFI (tabela de ownership da fase 5, `D-MEMORY-SAFETY`; EN×PT em par)

Tudo que cruza uma fronteira de linguagem é CÓPIA DE VALOR com lifetime confined — Kof nunca transfere a propriedade do seu heap para um runtime externo, e memória externa nunca é retida além da chamada a menos que o lado Kof a copie. As células são MEDIDAS contra a árvore e travadas pela matriz de comportamento (`docs/backend-parity.md` linha C-FFI, 165), não lembradas. A tabela de duas colunas que isto substitui carregava duas alegações defasadas — "Buffer INOUT descartado no native" (a face publicada do FFI era recusa `FFI001` na linha da declaração; a fatia A1 do #651 pousou a superfície `Buffer(U8)` de namespace/print no x86-64 e a fatia A2 pousou a face `extern` `B` no x86-64 — cross segue `FFI001`) e "Native: `kof_ffi_release` explícito" (o símbolo existe só como conceito do modelo, `OwnerKind.java:29-30`, sem superfície na árvore — medido 28/09) — corrigidas aqui pela prática de verdade documental do #665 e pela issue #670.

| Fronteira | JVM | Native | JS host runner | Python (`kof.interop`) |
|---|---|---|---|---|
| **Escalar (Int/Long/Float/Double/Bool)** | por valor via FFM (`FfiE2ETest`) | por valor, `call sym@PLT` direto, link-by-use — sem dlopen, sem ownership (#431, §369) | por valor via ponte do host, byte a byte com o JVM (`KofJsFfiBridge`, `FfiE2ETest` JVM↔JS) | valor JSON tipado novo por chamada (`interop-py-host.kf`, `json.decode` pelo tipo da face) |
| **String** | cópia na fronteira, nas duas direções | cópia na fronteira; retorno = boundary copy (`kof_ffi_from_cstr`, payload off 24, NULL→NULL) | cópia | valor string JSON |
| **Buffer(U8) in/INOUT** | copy-in + copy-back no close da `Arena.ofConfined()` confined (`BufferFfiE2ETest` 5/5) | **o `extern` `B` binda no x86-64 desde a fatia A2 do #651** — ponteiro do payload `obj+24`, a escrita do C é o copy-back (`BufferFfiE2ETest#bufferInoutCopyInCopyBackNativeParity`); a superfície de namespace/print binda pela fatia A1 (`BufferE2ETest#allocBytesAndPrintlnNativeParity`); cross riscv64/aarch64 **também binda desde a fatia B do #651** (`BufferFfiE2ETest#bufferParamCrossBindsAndMatchesJvm`, JVM==riscv64==aarch64) | paridade copy-in + copy-back (`BufferFfiE2ETest#bufferInoutCopyInCopyBackJsParity`, `JsRuntimeBuffer`) | sem buffer compartilhado — só valores |
| **record / array escalar** | por valor: `record` arg + return, `T[]`→ptr copy-in, out-buffer (`FfiStructE2ETest` 10/10, `FfiArrayE2ETest` 5/5, 3.8b) | x86-64: struct param/return por valor incl. sret (> 16 B), `T[]` copy-in SEM write-back (`FfiNativeArrayE2ETest`); cross: `T[]` copy-in (30/09, face 1, `kof_ffi_pack_array`), `String[]`→`char**` copy-in (face 2, `kof_ffi_pack_str_array`) retorno de struct por memória (face 3, sret; ponteiro de resultado ciente da arch `a0` riscv64 / `x8` aarch64) e o **param** struct by-value >16 B (face 3, ponteiro BYREF `a0`/`x0`) — todos JVM==x86-64==riscv64==aarch64 (`FfiNativeArrayE2ETest`/`FfiNativeStringArrayE2ETest`/`FfiNativeStructReturnE2ETest`/`FfiCrossStructParamE2ETest`); callbacks no cross → `FFI001` na declaração | não-escalar → `FFI002` (ponte struct JS pendente, `CompilerFfiBinding`) | n/a (só face JSON tipada) |
| **Callback (upcall)** | ponteiro de função C real, síncrono/não-escapante, contrato confined-arena (R3.4, `JvmFfiCallbackE2ETest`) | `FFI001` — remainder honesto do §369 | a ponte do host constrói o mesmo upcall, byte a byte (`upcallStub`, R3.4-C3) | n/a |
| **Ponteiro próprio retornado por C / handle opaco** | copiado antes do close da arena; nunca retido | `FFI001` na linha da declaração (handles opacos fora de escopo, remainder §61) | copiado (ponte do host) | n/a |
| **Liberação explícita** | nenhuma — `Arena.ofConfined()` confined fecha no fim da chamada | **nenhuma superfície de liberação existe**: `kof_ffi_release` é conceito do modelo (`OwnerKind.java:29-30`) e proposta D6-5 (`docs/ffi-abi-structs.md`), nunca implementado — medido 28/09, declarado aqui em vez de alegado | nenhuma — a ponte do host aloca por chamada (R3.4) | nenhuma — cada chamada é uma sessão-filha nova; globals do Python não sobrevivem entre chamadas |

- **Kof↔Python NÃO é memória compartilhada.** O motor é Kof puro sobre `process.spawn` + `json.decode` tipado (protocolo de 3 linhas, header do `interop-py-host.kf`): cada valor cruza como payload JSON novo, logo não há o que possuir ou liberar; falhas são nomeadas `INTEROP004/006/007/008`, nunca silêncio; sessão viva com handle nomeado é território da regra 6, indeciso.
- **Kof↔Rust NÃO tem superfície hoje — é AUSÊNCIA, não gap.** O `kof-c-compiler` é um compilador de subconjunto p/ fixtures (header do `KofCCompiler`: "native-only C subset compiler... no JVM target"), não um exportador; Kof não emite ABI de biblioteca consumível e nenhum doc alega o contrário. Uma coluna Rust entra nesta tabela quando uma necessidade real pousar — inventar suporte aqui seria contrato alucinado.
- **POUSADO 29/09 (unidade 2 da fase 5, decisões `#667`/`#668` A).** A face de **compilação** do `MEM020` (B-03) agora tem forma: um `extern` cujo parâmetro é `Buffer(U8)` INOUT escreve esse buffer, então duas escritas sem sincronização (worker×pai ou worker×worker) viram `MEM020` ERROR em compile-time sobre o `OwnershipPass` existente (`FfiCaptureSpawnE2ETest#concurrentFfiBufferWriteParentAndSpawnIsMem020` + `#concurrentFfiBufferWriteTwoSpawnsIsMem020`; a escrita única com `await` segue limpa — `#singleAwaitedFfiBufferWriteIsNotMem020`). `Script × extern` não morre mais cru no runtime com `KofRuntime.kof_ffi/4`: é recusado na linha da declaração reusando `FFI001` (`ScriptTargetTest#externIsRefusedOnScriptFfi001`, integração `FfiCaptureSpawnE2ETest#ffiRefusedOnScriptEvenInsideSpawn`). Como a recusa precede qualquer lowering, a face `MEM020` é **inalcançável no Script por construção** (sem `extern` ⇒ sem escrita FFI) — o pin correto no Script é `FFI001`, nunca `MEM020`.
- **POUSADO 30/09 (unidade 4 da fase 5, `D-MEM030-BORROW-RUNTIME`).** A metade de **runtime** do `MEM020` (B-03) agora tem forma: o objeto de runtime `Buffer(U8)` carrega um flag de **borrow gravável** exclusivo; uma escrita INOUT de `extern` o adquire e o libera após a downcall, então um **segundo borrow gravável concorrente** levanta `MEM020` em runtime enquanto escritor único/aguardado segue limpo. A primitiva está nas **seis** faces (JVM, JS, Native x86-64, riscv64, aarch64, Script). Pela decisão de acompanhamento da mantenedora, o caso **negativo** é provado por execução onde há preempção (JVM virtual threads; pthreads native x86-64/riscv64/aarch64) e por **inalcançabilidade estrutural** documentada em JS (seu `spawn` é `async`/`await` — cooperativo, single-threaded) e Script (sem superfície `Buffer`/`extern`: `FFI001`). Prova: `BufferRuntimeBorrowE2ETest` (controles positivos em todas as faces; o negativo x86-64 levanta exatamente um `MEM020`; a corrida negativa cross está temporariamente bloqueada pelo `known-bugs §545`, um SIGSEGV pré-existente do `spawn × extern` native/cross independente desta frente).

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
| `FFI` buffer | Arena confined (JVM/JS); Native ainda não tem face de buffer (`FFI001`, §7) | Sim | `MEM005` |

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
| Data race por aliasing mutável | Disciplina do programador + `MEM020/021` | `MEM021` compile (D-MEM021-SCALAR) + `MEM020` compile (#668) **e runtime** (#668 + `D-MEM030-BORROW-RUNTIME`, 30/09) | Todos alcançáveis (Script: `extern`/`Buffer` recusado `FFI001`; JS: `spawn` cooperativo ⇒ negativo N/A estrutural) |
| Null deref | Nulabilidade + estreitamento | `SEM049` (compile-time) | Todos |
| Resource leak | Close explícito | `MEM014` (compile-time; WARNING) | Todos (o Script o expõe via `Result.warnings()`, `D-SCRIPT-WARN-SURFACE`) |
| Confusão de propriedade FFI | Arena confinada por chamada (não existe superfície de release — `kof_ffi_release` é conceito do modelo, spec §7, medido 28/09) | `MEM005` — regra do modelo, sem superfície de emissão hoje | JVM + Native |
| Resource leak (DB/Web) | Close explícito | `MEM014` (compile-time; WARNING) | Todos (o Script o expõe via `Result.warnings()`, `D-SCRIPT-WARN-SURFACE`) |

> **Diagnósticos de classe WARNING no Script:** `MEM014`/`MEM022` disparam no frontend compartilhado em todos os alvos. No Script, o `interpret()` descartava WARNINGs (só ERRORS escapavam) — medido 29/09, pedido de decisão #678; **RESOLVIDO 29/09 (`D-SCRIPT-WARN-SURFACE`, opção A): o interpretador agora os expõe via `KofInterpreter.Result.warnings()` e o CLI/`KofScript` os imprimem em stderr como o caminho de compilação.**

> **Verificação Lei da Simplicidade**: Nenhuma anotação de lifetime no código do
> usuário. Todas as regras disparam nas fronteiras de superfície existentes
> (FFI, spawn, mutação stdlib, close de recurso, atribuição com null explícito).

---

## 12. Roteiro de Implementação (Pós-Spec)

| Fase | Trabalho | Status |
|---|---|---|
| **0** Investigação | `docs/spec/memory-safety-investigation.md` | ✅ FECHADA 25/09 |
| **1** Especificação | `docs/spec/memory-safety.md` (este doc) | ✅ FECHADA 25/09 (aceita, opção A) |
| **2** Infraestrutura do compilador | Representações internas de Ownership/Lifetime/Borrow/Escape (`dev.kof.compiler.memory`) | ✅ FECHADA 26/09 (fatias 1–4; BT success `9bcddfe90`; emissão = Fase 3, destravada) |
| **3** Primeiras garantias | Use-after-move, dangling, escape, aliasing mutável | ✅ FECHADA — fatias 3.1→4 pousadas (MEM001/002/013/014/021/022); ver `memory-safety-plan.md` |
| **4** Closures & async | Semântica de captura, fronteiras async | ✅ FECHADA 28/09 — 4.1 captura (#658), 4.2 async/futures (#659), 4.3 callbacks (#662); iteradores/geradores = ausência medida |
| **5** Native & FFI | Ponteiro/alloc/free, tabela de propriedade C ABI | 🔓 EM PROGRESSO — tabela de ownership pousada (#670); unidade 1 pinada (#666); unidade 2 pousada (#667/#668); unidade 3 pinada (`Buffer(U8)` INOUT × spawn/await); `#651` B cross pendente |
| **6** Cross-target | mesma semântica de memory-safety nos **quatro backends que existem** — JVM, Native (x86-64 + cross riscv64/aarch64), JS, Script. WASM não é gap desta frente: só reentra no contrato quando um backend WASM real pousar (`D-MEM-PHASE6-4BACKENDS`, 30/09) | ⏳ AGUARDANDO |

> **Escopo da fase 6 (corrigido 30/09, `D-MEM-PHASE6-4BACKENDS`):** o roadmap original nomeava "JVM / JS / WASM", mas a árvore **não tem backend WASM** (`docs/backend-parity.md` = JVM × Native × KofJS; ausência medida 28/09, #671). A paridade da fase 6 define-se, portanto, sobre os backends que existem (os quatro acima). WASM sai do contrato até um backend real pousar — **não** é gap aceito desta frente.

---

## Apêndice: Paridade Cross-Target

| Regra | JVM | Native | JS | Script |
|---|---|---|---|---|
| GC alcançabilidade | Preciso | Conservativo | GC do navegador | Interpretador |
| `val`/`var` mutabilidade | Compile-time | Compile-time | Compile-time | Compile-time |
| Nulabilidade estreitamento | Sim | Sim | Sim | Sim |
| FFI string return | Copy (arena) | Copy (Kof-owned) | Copy (JS string) | Copy |
| FFI Buffer INOUT | Copy-in + copy-back (`JvmFfiRuntime`) | **Binda no x86-64 e no riscv64/aarch64** (`#651` A2/B: o emissor passa o ponteiro do payload `obj+24`; a escrita do C é o copy-back — cross via `NativeFfiCallRiscv`) | Copy-back (bridge do host) | **Recusado na linha da declaração `FFI001`** (`#667` — Script não tem runtime FFI) |
| Worker stack roots | Sim (virtual threads) | **Nunca** (desabilitado após spawn) | N/A (event loop) | Pilha do interpretador |

---

*Fim da Especificação Fase 1. Gate: revisão da mantenedora deste documento contra
`docs/spec/memory-safety-investigation.md` e a superfície REAL do Kof.*