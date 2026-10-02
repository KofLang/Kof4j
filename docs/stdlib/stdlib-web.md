[English](stdlib-web.md) | [Português](stdlib-web.pt_BR.md)

# stdlib web — Kof's Native Web Stack

**Last updated:** September 4, 2026
**Version:** 0.5.0-beta (`kof.http` JVM+JS + retry/circuit; WebSocket/SSE JVM + hardening)
**Status:** implemented (Phase 1 of the Spring independence plan) — `kof serve` + `kof.http` JVM+JS + `app.ws`/`app.sse` JVM + limits/counters

---

## 1. Philosophy

> A Kof web application does not need Spring. HTTP, routes, JSON, request
> context and middleware are part of the Kof ecosystem.

No external dependency: the HTTP server is generated inside the JVM runtime of
the compiled program itself (`dev.kof.runtime.KofRuntime`). No servlet
container, no Spring MVC, no annotations.

## 2. Complete example

```kof
record User(String name, Int age)

main() {
    var app = web.app()

    // Middleware: returns null to continue; String to respond directly
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

    // Request body
    app.post("/echo") {
        return "got:" + body()
    }

    // End-to-end typed JSON
    app.post("/user") {
        var user = json.decode<User>(body())
        return json.encode(user)
    }

    app.listen(8080)
}
```

```bash
kof serve app.kf              # compiles and runs (the app calls app.listen)
kof run app.kf                # same — the program starts its own server
```

## 3. API

### `web.app()`

Creates an application. The returned value (`kof.web.App`) is a handle; at
runtime it is an internal registry identifier.

### Routes

| Call | HTTP method |
|---------|-------------|
| `app.get(path) { ... }` | GET |
| `app.post(path) { ... }` | POST |
| `app.put(path) { ... }` | PUT |
| `app.delete(path) { ... }` | DELETE |
| `app.patch(path) { ... }` | PATCH |
| `app.options(path) { ... }` | OPTIONS |

The body `{ ... }` is a trailing lambda — the route handler. A handler can
also be passed explicitly: `app.get("/x", handler)`.

- `path` supports segments with a parameter: `/users/:id` (`:` prefix).
- The handler returns `String` (response body, 200) or `null` (404).
- The response detects JSON automatically when the body starts with `{` or `[`
  (`Content-Type: application/json`).

### Middleware

`app.use { ... }` registers a middleware executed before routing.
Return `null` → continues; return `String` → immediate response (200).

### Security (`app.security()`) — D-SEC C18 (14/09)

| Call | Description |
|------|-------------|
| `app.security()` | Composite middleware with secure defaults (hardening headers) |
| `app.security(opts)` | Same, with overrides via `Map` |
| `app.policy(prefix, opts)` | Resource policy — same opts, applied to every route under `prefix` (D-HTTP-POLICIES, F2) |

Applies the **fixed order** rate-limit → CORS → headers → cookies/session → csrf →
auth → RBAC → route (D-SEC). Replaces the manual `app.use` chain.

Without arguments, enables the **hardening headers** (always safe) and **CSRF**
for state-changing methods:

- `Content-Security-Policy: default-src 'self'; frame-ancestors 'none'; base-uri 'self'`
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Referrer-Policy: no-referrer`
- `Strict-Transport-Security` — only under TLS (`listenSecure`)

Documented opts (`Map` keys; any other is ignored):

| Key | Type | Default | Effect |
|-----|------|---------|--------|
| `headers` | `Bool` | `true` | Enables/disables the headers above |
| `cors` / `corsOrigin` | `String` | off | Allowed origin, CSV or `*`. Origin not listed → 403; `OPTIONS` preflight → 204 |
| `rateLimit` | `String` or `Number` | off | `"limit/windowSeconds"` (e.g. `"100/60"`) or just the limit. Per remote IP **+ route pattern**; exceeded → 429 + `Retry-After` |
| `csrf` | `Bool` | `true` | Double-submit cookie: emits `csrf` (SameSite=Lax) on safe methods; requires `X-CSRF-Token` matching the cookie on POST/PUT/PATCH/DELETE, otherwise 403. `csrf:false` disables |
| `sessionHeader` | `String` | off | Session header name. Outside `publicPaths`, **every** request (GET included) requires a valid session; missing/invalid → 401 |
| `publicPaths` / `permitAll` | `String` CSV | — | Allow-list of public matchers (e.g. `"/register,/login"`); everything else requires authentication |
| `auth` | `Bool` | `false` | Requires a valid `Authorization: Bearer` JWT (secret via `auth.secret`); missing/invalid → 401 + `WWW-Authenticate` |
| `roles` | `String` CSV or `List` | — | Requires all roles (claims `roles`); missing → 403 (implies auth) |
| `responses` | `Map` | off | Declarative bodies for the pipeline's synthetic rejections: `unauthorized` (401), `forbidden` (403), `tooManyRequests` (429), `notFound` (404). Value = literal body (JSON auto-detected). Missing keys keep the built-in bodies (backward compatible) |

**Declarative rejection payloads (D-HTTP-POLICIES):** `responses` overrides the
built-in JSON of the pipeline rejections — e.g.
`o.put("responses", mapOf("unauthorized", "{\"error\":\"nope\"}"))` makes every
401 answer that body. Absent keys keep today's bodies, so adding `responses` is
additive and never changes an undeclared status. With scopes/endpoints the
payload comes from the **effective** policy, and `notFound` also feeds both 404
paths (`return null` and unknown route).

**Auth-if-present:** even without `auth: true`, a request that **carries**
`Authorization` with an invalid token never passes (401) — avoids "bad token
becomes anonymous".

```kof
main() {
    auth.secret(secrets.get("JWT_SECRET", "dev"))
    var app = web.app()
    var o = mapOf()
    o.put("cors", "https://app.example")
    o.put("rateLimit", "100/60")
    o.put("auth", true)
    o.put("roles", "admin")
    app.security(o)
    app.get("/admin") { return "ok" }
    app.listen(8080)
}
```

**Security by default:** `listen`/`listenSecure` with `KOF_ENV=production`
without `app.security()` warns on `stderr` (never fails silently).

**Resource policies (D-HTTP-POLICIES, F2):** `app.policy(prefix, opts)` scopes
the same opts to routes under a plain path prefix (`/admin`, `/api/v1`); `"*"`
(or `""`) means every request. The global `app.security(opts)` stays the default.
For a request the **effective** policy is the global merged with every matching
scope, **shortest prefix first** (longest wins) — the merge law:

- **scalars** (all keys except the lists): **deepest wins** — an undeclared key
  is inherited, never reset to its default;
- **lists** (`publicPaths`, `roles`): **union** — allow-lists only grow, the
  deepest scope adds, never removes.

```kof
app.security(mapOf("rateLimit", "200/60"))
app.policy("/admin", mapOf("roles", "admin"))       // 20/60 + admin
app.policy("*", mapOf("publicPaths", "/health"))    // public regardless of scope
```

Prefix-only in v1 (no globs/regex, no path params). Same JVM-only rule as
`app.security`: Native/JS report `WEB006`.

**Endpoint policy (F3):** a route may carry its own opts as the second argument
(`app.get(path, opts) { … }`, same opt keys). It is the **deepest** scope, so it
wins over the resource policy for that exact route; a route without opts inherits
the resource/global policy. It only applies after the route matches — an unknown
path is still protected by the path scopes/global.

```kof
app.policy("/api", mapOf("headers", false))
app.get("/api/show", mapOf("headers", true)) { return "show" }  // hardening back on
app.get("/api/hide") { return "hide" }                          // inherits off
```

**JVM-only** — Native/JS report `WEB006` (honest gap, same precedent
`WEB002`/`WEB005`).

### Server

| Call | Description |
|---------|-----------|
| `app.listen(port)` | Starts the server (blocking) on `0.0.0.0` |
| `app.listenSecure(port)` | Same, with dev self-signed TLS (JVM; `keytool` + `SSLServerSocket`) |
| `app.listenSecure(port, certPem, keyPem)` | TLS with a **user-supplied certificate** (PKCS#8 PEM) — production (JVM) |
| `app.port()` | Port actually bound (useful with `listen(0)`) |
| `app.close()` | Shuts down the server (graceful shutdown) |

`app.listen(0)` binds an ephemeral port; `app.port()` reveals the real port.
`app.listenSecure` is available on the JVM (Native/JS `WEB002`). The 3-arg
variant uses the user's cert/key pair (`-----BEGIN CERTIFICATE-----` /
`-----BEGIN PRIVATE KEY-----`, PKCS#8 RSA/EC/DSA key); the 1-arg self-signed
stays a dev convenience, not for production (D-SEC).

### Static files (`app.serveDir`) (31/08)

| Call | Description |
|---------|-----------|
| `app.serveDir(prefix, dir)` | Serves the files from `dir` under `prefix` (fallback after dynamic routes) |

The handler returns the **binary file** from disk with `Content-Type` by
extension (HTML/CSS/JS, images, audio, **video**, fonts, PDF...),
`Cache-Control` and protection against path traversal (`..`). It is the
alternative to pasting base64/HTML/CSS into a `String` literal in the source —
the app handles the FILE.

**Range requests**: `serveDir` answers `Range: bytes=...` with `206 Partial
Content` + `Content-Range` + `Accept-Ranges: bytes` (and `416` for an invalid
range). This is what allows `<video>`/`<audio>` to navigate and seek in the
browser — without Range, the player cannot position in the middle of the file.

```kof
var app = web.app()
app.serveDir("/media", "assets")   // GET /media/clip.mp4 → bytes + Range 206
app.listen(8080)
```

```html
<video src="/media/clip.mp4" controls></video>
```

Relative paths of the app resolve against the project root
(`-Dkof.root`, set by the `run`/`serve` CLI). **JVM-only** — Native/JS
report `WEB005` (documented gap).

### Health (`app.health`) (01/09)

| Call | Description |
|---------|-----------|
| `app.health(path)` | Registers a built-in health endpoint (e.g.: `/health`) |

`app.health("/health")` responds with the app state in JSON
(`{"status":"UP","ready":true,"alive":true}` — value of
`observability.health()/readiness()/liveness()`) **before the middlewares**:
load balancer/health-check probes do not go through auth/middleware. The app
can also mount its own: `app.get("/health") { return
observability.health() }`.

```kof
var app = web.app()
app.health("/health")   // GET /health → {"status":"UP","ready":true,"alive":true}
app.listen(8080)
```

### WebSocket (`app.ws`, RFC 6455) (30/08)

| Call | Description |
|---------|-----------|
| `app.ws(path) { ... }` | WebSocket route (route kind `WS`) |
| `wsMessage()` | Text of the `TEXT` message that triggered the handler (String) |
| `wsSend(text)` | Sends a `TEXT` frame back over the current connection |

The RFC 6455 handshake and the frame codec (with client→server masking) are
implemented inside the generated HTTP engine; the Kof handler is called per
`TEXT` message. The runtime also handles `PING`→`PONG`, `CLOSE` (ack) and
discards frames above the configurable frame limit (default 1 MiB, close
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

| Call | Description |
|---------|-----------|
| `app.sse(path) { ... }` | SSE route (route kind `SSE`); the handler receives the sender as the `sse` parameter |
| `sse.send(data)` | Unnamed event (`data: ...`) |
| `sse.event(name, data)` | Named event (`event: name\ndata: ...`) |
| `sse.close()` | Ends the client stream |
| `sse.isOpen()` | `Bool` — the stream is still open |

On the **JS** host (Graal) SSE is **handler-scoped**: events are written during
the handler body and the stream closes when the handler returns — the pump is
single-thread, so post-return push and multiple concurrent clients are the
`WEB003` residual there. On the JVM each SSE connection is independent
(ThreadLocal per connection). In both, the headers
`Content-Type: text/event-stream`, `Cache-Control: no-cache`,
`Connection: keep-alive` and `X-Accel-Buffering: no` are emitted.

```kof
app.sse("/events") {
    sse.send("one")
    sse.event("tick", "two")
    sse.close()
}
```

`app.ws` is available on the **JVM**; `app.sse` works on the JVM and (since
16/09, handler-scoped) on **JS**. On other targets / the JS residual they are
compile-time documented gaps: WebSocket → `WEB004`, SSE → `WEB003` (Native).

### Limits and observability (`app.configure`, `app.stats`)

| Call | Description |
|---------|-----------|
| `app.configure("maxConnections", n)` | Cap on concurrent connections (default `1024`); above that it responds `503` |
| `app.configure("maxFrameBytes", n)` | Mutable WebSocket frame limit (default `1 MiB`) |
| `app.configure("maxMessageBytes", n)` | Mutable WebSocket message limit (default `8 MiB`) |
| `app.configure("idleMs", n)` | Idle timeout applied to WebSocket and SSE deadline |
| `stats("SSE_CONNECTIONS_ACTIVE")` | Active SSE connections |
| `stats("WS_CONNECTIONS_ACTIVE")` | Active WebSocket connections |
| `stats("SSE_EVENTS_SENT")` | SSE events sent |
| `stats("WS_MESSAGES_RECEIVED")` / `stats("WS_MESSAGES_SENT")` | WS messages received/sent |

`app.configure` acts on the current app (per handle); the statistics are
global per JVM and returned as `String`.

### Request context (inside handlers/middleware)

| Function | Returns |
|--------|---------|
| `param("id")` | Path parameter (`String` — only a matched route reaches the handler) |
| `query("name")` | Query parameter (`String?` — `null` if absent; narrow before deref) |
| `header("x-auth")` | Case-insensitive header (`String?` — `null` if absent; narrow before deref) |
| `body()` | Raw request body |
| `method()` | HTTP method ("GET", "POST", ...) |
| `path()` | Request path |
| `status(code, body)` | Sets the response status and returns the body — use as a return (e.g.: `return status(201, "{\"ok\":true}")`) |
| `headerSet(name, value)` | Adds a response header (e.g.: `headerSet("X-Total", "42")`) |

The context is per-request (ThreadLocal at runtime) — handlers can be
concurrent without shared state. `status(code, body)` and
`headerSet(name, value)` allow rich responses (custom status + headers) —
previously handlers only produced automatic 200/404.

### Pagination helper (`import kof.web`)

`pageRequest(defaultLimit[, maxLimit]): PageRequest` (`D-PAGINATION` P5) reads
`?page/limit/offset` from the current request and returns the core record
`PageRequest(page, limit, offset)` — no HTTP type leaks into the handler. `page`
is 1-based (`offset = (page-1)*limit`); `limit` defaults to `defaultLimit` and is
clamped to `maxLimit` when `maxLimit > 0`; an explicit `?offset=` wins. Bad input
(non-integer, `page < 1`, `limit < 1`, `offset < 0`) throws a named
`PAGINATION:` error — map it to 400 in the handler:

```kof
import kof.web

app.get("/users") {
    try {
        val p = pageRequest(20, 100)
        return json.encode(orm.window<User>(db, p.limit(), p.offset()))
    } catch (String e) {
        return status(400, e)
    }
}
```

Native targets have no web context, so a `pageRequest` call there is a `WEB001`
gap (declared, never silent).

## 4. Concurrency

Each connection is handled on a virtual thread (JVM). The programmer writes
synchronous handlers; the runtime decides the strategy. SSE handlers run on
the shared `KOF_SSE_HANDLERS` and have a deadline of `idleMs * 4`; on timeout
the stream is closed and the task cancelled.

## 5. Current limitations (Phase 1, 0.5.0-beta)

- The `js` target supports the web stack base (`web.app()` + routes + context-fns with runtime: param/query/header/body/method/path/status/headerSet — `WEB001` fatia honestidade 16/09) plus **SSE handler-scoped** (`app.sse` + `sse.send/event/close/isOpen` + `sse()`, framing/headers same as the JVM — 16/09); residual gaps: SSE push after the handler returns and multiple concurrent clients (`WEB003`), `app.ws` (`WEB004`) and `stats()` report at compile-time; `kof.http` already works on JS via `Java HttpClient`.
- The `native` target (`x86_64`/`riscv64`/`aarch64`) has had the web server base since 03/09 (`NativeWebRuntime.java`: accept/route/lambda/body, `KofWebNativeE2ETest` 4/4); residual: TLS `WEB002`, ws `WEB004`, sse `WEB003`, path params/keep-alive/`status()`/`headerSet()` `WEB001`.
- `app.ws` is JVM-only (Native/JS → `WEB004` at compile-time). `app.sse` is JVM + JS handler-scoped (16/09; Native `WEB003`, JS post-return push `WEB003` residual).
- `app.serveDir` (static files + Range 206/416) is JVM-only (Native/JS `WEB005`).
- PR6 hardening (connection cap, `maxFrameBytes`/`maxMessageBytes` limits,
  `idleMs`, `app.stats`) is JVM; backpressure and fragmentation remain follow-up.
- `kof.http` client — ✅ JVM+JS (27/08; `timeout/retry/circuit` in parity 30/08), Native ✅ (asm HTTP/1.1; configurators `timeout/retry/circuit` are REAL on the 4 native targets since 17/09 — §259 CLOSED; the `HTTP002` branch stays dead because `KofHttp.supportedOn` always returns `true`).
- Middleware/routes for HTTP methods other than those listed: in the future.

> Closed in this phase (27–30/08): status codes + custom headers
> (`status(code, body)` / `headerSet(name, value)`); `kof.cache` on the 3
> targets; `WebSocket` (`app.ws`) + `SSE` (`app.sse`) on the JVM; `http.retry`/
> `http.circuit` in JVM+JS parity.

## 6. Tests (0.5.0-beta)

`KofWebE2ETest` 10 + `KofHttpServerTest` 8 + `KofHttpE2ETest` 4 (JVM+JS,
27/08) + `KofWebTlsTest` 5 + `KofWebSseE2ETest` 7 + `KofWebWsE2ETest` 11 +
`KofWebStreamE2ETest` 4 + `KofWsFrameTest` 7 + `KofHttpResilienceE2ETest` 3 +
`KofWebHardeningTest` 6 —
each test compiles a Kof program, runs the bytecode/JS as a subprocess and
exercises the server/client with real sockets (routing, path params, query,
headers, body, JSON round-trip, middleware, 404, multiple routes with trailing
lambda, `http.get/post/put/delete` + TLS + `retry`/`circuit`, RFC 6455
WebSocket handshake, frame codec with masking, named/multi-line SSE events,
concurrent WS/SSE streaming).

## 7. Architecture

```
Kof source (.kf)
   ↓ CompilerDriver
Kof IR (KofCall kof_web_*)
   ↓ JvmBackend
JVM bytecode
   ↓
dev.kof.runtime.KofRuntime (generated)  ← HTTP engine embedded in the program
   ├── KOF_WEB_APPS (app registry)
   ├── WebRoute (method, segments, params, handler, kind)
   ├── SseConnection / WsConnection / WsFrame
   ├── WebRequest (method, path, query, headers, body)
   └── accept loop (virtual threads) + dispatch
```

The `kof_web_*` calls are resolved at compile-time by the `KofWeb` table
(a dance analogous to `KofIo`): the programmer never sees threads, sockets or
HTTP parsing.

## 8. References

- Plan: `docs/development/DECISIONS.md` §D-SPRING (Phase 1)
- Status: `docs/status.md`
- Roadmap: `docs/development/roadmap.md` (Phase 3 — Web Platform)
