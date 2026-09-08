# Roadmap Audit — Matriz de Implementação (06/09/2026)

> Fonte: auditoria de código real (3 explorações com evidências file:line) +
> execução de suíte. Regra: estado REAL, não o que o roadmap diz.
> Baseline: `0.3.0-beta`, suíte anteriormente declarada 910 testes.

## Matriz de estado

| Item | Estado real | Código | Testes | Gap principal |
|---|---|---|---|---|
| 1. Standard Library | **PARTIAL (bom)** | 23 namespaces em `Kof*.java` com gates R6 (`supportedOn`/`gapCode`) | E2E por área (KofCache/Db/Http/Mq/Time/…) | 1 stub silencioso (`kofWebStub` JS, R6 violado); sec sem cross; db/orm JVM-only (gaps honestos); `scheduler.at` cron fake |
| 2. GC auto-collect | **PARTIAL** | mark-sweep real x86_64 (`RuntimeGc.java`); **SEM safe-points/root-map**; auto-collect desligado (`RuntimeMemory.java:121-133`); riscv/aarch **sem GC** (bump allocator) | `KofGcE2ETest` 3/3 (sweep/keep/reuse) | safe-points + roots por frame; GC no riscv64/aarch64 |
| 3. Package Manager | **MVP** | `Deps.java` (flat Maven Central, cache `~/.kof/deps`) | `DepsTest` 4/4 | POM/transitivas, lockfile, ranges, publish |
| 4. Async | **PARTIAL** | JVM vthreads + Handle/await/timeout; JS Promise real (CONC003 ✅); Native pthread | `KofConcurrency2Test` 15/15 | timeout/cancel/select completos; sem select sobre channels |
| 5. Concurrency G8 | **PARTIAL (bom)** | spawn/await/cancel/selectAny/awaitTimeout/channel/scheduler 3 targets | idem | `scheduler.at` cron = 60s fixo (MVP declarado); cancel por TID%256 |
| 6. KofAndroid | **DONE (com ressalva)** | `Target.ANDROID`; `--apk` pipeline (d8/aapt2/apksigner); `AndroidProjectWriter` (Maven) | pipeline depende de ANDROID_HOME | lifecycle/ART runtime cobertos na Fase 2; consolidar docs |
| 7. Debugger | **MVP** | DAP stdio JVM (`KofDebug.java`), breakpoints JDWP reais; DWARF line-only | docs/debug-adapter.md | **locals = placeholder** (`"line N"`); stepping/evaluate; VS Code ext |
| 8. KofJS | **PARTIAL (alpha → funcional)** | ESM + source maps V3 + GraalJS + runtimes DOM/UI (9 arquivos) | `KofJsE2ETest`, browser headless | ws/sse stub silencioso no JS (WEB001); serve×JS indireto |
| 9. LSP | **PARTIAL** | diagnostics reais via CompilerDriver (fonte única); hover/completion/references/rename **textuais** | `LspServerTest` 4/4 | hover/completion/rename devem usar SymbolTable; go-to-definition |
| 10. KofScript | **PARTIAL (bom)** | interpretador de IR compartilhado (mesma semântica por construção) | `KofScriptTest` 15/15 + gate paridade | globals por regex multiline-fragil; REPL re-avalia tudo |
| 11. Language Spec | **PARTIAL** | `docs/language-reference/` 11 arquivos extraídos + specification-status | — | 20 gaps SG abertos (SG-009 subtipagem = maior); gramática não-normativa |
| 12. Conformance Suite | **NOT STARTED** | — (BackendParityTest 11 casos JVM×JS×Nat + golden 16/16 são os proxies) | BackendParityTest | suíte oficial por categoria |
| 13. Full Web Platform | **NOT STARTED** | routing parcial no kof.ui; validação existe | — | declarativo/forms/SSR — depende de 8+9 |
| gRPC | **NOT STARTED** | — | — | planejado; não iniciar antes de P0-P2 |
| Auto-hosting | **NOT STARTED** | — | — | documentado como gap |

## Bugs semânticos críticos (P0) — FALLBACKS SILENCIOSOS UNKNOWN

Auditoria encontrou **12 fallbacks silenciosos** que aceitam programas
semanticamente inválidos (o compilador infere UNKNOWN e segue, em vez de
diagnosticar). Os 4 maiores (todos em `SemExpressionTyper`/`MemberCallTyper`):

1. **#7 — maior**: método inexistente em namespace builtin (`db.*`, `log.*`,
   `http.*`, `mq.*`, `time.*`, `security.*`, …) → UNKNOWN sem SEM025. Só
   `process`/List/Map/Set foram corrigidos (7ec8b9d, bugs 31/34).
2. **#3 —** `obj.campoInexistente()` → UNKNOWN sem diagnóstico
   (SemExpressionTyper.java:312).
3. **#6 —** `super.metodoInexistente()` → UNKNOWN (MemberCallTyper.java:92)
   enquanto caminho normal de classe emite SEM025.
4. **#8 —** receiver UNKNOWN + método inexistente → sem diagnóstico
   (SEM025 só quando `isKnownReceiver`, MemberCallTyper.java:384-386).

Regra do plano: **inferência nunca cria declaração implícita; identificador
inexistente deve falhar.** Estes casos são a prioridade P0.

> **STATUS 07/09 (verificado no código + testes, não nesta tabela):** #7
> (namespaces builtin → SEM025), #3 (campo inexistente em classe conhecida)
> e #6 (super.metodoInexistente) estão **CORRIGIDOS** — helper
> `unknownNamespaceMethod` (MemberCallTyper) + gate `isKnownReceiver`
> (SemExpressionTyper); prova `SemanticResolutionTest` (6/6 verde: matriz
> de 12 namespaces + web.app + super + campo + falso-positivo). #8
> (receiver UNKNOWN + método inexistente) é **error-recovery legítimo** —
> sem o tipo do receiver não há como diagnosticar sem falso-positivo;
> manter UNKNOWN. P0 de fallbacks semânticos: **FECHADO**.

## Ordem de execução (ajustada pela auditoria)

- **P0**: fallbacks semânticos (#7/#3/#6/#8 + SEM025 p/ os outros builtins) +
  reproduzir known-bugs abertos; suíte como gate.
- **P1**: GC auto-collect (safe-points + root-map por frame x86_64; depois cross).
- **P2**: `kofWebStub` → gap code (R6); PM lockfile+transitivas; debugger
  locals via JDWP VariableTable; LSP hover/references via SymbolTable.
- **P3**: cron real (`scheduler.at`); KofScript globals via frontend.
- **P4**: conformance suite estruturada; spec §subtipagem (SG-009).
- **P5**: web platform, gRPC, auto-hosting (não iniciar antes).

## Evidência bruta

Ver relatório de auditoria 06/09 (3 explorações): stdlib (23 áreas +
paridade por target), tooling (CLI 20 comandos, PM, debugger, KofJS, LSP,
KofScript), semântica (12 fallbacks, SEM025 cobertura, pipeline).
