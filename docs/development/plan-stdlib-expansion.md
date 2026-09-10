# Plano — Universal Standard Library (STDLIB)

**Dono:** lane KOFSCRIPT (fixes-for-kofagent) · **Status:** EM CURSO — **S7 parcial**: `addDays`/`diffDays` FEITOS em JVM/Script (S7a) + JS (S7b) + **Native x86** (S7c `cd622c47`, classe nova `runtime/RuntimeTimeIso.java`); resta só **riscv64/aarch64** (TIME002 residual — asm riscv da spec x86 pronta, bloqueado de PROVA sem qemu/toolchain) + `format`/`boundaries` (decisão de superfície da mantenedora); S0–S6, S8–S10 FEITOS (auditoria 10/09 vs código: KofMath/KofStrings/KofEncoding/KofUuid/KofValidation/KofNet/KofTime + KofRandomTest) · **Briefing:** maintainer 08/09 (universal stdlib, multitarget, anti-microdependência)

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
Gate:  ConformanceMatrixTest + matriz docs/development/conformance-matrix.md
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
| `strings` | ~~capitalize/uncapitalize~~ ✅ (uncapitalize FEITO 09/09 S11, 5 alvos) · toCamelCase/toPascalCase/toSnakeCase/toKebabCase (com HTTPServer/XMLParser) · slugify · truncate · repeat · reverse · count · removeWhitespace/normalizeWhitespace · padLeft/padRight · isNumeric/isInteger/isDecimal/isAlpha/isAlphaNumeric/isUpper/isLower/isAscii · escapeHtml/unescapeHtml/escapeJson · lines/words · indent/dedent |
| `uuid` | v4 · ~~isUuid~~ (FEITO S3b-ext 09/09, 5 alvos — UUID001 fechado no merge beta→main 10/09) · v7 · ulid/isUlid (P1) |
| `encoding` | base64Encode/Decode · base64UrlEncode/Decode · hexEncode/Decode · urlEncode/Decode |
| `random` | randomDouble · randomBoolean · randomChoice · randomString · randomBytes (secure split: `random.*` inseguro vs `security.*` seguro — já documentado) |
| `validation` (ext) | ~~isCpf/formatCpf~~ (formatCpf FEITO S12 09/09, 5 alvos) · ~~isCnpj~~ · formatCnpj FEITO S12b 09/09 (5 alvos) · ~~isCep/formatCep~~ (formatCep FEITO S12 09/09, 5 alvos) · isPis/isNis · isIp/isIpv4/isIpv6/isMac/isDomain/isPort · isCreditCard/creditCardBrand/last4 (Luhn) · isStrongPassword/passwordScore |
| `time` (ext) | addDays/addMonths/addYears · daysBetween/hoursBetween · startOf/endOf (day/week/month/year) · isLeapYear · daysInMonth · age · formatDate/parseDate · isToday/~~isWeekend~~ (FEITO S7-ext 09/09, 5 alvos) · today |
| `net` (novo, P2) | **6 escalares** `net.scheme/host/port/path/query/fragment(STR)->STR` + `queryEncode/queryDecode` — ver §4 (decisão S8, 09/09) |
| `util` (P2) | debounce/throttle · retry (backoff/jitter) |

**Não** (regra do briefing §48/§49 + R6): browser/DOM/storage/clipboard = lane KofUI
(kof.ui já existe); crypto caseiro = proibido (JCA já); `Result`/`Option` = não existe
na   (null-safety + throw são o mecanismo).

## 3. Degraus commitáveis (cada um: dispatch + 3 backends + teste Kof<Domain>Test + matriz + doc)

- **S0** este plano + claim DOING
- **S1a** `JvmRuntimeCallDescriptors` 504→≤500 (split por domínio — pré-requisito do gate)
- **S1** `math` (P0-a) — 4 targets
- **S2** `strings` (parte 1: cases/slug/pad/reverse/count) — 4 targets
- **S3** `strings` (parte 2: escapes/lines/words/indent/isX)
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
    (`planning-stdlib-array-returns.md`, PROPOSED) — retorno Array
    na camada de dispatch é decisão de design, não edição.
  - **S10 face main (845284e5 + fix §88, merge beta→main 10/09):**
    `random.double/boolean/int/hex` — as DUAS faces convivem no dispatch
    (`KofRandom.staticMethod` aceita `randomInt` E `int`, etc.; mesma runtime
    fn, retrocompat aditiva). O `double` fechou o FLT001 p/ a família random
    no riscv/aarch (B27: fcvt.d.l/fdiv + tradutor ucvtf/fld), após o fix do
    divisor 2^52→2^53 (§88). Borda documentada: `hex(n<=0)` → null em JVM/JS,
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
   - **ABERTO — TIME002 residual** (R6, nunca silencioso): `addDays`/`diffDays`
     em **riscv64/aarch64** (asm riscv da especificação x86 pronta em
     `RuntimeTimeIso`; `divl`→`divu/remu` seguro: z≥0 garantido pelo guard de
     range; aloc String = padrão kof_alloc riscv + translator aarch; fatia B
     própria — precedente NET001: x86 fecha primeiro, cross depois). Gate
     dispara no compile-time só p/ esses 2 alvos. **BLOQUEIO de prova:** sem
     cross-assembler/qemu no ambiente da lane — NÃO escrever asm sem montar/rodar.
   - **ABERTO**: `format`/`boundaries` (forma de API — `format(date, "yyyy-MM-dd")`
     vs funções escalares `yearOf`/`monthOf`… — decisão de superfície da
     mantenedora, como a família `net`/`validation`).
- **S8** `net` url/query parse/encode — **FEITO** (S8 decisão §4; KofNet 6 escalares + queryEncode/Decode, RuntimeUri, stdnet, NET001 riscv fechado B24).
- **S3b-wedge (uuid.v4) + S4 COMPLETO FEITOS 08/09:** uuid shape-verified 3 targets (SECN000 cross-arch fechado 09/09 — B25 getrandom ecall); encoding hex/url/base64/base64url (matriz stdenc 11 campos × 4; gates ENC002 base64* e SECN000 uuid nos cross). LIÇÃO JVM-runtime: nunca checked exceptions no KofRuntime gerado (SecureRandom new, não getInstanceStrong).
- **S1b-wedge FEITO (10/09):** `math.sqrt(DOUBLE)->Double` — PRIMEIRO Double da namespace `math` (abre o caminho p/ lerp/percentage/roundTo/parse*/pow). JVM (`Math.sqrt`) + SCRIPT (reflexão) + JS (`Math.sqrt`) + x86 (`sqrtsd %xmm0`, arg/ret pela convenção de bits `popq %rax; movq %rax, %xmm0; call; movq %xmm0, %rax; pushq %rax` — precedentes `kof_json_encode_double`/`kof_random_double`). NaN em <0 = IEEE (paridade medida nos 3). **MATH001 gate (R6):** riscv64/aarch64 — `fsqrt.d` trivial mas a lane não tem cross-assembler/qemu p/ montar+rodar (mesma condição de parada de UUID001/ENC002; regra: nunca asm sem prova). **ACHADO (bug 90):** o interpretador faz `==` de Double via `numEq`→`Double.compare` → `NaN == NaN` = `true` (divergência dos 3 compilados, IEEE) — semântica `==` congelada (regra 6), registrado em known-bugs + célula PARTIAL na matriz; o wedge NÃO toca no interpretador. ⚠️ Bug 44: matriz/testes usam SOMENTE comparações Bool (`sqrt(9.0)==3.0`), nunca `println` de double cru. Prova: `KofMathTest.sqrtJvm/sqrtNative/sqrtJs` (8 linhas byte-idênticos) + `sqrtGatedOnCrossArch` (MATH001 × 2) + `ConformanceMatrixTest.stdsqrt` (6 outputs; jvm/native/js + doc-gate) + harness C isolado (8 vetores, 0 fails — ANTES da suíte).
- **S3b.1 FEITO (10/09):** `uuid.isUuid(STR)->Bool` — predicado de **forma** 8-4-4-4-12 (36 chars, traços em 8/13/18/23, hex min/maiúsculo; version/variant NÃO verificadas). JVM+SCRIPT+JS+x86 sem gate (byte-scan plano; x86 validado no harness C isolado — 12 vetores + null, 0 fails — ANTES da suíte, lição S7c). **UUID001 gate (R6):** riscv64/aarch64 = fatia B própria pendente (mesma condição de parada de S7c-1 — sem cross-assembler/qemu na lane; spec x86 pronta em `RuntimeUuid`; NÃO escrever asm sem montar/rodar). Prova: `ConformanceMatrixTest.stduuidform` (7 outputs × 4 targets, doc-gate) + `KofUuidTest.isUuidShapeJvmJsNative` (JVM==JS==x86 byte-idênticos; última linha `isUuid(uuid.v4())` — paridade com o próprio gerador) + `isUuidGatedOnCrossArch` (UUID001 nos 2 alvos).
- **S1–S2b.2 FEITOS 08/09:** math(9) · strings predicados(8: isAlpha/isNumeric/
  isAlphaNumeric/isAscii/isUpperCase/isLowerCase/count) · strings conversores(4:
  capitalize/reverse/repeat/truncate — 1º caso de alocação de String no runtime,
  fatias riscv B7/B8; gap NAT-STR01 p/ reverse UTF-8 no Native). Nota de API:
  `validation.min/max` (binários, G4) ≠ `math.min/max` (aritméticos) — namespaces
  distintos, sem colisão; documentar em learn.
- **S9** matriz stdlib em docs/stdlib.md + learn/39-stdlib + training/idioms (math/
  strings/validation) + benchmarks mínimos (clamp/slugify) se aplicável — **FEITO** (matriz `std*` no ConformanceMatrixTest + `docs/stdlib.md` §STDLIB + `learn/39-stdlib.md` + `training/idioms/stdlib.md`/`strings.md`).

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
