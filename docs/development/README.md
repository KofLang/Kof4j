# Development — Documentos em Andamento (não concluídos)

> **Criado em:** 07/09/2026 · **Origem:** varredura de `docs/` por status `❌` / `🟡` / `PARTIAL` / `NOT STARTED` / `PLANNED` / `TODO` / `gap`
> **Propósito:** separar o que **ainda não está concluído** do que já é referência estável em `docs/`.
> `docs/` mantém só o que é **prova** (comportamento previsto, suíte verde, stable). `development/` é o **backlog vivo** — planos, audits, gaps e roadmaps que guiam o próximo trabalho.

**Regra para agentes:**
- `docs/status.md` + `docs/backend-parity.md` continuam sendo a **fonte de verdade do que funciona hoje** (sempre em `docs/`).
- `development/` é a **fila de prioridade**: se precisa saber *o que falta*, leia aqui primeiro.
- Ao fechar um item: mova o doc correspondente de `development/` de volta para `docs/` (ou marque como `FEITO` e arquive), no mesmo commit que fecha o gap (com prova: teste verde/suíte).

---

## Índice — o que foi movido de `docs/` para `development/`

### 1. Visão de Futuro (`future/` — Tiers 0–12, plataforma universal)
| Arquivo | Por que está aqui | Estado | Próximo passo |
|---|---|---|---|
| `future/README.md` | visão geral da plataforma futura | `PLANNED` | não iniciar antes de SYSTEMS fechar (R12) |
| `future/PLAN-UNIVERSAL-PLATFORM.md` | arquitetura universal (R1–R12) | `NOT STARTED` (arquitetura, não ação) | aguardar estágio SYSTEMS |
| `future/ACTION_PLAN.md` | ordem Tiers 0–12 | `PARTIAL` | Tier 1 pendentes (ver roadmap-audit) |
| `future/PLATFORM-PLAN.md` | plano de plataforma (F0–F2) | `EM CURSO` (F1 feita, F2 próxima) | F2 Target Architecture (`Target.SCRIPT`, `KofProjectConfig`, `TargetMatrix`) |
| `future/APPLICATION_MODEL.md` | RFC App Model (monólito ↔ distribuído) | `EM CURSO` / RFC 975 linhas | decisão maintainer Q1/Q2 → I1 (`AppManifest` + `kof new`) |
| `future/LEGACY_MIGRATION.md` | migração legado (kof inspect/translate) | `PLANNED` | não fazer antes do core estável |
| `future/LEGACY_IR.md` | IR legado | `PLANNED` | — |
| `future/DECOMPILER.md` | decompiler | `PLANNED` | — |
| `future/TRANSLATOR.md` | translator | `PLANNED` | — |
| `future/DIFFERENTIAL_TESTING.md` | differential testing | `PLANNED` | — |
| `future/IMPLEMENTATION_PLAN.md` | plano de implementação legado | `PLANNED` | — |
 | `future/PLAN-CANVAS-WIDGET.md` | Canvas widget (CANVAS001) | `FEITO` (`5a9cac4` — 3 targets; UI009 drawImage `6e3181f`) | — |

### 2. Roadmaps & Audits
| Arquivo | Por que está aqui | Estado |
|---|---|---|
| `roadmap.md` | §§8–11 ❌ não implementado (Frontend, Frontend+Backend same project, Architectura, Monólito→Micro) | `PARTIAL` (Fase 0 ✅, resto 🟡/❌) |
| `roadmap-audit.md` | matriz 06/09: 13 itens — 5× `PARTIAL`, 4× `NOT STARTED` | `PARTIAL` |
| `roadmap-gap-2026-09-03.md` | gap report NATIVE002 + discrepâncias | `PARTIAL` |
| `ecosystem-coverage.md` | matriz G1–G12: muitos `PARTIAL`/`PLANNED` (events, messaging, OAuth2, batch, AI) | `PARTIAL` |
| `actual-state.md` | "O que NÃO está implementado" residual 0.2.6-beta | `PARTIAL` |
| `language-state.md` | snapshot 02/09 desatualizado (SG-E2: 810 testes vs 969 hoje, v0.2.6 vs 0.3.0) | `OUTDATED` |

### 3. Plans de Plataforma
| Arquivo | Por que está aqui | Estado |
|---|---|---|
| `plan-platform-completion.md` | P0–P5: P3 (query DSL) ✅ mas P4–P5 (health/tracing/LSP/debug) pendentes | `PARTIAL` |
| `plan-spring-independence.md` | Fases 5–14: web completa + gRPC planejados, GC pending | `PARTIAL` |
| `planning-switch-expr.md` | SYN001 ✅ mas doc de planejamento histórico (contrato fechado) | `FEITO` (mantido aqui como histórico) |
| `planning-finally-return.md` | DD-01: `finally` no caminho `return` (bug 45) — proposta de semântica, aguarda mantenedora | `PROPOSED` |
| `planning-mutability.md` | DD: mutabilidade/`val` reassign — documento de debate | `PROPOSED` |
| `planning-stdlib-time-design.md` | DD-STDLIB-02: semântica de tempo restante (fuso/today, assinaturas compostas, hoursBetween, formatDate) — libera só todayIso/formatDateIso se D1-A | `PROPOSED` |
| `planning-stdlib-array-returns.md` | DD-STDLIB-01: retorno Array/objeto na camada de dispatch stdlib (S10c randomBytes/randomChoice) — recomendação: choice via idiom, bytes decide a mantenedora | `PROPOSED` |
| `plan-stdlib-expansion.md` | STDLIB universal S1–S10: S1–S8+S10a/b ✅ 09/09; S1b (FLT double) + S10c (DD-STDLIB-01) pendentes | `EM CURSO` |
| `refactoring/PLAN-SOLID-500.md` | regra ≤500 linhas: Fases 4–8 fechadas, mas F1–3 + 9 com resíduo 502 → 493 | `EM CURSO` |

### 4. Gaps & Bugs
| Arquivo | Por que está aqui | Estado |
|---|---|---|
 | `specification-gaps.md` | 23 entradas (SG-001–020 + E1–E3) — SG-001/007 resolvidos; demais ABERTOS (a maioria decisão de design, regra 6) | `ABERTO` (~21 gaps) |
 | `known-bugs.md` | bugs 1–60: 39 ABERTO (null de Map — decisão de design regra 6); 37/38/40 + CANVAS001 ✅ corrigidos; Native lane 43/44/46/48/50/59 | `ABERTO` (bug 39 + lane Native) |
| `security-plan.md` | 18 camadas: A ✅ mas B/C/D com ❌ (cookies, middleware, OAuth2, TLS cert próprio) | `PARTIAL` |

### 5. Native Multiarch
| Arquivo | Por que está aqui | Estado |
|---|---|---|
| `native-multiarch.md` | NATIVE002: core riscv64/aarch64 ✅ 26/26 mas paridade avançada (JSON/DB/HTTP/mq/cache) ❌ + GC riscv/aarch sem | `EM DESENVOLVIMENTO (parcial)` |
| `DATABASE_VISION.md` | níveis 0–2,4 ✅ mas nível 3 query DSL + pooling + Native/JS ORM ❌ | `PARTIAL` |
| `complexity-audit.md` | violações ≤500: NativeRuntime 17.3k, CompilerDriver 8.2k, JsBackend 5.7k | `EM CURSO` |

---

## O que ficou em `docs/` (concluído / referência estável)

Estes **não** foram movidos — são prova ou referência estável:

| Arquivo | Por que ficou |
|---|---|
| `docs/status.md` | gate da suíte (910 testes) + build — fonte de verdade do loop autônomo |
| `docs/backend-parity.md` | matriz JVM×Native×JS — referência de paridade (gaps com código, mas matriz é estável) |
| `docs/architecture.md` | ADR multi-target (atualizado 06/09, SG-E1 corrigido) |
| `docs/compiler-architecture.md` | pipeline real frontend→IR→backends (fonte atual) |
| `docs/security.md` | auditoria v1 + matriz (G9 fechado) |
| `docs/stdlib.md` + `docs/stdlib/*.md` | stdlib estável (kof.*) |
| `docs/concurrency.md` | spawn/await/channel/scheduler (CONC003 fechado) |
| `docs/observability.md`, `performance.md`, `philosophy.md` | referência estável |
| `docs/debugging*.md`, `debug-adapter.md` | DAP MVP (Fase 3) — parcial mas tooling base estável |
| `docs/http.md`, `docs/stdlib-*.md`, `docs/runtime/*` | runtime models (STRING/ARRAY/INHERITANCE completos) |
| `docs/language-reference/*` | spec extraída do código + probes (parcial mas separada como linguagem≠compilador) |
| `docs/targets/*`, `docs/tooling/*`, `docs/ui/*`, `docs/distribution/*` | docs por domínio (estáveis) |
| `docs/releases.md`, `docs/LICENSING.md`, `docs/kof-vs-java.md` | histórico/licença/comparativo |

> **Critério de aceite seletivo:** um doc foi para `development/` **se** (a) seu título/contéudo declara `PLANNED`/`NOT STARTED`/`PARTIAL`/`EM CURSO`/`EM DESENVOLVIMENTO`/`TODO`/`❌`/`🟡`/`gap` **ou** (b) ele é um **plano/roadmap/audit** cujo propósito é listar o que falta (não o que funciona). Docs que apenas *mencionam* gaps mas cujo corpo é referência estável (ex.: `backend-parity.md` lista gaps mas a matriz é a referência oficial) ficaram em `docs/`.

---

## Como usar (para o agente autônomo)

```
1. LEIA docs/status.md + docs/backend-parity.md          → o que funciona (gate)
2. LEIA development/roadmap-audit.md + development/roadmap.md
      + development/specification-gaps.md                → o que falta (fila P0→P5)
3. ESCOLHA o maior valor SEM dono EM CURSO no DOING.md
4. EXECUTE um escopo → teste → commit → atualize DOING.md
5. AO FECHAR: mova o doc de development/ de volta para docs/ no mesmo commit
```

**Sincronização:** `docs/` e `development/` são versionados juntos. Pull antes de cada commit (`git fetch && git pull --rebase --autostash`) — se outro agente moveu um doc de `development/` para `docs/` (item fechado), você verá o rename no rebase.

**Não confundir:** `training/` + `learn/` + `docs/` = **corpus estável** (comportamento previsto). `development/` = **backlog vivo** (trabalho que ainda não é comportamento previsto). Nunca mude comportamento congelado via `development/` sem bump + doc (regra 6).
