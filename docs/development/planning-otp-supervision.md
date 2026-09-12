# planning-otp-supervision.md — supervisão de workers estilo OTP (`one_for_one`) — EM DESENVOLVIMENTO

**Dono:** lane CONC · **Status:** 1ª fatia implementada 11/09 (núcleo em JVM+Script; Native=OTP001 §129, JS=OTP002 §132 — gates honestos). Autorização da mantenedora (issue #83, 11/09): implementar o menor núcleo funcional com testes.
**Criado:** 10/09 · **Emendado:** 11/09 · **Issue:** #83 (ViniciusKoiti)

> **Emendas de 11/09** (verificadas no código da `beta-0.4.0`, marcadas
> inline como "⚠️ Emenda 11/09"): §Separação de responsabilidades (nova),
> DD-OTP-01, 02, 03, 08, 09, 11 e a fatia S2. O motivo comum das duas
> emendas materiais: `selectAny` **não existe** em riscv64/aarch64, e a
> flag de shutdown depende de uma regra do SG-020 que ainda não tem prova.

## Estado das decisões (11/09)

| DD | Assunto | Estado |
|---|---|---|
| 01 | Forma (stdlib puro-Kof) | ⚠️ fechável **com emenda** — falta especificar o empacotamento de stdlib em `.kf` |
| 02 | Superfície (API) | ⚠️ fechável **com emenda** — a assinatura precisa declarar a camada |
| 03 | N workers sem bloquear | ❌ **ABERTA** — `selectAny` ausente em riscv/aarch; falta decidir o fallback |
| 04 | O que conta como falha | ✅ fechada |
| 05 | Plano ou árvore | ✅ fechada |
| 06 | Estado no restart | ✅ fechada |
| 07 | Escalonamento | ✅ fechada (contradição do texto resolvida) |
| 08 | Shutdown | 🚫 **BLOQUEADA** — regra 5 do SG-020 sem prova |
| 09 | Alvos | ❌ **ABERTA** — depende do 03 |
| 10 | Relógio injetável | ✅ fechada |
| 11 | Métrica de sucesso | ✅ fechada (quatro gates) |
| 12 | Onde os testes moram | ✅ fechada |
| 13 | Colisão do `cancelled()` | ✅ fechada (condicional ao 08) |

**8 fechadas · 2 fecháveis com emenda · 2 abertas · 1 bloqueada.**
As 8 são independentes entre si — podem ser ratificadas sem esperar as outras.
O caminho crítico é só o **DD-OTP-08**, e ele se resolve com um teste, não
com design: o padrão stop-flag (escritor + leitor em laço com deadline).

A issue #83 traz a análise OTP-vs-Kof completa e correta. Este documento
**verificou cada afirmação dela contra o código atual** (tudo confirmado —
ver §Fatos) e transforma as 13 caixas abertas em **DDs numeradas** com
recomendação técnica por DD. A decisão é da mantenedora; o documento existe
para que a decisão caiba numa leitura.

## Fatos verificados no código (não memória)

| Afirmação da issue | Verificação 10/09 |
|---|---|
| `spawn`/`await` desaçucaram p/ `__kof_spawn_expr`/`__kof_await` (sem nó AST) | `parser/ExpressionParser.java:96-107` ✅ |
| join implícito (shutdown hook espera tasks) | `jvm/JvmRuntimeCore.java` `KOF_ACTIVE_TASKS` + `addShutdownHook` ✅ |
| `cancelled()` native = flag por TID, 256 slots colisão | `runtime/RuntimeConcurrency.java:18` (`.space 256`) ✅ |
| `awaitTimeout`/`selectAny` no native = polling 1ms | `usleep(1000)` em `:212,:341` ✅ |
| `CONC003-JS-01`: handler-lambda (mq/timer/UI) não pode `await` | `CompilerPipeline.java:78` + `docs/concurrency.md:139` ✅ — MAS **lambda de `spawn` PODE** (é task-lambda; `spawn { await ... }` é o uso coberto por teste) |
| sem `sigaction` no Native | zero ocorrências em `runtime/`+`nat/` ✅ |
| `cancelled()` JS/interpretador sempre 0 | `docs/backend-parity.md:84` ✅ |

Fato NOVO material: o supervisor **puro-Kof** (sem runtime novo) roda no
spawn-task — a restrição `CONC003-JS-01` não o alcança. E o heap
compartilhado (o defeito p/ restart limpo) é a alavanca do shutdown
cooprativo cross-target: uma `Bool` capturada em `Box` é visível ao worker
em TODOS os targets, sem depender do `cancelled()` quebrado em 2 dos 5.

## Separação de responsabilidades (supervisor / runtime / worker)

> ⚠️ **Emenda 11/09** — seção nova. É o "ponto principal" declarado pela
> mantenedora na discussão da #83 e não estava no documento. Os DDs dizem
> *onde mora o código*; esta tabela diz *quem garante o quê*. Sem ela, a
> feature tende a virar mecanismo por backend.

| Camada | Responsabilidade | Estado hoje |
|---|---|---|
| **Supervisor** | acompanhar filhos, detectar encerramento, aplicar política de reinício, controlar shutdown | não existe |
| **Runtime** | fornecer término observável, cancelamento e tempo | parcial — término só via `Handle`+`await`; `spawn` comando **engole** a exceção (`JvmRuntimeCore.java:195-205`); `cancelled()` = `0` em JS/interp; `time.now()` ok |
| **Worker** | cooperar: checar a flag de parada e ser reconstruível pela fábrica | contrato hoje implícito — precisa virar documentado |

Duas fronteiras que estão borradas hoje e que esta separação corrige:

1. **Política no runtime.** `kof_spawn` decide sozinho o que fazer com a
   falha (imprime em stderr e segue) — isso é decisão de política dentro da
   camada de mecanismo. O supervisor só enxerga o que passa por
   `spawn expr` + `Handle`; `spawn` como comando é invisível para ele.
   **Consequência para a API:** todo filho supervisionado precisa nascer
   como `spawn` expressão, e isso é contrato, não detalhe.
2. **Responsabilidade sem contrato no worker.** `cancelled()` e a flag do
   DD-OTP-08 só funcionam se o worker checar. Isso deve estar escrito na
   assinatura do `.child(...)`, não no texto de um tutorial.

## Os DDs (cada um: opções + recomendação)

### DD-OTP-01 — Forma: onde mora a lógica
- **A (recomendada): stdlib puro-Kof** (`kof.supervisor`, objeto como
  `kof.mq`/`scheduler`) implementado COM a linguagem: lista de factories,
  laço `spawn`+`await`+`try/catch` + janela com `time.now()`. Zero asm,
  zero mudança de parser/typer, **paridade por construção nos 5 alvos**,
  sobrevive a troca de backend, WASM de graça. O "supervisor" é um objeto
  Kof; quem o roda é um `spawn` do usuário (ou da própria stdlib).
- B: runtime por backend (5) com `whenComplete` na JVM — notificação sem
  polling, mas custo em asm ×5 e risco de divergência (regra 5). Só se
  justifica SE A for insuficiente (medir depois; provável não).
- C: keyword nova — **rejeitar**: superfície congelada (regra 6, 0.2.6) +
  irreversível. Builtin no typer (estilo `selectAny`) é sombreável — não.
- Complexidade pertence à plataforma, e a plataforma Kof já tem
  spawn/await/try-catch: o supervisor é **código Kof**, não feature de VM.

> ⚠️ **Emenda 11/09 — falta o mecanismo de empacotamento.** A decisão (A)
> está certa, mas a analogia *"objeto como `kof.mq`/`scheduler`"* não se
> sustenta: `kof.mq` e `kof.scheduler` **não são Kof puro** — são
> `KofMq.java` e `KofScheduler.java`, código Java no compilador com runtime
> emitido por backend. **Não existe hoje nenhum módulo de stdlib escrito em
> `.kf`.** O único precedente de fonte Kof empacotada é
> `kof-compiler/src/main/resources/dev/kof/android-host.kf` (resource).
> **Pendência:** especificar como uma stdlib em Kof é compilada e ligada ao
> programa do usuário em cada alvo. Enquanto isso não estiver escrito, o
> "zero código de backend" da fatia S1 está subestimado.

### DD-OTP-02 — Superfície (API)
Proposta (mínima, aditiva, experimental tier R5):
```kof
supervisor(name)                                  // objeto
  .child(id, factory, policy)                     // permanent|transient|temporary (String)
  .restartLimit(max, windowMillis)
  .clock(nowFn)                                   // DD-OTP-10 (injeção p/ teste)
  .escalate(fn)                                   // DD-OTP-07
  .start()                                        // spawn supervisor-thread
  .stop()                                         // DD-OTP-08
supervisorStats()                                 // {started, restarts, dropped} p/ teste/E2E
```
Sem monitor nomeado / árvore (DD-OTP-05) — pilha mínima primeiro.

> ⚠️ **Emenda 11/09 — a API precisa declarar a camada.** A superfície lista
> métodos mas não diz o que o supervisor *promete*, o que ele *exige do
> runtime* e o que *exige do worker*. Como a separação de responsabilidades
> é o ponto central da proposta, isso pertence à assinatura:
> `.child(id, factory, policy)` deve documentar que `factory` **cria** o
> worker (DD-OTP-06) e que o worker **precisa checar a flag de parada**
> (DD-OTP-08) para que `.stop()` tenha efeito.

### DD-OTP-03 — N workers sem bloquear
**N por supervisor (recomendado):** a thread supervisora roda
`while (ativos) { await handle_morto }` — mas `await` bloqueia num só.
Solução com o que existe: a stdlib já tem `selectAny` (handle multi-join,
Nativo polling, JVM notify) — supervisor = laço `selectAny(handles)` → o
morto é reiniciado → recadastra. Custo por filho: zero threads extras (1
thread supervisora). Alternativa (thread supervisora por worker) é 1-thread
por filho — aceitável no JVM virtual-threads, pior no x86. Recomendo
`selectAny` primeiro; medir.

> ⚠️ **Emenda 11/09 — `selectAny` não existe em dois alvos.**
> `nat/NativeRiscvSpawn.java` emite **apenas** `kof_spawn_result`,
> `kof_spawn`, `kof_await` e `kof_spawn_join_all`. Não há
> `kof_select_any`, `kof_poll`, `kof_done`, `kof_cancel` nem
> `kof_await_timeout` em nenhum emissor riscv/aarch64 — e **não há gate de
> compile-time**, então o uso cai em erro de link por símbolo indefinido
> (padrão do bug 59), não num gap honesto. **Emenda:** o mecanismo de N
> workers precisa de fallback declarado — uma thread supervisora por filho
> onde `selectAny` não existe — ou riscv/aarch ficam limitados a **um
> worker por supervisor**, registrado como PARTIAL.

### DD-OTP-04 — O que conta como falha

> **✅ DECIDIDO — proposta de fechamento 11/09, aguarda ratificação (regra 6):**
> falha = exceção (String) não capturada que escapa do corpo do worker.
> Término normal é falha **apenas** em `permanent`. Heartbeat/hang fica
> fora, em fila própria (`OTP002`).
>
> **Limitação aceita e documentada:** exceções em Kof são `String`, sem
> hierarquia de tipos, e no Native o primeiro `catch` sempre captura — logo
> a política de reinício **não pode discriminar por tipo de erro**, só pela
> policy declarada no filho. Isso é contrato explícito, não omissão.

- exceção (String) não capturada → falha (único sinal cross-target real);
- terminar normal → **não** é falha em `transient`/`temporary`, é falha em
  `permanent` (semântica OTP, traduzível 1:1 com `await` que retorna);
- **travado sem crash (heartbeat): FORA do escopo inicial** — é outra
  feature (heartbeat+timer), e a issue acerta: conexão que trava é o caso
  caro. Fila própria (`OTP002` se promovida), nunca meia-implementada.

### DD-OTP-05 — Plano ou árvore

> **✅ DECIDIDO — proposta de fechamento 11/09, aguarda ratificação (regra 6):**
> supervisor plano, sem aninhamento. A resposta ao estouro do limite é o
> callback do DD-OTP-07, não um pai. A abstração de filho nasce genérica o
> bastante para aninhar depois sem quebrar a API. Árvore só com demanda real.

**Plano, sem aninhamento (recomendado p/ 1ª fatia).** Árvore exige
supervisor-como-filho + escalonamento semântico entre pais — puxa a
superfície inteira. Sem árvore, a resposta ao estouro é o callback do
DD-OTP-07. Reavaliar só com demanda real.

### DD-OTP-06 — Estado no restart

> **✅ DECIDIDO — proposta de fechamento 11/09, aguarda ratificação (regra 6):**
> o reinício **sempre** recria o worker pela fábrica; nunca reaproveita o
> estado que levou à falha. Confirmado pela mantenedora na discussão da #83.
> `.child(id, factory, policy)` documenta na própria assinatura que
> `factory` **cria** o worker. Sem isolamento de heap é o mais forte que se
> pode garantir — reiniciar sobre a closure velha é crash-loop até o limite.

**Exigir fábrica que reconstrói (recomendado e travado na API):** o tipo do
`child(id, factory, ...)` documenta `factory = () -> worker` que **cria** o
estado (novos records/vars por restart). Não há como fazer melhor sem
isolamento de heap (BEAM-heap é o pilar que NÃO importamos); reiniciar
sobre a closure velha = crash-loop até o limite (a armadilha que a issue
aponta). Documentar no training: "factory nova = estado novo".

### DD-OTP-07 — Escalonamento (estouro do limite)

> **✅ DECIDIDO — proposta de fechamento 11/09, aguarda ratificação (regra 6):**
> `.escalate(fn)` é **opcional, com default definido** — não obrigatório.
> Isso resolve uma contradição do texto original, que pedia *"callback
> obrigatório no start() se há limite"* e na linha seguinte descrevia um
> *"default sem `.escalate`"*: se fosse obrigatório, não haveria default.
>
> - **com** `.escalate(fn)`: ao estourar o limite o supervisor chama
>   `fn(id, motivo, contagem)` na thread supervisora e para de reiniciar
>   aquele filho — o usuário decide o que fazer;
> - **sem** `.escalate`: diagnóstico R6 no stderr e o supervisor para de
>   reiniciar aquele filho.
>
> Em nenhum dos dois casos o supervisor mata o processo, e em nenhum ele
> desiste em silêncio (R6).

Sem pai na árvore, as opções honestas: (a) matar processo — brutal, e
`process.exit` é interop (não stdlib-pura); (b) desistir em silêncio —
**proibido (R6)**; (c) **callback `.escalate(fn)` obrigatório no start() se
há limite (recomendado):** o supervisor chama `fn(id, motivo, contagem)`
na thread supervisora — o usuário decide (logar, trocar de estratégia,
sair). Default sem `.escalate`: diagnóstico R6 no stderr + supervisor
para (não reinicia, não mata o processo). Zero keyword nova.

### DD-OTP-08 — Shutdown (`stop()`)
**Cooperativo por flag Kof (recomendado):** `.stop()` seta `Bool` capturada
(visible no heap compartilhado — funciona em JS/interpretador onde
`cancelled()` é 0) + drenagem por deadline: espera filhos até
`stopDeadlineMillis` via `awaitTimeout`, depois desiste (diagnóstico,
nunca silencioso). `cancel(handle)` do runtime é bônus onde funciona (JVM/
x86), nunca a única alavanca. O join implícito é liberado quando o laço da
supervisora termina — nada novo aqui, só honestidade no deadline.

> ⚠️ **Emenda 11/09 — BLOQUEADA: a flag não tem garantia de visibilidade.**
> A afirmação *"uma `Bool` capturada em `Box` é visível ao worker em TODOS
> os targets"* depende da **regra 5 do SG-020** (SC para statics e campos
> compartilhados). Essa regra **ainda não está provada**: o teste citado,
> `KofConcurrency2Test.staticsAreSequentiallyConsistent:699`, não tem
> nenhuma escrita concorrente em campo compartilhado — declara
> `Resultado.r1..r4` e nunca os usa; o que prova é a borda do **await**. E
> `volatile` é non-goal da linguagem (SG-020 §5), com
> `BoxClassFactory.java:25` criando o campo como `AccessFlags.PUBLIC`
> simples (`ACC_VOLATILE` não aparece em nenhum ponto do compilador).
> Na JVM, portanto, `while (!parar.value)` pode ter a leitura içada do laço
> e **nunca observar o `stop()`** — falha que passa em teste curto e trava
> em worker de longa duração, exatamente o caso de uso da feature.
> **Destrava com:** um E2E do padrão stop-flag (escritor + leitor em laço,
> com deadline que falha se a escrita não for observada), ou reescrita da
> regra 5. Se a flag própria cair, a alternativa é o `cancel` do runtime —
> e aí o **bug 101 deixa de ser independente e passa a bloquear**
> (ver DD-OTP-13).

### DD-OTP-09 — Alvos
Puro-Kof (DD-OTP-01-A) = **JVM + Script + JS + Native x86 + riscv/aarch de
graça** (tudo usa só spawn/await/selectAny já existentes). Sem `OTP001` —
nenhum gap novo nasce desta feature (a restrição JS-01 não alcança
task-lambda; registrar no doc de paridade). `selectAny` polling no x86 é o
custo de regime, não de correção.

> ⚠️ **Emenda 11/09 — a cobertura declarada está errada.** "riscv/aarch de
> graça" é falso: `selectAny`, de que o DD-OTP-03 depende, não existe
> nesses alvos (ver emenda do DD-OTP-03). Isso contraria o princípio
> enunciado pela mantenedora — *"não faz sentido definir uma API que
> funcione bem em um backend e seja apenas uma promessa nos outros"*.
> **Cobertura real:** JVM + Native x86_64 + JS + Script. **riscv64/aarch64
> = PARTIAL declarada**, limitados a um worker por supervisor até alguém
> portar os auxiliares (trabalho da lane Native, junto com o gate R6 que
> hoje também falta).

### DD-OTP-10 — Relógio injetável

> **✅ DECIDIDO — proposta de fechamento 11/09, aguarda ratificação (regra 6):**
> `.clock(nowFn)`, default `time.now()`. Custo de um campo. É o que torna o
> gate de limite de reinícios determinístico em todos os alvos, e o que faz
> um teste cross sob qemu medir o supervisor em vez de medir o emulador.

**Sim (recomendado):** `.clock(nowFn)` default `time.now()`. Teste de
janela roda determinístico em todos os alvos, sem esperar wall-clock
(qemu distorce tempo — a issue acerta). Custo: 1 campo.

### DD-OTP-11 — Métrica de sucesso

> **✅ DECIDIDO — proposta de fechamento 11/09, aguarda ratificação (regra 6):**
> os **quatro gates** da emenda abaixo são o critério de fechamento do
> núcleo mínimo. O orçamento de regime normal é **medido, não prometido**.
> Throughput sob falha fica fora (exigiria harness novo — `kof bench` só
> mede wall-clock de processo curto).
>
> **Registrado explicitamente:** supervisão **não é feature de desempenho**.
> Em regime sem falha, com supervisor é igual ou pior que sem. O ganho é
> disponibilidade — e o benchmark de overhead existe para provar que o custo
> é pequeno, não para mostrar ganho.

**Gate booleano de contenção (recomendado p/ fechar a feature):** E2E —
worker que lança na 1ª invocação e termina na 2ª → supervisor reinicia,
programa completa, `supervisorStats().restarts == 1`. Orçamento de
regime-normal: supervisor vazio + 1 worker ok adds <5ms ao boot
(medido, não prometido). Throughput sob falha: FORA (harness novo —
fila própria se pedido).

> ⚠️ **Emenda 11/09 — são quatro gates, não um.** A mantenedora nomeou
> explicitamente os testes que fecham o núcleo mínimo; o texto acima cobre
> só o segundo:
>
> | Gate | Prova | Encosta em |
> |---|---|---|
> | Observação de falha | o supervisor **sabe** que o filho morreu | `spawn expr` + `Handle` |
> | Reinício individual | só o que morreu reinicia; os outros seguem | `one_for_one` + `.child(...)` |
> | Limite de reinícios | N reinícios em janela T param o ciclo | `.restartLimit` + `.clock` |
> | Encerramento controlado | `.stop()` termina de fato, sem pendurar no join implícito | `.stop()` + DD-OTP-08 |
>
> O quarto gate depende do DD-OTP-08, hoje bloqueado.

### DD-OTP-12 — Onde os testes moram

> **✅ DECIDIDO — proposta de fechamento 11/09, aguarda ratificação (regra 6):**
> E2E determinístico com `.clock()` injetado, **fora** da matriz de equality
> — o construto é não-determinístico por natureza, como `random.*`/`uuid`.
> Paridade por asserts de contrato nos alvos, padrão já estabelecido em
> `KofRandomTest`. CI: a matriz de target atual basta; eixo JDK (fallback de
> platform thread) e `bench`/long-run ficam em fila própria.

E2E determinístico com `.clock()` injetado (não conflita com as exclusões
da matriz: **não entra na matriz** equality — é não-determinístico por
natureza, como `random.*`/`uuid`; paridade por asserts de contrato nos 5
alvos, padrão já estabelecido em `KofRandomTest`). CI: target matrix atual
basta (não exigir eixo JDK novo — registrar fallback platform-thread como
teste separado e menor, `bench`/long-run são fila própria).

### DD-OTP-13 — Colisão do `cancelled()` (256 slots)

> **✅ DECIDIDO — proposta de fechamento 11/09, aguarda ratificação (regra 6):**
> bug 101 (flag por `TID % 256`, dois workers podem herdar o cancel um do
> outro) é trabalho **independente** da lane Native e **não bloqueia**
> supervisão — **enquanto** o DD-OTP-08 usar flag própria. Se o DD-OTP-08
> cair para o `cancel` do runtime, este item **vira bloqueador**.
> Dependência registrada explicitamente.

Bug independente (a issue pede registro): **bug 101 em `known-bugs.md`** —
flag por `TID % 256`, dois workers podem herdar cancel do outro. Fix
possível (tabela maior + chaining por ponteiro de handle) é da lane
Native; pequeno e isolado. Não bloqueia OTP (que usa flag própria).

## Fatia proposta (se a mantenedora aprovar o desenho)

1. **S1 (1 sessão):** `kof.supervisor` puro-Kof no runtime stdlib +
   `.child/.restartLimit/.clock/.start/.stop/.escalate` + E2E JVM/JS
   (DD-OTP-11 gate) + doc/learn/training. Zero código de backend.
2. **S2:** Native x86 (selectAny laço) + Script. ⚠️ **Emenda 11/09:**
   riscv/aarch **não** saem por construção — `selectAny` não existe lá
   (DD-OTP-03/09). Ou entram com fallback de uma thread supervisora por
   filho, ou ficam PARTIAL com um worker por supervisor.
3. **S3:** `supervisorStats` + janela ring + `temporary` drop + docs de
   paridade; promote p/ stable só com a matriz de gates completa (R5).

## O que NÃO é este plano

- Árvore de supervisores / monitores nomeados / link-monitor (DD-OTP-05 adia).
- Heartbeat contra hang (DD-OTP-04 — fila `OTP002`).
- Isolamento de heap (não é Kof — ver R12/core pequeno; a escolha é
  fábrica nova, não heap novo).
- WASM/JS Worker: sem SharedArrayBuffer hoje; supervisor puro-Kof já roda
  single-thread no JS (Promise) — paridade honesta, documentada.

## Spike medido + 1ª fatia (11/09 — fatos, não memória)

- **Forma DD-OTP-01-A (puro-Kof) CONFIRMADA viável e entregue** como pacote
  virtual `kof.supervisor` (host `dev/kof/supervisor-host.kf` escrito em Kof,
  injetado só no `import kof.supervisor` — mecanismo do android-host; o
  DD-OTP-01 previa "objeto como kof.mq/scheduler"; a injeção .kf resolve a
  pergunta de distribuição do plano sem backend Java ×5).
- **Fábrica = interface** (DD-OTP-06 "factory nova sempre"): campo/param de
  tipo-função está quebrado (§127 cast `as ()->T` → VerifyError; PARSE016 em
  campo `() -> Int`), e `class X(...)` primário é record imutável. `interface
  KofWorkerFactory { KofWorker novo() }` roda nos alvos viáveis.
- **Observação por `try{await}catch` num laço `vigiar` POR FILHO** (thread
  dedicada), NÃO polling `done`/`selectAny`: `selectAny` de primitivo quebra no
  JVM (§128) e o polling de handle-falha é frágil sem preempção (§132 no JS).
  Isso é a alternativa "thread supervisora por worker" do DD-OTP-03 — a do
  `selectAny` único ficou para quando §128/§132/§129 fecharem.
- **Impeditivos que tiveram que ser resolvidos/contornados:** §130 corrigido
  (SEM024 falso em corpo de método re-analisado — travava o builder fluente);
  §131 contornado (sobrecarga por aridade quebrada → `child` de 3 args único).
- **Paridade honesta:** JVM + Script rodam o núcleo; Native/JS bloqueiam no
  compile-time (OTP001/OTP002) por §129/§132 — NUNCA binário que trava (regra
  6). **S2 do plano (N workers sem bloquear, selectAny)** continua pendente:
  exige §128 (unbox selectAny) e, no Native, §129 (unwind por-thread). O
  documento fica em `docs/development/` até o Native fechar.
