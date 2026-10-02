# X2 — motor oficial `interop` (Python/R) — plano de implementação

last: fatia-4-cross
doing: interop-engine
next: none-concluded
location: docs/interop-engine-plan
state: done
intent: ship-complete-interop-engine
constraint: pr619-maintainer-only
decision: D-COMPLETE-FIRST

Pacote completo apenas — stubs, fachadas e "gaps aceitos" não são opções (a regra 11 vale para toda face que chega à superfície da linguagem; `DECISIONS.md` §`D-COMPLETE-FIRST` item 2, mantenedora 26/09).

## Contrato

Marshalling bidirecional tipado (`Int`/`Double`/`Bool`/`String`/`List`/`Map`/`record` ↔ JSON), gerenciamento real de processo (spawn, stdin/stdout, timeout, exit, cancel), estado de sessão, erros nomeados `INTEROP00x`, E2E por alvo, corpus sincronizado. Nasce `experimental` (R5).

## Fatias

| # | Fatia | Estado | Prova |
|---|---|---|---|
| 1 | Motor Python no JVM | pousada 26/09 | alvos {JVM, NATIVE x86, JS, SCRIPT}; `InteropPyScriptE2ETest` golden ≡ JVM |
| 2 | Motor R `KofR` | pousada 27/09 (`996777923`), certificada na CI em `9ec0a4eb9` | golden do round-trip de record byte-idêntico ao do Python |
| 3 | timeout / cancel / reuso | pousada 27/09 (`d7328c036` + §527 `9b4fa30f5`) | `InteropTimeoutE2ETest` 4/4, `InteropTimeoutScriptE2ETest` 1/1, `InteropRE2ETest` +2 gated-R |
| 4 | Faces cross Native / JS / Script | pousada 27/09 (lane issues, ordem da mantenedora "reinvindique e termine") — timeout007/deadline-reuso/cancel-ocioso + R-happy E2E riscv64≡aarch64≡JVM sob qemu (`InteropTimeoutE2ETest` +3, `InteropRE2ETest` +1 com gate de R); 008 segue JVM-only por desenho (objeto cruzando spawn não é contrato no Native); ANDROID/MCU/RISCV32 seguem R7 | `INTEROP005` só onde honestamente sem backing (ANDROID/MCU/RISCV32, hosts sem R) |
| 5 | Corpus + DoD de promoção | pousada 27/09, docs-only (`8aa5883c9` matriz, `eae0ac18e`+`06319455f` idioms, `745d1ed0f` learn, `6b70bbaff` coverage) | gates de doc verdes |

## Design (KOF-first, medido)

- Motor escrito em Kof, não em Java (precedente `D-KOF-AS-CLOUD`/`D-BOOTSTRAP`): loop RPC + marshalling + sessão vivem num host injetado pelo compilador (`dev/kof/interop-py-host.kf`), composto de `kof.process` + `kof.json`.
- Zero namespaces novos: `interop` + `kof.interop` já existem (X6, `D-INTEROP-REFLECT`; linha 68 do stdlib-boundary). Codecs são a fronteira — só `json.encode`/`json.decode`, sem parser manual.
- Superfície (regra 11): `var py = KofPy(fonte)` + `py.callInt/callDouble/callBool/callString(fn, listOf(...))`; tipo do resultado = nome do método; args = lista Kof tipada homogênea. Record arg/retorno: `py.callJson(fn, args)` + `json.decode<R>(payload)` no lado do usuário.
- Modelo = replay sem estado sobre `python3 -u -c fonte+prelude spec`; a sessão É a fonte (definições persistem, mutações de globals não). Face de sessão viva exige tipo de handle nomeado = superfície nova de compilador = regra 6, fatia futura.
- Canal do R: a spec viaja embutida na expressão `-e` como literal de string R escapado (`kofREscape`, ordem provada pelo golden); source reaplicada via `eval(parse(text=s$source))`.
- Wire (protocolo interno, não contrato externo): linha 1 `KOFPID <pid>`, linha 2 status (`KOFOK`/`KOFERR`/`KOFTIME`/`KOFCANCEL`/`""`), linha 3 payload JSON. O deadline mora no FILHO (python `signal.setitimer` SIGALRM→`TimeoutError`; R `setTimeLimit`); o cancel do R é nomeado pelo pai (o `tools::signalHandler` não consegue emitir `KOFCANCEL`). `timeout(Int ms)` default 30000, `0` = sem timer.
- Estado de sessão = corte declarado. Alvos {JVM, NATIVE x86, JS, SCRIPT} reais; `INTEROP005` em riscv64/aarch64 (o cross não recebeu `kof_json_encode_double`/`_long`, JSN001 só x86, §514) e ANDROID/MCU/RISCV32 (face de processo não provada, R7).
- Diagnósticos: `INTEROP001`/`002` (schema X6) e `003` (§510) tomados; o motor é dono de `004` (interpretador não encontrado), `005` (alvo/face sem backing, compile-time), `006` (falha remota), `007` (timeout), `008` (cancel).

## Bugs achados no caminho

- §520 (fatia 2): 4 causas raiz no encode/decode de record JSON (coletor de schema engolia campos String; decode nativo recebia o corpo sem aspas; `encode_string` deixava bytes de controle crus; interpretador sem ramo tag-4 no `encode_list`). Corrigidas na raiz. DECODE de `List<Record>` no x86 permanece `JSN004` (declarado).
- §516: `json.encode(listOf(record))` no x86 despejava o ponteiro cru do objeto; corrigido pelo walker de encode da fatia 2.
- §513 (fatia 1): `json.encode` colapsava slots Double/Long em `encode_int` (walkers x86 de lista/map); `List<Bool>` do JVM castava `Boolean`→`Integer`. Listas `Float` ficam tag-0. Prova `JsonNativeEncodeFpE2ETest`.
- §527 (fatia 3): o await de void deixava o `Object` do runtime na pilha da JVM → `VerifyError` no LOAD; corrigido no único site de lowering, `VoidAwaitStackFrameE2ETest` 4/4.

## Relacao com o ecossistema

- `kof-connector-ecosystem-plan.md` NÃO é puxado para development — abri-lo precisa da decisão explícita `D-CONNECTORS` (regra 6). Os motores X2 SÃO a forma processo dos conectores §5.5 (Python) / §5.6 (R) do catálogo; a rota embedding/CPython-C-API/R-C-API segue não-implementada e não é do X2. Se `D-CONNECTORS` abrir, o wire + as faces do X2 seguem como o adaptador do connector de processo; `INTEROP00x`, goldens e testes permanecem. Fatias 3–5 não afetadas.

## Fechamento

O item 2 FECHA quando toda fatia tiver prova → nota LANDED no `DECISIONS.md`, linha ✅ no roadmap e este doc move para `docs/` (regra dos três estados).

Fora da lane (registrado, intacto): `learn/21-java-interoperability.md` carrega a seção "Reflexão na fronteira" duplicada (linhas 83/116, de `29b8af404` X6) — pedido de revisão ao dono da X6.

**NÃO tocar:** `CompilerDriver.java`/`NativeRuntime.java` além dos hunks mínimos de wiring; IN PROGRESS de outras lanes (`memory/`, media cross); PR #619 (regra 10).
