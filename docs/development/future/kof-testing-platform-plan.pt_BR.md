[English](kof-testing-platform-plan.md) | [Português](kof-testing-platform-plan.pt_BR.md)

# Plataforma de Testes Kof — Unit / Integração / Frontend E2E

**Status:** Plano futuro — só design, **zero código**
**Local:** `docs/development/future/`
**Natureza:** arquitetura, contratos, intenção de API, dependências, critérios de promoção
**Fonte normativa:** `DECISIONS.md` §`D-TESTING-PLATFORM` pendente (regra 6 — a mantenedora decide)
**Dependências principais:** o comando `kof test` existente (`CmdTest`), a superfície de teste da
linguagem (`test`/`assert`), o harness por alvo (`ConformanceMatrixTest`), `KofJsRunner`,
`KofJsBrowserE2ETest`, a CLI, KofJS, o futuro KofWasm
**Plano companheiro:** `test-architecture-plan.md` (refatoração da **suíte Java do próprio
compilador** — camadas L0–L5, perfis, performance). Este documento é a **plataforma de testes do
usuário**; os dois se encontram no §13 (Performance) e não podem se duplicar.
**Estado de implementação:** não iniciado

> **Regra fundamental.** Este documento descreve uma direção arquitetural futura. Ele **não**
> altera a linguagem, não adiciona palavras-chave, não cria namespaces e não abre trilha de
> implementação. Toda sintaxe mostrada é uma **forma de intenção**; a forma definitiva pertence
> à mantenedora (regra 6).
>
> **KOF-first (regra 10) e "Kof não é Java" (regras 8/45).** A API de testes é expressa na
> **gramática da Kof** — já existe uma superfície `test`/`assert`. Nenhuma sintaxe de teste de
> JavaScript/Jest, Kotlin, Python ou Rust é importada. Playwright/Cypress são **providers de
> execução**, nunca a API pública.
>
> **Objetivo em uma linha:** testar pequeno quando o problema é pequeno, testar integrado quando
> a integração importa, abrir um navegador só quando o comportamento da aplicação precisa ser
> testado.

---

# 0. Objetivo e não-objetivos

## 0.1 Objetivo

Criar uma arquitetura oficial e modular de testes para a Kof em três níveis:

```text
Testes Unitários  →  Testes de Integração  →  Frontend E2E / Testes de Browser
```

Rápida no desenvolvimento cotidiano, determinística, extensível e capaz de crescer junto com
KofJS, KofWasm e os futuros targets. Um desenvolvedor deve ir de uma função isolada até uma
aplicação Kof completa rodando em navegador real sem sair da Kof.

## 0.2 Não-objetivos

* Não é um framework gigantesco: **não construir "um clone de Jest + JUnit + Playwright + Cypress
  dentro da Kof"**.
* Não é copiar a API JS do Playwright ou do Cypress — a API representa **conceitos de teste de
  browser**; os providers os implementam.
* Não é substituir a superfície `kof test`/`assert` existente — ela é **estendida de forma aditiva**.
* Não é rodar toda a matriz de browsers / todos os targets em toda mudança trivial.
* Não é retry como conserto de teste flaky.
* Não é mockar indiscriminadamente: comportamento real quando for barato e determinístico.

---

# 1. Arquitetura

```text
                    Kof Testing
                         │
          ┌──────────────┼──────────────┐
         Unit        Integração          E2E
          │              │              │
          │              │      Browser Automation
          │              │              │
          │              │      API de Browser Kof
          │              │              │
          │              │      Browser Driver (SPI)
          │              │         ┌────┴────┐
          │              │     Playwright  Cypress (depois)
          └──────────────┴──────────────┘
                         │
                    Test Runner
```

A API pública da Kof **não** é acoplada a Playwright/Cypress. Uma abstração de teste de browser
é da Kof; providers/servidores implementam a execução por baixo.

---

# 2. Primeira regra — analisar o que já existe

Antes de implementar qualquer coisa (regra 54). O inventário abaixo é o ponto de partida real;
**evoluí-lo, não substituí-lo e não duplicá-lo.**

| Mecanismo existente | Âncora real | Papel |
|---|---|---|
| Comando `kof test` | `kof-cli/.../CmdTest.java:15` (`kof test <file\|dir> [--target jvm\|native\|js] [--timeout <sec>]`); dispatch `Main.java:22` | Ponto de entrada do runner a estender |
| Superfície de teste da linguagem | `test "name" { }` + `assert(cond[, msg])`; palavra-chave `assert` `Lexer.java:68`, `TokenType.ASSERT`; desugar `CompilerDesugar.java:16,336` (`desugarTests`/`buildTestHarnessMain`) | A API sobre a qual a plataforma é construída — **nada de sintaxe nova** |
| Pipeline de compilação de testes | `CompilerPipeline.java:121-141` (`compileForTests`, `compileForTestsSources`, `discoveredTests`); `CompilerDriverState.java:208,223` | Encanamento do harness |
| Harness por alvo | `ConformanceMatrixTest.java:44-80` (`freshDriver`, `runJvm`, `runScript`, `runNative`) + 133 casos `matrix(...)` | O oráculo canônico de compilar-e-rodar a generalizar |
| Execução KofJS | `KofJsE2ETest.java:19` (**GraalJS** embutido, `KofJsRunner.run`); `KofJsHostlessRuntimeTest` | Teste do alvo JS (sem Node obrigatório) |
| Browser E2E (embrionário) | `KofJsBrowserE2ETest.java` (Chrome real `--headless --dump-dom` `:38`; macOS `safaridriver` W3C WebDriver `:81`; `findBrowser` `:93`; skip `:139`) | A semente do Browser Core; mecanismo cru, não abstração |
| Helpers compartilhados | `TestJdk.java:21-40`, `NativeToolchainGate.java:21`, `JsRuntimeTestSupport.java:19`, `CliProcessTree.java:23` | Primitivas reutilizáveis (pequenas, estáticas) |
| Scripts golden/integração | `tests/run-golden.sh:28`, `tests/run-integration.sh:118` (suíte assert do `kof test`) | Portões de release a manter |
| CI | `.github/workflows/ci.yml` (build+test, multiplatform, cross-native, structural), `release.yml` (golden+integração) | Perfis integram aqui |
| Guardas de ambiente | `assumeTrue` em 121 arquivos; `NativeToolchainGate`, guardas qemu, env de DB (`KOF_MYSQL_PORT`), `KofJsBrowserE2ETest:139` | A regra de determinismo já existe |
| Fixture de app real | `examples/fullstack/`; `FullStackE2ETest.java:33-45,90`; `KofWebE2ETest`, `KofHttpServerTest`, `KofWebWsE2ETest`/`KofWebSseE2ETest` | App de referência + integração web |
| Sem namespace `kof.test` | `StdCatalog.java:34-59` não lista `test`; `kof.test` é feature do CompilerDriver/CLI (`ecosystem-coverage.md:74,367`) | Questão aberta §12 |
| Sem WASM ainda | `TargetMatrix.java:72` → `WASM001`; `TargetMatrixTest.java:55`; `wasm-wasi-plan.md` | E2E cross-target deve esperar o KofWasm |
| Sem abstração de browser | confirmado: nenhuma dependência Playwright/Cypress/Selenium/Puppeteer no repo | O que este plano adiciona |
| Runner Maven surefire | `pom.xml:86-105`; JUnit Jupiter `pom.xml:40`; sem perfis | Suíte interna (plano companheiro) |

**Conclusão:** a linguagem já tem `test`/`assert` e um runner de CLI, mas **não há harness
compartilhado, nem níveis/perfis/tags, nem abstração de browser, nem artefatos**. A peça que
falta é a camada de plataforma, não uma linguagem nova.

---

# 3. Níveis — separação clara

## 3.1 Unit

Uma unidade isolada: lexer, parser, AST, analisador semântico, type checker, IR, função da
stdlib, utilitário, componente, serviço.

Características: rápido, isolado, determinístico, sem processo externo, sem browser, sem rede
real, sem DB real, sem filesystem real quando desnecessário. Meta: **milissegundos** (segundos
para grupos maiores).

## 3.2 Integração

Componentes reais trabalhando juntos: compilador Kof + filesystem; Kof + banco; Kof + HTTP;
Kof + stdlib; Kof + biblioteca nativa; Kof + JVM; Kof + runtime JS; Kof + runtime WASM; aplicação
Kof + backend. Dependências reais são permitidas quando fazem parte do comportamento testado;
não mockar o sistema inteiro.

## 3.3 Frontend E2E

Uma aplicação real em navegador:

```text
fonte Kof → KofJS / KofWasm → aplicação web → browser → interação de usuário real
```

Testar navegação, clique, teclado, formulários, inputs, links, routing, cookies, localStorage,
sessionStorage, fetch, WebSocket, upload, download, autenticação, permissões, dialogs,
comportamento responsivo, erros de frontend e integração frontend/backend.

---

# 4. API de Testes Unitários

Construir sobre a superfície existente (`test "name" { }` + `assert`). A extensão exata é
decisão de regra 6; **nada de sintaxe estrangeira**. Antes de qualquer API: estudar a gramática,
funções, módulos, closures, exceções/erros e o runner atual.

## 4.1 Assertions

Um conjunto pequeno, só onde o type system/runtime fazem sentido:

```text
assert(...)          já existe
assertEqual(...)
assertNotEqual(...)
assertTrue(...) / assertFalse(...)
assertNull(...) / assertNotNull(...)
assertThrows(...)
```

As assertions devem produzir diagnósticos úteis — `expected`, `actual`, `test`, `source` —
nunca um `test failed` seco.

## 4.2 Lifecycle

Suportar `beforeAll` / `afterAll` / `beforeEach` / `afterEach` quando necessário, mas nunca como
mecanismo de estado global. Um teste continua isolado.

## 4.3 Isolamento

```text
teste A → estado isolado
teste B → estado isolado
```

Nunca `teste A → estado global → teste B depende de A`. Filesystem temporário, portas, banco e
recursos têm lifecycle explícito.

## 4.4 Testes parametrizados

Avaliar tabelas `entrada → esperado` — muito útil para parser, type checker, encoding, HTTP,
processamento de string e operações numéricas. Não duplicar dezenas de testes só porque os
inputs mudam.

## 4.5 Property-based (futuro)

Deixar a arquitetura pronta para `encode(decode(x)) == x` ou `parse(print(ast)) == ast` quando a
propriedade fizer sentido. **Não implementar antes de existir necessidade real.**

## 4.6 Test doubles

Suporte mínimo a fake/stub/spy/mock. Regra: se o comportamento real é barato e determinístico,
usar o comportamento real. Mockar principalmente fronteiras externas: serviço HTTP, filesystem,
clock, fonte aleatória, processo externo, banco.

---

# 5. Harness de Integração

Infraestrutura para subir recursos: servidor HTTP, banco, filesystem, processo, serviço externo.
Cada recurso tem `start → health check → test → cleanup`. **Nunca deixar processos ou portas
abertas após o teste.**

## 5.1 Ambiente temporário

Suporte oficial a diretório, banco, config, servidor e variáveis de ambiente temporários.
`cleanup` deve acontecer **mesmo quando o teste falha**.

## 5.2 Matriz de integração

Declarar quais targets um teste precisa (`JVM`, `Native`, `JS`, `WASM`). Uma propriedade comum
aos targets pode rodar em vários; não rodar todos os targets para todo teste automaticamente.

## 5.3 Testes cross-target

Categoria dedicada provando que

```text
fonte Kof → JVM   ≡   fonte Kof → Native   ≡   fonte Kof → JS
```

produzem comportamento equivalente quando a feature é suportada. Isso generaliza o
`ConformanceMatrixTest` e se torna essencial para KofJS e KofWasm. Target não suportado →
diagnóstico honesto (classe `NATIVE002`/`WASM001`), nunca silêncio.

---

# 6. API de Testes de Frontend

Uma API oficial de teste de browser em Kof. Conceitualmente:

```text
browser.launch()          page.goto(...)
browser.newPage()         page.click(...)
                          page.fill(...)
                          page.press(...)
                          page.locator(...)
                          page.text(...)
page.attribute(...)       page.screenshot(...)
                          page.evaluate(...)
```

Esses nomes são apenas conceitos; a API final segue a gramática e convenções da Kof. **Não
copiar a API JS do Playwright.**

## 6.1 Abstração de browser

```text
API de Teste de Browser Kof
        │
   Browser Driver (SPI do provider)
        │
   ┌────┴────┐
Playwright  Cypress
```

A Kof é dona da API; Playwright e Cypress são **providers/backends**.

## 6.2 Provider Playwright (primeiro)

Iniciar browser, criar context/page, navegar, localizar elementos, interagir, coletar
informações, screenshots, traces, vídeos quando suportado, console, network, cookies, storage,
cleanup. O provider encapsula o Playwright; testes Kof nunca dependem das entranhas do Playwright.

## 6.3 Provider Cypress (depois)

Não assumir que o modelo de execução do Cypress é igual ao do Playwright. Expor apenas
capacidades com semântica compatível; capacidade ausente em um backend gera um **diagnóstico
claro `capability unsupported`** — nunca fingir equivalência.

## 6.4 Matriz de capacidades de browser

```text
Capacidade               Playwright   Cypress   Driver futuro
navegação                   ✓            ?           ?
clique / fill / teclado     ✓            ?           ?
locator / assertion         ✓            ?           ?
screenshot / vídeo / trace  ✓            ?           ?
interceptação de rede       ✓            ?           ?
cookies / storage           ✓            ?           ?
upload / download           ✓            ?           ?
dialogs / console           ✓            ?           ?
geolocalização / permissões ✓            ?           ?
múltiplas abas / iframes    ✓            ?           ?
websocket                   ✓            ?           ?
```

Os valores são **descobertos na implementação**, nunca supostos. O sistema deve saber quais
capacidades estão disponíveis (valores `✓`/`—`/`parcial` completados por provider).

## 6.5 Locators e auto-wait

Priorizar locators semânticos: `role`, `text`, `label`, `placeholder`, `test-id`, depois `css`,
`xpath`. Os testes não devem depender de CSS frágil. A API deve permitir localizar + afirmar
visível/habilitado/texto/valor/atributo.

Auto-wait é fundamental: preferir `wait until visible/enabled/text/network idle/URL/condition` a
`sleep(1000)`. **Sleeps arbitrários não são estratégia normal de sincronização.**

## 6.6 Assertions web

Conceitualmente `expect(locator).toBeVisible()`, `toHaveText`, `toHaveValue`,
`expect(page).toHaveURL` — mas idiomático Kof, não sintaxe JS copiada.

## 6.7 Teste de rede

Interceptação controlada de requests (intercept GET/POST, mock response, inspecionar
request/response). Manter **Frontend E2E** e **integração Frontend + Backend** separados: ambos
são necessários.

## 6.8 Fixtures

Fixtures reutilizáveis (`authenticatedPage`, `adminPage`, `loggedOutPage`, `testUser`,
`testDatabase`, `testBackend`) que **não** escondem dependências importantes — o teste continua
legível.

## 6.9 Full Web Integration

```text
backend Kof → HTTP/API → frontend KofJS / KofWasm → browser real
```

validando o sistema completo. Um app de referência (`examples/fullstack/` é a semente, §8.4)
deve expor testes unit + integração + e2e e servir de teste de integração da própria plataforma.

## 6.10 KofJS e KofWasm

A infraestrutura de browser deve funcionar para os dois:

```text
fonte Kof → KofJS   e   fonte Kof → KofWasm
```

quando a aplicação for compatível — protegendo a promessa "mesmo código Kof, target diferente".
KofWasm é futuro (`WASM001`, `wasm-wasi-plan.md`); a plataforma não deve prometê-lo cedo.

## 6.11 Matriz de browsers e mobile

Rodar a mesma suíte em Chromium/Firefox/WebKit quando o backend suportar; nunca todos os
browsers em todo PR por padrão. Perfis: `Fast Browser` e `Full Browser Matrix`. Suportar
desktop/tablet/mobile via viewport, emulação de dispositivo, toque e orientação quando suportado.

## 6.12 WebSocket / SSE

Testar `connect`, `message`, `disconnect`, `reconnect`, `error` — para aplicações Kof realtime
(semente: `KofWebWsE2ETest`, `KofWebSseE2ETest`).

## 6.13 Download / Upload

`upload file`, `download file`, verificar conteúdo/nome/MIME; integrar com `kof.file` quando
disponível.

---

# 7. Test Runner

O runner deve entender descoberta, filtragem, lifecycle, paralelismo, timeouts, retries,
artefatos, relatórios e exit codes. **Não reinventar o que o runner atual já tem** — integrar
com o `kof test` (`CmdTest`)/pipeline de teste do compilador.

## 7.1 Tagging

Categorizar testes: `unit`, `integration`, `e2e`, `slow`, `browser`, `network`, `database`,
`native`, `jvm`, `js`, `wasm`, `security` — permitindo filtros eficientes.

## 7.2 Paralelismo

Unit: paralelo por padrão quando isolado. Integração: controlado. E2E: por browser/context/projeto
quando seguro. Nunca compartilhar portas, banco, filesystem, sessão, cookies ou estado global.

## 7.3 Timeouts

Todo teste tem timeout razoável: separar unit, integração, e2e e ação de browser. Nunca deixar um
teste travar indefinidamente. (Semente: `CmdTest --timeout`, `CmdTestTimeoutTest`.)

## 7.4 Retry e detecção de flaky

Retries com muito cuidado, **nunca para esconder flakiness**. Registrar primeira execução,
retries, duração, ambiente, browser, target, resultado. Um teste que alterna pass/fail não é
saudável só porque o retry passou → relatório **Flaky Test Detection**.

## 7.5 Modo debug

`test --debug`: browser visível, slow motion opcional, logs detalhados, screenshots, trace.

---

# 8. Artefatos, relatórios, dados e lifecycle do servidor

## 8.1 Artefatos

Em falha de E2E, coletar automaticamente quando possível: screenshot, vídeo, trace, console,
network, HTML, logs — para `CI falhou → abrir artefato → ver exatamente o que aconteceu`.

## 8.2 Screenshots e regressão visual

Permitir screenshot de página/elemento/falha. Avaliar comparação de screenshot como capacidade
**futura** (`baseline → novo screenshot → comparação de pixels → relatório de diferença`) para
componentes, layouts, dashboards e páginas críticas. Deve ser determinístico; regressão visual
**não** é obrigatória para todos os testes.

## 8.3 Acessibilidade (avaliar)

Integração com ferramentas de acessibilidade para roles, labels, navegação por teclado, contraste
quando suportado, nomes acessíveis e estrutura semântica. Nunca substitui o teste manual de
acessibilidade.

## 8.4 Dados de teste e lifecycle do servidor

Mecanismos para seed de banco, criar usuários/fixtures e resetar estado; evitar fixtures globais
permanentes — cada execução limpa seu próprio estado. Para E2E:
`build app → start server → wait ready → run browser → collect artifacts → shutdown`; o
desenvolvedor não deve iniciar cinco processos manualmente.

## 8.5 Relatório unificado

```text
Kof Test Report
Unit         1203 passed / 0 failed   4.2s
Integração    430 passed / 2 failed   38s
E2E            87 passed / 1 failed   2m14s
```

com duração e identificação de testes lentos. Meta de design: `docs/testing/TEST-PERFORMANCE.md`
(o plano companheiro o propõe; §13).

---

# 9. Segurança, CI e perfis

## 9.1 Testes de segurança

Verificar XSS, CSRF, autenticação, autorização, comportamento de cookie, CORS, input inválido,
injection e expiração de sessão. Nunca substituir ferramentas especializadas de segurança.

## 9.2 Perfis

```text
test unit          → segundos
test integration   → minutos
test e2e           → automação de browser
test all           → unit + integração + e2e
test e2e --browser chromium|firefox|webkit
```

A sintaxe final segue a CLI existente. Esses perfis espelham a proposta Maven
`fast`/`integration`/`full`/`stress` do plano companheiro (`test-architecture-plan.md:167`),
operando no nível da plataforma em vez do nível da suíte interna.

## 9.3 Integração com CI

```text
Pull Request  → Unit + Integração relevante
Merge         → Unit + Integração + E2E
Release       → Full + Browser Matrix + Cross Target
```

Não rodar a matriz completa de browsers em toda mudança trivial. Reusar os workflows existentes
(`.github/workflows/ci.yml`, `release.yml`) e os portões de release (`tests/run-golden.sh`,
`tests/run-integration.sh`).

---

# 10. Testar o próprio framework

O framework precisa testar a si próprio: testes de unit/integração/driver/browser/falha/timeout/
paralelo/artefato — especialmente `test passes`, `test fails`, `test throws`, `test timeout`,
`test cleanup`, `test retry`, `test browser crash`, `test application crash`.

---

# 11. Implementação incremental

| Fase | Escopo |
|---|---|
| 1 · Unit Core | descoberta, assertions, lifecycle, isolamento, relatório, filtragem, timeout |
| 2 · Integração Core | fixtures, ambiente temporário, lifecycle de processo, HTTP, banco, cleanup |
| 3 · Browser Core | abstração de browser, page, locator, navegação, interação, assertions de browser |
| 4 · Playwright | primeiro provider completo; provar `Kof test → Playwright → Chromium → app real` |
| 5 · Cypress | segundo provider só depois da abstração estável; mapear capacidades |
| 6 · KofJS / KofWasm | E2E para os dois, mesma suíte quando possível |
| 7 · Cross-browser | Chromium, Firefox, WebKit |
| 8 · Avançado | regressão visual, acessibilidade, mocking de rede, WebSocket, upload/download, emulação mobile, traces, vídeos, diagnósticos avançados |

O plano companheiro (`test-architecture-plan.md`) pode avançar o eixo da **suíte interna** em
paralelo; nenhum bloqueia o outro, e ambos compartilham o §13.

---

# 12. Decisões abertas (regra 6 — a mantenedora decide)

* **D-TESTING-PLATFORM** — abrir a frente e seu escopo ordenado.
* A **sintaxe exata da API de testes** (assertions, lifecycle, parametrização, locators) — aditiva
  ao `test`/`assert` existente; sem sintaxe estrangeira.
* Se `kof.test` vira um **namespace da stdlib** (hoje o `StdCatalog` não tem nenhum) ou continua
  feature do compilador/CLI.
* **Política de providers**: Playwright/Cypress são dependências externas pesadas — como são
  declaradas, versionadas e barradas (interop-first, R9), e se vêm com a CLI ou são opt-in.
* Se o browser E2E roda **só na JVM** primeiro (o `KofJsBrowserE2ETest` atual é teste Java) ou
  também de um `kof test --e2e` standalone.
* Promoção: `future/` → `docs/development/` quando a primeira fatia landar (três estados + R12).

---

# 13. Performance (encontro com o plano companheiro)

Testes unitários não podem depender da infraestrutura de browser; testes de integração não podem
iniciar browser sem necessidade. Hierarquia: `Unit (barato) → Integração (moderado) → E2E (caro)`.
Quanto mais caro o nível, menos testes. É o mesmo princípio do `test-architecture-plan.md`
(§"Feedback cost"); a plataforma expõe os **perfis**, o plano companheiro mede/refatora a **suíte
Java interna**. Não duplicar o trabalho de profiling — referenciá-lo.

---

# 14. Relação com outros planos

* `docs/development/future/test-architecture-plan.md` — suíte Java interna (L0–L5, perfis,
  performance). **Complementar, não duplicado.**
* `docs/development/future/wasm-wasi-plan.md` — KofWasm; o E2E cross-target/WASM da plataforma
  depende dele (`WASM001` até então).
* `docs/development/future/qrcode-wasm-plan.md` — outro consumidor da frente WASM.
* `docs/development/future/kof-file-plan.md` — `kof.file` para helpers de upload/download.
* `docs/development/future/kof-connector-ecosystem-plan.md` — providers (drivers de browser) são
  uma SPI natural no estilo connector; referência cruzada para o padrão SPI/manifest.

---

# 15. Critérios de sucesso

Funcional quando for possível:

* **Unit** — criar e executar testes isolados rapidamente.
* **Integração** — rodar componentes reais juntos sem rodar a suíte inteira.
* **E2E** — abrir uma aplicação real em browser e executar ações reais.
* **Cross-target** — rodar a mesma aplicação Kof via KofJS (e KofWasm quando suportado).
* **Cross-browser** — rodar a mesma suíte em múltiplos browsers.
* **Diagnósticos** — uma falha E2E gera evidência suficiente para diagnosticar sem reprodução
  manual.

---

# 16. Regra final

**Não** construir "um clone de Jest + JUnit + Playwright + Cypress dentro da Kof". Construir uma
**infraestrutura de testes Kof** que entende a arquitetura da linguagem e usa ferramentas maduras
do ecossistema quando elas são a melhor implementação por baixo.

> **A Kof fornece a experiência de teste; os providers fornecem a execução.**

Testar Kof deve ser tão natural quanto escrever Kof:

```text
Kof → Kof Test → Unit / Integração / Browser → resultado
```

Sem gambiarra, sem abandonar a Kof para escrever os testes e sem transformar cada teste numa
compilação de duas horas.
