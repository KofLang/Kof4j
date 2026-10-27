[English](workflow.md) | [Português](workflow.pt_BR.md)

# Workflows — `kof.workflow`

**Data:** 19 de setembro de 2026
**Status:** MVP implementado (plano universal Estágio 2, linha 2.1, fatia 2.1.2) — `VERSION` 0.6.7

> **Escopo do MVP (Q2, enquete da mantenedora 19/09):** `job` / `dag` / `after` /
> `run` / `Report`. **Face 1 do 2.1.3 ENTREGUE 19/09:** `retry` (Q3 — helper aditivo
> do próprio workflow; `kof.http` NÃO é tocado, a migração dele é fatia assinada à
> parte). **Faces 2–3 do 2.1.3 ENTREGUES 19/09 (esta fatia):** `deadLetter` (Q4 — as
> DUAS faces: `Report.dead` in-memory sempre + sink durável opt-in por job) e
> `schedule` (delega a `scheduler.at` — durações D-SCHED-DURATION ou cron; Native
> recebe stub que falha ALTO em runtime c/ `CRON001`, o gate do scheduler segue
> em compile-time). **2.1.3 face 4 LANÇADA 19/09 (3a):** `checkpoint` (store sobre
> `kof.db`/`kof.orm`; Native recebe stub ALTO `ORM001` em runtime).
> **2.1.3 face 5 LANÇADA 19/09 (3b):** `runSupervised(dag, nome, maxReinicios)` —
> a DAG roda como workers supervisionados: cada job é um child `transient` de um
> `kof.supervisor` (o laço por filho É o one_for_one — só o filho que falha
> reinicia). Política de reinício = a do supervisor (plano §3 — o workflow nunca
> re-implementa); NÃO precisa de `import kof.supervisor` (o host vem com a face,
> dedupado pela marca quando o usuário também importa).

---

## 1. O que é

`kof.workflow` é uma **camada de composição sobre Kof puro**, escrita em Kof
(resource `dev/kof/workflow-host.kf`) e injetada **flat** pelo compilador quando
você faz `import kof.workflow` — o mesmo mecanismo e a mesma DD-OTP-01 opção A do
`kof.supervisor`. Como o host é um pacote virtual, a superfície é
`job(...)`/`dag(...)` pelados; **não existe prefixo `workflow.` nem identificador
`workflow`** depois do import.

O MVP executa o DAG **sequencial e determinístico** (um laço fixpoint). A camada não
cruza fronteira de runtime — sem threads, sem `await`, sem `process` — então
**não há gate de target**: o mesmo código roda byte-idêntico em JVM e JS e compila
em Native. Os gaps honestos (`PROC001`, `CRON001`, `ORM001`) aparecem apenas nos
*corpos* de job que você escreve, nunca na camada.

## 2. Superfície

```
import kof.workflow

job(String nome, () -> Bool corpo) -> KofWfJob
KofWfJob.after(KofWfJob dep) -> KofWfJob        // encadeável; guarda nulo/auto-dependência
dag(List<KofWfJob> jobs) -> KofWfDag            // guarda dag vazia / nomes duplicados / job nulo
KofWfDag.run() -> KofWfReport                   // fixpoint topológico sequencial
KofWfDag.retry(KofWfJob j, Int times, (Int) -> Int backoffMs) -> KofWfDag
KofWfDag.retryFixed(KofWfJob j, Int times) -> KofWfDag   // imediato, sem sleep
KofWfDag.deadLetter(KofWfJob j, (String, String) -> Bool sink) -> KofWfDag  // face durável opt-in
schedule(KofWfDag d, String expr) -> String               // 19/09: delega a scheduler.at, devolve o job id
checkpoint(KofWfDag d, String dbConn, String dagName) -> KofWfDag  // 19/09: store = kof.db/kof.orm (entity KofWfCk)
runSupervised(KofWfDag d, String supNome, Int maxReinicios) -> KofWfReport  // 19/09 (3b): dag como workers one_for_one (kof.supervisor)
exponential(Int baseMs, Int factor) -> (Int) -> Int       // backoff(1)=base, *factor a cada try

Campos do Report: succeeded failed skipped errors retries dead  // List<String> cada
Report.allOk() -> Bool                          // sem falhas, sem skips
Report.summary() -> String                      // "ok=... failed=... skipped=..."
```

Regras:

- O corpo retorna `Bool` (`true` = sucesso). `false` **ou um string lançado**
  falha o job; o motivo lançado entra em `errors` como `"nome: motivo"` (um
  `false` entra como `"nome: false"`).
- Um job falho ou pulado **envenena seus dependentes transitivos** — eles viram
  `skipped` e nunca rodam. Ramos independentes continuam.
- Um **ciclo é rejeitado no run time** com
  `workflow: ciclo detectado entre: ...` (mensagem acionável — nunca hang
  silencioso, invariante do §2 do plano).
- `retry(job, times, backoff)`: o corpo roda de novo após `sleep(backoff(tentativa))`
  até `times` tentativas extras (throw e `false` retryam ambos); `Report.retries`
  registra `"nome: tentativas=N"`, e `errors` guarda o ÚLTIMO motivo se ainda falhar.
  `retryFixed` é igual com espera zero. Só jobs membros da dag podem ser configurados
- `Report.dead` (deadLetter, face in-memory — SEMPRE presente): todo job que
  esgotou retry entra como `"nome: motivo"` (mesmo texto de `errors`); jobs
  bem-sucedidos nunca entram.
- `deadLetter(job, sink)`: a face durável é CÓDIGO DO USUÁRIO — o sink
  `(nome, motivo) -> Bool` recebe cada falha final (persista onde quiser, ex.
  `kof.orm` no SEU corpo; o workflow segue puro e neutro de alvo, nunca
  dependendo do `kof.orm`). `false` ou throw do sink falha ALTO com o nome do
  job (R6 — dead letter recusado não pode sumir). Um sink por job
  (re-registrar lança).
- `schedule(expr, dag)`: DELEGA ao `scheduler.at` (durações idiomáticas
  `30m`/`1d&30m` ou cron de 5 campos — D-SCHED-DURATION) e devolve o job id do
  scheduler. Cada disparo roda a dag INTEIRA dentro de `spawn` (JVM = uma
  thread por disparo; JS = pump cooperativo — a forma que a CONC003 permite
  dentro de callbacks de timer, já que `run()` pode `time.sleep` no backoff de
  retry). Disparo que falha não derruba o scheduler (isolado no spawn);
  persistência por disparo vai pelo `deadLetter`, que roda dentro de `run()`.
  No NATIVE a fatia é um stub que falha ALTO em runtime citando `CRON001`
  (o gate do scheduler é estático — referenciar `scheduler.at` no host
  rejeitaria o host INTEIRO no compile; o `scheduler.at` DIRETO do usuário
  mantém a recusa em compile-time).
- `checkpoint(d, dbConn, dagName)`: o store REUSA `kof.db`/`kof.orm`
  (entity `KofWfCk`, chave `dagName/jobName`, `CREATE TABLE IF NOT EXISTS` —
  idempotente). Job restaurado re-entra como `succeeded` SEM re-executar o
  corpo; o save acontece 1x por job após o sucesso (a chave unique mantém 1
  linha por job); save recusado/lançando falha ALTO com o nome do job (R6).
  A conexão vive nos closures da dag (sem close automático; em H2 mem use
  `DB_CLOSE_DELAY=-1`). No NATIVE a fatia é um stub que falha ALTO em
  runtime citando `ORM001` — o `kof.db`/`kof.orm` DIRETO do usuário mantém o
  gap honesto dele. BORDA DO PARSER (medida 19/09): campo de função-tipo
  logo após um campo `List<...>` não parseia (`PARSE023` "Expected parameter
  name") — os hooks do ck seguem um campo simples `String dagNome = null` e
  levam `(dagName, jobName)`; mexer na gramática é regra 6.
- `runSupervised(d, supNome, maxReinicios)` (2.1.3b — a supervisão DELEGA ao
  `kof.supervisor`, plano §3): todo job vira um child `transient` de UM
  `Supervisor`; o laço por filho É o one_for_one (só o filho que falha reinicia,
  vizinhos intocados). O corpo roda UMA vez por visita do watcher — `false`/throw
  faz o worker LANÇAR para o supervisor aplicar o `restartLimit`; esgotados os
  `maxReinicios` reinícios o job é derrubado (falha final) e os dependentes
  pulam (`skipped`) transitivamente. Dependências = espera cooperativa sobre
  flags de status por job (campo `Bool` mutável = `ACC_VOLATILE`, o mecanismo do
  DD-OTP-08). O `Report` é montado na ORDEM DE DECLARAÇÃO — determinístico em
  qualquer alvo (a face roda nos 4 alvos, sem stub: o núcleo supervisor entrega
  em todos — §129/OTP001 removido, JS desde §132). Guardas, todas ALTAS (R6):
  `maxReinicios < 1` é recusado (restart ilimitado silencioso = storm de threads
  — a lição medida quando um host caiu em 19/09); job com `retry()` na mesma dag
  é recusado (UMA política de reinício por face — o `retry` do workflow mora no
  `run()`); supervisor sem nome é recusado; orçamentos de espera/settle (30 s)
  falham com o nome do job, nunca travam em silêncio. `checkpoint` e
  `deadLetter` registrados na dag são HONRADOS aqui (restaurado = succeeded sem
  re-executar; o sink recebe a falha final). `kof.supervisor` NÃO precisa de
  import próprio — o `CompilerWorkflow` injeta o host do supervisor flat junto
  com a face e dedupa pela marca quando o usuário também importa
  `kof.supervisor`.
  (a guarda diz isso).

## 3. Idiomática

```
import kof.workflow

main() {
    var acc = listOf("start")
    var build = job("build", () -> { acc.add("built"); return true })
    var image = job("image", () -> { acc.add("imaged"); return true }).after(build)
    var ship  = job("ship",  () -> true).after(image)
    var lint  = job("lint",  () -> true)

    var rep = dag(listOf(ship, image, build, lint)).run()   // ordem = deps, não entrada
    println(rep.summary())   // ok=build,lint,image,ship failed= skipped=
    println(rep.allOk())     // true
    println(acc.get(1))      // built — estado de closure prova a sequenciação real
}
```

Um job que chama o shell usa o Result de `kof.process` (a tabela de gaps dele se
aplica):

```
var compile = job("compile", () -> process.run("make", listOf("-j4")).exitCode == 0)
```

`listOf(...)` em todo lugar — Kof não tem literal de lista `[]` nem de mapa `{}`
(medido; travado por negativo em `WorkflowPrimitivesE2ETest`).

## 4. Semântica de falha num relance

| resultado do corpo | Report | dependentes |
|---|---|---|
| corpo `true` | `succeeded` | rodam quando todos os deps succeederem |
| `false` (após esgotar retries) | `failed` + `errors` `"nome: false"` | `skipped` (transitivamente) |
| `throw "why"` (após retries) | `failed` + `errors` `"nome: why"` | `skipped` (transitivamente) |
| ciclo nos deps | `run()` lança `workflow: ciclo detectado entre: ...` | — |
| job com retry | `retries` `"nome: tentativas=N"` | — |

Guardas de construção (`dag`/`job`/`after` lançam na hora): nome vazio, corpo
nulo, dependência nula, auto-dependência, nomes duplicados na mesma dag, dag
vazia.

## 5. Bordas conhecidas do Kof (achadas ao construir o host)

- Um campo **sem inicializador** logo acima de um campo-lambda quebra o lookahead
  do parser no corpo de classe — inicialize (`String nome = null`). Gramática é
  terreno de regra 6; contornado no host.
- Um lambda-bloco cuja **única saída é `throw`** tipa `Void` — mantenha um
  `return` final para o corpo tipar como `() -> Bool`.
- Uma local de tipo-função **inferida com `var`** a partir de campo perde o tipo —
  anote: `var f: () -> Bool = job.corpo`.

## 6. Prova

`WorkflowE2ETest` 24/24 (goldens exatos de stdout, paridade byte JVM==JS): ordem
linear, cascata de falha, throw com motivo, mensagem de ciclo, conjunto de
guardas, corpos reais via lista capturada, retry (recupera na 3ª + esgota com
motivo + exponential), fire-count do schedule, restore do checkpoint entre runs,
caminho feliz supervisionado, one_for_one (o filho flaky reinicia, o vizinho não
— contadores provam os dois lados do "one"), drop por limite + skip transitivo,
as guardas R6 da face e o dedup do import duplo; compilação Native travada.
A camada de formas é
travada por `WorkflowPrimitivesE2ETest` (6/6, incl. os pins negativos de sintaxe).
Plano: `docs/workflow-plan.pt_BR.md` §5.
