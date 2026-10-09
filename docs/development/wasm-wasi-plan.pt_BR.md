[English](wasm-wasi-plan.md) | [Português](wasm-wasi-plan.pt_BR.md)

**Dono:** `192.168.15.101:9092` (lane TIER 15; 15.1+15.2+15.3-fatia1+15.3b+15.3c-fatiaA+15.3c-fatiaB pousadas; runtime GC-handle em seguida) — promovido por `D-WEB-WASI-DEFAULT-0710` (GATE do 0.6.0, #776); qualquer lane livre o reivindica no DOING primeiro (`D-PLAN-ONE-OWNER`).

# WebAssembly (WASM) + WASI — especificação de implementação futura

last: 15.3d-incremento2-FATIA-B POUSADO 09/10 (larguras de campo de record + paridade char/bool; `WasmWasiE2ETest` 10/10 + bateria 28/28; antes a 15.3d-incremento2-FATIA-A POUSADO 09/10) (`String.valueOf(Int|Long)` como VALOR via `kof.intToStr` no bump heap; `WasmWasiE2ETest` 8/8 + bateria 26/26; antes o 15.3d-incremento1 POUSADO 08/10 (ALOCACAO de RECORD + ACESSO a campo Int/Long no bump heap: `KofNewObject`->bump do `global 0` por `ClassLayout.totalSize`, `KofDup` no-op, `<init>` de record desempilha os args em ordem reversa de campo num scratch i64 e faz `i64.store` em `obj+fieldOffset`, `KofLoadField`=`i32` handle+`fieldOffset`->`i64.load`; novos opcodes i64 de memoria reais `Mem.LOAD64=0x29`/`STORE64=0x37`; SO campos Int/Long — String/Bool/Char/Double/aninhado + `toString`/`equals`/concat-de-record seguem recusando `WASM002` SEM artefatos ate o lowering de metodo de instancia; `WasmWasiE2ETest` 7/7, `recordAllocationAndIntFieldAccessMatchTheJvmOracle` stdout `1/2` == oracle JVM sob wasmtime v49.0.2); antes a 15.3c-fatiaB POUSADA 08/10 (`args` via `args_sizes_get`/`args_get` do preview1: `kof.readArgs` monta `[count][handle...]` de handles KofString no heap, argv[0] descartado p/ paridade JVM; `args.length` = `[arr]` + `i64.extend_i32_s` (D-WASM-02); `args[i]` = trap explicito de limites (`i32.le_u` -> `unreachable`, nunca lixo); E2E `alpha beta` == oracle JVM; `WasmWasiE2ETest` 5/5); antes a 15.3c-fatiaA POUSADA 08/10 (VARIAVEIS + CONCATENACAO de String num bump heap `global 0`@16384, handles `[len][bytes]\n`; `kof.strLit`/`kof.strConcat`/`kof.writeStr`; `WasmWasiE2ETest` 3/3 imprime `ab`/`xyz` == oracle JVM sob wasmtime v49.0.2); antes a 15.3b POUSADA 07/10 (`println(String)` de LITERAL via data segments + `kof.writeString`, paridade de stdout com oracle JVM sob wasmtime); antes a fatia 1 da 15.3 POUSADA 07/10 (lane `192.168.15.101:9092`) — `Target.WASI` EMITE um modulo WASI-preview1: `main` -> `_start`, `println` escalar -> `fd_write` (WasmWasiE2ETest 3/3, stdout == oracle JVM sob wasmtime); antes a 15.2 — o backend `wasm` emite o SUBSET ESCALAR: funções top-level `Int/Long/Double/Bool/Char`, binário WebAssembly direto (`WasmBackend`/`WasmBinary`/`WasmInstr`), Int=i64 (D-WASM-02), dispatcher `loop $dispatch` + `$pc`; executado + validado sob wasmtime v49.0.2 / wasm-tools 1.261.0 (`WasmScalarE2ETest` 3/3, oráculo = o mesmo programa na JVM). `WASI` segue `WASM001`; fora do subset (IO/coleções/records-2/void) recusa `WASM002` nomeando o plano + #776, SEM artefatos.
doing: 15.3 continua — 15.3d-incremento2 (`toString`/`equals`/concat de record via lowering de metodo de instancia) depois colecoes; `println(array)`/for-in/`.length` em String seguem recusando `WASM002`; depois 15.4 flip.
next: runtime GC-handle (records/colecoes) → 15.4 flip do frontend-padrão POR ÚLTIMO (só com paridade total; o corte segue gated por #776 via `D-LAB-STABILITY`).
location: docs/development/wasm-wasi-plan.pt_BR.md
state: EM DESENVOLVIMENTO

> **Estado (07/10): EM DESENVOLVIMENTO — promovido por ordem direta da**
> **mantenedora** (`D-WEB-WASI-DEFAULT-0710`); **zero código no dia da promoção.**
> O plano é **GATE do corte 0.6.0** (issue #776): o alvo web do Kof passa a ser
> **WASI por padrão**, o frontend desktop igualmente; o alvo JS/`kofjs`
> **continua existindo** quando explicitamente especificado; a **superfície da
> linguagem não muda**; **paridade total de comportamento** e **zero regressão**
> são obrigatórios. O plano está ABERTO e sem dono — qualquer lane livre o
> reivindica no DOING primeiro (`D-PLAN-ONE-OWNER`). Cada afirmação abaixo
> permanece marcada **CURRENT** (medido no código), **PLANNED** (proposto aqui)
> ou **TBD / DECISION REQUIRED** (marcadores históricos — o bloco 28/09
> `D-WASM-01..09` já resolveu as questões técnicas).
> por pedido explícito da mantenedora ("documente a implementação futura de
> WASM/WASI; NÃO implemente"). Não é fila de execução (regra três-estados +
> R12). Esta spec **não muda nada**: nenhum `Target`, backend, runtime ou
> semântica de linguagem.

> **Atualização de decisão (28/09/2026):** as questões técnicas que esta spec
> deixou TBD/DECISION REQUIRED foram **resolvidas** pela mantenedora em
> `DECISIONS.md` §`D-WASM-GO` (`D-WASM-01..09`: backend direto, `Int`=i64,
> unwinding por string lançada, handles+tabela de handles, GC mark-sweep do
> design nativo, env de closure explícito, WASI preview1, wasmtime primeiro,
> concorrência cooperativa). A spec continua **future/zero-código**; a promoção
> é uma-por-vez por `D-FUTURE-PROMOTION`. Não leia os marcadores TBD inline como
> decisões ainda abertas.
>
## Como ler este documento

Toda afirmação carrega exatamente uma marca:

| Marca | Significado |
|---|---|
| **CURRENT** | estado real do código hoje (arquivo citado) |
| **PLANNED** | feature futura, explicitamente *não implementada* |
| **TBD** | decisão aberta — ninguém decidiu; não trate como certa |
| **DECISION REQUIRED** | portão regra 6 — a mantenedora decide antes de implementar |

Nunca escreva "Kof suporta WASM": não suporta (CURRENT: `--target wasm` é
**rejeitado** com o gap honesto `WASM001`).

---

## 1. Objetivo da feature (PLANNED)

Adicionar **WASM** e **WASI** como targets oficiais do Kof:

```
Kof source → compilador Kof → IR Kof → backend WASM → module.wasm
```

O desenvolvedor escreve Kof e escolhe WebAssembly sem escrever JavaScript,
C ou linguagem intermediária. Kof **não** vira transpiler: WASM é
especificado como **backend de primeira classe**, par de JVM / Native / JS
/ Script (CURRENT enum: `Target.java` — `JVM, NATIVE, NATIVE_RISCV64,
NATIVE_AARCH64, JS, ANDROID, SCRIPT`; sem `WASM`).

## 2. WASM × WASI — camadas diferentes

```
WASM = o formato de módulo; execução controlada por um host (browser, runtime)
WASI = WASM + interface de sistema (fs, stdio, clocks, random, …) conforme a
       especificação WASI — camada SOBRE o módulo, não "WASM de servidor"
```

```
Kof → módulo WASM → Host                      (browser: glue JS + Web APIs)
Kof → módulo WASM → interface WASI → host/runtime WASI
```

## 3. Arquitetura futura

```
                          Kof
                            │
                   Frontend + IR (CURRENT: parser → SemanticAnalyzer →
                   lowerers; IR compartilhada já provada pelo Target.SCRIPT,
                   que interpreta a IR via CompilerPipeline.interpret)
                            │
     ┌──────────┬───────────┼────────────┬───────────┬──────────┐
     │          │           │            │           │          │
    JVM      Native         JS          WASM      Script     Android
 (ASM bytecode)(asm x86-64)(dev.kof.   ┌────┴────┐  (IR      (ASM→APK,
                       compiler/js)  Browser    WASI         §330)
                                     host      module
```

**Pontos de extensão identificados no código real** (investigar antes de
implementar — não assumir mais deste diagrama do que está escrito aqui):

- `Target.java` — enum + `isNative()`/`nativeArch()` (PLANNED: `WASM`,
  `WASI` — **TBD: uma entrada por host ou `WASM` + flag de host**;
  DECISION REQUIRED).
- `TargetMatrix.java` — **CURRENT**: mapeia os pedidos `wasm`/`kofwasm`/
  `kofwebasm`/`kofwebassembly`/`webassembly` **e as grafias WASI** `wasm32`/
  `wasm32-wasi`/`wasi`/`wasi-preview1`/`wasip1`/`kofwasi` ao gap honesto
  `WASM001`, nunca silencioso. **Fatia 15.1 (LANDED 07/10, lane
  compiler/JVM/native `192.168.15.30:9092`):** os dois caminhos CLI
  (`--frontend=wasi`/`kof.toml` via `TargetMatrix.parse`, legado
  `--target=wasi` via `KofCliSupport.parseTarget`) recusam com `WASM001`
  nomeado — nunca "unknown target" (R6) — e a mensagem aponta para o plano
  promovido + issue #776 + `D-WEB-WASI-DEFAULT-0710` (antes o obsoleto
  "Phase 6 of the platform plan"). Plumbing aditivo: **ainda sem valor no enum
  `Target`** (adjacente a superfície congelada, regra 6) e sem codegen. Prova
  RED-first: `TargetMatrixTest.wasiSpellingsAreHonestGap` +
  `SelectTargetsTest.legacyWasiTargetFlagIsHonestGap`.
- **Fatia 15.2 POUSADA (07/10, lane `192.168.15.101:9092`):** primeiro backend
  emissor do `wasm` — `dev/kof/compiler/wasm/WasmBackend` grava um módulo
  WebAssembly binário direto (seções `WasmBinary`, encodings `WasmInstr`)
  exportando toda função estática top-level ESCALAR (`Int/Long/Double/Bool/
  Char`; `Int=i64` por D-WASM-02) de `Default/Main`; fluxo de controle desce
  do grafo de blocos do IR para um dispatcher `loop $dispatch` + `$pc` (com
  profundidade de `br` explícita — fallthrough apenas-para-frente foi medido
  e rejeitado); chamadas diretas resolvem por nome na serialização. A borda
  do subset é recusa HONESTA, nunca skip silencioso: `CompilerPipeline` pega
  `WasmUnsupportedException` → `WASM002` nomeando plano + unidade + #776 e
  NÃO grava artefatos (Q7). `main`/IO/strings/coleções/records = 15.3+
  (D-WASM-03..06). Emissão WAT segue PLANNED (a linha `--emit=wat` precede a
  fatia; o produto da 15.2 é o binário). Prova: `WasmScalarE2ETest` 3/3 —
  emissão+validação via `wasm-tools` 1.261.0, execução via `wasmtime`
  v49.0.2 (`scripts/provision-wasmtime.sh`, checksums pinados, host-gated por
  `assumeTrue`), paridade com oráculo JVM de `add`, `collatz(27)=111`,
  `fib(10)=55`; o caso de recusa prova módulo parcial INEXISTENTE.
  `WasmTargetGateE2ETest` reppinado na verdade nova (wasm É backend; WASI
  segue `WASM001`; programa fora do subset → `WASM002`).

**Fatia 15.3-stdout POUSADA (07/10, lane `192.168.15.101:9092`):**
`Target.WASI` e agora um backend EMISSOR WASI-preview1: `main` compila para
`_start` exportado (modo comando), e todo `println` escalar escreve pelo
import `wasi_snapshot_preview1.fd_write`; `Int`/`Long` imprimem pelo helper
`kof.writeInt` (itoa emitido), `Bool` pelo `kof.writeBool`. Programas fora da
fatia (literais de string, `args`, records/colecoes) recusam `WASM002`
nomeando este plano + #776, sem escrever artefatos (Q7; provado por
`WasmWasiE2ETest` + `WasmTargetGateE2ETest`). Prova: `WasmWasiE2ETest` 3/3 —
modulo com imports preview1 + export `_start`, validado com `wasm-tools`
1.261.0, executado sob `wasmtime` v49.0.2 com stdout EXATAMENTE igual ao
oracle JVM do mesmo fonte (ints negativos/zero/resultado de chamada, bools),
exit 0; `WasmScalarE2ETest` 3/3 (15.2) e `WasmTargetGateE2ETest` 5/5
reppinado (WASI E backend; o gap de frontend fica `WASM001` ate 15.4) seguem
verdes; `kof deploy --target wasi` recusa com honestidade `WASM001` (emite
modulo, mas sem archive/host de deploy ainda — `SelectTargetsTest`/
`CmdDeployTest` verdes sem mudanca). `println(String)`, records, colecoes,
`args` e o runtime de GC handles pousam nas PROXIMAS fatias da 15.3
(regra GC-handle do plano).

**Fatia 15.3b POUSADA (07/10, lane `192.168.15.101:9092`):** `println(String)` de
LITERAL agora emite: os bytes vao para um DATA segment do wasm (pool com base em
1024, `+1` byte reservado por string para o newline, alinhado em 4), a interceptacao
do println empilha `(addr,len)` e o helper emitido `kof.writeString` escreve via
`fd_write` com o `\n` final. Prova: `WasmWasiE2ETest` 3/3 — main mista escalar+string
imprime EXATAMENTE o stdout do oracle JVM (`oi`, `hello kof` adicionados) sob
wasmtime v49.0.2, modulo valida com `wasm-tools`; strings fora da fatia (concat
`var s = "a" + "b"`, `println(args)`) recusam `WASM002` nomeando plano + #776 sem
artefatos (Q7); `WasmTargetGateE2ETest` 5/5 reppinado (o caso de recusa saiu de
literal — agora EMITIDO — para `args`); `WasmScalarE2ETest` 3/3 segue verde.
VARIAVEIS/concat/records/colecoes de string exigem o heap (shape de handle-table
dos §8/§9 do plano) — 15.3c+ (`args`) e depois as fatias de GC. NOTA 07/10: a suite
de 4 modulos e `4877 run / 3 F` — JavaFX ambiental (documentada) +
`Av1CoeffsE2ETest` aarch64/riscv64 = regressao EXTERNA catalogada como
`known-bugs` **§625** (bisect cai em `a2f69d2f7` do shift cross do §620; reproduzida
no tip remoto `45d839322` SEM nenhum codigo da lane WASI; lane dona
`192.168.15.30:9092`; NAO tocada por esta lane — regra de colisao) — **CORRIGIDA
08/10** por aquela lane (prologue cross/x86 zera slots acima de `paramSlotMax`, `PrologueSlotInitTest`).


- **Fatia 15.3c-fatiaA POUSADA (08/10, lane `192.168.15.101:9092`):** VARIAVEIS e
  CONCATENACAO de String agora emitem — um valor `String` vira um HANDLE i32 num
  bump heap (`global 0`, i32 mutavel inicializado em `16384`; layout `[len][bytes]\n`,
  o `+1` do newline reservado na emissao para o `println` nao realocar). Tres helpers
  emitidos: `kof.strLit(addr,len)` copia um literal do DATA segment para um bloco novo
  do heap e devolve o handle; `kof.strConcat(a,b)` aloca `la+lb`, copia os dois payloads
  (`copyLoop` — `block`+`loop` com `br_if`/`br`, `i32.load8_u` align 0, byte store com
  `addr` empilhado antes do `value`) e devolve o handle concatenado; `kof.writeStr(handle)`
  escreve `[h]+1` bytes via `fd_write` (reusado para cada `println` de string nao-literal).
  A interceptacao do println empilha `(addr,len)` para literal e chama `kof.strLit` para
  `kof_string_concat`/variaveis `String`, entao `var s = "a" + "b"; println(s)` e
  `println("x" + "y" + "z")` imprimem `ab`/`xyz`. Prova: `WasmWasiE2ETest` 3/3 — a main
  mista escalar+string+concat imprime EXATAMENTE o stdout do oracle JVM
  (`3/-42/0/55/true/false/14/oi/hello kof/ab/xyz`) sob wasmtime v49.0.2, modulo valida com
  `wasm-tools`, exit 0; `WasmTargetGateE2ETest` 5/5 + `WasmScalarE2ETest` 3/3 +
  `TargetMatrixTest` 10/10 verdes. Na epoca `args` ainda recusava; pousou no MESMO DIA
  como 15.3c-fatiaB abaixo (records/colecoes ficam; flip 15.4 POR ULTIMO). MEDIDO 08/10: a suite completa dos 4 modulos no tip `898bc50ab` + esta fatia = 8F + 2 flakes, TODOS externos/ambientais e stash-prova independentes da lane WASI: §625 `Av1CoeffsE2ETest` (2F, CORRIGIDA 08/10 pela lane `.30:9092` — zera slots stale do prologue) + §627 `KofTestingE2ETest` assert-float (2F, CORRIGIDO 08/10 pela lane `.30:9092` — `EQ/NE` do interpretador + `feq.s` cross) + §628 `JvmLauncherDiagnosticE2ETest` (3F deterministas no tip LIMPO — o `ExternalArgTighten` do §554 quebra as fixtures de pipe, dona compilador/interop) + JavaFX ambiental (1F) + flakes de carga `InteropTimeoutE2ETest` (2F, VERDES isolados). Fila viva 3->5 (5->3 apos as correcoes §625+§627).


- - **Fatia 15.3c-fatiaB POUSADA (08/10, lane `192.168.15.101:9092`):** `args` — um
  programa que toca `args` ganha prologo `kof.readArgs` no `_start`: WASI-preview1
  `args_sizes_get(&argc@48,&argv_buf_size@52)`, bump-aloca a TABELA de ponteiros
  (`argc*4`) e o BUFFER NUL (`sz`) no `global 0`, chama `args_get` e converte cada
  `argv[i+1]` (argv[0] = nome do programa, DESCARTADO p/ paridade JVM) num handle
  KofString `[len][bytes]\n` no heap (strlen + `copyInto`), devolvendo handle de
  array `[count][handle...]`. `args.length` reduz a `i32.load` de `[arr]` +
  `i64.extend_i32_s` (Int = i64, D-WASM-02); `args[i]` reduz a `i32.wrap_i64` do
  indice com bounds-check EXPLICITO (`count <= idx` via `i32.le_u` -> `unreachable`)
  — fora de limites e trap DETERMINISTICO, NUNCA leitura de lixo (modelo de excecao
  D-WASM-03 segue TBD); `println(args[i])` e `args[i] + "x"` usam o runtime de String
  da 15.3c-fatiaA (`kof.writeStr`/`kof.strConcat`). Prova: `WasmWasiE2ETest` 5/5 sob
  wasmtime v49.0.2 — `argsLengthIndexAndConcatMatchTheJvmOracle` compila a MESMA
  fonte para JVM e WASI, roda as duas com `alpha beta` e exige stdout byte-a-byte
  identico (`2/alpha/beta/alpha-beta`); `emptyArgsLengthMatchesJvmAndOobIndexTraps`
  pinta `args.length == 0` sem argv e exit NAO-ZERO do wasmtime para `args[7]`
  (`unreachable` explicito, exit 134 medido); face de recusa re-pinnada: `println(args)`
  (formato de array) e `for (a in args)` seguem `WASM002` nomeando plano + #776 sem
  artefatos. Higiene de gate: o split cobrado pelo `check_500` pousou COM esta fatia —
  `WasmBackend` 629 -> 204 (so assembly do modulo), lowering/dispatcher/Ctx extraidos
  para `WasmLowering` (447), `kof.readArgs` + `copyInto` vivem em `WasmArgsRuntime`
  (192), `WasmStdoutRuntime` 503 -> 329; puro movimento de codigo, bateria WASI verde
  depois. MEDIDO 08/10 (arvore pos-split + pos-correcao §625): suite completa 4 modulos = kof-compiler 4898 exec/5F + kof-cli 593/3F — §627 2F + §628 3F = 5F deterministas EXTERNAS (outras lanes) + JavaFX-amb 1F + flakes de carga InteropTimeout 2F = 3F ambientais; §625 VERDE apos a correcao `.30` do prologo (`6572e6367`, Av1Coeffs 6/6 re-medido aqui); RingPrivilege verde nesta passada; ZERO falhas WASI, bateria 23/23 + PrologueSlotInit 2/2.

**Fatia 15.3d-incremento1 POUSADA (08/10, lane `192.168.15.101:9092`):** ALOCACAO de RECORD + ACESSO a campo Int/Long — um valor de record e um bloco no bump heap cujo layout e o `ClassLayout.build(IRClass)` (MESMA fonte do Native: cabeca + slots de 8 bytes), o receptor e um handle `i32` no slot local (`lowerMethod` mapeia um local/param de tipo record para `i32`); `WasmLowering` ganha `KofNewObject` (bump do `global 0` por `totalSize`, guarda o handle num scratch `obj` e o empilha), `KofDup` (no-op — o handle que o construtor deixou na pilha de valores do wasm e reusado), o `<init>` de record (chamada `CONSTRUCTOR`: desempilha os valores dos argumentos na ordem reversa dos campos num scratch `v` i64 e faz `i64.store` em `obj + fieldOffset`) e `KofLoadField` (handle `i32` do receptor + `fieldOffset` -> `i64.load`). `WasmInstr.Mem` ganha os opcodes i64 de memoria reais `LOAD64=0x29`/`STORE64=0x37` (align 3) — o menor mecanismo, nao um hack por funcao.** **Honestidade de escopo (Q7/R6):** so campos `Int`/`Long` (i64 na pilha) sao construidos; um record com campo `String`/`Bool`/`Char`/`Double`/record-aninhado, `println(record)` (`toString`), `record == record` (`equals`) e record-em-`+` exigem lowering de metodo de INSTANCIA (ainda ausente) e seguem recusando `WASM002` nomeando plano + #776 SEM artefatos — nunca um modulo silenciosamente invalido. Prova: `WasmWasiE2ETest` 7/7 sob wasmtime v49.0.2 — `recordAllocationAndIntFieldAccessMatchTheJvmOracle` compila `record Point(Int x, Int y) { var p = Point(1,2); println(p.x); println(p.y) }` para JVM + WASI e afirma stdout byte-igual (`1/2`); `recordToStringAndEqualityStillRefuseHonestly` pina as tres faces ainda abertas (toString / igualdade / record com campo String) para `WASM002` + sem-artefatos. PROXIMO (incremento 2): as faces `toString`/`equals`/concat de record pousam com o lowering de metodo de instancia; depois colecoes; depois 15.4 flip POR ULTIMO.
**Fatia 15.3d-incremento2-FATIA-A POUSADA (09/10, lane `192.168.15.101:9092`):** `String.valueOf(Int|Long)` como VALOR (variavel / dentro de `+`) — `WasmStdoutRuntime.kofIntToStr` formata os digitos na MESMA janela de scratch que o `kof.writeInt` ja provado verde usa, depois bump-aloca um handle `[len][digits]` no heap com o padrao do `kof.strLit` (`copyLoop`, bloco +5) e devolve o handle; o `WasmLowering` roteia o valueOf de dono `String` que NAO e imediatamente `println` para `kof.intToStr` (Int|Long), trata receptor String como IDENTIDADE (o desugar do `+` da JVM reaplica `valueOf` sobre um handle existente — medido no `_start` emitido), e recusa o resto `WASM002` SEM artefatos (Q7); o `WasmBackend` registra o helper junto do runtime de strings. Prova: `WasmWasiE2ETest` 8/8 sob wasmtime v49.0.2 — `stringValuesOfVariablesAndConcatMatchTheJvmOracle` (mesma fonte JVM+WASI, byte-igual `3 / -42 / 7! / x100`); bateria WASI 26/26. PROXIMO: fatia B = larguras de campo de record (Bool/Char/handle-String = slots i32, Double = f64), fatia C = `toString`/`equals`/concat-de-record inline via `kof.strEq`.
**Fatia 15.3d-incremento2-FATIA-B POUSADA (09/10, lane `192.168.15.101:9092`):** LARGURAS DE CAMPO DE RECORD + paridade de impressao char/bool — `WasmStdoutRuntime.kofWriteChar` agora recebe o i64 que o `emitLiteral` 15.2 REALMENTE empilha para `Char` e faz wrap p/ um local i32 (o modulo antigo de `println(char)` era INVALIDO — um falso-verde latente agora pinado por `charAndBoolPrintMatchTheJvmOracle`); `WasmLowering` despacha os stores do `<init>` de record e as leituras `KofLoadField` por largura: Int/Long/Char = i64 (`Mem.STORE64/LOAD64`), Bool/handle-String = i32 (`Mem.STORE/LOAD`), Double = f64 (`Mem.STORE_F64/LOAD_F64` = 0x39/0x2b align 3, novo em `WasmInstr.Mem`) com scratch por funcao `v32`/`vf64`; `operandTypeBefore` responde o tipo do campo para o `println(campo)` registrar o escritor certo; `isStringField` reconhece campos `String` de record; `usesStringOps` registra o runtime de strings em programas com literais/handles de String sem nenhuma string impressa (so `Pair("ab", 7)` codificava uma chamada `kof.strLit` nao resolvida); campos record-aninhados seguem recusando `WASM002` SEM artefatos (a recusa `record-string-field` virada verde; `record-nested-field` pinada). Prova: `WasmWasiE2ETest` 10/10 (`charAndBoolPrintMatchTheJvmOracle`, `recordFieldWidthsMatchTheJvmOracle` byte-iguais ao oraculo JVM sob wasmtime v49.0.2); bateria WASI 28/28. PROXIMO: fatia C = `toString`/`equals`/concat-de-record inline via `kof.strEq`.

**Emenda (07/10, lane `192.168.15.101:9092`, 15.1-COMPLETA):** o passo do
  enum NÃO ficou adiado — o roadmap TIER 15 define a própria 15.1 como
  enum+encanamento (ordem da mantenedora `D-WEB-WASI-DEFAULT-0710`, que
  promoveu este plano). `Target.WASM`/`Target.WASI` agora EXISTEM: as formas
  canônicas `wasm`/`wasi` PARSEIAM como alvos reais ponta a ponta, e a recusa
  honesta de emissão (WASM001, nomeando este plano + unidade 15.2 + #776, sem
  escrever artefatos, nunca fallback JVM) vive em
  `TargetMatrix.validate`/`CompilerPipeline.lowerAndEmit`
  (`WasmTargetGateE2ETest` 5/5). Os solecismos longos mantêm o gap de string
  da `.30` verbatim; os dois testes da `.30` foram re-pinados para esta
  verdade (cobertura de solecismos preservada). O código do gap está
  reservado para este futuro (§14).
- `dev.kof.compiler.js` (`JsBackend`, `JsArtifactWriter`, …) — precedente
  estrutural para um pacote `dev.kof.compiler.wasm`.
- CLI: `dev.kof.cli` mapeia `"jvm"|"native"|"js"` para `Target` (ex.:
  `BenchDiscovery.java`); braço `wasm`/`wasi` é PLANNED (§23).
- Sistema de capabilities da stdlib — CURRENT: gates por `Target`
  (`supportedOn`) + códigos de gap `XXX00x` + matriz
  `docs/backend-parity.md` (§14).

## 4. Decisão arquitetural principal (REQUISITO)

```
Kof → WASM deve ser compilação DIRETA pela infraestrutura interna do
compilador (parser → typer → lowerer → emissor WASM), produzindo o binário
.wasm ele mesmo.
```

Rejeitado como arquitetura principal: `Kof → JavaScript → WASM` (amarra o
produto a toolchain JS e herda semântica JS) e `Kof → C → WASM`
(mediatizado por clang; perde tipos/IR do Kof, duplica a história da
toolchain C). O que viabiliza o backend próprio: o **front-half**
(lexer/parser/AST/`SemanticAnalyzer`/lowerers já target-neutrais — prova:
`SCRIPT` roda na IR compartilhada sem backend) e os **contratos de shape de
runtime** (GC, strings, dispatch) já pagos em JVM+Native+JS. **TBD**: se o
emissor consome a IR de lowering atual direto ou uma IR intermediária
orientada a WASM — decidir na Fase 0 (§31).

## 5. Target registry (15.1+15.2 POUSADA 07/10 — enum + matriz + parse da CLI + emissão ESCALAR)LANEJADA)

```
CURRENT (real hoje):
  kof compile/build/run → jvm | native (native.risc/arm cross) | js | script | --android
  kof --target wasm → EMITE o subset escalar desde 15.2 (fora dele: WASM002 honesto)
  kof --target wasi → PARSEIA; o compile recusa com WASM001 honesto — nunca silencioso

PLANNED (só esta doc — não documentar como existente):
  kof build --target=wasm / --target=wasi
  kof check --target=wasm / --target=wasi
  kof run   --target=wasm   (precisa de host — §19)
  kof run   --target=wasi   (precisa de runtime WASI — §19)
```

Formato da flag (`--target=x` vs uso atual) medido no `dev.kof.cli` na
implementação — **TBD**.

## 6. Backend WASM (spec futura)

O emissor deve produzir módulo core-WASM válido cobrindo ao menos:
`Type · Function · Table · Memory · Global · Export · Import · Code · Data`.

Mapeamento `conceito Kof → IR → WASM`:

| Conceito Kof | Representação WASM | Status |
|---|---|---|
| função | função WASM (type + code) | PLANNED |
| local | local WASM | PLANNED |
| control flow (`if`/`while`/`for`/`switch`) | `block`/`loop`/`if`/`br`/`br_table` | PLANNED |
| `Int` | `i64` ou `i32`? | **TBD — segue a spec numérica do Kof, nunca arbitráio** |
| `Float`/`Double` | `f32`/`f64` | PLANNED |
| referência (objeto/record/String) | `i32` offset na linear memory vs GC `ref` | **DECISION REQUIRED (§8)** |
| chamada ao host | `import` + adaptador JS/WASI | PLANNED |

Mapeamento que dependa de decisão aberta fica **TBD** — não inventar.

## 7. Runtime WASM

Runtime mínimo avaliado contra o runtime Nativo existente (allocator,
memória, strings, arrays, objetos, erros de runtime/panic, GC). Perguntas
abertas da Fase 0 (todas **TBD**): o que é reutilizável do Native
(source-level, não binário); ABI necessária (a ABI Native atual é
SysV-shaped x86-64; WASM é nova); representação de objetos Kof (a história
do slot de tamanho de lista tem seu próprio §252); administração de
memória (§8).

## 8. Memória linear

```
objeto Kof → runtime Kof → linear memory WASM
```

Pontos de investigação (nenhuma estratégia decidida): allocator
(bump-then-GC?), alignment, object layout, representação de ponteiro
(`i32` offset vs `i64` vs `ref` gerenciado), growth (`memory.grow`), free,
integração com GC, root tracking. Nada está comprometido — afirmar hoje
seria ficção.

## 9. Garbage collector

CURRENT do GC Kof: **JVM** delega ao GC da JVM; **Native** tem decomposição
mark-sweep **em andamento** no roadmap (G-0 riscv ✅ `356f33b9`; G-1..G-5
pendentes — `docs/development/roadmap.md`); auto-GC foi desabilitado após
um hang (nota do roadmap). WASM deve se acoplar a **esse** design, não
forkar o seu.

Dependências documentadas: safe points, root maps, referências de objeto,
ciclos alocação/coleção, metadados de runtime.

> **Invariantes: o backend WASM NÃO pode criar uma segunda semântica de
> gerenciamento de memória independente do Kof.** Se o GC atual não está
> pronto, isso é dependência explícita (§30), não motivo para inventar
> outro GC.

## 10. Strings

A representação futura de `String` em WASM deve **seguir a spec Kof
existente** (o que um `String` observa — length, indexação, comparação,
concatenação — é definido pela linguagem, não pelo backend). Cobrir:
decisão UTF-8 vs UTF-16 de armazenamento (**TBD — checar a semântica de
strings em `docs/` primeiro; não escolher arbitrariamente**), meaning de
length, allocation, concatenação, comparação (conteúdo `==`, congelada),
indexing/slicing, custo de conversão no host.

## 11. Records, objetos e classes

Mapeamento futuro a investigar: records (imutáveis — layout estático),
classes (campos mutáveis), interfaces, herança, **dispatch virtual**
(vtable? switch-on-type-tag? — **TBD**; o backend Native já escolheu o
esquema dele — estudá-lo). A semântica Kof deve ser preservada; abstrações
Kof **não** se traduzem automaticamente em abstrações JavaScript
(regras 8/9: Kof não é JS).

## 12. Closures

Implementação futura cobre: capture, environment, lifetime, allocation,
referência de função (calls indiretas via `table` WASM ou convenção
closure-record — **TBD**), closures aninhadas. **Seção de riscos**: closure
é o feature mais sensível a layout de memória — depende de §8/§9/§11
decididos em conjunto; a semântica de capture por referência deve bater
com os outros quatro targets (congelada — Freeze §6).

## 13. WASI

Superfície de integração: `stdin`, `stdout`, `stderr`, `arguments`,
`environ`, `clock`, `random`, `filesystem`; demais capabilities conforme a
versão escolhida.

```
Versão WASI: TBD — re-verificar o estado da spec upstream na hora de
implementar (era preview1 vs 0.2/component-model); nenhuma versão é
assumida aqui.
```

## 14. Capabilities

Integra com o sistema **CURRENT** (`supportedOn` por target + códigos
`XXX00x` + matriz `docs/backend-parity.md`). `WASM001` já existe no
`TargetMatrix` como rejeição honesta; a implementação precisará de uma
família de códigos por API não suportada (R6). Matriz futura — valores só
onde há decisão real (`✓ ~ - ?`):

```
                     JVM    Native  JS    WASM  WASI
strings/records      ✓      ✓       ✓      ?     ?
filesystem (kof.io)  ✓      ✓       -      -     ~
browser APIs         -      -       ✓      ~     -
clock                ✓      ✓       ✓      ?     ?
random               ✓      ✓       ✓      ?     ?
process (kof.process)✓      PROC001 ✓*     ?     ?
networking           ✓      ~       ?      -     ?
```

(`-` não aplicável / `~` parcial / `?` TBD — nada inventado; linha JS
`process` reflete `PROC001` em spawn/pipeline.)

> **Regra arquitetural (existente, R6): API indisponível num target deve
> produzir diagnóstico explícito quando conhecível em compile time. Sem
> silent stubs (Q7).**

## 15. Browser WASM

```
Kof → módulo WASM → host Browser → Web APIs (glue JS)
```

Distinto de KofJS: **KofJS** = target JavaScript (emissor em
`dev.kof.compiler.js`); **KofWASM** = target WebAssembly + host JS fino.
Bridge a documentar: DOM, eventos, `fetch`, storage, canvas, Web APIs —
cada uma é host integration, e cada gap voltado ao Kof ganha código (§14).
(Interage com `qrcode-wasm-plan.md` — plano de features QR+web — e com
`D-UI-SCOPE`: mudanças de regra do kof.ui pertencem a KofJS hoje e ao Kof
WebASM quando `Target.WASM` existir, nunca JVM/Native.)

## 16. Interop JavaScript

Separar **WASM core** (sem dependência de JS para rodar lógica Kof) de
**host integration específica de browser** (glue JS exigido pelo
browser). JS é *capability/host integration* quando necessário — nunca
dependência obrigatória para todo código Kof WASM. Mecanismo (gluegen?
wasm-bindgen-like? adaptador à mão?) — **TBD / DECISION REQUIRED**.

## 17. KofUI

```
KofUI
 ├── KofJS   (target UI web CURRENT)
 └── KofWASM (segundo target UI futuro)
```

Intenção: uma API conceitual de UI, hosts diferentes. **Não** duplicar a
API. Dividir: **abstração compartilhada** (intenta declarada — layout,
widgets, eventos) vs **implementação backend/host** (chamadas DOM, bridge
wasm). Regra 9 vale: código Kof declara intent; HTML/CSS nunca vaza para
código de usuário — WASM não é exceção.

## 18. WASI para aplicações (PLANNED)

CLI, servidores, edge, sandbox, plugins, embedded — futuros **possíveis**,
não garantias iniciais. WASI permite rodar Kof compilado fora do browser
em ambientes compatíveis; o que entra na primeira leva é escopo Fase 4+.

## 19. Host e runtime

`.wasm` é um **módulo**; rodar exige sempre um **host/runtime**. Registrar
para decisão (tudo **TBD / DECISION REQUIRED**):

```
runtime oficial de desenvolvimento: TBD
WASI runtime(s) suportados:         TBD (avaliar wasmtime/wasmer-like na
                                    hora; nenhuma ferramenta externa é
                                    adotada por esta doc)
descoberta de runtime (kof run):    TBD — diagnóstico honesto se ausente (R6),
                                    nunca fallback silencioso (Freeze §6)
```

> Kof não se acopla permanentemente a um runtime externo específico sem
> decisão arquitetural (R9 vale para *bibliotecas*, não para virar refém
> de um runtime).

## 20. Concorrência

Semântica congelada do Kof: `spawn`/`await` (Freeze §6). Análise futura:

- browser WASM: event loop single-threaded do host → mapeamento de `spawn`
  (**TBD**; worker threads = host-gated);
- `wasi-threads` / SharedArrayBuffer+atomics: não são WASM baseline —
  **TBD**, capability-gated;
- channels/scheduler sobre execução cooperativa;
- o modelo pthread de Native/JVM **não** pode ser assumido portável.

## 21. Networking

Documentar em separado, sem API falsa:

```
Browser WASM → fetch/WebSocket via bridge do host (glue JS)   PLANNED/TBD
WASI         → wasi-sockets quando spec+host suportarem        TBD
Native       → estado real atual (gaps na matriz)              CURRENT
JVM          → kof.http via JVM                                CURRENT
```

Registrar gaps explicitamente (R6) em vez de criar APIs que fingem andar.

## 22. Modules / imports

`import foo` continua sendo Kof independentemente do target — **nenhum
sistema de módulos exclusivo de WASM**. Questões futuras: link de múltiplos
módulos vs módulo único + `import`/`export`; fronteiras de módulo; o que
vira export WASM; dead-code elimination; empacotamento de dependências;
implicações do component-model (**TBD**).

## 23. CLI e build system

O build futuro deve tratar `wasm`/`wasi` como qualquer target: seleção,
output (`module.wasm` + glue do host — forma TBD), resolução de runtime,
linking, configuração de host, flags de capability. Marcar todo comando
como **proposta** (§5). O registro `koffing`/`deps` (CURRENT 1.5.x) ganha
empacotamento wasm-aware quando o ecossistema precisar — **TBD**.

## 24. WAT / debugging

Ferramentas futuras (PLANNED, sem promessa):

```bash
kof build --target=wasm --emit=wat   # proposta — forma texto p/ debug do backend
```

Pipeline de debug source-level (`Kof → WASM → debug info → devtools`):
suporte a DWARF/custom-name-section em WASM é **TBD**; o precedente de
source map V3 do backend JS (landado 01/09) é o modelo a estudar.

## 25. Conformance

Ligado à suíte futura de conformance (`docs/bugs-and-gaps/conformance-matrix.md`
é a matriz living CURRENT; layout `tests/conformance/{language,semantics,
stdlib,jvm,native,js,wasm,wasi}` é proposta):

> Validar **equivalência semântica** entre targets quando as
> **capabilities são equivalentes**. Nunca exigir binários idênticos.

## 26. Estratégia de testes

```
Compiler tests    : AST → IR → WASM (goldens de .wasm/.wat)
Validation tests  : módulo gerado passa num validator wasm
Runtime tests     : executa → stdout/exit esperados (golden medido — Q3)
WASI tests        : roda sob o host WASI escolhido → pins de comportamento
Cross-target      : mesmo programa → JVM/Native/JS/WASM(/WASI) quando as
                    capabilities permitem; divergência = gap diagnosticado
                    ou bug, nunca silêncio
```

Guardas honestas (`assumeTrue` + toolchain ausente ≠ red — §"Verification
loop").

## 27. Fuzzing

Fuzz/property testing é **obrigatório** para o backend WASM — gerar um
`.wasm` que *existe* não é gerar um `.wasm` *semanticamente correto*.
Superfícies prioritárias: control flow, **stack typing** (o validator WASM
é o adversário), assinaturas de função, locals, closures, ops de memória,
strings, object layout. Alimenta o mesmo programa de correctness já usado
no Native (família `ArrayBoundsDeepStressTest`).

## 28. Performance

Benchmarks futuros (lista, **sem promessa**): startup, custo de chamada,
aritmética inteira e float, loops, arrays, strings, alocação, JSON.
Comparar depois JVM / Native / JS / WASM / WASI com a tool de bench
existente (`BenchDiscovery` é CURRENT).

## 29. Segurança

Documentar: sandbox, capabilities, fronteiras do host, isolamento de
memória, acesso a filesystem, acesso a rede — WASI em especial.

> Meta de design: código Kof compilado para WASI não obtém acesso
> arbitrário ao SO senão através das capabilities declaradas do host.
> (R11: defesa primeiro — nada security-sensitive é caseiro neste backend.)

## 30. Dependências / pré-requisitos

Só o que a análise do código justifica:

| Dependência | Por quê | Status |
|---|---|---|
| GC Native concluído (roadmap G-1..G-5) | invariante §9 — uma semântica de memória | aberto |
| Estabilidade da IR | emissor WASM é consumidor novo da IR de lowering | SCRIPT já consome — risco baixo |
| `TargetMatrix`/capabilities | estender, não reinventar | CURRENT |
| Decisão D-UI-SCOPE | UI = KofJS/KofWebASM apenas | ratificada `95002c50` |
| Suíte de conformance | portão de claims de paridade | em evolução |
| Split da abstração KofUI (§17) | compartilhado vs host impl. | **TBD** |

## 31. Fases futuras

Cada fase: objetivo · dependências · componentes · testes · critérios.

```
Fase 0  Arquitetura / ABI   — decidir forma da IR (§4), memória (§8), versão
        WASI (§13), runtime (§19); componentes: esta doc + entradas em
        DECISIONS.md; testes: nenhum (design); conclui quando cada TBD acima
        tiver decisão da mantenedora.
Fase 1  Backend WASM mínimo — código straight-line + funções + control flow;
        validation tests.
Fase 2  Runtime + memória   — allocator/layout conforme Fase 0; goldens de
        runtime num host.
Fase 3  Paridade de linguagem — records/classes/dispatch/closures/strings;
        goldens cross-target; fuzzing LIGADO.
Fase 4  WASI                — versão + host da Fase 0; linhas de capabilities
        da stdlib; gaps honestos no resto.
Fase 5  Host Browser        — glue (§16), bridge DOM/eventos (§15).
Fase 6  Integração KofUI    — segundo alvo do D-UI-SCOPE navega.
Fase 7  Conformance         — suíte §25 verde sobre as capabilities declaradas.
```

## 32. Release gates

Esta doc sai de `docs/development/future/` para `docs/` **somente** com a
implementação real. Gerar `.wasm` **não** é suficiente:

```
[ ] backend real (sem roundtrip JS/C — requisito §4)
[ ] módulos válidos (validator-clean)  [ ] runtime ok  [ ] esquema de memória
[ ] strings  [ ] paridade de linguagem (4 targets existentes ≡ WASM onde há
    capability)
[ ] WASI     [ ] linhas da matriz de capabilities honestas (sem silent stub
    — R6/Q7)
[ ] testes de execução + conformance verdes  [ ] docs (learn/, training/idioms)
    atualizadas
```

## 33. Relação com o roadmap

- A fila transversal de `docs/architecture/IMPLEMENTATION-UNIVERSAL-PLATFORM.md`
  lista **WASM** como capacidade (série X) — esta doc é a spec profunda
  dela; a *ordem* obedece **R12** (estágio SYSTEMS fecha primeiro).
- `roadmap.md` §23 é o único plano ordenado — promoção de `future/` exige
  decisão da mantenedora registrada em `DECISIONS.md` (regra 6), que então
  cria a fila no §23.
- Dependências reais upstream: GC Native (G-1..G-5), maturidade da spec
  WASM/WASI (§13), host escolhido (§19) — sem lista artificial de features.

## 34. Registro de decisões

```
Decisão: backend direto (sem roundtrip JS/C)      Status: REQUISITO (esta doc,
        registrado 19/09; implementação ainda futura)
Decisão: código de gap WASM001 reservado          Status: CURRENT (TargetMatrix)
Decisão: D-UI-SCOPE (UI = KofJS/KofWebASM)        Status: DECIDIDA (95002c50)
Decisão: versão WASI                              Status: TBD
Decisão: forma do enum (WASM+WASI vs WASM+flag)   Status: TBD — DECISION REQUIRED
Decisão: representação ponteiro/GC-ref            Status: TBD — DECISION REQUIRED
Decisão: runtime oficial / hosts suportados       Status: TBD — DECISION REQUIRED
Decisão: mecanismo de glue JS                     Status: TBD
Decisão: modelo de concorrência em WASM           Status: TBD — spawn/await
        congelados (Freeze §6) o restringem (regra 6)
```

## 35. Organização dos arquivos

A tarefa de implementação propunha pasta `wasm-wasi/`; a **convenção do
repositório vence** (§35 da própria tarefa): `docs/development/future/` usa
pares planos EN + PT — este único par é o mínimo que mantém as 38 seções
endereçáveis. Se a feature for promovida, a divisão em `wasm-backend.md` /
`runtime.md` / `wasi.md` etc. acontece então, em `docs/`.

## 36. Documentos relacionados

- `qrcode-wasm-plan.md` / `.pt_BR.md` — estratégia de **features**
  QR + KofWasm (esta doc é o counterpart **backend** mais profundo).
- `DECOMPILER.md`, `TRANSLATOR.md` (nesta pasta) — a direção *reversa*
  (WASM → Kof), independente desta.
- `docs/backend-parity.md` — a matriz living que esta spec estende no
  futuro (nunca editar linhas de WASM antes de existir código — regra
  CURRENT: `WASM001` permanece a rejeição honesta).
- `docs/native-multiarch.md`, `docs/architecture/compiler-architecture.md`
  — precedentes de backend (cross-arch Native = o padrão de plumbing que
  entradas de Target seguem).

**Nenhuma implementação realizada. Apenas documentação.**
