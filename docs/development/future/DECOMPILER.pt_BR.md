[English](DECOMPILER.md) | [Português](DECOMPILER.pt_BR.md)

# DECOMPILER.md — Decompilador Kof (DESPRIORIZADO 15/09 → REABERTO 28/09, ainda em future/)

**Status:** **REABERTO pela mantenedora (`D-DEPRIORITIZED-REOPEN`, lote 28/09)** —
ainda só-plano aqui; a promoção a trabalho corrente é uma-por-vez por
`D-FUTURE-PROMOTION` (regra dos três estados). O código já no repo fica (kof-cli `Decompile.java` +
decoders, `DecompileTest` 67/67) — só a *fila* para aqui; o work-log completo
abaixo permanece como registro para uma sessão futura. Implementado:
`Decompile.java` + decoders de bytecode, Fases A–E com código; prova:
`DecompileTest` **67/67** verdes + `DecompilePostDominatorTest` 6/6
(re-medido 15/09 no tip `7b0bfbe0`; o 45/45 do cabeçalho era a linha de base
de 22/08). A lacuna restante
(medida 15/09: 1793 stubs no corpus de 706 classes; a maior — joins de
struct com computacao/invokes no teste, 519 TRAPs da familia `r14` —
exige o walker de post-dominador = sessao futura dedicada; os guards
locais "stop/join" e o fallback ingenuo de expressao foram tentados e
REJEITADOS, passos 1-3a do work-log abaixo)
**Data:** 22 de agosto de 2026

---

## 1. Objetivo

Recuperar código Kof idiomático a partir de artefatos compilados.

Primeiros alvos: `.class`, `.jar`, `.war`.

```text
JVM Class File
       ↓
Class File Parser
       ↓
Bytecode IR
       ↓
Control Flow Graph
       ↓
Type Recovery
       ↓
Data Flow Analysis
       ↓
Semantic Recovery
       ↓
Kof AST
       ↓
Kof Source
```

## 2. O que NÃO é

O decompiler **não é**:

- um "Java decompiler" que reconstrói o fonte Java original;
- uma reconstrução sintática do fonte perdido;
- um gerador de "Java com sintaxe Kof".

O objetivo é **Kof idiomático equivalente** — comportamento e estrutura,
não a forma original.

## 3. Prioridades

1. Equivalência semântica (o comportamento observável deve ser o mesmo);
2. Legibilidade (o resultado deve ser revisável por humanos);
3. Estrutura (classes, herança, interfaces, métodos, campos);
4. Tipos (primitivos, referências, arrays, generics recuperáveis);
5. Controle de fluxo (branches, loops, switches, exception regions);
6. Chamadas e dependências;
7. Exceptions;
8. Annotations e metadata quando presentes.

## 4. Informação Irrecuperável

Compilar é perder informação. O decompiler deve documentar o que não pode
recuperar:

- comentários;
- nomes locais (exceto quando debug info existir);
- estrutura sintática original;
- formatação;
- certas informações genéricas (erasure);
- intenção do programador;
- abstrações eliminadas na compilação.

> Decompilação é recuperação de **comportamento e estrutura** a partir das
> informações disponíveis — nunca do fonte original.

## 5. Confiança

Cada construção recuperada carrega um nível de confiança conceitual:

```text
Recovered exactly
Recovered with metadata
Inferred
Heuristic
Unknown
```

A plataforma nunca inventa informação silenciosamente para produzir código
que "parece válido".

## 6. Fases de Implementação

```text
Fase A  JVM Inspection          (.class/.jar + análise estrutural)
Fase B  JVM Bytecode IR         (Class File → Bytecode IR)
Fase C  Control Flow Recovery   (basic blocks, branches, loops, switches, exception regions)
Fase D  Type Recovery           (primitives, references, arrays, generics, inheritance)
Fase E  Kof Decompiler          (gerar Kof source)
```

> **Estado (08/09, `367d6c4`):** Fase D com genéricos ✅ — o parser lê o
> atributo `Signature` (JVMS 4.7.1/4.7.9.1) nos 3 níveis (classe, método,
> campo) e `Type.fromJvmSignature` recupera `List<String>`,
> `Map<String, Integer>`, arrays, wildcards e type-variables. Campos e
> métodos preferem a signature (EXACT) ao descriptor apagado por erasure.
>
> **Estado (08/09, este commit):** Fase C avançou — `do-while` (loop testado-
> embaixo) recuperado no `kof decompile`. Back-edge self/para-trás no bloco
> cond → `do { corpo } while (c)` (direção de CONTINUAÇÃO, sem inversão);
> corpo separado do teste → stub UNKNOWN honesto. Nunca mais `while` de corpo
> vazio com `return` dentro (código errado). Prova:
> `DecompileTest.bottomTestedLoopRecoversAsDoWhile` (17/17).
>
> **Estado (08/09, este commit):** Fase C — guard de **join compartilhado**
> (continue/&&/||/?:): re-entrar em bloco já emitido que não é o header do
> loop aberto → recusar (stub honesto). Nunca mais código errado compilável.
> `DecompileTest` 20/20 (inclui `diamondJoinShapesStayHonestStub` e o
> aninhado `recoversNestedWhileLoops` que continua recuperando).
>
> **Estado (09/09, este commit): robustez sobre código REAL (601 classes).**
> Rodar o decompiler sobre o próprio kof-compiler compilado expôs 3 bugs de
> parsing/length que só aparecem em bytecode de produção (javac, não os
> fixtures do teste). Correções (todas com teste):
> 1. **CP tags 16/17 invertidas** (`ClassFileParser`): JVMS 4.4 — `MethodType`
>    = tag 16 (u2), `Dynamic` = tag 17 (u2+u2). O parser tinha ao contrário →
>    qualquer classe com MethodType/CondY dessincronizava o constant pool
>    inteiro e CRASHAVA (`NumberFormatException "#378#513"`), matando o arquivo.
>    601→0 crash: antes só 1/601 decompilava SEM crash; agora 601/601.
> 2. **`length(0xba)=7` errado** (`BytecodeReader`): invokedynamic é 5 bytes
>    (opcode + u2 + 2 zero, JVMS 4.9.3). Com 7, o pc saltava a instrução
>    seguinte (`areturn`) → o teste do concat passava por ACIDENTE (fallback
>    "fim sem return"). Corrigido a captura de operandos (len==5&&0xba).
> 3. **`wide` drift** (`skipVariable`): `op == 0x84` era impossível (o op é
>    0xc4; 0x84 é o SUB-opcode) → `wide iinc` (6B) lido como 3, deslocando TODO
>    opcode seguinte. Agora lê o sub-opcode (0xc4,0x84 → 6 bytes; demais → 4).
> + **Marcador de truncamento**: instrução que não cabe no Code → Insn(-1)
>   → decoder default → null → stub honesto. A ferramenta NUNCA lança num
>   `.class` real (era o caminho que virava NumberFormatException/AIOOBE).
>
> Prova: `DecompileTest` 32/32 (+`invokedynamicIsFiveBytes`,
> `truncatedLastInstructionBecomesHonestStub`, `wideIincConsumesSixBytes`);
> medição sobre as 601 classes: 601/601 decompilam sem exceção, ~3306 métodos,
> 1812 stub (recuperação ~45%).

> **Fila Fase E medida (09/09):** `blockerSink` em `BytecodeDecoder` (custo
> zero quando null, uso offline) conta qual opcode derruba a recuperação
> sobre as 601 classes: `pop` 0x57 (347×), `instanceof` 0xc1 (165×),
> `checkcast` 0xc0 (137×), `new` 0xbb (126×, quase todo é `isJdkClass`
> recusando por R6), `astore_3`/arrays 0x4c (103×), `ifeq` 0x99 (102×).
> Ataque por ROI: pop/instanceof/checkcast são os 3 maiores. **Implementado e
> REVERTIDO no mesmo dia (lição R6):** emitidos como `x instanceof T`/`(x as
> T)`, a classe-alvo do bytecode é de DOMÍNIO (ex.: `DiagnosticCollector`) e
> não existe no `.kf` isolado → `kof check` falha "Undefined variable or
> type" — **recuperou código que não compila** (o teste de drift: decompile →
> check sobre as 601 classes; 12+ arquivos driftavam). A recuperação só é
> válida quando o nome do tipo já está em escopo (classes do MESMO arquivo
> recuperado) — requer o passes multi-classe do DECOMPILER (seção 7: resolver
> imports/usos), não um patch no decoder. `pop` sozinho também drifta: a
> heurística "tem parênteses = chamada" aceita `(x + (y))` aritmético.
> Fila correta da Fase E: primeiro multi-classe (tipo resolve), depois
> pop/instanceof/checkcast (bloqueados por aquele, não por eles mesmos).
>
> ⚠️ **Caveat do blockerSink (09/09):** ele conta cada desistência do caminho
> linear **de expressão** — mas um método só vira stub quando expressão E
> statements desistem; stores/branches (0x3a/0x4c/0x99…) aparecem no ranking
> mesmo sendo tratados pelo `emitLinear`. O ranking serve p/ ACHAR candidatos,
> não p/ contar stubs; a fila real = (a) nomes de domínio não-resolvidos
> (multi-classe §7) e (b) shapes estruturais recusados (joins, Fase C).
>
> **DRIFT 69→5 (09/09, este commit):** o contador de drift (decompile→check
> nos arquivos 100% recuperados) media PRÉ-EXISTENTES que os opcodes novos
> expunham. A causa nº1 era semântica e única: `ldc` emitia a string do CP
> CRUA (`\b`, newline real, `"` → LEX002/LEX004/'\' inesperado). Escapado com
> o escape canônico do concat (`BytecodeConcat.escape`), o drift caiu 69→5.
> As 5 restantes: 4× cross-file (classe referida noutro arquivo — exatamente
> o passes multi-classe do §7) + 1× wildcard `? extends` (gap próprio).

> **Estado (09/09, este commit): §7 degrau 2 — índice same-package.**
> `kof decompile <dir>` agora é em 2 passes (parse uma vez, reuso — sem
> re-parse): passe 1 monta `internalName → pacote` de TODA a árvore; passe 2
> decompila com o índice no `BytecodeFrame` (por-método, sem estático global
> — modo 1-arquivo tem índice null = byte-idêntico ao anterior). Com o índice,
> `instanceof`/`checkcast` de classe de DOMÍNIO do MESMO pacote recuperam
> (`arg0 instanceof B`, `(arg0 as B)`); fora do índice (outro pacote, `Outer$Inner`,
> CP malformada) segue stub honesto. Cross-package com import = degrau 3.
>
> Prova: par controlado B/C (javac → tree → par compila, zero drift) +
> `DecompileTest.decompileTreeResolvesSamePackageInstanceofAndCast` e
> `decompileTreeStillStubsOutOfTreeDomainTypes` (recusa preservada) —
> 38/38. Corpus 613 classes: 1674→1638 stubs; invariante textual verificada
> nos 613 `.kf` (36 instanceof/as de domínio emitidos em posição de código,
> 100% com `.kf` irmão no mesmo dir — zero drift por construção).
> Decisão de design: índice via `BytecodeFrame` (contexto existente), não
> estático global (vazaria entre arquivos/testes no mesmo JVM) nem parâmetro
> novo nas ~10 assinaturas dos decoders.

> **Estado (09/09, este commit): §7 degrau 3 — imports cross-package.**
> `TreeScope` por arquivo (índice + pacote atual + imports usados; frames
> compartilham — sem global, modo 1-arquivo intacto com bytes idênticos,
> provado: stubs single-file 1674 = baseline). `instanceof`/`checkcast`
> cross-package resolvem com `import` emitido; `new` em expression-body
> registra uso; `extends`/`implements` idem. Regra de sanidade: simples
> globalmente único (duplicado em 2+ pacotes → stub, mesmo com import
> possível — conservador; probe provou que `import` DESEMPATA no frontend,
> relaxamento futuro documentado).
>
> Prova: par p/B+q/C (extends+instanceof+new) compila junto (zero drift) +
> `decompileTreeEmitsImportsForCrossPackageDomainRefs` e
> `decompileTreeRefusesAmbiguousSimpleNames` — 40/40 DecompileTest. Corpus:
> tree 1636 stubs, 7 arquivos com import; invariante textual 36/36 nomes
> com `.kf` irmão. Tree-check 613 arquivos: 4 erros, todos wildcard
> `? extends` pré-existente (gap próprio) — ZERO SEM011/PKG (e idêntico sem
> os imports: o frontend resolve simples não-ambíguo module-wide; imports
> são explícitos/idiomáticos + desambiguadores + robustez).
> Decisão de design: índice no `BytecodeFrame` (contexto existente), coleta
> durante o decode (import não-usado por corpo-stub é inofensivo — probe:
> só import INEXISTENTE é erro PKG006); `new` em statement-body segue stub
> (statements não tratam 0xbb — gap Fase E próprio); assinaturas
> (param/return/field cross-package) = degrau 4.

> **Estado (09/09, este commit): §7 degrau 4 — tipos de assinatura.**
> Field/ctor-param/param/return cross-package registram import via
> `recordSignatureUses` (walker ClassType+args/Array/Nullable sobre
> `m.returnType`/`m.parameterTypes` e `fieldTypeTree` — signature preferida,
> descriptor como fallback; emissão de nomes INALTERADA). O hook fica no topo
> do loop de métodos (o `continue` do `<init>` pulava ctor-params — pego na
> revisão). Prova: par p/B+q/C (field+ctor+param+return+new) compila junto +
> `decompileTreeEmitsImportsForSignatureTypes` (41/41 DecompileTest). Corpus:
> arquivos-com-import 7→31; tree-check 614 = 4 erros wildcard pré-existentes,
> zero SEM011. Com degraus 1–4, a classe "nome não resolve" de drift morreu
> na árvore (resta só wildcard `? extends`, gap próprio, e slots — fora do
> decompiler).

> **Estado (09/09, este commit): Fase E — `new` em statement-body.**
> O `emitLinear` não tratava 0xbb/0x59/0xb7 (só o path linear): qualquer
> corpo multi-statement com `new B(...)` virava stub — 152 arquivos com stub
> contêm `new` de domínio. Mirror exato do path linear (marcador `⟦new⟧`,
> dup-só-pós-marker, `<init>` com argc+2 na pilha); JDK recusa (R6);
> cross-package registra import (frame já presente). Prova: par N/M
> (`var n = new N(x)` + field + return, same e cross-package) compila +
> `recoversNewInStatementBody` (42/42 DecompileTest). Corpus: single
> 1674→1665 e tree 1636→1627 stubs; drift single 13→13 (zero novo — baseline
> com o fix em stash, mesmo harness com package espelhado; os 13 são wildcard
> + refs cross-file que o path linear já emitia). Refactor gate ≤500:
> `statementOp` em `BytecodeKofTypes` (Statements 496, KofTypes 182).
> Suíte 1193+25+5+122 — ZERO falhas. Próximo da fila: `anewarray` 0xbd
> (69×) e joins estruturais (Fase C).

> **Estado (09/09, este commit): Fase E — arrays (`anewarray`/acessos).**
> `anewarray` (0xbd) + `xaload`/`xastore`/`arraylength` SÓ no statements-path
> (`statementOp`; linear recusa e cai no statements — mesma saída, menos
> código, sem pressão no gate). Elemento ESTRITO (`String`/`Object`/domínio;
> `Integer[]` recusa: `new Int[n]` é `int[]`, semântica distinta). Idioms:
> `new T[n]`, `a[i]`, `a[i] = v` (stmt), `a.length` (probes JVM/script).
> Prova: par A (`new String[n]` + store/load/length) compila +
> `recoversArrayCreateAndAccess` (43/43 DecompileTest). Corpus: single
> 1674→1658 stubs; drift 13→13 idêntico (zero novo). Suíte 1193+25+5+123 —
> ZERO falhas. Incidentes da unidade: (1) python sem checar ordem
> start<end DUPLICOU região do BytecodeDecoder (698 linhas) — revertido via
> `git checkout` (só tinha código novo da unidade) e o path linear foi
> ABANDONADO por desnecessário; (2) bug 71 registrado no caminho (Kof
> `new Int[2][3]` → VerifyError — lane compiler, não tocado);
> `multianewarray` recusado (sem forma válida p/ recuperar).


> **Estado (13/09, dono = 192.168.100.17): Fase E — Java record → `record` Kof.**
> Re-mediação da fila com atribuição POR MÉTODO (o caveat de 09/09 confirmado
> na prática: o blockerSink global contava o path de expressão que o de
> statements recupera depois). 216 das 688 classes do corpus (31%) são record
> e os 3 corpos sintéticos (`invokedynamic ObjectMethods` — o corpo NÃO existe
> no bytecode) eram a maior fonte única de stubs. Medido na ÁRVORE LIMPA
> (`mvn -am compile`, 688 classes, `Med2`): baseline sem recovery = **1856**
> stubs → records puros + type-params EXATOS = **1660** (−196) → `implements`
> resolvido = **1475** (−185). Total **−381 = 127 records × 3** (216 records;
> **89** ainda em skeleton). Cada etapa é zero-drift por construção (desvio →
> null → esqueleto de hoje).
>
> - **Estágio 1** (`BytecodeRecords.pureRecordComponents`): atributo `Record`
>   (parser já expõe em `ir.attributes` — zero mudança no parser
>   compartilhado) + super = `java/lang/Record` + shape EXATO por bytecode
>   (ctor `aload_0;invokespecial;N×(aload_0;load;putfield f_i);return`,
>   equals/hashCode/toString = `invokedynamic` skeleton, accessor =
>   `aload_0;getfield f_i;ret` com descriptor casando campo), sem método extra,
>   sem nome reservado (`val` → PARSE015).
> - **Type-params** (JVMS 4.7.9.1, forma `Ident:Lclass;`): `record Gp<T>`
>   emite `<T>` EXATO; bound genérico/interface-bound → RECUSA (skeleton).
> - **Estágio 2** (`pureRecord(ir, scope)`): probe R7 — o frontend Kof quer
>   `record Nome implements I(...)` (implements ANTES dos componentes; ordem
>   Java dá PARSE007). Interface = classe TOPO do MESMO pacote (resolve sem
>   import) OU via `TreeScope.resolve` (cross-package emite `import`);
>   `Outer$Inner`/JDK/fora-da-árvore/ambíguo/scope-null (1-arquivo) → skeleton.
>
> Prova: `DecompileTest` 55/55 (round-trips puro/genérico/mesmo-pacote/
> cross-package+import compilam; método-extra/reservado/interface-fora-da-árvore
> → skeleton honesto). Drift-check da árvore inteira (`DriftCheck`:
> `decompileTree` 688 → `compileSources` batch): **estágio-2 = 4 erros =
> baseline = 4 erros**, todos wildcard `? extends` PRÉ-EXISTENTE em
> `ClassDeclarationNode` (não-record) — **zero drift novo**, 62 arquivos
> mudaram de esqueleto p/ record. Restam de records (na época): os **89** em
> skeleton — categorizados no estágio-3 abaixo (interface fora-da-árvore
> p/ 1-arquivo, `Outer$Inner`, class-signature com bound genérico, static
> members). Fila Fase E
> re-medida (Med2 pós-unidade): top dos 1475 restantes = store+branch
> estrutural (`astore` 0x4c/0x4d/0x4e/0x3a, joins de loop = Fase C);
> instanceof/checkcast SÓ tratam quando a árvore resolve o nome
> (statements-path sem escopo = ainda caem — o furo do 09/09).
>
> **Fase C — o gargalo REAL medido (13/09, `StoreCat`, dono = 192.168.100.17):**
> categorizar o drop por MÉTODO (não last-opcode): o `struct()` de
> `BytecodeStatements` (linha 206) RECUSA re-entrância de join estrutural →
> stub silencioso (sem opcode registrado). Isso é **2452 métodos** (a causa
> `ffffffff`/outra do StoreCat) — de longe o MAIOR gargalo do corpus, e é
> 100% deliberado ("recovery de join estruturado é trabalho futuro"). Os
> counts de instanceof/STORE/branch (202+267+~240) são SINTOMAS do mesmo
> join: o bloco pós-if não re-entra. **Plano de ataque (sub-caso estreito
> primeiro):** `if (cond) { then }` SEM else com join no fim (ex.
> `CompilerTypeSupport.fieldOk`, `KofUi.themeColor`) — o `struct` hoje trata
> if-then-else (linha 271) mas não if-then puro com fall-through ao pós-bloco.
> Tratar SÓ re-entrância que é join de if NÃO-loop (header ainda recusa),
> emitir `if (cond) { then }` e continuar em `b.succ.get(0)`. **Prova
> OBRIGATÓRIA antes do commit (R6/Q0):** golden EXECUTANDO — o harness já
> existe e É o modelo: `DecompileTest` linha 747-791 ("FORTE: não basta
> compilar — executa os 3 caminhos") faz `java -cp <out> S` e compara stdout;
> replicar p/ o sub-caso de join (if-then sem else: braço-tomado e
> braço-pulado devem dar o MESMO output no .kf quanto no .class original,
> em JVM; cross-target JS/Native/Script = gap diagnosticado se divergir).
> Um join recuperado errado é compilável mas semanticamente ERRADO = R6
> (pior bug possível); NUNCA relaxar o `struct` sem o golden de execução.
>
> **Diagnóstico EXATO do sub-caso (13/09, dono = 192.168.100.17 — pronto p/
> a próxima sessão executar):** fixture mínimo reproduzido
> (`/tmp/opencode/join/J.java`, `g(int x)`, oracle JVM medido: g(6)=107,
> g(1)=101): `r = 100; if (x > 5) { r = r + x }` SEM else + join que
> continua. O decompiler de HOJE stuba `g` e já trata `h` (then termina em
> `return`) como if-else-expression via path linear. **Traço do CFG (por que
> stuba):** líderes {0,8,12}; B0[0,8) `if_icmple 12` → succ=[12,8]; B1[8,12)
> corpo-then → succ único=[12]; B2[12,…) join. Em `struct`(B0): linha 271
> abre `if`, chama `struct(B1)`; B1 tem succ único 12 → linha 280 **anda**
> para B2 e marca 12 emitida (o corpo do join cai DENTRO do `if`); volta e
> linha 275 `struct(B2)` re-entra em 12 → linha 206 RECUSA (join não-header)
> → null → stub. **Correção NÃO é 3 linhas:** exige um *boundary* de parada
> — o then de if-sem-else deve PARAR no join (não andar para ele) e o join
> ser emitido UMA vez como sequela do `if`. Caminho: parâmetro `Set<Integer>
> stop` (ou `joinStop`) em `struct`/`emitLinear`-walker: quando o próximo
> bloco é o `exitStart` do if-encadeamento atual e não tem outro pred,
> emite `if (cond) { <then> }` (SEM else) e continua `struct(exitStart)` uma
> vez. Detectar if-sem-else: `then.succ==[exitStart]` && `exitStart` preds
> ⊆ {b, then}. **NÃO tocar:** guard com return/goto no then (path linear já
> trata), skip com pred extra (break/continue/&&/|| — manter recusa linha
> 206), header de loop (intacto). Arquivo: só `BytecodeStatements.struct`
> (+ walk de emitLinear do then) + `DecompileTest`. **Golden OBRIGATÓRIO no
> MESMO commit (não só compilar):** padrão `DecompileTest:747-791`
> (`java -cp <out> J` stdout == oracle JVM nos DOIS caminhos — g(6)=107,
> g(1)=101) + drift-check da árvore inteira (4=4) + suíte 4-módulos; sem o
> golden de execução passando, NÃO commitar (R6: join errado = compilável
> mas semanticamente errado = pior bug). Orçamento: thread `stop` por todas
> as recursões de struct — sessão inteira dedicada, não encaixa no fim desta.
>
> **DEGRAU 1 EXECUTADO — if-then puro sem else (13/09, dono = 192.168.100.17):**
> o caminho do diagnóstico funcionou, em sessão dedicada: `stops` (Set) em
> `struct`, borda SÓ para `pureIfThen` (join não-loop, preds exatos {if,then},
> then com succ único == join), emitindo `if (cond) { then }` SEM else e a
> sequela UMA vez. `recoversIfThenJoinAndRunsIt` executa o .kf decompilado
> (oracle g(6)=107/g(1)=101; TDD: RED no stub, GREEN com o fix). **Dois traps
> medidos ANTES do commit:**
> 1. **Aninhamento vira CÓDIGO ERRADO COMPILÁVEL** se a borda também descer
>    no braço do else (variante com `withStop(exitStart)` no then+else): em
>    `if(a){if(b){..} seq1}else{seq2} seq3`, a `seq3` foi sugada p/ dentro do
>    else. Revertido; o aninhamento fica em STUB honesto e
>    `nestedIfWithoutElseStaysHonestStub` trava a recusa (R6: recusar > errar).
> 2. **`emitLinear` retorna PARCIAL em branch** (case 0x99-0xa7/0xaa-0xb1/0xbf
>    → return): o fallback de prólogo (bloco fundido init+teste, onde
>    `blockCondition` recusa) ganhou guarda de fluxo — prefixo com desvio →
>    stub. Foi o que destravou `g` de verdade (javac funde `int r=100;` no
>    bloco do teste; sem o fallback o join puro não pega NADA no corpus).
> Medido (A/B stash, mesma árvore 690 classes): stubs 1390 → **1387** (−3; o
> if-sem-else PURO fundido-simples é raro — `fieldOk`/`themeColor` têm shapes
> não-compatíveis). DriftCheck = baseline 4; suíte 4-módulos 1691/0/5-skip.
> **Restante da Fase C:** joins de aninhamento/loop/else exigem pós-dominador
> real (walker com emissão única de join + sequela por fora do `if-else` —
> trap 1 mostra que borda ingênua corrompe; não é stop simples).
>
> **DEGRAU 2a EXECUTADO — if-else LINEAR com sequela + bug latente do
> `blockCondition` destravado (13/09, dono = 192.168.100.17):** ROI medido
> (`Orient`): 1138 candidatos `then.succ==else.succ==[P]`, `preds(P) =
> {then,else}` exatos, P não-loop — a borda de stop entra nos DOIS braços
> (cópia compartilhada; o dono do join o emite na sequela) e o trap 1 é
> impossível *por construção* aqui (preds(P) não contém o if → P não é alvo
> de branch). `pureIfElse` no mesmo sítio do `pureIfThen`. **Mas descer até
> aqui revelou um BUG LATENTE grave** (exatamente o serviço que Q4 pede):
> `blockCondition` coletava "até 2 loads" do bloco de teste SEM exigir
> aridade — em `if (i % 2 == 0) continue` o corpo `[iload i, iconst 2, irem,
> ifne]` virava `if (i == 0)` (o `irem` ignorado!) = CÓDIGO ERRADO
> COMPILÁVEL. Antes do degrau 2a o método stubava antes de chegar ali e o
> bug estava abafado; eu provei por EXECUÇÃO (decompilado `0 0 1 3 6 10
> 15 21 28` vs oracle `0 0 1 1 4 4 9 9 16` — o guard histórico
> `diamondJoinShapesStayHonestStub` pegou, e ele é LEI). Fix na raiz:
> aridade exata (todos os insns do bloco de teste devem ser loads puros →
> senão `null` = stub honesto). A variante `i == 3` (sem cálculo) agora
> recupera como `if (v == 3) { } else { corpo }` — golden de execução
> `recoversContinueAsEmptyThenJoinAndRunsIt` (contFor 0..6 = 0 0 1 3 3 7
> 12, increment no join preservado nos dois caminhos). Stubs na árvore:
> 1387 → 1409 — o AUMENTO é QUALIDADE: as "recuperações" que seriam código
> errado viraram stub honesto (Q5/R6); contagem de stub não é métonimo de
> conformidade quando o alternante era código errado. DriftCheck =
> baseline 4; DecompileTest 62/62.
>
> **DEGRAU 2b EXECUTADO — narrowing `ifnull`/`ifnonnull` (0xc6/0xc7) (13/09,
> dono = 192.168.100.17):** ROI medido (`NullTest`): **308** testes nulos sobre
> load PURO (226 ifnull + 82 ifnonnull) stubavam só porque `invCond`
> (`BytecodeCp`) mapeava apenas 0x99-0xa4 — `blockCondition` devolvia null →
> stub. É o **idiom canônico** da linguagem (§Null safety: `if (x != null)`).
> Fix mínimo: +2 linhas no `invCond` (0xc6→`!= null`, 0xc7→`== null`;
> aridade 1 cai natural no `blockCondition` de aridade-exata do degrau 2a).
> `len()` vira if-expression ternária (o path linear já existia, só faltava o
> mapeamento); `nul()` vira if-sem-else (degrau 1). A/B mesma árvore 692:
> 1412→**1402** (−10; os outros ~298 têm JOIN/cálculo no corpo = gargalo
> ALHEIO ao narrowing). DriftCheck baseline (o `recoverExpression` —
> ternários/elif — usa o MESMO `invCond` e não derivou); golden
> `recoversNullNarrowAndRunsIt` (oracle 3/0/5/9, 4 caminhos). **Anti-fachada
> (Q7):** adicionei E REMOVI 0xc6/0xc7 do `contCond` (do-while) — o dispatch
> do do-while só roteia 0x99-0xa4, o mapeamento seria código morto
> (`do{}while(x==null)` seguiria stubando honesto). Registrado p/ não refazer.
>
> **DEGRAU 3a TENTADO E REJEITADO — fallback de expressão na `blockCondition`
> (13/09, dono = 192.168.100.17, hipótese negativa registrada):** ROI medido
> (`Arity1`/`Why0` na máquina REAL `linearReturn`): dos ~1990 candidatos
> if-sem-else com prefixo multi-insn, 816 "avaliam a 1 valor" SEM guardas de
> estrutura. Implementei a máquina exposta (`machineRun` + flag `stopped`,
> byte-idêntica ao `linearReturn` no round-trip: DriftCheck = baseline 4) e o
> fallback com guardas (sem ret/throw no prefixo, pilha final exata =
> aridade, tipos I/L). **`diamondJoinShapesStayHonestStub` quebrou (63/1):**
> o fixture tem loop cujo TESTE tem cálculo fundido no header pelo javac —
> recuperar a cond sem recuperar o incremento do `for` emite `while` que
> PERDE o incremento = código errado compilável, o trap 3 exato. A
> aridade-exata do degrau 2a é guard de **estrutura**, não só da expressão:
> teste com cálculo em header de loop SINALIZA `for` dessugarado, cujo corpo
> exige o pós-dominador. Revertido o código inteiro (árvore = `edc4728c`).
> Fica travado: QUALQUER fallback de cond precisa de gate `!isLoopHeader` por
> construção, e mesmo assim só depois do walker com pós-dominador (os
> 404+160+228 "default@0xc0/0xc1/0x3a" do `Why0` = stores e checkcast no
> caminho — shape que nunca é só expressão).
>
> **Confirmação do gate (2ª tentativa, mesma sessão):** re-apliquei o fallback
> COM `if (isLoopHeader(entry)) return null` — e o `diamondJoin...` QUEBROU DE
> NOVO. A cond `i % 2 == 0` do `for+continue` não está no header do loop: mora
> num bloco ANINHADO cujo then/else joinam no bloco-de-incremento (que carrega
> o back-edge). Recupera-la reconstrói o `for` como `while` com o `i++` sugado
> p/ dentro do else = código errado (exatamente o que a lei diamond trava).
> **Conclusão firme: nenhuma guarda local basta — o único caminho para teste
> com cálculo é o walker com pós-dominador (degrau 3 real).** A extração da
> máquina (`machineRun`) está provada byte-idêntica (DriftCheck baseline 4) e
> é pré-requisito do degrau 3. **AUDITORIA 14/09 (lane docs/development, vs
> CÓDIGO, não memória): a frase acima ficou OBSOLETA — a extração ENTROU no
> tree em `158c174b` (13/09 19:52, "refactor(decompiler): extrai maquina de
> expressao do linearReturn"), DEPOIS desta sessão ter escrito o revert.
> Hoje `BytecodeDecoder.machineRun` está VIVA e É o corpo de `linearReturn`
> (BytecodeDecoder.java:73/97). Pré-requisito do degrau 3: presente e
> provado (o DriftCheck=baseline-4 byte-idêntico vale — linearReturn passa
> por ela em toda chamada).
>
> **Estágio 3 (13/09, dono = 192.168.100.17): interna do MESMO pacote.**
> Categorização reflexiva dos 89 rejeitados (harness `RecCat`): **31** eram
> só `implements Outer$Inner` do mesmo pacote (cluster `JsIr$*` com 43
> internos, `SymbolTable$*`); o resto = 52 shape-fail (método extra real),
> 4 static-field, 2 reserved. Probes 13/09 no frontend: SEM042 proíbe tipo
> ANINHADO, mas nome top com `$` compila — `record X$Y implements X$Z(...)`
> ✓, `record X(...) { extra() }` ✓, `static` em record ✓, compact
> constructor ✗ (PARSE018). E o decompiler já emite cada interno como classe
> TOP de arquivo irmão (688/688 nomes simples únicos, medido). `pureRecord`
> agora aceita interna quando o pacote casa E o interno está no índice
> (`TreeScope.inIndex` — árvore parcial sem irmão = skeleton honesto);
> interna CROSS-pacote → skeleton (import `p.Outer$I` sem probe). Corpus:
> 1475→**1382** stubs (−93 = 31 × 3 sintéticos; **158** de 216 records
> recuperados). Drift-check: current 4 erros = baseline 4 (zero drift — só
> `ClassDeclarationNode.kf x4` wildcard pré-existente). Prova: +2 testes
> (57/57 `DecompileTest`) — mesmo-pacote interna round-trip compila
> (marker interface; interface abstrata exige método extra = outro gap),
> cross-package interna → skeleton honesto. Restam 58 records em skeleton
> (52 com método extra — `record X(...) { corpo }` seria a chave, mas
> `RecExtra2` mede só 3 com TODOS os corpos recuperáveis hoje; 4 static
> field; 2 reserved) — custo/benefício baixo; a fila real da Fase E agora é
> o caminho não-record (1382 stubs): joins estruturais + `astore`/
> `istore` multi-stmt (Fase C) com o statements-path já escopado.
>
> **Experimente NEGATIVO (13/09, dono = 192.168.100.17 — documentado p/ não
> refazerem):** aceitar `$` no regex do `TreeScope.resolve` (habilitar
> `instanceof`/`as` de interna em expressão). Probes ✓
> (`x instanceof Box$Expr`/`as Box$Expr` compilam), MAS **−9 stubs só**
> (1382→1373; os 238 "resolvable-domain" contados por `Why193` não eram o
> drop real — o que derruba ex. `BuiltinTypes.isString` é `astore_1`+`ifeq`
> de corpo multi-stmt = Fase C) e **risco latente real**: `arrayElementType`
> passaria a aceitar interna → `new Type$ClassType[n]` = PARSE041 no
> frontend (o drift-check do corpus não pegou: nenhum arquivo do corpus
> dispara anewarray-de-interna; outro corpus dispara). ROI negativo + perigo
> cross-corpus → REVERTIDO (working tree limpo, HEAD = estágio-3).

>
> **Estado (14/09, este commit, dono = 192.168.100.17 — lane docs/development
> EXCLUSIVA por diretriz da mantenedora): pré-requisito do PASSO 3 da Fase C
> entregue.** `PostDominator.java` — passada pura de pós-dominador imediato
> (dual bit-set de Cooper–Harvey–Kennedy: `pdom(b) = {b} ∪ ⋂ pdom(succ)`,
> terminal → `{b, EXIT}`, interseção monótona converge sem depender de ordem
> de iteração; determinístico — princípio D-ENGINEERING: a formulação padrão
> de compiladores, não reinventada). É o pré-requisito travado pelas duas
> rejeições do STEP-3a (13/09): um teste com computação só pode ser
> recuperado pelo walker que consome pós-dominadores, nunca por guarda local.
> Prova: `DecompilePostDominatorTest` 5/5 com oráculos caminho-até-EXIT
> calculados à mão (cadeia linear, join if-then-else, back-edge de while,
> if aninhado, fork sem join). **Nenhuma saída de recovery mudada ainda** —
> 63 `DecompileTest` intocados (re-rodados frescos 115.6s, verde; o número
> 87.43s era relatório surefire STALE, pego e corrigido — honestidade acima
> de falso verde), contagem de stubs do corpus inalterada por construção (a
> passada ainda não está ligada). **PRÓXIMO neste doc (unidade 2):** consumir
> `immediatePostDom` em `BytecodeStatements.struct()` p/ recuperar as formas
> de teste-com-computação que o step 3a rejeitou (o `for+continue` cujo cond
> vive num bloco aninhado que faz join no incremento) — cada recuperação
> precisa manter `diamondJoinShapesStayHonestStub` verde (a lei do diamante
> é vinculante).

>
> **Estado (14/09 ~16:25, este commit, dono = 192.168.100.17): ROI do walker
> do passo 3 MEDIDO (harness `/tmp/opencode/roi/dev/kof/cli/Roi.java`,
> descartável no package `dev.kof.cli` como Orient/Why0/StoreCat — prática da
> lane: medir antes de escrever).** Corpus REAL hoje
> (`kof-compiler/target/classes`): 699 classes (0 falhas de parse), 3899
> métodos, **2628 stubados** (67%). Destes, **1098** têm ao menos uma forma
> "bloco succ==2 com `blockCondition==null` e computação no bloco-teste"
> (tam do bloco-teste 1..20 insns antes do cond — init/store/irem fundidos de
> `for`/`while`) — teto do que o walker com `immediatePostDom` destrava (muitos
> ainda vão resistir à lei do diamante; o rendimento real vem fatia a fatia).
> ROI ≫ 30 → **decisão: construir o walker**. *(→
> SUPERSEDIDA pela RE-MEDIÇÃO abaixo, 14/09 ~18:20: o proxy supercontou, o
> walker como escopado não tem alvo líquido novo — leia antes de construir.)* Escopo da unidade 2 (travado
> pela medição): consumir `immediatePostDom` no ramo `cond == null` do `struct()`
> — recuperar teste-com-computação SOMENTE quando o join P = idom(then) =
> idom(senão-caminho), P NÃO é loop-header e as back-edges dos braços não
> cruzam P (a construção que torna trap 1 impossível — critério do passo 2a
> estendido a teste não-puro); cada fatia mantém
> `diamondJoinShapesStayHonestStub` VERDE (lei vinculante) e adiciona golden
> de execução (oracle JVM).

> **PROVA DE CORRETUDE adicionada (unidade 2a): `pathOracle` brute-force
> sobre a DEFINIÇÃO (X pdom b ⟺ todo caminho simples b→terminal passa
> por X) contra a passada rápida bit-set em CFGs REAIS do corpus (300
> classes de `kof-compiler/target/classes`, blocos ≤40) — 6/6 verde,
> 4.28s, divergência ZERO bloco a bloco. O walker pode agora consumir
> `immediatePostDom` com a passada confiada.

> **RE-MEDIDO (14/09 ~18:20, dono = 192.168.100.17): o "1098" acima era um
> PROXY que SUPERCONTOU — o walker NÃO tem alvo líquido novo (harness
> `/tmp/opencode/w2b/dev/kof/cli/Roi2.java` + `Roi3.java`, descartável
> package-private como o Roi.java; a classificação é pela CAUSA REAL do stub,
> não pela forma do blockCondition).** Os 1098 contaram cada
> "bloco succ==2 com `blockCondition==null`" sem checar se o método ainda é
> descompilado pelo caminho prologue adicionado na unidade 2a (`5c944709`:
> um `int x=…; if (x%3==0){}else{}` não-loop fundido JÁ tem a FORMA
> recuperada hoje — medido: `computed`/`cmp` emitem `if (v1 == 0) { … } else
> { … }`, mas a saída emitida NÃO COMPILA: o `var` do local sobe na sua
> PRIMEIRA atribuição, que fica DENTRO do ramo then, e é lido depois do join →
> `SEM000 Undefined variable` (medido 19:50 via Runner2 no HEAD recompilado —
> defeito PRE-EXISTENTE da 2a, catalogado §238, NÃO é alvo do walker). Logo a
> 2a "recupera" só quando o local é inicializado ANTES do if (o teste `E.java`
> que passa tem `int r=1` pré-if). Separando
> os 2642 stubs pela causa que FAZ o `recoverStatements` devolver null, entre
> os que TÊM um teste computado 2-succ:
>
> | causa do stub (medido, corpus 699 classes / 3899 métodos / 2642 stubs) | qtde | de quem é a lane |
> |---|---|---|
> | o teste tem um `invoke`/`getfield`/`new` na computação (`.equals`, `.size`, `String.join`…) | **646** | descritor de interop (§234/§224/§225) — **lane compiler**, NÃO é problema de CFG |
> | o teste computado está no HEADER de um LOOP (diamante com `continue`/back-edge) | **453** | **travado pela lei vinculante do diamante** + Kof não tem `continue` → regra 6 (contrato), NÃO é um edit |
> | não-loop, prefixo puro load/const/arith mas um opcode que o `loadValue` não trata (sipush/lcmp/ldc_w) | 8 (+2 store) | gap de cobertura de opcode, não o walker |
>
> **Veredito: o walker da unidade 2b como escopado (consumir `immediatePostDom`
> p/ recuperar um teste não-puro) é DESCARTADO — a medição mostra que o caso
> fundido não-loop já é coberto pela 2a, e os stubs de teste computado restantes
> são ou interop (646, §234) ou a colisão lei-do-diamante/`continue` (453, regra
> 6).** O `PostDominator` + `pathOracle` (unidades 1/2a) ficam como fundação
> confiada (verde, 6/6, zero regressão); simplesmente NÃO são ligados porque não
> sobra nada no escopo deles que a lei permita e que a 2a já não faça. Re-scope
> honesto, NÃO um drop silencioso: as 453 faces loop-diamante + 646 interop ficam
> registradas aqui como o trabalho real (adiado/outra-lane) p/ o próximo agente
> não pagar de novo a arqueologia da ROI.
>
> **PRÓXIMO PASSO deste doc:** a superfície recuperável do decompiler está no
> teto honesto para formas estruturadas. O trabalho aberto do decompiler é agora
> (a) a recuperação de `continue`/`break` em `for` SE a mantenedora levantar a
> lei do diamante (regra 6 — precisa de um `continue` na linguagem, decisão de
> contrato, NÃO esta lane) e (b) nits de cobertura de opcode (sipush/lcmp no
> `loadValue`) que são micro-fix da lane compiler, não um walker estrutural.
> Nenhum dos dois é trabalho de modo autônomo em `docs/development/`. **ATUALIZA
> ÇAO (14/09 ~20:55, mesma sessao):** a caça ao 5º-red que produziu esta
> re-medicao TAMBEM expôs um defeito real da lane — §236 (a dobra ambigua
> Bool-vs-Int da comparacao) ACHADA & CORRIGIDA com teste novo de recompile
> (`comparisonReturnRespectsBoolVsIntReturnType`, DecompileTest 64/64 verde),
> e §238 (o join `pureIfElse` da 2a emitindo `var` dentro do ramo → saida
> nao-compilavel) catalogado com repro medido — §238 virou a proxima unidade
> autonoma (2c). **ATUALIZACAO 2 (14/09 ~21:35): §238 ✅ CORRIGIDO (unidade
> 2c)** — classe NOVA `StructWalker.hoistEscapingLocals` içar `var`
> default-init (0/0L/0.0 por opcode da store; fstore/astore → recusar p/
> stub honesto) antes do `if` nos caminhos `pureIfElse`; 2 testes novos que
> FALHAM no codigo antigo (prova Q0: 2/2 red com o revert) + executam com
> oracle JVM medido (`10/21/12`); DecompileTest 66/66, PostDom 6/6, kof-cli
> COMPLETO 251/251 BUILD SUCCESS, lei do diamante VERDE, check_500 OK
> (537→547 TOLERADA, StructWalker 84 linhas). O que resta na lane
> estrutural: a face lei-do-diamante + `continue` (453, regra 6 — decisao da
> mantenedora) e os testes interop (646, lane compiler §234) — o doc volta
> a um ponto de parada genuino. **ATUALIZACAO 3 (14/09 ~22:05, mesma
> unidade): face sipush FECHADA** — `loadValue` espelha o `machineRun`
> (0x11 to short), seguro so DEPOIS do hoist §238 (antes, um fix
> so-de-sipush virava stub em saida nao-compilavel — a re-medida 18:20
> marcou §238 como pre-requisito); `if (a == 30000)` agora recupera
> COMPILAVEL + executavel (`sipushConstantInTestIsRecoveredAndRuns`,
> oracle JVM `1|2|2`); DecompileTest 67/67. Fechando divergencia de
> passada, nao forma nova. A lane estrutural esta agora exausta.

## 7. Relação com o Compilador

O decompiler alimenta o pipeline existente:

```text
Legacy Semantic IR
        ↓
    Kof AST
        ↓
Kof Compiler (frontend existente)
        ↓
    Kof IR
        ↓
 JVM / Native
```

Não duplica o frontend do Kof. O ponto de entrada é o **Kof AST**.