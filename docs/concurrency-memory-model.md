# Kof Concurrency Memory Model (SG-020)

> **Estado:** SPEC ADOTADA (decisão 11 da mantenedora, 09/09) — implementada
> e VALIDADA 10/09: as 5 bordas de HB da §2 têm prova em
> `KofConcurrency2Test`/`SpawnE2ETest` (verde na suíte). SG-020 FECHADO — o
> documento saiu de `docs/development/` (não há trabalho pendente aqui).
> **Origem:** decisão 11 do maintainer sobre os gaps de spec ("implementar
> modelo de memória concorrente com green threads").
> **Data:** 09/09/2026 · lane spec-gaps · movido p/ docs/ 11/09.

## 1. O que é especificado

O modelo de concorrência de Kof é **sequentially consistent (SC)** em todos
os targets. Toda observação de escrita concorrente por outro fluxo de
execução obedece happens-before **total** — sem reordenação observável
dentro de um mesmo fluxo.

Primitivas (todas da linguagem, nunca mecanismo):

| Primitiva | Semântica |
|---|---|
| `spawn f()` / `spawn { ... }` | cria fluxo de execução (green thread); fire-and-forget quando o valor é descartado |
| `val h = spawn f()` → `await h` | Handle<T>; `await` **bloqueia** o chamador até o retorno e estabelece HB |
| `time.interval(ms, fn)` / `scheduler.every(ms) { }` | job recorrente; cancelamento por id |
| `Channel<T>` | FIFO; `send` bloqueia se cheio, `receive` bloqueia se vazio; cada valor entregue a exatamente 1 receptor |

## 2. Happens-before (regras normativas)

1. **spawn:** tudo que o pai escreveu antes do `spawn` é visível ao filho
   (HB = borda do spawn).
2. **await:** tudo que a tarefa escreveu antes de retornar é visível ao
   awaiter depois do `await` (HB = borda do await).
3. **Channel:** `send(v)` HB `receive()` que entrega `v`.
4. **Cancelamento** (`cancel(h)`/`h.cancelled()`): o cancelamento é
   HB-observável pela tarefa alvo em seu próximo ponto de agendamento; não
   há preempção síncrona.
5. **Locais são privados** ao fluxo; **statics e campos de objetos
   compartilhados** seguem SC (escritas atômicas por campo; sem tornados
   de reordenação observáveis).
6. **Data race** (mesma variável sem as bordas acima): o comportamento é
   definido como **leitura do valor mais recente em ordem de
   sincronização** (SC) — nunca valor "rasgado" para tipos de uma palavra
   (Int/Bool/Char/refs). `Long`/`Double` em targets nativos de 32 bits não
   existem (targets são 64 bits) — sem word-tearing em nenhum target.

## 3. Mapeamento por target (como a spec é honrada)

| Target | Fluxo (green thread) | Primitivo de sincronização | HB garantido por |
|---|---|---|---|
| **JVM** | virtual thread (`Thread.startVirtualThread`, 21+; platform no ART) | JMM | `CompletableFuture` (spawn/await), `LinkedBlockingQueue` (Channel) — ambos garantem HB conforme JMM §17.4 |
| **Native x86_64** | `clone(220)` com heap compartilhado | x86-TSO (hardware forte) | futex (`SYS_futex` 202) WAIT/WAKE em spawn/await/channel; lock `lock cmpxchg` |
| **Native riscv64/aarch64** | herda x86 via tradutor | fraco — precisa **fence explícito** | futex + `fence rw,rw` / `dmb ish` nos pontos de HB (já emitidos pelo runtime: `amoswap` acq/rel, `fence`→`dmb ish` no tradutor) |
| **Interp/Script** | virtual thread (mesmo runtime JVM) | idem JVM | idem JVM |

**Regra de paridade (R5/R6):** qualquer target novo deve provar as 5 bordas
de HB da §2 com os E2Es da §4 antes de sair de `experimental`.

## 4. Provas de conformidade (DoD)

1. `spawnWriteBeforeAwaitIsVisible` — pai escreve `x=41`, spawn lê e soma,
   await devolve 42 (borda 1+2).
2. `channelSendHappensBeforeReceive` — send de valor construído, receive
   observa efeito colateral do construtor (borda 3).
3. `staticsAreSequentiallyConsistent` — N spawn incrementando o MESMO static
   N×1000 vezes; resultado final exato N×1000 (borda 5, atomicidade por
   campo).
4. `noWordTearingOnLong` — Long static escrito por 1 fluxo, lido por outro
   em loop, nunca observa valor parcial (borda 6).
5. Cancelamento: `cancel` seguido de `cancelled()` observável na tarefa
   (borda 4) — coberto pelos testes existentes de handle.

> **Estado atual das provas (validado 10/09):** (1)(2)(5) já cobertos por
> `SpawnE2ETest`/`KofConcurrency2Test` (canal JVM+Native, spawn/await
> cross-target). **(3) `staticsAreSequentiallyConsistent` (4×soma 0..999 =
> 1998000, HB de await) e (4) `noWordTearingOnLong` (leitor nunca observa
> valor parcial) IMPLEMENTADOS** — `KofConcurrency2Test:699/:737`, verde na
> suíte (29/0/1-skip qemu). Todas as 5 bordas de HB da §2 têm prova.

> **⚠️ Emenda 11/09 — a regra 5 ainda não tem prova.** Revisão de
> `KofConcurrency2Test:699` (`staticsAreSequentiallyConsistent`): o programa
> declara `Resultado.r1..r4` e **nunca os usa** — as quatro tarefas chamam
> `soma1000()`, função pura sem estado compartilhado, e o pai soma os valores
> devolvidos pelos `await`. Não há **nenhuma escrita concorrente em campo
> compartilhado** no teste. O que ele demonstra é a borda **2 (await)**, não a
> regra **5 (SC para statics e campos de objetos)**. O comentário do próprio
> teste já reconhece o limite: *"data race de read-modify-write NÃO é atômico
> por definição do modelo"*.
>
> `noWordTearingOnLong` (`:737`) **é** prova real da regra 6 — há escrita
> concorrente em `Estado.v` com leitura em laço.
>
> **Lacuna que importa para `development/planning-otp-supervision.md`
> (DD-OTP-08):** o padrão **stop-flag** — um fluxo escreve `Bool = true`, outro
> lê em laço até observar — não é coberto por teste nenhum. `Estado.pronto` é
> escrito em `noWordTearingOnLong` e nunca lido. Como `volatile` é non-goal
> (§5) e `BoxClassFactory.java:25` cria o campo como `AccessFlags.PUBLIC`
> simples (`ACC_VOLATILE` não aparece em nenhum ponto do compilador), na JVM a
> visibilidade da flag depende inteiramente de a regra 5 valer — justamente a
> que falta provar. Sem isso, `.stop()` pode nunca ser observado por um worker
> de longa duração: falha que **passa** em teste curto e **trava** em produção.
>
> **Pendência (destrava o DD-OTP-08):** ou um E2E do padrão stop-flag
> (escritor + leitor em laço, com deadline que falha se a escrita nunca for
> observada), ou reescrever a regra 5 para o que as provas de fato cobrem.

## 5. O que NÃO é especificado (non-goals)

- `volatile`/`synchronized` explícitos na superfície da linguagem — Kof não
  expõe mecanismo (regra 1: intenção, não mecanismo). Se um programa precisa
  de controle fino, a abstração correta é Channel (mensagens, não memória).
- Preempção com quantum — green threads são cooperativos por ponto de
  agendamento (await/channel/time), sem garantia de fatia de tempo.
- Modelo relaxed/acquire-release exposto ao usuário — SC-only é a spec;
  relaxar seria quebra de contrato (bump + discussão).
