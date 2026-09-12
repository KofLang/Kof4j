# Kof vs Java — Comparação Técnica

**Última atualização:** 2 de setembro de 2026
**Versão:** 0.2.6-beta (810 testes; 7 targets; pattern matching + `String?` + spawn Native)

---

## Visão Geral

| Aspecto | Java | Kof |
|---------|------|-----|
| Tipagem | Forte, estática | Forte, estática (0.2.6-beta) |
| OO | Classes, interfaces, records | Classes, interfaces, records + `enum` + pattern matching `case String s`/`Point(x,y)` |
| Herança | Simples + interfaces | Simples + interfaces (3 níveis) |
| GC | Automático | JVM: automático / Native: free-list `kof_free_head` (mark-sweep pendente; auto-GC desativado — `munmap` fallback, 27-31/08) |
| Compilação | javac → bytecode | Kof → IR → JVM/Native (x86_64; `native.risc`/`native.arm` placeholder) / JS (GraalJS) / KofC / KofScript / Android (Fase 1) |
| Sintaxe | Verbosa | Concisa (`String?`, `map/filter/reduce`, `let` → `KofScriptGlobals`) |

---

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }
}
```

### Kof (implementado)

```kof
class User(String name, Int age) {
    greeting(): String { return "Hello " + name }
}
```

**Diferença:** Kof não precisa de getters/setters. `class X(...)` é
**record-style** (imutável — leitura `u.name` vira accessor; escrita `u.name =
"x"` não). Para **estado mutável**, use campos + `constructor(...)` (acesso
direto `u.name` / `u.age = 30`).

---

## Records

### Java

```java
public record Point(int x, int y) {}
```

### Kof

```kof
record Point(Int x, Int y)
```

**Diferença:** Praticamente idênticos. Kof é ligeiramente mais conciso.

---

## Herança

### Java

```java
public class Animal {
    protected String name;
    public Animal(String name) { this.name = name; }
    public String speak() { return name; }
}

public class Dog extends Animal {
    public Dog(String name) { super(name); }
    public String speak() { return "woof"; }
}
```

### Kof

```kof
class Animal {
    String name
    public constructor(String name) {
        this.name = name
    }
    public speak(): String {
        return name
    }
}
class Dog extends Animal {
    public constructor(String name) {
        super(name)
    }
    public speak(): String {
        return "woof"
    }
}
```

**Diferença:** Kof é mais conciso. Sem `private`/`protected` em campos (acesso direto).

---

## Null Safety

### Java

```java
// Null pointer exception em runtime
String s = null;
s.length(); // NPE
```

### Kof

Kof tem null safety **básica** (`String?`/`Int?`, 27/08): tipos nullable com
`Type?` e `?`-check em compile-time (`var s: String? = null`, `s == null`).

**Proposta futura:** checks avançados (smart casts, Option no core).

---

## Generics

### Java

```java
List<String> list = new ArrayList<>();
list.add("hello");
String s = list.get(0);
```

### Kof

```kof
var list = listOf("hello", "world")
list.add("!")
var s = list.get(0)
```

Generics por erasure (classes e funções). Bounds: planejados.

---

## Collections (0.2.6-beta)

### Java

```java
List<String> list = new ArrayList<>();
Map<String, Integer> map = new HashMap<>();
Set<String> set = new HashSet<>();
```

### Kof

```kof
var list = listOf("hello", "world")     // List<String>
list.add("!")
list.contains("hello")
list.size
var m = mapOf("a", 1)                   // Map<K,V> 0.1.0 — 3 targets
var s = setOf(1, 2, 3)                  // Set<T> 0.1.0 — 3 targets
var doubled = list.map((x: Int) -> x * 2) // 0.2.0 — map/filter/reduce 3 targets
```

---

## Exceptions

### Java

```java
try {
    throw new RuntimeException("error");
} catch (RuntimeException e) {
    System.out.println(e.getMessage());
} finally {
    // cleanup
}
```

### Kof

```kof
try {
    throw "error"
} catch (String e) {
    println(e)
} finally {
    // cleanup
}
```

**Diferença:** Kof tem try/catch/finally reais nos 3 targets (JVM exception table; Native unwinding pela cadeia de frames).

---

## Concorrência

### Java

```java
ExecutorService executor = Executors.newFixedThreadPool(4);
Future<String> future = executor.submit(() -> "result");
```

### Kof

Implementado: `spawn` com join implícito (JVM: virtual threads; Native:
`pthread_create` + trampoline + `pthread_join` com allocator thread-safe
(futex), 31/08; JS: sequencial). `await`/handles tipados. Zero API de
plataforma exposta (`Thread`/`Executor` são internos do runtime).

---

## Dependency Injection

### Java (Spring)

```java
@Service
public class UserService {
    @Autowired
    private UserRepository repository;
}
```

### Kof (PROPOSTA)

```kof
service UserService {
    inject UserRepository repository
}
```

**Status:** Proposta apenas.

---

## HTTP

### Java (Spring Boot)

```java
@RestController
public class UserController {
    @GetMapping("/users/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.findById(id);
    }
}
```

### Kof

```kf
var app = web.app()
app.get("/users/:id") {
    return User(param("id"))
}
app.post("/user") {
    return json.encode(json.decode<User>(body()))
}
app.ws("/chat") { ... }          // WebSocket (JVM, 30/08)
app.listen(8080)
```

**Status:** Implementado (JVM) — stack web nativa `web.app()` (rotas,
middleware, JSON, WebSocket/SSE, `status`/`headerSet`); `kof serve` executa.

---

## Configuração

### Java (Spring Boot)

```properties
# application.properties
server.port=8080
spring.datasource.url=jdbc:mysql://localhost/mydb
```

### Kof

```kf
var port = config.int("server.port", 8080)
var url = config.str("database.url", "jdbc:h2:mem")
```

**Status:** Implementado — `kof.config` tipado (JVM/Native; precedência
arquivo > env > profile > default; JS reporta CONF001).

---

## Resumo (0.2.6-beta, 31/08/2026 — `VERSION` 0.2.6-beta, `mvn test` 810, 7 targets)

| Feature | Java | Kof 0.2.6-beta | Kof Futuro |
|---------|------|---------------|------------|
| Classes / Records / Herança / Interfaces / Virtual dispatch | ✅ | ✅ (JVM/Native x86_64 + riscv64 + JS `kof.http`) | ✅ |
| Null safety `String?` | ✅ (via `Optional`/checker) | ✅ básica `String?` (`Type?`) 27/08 | checks avançados |
| Generics `Box<T>` + `List<T>` | ✅ | ✅ `Box<T>` erasure (`substituteTypeVariable` `CompilerDriver.java:3972`) | bounds |
| Collections `List`/`Map`/`Set` + `map/filter/reduce` | ✅ | ✅ `List map/filter/reduce` + `Map`/`Set` 3 targets 27/08 | — |
| Exceptions `try/catch/finally` | ✅ | ✅ JVM unwinding + Native unwinding | — |
| Pattern matching `case String s` + `Point(x,y)` | ✅ (17+) | ✅ JVM/Native/JS 27/08 | guards |
| Concorrência `spawn`/`await` | ✅ | ✅ JVM + JS sequencial; Native `CONC001` | Native scheduler |
| HTTP `serve` + `kof.http` | Framework | ✅ `web.app()` JVM + `kof.http` JVM+JS | Native HTTP |
| Config `kof.config` | Framework | ✅ JVM+Native (free-list 27/08) | JS `CONF001` |
| Logging / Observability | Framework | ✅ `kof.log` JVM+Native + `kof.observability` 3 targets | tracing |
| Database `kof.db`/`kof.orm` | Framework | ✅ JDBC + SQLite native + MySQL `kof_db_mysql_scramble` | query DSL |
| DI | Framework | ❌ (planned `service`) | proposta |
| KofScript / KofC | — | ✅ `KofScript` `let`→`KofScriptGlobals` + `KofCcompiler` `kof c` | — |
| Targets | — | JVM stable (ws/sse), native x86_64 stable (free-list + pthread spawn), native.risc/native.arm (placeholder via qemu), js alpha, kofc, android Fase 1 | — |
