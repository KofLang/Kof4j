# Conformance Matrix — Feature × Target (Fase 9, plano de plataforma)

> **Criado:** 07/09/2026 · **Dono:** lane KOFSCRIPT (fixes-for-kofagent)
> **Plano:** `docs/development/future/PLATFORM-PLAN.md` Fase 9 ·
> **Roadmap-audit:** linha 12 "Conformance Suite — NOT STARTED (BackendParityTest é proxy)" + fila P4.
>
> **Regra:** cada célula é travada por teste em `ConformanceMatrixTest`
> (kof-compiler/src/test). **DONE** = os 4 targets concordam com a saída
> esperada. **PARTIAL** = divergência com bug registrado em
> `docs/development/known-bugs.md` (ref na célula). **UNSUPPORTED** = gap
> honesto (R6: diagnóstico, nunca stub silencioso).
>
> Targets: **JVM** (bytecode compilado), **Native** (x86_64 ELF; riscv64/
> aarch64 via qemu = follow-up), **Script** (interpretador de IR — o alvo
> de execução direta), **KofJS** (GraalJS). Casos determinísticos apenas:
> (a) linguagem sem efeito colateral; (b) concorrência com ordem garantida
> por `await`/FIFO (lote 3). O que tem ordem NÃO-garantida (fire-and-forget
> sem `await`) e o que depende de tempo real (sleep/interval) ficam em
> `KofConcurrency2Test`/`KofTimeE2ETest`/`KofMqE2ETest` com asserções
> frouxas — não entram na matriz.

## Matriz (lote 1 — linguagem core)

| Feature | Saída esperada | JVM | Native | Script | KofJS | Caso (ConformanceMatrixTest) |
|---|---|---|---|---|---|---|
| aritmética int + overflow | `-2147483648` / `-1` / `1` | DONE | DONE | DONE | DONE | `arith` |
| long div/mod | `3333333333` / `4` | DONE | DONE | DONE | DONE | `longdiv` |
| cast `d as Int` / `L as Int` / `66 as Char` | `9` / `70000` / `66` | DONE | DONE | DONE | DONE | `cast` |
| float println | `0.3333333333333333` / `5.0` / `3.5` | DONE | PARTIAL (bug 44: 6 casas + `5`) | DONE | PARTIAL (doc: `5` vs `5.0`) | `floatprint` |
| string unicode length/charAt | `4` / `233` / `café!` | DONE | PARTIAL (bug 43: UTF-8 5/195) | DONE | DONE | `unicode` |
| string ops split/toLowerCase/trim | `4` / `hello world` / `x\|` | DONE | DONE | DONE | DONE | `strops` |
| map put/get/size | `1` / `2` | DONE | DONE | DONE | DONE | `map` |
| list empty/isEmpty/contains | `true` / `0` / `false` | DONE | DONE | DONE | DONE | `emptylist` |
| `null == null` / `!=` | `true` / `false` | DONE | DONE | DONE | DONE | `nulleq` |
| if-expr curto-circuito null | `iguais` / `nao-ne` | DONE | DONE | DONE | DONE | `nulleqshortcut` |
| set dedup/contains | `3` / `true` / `false` | DONE | DONE | DONE | DONE | `setdedup` |
| if-expression aninhada | `small` | DONE | DONE | DONE | DONE | `nestedif` |
| switch-expression `case ->` | `three` | DONE | DONE | DONE | DONE | `switchexpr` |
| for-in + break/continue | `4` | DONE | DONE | DONE | DONE | `breakcont` |
| record `==` conteúdo + toString + accessor | `true` / `P[x=1, y=2]` / `1` | DONE | DONE | DONE | DONE | `record` |
| record `hashCode()` igual | `true` | DONE | DONE (bug 42 Native corrigido) | DONE | DONE (bug 42 JS corrigido `1ecfb3d`) | `recordhash` |
| lambda filter/map/reduce | `90` | DONE | DONE | DONE | DONE | `lambdachain` |
| lambda captura mutável | `3` | DONE | DONE | DONE | DONE | `lambdacapture` |
| array 2D/length | `60` / `3` | DONE | DONE | DONE | DONE | `array2d` |
| campo estático + bump | `1` / `2` / `2` | DONE | DONE (bug 41 corrigido 07/09) | DONE | DONE | `staticfield` |
| campo estático `+=` | `2` / `4` / `4` | DONE | DONE (bug 41) | DONE | DONE | `staticpluseq` |
| concat string+num (ordem) | `n=42` / `3x` / `x12` | DONE | DONE | DONE | DONE | `concat` |
| lógica booleana + comparação | `false` / `true` / `false` / `true` | DONE | DONE | DONE | DONE | `boollogic` |
| bitwise & \|\| ^ << >> | `2` / `7` / `5` / `16` / `64` | DONE | DONE | DONE | DONE | `bitwise` |
| stdlib kof.math (S1: clamp/abs/sign/min/max/isEven/isOdd/isZero) | `10` / `0` / `7` / `-1` / `3` / `8` / `true` / `false` / `true` | DONE | DONE | DONE | DONE | `stdmath` |
| stdlib kof.strings (S2a: isAlpha/isNumeric/isAlphaNumeric/isAscii/isUpper/isLower/count) | `true` / `false` / `false` / `true` / `false` / `false` / `true` / `false` / `true` / `true` / `true` / `false` / `true` / `false` / `2` / `1` | DONE | DONE | DONE | DONE | `stdstrings` |
| stdlib kof.strings (S2b: capitalize/reverse/repeat/truncate/pad — ASCII) | `Hello world` / `1abc` / `321cba` / `kayak` / `ababab` / `hello` / `abc` / `007` / `ab---` | DONE | DONE | DONE | DONE | `stdstrings2b` |
| stdlib kof.strings (S2b.4: toCamelCase/toPascalCase/toSnakeCase/toKebabCase/slugify — word-split HTTPServer/XMLParser) | `http_server` / `xml_parser` / `helloWorld` / `HelloWorld` / `hello-world` / `hello-world-42` | DONE | DONE | DONE | DONE | `stdstrings2b4` |
| stdlib kof.validation BR (S5: isCpf/isCnpj/isCep/isPis — pesos aritméticos, mod-11 por subtração) | `true` / `false` / `true` / `false` / `true` / `false` / `true` / `false` | DONE | DONE | DONE | DONE | `stdvalidation` |
| stdlib kof.validation rede (S6a: isIpv4/isMac/isPort — dotted-quad sem zero à esquerda; MAC 6 hex sep : ou - consistente; porta 1..65535) | `true` / `false` / `false` / `true` / `false` / `true` / `false` | DONE | DONE | DONE | DONE | `stdvalidationnet` |
| stdlib kof.validation Luhn (S6b: isCreditCard — dígitos extraídos, 12..19, soma de Luhn %10; 20+ dígitos => false) | `true` / `true` / `true` / `false` / `false` / `false` | DONE | DONE | DONE | DONE | `stdluhn` |
| stdlib kof.validation IPv6 (S6b.3: isIpv6 — subconjunto RFC 5952; '::' no máx uma vez; sem forma mista/zona) | `true` / `true` / `true` / `false` / `false` / `false` | DONE | DONE | DONE | DONE | `stdipv6` |
| stdlib kof.strings escapeHtml (S3.1: 5 entidades; >=128 cópia; null/"" => original) | `a&lt;b&gt;&amp;&quot;&#39;c` / `Café &amp; ç` / `&amp;amp;lt;` / `&lt;a href=&quot;u&quot;&gt;y&lt;/a&gt;` | DONE | DONE | DONE | DONE | `stdescape` |
| stdlib kof.validation domínio (S6c: isDomain — RFC 1123 labels, TLD>=2 letras, >=2 labels; v1 sem ponto final/IDN) | `true` / `true` / `false` / `false` / `false` / `false` | DONE | DONE | DONE | DONE | `stddomain` |
| stdlib kof.time (S7: isLeapYear/daysInMonth/dayOfWeek/daysBetween — calendário civil; ano<1 ou >9999 ou data inexistente => false/0; dia 1=seg..7=dom) | `true` / `false` / `true` / `false` / `29` / `28` / `30` / `0` / `4` / `3` / `0` / `60` / `-60` / `0` | DONE | DONE | DONE | DONE | `stdtime` |
| stdlib kof.encoding (S4: hex + base64 + url + base64url — UTF-8 por bytes) | `4869` / `Hi` / `636166c3a9` / `café` / `TWFu` / `café` / `a%20b` / `café` / `ZmImTy0-Zg` / `fb&O->f` / `E` | DONE | DONE² | DONE | DONE | `stdenc` |

> ¹ **STRN001 FECHADO 09/09:** joinWords portado p/ riscv64 (fatia B15) + aarch64
> (mesmo asm traduzido) — paridade byte-a-byte com o x86_64 provada por diff do
> golden oracle no qemu (16 vetores, incl. delimitadores UTF-8 `>=128`).
> `KofStringsTest.wordConvertersClosedOnCrossArch`.

> ² `encoding.hex*` e `encoding.url*` (byte-puro, asm riscv/aarch própria — B10/B11,
> syntax-checked riscv64-as + aarch64-as rc=0) rodam nos 3 nativos; `encoding.base64*`
> e `encoding.base64Url*` são **ENC002** gated em riscv64/aarch64 (reusam
> os `kof_b64*_internal` do runtime crypto/JWT x86 — sem libc no asm puro cross-arch;
> política SECN000; `KofEncodingTest.base64GatedOnCrossArch`). A matriz roda native=x86
> (tem os símbolos), então os 4 targets cobrem o caso.
| stdlib kof.uuid (S3b: v4 — não-determinístico, SEM caso de matriz) | shape `xxxxxxxx-xxxx-4xxx-[89ab]xxx-xxxxxxxxxxxx` | ✅ assert | ✅ assert¹ | ✅ | ✅ assert | `KofUuidTest` 4/4 |

> `uuid.v4()` não entra na matriz equality (entropia): paridade provada por
> ASSERTS DE SHAPE nos 3 targets testáveis (JVM/Native-x86/JS: length=36,
> traços em 8/13/18/23, dígito 14='4', dígito 19∈{8,9,a,b}, unicidade de 2
> draws) — riscv/aarch **SECN000** gated (sem getrandom no asm puro; mesmo
> portão de toda a crypto lane). ¹ x86 fixa variant='8' (nibble alto direto).

> **S2b ASCII:** `capitalize` usa a MESMA regra nos 4 targets (byte 0 `a-z`→`A-Z`).
> `reverse` é byte-reverso no Native e UTF-16/UTF-8 nos demais — coincidem em ASCII
> (caso `stdstrings2b`). Casos não-ASCII: **NAT-STR01** (gap do UTF-8 nativo,
> `plan-stdlib-expansion.md` §5) — não entram na matriz até corrigido (R5/R6).
| recursão profunda (fact 10) | `3628800` | DONE | DONE | DONE | DONE | `recursion` |
| list add/set/remove | `99` / `4` / `2` / `3` | DONE | DONE | DONE | DONE | `listops` |
| map keys() + iteração | `6` | DONE | DONE | DONE | DONE | `mapiter` |

## Matriz (lote 2 — erros/null/JSON)

| Feature | Saída esperada | JVM | Native | Script | KofJS | Caso |
|---|---|---|---|---|---|---|
| try/catch throw-as-String | `caught:not found: x` / `after` | DONE | DONE | DONE | DONE | `trycatch` |
| try/catch/finally (sem throw) | `in` / `fin` / `after` | DONE | DONE | DONE | DONE | `trycatchfin` |
| throw propagando p/ catch externo | `got:kaboom` | DONE | DONE | DONE | DONE | `throwprop` |
| try aninhado | `caught-inner:inner` / `end` | DONE | DONE | DONE | DONE (fix 07/09) | `nestedtry` |
| re-throw dentro de catch | `outer:re:x` / `end` | DONE | DONE | DONE | DONE (bug 52 — fix colateral do bug 45, `c727fee`) | `catchrethrow` |
| null-safety narrowing (`!= null`) | `val=1` / `null-ok` | DONE | DONE | DONE | DONE | `nullnarrow` |
| json.encode int/string/bool | `42` / `"oi"` / `true` | DONE | DONE | DONE | DONE | `jsonenc-int` |
| json.encode lista | `[1,2,3]` | DONE | DONE | DONE | DONE | `jsonenc-list` |
| json.encode record | `{"x":1,"y":2}` | DONE | DONE | DONE | DONE | `jsonenc-record` |
| json.decode int/string/bool | `7` / `oi` / `true` | DONE | DONE | DONE | DONE | `jsondec-int` |
| json.decode lista de primitivo | `3` / `2` | DONE | DONE | DONE | DONE | `jsondec-list` |
| json.decode record | `1` / `2` | DONE | DONE | DONE (fix 07/09) | DONE | `jsondec-record` |
| json.decode lista de record | `2` / `2` | DONE | PARTIAL (bug 48: não compila) | DONE (fix 07/09) | DONE | `jsondec-recordlist` |

> **Fix 07/09 (lane interpreter):** `json.decode<Record>` no interpretador
> dava exit 1 + stderr só `Point` (R6) — o método gerado `kof_json_decode_Point`
> faz `Class.forName`, mas no interpretador a classe Kof é `KofObj`. Corrigido
> em `KofInterpreterRuntime.decodeKofValue` (espelha `encodeKof`). O caso
> `jsondec-record` passou de PARTIAL(Script) → DONE.

## Matriz (lote 3 — concorrência DETERMINÍSTICA)

> Casos de `spawn`/`await`/`channel` onde a ordem é garantida (await
> bloqueia; FIFO na mesma thread). O **fire-and-forget** sem `await` é
> NÃO-determinístico por design (ordem de agendamento) e fica em
> `KofConcurrency2Test` com asserções frouxas — não entra na matriz.

| Feature | Saída esperada | JVM | Native | Script | KofJS | Caso |
|---|---|---|---|---|---|---|
| `spawn fn` + `await` (resultado) | `42` | DONE | DONE | DONE | DONE | `spawnawait-fn` |
| 2 handles: cada `await` devolve o SEU | `2` / `11` | DONE | DONE | DONE (fix race 07/09) | DONE | `spawnawait-two` |
| channel mesma-thread (FIFO Int+String) | `s=11` / `ab` | DONE | DONE | DONE | DONE | `channel-samethread` |
| channel send-em-spawn + receive | `42` | DONE | PARTIAL (bug 50: SIGSEGV) | DONE | DONE | `channel-spawn` |
| channel 2 sends em spawn + 2 receives | `1` / `2` | DONE | PARTIAL (bug 50) | DONE | DONE | `channel-spawn-two` |

> **Fix race 07/09 (lane interpreter):** `KofInterpreter.lastReturned` era um
> ÚNICO campo de instância sobrescrito por cada `KofReturn`; com 2 `spawn`
> concorrentes (virtual threads) o `await` do handle 2 podia ler o retorno do
> handle 1 (reproduzido 3/120: `11|11`/`2|2`). Correção: retorno vive na
> `KofInterpreterFrame.Frame.returnValue` (per-invocação/per-thread). Prova
> `KofScriptTest.concurrentAwaitReturnsOwnTaskResult` (25 tasks × 8 runs).
> **Bug 51** (vazamento de estado de `CompilerDriver` reutilizado → link
> Native quebrado) descoberto durante o lote 3: o teste usa driver fresco por
> caso (como o CLI — 1 processo/compilação).

## Alvos fora da matriz (Android / WebAssembly)

A matriz cobre os 4 alvos de execução de programa (JVM/Native/Script/KofJS).
Dois alvos nomeados na plataforma **não** entram nas células — cada um por um
motivo diferente, ambos honestos (R6):

- **`android`** — não é um backend de execução: é **empacotamento do app
  inteiro**. Compila no pipeline JVM e produz APK via SDK oficial (d8 → aapt2
  → zip → zipalign → apksigner; ver `CmdBuild.runApkPipeline`). A matriz de
  conformidade **linguagem×target** já vale para o bytecode JVM que o APK
  empacota; o que Android acrescenta é toolchain de empacotamento, não
  semântica. Requisito de ambiente: `ANDROID_HOME` + build-tools 34 — sem
  SDK a CLI reporta o erro (nunca simula o APK). A compilação no target é
  coberta por `AndroidInteropE2ETest` (semântica JVM em `Target.ANDROID`);
  o pipeline de APK em si exige SDK e não tem E2E na suíte.

- **`wasm` / `kofwebassembly`** — **WASM001: ainda não existe**. Não há
  `Target.WASM`; `TargetMatrix.frontendGapFor` mapeia os nomes pedidos
  (`wasm`, `kofwasm`, `kofwebasm`, `kofwebassembly`, `webassembly`) ao gap
  **WASM001**, planejado na Fase 6 do plano de plataforma
  (`docs/development/future/PLATFORM-PLAN.md`). Os dois caminhos da CLI
  diagnosticam igual: `--frontend=wasm`/`kof.toml` →
  `TargetMatrix.parse` com o gap; `--target=wasm` (flag legado) → a mesma
  mensagem via `KofCliSupport.parseTarget`. Nunca compila como JVM por
  engano. Prova: `TargetMatrixTest.wasmGapMessagePointsToRealPlanPath` +
  `SelectTargetsTest.wasmFrontendIsHonestGap`.

## Notas de método

- **Oráculo:** a saída esperada é a do **comportamento documentado**
  (corpus `training/`/`learn/` + `docs/backend-parity.md`), não "o que o
  JVM imprime" — onde o JVM diverge do documento, é bug do JVM, não
  do target.
- **Native x86_64** é o "runtime de referência" do Native; riscv64/aarch64
  herdam via `translateRiscvToAarch64` e são cobertos por
  `NativeRiscv64E2ETest`/`NativeAarch64E2ETest` (20/20 cada) — o follow-up
  desta matriz é estender os casos aqui para qemu.
- **Script = JVM no interpretador** (`runFile(f, Target.JVM)`/`SCRIPT`):
  por construção roda a MESMA IR otimizada do frontend — divergência
  Script×JVM-compilado é bug do interpretador OU do lowering (ambos
  têm gate de paridade próprio: `KofInterpreterParityTest` 16/16).
- **Riscar célula** = editar a matriz + commit; o teste é a prova, a
  matriz é o índice (R6: o teste falha antes da doc divergir).
