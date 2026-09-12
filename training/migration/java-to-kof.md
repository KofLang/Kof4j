# Java to Kof Migration

**Version:** 0.3.22-beta (Sep 2026)

## Classes

### Java
```java
public class User {
    private String name;
    private int age;
    
    public User(String name, int age) {
        this.name = name;
        this.age = age;
    }
    
    public String getName() { return name; }
    public int getAge() { return age; }
}
```

### Kof (record-style para dados imutáveis)
```kof
// class X(...) == record X(...): imutável, accessors, leitura u.name ok
class User(String name, Int age) {
}
var u = User("Mel", 30)
println(u.name)
```

> **Estado mutável (o Java `private` + setters NÃO se traduz 1:1):** em Kof o
> campo é público e mutável — sem getter/setter. Se a entidade muda, use
> classe com campos + `constructor(...)`:
> ```kof
> class User2 {
>     String name
>     Int age
>     public constructor(String name, Int age) {
>         this.name = name
>         this.age = age
>     }
> }
> var u2 = User2("Mel", 30)
> u2.age = 31      // campo direto — sem setAge()
> ```
> Dados imutáveis → `record User(String name, Int age)` (accessors `u.name()`).

## Records

### Java
```java
public record Point(int x, int y) {}
```

### Kof
```kof
record Point(Int x, Int y)
var p = Point(10, 20)
switch (p) {
    case Point(var x, var y): println(x + "," + y) // destructuring
}
```

## Inheritance

### Java
```java
public class Animal {
    protected String name;
    public Animal(String name) { this.name = name; }
}
public class Dog extends Animal {
    public Dog(String name) { super(name); }
}
```

### Kof
```kof
class Animal(String name) { }
class Dog extends Animal {
    public constructor(String name) { super(name) }
}
```

## Interfaces

### Java
```java
public interface Speaker {
    String speak();
}
public class Dog implements Speaker {
    public String speak() { return "woof"; }
}
```

### Kof
```kof
interface Speaker {
    speak(): String
}
class Dog implements Speaker {
    public speak(): String { return "woof" }
}
```

## Collections

### Java
```java
List<String> list = new ArrayList<>();
list.add("hello");
Map<String, Integer> map = new HashMap<>();
map.put("a", 1);
Set<String> set = new HashSet<>();
```

### Kof (3 targets)
```kof
var list = listOf("hello")
list.add("world")
list.contains("hello")
var x = list.get(0)          // fix 27/08 — sem workaround manual
println(list.size)

var map = mapOf("a", 1)
map.put("b", 2)
var v = map.get("a")

var set = setOf(1, 2, 3)
set.add(4)

// Higher-order
var nomes = users.map((u: User) -> u.name)
var pares = nums.filter((x: Int) -> x % 2 == 0)
var soma = nums.reduce((a: Int, b: Int) -> a + b, 0)

// Generics com primitivo
var box: Box<Int> = Box(42)
```

`List`, `Map`, `Set` disponíveis em JVM/Native/JS com `map/filter/reduce` e `Box<T>`.

## Null safety — Option vs String?

### Java
```java
Optional<String> maybe = Optional.of("hi");
String nullable = null;
```

### Kof (0.3.22-beta)
```kof
String? maybe = null
if (maybe != null) {
    println(maybe.length)   // narrowing
}
String? other = "ola"
var len = if (other != null) other.length else 0
// Option<T> genérico ainda planned — use String? para casos simples
```

## HTTP

### Java (Spring)
```java
@RestController
public class UserController {
    @GetMapping("/users/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.findById(id);
    }
}
```

### Kof (0.3.22-beta)
```kof
// kof.http client — JVM + JS (Java HttpClient interop), Native HTTP002
// verbos: get/post/put/delete/patch/options
var html = http.get("https://example.com")
var resp = http.post(api, json.encode(user), "Content-Type: application/json")
if (http.status(url) == 404) { println("not found") }
http.timeout(30)    // resiliência (30/08): timeout/retry/circuit
http.retry(3)
http.circuit(5)

// web server (JVM; Native/JS WEB001)
var app = web.app()
app.get("/users/:id") { return "user " + param("id") }
return status(201, body())       // status customizado por handler
headerSet("X-App", "kof")        // headers customizados
app.ws("/chat") { wsSend(wsMessage()) }  // WebSocket
app.sse("/events") { sse.send("tick") }  // SSE
app.listen(8080)
```

## Imports

### Java
```java
import java.util.List;
import java.util.*;
```

### Kof (fix 27/08)
```kof
import a.b.C          // file-specific — projetos grandes agora OK
import a.b.*
```

## KofScript

### Java — não aplicável

### Kof (0.3.22-beta)
```kof
let x = 5            // top-level let → KofScriptGlobals
const y: Int = 10
var name = "Mel"
println(x + y)
```
