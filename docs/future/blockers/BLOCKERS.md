# BLOCKERS — o que impede a conclusão de `docs/future` + plano de execução

**Data:** 05/09/2026 · **Branch ativa:** `planning-future`

> Nada aqui é "esquecido". Cada item bloqueado tem: o **bloqueador**, o que ele
> **bloqueia**, o **estado/dono** e um **plano de execução** para destravar.
> Quando um bloqueador cair, o agente retoma pela ordem abaixo.

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

- **Bloqueador:** R12 (frentes novas só após SYSTEMS fechar) + dependência das
  fundações (#2 FFI, scoped resources, package manager).
- **Escopo (postergado, não esquecido):** AUTOMATION (`kof.workflow`), INFRA (#4),
  DATA (Arrow/Parquet FFI), SECURITY expansion (PQC), SCIENTIFIC (BLAS/GPU),
  BIO (`kof-bio`), UNIVERSAL (integração).
- **Plano de execução:** seguir `docs/future/ACTION_PLAN.md` Tiers 6–12 na ordem,
  cada um com FFI/interop primeiro (nunca reimplementar Arrow/BLAS/CUDA/ML).

## 6. Decompiler — sem equivalente Kof (permanente)

- **Bloqueador:** não há `kof.math`, nem `StringBuilder` (concatenação usa `+`).
- **Comportamento:** `Math.*`, `Integer.parseInt` (já mapeado), `StringBuilder.*`
  caem no **stub honesto** `throw "body not recovered"` (`Confidence UNKNOWN`).
- **Plano de execução (opcional):** se um dia houver `kof.math`/conversões,
  estender `mapStaticCall`/`mapStdlib` em `BytecodeDecoder` — sem isso, o stub
  honesto é o correto (nunca inventar).

---

## Ordem de retomada (quando um bloqueador cair)

```
#1 REFACTOR-500 → #2 FFI struct → #4 infra → #5 Tiers 6–12 → (#3 quando bump) (#6 opcional)
```

Referências cruzadas: `docs/future/IMPLEMENTATION_PLAN.md` (status por subtarefa),
`docs/future/action-plan.md` (TIERs), `DOING.md` (dono/estado de cada gap).