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
| float println | `0.3333333333333333` / `5.0` / `3.5` | DONE | DONE (bug 44 ✅ 10/09 x86) | DONE | PARTIAL (doc: `5` vs `5.0`) | `floatprint` |
| string unicode length/charAt | `4` / `233` / `café!` | DONE | DONE (bug 43 ✅ 10/09 x86) | DONE | DONE | `unicode` |
| string unicode astral (surrogate pair) | `4` / `55357` / `56832` / `98` | DONE | DONE (bug 43 ✅ 10/09 x86) | DONE | DONE | `unicode-astral` |
| string unicode substring (code units, fronteiras bem-formadas) | `afé` / `é` / `😀` / `3` / `b` | DONE | DONE (bug 43 ✅ 10/09 x86) | DONE | DONE | `unicode-substring` |
| string unicode indexOf/lastIndexOf (code units) | `6` / `-1` / `4` / `3` | DONE | DONE (bug 43 ✅ 10/09 x86) | DONE | DONE | `unicode-indexof` |
| string ops split/toLowerCase/trim | `4` / `hello world` / `x\|` | DONE | DONE | DONE | DONE | `strops` |
| map put/get/size | `1` / `2` | DONE | DONE | DONE | DONE | `map` |
| list empty/isEmpty/contains | `true` / `0` / `false` | DONE | DONE | DONE | DONE | `emptylist` |
| `null == null` / `!=` | `true` / `false` | DONE | DONE | DONE | DONE | `nulleq` |
| if-expr curto-circuito null | `iguais` / `nao-ne` | DONE | DONE | DONE | DONE | `nulleqshortcut` |
| set dedup/contains | `3` / `true` / `false` | DONE | DONE | DONE | DONE | `setdedup` |
| if-expression aninhada | `small` | DONE | DONE | DONE | DONE | `nestedif` |
| switch-expression `case ->` | `three` | DONE | DONE | DONE | DONE | `switchexpr` |
| if-expr heterogêneo Int/String (issue #57) | `1` | DONE | DONE | DONE | DONE (bug 69 corrigido) | `ifexpr-heterogeneous-direct` |
| switch-expr heterogêneo Int/String (issue #57) | `1` | DONE | DONE | DONE | DONE (bug 69 corrigido) | `switchexpr-heterogeneous-direct` |
| if-expr heterogêneo Int/Long (§70, crash de join) | `1` | DONE | DONE | DONE | DONE (bug 69 corrigido) | `ifexpr-intlong-direct` |
| if-expr heterogêneo Long/Double (§70) | `2` | DONE | DONE | DONE | DONE (bug 69 corrigido) | `ifexpr-longdouble-direct` |
| if-expr heterogêneo Int/null (§70) | `1` | DONE | DONE | DONE | DONE (bug 69 corrigido) | `ifexpr-intnull-direct` |
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
| stdlib kof.math (S1: clamp/abs/sign/min/max/isEven/isOdd/isZero + `==true`/`==false` §93) | `10` / `0` / `7` / `-1` / `3` / `8` / `true` / `false` / `true` / `true` / `false` | DONE | DONE | DONE | DONE | `stdmath` |
| stdlib kof.math (S1b: sqrt — primeiro Double; comparações Bool, NaN em <0 = IEEE; riscv/aarch = MATH001) | `true` / `true` / `true` / `true` / `false` / `true` | DONE | DONE | PARTIAL (bug 94: `numEq`→`Double.compare`, `NaN==NaN` true) | DONE | `stdsqrt` |
| stdlib kof.math (S1b.1: lerp/percentage/isInteger/isDecimal — Double puro, SSE2; subset determinístico, NaN só nos compilados via KofMathTest; riscv/aarch = MATH001) | `true` ×15 | DONE | DONE | DONE | DONE | `stdmathdouble` |
| stdlib kof.strings (S2a: isAlpha/isNumeric/isAlphaNumeric/isAscii/isUpper/isLower/count + `==true` §93) | `true` / `false` / `false` / `true` / `false` / `false` / `true` / `false` / `true` / `true` / `true` / `false` / `true` / `false` / `2` / `1` / `true` | DONE | DONE | DONE | DONE | `stdstrings` |
| stdlib kof.strings (S2b: capitalize/reverse/repeat/truncate/pad — ASCII) | `Hello world` / `1abc` / `321cba` / `kayak` / `ababab` / `hello` / `abc` / `007` / `ab---` | DONE | DONE | DONE | DONE | `stdstrings2b` |
| stdlib kof.validation (S12/S12b: formatCpf/formatCep/formatCnpj — pontuação BR, face leniente; formatPis NÃO entra — máscara ambígua = decisão) | `529.982.247-25` / `123` (no-op) / `01310-100` / `34.546.401/0001-63` | DONE | DONE | DONE | DONE | `formatBr*`/`formatCnpj*` (KofValidationTest; riscv/aarch sob qemu, assert) |
| stdlib kof.strings (S11: uncapitalize — espelho do capitalize, ASCII) | `hello World` / `hELLO` / `1abc` / `hello` | DONE | DONE | DONE | DONE | `uncapitalizeAllTargets` (KofStringsTest; riscv B7 + aarch sob qemu) |
| stdlib kof.strings (S2b.4: toCamelCase/toPascalCase/toSnakeCase/toKebabCase/slugify — word-split HTTPServer/XMLParser) | `http_server` / `xml_parser` / `helloWorld` / `HelloWorld` / `hello-world` / `hello-world-42` | DONE | DONE | DONE | DONE | `stdstrings2b4` |
| stdlib kof.validation BR (S5: isCpf/isCnpj/isCep/isPis — pesos aritméticos, mod-11 por subtração; + `==true`/`==false` §93) | `true` / `false` / `true` / `false` / `true` / `false` / `true` / `false` / `true` / `true` | DONE | DONE | DONE | DONE | `stdvalidation` |
| stdlib kof.validation rede (S6a: isIpv4/isMac/isPort — dotted-quad sem zero à esquerda; MAC 6 hex sep : ou - consistente; porta 1..65535) | `true` / `false` / `false` / `true` / `false` / `true` / `false` | DONE | DONE | DONE | DONE | `stdvalidationnet` |
| stdlib kof.validation Luhn (S6b: isCreditCard — dígitos extraídos, 12..19, soma de Luhn %10; 20+ dígitos => false) | `true` / `true` / `true` / `false` / `false` / `false` | DONE | DONE | DONE | DONE | `stdluhn` |
| stdlib kof.validation IPv6 (S6b.3: isIpv6 — subconjunto RFC 5952; '::' no máx uma vez; sem forma mista/zona) | `true` / `true` / `true` / `false` / `false` / `false` | DONE | DONE | DONE | DONE | `stdipv6` |
| stdlib kof.net (S8: 6 campos URI v1 + fachada query*) | `https\|host.io\|8443\|/p\|q\|f` / `/only/path\|onlyquery` / `a%20b%26c%3D1` / `a b&c=1` | DONE | DONE | DONE | DONE | `stdnet` |
| stdlib kof.strings unescapeHtml (S3.1b: 5 nomeadas + &#DDD;/&#xHH;→UTF-8; outro & LITERAL; 0/surrogate/overflow LITERAL) | `a&b` / `<x>` / `café` / `☃` / `&&` / `&notreal;` | DONE | DONE | DONE | DONE | `stdunescape` |
| stdlib kof.strings whitespace (S3.2: removeWhitespace/normalizeWhitespace — WS=9..13+32; >=128 não-WS; colapso p/ 1 espaço) | `abc\|Caféé` / `a b\|a b` / `\|[]` | DONE | DONE | DONE | DONE | `stdws` |
| stdlib kof.strings escapeHtml (S3.1: 5 entidades; >=128 cópia; null/"" => original) | `a&lt;b&gt;&amp;&quot;&#39;c` / `Café &amp; ç` / `&amp;amp;lt;` / `&lt;a href=&quot;u&quot;&gt;y&lt;/a&gt;` | DONE | DONE | DONE | DONE | `stdescape` |
| stdlib kof.strings escapeJson (S3.1c: corpo de literal JSON RFC 8259 — backslash dobra, aspas escape, ctrl 2-char/backslash-u, demais cópia; null/"" => original; golden 5 backends em KofStringsTest) | `plain` / `quote \" inside` / `back\\\\slash` / `a\\u0001b` | DONE | DONE | DONE | DONE | KofStringsTest |
| stdlib kof.validation domínio (S6c: isDomain — RFC 1123 labels, TLD>=2 letras, >=2 labels; v1 sem ponto final/IDN) | `true` / `true` / `false` / `false` / `false` / `false` | DONE | DONE | DONE | DONE | `stddomain` |
| stdlib kof.time (S7: isLeapYear/daysInMonth/dayOfWeek/daysBetween — calendário civil; ano<1 ou >9999 ou data inexistente => false/0; dia 1=seg..7=dom) | `true` / `false` / `true` / `false` / `29` / `28` / `30` / `0` / `4` / `3` / `0` / `60` / `-60` / `0` | DONE | DONE | DONE | DONE | `stdtime` |
| stdlib kof.* (S10–S12b, S3b-ext, S7-ext: paridade kof-script × JVM compilado — fachada random, format BR, uncapitalize, isUuid, isWeekend) | (asserts de contrato + golden; não-determinístico só via fachada) | DONE | DONE | — | — | `KofScriptStdlibParityTest` (kof-script, 5) |
| stdlib kof.uuid (S3b-ext: isUuid — shape RFC 4122, 8-4-4-4-12 hex, hífens 8/13/18/23; sem checar versão/variante) | `true` / `true`(maj) / `false`(sem traço/tam/g/empty) | DONE | DONE | DONE | DONE | `isUuid*` (KofUuidTest; riscv/aarch assert sob qemu) |
| stdlib kof.time (S7-ext: isWeekend — dayOfWeek>=6, wrapper nos 5 alvos; data inválida => false) | `true`(sáb) / `false`(qua) / `false`(inválida) | DONE | DONE | DONE | DONE | `calendar*` (KofTimeE2ETest; riscv/aarch assert sob qemu) |
| stdlib kof.time (S7a/b/c: addDays/diffDays em data ISO String — parse estrito YYYY-MM-DD, inválido => ""/0; JVM/java.time + JS algoritmo civil sem Date + x86 asm `RuntimeTimeIso`) ⁴ | `2024-02-29` / `2023-03-01` / `2025-01-01` / `2023-12-31` / `''` / `''` / `60` / `-60` / `0` | DONE | DONE ⁴ | DONE | DONE | `stdtime2` |
| stdlib kof.encoding (S4: hex + base64 + url + base64url — UTF-8 por bytes) | `4869` / `Hi` / `636166c3a9` / `café` / `TWFu` / `café` / `a%20b` / `café` / `ZmImTy0-Zg` / `fb&O->f` / `E` | DONE | DONE² | DONE | DONE | `stdenc` |

> ¹ **STRN001 FECHADO 09/09:** joinWords portado p/ riscv64 (fatia B15) + aarch64
> (mesmo asm traduzido) — paridade byte-a-byte com o x86_64 provada por diff do
> golden oracle no qemu (16 vetores, incl. delimitadores UTF-8 `>=128`).
> `KofStringsTest.wordConvertersClosedOnCrossArch`.

> ⁴ **TIME002 parcial (riscv64/aarch64)**: `addDays`/`diffDays` rodam em JVM/Script/JS e
> no native **x86** (`RuntimeTimeIso` — parse ISO + inversa civil Hinnant no
> asm; round-trip exaustivo 1..9999 + fuzz C 200k). O port riscv64/aarch64
> (fatia B) mantém o gate TIME002 nesses dois alvos (precedente NET001:
> x86 fecha primeiro); provado em `KofTimeE2ETest` (erro claro no compile).

> ³ **NET001 FECHADO 09/09:** `net.*` roda nos 3 nativos — x86 (RuntimeUri) +
> riscv64 (fatia B24) + aarch64 (mesmo asm traduzido); paridade byte-a-byte
> nos 17 vetores do oracle (`KofNetTest.netOnCrossArch`, qemu).

> ² `encoding.hex*`/`encoding.url*` (B10/B11) e `encoding.base64*`/`base64Url*`
> (B23, **ENC002 fechado 09/09** — port riscv com alfabeto aritmético + decode
> tolerante, spec única do x86/JVM/JS; `KofEncodingTest.base64RunsOnCrossArch`
> prova riscv+aarch sob qemu) rodam nos 3 nativos + JVM + JS + Script.
| stdlib kof.uuid (S3b: v4 — não-determinístico, SEM caso de matriz) | shape `xxxxxxxx-xxxx-4xxx-[89ab]xxx-xxxxxxxxxxxx` | ✅ assert | ✅ assert¹ | ✅ | ✅ assert (+riscv/aarch qemu) | `KofUuidTest` 4/4 |
| stdlib kof.uuid (S3b.1: isUuid — predicado de forma 8-4-4-4-12, hex min/maiúsc, version/variant não verificadas; riscv/aarch = fatia B25, UUID001 fechado no merge beta→main 10/09) | `true` / `true` / `false` / `false` / `false` / `false` / `true` | DONE | DONE | DONE | DONE | `stduuidform` |
| stdlib kof.random (S10/S10a/S10b — não-determinístico, SEM caso de matriz) | contrato `0<=randomInt(b)<b` / `randomBoolean∈{0,1}` / `randomString: len==n, chars∈alphabet` + face main `double∈[0,1)` / `hex: 2n chars, n<=0→null (JVM/JS; x86 →""` pré-existente da crypto lane) + bordas lenientes (`b<=0→0`) | ✅ assert | ✅ assert | ✅ | ✅ assert (+riscv/aarch qemu) | `KofRandomTest` 12/12 |

> `random.*` não entra na matriz equality (entropia — mesma razão do uuid):
> paridade provada por ASSERTS DE CONTRATO nos 5 alvos (JVM SecureRandom, JS
> kof_platform/crypto, x86/riscv/aarch getrandom(2)). **S10a 09/09** (beta):
> `randomInt`/`randomBoolean`. **S10b 09/09** (beta): `randomString(n,
> alphabet)` (borda leniente `""`). **S10 10/09** (main, fix §92): `double/
> boolean/int/hex` — o `double` chegou a riscv/aarch na fatia B27
> (fcvt.d.l/fdiv + tradutor ucvtf/fld), encerrando o FLT001 para a família
> random. As duas faces convivem no dispatch (retrocompat aditiva).
> `randomBytes`/`randomChoice` ficam S10c (retorno Array/objeto
> sem precedente na camada de dispatch — DD-STDLIB-01).

> `uuid.v4()` não entra na matriz equality (entropia): paridade provada por
> ASSERTS DE SHAPE nos 3 targets testáveis (JVM/Native-x86/JS: length=36,
> traços em 8/13/18/23, dígito 14='4', dígito 19∈{8,9,a,b}, unicidade de 2
> draws; **riscv64/aarch64 SECN000 FECHADO 09/09** — getrandom(2) via ecall
> (syscall 278, probe em ambos os qemu) na fatia riscv B25 + aarch translator;
> KofUuidTest.uuidV4CrossArch roda o shape+unicidade sob qemu nos dois).
> ¹ variant por MÁSCARA nos 5 backends (b[8]=(b[8]&0x3f)|0x80 ⇒ char ∈
> {8,9,a,b}) — x86 parity corrigida 09/09 com o fechamento do SECN000 (antes
> fixava '8', subset do RFC com distribuição divergente — regra 5).

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
| `spawn { return ... }` (lambda-literal com retorno, bug 46) + `await` | `42` | DONE | DONE | DONE | DONE | `spawnexpr-return` |
| 2 handles: cada `await` devolve o SEU | `2` / `11` | DONE | DONE | DONE (fix race 07/09) | DONE | `spawnawait-two` |
| channel mesma-thread (FIFO Int+String) | `s=11` / `ab` | DONE | DONE | DONE | DONE | `channel-samethread` |
| channel send-em-spawn + receive | `42` | DONE | DONE (bug 50 fix 09/09) | DONE | DONE | `channel-spawn` |
| channel 2 sends em spawn + 2 receives | `1` / `2` | DONE | DONE (bug 50 fix 09/09) | DONE | DONE | `channel-spawn-two` |

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
