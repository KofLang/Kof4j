[English](concurrency.md) | [Português](concurrency.pt_BR.md)

# Idioms — Concurrency

**Status:** available (3 targets) · **Introduced:** 0.0.5-alpha · **Updated:**  0.5.0-beta (Sep 2026) (31/08: CONC001 fechado; 15/09: helpers cross do CONC001) · **JS:** event-loop (CONC003 fechado 03/09)

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

// Com resultado (0.5.0-beta)
main() {
    val r = spawn trabalho()   // Handle<T> tipado
    var v = await r            // bloqueia; T com unboxing de primitivos
    println(v)
}

// Lambda literal com return + Handle (0.5.0-beta)
main() {
    var n = 21
    var h = spawn { return n * 2 }   // Handle<Int>
    println(await h)                 // 42
}
```

## Semântica real (verificada — 0.5.0-beta)

- a tarefa roda em paralelo: JVM virtual threads; **Native `pthread_create` + trampoline + `pthread_join` (CONC001 fechado 31/08)**; JS event-loop (statement e expressão cobrem; async real = CONC003 fechado 03/09);
- o programa **espera as tarefas antes de sair** (join implícito: `kof_spawn_join_all` no fim do main no Native);
- `val r = spawn f()` devolve `Handle<T>` tipado; `await r` com unboxing;
- `var h = spawn { return expr }` (lambda literal com `return` + Handle) funciona no JVM/JS/interpretador — **gap: Native x86_64 → SIGSEGV (bug 46, known-bugs.md)**; usar `spawn fn(arg)` (função nomeada) como workaround no Native até o fix;
- **o pai não pode escrever um binding capturado antes do join (`MEM021`, `D-MEM021-SCALAR` + #660):** se o worker ESCREVE um local capturado (`spawn { n = n + 1 }`) e o pai reatribui/incrementa o MESMO binding sem `await`/`join_all` entre, o compilador emite `MEM021` (ERROR de compilação) — a escrita do worker força o box de representação, então pai e worker compartilham o slot (corrida silenciosa `202`/`101` antes do fix). Captura só-leitura nunca corre (por valor, sem box). `await h` (ou qualquer join) antes da escrita do pai é o fix;
- exceção na tarefa não derruba o programa;
- **KofScript** `var`/`val` no topo também suporta spawn/await via KofScriptGlobals.

## When to use

- trabalho independente que pode rodar em paralelo (processamento de filas,
  I/O, notificações);
- tarefas de background;
- quando o resultado é necessário — use `val r = spawn f(); await r`.

## When not to use

- quando a ordem importa e não há sincronização.
- JS para paralelismo real de CPU (event-loop single-thread — concorrência é
  real, paralelismo não; CONC003 fechado 03/09).

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
// periódicas: interval/cancel — 3 targets (TIME001 fechado: Native 01/09 + cross 05/09, JS 02/09)
var id = time.interval(1000, () -> println("tick"))
```

Para `every` programado, `kof.scheduler` existe nos 3 targets
(`SCHED001` fechado no Native 31/08): `scheduler.every(100) { ... }`, `scheduler.cancel(id)`.
⚠️ `scheduler.at("0 3 * * *", fn)` é um **stub de 60s em todos os targets** (`CRON001` — a
expressão cron é ignorada, o job só roda uma vez por minuto); até o CRON001 landar, calcule
os milissegundos até o próximo disparo e rode `spawn { time.sleep(ms); job() }` (re-arme
dentro do job para agenda recorrente).

## WHY

`spawn` expressa intenção. Thread/Runnable/Executor são mecanismos da
plataforma — a decisão de como executar pertence ao runtime.

## Limitações honestas (0.5.0-beta)

- ~~Native: CONC001~~ — ✅ fechado 31/08 (pthread_create + trampoline + await/pthread_join + allocator thread-safe futex + join implícito);
- JS: ~~execução sequencial~~ → async real de event-loop — `spawn`/`await`
  cobrem statement e expressão (CONC003 fechado 03/09); limitações conhecidas:
  `cancelled()` sempre `0` (sem thread-local da task atual) e só task-lambdas
  viram `async function` (CONC003-JS-01);
- `time.sleep` no JS é COOPERATIVO (§132): ele cede ao pump. Um golden determinístico
  deve ESPERAR timers com **UM `time.sleep(ms)` longo** (o padrão do teste de schedule
  em `WorkflowE2ETest`), nunca com polling `time.sleep(10)` dentro de `while (a && b)` —
  o polling curto compete com o pump e pode morrer em silêncio (rc=0, sem output;
  medido 20/09 na construção do makealive 3.3).
- filas produtor/consumidor: `kof.mq` — 3 targets (Native 01/09, MQ001 fechado; pub/sub + `mq.queue()`/`push`/`pop`);
- self-cancel (`var id = time.interval(ms, () -> { … time.cancel(id) })`) — ler o handle dentro do
  próprio inicializador funciona em JVM/JS/Script desde 16/09 (§253 face A); **o Native rejeita em
  compile-time com SEM092** até a face B landar (ler o handle capturado dá SIGSEGV no x86 — gap
  honesto, nunca silencioso);
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

Paridade honesta: **JVM + Script + Native x86** entregam o núcleo (Native x86 ✅ 15/09 —
§129 CORRIGIDO, DECISIONS §2 opção B: cadeia de handler TLS por thread, handler por
worker). NATIVE riscv/aarch = `OTP001` (o `clone` cru não tem TLS para a cadeia de
handler — o conserto do x86 não porta direto), JS = `OTP002` (event-loop single-thread
não agenda task-de-task — §132). Nos dois o `import kof.supervisor` falha no
compile-time com
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
