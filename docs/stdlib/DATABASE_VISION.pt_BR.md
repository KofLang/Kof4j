[English](DATABASE_VISION.md) | [Português](DATABASE_VISION.pt_BR.md)

# Database Vision — Persistência como Parte da Linguagem

> **CONCLUÍDA — movida de `docs/development/` p/ `docs/stdlib/` em 12/09** (regra
> dos 3 estados: nada concluído fica em development/). Níveis 0–4 implementados e
> provados: Nível 3 (Query DSL tipada `User.query(db){...}` → `db.query<T>`) ✅
> 01/09 (`KofOrmE2ETest` 22); MySQL prepared binário ✅ 03/09
> (`KofDbE2ETest.nativeMysqlPreparedBinary`). Connection pooling é PLANNED (nenhuma pool hoje — cada `connect` abre sua própria conexão, §Limitações abaixo). DB001/ORM001 em
> (DB001 fechado: riscv/aarch 15/09 + JS 16/09; ORM001 fechado no JS 18/09); ORM **F1a+F1c+F1d+F3a+F2c3+F2a+F2b+F2c1+F2c2 reais desde 22-23/09 no Native cross riscv64/aarch64** (`deleteAll`/`count`/`create`/`migrate`/`count` com filtro/`delete`/`save`/`saveAll`/`find`/`all`/`where`/`where_op`/`page` sobre SQLite, peças `RtB50`/`RtB51`/`RtB52`/`RtB53`/`RtB54`/`RtB55`+`RtB55Helpers`/`RtB56`/`RtB57`+`RtB57Helpers`/`RtB58`/`RtB59`/`RtB60` + `NativeRiscvOrmCtors` por-programa (o resolver `kof_orm_ctors`); §447 corrigido: literal Bool boxeia `kof_box_bool` no Native; §449 corrigido: resolver x86 multi-entidade + leitura `Double` em `RuntimeOrm5/6/7/8` + paridade do `bind_key`), **o row-object do ORM completo no cross — nenhuma face `ORM001` resta** (ORM x86-64 real desde 22/09: 13/13 faces sobre SQLite + MySQL wire, F2d1–F2d7) tracked em `docs/backend-parity.md`,
> não pendência desta visão.

**Última atualização:** 12 de setembro de 2026
**Versão:** 0.5.0-beta
**Status:** Nível 0-2 e 4 implementados (`kof.db` + `kof.orm`, 0.4.0-beta):
`entity` (schema na linguagem), `orm.create/save/saveAll/find/all/where/
where-op/delete/deleteAll/count/count-filtrado/page/migrate` (JDBC no JVM:
H2, MySQL, MariaDB, PostgreSQL, SQLite; mappings de records; migrations
versionadas) + **MongoDB**; SQLite nativo via `libsqlite3.so.0` direto
(roundtrip E2E real); MySQL/MariaDB nativo via wire protocol em progresso
(auth scramble SHA-1 `kof_db_mysql_scramble` + `lenenc` + parse `user:pass@`
done; handshake completo/query/prepared pendentes); `VERSION` 0.5.0-beta;
build 3225 testes.

---

## O Problema com Hibernate/JPA

Hibernate resolve problemas reais:
- Mapeamento objeto-relacional
- Queries tipadas
- Lazy loading
- Transações
- Cache

Mas introduz complexidade massiva:
- EntityManager/Session
- Repository pattern
- JPQL
- Annotations (@Entity, @Column, @Id, etc.)
- XML de configuração
- DTOs artificiais
- Mapeamentos repetitivos

A pergunta é: **quanto disso existe porque Java não conhece banco de dados?**

---

## Filosofia Kof

> Se a linguagem pudesse definir entidades diretamente, precisaríamos de ORM?

### Entidades como Linguagem

```kof
entity User {
    id: Long generated
    name: String
    email: String unique
    age: Int
}
```

Isso define:
- Tabela `user`
- Colunas `id`, `name`, `email`, `age`
- `id` é auto-increment
- `email` tem unique constraint
- Tipos são mapeados automaticamente

### Queries Tipadas

```kof
// Query simples
users.find(1)

// Query com条件
users.where(User.age > 18)

// Query com joins
users.with(User.address).where(User.name == "Mel")
```

Sem:
- EntityManager
- Repository
- JPQL
- Criteria API
- Annotations

---

## Comparação

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

### Kof (PROPOSTA)

```kof
entity User {
    id: Long generated
    name: String
    email: String unique
    age: Int
}

// Queries são parte da linguagem
users.find(1)
users.where(User.age > 18)
```

---

## Arquitetura Proposta

### Nível 1: Schema Definition

```kof
entity User {
    id: Long generated
    name: String
    email: String unique
    age: Int
    createdAt: DateTime auto
}
```

O compilador:
1. Valida os tipos
2. Gera SQL DDL automaticamente
3. Cria mappings objeto-relacionais

### Nível 2: Query System

```kof
// Find by ID
var user = users.find(1)

// Where clause
var adults = users.where(User.age > 18)

// With joins
var usersWithAddress = users.with(User.address)
```

O compilador:
1. Valida tipos das queries
2. Gera SQL otimizado
3. Tipa o resultado

### Nível 3: Transactions

```kof
transaction {
    users.save(user)
    addresses.save(address)
}
```

### Nível 4: Migrations

```kof
migration "add_phone_to_users" {
    add Column("phone", String) to User
}
```

---

---

# Nível 0 — Implementado (kof.db)

```kof
var db = db.connect("jdbc:h2:mem:test")     // JVM: JDBC idiomático
db.execute(db, "create table users(id int, name varchar)")
db.execute(db, "insert into users values (?, ?)", 1, "Mel")
var rows = db.query<User>(db, "select * from users where id = ?", 1)
transaction {
    db.execute(db, "insert into users values (2, 'Kof')")
}
db.close(db)
```

- **JVM:** `connect(url)` / `connect(url, user, pass)`, `execute` (0-4 binds),
  `query` (0-4 binds, linhas em JSON) e `query<T>` (mapping de records),
  `transaction { }` (commit/rollback), `close` — JDBC (H2/MySQL/MariaDB/
  PostgreSQL/SQLite).
- **Native:** SQLite via link direto de `libsqlite3.so.0` (sem driver JDBC) —
  `db.connect("sqlite:/path.db")`, execute/query tipado, roundtrip E2E real
  (`nativeSqliteRoundtrip`).
- **Native MySQL/MariaDB:** wire protocol próprio sobre sockets nativos (real no x86-64: connect/prepared/query + as 13 faces ORM medidas contra o MariaDB 12.3.2, F2d1–F2d7 22/09)
  (auth scramble SHA-1 `kof_db_mysql_scramble` + `lenenc` + parse de
  `user:pass@` na DSN `mysql://[user[:pass]@]host[:port][/db]`) — em
  progresso: handshake completo, query e prepared statements pendentes;
  sem teste E2E contra servidor real ainda.
- **JS:** não-tipado (16/09, ponte no host GraalJS); `query<T>` tipado = `DB002` FECHADO 18/09 (bind no guest via `__kof_decode_<T>`).
- Testes: `KofDbE2ETest` (9) + `KofOrmE2ETest` (16, inclui MariaDB/PostgreSQL/
  MongoDB com skip condicional + SQLite nativo). O link nativo inclui a lib
  do MySQL apenas quando o programa a usa (DSN literal detectado em
  compile-time).

Limitações conhecidas do nível 0: bind máximo de 4 parâmetros; sem
connection pooling (cada `connect` abre conexão própria); sem timeouts/
retries/observability; `db.execute(db, ...)` exige o receiver como primeiro
argumento (a API idiomática será `db.execute(sql, binds...)`).

---

# Nível 1-2 — Implementado (kof.orm)

A `entity` na linguagem + CRUD tipado (o ORM da própria linguagem). O
compilador conhece campos, tipos e constraints em **compile-time** (nunca
reflection para descobrir o schema); `generated`, `unique` e PK não-numérica
são suportadas.

```kof
entity User {
    id: Long generated
    name: String
    email: String unique
    age: Int
}

main() {
    var db = db.connect("jdbc:h2:mem:app;DB_CLOSE_DELAY=-1")
    orm.create<User>(db)                                  // DDL do schema
    orm.save(db, User(0, "Mel", "mel@kof.dev", 30))       // insert/update
    var u = orm.find<User>(db, 1)                         // PK
    var adultos = orm.where<User>(db, "age", ">", 30)     // operadores
    orm.saveAll<User>(db, l)                              // batch (upsert por PK)
    var pg = orm.page<User>(db, 20, 40)                   // paginação (limit, offset)
    var win = orm.window<User>(db, 20, 0)                 // Window<User> (import kof.pagination)
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
| `orm.migrate(db, name, sql)` | migration versionada (tabela `kof_migrations`; cada migração roda uma vez) |

- **Backends SQL:** H2/SQLite/MySQL/MariaDB/PostgreSQL via JDBC (JVM).
- **MongoDB:** `save/find/all/where/delete/count` sobre o driver oficial via
  reflexão compatível (`Bson`/`Class`, sem `ClientSession`); teste E2E com
  container real (skip condicional; serviço Mongo no CI).
- **Native x86-64:** real (`kof_orm_*` em asm sobre o `kof_db_*` nativo; SQLite + MySQL wire). **Native riscv64/aarch64:** o **row-object do ORM está COMPLETO desde 23/09** — F1a+F1c+F1d+F3a+F2c3+F2a+F2b+F2c1+F2c2+F2c3 reais (`deleteAll`/`count`/`create`/`migrate`/`count` com filtro/`delete`/`save`/`saveAll`/`find`/`all`/`where`/`where_op`/`page` sobre SQLite, peças `RtB50`/`RtB51`/`RtB52`/`RtB53`/`RtB54`/`RtB55`+`RtB55Helpers`/`RtB56`/`RtB57`+`RtB57Helpers`/`RtB58`/`RtB59`/`RtB60` + `NativeRiscvOrmCtors` por-programa; §447 literal Bool boxeia `kof_box_bool` no Native; §449 resolver x86 multi-entidade + leitura `Double` em `RuntimeOrm5/6/7/8` + paridade do `bind_key`); **nenhuma face `ORM001` resta no cross** (MySQL ali é recusa runtime honesta, R7). **JS:** FECHADO
  18/09 — `kof.orm` roda no host GraalJS via `KofJsOrmBridge` (mesmo SQL de
  `JvmOrmRuntime`), records tipados bindados no guest (`__kof_decode_<T>`);
  E2E byte-paridade em `KofOrmE2ETest` (casos `js*`).
- Testes: `KofOrmE2ETest` (16; entity, CRUD, `where` operadores, `migrate`,
  `unique`, PK não-numérica, MongoDB E2E, `ORM001`/`ORM002`).

O **nível 3 (Query DSL tipada)** `User.query(db) { where age > 18; orderBy
name; limit 10 }` foi implementado em 01/09: o compilador baixa o bloco para
`db.query<T>` (SQL preparada em compile-time, valores como binds) — `KofOrmE2ETest` 32.

---

## Por Que Não ORM?

ORM tradicional:
- Mapeia objetos para tabelas
- Usa reflection para descobrir campos
- Requer annotations para configuração
- Introduz abstrações pesadas (Session, EntityManager)

Kof propõe:
- Entidades são definidas na linguagem
- O compilador gera o SQL
- Queries são tipadas e validadas em compile-time
- Sem reflection, sem annotations, sem abstrações pesadas

---

## Conexão com Banco de Dados

### Configuração

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

Planejado — não implementado. Hoje cada `db.connect` abre sua própria conexão física; uma pool gerenciada (reuso, sizing, timeouts) é um residual documentado desta visão, não o comportamento atual.

---

## Limitações Conhecidas

1. **Sem suporte a múltiplos bancos em uma mesma conexão** — foco inicial em
   um backend por `connect` (JVM: H2/MySQL/MariaDB/PostgreSQL/SQLite; Native:
   SQLite + MySQL wire x86-64 real)
2. **Sem lazy loading** — pode ser adicionado futuramente
3. **Sem cache** — pode ser adicionado futuramente
4. **Migrations** — já implementadas de forma explícita + versionada
   (`orm.migrate`, tabela `kof_migrations`); sem auto-detecção de schema
   (o compilador conhece o `entity` em compile-time)
