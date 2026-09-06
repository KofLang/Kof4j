# Runbook — adicionar um namespace stdlib (`kof.<ns>`)

> Padrão verificado contra o código (05/09 → **atualizado 06/09 pós-merge** do
> REFACTOR-500). Serve para implementar TIER 6–12 de forma mecânica. Após o
> merge beta-0.3.0, as camadas semântica (`SemanticAnalyzer`/`Parser`) e de
> runtime (`JvmRuntime`/`NativeRuntime`) **já estão ≤500** (módulos). Os pontos
> de toque reais mudaram: resolver/baixar uma chamada acontece nos *lowerers* e
> *typers* modulares, não mais direto no gigante.

## Os 8 passos (na ordem)

1. **`Kof<Ns>.java`** (classe nova, ≤500) — a fachada de dispatch:
   - `static boolean is<Ns>Namespace(String name)` — `"workflow".equals(name)` `||` `"batch".equals(name)` …
   - `static <Ns>Call staticCall(String method, List<Type> argTypes)` → resolve para um
     record `call(function, parameterTypes, returnType)` ou `null`.
   - `static boolean supportedOn(Target target)` — paridade honesta (R7): gate de compile-time.
   - (exemplo real: `KofMq.MqCall`, `KofProcess`.)

2. **Tipar a chamada** (pós-merge, camada modular):
   - Chamada **top-level** (`service()`) → `BuiltinCallTyper.java` (resolve função
     top-level; é aqui que `extern` vira contrato, nunca `SEM015`).
   - Chamada com **receiver de namespace** (`kof.<ns>.x()`) → `SemExpressionTyper` /
     `MemberCallTyper` resolve o tipo do receiver; exceção de namespace na cadeia
     `SEM011` nas declarações de `IdentifierExpr` é feita via `Kof<Ns>.is<Ns>Namespace`.

3. **Baixar para `KofCall`** (lowering, camada modular):
   - Chamada **estática/builtin** → `ExpressionStaticCallLowerer.java` (o padrão do
     bloco `kof_now`/`kof_ui_emit`; `ExternCallLowerer.java` é o pedaço FFI).
   - Chamada de **instância** (`app.x()`) → `ExpressionMethodCallLowerer.java` /
     `ExpressionInstanceDispatchLowerer.java` (dispatch do receiver).
   - resolve via `Kof<Ns>.staticCall(...)`, checa `supportedOn(target)` → gap `XXX00x`,
   - emite `ops.add(new KofCall(Kof<Ns>.NS, nsCall.function(), …))`.

4. **`JvmRuntime.java`** (orquestrador, 132 linhas) — concatenar `Jvm<Ns>Runtime.source()`
   na `source()` (e `uses<Ns>` se for gate por capability, como FFI/Vulkan).

5. **`JvmRuntimeCallDescriptors.java`** — casos `kof_<ns>_*` (descritor JVM +
   tipo de retorno em ambos os `switch`).

6. **`Jvm<Ns>Runtime.java`** (classe nova, ≤500) — o corpo do runtime como source string
   (padrão `JvmFfiRuntime`/`JvmMediaRuntime`).

7. **`NativeRuntime.java`** (orquestrador, 142 linhas) → `Native<Ns>Runtime.java` novo,
   concatenado + asm x86_64 (padrão `NativeHttpRuntime`/`NativeDbPrepared`, ≤500/módulo).
   ⚠️ A emissão nativa ainda passa pelo `NativeBackend.java` (Fase 3 do refactor
   EM CURSO por agente-idiomatic) — não tocar até a lane fechar.

8. **`JsBackend.java`** — emit JS real ou gap honesto (`XXX00x`, nunca stub silencioso).

> Status dos gigantes (pós-merge 06/09): `NativeBackend` 8834 (F3), `CompilerDriver`
> 3487 (F2), `JvmBackend` 1405 (Fase 4) — todos com dono em `beta-0.3.0`. Os passos
> 2/3/7 que tocam esses arquivos só fecham quando a lane deles fechar; o resto
> (typers/lowerers modulares, runtime JVM/descritores) já é destravável hoje.

## Regra de negócio por passo

- Semântica nova (tipo novo, operador, mudança de ordem de avaliação) → **congelada**:
  vira plano em `planning-*`, nunca edição (ex.: `Secret`/`KeyHandle` no TIER 9).
- Capacidade pesada (Arrow/BLAS/liboqs/SSH) → **FFI**, nunca reimplementar.
- Todo namespace nasce com **paridade JVM/Native/JS ou gap diagnosticado** (R7).

## TIER 6–12 — API de cada namespace (referência rápida)

| Tier | Namespace | Call (uso idiomático) | Gaps esperados |
|---|---|---|---|
| 6 | `kof.workflow` / `kof.batch` | `wf.job("x") { }`, `wf.pipeline(...)`, `wf.retry(3)`, `wf.deadLetter(...)` | build sobre `spawn`/`scheduler`/`process` (todos existentes) |
| 6 | `kof.shell` | `shell.run("ls -la")` (sobre `process.run`) | wrapper fino, sem nova semântica |
| 6 | `kof.ssh` | `ssh.connect(host){ exec; scp }` → FFI libssh2 | FFI001/002 (nunca reimplementar SSH) |
| 7 | `kof.infra` | `infra "prod" {}` (codegen), `infra.plan/apply/destroy` | codegen via `CodegenStep` + providers FFI/REST/CLI |
| 8 | `dataframe` + `kof.ml` | `df.select/filter/groupBy`, `ml.infer(model, x)` → FFI Arrow/ONNX | FFI + package manager |
| 9 | `kof.security` (exp) | `Secret`/`KeyHandle`, `keys.generate/derive/rotate`, PQC | tipo novo (bump) + FFI liboqs |
| 10 | `hpc` | `hpc.gemm`, `hpc.fft` → FFI BLAS/LAPACK | FFI + scoped resources (GPU handle) |
| 11 | `kof-bio` (pacote) | `bio.fasta(...)`, `bio.fastq(...)` | pacote oficial, FFI htslib |
| 12 | integração | package manager maduro + LSP por domínio + corpus | depende de tudo |

## Ordem de retomada

```
REFACTOR-500 → FFI struct → infra (T7) → automation (T6) → data (T8)
  → security-exp (T9) → sci (T10) → bio (T11) → universal (T12)
```

Referências: `docs/future/blockers/BLOCKERS.md` (bloqueador + plano por tier),
`docs/future/IMPLEMENTATION_PLAN.md` (status por subtarefa).