# Web Architecture — `kof serve`

**Data:** 2 de setembro de 2026
> **Atualizado (0.2.6-beta):** `kof serve` com handlers top-level + stack web nativa `web.app()` (Fase 1 Spring independence) — rotas com lambda trailing, path params, query, headers, body, middleware, JSON tipado, status/headers customizados e servidor HTTP gerado no runtime; `kof.http` client `http.get/post/put/delete/patch/options/status` + `timeout/retry/circuit` funciona em **JVM + JS** (JS via `Java HttpClient` interop no `KofJsRunner`; retry/circuit em paridade JVM+JS, 30/08) — Native `HTTP002`; TLS `listenSecure` JVM. Ver [docs/stdlib-web.md](stdlib-web.md) e `docs/status.md:10-28` (810 testes, golden 16/16, integration 9/9).

**Status:** Implementado (Fase H) — 0.2.6-beta `VERSION` 0.2.6-beta
**Versão:** 0.2.6-beta

---

## 1. Filosofia

> A complexidade de criar uma aplicação web deve ser resolvida pela linguagem, compilador e runtime — não por frameworks.

Kof não é outro Spring. Kof é uma linguagem onde criar uma API HTTP deve ser tão simples quanto escrever uma função.

```kof
// Conceitual — ainda não implementado
route GET "/users/{id}" {
    return users.find(id)
}
```

A pergunta guia é: **"O programador realmente precisa escrever isso?"**

Se a resposta for não, a linguagem deve resolver.

---

## 2. Arquitetura Geral

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

### Camadas

| Camada | Responsabilidade | Backend |
|--------|-----------------|---------|
| **Language** | Sintaxe, tipos, semântica | Comum |
| **Compiler** | Análise, IR, codegen | Comum |
| **Runtime** | Memory, strings, arrays | Específico por backend |
| **Net Layer** | Sockets, I/O | Específico por backend |
| **HTTP Layer** | Request/Response parsing | Comum (usa Net Layer) |
| **App Layer** | Routing, handlers | Comum (usa HTTP Layer) |

---

## 3. O que pertence a cada camada

### Linguagem
- Sintaxe de rotas (futuro)
- Declaração de handlers
- Tipos de request/response

### Compiler
- Parsing de rotas (quando implementado)
- Validação de assinaturas
- Geração de IR para dispatch

### Runtime (Native)
- Socket syscalls (bind, listen, accept, read, write)
- Event loop / accept loop
- Buffer management
- HTTP parsing
- Memory management

### Runtime (JVM)
- Java NIO / Netty equivalent
- Virtual threads para concurrency
- HTTP parsing

### Standard Library
- HTTP model (Request, Response)
- JSON serialization
- Routing API
- Middleware API

### CLI
- `kof serve` command
- `--port`, `--host` flags
- Watch mode (futuro)

---

## 4. `kof serve` — Comportamento

### Sintaxe

```bash
kof serve <file.kf> [--port <port>] [--host <host>]
```

### Flags

| Flag | Default | Descrição |
|------|---------|-----------|
| `--port` | 8080 | Porta do servidor — **só no modo legacy** (`handle(...)`). Em app kof-native (`web.app()` + `app.listen`), a porta é do app; a CLI avisa que `--port` é ignorado (#35.3, R6) |
| `--host` | 0.0.0.0 | Endereço de bind — idem: só modo legacy |

### Comportamento

1. Compila o arquivo `.kf`
2. **Modo legacy** (função `handle(...)`): inicia servidor HTTP na porta `--port`;
   cada request chama o handler.
3. **Modo kof-native** (`web.app()` + `app.listen(port)`): o **app** sobe e
   escuta na porta que **ele** define; a CLI só compila e executa, e o banner
   reporta a porta real do app (ou avisa que `--port` foi ignorado).

### Modo de operação

```bash
# Desenvolvimento (JVM)
kof serve app.kf --port 8080

# Produção (Native)
kof serve app.kf --port 8080   # serve compila para JVM
```

---

## 5. HTTP Model

### Request

```kof
// Conceitual
request.method     // "GET", "POST", etc.
request.path       // "/users/123"
request.headers    // map of headers
request.body       // raw body bytes
request.query      // query parameters
```

### Response

```kof
// Conceitual
response.status(200)
response.header("Content-Type", "application/json")
response.body(jsonString)
```

### Handler

```kof
// Conceitual — forma mínima
handle(request: Request): Response {
    return response(200, "Hello, World!")
}
```

---

## 6. Routing

### Modelo de rotas

```kof
// Conceitual
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

### Validação em compile-time

```kof
// Erro se dois routes têm o mesmo path+method
route GET "/users" { ... }
route GET "/users" { ... }  // ERRO: rota duplicada
```

---

## 7. JSON

### Serialização

```kof
// Conceitual
var user = User("Mel", 26)
var json = encode(user)
// → {"name":"Mel","age":26}
```

### Deserialização

```kof
// Conceitual
var user = decode<User>(request.body)
```

### Schema generation

O compiler pode gerar JSON schemas a partir de records/classes:

```kof
record User(String name, Int age)
// → gera JSON schema automaticamente
```

---

## 8. Middleware

### Modelo

Middleware como composição de funções:

```kof
// Conceitual
auth(handler: Handler): Handler {
    return (req: Request) -> Response {
        if (!req.headers.has("Authorization")) {
            return response(401, "Unauthorized")
        }
        return handler(req)
    }
}
```

### Uso

```kof
route GET "/admin" with auth {
    // handler
}
```

---

## 9. Concorrência

### JVM

- Virtual threads (Java 21+)
- Cada request em uma virtual thread
- Structured concurrency para parallelismo

### Native

- Thread pool com worker threads
- ou event loop (futuro)

### Abstração comum

```kof
// Conceitual — o programador não escreve isso
// O runtime decide a estratégia
```

O programador escreve handlers síncronos. O runtime executa em threads assíncronas.

---

## 10. Segurança

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

- Validação de input
- Sanitização

---

## 10.1 TLS/HTTPS (G12)

```kof
var app = web.app()
app.get("/hello") { return "Hello TLS" }
app.listenSecure(8443) // JVM: gera self-signed via keytool (SAN=IP:127.0.0.1,DNS:localhost), SSLServerSocket
```

- **Server:** `app.listenSecure(port)` — `KofWeb.java:84` `kof_web_listen_secure` → `JvmRuntime.java:370` `SSLServerSocket` + `keytool -genkeypair` (JKS, `SAN=IP:127.0.0.1,DNS:localhost`); Native/JS reportam `WEB002`.
- **Client:** `kof.http.get("https://...")` — `JvmWebRuntime.java:238` `KOF_HTTP_CLIENT_INSECURE` (`SSLContext` trust-all + `SSLParameters` sem `endpointIdentification`, `HttpClient` com `sslContext` insecure) — necessário para self-signed em testes.
- **Teste:** `KofWebTlsTest.java:12` 5 testes (hello, headers, `http` over TLS, gaps Native/JS `WEB002`/`WEB001`).

---

## 10.2 `kof.http` client — resiliência (timeout/retry/circuit) (G2, 30/08)

O client `kof.http` (JVM + JS) ganha três funções globais de resiliência que
atuam sobre **todas** as chamadas `http.*` subsequentes:

```kof
http.timeout(30)      // timeout por request, em segundos (default 15)
http.retry(2)         // repete a request em exceção E em HTTP 5xx (default 0)
http.circuit(3)       // circuito abre após 3 falhas (default 0 = sem circuito)
http.circuit(0)       // desliga o circuito e zera o estado de falhas
```

- **`timeout(s)`** — aplica `Duration.ofSeconds(s)` a cada request
  (`JvmWebRuntime.kof_http_timeout_set`). Default: 15 s.
- **`retry(n)`** — `n` tentativas extras; repete a request quando lança
  exceção (connexão recusada, timeout) **ou** quando o status HTTP é `>= 500`
  (`JvmWebRuntime.kof_http_retry_set`). Default: 0. `retry(0)` desliga.
- **`circuit(trips)`** — abre o circuito após `trips` falhas consecutivas
  (exceção ou HTTP `>= 500`); enquanto aberto, as requests falham na hora
  (fail-fast) com `IOException("kof.http circuit open (fail fast): <url>")`
  por 30 s (`KOF_HTTP_CIRCUIT_WINDOW_MS`). `circuit(0)` desliga e zera
  contador/falha. Default: 0 (desligado).

A paridade JVM+JS é exercida por `KofHttpResilienceE2ETest` (3/3): retry
recupera num endpoint flaky (2×500 → 200), circuito abre após falha e
fail-fast, e `circuit(0)` recupera. Native reporta `HTTP002`.

---

## 11. Observabilidade

### Logging

```kof
// Conceitual
log("Request received")
log("Response sent", level=INFO)
```

### Metrics

```kof
// Conceitual — coletado automaticamente
// request_count, latency, error_rate
```

### Tracing

```kof
// Conceitual — request ID propagado automaticamente
```

---

## 12. CLI

### Comandos

| Comando | Descrição |
|---------|-----------|
| `kof serve` | Inicia servidor HTTP |
| `kof serve --port 8080` | Define porta |
| `kof serve --host 0.0.0.0` | Define endereço |

### Flags

| Flag | Default | Descrição |
|------|---------|-----------|
| `--port` | 8080 | Porta |
| `--host` | 0.0.0.0 | Endereço |

---

## 13. Native Backend

### Syscalls necessários

| Syscall | Número | Propósito |
|---------|--------|-----------|
| `socket` | 41 | Criar socket |
| `bind` | 49 | Bind em endereço |
| `listen` | 50 | Escutar conexões |
| `accept` | 43 | Aceitar conexão |
| `read` | 0 | Ler dados |
| `write` | 1 | Enviar dados |
| `close` | 3 | Fechar socket |

### Runtime functions

| Função | Propósito |
|--------|-----------|
| `kof_net_socket(domain, type, protocol)` | Criar socket |
| `kof_net_bind(fd, port, addr)` | Bind |
| `kof_net_listen(fd, backlog)` | Listen |
| `kof_net_accept(fd)` | Accept |
| `kof_net_read(fd, buf, len)` | Read |
| `kof_net_write(fd, buf, len)` | Write |
| `kof_net_close(fd)` | Close |

---

## 14. JVM Backend

### Implementação

- Usa Java NIO ou sockets padrão
- Virtual threads para concorrência
- `java.net.ServerSocket` para bind/listen/accept
- `java.io.InputStream/OutputStream` para read/write

---

## 15. Extensibilidade

### API comum

```kof
// Conceitual
interface HttpServer {
    start(port: Int)
    stop()
    route(method: String, path: String, handler: Handler)
}
```

### Backend-specific

Cada backend pode ter implementações específicas se necessário, mas a API básica deve ser comum.

---

## 16. Riscos Arquiteturais

1. **Hot reload** — implementar sem quebrar a semântica da linguagem
2. **Graceful shutdown** — como o processo termina?
3. **State management** — como lidar com estado entre requests?
4. **Error handling** — como erros de runtime afetam o servidor?
5. **Memory leaks** — como o GC lida com objetos de request/response?

---

## 17. Próximos Passos

1. Implementar syscalls de rede no NativeRuntime
2. Adicionar `serve` ao CLI
3. Implementar HTTP server mínimo (single-threaded)
4. Implementar request/response parsing
5. Conectar com função handler do programa Kof
6. Adicionar testes E2E
7. Documentar
