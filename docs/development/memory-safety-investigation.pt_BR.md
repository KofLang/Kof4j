[English](memory-safety-investigation.md) | [Português](memory-safety-investigation.pt_BR.md)

# Memory safety — Fase 0: investigação do modelo de memória implícito do Kof (D-MEMORY-SAFETY)

> **Status: entregável da Fase 0** do `memory-safety-plan.md` (mantenedora
> 25/09, `DECISIONS.md` §`D-MEMORY-SAFETY`). Só pesquisa — zero edições no
> compilador. Toda afirmação abaixo tem evidência file:line (caminhos
> relativos a `kof-compiler/src/main/java/dev/kof/compiler/`). Investigado
> 25/09 no tip `9dc5922fc` (beta-0.5.0).

## 1. Estado atual — o que o compilador realmente faz hoje

### 1.1 Representação de variável (por estágio)
AST: `VarDeclStmt(type, name, init)` — `val`/`var` viajando como **strings**
no campo type (`parser/StatementParser.java:409-432`). Símbolo semântico:
`LocalVariableSymbol(name, type, index, isVal)` (`SymbolTable.java:147`,
construído em `StatementAnalyzer.java:100,148`). IR: `KofStoreLocal/
KofLoadLocal` + `IRLocalVariable`; alocação sequencial de slots (+1, +2 para
long/double) (`StatementLowererLocalBoxing.java:135-137`). Backends: JVM = o
índice do IR É o slot local (`JvmOpEmitter.java:70-75`; `Nullable(primitive)`
→ ASTORE, 1 slot); nativo x86-64 = **um slot de qword na pilha por local,
tudo** — long/double incluídos (`nat/NativeMethodEmitter.java:232-239`), com
DWARF mapeando os mesmos slots (`NativeMethodEmitter.java:158-174`); JS =
predecl `let/const` (`js/JsEmitter.java:107-110`).

### 1.2 Valor vs referência; cópia vs compartilhamento
Decidido pelo modelo de tipos, não por backend: `Type.PrimitiveType`
(bool/byte/short/int/long/float/double/char) copia (`Type.java:7-17`); todo
o resto apaga para referência (`CompilerTypeSupport.java:89-92`). `String` é
ClassType (referência; valores imutáveis, refs compartilhadas — concat
aloca um KofString novo, `runtime/RuntimeStringBase.java:116-156`). **Não
existe cópia profunda na atribuição em lugar nenhum**
(`ExpressionAssignmentLowerer.java:38,68,124,168,178` — emit-valor + store).
As únicas cópias mecânicas: copy-in/copy-back da FFI, cópia de crescimento
do buffer de lista nativo (`RuntimeList.java:44-66`), clone defensivo
`Buffer.bytes()` na JVM (`jvm/JvmBufferRuntime.java:25-26`).

### 1.3 Escape analysis: NENHUMA, em qualquer backend
O único otimizador de IR faz constant folding / dead stack-effect /
reachability / jump threading (`backend/Optimizer.java:66-110`). Todo
objeto/record/closure/box/Handle é `new` na JVM, `kof_alloc` no nativo
(`nat/NativeOpHelpers.java:36-53`).

### 1.4 Mutabilidade (val/var)
Aplicada na análise semântica, não no parsing: `SEM037` rejeita reatribuição
de `val` (`SemAssignmentAnalyzer.java:39-46`; for-update via
`StatementAnalyzer.java:239-242`). Família: `SEM038` escrita em componente
de record, `SEM054` `l[i]=v` em List, guarda de campo final
(`MemberCallTyper.java:487-495`). **`val` congela a LIGAÇÃO, não o objeto**:
`val l = listOf(1); l.add(2)` é legal hoje (não existe conceito de
imutabilidade profunda).

### 1.5 Captura de closure: por valor, com box só quando mutada
Classe sintética `Lambda<N>`; cada captura = campo privado final setado uma
vez pelo construtor (`CompilerLambdaClass.java:53-57,95-101,172-186`); o
sítio de criação empurra o **valor atual** da captura
(`CompilerCaptures.java:21-30`). Um pré-pass marca locais mutados dentro da
lambda e **re-boxeia** num `Box` de heap compartilhado
(`CompilerCaptureScanner.java:454-467,510-524`; `CapturedVarBox.java:15-56`;
gate `StatementLowererLocalBoxing.java:37-39`). Consequência: capturas sem
box são **snapshots**; mutação só é visível pelo caminho do box. O JS
transpila a mesma forma (`js/MethodCtx.java:56-98`) — a semântica de captura
é uniforme entre backends.

### 1.6 Modelo de GC (nativo x86-64): mark-sweep conservador
Raízes = (a) a pilha inteira da main thread, varredura de qword do `rsp` do
coletor até `kof_main_stack_bottom` (cap 64 MB, fallback 4 KB —
`runtime/RuntimeGc.java:28-57`); (b) estáticos `.data`+`.bss` de
`kof_heap_root_start` até `_end` (`RuntimeGc.java:59-75`); (c) os 15 GPRs,
porque `kof_gc_collect_now` faz spill em bloco (`RuntimeGc.java:262-311`,
fix §260). O mark aceita qualquer valor 8-alinhado em `[kof_heap_low,
kof_heap_high)` que acerte um bloco vivo da gc-list, depois varre cada qword
do payload conservadoramente (`RuntimeGc.java:86-131,143-219`). **Pilhas de
workers NUNCA são raízes** — o auto-collect é desabilitado permanentemente
após o primeiro `spawn` (`RuntimeMemory.java:127-138`, gate também em
`RuntimeConcurrency.java:202-212`).

### 1.7 Alocação/liberação nativa
Header de 32 B antes do payload: size / free-next / gc-next / flags (bit0
mark, bit1 free-list) (prólogo do alloc em `RuntimeMemory.java`;
`kof_init_object` `RuntimeMemory.java:12-21`). Alloc: first-fit com futex
(cap de varredura 1M nós) → na exaustão UM `kof_gc_collect_now` (a menos que
haja spawn) → crescimento por `mmap` (`RuntimeMemory.java:47-203`). O
`kof_free` existe mas é chamado só por 4-5 pontos internos do runtime (anel
de log, tabelas de observability, nós de channel) — **sem RAII, sem free
determinístico de objeto do usuário**; lixo pós-frame só é recuperado por um
ciclo de GC — e após o primeiro spawn, efetivamente **nunca** (leak por
design, documentado no runtime).

### 1.8 Ownership na FFI
JVM: `Arena.ofConfined()` confinada por chamada, fechada em `finally`;
arrays com copy-in; `Buffer(U8)` INOUT copy-in+copy-back; retorno `char*`
copiado para String antes da arena morrer
(`jvm/JvmFfiRuntime.java:126-191,320-363`). A regra escrita: "a vida do
buffer é da linguagem, nunca malloc/free do programador"
(`JvmFfiRuntime.java:135-138`). Nativo: mesma política declarada em
`nat/NativeFfiCall.java:33-36` (retorno de string copiado, buffer C nunca
free'd), mas **escritas em array são descartadas no nativo**
(`NativeFfiCall.java:422-470`) — divergência declarada da face copy-back do
Buffer na JVM.

### 1.9 Modelo de nullability
`Type.NullableType(inner)` (`Type.java:56-57,103-106`). JVM: T? de
referência = null plano; T? de primitivo **boxeado** (ASTORE,
#278/D-NULL-INTENT — `JvmLiteralEmitter.java:295-300`,
`StatementLowererLocalBoxing.java:26-36`). Nativo: null = sentinela 0
(`NativeOpHelpers.java:83`); T? de primitivo mantém a **representação sem
box** com default 0 (divergência fase-2 declarada,
`StatementLowererLocalBoxing.java:21-24`). JS: null/undefined coalescidos
nos helpers do runtime (`js/JsRuntimeCore.java:243-246,288`). Narrowing:
flow-sensitive só para `x != null` (redefine o símbolo num escopo filho,
`SemNarrowing.java:20-47`; `||` não estreita, `:39`). Códigos de mau uso:
SEM049 (deref sem narrowing), SEM048 (literal `= null`).

### 1.10 Aliasing: pervasivo e não rastreado
Toda atribuição/parâmetro/retorno não-primitivo copia só o ponteiro →
`var b = a` (List/Map/record/Buffer/array) faz alias em TODOS os backends;
mutação por um é visível pelo outro (identidade de ArrayList na JVM
`jvm/JvmOpCollections.java:95-146`; header de lista compartilhado no nativo
`RuntimeList.java:15-66`; `Buffer.data` compartilhado
`jvm/JvmBufferRuntime.java:14-23`). Não existe análise de alias/points-to
em lugar nenhum; a única lógica de identidade é o dispatcher do `==`
(content-equals ou fallback pointer-compare, `JvmOpEmitter.java:503-522`;
`NativeClassMeta.java:231-255`).

### 1.11 Ciclo de vida de recursos: só close explícito
Web: `kof_web_close` fecha o ServerSocket; apps vivem num registry estático
para sempre caso contrário (`jvm/JvmWebCoreRuntime.java:496-507`). DB: mapa
estático, fechado só por `kof_db_close` (`jvm/JvmConfigRuntime.java:215,260-296`;
nativo `RuntimeDb2.java:293`, `RuntimeDb4.java:34`). Arquivos: IO de arquivo
inteiro por chamada, handles dentro dos helpers
(`jvm/JvmRuntimeIo.java:26-130`). Sem finalizers/Cleaners em lugar nenhum —
esquecer `close()` vaza o recurso de baixo pela vida do processo (o GC
coleta só o wrapper).

### 1.12 Concorrência (spawn/await)
JVM: virtual thread + `CompletableFuture`; o `await` devolve a MESMA
referência que o worker produziu — sem cópia na fronteira
(`jvm/JvmRuntimeCore.java:76-170`). Nativo: handle de 56 B + trampolim;
`handle->result` é um qword cru — o ponteiro é compartilhado, não copiado
(`RuntimeConcurrency.java:190-279,297-329`); capturas cruzam por box ou
snapshot (§1.5); `kof_spawn_join_all` garante nenhum órfão.

## 2. Ledger de bugs conhecidos — a história adjacente a memória (varredura §)

Família fechada "GC conservador perdeu uma raiz": **§503** (raiz estática
`.quad` desalinhada invisível → sweep liberou buffers vivos; a lei do
`.balign 8`), **§260** (collect com temporários vivos em registradores →
spill em bloco dos GPRs), **G-6b/#113** (faixa de raízes estáticas + faixa
de varredura de pilha estreitas demais na origem). Família corrupção:
**§292** (overflow no header de 32 B do bloco seguinte — corrupção do
alocador sob pressão de free), **§252** (aliasing de slot de pilha vs
endereço de retorno do callee), **§117** (colisão de hash de TID → cancel
vazando entre workers), **§286** (race de cancel perdido → protocolo Dekker),
**§129** (throw no worker longjmp'ou para o frame da main thread → cadeias
de exceção por TID). Família leak: **§300** (vazamento de DOM no re-render
JS). Races: **§291/§256(b)/§364**. Nenhuma entrada ABERTA nomeia
use-after-free/double-free/dangling — a classe de linguagem é prevenida por
design (o GC nunca devolve memória liberada enquanto uma raiz conservadora
pode vê-la), mas §503/§260 provam que a segurança repousa em **disciplina
manual dos emissores**, não num invariante checado.

## 3. Pontos frágeis (onde o modelo implícito pode morder)

1. **Pilhas de workers nunca são raízes; o collect desliga para sempre após
   o primeiro spawn** → programas nativos concorrentes crescem
   monotonicamente (leak por design). Qualquer futuro "reabilitar collect
   com workers vivos" reabre a família §260/§291.
2. **Segurança de raiz estática = disciplina manual de `.balign 8`** — nada
   no emissor impõe; a classe do §503 é estrutural (qualquer arquivo novo de
   runtime pode se des-enraizar em silêncio).
3. **A varredura conservadora sobre-retém lixo** (palavras stale de frames
   que retornaram) — leak-safe, nunca corruption-safe contra mudanças
   futuras de precisão.
4. **Captura mutável só via box**: uma captura que o scanner não classificar
   (construto novo, sombreamento) vira snapshot stale em silêncio (a família
   SIGSEGV nativo face-B do §253).
5. **Aliasing não rastreado + copy-back da FFI num `Buffer.data`
   compartilhado**: duas variáveis em alias de um Buffer enquanto uma vai
   para o C = mutação in-place com zero consciência do compilador.
6. **`Nullable(primitive)` tem duas representações** (boxeado JVM/JS vs
   sem-box-com-default-0 nativo) — gerador permanente de bugs de paridade
   null-vs-0 (família §267/§279/§365/§368).
7. **`kof_free` quase sem uso e não integrado ao ciclo de vida** — blocos
   seguem na gc-list após o free; a corretude depende só do bit1 de flag.
8. **Sem finalizers de recursos** — recursos `db`/`web`/ffi vazam num close
   esquecido; nada amarra o tempo de vida do recurso ao escopo.

## 4. Respostas às perguntas centrais da frente (preliminares, para a Fase 1)

- **Use-after-free pode acontecer no Kof hoje?** No nível da linguagem, não
  (heap gerenciado por GC; blocos liberados ficam válidos-porém-sem-raiz
  até o reuso). Nas fronteiras: retornos da FFI copiam (seguro), mas a face
  nativa de escritas-em-array-descartadas significa dados que somem em
  silêncio, não dangling.
- **Referências pendentes (dangling) podem acontecer?** Não — não há
  referências do usuário a memória interior; a família de ponteiros
  interiores é só interna ao runtime (classe §252).
- **Double-free pode acontecer?** Só dentro do runtime (nenhum aberto).
- **Data races acidentais podem acontecer?** Sim, no nível do modelo: o
  `spawn` compartilha referências (JVM) / qwords (nativo); dois workers em
  alias de uma List é dessincronizado no nativo (sem locks no RuntimeList) —
  hoje a única guarda é disciplina + `join_all`.
- **Qual é o modelo de lifetime?** Implícito: gerenciado por GC, enraizado
  conservadoramente; escopos não têm significado de memória além do reuso de
  slots; closures estendem lifetime por possuírem capturas; recursos o
  ignoram por completo.
- **Escape analysis é necessária?** Não para segurança — para o custo do
  leak-por-design nativo e para futura alocação em pilha de closures/boxes
  que não escapam.

## 5. Compatibilidade e restrições para a spec (insumos da Fase 1)

Toda garantia deve ser **aditiva** (regra 2 do freeze): programas de hoje
seguem compilando e rodando. A null safety é intocável (brief §1); `val`/
`var`, `==`, ordem de avaliação, exceções-como-String são congelados
(rule 6). A Lei da Simplicidade (rule 11) proíbe cerimônia de anotação de
lifetime: o modelo deve entregar garantias pela **superfície existente**
(inferência de ownership, diagnósticos nos construtos de fronteira: chamadas
FFI, capturas de spawn, chamadas de stdlib sensíveis a aliasing, close de
recursos) ou por **fronteiras nomeadas opt-in**, nunca por sintaxe nova
obrigatória. Cross-target (D-FULL-PARITY-050): qualquer semântica decidida
aqui pousa com a linha do ledger de paridade preenchida (JVM/Native/JS +
golden).

## 6. Próximos passos

1. **Revisão deste documento pela mantenedora** (gate da Fase 0 no plano).
2. Fase 1: `docs/spec/memory-safety.md` — formalizar Ownership/Lifetime/
   Borrowing/Aliasing/Mutability/Move/Copy/Clone/Drop/Escape/Closure
   Capture/Concurrency/FFI/Unsafe Boundaries contra a superfície real
   documentada aqui, cada regra com classificação permitido/proibido/
   requer-sync/compile-time/runtime.
3. Fase de infraestrutura do compilador só depois da fila atual fechar
   (restrição do brief) — candidatos surgidos nesta investigação: um teste
   de invariante de alinhamento no emissor (mata a classe §503
   mecanicamente), uma guarda de exaustividade do capture-scanner, um
   estudo de lifetime de recursos para close com escopo à la `use`.
