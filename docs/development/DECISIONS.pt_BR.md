[English](DECISIONS.md) | [Português](DECISIONS.pt_BR.md)

# DECISIONS — registro de decisões da linguagem

**Última atualização:** 30/09/2026
**Mantenedora:** Mel Santos
**Natureza:** registro normativo e histórico de decisões de arquitetura, semântica e evolução da linguagem.

> Este arquivo registra decisões que já foram tomadas. Ele não é um backlog, um diário de implementação nem uma coleção de propostas abertas.
>
> Uma decisão registrada aqui continua sendo a fonte de verdade até ser formalmente substituída por outra decisão. O código, os testes e o roadmap devem convergir para este contrato.

---

## 0. Índice de decisões

Auxílio de navegação, não é uma decisão por si só. Ordenado como neste arquivo.

- **1.** Autoridade e regras de alteração
- **2.** Invariantes globais
- **D-STDLIB** — tempo e calendário
- **D-SEC** — segurança
- **D-APP** — modelo de aplicação
- **D-SPRING** — independência de framework
- **D-RELEASE** — critério de avaliação de patch
- **D-ASM-GATE** — gate de ASM riscv/aarch
- **D-BACKEND-SEMANTICS** — semântica de backend
- **D-BASELINE** — baseline da toolchain
- **D-NULL** — nullabilidade e primitivos
- **D-NULL-INTENT** — intenção explícita de nullabilidade
- **D-PRINT** — conversão implícita de Char
- **D-NARROW-WHILE** — narrowing de fluxo
- **D-ENUM207** — identidade de enum
- **D-VALUE-RECORD** — value records / tipos de valor de primeira classe
- **D-DEV-PRIORITY** — "Em desenvolvimento" é a prioridade absoluta
- **D-DIAG-EN** — tooling/diagnósticos em inglês; docs EN+PT
- **D-DECL-RETURN** — o tipo de retorno declarado é lei (#333)
- **D-NOT-JAVA** — Kof não é Java/Kotlin
- **D-UI-STYLE** — `style` declarativo (UI007)
- **D-UI-TOKENS** — tokens do design system
- **D-UI-APPSTATE** — `AppState(initial)` store-raiz
- **D-UI-DIFF** — reuso de nó
- **D-UI-AUTOUNSUB** — inscrições com escopo por component
- **D-UI-CANCELLED** — `cancelled()` em ações async de UI
- **D-UI-SCOPE** — atualizações de regra do `kof.ui`
- **D-UNIVERSAL** — promoção do universal-platform
- **D-TRIAGE** — a checagem de filosofia precede a issue
- **D-POLL-19** — todas as decisões pendentes resolvidas (enquete 19/09)
- **D-TROOL** — `Bool` nunca nullable; `Troolean`
- **D-KOF-FIRST** — contrato interno antes da comparação externa
- **D-SCHED-DURATION** — durações idiomáticas no `scheduler.at`
- **D-WORKFLOW-RUN** — `kof workflow run`
- **D-MAKEALIVE** — Kof Makealive (Estágio 3)
- **D-MAKEALIVE-CLI** — contrato da 3.8: `kof makealive plan|apply|destroy` (20/09)
- **D-MAKEALIVE-SYNTAX** — 3.2 `infra "prod" { }` = açúcar puro sobre `design()` (21/09)
- **D-ARRAY-PRINT** — §388-B: `println(Int[])` é o formato de container §107 (21/09)
- **D-KOF-AS-CLOUD** — Kof tem que SER a nuvem
- **D-BOOTSTRAP** — o bootstrapper (Kof em Kof)
- **D-DB-GAPS** — gaps órfãos de DB/ORM
- **D-BRANCH-0.5.0** — trabalho move para `beta-0.5.0` *(SUPERSEDED 28/09 por D-QUALITY-PIPELINE-2609 / D-BRANCH-PIPELINE)*
- **D-RELEASE-1.0** — KOF 1.0 EXIT GATE
- **D-VERSION-BUMP-0.5.0** — revisão para `0.5.0-beta`
- **D-1.0-EDGES** — arestas abertas fechadas
- **D-SLOT-PIN** — §383/#561 valor armazenado do "miss abençoado"
- **D-RELEASE-0.5.0-GATE** — gate de release 0.5.0
- **D-RELEASE-0.5.0-SCOPE** — planos em voo no allowlist; EG-8 desacoplado
- **D-RULE6-BATCH** — triagem rule-6: seis decisões (funções como valores, SEM084-087, ABI na 1.0)
- **D-FFI-STRUCT** — ABI de struct/array da FFI (D6)
- **R6-SCOPE** — entrega incremental não fere o R6
- **D-R3-BUFFER** — out-buffer = tipo nominal `Buffer(U8)`
- **D-R3-HANDLE-LIFETIME** — memória do `Handle` é automática
- **D-ARTIFACT-TRUST** — contrato de confiança dos artefatos 1.0
- **D-VERSIONING-RELEASE** — política consolidada de versionamento e corte de release
- **D-DEBT-SCOUT** — ferramenta de escoteiro de dívida técnica autorizada, só Wave 1, sem capacidade de publicar Issue
- **D-DEBT-SCOUT-W2** — Wave 2 autorizada (qualificação de evidência, clustering, SARIF); ainda shadow, ainda sem publicar Issue
- **D-KOF-IS-KOF** — código Kof nunca embute HTML/CSS/JS (regra absoluta)

---

## 1. Autoridade e regras de alteração

### 1.1 Quem decide

A mantenedora decide questões de contrato, semântica, arquitetura e direção da linguagem.

Agentes e contribuidores podem:

* investigar alternativas;
* propor decisões;
* implementar decisões aprovadas;
* corrigir bugs e divergências em relação ao contrato;
* atualizar evidências e estado de execução.

Agentes e contribuidores **não podem alterar o contrato de uma decisão por interpretação própria**.

### 1.2 Como uma decisão entra neste arquivo

Uma decisão só é considerada vigente quando registrada com:

* identificador estável;
* data;
* escopo;
* contrato;
* decisão tomada;
* relação com decisões anteriores, quando aplicável;
* estado de implementação, se houver.

A decisão não mora no chat. O chat pode conter a discussão; este arquivo contém o resultado normativo.

### 1.3 Como uma decisão é revisada

Uma decisão vigente só pode ser alterada por uma nova entrada que:

1. identifique a decisão anterior;
2. explique o que muda;
3. registre a nova decisão;
4. preserve o histórico;
5. atualize o roadmap e a documentação afetada.

Uma decisão substituída não é apagada.

### 1.4 Estados permitidos

| Estado        | Significado                                                |
| ------------- | ---------------------------------------------------------- |
| `DECIDED`     | Contrato aprovado, ainda sem implementação completa        |
| `IN_PROGRESS` | Implementação em andamento                                 |
| `IMPLEMENTED` | Implementação concluída e validada                         |
| `PARTIAL`     | Parte do contrato implementada; gaps explícitos permanecem |
| `BLOCKED`     | Implementação depende de outra decisão ou capacidade       |
| `SUPERSEDED`  | Substituída por outra decisão                              |
| `REJECTED`    | Alternativa analisada e rejeitada                          |
| `CLOSED`      | Registro encerrado sem backlog restante                    |

O estado de implementação **não altera o contrato**.

---

## 2. Invariantes globais

Estas regras continuam válidas independentemente das decisões abaixo.

### G-01 — Freeze de superfície

O congelamento 0.2.6-beta permanece vigente para os itens explicitamente congelados:

* operadores;
* `==`;
* `spawn`;
* coleções;
* demais itens cobertos pela regra 6.

Uma decisão posterior pode alterar um contrato congelado somente quando registrar explicitamente a revisão correspondente.

### G-02 — Visão universal

R1–R12 permanecem como invariantes da visão universal da linguagem.

### G-03 — Limite de complexidade

A regra ≤500 permanece vigente.

### G-04 — Suíte como gate

A suíte de conformidade permanece como gate de integração. Código não pode ser considerado concluído apenas porque compila localmente.

### G-05 — Gap honesto

Quando uma capacidade não existe em determinado target, o sistema deve:

* reportar o gap documentado;
* usar o código de diagnóstico correspondente;
* nunca produzir um resultado silenciosamente incorreto;
* nunca simular suporte inexistente como se fosse suporte real.

### G-06 — Paridade

Quando uma decisão define comportamento observável, a implementação deve buscar o mesmo contrato em todos os targets suportados.

Uma diferença entre targets só é aceitável quando:

1. estiver explicitamente documentada;
2. tiver diagnóstico ou comportamento definido;
3. estiver representada na matriz de conformidade.

### G-07 — Determinismo

Mesma entrada, mesmo contrato e mesmo target devem produzir resultado determinístico.

Quando a decisão exigir paridade cross-target, o resultado observável deve ser equivalente, salvo gaps explicitamente registrados.

### G-08 — Não reinventar a roda

A lógica interna do compilador e do runtime deve se inspirar prioritariamente em:

1. Java — JLS, JVMS e comportamento de `java.lang`/`java.math`;
2. C — ISO C e `libm`, especialmente para o backend nativo;
3. outras linguagens, quando a referência for uma técnica de backend.

Essa regra não autoriza copiar a superfície de outra linguagem. Sintaxe, ergonomia e modelo de escrita continuam sendo decisões próprias do Kof.

---

# 3. Decisões vigentes

## D-STDLIB — tempo e calendário

**Data:** 13/09/2026
**Estado:** `IMPLEMENTED`
**Escopo:** semântica de data, hora e calendário da stdlib.

### Contrato

**D-STDLIB.1 — UTC como referência padrão**

`today()` e `isToday` derivam de `now()` em UTC em todos os targets.

O fuso local só pode ser obtido por API explícita:

```kof
time.tzOffsetSeconds()
```

Native sem suporte de timezone reporta `TIME003`.

Não existe paridade acidental de timezone entre targets.

**D-STDLIB.2 — Calendário escalar**

A API base de calendário usa valores escalares sobre ISO.

Exemplo:

```kof
time.addDays("YYYY-MM-DD", n) -> String
```

A stdlib base não introduz retornos compostos para operações de calendário.

**D-STDLIB.3 — Diferença de horas**

`hoursBetween` conta horas inteiras completas, com truncamento em direção a zero, consistente com `daysBetween`.

Não usa float nem assinatura de 12 argumentos.

**D-STDLIB.4 — Formatação ISO**

```kof
time.formatDateIso(y, m, d) -> String
```

Data inválida retorna `""`.

**D-STDLIB.5 — Parsing ISO**

```kof
time.parseDateIso(value) -> Int
```

Retorna o serial `daysFromEpoch`.

Entrada inválida retorna `0`.

Patterns arbitrários como `dd/MM/yyyy` não fazem parte da stdlib base.

**D-STDLIB.6 — isToday**

```kof
time.isToday(y, m, d) -> Bool
```

Compara com a data UTC derivada de `now()`.

### API ratificada

| Função                      | Contrato                  | Targets                   |
| --------------------------- | ------------------------- | ------------------------- |
| `time.todayIso()`           | `() -> String`            | 5                         |
| `time.formatDateIso(y,m,d)` | `(Int,Int,Int) -> String` | 5                         |
| `time.isToday(y,m,d)`       | `(Int,Int,Int) -> Bool`   | 5                         |
| `time.hoursBetween(...)`    | `(Int × 8) -> Int`        | 5                         |
| `time.parseDateIso(String)` | `String -> Int`           | 5                         |
| `time.tzOffsetSeconds()`    | `() -> Int`               | JVM/JS/SCRIPT; Native gap |

### Evidência

Implementação S7e–S7h concluída em 13/09/2026.

* `KofTimeE2ETest`: 30/30
* Matrizes `stdtime3`–`stdtime6`
* Paridade Script
* Suíte: 1772/0/0

**Referência de implementação:** `docs/stdlib/time.md`
**Fila:** encerrada.

---

## D-SEC — segurança

**Data:** 13–14/09/2026
**Estado:** `PARTIAL`
**Escopo:** criptografia, cookies, middleware de segurança e OAuth2/OIDC.

### Invariantes

* Criptografia não é implementada de forma caseira.
* JVM utiliza JCA quando aplicável.
* JS utiliza WebCrypto quando aplicável.
* Native utiliza implementação auditada ou reporta gap.
* Algoritmos, formatos de token e regras de validação são contratos observáveis.
* Gaps são reportados por `SECN00x`.

### D-SEC.1 — ChaCha20-Poly1305

**Decisão:** adicionar suporte a ChaCha20-Poly1305 conforme RFC 8439.

Formato:

```text
chacha20$<nonceB64(12B)>$<ct+tagB64(16B tag)>
```

API:

```kof
security.chacha20Encrypt(text, keyHex) -> String
security.chacha20Decrypt(token, keyHex) -> String
```

A chave deve ter 32 bytes representados em hexadecimal.

Nonce nunca pode ser reutilizado com a mesma chave.

Native sem implementação reporta `SECN002`.

**Estado:** JVM + JS implementados. Native permanece em gap.

**Evidência:** vetor RFC 8439 + `node:crypto` + `KofSecurityTest`.

### D-SEC.2 — Cookies

API:

```kof
security.cookieSet(name, value, opts)
security.cookieGet(header, name)
```

Defaults:

* `HttpOnly`;
* `Secure`;
* `SameSite=Lax`;
* `Path=/`.

Native sem implementação reporta `SECN006`.

**Estado:** JVM + JS implementados.

### D-SEC.3 — Middleware `app.security()`

A ordem do pipeline é fixa:

```text
rate-limit
→ CORS
→ security headers
→ cookies/session
→ CSRF
→ authentication
→ RBAC
→ route
```

O usuário configura políticas, mas não recompõe a ordem interna.

Sem argumentos, `app.security()` aplica os defaults de hardening.

Em produção, `listen`/`listenSecure` sem `app.security()` emite warning.

### D-SEC.4 — Autenticação padrão

Quando `app.security()` está configurado:

* o default é autenticação obrigatória;
* métodos de leitura não são implicitamente públicos;
* caminhos públicos devem ser declarados por allow-list;
* `permitAll` é alias de `publicPaths`;
* token inválido nunca passa silenciosamente;
* CSRF é ligado por default para métodos que alteram estado;
* `csrf:false` desliga explicitamente essa proteção.

### D-SEC.5 — OAuth2/OIDC

A implementação segue esta ordem:

1. resource server;
2. client authorization-code + PKCE;
3. provider: fora do escopo.

O resource server valida JWT de terceiros por JWKS, issuer e audience.

Algoritmos permitidos:

* RS256/384/512;
* ES256/384/512.

São rejeitados:

* `none`;
* HS*;
* algoritmos fora da allow-list.

Native/JS sem implementação reportam `SECN007`.

### D-SEC.6 — TLS

API:

```kof
app.listenSecure(port, certPem, keyPem)
```

A chave deve estar em PKCS#8 PEM.

JVM é o primeiro target.

Self-signed permanece conveniência de desenvolvimento, não configuração de produção.

### Evidência

* `KofSecurityTest`
* `KofWebE2ETest`
* `KofBlogE2ETest`
* `KofOAuthResourceServerTest`

**Referência:** `docs/stdlib/security.md`
**Implementação:** `docs/stdlib/stdlib-web.md`
**Fila restante:** Native crypto/TLS e gaps documentados.

---

## D-APP — modelo de aplicação

**Data:** 13/09/2026
**Estado:** `PARTIAL`
**Escopo:** manifesto, composição de componentes e execução de aplicações.

### Contrato

**D-APP.1 — Manifesto**

O manifesto da aplicação é:

```text
kof.toml
```

Ele é opcional.

Sem manifesto, o comportamento atual deve permanecer 1:1.

**D-APP.2 — Unidade de aplicação**

Uma aplicação é um diretório contendo um módulo Kof e um `main()`.

Componentes possíveis:

* backend;
* frontend;
* static.

Uma aplicação é a menor unidade de `kof serve` e deploy.

**D-APP.3 — Frontend**

Frontend é outro módulo Kof compilado para JS.

O backend não chama funções do frontend diretamente.

A comunicação front→back ocorre por HTTP/JSON.

**D-APP.4 — System**

System é composição de deploy, não de compilação.

`kof serve --system` permanece rejeitado.

A alternativa aprovada é:

```text
kof serve --list
```

**D-APP.5 — Fat jar**

A flag:

```text
kof build --fat
```

é opcional.

Default continua sendo classpath explícito.

**D-APP.6 — Rebuild**

O frontend é reconstruído sob demanda por hash.

Watcher permanece futuro.

**D-APP.7 — Targets**

Wasm entra na matriz quando o target estiver aberto.

Android é declarado como suportado pelo modelo WebView + KofJS, sem alterar o modelo de aplicação.

### Manifesto

Seções:

```toml
[app]
[serve]
[frontend]
[static]
```

O manifesto é lido pela CLI, não pelo compilador.

Erro de manifesto deve produzir diagnóstico claro.

### Topologias permitidas

* monolith;
* modular monolith;
* microservices;
* microfrontends;
* full-stack;
* backend-only;
* frontend-only;
* full-stack distribuído;
* gateway.

O modelo não muda entre essas topologias.

### Evidência

* `KofProjectConfig`
* `CmdBuildFatTest`
* `KofBlogE2ETest`
* `KofWebE2ETest`

**Referência:** `docs/backend-parity.md` (modelo de app `APP001–003`)
**Fila:** `CmdNew` ✅ (`new` em `Main.java:37`); integração de manifesto/dependências ✅ (`kofdeps` + lock transitivo 1.5.2 + registry pull 1.5.3-S2, 19/09); gaps de target → rastreados em `docs/backend-parity.md` (ledger, não este registro). `APP002` ✅ FEITO 23/09 (#598, ratificado no chat): o `kof serve` repassa `[server] port` do `kof.toml` como `KOF_SERVER_PORT` quando a env var não está setada (env do usuário prevalece) — prova `ServeManifestPortE2ETest` 2/2.

---

## D-SPRING — independência de framework

**Data:** 13/09/2026 · **Concluída:** 19/09/2026 (auditoria vs código, este commit)
**Estado:** `CONCLUÍDA`

### Contrato

* Nenhum componente da stdlib depende de Spring.
* Nenhum backend gera Java-source como etapa obrigatória.
* Capacidades fundamentais possuem API Kof-native.
* Spring pode ser consumido como alternativa de interoperabilidade.
* O teste de independência tem o mesmo peso do teste de interoperabilidade.

### Fases

| Fase                | Estado        |
| ------------------- | ------------- |
| 1–9                 | `IMPLEMENTED` |
| 10 — testing nativo | `IMPLEMENTADA 19/09` (`kof test` harness: `test "nome" { }` → runner sintetizado, `CmdTest.java:15,78`; CliFlagStrictness/CmdBuildAndroidAab cobrem a face; suíte 2772/0F) |
| 11 — CLI completa   | `IMPLEMENTADA 19/09` (run/build/test/serve/fmt/deps/init/check ligados em `Main.java:18-41`; deps = Maven + registry 1.5.3-S2) |
| 12 — blog E2E       | `IMPLEMENTADA 19/09` (`KofBlogE2ETest` verde na suíte reactor completa) |

### Fase 10

`kof test` deve executar testes do projeto com asserts Kof.

JUnit não é obrigatório.

Escopo inicial:

* unit;
* HTTP com `web.app` em porta efêmera.

Property testing, stress e mocks são incrementos separados.

### Fase 11

Consolidar:

```text
run
build
test
serve
fmt
deps
init
check
new
```

`kofdeps add/remove/list/resolve` já existe.

Falta integrar dependências ao manifesto.

### Fase 12

O blog E2E é o aplicativo canônico de validação da plataforma:

* backend;
* frontend;
* banco;
* autenticação;
* validação;
* manifesto.

A validação deve ser feita por target, com gaps honestos.

---

## D-RELEASE — critério de avaliação de patch

**Data:** 14/09/2026
**Estado:** `DECIDED`

### Contrato

A linha 0.4.0 já foi liberada no #138.

O desenvolvimento continua normalmente após a release.

Quando a beta estiver entre 100 e 150 commits à frente da main, deve-se avaliar uma release de patch.

### Regra

```text
git rev-list --count origin/main..origin/beta-0.4.0
```

Ao cruzar a faixa:

1. abrir issue de release;
2. executar suíte completa;
3. fechar issues conhecidas da lane de bugs/paridade;
4. avaliar o conteúdo acumulado;
5. atualizar versão somente após a avaliação.

### SemVer

Se houver capability nova material que altere contrato, operador ou superfície de API, a versão avaliada deixa de ser patch e passa a ser minor.

O contador não congela features.

Features e fixes concluídos com suíte verde entram no pacote.

**Estado registrado em 14/09:** `main..beta = 1`.

### Relação (adicionada em 22/09/2026 — `D-VERSIONING-RELEASE`)

O histórico acima é preservado (§1.3). A `D-VERSIONING-RELEASE` generaliza e
refina esta regra sem apagá-la:

- a faixa de `100–150 commits` é o **gatilho ordinário de avaliação de
  release** — um gatilho, nunca uma autorização para publicar;
- a faixa é `LAST_RELEASE..ACTIVE_BRANCH`, não mais fixada em
  `origin/beta-0.4.0`;
- a classificação PATCH/MINOR/MAJOR agora segue a `D-VERSIONING-RELEASE`.

---

## D-ASM-GATE — gate de ASM riscv/aarch

**Data:** 14/09/2026
**Estado:** `DECIDED`

### Contrato

O gate específico de inspeção de ASM para riscv/aarch é opcional enquanto o desenvolvimento nativo não estiver completo.

A main e a beta nunca podem permanecer com testes quebrados.

### Regra

O gate pode ser reativado com:

```text
KOF_ASM_GATE=1
```

O padrão é skip explícito.

O corpo do teste continua portável:

* se `.s` existe, inspeciona o texto;
* se não existe, exige o binário linkado.

### Proteção mantida

A regressão de labels duplicadas continua coberta pelos E2Es sob qemu.

### Reativação

Quando a lane Native estiver completa e a matriz 5/5 estiver verde, o `assumeTrue` deve ser removido e o gate volta a ser obrigatório.

---

## D-BACKEND-SEMANTICS — semântica de backend

**Data:** 14–15/09/2026
**Estado:** `IMPLEMENTED`

Esta seção registra decisões de semântica já ratificadas e implementadas.

### §101 — NaN em comparações relacionais

**Decisão:** IEEE 754 puro.

Comparações relacionais com NaN retornam `false`.

`!=` retorna `true`.

Todos os targets devem concordar.

### §129 — unwind cross-thread

**Decisão:** frame de exceção por thread.

A cadeia de exceções é thread-scoped.

Worker sem handler interno publica a exceção no handle.

O consumidor relança em `await`, `await_timeout` e `select_any`.

**Extensão 19/09 — mecanismo riscv64/aarch64 (decisão da mantenedora no chat):**
o mesmo contrato §129 é portado para os targets cross usando **TLS real via
`clone`** (não uma tabela por-TID). Cada thread ganha seu próprio topo de
cadeia: a main no `_start` (nosso entry point — **não** passa por
`__libc_start_main`, então a `tp` é nossa para definir) e cada worker via
`CLONE_SETTLS` + bloco TLS por worker (as flags do clone já carregam
`CLONE_SETTLS`; hoje `a3`/tls vai como `0`). O `kof_spawn_trampoline` instala
o frame de handler por worker e publica a causa em `handle->exc` (offset 48 no
riscv), como faz o option B do x86_64. **Bloqueio conhecido a resolver na
implementação:** o tradutor aarch64 mapeia riscv `tp` → `x4`
(`NativeAarch64Helpers:74`), o que colide com `a4` → `x4` (`:98`) e não lê
`TPIDR_EL0` (o thread pointer real do aarch64, via `mrs`) — os sítios de acesso
compartilhados precisam funcionar nas duas arches antes do port entrar.
Evidência do baseline RED: dois testes cross espelhando o §129 x86 penduram sob
qemu (o `throw` do worker faz longjmp na `kof_exc_chain` global da `main`).

**Correção 19/09 — TLS-via-`tp` é ABI-inseguro; mecanismo trocado por tabela
por-TID (medido, agente).** O mecanismo "TLS real via `clone`" acima foi
implementado e **quebra a libc de forma provada**: sobrescrever o thread pointer
(riscv `tp`=x4 / aarch64 `TPIDR_EL0`) dessincroniza a TLS da própria biblioteca
C. Sob qemu isso produziu `SIGSEGV` (exit 139) em testes aarch64 que chamam
`snprintf`/`strtod` via `RuntimeDtoa` (B45): `nativeValueOfDoubleFloatMatchesJvmGolden`,
`aarch64NegativeFloatDoubleRuns`, `nativeCollectionPrintMatchesJvmGolden`. No
riscv a mesma mudança também regrediu `crossNativeConcurrencyHelpersRun`
(`done(a)` true→false). Causa raiz: nosso `_start` é nosso, mas qualquer
programa Kof ainda pode chamar a libc (dtoa/format), então a `tp` **não** é
nossa para reaproveitar. **Resolução (desvio de mecanismo, contrato intacto):**
o *contrato* §129 (cadeia thread-scoped, worker publica em `handle->exc`,
consumidor relança em `await`/`await_timeout`/`select_any`) é mantido exatamente;
só o *mecanismo* muda para uma **tabela por-TID** `kof_exc_slots` (256 entries ×
16 B `[tid, chain]`, chave `gettid`=a7 178, probe linear, mesmo padrão do
`kof_cancel_slots`/CONC001), com um helper `kof_exc_slot()` retornando `&chain`
da thread atual. É a segunda opção, mais segura, e não toca o thread pointer.
Prova: os dois testes cross agora passam verdes em riscv64/aarch64 sob qemu
(`KofConcurrency2Test` `spawnWorkerThrowIsolatedFromSiblingsCrossArch` +
`spawnWorkerThrowUnhandledPropagatesCrossArch`, 138/0 na rodada de 19/09).

### `roundTo`

**Decisão:** arredondamento decimal aritmético.

```kof
math.roundTo(value, decimals)
```

* mesmo tipo numérico do valor;
* `decimals = 0` arredonda para inteiro;
* negativos arredondam dezenas, centenas etc.;
* modo half-away-from-zero;
* sem locale;
* sem pattern DSL;
* determinístico.

### §179 — tipos builtin UI/media

Tipos builtin declarados são mapeados pelo resolvedor sem quebrar shadowing do usuário.

Uma classe de usuário com o mesmo nome continua vencendo.

### `app.security()`

O modelo mental é inspirado no Spring Security:

* filter chain fixa;
* autenticação por default;
* allow-list explícita para rotas públicas;
* CSRF por default;
* superfície Kof própria.

### §180 — impressão de float/double

Native deve alinhar-se ao comportamento observável de `Double.toString` e `Float.toString` do Java:

* shortest round-trip;
* `Float` mantém sua própria representação;
* notação científica conforme o limiar definido;
* `E` maiúsculo;
* mantissa com parte fracionária.

---

## D-BASELINE — baseline da toolchain

**Data:** 14/09/2026
**Estado:** `IMPLEMENTED`

O baseline de build do repositório sobe de Java 21 para Java 25.

Isso altera a toolchain do repositório, não o runtime mínimo dos programas Kof.

### O que muda

* `pom.xml`: `release=25`;
* CI;
* CodeQL;
* release;
* benchmark;
* Android tooling;
* JDK embutido do `package.sh`;
* documentação de build.

### O que não muda

* `JvmBackend` continua emitindo bytecode V21;
* Android continua com `release="21"`;
* `KofVersion.TOOLING_API=21`;
* programas Kof continuam com runtime mínimo JVM 21+.

---

## D-NULL — nullabilidade e primitivos

**Data:** 15/09/2026
**Estado:** `DECIDED`
**Revisão de:** §125 / SEM048

### Correção histórica

A decisão anterior foi interpretada incorretamente.

O contrato nunca proibiu que `T?` boxed carregasse `null`.

O que é proibido é fabricar `null` em um ponto sem null-safety.

### Contrato

* `Int?`, `Boolean?`, `Double?` e demais `T?` podem carregar `null`;
* `Int`, `Boolean`, `Double` e demais tipos não-nullable não carregam `null`;
* `null` literal em ponto não-nullable é erro de compile-time;
* `T?` boxed não está congelado pela regra 6;
* o comportamento deve ser completado nos targets em lockstep.

### Estado

O trabalho de boxed nullable segue na fila §241/#252/#259/#266.

O catálogo anterior que tratava isso como proibido está corrigido.

---

## D-NULL-INTENT — intenção explícita de nullabilidade

**Data:** 15/09/2026
**Estado:** `DECIDED`
**Revisão de:** opção A do §125

### Contrato

Null não é esperado por default.

Uma declaração sem intenção explícita não pode produzir ou carregar `null`.

A intenção de nullabilidade é expressa pela comparação:

```kof
if (x == null) { ... }
if (x != null) { ... }
```

Isso vale para todos os tipos.

Quando a intenção existe:

* `T?` pode carregar null real;
* `x == null` deve responder corretamente;
* `println(x)` deve imprimir `null`;
* o comportamento deve ser equivalente em todos os targets.

O `null` literal continua proibido em pontos não-nullable.

### Regras de implementação

* `Int?` e demais primitivos nullable devem usar representação boxed real;
* não pode haver dobra silenciosa `null → 0`;
* não pode haver dobra silenciosa `null → false`;
* unbox de null deve ter comportamento definido;
* campos não inicializados devem ter comportamento definido;
* map-miss não pode inventar valor.

### Fila

1. JVM + Script + JS;
2. Native;
3. intenção em declarações não-nullable;
4. auditoria e eliminação dos caminhos silenciosos.

### Proteção de escopo

A implementação do núcleo de intenção está sob responsabilidade da mantenedora.

As lanes não devem atacar o núcleo boxed-nullable de #266/#259 sem nova autorização.

**Autorização (16/09, mantenedora via chat, regra de hierarquia):** a
mantenedora delegou explicitamente a frente D-NULL-INTENT à lane de agentes
(`192.168.100.22`, issue-watcher/compiler) — o requisito de "nova autorização"
acima está cumprido. O núcleo boxed-nullable de #266/#259 está DESTRAVADO para
implementação por esta lane, seguindo a fila do D-NULL-INTENT (1. JVM + Script
+ JS; 2. Native; 3. intenção em declarações não-nullable; 4. auditoria e
eliminação dos caminhos silenciosos). O contrato em si (intenção explícita via
`== null`, `T?` boxed real, sem dobra silenciosa) permanece inalterado.
Registrado aqui pela lane antes/com a implementação, conforme a regra de
hierarquia (diretriz posterior explícita da mantenedora supera restrição
documentada anterior).

**Autorização (23/09, mantenedora via chat, regra de hierarquia):** a
mantenedora ABRIU o item 2 (**Native**) da fila desta decisão — a frente do
ABI de caixa marcada (`roadmap.md` §23 TIER 2.6.2, `N2`) — para implementação
pela lane do compilador (`9092`). A face Native restante é o **print
polimórfico de referência `Object`**: um record/classe tipado `Object` (`as
Object` direto ou local tipado `Object`) chega ao `kof_box_to_string`, que só
decodifica a caixa MAGIC de primitivo e, fora disso, passa o ponteiro cru —
então `println(o)` imprime vazio em vez do `toString` do record (medido 23/09:
JVM `Point[x=1, y=2]` vs Native vazio — repro em `NativeObjectBoxPrintE2ETest`).
O contrato decidido permanece: a caixa é `[MAGIC][tag][valor]` (24 B, §3.9
`RUNTIME_ABI.md`) e nenhum segundo ABI é criado; a face de referência despacha
`toString` pela identidade da própria classe (`type_id` no offset 0, o mesmo
discriminador que o `kof_instanceof` já usa). Registrado aqui pela lane
antes/com a implementação.

As lanes podem atuar em:

* auditoria SEM048/SEM049;
* catálogo de caminhos silenciosos;
* correções que não sobreponham a implementação protegida.

---

## D-PRINT — conversão implícita de Char

**Data:** 15/09/2026
**Estado:** `DECIDED`

### Contrato

`println` imprime `Char` como caractere.

```kof
println('A') // A
```

Concatenação também preserva o caractere:

```kof
"char: " + 'A' // char: A
```

Conversão numérica exige API explícita.

O armazenamento de `Char` em coleções continua sendo contrato separado.

---

## D-NARROW-WHILE — narrowing de fluxo

**Data:** 15/09/2026
**Estado:** `DECIDED`

O narrowing existente em `if` deve ser estendido para:

* condição de `while`;
* receptores de campo de classe;
* escopos estreitados por comparação de null.

Reatribuição dentro do escopo estreitado não altera a nullabilidade da declaração.

O falso-positivo SEM012 do caso #159 deve ser eliminado.

---

## D-ENUM207 — identidade de enum

**Data:** 15/09/2026
**Estado:** `IMPLEMENTED`

A implementação de identidade de enum foi reatribuída à lane bugs-and-gaps e está **completa**: enums são classes reais (`CompilerEnumLowering`), identidade `==`, `name`/`ordinal`/`values()` reais. A mudança semântica abaixo foi tratada como **decisão de contrato** (não correção local) e está resolvida — a fatia 1 tornou `enum == String` um **erro de tipo** (`SEM062`) em todos os alvos; a fatia 2 materializou enums como classes reais. Prova: `EnumIdentityE2ETest` 6/6; `known-bugs.md` §211 ✅ CLOSED 15/09; issue #207 fechada.

```kof
Dir.N == "N"
```

A semântica final está registrada nesta seção; o comportamento foi alterado sob esta decisão.

---

## D-VALUE-RECORD — value records / tipos de valor de primeira classe

**Data:** 16/09/2026

**Estado:** `DECIDED`

**Origem:** issue #275 (proposta de feature).

### Contexto

Kof já tem `record` imutável conciso (ex. `record Vec2(Float x, Float y)`),
mas não tem como declarar explicitamente que um agregado definido pelo usuário
tem **semântica de valor e sem identidade de objeto**. Para tipos pequenos
orientados a dados (vetores, coordenadas, cores, intervalos, tokens de parser,
estado de iterador), exigir uma alocação de objeto separada adiciona pressão
de alocação, trabalho de GC, indireção e pior localidade de cache. Depender de
escape analysis do JVM não expressa intenção e não vale nos backends Native/JS.

### Decisão

A sugestão foi **aceita**: adicionar uma entrada na fila de implementação em
`docs/development/future/` para engenharia e desenvolvimento futuros de uma
forma de valor de `record` (`value record`).

### Contrato

* Um `value record` tem o mesmo modelo de dados imutável conciso de um `record`
  Kof existente, mas explicitamente sem identidade de objeto observável.
* Igualdade/hash por campos (já o contrato de `record`).
* Aditivo e retrocompatível: o `record` comum mantém sua semântica existente;
  o código existente continua compilando e rodando (regra 2).
* ABI por alvo é uma decisão de escopo explícita antes do código (R7 honest
  scope): JVM → value/inline class; Native → passagem por valor (struct por
  valor/registradores); JS → objeto congelado comum.
* Fronteira: stdlib do núcleo, não pacote oficial (R1).

### Status

Apenas planejado — **não é desenvolvimento atual**. Sem implementação em
andamento. Lanes não devem abrir esta frente sem nova autorização (regra 6 /
R12: frentes novas não abrem antes de o estágio SYSTEMS fechar).

### Implementação

Entrada de fila adicionada em `docs/development/roadmap.md` §23 (TIER 2) e
esta decisão registrada; engenharia agendada para a fila futura.

### Relações

* `Relacionada:` #275 (issue)

---

## D-DEV-PRIORITY — "Em desenvolvimento" é a prioridade absoluta de toda lane

**Data:** 2026-09-16

**Estado:** `ATIVO` (sobrepõe qualquer ordenação de preferência por lane)

A regra da mantenedora (16/09, chat): **a prioridade total é completar o
roadmap `Em desenvolvimento`** — todo agente, toda lane, todo re-trigger
autônomo escolhe a próxima tarefa desta lista, em ordem, antes de qualquer
outra coisa (outros gaps, outras filas, frentes novas). Catalogar e corrigir
bugs seguem como sempre (o gate de qualidade nunca relaxa), mas a SELEÇÃO DE
TAREFA segue as frentes abaixo.

**As frentes (conforme declaradas pela mantenedora 16/09):**

1. **Standard Library** — contratos em estabilização (a série S:
   `PLAN-STDLIB-EXPANSION`; faces ainda abertas andam na fila por item).
2. **GC auto-collect** — safe-points + mapa de raízes por frame.
3. **Package manager além do MVP** — registry.
4. **Debugger além do MVP JVM** — DAP via stdio já está no JVM; JS
   source maps linha ✅; DWARF Native linha ✅ parcial — variáveis/expressões
   e breakpoints nativos pendentes + ext. VS Code.
5. **KofJS — a plataforma web no browser** — ES Modules via GraalJS; base do
   servidor web ✅ (`HttpServer` + `KofJsWebQueue`); SSE handler-scoped ✅
   16/09 (`7cd69a7b`); residual por feature: ws = **WEB004**, TLS = **WEB002**,
   sse push pós-return/multi-cliente = **WEB003**, path params/keep-alive =
   **WEB001** (linha canônica: `backend-parity.pt_BR.md` "web no Native/JS").
6. **kof.web no Native** — residual por feature: TLS = **WEB002**, path
   params/keep-alive = **WEB001**, ws = **WEB004**, sse = **WEB003**.
7. **kof.db/orm no JS** — **DB001 FECHADO 16/09** (nao-tipado
   `connect/execute/query/close/transaction` na ponte do host GraalJS —
   `3e55df51`+`eb9140cb`); residual `db.query<T>` tipado = `DB002` FECHADO 18/09: bind no guest via `__kof_decode_<T>`
   (arquitetural: JS não emite bytecode JVM no classpath do host) +
   `kof.orm` = `ORM001` FECHADO 18/09: `KofJsOrmBridge` roda o mesmo SQL de `JvmOrmRuntime` no host GraalJS, records tipados bindados no guest via `__kof_decode_<T>` (E2E byte-paridade; WASM planejado).

**Relação com as outras regras:** esta decisão decide a **ordem**, não o que
é **aceitável** — Q0–Q7, o freeze, a regra 6 e a regra dos três estados
mantêm toda a sua força. Uma frente bloqueada (regra 6, outro dono
`EM CURSO`, ou gate no estilo `§258`) é registrada e o agente pega a PRÓXIMA
frente desta lista — a lista é a fila, não uma sugestão. A regra de prioridade
do `AGENTS.md` (".md solto primeiro") e as camadas do roadmap §23 ficam
subordinadas a esta decisão enquanto a lista de `Em desenvolvimento` tiver
itens abertos.

---

## D-DIAG-EN — tooling e diagnósticos em inglês; docs continuam EN+PT

**Data:** 16/09/2026

**Estado:** `DECIDIDO`

**Origem:** issue #324 (decisão da mantenedora no chat: "aprovo a tradução
completa para inglês"; escopo confirmado 16/09: "tooling da linguagem 100%
em ingles mas as documentações precisam de ingles + pt. porem toda mensagem
de erro da linguagem precisa ser em ingles pro usuario. até pq kof é uma
plataforma universal").

### Contexto

O compiler emite ~144 fragmentos de mensagem visíveis ao usuário em
português (parser, typers, lowerers e runtimes JVM/JS/Native), e ~37
arquivos de teste asserem esses fragmentos. A metade internal-repr do #324
foi corrigida por `130aa213` (`Type.display`); a metade de língua estava
travada aqui como regra 6 até esta decisão.

### Contrato

1. **Toda mensagem visível ao usuário emitida pelo tooling da linguagem é
   em inglês**: diagnósticos do compiler (códigos PARSE/SEM/…), strings de
   erro lançadas pelos runtimes da stdlib (JVM/JS/Native/interpretador),
   saída de CLI/REPL/LSP, logs de build/decompilação e templates de config
   gerados.
2. **Documentação é eixo diferente e continua bilíngue** — cada doc mantém
   o par EN + PT (invariantes do `docs-lang.sh` inalterados).
3. **Códigos nunca mudam** — só o texto da mensagem (SEM048 continua
   SEM048). Testes que casam o *texto* da mensagem migram junto com sua
   unidade; testes que casam o *código* não são tocados.
4. Razão: Kof é plataforma universal — a língua da ferramenta viaja com a
   ferramenta, não com o idioma do usuário.

### Implementação

Fila aberta no DOING (lane issues: tradução em unidades por pacote;
arquivos staged/EM ANDAMENTO de outros agentes ficam de fora de cada unidade
até landarem). Docs que citam textos PT de mensagens (learn/training)
sincronizam como unidade-doc de acompanhamento por pacote traduzido.

### Relacionamentos

- Relacionado: #324 (metade internal-repr fixada por `130aa213`), R6
  (diagnóstico cirúrgico, nunca silencioso), G-01 (freeze da superfície —
  *códigos* congelados; o *texto* deste eixo é definido por esta decisão).

---

## D-DECL-RETURN — o tipo de retorno declarado é lei (#333)

**Data:** 16/09/2026

**Estado:** `IMPLEMENTADO` (top-level/ctor) — `b1ea1718`, 19/09. Ver nota abaixo
sobre o escopo de métodos

**Origem:** issue #333 (decisão da mantenedora no chat, 16/09: "classe
definida como int deve obrigatoriamente retornar int"; "função definida como
int deve obrigatoriamente retornar int e o mesmo vale pras outras tipagem.
função de um tipo declarado deve retornar aquele tipo").

### Contrato

1. **O tipo de retorno declarado é a lei** em todo backend: função/método
   declarado `T` deve retornar valor atribuível a `T`; função declarada
   `void` NÃO pode `return <valor>` — isso é diagnóstico em compile-time
   (nunca re-typing silencioso).
2. **A re-inferência silenciosa `void→T` sai das duas rotas** que hoje
   discordam (raiz medida no thread do #333):
   `SemanticAnalyzer.analyzeMethodBody` (re-tipa o símbolo da classe) e
   `CompilerFunctionLowering.lowerFunctionInner` (re-tipa só o descritor da
   definição — call-sites continuam resolvendo `()V` → o
   `NoSuchMethodError`). Todo call-site resolve contra o tipo **declarado**.
3. **Inferência só onde não há tipo declarado** (`main()`, e formas
   top-level sem anotação como hoje).
4. É **mudança deliberada de contrato** (freeze regra 1): código que hoje
   compila re-tipando um `void` declarado passa a falhar com o diagnóstico
   acima — aprovado pela mantenedora com este registro; entrada no
   CHANGELOG vai junto do commit de implementação.

### Implementação

Dono: lane compiler (issue sweep reivindicado no DOING, 17/09). O texto do
diagnóstico segue D-DIAG-EN (inglês).

**Como chegou no código (`b1ea1718`, 19/09, `SEM093`) — medido no jar do tip
0.4.6:**

- **Item 1 (void declarado):** vale para **funções top-level e construtores**;
  um **método de classe** declarado `void` com `return <valor>` ainda compila
  pela reinferência bug-26 dos dois lados (§130) — o commit da mantenedora diz
  isso de propósito: "Metodos ficam de fora". A face b do thread #333
  (`b.m()` imprimindo através do slot retipado) continua aceita por decisão,
  a menos que a mantenedora estreite o §130 depois.
- **Item 2 (re-tipo silencioso):** o re-tipo só-do-descritor do
  `FunctionLowering` (a metade NoSuchMethodError) **sumiu no top-level**; o
  re-tipo do symbol em `analyzeMethodBody` sobrevive em métodos, com os
  call-sites resolvendo contra o symbol retipado (par consistente — sem crash
  de link).
- **Item 3 (sem tipo declarado):** no **top-level** o "as hoje" foi apertado de
  propósito — `f() { return 5 }` (sem anotação) agora é `SEM093` (prova:
  `VoidReturnValueE2ETest#untypedTopLevelWithReturnRejected`), porque o
  top-level sem anotação era exatamente a face do NSME silencioso. Métodos sem
  anotação continuam inferindo (§130). Este registro preside; afrouxar depois
  é decisão da mantenedora (regra 6).
- **Item 4:** a entrada do CHANGELOG vem neste mesmo commit (EN+PT).



### Relacionamentos

- Fecha o bloqueio por regra 6 do #333 (o fix estava catalogado, esperando
  exatamente esta decisão).
- Relacionado: D-NULL-INTENT (família declarado-vs-inferido), bug 62.

---

## D-NOT-JAVA — Kof não é Java/Kotlin: pedido de feature de outra língua NÃO é bug do Kof

**Data:** 2026-09-18



**Origem:** diretriz da mantenedora, 18/09 (varredura de issues): "ele ta
abrindo issue de java no kof. kof não é java. não tem string builder no kof.
responde todas e as que não forem relativas a kof ou que ele usou treinamento
errado devem ser ignoradas e fechadas. adiciona isso como regra absoluta."

### Contrato

1. Pedido de construto que não existe no Kof porque é **Java/Kotlin/C#
   traduzido** NÃO é bug: o compilador rejeitar é comportamento correto.
   Exemplos medidos na varredura: `StringBuilder`, `val`/`var` top-level,
   keywords `fun`/`val`, `v is Car`, `"""três aspas"""`, `Pair`, `it` implícito
   de lambda, `mutableListOf`, Elvis `?:`, `?.`, intervalo `0..n`, `!!`,
   argumento nomeado, construtor primário estilo Kotlin COM corpo,
   `catch (e: Type)`, `object`, `open`/`override`.
2. Tratamento: **responder uma vez com o idiom do Kof que substitui** (a
   tabela de idioms de `AGENTS.md`) e **FECHAR a issue** como não-procedente.
   Não implementar a feature estrangeira nem "melhorar o diagnóstico" de uma
   rejeição correta.
3. Exceção (trabalho real): o Kof *promete* o construto em
   `training/`/`learn/`/docs e o compilador discorda da própria documentação —
   aí é bug (regra 4 do freeze); e *como* a feature existiria é decisão de
   design reservada à mantenedora (regra 6).
4. A regra fica registrada como **§8 de AGENTS.md** (EN+PT) — absoluta.

### Evidência

- Varredura 18/09, fechadas sob esta regra: #407, #417, #418, #422, #424,
  #425, #406, #410, #414, #416, #411, #412, #419, #420, #421, #404, #364,
  #367, #350, #386, #427 (cada fechamento traz o idiom Kof correto).
- Autoridade no corpus: `AGENTS.md` §"Fake idioms — DO NOT EXIST in Kof",
  `training/anti-patterns/fake-idioms.md`, `learn/15` (`is`/binding não tem
  suporte → `switch`/`as`).

### Relações

- Generaliza o precedente do cluster de açúcar `let/const` (§263) e
  `Int.MAX_VALUE` (bug 99 / SEM050).
- NÃO cobre: bugs cujo reproducer é Kof válido (#403, #336, #313 — ficam e
  foram corrigidos), nem o cluster nullable-primitive (D-NULL-INTENT), nem a
  resolução de tipo JDK sem qualificar (§268).
## D-UI-STYLE — `style` declarativo (UI007)

**Data:** 17/09/2026

**Estado:** `DECIDED`

**Origem:** decisão da mantenedora no chat, respondendo às cinco perguntas
abertas do `KOFUI-AUDIT.md` §UI007 ("UI007 — design proposal"). O item estava
`BLOQUEADO` pela regra 6 (superfície de API); este registro o desbloqueia.

### Contexto

O UI007 pede "declarative `style` (idiomatic CSS), own parser". A superfície
exata é congelamento de API, então não podia ser implementada por julgamento
do agente. O `Style(Int, Int, Int, Int)` existente (background, foreground,
padding, radius — `kof_ui_style_new`) já está entregue nos quatro alvos
(real só no KofJS; no-op documentado nos demais).

### Contrato

1. **Superfície.** `Style("<declarações>")` — um argumento String literal,
   pares `prop: value;` separados por `;`. Produz um valor `kof.ui.Style`,
   consumido por `View(style)` exatamente como a forma de 4 Ints. O
   `Style(4 Ints)` existente fica **intocado** (aditivo, retrocompatível).
2. **Parse no compilador (Q4).** As declarações são parseadas e validadas em
   compile-time; o lowering carrega o texto CSS **normalizado**. Argumento
   não-literal é diagnóstico (nada de parse em runtime).
3. **Cores (Q1).** Aceita hex CSS (`#rgb`, `#rrggbb`, `#rrggbbaa`), nomes de
   cor CSS e os nomes de `Palette` (`red`, `cyan`, …) — a mesma tabela do
   `Palette`. O compilador valida o valor e o mantém no CSS normalizado (o
   browser resolve o nome). A forma de 4 Ints segue sendo o caminho para
   passar um `Color` calculado (a forma String aceita só literal).
4. **Unidades (Q2).** Inteiro nu significa `px`; os sufixos `px`, `%`, `em`
   e `rem` são aceitos.
5. **Propriedades (Q3).** Uma **whitelist tipada**. Propriedade fora da
   whitelist é diagnóstico em compile-time (`SEM076`) — nunca repassada em
   silêncio para `node.style` (R6). Declaração malformada é `SEM077`; valor
   inválido para propriedade conhecida é `SEM078`.
6. **Escopo (Q5).** `setStyle(style)` — recebendo o valor `Style`, exatamente
   como `View(style)` — fica disponível em **todo widget DOM**
   (`KofUi.isDomWidget`), não só `View`, pela família compartilhada
   `kof_ui_widget_set_style` (padrão do UI005, mesma forma de
   `setFont(font)`).

### Invariantes

- **Zero regressão na forma de 4 Ints**: `Style(Int, Int, Int, Int)` mantém
  a semântica exata em todos os alvos.
- **Paridade honesta por alvo**: o style declarativo é real no KofJS e
  **no-op** no JVM/Native/Script, igual ao gap já existente do `Style`
  (UI001). O no-op segue documentado; não vira fallback silencioso.
- **Diagnósticos seguem D-DIAG-EN** (texto da mensagem em inglês; códigos
  estáveis).
- Os códigos novos são **aditivos** e não renumeram nada.

### Alternativas rejeitadas

- **Parse em runtime** (opção do Q4): rejeitada — "own parser" mais validação
  em compile-time (Q3) exigem o compilador; um parse em runtime também
  transformaria o erro de propriedade desconhecida em falha de execução, e
  não em diagnóstico.
- **`setStyle` só em `View`** (opção do Q5): rejeitada pela mantenedora em
  favor da superfície mais ampla (todo widget DOM).

### Implementação

Reivindicado no `DOING.md` (UI007, lane UI/style); roadmap §8 Frontend.
Fatia A = parser no compilador + `Style(String)` + lowering + runtime JS +
testes; fatia B = `setStyle` em todo widget DOM.

### Evidências

`UiStyleCssE2ETest` 10/10 (JVM + Native + Script + JS: happy path, nomes CSS,
unidades, `setStyle` num Label, `SEM076`/`SEM077`/`SEM078`, não-literal,
não-regressão da forma de 4 Ints) + `KofJsBrowserE2ETest` (Chrome headless,
DOM real: `declarativeStyleRendersInRealBrowserDom`,
`setStyleRendersOnAnyDomWidgetInRealBrowser`); suíte completa dos 4 módulos
verde fora do flake pré-existente §252 e dos reds cross §181/§256;
`docs-lang.sh check` 0/0/0.

### Relacionamentos

- Fecha o bloqueio por regra 6 do UI007 (`KOFUI-AUDIT.md` §UI007).
- Relacionado: UI001 (família do no-op silencioso), UI005 (família
  compartilhada `kof_ui_widget_*`), D-DIAG-EN.

---

## D-UNIVERSAL — promoção do `IMPLEMENTATION-UNIVERSAL-PLATFORM` a trabalho corrente (R12 sobreposto)

**Data:** 2026-09-17

**Estado:** `DECIDED`

**Origem:** diretriz da mantenedora no chat, 17/09/2026: "se acabaram os docs
preciso que voce assuma a frente
docs/development/future/PLAN-UNIVERSAL-PLATFORM.pt_BR.md" (nome original;
renomeado para `IMPLEMENTATION-UNIVERSAL-PLATFORM` na mesma promoção) →
respondido "Promover p/ development/ e implementar".

### Contrato

1. `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` + `.pt_BR.md` **saem de `future/`** e passam a
   ser trabalho corrente em `docs/development/`, estado **EM
   DESENVOLVIMENTO**.
2. O portão de promoção de `docs/development/README.md` §4.3 ("decisão +
   SYSTEMS fechado (R12)") é **sobreposto por esta decisão**: a mantenedora
   autoriza abrir a frente com o SYSTEMS ainda em andamento.
3. O documento deixa de ser "só visão": a regra do próprio cabeçalho
   ("não implementa nada, não move arquivos, não abre frente nova") é
   **revogada e reescrita** no mesmo commit da movimentação.
4. O **primeiro ponto de entrada é o Estágio 1 (consolidação SYSTEMS, §10
   Estágio 1)** e as recomendações executáveis **R1–R12 (§15)** — não o Tier
   6+ (AUTOMATION/INFRA/DATA/…), que mantém sua ordem em `roadmap.md` §23.
5. A visão/design do documento **não** é editada por agentes: só as claims de
   estado são sincronizadas com o código real (regra dos três estados), e cada
   unidade de implementação segue Q0–Q7 como qualquer outra mudança.
6. A semântica congelada do core permanece congelada; toda mudança é aditiva e
   por alvo (R6/R7 valem).

### Invariantes

- O plano **não** vira licença para quebrar o freeze (a regra 6 do `AGENTS.md`
  continua valendo para operadores/precedência/ordem de avaliação).
- `roadmap.md` §23 continua sendo o **plano único ordenado**; este documento
  fornece a arquitetura dos Tiers 6–12.
- Nenhum domínio pesado (`ml`/`bio`/`hpc`) entra na stdlib base (R1).

### Alternativas rejeitadas

- **Respeitar o R12 e fechar o SYSTEMS primeiro** (fazer os itens do TIER 1
  antes de promover) — rejeitada pela mantenedora, que escolheu promover
  agora.
- **Promover só o documento sem abrir implementação** — rejeitada: a diretriz
  é "promover **e implementar**".

### Implementação

- Arquivos movidos: `docs/development/future/PLAN-UNIVERSAL-PLATFORM.md` →
  `docs/development/PLAN-UNIVERSAL-PLATFORM.md` (e o par `.pt_BR.md`; promoção de
  17/09), depois **renomeados** para
  `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` ao dividir em rastreador executável +
  companion de visão
  `docs/architecture/UNIVERSAL-PLATFORM-VISION.md`.
- Fila: `roadmap.md` §23 TIER 6–12 agora aponta para o novo caminho e registra
  a sobreposição do R12; a primeira unidade executável sai do Estágio 1 /
  R1–R12.
- Acompanhamento: `DOING.md` + `DOING.pt_BR.md`.

### Evidência

- Movimentação + reescrita do cabeçalho + sincronização de referências no
  mesmo commit; `docs-lang.sh check` 0/0/0; `check_500.sh` rc=0.

### Relações

- `Overrides: R12` (a meta-regra "não interromper o presente").
- `Related:` `roadmap.md` §23 (Tiers 0–12), §22 (Plataforma Universal),
  `AGENTS.md` §"Invariantes da plataforma".

---


## D-UI-TOKENS — tokens do design system (Fase 10, pilar 9)

**Data:** 2026-09-18

**Estado:** `DECIDIDO`

**Origem:** autorização de escopo da mantenedora (chat, 18/09 — a resposta
"todas" para as Fases 8–11 do Component Core). A superfície exata (nomes
dos namespaces, nomes dos membros, valores em px) é um congelamento de API
(regra 6); fica travada aqui, seguindo a convenção **D-UI-STYLE Q2** (Int nu
é pixels) e a grade de 8px (consenso Material/Tailwind) para que os tokens
sejam previsíveis e idiomáticos. A *forma* (cinco namespaces de constantes)
é o contrato; os valores específicos da escala podem ser ajustados pela
mantenedora sem mudar a forma da API.

### Contexto

`KOFUI-AUDIT.md`/`architecture.md` §2.1 pilar 9 ("Design system — Theme +
tokens") lista os tokens `Color/Type/Spacing/Border/Radius/Elevation`. Hoje
só existem `Color` (via `Palette`/`Color`) e `Theme`; não há tokens de
Spacing/Border/Radius/Elevation/Typography, então os layouts usam literais
hard-coded (`padding: 16`, `border-radius: 4`) em vez de nomear a intenção
de design.

### Contrato

1. **Superfície.** Cinco namespaces de constantes, cada um um fold em
   compile-time para um `Int` nu (px):

   | Namespace | Membros (→ px) |
   |-----------|----------------|
   | `Spacing` | `xs`=4 `sm`=8 `md`=16 `lg`=24 `xl`=32 |
   | `Radius` | `none`=0 `sm`=2 `md`=4 `lg`=8 `full`=9999 |
   | `Border` | `hairline`=1 `thin`=2 `medium`=4 `thick`=8 |
   | `Elevation` | `none`=0 `sm`=1 `md`=2 `lg`=3 `xl`=4 |
   | `Typography` | `xs`=12 `sm`=14 `md`=16 `lg`=20 `xl`=24 `hero`=32 |

2. **Fold no compilador (frontend compartilhado).** Um `FieldAccessExpr`
   `Namespace.membro` é folding para `KofLoadLiteral(Int)` pelo mesmo
   idiom que o `Palette`. Como o fold vive no frontend compartilhado, os
   quatro targets (JVM/Native/Script/JS) carregam a mesma constante —
   **paridade cross-target por construção**.

3. **R6 — sem 0 silencioso.** Um membro inexistente (`Spacing.huge`) e uma
   chamada de método num namespace (`Spacing.of(4)`) são um diagnóstico de
   compile-time **`SEM079`** (mensagem em inglês, lista os membros válidos).
   O buraco silencioso pré-existente `Palette.nope` é um gap separado e
   catalogado (é superfície da lane compiler; os tokens não o replicam).

4. **Aditivo e retrocompatível.** Nenhum identificador existente é
   sombreado (`Spacing`/`Radius`/`Border`/`Elevation`/`Typography` eram
   não usados). Os tokens compõem com os primitivos existentes
   (`Label.setFontSize(Typography.lg)`, `Style("padding: " + Spacing.md + …)`
   é a forma *literal* do style — os tokens carregam os mesmos valores px).

### Invariantes

- Os cinco namespaces são **apenas constantes** (sem métodos, sem forma
  `var`).
- Valores são `Int` px puros (D-UI-STYLE Q2); `full`=9999 é o idiom CSS de
  "pílula" (totalmente arredondado).
- Diagnósticos seguem D-DIAG-EN. **Emenda (18/09, colisão entre lanes):** os
  códigos desta decisão e da D-UI-STYLE foram re-numerados — a lane compiler
  já havia publicado `SEM073` (aridade do `reduce`, `MemberCallTyper`) e `SEM074`
  (método em primitivo, `SemMethodCallTyper`) no `beta-0.4.0`. Style:
  Style: `SEM073/74/75` → **`SEM076/77/78`**; tokens: `SEM076` → **`SEM079`** (renúmero final 18/09: o `beta-0.4.0` tomou `SEM073/74` para reduce-arity/primitive-method e `SEM075` para o diagnóstico static-field — esta lane deslocou de novo para manter unicidade).
  Só os rótulos mudaram; nenhuma semântica mudou (as decisões valem como decididas).
- Sem superfície de runtime: o fold é em compile-time, então não há no-op
  por target a documentar (diferente do UI001) — o valor está na IR.

### Alternativas rejeitadas

- **Um namespace `Tokens` com membros aninhados** (`Tokens.Spacing.md`):
  rejeitado — um nível extra de cerimônia sem ganho; o `Spacing.md` plano
  casa com `Palette.red` e a tabela de idiom.
- **Objetos de runtime / tokens `var`**: rejeitado — tokens são constantes
  em compile-time; uma forma de runtime adicionaria um no-op por target
  (família UI001) sem benefício.

### Implementação

Reivindicado no `DOING.md` (Fases 8–11, lane UI/style). `KofUiTokens.java`
(tabela de fold + mensagens) + os seis pontos de toque do `Palette`
(`SemExpressionTyper`×2, `ExpressionTyper`, `ExpressionLowerer`,
`ExpressionMethodCallLowerer`, `MemberCallTyper`).

### Evidência

`UiTokensE2ETest` 7/7 — tabela golden em JVM + Native + Script (mesmo fold
compartilhado → saída idêntica), DOM JS (o valor chega no texto renderizado),
`SEM079` membro inexistente, `SEM079` chamada de método, composição com
`Style`/widget; suíte 4-módulos verde fora do flake pré-existente §252 e dos
reds cross §181/§256; `docs-lang.sh check` 0/0/0.

### Relacionamentos

- Entrega o pilar 9 de `architecture.md` §2.1 (Fase 10).
- Relacionado: D-UI-STYLE (convenção px da Q2), `Palette` (idiom de fold),
  UI001, D-DIAG-EN.

---

## D-UI-APPSTATE — Fase 8: `AppState(initial)` é o store-raiz da aplicação

**Data:** 2026-09-18

**Estado:** `DECIDIDA`

**Contexto:** a `docs/ui/architecture.md` §2.6 define três escopos de
estado. O local (`state`/`text`/`flag` no `Component`) e o `Store`
compartilhado (get/set/subscribe/unsubscribe) já funcionavam; faltava o
escopo **raiz da aplicação** (Fase 8). Ao ligá-lo, o §301 foi medido e
corrigido primeiro: o `Store.unsubscribe` do JS era no-op silencioso
(identidade wrapper-vs-raw), então a perna "cleanup" do §2.6 não tinha
primitivo funcional — ver `known-bugs.md` §301.

**Decisão (contrato mínimo):**
- `AppState(initial)` — um argumento, devolve o store do **escopo da
  aplicação**: um **singleton create-or-get** sobre a máquina do Store. A
  primeira chamada cria com `initial`; as seguintes devolvem o MESMO handle
  e **ignoram** o `initial` (documentado; o valor vive no runtime, um slot
  por processo).
- O handle devolvido é um `Store` — os métodos são exatamente
  `get`/`set`/`subscribe`/`unsubscribe`; nenhuma superfície nova, nenhuma
  máquina `State`/`Signal`.
- O ponto é a alcançabilidade: components chamam `AppState(0)` em qualquer
  lugar em vez de prop-drilling de handle.
- `storesLive()` conta o slot do app-state (probe de leak inalterado).
- O cleanup de inscrições no unmount segue **manual** (`unsubscribe(h)` —
  agora real pelo §301): atribuir inscrições automaticamente a components é
  contrato maior (qual component é o "current" durante um subscribe?) —
  regra 6, não decidido aqui.
- JVM/Native mantêm os no-ops documentados do Store (UI é KofJS —
  backend-parity); o singleton JVM ainda conta uma vez em `storesLive()`.

**Amendável sem quebrar código:** a semântica de ignorar o `initial`
posterior e extensões futuras (p.ex. auto-unsub) são registradas aqui
primeiro; a forma da chamada é congelada.

**Evidência:** `ComponentCoreE2ETest.appStateIsCreateOrGetSingleton` +
`appStateDrivesComponentsWithoutPropDrilling` (VERMELHO pré-feature — SEM015
"Undefined function: 'AppState'"; verde pós-wiring); golden medido por
target (JS `10,10,x=10,x=42,,1`; JVM `0,0,"",1`; Native `0,0,"",0`);
ComponentCore 24/24 + UiE2E 29 + browser 28 + Router 4 + style/tokens 17 +
CoreRegression 102 + CompilerDriver 256 verdes.

- Relacionado: D-UI-STYLE, D-UI-TOKENS, §301, D-BACKEND-SEMANTICS (no-op stores).

---

## D-UI-DIFF — Fase 9 (atualização parcial / reuso de nó): **DECIDIDA (B) — reuso da raiz por tipo**

**Data:** 2026-09-18 · **Data da decisão:** 2026-09-18 (mantenedora, enquete multi-escolha na sessão)

**Estado:** `DECIDIDA` — opção **(B) reuso da raiz por tipo**. Fila aberta em `DOING.pt_BR.md` (dono .17, lane kof-ui).

**Contexto:** a Fase 9 de `architecture.md` quer "atualização parcial":
reusar o nó DOM quando o view re-renderiza o mesmo widget na mesma posição.
Hoje o re-render é rebuild+prune: o §300 tirou o vazamento, mas a identidade
ainda é recriada — **medido 18/09 (host embarcado, probe scratch):** o
handle do label-raiz de um `view (s) -> Label("v="+s)` é `3` após 1 state
write e `7` após 5 (um handle novo por render; subárvore antiga podada,
correta mas nova). Consequências: toda referência que o usuário guardou a um
widget de um render anterior fica obsoleta, e estado do DOM real (focus de
input, cursor, scroll, transições CSS) se perde a cada state write.

**Opções (não decididas aqui — regra 6, ciclo de vida/identidade do §2.7 é
congelado):**
- **(A) Reconcilador posicional completo** (VDOM-lite): builders emitem
  descritores, um diff por (posição, tipo) conserta propriedades no lugar.
  Maior ganho, maior risco: exige tabela de cópia de propriedades por família
  de widget e faz handles antigos continuarem vivos — mudança de contrato de
  identidade.
- **(B) Reuso da raiz por tipo** (primeira fatia): quando raiz antiga e nova
  são do mesmo tipo, copiar as propriedades de valor para o nó ANTIGO e
  descartar o novo — um widget por vez, mensurável, mas ainda é mudança de
  identidade de handle na raiz (decisão de aliasing obrigatória).
- **(C) Manter rebuild; sem reuso.** Honestos e simples; a perda de
  focus/cursor fica limitação documentada (estado atual).

**Recomendação (lane UI/style):** (B) com nota explícita de identidade — a
menor unidade coesa que conserta a dor visível (perda de focus no caso
comum de widget único na raiz) sem camada VDOM. A decisão (qual opção + o
contrato de continuidade de handle) é da mantenedora.

**Decisão (mantenedora, 18/09) — o contrato de identidade de (B):** quando
o render anterior e o próximo de um component `view` produzem o **mesmo
tipo de raiz**, o nó DOM ANTIGO é mantido e as propriedades de valor são
copiadas do nó fresco para ele; o handle raiz antigo **continua vivo**
(continuidade de identidade — este é o chamada de aliasing que a opção B
exige). Tipo de raiz diferente → rebuild + prune exatamente como hoje
(§300). Sem camada VDOM, sem chaveamento, sem diff posicional de filhos
nesta fatia. Prova esperada: o probe mostra o MESMO handle entre state
writes quando o tipo é estável; elemento com focus sobrevive ao write;
troca de tipo ainda poda (sem regressão do §300).

**Relacionado:** §300 (prune), §301 (unsubscribe), linhas da Fase 9 na
audit, D-UI-APPSTATE (postura do unsub manual — agora substituída por
D-UI-AUTOUNSUB), D-UI-CANCELLED.

---

## D-UI-AUTOUNSUB — inscrições do Store têm escopo automático por component: **DECIDIDA (A)**

**Data:** 2026-09-18 (mantenedora, enquete multi-escolha na sessão)

**Estado:** `DECIDIDA` — opção **(A) escopo automático por component**.

**Contexto:** desde o §301 `unsubscribe(h)` é primitivo real, mas a limpeza
é manual — um component que `subscribe` no mount vaza o callback (e o
closure capturado) depois que o component é podado (o registry do §300 sabe
exatamente quando). O D-UI-APPSTATE registrava "limpeza continua manual"
como postura ANTERIOR à decisão.

**Decisão:** um `subscribe` feito **enquanto um component é o alvo de
render corrente** fica vinculado àquele component; quando o component sai
da árvore (poda de subárvore, §300), o runtime dá unsubscribe
automaticamente. Inscrições fora de contexto de component (escopo de
aplicação — ex.: um observador de `AppState` criado no `main`) mantêm
semântica manual — o primitivo do §301 continua valendo para elas.
Backward compatível: nada que compila hoje muda de comportamento, exceto
inscrições vazadas que morrem com o component.

**Relacionado:** §300 (registry de subárvore), §301 (unsubscribe),
D-UI-APPSTATE (postura substituída), D-UI-DIFF (mesmo encanamento de
ciclo de vida).

---

## D-UI-CANCELLED — `cancelled()` em ações async de UI: **DECIDIDA (A) — escopo do component de origem**

**Data:** 2026-09-18 (mantenedora, enquete multi-escolha na sessão)

**Estado:** `DECIDIDA` — opção **(A) true quando o component de origem saiu
da árvore**.

**Contexto:** `cancelled()` dentro de callbacks async de ação UI hoje é
conservador (quase sempre `false`) — uma resposta `spawn`/`http` tardia pode
escrever numa subárvore DOM que o §300 já podou. A opção B (por versão de
estado) foi rejeitada por agressiva demais (mata updates legítimos, mudança
de contrato pesada); a C (handles manuais) foi rejeitada por cerimônia.

**Decisão:** a ação lembra a instância de component em que foi criada;
`cancelled()` retorna `true` quando o nó daquele instância não está mais na
árvore viva (o mesmo registry do §300). Callbacks async devem guardar o
toque de DOM com `cancelled()` — quando true, a resposta é descartada.
Efeitos colaterais não-DOM são responsabilidade do programador (inalterado).

**Relacionado:** §300, D-UI-AUTOUNSUB (mesmo substrato de ciclo de vida),
Fase 8 (`view`/ações), D-BACKEND-SEMANTICS (`spawn`/`await` congelados —
isto é observabilidade de UI, não mudança de contrato de concorrência).

---

## D-UI-SCOPE — atualizações de regra do `kof.ui`: **DECIDIDA — a única exceção nomeada à regra 5**

**Data:** 2026-09-18 (mantenedora, enquete multi-escolha na sessão)

**Estado:** `DECIDIDA` — exceção ratificada; este registro converte a diretiva
verbal em contrato.

**Origem:** diretiva da mantenedora no chat, 18/09/2026: "regra do ui so vale
pra js e webasm" — atualizações de regra do kof.ui valem apenas para KofJS e
Kof WebASM, não para JVM/Native. A fala chegou sem id de decisão (regra 6:
contratos mudam só por decisão registrada). Este registro a ratifica na forma
escolhida pela mantenedora (regra única nomeada, redação recomendada para a
condição de WASM).

### Contexto

A regra 5 do AGENTS torna a paridade absoluta lei ("mesmo output em todo
target"). O trabalho de regras do kof.ui sempre foi só-JS na prática:
`kof_dom_patch` (§257/§300), cache de diff (D-UI-DIFF), `cancelled()`
(D-UI-CANCELLED), o Router (Fase 7) — os lados JVM/Native são no-op ou degrade
por design. Registrar isso como "gap de paridade" era codificar intenção como
dívida. A fala da mantenedora transforma a prática em lei; esta decisão a torna
exceção **nomeada**, para que ledger, matriz de paridade e consultas de CI
parem de tratá-la como bug.

### Contrato

1. **Atualizações de regra do `kof.ui`** — mudanças na semântica da linguagem
   de UI (regras de renderização, re-render dirigido por estado, propagação de
   sinais, `when`/`each`, diffing, cancelamento/`cancelled()`, o substrato de
   ciclo de vida do §300) — são a **única exceção nomeada** à regra 5. O dever
   de paridade vincula **KofJS** (hoje) e **Kof WebASM** *no instante em que
   `Target.WASM` existir* (o enum é `JVM/NATIVE/ANDROID/JS` — medido 18/09; não
   há superfície WASM a quebrar ainda). **Não** vincula JVM/Native, nunca: a
   ausência de regras de UI lá é o design, não um gap.
2. **Não** está dentro da exceção: o restante de `kof.ui` (assinaturas de API e
   o que a matriz de paridade já mede linha a linha — inalterado), tudo fora de
   `kof.ui`, e a regra 5 para semântica de core e outputs de stdlib. A exceção
   é **prospectiva**: impede *novas* obrigações de paridade em JVM/Native para
   atualizações de regra; não reescreve linhas existentes da matriz.
3. A exceção é **nomeada e enumerável** (exatamente esta superfície, exatamente
   este conjunto de targets). Não é template: qualquer exceção futura exige um
   novo `D-` ratificado pela mantenedora (regra 6).

### Consequências

- `docs/backend-parity.md` ganha a cláusula de exceção sob a seção Princípio,
  EN+PT, no mesmo commit desta decisão.
- Uma mudança de regra de UI é entregue com testes só em JS (+ WebASM quando
  existir); **não** adicionar asserções de paridade JVM/Native para o
  comportamento de regra, e não abrir item em `known-bugs` pela ausência.
- Os registros existentes D-UI-STYLE / D-UI-TOKENS / D-UI-DIFF / D-UI-AUTOUNSUB /
  D-UI-CANCELLED são todos consistentes com esta exceção (embarcaram só no
  KofJS, de fato).

### Relacionado

- Regras 5, 6, 10 do AGENTS; `docs/backend-parity.md` §Princípio.
- D-UI-DIFF, D-UI-AUTOUNSUB, D-UI-CANCELLED (o substrato que isto rege).
- `IMPLEMENTATION-UNIVERSAL-PLATFORM` R7 (degrade honesto no browser) —
  relacionado mas distinto: R7 é degrade de runtime com diagnóstico, isto é
  exclusão de escopo.

---


## D-TRIAGE — a checagem de filosofia precede a issue (portão docs-first)

**Data:** 2026-09-18

**Estado:** `DECIDIDA`

### Contrato

Um pedido que importa uma **pilha estrangeira** para o Kof (tags HTML/CSS/
`innerHTML` no `kof.ui`, camadas de framework, engines de template — caso
#449 "RawView") NÃO é feature faltante e NÃO é bug: é violação de filosofia
de um autor que não leu a documentação. A PRIMEIRA resposta deve mandar o
autor para a leitura (`docs/philosophy.pt_BR.md` "O que Kof NÃO é",
`training/idioms/<área>.pt_BR.md`, `training/anti-patterns/`) com uma linha
de PORQUÊ e o idiom Kof que cobre a necessidade real. A issue só fica aberta
se a necessidade sobreviver à leitura E nenhuma abstração Kof cobri-la — a
resolução é então decisão da mantenedora (família D-UI-*), nunca a sintaxe
importada. Regra 9 do `AGENTS.pt_BR.md` (generalizando a regra 8 "Kof não é
Java"); os templates de issue carregam um checkbox obrigatório que faz o
humano assinar o mesmo portão.

### Evidência

#449 fechada pela mantenedora 18/09 (RawView com `setCss`/`setHtml`); regra
no `AGENTS.md`/`AGENTS.pt_BR.md` §"Regras de ferro" item 9; bullet de
filosofia "Não é markup disfarçado" EN+PT; checkboxes de
`feature_request.yml`/`bug_report.yml`. Mudança apenas de governança (sem
código).

---

# 4. Decisões rejeitadas ou substituídas

Esta seção é histórica. Ela não define o comportamento atual.

| ID                  | Decisão anterior                        | Estado       | Substituída por                  |
| ------------------- | --------------------------------------- | ------------ | -------------------------------- |
| §125                | `null` para primitivo dobra em zero     | `SUPERSEDED` | D-NULL                           |
| §125                | `T?` boxed seria proibido               | `SUPERSEDED` | D-NULL                           |
| §125                | `null` silencioso em retorno/atribuição | `SUPERSEDED` | D-NULL-INTENT                    |
| C18 interino        | leituras públicas por default           | `SUPERSEDED` | D-SEC.4                          |
| D-PLATFORM          | plano separado de plataforma            | `CLOSED`     | D-APP + roadmap §23              |
| D-PLAT              | plano separado de conclusão             | `CLOSED`     | roadmap §23 + Definition of Done |
| D-ASM-GATE anterior | inspeção ASM obrigatória sempre         | `SUPERSEDED` | D-ASM-GATE atual                 |
| D-BRANCH-0.5.0      | trabalho move para `beta-0.5.0`         | `SUPERSEDED` | D-QUALITY-PIPELINE-2609 / D-BRANCH-PIPELINE |

---

# 5. Relação com outros documentos

Este arquivo define **o contrato**.

Os demais documentos definem:

| Documento                | Responsabilidade                        |
| ------------------------ | --------------------------------------- |
| `roadmap.md`             | O que será implementado e em qual ordem |
| `DOING.md`               | O que está sendo executado agora        |
| `AGENTS.md`              | Regras operacionais para agentes        |
| `docs/architecture/`     | Arquitetura detalhada                   |
| `docs/stdlib/`           | Contratos e APIs da stdlib              |
| `docs/backend-parity.md` | Matriz de paridade e gaps               |
| `docs/bugs-and-gaps/`    | Bugs, gaps e regressões                 |
| `training/`              | Material de aprendizagem e corpus       |
| `CHANGELOG`              | Histórico de releases                   |

### Regra de precedência

Em caso de conflito:

1. decisão vigente neste arquivo;
2. documentação normativa específica;
3. testes de conformidade;
4. implementação atual;
5. histórico de chat.

Código e teste que contradizem uma decisão vigente indicam divergência a corrigir, não uma nova decisão automática.

---

# 6. Como registrar uma nova decisão

Use este formato:

```markdown
## D-XXXX — título

**Data:** YYYY-MM-DD
**Estado:** DECIDED | IN_PROGRESS | IMPLEMENTED | PARTIAL |
BLOCKED | SUPERSEDED | REJECTED | CLOSED
**Escopo:** ...

### Contexto

Por que a decisão foi necessária.

### Decisão

Contrato aprovado, sem detalhes de implementação desnecessários.

### Invariantes

O que não pode ser quebrado.

### Alternativas rejeitadas

Somente quando necessário para preservar o raciocínio.

### Implementação

Referência ao roadmap ou DOING.

### Evidência

Testes, commits, matriz ou documentação.

### Relações

- `Supersedes: ...`
- `Depends on: ...`
- `Related: ...`
```

---

# 7. Regra final

**Decisões são permanentes até serem substituídas. Implementações são revisáveis.**

O código pode mudar.

O teste pode mudar.

O roadmap pode mudar.

O contrato só muda por decisão registrada.

---

## D-POLL-19 — todas as decisões pendentes resolvidas (enquete de múltipla escolha, mantenedora 19/09)

**Data:** 2026-09-19 · **Estado:** `DECIDIDO` (lote) · **Respostas (mantenedora):**
D1-A · D2-A · D3-A · D4-A · D5-B · D6-A · D7-A · #401 = "BUG REAL — `List<Int>` é
diferente de `List<String>`" (= opção A, rejeição em compile-time) · X8-A · LSP-A
(rename cross-file) · LSP-A (assinaturas de hover via `StdCatalog`).

| # | Decisão (opção) | Destrava / fila |
|---|---|---|
| D1 (A) | re-baseline do auto-collect do GC x86 **aprovado agora** | 1.2.2/1.2.3 seguem; portão do Estágio 6 aberto — execução = lane nativa |
| D2 (A) | registry MVP = **local + GitHub Releases como host oficial** (publish = Release com artefato + SHA256SUMS) | 1.5.3 ⛔→aberto; face `kof deploy --publish` (lane docs→plataforma); 8.2 gerenciador de pacotes |
| D3 (A) | **plano de design do bare-metal/bootável autorizado** | 1.7: doc de plano em `docs/development/` (lane nativa rascunha, mantenedora revisa) |
| D4 (A) | **padrão conservador**: todo namespace nasce `experimental`; promoção por-namespace com o DoD do R5 | R5; `docs/backend-parity.md` §Tiers (linha do default adicionada 19/09); **gate de máquina 21/09** — tier em `scripts/stdlib_boundary.txt` + `scripts/check_stdlib_boundary.sh` (recusa tier ausente/inválido e `stable` sem pin) |
| D5 (B) | **sem sintaxe nova** — recursos escopados = `close()` + `try/finally`; `using` está FORA | 6.5 entrega padrão, não gramática; `future/scoped-resources` segue design-only |
| D6 (A) | ABI struct/array do R3: **spec escrita primeiro, revisão, depois código** | spec `docs/ffi-abi-structs.md` (rascunho da lane docs→plataforma 19/09, design-only); implementação = lane compilador |
| D7 (A) | value records (TIER 2.7) com **front aberta agora** | fila 2.7.1+ do roadmap §23 ativa — lane compilador (coordenação com Cluster A) |
| #401 | **bug real**: `List<Int>` atribuído como `List<String>` deve ser REJEITADO em compile-time | Cluster A §270/§271 (`.22`) executa; freeze regra 1 respeitado — o código que compila hoje FALHA no runtime, então apertar casa com o contrato documentado |
| X8 (A) | runner `kof.test` implementado **exatamente como o roadmap §G6 especifica** | X8 fatia 3 — lane docs→plataforma |
| LSP-A (rename) | **rename cross-file via WorkspaceEdit** na mesma convenção textual dos `references` (honestidade "primeiro hit" documentada) | continuação X10 — lane docs→plataforma |
| LSP-A (assinaturas) | assinaturas no hover: **`StdCatalog` estendido com assinaturas extraídas do typer** (fonte única) | continuação X10 — lane docs→plataforma |

**Evidência:** mensagem da mantenedora 19/09 ~03:5x (-03), respostas de múltipla
escolha em uma linha; o commit de ratificação atualiza a tabela D de
`IMPLEMENTATION-UNIVERSAL-PLATFORM.md` (+PT), `roadmap.md` §23 (D7),
`backend-parity.md` (D4) e `known-bugs.md` §270 (#401).

---

## D-TROOL — `Bool` nunca é nullable; os três estados vivem em `Troolean` (mantenedora 19/09)

**Data:** 2026-09-19 · **Estado:** `DECIDIDO` · **Revisão de:** a face
`Nullable(Bool)` do D-NULL-INTENT (família §295/§306) · **Evidência:** diretiva
da mantenedora no chat 19/09 ~12:0x (-03): "se voce declara uma variavel
nullable e nao instancia ela, ela ja tem valor null por padrao, a tentativa de
atribuir null a um nullable via codigo precisa continuar sendo recusada. null
so existe como valor se a variavel nao for instanciavel ou se o retorno de
alguma funcao vier null. alem disso nao deve interferir nos valores de
primitivos e boolean nao pode ser nullable. so existe 2 valores possiveis pra
ele. se quiser true, false, null use troolean, que tem 3 estados. cria a logica
do tipo trool."

### Contrato (medido 19/09 no jar do tip — itens 1–3 JÁ são o comportamento atual, agora ratificados)

1. **Nullable não-instanciado = `null`** — `String? s`, `Int? q`, `Bool? b` (até
   o item 4 desta decisão) declarados sem inicializador já imprimem/comparam
   `null` em JVM, Script e JS (medido). Ratificado como contrato.
2. **Atribuição direta `= null` continua recusada** — `SEM048` (null só chega a
   um `T?` via API — `map.get`, `readLine`, função que `return null` num `T?`) —
   inalterado desde 10/09 (D-NULL-INTENT/SG-008).
3. **Primitivos não são tocados** — `Int n` não-nullable mantém default `0`
   (`println` → `0`, medido); primitivos nullable (`Int?`…) mantêm a
   representação boxed e o default `null` do §295. Esta decisão não muda NADA
   para eles.
4. **NOVO — `Bool` tem exatamente dois valores.** `Bool?` vira diagnóstico de
   compile-time **`SEM095`** (`"Bool tem exatamente dois valores (true/false) —
   para true/false/desconhecido use Troolean"`). Vale para declarações,
   parâmetros, retornos e argumentos de tipo (`Nullable(BOOL)` do usuário). O
   corpus tem **0** ocorrências de `Bool?` (medido em `training/`, `learn/`,
   `docs/language/`); só 4 arquivos de teste internos a carregam, e migram com
   a mudança. SEM094 fica reservado ao gate de switch-return do PR #481 (bot,
   fechado sem merge) — se ele reaparecer primeiro, os códigos trocam e esta
   entrada é atualizada.
5. **NOVO — `Troolean`**: tipo nominal de três estados `{true, false,
   unknown}`.
   - unknown chega exatamente como `null` nas regras 1–2: declaração
     não-instanciada (`Troolean t`) ou API/função retornando `null` nele;
     atribuição literal `= null`/`= unknown` NÃO é adicionada (mesmo espírito do
     SEM048).
   - **Lógica forte de Kleene** (o "trool" canônico): `!` troca T/F e mantém U;
     `&&` = F-dominante (F∧qualquer=F; senão U se há U; senão T); `||` =
     espelho (T-dominante). `!`, `&&`, `||` sobre Trooleans seguem essas tabelas.
   - igualdade `==`/`!=` contra `true`/`false`; o teste do estado unknown é o
     intent-check `== null` do D-NULL-INTENT (`t == null` significa "é
     desconhecido" — nenhuma sintaxe nova); `println` mostra `true` / `false` /
     `null` (mesmas faces de hoje).
   - em **posição de condição** (`if`/`while`/if-expr): `if (t)` ≡
     `if (t == true)` — idêntico ao açúcar §306(a) ratificado para `Bool?`
     (UNKNOWN pega o ramo false; documentado, não silencioso).

### Deliberação de implementação (decisão de lane dentro do contrato decidido)

`Troolean` baixa para a **maquinaria boxed-Boolean nullable que já funciona**
(§295/§306) em JVM/Script/JS — o front-end desaçúcar as tabelas de Kleene em
comparações `== true` / `== false` / `== null` + árvores de if-expr que os
backends já emitem corretamente; nenhuma classe de runtime nova por backend.
Native: a face boxed-`T?` lá é a frente aberta do PR #465 (fila 2 do
D-NULL-INTENT) — se `Nullable(Bool)` ainda não se comporta em Native,
`Troolean` entra com diagnóstico honesto `NAT-TROOL001`, nunca fallback
silencioso (regra 6 do freeze / R6).

### Fila

1. Front-end: registrar `Troolean`; `SEM095` em todo `Nullable(Bool)` escrito
   pelo usuário; desaçúcar Kleene; faces de condição/println/`==` — provas
   JVM+Script+JS via `runAll3`; os 4 arquivos de teste com `Bool?` migram para
   `Troolean` (mesmas asserções — as faces de truthiness do §306 sempre foram
   sobre a leitura de 3 estados).
2. Face Native (medir; diagnóstico-ou-funciona — nada inventado).
3. Corpus: `training/idioms/` (errors/control-flow) + `fake-idioms.md` (linha
   `Bool?` → Troolean), `docs/language-reference/types.md`, nota de revisão no
   D-NULL-INTENT, entrada de migração no CHANGELOG (linha 0.4.0), célula da
   matriz em `backend-parity.md`.

### Relacionadas

Fecha a família por decisão: **#462** (`Bool?` em contexto de valor →
VerifyError) e **#486** (`&&`/`||` sobre `Bool?` vazam `null` no JS /
VerifyError no JVM) — os relatos são reais, mas a correção deixou de ser
"far o `Bool?` funcionar em posição de valor": `Bool?` está sendo REMOVIDO; as
faces viram testes de `Troolean` na fatia 1.

---

## D-KOF-FIRST — contrato interno antes da comparação externa (`KOF-primeiro, externo-depois`)

**Data:** 2026-09-19 · **Estado:** `DECIDED` (ratificado 19/09/2026 — flip `PROPOSTO`→`DECIDED` executado na sessão da lane PR-EXTERNA, 19/09; o texto da regra não muda em relação à proposta da mantenedora `KOF_FIRST_CONTRACT_RULE.md`. A partir daqui é contrato ratificado, não mais apenas regra de trabalho) · **Escopo:** triagem de issues/PRs, bug hunting, classificação de gaps, uso de referências externas · **Relacionadas:** `D-NOT-JAVA` (regra 8), `D-TRIAGE` (regra 9), a regra de precedência do §5

### Contexto

O risco não é uma issue errada; é a linguagem evoluir por acidente. Uma
expectativa estrangeira entra como "bug", recebe um patch plausível, um teste
congela o novo comportamento, a documentação passa a ensiná-lo — e a
superfície do Kof cresceu sem decisão. O repositório já carrega as peças da
resposta (regra 8 "Kof não é Java", regra 9 "a checagem de filosofia precede a
issue", `D-NOT-JAVA`, `D-TRIAGE`, e a precedência que põe o `DECISIONS.md`
acima da implementação) e, ao mesmo tempo, os casos medidos que motivaram esta
regra: #410 (`0..n` como range), #416 (`!!`), #407 (`val`/`var` top-level),
#424 (`StringBuilder`), #415 (`String[i]`), #449 (`RawView`), #483
(`name() -> Type`), #492/PR #496 (`(Int x) -> x * x`).

### Contrato

1. **Nenhum resultado externo é oráculo.** Uma língua, especificação, fórum,
   benchmark, paper ou runtime não define, por si só, o comportamento esperado
   do Kof.
2. **O reproducer deve ser Kof válido.** Antes de abrir ou validar uma issue,
   provar que o trecho usa gramática e sintaxe reconhecidas pelo Kof.
3. **O contrato do Kof vem antes da implementação.** Identificar a decisão, a
   documentação normativa, o teste de conformidade ou a regra aplicável
   *antes* de classificar o comportamento observado.
4. **O idiom Kof é procurado antes da feature estrangeira.** Se a necessidade
   já é resolvida por abstração Kof existente, rejeitar a sintaxe estrangeira
   não é bug.
5. **Divergência interna precede comparação externa.** Um bug é demonstrado
   como divergência entre o Kof e o próprio contrato, ou entre alvos regidos
   pelo mesmo contrato.
6. **Gap precisa ser provado.** Só existe gap quando a necessidade legítima
   permanece sem solução satisfatória dentro do Kof atual.
7. **A pesquisa externa só começa depois do gap.** Provado o problema interno,
   outras línguas e a literatura podem ser estudadas.
8. **Referências externas fornecem princípios, não superfície.** Extrair
   invariantes, técnicas, modelos formais, falhas conhecidas, trade-offs.
9. **Toda solução externa é traduzida de volta para Kof.** Nome, sintaxe, API,
   semântica e ergonomia são avaliados contra a filosofia, as decisões, os
   alvos e as abstrações do Kof.
10. **Mudança de contrato é decisão, não bugfix.** Proposta que muda
    gramática, semântica, operadores, modelo de tipos ou API congelada exige
    decisão explícita da mantenedora (regra 6).

### Classificação (Gate 4 — sem ela não há patch de produção)

| Categoria | Existe quando |
|---|---|
| `BUG REAL` | programa Kof válido + contrato Kof define o comportamento + implementação diverge |
| `DIVERGÊNCIA DE ALVO` | a mesma construção Kof válida se comporta diferente entre alvos sem gap honesto documentado |
| `GAP REAL` | necessidade legítima + sem sintaxe/idiom/stdlib/composição Kof adequada + sem decisão que a rejeite |
| `DESIGN REQUEST` | a intenção é alterar, ampliar ou substituir uma decisão de superfície/semântica |
| `NOT-VALID` | o reproducer depende de construção que não é Kof e há idiom Kof para a intenção |
| `AMBIGUIDADE DE CONTRATO` | docs, decisões, testes e implementação não determinam o comportamento normativo → evidências + alternativas + decisão da mantenedora, nunca fix automático |

### Gates (o pipeline, em ordem)

- **Gate 0 — o reproducer é Kof?** Conferir `docs/language-reference/`
  (grammar, syntax, types), o doc da própria feature, `training/`, `learn/`,
  `training/anti-patterns/fake-idioms.md`, este arquivo. Não é Kof → não há
  bug demonstrado; ir ao Gate 1.
- **Gate 1 — intenção e idiom.** Nunca parar em "essa sintaxe não existe":
  nomear a intenção real e o idiom Kof que a expressa. Idiom resolve →
  `NOT-VALID`.
- **Gate 2 — contrato que governa.** `DECISIONS.md` → docs normativos →
  conformidade/golden → matriz de paridade → implementação; histórico de chat
  só como evidência auxiliar. Registrar `fonte do contrato` / `enunciado do
  contrato` / `comportamento esperado do Kof`.
- **Gate 3 — medição interna.** Rodar o reproducer **Kof válido** nos alvos
  relevantes (JVM / Script / JS / Native x86 / Native riscv64-aarch64 quando
  aplicável).
- **Gate 4 — classificação.** Uma das seis categorias acima.
- **Gate 5 — prova do gap.** Para `GAP REAL`, responder *não* a todas: existe
  sintaxe Kof válida? existe idiom documentado? existe stdlib/API? existe
  composição de recursos Kof que resolve razoavelmente? existe decisão que
  rejeita conscientemente essa superfície? existe gap já catalogado?
- **Gate 6 — pesquisa externa.** Agora, e só agora.
- **Gate 7 — tradução de volta para Kof** (que problema interno resolve, que
  princípio é reaproveitável, o que é específico da língua de origem, conflito
  com alguma decisão Kof, nova sintaxe/API, complexidade acidental, paridade,
  gap honesto em algum alvo, se é expressável com mecanismos existentes).
- **Gate 8 — decisão.** Mudança de contrato → proposta comparativa,
  trade-offs, impacto de migração e por alvo, recomendação técnica **sem
  auto-ratificação**, decisão da mantenedora.
- **Gate 9 — implementação e prova.** RED reproduzindo o contrato → fix da
  causa raiz → GREEN → conformidade cross-target → golden/migração → docs e
  CHANGELOG.

### Bloqueado sem decisão

PR automática de produção é apropriada **só** para `BUG REAL` confirmado,
`DIVERGÊNCIA DE ALVO` confirmada, ou implementação de decisão já ratificada.
Fica bloqueada enquanto a issue for `AMBIGUIDADE DE CONTRATO`,
`DESIGN REQUEST` ou `GAP` não ratificado. Qualquer diff de parser/lexer que
introduza forma nova aceita deve responder *qual decisão autoriza esta nova
superfície* — sem decisão, `STOP`.

### Bloco de evidência (issues e PRs)

Issues e relatórios do bug hunter carregam `KOF VALIDITY` (fonte da gramática,
fonte da sintaxe/documentação, reproducer validado como Kof), `CONTRACT`
(decisão/fonte, comportamento esperado), `MEASUREMENT` (alvos, comportamento
real), `CLASSIFICATION` e `DUPLICATE CHECK`. Se `KOF VALIDITY` não puder ser
provado, nenhuma issue é aberta automaticamente. PRs carregam a fonte do
contrato, o reproducer Kof válido, o RED antes da mudança de produção, a causa
raiz, o fix, por que a mudança altera (ou não) o contrato do Kof, a prova de
regressão e o impacto cross-target.

### Evidência

Proposta para a mantenedora `KOF_FIRST_CONTRACT_RULE.md` (19/09); regras 8 e 9
do `AGENTS.md`/`AGENTS.pt_BR.md`; `D-NOT-JAVA`, `D-TRIAGE`, regra de
precedência do §5; casos medidos #407, #410, #415, #416, #424, #449, #483,
#492/#496. Mudança só de governança: nenhum código, semântica ou superfície
tocada.
## D-SCHED-DURATION — expressões de duração idiomáticas no `scheduler.at`

**Data:** 19/09/2026 · **Estado:** `DECIDIDO` (diretriz da mantenedora no
chat: "coloca pra aceitar expressões idiomáticas também. scheduler.at(30m)
por exemplo, pode ter s, m, h, d, M, a" + "e aceitar expressões compostas
(1d&30m) por exemplo")

**Decisão (aditiva, regra 2 do freeze):** o 1º argumento de
`scheduler.at(expr, fn)` aceita, ALÉM do cron de 5 campos UTC (inalterado),
uma **expressão de duração idiomática**:

* `termo := dígitos unidade`, `unidade ∈ { s, ms, m, h, d, M, a }` — `s` segundos, `ms` milissegundos,
  `m` minutos, `h` horas, `d` dias (fixos, em ms); `M` meses e `a` anos
  avançam o CALENDÁRIO UTC (virada de mês/ano, com clamp no último dia do
  mês alvo — `2024-01-31` + `1M` = `2024-02-29`);
* composição com **`&`** (ex. `1d&30m`, `1M&15m`): os termos fixos
  (s/m/h/d) somam em ms e entram como OFFSET após o avanço de calendário;
* semântica: 1º disparo após o intervalo contado de agora, depois
  repetidamente (intervalo fixo, ou o próximo instante avançado no
  calendário para M/a — a âncora avança do disparo anterior, nunca de
  `now`, sem drift);
* string que NÃO é duração mantém o caminho cron (mesmo parser de 5
  campos, mesmos erros); duração MALFORMADA (unidade desconhecida, termo
  zero/vazio) lança com mensagem clara — nunca silencioso (R6);
* alvos: JVM + JS (mesmo algoritmo, golden de paridade via probe); Native
  mantém a recusa honesta `CRON001` em compile-time (o gap já cobre toda a
  superfície do `at`).

**Evidência:** mensagens da mantenedora 19/09 (esta sessão, lane .18).
1º consumidor: `flow.schedule(cron)` do bundle 2.1.3 do `kof.workflow`
(mesmo gap honesto no Native).

## D-WORKFLOW-RUN — `kof workflow run` é um runner completo de introspecção sobre `pipeline()`

**Data:** 19/09/2026 · **Estado:** `DECIDIDO` (enquete da mantenedora no
chat desta sessão: escolheu **runner completo (introspecção)** para a linha
2.6 e **exemplo de pipeline real + prova E2E** para a linha 2.5)

**Decisão (aditiva, regra 2 do freeze):** a linha 2.6 de
`IMPLEMENTATION-UNIVERSAL-PLATFORM.md` entrega um **runner completo**, não
um alias do `kof run`:

* um **arquivo de pipeline** é um módulo `.kf` que importa `kof.workflow` e
  define uma função top-level `pipeline(): KofWfDag`; não tem `main()` (o
  runner sintetiza a entrada). É a única convenção nova; nada do que existe
  hoje muda (`kof run` continua rodando qualquer `.kf` inalterado);
* `kof workflow list <file.kf>` — lista os jobs e suas dependências (a DAG);
  `--json` para a forma legível por máquina;
* `kof workflow run <file.kf>` — roda a dag; `--job <name>` restringe ao job
  nomeado **e suas dependências transitivas**; `--dry-run` imprime a ordem
  topológica sem executar nenhum corpo; `--json` emite o `Report`
  estruturado;
* código de saída: `0` se `Report.allOk()`, senão `1` (mesma honestidade do
  `kof test`);
* alvos: JVM primeiro (R7); JS e os demais alvos são fatias seguintes com o
  mesmo gap honesto quando um corpo de job precisa de uma primitiva que o
  alvo não tem;
* o runner é **tooling**, o pipeline é **código Kof** (VISION §4.3);
  `kof workflow run` consome o mesmo frontend — sem parser paralelo.

**Evidência:** enquete da mantenedora nesta sessão (opções: alias fino /
convenção mínima `pipeline()` / **introspecção completa** / só plano).
VISION `UNIVERSAL-PLATFORM-VISION.md:1137`; `workflow-plan.md` (2.1
assinado 19/09); precedente X9 `kof deploy` para fatias de tooling.

## D-MAKEALIVE — Kof Makealive (Estágio 3): namespace, providers, estado, superfície

**Data:** 2026-09-20 · **Estado:** `DECIDIDO` (mantenedora, enquete no chat
nesta sessão — respostas às Q1–Q4 do §6 de `makealive-plan.md`)

**Decisão (aditiva, freeze regra 2):**

* **Q1 — namespace = `kof.makealive`** (opção A). O literal do tracker
  `kof.infra` é HARD-DENY pelo gate-máquina R1 (medido:
  `check_stdlib_boundary.sh` rc=1, "official package only"; plano §2.1) — o
  nome é o domínio decidido (VISÃO §4.2). Embarca como host puro-Kof de
  namespace virtual (padrão workflow/supervisor, DD-OTP-01 A) + linha
  `platform` em `scripts/stdlib_boundary.txt`.
* **Q2 — providers v1 = superfície genérica COMPLETA**: local-FS
  (idempotência mediável ponta a ponta com zero credencial de cloud) + REST
  via `kof.http` + CLI via `kof.shell` — todos como interop (R9). Clouds
  concretas continuam pacotes oficiais (`infra-<cloud>`, R1 — nunca literal
  no compilador).
* **Q3 — estado = `kof.db` desde o dia 1** (a mantenedora escolheu a opção
  não recomendada sobre JSON/kof.io). Consequência: onde o kof.db é gated, o
  estado do Makealive é gated junto (Native = os gaps honestos
  `DB001`/`ORM001` até a frente GAPS-DB fechá-los — ver D-KOF-AS-CLOUD:
  fechá-los está no caminho do Kof-as-cloud, não é degrade permanente).
* **Q4 — host flat injetado, superfície em inglês**: `resource`/`requires`/
  `plan`/`apply`/`destroy` (sem prefixo `makealive.`; paridade com
  `job`/`dag`/`run`). O golden do 3.1 congela esses nomes.

**Evidência:** enquete da mantenedora no chat 19/09–20/09 (respostas: A /
"completo" / "kof.db desde o dia 1" / "confirmar flat + inglês"). Colisão R1
medida 19/09 (plano §2.1). **Destrava a linha 3.1 do tracker** (dono .18):
próximo = 3.1 core host + `MakealiveE2ETest`; 3.2/3.7 eram ⛔ R4 — **R4 ✅ pousou 21/09** (3.2 segue decisão de superfície regra 6; 3.7 depende dela); 3.8
(contrato do CLI `kof infra`) segue pergunta aberta (regra 6). **ATUALIZAÇÃO 21/09:** resolvido por
**`D-MAKEALIVE-SYNTAX`** (abaixo) — **3.2 DECIDIDA** (açúcar puro sobre `design()`),
que **destrava a 3.7**; **3.8 reiterada** (`kof makealive` apenas, `kof infra` não adicionado).

## D-KOF-AS-CLOUD — Kof tem que estar pronto para SER a própria nuvem

**Data:** 2026-09-20 · **Estado:** `DECIDIDO` (direção estratégica — não é
ordem de trabalho)

**Decisão:** o endgame da plataforma universal é o Kof **abrigando o Kof**: a
linguagem provisiona a infraestrutura onde roda (Makealive, Estágio 3), roda
nela (Native bare-metal/bootable, 1.7 + D3) e auto-hospeda os próprios
pacotes (registry 1.5.3 + D2). Consequência concreta para a fila: paridade
DB/ORM cross-target (as faces `DB001`/`ORM001` — Native e Android) não é
mais "gap honesto, para sempre" — é **item de caminho** do Kof-as-cloud: uma
plataforma que roda na própria infraestrutura provisionada precisa da própria
camada de estado em todo target que provisiona. Isso NÃO anula a ordem de
estágios (R12), o freeze ou o gate de qualidade — reprioriza a frente
GAPS-DB dentro das lanes existentes.

**Evidência:** mensagem da mantenedora 20/09 ("kof tem que estar pronto pra
ser a própria nuvem depois"), logo depois de escolher kof.db-desde-o-dia-1
para o estado do Makealive (D-MAKEALIVE Q3).

## D-BOOTSTRAP — objetivo final: o bootstrapper — Kof feito em Kof

**Data:** 2026-09-20 · **Estado:** `DECIDIDO` (mantenedora — o OBJETIVO
FINAL do projeto; alcançado como o último estágio da plataforma; estende
D-KOF-AS-CLOUD)

**Decisão:** o **objetivo final** (norte) do Kof é o **bootstrapper**: o
compilador Kof **escrito em Kof** (`kof feito em kof`). A implementação Java
é o bootstrap que produz a implementação auto-hospedada; depois dela, a
toolchain roda sobre a própria linguagem, e a direção "Kof como a própria
nuvem" fecha de ponta a ponta: o compilador compila a si mesmo, provisiona a
própria infra (Makealive), roda nela (Native/bootable) e auto-hospeda os
próprios pacotes (registry).

Consequências para a fila (só agendamento — a R12 continua governando a
execução):

* um **plano de design** é autorizado (precedente D3 bare-metal: plano
  rascunhado, mantenedora revisa, execução espera a ordem de estágios) —
  **BS-1 (enquete 20/09): DECIDIDO**, rascunho na lane `.18`;
* a decisão NÃO abre a frente do bootstrapper antes dos estágios existentes
  fecharem — apenas o *planejamento* é puxado para frente (o padrão de
  override do D-UNIVERSAL);
* o core Java segue sendo a referência (semântica frozen); o compilador
  bootstrap é provado **byte-a-byte contra o MESMO corpus E2E golden** (o
  corpus é o oráculo — Q0–Q7 valem para o bootstrap também, zero
  alucinação);
* é um objetivo de **plataforma**, não de domínio: nenhuma feature de
  linguagem é justificada "para o bootstrapper"; qualquer escape hatch que o
  bootstrap precise é decisão de design (regra 6), nunca acréscimo silencioso.

**Evidência:** mensagem da mantenedora 20/09 ("o final stage de tudo é
bootstrapper. kof feito em kof").

## D-DB-GAPS — os gaps órfãos de DB/ORM: rota por alvo (DECIDIDO)

**Data:** 2026-09-20 · **Estado:** `DECIDIDO` (enquete da mantenedora
20/09; lane dona `.18` — o dono anterior morreu sem sucessor: "não tem
ninguém nas gaps de db. agente morto")

**Decisão (três gaps, uma rota):**

* **DB-1 — `ORM001` no Native (linha 1.1.9):** opção **A**. O ORM chega
  como `kof_orm_*` em asm **sobre a superfície `kof_db_*` nativa que já
  existe** (mesma pilha do JVM: `JvmOrmRuntime` → JDBC → `kof_db_*`); JS
  mantém `KofJsOrmBridge`, JVM mantém o runtime host-side — Native fecha
  por último, pela R7. Sem atalho de embutir JVM, sem fallback silencioso
  (R6).
* **DB-2 — Android recusa `kof.db` (§278, linha 1.1.10):** **implementar
  corretamente — Android É JVM**, então tem que ter o **mesmo
  comportamento que o JVM**. **IMPLEMENTADO 20/09**: `supportedOn` de
  `KofDb`/`KofOrm` inclui `ANDROID`; pinado por
  `KofDbE2ETest.androidDbEmitsTheSameBytecodeAsJvm` (`Main.class`
  byte-idêntico) e pelo pin virado
  `DomainGapCodesTest.androidCompilesDbLikeJvmAndRefusesCryptoWithTheDocumentedCode`;
  §278 agora é PARCIAL (SECN/GPU abertos). A recusa `DB001` no alvo Android é levantada;
  o pin `DomainGapCodesTest.androidRefusesDbAndCryptoWithTheDocumentedCodes`
  vira paridade na face de DB. As recusas `SECN00x`/`GPU001` seguem
  honestas até aquelas pilhas de fato rodarem no Android (outras lanes; o
  mesmo princípio fica registrado aqui — assinatura regra 6: mantenedora
  20/09, "implementa corretamente. android é jvm, logo androids tem que ter
  o mesmo comportamento que jvm").
* **DB-3 — MySQL em riscv64/aarch64:** opção **B** — **estender** a
  superfície MySQL aos alvos cross (degradação não permitida; o x86 é a
  referência do contrato).

Nota de sequência (MK-1, mesma enquete): a **fatia 3.1 do makealive é
"completa" numa peça só** — núcleo + providers local-FS/REST/CLI + face de
estado `kof.db`, não um fragmento só-núcleo (a decisão Q3 de `D-MAKEALIVE`
vale desde o primeiro apply). Os gaps de DB são a **frente de
pré-requisito** desse 3.1 completo no Native; a fatia segue honesta em
JVM/JS enquanto isso (esses alvos já têm `kof.db` real).

**Evidência:** respostas da mantenedora 20/09 — DB-1 "A) kof_orm_* em asm
sobre os kof_db_* existentes", DB-2 "implementa corretamente… android é
jvm", DB-3 "B) estender MySQL p/ riscv/aarch", MK-1 "B) completo de uma
vez".

### Adendo D-DB-GAPS (21/09/2026, mantenedora) — PARIDADE TOTAL de DB em todos os alvos

Enquete (chat 21/09, na triagem do §421): perguntado qual gap-code nativo usar
para a aceitação silenciosa de schemes não suportados, a mantenedora respondeu
**paridade total — todo alvo deve ACEITAR `mariadb`, `mysql`, `sqlite`,
`mongodb`, … (sem endpoint de gap-code)**. Isso generaliza o DB-3: a superfície
de DB alcança o *mesmo conjunto de schemes* em JVM/Android/JS/Native, cada
scheme **real** (R6). Um scheme não suportado é **gap interino declarado**
apenas enquanto a fatia pousa — nunca recusa permanente, nunca aceite silencioso.

**Estado medido (21/09, esta lane — medição, não memória):**
- **JVM/Android/JS:** JDBC via host — qualquer URL JDBC com driver no
  classpath (h2, sqlite-jdbc, mysql, mariadb, postgres); o delegate JS É o JDBC
  do host. MongoDB **não** é JDBC (protocolo separado).
- **Native (x86-64/riscv64/aarch64):** `sqlite:` (libsqlite3, link-by-use) +
  `mysql://` (wire protocol em `RuntimeDb2.java`) são reais; `mariadb://`
  (compatível mysql-wire) e `mongodb://`/`oracle://` **não** são parseados —
  `kof_db_connect` registra um handle tipo-0 e a falha aparece tarde no
  `.Lorm_conn` (**§421**).
- `kof_db_type` já reserva **1=sqlite 2=mysql 3=oracle 4=mongo** → o modelo de
  tipo antecipa esta frente.

**Fatias (fila aberta em `docs/stdlib/db-parity-plan.pt_BR.md`):** S0
diagnóstico interino honesto (limpa o aceite silencioso do §421 enquanto os
schemes pousam); S1 `mariadb://` = alias mysql-wire (Native, 3 arcos); S2
paridade de schemes JDBC JVM/JS/Android (por-driver medido, diagnóstico honesto
de driver ausente); S3 `mongodb://` interop-first (driver/wire — nunca um
servidor caseiro, R9); S4 oracle (idem). Dono: frente DB/ORM (dono a nomear) +
esta lane para o plano/registros. **Não é mudança de superfície congelada** —
alarga as URLs aceitas; a API `kof.db`/`kof.orm` não muda.

## D-BRANCH-0.5.0 — trabalho move para `beta-0.5.0`; `beta-0.4.0` fica para pousos em voo + preparo da release (20/09/2026, ordem da mantenedora)

**Estado:** `SUPERSEDED` (28/09/2026) por `D-QUALITY-PIPELINE-2609` / `D-BRANCH-PIPELINE` — a esteira `lab → testing → prerelease → stable → release/x.y.z → tag` substituiu "trabalho move para `beta-0.5.0`" no cutover da 0.5.0; `beta-*` está congelada. Histórico preservado abaixo (nunca apagado, §1.3).

**Ordem (chat 20/09/2026):** "avise os outros agentes, vamos mover todo trabalho
pra branch beta-0.5.0 e começar a preparar a nova release".

**Decidido:**
- Nova branch ativa: `beta-0.5.0`, cortada do tip de `beta-0.4.0`. Todo commit
  novo (código e docs, todas as lanes) entra nela.
- `beta-0.4.0` ainda recebe o que já está em voo (ex.: WIP §374/#553 da `.22`);
  cada pouso lá é adiantado (ff) para `beta-0.5.0` pela lane docs, para as duas
  nunca divergirem em conteúdo.
- Bump de versão (`<revision>0.4.7-beta</revision>` do `pom.xml` → número novo),
  corte do CHANGELOG e tag são **itens do preparo de release** — a mantenedora
  confirma o número no corte (regra 6); ninguém bumpa unilateralmente.
- A fila do preparo morava em `docs/distribution/release-beta-0.5.0.pt_BR.md`
  (+EN) — FECHADA 28/09 (saiu de `development/`).

**Evidência:** ordem da mantenedora 20/09/2026 (chat); linhas de branch ativa do
`AGENTS.md`(+PT) e este registro no mesmo passo; issues abertas #550/#553/#554
e o guarda-chuva #555 avisados por comentário; banner no `DOING.md`(+PT) para
todas as lanes.

## D-RELEASE-1.0 — KOF 1.0 EXIT GATE: estabilização dos contratos é a meta de desenvolvimento; não existe RC/release 1.0 com qualquer item em falta ou qualquer aresta aberta (20/09/2026, ratificação da mantenedora)

**Ordem (chat 20/09/2026):** "decisão de `docs/development/future/PROPOSAL-1.0-EXIT-GATE.md`
ratificada. concordo com o planejamento. setar como meta de desenvolvimento a
estabilização dos contratos seguindo o planejamento existente nessa issue. kof
RC 1.0.0 e kof release 1.0.0 só existem QUANDO todos os pontos estiverem
correspondentes e não houver nenhuma aresta aberta".

**Decidido:**
- A proposta vira o contrato normativo, registrado aqui como `D-RELEASE-1.0`.
  O documento foi promovido de `future/` para
  `docs/PROPOSAL-1.0-EXIT-GATE.md` (+`.pt_BR.md`), o bloco de
  aprovação da §22 foi preenchido como registro mecânico desta aprovação no
  chat (palavras dela citadas como evidência), e o status do cabeçalho mudou
  para RATIFICADO.
- **O EXIT GATE (§8 + complemento D-BRANCH-0.5.0) é vinculante**: uma build só
  pode ser declarada Kof RC 1.0.0 com TODO item obrigatório satisfeito por
  evidência reproduzível na mesma candidata, e o RC só vira Stable 1.0.0 com o
  gate ainda verde e sem regressão RC→Stable. Não existe corte, tag nem
  publicação de "1.0" enquanto QUALQUER item estiver em falta ou QUALQUER
  aresta estiver aberta — essa é a definição de "todos os pontos correspondentes
  e nenhuma aresta aberta", e é responsabilidade de toda lane, não cerimônia do
  dia do release.
- **Meta de desenvolvimento (imediata)**: estabilização dos contratos pela fila
  da §23 — definir `release-blocker` mecanicamente (classificação §11 em quatro
  categorias para toda issue aberta), implementar o gate mecânico com REDs
  escritos ANTES de qualquer lógica de gate (critérios de confiabilidade §10:
  veredito do mesmo SHA, sem análise velha decidindo commit novo, sem
  false-green conhecido, fim do `CODEQL_GATE_SKIP` de rotina), validar
  ANTES/DEPOIS, testar o pacote real fora do repo, rodar a matriz final de
  alvos. Só então pode existir a primeira candidata a RC. Fila aberta em
  `docs/development/roadmap.md` §23 e claimada no `DOING.md`.
- **Q1 (quando a linha 1.0 começa)**: como proposto — quando a Mel declarar
  explicitamente aberta a linha/candidata 1.0 (item do complemento do gate);
  nada antes disso.
- **Q2/Q7 (superfície — AINDA ABERTAS, são as primeiras "arestas" a fechar)**:
  a ratificação aprova o contrato e o planejamento; não fabrica respostas que
  ela não deu. KofC e Android dentro da superfície Stable 1.0, e os candidatos
  de reforço `[? MEL]` da §35, continuam decisões da mantenedora que bloqueiam
  apenas o primeiro RC — pergunta não respondida é aresta não fechada. Enquanto
  isso, site/README NÃO PODEM implicar decisão que não existe (o site hoje
  marca KofC "Disponível" — sincronizar docs/site é item da fila).
- **Q3**: mecanismo = a classificação da §11 é normativa (toda issue aberta em
  exatamente uma de BLOCKS 1.0 / OUTSIDE 1.0 SURFACE / POST-1.0 / NOT A BUG),
  tornada mecânica por labels + ledger + o gate mecânico — nunca pela ausência
  de label.
- **Q4**: congelamento como proposto — a superfície pública congela a partir do
  primeiro RC aprovado pela Mel; estabilização, fixes, testes, docs e CI seguem
  andando.
- **Q5**: gaps podem permanecer apenas explicitamente FORA da superfície 1.0,
  com alvo conhecido, diagnóstico honesto, docs atualizadas e a decisão de
  escopo registrada.
- **Q6**: os critérios de confiabilidade do gate (§10) são aceitação vinculante,
  não aspiração; a solução técnica do Quality Gate segue frente própria (passos
  5–8 da §23).

**Não-objetivos deste registro:** NÃO autoriza corte de 1.0, NÃO faz bump de
VERSION (0.4.7-beta permanece até o release-prep, por D-BRANCH-0.5.0) e NÃO
fecha a #560 — a issue fica como fio de acompanhamento até a fila que abriu ser
executada.

**Evidência:** ratificação da mantenedora 20/09/2026 (chat, verbatim no bloco
§22 do doc e neste registro); doc EN+PT atualizado + promovido no mesmo passo;
fila no `roadmap.md` §23; claim no `DOING.md`; #560 cross-notificada.


## D-VERSION-BUMP-0.5.0 — a revisão passa a `0.5.0-beta` na branch ativa (20/09/2026, ordem da mantenedora)

**Decisão (mantenedora, chat 20/09/2026):** "faz o bump de versão em tudo no repo
pra beta 0.5.0" — a versão do produto sobe `0.4.7-beta → 0.5.0-beta` na branch
ativa `beta-0.5.0` (`D-BRANCH-0.5.0`). Isso fecha o item 3 do checklist de
release (`docs/distribution/release-beta-0.5.0.md`) e substitui a cláusula
"VERSION fica em 0.4.7-beta" do `D-RELEASE-1.0` apenas no sentido de que a fase
de preparo de release que ela reservava foi iniciada por ordem.

**Mecânica:** fonte única `VERSION` → `scripts/bump-version.sh` sincroniza o
`<revision>` do `pom.xml` e `dev/kof/version.properties` (`kof.version=0.5.0-beta`;
compiler/runtime/stdlib `0.5.0`; tooling API 21 inalterada). Docs com stamp de
**versão corrente** (README, `docs/status`, cabeçalho do `docs/backend-parity`,
cabeçalho do `AGENTS.md`, saídas de distribution/install, cabeçalhos
training/learn, stamps "Updated:" dos idiomas, exemplos de nome de artefato)
foram bumpados EN+PT no mesmo commit. Menções **históricas** (seções do
CHANGELOG, medições do `known-bugs.md` feitas em jars `0.4.7-beta`,
"Introduced:", stamps de feature como `D-TROOL 19/09`) foram mantidas —
história não se reescreve (regra 4 do freeze).

**Verificação antes do bump (item 3 do prep):** nenhum teste ou script fixa a
versão do artefato (só um comentário histórico no javadoc de
`TrooleanLawE2ETest`).


## D-1.0-EDGES — as arestas abertas estão fechadas: 5ª categoria, #564/#565 como `1.0-blocks`, KofC + Android dentro da superfície 1.0, os nove reforços da §35 obrigatórios, e a linha 1.0 abre após o release 0.5.0 + EG-1..EG-7 (20/09/2026, respostas da mantenedora às sete perguntas abertas)

**Contexto:** a ratificação do `D-RELEASE-1.0` deixou sete arestas abertas (as
perguntas `[? MEL]` da §35 do `PROPOSAL-1.0-EXIT-GATE.md`, a classificação de
#560/#564/#565 e a Q1). A mantenedora respondeu todas as sete (enquete,
20/09/2026). Este registro trava as respostas; os não-objetivos do
`D-RELEASE-1.0` (sem corte 1.0, sem tag, #560 segue aberta) continuam valendo.

**Decidido (as sete):**

1. **#560 — 5ª categoria.** O guarda-chuva de acompanhamento do gate não é
   defeito e não cabe nas quatro categorias da §11; a §11 agora tem **cinco**.
   O label foi criado como `release-tracking` e renomeado para
   **`tracking/contract`** ("Tracks an already-ratified contract; valid in
   stabilization, must close before RC"); a #560 o carrega. O gate mecânico
   (`scripts/check_release_blockers.sh`) o reconhece; uma issue nessa categoria
   é válida durante a estabilização, mas ainda precisa fechar antes do RC.
2. **#564 — `1.0-blocks`.** `kof deps resolve <owner>/<repo>@<ver>` sempre falha
   com REG002 contra os GitHub Releases reais (pickTarball corta o objeto do
   asset no "uploader" aninhado) — o contrato de pacote/deps está quebrado
   ponta a ponta.
3. **#565 — `1.0-blocks`.** Todo fat jar JVM construído pelo `kof deploy`
   embute uma cópia truncada de si mesmo como entrada `kof-app.jar` (zip
   inválido) — integridade do artefato de deploy.
4. **Q1 — quando a linha 1.0 abre.** Depois do **release 0.5.0 cortado** e dos
   **EG-1..EG-7 fechados**; só então ela declara e a primeira candidata a RC é
   cortada (EG-8).
5. **KofC — dentro da Stable 1.0, com gate próprio.** O "Disponível" do site
   fica consistente; KofC é alvo pleno da superfície 1.0 com gate próprio, não
   item fora/opcional.
6. **Android — dentro da 1.0, com gate próprio** (a opção cheia, não a parcial
   recomendada; o CI já roda o APK). Android é alvo pleno da superfície 1.0 com
   gate próprio.
7. **§35 — os nove candidatos viram gates obrigatórios** (não só os quatro
   recomendados): app real com o pacote; baseline/sem regressão; política de
   falha/flake sem false-green; snapshot da Stable Surface no RC1; identidade
   do artefato (SHA256/provenance); manifesto de evidência por alvo; corpus de
   compatibilidade; waiver formal (+ os demais itens do doc).

**Consequência — superfície Stable 1.0 = 8 alvos:** JVM, Native x86-64,
riscv64, aarch64, JS, Script, **KofC**, **Android**, cada um com gate próprio
onde aplicável.

**Não-objetivos:** NÃO autoriza o corte 1.0 (isso é o EG-8, condicionado a
EG-1..EG-7 + o release 0.5.0), NÃO muda a VERSION, NÃO fecha a #560.

**Evidência:** respostas da mantenedora 20/09/2026 (enquete); label
`tracking/contract` na #560; #564/#565 rotuladas `1.0-blocks`; ledger
`scripts/release-blockers.tsv`; gate `scripts/check_release_blockers.sh` (cinco
categorias; `--rc-gate` RED com 4 `1.0-blocks` abertos).
## D-SLOT-PIN — §383/#561 valor armazenado do "miss abençoado": o slot pinado vence; o JS coage no store (opção (a)) (20/09/2026, despacho da mantenedora)

**Data:** 20/09/2026 · **Estado:** `DECIDIDO` (despacho da mantenedora da
unidade #561, 20/09 — "o tipo pinado do slot vence, o valor é reescrito no
store; o JS DEVE bater com o consenso de 3 alvos")

**Decisão:** o dossiê §383 (três opções medidas) resolve-se com a
**opção (a) — coagir o JS ao slot**. Fundamentos, todos em lei pré-existente
(a questão está FECHADA pelo contrato, não reaberta): freeze regra 5
(divergência JVM/Native/JS no mesmo programa é bug de paridade — nunca
divergência silenciosa), a lei de medida "golden = oracle JVM" e o contrato do
miss abençoado do §126 COMO IMPLEMENTADO (box-pelo-slot no store —
`listOf(1).add(true)` guarda `1` em JVM/Script/Native). Consequências no mesmo
commit:

- **JS** (`JsCollectionOps.slotStoreCoerce`): no store pinado de List
  add/set, slot numérico + arg Bool → `v ? 1 : 0`; slot Bool + arg
  numérico/char → truthiness — no MESMO ponto da coerção dos outros alvos (o
  store).
- **JVM** (`CompilerEmissionHelpers.coerceStoreWiden`, só sites de List): o
  miss abençoado bool→Long agora emite `I2L` antes do box do slot — a face
  morria em `Long.valueOf(J)` sobre `ICONST_1` (frame crash COMP002, medido
  20/09); Script/Native já gravavam `1` e permanecem byte-idênticos.
- **Pares que cruzam a fronteira de categoria NÃO são miss abençoado**
  (`CollectionWrites.breaksPinnedList`, sites de List add/set + literal
  `listOf`): primitivo em slot de REFERÊNCIA (`listOf(listOf(1)).add(true)`)
  e o espelho (objeto em slot primitivo) quebram nos DOIS alvos compilados
  (VerifyError no load no JVM, SIGSEGV/lixo no Native) e divergem nos dois
  tolerados — a própria doutina do §126 ("rejeitar só o que quebra de
  verdade") os torna SEM056 nos quatro.
- **Map/Set continuam intocados**: lá a heterogeneidade de categorias
  vizinhas é tolerada pelo consenso 3/4 (faces S2/M1 medidas 20/09 — coagir
  moveria o JS para o lado da minoria Native).

**Evidência:** `HeterogeneousSlotPinE2ETest` 16/16
(JVM≡Script≡JS≡Native byte-a-byte, goldens de execuções JVM 20/09); §383
virado em `known-bugs.md` EN+PT; #561 respondida com a matriz medida.

## D-RELEASE-0.5.0-GATE — o gate de release 0.5.0: sete condições, todas medidas, nenhuma aresta aberta (20/09/2026, diretiva da mantenedora)

**Contexto:** o release 0.5.0 é a pré-condição que a mantenedora definiu para
abrir a linha 1.0 (`D-1.0-EDGES` Q1: release 0.5.0 cortado + EG-1..EG-7
fechados). Este registro trava o **gate de release do 0.5.0** — as sete
condições que ela declarou como prioridade para "liberar o gate 0.5.0 para
todos os agentes". Ele refina (nunca substitui) o checklist de preparação do
release e o gate §8: as sete são a **aceitação**; a fila que as satisfaz é o
`roadmap.md` §24 (EG) + `release-beta-0.5.0-prep.md` + as lanes de cada item.

**Decidido — as sete condições (lista da mantenedora, 20/09/2026):**

1. **Paridade 100% entre os alvos** — o mesmo programa produz o mesmo
   resultado observável em todo alvo da superfície 0.5.0; divergência é bug
   (regra 5 do freeze) ou gap diagnosticado `XXX00x`, nunca silencioso.
2. **Nenhuma decisão pendente** — o `DECISIONS.md` não carrega pergunta aberta
   que mude a superfície (nenhum `[? MEL]` não resolvido no PROPOSAL e nenhum
   `Estado: OPEN`); nada espera por decisão. Um item `Estado: OPEN — spec/plano
   primeiro` tem direção decidida mas plano pendente, então o gate reporta
   `NEEDS-REVIEW`, nunca verde silencioso.
3. **Todos os `.md` soltos em `docs/development/` concluídos e movidos** — a
   regra dos três estados: `docs/development/` mantém só trabalho com
   implementação pendente; doc concluído move para `docs/`.
4. **Estabilidade total** — suíte completa verde (0F/0E fora das guardas
   ambientais documentadas) + matriz de conformidade 5/5 medida na candidata.
5. **0 issues abertas que sejam bug** — nenhuma issue OPEN do GitHub que seja
   bug. A #566 entra como bloqueio (mantenedora 20/09/2026).
6. **Todas as arestas fechadas** — toda aresta aberta fechada com prova: a
   **fila EG inteira (EG-1 até EG-10)** e as issues `1.0-blocks` abertas. O
   release 0.5.0 espera até cada dono fechar e mover o próprio trabalho
   (confirmado pela mantenedora 20/09/2026 — o gate mede, não assume o item de
   outra lane).
7. **Nada pendente em bugs-and-gaps** — `docs/bugs-and-gaps/known-bugs.md` e
   `specification-gaps.md` sem entrada live/OPEN.

**Ordem de execução proposta (leitura do agente — a mantenedora pode
reordenar):** primeiro as arestas de corretude que já são `1.0-blocks` e os
known-bugs live (condições 1/5/6/7 — compartilham as mesmas causas-raiz e
desbloqueiam o resto), depois a higiene de docs/decisão (2/3), com a
estabilidade (4) medida por último na candidata congelada. O script do gate
reporta cada condição como GREEN / RED / NEEDS-MEASURE, para a lista de
trabalho ser exata, nunca a olho.

**Não-objetivos:** NÃO corta o release 0.5.0 (isso é decisão da mantenedora no
corte, regra 6), NÃO bumpa `VERSION`, NÃO abre a linha 1.0, NÃO autoriza RC
1.0 (EG-8 segue gateado no corte do 0.5.0).

**Evidência:** diretiva da mantenedora 20/09/2026 (chat); estado inicial
medido (20/09/2026): `scripts/check_known_bugs_status.sh` reporta 19
known-bugs live (EN×PT consistentes); 4 issues OPEN com label `bug`
(#561/#563/#564/#566); `scripts/check_release_blockers.sh --rc-gate` RED com 4
`1.0-blocks` abertos (#561/#563/#564/#566); `specification-gaps.md` 0 abertos.

### Adendo (20/09/2026) — string de versão, congelamento do `main` e o modelo de consumo de pacote

Três respostas da mantenedora (chat, 20/09/2026), mesmo escopo de release:

1. **O release 0.5.0 sai como `0.5.0-beta`** (mantém o sufixo beta; sem
   codename — reservado para a 1.0, `release-naming.md`). As lanes podem
   redigir o CHANGELOG e pré-preparar a tag; o corte em si segue esperando as
   sete condições.
2. **O `main` fica congelado até o release 0.5.0.** Os 12 alertas CodeQL
   pré-fix do `main` não são portados agora; o port é ação do dia do release.
   As condições 5/6 do gate são medidas na branch ativa `beta-0.5.0`.
3. **Um pacote publicado por `kof deploy --publish` é consumido como MÓDULO-FONTE
   — opção (b) da #566.** O artefato publicado precisa carregar as fontes; o
   consumo é via módulo-fonte (`import regsmoke.Greeter` resolve contra as
   fontes instaladas), **não** via jar compilado. Consequências abertas para a
   lane cli/deps: o `kof deploy --publish` precisa empacotar a superfície
   pública de fontes da biblioteca (hoje empacota só classes alcançáveis do
   `main`), e a instalação do registry (`kof deps resolve`) precisa colocar o
   módulo-fonte onde o gate de import o encontra. Os três defeitos concretos
   separados da #566 (#567 `--classpath` no-op silencioso — R6, #568 falso
   SEM015, #569 `build` emite em erro) seguem defeitos e andam independente do
   modelo.

**Não-objetivos:** a decisão do modelo NÃO muda sintaxe/semântica de Kof; ela
apenas fecha o contrato de consumo do Registry. NÃO corta o 0.5.0 nem abre a 1.0.

---

## D-FFI-STRUCT — ABI de struct/array da FFI: as decisões D6 (records por valor)

**Data:** 2026-09-20

**Estado:** DECIDIDO · **Revisão (20/09/2026):** a resposta de múltipla escolha
da mantenedora fixou **D6-1 = A+B** (lane `.14`/`.22` havia gravado a opção A)
e confirmou D6-2..D6-5. Este é o registro canônico; o texto antigo (opção A)
fica preservado em *Superseded* logo abaixo. Issues **#572/#573** (3.8b) alinham
a **B** — `D-FFI-STRUCT-B` (21/09) supersede a leitura A+B de D6-1: `record`
fica por valor read-only; o delta é o `struct` mutável por referência
(`Buffer(U8)` cobre o out-buffer).

**Escopo:** fecha as questões `D6-1..D6-5` de
`docs/ffi-abi-structs.md` (§4) — a spec que gateia a ABI de
struct/array da FFI (tracker 3.8a/3.8b/3.7). O 3.8a (`AbiLayout`) já pousou
20/09.

### Contexto

A FFI (R3) binda só o conjunto escalar `{Int, Long, Float, Double, Boolean,
String}` + callbacks (`FfiSignature`); struct/array/out-buffer/opaque são os
gaps honestos `FFI001`/`FFI002` (`CompilerPipeline.isExternBound`). A spec
`ffi-abi-structs.md` mediu a divisão de custo: o lado JVM é quase de graça (a
FFM classifica), o asm nativo é a metade caríssima (3.7). Cinco decisões
gateavam qualquer código.

### Decisão

- **D6-1 = A+B: `record` de Kof mapeia um struct C por valor (read-only, campos
  escalares) MAIS uma nova declaração mutável `struct` por referência** — a
  forma que habilita buffers in/out. O keyword `struct` é superfície nova de
  linguagem: entra só pelo gate da Simplicidade (regra 11) antes de landar.
- **D6-2 = arrays primitivos bindam; `List<T>` não.** `new Int[n]`/
  `new Byte[n]` (sintaxe existente) cruzam como `ptr` com **nenhum length
  implícito** (a API C recebe o length explicitamente). `List<T>` continua
  `FFI001` (cópia boxed por chamada é não-provada contra benchmark).
- **D6-3 = out-buffers são um kind de ABI próprio, não `String`.** Um
  out-buffer é `new Byte[n]` cruzando como `Buffer(U8, INOUT)` (copy-in /
  call / copy-back), **nunca o token `S`** (`S` = `char*` UTF-8
  NUL-terminated, read-only). O length fica argumento C explícito.
- **D6-4 = retorno por valor > 16 B.** O `Linker` da FFM esconde o sret no
  JVM; o backend **asm nativo** o implementa por ABI (SysV hidden pointer /
  AAPCS64 hidden `x8` / LP64 reference) — 3.7, lane native.
- **D6-5 = arena confinada por downcall.** `Arena.ofConfined()` aberta no
  downcall e fechada depois; um `char*` retornado é copiado e nunca
  possuído (`String` de Kof é imutável). A wart medida (um `Arena.global()`
  no caminho de argumento string) é corrigida na mesma frente — nenhum vazamento
  deixado à deriva.

### Superseded (preservado — o registro antigo de opção A, lane `.14`/`.22`, 20/09)

> A lane havia gravado **D6-1 = opção A** (só `record`, por valor, read-only,
> sem `struct` novo) e tratava D6-2/3/4 como adiados com dono. A resposta da
> mantenedora em 20/09 (A+B; os cinco decididos) a supera. Mantido para
> rastreabilidade.

**Codificação:** a gramática de tokens do `FfiSignature` ganha um token de
struct `@<fieldchars>` (ex.: `div(Int,Int):Div` → `@ii`), reusando os chars
escalares `i j f d b`; arrays/out-buffers ganham os tokens deles na fatia
própria. Qualquer coisa fora do conjunto decidido continua `FFI001`/`FFI002`
(R6), nunca silenciosa.

### Invariantes

- Zero regressão na FFI escalar/callback (`FfiE2ETest` /
  `JvmFfiCallbackE2ETest` continuam verdes).
- Native/JS mantêm `FFI001`/`FFI002` para struct até 3.7/JS pousarem — nunca
  um binding parcial silencioso (R6).
- Só campos escalares bindam na v1; um record com campo não-escalar é
  `FFI001` no JVM (honesto).

### Alternativas rejeitadas

- **B (sintaxe nova `struct`) para v1** — rejeitada: adiciona superfície de
  linguagem (regra 11) antes de necessidade provada; D6-3 cobre out-buffers.
- **`S` para out-buffers** — rejeitada (medido 20/09): `String` ≠ buffer
  mutável (mutabilidade, length, direção, tempo de vida).
- **`Arena.global()`** — rejeitada: vaza cada argumento string num processo
  de vida longa.

### Implementação

`docs/ffi-abi-structs.md` §6: 3.8a ✅ (pousado), **3.8b = binding
JVM (esta frente)** — lane compiler; 3.7 = asm nativo (lane native); fronteira
JS = decisão própria. Claim no `DOING.md` antes do código (este commit).

### Evidência

- Spec + layout medido: `docs/ffi-abi-structs.md` §1–§3;
  `AbiLayoutTest` (14 shapes × 3 ABIs, golden GCC 13.3).
- Prova E2E do 3.8b: `FfiStructE2ETest` (JVM: struct como arg + retorno de
  record via `.so` C real, byte-a-byte vs o oráculo C; Native/JS pinados
  `FFI001`/`FFI002`).

### Relações

- `Supersedes: nenhuma`
- `Depends on: D-POLL-19 (spec-first), AbiLayout 3.8a`
- `Related: R3 (FFI), R6 (nunca silencioso), R9 (interop-first), D-KOF-FIRST`

## D-ARTIFACT-TRUST — contrato de confiança dos artefatos de release do 1.0: integridade + artefato-exato + provenance de build neutra, atestada pelo workflow oficial; verificação obrigatória no portão de release e no `kof deps resolve` para pacotes oficiais (20/09/2026, respostas da mantenedora ao #571)

Decidido via multi-escolha da lane de issues (20/09). **(1) Propriedades obrigatórias:** integridade `SHA256SUMS` (jars soltos entram no ciclo) + a invariant artefato-exato do `§32.6` com enforcement MECÂNICO no `--rc-gate` (digest testado == digest publicado) + attestation de provenance de build verificável online e offline. **(2) Identidade/vendor:** o workflow oficial sob Actions é a identidade atestante; o contrato enuncia PROPRIEDADES NEUTRAS (nome de vendor não-normativo — `D-KOF-FIRST`/R11: crypto nunca caseira, nenhum vendor é oracle). **(3) Objeto:** para biblioteca, o artefato verificado é o tarball de FONTES (confirma #571-Q12); cada binário de alvo leva o próprio digest. **(4) Onde é obrigatório / falha:** o portão de release BLOQUEIA sem evidência válida; `kof deps resolve`/Registry BLOQUEIA DUREZAMENTE pacote OFICIAL sem evidência válida; comunidade = warning honesto (R6, nunca silêncio). **(5) Hardening (fila separada, fora do texto do contrato):** pin por SHA das 59 refs de actions, least-privilege por job (fim do `contents:write` workflow-level/push direto a main), rulesets no `main`+branch ativa; commits assinados/SBOM = pós-1.0. Fila: (a) enforcement de digest no rc-gate + jar-no-SUMS (lane tooling), (b) attest+verify no workflow de release (lane CI), (c) checagem de evidência no resolve com política oficial/comunidade (lane cli/deps).

## D-1.0-STABILITY-100 — o critério de estabilidade total para fechar o 1.0.0: TODO item de `docs/development/`, `docs/development/future/` e `docs/bugs-and-gaps/` 100% resolvido, com paridade total entre alvos comprovada (20/09/2026, regra da mantenedora)

Regra (ABSOLUTA, refina `D-RELEASE-1.0`): nenhum release KOF 1.0.0 enquanto QUALQUER item permanecer aberto/não entregue nos três registros — `docs/development/` (planos com implementação pendente), `docs/development/future/` (features promovidas têm de ser DESENVOLVIDAS, não adiadas para depois do 1.0), `docs/bugs-and-gaps/` (bugs, gaps de spec, matrizes de paridade) — e paridade significa a matriz multi-alvo MEDIDA (regra 5 do freeze), provada por testes/goldens, nunca por alegação. "Estável" é um ESTADO A VERIFICAR (AGENTS §Estabilidade), e o 1.0.0 é a formalização desse estado; a fila EG, o `release-blockers.tsv` e esta regra têm de concordar — fechar uma issue sem a entrega não quita o bloqueio: só a prova landed quita.

---

## D-R3-3.3 — handles e out-buffers da FFI (múltipla escolha, mantenedora 21/09/2026)

**Data:** 2026-09-21 · **Estado:** `DECIDIDO` · **Opção escolhida:** **A** (de A/B/C).

`void*` / `T*` / out-buffers são representados por um **`Handle` opaco
nominal** (não-aritmético, nunca um inteiro) mais **`Buffer(U8, INOUT)`** para
buffers de bytes por referência — consistente com a **D6-3** (`Buffer(U8,
INOUT)`, sem sintaxe nova de buffer). Sem aritmética de ponteiro.
`Pointer`/`OpaqueHandle`/`Buffer`/`Struct` continuam tipos de ABI distintos
mesmo quando um registrador carrega um endereço (R6: nunca silencioso).

- **Destrava:** R3-3.3 → aberta; a fatia de out-buffer/buffer da R3
  (pré-requisito dos Estágios 4–7, todos atrás da R3).
- **Evidência:** D6-3; `docs/ffi-abi-structs.md`.
- **Relações:** `Depends on: D-POLL-19/D6 · Related: R3, R6, R9`.

---

## D-R3-3.5 — variadics da FFI (múltipla escolha, mantenedora 21/09/2026)

**Data:** 2026-09-21 · **Estado:** `DECIDIDO` · **Opção escolhida:** **A**.

**Sem variadics gerais em Kof.** O caller da FFI passa `List`/`Array`/`Buffer`;
chamadas estilo `printf` são cobertas por overloads de aridade fixa. Razão: o
`Linker` do FFM/JVM **não tem downcall variádico**, então um marcador `...`
divergiria por alvo — mentira silenciosa (R6/R7). Uma chamada libc variádica
sem forma fixa fica como gap documentado explícito.

- **Destrava:** R3-3.5 fechada como "sem variadics" (documentado).
- **Relações:** `Depends on: D-POLL-19/D6 · Related: R3, R6, R7`.

---

## D-TYPE-VARIANCE — variance + sealed types (múltipla escolha, mantenedora 21/09/2026)

**Data:** 2026-09-21 · **Estado:** `IMPLEMENTADA` (plano aprovado; X5.1–X5.5 pousadas 21/09) · **Opção escolhida:** **C** (variance + sealed).

A mantenedora **abre** variance + sealed types como frente de núcleo do sistema
de tipos (coleções científicas + `switch` exaustivo). É **mudança de núcleo
congelado (regra 6)** e segue a disciplina **spec-first** da D6: um plano de
design escrito é rascunhado e revisado **antes de qualquer diff** de
parser/typer — nada pousa em silêncio. Type-classes seguem rejeitadas
(não-objetivo permanente).

- **Destrava:** X5 → pousada (spec-first: plano revisado, depois fatias com prova).
- **Pousado:** X5.1–X5.5 (21/09) — `sealed` + `switch` exaustivo (`SEM080`/`SEM081`),
  variância declaration-site e use-site (`SEM082`); prova `SealedTypeE2ETest`,
  `TypeVarianceE2ETest`, `UseSiteVarianceE2ETest` (45 testes verdes).
- **Relações:** `Related: regra 6, regra 11, R10, não-objetivos permanentes, D-KOF-FIRST`.

---

## D-INTEROP-REFLECT — reflexão de interop (múltipla escolha, mantenedora 21/09/2026)

**Data:** 2026-09-21 · **Estado:** `IMPLEMENTADA` (X6.0–X6.3 pousadas 22/09) · **Opção escolhida:** **A — intrínseco de compile-time**.

A mantenedora autorizou começar o X6 (21/09). Superfície congelada:

- **`interop.schema(R)`** — **intrínseco de compile-time** no namespace
  `interop`, onde `R` é um tipo `record` declarado no módulo. Resolve para uma
  **`List<Field>` imutável**, com **`record Field(String name, String type)`**
  sendo um record fornecido pelo compilador cujas entradas são os componentes
  do record, em ordem de declaração.
- **Zero reflexão em runtime**: o compilador já conhece a estrutura do record,
  então o intrínseco é dobrado em compile-time — sem `java.lang.reflect`, sem
  metaprogramação em runtime, sem `Class.forName`.
- **Mesma saída em todos os alvos** (JVM/Native/Script/KofJS): a dobra é no
  frontend, então não há gap `REF001` (a postura JVM-first é satisfeita
  trivialmente).
- **Só na fronteira**: o namespace é `interop`; não é fundação da linguagem e
  não deve crescer para reflexão geral (cerca documentada).

A reflexão é autorizada **somente na fronteira de interop** (nunca fundação da
linguagem). O plano incremental foi rascunhado primeiro (fatias com prova por
fatia) — o mesmo portão spec-first da D6/X5.

- **Destrava:** X6 → X6.1–X6.3 pousadas.
- **Próxima entrega:** — (concluída; `InteropSchemaE2ETest` 18/18).
- **Relações:** `Related: regra 6, regra 11, R9, X5, D-KOF-FIRST`.

---

## D-CODEGEN-STEP — hook de codegen em compile-time (múltipla escolha, mantenedora 21/09/2026)

**Data:** 2026-09-21 · **Estado:** `DECIDIDO` · **Opção escolhida:** **A**.

Implementar **`CodegenStep`** como **hook interno do compilador** (sem sintaxe
de usuário) — R4 (`🔵`). É o bloqueador declarado do Estágio 3 (desugar de
`infra "prod" {}`, 3.2) e da migração DDL/runner. Não adiciona **superfície de
linguagem**; qualquer forma de usuário (3.2) é decisão própria posterior
(portão da regra 11).

- **Destrava:** R4 → em curso; 3.2 destravada **atrás da R4**.
- **Evidência:** `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` R4 + caminho crítico.
- **Relações:** `Related: R4, R8 (mesmo frontend), 3.2, regra 11`.

## D-GRAPHICS-GAMING — gráficos além de formulários: um plano future para a superfície 2D/3D/jogos é OBRIGATÓRIO (20/09/2026, pedido da mantenedora)

A mantenedora pergunta como Kof lida com 2D, 3D e gráficos não-web ("como alguém desenvolve um jogo em Kof?") e dirige: abrir o plano em `docs/development/future/`. Estado real hoje: `kof.ui` é superfície de formulário/intenção (JVM=JavaFX, JS=DOM, Android=APK); o corpus NÃO tem abstração de jogo (frame loop, sprites, malhas, input-por-frame, áudio, GPU) — jogo hoje seria interop, não idioma (fronteira regra 8/11: a forma de API estrangeira não é a resposta; o plano deve definir a INTENÇÃO Kof que os backends abaixam, gaps por-alvo honestos R6/XXX001, interop-first R9 para engines/libs — nunca renderizador caseiro, e KofC/wasm são future). DOC-PLANO: `docs/development/graphics-gaming-plan.md` — skeleton na próxima sessão; perguntas que o plano DEVE responder: primitiva de game-loop (idioma `scene`/`frame`?), superfície 2D sprite/tilface, escopo 3D (mesh/camera/material como intenção vs. FFI para GPU nativa), áudio, modelo de input, honestidade por-alvo (JVM/Native/JS/web + KofC depois) e a guarda de non-goals (sem canvas/HTML vazando para código de usuário). Prioridade: future/ — NÃO compete com o 1.0 (R12 + D-1.0-STABILITY-100: só entra na superfície 1.0 por promoção explícita dela).

### Adendo D-GRAPHICS-GAMING (20/09/2026, mantenedora, mesma sessão) — a superfície de mídia ESTÁ no escopo do plano: pipeline de som E suporte a vídeo

Kof também precisa de um **pipeline de som** (reprodução, streams, volume/mix, o caso de áudio de jogo: SFX de baixa latência) e de **suporte a vídeo** (uma superfície de intenção `video`/`VideoView` no mundo `kof.ui`: reprodução de arquivo/stream, o chrome do player pertencendo à plataforma, nunca ao código do usuário). O doc do plano DEVE tratar mídia como first-class: o idioma KOF (ex.: `sound.play("x.ogg")`, componente de painel `video`) + lowering por-alvo JVM (JavaFX Media/`javax.sound` JÁ existem hoje em JVM — medir antes de prometer), JS (`<video>`/WebAudio do browser — a plataforma renderiza), Native (interop-first R9: SDL_mixer/miniaudio/OpenAL/ffmpeg — nunca codec caseiro; gaps honestos `XXX001` onde faltar, ex. áudio no cross riscv), + a guarda de non-goals (sem tags `<audio>` HTML5 vazando para código Kof; codecs são problema da plataforma). O pipeline de som (mixing/grafos) ganha SEÇÃO PRÓPRIA no plano respondendo: contrato de latência, formatos suportados por alvo, enumeração de dispositivos, e se `kof.sound` é stdlib-core ou pacote stdlib (fronteira R1).

### Adendo 2 D-GRAPHICS-GAMING (20/09/2026, mantenedora) — SEM JavaFX; a superfície de mídia/imagens exige PARIDADE TOTAL

"Sem JavaFX. Tem que ter paridade total." Consequências registradas: (1) o plano de gráficos/mídia NÃO PODE usar JavaFX (nem toolkit single-target) como backend da superfície KOF — o JVM tem de chegar ao mesmo idioma pela MESMA pilha portátil dos outros alvos (interop-first R9: a forma que o plano avalia é uma camada portátil classe SDL/GL rebaixada por bindings por-alvo, não chrome de plataforma); (2) PARIDADE TOTAL é o critério de aceite desta superfície — diferente do "escopo honesto por alvo" do R7, um recurso de gráficos/mídia só entra na superfície da linguagem quando TODO alvo rodar o MESMO programa com o MESMO comportamento (ou o recurso não é promovido); (3) o kof.ui-JVM atual (JavaFX) continua funcionando intacto (compatibilidade retroativa, regra 2 do freeze) mas é a face LEGADA da área, não a futura — migração/reforma é QUESTÃO DE DESIGN que o plano deve responder (regra 6), nunca decisão de agente; (4) o item de remoção do JavaFX entra no plano `docs/development/graphics-gaming-plan.md` §parity como seção própria (medir hoje: quais classes kof.ui ligam javafx.* no alvo JVM).

### Adendo 3 D-GRAPHICS-GAMING (20/09/2026, mantenedora — CORREÇÃO ao adendo 2) — Kof nunca usou e nunca vai usar JavaFX; toda mensagem de JavaFX em Kof é erro disfarçado

A mantenedora revoga o enquadramento "kof.ui-JVM é legado JavaFX que continua funcionando": **Kof NUNCA usou JavaFX e NUNCA vai usar** — coerente com a regra da casa de 12/09 (`AGENTS.md`, "regra JavaFX"): a mensagem "componentes de runtime do JavaFX não foram encontrados" NUNCA é benigna, é o launcher engolindo um `VerifyError`/`ExceptionInInitializer` real — erro disfarçado, sempre com causa raiz, nunca acomodado. Logo: (a) o item (3) do adendo 2 passa a valer: qualquer ligação `javafx.*` encontrada na árvore do Kof NÃO é face legada — é DEFEITO a remover pelo pipeline normal de bug (regra 4 do freeze: corrigir o código até o comportamento documentado, nunca documentar em volta dele); (b) `kof.ui` no JVM era, é e será servido pela pilha portátil de paridade desde o início — a seção "migração" do plano vira seção de ERRADICAÇÃO: medir toda referência `javafx` em src/docs/std-lib (`grep -rn "javafx" kof-*/src` etc.), classificar cada uma (erro-disfarçado do tipo exceção engolida vs. alegação errada de doc) e abrir como itens com repro; (c) compatibilidade retroativa NÃO protege caminho JavaFX — código Kof de usuário nunca nomeou JavaFX, removê-lo não pode quebrar nenhum programa Kof válido (a promessa de compat é aos programas Kof, não aos internos).
## D-MAKEALIVE-CLI — contrato da 3.8: `kof makealive plan|apply|destroy` (tooling sobre o host)

Decidido 20/09 por delegação do maintainer à `.18` ("propor o contrato + implementar") — a
linha 3.8 exigia decisão de contrato de comando (regra 6). **(1) Verbo:** `kof makealive
<plan|apply|destroy> <file.kf>` — NÃO `kof infra`: o Q1 do D-MAKEALIVE decidiu que o namespace
É o nome (`kof.makealive`) e o literal `infra` segue HARD-DENY no ledger do stdlib (R1); o
CLI segue o nome decidido. **(2) Convenção de programa (a postura D-WORKFLOW-RUN da 2.6):** o
arquivo é Kof puro — `import kof.makealive`, `design(): Infrastructure`,
`provider(): Provider`, sem `main()`; o tool síntetiza um main() sobre as faces do próprio
host (`plan`/`apply`/`destroy`/`mkLoadState`/`mkSaveState`/`mkMaxGen`) e conversa pela linha
marcada `@@KOF_MAKEALIVE@@ {json}`; a formatação humana/JSON mora no CLI, nunca no host.
**(3) Estado:** arquivo h2 via `--state PATH` (default `<file>.makealive`); o contrato "quem
traz o driver JDBC é o runner" segue intacto (KofJsDbBridge); apply/destroy SEMPRE salvam
`gen = max+1` — e o destroy persiste uma MARCA de estado vazio (`res ""`) para que a geracao
vazia fique visível a `mkMaxGen`/`mkLoadState` (bug achado pelo E2E desta própria decisão;
golden `emptyGenerationIsVisibleAndLoadsEmpty`). **(4) Alvos:** JVM+JS paridade de bytes (R7);
script/native recusa honesta (igual 2.6) — o stub Native do host mantém `ORM001` no call-site.
**(5) rc:** a linha marcada decide (`allOk`); throw (provider recusou set/delete, guardas de
argumento) = sem linha marcada, a saída crua É o diagnóstico, rc 1. Prova:
`CmdMakealiveTest` 7/7 + `MakealiveMaxGenE2ETest` 4/4 + bateria Makealive 14/14.

## D-MAKEALIVE-SYNTAX — 3.2: `infra "prod" { ... }` é AÇÚCAR PURO sobre `design()` (21/09/2026, mantenedora)

**Data:** 2026-09-21 · **Estado:** `DECIDED` · **Decide:** `makealive-plan.md` §5 linha **3.2**
(e destrava **3.7**; reitera **3.8**) · **Substitui:** nada.

**Contexto:** as três linhas restantes do makealive (3.2/3.7/3.8) foram à enquete da
mantenedora. A linha **3.2** ("sintaxe `infra "prod" {}`") exigia decisão regra 6 por ser
**superfície de parse nova voltada ao usuário**; o bloqueio do hook de codegen já havia caído
com **R4** (hook `CodegenStep`, pousado 21/09).

**Decisão (regra 11 — Simplicity Law):** ADICIONAR o bloco, como **açúcar sintático puro** —
ele desugara sobre os records/builder já decididos do host e **não ganha semântica própria**
(plano §7, "no HCL inside Kof").

- **(1) Forma:** declaração top-level `infra "prod" { <chamadas> }`. `infra` continua
  **IDENTIFIER** (despachado igual a `test`/`application`), **não** é palavra reservada nem
  token novo — logo `LanguageCoreSurfaceTest` (8.6) fica verde **por construção**.
- **(2) Desugaring:** o bloco vira `design(): Infrastructure` — um local sintetizado
  `__infra = Infrastructure("prod")`, cada statement `nome(args)` vira `__infra.nome(args)`
  (as faces do host `resource`/`prop`/`requires`), e `return __infra`. A saída é idêntica ao
  `design()` imperativo escrito à mão; o contrato do CLI (`D-MAKEALIVE-CLI`) não muda.
- **(3) Sem HCL, sem aninhamento:** o corpo é Kof puro de chamadas — sem `chave = valor`, sem
  sub-bloco `resource`, sem tipo novo, sem runtime novo.
- **3.7** (detecção de ciclo em compile-time) — **FECHADA como runtime-only** (mantenedora
  21/09, adendo): com o bloco como açúcar puro o compilador só vê chamadas genéricas, então
  um grafo em compile-time daria ao `infra` **semântica própria** (contra §7 / regra 11); a
  **recusa em runtime** pousada na 3.1 já nomeia os membros do ciclo — esse É o contrato.
  Nenhum check estático é adicionado.
- **3.8** — reiterada: `kof makealive plan|apply|destroy` é o **único** verbo
  (`D-MAKEALIVE-CLI`); `kof infra` **não** é adicionado.

**Prova (medida no pouso):** `InfraSyntaxE2ETest` — um arquivo `infra "prod" { ... }` e seu
gêmeo `design()` escrito à mão produzem `plan`/`apply` byte-idênticos (JVM==JS), mais um golden
de sintaxe (`infra` não é reservado; corpo mantido nas faces do host). Documentado em
`docs/stdlib/makealive.md` + `learn/`.

## Adendo 4 D-GRAPHICS-GAMING (20/09/2026, mantenedora) — Kof terá ENGINE GRÁFICA PRÓPRIA para jogos

Ordem: Kof precisa de uma engine gráfica própria para games — a recomendação interop-first do plano (R9) está REVOGADA para este domínio (precedente tipo D-UNIVERSAL). A engine é da Kof (código Kof/platform, pilotada pela casa), exposta em idioma zero-boilerplate (regra 11: idiomatic, fácil, sem complexidade acidental); bindings FFI ficam limitados ao que não é engine (janela/GPU/device de áudio). Consequência: plano `future/graphics-gaming-plan.md` §§3–4+Q1/Q5/Q7 + README/learn/training/docs de UI-mídia precisam REESCRITA nesta direção; R9 ganha exceção nomeada no DECISIONS. Decisões de detalhe (nome da engine, primeira fatia, formatos) continuam regra 6 via Qs do plano.

## D-PROPERTY — property-based testing: SEM sintaxe nova — a superfície é o idioma existente `test` + `kof.rng` + `assert` (21/09/2026, delegado pela mantenedora)

**Data:** 2026-09-21 · **Estado:** `DECIDED` · **Decide:** `SG-023` (opção **C** + opção **iii**) · **Fecha:** o restante do X8 (G6 "next").

**Contexto:** a frente X8 pousou `rng` (fatias 1–2), `kof test --timeout` e suítes nomeadas por diretório. As duas faces restantes — runner de property e fixtures de suíte — foram registradas como **`SG-023`** ("PEDIDO, sem decisão") porque ambas *sugeriam* superfície nova voltada ao usuário (regra 6). Questionada para decidir, a mantenedora delegou a escolha da superfície ("Decide SG-023 surface").

**Decisão (regra 11, Lei da Simplicidade): SEM sintaxe nova.** O mecanismo já existe e está documentado:
- **property** = **opção C** — um `test "name" { }` cujo corpo semeia o `rng` e faz o loop, usando `assert(cond, msg)`. **REJEITADAS** a opção A (keyword `property`/`forAll`) e a opção B (modo implícito `kof test --props`): cerimônia sobre um mecanismo que a linguagem já tem.
- **fixtures** = **opção iii** — nada novo; o padrão `D5-B` (`close()` + `try/finally`) já expressa setup/teardown por teste. **REJEITADAS** a opção i (blocos `setup`/`teardown`) e a opção ii (arquivo de convenção `_suite.kf`).

**Por quê:** "Kof tem que ser mais simples que qualquer alternativa" — `rng.seed(42)` + loop + `assert` é mais curto e declara melhor a intenção do que keyword + inferência de geradores + maquinário de shrinking; o compilador fica menor e o idioma roda em todo alvo com `rng`. Respeita o `D-KOF-FIRST` (nenhum empréstimo de QuickCheck/Hypothesis antes de um contrato Kof).

**Prova:** `PropertyTestIdiomE2ETest` **7/7** (kof-compiler) — uma property semeada de 200 iterações PASSA e é reprodutível entre execuções, seu `checksum` é idêntico byte a byte **JVM==JS** e **JVM==Native-x86** (paridade do `rng`), uma property falsificável FALHA deterministicamente com a mensagem derivada da seed e exit 1 (JVM+JS), e uma property de zero iterações PASSA vacuousamente. Documentado em `training/idioms/stdlib.md` + `learn/23-testing.md`.

**Não é mudança de linguagem:** nenhum parser/typer/codegen tocado; a superfície do `kof test` não muda.

## D-ARRAY-PRINT — §388-B: imprimir um `Int[]` inteiro é formato de container (21/09, mantenedora)

A entrada §388 registrou a paridade reversa das bytes-faces: JVM/Script
imprimiam `[I@65629ac6` (toString cru do `int[]` Java) enquanto o KofJS imprimia
`65,66,67` — nenhuma linha do corpus declarava como um array primitivo se
IMPRIME (a linha de formato de container da matriz cobria só List/Map/aninhados).
A mantenedora decidiu no chat em 21/09 (regra 6): o **formato de container
vence** — isto é, a gramática §107 já valendo para coleções (oracle =
`ArrayList.toString`, `[65, 66]` com colchetes e separador `, `; o JS espelha via
`kofFormat`, os três alvos nativos via `kof_array_to_string`).

Nota de calibragem: a opção do voto foi redigida "65,66,67" (a face JS da
época), mas o que se votou contra foi a forma de IDENTIDADE; o formato de
container da casa — declarado para coleções desde §107 e fixado em
`conformance-matrix.pt_BR.md` — é `[65, 66]`. A implementação segue §107, não o
texto literal da opção.

Escopo: `println(new Int[n])`, `println(readBytes())`, plano/aninhado/vazio,
records e Strings pelo toString de conteúdo do próprio elemento. Passar `List`
numa bytes-face continua erro de compilação (`SEM099`, §388-A) — intocado por
esta decisão. Testes: célula `arrayprint` em `ConformanceMatrixTest`
(JVM/Script/JS/nativo), `ArrayPrintFormatE2ETest`, goldens riscv64/aarch64 em
`Native*E2ETest` (CI/qemu).

## R6-SCOPE — entrega incremental NÃO fere o R6 (mantenedora, 21/09/2026)

**Estado:** `DECIDIDO` · esclarecimento ABSOLUTO do R6 (nunca silencioso).

O R6 proíbe **silêncio**, não **escopo parcial**. Uma entrega que é uma **fatia
vertical completa para o seu escopo declarado**, com os caminhos ainda não
suportados falhando por **diagnóstico honesto** (`FFI001`/`FFI002`/`XXX00x` —
que *é* o R6), **não** fere o R6. O R6 é violado só quando o gap é **escondido**:
stub silencioso, fallback fraco, divergência que o usuário não enxerga.

Consequência: toda capacidade ainda não entregue é construída
**incrementalmente** (ex. JVM-first, com Native/JS como gaps *declarados e
diagnosticados* — R7) e cada fatia pousa inteira para o seu escopo. "Não dá
para fazer tudo de uma vez" não é motivo para adiar a fatia; "esconder a parte
que falta" é o único movimento proibido.

## D-R3-BUFFER — out-buffer é o tipo nominal `Buffer(U8)` (mantenedora, 21/09/2026)

**Estado:** `DECIDIDO` · **Opção escolhida:** tipo nominal (de reusar `Byte[]` /
nominal `Buffer(U8)` / separar).

D6-3/D-R3-3.3 fixaram que out-buffers existem como tipo ABI próprio
(`Buffer(U8, INOUT)`, copy-in / chamada / copy-back, **nunca `S`**). Esta
decisão fixa a **grafia que o usuário escreve**: um tipo **nominal `Buffer(U8)`**
na assinatura do `extern` — *não* um reuso de `Byte[]` (o `T[]` escalar segue o
`ptr` read-only da fatia 3/D6-2). `Buffer` continua um tipo ABI distinto mesmo
quando um registrador carrega um endereço (R6).

**Criação/vida (respondido 21/09):** o programador obtém um buffer com
**`buffer.alloc(Int n) : Buffer(U8)`** (stdlib) e a vida é **automática** — o
compilador libera no fim do escopo; o programador nunca aloca nem libera.
`Buffer(U8)` como parâmetro de `extern` é `Buffer(U8, INOUT)`: copy-in, chamada,
copy-back; o comprimento é argumento C explícito (D6-3). Inspeciona com
`Buffer.bytes()`. Fatia incremental: JVM primeiro; Native/JS mantêm
`FFI001`/`FFI002` honestos (R6-SCOPE).

## D-R3-HANDLE-LIFETIME — a memória do `Handle` é automática (mantenedora, 21/09/2026)

**Estado:** `DECIDIDO` · **Direção escolhida:** automática (de `Handle<T>` único,
ou adiar).

D-R3-3.3 escolheu um `Handle` opaco nominal (nunca um inteiro, sem aritmética de
ponteiro). Esta decisão fixa a sua **vida**: alocação/desalocação devem ser
**automáticas — o programador nunca gerencia memória** (sem `malloc`/`free`
manual). O `Handle` portanto não pousa como tipo FFI isolado agora; ele é
entregue junto com o mecanismo de recurso/vida gerenciado pela linguagem (frente
scoped-resources / RAII, `docs/scoped-resources-plan.md`),
que é o dono da estratégia de alocação. Até lá, externs com `Handle` seguem
`FFI001`/`FFI002` honestos (R6).

---

## D-DESUGAR-STEP — 2.2.3 resolvido: registry de desugar na fase AST (opção B) (mantenedora 21/09/2026)

**Data:** 2026-09-21 · **Estado:** `DECIDED` · **Opção escolhida:** **B**.

A mantenedora escolheu a **opção B** do `docs/architecture/codegen-step-2.2.3-assessment.md` (+PT) — **implementada 21/09 (`85779f20`)**:
adicionar um **registry `DesugarStep` na fase AST** espelhando
`CodegenStep`/`CodegenStepPipeline`, e migrar os quatro desugars de fonte
(`desugarTests`/`desugarApplication`/`desugarInfra`/`desugarNestedFunctions`,
hoje em `CompilerDesugar`, `CompilerPipeline.java:301-304`) para steps
registrados. **Interno ao compilador, zero superfície de linguagem** (regra 11:
nada chega ao código do usuário). **Sem mudança de comportamento** (freeze regra
3): mesma suíte + E2E golden por alvo, saída byte-idêntica. O DDL do ORM
permanece no lowering (não é candidato). Fila: `roadmap.md` §23 `2.2.3` +
tracker R4.

- **Destrava:** 2.2.3 (`⛔` → aberta, fatias).
- **Relações:** `Relacionado: D-CODEGEN-STEP, freeze regra 3, regra 6, regra 11`.

## D-TYPE-VARIANCE / D-INTEROP-REFLECT — plano APROVADO, fatias autorizadas (mantenedora 21/09/2026)

**Data:** 2026-09-21 · **Estado:** `IMPLEMENTADA` (plano aprovado; superfície X5+X6 pousada 22/09).

O `future/type-system-extensions-plan.md` (+PT) foi revisado e **APROVADO**. X5
(variance+sealed, opção C) e X6 (reflexão de interop) começaram em **fatias
incrementais, cada uma com prova própria**. A superfície pousou (X5.5 + X6.3,
22/09), então a condição 2 do gate não precisa mais de revisão. O plano é
promovido para `docs/development/` (três estados). Fila: `roadmap.md`
§2.8.4/§2.8.5.

## D-SECRETS — Stage 5 / 3.6 promovido; face 1 autorizada (mantenedora 21/09/2026)

**Data:** 2026-09-21 · **Estado:** `DECIDED` · **P1+P2+P3 POUSADAS.**

O `future/secrets-plan.md` (+PT) é **promovido para `docs/development/`**; a
**face 1 (tipo `Secret`)** é autorizada como superfície votada por regra 6,
incremental com prova; `KeyHandle`/redação seguem face a face. Último resíduo do
`makealive-plan` (3.6). Fila: tracker 3.6.

**Face 1 POUSADA 21/09 (`32285136`):** tipo valor `Secret` — construtoras
`secrets.of(text)` / `secrets.secret(name)`, `reveal()` (único export cru),
`redacted()`, impressão redigida (`Secret(*** )`), `==` constant-time.
JVM-primeiro (R7); JS/Native/Script/Android = gap honesto `SECN008` (R6). Prova
`SecretE2ETest` 4/4.

**Resto da P1 + P2 + P3 AUTORIZADOS (mantenedora 21/09, ordem direta "implementa
tudo o que falta do secrets plan … precisa fechar"):** implementar todo o
`secrets-plan.md` e fechar o plano. Opções escolhidas (as alternativas do próprio
plano): `secrets.get` segue o `String` cru legado (congelado 0.2.6) e
`secrets.secret`/`secrets.of` são o caminho tipado — **não-quebrante**; a
**P3 `KeyHandle`** é puxada **para frente do "after 1.0"** pela mesma ordem. Cada
face segue incremental com prova e seu gap honesto por alvo. Fila: tracker 3.6 /
`secrets-plan.md` §2.

**TODAS AS FACES POUSADAS 21/09 (`04473bbe`):** resto da P1 (`secrets.fromBytes(Int[])`,
`hashCode` de identidade); **P2** redação forçada (runtime `json.encode(Secret)` →
`"Secret(*** )"`; compile-time lint `SECN009` quando `reveal()` alimenta
`log.*`/`json.encode`); **P3 `KeyHandle`** (`secrets.keyFromHex/keyFromPem/
keyFromKeystore`, `rotate()` que revoga o handle antigo → uso posterior `SECN010`,
sobrecargas `KeyHandle` de `crypto.hmacSha256/aesgcm/chacha20` e
`jwt.create/verify`; chave crua nunca exposta). JVM-primeiro (R7), `SECN008` nos
demais (R6). Prova: `SecretE2ETest` 7/7 + `KeyHandleE2ETest` 5/5. O plano está
fechado e movido para `docs/architecture/secrets-plan.md` (registro de design).

## D-FFI-STRUCT-B — D6-1 opção B (`struct` mutável): aprovada spec-first (mantenedora 21/09/2026)

**Data:** 2026-09-21 · **Estado:** `DECIDED` · design-first (sem código ainda).

A mantenedora **aprovou a D6-1 B** (nova declaração `struct` mutável, by-ref,
para buffers in/out) **spec-first**: a superfície é desenhada/medida no
`ffi-abi-structs.md` e revisada **antes de qualquer diff** de parser/typer (regra
11). Records seguem by-value read-only; `Buffer(U8)` já cobre o caso out-buffer
pousado. Fila: tracker 3.8 (`ffi-abi-structs.md` §6).

## D-DB-PARITY-OWNER — dono da frente db-parity nomeado; S0/S1 autorizadas (mantenedora 21/09/2026)

**Data:** 2026-09-21 · **Estado:** `DECIDED`.

O `db-parity-plan.md` (+PT) ganha dono (lane docs/plataforma, autora do
`D-DB-GAPS`) e começa **S0** (diagnóstico interino honesto do §421) + **S1**
(`mariadb://` = alias mysql-wire), cada uma com prova; S2–S4 seguem por fatia.
Adendo ao `D-DB-GAPS`.

## D-RELEASE-0.5.0-GATE condição 2 — segue `NEEDS-REVIEW` com frente de superfície em voo (mantenedora 21/09/2026)

**Data:** 2026-09-21 · **Estado:** `DECIDED` (confirmação).

A condição 2 reporta `NEEDS-REVIEW` — **não RED** — enquanto uma frente aprovada
`State: OPEN` não pousou; não bloqueia o corte 0.5.0 por si só. Uma entrada
`OPEN` nunca é "nada espera".

## D-X5-SURFACE — congelamento da superfície X5 (mantenedora 21/09/2026)

**Data:** 2026-09-21 · **Estado:** `DECIDED`.

Respostas da mantenedora às perguntas a–d do plano:
- **(a) keyword de variance = `out`/`in`** — declaration-site, 1 char (passa a regra 11).
- **(b) `sealed` aplica-se a `class`/`record` + `interface`.**
- **(c) projeção use-site (`List<out T>`) ESTÁ no v1** (sobrepõe o default "deferred" do plano; X5.4 vira fatia do v1).
- **(d) diagnósticos ficam na família existente `SEM0xx`** (sem família nova).

Superfície: `sealed class/record/interface`; subtipos fora do conjunto = diagnóstico;
`switch` exaustivo sobre sujeito sealed; `out`/`in` em params genéricos com checagem
de sonoridade de atribuição. Só compiler/frontend; sem superfície de runtime.
Fila: `roadmap.md` §2.8.4; fatias X5.0→X5.5 (X5.4 agora no v1).

- **Relações:** `Relacionado: D-TYPE-VARIANCE, regra 6, regra 11, R10`.

## D-RELEASE-0.5.0-SCOPE — os planos em voo com dono ainda soltos entram no allowlist da condição 3 e o EG-8 é desacoplado da condição 6 (mantenedora 21/09/2026)

**Data:** 2026-09-21 · **Estado:** `DECIDED` (respostas da mantenedora no chat).

Duas decisões de escopo do gate 0.5.0 (`D-RELEASE-0.5.0-GATE`), para o release
não esperar frentes abertas de outras linhas:

- **(a) Condição 3 — allowlist dos planos EM VOO com dono ainda soltos.**
  `db-parity-plan` (dono lane gaps-db) e `ffi-abi-structs` (dono jonas) ficam
  em `docs/development/` **sem virar RED na condição 3**: cada um tem dono, fila
  viva e implementação pendente declarada; concluem nas próprias frentes
  (regra dos três estados), não como pré-condição do corte 0.5.0. O ALLOWLIST
  do gate os carrega; a fila do README segue rastreando-os. Doc sem dono/fila
  continua RED. (`IMPLEMENTATION-UNIVERSAL-PLATFORM` estava na lista original
  da decisão e **concluiu 21/09** — movido para `docs/architecture/`;
  `type-system-extensions-plan` (dono compiler/X5) **concluiu 22/09** — X5+X6
  landados com prova, movido para `docs/`; portanto nenhum dos dois é
  solto/allowlistado.)
- **(b) Condição 6 — EG-8 desacoplado.** EG-8 é o primeiro candidato a RC 1.0
  mais a declaração explícita da mantenedora "a linha 1.0 está aberta", e só
  abre depois de EG-1..EG-7 fecharem — pertence à linha 1.0, não ao gate
  0.5.0. A condição 6 agora mede **EG-1..EG-7 + os `1.0-blocks` abertos**; um
  EG-8 aberto nunca a torna RED.
- **(c) Condição 7 — triagem em lote.** A mantenedora recebe os 14 bugs vivos
  do ledger com recomendação por item (fechar / post-1.0 / não-é-bug) e
  classifica em lote; até lá a condição segue RED (medida).

**Evidência:** `scripts/check_release_050_gate.sh` (ALLOWLIST + `c_edges`,
`--selftest` com os dois casos plantados), `release-beta-0.5.0-prep.md`
condições 3/6, lista de pendentes da condição 3 no README.

- **Relationships:** `Related: D-RELEASE-0.5.0-GATE, D-RELEASE-1.0, rule 6, rule 3`.

## D-CLOSEALL-BATCH — lote dos 14 known-bugs: a mantenedora ordenou fechar tudo com evidências; os 3 forks rule-6 foram votados (mantenedora, 21/09/2026)

**Date:** 2026-09-21 · **State:** `DECIDED` (mantenedora, interativo — diretiva
"você assume bugs-and-gaps e fecha todos os bugs que existem e apresenta
evidências para todos"; sub-votos respondidos no chat com as opções recomendadas).

- **§334 (`kof_box_equals` NaN) → CLOSED:** divergência documentada e
  INALCANÇÁVEL do código-fonte Kof (nenhum produtor de NaN hoje — divisão por
  zero é diagnóstico de compile-time OBS-009); downgrade para informativo,
  como a própria entrada prevê.
- **§188 (`"2026" as Int` → VerifyError) → (A) REJEITAR no compile-time** —
  novo SEM0xx nomeando o idiom canônico `math.parseInt`.
- **§400 (função nomeada passada como VALOR, SEM011 falso) → (A) MANTER a
  rejeição, novo diagnóstico nomeia a regra real e aponta o idiom lambda
  (`probe` → `() -> probe()`, byte-paridade já medida).**
- **§283 (native aarch64: processo nunca encerra com `time.interval` vivo)
  → (B) WORKER COMO THREAD DAEMON — paridade com `java.util.Timer` (daemon
  por padrão na JVM).**
- **§418 (harness riscv debug sem destroy) + §423 (channels riscv/aarch sem
  runtime): HANDOFF p/ lane nat/native-debug (9093, ativa no período — fechou
  §424/425/427); assumo se ela parar (regra dead-task).**
- O resto do lote (§302/§280/§268/§248/§278/§205) = trabalho normal da lane;
  §271/§288 = MESMA raiz river (caminho único de resolução de tipo).

**Relationships:** regra 6, river §271/§288, §334/OBS-009, DECISIONS.md (política de NaN se precisar).

## D-RULE6-BATCH — revisão rule-6 da triagem: seis decisões (mantenedora 21/09/2026)

**Data:** 2026-09-21 · **Estado:** `DECIDED` (respostas da mantenedora no chat, múltipla escolha).

A mantenedora revisou os seis itens rule-6 da triagem dos 14 vivos e decidiu:

- **§400 — (B) funções nomeadas viram VALORES — supersede o (A) do lote.**
  A mantenedora havia votado **(A) manter a rejeição + corrigir a mensagem** no
  `D-CLOSEALL-BATCH` e a lane do lote pousou isso (`cd04246c`, novo SEM011
  nomeando o idiom); nesta revisão ela respondeu **(B)**: função top-level
  nomeada em posição de argumento converte para o `FunctionType` esperado
  (overloads incluídos). **Decisão operativa: (B)** — o diagnóstico (A)
  pousado fica só até a conversão pousar (é estritamente melhor que a
  mensagem antiga e desaparece com a superfície); expansão de superfície →
  gate da regra 11 + fila na frente de tipos, nunca edição drive-by. Pendente
  a confirmação dela da supersessão (sinalizado no chat 21/09).
- **§188 — (A) rejeitar `String as Int` com diagnóstico (`SEM084`).** `as` é
  conversão numérica, não parsing textual; o caminho canônico segue
  `math.parseInt`. Mata o false-accept (check limpo → CCE em runtime).
- **§288 — (b) `TypeVariable` no parse + rejeição interina (`SEM085`).**
  Anotar type-params como `TypeVariable` no parse dentro do owner genérico
  (fonte única); até o fix do pipeline pousar, rejeitar a forma composta em vez
  de converter erro alto em crash no load.
- **§302 — (A+B) corrigir o pin guard + diagnosticar o que sobrar (`SEM086`).**
  Paridade `Type.of`/`toType` em locals × fields × params × records (os quatro
  backends); tipo de coleção nu que continuar ambíguo ganha diagnóstico
  honesto.
- **§268 — (A) `java.lang` implícito via probe cacheado + diagnóstico honesto
  (`SEM087`).** `Class.forName("java.lang."+n)` cacheado; nome simples não
  resolvido em `extends`/`implements` → diagnóstico (nada de super raw);
  `Object` → `java/lang/Object`.
- **§271 — (B) diagnóstico honesto interino agora; a ABI COMPLETA de erasure
  na linha 1.0.** Emissão de bridge/descriptor nos quatro backends é
  **entrega da 1.0, não post-1.0** (mantenedora 21/09): a escada de releases
  pode continuar por mais minors betas (até 0.9.x se precisar) antes da 1.0; o
  que não pode shipar é o `NoSuchMethodError` silencioso. Interino: recusar o
  dispatch de interface genérica não-suportado com código (espelhando
  `NAT005`).

**Fila:** DOING (claim da lane 9093); cada fix carrega a própria prova
(RED→GREEN) e atualiza a seção do ledger no mesmo commit.

- **Relationships:** `Related: D-RELEASE-0.5.0-SCOPE, D-RELEASE-1.0, rule 6, rule 11, R6`.

## D-BAREMETAL-BOOT — frente bare-metal promovida: `PLAN-BAREMETAL-BOOT` sai de `future/`, R12 sobreposto, escopo ordenado = bare-metal com ring0/ring1 (mantenedora 22/09/2026)

**Data:** 22/09/2026 · **Estado:** `DECIDED` (ordem da mantenedora no chat —
sessão autônoma) · **Sobrepõe:** o portão R12 (SYSTEMS antes de tudo) **só para
esta frente** — mesmo padrão do §D-UNIVERSAL.

**Decisão (palavras da mantenedora):** "você vai assumir PLAN-BAREMETAL-BOOT de
future e vai trazer pra desenvolvimento. quero que desenvolva pra baremetal com
suporte a ring0 e ring1".

1. **Promoção (três-estados):** `PLAN-BAREMETAL-BOOT.md` (+`.pt_BR`) vai de
   `future/` para `docs/development/`, status **EM DESENVOLVIMENTO**; a
   linha do `future/README` é reclassificada como movida; `roadmap.md` 1.6 e o
   tracker 1.7 acompanham no mesmo commit.
2. **Sobreposição do R12** para esta frente (ordem da mantenedora), registrada
   aqui — a frente abre agora; a ordem do §7 do próprio plano governa
   (B-0 → B-1 → caminho de boot → anéis B-6 → …).
3. **Escopo ordenado:** bare-metal **com ring0/ring1** (níveis de privilégio
   x86_64, face B-6) — boot em CPL0 + domínios ring1 com prova falseável de
   `#GP`. A **superfície Kof** para mirar um domínio ring1 **NÃO** é decidida
   aqui: é decisão rule 6 (valem a regra 11 / Lei da Simplicidade) e só pousa
   com revisão da mantenedora.
4. **Fila:** DOING (claim da lane, sessão 9092); a condição 3 do gate 0.5.0
   mantém o plano no allowlist como frente em voo com dono (padrão
   `D-RELEASE-0.5.0-SCOPE`) — o corte 0.5.0 não espera por ela.

**Evidência:** mensagem da mantenedora 22/09/2026 (chat, esta sessão); arquivo
do plano promovido no mesmo commit; ALLOWLIST do `check_release_050_gate.sh`
atualizado.

- **Relações:** `Related: D-POLL-19 (D3-A), D-UNIVERSAL (padrão de sobreposição do R12), D-BOOTSTRAP (norte), rule 6, rule 11, R12`.

## D-VERSIONING-RELEASE — política consolidada de versionamento e corte de release: PATCH = sem diff de superfície pública contratada, MINOR obrigatório para qualquer diff de superfície pública pré-1.0, SemVer estrito pós-1.0, gatilho ≠ corte (aprovação da mantenedora 22/09/2026)

**Data:** 22/09/2026 · **Estado:** `DECIDED` (aprovação da mantenedora — PR #582
mergeado em 22/09/2026) · **Sobrepõe parcialmente:** as regras de classificação
e de gatilho da `D-RELEASE` — a decisão histórica é preservada (§1.3); só o
escopo dela é refinado. · **Incorpora por referência, sem relaxar:**
`D-RELEASE-1.0`, `D-1.0-EDGES`, `D-RELEASE-0.5.0-GATE`,
`D-RELEASE-0.5.0-SCOPE`.

**Escopo:** como uma mudança é classificada (PATCH/MINOR/MAJOR) e quando um
candidato a release é avaliado; não corta uma release por si só.

**Contrato:**

1. **Classificação é separada do corte.** Uma mudança ser PATCH não autoriza
   publicar um PATCH; classificação → avaliação → candidato → gate → corte é um
   pipeline, e um gatilho só abre a avaliação.
2. **PATCH pré-1.0:** reservado a mudanças **sem diff de superfície pública
   contratada** — bugfix, fix de segurança/regressão, fix de paridade para
   satisfazer um contrato existente, performance/refactor interno,
   CI/tooling/packaging/docs, ou melhoria de diagnóstico que não muda o
   contrato. `PUBLIC_CONTRACT_SURFACE_DIFF = 0 → PATCH admissível`.
3. **MINOR pré-1.0 (obrigatório):** qualquer **superfície pública contratada
   nova ou alterada** — sintaxe/operador/semântica observável nova, API ou
   namespace público relevante, comando/flag público, capacidade pública da
   stdlib, contrato de pacote/registry/interop, promoção de alvo a
   Supported/Stable, ou breaking change pré-1.0 deliberado e aprovado.
   `PUBLIC_CONTRACT_SURFACE_DIFF > 0 → PATCH proibido, MINOR no mínimo,
   Decision ID obrigatório`.
4. **Breaking change pré-1.0:** MINOR + decisão registrada + nota de
   impacto/migração + prova. Nunca escondido num patch por o projeto estar
   abaixo de 1.0.
5. **Pós-1.0:** SemVer estrito — PATCH = fixes retrocompatíveis, MINOR =
   funcionalidade pública retrocompatível nova, MAJOR = mudança incompatível de
   contrato; a compatibilidade é avaliada nas dimensões **fonte**,
   **artefato/binário**, **comportamental** e **paridade cross-target**.
6. **Primeiro `1.0.0`:** só quando a `D-RELEASE-1.0` (EXIT GATE) estiver
   totalmente GREEN no mesmo candidato e nenhuma aresta da `D-1.0-EDGES`
   estiver aberta — nunca por contagem de commits, de features, idade do
   projeto ou alcance de `0.9.9`.
7. **Gatilho ordinário (generalizado):** `LAST_RELEASE..ACTIVE_BRANCH` (não
   fixado a uma branch histórica) cruzando **100–150 commits** abre uma
   AVALIAÇÃO DE RELEASE — nunca publicação automática.
8. **Gatilho extraordinário:** um fix de segurança relevante, uma regressão
   crítica, um fix urgente de distribuição/pacote, ou uma decisão explícita da
   mantenedora abrem a avaliação imediatamente.
9. **Baseline comum de elegibilidade ao corte** (os gates por linha seguem
   prevalecendo): SHA do candidato identificado; VERSION/pom/recurso de versão
   consistentes; CHANGELOG/metadados de release coerentes; suíte exigida GREEN
   no candidato; classificação PATCH/MINOR/MAJOR provada; decisões necessárias
   registradas; bloqueadores aplicáveis resolvidos; pacote real validado quando
   aplicável; confiança/provenance do artefato conforme `D-ARTIFACT-TRUST`;
   docs EN/PT sincronizadas.
10. **Gate mecânico futuro (backlog, não esta decisão):**
    `release-surface-gate` comparando a última release com o candidato em
    gramática, language-reference, operadores, regras de tipagem, catálogo da
    stdlib Stable, comandos/flags contratuais da CLI, contrato de
    pacote/registry e Stable Target Surface; `PUBLIC_SURFACE_DIFF > 0` rejeita
    PATCH.

**Texto materializado:** `docs/distribution/VERSIONING.md` (+PT) descreve o
estado atual e aponta para cá; a `D-RELEASE` mantém o histórico com uma nota de
relação; `D-RELEASE-0.5.0-GATE` e `D-RELEASE-1.0` ficam inalteradas.

**Evidência:** `docs/distribution/PROPOSAL-VERSIONING-RELEASE.md` (+PT) — bloco
de evidência KOF-first e ancoragem externa; aprovação da mantenedora (PR #582
mergeado em 22/09/2026; registro de issue fechada).

- **Relações:** `Related: D-RELEASE (parcialmente sobreposta), D-RELEASE-1.0, D-1.0-EDGES, D-RELEASE-0.5.0-GATE, D-RELEASE-0.5.0-SCOPE, D-ARTIFACT-TRUST, D-BRANCH-0.5.0, D-VERSION-BUMP-0.5.0, rule 6`.

## D-TECHDEBT-23/09 — vereditos do ledger de dívida técnica (mantenedora 23/09/2026, múltipla escolha)

**Data:** 23/09/2026 · **Estado:** `DECIDIDO` (respostas da mantenedora no chat,
múltipla escolha — "chama no pente") · **Fonte:** `docs/development/tech-debt.pt_BR.md`
§5 (6 perguntas abertas) → respostas: §248 = portar JS+Native · §271 = emitir
bridges · §278 = portar as stacks · §423 = agendar o port · split = todos em
lote · D6-1=B = abrir agora.

1. **§248 — default methods de interface: PORTAR JS + NATIVE** (não só JVM).
   Fila: lane compiler — emitir defaults no JS + Native com prova de paridade.
2. **§271 — dispatch de interface genérica: EMITIR BRIDGES** (linha de ABI de
   erasure decidida agora). Fila: lane compiler — bridge methods nos impls de
   interface genérica.
3. **§278 — Android: PORTAR AS STACKS** (`kof.security`/`kof.gpu` rodam no
   Android; `kof.db`/`kof.orm` já corrigidos via DB-2). Fila: lane gaps-db.
4. **§423 — channels cross: AGENDAR O PORT** (runtime `kof_channel_*`
   riscv64/aarch64 + prova qemu). Fila: lane nat.
5. **Ordem de split: TODOS EM LOTE** — `NativeBackend` 603 (VERMELHO) +
   `CompilerPipeline` 588 + `RuntimeOrm7` 585 num lote só (precedente
   §442/§446, behavior-preserving).
6. **D6-1=B — ABRIR AGORA** (frente `struct` mutável by-ref abre sob
   spec-first + Lei da Simplicidade, regra 11). Fila: lane FFI — design §4/§6
   para revisão, depois diff de parser/typer.

**Evidência:** respostas de múltipla escolha da mantenedora 23/09 (esta sessão);
`docs/development/tech-debt.pt_BR.md` §5.

- **Relações:** `Related: tech-debt.pt_BR.md §5, regra 6, regra 11, R6, D-FFI-STRUCT-B, D-RELEASE-0.5.0-GATE (cond. 2/7).`

## D-DEBT-SCOUT — frente do KOF Technical Debt Scout abre: só Wave 1 (determinístico, somente shadow), sem capacidade de publicar Issue (dirigido pelo usuário, 23/09/2026)

> **24/09/2026 — MORTA pela mantenedora:** a dívida foi medida zerada
> (todo §NNN vivo que o ledger rastreava está ✅ no `known-bugs.md`; gate de
> tamanho verde) e a ferramenta/workflow/testes `tech-debt`/`technical-debt`/
> `debt-scout` foram removidos por ordem dela. Esta decisão fica como história.

**Data:** 2026-09-23 · **Estado:** `DECIDED` (escopo, não detalhe de
implementação) · **Fonte:** dois documentos de pesquisa fornecidos pelo
usuário nesta sessão (`KOF_TECHNICAL_DEBT_SCOUT_AGENT_V1_BACKUP.md`,
`KOF_TECHNICAL_DEBT_SCOUT_AGENT_V2.md`) — a V2 substitui a V1 conforme o
próprio §0 dela. O contrato operacional condensado pousou em
`docs/development/technical-debt/DEBT_SCOUT_CONTRACT.md` no mesmo commit
deste registro.

**Decisão:** abre-se uma nova frente de ferramenta — descoberta/
documentação automatizada de dívida técnica histórica —, restrita
estritamente à **Wave 1** do desenho V2: só detectores determinísticos
(nenhum LLM no laço), um schema de candidato estável + dois fingerprints
(finding/dívida), descoberta de branch/ref que nunca hardcoda uma versão,
e um orquestrador de scan. **Nenhum script e nenhum workflow deste pouso
pode chamar a API de escrita de Issues do GitHub, e nenhum workflow
concede `issues: write` a este sistema.** O Scout alimenta a triagem
humana (hoje: `docs/development/tech-debt.md`, o ledger mantido pela
mantenedora) — nunca é um segundo escritor desse ledger, e não decide
contrato de linguagem, não implementa correções, não fecha Issues, não
faz merge de PR (a regra 6 se aplica a qualquer coisa que o Scout
levante e que exija mudança de contrato).

**Por que um registro de decisão para ferramenta, não só um edit:** esta
frente pode, em waves futuras que os documentos-fonte descrevem, ganhar
a capacidade de abrir Issues no GitHub de forma autônoma. Essa
capacidade **não** está autorizada por este registro — avançar além da
Wave 1 (upload de SARIF, o Debt Inbox e, principalmente, qualquer mudança
de permissão de workflow rumo a `issues: write` para este sistema) exige
seu próprio registro em `DECISIONS.md` com a autorização de fase da
mantenedora (`DEBT_SCOUT_CONTRACT.md` §7, fases S1/S2/S3), do mesmo jeito
que `D-ARTIFACT-TRUST` condicionou o caminho de escrita de
`scripts/agent-close-issue.sh`.

**Rejeitado neste pouso:** copiar qualquer um dos dois documentos-fonte
de 104 seções verbatim para o repositório (viola a lição de "partes
pequenas", `AGENTS.md` §"Lição aprendida (09/04)"); um framework paralelo
de risco/evidência/despacho (a própria V2 §46 manda reusar
`scripts/agent-*.sh`); qualquer caminho de auto-publicação antes de uma
fase de trust-rollout ser explicitamente autorizada.

**Evidência:** os dois documentos-fonte (fornecidos na sessão, 23/09/2026);
infraestrutura de agente existente (`scripts/agent-common.sh`,
`agent-dispatch-gate.sh`, `agent-state-fingerprint.sh`, `agent-risk.sh`,
`agent-evidence.sh`, `agent-verify.sh`) confirmada presente e reusável;
`docs/development/tech-debt.md` (aberto 23/09) confirmado como o ledger
manual existente que esta ferramenta alimenta em vez de duplicar; nenhuma
label `technical-debt` existe ainda no GitHub (`gh label list`), então
qualquer rotulação futura fica no corpo do candidato conforme o contrato,
não numa taxonomia inventada na hora.

- **Relações:** `Related: AGENTS.md regra 6/8/9/10/11, R6,
  D-ARTIFACT-TRUST, D-KOF-FIRST, tech-debt.md.`

## D-DEBT-SCOUT-W2 — Wave 2 do Debt Scout autorizada: qualificação de evidência, clustering de causa-raiz, principal/interest/lock-in, classificador C2/C3, Debt Inbox, SARIF — ainda shadow, ainda zero publicação de Issue (dirigido pelo usuário, 23/09/2026)

**Data:** 2026-09-23 · **Estado:** `DECIDED` · **Fonte:** o usuário disse
"segue para wave2" depois de revisar o pouso da Wave 1 (contrato
`DEBT_SCOUT_CONTRACT.md` §11, `KOF_TECHNICAL_DEBT_SCOUT_AGENT_V2.md` §94).

**Decisão:** a Wave 2 do desenho V2 é autorizada: um context builder
KOF-first determinístico, clustering de causa-raiz (por `debt_fingerprint`),
o vetor principal/interest/lock-in (nunca um score único, contrato
§11/§12), um classificador de confiança C2/C3 que só promove um cluster
além de `C1` quando o checklist de evidência obrigatória (contrato §7,
requisitos de C3) está de fato satisfeito — nunca por muitos sinais
fracos —, um Debt Inbox para achados C2 sem localização de código, e um
escritor SARIF para achados C0/C1/C2 que TÊM localização (contrato §37:
"prefira SARIF/code scanning a abrir Issue" exatamente para esse caso).

**O que este registro explicitamente NÃO autoriza ainda:** o publisher
de C3, qualquer concessão de `issues: write` em lugar nenhum, qualquer
trigger push/schedule. A Wave 2 fica `mode: shadow` de ponta a ponta —
`issues_created` continua sendo `0` estrutural, provado do mesmo jeito
que a Wave 1 provou (o relatório/workflow afirma isso, não só pretende).
Avançar para a Wave 3 (§95 do spec-fonte: o publisher canary de C3)
precisa do próprio registro em `DECISIONS.md` com a autorização de fase
S1 da mantenedora (`DEBT_SCOUT_CONTRACT.md` §7), exatamente como
`D-DEBT-SCOUT` já dizia.

**Novo privilégio que esta wave introduz, com escopo apertado:** o job
`scan` do workflow ganha `security-events: write` (só upload de SARIF,
`github/codeql-action/upload-sarif`) — continua zero `issues: write` em
qualquer lugar. É a mesma disciplina de mínimo privilégio que
`D-ARTIFACT-TRUST` já aplica em todo outro workflow deste repositório.

**Evidência:** a própria rodada real da Wave 1 (`https://github.com/
KofLang/Kof4j/actions/runs/35839064175`) mediu que, dos 33 candidatos
reais (32 `C0` SATD, 1 `C1` de branch-drift), **zero** se qualificou
para Issue sob os próprios gates do contrato quando triados à mão —
essa triagem é exatamente o que o classificador da Wave 2 agora faz de
forma mecânica, então o resultado da próxima rodada é conferível sem
re-triagem manual toda vez.

- **Relações:** `Related: D-DEBT-SCOUT, DEBT_SCOUT_CONTRACT.md §7/§11/§37,
  D-ARTIFACT-TRUST, regra 6.`

## D-BAREMETAL-RING1-SURFACE — superfície do domínio ring1 = um marcador embutido `ring1(fn)` (sem sintaxe nova); o compilador baixa para a transição CPL0→CPL1 (mantenedora 23/09/2026)

**Data:** 2026-09-23 · **Estado:** `DECIDED` (a mantenedora respondeu a múltipla
escolha no chat, opção "Built-in marker function") · **Origem:** B-6.1 pousou
(GDT/TSS/IDT do Kof sob OVMF); o B-6.2 (entrada CPL1) estava bloqueado nesta
decisão rule-6 (`D-BAREMETAL-BOOT` §3 deixou a superfície Kof aberta).

**Decisão:** a forma de o código Kof mirar um **domínio ring1** é uma **função
marcadora embutida `ring1(fn)`** — uma chamada cujo nome do alvo é reservado e
baixado pelo compilador; **sem gramática, palavra-chave, bloco, anotação ou
modificador novos**. `ring1(fn)` roda o valor de função Kof dado em **CPL1** e
devolve o resultado a CPL0: o runtime faz a transição `iretq` (`CS=0x18` RPL=1,
`SS=0x20`, `rsp0` do TSS sustentando o trap de volta), chama a função, e o
caminho de fault de instrução privilegiada retorna por um gate ring0.

**Razão (Lei da Simplicidade, rule 11):** o Kof declara a **intenção**
(`ring1(fn)`), a plataforma faz o mecanismo. Uma chamada embutida é a menor
superfície possível — parseia como chamada comum, existe em zero produções de
gramática e lê exatamente como um humano escreveria. Palavra-chave/bloco foi
rejeitado como superfície maior e menos Kof.

**Restrições (semântica congelada intocada):**
1. O embutido só faz sentido no perfil bare-metal/UEFI x86_64 com anéis
   (`NativeProfile.UEFI_RING`/seu caminho de boot). Em qualquer outro alvo/backend
   ele deve falhar com **diagnóstico nomeado** (`NATIVE003`), nunca no-op
   silencioso (R6) — um domínio CPL1 não existe em JVM/JS/riscv/aarch64 hoje
   (R7, escopo honesto).
2. **Aditivo**: código que não chama `ring1` fica intocado; sem mudança de
   operadores, precedência, ordem de avaliação ou API existente.
3. `ring1` recebe um **valor de função** (função/lambda Kof); não é palavra-chave
   de statement, então compõe como expressão devolvendo o resultado da função.

**Fila:** B-6.2 (`PLAN-BAREMETAL-BOOT`), lane baremetal, sessão 9092; o B-6.3
(prova de `#GP` no domínio ring1 + sabotagem do descritor da GDT) segue quando o
B-6.2 estiver provado. A condição 3 do gate 0.5.0 mantém o plano na allowlist
(plano em voo, padrão `D-BAREMETAL-BOOT`) — o corte não espera por ele.

**Evidência:** resposta de múltipla escolha da mantenedora no chat, 23/09/2026
(esta sessão), opção "Built-in marker function (Recommended)"; registrado aqui
antes de qualquer código do B-6.2 (rule 6: não se ataca frente sem decisão
travada).

- **Relacionados:** `Related: D-BAREMETAL-BOOT, D-UNIVERSAL, D-BOOTSTRAP, rule 6, rule 11, R6, R7`.

## D-BAREMETAL-BODIES — corpos de plataforma B-5 autorizados (tempo no BIOS via RTC primeiro) e B-4 (MCU) autorizado com seu pré-requisito de coletor (mantenedora 24/09/2026)

**Data:** 2026-09-24 · **Estado:** `DECIDED` (resposta da mantenedora no chat,
esta sessão: "autorizo 1 e 2") · **Estende:** `D-BAREMETAL-BOOT` (o plano da
frente `PLAN-BAREMETAL-BOOT` §B-4 / §B-5)

**Decisão (palavras da mantenedora):** *"autorizo 1 e 2"* —

1. **B-5 (corpos de plataforma)** pode ser implementado, começando pela face
   **BIOS**: `kof_plat_time` preenchido pelo **RTC** CMOS (portas de E/S
   `0x70`/`0x71`), devolvendo o tempo epoch que a ABI já espera
   (`ts[0]=tv_sec`, `ts[1]=tv_nsec`), de modo que um `time.now()` Kof rode bare
   sob SeaBIOS. Capacidades que ainda não têm corpo permanecem **recusas
   NOMEADAS** (R6), nunca stub silencioso; uma recusa no BIOS deve imprimir
   diagnóstico **ASCII legível** (não a forma UTF-16 do UEFI, que o COM1
   renderiza como lixo com NULs intercalados).
2. **B-4 (MCU)** é autorizado como frente; segue **bloqueado pelo seu
   pré-requisito duro** — o coletor de GC `native-multiarch.md` **G-4/G-5**
   (RAM em escala de KB) — que é desenvolvido **primeiro**, em fatias.

**Nada relaxado:** a **superfície/semântica Kof não muda** — só os corpos da
HAL `kof_plat_*` atrás da ABI existente (sem mudança de gramática, operador,
modelo de tipos ou contrato congelado); todo caminho ainda ausente mantém
**diagnóstico nomeado** (R6/R7); a semântica de anéis `#GP`/CPL do B-6 fica
inalterada.

**Fila:** `roadmap.md` §23 (frente baremetal) + claim no DOING no mesmo commit;
**B-5 tempo no BIOS = primeira fatia** (prova: `BiosBootE2ETest` bota um
`time.now()` Kof sob SeaBIOS); **B-4 segue o coletor G-4/G-5**.

**Evidência:** mensagem da mantenedora 24/09/2026 (chat, esta sessão);
registrado aqui **antes** de qualquer código de B-5/B-4 (rule 6: não se ataca
frente sem decisão travada).

- **Relacionados:** `Related: D-BAREMETAL-BOOT, D-UNIVERSAL, D-BOOTSTRAP, rule 6, rule 11, R6, R7, R12`.

## D-BAREMETAL-MCU-GC — o B-4 NÃO fecha antes de o coletor G-4/G-5 ser portado para 32-bit; `kof_plat_time` no MCU = recusa NOMEADA do wall + mono via SysTick (mantenedora 24/09/2026)

**Data:** 2026-09-24 · **Estado:** `DECIDIDO` (respostas da mantenedora no chat,
nesta sessão, 24/09) · **Estende:** `D-BAREMETAL-BODIES` (o item 2 deixou o B-4
bloqueado pelo coletor; este trava o critério de fechamento e a semântica de
tempo do MCU)

**Decisão (respostas da mantenedora, em ordem):**

1. **O B-4 ainda NÃO fecha.** O slice mínimo print-only do MCU (hello + reset
   path da vector table asserido nas duas arches riscv32 e Cortex-M3, o resto
   recusado honestamente com `NATIVE002`/`CONC003`) satisfaz o aceite literal do
   §B-4, mas **não** é o critério de fechamento: o coletor `native-multiarch.md`
   **G-4/G-5** precisa ser **portado para o MCU 32-bit** (alocação + mark/sweep +
   uma prova long-running que recicla sob `qemu-system-riscv32 -M virt` e/ou
   `qemu-system-arm -M mps2-an385`) antes de o `PLAN-BAREMETAL-BOOT` sair de
   `docs/development/`. O heap é dimensionado pelo linker script (escala de KB),
   não pela arena fixa de 262 144 B em `.bss`.
2. **Semântica do `kof_plat_time` no MCU** (um MCU sem RTC): o relógio **wall**
   (`time.now()`) é **recusa NOMEADA** (R6/R7) — nunca uma epoch falsa; o
   caminho **monotônico** (`kof_plat_time_mono`, e `time.sleep` onde couber) é
   fornecido pelo contador **SysTick** com `boot = 0`.

**Nada relaxado:** nada. A **superfície/semântica Kof não muda** — só internos do
runtime 32-bit e os corpos da HAL `kof_plat_*` atrás da ABI existente; todo
caminho ainda ausente mantém **diagnóstico nomeado** (R6/R7).

**Fila:** `roadmap.md` §23 (frente baremetal) + claim no DOING no mesmo commit;
**primeira fatia = o port do alocador + coletor 32-bit** (prova sob qemu), depois
os corpos de tempo do MCU.

**Evidência:** respostas da mantenedora 24/09/2026 (chat, esta sessão), às duas
opções apresentadas após o pouso do B-4.3 sl.1; registrado aqui **antes** de
qualquer código de coletor/tempo-MCU (regra 6).

- **Relacionados:** `Related: D-BAREMETAL-BOOT, D-BAREMETAL-BODIES, D-UNIVERSAL, D-BOOTSTRAP, rule 6, rule 11, R6, R7`.

## D-FULL-PARITY-050 — Paridade total da plataforma é a regra ABSOLUTA de todo plano e IMPEDITIVO da 0.5.0: a release não corta enquanto `docs/development/parity/PARITY-GAPS.pt_BR.md` tiver linha aberta (mantenedora 24/09/2026)

**Data:** 2026-09-24 · **Estado:** `DECIDIDO` (mensagens da mantenedora no
chat, nesta sessão, 24/09) · **Estende:** `D-UNIVERSAL`,
`D-RELEASE-0.5.0-SCOPE`, invariante de plataforma R7

**Decisão (palavras da mantenedora, em ordem):** *"A PLATAFORMA UNIVERSAL TA
IMPLEMENTADA SÓ PRA JVM? ISSO É INACEITAVEL. TUDO TEM QUE TER PARIDADE TOTAL.
DOCUMENTE ISSO COMO IMPEDITIVO PARA 0.5.0"*; *"A REGRA ABSOLUTA PRA QUALQUER
PLANO É A PARIDADE TOTAL"*; *"APROVEITA E PESQUISA TUDO QUE TA COM PARIDADE
PARCIAL E BOTA EM docs/development/parity PARIDADE TOTAL É INDISPENSAVEL"*.

**O que foi decidido:**

1. **Paridade total (JVM/Script ≡ Native x86-64 ≡ Native riscv64/aarch64 ≡
   JS, byte/golden vs o oráculo JVM) é a regra ABSOLUTA de todo plano** — uma
   frente nova que pousar JVM-first DEVE carregar o plano de paridade no
   mesmo item da fila; "gap declarado" é estado de rastreio, nunca de
   aceitação.
2. **Condição 8 da release 0.5.0 (full_parity) é IMPEDITIVA:** o ledger
   `docs/development/parity/PARITY-GAPS.md`(+PT) precisa ter **0 linhas
   abertas** no corte. O ledger foi criado medido (24/09) com as 16 linhas
   abertas (códigos de `DomainGapCodesTest`, `Kof*.java`, tabela de paridade
   da stdlib e `known-bugs.md`): process/shell (`PROC001`), ssh (sem código
   ainda — catalogar), media (`MEDIA001`/`MEDIA003`), mq (`MQ001`), gpu JS +
   golden cross (`GPU001`), observability golden cross (`OBS003`), time cross
   (`TIME002`/`TIME004`), cache/config/log golden cross + log interpretador
   (`CONF001`), `math.pow` cross (`MATH001`), `strings.reverse` não-ASCII +
   cinco métodos de String (`NAT-STR01`/`STR003`), web T1 native (`WEB00x`),
   `kof.io` cross (`NAT006`/`NAT007`), security cross
   (`SECN001/003/004/005`), `orm.*` nativo (`ORM001`), `db.*` nativo
   query/prepared (`DB001`).
3. **A condição 1 existente (paridade 100%) medida na MATRIZ DE CONFORMIDADE
   permanece** — o ledger ACRESCENTA a cauda longa que a matriz nunca cobriu
   (golden não medido conta como ABERTO, Q5: sem falso verde).
4. **Definition of done por linha:** face compila + golden/E2E byte a byte +
   tabelas de docs atualizadas no MESMO commit + linha removida no MESMO
   commit.
5. **Todo plano FUTURO herda a regra:** plano sem seção de paridade (alvos ×
   prova) está mal classificado (regra dos três estados) — o agente adiciona
   ou roteia o gap para este ledger.

**Evidência:** mensagens da mantenedora 24/09/2026 (chat, esta sessão);
ledger criado com o estado completo medido no mesmo commit; gate de máquina
ligado no `scripts/check_release_050_gate.sh` (`full_parity`).

- **Relacionamentos:** `Relaciona: D-UNIVERSAL, D-RELEASE-0.5.0-SCOPE,
  D-DB-GAPS, D-GRAFICOS-GAMING, R6, R7, Q5, regra 6`.

## D-MEMORY-SAFETY — Front de segurança de memória (propriedade/tempo de vida/empréstimo/aliasing/FFI) aberto em `docs/development/`, dono = lane de paridade; investigação primeiro, core intocado até a fila atual fechar (mantenedora 25/09/2026)

**Data:** 25/09/2026 · **Estado:** `DECIDIDO` (mantenedora 25/09, esta sessão) · **Extende:** `D-KOF-FIRST`, `D-FULL-PARITY-050`, rule 6, rule 8, rule 11, R6, R7, SG/D-KOF-AS-CLOUD

**Decisão (palavras da mantenedora, em ordem):** *"pode botar em docs/development e ja assumir essa frente"*; *"o brief (seções 1–27) — pode ir pra docs/development e ja assumir essa frente"*; *"o trabalho de código só começa DEPOIS que a fila atual estiver concluída"*; *"Kof-first investigation — no assumption that Kof works like Rust, C++, Java, Kotlin, Swift or Zig"*; *"Architecture before code — Phase 0 investigation and Phase 1 spec precede ANY compiler edit"*; *"Implementation waits for the current queue (brief, final line)"*; *"Simplicity Law (rule 11): strong guarantees without turning Kof code into an endless chain of lifetime annotations"*; *"Cross-target by construction — JVM, Native, JS and the planned WASM express the same Kof semantics; GC on JVM/JS never excuses semantic divergence; FFI must define the owner per crossing"*; *"Diagnostics and tests are part of the feature — every rule ships with valid/invalid/expected-diagnostic/regression/per-backend cases"*; *"Forbidden: copying Rust's borrow checker, inventing syntax (let, const, foreign move markers), a null-safety rewrite, big-bang compiler refactors, single-backend ownership, hiding ownership problems in the runtime"*.

**O que foi decidido:**

1. **Investigação antes de código** — Fase 0 produz `docs/spec/memory-safety-investigation.md` (estado atual, riscos, modelo de lifetime implícito, pontos frágeis, proposta, alternativas, impacto no backend, impacto de compatibilidade, plano incremental) ANTES de qualquer edição no compilador. Fase 1 produz a spec formal `docs/spec/memory-safety.md` (Ownership, Lifetime, Borrowing, Aliasing, Mutability, Move, Copy, Clone, Drop/Destruction, Escape, Closure Capture, Concurrency, FFI, Unsafe Boundaries). A semântica existe ANTES da implementação.
2. **A fase de implementação só começa depois que a fila atual fechar** (a linha final do brief: "Esse trabalho só começa depois que a fila atual estiver concluída") — até lá: investigação, drafts de spec e estudos de infraestrutura de compilador apenas, ZERO edições prematuras no core.
3. **Complexidade acidental zero na superfície da linguagem** (regra 11): o modelo deve ser forte o suficiente para tornar classes inteiras de bugs impossíveis sem transformar Kof em uma corrente infinita de anotações de lifetime; sucesso = o compilador consegue dizer "este programa não pode produzir esta classe de erro" (use-after-free, double-free, dangling reference, invalid lifetime escape, unsafe mutable aliasing, unexpected null, accidental data race) com uma fronteira explícita onde uma prova é impossível.
4. **Cross-target por construção** — JVM, Native, JS e o planejado WASM devem expressar a MESMA semântica Kof (GC na JVM/JS nunca justifica divergência de aliasing/mutabilidade/lifetime); fronteiras FFI devem definir owner/keeper/free-writer/guardian para cada tipo de crossing.
5. **Diagnósticos e testes são parte da feature** — toda regra pousa com válido/inválido/diagnóstico-esperado/regressão/por-backend testes (suítes pequenas por domínio, adaptadas à árvore de testes real, nada de suíte gigante).
6. **Proibido:** copiar o borrow checker do Rust, inventar sintaxe (`let`, `const`, marcadores de move estrangeiros), um rewrite de null-safety, refatoração big-bang do compilador, propriedade single-backend, esconder problemas de propriedade no runtime.

**Plano de fases (do brief, ordem verificável por máquina):** Fase 0 doc de investigação → Fase 1 spec (`docs/spec/memory-safety.md`) → Fase 2 infraestrutura do compilador (representações ownership/lifetime/borrow/alias/mutability/escape/resource-state) → Fase 3 primeiras garantias (use-after-move, dangling refs, escapes inválidos, aliasing mutável, dupla propriedade/destruição) → Fase 4 closures/async → Fase 5 Native+FFI → Fase 6 paridade JVM/JS/WASM da semântica. Cada fase gateia a próxima; uma fase sem seus testes de prova não fecha.

**Evidência:** mensagens da mantenedora 25/09/2026 (chat, esta sessão): o brief completo (seções 1–27) + "pode botar em docs/development e ja assumir essa frente"; plan doc criado no mesmo commit (`docs/development/memory-safety-plan.md` EN+PT).

- **Relacionamentos:** `Relaciona: D-FULL-PARITY-050, D-UNIVERSAL, D-RELEASE-0.5.0-SCOPE, D-DECOMPILER, D-BOOTSTRAP, D-DB-GAPS, D-GRAFICOS-GAMING, D-MAKEALIVE, D-MAKEALIVE-CLI, D-KOF-AS-CLOUD, D-KOF-FIRST, D-RELEASE-0.5.0-GATE, D-BRANCH-0.5.0, D-BAREMETAL-BOOT, D-BAREMETAL-BODIES, D-BAREMETAL-MCU-GC, D-GRAFICOS-GAMING, D-UNIVERSAL, D-DB-GAPS, rule 6, rule 11, R6, R7, D-MEMORY-SAFETY`.

### Atualização 26/09 — Fase 1 CLOSED, Fase 2 DESTRAVADA (mantenedora, chat)

**Fase 1 FECHADA 25/09** (opção A — spec aceita; espelhos pousados em
`8d5634216`/`0670f2312`). Em 25/09 a mantenedora escolheu a opção `J` (a
fila espera a fila atual); em 26/09 ela destravou — palavras dela:
**"fase 2 destravada"**. **Fase 2 (infraestrutura de compilador) = EM
DESENVOLVIMENTO**, dona = lane paridade (claim desta lane no `DOING.md`
EN+PT, mesmo commit). Escopo travado: representações internas do compilador
no pacote `dev.kof.compiler.memory` — ownership/lifetime/borrowing/
aliasing/mutability/escape/resource-state + o enum de códigos de diagnóstico
`MEMxxx` dos §1–§10 da spec — SOMENTE estruturas e testes; **zero mudança de
comportamento (suite byte-green)**; emissão/encaminhamento dos diagnósticos é
da Fase 3. As proibições do brief continuam valendo (nada de copiar o
borrow-checker, nenhuma sintaxe nova, nenhuma reescrita do null-safety).

**Evidência:** mensagem da mantenedora 26/09/2026 (chat, sessão autônoma);
viradas do plano+spec EN+PT no mesmo commit.

**Ver também:** chamada (4) de `D-DECISION-BATCH-2609` — a mesma decisao da
mantenedora registrada em lote pela lane do watcher; este bloco trava o
escopo da implementacao, nao e uma segunda decisao.


## D-DECISION-BATCH-2609 — lote de quatro decisões da mantenedora: §493 Native lança como a JVM; merge do #619 SEGURA; merge do #624 SAI; Fase 2 memory-safety DESTRABA (mantenedora 26/09/2026)

Quatro decisões tomadas de uma vez pelo prompt multipla-escolha da sessao
(26/09/2026, ~02:40):

1. **§493 — a lei e o comportamento da JVM**: `orm.delete`/`deleteAll` sobre
   MySQL com conexao morta/invalida deve LANCAR a string de erro em todo
   target. O Native x86-64 e o cross (riscv64/aarch64) hoje devolvem `true`
   — esse e o bug (caminhos de erro de RuntimeDb5/`RtB75`/`RtB54`). Coerente
   com "excecoes sao Strings", R6 (nunca silenciar) e a Acceptance do
   db-parity-plan (sem divergencia silenciosa). **Fila:** fechar §493 no
   ledger com prova RED→GREEN cross-target (fixture server-down); ai a regra
   de conclusao move `db-parity-plan` para `docs/stdlib/`. O codigo e da
   lane gaps-db/native-runtime; esta entrada e o gate.
2. **PR #619 (beta-0.5.0 → main) — merge SEGURADO.** A branch segue
   recebendo trabalho; o ato de release e exclusivo da mantenedora (regra
   10). Nenhuma acao para agentes alem de manter `beta-0.5.0` verde.
3. **PR #624 (#623, box de tamanho estendido MP4) — merge LIBERADO.**
   Verificado antes de pousar: commit unico `d7f0745fc` de
   `PublioSantos/Kof4j`, APROVADO; os dois vermelhos eram artefato de base
   antiga (branch de 25/09 10:42, anterior aos fixes §506/§507/§508). Merge
   simulado em ramo descartavel sobre o tip `c298406e2`: `run-agent-tests.sh`
   VERDE + bateria de media 17/0F/0E. Mergeado como merge commit
   preservando o SHA do autor.
4. **memory-safety Fase 2 — AUTORIZADA agora.** A trava da "opcao J" (zero
   edits prematuros no core) foi levantada pela mantenedora: a lane parity
   (dona da frente por `D-MEMORY-SAFETY`) segue para a Fase 2 — estruturas
   internas do compilador de ownership/lifetime/borrow/alias/mutabilidade/
   escape/resource-state. Os gates da Fase 1 seguem satisfeitos
   (`docs/spec/memory-safety.md` pousado 25/09); a Fase 3+ continua gateada
   pelos testes de prova da Fase 2.

## D-QUALITY-PIPELINE-2609 — esteira de branches = pipeline de qualidade (lab → testing → prerelease → stable → release/x.y.z → tag); lab sem CI por push; gates de promoção 80%/100%/100%+CLOSEALL; migração ATÔMICA pós-0.5.0 com `release/0.5.0` de piloto (mantenedora 26/09/2026)

**Evidência:** issue #626 (proposta da mantenedora, 26/09 04:52Z) +
resposta da mantenedora 26/09 05:53Z aceitando a revisão técnica da lane
paridade/qualidade ("desenho fechado conceitualmente como uma quality
pipeline").

(O comentário da lane irmã na #626 registrou o mesmo desenho sob o nome
`D-QUALITY-PIPELINE`; é a MESMA decisão e esta entrada datada é o registro
único — uma representação por afirmação. `794aa4721` havia commitado os
marcadores de conflito do stash-pop; `e29ba47b3` mesclou os dois lados
nesta entrada.)
**Decisão (opção: pipeline de qualidade por estágios, não ambientes soltos):**

| Estágio | Papel | CI | Quebra? | Publicável? |
|---|---|---|---|---|
| `lab` | experimentação | **SEM CI por push** (validação pesada vai para a promoção) | contrato pode mudar (ver ponto em aberto abaixo) | não |
| `testing` | integração/QA | suíte completa na promoção `lab→testing` (primeiro gate formal) | idealmente não | potencialmente |
| `prerelease` | candidato público | **≥80% verde para entrar de `testing`; 100% para avançar** | sem features | sim |
| `stable` | contrato fechado | 100% + critérios de fechamento da versão (CLOSEALL+docs) | não | sim |
| `release/x.y.z` | só empacotamento (temporária, de `stable`) | validações finais | não | sim |
| tag | o contrato público | — | — | — |

- **Hotfix em `stable`:** PR + backport explícito + revalidação antes de
  voltar ao `stable` — nunca porta dos fundos para desenvolvimento.
- **Prova de promoção é objetiva:** o checklist de
  `release-beta-0.5.0-prep.md` (cond.7 = `check_known_bugs_status.sh`
  live-vazio + matriz de conformidade) vira a definition-of-promotion.
- **Timing (rígido):** a migração acontece SÓ depois que o ciclo do 0.5.0
  fechar no modelo atual; `release/0.5.0` serve de piloto do último estágio.
  O corte é UMA mudança atômica: branches + CI + scripts + `AGENTS.md` +
  `DOING.md` + `DECISIONS.md` + automações (migração parcial = agente
  empurrando no lugar errado — palavras da mantenedora).
- **PONTO EM ABERTO (não decidido):** se o `lab` mantém o piso de
  zero-regression (regra 8) mesmo sem CI por push — levantado na review
  da lane ("não quebrável", precedentes `7f174a6f`); a resposta da
  mantenedora não tocou nisso; decidir no plano de corte, não assumir.
- **Até lá NADA muda (histórico — este bullet antecede o cutover e está
  substituído pelo CUTOVER EXECUTADO abaixo / `D-BRANCH-PIPELINE`):**
  `beta-0.5.0` era a branch ativa (`D-BRANCH-0.5.0`, `SUPERSEDED` 28/09);
  agentes seguiam empurrando para ela; #619 segue HELD (regra 10). Fila: roadmap §23 `TIER 14`.
- **PONTO TECNICO ABERTO #2 (denominador do `≥80%`):** branch protection
  mede checks como booleanos — o percentual nao e aplicavel por protection,
  tem de viver num script de promocao sobre lista FIXA e enumeravel de checks
  (Build+Tests, Native cross, Structural, kof.io x3, CodeQL Gate, bots), e um
  "80% que tolera vermelho" precisa classificar QUAIS vermelhos: funcionais
  (bloqueiam sempre — regra 8) vs ambiente/toolchain documentados (whitelist
  nomeada). Formula proposta para o commit da migração: `testing -> prerelease`
  = zero vermelho funcional + no maximo N vermelhos de ambiente nomeados no
  whitelist (= o ~80% mensuravel); `prerelease -> stable` = 100% na mesma
  enumeracao. Generalizacao natural do `check_release_050_gate.sh` (ja faz
  esse formato para a release). Respondido na issue (comentario da lane).
- **CUTOVER EXECUTADO (28/09/2026, mantenedora: "0.5.0 acabou de ser mergeada na
  main, pode começar"):** `lab`/`testing`/`prerelease`/`stable` criadas a partir de
  `origin/main` (`317d9f6b1`); `beta-*` congeladas; CI re-apontado (`codeql.yml`,
  `kof-*-bot*.yml`, `pr-base-guard.yml`, `dependabot.yml`, `scripts/codeql-gate.sh`);
  broadcast + instruções de migração na issue #647. Respostas da mantenedora:
  (a) o cutover acontece AGORA (0.5.0 fechou); (b) `lab` MANTÉM o piso zero-regressão
  (regra 8) mesmo sem CI por push — **OPEN POINT resolvido**; (c) o denominador `≥80%`
  foi DROPADO: toda promoção é 100%. `AGENTS.md`/`.pt_BR.md` agora declaram
  `D-BRANCH-PIPELINE: active branch = `lab``. A automação de promoção é o
  `TIER 14.3` (roadmap).

## D-COMPLETE-FIRST — regra de escolha para decisoes automaticas: a solucao idiomatica E COMPLETA (sem stub, sem desistir, sem assumir gap, paridade total) e A OPCAO que as lanes seguem; alternativas ralas/stub/aceitar-gap nao sao opcoes (mantenedora 26/09/2026)

**Data:** 2026-09-26 · **Estado:** `DECIDIDO` (mantenedora, chat, sessao)

**Decisao (palavras da mantenedora, em ordem):** "me da soluções idiomaticas,
nada de desistir ou assumir gap" → "muito menos stub" → "percebe que depois
das minhas reclamações vc me deu só uma opção? **é ela q vc segue**".

**A regra, operacional:** quando uma frente rule-6 e triada, a **forma
completa idiomatica** — a que seria apresentada como contrato real (passe de
analise, nao diagnostico solto; motor completo, nao facade `eval`-devolve-
String; runner ligado ponta a ponta, nao flag pela metade; liberacao de
ciclo de vida deterministica, nao "espera o GC") — **e a decisao que a lane
segue**, sem devolver a voto. O que NAO e escolha: desistir, "aceitar um gap"
como resposta a uma necessidade legitima, stubs/facades/APIs ralas, ou uma
fatia que finge que o resto existe. Escopo de alvo legitimo (R7: JVM-first
com diagnostico NOMEADO no caminho impossivel-no-alvo) nao e gap — e a
entrega completa do escopo declarado. Quando existirem duas ou mais opcoes
genuinamente completas (contratos cheios diferentes), a mantenedora ainda
vota entre elas; quando exatamente uma e completa, segue-se ela de imediato.

**Consequencias operacionais — as quatro perguntas rule-6 abertas de 26/09
resolvem-se como a opcao cheia de cada uma** (cada uma pousa como pacote
completo, nunca stub, com prova por alvo antes de fechar):

1. **O-02 × N-02 (memory-safety Fase 3):** resolvido **sem literal null** —
   o passe de analise de ownership/lifetime no pipeline do compilador com
   emissao MEM001/MEM002/MEM005 e os casos de interacao nos 4 alvos. O
   DECISION REQUEST do plano esta FECHADO por esta regra; Fase 3 DESTRAVADA.
2. **X2 (interop Python/R):** o pacote oficial `interop` completo —
   marshalling bidirecional tipado (Int/Double/Bool/String/List/Map/record
   ↔JSON), gerenciamento real de processo (spawn, stdin/stdout, timeout,
   exit, cancelamento), estado de sessao, erros nomeados `INTEROP00x`, E2E
   por alvo, corpus (`training/idioms/interop.md` + `learn/` + matriz de
   paridade) sincronizado. Nasce `experimental` pela R5.
   **Progresso 26/09 — fatia 1 POUSADA (item ainda aberto, fatias 2+ pendentes):**
   o motor Python `KofPy` pousou como host escrito em Kof (`interop-py-host.kf`)
   sobre `process.spawn` + `json.decode<T>` tipado — superficie `var py = KofPy(src)`
   + `py.callInt/callDouble/callBool/callString(fn, listOf(...))`, goldens
   JVM≡x86≡JS≡SCRIPT medidos byte-identicos, `INTEROP004`/`INTEROP006` nomeados,
   recusa `INTEROP005` onde a face nao esta provada (cross travado pelo §514,
   ABERTO — lane native; ANDROID/MCU/RISCV32 pela R7). O contrato replay
   "a sessao E a fonte" substitui o `python -` de vida longa (medido impossivel
   sem EOF); uma superficie de sessao viva exige tipo de handle nomeado =
   regra 6, fatia futura. Os args Double do motor expuseram e CORRIGIRAM o §513
   (colapso da tag de elemento do json) na raiz, com regressao oraculo-JVM.
   Restante: records↔JSON + motor R (fatia 2), timeout/cancel (fatia 3),
   re-entrada cross com o §514 (fatia 4), DoD de corpus + promocao (fatia 5).
3. **X8 fatia 3 (suites nomeadas):** a tag opcional do primitivo `test`
   fluindo parser→typer→IR→catalogo do runner (fonte unica), `kof test --tag`
   com filtragem real, setup/teardown condicionais como funcoes (setup que
   falha pula os testes do grupo, nomeados), goldens E2E no CLI, recusa
   runner does not exist.
   **POUSADA (26/09):** a tag = literais extras de string na declaracao
   (`test "n", "smoke" { }`) — sintaxe nova zero (rule 11; a superficie SG-023 iii
   mantida); o filtro e decidido no COMPILE-TIME no `TestHarnessBuilder`, entao os
   quatro alvos executam o mesmo catalogo filtrado (rule 5 por construcao);
   `setup`/`teardown` sao funcoes comuns sem argumentos (setup que lanca = SKIP
   nomeado; teardown roda via `finally` ate em teste que falha). Provas:
   `TestTagsE2ETest` 10/10 + `CmdTestTagTest` 4/4 + `StructuredTestE2ETest` legado
   intacto + corpus (`learn/23-testing` EN+PT, `training/tooling/cli` EN+PT).
4. **auto-unsubscribe do `kof.ui`:** liberacao deterministica no **unmount**
   do componente (o caminho que ja anda a arvore), travas de leak
   (`subscriptionsLive()`/`storesLive()` = 0 apos mount/unmount × N),
   subscription fora de componente segue sem dono e manual por design,
   JVM/Native mantem o no-op documentado da paridade UI=KofJS.
   **POUSADO (26/09):** a metade das subscriptions ja havia pousado como
   `D-UI-AUTOUNSUB` (A); este item completou o ciclo — um **Store criado
   durante o ciclo de vida de um componente pertence a ele e morre no
   unmount** (a entrada e deletada: valor + subscriptions carregadas vao
   junto; `AppState` nunca e atribuido, app por definicao), e a trinca de
   sondas `uiNodesLive()` / `storesLive()` / **`subscriptionsLive()`** (nova
   — 7 pontos de wiring, face JVM honestamente 0, asm Native 0, Script herda
   UI002) trava isso. Evidencia: `UiLeakLockE2ETest` 6/6 (store + sub do
   componente morrem, medido `1,1→0,0`; controle app-scope sobrevive `1,1`;
   AppState sobrevive; unsubscribe manual conta exato `2→1→0`; stress 10k
   ciclos `0\n0\n0` em JVM/Native/JS) + bateria UI existente 83/83 intacta
   (regra 2). Corpus: claim §279 stale corrigido + idiom de trava de leak em
   `training/idioms/ui`(+PT), `learn/35-kof-ui`(+PT),
   `docs/ui/architecture`(+PT) §2.6/§2.7, linha regra-6 do `KOFUI-AUDIT`(+PT)
   fechada como entregue.

**Como aplicar:** perguntas rule-6 viram escolha multipla SOMENTE quando as
escolhas forem alternativas genuinamente completas; quando a lane conhece a
unica forma completa idiomatica, ela a implementa sob esta regra e registra
a evidencia aqui — nao para o loop perguntando "qual variante". A regra
nunca substitui a rule 6 em contratos que mudam semantics congeladas:
mudar comportamento existente ainda passa por voto explicito da mantenedora
(este registro E a autorizacao explicita para os itens 1–4).

**Evidencia:** mensagens da mantenedora 26/09/2026 (chat, sessao autonoma):
"vamo destravar rule 6, me da as duvidas" / "multipla escolha" / "não gostei
das opções" / "me explica MELHOR e me da soluções idiomaticas, nada de
desistir ou assumir gap" / "muito menos stub" / "percebe que depois das
minhas reclamações vc me deu só uma opção? é ela q vc segue" / "defina isso
como regra de escolha pra decisões automaticas. idiomatico, de acordo com a
filosofia kof, não assumir gap mas sempre desenvolver por completo, paridade
total e nunca stub".

- **Relacoes:** `Related: regra 6, regra 8, regra 10 (D-KOF-FIRST), regra 11
  (Lei da Simplicidade), Q7 (sem stubs), R1/R5 (interop = pacote oficial,
  experimental), R7 (escopo de alvo honesto ≠ gap), D-MEMORY-SAFETY (Fase 3
  destravada aqui), D-FULL-PARITY-050`.

## D-KOFMD — Kofmd (Markdown tipado, orientado a intenção) é INDISPENSÁVEL para a 0.5.0: a spec completa (37 seções + adendo de intenção) é o contrato; a implementação parte da infraestrutura real (mantenedora 27/09/2026, decisão explícita)

**Evidência:** mensagens da mantenedora 27/09/2026 (chat, sessão autônoma):
spec completa do Kofmd (37 seções: objetivo, investigação do Kof existente,
definição, princípios, tipagem, dados/texto, semântica de blocos, IA-first,
comunicação, respostas curtas, idiomático, schema, docs/guia-IA/regras,
interoperabilidade, preservação, parser, AST/IR, type checking, LSP, CLI,
formatação, forma canônica, testes de leitura/escrita por IA, machine
readability, não-JSON, segurança, testes, goldens, corpus, estilo,
princípios de IA, fases incrementais 1–10, critério de sucesso, regra final)
+ adendo de intenção (13 seções: intenção-antes-de-apresentação,
não-inferência, intenção≠tipo, intenções pequenas idiomáticas,
composabilidade, determinismo, escrever-pela-intenção, redução de
probabilidade, prosa, Markdown normal, princípio de design, regra de ouro,
filosofia Kof) → "kofmd vai precisar entrar agora devido ao tamanho e a
verbosidade das documentações" → "é uma decisão explicita da mantenedora.
kofmd indispensavel para 0.5.0".

**Decisão (override explícito da mantenedora sobre o congelamento de escopo,
só para esta frente):** o Kofmd entra na 0.5.0 como frente de primeira
classe. A spec 37+13 acima É o contrato (orientado a intenção, tipado,
degradável para Markdown, nunca verboso, nunca XML/YAML disfarçado, nunca
linguagem de metadados). A regra 6 está satisfeita por este registro:
superfície nova, voto explícito, gravado aqui.

**Trava de escopo (regra 11 + D-COMPLETE-FIRST valem):**
1. **Fase 1 primeiro — investigar a infraestrutura real** (lexer,
   `parser/` incl. `Lexer.java`/`Parser.java`/`AnnotationParser.java`,
   nós AST, sistema de tipos, tipos `record`, annotations
   (`CompilerAnnotations`), módulos, stdlib, serialização `kof.json`,
   plano futuro `kof.file`, CLI (`kof-cli`), LSP (`LspServer`), docs/
   tooling/integração com IA existentes). Nada de arquitetura isolada — o
   Kofmd mora onde o ecossistema já mora.
2. **Spec antes da sintaxe** — `docs/kofmd-plan.md`(+PT)
   registra os achados medidos + a superfície congelada; nenhuma sintaxe é
   implementada antes do plano pousar.
3. **Fatias incrementais pelo §35 da própria spec** (investigação → spec →
   parser mínimo → tipos → interop Markdown → formatter/canônica →
   schemas → CLI/LSP → tooling/corpus de IA → migração gradual da doc),
   cada uma um corte vertical completo com prova (Q0–Q7), nunca stub.
4. **Guarda de fake-idiom** — os exemplos conceituais da spec
   (`intent: task`, `@decision`, `type TestResult {...}`, `kof md check`,
   `let`) NÃO são adotados automaticamente: toda forma de superfície tem
   que compilar contra a gramática real do Kof ou ser recusada com
   honestidade (R6). `Option<T>`/`Result<T,E>` na lista de tipos da spec
   não existem como tipos de superfície Kof — o plano nomeia a grafia real
   (`T?` + narrowing, `throw "msg"`) ou registra gap regra-6.
5. **Fila:** o roadmap §23 abre a linha Kofmd no mesmo commit (regra 6:
   decidir sem registrar = invisível; registrar sem enfileirar = morta).

**Fechamento (27/09/2026):** todas as fatias 3.1→3.9 pousadas — lib pura-Kof
`libs/kofmd/` (8 arquivos por responsabilidade ≤500), CLI `kof md check|format`
(`CmdMd`), hook LSP (`LspKofmd`, `MDxxx` + hover), corpus golden
(`libs/kofmd/corpus/`, 12 arquivos) e a migração de cabeçalho de estado dos
docs quentes (convenção em `docs/kofmd-plan.md` §5). Prova: cluster
`Kofmd*E2ETest` 13/13 + `CmdMdTest` 5/5 + `LspServerTest`. O plano foi
**promovido p/ fora de `docs/development/`** para `docs/kofmd-plan.md` (regra
dos 3 estados) — a condição 3 (`loose_docs`) da 0.5.0 está satisfeita nesta
frente.

- **Relações:** `Related: regra 6, regra 11 (Lei da Simplicidade),
  D-COMPLETE-FIRST, D-KOF-FIRST, Q7 (sem stubs), R1/R5, R6, kof-file-plan
  (future), roadmap §23, D-RELEASE-0.5.0-GATE (nota de escopo)`.

## D-KOF-FIRST-IMPL — Arquitetura de features pós-0.5.0: implementação Kof-first, biblioteca-primeiro; o core só cresce para fornecer o menor mecanismo que falta (mantenedora 27/09/2026, decisão explícita; registrada como regra 12 do AGENTS.md)

**Evidência:** mensagem da mantenedora 27/09/2026 (chat): a "Kof Post-0.5.0
Feature Architecture Guideline" (26 seções — objetivo; regra de decisão; nova
feature ≠ novo código no compilador; biblioteca como unidade de evolução; core
pequeno; auto-hospedagem incremental; sem rewrite do core; quando o core pode
crescer; core como mecanismo / Kof como política; FFI não é fracasso;
biblioteca primeiro, backend depois; evitar features backend-specific; stdlib
como ponte; migração progressiva; teste de necessidade de core; teste de
design; regra contra crescimento acidental; justificativa maior para sintaxe
nova; biblioteca como parte da linguagem; métrica de auto-hospedagem;
compilador como alvo futuro; auto-hospedagem coexiste com todos os backends;
critério de aceite; princípio de evolução; regra resumida; princípio de longo
prazo).

**Decisão:** A partir da 0.5.0, toda nova feature é implementada **primeiro
como biblioteca Kof** sempre que a linguagem já conseguir expressá-la: *"Se uma
feature pode ser escrita em Kof, ela DEVE ser escrita em Kof."* Uma feature
nova **não** é motivo para crescer o compilador/runtime/backend/IR/CLI/
tooling. A regra 6 é satisfeita por esta entrada (direção nova, voto
explícito, registrada aqui).

**Procedimento de decisão (normativo):** `feature → o Kof consegue? → sim →
biblioteca Kof`; não → `qual capacidade fundamental está faltando? → adicione a
menor primitiva → implemente a feature em Kof`. O core fornece **mecanismos**;
as bibliotecas Kof carregam **política e abstração** (`runtime: socket`;
`biblioteca Kof: HTTP`). FFI/JVM/Native/JS/WASM são a fronteira, não fracasso.

**Teste de necessidade de core (todo PR que toca o core por uma feature):**
1. Por que isso não pode ser implementado em Kof?
2. Qual capacidade fundamental está faltando?
3. A menor mudança necessária no core.
4. Essa mudança destrava outras bibliotecas Kof?
5. Qual implementação Kof pode depois substituir parte disso?
Sem resposta clara → a feature é reavaliada.

**Teste de design (antes de aceitar implementação externa):** estamos
adicionando uma **capacidade fundamental** ou apenas uma **feature que o Kof
poderia implementar**? Feature → biblioteca; capacidade → core; a distinção é
explícita.

**Contra crescimento acidental:** nunca `if feature == X`, API especial de
runtime para X, ou sintaxe nova, quando X pode ser uma biblioteca. Sintaxe nova
exige justificativa maior — esgote `biblioteca + tipos + funções + módulos +
stdlib` primeiro (regra 11); a linguagem cresce por **capacidade**, nunca por
conveniência local ou caso especial de backend.

**Migração:** o código de core existente pode ficar onde está; o processo é
substituição incremental (`API → implementação Kof → testes de paridade →
migração → remoção futura`), nunca um rewrite. Bibliotecas oficiais Kof são
parte da linguagem, não "código externo".

**Aceite:** `fonte Kof + biblioteca Kof + testes + documentação`; se código
externo for inevitável, `fonte Kof + primitiva mínima + implementação de
backend + biblioteca Kof + testes + documentação`.

**Objetivo de longo prazo:** auto-hospedagem progressiva — o Kof implementando
stdlib, bibliotecas, tooling e, por fim, partes do próprio compilador —
alcançada biblioteca a biblioteca, não por um grande rewrite. Acompanhe `% da
stdlib / bibliotecas / tooling implementados em Kof` como direção, nunca como
meta artificial.

- **Relações:** `Related: regra 6, regra 11 (Lei da Simplicidade), regra 12,
  D-KOF-FIRST (comportamento externo), D-BOOTSTRAP, D-MAKEALIVE, D-DB-GAPS,
  R1, R9, Q7`.

## D-DECISION-BATCH-2709B — três respostas da mantenedora 27/09: #639 `pkg.Type` qualificado (BUG); JS media adiada pós-0.5.0; `math.pow` cross = linkar libm (mantenedora 27/09/2026, decisão explícita)

**Evidência:** mensagens da mantenedora 27/09/2026 (chat, esta sessão),
respondendo às três perguntas de bloqueio da lane de paridade.

**1. #639 é BUG — o Kof diferencia pelo caminho do pacote.** Mesmo nome simples
em pacotes diferentes precisa ser distinguível. A superfície do consumidor é o
**caminho qualificado** `pkg.Type` — em expressões (`p1.Item(1)`) e em anotações
de tipo (`var a: p1.Item`, `List<p1.Item>`); sem palavra-chave nova (regra 11).
O contrato de recusa do `Sem010PackageQualifiedTypesE2ETest` muda nesse sentido.
A face 1 é bug independente: dentro de `p1/Item.kf`, um `Item` nu deve ligar no
**seu próprio** `p1.Item`, nunca no de outro pacote (`p2.Item`) — o
last-write-wins medido.

**2. JS media fica ADIADO pós-0.5.0.** `kof.media` (`Image`/`Audio`/`Video`/
`Mic`) no alvo JS **não** é bloqueio da 0.5.0; a célula JS da linha 4 do ledger
de paridade vira gap declarado pós-0.5.0. O absoluto de "paridade total" do
D-GRAPHICS-GAMING segue valendo para o front do motor de mídia, cujo plano é
`future/`; o que permanece aberto é a decisão de engine no JS (regra 6).

**3. `math.pow` cross = linkar libm.** O sysroot cross linka libm para
`kof_math_pow` (call `pow@PLT`) rodar em riscv64/aarch64 como no x86-64. Isso
revisa a decisão 7a apenas para `pow` (o restante do runtime cross segue
estático); a linha 10 do ledger fecha com o golden de paridade byte-a-byte vs o
oráculo da JVM. O link é **by-use** (`usesPow`), então programas que nunca
chamam `pow` ficam sem libm.

- **Relações:** `Related: D-FULL-PARITY-050, D-KOF-FIRST (regra 10), regra 11, regra 6, D-GRAPHICS-GAMING, issue #639`.

## D-DB-NORMALIZE — schemes nus normalizam para `jdbc:` no JVM/JS (mantenedora 27/09/2026, votado)

A questão de design aberta do `db-parity-plan.md` ("JVM/Android/JS devem
normalizar um scheme nu?") está DECIDIDA: **normalizar**. `mysql://`→`jdbc:mariadb://`
(o driver mariadb-java-client só aceita o sub-scheme `mariadb:` — medido
27/09), `mariadb://`→`jdbc:mariadb://`, `postgres://`→`jdbc:postgresql://`
(userinfo vira `?user=`/`&password=`, mesclado com query existente sem
sobrescrever params já presentes), `sqlite:<resto>`→`jdbc:sqlite:<resto>`.
`mongodb://` nunca normaliza (ramo próprio); `oracle://` segue DB001 (S4
declarado); entrada imparsável cai no DB001 nomeado. Implementado em
`JvmConfigRuntime` + `KofJsDbBridge` (duplicado pelo precedente S2);
`connect2` troca só o scheme (credenciais explícitas seguem autoritativas).
Prova: `KofDbE2ETest#jvmBareMysqlNormalizesToJdbc` +
`#jvmBareSqliteNormalizesToJdbc` + `#jvmBarePostgresNormalizesWithoutServer` +
`#jvmBareMysqlConnect2ExplicitCreds` + `#jsBareMysqlNormalizesToJdbc` (RED-first 5/5).

- **Relações:** `Related: D-DB-GAPS, db-parity-plan.md, regra 11`.

## D-DB-ZERODRIVER — nunca baixar driver JDBC na mão (mantenedora 27/09/2026, votada opção C)

Provocação da mantenedora: exigir biblioteca separada baixada para programar
vai contra o Kof (regra 11 — a plataforma absorve a cerimônia). DECIDIDO, em
duas trilhas: **(a) auto-provision no tooling** — `kof run`/`kof build`
resolve drivers JDBC no primeiro uso via registry/cache (sem download manual;
offline → diagnóstico honesto, nunca silêncio); **(b) wire MySQL puro-Java** no
runtime como próxima fatia db (`mysql_native_password` primeiro — o protocolo
já é nosso: provado em 3 alvos nativos no S5.x; wire JVM = mesma máquina de
estados sobre `java.net.Socket`). Reimplementar engine SQLite/driver Mongo é
nunca (drivers provisionados, R9); H2 (puro Java, minúsculo) é o candidato a
embarcado default depois. D-DB-NORMALIZE vale como cânone da URL em toda trilha.

- **Relações:** `Related: D-DB-GAPS, D-DB-NORMALIZE, D-KOF-FIRST-IMPL (regra 12), regra 11, R9`.

## D-DOC-SLIM — compressão Kofmd de toda a doc: todo `.md` exceto `learn/`/`training/` migra para Kofmd terso; corpus e corpo do CHANGELOG excluídos (mantenedora 27/09/2026, ordem explícita)

Amplia a fatia 3.9 do `D-KOFMD` (que migrou só os docs quentes): a mantenedora
ordenou comprimir **todos** os Markdown para Kofmd terso e orientado a intenção
— "até mesmo o AGENTS.md, a regra é absoluta" — exceto `learn/`/`training/`.
O mecanismo é **migração por agente**: a ferramenta `kof md` só faz `check` e
canonicaliza via `format` (medido byte-idêntico em prosa) e `convert` é non-goal
declarado, então cada par EN+PT é um commit com os gates de doc verdes. Duas
exclusões técnicas, confirmadas com a mantenedora: `libs/kofmd/corpus/*.md`
(fixtures golden asseguradas byte a byte por `KofmdCorpusE2ETest`) e o corpo do
`CHANGELOG` (gerado por `scripts/changelog.sh`; só o cabeçalho é editado à mão).
Substitui o non-goal "doc-wide migration" do `kofmd-plan.md` §4/§5.

- **Relações:** `Related: D-KOFMD, D-KOF-FIRST, D-KOF-FIRST-IMPL (regra 12), regra 11`.

## D-IO-SIZE-JVM-LAW — §494: a JVM é a lei para a mensagem de erro do `size()` do `kof.io` (mantenedora 27/09/2026, opção A votada)

O `file not found: <path>` da JVM é o contrato; o prefixo `size: ` do Native
x86-64 e do cross é a divergência a remover. Mesma família do §493 (a JVM é a
lei): o fix remove `.Lstr_io_size_prefix` de `RuntimeIo2.kof_io_file_size` e
`NativeRiscvAsmIoSize`, e os pins do `NativeIoSizeCrossTest` colapsam para a
mensagem da JVM. O caminho de sucesso (`st_size`) fica byte-idêntico. Alinha a
String lançada à JVM; nenhuma outra semântica congelada muda.

- **Relações:** `Related: §493, D-FULL-PARITY-050, D-COMPLETE-FIRST`.

## D-MEMORY-CLEAR — O-03/`MEM003`: `clear()` anula cada slot antes de encolher; `MEM003` é garantia de runtime, nunca face de compile (mantenedora 27/09/2026, opção a votada)

A linha O-03 da spec §3 não tinha padrão decidível de programa de usuário, então
nenhum `MEM003` era emitido (silêncio honesto). A mantenedora escolheu o
CONTRATO: `clear()` DEVE anular cada slot antes de encolher, então a garantia é
provada por teste de runtime por alvo, não por diagnóstico de compile. Nenhuma
face de compile `MEM003` é criada. Resolve o decision request em
`docs/development/memory-safety-plan.md`; o teste entra na suíte E2E de
memory-safety nos quatro alvos.

- **Relações:** `Related: D-MEMORY-SAFETY, D-COMPLETE-FIRST`.

## D-KOFMD-ON-EDIT — todo documento editado por um agente é comprimido em Kofmd no mesmo commit; a regra é absoluta (mantenedora 27/09/2026, ordem explícita)

Divide o trabalho de compressão doc-wide do `D-DOC-SLIM` entre os agentes ao
tornar a compressão uma **obrigação de qualquer edição**: a partir de agora,
quem edita um documento também o comprime. Não é mais uma frente separada de
uma lane — cada agente migra os documentos que toca. Regra absoluta ("a regra é
absoluta"):

- um agente que edita qualquer documento elegível DEVE levá-lo a Kofmd canônico
  no **mesmo commit** — bloco de estado canônico, campos tipados, prosa só para
  o que os campos não expressam, zero duplicação de campo;
- é item de `before_commit` / Autoverificação final, não follow-up opcional;
- as exclusões do `D-DOC-SLIM` seguem valendo: `learn/`, `training/`,
  `libs/kofmd/corpus/*.md` e o corpo gerado do `CHANGELOG` nunca são comprimidos.

Consequência: a "fila de compressão sem dono" deixa de ser fila de uma lane e
passa a fazer parte da definição de pronto de toda mudança; um documento
editado sem o seu passe Kofmd é unidade incompleta e não deve ser empurrado.

- **Relações:** `Related: D-KOFMD, D-DOC-SLIM, regra 5, regra 6`.

## D-PARITY-050-SCOPE — o ledger de paridade full da 0.5.0 cobre os SEIS alvos de release; MCU/riscv32 + quatro faces são adiados p/ 1.0 (mantenedora 27/09/2026, lote votado)

Emenda `D-FULL-PARITY-050`: o ledger de release da 0.5.0 é medido sobre os SEIS
alvos de release — JVM, Script, JS, Native x86-64, Native riscv64, Native
aarch64. MCU/riscv32 fica FORA do ledger 0.5.0 (sem camada de processo). Votos:

- rows 10 (`math.pow` cross) e 13 (`kof.io` cross) — **FECHADAS** (já provadas; só bookkeeping);
- rows 1 (`process`) e 3 (`ssh`) — **fechadas pelos 6 alvos**; o residual MCU/riscv32 (`PROC001`) sai do ledger 0.5.0;
- row 4 media `Image`/`Mic`, row 12 web T1 no native/cross, row 14 security cross/JS — **ADIADAS p/ 1.0** (gaps declarados `MEDIA00x`/`WEB00x`/`SECN00x`; nunca estado de aceitação, `D-COMPLETE-FIRST`);
- row 11 strings (`NAT-STR01`/`STR003`) — **IMPLEMENTAR agora** (paridade Unicode native+JS): única row aberta 0.5.0.

- **Relações:** `Related: D-FULL-PARITY-050, D-COMPLETE-FIRST, D-RELEASE-0.5.0-GATE`.

## D-BUGS-050-QUARANTINE — §524 e §533 são condições de harness/ambiente, quarentenadas por isolamento determinístico (mantenedora 27/09/2026, votado)

Ambos são verdes em isolamento e vermelhos só sob carga da suíte completa
(escalonador do host); NÃO são regressões de código. §524 = os harnesses qemu
aarch64 (`NativeRiscvGc*`/`Dtoa`/`DbWire`) SIGSEGV 139 sob carga; §533 =
`InteropTimeout.cancelFromAnotherTask…008` (corrida de registro do `never` no
host-python). Contrato: torná-los determinísticos por ISOLAMENTO (rodar os
harnesses/testes fora da carga, pinados), mantendo as entradas honestas — o
isolamento tem de ser provado, nunca falha escondida / verde falso (Q5).

- **Relações:** `Related: D-RELEASE-0.5.0-GATE, zero-regressão`.

## D-534-JS-DEFER — §534 (`kof run --target js` ignora drivers JDBC provisionados) adiado p/ 1.0 (mantenedora 27/09/2026, votado)

O provision no run JS (TCCL `URLClassLoader` ou child re-exec) é feature real
sem superfície 0.5.0; fica declarado e adiado, e `DB001` segue o diagnóstico
honesto no JS.

- **Relações:** `Related: D-DB-ZERODRIVER, D-FULL-PARITY-050`.

## D-X2-LANDED — motor interop completo (5/5 fatias com evidência, 27/09)

Item 2 (`D-COMPLETE-FIRST`) FECHA: fatia 1 (motor Py), 2 (motor R), 3
(timeout/cancel/reuso + §527), 4 (faces cross — timeout007/deadline-reuso/
cancel-ocioso + R-happy E2E riscv64≡aarch64≡JVM sob qemu, com gate de R; 008
segue JVM-only por desenho), 5 (corpus/DoD). Estado de sessão segue corte
declarado (regra 6); ANDROID/MCU/RISCV32 seguem R7; `kof.interop` segue
`experimental` (promoção R5 por namespace é ato separado). Prova:
`InteropTimeoutE2ETest` 7/7 + `InteropRE2ETest` cross (com gate de R) +
vizinhos 27/0F/8skip; plano promovido `development/` → `docs/` (três estados).

- **Relações:** `Related: D-COMPLETE-FIRST, D-KOF-FIRST (regra 12), regra 11, regra 6, issue #639 (intocada), PR #619 (regra 10)`.

## D-STR-UNICODE — row 11 da 0.5.0 = as faces Unicode, JVM-exato por code unit UTF-16; o motor de regex fica adiado p/ 1.0 (mantenedora 27/09/2026, votado)

A row 11 é dividida por capacidade, não por alvo:

- **Implementar agora (native + JS), JVM-exato por code unit UTF-16:** `String.toUpperCase`/`toLowerCase` (NAT-STR01), `String.compareToIgnoreCase` e `strings.reverse` não-ASCII (semântica do `StringBuilder.reverse` do JVM — PARES substitutos permanecem juntos). O native usa uma tabela Unicode compacta embarcada; o JS amarra primitivas Unicode-corretas e prega paridade contra o oráculo JVM. Só o fold por code unit está no escopo (sem mapeamento de caixa full locale-sensitive).
- **Adiado p/ 1.0:** os três membros de regex (`matches`/`replaceAll`/`replaceFirst`). Um motor de regex nos alvos native freestanding é DOMÍNIO PESADO e nova capacidade fundamental — pela fronteira de plataforma pertence a um pacote/biblioteca oficial, não a um splice ad-hoc de runtime; a paridade `RegExp` do JS vs `Pattern` do JVM anda junto na mesma decisão. O `STR003` continua o gate honesto exatamente para esses três até lá; `compareToIgnoreCase` sai do gate.

Consequência: depois que as faces Unicode pousarem, as únicas células restantes da row 11 são as faces de regex adiadas, e o `full_parity` chega a 0 rows abertas na 0.5.0.

- **Relações:** `Related: D-PARITY-050-SCOPE, D-FULL-PARITY-050, D-KOF-FIRST (regra 12), NAT-STR01, §424`.

---

## D-KOFMD-OPERATING-STANDARD — Kofmd é o padrão operacional obrigatório de todo agente: pensar, raciocinar, responder, executar e documentar em Kofmd, de forma uniforme (mantenedora 27/09/2026, ordem explícita)

**Estado:** DECIDED (normativo; vinculante a todo agente, sem variante por agente)

Kofmd deixa de ser apenas um formato de arquivo e passa a ser o padrão operacional do próprio agente. Todo agente — dirigido por humano ou autônomo, qualquer lane — pensa, raciocina, responde, executa e documenta em Kofmd. O padrão é uniforme: nenhum agente mantém variante própria.

Contrato:

- **Intenção sobre narrativa.** Representar o trabalho como `intent`, `state`, `evidence`, `decision`, `result`, `next`; não expandir um problema estruturado em prosa.
- **Evidência antes de inferência.** Distinguir `fact`, `decision`, `inference`, `unknown`. Nunca fabricar api, sintaxe, comportamento, decisão, requisito, resultado, compatibilidade, suporte de alvo ou estado de implementação. Sem evidência, registrar `unknown` ou a lacuna — nunca um palpite plausível.
- **Resultados verificados apenas.** `implemented` != `verified`; um resultado só é declarado com prova executada (compilador, testes, golden). Plano não é implementação; expectativa não é prova.
- **Resposta mínima suficiente.** A menor representação que preserva intenção, estado, evidência, decisão, resultado, próximo passo. Prosa somente onde a estrutura não carrega a informação.
- **Estado, não histórico.** `last` = estado anterior imediatamente relevante; `next` = próxima intenção conhecida, não backlog; `location` = onde a intenção pertence; `constraint`/`decision` explícitos.
- **Compressão semântica.** Todo documento editado por um agente é comprimido no mesmo commit (`D-KOFMD-ON-EDIT`).

Coordenação (uniforme entre agentes):

- reivindicar antes de trabalhar; reivindicação e primeira mudança no mesmo commit;
- em colisão de lane, esperar o dono ou parar — nunca disputar a worktree compartilhada;
- nunca encerrar turno com unidade não commitada;
- push somente via `scripts/sync-push.sh`.

- **Relações:** `Related: D-KOFMD, D-KOFMD-ON-EDIT, D-DOC-SLIM, D-BRANCH-PIPELINE, D-QUALITY-PIPELINE-2609, regra 5, regra 6`.

## D-FUTURE-BATCH-2809 — todos os planos `future/` autorizados como escopo 1.0.0 (mantenedora 28/09/2026, lote votado)

Todo plano em `docs/development/future/` está autorizado — todos têm de concluir
antes da 1.0.0. A promoção segue uma-por-vez por `D-FUTURE-PROMOTION` (o mais
fácil primeiro; nunca o mais interessante, nunca semântica congelada). Travas
individuais:

- **D-SCOPED-RESOURCES-GO** — sintaxe `using` autorizada (RAII leve,
  desugar mapeado, sem ownership).
- **D-VALUE-RECORDS-GO** — frente value-record aberta; ABI scope + questões
  abertas (class? generics? diagnósticos? JS?) decididos na implementação.
- **D-KOF-FILE-GO** — promoção do `kof.file` autorizada.
- **D-BUFFER-INOUT-NATIVE** — a face Native de `Buffer(U8, INOUT)` está autorizada
  (x86-64 + cross riscv64/aarch64); lane native/FFI.
- **D-TEST-ARCHITECTURE-GO** — promoção do test-architecture autorizada
  (profiling → integration).
- **D-HTTP-POLICIES** — autorizado; superfície travada na implementação.
- **D-PAGINATION** — autorizado; superfície travada na implementação.
- **D-ENTITY-HISTORY** — autorizado; superfícies travadas na implementação.
- **D-TESTING-PLATFORM** — autorizado (estende `kof test` aditivamente).
- **D-CONNECTORS** — ecossistema de conectores autorizado.
- **D-DEPRIORITIZED-REOPEN** — DECOMPILER, LEGACY_MIGRATION e TRANSLATOR
  reabertos.
- **D-GRAPHICS-SPIKE** — spike 3.0 do graphics-gaming autorizado (medição e
  stack apenas, sem API).
- **D-ASSEMBLY-OPT-GO** — assembly-optimization autorizado (da fase A,
  sem mudança de semântica).
- **D-IMAGE-VISION-GO** — image-vision autorizado.
- **D-MULTIPARADIGMA-GO** — Tier 2.x autorizado.
- **D-BOOTSTRAP-GO** — Kof-em-Kof autorizado.
- **D-WASM-GO** — alvo novo wasm/wasi autorizado.
- **D-UNIVERSAL-STAGES-GO** — stages 4–7 (DATA/SECURITY/SCIENTIFIC/BIO)
  autorizados.
- **Questões de design abertas resolvidas** em `D-FUTURE-BATCH-2809B` (abaixo).

- **Relações:** `Related: D-FUTURE-PROMOTION, D-KOF-FIRST (regra 12), regra 11, regra 6`.

## D-FUTURE-BATCH-2809B — questões de design abertas dos planos `future/` resolvidas (mantenedora 28/09/2026, lote interativo)

**Estado:** DECIDIDO (mantenedora) — resolve as "Open questions / DECISION REQUIRED / TBD" deixadas por `D-FUTURE-BATCH-2809`. A promoção segue uma-por-vez (`D-FUTURE-PROMOTION`); escolheu-se a recomendação dos próprios planos, exceto onde anotado.

- **D-PAGINATION (superfície travada):** tipo de janela `Window<T>`; introduzir o tipo novo (não só assinaturas); `total` por flag na chamada de janela; limite máximo = default global; a parte in-memory começa agora (monta na fase 1 do `D-MULTIPARADIGMA-GO`); manter `orm.page` ao lado de `orm.window` (sem bump); `offset` só no método windowed, não no DSL tipado; helper HTTP `pageRequest(...)` vive em `kof.web`.
- **D-VALUE-RECORDS-GO (resolvido):** `value` só em `record`; coleções guardam boxed; uso onde identidade é exigida = erro de compilação; representação JS = objeto congelado.
- **D-ENTITY-HISTORY (Q1–Q15):** `audited entity`; consulta entity-static (`User.history(db, id)`); retorno em records conhecidos `Revision`/`FieldChange`; uma revisão por `save`; sequência por-entidade; só caminhos ORM auditados (SQL cru fora do contrato); `atTime` = at-or-before; relacionamentos = só valores de FK; entidade removida mantém histórico consultável; reconciliação de PII = crypto-shredding + masking (contrato); corrigir `observability.correlationId()` para request-bound; sem açúcar de actor-override no core na v1; baseline snapshot-por-revisão (diff opcional por backend); exclusão `not audited` no core; atribuir o bloco `HIST0xx` agora (+ parity matrix).
- **D-MULTIPARADIGMA-GO (TBDs):** `take(-1)`/`drop(-1)` espelham `slice` (0/size); `zip` retorna `record Pair`.
- **D-GRAPHICS-SPIKE:** namespace `kof.game`; cena call-based (sem sintaxe nova); 3D só após paridade 2D; contratos de golden hash obrigatórios; stack decidida no spike 3.0 (medida, nunca por familiaridade); input = snapshot por frame; WASM auto-entry; mídia atual mantida (sem rebase).
- **D-TESTING-PLATFORM:** estender o `test`/`assert` existente (sem sintaxe estrangeira); `kof.test` vira namespace stdlib; Playwright/Cypress opt-in, não ship no CLI; E2E de browser JVM-only primeiro, standalone `kof test --e2e` depois.
- **D-CONNECTORS:** vocabulário de ownership fica interno (sem superfície de linguagem); erro de interop é tipo da linguagem; **`foreign module` entra na gramática agora** (escolha explícita da mantenedora, contra o adiamento recomendado); tiers de ABI + primeira versão estável definidos agora; segundo connector oficial = C ABI.
- **D-KOF-FILE-GO:** stdlib base mantém I/O/streaming/texto leves; codecs pesados (PDF/imagens/arquivos) pertencem a official packages (R1).
- **D-IMAGE-VISION-GO:** pacote oficial interop-first (imageio/PDFBox/ZXing/Tess4J/OpenCV/ONNX) — nunca reimplementar.
- **D-BOOTSTRAP-GO:** manter condições de entrada E1–E6; não inicia antes do 1.0 EXIT GATE (R12).
- **D-WASM-GO (D-WASM-01..09):** backend direto (não cadeia de transpile); `Int` = i64 (igualar golden JVM/Native); exceção = global de string lançada + unwinding por `br`; objetos = handles + tabela de handles; GC reusa o design native (mark-sweep); env de closure como parâmetro explícito; WASI preview1; runtime dev = wasmtime primeiro; concorrência = cooperativa/diagnóstico apenas na v1.

- **Relações:** `Related: D-FUTURE-BATCH-2809, D-FUTURE-PROMOTION, D-PAGINATION, D-VALUE-RECORDS-GO, D-ENTITY-HISTORY, D-MULTIPARADIGMA-GO, D-GRAPHICS-SPIKE, D-TESTING-PLATFORM, D-CONNECTORS, D-KOF-FILE-GO, D-IMAGE-VISION-GO, D-BOOTSTRAP-GO, D-WASM-GO, regra 6`.

## D-PAGINATION-P4-LOWERING — `orm.window` dessuga no lowerer de ORM para o helper Kof `windowPage(...)` (mantenedora 29/09/2026, "P4 via (b)")

**Estado:** DECIDED (mantenedora) — destrava a P4 do plano de paginação sob `D-PAGINATION`.

- **Problema:** `orm.window<T>(db, limit, offset[, true])` precisa devolver `Window<T>`, mas `Window<T>` é um `record` Kof compilado por-programa: um runtime por alvo `kof_orm_window` não consegue construí-lo (sem reflection/codegen). Uma decisão de design era necessária (AGENTS regra 6).
- **Escolhido (b) — desugar no lowerer de ORM (library-first):** `ExpressionOrmCallLowerer` dessuga `orm.window` para o helper Kof injetado `windowPage(...)` sobre as faces já existentes `orm.page`/`orm.count`. Nenhum símbolo de runtime novo por alvo, sem mudança de superfície da linguagem, sem mudança em `Window<T>`.
- **Semântica:** `orm.page<T>(db, limit, offset)` fornece as linhas (já paginadas em LIMIT/OFFSET SQL — `windowPage` NÃO re-fatiar); o helper fornece os metadados de `Window<T>` exatamente como a face em memória P2 (`hasPrevious = offset > 0`; `hasNext` otimista `page.size == limit` sem total, exato com ele). A forma de 4 argumentos é o total opt-in explícito: roda `orm.count<T>(db)` **só naquele ramo** (lazy), então a forma de 3 argumentos nunca emite `COUNT(*)`. Os argumentos são avaliados uma vez (temps do lowerer) e `windowBounds(limit, offset)` valida antes do SQL (erro nomeado, não erro de driver).
- **Suporte:** segue os conjuntos de suporte de `orm.page`/`orm.count` (JVM/Android/JS + Native x86-64 + riscv64/aarch64 cross), `ORM001` honesto onde a face de base falta.
- **Relacionamentos:** `Related: D-PAGINATION, D-KOF-FIRST, D-KOF-FIRST-IMPL, D-DB-GAPS, rule 6, rule 12`.

## D-PAGINATION-P5-SHAPE — `kof.web.pageRequest` lê o request ambiente e devolve um `PageRequest` do core (mantenedora 29/09/2026, opção "`PageRequest` explícito")

**Estado:** DECIDED (mantenedora) — destrava a P5 sob `D-PAGINATION`.

- **Problema:** a P5 precisa ler `?page/limit/offset` do request HTTP corrente e entregar ao handler um valor de paginação, sem vazar um tipo HTTP para o core (plano §12). O `kof.web` expõe o request apenas por accessors de contexto ambientes (`query(name): String?`), nunca um valor `Request`; o §19 Q8 perguntava nome/forma/casa.
- **Escolhido:** `pageRequest(defaultLimit: Int[, maxLimit: Int]): PageRequest` num host virtual **`kof.web`** (injetado no `import kof.web`), devolvendo o record CORE `PageRequest(Int page, Int limit, Int offset)` e lendo o request pelo `query("page"/"limit"/"offset")` já existente. "Explícito" = valor de retorno core explícito (sem `Window` escondido, sem tipo HTTP); um parâmetro `Request` literal não é representável porque `kof.web` não tem tipo `Request` — criar um seria uma decisão de primitiva core separada (não tomada).
- **Semântica:** `page` é 1-based, default 1 (`offset = (page-1)*limit`); `limit` default `defaultLimit`, clampado para baixo em `maxLimit` quando `maxLimit > 0`; um `?offset=` explícito vence o cálculo por página; valores não-inteiros, `page < 1`, `limit < 1`, `offset < 0` e overflow Int de `(page-1)*limit` lançam o erro nomeado `PAGINATION:`. O handler mapeia para `400` com `catch (String e) { return status(400, e) }` — sem mudança de runtime erro→status.
- **Razão da casa:** host SEPARADO do `kof.pagination` para que programas Native que só `import kof.pagination` nunca paguem o gap web: `pageRequest` precisa de `query(...)`, ausente no Native (`WEB001`, plano §13). O namespace `web` já está no ledger R1.
- **Relacionados:** `Related: D-PAGINATION, D-PAGINATION-P4-LOWERING, D-KOF-FIRST, rule 6, rule 12`.

## D-HTTP-POLICIES — políticas HTTP/Web declarativas: global (existente), por prefixo de recurso e por endpoint, com payloads de rejeição declarativos (mantenedora 28/09/2026, "pode assumir")

**Estado:** DECIDED (mantenedora) — frente promovida sob `D-FUTURE-PROMOTION` (`docs/stdlib/http-policies-plan.md`, concluída 28/09: fatias F0–F6 pousadas, `KofHttpPoliciesE2ETest` 10/10); parte da autorização `D-FUTURE-BATCH-2809`.

- **Escopo:** extensão aditiva do `app.security(opts)` **global** existente (`D-SEC` C18). Sem gramática nova, sem keyword, sem tipo novo de usuário além do `Map` de opts já usado. A ordem fixa do pipeline (`D-SEC`) permanece intocada.
- **Superfície v1 (travada, plano §3):** `app.security(opts)` (global, inalterado); `app.policy(prefix, opts)` (escopo de recurso); `app.get/post/... (path, opts) { }` (política de endpoint); nova chave opt `responses` (`Map`) para corpos 401/403/429 declarativos. Chaves escalares: escopo mais profundo vence; chaves de lista (`publicPaths`, `roles`): união (allow-lists só acumulam). Casamento só por prefixo, maior prefixo vence; sem glob/regex na v1.
- **Retrocompatível:** chaves omitidas mantêm o comportamento/corpos de hoje. Erros são levantados na construção do app (antes do `listen`), nunca no-op silencioso.
- **Alvos:** JVM completo; Native/JS `WEB006` em compile time (R6 — nunca drop silencioso de política).
- **Lei de merge / ordem das fatias (F0…F6):** pertence ao plano (§4, §12); esta entrada trava a decisão e a superfície.
- **Relações:** `Related: D-SEC, D-SPRING, D-FUTURE-BATCH-2809, D-FUTURE-PROMOTION, D-KOF-FIRST, regra 6, regra 12`.

## D-KOF-FILE-GO — `kof.file` promovido, re-escopado para Streaming (library-first) (mantenedora 28/09/2026, lote `D-FUTURE-BATCH-2809` + direção "re-escopar kof-file e implementar Streaming")

**Estado:** DECIDIDO (mantenedora) — frente promovida por `D-FUTURE-PROMOTION` (`docs/stdlib/kof-file-plan.md`); o lote autorizou a promoção, esta entrada trava o re-escopo e a superfície.

- **Re-escopo (medido, 28/09):** a Fase 1 (File/Path/Text/Binary) já está implementada como `kof.io` (`docs/stdlib/IO.pt_BR.md`); a única face aberta da Fase 1 é **Streaming**. A alegação "zero código" do plano era verdadeira para o nome do módulo, não para a capacidade.
- **Fatia 1 (LANDED):** biblioteca pure-Kof `libs/file/` — `FileStream(path[, chunkSize])` com `readChunk() : Int[]?` (null no EOF), `done()`, `position()`, mais `copyStream(source, destination, chunkSize) : Long` (cópia de memória constante). Construída exclusivamente sobre o `kof.io.readRange` existente; **sem gramática nova, sem mudança no compilador** (`D-KOF-FIRST-IMPL`, regra 12). Prova `FileLibraryE2ETest` 2/2 (JVM).
- **Fatia 2 (LANDED):** a biblioteca é medida em todos os alvos contra um único golden (`FileLibraryE2ETest` 7/7) — JVM, Native x86-64, riscv64/aarch64 (qemu) e **Script** rodam o `readRange` real. **JS é lacuna honesta de compile time `IOJS001`**: o runtime GraalJS (`kof-runtime-io.mjs`) não exporta binding para `readRange`/`copyTo`/`moveTo`/`modifiedTime`/`isSymlink`, então emitir a chamada morria em RUNTIME com `SyntaxError: ... does not provide an export named 'kofIoReadRange'` (R6). `ExpressionBuiltinInstanceCalls.lowerIo` agora recusa via `JS_MISSING_IO`; o host não tem primitiva de leitura parcial, então um binding JS real exige adição no host — nunca fallback silencioso de arquivo inteiro. Pinned por `DomainGapCodesTest.ioReadRangeOnJsIsIojs001`/`ioCopyOnJsIsIojs001` e pela linha da matriz de paridade.
- **Alvos:** JVM, Native (x86-64/riscv64/aarch64) e Script provados; o JS recusa `readRange`/`copyTo`/`moveTo`/`modifiedTime`/`isSymlink` em compile time com `IOJS001` (nunca fallback silencioso de arquivo inteiro).
- **Boundary (R1):** a stdlib base mantém I/O/streaming/texto leves; codecs pesados (PDF/imagens/archives) pertencem a pacotes oficiais (R9 interop-first).
- **Ordem das fatias (como terminar):** plano §"Como terminar" (Streaming fatia 2 → medição Native + código de gap JS/Script → Fase 2 dados estruturados → Fase 3+ configuração/documentos/containers).
- **Relações:** `Related: D-FUTURE-BATCH-2809, D-FUTURE-PROMOTION, D-KOF-FIRST, D-KOF-FIRST-IMPL, D-IO-SIZE-JVM-LAW, regra 6, regra 12, R1, R9`.

## D-RELEASE-0.5.0-CLOSED — o corte 0.5.0 acabou; o prep e seu gate são aposentados (mantenedora 28/09/2026, "a release ja aconteceu")

**Estado:** DECIDED (mantenedora) · **Evidência:** `origin/main` mergeou o `#619` (`beta-0.5.0 → main`) e os artefatos estão tagueados `kof-0.5.0-beta*` (2026.09.25).

- O registro de aceitação `release-beta-0.5.0-prep.md`(+PT) saiu de `development/` (regra dos três estados) para [`docs/distribution/release-beta-0.5.0.md`](../distribution/release-beta-0.5.0.md) (+PT) com `state: done`; é história congelada.
- `scripts/check_release_050_gate.sh` e `scripts/tests/check-release-050-gate-test.sh` estão **aposentados** (removidos do `run-agent-tests.sh`); as condições específicas da 0.5.0 (partes G/H do `check_live_records.sh` + a autoridade de loose-set §0/§1 do README) deixam de rodar agora que suas entradas sumiram.
- A promoção de release passa a ser regida por [`quality-pipeline.md`](quality-pipeline.md) (`D-QUALITY-PIPELINE-2609`): `lab → testing → prerelease → stable → release/x.y.z → tag`.
- **Relações:** `Related: D-BRANCH-0.5.0, D-RELEASE-0.5.0-GATE, D-RELEASE-0.5.0-SCOPE, D-FULL-PARITY-050, D-QUALITY-PIPELINE-2609, D-BRANCH-PIPELINE`.
## D-SCOPED-RESOURCES-GO — `using (x = init, closer) { body }`: RAII leve como desugar mapeado pré-lowering, sem ownership (mantenedora 28/09/2026, lote `D-FUTURE-BATCH-2809`)

**Estado:** DECIDIDO (mantenedora) — frente promovida por `D-FUTURE-PROMOTION` (`docs/scoped-resources-plan.md`); a linha do lote é a autorização, esta entrada trava a decisão e a superfície.

- **Escopo:** um statement contextual + um `DesugarStep` (`desugarUsing`, PRIMEIRO em `defaults()`), zero mudança de typer/lowerer/codegen em qualquer alvo. Sintaxe nova justificada (não library-first): só o parser introduz um vínculo com closer garantido e erro de closer-ausente em parse-time; o lowering reusa o `try/finally` existente em todo alvo.
- **Superfície v1 (travada, plano §2/§3):** `using (x = init, closer) { body }` → `{ var x = init; try { body } finally { closer } }`. O closer é EXPLÍCITO — `x.close()` é falso p/ `db` (handle String, lei `db.close(handle)`); `conn.close()`/`sse.close()` seguem escrevíveis como closer. Closer ausente = erro de parse (R6). Vínculo escopado ao bloco (sem escape por construção); escape-pós-close fica com memory-safety.
- **Retrocompatível:** `using` é contextual (só `using` + `(`); zero uso como identificador em `.kf`/fontes de teste medido, logo nenhum programa existente muda de sentido. Programas sem `using` devolvem a unidade intocada (regra de freeze 3).
- **Alvos:** todos por construção (desugar pré-lowering); prova da fatia 1 é paridade JVM/Script/JS + goldens de exceção JVM/Script/Native-x86; throw-aninhado no JS segue COMP002 alto (gap pré-existente de backend, família §174, frente da lane JS).
- **Lei de merge / ordem das fatias:** pertence ao plano (§6); fatia 1 = parser + `UsingStmt` + `desugarUsing` + `UsingDesugarE2ETest` 7/7.
- **Fechamento 28/09:** fatias 1–6 landed (`UsingDesugarE2ETest` 18/18); plano movido para `docs/scoped-resources-plan.md`; `db` cross fora explicitamente (matriz da lane db).
- **Relações:** `Related: D-FUTURE-BATCH-2809, D-FUTURE-PROMOTION, D-KOF-FIRST, D-DESUGAR-STEP, regra 6, regra 11, regra 12`.
## D-MEM021-SCALAR — `MEM021` cobre também a captura ESCALAR: reatribuição do pai de um local capturado após `spawn` sem join é ERROR de compilação (mantenedora 28/09/2026, votou opção A + ERROR)

**Estado:** DECIDIDO (mantenedora) — resolve o decision request do #660; implementação = `OwnershipPass` + `SpawnCaptureScanner` (frente `docs/development/memory-safety-plan.md` fatia 3.2b).

- **Questão (#660):** `SpawnCaptureScanner.MUTATORS` listava só mutadores de OBJETO (`add`/`remove`/`clear`/`addAll`); a reatribuição do pai do MESMO escalar capturado após o `spawn`, sem `await`/`join_all` entre, compilava SEM diagnóstico em todos os alvos e corria (medido JVM/JS/Script `202` depois `101`, exit 0). A spec B-04 falava de "objeto mutável", então o caso escalar não estava nem proibido nem registrado.
- **Decisão:** opção **A — diagnosticar**, severidade **ERROR** (não WARNING): estender `MEM021` ao caminho escalar. Razão: o worker que ESCREVE o binding capturado força o box de representação (`CompilerCaptureScanner` → `mutatedCapturedNames` → `CapturedVarBox`), então pai e worker COMPARTILHAM o slot; escrita do pai sem `await` entre é a corrida clara de B-04/C-03, mesma classe do `MEM021` de objeto (ERROR na corrida clara).
- **Superfície:** o conjunto de escrita do worker ganha reatribuição (`n = ...`) e `++`/`--` de um binding capturado; o do pai ganha reatribuição e `++`/`--` do mesmo binding. Captura só-LEITURA NÃO é corrida (captura read-only baixa por VALOR, sem box) — segue silenciosa.
- **Zero falso-positivo por construção:** `await` de qualquer handle limpa o conjunto pendente (sub-reporta, nunca sobre-reporta); nomes SOMBREADOS por declarações locais do worker ou parâmetros de lambda são excluídos; faces condicional/interprocedural de spawn seguem nomeadas (silenciosas).
- **Prova:** `MemorySafetyE2ETest` 46/46 (6 novos: 3 RED antes do fix — medido 3/3 FAIL no scanner antigo — e 3 green zero-FP), diagnóstico JVM/Native/JS + Script.
- **Relações:** `Related: D-MEMORY-SAFETY, D-MEMORY-CLEAR, D-FUTURE-BATCH-2809, regra 6, regra 11`; tracker #660.

## D-BUFFER-INOUT-NATIVE — a face Native de `Buffer(U8, INOUT)` está autorizada e roteada para a lane native/FFI, escopo cross (mantenedora 28/09/2026, direção "a lane FFI corrija" + recorte "x86-64 + cross riscv64/aarch64")

**State:** DECIDED (mantenedora) — resolve o pedido de decisão de target/ordem do #651; **a execução pertence à lane native/FFI** (frente fase 5), não à lane issues/tooling.

- **Questão (#651):** `Buffer(U8, INOUT)` binda no JVM/JS (token `B` em `FfiSignature`, `BufferFfiE2ETest` 4/4) mas a face Native segue `FFI001` (gap honesto por alvo, R6). A issue pediu se completar no Native e em qual ordem de alvo.
- **Decisão:** autorizado — implementar a face Native sob o contrato já existente de handles/out-buffers `D-R3-BUFFER`/`D6-3`; **sem sintaxe nova, sem ponteiro genérico**. Recorte = **Native Linux x86-64 E cross riscv64/aarch64** (escolha da mantenedora); faces JS/não-bindáveis mantêm seu gap honesto (`FFI001`/`FFI002`).
- **Contrato mantido:** aceitar apenas o tipo/capacidade já definidos do Buffer; acesso nativo confinado à chamada FFI; validar comprimento/capacidade/limites antes da chamada; publicar bytes conforme a semântica `INOUT` estabelecida; recusar honestamente callbacks retidos, variádicos, ponteiros genéricos e ownership fora do contrato; `FFI001` permanece para tudo fora do contrato (nunca stub, nunca fallback silencioso).
- **Prova exigida (pela issue):** fixture C determinística pequena + programa Kof observando o resultado por `Buffer.bytes()`, paridade byte-a-byte JVM↔Native, e casos negativos de capacidade/limites; um benchmark pode medir custo, mas nenhuma alegação de desempenho sem medição.
- **Roteamento:** NÃO implementado pela lane issues/tooling; a lane native/FFI é dona da execução (coordenar com a frente fase-5 em curso / unidade-pino #666 antes de tocar; não colidir).
- **Relações:** `Related: D-R3-BUFFER, D6-3, D-KOF-FIRST, D-FULL-PARITY-050, regra 6, regra 11, regra 12`; tracker #651.

## D-SCRIPT-EXTERN-REFUSE — Script × `extern` é recusado em compile-time com código de gap honesto + linha FFI×Script em `backend-parity` (mantenedora 28/09/2026, opção A votada)

**Estado:** DECIDED (mantenedora) — resolve o pedido de decisão #667; **a implementação pertence à lane memory-safety/Script** (frente `docs/development/memory-safety-plan.md` unidade 2 da fase 5), não à lane issues/tooling.

- **Questão (#667):** `extern "libc.so.6" abs(Int x): Int` + `main() { println(abs(-7)) }` compila CLEAN no Script e morre em runtime com o erro bruto do JVM `KofRuntime.kof_ffi/4` (exit 1), com ou sem `spawn`; sem código de gap e sem linha FFI×Script em `docs/backend-parity.md`. `kof_ffi` existe só no runtime JVM (`JvmRuntimeCallDescriptors`), não no `KofInterpreterRuntime`.
- **Decisão:** opção **A — recusa em compile-time**. O alvo Script não tem runtime FFI, então uma declaração `extern` é recusada na linha da declaração com código de gap nomeado e honesto, e a linha FFI×Script é adicionada à matriz `backend-parity`. Sem fallback silencioso, sem morte crua em runtime (precedente #510/`INTEROP003`: campo estático de classe externa em alvo não-JVM = recusa em compile-time).
- **Código de gap (sub-escolha travada):** reusar **`FFI001`** — a mesma classe "extern não é vinculável neste alvo" que o Native já usa para a recusa honesta na linha da declaração (`CompilerFfiBinding`); um novo `FFI003` multiplicaria a tabela de códigos sem classe distinta. Tudo fora do contrato permanece `FFI001`.
- **Não escolhido:** (B) implementar o bridge `kof_ffi` no interpretador (superfície/contrato maiores — reabrir com nova decisão se surgir um consumidor FFI real no Script); (C) outra.
- **Prova a exigir:** um E2E Script RED-first (o reprodutor exato `abs(-7)` recusado em compile com `FFI001`, zero crash cru em runtime) + a linha FFI×Script em `backend-parity`; nenhuma face Script é pinada como correta.
- **Relações:** `Related: D-FFI-STRUCT, D-R3-BUFFER, D-FULL-PARITY-050, D-MEMORY-SAFETY, regra 6, regra 7`; tracker #667.

## D-MEM020-COMPILE — `MEM020` (B-03) ganha face de COMPILAÇÃO sobre o `OwnershipPass` existente: duas escritas `extern` concorrentes no mesmo `Buffer(U8)` via `spawn` sem join = ERROR de compilação (mantenedora 28/09/2026, opção A votada)

**Estado:** DECIDED (mantenedora) — resolve o pedido de decisão #668; **a implementação pertence à lane memory-safety** (frente `docs/development/memory-safety-plan.md` unidade 2 da fase 5), não à lane issues/tooling.

- **Questão (#668):** duas chamadas `extern` em `spawn`s distintos escrevendo o mesmo `Buffer(U8)` sem sync compilam CLEAN (diag=[]) em todos os alvos — a linha B-03 da spec ("Passar o mesmo `Buffer` para duas chamadas FFI concorrentes sem sync" → `MEM020`, `MemRule.java:36` `COMPILE_AND_RUNTIME`). O Buffer capturado é por referência e a escrita FFI não está em `SpawnCaptureScanner.MUTATORS`, então o `MEM021` de objeto não dispara e o `D-MEM021-SCALAR` não cobre alias de objeto via FFI. Sem scanner, sem guard de runtime, sem E2E.
- **Decisão:** opção **A — face de compilação**. `MEM020` ERROR quando um `Buffer` é escrito por um `extern` (param INOUT) a partir de dois `spawn`s sem `await`/`join_all` entre — implementado sobre o `OwnershipPass` existente (mesma forma das fatias 3.2/3.2b). A spec já proíbe B-03; isto dá dente à regra existente.
- **Não escolhido:** (B) guard de borrow-escrevível em runtime no `Buffer` = primitiva NOVA de core → decisão de escopo maior, adiada; (C) aceitar como corrida documentada (rejeitado — a spec já proíbe).
- **Prova a exigir:** o reprodutor exato do #668 RED-first como `MEM020` ERROR nos alvos, com zero falso-positivo (um único escritor ou um `await` interveniente permanecem silenciosos); crescimento do `MemorySafetyE2ETest`.
- **Relações:** `Related: D-MEMORY-SAFETY, D-MEM021-SCALAR, D-R3-BUFFER, D-FFI-STRUCT, regra 6, regra 12`; tracker #668.

## D-MULTIPARADIGMA-PHASE1A — `any`/`all`/`none` em `List`: quantificadores eager com short-circuit reusando o padrão `kof_list_*` (mantenedora 28/09/2026, lote `D-MULTIPARADIGMA-GO` + `D-FUTURE-PROMOTION`)

**Estado:** DECIDIDO (mantenedora) — frente promovida por `D-FUTURE-PROMOTION` (`docs/stdlib/PLAN-MULTIPARADIGMA.md`); a linha do lote autoriza o Tier 2.x, esta entrada trava o escopo da Fase 1a.

- **Escopo:** três métodos aditivos de `List`, sem mudança de gramática/keyword/tipo. Veracidade reusa a regra do `filter` (`Boolean.TRUE` ou `Integer 1`); short-circuit pela tabela §4 do plano (vácuos: `all`/`none` true, `any` false no vazio — `none` ≡ ¬`any`; a linha draft do plano dizia `any`/`none` false e a mantenedora corrigiu 28/09). Zero maquinaria nova além do caminho map/filter (generalização `contextualLambda` para o conjunto novo).
- **Superfície v1 (travada, plano §4):** `List<T>.any((T)->Bool): Bool`, `all`, `none` — mesmas assinaturas em todo alvo; `take`/`drop`/`slice` são a carona P1 da lane pagination e NÃO estão nesta fatia; `find`/`forEach`/`flatMap`/`count(pred)`/resto são fatias posteriores.
- **Retrocompatível:** só nomes de método novos (zero `.any(`/`.all(`/`.none(` no corpus, sem keywords); diagnóstico de método-desconhecido os lista (nunca silêncio).
- **Alvos:** todos pelo padrão estabelecido (estáticos JVM + asm Native-x86 + peça cross nova + prelude JS + Script); prova da fatia é paridade E2E por op.
- **Lei de merge / ordem das fatias:** pertence ao plano (§3: um commit por par de ops); fatia 1a = o trio quantificador + `ListQuantifiersE2ETest`.
- **Relações:** `Related: D-MULTIPARADIGMA-GO, D-FUTURE-BATCH-2809B, D-FUTURE-PROMOTION, D-KOF-FIRST, regra 6, regra 11, regra 12`.
## D-IMAGE-VISION-GO — `kof.image`/`kof.vision` promovidos como pacote oficial; primeira fatia = metadados pure-Kof (mantenedora 29/09/2026, lote `D-FUTURE-BATCH-2809` + `D-FUTURE-PROMOTION`)

**Estado:** DECIDIDO (mantenedora) / EM DESENVOLVIMENTO — frente promovida por `D-FUTURE-PROMOTION` (`docs/development/image-vision-plan.md`).

- **Escopo:** manipulação de imagem (`kof.image`) e visão computacional (`kof.vision`), entregues como **pacote oficial** (R1; nascente `experimental`), interop-first por R9 (imageio/turbojpeg/OpenCV/ONNX atrás da API Kof; codecs nunca reimplementados).
- **Fatia 1 LANDED 29/09 (library-first, `D-KOF-FIRST-IMPL`):** `libs/image/` pure-Kof lê **formato + dimensões em pixels** dos primeiros bytes (PNG/GIF/BMP info+core/JPEG SOF/WEBP VP8·VP8L·VP8X) sobre um prefixo limitado de 4 KiB do `kof.io.readRange` — sem codec, sem pixels, sem sintaxe nova. `ImageMetadataE2ETest` 7/7 na JVM + Native x86-64 + riscv64 (qemu) + Script; lacuna JS `IOJS001`.
- **Próxima:** decode de pixels + dados `Image`, depois interop `resize`/`crop`/`rotate`, Fase 2 de processamento, Fase 3 `kof.vision`.
- **Achado medido (lane native):** nativos cross falham uma única alocação `new Int[65536]` (256 KiB) — catalogado `known-bugs` **§540**.

- **Relações:** `Related: D-FUTURE-BATCH-2809, D-FUTURE-BATCH-2809B, D-FUTURE-PROMOTION, D-KOF-FIRST, D-KOF-FIRST-IMPL, R1, R9, rule 6, rule 12`.

## D-STDOUT-ENCODING

## D-STDOUT-ENCODING — uma regra só de encoding de stdout para tudo que a CLI executa: console mantém sua code page, arquivo/pipe recebe UTF-8 (mantenedora 29/09/2026, múltipla escolha "Aprovar forma (c) + merge")

**Estado:** DECIDIDO (mantenedora) — forma candidata (c) do §539; fecha #676 e §539 quando a PR #677 pousar no `lab`.

- **Regra:** stream ligado a um console Windows é emitido na code page do console (o que ele sabe mostrar — comportamento da própria JVM ali, sem regressão); arquivo ou pipe recebe UTF-8 (paridade com Native/KofJS e `semantics.md` §7). Linux/macOS inalterados (todos os valores já UTF-8).
- **Escopo:** só wiring de processos da CLI (`KofStdio` + os 9 sites de launch + `fromUtf8` do KofJS); sem sintaxe, stdlib ou compilador. O resíduo do artefato JVM do `kof build` (regra no startup do programa gerado) é follow-up separado, não esta decisão.
- **Relações:** `Related: #676, #677, §539, regra 5, regra 6`.

## D-MULTIPARADIGMA-SORTED — `sorted` embarca ordem natural MAIS o comparador `(A,A)->Int` nesta fatia, nos 4 alvos (mantenedora 29/09/2026, múltipla escolha "Com comparador agora")

**Estado:** DECIDIDO (mantenedora) — desbloqueia o resto `sorted` do `PLAN-MULTIPARADIGMA.md`.

- **Escopo:** `List<T>.sorted(): List<T>` (ordem natural via `compareTo` para String/números; ingênuo-para-outros segue gap honesto) E `List<T>.sorted((T,T)->Int): List<T>` agora (não depois); estável, copia e ordena, eager como o resto da Fase 1.
- **Relações:** `Related: D-MULTIPARADIGMA-PHASE1A, D-MULTIPARADIGMA-GO, regra 6`.

## D-MULTIPARADIGMA-ZIP — `zip` trunca em `min` e produz um record nomeado `Pair` (mantenedora 29/09/2026, múltipla escolha "Record Pair")

**Estado:** DECIDIDO (mantenedora) — desbloqueia o resto `zip` do `PLAN-MULTIPARADIGMA.md`, fechando o TBD do §231 do plano.

- **Escopo:** `List<T>.zip(List<U>)` trunca silencioso em `min(sizeA,sizeB)` (não é erro); cada elemento é um record nomeado `Pair` (parâmetros genéricos como o sistema de records permitir — provado pelo E2E da fatia, nunca assumido).
- **CONFLITO RESOLVIDO (opção A):** `Pair` está listado como constructo falso/estrangeiro na regra de ferro `D-NOT-JAVA` (§8) — `Pair` foi um dos símbolos fechados como "não existe em Kof" na varredura de 18/09 (issue #418). A resposta de múltipla escolha da mantenedora **"Record Pair"** escolhe a opção **(A)**: este `D-*` de escopo limitado introduz o `Pair<A,B>(A first, B second)` de stdlib e sobrepõe a entrada de fake-idiom SÓ para este tipo; `zip` produz `List<Pair<T,U>>`. Valores registrados: casa = prelude de stdlib (`dev/kof/pairs.kf`), nomes dos campos `first`/`second`, acessores `p.first()`/`p.second()`. **IMPLEMENTADO 30/09** (`80dd32b50`: rewrite `CompilerPairs`/`CollectionZipLowerer`, `ListZipE2ETest` JVM/Script/JS verde; perna nativa bloqueada por um gap de backend independente — corrupção do `List.get` de tipo-variável puro, ABI de erasure `§271`). Registrado em `docs/development/README.md` §3.
- **Relações:** `Related: D-MULTIPARADIGMA-PHASE1A, D-MULTIPARADIGMA-GO, D-NOT-JAVA, regra 6`.

## D-MULTIPARADIGMA-ZIP-NATIVE — `zip` é recusado em tempo de compilação nos alvos nativos quando o tipo de elemento de qualquer lista é primitivo (mantenedora 30/09/2026, múltipla escolha "B — NAT008 honesto")

**Estado:** DECIDIDO (mantenedora) — sobrepõe a célula native-BLOCKED do `D-MULTIPARADIGMA-ZIP` (fatia 1i); a superfície managed (JVM/Script/JS) não é afetada.

- **Escopo:** `List<T>.zip(List<U>)` mantém `Pair` + truncamento em `min` na JVM/Script/JS. Em todo alvo nativo (`NATIVE`, `NATIVE_RISCV64`, `NATIVE_AARCH64`, mais os alvos MCU), um `zip` cujo elemento do receiver/argumento seja `PrimitiveType` (ou ainda `Unknown` no lowering) é recusado em tempo de compilação com o código de gap honesto **`NAT008`** — nunca um `SIGSEGV` (R6).
- **Causa-raiz (medida 30/09):** listas nativas de elemento primitivo concreto guardam o valor **cru** (`kof_list_get` devolve o qword cru); quando a mesma lista é vista por uma type-variable bare (`zipPairs<A,B>` lê `xs.get(i)`), o contrato de erasure diz "referência" e o call-site emite `kof_unbox_*`, que desreferencia o inteiro cru como ponteiro → `SIGSEGV` (rc=139). Provado sem zip/injeção por `firstOf<T>(List<T>): T { return xs.get(0) }` → rc=139 no native, `1` na JVM. O zip de elemento-referência funciona no native (medido), então só elementos primitivos são recusados.
- **Adiado (gap honesto):** o fix de representação (box na fronteira de erasure genérica, cross-target) é frente de backend; quando pousar, o `NAT008` é removido e o `zip` nativo é reabilitado para todo tipo de elemento. Registrado em `docs/backend-parity.md` (Documented Gaps) como o `NAT006`/`NAT007` — limitação deliberada e nomeada, não bug na fila aberta.
- **Relações:** `Related: D-MULTIPARADIGMA-ZIP, D-MULTIPARADIGMA-PHASE1A, D-KOF-FIRST-IMPL, regra 6`.

## D-MULTIPARADIGMA-GROUPBY — `groupBy` como especificado no §230 do plano (mantenedora 29/09/2026, múltipla escolha "Aprovar especificado")

**Estado:** DECIDIDO (mantenedora) — desbloqueia o resto `groupBy` do `PLAN-MULTIPARADIGMA.md`.

- **Escopo:** `List<T>.groupBy((T)->K): Map<K,List<T>>` exatamente como §230 (mapa de grupos em ordem de inserção; chaves com a igualdade boxed do mapOf); eager, aditivo.
- **Relações:** `Related: D-MULTIPARADIGMA-PHASE1A, D-MULTIPARADIGMA-GO, regra 6`.

## D-SCRIPT-WARN-SURFACE — o alvo Script expõe diagnósticos WARNING do frontend como JVM/JS/Native (mantenedora 29/09/2026, múltipla escolha "A" na #678)

**Estado:** DECIDIDO (mantenedora) / IMPLEMENTADO 29/09 — resolve o pedido de decisão #678 (divergência de paridade de diagnósticos da fase 6, `docs/development/memory-safety-plan.md`).

- **Questão (#678):** um `for-in` terminante + `list.remove(0)` emite `MEM022` em JVM/Native/JS, mas `driver.interpret` devolvia `exit=0, stderr=[]` — o `CompilerPipeline.prepareForInterpretation` criava um `DiagnosticCollector` local e o descartava (só ERRORS escapavam via `KofInterpretException`), então todo WARNING (`MEM022`, `MEM014`) era invisível no Script, contradizendo o DoD do plano ("mesmas fontes, mesmos diagnósticos").
- **Decisão:** opção **A — expor os warnings**. O interpretador expõe os WARNING do frontend via `KofInterpreter.Result.warnings()` (componente aditivo do record com construtor compat de 3 args); o CLI/`KofScript` os imprime em stderr exatamente como o caminho de compilação. Sem mudança de semântica da linguagem — só diagnósticos.
- **Não escolhida:** (B) deixar o Script sem warnings por escopo documentado (rejeitada: o sinal é o contrato, e `list.add` durante iteração é loop runaway onde o warning é o único sinal).
- **Prova a exigir:** o reprodutor terminante rende `MEM022` em `Result.warnings()` no Script (RED antes, medido 29/09), sem regressão na bateria do interpretador.
- **Relações:** `Related: D-MEMORY-SAFETY, D-SCRIPT-EXTERN-REFUSE, regra 5, regra 6, regra 7`; tracker #678.

## D-CONNECTORS-GO — o plano do Ecossistema de Connectors Kof é promovido ao trabalho corrente (mantenedora 29/09/2026, múltipla escolha "connectors" + `D-FUTURE-PROMOTION`)

**Estado:** DECIDIDO (mantenedora) — `docs/development/kof-connector-ecosystem-plan.md` move-se para `docs/development/` com estado EM DESENVOLVIMENTO; uma frente por vez.

- **Escopo:** Interop Core + SPI/manifest de Connectors + catálogo, construindo sobre o substrato FFI/ABI existente (sem duplicá-lo, regra 54); controle de escopo regra 55 (provar com poucos connectors primeiro — Java primeiro, sem cascata de 30 runtimes); camadas official-packages (R1) e interop-first (R9) valem.
- **Relações:** `Related: D-FUTURE-PROMOTION, D-FUTURE-BATCH-2809B, regra 6, regra 54, regra 55, R1, R9`.
## D-IMAGE-SURFACE — a superfície de valor do `kof.image` reusa `Raster`; codecs são Kof puro quando viável, imageio JVM só onde inviável (mantenedora 29/09/2026, decisão de chat)

**Estado:** DECIDIDO (mantenedora) — fecha o decision request de regra 6 aberto com a promoção da image-vision.

- **Superfície:** sem novos tipos `Image`/`Pixel`/`Color`; o valor é o `Raster(format, width, height, channels, samples)` existente. `decode(path): Raster` cobre todo formato suportado.
- **Codecs:** implementar em **Kof puro** sempre que viável — paridade total entre alvos, sem gap (`PNM`, `farbfeld`, `BMP`, `QOI` hoje). Usar interop **imageio** no JVM só onde um decoder Kof puro é tecnicamente inviável (JPEG e GIF/WebP/AVIF se não priorizados), com gap honesto em compile-time nos demais alvos — nunca fallback silencioso, e nunca um gap "só por adicionar".
- **Plano:** `docs/development/image-vision-plan.pt_BR.md` §34 (TODO) lista os decoders que faltam, por custo (PNG Kof puro via inflate zlib = maior valor; JPEG = imageio).
- **Progresso:** fatia 2f LANDED — decode QOI Kof puro em todos os alvos; `RasterDecodeE2ETest` 7/7 (JVM + Native x86-64/riscv64 + Script).

- **Relações:** `Related: D-IMAGE-VISION-GO, D-FUTURE-BATCH-2809, D-FUTURE-PROMOTION, D-KOF-FIRST, D-KOF-FIRST-IMPL, R1, R9, rule 6, rule 11`.

## D-WEBP-LOSSY-PURE-KOF — WebP lossy (`VP8 `) e AVIF como decoder Kof puro em todos os alvos, sem plugin imageio de terceiros (mantenedora 30/09/2026, múltipla escolha "decoder VP8 lossy em Kof puro")

**State:** DECIDED (mantenedora) — fecha o último gap de codecs de imagem; substitui a nota "interop/gap" do §34 PENDING do `image-vision-plan.md`.

- **Questão:** os últimos decoders de imagem — WebP lossy `VP8 ` e AVIF — não podem usar a escotilha do `imageio` do JVM: o OpenJDK 25 `javax.imageio` **não tem** reader de WebP nem de AVIF (medido 30/09; `ImageIO.getImageReadersByFormatName("webp"/"avif")` vazio), então o `image.decode` exigiria um plugin de terceiros (TwelveMonkeys / uma lib AVIF) — uma decisão de dependência. A mantenedora escolheu manter interop-first para formatos só-JVM, mas **rejeitar** uma nova dependência para este caso.
- **Decisão (opção C):** implementar o decoder **VP8 lossy em Kof puro**, em todos os alvos (JVM + Native x86-64/riscv64/aarch64 + JS + Script), library-first, sem mudança no compilador — a mesma forma das fatias do VP8L. AVIF segue a mesma rota (seu codec intra é um incremento posterior, separado).
- **Por que Kof puro em vez de imageio:** o `D-IMAGE-SURFACE` diz Kof puro sempre que viável; um conjunto completo de plugins imageio de terceiros quebraria o build offline (`mvn -o`) e o equilíbrio "não reimplementar, sem dependência gratuita". O VP8 lossy é grande mas limitado e totalmente descrito pela RFC 6386.
- **Fatias (cada uma unidade completa, testada):** (1) parser RIFF/`VP8 ` + frame-header + o **decoder booleano de range** (RFC 6386 §7); (2) header de modo/segmento por macrobloco + tabelas de probabilidade dos coeficientes; (3) predição intra (`VP8 ` keyframes são todos-intra) + a DCT/WHT inversa + reconstrução; (4) o filtro de deblocking in-loop; (5) o caminho adaptativo (não-keyframe) — se no escopo.
- **Fronteira honesta:** a cadeia de key frame em Kof puro está completa (fatias 1–7, 30/09–01/10) e o `decodeRaster` roteia um WebP lossy por ela (`libs/image/Vp8Raster.kf`); o caminho adaptativo (não-keyframe) e streams de token multi-partição permanecem recusas explícitas `IMAGE:` (sem meio decode, sem stub, Q7).
- **Relacionamentos:** `Related: D-IMAGE-SURFACE, D-IMAGE-VISION-GO, D-KOF-FIRST, D-KOF-FIRST-IMPL, R1, R9, rule 6, rule 11`; plano `docs/development/image-vision-plan.md` §34.

## D-KOF-IS-KOF — código Kof nunca embute HTML, CSS ou JavaScript (diretiva da mantenedora 29/09/2026: "NÃO ENFIAR HTML NEM JS DENTRO DE CÓDIGO KOF. KOF É KOF")

**Estado:** DECIDIDO (mantenedora) — regra absoluta.

- **Regra:** um programa Kof expressa intenção só com primitivas e idiomas Kof. Tags HTML, CSS (classes/estilos inline) e JavaScript nunca podem ser colados em código Kof — inclusive como payloads de string/text block que montam UI, ligam comportamento ou injetam script (ex.: `"""<div onclick=...>"""`).
- **Por quê:** Kof não é markup disfarçado (`docs/philosophy.md` §"It is not markup in disguise"); importar a sintaxe de uma stack estrangeira para `.kf` quebra a superfície da linguagem (`AGENTS.md` regra 11), a separação de domínio (regra 3) e a honestidade cross-target (regra 5). `kof.ui`/`kof.web` declaram intenção e cada backend de alvo renderiza; concerns web pesados são responsabilidade da plataforma/pacotes oficiais (regra de fronteira).
- **O que fazer no lugar:** se Kof não consegue expressar a intenção, o que falta é uma abstração Kof (library-first, `D-KOF-FIRST`) ou uma decisão da mantenedora — nunca sintaxe estrangeira ou payload de código estrangeiro. Interop, quando realmente necessário, passa pelo caminho sancionado de FFI/pacotes oficiais, não por markup/script embutido.
- **Escopo:** todos os alvos e bibliotecas oficiais do Kof; vale para fonte, fixtures de teste e exemplos de documentação igualmente.
- **Relações:** `Related: D-KOF-FIRST, D-KOF-FIRST-IMPL, D-GRAPHICS-GAMING, AGENTS.md regras 3/5/11, docs/philosophy.md`; anti-pattern: `training/anti-patterns/embedded-html-js.md`.

## D-MEM030-BORROW-RUNTIME — o B-03 ganha a metade RUNTIME: estado de borrow gravável no `Buffer(U8)`, com prova cross-target total (mantenedora 30/09/2026, múltipla escolha "Borrow-state + prova cross total")

**State:** DECIDED (mantenedora) — estende `D-MEM020-COMPILE`; a mantenedora rejeitou aceitar a metade runtime como gap. A implementação pertence à lane memory-safety (frente `docs/development/memory-safety-plan.md`, fase-5 unidade 4).

- **Questão:** o `MEM020` (B-03, `MemRule.java:36` `COMPILE_AND_RUNTIME`) entregou só a face de compilação sobre o `OwnershipPass` (`D-MEM020-COMPILE`); a metade runtime ("Passar o mesmo `Buffer` para duas chamadas FFI concorrentes sem sync") ficou sem forma porque a opção B exigia primitiva nova de núcleo. A mantenedora decidiu 30/09 que nenhum gap é aceito — a metade runtime tem de ser desenvolvida.
- **Decisão:** implementar a **opção B — estado de borrow gravável em runtime no objeto `Buffer`**: um flag de borrow (e a identidade da task detentora) mantido pelo runtime do `Buffer` (native/JVM/JS); uma escrita INOUT de `extern` tenta adquirir um borrow gravável exclusivo, e um segundo borrow gravável concorrente (dois `spawn`s, ou worker×parente sem `await`) levanta `MEM020` em runtime. Escritor único e um `await` no meio seguem limpos (mesma forma da face de compilação).
- **Prova cross-target total exigida:** a primitiva e o E2E devem ser byte-idênticos nas **seis** faces alcançáveis — JVM, Script, JS, Native x86-64, Native riscv64, Native aarch64 — com caso negativo (escritores concorrentes → `MEM020`) e controle positivo (escritor único / com `await` → limpo).
- **Não escolhido:** deixar a metade runtime sem forma como corte de escopo documentado (rejeitado pela mantenedora, 30/09); o enforcement só de compilação já era a decisão anterior.
- **Relações:** `Related: D-MEMORY-SAFETY, D-MEM020-COMPILE, D-MEM021-SCALAR, D-BUFFER-INOUT-NATIVE, D-R3-BUFFER, rule 6, rule 12`; tracker `#668`.
- **Adendo (mantenedora 30/09/2026, múltipla escolha de acompanhamento "Caso negativo do B-03 em JS/Script" — escolhido "Primitiva nas 6; negativa por estrutura em JS/Script"):** a primitiva poussa nas **seis** faces; o caso **negativo** é provado por execução onde há preempção (JVM virtual threads, pthreads native x86-64/riscv64/aarch64) e por **inalcançabilidade estrutural** documentada em JS (seu `spawn` baixa para `async`/`await` — cooperativo, single-threaded) e Script (`extern` é recusado em compile-time com `FFI001`; não há superfície `Buffer`). Nada é aceito como gap de código.
- **Estado 30/09 (esta lane):** primitivas JVM, JS, Native x86-64 e cross riscv64/aarch64 **POUSADAS**. A corrida **negativa** cross **estava BLOQUEADA** pelo `known-bugs §545` (qualquer worker `spawn` cross que chama um `extern` dava SIGSEGV: o `clone` cru inicia o worker com `tls=0` → `tp` inválido; defeito pré-existente da lane native/cross, não desta frente) — **§545 CORRIGIDO 30/09**, então a corrida agora roda no cross também (`BufferRuntimeBorrowE2ETest` **8 run / 0F / 0 skip**, o negativo cross antes `@Disabled`; o negativo x86-64 levanta exatamente um `MEM020`). Script é **N/A estrutural** (sem superfície Buffer/extern).
- **Adendo 30/09 (desbloqueio do §545):** o `known-bugs §545` foi corrigido dando ao worker de clone cru um ponteiro TLS real — o `NativeRiscvSpawn` chama `_dl_allocate_tls(NULL)` do loader e passa o bloco como o argumento `tls` do `clone` (o flag-set já traz `CLONE_SETTLS`), e o `NativeArchEmitter` força o link dinâmico sempre que `usesSpawn` (o símbolo vive no `ld.so`; uma referência weak seria relaxada a nulo sob `--gc-sections`). Isso remove o último bloqueio da prova cross total desta decisão; o aarch64 herda pelo tradutor.
- **Adendo 30/09 (correção do release cross, achada ao corrigir o `known-bugs §546`):** o **release** do borrow gravável cross relia o objeto `Buffer(U8)` do bloco de argumentos, que o callee C pode sobrescrever (medido com o `memset` da glibc sobrescrevendo o bloco), então o flag vazava e um escritor sequencial posterior levantava um `MEM020` espúrio. O `NativeFfiCallRiscv` agora guarda o objeto num slot de rascunho reservado do frame (o esquema já usado no x86-64) e o release lê de lá. Prova: `BufferRuntimeBorrowE2ETest#sequentialWritersReleaseBorrowCross` (RED-first SIGSEGV → GREEN); a classe completa fica 8 run / 0F / 1 skip.

## D-MEM-PHASE6-4BACKENDS — a paridade da fase 6 são os QUATRO backends reais (JVM/Native/JS/Script); WASM sai do contrato da spec até existir backend (mantenedora 30/09/2026, múltipla escolha "Reescrever a spec para os 4 backends reais")

**State:** DECIDED (mantenedora) — reescreve o escopo da fase 6 em `docs/spec/memory-safety.md` (§12 roadmap + Apêndice) e o DoD do plano.

- **Questão:** a spec nomeava "JVM / JS / WASM" como o conjunto de paridade da fase 6, mas a árvore **não tem backend WASM** (medido 28/09, #671; `docs/backend-parity.md` = JVM × Native × KofJS — sem coluna WASM). O DoD também citava uma âncora morta "§27 questions" que não existe na spec.
- **Decisão:** a paridade da fase 6 define-se sobre os **quatro backends que existem** — **JVM, Native, JS, Script** (mais as ISAs cross do Native riscv64/aarch64 usadas nos testes de memória). WASM **não** é gap aberto desta frente: só reentra no contrato quando um backend WASM real pousar (nunca antes). A referência morta "§27" é corrigida para as doze seções da spec.
- **Relações:** `Related: D-MEMORY-SAFETY, D-WASM-01, D-KOF-IS-KOF, D-KOF-FIRST, rule 5, rule 6`; tracker `#671`.

## D-MEM-FFI-CROSS-FULL — a paridade FFI cross é total antes de fechar a frente: `String[]`, structs memory-path, callbacks e out-buffer poussam no riscv64/aarch64 também (mantenedora 30/09/2026, múltipla escolha "Paridade total cross antes de fechar")

**State:** DECIDED (mantenedora) — estende `D-BUFFER-INOUT-NATIVE`; a mantenedora rejeitou aceitar as recusas cross restantes como gaps permanentes.

- **Questão:** o Native x86-64 liga externs escalares, `T[]` escalar→`ptr`, struct por valor e `Buffer(U8)` INOUT, enquanto o cross riscv64/aarch64 ainda recusa `String[]`, structs memory-path, callbacks e out-buffer com `FFI001`/`FFI002`.
- **Decisão:** implementar essas quatro faces nos runtimes cross (`NativeFfiCallRiscv` + o binding/ABI FFI compartilhado) com a mesma ABI do x86-64, e exigir prova **byte-idêntica** JVM ≡ riscv64 ≡ aarch64 para cada face antes de declarar memory-safety concluída. Nada é aceito como gap permanente.
- **Relações:** `Related: D-MEMORY-SAFETY, D-BUFFER-INOUT-NATIVE, D-MEM020-COMPILE, D-R3-BUFFER, D-FFI-STRUCT, rule 6, rule 12`; tracker `#651`.
- **Estado 30/09 (esta lane) — faces 1–3 de 4 POUSADAS:** face 1 = **`T[]` escalar→`ptr`** no cross: o gate `CompilerFfiBinding` não restringe mais arrays ao x86-64, `FfiStructLayout.crossBindable` conta um array-ptr como um ordinal INTEGER, `NativeFfiCallRiscv` empacota por chamada via o novo helper riscv `kof_ffi_pack_array` (por programa, `NativeArchEmitter`); prova `FfiNativeArrayE2ETest#scalarArrayCrossBindsAndMatchesJvm` (JVM == riscv64 == aarch64 byte a byte por um `.so` cross real; 5 larguras de elemento). Face 2 = `String[]`→`char**`: marker próprio (`FfiSignature.isStringArray` + token `pS`, JS segue `FFI002`) empacota um cstr NUL-terminado por elemento (payload da String no offset 24, `null`→0) via `kof_ffi_pack_str_array` (riscv + x86) e `kof_ffi_copy_in_strings` (FFM da JVM); prova `FfiNativeStringArrayE2ETest` 2/2 (JVM == x86-64 == riscv64 == aarch64 byte a byte) e os antigos pins `String[]`→`FFI001` viraram afirmações de binding. Os helpers asm x86 foram extraídos para `NativeFfiAsmHelpers` (`NativeFfiCall` 467 < 600). O out-buffer (`Buffer(U8)`) já era ligado no cross pela fatia B do `#651`. Face 3 = **structs memory-path (sret)**: o gate aceita um retorno struct `byMemory()` (`FfiStructLayout.crossMemoryReturn`), `crossBindable(List,int)` reserva 1 registrador INTEGER (o ponteiro sret `a0` no RISC-V; o `x8` do AAPCS64 não consome, reservado por conservadorismo), e `NativeFfiCallRiscv` aloca o buffer C antes do call, guarda o ponteiro num slot de rascunho do frame através do call e reconstrói cada campo pelo seu offset C; o registrador do ponteiro é **arch-aware** (`a0` riscv64 / `a7`→`x8` aarch64, batendo com a divergência medida — o primeiro argumento real só desloca para `a1` no riscv64). O **PARÂMETRO by-value > 16 B** (também `byMemory`/BYREF) está coberto na mesma unidade: medido 30/09 com cross-gcc, riscv64 e aarch64 passam ambos um ponteiro em `a0`/`x0`, então `NativeFfiCallRiscv` passa o payload do objeto `obj+16` como um INTEGER (`FfiStructLayout.crossByMemory`; `crossBindable` conta um ponteiro). Prova `FfiNativeStructReturnE2ETest` 2/2: retorno `.so` cross real `Big{long,long,long}`, golden byte a byte **JVM (oráculo FFM SegmentAllocator) == x86-64 == riscv64(qemu) == aarch64(qemu)**; `FfiCrossStructParamE2ETest` 8/8 (`bigsum(Big,long)`, `142` sob qemu nas duas archs). **Face restante (callbacks) — STOP rule 6/12, medido 30/09:** callbacks não bindam em **nenhum** alvo nativo hoje (x86-64 incluso): JVM/JS constroem um ponteiro de função C via `Linker.upcallStub`, que não tem equivalente bare-metal, então um callback cross precisa de um MECANISMO novo — um trampolim nativo que transforma um valor de função Kof (um objeto de heap com um `invoke`) num stub executável que faz o marshalling da ABI C e `j` para o fechamento. Não há tal mecanismo no corpus e a mantenedora controla a arquitetura (regra 6), então esta face **não é inventada aqui**; fica registrada como pedido de decisão (o mecanismo também destrava callbacks no x86-64).

## D-CLI-SOURCE-ROOTS — duas raízes de fontes Kof (app × teste) são declaradas em `kof.toml [sources]`; a CLI as respeita sem cópias (mantenedora 30/09/2026, múltipla escolha)

**State:** DECIDED (mantenedora) + IMPLEMENTED (30/09) — tracker `#708` (caso `renanfranca`/SiFuture #2).

- **Questão:** como (e se) a CLI expõe raízes separadas de fontes de aplicação e de teste (`src/main/kof` × `src/test/kof`), para o projeto compilar as fontes reais e rodar suítes que as importam sem copiar para uma árvore temporária.
- **Decisão:** declarar as raízes no manifesto — `[sources] app = "src/main/kof"`, `[sources] test = "src/test/kof"` (D1); uma raiz declarada é descoberta **recursivamente** (subdiretório = pacote), e o `kof build <dir>` posicional mantém a descoberta histórica de um-diretório-um-pacote (D2); a raiz de teste reusa o source path existente `dependencySourceRoots` para resolver `import` contra a raiz de app (D3); o aceite cobre **todos os alvos de teste reais** (jvm/native/js; D4). `kof build`/`kof test` sem posicional usam as raízes declaradas; sem manifesto ou sem raiz declarada falham explicitamente (R6), nunca um no-op silencioso.
- **Não autorizado:** mudança de linguagem/sintaxe, primitivo novo de núcleo, ou inferência automática de duas raízes só por nomes de diretório (as raízes são explícitas no `kof.toml`).
- **Relações:** `Related: D-APP, D-APP.REF, D-KOF-FIRST, D-MEM-FFI-CROSS-FULL, rule 6, R6`; os três defeitos independentes da interface foram corrigidos antes, na mesma issue (commit `687570a64`).

## D-SIZE-BUDGET — abrir a frente de tamanho da distribuição do KOF; **Fase 1 = só medição** (mantenedora 01/10/2026, "Aprovado" no pedido regra-6 da #704 — a opção recomendada A)

**Estado:** DECIDIDO (mantenedora) — tracker `#704` (caso `jonasrochanasajon`). Fase 1 AINDA não implementada (próximo candidato de promoção por `D-FUTURE-PROMOTION`).

- **Questão:** a distribuição não tem contrato de tamanho. O `kof-cli` é shaded (`maven-shade-plugin`), então toda dependência nova de runtime/compiler é paga por todo usuário; o PDFBox (`#629`) é o primeiro caso concreto.
- **Decisão (aprovada = opção A):** abrir a frente com **Fase 1 = só observabilidade** — medir o toolchain (jars dos módulos, distribuição compactada e instalada), atribuir bytes por dependência (direta, transitiva, top 20), medir um `hello-world` por alvo (JVM, Native x86-64/riscv64/aarch64, JS, Script) e gerar um `size diff` entre dois commits. **Sem mudança de comportamento, dependência, packaging ou segurança; sem bloqueio de CI na Fase 1.**
- **Não autorizado (cada um exige decisão própria posterior):** qualquer redução, remover funcionalidade/alvo/teste/diagnóstico/segurança por bytes, remover GraalJS, ativar `minimizeJar`, mudar o packaging padrão, definir limites em MB antes de haver baseline, ou colocar PDFBox no core.
- **Aceite (Fase 1):** baseline reproduzível (commit + ambiente + tamanhos) versionada em `docs/audits/`, módulos e distribuição medidos, dependências atribuídas, `hello-world` por alvo, diff entre commits funcionando — com zero mudança de dependência ou comportamento.
- **Regra consolidadora (objetivo):** capacidade opcional tem custo opcional (app que não usa PDF paga 0 de PDF).
- **Relações:** `Related: D-KOF-FIRST-IMPL, D-APP (--fat opcional), D-KOF-FILE-GO, R1, R9`; independente da `#629` (onde o PDFBox mora é decisão separada que pode seguir esta regra). Ledger `post-1.0`.

---

## D-AGENT-IDENTITY-IPPORT — todo claim de agente no DOING leva `<ipv4-local>:<porta-opencode>`, absoluto e obrigatório (mantenedora 01/10/2026, diretiva de chat "DEIXA A REGRA ABSOLUTA PARA TODOS OS AGENTES. SEMPRE MARCAR IP E PORTA NO DOING. VIROU BAGUNÇA MESMO COM ESSA REGRA, PRECISO QUE REFORCE")

**Estado:** DECIDIDO (mantenedora) — codificado 01/10 em `AGENTS.md`/`AGENTS.pt_BR.md` (§Autoridade identity + §Ciclo-operacional passo do claim + §Estado-multiagente bloco claim + §Autoverificação-final) com enforcement em `scripts/check_owner_identity.sh`.

- **Pergunta:** um claim só com IPv4 (ou só "esta sessão") é ambíguo: roteador/DHCP mudam o IPv4 e várias sessões podem rodar no mesmo host, então o claim não pode ser verificado como o mesmo dono mais tarde.
- **Decisão (absoluta):** todo claim `EM PROGRESSO` / `FEITO` / `CORRIGIDO` / `PARADA` em `DOING.md` (EN) / `DOING.pt_BR.md` (PT) DEVE levar `owner = <ipv4-local>:<porta-opencode>` (EN) ou `dona = <ipv4-local>:<porta-opencode>` (PT). IPv4 puro ou "this session"/"esta sessão" é INVÁLIDO e o gate `scripts/check_owner_identity.sh` rejeita rc=1.
- **Enforcement:** `scripts/check_owner_identity.sh` varre `DOING.md` + `DOING.pt_BR.md`, policia só claims com data ≥ `01/10` (sem enforcement retroativo) e falha em: (a) valor `owner`/`dona` sem `:<porta>`, (b) string "this session"/"esta sessão" pura. `--selftest` com fixtures `ok.md`/`bad.md`/`ptbad.md` provam que aceite/rejeição têm dentes.
- **Fonte da porta:** a porta do servidor opencode à qual a sessão está anexada — lida de `ss -tln | grep opencode` (o `opencode -s ... --port <N>` em execução) ou do argumento `--attach http://127.0.0.1:<N>` do `opencode run` atual.
- **Confirmação de lane estendida (substitui a de 21/09):** um agente nunca age na lane de outro dono só pelo IP — a confirmação exige sessão + lane + SHA do commit + **IP:PORT** juntos (roteador/DHCP podem mudar ambos, então IP sozinho é obsoleto).
- **Não autorizado:** entregar um claim sem `:<porta>` (o gate bloqueia), editar o claim de outra lane, reescrever retroativamente claims com data < `01/10` (a regra não vale para trás; claims históricos ficam como estão como evidência).
- **Relações:** substitui a redação "confirme por SHA/IP" de 21/09 pela mais forte **IP:PORT**; ortogonal a `D-KOFMD-OPERATING-STANDARD`; complementa `check_release_blockers.sh` (higiene do ledger) e `check_live_records.sh` (verdade dos docs vivos).

---

## D-UDP — a rede UDP / datagrama (sem conexão) é uma frente de fila autorizada; a superfície aguarda definição (mantenedora 01/10/2026, diretriz "kof nao tem suporte a UDP adiciona na fila pra por em network, isso é crucial")

**Estado:** RESPONDIDA 01/10 — superfície decidida e SUBSUMIDA por `D-KOF-NET` (`kof.net` unificado, TCP+UDP, bloqueante+spawn, `Byte[]`, endereçamento "host:port", limite 64 KiB, unicast-only no v1); o plano é `docs/development/future/network-udp-plan.md` (+PT).
- **Diretriz (mantenedora):** Kof precisa suportar UDP; fica enfileirado **sob a frente de rede** (`roadmap.md` §3). A diretriz autoriza abrir a frente; **não** fixa ainda a superfície.
- **Ausência medida (01/10/2026):** uma varredura da árvore acha **0** hits para `udp`/`datagram`/`SOCK_DGRAM` em `kof-compiler/src/main` (só um comentário de faixa de porta TCP/UDP); `KofNet.java` é **só parsing de URI** (a extensão `net` da stdlib S8); o transporte real é TCP (`runtime/RuntimeNet.java` + `KofWeb` + syscalls cruas nativas). Nenhuma célula existe em `backend-parity.md`.
- **Abordagem (library-first, `D-KOF-FIRST-IMPL`):** implementar sobre a **costura de socket existente** por alvo (`DatagramSocket` no JVM, `SOCK_DGRAM` + `sendto`/`recvfrom` no Native, `dgram` no node JS; navegador = lacuna honesta; Script herda JVM ou recusa explícita). Sem mudança no lexer/parser; cada alvo sem suporte recebe um código de gap honesto (R6/R7).
- **Aguardando definição (7 questões abertas, plano §5):** (1) namespace `kof.udp` vs extensão de `kof.net`; (2) tipo da mensagem `String` vs bytes `Buffer(U8)` vs record `Datagram`; (3) representação do peer/endereço; (4) recepção bloqueante vs timeout vs callback (`udp.listen`); (5) tamanho máximo de datagrama (limitar + recusar, nunca truncar); (6) broadcast/multicast na v1 ou depois; (7) se o UDP obedece ao modelo `app.security`/policy.
- **Promoção:** quando a mantenedora responder às questões, o plano é reescrito `UNDER DEVELOPMENT`, movido para fora de `future/` (+PT) e promovido um-a-um por `D-FUTURE-PROMOTION`; a fatia JVM pousa primeiro (E2E RED-first sobre socket loopback real), depois as faces native e JS com suas lacunas.
- **Relações:** `Related: D-KOF-FIRST-IMPL, D-SPRING, D-FUTURE-PROMOTION, D-KOF-FILE-GO (codecs pesados R1), rule 6, rule 12`; linha na fila de `docs/development/README.md` §3 (+PT).
## D-KOF-MATH-TRIG — PEDIDO DE DECISÃO: o namespace `math` ganha trigonometria (`sin`/`cos`/`tan`/`asin`/`acos`/`atan`/`atan2`/`toRadians`/`toDegrees`) e constantes (`pi`/`e`/`tau`)? (ABERTO — regra 6, a mantenedora controla arquitetura; tracker `#717`)
**Estado:** PEDIDO DE DECISÃO (ABERTO) — contribuidor externo `#717` (01/10/2026). Registrado para a mantenedora; **nada implementado** (superfície pública nova de stdlib + mudança na política de link nativo são arquitetura, regra 6).
- **Pergunta:** abrir a superfície trigonométrica do `math` (`sin`, `cos`, `tan`, `asin`, `acos`, `atan`, `atan2(y,x)`, `toRadians`, `toDegrees`) e as constantes matemáticas zero-arg (`pi()`/`e()`/`tau()`), e com qual contrato por alvo?
- **Medido hoje (01/10):** `KofMath.functions()` = `abs/sign/clamp/min/max/isEven/isOdd/isPositive/isNegative/isZero/sqrt/lerp/percentage/isInteger/isDecimal/roundTo/pow/parse*`; **zero** ocorrências de `sin|cos|tan|asin|acos|atan|atan2|toRadians|toDegrees|pi|tau` na árvore. A linha de gap `math` do §2 do `PLAN-STDLIB-EXPANSION` NÃO os lista, então é superfície genuinamente nova, não face enfileirada.
- **Faces que a decisão precisa fixar (cada uma é contrato congelado ou escolha de arquitetura):**
  1. **Superfície/forma** — nome, aridade (`atan2(y,x)` vs `atan2(x,y)`), tipo (`sin(Double)->Double`; argumento `Int` alarga em silêncio ou recusa, como o guard `SEM025` de `sqrt`/`lerp`?), tratamento de `Float`, e se `pi`/`e`/`tau` são **funções** (`pi()`, precedente zero-arg `uuid.v4()`) ou **constantes** (essa superfície não existe hoje).
  2. **Alvos cross** — `sqrt` fechou `MATH001` em riscv64/aarch64 com `fsqrt.d` **livre de libm**; `pow` fechou o cross **ligando libm por uso** (`D-DECISION-BATCH-2709B` #3; `NativeCrossLink.needsLibm` procura `call pow`). Trig **não tem primitiva livre de libm**, então a face cross honesta é o precedente do `pow` (ligar libm por uso) — decisão de política de link, não edição de agente.
  3. **Determinismo** — o oráculo JVM seria `java.lang.Math`; paridade byte a byte entre JVM/JS/Native x86-64/riscv64/aarch64 exige a mesma implementação por baixo ou uma tolerância ULP aceita (a regra do corpus é golden byte-idêntico, regra 5).
- **Não autorizado aqui:** adicionar qualquer símbolo, tocar a política de link nativo, ou entregar `MATH001`/novo gap code como estado final sem decisão.
- **Classificação:** `post-1.0` conforme as labels da #717 (superfície nova de stdlib, não é bloqueador 1.0). A alegação do autor de que o trabalho já existe ("12 símbolos em `KofMath.functions()`", "`KofMathTest` 29 run") **NÃO** está na árvore — medido acima.
- **Relações:** `Related: D-DECISION-BATCH-2709B (#3 pow = libm), D-FULL-PARITY-050, D-KOF-FIRST, rule 5, rule 6, rule 11`; plano `docs/stdlib/PLAN-STDLIB-EXPANSION.md`.

---

## D-KOFSHARE-100KOF — o KofShare é um aplicativo 100% Kof (servidor + cliente); interop NÃO é rota de produto; a decisão de capacidade do §559 é (a) frente stdlib (mantenedora 01/10/2026, diretriz "o kofshare é 100% feito em kof" / "kofshare é um aplicativo, um servidor e um cliente")

**Estado:** DECIDIDO — fecha a escolha regra-6 do §559; abre a frente de fila do socket-front `kof.net`

- **Diretriz (mantenedora):** o KofShare — o produto de compartilhamento P2P de arquivos (servidor + cliente, repositório `kof-share`) — é escrito **inteiramente em Kof**. Interop JVM sobre `java.net.ServerSocket`/`KeyAgreement`/`Signature` é PROIBIDO como rota de produto.
- **O que o interop foi:** a evidência de sonda do §559 — o caminho mais rápido para MEDIR as capacidades faltantes (transporte, acordo de chaves, assinatura) e derivar a semântica do protocolo de transferência (X25519 + Ed25519 + AES-GCM + framing HMAC, verde 01/10). As sondas ficam como evidência; a rota morre.
- **Consequência da decisão (§559):** opção **(a)** — a superfície de data-plane vira uma frente oficial da stdlib: sockets orientados a conexão no namespace de rede (o menor primitivo pelo `D-KOF-FIRST`), e uma face de acordo/troca de chaves no `kof.security` (família `SECN005`). Até a frente existir, **o KofShare está BLOQUEADO por capacidade da stdlib, não por código de produto** — bloqueio honesto, nunca fallback silencioso por interop (`no-silent-fallback`).
- **Casa arquitetural:** TCP/listen/accept/connect entra na frente de rede já autorizada pelo `D-UDP` (mesma família, mesma pergunta de namespace — a pergunta aberta (1) do plano UDP, `kof.udp` vs extensão de `kof.net`, agora É também a pergunta de nomeação do TCP; responder uma vez, para ambos).
- **O que isto NÃO autoriza:** inventar a superfície de sockets sem a resposta da mantenedora às perguntas de namespace/bloqueio/tipos (regra 6); lançar o KofShare em interop mesmo assim; um shim C privado por produto.
- **Relações:** `Depende de: D-KOF-FIRST, D-KOF-FIRST-IMPL, D-UDP (pergunta de nomeação), §559 (casa catalogada)`

---

## D-KOF-NET — frente de rede unificada `kof.net` (TCP + UDP), verbos bloqueantes + `spawn`, payload `Byte[]`, UDP endereçado por `"host:port"`, limite de 64 KiB, unicast-only no v1 (mantenedora 01/10/2026, votos regra-6 no chat)

**Estado:** DECIDIDO — o contrato de superfície da frente de rede da stdlib; destrava o bloqueio do KofShare (`D-KOFSHARE-100KOF`/§559-a); a implementação segue o fluxo de promoção

- **Namespace:** UM `kof.net` para as duas famílias de transporte, ao lado dos acessores URI já existentes (`net.scheme/host/port/path/query/fragment/queryEncode/Decode` — medido: todos recebem String de URL, sem colisão de verbos). Verbos TCP e verbos UDP vivem no mesmo namespace.
- **Escopo:** TCP e UDP NA MESMA frente/v1 (a mantenedora escolheu "TCP + UDP juntos agora", rejeitando TCP-primeiro) — o item de fila embarca como uma frente de rede coerente.
- **Modelo de bloqueio:** verbos BLOQUEANTES (`listen/connect/accept/send/receive` bloqueiam o worker chamador); o paralelismo é a concorrência Kof existente — `spawn` de um worker por conexão/endpoint. Nenhuma máquina async/await de I/O é introduzida (nenhuma existe, medida para I/O; no-silent-fallback).
- **Tipo de payload:** `Byte[]` flui nas duas mãos nos dois transportes (bytes de stream no TCP, um datagrama por `send` no UDP). Sem record `Message`/`Datagram`, sem overload de conveniência String no v1 (um jeito de fazer; os namespaces `encoding` convertem).
- **Endereçamento UDP:** endpoint/par = `String "host:port"` (ex.: `"127.0.0.1:9000"`); o `receive` entrega os bytes E o endereço de origem nessa forma. Nenhum tipo `Addr` novo no v1.
- **Limite de datagrama:** 64 KiB (limite IPv4 prático) — `bind`/`send` RECUSAM maiores com diagnóstico `NET00x`; sem fragmentação transparente; comportamento idêntico por alvo.
- **Broadcast/multicast:** NÃO no v1 (só unicast). Uma face posterior exige decisão regra-6 própria.
- **Segurança/política:** endpoints de rede obedecem ao modelo `app.security`/política existente — nenhuma face nova de política é inventada aqui. A face de troca de chaves (`SECN005`, o outro requisito do `D-KOFSHARE-100KOF`) permanece decisão de superfície separada.
- **Superfície (contrato, a validar por compilação na fatia 1):** `net.listen(port) -> Listener`, `listener.accept() -> Conn`, `net.connect(host, port) -> Conn`, `conn.send(Byte[]) -> Int`, `conn.receive(maxBytes) -> Byte[]`, `conn.close()`, `listener.close()`; `net.bind(port) -> Endpoint`, `endpoint.send(addr, Byte[])`, `endpoint.receive(maxBytes)` com origem, `endpoint.close()`. Nomes/tipos congelados por esta decisão; a forma exata do receive-com-origem é a primeira questão de projeto a sonda-de-compilação (ausência de tupla no Kof ⇒ provável `record Datagram(Byte[] bytes, String from)` — a preferência Byte[]/sem-record nova é honrada no ENVIO; o LADO RECEBEDOR pode exigir o portador da origem: decidir via sonda RED-first, mantendo esta nota atualizada).
- **Relações:** `Depende de: D-KOF-FIRST-IMPL (library-first), D-UDP (subsumido aqui), §559 (gap catalogado que isto fecha), D-KOFSHARE-100KOF (produto bloqueado nisto)`; `Resolve-questoes: D-UDP plano §5 (1,2,3,4,5,6 — numeração histórica, plano agora é ponteiro)`
