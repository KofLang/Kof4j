# CONCURRENCY.md — Modelo de Concorrência Kof

**Status:** Implementado nos 3 targets, concorrência real nos 3 (JVM virtual threads + JS async/await/Promise + Native pthread) — 0.2.6-beta 03/09
**Versão:** 0.2.6-beta
**Data:** 3 de setembro de 2026 (CONC003 fechado — JS deixa de ser sequencial)

---

## 0. Implementado (0.2.6-beta)

### `spawn` — statement

```kof
spawn processarFila()      // chamada de função como tarefa
spawn {                    // bloco inline (lambda sem capturas)
    println("background")
}
```

Semântica implementada:

- a tarefa roda concorrentemente (JVM: virtual threads; Native: OS threads via `pthread_create`);
- o programa **espera as tarefas antes de sair** (join implícito — contador +
  shutdown hook no JVM; `pthread_join` no Native);
- o valor de retorno da função é descartado (fire-and-forget);
- exceções na tarefa são impressas em stderr (não derrubam o programa);
- ~~**Native**: diagnostic `CONC001`~~ — ✅ fechado 31/08: `pthread_create` +
  trampoline + `await`/`pthread_join` + allocator thread-safe (lock futex) +
  join implícito no main (histórico: "spawn: not supported on the Native
  target yet" — gap documentado, nunca mascarado);
- isolamento por valor: a tarefa recebe os argumentos; sem estado
  compartilhado primitivo na linguagem.

### Implementado 0.1.0 → 0.2.6-beta

- `spawn` statement + `val r = spawn f()` + `await r` com `Handle<T>` tipado e unboxing (`KofAwaitTest` 7/7, `KofConcurrency2Test` 10/10) — JVM; JS **async real** via `async`/`await`/`Promise` do GraalJS, fechado 03/09 (`CONC003`, ver seção 4); Native pthread completo (CONC001 fechado)
- `done(h)`/`poll(h)` não-bloqueantes, `cancel(h)`/`cancelled()` (cancel cooperativo) e `selectAny(h1, h2, …)` — JVM + Native (polling 1ms, `KofConcurrency2Test`); JS via handle `{done,value,error,promise}` + `Promise.race` (`cancelled()` sempre `0` no JS — sem thread-local, ver seção 4); Android segue `AND001`
- `awaitTimeout(r, ms)` — valor se a task terminar no prazo; senão lança exceção (capturável via `try/catch`) — JVM (`Future.get(ms)`) + Native (polling 1ms com deadline) + JS (polling cooperativo via `await Promise.resolve()`, dispara de verdade contra task mais lenta — `KofConcurrency2Test.awaitTimeoutSlowTaskJs`)
- `channel<T>()` — FIFO thread-safe com `c.send(v)`/`c.receive()` — JVM (`LinkedBlockingQueue`, `put`/`take` bloqueantes) + Native (lista ligada + mutex futex + polling 1ms) + JS (fila de resolvers pendentes — `receive()` em canal vazio bloqueia de verdade até um `send()` posterior, `KofConcurrency2Test.channelBlocksBeforeSendJs`)
- Lambdas com captura via `BoxN` já suportam `spawn { println(x) }` — inclusive captura de variável mutada de escopo externo, nos 3 targets
- `kof.mq` publish/subscribe/queue — **3 targets** (JVM in-memory; Native asm 01/09, MQ001 fechado; JS in-process); `kof.time interval/cancel` — JVM+Native

### Não exposto

Nenhuma API de plataforma (Thread/Runnable/Executor) é visível na linguagem.
`Thread.startVirtualThread` é detalhe interno do runtime JVM.

### Próximas iterações (P2)

- ~~filas produtor/consumidor tipadas (`kof.concurrent.Queue`)~~ — ✅ 31/08, canal com bloqueio real 03/09: `channel<T>()` com `send`/`receive` (JVM `LinkedBlockingQueue` bloqueante + Native FIFO futex + JS fila de resolvers pendentes);
- ~~`CONC003` — async real no target JS~~ — ✅ 03/09: `async`/`await`/`Promise` do GraalJS, `KofJsRunner` drena a fila de microtasks (`kofActiveTasks`), ver seção 4;
- scheduler nativo (threads no target Native — depende de futex/clone);
- `select` múltiplo com timeout (`selectAny` já ✅ sem timeout; a combinação com deadline é o próximo passo);
- `cancelled()` real no JS — hoje sempre `0` (limitação conhecida: sem thread-local para contexto "task atual" em async functions intercaladas no GraalJS embutido, ver seção 4).

Concorrência é uma capacidade da **linguagem/stdlib**, não uma coleção de
APIs da plataforma.

O programador expressa **intenção**:

```text
tarefas concorrentes
```

e não:

```text
Thread / ExecutorService / CompletableFuture / pthread / epoll / libuv
```

A decisão de como executar (virtual thread, platform thread, event loop,
worker) pertence ao **target/runtime**.

---

## 2. Semântica (o que a linguagem promete)

### 2.1 Tarefas

Uma tarefa é uma unidade de execução concorrente com:

- início explícito (função ou bloco);
- terminação implícita (fim do corpo);
- resultado opcional (valor de retorno observável);
- falha propagável (exceção da tarefa é observável).

Conceitualmente:

```text
task
```

### 2.2 Isolamento

O modelo Kof proposto é **isolamento por valor** (como o modelo de atores,
sem a cerimônia):

- cada tarefa possui seu próprio contexto de execução;
- comunicação ocorre por **valores trocados explicitamente** (parâmetros,
  retornos, filas);
- **sem memória compartilhada mutável** como modelo primário (elimina data
  races por construção);
- o runtime pode escalar tarefas livremente entre OS threads.

Isso NÃO é decidido ainda — é a direção proposta. Alternativa considerada:
memória compartilhada com sincronização explícita (rejeitada como modelo
primário por reproduzir a complexidade de threads).

### 2.3 Comunicação

Troca de valores entre tarefas através de:

- parâmetros e retornos (estilo "join");
- filas (produtor/consumidor) — abstração planejada na stdlib
  (`kof.concurrent.Queue`);
- callbacks estruturados (não como modelo primário).

### 2.4 Sincronização

- Por construção (isolamento);
- por valores (retorno/fila);
- nunca por locks como API primária.

---

## 3. Sintaxe (escolhida: `spawn` — 0.2.6-beta)

**Implementada nos 3 targets, concorrência real em todos:**

```kof
spawn task()
spawn { println("background") }
val handle = spawn tarefa()   // Handle<T> tipado — 0.1.0
val result = await handle      // unboxing + exceção limpa — 0.1.0
```

`spawn`/`await` funcionam em JVM (virtual threads), JS (`async`/`await`/`Promise` do GraalJS, 03/09) e Native (OS threads via `pthread_create`, 31/08).

**Restrição só-JS (`CONC003-JS-01`):** uma lambda comum passada para
`list.map`/`filter`/`reduce` (ou handler de UI/timer/mq) não pode usar
`await`/`spawn expr`/`channel.receive()` — só o corpo de um `spawn { ... }`
pode. É erro de compilação, não um comportamento silenciosamente errado:
o motivo é que `Array.prototype.map/filter/reduce` do JS é síncrono e não
sabe lidar com um callback que devolve `Promise` — sem essa restrição, o
resultado viraria `Array<Promise<T>>` mascarado de `List<T>`, corrompendo
dado sem erro nenhum. JVM/Native não têm essa restrição.

Rejeitadas: `async { }` (confunde com async/await).

Decisões pendentes:

- como expressar filas/pub-sub (`kof.concurrent.Queue` planned);
- modelo de erro (exceção já propaga via `await` com unwrap `ExecutionException` — ver `KofAwaitTest`).

**Não implementar sintaxe antes da semântica acima ser validada.** — validada 0.1.0.

---

## 4. Mapeamento por Target (0.2.6-beta)

A mesma semântica Kof utiliza implementações diferentes:

| Target | Implementação | Status |
|--------|---------------|--------|
| JVM 21+ | Virtual Threads (scheduler da JVM) | ✅ `await`/`Handle<T>` + `kof.mq` |
| Native x86_64 | OS threads: `pthread_create` + trampoline + `await`/`pthread_join` + `done`/`poll`/`cancel`/`cancelled`/`selectAny` + allocator thread-safe (futex) | ✅ 31/08 (`CONC001` fechado) |
| Native riscv64/aarch64 | OS threads futuro (target ainda placeholder) | `CONC001` (placeholder) |
| JS (GraalJS) | `async`/`await`/`Promise` nativos — coloração async por fixpoint no compilador (`JsBackend.computeAsyncColoring`), handle `{done,value,error,promise}`, canais com fila de resolvers pendentes, `KofJsRunner` drena a fila de microtasks (`kofActiveTasks`) | ✅ 03/09 (`CONC003` fechado) |
| KofScript | JVM via KofScriptGlobals | ✅ |

O código Kof não muda entre targets; no x86_64 não há mais gap de
`spawn`/`await` (`CONC001` fechado) nem no JS (`CONC003` fechado) — o
restante do `CONC001` se aplica só aos targets riscv64/aarch64 (placeholder).
No JS especificamente: só lambdas criadas direto num site de `spawn`
("task-lambdas") podem virar `async function`; ver restrição
`CONC003-JS-01` na seção 3. `cancelled()` no JS sempre retorna `0`
(limitação conhecida, sem thread-local equivalente para "task atual" em
async functions intercaladas no GraalJS embutido).

---

## 5. I/O Concorrente

Código como:

```kof
loadUser(id: UUID): User {
    return database.users.find(id)
}
```

não exige que o usuário saiba se internamente foi usado:

```text
blocking I/O
non-blocking I/O
virtual thread
epoll
event loop
worker
```

Essa decisão pertence ao target/runtime.

---

## 6. Dependências (0.2.6-beta)

- ✅ Lambdas com captura via `BoxN` — implementado (necessário para `spawn { ... }` idiomático);
- filas na stdlib (`kof.concurrent.Queue` — planned, `kof.mq` já fornece pub/sub);
- modelo de exceção por tarefa — ✅ unwrap `ExecutionException` no `await` (JVM);
- ✅ OS threads no Native — `pthread_create` + trampoline + futex (31/08, `CONC001` fechado no x86_64); scheduler `scheduler.every/at` no Native segue `SCHED001`.

## 7. Fases de Implementação

1. Semântica validada (este documento);
2. Primitive `spawn`/`async` no JVM (virtual threads quando disponíveis) — ✅;
3. Scheduler Native — ✅ (pthread, `CONC001` fechado);
4. Filas/pub-sub na stdlib — ✅ (`channel<T>()`, `kof.mq`);
5. KofJS — ✅ 03/09 (`CONC003` fechado, ver seção 4).

## 8. Não-Fazer

- Expor `Thread`, `ExecutorService`, `pthread`, `epoll` como API da linguagem;
- locks como modelo primário;
- `CompletableFuture`-style APIs vazando para o usuário.