[English](kof-vs-java.md) | [Português](kof-vs-java.pt_BR.md)

# Kof vs Java — Technical Comparison

**Last updated:** September 12, 2026
**Version:** 0.6.7 (7 targets; pattern matching + `String?` + Native spawn)

---

## Overview

| Aspect | Java | Kof |
|---------|------|-----|
| Typing | Strong, static | Strong, static (0.6.0) |
| OO | Classes, interfaces, records | Classes, interfaces, records + `enum` + pattern matching `case String s`/`Point(x,y)` |
| Inheritance | Single + interfaces | Single + interfaces (3 levels) |
| GC | Automatic | JVM: automatic / Native: free-list `kof_free_head` (mark-sweep implemented 03/09, manual; auto-collect on exhaustion ✅ 19/09 — D1-A, §260 CLOSED) |
| Compilation | javac → bytecode | Kof → IR → JVM/Native (x86_64 + riscv64 + aarch64) / JS (GraalJS) / KofC / KofScript / Android (Phase 1) |
| Syntax | Verbose | Concise (`String?`, `map/filter/reduce`, `var`/`val` → `KofScriptGlobals`) |

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

### Kof (implemented)

```kof
class User(String name, Int age) {
    greeting(): String { return "Hello " + name }
}
```

**Difference:** Kof does not need getters/setters. `class X(...)` is
**record-style** (immutable — reading `u.name` becomes an accessor; writing `u.name =
"x"` does not). For **mutable state**, use fields + `constructor(...)` (direct
access `u.name` / `u.age = 30`).

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

**Difference:** Practically identical. Kof is slightly more concise.

---

## Inheritance

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

**Difference:** Kof is more concise. No `private`/`protected` on fields (direct access).

---

## Null Safety

### Java

```java
// Null pointer exception at runtime
String s = null;
s.length(); // NPE
```

### Kof

Kof has **basic** null safety (`String?`/`Int?`, 27/08): nullable types with
`Type?` and `?`-check at compile-time (`var s: String? = null`, `s == null`).

**Future proposal:** advanced checks (smart casts, Option in the core).

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

Generics by erasure (classes and functions). Bounds: planned.

---

## Collections (0.6.0)

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

**Difference:** Kof has real try/catch/finally on the 3 targets (JVM exception table; Native unwinding through the frame chain).

---

## Concurrency

### Java

```java
ExecutorService executor = Executors.newFixedThreadPool(4);
Future<String> future = executor.submit(() -> "result");
```

### Kof

Implemented: `spawn` with implicit join (JVM: virtual threads; Native:
`pthread_create` + trampoline + `pthread_join` with a thread-safe allocator
(futex), 31/08; JS: event-loop (CONC003 03/09)). `await`/typed handles. Zero platform API
exposed (`Thread`/`Executor` are runtime internals).

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

### Kof (PROPOSED)

```kof
service UserService {
    inject UserRepository repository
}
```

**Status:** Proposal only.

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

**Status:** Implemented (JVM) — native web stack `web.app()` (routes,
middleware, JSON, WebSocket/SSE, `status`/`headerSet`); `kof serve` runs it.

---

## Configuration

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

**Status:** Implemented — typed `kof.config` (JVM/Native; precedence
file > env > profile > default; CONF001 closed).

---

## Summary (0.5.0-beta, re-synced 17/09/2026 — `VERSION` 0.5.0-beta, `mvn test` 2218, 7 targets)

| Feature | Java | Kof 0.6.0 | Kof Future |
|---------|------|---------------|------------|
| Classes / Records / Inheritance / Interfaces / Virtual dispatch | ✅ | ✅ (JVM/Native x86_64 + riscv64 + JS `kof.http`) | ✅ |
| Null safety `String?` | ✅ (via `Optional`/checker) | ✅ basic `String?` (`Type?`) 27/08 | advanced checks |
| Generics `Box<T>` + `List<T>` | ✅ | ✅ `Box<T>` erasure (`substituteTypeVariable` `CompilerTypes.java:423`) | bounds |
| Collections `List`/`Map`/`Set` + `map/filter/reduce` | ✅ | ✅ `List map/filter/reduce` + `Map`/`Set` 3 targets 27/08 | — |
| Exceptions `try/catch/finally` | ✅ | ✅ JVM unwinding + Native unwinding | — |
| Pattern matching `case String s` + `Point(x,y)` | ✅ (17+) | ✅ JVM/Native/JS 27/08 | guards |
| Concurrency `spawn`/`await` | ✅ | ✅ JVM + Native pthread + JS event-loop (CONC001/CONC003 closed) | — |
| HTTP `serve` + `kof.http` | Framework | ✅ `web.app()` JVM + `kof.http` JVM/Native/JS | — |
| Config `kof.config` | Framework | ✅ JVM+Native+JS (free-list 27/08, CONF001 closed 16/09) | — |
| Logging / Observability | Framework | ✅ `kof.log` JVM+Native + `kof.observability` 3 targets (health/metrics/histograms/spans) + **OTel export ✅ JVM/JS (`exportSpans()` → OTLP/JSON, `OBS003`)** | Native OTel export (`OBS003`) |
| Database `kof.db`/`kof.orm` | Framework | ✅ JDBC + native SQLite + MySQL `kof_db_mysql_scramble` | query DSL |
| DI | Framework | ❌ (planned `service`) | proposal |
| KofScript / KofC | — | ✅ `KofScript` `var`/`val`→`KofScriptGlobals` + `KofCcompiler` `kof c` | — |
| Targets | — | JVM stable (ws/sse), native x86_64 stable + native.risc/native.arm full core (free-list + mark-sweep + pthread spawn, via qemu), js alpha, kofc, android Phase 1 | — |
