# Plano de Ação — Implementação de `docs/future`

**Criado:** 01/09/2026
**Escopo:** todos os planos em `docs/future/` — `DECOMPILER.md`,
`DIFFERENTIAL_TESTING.md`, `LEGACY_IR.md`, `LEGACY_MIGRATION.md`,
`TRANSLATOR.md`, `PLAN-UNIVERSAL-PLATFORM.md`.
**Dificuldade:** `E` fácil (processo/pouco código) · `M` médio (feature
contida) · `H` alto (arquitetural) · `R` pesquisa.

> Regra transversal (R12): nenhum item de plano futuro é **ação** sobre o
> estado atual. Toda fase abaixo move o doc correspondente de `future/` para
> `docs/` assim que tiver código, documentando estado real + "como finalizar".

---

## TIER 0 — Guardrails e processos (E, custo ≈ zero)

| # | Item | Serve | DoD |
|---|------|-------|-----|
| 0.1 | Adotar R1/R5/R6/R7/R9/R10/R11/R12 como invariantes (AGENTS.md + roadmap.md) | UNIVERSAL §10/§15 | ✅ 01/09 AGENTS.md + roadmap §22 |
| 0.2 | Convenção de gaps por domínio (`INFRA00x`, `DATA00x`, `SCI00x`, `BIO00x`, `SECPQ`) + matriz de paridade | UNIVERSAL R6 | ✅ 01/09 `docs/backend-parity.md` |
| 0.3 | Tiers de estabilidade `stable`/`experimental` | UNIVERSAL R5 | ✅ 01/09 `docs/backend-parity.md` |
| 0.4 | Regra `future/ → docs/` em cada fase | future/README | aplicada em cada fase |

## TIER 1 — Fechamento do estágio SYSTEMS (M a H, pré-requisito R12)

> Não é "futuro", mas o UNIVERSAL **proíbe** abrir domínio novo antes (R12;
> §10 Estágio 1). Fechar antes de Tier 6+.

1.1 Fechar gaps de paridade: `HTTP002` (http Native), `WEB001` (JS), `WEB002`
(Native web), `CONC003` (async JS real), `LOG001` (✅ 01/09 — JS `console.*` +
`KOF_LOG_LEVEL`, `KofLogE2ETest` 11), `MQ001`, `SCHED001`/
`TIME001`, `SECN002` (AES-GCM JS), `OBS002`, `MEDIA*` — **M**
> Fix de infra 01/09: runtime JVM agora inclui o bloco Vulkan só quando o
> programa usa `kof.vk` (capability/link-por-uso — R2); `--enable-preview`
> (FFM preview API no JDK 21) só para programas Vulkan.
1.2 GC mark-sweep automático no Native — **H**
1.3 Query DSL tipada (`kof.db` nível 3: `User.query {}`) — **M**
1.4 Package manager MVP (`kof init`, `kofdeps`, registry) + generalizar
capability/link-por-uso (R2) — **M–H**
1.5 Tracing/OpenTelemetry + lifecycle `application{}` — **M**
> ✅ 01/09 — spans W3C com timing (`observability.spanStart/spanEnd`, 3
> targets); lifecycle `application { onStart/onShutdown }` (desugar →
> prólogo/epílogo do main, 3 targets). OpenTelemetry full (export) pendente.

## TIER 2 — Fundações de compilador para a plataforma (M, §7)

2.1 **FFI formalizado** (R3): assinatura `.so`/função externa em compile-time — **M**
2.2 **Codegen de compile-time formalizado** (R4): `KofRuntime`, runner de
teste, DDL de `entity`; base de `infra "prod" {}`, stubs gRPC — **M**
2.3 Compile-time eval leve (estender otimizador) — **E–M**
2.4 Scoped resources (RAII leve, sem ownership) — **M**
2.5 Variance/sealed (opcional) — **M**

## TIER 3 — Plataforma de migração legado — fundação (E a M)

3.1 Design + protótipo do **Legacy Semantic IR** (modelo, Confidence Model,
Unknown\*, Source Mapping) — `LEGACY_IR` (Fases B/C/D)
3.2 **`kof inspect <input>`** (Fase A): análise estrutural de `.class`/`.jar`
— `LEGACY_MIGRATION` §3.1
3.3 **Fase B — JVM Bytecode IR**: Class File → Bytecode IR linear — `LEGACY_IR`

## TIER 4 — Plataforma de migração legado — recuperação semântica (H)

4.1 **Fase C — Control Flow Recovery**: basic blocks, CFG, branches, loops,
switches, exception regions → `try/catch/finally`
4.2 **Fase D — Type Recovery**: primitives, referências, arrays, generics,
herança + Data Flow Analysis

## TIER 5 — Plataforma de migração legado — geradores e verificação (M a H)

5.1 **Fase E — Kof Decompiler**: Legacy Semantic IR → Kof AST → fonte
idiomático (equivalência semântica primeiro; nunca inventar) — **H**
5.2 **Fase F — Java Translator**: parser Java (subset → completo) → Kof AST;
protótipo primeiro (classes, campos, métodos, `if`/`while`, strings) — **M→H**
5.3 **Fase G — Differential Testing**: legado vs Kof com mesmos vetores,
comparando saídas observáveis (stdout/stderr/exit/exceptions/arquivos/DB) — **M–H**
5.4 **Fase H — Migration Reports**: `kof migrate` com relatório de
rastreabilidade — **M**
5.5 CLI `kof decompile`/`translate`/`migrate`/`compare` (todos via frontend) — **M**

## TIER 6 — Plataforma universal — AUTOMATION (M, estágio 2)

`kof.workflow`/`kof.batch` (jobs, pipelines, retry, checkpoints, dead-letter)
· `kof.shell` (sobre `kof.process`) · `kof.ssh` (FFI) · cron/scheduler maduro ·
`kof workflow run` · CI/CD como código Kof. **Não:** reimplementar bash; jobs
como código, nunca YAML.

## TIER 7 — Plataforma universal — INFRAESTRUTURA / Kof Makealive (M–H, estágio 3)

`kof.infra` (records de recurso + grafo + diff) · `infra "prod" {}` (sacar
sobre records — codegen, não HCL) · reconciliation loop · state em `kof.db` ·
providers via FFI/REST/CLI · `kof infra plan/apply/destroy`. Deps: 1.4, 2.1, 2.2.

## TIER 8 — Plataforma universal — DATA (H, estágio 4)

`dataframe` tipado (lazy, colunar) · **Arrow/Parquet por FFI** (wrapper, nunca
reimplementar) · estatística (wrapper+FFI) · `kof.ml` (inferência via
ONNX/libtorch FFI; treinamento orquestrado) · visualização leve ·
experiment tracking. Deps: 2.1, package manager.

## TIER 9 — Plataforma universal — SECURITY expansion (E a R, estágio 5 + §4.8.1)

S1 fechar `SECN002` · S2 tipo `Secret`/`KeyHandle` + redaction forçada +
`SECD00x` · S3 `keys.*` (generate/derive-HKDF/rotate/store/revoke) ·
S4 assimétrica (ECC/RSA) + ChaCha20-Poly1305 + X.509/PEM · S5 **PQC**
(ML-KEM-768/ML-DSA-65 via `liboqs`, vetores NIST) · S6 híbrido
(KEM+HKDF+AES-256-GCM, anti-downgrade) · S7 `secure.channel` + TLS
multi-target · S8 paridade honesta (`SECPQ`) · `kof.net`/forense/threat-intel.
**Regra absoluta:** só FFI a lib auditada; nunca cripto caseira.

## TIER 10 — Plataforma universal — SCIENTIFIC (H/R, estágio 6)

BLAS/LAPACK por FFI (wrapper) · SIMD/vectorização Native (pesquisa) · GPU
(generalizar FFM Vulkan; CUDA/OpenCL por FFI) · distributed (FFI MPI) ·
profiling HPC. Deps: 2.1, 2.4, 1.2.

## TIER 11 — Plataforma universal — BIO (M–H, estágio 7)

`kof-bio` (pacote oficial): FASTA/FASTQ/VCF/BAM records · alinhamento/
variantes via FFI/CLI (BLAST/htslib) · pipelines genômicos (workflow +
checkpointing) · lab automation. Deps: 6, 8, 10.

## TIER 12 — Plataforma universal — UNIVERSAL (H, estágio 8)

Integração total · package manager maduro · LSP/debug/profiler por domínio
(mesmo frontend) · deploy multi-alvo · corpus `training/` dos domínios.
**Teste final:** o core da linguagem deve ter crescido **quase nada**.

---

## Regras transversais (todos os itens)

1. **Loop de verificação do AGENTS.md**: compilar → testes da área → suíte
   completa → lint.
2. **DoD por feature**: 3 targets ou gap diagnosticado, E2E por target,
   benchmark/stress quando plausível, `training/` + docs sincronizados, suíte
   verde.
3. **Nunca silencioso**: todo gap vira diagnóstico compile-time.
4. **Tooling sempre sobre o mesmo frontend** (R8): nunca parser paralelo.
5. **Mover `docs/future/*` → `docs/`** quando a fase tiver código.
6. **Non-goals** (UNIVERSAL §12/§7): sem macros abertas/type-classes/
   annotations/ownership/effect system; sem cripto caseira; sem reimplementar
   Arrow/BLAS/ML/alinhadores; sem "Kali em Kof"; sem target por domínio.