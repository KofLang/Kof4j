# Plano — Universal Standard Library (STDLIB)

**Dono:** lane KOFSCRIPT (fixes-for-kofagent) · **Status:** EM CURSO · **Briefing:** maintainer 08/09 (universal stdlib, multitarget, anti-microdependência)

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
| `strings` | capitalize/uncapitalize · toCamelCase/toPascalCase/toSnakeCase/toKebabCase (com HTTPServer/XMLParser) · slugify · truncate · repeat · reverse · count · removeWhitespace/normalizeWhitespace · padLeft/padRight · isNumeric/isInteger/isDecimal/isAlpha/isAlphaNumeric/isUpper/isLower/isAscii · escapeHtml/unescapeHtml/escapeJson · lines/words · indent/dedent |
| `uuid` | v4 · isUuid · v7 · ulid/isUlid (P1) |
| `encoding` | base64Encode/Decode · base64UrlEncode/Decode · hexEncode/Decode · urlEncode/Decode |
| `random` | randomDouble · randomBoolean · randomChoice · randomString · randomBytes (secure split: `random.*` inseguro vs `security.*` seguro — já documentado) |
| `validation` (ext) | isCpf/formatCpf · isCnpj · isCep/formatCep · isPis/isNis · isIp/isIpv4/isIpv6/isMac/isDomain/isPort · isCreditCard/creditCardBrand/last4 (Luhn) · isStrongPassword/passwordScore |
| `time` (ext) | addDays/addMonths/addYears · daysBetween/hoursBetween · startOf/endOf (day/week/month/year) · isLeapYear · daysInMonth · age · formatDate/parseDate · isToday/isWeekend · today |
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
  - **S10a/b FEITO 09/09:** `randomInt(bound)`/`randomBoolean`/`randomString(n,
    alphabet)` nos 5 alvos (entropia só do SO — getrandom/SecureRandom/crypto;
    x86 alias `kof_sec_random_int`, riscv B27/B28 + aarch translator, JS
    kof_platform+crypto fallback, JVM SecureRandom). Matriz `random` (assert-
    only, estilo uuid). `randomChoice` NÃO entra: idiom `l[randomInt(l.size)]`
    (a regra — complexidade a quem usa). `randomBytes`/`randomChoice` binário =
    DD-STDLIB-01 (`planning-stdlib-array-returns.md`, PROPOSED) — retorno Array
    na camada de dispatch é decisão de design, não edição. `randomDouble` = S1b
    (FLT001 — double não existe no asm riscv/aarch, probe 09/09).
- **S6** ext `validation` network (IPv4/IPv6/mac/domain/port) + Luhn
- **S7** ext `time` (add/diff/boundaries/format) — JVM java.time, JS Date, Native syscall
- **S8** `net` url/query parse/encode
- **S3b-wedge (uuid.v4) + S4 COMPLETO FEITOS 08/09:** uuid shape-verified 3 targets (SECN000 cross-arch fechado 09/09 — B25 getrandom ecall); encoding hex/url/base64/base64url (matriz stdenc 11 campos × 4; gates ENC002 base64* e SECN000 uuid nos cross). LIÇÃO JVM-runtime: nunca checked exceptions no KofRuntime gerado (SecureRandom new, não getInstanceStrong).
- **S1–S2b.2 FEITOS 08/09:** math(9) · strings predicados(8: isAlpha/isNumeric/
  isAlphaNumeric/isAscii/isUpperCase/isLowerCase/count) · strings conversores(4:
  capitalize/reverse/repeat/truncate — 1º caso de alocação de String no runtime,
  fatias riscv B7/B8; gap NAT-STR01 p/ reverse UTF-8 no Native). Nota de API:
  `validation.min/max` (binários, G4) ≠ `math.min/max` (aritméticos) — namespaces
  distintos, sem colisão; documentar em learn.
- **S9** matriz stdlib em docs/stdlib.md + learn/39-stdlib + training/idioms (math/
  strings/validation) + benchmarks mínimos (clamp/slugify) se aplicável

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
