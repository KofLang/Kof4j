[English](test-architecture-plan.md) | [Português](test-architecture-plan.pt_BR.md)

# 🧪 Plano de Refatoração — Arquitetura e Modularização de Testes do Kof

**Status:** `UNDER DEVELOPMENT` — promovido de `future/` 28/09/2026 (`D-TEST-ARCHITECTURE-GO`, `D-FUTURE-BATCH-2809`, `D-FUTURE-PROMOTION`)
**Dono:** `192.168.15.30:9092` (lane compiler/JVM — claims do split de higiene + matriz de paridade R6; UM plano, UM dono por `D-PLAN-ONE-OWNER`)
**Decisão:** `D-TEST-ARCHITECTURE-GO` (`DECISIONS.md`) — promoção autorizada "profiling → integration".
**Estado real (atualizado 30/09/2026):** a suíte são milhares de
arquivos `*Test.java` sem camadas/harness; o plano está em andamento. **Pousado:**
Fase 1 profiling (`scripts/test-suite-profile.sh` + `docs/testing/TEST-PERFORMANCE.md`),
Fase 2 auditoria de descoberta (`scripts/test-suite-audit.sh`) e Fase 2 **ratchet**
(`scripts/check_test_hygiene.sh` sobre o baseline congelado
`scripts/test-hygiene-baseline.txt`, **117 chaves, rc=0** — 132 na medição de
30/09, apertado pela extração da Fase 3 de 02/10; a cabeça da Fase 3 com 0 citações está esgotada, o próximo candidato tem 10 citações
de doc, e o cluster `dupname` restante exigia o harness da Fase 5; as fatias da Fase 5 de 03/10–08/10 apertaram `jvmOracle` 131→130, `stopServer` 130→129, `assertRuns` 129→128, `runScript` 128→127, `runKof` 127→126, `assertBoth` 126→125, `copyLibrary` 125→124, `runBoth` 124→123, `runAll3` 123→122, `assumeToolchain` 122→121, `assumeAarch64` 121→120, `assumeCross` 120→119, `runQemu` 119→118 e `runQemuE` 118→117). **Fatia quick-win 1 (28/09):**
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
**Extração da Fase 3 (02/10):** o `DepsRegistryTest` (509 linhas, uma chave `oversized` do ratchet)
cedeu seu harness compartilhado a um novo `DepsRegistrySupport` (fake server do GitHub Releases,
construtor do pacote D2-A, runner de subprocesso CLI) — o padrão consagrado da Fase 3 (`abstract
class …Support`, a classe de teste o `extends`). `DepsRegistryTest` 509→303, `DepsRegistrySupport`
230; as 4 classes vizinhas que fazem `import static dev.kof.cli.DepsRegistryTest.*` continuam
resolvendo por herança (zero drift de citação). Prova: `DepsRegistryTest` 13/13 +
`DepsRegistryTrustTest` 5/5 + `DepsSourceModuleTest` 8/8 + `CmdDeploySourcesTest` 5/5 = **31/31
verdes**; baseline do ratchet 132→**131** chaves (oversized 19→18). (Os `DepsRegistryTest$*.class`
órfãos do layout aninhado antigo tiveram de ser purgados do `target/test-classes` antes da corrida
focada — o compilador incremental do maven não os apaga.)
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

**Primeira fatia da Fase 5 ENTREGUE (03/10, `D-TEST-ARCHITECTURE-PHASES`):** o
harness cross-target byte-idêntico da família `NativeIo*CrossTest` (15 classes:
bytes/dir-delete/dir-list/fs/mkdirs/copy/move/normalize/path/read-range/resolve/
size/stat/text/to-absolute) foi consolidado numa base parametrizada por alvo
`NativeCrossSupport` — `has` (guard de toolchain), `capture`, `runJvm` (oráculo
JVM) e `runCross` (compila + qemu, parametrizado por `Target`), mais as
conveniências de instância `runJvm`/`runCross`/`runNative` e `base` usadas por
copy/move. ~716 linhas de helper duplicadas removidas (família 1995→1172, mais a
base de 107; `NativeIoCopyCrossTest` 147→75). **Cada alvo continua um `@Test`
real e nomeado por alvo** (JVM / riscv64 / aarch64): só o mecanismo de execução é
compartilhado, as contagens por alvo são preservadas. Prova: **54/54**
`NativeIo*CrossTest` + `NativeCrossWideArgsE2ETest` verdes (o cross
riscv64/aarch64 realmente executou, não pulou); ratchet do harness fica em **131**
chaves com zero dívida nova. Trabalho restante da Fase 5: o `@Test` de oráculo
JVM compartilhado (`jvmOracle`, 13 declarações) e as demais famílias cross-target
(`main`, `assumeToolchain`, `copyLibrary`, `stopServer`) — incremento de design,
adiado para manter esta fatia de risco zero.

**Segunda fatia da Fase 5 ENTREGUE (03/10):** os 13 `@Test` `jvmOracle`
byte-idênticos (12 `NativeIo*CrossTest` + `NativeCrossWideArgsE2ETest`) foram
para uma nova base `NativeIoJvmOracleSupport` — a subclasse fornece
`jvmOracleSource(tempDir)` + `jvmOracleExpected()`, a base tem o `@Test`. Cada
subclasse mantém o seu fonte/golden, então nenhuma asserção por alvo ou por face
fica escondida. Prova: **54/54** `NativeIo*CrossTest` +
`NativeCrossWideArgsE2ETest` verdes (o cross riscv64/aarch64 executou); a chave
`dupname jvmOracle` do ratchet foi **eliminada** (baseline 131→130). As 4 classes
com shape JVM não-padrão (`copy`/`move`/`text`/`metadata`) mantêm o seu próprio
`@Test` em `NativeCrossSupport`; `NativeIoMetadataE2ETest` (mesma família) também
foi movida para `NativeCrossSupport` (4/4 verdes). Restante: `main` (em sua maioria fonte Kof em text blocks,
pista falsa), `assumeToolchain` (26 assinaturas divergentes) e `copyLibrary` (35
classes, dois shapes) — cada uma exige o seu próprio incremento limitado.

**Fatia 3 da Fase 5 ENTREGUE (03/10):** o `@Test` `targetGapRefusal` duplicado
(`CryptoSignE2ETest` × `KeyExchangeE2ETest`, mesmo nome/código nomeado diferente
— introduzido pela lane D-KOF-SIGN em `9d2f5c81e`, que deixou o gate de higiene
RED) foi consolidado numa nova base `TargetGapRefusalSupport`: a subclasse
fornece `gapProgram()`/`gapCode()`/`gapLabel()`, a base tem o `@Test`.
`CryptoSignE2ETest` 3/3 + `KeyExchangeE2ETest` 6/6 verdes; `check_test_hygiene`
de volta a rc=0 (130 chaves, 0 dívida nova).

**Fatia 4 da Fase 5 ENTREGUE (03/10):** o teardown duplicado de processo-filho
(`private Process serverProcess;` + `@AfterEach stopServer()` — destroy → wait 5s
→ destroyForcibly) copiado em 11 classes E2E que sobem servidor
(`KofHttp*`/`KofWeb*`/`PaginationPageRequestE2ETest`/`KofMediaE2ETest`) foi
consolidado numa nova base `ServerProcessSupport`, que possui o campo e o único
`@AfterEach`. As 7 classes sem outra base estendem-na direto; as 4 que já
estendem `WsFrameSupport`/`KofWebPrograms`/`KofMediaSupport` agora a alcançam por
esses suportes (cada um re-baseado em `ServerProcessSupport`) — então o nome do
método `stopServer` vive em exatamente UMA classe. O `@AfterEach` in-process do
`KofHttpServerTest` (fecha um `KofHttpServer`, não um `Process` filho) foi
renomeado `closeServer` para permanecer distinto; o comportamento é inalterado.
Nenhum corpo de teste, contagem de alvo ou asserção mudou. Prova: as 12 baterias
afetadas **118/118** verdes (web/http/media/paginação + `KofHttpServerTest`); a
chave `dupname stopServer` do ratchet foi **eliminada** — baseline re-congelada
130→**129**. O `cleanup()` do `KofOAuthResourceServerTest` (mesma parada de
processo mais um `jwksServer.stop`) foi deliberadamente deixado como está: não é
duplicata de `stopServer` e dobrá-lo exigiria um segundo gancho de teardown.

**Fatia 5 da Fase 5 ENTREGUE (03/10):** o helper de execução JVM `assertRuns` era
byte-idêntico em 7 classes E2E core (`FnTypeInGenericDeclaredTypeTest`,
`HeterogeneousListInferTest`, `ReduceStringCastTest`, `NestedFnTypeArityTest`,
`LambdaFieldCaptureTest`, `FnTypeFieldCallTest`, `PrimitiveStringEqTest`) — tanto
a sobrecarga de 3 argumentos quanto a de 2. Agora vive uma vez numa nova base
`JvmRunSupport` (que possui `runJvmMain` + as duas sobrecargas `assertRuns`);
cada uma das 7 classes estende-a. O helper homônimo do `KofCacheE2ETest` tem
forma diferente (`(Path, String, String, Target, String)`, compila e roda o alvo
escolhido) e foi renomeado `assertTargetRuns` para que o nome `assertRuns` não
fique sobrecarregado entre classes por acidente. Nenhum corpo de teste, alvo ou
asserção mudou. Prova: as 8 baterias afetadas **38/38** verdes; a chave `dupname
assertRuns` do ratchet foi **eliminada** — baseline re-congelada 129→**128**.

**Fatia 6 da Fase 5 ENTREGUE (03/10):** os helpers de execução multi-arquivo eram
byte-idênticos em 4 classes E2E core — `runScript(Path root, List<Path> sources,
String expected)` em `SealedTypeE2ETest`/`TypeVarianceE2ETest`/
`UseSiteVarianceE2ETest`/`InteropSchemaE2ETest` e `runJs(Path root, List<Path>
sources, String expected)` em 3 delas. Agora vivem uma vez numa nova base
`MultiSourceRunSupport` (que também possui o `driver` compartilhado), que as 4
classes estendem. Nenhum corpo de teste, alvo ou asserção mudou. Prova: as 4
baterias afetadas **47/47** verdes; a chave `dupname runScript` do ratchet foi
**eliminada** — baseline re-congelada 128→**127**. (`runJs` segue como chave:
`KofRandomTest`/`KofStringsIndentDedentTest` definem helpers `runJs` de forma
diferente, deixados como estão.)

**Fatia 7 da Fase 5 ENTREGUE (03/10):** o runner de biblioteca instalada `runKof` (seta `kof.install.dir`,
compila para JVM, carrega `Default.Main` por reflexão) era byte-idêntico em 6 classes E2E Kofmd
(`KofmdE2ETest`/`KofmdVocabE2ETest`/`KofmdFormatE2ETest`/`KofmdCorpusE2ETest`/`KofmdRoundTripE2ETest`/
`KofmdLspSupportE2ETest`) e `PdfLibraryE2ETest` (só o prefixo do diretório temporário diferia). Agora vive
uma vez numa nova base `KofmdRunSupport` (que também possui o `driver`/`tmp` compartilhados), com a subclasse
fornecendo `copyLibrary` e um `outPrefix()` sobreponível. Nenhum corpo de teste, alvo ou asserção mudou.
Prova: as 7 baterias afetadas **19/19** verdes; a chave `dupname runKof` do ratchet foi **eliminada** —
baseline re-congelada 127→**126**.

**Fatia 8 da Fase 5 ENTREGUE (03/10):** os helpers de paridade de saída JVM+JS
`driver`/`runJvm`/`runJs`/`assertBoth` eram byte-idênticos em `JsIfFoldStatementE2ETest`/`JsLoopIfTailE2ETest`
(idênticos) e `NullablePrimitiveRelationalConditionTest` (só quebra de linha na assinatura). Agora vivem
uma vez numa nova base `JsParityRunSupport`, que as 3 classes estendem. Nenhum corpo de teste, alvo ou
asserção mudou. Prova: as 3 baterias afetadas **18/18** verdes (JsIfFold 8, JsLoopIfTail 7,
NullableRelational 3); a chave `dupname assertBoth` foi **eliminada** — baseline re-congelada 126→**125**.

**Fatia 9 da Fase 5 ENTREGUE (04/10):** o par de instalação de biblioteca `copyLibrary` +
`findLibraryRoot` era byte-idêntico (a menos do nome da biblioteca e do seu arquivo-marcador) em 36
classes E2E cobrindo `libs/file`, `libs/interop`, `libs/image`, `libs/kofmd` e `libs/pdf`. Agora vive uma
vez numa nova interface `LibraryInstallSupport` (default `copyLibrary`/`findLibraryRoot`/`findLibsRoot`),
que as 36 classes implementam — cada uma fornecendo só `libraryName()` + `libraryMarkers()`; os quatro
connectors multi-biblioteca ainda sobrepõem `libraryNames()` (`file`+`interop`). A auditoria conta apenas
métodos `void`, então os provedores não-void não adicionam chave. `KofmdRunSupport` também implementa a
interface e o `copyLibrary` abstrato foi removido. Nenhum corpo de teste, alvo ou asserção mudou. Prova:
as 36 baterias afetadas **268/268** verdes (1 skip honesto de toolchain); a chave `dupname copyLibrary`
foi **eliminada** — baseline re-congelada 125→**124**.
Follow-up **#750** pegou o ratchet ainda VERMELHO após o pouso concorrente do TIFF: `TiffDecodeE2ETest`
era a 37ª classe com `void copyLibrary` próprio. Agora implementa a mesma interface (`image` +
`Tiff.kf`) e os helpers locais de instalação foram removidos; `check_test_hygiene` mede **124 chaves, rc=0**.

**Fatia 10 da Fase 5 ENTREGUE (04/10):** a suíte de helpers JVM+JS `runJvm`/`runJs`/`runBoth` era
idêntica em `ArrayBoundsSafetyE2ETest`, `CoreRegressionE2ETest` e `WrapperStaticCallsE2ETest` (o corpo de
`runJvm` diferia só por dois espaços de indentação). Agora vive uma vez numa nova base `JvmJsRunSupport`
(que também possui o `driver` compartilhado), que as 3 classes estendem. `WrapperStaticCallsE2ETest`
mantém o seu `runNativeX86`. O novo `TiffDecodeE2ETest` (lane de imagem) foi migrado para
`LibraryInstallSupport` para a chave `copyLibrary` seguir eliminada. Nenhum corpo de teste, alvo ou
asserção mudou. Prova: as 4 baterias afetadas **126/126** verdes; a chave `dupname runBoth` foi
**eliminada** — baseline re-congelada 124→**123**.

**Fatia 11 da Fase 5 ENTREGUE (04/10):** o helper 3-alvos `runAll3(Path, String, String)` era
byte-idêntico (módulo `private`/`protected` e comentários) entre `NullableBoolTruthinessE2ETest`/
`TrooleanLawE2ETest` e a base existente `NullablePrimitiveContractSupport` — as duas classes agora
estendem a base e as suas cópias duplicadas de `runJvm`/`runScript`/`runJs`/`runAll3`/`assertTarget`
sumiram, então `runAll3` vive em exatamente uma classe. As duas formas genuinamente diferentes foram
renomeadas: o helper 4-alvos de `NullablePrimitiveFieldWriterE2ETest` (acrescenta `runNativeX86`) →
`runAll4Targets`, e o helper inline baseado em nome de `AsCastPrecedenceE2ETest` → `runAllThree`. Nenhum
corpo de teste, alvo ou asserção mudou. Prova: as 4 baterias afetadas **43/43** verdes
(NullableBoolTruthiness 15, TrooleanLaw 13, NullablePrimitiveFieldWriter 9, AsCastPrecedence 6); a chave
`dupname runAll3` foi **eliminada** — baseline re-congelada 123→**122**.

**Fatia 12 da Fase 5 ENTREGUE (05/10):** a família `assumeToolchain` — o último
cluster `dupname` restante (26 classes, cada uma com a sua cópia privada do
mesmo guard "este binário existe?") — é consolidada atrás de uma nova interface
`NativeToolchainAssumptions` (`hasTool` prefix-aware §591 + os guards nomeados
`assumeNativeRiscv64`/`assumeNativeRiscv64WithSysroot`/`assumeNativeAarch64`/
`assumeNativeX86_64`/`assumeMcuRiscvAsm`/`assumeMcuArmAsm`, além do genérico
`assumeToolchain(String...)` para o qual os suportes abstratos encaminham). As
26 classes implementam a interface e apagam as declarações locais; cada ponto de
chamada sem argumento agora nomeia o guard que precisa (`assumeToolchain()` → o
guard explícito), então o conjunto de ferramentas de cada teste fica visível no
call site em vez de enterrado num corpo por classe. O
`assumeToolchain(String arch)` distinto de `KofHttpNativeResilienceCrossTest`
foi renomeado `assumeArchToolchain` para o nome não ficar sobrecarregado por
acidente. Nenhum corpo de teste, alvo ou asserção mudou. Prova: as 25 baterias
afetadas **342 rodados / 0F / 0E / 15 pulados** (os pulos são os guards honestos
de cross/qemu ausente); `check_test_hygiene` rc=0 com a chave
`dupname assumeToolchain` **eliminada** — baseline re-congelada 122→**121** (as
3 chaves reportadas são do `PdfTextE2ETest` não-rastreado da lane PDF +
`startServer`, dívida externa pré-existente).

**Fatia 13 da Fase 5 ENTREGUE (05/10):** os dois últimos clusters `dupname` de
toolchain — `assumeAarch64` (8 classes) e `assumeCross` (2 classes) — são
consolidados na mesma interface `NativeToolchainAssumptions`. Foi acrescentado
`assumeNativeAarch64WithSysroot()` (espelho da variante riscv64) para o
`NativeRiscvDtoaTest`, cujo `assumeAarch64` local também exigia o sysroot libc
cross; as outras 7 classes mapeiam para `assumeNativeAarch64()`.
`NativeRiscvDbWireTest` e `PlatformSeamSabotageTest` (que ainda mantinham um
`has` local completo) agora também implementam a interface, e o
`KofConfigCrossTest` descartou o par local `has`/`assumeCross` (o `has` local só
era usado pelo guard). Nenhum corpo de teste, alvo ou asserção mudou. Prova: as
10 baterias afetadas **74 rodados / 0F / 0E / 17 pulados** (os pulos são os
guards honestos de aarch64/qemu ausente); `check_test_hygiene` rc=0 com as duas
chaves `dupname` **eliminadas** — baseline re-congelada 121→**119**. A chave NOVA
`dupname startServer` anterior (do helper do `KofHttpErrorContractE2ETest` do
#756) também foi limpa ao renomeá-lo `startContractServer`.

**Fatia 14 da Fase 5 ENTREGUE (08/10, lane compiler/JVM/native `192.168.15.30:9092`):**
o cluster `dupname` `runQemu` — seis classes (`KofUuidTest`, `KofStringsSupport`,
`KofValidationSupport`, `KofStringsIndentDedentTest`, `KofTimeE2ETest`,
`KofRandomTest`) declaravam cada uma um helper equivalente byte a byte que compila
uma fonte Kof para um alvo cross e roda o binário sob QEMU, afirmando exit 0. O
helper agora vive uma vez numa nova interface `QemuRunSupport` (um método `default`
sobre um acessor abstrato `driver()`), que estende `NativeToolchainAssumptions`; as
seis classes a implementam e suas cópias locais são removidas. Os três pontos de
chamada do `KofRandomTest` passam o nome do arch do qemu explicitamente
(`qemu-riscv64`/`qemu-aarch64`), alinhando com as outras cinco. Nenhum corpo de
teste, alvo ou asserção mudou. Prova: as seis baterias afetadas **151 rodados / 0F /
0E**; `check_test_hygiene` rc=0 com `dupname runQemu` **eliminada** — baseline
re-congelada 119→**118**.

**Fatia 15 da Fase 5 ENTREGUE (08/10, lane compilador/JVM/nativo `192.168.15.30:9092`):**
o cluster `dupname` `runQemuE` — `KofNetTest` e `KofEncodingTest` declaravam cada
uma um helper equivalente byte a byte que compila uma fonte Kof para um alvo cross,
roda o binário sob QEMU e afirma que o stdout é igual ao oráculo JVM (um superconjunto
do `runQemu`, que só afirma exit 0). O helper agora vive uma vez como um segundo
método `default` na interface `QemuRunSupport` existente; as duas classes a
implementam (adicionando o acessor `driver()` sobre o campo existente) e suas cópias
locais são removidas. Nenhum corpo de teste, alvo ou asserção mudou. Prova: as duas
baterias afetadas **18 rodados / 0F / 0E / 0 pulados** (as pernas cross riscv64+aarch64
realmente executaram); `check_test_hygiene` rc=0 com `dupname runQemuE` **eliminada** —
baseline re-congelada 118→**117**.

### Fase 6 — Conformance

Criar suíte oficial de equivalência.

**Fatia 1 da Fase 6 ENTREGUE (05/10):** a suíte oficial de equivalência
(`tests/golden/`) cobria apenas JVM + native; o Golden Suite do plano define a
meta como "mesmo código → mesma saída em todo alvo" com o **exit code**
verificado, e a lista de alvos é JVM/Native/JS/Script. O `tests/run-golden.sh`
agora roda cada caso nos quatro alvos — `jvm`/`native`/`js` (compila + executa o
artefato) e `script` (`kof run --target script`, interpretação direta de IR) —
afirmando que o stdout capturado é igual a `expected.txt` E que o exit code é
`0`. Foram acrescentados o seletor opcional `--target` e o filtro posicional de
casos; o padrão (o que CI/release chamam) roda os quatro. Ferramentas externas
são guardadas honestamente (R6): um alvo cujo runtime falta (`as`/`ld` para
native, `node` para js) é **PULADO com o motivo**, nunca aprovado em silêncio.
Nenhum compilador, classe de teste ou asserção mudou — infraestrutura de teste
apenas. Prova (executada): `tests/run-golden.sh` **48/48** (12 casos × 4 alvos:
jvm, native, js, script), exit 0.

**Fatia 2 da Fase 6 ENTREGUE (05/10):** a *cobertura* da suíte de equivalência
cresceu de 12 para **16 casos** — quatro casos novos de superfície da linguagem
escolhidos para exercitar contratos que o conjunto antigo não cobria:
`null-safety` (estreitamento de nullable + `if (x != null)`), `map-set`
(`mapOf`/`put`/`getOrDefault`/`containsKey` + `setOf`/`add`/`contains`/`size`),
`pipelines` (`sorted`/`distinct`/`any`/`all`/`count`/`find`/`map`/`filter`) e
`switch-expr` (switch como expressão `case -> ...` + switch statement, `break`
opcional). Cada caso é validado nos quatro alvos pelo mesmo runner, então o
contrato "mesmo código → mesma saída em todo alvo" agora está pinado também
para essas quatro superfícies. Um valor esperado foi corrigido durante a
autoria RED-first (`sorted()` de `[3,1,2,1]` é `[1,1,2,3]`, não `[1,2,3,3]`),
confirmando que o runner pega um golden errado. Sem mudança de compilador —
infraestrutura de teste apenas. Prova (executada): `tests/run-golden.sh`
**64/64** (16 casos × 4 alvos), exit 0.

**Fatia 3 da Fase 6 ENTREGUE (05/10):** mais quatro casos — **20 no total** —
cobrindo o modelo de objetos e generics: `classes` (constructor explícito +
campos mutáveis + `extends` + override implícito + `super(name)`, escrita de
campo via `this`), `interfaces` (`implements` + um `List<Speaker>` despachado
virtualmente), `generics-box` (`class Box<T>(T value)` com erasure +
`substituteTypeVariable` na JVM e Native) e `enum` (`enum Color { … }` +
`name()` + `values().size`). São as superfícies onde a erasure
JVM/Native/JS mais diverge, então piná-las nos quatro alvos é o incremento de
cobertura de maior valor que resta na Fase 6. Prova (executada):
`tests/run-golden.sh` **80/80** (20 casos × 4 alvos), exit 0.

**Fatia 4 da Fase 6 ENTREGUE (05/10):** mais três casos — **23 no total** —
fechando as superfícies de alto valor restantes: `strings-methods`
(`trim`/`substring`/`startsWith`/`endsWith`/`indexOf`/`toUpperCase`/
`toLowerCase`/`charAt` + `==` de conteúdo), `closures` (captura mutável via o
`BoxN` sintético + captura dentro de `map`/`filter`) e `sealed-switch`
(`sealed class` + switch expression exaustivo sem `default` — o contrato
§X5.1/§X5.2, apagado no codegen). Prova (executada): `tests/run-golden.sh`
**92/92** (23 casos × 4 alvos), exit 0.

**Fatia 5 da Fase 6 ENTREGUE (05/10):** mais três casos — **26 no total** —
pinando as superfícies de controle de laço e numérica/string que o conjunto
antigo não exercitava: `loops-control` (`do-while` roda o corpo uma vez e então
itera pela condição, `break` sai de um `for`, `continue` pula uma iteração tanto
em um `for` quanto em um `for-in`), `numeric-casts` (`3.9 as Int` trunca para
`3`, divisão inteira `7/2` = `3` e módulo `7%3` = `1`, precedência aritmética
`2 + 3 * 4` = `14` vs `(2 + 3) * 4` = `20`, promoção `Int`→`Double` `5 + 2.5` =
`7.5`, `5 as Double / 2` = `2.5`) e `string-parts-valueof` (`split(",")` +
`String[]` indexado, `toCharArray()` + `chars[0] as Int` = a unidade de código,
`equalsIgnoreCase` insensível a conteúdo, e `String.valueOf` para `Int` e
`Double`). Cada caso é validado nos quatro alvos pelo mesmo runner. Cada valor
foi medido primeiro no alvo Script e conferido contra o contrato Kof antes de o
golden ser congelado. Sem mudança de compilador — infraestrutura de teste
apenas. Prova (executada): `tests/run-golden.sh` **104/104** (26 casos × 4
alvos), exit 0.

**Fatia 6 da Fase 6 ENTREGUE (05/10):** mais três casos — **29 no total** —
pinando as superfícies de operadores, mutação de coleções e exaustividade de
enum: `bitwise-ops` (`&`/`|`/`^`/`<<`/`>>` — os operadores mais propensos a
divergir porque o bitwise do JS é 32-bit enquanto os caminhos JVM/Native são
64-bit, então a igualdade cross-target é uma guarda real), `list-map-mutation`
(`list.add`/`get`/`set` e `map.put`/`get`/`keys().size` após a construção,
distinto dos casos read-only `collections`/`map-set`) e `enum-switch-expr` (um
switch expression de enum sem `default` — o contrato de exaustividade para
enums, distinto da forma `sealed class` em `sealed-switch`). Cada caso é
validado nos quatro alvos. Prova (executada): `tests/run-golden.sh` **116/116**
(29 casos × 4 alvos), exit 0.

**Fatia 7 da Fase 6 ENTREGUE (06/10):** mais dois casos — **31 no total** — pinando
as duas superfícies que a família #770/#772 acabou de exercitar e o contrato de
inteiro largo: `std-math-nullable` (um `Int?`/`String?` estreitado passado a uma
chamada std de formal primitivo — `math.abs`/`math.min`/`math.max`/`math.parseInt`
por guardas de null, a forma exata que regrediu no native em `known-bugs` §612) e
`long-arithmetic` (add/sub/mul/div/mod de `Long` 64-bit, menos unário, relacional e
a identidade de round-trip — a superfície mais propensa a divergir porque o JS usa
`BigInt` enquanto JVM/Native são 64-bit, então a igualdade cross-target é uma guarda
real). Ambos validados nos quatro alvos, mais riscv64/aarch64 sob qemu na autoria.
Prova (executada): `tests/run-golden.sh` **124/124** (31 casos × 4 alvos), exit 0.

**Fatia 8 da Fase 6 ENTREGUE (06/10):** mais dois casos — **33 no total** — pinando
o contrato de formatação de ponto flutuante e a superfície de concorrência:
`double-formatting` (literais e aritmética `Double` — `1.0`, `2.5`, `1.0/3.0` =
`0.3333333333333333`, o artefato IEEE-754 `0.1 + 0.2` =
`0.30000000000000004`, `1.0/0.0` = `Infinity`, `1e3` = `1000.0`, `7.5 % 2.0` =
`1.5`; formatação `Number`/`BigInt` do JS vs `double` de JVM/Native é uma guarda
real de divergência) e `concurrency-spawn-await` (`val h = spawn f(n)` com
`Handle<T>` tipado + desboxing no `await h`, duas tarefas juntadas e combinadas; o
contrato congelado de `spawn`/`await` nos quatro alvos). Ambos validados nos quatro
alvos, mais riscv64/aarch64 sob qemu na autoria. Prova (executada):
`tests/run-golden.sh` **132/132** (33 casos × 4 alvos), exit 0.

**Fatia 9 da Fase 6 ENTREGUE (06/10):** mais um caso — **34 no total** — pinando o
contrato de `return`-através-de-`finally` que o SIGSEGV nativo do §613 expôs:
`finally-return` (`return` dentro do `try` E dentro do `catch` de um
`try/catch/finally`, com o `finally` rodando nos dois caminhos — a forma exata do
`known-bugs` §613). Validado nos quatro alvos. Prova (executada):
`tests/run-golden.sh` **136/136** (34 casos × 4 alvos), exit 0.

**Fatia 10 da Fase 6 ENTREGUE (06/10):** mais um caso — **35 no total** — pinando as
duas faces de conclusão abrupta do `try/finally` que a varredura da fatia 9 da Fase 6
catalogou como `known-bugs` §617, agora CORRIGIDO: `finally-control-flow` combina um
`break`/`continue` saindo de um `try` (o finally DEVE rodar antes do salto) com um
`try/finally` aninhado cujo try interno `return`a (o finally externo roda, o valor
interno sobrevive). Validado nos quatro alvos; adicionalmente rodado em
riscv64/aarch64 sob qemu na autoria. Prova (executada):
`tests/run-golden.sh` **140/140** (35 casos × 4 alvos), exit 0.

**Fatia 11 da Fase 6 ENTREGUE (08/10, lane compiler/JVM/native `192.168.15.30:9092`):** mais
um caso — **36 no total** — pinando as superfícies de consulta/ordem superior de
`List` que o caso `pipelines` deixou de fora: `list-higher-order` exercita
`reduce(lambda, seed)` (forma com seed; `SEM073` se omitida),
`indexOf`/`lastIndexOf` (`-1` quando ausente), `isEmpty`, `none(pred)`,
`find(pred)` (o primeiro match), `slice(off, len)`/`take(n)`/`drop(n)` (cópias
materializadas, clampadas), `groupBy` (`Map<K, List<E>>`), `flatMap` (lista
achatada), `sort()` in place, `addAll`, `subList`, `remove` e
`sorted(comparator)` com comparador descendente. A face `zip` fica
deliberadamente de FORA: é o gap nativo documentado `NAT008` (um elemento
primitivo cruza um parâmetro de tipo nu), então um caso golden que exige os
quatro alvos não pode piná-lo — a recusa É o contrato. Validado nos quatro
alvos. Prova (executada): `tests/run-golden.sh` **144/144** (36 casos × 4
alvos), exit 0.

**Fatia 12 da Fase 6 ENTREGUE (08/10, lane compiler/JVM/native `192.168.15.30:9092`):** mais
um caso — **37 no total** — pinando a superfície de métodos de `Map`/`Set` que o
caso `map-set` deixou de fora (aquele caso só exercitava `mapOf`/`put`/`get`/
`getOrDefault`/`containsKey` e `setOf`/`add`/`contains`/`size`): `map-methods`
exercita `size`, `containsValue`, `putIfAbsent` (retorna o valor anterior e NÃO
sobrescreve; `null` numa chave nova), `remove(key)` (retorna o valor removido),
`isEmpty`/`clear`/`size` em `Map` e `Set`, e `Set.add` de um duplicado deixando o
tamanho inalterado. Validado nos quatro alvos. Prova (executada):
`tests/run-golden.sh` **148/148** (37 casos × 4 alvos), exit 0.

**Fatia 13 da Fase 6 ENTREGUE (08/10, lane compiler/JVM/native `192.168.15.30:9092`):** mais
um caso — **38 no total** — pinando a superfície de atribuição composta, que
nenhum caso anterior exercitava: `compound-assign` aplica `+=`/`-=`/`*=`/`/=`/`%=`
a um acumulador `Int` (`10 → 15 → 12 → 24 → 6 → 1`), `+=` a uma `String` (`"a"` →
`"abc"`) e `+=` a um `Double` (`1.5` → `4.0`, mantendo o tipo de ponto flutuante).
Validado nos quatro alvos. Prova (executada): `tests/run-golden.sh` **152/152**
(38 casos × 4 alvos), exit 0.

**Fatia 14 da Fase 6 ENTREGUE (10/10, lane compiler/JVM/native `192.168.15.30:9092`):** mais
um caso — **39 no total** — pinando o contrato de promoção aritmética de
`Byte`/`Short`/`Char` (`D-KOF-BYTE-ARITH` / `known-bugs` §561, corrigido 02/10), que
nenhum caso anterior exercitava: `byte-arith` pina que operandos `Byte`/`Short`/`Char`
PROMOVEM a `Int` (`Byte * 256` = `16640`, `Byte + Byte` = `240` — a repro do §561 que
crashava `Byte.valueOf` na JVM enquanto JS/Native devolviam o Int não truncado;
`Short + Short` = `2000`/`60000`, `Short * 3` = `90000`, `Char + Char` = `194`,
`Byte + Int` misto = `165`, `Char - Char` = `25`), mais o narrowing explícito
`(bb + 1) as Byte` = `66` e o wrap `300 as Byte` = `44` (a face `i2b` do `#471`). O
resultado é o `Int` não truncado, então a divergência JVM/JS/Native/Script que o §561
registrou é um guard real aqui. Validado nos quatro alvos. Prova (executada):
`tests/run-golden.sh` **156/156** (39 casos × 4 alvos), exit 0.

### Fase 7 — Integração

Implantar:

```
mvn verify
```

ou equivalente.

**Fatia da Fase 7 ENTREGUE (05/10):** o deploy de integração do plano agora é um
perfil Maven real — `mvn verify -Pintegration` roda a suíte golden oficial
(jvm+native+js+script) e a suíte de integração do CLI (`kof build`/`run`/`check`/
`serve`/`test`) na fase `verify` contra o jar do CLI recém-sombreado, via
`exec-maven-plugin` no `kof-cli` (`workingDirectory` =
`${maven.multiModuleProjectDirectory}`, então os scripts da raiz rodam
independente do módulo). Nenhum YAML de CI precisou mudar; os passos existentes
seguem chamando os scripts direto, e o perfil dá um comando único local/CI.
Prova (executada): `mvn -o -pl kof-cli -Pintegration exec:exec@golden-tests`
→ **48/48**; `...@integration-tests` → **9/9**; e o reactor completo
`mvn -o -pl kof-cli -am -Pintegration verify` → BUILD SUCCESS, ambas as suítes
verdes na fase `verify`.

**Fatia 2 da Fase 7 ENTREGUE (05/10):** a suíte de integração do CLI ganhou os
dois alvos que faltavam — `kof build --target js` + `node Default.mjs` (guardado
em `node`, igual ao runner golden, então um host sem Node reporta SKIP em vez de
vermelho falso) e `kof run --target script` (interpretação direta de IR). O
`tests/run-integration.sh` agora exercita **12** checagens (eram 9) pelos quatro
alvos mais as superfícies do CLI (`check`/`serve`/`test`). Prova (executada):
`tests/run-integration.sh` **12/12**, exit 0; a execução `integration-tests` do
perfil `mvn verify -Pintegration` roda o mesmo script.

**Fatia 3 da Fase 7 ENTREGUE (05/10):** a suíte de integração agora pina o próprio
despacho do `kof run` nos dois alvos que faltavam — `kof run --target native` (o
CLI compila, monta e executa, distinto da perna `build`+executar já coberta) e
`kof run --target js` (o motor JS embutido, sem `node` externo). O
`tests/run-integration.sh` agora exercita **14** checagens (eram 12) e todo alvo
tem tanto uma perna `build` quanto uma `run`. Prova (executada):
`tests/run-integration.sh` **14/14**, exit 0.

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
   atual = **117** chaves não-comentário, por `scripts/test-hygiene-baseline.txt`);
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
(`D-TEST-ARCHITECTURE-GO`); as Fases 1–4 estão CONCLUÍDAS (oversized 43→18; ratchet do harness 146→119, zero pares idênticos restantes). **A Fase 5 agora está AUTORIZADA e quinze fatias ENTREGUES** (`D-TEST-ARCHITECTURE-PHASES`, mantenedora 03/10 — `NativeCrossSupport` 54/54, `NativeIoJvmOracleSupport` (chave `jvmOracle` eliminada), `TargetGapRefusalSupport`, `ServerProcessSupport` (chave `stopServer` eliminada, 118/118), `JvmRunSupport` (chave `assertRuns` eliminada, 38/38), `MultiSourceRunSupport` (chave `runScript` eliminada, 47/47) `KofmdRunSupport` (chave `runKof` eliminada, 19/19) `JsParityRunSupport` (chave `assertBoth` eliminada, 18/18) `LibraryInstallSupport` (chave `copyLibrary` eliminada, 268/268), `JvmJsRunSupport` (chave `runBoth` eliminada, 126/126), a consolidação `runAll3` em `NullablePrimitiveContractSupport` (43/43), `NativeToolchainAssumptions` (chave `assumeToolchain` eliminada, 342/342), a limpeza `assumeAarch64`+`assumeCross` (121→119, 74/74), `QemuRunSupport` (chave `runQemu` eliminada, 151/151, 119→118) e a consolidação `runQemuE` em `QemuRunSupport` (18/18, 118→117)); as Fases 5–7 seguem trabalho aberto, com o cluster `main` confirmado falso-positivo (`main()` Kof dentro de text blocks de fonte de teste) e a primeira fatia da Fase 6 já ENTREGUE.
