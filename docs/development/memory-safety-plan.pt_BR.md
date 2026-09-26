[English](memory-safety-plan.md) | [Português](memory-safety-plan.pt_BR.md)

# Memory safety — ownership, lifetime, borrowing, aliasing (D-MEMORY-SAFETY)

> **Status: frente ATIVA, dono = lane paridade (mantenedora 25/09,
> `DECISIONS.md` §`D-MEMORY-SAFETY`).** Fase 0 (investigação) **CLOSED
> 25/09** — `docs/spec/memory-safety-investigation.md` aceita. Fase 1 (spec)
> **CLOSED 25/09 — a mantenedora escolheu "revisar e fechar" (opção A da
> lista de decisões)**: `docs/spec/memory-safety.md`(+PT) é a spec aceita.
> Edições no core (Fases 2+) esperam a fila de
> desenvolvimento atual fechar (restrição final do brief; opção J — ZERO
> edições prematuras no core até lá).

**Objetivo:** definir uma semântica de memória séria para o Kof, de forma que
classes inteiras de bugs de memória sejam impossíveis — ou vivam atrás de uma
fronteira explícita que o programador precisa nomear. NÃO é uma feature
chamada `ownership`: o critério de sucesso é o compilador poder dizer *"este
programa não pode produzir esta classe de erro"* (use-after-free, double-free,
dangling reference, escape de lifetime inválido, mutable aliasing unsafe,
null inesperado, data race acidental).

## Restrições duras do brief (travadas em `D-MEMORY-SAFETY`)

1. **O Kof já tem null safety** — nunca reinventar/substituir/duplicar;
   só estudar nullability × ownership × lifetime × borrowing.
2. **Investigation Kof-first** — nenhuma suposição de que o Kof funciona
   como Rust, C++, Java, Kotlin, Swift ou Zig (rule 10; rule 8: pedido de
   feature de outra linguagem não é bug do Kof).
3. **Arquitetura antes de código** — a investigação (Fase 0) e a spec
   (Fase 1) precedem QUALQUER edição no compilador.
4. **A implementação espera a fila atual** (brief, linha final).
5. **Lei da Simplicidade** (rule 11): garantias fortes sem transformar
   código Kof numa corrente infinita de anotações de lifetime.
6. **Cross-target por construção** — JVM, Native, JS e o WASM planejado
   expressam a mesma semântica Kof; GC na JVM/JS nunca desculpa divergência
   semântica; a FFI define o dono em cada travessia.
7. **Diagnósticos e testes fazem parte da feature** — toda regra chega com
   casos válido/inválido/diagnóstico-esperado/regressão/por-backend; suítes
   pequenas por domínio, adaptadas à árvore de testes real.
8. **Proibido:** copiar o borrow checker do Rust, inventar sintaxe (`let`,
   `const`, marcadores de move estrangeiros), reescrever a null safety,
   refactor big-bang do compilador, ownership de um único backend, esconder
   problemas de ownership no runtime.

## Fases (cada uma gates a seguinte; fase só fecha com prova)

| Fase | Entregável | Gate |
|---|---|---|
| **0 — Investigação** ✅ | `docs/spec/memory-safety-investigation.md` (EN+PT): estado atual (parser/AST/semântica/tipos/IR/resolução de símbolos/mutabilidade/closures/escopo/lifetime implícito por backend: JVM/Native/JS/infra-WASM/FFI/ponteiros/collections/async), riscos encontrados, bugs existentes relacionados (varredura do § ledger), pontos frágeis, proposta, alternativas consideradas, impacto de compatibilidade, plano incremental | doc de investigação aceito (revisão da mantenedora); as 20 perguntas do §1 respondidas com evidência file:line — **FECHADA 25/09** |
| **1 — Especificação** | `docs/spec/memory-safety.md` (EN+PT): Ownership, Lifetime, Borrowing, Aliasing, Mutability, Move, Copy, Clone, Drop/Destruction, Escape, Closure Capture, Concurrency, FFI, Unsafe Boundaries — cada um com: permitido / proibido / requer-sync / compile-time / runtime / dependente-de-tipo | spec aceita; a matriz de segurança (§22 do brief) escrita na sintaxe REAL do Kof |
| ~~**2 — Infraestrutura do compilador**~~ | representações internas de ownership/lifetime/borrow/alias/mutability/escape/resource-state — **✅ FECHADA 26/09** (fatias 1–4: `OwnerKind`/`MemRule`/`ManagedResource`/`CaptureMode`/`MoveDetector`+`MoveTransfer` em `dev.kof.compiler.memory`; `MemoryModelTest` 8/8; `Build + Tests` success `9bcddfe90` — zero mudança de comportamento) | ALCANÇADO 26/09 (estruturas compilam; suite byte-green no runner) |
| **3 — Primeiras garantias** | use-after-move; referências pendentes; escapes inválidos; mutable aliasing; dupla ownership/destruição | por regra: caso válido compila, caso inválido recebe o diagnóstico NOMEADO, teste de regressão, prova por backend |
| **4 — Closures & async** | semântica de captura de closure; callbacks; async/futures; iteradores/geradores | mesma forma de prova |
| **5 — Native & FFI** | ponteiros/alocação/destruição/C ABI/demais conectores; a tabela de ownership Kof↔C↔Rust↔JVM↔Python | todo tipo de fronteira tem decisão de dono/quem-libera/quem-guarda + teste |
| **6 — JVM / JS / WASM** | a mesma semântica em todos os backends | matriz de prova cross-target verde |

## Próximo passo imediato (esta lane)

**Fase 0** — varrer o compilador pelas 20 respostas do §1 (representação de
variável, valor vs referência, cópia vs compartilhamento, consciência de
escape, mutabilidade no type system, captura de closure, ownership na FFI,
liberação no Native, representação por backend) e varrer o `known-bugs.md`
por bugs existentes de referência/aliasing/lifetime/recurso/padrões (a classe
do §503 GC-root já é uma entrada). Produzir o doc de investigação.
**Nenhuma edição no compilador.**

**Fase 2 DESTRAVADA 26/09 pela mantenedora (chat: "fase 2 destravada").**
A lane agora constrói as representações internas do compilador — pacote
`dev.kof.compiler.memory` com modelos de ownership/lifetime/borrowing/
aliasing/mutability/escape/resource-state + o enum de códigos de diagnóstico
`MEMxxx` da spec (§1–§10). Somente estruturas + testes; **zero mudança de
comportamento** (suite byte-green). Emissão/encaminhamento dos diagnósticos
é da Fase 3.

**Fase 2 POUSADA 26/09 (fatias 1–4, tip `9bcddfe90`):** `OwnerKind` (§2.1),
`MemRule` (as 31 regras O/L/B/M/E/C/N com diagnóstico ou permissivas),
`ManagedResource` (§9), `CaptureMode` (§6.1), `MoveDetector`+`MoveTransfer`
(O-02, somente-leitura, sem emissão) — `MemoryModelTest` 8/8, zero mudança de
comportamento. A fila de representação está EXAUSTA.

> **PEDIDO DE DECISÃO — RESOLVIDO 26/09 por D-COMPLETE-FIRST (DECISIONS.md):**
> o padrão de move `var a = b; b = null` colidia com N-02/SEM048 (literais
> null vedados). A regra da mantenedora (26/09, chat) torna a forma completa
> idiomatica a decisão: **re-expressar O-02 sem literal null (c)** — o passe
> de análise de ownership/lifetime no pipeline do compilador, com emissão
> MEM001/MEM002/MEM005 e os casos de interação nos 4 alvos, pousando como um
> pacote completo (passe + emissão + prova por alvo; sem diagnóstico solto,
> sem stub, sem aceitar gap). **Fase 3 DESTRAVADA.**

**Fase 3 — fila de fatias (cada uma entrega completa do seu escopo declarado):**

| Fatia | Face | Estado |
|---|---|---|
| **1** Regiao retilinea | O-01/`MEM001` dupla reivindicacao + O-02/`MEM002` uso-apos-claim via alias — `OwnershipPass` ligado no `StatementAnalyzer.analyzeBody` (frontend compartilhado = mesma analise nos 4 alvos), `MemorySafetyE2ETest` (invalidos nos 4, validos byte-green com golden JVM/Script) | POUSADA 26/09 |
| **2** Cruzamento de fluxo | claim/leitura condicionais (if/while/try/switch) com merge de estado por ramo | pendente |
| **3** Escape/dangling | L-04/`MEM013` (captura estende vida) e faces de dangling da tabela §3 | pendente |
| **4** Aliasing mutavel em fronteiras | B-03/`MEM020` (buffer FFI escrevivel unico) + B-04/`MEM021` em `spawn` | pendente |
| **5** Containers & nao fechados | O-03/`MEM003` (clear libera) + L-05/`MEM014` (§9: web/db sem close) | pendente |

## Fase 3 — design (DESTRAVADA 26/09 por `D-COMPLETE-FIRST`; pacote = passe + emissão + prova por alvo)

A superfície de emissão é o que EXISTE na superfície do usuário (medido
26/09, não assumido):

- **MEM005 (ownership FFI) — JÁ SATISFEITA na fronteira**: o Native recusa
  externs record/array/out-buffer com `FFI001` NA LINHA DA DECLARAÇÃO
  (`interop.md`, medido); JVM/JS fazem copy-back; retornos String são
  copiados na fronteira em todo alvo. A fatia 3.3 documenta isso como a face
  compile de O-05 — NENHUM diagnóstico duplicado será inventado para um
  caminho já honesto (regra 11).
- **MEM001/MEM002 (ownership/dangling no release) — fatia 3.1**: o único
  release real na superfície é `.close()` (close de `web` medido em
  `KofWeb.java:54`; db/catálogo). Passe `MemoryReleaseAnalysis` em
  `dev.kof.compiler.memory`: após `x.close()` num caminho sensível a
  ramificação, QUALQUER uso posterior de `x` = MEM001 (dono já liberou) com
  posição; o passe é NA FRENTE do compilador, logo os 4 alvos emitem o MESMO
  diagnóstico por construção (prova de paridade = um E2E por alvo
  compilando as MESMAS fontes). Programas válidos ficam byte-verdes (nunca
  usam handle fechado); o caso nunca-fechado é WARNING (MEM014) — servidores
  legitimamente rodam até o fim do processo — nunca erro.
- **MEM021 (aliasing mutável em spawn) — fatia 3.2**: `spawn` capturando
  objeto MUTÁVEL que o pai também muta depois do spawn (e vice-versa), sem
  `await`/`join_all` entre os dois, é a face proibida B-04/C-03 da spec;
  computável no mesmo passe; ERRO no padrão claro de corrida, silêncio fora
  dele — zero falso-positivo é pré-requisito de pouso.

DoD do pacote: passe + wiring + `MemorySafetyE2ETest` por alvo (JVM/Script/
JS/Native com as mesmas fontes e os mesmos diagnósticos) + nota de corpus em
`training/idioms/concurrency.md`+`interop.md` quando a emissão pousar; cada
fatia pousa completa ou não pousa (`D-COMPLETE-FIRST`).

## Definition of done (a frente inteira)

As 12 perguntas do §27 respondidas na spec, a lista de classes de bugs
impossíveis explícita, e a implementação casando com a spec com a matriz de
segurança do §22 verde por backend — "umas estruturas chamadas
`Ownership`/`Borrow`/`Lifetime`" NÃO é done.

**Relacionamentos:** `DECISIONS.md` §`D-MEMORY-SAFETY` (o registro da
decisão); `PARITY-GAPS.md` (o bloqueador atrás do qual esta frente fila);
`rule 6` (semântica congelada — qualquer semântica de ownership que mude
ordem de avaliação ou contratos de operador é decisão da mantenedora, nunca
edição de agente).
