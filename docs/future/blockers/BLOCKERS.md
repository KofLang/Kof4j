# BLOCKERS — o que impede a conclusão de `docs/future` + plano de execução

**Data:** 05/09/2026 · **Branch ativa:** `planning-future`

> Nada aqui é "esquecido". Cada item bloqueado tem: o **bloqueador**, o que ele
> **bloqueia**, o **estado/dono** e um **plano de execução** para destravar.
> Quando um bloqueador cair, o agente retoma pela ordem abaixo.

## Estado atual — migração legado (Fases A–H + TIER 2) CONCLUÍDA e CONGELADA

A plataforma de migração legado e as fundações estão **implementadas e testadas**
(suíte verde) — e **congeladas** até o REFACTOR-500 fechar (não tocar nas classes
gigantes em refactor):

- ✅ `kof inspect` / `decompile` (12 capacidades + stdlib map) / `translate`
  (record/enum/ternário/lambda/boxed) / `compare` (stdout/stderr/exit/**files**)
  / `migrate` (relatório traceable).
- ✅ FFI (`Int`/`String`/`Double`/`Int[]` em JVM+Native), codegen hook
  (`CodegenStep` + `desugarEntity`), ct-eval (string-concat fold).
- ✅ Confidence Model (`exact`/`unknown`), salvaguardas de release.

**Retomada:** assim que o REFACTOR-500 fechar, seguir a ordem abaixo (#1 → #2 → …).

---

## 1. REFACTOR-500 (regra ≤500 linhas)

- **Bloqueador:** divisão das classes gigantes em andamento.
- **Estado:** `EM CURSO` — agente-idiomatic (Fases 1–3+9: `NativeRuntime`,
  `CompilerDriver`, `NativeBackend`); fixes-for-kofagent (Fases 4–8: `JsBackend`,
  `JvmRuntime`, `SemanticAnalyzer`, `Parser`). Ver `docs/refactoring/PLAN-SOLID-500.md`.
- **Bloqueia:** qualquer código que toque `CompilerDriver`/`NativeRuntime`/etc.
  — em especial o **FFI struct/array marshalling** (2.1.6), cujo lowering
  (`isExternBound`, `externSignatures`, branch `kof_ffi_*`) vive no `CompilerDriver`.
- **Plano de execução (ao fechar):**
  1. Rebase/merge do refactor na `planning-future` (nunca merge de `beta-*`; usar `git cherry-pick` se preciso — regra de salvaguardas).
  2. Reaplicar o lowering de FFI (`isExternBound` + branch do `case MethodCallExpr`) na estrutura modularizada do `CompilerDriver`.
  3. `mvn test` suíte completa (gate) + `FfiE2ETest`.

## 2. FFI struct/array marshalling (TIER 2.1.6-restante)

- **Bloqueador:** #1 (REFACTOR-500). Já existem `Int→Int`, `String→Int`,
  `Double→Double`, `Int[]→Int` em JVM+Native (`JvmFfiRuntime`/`NativeFfiRuntime`).
- **Falta:** `struct` (records Kof ↔ `struct` C, layout ABI), arrays de outros
  tipos, ponteiros/`char*` de retorno.
- **Plano de execução:**
  1. Após #1: generalizar o lowering para mais assinaturas (`Long`, `Bool`,
     arrays de tipos variados) e `struct` (records → `MemorySegment`/ABI).
  2. A fronteira segura de buffer (lifetime pelo GC) → junto com o scoped-resources (#5).

## 3. Semântica congelada (0.2.6-beta) — features de linguagem novas

- **Bloqueador:** regra "semântica congelada" (AGENTS.md) — mudança de
  contrato/sintaxe exige **bump de versão** + discussão, nunca adição silenciosa.
- **Bloqueia:**
  - **Scoped resources (`using`)** — design pronto em `docs/future/scoped-resources-plan.md`.
  - **Variance/sealed** — decisão: deferir (`enum` cobre closed-alternatives).
- **Plano de execução:**
  1. Quando abrir a linha `0.3.0` (ou `1.0`): ativar o desugar `using` no
     pipeline `CodegenStep` (`CompilerDriver.runCodegen`), conforme o design.
  2. `variance/sealed`: reabrir só se uma pipeline científica exigir type-safety covariante.

## 4. `infra "prod" {}` (TIER 7 / ACTION_PLAN 2.2.4)

- **Bloqueador:** depende de FFI formalizado (#2) + package manager (`kof deps`,
  FEITO). É infra-as-code — TIER 7, não TIER 2.
- **Plano de execução:**
  1. Após #1/#2: `infra` como records de recurso + grafo + diff (codegen fechado,
     reusando `CodegenStep`), providers via FFI/REST/CLI.
  2. Deps: `kof deps` (MVP) já resolve; falta generalizar capability/link-por-uso.

## 5. Tiers 6–12 (universal platform)

> Atualizado 06/09 pós-merge do REFACTOR-500: a camada semântica/runtime JVM
> está modular (typers/lowerers/`Runtime*` ≤500), então os namespaces **JVM-first**
> são destraváveis hoje. O que bloqueia é o **lado Native/JS** (passa por
> `NativeBackend`/`JsBackend`, F2/F3 em curso) e **FFI pesado** (#2).

### TIER 6 — AUTOMATION (`kof.workflow` / `kof.batch` / `kof.shell` / `kof.ssh` / cron)

- **FEITO (JVM-first, 06/09):** `kof.workflow` (`job(name){}/run/pipeline`, gap
  WF001) + `kof.shell` (`run(cmd)` via `sh -c` sobre `kof.process`, gap SHL001) +
  **cron maduro** no `scheduler.at` (parse 5 campos + próxima ocorrência, resolução
  de minutos). Padrão: `KofXxx` + `ExpressionXxxCallLowerer` + `JvmXxxRuntime` +
  `BuiltinCallTyper`/`MethodCallTyper`/`SemExpressionTyper` + `JvmRuntimeCallDescriptors`.
- **Resta:** lados Native/JS (F2/F3) e `kof.ssh` (**FFI** libssh2, #2). `kof.batch`
  (checkpoint/dead-letter) é marginal — reavaliar após workflow amadurecer.

### TIER 7 — INFRA (`kof.infra` / `infra "prod" {}`)

- **Bloqueador:** `infra "prod" {}` é código novo de parser+semanântica (desugar
  via `CompilerDesugar`/`desugarEntity` — destravado) + providers FFI/REST/CLI (#2).
- **Plano:** 1) `infra "prod" {}` como records de recurso + grafo + diff via
  desugar; 2) reconciliation loop com `spawn`/`scheduler`; 3) state em `kof.db`;
  4) CLI `kof infra plan/apply/destroy`. *Uso tipado, nunca HCL.*

### TIER 8 — DATA (`dataframe` / Arrow / `kof.ml`)

- **Bloqueador:** FFI formalizado (#2) + package manager (carregamento de
  dependências pesadas). NOVO conceito: `dataframe` (lazy, colunar).
- **Plano:** 1) Arrow/Parquet por **FFI** (bindings JVM `Apache Arrow`, nunca
  reimplementar); 2) wrapper tipado (records + `df.select/filter/groupBy`);
  3) `kof.ml` como inferência via FFI (ONNX Runtime / libtorch) — treinamento
  orquestrado por fora. Deps: #2.

### TIER 9 — SECURITY expansion

- **Já feito:** `kof.security` (password/hash/jwt/aesGcm/rateLimit) — `SECN002`
  (AES-GCM JS) fechado; `OBS002`, paridade crypto JVM/Native/JS.
- **Bloqueador:** `Secret`/`KeyHandle` é **tipo novo** (precisa semântica/parser
  = #3 semântica congelada); `keys.*` (generate/derive-HKDF/rotate) e **PQC**
  (ML-KEM-768/ML-DSA-65) são **FFI** a `liboqs` (#2).
- **Plano:** 1) S1/S2: tipo `Secret` + redaction forçada (nova semântica → bump);
  2) S3 `keys.*` sobre FFI (liboqs/JCA auditada); 3) S5 PQC via `liboqs` FFI
  (vetores NIST); 4) S6 híbrido KEM+HKDF+AES-256-GCM. *Nunca cripto caseira.*

### TIER 10 — SCIENTIFIC

- **Bloqueador:** FFI formalizado (#2) + scoped resources (#3, para handles GPU)
  + GC safe-points (1.2, fora do escopo atual).
- **Plano:** 1) BLAS/LAPACK por FFI (wrapper, nunca reimplementar); 2) GPU:
  generalizar FFM Vulkan (já há `kof.vk`) + CUDA/OpenCL por FFI; 3) distributed
  via MPI FFI. *Kof fornece a fronteira; o motor fica por fora.*

### TIER 11 — BIO (`kof-bio`)

- **Bloqueador:** depende de TIER 6 (workflow) + 8 (data) + 10 (sci) — todos
  bloqueados por #2.
- **Plano:** 1) `kof-bio` como **pacote oficial** (não stdlib): FASTA/FASTQ/VCF/BAM
  como records; 2) alinhamento/variantes via FFI/CLI (BLAST/htslib), nunca
  reimplementar; 3) pipelines genômicos via TIER 6 (workflow+checkpoint). Deps 6/8/10.

### TIER 12 — UNIVERSAL

- **Bloqueador:** depende de tudo (Tiers 6–11).
- **Plano:** 1) package manager maduro + capability/link-por-uso generalizado;
  2) LSP/debug/profiler por domínio (mesmo frontend — R8); 3) deploy multi-alvo;
  4) corpus `training/` por domínio. *Teste final: o core cresceu quase nada.*

---

## Ordem de retomada (quando um bloqueador cair)

```
#1 REFACTOR-500 → #2 FFI struct → #4 infra (TIER 7) → TIER 6 automation
  → TIER 8 data → TIER 9 security-exp → TIER 10 sci → TIER 11 bio → TIER 12 universal
  (#3 scoped/Secret no bump) (#6 Math. opcional)
```

Referências cruzadas: `docs/future/IMPLEMENTATION_PLAN.md` (status por subtarefa),
`docs/future/action-plan.md` (TIERs), `DOING.md` (dono/estado de cada gap).