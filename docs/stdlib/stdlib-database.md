[English](stdlib-database.md) | [Português](stdlib-database.pt_BR.md)

# stdlib database — Kof Native Database

**Last updated:** September 18, 2026
**Version:** 0.5.0-beta
**Status:** implemented (Phase 5 of the Spring independence plan) — JVM (JDBC) + Native (SQLite via direct `.so` + MySQL wire real on x86-64, F2d1–F2d7 22/09) + `kof.orm` (JVM + MongoDB); JS untyped ✅ (16/09), typed `query<T>` ✅ (18/09, `DB002` closed), `kof.orm` ✅ on JS (18/09, `ORM001` closed); `ORM001` remains only on Native cross riscv64/aarch64

---

## 1. Philosophy

> Database access is a capability of the platform. JDBC is the internal
> mechanism (JVM interoperability); the exposed API is Kof-idiomatic — no
> `EntityManager`, `Session`, `PersistenceContext` or `@Transactional`.

## 2. API

```kof
main() {
    var db = db.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1")

    db.execute(db, "create table users(id int, name varchar(50))")
    db.execute(db, "insert into users values (?, ?)", 1, "Mel")
    db.execute(db, "insert into users values (?, ?)", 2, "Kof")

    // Untyped query: each row becomes a JSON object
    var rows = db.query(db, "select * from users where id = ?", 1)
    println(rows.get(0))     // {"id":1,"name":"Mel"}

    // Typed query: automatic bind to records/classes
    var users = db.query<User>(db, "select * from users order by id")
    println(users.get(0).name)

    // Transaction: automatic commit; rollback on error
    transaction {
        db.execute(db, "insert into users values (3, 'Ada')")
    }

    db.close(db)
}
```

## 3. Functions

| Call | Description |
|---------|-----------|
| `db.connect(url)` | Connects and returns the handle |
| `db.connect(url, user, pass)` | Connects with credentials |
| `db.execute(handle, sql[, args...])` | UPDATE/INSERT/DELETE; returns affected rows |
| `db.query(handle, sql[, args...])` | SELECT; `List<String>` — each row as JSON |
| `db.query<T>(handle, sql[, args...])` | SELECT; `List<T>` — bind by column name |
| `transaction { ... }` | Transactional block (uses the last connection) |
| `db.close(handle)` | Closes the connection |

- Bind with `?` placeholders; `Int/Long/Bool/String` args are converted
  automatically (boxing).
- Columns are normalized to lowercase (H2/Postgres return uppercase).
- Up to 4 bind arguments per call (fixed-arity overloads).

## 4. Example with the web stack

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

JDBC through `java.sql.DriverManager` — any JDBC driver on the classpath
works (H2, MySQL, MariaDB, PostgreSQL, SQLite) on the JVM. The driver is resolved
by the JDK's `ServiceLoader`; no library coupling in the Kof runtime.

Native:
- **SQLite** — direct link to `libsqlite3.so.0` (no JDBC driver), DSN
  `sqlite:/path.db`; `execute`/`query`/typed bind with real E2E roundtrip.
- **MySQL/MariaDB** — own wire protocol over native sockets (WIP):
  handshake + `mysql_native_password` auth (SHA-1 scramble, `kof_db_mysql_scramble`)
  + `lenenc` + parse of `user:pass@` in the DSN `mysql://[user[:pass]@]host[:port][/db]`.
  The link includes the MySQL lib only when the program uses it (literal DSN
  detected at compile-time). Full handshake, query and prepared statements
  still in progress (P3).

## 6. Targets (0.5.0-beta)

| Target | Status | Notes |
|--------|--------|-------|
| JVM | ✅ complete (JDBC) | `db.connect`/`execute`/`query<T>`/`transaction` (H2/MySQL/MariaDB/PostgreSQL/SQLite) + `orm.*` (entity, `saveAll`, `where` operators, `page`, filtered `count`, `deleteAll`, `migrate`, MongoDB) |
| Native x86_64 | ✅ SQLite; MySQL wire real (all 13 ORM faces 22/09) | `sqlite:` DSN complete; MySQL wire protocol (SHA-1 scramble + lenenc + `user:pass@`) — handshake/COM_QUERY/prepared complete, measured against MariaDB 12.3.2 |
| Native riscv64 | ✅ SQLite (riscv64) | `li a7` syscalls |
| JS | ✅ untyped (16/09); typed `query<T>` = `DB002` CLOSED 18/09 | `connect/execute/query/close/transaction` via `kof_platform.db*` on the GraalJS host; typed `orm.*` CLOSED 18/09 (`ORM001` — `KofJsOrmBridge`, same SQL as JVM, byte-parity E2E) |

## 7. Tests (0.5.0-beta)

`KofDbE2ETest` 8 + `KofOrmE2ETest` 16 (includes MariaDB/PostgreSQL/MongoDB with conditional skip + native SQLite) — execute + JSON query,
typed query with bind, transaction with commit, rollback on exception,
credentials; JS untyped roundtrip + transaction byte-parity with JVM (16/09, `DB001` closed); `query<T>` in JS CLOSED 18/09 as `DB002` (guest-side bind; Native SQLite ✅).

## 8. Planned evolution (residual)

- Typed query DSL `User.query { where age > 18 }` (level 3 DATABASE_VISION)
- Connection pooling + `kof.db`/`kof.orm` outside the JVM (JS via WASM, Native ORM over SQLite)
- ~~Complete native MySQL/MariaDB~~ — ✅ x86-64 closed 22/09 (SHA-1 scramble + `lenenc` + `user:pass@` + all ORM faces; cross riscv64/aarch64 still open)
  parse done; full handshake, query and prepared statements still missing
- `repository<User>` / repository abstraction

## 9. `kof.orm` (summary)

The language's own ORM (`entity` in the language → DDL + CRUD). Full API,
backends and tests in `docs/stdlib/DATABASE_VISION.md`.

```kof
entity User {
    id: Long generated
    name: String
    email: String unique
    age: Int
}

main() {
    var db = db.connect("jdbc:h2:mem:app;DB_CLOSE_DELAY=-1")
    orm.create<User>(db)                                   // schema DDL
    orm.save(db, User(0, "Mel", "mel@kof.dev", 30))        // insert/update
    var u = orm.find<User>(db, 1)                          // PK
    var adultos = orm.where<User>(db, "age", ">", 30)      // operators
    orm.saveAll<User>(db, l)                               // batch (upsert by PK)
    var pg = orm.page<User>(db, 20, 40)                    // pagination
    var win = orm.window<User>(db, 20, 0)                  // Window<User> (import kof.pagination)
    println(orm.count<User>(db))
    orm.delete<User>(db, 1)
    orm.migrate(db, "add-phone", "ALTER TABLE user ADD phone VARCHAR")
}
```

| Call | Description |
|---------|-----------|
| `orm.create<T>(db)` | Generates the DDL from the `entity` |
| `orm.save(db, t)` / `orm.saveAll<T>(db, list)` | insert/update (upsert by PK) |
| `orm.find<T>(db, pk)` / `orm.all<T>(db)` | by PK / all |
| `orm.where<T>(db, field, value[, op])` | filter (op: `=` `>` `<` `>=` `<=` `!=` `LIKE`) |
| `orm.count<T>(db[, field, value])` | count (with optional filter) |
| `orm.page<T>(db, limit, offset)` | pagination (rows only) |
| `orm.window<T>(db, limit, offset[, total])` | `Window<T>` rows + nav flags; `total=true` adds a lazy `COUNT(*)` (needs `import kof.pagination`) |
| `orm.delete<T>(db, pk)` / `orm.deleteAll<T>(db)` | deletion |
| `orm.migrate(db, name, sql)` | versioned migration (runs once) |

Backends: SQL via JDBC (JVM) + **MongoDB** (official driver, E2E with a real
container, conditional skip). Native x86-64 real (`kof_orm_*` asm over the
native `kof_db_*`; SQLite + MySQL wire; 13 faces, F2d1–F2d7 22/09; `ORM001` only
on cross riscv64/aarch64); **JS CLOSED 18/09**
(`KofJsOrmBridge`, same SQL as the JVM runtime, byte-parity E2E).
