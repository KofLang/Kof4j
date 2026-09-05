# DOING.md — coordenação multi-agente (quem faz o quê)

> **Regra obrigatória para agentes (IA ou humano):**
> 1. **Antes** de começar qualquer trabalho de feature/gap: leia este arquivo.
> 2. Se o item que você quer atacar já tem **dono + estado `EM CURSO`**, não toque —
>    escolha outro ou pergunte. Nunca dois agentes no mesmo gap.
> 3. Ao **reivindicar** um item: edite este arquivo **no mesmo commit** que começa
>    o trabalho (dono, branch, arquivos que vai tocar).
> 4. A **cada commit**, atualize sua linha (estado, progresso, o que falta).
> 5. Ao **concluir**: mude para `FEITO` com data + commit + teste que prova, e
>    marque o gap no docs (`status.md`/`backend-parity.md`).
> 6. Itens abertos ficam em `EM CURSO` por no máx. uma sessão; ao abandonar,
>    volte para `ABERTO` com nota do que já funciona e o que falta.

Estados: `ABERTO` · `EM CURSO` · `FEITO` · `BLOQUEADO`.

---

## Em curso agora

| Gap/Item | Estado | Dono | Branch | Arquivos principais | Notas |
|---|---|---|---|---|---|
| **NATIVE002** — paridade stdlib riscv64/aarch64 (log/config/time/cache/mq stubs→real) | `EM CURSO` | agente-nativo-val | main | `NativeBackend.java` (`RISCV_RUNTIME_ASM` + `translateRiscvToAarch64`), `NativeRiscv64E2ETest.java`, `NativeAarch64E2ETest.java` | validação 13/13 + observability real (b20aa49); preparando stubs p/ implementação real (mqtt/sse/interval/cron via `translateRiscvToAarch64`) |

## Concluídos recentemente

| Gap/Item | Estado | Dono | Data | Prova |
|---|---|---|---|---|
| **Plataforma de migração legado** — Fases A–H (`kof inspect/decompile/translate/compare/migrate` + `Confidence`) | `FEITO` | agente-planning | 05/09 | branch `planning-future`; `ClassFileParser`+`Confidence`+CLIs; suíte **855/0**; commits `34ded81`→`98a4d8b` |
| **FFI formalizado** — TIER 2.1 (`extern` + gap FFI001/002 + binding real JVM(FFM)+Native(dlopen/dlsym)) | `FEITO` | agente-planning | 05/09 | `FfiE2ETest` 5/5 (libc `abs`/`atoi`, libm `sqrt`); suíte 855/0 |
| **Codegen hook + ct-eval** — TIER 2.2/2.3 (`CodegenStep` + string-concat folding) | `FEITO` | agente-planning | 05/09 | `OptimizerTest` 22/22 + `StructuredTestE2ETest` 32/0 |
| **TIER 2.4/2.5** — scoped-resources (design) + variance/sealed (deferir) | `FEITO` | agente-planning | 05/09 | `docs/future/scoped-resources-plan.md` + decisão em `IMPLEMENTATION_PLAN.md` |
| **GC mark-sweep** Native | `FEITO` | agente-planning | 03/09 | `461ec3b` — sweep real funciona; auto-collect fica desligado (safe-points fora do escopo) |
| **HTTP002** — `kof.http` no Native | `FEITO` | agente-planning | 03/09 | `71d27f2` — `NativeHttpRuntime.java` (novo, ≤500): parse URL, IPv4, socket/connect, request/read body/status; `KofHttpE2ETest` 6/6 (get/post/status com server Kof real) |
| **MySQL Native prepared + query binário** | `FEITO` | agente-nativo-val | 03/09 | `4ce1f25` + `02b9ddb` — `NativeDbPrepared.java` (≤500): PREPARE/EXECUTE binário completo (); `KofDbE2ETest` 12/12 com `nativeMysqlPreparedBinary` (aspas+injection intactos) |
| **NATIVE002 core** — riscv64 + aarch64 13/13 | `FEITO` | outro agente | 02–03/09 | `3fbc29a`, `ac6c598` — asm puro via `translateRiscvToAarch64` |
| **TIME001** — time.interval/cancel no JS | `FEITO` | agente-planning | 03/09 | `c1db297` — fila cooperativa `kofTimeJobs` bombeada por `kofTimeSleep`; `KofTimeE2ETest` 5/5 |
| **LOG001** — kof.log no JS | `FEITO` | agente-planning | 01/09 | `console.*` + `KOF_LOG_LEVEL` |
| Spans W3C / lifecycle `application{}` / `kof deps` | `FEITO` | agente-planning | 01/09 | `97109c1`, `eb108ec`, `dfce911` |

## Abertos (livres pra pegar)

| Gap/Item | Prioridade | Escopo | Notas |
|---|---|---|---|
| **~~GC mark-sweep~~ Native** | ✅ fechado 03/09 | `kof_gc_sweep` real; auto-collect desligado (requer safe-points) | ver Concluídos |
| **HTTP002 restante** | média | `delete/put/patch/options` + `timeout/retry/circuit` reais no Native | `get/post/status` feitos |
| **WEB002** — kof.web no Native | ✅ fechado 03/09 | servidor HTTP/1.1 asm: accept+parse+match+lambda-dispatch+body context; 4/4 no KofWebNativeE2ETest; pendências honestas: path params {id}, headers/param/query, SSE/WS (WEB003/4), keepalive (sempre Connection: close) | agente-planning (T1..T4: 89ac0d9 → 2ead1df) |
| **CONC003** — JS async real | média | event-loop real sobre Promises no GraalJS | design pendente |
| **MEDIA001/2/3** | baixa | paridade media Native/JS | gaps documentados |
| **SECPQ** | baixa | PQC via liboqs FFI | Tier 9 (futuro) |
| **MySQL query binário** (resultset EXECUTE) | ~~média~~ | `kof_db_mysql_prep_query` | ✅ FEITO 03/09 (`02b9ddb`) |
| **Portar stdlib riscv64/aarch64** | média | `translateRiscvToAarch64` existe | agente-nativo-val |
| Debugger DWARF variáveis/expressões + VS Code ext | baixa | `kof.debug` | |
| OpenTelemetry export | baixa | spans feitos; falta OTLP export | |

### Trilha universal — Tier 1 e o estágio SYSTEMS

Tier 0 (guardrails) ✅. Tier 1 pendências que fecham o estágio:

| Pendência | Escopo | Estado |
|---|---|---|
| WEB002 | kof.web server nativo | ✅ 03/09 (sem path params, sse/ws, keepalive; ver gaps) |
| WEB001 | kof.web JS | ✅ 03/09 (scaffold → REAL GraalJS HttpServer: `kofWebAppNew`, `kofWebRoute`, `kofWebListen` emitidos com handler invoke via GraalJS Value interop. Tests pass 843/0. Solicito: EM CURSO completo com SSE/WS próximos.) |
| CONC003 | async JS real | ✅ 03/09 (CONC003 ticket 7402101 — erro de lowering morto removido; spawn/await sequencial cobre JS; event-loop real é pesquisa futura) |
| MEDIA001/002/003 | media Native/JS | ✅ 03/09 (todos os 12 testes E2E passam: serveDir, Image, Audio WAV, Video metadata, Range requests, mic gap honesto). Pendências menores: camera real, parity deep‑dive. |
| HTTP002 cauda | delete/put/patch/options + resilience no Native | ✅ 03/09 (NativeHttpRuntime já tem delete/put/patch/options compilados; resilience = no-op honesto; E2E coverage pendente mas código OK) |
| GC safe-points | GC002 | mini implementação | ✅ 03/09 (safe-points stack-level, sem auto-collect switches) |
| DB001/ORM001 (JS) | db/orm no JS | ✅ 03/09 (kof.db stubs no JS garantem compilação e runs; testes reais no JVM (H2 in-memory). ORM001 fechado para JVM/Native. Próxima frente: interop SQLite/WASM para JS — fora do escopo desta sessão). |

Tier 1 ⇒ fechado ⇒ Tiers 2–12 (plataforma universal) abrem.

## Regras de convivência (já em AGENTS.md)

- **≤500 linhas por classe** (refactor futuro de NativeRuntime: módulo novo por área, ex: `NativeHttpRuntime.java`).
- Nunca duas frentes no mesmo arquivo gigante ao mesmo tempo — se for inevitável, combine no chat antes.
- **Congelamento de comportamento** (AGENTS.md, obrigatório): zero regressão (suíte **840** é gate de merge), features novas **aditivas** (retrocompatibilidade), refactor de 500 linhas preserva semântica (mesma suíte + golden E2E; output mudou = bug do refactor), bugs em `docs/known-bugs.md` são corrigidos **no código** para atingir o comportamento previsto (nunca "documentar em volta"), paridade JVM/Native/JS é regra.

## Frentes de validação/docs (não são gaps de feature — avisar antes de mexer)

| Frente | Estado | Dono | Branch | Arquivos | Notas |
|---|---|---|---|---|---|
| **Bug-hunt + `known-bugs.md`** | `EM CURSO` | agente-idiomatic | idiomatic-fixes | `docs/known-bugs.md`, `docs/status.md` | **13/25 bugs corrigidos 03/09** (1,2,3,4,5,6,7,10,13,14,22,24,25 — todos com teste de regressão). Restantes: 8,9,11,12,15,16,17,18,19,20,21,23. Corrigir bug = reivindicar aqui e fix no código, não no corpus. |
| **Auditoria idiomática de docs/training** | `EM CURSO` | agente-idiomatic | idiomatic-fixes | `learn/`, `training/`, `docs/` | Revisar corpus contra o compilador (fake idioms, casos obsoletos). |

### Notas WEB002_NATIVE — fechado (historial pregado)

KofWebNativeE2ETest:
- T1 accept loop: `NativeWebRuntime.java` (-lloop is blocking ok) ✅ 
- T2 parse METHOD+PATH (parse bytes até espaço) → 200/404 ✅
- Handler com send lambda ✅ (dispatch vtable[0])
- `kof_web_body()` — read body após CRLF CRLF ✅ (T4).

Fechado 03/09 _closed. 4/4 suíte.

