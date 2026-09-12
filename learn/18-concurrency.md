# 18 — Concorrência

> **Status: implementado (JVM / Native / JS) — 0.2.6-beta — `spawn`/`await` nos 3 targets**
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
  riscv64/aarch64 não existem (ver a tabela de gaps abaixo).

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
não existe.

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
| `poll` / `done` | ✅ `KofAwaitTest.pollDoneJvm` | ✅ `KofConcurrency2Test.pollDoneNative` | ❌ `kof_poll`/`kof_done` não emitidos | ✅ `KofAwaitTest.pollDoneJs` |
| `cancel` / `cancelled` | ✅ `KofConcurrency2Test.cancelCooperativeJvm` | ⚠️ `KofConcurrency2Test.cancelCooperativeNative` — mas ver **bug 101** | ❌ `kof_cancel` não emitido | ✅ no-op (`cancelled()` = `0`) — `KofConcurrency2Test.cancelJsSequential` |
| `selectAny` | ✅ `KofConcurrency2Test.selectAnyJvm` | ✅ polling 1 ms — `KofConcurrency2Test.selectAnyNative` | ❌ `kof_select_any` não emitido | ✅ `Promise.race` — `KofConcurrency2Test.selectAnyJs` |
| `awaitTimeout` | ✅ `KofConcurrency2Test.awaitTimeoutJvm` | ✅ polling 1 ms — `KofConcurrency2Test.awaitTimeoutNative` | ❌ `kof_await_timeout` não emitido | ✅ `KofConcurrency2Test.awaitTimeoutJs` |

`spawn`/`await` fecharam o `CONC001` no Native em 31/08 (pthread_create +
trampoline + `pthread_join` + allocator thread-safe via futex), e os
construtos auxiliares (`poll`/`done`/`cancel`/`cancelled`/`selectAny`/
`awaitTimeout`) **também funcionam no x86_64** desde então — runtime em
`runtime/RuntimeConcurrency.java:304`, prova em
`KofConcurrency2Test.selectAnyNative`.

No x86_64, `cancel`/`cancelled` funcionam mas carregam o **bug 101** (flag
por `TID % 256` — dois workers podem herdar o cancel um do outro).

Em **riscv64/aarch64** esses auxiliares não existem:
`nat/NativeRiscvSpawn.java` emite apenas `kof_spawn_result`, `kof_spawn`,
`kof_await` e `kof_spawn_join_all`. Desde 11/09 a ausência é um gap R6
honesto: `ExpressionStaticCallLowerer` detecta `poll`/`done`/`cancel`/
`cancelled`/`selectAny`/`awaitTimeout` nesses alvos e emite **`CONC001` em
compile-time** (antes caía no `sanitizeName` genérico de
`NativeRiscvCrossOps.resolveCalleeNameRiscv` e o erro só aparecia no
**link**, como símbolo indefinido — mesmo padrão do bug 59). Prova:
`KofConcurrency2Test.crossMissingConcurrencyHelpersReportConc001` (issue
#91). O que falta para fechar de vez é portar os símbolos, não o diagnóstico.

> **Esta tabela é travada por teste.** `ConcurrencyGapsDocTest` (em
> `kof-compiler/src/test/java/dev/kof/compiler/`) quebra o build se uma
> célula de suporte não citar um método de teste existente, ou se uma
> célula marcada `❌` referir-se a um símbolo que os emissores cross de
> fato emitem. É o mesmo padrão do `ConformanceMatrixDocTest`, que trava
> `docs/development/conformance-matrix.md`. Motivo: esta tabela passou
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
> ele, **as 66 provas dessas duas classes pulam em silêncio**. Execução de
> 11/09 numa máquina sem o toolchain: JVM, Native x86_64 e JS passaram
> (`SpawnE2ETest` 10/10, `KofAwaitTest` 8/8, `KofConcurrency2Test` 29/29
> com 1 skip de qemu, `ConcurrencyGapsDocTest` 3/3), e riscv/aarch
> pularam 33/33 cada. Portanto o `✅` dessa coluna significa *"provado
> onde o toolchain existe"*, não *"provado"*. Instalar `qemu-user` e os
> assemblers cruzados no CI é o que fecharia essa lacuna — nos runners do
> GitHub o `sudo apt-get` funciona sem senha (`ci.yml:41` já faz isso).

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
