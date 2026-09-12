# Standard Library — Proposta

**Última atualização:** 2 de setembro de 2026
> **Atualizado (0.2.6-beta):** a stdlib está amplamente implementada nos 3
> targets (JVM/Native/JS) — `kof.core`, `kof.collections`, `kof.io`,
> `kof.time`, `kof.json` (FP + arrays completos no Native, 31/08),
> `kof.http` (client + resiliência JVM+JS), `kof.web` (`web.app()` +
> WebSocket/SSE JVM), `kof.db`/`kof.orm`, `kof.security`, `kof.config`,
> `kof.logging`, `kof.observability`, `kof.mq`, `kof.cache`,
> `kof.scheduler`, `kof.validation`, `kof.test`, `kof.ui` (Color/Theme/
> Palette + widgets). **Esta página é o plano original; o estado atual, a
> matriz de módulos e a arquitetura vivem em `docs/stdlib/stdlib.md`** (fonte de
> referência). A tabela abaixo é o plano completo.

**Status:** amplamente implementado (0.2.6-beta; ver `docs/stdlib/stdlib.md`)

---

## Filosofia

> Se é essencial para qualquer programa, pertence à plataforma.

A standard library deve ser:
- Mínima
- Coerente
- Sem dependências externas
- Disponível em todos os backends

---

## Módulos Propostos

### kof.core

Tipos e operações básicas.

```
String.length()
String.charAt(index)
String.substring(start, end)
String.concat(other)
String.equals(other)
String.contains(other)
String.startsWith(prefix)
String.endsWith(suffix)
String.trim()
String.toLowerCase()
String.toUpperCase()
String.indexOf(other)
String.split(delimiter)
```

### kof.io

Entrada/saída básica.

```
println(value)
print(value)
input() → String
```

### kof.time

Data e hora.

```
DateTime.now()
DateTime.parse("2024-01-01")
duration.hours()
```

### kof.json

Serialização JSON.

```
json.encode(obj)
json.decode(str, Type)
```

### kof.sql

Acesso a banco de dados (futuro).

```
users.find(1)
users.where(User.age > 18)
```

### kof.http

Cliente HTTP (futuro).

```
http.get("https://api.example.com/users")
http.post("https://api.example.com/users", data)
```

### kof.concurrent

Concorrência — `spawn` implementado (JVM, virtual threads).

```kof
spawn processarFila()
spawn { ... }
```

`await`/resultado de tarefa: planejado. Ver `docs/language-reference/concurrency.md`.

### kof.test

Testes — `assert(cond[, "msg"])` + `kof test <file.kf|dir>` implementados.

```kof
main() {
    assert(2 + 2 == 4)
}
```

Suite estruturada (`test "soma" { ... }`): planejada.

---

## Prioridade

| Módulo | Prioridade | Status |
|--------|-----------|--------|
| kof.core | Alta | Parcial (String ops, println, tipos) |
| kof.io | Alta | Implementado (File/Path/Directory) |
| kof.web | Alta | Planejado |
| kof.http | Alta | Implementado (`kof serve` + KofHttpServer) |
| kof.json | Média | Implementado (`json.encode`/`decode`) |
| kof.time | Média | Implementado (`now()`) |
| kof.concurrent | Alta | Parcial (`spawn` JVM) |
| kof.test | Alta | Parcial (`assert` + `kof test`) |
| kof.sql | Alta | Não implementado |
| kof.concurrent | Média | Não implementado |
| kof.test | Alta | Não implementado |

---

## Princípios

1. **Mínimo necessário** — não criar bibliotecas que ninguém usa
2. **Coerência** — APIs devem seguir padrões consistentes
3. **Backend-agnostic** — mesma API em JVM e Native
4. **Sem dependências** — standard library não depende de bibliotecas externas
5. **Evolução** — APIs podem ser estendidas sem quebrar código existente
