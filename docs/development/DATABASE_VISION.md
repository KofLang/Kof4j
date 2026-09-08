# Database Vision — Persistência como Parte da Linguagem

**Última atualização:** 2 de setembro de 2026
**Versão:** 0.2.6-beta
**Status:** Nível 0-2 e 4 implementados (`kof.db` + `kof.orm`, 0.2.6-beta):
`entity` (schema na linguagem), `orm.create/save/saveAll/find/all/where/
where-op/delete/deleteAll/count/count-filtrado/page/migrate` (JDBC no JVM:
H2, MySQL, MariaDB, PostgreSQL, SQLite; mappings de records; migrations
versionadas) + **MongoDB**; SQLite nativo via `libsqlite3.so.0` direto
(roundtrip E2E real); MySQL/MariaDB nativo via wire protocol em progresso
(auth scramble SHA-1 `kof_db_mysql_scramble` + `lenenc` + parse `user:pass@`
done; handshake completo/query/prepared pendentes); `VERSION` 0.2.6-beta;
build 810 testes.

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
- **Native MySQL/MariaDB (WIP):** wire protocol próprio sobre sockets nativos
  (auth scramble SHA-1 `kof_db_mysql_scramble` + `lenenc` + parse de
  `user:pass@` na DSN `mysql://[user[:pass]@]host[:port][/db]`) — em
  progresso: handshake completo, query e prepared statements pendentes;
  sem teste E2E contra servidor real ainda.
- **JS:** `DB001` (diagnóstico claro em compile-time).
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
| `orm.page<T>(db, limit, offset)` | paginação |
| `orm.delete<T>(db, pk)` / `orm.deleteAll<T>(db)` | exclusão |
| `orm.migrate(db, name, sql)` | migration versionada (tabela `kof_migrations`; cada migração roda uma vez) |

- **Backends SQL:** H2/SQLite/MySQL/MariaDB/PostgreSQL via JDBC (JVM).
- **MongoDB:** `save/find/all/where/delete/count` sobre o driver oficial via
  reflexão compatível (`Bson`/`Class`, sem `ClientSession`); teste E2E com
  container real (skip condicional; serviço Mongo no CI).
- **Native/JS:** reportam `ORM001` (gap documentado em compile-time).
- Testes: `KofOrmE2ETest` (16; entity, CRUD, `where` operadores, `migrate`,
  `unique`, PK não-numérica, MongoDB E2E, `ORM001`/`ORM002`).

O **nível 3 (Query DSL tipada)** `User.query(db) { where age > 18; orderBy
name; limit 10 }` foi implementado em 01/09: o compilador baixa o bloco para
`db.query<T>` (SQL preparada em compile-time, valores como binds) — `KofOrmE2ETest` 22.

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

O runtime pode gerenciar connection pool automaticamente. O programador não precisa configurar.

---

## Limitações Conhecidas

1. **Sem suporte a múltiplos bancos em uma mesma conexão** — foco inicial em
   um backend por `connect` (JVM: H2/MySQL/MariaDB/PostgreSQL/SQLite; Native:
   SQLite + MySQL WIP)
2. **Sem lazy loading** — pode ser adicionado futuramente
3. **Sem cache** — pode ser adicionado futuramente
4. **Migrations** — já implementadas de forma explícita + versionada
   (`orm.migrate`, tabela `kof_migrations`); sem auto-detecção de schema
   (o compilador conhece o `entity` em compile-time)
