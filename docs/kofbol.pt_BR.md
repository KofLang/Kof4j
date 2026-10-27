last: PROMOVIDO de `docs/development/future/` 14/10 (ordem da mantenedora "assume um novo plano em future" + `D-FUTURE-PROMOTION`); `D-KOFBOL`
doing: fatia 11 (docs + guia + a matriz de conformância) POUSADA 15/10 — `docs/kofbol-guide.pt_BR.md` (+EN): o que/por quê, o modelo de descritor, o import de copybook (OCCURS/88/REDEFINES), EBCDIC + COMP-3, transportes/mensageria/batch/transações, o modelo de códigos de retorno, a tabela COMPLETA LEG001–LEG019, a conformância cross-target (4 alvos, a fronteira §734) e as limitações EXPLÍCITAS + o caminho de modernização (§8). Cada face documentada tem prova executada. doing: fatia 10 (transações) POUSADA 15/10 — `libs/legacy/Tx.kf`: `begin`/`stage`/`commit`/`rollback` sobre o broker reutilizando o padrão funcional (§13: NADA inventado — sem protocolo distribuído, sem falsificação de timeout/retry; os engines são donos disso). All-or-nothing: NADA é visível até o `commit` (o broker base segue vazio antes — medido); o `rollback` devolve o base inalterado com a contagem descartada como VALOR explícito. Prova: `KofbolFatia10E2ETest` 2/2 (JVM + Script). doing: fatia 9 (a mensageria MQ-shaped + o relatório de batch) POUSADA 15/10 — `libs/legacy/Messaging.kf`: `LegacyMessage(queue, payload, correlationId)` + o `LegacyBroker` funcional em memória (`publish` anexa, a fila criada no primeiro uso; `receive` FIFO com a disciplina do §10 — fila VAZIA é um VALOR WARNING, nunca exceção; o sinal é o STATUS, nunca um null) + `batchDecode` (§4.6: um registro ruim NUNCA mata o batch e NUNCA é silencioso — contado com o índice no `BatchReport(total, ok, failed, failedIndexes)`). Timeout/retry/dead-letter pertencem a um engine real e NÃO são falsificados (as palavras do próprio plano). O `batchDecode` vive no Legacy.kf ao lado do `decode`. Prova: `KofbolFatia9E2ETest` 2/2 (JVM + Script). doing: fatia 8 (a paridade do NÚCLEO PORTÁVEL) POUSADA 15/10 — `KofbolParityE2ETest` 4/4: o MESMO probe (fixed-width · EBCDIC · o adapter · o parser de copybook com OCCURS) byte-idêntico em JVM/Script/JS/Native x86. FRONTEIRA: a face JS do COMP-3 é o §734 (o emitter JS devolve `undefined` para a forma do unpack — catalogado ABERTO com repro single-file; JVM/Script/Native cobertos pelo EXPECTED3). A fatia de paridade fez o trabalho dela: ENCONTROU a divergência. doing: fatia 7 (a abstração de transporte + a matriz de capacidades honesta) POUSADA 15/10 — `libs/legacy/Transport.kf`: `record Transport(kind, target)` + `capabilityMatrix()` (file CONECTADO · stdio/tcp/http/mq DECLARADOS com o que precisam por §11) + `transport()` recusando tipos declarados LEG018 + a leitura/escrita de arquivo embrulhada na disciplina de RC (`readPayload`/`writePayload`; alvo ilegível LEG019). O texto do payload segue agnóstico de transporte: `readPayload(t).text` alimenta o `decode` sem mudança. Prova: `KofbolFatia7E2ETest` 2/2 (JVM + Script). doing: fatia 6 (o adapter + camada anti-corrupção + o modelo de códigos de retorno) POUSADA 15/10 — `libs/legacy/Adapter.kf`: `record LegacyResponse(returnCode, status, message)` + `response(rc, msg)` com a tabela de FAIXAS (0 OK · 4 WARNING · 8/12 ERROR · >=16 SEVERE; negativo recusado LEG017) — um RC não-zero É DADO, nunca exceção, nunca engolido (§10); a fronteira anti-corrupção (§7): `toMap` (posições → ligações NOMEADAS) + `valuesOf` (ligações → a ordem de encode; chave ausente recusada LEG016, nunca um vazio silencioso). Prova: `KofbolFatia6E2ETest` 2/2 (JVM + Script). doing: fatia 5c (REDEFINES) POUSADA 15/10 — REDEFINES elementar suportado: `record Redef(name, offset, view)` + `Copybook(..., redefines)`, o lookup de offset sobre os inícios correntes dos campos, e `decodeFrom(view, rec, offset)` para a leitura overlay (`renderRedefines` emite o layout da visão com o offset explícito). Recusados LEG014: REDEFINES de item OCCURS, REDEFINES + OCCURS, REDEFINES de grupo (o modelo de offset segue plano); LEG015 alvo não encontrado. MEDIDO + CORRIGIDO: o ponto da sentença COBOL anda no ÚLTIMO token (`COMP-3.` escapava do match de cláusula) — os dois parsers agora o tiram antes de comparar. Prova: `KofbolFatia5cE2ETest` 2/2. FATIA 5 COMPLETA: OCCURS + 88-level + REDEFINES. doing: fatia 5b (os nomes de condição 88-LEVEL) POUSADA 15/10 — o `parseCopybookFull` interpreta `88 <nome> VALUE 'lit'.` em `record Condition(name, value)` + `record Copybook(layout, conditions)` e o `renderConditions` gera as constantes nomeadas (`var FLAG-ON = "Y"`) — o TESTE da condição fica no lado da aplicação (o gerador emite valores, não booleanos). UM literal por condição: múltiplos valores / THRU recusados LEG014, malformado LEG015; o `parseCopybook` segue recusando 88 (nunca um drop silencioso). Prova: `KofbolFatia5bE2ETest` 2/2 (JVM + Script). RESTA na fatia 5: REDEFINES (segue LEG014). doing: fatia 5a (OCCURS — a repetição fixa) POUSADA 15/10 — o `parseCopybook` aceita `OCCURS <n> [TIMES]` em itens elementares (com PIC) e expande o campo em nomes com subscrito `NAME(1)..NAME(n)` (byte-exact: a largura do layout multiplica); OCCURS de grupo segue recusado LEG014 (DEPENDING ON fora de escopo); `OCCURS 0`/contagens não numéricas recusadas LEG015. Prova: `KofbolFatia5E2ETest` 2/2 (JVM + Script; o layout order de 5 campos, o round-trip `ACMEABCDEF007`, o render da expansão). RESTA na fatia 5: REDEFINES + os nomes de condição 88-level (seguem LEG014). doing: fatia 4 (o import de copybook) POUSADA 15/10 — `libs/legacy/Copybook.kf` (`parseCopybook`/`renderLayout` + `picField`/`picNines`/`picRuns`): o copybook fixed-format MVP (itens 01–49 planos com cláusula PIC, grupos pulados) → o descritor Layout → o contrato Kof gerado; a Q2 toma a rota recomendada de CLI standalone (SEM front-end de compilador). `Field` ganhou `scale` (aditivo; `decimalOf(name, digits, scale)`). O CLI: `kof legacy <copybook> [varName]` (`CmdLegacy` + `LegacyLibrary`, o padrão KofmdLibrary — main fino, toda política na lib). Códigos de gap LEG014 (cláusula ainda não suportada: OCCURS/REDEFINES/88/COMP/VALUE …) / LEG015 (malformado) — nunca um drop silencioso. Prova: `KofbolFatia4E2ETest` 2/2 (JVM + Script) + `CmdLegacyTest` 3/3 (kof-cli). doing: fatia 3 (decimal empacotado COMP-3) POUSADA 14/10 — `packComp3`/`unpackComp3` + `comp3Width` com prova COMPLETA de sinal/escala/overflow: sinais C/F/A/E positivos + D/B negativos, ponto decimal implícito na `scale`, o pad de zero à esquerda na CONTAGEM TOTAL de nibbles (dígitos + sinal), e códigos de gap LEG009 (overflow) / LEG010 (malformado) / LEG011 (nibble de dígito ruim) / LEG012 (nibble de sinal ruim) / LEG013 (largura errada). `KofbolFatia3E2ETest` 2/2 (JVM + Script; +12345 → 12 34 5C, -12.34 → 01 23 4D → "-12.34"). doing: fatia 2 (o codec EBCDIC CP037, UMA página com tabelas EXPLÍCITAS) POUSADA 14/10 — `encode37`/`decode37` + os compostos de layout `encodeLayout37`/`decodeLayout37`, códigos de gap LEG007 (byte fora da faixa de texto CP037) / LEG008 (char fora da faixa imprimível), as tabelas geradas de um codec CP037 REAL e verificadas por round-trip sobre toda a faixa ASCII imprimível (embutidas como listas decimais de bytes, o padrão split/toInt — Kof puro, todos os alvos); Q5 segue aberta para MAIS páginas. `KofbolFatia2E2ETest` 2/2 (JVM + Script). doing: fatia 1 (encode/decode fixed-width `PIC X`/`PIC 9`, ASCII) POUSADA 14/10 — biblioteca Kof pura `libs/legacy/Legacy.kf` (`textOf`/`digitsOf`/`fieldPic`/`totalLength`/`decode`/`encode`/`numericOf` + códigos de gap LEG001–LEG006) com goldens byte-exact; `KofbolFatia1E2ETest` 2/2 (JVM + Script)
next: nada — TODAS as fatias do §20 pousadas ou delimitadas (as páginas EBCDIC = Q5; a face JS do COMP-3 = §734; as cláusulas LEG014 retornam com demanda); a PROMOÇÃO para docs/ é ESTE pouso
local: docs/kofbol.pt_BR.md
state: promoted
dona: 192.168.100.14:0, flatt CLI, sem servidor opencode neste host; porta n/a

intent: kofbol-legacy-banking-interop

**Decisão `D-KOFBOL` (mantenedora 14/10):** PROMOVIDO a trabalho corrente — o plano moveu `future/` → `docs/development/`. O gate de regra 6 é **resolvido na direção que não é de regra 6**: o modelo de descritor sobre `record` (a decisão aberta **Q1** toma a opção record+descritor, NÃO uma declaração `legacy` nova) e a rota decimal inteiro-escalonado em Kof puro (**Q3**) — **sem mudança de superfície de linguagem, sem primitiva nova de core**. O plano segue **biblioteca-primeiro por construção** (`D-KOF-FIRST`): a primeira versão é expressável como bibliotecas Kof puras sobre primitivas que já existem.

**Fonte:** pedido da mantenedora (30/09/2026).

**Trabalho já decidido que este plano DEVE reutilizar (não duplicar):**
- `docs/stdlib/kof-connector-ecosystem-plan.md` — `UNDER DEVELOPMENT`
  (`D-CONNECTORS-GO`, `DECISIONS.md:4459`); COBOL já é caso declarado
  (`:438-441`, linha de capacidade `:467`, fase 7 `:520`) com a trava
  "direção, não uma demanda de 30 runtimes" (regra 55, `:448`).
- `libs/interop/` (13 arquivos Kof puros) — `ForeignModule.kf`, `CAbiConnector.kf`,
  `ConnectorSpi.kf`, `InteropCore.kf`, … o scaffolding de connector onde o
  KofBOL se pluga.
- `AbiLayout.java` + `FfiStructLayout.java` + `docs/ffi-abi-structs.md` (D6,
  concluído 23/09) — layout/arg-class binário, goldens contra GCC 13.3.
- `KofBuffer.java` (`Buffer(U8)`, D-R3-BUFFER) — a superfície de bytes.
- `KofDb.java`/`KofOrm.java` `transaction { … }` — o padrão transacional.
- `KofObservability.java` — ids de correlação/requisição.
- `docs/legacy-migration-plan.md` + `DECOMPILER.md` +
  `TRANSLATOR.md` — o cluster de migração (bytecode JVM); COBOL é nomeado lá
  como *possível frontend* (`LEGACY_MIGRATION.md:185,294,357`).

---

# KofBOL — plano (interoperabilidade e modernização incremental de legado bancário)

## 0. Propósito

> **O Kof não precisa substituir o legado de uma vez. O Kof precisa conseguir
> conversar com ele.**

O `KofBOL` é uma capacidade em camadas de **interoperabilidade e migração**
para sistemas bancários construídos sobre COBOL, runtimes mainframe e formatos
de dados legados. **Não** é um COBOL novo, **não** é um compilador COBOL e
**não** é um framework bancário. É a **ponte**, não o destino.

Pergunta de aceitação final (§30):

> Um sistema bancário antigo consegue continuar funcionando exatamente como
> funciona hoje enquanto uma nova aplicação Kof começa a substituir suas partes
> aos poucos?

## 1. Linha de base medida (novidade honesta)

A investigação (ancorada em arquivos) estabeleceu exatamente o que existe:

| Preocupação | Estado real | Evidência |
|---|---|---|
| `KofBOL` | **não existe** | busca case-insensitive → 0 hits |
| EBCDIC / code pages | **ausente** | 0 hits |
| packed decimal / COMP-3 | **ausente** | 0 hits |
| fixed-width / copybook | **ausente** | 0 hits |
| CICS / IMS / VSAM / JCL | **ausente** | 0 hits |
| MQ / dead-letter / transação MQ | **ausente** | `KofMq.java` é pub/sub em memória |
| driver DB2 | **ausente** | `DbDrivers.java` = mariadb/mysql/postgres/sqlite |
| tipo decimal / money | **ausente** | `Type.java:7-15` = Int/Long/Double/Float/Char/Byte/Short |
| socket cru user-facing | **ausente** | `KofNet.java` só URL; sockets internos (`runtime/RuntimeNet.java:12`) |
| interop nível COBOL | **nomeado, não implementado** | `kof-connector-ecosystem-plan.md:438-441,467,520`; `LEGACY_MIGRATION.md:185,294,357` |
| scaffolding de connector | **existe** | `libs/interop/` (13 `.kf`), `D-CONNECTORS-GO` |
| superfície de bytes | **existe** | `KofBuffer.java` (`Buffer(U8)`) |
| layout binário | **existe** | `AbiLayout.java`, `FfiStructLayout.java` |

**Consequência:** as preocupações de dados mainframe (EBCDIC, COMP-3,
copybooks, fixed-width, return codes, batch) são genuinamente novas; o
*scaffolding* (connectors, FFI/ABI, bytes, transações, observabilidade) não é.
Portanto o KofBOL é um esforço **library-first** que compõe costuras existentes.

## 2. Não-objetivos (explícitos)

- Sem COBOL novo. Sem `PERFORM`/`MOVE`/`COMPUTE`/`PIC`/`OCCURS`/`REDEFINES`
  como sintaxe Kof.
- Sem reprodução de JCL.
- Sem framework enterprise, sem camadas de abstração gigantes.
- Não assumir que todo legado é IBM, ou mainframe, ou um EBCDIC universal.
- Nunca converter dinheiro para `float`/`double`.
- Nunca esconder return codes; nunca transformar todo erro legado em exception.
- Sem acoplamento à JVM na superfície pública; sem criptografia própria.

## 3. Arquitetura em camadas

```text
Aplicação Kof
       ↓
API KofBOL                 (Kof idiomático)
       ↓
Legacy Contract            (estruturado, tipado, serializável)
       ↓
Encoding / Marshalling     (EBCDIC, fixed-width, COMP-3, binário, copybook)
       ↓
Transport                  (arquivo, TCP, HTTP, MQ, stdio batch, CICS, DB2…)
       ↓
Sistema legado
```

A **API de negócio não pode acoplar-se ao transporte**. Contrato é dado;
transporte é forma de mover bytes. Compõem, mas nunca se fundem.

## 4. Superfície proposta (intenção, não gramática final)

### 4.1 Contrato legado

Reusar o `record` da linguagem como base (não é preciso uma nova espécie de
declaração na v1 — decisão aberta Q1):

```kof
legacy Account {
    field accountNumber string fixed(10)
    field branch        string fixed(4)
    field balance       decimal(15, 2) packed
    field status        string fixed(1)
}
```

`legacy` é açúcar para um `record` mais um *descritor de layout* (offsets,
encodings, metadados COBOL). Permanece explícito, tipado, serializável,
validável e reutilizável.

### 4.2 Decode / encode

```kof
record = Account.decode(bytes)
bytes  = Account.encode(record)
```

Padding, truncamento, alinhamento, encoding, campos numéricos, campos
opcionais e campos inválidos devem ter, cada um, comportamento **documentado e
explícito** e erros explícitos (§10).

### 4.3 Copybook

```text
01 ACCOUNT-RECORD.
   05 ACCOUNT-NUMBER PIC X(10).
   05 BRANCH         PIC 9(04).
   05 BALANCE        PIC S9(13)V99 COMP-3.
   05 STATUS         PIC X(01).
```

Um copybook deve ser **importável** para um contrato Kof. Abordagem decidida
(recomendada, decisão aberta Q2): uma **ferramenta CLI/import standalone** que
faz o parse do copybook e **gera um contrato Kof** — o parser **não** mora no
runtime e **não** vai para o front-end do compilador. Isso mantém o runtime
pequeno e o contrato gerado revisável.

### 4.4 Transport / sessão / transação (com namespace, não um god-object)

Manter `contract`, `transport`, `session`, `transaction`, `request`, `response`
separados. Sem vazamento tipo `cics_exec`:

```kof
conn   = kofbol.connect(transport)
resp   = conn.call(AccountRequest(...))     // response.status/returnCode/message/data
conn.close()
```

### 4.5 Return codes (estruturados, nunca escondidos)

```kof
response.status       // status de nível Kof
response.returnCode   // Int — o RC legado (0/4/8/12/16/…)
response.message      // String
response.data         // payload tipado
```

O desenvolvedor mapeia `returnCode` para o modelo de erro do Kof
**explicitamente**; o KofBOL nunca o faz em silêncio.

### 4.6 Batch

```kof
job.processCustomerFile {
    input  customers
    output results
}
```

Representa intenção (`job`/`step`/`input`/`output`/`returnCode`/`dataset`) e
integra com o ambiente existente (stdio/arquivo), nunca reimplementa JCL.

## 5. Mapeamento de tipos COBOL (semântica explícita)

| COBOL | Representação KofBOL | Notas |
|---|---|---|
| `PIC X(n)` | string fixed-width, `n` bytes | EBCDIC ou ASCII conforme o encoding do contrato |
| `PIC 9(n)` | dígitos fixed-width sem sinal | dígitos zoned/display |
| `PIC S9(n)` | dígitos com sinal | encoding de sinal explícito (trailing/leading) |
| `V` (decimal implícito) | escala sobre um valor decimal | nunca float |
| `COMP` / `BINARY` | inteiro binário | big-endian vs little-endian explícito |
| `COMP-3` (packed) | decimal packed | regras do nibble de sinal explícitas |
| `DISPLAY` | dígitos de caractere | code page explícito |
| `OCCURS` | array fixo (`List<E>` / `E[]`) | limites de contagem explícitos |
| `REDEFINES` | visão union/overlay | explícita, validada |
| `88-level` | condição/booleano nomeado | explícito |

Sinal, escala, precisão, overflow, truncamento, arredondamento e encoding são
cada um uma decisão documentada; nenhum é assumido.

## 6. Fixed-width / COMP-3 / EBCDIC (o núcleo prioritário)

- **Fixed-width**: campo = `(offset, length, encoding, numeric-kind)`. O layout
  é calculado uma vez a partir do contrato (reusar a ideia do `AbiLayout`; um
  engine de layout Kof puro é o mecanismo da v1).
- **Packed decimal (COMP-3)** é prioritário. **Nunca** pode passar por
  `float`/`double`. A representação recomendada na v1 é um **inteiro escalado
  Kof puro + escala explícita** (ex.: um tipo-valor `Decimal(unscaled: Long,
  scale: Int)` ou string de dígitos), documentado para precisão/overflow. Se um
  escalar decimal de primeira classe for julgado necessário depois, é um
  **primitivo de núcleo** e decisão regra 6 (Q3); a v1 não pode precisar dele.
- **EBCDIC** é explícito e configurável por contrato (`encoding ebcdic …`).
  "EBCDIC" não é uma tabela única: o plano exige **code pages nomeados**
  (ex.: CP037, CP500, CP1140) com tabela explícita e versionada, não um default
  silencioso de backend. Conversões ASCII/UTF-8 também explícitas.

## 7. Adapter / camada anticorrupção

Os detalhes legados (`PIC`, `COMP-3`, EBCDIC, return codes) **não** podem
vazar para a aplicação. O adapter é a fronteira:

```text
Legacy Customer  →  Adapter KofBOL  →  Kof Customer
```

Contratos e adapters vivem na borda; o modelo de domínio permanece limpo.

## 8. Modernização incremental (o ponto)

```text
COBOL → Contrato KofBOL → Adapter Kof → Serviço Kof → Substituição Kof
```

Caminho realista (documentado como guia, §15):

```text
1. Identificar contrato
2. Representar contrato em KofBOL
3. Criar adapter
4. Colocar Kof na frente do legado
5. Migrar uma operação
6. Validar comportamento
7. Migrar outra operação
8. Remover dependência antiga
```

Mensagem central: **não reescreva o banco inteiro — extraia fronteiras.**

## 9. Onde o KofBOL entra na arquitetura

- **Não é construto de compilador na v1.** Library-first: uma tabela de
  dispatch de namespace (`KofBol.java`) + bibliotecas Kof puras, espelhando
  `KofValidation.java` / `KofMq.java`.
- **Contratos** são `record`s (sistema de tipos existente).
- **Bytes** andam em `Buffer(U8)` (`KofBuffer.java`).
- **Import de copybook** é subcomando de CLI (precedente: `kof-cli/.../Main.java:27-31`
  + provisionamento do `DbDrivers.java`), gerando código Kof — sem parser no
  runtime.
- **Connectors** (`kof-cobol-connector`) compõem `libs/interop/`
  (`ForeignModule`, `CAbiConnector`) conforme a fase 7 do plano de connectors.
- **Registro** (quando promovido) segue a mecânica existente: hooks de typer
  (`MethodCallTyper`/`MemberCallTyper`/`BuiltinCallTyper`/`SemUndefinedVarGuard`),
  um lowerer, runtimes por alvo (JVM / native x86-64 / cross riscv64-aarch64 /
  JS / Script), o ledger de stdlib (`scripts/stdlib_boundary.txt` +
  `scripts/check_stdlib_boundary.sh`) e o ledger R6 de paridade
  (`docs/backend-parity.md` + `DomainGapCodesTest`).

## 10. Erros e return codes

- Erros de definição/parse (contrato/copybook): `KOFBOL-P…`.
- Erros semânticos (layout inválido, code page desconhecido, overflow de
  escala): `KOFBOL-E1…`.
- Erros de runtime/transporte (timeout, disconnect, payload malformado):
  `KOFBOL-E2…`.
- **Um return code legado não-zero é dado, não exception**, por padrão.
- **Sem match / sem mensagem / RC≠0 são valores com semântica explícita**, nunca
  engolidos em silêncio e nunca lançados automaticamente.

## 11. Abstração de transporte

Separar os eixos. Transportes iniciais a investigar, cada um com matriz de
capacidade honesta por alvo: **arquivo**, **stdio batch** (`kof.process`),
**TCP** (existe `kof_net_socket_*` interno, falta superfície), **HTTP**
(`kof.http`), **MQ** (estender `kof.mq` além do pub/sub em memória), **CICS**
(via costura real de integração se viável), **DB2** (via driver pinado,
precedente `DbDrivers.java`). IMS/VSAM são prioridade menor. Não é obrigatório
que todo alvo suporte todo transporte; combinações não suportadas recebem
código de gap explícito, nunca fallback silencioso.

## 12. Messaging (formato MQ, agnóstico de engine)

Projetar uma abstração de messaging reutilizável (`send`/`receive` com timeout,
correlation id, message id, headers, retry, dead-letter, encoding, transação
quando suportada) que o IBM MQ possa lastrear depois — **não** uma API
IBM-MQ-específica na camada de negócio.

## 13. Transações

Reusar o padrão `transaction { … }` existente. Suportar
begin/commit/rollback/timeout/correlação/idempotência/retry/fronteiras. **Não**
inventar um protocolo de transação distribuída próprio; integrar com o que
existe.

## 14. Segurança e observabilidade

- **Segurança:** TLS/mTLS, certificados, secrets via `Secret`/`KeyHandle`,
  isolamento de credenciais, audit logging, integridade de mensagem, proteção
  contra replay, correlation ids, idempotência, validação de entrada/encoding.
  Sem criptografia própria — reusar `KofSecurity`.
- **Observabilidade:** request/correlation/transaction id, sistema, operação,
  duração, return code, status de transporte — via `KofObservability`. **Nunca
  logar secrets, tokens, PAN, CVV ou payload completo por padrão**; redaction é
  opt-in.

## 15. Performance

Suportar zero-copy quando possível, streaming, buffers reutilizáveis, conexões
persistentes, pooling, batching, backpressure. O pipeline
`EBCDIC → String → Object → String → EBCDIC` **não** pode ser obrigatório em
toda operação; um caminho eficiente de bytes é permitido.

## 16. Cross-target (Universal Platform)

API comum; backends variam (`adapter JVM`, `adapter Native`, `adapter JS`,
`adapter WASM`). Sem API Java-específica na stdlib pública. Capacidades que
existem só em alguns alvos são declaradas explicitamente (códigos de gap). O
modelo de dados de contrato e os codecs EBCDIC/COMP-3/fixed-width são o
**núcleo portável** (Kof puro) e devem ser cross-target desde o início.

## 17. Entregáveis de documentação (na promoção)

`docs/` ganha: o que/porquê, arquitetura, contratos legados, copybooks,
fixed-width, EBCDIC, packed decimal, transportes, messaging, batch, transações,
return codes, tratamento de erros, segurança, observabilidade, estratégia de
migração, estratégia de modernização, cross-target, limitações. Mais o guia
**"Modernizando um sistema COBOL com Kof"** (caminho da §8).

## 18. Plano de testes (quando implementado)

- **Encoding:** EBCDIC (por code page), ASCII, UTF-8.
- **Numérico:** decimal, packed decimal, COMP, com sinal, overflow.
- **Layout:** fixed-width, padding, truncamento, estruturas aninhadas, arrays,
  campos opcionais.
- **Copybook:** parser, conversão, erros, casos reais.
- **Transporte:** timeout, retry, disconnect, resposta malformada, correlação.
- **Transações:** commit, rollback, timeout, falha.
- **Segurança:** payload malformado, encoding inválido, payload gigante,
  replay, vazamento de credencial.
- **Compatibilidade:** fixtures representativas de records COBOL reais
  (goldens byte-exatos).
- **Cross-target:** decode/encode byte-idênticos na JVM + Native + JS + Script.

## 19. Decisões abertas (regra 6 — mantenedora)

1. **Q1 Declaração `legacy` nova vs `record` + descritor** — açúcar ou record?
2. **Q2 Casa do parser de copybook** — CLI/import standalone gerador
   (recomendado) vs stdlib vs front-end do compilador.
3. **Q3 Representação decimal** — tipo-valor Kof puro de inteiro escalado
   (recomendado, sem mudança de núcleo) vs primitivo decimal de núcleo (regra 6).
4. **Q4 Escopo da v1** — confirmar o conjunto prioritário da §27.
5. **Q5 Quais code pages EBCDIC** entram primeiro (CP037/CP500/CP1140…).
6. **Q6 COBOL é um `kof-cobol-connector` dentro do `D-CONNECTORS`** (framing de
   interop existente) ou um front KofBOL próprio? Isso determina se um
   `D-KOFBOL` novo é necessário ou se o trabalho anda sob `D-CONNECTORS-GO`.
7. **Q7 Superfície de transporte** — expor namespace de socket cru agora, ou
   manter transportes internos e começar com arquivo/stdio/HTTP/MQ?
8. **Q8 Backend MQ** — abstração de messaging Kof pura primeiro; quais bindings
   reais de MQ depois.
9. **Q9 Relação com `LEGACY_MIGRATION.md`** — um caminho COBOL→contrato Kof de
   *dados* faz parte do KofBOL, ou é estritamente interop (o sistema existente
   consome/alimenta o Kof)?
10. **Q10 Execução batch** — orquestração Kof pura vs executar JCL real no host.

## 20. Roadmap (cada fatia uma lane, RED-first; nada começa só deste doc)

- **Spike-0 — inventário e modelo de contrato.** Travar o modelo
  `legacy`/descritor e o engine de layout; sem parser.
- **Fatia 1 — encode/decode fixed-width** para `PIC X`/`PIC 9` (só ASCII),
  goldens byte-exatos, JVM + Script.
- **Fatia 2 — codecs EBCDIC** (um code page primeiro) com tabelas explícitas.
- **Fatia 3 — packed decimal (COMP-3)** com testes completos de sinal/escala/overflow.
- **Fatia 4 — CLI de import de copybook** gerando contratos Kof.
- **Fatia 5 — contrato `legacy` + `decode`/`encode` + `REDEFINES`/`OCCURS`/88-level.**
- **Fatia 6 — adapter + camada anticorrupção + modelo de return code.**
- **Fatia 7 — abstração de transporte** (arquivo/stdio/HTTP primeiro) + matriz de capacidade.
- **Fatia 8 — paridade Native x86-64 + riscv64/aarch64 + JS** do núcleo portável.
- **Fatia 9 — messaging (formato MQ) + abstração de batch.**
- **Fatia 10 — transações + connectors CICS/DB2/IMS/VSAM** (conforme viável).
- **Fatia 11 — docs + guia + conformance-matrix + fixtures.**

## 21. Entregáveis (implementação completa, quando promovido)

Implementação completa do KofBOL; APIs públicas; contratos; encoding;
fixed-width; packed decimal; suporte a copybook; arquitetura de adapter;
testes; fixtures; documentação; exemplos reais; changelog; e um relatório
técnico curto (arquitetura, decisões, limitações, alvos suportados/futuros,
riscos, próximos passos). **Sem TODO crítico; sem pseudocódigo; sem entrega
apenas-de-spec.**

## 22. Contrato de não-regressão

- Só aditivo; sem mudança nas superfícies congeladas (operators, precedence,
  evaluation order, null safety, content `==`, String exceptions, spawn/await,
  List/Map/Set).
- Sem mudança de tipo/gramática de núcleo na v1 (library-first).
- Cada fatia carrega testes no mesmo commit (`AGENTS.md` Q0–Q7).

## 23. Pergunta de revisão final

> **O KofBOL é a ponte, não o destino.**

Responda tecnicamente, antes de considerar concluído: o legado consegue seguir
funcionando exatamente como hoje enquanto o Kof substitui suas partes aos poucos
— e pode finalmente ser **encapsulado, integrado, substituído gradualmente e
removido quando fizer sentido**, sem rewrite big-bang? Se sim, o KofBOL está
cumprindo seu propósito.
