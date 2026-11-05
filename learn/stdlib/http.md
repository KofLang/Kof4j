[English](http.md) | [Português](http.pt_BR.md)

# kof.http — HTTP client as a call

> **Status: stable — JVM + JS (same source, byte-parity); Native has its own
> core (HTTP002 gap code on the pieces still absent).**

| Function | Form |
|----------|------|
| `get` | `get(String url) -> String` · `get(url, headers...) -> String` |
| `post` | `post(String url, String body) -> String` · `post(url, body, headers...) -> String` |
| `put` | `put(String url, String body) -> String` · variants with headers |
| `delete` | `delete(String url) -> String` · variants with headers |
| `patch` | `patch(String url, String body) -> String` · variants with headers |
| `options` | `options(String url) -> String` · variants with headers |
| `status` | `status(String url) -> Int` |
| `send` | `send(String method, String url) -> Response` · `send(method, url, String? body, headers...) -> Response` |
| `timeout` | `timeout(Int seconds) -> void` |
| `retry` | `retry(Int count) -> void` |
| `circuit` | `circuit(Int threshold) -> void` |

```kf
main() {
    http.timeout(5)
    var body = http.get("https://api.example.com/tasks")
    var t = json.decode<Task>(body)
    http.post("https://api.example.com/tasks", json.encode(t), "Content-Type: application/json")
}
```

- Verbs take headers as varargs (`"Name: value"` strings).
- `get`/`post`/... return the body and throw on a 5xx; when the status or the
  response headers matter, use `send`.
- `timeout`/`retry`/`circuit` are PROCESS-WIDE policies — set once, every call
  obeys (intention, not per-call ceremony).
- `timeout` is in seconds.
- JSON payloads compose with [kof.json](json.md); the real-world walkthrough
  lives in [26 — Real World Application](../26-real-world-application.md).

## `send` and `Response`

`http.send(method, url[, body][, headers...])` makes any request and returns a
`Response`. A 4xx or 5xx is a response, not an error; only a transport failure
(connection refused, timeout, TLS) throws, as a `String` you can catch.

| Method | Returns |
|--------|---------|
| `status()` | `Int` |
| `body()` | `String` (empty when the response has none) |
| `header(String name)` | `String?`, the name is case-insensitive, `null` when absent |
| `headers()` | `Map<String, String>`, lower-case names, repeated values joined by `", "` |

```kf
main() {
    val r = http.send("PUT", "https://api.example.com/tasks/7", json.encode(task),
        "Content-Type: application/json")
    if (r.status() == 404) {
        println("no task 7")
    } else {
        println(r.header("ETag"))
        println(r.body())
    }
    val gone: Response = http.send("DELETE", "https://api.example.com/tasks/7")
    println(gone.status())
}
```

- The body may be omitted or `null` (`send("GET", url)`).
- `Response` is a type name you can use for a variable, parameter or return.
- `send` follows `timeout` and `circuit` (a 5xx counts against the circuit, but is
  still returned); it does not `retry`, since a 5xx is an answer the caller reads.
- Targets: JVM, Script and JS (on Node/browser, as `get`, the call returns a value
  to `await`). Native and Wasm refuse `send` at compile time with `HTTP002`; on Native,
  `get`/`post`/`status` remain.
