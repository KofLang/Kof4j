last: none
doing: none-planned
next: spike-0-inventory
location: docs/development/future
state: planned

intent: kof-expr-native-text-pattern-dsl

**Gate regra 6:** `Kof.expr` é uma SINTAXE nova + SEMÂNTICA nova (superfície de
semântica congelada). Este documento é **apenas plano, zero código**. A promoção
exige decisão da mantenedora (`D-KOF-EXPR` ou extensão de `D-STR-UNICODE`) e o
fluxo de promoção de futuro (`docs/development/future/README.md` §"When to move",
`AGENTS.md` §"Future promotion"): reescrever com `UNDER DEVELOPMENT`, estado real
+ how-to-finish, enfileirar em `roadmap.md` §23, apontar `docs/status.md` para
ele, reivindicar em `DOING.md`.

**Fonte:** pedido da mantenedora (30/09/2026). Decisões relacionadas:
`DECISIONS.md` §D-STR-UNICODE (27/09/2026) — o engine de regex é adiado para 1.0
e é domínio pesado que pertence a um pacote oficial, não a um enxerto de runtime;
`STR003` hoje barra os três membros de regex. `Kof.expr` é candidato a responder
a esse adiamento, mas **não** é um engine de regex com nomes bonitos.

---

# Kof.expr — plano (DSL nativa e declarativa de padrões de texto)

## 0. O propósito em uma frase

> `Kof.expr` existe para que o programador descreva a **intenção de um padrão de
> texto** sem precisar pensar como uma máquina de regex.

A pergunta de aceitação para toda decisão de design é:

> "Alguém que nunca viu regex conseguiria escrever uma expressão de matching
> depois de ler a documentação do Kof.expr?"

Se a resposta for não, a API está errada.

Corolário (o anti-objetivo): **`Kof.expr` não é regex com nomes mais bonitos.** A
abstração pública é *semântica* (`letter`, `one_or_more`, `capture`, `between`) e
o engine de regex — se houver — é detalhe interno de lowering que o usuário nunca
vê.

## 1. Linha de base medida (por que isto existe)

A investigação (ancorada em arquivos) estabelece o ponto de partida:

- **Não existe DSL de padrões de primeira classe.** Os únicos toques em regex na
  superfície Kof são os três membros `String` `matches` / `replaceAll` /
  `replaceFirst`, barrados por `STR003` (`StringTargetGaps.java:31-43`), e
  `validation.matches` (`KofValidation.java:50`).
- **Os toques em regex já divergem entre alvos.** `String.split` e
  `String.replace` usam regex Java de verdade na JVM
  (`jvm/JvmOpEmitter.java:190-222`) e no Script
  (`KofInterpreterCollections.java:74-80`), mas são literais/byte na JS
  (`js/JsRuntimeCore.java:257-263`) e no Native
  (`runtime/RuntimeStringEdit.java:188-219`; `nat/NativeX86StringCalls.java:252`).
  `validation.matches` é regex real na JVM/JS e busca literal de substring no
  Native (`runtime/RuntimeValidation.java:214-256`;
  `nat/NativeRiscvAsmRtB3.java:174-207`). A divergência não está fixada em
  `conformance-matrix.md`.
- **`DECISIONS.md` §D-STR-UNICODE (27/09/2026)** adia o engine de regex para 1.0
  e o classifica como domínio pesado de pacote oficial.
- **Uma nova espécie de declaração é caminho conhecido**: keyword no lexer
  (`parser/Lexer.java:13-85`), dispatch de topo (`parser/Parser.java:41-86`),
  symbol table (`SymbolTableBuilder.java:15-125`), dispatch semântico
  (`SemanticAnalyzer.java:241-253`), lowering para IR
  (`CompilerPipeline.lowerToIR:215-275`), com `CompilerEnumLowering.java` como
  modelo canônico de declaração embutida rebaixada para ops IR genéricas que os
  quatro alvos já suportam.
- **Paridade de pipeline é por construção**: o mesmo parser → semântica →
  lowering → otimização alimenta o emit JVM/Native/JS e o interpretador Script
  (`CompilerPipeline.analyzeAndLower:318-363`). Um construto que rebaixa para
  ops `Kof*` existentes atinge todos os alvos — inclusive o interpretador — de
  graça.

O plano abaixo foi desenhado para que **a v1 não toque nenhum backend por alvo**:
é um construto de front-end que rebaixa para o runtime de String existente mais
um pequeno runtime de matching uniforme expresso com ops existentes.

## 2. Não-objetivos (v1)

- Não substituir a semântica de `String.matches`/`replaceAll`/`replaceFirst`
  (decisão separada de `D-STR-UNICODE`; `STR003` permanece).
- Não correr atrás de features estilo PCRE (lookbehind, backreferences, grupos
  atômicos, condicionais). Incluir só o que expressa intenção.
- Não criar um segundo parser de uma "mini-linguagem dentro de uma string". O
  `Kof.expr` é sintaxe Kof de verdade.
- Não criar backend novo nem caminho específico de alvo na v1.
- Sem reflection, sem macros, sem processamento de anotações.
- Sem dependência de biblioteca de regex de terceiros para a superfície. Um
  engine, se usado, é detalhe interno (§9).

## 3. Superfície proposta (intenção, não gramática final)

A superfície deve ler como Kof e compor como Kof. Duas formas candidatas:

**Forma A — bloco de declaração** (mais rica; estende a família
`enum`/`record`/`class`):

```kof
expr email {
    start
    one_or_more { letter digit "." "_" "%" "+" "-" }
    "@"
    one_or_more { letter digit "." "-" }
    "."
    between(2, 63) letter
    end
}
```

**Forma B — expressão de valor** (compõe dentro de funções, records,
constantes — sem nova espécie de topo):

```kof
val email = one_or_more { letter digit "." "_" "%" "+" "-" }
    + "@"
    + one_or_more { letter digit "." "-" }
    + "."
    + between(2, 63) letter
```

O plano recomenda **A como declaração e B como valor componível**, sobre a mesma
árvore de expressão: `expr nome { … }` é açúcar para vincular um valor de padrão
nomeado, exatamente como `enum` vincula um tipo nomeado. Corpos `expr` são
construídos de *átomos semânticos* primitivos e *combinadores*; um `expr`
nomeado pode ser referenciado por nome dentro de outro `expr` (composição, §5).

### 3.1 Átomos semânticos (v1)

| Átomo | Significado (documentado por alvo, §11) |
|---|---|
| `start`, `end` | âncora no início / fim do sujeito |
| `literal("…")` ou `"…"` puro | sequência literal exata |
| `any` | exatamente um caractere (escalar Unicode) |
| `letter`, `digit`, `whitespace` | classes Unicode |
| `word` | a classe de palavra documentada (letter/digit/`_` — §11) |
| `hex` | `[0-9A-Fa-f]` (documentado, ASCII por definição) |
| `one_of("a", "b", …)` | pertinência (idioma Kof `setOf(...).contains`) |
| `none_of("a", "b", …)` | complemento da pertinência |

### 3.2 Quantificadores (v1)

`optional { … }`, `zero_or_more { … }`, `one_or_more { … }`,
`exactly(n) { … }`, `between(min, max) { … }` (inclusivo; `max` pode ser aberto —
questão aberta §16 Q3).

Quantificadores aplicam à *unidade seguinte* — um átomo ou um bloco. Este é o
único ponto em que `Kof.expr` toma emprestada a noção regex de "o próximo
elemento", e ela é explicitada com chaves para ninguém precisar lembrar
precedência de regex.

### 3.3 Agrupamento, alternativa, captura (v1)

- Agrupamento é bloco `{ … }` (ou `(...)` se a gramática preferir; questão
  aberta §16 Q1) — sem ofuscação `(?:…)`.
- Alternativa é explícita: `either { … or … }` (ou `one_of` para caracteres
  únicos). Sem `|` por padrão.
- Captura é nomeada e explícita: `capture("nome") { … }`. Captura sem nome não é
  necessária na v1.
- Repetição de grupo usa os mesmos quantificadores dos átomos.

### 3.4 Os exemplos obrigatórios (superfície-alvo)

Estes são os exemplos exigidos pela documentação, escritos na superfície
proposta. **Ainda não são executáveis** (isto é plano); quando o plano for
promovido viram corpus `E2E` e devem rodar byte-idênticos em todos os alvos.

```kof
expr identifier {
    one_or_more { letter digit "_" }
}

expr email {
    start
    one_or_more { letter digit "." "_" "%" "+" "-" }
    "@"
    one_or_more { letter digit "." "-" }
    "."
    between(2, 63) letter
    end
}

expr uuid {
    exactly(8) hex
    "-"
    exactly(4) hex
    "-"
    exactly(4) hex
    "-"
    exactly(4) hex
    "-"
    exactly(12) hex
}

expr phone {
    optional("+55")
    optional(" ")
    optional("(")
    exactly(2) digit
    optional(")")
    optional(" ")
    exactly(4) digit
    "-"
    exactly(4) digit
}

expr ipv4 {
    octet
    "."
    octet
    "."
    octet
    "."
    octet
}

expr octet {
    between(1, 3) digit
}

expr date {
    capture("day")   { exactly(2) digit }
    "/"
    capture("month") { exactly(2) digit }
    "/"
    capture("year")  { exactly(4) digit }
}

expr logLine {
    capture("time")  { exactly(2) digit ":" exactly(2) digit }
    " "
    capture("level") { one_of("INFO", "WARN", "ERROR") }
    " "
    capture("msg")   { zero_or_more any }
}
```

Observe `octet` demonstrando **composição por nome**: `ipv4` reusa um `expr`
definido antes. (`octet` como escrito casa 1–3 dígitos, não um octeto real
0–255; um octeto correto é exemplo de *composição + alternativas explícitas* e
fica para a fatia de promoção, §16 Q6.)

## 4. Onde o `Kof.expr` entra na arquitetura

Seguindo exatamente o modelo do `enum`:

1. **Lexer** (`parser/Lexer.java:13-85`): adicionar `expr` como keyword
   contextual (como `sealed` — `parser/TypeDeclarations.java:56-61`,
   `ParseContext.sealedModifierAhead()`), para não quebrar código existente que
   usa `expr` como identificador (compatibilidade aditiva).
2. **Dispatch de topo** (`parser/Parser.java:41-86`,
   `parser/TypeDeclarations.java:35-45`): reconhecer `expr NOME { … }`.
3. **AST**: um novo arquivo de nó (um record por arquivo, convenção do repo),
   ex. `ExprDeclarationNode.java`, mais a hierarquia de nós de padrão
   (átomo/quantificador/grupo/alternativa/captura). Nós carregam `SourcePosition`
   para diagnóstico.
4. **Symbol table** (`SymbolTableBuilder.java:15-125`): registrar um
   `ExprSymbol` (nome + descritor de padrão normalizado). Um `expr` referenciado
   dentro de outro resolve para `ExprSymbol` — o mesmo mecanismo de referência
   de tipo.
5. **Análise semântica** (`SemanticAnalyzer.java:241-253` + satélite
   `SemExprAnalyzer.java`): validar domínio (contagem de quantificador é `Int`
   ≥ 0; `between` exige `min ≤ max`; nomes de captura duplicados; átomo
   desconhecido; referência `expr` desconhecida; posição de `start`/`end`).
   Erros usam nova família de códigos (§12).
6. **Lowering** (`CompilerPipeline.lowerToIR:215-275` + novo
   `CompilerExprLowering.java`): rebaixar o padrão para **ops IR genéricas
   existentes** — sem nova op de backend se evitável. A estratégia (§9) é
   construção portátil do matcher, então a IR emitida é um `KofCall` para um
   runtime `kof_expr_*` que o alvo já sabe emitir, ou (preferida na v1) uma
   biblioteca Kof pura (§8).
7. **Superfície de métodos**: `match` / `matches` / `find` / `replace` /
   `captures` são métodos no valor de padrão, implementados com o runtime de
   `String` + coleções já presente em todos os alvos.

## 5. Composição, módulos e namespacing

- Um `expr` nomeado é referenciável por nome onde se espera um valor de padrão.
- Declarações `expr` obedecem às regras de visibilidade/import existentes (um
  `expr` `public` do pacote `p` é importado como qualquer símbolo). Sem sistema
  de módulos novo.
- Padrões compõem com constantes/`val` e funções porque são valores comuns;
  `phone` na §3.4 pode ser um `val` (Forma B) se a mantenedora preferir não ter
  nova espécie de topo.
- Ciclos (`a` referencia `b` referencia `a`) são erro semântico (§12, família
  `KOF-E100x` de padrão recursivo), não expansão infinita.

## 6. Capturas e tipagem do resultado

O resultado de um match não pode ser um `Map<String, Any>` de conveniência.
Duas opções, decididas na promoção (§16 Q4):

- **Opção T (tipada por padrão, preferida):** cada `expr` com capturas induz um
  tipo-valor pequeno (um `record`) cujos campos são as capturas (`date.day`,
  `date.month`, `date.year`). O compilador já tem lowering de record e tipagem
  nominal (`CompilerIfaceRecordLowering.java`, `ClassShapeChecks`,
  `DeclaredTypeChecker`). É a opção mais coerente com Kof e mais segura; o custo
  é um tipo gerado por `expr` com captura.
- **Opção U (tipo de resultado uniforme):** um único valor `Match` expondo
  `captured("day"): String?` e `capturedInt("day"): Int?`; honesto, mas fracamente
  tipado. Recomendada só como fallback se tipos gerados se provarem caros.

Conjunto de operações sobre um valor de padrão (mantido explicitamente distinto —
sem ponto de entrada único ambíguo):

```kof
email.matches(text)     // Bool — match do sujeito inteiro (ancorado)
email.match(text)       // Match? / record? — sujeito inteiro + capturas
email.find(text)        // índice Int ou Match? — primeira ocorrência, não ancorado
email.replace(text, replacement)   // String — literal ou ciente de captura
email.findAll(text)     // List<Match> — todas as ocorrências
```

`matches` = match completo; `find` = busca; `replace` = transformação;
`captures` = extração. Nomes distintos para intenções distintas.

## 7. Transformação

- `replace(text, replacement)`: substituição literal na v1.
- Substituição ciente de captura (uma função do record de captura para `String`,
  ou um template pequeno com placeholders explícitos como
  `replace(text) { m -> m.day + "/" + m.month }`) é extensão avaliada (§16 Q5).
  Incluir só se permanecer reveladora de intenção; **não** criar uma
  mini-linguagem `$1`/`\1`.

## 8. Duas arquiteturas candidatas (decididas na promoção)

**Estratégia P — biblioteca Kof pura sobre um runtime de matcher (recomendada na
v1).** Construir o padrão como valor Kof (uma pequena árvore de records) e casar
com um interpretador Kof puro sobre primitivas de `String` que já existem em
todos os alvos (`kof_string_length`, `charAt`/`toCharArray`, `substring`,
comparações). Sem engine de regex, sem op de backend nova, paridade total por
construção (inclusive o interpretador Script). Custo: vazão menor que um engine
compilado para entradas muito grandes; aceitável na v1 e eliminável depois
(Estratégia C). É `D-KOF-FIRST`: Kof expressa com suas próprias primitivas.

**Estratégia C — compilar para um matcher especializado (depois).**
Quando performance exigir, rebaixar um padrão para IR/assembly especializado por
alvo (ou para o regex nativo do alvo: `java.util.regex` na JVM, `RegExp` na JS,
um pequeno runtime NFA no Native) **atrás da mesma API pública**. O usuário nunca
escolhe. É aqui que §9 (performance) e §10 (ReDoS) precisam ser engenheirados;
pode ser introduzido fatia a fatia sem mudar a superfície.

O plano recomenda **P primeiro**, com a representação interna escolhida de modo
que C seja substituição direta (o descritor de padrão é serializável/compilável).

## 9. Estratégia por alvo e paridade

Como a v1 rebaixa para ops existentes de String/coleções, os cinco runtimes
(JVM, Native x86-64, Native riscv64/aarch64, JS, Script) executam a mesma
semântica. A convenção de nomes segue o repo: `kof_expr_<op>` (Native/JS/
interpretador, camelCase na JS como de praxe: `kof_expr_x` → `kofExprX`, ver
`js/JsTypeMapper`), e o lado JVM passa pela allow-list do `KofRuntime.java`
gerado (`jvm/JvmRuntime.java:23-91`). Nada disso é exposto publicamente.

Duas divergências conhecidas devem ser **decididas, não herdadas**:

- `String.split`/`replace` regex-vs-literal: `Kof.expr` deve oferecer uma
  operação tipo `split`/`findAll` com semântica *documentada*; não pode
  adicionar silenciosamente outro comportamento divergente.
- `validation.matches`: uma vez existindo `Kof.expr`, `validation.matches` pode
  ser reexpresso em termos dele (follow-up, não v1), removendo a divergência
  JVM-regex/Native-literal registrada acima.

## 10. Semântica Unicode (explicitude não negociável)

`Kof.expr` não pode definir silenciosamente `letter = [A-Za-z]`. O plano:

- O modelo de sujeito é o modelo de string Kof (UTF-8 + NUL no Native,
  `docs/runtime/STRING_MODEL.md`); "caractere" é um valor escalar Unicode, e o
  acesso a caractere é por code point, não por unidade UTF-16 ou byte.
- `letter`/`digit`/`whitespace`/`word` são definidos por uma tabela explícita de
  categorias Unicode embarcada com a biblioteca (documentada, versionada), não
  pelo backend. `hex` é ASCII por definição.
- `any` = um escalar Unicode. `any` orientado a byte não é oferecido na v1.
- Normalização (NFC/NFD) **não** é implícita; um combinador `normalized(...)`
  pode ser adicionado explicitamente se demandado (§16 Q7).
- A versão Unicode exata fixada pela biblioteca deve ser registrada em
  `docs/backend-parity.md` (mesma disciplina das faces `STR`/Unicode).

## 11. Performance

- Padrões são **compilados uma vez** na inicialização da declaração/`val`, não
  por chamada; `matches` repetidos reusam a forma compilada (cache por
  identidade do valor de padrão).
- A Estratégia P é O(n·m) pior caso para um matcher backtracking direto; o plano
  **proíbe** backtracking ilimitado por construção (§12). O matcher recomendado
  para a v1 é estilo Thompson/NFA ou com passo explicitamente limitado, para que
  backtracking catastrófico não possa ocorrer (isto é escolha de *design*, feita
  agora, não remendo posterior).
- Alocação: resultados de match só alocam em `match`/`findAll`, não em
  `matches`.
- Limites de entrada são explícitos e documentados (§12); sem buffers de captura
  ilimitados.

## 12. Segurança (seguro por padrão)

- **ReDoS é projetado para fora**: o engine da v1 não pode usar backtracking
  ingênuo com repetição ilimitada. Se a Estratégia C usar um engine do host,
  padrões com potencial catastrófico são rejeitados em compile-time ou executados
  com orçamento documentado de passos/tempo.
- Entrada não confiável: comprimento máximo de sujeito documentado e orçamento
  de passos, com diagnóstico distinto quando exceder (família `KOF-E2xxx` em
  runtime), nunca um travamento.
- Padrões construídos dinamicamente (valor de padrão montado em runtime) são
  permitidos só pela API de combinadores checada por tipo — um padrão nunca pode
  ser construído de uma string de regex crua, então injeção de sintaxe regex é
  impossível por construção.

## 13. Diagnósticos (erros devem ensinar)

Família dedicada (códigos finais na promoção), emitida com arquivo/linha/coluna
+ contexto, seguindo `DiagnosticCollector` (`DiagnosticCollector.java:25-57`):

- Erros de definição/parse (`KOF-P…`): `}` faltando, combinador desconhecido,
  literal inválido.
- Erros semânticos (`KOF-E1…`): `between(3, 2)`, `exactly(-1)`, nome de captura
  duplicado, referência `expr` desconhecida, padrão recursivo, quantificador
  aplicado a nada.
- Erros de runtime (`KOF-E2…`): sujeito longo demais, orçamento de passos
  excedido.
- **Não-casar não é erro**: `matches` retorna `false`, `match` retorna `null`
  (nullable, consistente com a nullability do Kof), `find` retorna `-1`/`null`
  conforme a assinatura escolhida. Essa distinção é documentada explicitamente.

Todo código novo deve ser adicionado a `docs/backend-parity.md` (o ledger R6
checado por `DomainGapParityMatrixTest.everyPinnedGapIsDocumentedInTheParityMatrix`),
ainda que a v1 não tenha lacunas de alvo.

## 14. Checklist de integração (arquivos que uma fatia de promoção toca)

Modelado no caminho do enum; cada um referenciado com sua faixa de linhas atual:

| Estágio | Arquivo |
|---|---|
| Keyword contextual | `parser/Lexer.java:13-85` |
| Dispatch de topo | `parser/Parser.java:41-86`, `parser/TypeDeclarations.java:35-45` |
| Nós AST | novo `ExprDeclarationNode.java` + records de nós de padrão |
| Symbol table | `SymbolTableBuilder.java:15-125` |
| Semântica | `SemanticAnalyzer.java:241-253` + novo `SemExprAnalyzer.java` |
| Lowering | `CompilerPipeline.lowerToIR:215-275` + novo `CompilerExprLowering.java` |
| Tabelas de runtime | `KofExpr.java` (gate/namespace + `supportedOn`), `jvm/JvmRuntime.java:23-91`, `js/JsRuntimeOps.java`, `nat/NativeRiscvCrossOps.java`, arquivos nativos estilo `RuntimeValidation`, `KofInterpreterCollections.java` |
| Ledger | `scripts/stdlib_boundary.txt`, `docs/backend-parity.md` |
| Docs | `docs/language-reference/*`, `docs/stdlib/*`, este plano movido para `docs/` |
| Corpus | `training/idioms/` (novo `kof-expr.md`), `training/anti-patterns/fake-idioms.md` (o que `Kof.expr` *não* é) |

## 15. Entregáveis de documentação (na promoção)

`docs/` ganha: o que/porquê, filosofia da sintaxe, literais, classes semânticas,
quantificadores, composição, capturas, match, busca, replace, Unicode,
performance, segurança, cross-target e exemplos reais. Seção obrigatória
**"Kof.expr vs Regex"** explicando que *regex descreve como caracteres devem ser
reconhecidos através de notação compacta, enquanto Kof.expr descreve a intenção
do padrão através de construções explícitas* — não um diff superficial de
sintaxe.

## 16. Decisões abertas (regra 6 — mantenedora)

1. **Q1 Bloco vs parênteses** para alvo de agrupamento/quantificador: `{ … }`
   em tudo (recomendado) ou `( … )`.
2. **Q2 Declaração `expr` vs só `val`** (Forma A vs B) — a linguagem ganha nova
   espécie de topo, ou um valor componível basta?
3. **Q3 `between(min, max)` aberto** — permitir `max` omitido (significa
   "≥ min")?
4. **Q4 Tipagem do resultado de captura** — Opção T (record gerado, preferida)
   vs Opção U (acessador `Match`).
5. **Q5 Replace ciente de captura** — incluir forma de template por função na
   v1?
6. **Q6 Expansão numérica/estrutural** (ex. octeto real de IPv4, IPv6) — na
   biblioteca, não no core.
7. **Q7 Normalização** — oferecer `normalized(...)` explícito?
8. **Q8 Destino das divergências de `validation.matches` e
   `String.split/replace`** quando `Kof.expr` existir.
9. **Q9 O engine** — v1 Kof puro (P) é a recomendação; confirmar que engines
   nativos (JVM `Pattern`, JS `RegExp`) são aceitáveis depois como lowerings
   *internos*.
10. **Q10 Nomes** — `Kof.expr` vs namespace `kof.expr` (`KofExpr.java`) vs
    espécie de linguagem. A grafia do pedido é `Kof.expr`; confirmar que a
    superfície canônica (`expr` keyword + namespace de runtime `kof.expr`) é
    aceitável.

## 17. Roadmap em fases (cada fatia é uma lane, RED-first)

- **Spike-0 — inventário (companheiro deste doc, fatia 0 de promoção).** Travar
  gramática e AST; escrever os testes de parser primeiro (sem lowering).
- **Fatia 1 — átomos + sequência + literal + match/matches** na JVM + Script
  (Estratégia P), conferidos byte a byte.
- **Fatia 2 — quantificadores** (`optional`/`zero_or_more`/`one_or_more`/
  `exactly`/`between`).
- **Fatia 3 — classes** (`letter`/`digit`/`whitespace`/`word`/`hex`/`any`) com a
  tabela Unicode fixada.
- **Fatia 4 — grupos + `either` (alternativa) + `one_of`/`none_of`.**
- **Fatia 5 — capturas + `match`/`captures` + tipagem do resultado (Opção T).**
- **Fatia 6 — paridade Native x86-64 + riscv64/aarch64.**
- **Fatia 7 — paridade JS.**
- **Fatia 8 — `find`/`findAll`/`replace` + orçamentos de segurança + benchmarks
  de performance.**
- **Fatia 9 — docs + corpus + conformance-matrix + os exemplos obrigatórios
  compilados e testados.**

Cada fatia deve ser promovida explicitamente; nenhuma começa só a partir deste
documento.

## 18. Contrato de não-regressão

- Só aditivo: identificadores existentes chamados `expr` continuam funcionando
  (keyword contextual).
- `STR003` e todo o comportamento existente de String/`validation` inalterados
  na v1.
- Sem mudança nas superfícies congeladas (`operators`, `precedence`,
  `evaluation order`, `null safety`, `content ==`, `String exceptions`,
  `spawn/await`, `List/Map/Set`).
- Cada fatia carrega testes no mesmo commit (`AGENTS.md` Q0–Q7).

## 19. Pergunta de revisão final (antes de promover → implementar)

> "Se eu tivesse que ensinar `Kof.expr` para alguém que nunca viu regex, essa
> pessoa conseguiria escrever uma expressão depois de ler a documentação?"

Se não — simplifique. Se sim — o plano está pronto para ser promovido para
`docs/development/` e implementado de ponta a ponta, começando no Spike-0.
