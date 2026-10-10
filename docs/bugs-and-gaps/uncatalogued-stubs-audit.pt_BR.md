[English](uncatalogued-stubs-audit.md) | [Português](uncatalogued-stubs-audit.pt_BR.md)

# Stubs não catalogados / desenvolvimento incompleto — ledger de revisão

> **Registro vivo da frente de REVISÃO** (sessão 9094, branch `beta-0.5.0`).
> **Não** é um ledger de bugs: um achado *confirmado* sobe para
> `known-bugs.md` (`§NNN`, EN+PT) com repro; um *candidato* fica aqui até ser
> medido. O inventário mecânico é reprodutível: `scripts/audit-stubs.sh`
> (somente leitura).

**Início:** 21/09/2026 · **Base medida:** `16340f62` · **Escopo:**
`*/src/main` (761 `.java`) + os 12 arquivos `.kf` host em
`kof-compiler/src/main/resources/dev/kof/`.

## Por que esta frente existe

A mantenedora pediu uma varredura do **código inteiro** por stubs /
desenvolvimento parcial que **não** esteja documentado. O risco que esta frente
ataca é exatamente o silencioso (R6): um caminho que *finge* funcionar — um
`return null`/`return 0` de fachada, um `default` que engole, uma exceção
engolida — e para o qual nenhuma entrada de ledger aponta.

## Método (reprodutível)

```bash
bash scripts/audit-stubs.sh /home/mel/Kof4j > /tmp/slice1-report.txt
```

**Aviso de ruído (medido, não presumido):** os comentários do Kof são em
português, onde `todo` = *todos* e `stub honesto` = **recusa deliberada R6**
(documentada no próprio código, ex.: `NativeMethodEmitter:387`,
`KofJsDbBridge:103`). Um match cru é, portanto, um **candidato**, nunca um
achado — todo candidato é triado à mão e comparado com `docs/bugs-and-gaps/*`
antes de ser catalogado. Esta frente explicitamente **não** recataloga as
recusas honestas legítimas.

## Fatia 1 — resultados medidos (21/09, base `16340f62`)

| Sinal | Cru | Triado |
|---|---|---|
| `TODO/FIXME/XXX/HACK` case-sensitive (`.java`) | 12 | **0 real** — os 12 são "todo" português (every) ou notas de fix com `§` |
| `UnsupportedOperationException` | 3 | **0 stubs** — 1 é string numa lista; 2 são falhas duras honestas (`NativeMethodEmitter:387` "R6: never silent"; `KofJsDbBridge:103` "DB002") |
| `catch` vazio (`.java`) | 31 | **30 benignos** (cleanup de arquivo / probe de ambiente / fallback reflexivo) + **1 candidato** (abaixo) |
| `@Disabled` / `@Ignore` (testes) | 0 | 0 — nenhum teste desabilitado escondendo trabalho pendente |
| `assumeTrue` / `Assumptions.` | 216 | todos com **motivo honesto de ambiente** (toolchain C, MySQL, node, qemu, H2) — é contrato da casa, não máscara |

**Conclusão da fatia 1:** o código **não tem `TODO` abandonado nem teste
desabilitado**. Seus "stubs" são a recusa R6 projetada (diagnóstico honesto),
que é comportamento correto, não dívida. O valor restante da auditoria é,
portanto, **semântico** (paridade / tratamento parcial), não greppável.

## Candidatos (não verificados — ainda NÃO catalogados como bug)

### UI-JS-1 — handlers de evento de UI no JS engolem exceção em silêncio

- `kof-compiler/src/main/java/dev/kof/compiler/js/JsRuntimeUiEvents.java:62`
  (e `:78`, `:153`, `:173`): `try { … fn(kofEv) } catch (e) {}` — um listener
  que lança é **descartado em silêncio**, sem `console.error`.
- Contraste: `JsRuntimeUiComponents.java:226` faz explicitamente
  `console.error("[kof] " + where + ": " + detail)` e relança — o runtime de UI
  do JS tem duas políticas diferentes para o mesmo caso "callback do usuário
  falhou".
- **Estado: FECHADO (fatia 4) — por design, não é bug.** Medição: no JVM o
  runtime `kof.ui` é um **no-op** documentado (`JvmRuntimeUi.kof_ui_widget_on:146`
  e `kof_ui_component_on:180` têm corpo vazio), e as regras de
  renderização/estado de `kof.ui` são a **única exceção nomeada** à paridade
  (`D-UI-SCOPE`, 18/09 — `docs/backend-parity.md:225`): autoradas/efetivas no
  KofJS, nunca no JVM/Native. Sem política JVM/Script de que divergir, o
  `catch (e) {}` do JS é o isolamento de erro de handler do motor de UI, não um
  stub não documentado. **Nenhum `§NNN` aberto.**

## Fatia 2 — invariante de paridade + 1 achado de drift de doc (21/09)

**Invariante checada (grep + leitura de todos os call-sites):** para todo
`Kof*.supportedOn(...) == false` há um `gapCode(...)` **emitido no mesmo ponto
de lowering** — ex.: `ExpressionStaticCallLowerer:139/146` (db),
`ExpressionMethodCallLowerer:411/413` (std/buffer/rng/math), `:429/432`
(observability), `ExpressionTimeCallLowerer:19/27` e `:53/60`,
`ExpressionDbCallLowerer:20/28`, `ExpressionLogCallLowerer:19/27`,
`ExpressionOrmCallLowerer:35` + `CompilerOrmSupport:77/83`,
`ExpressionSchedulerCallLowerer:19/21`. `KofGpu` não tem método `gapCode()`,
mas emite `GPU001` inline em `ExpressionMethodCallLowerer:356-359`.
**Nenhum gap silencioso de paridade encontrado** na superfície `Kof*`.

**DRIFT-NET-1 (achado, corrigido, só comentário):** `KofNet.java` anunciava o
oposto da realidade — o comentário de classe dizia *"riscv/aarch ainda gated em
compile-time"* e `supportedOn` dizia *"byte-scan nativo pendente"*, enquanto
`conformance-matrix.md` §net registra **NET001 CLOSED 09/09** e
`NativeRiscvAsmRtB24` implementa `kof_net_queryEncode`/`queryDecode` (fatia
B24, aarch via tradutor; prova `KofNetTest.netOnCrossArch`). O `supportedOn`
devolve `true` para **todos** os alvos (correto), tornando
`gapCode()="NET001"` **vestigial**. Corrigi os dois comentários stale e anotei
o vestigial — **sem mudança de comportamento**, logo o teste da Q1 não se
aplica (um comentário não regride); prova = `mvn -o -pl kof-compiler -am
compile` rc=0.

## Fatia 3 — mais dois comentários stale "gated/pendente" (21/09)

Grep por `ainda não|pendente|gated|not yet|por ora` na stdlib `Kof*` e
cruzamento com a matriz/código. Ambos são o mesmo drift "comentário
append-only" (afirmação stale deixada acima da própria correção) — correções
só de comentário, sem comportamento:

- **DRIFT-UUID-1** `KofUuid.supportedOn` dizia *"Os 3 nativos ainda não têm
  fatia asm — UUID001 os bloqueia"*, contradizendo as linhas seguintes
  (*"S3b.2 FEITO nos 5 alvos 10/09 … Gate removido"*) e a matriz (**UUID001
  fechado**, merge beta→main 10/09; riscv B25b + aarch tradutor). Frase stale
  removida.
- **DRIFT-STRN-1** o javadoc de `KofStrings` dizia que os conversores de
  palavra (joinWords) ficam *"gated … com o bug 59 aberto"*, mas **STRN001
  FECHADO 09/09** (riscv B15 + aarch tradutor) e `supportedOn` devolve `true`.
  Reescrito.

Prova dos dois: `mvn -o -pl kof-compiler -am compile` rc=0 (só comentário ⇒ Q1
não se aplica).

## Fatia 2b — invariante travada como teste (21/09)

Novo `kof-compiler/src/test/java/dev/kof/compiler/StdParityGapAuditTest.java`
(**15/15 verde**) transforma a matriz de suporte auditada num catraca: para
cada namespace com gate afirma o conjunto exato de alvos `unsupported` e o gap
code exato (buffer FFI001/FFI002, db DB001, log LOG001, orm ORM001, rng RNG001,
gpu, tetris EGG001, scheduler SCHED001/CRON001, observability
OBS003, time.tzOffsetSeconds TIME003, security.sha512 SECN003), além dos
namespaces always-true permanecerem sem gate. Um gate novo num namespace
always-true agora quebra o teste de propósito — a matriz é lei e tem de ser
atualizada junto.

> **Atualização 27/09:** `math.pow` saiu desta catraca — o cross agora liga libm
> por uso (`pow@PLT`, row 10 fechada `e69ea2bc6`), então `KofMath.supportedOn`
> retorna `true` em todo alvo e `MATH001` é só um código reservado.
> `StdParityGapAuditTest` foi repontado de acordo.

O teste **corrigiu um palpite meu**: `security.sha512` é gated não só em
riscv/aarch, mas também em **ANDROID e SCRIPT** (`JVM || JS || isNative`), logo
o gap code `SECN003` dispara em quatro alvos. Medido, não lembrado (Q3).

Prova: `mvn -o -pl kof-compiler -am -Dtest=StdParityGapAuditTest test` →
**15/15** (13 core + 2 por função: `KofSecurity`
chacha/cookie/auth/resource-server e `KofTime.addDays/diffDays` sem gate).

## Fatia 4 — UI-JS-1 fechado + duas varreduras negativas (21/09)

- **UI-JS-1 fechado por medição** (ver o candidato acima): o `kof.ui` no JVM é
  no-op documentado e `D-UI-SCOPE` faz da UI a única exceção nomeada de
  paridade — não há stub não documentado nem divergência a corrigir.
- **Arquivos `.kf` host (12) — todos declarados:** `makealive-db-host.native.kf`,
  `workflow-ckpt-host.native.kf` e `workflow-sched-host.native.kf` carregam
  **stubs de falha alta citando o gap** (ORM001/CRON001, R6), e o resto não tem
  marcador (`todo` = *todos* em português). **0 stubs não documentados.**
- **Varredura de fachada Q7:** 133 `default -> null` nos dispatchers estilo
  `staticMethod` são o idioma da casa para *"não é membro deste namespace"* — o
  typer/lowerer transforma o null num diagnóstico (o caminho R6), não numa
  fachada silenciosa; os 4 `catch { return null }` são fallback reflexivo/parse
  (`JvmRuntimeCore:196`, `JvmTimeRuntime:270`, `CmdEditor:276`,
  `KofScriptExecutor:162`). **0 fachadas silenciosas encontradas.**

## Fatia 5 — conclusão da varredura (21/09)

A varredura do código inteiro **convergiu para um negativo**: pelo inventário
de marcadores, pela invariante de paridade, pela varredura de fachada Q7, pelos
12 arquivos `.kf` host e pelo candidato de UI, **nenhum stub não documentado /
desenvolvimento silenciosamente incompleto foi encontrado**. O que a frente
*de fato* produziu é real e está commitado:

- **3 drifts de documentação corrigidos** (só comentário): DRIFT-NET-1,
  DRIFT-UUID-1, DRIFT-STRN-1 — todos afirmações stale "gated/pendente"
  contradizendo a matriz e o código.
- **1 invariante travada**: `StdParityGapAuditTest` **15/15** (conjunto de alvos
  unsupported + gap code por namespace com gate; um gate novo num namespace
  always-true quebra de propósito).
- **1 candidato fechado por medição**: UI-JS-1 — por design (`D-UI-SCOPE`).
- **2 varreduras negativas registradas**: hosts `.kf` e fachadas Q7.

Restante (opcional, não é fila): a passada ampla de deriva doc/código sobre
toda afirmação de `docs/` (item 3 das próximas passadas); até então a frente
está **convergida**.

## Passada 3 — referências a testes-fantasma (21/09)

Método: todo token `*Test` em backticks nos `docs/` conferido contra a árvore
(grep em todo `*/src/test` + nome de arquivo). **Ausente = referência-fantasma.**
232 tokens nos três ledgers de paridade, 11 em todos os docs.

| Token | Onde | Veredito |
|---|---|---|
| `KofChannelTest`, `ChannelStdlibE2ETest` | prova-vizinha do §374 em `known-bugs.md` | **evidência quebrada** — nunca existiram; anotados inline, vizinho real do canal = `KofConcurrency2Test` 48/48 |
| `AarchSchedSmokeTest`, `GenericFieldChainE2ETest`, `NullableReceiverFieldWriteE2ETest`, `KofCharCrossE2ETest`, `ProcessRunE2ETest` | ponteiros abertos / esboços de fix | nome proposto (teste a escrever) — não é drift |
| `NullablePrimitiveFieldsE2ETest` | nota de reversão do §243 | histórico (existiu quando a face meio-pousada foi pinada) — não é drift |
| `SequenceE2ETest`, `ChannelE2ETest` | `docs/stdlib/PLAN-MULTIPARADIGMA.md` | plano concluído, movido para `docs/stdlib/` (regra dos 3 estados) — não é drift |
| `KofSemanticTest` | `decisions/planning-mutability.md` | proposto — não é drift |

Resultado: **2 referências-fantasma usadas como prova** (ambas já sinalizadas
transitoriamente pela sessão q553, `6ce55b28`); agora catalogadas de forma
durável e anotadas inline. As outras 9 são nomes legítimos de plano/histórico.

### Passada 3 (cont.) — contagens vivas stale no tracker (21/09)

Amostragem dos deltas datados contra o código: as rows vivas do tracker
carregavam contagens congeladas no pouso e já crescidas:
- row X10 `StdCatalog` = 31 → **34** (`StdCatalogTest:91` trava 34;
  buffer/shell/ssh pousaram após o fecho do X10) — anotado inline, datado.
- row R1 `stdlib_boundary.txt` = 31 → **35 registrados + 15 `excluded`** (50
  linhas de namespace, medido 21/09) — anotado inline.
O snapshot histórico é preservado e a leitura atual corrigida. Os **deltas**
datados são snapshots por desenho (não são reescritos).

### Passada 3 (cont. II) — códigos de gap citados sem ocorrência em Java (21/09)

Extraído todo token `[A-Z]{2,7}\d{3}` dos cinco ledgers vivos (174 códigos) e
conferido contra todo `.java` da árvore. 11 ausentes, **todos contabilizados**:

| Código | Onde | Veredito |
|---|---|---|
| `HTTP003` | §259 | já catalogado **fantasma** (o compilador nunca emite) |
| `UUID002` | conformance-matrix | a matriz declara explicitamente que não existe |
| `SEM094` | known-bugs | **reservado** ao gate de switch-return (DECISIONS §D-TROOL) |
| `MEDIA002` | status.md | **label** do gap de câmera documentado, nunca código emitido (o `KofMedia` emite MEDIA001/MEDIA003) — ressalva adicionada |
| `COL001`, `STR002`, `CANVAS001`, `SEM063` | paridade/ledger | **históricos/fechados** ("era"/"renumerado"/"fechado") |
| `APP002` | matriz APP001–003 | **residual** documentado (`[server] port` do `kof.toml` não consumido) |
| `AND003` | training reference | **caveat documentado, não gate de compile-time** |
| `UIW008` | roadmap | **ID de item** do roadmap, não código de gap |

Resultado: **nenhum código-fantasma não documentado** — a disciplina de
gap-codes se sustenta (o drift §259 já tem guarda mecânica, `DomainGapCodesTest`).
Só o `MEDIA002` carecia da ressalva "label, não código emitido"; adicionada inline.

## Passada 4 — fecho da frente (21/09)

Spot-check final do mandato: o `ConformanceMatrixTest` — a classe que a matriz
diz travar cada célula ✅ — rodou **12/12 verde** no tip
(`mvn -o -pl kof-compiler -am -Dtest=ConformanceMatrixTest test`, 111,5s), logo
a cadeia doc → teste citado → golden se sustenta.

**Frente concluída.** Ao longo de cinco fatias e quatro passadas a varredura
não achou **nenhum stub / desenvolvimento incompleto não documentado**:
0 `TODO` abandonado, 0 teste desabilitado, a invariante de paridade está
travada mecanicamente, 2 referências-fantasma de *prova* foram catalogadas e
anotadas, contagens/códigos stale corrigidos, e os dois candidatos (UI-JS-1,
MEDIA002) são por desenho/documentados. Artefatos duráveis: este ledger, o
ratchet `StdParityGapAuditTest` e `scripts/audit-stubs.sh`. Novos re-triggers
desta frente devem ser recusados (estabilidade AGENTS); trabalho novo aguarda
regressão ou decisão da mantenedora.

## Passada 5 — auditoria semântica, batch 1 (21/09, a frente profunda)

As varreduras lexicais estão esgotadas; o desenvolvimento incompleto não
documentado real é **semântico**. Quatro leituras paralelas (emit-sites de
codegen, ops no-op de runtime, CLI/LSP/tooling, testes fracos) geraram
candidatos; cada um foi verificado no código antes de catalogar. Batch 1
(código/paridade, catalogado como §424–§426, EN+PT):

- **§424** — cinco métodos `String` aceitos (`matches`/`replaceAll`/`replaceFirst`/
  `toCharArray`/`compareToIgnoreCase`) silenciosamente incompletos no JS e
  link-fail no Native sem gap code.
- **§425** — `kof.config` riscv64/aarch64 como stub de default silencioso
  enquanto `supportedOn` retorna true (javadoc `CONF001` stale).
- **§426** — `time.collect()` no JS compila sem runtime e sem gate.

Batch 2/3 (verificado, catalogado como §427–§431, EN+PT): runtime `kof.io`/web-T1
do cross ausente com gate errado (§427); fachada do DAP
`default -> respond(success:true, {})` + claim de `restart` no `debug-adapter.md`
(§428); LSP `default -> {}` sem resposta JSON-RPC (§429); testes false-green —
`RouterE2ETest#debugConc001` zero-assert, 5 `NativeDebugTest*` só-print,
`KofWebNativeE2ETest` `assertTrue(true)`, `BareCollectionPrimitiveArgE2ETest`
`assumeTrue(compileSuccess)` mascarando regressões nativas (§430); drift menor de
tooling — `serveStatic` morto com javadoc falso, ramo inalcançável do DAP,
opções desconhecidas do `Compare`, `.class` stale (§431).

O subconjunto mecanizável destes checks (`default` de resposta vazia, padrões
weak-green) é a próxima unidade: o `scripts/audit-stubs.sh` ganha as seções para
a classe de achado ser reprodutível, não uma leitura única.

**Feito (Fatia 6):** `scripts/audit-stubs.sh` v2.1 adiciona as seções **11**
(resposta vazia DAP/LSP `default -> { }` / `respond(..., Map.of())`), **12**
(false-green `assertTrue(true)`/`assumeTrue(...success())`) e **13**
(harnesses `*DebugTest*` só-print), com contagens no resumo, e o
`scripts/tests/audit-stubs-test.sh` ganha fixtures RED-first para cada (14
checagens, verde; fixture limpa segue 0). Na árvore real as seções apontam
exatamente §428/§429/§430 mais o candidato `KofDebug.java:77` agora anotado no
§431.

### Fatia 7 — backstop de símbolo de runtime: medido NÃO confiável (21/09)

O objetivo era um diff mecânico de símbolo emitido × definido por alvo para
pegar a classe §424/§427. **Medido na árvore: inviável.** 781 literais `kof_*`
são emitidos; o diff contra todo `.globl`/definição deixa **81 candidatos,
todos falsos positivos** — os literais são prefixos dinâmicos (`"kof_ui_"`,
`"kof_db_"`, `"kof_static_"`), nomes despachados por igualdade de string no JS
(`kof_args`/`kof_box`), nomes de método de desugar do Kof (`kof_app_on_start`),
nomes de tabela SQL (`kof_migrations`) ou bindings de contexto JS
(`kof__uiRootHtml`). O furo do §424 nem é literal `kof_*`: é um
`java_lang_String_<método>` **sintetizado** no `NativeOpHelpers`. Conclusão: o
backstop confiável é **comportamental** (E2E por alvo sobre um corpus de chamadas
stdlib) mais os `DomainGapCodesTest`/`StdCatalogTest` existentes; um diff
estático de símbolos não é um e não deve ser construído. O check direcionado do
registry `String` × alvo (a classe §424) é a mecanização acionável.

### Fatia 8 — registry `String` × alvo JS, mecanizado (21/09)

`StringMethodTargetCoverageTest` (3 testes, verde) é o backstop direcionado que a
Fatia 7 concluiu ser a forma certa. Lê `StringMethodRegistry.java` (27 nomes
aceitos) e `JsCallEmitter.java`, e afirma que todo método está classificado: **10**
mapeiam para um membro real de `String.prototype`, **8** têm `case` explícito,
**4** passam por `kof_string_to_*`, e **5 são o gap conhecido do §424**
(`matches`/`replaceAll`/`replaceFirst`/`toCharArray`/`compareToIgnoreCase`). Um
método novo no registry sem classificação quebra o teste — provado RED-first
plantando `toTitleCase`, que deu `sem classificacao: [toTitleCase]`. O conjunto de
gaps é fixado nos cinco documentados (cresce ou encolhe → vermelho).

### Fatia 9 — mesmo ratchet nos alvos NATIVOS (21/09)

Estendendo o teste da Fatia 8 com `everyStringMethodHasAClassifiedNativeTarget`
(agora 4 testes, verde): lê o dispatch x86 (`NativeX86StringCalls.java`,
`"x".equals(kc.methodName())`) e o switch cross riscv
(`NativeRiscvCrossOps.java`, `case "x" ->`), intersecta com o registry e afirma
que o resto é exatamente o gap conhecido. **Resultado medido: o gap nativo é os
MESMOS cinco do JS** — todo outro método do registry tem lowering x86+riscv, e
nenhum dos dois alvos rebaixa `matches`/`replaceAll`/`replaceFirst`/
`toCharArray`/`compareToIgnoreCase`, então ambos caem no link-fail do
`java_lang_String_<m>` sintetizado (§424). RED-first provado renomeando o
handler x86 de `split` → `sem lowering x86/riscv: [split]`. Logo o §424 é
simétrico entre JS e Native; a única questão aberta é a correção (um diagnóstico
em vez do link-fail), que é decisão rule-6, não edição de agente.

### Fatia 10 — símbolos de coleção × alvos, mecanizado (21/09)

`CollectionSymbolTargetCoverageTest` (1 teste, verde) lê o lowering de coleção
(`CollectionCallLowerer`/`CollectionMethodGates`/`CollectionValueOps`, **31**
símbolos `kof_list_*`/`kof_map_*`/`kof_set_*` emitidos) e afirma que todo símbolo
existe em cada backend — JVM (`case`s inline do `JvmOpCollections` + runtime), JS
e Native x86/riscv. **Medido: 31/31 nos três alvos.** Diferente do §424, o
lowering de coleção é um mapa explícito nome→símbolo (sem nomes sintetizados
`java_lang_*`), então a presença é mecanizável. É um ratchet de PRESENÇA, não uma
prova semântica, com guarda anti-vácuo (≥25 símbolos parseados). RED-first
provado renomeando `kof_list_sort`→`kof_list_sortx` →
`JVM: ... SEM handler: [kof_list_sortx]`.

### Fatia 11 — §424 medido comportamentalmente; frente convergida (21/09)

O `StringGapMeasuredTest` (2 testes, verde) compila `"abc".matches("a.*")` por
alvo em vez de ler fontes. **Medido:** o JS emite a chamada direta ao membro (sem
helper) — compila limpo, a falha é só em runtime, que é a face genuinamente
**silenciosa**; o Native roda o `ld` *durante o compile* e falha com `undefined
reference to 'java_lang_String_matches'` como `COMP001` — **visível em tempo de
compilação**, não um break silencioso de runtime. Isso corrige o corte de
severidade do §424 (JS silencioso > Native visível) e fica registrado na entrada
do bug (EN+PT). Com os backstops mecânicos (Fatia 8 String×JS, Fatia 9
String×Native, Fatia 10 símbolos de coleção) mais esta medição, nenhum gap novo
apareceu — a frente de revisão está **convergida**. Novos re-triggers devem ser
recusados (estabilidade AGENTS) até regressão medida ou decisão da mantenedora; a
correção do §424 (um gap code honesto do Kof em vez do mapeamento direto no JS /
`COMP001` cru) é decisão **rule-6**, não edição de agente.

## Próximas passadas (planejadas — ainda não executadas)

1. **Checagem de assimetria de paridade** — **FEITA (fatia 2b,
   `StdParityGapAuditTest` 15/15**, incluindo os gates por função de
   `KofSecurity` e `KofTime`).
2. **Varredura de fachada Q7** — **FEITA (fatia 4, negativa): 0 fachadas silenciosas.**
3. **Deriva doc/código:** features marcadas como prontas em `docs/` cujo código
   é parcial (cruzar as matrizes de paridade e o tracker contra o código). —
   **parcialmente feita** (fatias 2 e 3 acharam 3 drifts); continuar sobre os
   gates por função.
4. **Arquivos `.kf` host** — **FEITA (fatia 4, negativa): 0 stubs não documentados.**
5. **Fechar UI-JS-1** — **FEITA (fatia 4): fechado por medição, por design.**

## Proveniência

- Script de inventário: `scripts/audit-stubs.sh` (somente leitura, idempotente) —
  agora guardado por um teste RED-first de fixture
  `scripts/tests/audit-stubs-test.sh` (um `TODO`/`catch` vazio/`@Disabled`
  plantado precisa ser achado; fixture limpa dá 0; a árvore não pode mudar; raiz
  sem `*/src/main` recusa), ligado em `scripts/tests/run-agent-tests.sh` (lane
  docs/.18, 21/09).
- Base: `16340f62`; contagens re-medidas nesta base.
- Artefatos de recon prévios: `/tmp/opencode/audit/{markers,candidates,hard}.txt`.
