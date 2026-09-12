# Idioms — Database / ORM

**Status:** available · **Introduced:** 0.2.6-beta · **Updated:** 0.3.22-beta (Sep 2026) (02 Sep 2026)

## What it is

`kof.db` fala SQL via binds preparados; `kof.orm` conhece o schema da
entidade em **compile-time** (campos, tipos e constraints declarados com
`entity` — nunca reflection, nunca annotations). O **Query DSL** (nível 3,
`ORM001`) expressa consultas tipadas sem string de SQL:

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

// Query DSL: where / orderBy / limit — o compilador gera a SQL
var adultos = User.query(db) {
    where age > 25
    orderBy name asc
    limit 10
}
println(adultos.size)
println(adultos.get(0).name)
```

O lowering é agnóstico de target (emite o mesmo `db.query<T>` no JVM e no
Native) e o E2E roda no JVM (H2). `KofOrmE2ETest` (22).

## API real (verificada no compilador — 0.3.22-beta)

```kof
var db = db.connect("jdbc:h2:mem:app")
db.execute(db, "create table t(id int)")
db.execute(db, "insert into t values (?)", 1)
var rows = db.query<User>(db, "select * from t where id = ?", 1)

// ORM — CRUD sobre o schema
orm.create<User>(db)
orm.save(db, User(0, "Mel", "mel@kof.dev", 30))
var u = orm.find<User>(db, 1)
var all = orm.all<User>(db)
orm.where<User>(db, "age", ">", 25)        // + operador opcional
orm.count<User>(db, "age", 30)
orm.delete<User>(db, 1)
orm.page<User>(db, 1, 20)
orm.deleteAll<User>(db)

// Query DSL (nível 3) — múltiplos where = AND
User.query(db) {
    where age >= 25
    where age < 40
    orderBy age desc
    limit 10
}
```

## When to use

- Persistência relacional: `db` para SQL explícito com binds; `orm` para CRUD
  sobre uma entidade tipada; Query DSL para filtros/ordenação/limit sem
  montar string de SQL.
- `where`/`orderBy` do DSL referenciam **colunas** (nomes de campo da
  entidade) — o compilador valida contra o schema.

## When not to use

- Não montar SQL por concatenação de entrada quando um bind `?` resolve
  (injeção) — `db.execute`/`db.query` e o DSL já usam binds.
- Não usar `List<entity>` + busca linear quando `orm.where`/Query DSL
  resolvem.

## BAD — SQL por string + sem tipos

```kof
// ❌ SQL montada com concatenação de entrada (injeção) + loop manual p/ filtrar
var sql = "select * from user where age > " + entrada
var rows = db.query(db, sql)
var ok = rows.filter((u: User) -> u.age > 25)
```

```kof
// ✅ binds preparados + Query DSL (intenção, sem string de entrada)
var ok = User.query(db) {
    where age > entrada      // entrada vira bind `?`
}
```

## BAD — ORM sem validação de coluna

```kof
// ❌ coluna inexistente só falha em runtime (ou pior: retorna tudo)
var r = orm.where<User>(db, "idade", 30)   // ORM003 em compile-time
```

```kof
// ✅ validação tipada: `idade` não é campo de `User` → erro no compile
var r = orm.where<User>(db, "age", 30)
```

## Gaps (diagnóstico claro, nunca fallback silencioso)

- Coluna inexistente no `where` do ORM/DSL → `ORM003`.
- `where` sem comparação, operador não suportado ou >4 binds no DSL → `ORM004`.
- ORM fora do JVM (Native/JS) → `ORM001` em compile-time.
- `db` no JS → `DB001`.
