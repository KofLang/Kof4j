[English](pagination-plan.md) | [Português](pagination-plan.pt_BR.md)

# Paginação nativa — intenção de janela de dados de primeira classe (plano de implementação)

**Status:** CONCLUÍDO 29/09 — promovido 28/09/2026 de `future/` para
`docs/development/` (`D-PAGINATION`, `D-FUTURE-BATCH-2809B`, `D-FUTURE-PROMOTION`),
fatias P0→P6 todas landed, movido para `docs/stdlib/` (regra dos 3 estados).
**Solicitado por:** mantenedora (23/09/2026)
**Gate da regra 6:** RESOLVIDO — a mantenedora travou `DECISIONS.md`
§`D-PAGINATION` (superfície abaixo em §19/§20). Este documento agora registra o
estado real + como terminar; não é mais "só plano".
**Superfície travada (28/09/2026):** tipo de janela `Window<T>`; introduzir o tipo
novo; `total` por flag na chamada de janela; max default global; a parte
in-memory começa agora (monta na fase 1 do `D-MULTIPARADIGMA-GO`); manter
`orm.page` ao lado de `orm.window` (sem bump); `offset` só no método windowed
(não no DSL tipado); helper HTTP `pageRequest(...)` em `kof.web`.
**Como terminar:** seguiu a ordem §20 P0→P6 (cada fatia provada de forma independente) — COMPLETO 29/09, sem outra fatia declarada. P1 (`slice`/`take`/`drop`) + P2 (`Window<T>` + `window(...)`)
POUSARAM 28/09; **P4 (`orm.window`) + P6 (docs/corpus) POUSARAM 29/09**, e a
**P5 (`kof.web.pageRequest`) POUSOU 29/09** (`D-PAGINATION-P5-SHAPE`) — P0→P6
todos fechados. **P3 está FORA da superfície travada** — ver a correção abaixo.
**Correção (29/09/2026):** §7.4/§10/§15/§16/§21/§22 e a antiga P3 planejavam um
token `offset` na DSL tipada. A superfície travada pela mantenedora
(`D-PAGINATION`, `DECISIONS.md` §D-PAGINATION) diz o oposto: `offset` vive **só
no método windowed, nunca na DSL tipada**. Um token na DSL muda a gramática
congelada (regra 6), então fica adiado para uma NOVA decisão da mantenedora; a
sequência executável é P4→P5→P6. As menções a DSL-offset abaixo são notas
históricas de projeto, não escopo.
**Snapshot:** recon original no tip `63c7b15d8`; remeça todo `file:line` antes de
uma fatia pousar. Branch ativa = `lab` (`D-BRANCH-PIPELINE`).

---

## 1. Contexto

Aplicações backend — a classe de apps que uma equipe construiria com Spring Boot —
repetem a mesma cerimônia em todo endpoint de lista: parsear `page` / `limit` /
`offset`, validar, clampar, calcular o offset, montar `LIMIT ? OFFSET ?`, carregar
as linhas e depois calcular `hasNext` / `total`. A filosofia do Kof ("intenção,
não mecanismo") diz que essa repetição é complexidade acidental que pertence à
plataforma.

O objetivo é uma **abstração de linguagem/stdlib para a intenção "acessar uma
janela de dados"**, desacoplada tanto do HTTP quanto do SQL:

```
expressão Kof (filter -> order -> pagination)  ->  materialização
```

- sobre uma conexão DB, ela deve empurrar `LIMIT`/`OFFSET` para o SQL;
- sobre um `List` em memória, deve ser uma visão/derivação dos dados já materializados;
- sobre HTTP, deve ser alcançável sem o core saber de HTTP.

Este plano investiga o estado **real** do repositório primeiro, e então propõe um
design incremental e compatível com a arquitetura. Ele **não** implementa nada.

---

## 2. Problema

1. **Nenhuma intenção de janela de primeira classe.** A única primitiva de
   paginação em todo o código é `orm.page<T>(db, limit, offset)`
   (`JvmOrmRuntime.java:450-471`, `KofJsOrmBridge.java:296-302`,
   `RuntimeOrm8.java`, `NativeRiscvAsmRtB60.java`). Ela devolve um `List<T>` cru,
   **sem metadados** (`hasNext`/`total`), então todo chamador recalcula.
2. **A DSL tipada de query não tem offset.** `Entity.query(db) { where …; orderBy f;
   limit N }` — o parser aceita só `where`/`orderBy`/`limit`
   (`ExpressionParser.java:505-537`), a AST `QueryDslExpr` tem `limit` e **nenhum
   `offset`** (`QueryDslExpr.java:4-9`), e o gerador de SQL emite apenas ` LIMIT n`
   (`CompilerOrmSupport.java:170-178`). Páginas além da primeira são impossíveis na DSL.
3. **`orm.all` / `orm.where` / `orm.where_op` são ilimitados.** Emitem
   `SELECT * FROM "t"` sem janela, então uma tabela grande é sempre carregada
   inteira (`JvmOrmRuntime.java:273-315`).
4. **Nenhum helper HTTP.** `query("page")` devolve `String?`
   (`KofWeb.java:242-244`, `JvmRuntimeWebDispatch.java:492-495`); todo handler
   escreve o próprio `if (p != null) { var n = p.toInt() }` sem default, sem
   clamp, sem max. E o **JVM NÃO faz URL-decode** dos valores de query enquanto o
   JS faz (`JvmWebCoreRuntime.java:139-145` vs `JsRuntimeUiWeb.java:238-246`) —
   um bug de paridade que qualquer helper deve reconciliar primeiro.
5. **Inconsistência de superfície existente.** O catálogo LSP documenta
   `orm.page(String entity, Object offset, Object limit)` (`StdCatalog.java:235`)
   mas a ordem do runtime é `(limit, offset)` (`KofOrm.java:178-181`). Uma
   armadilha latente que precisa ser corrigida antes de construir em cima.

---

## 3. Estado atual encontrado no Kof (medido)

### 3.1 Collections e pipelines

- `listOf`/`mapOf`/`setOf`; métodos de List despachados em `CollectionCallLowerer.java:82-278`.
- **A única op de janela hoje é `List.subList(begin, end)`** — semiaberta,
  materializa uma cópia (`CollectionCallLowerer.java:96`; JVM `JvmOpCollections.java:184-197`).
- As higher-orders são **só** `map`/`filter`/`reduce` em `List<T>`, todas **eager e
  alocando** (`CollectionCallLowerer.java:16-56`; JVM `JvmStringMiscRuntime.java:71-114`).
  Sem sequência lazy, sem fusão.
- `sort()` é só ordem natural; **não há Comparator no Kof** por design
  (`CollectionMethodGates.java:30-31`; `training/idioms/collections.md:62-93`).
- Cada op de collection é espelhada em **três camadas** (typer
  `MemberCallTyper`/`CollectionMethodTyper`, `MethodCallTyper`, lowerer
  `CollectionCallLowerer`) mais shims por alvo — fato que uma op nova deve respeitar.

### 3.2 DB / ORM

- `kof.db` (`KofDb.java`) passa o SQL cru do usuário para `prepareStatement`; SQL
  gerado vive só no ORM e na DSL de query.
- `orm.page` é o **único** pushdown de LIMIT/OFFSET e já é real em todas as pernas
  (JVM/JS/x86 SQLite+MySQL/riscv64+aarch64).
- `MAX_BIND = 4` (`KofDb.java:38`) limita os binds de `where + limit`
  (`CompilerOrmSupport.java:181-189`).
- A superfície DB é declarada **congelada** (`docs/stdlib/db-parity-plan.md:186-192`;
  `DECISIONS.md` §D-DB-GAPS addendum `:2356-2385`) → adicionar um offset ou mudar
  o tipo de retorno de `orm.page` é **regra 6**.

### 3.3 HTTP / JSON

- Nenhum tipo de usuário `WebRequest`/`WebResponse`: handlers leem funções de
  contexto (`param/query/header/body/method/path`) apoiadas em `ThreadLocal`
  (`JvmRuntimeWebDispatch.java:174`). Resposta = valor de retorno + `status()`/`headerSet()`.
- **Nenhum helper de paginação, nenhum envelope de resposta, nenhum header
  padrão** em código ou docs (buscado `meta`/`links`/`hasMore`/`totalPages`).
- `kof.json` serializa records/listas arbitrários no JVM/JS
  (`JvmRuntimeJson.java:162-181`); no Native os objetos são compostos em
  compile-time e `List<Record>`/`Map<String,T>` são gaps `JSN004`
  (`ExpressionJsonCallLowerer.java:140-208`).

### 3.4 Restrições (ver §6)

- **R1** a fronteira da stdlib é machine-gated (`scripts/check_stdlib_boundary.sh` +
  `scripts/stdlib_boundary.txt`); o namespace `data` é **HARD-DENY**
  (`stdlib_boundary.txt:11`). Collections são **nível de linguagem**, não um namespace.
- **G-01** congela a semântica de collections; superfície nova que a toque = regra 6.
- `PLAN-MULTIPARADIGMA.md` já planeja `take/drop/distinct/sorted` (Fase 1,
  permitida) e `Sequence<T>`/`queryOf`/backend SQL (Fases 3–7, **barradas pelo
  fechamento de SYSTEMS/Tier 1**).
- **R12**: nenhuma frente nova abre antes de SYSTEMS fechar, exceto `D-UNIVERSAL` e
  `D-BAREMETAL-BOOT`. A paginação deve pegar carona numa frente aberta ou receber decisão.
- Qualquer superfície pública nova → **MINOR + Decision ID** (`D-VERSIONING-RELEASE`).

---

## 4. Objetivo

Tornar **"acessar a janela `(limit, offset)` de uma fonte de dados"** uma intenção
de primeira classe, neutra de alvo, que:

1. seja o **mesmo conceito** para um `List` em memória e uma query de DB;
2. **empurre** `LIMIT`/`OFFSET` para o SQL quando a fonte for um DB (sem carga total);
3. nunca materialize antes da janela quando a fonte suporta;
4. devolva metadados de janela (`hasNext`, `hasPrevious`, `total` opcional) sem
   `COUNT(*)` implícito;
5. seja alcançável sobre HTTP **sem** o core depender de HTTP;
6. não bloqueie uma evolução futura para **cursor/keyset**.

---

## 5. Não objetivos

- **Não é um framework de paginação** (sem beans `Pageable`/`PageRequest`/`Sort`
  no sentido Spring, sem abstrações de repositório).
- **Não é um namespace pesado novo.** `kof.data` é proibido (R1); a frente DB pega
  carona na linha `orm` já existente, a frente em memória é nível de linguagem.
- **Não é um motor genérico de `Sequence<T>` lazy** agora — isso é
  `PLAN-MULTIPARADIGMA` Fases 3–7, barradas.
- **Não é cursor/keyset** na v1 (contrato preparado, implementação depois).
- **Não acoplado ao HTTP**: o helper HTTP é açúcar sobre o conceito central, nunca o conceito.
- **Não é uma API `COUNT(*)`-sempre**: `total` é opt-in.

---

## 6. Decisões arquiteturais existentes relevantes

| Decisão / regra | Restrição sobre este plano |
|---|---|
| `D-KOF-FIRST` (regra 10) | APIs externas (JDBC, Spring, SQL) não são oráculos; o contrato Kof é desenhado internamente e depois expresso. |
| Lei da Simplicidade (regra 11) | a superfície deve ser mais curta/clara que o boilerplate que remove; sem cerimônia. |
| R1 + `stdlib_boundary.txt` | namespace novo ⇒ linha no ledger + camada, ou o build falha; `data` hard-denied. Preferir **nenhum namespace novo**. |
| G-01 collections congeladas | adicionar métodos ao `List` / mudar seu contrato = regra 6. |
| `D-DB-GAPS` addendum | DB/ORM é congelado; face nova herda **paridade nos 4 alvos** com diagnósticos R6 honestos. |
| `D-VERSIONING-RELEASE` | superfície pública nova = MINOR + Decision ID. |
| R12 / `roadmap.md` §23 | a frente abre só com decisão ou pegando carona em `db-parity-plan` / `PLAN-MULTIPARADIGMA` Fase 1. |
| `roadmap.md` §24 / `D-RELEASE-1.0` | um item novo em `future/` é um item aberto para o EXIT GATE 1.0 (aceito, pois a mantenedora pediu). |
| `PLAN-MULTIPARADIGMA.md` | janela em memória alinha com a Fase 1; a ordem lazy/backend SQL é normativa (Fases 3–7) e não pode ser pulada. |

---

## 7. Design proposto

### 7.1 O conceito central: uma janela, não um número de página

```
Window<T> = { items: List<T>, offset: Int, limit: Int,
              hasPrevious: Bool, hasNext: Bool, total: Long? }
```

- **`items`** — a janela materializada (possivelmente vazia).
- **`offset`/`limit`** — o pedido normalizado que a produziu.
- **`hasPrevious`** — `offset > 0`.
- **`hasNext`** — exato quando `total != null` (`offset + items.size < total`);
  senão **otimista** (`items.size == limit`), documentado explicitamente.
- **`total`** — `null` a menos que o chamador peça. **Nunca** dispara um
  `COUNT(*)` implícito.

Por que `Window`, não `Page`: `Page` implica número de página + total (formato
Spring); `Window` expressa a intenção Kof (uma visão offset/limit) e fica neutro
para um cursor futuro. (`Page`/`Slice` são rejeitados como nome estrangeiro; regras 8/9.)

### 7.2 Onde cada peça mora

| Camada | Peça | Razão |
|---|---|---|
| **nível de linguagem (collections)** | record `Window<T>` + ops de janela no `List` (`slice`/`take`/`drop`, `window(limit, offset)`) | collections são nível de linguagem; cabe na Fase 1 do `PLAN-MULTIPARADIGMA` |
| **plataforma `kof.orm`** | faces de query com janela devolvendo `Window<T>`; `offset` na DSL SQL; `total` só via `count` explícito | pega carona na linha `orm` do ledger; DB é plataforma |
| **plataforma `kof.web`** | helper `pageRequest(...)` lendo `?page/limit/offset`, validado | açúcar; mantém o core livre de HTTP |
| **futuro** | `CursorRequest` alimentando o mesmo `Window<T>` | keyset, decisão depois |

Nenhum namespace novo é introduzido → gate R1 intacto.

### 7.3 Abordagens comparadas

| | **A. `Window<T>` + composição (recomendada)** | **B. estender assinaturas, sem tipo novo** | **C. `Sequence<T>` lazy** |
|---|---|---|---|
| Em memória | `l.window(limit, offset) -> Window<T>` | `l.slice(offset, limit) -> List<T>` | `seq.skip(offset).take(limit)` |
| DB | `orm.window<T>(db, limit, offset)` + DSL `offset` | adiciona `offset` na DSL; mantém `orm.page` devolvendo `List` | espinha de query lazy |
| Metadados | no `Window<T>` | chamador recalcula | na espinha |
| Superfície nova | um record (+ ops) → regra 6 | uma keyword na DSL + talvez sem ops → menor | grande (middle-end de IR) |
| Filosofia | intenção explícita, reusa `subList`/`orm.page` | menor superfície, mais boilerplate | mais poder, mais complexidade |
| Gate | OK após `D-PAGINATION` | OK após `D-PAGINATION` | barrada até SYSTEMS (Fases 3–7) |

**Recomendação: A**, com **B** como fallback estritamente mínimo caso a
mantenedora rejeite um tipo novo. **C** é a sucessora futura, não a v1.

### 7.4 Superfície proposta (proposta — nomes são questões em aberto, §19)

```kof
// em memória (nível de linguagem)
val w = users.window(20, 40)          // Window<User>; hasNext/hasPrevious; total = null
val w2 = users.window(20, 40, true)   // total computado localmente (size), opt-in

// DB (plataforma kof.orm) — empurra LIMIT ? OFFSET ? para baixo
val w3 = orm.window<User>(db, 20, 40)             // sem COUNT(*)
val w4 = orm.window<User>(db, 20, 40, true)       // COUNT(*) opt-in p/ total
// e a DSL tipada ganha offset:
val q = User.query(db) {
    where(active = true)
    orderBy(name) asc
    limit(20) offset(40)
}

// HTTP (plataforma kof.web) — lê ?page=&limit= / ?offset=&limit=, validado
val w5 = pageRequest(20)              // limite default; clamp; rejeita entrada ruim
```

O core (`Window<T>`, `List.window`) não sabe nada de SQL nem de HTTP.

---

## 8. Contratos / semântica

| Questão | Decisão (proposta) |
|---|---|
| tipo de `limit` | `Int` (OFFSET/LIMIT de SQL são 32-bit na prática); overflow documentado |
| tipo de `offset` | `Int`, `>= 0` |
| `limit`/`offset` negativos | **rejeitar** com erro nomeado (`throw "PAGINATION: limit/offset must be >= 0"`), nunca clamp silencioso |
| `limit == 0` | válido → janela vazia; `hasNext` diz se há mais; possibilita leitura só de metadados |
| `offset > size` | janela vazia, `hasPrevious = true`, `hasNext = false`; **sem erro** |
| limite máximo | **não** imposto pela primitiva core/DB (um cap escondido é comportamento silencioso); o **helper HTTP** impõe um max configurável (constante default, ex. 1000) |
| ordem determinística | paginar sem `orderBy` é não determinístico; a face DB documenta e DEVE emitir diagnóstico ao janelar um `all`/`where` sem ordem (`ORM00x`) |
| total | só quando pedido explicitamente (`orm.window(..., true)` / size local); nunca `COUNT(*)` implícito |
| precisão de `hasNext` | exata com `total`; otimista sem ele (documentado, testável) |

---

## 9. Integração com collections

- Reusar `List.subList` como base (`JvmOpCollections.java:184-197`); `window` =
  normalizar + `subList` + envolver em `Window<T>`.
- Ops novas no List (`slice`/`take`/`drop`/`window`) entram nas **três camadas
  espelhadas** de typer/lowering (`MemberCallTyper`, `CollectionMethodTyper`,
  `CollectionCallLowerer`) e no shim de cada alvo (JVM/Native x86+riscv/JS/Script).
- É exatamente o território da **Fase 1** do `PLAN-MULTIPARADIGMA` (ops eager de
  List), permitido antes de SYSTEMS; o caminho lazy fica na Fase 3+.

## 10. Integração com DB

- Adicionar `offset` na DSL tipada: parser (`ExpressionParser.java:505-537`), AST
  (`QueryDslExpr`), gerador de SQL (`CompilerOrmSupport.java:170-178`).
- Adicionar `orm.window<T>(db, limit, offset[, total])` devolvendo `Window<T>`,
  construída sobre a forma **existente** `kof_orm_page` (`SELECT * … LIMIT ? OFFSET ?`)
  mais `kof_orm_count` para o total opt-in. As quatro pernas já têm a forma SQL.
- Manter o comportamento de `orm.page` (congelado) ou supersedê-lo via decisão;
  corrigir a inversão de `StdCatalog.java:235` na mesma unidade.

## 11. Pushdown / otimização

- DB: a janela é emitida como `LIMIT ? OFFSET ?` **antes** da materialização; o
  ORM já faz isso para `page` e o mesmo gerador é estendido à DSL e ao `window`.
- Em memória: os dados já estão materializados, então paginar é uma **visão**; não
  se faz alegação de pré-materialização (honesto).
- O caminho lazy futuro (`PLAN-MULTIPARADIGMA` Fase 3/7) é onde
  `filter → order → pagination → materialization` fica realmente adiado e
  SQL-generativo; este plano não pula essa ordem.
- `MAX_BIND = 4` (`KofDb.java:38`): uma combinação `where` + `limit` + `offset`
  precisa caber no cap de 4 binds, ou o cap é revisitado sob a decisão.

## 12. Integração HTTP (opcional, desacoplada)

- Um helper `kof.web` `pageRequest(defaultLimit[, maxLimit])` lê a query string,
  valida/clampa e devolve o valor de pedido **do core** — nenhum tipo HTTP vaza
  para o core.
- O handler devolve `json.encode(w)`; o envelope é a própria forma `Window<T>`,
  então JVM/JS a codificam de graça e o Native precisa do conjunto de campos
  passando nos gates de schema JSON (`JSN002`/`JSN004` — `Window<Record>` pode
  cair em `JSN004`; documentado e diagnosticado, nunca silencioso).
- **Pré-requisito: FEITO 29/09** — a divergência de decode de query JVM/JS foi
  corrigida (`JvmWebCoreRuntime.WebRequest.query` agora espelha o
  `decodeURIComponent`: `%XX` UTF-8 estrito, `+` literal, malformado → erro;
  prova `KofWebE2ETest#queryValueIsPercentDecoded` + `KofWebJsE2ETest`).
- Headers padrão de paginação (`X-Total-Count`, `Link`) **não** são obrigatórios;
  se desejados, são decisão separada de `kof.web`.

## 13. Compatibilidade entre alvos

| Alvo | `Window<T>` em memória | `orm.window` (pushdown SQL) | Helper HTTP |
|---|---|---|---|
| JVM / Android | real | real (JDBC) | real |
| Native x86-64 | real | real (sqlite; mysql x86) | gap (residual `WEB001`) |
| Native riscv64/aarch64 | real | real (sqlite via `RtB60`) | gap |
| JS | real | real (bridge) | real |

`Window<T>` é um record normal → sem IR nova. `orm.window` herda a obrigação de
paridade total do `D-DB-GAPS` com diagnósticos R6 honestos enquanto uma fatia pousa.

## 14. Evolução futura para cursor/keyset

- O carrier é genérico (`Window<T>` com `items`/metadados), então keyset pode reusá-lo.
- Adicionar um tipo de pedido **separado** depois (`CursorRequest(limit, afterKey)`)
  e uma face `orm.after<T>(db, limit, key)`; o resultado continua `Window<T>`, com a
  chave do último item disponível ao chamador.
- O contrato inicial portanto **não deve codificar "offset" no nome do tipo**
  (razão para preferir `Window` a `OffsetPage`), e `hasNext` deve ser derivável de
  `items` + `limit`, o que é.
- Keyset é uma **decisão regra 6 própria**; este plano só garante que não é bloqueado.

## 15. Impacto no compiler / IR / backend

- **Ops de collection** (`slice`/`take`/`drop`/`window`): typer + lowering + shims
  dos 4 alvos (sem mudança de op de IR; baixam para `kof_list_*`/`subList` existentes).
- **record `Window<T>`**: declarativo (`record`), sem IR nova, mas o JSON no Native
  precisa da tabela de schema (`NativeJsonSchema.java`) cobrindo os campos.
- **DSL DB `offset`**: parser + AST + lowerer de SQL (compiler).
- **`orm.window`**: tabela `KofOrm` + faces de runtime JVM/JS/x86/riscv (compiler +
  fatias de runtime), espelhando `kof_orm_page`.
- **Helper HTTP**: `KofWeb` contexto/whitelist + gates por alvo + runtimes.
- Ou seja, é **majoritariamente trabalho de compiler/backend, não stdlib pura**; só
  a semântica de valor de `Window<T>` é "tipo stdlib".

## 16. Testes necessários

- Unit/E2E por op: casos de borda de `slice`/`take`/`drop` (0, exatamente-size,
  offset==size, offset>size, negativo) nos 4 alvos.
- Metadados de `Window<T>`: `hasNext`/`hasPrevious` nos dois regimes (com/sem total).
- DB: roundtrip de `orm.window` no JVM (H2/SQLite) + Native (sqlite sob qemu) + JS;
  verificar que `LIMIT ? OFFSET ?` está realmente no SQL executado (spy/assert).
- DSL: `limit(N) offset(M)` compila e devolve as linhas certas; `ORM00x` em entrada ruim.
- HTTP: `pageRequest` parseia `?page=3&limit=20`, clampa, rejeita entrada ruim;
  teste de paridade de URL-decode JVM/JS.
- Paridade cross-target: mesmo programa → mesma saída observável em JVM/Native/JS,
  ou gap declarado.
- Sem `COUNT(*)` implícito: garantir que nenhuma query de count é emitida a menos
  que pedida.

## 17. Compatibilidade / regressão

- **Aditivo** onde possível; se o tipo de retorno de `orm.page` mudar, é bump
  deliberado + migração conforme o congelamento.
- A correção de `StdCatalog.java:235` (inversão offset/limit) é correção de doc
  compatível para trás.
- Zero regressão nas suítes web/DB/collections existentes é o gate de merge.

## 18. Riscos

1. **Scope creep para framework** — mitigado pelo contrato `Window` e pelos não objetivos.
2. **Paginação não determinística** sem ordenação — precisa do diagnóstico.
3. **Pressão no cap de binds** (`MAX_BIND=4`) — pode forçar uma decisão.
4. **Gaps de JSON no Native** para `Window<Record>` (`JSN004`) — precisa diagnóstico.
5. **Paridade HTTP/native** (JVM sem URL-decode) — corrigir primeiro.
6. **Gate R12/1.0** — frente/item aberto novo; precisa da decisão da mantenedora.
7. **Congelamento de `orm.page`** — mudá-lo é regra 6; a abordagem B evita a quebra.

## 19. Questões em aberto (para a mantenedora)

1. **Nome**: `Window<T>` (recomendado) vs `Slice<T>` vs `Page<T>`?
2. Tipo novo mesmo, ou Abordagem B (estender assinaturas, sem tipo)?
3. `total`: flag `orm.window(..., true)`, ou exigir sempre uma chamada explícita
   `orm.count`?
4. Max de limite default + é global ou por endpoint?
5. A parte em memória é permitida agora sob `PLAN-MULTIPARADIGMA` Fase 1, ou a
   frente inteira espera SYSTEMS?
6. `orm.page` é superseded (bump) ou mantido ao lado de `orm.window`?
7. `offset` vai para a DSL tipada, ou só o método com janela?
8. Nome/forma do helper HTTP (`pageRequest(default[, max])`) e se ele pertence ao
   `kof.web` ou a uma função de contexto `kof.pagination`. **RESOLVIDO 29/09** —
   `D-PAGINATION-P5-SHAPE`: `kof.web.pageRequest(default[, max]): PageRequest`
   (record core, `query` ambiente), host `kof.web` separado.

## 20. Implementação em fases/fatias

Cada fatia é provável de forma independente; nenhuma pousa sem teste e docs.

- **P0 — Recon + decisão (este documento).** Corrigir `StdCatalog.java:235` (FEITO
  28/09: catálogo corrigido para `(entity, limit, offset)` + trava de regressão
  `StdCatalogSignaturesTest#ormPageCatalogDocumentsLimitBeforeOffset`); travar
  `D-PAGINATION`.
- **P1 — Janela em memória (nível de linguagem).** `slice`/`take`/`drop` no `List`
  (três camadas + 4 shims). FEITO 28/09: `kof_list_take/drop/slice` em
  JVM/JS/interpretador + nativo x86-64/riscv64/aarch64 (clamping honesto,
  negativo = erro nomeado `PAGINATION`); prova `PaginationSliceE2ETest` 7/7
  (cross riscv/aarch rodou) + `KofScriptStdlibParityTest#paginationSliceParity`.
  *(_Pega carona na Fase 1 do `PLAN-MULTIPARADIGMA`; ainda sem tipo novo._)
- **P2 — valor `Window<T>` + `window(limit, offset[, total])`.** FEITO 28/09:
  `record Window<T>` + `window<T>(List<T>, Int, Int): Window<T>` e
  `window<T>(List<T>, Int, Int, Bool): Window<T>` escritos EM KOF
  (`dev/kof/pagination.kf`, library-first por `D-KOF-FIRST-IMPL`), injetados FLAT
  no `import kof.pagination` explícito (`CompilerPagination.java`, mesmo mecanismo
  de `kof.supervisor`/`kof.workflow`/`kof.interop` — `Window`/`window` definidos pelo
  usuário colidem e pulam a injeção). Inferência aninhada de type-args pousou junto
  (`substituteTypeVariableIn` recursivo em `CompilerTypes` + binding por argumentos
  em `MethodCallTyper#tloFns` — corrige `Box<Int>.items()` e `window(l,2,1)` emitindo
  `List<T>`/`Window<T>` no Native, que boxeava um `int` cru no `println` e dava
  SIGSEGV via `kof_box_to_string`). Prova: `PaginationWindowE2ETest` 7/7 (JVM/JS/Script/
  nativo x86-64 + cross riscv64/aarch64, 0 skips) + vizinhos 150/150. P4
  próxima (não iniciada).
- **P3 — offset no DB pela DSL tipada: FORA DE ESCOPO.** A superfície travada
  (`D-PAGINATION`) mantém `offset` fora da DSL tipada (um token de gramática é
  mudança de superfície congelada, regra 6). Adiado para uma NOVA decisão da
  mantenedora; não implementar a partir deste plano. O offset windowed no DB é a
  P4 abaixo.
- **P4 — `orm.window<T>(db, limit, offset[, true])`** nas quatro pernas, reusando
  `kof_orm_page`/`kof_orm_count`. **LANDED (29/09)** por
  `D-PAGINATION-P4-LOWERING`: o lowerer de ORM dessuga `orm.window` no helper
  Kof `windowPage(...)` injetado sobre `orm.page`/`orm.count` (library-first; sem
  runtime por alvo para o record `Window`; `windowPage` NÃO re-fatiar — o SQL já
  paginou; `windowBounds` valida antes do SQL; o `orm.count` da forma de 4 args é
  lazy/opt-in). Prova: `PaginationOrmWindowE2ETest` 5/5 — paridade JVM(H2) +
  Native x86(sqlite) + JS + erro nomeado negativo + import obrigatório.
- **P5 — helper HTTP `pageRequest(...)`** + correção da paridade de decode de query.
  **LANDED (29/09)** por `D-PAGINATION-P5-SHAPE`: o host virtual Kof `kof.web`
  (injetado no `import kof.web`) fornece `pageRequest(defaultLimit[, maxLimit]):
  PageRequest`, devolvendo o core `PageRequest(Int page, Int limit, Int offset)` e
  lendo o `query("page"/"limit"/"offset")` ambiente; page 1-based, clamp em
  `maxLimit`, `?offset=` explícito vence, erro nomeado `PAGINATION:` em entrada
  ruim (handler mapeia para 400 via `status(400, e)`). Prova:
  `PaginationPageRequestE2ETest` 8/8 (page/limit→offset, defaults, clamp, offset
  explícito, page=0, não-inteiro, offset negativo, import obrigatório) +
  `KofWebE2ETest` 28/28 (zero regressão).
- **P6 — Docs/corpus** (`training/idioms/database.md`, `collections.md`,
  `docs/stdlib/stdlib-database.md`, `DATABASE_VISION.md`), sincronizar a
  assinatura de `orm.page` em todo lugar. **LANDED (29/09)** — `training/idioms/
  collections.md` + `database.md` (EN+PT) trazem `Window<T>`/`window`/`orm.window`;
  `docs/stdlib/stdlib-database.md` + `DATABASE_VISION.md` (EN+PT) documentam
  `orm.window<T>(db, limit, offset[, total])`; a assinatura de `orm.page` ja era
  `(db, limit, offset)` em todo lugar.
- **Futuro (decisão separada) — cursor/keyset** e a espinha lazy do
  `PLAN-MULTIPARADIGMA`.

> **Decisão resolvida (29/09 — `D-PAGINATION-P4-LOWERING`):** a mantenedora
> escolheu **(b)** — o lowerer de ORM dessuga `orm.window` no helper Kof
> `windowPage(...)` sobre `orm.page`/`orm.count`, então nenhum runtime por
> alvo constrói o record `Window<T>`.
>
> **Decisão resolvida (29/09 — `D-PAGINATION-P5-SHAPE`):** a mantenedora escolheu
> a forma de retorno core explícita — o `pageRequest(default[, max]): PageRequest`
> de `kof.web` lê o `query(...)` ambiente e devolve o record core `PageRequest`;
> o §19 Q8 está FECHADO, então a P5 não tem ponto de design aberto.

## 21. Critérios de aceite por fase

- **P1:** `slice/take/drop` corretos para `0`, exatamente-size, `offset==size`,
  `offset>size`, negativo→erro nomeado, em JVM/Native/JS/Script; suíte verde.
- **P2:** metadados de `Window<T>` exatos com `total`, otimistas sem; JSON
  encode/decode em JVM/JS e Native (ou `JSN004` declarado); sem `COUNT(*)`.
- **P3:** `offset` na DSL produz linhas certas; SQL contém `LIMIT ? OFFSET ?`;
  janela sem ordem emite `ORM00x`, nunca silêncio.
- **P4:** paridade de `orm.window` JVM/Native/JS (mesmas linhas, mesmos metadados);
  total opt-in emite exatamente um `COUNT(*)`; sem carga total no caminho janelado.
- **P5:** `?page=3&limit=20` → offset 40 sem código no handler; valores ruins → 400
  com mensagem nomeada; decode de query JVM/JS idêntico.
- **P6:** docs e `StdCatalog` consistentes; `check_stdlib_boundary.sh` verde (sem
  namespace novo); zero regressão.

## 22. Respostas finais (conforme pedido)

1. **Abordagem compatível:** **A — um valor de intenção `Window<T>` composto do
   `subList` existente (em memória) e do `LIMIT/OFFSET` de `kof_orm_page` (DB)**,
   com o helper HTTP isolado em `kof.web`. Fallback **B** (sem tipo novo) se a
   mantenedora rejeitar superfície nova; **C** (sequência lazy) é o futuro barrado.
2. **Arquivos/módulos prováveis:**
   - collections: `MemberCallTyper.java`, `CollectionMethodTyper.java`,
     `MethodCallTyper.java`, `CollectionCallLowerer.java`, `jvm/JvmOpCollections.java`,
     `runtime/RuntimeList*.java`, `js/JsRuntimeCollections.java`,
     `KofInterpreterCollections.java`, riscv `NativeRiscvAsmMapset1/RtB*`.
   - DB: `parser/ExpressionParser.java`, `QueryDslExpr.java`,
     `CompilerOrmSupport.java`, `KofOrm.java`, `jvm/JvmOrmRuntime.java`,
     `KofJsOrmBridge.java`, `runtime/RuntimeOrm8.java`, `nat/NativeRiscvAsmRtB60.java`,
     `StdCatalog.java`.
   - HTTP: `KofWeb.java`, `jvm/JvmRuntimeWebDispatch.java`, `jvm/JvmWebCoreRuntime.java`,
     `js/JsRuntimeUiWeb.java`.
   - JSON (Native): `nat/NativeJsonSchema.java`, `ExpressionJsonCallLowerer.java`.
3. **Contratos novos:** `Window<T>` (valor + semântica de metadados),
   `orm.window<T>(db, limit, offset[, total])`, `kof.web.pageRequest(...)`. (O
   token `offset` da DSL mostrado nas seções de projeto NÃO está na superfície
   travada — ver a correção no cabeçalho.) Todos exigem `D-PAGINATION`.
4. **Implementar primeiro:** P1 (em memória `slice/take/drop`, sem tipo novo) —
   a menor, pega carona na Fase 1 do `PLAN-MULTIPARADIGMA`, útil imediatamente.
5. **Explicitamente futuro:** paginação cursor/keyset; a espinha lazy
   `Sequence<T>` e o `queryOf` SQL-generativo (`PLAN-MULTIPARADIGMA` Fases 3–7,
   barradas por SYSTEMS).
