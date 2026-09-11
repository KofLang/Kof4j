# planning-otp-supervision.md — supervisão de workers estilo OTP (`one_for_one`) — PROPOSED

**Dono:** lane CONC · **Status:** PROPOSED (aguarda decisão da mantenedora — regra 6)
**Criado:** 10/09 · **Issue:** #83 (ViniciusKoiti) · **Zero código nesta fase**

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

### DD-OTP-03 — N workers sem bloquear
**N por supervisor (recomendado):** a thread supervisora roda
`while (ativos) { await handle_morto }` — mas `await` bloqueia num só.
Solução com o que existe: a stdlib já tem `selectAny` (handle multi-join,
Nativo polling, JVM notify) — supervisor = laço `selectAny(handles)` → o
morto é reiniciado → recadastra. Custo por filho: zero threads extras (1
thread supervisora). Alternativa (thread supervisora por worker) é 1-thread
por filho — aceitável no JVM virtual-threads, pior no x86. Recomendo
`selectAny` primeiro; medir.

### DD-OTP-04 — O que conta como falha
- exceção (String) não capturada → falha (único sinal cross-target real);
- terminar normal → **não** é falha em `transient`/`temporary`, é falha em
  `permanent` (semântica OTP, traduzível 1:1 com `await` que retorna);
- **travado sem crash (heartbeat): FORA do escopo inicial** — é outra
  feature (heartbeat+timer), e a issue acerta: conexão que trava é o caso
  caro. Fila própria (`OTP002` se promovida), nunca meia-implementada.

### DD-OTP-05 — Plano ou árvore
**Plano, sem aninhamento (recomendado p/ 1ª fatia).** Árvore exige
supervisor-como-filho + escalonamento semântico entre pais — puxa a
superfície inteira. Sem árvore, a resposta ao estouro é o callback do
DD-OTP-07. Reavaliar só com demanda real.

### DD-OTP-06 — Estado no restart
**Exigir fábrica que reconstrói (recomendado e travado na API):** o tipo do
`child(id, factory, ...)` documenta `factory = () -> worker` que **cria** o
estado (novos records/vars por restart). Não há como fazer melhor sem
isolamento de heap (BEAM-heap é o pilar que NÃO importamos); reiniciar
sobre a closure velha = crash-loop até o limite (a armadilha que a issue
aponta). Documentar no training: "factory nova = estado novo".

### DD-OTP-07 — Escalonamento (estouro do limite)
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

### DD-OTP-09 — Alvos
Puro-Kof (DD-OTP-01-A) = **JVM + Script + JS + Native x86 + riscv/aarch de
graça** (tudo usa só spawn/await/selectAny já existentes). Sem `OTP001` —
nenhum gap novo nasce desta feature (a restrição JS-01 não alcança
task-lambda; registrar no doc de paridade). `selectAny` polling no x86 é o
custo de regime, não de correção.

### DD-OTP-10 — Relógio injetável
**Sim (recomendado):** `.clock(nowFn)` default `time.now()`. Teste de
janela roda determinístico em todos os alvos, sem esperar wall-clock
(qemu distorce tempo — a issue acerta). Custo: 1 campo.

### DD-OTP-11 — Métrica de sucesso
**Gate booleano de contenção (recomendado p/ fechar a feature):** E2E —
worker que lança na 1ª invocação e termina na 2ª → supervisor reinicia,
programa completa, `supervisorStats().restarts == 1`. Orçamento de
regime-normal: supervisor vazio + 1 worker ok adds <5ms ao boot
(medido, não prometido). Throughput sob falha: FORA (harness novo —
fila própria se pedido).

### DD-OTP-12 — Onde os testes moram
E2E determinístico com `.clock()` injetado (não conflita com as exclusões
da matriz: **não entra na matriz** equality — é não-determinístico por
natureza, como `random.*`/`uuid`; paridade por asserts de contrato nos 5
alvos, padrão já estabelecido em `KofRandomTest`). CI: target matrix atual
basta (não exigir eixo JDK novo — registrar fallback platform-thread como
teste separado e menor, `bench`/long-run são fila própria).

### DD-OTP-13 — Colisão do `cancelled()` (256 slots)
Bug independente (a issue pede registro): **bug 101 em `known-bugs.md`** —
flag por `TID % 256`, dois workers podem herdar cancel do outro. Fix
possível (tabela maior + chaining por ponteiro de handle) é da lane
Native; pequeno e isolado. Não bloqueia OTP (que usa flag própria).

## Fatia proposta (se a mantenedora aprovar o desenho)

1. **S1 (1 sessão):** `kof.supervisor` puro-Kof no runtime stdlib +
   `.child/.restartLimit/.clock/.start/.stop/.escalate` + E2E JVM/JS
   (DD-OTP-11 gate) + doc/learn/training. Zero código de backend.
2. **S2:** Native x86 (selectAny laço) + Script; riscv/aarch por
   construção (mesmo IR), prova com assert-only (padrão bug 59).
3. **S3:** `supervisorStats` + janela ring + `temporary` drop + docs de
   paridade; promote p/ stable só com a matriz de gates completa (R5).

## O que NÃO é este plano

- Árvore de supervisores / monitores nomeados / link-monitor (DD-OTP-05 adia).
- Heartbeat contra hang (DD-OTP-04 — fila `OTP002`).
- Isolamento de heap (não é Kof — ver R12/core pequeno; a escolha é
  fábrica nova, não heap novo).
- WASM/JS Worker: sem SharedArrayBuffer hoje; supervisor puro-Kof já roda
  single-thread no JS (Promise) — paridade honesta, documentada.
