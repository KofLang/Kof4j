# Kof vs Spring — O Problema que Kof Resolve

**Última atualização:** 2 de setembro de 2026
**Versão:** 0.2.6-beta (810 testes; web stack completa: ws/sse/middleware/cache; `kof.http` JVM+JS com retry/circuit; `kof.db` SQLite nativo + MySQL WIP)

---

## A Pergunta Central

> "O que o Spring resolve que deveria ser responsabilidade da linguagem?"

Spring não é ruim. Spring resolve problemas reais. Mas muitos desses problemas existem porque Java não os resolve nativamente.

Kof pergunta: **"Se a linguagem já resolvesse isso, precisaríamos do framework?"**

---

## Mapeamento: Spring → Problema Real → Solução Kof

### 1. Dependency Injection

**Problema real:** Objetos precisam de outros objetos. Criar e conectar manualmente é verboso e acoplado.

**Solução Spring:**
```java
@Service
public class UserService {
    @Autowired
    private UserRepository repository;
}
```

**Solução Kof (PROPOSTA):**
```kof
service UserService {
    inject UserRepository repository
}
```

**Por que é melhor:** O compilador pode resolver o grafo de dependências em compile-time. Sem reflection, sem runtime magic.

### 2. Configuration

**Problema real:** Aplicações precisam de configuração (portas, URLs, credenciais).

**Solução Spring:**
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

**Solução Kof (PROPOSTA):**
```kof
config {
    port = 8080
    database.url = "jdbc:mysql://localhost/mydb"
}
```

**Por que é melhor:** Configuração tipada pelo compilador. Erros de configuração capturados em compile-time.

### 3. HTTP Routing

**Problema real:** Criar APIs REST requer muito boilerplate.

**Solução Spring:**
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

**Solução Kof (implementada no JVM, Fase 1 — independência do Spring):**
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
app.listen(8080)              // servidor próprio, sem servlet container
```

**Por que é melhor:** Routing é parte da linguagem. Sem annotations, sem ResponseEntity, sem boilerplate. Engine HTTP gerada no runtime do programa; cada conexão em virtual thread; WebSocket/SSE/middleware/`status`/`headerSet` nativos (30-31/08).

### 4. Validation

**Problema real:** Validação de dados é repetitiva e propensa a erros.

**Solução Spring:**
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

**Solução Kof (PROPOSTA):**
```kof
class User {
    name: String required size(2, 50)
    age: Int range(0, 150)
}
```

**Por que é melhor:** Validação é parte da definição do tipo. O compilador pode gerar código de validação automaticamente.

### 5. Serialization

**Problema real:** Converter objetos para JSON/XML requer annotations ou configuração.

**Solução Spring:**
```java
@Data
public class User {
    private Long id;
    private String name;
}
```

**Solução Kof (PROPOSTA):**
```kof
class User {
    Long id
    String name
    // Serialização automática para JSON
}
```

**Por que é melhor:** Se a classe tem campos públicos, a serialização pode ser implícita.

### 6. Lifecycle

**Problema real:** Aplicações precisam de inicialização e finalização.

**Solução Spring:**
```java
@Component
public class MyService {
    @PostConstruct
    public void init() { ... }
    
    @PreDestroy
    public void cleanup() { ... }
}
```

**Solução Kof (PROPOSTA):**
```kof
service MyService {
    lifecycle {
        startup { ... }
        shutdown { ... }
    }
}
```

**Por que é melhor:** Lifecycle é parte da linguagem, não de annotations.

### 7. Testing

**Problema real:** Testes em Java requerem frameworks (JUnit, Mockito, etc.).

**Solução Spring:**
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

**Solução Kof (implementada):**
```kf
test "find user by id" {
    assert(users.find(1) != null)
}
```

**Por que é melhor:** Testing é parte da linguagem. Sem annotations, sem framework. `test "nome" { }` nos 3 targets; runner sintetizado em compile-time (zero reflection); `kof test` reporta PASS/FAIL por nome + exit code.

---

## O Que Kof NÃO Deve Fazer

1. **Não criar um Spring clone.** O objetivo é eliminar a necessidade do Spring, não reimplementá-lo.

2. **Não exigir configuração para recursos básicos.** Se algo pode ser inferido, não deve ser configurado.

3. **Não criar abstrações desnecessárias.** Cada abstração deve justificar sua existência.

4. **Não copiar annotations.** Se a linguagem pode resolver algo, não use annotations.

---

## Prioridade

| Feature | Prioridade | Justificativa | Status (0.2.6-beta, 31/08) |
|---------|-----------|---------------|----------------------------|
| DI | Alta | Elimina boilerplate massivo | planejado (proposta `service`) |
| HTTP routing | Alta | Essencial para backends | ✅ `web.app()` JVM (rotas, middleware, JSON, ws/sse) |
| Configuration | Média | Melhora DX significativamente | ✅ `kof.config` JVM/Native |
| Validation | Média | Elimina beans validation | ✅ `kof.validation` 3 targets |
| Serialization | Média | Essencial para APIs | ✅ `json.encode/decode` 3 targets |
| Lifecycle | Baixa | Pode esperar | planejado (`application { onStart/onShutdown }`) |
| Testing | Alta | Essencial para produtividade | ✅ `test "nome" { }` + `kof test` 3 targets |
