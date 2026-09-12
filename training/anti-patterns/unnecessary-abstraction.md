# Anti-pattern — Unnecessary Abstraction

**Updated:** 0.3.22-beta (Sep 2026) (02 Sep 2026)

## Name

Abstrações criadas sem problema real.

## Problem

Classes "Manager", "Helper", "Context", "Handler", "Wrapper", "Factory" que
apenas repassam chamadas. Cada camada adiciona indireção sem semântica.

## Bad example

```kof
class UserManager {
    UserRepository repo
    constructor() {
        repo = new UserRepository()
    }
    find(Int id): User {
        return repo.find(id)
    }
}
class UserRepository {
    find(Int id): User {
        // lógica real
    }
}
```

## Why it is bad

O consumidor precisa conhecer duas classes para fazer o que uma função faz.
A indireção não resolve nenhum problema (transação? cache? permutabilidade?).

## Preferred approach

```kof
User findUser(Int id) {
    // lógica real
}
```

## Regra

Adicione uma camada somente quando ela resolve um problema concreto:
- permutabilidade testada (interface + múltiplas implementações);
- transação/cleanup transversal;
- estado compartilhado real.

Se a camada apenas repassa, remova-a.

## Exceptions

- Interop com código legado que exige a estrutura.