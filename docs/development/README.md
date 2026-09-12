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
| `docs/actual-state.md` | **movido p/ docs/ 11/09** — snapshot histórico 0.2.6-beta (registro, não backlog) | `HISTÓRICO` |
| `docs/language-state.md` | **movido p/ docs/ 11/09** — snapshot histórico 02/09 (SG-E2; registro, não backlog) | `HISTÓRICO` |

### 3. Plans de Plataforma
| Arquivo | Por que está aqui | Estado |
|---|---|---|
| `plan-platform-completion.md` | P0–P5: P3 (query DSL) ✅ mas P4–P5 (health/tracing/LSP/debug) pendentes | `PARTIAL` |
| `plan-spring-independence.md` | Fases 5–14: web completa + gRPC planejados, GC pending | `PARTIAL` |
| `planning-switch-expr.md` | **movido p/ `docs/planning-switch-expr.md` 10/09** (SYN001 FECHADO — nada pendente; concluído não fica em development/) | `FEITO` |
| `planning-mutability.md` | **movido p/ `docs/planning-mutability.md` 10/09** (DD-02/SEM037/SEM038 aplicados, #42 fechada) | `FEITO` |
| `planning-finally-return.md` | **movido p/ `docs/development/future/` 10/09** (PROPOSED — bug 45, zero código, decisão da mantenedora pendente) | `PLANEJADO` |
| `planning-stdlib-time-design.md` | **movido p/ `docs/development/future/` 11/09** (DD-STDLIB-02 PROPOSED — zero código em andamento; sem decisão da mantenedora não é trabalho atual) | `PLANEJADO (future)` |
| `planning-stdlib-array-returns.md` | **movido p/ `docs/development/future/` 11/09** (DD-STDLIB-01 PROPOSED — zero código; trava de dispatch Array/objeto) | `PLANEJADO (future)` |
| `plan-stdlib-expansion.md` | STDLIB universal S1–S12: S1–S8+S10–S12 ✅ 09–11/09 (TIME002 fechado 11/09 fatia B33; MATH001 fechado 11/09 fatia B32); pendentes: S10c (DD-STDLIB-01) + S7 `format`/`boundaries` (decisão de superfície) — `pow`/`roundTo`-mode ag. mantenedora | `EM CURSO` |
| `planning-otp-supervision.md` | DD-OTP-01..13: supervisão OTP one_for_one (issue #83) — recomend. stdlib puro-Kof + fábrica + escalate-callback + flag própria (5 alvos grátis) — decide a mantenedora | `PROPOSED` |
| `PLAN-TREE-SHAKING.md` | **movido de `future/` 12/09** — stdlib por alcançabilidade (issue #97, frente designada pela mantenedora): S-1 (T0) ✅ 12/09 (`ArtifactSize` parser ELF64 + `ArtifactSizeTest` gate 5% + `kof build --print-sizes` — números da §1 do plano travados por teste); S-2 (T1a.1) ✅ 12/09 (`RuntimeSlices` — mapa provides/needs por reflexão derivada do fonte, paridade byte-idêntica; hello-needs = 14/611 símbolos); S-3..S-5 (T1a.2 poda x86→riscv) + S-6 (T2 JS por família) + S-7 (docs consolidadas) pendentes | `EM CURSO` |
| `refactoring/PLAN-SOLID-500.md` | regra ≤500 linhas: Fases 4–8 fechadas, mas F1–3 + 9 com resíduo 502 → 493 | `EM CURSO` |

### 4. Gaps & Bugs
| Arquivo | Por que está aqui | Estado |
|---|---|---|
 | `specification-gaps.md` | 23 entradas (SG-001–020 + E1–E3) — SG-001/007 resolvidos; demais ABERTOS (a maioria decisão de design, regra 6) | `ABERTO` (~21 gaps) |
 | `known-bugs.md` | fila viva: abertos atacáveis = §45 (finally+return, lowerers) + §104b-ii (Object.equals/record-em-coleção + storage-box de record no asm, Native); 🟡 PARCIAIS-honestos = §107 (println coleção: face escalar ✅ CORRIGIDA 12/09 nos 3 nativos — `f3b3821c`+cross B39, golden JVM byte-idêntico; restam record/aninhado=`?` e FP-cross=FLT001, ambos recusa visível e ambos pendurados no §104b-ii) ; congelados/regra 6 = §94/§96/§98/§101/§106 (json.encode Map); corrigidos 08–12/09: 1–8/10–17/19–20/26 + 39/44/46/48/50/59/62–64/96–105 + §104c (JS) + §107-JS + §107-escalar (Native x86+riscv+aarch) + §108 + §109–§112 (paridade sweep 11/09) + §138 + MATH001/TIME002 | `ABERTO` (fila §45/§104b-ii; §107/§108/§138 FECHADOS 11–12/09) |
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
| `docs/concurrency-memory-model.md` | SG-020 — spec SC + 5 bordas HB (ADOTADA 09/09, validada 10/09 — KofConcurrency2Test; movida p/ docs/ 11/09: nada pendente) |
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
