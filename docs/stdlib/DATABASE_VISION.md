[English](DATABASE_VISION.md) | [Português](DATABASE_VISION.pt_BR.md)

# Database Vision — Persistence as Part of the Language

> **COMPLETED — moved from `docs/development/` to `docs/stdlib/` on 12/09** (rule
> of the 3 states: nothing completed stays in development/). Levels 0–4 implemented and
> proven: Level 3 (typed Query DSL `User.query(db){...}` → `db.query<T>`) ✅
> 01/09 (`KofOrmE2ETest` 22); binary prepared MySQL ✅ 03/09
> (`KofDbE2ETest.nativeMysqlPreparedBinary`). Connection pooling is PLANNED (no pool today — each `connect` opens its own connection, §Limitations below). DB001/ORM001 in
> (DB001 closed: riscv/aarch 15/09 + JS 16/09; ORM001 closed on JS 18/09); ORM **F1a+F1c+F1d+F3a+F2c3+F2a+F2b+F2c1+F2c2 real since 22-23/09 on Native cross riscv64/aarch64** (`deleteAll`/`count`/`create`/`migrate`/`count` with filter/`delete`/`save`/`saveAll`/`find`/`all`/`where`/`where_op`/`page` over SQLite, pieces `RtB50`/`RtB51`/`RtB52`/`RtB53`/`RtB54`/`RtB55`+`RtB55Helpers`/`RtB56`/`RtB57`+`RtB57Helpers`/`RtB58`/`RtB59`/`RtB60` + per-program `NativeRiscvOrmCtors` (the `kof_orm_ctors` resolver); §447 fixed: Bool literal boxes `kof_box_bool` on Native; §449 fixed: x86 resolver multi-entity + `Double` read on `RuntimeOrm5/6/7/8` + `bind_key` parity), **the ORM row-object complete on the cross — no `ORM001` face remains** (x86-64 ORM real since 22/09: 13/13 faces over SQLite + MySQL wire, F2d1–F2d7) tracked in `docs/backend-parity.md`,
> not a pending item of this vision.

**Last updated:** September 12, 2026
**Version:** 0.5.0-beta
**Status:** Levels 0-2 and 4 implemented (`kof.db` + `kof.orm`, 0.4.0-beta):
`entity` (schema in the language), `orm.create/save/saveAll/find/all/where/
where-op/delete/deleteAll/count/count-filtrado/page/migrate` (JDBC on the JVM:
H2, MySQL, MariaDB, PostgreSQL, SQLite; record mappings; versioned
migrations) + **MongoDB**; native SQLite via `libsqlite3.so.0` directly
(real E2E roundtrip); native MySQL/MariaDB via wire protocol in progress
(auth scramble SHA-1 `kof_db_mysql_scramble` + `lenenc` + parse `user:pass@`
done; full handshake/query/prepared pending); `VERSION` 0.5.0-beta;
build 3225 tests.

---

## The Problem with Hibernate/JPA

Hibernate solves real problems:
- Object-relational mapping
- Typed queries
- Lazy loading
- Transactions
- Cache

But it introduces massive complexity:
- EntityManager/Session
- Repository pattern
- JPQL
- Annotations (@Entity, @Column, @Id, etc.)
- Configuration XML
- Artificial DTOs
- Repetitive mappings

The question is: **how much of this exists because Java does not know about databases?**

---

## Kof Philosophy

> If the language could define entities directly, would we need an ORM?

### Entities as Language

```kof
entity User {
    id: Long generated
    name: String
    email: String unique
    age: Int
}
```

This defines:
- Table `user`
- Columns `id`, `name`, `email`, `age`
- `id` is auto-increment
- `email` has a unique constraint
- Types are mapped automatically

### Typed Queries

```kof
// Simple query
users.find(1)

// Query with condition
users.where(User.age > 18)

// Query with joins
users.with(User.address).where(User.name == "Mel")
```

Without:
- EntityManager
- Repository
- JPQL
- Criteria API
- Annotations

---

## Comparison

### Hibernate/JPA

```java
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "name", nullable = false, length = 50)
    private String name;
    
    @Column(name = "email", unique = true)
    private String email;
    
    // getters, setters, constructors...
}

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findByAgeGreaterThan(int age);
}
```

### Kof (PROPOSAL)

```kof
entity User {
    id: Long generated
    name: String
    email: String unique
    age: Int
}

// Queries are part of the language
users.find(1)
users.where(User.age > 18)
```

---

## Proposed Architecture

### Level 1: Schema Definition

```kof
entity User {
    id: Long generated
    name: String
    email: String unique
    age: Int
    createdAt: DateTime auto
}
```

The compiler:
1. Validates the types
2. Generates SQL DDL automatically
3. Creates object-relational mappings

### Level 2: Query System

```kof
// Find by ID
var user = users.find(1)

// Where clause
var adults = users.where(User.age > 18)

// With joins
var usersWithAddress = users.with(User.address)
```

The compiler:
1. Validates query types
2. Generates optimized SQL
3. Types the result

### Level 3: Transactions

```kof
transaction {
    users.save(user)
    addresses.save(address)
}
```

### Level 4: Migrations

```kof
migration "add_phone_to_users" {
    add Column("phone", String) to User
}
```

---

---

# Level 0 — Implemented (kof.db)

```kof
var db = db.connect("jdbc:h2:mem:test")     // JVM: idiomatic JDBC
db.execute(db, "create table users(id int, name varchar)")
db.execute(db, "insert into users values (?, ?)", 1, "Mel")
var rows = db.query<User>(db, "select * from users where id = ?", 1)
transaction {
    db.execute(db, "insert into users values (2, 'Kof')")
}
db.close(db)
```

- **JVM:** `connect(url)` / `connect(url, user, pass)`, `execute` (0-4 binds),
  `query` (0-4 binds, rows as JSON) and `query<T>` (record mapping),
  `transaction { }` (commit/rollback), `close` — JDBC (H2/MySQL/MariaDB/
  PostgreSQL/SQLite).
- **Native:** SQLite via direct linking of `libsqlite3.so.0` (no JDBC driver) —
  `db.connect("sqlite:/path.db")`, typed execute/query, real E2E roundtrip
  (`nativeSqliteRoundtrip`).
- **Native MySQL/MariaDB:** own wire protocol over native sockets (real on x86-64: connect/prepared/query + all 13 ORM faces measured against MariaDB 12.3.2, F2d1–F2d7 22/09)
  (auth scramble SHA-1 `kof_db_mysql_scramble` + `lenenc` + parsing of
  `user:pass@` in the DSN `mysql://[user[:pass]@]host[:port][/db]`) — in
  progress: full handshake, query and prepared statements pending;
  no E2E test against a real server yet.
- **JS:** untyped (16/09, bridge on the GraalJS host); typed `query<T>` = `DB002` CLOSED 18/09 (guest-side bind via `__kof_decode_<T>`).
- Tests: `KofDbE2ETest` (9) + `KofOrmE2ETest` (16, includes MariaDB/PostgreSQL/
  MongoDB with conditional skip + native SQLite). The native link includes the MySQL
  lib only when the program uses it (literal DSN detected at
  compile-time).

Known limitations of level 0: maximum bind of 4 parameters; no
connection pooling (each `connect` opens its own connection); no timeouts/
retries/observability; `db.execute(db, ...)` requires the receiver as the first
argument (the idiomatic API will be `db.execute(sql, binds...)`).

---

# Level 1-2 — Implemented (kof.orm)

The `entity` in the language + typed CRUD (the language's own ORM). The
compiler knows fields, types and constraints at **compile-time** (never
reflection to discover the schema); `generated`, `unique` and non-numeric PK
are supported.

```kof
entity User {
    id: Long generated
    name: String
    email: String unique
    age: Int
}

main() {
    var db = db.connect("jdbc:h2:mem:app;DB_CLOSE_DELAY=-1")
    orm.create<User>(db)                                  // schema DDL
    orm.save(db, User(0, "Mel", "mel@kof.dev", 30))       // insert/update
    var u = orm.find<User>(db, 1)                         // PK
    var adultos = orm.where<User>(db, "age", ">", 30)     // operators
    orm.saveAll<User>(db, l)                              // batch (upsert by PK)
    var pg = orm.page<User>(db, 20, 40)                   // pagination (limit, offset)
    var win = orm.window<User>(db, 20, 0)                 // Window<User> (import kof.pagination)
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
| `orm.migrate(db, name, sql)` | versioned migration (table `kof_migrations`; each migration runs once) |

- **SQL backends:** H2/SQLite/MySQL/MariaDB/PostgreSQL via JDBC (JVM).
- **MongoDB:** `save/find/all/where/delete/count` over the official driver via
  compatible reflection (`Bson`/`Class`, without `ClientSession`); E2E test with
  a real container (conditional skip; Mongo service in CI).
- **Native x86-64:** real (`kof_orm_*` asm over the native `kof_db_*`; SQLite + MySQL wire). **Native riscv64/aarch64:** the ORM **row-object is COMPLETE since 23/09** — F1a+F1c+F1d+F3a+F2c3+F2a+F2b+F2c1+F2c2+F2c3 real (`deleteAll`/`count`/`create`/`migrate`/`count`-with-filter/`delete`/`save`/`saveAll`/`find`/`all`/`where`/`where_op`/`page` over SQLite, pieces `RtB50`/`RtB51`/`RtB52`/`RtB53`/`RtB54`/`RtB55`+`RtB55Helpers`/`RtB56`/`RtB57`+`RtB57Helpers`/`RtB58`/`RtB59`/`RtB60` + per-program `NativeRiscvOrmCtors`; §447 Bool literal boxes `kof_box_bool` on Native; §449 x86 resolver multi-entity + `Double` read on `RuntimeOrm5/6/7/8` + `bind_key` parity); **no `ORM001` face remains on the cross** (MySQL there is an honest runtime refusal, R7). **JS:** CLOSED
  18/09 — `kof.orm` runs on the GraalJS host via `KofJsOrmBridge` (same SQL as
  `JvmOrmRuntime`), typed records bound guest-side (`__kof_decode_<T>`); byte-parity
  E2E in `KofOrmE2ETest` (`js*` cases).
- Tests: `KofOrmE2ETest` (16; entity, CRUD, `where` operators, `migrate`,
  `unique`, non-numeric PK, MongoDB E2E, `ORM001`/`ORM002`).

**Level 3 (typed Query DSL)** `User.query(db) { where age > 18; orderBy
name; limit 10 }` was implemented on 01/09: the compiler lowers the block to
`db.query<T>` (SQL prepared at compile-time, values as binds) — `KofOrmE2ETest` 32.

---

## Why Not ORM?

Traditional ORM:
- Maps objects to tables
- Uses reflection to discover fields
- Requires annotations for configuration
- Introduces heavy abstractions (Session, EntityManager)

Kof proposes:
- Entities are defined in the language
- The compiler generates the SQL
- Queries are typed and validated at compile-time
- No reflection, no annotations, no heavy abstractions

---

## Database Connection

### Configuration

```kof
config {
    database {
        url = "jdbc:postgresql://localhost/mydb"
        user = "admin"
        password = "secret"
    }
}
```

### Connection Pool

Planned — not implemented. Today each `db.connect` opens its own physical connection; a managed pool (reuse, sizing, timeouts) is a documented residual of this vision, not current behavior.

---

## Known Limitations

1. **No support for multiple databases in a single connection** — initial focus on
   one backend per `connect` (JVM: H2/MySQL/MariaDB/PostgreSQL/SQLite; Native:
   SQLite + MySQL wire x86-64 real)
2. **No lazy loading** — can be added in the future
3. **No cache** — can be added in the future
4. **Migrations** — already implemented explicitly + versioned
   (`orm.migrate`, table `kof_migrations`); no schema auto-detection
   (the compiler knows the `entity` at compile-time)
