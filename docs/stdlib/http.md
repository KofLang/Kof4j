[English](http.md) | [Português](http.pt_BR.md)

# Web Architecture — `kof serve`

**Date:** September 2, 2026
**Re-synced:** September 17, 2026 (0.5.0-beta)

> **Updated (0.2.6-beta):** `kof serve` with top-level handlers + native web stack `web.app()` (Spring independence Phase 1) — routes with trailing lambda, path params, query, headers, body, middleware, typed JSON, custom status/headers and HTTP server generated in the runtime; `kof.http` client `http.get/post/put/delete/patch/options/status` + `timeout/retry/circuit` works on **JVM + JS + Native** (JS via `Java HttpClient` interop in `KofJsRunner`; Native = asm HTTP/1.1, https + real DNS still gaps; retry/circuit at 3-target parity, native knobs real on the 4 targets since 17/09 — §259); TLS `listenSecure` JVM. See [docs/stdlib/stdlib-web.md](stdlib-web.md) and `docs/status.md` (current suite count).

**Status:** Implemented (Phase H) — `VERSION` 0.5.0-beta
**Version:** 0.5.0-beta

---

## 1. Philosophy

> The complexity of creating a web application must be solved by the language, compiler and runtime — not by frameworks.

Kof is not another Spring. Kof is a language where creating an HTTP API must be as simple as writing a function.

```kof
// Conceptual — not yet implemented
route GET "/users/{id}" {
    return users.find(id)
}
```

The guiding question is: **"Does the programmer really need to write this?"**

If the answer is no, the language must solve it.

---

## 2. General Architecture

```
Kof Source (.kf)
       │
       ▼
  Kof Compiler
       │
       ▼
    Kof IR
       │
  ┌────┴────┐
  ▼         ▼
 JVM      Native
  │         │
  ▼         ▼
JVM GC   Kof Runtime
              │
              ▼
         Socket Layer
              │
              ▼
          HTTP Layer
              │
              ▼
         Application
```

### Layers

| Layer | Responsibility | Backend |
|--------|-----------------|---------|
| **Language** | Syntax, types, semantics | Common |
| **Compiler** | Analysis, IR, codegen | Common |
| **Runtime** | Memory, strings, arrays | Backend-specific |
| **Net Layer** | Sockets, I/O | Backend-specific |
| **HTTP Layer** | Request/Response parsing | Common (uses Net Layer) |
| **App Layer** | Routing, handlers | Common (uses HTTP Layer) |

---

## 3. What belongs to each layer

### Language
- Route syntax (future)
- Handler declaration
- Request/response types

### Compiler
- Route parsing (when implemented)
- Signature validation
- IR generation for dispatch

### Runtime (Native)
- Socket syscalls (bind, listen, accept, read, write)
- Event loop / accept loop
- Buffer management
- HTTP parsing
- Memory management

### Runtime (JVM)
- Java NIO / Netty equivalent
- Virtual threads for concurrency
- HTTP parsing

### Standard Library
- HTTP model (Request, Response)
- JSON serialization
- Routing API
- Middleware API

### CLI
- `kof serve` command
- `--port`, `--host` flags
- Watch mode (future)

---

## 4. `kof serve` — Behavior

### Syntax

```bash
kof serve <file.kf> [--port <port>] [--host <host>]
```

### Flags

| Flag | Default | Description |
|------|---------|-----------|
| `--port` | 8080 | Server port — **legacy mode only** (`handle(...)`). In a kof-native app (`web.app()` + `app.listen`), the port belongs to the app; the CLI warns that `--port` is ignored (#35.3, R6) |
| `--host` | 0.0.0.0 | Bind address — same: legacy mode only |

### Behavior

1. Compiles the `.kf` file
2. **Legacy mode** (`handle(...)` function): starts an HTTP server on the `--port` port;
   each request calls the handler.
3. **kof-native mode** (`web.app()` + `app.listen(port)`): the **app** starts and
   listens on the port that **it** defines; the CLI only compiles and runs, and the banner
   reports the app's real port (or warns that `--port` was ignored).

### Operation mode

```bash
# Development (JVM)
kof serve app.kf --port 8080

# Production (Native)
kof serve app.kf --port 8080   # serve compiles to JVM
```

---

## 5. HTTP Model

### Request

```kof
// Conceptual
request.method     // "GET", "POST", etc.
request.path       // "/users/123"
request.headers    // map of headers
request.body       // raw body bytes
request.query      // query parameters
```

### Response

```kof
// Conceptual
response.status(200)
response.header("Content-Type", "application/json")
response.body(jsonString)
```

### Handler

```kof
// Conceptual — minimal form
handle(request: Request): Response {
    return response(200, "Hello, World!")
}
```

---

## 6. Routing

### Route model

```kof
// Conceptual
route GET "/users" { ... }
route POST "/users" { ... }
route GET "/users/{id}" { ... }
route DELETE "/users/{id}" { ... }
```

### Path parameters

```kof
route GET "/users/{id}" {
    var id = param("id")  // String
    // ...
}
```

### Compile-time validation

```kof
// Error if two routes have the same path+method
route GET "/users" { ... }
route GET "/users" { ... }  // ERROR: duplicate route
```

---

## 7. JSON

### Serialization

```kof
// Conceptual
var user = User("Mel", 26)
var json = encode(user)
// → {"name":"Mel","age":26}
```

### Deserialization

```kof
// Conceptual
var user = decode<User>(request.body)
```

### Schema generation

The compiler can generate JSON schemas from records/classes:

```kof
record User(String name, Int age)
// → generates JSON schema automatically
```

---

## 8. Middleware

### Model

Middleware as function composition:

```kof
// Conceptual
auth(handler: Handler): Handler {
    return (req: Request) -> Response {
        if (!req.headers.has("Authorization")) {
            return response(401, "Unauthorized")
        }
        return handler(req)
    }
}
```

### Usage

```kof
route GET "/admin" with auth {
    // handler
}
```

---

## 9. Concurrency

### JVM

- Virtual threads (Java 21+)
- Each request on a virtual thread
- Structured concurrency for parallelism

### Native

- Thread pool with worker threads
- or event loop (future)

### Common abstraction

```kof
// Conceptual — the programmer does not write this
// The runtime decides the strategy
```

The programmer writes synchronous handlers. The runtime executes them on asynchronous threads.

---

## 10. Security

### Layer 1 — Runtime

- Request size limits
- Header limits
- Timeout (read, write, connection)
- Path normalization

### Layer 2 — Standard Library

- CORS
- Rate limiting
- Authentication/Authorization hooks

### Layer 3 — Application

- Input validation
- Sanitization

---

## 10.1 TLS/HTTPS (G12)

```kof
var app = web.app()
app.get("/hello") { return "Hello TLS" }
app.listenSecure(8443) // JVM: generates self-signed via keytool (SAN=IP:127.0.0.1,DNS:localhost), SSLServerSocket
```

- **Server:** `app.listenSecure(port)` — `KofWeb.java:142` `kof_web_listen_secure` → `JvmRuntimeWebServer.java:50` `SSLServerSocketFactory` + `keytool -genkeypair` (JKS, `SAN=IP:127.0.0.1,DNS:localhost`); Native/JS report `WEB002`.
- **Client:** `kof.http.get("https://...")` — `JvmWebHttpRuntime.java:112` `KOF_HTTP_CLIENT_INSECURE` (`SSLContext` trust-all + `SSLParameters` without `endpointIdentification`, `HttpClient` with insecure `sslContext`) — needed for self-signed in tests.
- **Test:** `KofWebTlsTest.java:12` 5 tests (hello, headers, `http` over TLS, Native/JS gaps `WEB002`).

---

## 10.2 `kof.http` client — resilience (timeout/retry/circuit) (G2, 30/08)

The `kof.http` client (JVM + JS) gains three global resilience functions that
act on **all** subsequent `http.*` calls:

```kof
http.timeout(30)      // timeout per request, in seconds (default 15)
http.retry(2)         // retries the request on exception AND on HTTP 5xx (default 0)
http.circuit(3)       // circuit opens after 3 failures (default 0 = no circuit)
http.circuit(0)       // turns off the circuit and resets the failure state
```

- **`timeout(s)`** — applies `Duration.ofSeconds(s)` to each request
  (`JvmWebHttpRuntime.kof_http_timeout_set`). Default: 15 s.
- **`retry(n)`** — `n` extra attempts; retries the request when it throws
  an exception (connection refused, timeout) **or** when the HTTP status is `>= 500`
  (`JvmWebHttpRuntime.kof_http_retry_set`). Default: 0. `retry(0)` turns it off.
- **`circuit(trips)`** — opens the circuit after `trips` consecutive failures
  (exception or HTTP `>= 500`); while open, requests fail immediately
  (fail-fast) with `IOException("kof.http circuit open (fail fast): <url>")`
  for 30 s (`KOF_HTTP_CIRCUIT_WINDOW_MS`). `circuit(0)` turns it off and resets
  counter/failure. Default: 0 (off).

JVM+JS parity is exercised by `KofHttpResilienceE2ETest` (3/3): retry
recovers on a flaky endpoint (2×500 → 200), the circuit opens after a failure and
fail-fast, and `circuit(0)` recovers. Native implements the three knobs for real since 17/09 (§259 CLOSED, the 4 native targets): non-blocking connect + `poll` deadline + `SO_RCVTIMEO`/`SO_SNDTIMEO` (`throw "kof.http: timeout"`), retry on exception/`>=500`, circuit fail-fast 30s half-open — messages identical to the JVM (`KofHttpNativeResilienceCrossTest` 4/4 under qemu). `HTTP002` is the only HTTP gap code defined — it is **not emitted today** (the branch is dead because `KofHttp.supportedOn` always returns `true`; `KofHttp.gapCode()` has no callers, §259), reserved for a genuinely unsupported native HTTP target.

---

## 11. Observability

### Logging

```kof
// Conceptual
log("Request received")
log("Response sent", level=INFO)
```

### Metrics

```kof
// Conceptual — collected automatically
// request_count, latency, error_rate
```

### Tracing

```kof
// Conceptual — request ID propagated automatically
```

---

## 12. CLI

### Commands

| Command | Description |
|---------|-----------|
| `kof serve` | Starts the HTTP server |
| `kof serve --port 8080` | Sets the port |
| `kof serve --host 0.0.0.0` | Sets the address |

### Flags

| Flag | Default | Description |
|------|---------|-----------|
| `--port` | 8080 | Port |
| `--host` | 0.0.0.0 | Address |

---

## 13. Native Backend

### Required syscalls

| Syscall | Number | Purpose |
|---------|--------|-----------|
| `socket` | 41 | Create socket |
| `bind` | 49 | Bind to address |
| `listen` | 50 | Listen for connections |
| `accept` | 43 | Accept connection |
| `read` | 0 | Read data |
| `write` | 1 | Send data |
| `close` | 3 | Close socket |

### Runtime functions

| Function | Purpose |
|--------|-----------|
| `kof_net_socket(domain, type, protocol)` | Create socket |
| `kof_plat_net_bind(fd, addr, len)` | Bind |
| `kof_plat_net_listen(fd, backlog)` | Listen |
| `kof_plat_net_accept(fd, addr, lenp)` | Accept |
| `kof_net_read(fd, buf, len)` | Read |
| `kof_net_write(fd, buf, len)` | Write |
| `kof_plat_close(fd)` | Close fd |

---

## 14. JVM Backend

### Implementation

- Uses Java NIO or standard sockets
- Virtual threads for concurrency
- `java.net.ServerSocket` for bind/listen/accept
- `java.io.InputStream/OutputStream` for read/write

---

## 15. Extensibility

### Common API

```kof
// Conceptual
interface HttpServer {
    start(port: Int)
    stop()
    route(method: String, path: String, handler: Handler)
}
```

### Backend-specific

Each backend may have specific implementations if needed, but the basic API must be common.

---

## 16. Architectural Risks

1. **Hot reload** — implementing it without breaking the language semantics
2. **Graceful shutdown** — how does the process terminate?
3. **State management** — how to handle state between requests?
4. **Error handling** — how do runtime errors affect the server?
5. **Memory leaks** — how does the GC handle request/response objects?

---

## 17. Next Steps

1. Implement network syscalls in NativeRuntime
2. Add `serve` to the CLI
3. Implement a minimal HTTP server (single-threaded)
4. Implement request/response parsing
5. Connect with the handler function of the Kof program
6. Add E2E tests
7. Document
