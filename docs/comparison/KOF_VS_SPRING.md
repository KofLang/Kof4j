[English](KOF_VS_SPRING.md) | [Português](KOF_VS_SPRING.pt_BR.md)

# Kof vs Spring — The Problem Kof Solves

**Last updated:** September 12, 2026
**Version:** 0.6.7 (complete web stack: ws/sse/middleware/cache; `kof.http` JVM+JS with retry/circuit; `kof.db` native SQLite + MySQL wire x86-64 real, 13 ORM faces)

---

## The Central Question

> "What does Spring solve that should be the language's responsibility?"

Spring is not bad. Spring solves real problems. But many of those problems exist because Java does not solve them natively.

Kof asks: **"If the language already solved this, would we need the framework?"**

---

## Mapping: Spring → Real Problem → Kof Solution

### 1. Dependency Injection

**Real problem:** Objects need other objects. Creating and wiring them manually is verbose and coupled.

**Spring solution:**
```java
@Service
public class UserService {
    @Autowired
    private UserRepository repository;
}
```

**Kof solution (PROPOSED):**
```kof
service UserService {
    inject UserRepository repository
}
```

**Why it is better:** The compiler can resolve the dependency graph at compile-time. No reflection, no runtime magic.

### 2. Configuration

**Real problem:** Applications need configuration (ports, URLs, credentials).

**Spring solution:**
```properties
server.port=8080
spring.datasource.url=jdbc:mysql://localhost/mydb
```

```java
@Configuration
public class AppConfig {
    @Bean
    public DataSource dataSource() {
        return DataSourceBuilder.create()
            .url("jdbc:mysql://localhost/mydb")
            .build();
    }
}
```

**Kof solution (PROPOSED):**
```kof
config {
    port = 8080
    database.url = "jdbc:mysql://localhost/mydb"
}
```

**Why it is better:** Configuration typed by the compiler. Configuration errors caught at compile-time.

### 3. HTTP Routing

**Real problem:** Creating REST APIs requires a lot of boilerplate.

**Spring solution:**
```java
@RestController
@RequestMapping("/api/users")
public class UserController {
    @GetMapping("/{id}")
    public ResponseEntity<User> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findById(id));
    }
    
    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody User user) {
        return ResponseEntity.ok(userService.create(user));
    }
}
```

**Kof solution (implemented on the JVM, Phase 1 — independence from Spring):**
```kf
var app = web.app()
app.get("/users/:id") {
    return User(param("id"))
}
app.post("/users") {
    return json.encode(json.decode<User>(body()))
}
app.use { ... }               // middleware (auth, logging, ...)
app.ws("/chat") { ... }       // WebSocket (30/08, RFC 6455)
app.listen(8080)              // own server, no servlet container
```

**Why it is better:** Routing is part of the language. No annotations, no ResponseEntity, no boilerplate. HTTP engine generated in the program's runtime; each connection on a virtual thread; native WebSocket/SSE/middleware/`status`/`headerSet` (30-31/08).

### 4. Validation

**Real problem:** Data validation is repetitive and error-prone.

**Spring solution:**
```java
public class User {
    @NotNull
    @Size(min = 2, max = 50)
    private String name;
    
    @Min(0)
    @Max(150)
    private int age;
}
```

**Kof solution (PROPOSED):**
```kof
class User {
    name: String required size(2, 50)
    age: Int range(0, 150)
}
```

**Why it is better:** Validation is part of the type definition. The compiler can generate validation code automatically.

### 5. Serialization

**Real problem:** Converting objects to JSON/XML requires annotations or configuration.

**Spring solution:**
```java
@Data
public class User {
    private Long id;
    private String name;
}
```

**Kof solution (PROPOSED):**
```kof
class User {
    Long id
    String name
    // Automatic serialization to JSON
}
```

**Why it is better:** If the class has public fields, serialization can be implicit.

### 6. Lifecycle

**Real problem:** Applications need initialization and shutdown.

**Spring solution:**
```java
@Component
public class MyService {
    @PostConstruct
    public void init() { ... }
    
    @PreDestroy
    public void cleanup() { ... }
}
```

**Kof solution (PROPOSED):**
```kof
service MyService {
    lifecycle {
        startup { ... }
        shutdown { ... }
    }
}
```

**Why it is better:** Lifecycle is part of the language, not of annotations.

### 7. Testing

**Real problem:** Tests in Java require frameworks (JUnit, Mockito, etc.).

**Spring solution:**
```java
@SpringBootTest
public class UserServiceTest {
    @Autowired
    private UserService service;
    
    @Test
    public void testFind() {
        assertNotNull(service.findById(1L));
    }
}
```

**Kof solution (implemented):**
```kf
test "find user by id" {
    assert(users.find(1) != null)
}
```

**Why it is better:** Testing is part of the language. No annotations, no framework. `test "name" { }` on the 3 targets; runner synthesized at compile-time (zero reflection); `kof test` reports PASS/FAIL by name + exit code.

---

## What Kof Should NOT Do

1. **Do not create a Spring clone.** The goal is to eliminate the need for Spring, not to reimplement it.

2. **Do not require configuration for basic resources.** If something can be inferred, it should not be configured.

3. **Do not create unnecessary abstractions.** Every abstraction must justify its existence.

4. **Do not copy annotations.** If the language can solve something, do not use annotations.

---

## Priority

| Feature | Priority | Justification | Status (0.6.0) |
|---------|-----------|---------------|----------------------------|
| DI | High | Eliminates massive boilerplate | planned (`service` proposal) |
| HTTP routing | High | Essential for backends | ✅ `web.app()` JVM (routes, middleware, JSON, ws/sse) |
| Configuration | Medium | Significantly improves DX | ✅ `kof.config` JVM/Native |
| Validation | Medium | Eliminates beans validation | ✅ `kof.validation` 3 targets |
| Serialization | Medium | Essential for APIs | ✅ `json.encode/decode` 3 targets |
| Lifecycle | Low | Can wait | ✅ `application { onStart/onShutdown }` (language-level, runs before/after `main`) |
| Testing | High | Essential for productivity | ✅ `test "name" { }` + `kof test` 3 targets |
