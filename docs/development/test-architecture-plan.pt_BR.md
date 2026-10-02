[English](test-architecture-plan.md) | [Português](test-architecture-plan.pt_BR.md)

# 🧪 Plano de Refatoração — Arquitetura e Modularização de Testes do Kof

**Status:** `UNDER DEVELOPMENT` — promovido de `future/` 28/09/2026 (`D-TEST-ARCHITECTURE-GO`, `D-FUTURE-BATCH-2809`, `D-FUTURE-PROMOTION`)
**Dono:** lane issues/tooling (esta sessão)
**Decisão:** `D-TEST-ARCHITECTURE-GO` (`DECISIONS.md`) — promoção autorizada "profiling → integration".
**Estado real (atualizado 30/09/2026):** a suíte são milhares de
arquivos `*Test.java` sem camadas/harness; o plano está em andamento. **Pousado:**
Fase 1 profiling (`scripts/test-suite-profile.sh` + `docs/testing/TEST-PERFORMANCE.md`),
Fase 2 auditoria de descoberta (`scripts/test-suite-audit.sh`) e Fase 2 **ratchet**
(`scripts/check_test_hygiene.sh` sobre o baseline congelado
`scripts/test-hygiene-baseline.txt`, **132 chaves, rc=0** — a medição de 30/09; a
cabeça da Fase 3 com 0 citações está esgotada, o próximo candidato tem 10 citações
de doc, e o cluster `dupname` restante exige o harness da Fase 5). **Fatia quick-win 1 (28/09):**
removida a chave `Thread.sleep` falso-positiva (menção só em comentário no
`AsyncSleepJsE2ETest`) e o settle redundante pós-`startServer` no
`KofWebHardeningTest` (o probe de readiness de porta já garante o bind).
**Fatia quick-win 2 (28/09):** o probe de readiness JVM duplicado
(`while (attempt < 40)` + `Thread.sleep(100)`, copiado em `KofWebE2ETest`,
`KofHttpE2ETest`, `KofHttpPoliciesE2ETest`, `KofWebStreamE2ETest`) vive agora uma
única vez em `TestServerFixture.awaitListening(Process, int)` → baseline 185→182
chaves (4 chaves de classe removidas, 1 do helper adicionada). **Fatia quick-win 3
(28/09):** o mesmo fixture absorveu os loops de readiness de `KofMediaE2ETest`,
`KofOAuthResourceServerTest`, `KofWebHardeningTest`, `KofWebSseE2ETest` e
`KofWebWsE2ETest`; o settle redundante de reconexão do SSE foi removido; e o teste
de corrida 503 de `maxConnections` deixou o sleep fixo por um poll limitado (estava
flaky: 1/3 verde) → baseline 182→179 chaves. **Correção honesta:** o settle
"redundante" da fatia 1 no `KofWebHardeningTest` fazia parte do timing dessa corrida
— o teste agora espera o 503 em vez de adivinhar. O custo visível é a latência de
feedback, não a correção (a suíte do reator está verde).
**Fatia quick-win 4 (28/09):** o `TestServerFixture` ganhou `awaitPort(port, attempts,
interval)` (só TCP) e `awaitListening(process, port, attempts, interval)` com orçamento
explícito; os loops de readiness puros restantes em `KofWebNativeE2ETest` (4),
`KofWebJsE2ETest` (3) e `KofBlogE2ETest` (1) agora os chamam em vez de probes manuais →
baseline 179→176 chaves. O fail-fast na morte do filho e o kill-no-timeout ficam dentro do fixture.
**Fatia quick-win 5 (29/09):** o `TestServerFixture` ganhou `awaitTrue(attempts,
interval, condition)` — um poll limitado para contadores/códigos de resposta que
trata um probe que lança como "ainda não pronto". O `KofWebHardeningTest` trocou
seus quatro settles fixos (`awaitStats` 20 ms, o poll de 503 50 ms, e os decrementos
de contador SSE/WS 1600/100 ms) por polls limitados; o `KofWebWsE2ETest` trocou o
settle "socket segue aberto" de 300 ms por um read com `setSoTimeout(300)` que deve
expirar → baseline 176→174 chaves. Ambos de-flake: os settles antigos adivinhavam a
margem do `time.sleep(1500)` do app.
**Fatia quick-win 6 (29/09):** o `TestServerFixture` ganhou
`await(process, port, attempts, interval, probe)` — um probe de readiness custom onde
`IOException` significa "ainda não pronto" e qualquer outra exceção aborta, então um
`AssertionError` dentro do probe ainda falha o teste; o `awaitListening` agora delega
a ele. O `KofWebTlsTest` trocou seus dois loops de readiness por handshake SSL por
`await`; o `KofLogE2ETest` trocou seus dois loops de readiness por `awaitListening` e
seus dois settles fixos `Thread.sleep` (300/400 ms) por polls limitados `awaitTrue`
sobre um stdout agora thread-safe (`StringBuffer`) — zerando as duas últimas chaves
`sleep` do E2E web/log. A contagem do baseline fica 174: as 2 chaves `sleep` removidas
são compensadas por 2 **leads** `dupname` (`assertManagedTargets`, `runCross`) que
entraram no conjunto congelado com as lanes kof-file/multiparadigma na mesma janela —
registrados, não escondidos.
**Fatia quick-win 7 (29/09):** os sleeps de readiness/teardown do E2E de CLI foram para um
novo `CliAwaitFixture` (`awaitTrue`, `awaitExit`, `pause`, infra de teste do `kof-cli`):
`ServePortTest` (2 loops de readiness + a espera de órfão §390), `ServeManifestPortE2ETest`
e `FullStackE2ETest` (readiness) e `CliDebugProcessLeakTest` (espera de órfão §438) agora
polls com deadline ou bloqueiam em `ProcessHandle.onExit()` em vez de `Thread.sleep` fixo
→ baseline 174→171 chaves (4 chaves de teste removidas, 1 do fixture adicionada).
`KofDebugJvmExceptionTest` mantém seus 500 ms — um "deixa o laço rodar antes de pausar"
intencional no fluxo DAP, não um settle de readiness.
**Fatia quick-win 8 (29/09):** `KofTimeE2ETest#durationSchedulerAtFiresJvm` foi re-medido e
reclassificado — seus dois `Thread.sleep(150/80)` NÃO eram timing de boot load-bearing, mas um
poll de scheduler: ambos viraram polls limitados `TestServerFixture.awaitTrue` (espera ≥3 disparos
num intervalo de 20 ms; depois assegura nenhum disparo nos 80 ms após `cancel`), e `TickCounter.n`
agora é `volatile` (lido de outra thread do scheduler). Baseline 171→170 chaves (1 arquivo sai do
conjunto sleep); `KofTimeE2ETest` 44/44 (0 skip), teste focado 4/4 execuções.
**Custo da Fase 3 encontrado (29/09):** a divisão de teste gigante NÃO é incremento barato — nomes de
classe de teste são citados como prova em `docs/` (ex.: `TranslateTest` em `known-bugs`, `audits/`,
`future/TRANSLATOR`), então dividir/renomear uma classe exige varredura de referências e arrisca drift
de doc. Isto agora é **medido, não adivinhado**: `scripts/test-suite-audit.sh --citations` conta, por
classe oversized, quantos arquivos sob `docs/` citam seu nome (`0` = divisão sem varredura de
citação). Cabeça medida do mais barato ao mais caro: `ArrayBoundsStressTest` (2),
`KofSetEqualityTest` (2), `SemanticResolutionTest` (4),
`CmdDeployTest`/`BiosBootE2ETest`/`KofInterpreterParityTest`/`NullablePrimitiveContractE2ETest` (8) …
`ConformanceMatrixTest` (42). Duas citações em `docs/bugs-and-gaps` de
`KofSetEqualityTest`/`ArrayBoundsStressTest` são contagens de classe ("`KofSetEqualityTest` inteiro
21/21"), que sofrem drift mesmo mantendo o método citado — então a regra barata da Fase 3 é: **mover
só testes não citados, manter métodos citados e o nome da classe no arquivo original, atualizar as
contagens**. **Primeira divisão landada (29/09):** o suporte reutilizável de
`KofSetEqualitySupport` (as quatro fontes Kof + os runners JVM/JS) foi extraído do
`KofSetEqualityTest` — os 21 casos e o método citado ficaram, então **zero drift de citação** — com
oversized 43→42 e baseline 170→169. **Segunda divisão landada (29/09):** `KofMathSupport` extraiu
os runners JVM/Native/JS + golden cross-arch sob qemu + guard de toolchain do `KofMathTest` (os 29
casos e o nome de classe citado no `conformance-matrix`/paridade ficaram) → oversized 42→41,
baseline 169→168. **Terceira divisão landada (29/09):** `ArrayBoundsStressSupport` extraiu os
runners JVM/JS/Native, os geradores de programa Kof e os oráculos de invariantes do
`ArrayBoundsStressTest` (os 15 casos e o nome de classe citado ficaram) → oversized 41→40,
baseline 168→167. **Quarta divisão landada (29/09):** `KofMediaSupport` extraiu os builders puros
de bytes WAV/MP4 (`makeWav`/`mp4Box`/`makeMp4`/`mp4Box64`/`makeMp4WithExtendedSizeBoxBeforeMoov`)
do `KofMediaE2ETest` (os 17 casos e o nome de classe citado ficaram) → oversized 40→39, baseline
167→166. **Quinta divisão landada (29/09):** `NullablePrimitiveContractSupport` extraiu os runners
JVM/SCRIPT/JS + o oráculo de alvo do `NullablePrimitiveContractE2ETest` (os 26 casos e o nome de
classe citado ficaram) → oversized 39→38, baseline 166→165. **Sexta divisão landada (29/09):**
`LambdaSupport` extraiu os runners JVM/Native/SCRIPT/JS do `LambdaE2ETest` (os 36 casos e o nome de
classe citado ficaram) → oversized 38→37, baseline 165→164. **Sétima divisão landada (29/09):**
`BiosBootSupport` extraiu os helpers de qemu/serial/build do `BiosBootE2ETest` (os 10 casos e o
nome de classe citado ficaram) → oversized 37→36, baseline 164→163. **Oitava divisão landada
(29/09):** `FfiStructSupport` extraiu a fonte do shim C, o compilador do host `.so`, a busca de
toolchain e os runners JVM/Native/JS do `FfiStructE2ETest` (os 12 casos e o nome de classe citado
ficaram) → oversized 36→35, baseline 163→162. **Nona divisão landada (29/09):** `ShellSupport`
extraiu o harness de captura JVM/JS e os oráculos de paridade/refusal (com o campo `@TempDir`
herdado) do `ShellE2ETest` (os 21 casos e o nome de classe citado ficaram) → oversized 35→34,
baseline 162→161. **Décima divisão landada (29/09):** `KofStringsSupport` extraiu os runners
JVM/Native/JS/qemu, o guard de toolchain e os dois maiores programas Kof inline (como constantes
`ALL_JVM`/`ALL_NATIVE`) do `KofStringsTest` (os 18 casos e o nome de classe citado ficaram) →
oversized 34→33, baseline 161→160. **Décima primeira divisão landada (29/09):**
`KofSwitchExprSupport` extraiu os runners JVM/Native/JS e hoisted os 32 programas Kof inline do
`KofSwitchExprE2ETest` para constantes nomeadas (os 32 casos e o nome de classe citado ficaram) →
oversized 33→32, baseline 160→159. **Décima segunda divisão landada (29/09):**
`KofInterpreterParitySupport` (harness) + `KofInterpreterParityPrograms` (os 8 maiores programas
Kof inline, hoisted) saídos do `KofInterpreterParityTest` (os 26 casos e o nome de classe citado
ficaram) → oversized 32→31, baseline 159→158. **Décima terceira divisão landada (29/09):**
`UiSupport` (runners) + `UiPrograms` (11 maiores programas Kof inline, hoisted) saídos do
`UiE2ETest` (os 29 casos e o nome de classe citado ficaram) → oversized 31→30, baseline 158→157.
**Décima quarta divisão landada (29/09):** `JvmSupport` (runner) + `JvmPrograms` (12 maiores
programas Kof inline, hoisted) saídos do `JvmE2ETest` (os 35 casos e o nome de classe citado
ficaram) → oversized 30→29, baseline 157→156. **Décima quinta divisão landada (29/09):**
`KofValidationSupport` (runners) + `KofValidationPrograms` (6 maiores programas Kof inline,
hoisted) saídos do `KofValidationTest` (os 34 casos e o nome de classe citado ficaram) →
oversized 29→28, baseline 156→155. **Décima sexta divisão landada (29/09):** `KofJsSupport`
(runners) + `KofJsPrograms` (24 programas Kof inline, hoisted) saídos do `KofJsE2ETest` (os 40
casos e o nome de classe citado ficaram) → oversized 28→27, baseline 155→154. **Décima sétima divisão landada (29/09):** `KofWebPrograms`
(19 programas Kof inline + o `WEB_APP` compartilhado, hoisted) saídos do `KofWebE2ETest` (os 28
casos e o nome de classe citado ficaram) → oversized 27→26, baseline 154→153. **Décima oitava divisão landada (29/09):** `KofScriptPrograms`
(8 programas Kof inline, hoisted) saídos do `KofScriptTest` (os 25 casos e o nome de classe
citado ficaram) → oversized 26→25, baseline 153→152. **Décima nona divisão landada (29/09):** `WorkflowPrograms`
(7 programas Kof inline, hoisted) saídos do `WorkflowE2ETest` (os 24 casos e o nome de classe
citado ficaram) → oversized 25→24, baseline 152→151. **Vigésima divisão landada (29/09):**
`DomainGapPrograms` (10 programas Kof inline, hoisted) saídos do `DomainGapCodesTest` (os 29 casos
e o nome de classe citado ficaram) → oversized 24→23, baseline 151→150. **Vigésima primeira
divisão landada (29/09):** `BackendParityPrograms` (5 programas Kof inline, hoisted) saídos do
`BackendParityTest` (os 19 casos e o nome de classe citado ficaram) → oversized 23→22, baseline
150→149. **Vigésima segunda divisão landada (29/09):** `ComponentCoreSupport` (runners) +
`ComponentCorePrograms` (28 programas hoisted) saídos do `ComponentCoreE2ETest` (os 29 casos e o
nome de classe citado ficaram) → oversized 22→21, baseline 149→148. **Vigésima terceira divisão
landada (29/09):** `SemanticResolutionSupport` (driver + oráculos SEM025/SEM050) +
`SemanticResolutionPrograms` (19 programas hoisted) saídos do `SemanticResolutionTest` (os 30 casos
e o nome de classe citado ficaram) → oversized 21→20, baseline 148→147. **Vigésima quarta
divisão landada (29/09):** `CmdDeploySupport` (16 métodos/records helper, extraídos) saídos do
`CmdDeployTest` (os 16 casos e o nome de classe citado ficaram) → oversized 20→19, baseline
147→146. **Vigésima quinta divisão landada (30/09):** `DomainGapParityMatrixTest` (o teste-ledger R6 +
`repoRoot`/`GAP_CODE`, extraídos) saídos do `DomainGapCodesTest` (os 31 casos de comportamento e o
nome de classe citado ficaram; 6 citações de doc do ledger moveram-se) — o arquivo tinha voltado a
passar de 500 com os pinos de `zip`/`NAT008` de 30/09 → oversized 492, baseline 146 (inalterado; a
classe não estava no baseline congelado). A métrica é guia, não oráculo:
nomear candidatos nesta fila (e no `README`) já
adiciona citações a uma classe, então **re-meça o `--citations` antes de escolher a próxima
divisão**. Essa regra + ordem é o todo da Fase 3 traçado.
**Como terminar:** Fase 1/2 descoberta feita — depois **modularização da Fase 3** (re-medir
`--citations`; extrair suporte e mover só testes não citados, mantendo métodos citados e nomes de
classe) e **remoções quick-win da Fase 2** intercaladas (encolher o baseline: sleeps / duplicação /
oversized) → 4 (harness) → 5 (alvos) → 6 (conformance) → 7 (`mvn verify`). **Infraestrutura de teste
pura — o compilador nunca é tocado** (regra de ouro abaixo). Uma fatia por commit, RED-first +
`check_500`.

## 📌 Visão Geral

Atualmente o repositório possui **milhares de testes**, mas eles não estão
organizados como uma arquitetura de testes. Eles foram crescendo junto com o
compilador.

O problema não é a quantidade.

O problema é que, ao longo do tempo, surgiram:

- testes repetidos;
- cenários equivalentes escritos de formas diferentes;
- testes gigantes tentando validar muitas coisas;
- classes de teste muito acopladas à implementação;
- testes de sintaxe misturados com testes de lowering;
- testes de lowering misturados com execução;
- execução E2E misturada com conformance;
- testes de stress convivendo com testes rápidos;
- suítes cujo feedback é lento.

Isso compromete três coisas:

1. a velocidade de desenvolvimento;
2. a confiabilidade do compilador;
3. a qualidade da engenharia do projeto.

## 🎯 Objetivo

Transformar os testes em um sistema organizado, modular, rápido e
determinístico.

A suíte deve deixar de ser apenas um grande volume de arquivos `*Test.java`
e passar a possuir camadas claras de validação.

## 🧠 Filosofia de Testes do Kof

Propor a seguinte filosofia oficial do projeto:

> Um teste não existe para provar que o código funciona.
>
> Um teste existe para impedir que uma decisão de engenharia seja perdida no
> futuro.

Consequentemente:

- todo bug corrigido permanece protegido;
- toda decisão de design permanece documentada;
- todo comportamento observável permanece validado;
- nenhum teste existe apenas para aumentar número.

## 🏗️ Arquitetura em Camadas

Propor uma arquitetura formal:

```
L0 - Unit Tests
    Parser
    Lexer
    AST
    Typer
    Semantic Analysis

L1 - Component Tests
    Lowering
    Codegen
    IR
    Optimizer
    Backend
    ABI

L2 - Target Execution
    JVM
    Native
    JavaScript
    Script
    Android

L3 - E2E
    Compilar
    Executar
    Comparar stdout
    Verificar exit code

L4 - Conformance
    sintaxe
    semântica
    stdlib
    operadores
    runtime

L5 - Stress
    concorrência
    memória
    fuzzing
    carga
    estabilidade
```

## 🔥 Principais Problemas Identificados

### 1. Repetição massiva de estrutura

Atualmente muitos testes:

- criam compilador;
- carregam código;
- compilam;
- executam;
- conferem string.

Isso se repete em praticamente toda a suíte.

Propor a criação de um **Kof Test Harness** oficial, centralizando:

```
compile()
run()
expect()
expectOutput()
expectDiagnostic()
```

## 2. Falta de isolamento entre targets

Hoje os testes:

```
JVM
Nativo
JS
Script
```

acabam convivendo no mesmo repositório de testes sem fronteiras explícitas.

Propor separação formal:

```
compiler/
native/
jvm/
js/
script/
shared/
conformance/
```

## 3. Testes gigantes

Existem arquivos com centenas de cenários.

Isso dificulta:

- depurar;
- executar isoladamente;
- medir tempo;
- descobrir regressões.

Propor divisão por responsabilidade.

## 4. Ausência de perfis de execução

Atualmente o desenvolvedor praticamente executa tudo.

Deveriam existir perfis:

### Fast

```
mvn test -Pfast
```

Objetivo: feedback abaixo de ~30 segundos.

Deve conter:

- Parser
- Lexer
- Typer
- Lowering
- Unit
- Component

### Integration

```
mvn test -Pintegration
```

Contém:

- targets
- execução
- golden
- ABI

### Full

```
mvn test
```

Tudo.

### Stress

```
mvn test -Pstress
```

Contém:

- concorrência
- memória
- fuzzing
- estabilidade

Essa separação evita que testes de 10 minutos ditem o ritmo do dia a dia.

## 5. Falta de rastreabilidade

Hoje não existe mapa fácil entre:

- feature
- teste
- bug
- decisão

Propor que cada família de testes declare:

```
Feature:
Records
Pattern Matching
Generics
FFI
```

e:

```
Cobertura:
Parser
Typer
Lowering
JVM
Native
JS
```

## 6. Golden tests

Propor a criação de uma suíte oficial **Golden Suite**.

Ela deveria conter:

- exemplos reais;
- código compilado;
- saída esperada;
- exit code;
- hash.

Objetivo:

```
mesmo código
↓
mesma saída
↓
em todos os targets
```

## 7. Testes de regressão

Hoje muitos bugs viram um único testcase.

Propor política oficial:

> Todo bug corrigido deve gerar:
>
> 1. Reprodução mínima
> 2. Teste de regressão
> 3. Referência permanente

O teste nunca deve ser removido.

## 8. Testes de compiler crash

Hoje muitos testes tentam reproduzir erros.

Falta uma suíte dedicada a Stability.

Ela deveria validar:

- parser nunca trava;
- lowering nunca lança exceção inesperada;
- typer nunca entra em loop;
- código inválido sempre produz diagnóstico;
- AST nunca fica inconsistente.

## 9. Testes determinísticos

Nenhum teste deve depender de:

- hora atual;
- rede;
- sistema operacional específico;
- disponibilidade de ferramenta externa sem guarda explícita.

Toda dependência externa deve ser protegida por guardas de ambiente honestas
(`assumeTrue` + gap documentado — R6, nunca pular silencioso).

## 10. Custos de feedback

Propor medição contínua.

Gerar relatório:

```
teste
tempo
falhas
estabilidade
```

Os testes mais lentos devem ser monitorados permanentemente.

## 🧪 Estratégia de Refatoração

A refatoração NÃO deve alterar o compilador.

Ela deve alterar apenas a infraestrutura de testes.

### Fase 1 — Profiling

Instrumentar toda a suíte.

Descobrir:

- tempo por classe
- tempo por target
- testes duplicados
- testes redundantes
- testes instáveis

### Fase 2 — Quick Wins

**Estado (28/09):** descoberta + guarda POUSADAS — `scripts/test-suite-audit.sh`
mede os leads (sleeps / oversized / nomes duplicados); `scripts/check_test_hygiene.sh`
é o ratchet sobre o baseline congelado `scripts/test-hygiene-baseline.txt`
(`--write-baseline` só depois de melhorar). As remoções abaixo são o trabalho aberto
(encolher o baseline, depois regravar).

Remover:

- repetição;
- sleeps;
- loops desnecessários;
- setup redundante.

### Fase 3 — Modularização

Separar camadas:

```
compiler tests
backend tests
target tests
conformance tests
stress tests
```

### Fase 4 — Harness

**Primeira fatia landada (29/09):** os helpers de layout de bytes de mídia duplicados
(`be32`/`type4`/`le16`/`le32`/`clipMp4`/`clipZeroSizeMp4`/`wav`) de `MediaCrossE2ETest` e
`MediaNativeE2ETest` foram consolidados numa base compartilhada `MediaByteSupport` — eliminando 3
chaves `dupname` do ratchet (ambas as classes seguem verdes, zero drift de citação). **Segunda
fatia da Fase 4 (29/09):** `KofCSupport` consolidou o harness duplicado do compilador C
(`has`/`requireTools`/`run`/`assertAllPrograms`/`compile` + o record `Prog`) entre os 4
`KofC*CompilerTest` — removendo 2 chaves `dupname` (`assertAllPrograms`, `requireTools`;
`has`/`run`/`compile` persistem declarados noutros módulos), as 4 classes verdes. **Terceira
fatia da Fase 4 (29/09):** os 3 `@Test` do laço de golden compartilhados por
`KofCParamsCompilerTest`/`KofCStructCompilerTest` foram para uma base intermediária
`KofCGoldenSupport` (só elas a estendem) — removendo mais 3 chaves `dupname`. Harness 146→**138**
(8 chaves eliminadas nas três fatias). **Quarta fatia da Fase 4 (29/09):** os 3 `@Test` de nível de
log idênticos (`errorLevelSuppressesInfo`/`offSuppressesEverything`/`warnGoesToStderr`)
compartilhados por `KofLogE2ETest`/`NativeLogE2ETest` foram para uma base `LogLevelSupport` (cada
subclasse fornece o seu `run`); mais 3 chaves `dupname` removidas, contagens preservadas (11/7).
Harness 146→**135** (11 chaves eliminadas nas quatro fatias). **Quinta fatia da Fase 4 (29/09):**
os helpers de frame WebSocket idênticos (`writeMaskedFrame`/`readFully`, com `MASK`) compartilhados
por `KofWebHardeningTest`/`KofWebWsE2ETest` foram para uma base `WsFrameSupport`; mais 2 chaves
`dupname` removidas. Harness 146→**133** (13 chaves eliminadas nas cinco fatias). **Sexta fatia da Fase 4 (29/09):**
o helper `javac` idêntico de `CompareTest`/`MigrateTest` (kof-cli) foi para uma base
`CliJavacSupport` — mais 1 chave `dupname`. Harness 146→**132** (14 chaves eliminadas nas seis
fatias).

**Harness de pares idênticos da Fase 4 ESGOTADO (29/09):** seis fatias reduziram o ratchet
146→132 consolidando métodos com corpo **byte-idêntico** em exatamente duas classes (bytes de
mídia, harness C, laço de golden C, nível de log, frames WebSocket, `javac`). A auditoria confirma
**zero pares idênticos restantes**; todo `dupname` restante é um teste/harness compartilhado POR
DESENHO entre dois alvos (ex.: golden de `KofCParamsCompilerTest`×`KofCStructCompilerTest`,
`execArithmetic` em `JvmE2ETest`×`KofJsE2ETest`, `native*MatchesJvmGolden` em
`NativeAarch64`×`NativeRiscv64`). Consolidá-los exige um **harness cross-target parametrizado por
alvo** (Fase 5) — incremento de design, não refactor de risco zero; forçá-lo esconderia o alvo sob
hocks de abstração e arriscaria as contagens por alvo citadas. Ferramenta para dimensionar:
`scripts/test-suite-audit.sh --dups` (read-only) lista cada nome duplicado com as classes que o
declaram; os topos atuais são `main` (32 classes), `assumeToolchain` (26), `copyLibrary` (16),
`stopServer` (12), `jvmOracle` (12).

Nota da Fase 3 (29/09): extração pura esgotada — **43 oversized → 19** em 24 divisões, todas com
zero drift; o restante ou pertence a lane ativa ou exige mover testes (citation sweep). **Reaberta
uma vez por regrowth (30/09):** o `DomainGapCodesTest` passou de 500 de novo com os pinos
`zip`/`NAT008` — a contagem de chaves `oversized` 19→20 (uma chave NOVA, RED) — uma vigésima quinta
extração pura (`DomainGapParityMatrixTest`, o ledger R6) devolveu-o a 492 → a contagem de volta a
**19**.

Criar infraestrutura oficial.

### Fase 5 — Targets

Separar execução:

```
JVM
Native
JS
Script
```

### Fase 6 — Conformance

Criar suíte oficial de equivalência.

### Fase 7 — Integração

Implantar:

```
mvn verify
```

ou equivalente.

## 📊 Meta

Após a refatoração:

- feedback rápido;
- menos redundância;
- arquitetura testável;
- maior confiança em releases;
- regressões mais fáceis de investigar;
- testes que explicam decisões;
- suíte que acompanha o crescimento do Kof.

## Diagrama da Arquitetura

```
Kof Test Suite
        │
        ├── Unit
        │
        ├── Component
        │
        ├── Backend
        │
        ├── Target
        │      │
        │      ├── JVM
        │      ├── Native
        │      ├── JavaScript
        │      ├── Script
        │      └── Android
        │
        ├── E2E
        │
        ├── Conformance
        │
        ├── Golden
        │
        ├── Fuzzing
        │
        └── Stress
```

## Regra de ouro

> "O compilador pode mudar de arquitetura.
>
> A suíte de testes não."

Essa frase resume exatamente a direção que estamos seguindo.

## Conclusão

A suíte de testes do Kof não deve ser tratada como código secundário.

Ela é um dos principais ativos de engenharia do projeto.

Depois de consolidarmos o compilador, essa é uma das maiores oportunidades de
evolução da qualidade do Kof.

Proposta adicional: ao final da refatoração, gerar um documento permanente:

```
docs/testing/TEST-PERFORMANCE.md
```

registrando métricas como:

- tempo total;
- tempo por camada;
- testes mais lentos;
- testes mais instáveis;
- evolução do runtime da suíte.

## Próximo Passo

Antes de qualquer refatoração profunda, o caminho é:

1. medir a suíte inteira (`scripts/test-suite-profile.sh`, Fase 1 — ferramenta
   POUSADA; resultados em `docs/testing/TEST-PERFORMANCE.md`);
2. identificar os 20 testes mais lentos (o profiler os ranqueia);
3. procurar duplicações (Fase 2 — descoberta + ratchet POUSADAS:
   `scripts/test-suite-audit.sh` + `scripts/check_test_hygiene.sh`; trabalho =
   encolher `scripts/test-hygiene-baseline.txt` via remoções quick-win — autoridade
   atual = **132** chaves não-comentário, por `scripts/test-hygiene-baseline.txt`);
4. propor modularização (Fase 3 — iniciada: `--citations` mede o custo de divisão por classe
   oversized e a regra de drift está fixada; quatro divisões landadas = `KofSetEqualitySupport`
   do `KofSetEqualityTest` (21/21 mantidos), `KofMathSupport` do `KofMathTest` (29/29 mantidos),
   `ArrayBoundsStressSupport` do `ArrayBoundsStressTest` (15/15 mantidos), `KofMediaSupport` do
   `KofMediaE2ETest` (17/17 mantidos), `NullablePrimitiveContractSupport` do
   `NullablePrimitiveContractE2ETest` (26/26 mantidos), `LambdaSupport` do `LambdaE2ETest`
   (36/36 mantidos), `BiosBootSupport` do `BiosBootE2ETest` (10/10 mantidos) e `FfiStructSupport`
   do `FfiStructE2ETest` (12/12 mantidos), `ShellSupport` do `ShellE2ETest` (21/21 mantidos) e
   `KofStringsSupport` do `KofStringsTest` (18/18 mantidos) e `KofSwitchExprSupport` do
   `KofSwitchExprE2ETest` (32/32 mantidos) e `KofInterpreterParitySupport`/`...Programs` do
   `KofInterpreterParityTest` (26/26 mantidos), `UiSupport`/`UiPrograms` do `UiE2ETest` (29/29
   mantidos), `JvmSupport`/`JvmPrograms` do `JvmE2ETest` (35/35 mantidos) e
   `KofValidationSupport`/`...Programs` do `KofValidationTest` (34/34 mantidos) e `KofJsSupport`/`KofJsPrograms` do `KofJsE2ETest` (40/40
   mantidos) e `KofWebPrograms` do `KofWebE2ETest` (28/28 mantidos) e `KofScriptPrograms` do `KofScriptTest`
   (25/25 mantidos) `WorkflowPrograms` do `WorkflowE2ETest` (24/24 mantidos) e `DomainGapPrograms` do
   `DomainGapCodesTest` (29/29 mantidos) e `BackendParityPrograms` do `BackendParityTest` (19/19
   mantidos) e `ComponentCoreSupport`/`ComponentCorePrograms` do `ComponentCoreE2ETest` (29/29
   mantidos) e `SemanticResolutionSupport`/`SemanticResolutionPrograms` do `SemanticResolutionTest`
   (30/30 mantidos) e `CmdDeploySupport` do `CmdDeployTest` (16/16 mantidos) → oversized 43→19,
   baseline 170→146 no estágio da Fase 3; o ratchet do harness continuou **146→132** na Fase 4
   e está ESGOTADO — nenhuma nova divisão está na fila).

**Importante:** essa refatoração não deve interferir em nada no compilador. É
puramente de infraestrutura de testes (regra de ouro). A frente está aberta
(`D-TEST-ARCHITECTURE-GO`); as Fases 1–4 estão CONCLUÍDAS (oversized 43→18; ratchet do harness 146→132, zero pares idênticos restantes) — o único trabalho aberto é a Fase 5 (harness cross-target parametrizado por alvo), que precisa de decisão.
