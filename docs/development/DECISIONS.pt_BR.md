[English](DECISIONS.md) | [Português](DECISIONS.pt_BR.md)

# DECISIONS — registro de decisões da linguagem

**Última atualização:** 15/09/2026
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
- **D-BRANCH-0.5.0** — trabalho move para `beta-0.5.0`
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

**Fatias (fila aberta em `docs/development/db-parity-plan.pt_BR.md`):** S0
diagnóstico interino honesto (limpa o aceite silencioso do §421 enquanto os
schemes pousam); S1 `mariadb://` = alias mysql-wire (Native, 3 arcos); S2
paridade de schemes JDBC JVM/JS/Android (por-driver medido, diagnóstico honesto
de driver ausente); S3 `mongodb://` interop-first (driver/wire — nunca um
servidor caseiro, R9); S4 oracle (idem). Dono: frente DB/ORM (dono a nomear) +
esta lane para o plano/registros. **Não é mudança de superfície congelada** —
alarga as URLs aceitas; a API `kof.db`/`kof.orm` não muda.

## D-BRANCH-0.5.0 — trabalho move para `beta-0.5.0`; `beta-0.4.0` fica para pousos em voo + preparo da release (20/09/2026, ordem da mantenedora)

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
- A fila do preparo mora em `docs/development/release-beta-0.5.0-prep.pt_BR.md`
  (+EN).

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
release (`docs/development/release-beta-0.5.0-prep.md`) e substitui a cláusula
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

A mantenedora pergunta como Kof lida com 2D, 3D e gráficos não-web ("como alguém desenvolve um jogo em Kof?") e dirige: abrir o plano em `docs/development/future/`. Estado real hoje: `kof.ui` é superfície de formulário/intenção (JVM=JavaFX, JS=DOM, Android=APK); o corpus NÃO tem abstração de jogo (frame loop, sprites, malhas, input-por-frame, áudio, GPU) — jogo hoje seria interop, não idioma (fronteira regra 8/11: a forma de API estrangeira não é a resposta; o plano deve definir a INTENÇÃO Kof que os backends abaixam, gaps por-alvo honestos R6/XXX001, interop-first R9 para engines/libs — nunca renderizador caseiro, e KofC/wasm são future). DOC-PLANO: `docs/development/future/graphics-gaming-plan.md` — skeleton na próxima sessão; perguntas que o plano DEVE responder: primitiva de game-loop (idioma `scene`/`frame`?), superfície 2D sprite/tilface, escopo 3D (mesh/camera/material como intenção vs. FFI para GPU nativa), áudio, modelo de input, honestidade por-alvo (JVM/Native/JS/web + KofC depois) e a guarda de non-goals (sem canvas/HTML vazando para código de usuário). Prioridade: future/ — NÃO compete com o 1.0 (R12 + D-1.0-STABILITY-100: só entra na superfície 1.0 por promoção explícita dela).

### Adendo D-GRAPHICS-GAMING (20/09/2026, mantenedora, mesma sessão) — a superfície de mídia ESTÁ no escopo do plano: pipeline de som E suporte a vídeo

Kof também precisa de um **pipeline de som** (reprodução, streams, volume/mix, o caso de áudio de jogo: SFX de baixa latência) e de **suporte a vídeo** (uma superfície de intenção `video`/`VideoView` no mundo `kof.ui`: reprodução de arquivo/stream, o chrome do player pertencendo à plataforma, nunca ao código do usuário). O doc do plano DEVE tratar mídia como first-class: o idioma KOF (ex.: `sound.play("x.ogg")`, componente de painel `video`) + lowering por-alvo JVM (JavaFX Media/`javax.sound` JÁ existem hoje em JVM — medir antes de prometer), JS (`<video>`/WebAudio do browser — a plataforma renderiza), Native (interop-first R9: SDL_mixer/miniaudio/OpenAL/ffmpeg — nunca codec caseiro; gaps honestos `XXX001` onde faltar, ex. áudio no cross riscv), + a guarda de non-goals (sem tags `<audio>` HTML5 vazando para código Kof; codecs são problema da plataforma). O pipeline de som (mixing/grafos) ganha SEÇÃO PRÓPRIA no plano respondendo: contrato de latência, formatos suportados por alvo, enumeração de dispositivos, e se `kof.sound` é stdlib-core ou pacote stdlib (fronteira R1).

### Adendo 2 D-GRAPHICS-GAMING (20/09/2026, mantenedora) — SEM JavaFX; a superfície de mídia/imagens exige PARIDADE TOTAL

"Sem JavaFX. Tem que ter paridade total." Consequências registradas: (1) o plano de gráficos/mídia NÃO PODE usar JavaFX (nem toolkit single-target) como backend da superfície KOF — o JVM tem de chegar ao mesmo idioma pela MESMA pilha portátil dos outros alvos (interop-first R9: a forma que o plano avalia é uma camada portátil classe SDL/GL rebaixada por bindings por-alvo, não chrome de plataforma); (2) PARIDADE TOTAL é o critério de aceite desta superfície — diferente do "escopo honesto por alvo" do R7, um recurso de gráficos/mídia só entra na superfície da linguagem quando TODO alvo rodar o MESMO programa com o MESMO comportamento (ou o recurso não é promovido); (3) o kof.ui-JVM atual (JavaFX) continua funcionando intacto (compatibilidade retroativa, regra 2 do freeze) mas é a face LEGADA da área, não a futura — migração/reforma é QUESTÃO DE DESIGN que o plano deve responder (regra 6), nunca decisão de agente; (4) o item de remoção do JavaFX entra no plano `docs/development/future/graphics-gaming-plan.md` §parity como seção própria (medir hoje: quais classes kof.ui ligam javafx.* no alvo JVM).

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
scoped-resources / RAII, `docs/development/future/scoped-resources-plan.md`),
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
- **Até lá NADA muda:** `beta-0.5.0` segue a branch ativa
  (`D-BRANCH-0.5.0` em vigor); agentes seguem empurrando para ela; #619
  segue HELD (regra 10). Fila: roadmap §23 `TIER 14`.
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
   recusa `INTEROP005` onde a face nao esta provada (cross travado pelo §513,
   ABERTO — lane native; ANDROID/MCU/RISCV32 pela R7). O contrato replay
   "a sessao E a fonte" substitui o `python -` de vida longa (medido impossivel
   sem EOF); uma superficie de sessao viva exige tipo de handle nomeado =
   regra 6, fatia futura. Os args Double do motor expuseram e CORRIGIRAM o §512
   (colapso da tag de elemento do json) na raiz, com regressao oraculo-JVM.
   Restante: records↔JSON + motor R (fatia 2), timeout/cancel (fatia 3),
   re-entrada cross com o §513 (fatia 4), DoD de corpus + promocao (fatia 5).
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
