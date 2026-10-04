[English](README.md) | [Português](README.pt_BR.md)

# Standard Library — Proposta

**Última atualização:** 16 de setembro de 2026
> **Atualizado (0.5.0-beta):** a stdlib está amplamente implementada nos 3
> targets (JVM/Native/JS) — `kof.core`, `kof.collections`, `kof.io`,
> `kof.time`, `kof.json` (FP + arrays completos no Native, 31/08),
> `kof.http` (client + resiliência JVM+JS), `kof.web` (`web.app()` +
> WebSocket/SSE JVM), `kof.db`/`kof.orm`, `kof.security`, `kof.config`,
> `kof.logging`, `kof.observability`, `kof.mq`, `kof.cache`,
> `kof.scheduler`, `kof.validation`, `kof.test`, `kof.ui` (Color/Theme/
> Palette + widgets). **Esta página é o plano original; o estado atual, a
> matriz de módulos e a arquitetura vivem em `docs/stdlib/stdlib.md`** (fonte de
> referência). A tabela abaixo é o plano original, com o estado atual.

**Status:** amplamente implementado (0.5.0-beta; ver `docs/stdlib/stdlib.md`)

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
time.now()                 // millis desde a epoch
time.todayIso()            // "2026-10-04"
time.addDays(iso, 7)       // ISO entra, ISO sai
time.diffDays(a, b)
time.sleep(ms)
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

`await`/resultado de tarefa: implementado — `val r = spawn f()` + `await r` (3 targets). Ver `docs/language-reference/concurrency.md`.

### kof.test

Testes — `assert(cond[, "msg"])` + `kof test <file.kf|dir>` implementados.

```kof
main() {
    assert(2 + 2 == 4)
}
```

Suite estruturada (`test "soma" { ... }`): implementada nos 3 targets (`StructuredTestE2ETest` 11/11).

---

## Prioridade

| Módulo | Prioridade | Status |
|--------|-----------|--------|
| kof.core | Alta | Implementado (String ops, println, tipos) |
| kof.io | Alta | Implementado (File/Path/Directory) |
| kof.web | Alta | Implementado (`web.app()` + ws/sse) |
| kof.http | Alta | Implementado (`kof serve` + client) |
| kof.json | Média | Implementado (`json.encode`/`decode`) |
| kof.time | Média | Implementado (`now()`, `sleep`, `interval`) |
| kof.concurrent | Alta | Implementado (`spawn`/`await`/`channel<T>`, 3 targets) |
| kof.test | Alta | Implementado (`assert` + `test "name"` + `kof test`) |
| kof.sql | Alta | Implementado como `kof.db`/`kof.orm` (MySQL wire x86-64 real, 13 faces ORM 22/09) — nome do módulo no plano |

---

## Princípios

1. **Mínimo necessário** — não criar bibliotecas que ninguém usa
2. **Coerência** — APIs devem seguir padrões consistentes
3. **Backend-agnostic** — mesma API em JVM e Native
4. **Sem dependências** — standard library não depende de bibliotecas externas
5. **Evolução** — APIs podem ser estendidas sem quebrar código existente
