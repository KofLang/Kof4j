[English](http.md) | [Português](http.pt_BR.md)

# kof.http — cliente HTTP como uma chamada

> **Status: estável — JVM + JS (mesmo fonte, paridade byte a byte); Native tem
> núcleo próprio (HTTP002 como código de gap nas partes ainda ausentes).**

| Função | Forma |
|--------|-------|
| `get` | `get(String url) -> String` · `get(url, headers...) -> String` |
| `post` | `post(String url, String body) -> String` · `post(url, body, headers...) -> String` |
| `put` | `put(String url, String body) -> String` · variantes com headers |
| `delete` | `delete(String url) -> String` · variantes com headers |
| `patch` | `patch(String url, String body) -> String` · variantes com headers |
| `options` | `options(String url) -> String` · variantes com headers |
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

- Os verbos recebem headers como varargs (strings `"Nome: valor"`).
- `get`/`post`/... devolvem o corpo e lançam erro num 5xx; quando o status ou os
  headers da resposta importam, use `send`.
- `timeout`/`retry`/`circuit` são políticas DO PROCESSO — define uma vez,
  toda chamada obedece (intenção, não cerimônia por chamada).
- `timeout` é em segundos.
- Payloads JSON compõem com [kof.json](json.pt_BR.md); o passo a passo real
  vive em [26 — Aplicação Real](../26-real-world-application.pt_BR.md).

## `send` e `Response`

`http.send(method, url[, body][, headers...])` faz qualquer requisição e devolve
um `Response`. Um 4xx ou 5xx é uma resposta, não um erro; só uma falha de
transporte (conexão recusada, timeout, TLS) lança, como uma `String` que dá
para capturar.

| Método | Devolve |
|--------|---------|
| `status()` | `Int` |
| `body()` | `String` (vazia quando a resposta não tem corpo) |
| `header(String name)` | `String?`, o nome não diferencia maiúsculas, `null` quando ausente |
| `headers()` | `Map<String, String>`, nomes em minúsculas, valores repetidos unidos por `", "` |

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

- O corpo pode ser omitido ou `null` (`send("GET", url)`).
- `Response` é um nome de tipo que serve para variável, parâmetro ou retorno.
- `send` segue `timeout` e `circuit` (um 5xx conta contra o circuito, mas é
  devolvido); não faz `retry`, porque um 5xx é uma resposta que quem chamou lê.
- Alvos: JVM, Script e JS (no Node/browser, como o `get`, a chamada devolve um
  valor para `await`). Native e Wasm recusam `send` em tempo de compilação com
  `HTTP002`; no Native, `get`/`post`/`status` continuam disponíveis.
