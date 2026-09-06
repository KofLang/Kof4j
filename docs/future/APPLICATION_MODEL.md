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

## 8. Specialized Applications

### 8.1 Backend-only

```toml
[app]
name = "billing-api"
target = "jvm"
```

Sem `[frontend]`/`[static]`. `kof serve` = backend puro (exatamente o
comportamento de hoje). `kof build` = artifact do backend (seção 13).

### 8.2 Full-stack

Seção 7.2. É a **composição canônica** que o modelo deve tornar trivial:
`kof serve` → 1 processo com backend + frontend + static.

### 8.3 Frontend-only

```toml
[app]
name = "marketing-site"
target = "js"
entry = "src/main.kf"      # entry do MÓDULO frontend (o app inteiro é frontend)
```

Sem backend: `kof build --target js` (já existe) gera o bundle;
`kof serve` **serve o bundle estático** em porta local (dev preview) —
implementação: a CLI monta um mini-server estático (ou reusa `web.app()` de um
wrapper gerado? **decisão I4**: mais simples = CLI serve o diretório
`build/web` com MIME table existente em `JvmMediaWebRuntime.java:138`).
Produção: o bundle é servido por **qualquer** servidor/CDN (o modelo não
prescreve).

---

## 9. Distributed Applications

**Definição.** Um sistema distribuído Kof é **N Kof Applications**, cada uma
independente (próprio diretório, manifesto, artifact, processo, porta), que
comunicam entre si por **primitivas existentes** (seção 12). **Nada novo na
linguagem ou no compilador** — o "distribuído" emerge da composição.

```text
Kof System
├── users/      (Kof Application: backend API)
├── orders/     (Kof Application: backend API)
├── auth/       (Kof Application: backend API)
└── frontend/   (Kof Application: frontend-only OU full-stack)
```

### 9.1 Microservices

Cada serviço é uma aplicação **backend-only** (seção 8.1) com rotas HTTP/JSON
próprias. O frontend (qualquer aplicação) chama os serviços por URL:

```kof
// no frontend ou em um serviço que agrega:
var user = http.get("http://users:8081/api/users/1")
```

**Onde fica a configuração de endereços?** No **manifesto/configuração de
runtime** de cada aplicação (`kof.config` — existe: env/`/proc/self/environ`,
3 targets) — ex.: `KOF_USERS_URL`. **Não** fica na linguagem. Service discovery
(automação de endereços) é **deploy** (DNS interno, K8s service, registry
externo) — O Kof **não** implementa discovery próprio (non-goal).

### 9.2 Microfrontends

Cada frontend é uma aplicação **frontend-only** (seção 8.3) que gera um bundle
próprio. Um **shell** (aplicação com backend, ou mesmo frontend-only) monta os
bundles. Composição no navegador: o shell carrega o `index.html`/entry de cada
frontend (iframe, script tag, ou module federation **no navegador** — o último
é responsabilidade do navegador/deploy, **não** do Kof). O modelo do Kof só
garante: (a) cada frontend é um app independente buildável/servível; (b) as
fronteiras entre eles são URL/asset — o mesmo princípio HTTP/JSON (P8).
Module Federation *em Kof* não é feature desta RFC (non-goal).

### 9.3 Full-stack distribuído

```text
Kof System
├── users/    (full-stack: backend API + frontend do domínio)
├── orders/   (full-stack: backend API + frontend do domínio)
└── gateway/  (backend-only: roteia /users/* → users:8081, /orders/* → orders:8082)
```

Cada aplicação é um full-stack normal (7.2); o gateway é um backend que
**proxy** (rotas que redirecionam/forward via `kof.http` — possível hoje, sem
novo código; um `kof.proxy` conveniência pode vir como stdlib aditiva no futuro,
fora desta RFC).

---

## 10. Application Composition (Kof System / Workspace)

**Decisão de design: adotar o nome `Kof System`.**

- **"System"** descreve a semântica (conjunto de aplicações que formam um
  sistema); "Workspace" é jargão de tooling (VS Code) e não diz o que o
  objeto *é*.
- **Um System é apenas um diretório contendo N subdiretórios, cada um uma
  Kof Application** (cada um com seu `kof.toml` com `name` único).
- **O System NÃO compila.** Não existe build unificado: cada aplicação
  compila/sobe/builda sozinha. O System existe para: (a) dar nome/identidade
  ao conjunto em docs e tooling; (b) ancorar convenções compartilhadas
  (ex.: portas por nome, `.env` compartilhado em dev — ambas convenções
  documentadas, não enforced); (c) permitir a CLI opcional `--system` (seção 15).
- **Manifesto de System (opcional):** `kof.toml` na raiz do System **sem**
  `[app]` (ou com `[system]`):

```toml
[system]
name = "shop"
apps = ["users", "orders", "auth", "frontend"]   # subdiretórios (default: todos os subdirs com kof.toml)

[dev.ports]
users = 8081
orders = 8082
auth = 8083
frontend = 3000
```

  O manifesto do System é lido **só pela CLI** (dev conveniences) e por
  tooling (docs, IDE). Nunca pelo compilador.

- **O que o System NUNCA faz:** iniciar aplicações em cascada pelo `kof serve`
  de outra; injetar dependência de compilação entre apps (cada app é um módulo
  isolado — se um app precisa de código de outro, é **package/import**
  (`kofdeps`/package manager — roadmap §6), não "system"); garantir
  descoberta de serviço.

**Particionamento monólito → microserviços (roadmap §11):** o caminho
suportado pelo modelo:

```text
1. Monólito full-stack (7.2): src/ com pacotes domain/api/services
2. Extrair um pacote (ex. orders) para um novo diretório = nova aplicação:
   - o código vira um app backend-only (mesma linguagem, mesmo padrão);
   - as chamadas internas que eram funções viram HTTP/JSON (P8: a fronteira
     já era HTTP para o frontend — agora também entre serviços);
   - shared code (records, helpers) vira um package importado via kofdeps.
3. Nada na linguagem/compilador impede; o custo é só a reorganização de
   diretórios + troca de chamada-função por http.get — trabalho do dev.
```

O modelo **não** promete "particionamento automático" (would be marketing);
promete **não bloquear** e **padronizar a forma** (cada unidade = 1 app =
1 artifact = 1 HTTP service com JSON).

---

## 11. Service Communication

Matriz de mecanismos — o modelo é **compatível** com todos; **implementados
hoje** são só os marcados ✅:

| Mecanismo | Hoje? | Como | Gap |
|---|---|---|---|
| **HTTP/REST (JSON)** | ✅ | `kof.http` client (3 targets) + `kof.web` server + `kof.json` | — (cauda Native: HTTP003) |
| **WebSocket** | ✅ JVM | `app.ws` RFC 6455 | WEB004 (Native/JS) |
| **SSE** | ✅ JVM | `app.sse` | WEB003 (Native/JS) |
| **gRPC/RPC tipado** | ❌ | futuro (roadmap §TIER 2.2 cita stubs gRPC como codegen) | futuro (fora desta RFC) |
| **Messaging/Events/Queues** | 🟡 `kof.mq` pub/sub **in-process** | `KofMq` (3 targets, lista em memória) | broker externo = futuro; o modelo não impede (o broker é outro serviço que expõe HTTP) |
| **Database compartilhada** | ✅ `kof.db` (JVM H2/MySQL; Native MySQL) | — | — (padrão antigo, válido para evolução gradual) |

**Regra do modelo (P8):** a fronteira entre aplicações é **sempre** uma
primitiva de rede (HTTP/JSON hoje). O mesmo programa Kof que chama uma função
local pode, com a fronteira trocada, chamar `http.get` — sem mudar a forma do
código (records + `kof.json` nos dois lados). WebSocket/SSE quando fechados
(WEB003/4) entram na mesma matriz sem mudar o modelo.

---

## 12. Build Model

```text
Application ──kof build──▶ Artifact ──▶ Deployment
```

| Application type | Target | Artifact (após I3) | Condições de execução |
|---|---|---|---|
| backend-only | jvm | **`<name>.jar`** standalone (classes + runtime gerado + manifest `Main-Class`) | `java -jar <name>.jar` (JRE) |
| backend-only | native | **ELF binário** + `static/` opcional ao lado | executar binário (port via flag/env) |
| full-stack | jvm | **`<name>.jar`** com `web/` (bundle js) + `static/` empacotados em `/web/*`, `/static/*` | `java -jar <name>.jar` — 1 processo |
| frontend-only | js | **bundle** `build/web/` (index.html + .mjs) — **já existe hoje** | qualquer servidor/CDN |
| (hoje, android) | android | projeto Maven + APK (pipeline existe: `CmdBuild.java:127-190`) | Android |

**Regras:**

1. `kof build` **sempre** compila; **empacota** quando o target permite e a
   aplicação tem entry definido (I3). Sem `kof.toml` → mantém comportamento de
   hoje (pasta de classes) — retrocompatibilidade P5.
2. **Jar standalone** (jvm): o runtime é **gerado no bytecode** hoje
   (`KofRuntime` — `docs/LICENSING.md:68`); o jar apenas reúne `.class` +
   assets + `Main-Class`. Sem runtime separado para distribuir (licensing
   preservado). Deps `kofdeps` (jars externos) **não** entram no jar (R: jar
   fat de deps = decisão de packaging documentada, não default; default =
   classpath explícito via manifest `Class-Path` ou instruções no doc).
3. **Native:** ELF já é standalone (sem libc, sem runtime externo — o runtime
   está no binário). O packaging = copiar binário + `static/` para o output.
   Full-stack native = **não** (bundle js no browser exige server que sirva
   HTML — o ELF **pode** servir (WEB005 serveDir Native, gap) — honesto:
   full-stack nativo fica **JVM-first** (R7 escopo honesto); native full-stack
   abre quando WEB005 fechar.
4. **JS frontend:** artifact já existe (bundle). Full-stack js **como server**
   (GraalJS embutida servindo o próprio bundle) é possível hoje
   (`KofJsRunner`) mas **não é o default** (default = backend jvm + bundle).
5. **`--system`** (seção 15): `kof build --system` na raiz do System =
   **sequência de `kof build`** em cada app (1 artifact por app). Sem
   unificação.
6. **Output layout** (com manifesto): `build/<target>/<name>/` com o artifact
   + assets; flag `--output` continua valendo.

---

## 13. Runtime Model

- **Dev (`kof serve`):** 1 processo por aplicação. Full-stack: o backend
  carrega o bundle em tempo de runtime (rebuild sob demanda se o frontend
  mudou). **Hot-reload: fora de escopo** (não existe hoje; futuro).
- **Produção:** o artifact é **imutável**; o runtime lê config por env
  (`kof.config` — existe) e sub-processos por conexão (JVM: virtual threads —
  `docs/stdlib-web.md:247-252`).
- **Lifecycle:** `application { onStart/onShutdown }` (existe, 3 targets) é o
  único lifecycle de aplicação — o modelo **não inventa** outro (I2 pode
  **usar** onStart para log "app pronto em :port" — convenção, não feature).
- **Múltiplas aplicações no mesmo host (dev):** cada `kof serve` é um processo
  com sua porta; orquestração de "subir todos" é script/docker-compose (P3).

---

## 14. Deployment Model

```text
Kof Language → Kof Compiler → Kof Application → Artifact → Deployment
```

- O **compilador/CLI não conhece** Docker/K8s/VM (P4). A fronteira é o
  **artifact** (jar/ELF/bundle) + **contrato de execução** documentado:
  porta (flag/env `PORT`), config (env), health endpoint (`app.health` —
  existe).
- **Recipes (documentação, não código no repo):** `Dockerfile` de exemplo por
  target em `docs/deployment/` (jvm: `FROM jre + java -jar`; native: `FROM
  scratch + copy ELF + EXPOSE`; frontend: `COPY build/web → nginx/CDN`).
  São exemplos que o usuário copia — zero acoplamento.
- **K8s:** service = 1 container = 1 artifact; service discovery = DNS do
  cluster (o app lê `KOF_<NAME>_URL` via `kof.config`); gateway = um app
  normal. Nada proprietário.

---

## 15. CLI

### 15.1 Comandos atuais (não mudam de semântica)

| Comando | Hoje | Após esta RFC |
|---|---|---|
| `kof serve <file.kf>` | arquivo + irmãos, JVM | **mesmo** se houver `kof.toml` no cwd (ou no dir do arquivo): lê manifesto, compila frontend se declarado, sobe full-stack |
| `kof build <dir>` | compila dir (Go-like) | **mesmo** + empacota (I3) quando manifesto/entry permite |
| `kof run` | compila+roda | lê manifesto (entry/target default) — aditivo |
| `kof deps` | kofdeps | **intocado** (manifesto de app ≠ deps) |

### 15.2 Novos (aditivos)

- `kof serve` **sem argumento**: lê `kof.toml` do cwd (erro claro se não
  houver — R6). Hoje exige arquivo; manter os dois.
- `kof build --system`: na raiz do System, executa `kof build` em cada app
  (sequencial, 1 artifact cada). **Não** é orquestração.
- `kof serve --system`: **rejeitado com mensagem** (P3) — "each application is
  served individually; see docs for docker-compose recipe". (Decisão consciente
  contra a conveniência: "subir tudo" esconde portas/falhas individuais e
  cria estado na CLI; o script do usuário faz melhor. Reavaliar em feedback.)
- `kof new <name> [--frontend]`: **esqueleto** (I1): gera `kof.toml` mínimo +
  estrutura de diretórios (src/, web/ se `--frontend`, static/). Só templates —
  sem mágica. (Preenche a ausência de `kof init` do roadmap §TIER 1.4 — este
  é o init de **aplicação**, distinto do init de **deps**.)

### 15.3 Flags de manifesto × flags de CLI

**Flag sempre vence manifesto** (`--port` > `[serve].port`), manifesto vence
convenção default. Ordem: flag > manifesto > default.

## 16. Target Compatibility

| Capacidade do modelo | JVM | Native x86_64 | riscv64/aarch64 | JS | Wasm |
|---|---|---|---|---|---|
| backend-only (`kof serve`/`build`) | ✅ hoje | ✅ hoje | 🟡 qemu | 🟡 GraalJS | ❌ inexistente |
| **Full-stack** (`kof serve` + frontend) | ✅ **I2** | ❌ **APP001** (exige WEB005) | ❌ APP001 + gates | ❌ APP001 | ❌ |
| Frontend-only (bundle) | n/a | n/a | n/a | ✅ **hoje** | ❌ |
| `kof build` empacota | ✅ **I3** | ✅ **I3** | 🟡 I3 | ✅ bundle (hoje) | ❌ |
| Sistema (N apps) | ✅ | ✅ | 🟡 | ✅ | ❌ |
| HTTP/JSON entre apps | ✅ | 🟡 HTTP003 | 🟡 HTTP003+DB001 | ✅ | ❌ |
| WS/SSE entre apps | ✅ | ❌ WEB004/3 | ❌ | ❌ WEB004/3 | ❌ |

**Regras (R7):** **JVM é o target full-stack primeiro** — target ≠ JVM com
`[frontend]` reporta **APP001** em serve/build-time (erro claro; nunca
servir só o backend e "esquecer" o frontend — R6). **JS/Wasm = frontends**: o
target `js` é o frontend padrão (P9); "KofWasm" não existe hoje (2.4) e, quando
existir, o modelo o acomoda sem mudança. **Native full-stack** abre quando
**WEB005** fechar (binário já serve HTTP — WEB002 base). A tabela é o
contrato: gaps com código, nunca divergência silenciosa.

---

## 17. Security Boundaries

Limites **arquiteturais** (implementação = gaps existentes, não desta RFC):

1. **Fronteira de aplicação = fronteira de segurança.** Cada aplicação é
   processo/isolamento próprio; **nunca** dividir processo entre apps do
   System (P3).
2. **AuthN/AuthZ:** responsabilidade de cada aplicação (e do gateway).
   Primitivas: `kof.security` (gaps SECN00x) + headers/cookies. O modelo não
   define protocolo.
3. **Secrets/config:** `kof.config` (env — existe) é o único canal. **Secrets
   nunca em `kof.toml`** (manifesto é committable: nome, porta, caminhos). CLI
   avisa se chaves contiverem "key"/"secret"/"token" (heurística, R6).
4. **CORS:** full-stack same-origin (mesma porta) → sem CORS. Dev com portas
   separadas → `[frontend].cors` envia headers permissivos **só em `kof
   serve`**, nunca no artifact. Produção = deploy.
5. **API exposure:** recomendação (docs, não enforced): prefixo `/api/*` +
   `/health` — facilita gateway/proxy.
6. **Static isolation:** `serveDir` limita ao diretório raiz; teste de
   regressão I2 garante que `GET /../main.kf` não vaza fonte.
7. **Service-to-service TLS:** responsabilidade do deploy (terminação) ou do
   app (`listenSecure` JVM; HTTP003/WEB002 demais). O modelo documenta a
   expectativa, não implementa.
8. **Frontend isolation (browser):** cada frontend é origin/subpath próprio;
   CSP/headers do deploy. Sem acoplamento.

## 18. Compatibility

- **Código Kof:** **zero** mudança de sintaxe/semântica (seção congelada —
  AGENTS.md). Nada de keyword, nada de construct novo de linguagem.
- **Projetos sem `kof.toml`:** `kof serve <file.kf>` e `kof build <dir>`
  comportam-se **idênticos** (P5). A suíte atual (969+ testes) é gate — nenhum
  teste existente muda de resultado sem mudança de contrato deliberada.
- **`handle(...)` legacy:** o caminho legacy do `kof serve` (app sem `main`)
  continua funcionando (não remover — AGENTS.md §17).
- **Versionamento:** feature aditiva → proposta **0.4.0-beta** (Open Q2).
- **Gaps novos:** `APP001` (full-stack em target não-JVM), `APP002`
  (packaging em target sem caminho de empacotamento), `APP003` (validação de
  manifesto/System: TOML inválido, entry ausente, nome duplicado). Nenhum gap
  é stub silencioso: erro claro + doc.

---

## 19. Testing

### 19.1 Gates (AGENTS.md)

Suíte completa `mvn test` verde em cada incremento; golden E2E por target
para o que muda (serve/build).

### 19.2 Cenários da RFC (novos testes, aditivos)

**Cenário A — Monólito full-stack (JVM).** `examples/fullstack/` (novo):
backend (`web.app()` + 1 rota API JSON + `serveDir` do bundle) + frontend
(módulo js mínimo) + `static/` (1 css). E2E (padrão `KofWeb*E2ETest`): `kof
serve examples/fullstack` em porta efêmera → `GET /` = `index.html` do bundle;
`GET /api/ping` = JSON; `GET /static/app.css` = css; traversal bloqueado (17.6).
Depois (I3): `kof build examples/fullstack --target jvm` →
`build/jvm/fullstack.jar` com `Main-Class` + `web/index.html`; `java -jar`
responde `/` e `/api/ping`.

**Cenário B — Aplicações independentes.** `examples/system/` (novo):
`service-a/` + `service-b/` (backend-only) + `frontend/` (frontend-only), cada
um com `kof.toml`. E2E: `kof build` gera artifact próprio por app; `kof serve`
de cada um sobe na própria porta **sem** depender do outro; `service-b` chama
`service-a` via `kof.http` (prova de comunicação — §9.1).

**Cenário C — Full-stack distribuído.** `examples/system-dist/` (novo):
`frontend/` → `gateway/` (proxy) → `users/` + `orders/`. E2E: subir os 4
(portas por app), request do frontend chega ao app certo via gateway. **Se o
CI ficar frágil** (processos/portas), vira teste de integração opcional
(marcado) — a garantia arquitetural já está no Cenário B.

**Regressão específica do modelo:**

- `kof serve <file.kf>` **sem** `kof.toml` → comportamento inalterado
  (testes existentes passam = gate).
- Manifesto inválido (TOML malformado, `entry` ausente, nome duplicado no
  System) → erro claro com gap-code (APP003), nunca crash/opaque.
- `[frontend]` em target native → **APP001** claro (R6).
- `kof build --system` com 1 app quebrando → exit ≠ 0, demais reportados.

### 19.3 Fora do escopo de teste desta RFC

Hot-reload, TLS cross-target, WS/SSE cross, brokers — cada um com seu gap
(WEB00x/HTTP003) e seus testes.

---

## 20. Migration

**Não existe migração:** feature aditiva. Projeto antigo = projeto sem
`kof.toml` = comportamento de hoje. **Adoção opcional** (doc): (1) criar
`kof.toml` à mão (o `kof new` serve de referência); (2) adicionar `[frontend]`
quando o app ganhar frontend; (3) deploy passa a `java -jar` (recipe §14).
**Reversível:** apagar `kof.toml` volta 1:1.

---

## 21. Open Questions

1. **`kof.toml` vs estender `kofdeps` vs `kof.json`** — proposta `kof.toml`
   (estruturado, separado de deps). Decisão do maintainer.
2. **Bump** — 0.3.x vs **0.4.0-beta** (proposta; manifesto é capability nova).
3. **Shared types front/backend** (roadmap §9) — hoje = duplicação; futuro =
   package local importado pelos dois (package manager). Não bloqueia.
4. **`kof serve --system`** (subir tudo em dev) — rejeitado por ora (§15.2);
   alternativa: modo "lista apps+portas" sem subir.
5. **`[frontend].api`** — convenção (docs) vs feature de rewrite. Proposta:
   convenção.
6. **Jar fat com `kofdeps`** — proposta: flag `--fat` opcional (I3); default
   classpath explícito.
7. **Wasm** — quando abrir, tabela §16 ganha coluna ✅ frontend; modelo não muda.
8. **Gateway em stdlib** (`kof.proxy`) — convenção hoje; se 3+ apps
   precisarem, stdlib aditiva (fora desta RFC).
9. **Rebuild de frontend em `kof serve`** — proposta I2: rebuild sob demanda
   por hash (sem watcher); watcher = futuro.
10. **Android** — full-stack já existe de fato (WebView + KofJS). Declarar na
    tabela §16 como ✅ (sem código novo)?

---

## 22. Future Extensions (o modelo NÃO faz, mas não bloqueia)

- **Particionamento automático** monólito→serviços (análise de deps, cut
  sugerido) — tooling futuro.
- **Module federation** — navegador/deploy, não Kof.
- **Service discovery proprietário** — nunca (non-goal).
- **Orquestrador embutido** (subir/escalar/monitorar) — nunca na CLI;
  observabilidade = `kof.log`/spans (existem) + OTLP (gap existente).
- **Cluster/HA/autoscaling** — deploy.
- **KofWasm** — target futuro.
- **gRPC/RPC tipado, brokers** — gaps de stdlib futuros (§11).

## 23. Plano de Implementação (incrementos)

> Regras: cada incremento = commit(s) + suíte completa verde + E2E que prova;
> escopo realizável numa sessão (AGENTS.md); gaps APP001/002/003.
> **Lane:** nenhum incremento refatora `NativeRuntime`/`CompilerDriver`/`Parser`
> (REFACTOR-500 em curso) — toca `kof-cli/` + docs + `examples/` + novas
> classes ≤500. Colisão inevitável em arquivo gigante `EM CURSO` de outro
> agente = adiar o incremento (regra de ouro, §condições de parada 2).

### I1 — Manifesto `kof.toml` + `kof new` (E–M, ~1 sessão)

**Escopo:** parser de `kof.toml` mínimo (subconjunto: `[section]`, `key =
value` string/int/bool, comentários `#`) em classe nova `AppManifest.java`
(≤500) no `kof-cli`; `kof new <name> [--frontend]` (esqueletos backend-only /
full-stack / frontend-only); validação (APP003: TOML inválido, entry ausente,
`name` duplicado no System); `run`/`serve`/`build` leem manifesto quando
presente (flag > manifesto > default). **Sem manifesto = 1:1 hoje (P5).**

- **Prova:** unit tests do parser (válido, inválido, sections ausentes, tipo
  errado, comments); `kof new` gera app que `kof serve` sobe (E2E, porta
  efêmera); suíte completa verde.
- **Arquivos:** `AppManifest.java` (novo), `CmdNew.java` (novo), `Main.java`
  (registro), `CmdServe/CmdBuild/CmdRun.java` (leitura aditiva).
- **Não toca:** Parser/compilador (manifesto é lido só pela CLI — P5/P6).

### I2 — Full-stack em `kof serve` (JVM) (M–H, 1–2 sessões)

**Escopo:** `kof serve` com `[frontend]`:
1. compila o módulo frontend para `js` (bundle em `build/web/`) — reuso de
   `CompilerDriver` com `Target.JS`;
2. compila o backend (como hoje);
3. **o app é dono das rotas** (P2): o idiom full-stack é o próprio backend
   chamar `app.serveDir("<out do frontend>")` (idiom existe —
   `docs/stdlib-web.md:114-142`); a CLI faz o build do frontend e passa o
   path via `-Dkof.web.out=...` (env `KOF_WEB_OUT` no native/futuro); o
   skeleton do `kof new --frontend` já inclui o `serveDir` (exemplo canônico);
4. rebuild do frontend sob demanda se mudou (hash; sem watcher);
5. `[static]` → idem (app chama `serveDir` do static);
6. target ≠ JVM com `[frontend]` → **APP001** claro (R6);
7. dev CORS (§17.4): `-Dkof.dev=true` injeta header permissivo **só** em
   `kof serve` (via wrapper do `KofHttpServer` legado / property lida pela
   stdlib web JVM — aditivo), nunca no build.

- **Prova (Cenário A):** `examples/fullstack/` + E2E: `GET /` = bundle,
  `GET /api/ping` = JSON, `GET /static/app.css` = css, traversal bloqueado,
  APP001 em native, sem-manifesto inalterado. Suíte verde.
- **Arquivos:** `CmdServe.java` (pipeline frontend + properties),
  `KofCliSupport.java` (hash), `examples/fullstack/**`.
- **Corrige no caminho:** `docs/stdlib-web.md:254-262` (desatualizado) +
  numeração WEB003/004 em `docs/security.md:55-56` (§2.5).

### I3 — Packaging: `kof build` gera artifact (M, 1–2 sessões)

**Escopo:** `kof build` empacota quando há `kof.toml` com entry:
1. **jvm** → `build/jvm/<name>.jar`: classes + `web/` (bundle, se full-stack)
   + `static/` + manifest `Main-Class` (+ `Class-Path` para deps `kofdeps`
   resolvidas — OQ6); flag `--fat` opcional (deps embutidas);
2. **native** → `build/native/<name>/`: ELF + `static/` (se declarado);
3. **js** → bundle (já existe; layout unificado `build/js/<name>/`);
4. target sem caminho de empacotamento → **APP002** claro;
5. sem `kof.toml` → **comportamento de hoje** (pasta de classes) — P5.

- **Prova:** Cenário A via jar (`java -jar` responde `/` + `/api/ping`);
  jar contém `web/index.html`; `Main-Class` correto; native binário + static
  no output; APP002 em target sem empacotador; sem-manifesto inalterado.
- **Arquivos:** `CmdBuild.java` (fase de packaging), `AppPackager.java`
  (novo, ≤500 — jar via `java.util.jar`, ELF via copy).

### I4 — Kof System: manifesto de System + `--system` (E–M, 1 sessão)

**Escopo:** `kof.toml` de System (`[system]` + `[dev.ports]` — §10); `kof
build --system` (sequência de builds, 1 artifact por app); `kof serve
--system` **rejeitado** com mensagem (§15.2, P3); validação APP003 (nome
duplicado, app sem `kof.toml`); docs de deploy (recipes Dockerfile/K8s em
`docs/deployment/` — exemplos copiáveis, §14).

- **Prova:** Cenários B e C (E2E: builds independentes, serve independente,
  `service-b` → `service-a` via `kof.http`; gateway roteia); nomes duplicados
  → APP003; `--system` com app quebrando → exit ≠ 0.
- **Arquivos:** `AppManifest.java` (seção system), `CmdBuild.java`
  (`--system`), `docs/deployment/*.md`, `examples/system/**`,
  `examples/system-dist/**`.

### Sequência e critérios de fechamento da RFC

```text
I1 (manifesto) ─▶ I2 (full-stack serve) ─▶ I3 (packaging) ─▶ I4 (System)
```

- Cada incremento fecha uma lacuna da §2.6: I1→L1/L6(parte), I2→L2, I3→L3,
  I4→L4/L5.
- **RFC considera-se implementada** quando: Cenário A via serve **e** via
  jar passam; Cenário B passa; Cenário C passa (ou marcado opcional com
  justificação); suíte completa verde; `docs/application-model.md` (movido de
  `future/`) marca ✅ por seção; `docs/status.md`/`backend-parity.md`
  atualizados com APP001/002/003 na matriz.
- **Dependências externas (não desta RFC, mas mudam a tabela §16):** WEB005
  (serveDir Native → full-stack native), WEB003/004 (SSE/WS cross →
  comunicação rica), HTTP003 (https/DNS native → serviços nativos).

### Estimativa honesta

| Incremento | Sessões | Risco principal |
|---|---|---|
| I1 | 1 | parser TOML minimalista (mitigado: subconjunto, sem array-of-tables) |
| I2 | 1–2 | rebuild sob demanda sem watcher (hash simples); CORS dev |
| I3 | 1–2 | jar com assets + Main-Class (mecânico); classpath de deps |
| I4 | 1 | testes multi-processo no CI (mitigado: Cenário C opcional) |

**Total: ~4–6 sessões** para o modelo completo (monólito full-stack +
aplicações independentes + System), sem tocar semântica de linguagem.

---

## 24. Checklist de conformidade (AGENTS.md)

- [x] Diretriz primária: a feature **reduz** verbosidade (1 `kof serve` em vez
      de 2 servers + config manual); idiom = `serveDir` + rotas (não wrapper
      mágico).
- [x] Regra 2 (complexidade na plataforma): empacotar/servir está na CLI, não
      no código do usuário.
- [x] Zero regressão: P5 (sem manifesto = 1:1); suíte como gate.
- [x] Retrocompatibilidade aditiva: tudo novo é opt-in (`kof.toml`, flags).
- [x] R6 (nunca silencioso): APP001/002/003 com erro claro + matriz.
- [x] R7 (escopo honesto por target): tabela §16, JVM-first.
- [x] R12 (futuro não é ação): `future/` até haver código.
- [x] ≤500 linhas/classe: `AppManifest`, `CmdNew`, `AppPackager` novos e
      pequenos; `CmdServe`/`CmdBuild` crescem só na leitura do manifesto
      (se passar de 500, extrair — já extraídos do `Main` na Fase 8).
- [x] Semântica congelada: zero mudança de linguagem.
- [x] Corpus: ao implementar I2, adicionar idiom "full-stack em Kof"
      (`training/idioms/` — BAD: 2 servers manuais; GOOD: `kof.toml` +
      `kof serve`).
