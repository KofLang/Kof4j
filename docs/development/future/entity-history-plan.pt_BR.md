[English](entity-history-plan.md) | [Português](entity-history-plan.pt_BR.md)

# Histórico de Entidades — auditoria temporal para `kof.orm` (especificação, plano apenas, zero código)

**Status:** proposta, zero código — `docs/development/future/`
**Solicitado por:** mantenedora (26/09/2026)
**Trava da regra 6:** a superfície de declaração e a superfície de consulta
propostas aqui são **decisões de projeto**. Antes de qualquer código, a
mantenedora trava uma entrada em `DECISIONS.md` (`D-ENTITY-HISTORY`) com a
superfície escolhida. Este documento é uma proposta, não uma autorização.
**Snapshot:** branch `beta-0.5.0`, tip `12bacc39e`. Todo `file:line` abaixo foi
medido nesse tip.

---

## 1. Contexto — o que Kof tem hoje (medido)

Kof já tem uma superfície de persistência real e multi-backend, mas **nada**
sobre histórico de entidade, revisões ou auditoria temporal existe em qualquer
lugar do repositório. Os fatos medidos relevantes:

- **Entidade é palavra-chave, não anotação.** `entity Name { campo: Tipo
  [generated] [unique] ... }` é parseada e rebaixada para **record**
  (`CompilerPipeline.java:228-229` preenche `driver.entitySchemas`, depois o
  lowering de record roda). Chamadas ORM tipadas exigem uma `entity` declarada;
  um `record` puro dá `ORM002` (`ExpressionOrmCallLowerer.java`).
- **A chave primária é implícita.** A PK é o primeiro campo marcado `generated`;
  sem nenhum, o primeiro campo declarado (`JvmOrmRuntime.java:89`,
  `kof_orm_pkIndex`). Não há palavra-chave de `@Id`/chave primária.
- **Superfície ORM** (`KofOrm.java:137`, `functions()`): `create, save, find,
  all, delete, count, deleteAll, where, saveAll, page, migrate` — mais a query
  DSL tipada `Entity.query(db) { where … orderBy … limit … }`
  (`CompilerOrmSupport.lowerQueryDsl`). Suporte por alvo: `orm.supportedOn`
  = JVM/ANDROID/JS, com `fnSupportedOn` estendendo faces para NATIVE e cross;
  combinações não suportadas recusam com `ORM001` (R6, nomeado).
- **Superfície de DB** (`KofDb.java:80`): `connect, query, execute, close,
  transaction`. Transações são um **bloco**, não uma chamada:
  `transaction { … }` — chamada com trailing-lambda
  (`parser/ExpressionParser.java:149`, rebaixada para `kof_db_transaction`),
  com blocos aninhados participando da transação externa
  (`jvm/JvmConfigRuntime.java:466-500`; Native `runtime/RuntimeDb4.java:50-83`).
- **Não há tipo timestamp.** `time.now()` retorna `Long` em **milissegundos
  epoch** (`KofTime.java:13`); datas são `String` ISO-8601 (`todayIso`,
  `parseDateIso`, `formatDateIso`). O mapa de tipos de coluna do ORM não tem
  tipo timestamp — um timestamp hoje é uma coluna `Long` ou `String`
  (`KofOrm.dbType`).
- **Não há hooks, listeners ou interceptors de ciclo de vida.** Os únicos
  mecanismos de extensão são do lado do compilador: o mapa de schema da
  `entity`, os passos internos de super-bridge/desugar/codegen, e a intrínseca
  de introspecção de tempo de compilação `interop.schema(R)`
  (`CompilerInterop.java:17-19` — explicitamente sem reflexão em runtime).
  Existem anotações `@Name`, mas são **apenas de interop**, nunca mecanismo de
  ORM (`training/idioms/database.md:11`).
- **Identidade/contexto existe, mas fragmentado.** `auth.user()/claims()/
  hasRole(…)` leem o bearer token do request atual (`KofSecurity.java:231-255`);
  `observability.requestId()/correlationId()/traceId()` existem, mas na JVM
  `requestId()` é um **UUID aleatório novo a cada chamada** e `correlationId()`
  apenas o chama (`jvm/JvmStringObsRuntime.java:223-229`) — **não** está ligado
  ao request HTTP, ao contrário do texto da doc. O id real por request
  (`KOF_LOG_REQUEST_ID`) é interno e não exposto como função Kof.
- **Serialização:** `json.encode(value) -> String`, `json.decode<T>(json) -> T`
  (`learn/stdlib/json.md`); linhas de DB voltam em formato JSON. Versionamento
  de schema existe para **DDL** (`orm.migrate`, `kof_migrations`), nunca para
  linhas.
- **Docs:** `learn/stdlib/orm.md`, `training/idioms/database.md`,
  `docs/stdlib/DATABASE_VISION.md`, `docs/development/db-parity-plan.md`,
  `DECISIONS.md` §`D-DB-GAPS`. `docs/stdlib/observability.md:134` menciona
  "future audit logging" uma vez, sem design.

**Conclusão do levantamento:** histórico de entidade seria um contrato
genuinamente novo. Nada da superfície atual precisa ser substituído — a feature
tem de ser **aditiva** e falar a língua existente do ORM/DB.

---

## 2. Problema

`created_at` / `updated_at` / `updated_by` respondem a uma pergunta — *quem
tocou esta linha por último, e quando* — e perdem todo o resto. São
insuficientes porque:

- **São lossy.** Só o último estado sobrevive; os estados intermediários
  desaparecem. "Qual era o preço em 14/09?" fica sem resposta.
- **Não atribuem uma mudança específica.** `updated_by` diz quem escreveu a
  linha por último, não quem mudou *qual* campo.
- **São frágeis sob transações multi-entidade.** Uma transação que toca cinco
  linhas deixa cinco timestamps sem relação; não há contexto de revisão
  compartilhado para agrupá-las.
- **São mutáveis.** Nada impede a aplicação (ou um bug) de escrever
  `updated_at` mentindo. Uma coluna de timestamp não é um registro.
- **Não sobrevivem ao DELETE.** Sumiu a linha, sumiu o `created_at` com ela.
- **Não descrevem a mudança de forma.** Quando um campo é removido ou
  renomeado, o valor antigo simplesmente some do schema — não há registro do
  que ele costumava ser.

A necessidade não é "log de compliance" como produto: é uma **capacidade da
camada de dados** — a camada de persistência consegue responder *"como esta
entidade estava, quando, e por quê"* com a mesma seriedade com que responde
*"como esta entidade está agora"*.

---

## 3. Objetivos

- Registrar o **ciclo de vida** de uma entidade auditada: criação (`INSERT`),
  mudança (`UPDATE`), remoção (`DELETE`).
- Dar a cada mudança registrada uma **revisão**, um **timestamp**, uma
  **operação**, um **ator** e, quando disponível, um **id de
  correlação/request**.
- Oferecer uma **superfície de consulta Kof-native**: histórico de uma
  entidade, estado em uma revisão, estado em um instante, mudanças entre
  revisões, campos alterados.
- Manter a capacidade **opt-in por entidade**; entidades auditadas e não
  auditadas coexistem no mesmo programa e na mesma transação.
- Ser **honesto por backend**: definir um contrato que um backend JDBC
  implementa primeiro, que o Native pode implementar depois, e que qualquer
  backend futuro possa implementar — sem o contrato da linguagem depender de
  JDBC, JPA ou Hibernate.
- Ser **aditivo e retrocompatível**: programas, entidades, bancos e migrations
  Kof existentes continuam funcionando sem toque.
- Fazer as escritas de auditoria **participarem das transações do app**
  (atomicidade não é opcional).
- Expor **diagnósticos honestos** (R6) quando um alvo/backend não puder
  fornecer histórico — nunca um no-op silencioso nem uma lista vazia fingindo
  ser "sem mudanças".

## 4. Não-objetivos

- **Não é event sourcing.** A tabela da entidade continua sendo a fonte de
  verdade do estado atual; o histórico é um registro derivado, não o write
  model.
- **Não é CQRS.** Sem projeção de read-model, sem separação comando/consulta.
- **Não substitui o log da aplicação.** `kof.log` e `kof.observability`
  mantêm seus papéis; o histórico de entidade não captura eventos arbitrários
  nem mensagens de texto livre.
- **Não é um produto de compliance.** Workflows de retenção/legal-hold/erasure
  são *consumidores* da capacidade, não parte do contrato central.
- **Não é Hibernate/JPA/Envers.** Sem anotações `@Audited`, sem `AuditReader`,
  sem `RevisionListener` programático — a superfície Kof é própria.
- **Não é amarrado a um banco.** O contrato é lógico; o armazenamento físico é
  decisão do backend (JDBC primeiro, outros depois).
- **Não é bitemporal.** A spec registra quando a escrita aconteceu (tempo de
  transação). "Tempo válido" vs "tempo de transação" (um modelo bitemporal
  completo) é uma **extensão futura** explícita, não o núcleo.
- **Não é uma feature de segurança.** Imutabilidade/PII/retenção são
  documentadas como limites e consumidores (§14), não resolvidas pelo núcleo.
- **Não é automático para toda entidade.** Apenas opt-in (§20).
- **Não é um substituto de `@Version`/optimistic locking do ORM.**
  Versionamento de linha para controle de concorrência é preocupação separada;
  esta spec não o define.

## 5. Motivação

A filosofia Kof é *intenção, não mecanismo*, e *a complexidade pertence à
plataforma*. Hoje um desenvolvedor Kof que precisa de histórico tem de
desnormalizar colunas de auditoria na mão, adicionar triggers no banco
(mecanismo, não intenção) ou puxar um framework Java — tudo isso quebra a
promessa multi-backend. Uma *intenção de histórico* de primeira classe na
entidade deixa o compilador e o backend implementarem o mecanismo uma vez, para
que o código de aplicação escreva o que quer dizer (`User.history(db, id)`),
não como reconstruí-lo.

Também fecha um gap arquitetural real: Kof já versiona seu **schema**
(`orm.migrate`) sem contraparte em nível de linha, e já tem os blocos
construtores (entidades como metadado de tempo de compilação, um bloco de
transação, serialização `array`, superfícies de ator/correlação) — a feature é
uma integração de peças existentes, não um universo novo.

## 6. Conceitos

| Conceito | Definição |
|---|---|
| **Entidade auditada** | Uma `entity` declarada portando a capacidade de histórico (opt-in). Entidades não auditadas não têm histórico nem overhead. |
| **Revisão** | Inteiro monotônico, por entidade, ordenando os estados registrados de uma entidade (`1, 2, 3, …`). Revisão `0`/ausente significa "sem histórico". |
| **Registro de auditoria** | Um fato armazenado: `(entidade, pkEntidade, revisão, operação, at, ator, correlationId, payload)`. |
| **Operação** | Uma de `INSERT`, `UPDATE`, `DELETE` (conjunto fechado no núcleo; extensão é Open Question). |
| **Ator** | Quem causou a mudança: um usuário autenticado, um serviço, um job, uma migration, um processo interno ou o sistema. Representado como um id `String` com um tipo (ver §11). |
| **Timestamp (`at`)** | Instante do registro, `Long` em milissegundos epoch (casando com `time.now()`), com ISO-8601 aceito como entrada de consulta. |
| **Estado histórico** | Os valores dos campos mapeados da entidade em uma revisão — um valor da própria forma record da entidade (ou `null` se ela não existia naquele ponto). |
| **Mudança de campo** | `(revisão, campo, anterior, atual)` — um campo mapeado que mudou em uma revisão. Valores são representados de forma portável (ver §10). |
| **Contexto de revisão** | O agrupamento de todos os registros de auditoria escritos por **uma transação**, identificado por um id de grupo e carregando o ator/correlation id da transação. |
| **Id de correlação** | Um id fornecido pela aplicação ou derivado do contexto, amarrando uma mudança a um request/workflow. Opcional; pode estar ausente. |

## 7. Modelo conceitual

### 7.1 Operações

- `INSERT` — a entidade é criada. O registro captura o estado **depois** da
  inserção (incluindo qualquer PK gerada). Revisão = 1 para aquela entidade.
- `UPDATE` — uma entidade existente é persistida com campos mapeados
  alterados. Revisão = anterior + 1. Uma atualização que nada muda **não**
  produz revisão (escritas no-op não são histórico).
- `DELETE` — a entidade é removida. Revisão = anterior + 1, e o registro
  captura o **último estado conhecido** para que o histórico da entidade e sua
  forma final permaneçam consultáveis após a remoção.

Restauração **não** é uma operação do núcleo: restaurar é "ler um estado
histórico e então `save` como uma nova revisão". Não precisa de API nova (§28
discute um açúcar opcional).

### 7.2 Estratégias de representação de estado

| Estratégia | O que é armazenado por revisão | Reconstrução |
|---|---|---|
| **Snapshot completo** | todos os valores dos campos mapeados da entidade | O(1) — lê o registro |
| **Apenas diff** | só os campos que mudaram | O(revisões) — replay desde a revisão 1 |
| **Snapshot + diff** | os campos alterados **e** um snapshot completo materializado | Leitura O(1), consulta de diff O(1) |

**Recomendado: snapshot-por-revisão (snapshot completo da entidade auditada)
com diffs computados pelo contrato na leitura.** Justificativa:

- **A reconstrução é O(1)** e não pode ser corrompida por um registro
  intermediário perdido — o estado é autocontido.
- **A evolução de schema é honesta** (§16): um snapshot antigo guarda
  exatamente os campos que existiam então; campos removidos permanecem
  visíveis, campos adicionados estão ausentes.
- **O diff é preocupação de leitura**, não de escrita: `changes(id)` compara
  snapshots consecutivos sem um segundo formato de armazenamento.
- **O custo de escrita é limitado** pela largura da entidade — aceitável para
  os tamanhos que Kof visa; entidades enormes são tratadas em §15 (opt-out de
  campos por entidade é extensão futura).
- Um backend **pode** adicionalmente materializar diffs ou deltas como
  otimização, desde que a semântica do contrato (abaixo) valha; a escolha
  física é do backend (§17).

Tombstones: `DELETE` guarda o snapshot final mais a operação `DELETE`, então
"estado na revisão N após a remoção" é respondível e "existia no instante T" é
`false` apenas para T depois do delete.

### 7.3 Identidade de revisão

Dois ids aparecem conceitualmente:

- **Revisão da entidade** (`1, 2, 3, …` por entidade): a ordenação voltada ao
  usuário usada por `at(id, revisão)` e `changes(id, from, to)`.
  **Recomendado** como semântica de revisão do contrato — monotônica
  por-ente e determinística, o que a torna portável entre backends com
  capacidades de sequência global diferentes.
- **Id do contexto de revisão** (um por transação): agrupa todos os registros
  escritos pela mesma transação; não é a "revisão" voltada ao usuário. Sua
  forma (contador global, UUID, `(at, grupo)`) é decisão do backend.

## 8. Arquitetura

O contrato é em camadas para que a linguagem nunca dependa de um driver:

```
                 Linguagem Kof (frontend do compilador)
                         |  entidades + intenção de histórico (metadado)
                 Contrato ORM Kof  ("o que histórico significa")
                         |
        +----------------+----------------+
        |                |                |
    Backend JDBC    Backend Native    Backend futuro
    (primeiro)      (depois)          (WASM/hosted/…)
```

- **Linguagem/frontend** possui: a declaração opt-in (metadado na entidade),
  as regras de tipo da superfície de consulta e os diagnósticos de tempo de
  compilação. Não sabe nada de SQL ou tabelas.
- **Contrato ORM** possui: a semântica do registro de `INSERT/UPDATE/DELETE`,
  a ordenação de revisões, a forma dos resultados de consulta, o acoplamento de
  atomicidade às transações, e o modelo de erro/capacidade. É expresso como
  metadado de compilador + pontos de entrada de runtime, exatamente como as
  faces `kof_orm_*` existentes.
- **Runtime** possui: ler o `time`, resolver o contexto de ator/correlação e a
  orquestração de escrita/leitura.
- **Backend** possui: o schema físico, índices, SQL/asm e qualquer otimização —
  escondido atrás do contrato.
- **Banco** possui: durabilidade, os tipos reais, constraints e armazenamento.

Isso preserva *"Kof não precisa possuir tudo; Kof precisa ser capaz de integrar
tudo"*: histórico é um **contrato de integração**, não um framework embutido.

## 9. Linguagem / API

> Tudo nesta seção é uma **proposta**, não uma API existente. O corpus atual usa
> chamadas entity-static para ORM tipado (`Entity.query(db)`) e funções de
> namespace (`orm.*`), então as duas opções abaixo são avaliadas contra essa
> convenção.

### 9.1 Declaração (opt-in)

**Proposta principal — um modificador de declaração, espelhando
`generated`/`unique` (sem anotações):**

```kof
audited entity User {
    id: Long generated
    name: String
    email: String
}
```

Alternativas avaliadas (ambas mais fracas):

- `audit User` como declaração avulsa — separa a capacidade da entidade, convida
  ao drift, e lê como um verbo sem sujeito.
- Anotação `@Audited` — viola `training/idioms/database.md:11` ("nunca
  anotações" para o ORM) e é invisível para o Native por design.

O modificador é compilado para o metadado da entidade (uma flag `audited` ao
lado de `generated`/`unique`), de modo que o compilador pode rejeitar chamadas
de histórico em entidades não auditadas em tempo de compilação (`HIST002`, §20).

### 9.2 Superfície de consulta

**Proposta principal — chamadas entity-static, consistentes com
`Entity.query(db)`:**

```kof
var rs: List<Revision>        = User.history(db, id)
var r:  Revision              = User.history(db, id, 3)          // uma revisão
var u:  User?                 = User.at(db, id, 3)               // estado na revisão
var u2: User?                 = User.atTime(db, id, "2026-09-26T12:00:00Z")
var cs: List<FieldChange>     = User.changes(db, id)             // todas as mudanças
var cs2: List<FieldChange>    = User.changes(db, id, 2, 5)       // entre revisões
var rs2: List<Revision>       = User.history(db, id, offset, limit) // paginação
```

Semântica e tipos de resultado:

| Chamada | Retorna | Notas |
|---|---|---|
| `E.history(db, id)` | `List<Revision>` | mais antiga → mais nova; vazia quando não há histórico |
| `E.history(db, id, revisão)` | `Revision?` | uma revisão; `null` quando ausente |
| `E.history(db, id, offset, limit)` | `List<Revision>` | paginação espelhando `orm.page<T>(db, offset, limit)` |
| `E.at(db, id, revisão)` | `E?` | estado mapeado naquela revisão; `null` se a revisão não existe |
| `E.atTime(db, id, iso)` | `E?` | estado a partir da última revisão até `iso`; `null` antes da existência |
| `E.changes(db, id)` | `List<FieldChange>` | mudanças por campo em todo o histórico |
| `E.changes(db, id, from, to)` | `List<FieldChange>` | intervalo inclusivo |

**Alternativa rejeitada:** um namespace global `audit.*`
(`audit.User.history(id)`). Introduz um segundo universo de nomes para algo que
a entidade já nomeia, e duplica a convenção entity-static. É documentada aqui
apenas porque o briefing a levantou.

**Tipos de retorno** (records conhecidos do compilador, propostos):
`Revision(revision: Long, at: Long, operation: String, actor: String?, correlationId: String?)` e
`FieldChange(revision: Long, field: String, previous: String?, current: String?)`.
Valores em `FieldChange` são **strings portáveis** (fragmentos JSON canônicos
para compostos) para que o contrato sobreviva a mudanças de tipo; acessores
tipados são extensão futura (§28). `E.at`/`E.atTime` retornam o próprio tipo
record da entidade (`E?`).

### 9.3 Semântica dos casos de borda

- **Entidade não existe hoje, mas tinha histórico:** `history`/`changes` ainda
  o retornam (o histórico sobrevive à linha); `at` retorna o estado naquele
  momento.
- **Após `DELETE`:** a revisão `DELETE` é a última; `at(última)`,
  `atTime(iso após delete)` retorna `null` (a entidade não existia), enquanto
  `at(revisão antes do delete)` retorna o estado antigo.
- **Entidade sem histórico (auditada mas nunca escrita, ou revisão 0):**
  `history`/`changes` retornam listas vazias; `at`/`atTime` retornam `null`.
- **Entidade não auditada:** erro de compilação `HIST002` — nunca uma lista
  vazia silenciosa.
- **Argumento inválido** (`revisão <= 0`, `from > to`, timestamp ISO
  malformado, `offset`/`limit` negativo): erro de programação `HIST005` em
  tempo de compilação quando constante, senão erro nomeado de runtime (§20).
- **Garantia de ordenação:** registros são retornados em ordem crescente de
  revisão.

## 10. Modelo de dados (contrato de persistência lógico)

O contrato exige duas estruturas lógicas; **os nomes são ilustrativos** — um
backend pode escolher quaisquer nomes físicos, tipos e índices:

```
audit_revision(
    revision_id      -- id do contexto de revisão (por transação)
    entity           -- nome da entidade auditada (string)
    entity_pk        -- valor da chave primária da entidade afetada (string portável)
    revision         -- revisão monotônica por entidade (1..n)
    operation        -- INSERT | UPDATE | DELETE
    at               -- milissegundos epoch
    actor            -- id do ator (nullable)
    actor_kind       -- user | service | job | migration | internal | system
    correlation_id   -- nullable
    payload          -- snapshot completo dos campos mapeados (texto JSON)
)
```

Informação mínima para reconstruir o histórico: nome da entidade, valor da PK,
revisão por entidade, operação, timestamp, ator(+kind), id de correlação e o
snapshot dos campos. Um backend precisa de garantia de unicidade em
`(entity, entity_pk, revision)` e de um índice suportando buscas
`(entity, entity_pk, revision)` e `(entity, entity_pk, at)`.

Nada no contrato exige um tipo de coluna específico, uma estratégia de PK
específica ou uma codificação JSON específica — apenas que o *leitor* consiga
produzir as formas de documento de §9.

## 11. Semântica do contexto (ator, timestamp, correlação)

Por registro de auditoria:

- **Timestamp** é tomado pelo **runtime no momento da escrita de auditoria da
  transação** (não pela aplicação), a menos que um backend precise derivá-lo.
  Todos os registros escritos por uma transação compartilham o mesmo timestamp
  e contexto de revisão — é isso que torna uma transação multi-entidade
  coerente.
- **Resolução do ator** (proposta):
  1. override explícito no escopo (um açúcar futuro, por ex. um bloco — §27);
  2. `auth.user()` quando o request está autenticado
     (`KofSecurity.java:231-255`), `actor_kind = user`;
  3. um default declarado pela aplicação para o escopo (job/migration/serviço),
     `actor_kind` conforme;
  4. caso contrário `actor_kind = system`, `actor = null` — **nunca** uma
     mentira.
- **Id de correlação**: do contexto de runtime quando existe; senão ausente.
  **Ressalva medida:** hoje `observability.correlationId()` na JVM é um UUID
  aleatório novo a cada chamada (`jvm/JvmStringObsRuntime.java:223-229`) e não
  está ligado ao request — ligá-lo (ou ao interno `KOF_LOG_REQUEST_ID`) a um
  contexto real de request é um **pré-requisito** e uma Open Question (§27), não
  um pressuposto desta spec.
- O contrato deve representar mudanças feitas por usuários, serviços, jobs,
  migrations, processos internos e o sistema — por isso `actor_kind` é parte do
  modelo lógico, não detalhe posterior.

## 12. Transações

- **Atomicidade é obrigatória.** Registros de auditoria são escritos **dentro
  da mesma transação de banco** que a mudança da entidade; um rollback desfaz o
  registro de auditoria junto. O bloco existente `transaction { … }`
  (`parser/ExpressionParser.java:149`, com aninhamento participante na JVM e no
  Native) é o carregador natural — nenhum primitivo de transação novo é
  proposto.
- **Múltiplas entidades em uma transação:** cada entidade afetada ganha sua
  própria revisão por entidade; todos os registros compartilham um **id de
  contexto de revisão**, um timestamp e o mesmo ator/correlation id.
- **A mesma entidade alterada várias vezes em uma transação:** a proposta é
  registrar **uma revisão por mudança de estado persistida** em ordem, ou —
  alternativa válida — colapsar no último estado com uma única revisão para a
  transação. Isto é uma **Open Question** (§27/Q4); a recomendação é "uma
  revisão por `save`", porque colapsar perde estados intermediários que a
  feature existe para preservar.
- **Uma transação que falha após escrever a entidade (antes do commit):** o
  registro de auditoria não é visível após o rollback; o contador de revisão
  não é consumido de forma que deixe buracos observáveis a leitores (buracos
  são aceitáveis estruturalmente, mas leitores devem ordenar por revisão, não
  contá-las).

## 13. Concorrência

- **Duas transações mudando a mesma entidade:** cada uma escreve dentro da sua
  própria transação de banco; a revisão é atribuída na hora da escrita. A
  garantia de unicidade em `(entity, entity_pk, revision)` força serialização
  (uma transação retenta ou falha deterministicamente) — isto tem de ser
  especificado pelo backend, nunca deixado a uma revisão perdida silenciosa.
- **Ordenação:** as revisões ordenam os estados; **timestamps iguais são
  permitidos** (duas transações podem compartilhar um milissegundo) e não devem
  ser usados para ordenar.
- **Isolamento:** o contrato herda o isolamento do banco; a spec exige apenas
  que **uma revisão commitada nunca seja mutada depois** e que escritores
  concorrentes não consigam intercalar dois estados sob uma mesma revisão.
- **Geração de id de revisão:** por entidade, atribuída atomicamente com a
  escrita (sequência/`MAX(revision)+1` sob uma constraint única, ou nativo do
  backend). Uma estratégia de geração do id de contexto de revisão global é
  específica do backend.
- **Leituras** devem ser consistentes: uma consulta de histórico nunca vê uma
  transação meio-escrita (está dentro/fora da transação de banco, conforme o
  isolamento).

## 14. Segurança e privacidade (limites, não feature completa)

- **Quem lê:** o histórico pode expor dados removidos e PII antiga — o acesso
  de leitura tem de ser uma **decisão de autorização separada** da leitura da
  entidade atual (proposta: o backend/aplicação guarda a chamada de histórico;
  o núcleo não inventa uma linguagem de política).
- **Quem escreve:** as escritas de auditoria são feitas pelo ORM/runtime; **não
  há API pública de escrita/edição/remoção para registros de auditoria** no
  contrato — essa é a principal medida anti-adulteração no nível da linguagem.
- **Adulteração:** o contrato proíbe atualizar o histórico; evidência real de
  adulteração (hash chains, assinaturas) é uma **extensão futura**, não núcleo.
- **Dados sensíveis:** uma entidade auditada pode conter segredos/PII. O
  requisito da spec é **exclusão por campo** (marcador `not audited` — extensão
  futura, §28) e a afirmação honesta de que, por padrão, o snapshot mapeado
  completo é registrado; **não** auditar material de credencial. Mascaramento,
  criptografia em repouso e redação são consumidores/preocupações do banco.
- **Retenção/erasure:** documentadas em §15 como opção de contrato, não política
  automática. Erasure estilo GDPR conflita com histórico imutável e é uma Open
  Question explícita (§27/Q10).

## 15. Desempenho e retenção

- **Overhead de escrita:** uma linha extra por mudança de estado persistida de
  uma entidade auditada (estratégia de snapshot), escrita na mesma transação.
  Entidades não auditadas pagam **zero**. O overhead esperado é proporcional à
  largura da entidade auditada, não ao tamanho da tabela.
- **Overhead de leitura:** `history`/`changes` são varreduras de intervalo
  indexadas em `(entity, entity_pk, revision)`; `at`/`atTime` são buscas de
  linha única; `atTime` usa adicionalmente o índice `(entity, entity_pk, at)`.
- **Paginação é obrigatória** na API (`offset/limit`) e deve ser empurrada pelo
  backend quando possível (sem materializar o histórico inteiro para uma
  página).
- **Entidades de alta frequência:** a estratégia de snapshot multiplica o
  armazenamento; as mitigações documentadas são (a) exclusão de campos,
  (b) materialização de delta pelo backend, (c) retenção/arquivamento — as duas
  primeiras são extensões futuras, a terceira é política de backend.
- **Contrato de retenção (opcional, deliberado):** o núcleo **não** expõe
  expiração automática. Se um backend suportar retenção, ela tem de ser
  **explícita** (uma migration/declaração), e leituras de histórico devem
  afirmar honestamente quando revisões foram expiradas (`HIST004`, §20) em vez
  de retornar silenciosamente um histórico mais curto.
  Arquivamento/particionamento são preocupações do backend (§17).

## 16. Evolução de schema (análise obrigatória)

Exemplo: `User { name }` → `User { name, email }`.

Como cada revisão guarda um **snapshot autodescritivo** indexado por nome de
campo, revisões antigas permanecem interpretáveis:

- **Campo adicionado:** snapshots antigos simplesmente não o têm;
  `at(revisãoAntiga)` retorna a entidade com o campo ausente/defaultado pelas
  regras do record, e `changes` reporta-o aparecendo quando foi escrito pela
  primeira vez (anterior = ausente).
- **Campo removido:** snapshots antigos ainda o carregam; a entidade mapeada
  atual não tem mais o componente, então o campo removido é visível apenas pelo
  payload bruto (um acessor `payload`/raw futuro, §28) ou por `changes`.
- **Campo renomeado:** sem uma declaração de mapeamento, isto lê como
  remoção+adição — honesto, ainda que ruidoso. Um mapeamento explícito de
  renomeação na migration é uma **extensão futura**; a spec exige que o
  contrato *permita* tal mapeamento sem mudar os payloads armazenados.
- **Tipo alterado:** `FieldChange` carrega representações `String` portáveis,
  então um valor `"42"` (Int) vs `"42"` (String) só é distinguível pelo payload
  bruto; acessores históricos tipados são extensão futura. O contrato não pode
  quebrar em mudanças de tipo — ele degrada para a representação portável.
- **Entidade removida:** as tabelas de histórico são independentes da tabela da
  entidade, então o histórico de uma entidade deletada **permanece consultável
  pelo nome da entidade**. Se remover a declaração deve purgar o histórico é
  **explicitamente não automático** (Open Question §27/Q9).
- **Relacionamento alterado:** o núcleo registra **campos escalares mapeados**
  da entidade auditada. Snapshots completos de grafo de relacionamentos
  (seguindo referências) estão **fora de escopo** do núcleo (Open Question
  §27/Q8) — a fronteira honesta é: auditar o valor da chave estrangeira, não o
  histórico da entidade referenciada.

## 17. Portabilidade por backend — quem possui o quê

| Camada | Possui |
|---|---|
| Contrato da linguagem | o modificador de declaração, a superfície de consulta, diagnósticos de compilação, formas de resultado |
| ORM Kof | a semântica de registro, ordenação de revisões, acoplamento transacional, capacidade/erros |
| Runtime | relógio, resolução de ator/correlação, orquestração |
| Backend | schema físico, SQL/asm, índices, primitivas de concorrência, otimizações |
| Banco | durabilidade, tipos, constraints, isolamento |

A linguagem **não pode** nomear tabelas, SQL, JPA ou JDBC. Consequência: uma
chamada de histórico de entidade compila uma vez e é resolvida por alvo; onde um
alvo não tem implementação, o compilador/runtime **recusa por nome**
(`HIST001`), exatamente como `ORM001`/`DB001` hoje (R6). O primeiro alvo de
implementação é **JDBC** (JVM, onde transações e o ORM já existem), mas essa
ordem é decisão de implementação, nunca parte do contrato.

## 18. Interoperabilidade

- **JDBC (JVM):** primeiro backend natural; SQL na mesma conexão das escritas
  da entidade, juntando-se a `transaction { … }`.
- **Native (x86-64, riscv64/aarch64):** as faces de DB/ORM já existem
  (`kof_db_*`, `kof_orm_*`, wire SQLite/MySQL); histórico é outra face de ORM,
  então a rota native é "emitir o mesmo contrato em asm/runtime", não um
  redesenho.
- **Alvos cross sem DB (JS/WASM/MCU):** recusa honesta (`HIST001`), com
  capacidade documentada por alvo.
- **Apps híbridos:** como o histórico é camada de dados, um serviço Kof e um
  serviço Java compartilhando o mesmo banco podem ambos consumir as tabelas de
  auditoria desde que concordem no schema físico (nota de compatibilidade, não
  obrigação do contrato).

## 19. Observabilidade

- Histórico de entidade **não substitui** `kof.observability`. A relação é
  complementar: histórico armazena linhagem de *entidade* no banco;
  observabilidade armazena linhagem de *operação* em traces/métricas/logs.
- O **id de correlação é a chave de junção**: quando tracing e histórico
  compartilham um correlation id, um trace pode apontar para as revisões que
  produziu, e uma revisão pode apontar para o seu trace.
- **Não duplicar responsabilidades:** o núcleo não registra mensagens de log,
  dados de span nem métricas. O único primitivo compartilhado é o correlation
  id — e hoje esse primitivo não é ligado ao request (§11), o que é um
  pré-requisito a corrigir na camada de observabilidade, não aqui.

## 20. Semântica de erro e modelo de capacidade

Novos códigos nomeados (proposta; numeração exata a atribuir com a mantenedora):

| Código | Classe | Significado |
|---|---|---|
| `HIST001` | capacidade | O alvo/backend não tem implementação de histórico de entidade (recusa nomeada, R6) |
| `HIST002` | compilação | Consulta de histórico em uma entidade não auditada |
| `HIST003` | runtime | Escrita de auditoria falhou (ex.: conflito de unicidade/serialização não retentado) |
| `HIST004` | runtime | A revisão pedida foi expirada/arquivada por política de retenção explícita |
| `HIST005` | compilação/runtime | Argumento inválido: `revisão <= 0`, `from > to`, ISO malformado, janela negativa |

Não-encontrado **não** é erro: `E.at`/`E.atTime` retornam `null`,
`history`/`changes` retornam vazio — consistente com `orm.find<T>` retornando
`T?`.

**Modelo de capacidade (proposta):**

- **Opt-in por entidade** (`audited entity`). Padrão **off** — compatibilidade.
- **Capacidade do alvo/backend:** obrigatória de ser *declarada*; ausência é
  recusa nomeada, nunca um no-op silencioso.
- **Capacidade do contrato ORM:** sempre presente (o que histórico significa não
  é opcional uma vez que a entidade é auditada).
- **Não obrigatório globalmente:** não há um switch "auditar tudo" no núcleo
  (uma extensão futura poderia adicionar um default de projeto).

## 21. Compatibilidade

- **Código Kof existente:** inafetado — o modificador e as chamadas são sintaxe
  nova; programas que não os usam compilam e rodam inalterados.
- **Entidades existentes:** não auditadas; zero overhead; nenhuma migration
  necessária.
- **Bancos existentes:** nenhuma mudança até uma entidade ser marcada como
  auditada e a estrutura de histórico ser criada (um passo explícito estilo
  `orm.migrate`, não um DDL implícito em runtime).
- **Migrations:** DDL aditivo (novas estruturas); migrations existentes
  intocadas.
- **Versões futuras:** a superfície de declaração e consulta é aditiva; o
  schema físico está atrás do contrato, então um backend pode mudar seu
  armazenamento sem mudar o código Kof.

## 22. Exemplos (ilustrativos; sintaxe segue o Kof atual)

> Estes exemplos são propostas. Seguem a superfície real onde ela existe
> (`entity`, `orm.*`, `db.connect`, `transaction { }`, `auth.*`) e a superfície
> de histórico proposta onde não existe.

**1 — entidade auditada + criação**

```kof
audited entity User {
    id: Long generated
    name: String
    email: String
}

main() {
    var db = db.connect("sqlite:app.db")
    orm.migrate(db, "create-users", "create table User(id integer primary key autoincrement, name text, email text)")
    var u = User(0, "Mel", "mel@example.com")
    orm.save(db, u)                    // revisão 1: INSERT
}
```

**2 — atualização**

```kof
transaction {
    var u = orm.find<User>(db, id)
    var changed = User(u.id, "Mel Santos", u.email)
    orm.save(db, changed)              // revisão 2: UPDATE (name)
}
```

**3 — histórico completo**

```kof
var rs = User.history(db, id)
for (var r in rs) {
    println(r.revision + " " + r.operation + " " + r.actor)
}
```

**4 — estado em uma revisão**

```kof
var at2: User? = User.at(db, id, 2)
if (at2 != null) {
    println(at2.name)
}
```

**5 — estado em um instante**

```kof
var then: User? = User.atTime(db, id, "2026-09-26T12:00:00Z")
```

**6 — mudanças entre revisões**

```kof
var cs = User.changes(db, id, 2, 5)
for (var c in cs) {
    println(c.field + ": " + c.previous + " -> " + c.current)
}
```

**7 — quem mudou qual campo**

```kof
for (var c in User.changes(db, id)) {
    println("rev " + c.revision + " " + c.field)
}
```

**8 — remoção**

```kof
orm.delete<User>(db, id)               // nova revisão: DELETE (snapshot final guardado)
```

**9 — histórico após a remoção**

```kof
var before = User.at(db, id, 2)        // ainda disponível
var now = User.at(db, id, User.history(db, id).size)   // null (estado deletado)
```

**10 — contexto de ator / correlação**

```kof
// dentro de uma rota web, auth.user() resolve o ator automaticamente
app.post("/users") {
    transaction {
        var u = User(0, body(), "x@example.com")
        orm.save(db, u)                // actor = auth.user(); correl. = contexto
    }
}

// uma migration/job declara o próprio ator (açúcar proposto, §28)
audit.as("migration-2026-09") {
    transaction { /* writes */ }
}
```

## 23. Estratégia de testes (quando implementado)

- **Registro:** INSERT/UPDATE/DELETE produzem exatamente uma revisão cada;
  update no-op não produz nenhuma; o snapshot final no DELETE é preservado.
- **Consultas:** ordenação e paginação de `history`; `at` por revisão;
  fronteiras de `atTime` (antes da existência, entre revisões, timestamp igual,
  após delete); intervalos de `changes` e pares de campos.
- **Transações:** commit escreve registros; rollback não escreve nenhum;
  transação multi-entidade compartilha o contexto de revisão; mesma entidade
  duas vezes (conforme decisão Q4); `transaction { }` aninhado participa.
- **Concorrência:** dois escritores na mesma entidade serializam; sem revisão
  perdida; timestamps iguais ainda ordenam por revisão.
- **Contexto:** ator autenticado, ator anônimo/sistema, override
  job/migration, correlation id presente/ausente.
- **Evolução de schema:** campo adicionado/removido/renomeado/tipo alterado;
  entidade removida; snapshot antigo ainda legível.
- **Capacidade:** `HIST001` em alvo não suportado; `HIST002` em entidade não
  auditada; `HIST004` onde a retenção é explícita.
- **Cross-backend:** o mesmo programa produz a mesma semântica de histórico em
  JDBC e (depois) Native; casos divergentes falham por nome, nunca em silêncio.
- **Compatibilidade:** suítes ORM existentes continuam verdes; entidades não
  auditadas não pagam nada; migrations permanecem aditivas.
- **Golden/por alvo:** resultados de consulta byte-estáveis e diagnósticos
  nomeados por alvo (padrão da casa: E2E com saída golden + matrizes de
  paridade de backend).

## 24. Rollout (incremental; não é compromisso)

- **Fase 1 — Contrato + metadado (esta spec).** Travar a superfície de
  declaração e consulta em `DECISIONS.md`; definir o modelo lógico e os
  códigos.
- **Fase 2 — Metadado + backend JDBC.** Metadado `audited entity`, as tabelas
  de histórico, registro em `INSERT/UPDATE/DELETE`, escritas dentro de
  `transaction { … }`.
- **Fase 3 — Consultas históricas.** `history`/`at`/`atTime`/`changes` +
  paginação na JDBC, com goldens E2E.
- **Fase 4 — Contexto.** Resolução real de ator e um correlation id ligado ao
  request (depende de um fix de observabilidade).
- **Fase 5 — Backend Native.** O mesmo contrato no runtime ORM native.
- **Fase 6 — Avançado.** Retenção/arquivamento, acessores históricos tipados,
  exclusão de campos, evidência de adulteração — cada um com sua decisão.

## 25. Comparação com Hibernate Envers (referência conceitual apenas)

Envers resolve o mesmo *problema* — auditar mudanças de entidade com revisões —
e esta spec toma emprestadas as **perguntas**, não as respostas:

| Aspecto | Envers | Esta proposta (Kof) |
|---|---|---|
| Declaração | anotações `@Audited` | `audited entity` (palavra-chave, opt-in) |
| Modelo de revisão | entidade de revisão global com um listener | revisão por entidade + contexto de revisão da transação |
| API de leitura | `AuditReader` (API Java imperativa) | chamadas entity-static Kof retornando records Kof |
| Armazenamento | tabelas `_AUD` geradas por entidade | estruturas escolhidas pelo backend atrás de um contrato |
| Acoplamento | ciclo de vida JPA/Hibernate | contrato ORM + `transaction { }`, independente de driver |
| Configuração | anotações, properties, listeners | superfície da linguagem + modelo de capacidade (R6) |

**Envers não é a API do Kof.** O conceito (revisões append-only com consultas
de estado) é; o mecanismo pertence ao backend.

## 26. Avaliação de nome

| Candidato | Avaliação |
|---|---|
| Temporal Entity Audit | acurado mas longo; "temporal" sugere semântica bitemporal que o núcleo não tem |
| ORM Audit | amarra o conceito a um subsistema; histórico é conceito de camada de dados |
| Entity Revision | descreve um artefato, não a capacidade; "revisão" é um conceito dentro dela |
| Entity History ✅ | **escolhido** — diz a intenção ("o histórico desta entidade") sem bagagem de framework emprestado; casa com a nomeação intent-first do Kof |
| Audit (sozinho) | sobrecarregado neste repo: `docs/audits/`, audit logging de segurança — ambíguo |

**Decisão:** o conceito é **Entity History**; a palavra-chave da superfície é
`audited`; o prefixo de consulta é a própria entidade (`User.history(db, id)`);
o documento é `entity-history-plan.md`. "Revisão", "registro de auditoria" e
"correlation id" permanecem nomes internos de conceito.

## 27. Open questions (decisões que a mantenedora precisa tomar)

- **Q1. Superfície de declaração:** `audited entity` vs `history entity` vs uma
  cláusula? (Recomendação: `audited entity`.)
- **Q2. Superfície de consulta:** entity-static (`User.history(db, id)`) vs um
  namespace `audit.*`? (Recomendação: entity-static, espelhando
  `User.query(db)`.)
- **Q3. Formas de retorno:** `Revision`/`FieldChange` são records conhecidos do
  compilador, ou `Map`/JSON puros? (Recomendação: records conhecidos; acessores
  históricos tipados depois.)
- **Q4. Mesma entidade mudada N vezes em uma transação:** uma revisão por
  `save`, ou uma revisão colapsada por transação? (Recomendação: por `save`.)
- **Q5. Semântica de revisão:** sequência por entidade (recomendado) vs id de
  revisão global exposto ao usuário?
- **Q6. Registrar `INSERT` mesmo quando a entidade é criada fora do ORM (só
  `orm.save`)?** E `db.execute` cru tocando uma tabela auditada? (Proposta:
  apenas caminhos ORM são auditados; SQL cru fora de contrato.)
- **Q7. Semântica de `atTime`:** última revisão até o instante (recomendado) vs
  primeira depois?
- **Q8. Relacionamentos:** auditar apenas valores de FK (recomendado) vs
  snapshots de grafo?
- **Q9. Remoção de entidade:** manter histórico consultável (recomendado) vs
  purgar na remoção da declaração?
- **Q10. Erasure/retenção:** como o contrato reconcilia histórico imutável com
  erasure de PII (mascaramento, crypto-shredding, isenção)? Opção de contrato
  apenas.
- **Q11. Pré-requisito do correlation id:** corrigir
  `observability.correlationId()` para ser ligado ao request, ou expor
  `KOF_LOG_REQUEST_ID`? Qual camada é dona?
- **Q12. Açúcar de override de ator:** necessário no núcleo, ou o default de
  app basta para a v1?
- **Q13. Estratégia de armazenamento:** snapshot-por-revisão é aceitável como
  baseline obrigatória, com materialização de diff opcional por backend?
  (Recomendação: sim.)
- **Q14. Exclusão de campos:** marcador `not audited` na entidade — núcleo ou
  extensão?
- **Q15. Numeração de códigos `HIST0xx`:** atribuir o bloco com a mantenedora e
  adicioná-lo à matriz de paridade.

## 28. Extensões futuras

- Exclusão de campos (`not audited`), acessores históricos tipados, acesso ao
  payload bruto, mapeamentos de renomeação para evolução de schema.
- Declarações de retenção/arquivamento/particionamento; política de
  soft-delete vs hard-delete.
- Modelo bitemporal (tempo válido vs tempo de transação).
- Evidência de adulteração (hash chain/assinatura) e crypto-shredding para
  erasure.
- Snapshots de grafo de relacionamentos e joins "as-of" entre entidades.
- Correlação com spans/métricas de `kof.observability` como junção de primeira
  classe.
- Um default de auditoria em nível de projeto (opt-out em vez de opt-in).

## 29. Evidência medida (file:line no tip `12bacc39e`)

- `kof-compiler/src/main/java/dev/kof/compiler/KofOrm.java:137` — funções ORM.
- `kof-compiler/src/main/java/dev/kof/compiler/KofOrm.java:47` — namespace
  `orm`.
- `kof-compiler/src/main/java/dev/kof/compiler/CompilerPipeline.java:228-229` —
  schemas de entidade registrados de `EntityDeclarationNode`.
- `kof-compiler/src/main/java/dev/kof/compiler/jvm/JvmOrmRuntime.java:89` —
  `kof_orm_pkIndex` (primeiro `generated`, senão primeiro campo).
- `kof-compiler/src/main/java/dev/kof/compiler/KofDb.java:80,110` — funções DB
  incl. `transaction`.
- `kof-compiler/src/main/java/dev/kof/compiler/parser/ExpressionParser.java:149`
  — chamada trailing-lambda `transaction { … }`.
- `kof-compiler/src/main/java/dev/kof/compiler/KofTime.java:13` — `time.now()`
  em milissegundos epoch.
- `kof-compiler/src/main/java/dev/kof/compiler/jvm/JvmStringObsRuntime.java:223-229`
  — `requestId()` aleatório por chamada; `correlationId()` delega a ele.
- `kof-compiler/src/main/java/dev/kof/compiler/CompilerInterop.java:17-19` —
  `interop.schema(R)` em tempo de compilação, sem reflexão em runtime.
- `docs/stdlib/observability.md:134` — a única menção a "future audit
  logging".
- Planos relacionados: `docs/development/pagination-plan` (windowing),
  `docs/development/memory-safety-plan.md`,
  `docs/development/db-parity-plan.md`, `docs/stdlib/DATABASE_VISION.md`,
  `DECISIONS.md` §`D-DB-GAPS`.

**Nenhuma implementação foi realizada.** Este documento é a alteração inteira.
