[English](kof-testing-platform-plan.md) | [Português](kof-testing-platform-plan.pt_BR.md)

# Plataforma de Testes Kof — Unit / Integração / Frontend E2E

**Dono:** `192.168.15.30:9093` (lane issues/tooling — claims DEVEM levar IP:PORTA, `D-AGENT-IDENTITY-IPPORT`)
**Status:** EM DESENVOLVIMENTO — promovido de `future/` 30/09/2026 (`D-TESTING-PLATFORM`, `D-FUTURE-BATCH-2809`/`B`, `D-FUTURE-PROMOTION`)
**Local:** `docs/development/`
**Natureza:** plano de implementação — estado real + como terminar (registro de design mantido abaixo)
**Fonte normativa:** `DECISIONS.md` §`D-TESTING-PLATFORM` (28/09, autorizada — `D-FUTURE-BATCH-2809`/`B`); a promoção a trabalho corrente é uma-por-vez por `D-FUTURE-PROMOTION`
**Dependências principais:** o comando `kof test` existente (`CmdTest`), a superfície de teste da
linguagem (`test`/`assert`), o harness por alvo (`ConformanceMatrixTest`), `KofJsRunner`,
`KofJsBrowserE2ETest`, a CLI, KofJS, o futuro KofWasm
**Plano companheiro:** `test-architecture-plan.md` (refatoração da **suíte Java do próprio
compilador** — camadas L0–L5, perfis, performance). Este documento é a **plataforma de testes do
usuário**; os dois se encontram no §13 (Performance) e não podem se duplicar.
**Estado de implementação:** fatia 1 (helpers de asserção) POUSADA 30/09; fatia 2 (`assertThrows`) POUSADA 30/09 — o bloqueio foi corrigido (ver §15); fatia 3 (asserções do unit-core) POUSADA 01/10; fatia 4 (asserções numéricas Long/Double/Float) POUSADA 01/10; fatia 5 (Byte/Short/Char + `assertNotEqualBool`) POUSADA 01/10; fatia 6 (par genérico `assertEqual<T>`/`assertNotEqual<T>`) POUSADA 02/10 — desbloqueada pela correção do `known-bugs` §553 (`D-EQ-UNBOUNDED-T`), então o §4.1 está **completo**; §5 harness — fatia 1 (temp dir + poll de prontidão) POUSADA 06/10, fatia 2 (ciclo de vida de banco `withDb` no opt-in `kof.test.db`) POUSADA 07/10, fatia 3 (ciclo de vida de servidor `withServer` no opt-in `kof.test.web`, `spawn`+prontidão+close no `finally`) POUSADA 08/10 — completo no JVM; os alvos cross reportam o gap pré-existente `app.close`/`WEB001` honestamente (sem fallback silencioso); política do provider de browser do §6 REGISTRADA 08/10 (só docs — opt-in por projeto `C`, todos os alvos `T1`, `kof.test` compilador/CLI `T2`; a fatia do provider em si segue gated pela decisão aberta da regra 6 sobre declaração de provider); medição de duração do runner do §8.5 (tempo por arquivo + arquivo mais lento) POUSADA 08/10; faces `processo`/`config`/`variáveis de ambiente` do §5.1 REGISTRADAS 08/10 como fronteiras medidas (handle não nomeável + sem varargs-de-lista; sem primitiva setenv, config só-leitura — regra 6).

> **Fatia 6 (POUSADA 02/10).** A última face do §4.1: o par genérico `assertEqual<T>(T expected, T actual, String label)` / `assertNotEqual<T>(...)` em `dev/kof/test.kf`. Ficou deliberadamente adiado (não entregue quebrado) até o `known-bugs` §553 ser resolvido: a resposta regra-6 da mantenedora `D-EQ-UNBOUNDED-T` (02/10) fixa `==` sobre um `T` não-limitado como **igualdade estrutural de conteúdo** em todo alvo, então o helper é correto para qualquer `T` (Int, String, record, …). O label stringifica `expected`/`actual` via `+` — sem primitiva nova, sem runtime por alvo. Prova RED-first: novo `GenericEqualityE2ETest` **16/16** (o par genérico verde em JVM/Script/JS/Nativo e lançando em mismatch real; o golden de semântica de `==` byte-idêntico ao oráculo JVM em JVM + Script + JS + Native x86-64 + riscv64(qemu) + aarch64(qemu)); `KofTestingE2ETest` 7/7. O §4.1 está completo; as faces restantes são regra-6/decisão (§4.4 parametrizado, §4.6 doubles, §5 harness). O **provider de browser** (§6) não está mais barrado: `D-MAINT-BATCH-0510`/`T1` decide que ele deve servir **todos os alvos** (JVM + JS + Native), e `/T2` decide que o `kof.test` continua **feature do compilador/CLI** (não namespace da stdlib) — ver §12.

> **Fatia 5 (POUSADA 01/10).** A superfície escalar restante do §4.1: `assertEqualByte`/`assertNotEqualByte`,
> `assertEqualShort`/`assertNotEqualShort`, `assertEqualChar`/`assertNotEqualChar`, mais o
> `assertNotEqualBool` que faltava (o lado da igualdade já existia). Adicionadas a `dev/kof/test.kf`
> (mesmo mecanismo de pacote virtual). **Sem aritmética de `Byte`/`Short`** — os helpers só comparam
> (`!=`/`==`) e imprimem, então não tocam o caminho aritmético do `#720` (`known-bugs` §561 — **CORRIGIDA 02/10** por `D-KOF-BYTE-ARITH` = promover `Byte`/`Short`/`Char` para `Int`).
> Tipadas por primitivo, não um `assertEqual<T>` genérico (ainda adiado pelo §553). Aditivo, Kof puro,
> sem sintaxe/primitiva nova. Prova: `KofTestingE2ETest` **7/7** em JVM + JS + Script + Native x86-64 +
> cross riscv64(qemu) + aarch64(qemu), paridade-por-golden com o oráculo JVM (RED pré-fatia: 7 ×
> `SEM015 Undefined function` na perna JVM). Próximo: faces de integração/browser (§11 fases 2–4).

> **Fatia 4 (POUSADA 01/10).** As asserções numéricas que ainda faltavam no §4.1 ao nível de primitivo: `assertEqualLong`/`assertNotEqualLong`, `assertEqualDouble`/`assertNotEqualDouble` e `assertEqualFloat`/`assertNotEqualFloat`, adicionadas a `dev/kof/test.kf` (mesmo mecanismo de pacote virtual). **Tipadas por primitivo** — NÃO um `assertEqual<T>` genérico, que segue adiado pelo `known-bugs` §553 (`==` sobre um `T` não-limitado diverge entre alvos). Aditivo, Kof puro, sem sintaxe/primitiva nova. Prova: `KofTestingE2ETest` **7/7** em JVM + JS + Script + Native x86-64 + cross riscv64(qemu) + aarch64(qemu), paridade-por-golden com o oráculo JVM (RED pré-fatia 1/1 na perna JVM: 12 × `SEM015 Undefined function` para os seis helpers). Agora lifecycle (§4.2, exige decisão de runner/desugar) e as faces de integração/browser (§11 fases 2–4).

> **Fatia 3 (POUSADA 01/10).** As asserções restantes do §4.1 foram adicionadas ao pacote virtual `kof.test`: `assertNotEqualString`, `assertEqualBool`, `assertNull<T>(T? value, String label)` e `assertNotNull<T>(T? value, String label)`. As checagens de null são **genéricas sobre `T?`** (apagadas por alvo), então aceitam qualquer valor anulável e ainda recusam o tipo errado em compilação; nada novo foi adicionado à linguagem. Aditivo, Kof puro, sem runtime por alvo. Prova: `KofTestingE2ETest` **7/7** em JVM + JS + Script + Native x86-64 + cross riscv64(qemu) + aarch64(qemu), paridade-por-golden com o oráculo JVM (RED pré-fatia 6/7 `SEM015 Undefined function`). O §4.1 está completo **exceto** o par genérico `assertEqual`/`assertNotEqual`, deliberadamente **adiado** (não entregue quebrado): um helper de igualdade genérico não pode estar correto em todos os alvos até o `known-bugs` §553 ser decidido — `==` sobre um `T` não-limitado diverge (JS estrutural vs JVM/Script/Nativo por referência), questão de operador congelado (regra 6). Agora lifecycle (§4.2) e as faces de integração/browser (§11 fases 2–4).

> **Fatia 2 (POUSADA 30/09).** `assertThrows(() -> Void block, String label)` adicionado ao pacote virtual `kof.test`. O bloqueio era o `known-bugs` §549: o handler nativo de `try` vazava no caminho de saída normal, então este exato helper tomava o catch no caminho sem-exceção no nativo. O §549 agora está CORRIGIDO (novo IR `KofExcUnlink` no fim normal, x86 + cross riscv/aarch64, paridade no interpretador/JS; ver CHANGELOG). Prova: `KofTestingE2ETest` **7/7** em JVM + JS + Script + Native x86-64 + cross riscv64(qemu) + aarch64(qemu), paridade-por-golden com o oráculo JVM (RED pré-fix 3/7). Restava uma face nativa (`return`/`break`/`continue` DENTRO de um `try`, um dos quais crashava o próximo throw) catalogada como §551 — NÃO bloqueava esta fatia, e agora está ✅ CORRIGIDA 01/10 (profundidade por região + unlink relativo à cadeia).

> **Fatia 1 (POUSADA 30/09).** Pacote virtual pure-Kof `kof.test`
> (recurso `dev/kof/test.kf` + `CompilerTesting.java`, injetado flat no
> `import kof.test` explícito; mesmo mecanismo de `kof.pagination`/`kof.pairs`):
> `assertTrue`/`assertFalse`/`assertEqualInt`/`assertEqualString`/`assertNotEqualInt`/`fail`.
> Aditivo à superfície `test`/`assert` existente — **sem sintaxe nova**, sem runtime por
> alvo (só `throw` de `String`, já tratado pelos 4 alvos); diagnóstico útil (label +
> esperado/atual) em vez do `assertion failed` puro. `assertTrue` definido pelo usuário
> desliga a injeção (colisão é sinal, não silêncio). Prova: `KofTestingE2ETest` 5/5
> (JVM + JS + Script + Native x86-64 paridade-por-golden + colisão). Próximo:
> `assertThrows`, lifecycle e as faces de integração/browser (§11 fases 2–4).

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

**Estado (02/10):** os helpers primitivos pousaram incrementalmente — fatia 1 (`assertTrue`/`assertFalse`/`assertEqualInt`/`assertEqualString`/`assertNotEqualInt`/`fail`), fatia 2 (`assertThrows`), fatia 3 (`assertNotEqualString`/`assertEqualBool`/`assertNull`/`assertNotNull`), fatia 4 (`Long`/`Double`/`Float`), fatia 5 (`Byte`/`Short`/`Char` + `assertNotEqualBool`), fatia 6 (par genérico `assertEqual<T>`/`assertNotEqual<T>`, desbloqueado pela correção do `known-bugs` §553 / `D-EQ-UNBOUNDED-T`). O §4.1 está **completo**. Prova: `KofTestingE2ETest` 7/7 + `GenericEqualityE2ETest` 16/16 em JVM + JS + Script + Native x86-64 + riscv64/aarch64(qemu).

## 4.2 Lifecycle

**Estado:** ENTREGUE 01/10 (`CompilerDesugar` + `TestHarnessBuilder`).

Implementado sem sintaxe nova — usa a convenção existente de função `Void` de nível superior sem
argumentos:
- `beforeAll()`: executa uma vez antes de qualquer teste rodar (em `try/catch` reportando `beforeAll failed`)
- `afterAll()`: executa uma vez depois que todos os testes terminam
- `beforeEach()`: alias §4.2 do `setup()` existente (executa antes de cada teste; uma falha gera um `SKIP` nomeado)
- `afterEach()`: alias §4.2 do `teardown()` existente (executa via `finally` depois de cada teste)

Prova: `TestTagsE2ETest` (18/18 verde). Mantém o isolamento dos testes — nunca um mecanismo de estado global.

## 4.3 Isolamento

**Status:** ENTREGUE 01/10 (`TestHarnessBuilder`).

```text
teste A → estado isolado
teste B → estado isolado
```

Nunca `teste A → estado global → teste B depende de A`. Filesystem temporário, portas, banco e
recursos têm lifecycle explícito:
- Se `beforeAll()` falhar: todos os testes seguintes são pulados com um `SKIP <nome>: beforeAll failed`
  nomeado, a falha é contada, e o runner sai com código 1. Testes nunca rodam contra uma fixture
  compartilhada quebrada/ausente.
- Se `afterAll()` falhar: o erro é capturado, reportado (`afterAll failed: <e>`), a falha é contada,
  e o resumo é impresso com código de saída não-zero.
- Se `beforeEach()` / `setup()` falhar: aquele teste específico é marcado `SKIP` e não roda.
- `afterEach()` / `teardown()` sempre roda via `finally` para todo teste que o setup deixou executar.
- Se `afterEach()` / `teardown()` lançar, é **falha nomeada, contada, e a corrida continua** (`teardown failed: <e>`) — nunca um throw sem catch que aborta o harness após o primeiro teste (defeito medido, `known-bugs` §570, CORRIGIDO 02/10). Mesmo contrato do `afterAll()`.
- Declarar **os dois** `setup()` e `beforeEach()` (ou `teardown()` e `afterEach()`) é **recusado em compile-time** com o erro nomeado `TEST001` (`ambiguous test lifecycle: … they are the same hook; keep only one`). Os dois nomes são aliases de um hook, então manter ambos rodaria em silêncio só o último (defeito medido, `known-bugs` §577, CORRIGIDO 02/10).

Prova: `TestTagsE2ETest` (22/22 verde).

## 4.4 Testes parametrizados

Avaliar tabelas `entrada → esperado` — muito útil para parser, type checker, encoding, HTTP,
processamento de string e operações numéricas. Não duplicar dezenas de testes só porque os
inputs mudam.

> **DECIDIDO 06/10 (`D-MAINT-BATCH-0610`/D):** §4.4 é a primeira face autorizada do conjunto
> rule-6 restante deste plano (§4.6 test doubles e §5 harness depois). Continua infraestrutura
> de teste aditiva — sem mudança de linguagem/semântica; a superfície concreta segue a
> gramática do Kof e é definida na implementação.

**Status: LANDADO 06/10 (`D-MAINT-BATCH-0610`/D).** A superfície é o helper aditivo
`testRows(rows, label, body)` do `kof.test` — escrito em Kof, sem sintaxe/primitiva nova
(`D-KOF-FIRST` item 12), injetado flat no `import kof.test` explícito como os demais helpers.
Cada linha é um `List<String>` (as colunas); o `body` recebe a linha e asserta com os helpers
do §4.1:

```kof
import kof.test

test "square table" {
    testRows(listOf(listOf("1", "1"), listOf("2", "4"), listOf("3", "9")), "square", (r: List<String>) -> {
        var input = r.get(0).toInt()
        if (input * input != r.get(1).toInt()) {
            throw "expected " + r.get(1) + ", got " + (input * input)
        }
    })
}
```

As linhas rodam **isoladas** — uma falha não aborta as demais — e as falhas agregam numa **única**
mensagem nomeada (`<label>: N of M rows failed` + `row <i> <row>: <motivo>`), que o harness
reporta como `FAIL <test>: …` (throw de String, mesmo caminho nos 4 alvos). A tabela uniforme de
`List<String>` é o incremento completo e honesto: um `(T) -> Void` genérico é recusado em
compile-time (`SEM085`, ABI de erasure é da linha 1.0), então uma tabela por record tipado exigiria
um helper por tipo — cerimônia nenhuma. **Prova:** `TestRowsE2ETest` **7/7** (tabelas que passam/
falham, isolamento de linha, linha ruim nomeada) em JVM + JS + Native x86-64 + riscv64/aarch64(qemu);
não-regressão `KofTestingE2ETest` 7/7 + `StructuredTestE2ETest` 12/12 + `TestTagsE2ETest` 23/23
+ `GenericEqualityE2ETest` 16/16 + `AssertE2ETest` 5/5 + `StdCatalogTest` 11/11 = **74/74**.

## 4.5 Property-based (futuro)

Deixar a arquitetura pronta para `encode(decode(x)) == x` ou `parse(print(ast)) == ast` quando a
propriedade fizer sentido. **Não implementar antes de existir necessidade real.**

## 4.6 Test doubles

Suporte mínimo a fake/stub/spy/mock. Regra: se o comportamento real é barato e determinístico,
usar o comportamento real. Mockar principalmente fronteiras externas: serviço HTTP, filesystem,
clock, fonte aleatória, processo externo, banco.

> **DECIDIDO 06/10 (`D-MAINT-BATCH-0610B`/A):** escopo = **apenas seams de clock/random** —
> injetar um clock determinístico e uma fonte aleatória reprodutível; sem framework de
> mock/stub/spy.

**Status: LANDADO 06/10 (`D-MAINT-BATCH-0610B`/A).** A superfície são três helpers aditivos do
`kof.test`, escritos em Kof (sem sintaxe/primitiva nova, `D-KOF-FIRST` item 12):

```kof
import kof.test

// seam de clock: uma fonte de epoch millis que o teste controla, no lugar de time.now()
var clock = fixedClock(1000L)        // sempre 1000
var stepped = scriptedClock(listOf(10L, 20L, 30L))  // 10, 20, 30, depois repete 30

// seam de random: mesma seed => mesma sequência em todo backend e toda execução
var r = seededRandom(42)
var roll = r.next(6)
```

`fixedClock(millis)` congela um instante; `scriptedClock(times)` devolve a próxima medida por
chamada e, esgotada, repete a **última** (nunca lança, nunca inventa valor depois que o roteiro
acabou; lista vazia = 0). `seededRandom(seed)` é um LCG inteiro em Kof puro (multiplicador 32719,
módulo 32749, então o produto cabe em `Int` de 32 bits **sem overflow**) — um teste que dependa
de aleatoriedade ganha sequência reprodutível, e a falha volta igual a cada execução. O `rng` da
stdlib **não** é usado de propósito: ele tem gap cross (`RNG001`) e o arquivo inteiro do host
compila em todo alvo, então todo helper precisa ser suportado nos quatro.

**Prova:** `TestSeamsE2ETest` **7/7** (fixed/scripted/esgotamento, reprodutibilidade por seed entre
execuções) em JVM + JS + Native x86-64 + riscv64/aarch64(qemu); não-regressão `KofTestingE2ETest`
7/7 + `TestRowsE2ETest` 7/7 + `StructuredTestE2ETest` 12/12 + `TestTagsE2ETest` 23/23 +
`GenericEqualityE2ETest` 16/16 + `AssertE2ETest` 5/5 + `StdCatalogTest` 11/11 = **86/86**.

---

# 5. Harness de Integração

> **DECIDIDO 06/10 (`D-MAINT-BATCH-0610B`/B):** a superfície do harness vive na **biblioteca Kof**
> (`kof.test`) — ciclo de temp dir / server / db com cleanup via `try/finally`, injetada flat no
> `import kof.test` explícito. Sem superfície só-Java. AUTORIZADO; na fila depois do §4.6.

**Status: LANDED 06/10 (primeira fatia — ciclo de vida de temp dir + poll de prontidão); LANDED
07/10 (segunda fatia — ciclo de vida de conexão de banco).** A superfície é escrita em Kof
(`dev/kof/test.kf`), sem sintaxe/primitiva nova (`D-KOF-FIRST` item 12):

```kof
import kof.test
import kof.test.db

// start → test → cleanup, cleanup mesmo quando o corpo lança
withTempDir("build/tmp", (d: String) -> {
    File(Path(d).resolve("data.txt")).writeText("hello")
    assertEqualString("hello", File(Path(d).resolve("data.txt")).readText(), "ida e volta")
})

// conexão de banco: aberta, usada, fechada — mesmo quando o corpo lança
withDb("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", (h: String) -> {
    db.execute(h, "create table t(id int)")
    db.execute(h, "insert into t values (?)", 7)
    assertEqualString("{\"n\":1}", db.query(h, "select count(*) as n from t").get(0), "linhas")
})

// poll de prontidão limitado para um recurso que sobe assincronamente
var up = waitUntil(() -> File("build/tmp/ready").exists(), 40, 25)
```

`withTempDir(dir, body)` cria o diretório, roda o corpo e remove a árvore recursivamente num
`finally` (os dois caminhos). `removeTree(path)` é a remoção recursiva, Kof puro (`Directory.list()`
+ `File.delete()`): ela é anterior à correção do `known-bugs` §618, quando o `Directory.delete()`
só removia diretório vazio no JS — desde 08/10 o próprio `delete()` é recursivo nos 4 alvos (§618
CORRIGIDO), e o `removeTree` segue como a forma portátil em Kof puro que não precisa de backend. `waitUntil(probe, attempts, intervalMs)` sonda, dorme entre as tentativas e
devolve o último resultado — nunca lança, nunca inventa sucesso; `attempts <= 0` faz uma única
sonda. `withDb(url, body)` abre `db.connect(url)`, roda o corpo e fecha a conexão num `finally`
(os dois caminhos) — o par simétrico do open, então um teste de integração nunca deixa conexão
aberta. O corpo é `(String) -> Void` (o handle é opaco, tipo concreto — `(T) -> Void` é recusado
com `SEM085`), então o helper é injetado flat em todo alvo. **Ele mora num host opt-in separado
`kof.test.db`, não no `kof.test`** (precedente `CompilerWeb` vs `kof.pagination`): o `withDb` chama
`db.connect`/`db.close`, e o native cross liga libsqlite3 **por uso**
(`NativeCrossLink.needsSqlite` varre o asm podado por `call sqlite3_*`). Como o `kof.test` é
injetado flat inteiro, um helper de banco morando lá faria **todo** programa que importa
`kof.test` tentar `-lsqlite3` no cross (medido: `riscv64-linux-gnu-ld: cannot find -lsqlite3`); o
import separado faz só quem pede `kof.test.db` pagar o gap.

**Prova:** `IntegrationHarnessE2ETest` **7/7** (cria/escreve/lê, cleanup no sucesso, cleanup no
throw, poll limitado, sem rastro no disco) nos alvos JVM + JS + Native x86-64 + riscv64/aarch64(qemu)
— também é a guarda de regressão de que `import kof.test` sozinho **não** força sqlite no cross;
`DbLifecycleE2ETest` **3/3** (corpo roda, conexão fechada no sucesso e no throw — provado no JVM +
JS pelo banco H2 em memória ficar vazio após o helper, e no Native x86-64 pelo corpo + continuação
após o throw com os dados persistidos). RED-first: pré-fatia a sonda não compila
(`SEM015 Undefined function: 'withDb'`).

O helper de ciclo de vida de servidor **é** componível em Kof puro, ao contrário da nota anterior:
`app.listen(port)` bloqueia, mas o helper o sobe numa tarefa (`var h = spawn { app.listen(port) }`),
sonda a porta com um probe limitado de `http.get` até ela aceitar, roda o corpo e fecha o app num
`finally` — `app.close()` e então `await h` — tanto no caminho de sucesso quanto no de throw. Ele mora
num **host opt-in separado `kof.test.web`** (não em `kof.test`), precedente `CompilerTestDb`: depende de
`kof.web`/`kof.http` e da primitiva `spawn`, e `kof.test` é injetado FLAT inteiro, então um helper web
morando lá faria todo teste de unidade carregar a árvore web. `withServer(app, port, body)` recebe o
`app` **já configurado** (rotas registradas antes do helper) porque `app.listen` precisa começar depois
que as rotas existem; o corpo é `(String) -> Void` (a URL). **`app.close()` (`kof_web_close`) é um
símbolo de runtime só do JVM hoje**, então o helper é completo no JVM e os alvos cross reportam o gap
pré-existente `WEB001` honestamente em compile-time (R6 — nunca fallback silencioso, nunca vazamento);
no dia em que `kof_web_close` pousar em Native/JS o pin vira edição consciente. **Prova:** novo
`ServerLifecycleE2ETest` **3/3** — golden do ciclo JVM (o corpo vê seu próprio `pong`; a porta é
recusada depois do helper nos dois caminhos, i.e. o `finally` rodou), os alvos cross pinados no `WEB001`
honesto, e a guarda opt-in (`withServer` é indefinido sem `import kof.test.web`). RED-first: o helper
foi desbloqueado pelos fixes `known-bugs` §630 (tipagem de lambda com `return` aninhado) e §632
(descritor de parâmetro de tipo função pontuado); o §633 corrigiu o falso positivo `MEM014` que o helper
expôs.

Infraestrutura para subir recursos: servidor HTTP, banco, filesystem, processo, serviço externo.
Cada recurso tem `start → health check → test → cleanup`. **Nunca deixar processos ou portas
abertas após o teste.**

**Status: temp dir + db + servidor POUSADOS; `processo`, `config` e `variáveis de ambiente` restam
(fronteiras medidas, 08/10 — lane issues/tooling `192.168.15.30:9093`).** Os três ciclos de vida
pousados (`withTempDir`/`withDb`/`withServer`) são o escopo `D-MAINT-BATCH-0610B`/B da mantenedora. Os
recursos restantes do §5.1 estão bloqueados por *mecanismos ausentes*, não por uma API indefinida —
então são registrados como fronteiras em vez de um stub (Q7):

* **Ciclo de vida de processo (`withProcess(program, args, body)`) — NÃO expressável em Kof puro
  hoje.** A intenção é o par simétrico do `withDb`: dar `process.spawn` num filho, rodar o corpo e
  garantir `h.kill()` num `finally` (os dois caminhos). Dois bloqueios medidos: (1) o handle do spawn
  é um token interno `java.lang.Long` **sem tipo Kof nomeável** — `(Long) -> Void` e
  `(java.lang.Long) -> Void` falham (`SEM074`/`SEM014`; `Long` é o primitivo, não o handle), então o
  handle não pode ser parâmetro; (2) `process.spawn(program, List<String>)` é **recusado** (`SEM025`)
  — só o varargs fixo de String `process.spawn("prog", "a", "b")` tipa, então um helper não consegue
  repassar uma lista variável de argumentos. A fronteira honesta espelha o caso
  `app.close`/`WEB001`: nenhum helper pousa até o handle ser nomeável (o precedente
  `kof.process.Result` de `D-MAINT-BATCH-0610`/C) ou o spawn aceitar uma lista.
* **Variáveis de ambiente (`withEnv(name, value, body)`) — precisa de uma primitiva de set/restore que
  não existe.** A stdlib só *lê* (`config.env(key)`, `config.*`); não há equivalente a `setenv` para
  escrever uma variável e restaurá-la num `finally`, então um helper de ambiente com escopo não pode
  ser escrito em Kof. Adicioná-lo é **superfície nova da stdlib (regra 6)** — registrado para a
  mantenedora, nunca inventado.
* **Config (`withConfig(entries, body)`) — a mesma primitiva ausente, mais nenhuma superfície de
  escrita.** `config.get`/`config.has` leem do arquivo nomeado por `KOF_CONFIG` (default
  `kof.config`) e do ambiente do processo (`RuntimeConfig1`/`RuntimeConfig2`,
  `NativeRiscvAsmConfig3`); um config de teste com escopo precisa então *escrever* `KOF_CONFIG` (ou
  um arquivo que o leitor pegue) e *restaurá-lo* num `finally` — i.e. a mesma primitiva `setenv` que
  o helper de ambiente precisa. O `config` não tem escritor nenhum hoje, então um helper não semeia
  um valor em Kof puro; registrado para a mantenedora junto com `withEnv`, nunca inventado.

Todos os três são aditivos/library-first quando o mecanismo existir; nenhum bloqueia o harness pousado.

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

> **DECIDIDO 06/10 (`D-MAINT-BATCH-0610B`/C):** o provider de browser (Playwright/Cypress) é
> **opt-in por projeto** — declarado por projeto, a CLI **não** o empacota (interop-first R9, sem
> dependência pesada por padrão). AUTORIZADO; na fila depois do §5.

**Status: POLÍTICA REGISTRADA 08/10 (só docs, lane issues/tooling `192.168.15.30:9093`).** A
política do §6 fica fixada por três decisões e registrada aqui: **(C)** opt-in por projeto — a CLI
**não** empacota Playwright/Cypress; **(T1)** o provider de browser deve servir **todos os alvos**
(JVM + JS + Native), não só JVM; **(T2)** `kof.test` segue como **feature do compilador/CLI**, não
um namespace da stdlib (`StdCatalog` inalterado). **Fronteira honesta — nenhuma API de browser
pousa ainda:** toda a superfície do §6 (abstração/SPI de browser, provider Playwright, locators,
assertivas web, interceptação de rede, matriz de capacidades) é a **fatia do provider**, ainda
gated pela decisão aberta da regra 6 (§12): *como* os providers são declarados, versionados e
gated (interop-first R9). Este documento registra a política; **não** promete uma API antes dessa
decisão — sem stub, sem superfície falsa (Q7). A semente é o `KofJsBrowserE2ETest` (um mecanismo
cru: Chrome real `--headless --dump-dom`, `safaridriver` W3C WebDriver no macOS), não uma abstração.
O padrão SPI/manifest do provider cruza com `kof-connector-ecosystem-plan.md` (§14). Os valores da
matriz de capacidades seguem `?` até serem descobertos na implementação (§6.4) — nunca assumidos.

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

**Descoberta (ENTREGUE 02/10, `known-bugs` §576):** um `.kf` descoberto que não declara `test`
nem um `main` top-level é um **módulo auxiliar** (helpers compartilhados), não uma suíte. O
`kof test` o pula com `SKIP <arquivo> (no tests, no main)` e conta como skip — nunca como
pass/fail. Uma corrida em que todo arquivo é módulo auxiliar sai 1 (`no runnable test or program
file found`); zero arquivos executáveis não é sucesso. Um arquivo com `main` e sem testes segue
rodando como programa (contrato preservado), e o stdout do programa é mantido — impresso antes do
`PASS`, casando a perna JS (defeito medido, `known-bugs` §578, CORRIGIDO 02/10). Isso exige que o
compilador exponha `CompilerDriver.hasMainEntryPoint()`, setado uma vez por unidade pelo passo de
desugar de testes.

**Validação da declaração:** a declaração `test` recusa um NOME vazio e uma TAG vazia com
`PARSE010` (`test name must not be empty` / `test tag must not be empty`) — um teste sem nome
rodaria como `PASS ` sem identidade (defeito medido, `known-bugs` §579, CORRIGIDO 03/10).

**Sondagens negativas medidas (03/10, sem defeito — não re-sondar):** (a) um alias `.kf`
**simbolizado** (`alias.kf -> real.kf`) é descoberto e rodado como arquivo próprio, então o mesmo
teste reporta duas vezes — é descoberta duplicada por caminho, não violação de contrato, e o
`Files.walk` não segue symlinks de diretório (um loop `self -> .` / `up -> ..` não trava). (b) Nomes
de teste **duplicados** num arquivo rodam ambos e são ambos reportados (`PASS same` / `FAIL same:
assertion failed`); são declarações distintas, não sobrescrita silenciosa. (c) Um **corpo de teste
vazio** passa (`PASS nothing`). (d) Um `test` **aninhado dentro de uma função** é recusado `SEM011`
(`Undefined variable or type: 'test'`) — declarações de teste são só top-level, como pretendido.

## 7.1 Tagging

Categorizar testes: `unit`, `integration`, `e2e`, `slow`, `browser`, `network`, `database`,
`native`, `jvm`, `js`, `wasm`, `security` — permitindo filtros eficientes.

**Status:** POUSADA 26/09 (X8 fatia 3) — as tags são declaradas no primitivo
(`test "nome", "smoke" { }`) e `kof test --tag <t>` filtra em COMPILE-TIME (propriedade de sistema
`kof.test.tag`; o harness sintetizado é gerado uma vez e todo alvo roda o mesmo catálogo filtrado,
paridade rule-5 por construção). Um filtro que não casa **nada** num arquivo é um no-op honesto
(exit 0, o harness imprime `kof test: tag '<t>' (0 of N)` / `no tests with tag '<t>' (of N)`) — um
filtro de tag não é um gate que quebra o build.

**Arquivos sem match são SKIP, nunca passed (defeito medido `known-bugs` §587, CORRIGIDO 04/10):**
o `CmdTest` contava todo arquivo cujo harness saía 0 como `passed`, então um arquivo cujos testes
todos falhavam o filtro imprimia `suite b: 1 passed, 0 failed` / `2 passed, 0 failed` — um falso
verde indistinguível de um passe real. O `CompilerDriver.TestInfo` agora expõe as `tags` declaradas,
e um arquivo sem match é `SKIP <arquivo> (no tests with tag '<t>')`, contado em `skippedByTag` e
excluído de `passed`.

**Multi-tag (POUSADA 06/10, lane issues/tooling `192.168.15.30:9093`):** o valor de `--tag` é uma
lista separada por vírgula e casa por **disjunção (OR)** — `kof test --tag smoke,ui` mantém todo
teste que carregue *qualquer* uma das tags listadas; um valor simples (sem vírgula) é o caso de uma
tag só e mantém o contrato histórico byte a byte (regra 2). Espaços em volta de cada tag são
ignorados; um item vazio é descartado. O parse vive em `TestHarnessBuilder.matchesAnyTag` (catálogo
em compile-time) e é espelhado em `CmdTest.hasTagMatch` (para o veredito SKIP de zero-match do §587
concordar com o harness). Prova RED-first: novo `TestTagsMultiE2ETest` **4/4** (união, trim, tag
desconhecida na lista, no-op honesto de todas desconhecidas; pré-fix **3 RED** com o match de tag
única antigo), `TestTagsE2ETest` **23/23** inalterado e `CmdTestTagTest` **7/7** (era 6 — a perna de
CLI `--tag smoke,ui`). **Prova de paridade rule-5 no Native x86-64:** `TestTagsNativeE2ETest` **2/2**
compila o harness multi-tag para `Target.NATIVE` e afirma o MESMO catálogo filtrado (`kof test: tag
'smoke,ui' (2 of 3)`) mais o no-op honesto de todas desconhecidas; os alvos nativos cross não são alvos
de teste anunciados (`kof test --target jvm|native|js`) e o main do harness não linka `kof_process_exit`
lá. **Recusa de alvo cross (POUSADA 06/10, `known-bugs` §615):** `kof test --target
native.risc`/`native.arm` era suporte falso (compilava e então morria com um erro cru `undefined
reference to 'kof_process_exit' [COMP001]`); o `CmdTest` agora recusa ambos cedo com mensagem nomeada
apontando `--target jvm|native|js` e `kof build --target native.<arch>` + qemu (a suíte E2E do
compilador é o runner cross). Prova RED-first: `CmdTestCrossTargetRefusalTest` **2/2**.
**Negação segue como trabalho futuro** (precisa de uma sintaxe para distinguir
"não esta tag" de uma tag literalmente chamada com um `!`; não decidido).

## 7.2 Paralelismo

Unit: paralelo por padrão quando isolado. Integração: controlado. E2E: por browser/context/projeto
quando seguro. Nunca compartilhar portas, banco, filesystem, sessão, cookies ou estado global.

## 7.3 Timeouts

Todo teste tem timeout razoável: separar unit, integração, e2e e ação de browser. Nunca deixar um
teste travar indefinidamente. (Semente: `CmdTest --timeout`, `CmdTestTimeoutTest`.)

**Status:** um `--timeout <sec>` global LANDOU (JVM/Native via `Process.waitFor` + `destroyForcibly`;
JS best-effort em processo com `Thread.join`). Timeouts **por nível** seguem abertos — precisam do
conceito de nível, ainda ausente no runner. A **medição de duração** complementar (tempo por arquivo +
arquivo mais lento) LANDOU 08/10 e está registrada no §8.5.

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

**Status: PRIMEIRA FATIA POUSADA 08/10 (lane issues/tooling `192.168.15.30:9093`).** O runner agora
mede o tempo de parede por arquivo e imprime uma linha aditiva após o sumário —
`time: <total>ms total, slowest <file> (<ms>ms)` — a base da **identificação de testes lentos**. Sai
no caminho de sucesso **e** no de falha (antes do exit não-zero), então um arquivo lento/falho é
diagnosticável só pela saída da corrida; sem flag nova, sem conceito de nível, sem mudar as linhas
históricas de sumário/suíte (byte-compatível — as asserções existentes seguem verdes). O recorte por
**nível** (Unit/Integration/E2E) e o relatório legível por máquina seguem abertos: precisam do
conceito de nível que o runner ainda não tem (o eixo `--tag` é o único classificador hoje). Prova:
`CmdTestSuiteTest` **10/10** — novos `runnerReportsTotalTimeAndSlowestFile` + `timingIsPrintedBeforeAFailingExit`,
RED-first (ambos falham pré-fatia, sem a linha `time:`).

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

**Resolvidas 05/10 por `D-MAINT-BATCH-0510`** (poll de chat da mantenedora):

* **T2 — `kof.test` continua feature do compilador/CLI**, NÃO namespace da stdlib; o `StdCatalog`
  fica inalterado. (Era decisão aberta abaixo; agora decidida.)
* **T1 — o provider de browser E2E deve servir TODOS os alvos** (JVM + JS + Native), não só JVM.
  A declaração/versionamento da dependência Playwright/Cypress ainda landa com a fatia do provider.
  (Era decisão aberta abaixo; agora decidida.)

Ainda abertas (regra 6):

* **D-TESTING-PLATFORM** — abrir a frente e seu escopo ordenado.
* A **sintaxe exata da API de testes** (assertions, lifecycle, parametrização, locators) — aditiva
  ao `test`/`assert` existente; sem sintaxe estrangeira.
* **Política de providers**: Playwright/Cypress são dependências externas pesadas — como são
  declaradas, versionadas e barradas (interop-first, R9). **A metade "vêm com a CLI ou são
  opt-in" está resolvida** pelo `D-MAINT-BATCH-0610B`/C (opt-in por projeto, a CLI não empacota);
  o mecanismo de declaração/versionamento/gate segue aberto e barra a fatia do provider do §6.
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

* `docs/development/test-architecture-plan.md` — suíte Java interna (L0–L5, perfis,
  performance). **Complementar, não duplicado.**
* `docs/development/wasm-wasi-plan.md` — KofWasm; o E2E cross-target/WASM da plataforma
  depende dele (`WASM001` até então).
* `docs/development/future/qrcode-wasm-plan.md` — outro consumidor da frente WASM.
* `docs/stdlib/kof-file-plan.md` — `kof.file` para helpers de upload/download.
* `docs/stdlib/kof-connector-ecosystem-plan.md` — providers (drivers de browser) são
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
