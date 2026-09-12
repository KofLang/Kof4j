# 27 — Boas Práticas

> **Kof 0.3.22-beta — `String?`, `Point(x,y)`, `map/filter/reduce`, `intention->Kof->frontend->IR->backend->runtime`**

## Novidades 0.2.0 que afetam estilo

- Use `String?` para nullable em vez de comentários sobre null.
- Prefira `case Point(x, y):` a `if` cascata quando desestruturar records.
- Use `list.map/filter/reduce` em vez de `for` manual quando a intenção for transformar.
- `let`/`const` no topo só em `.ks` (KofScript → `KofScriptGlobals`); em `.kf` use `var`/`val`.
- Web: um app `web.app()` por processo; middleware em `app.use { }` antes das rotas;
  respostas ricas com `status(código, body)` + `headerSet(...)` em vez de strings crues.
- HTTP client: `http.get/post/put/delete/patch/options` + `timeout`/`retry`/`circuit`
  (JVM+JS) — nunca raw sockets para HTTP.
- `spawn` para paralelismo; `await` para o resultado. Sem API de thread exposta.
- Formate com `kof fmt -w` (31/08) — o formatter é idempotente.

## Naming

- **Classes**: PascalCase (`UserService`, `TaskRepository`)
- **Records**: PascalCase (`User`, `Point`)
- **Métodos**: camelCase (`findUser`, `isActive`)
- **Campos**: camelCase (`userName`, `createdAt`)
- **Variáveis locais**: camelCase (`indice`, `tamanho`)
- **Constants**: SCREAMING_SNAKE_CASE (`MAX_SIZE`, `DEFAULT_TIMEOUT`)
- **Packages**: lowercase (`com.exemplo.users`)

## Organização

Um arquivo `.kf` deve conter uma principal declaração de tipo.

```
src/main/kof/
├── com/exemplo/
│   ├── model/
│   │   ├── User.kf
│   │   └── Task.kf
│   ├── service/
│   │   ├── UserService.kf
│   │   └── TaskService.kf
│   ├── repository/
│   │   ├── UserRepository.kf
│   │   └── TaskRepository.kf
│   └── controller/
│       ├── UserController.kf
│       └── TaskController.kf
```

## Composition vs Inheritance

Prefira composição:

```kf
// BOM
class Motorista(Carro carro) {
    void dirigir() {
        carro.mover();
    }
}

// EVITAR (quando não faz sentido)
class Motorista extends Carro {
    // ...
}
```

Use herança apenas quando a relação for "é um tipo de":
- `Cachorro` é um `Animal`
- `Exception` é um `Exception`
- `AdminController` é um `Controller`

## Error Handling

```kf
// BOM: tratamento explícito
User findUser(UUID id) {
    return repository.findById(id)
        .orElseThrow(() -> new UserNotFound(id.toString()));
}

// EVITAR: swallowed exceptions
try {
    riskyOperation();
} catch (Exception e) {
    // silenciosamente ignorado
}
```

## Imutabilidade

Prefira `val` sobre `var`:

```kf
val nome = "Mel";        // bom
var nome = "Mel";        // ok se precisar reatribuir
```

Prefira records sobre classes mutáveis para dados:

```kf
record User(String name, String email)  // imutável
```

## Próximo passo

[Design da Linguagem →](28-language-design.md)
