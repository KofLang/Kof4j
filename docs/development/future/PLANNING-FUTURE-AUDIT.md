# Auditoria: `planning-future` × `docs/development/future` (07/09)

> Pergunta: a branch terminou de implementar o que o plano documenta?
> **Resposta: NÃO — entregou a maior parte da plataforma de migração
> legado (Fases A/B/C-parcial/E/F/G/H, 33 testes), mas Fase D não existe,
> FFI/Codegen foram DESCARTADOS num merge, e o HEAD da branch NÃO COMPILA
> contra a beta atual.**

## 1. O que a branch tem (125 commits únicos, 16 arquivos de código)

### Plataforma de migração legado (Tiers 3–5, Fases A–H) — implementada

| Fase | Entregue | Arquivo | Prova |
|---|---|---|---|
| A — `kof inspect` | ✅ estrutura de `.class`/`.jar` + estatísticas IR | `Inspect.java` (221) | via MigrateTest |
| B — Bytecode IR | ✅ decoder com tabela de tamanhos, alvos de branch | `BytecodeReader.java` (206) | DecompileTest |
| C — Control Flow | ⚠️ PARCIAL: basic blocks, CFG, back-edge/loop, try/catch/finally; **switch/athrow opacos** (javadoc: "por ora") | `BytecodeReader` + `BytecodeDecoder` (763) | `recoversWhileLoop`, try/catch |
| D — Type Recovery | ❌ **NÃO IMPLEMENTADA** — 0 ocorrências de instanceof/checkcast no decoder | — | — |
| E — Decompiler | ✅ skeleton estrutural + recuperação de corpos (aritmética, comparação, if/else-return, while, try/catch/finally); stub honesto fora do subconjunto ("nunca inventa") | `Decompile.java` (220) | `DecompileTest` **15** |
| F — Java Translator | ✅ subset real: classes/campos/métodos/records/if/while/for/strings; `equals`→`==`, sem `new`, static→top-level | `Translate.java` (834) | `TranslateTest` **9** |
| G — Differential | ✅ stdout/exit/stderr + side-effects de arquivo | `Compare.java` (222) | `CompareTest` **6** (inclui `divergenceDetected`) |
| H — Migration Reports | ✅ relatório rastreável (recuperado vs revisão manual) | `Migrate.java` (174) | `MigrateTest` **3** |
| Confidence Model | ✅ enum EXACT/WITH_METADATA/INFERRED/HEURISTIC/UNKNOWN (`7e6fbe8`) — **PERDIDO no merge `c9fcd41`** (não está no HEAD) | `Confidence.java` | usado em Decompile/Migrate |

CLI registrada no `Main.java`: `inspect`/`decompile`/`translate`/`compare`/`migrate` + `new` (scaffold com kof.toml).

### AppManifest / `kof new` (I1–I4 do APPLICATION_MODEL)
`AppManifest.java` (182) + `CmdNew.java` (141): gera projeto com `kof.toml`,
`[system]`/`[dev.ports]`, `serve` lê o toml, `build` empacota.
⚠️ **Colisão com a beta**: a beta tem `KofProjectConfig` (meu F2) — DOIS
parsers de kof.toml. Reconciliar no merge (um chama o outro, ou um substitui).

### Fixes de bugs (já na beta ou equivalentes)
#37/#40 (na beta), #47 retry/circuit HTTP nativo (a beta tem), #48
json.decode<List<Record>> interpretador (na beta `41d989a`), #49 try
aninhado KofJS (na beta `5d6e68a`).

## 2. O que o plano documenta e a branch NÃO tem

1. **Fase D — Type Recovery** (LEGACY_IR, Tier 4.2): análise de
   `instanceof`/`checkcast`/`new` + data flow → **zero código**.
2. **FFI formalizado (TIER 2.1)** — o doc de reconciliação da própria
   branch (`planning-future-reconcile.md`) afirma entregue (`extern`,
   FFI001/FFI002, `NativeFfiRuntime`/`JvmFfiRuntime`, commit `dd07cb0`),
   mas **não existe no HEAD**: o merge `c9fcd41` ("favor beta") o
   descartou. Está só no histórico.
3. **Codegen hook (TIER 2.2)** — `CodegenStep`/`runCodegen`: idem, não
   está no HEAD. (ct-eval 2.3: `OptimizerConstantFold` já existia na
   beta — não é entrega deles.)
4. **`kof inspect --java`** (task Java-Inspect-CLI do IMPLEMENTATION_PLAN):
   não implementado (Inspect só lê `.class`).
5. **Decompiler-Confidence** (task: IR marca inferred vs exact): o enum
   existiu (`7e6fbe8`) mas foi perdido no merge — `Decompile.java` do HEAD
   importa `dev.kof.compiler.Confidence` que **não existe**.

## 3. Estado do merge (CRÍTICO)

**O HEAD da branch NÃO COMPILA nem sozinho** (provado em worktree isolado
07/09 — os CLI-files quebram contra o PRÓPRIO parser da branch):
- o merge `c9fcd41` ("favor beta") substituiu o parser **rico** da branch
  (514 linhas, com `returnTypeName()`, `parameterTypeNames()`,
  `instanceofCount`, `checkcastCount`, `m.code`/`CodeAttribute`,
  `constantPool[]`, `Instruction`, `BasicBlock`, `disassemble`) pelo parser
  **pobre** da beta (196 linhas, só `magic/thisClass/methods/fields/...`)
  — os dois são byte-idênticos no HEAD;
- os consumidores (`Decompile`/`Inspect`/`Migrate`) ficaram referenciando
  a API rica perdida → ~8 erros `cannot find symbol` + import stale
  (`dev.kof.compiler.ClassFileParser` → o SOLID moveu p/ `parser.`) +
  `Confidence.java` perdido (import não resolve);
- o parser rico existe só no histórico: `34ded81` (Type Recovery) e
  `42d51cc` (fix decompilador), ANTES do move SOLID `190b393`.

**Consequência para R1:** portar a plataforma exige restaurar o parser
rico (de `34ded81`, realocado p/ `parser.`) + `Confidence.java`
(`7e6fbe8`) + ajustar imports dos 5 CLI-files — não é só "corrigir import".

## 4. Veredito e próximos passos

**Veredito:** trabalho real e de qualidade (33 testes, honestidade R6 —
"nunca inventa", stubs com Confidence), mas **incompleto** (Fase D zero,
FFI/Codegen perdidos) e **não mergeável como está** (HEAD quebrado contra
a beta).

**Reconciliação (fila, na ordem):**
1. **R1** — portar a plataforma de migração para a beta: restaurar
   `Confidence.java`, backportar os membros do `ClassFileParser`
   (returnTypeName/parameterTypeNames/instanceofCount/checkcastCount/code/
   constantPool) para `parser.ClassFileParser`, ajustar imports, rodar os
   4 testes (33) na beta.
2. **R2** — reconciliar kof.toml: `AppManifest` (branch) × `KofProjectConfig`
   (beta) — um parser só (provável: AppManifest consome KofProjectConfig,
   ou vice-versa; decisão de design se fundir semânticas).
3. **R3** — decidir FFI: re-portar `dd07cb0` (extern/FFI001/FFI002 +
   runtimes) ou registrar como perdido e reescrever (TIER 2.1 do plano).
4. **R4** — Fase D (Type Recovery) — o gap real do plano (Tier 4.2).
5. **R5** — `inspect --java` + switch/athrow recovery (completar C).
