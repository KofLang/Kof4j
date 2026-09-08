# Reconciliação planning-future ↔ beta-0.3.0

**Data:** 05/09/2026 · **Branch:** `planning-future` (não sair dela; só sincronizar com `beta-0.3.0`)

## O que a `planning-future` entrega

1. **Plataforma de migração legado** (docs/future, Fases A–H):
   `kof inspect` / `decompile` / `translate` / `compare` / `migrate` + `Confidence`.
2. **FFI formalizado** (TIER 2.1): sintaxe `extern`, gap `FFI001`/`FFI002`,
   binding real JVM (FFM) e Native x86-64 (dlopen/dlsym) — `abs`/`atoi`/`sqrt`.
3. **Codegen hook** (TIER 2.2) `CodegenStep` + **ct-eval** (2.3) string-concat folding.
4. **Decisões** 2.4 (scoped resources — design) e 2.5 (variance/sealed — deferir).

## Estado do merge (05/09)

- `planning-future` mergeada com `origin/beta-0.3.0` (commit `4997e56`).
- Conflitos resolvidos: `JvmRuntime` (gate preview = `usesExtern` + `version<22`)
  e `DOING.md` (entradas de ambos agentes preservadas).
- Versão agora `0.3.0-beta`.

## Falha pré-existente do beta-0.3.0 (NÃO é da planning-future)

`NativeE2ETest.execStringCharAt` → espera `72\n111`, obtém `H\no`.
- Reproduz **igual** no `origin/beta-0.3.0` limpo (worktree isolado confirmado).
- Causa: semântica de `println(char)`/`charAt` no Native mudou no refactor/String-methods.
- **Dono:** agente do refactor no `beta-0.3.0` — não corrigir aqui (evita conflito).

## Checklist de normalização (quando o refactor ≤500 linhas fechar)

1. `git fetch` + merge `origin/beta-0.3.0` de novo na `planning-future`.
2. Rearranchar minhas adições que o refactor mover (pontos de contato):
   - `CompilerDriver`: `externSignatures`, `isExternBound`, branch de lowering
     no `case MethodCallExpr`, `CodegenStep`/`runCodegen`.
   - `SemanticAnalyzer.findExtern` · `Parser.parseExternDeclaration` (resolvem
     o nome `extern` sem SEM015).
   - Já isoladas em classes novas: `NativeFfiRuntime`, `JvmFfiRuntime` (≤100 linhas).
3. Rodar a suíte completa + E2E: `FfiE2ETest`, `ClassFileE2ETest`,
   `DecompileTest`, `TranslateTest`, `CompareTest`, `MigrateTest`, `OptimizerTest`.
4. Reportar/rastrear `execStringCharAt` (se ainda vermelho) ao dono do refactor.

## Regra de convivência

Não inchar as classes gigantes (`CompilerDriver`, `NativeRuntime`, `JvmRuntime`, …).
Novo código FFI/migração vai em classes novas ≤500 linhas (padrão já seguido
com `NativeFfiRuntime`/`JvmFfiRuntime`).