# PLATAFORMA DE APLICAÇÃO COMPLETA — Auditoria F0 + Plano Técnico

> Fonte: auditoria de código real (evidências `arquivo:linha`) + plano por fases.
> Princípio (AGENTS.md): IR compartilhado + frontend semântico compartilhado +
> capability system + backend/target específico. Zero fallback silencioso (R6).
> Zero regressão: tudo aditivo;   (0.2.6-beta) não muda.

## F0 — ESTADO REAL (o que já existe)

### Module system (hoje)

| Mecanismo | Onde | Estado |
|---|---|---|
| Module root = LCA das fontes | `ModuleRoots.moduleRootFor` (ModuleRoots.java:13) | DONE (implícito) |
| Merge multi-arquivo em 1 unit | `CompilerPipeline.parseAndMerge` (:318-376) | DONE |
| Import dir (`import a.b` → `a/b/*.kf`) | `CompilerImports.expandKofImports` (:32-81) | DONE |
| Import arquivo (`import a.b.C`) | `CompilerImports.java:82-125` | DONE |
| Transitividade (BFS, cap 256) | `CompilerImports.java:18-153` | DONE |
| PKG003/004/005 (ilegível/pkg≠dir/dup) | CompilerImports + CompilerPipeline | DONE (estável, congelado) |
| PKG002 (1 main por módulo) | CompilerPipeline.java:364-369 | DONE (estável) |
| Qualificação de nome por import | `MemberResolver.qualifyViaImports` (:63) | DONE (lexical, sufixo) |
| Descoberta de RAIZ DE PROJETO | — | **NÃO EXISTE** |
| kof.toml / manifesto | — | **NÃO EXISTE** (só RFC APPLICATION_MODEL.md) |
| Diagnóstico de import não resolvido | — | **NÃO EXISTE** (silencioso, CompilerImports.java:126) |
| Diagnóstico de import circular | — | **NÃO EXISTE** (visitedDirs pula sem reportar) |
| Cache de módulos parseados | — | **NÃO EXISTE** (re-lê/re-parseia toda compilação) |

**Gap F1 central**: sem raiz de projeto, `kof run src/main.kf` usa `src/` como
raiz → `import shared.Validation` (fora de `src/`) falha silenciosamente.

### Targets (hoje)

`Target.java:3-9`: `JVM, NATIVE, NATIVE_RISCV64, NATIVE_AARCH64, JS, ANDROID`.
Sem `SCRIPT`, sem `WASM`. KofScript é *modo* (interpretador de IR no JVM),
não valor do enum. Gates R6 distribuídos: cada `Kof*.java` tem
`supportedOn(Target)` + `gapCode()` (14 namespaces; DB001, ORM001, GPU001,
SECN00x, MQ001, VAL001, OBS001/2...). `selectBackend` em
CompilerPipeline.java:151-162.

### CLI (hoje)

`build` (CmdBuild: jvm/native/js/android + `--apk`), `run` (CmdRun: exec 4
targets; android = print-only), `serve` (**JVM-hardcoded**, CmdServe.java:64,
sem --target, sem servir frontend estático por conta própria), `check`
(JVM fixo, Main.java:295), `test`, `script`/`repl`, `deps` (Maven/kofdeps),
`init`, `c`, `fmt`, `lsp`, `debug`, `config gen` (config de APP, não projeto).
`--target` via `KofCliSupport.parseTarget` (:48-62).

### Matriz de status por área da plataforma

| Área | Status | Evidência |
|---|---|---|
| Targets matrix + gates R6 | DONE (6 targets) | Target.java:3-9; 14× Kof*.java |
| kof.toml | NOT STARTED | zero .toml no repo |
| kof build multi-target | DONE (por target único) | CmdBuild.java:24-122 |
| kof run | DONE | CmdRun.java:103-159 |
| kof serve full-stack | PARTIAL (JVM fixo, sem frontend) | CmdServe.java:64 |
| Module resolution cross-dir | PARTIAL (funciona só sob LCA) | CompilerImports.java |
| KofJS (frontend) | PARTIAL/alpha (bundle+index.html+GraalJS; WEB001/2) | JsBackend.java:31-58 |
| KofWebAssembly | NOT STARTED | APPLICATION_MODEL.md:144-146 |
| KofAndroid | PARTIAL (projeto+APK ok; run no-op) | CmdBuild.java:130-193 |
| KofUI | PARTIAL (DOM/Router/Component/Store/Canvas em JS; JVM no-op) | KofUi.java:17-384 |
| KofScript wildcard | PARTIAL (interpret JVM; dispatch duplicado CLI/módulo) | KofScript.java:196-238 |
| Conformance matrix | NOT STARTED | docs/roadmap-audit.md |

## PLANO POR FASES (ordem; cada fase = gate de suíte verde)

### Fase 1 — Module System (cross-directory)
1. `ProjectLocator`: sobe da fonte até achar `kof.toml` → projectRoot.
   Sem kof.toml: comportamento atual (LCA) intocado — aditivo.
2. `CompilerImports`/`CompilerPipeline`: quando projectRoot existe,
   moduleRoot := projectRoot (imports resolvem a partir da raiz do projeto).
3. PKG006: import que não resolve em arquivo/dir **e** não é externo
   (android.*, kof.*, java.*, jakarta.*, wildcard `.*`) → diagnóstico.
4. PKG007: ciclo de import real (A→B→A) → diagnóstico (hoje só pula).
5. CLI: `run/build/check/serve` usam projectRoot quando kof.toml existe.
6. Testes: mesmo dir, irmão, pai, source root, package, import inválido
   (PKG006), circular (PKG007), retrocompatibilidade sem kof.toml.

### Fase 2 — Target Architecture
1. `Target`: adicionar `SCRIPT` (coringa: backend e frontend interpretados).
2. `KofProjectConfig`: parser mínimo de `kof.toml` (subconjunto INI:
   `[project] name`, `[backend] target`, `[frontend] target`, `[server] port`).
   Sem dependência nova (parser próprio ~100 linhas).
3. Validação de combinação backend×frontend (matriz §6) antes do build.
4. CLI: `--backend=X --frontend=Y` com override do kof.toml; `kof check`
   respeita --target.
5. Centralizar seleção: `TargetMatrix` (backend válido × frontend válido ×
   capacidades), consumido por build/run/serve.

### Fase 3 — Full-stack `kof build/run/serve`
1. `kof build`: compila backend (target de [backend]) + frontend (KofJS hoje)
   no mesmo projeto; artefatos em `build/backend` + `build/frontend`.
2. `kof run --backend --frontend`: roda backend + serve frontend estático.
3. `kof serve --backend --frontend`: backend JVM/Native/Script + estáticos do
   frontend (hoje CmdServe é JVM-only — refatorar para matriz).
4. Database continua capacidade do backend (kof.db/orm por target, gates R6).
5. Testes full-stack: JVM+KofJS, Native+KofJS, Script+KofJS, Script+Script.

### Fase 4 — KofUI (plataforma web completa)
- Auditoria de cobertura HTML/CSS/DOM do KofUi atual → matriz de gaps
  (elementos, atributos, eventos, forms, canvas, storage, fetch, ws, sse).
- Estender registry KofUi + JsRuntimeUi* (arquivo:elemento, diagnostics R6
  para o que não existe no target).
- `style` declarativo (CSS idiomático) — novo, com parse próprio.

### Fase 5 — KofJS (Web APIs)
- Matriz DOM/Fetch/WebSocket/Storage/... com supportedOn+gapCode.
- Completar gaps WEB001/WEB002 e APIs listadas no plano.

### Fase 6 — KofWebAssembly
- `Target.WASM` + backend WasmBackend (IR→WASM real, não JS renomeado).
- Browser runtime bridge (DOM/events via JS glue mínimo).
- Paridade com KofJS via matriz de capacidades.

### Fase 7 — KofAndroid
- APIs Android idiomáticas (Activity/Lifecycle/Intent/Permissions) com
  supportedOn(Target.ANDROID)+gapCode; run Android real (emulador/device).

### Fase 8 — KofScript coringa
- `Target.SCRIPT` no enum; backend=script (interpret) e frontend=script
  (SSR/HTTP dinâmico via interpret); watch/reload.

### Fase 9 — Conformance
- Matriz Feature × {JVM,Native,Script,KofJS,KofWasm,Android} com
  DONE/PARTIAL/UNSUPPORTED; CI compara matriz × testes reais.

## RESTRIÇÕES (do plano + AGENTS.md)
- Nenhum `if target == X` espalhado: gates centralizados nos Kof*.java
  (supportedOn/gapCode) e em TargetMatrix (F2).
-  : PKG002/4/5 e regra "dir = pacote" não mudam;
  F1 é aditivo (kof.toml opt-in; sem ele, tudo como antes).
- Microsserviços: kof.http/kof.web/http server existentes (CmdServe,
  KofHttpServer, web.app()) permanecem intactos — full-stack é aditivo.
- Cada fase fecha com: suíte verde + testes novos + docs + matriz atualizada.
