[English](http-policies-plan.md) | [Português](http-policies-plan.pt_BR.md)

# Políticas HTTP/Web declarativas — plano de implementação

**Status:** `CONCLUÍDO` — promovido de `future/` 28/09/2026 (`D-HTTP-POLICIES`, mantenedora "pode assumir"), todas as fatias F0–F6 pousadas 28/09/2026; movido `development/` → `docs/stdlib/` (regra dos 3 estados).
**Dono:** lane pipeline (esta sessão); JVM primeiro, Native/JS = `WEB006` honesto
**Decisão:** `D-HTTP-POLICIES` (`DECISIONS.md`) — aditivo; superfície travada como §3.
**Estado real (28/09/2026):** implementado e verificado. `app.security(opts)` carrega
o opt declarativo `responses` (F0); `Policy` + `merge`/`appliesTo` (F1); escopos de
recurso `app.policy(prefix, opts)` com resolução da política efetiva por prefixo
mais longo (F2); opts por endpoint `app.get(path, opts) { }` (F3); `responses` da
política efetiva incl. `notFound` nos dois caminhos de 404 (F4); chave de
rate-limit por rota `ip + padrão de rota` (F5); validação de prefixo + `WEB006` em
Native/JS + docs (F6). Prova: `KofHttpPoliciesE2ETest` 10/10 + regressão web 65/65.
**Como terminar:** feito. Reabrir apenas como frente nova (regra 6) — ex.: glob
matchers ou políticas Native/JS, ambos explicitamente fora do escopo da v1.

---

## 1. Objetivo

Permitir que uma aplicação web Kof **declare** políticas HTTP transversais
(authorization, rate limits, headers, payloads de rejeição, configurações comuns
de WebService) **uma vez**, e as associe **globalmente, a um recurso (prefixo de
path) ou a um endpoint** — sem o usuário escrever `if`/`switch` por endpoint.

A feature é uma **extensão do que já existe** (`app.security(opts)`), não um
framework novo de middleware. Kof continua Kof: o usuário declara a
**intenção** ("este recurso exige `admin`, 100 req/min"), o runtime compõe e
executa a pipeline fixa.

---

## 2. Estado atual (código real, não suposto)

A stack web já tem um mecanismo de política **global**. Nada por-rota existe hoje.

> Recon capturado **antes** da implementação (tip medido `9d3b36980`); ver o
> cabeçalho para o estado concluído (F0–F6 pousadas).

| Peça | Onde | O que faz hoje |
|---|---|---|
| Tabela de compile-time | `kof-compiler/src/main/java/dev/kof/compiler/KofWeb.java` | Mapeia `web.app()`, `app.get/post/...`, `app.use`, `app.security([opts])`, `app.listen`, `app.configure` para chamadas `kof_web_*`. `ROUTE_METHODS` em `KofWeb.java:36`; `security` em `KofWeb.java:114`. |
| Modelo app/runtime | `.../jvm/JvmWebCoreRuntime.java` | `WebApp` (`:384`) guarda campos de política **planos**; `WebRoute` (`:93`) guarda só `method/segments/params/handler/kind`; `kof_web_route` (`:468`); `kof_web_use` (`:490`). |
| Parser da política | `.../jvm/JvmWebSecurityRuntime.java` | `kof_web_security_opts` faz o parse de um `Map` (`headers`, `cors`/`corsOrigin`, `rateLimit`, `csrf`, `sessionHeader`, `publicPaths`/`permitAll`, `auth`, `roles`) nos campos do `WebApp`. |
| Dispatch | `.../jvm/JvmRuntimeWebDispatch.java` | `kof_web_security_pipeline(app, req)` (`:45`) roda a **ordem fixa** rate-limit → cors → headers → session → csrf → auth → RBAC **antes** do match de rota (`:210` antes de `:216`). |
| Descriptors | `.../jvm/JvmRuntimeCallDescriptors.java:210-216`, `JvmRuntimeReturnDescriptors.java:204` | Assinaturas JVM de `kof_web_*`. |
| Decisão | `docs/development/DECISIONS.md` §D-SEC.3/.4 (`:368`) | A ordem fixa da pipeline e a lei de "autenticar por padrão". |
| Docs | `docs/stdlib/stdlib-web.md` §3 `app.security()`; `training/idioms/web.md` | O contrato de usuário de `app.use` e `app.security`. |
| Testes | `KofWebHardeningTest` (6), `KofWebE2ETest` (10), `KofHttpServerTest` (8) | Segurança global + roteamento E2E com sockets reais. |
| Outros alvos | `.../nat/NativeWebRuntime.java`, `.../js/JsRuntimeUiWeb.java` | Base web (WEB001); `app.security` é **gap de compile-time `WEB006`** (`KofWeb.gapCode`, `:189`). |

**O gap:** as políticas são **globais ao app**. O único controle por path é a
allow-list `publicPaths` dentro dos opts globais. Para proteger um endpoint, hoje
o usuário precisa escrever `app.use { ... if (path() == "/admin") ... }` —
exatamente o `if`/`switch` manual que a melhoria quer remover. `WebRoute` não tem
campo para carregar política por rota.

**Fato útil (verificado):** a gramática **já parseia** uma chamada com argumentos
seguida de lambda à direita — `ExpressionParser.java:202-212` anexa o bloco
`{ ... }` como último argumento depois de `parseArguments`. Então
`app.get("/x", opts) { ... }` já é parseável hoje; só a tabela `KofWeb` e o typer
precisam aceitar o argumento extra.

---

## 3. Comportamento desejado

`app.security(opts)` continua sendo a **política default global**. Duas formas
aditivas a escopam:

```kof
main() {
    auth.secret(secrets.get("JWT_SECRET", "dev"))
    var app = web.app()

    // 1. GLOBAL default — superfície inalterada, mesmas opções.
    var g = mapOf()
    g.put("rateLimit", "200/60")
    g.put("auth", true)
    app.security(g)

    // 2. Política de RECURSO — vale p/ toda rota sob o prefixo.
    var admin = mapOf()
    admin.put("roles", "admin")
    admin.put("rateLimit", "20/60")
    admin.put("responses", mapOf("forbidden", "{\"error\":\"forbidden\"}"))
    app.policy("/admin", admin)

    // 3. Política de ENDPOINT — a forma lambda à direita com um Map de opts.
    var strict = mapOf("auth", true, "rateLimit", "5/60")
    app.post("/login", strict) {
        return "{\"token\":\"...\"}"
    }

    // Público por intenção, não por um `if` dentro de cada handler:
    var pub = mapOf("publicPaths", "/health,/metrics")
    app.policy("*", pub)

    app.get("/admin/users") { return usersJson() }   // herda a política /admin
    app.get("/health") { return "ok" }               // público (allow-list)
    app.listen(8080)
}
```

Regras que a superfície deve tornar óbvias (declaradas, não re-derivadas pelo
usuário):

- Um handler **nunca re-checa** o que uma política já declarou.
- `publicPaths`/`permitAll` e `roles` **acumulam** entre escopos
  (allow-lists crescem; o escopo mais profundo adiciona, nunca remove).
- Toda outra chave **sobrepõe** (o escopo mais profundo que casa vence).
- A **ordem da pipeline é fixa pelo runtime** (§D-SEC.3); o usuário nunca a
  compõe.

---

## 4. Modelo semântico

### 4.1 Valor Policy

Um `Policy` é a forma parseada do `Map` de opts existente (mesmas chaves, mesmos
defaults de `app.security`). O parse é exatamente o corpo atual de
`kof_web_security_opts`, extraído em um parser reutilizável:

```
Policy.parse(Map opts, Policy base) -> Policy     // base = escopo pai (opcional)
Policy.merge(child)                               // escalar: filho vence; listas: união
Policy.appliesTo(String path)                     // match de prefixo (ver 4.2)
```

### 4.2 Match de escopo

- Um escopo é registrado com um **prefixo de path simples** (`/admin`, `/api/v1`).
- `"*"` (ou `""`) significa "toda requisição".
- O match é de **prefixo** no path normalizado da requisição; o **prefixo mais
  longo** que casa vence.
- **Sem globs/regex na v1** — simples e previsível; se surgir necessidade real,
  vira decisão da regra 6, não um motor de glob silencioso.
- Path params no matcher **não** são suportados na v1 (só prefixo).

### 4.3 Precedência (a lei de merge)

Para uma requisição cujo path casa `P` e, quando uma rota casa, cuja rota carrega
a política `E`:

```
effective = global.merge( P1.merge( P2.merge( ... Pn ) ) ).merge( E )
```

onde `P1..Pn` são os escopos de path do **menor ao maior prefixo** (o maior
aplicado por último → vence nos escalares). Chaves escalares: **o mais profundo
vence**. Chaves de lista (`publicPaths`, `roles`): **união** de todos os escopos.

`publicPaths` é avaliado **uma vez** contra o path da requisição com a allow-list
efetiva (um path público é público independentemente de qual escopo o declarou).

### 4.4 Política de endpoint vs. o invariante global-antes-do-roteamento

Hoje a pipeline roda **antes** do match de rota, então um path desconhecido ainda
recebe headers de segurança / auth. Esse invariante deve sobreviver:

1. Computar `pathPolicy` dos escopos que casam o path da requisição.
2. Casar a rota (sem invocar).
3. `effective = pathPolicy.merge(routePolicy)` se uma rota casou, senão
   `pathPolicy`.
4. Rodar a pipeline fixa com `effective`.
5. Em caso de passe: invocar o handler, ou responder o 404 com o payload/headers
   de rejeição **efetivos**.

### 4.5 Payloads de rejeição (declarativos)

Nova chave documentada de opt `responses` (um `Map`) permite ao app declarar o
corpo devolvido pelas rejeições sintéticas da pipeline, em vez do JSON embutido:

| Chave | Disparada por |
|---|---|
| `unauthorized` | 401 (auth/session) |
| `forbidden` | 403 (roles/CORS/CSRF) |
| `notFound` | 404 (ausência documentada — `return null`) |
| `tooManyRequests` | 429 (rate limit) |

O valor é o corpo literal da resposta (`String`); o `Content-Type` segue a
auto-detecção de JSON existente. Chaves ausentes mantêm os corpos embutidos de
hoje (**compatível para trás**). Payloads não referenciam nada (sem template na
v1) — um corpo estático, que é o caso comum de WebService.

---

## 5. Arquitetura (interna)

### 5.1 Forma do runtime

- Um novo holder imutável `Policy` dentro do `KofRuntime` gerado
  (em `JvmWebSecurityRuntime.java`, onde o parser já vive).
- `WebApp` ganha:
  - `Policy globalPolicy` (substituindo os campos planos `security*` — refactor
    puro, **sem mudança de comportamento**);
  - `List<ScopedPolicy> policies` (`prefix` + `Policy`);
  - mapa `responses` (parte do `Policy`).
- `WebRoute` ganha um campo: `Policy policy` (`null` para as rotas de hoje).
- A assinatura da pipeline vira
  `kof_web_security_pipeline(WebApp app, WebRequest req, Policy effective)`.

### 5.2 Novas funções `kof_web_*`

| Superfície Kof | Função runtime | Assinatura (JVM) |
|---|---|---|
| `app.policy(prefix, opts)` | `kof_web_policy` | `(String appId, String prefix, Map opts)V` |
| `app.get/post/... (path, opts) { }` | `kof_web_route` **overload** | `(String appId, String method, String path, Map opts, Object handler)V` |

`kof_web_route` mantém a forma de 4 args inalterada; a de 5 args é aditiva.

### 5.3 Mudanças no dispatch (`JvmRuntimeWebDispatch.java`)

- `kof_web_security_pipeline` lê o `Policy effective` passado em vez dos campos
  `app.security*`.
- `kof_web_dispatch` computa `pathPolicy` + `routePolicy` e passa a política
  efetiva à pipeline (reestruturação descrita em 4.4).
- `kof_web_build`/helpers de rejeição leem `responses` da política efetiva.

---

## 6. Pontos exatos do código a alterar

| # | Arquivo | Mudança |
|---|---|---|
| 1 | `KofWeb.java` | Adicionar `policy` no `instanceMethod` (switch `:105`); aceitar a forma de 3 args de rota (`:99`); estender `gapCode` para `kof_web_policy` e a rota de 5 args serem `WEB006` fora do JVM. |
| 2 | `JvmWebCoreRuntime.java` | `WebRoute` (`:93`) + `policy`; `WebApp` (`:384`) → `Policy globalPolicy` + lista `policies`; overload de `kof_web_route` (`:468`); novo `kof_web_policy`. |
| 3 | `JvmWebSecurityRuntime.java` | Extrair `Policy` + `parse`/`merge`/`appliesTo`; `kof_web_security_opts` monta `globalPolicy`; parse de `responses`. |
| 4 | `JvmRuntimeWebDispatch.java` | `kof_web_security_pipeline(..., Policy)`; resolução da política efetiva em `kof_web_dispatch` (`:173`); payloads de rejeição. |
| 5 | `JvmRuntimeCallDescriptors.java:210` | Descriptors de `kof_web_policy` e da rota de 5 args. |
| 6 | `BuiltinCallTyper.java` / `ExpressionBuiltinInstanceCalls.java` (`:70`) | Métodos de rota com `(STR, MAP, handler)`; `policy(STR, MAP)`. |
| 7 | `CompilerComparisons.java:514` | Classificação valo/void das novas chamadas. |
| 8 | `JsRuntimeUiWeb.java` + `NativeWebRuntime.java` | Gate de compile-time `WEB006` para `app.policy` / opts de rota (não ignorar em silêncio — R6). |

> A gramática da lambda à direita (`ExpressionParser.java:202-212`) **não**
> precisa de mudança — confirmado lendo o parser.
>
> Os touchpoints 6/7 (`BuiltinCallTyper` / `CompilerComparisons`) **não**
> precisaram de mudança: as chamadas de rota já baixam por
> `ExpressionBuiltinInstanceCalls`, e as formas rota-de-3-args / `policy` estão
> cobertas por suítes verdes (F2–F6). Verificado, não assumido.

---

## 7. Impacto nos backends

| Alvo | v1 | Regra |
|---|---|---|
| **JVM** | Implementação completa. | Primeiro alvo (mesmo precedente de `app.security`, `serveDir`, `ws`). |
| **Native** | `WEB006` em compile-time (gap honesto). | R7/R6: declarado, nunca silencioso. |
| **JS** | `WEB006` em compile-time. | Idem. |

A dívida de paridade é **explícita**: o plano não promete políticas em
Native/JS; seguem gap documentado até promoção separada (regra 6).

---

## 8. Erros e comportamento inválido (R6 — nunca silencioso)

- Chave de opt desconhecida → **ignorada** (comportamento de hoje, mantido por
  compatibilidade). Uma decisão futura pode virar warning; não na v1.
- `rateLimit` inválido (sem `/`) → `IllegalArgumentException` (existente).
- Prefixo inválido em `policy` → `IllegalArgumentException` no startup. `"*"` e
  `""` significam "toda request" (§4.2); qualquer outro prefixo deve começar com
  `/`. (Supera o texto anterior "vazio/em branco é inválido", que contradizia
  §4.2.)
- `app.policy` com opts que não é `Map` → rejeição em compile-time (o typer
  devolve `null` → caminho existente de "chamada de instância não suportada"),
  nunca um no-op silencioso.
- Em alvos não-JVM → `WEB006` em compile-time (sem política descartada em
  silêncio).
- Erros acontecem quando o app é **montado**, antes do `listen`, então uma
  política mal configurada falha rápido.

---

## 9. Impacto de compatibilidade

- **Aditivo.** `app.security(opts)`, `app.use`, `app.get(path, handler)` e
  lambdas à direita de 1 arg seguem inalterados.
- Os campos planos `WebApp.security*` viram um objeto `Policy` — refactor
  **interno**, invisível no comportamento do bytecode (provado pela suíte de
  hardening existente continuar verde, fatia F1).
- Sem mudança de gramática, sem keyword nova, sem tipo novo exposto ao usuário
  além do `Map` de opts que `app.security` já usa.

---

## 10. Riscos

1. **Regressão da ordem do dispatch** — mover o match de rota para antes da
   pipeline pode mudar o comportamento de paths desconhecidos. Mitigação: F2
   mantém o resultado da pipeline global atual em todos os testes existentes; o
   comportamento novo só é alcançado quando existem escopos.
2. **Thread-safety da política** — políticas são lidas a cada requisição; tornar
   `Policy` imutável e montar a lista de escopos antes do `listen`.
3. **Chave do rate-limit** — um limite por rota precisa de chave composta
   `ip + padrão de rota efetivo`; hoje o contador é por IP com a janela global.
   F5 precisa adicionar a dimensão de rota sem quebrar o limite global.
4. **Superfície de tipo/typer** — suportar `(STR, MAP, handler)` no typer não
   pode enfraquecer a inferência valo/void existente (`CompilerComparisons:514`).
5. **Dois alvos atrás** — o gate em Native/JS precisa entrar na primeira fatia,
   ou a feature vaza um no-op silencioso (violação do R6).

---

## 11. Alternativas consideradas

| Alternativa | Por que descartada |
|---|---|
| **Framework genérico de middleware** (registrar/reduzir/next) | Contraria a filosofia: é framework, adiciona complexidade acidental, e o usuário precisa reconstruir a ordem. |
| **Anotações** (`@Auth`, `@RateLimit`) | Kof não tem anotações como fundação (non-goal permanente). |
| **Sintaxe de bloco `route`** (`route GET "/x" with auth { }`) | Mudança de gramática; mais pesada que a forma lambda-à-direita + opts que já parseia; regra 6. |
| **Config de política em `kof.toml`** | Não expressa valores de runtime (secrets, roles do código); menos reveladora de intenção; mais difícil de associar por endpoint. |
| **Status quo (só `app.security` global + `app.use` manual)** | Falha na necessidade central: força `if path() == ...` por endpoint — exatamente o que se quer remover. |
| **Globs/regex completos na v1** | Complexidade acidental; um prefixo cobre os casos reais; globs seriam decisão posterior da regra 6. |

---

## 12. Fatias ordenadas de implementação (cada uma provável de forma independente)

> Cada fatia: compile + teste + `check_500`; commit por fatia. Nenhuma fatia
> entra sem prova verde e docs atualizadas.

**Status das fatias (28/09/2026):** ✅ F0–F6 todas pousadas — plano concluído,
movido para `docs/stdlib/http-policies-plan.md`.

- **F0 — Payloads `responses` globais no `app.security(opts)` (aditivo).**
  `JvmWebSecurityRuntime.kof_web_security_opts` parseia um Map `responses`
  (`unauthorized`/`forbidden`/`tooManyRequests`/`notFound`); `WebApp.securityResponses`
  + `JvmRuntimeWebDispatch.kof_web_sec_response` alimentam os 401/403/429 do
  pipeline fixo; chaves ausentes mantêm os corpos embutidos (compat). **Prova:**
  `KofWebE2ETest` 27/27 — nova `securityDeclarativePayloadsForUnauthorizedAndRateLimit`
  (401+429 custom, padrões preservados por `appSecurityPipelineE2E`) +
  `securityDeclarativeForbiddenPayload` (403 custom); `KofWebHardeningTest` 6/6,
  `KofBlogE2ETest` 1/1, `KofOAuthResourceServerTest` 4/4, `DomainGapCodesTest` 27/27.
  Docs: `docs/stdlib/stdlib-web.md` §3, `training/idioms/web.md`.

- **F1 — Refactor para `Policy` (sem mudança de comportamento).** ✅ POUSADA 28/09:
  `Policy` (`parse`) em `JvmWebSecurityRuntime.java` + `WebApp.globalPolicy`
  substituem os campos planos; `kof_web_security_pipeline(app, req, Policy)` lê a
  política. **Prova:** `KofWebE2ETest` 27/27, `KofWebHardeningTest` 6/6,
  `KofBlogE2ETest` 1/1, `KofOAuthResourceServerTest` 4/4, `DomainGapCodesTest` 27/27.
- **F2 — `app.policy(prefix, opts)` (escopos de recurso).** ✅ POUSADA 28/09:
  `ScopedPolicy` + `Policy.merge`/`copy`/marcadores em `JvmWebSecurityRuntime.java`;
  `kof_web_policy` + `kof_web_effective_policy` (prefixo mais longo);
  `WebApp.policies`; caso `policy` em `KofWeb` + `kof_web_policy`→`WEB006`;
  descriptors. **Prova:** nova `KofHttpPoliciesE2ETest` 5/5 — escopo sob `/admin`
  apenas (401/403/200), escalar `headers:false` vence, união de
  `publicPaths`/`roles`, `WEB006` em Native/JS; regressão web 65/65.
- **F3 — Opts de endpoint (`app.get(path, opts) { }`)**, ✅ POUSADA 28/09: caso
  `policy`/rota-de-3-args em `KofWeb` → `kof_web_route_opts`; `WebRoute.policy` +
  ctor sobrecarregado; runtime `kof_web_route_opts`; `kof_web_match_route`
  pré-casa e funde a política de endpoint na efetiva antes da pipeline; `WEB006`
  em Native/JS. **Prova:** `KofHttpPoliciesE2ETest#endpointPolicyOverridesScope`
  (endpoint `headers:true` vence o `false` do escopo; rota sem opts herda; global
  fora) + `WEB006` das route-opts; regressão web verde.
- **F4 — Payloads declarativos `responses`.** ✅ POUSADA 28/09: a pipeline já lê a
  política **efetiva** (`kof_web_sec_response(p, …)`), e os dois caminhos de 404
  (`return null` + path desconhecido) agora honram o payload `notFound`.
  **Prova:** `KofHttpPoliciesE2ETest#responsesUseEffectivePolicy` (escopo herda o
  `unauthorized` global, sobrepõe o `forbidden`) +
  `#notFoundPayloadFromEffectivePolicy` (os dois 404); chaves ausentes mantêm os
  corpos embutidos (teste de compat do F0).
- **F5 — Chave de rate-limit por rota** (`ip + padrão de rota`). ✅ POUSADA 28/09:
  `WebRoute.path` (padrão registrado) + `kof_web_security_pipeline(..., String
  routePattern)` usam a chave `ip + "|" + padrão` (`matched.path`, ou `""` para
  path desconhecido). **Prova:** `KofHttpPoliciesE2ETest#perRouteRateLimitKeys` —
  `/a` (limite 1), `/b` (limite 2, contador próprio), `/c` (global 3) não
  compartilham contador; `securityRateLimitByRemoteAddress` (rota única) segue
  verde.
- **F6 — Docs + decisão + gaps.** ✅ POUSADA 28/09: validação de prefixo (`"*"`/`""`
  = todos, senão deve começar com `/`) com
  `KofHttpPoliciesE2ETest#invalidPolicyPrefixFailsAtStartup`; `WEB006` em
  Native/JS provado (call sites client + server); `stdlib-web` §3 +
  `training/idioms/web` (EN+PT); `D-HTTP-POLICIES` travada; roadmap Fase 4
  linkada. Plano concluído → movido para `docs/stdlib/` (regra dos 3 estados).

---

## 13. Testes necessários (resumo)

| Fatia | Teste | Foco |
|---|---|---|
| F1 | `KofWebHardeningTest`, `KofWebE2ETest` (inalterados) | zero regressão |
| F2 | `KofHttpPoliciesE2ETest` (escopo de recurso, precedência, sem match) | a lei de merge |
| F3 | override + herança de endpoint | o mais profundo vence |
| F4 | payloads de rejeição + defaults | corpos declarativos |
| F5 | rate limit por rota vs global | chave composta |
| F6 | `WEB006` em Native/JS + `kof check` | gap honesto |

Todos os testes compilam um programa `.kf` e o dirigem por **sockets reais**
(padrão dos testes web existentes: `KofWebE2ETest`), então a prova é o servidor
rodando, não a memória.

---

## 14. Documentação necessária quando promovida

- `docs/stdlib/stdlib-web.md` §3 — novo `app.policy` + opts de endpoint +
  `responses`, com a lei de merge e a tabela de precedência.
- `training/idioms/web.md` — um par BAD (`if path() == ...` por endpoint) → GOOD
  (`app.policy`).
- `docs/development/DECISIONS.md` — `D-HTTP-POLICIES` (regra 6): superfície + lei
  de merge + escopo JVM-first.
- `docs/development/roadmap.md` Fase 4 (Security) — linkar a frente.
- Este documento sai de `docs/development/future/` para `docs/development/` com
  `UNDER DEVELOPMENT` na F1 (regra dos três estados).

---

## 15. Condições de promoção

- A mantenedora trava `D-HTTP-POLICIES` no `DECISIONS.md` (regra 6).
- A lane web atual não tem mudança em voo em
  `JvmWebCoreRuntime`/`JvmRuntimeWebDispatch` (evitar colisão de mesmo arquivo).
- R12: abre só com autorização explícita de fatia (é uma frente nova sobre a
  superfície da linguagem/stdlib — o gate da Lei da Simplicidade se aplica à
  superfície antes de pousar).
