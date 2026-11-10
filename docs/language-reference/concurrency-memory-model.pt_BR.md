[English](concurrency-memory-model.md) | [Português](concurrency-memory-model.pt_BR.md)

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
| `lock (alvo) { ... }` | exclusão mútua sobre `alvo` (objeto não nulo) durante o bloco; liberado em toda saída (fim, `return`, `break`/`continue`, throw); reentrante (G240, 06/11) |

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
7. **lock:** sair de um bloco `lock (x) { }` HB a próxima entrada num bloco
   `lock (x) { }` sobre o mesmo objeto `x`, por qualquer fluxo (HB = borda do
   lock). Dois fluxos nunca rodam ao mesmo tempo blocos travados no mesmo
   `x`; o fluxo que segura o lock pode entrar de novo (reentrante).

## 3. Mapeamento por target (como a spec é honrada)

| Target | Fluxo (green thread) | Primitivo de sincronização | HB garantido por |
|---|---|---|---|
| **JVM** | virtual thread (`Thread.startVirtualThread`, 21+; platform no ART) | JMM | `CompletableFuture` (spawn/await), `LinkedBlockingQueue` (Channel), `ReentrantLock` por objeto (`lock`) — todos garantem HB conforme JMM §17.4 |
| **Native x86_64** | `clone(220)` com heap compartilhado | x86-TSO (hardware forte) | futex (`SYS_futex` 202) WAIT/WAKE em spawn/await/channel; lock `lock cmpxchg`; `lock (x)`: tabela reentrante de locks segurados sob um guard futex (`RuntimeLock`) |
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

> **⚠️ Emenda 14/09 — a regra 5 agora tem prova E correção de backend.**
> `stopFlagFieldWriteObservedBySpinReader` +
> `stopFlagCapturedBoxObservedBySpinReader` (`KofConcurrency2Test`) são o
> padrão stop-flag do DD-OTP-08: campo de instância (e campo de Box capturada
> por closure) escrito por um fluxo, girado em laço por outro, com a escrita
> do escritor aos +100ms dentro de um laço de 500M iterações. **Pré-fix
> (medido, 3/3):** o campo era emitido simples (`BoxClassFactory`
> `AccessFlags.PUBLIC`; nenhum `ACC_VOLATILE` em ponto algum do compilador) e
> o C2 hoistava o `getfield` fora do laço — o leitor imprimia
> `nao-observou`, i.e. `.stop()` jamais seria observado por um worker longevo.
> **Fix (causa raiz, mesma unidade):** todo campo **mutável** (não-`final`) de
> classe Kof agora é emitido com `ACC_VOLATILE` (emissão de campos de
> `JvmBackend`; `AccessFlags.VOLATILE = 0x0040`, o bit de field-flags da §4.7
> que divide valor com o `BRIDGE` de method-flags). Campos `final` (records,
> refs de closure) mantêm a semântica. Isso conforma o CÓDIGO à regra 5 da §2
> (ratificada 09/09) — a spec já era lei, o código era o desvio. Pós-fix,
> `observou` prova a borda na JVM. JS/interpretador andam no runtime da JVM
> (§3); x86-TSO não precisava de fence para este padrão (SC da regra 5);
> riscv/aarch herdam via tradutor (§3). **DD-OTP-08 (`.stop()` do supervisor)
> está DESTRAVADO**: a flag que o supervisor seta agora é garantidamente
> visível ao worker sem `volatile` como superfície de linguagem (segue
> non-goal §5; a abstração carrega a prova).

## 5. O que NÃO é especificado (non-goals)

- **Modificadores** `volatile`/`synchronized` explícitos na superfície da
  linguagem — Kof não expõe mecanismo (regra 1: intenção, não mecanismo);
  eles continuam um warning (SEM091). As abstrações corretas são Channel
  (mensagens, não memória) e, desde 06/11, o bloco `lock (alvo) { }`
  (decisão da mantenedoria, G240): uma seção crítica com escopo sobre um
  objeto, com a borda HB da regra 7 do §2, liberada em toda saída. Ao
  contrário de um modificador de método, ele nomeia o objeto que exclui e não
  deixa o lock escapar do bloco.
- Preempção com quantum — green threads são cooperativos por ponto de
  agendamento (await/channel/time), sem garantia de fatia de tempo.
- Modelo relaxed/acquire-release exposto ao usuário — SC-only é a spec;
  relaxar seria quebra de contrato (bump + discussão).
