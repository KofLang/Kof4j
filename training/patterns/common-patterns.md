# Kof Common Patterns

**Version:** 0.3.22-beta (Sep 2026)

## CRUD Entity (record + primary constructor)

```kof
record User(String name, String email)
var u = User("Mel", "mel@kof.dev")
```

## Service Pattern — prefer funções top-level (0.3.22-beta)

```kof
// Kof não precisa de Service/Repository ceremony — função top-level é idiomática
User findUser(Int id) {
    // implementation
}
String createUser(String name, String email): String {
    return json.encode(User(name, email))
}
```

Se estado for necessário, use classe com `Map`:

```kof
class UserService {
    Map<Int, User> store
    constructor() { store = mapOf() }
    put(Int id, User u) { store.put(id, u) }
    get(Int id): User? { return store.get(id) } // String?/User? se ausente
}
```

## Web Handler — legada + nativa

```kof
// Legada (ainda suportada)
handle(String method, String path, String body): String {
    if (path == "/users") return "{\"users\": []}"
    return "Not found"
}

// Nativa (idiomática) — JVM
var app = web.app()
app.get("/users") { return json.encode(users) }
app.get("/users/:id") { return param("id") }
app.post("/users") { var u = json.decode<User>(body()); return json.encode(u) }
return status(201, json.encode(u))   // status code customizado
headerSet("X-Custom", "value")       // header customizado
app.use { ... }                      // middleware
app.ws("/chat") { wsSend("echo: " + wsMessage()) }        // WebSocket
app.sse("/events") { sse.send("tick"); sse.event("ev", "dados"); sse.close() }  // SSE
app.listen(8080)
app.listenSecure(8443)               // TLS
```

`web.app()` completo no JVM (rotas `get/post/put/delete/patch/options`, `status(201, body)`,
`headerSet`, `app.use`, WebSocket `app.ws`, SSE `app.sse` com `sse.send/event/close`,
`listenSecure` TLS) — 30/08. Native/JS: WEB001.

## HTTP client (0.3.22-beta)

```kof
var html = http.get("https://example.com")
var resp = http.post(api, json.encode(body), "Content-Type: application/json")
if (http.status(url) == 200) { println(resp) }
http.timeout(30)    // ms
http.retry(3)       // repete em exceção + HTTP 5xx
http.circuit(5)     // abre circuito após N falhas por 30s; circuit(0) recupera
// verbos: get/post/put/delete/patch/options
// JVM + JS (Java HttpClient interop); Native HTTP002
```

## Error Handling

```kof
try {
    var result = riskyOperation()
    println(result)
} catch (String e) {
    println("Error: " + e)
} finally {
    println("Cleanup")
}

// Ausência como valor — String? (0.3.22-beta)
String? maybe = find("key")
if (maybe != null) {
    println(maybe)
} else {
    println("not found")
}
```

## Collections — higher-order (0.3.22-beta)

```kof
var nomes = users.map((u: User) -> u.name)
var adultos = users.filter((u: User) -> u.age >= 18)
var soma = nums.reduce((a: Int, b: Int) -> a + b, 0)

// Sem workaround manual de List.get
var x = listOf(1,2,3).get(1)   // 2
```

## Record pattern destructuring (0.3.22-beta)

```kof
record Point(Int x, Int y)
var p = Point(10, 20)
switch (p) {
    case Point(var x, var y):
        println(x + "," + y)
        break
}
```

## Array Processing

```kof
var items = new Int[5]
for (var i = 0; i < items.length; i++) {
    items[i] = i * 2
}
for (var n in items) {
    println(n)   // for-in também para arrays
}
```

## String Processing

```kof
var s = "Hello World"
println(s.length)           // 11
println(s.charAt(0))        // 72 (H)
println(s.substring(0, 5))  // "Hello"
println(s.contains("World")) // true
println(s.startsWith("Hello")) // true
println(s.endsWith("World"))   // true
var t: String? = null
if (t != null) println(t.length)
```

## Inheritance + Interface

```kof
interface Serializable {
    serialize(): String
}
class Entity implements Serializable {
    String id
    public constructor(String id) {
        this.id = id
    }
    public serialize(): String {
        return "{\"id\": \"" + id + "\"}"
    }
}
```

## KofScript global (0.3.22-beta)

```kof
let x = 5
const prefix: String = "ola"
println(prefix + " " + x)   // KofScript → KofScriptGlobals
```

## KofC — C subset nativo-only

```c
int counter;
void inc() { counter = counter + 1; }
int main() { inc(); return counter; }
```
