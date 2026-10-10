[English](database.md) | [Português](database.pt_BR.md)

# Idioms — Database / ORM

**Status:** available · **Introduced:** 0.2.6-beta · **Updated:**  0.5.0-beta (Sep 2026) (02 Sep 2026)

## What it is

`kof.db` speaks SQL via prepared binds; `kof.orm` knows the entity schema at
**compile-time** (fields, types and constraints declared with `entity` — never
reflection, never annotations). The **Query DSL** (level 3, `ORM001`) expresses
typed queries without an SQL string:

```kof
entity User {
    id: Long generated
    name: String
    email: String unique
    age: Int
}

var db = db.connect("jdbc:h2:mem:app")
orm.create<User>(db)
orm.save(db, User(0, "Mel", "mel@kof.dev", 30))

// Query DSL: where / orderBy / limit — the compiler generates the SQL
var adultos = User.query(db) {
    where age > 25
    orderBy name asc
    limit 10
}
println(adultos.size)
println(adultos.get(0).name)
```

The lowering is target-agnostic (emits the same `db.query<T>` on JVM and
Native) and the E2E runs on JVM (H2). `KofOrmE2ETest` (22).

## Real API (verified in the compiler — 0.5.0-beta)

```kof
var db = db.connect("jdbc:h2:mem:app")
db.execute(db, "create table t(id int)")
db.execute(db, "insert into t values (?)", 1)
var rows = db.query<User>(db, "select * from t where id = ?", 1)

// ORM — CRUD over the schema
orm.create<User>(db)
orm.save(db, User(0, "Mel", "mel@kof.dev", 30))
var u = orm.find<User>(db, 1)
var all = orm.all<User>(db)
orm.where<User>(db, "age", ">", 25)        // + optional operator
orm.count<User>(db, "age", 30)
orm.delete<User>(db, 1)
orm.page<User>(db, 20, 0)        // (limit, offset) — 20 rows from the start
// orm.window needs `import kof.pagination`; see training/idioms/collections.md for Window<T>
var page = orm.window<User>(db, 20, 0)          // Window<User>: rows + nav flags
var counted = orm.window<User>(db, 20, 0, true) // 4th arg = opt-in total (lazy COUNT(*))
orm.deleteAll<User>(db)
orm.saveAll<User>(db, users)          // batch insert/update in one pass

// Query DSL (level 3) — multiple where = AND
User.query(db) {
    where age >= 25
    where age < 40
    orderBy age desc
    limit 10
}
```

## When to use

- Relational persistence: `db` for explicit SQL with binds; `orm` for CRUD
  over a typed entity; Query DSL for filters/ordering/limit without
  building an SQL string.
- The DSL's `where`/`orderBy` reference **columns** (entity field names) —
  the compiler validates against the schema.

## When not to use

- Do not build SQL by concatenating input when a `?` bind solves it
  (injection) — `db.execute`/`db.query` and the DSL already use binds.
- Do not use `List<entity>` + linear search when `orm.where`/Query DSL
  solve it.

## BAD — SQL as a string + no types

```kof
// ❌ SQL built with input concatenation (injection) + manual loop to filter
var sql = "select * from user where age > " + entrada
var rows = db.query(db, sql)
var ok = rows.filter((u: User) -> u.age > 25)
```

```kof
// ✅ prepared binds + Query DSL (intent, no input string)
var ok = User.query(db) {
    where age > entrada      // input becomes a `?` bind
}
```

## BAD — ORM without column validation

```kof
// ❌ a nonexistent column only fails at runtime (or worse: returns everything)
var r = orm.where<User>(db, "idade", 30)   // ORM003 at compile-time
```

```kof
// ✅ typed validation: `idade` is not a field of `User` → compile error
var r = orm.where<User>(db, "age", 30)
```

## Gaps (clear diagnostic, never silent fallback)

- Nonexistent column in the ORM/DSL `where` → `ORM003`.
- `where` without comparison, unsupported operator or >4 binds in the DSL → `ORM004`.
- ORM outside the JVM (Native) → `ORM001` at compile-time. JS CLOSED 18/09 (`KofJsOrmBridge`).
- Typed `db.query<T>` on JS → `DB002` (untyped connect/execute/query/transaction
  work on JS since `DB001` closed 16/09).
