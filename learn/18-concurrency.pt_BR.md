[English](18-concurrency.md) | [Português](18-concurrency.pt_BR.md)

# 18 — Concorrência

> **Status: implementado (JVM / Native / JS) — 0.5.0-beta — `spawn`/`await` nos 3 targets**
>
> Kof não expõe `Thread`, `Runnable` nem `CompletableFuture`: a intenção é
> `spawn` (rode em paralelo) e `await` (espere o resultado). JVM usa virtual
> threads; Native roda em pthread (CONC001 fechado em 31/08); JS roda sobre
> `async`/`await`/`Promise` reais do GraalJS (CONC003 fechado em 03/09,
> `spawn`/`await` deferem de verdade via microtask). Os gaps
> restantes são documentados, nunca silenciosos. Chain:
> `intention->Kof->frontend->IR->backend->runtime`.

## spawn — dispare e esqueça

```kf
baixar(String url) {
    // trabalho lento...
}

main() {
    spawn baixar("https://example.com")   // roda em paralelo
    println("seguindo o fluxo principal")
}
```

O corpo pode ser qualquer expressão — o compilador embrulha numa tarefa
sintética:

```kf
spawn {
    var i = 0
    while (i < 100) {
        processar(i)
        i++
    }
}
```

## spawn + await — resultado tipado

`spawn <expressão>` devolve um handle tipado `Handle<T>`; `await` bloqueia
a virtual thread chamadora até o valor chegar:

```kf
Int somar(a: Int, b: Int) {
    return a + b
}

main() {
    val r = spawn somar(2, 3)     // Handle<Int>
    // ...trabalho enquanto a soma acontece...
    val total = await r           // Int — unboxing automático
    println(total)                // 5
}
```

Primitivos (`Int`, `Bool`) e referências funcionam igualmente:

```kf
String buscar() { return "dados" }

main() {
    val r = spawn buscar()
    println(await r)              // "dados"
}
```

## poll / done — sem bloquear

```kf
val r = spawn trabalho()
if (done(r)) {
    println("pronto: " + poll(r))
}
```

- `poll(r)` devolve o valor se pronto; **default do tipo** (0/false) para
  primitivos não-prontos, `null` para referências. Use `done()` para
  distinguir "não pronto" de um valor default.
- `done(r)` → `Bool`.
- `poll`/`done` funcionam em JVM, JS e Native x86_64 (no JS a execução é
  sequencial, então `poll` sempre tem o valor e `done` é `true`); em
  riscv64/aarch64 também funcionam desde 15/09 (CONC001 fechado — ver a
  tabela de gaps abaixo).

## Exceções atravessam await

A exceção lançada dentro da tarefa chega **com a mensagem original** no
ponto do await — o runtime desembrulha o wrapper:

```kf
Int quebra() { throw "boom" }

main() {
    val r = spawn quebra()
    try {
        await r
    } catch (String e) {
        println(e)   // "boom"
    }
}
```

## Cancelamento cooperativo

```kf
Int trabalho() {
    var i = 0
    while (i < 10000 && !cancelled()) {
        time.sleep(1)
        i++
    }
    return i
}

main() {
    val r = spawn trabalho()
    time.sleep(30)
    assert(cancel(r))       // marca a tarefa
    await r                 // a tarefa sai do loop cedo
}
```

- `cancel(r)` marca o handle; **a tarefa decide quando sair** consultando
  `cancelled()` dentro do próprio corpo.
- `cancelled()` fora de uma tarefa devolve `false`.
- No JS é no-op marcado (`cancel` devolve `0`, `cancelled` devolve `false`) —
  execução é sequencial.

## selectAny — primeiro que chegar

```kf
val a = spawn lenta()      // 300ms
val b = spawn rapida()     // imediata
println(selectAny(a, b))   // valor da rapida
```

Bloqueia até **qualquer** handle completar e devolve o valor dele. No JS é
`Promise.race` sobre os handles (`js/JsRuntimeUiLayout.java:304`); no Native
x86_64 funciona por polling de 1 ms sobre os handles; em riscv64/aarch64
também funciona desde 15/09 (CONC001 fechado).

## Canais — FIFO tipado entre workers

```kf
val c = channel<Int>()     // canal tipado (FIFO, bloqueante)
c.send(5)                  // bloqueia até um receptor pegar
var x = c.receive()        // bloqueia até um produtor entregar
println(x)                 // 5
```

- `channel<T>()` cria um canal tipado; um `channel()` nu boxeia pelo tipo do
  argumento (§374).
- `send(v)` devolve `void`; `receive()` devolve `T` — ambos bloqueiam (JVM:
  put/take de `LinkedBlockingQueue`; Native: fila de futex do runtime
  `kof_channel_*`; JS: fila sequencial com a mesma ordem observável).
- Os membros válidos são exatamente `send`/`receive` — qualquer outro é SEM025.
- Canais funcionam em TODOS os alvos, incluindo riscv64/aarch64 (§423 fechado
  em 23/09), e o fix §485 (24/09) garante que uma fila drenada nunca devolve
  NULL velho (SIGSEGV determinístico antes; repro single-threaded agora limpo).

```kf
// produtor/consumidor
spawn produtor(c)
var v = c.receive()
```

## lock — exclusão mútua sobre um objeto compartilhado

Quando várias tasks atualizam o mesmo objeto, `lock (alvo) { ... }` deixa só
uma delas rodar o bloco por vez para aquele alvo:

```kf
class Counter {
    Int n = 0

    add() {
        lock (this) {
            n = n + 1          // ler-modificar-escrever, nunca intercalado
        }
    }
}
```

- O alvo é avaliado **uma vez** e precisa ser um objeto não nulo (instância de
  classe, record, coleção). Um primitivo, uma `String` ou um valor nulável é
  recusado em tempo de compilação (`CONC004`): trave o objeto dono do estado
  compartilhado, normalmente `this`.
- O lock é liberado em **toda** saída do bloco: fim normal, `return`,
  `break`/`continue` e um `throw`.
- É **reentrante**: a task que segura o lock pode entrar nele de novo
  (`lock (c) { lock (c) { ... } }`, ou um método travado chamando outro).
- Sair de um `lock` em `x` acontece-antes da próxima entrada num `lock` em `x`
  (modelo de memória §2, regra 7): o que foi escrito dentro é visível para o
  próximo dono.
- Por alvo: JVM e Script usam um lock reentrante por objeto (por identidade),
  o Native um lock futex reentrante (x86-64, riscv64, aarch64), JS e Wasm rodam
  uma thread só, então não há o que excluir. No JS uma função que usa `lock`
  **e** suspende (`await`, `sleep`, `channel.receive()` ou uma chamada a uma
  função que suspende) é recusada com `CONC005`: outra task rodaria com o lock
  segurado. Tire a chamada que suspende dessa função.
- Prefira um `Channel` quando as tasks passam dados entre si; o `lock` é para
  uma seção crítica pequena sobre estado compartilhado.

## Semântica

- JVM: cada `spawn` roda numa **virtual thread** (JDK 21+) — barato para
  milhares de tarefas. Native: a tarefa roda numa **pthread** criada pelo
  trampoline do runtime. JS: o corpo roda sequencialmente (sem paralelo).
- O programa espera as tarefas antes de sair (join implícito no runtime).
- Exceção dentro da tarefa é re-lançada no ponto do `await`.
- `await` num handle duas vezes devolve o mesmo valor (o resultado é memoizado pelo runtime).

## Gaps por target (nunca silenciosos)

Cada `✅` cita o teste que o prova; cada `❌` significa que o símbolo do
runtime **não é emitido** para aquele alvo. O `ConcurrencyGapsDocTest`
trava esta tabela contra o código — ver nota ao final da seção.

| Construto | JVM | Native x86_64 | Native riscv64/aarch64 | JS |
|-----------|-----|----------------|-------------------------|----|
| `spawn stmt` | ✅ `SpawnE2ETest.spawnRunsConcurrentlyAndJoins` | ✅ pthread — `SpawnE2ETest.nativeSpawnStmtRuns` | ✅ `clone` 220 — `NativeRiscv64E2ETest.riscv64SpawnFireAndForgetJoins` | ✅ microtask — `SpawnE2ETest.jsSpawnStmtRunsSequentially` |
| `val r = spawn expr` | ✅ `KofAwaitTest.awaitJvm` | ✅ pthread — `SpawnE2ETest.nativeSpawnExprAwait` | ✅ `clone` 220 — `NativeRiscv64E2ETest.riscv64SpawnAwait` | ✅ microtask — `KofAwaitTest.awaitJs` |
| `await r` | ✅ `KofAwaitTest.awaitJvm` | ✅ `pthread_join` — `KofAwaitTest.awaitNativeRuns` | ✅ futex sobre `done` — `NativeAarch64E2ETest.aarch64SpawnAwait` | ✅ `KofAwaitTest.awaitJs` |
| `poll` / `done` | ✅ `KofAwaitTest.pollDoneJvm` | ✅ `KofConcurrency2Test.pollDoneNative` | ✅ `kof_poll`/`kof_done` (CONC001 fechado 15/09) — `KofConcurrency2Test.crossNativeConcurrencyHelpersRun` | ✅ `KofAwaitTest.pollDoneJs` |
| `cancel` / `cancelled` | ✅ `KofConcurrency2Test.cancelCooperativeJvm` | ✅ `KofConcurrency2Test.cancelCooperativeNative` (bug 101 → §117 FIXED 13/09, tabela por TID real `3734f2aa`) | ✅ `kof_cancel` TID real (CONC001 fechado 15/09) — `KofConcurrency2Test.crossNativeCancelDuringRunningWorker` | ✅ no-op (`cancelled()` = `0`) — `KofConcurrency2Test.cancelJsSequential` |
| `selectAny` | ✅ `KofConcurrency2Test.selectAnyJvm` | ✅ polling 1 ms — `KofConcurrency2Test.selectAnyNative` | ✅ `kof_select_any` (CONC001 fechado 15/09) — `KofConcurrency2Test.crossNativeConcurrencyHelpersRun` | ✅ `Promise.race` — `KofConcurrency2Test.selectAnyJs` |
| `awaitTimeout` | ✅ `KofConcurrency2Test.awaitTimeoutJvm` | ✅ polling 1 ms — `KofConcurrency2Test.awaitTimeoutNative` | ✅ `kof_await_timeout` (CONC001 fechado 15/09) — `KofConcurrency2Test.crossNativeConcurrencyHelpersRun` | ✅ `KofConcurrency2Test.awaitTimeoutJs` |
| `lock (x) { }` | ✅ `ReentrantLock` por objeto — `LockBlockE2ETest.lockExcludesAndReleasesOnEveryExitOnJvmScriptAndJs` | ✅ tabela de locks futex reentrantes — `LockBlockE2ETest.lockExcludesOnNativeX86` | ✅ `kof_lock_enter`/`kof_lock_exit` (G240, 06/11) — `LockBlockE2ETest.lockExcludesOnNativeCross` | ✅ no-op (uma thread; CONC005 recusa lock + suspensão) — `LockBlockE2ETest.lockExcludesAndReleasesOnEveryExitOnJvmScriptAndJs` |

`spawn`/`await` fecharam o `CONC001` no Native em 31/08 (pthread_create +
trampoline + `pthread_join` + allocator thread-safe via futex), e os
construtos auxiliares (`poll`/`done`/`cancel`/`cancelled`/`selectAny`/
`awaitTimeout`) **também funcionam no x86_64** desde então — runtime em
`runtime/RuntimeConcurrency.java:304`, prova em
`KofConcurrency2Test.selectAnyNative`.

No x86_64, `cancel`/`cancelled` usam a tabela por **TID real + sonda
linear** — a colisão `TID % 256` antiga (bug 101, renumerado §117) foi
CORRIGIDA em 13/09 (`3734f2aa`).

Em **riscv64/aarch64** os auxiliares **existem desde 15/09**: o `CONC001`
foi fechado portando os símbolos (`e8364c97`) — a fatia
`nat/NativeRiscvAsmRtB48.java` emite `kof_poll`, `kof_done`, `kof_cancel`,
`kof_select_any` e `kof_await_timeout`, com cancel por TID real
(`gettid(178)` gravado pelo kernel no ctid do `clone`, sem colisão).
Nota histórica: entre 11/09 e 15/09 a ausência era um gate R6 honesto —
`ExpressionStaticCallLowerer` emitia **`CONC001` em compile-time** (issue
#91; antes não havia gate e o erro só aparecia no **link**, como símbolo
indefinido — mesmo padrão do bug 59); o gate foi **removido** com o port.
Prova: `KofConcurrency2Test.crossNativeConcurrencyHelpersRun` e
`crossNativeCancelDuringRunningWorker` (qemu, nas duas arches — pulam sem o
toolchain cruzado). O que segue aberto no cross é o supervisor OTP
(`OTP001` — `clone` cru sem TLS para a cadeia de handlers do §129), não os
helpers.

> **Esta tabela é travada por teste.** `ConcurrencyGapsDocTest` (em
> `kof-compiler/src/test/java/dev/kof/compiler/`) quebra o build se uma
> célula de suporte não citar um método de teste existente, ou se uma
> célula marcada `❌` referir-se a um símbolo que os emissores cross de
> fato emitem. É o mesmo padrão do `ConformanceMatrixDocTest`, que trava
> `docs/bugs-and-gaps/conformance-matrix.md`. Motivo: esta tabela passou
> meses dizendo que os auxiliares reportavam `CONC001` no Native — porque
> nada a comparava com o código.
>
> Limite do guard, explícito: ele prova que a doc aponta para testes que
> **existem**, não que esses testes **passam**. A prova de comportamento
> continua sendo a suíte.
>
> **A coluna riscv64/aarch64 não tem o mesmo peso de prova das outras.**
> `NativeRiscv64E2ETest` e `NativeAarch64E2ETest` são inteiramente
> condicionados por `Assumptions.assumeTrue(...)` ao toolchain cruzado
> (`riscv64-linux-gnu-as`, `riscv64-linux-gnu-ld`, `qemu-riscv64`) — sem
> ele, as provas cross dessas duas classes (2×42 = 84 na medição de
> 16/09; cresce a cada commit) **pulam em silêncio**. Execução de
> 11/09 numa máquina sem o toolchain: JVM, Native x86_64 e JS passaram
> (`SpawnE2ETest` 10/10, `KofAwaitTest` 8/8, `KofConcurrency2Test` 29/29
> com 1 skip de qemu, `ConcurrencyGapsDocTest` 3/3), e riscv/aarch
> pularam 33/33 cada. Portanto o `✅` dessa coluna significa *"provado
> onde o toolchain existe"*, não *"provado"*. **Essa lacuna está fechada
> na CI:** o job `cross-native` (`ci.yml`) instala as binutils cruzadas, a
> libc e o `qemu-user-static` e roda as duas suítes sob qemu — um job verde
> ali é prova real de paridade cross, não um skip maquiado. Numa máquina
> local sem o toolchain, o guard `assumeTrue` continua valendo (skip
> honesto, 42+42).

No JS o modelo é
single-threaded, mas concorrente de verdade sobre o event-loop: `spawn`
enfileira a task como microtask (não roda na hora) e `await` de fato
suspende até ela resolver — o programa espera todas as tasks spawnadas
antes de sair, igual JVM/Native (`CONC003` fechado).

## Target separation (0.2.0)

`Target` enum separa `NATIVE` (x86-64) de `NATIVE_RISCV64` e `NATIVE_AARCH64`.
`spawn`/`await` funcionam no Native (pthread) — a separação vale para
codegen/linker (`as`/`ld` por arch), não muda a semântica de concorrência.
Native usa free-list `kof_free_head` para reuso de `mmap`.

## Próximo passo

**[19 — Pacotes e Módulos](19-packages-and-modules.md)**
