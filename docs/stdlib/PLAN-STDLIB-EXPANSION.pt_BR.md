[English](PLAN-STDLIB-EXPANSION.md) | [Português](PLAN-STDLIB-EXPANSION.pt_BR.md)

# Plano — Universal Standard Library (STDLIB)

**Dono:** lane KOFSCRIPT (fixes-for-kofagent) · **Status:** CONCLUÍDO (14/09) — S0–S13 concluídos e consolidados em docs/stdlib/stdlib.md; pendências de decisão movidas para DECISIONS.md §D-STDLIB · **Briefing:** maintainer 08/09 (universal stdlib, multitarget, anti-microdependência)

## 0. Arquitetura real (mapeada 08/09 — NÃO inventar paralela)

Kof já tem o mecanismo exato que o briefing pede ("API comum → implementação por target"):

```
Kof<Domain>.java (raiz dev/kof/compiler)     ← dispatch + tipagem (record <D>Call)
   └ MethodCallTyper.java (380-413)          ← hook: <namespace>.fn(...) por prefixo
        ├ JVM:    jvm/JvmString<Domain>Runtime.java (source gerado) +
        │         jvm/JvmRuntimeCallDescriptors.java (descritores) → interpretador herda
        │         (KofInterpreter.dispatch → JvmRuntime.hasRuntimeFn) = 2 targets de 1
        ├ Native: runtime/Runtime<Domain>.java (x86_64 ASM) +
        │         nat/NativeRiscvAsmRtB*.java (riscv64; aarch64 = tradutor)
        └ JS:     js/JsRuntimeUi<Domain>.java (export kofCamelCase) — bundle kof-runtime.mjs
Teste: Kof<Domain>Test.java (padrão KofValidationTest: JVM+Native+JS, 136 linhas)
Gate:  ConformanceMatrixTest + matriz docs/bugs-and-gaps/conformance-matrix.md
```

Precedente exato: `validation` (G4, `KofValidation.java` 77 linhas + 3 backends + teste).
Idiom da API: **namespace-qualificado** (`validation.required`, `security.hash`) — o
briefing aceita ("adapte à arquitetura real"). Então: `math.clamp(...)`,
`strings.slugify(...)`, `uuid.v4()`, `encoding.base64Encode(...)`, `time.addDays(...)`
— NÃO top-level solto (colidiria com o modo estabelecido).

## 1. O que JÁ EXISTE (não duplicar)

- `json.*` (G0), `io`/`File`/`Path` (KofIo), `http.*`, `db.*`, `config.*` (inclui `env`),
  `cache.*`, `log.*`, `mq.*`, `orm.*`, `web.*`, `ui.*`, `time` (now/sleep/interval/cancel),
  `crypto`/`security`/`jwt`/`secrets`/`passwords` (KofSecurity: hash/verify/needsRehash/
  constantTimeEquals/randomHex/randomInt/encryptAesGcm/csrf/session/rateLimit...),
  `validation` (required/notBlank/minLength/maxLength/lengthBetween/inRange/min/max/
  matches/isEmail/isUrl/isInt/isLong).

## 2. Lacunas reais (o briefing ∩ o que falta)

| Namespace | Funções novas (P0 primeiro) |
|---|---|
| `math` | clamp · sign · abs · isEven/isOdd · isPositive/isNegative/isZero · lerp · percentage · roundTo · isInteger/isDecimal · parseInt/parseLong/parseDouble + OrNull/OrDefault · pow/sqrt |
| `strings` | ~~capitalize/uncapitalize~~ ✅ (uncapitalize FEITO 09/09 S11, 5 alvos) · toCamelCase/toPascalCase/toSnakeCase/toKebabCase (com HTTPServer/XMLParser) · slugify · truncate · repeat · reverse · count · removeWhitespace/normalizeWhitespace · padLeft/padRight · isNumeric/isInteger/isDecimal/isAlpha/isAlphaNumeric/isUpper/isLower/isAscii · escapeHtml/unescapeHtml/escapeJson · lines/words · ~~indent/dedent~~ ✅ (FEITO 11/09 S3.3, 5 alvos) |
| `uuid` | v4 · ~~isUuid~~ (FEITO S3b-ext 09/09, 5 alvos — UUID001 fechado no merge beta→main 10/09) · ~~v7~~ (FEITO S3b.2 10/09, 5 alvos — RFC 9562) · ulid/isUlid (P1) |
| `encoding` | base64Encode/Decode · base64UrlEncode/Decode · hexEncode/Decode · urlEncode/Decode |
| `random` | randomDouble · randomBoolean · randomChoice · randomString · randomBytes (secure split: `random.*` inseguro vs `security.*` seguro — já documentado) |
| `validation` (ext) | ~~isCpf/formatCpf~~ (formatCpf FEITO S12 09/09, 5 alvos) · ~~isCnpj~~ · formatCnpj FEITO S12b 09/09 (5 alvos) · ~~isCep/formatCep~~ (formatCep FEITO S12 09/09, 5 alvos) · ~~isPis/isNis~~ · isIp/isIpv4/isIpv6/isMac/isDomain/isPort · isCreditCard (FEITO S6b)/creditCardBrand/last4 (Luhn — FEITO 01/10 S12c, 5 alvos) · isStrongPassword/passwordScore |
 | `time` (ext) | addDays/~~addMonths~~ (FEITO 01/10, 5 alvos — S7a-ext: clamp de fim de mês, paridade inteira pura)/~~addYears~~ (FEITO 01/10, 5 alvos — S7a-ext2: clamp de fim de mês no ano destino, paridade inteira pura) · daysBetween/hoursBetween · ~~startOf/endOf~~ (day/week/month/year) (FEITO 01/10, 5 alvos — S7a-ext3: composto dos primitivos com paridade provada, byte-identidade inteira pura) · isLeapYear · daysInMonth · ~~age~~ (FEITO 01/10, 5 alvos — S7-wedge-ext: anos completos, paridade inteira pura) · formatDate/parseDate · isToday/~~isWeekend~~ (FEITO S7-ext 09/09, 5 alvos) · today |
| `net` (novo, P2) | **6 escalares** `net.scheme/host/port/path/query/fragment(STR)->STR` + `queryEncode/queryDecode` — ver §4 (decisão S8, 09/09) |
| `util` (P2) | debounce/throttle · retry (backoff/jitter) |

**Não** (regra do briefing §48/§49 + R6): browser/DOM/storage/clipboard = lane KofUI
(kof.ui já existe); crypto caseiro = proibido (JCA já); `Result`/`Option` = não existe
na semântica congelada (null-safety + throw são o mecanismo).

## 3. Degraus commitáveis (cada um: dispatch + 3 backends + teste Kof<Domain>Test + matriz + doc)

- **S0** este plano + claim DOING
- **S1a** `JvmRuntimeCallDescriptors` 504→≤500 (split por domínio — pré-requisito do gate) — **FEITO 08/09** (`ea0046c4`: 504→354, `JvmRuntimeReturnDescriptors` extraído; medido 13/09: **414 ≤500**, fora do baseline `check_500-baseline.txt`)
- **S1** `math` (P0-a) — 4 targets — **FEITO 08/09** (math clamp/abs/sign/min/max/isEven/isOdd/isPositive/isNegative/isZero `d0b829a1`; Double segue em S1b/S1b.1/S1b.2; `KofMathTest` 15 testes)
- **S13a** `math.parseInt/parseLong/parseDouble` (P0, §2+línea 41) — **FEITO 13/09** (lane development .18): fachada de namespace sobre as runtime fns EXISTENTES `kof_string_to_int/long/double` nos 4 backends (regra 2, zero runtime novo; riscv/aarch = B30/B31, golden byte-idêntico). Contrato JDK com trim; inválido/overflow LANÇA (Or* = S13b). Prova: `KofMathTest.parse{Jvm,Native,Js,CrossArch,TypeGuardRefused}` (guard SEM025 no typer) + célula `stdmathparse` matriz 4 targets + `KofScriptStdlibParityTest.mathParseParity`. Fix de rota JS no `JsRuntimeOps.handleRuntimeOp` (FUNCTION args vs METHOD receiver — emit idêntico ao case .toInt() de JsCallEmitter). Suíte 1698/0/0.
- **S13c `math.parse{Int,Long,Double}OrNull`** — **BLOQUEADO (regra 6, decisão da mantenedora)**: §125 congelou nullable de primitivo (`Int?` folda p/ `()I`, `== null` = `iconst_0` — verificado por javap nos 4 alvos 13/09). OrNull seria idêntico a `parseOrDefault(s, 0)` = código morto (Q7). Requer nullable-primitivo real (mudança de contrato). **S7e time (D-STDLIB): `todayIso/formatDateIso/isToday` FEITO 13/09** (5 alvos; prova `KofTimeE2ETest` S7e 18/18 + matriz `stdtime3`; suíte 1733/0/0). **S7f `hoursBetween` FEITO 13/09** (D3 floor simétrico, 5 alvos; fix de causa raiz no emit x86 genérico 7+ args; prova `KofTimeE2ETest.hoursBetween*` 23/23 + matriz `stdtime4` + parity Script; suíte 1758/0/0). **S7g `parseDateIso` FEITO 13/09** (D4 serial daysFromEpoch, inválido ⇒ 0, 5 alvos; serial fecha com hoursBetween/daysBetween; prova `KofTimeE2ETest.parseDateIso*` 28/28 + matriz `stdtime5` + parity Script; suíte 1764/0/0). **S7h `tzOffsetSeconds` FEITO 13/09** (D1 fuso do host; JVM/JS/SCRIPT paridade por oracle JVM; **Native = gap honesto TIME003** — recusa com diagnóstico; prova `KofTimeE2ETest.tzOffset*` 30/30 + matriz `stdtime6` PARTIAL native + parity Script; suíte 1767/0/0). **FILA D-STDLIB TIME FECHADA 13/09 — 6/6 itens executados.**
- **S13b** `math.parse{Int,Long,Double}OrDefault` (P0, briefing §43) — **FEITO 13/09** (lane development .18): falha de parse DEVOLVE o default (nunca lança). Backends: JVM try/catch (`JvmStringCoreRuntime` + descritores I/J/D), JS wrapper (`JsRuntimeUiStdlib`), x86 wrapper c/ handler local no `kof_exc_chain` (`RuntimeStringParseOrDefault` NOVO — mecanismo try/catch Kof), riscv **B41** (`NativeRiscvAsmRtB41` NOVO; aarch herda via tradutor; B34–B40 = outras lanes — regra 8). Fix de CAUSA RAIZ no `KofStd`/`ExpressionMethodCallLowerer`: widening genuíno (I2L/I2F/I2D/...) nos args do std call — literal Int em param Long crashava COMPUTE_FRAMES (NegativeArraySize) no JVM. Fix de rota JS: os cases `kof_string_to_*` (S13a/S13b) estavam DENTRO do bloco `kof_web_` (inertes) — movidos p/ o fluxo principal. Prova: `KofMathTest.parseOrDefault{Jvm,Native,Js,CrossArch}` (24 testes) + célula `stdmathparseord` + `mathParseOrDefaultParity`. Suíte 1720/0/0. Residual: §175 (vazio Double = 0.0 no Native — paridade do parse BASE, fila própria).
- **S2** `strings` (parte 1: cases/slug/pad/reverse/count) — 4 targets — **FEITO 08/09** (predicados isAlpha/isNumeric/isAlphaNumeric/isAscii/isUpperCase/isLowerCase/count + conversores capitalize/reverse/repeat/truncate; `KofStringsTest` 16 testes)
- **S3** `strings` (parte 2: escapes/lines/words/indent/isX) — **FEITO** (escapeJson `aef9cf23` + indent/dedent nos 5 targets `ca1e3d36`; `KofStringsIndentDedentTest` 4 testes)
- **S4** `encoding` (hex/url/base64/base64Url) **FEITO 08/09** — matriz `stdenc`
  4 alvos; base64* nos **4 alvos — ENC002 fechado 09/09** (port riscv B23; spec tolerante única). ⚠️ Nota:
  o runner JS do projeto (GraalJS embutido) NÃO tem `TextEncoder/TextDecoder` —
  UTF-8 codificado à mão em `JsRuntimeUiStdlib`. `uuid` (v4/v7/ulid) segue em S3b.
- **S5** `random` novo namespace + ext `validation` BR (CPF/CNPJ/CEP/PIS/NIS com
  checksum reutilizável interno — §18 briefing)
  - **PARIDADE kof-script FEITA 10/09:** `KofScriptStdlibParityTest` (5
    testes) prova interpretador (Target.SCRIPT) × JVM compilado para toda a
    stdlib nova da sessão — uncapitalize, formatCpf/formatCep/formatCnpj,
    isUuid (+v4), isWeekend, fachada random (contrato/faixa, nunca valor
    sorteado). Sem GAP: o interpretador resolve kof_* por reflexão no MESMO
    KofRuntime gerado (paridade por construção, R5); o teste é a prova, não
    a memória. Roda no gate de kof-script (25 -> 30).
  - **S12b FEITO 09/09:** `validation.formatCnpj` nos 5 alvos — 14 dígitos
    => NN.NNN.NNN/NNNN-NN (canônico IBGE único). Arquivos NOVOS (gates
    estouravam): x86 RuntimeValidationFmtBr (Br 455/500; emit após Br em
    NativeRuntime — usa kof_br_digits dele) + riscv B29 (B12 484/500; append
    NativeRiscvAsm). LIÇÕES de S12 respeitadas (frame -48, len@16/20=0,
    movl não leal). KofValidationTest formatCnpj* 5/5 (classe 34/34).
    **formatPis NÃO entra:** máscara 11-dígitos sem forma IBGE única
    (3.5.2.1 vs 3.4.3.1) = decisão de design — nota, não código (regra 6).
  - **S3b-ext FEITO 09/09:** `uuid.isUuid(STR->BOOL)` nos 5 alvos — shape
    RFC 4122 (36; hífens em 8/13/18/23; resto hex maiúsculo/minúsculo). Não
    valida versão/variante. JVM JvmUuidRuntime + JS JsRuntimeUiUuid
    (fragmentos novos — gates ≤500); x86 RuntimeUuid; riscv B25 (LIÇÃO:
    upper-bound de banda com bltu é EXCLUSIVO — 58/71/103, não 57/70/102;
    'e'/'9' eram rejeitados — isolado no trace x86-ok/riscv-fail). KofUuidTest
    isUuid* (JVM/JS golden + cross assert v4()-paridade).
  - **S7-ext FEITO 09/09:** `time.isWeekend(y,m,d)` nos 5 alvos — wrapper
    `dayOfWeek >= 6` (ISO 1=seg..7=dom; data inválida => dayOfWeek 0 => false,
    gating automático). JVM JvmTimeRuntime + descritor (III)Z (não I —
    boolean real; NoSuchMethodError descoberto no E2E); JS kofTimeIsWeekend
    (wrapper em JsRuntimeUiWeb); x86 wrapper `call kof_time_dayOfWeek` +
    cmpl $6; riscv B14 wrapper — LIÇÃO: wrapper riscv SEMPRE salva `ra`
    (jalr do call clobbera ra → ret volta ao próprio corpo = loop infinito;
    isolado via qemu -d in_asm); aarch traduz. KofTimeE2ETest calendar*
    estendidos (JVM/JS/x86 println + cross assert).
  - **S12 FEITO 09/09:** `validation.formatCpf/formatCep` nos 5 alvos —
    pontuação BR (11 dígitos => DDD.DDD.DDD-DD; 8 => DDDDD-DDDD; senão
    original, nunca lança — face leniente; reusa kof_br_digits já portada).
    x86 RuntimeValidationBr (movl $34/$39, não leal — gas); riscv B12
    (frame -48: -40 desalinha PS; len em 16, 20=0); aarch traduz; JVM
    JvmStringValidationRuntime; JS JsRuntimeUiValidation (novo fragmento,
    Crypto 489/500 sem espaço). KofValidationTest formatBr* (5 alvos).
  - **S11 FEITO 09/09:** `strings.uncapitalize` nos 5 alvos — espelho byte-a-
    byte do capitalize (dispatch único KofStrings; JVM JvmStringWsRuntime, JS
    kofStringsUncapitalize, x86 RuntimeStringsConv derivado, riscv B7, aarch
    traduzida; KofStringsTest#uncapitalizeAllTargets golden 3 + assert qemu 2).
  - **S10a/b FEITO 09/09:** `randomInt(bound)`/`randomBoolean`/`randomString(n,
    alphabet)` nos 5 alvos (entropia só do SO — getrandom/SecureRandom/crypto;
    x86 alias `kof_sec_random_int`, riscv B27/B28 + aarch translator, JS
    kof_platform+crypto fallback, JVM SecureRandom). `randomChoice` NÃO entra:
    idiom `l[randomInt(l.size)]` (a regra — complexidade a quem usa).
    `randomBytes`/`randomChoice` binário = DD-STDLIB-01
    (`docs/stdlib/DD-STDLIB-01-array-returns.md`, **DECIDIDO 13/09 (opção 6a) +
    IMPLEMENTADO nesta unidade**: `random.randomBytesHex(n)->String` como
    alias aditivo de `random.hex`, mesma runtime fn `kof_random_hex`, 5 alvos;
    `randomBytes` binário RESERVADO; choice = idiom) — retorno Array
    na camada de dispatch é decisão de design, não edição.
  - **S10 face main (845284e5 + fix §92, merge beta→main 10/09):**
    `random.double/boolean/int/hex` — as DUAS faces convivem no dispatch
    (`KofRandom.staticMethod` aceita `randomInt` E `int`, etc.; mesma runtime
    fn, retrocompat aditiva). O `double` fechou o FLT001 p/ a família random
    no riscv/aarch (B27: fcvt.d.l/fdiv + tradutor ucvtf/fld), após o fix do
    divisor 2^52→2^53 (§92). Borda documentada: `hex(n<=0)` → null em JVM/JS,
    `""` em x86/riscv (callee kof_sec_random_hex pré-existente — divergência
     registrada na matriz, não silenciosa). KofRandomTest 12/12.
- **S6** ext `validation` network (IPv4/IPv6/mac/domain/port) + Luhn — **FEITO** (S6a/S6b, `KofValidation.java` isIpv4/isIpv6/isMac/isPort/isDomain/isCreditCard + RuntimeValidationNet; matrizes stdvalidation*/stdluhn/stdipv6/stddomain).
- **S7** ext `time` (add/diff/boundaries/format) — **PARCIAL (degrau aberto):**
   - **FEITO** calendário `isLeapYear/daysInMonth/dayOfWeek/daysBetween` (4 alvos;
     matriz stdtime) + `isWeekend` (S7-ext, 5 alvos). **S7a** `addDays`/`diffDays`
     em data ISO (String) JVM+Script via `java.time` (10/09 — `JvmTimeRuntime`
     reusam `kof_time_validDate`/época civil). **S7b** JS (10/09 —
     `JsRuntimeUiWeb`, MESMO algoritmo civil do wedge, SEM `Date` => paridade
     byte-idêntica). **S7c** native **x86** (10/09 — `runtime/RuntimeTimeIso.java`:
     `.Lka_parse2` + `.Lka_civil` (round-trip EXAUSTIVO 1..9999) + alocação String
     no asm; harness C 200k fuzz 0 fails). Matriz `stdtime2` (JVM+Script+JS+x86;
     riscv/aarch=TIME002) + `KofTimeE2ETest...Time002Gate`.
   - **S7d FEITO 11/09 — TIME002 FECHADO** (`addDays`/`diffDays` em riscv64/
     aarch64, fatia **B33**): port 1:1 do spec x86 (`RuntimeTimeIso`) —
     `.Lu8_parse2` (formato + dígitos + kdv_valid) / `.Lu8_civil` (inversa
     Hinnant) / `.Lu8_put4`/`.Lu8_put2`, reusando `kdv_valid`/`kdv_epoch` da
     B14; `divl`→`divu/remu` (z≥0 pelo guard -719162..2932896); aloc String =
     padrão kof_alloc riscv; aarch herda via tradutor. Gate `KofTime.supportedOn`
     removido (5 alvos). **LIÇÕES riscv do port (registradas no código):**
     (1) `call` no riscv é `jalr ra` — sobrescreve o `ra` do caller (não é a
     pilha do x86); helper que termina em `call h; ret` precisa de **tail-jmp**
     `j h` senão o `ret` volta ao próprio corpo = loop infinito (pegado no
     trace qemu do parse2). (2) `kdv_valid` faz `call daysInMonth` e **clobbers
     s0** — nenhum valor vivo em `s0` entre calls (tudo em slot de pilha,
     lição B14). (3) `blt`/`bge` SIGNED no bounds epoch (diferença de `bltu`
     unsigned). Prova: golden byte-idêntico de 9 linhas (oracle JVM medido)
     sob qemu-riscv64 + qemu-aarch64 (`KofTimeE2ETest` invertendo o antigo
     `Time002Gate`); KofTimeE2ETest 11/11, matriz stdtime2 + E2E riscv/aarch.
   - **ABERTO**: `format`/`boundaries` (forma de API — `format(date, "yyyy-MM-dd")`
     vs funções escalares `yearOf`/`monthOf`… — decisão de superfície da
     mantenedora, como a família `net`/`validation`).
- **S8** `net` url/query parse/encode — **FEITO** (S8 decisão §4; KofNet 6 escalares + queryEncode/Decode, RuntimeUri, stdnet, NET001 riscv fechado B24).
- **S3b-wedge (uuid.v4) + S4 COMPLETO FEITOS 08/09:** uuid shape-verified 3 targets (SECN000 cross-arch fechado 09/09 — B25 getrandom ecall); encoding hex/url/base64/base64url (matriz stdenc 11 campos × 4; gates ENC002 base64* e SECN000 uuid nos cross). LIÇÃO JVM-runtime: nunca checked exceptions no KofRuntime gerado (SecureRandom new, não getInstanceStrong).
- **S1b.1 FEITO (10/09):** `math.lerp(a,b,t)`/`percentage(part,total)` (Double->Double) + `math.isInteger/isDecimal(DOUBLE)->Bool` — escalares Double **puros** (SSE2 `subsd/mulsd/addsd/divsd` + `cvttsd2si/ucomisd`; 0x7ff exp = NaN/Inf, exp>=0x433 = |v|>=2^52). JVM (`JvmStringMathRuntime`) + SCRIPT (reflexão) + JS (`kofMathLerp/Percentage/IsInteger/IsDecimal` — Bool=1/0, chokepoint §93) + x86 (`RuntimeMath`; arg/ret **bits crus via rax** = cavalga o generic path, zero mudança em NativeX86Calls — ao contrário do sqrt que precisava xmm). Guard de tipo: só Double (Int NÃO alarga em silêncio — SEM025). **`pow`/`roundTo` ADIADOS**: `pow` exige libm (o link nativo é `-lc` só — mudar o link = decisão de contrato da mantenedora — NÃO altero o NativeAssembler sem decisão). **✅ DECIDIDO 13/09 (opção 7a): link `-lm` APROVADO — implementar `pow`.** Nota: a ratificação da mantenedora cobre SÓ `pow`; o "+ `roundTo` via floor asm" era anotação de agente neste plano — `roundTo` NÃO foi aprovado (superfície/assinatura indefinida = regra 6, aguarda decisão).. **S1b.2 FEITO 13/09 (outro agente, `7f174a6f` — dispatch+shim, sem E2E/matriz ainda):** `math.pow(DOUBLE,DOUBLE)->Double` em `KofMath` + `RuntimeMath.kof_math_pow` (x86 `pow@PLT`, `-lm` sempre ligado; JVM/JS `Math.pow`; riscv/aarch MATH001). PROVA: harness C isolado 18/18 (golden = oracle JVM medido, nunca memória — 2.675-style fica fora) + `KofMathTest` doubleOpsJvm/Native/Js + **MATH001 FECHADO 11/09: `sqrtCrossArch`/`doubleOpsCrossArch` rodam os MESMOS golden byte-idênticos sob qemu-riscv64 + qemu-aarch64** (fatia B32: fsqrt.d/fadd/fsub/fmul/fdiv/fcvt.l.d/fcvt.d.l/feq.d; aarch herda via tradutor — `fsqrt` separado + `fcvt.w/l→fcvtzs` corrigidos aqui); matriz `stdmathdouble` (15 saídas) + doc-gate. KofMathTest 11/11. **ACHADO irmão (bug 101):** o `NE` de Double cross riscv era `fle+snez` (dizia `NaN!=NaN`=false, divergia do x86/JVM/JS=IEEE true) — corrigido p/ `feq+seqz` na mesma prova (necessário: o golden DBL_SRC usa `NaN!=NaN`). Divergência RELACIONAL (`<`/`<=`/`>=` com NaN: x86=JVM via dcmpg quirk, riscv=IEEE flt/fle) é PRÉ-EXISTENTE, operadores congelados (regra 6) → registrada bug 101, NÃO alterada (o número 100 foi tomado pelo bug Char-método-String na mesma data).
- **S1b.3 FEITO 14/09 (DECISIONS §3, dono 192.168.100.18):** `math.roundTo(value: Double, decimals: Int) -> Double` — meio-para-longe-do-zero (âncora C `round()`) por escala decimal determinística (âncora Java `BigDecimal.setScale`, mas **aritmética**, não decimal-string): `p=10^|d|` por multiplicação repetida (cada passo 1 op IEEE corretamente arredondada → byte-idêntico 5 alvos); `d>=0`: `roundHalfAway(v*p)/p`, `d<0`: `roundHalfAway(v/p)*p` (decimals negativo arredonda p/ dezenas/centenas); `|d|` satura em 308; overflow de `v*p` → devolve `v`. Sem libm. Backends: JVM (`JvmStringMathRuntime`, reflexão p/ SCRIPT), JS (`kofMathRoundTo`), x86 (`RuntimeMath`), riscv (fatia B32; aarch via tradutor). Guard de tipo: `decimals` precisa ser `Int` (SEM025). Contrato travado: `roundTo(2.675,2)==2.68`. Prova: `KofMathTest.roundTo{Jvm,Native,Js,CrossArch}` + `roundToTypeGuardRefused` + `ConformanceMatrixTest.stdmathround` (4 targets, doc-gate) + `KofScriptStdlibParityTest.mathRoundToParity`.
- **S1b-wedge FEITO (10/09):** `math.sqrt(DOUBLE)->Double` — PRIMEIRO Double da namespace `math` (abre o caminho p/ lerp/percentage/roundTo/parse*/pow). JVM (`Math.sqrt`) + SCRIPT (reflexão) + JS (`Math.sqrt`) + x86 (`sqrtsd %xmm0`, arg/ret pela convenção de bits `popq %rax; movq %rax, %xmm0; call; movq %xmm0, %rax; pushq %rax` — precedentes `kof_json_encode_double`/`kof_random_double`). NaN em <0 = IEEE (paridade medida nos 3). **MATH001 FECHADO 11/09:** riscv64/aarch64 — fatia B32 `fsqrt.d` (+ aarch translator `fsqrt` separado); prova `KofMathTest.sqrtCrossArch` (8 linhas golden byte-idênticos sob qemu, invertendo o antigo `sqrtGatedOnCrossArch`). **ACHADO (bug 94):** o interpretador faz `==` de Double via `numEq`→`Double.compare` → `NaN == NaN` = `true` (divergência dos 3 compilados, IEEE) — semântica `==` congelada (regra 6), registrado em known-bugs + célula PARTIAL na matriz; o wedge NÃO toca no interpretador. ⚠️ Bug 44: matriz/testes usam SOMENTE comparações Bool (`sqrt(9.0)==3.0`), nunca `println` de double cru. Prova: `KofMathTest.sqrtJvm/sqrtNative/sqrtJs` (8 linhas byte-idênticos) + `sqrtGatedOnCrossArch` (MATH001 × 2) + `ConformanceMatrixTest.stdsqrt` (6 outputs; jvm/native/js + doc-gate) + harness C isolado (8 vetores, 0 fails — ANTES da suíte).
- **S3b.1 FEITO (10/09):** `uuid.isUuid(STR)->Bool` — predicado de **forma** 8-4-4-4-12 (36 chars, traços em 8/13/18/23, hex min/maiúsculo; version/variant NÃO verificadas). JVM+SCRIPT+JS+x86 sem gate (byte-scan plano; x86 validado no harness C isolado — 12 vetores + null, 0 fails — ANTES da suíte, lição S7c). **UUID001 gate (R6):** riscv64/aarch64 = fatia B própria pendente (mesma condição de parada de S7c-1 — sem cross-assembler/qemu na lane; spec x86 pronta em `RuntimeUuid`; NÃO escrever asm sem montar/rodar). Prova: `ConformanceMatrixTest.stduuidform` (7 outputs × 4 targets, doc-gate) + `KofUuidTest.isUuidShapeJvmJsNative` (JVM==JS==x86 byte-idênticos; última linha `isUuid(uuid.v4())` — paridade com o próprio gerador) + `isUuidGatedOnCrossArch` (UUID001 nos 2 alvos).
- **S3b.2 FEITO (10/09):** `uuid.v7()->String` — RFC 9562 time-ordered UUID (48 bits unix ms timestamp big-endian + version 7 + variant 10xx + entropia criptográfica). JVM (`JvmUuidRuntime`) + JS (`JsRuntimeUiUuid`) + Native x86_64 (`RuntimeUuid` via `kof_now` e `kof_sec_random_hex`). **UUID002 gate (R6):** riscv64/aarch64 rejeitados honestamente no compilador até port dedicado. Prova: `KofUuidTest` (`uuidV7Jvm`, `uuidV7Native`, `uuidV7Js`, `uuidV7MonotonicOrderJvm`, `uuidV7GatedOnCrossArch`, `isUuidShapeJvmJsNative`).
- **S1–S2b.2 FEITOS 08/09:** math(9) · strings predicados(8: isAlpha/isNumeric/
  isAlphaNumeric/isAscii/isUpperCase/isLowerCase/count) · strings conversores(4:
  capitalize/reverse/repeat/truncate — 1º caso de alocação de String no runtime,
  fatias riscv B7/B8; gap NAT-STR01 p/ reverse UTF-8 no Native). Nota de API:
  `validation.min/max` (binários, G4) ≠ `math.min/max` (aritméticos) — namespaces
  distintos, sem colisão; documentar em learn.
- **S9** matriz stdlib em docs/stdlib/stdlib.md + learn/39-stdlib + training/idioms (math/
  strings/validation) + benchmarks mínimos (clamp/slugify) se aplicável — **FEITO** (matriz `std*` no ConformanceMatrixTest + `docs/stdlib/stdlib.md` §STDLIB + `learn/39-stdlib.md` + `training/idioms/stdlib.md`/`strings.md`).

Cada S = suíte completa verde (com `-Dmaven.test.failure.ignore=true`) + DOING atualizado.

## 4. Decisões de API (estáveis antes de codar)

- Nomes: `is<Cpf>`, `format<Cpf>`, `normalize*` (§42 briefing) — já é o padrão do repo.
- Falha de parse = `OrNull`/`OrDefault` (briefing §43 ≡ `String?` de Kof).
- Nativos: funções puramente aritméticas em JS/Java/ASM sem regex quando trivial
  (clamp/sign/abs inline-áveis? NÃO — manter runtime fn única p/ paridade byte-idêntica
  entre targets, que é o que a matriz prova).
- riscv64/aarch64: enquanto bug 59 abre, novos símbolos seguem o padrão SECN000?
  NÃO — validation já tem riscv real (B3); copiar o padrão B3 (asm puro, sem libc).
  Gate de paridade = ConformanceMatrixTest (riscv só roda via qemu no CI de NATIVE002).

## 4. Decisão S8 — forma de `net` (09/09)

**Escolhida: 6 funções escalares** (`net.scheme(s)` … `net.fragment(s)`, todas
`STR->STR`), **NÃO** um record `Uri(...)` retornado por `net.urlParse`. Motivo
técnico, não estilístico: **nenhuma função do runtime asm devolve objeto
estruturado hoje** (precedente KofHttp: "the body is returned as a String";
records são classes geradas pelo compilador, não alocáveis pelo asm x86/riscv).
Um `urlParse` que retorna record seria o PRIMEIRO objeto alocado em runtime no
Native — escopo próprio, multi-sessão, e exige design separado (IR de record
allocável). A forma escalar replica EXATAMENTE o precedente da família
`validation` (`isIpv4(STR)->Bool`, …): N funções sobre a mesma String, cada uma
byte-scan puro, portável aos 4 targets sem novo mecanismo. É **totalmente
aditiva** (namespace novo → nada congelado em jogo; regra 6 satisfeita).

**Semântica v1 (RFC 3986 subset, escopo honesto travado na matriz, R6):**
- `scheme`: `[A-Za-z][A-Za-z0-9+.-]*` antes do primeiro `:`; senão `""`.
- authority só após `//` ; `userinfo@` ignorado (host = após o último `@`).
- `host`: até o primeiro `:` do authority ou fim dele; `port`: após esse `:`
  (String, não Int — tolerante/sem parse, igual política validation).
- **v1 SEM literal IPv6 entre colchetes** (`[::1]` cai no host como está) —
  documentado, como isIpv6/isDomain (sem zona, sem forma mista).
- `path` até `?`/`#`; `query` após 1º `?` até `#`; `fragment` após 1º `#`.
- campo ausente ⇒ `""` (natural "sem substring"); `null` ⇒ `null` (paridade
  com todas as STR->STR da stdlib). Entrada malformada ⇒ melhor esforço por
  campo, NUNCA lança (família validation).
- `queryEncode`/`queryDecode`: percent-encoding de chave/valor reusa EXATAMENTE
  `encoding.urlEncode/urlDecode` (não re-implementar — regra 2); `net` é uma
  fachada de intenção (`net.queryEncode` == `encoding.urlEncode`).
