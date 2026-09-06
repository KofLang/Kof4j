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

## 3. Design Principles

- **P1 — Uma única abstração.** `Kof Application` é a única unidade nominal.
  Monólito e distribuído são **topologias do sistema**, não tipos de aplicação.
  Não existe `KofMonolith`, `KofMicroservice`, `KofMicrofrontend` na linguagem,
  no compilador ou na CLI.
- **P2 — O desenvolvedor escolhe a topologia.** A linguagem fornece primitivas
  (rotas, http, json, web, spawn); a arquitetura é escolha do projeto,
  expressa em **diretórios + manifesto**, nunca em sintaxe.
- **P3 — `kof serve` = "iniciar esta aplicação".** Nunca inicia aplicações
  vizinhas. Orquestração de múltiplas aplicações é responsabilidade do deploy
  (docker-compose, scripts, k8s) — não da CLI (seção 15 discute a exceção).
- **P4 — Separação Language → Compiler → Application → Artifact → Deployment.**
  O compilador emite artifact; onde o artifact roda (JVM, ELF, browser,
  container, cluster) é do deployment. Nenhuma menção a Docker/K8s no código do
  compilador (receita em docs é aceitável; acoplamento, não).
- **P5 — Aditivo, retrocompatível.** Projeto sem `kof.toml` continua 1:1
  compatível (convenção Go-like atual é o default). `kof.toml` é opt-in e só
  **reafirma** o que a convenção já fazia ou **estende** (frontend, static,
  porta padrão). Sem manifesto = comportamento de hoje (zero regressão).
- **P6 — Componentes são convenção de diretório + manifesto, não sintaxe.**
  Frontend/backend/assets são **partes de uma aplicação** identificadas por
  localização (`web/`, `frontend/`, `static/`...) e/ou manifesto. O compilador
  não ganha keywords de "serviço" ou "página".
- **P7 — Honestidade de target (R6/R7).** Cada capacidade declara suporte por
  target; o que não existe vira gap-code com erro de compilação claro — nunca
  fallback silencioso, nunca paridade forçada.
- **P8 — Interação entre aplicações = primitivas existentes.** HTTP/JSON
  (`kof.http` + `kof.json`) cobrem 90% do caso distribuído hoje. WebSocket/SSE
  existem no JVM. Messaging/RPC = futuros gaps, não desta RFC (seção 14).
- **P9 — KofJS é o frontend.** O target `js` é a forma padrão de frontend Kof.
  Um frontend é um módulo Kof normal, compilado para bundle estático
  (`index.html` + `.mjs`). Não existe "KofHTML" nem sintaxe de componente
  nesta RFC (roadmap §8 é trabalho separado, futuro).
- **P10 — O modelo não bloqueia o futuro.** Tudo que o plano de implementação
  tocar (manifesto, build pipeline, CLI) deve ser extensível a: particionamento
  monólito→serviços sem reescrita, microfrontends, Wasm (quando existir),
  service discovery externo — sem quebrar o que já funciona.

---

## 4. Application

**Definição.** Uma **Kof Application** é:

> um diretório contendo um **módulo Kof** (conjunto de `.kf` com um `main()` —
> convenção Go-like existente) + um **manifesto opcional** `kof.toml`
> descrevendo os **componentes** da aplicação (frontend, static) e configurações
> de execução (porta/host default, target default).

```text
my-app/
├── kof.toml            # manifesto (OPCIONAL — sem ele, convenção atual)
├── kofdeps             # dependências (existe hoje)
├── src/
│   ├── main.kf         # entrypoint (main())
│   ├── api/
│   ├── domain/
│   ├── services/
│   ├── web/            # componente FRONTEND (módulo Kof compilado p/ js)
│   │   ├── main.kf
│   │   └── pages/
│   └── static/         # componente STATIC (arquivos cruos: css, img, fonts)
└── tests/
```

**Regras:**

1. **Uma aplicação = um módulo Kof = um `main()`** (backend). O frontend, se
   existir, é **outro módulo Kof** dentro do diretório, com entrypoint próprio
   (ex.: `web/main.kf`) — compilado separadamente para o target `js`.
2. **Uma aplicação tem zero, um ou mais componentes**: backend (módulo com
   `main`), frontend (módulo → bundle js), static (arquivos). Full-stack =
   backend + frontend (+ static). Nada é obrigatório (seção 8).
3. **Uma aplicação é a menor unidade de `kof serve` e de deployment** (1
   processo, 1 artifact, 1 porta).
4. **Um conjunto de aplicações pode formar um Kof System** (seção 11) — mas o
   System é noção de **composição de deploy**, não de compilação: cada
   aplicação compila e serve sozinha.
5. **Sem `kof.toml`** (projeto de hoje): entry = o `main()` do módulo; sem
   frontend; `kof serve`/`kof build` se comportam **exatamente** como hoje.
   O manifesto só torna explícito o que a convenção já infere.

### 4.1 Manifesto — `kof.toml` (formato proposto)

> Nome `kof.toml` por simetria com `kofdeps` (sem colisão: `kofdeps` é lista de
> dependências; `kof.toml` é a aplicação). Alternativa considerada: estender
> `kofdeps` — rejeitada por responsabilidade (deps ≠ aplicação) e por TOML ser
> estruturado (seções), não flat.

```toml
[app]
name = "my-app"                  # obrigatório (identidade no System)
version = "0.1.0"                # opcional
entry = "src/main.kf"            # opcional — default: unique main() do módulo
target = "jvm"                   # opcional — jvm|native|js|android (default jvm)

[serve]
port = 8080                      # opcional — default 8080 (flag --port sempre vence)
host = "0.0.0.0"                 # opcional — default 0.0.0.0

[frontend]
path = "web"                     # diretório do módulo frontend (módulo Kof)
entry = "web/main.kf"            # opcional — unique main() daquele módulo
out = "build/web"                # opcional — onde o bundle js vai
base = "/"                       # opcional — prefixo de montagem (default "/")
api  = "/"                       # opcional — prefixo das rotas de API no mesmo app
cors = true                      # opcional — header CORS dev (default: dev=true)

[static]
path = "static"                  # opcional — diretório de arquivos cruos
```

**Semântica:**

- Seções ausentes = ausentes na aplicação (backend-only = sem `[frontend]`).
- `[frontend].path` aponta para **subdiretório do app** (full-stack) — em um
  app frontend-only, `entry` pode apontar para o próprio módulo raiz e o
  backend simplesmente não existir (seção 8.3).
- O manifesto é lido **pela CLI** (serve/build/run), nunca pelo compilador
  como fonte de sintaxe. O compilador continua recebendo a lista de arquivos
  como recebe hoje.
- Erros de manifesto (TOML inválido, `entry` inexistente) = erro de CLI claro
  com a linha do problema (R6).

---

## 5. Application Components

| Componente | O que é | Como é declarado | Target |
|---|---|---|---|
| **backend** | Módulo Kof com `main()`; rotas via `web.app()`, dados via `kof.db`, etc. | `main()` na raiz do app (convenção) ou `[app].entry` | jvm, native, android (js só como server embarcado — hoje GraalJS) |
| **frontend** | Módulo Kof compilado para **bundle estático** (`index.html` + `.mjs`) | `[frontend]` no manifesto | `js` (browser). Futuro: wasm |
| **static** | Arquivos cruos (css/img/fontes/`index.html` puro) | `[static]` no manifesto | nenhum (copy) |
| **assets** | Mesma coisa que static para o frontend (dentro do bundle) | convensão do módulo js (já existe: `index.html` no build js) | js |

**Frontend ↔ backend dentro de uma aplicação** (full-stack, seção 7): o
backend monta o bundle do frontend em uma rota (via `app.serveDir` — **já
existe no JVM**, `docs/stdlib-web.md:114-142`) e expõe as rotas de API no
mesmo processo. O frontend chama a API **por HTTP** (`kof.http` no target js —
existe: interop/fetch, `docs/targets/KOFJS.md:174-175`) — **nunca** por
chamada direta. Consequência importante: o contrato frontend→backend é sempre
uma API HTTP/JSON, independentemente de estarem no mesmo processo. Isso torna
"mesmo processo" e "serviço remoto" **intercambiáveis** — a base da
portabilidade monólito↔distribuído.

**Shared models**: records/classes usadas nos dois lados são hoje **código
duplo** (o bundle js não importa do bytecode jvm). Partilhar tipos sem
duplicação é **Open Question** (seção 17) — não blocker: a fronteira HTTP/JSON
já isola.

---

## 6. Topology

```text
                    Kof System
                         │
       ┌─────────────────┼─────────────────┐
       │                 │                 │
    Monolith       Modular Monolith    Distributed
       │                 │                 │
       ▼                 ▼                 ▼
   1 Application     1 Application    N Applications
   (1 dir)           (1 dir, módulos  (N dirs, cada um
                     internos)         1 Application)
```

| Topologia | Como se expressa no modelo | Nota |
|---|---|---|
| **Monolith** | 1 aplicação, tudo em `src/` | o default de hoje |
| **Modular monolith** | 1 aplicação, código organizado em pacotes (`domain/`, `api/`, ...) | **não requer nada do modelo** — é organização de código (já possível: imports de pacotes funcionam); o modelo apenas não proíbe |
| **Microservices** | N aplicações backend-only, 1 `kof.toml` cada | cada uma serve seu próprio HTTP |
| **Microfrontends** | N aplicações frontend-only + 1 shell que monta (serve) os bundles | cada bundle é um app; o shell é um app (estático+rotas) |
| **Full-stack (monolítico)** | 1 aplicação com `[frontend]` + backend | a meta do incremento I2 |
| **Backend-only** | 1 aplicação sem `[frontend]` | API service |
| **Frontend-only** | 1 aplicação sem backend (só bundle) | site estático com lógica KofJS |
| **Full-stack distribuído** | N aplicações full-stack + opcional gateway | cada serviço tem seu frontend e backend; o gateway é uma aplicação backend que roteia |

**O que muda entre topologias:** apenas o **número de diretórios/aplicações** e
o **quem-orquestra-os**. A linguagem, o compilador e a CLI não mudam.

---

## 7. Monolithic Applications

### 7.1 Backend-only (o que já funciona)

```bash
# hoje (sem manifesto — zero mudança):
kof serve main.kf --port 8080
kof build src/ --target jvm
```

### 7.2 Full-stack (o que o incremento I2 entrega)

```bash
# com kof.toml declarando [frontend]:
$ cd my-app
$ kof serve                    # lê kof.toml do cwd
# 1. compila frontend (web/) → build/web/ (bundle js)
# 2. compila backend (src/)  → build/classes (jvm)
# 3. sobe o backend com o bundle montado:
#    GET /            → serveDir build/web  (index.html + .mjs)
#    GET /api/*       → rotas do backend
#    GET /static/*    → [static] se presente
```

`kof serve` full-stack em **1 processo** (JVM). O bundle do frontend é
**recompilado se mudou** (checksum) — dev loop sem build manual.

### 7.3 Backend + static (sem frontend Kof)

`[static]` sem `[frontend]`: backend serve arquivos cruos em `/static/*`
(via `serveDir`). Caso: site com HTML escrito à mão + API Kof.

### 7.4 Monólito modular

Não é feature do modelo: é organização do código dentro de 1 aplicação
(pacotes `domain/`, `api/`, `services/`). A única contribuição do modelo: o
manifesto **pode** anotar módulos internos (informação para tooling futuro),
mas nada depende disso. A portabilidade monólito→serviços vem do fato de as
fronteiras internas serem **funções/records**, e as fronteiras externas serem
**HTTP/JSON** (P8) — ver seção 11.

<!--CONTINUA-SECAO-8-->
