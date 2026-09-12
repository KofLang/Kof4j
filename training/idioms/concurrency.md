# Idioms — Concurrency

**Status:** available (3 targets) · **Introduced:** 0.0.5-alpha · **Updated:** 0.3.22-beta (Sep 2026) (31/08: CONC001 fechado) · **JS:** sequencial (CONC003 parcial)

## What it is

`spawn` executa uma tarefa concorrentemente sem expor threads:

```kof
void processar(Int id) {
    println("processando " + id)
}
main() {
    spawn processar(1)
    spawn processar(2)
    spawn {
        println("background")
    }
    println("fim")
}

// Com resultado (0.3.22-beta)
main() {
    val r = spawn trabalho()   // Handle<T> tipado
    var v = await r            // bloqueia; T com unboxing de primitivos
    println(v)
}

// Lambda literal com return + Handle (0.3.22-beta)
main() {
    var n = 21
    var h = spawn { return n * 2 }   // Handle<Int>
    println(await h)                 // 42
}
```

## Semântica real (verificada — 0.3.22-beta)

- a tarefa roda em paralelo: JVM virtual threads; **Native `pthread_create` + trampoline + `pthread_join` (CONC001 fechado 31/08)**; JS sequencial (statement e expressão cobrem; async real = CONC003 parcial);
- o programa **espera as tarefas antes de sair** (join implícito: `kof_spawn_join_all` no fim do main no Native);
- `val r = spawn f()` devolve `Handle<T>` tipado; `await r` com unboxing;
- `var h = spawn { return expr }` (lambda literal com `return` + Handle) funciona no JVM/JS/interpretador — **gap: Native x86_64 → SIGSEGV (bug 46, known-bugs.md)**; usar `spawn fn(arg)` (função nomeada) como workaround no Native até o fix;
- exceção na tarefa não derruba o programa;
- **KofScript** `let` top-level também suporta spawn/await via KofScriptGlobals.

## When to use

- trabalho independente que pode rodar em paralelo (processamento de filas,
  I/O, notificações);
- tarefas de background;
- quando o resultado é necessário — use `val r = spawn f(); await r`.

## When not to use

- quando a ordem importa e não há sincronização.
- JS para paralelismo real de CPU (execução sequencial; CONC003 parcial).

## BAD — expor plataforma

```kof
// NÃO EXISTE — não há Thread/Executor na linguagem
var t = new Thread(() -> work())
t.start()
```

## GOOD

```kof
spawn work()
val r = spawn compute()
var v = await r
```

## GOOD — kof.time interval como scheduler

```kof
// periódicas: interval/cancel apenas JVM (TIME001 no Native/JS)
var id = time.interval(1000, () -> println("tick"))
```

Para `every`/`at` programados, `kof.scheduler` existe em JVM/JS
(`Native SCHED001`): `scheduler.every(100) { ... }`, `scheduler.at("0 3 * * *") { ... }`, `scheduler.cancel(id)`.

## WHY

`spawn` expressa intenção. Thread/Runnable/Executor são mecanismos da
plataforma — a decisão de como executar pertence ao runtime.

## Limitações honestas (0.3.22-beta)

- ~~Native: CONC001~~ — ✅ fechado 31/08 (pthread_create + trampoline + await/pthread_join + allocator thread-safe futex + join implícito);
- JS: execução sequencial — `spawn`/`await` cobrem statement e expressão; async real de event-loop = CONC003 parcial;
- filas produtor/consumidor: `kof.mq` — 3 targets (Native 01/09, MQ001 fechado; pub/sub + `mq.queue()`/`push`/`pop`);
- lambdas com captura funcionam em spawn (BoxN).

## GOOD — kof.supervisor: reinício supervisionado (OTP, issue #83)

```kof
// Falha de worker NÃO mata o sistema: o supervisor observa, reinicia com
// uma fábrica NOVA, respeita o limite, e escala quando estoura.
import kof.supervisor

class Conecta implements KofWorkerFactory {
    KofWorker novo() { return WorkerConexao() }   // objeto novo por reinício
}
class WorkerConexao implements KofWorker {
    Object run() {
        // lança (exceção é String) → o supervisor captura a falha
        throw "conexao caiu"
    }
}
main() {
    var s = supervisor("net")
        .child("conn", Conecta(), "permanent")   // permanent: cai → reinicia
        .restartLimit(5)
    s.start()
    // ... s.stop(2000) para encerrar controlado; s.stats() observa ...
}
```

A fábrica (`KofWorkerFactory.novo()`) retorna um `KofWorker` **novo** a cada
reinicio — não se reinicia o objeto que falhou, re-fabrica-se (isolamento de
estado). As três politicas: `permanent` (cai → sempre reinicia), `transient`
(termina normal → para; só reinicia se falhar), `temporary` (nunca reinicia —
conta como descartado). `escalate(cb)` chama `disparou(id, motivo, reinicios)`
no limite (sem `escalate` o supervisor **para de reiniciar e avisa** — nunca
silencioso).

Paridade honesta: **JVM + Script** (interpretador) entregam o núcleo. NATIVE =
`OTP001` (o `throw` em task no backend nativo atual cai no handler chain global
— §129), JS = `OTP002` (event-loop single-thread não agenda task-de-task —
§132). Nos dois o `import kof.supervisor` falha no compile-time com
diagnóstico claro, nunca um binário que trava.

## WHY

Supervisão é **intenção**, não mecanismo: o usuário declara o *quê* vigiar
(fábrica + política + limite), não *como* reaplicar threads. A plataforma
(`spawn`/`await`/`try-catch`) já existe; o supervisor é código Kof por cima.

## GOOD — fetch assíncrono: `var h = spawn http.get(url); await h`

```kof
// ❌ BAD — "paralelo" com threads/futures de outra linguagem, ou síncrono no JS
val a = http.get(urlA)            // bloqueia a thread inteira até responder
val b = http.get(urlB)            // sequencial: soma as latências

// ✅ GOOD — a linguagem já tem Handle: spawn dá concorrência, await pega o valor
var ha = spawn http.get(urlA)
var hb = spawn http.get(urlB)
val a = await ha                  // dispara antes de esperar; latência = max(a,b)
val b = await hb

// ✅ GOOD — "qualquer um primeiro"
val first = await selectAny(spawn http.get(a), spawn http.get(b))
```

Em JVM/Script/Native a thread do worker faz o I/O; no **Node/browser** o
`http.*` é `fetch` de verdade — o `Handle` carrega a Promise, e o `await`
resolve o corpo (`spawn`+`await` é o ÚNICO caminho que transporta em JS puro;
chamada síncrona lá devolve o Promise cru — §133). Nunca `Thread`/`Future`/
`async`/`await` de outra linguagem: `spawn`/`await` cobrem os três.

## Anti-patterns relacionados

- `fake-idioms.md` — `async`/`await`/Thread não existem (use `spawn`/`await`)
