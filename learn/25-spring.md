# 25 — Spring

> **Status: futuro (pós 0.2.6-beta — `kof.web` + `kof_db` já cobrem o caso sem Spring)**
>
> A integração com Spring é um dos objetivos de longo prazo da Kof. Este capítulo documenta a visão planejada — e o que funciona hoje sem Spring.

## O que funciona hoje (sem Spring)

APIs web completas rodam com `kof.web` (stack nativa, sem container) +
`kof.db`/`kof.orm` para persistência:

```kf
record User(String name, Int age)

main() {
    var app = web.app()
    app.use {                          // middleware
        if (header("x-auth") == "secret") {
            return null
        }
        return "{\"error\": \"unauthorized\"}"
    }
    app.get("/users/:id") {
        return "user " + param("id") + " q=" + query("name")
    }
    app.post("/user") {
        var user = json.decode<User>(body())
        return status(201, json.encode(user))
    }
    app.ws("/chat") {                  // WebSocket (30/08)
        var m = wsMessage()
        wsSend("echo: " + m)
    }
    app.sse("/events") {               // SSE (30/08)
        sse.send("hello")
        sse.event("tick", "hello")
        sse.close()
    }
    app.listen(8080)
}
```

- Rotas `get/post/put/delete/patch/options` + `ws` + `sse`, path params
  (`:id`), `query()`, `header()`, `body()`, `method()`, `path()`;
- Resposta rica: `status(201, body)` + `headerSet("X", "y")`;
- Cliente HTTP: `http.get/post/put/delete/patch/options` + `timeout`/
  `retry`/`circuit` (JVM+JS; Native reporta `HTTP002`);
- Web: `WEB002` no Native (sem servidor) — a stack web é JVM hoje.

Ver `docs/stdlib/stdlib-web.md`.

## A visão de longo prazo: o objetivo

Usar Spring Boot real, não um "Kof Spring".

```kf
@SpringBootApplication
class Application {
    static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

## REST Controller

```kf
@RestController
class UserController(UserService service) {

    @GetMapping("/users/{id}")
    User find(@PathVariable UUID id) {
        return service.find(id);
    }

    @GetMapping("/users")
    List<User> findAll() {
        return service.findAll();
    }

    @PostMapping("/users")
    User create(@RequestBody CreateUserRequest request) {
        return service.create(request);
    }
}
```

## Service

```kf
@Service
class UserService(UserRepository repository) {

    User find(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> new UserNotFound(id.toString()));
    }

    List<User> findAll() {
        return repository.findAll();
    }
}
```

## Repository

```kf
interface UserRepository extends CrudRepository<User, UUID> {
    List<User> findByActiveTrue();
}
```

## Como funciona

1. Kof gera bytecode JVM padrão
2. Spring enxerga as annotations no bytecode
3. Spring cria proxies normalmente
4. Injeção de dependência funciona
5. AOP funciona
6. Transaction management funciona

Kof não precisa de módulo especial para Spring. O bytecode é Java.

## Configuration

```kf
@Configuration
class AppConfig {
    @Bean
    DataSource dataSource() {
        return new HikariDataSource();
    }
}
```

## Testes com Spring

```kf
@SpringBootTest
class UserServiceTest {
    @Autowired
    UserService service;

    @Test
    void deveEncontrarUser() {
        var user = service.find(UUID.randomUUID());
        assertNotNull(user);
    }
}
```

## Próximo passo

[Aplicação Real →](26-real-world-application.md)
