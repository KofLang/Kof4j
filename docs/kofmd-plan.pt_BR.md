[English](kofmd-plan.md) | [Português](kofmd-plan.pt_BR.md)

# Plano de implementação do Kofmd — Markdown tipado, orientado a intenção (D-KOFMD)

last: 3.9-hot-doc-migration
doing: closed
next: none
location: kofmd-plan
state: done
constraint: learn-training-not-migrated
decision: D-KOFMD

> **FECHADO 27/09 — todas as fatias 3.1→3.9 pousadas** (`KofmdTool` varredor,
> inferência/schema escalar `MD002`, vocabulário de memória, round-trip,
> formatter canônico, CLI `kof md`, gancho LSP, corpus golden, migração dos
> docs quentes + convenção). Fase 1 (investigação) e Fase 2 (superfície
> congelada §2) FEITAS. Decisão explícita da mantenedora 27/09/2026
> (`D-KOFMD`): o Kofmd é INDISPENSÁVEL para a 0.5.0 — a verbosidade das docs é
> o problema que ele resolve. Fila: roadmap §23. **Promovido p/ fora de
> `docs/development/` no fechamento** (regra dos 3 estados); este doc é agora o
> registro congelado + a convenção de migração (§5).

## 0. Contrato (a spec 37+13, condensada — o texto completo mora no chat)

Kofmd = **Markdown para humanos + semântica Kof para dados + intenção
explícita + memória operacional compacta para agentes.** Extensão `.md`,
degradável para Markdown puro, nunca verboso, nunca XML/YAML/JSON
disfarçado, nunca linguagem de metadados. Regra de ouro: **intenção no
menor número razoável de palavras, sem perder semântica.** `Write intent,
not narration.`

Núcleo de memória de agente (a superfície genuinamente nova): `last` /
`doing` / `next` / `location` / `state` / `instructions` / `constraint` /
`decision` / `result` / `question` / `answer` (+ `reason`, `symbol` como
modificadores).

## 1. Achados da Fase 1 — MEDIDOS contra a árvore real (27/09)

| Necessidade da spec | Infraestrutura real | Veredito |
|---|---|---|
| Lexer/parser p/ reusar | `parser/Lexer.java` (509 ln) + `parser/Parser.java` (403 ln) + `parser/AnnotationParser.java` (168 ln) + `parser/TypeParser.java` (324 ln) | Só gramática Kof — NÃO há parser Markdown em lugar nenhum da árvore (só um sniff MIME `case "md"` no `JvmMediaWebRuntime`). O parse de blocos Markdown é código novo, mas em classe própria, sem tocar a gramática Kof |
| Sintaxe `@bloco` (`@decision`…) | `AnnotationParser`: `@Name(k = v)`, constantes de compile-time, metadados de interop preservados até IR/bytecode | **Precedente real**: o prefixo `@` já parsa em Kof. As intenções de bloco do Kofmd reusam a forma `@Name` no nível do doc — zero conflito de gramática |
| Sistema de tipos p/ reusar | `Type.java`: sealed `PrimitiveType/ClassType/TypeVariable/FunctionType/ArrayType/WildcardType/UnknownType/NullableType`; primitivos = `String Int Bool Float Double Long Char Void Object` + builtins `List/Map/Set`; `record` = o portador de dados; `json.encode/decode<T>` = o precedente de serialização | **Sem `Option<T>`/`Result<T,E>`/`Date/Time/DateTime/Duration/UUID/URL/Path/Bytes` como tipos de superfície** (a lista do §6 da spec NÃO é adotada por atacado — guarda de fake-idiom, item 4 do D-KOFMD). Ausência = `String?` + narrowing; erro = `throw "msg"`. Refinamentos escalares declarados por schema (date/time/uuid/url/path) validam como `String` + checagem de formato, nunca primitivos novos |
| Declaração de schema | `record Point(Int x, Int y)` + dobra `json.decode<T>` em compile-time | Schemas Kofmd SÃO records Kof. `type TestResult {...}` (§37 da spec) se escreve `record TestResult(Int total, Int passed, Int failed)` — zero sintaxe nova de declaração |
| Portador de dados | `record` (imutável, acessores, suporte `json` JVM+JS) | Blocos de memória de agente decodificam para records; nenhum valor novo de runtime |
| Casa do CLI | dispatch no `kof-cli/Main.java` (`build/run/check/test/fmt/lsp/...`); `CmdCheck` = o precedente em forma de check | `kof md check/format/convert` segue os padrões `CmdCheck`/`Fmt`; subcomando `md` sob `Main`, uma classe por verbo (gate ≤500) |
| Casa do LSP | `LspServer.java` + `LspHover/LspSymbols/LspRename/...` | Diagnósticos/hover/completion do Kofmd pegam carona no servidor existente; hook de arquivo `.md`, sem segundo servidor |
| Relação com `kof.file` | `docs/stdlib/kof-file-plan.md` (CONCLUÍDO 28/09): API unificada de arquivo/formato, fronteira R1 (codecs pesados = pacotes oficiais), R9 interop-first | O I/O de arquivo do Kofmd (ler `.md`, escrever canônica) compõe as faces de texto do `kof.io`; nunca duplica o `kof.file` — quando o `kof.file` promover, o Kofmd pega carona |
| Infra de testes | E2E `*E2ETest` por área + goldens + `ConformanceMatrixTest` | `KofmdE2ETest` + corpus golden `kofmd/*.md` (uma ideia por arquivo, §48 da spec) |

## 2. Superfície congelada (decisão da Fase 2 — o que sai, nada mais)

**Gramática de nível de doc** (um `.md` é varrido por blocos de linha;
tudo que não for construção Kofmd é prosa preservada — o Markdown segue
válido):

```text
# Heading / paragraph / list / code / table  → preservado, zero semântica
key: value                                    → TypedField (inferência escalar: Bool/Int/Float/String)
@intent                                       → intenção de bloco para o bloco seguinte
record Name(Field: Type, ...)                 → declaração de schema (sintaxe real de record Kof)
```

**Vocabulário de memória de agente (conjunto inicial fechado, §§12–26 da
spec):** `last`, `doing`, `next`, `location`, `state`, `instructions`
(lista), `constraint`, `decision`, `result`, `question`, `answer`,
`reason`, `symbol`. Nenhuma outra chave é especial — chaves desconhecidas
são `TypedField`s planas (nunca erro; segurança §54: desconhecido ≠
executar).

**Forma canônica:** `last/doing/next/location/state` primeiro (nesta
ordem), depois alfabética; um espaço após `:`; listas como `- item`; sem
eco de prosa atrás de campo (regra zero-redundância).

## 3. Fatias (cada uma = corte vertical completo + prova, Q0–Q7)

| # | Fatia | Prova |
|---|---|---|
| 3.1 | `KofmdTool`: varredor de blocos + parse de `TypedField`/`@intent` → `record KofmdDoc` (JVM-first; demais `MD001` honesto) | goldens de parse no `KofmdE2ETest` |
| 3.2 | Inferência escalar + validação contra schema `record` (Bool/Int/Float/String; mismatch = `MD002`) | mismatch RED→GREEN |
| 3.3 | Validação do vocabulário de memória (conjunto fechado; forma de lista do `instructions`) | goldens de vocabulário |
| 3.4 | Round-trip Markdown (`Kofmd→Markdown→Kofmd` preserva semântica; prosa intocada) | goldens de round-trip |
| 3.5 | Formatter canônico (emissão determinística) | idempotência `fmt(fmt(x))==fmt(x)` |
| 3.6 | CLI `kof md check/format` (`CmdMd`, padrão `CmdCheck`) | E2E de CLI |
| 3.7 | Hook LSP (diagnósticos p/ `MD002` + hover) | estilo `LspServerTest` |
| 3.8 | Corpus golden `kofmd/*.md` + testes de leitura/escrita por IA (§§48/51 da spec) | corpus verde |
| 3.9 | Migração gradual SÓ dos docs quentes de trabalho do agente (ver §5) | consistência, não volume |

Códigos de gap: `MD001` (alvo sem backing) / `MD002` (mismatch de
tipo/schema). Ambos nomeados, nunca silêncio (R6).

## 4. Não-metas para a 0.5.0

Completion pleno no LSP, promoção do `kof.file`, tipos primitivos novos.
Tudo além da tabela acima = recusa honesta. (`migração da doc inteira` era
não-meta aqui; **substituída 27/09 por `D-DOC-SLIM`** — todo `.md` exceto
`learn/`/`training/` migra, corpus + corpo do CHANGELOG excluídos.)

## 5. Escopo da migração — fatia 3.9: docs quentes; TODA a doc por `D-DOC-SLIM` (27/09)

> **`D-DOC-SLIM` (27/09):** o escopo abaixo é o mínimo da fatia 3.9; a
> mantenedora o ampliou para todo `.md` exceto `learn/`/`training/` (corpus +
> corpo do CHANGELOG excluídos).

A migração Kofmd cobre **apenas os documentos quentes de trabalho que os
agentes leem e escrevem a cada turno**: `DOING.md`(+PT),
`docs/status.md`(+PT), cabeçalhos de fila dos `docs/development/*-plan.md`
(+PT), entradas do `CHANGELOG.md`(+PT), linhas do ledger
`docs/bugs-and-gaps/known-bugs.md`(+PT), linhas de fila do
`docs/development/roadmap.md`(+PT) §23 — i.e. os arquivos de estado
nomeados pelo loop autônomo (§"The loop": READ the state → CHOOSE →
CLAIM → EXECUTE → GO BACK).

**Explicitamente FORA do escopo: `learn/` e `training/`.** São o corpus de
ensino humano/IA (tutoriais + idiomas/anti-padrões) — prosa-first por
design, consumidos como narrativa, versionados como doutrina. Convertê-los
destruiria a legibilidade que justifica a existência deles, por zero ganho
operacional: nenhum loop de agente lê `learn/` por turno. Se uma futura
decisão da mantenedora reabrir isto, reabre como voto regra-6 próprio,
nunca como deriva dentro da fatia 3.9.

### Cabeçalho de estado de doc quente (convenção da migração)

Todo documento quente de trabalho no escopo acima **DEVE** carregar um bloco
de estado Kofmd canônico logo após o título, codificando os campos de
continuidade que o loop autônomo lê:

```text
last: <token>
doing: <token>
next: <token>
location: <doc-ou-módulo>
state: <active|blocked|done|failed>
```

`constraint` e `decision` seguem quando uma regra ou uma escolha congelada
governa o documento. O bloco é a fonte de verdade legível por máquina da
continuidade; a prosa do documento abaixo dele (claims, histórico, razão) é
preservada e nunca duplicada em campos (zero-redundância, spec §3.6). A
migração é aplicada documento a documento.
