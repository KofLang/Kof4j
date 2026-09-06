# Runbook — adicionar um namespace stdlib (`kof.<ns>`)

> Padrão verificado contra o código atual (05/09) e usado por `kof.mq`,
> `kof.http`, FFI, `kof.scheduler` etc. Serve para implementar TIER 6–12 de
> forma mecânica assim que o REFACTOR-500 fechar. **Todo namespace novo exige
> tocar os gigantes** (`CompilerDriver`/`SemanticAnalyzer`/`JvmRuntime`/
> `NativeRuntime`) — por isso os tiers estão bloqueados até o refactor.

## Os 8 passos (na ordem)

1. **`Kof<Ns>.java`** (classe nova, ≤500) — a fachada de dispatch:
   - `static boolean is<Ns>Namespace(String name)` — `"workflow".equals(name)` `||` `"batch".equals(name)` …
   - `static <Ns>Call staticCall(String method, List<Type> argTypes)` → resolve para um
     record `call(function, parameterTypes, returnType)` ou `null`.
   - `static boolean supportedOn(Target target)` — paridade honesta (R7): gate de compile-time.
   - (exemplo real: `KofMq.MqCall`, `KofProcess`.)

2. **`SemanticAnalyzer.java`** — adicionar `&& !Kof<Ns>.is<Ns>Namespace(ie.name())`
   a **todas** as cadeias de exceção do `SEM011`/`SEM022` (são várias; hoje em
   `resolveExpr` de `IdentifierExpr` ~linha 890 e `AssignmentExpr` ~linha 938 —
   grepar `KofMq.isMqNamespace` acha todas).

3. **`CompilerDriver.java`** — baixar a chamada `mc` para `KofCall`:
   - no lowering de member-call (`if (mc.receiver() instanceof IdentifierExpr rid
     && Kof<Ns>.is<Ns>Namespace(rid.name()))` — padrão das linhas 4583 e 6623 do `KofMq`);
   - resolve via `Kof<Ns>.staticCall(...)`, checa `supportedOn(target)` → gap `XXX00x`,
   - emite `ops.add(new KofCall(Kof<Ns>.NS, nsCall.function(), …))`.

4. **`JvmRuntime.java`** — concatenar o runtime novo na `source()` (linhas 121–131):
   `+ Jvm<Ns>Runtime.source()` (e `uses<Ns>` se for gate por capability, como FFI/Vulkan).

5. **`JvmRuntimeCallDescriptors.java`** — casos `kof_<ns>_*` (descritor JVM +
   tipo de retorno em ambos os `switch` — padrão das linhas 35–37 e 181–185).

6. **`Jvm<Ns>Runtime.java`** (classe nova, ≤500) — o corpo do runtime como source string
   (padrão `JvmFfiRuntime`/`JvmMediaRuntime`).

7. **`NativeRuntime.java` / `Native<Ns>Runtime.java`** — asm x86_64 + dispatch
   (padrão `NativeHttpRuntime`/`NativeDbPrepared`, ≤500 por módulo).

8. **`JsBackend.java`** — emit JS real ou gap honesto (`XXX00x`, nunca stub silencioso).

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