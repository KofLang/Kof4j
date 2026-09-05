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
- `poll`/`done` funcionam em JVM e JS (no JS a execução é sequencial, então
  `poll` sempre tem o valor e `done` é `true`); no Native ainda reportam
  `CONC001` (ver a tabela de gaps abaixo).

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

Bloqueia até **qualquer** handle completar e devolve o valor dele. No JS
(sequencial) devolve o primeiro argumento; no Native reporta `CONC001`.

## Semântica

- JVM: cada `spawn` roda numa **virtual thread** (JDK 21+) — barato para
  milhares de tarefas. Native: a tarefa roda numa **pthread** criada pelo
  trampoline do runtime. JS: o corpo roda sequencialmente (sem paralelo).
- O programa espera as tarefas antes de sair (join implícito no runtime).
- Exceção dentro da tarefa é re-lançada no ponto do `await`.
- `await` num handle duas vezes devolve o mesmo valor (o resultado é memoizado pelo runtime).

## Gaps por target (nunca silenciosos)

| Construto | JVM | Native | JS |
|-----------|-----|--------|----|
| `spawn stmt` | ✅ | ✅ (pthread) | ✅ (sequencial) |
| `val r = spawn expr` | ✅ | ✅ (pthread) | ✅ (sequencial) |
| `await r` | ✅ | ✅ | ✅ |
| `poll` / `done` | ✅ | `CONC001` | ✅ |
| `cancel` / `cancelled` | ✅ | `CONC001` | ✅ (no-op) |
| `selectAny` | ✅ | `CONC001` | ✅ |

`spawn`/`await` fecharam o `CONC001` no Native em 31/08 (pthread_create +
trampoline + `pthread_join` + allocator thread-safe via futex); os
construtos auxiliares (`poll`/`done`/`cancel`/`cancelled`/`selectAny`)
ainda reportam `CONC001` em compile-time no Native. No JS o modelo é
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
