# stdlib web — Stack Web Nativa do Kof

**Última atualização:** 4 de setembro de 2026
**Versão:** 0.2.6-beta (`kof.http` JVM+JS + retry/circuit; WebSocket/SSE JVM + hardening)
**Status:** implementado (Fase 1 do plano de independência do Spring) — `kof serve` + `kof.http` JVM+JS + `app.ws`/`app.sse` JVM + limites/contadores

---

## 1. Filosofia

> Uma aplicação web Kof não precisa de Spring. HTTP, rotas, JSON, contexto de
> request e middleware são parte do ecossistema Kof.

Nenhuma dependência externa: o servidor HTTP é gerado dentro do runtime JVM do
próprio programa compilado (`dev.kof.runtime.KofRuntime`). Sem servlet
container, sem Spring MVC, sem annotations.

## 2. Exemplo completo

```kof
record User(String name, Int age)

main() {
    var app = web.app()

    // Middleware: retorna null para continuar; String para responder direto
    app.use {
        if (header("x-auth") == "secret") {
            return null
        }
        return "{\"error\": \"unauthorized\"}"
    }

    app.get("/hello") {
        return "Hello from Kof"
    }

    // Path parameter + query string
    app.get("/users/:id") {
        return "user " + param("id") + " q=" + query("name")
    }

    app.get("/agent") {
        return "agent=" + header("user-agent")
    }

    app.get("/me") {
        return method() + " " + path()
    }

    // Corpo da request
    app.post("/echo") {
        return "got:" + body()
    }

    // JSON tipado de ponta a ponta
    app.post("/user") {
        var user = json.decode<User>(body())
        return json.encode(user)
    }

    app.listen(8080)
}
```

```bash
kof serve app.kf              # compila e executa (a app chama app.listen)
kof run app.kf                # idem — o programa inicia o próprio servidor
```

## 3. API

### `web.app()`

Cria uma aplicação. O valor retornado (`kof.web.App`) é um handle; em runtime
é um identificador de registro interno.

### Rotas

| Chamada | Método HTTP |
|---------|-------------|
| `app.get(path) { ... }` | GET |
| `app.post(path) { ... }` | POST |
| `app.put(path) { ... }` | PUT |
| `app.delete(path) { ... }` | DELETE |
| `app.patch(path) { ... }` | PATCH |
| `app.options(path) { ... }` | OPTIONS |

O corpo `{ ... }` é um lambda trailing — o handler da rota. Um handler pode
também ser passado explicitamente: `app.get("/x", handler)`.

- `path` suporta segmentos com parâmetro: `/users/:id` (prefixo `:`).
- O handler retorna `String` (corpo da resposta, 200) ou `null` (404).
- A resposta detecta JSON automaticamente quando o corpo começa com `{` ou `[`
  (`Content-Type: application/json`).

### Middleware

`app.use { ... }` registra um middleware executado antes do roteamento.
Retorno `null` → continua; retorno `String` → resposta imediata (200).

### Servidor

| Chamada | Descrição |
|---------|-----------|
| `app.listen(port)` | Inicia o servidor (bloqueante) em `0.0.0.0` |
| `app.listenSecure(port)` | Idem, com TLS (JVM; self-signed `keytool` + `SSLServerSocket`) |
| `app.port()` | Porta efetivamente vinculada (útil com `listen(0)`) |
| `app.close()` | Encerra o servidor (graceful shutdown) |

`app.listen(0)` vincula uma porta efêmera; `app.port()` revela a porta real.
`app.listenSecure` está disponível no JVM (Native/JS `WEB002`).

### Arquivos estáticos (`app.serveDir`) (31/08)

| Chamada | Descrição |
|---------|-----------|
| `app.serveDir(prefix, dir)` | Serve os arquivos de `dir` sob `prefix` (fallback após as rotas dinâmicas) |

O handler devolve o **arquivo em binário** do disco com `Content-Type` pela
extensão (HTML/CSS/JS, imagens, áudio, **vídeo**, fontes, PDF...),
`Cache-Control` e proteção contra path-traversal (`..`). É a alternativa a
colar base64/HTML/CSS em `String` literal no fonte — o app trata o ARQUIVO.

**Range requests**: `serveDir` responde `Range: bytes=...` com `206 Partial
Content` + `Content-Range` + `Accept-Ranges: bytes` (e `416` para range
inválido). Isso é o que permite `<video>`/`<audio>` navegarem e seekarem no
browser — sem Range, o player não consegue posicionar no meio do arquivo.

```kof
var app = web.app()
app.serveDir("/media", "assets")   // GET /media/clip.mp4 → bytes + Range 206
app.listen(8080)
```

```html
<video src="/media/clip.mp4" controls></video>
```

Caminhos relativos do app resolvem contra a raiz do projeto
(`-Dkof.root`, definido pelo CLI `run`/`serve`). **JVM-only** — Native/JS
reportam `WEB005` (gap documentado).

### Health (`app.health`) (01/09)

| Chamada | Descrição |
|---------|-----------|
| `app.health(path)` | Registra um endpoint de saúde built-in (ex.: `/health`) |

`app.health("/health")` responde com o estado do app em JSON
(`{"status":"UP","ready":true,"alive":true}` — valor de
`observability.health()/readiness()/liveness()`) **antes dos middlewares**:
sondas de load balancer/health-check não passam por auth/middleware. O app
também pode montar o próprio: `app.get("/health") { return
observability.health() }`.

```kof
var app = web.app()
app.health("/health")   // GET /health → {"status":"UP","ready":true,"alive":true}
app.listen(8080)
```

### WebSocket (`app.ws`, RFC 6455) (30/08)

| Chamada | Descrição |
|---------|-----------|
| `app.ws(path) { ... }` | Rota WebSocket (route kind `WS`) |
| `wsMessage()` | Texto da mensagem `TEXT` que acionou o handler (String) |
| `wsSend(text)` | Envia um frame `TEXT` de volta pela conexão corrente |

O handshake RFC 6455 e o frame codec (com máscara cliente→servidor) são
implementados dentro do engine HTTP gerado; o handler Kof é chamado por
mensagem `TEXT`. O runtime também trata `PING`→`PONG`, `CLOSE` (ack) e
descarta frames acima do limite configurável de frame (default 1 MiB, close
`1009`).

```kof
app.ws("/chat") {
    var m = wsMessage()
    if (m == "bye") {
        return
    }
    wsSend("echo: " + m)
}
```

### Server-Sent Events (`app.sse`) (30/08)

| Chamada | Descrição |
|---------|-----------|
| `app.sse(path) { ... }` | Rota SSE (route kind `SSE`); o handler recebe o sender como parâmetro `sse` |
| `sse.send(data)` | Evento sem nome (`data: ...`) |
| `sse.event(name, data)` | Evento com nome (`event: name\ndata: ...`) |
| `sse.close()` | Encerra o stream do cliente |
| `sse.isOpen()` | `Bool` — o stream segue aberto |

Cada conexão SSE é independente (ThreadLocal por conexão); os headers
`Content-Type: text/event-stream`, `Cache-Control: no-cache`,
`Connection: keep-alive` e `X-Accel-Buffering: no` são emitidos.

```kof
app.sse("/events") {
    sse.send("one")
    sse.event("tick", "two")
    sse.close()
}
```

`app.ws` e `app.sse` estão disponíveis no **JVM**. Em outros targets são
gaps documentados em compile-time: WebSocket → `WEB004`, SSE → `WEB003`
(Native/JS).

### Limites e observabilidade (`app.configure`, `app.stats`)

| Chamada | Descrição |
|---------|-----------|
| `app.configure("maxConnections", n)` | Cap de conexões concorrentes (default `1024`); acima disso responde `503` |
| `app.configure("maxFrameBytes", n)` | Limite de frame WebSocket mutável (default `1 MiB`) |
| `app.configure("maxMessageBytes", n)` | Limite de mensagem WebSocket mutável (default `8 MiB`) |
| `app.configure("idleMs", n)` | Idle timeout aplicado a WebSocket e deadline SSE |
| `stats("SSE_CONNECTIONS_ACTIVE")` | Conexões SSE ativas |
| `stats("WS_CONNECTIONS_ACTIVE")` | Conexões WebSocket ativas |
| `stats("SSE_EVENTS_SENT")` | Eventos SSE enviados |
| `stats("WS_MESSAGES_RECEIVED")` / `stats("WS_MESSAGES_SENT")` | Mensagens WS recebidas/enviadas |

`app.configure` atua no app corrente (por handle); as estatísticas são
globais por JVM e devolvidas como `String`.

### Contexto de request (dentro de handlers/middleware)

| Função | Retorna |
|--------|---------|
| `param("id")` | Path parameter |
| `query("name")` | Query parameter |
| `header("x-auth")` | Header (case-insensitive) |
| `body()` | Corpo cru da request |
| `method()` | Método HTTP ("GET", "POST", ...) |
| `path()` | Caminho da request |
| `status(code, body)` | Define o status da resposta e retorna o corpo — use como retorno (ex.: `return status(201, "{\"ok\":true}")`) |
| `headerSet(name, value)` | Adiciona um header de resposta (ex.: `headerSet("X-Total", "42")`) |

O contexto é por-request (ThreadLocal em runtime) — handlers podem ser
concorrentes sem estado compartilhado. `status(code, body)` e
`headerSet(name, value)` permitem respostas ricas (status customizado +
headers) — antes os handlers só produziam 200/404 automáticos.

## 4. Concorrência

Cada conexão é tratada em uma virtual thread (JVM). O programador escreve
handlers síncronos; o runtime decide a estratégia. Handlers SSE rodam no
`KOF_SSE_HANDLERS` compartilhado e têm deadline `idleMs * 4`; em timeout o
stream é fechado e a task cancelada.

## 5. Limitações atuais (Fase 1, 0.2.6-beta)

- O target `js` reporta `WEB001` para a stack web (gap documentado); `kof.http` já funciona no JS via `Java HttpClient`.
- O target `native` (`x86_64`/`riscv64`/`aarch64`) não possui servidor web ainda (`WEB002` TLS também).
- `app.ws`/`app.sse` são JVM-only (Native `WEB004`, JS `WEB003`).
- `app.serveDir` (arquivos estáticos + Range 206/416) é JVM-only (Native/JS `WEB005`).
- Hardening PR6 (connection cap, limites `maxFrameBytes`/`maxMessageBytes`,
  `idleMs`, `app.stats`) é JVM; backpressure e fragmentação seguem follow-up.
- `kof.http` client — ✅ JVM+JS (27/08; `timeout/retry/circuit` em paridade 30/08), Native `HTTP002` pendente.
- Middleware/rotas de outros métodos HTTP além dos listados: futuramente.

> Fechas nesta fase (27–30/08): status codes + headers customizados
> (`status(code, body)` / `headerSet(name, value)`); `kof.cache` nos 3
> targets; `WebSocket` (`app.ws`) + `SSE` (`app.sse`) no JVM; `http.retry`/
> `http.circuit` em paridade JVM+JS.

## 6. Testes (0.2.6-beta)

`KofWebE2ETest` 10 + `KofHttpServerTest` 8 + `KofHttpE2ETest` 4 (JVM+JS,
27/08) + `KofWebTlsTest` 5 + `KofWebSseE2ETest` 7 + `KofWebWsE2ETest` 11 +
`KofWebStreamE2ETest` 4 + `KofWsFrameTest` 7 + `KofHttpResilienceE2ETest` 3 +
`KofWebHardeningTest` 6 —
cada teste compila um programa Kof, executa o bytecode/JS como subprocesso e
exercita o servidor/cliente com sockets reais (routing, path params, query,
headers, body, JSON round-trip, middleware, 404, múltiplas rotas com lambda
trailing, `http.get/post/put/delete` + TLS + `retry`/`circuit`, handshake
WebSocket RFC 6455, frame codec com máscara, SSE eventos nomeados/multi-line,
streaming WS/SSE concorrente).

## 7. Arquitetura

```
Kof source (.kf)
   ↓ CompilerDriver
Kof IR (KofCall kof_web_*)
   ↓ JvmBackend
bytecode JVM
   ↓
dev.kof.runtime.KofRuntime (gerado)  ← engine HTTP embutido no programa
   ├── KOF_WEB_APPS (registro de apps)
   ├── WebRoute (method, segments, params, handler, kind)
   ├── SseConnection / WsConnection / WsFrame
   ├── WebRequest (method, path, query, headers, body)
   └── accept loop (virtual threads) + dispatch
```

As chamadas `kof_web_*` são resolvidas em compile-time pela tabela `KofWeb`
(dança análoga a `KofIo`): o programador nunca vê threads, sockets ou parsing
HTTP.

## 8. Referências

- Plano: `docs/plan-spring-independence.md` (Fase 1)
- Status: `docs/status.md`
- Roadmap: `docs/roadmap.md` (Fase 3 — Web Platform)
