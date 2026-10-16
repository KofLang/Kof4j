[English](assembly-optimization-plan.md) | [Português](assembly-optimization-plan.pt_BR.md)

# Otimização de Assembly Cross-Target

**Estado:** EM DESENVOLVIMENTO — promovido de `future/` para `docs/development/` 13/10 (`D-FUTURE-PROMOTION` + `D-ASSEMBLY-OPT-GO`; lane docs/lab `192.168.15.130:9093`). **Fatia atual: Fase E — 23 fatias de ISA pousadas nos três alvos native (x86-64, AArch64, RISC-V 64); o modelo de locais mapeado a registradores está VIVO no codegen native x86-64 (`D-CODEGEN-REWRITE`, flag ON por padrão).** As listas de bullets do §7 (otimizações específicas de ISA) para x86-64/AArch64/RISC-V estão agora essencialmente ESGOTADAS — todo candidato restante foi MEDIDO de baixo valor (1-2 sítios por amostra) ou exige liveness entre regiões que o peephole de texto não fornece com segurança, então nenhuma fatia §7 adicional foi forçada. As faces maiores restantes são a **Fase D (target lowering — formalizar a declaração IR-op → instrução de máquina por backend; o emissor x86 já cobre exaustivamente as 37 ops de IR com um default que lança, então é uma formalização, não uma lacuna de correção)** e o trabalho de **robustez do decompilador JS multi-arquivo** que destrava o §3 O2 (`D-JS-ROBUST`, caminho de fix registrado). A Fase A (camada de otimização estruturada sobre o otimizador de IR LINEAR existente) e a Fase B (propagação de constantes/cópias + eliminação de stores mortos, ligadas no `Optimizer` de produção com redução de instruções medida) estão completas; as fatias 1–6 da Fase C adicionam liveness sobre o CFG, o grafo de interferência, um alocador guloso determinístico, custo de spill ponderado por loop, a classificação caller/callee-saved por ABI e o plano de materialização de spill; a §18 adiciona o contador de métricas do IR; a determinismo §20 é verificado por `OptimizerDeterminismTest` (o otimizador de produção é determinístico — mesma entrada, mesma saída — e idempotente — ponto fixo — e `x * 4` alcança o shift da §12 em produção). Primeira fatia da Fase F (suíte de benchmark) = `OptimizerBenchmarkE2ETest` — um harness de benchmark cross-target cobrindo dez categorias da §17/Fase F (aritmética, loops, branches, chamadas, constantes, bitwise, arrays, structs/records, pressão de registradores, memória) na JVM E native x86-64; a FFI é exercida pelos testes E2E de FFI dedicados e a FFI native permanece o gap diagnosticado `FFI001`, então ainda não é uma categoria de benchmark cross-target: cada fixture compila para a JVM e os três alvos native (x86-64, aarch64, riscv64 via qemu), roda em todo alvo disponível (afirmando a saída do programa — a metade de correção, paridade cross-target) e reporta os deltas de instrução/load/store do otimizador de produção (a metade medida, ex. `constants` 80→72 instr, stores 11→9) e o tamanho do ELF native (métrica §18); o tempo é impresso como OBSERVAÇÃO, nunca afirmado (§17: assembly menor não é necessariamente mais rápido). A §17 = `OptimizerMetrics.java` — o harness de comparação antes/depois que a §17 exige ("cada otimização relevante deve ter … comparação antes/depois"): roda o `Optimizer` de produção sobre o stream de ops de um método e reporta o `IrMetrics` antes/depois (deltas de instrução/load/store), para que o efeito de uma otimização seja MEDIDO, não cosmético; prova `OptimizerMetricsTest` 3/3. **Fase C fatia 7 (WIRING) = `LivenessDeadStores.java`** — a materialização que CONSOME o `LivenessAnalysis`: um store é morto quando seu slot não está vivo depois dele (o valor guardado nunca é lido), removido junto com um produtor puro; LIGADO no `Optimizer` de produção (após as passes da Fase B). É o critério "redução de loads/stores artificiais" da §21 — a pass de liveness remove um store nunca lido que a regra de sobrescrita da Fase B não consegue; validada numa suíte rápida ampla (268/269, incl. `CoreRegressionE2ETest` 104/104, todas as suítes de imagem + switch) com só a minha própria asserção de contagem da Fase B ajustada para cima (o pipeline agora dobra mais). **§709 (achado+corrigido 13/10 pela lane compiler/JVM/native `192.168.15.30:9092`, `cce555c00`):** a pass pousou um bug — removia a inicialização de um local lido SÓ num `catch`/`finally`, porque a aresta de exceção do `LivenessAnalysis` dependia de `blockOf(KofTryStart.handlerLabel)`, que é `-1` (o handler é o op `KofCatchStart`, não um `KofLabel`); o CFG agora mapeia o handler (`KofCatchStart` é líder de bloco, `KofTryStart` fronteira) + a liveness modela `tryHandlers`. . **§712 (achado+corrigido 14/10 pela mesma lane, `2d571f8f4`):** a MESMA pass dead-storeou um `finally` que lê um local que o try/catch atribui — um SEGUNDO caso de aresta de exceção (depois do catch do §709). Duas instâncias é um padrão, então a pass foi ENDURECIDA: `LivenessDeadStores` agora não remove NADA de um método que tenha qualquer `KofTryStart`/`KofCatchStart` (conservador por construção; métodos com muita exceção perdem a otimização, correção não é negociável). Prova `LivenessDeadStoresTest` 7/7 (incl. o novo `aMethodWithATryRemovesNothing`). A lição durável permanece: meu teste unitário não pegou o §709 porque montava um IR IDEALIZADO (`new KofLabel(handler)`) que o lowering real nunca emite — a lição durável: construir testes de análise a partir do IR REAL do lowering, ou provar com um E2E que compila e roda. O restante da Fase C é aplicar a COLORAÇÃO de registradores (não só a remoção de stores mortos) no codegen de um backend — o ponto onde a Fase C encontra a Fase D e exige uma decisão de escopo de ABI por alvo (R7). §12 = `StrengthReduction.java` — uma multiplicação por uma potência de dois vira um shift à esquerda, SÓ onde é exatamente equivalente (int/long complemento de dois; nunca ponto flutuante, nunca divisão — `x / 2` arredonda para zero enquanto `x >> 1` arredonda para −∞); registrado em `PassPipeline.standard()` E LIGADO no `Optimizer` de produção (após o folding, para que um literal dobrado ainda o alcance); o valor do shift é SEMPRE um literal int mesmo para um valor long (JVM `lshl`/`ishl` tomam (long,int)/(int,int) — um literal long de shift é VerifyError, §167); prova `StrengthReductionTest` 7/7 + validado na suíte JVM/JS ampla (CoreRegression 104/104) e numa execução native x86 (`10L * 8L` → `80`). O `Optimizer.java` linear mantém seu próprio constant folding + eliminação de efeitos-mortos de pilha + alcançabilidade + jump-to-next sobre uma lista de ops ACHATADA (`IRBasicBlock` é um único bloco plano hoje); a fundação da Fase A (grafo de fluxo de controle / partição em blocos básicos / arestas de sucessão), as passes da Fase B e as análises da Fase C ficam ao lado, zero mudança de comportamento para os backends existentes.
**Como terminar:** Fase A (CFG + registradores virtuais + análise de uso/efeito + pipeline de passes), depois Fase B (otimizações básicas) → Fase C (alocação de registradores) → Fase D (lowering por alvo) → Fase E (ISA) → Fase F (benchmarks); cada critério de fase conforme §21. A ABI por-alvo é uma decisão de escopo antes da Fase D (R7 escopo honesto).
**Local:** `docs/development/assembly-optimization-plan.pt_BR.md`
**Natureza:** arquitetura, contratos, estratégia de implementação e critérios de promoção
**Escopo:** backend Native do Kof em múltiplas ISAs
**Alvos:** x86_64, aarch64, riscv64 e futuros backends Native
**Princípio:** otimizar antes do assembly específico da ISA sempre que possível; especializar no backend somente quando a arquitetura exigir.

last: promovido 13/10 (`D-FUTURE-PROMOTION`; o único plano de `future/` cujo gate `D-ASSEMBLY-OPT-GO` já está decidido, sem sintaxe nova, aditivo compilador-interno)
doing: Fase A COMPLETA 13/10 + Fase B COMPLETA 13/10 + Fase C fatia 1 (liveness) POUSADA 13/10 — a Fase A pousou todos os seis itens da §21: `ControlFlowGraph.java` (blocos + arestas de sucessão), `EffectAnalysis.java` (efeito + def/uso de locais), `VirtualRegisters.java` (numeração (slot,tipo)), `PassPipeline.java` + `IrPass.java` (ordenado, fixpoint, determinístico). As fatias 1–3 da Fase B (`ConstantPropagation`, `CopyPropagation`, `DeadCodeElimination`) estão LIGADAS no `Optimizer.passes()` de produção (depois do constant folding, antes da eliminação de efeitos-mortos de pilha); três invariantes compilador-internos foram medidos (RED-first): (1) `KofStatementIf` é fronteira de controle (dobrar a condição orfanava o marcador de reconstrução estrutural do JS — `KofJsE2ETest` 38/38 depois); (2) um read-back imediato `store(slot); load(slot)` fica verbatim (os backends fazem pattern-match nele — `#switch`/`#recv`; desligar a guarda quebrou `SwitchLongDoubleSupportE2ETest` 3/5); (3) a propagação de constantes emite uma instância DISTINTA de literal por sítio reescrito (compartilhar uma instância fazia a eliminação de stores mortos por identidade apagar uma leitura sobrevivente junto com um produtor morto — `Raster.kf` → crash de frame ASM). Redução medida: um store morto derruba 2 ops (`ds1` 68→66), dois derrubam 6 (`ds2` 74→68); prova `OptimizerPhaseBReductionTest` 3/3. Fase C fatia 1 = `LivenessAnalysis.java` — dataflow regressivo (`liveOut(b)=∪liveIn(s)`, `liveIn(b)=use∪(liveOut\def)`) sobre o CFG da Fase A, com aresta de exceção conservadora (try→handler); `liveIn`/`liveOut` por bloco + `liveAfter` por op; determinístico; ADITIVO (leitura pura, sem mudança de comportamento); prova `LivenessAnalysisTest` 7/7. Fase C fatia 2 = `InterferenceGraph.java` — dois slots interferem quando um está vivo depois do outro ser definido (conservador: nunca omite uma aresta; arestas extras só custam otimalidade, nunca correção); ordem de nós determinística (§20); expõe também dicas de coalescência da relação de cópia (`coalesceHints`); todo slot definido é um nó mesmo sem arestas; ADITIVO; prova `InterferenceGraphTest` 7/7. Fase C fatia 3 = `RegisterAllocator.java` — coloração gulosa determinística do grafo (grau decrescente, empate por índice crescente, §20): cada nó pega a menor cor não usada pelos vizinhos; um nó sem cor livre dentro do orçamento de registradores faz SPILL (cor -1). Nunca dá a mesma cor a dois slots interferentes (sempre correto, no pior caso subótimo); as cores são números abstratos de registrador (o mapeamento ISA é Fase D/E). ADITIVO (nada o consome ainda); prova `RegisterAllocatorTest` 6/6. Fase C fatia 4 = `SpillCost.java` + seleção de spill por custo — o custo de spill de um slot é a contagem de usos ponderada pela profundidade de aninhamento de loop (um uso dentro de um loop é pago a cada iteração), derivada das back edges do CFG; quando um nó não tem cor livre o alocador derrama o nó mais barato entre ele e seus vizinhos coloridos (um vizinho de menor custo é preferido a derramar o nó atual). Prova `SpillCostTest` 5/5. Fase C fatia 5 = `CallingConvention.java` — classificação caller/callee-saved por ABI (x86-64 SysV, AAPCS64, RISC-V LP64D), chaveada no enum existente `AbiLayout.Abi`; expõe os registradores de argumento inteiros e os conjuntos voláteis/preservados que o alocador consulta (um valor vivo através de uma chamada não pode ficar num registrador caller-saved). Dados puros de ABI; prova `CallingConventionTest` 5/5. Fase C fatia 6 = `SpillPlanner.java` — dado o conjunto de spills da alocação, computa (como dados puros) quais ops precisam de um reload inserido antes delas e quais precisam de um store-back depois; a ponte entre a decisão de alocação e o wiring (não-aditivo) do codegen. Nada o consome ainda; prova `SpillPlannerTest` 4/4. Métricas §18 = `IrMetrics.java` — um contador puro sobre o stream de ops (contagens de instrução/load/store/branch/call/return) mais as cifras de spill e uso de registradores da alocação, para que o efeito de uma otimização seja MEDIDO em vez de afirmado; prova `IrMetricsTest` 4/4. **Isto fecha a camada de análise/decisão da Fase C** (liveness + registradores virtuais + alocação + custo/seleção de spill + caller/callee-saved) de forma ADITIVA; o restante da Fase C é a materialização — ligar o alocador no codegen para de fato reduzir loads/stores artificiais (mudança de codegen, não aditiva; o ponto onde a Fase C encontra a Fase D)
next: DECISÕES TOMADAS (mantenedora 13/10) — (1) `D-CODEGEN-REWRITE`: rewrite completo, ABI System V x86-64 primeiro, o RegisterAllocator vira o modelo de locais do codegen; (2) `D-JS-ROBUST`: tornar o backend JS robusto a fluxo otimizado (plano de correção registrado: ensinar ao JsControlFlowParser o merge de pilha no jump-to-join); (3) promoção de future/ pendente da mantenedora nomear o plano. PRÓXIMA FATIA = começar `D-CODEGEN-REWRITE` aditivamente: formalizar o modelo de registradores SysV x86-64 sobre o `CallingConvention`/`AbiLayout` existentes (classes de registrador + um mapeamento local→registrador atrás de uma flag), mantendo o caminho frame-slot verde até a paridade. **D-CODEGEN-REWRITE fatia 1 = `X86_64RegisterFile.java`** — o arquivo de registradores System V x86-64 como dados puros: as duas CLASSES de registrador (INTEGER/SSE), registradores alocáveis por classe (callee-saved primeiro, rsp/rbp excluídos), registradores de argumento/retorno, e `classOf(Type)` (float/double → SSE); consistente com `CallingConvention.SYSV_X86_64`; ADITIVO (não binda nada); prova `X86_64RegisterFileTest` 8/8. **D-CODEGEN-REWRITE fatia 2 = `RegisterAssignment.java`** — resolve as cores abstratas do alocador para registradores x86-64 reais por classe (cor N → o N-ésimo registrador alocável da classe do slot); um slot spillado, um tipo desconhecido ou uma cor além da contagem de registradores da classe vai para a memória. **Restrição GC-safe (medida):** só int/bool são colocáveis em registrador — uma REFERÊNCIA fica no frame (o GC native é conservador e varre o frame, então uma ref só em registrador seria coletada enquanto viva); `RegisterAssignment.placeable(Type)`; ADITIVO; prova `RegisterAssignmentTest` 9/9. **D-CODEGEN-REWRITE fatia 3 = `RegisterPlanner.java`** — o driver por método que encadeia liveness→interferência→alocador→assignment num `Placement` (o ponto de entrada único que o rewrite do codegen chama); ADITIVO; prova `RegisterPlannerTest` 4/4. **D-CODEGEN-REWRITE fatia 4 = primitivas de consciência de chamada** — `LivenessAnalysis.liveAcrossCalls` (slots vivos logo após um `KofCall`) + `RegisterAllocator.allocate(..., callCrossing, calleeSavedCount)` (um slot que cruza uma chamada fica confinado às cores callee-saved `0..k-1` — a frente da lista alocável da classe — ou faz spill; uma colocação caller-saved deixaria a chamada clobberá-lo) + `X86_64RegisterFile.calleeSavedCount`; ADITIVO (nada os consome ainda); prova `CallAwareAllocationTest` 4/4 + `X86_64RegisterFileTest` 10/10. **fatia 5 (pré-requisito) = `Placement.calleeSavedUsed()`** — os registradores callee-saved que a colocação de um método USA (os que o prólogo deve salvar / o epílogo restaurar; um registrador callee-saved não usado não precisa de save); prova `RegisterPlannerTest` 6/6. **fatia 6 (pré-requisito) = `RegisterPlanner.eligibleForRegisterLocals(method)`** — o portão de elegibilidade conservador para a virada: só métodos cujos locais são TODOS da classe INTEGER (int/bool/ref) e que não usam `long`/`double` de largura dupla qualificam; métodos SSE/largura-dupla/`this`-não-INTEGER ficam no caminho frame-slot. Prova `RegisterPlannerTest` 9/9. **Design da fatia 7 (reconhecimento, AINDA não implementada):** a virada de emissão tem duas subtilezas medidas que precisam ser tratadas antes de ser segura. (1) **Visibilidade de raiz de GC** — o GC native é conservador e varre o FRAME; uma REFERÊNCIA guardada num registrador seria invisível a ele, então só locais int/bool podem ser colocados em registrador e locais de referência devem ficar no frame (o conjunto de registradores do alocador deve ser restringido por classe, e a elegibilidade refinada). (2) **Estágio no prólogo** — os args chegam em registradores ABI fixos; movê-los direto para o registrador atribuído arrisca o problema de parallel-move (o destino de um arg clobberar a fonte de outro), então os params devem ser estagiados pelo slot de frame primeiro (prólogo inalterado), depois copiados frame→registrador. Epílogo: os registradores callee-saved usados (rbx/r12–r15; `Placement.calleeSavedUsed`) são salvos no prólogo e restaurados em TODO sítio `KofReturn`/`KofReturnVoid` antes de `movq %rbp,%rsp` (lidos de volta dos seus slots de save no frame, já que `movq %rbp,%rsp` descarta a pilha). Registradores de rascunho confirmados livres: o emissor usa só %rax/%rcx/%rdx/%rdi/%rsi/%r8/%r9/%r10/%r11 (+%rip/%rsp/%rbp), nunca rbx/r12–r15. (3) **Extensão de frame** — os slots de save dos callee-saved devem ficar DENTRO do frame alocado: o chamador estende o frame em `regs.size()*8` e os coloca logo após os locais (`-(localsBytes + (i+1)*8)`); um offset além do frame escreveria abaixo de `%rsp` e corromperia a pilha. A camada de fragmentos (`NativeRegisterLocalAsm`, prova `NativeRegisterLocalAsmTest` 5/5) emite o texto prologue-save/epilogue-restore/load/store com os offsets dados pelo chamador, então o chamador é dono dessa correção. **Fatia 9 (virada de emissão) IMPLEMENTADA, atrás de flag OFF:** `NativeRegisterLocalSupport` + `NativeRegisterLocalAsm` ligam os fragmentos no `NativeMethodEmitter` para métodos ELEGÍVEIS só-int/bool — o prólogo salva os callee-saved usados (frame estendido pelos seus slots) e estagia os params pelo frame e depois os copia para os seus registradores (um `this`/param de referência nunca é colocado em registrador, então é pulado); `KofLoadLocal`/`KofStoreLocal` de um slot colocado empilham/desempilham o seu registrador; todo return/epílogo restaura o conjunto callee-saved. Controlada pela propriedade `kof.native.register.locals` (default OFF), então a produção fica INALTERADA. Provada: com a flag ON, `NativeRegisterLocalsE2ETest` 2/2 + um lote native 136/136 (NativeE2ETest 68, casts, overflow, divisão por zero, config, erasure, bounds de array) passam; com a flag OFF, as mesmas suítes passam (caminho de produção intacto). Dois bugs achados+corrigidos ao ligar: (a) os nomes de registrador precisavam do prefixo AT&T `%` (`X86_64RegisterFile` guarda nomes nus); (b) o prólogo deve copiar o slot 0 também — ele é o primeiro PARAM para um método estático (só `this` num método de instância é slot 0, e uma referência nunca é colocada em registrador). A flag agora está ON POR PADRÃO (produção) — validada no lote native x86-64 completo (224/224, incl. try-handler-leak, casts, overflow, divisão por zero, config, erasure, bounds de array) + aarch64 55/55 + riscv64 58/58; `-Dkof.native.register.locals=false` é uma escotilha de volta ao caminho frame-slot. **Elegibilidade ESTENDIDA:** o portão não exclui mais métodos com locais de largura dupla (long/float/double) ou de referência — `placeable` já mantém esses no frame, então qualquer método qualifica e só os seus locais int/bool são colocados em registrador (um método misto int+double coloca o int, mantém o double no frame). Validado no lote native x86-64 completo (229/229, incl. `SwitchLongDoubleSupportE2ETest`). **Referências ficam no FRAME (medido):** testei colocar referências em registrador e uma String viva em `rbx` sobreviveu a 50000 alocações num método SIMPLES (o coletor empilha os callee-saved antes de marcar) — mas o lote completo mostrou MÉTODOS COM try/catch/finally CRASHANDO (SIGSEGV, `NativeTryHandlerLeakE2ETest` + `NativeErasureBoxE2ETest`) quando referências são colocadas em registrador: o spill não é garantia de GC suficiente em todos os caminhos. Então `placeable` permanece só-int/bool — referências ficam no frame. (A extensão de elegibilidade int/bool acima É segura e fica.) **CAUSA-RAIZ + FIX (corrigida 14/10):** o crash de referência NÃO era um problema do grafo de interferência — era a EMISSÃO do `KofCatchStart`: ela gravava a exceção capturada no slot de FRAME do local incondicionalmente, então uma variável capturada COLOCADA EM REGISTRADOR (só uma referência pode ser ambas capturada e colocada em registrador) lia um valor stale → SIGSEGV. FIX: `KofCatchStart` agora é CIENTE DA COLOCAÇÃO (grava no registrador quando o slot está em registrador). Com esse fix, métodos com exceção são elegíveis de novo — a exclusão do ciclo anterior era SUPER-CONSERVADORA (com referências enquadradas, métodos com exceção sempre foram seguros; o crash só aparecia quando referências TAMBÉM eram colocáveis). Estado líquido: `eligibleForRegisterLocals` = true (qualquer método), `placeable` = só-int/bool (referências ficam enquadradas — ainda MEDIDO inseguro: `NativeE2ETest.nativeLambdaMutableCapture` crasha quando referências são colocáveis, porque lambdas não preservam uma referência capturada colocada em registrador). Validado no lote native x86-64 completo 225/225. **PARAMS agora colocados (14/10, achado medindo o asm emitido):** MEDI que `RegisterPlanner.plan` não produzia colocação para a forma comum `f(a, b) { ... }` — o grafo de interferência só cria nós para slots definidos por `KofStoreLocal`, e PARAMS são definidos pela ABI (chegam em registradores, nunca são armazenados), então o modelo de registradores era INERTE para params (a maioria dos métodos reais). FIX: `RegisterPlanner.plan` adiciona os slots de param via `InterferenceGraph.withParams` (params são vivos desde a entrada, então dois params vivos na entrada interferem e recebem registradores DISTINTOS); isto exigiu `LivenessAnalysis.blockCount()`. Dois bugs achados+corrigidos ao habilitar: (a) o `paramSlotMax` do emissor conta o slot 0 como `this` mesmo para método ESTÁTICO, então super-contava em um e o prólogo copiava um slot extra, ATROPELANDO um param vivo colocado em registrador (SIGSEGV em `NativeErasureBoxE2ETest.erasureBoxNativeX86`); o prólogo agora usa a contagem REAL de slots de param. Validado: lote native x86-64 completo 252/252 + cross (aarch64/riscv64) 113/113. Prova `RegisterPlannerTest` 12/12 (novo `twoParamsArePlacedInDistinctRegisters`). (`Av1CoeffsE2ETest.av1CoeffsOnNativeX86` falha com erro native de bounds de array TANTO com a flag ON quanto OFF — PRÉ-EXISTENTE, independente desta mudança.); a fatia JS-robust é a alternativa para destravar a §3 O2. **Reconhecimento D-JS-ROBUST (medido 14/10, caminho de fix do `D-JS-ROBUST` ESTREITADO):** investiguei ligar o `JumpThreading` (o passe estrutural do §3 O2) tornando a reconstrução do JS robusta. Achado: NÃO é um bloqueio, são DOIS, ambos nos pattern-matchers de forma-exata do DECOMPILADOR JS (o backend JS é um decompilador que reconstrói if/while/switch a partir dos alvos/labels exatos de salto). (1) Retarget de um salto CONDICIONAL quebra o `JsControlFlowParser` — o parser exige um `KofConditionalJump` imediatamente seguido de `Label(cj.trueLabel())` (`JsExpressionStatementParser`), que o threading quebra. (2) Threading SÓ de saltos INCONDICIONAIS conserta o bloqueio (1) — `KofJsE2ETest` 38/38 + `CoreRegressionE2ETest` 104/104 continuam verdes — mas a reconstrução de SWITCH (`JsSwitchParser`) então quebra num switch aninhado (`KofSwitchExprE2ETest.nestedJs` → `KofJS: store with empty stack`): ele deriva `switchEnd` e labels de corpo dos alvos exatos de `KofJump`, então um salto retargetado faz a varredura linear consumir um conjunto de ops diferente e dessincronizar a pilha. CONCLUSÃO: ligar o `JumpThreading` exige TANTO o parser if/loop QUANTO o parser de switch tornados cientes de trampolins — uma mudança multi-arquivo no decompilador JS, não uma fatia limitada; nada entregue em vez de uma regressão (Q5/zero-regressão). O caminho de fix é preciso: ensinar `JsControlFlowParser` E `JsSwitchParser` a resolver um alvo retargetado pelo mapa de trampolins (ou fazer o `JumpThreading` também colapsar os labels de trampolim agora inalcançáveis), então ligar a forma só-incondicional.
state: active
owner: `192.168.15.130:9093` (lane docs/lab)

---

## 1. Objetivo

Estabelecer uma estratégia de otimização para o backend Native do Kof capaz de produzir assembly pequeno, eficiente e previsível em diferentes arquiteturas sem transformar cada backend em um compilador independente.

O objetivo não é simplesmente emitir menos instruções.

O objetivo é preservar a intenção do programa através da pipeline:

```text
Kof
 ↓
AST
 ↓
Kof IR
 ↓
análise semântica
 ↓
otimizações independentes de ISA
 ↓
lowering
 ↓
otimizações específicas da ISA
 ↓
register allocation
 ↓
instruction selection
 ↓
assembly
```

A mesma construção Kof deve possuir uma representação intermediária suficientemente rica para permitir otimizações comuns, enquanto cada backend pode explorar características próprias da arquitetura de destino.

---

# 2. Princípios

## 2.1 Assembly não é o nível primário de otimização

O Kof não deve depender de transformar assembly já emitido como mecanismo principal de otimização.

Preferencialmente:

```text
AST
 ↓
IR
 ↓
IR otimizado
 ↓
backend
 ↓
assembly
```

e não:

```text
AST
 ↓
assembly ruim
 ↓
assembly optimizer
 ↓
assembly menos ruim
```

O assembly final pode possuir peephole optimization, mas isso deve ser uma última camada.

---

## 2.2 Otimização deve ser cross-target por padrão

Uma transformação deve ser implementada uma única vez quando sua validade independe da arquitetura.

Exemplos:

* constant folding;
* constant propagation;
* dead-code elimination;
* unreachable-code elimination;
* common subexpression elimination;
* simplificação algébrica;
* eliminação de temporários;
* propagação de cópias;
* redução de branches;
* análise de efeitos;
* eliminação de operações redundantes.

A arquitetura só deve participar quando a transformação depender da ISA, ABI ou modelo de memória.

---

## 2.3 O backend não deve conhecer a semântica inteira da linguagem

O backend deve receber uma representação já suficientemente reduzida.

O backend deve responder:

> “Como represento esta operação nesta ISA?”

e não:

> “O que o programa quis dizer?”

Isso mantém a fronteira:

```text
Kof IR
   │
   │ semântica
   ▼
Optimizer
   │
   │ operação reduzida
   ▼
Target Lowering
   │
   ▼
ISA
```

---

# 3. Camadas de otimização

A pipeline deve ser dividida em camadas explícitas.

## O1 — otimizações semânticas

Independentes de arquitetura.

Exemplos:

```text
1 + 2
↓
3
```

```text
x * 0
↓
0
```

```text
if (false) {
    ...
}
↓
remover bloco
```

```text
x = y
return x
↓
return y
```

Estas transformações devem operar sobre o IR.

---

## O2 — otimizações estruturais

Transformam o fluxo de controle e representação das operações.

Incluem:

* basic block simplification;
* branch folding;
* block merging;
* unreachable block removal;
* jump threading;
* loop simplification;
* loop invariant code motion;
* strength reduction;
* induction variable simplification.

Exemplo:

```text
if (condition) {
    return 10;
} else {
    return 20;
}
```

deve chegar ao backend já representado como um fluxo mínimo.

---

# 4. O3 — otimizações de memória

O Kof deve reduzir loads e stores desnecessários antes do lowering específico da ISA.

Exemplo:

```text
store x
load x
```

quando não existe nenhuma operação capaz de alterar `x` entre os dois pontos:

```text
valor já disponível
```

O backend não deve precisar gerar:

```asm
store
load
```

para depois tentar eliminar as duas instruções.

A análise deve ocorrer no IR.

---

# 5. O4 — register allocation

O Kof deve possuir uma etapa explícita de alocação de registradores.

Objetivo:

```text
IR temporaries
       ↓
virtual registers
       ↓
register allocation
       ↓
physical registers
```

A implementação inicial pode utilizar uma estratégia simples e determinística.

Posteriormente podem ser introduzidos:

* liveness analysis;
* interference graph;
* linear scan;
* graph coloring;
* spill heuristics;
* register class constraints;
* caller/callee-saved awareness.

A escolha da estratégia não deve fazer parte do contrato da linguagem.

---

# 6. O5 — target lowering

Depois das otimizações cross-target, operações abstratas devem ser convertidas para operações específicas da ISA.

Exemplo:

```text
ADD
```

pode virar:

```text
x86_64 → add
aarch64 → add
riscv64 → add
```

Mas algumas operações podem exigir sequências completamente diferentes.

O lowering deve encapsular essas diferenças.

---

# 7. O6 — otimizações específicas da ISA

Cada backend pode possuir otimizações próprias.

## x86_64

Possíveis otimizações:

* `lea` para operações aritméticas apropriadas;
* escolha entre `mov`, `lea` e operações combinadas;
* utilização eficiente de flags;
* redução de instruções para constantes;
* seleção de instruções com menor custo;
* utilização futura de SSE/AVX quando suportado.

Exemplo conceitual:

```text
a + b * 4
```

pode ser representado por:

```asm
lea rax, [rdi + rsi*4]
```

quando a situação permitir.

---

## AArch64

Possíveis otimizações:

* uso de immediate forms;
* addressing modes;
* combinação de shift + arithmetic;
* escolha adequada entre `add`, `sub`, `lsl` etc.;
* exploração de registradores temporários;
* instruções de extensão incorporadas ao acesso à memória.

Exemplo conceitual:

```text
load 32-bit signed value
extend to 64-bit
```

pode ser representado diretamente por uma instrução de load com extensão apropriada quando disponível.

---

## RISC-V 64

Possíveis otimizações:

* utilização de immediate forms;
* redução de instruções de materialização de constantes;
* addressing sequences;
* seleção apropriada de `addi`, `slli`, `add`, etc.;
* utilização de extensões disponíveis quando suportado pelo target;
* controle explícito de sequences afetadas por linker relaxation.

O backend não deve assumir que uma otimização válida em x86 existe em RISC-V.

---

# 8. Constant folding

Operações constantes devem ser resolvidas antes da geração de assembly.

Exemplo:

```kof
var x = 10 * 20
```

deve resultar em:

```text
x = 200
```

e não em:

```asm
mov ...
mov ...
imul ...
```

A otimização deve ocorrer no IR.

---

# 9. Constant propagation

Quando um valor é conhecido:

```text
x = 10
y = x + 2
```

deve tornar-se:

```text
y = 12
```

quando não houver efeitos que impeçam a propagação.

---

# 10. Dead Code Elimination

Código cujo resultado não possui observadores deve ser removido.

Exemplo:

```text
x = expensive()
return 42
```

Se `x` nunca for utilizado e `expensive()` não possuir efeitos observáveis:

```text
return 42
```

A análise de efeitos deve ser explícita.

O Kof não deve remover chamadas que possam:

* alterar memória observável;
* executar I/O;
* alterar estado global;
* acessar FFI;
* provocar efeitos definidos pelo runtime.

---

# 11. Copy propagation

Sequências como:

```text
a = b
c = a
```

podem ser reduzidas para:

```text
c = b
```

Isso também reduz pressão sobre registradores.

---

# 12. Strength reduction

Operações caras podem ser substituídas quando semanticamente equivalentes.

Exemplo:

```text
x * 2
```

pode tornar-se:

```text
x << 1
```

quando a semântica do tipo permitir.

Porém, a transformação não deve ser aplicada cegamente.

O custo real da operação depende da ISA e do contexto.

---

# 13. Peephole optimization

Uma pequena etapa final pode observar sequências específicas de instruções.

Exemplo:

```asm
mov rax, rbx
mov rcx, rax
```

pode ser reduzido para:

```asm
mov rcx, rbx
```

quando seguro.

Peephole optimization deve permanecer pequena.

Ela não deve virar uma segunda implementação do compilador.

---

# 14. ABI e otimização

A ABI faz parte do contrato do backend.

Otimizações nunca podem alterar incorretamente:

* registradores de argumento;
* registradores de retorno;
* caller-saved;
* callee-saved;
* stack alignment;
* layout de parâmetros;
* layout de structs;
* regras de retorno;
* convenções de chamada externas.

Especialmente para FFI:

```text
Kof
 ↓
ABI lowering
 ↓
C / foreign function
```

deve produzir exatamente a convenção esperada pelo alvo.

Uma otimização que quebra ABI é incorreta mesmo que produza assembly aparentemente melhor.

---

# 15. Structs e layout

Otimizações devem preservar o layout definido pelo contrato da ABI.

Para:

```c
struct Pair {
    int a;
    int b;
};
```

o backend deve manter:

```text
a = 32 bits
b = 32 bits
total = 64 bits
```

Otimizações podem eliminar loads/stores redundantes, mas não podem alterar o layout observável por FFI.

---

# 16. Cross-target equivalence

O mesmo programa Kof deve produzir resultados semanticamente equivalentes em:

```text
x86_64
aarch64
riscv64
```

Não é necessário que o assembly seja idêntico.

É necessário que:

```text
resultado
efeitos observáveis
ABI
layout
semântica
```

sejam equivalentes.

Portanto:

```text
mesmo IR
   ↓
┌──────────┬──────────┬──────────┐
x86_64     AArch64    RISC-V
 ↓           ↓          ↓
asm A       asm B      asm C
```

é esperado.

---

# 17. Benchmark-driven optimization

Nenhuma otimização específica de ISA deve ser promovida apenas porque “parece melhor no assembly”.

Cada otimização relevante deve possuir:

1. caso mínimo;
2. assembly esperado ou propriedades esperadas;
3. benchmark;
4. comparação antes/depois;
5. validação de corretude;
6. cobertura nos targets suportados.

O objetivo é evitar otimizações cosméticas.

Assembly menor também não significa necessariamente código mais rápido.

---

# 18. Métricas

O backend deve acompanhar, quando possível:

* tamanho do `.text`;
* número de instruções;
* número de loads;
* número de stores;
* número de branches;
* número de spills;
* número de chamadas;
* uso de registradores;
* tempo de execução;
* tamanho final do ELF.

A métrica deve ser escolhida conforme o objetivo da otimização.

---

# 19. Debuggabilidade

As otimizações não devem destruir completamente a capacidade de depuração.

Quando debug estiver habilitado, o backend deve preservar informações suficientes para relacionar:

```text
Kof source
    ↓
IR
    ↓
machine instruction
```

Otimizações podem remover variáveis ou fundir operações, mas isso deve ser representado corretamente nas informações DWARF futuras.

---

# 20. Determinismo

A geração de assembly deve ser determinística.

Dado:

```text
mesmo source
mesmo target
mesmas flags
mesma versão do compilador
```

o resultado deve ser reproduzível.

Isso facilita:

* testes;
* debugging;
* comparação de assembly;
* benchmarks;
* releases;
* investigação de regressões.

---

# 21. Fases de implementação

## Fase A — infraestrutura

* definir contratos do optimizer;
* definir representação de basic blocks;
* definir virtual registers;
* definir análise de uso;
* definir análise de efeitos;
* definir pipeline de passes.

**Critério:** nenhum backend existente pode perder comportamento.

---

## Fase B — otimizações básicas

Implementar:

* constant folding;
* constant propagation;
* copy propagation;
* dead-code elimination;
* unreachable-code elimination;
* basic block simplification.

**Critério:** redução mensurável de instruções em fixtures representativas.

---

## Fase C — register allocation

Implementar:

* liveness;
* virtual registers;
* allocation;
* spill;
* caller/callee-saved handling.

**Critério:** redução de loads/stores artificiais e manutenção da ABI em todos os targets.

---

## Fase D — target lowering

Formalizar:

```text
Kof IR operation
       ↓
Target lowering
       ↓
Machine instruction
```

Cada backend deve declarar explicitamente quais operações suporta.

**Fatia 1 da Fase D POUSADA (14/10):** `NativeOpSupport` — a declaracao de suporte a ops do backend native x86-64 como DADOS explicitos (`Set<Class<?>> X86_64`, as 37 ops de IR); o `switch` do emissor e a sua materializacao e o seu `default` agora assevera que a op NAO e declarada (R6: nunca silencioso). `NativeOpSupportTest` protege o contrato de tres formas: (1) a declaracao deve IGUALAR as ops tratadas pelo emissor (drift em qualquer direcao falha); (2) deve ser COMPLETA contra toda `KofOperation` do compilador — uma op NOVA falha aqui, um ponto de decisao deliberado, em vez de uma `UnsupportedOperationException` em runtime no campo; (3) PROVADO RED numa op removida de proposito (`KofUnary`) antes de restaurar.  **Fatia 2 da Fase D POUSADA (14/10):** a declaracao do backend CROSS — `NativeOpSupport.RISCV64_CROSS` (= `X86_64` menos `KofFunctionAddress`, que o lowering cross nunca materializa; chamadas de ordem superior vao por `KofCall`) + `supportsCross`. `NativeOpSupportTest` estende a guarda de drift ao emissor cross (`theCrossDeclarationMatchesTheCrossEmitterExactly`). E um fix real de Q7: o `default` do emissor cross SILENCIOSAMENTE anexava um comentario `# NATIVE002` para uma op nao tratada — um compile que "passa" com um binario errado (violando "no silent fallback"). Agora LANCA `IllegalStateException(\"NATIVE002: …\")`, que o `CompilerPipeline` converte num diagnostico NATIVE002 (nunca um artefato que finge rodar). Verificado que nenhuma op real o atinge: a suite cross completa (`NativeRiscv64E2ETest` 58/58 + `NativeAarch64E2ETest` 55/55) fica verde; lote native completo 483/483. **Fatia 3 da Fase D POUSADA (14/10):** a recusa de Q7 agora e GUARDADA por um teste — `NativeRiscvCrossRefusesTest` chama o emissor cross com `KofFunctionAddress` (a unica op fora de `RISCV64_CROSS`) e assevera que LANCA `NATIVE002` e nao emite nada; PROVADO RED contra o comportamento antigo (comentario silencioso) antes de restaurar.  **Fatia 25 da Fase E POUSADA (14/10, achada re-medindo o asm):** x86_64 §7 "choice between mov, lea and combined operations" — `lea{l,q} OP, %{e,r}ax` + `movq %rax, %rN` dobra em `lea{l,q} OP, %rN` (o lea escreve o destino do chamado diretamente, descartando o move). Seguro porque um lea nao tem efeitos colaterais alem de escrever o destino (sem flags) e le o seu operando ANTES de escrever o destino na MESMA instrucao, entao um destino que tambem aparece no operando e valido. MEDIDO: 93 sitios compute-em-rax-depois-move numa amostra, 13 deles pares lea (as dobras mais novas deixam o resultado do lea em rax, que um move seguinte re-materializa). Prova `NativeX86PeepholeTest` 47/47 + lote native 373/373 + cross + sanidade ampla 339/339. **Fatia 26 da Fase E POUSADA (14/10):** x86_64 §7 "efficient use of flags" — um teste flag-only imediatamente apos um `and/or/xor/test` do MESMO registrador completo (`andl $m, %eax; testl %eax, %eax` ou `; cmpl $0, %eax`) e REDUNDANTE e descartado: and/or/xor/test ja setam SF/ZF/PF daquele registrador e limpam CF/OF exatamente como `test %R,%R`. SO and/or/xor/test qualificam — `add`/`sub` setam CF (carry/borrow) diferente de `test` (CF=0), entao NAO sao dobrados (um `jb`/`jae` seguinte mudaria de sentido). MEDIDO: 37 sitios numa amostra. Prova `NativeX86PeepholeTest` 51/51 + lote native 527/527 + cross + sanidade ampla 343/343. **Fatia 27 da Fase E POUSADA (14/10):** x86_64 §7 "lower-cost instruction selection" — `jcc X` + `jmp Y` onde `X` e o label IMEDIATAMENTE apos o `jmp` significa "se cc vai para X (fall-through), senao para Y", que e exatamente `j!cc Y`. O passe inverte a condicao e descarta o `jmp` — uma instrucao menos por sitio. Tabela completa de inversao de condition codes; guardado contra `X == Y` e alvos indiretos (`jmp *reg`). MEDIDO: 188 sitios numa amostra. Prova `NativeX86PeepholeTest` 55/55 + lote native 563/563 + cross + sanidade ampla 347/347.

**Auditoria de consistencia da Fase D:** apos fazer o cross recusar, auditei todo `default` de emissor nativo por fallback silencioso. Os MCU (`NativeMcuRiscv32`/`NativeMcuArm`) ja recusam corretamente (default chama `unsupported(...)` -> NATIVE002), entao x86-64 (lanca `UnsupportedOperationException`), cross (agora lanca) e MCU sao UNIFORMES: declaram as ops suportadas, recusam o resto com codigo diagnosticado.

---

## Fase E — ISA optimization

Adicionar otimizações específicas:

```text
x86_64
AArch64
RISC-V
```

sem contaminar o IR com detalhes desnecessários de uma única arquitetura.

**Fatia 1 da Fase E POUSADA (14/10):** x86_64 §7 "reduction of instructions for constants" — um imediato que cabe num imm32 com extensão de sinal é emitido como um único `pushq $imm` em vez de `movq $imm, %rax; pushq %rax` (2 instruções → 1). Vale para `int` (sempre 32 bits) e para um `long` só quando ele volta por `int` (um long largo mantém o caminho de duas instruções, pois `pushq` estende o sinal). Prova `NativeConstEmissionTest` 5/5 (int, int negativo, long pequeno, long largo, bool) + o lote native x86-64 completo 225/225. É só-native (os backends JS/JVM não são afetados) e não exige mudança no IR — o requisito "sem contaminar o IR" do plano. **Fatia 2 da Fase E POUSADA (14/10):** x86_64 §7 "reduction of instructions" — o par no-op `pushq %rax` + `popq %rax` no sítio de return é colapsado: o valor de retorno é o último empilhado, e quando esse push foi `pushq %rax` (resultado de binário/chamada/load de frame — rax já o contém) o pop é redundante, então ambos são descartados; qualquer outro produtor (o `pushq %rbx` de um local em registrador, o `pushq $imm` de um literal) ainda precisa do pop para rax. O helper pula linhas de debug `.loc` à direita (uma posição de fonte fica entre o push e o return). Prova `NativeReturnPeepholeTest` 4/4 + o lote native x86-64 completo 234/234. Extraído para `NativeReturnEmit` para manter o `NativeMethodEmitter` sob o gate de 500 linhas (633 → 588; `dwarfKindOf` também foi para `NativeDwarf`). **Fatia 3 da Fase E POUSADA (14/10):** x86_64 §7 "reduction of instructions for constants" — uma contagem de shift CONSTANTE usa a forma imediata `shl{l,q} $N, %{e,r}ax` em vez de passar a contagem por `%rcx` (`pushq $N; popq %rcx; shl %cl`); a contagem é mascarada (31 para int, 63 para long) para casar exatamente a semântica JVM/hardware, e uma contagem computada mantém a forma `%cl`. Prova `NativeShiftImmediateTest` 4/4 + o lote native x86-64 completo 234/234. **Fatia 4 da Fase E POUSADA (14/10):** x86_64 §7 "reduction of instructions for constants" — um operando direito CONSTANTE de ADD/SUB/MUL/AND/OR/XOR usa a forma imediata `op{l,q} $N, %{e,r}ax` (ou `imul{l,q} $N, %{e,r}ax`) em vez de passar por `%rcx`. A constante é o ÚLTIMO valor empilhado (o RHS); para o SUB não-comutativo é exatamente o operando que a forma imediata espera, então o resultado é idêntico — um operando CONSTANTE PRIMEIRO (`100 - x`) NÃO está no topo e mantém corretamente a forma `%rcx` (verificado). Prova `NativeArithImmediateTest` 5/5 + o lote native x86-64 completo 238/238. **Fatia 5 da Fase E POUSADA (14/10):** x86_64 §7 "reduction of instructions for constants" — um operando direito CONSTANTE de uma COMPARAÇÃO (EQ/NE/LT/LE/GT/GE) usa a forma imediata `cmp{l,q} $N, %{e,r}ax` em vez de passar por `%rcx`; `x == 0`/`x != 0` usam o canônico `test{l,q} %eax, %eax`. Prova `NativeCompareImmediateTest` 6/6 + o lote native x86-64 completo 243/243. **REFACTOR-500 (14/10):** o `NativeX86Arith` chegou a 592 linhas (os itens §7 acima o cresceram), então a aritmética binária SSE float/double foi extraída para um novo `NativeX86SseArith` (os helpers de forma-imediata ficam no `NativeX86Arith`). `NativeX86Arith` 592 → 417 linhas; preserva comportamento (lote native completo 249/249 + `SwitchLongDoubleSupportE2ETest` + `FloatCompoundAssignE2ETest` + cross, tudo verde). **Fatia 6 da Fase E POUSADA (14/10):** x86_64 §7 "reduction of instructions for constants" — um operando direito CONSTANTE de um salto condicional INTEIRO (a forma de branch de uma comparação) usa a forma imediata `cmp{l,q} $N, %{e,r}ax` (ou o canônico `test{l,q} %eax, %eax` para `== 0`/`!= 0`) em vez de desempilhá-lo em `%rcx`; um operando computado mantém a comparação de registradores. Prova `NativeCondJumpImmediateTest` 3/3 + o lote native x86-64 completo 249/249. (Os helpers `constantPushedOnTop`/`dropLastNonLocLine` passaram a package-private no `NativeX86Arith` e são compartilhados.) **Fatia 7 da Fase E POUSADA (14/10):** x86_64 §7 "lower-cost instruction selection" — um peephole de texto (`NativeX86Peephole`) sobre cada corpo de método emitido colapsa o par adjacente `pushq %X; popq %Y` da máquina de pilha em `movq %X, %Y` (ou descarta ambos quando `X == Y`), uma instrução em vez de duas, sem ida-e-volta à pilha. Só casa pares registrador-registrador (um push imediato/memória fica intacto) e só dentro da região emitida do próprio método (um `.loc` entre o par não bloqueia). Aplicado no fim do `emitMethod`. Medido: um corpo de loop `s = s + a*2 + b` perde ~6 idas-e-voltas à pilha por iteração. Prova `NativeX86PeepholeTest` 6/6 + o lote native x86-64 completo 252/252 + cross + JVM/JS/switch. **Fatia 8 da Fase E POUSADA (14/10):** o peephole também colapsa um push IMEDIATO (`pushq $N; popq %Y` → `movq $N, %Y`, exato: o imediato cabe num imm32 com extensão de sinal por construção — ver `emitLoadLiteral`), enquanto um push de memória fica intacto. Um local inicializado com uma constante (`var s = 0`) agora emite um `movq $0, %reg` em vez de uma ida-e-volta à pilha. Prova `NativeX86PeepholeTest` 7/7 + o lote native x86-64 completo 259/259. **Fatia 9 da Fase E POUSADA (14/10):** o peephole também colapsa um push/pop que ABRANGE exatamente um `movq` (`pushq %A; movq %B, %C; popq %D` → `movq %A, %D; movq %B, %C`) — a forma de uma op binária cujos dois operandos são valores em registrador. Guardas estritas o mantêm correto: o movq do meio não pode ler nem escrever `%D` (risco de clobber). Economiza uma instrução em TODA op binária de dois registradores. Um bug achado+corrigido ao pousar: remover o push antes do pop deslocava o índice do pop e apagava a linha errada (SIGSEGV em `NativeE2ETest.execListGrow`); o pop agora é removido primeiro. Prova `NativeX86PeepholeTest` 10/10 (incl. a regressão do self-pop abrangido) + o lote native x86-64 completo 262/262. **Fatia 10 da Fase E POUSADA (14/10):** o peephole também dobra um carregamento de scratch na sua op: `movq %Y, %rcx` + `addl %ecx, %eax` → `addl %Y32, %eax` (a op lê só os 32 bits baixos de rcx, que o movq pôs exatamente em Y — exato; o resultado e as flags são inalterados). Remove uma instrução por op binária de dois registradores (`+ - * & | ^` e comparações inteiras). Guarda estrita: só quando `%rcx`/`%ecx`/`%cl` aparece no método APENAS como um carregamento de scratch seguido imediatamente de uma op substituível — um shift (contagem deve ficar em `%cl`) ou um store em rcx para um local em registrador desabilita toda a dobra. Prova `NativeX86PeepholeTest` 14/14 + o lote native x86-64 completo 266/266. **Fatia 11 da Fase E POUSADA (14/10):** a dobra de scratch também cobre o DESTINO flag-only de uma comparação (`movq %Y, %rcx; cmpl %eax, %ecx` → `cmpl %eax, %Y32`) e pula carregamentos de operando `movq %Z, %rax` independentes no meio, então a forma de condição de loop (`i < n` → `cmpl %eax, %r13d`) perde os dois carregamentos de scratch. A substituição usa a LARGURA do operando (32/64). Prova `NativeX86PeepholeTest` 16/16 + o lote native x86-64 completo 268/268. **Fatia 12 da Fase E POUSADA (14/10):** x86_64 §7 "lea for suitable arithmetic operations" — `movq %X, %rax` + `shl{l,q} $N, %{e,r}ax` (N=1/2/3, uma multiplicação por 2/4/8, o que o `StrengthReduction` produz) dobra num único `lea` que escala X diretamente (`leal (%ebx,%ebx), %eax`, `leal 0(,%ebx,4), %eax`, `leal 0(,%ebx,8), %eax`). Uma instrução em vez de duas; escala >8 não tem forma lea e fica intacta. Prova `NativeX86PeepholeTest` 21/21 + o lote native x86-64 completo 273/273. **Fatia 13 da Fase E POUSADA (14/10):** x86_64 §7 "efficient use of flags" — `movq %X, %rax` imediatamente seguido de uma comparação/teste que lê `%eax/%rax` dobra a materialização embora: `cmpl $2, %eax` → `cmpl $2, %X32`, `testl %eax, %eax` → `testl %X32, %X32`. Seguro porque a comparação lê só os 32 bits baixos de rax (ou o 64 completo), que o movq pôs exatamente em X, e tudo depois de uma comparação lê as FLAGS ou sobrescreve rax (setcc+movzbl), nunca o seu valor. Comum em condições de ramo (`if (n < 2)`). Prova `NativeX86PeepholeTest` 26/26 + o lote native x86-64 completo 273/273. **Fatia 14 da Fase E POUSADA (14/10):** x86_64 §7 "choice between mov, lea and combined operations" — `movq %X, %rax` + `add{l,q} %Y, %{e,r}ax` (ou `add $imm`) dobra num único `lea` que computa a soma sem materialização: `leal (%ebx,%r12d), %eax`, `leal 7(%ebx), %eax` (64-bit: `leaq`). Seguro porque o resultado do add do Kof é um valor em rax e as suas FLAGS não são usadas (comparações são `cmp` explícito, nunca um jcc consumidor de flags depois de um add), então o `lea` agnóstico-a-flags é equivalente. Assim `a + b` agora emite UMA instrução. Prova `NativeX86PeepholeTest` 29/29 + o lote native x86-64 completo 281/281. **Fatia 15 da Fase E POUSADA (14/10):** x86_64 §7 lea for suitable arithmetic operations — uma multiplicacao por 3/5/9 dobra num `lea` com indice escalado: `imull $3` vira `leal (%ebx,%ebx,2), %eax`, `$5` vira `leal (%ebx,%ebx,4), %eax`, `$9` vira `leal (%ebx,%ebx,8), %eax`. Outras constantes (7, 11, ...) nao tem forma base+indice*escala e ficam `imull`. Prova `NativeX86PeepholeTest` 32/32 + o lote native x86-64 completo 284/284. **Fatia 16 da Fase E POUSADA (14/10):** x86_64 §7 choice between mov/lea/combined — um add imediato dobra no deslocamento de um `lea` existente: `leal (%ebx,%ebx,2), %eax; addl $1, %eax` vira `leal 1(%ebx,%ebx,2), %eax` (e `leal (%ebx), %eax; addl $7` vira `leal 7(%ebx), %eax`). Seguro: o lea nao tem efeitos colaterais alem de escrever rax e as flags do add imediato nao sao usadas. Um lea com deslocamento existente fica intacto (match inequivoco). Prova `NativeX86PeepholeTest` 36/36 + o lote native x86-64 completo 284/284. **REFACTOR-500 (14/10):** as dobras de lea (shift/mul→lea, add→lea, add-into-displacement) foram extraídas para um novo `NativeX86LeaFold` (o arquivo chegou a 584 linhas, perto do gate de 600). `NativeX86Peephole` 584 → 357 linhas; preserva comportamento (lote native completo 288/288). **Fatia 17 da Fase E POUSADA (14/10):** x86_64 §7 lower-cost instruction selection — `cmp{l,q} $0, %reg` e reescrito para `test{l,q} %reg, %reg` (FLAGS identicas, codificacao menor, sem imediato). Prova `NativeX86PeepholeTest` 40/40 + o lote native x86-64 completo 288/288. **Fatia 18 da Fase E POUSADA (14/10):** x86_64 §7 lower-cost instruction selection — `movq %rbp, %rsp` + `popq %rbp` (o teardown do frame) e reescrito para a instrucao unica `leave` (mesmo efeito, um byte menor). Prova `NativeX86PeepholeTest` 42/42 + o lote native x86-64 completo 292/292. **Fatia 19 da Fase E POUSADA (14/10):** x86_64 §7 lower-cost instruction selection — um `jmp L` incondicional imediatamente seguido do label `L:` (so `.loc` no meio) salta para a instrucao seguinte, entao o salto e descartado. Prova `NativeX86PeepholeTest` 44/44 + o lote native x86-64 completo 296/296. **Fatia 20 da Fase E POUSADA (14/10):** AArch64 §7 "use of immediate forms" — `andi`/`ori` agora emitem o imediato bitmask DIRETO (`and x9, x9, #-16`, `orr x22, x22, #2`) em vez de sempre `mov x17, #imm` + `op x9, x9, x17`. Controlado por `NativeAarch64Helpers.isLogicalImm`, VERIFICADO contra o assembler real sobre uma tabela medida accept/reject (0 e -1 sao rejeitados; `0xFF00`/`0xAA..AA`/`0x00FF00FF00FF00FF` aceitos — a regra ingenua run-of-ones estava errada). Prova `NativeAarch64LogicalImmTest` 2/2 + `NativeAarch64E2ETest` 55/55 (qemu) + o lote native 283/283. **Fatia 21 da Fase E POUSADA (14/10):** AArch64 §7 lower-cost instruction selection — um peephole de texto (`NativeAarch64Peephole`, aplicado apos a traducao riscv→aarch64) colapsa a tripla push+pop da maquina de pilha riscv `sub sp,sp,#8; str X,[sp]; pop Y` em `mov Y, X` (ou descarta quando X == Y). MEDIDO: 29 de 99 sequencias de push numa amostra reduzem. Prova `NativeAarch64PeepholeTest` 5/5 + `NativeAarch64E2ETest` 55/55 (qemu) + cross + JVM/JS/switch. **Fatia 22 da Fase E POUSADA (14/10):** RISC-V 64 §7 lower-cost instruction selection — um peephole de texto (`NativeRiscvPeephole`, aplicado ao texto riscv64 antes do link E antes da traducao riscv→aarch64) colapsa a tripla push+pop `addi sp,sp,-8; sd X,0(sp); pop Y` em `mv Y, X` (ou descarta quando X == Y). MEDIDO: 30 de 82 sequencias de push numa amostra reduzem. Como o aarch64 e traduzido DESTE texto riscv, isto beneficia OS DOIS alvos cross. Prova `NativeRiscvPeepholeTest` 5/5 + riscv 58/58 + aarch64 55/55 (qemu) + cross. **Fatia 23 da Fase E POUSADA (14/10):** RISC-V 64 §7 lower-cost instruction selection — o peephole riscv tambem descarta self-moves `mv X, X` (no-op; a maquina de pilha riscv pode emitir um apos uma dobra). Prova `NativeRiscvPeepholeTest` 7/7 + riscv 58/58 + aarch64 55/55 (qemu) + cross. **Fatia 24 da Fase E POUSADA (14/10):** AArch64 §7 lower-cost instruction selection — o peephole aarch64 tambem descarta self-moves `mov X, X` (no-op). Prova `NativeAarch64PeepholeTest` 7/7 + aarch64 55/55 (qemu) + cross + JVM/JS/switch. Um bug de parse (o offset fixo de coluna assumia `shll`, um caractere mais curto que `imull`) foi pego e corrigido achando o `$`.

---

## Fase F — benchmark suite

Criar uma suíte cross-target contendo:

* aritmética;
* loops;
* branches;
* chamadas;
* structs;
* arrays;
* memória;
* FFI;
* operações bitwise;
* constantes;
* casos com alta pressão de registradores.

Cada benchmark deve ser executado nos targets disponíveis.

---

# 22. Critérios de promoção

Uma otimização pode sair de `future/` quando:

* possui especificação clara;
* possui implementação;
* possui testes de corretude;
* possui cobertura cross-target quando aplicável;
* não altera contratos de ABI;
* não introduz comportamento dependente de target no código cross-target;
* possui benchmark quando houver impacto de performance;
* não degrada significativamente outro target sem justificativa explícita;
* possui documentação do comportamento esperado.

Uma otimização que melhora x86_64 mas degrada AArch64 e RISC-V não deve ser tratada como otimização universal.

Ela deve ser explicitamente classificada como target-specific.

---

# 23. Regra de ouro

O backend deve preferir:

```text
semântica correta
    ↓
IR bom
    ↓
otimização geral
    ↓
lowering correto
    ↓
otimização específica
    ↓
assembly eficiente
```

e evitar:

```text
assembly gerado de forma ingênua
    ↓
centenas de patches
    ↓
peephole infinito
    ↓
backend impossível de manter
```

---

# 24. Resultado esperado

A arquitetura final deve permitir que uma única implementação Kof produza código nativo adequado para múltiplas arquiteturas:

```text
                     Kof
                      │
                      ▼
                     IR
                      │
             ┌────────┴────────┐
             │                 │
       otimizações         análise
       cross-target        semântica
             │                 │
             └────────┬────────┘
                      ▼
                Target Lowering
                      │
        ┌─────────────┼─────────────┐
        ▼             ▼             ▼
     x86_64         AArch64       RISC-V
        │             │             │
        ▼             ▼             ▼
      ASM           ASM           ASM
        │             │             │
        └─────────────┼─────────────┘
                      ▼
                   linker
                      │
                      ▼
                 executável
```

O Kof continua sendo responsável por sua própria geração nativa. C, LLVM ou outras linguagens/toolchains não são necessários como intermediários obrigatórios.

Ferramentas externas podem continuar existindo na borda do sistema para assembler, linker, debug ou interoperabilidade, mas **não definem a arquitetura do compilador Kof**.

---

# 25. Fora de escopo inicial

Este plano não exige inicialmente:

* SIMD automático;
* auto-vectorization;
* PGO;
* JIT;
* superoptimization;
* instruction scheduling avançado;
* speculative optimization;
* otimização agressiva de floating point;
* geração de código específica para cada microarquitetura;
* substituição do assembler/linker do sistema.

Esses recursos podem ser adicionados posteriormente quando houver evidência de necessidade.

---

## Estado final pretendido

O backend Native deve produzir assembly que seja:

**correto, pequeno, previsível, ABI-compatible, cross-target e competitivo**, sem sacrificar a simplicidade arquitetural do Kof.

A regra fundamental permanece:

> **Kof não precisa gerar o assembly mais complexo possível. Precisa gerar o assembly necessário, corretamente e sem complexidade acidental.**
