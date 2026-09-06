# Kof Application Model — RFC & Plano de Implementação

**Status:** RFC (sem código — regra de `docs/future/`: move para `docs/` quando
houver código em desenvolvimento, com status real + "como finalizar")
**Criado:** 05/09/2026
**Versão-alvo:** 0.4.0-beta (a linha 0.3.0 está em curso — ver DOING.md)
**Gap-codes desta RFC:** `APP001`..`APP003` (convenção R6; matriz em
`docs/backend-parity.md`)
**Relacionados:** `docs/roadmap.md` §§8–11 (promessas ❌ de 02/09),
`docs/stdlib-web.md`, `docs/targets/KOFJS.md`,
`docs/future/PLAN-UNIVERSAL-PLATFORM.md` (R1/R6/R7/R9/R12)

---

## 1. Motivation

O Kof hoje **não tem um modelo de aplicação**: "aplicação" é uma convenção
implícita (diretório Go-like de `.kf` + `main()` + `-Dkof.root`).
Consequências auditadas (seção 2):

- **Full-stack não é primeira classe.** `kof serve` sobe só backend (JVM-only);
  o frontend (KofJS) é um build separado que o usuário serve manualmente.
  `docs/roadmap.md` §9 ("Frontend + Backend no Mesmo Projeto") está ❌ desde
  02/09.
- **`kof build` não empacota.** JVM sai como pasta de `.class` sem jar/manifest;
  não existe artifact de aplicação standalone com assets (roadmap §11 ❌).
- **Sem manifesto de projeto.** Não há `kof.toml`/equivalente: porta, target,
  entrypoint, assets e frontend são descobertos por convenção de diretório ou
  flags de CLI.
- **Distribuído é impensável hoje.** Não existe conceito de "sistema" composto
  de aplicações; cada `kof serve` é "um arquivo + irmãos". O roadmap §11
  ("Monólito → Microserviços") está ❌.

A visão que este plano implementa (decisão do desenvolvedor, não da linguagem):

```text
                        Kof
                         │
                         ▼
                  Kof Application          ← única abstração (unidade lógica
                         │                   + unidade de deploy)
              ┌──────────┴──────────┐
              │                     │
          Monolithic            Distributed
   (1 application)        (Kof System = N applications)
              │                     │
              ▼                     ▼
       1 Artifact/Deploy     N Artifacts/Deploys
```

Kof **não** assume `Kof Application == Monolith` nem `Kof Application ==
Microservice`. Topologia é **decisão de composição/deployment**, não
característica da linguagem. O mesmo modelo suporta: monólito, monólito
modular, microserviços, microfrontends, full-stack, backend-only,
frontend-only e full-stack distribuído.

### Non-goals (inviáveis por design — não implementar)

- Duas arquiteturas incompatíveis (`KofMonolith`/`KofMicroservice` separados).
- Obrigar (ou viés implícito para) monólito, microserviços ou microfrontends.
- Orchestration complexa; `kof serve` iniciando "todos os serviços do mundo".
- Acoplamento a Docker/Kubernetes/plataforma específica no compilador ou CLI.
- Service discovery proprietário; Module Federation por marketing.
- Inventar APIs de linguagem: tudo que este plano usa já existe
  (`kof.web`, `kof.http`, `kof.json`, `kofdeps`, `application{}`) ou vira
  gap `APP00x`/`WEB00x` honesto (R6 — nunca stub silencioso).
- Mudar semântica da linguagem (congelada 0.2.6/0.3.0 — AGENTS.md).
- Paridade artificial entre targets (R7): o que cada target suporta é
  documentado, não forçado.

---

## 2. Auditoria do estado atual (verificada — 05/09)

> Fonte: leitura direta do código. Caminhos relativos à raiz do repo.

### 2.1 CLI (`kof-cli/`)

| Comando | Comportamento real | Evidência |
|---|---|---|
| `kof serve <file.kf> [--port] [--host]` | Compila **o arquivo + irmãos `.kf` do diretório** como UM módulo para **JVM sempre**; sobe (a) app Kof-native (`main()` + `web.app()`) → processo filho `java -Dkof.root=<dir> -cp <tmp> <Main>`; (b) app legacy sem `main` (`handle(...)`) → `KofHttpServer` in-process | `CmdServe.java:48-112` (JVM fixo `:59`; detecção de `main` `:91-97`; `kof.root` `:109`) |
| `kof build <src-dir> [--target ...] [--output] [--release] [--apk] [--deps] [--classpath] [--keystore ...]` | Convenção Go-like: **todos os `.kf` do diretório formam UM módulo** (raiz = dir passado); compila; `--apk` roda pipeline d8/aapt2/zipalign/apksigner (Android SDK) | `CmdBuild.java:106-118`; pipeline `:127-190` |
| `kof deps <init\|add\|remove\|list\|resolve>` | MVP de package manager: arquivo **`kofdeps`** (1 dep Maven `g:a:v` por linha) → cache `~/.kof/deps`; consumido por `--deps` | `Deps.java:39,112-130,212-253` |
| `kof run` | Compila e executa (JVM; nativo procura `tempDir/Default/Main`) | `CmdRun.java` |
| `kof script`/`repl`, `kof c`, `kof fmt`, `kof test`, `kof bench`, LSP, debug | Fora do escopo desta RFC | `Main.java` |

**Fatos-chave:**

1. **Não existe manifesto de aplicação.** O único "manifesto" é `kofdeps`
   (dependências Maven). Porta/host/entry são flags; raiz do projeto é
   implícita (`-Dkof.root` = diretório do arquivo — `CmdServe.java:109`).
2. **`kof serve` é JVM-only** e trata a aplicação como "arquivo + irmãos", não
   como projeto com componentes. Não serve um build de produção/artifact.
3. **`kof build` não empacota** (JVM = dir de `.class` em `build/classes`,
   `CmdBuild.java:29`; sem jar fat, sem manifest de main, sem assets).
4. **Não existe conceito de workspace/system** multi-aplicação em nenhum lugar
   (CLI, compilador, docs).

### 2.2 Construct de "aplicação" na linguagem

- **Único construct sintático: bloco top-level `application { onStart/onShutdown }`**
  (lifecycle, 3 targets; desugar → prólogo/epílogo do `main`) —
  `Parser.java:214-245`, `CompilerDesugar.java:53-70`.
- **Não existe** declaração de serviço/route/model em linguagem: rotas são
  código comum (`web.app()` + `app.route(...)`), como em qualquer programa.

### 2.3 Web / frontend / backend (stdlib)

| Capacidade | JVM | Native x86_64 | JS (GraalJS) | Gap |
|---|---|---|---|---|
| `web.app()` + rotas HTTP/1.1 | ✅ | ✅ base (rota **literal** + `body()` + `status`) | ✅ base (chave exata `method:path`) | WEB002/WEB001 residual |
| Path params `:id`, `param()/query()/header()` | ✅ | ❌ | ❌ | WEB002/WEB001 residual |
| Middleware `app.use` | ✅ | ❌ | ❌ | idem |
| `app.listenSecure` (TLS) | ✅ (self-signed/keytool) | ❌ | ❌ | WEB002 |
| WebSocket `app.ws` (RFC 6455) | ✅ | ❌ | ❌ | WEB004 |
| SSE `app.sse` | ✅ | ❌ | ❌ | WEB003 |
| **`app.serveDir` (estáticos + Range 206/416)** | ✅ | ❌ | ❌ | **WEB005** |
| `app.health`, `app.configure`/`stats` (hardening) | ✅ | ❌ | ❌ | idem |
| Keep-alive | ❌ (`Connection: close`) | ❌ | ❌ | `docs/status.md:663` |

- **`kof.http` é CLIENT** (get/post/put/delete/patch/options/status +
  timeout/retry/circuit): JVM ✅ real (`java.net.http`); JS ✅ (interop +
  fetch); Native 🟡 HTTP/1.1 asm puro (https→throw; DNS não-IP→127.0.0.1) —
  gap **HTTP003**. `KofHttp.java:53-55`, `NativeHttpRuntime.java:4-7`.
- **Gating de gaps no lowering** (R6): target ≠ JVM/ANDROID fora do conjunto
  T1 do Native → erro de compilação com o gap-code
  (`ExpressionMethodCallLowerer.java:1604-1624`, `KofWeb.java:154-161`).
- **KofJS** (`--target js`): emite **bundle deployável** — `Default.mjs` +
  `kof-runtime.mjs` + `kof-runtime-io.mjs` + `index.html` + source maps
  (`JsBackend.java:5905-6014`). Deploy documentado = "sirva a pasta como
  aplicação web estática" (`learn/37-kofjs.md:52-53`). **Base do frontend
  full-stack: o bundle roda no browser como JS comum; GraalJS embutida só é
  necessária no server.**
- **Android** é hoje o **único caso real de full-stack num projeto**: host
  Activity em Kof + WebView carregando o build KofJS como assets
  (`AndroidProjectWriter.java:18-21,155-157`) — prova de que "frontend +
  backend no mesmo artifact" já funciona em um target.

### 2.4 Targets (enum oficial — `Target.java:3-9`)

`JVM`, `NATIVE` (x86_64), `NATIVE_RISCV64`, `NATIVE_AARCH64` (execução real via
qemu; stdlib parcial — gates DB001/FLT001/SECN000), `JS`, `ANDROID`.

**Wasm NÃO existe** como target: `git grep -il wasm` retorna apenas 5 menções
futuras (MIME table, interop SQLite/WASM para JS, roadmap) — sem codegen, sem
plano de target. "KofWasm" neste plano é **futuro**, não premissa.

### 2.5 Exemplos e docs

- `examples/` contém **1 arquivo** (`orm/Main.kf` — backend-only, `kof.orm`/H2).
  **Zero** exemplos web/full-stack; programas web vivem como fonte inline em
  testes E2E (`KofWebE2ETest`, `KofWebNativeE2ETest`, `KofWebWsE2ETest`,
  `KofWebSseE2ETest`, `KofJsBrowserE2ETest` — `docs/stdlib-web.md:270-281`).
- `docs/stdlib-web.md:254-262` está **desatualizado** (diz "Native não possui
  servidor web"; o server base existe desde 03/09 — `DOING.md:106`,
  `docs/backend-parity.md:62,85`). Corrigir junto do incremento I2.
- Inconsistência de numeração WS/SSE entre `docs/security.md:55-56` e
  `docs/stdlib-web.md:210,258` (WEB003/WEB004) — corrigir no mesmo pass.

### 2.6 Síntese: fundação vs lacuna

**Já existe (fundação do modelo):** módulo Go-like + `main()` + `kof.root`;
`application{}` lifecycle (3 targets); `kof.web` server rico (JVM) com
`serveDir` (estáticos + Range); `kof.http` client (3 targets); `kof.json`
(encode/decode, 3 targets); `kofdeps`; bundle KofJS estático; Android híbrido.

**Falta (o que esta RFC preenche):**

| # | Lacuna | Onde |
|---|---|---|
| L1 | Manifesto de aplicação (entry, target, porta, frontend, static) | não existe |
| L2 | Full-stack como primeira classe (backend compila frontend e o monta) | não existe (roadmap §9 ❌) |
| L3 | Packaging de aplicação (jar standalone / artifact com assets) | não existe (roadmap §11 ❌) |
| L4 | Conceito de System/Workspace (composição de N aplicações) | não existe |
| L5 | Comunicação entre aplicações no modelo (HTTP hoje; resto = gap) | não existe |
| L6 | Exemplos web/full-stack em `examples/` | zero |

<!--CONTINUA-SECAO-3-->
