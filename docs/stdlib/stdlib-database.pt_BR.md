[English](stdlib-database.md) | [Português](stdlib-database.pt_BR.md)

# stdlib database — Banco de Dados Nativo do Kof

**Última atualização:** 18 de setembro de 2026
**Versão:** 0.5.0-beta
**Status:** implementado (Fase 5 do plano de independência do Spring) — JVM (JDBC) + Native (SQLite via `.so` direto + MySQL wire real no x86-64, F2d1–F2d7 22/09) + `kof.orm` (JVM + MongoDB); JS nao-tipado ✅ (16/09), tipado `query<T>` ✅ (18/09, `DB002` fechado), `kof.orm` ✅ no JS (18/09, `ORM001` fechado); `ORM001` segue só no Native cross riscv64/aarch64

---

## 1. Filosofia

> Acesso a banco é uma capacidade da plataforma. JDBC é o mecanismo interno
> (interoperabilidade JVM); a API exposta é Kof-idomática — sem
> `EntityManager`, `Session`, `PersistenceContext` ou `@Transactional`.

## 2. API

```kof
main() {
    var db = db.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1")

    db.execute(db, "create table users(id int, name varchar(50))")
    db.execute(db, "insert into users values (?, ?)", 1, "Mel")
    db.execute(db, "insert into users values (?, ?)", 2, "Kof")

    // Consulta sem tipo: cada linha vira um objeto JSON
    var rows = db.query(db, "select * from users where id = ?", 1)
    println(rows.get(0))     // {"id":1,"name":"Mel"}

    // Consulta tipada: bind automático para records/classes
    var users = db.query<User>(db, "select * from users order by id")
    println(users.get(0).name)

    // Transação: commit automático; rollback em erro
    transaction {
        db.execute(db, "insert into users values (3, 'Ada')")
    }

    db.close(db)
}
```

## 3. Funções

| Chamada | Descrição |
|---------|-----------|
| `db.connect(url)` | Conecta e retorna o handle |
| `db.connect(url, user, pass)` | Conecta com credenciais |
| `db.execute(handle, sql[, args...])` | UPDATE/INSERT/DELETE; retorna linhas afetadas |
| `db.query(handle, sql[, args...])` | SELECT; `List<String>` — cada linha em JSON |
| `db.query<T>(handle, sql[, args...])` | SELECT; `List<T>` — bind por nome de coluna |
| `transaction { ... }` | Bloco transacional (usa a última conexão) |
| `db.close(handle)` | Fecha a conexão |

- Bind com `?` placeholders; args de `Int/Long/Bool/String` são convertidos
  automaticamente (boxing).
- Colunas são normalizadas para minúsculas (H2/Postgres devolvem maiúsculas).
- Até 4 argumentos de bind por chamada (overloads de aridade fixa).

## 4. Exemplo com a stack web

```kof
record User(Int id, String name)

main() {
    var db = db.connect(config.str("database.url", "jdbc:h2:mem:app"))
    db.execute(db, "create table if not exists users(id int, name varchar(50))")

    var app = web.app()
    app.get("/users") {
        var users = db.query<User>(db, "select * from users order by id")
        return json.encode(users)
    }
    app.post("/users") {
        var u = json.decode<User>(body())
        db.execute(db, "insert into users values (?, ?)", u.id, u.name)
        return "{\"ok\": true}"
    }
    app.listen(config.int("server.port", 8080))
}
```

## 5. Drivers

JDBC por `java.sql.DriverManager` — qualquer driver JDBC no classpath
funciona (H2, MySQL, MariaDB, PostgreSQL, SQLite) no JVM. O driver é resolvido
pelo `ServiceLoader` do JDK; nenhum acoplamento de biblioteca no runtime Kof.

Native:
- **SQLite** — link direto da `libsqlite3.so.0` (sem driver JDBC), DSN
  `sqlite:/path.db`; `execute`/`query`/bind tipado com roundtrip E2E real.
- **MySQL/MariaDB** — wire protocol próprio sobre sockets nativos (WIP):
  handshake + auth `mysql_native_password` (scramble SHA-1, `kof_db_mysql_scramble`)
  + `lenenc` + parse de `user:pass@` na DSN `mysql://[user[:pass]@]host[:port][/db]`.
  O link inclui a lib do MySQL apenas quando o programa a usa (DSN literal
  detectado em compile-time). Handshake completo, query e prepared statements
  ainda em progresso (P3).

## 6. Targets (0.5.0-beta)

| Target | Estado | Notas |
|--------|--------|-------|
| JVM | ✅ completo (JDBC) | `db.connect`/`execute`/`query<T>`/`transaction` (H2/MySQL/MariaDB/PostgreSQL/SQLite) + `orm.*` (entity, `saveAll`, `where` operadores, `page`, `count` filtrado, `deleteAll`, `migrate`, MongoDB) |
| Native x86_64 | ✅ SQLite; MySQL wire real (13 faces ORM 22/09) | `sqlite:` DSN completo; MySQL wire protocol (scramble SHA-1 + lenenc + `user:pass@`) — handshake/COM_QUERY/prepared completos, medidos contra o MariaDB 12.3.2 |
| Native riscv64 | ✅ SQLite (riscv64) | `li a7` syscalls |
| JS | ✅ nao-tipado (16/09); `query<T>` tipado = `DB002` FECHADO 18/09 | `connect/execute/query/close/transaction` via `kof_platform.db*` no host GraalJS; query tipado: FECHADO 18/09; `orm.*` FECHADO 18/09 (`ORM001` — `KofJsOrmBridge`, mesmo SQL do JVM, E2E byte-parity) |

## 7. Testes (0.5.0-beta)

`KofDbE2ETest` 8 + `KofOrmE2ETest` 16 (inclui MariaDB/PostgreSQL/MongoDB com skip condicional + SQLite native) — execute + query JSON,
query tipada com bind, transação com commit, rollback em exceção,
credenciais; roundtrip nao-tipado + transaction no JS byte-parity com JVM (16/09, `DB001` fechado); `query<T>` no JS FECHADO 18/09 como `DB002` (bind no guest; SQLite nativo ✅).

## 8. Evolução planejada (residual)

- Query DSL tipada `User.query { where age > 18 }` (nível 3 DATABASE_VISION)
- Connection pooling + `kof.db`/`kof.orm` fora do JVM (JS via WASM, Native ORM sobre SQLite)
- ~~MySQL/MariaDB native completo~~ — ✅ x86-64 fechado 22/09 (auth scramble SHA-1 + `lenenc` + parse + todas as faces ORM; cross riscv64/aarch64 segue aberto)
  `user:pass@` done; falta handshake completo, query e prepared statements
- `repository<User>` / abstração de repositório

## 9. `kof.orm` (resumo)

O ORM da própria linguagem (`entity` na linguagem → DDL + CRUD). Full API,
backends e testes em `docs/stdlib/DATABASE_VISION.md`.

```kof
entity User {
    id: Long generated
    name: String
    email: String unique
    age: Int
}

main() {
    var db = db.connect("jdbc:h2:mem:app;DB_CLOSE_DELAY=-1")
    orm.create<User>(db)                                   // DDL do schema
    orm.save(db, User(0, "Mel", "mel@kof.dev", 30))        // insert/update
    var u = orm.find<User>(db, 1)                          // PK
    var adultos = orm.where<User>(db, "age", ">", 30)      // operadores
    orm.saveAll<User>(db, l)                               // batch (upsert por PK)
    var pg = orm.page<User>(db, 20, 40)                    // paginação
    var win = orm.window<User>(db, 20, 0)                  // Window<User> (import kof.pagination)
    println(orm.count<User>(db))
    orm.delete<User>(db, 1)
    orm.migrate(db, "add-phone", "ALTER TABLE user ADD phone VARCHAR")
}
```

| Chamada | Descrição |
|---------|-----------|
| `orm.create<T>(db)` | Gera o DDL a partir do `entity` |
| `orm.save(db, t)` / `orm.saveAll<T>(db, list)` | insert/update (upsert por PK) |
| `orm.find<T>(db, pk)` / `orm.all<T>(db)` | por PK / todas |
| `orm.where<T>(db, field, value[, op])` | filtro (op: `=` `>` `<` `>=` `<=` `!=` `LIKE`) |
| `orm.count<T>(db[, field, value])` | contagem (com filtro opcional) |
| `orm.page<T>(db, limit, offset)` | paginação (só linhas) |
| `orm.window<T>(db, limit, offset[, total])` | `Window<T>` linhas + flags de navegação; `total=true` adiciona um `COUNT(*)` lazy (exige `import kof.pagination`) |
| `orm.delete<T>(db, pk)` / `orm.deleteAll<T>(db)` | exclusão |
| `orm.migrate(db, name, sql)` | migration versionada (roda uma vez) |

Backends: SQL via JDBC (JVM) + **MongoDB** (driver oficial, E2E com
container real, skip condicional). Native x86-64 real (`kof_orm_*` em asm sobre o `kof_db_*` nativo; SQLite + MySQL wire; 13 faces, F2d1–F2d7 22/09; `ORM001` só no cross riscv64/aarch64); **JS FECHADO 18/09**
(`KofJsOrmBridge`, mesmo SQL do runtime JVM, E2E byte-paridade).