[English](README.md) | [Português](README.pt_BR.md)

# docs/development/future/ — só plano futuro (zero código)

**Regra desta pasta:** aqui vive **apenas** o que é **plano para o futuro** —
documento de arquitetura/visão **sem código implementado** (ou com código que
é explicitamente não-entregável e fora do escopo atual).

> Se uma ideia **já está sendo implementada** (mesmo parcialmente), o doc
> correspondente **não fica aqui** — ele vive em `docs/` e documenta o **estado
> real** (o que já existe) + **como finalizar**. Assim quem lê sabe exatamente
> onde a coisa está e o que falta.

## Exemplo recente (01/09)

- `kof-native-risc-arm.md` **saiu daqui** para `docs/native-multiarch.md`: o
  plumbing (enum `Target.NATIVE_RISCV64/AARCH64`, CLI `native.risc/arm`,
  dispatch, cross-as/ld) já está no código, então o item é **em desenvolvimento**
  e passou a ser documentado com estado real + plano de finalização.

## O que fica aqui (só plano, sem código)

| Doc | Tema | Por que fica em `future/` |
|-----|------|---------------------------|
| ~~`type-system-extensions-plan.md`~~ **movido → [`../../type-system-extensions-plan.pt_BR.md`](../../type-system-extensions-plan.pt_BR.md)** | X5 variância + sealed · X6 reflexão de interop | plano **CONCLUÍDO** (X5+X6), movido para `docs/` 22/09 (três estados) |
| `PLAN-MULTIPARADIGMA.md` | multiparadigma / pipelines funcionais e queries declarativas (`users.filter{...}.map{...}`), diagnóstico no HEAD 16/09 | **design puro, zero código no doc** (§8 não lista nenhum arquivo alterado); promovido só quando o primeiro incremento funcional landar (SYSTEMS fechado, R12) |
| `assembly-optimization-plan.md` | otimização de assembly Native multi-ISA (pipeline IR→optimizer→target lowering→ISA; fases A–F; critérios de promoção) | **só plano, zero código** — registrado a pedido da mantenedora 22/09; princípio cross-target (otimizar antes do assembly específico da ISA); regra de ouro: IR bom + lowering, não peephole infinito; toda otimização exige teste de corretude + benchmark + cobertura cross-target (§22) |
| `scoped-resources-plan.md` | RAII leve (TIER 2.4, `using`/`resource_scope`) | design puro — zero ocorrências de `resource_scope`/`kof_resource`/`using` no lexer/parser/runtime; gated por bump |
| `value-records-plan.md` | value records / tipos-valor first-class (TIER 2.7, `value record`) — aceito 16/09 (issue #275, `DECISIONS.md` §D-VALUE-RECORD) | **zero código** — a feature não existe no lexer/parser/backends; só design, barrado pelo R12 + autorização explícita para abrir a frente |
| ~~`secrets-plan.md`~~ **promovido 21/09 → [`../../architecture/secrets-plan.md`](../../architecture/secrets-plan.md)** | `Secret`/`KeyHandle` + redação forçada (Estágio 5, tracker 3.6) | **FECHADO 21/09** — todas as faces pousadas (`04473bbe`), registro de design movido para `docs/architecture/` |
| `kof-file-plan.md` | plano estratégico do `kof.file` (core → Data → Document → Archives) | **só plano, zero código** — registrado a pedido da mantenedora 19/09; codecs pesados pertencem a pacotes oficiais (gate R1); pontes já existem (`kof.json`/`kof.db`/`kof.http`) |
| `image-vision-plan.md` | `kof.image` + `kof.vision` (pixels/filtros → codecs → OCR/QR/código-de-barras → pipeline de visão → ML) | **só plano, zero código** — registrado a pedido da mantenedora 19/09; pacotes oficiais por R1; interop-first por R9 (imageio/PDFBox/ZXing/Tess4J/OpenCV/ONNX); promoção atrás do R12 |
| `graphics-gaming-plan.md` | superfície de intenção de 2D/3D/jogo/som/vídeo (`scene`/`frame`, sprites/tiles, malha/câmera/material, `sound.play`, painel `video`) — `DECISIONS.md` §D-GRAPHICS-GAMING + 4 adendos (20/09) | **só plano, zero código** — registrado a pedido da mantenedora 20/09; aceite é paridade TOTAL nos 4 alvos (adendo 2 — R7 não vale aqui); erradicação JavaFX medida com 0 ligações (adendo 3); **engine PRÓPRIA da Kof** para o domínio de gráficos/mídia (adendo 4 — a exceção nomeada ao R9; FFI só na camada não-engine: janela/GPU/dispositivo de áudio; codecs nunca caseiros); frentes abrem só por promoção explícita de fatia por ela (R12 + regra 6) |
| `qrcode-wasm-plan.md` | `kofqrcode` (leitor arquivo/câmera + gerador) + target frontend `KofWasm` | **só plano, zero código** — registrado a pedido da mantenedora 19/09; QR = pacote oficial (R1, interop ZXing R9); `Target.WASM` não existe (rejeição honesta `WASM001` no `TargetMatrix`) |
| `wasm-wasi-plan.md` | spec técnica WASM+WASI: backend direto de primeira classe, runtime/memória linear/GC, WASI, host browser, capabilities, fases 0–7, release gates | **só documentação, zero código** — registrada a pedido da mantenedora 19/09; spec profunda da linha WASM do universal-platform; todo ponto indeciso marcado TBD/DECISION REQUIRED (regra 6); checklist de validação no §36 |
| `test-architecture-plan.md` | refatoração da suíte de testes: camadas L0–L5, perfis (fast/integration/full/stress), harness, golden, determinismo, medição | **só plano, zero código** — registrado a pedido da mantenedora 19/09; infraestrutura pura de testes (não toca o compilador); R12 — promover só depois de fechar o trabalho atual do compilador |
| `kof-testing-platform-plan.md` | **Plataforma de Testes Kof** (voltada ao usuário): Unit / Integração / Frontend E2E em três níveis — aditiva à superfície `test`/`assert` existente e ao runner `kof test`; abstração de browser + SPI de provider (Playwright primeiro, Cypress depois), matriz de capacidades, locators semânticos/auto-wait, harness/artefatos/relatórios/perfis; complementa o `test-architecture-plan.md` (suíte Java interna) | **só plano, zero código** — registrado a pedido da mantenedora 26/09; ancorado no inventário real (`CmdTest.java:15`, `test`/`assert` em `Lexer.java:68`/`CompilerDesugar.java:16`, `ConformanceMatrixTest.java:44`, `KofJsE2ETest`/`KofJsBrowserE2ETest`, `WASM001`); a Kof é dona da API, Playwright/Cypress são providers; regra 6 — promoção exige `D-TESTING-PLATFORM` |
| `http-policies-plan.md` | políticas HTTP/Web declarativas (authorization, rate limit, headers, payloads de rejeição) associáveis **globalmente, por recurso e por endpoint** — estende o `app.security(opts)` global existente | **só plano, zero código** — registrado a pedido da mantenedora 23/09; o parser global já existe, o gap é escopo + payloads; JVM-first, Native/JS são gaps honestos `WEB006`; regra 6 — promoção exige decisão `D-HTTP-POLICIES` travada no `DECISIONS.md` |
| `pagination-plan.md` | intenção de janela de primeira classe `Window<T>` (em memória + pushdown SQL `LIMIT/OFFSET` + helper HTTP opcional), pronta para cursor/keyset | **só plano, zero código** — registrado a pedido da mantenedora 23/09; compõe o `List.subList` + `orm.page` (`kof_orm_page`) existentes; a parte em memória pega carona na Fase 1 do `PLAN-MULTIPARADIGMA`, o resto é barrado; regra 6 — promoção exige decisão `D-PAGINATION` |
| `kof-connector-ecosystem-plan.md` | **Interoperabilidade Kof — Ecossistema de Connectors**: um Interop Core (modelo ABI/tipos, ownership, strings, structs, callbacks, erros, carregamento de bibliotecas, versionamento, diagnósticos) + Connector SPI/manifest + catálogo (Java primeiro, C ABI depois, então JVM/Systems/gerenciado/scripting/científico/legado) | **só plano, zero código** — registrado a pedido da mantenedora 26/09; greenfield (o conceito de "connector" não existe); constrói sobre o substrato FFI/ABI existente (`ffi-abi-structs.md`, `FfiSignature`/`AbiLayout`/`FfiStructLayout`, `JvmFfiRuntime`, `ExternalClasspath`, `KofProcess`) e nunca o duplica (regra 54); regra 6 — promoção exige decisão `D-CONNECTORS`; controle de escopo regra 55 (provar com poucos connectors, sem cascata de 30 runtimes) |
| `docs/shell-plan.md` (movido) | `kof.shell` — shell idiomático sobre `kof.process` — **RECLASSIFICADO 19/09: MVP v1 landou (`34e4344f`); o doc agora mora em `docs/development/`** — faces residuais (pipeline JS/Native, glob/`~`/redireção v2) rastreadas lá | — | — |
| `docs/workflow-plan.md` (movido) | `kof.workflow` — jobs/pipelines/retry/checkpoints/dead-letter (TIER 2.1) — **RECLASSIFICADO 19/09: aprovado pela enquete da mantenedora (Q1–Q4) e recon 2.1.0 FEITO (`WorkflowPrimitivesE2ETest` 6/6 + conserto do descriptor `Result` de lambda `8ec07214`); a doc agora mora em `docs/development/`** — **MVP 2.1.2 ATERRISSOU 19/09** (host pure-Kof, `job`/`dag`/`after`/`run`/`Report` flat, `WorkflowE2ETest` 7/7) + docs 2.1.4; fila residual = add-ons 2.1.3 |
| ~~`PLAN-BAREMETAL-BOOT.md`~~ **promovido 22/09 → [`../../PLAN-BAREMETAL-BOOT.md`](../../PLAN-BAREMETAL-BOOT.md)** | **nativo → bare-metal/bootável com ring0/ring1** (faces B-0…B-6: costura HAL, freestanding, UEFI, BIOS legado, MCU, anéis de privilégio x86_64) | **PROMOVIDO 22/09** por ordem da mantenedora (`DECISIONS.md` §D-BAREMETAL-BOOT): a frente está aberta (R12 sobreposto só para ela), escopo ordenado inclui ring0/ring1; a ordem do §7 do plano governa (B-0 → B-1 → caminho de boot → B-6) |
| `PLAN-BOOTSTRAP.md` | **o Bootstrapper: Kof escrito em Kof (BS-1)** — a **estrela-guia** da plataforma (`DECISIONS.md` §D-BOOTSTRAP, 20/09): um `kofc.kf` que compila todo o corpus byte-idêntico ao core Java e depois compila a si mesmo (ponto fixo); "Kof como sua própria nuvem" fecha ponta-a-ponta | **só plano de design, zero código** — rascunhado adiantado pela mantenedora (lane dona `.18`), execução barrada pela regra dos três estados + R12: não pode começar antes do EXIT GATE 1.0 fechar (`roadmap.md` §24) e nenhum estágio pode ser pulado; toda saída de emergência é decisão regra 6. Condições de entrada E1–E6 (§2); fases BS-A…BS-E (§3); linha NORTH STAR do `roadmap.md` |
| `DECOMPILER.md` + `TRANSLATOR.md` + `LEGACY_MIGRATION.md` | plataforma de migração legado (decompiler/translator/IR/diff-testing) | **DESPRIORIZADO pela mantenedora 15/09 — de volta desde `docs/development/`.** O código fica em kof-cli (`DecompileTest` 67/67, `TranslateTest` 61/61); a FILA está pausada: promoção exige decisão explícita dela |
| ~~`planning-stdlib-array-returns.md`~~ → `docs/stdlib/DD-STDLIB-01-array-returns.md` | DD-STDLIB-01 | **FECHADO 13/09** — decisão 6a + implementação (`randomBytesHex`->String; choice=idiom), movido p/ docs/ |

> **`IMPLEMENTATION-UNIVERSAL-PLATFORM.md` saiu de `future/` em 17/09/2026** — promovido
> a trabalho corrente por decisão da mantenedora — hoje `docs/architecture/IMPLEMENTATION-UNIVERSAL-PLATFORM.md` —
> que **sobrepõe o portão R12** (ver `DECISIONS.md`
> §D-UNIVERSAL). A visão/design não muda; o ponto de entrada é o Estágio 1
> (consolidação SYSTEMS) e as recomendações executáveis R1–R12.

## Histórico: o que saiu de `future/` antes (snapshot 12/09 — NÃO é o estado atual)

> **Não leia como estado atual** (atualizado 17/09). O cluster de migração
> **voltou para `future/` em 15/09** (linha acima), e os docs de plataforma/app-model
> foram **ratificados e consolidados no `DECISIONS.md` em 13/09** (os 6 arquivos de
> `decision-pending/` foram apagados). A tabela fica só como registro da queda de 12/09.

| Doc | Destino / casa atual |
|-----|------------------|
| `DECOMPILER.md`, `TRANSLATOR.md`, `LEGACY_MIGRATION.md` (os `IMPLEMENTATION_PLAN.md`+`ACTION_PLAN.md` foram FUNDIDOS p/ `roadmap.md` §23 e os `DIFFERENTIAL_TESTING.md`+`LEGACY_IR.md` p/ dentro do `LEGACY_MIGRATION.md`, tudo 13/09) | **de volta em `future/` 15/09** (despriorizado) — código+testes ficam em kof-cli (`kof inspect/decompile/translate/compare/migrate`, `Main.java`); contagem viva em `roadmap.md` §23 TIER 3–5 |
| `PLATFORM-PLAN.md` | `DECISIONS.md` §D-PLATFORM (ratificado 13/09; o arquivo foi apagado) |
| `APPLICATION_MODEL.md` | `DECISIONS.md` §D-APP (Q1–Q10 travados 13/09; o arquivo foi apagado) |
| `PLANNING-FUTURE-AUDIT.md`, `planning-future-reconcile.md` | `docs/audits/` (encerradas 13/09); R2→`DECISIONS.md` §D-APP/§D-PLATFORM, R5→cluster migração |
| `planning-finally-return.md` | `docs/decisions/DD-01-finally-return.md` (FECHADO 13/09; bug 45 CORRIGIDO) |
| `planning-stdlib-time-design.md` | `DECISIONS.md` §D-STDLIB (ratificado 13/09; o arquivo foi apagado) — `addDays`/`diffDays` nos 5 alvos (TIME002 11/09) |

## Quando mover de `future/` para `docs/`

Quando o item deixar de ser "só plano" e **houver código em desenvolvimento**,
mesmo parcial:

1. Mover/reescrever o doc em `docs/` com **status `EM DESENVOLVIMENTO`**;
2. Documentar **o que já está feito** (arquivos/linhas reais) vs **o que falta**;
3. Incluir seção **"como finalizar"** (passo a passo com dependências);
4. Atualizar `docs/backend-parity.md` / `docs/status.md` para apontar o novo
   caminho;
5. Manter o gap-code (ex.: `NATIVE002`) até o item fechar.
