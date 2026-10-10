last: none
doing: none-planned
next: spike-0-inventory
location: docs/development/future
state: planned

intent: kof-financial-money-movement

> **PT-BR** · EN: [`kof-financial-plan.md`](kof-financial-plan.md)

**Gate regra 6:** o `kof.financial` introduz namespaces NOVOS de stdlib. Todo o
plano foi desenhado para ser **library-first** (`D-KOF-FIRST`): a v1 deve ser
expressável como bibliotecas Kof puras sobre primitivos que já existem. Se um
primitivo novo de núcleo for julgado necessário depois (um escalar decimal, um
modo de arredondamento exato), isso é **semântica congelada** e decisão da
mantenedora. Este documento é **apenas plano, zero código**. A promoção exige
decisão da mantenedora (`D-FINANCIAL-GO`) mais o fluxo de promoção de futuro
(`docs/development/future/README.md` §"When to move", `AGENTS.md` §"Future
promotion"). A promoção está atualmente CONGELADA por `D-FUTURE-FREEZE`; este
plano é autorado sob esse congelamento, não promovido.

**Fonte:** briefing da mantenedora (02/10/2026), escopo resumido: dinheiro,
moeda, câmbio, tarifas, transação/pagamento/transferência/estorno/refund/
liquidação, conta/saldo/razão (partidas dobradas), eventos/webhooks/
idempotência/retentativas/reconciliação, provedores/adaptadores, fronteira de
compliance, perfis de jurisdição.

**Trabalho já decidido que este plano DEVE reutilizar (não duplicar):**
- `docs/development/future/kofbol-plan.md` — interop de banco legado; já
  declara a mesma regra de dinheiro (`Never convert money to float/double`) e a
  mesma ausência medida de um tipo decimal. O `kof.financial` é a contraparte
  **greenfield**: o KofBOL fala com o sistema antigo, o `kof.financial` modela
  o novo.
- `KofDb.java`/`KofOrm.java` `transaction { … }` — a costura de commit/rollback
  existente (`stdlib-database.md:36-38,53`); a unidade de lançamento da razão
  anda sobre ela.
- `KofJson.java` / `json.encode|decode<T>` — persistência e payloads de
  provedor.
- `KofHttp.java` + `KofWeb.java` — clientes de provedor e recepção de webhook.
- `KofSecurity.java` — assinaturas HMAC para webhooks, random seguro para ids.
- `KofUuid.java` — `uuid.v4()`/`uuid.v7()` para chaves de idempotência.
- `KofTime.java` — `time.now()` epoch millis; datas ISO.
- `KofObservability.java` — ids de correlação/requisição (retentativas de
  webhook, reconciliação).
- `docs/development/future/entity-history-plan.md` — auditoria temporal, o
  mesmo instinto append-only que a razão precisa; `audited entity` pode
  hospedar as linhas da razão.

---

# `kof.financial` — plano (dinheiro, contas e movimento)

## 0. Propósito

> **O Kof deve conseguir modelar dinheiro e movê-lo entre contas sem nunca
> perder um centavo, em qualquer alvo, e sem inventar uma linguagem.**

`kof.financial` é uma **família de bibliotecas Kof puras** para os interesses
*nucleares* do dinheiro: representar valores exatamente, manter saldos, lançar
movimentos de partidas dobradas, conduzir o ciclo de vida de uma transação
(autorizar → capturar → liquidar → estornar/reverter), cobrar tarifas, emitir
eventos e reconciliar contra um provedor. **Não** é gateway de pagamento,
**não** é integração bancária, **não** é motor de impostos e **não** é sistema
de compliance. Provedores e jurisdições são **adaptadores e dados**, nunca
política embutida em código.

Pergunta final de aceitação (§24):

> Uma aplicação Kof consegue representar dinheiro exatamente, lançar uma razão
> balanceada, conduzir um pagamento por todo o seu ciclo de vida com
> retentativas idempotentes e reconciliar o resultado contra um provedor
> externo — em JVM, Native e JS — usando apenas `kof.financial` e primitivos
> que já existem?

## 1. Linha de base medida (novidade honesta)

A investigação (ancorada em arquivo, tip `a3912a971`) estabeleceu exatamente o
que existe. A **estrutura de apoio** é real; o **domínio de dinheiro** é
inteiramente novo.

| Interesse | Estado real | Evidência |
|---|---|---|
| `kof.financial` / qualquer namespace financeiro | **não existe** | busca no repo → 0 hits |
| Tipo Decimal / `BigDecimal` / ponto-fixo / dinheiro | **ausente** | `Type.java:7-15`; `kofbol-plan.md:69`; `types.md:145-152` |
| Aritmética decimal exata | **ausente** | `KofMath.java` só tem `roundTo(Double,Int)`; sem tipo de escala |
| `record` (imutável, igualdade estrutural) | **existe** | `classes.md:99-129`; `RecordEqualityLowerer.java` |
| `class` + `interface` + `enum` (constantes) | **existe** | `classes.md:9-96,133-220` |
| Genéricos, narrowing `T?`, `switch`, `for-in` | **existe** | `type-system.md`; `statements.md:110-151` |
| Exceções String + códigos de gap nomeados | **existe** | `statements.md:160-189`; `DomainGapCodesTest.java` |
| `db.connect` + `transaction { }` (commit/rollback) | **existe** | `KofDb.java:80,110-112`; `jvm/JvmConfigRuntime.java:535-569` |
| `entity` + CRUD ORM (`save/find/all/where/page`) | **existe** | `KofOrm.java:147`; `DATABASE_VISION.md` |
| `json.encode` / `json.decode<T>` para records | **existe** | `StdCatalog.java:401-403`; `JsonDispatch.java:78-86` |
| `http.get/post/...`, `web.app()`, recepção de webhook | **existe** | `KofHttp.java:81-110`; `stdlib-web.md` |
| `crypto.hmacSha256` / `sha256` / random seguro | **existe** | `KofSecurity.java:75`; `security.md` |
| `uuid.v4()` / `uuid.v7()` | **existe** | `KofUuid.java:35-49` |
| `time.now()` (epoch ms) + helpers ISO | **existe** | `KofTime.java:128-233` |
| `String.format("%.2f", …)` (Locale.ROOT) | **existe** (JVM/JS; Native não verificado) | `StringFormatCallLowerer.java:24-63` |
| `strings.padLeft/padRight` | **existe** | `KofStrings.java:82-87` |
| `spawn`/`await`/`channel`; sequencialmente consistente | **existe** | `concurrency.md`; `concurrency-memory-model.md` |
| Sobrecarga de operador | **ausente** | `classes.md:268` |
| Tipo data/hora/instante/duração, banco de timezones | **ausente** | apenas strings ISO + epoch ms |
| Isolamento de transação / savepoints / rollback explícito | **ausente** | só `setAutoCommit(false)` + rollback por exceção |
| Pool de conexões | **ausente (planejado)** | `DATABASE_VISION.md:322-324` |
| Idempotência / retry / outbox / razão de webhook | **ausente** | 0 hits |
| Razão de partidas dobradas / diário / lançamento | **ausente** | 0 hits |
| Taxa de câmbio / tabela de tarifas / reconciliação | **ausente** | 0 hits |

**Consequência:** representação de dinheiro, a razão, o ciclo de vida,
idempotência, retentativas e reconciliação são genuinamente novos. As costuras
de persistência, transporte, cripto, id e tempo já existem e devem ser
reutilizadas, não reconstruídas.

## 2. Não-objetivos (explícitos)

- **Nenhum tipo de dinheiro novo na linguagem.** Sem keyword `decimal`/`money`,
  sem sobrecarga de operador. A v1 modela dinheiro como um `record` sobre
  `Long` em unidades menores mais um código de moeda explícito. Um escalar
  decimal de núcleo, se algum dia necessário, é decisão separada de regra 6
  (`D-FINANCIAL-DECIMAL`).
- **Nunca float para dinheiro.** `Double`/`Float` são proibidos em qualquer
  campo de valor, taxa, tarifa ou saldo; um diagnóstico deve impor isso na
  fronteira da biblioteca.
- **Nenhum gateway de pagamento.** Nenhuma API específica de provedor é
  embutida; provedores são adaptadores atrás de uma interface.
- **Nenhum motor de impostos.** Imposto é uma *tarifa* calculada pelo código da
  aplicação; a biblioteca apenas move valores.
- **Nenhum motor de compliance.** KYC/AML/sanções são *verificações
  estruturadas com uma fronteira de decisão*; a biblioteca registra o
  resultado, não decide a lei.
- **Nenhuma razão singleton global.** Contas e razões são valores; o estado
  vive no store que a aplicação escolher.
- **Nenhum motor de timezone/calendário.** A v1 usa epoch millis + strings ISO;
  um tipo de data real é preocupação separada.
- **Nenhuma chamada de rede oculta.** Nenhuma chamada de provedor dentro de
  computação pura; I/O é sempre uma chamada explícita de adaptador.
- **Nenhum arredondamento silencioso.** Todo ponto de arredondamento recebe um
  modo explícito.

## 3. Arquitetura em camadas

```text
Aplicação Kof
      ↓
kof.financial             (API Kof idiomática: Money, Account, Journal, Tx)
      ↓
modelo de domínio         (records: Money, Currency, Rate, Fee, Entry, Event)
      ↓
motor                     (regras de lançamento, ciclo de vida, arredondamento, idempotência)
      ↓
portas                    (LedgerStore, Provider, Clock, IdGenerator — interfaces)
      ↓
adaptadores               (kof.db / kof.orm / http / file — escolhidos pela app)
      ↓
mundo externo             (banco, provedor, consumidor de webhook)
```

O **domínio não deve depender do adaptador**. Um `Journal` lança através de uma
interface `LedgerStore`; `kof.db` é uma implementação. É isso que mantém a
biblioteca testável em memória e honesta entre alvos.

## 4. Representação de dinheiro (a decisão central)

Esta é a decisão da qual todo o resto depende, então ela vem primeiro e é
registrada como `D-FINANCIAL-MONEY`.

**Modelo v1 — unidades menores + código ISO-4217:**

```kof
// PROPOSTO (Kof puro; só `record` + `Long` + `String` existem hoje)
record Money(Long minor, String currency)
```

- `minor` é o valor na menor unidade da moeda (centavos, pence, subunidades
  tipo satoshi) — sempre um `Long` exato, nunca um `Double`.
- `currency` é o código alfa ISO-4217 (`"BRL"`, `"USD"`, `"EUR"`).
- `Money` é **imutável** e compara por **conteúdo** (`==` em record é
  estrutural — `type-system.md:333-345`), então `Money(100,"BRL") ==
  Money(100,"BRL")` é `true`.
- Um descritor `Currency` carrega a escala e metadados:

```kof
// PROPOSTO
record Currency(String code, Int minorUnits, String symbol)
```

A tabela de escalas padrão (2 para a maioria, 0 para JPY/KRW, 3 para
BHD/KWD/JOD/TND, etc.) é **dado**, não código, e vive num arquivo Kof.
`D-FINANCIAL-MONEY` registra: unidades menores + código, só `Long`, sem
mudança de núcleo.

**Por que não `Double`?** `0.1 + 0.2 != 0.3` em IEEE-754; um centavo perdido
por transação é um bug em escala. `types.md:36-38` documenta wrap-around
silencioso até para `Long`; o overflow deve, portanto, ser checado
explicitamente em cada fronteira aritmética (§4.2).

**Por que não um decimal de núcleo?** Seria superfície de semântica congelada,
precisaria de suporte de backend em cinco alvos, e `kofbol-plan.md:364` já
enquadra o mesmo trade-off. O caminho library-first é mais barato e reversível.

### 4.1 Contrato aritmético

Toda aritmética de `Money` é **explícita, checada e segura por moeda**:

```kof
// PROPOSTO
Money add(Money a, Money b)          // lança se moedas diferirem; checa overflow
Money sub(Money a, Money b)          // lança se a.minor < b.minor (a menos que negue)
Money negate(Money a)                // lança em Long.MIN_VALUE
Money scale(Money a, Int num, Int den, Rounding mode)   // multiplica depois divide
```

- **Divergência de moeda é erro, nunca conversão implícita.** Conversão é
  operação FX explícita (§4.3).
- **Overflow é detectado** com pré-checagens (`a.minor > Long.MAX - b.minor`),
  não wrap silencioso. Diagnósticos `FIN001` (moeda divergente) e `FIN002`
  (overflow de valor).
- `add`/`sub` são **funções de topo**, não métodos — o Kof não tem sobrecarga
  de operador, e `Money` é um record.

### 4.2 Arredondamento

O arredondamento é **argumento explícito de primeira classe**, nunca default.

```kof
// PROPOSTO
enum Rounding { HalfUp, HalfEven, Down, Up, Floor, Ceiling }
Money roundTo(Money a, Int scale, Rounding mode)
```

- `HalfEven` (arredondamento bancário) é o default recomendado para
  liquidação; `HalfUp` para exibição. O modo é sempre passado pelo chamador.
- O arredondamento é implementado sobre aritmética inteira (quociente/resto),
  então é exato e idêntico em todo alvo. **Não deve** passar por
  `Double`/`math.roundTo`.
- `D-FINANCIAL-ROUNDING` registra o algoritmo inteiro e o default.

### 4.3 Câmbio e taxas

```kof
// PROPOSTO
record Rate(String base, String quote, Long num, Long den, Long asOfMillis, String source)
Money convert(Money amount, Rate rate, Rounding mode)   // amount.currency == rate.base
```

- Uma taxa é um racional exato `num/den` (ex.: `5_4321/1_0000`), **não** um
  `Double`; isso preserva precisão e torna a conversão determinística.
- `asOfMillis` e `source` são obrigatórios: uma taxa sem procedência é um bug.
- A busca de taxa é uma interface `RateSource` (§12), então testes usam tabela
  fixa e produção usa um provedor.
- Taxas cruzadas (A→B→C) compõem por multiplicação racional, com regra
  documentada de arredondamento intermediário (`D-FINANCIAL-FX`).

## 5. Superfície proposta (intenção, não gramática final)

Namespaces (cada um um pacote Kof puro; sem mudança de núcleo):

| Namespace | Responsabilidade | Exemplos |
|---|---|---|
| `kof.financial` | o import guarda-chuva | re-exporta os records públicos |
| `kof.financial.money` | `Money`, `Currency`, aritmética, arredondamento | `add`, `roundTo` |
| `kof.financial.fx` | `Rate`, conversão, fontes de taxa | `convert` |
| `kof.financial.account` | `Account`, `Balance`, holds | `balanceOf` |
| `kof.financial.ledger` | `Entry`, `Journal`, `Posting`, partidas dobradas | `post`, `journal` |
| `kof.financial.tx` | ciclo de vida + máquina de estados | `authorize`, `capture` |
| `kof.financial.fees` | tabelas de tarifas, split, arredondamento | `applyFees` |
| `kof.financial.events` | eventos de domínio, outbox, webhooks | `emit`, `dispatch` |
| `kof.financial.idem` | chaves e registros de idempotência | `idempotent` |
| `kof.financial.recon` | reconciliação contra extratos | `reconcile` |
| `kof.financial.provider` | interface `Provider` + adaptadores | `FakeProvider` |
| `kof.financial.compliance` | verificações estruturadas + decisões | `CheckResult` |
| `kof.financial.jurisdiction` | perfis de jurisdição como dados | `JurisdictionProfile` |

### 5.1 Records centrais (todos `PROPOSTO`, todos Kof puro)

```kof
record Currency(String code, Int minorUnits, String symbol)
record Money(Long minor, String currency)
record Account(String id, String ownerRef, String currency, String kind)
record Balance(String accountId, Long postedMinor, Long pendingMinor)
record Entry(String accountId, String direction, Long minor, String currency)
record Journal(String id, String correlationId, Long postedAtMillis, List<Entry> entries)
record Event(String id, String kind, String payloadJson, Long occurredAtMillis)
```

`direction` é `"debit"`/`"credit"` (uma `String` restringida por helper, ou um
`enum Direction { Debit, Credit }` já que o enum hoje não tem corpo —
`classes.md:147-151`). A forma enum é preferida e é compatível com o
`EXISTENTE`.

### 5.2 Interfaces de store / portas

```kof
// PROPOSTO
interface LedgerStore {
    Journal append(Journal j)
    List<Journal> journalsFor(String accountId)
    Balance balance(String accountId)
}

interface Clock { Long nowMillis() }
interface IdGenerator { String next(String prefix) }
interface RateSource { Rate? rate(String base, String quote) }
```

Estes são mecanismos `EXISTENTES`: `interface` com métodos abstratos
(`classes.md:196-220`). Implementações: `DbLedgerStore` (sobre `kof.db`),
`MemoryLedgerStore` (sobre `List`/`Map`), `OrmLedgerStore` (sobre `kof.orm`).

## 6. Ciclo de vida da transação (máquina de estados)

O ciclo de vida é a *espinha* de um pagamento. É modelado explicitamente para
que transições inválidas sejam impossíveis, não apenas desencorajadas.

```kof
// PROPOSTO
enum TxState { Created, Authorized, Captured, Settled, Refunded, Reversed, Failed, Expired }
record Transaction(String id, String idempotencyKey, Money amount, TxState state,
                   Long createdAtMillis, Long updatedAtMillis, String providerRef)
```

Transições (cada uma função de topo retornando um novo record imutável):

| De | Evento | Para | Regra |
|---|---|---|---|
| `Created` | `authorize` | `Authorized` | provedor aprova |
| `Created` | `fail` | `Failed` | provedor recusa |
| `Authorized` | `capture` | `Captured` | valor ≤ autorizado |
| `Authorized` | `expire` | `Expired` | passou de `expiresAt` |
| `Captured` | `settle` | `Settled` | fundos chegam |
| `Captured`/`Settled` | `refund` | `Refunded` | ≤ capturado, pode ser parcial |
| qualquer não-terminal | `reverse` | `Reversed` | anula autorização/chargeback |
| `Settled` | `refund` | `Refunded` | distinção estorno/refund explícita |

- **`refund` vs `reversal`** são eventos distintos com lançamentos distintos: um
  refund é um novo movimento de saída; um reversal anula uma autorização prévia
  (nenhum fundo movido) ou recupera uma cobrança liquidada.
  `D-FINANCIAL-LIFECYCLE` registra a semântica exata.
- Cada transição emite um `Event` (§10) e (quando liquidada) um `Journal`
  balanceado (§8). Capturas/refunds parciais são de primeira classe.
- Transições inválidas lançam `"FIN003 invalid transition <from> -> <event>"`.

## 7. Tarifas

Tarifas são computadas por uma tabela explícita e aplicadas como **entradas
separadas na razão**, nunca dobradas no valor principal.

```kof
// PROPOSTO
record FeeRule(String id, String kind, Long fixedMinor, Long bps, String currency)
record Fee(String ruleId, Money amount)
List<Fee> applyFees(Money principal, List<FeeRule> rules)
Money netOfFees(Money principal, List<Fee> fees)
```

- `kind` ∈ `"fixed"`, `"percentage"` (pontos-base, inteiro), `"tiered"`,
  `"interchange"`. Toda aritmética é inteira + arredondamento explícito.
- Uma tarifa é sempre lançada na sua própria conta (ex.: `fees:interchange`),
  então a razão balanceia e o reporte é possível.
- `D-FINANCIAL-FEES` registra a convenção de bps (1 bp = 1/10000) e o ponto de
  arredondamento.

## 8. Razão de partidas dobradas

A razão é a **fonte da verdade**; saldos são derivados.

```kof
// PROPOSTO
Journal post(List<Entry> entries, String correlationId)
Bool isBalanced(Journal j)     // soma(débitos) == soma(créditos), mesma moeda
```

Regras:

1. **Toda razão balanceia**: a soma dos débitos em unidades menores é igual à
   soma dos créditos, por moeda. `isBalanced` é checada antes do append; uma
   razão desbalanceada lança `"FIN004 unbalanced journal"`.
2. **Entradas são imutáveis e append-only.** Não há update ou delete; uma
   correção é um *lançamento reverso* que referencia o `correlationId`
   original. É o mesmo instinto de `entity-history-plan.md`.
3. **Sinais são explícitos** via `direction`, nunca codificados como valores
   negativos.
4. **Razões multi-moeda** mantêm uma sub-razão balanceada por moeda; o próprio
   movimento FX é um par de entradas numa conta dedicada `fx:position`.
5. **Tipos de conta** seguem a identidade contábil: `asset`, `liability`,
   `equity`, `revenue`, `expense`. A convenção de sinal do saldo é documentada
   em `D-FINANCIAL-LEDGER`.

### 8.1 Derivação de saldo

```kof
// PROPOSTO
Long postedBalance(String accountId, LedgerStore store)   // soma das entradas lançadas
Long availableBalance(Balance b)                          // posted - pending holds
```

Saldos são **computados a partir das entradas**, nunca armazenados como
verdade primária; um `Balance` em cache é uma otimização que o store pode
manter, e `reconcile` (§11) verifica o cache contra as entradas.

### 8.2 Holds / pendente

```kof
// PROPOSTO
record Hold(String accountId, Long minor, String currency, Long expiresAtMillis)
Balance applyHold(Balance b, Hold h)
```

Um hold reduz o `available`, mas não o `posted`; a captura converte um hold em
entradas lançadas. É isso que torna authorize/capture honesto.

## 9. Contas e saldos

- Uma `Account` é identificada por um `id` opaco mais um `ownerRef` e uma
  `currency`; um dono multi-moeda tem uma conta por moeda.
- **Nenhuma suposição de saldo negativo** é embutida; se uma conta pode ficar
  negativa é política (`AccountPolicy`), não modelo.
- `Account.kind` ∈ `"customer"`, `"merchant"`, `"fee"`, `"fx"`,
  `"settlement"`, `"external"`.

## 10. Eventos, webhooks, idempotência, retentativas

### 10.1 Idempotência

```kof
// PROPOSTO
record IdempotencyRecord(String key, String requestHash, String responseJson, Long createdAtMillis)
String idempotencyKey(String prefix)            // prefix + uuid.v7()
Transaction idempotent(String key, String requestHash, () -> Transaction body)
```

- Toda operação que muta aceita uma `idempotencyKey`. Um replay com a **mesma
  chave e o mesmo hash de requisição** retorna a resposta armazenada; um replay
  com a mesma chave mas **hash diferente** é erro `FIN005` (conflito de
  idempotência) — nunca uma segunda cobrança silenciosa.
- As chaves usam `uuid.v7()` (`KofUuid.java:35-49`), então são ordenadas no
  tempo.

### 10.2 Eventos de domínio + outbox

```kof
// PROPOSTO
record Event(String id, String kind, String aggregateId, String payloadJson, Long occurredAtMillis, String correlationId)
void emit(LedgerStore store, Event e)          // append no outbox na mesma transação
List<Event> pending(LedgerStore store)
void markDispatched(LedgerStore store, String eventId)
```

- Eventos são escritos num **outbox** na mesma `transaction { … }` do
  lançamento da razão (`KofDb.java:110-112`), então estado e evento nunca
  divergem.
- Um dispatcher (código da aplicação, ou `spawn`) drena o outbox.

### 10.3 Webhooks (saída e entrada)

- **Saída:** assine o payload com `crypto.hmacSha256(secret, body)`
  (`KofSecurity.java:75`), POST com `http.post` (`KofHttp.java`), registre a
  tentativa. Retentativas usam backoff exponencial com número máximo de
  tentativas; cada tentativa é um `Event`.
- **Entrada:** verifique a assinatura com `crypto.hmacSha256` e comparação
  **de tempo constante** (`security.constantTimeEquals`), rejeite timestamp
  velho, então deduplique por id de evento (idempotência).
- A política de retentativa é dado (`RetryPolicy(Int maxAttempts, Long
  baseDelayMillis, Long maxDelayMillis)`), não uma constante enterrada no
  código.

## 11. Reconciliação

```kof
// PROPOSTO
record StatementLine(String externalId, Long minor, String currency, Long valueDateMillis, String raw)
record Match(String journalId, String statementLineId, String kind)   // exact | partial | unmatched
List<Match> reconcile(List<Journal> ours, List<StatementLine> theirs, Int toleranceMinor)
```

- O casamento é determinístico: valor+moeda+janela de data exatos primeiro,
  depois tolerância, depois manual. Itens não casados são **retornados**,
  nunca descartados.
- `toleranceMinor` é explícito; `0` significa exato.
- O resultado é um valor de relatório; uma *decisão* de reconciliação é
  política da aplicação.

## 12. Provedores / adaptadores

```kof
// PROPOSTO
interface Provider {
    AuthorizeResult authorize(Transaction t)
    CaptureResult capture(Transaction t)
    RefundResult refund(Transaction t, Money amount)
    List<StatementLine> statement(String accountRef, String fromIso, String toIso)
}
```

- `Provider` é uma interface (`classes.md:196-220`); `FakeProvider` vive na
  biblioteca e é o duble de teste. Adaptadores reais vivem em
  `kof.financial.provider.*` e usam `kof.http`/`kof.json`.
- Erros de provedor são mapeados para um record **fechado** `ProviderError` com
  flag `retryable`; a camada de ciclo de vida decide se retenta.
- **Nenhuma chamada de provedor acontece dentro da aritmética de `Money`.** A
  fronteira da porta é explícita e mockável.

## 13. Persistência e migrações

- Adaptadores `LedgerStore`: `MemoryLedgerStore` (testes), `DbLedgerStore`
  (`db.query`/`db.execute`/`transaction`, `KofDb.java:80`), `OrmLedgerStore`
  (`entity` + ORM, `KofOrm.java:147`).
- Tabelas (via `entity`): `account`, `journal`, `entry`, `event`,
  `idempotency`, `hold`, `statement_line`. Todas append-only exceto caches.
- Evolução de schema: `orm.migrate` existe (`KofOrm.java:147`); o plano exige
  migrações **forward-only** com tabela de versão. Isso reutiliza o ORM, sem
  mudança de núcleo.
- Dinheiro é armazenado como colunas `Long` minor + `String` currency;
  **nunca** uma coluna de ponto flutuante. Um check de banco deve rejeitar
  colunas float para esses campos.

## 14. Fronteira de compliance (só estrutura)

A biblioteca **não** implementa lei; ela torna a *fronteira* explícita.

```kof
// PROPOSTO
enum CheckOutcome { Pass, Review, Block }
record CheckResult(CheckOutcome outcome, String code, String detail)
interface ComplianceCheck { CheckResult run(Transaction t) }
```

- Verificações (status KYC, triagem de sanções, limites de velocidade) são
  compostas como `List<ComplianceCheck>`; a app as fornece.
- A **decisão** é sempre registrada como um `CheckResult` com código e razão, e
  emitida como `Event`. Nenhum bloqueio silencioso.
- Perfis de jurisdição (§15) parametrizam quais verificações se aplicam.

## 15. Perfis de jurisdição

Jurisdições são **records de dados**, não ramos de código:

```kof
// PROPOSTO
record JurisdictionProfile(String code, String currency, Int minorUnits,
                           List<String> requiredChecks, Long maxAmountMinor,
                           Int settlementDays, String taxIdLabel)
```

- `code` é ISO-3166 alfa-2; perfis vivem num arquivo Kof de dados e são
  carregados pela app.
- Um perfil pode definir `maxAmountMinor`, verificações obrigatórias, janela de
  liquidação e moeda local. Ele **nunca** contém lógica de negócio.
- Perfis são versionados (data de vigência) para que uma mudança de regra seja
  auditável.

## 16. Matriz cross-target (§93)

`kof.financial` é Kof puro; herda a paridade dos primitivos que usa. A
expectativa honesta (a ser medida na implementação, RED-first):

| Capacidade | JVM | Native x86-64 | Native riscv64/aarch64 | JS | Script |
|---|---|---|---|---|---|
| Aritmética `Long` + pré-checagens de overflow | DONE | DONE | DONE | DONE | DONE |
| Igualdade estrutural de `record` | DONE | DONE | DONE | DONE | DONE |
| `enum` + `switch` | DONE | DONE | DONE | DONE | DONE |
| Store em memória `List`/`Map` | DONE | DONE | DONE | DONE | DONE |
| `json.encode/decode<T>` | DONE | DONE (gaps `JSN001/002/004` em algumas formas) | DONE (mesmos gaps) | DONE | DONE |
| `db.connect` + `transaction {}` | DONE | DONE (SQLite/MySQL wire) | DONE | DONE | TBD |
| `http.post` (chamadas de provedor) | DONE | DONE (https → throw) | DONE (https → throw) | DONE | TBD |
| `crypto.hmacSha256` (webhooks) | DONE | DONE | **`SECN000`** (gaps cross de AES-GCM/ChaCha20) | DONE | DONE |
| `uuid.v7` | DONE | DONE | DONE | DONE | DONE |
| `spawn`/`await` (dispatch assíncrono) | DONE | DONE | DONE | DONE | DONE |
| `time.now` + helpers ISO | DONE | DONE | `TIME003` (`tzOffsetSeconds`) | DONE | DONE |

Regras: uma capacidade que não está honestamente disponível num alvo deve
levantar um **diagnóstico nomeado** no call site (`SECN000`, `JSN00x`,
`TIME003`, `DB001`, …) — nunca um fallback silencioso. O plano de implementação
deve remedir cada linha e substituir `DONE` pela prova executada.

## 17. Matriz de rastreabilidade de requisitos (§92)

Os temas do briefing são enumerados aqui e classificados. `EXISTENTE` = o
primitivo existe hoje; `PROPOSTO` = trabalho de biblioteca Kof pura (sem
mudança de núcleo); `CORE` = exige decisão de semântica congelada da
mantenedora; `FUTURO` = fora da v1.

| # | Requisito | Classe | Primitivo / plano |
|---|---|---|---|
| 1 | Representar dinheiro exatamente | PROPOSTO | `Money(Long minor, String code)` §4 |
| 2 | Nunca usar float para dinheiro | PROPOSTO | diagnóstico de fronteira `FIN006` §2 |
| 3 | Metadados de moeda + unidades menores | PROPOSTO | `Currency` + tabela de dados §4 |
| 4 | Aritmética segura por moeda | PROPOSTO | `add`/`sub` + `FIN001` §4.1 |
| 5 | Detecção de overflow | PROPOSTO | pré-checagens + `FIN002` §4.1 |
| 6 | Modos de arredondamento explícitos | PROPOSTO | enum `Rounding` + `roundTo` §4.2 |
| 7 | Conversão FX com taxas exatas | PROPOSTO | `Rate` racional + `convert` §4.3 |
| 8 | Procedência da taxa (fonte/tempo) | PROPOSTO | `Rate.asOfMillis`/`source` §4.3 |
| 9 | Contas (por moeda) | PROPOSTO | `Account` §9 |
| 10 | Saldos derivados das entradas | PROPOSTO | `postedBalance` §8.1 |
| 11 | Holds / pendente | PROPOSTO | `Hold` + `availableBalance` §8.2 |
| 12 | Lançamentos de partidas dobradas | PROPOSTO | `Journal`/`Entry` + `isBalanced` §8 |
| 13 | Razão imutável append-only | PROPOSTO | sem update/delete; lançamento reverso §8 |
| 14 | Razões multi-moeda | PROPOSTO | sub-razões por moeda §8 |
| 15 | Ciclo de vida da transação | PROPOSTO | `TxState` + transições §6 |
| 16 | Pagamento/transferência | PROPOSTO | ciclo de vida + razão §6/§8 |
| 17 | Refund (total/parcial) | PROPOSTO | transição `refund` §6 |
| 18 | Estorno / chargeback | PROPOSTO | transição `reverse` §6 |
| 19 | Liquidação | PROPOSTO | transição `settle` + conta de liquidação §6 |
| 20 | Tabelas de tarifas | PROPOSTO | `FeeRule`/`applyFees` §7 |
| 21 | Contas de tarifa / valor líquido | PROPOSTO | entradas separadas §7 |
| 22 | Chaves de idempotência | PROPOSTO | `uuid.v7` + `IdempotencyRecord` §10.1 |
| 23 | Detecção de conflito de idempotência | PROPOSTO | `FIN005` §10.1 |
| 24 | Eventos de domínio | PROPOSTO | `Event` §10.2 |
| 25 | Outbox (atômico com o lançamento) | PROPOSTO | `transaction { }` §10.2 |
| 26 | Webhooks de saída (assinados) | PROPOSTO | `crypto.hmacSha256` + `http.post` §10.3 |
| 27 | Webhooks de entrada (verificados) | PROPOSTO | HMAC + tempo constante + dedupe §10.3 |
| 28 | Retentativas com backoff | PROPOSTO | `RetryPolicy` §10.3 |
| 29 | Reconciliação | PROPOSTO | `reconcile` + `Match` §11 |
| 30 | Relatório de não casados | PROPOSTO | retornados, nunca descartados §11 |
| 31 | Interface de provedor | PROPOSTO | `Provider` §12 |
| 32 | Mapeamento de erro de provedor | PROPOSTO | `ProviderError(retryable)` §12 |
| 33 | Provedor fake para testes | PROPOSTO | `FakeProvider` §12 |
| 34 | Portas de persistência | PROPOSTO | `LedgerStore` §5.2/§13 |
| 35 | Adaptadores DB/ORM | PROPOSTO | `KofDb`/`KofOrm` §13 |
| 36 | Migrações forward-only | PROPOSTO | `orm.migrate` §13 |
| 37 | Fronteira de verificação de compliance | PROPOSTO | `ComplianceCheck` §14 |
| 38 | Decisões de compliance registradas | PROPOSTO | `CheckResult` + evento §14 |
| 39 | Perfis de jurisdição como dados | PROPOSTO | `JurisdictionProfile` §15 |
| 40 | Versionamento de perfil | PROPOSTO | data de vigência §15 |
| 41 | Ids de correlação | EXISTENTE | `KofObservability.java` |
| 42 | Random seguro / ids | EXISTENTE | `crypto.randomHex`/`uuid.v7` |
| 43 | Fonte de tempo | EXISTENTE | `time.now()` |
| 44 | Payloads JSON | EXISTENTE | `json.encode/decode<T>` |
| 45 | Transporte HTTP | EXISTENTE | `kof.http` |
| 46 | Receptor de webhook | EXISTENTE | `kof.web` |
| 47 | Concorrência (dispatch assíncrono) | EXISTENTE | `spawn`/`await`/`channel` |
| 48 | Reporte de erro | EXISTENTE | exceções String + `FIN0xx` |
| 49 | Escalar decimal de núcleo | CORE | `D-FINANCIAL-DECIMAL` (não v1) |
| 50 | Sobrecarga de operador para `Money` | CORE | proibida, não proposta |
| 51 | Liquidação com timezone | FUTURO | precisa de um tipo data/hora |
| 52 | Mapeamento de mensagem ISO-20022 | FUTURO | anda com `kofbol-plan.md` |
| 53 | Conectividade com rede de cartões | FUTURO | adaptador de provedor, fora da v1 |
| 54 | Motor de cálculo de impostos | FUTURO | preocupação de app/tarifa, fora da v1 |
| 55 | Motor AML/KYC completo | FUTURO | vendor de compliance, fora da v1 |

> A enumeração completa de 102 itens do briefing foi fornecida como escopo
> resumido; esta matriz é o mapeamento honesto, ancorado em evidência, desse
> escopo. Qualquer item não listado aqui é **desconhecido** e não pode ser
> reivindicado. `D-FINANCIAL-TRACE` registra o mapeamento e os itens abertos.

## 18. Matriz de jurisdição / compliance (§94)

Perfis são exemplos da *forma*, não aconselhamento jurídico; cada célula é
dado.

| Jurisdição | Moeda | Unidades menores | Verificações exigidas (forma v1) | Valor máx. | Liquidação |
|---|---|---|---|---|---|
| BR | BRL | 2 | KYC, velocidade | perfil | D+1 |
| US | USD | 2 | KYC, sanções | perfil | D+1 |
| EU | EUR | 2 | KYC, SCA, sanções | perfil | D+1 |
| JP | JPY | 0 | KYC | perfil | D+1 |
| BH | BHD | 3 | KYC, sanções | perfil | D+2 |

A matriz é carregada de um arquivo Kof versionado; adicionar uma jurisdição é
adicionar uma linha, nunca um ramo de código. `D-FINANCIAL-JURISDICTION`
registra o formato do arquivo e a regra de versionamento.

## 19. Erros e diagnósticos

| Código | Significado | Onde |
|---|---|---|
| `FIN001` | moeda divergente | `add`/`sub`/`convert` |
| `FIN002` | overflow de valor | aritmética |
| `FIN003` | transição de ciclo de vida inválida | `tx` |
| `FIN004` | razão desbalanceada | `post` |
| `FIN005` | conflito de idempotência | `idempotent` |
| `FIN006` | float usado para dinheiro | check de fronteira |
| `FIN007` | moeda desconhecida | lookup de `Currency` |
| `FIN008` | taxa ausente/expirada | `convert` |
| `FIN009` | tolerância de reconciliação excedida | `reconcile` |

Estes são **runtime** `throw "FIN00x ..."` (a moeda de erro do Kof é `String` —
`statements.md:166-167`). Um diagnóstico de compile-time só é possível se um
recurso futuro de núcleo permitir; a biblioteca não deve fingir. Os códigos são
registrados em `docs/backend-parity.md` quando o plano for promovido.

## 20. Plano de testes (quando implementado)

- **Dinheiro:** testes estilo propriedade sobre pares aleatórios de
  `Long`/moeda; fronteiras de overflow (`Long.MAX`), arredondamento em `.5`
  para todo modo, valores negativos.
- **Razão:** todo lançamento balanceia; reverter uma razão restaura o saldo
  exato anterior; sub-razões multi-moeda balanceiam independentemente.
- **Ciclo de vida:** toda transição legal, toda transição ilegal lança
  `FIN003`; aritmética de captura/refund parciais.
- **Idempotência:** replay mesma chave+hash retorna a mesma resposta; mesma
  chave hash diferente → `FIN005`; concorrência via `spawn` (modelo
  sequencialmente consistente — `concurrency-memory-model.md`).
- **Eventos/outbox:** evento e lançamento comitam atomicamente; rollback não
  deixa nenhum dos dois.
- **Webhooks:** verificação de assinatura aceita boa/rejeita adulterada;
  timestamp velho rejeitado; dedupe de entrada.
- **Recon:** exato/parcial/não casado, fronteiras de tolerância.
- **Cross-target:** rodar a suíte em JVM + Native x86-64 + riscv64/aarch64
  (qemu) + JS + Script; afirmar diagnósticos nomeados onde um primitivo está
  ausente.
- **RED-first** para todo bug fix e toda capacidade nova (`Q0`/`Q1`).

## 21. Roadmap (cada fatia uma lane, RED-first; nada começa só deste doc)

| Fatia | Escopo | Depende de | Prova |
|---|---|---|---|
| F0 | `Money`, `Currency`, aritmética, arredondamento | — | testes de dinheiro, 5 alvos |
| F1 | `Account`, `Balance`, holds | F0 | testes de derivação de saldo |
| F2 | `Journal`, `Entry`, lançamento de partidas dobradas | F1 | testes de lançamento balanceado |
| F3 | máquina de estados do ciclo de vida | F2 | testes da matriz de transição |
| F4 | tarifas | F2 | testes de split de tarifa |
| F5 | idempotência + eventos + outbox | F2 | testes de replay/atomicidade |
| F6 | webhooks (assinar/verificar/retry) | F5 | testes de assinatura + retry |
| F7 | reconciliação | F2 | testes de casado/não casado |
| F8 | interface de provedor + fake | F3 | E2E com provedor fake |
| F9 | adaptadores DB/ORM + migrações | F2 | E2E de persistência |
| F10 | compliance + perfis de jurisdição | F3 | testes de verificação por perfil |

Cada fatia é uma unidade coesa; `D-FUTURE-PROMOTION` permite um plano por vez,
e cada fatia é reivindicada no `DOING.md` com `owner = <ip>:<port>`.

## 22. Decisões abertas (regra 6 — mantenedora)

1. `D-FINANCIAL-MONEY` — unidades menores + código ISO (recomendado) vs um
   escalar decimal de núcleo.
2. `D-FINANCIAL-ROUNDING` — modo default de liquidação (`HalfEven`
   recomendado).
3. `D-FINANCIAL-FX` — regra de arredondamento intermediário para taxas
   cruzadas compostas.
4. `D-FINANCIAL-LIFECYCLE` — semântica exata de refund vs reversal e regras
   parciais.
5. `D-FINANCIAL-LEDGER` — convenção de sinal de saldo por tipo de conta.
6. `D-FINANCIAL-FEES` — convenção de bps e ponto de arredondamento.
7. `D-FINANCIAL-JURISDICTION` — formato do arquivo de perfis e versionamento.
8. `D-FINANCIAL-TRACE` — a fonte da enumeração de requisitos (a lista de 102
   itens) e sua casa canônica.
9. `D-FINANCIAL-GO` — autorização para promover este plano (atualmente
   congelado por `D-FUTURE-FREEZE`).

## 23. Contrato de não-regressão

- Nenhuma superfície existente da linguagem muda; nenhuma semântica congelada é
  tocada.
- Nenhum float em nenhum caminho de dinheiro; um teste deve provar que `FIN006`
  dispara.
- Todo namespace novo é registrado em `scripts/stdlib_boundary.txt` quando
  promovido (gate R1).
- Todo diagnóstico é catalogado em `docs/backend-parity.md` (R6).
- O plano permanece `planned`; nada aqui é implementado até ser promovido.

## 24. Pergunta final de revisão

> Uma aplicação Kof consegue representar dinheiro exatamente, lançar uma razão
> balanceada, conduzir um pagamento por todo o seu ciclo de vida com
> retentativas idempotentes e reconciliar o resultado contra um provedor
> externo — em JVM, Native e JS — usando apenas `kof.financial` e primitivos
> que já existem?

Se a resposta for não porque falta um primitivo, o primitivo faltante é
nomeado, classificado `CORE` ou `FUTURO`, e enviado à mantenedora — nunca
contornado em silêncio.
