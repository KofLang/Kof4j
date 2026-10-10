last: none
doing: none-planned
next: spike-0-inventory
location: docs/development/future
state: planned

intent: kof-cobol-translator-source-to-source

> **PT** · EN canônico: [`kof-cobol-translator-plan.md`](kof-cobol-translator-plan.md)

**Portão regra 6:** o `kof-cobol-translator` é um **tradutor source-to-source**
que gera código Kof real. Não introduz sintaxe Kof nem mudança de semântica
congelada na v1, mas é uma nova superfície oficial de tooling e sua política de
saída (nomenclatura, representação de `Money`, tratamento de constructos não
suportados) é arquitetura. Este documento é **só plano, zero código**. A
promoção exige decisão da mantenedora (`D-COBOL-TRANSLATOR-GO`) mais o fluxo de
promoção de future (`docs/development/future/README.md` §"When to move",
`AGENTS.md` §"Future promotion"). A promoção está CONGELADA por
`D-FUTURE-FREEZE`; este plano é escrito sob esse congelamento, não promovido.

**Fonte:** pedido da mantenedora (02/10/2026).

**Trabalho relacionado já decidido que este plano DEVE reutilizar (não duplicar):**
- `docs/development/future/kofbol-plan.md` — **KofBOL**, a ponte de
  *interoperabilidade* de legado bancário. Seu §5 **mapeamento de tipos COBOL**
  (PIC/COMP/COMP-3/DISPLAY/EBCDIC/fixed-width/OCCURS/REDEFINES/88) e §6
  (fixed-width, packed decimal, code pages EBCDIC) são a **fonte de verdade
  semântica da representação de dados COBOL**; este tradutor deve chamar esse
  modelo, não re-inventá-lo. KofBOL é a ponte; este tradutor é o caminho de
  *migração*. A decisão aberta Q9 do KofBOL (o caminho COBOL→contrato Kof é
  KofBOL ou interop?) é **respondida aqui**: o contrato de dados é compartilhado;
  o tradutor o consome.
- `docs/development/future/LEGACY_MIGRATION.md` §4 — a **Legacy Semantic IR**
  + o modelo de confiança de 5 níveis (`Confidence.java`) + mapeamento de
  origem. COBOL já é nomeado lá como *frontend possível* (`:185,294,357`). Este
  plano instancia essa IR para COBOL.
- `docs/development/future/TRANSLATOR.md` — a arquitetura do tradutor Java→Kof
  existente (`Java Source → … → Kof AST → Kof Source`) e sua disciplina de gap
  honesto (R6: nunca emitir Kof quebrado; emitir diagnóstico).
- `kof-cli/.../Translate.java` + `TranslateLexer/Expr/Statements/Types/New/Switch/Statics`
  — o **comando `kof translate` vivo** (`Main.java:29`, `TranslateTest` 61/61).
  O front COBOL pluga nesse comando, compartilhando o contrato de
  `TranslateException`/gap honesto; **não** substitui o front Java.
- `KofFormatter.format(src, fileName)` (`kof-compiler`) — o parser+printer
  canônico; o Kof gerado **deve** passar por ele (§19, §68).
- `docs/development/future/kof-financial-plan.md` — o modelo de dinheiro
  *greenfield* (`Money` = `Long` minor units + ISO-4217); a saída
  financeiro-decimal do tradutor mira isto, nunca `Float`/`Double`.
- `docs/stdlib/kof-connector-ecosystem-plan.md` fase 7 +
  `docs/development/future/kofbol-plan.md` — `kof-cobol-connector` para o
  caminho híbrido/gradual.
- `KofProjectConfig` `[sources]` (`D-CLI-SOURCE-ROOTS`, #708) — o modelo de
  raízes de código declaradas que o projeto gerado usa.
- `CmdNew.java` (`kof new`) — o scaffold canônico de projeto (`kof.toml` +
  `src/Main.kf`) que o projeto de saída deve seguir.

**Não duplicar:** o mapeamento de dados COBOL (KofBOL §5), a IR
(LEGACY_MIGRATION §4), o front Java (`Translate.java`), o formatter
(`KofFormatter`), o modelo de dinheiro (`kof-financial`). Este plano é dono do
**front COBOL + pipeline de projeto + política de geração** que fica entre eles.

---

# `kof-cobol-translator` — plano (COBOL fonte → Kof fonte)

## 0. Propósito

Dar ao ecossistema Kof um **tradutor COBOL→Kof source oficial e
determinístico**: aponte-o para um projeto legado e receba um projeto Kof
organizado, compilável e semanticamente equivalente em um **diretório de saída
separado**, deixando o COBOL intocado.

Pergunta final de aceitação (§30):

> Dado um projeto COBOL real, o `kof translate project` produz um projeto Kof
> que compila, cujo comportamento é provadamente equivalente num corpus de
> fixtures, com todo constructo não traduzível nomeado num diagnóstico — nunca
> mistraduzido em silêncio?

## 1. A regra fundamental (anti-transliteração)

O tradutor **entende** COBOL; não substitui palavras-chave.

```text
COBOL
  ↓ lexing
tokens COBOL
  ↓ parsing
AST COBOL
  ↓ análise semântica
COBOL Semantic IR
  ↓ normalização
modelo semântico Kof
  ↓ AST Kof (ou fonte Kof canônica via KofFormatter)
projeto Kof organizado
```

**Nunca:**

```text
COBOL
  ↓ substitui palavras-chave
texto com cara de Kof
```

A mesma disciplina que o tradutor Java já impõe (TRANSLATOR.md §2) vale para
COBOL. Um constructo que não pode ser traduzido com semântica preservada vira
**diagnóstico explícito** (§21), nunca código que só parece válido (R6).

## 2. Motivação

- COBOL é a linguagem dominante dos sistemas centrais de bancos/seguros/governo.
  É caso nomeado no plano de conectores (fase 7) e no cluster de migração de
  legado.
- KofBOL (a ponte) deixa um sistema COBOL legado seguir rodando enquanto o Kof
  cresce à frente dele. Este tradutor é o **caminho de migração complementar**:
  mover um *programa* de COBOL para Kof fonte, um programa por vez, validado por
  testes diferenciais.
- A plataforma já tem as peças: um comando `kof translate` vivo, a Legacy
  Semantic IR + modelo de confiança, o mapeamento de dados do KofBOL e o
  formatter canônico. Falta o front COBOL e o pipeline de projeto.

## 3. Arquitetura

### 3.1 Pipeline

```text
diretório do projeto
  ↓ scan + descoberta + detecção de dialeto   (§5, §6)
inventário do projeto + grafo de dependências (§9, §10)
  ↓ resolve copybooks (COPY / REPLACING)       (§17)
  ↓ lex                                         (§7)
tokens COBOL
  ↓ parse (ciente de source-format)             (§8)
AST COBOL                                       (§9)
  ↓ análise semântica (dados + procedimento)    (§11, §12)
COBOL Semantic IR  (Legacy Semantic IR, LEGACY_MIGRATION §4)
  ↓ normalização                                (§50)
modelo semântico Kof
  ↓ geração (AST Kof / fonte canônica)          (§19)
  ↓ KofFormatter.format                         (§68)
  ↓ validação (kof check / compilação)          (§23, §62)
projeto Kof organizado + manifest + relatório   (§20, §24, §90)
```

### 3.2 Fronteiras dos componentes

Espelha o split do tradutor existente (`Translate.java` ≤500 + classes helper)
para que cada unidade seja um escopo coeso:

| Componente | Responsabilidade | Reuso / precedente |
|---|---|---|
| `CobolScanner` | descoberta de arquivos, extensões, exclusões, detecção de dialeto | novo; padrões de descoberta do `Deps.java` |
| `CobolLexer` | tokenização ciente de source-format | novo; forma do `TranslateLexer` |
| `CobolParser` | AST para divisões/seções/parágrafos/sentenças/verbos | novo; forma do `TranslateStatements` |
| `CobolAst` | nós da AST COBOL | novo |
| `CobolSemantics` | modelo de dados, PIC, níveis, condições | novo; **usa o mapeamento KofBOL §5** |
| `CobolIr` | Legacy Semantic IR | `LEGACY_MIGRATION.md` §4 + `Confidence.java` |
| `CobolNormalize` | normalização de fluxo/dados | novo |
| `KofEmitter` | AST Kof / fonte canônica | `KofFormatter` + idiomas do `TranslateStatements` |
| `CobolDiagnostics` | códigos/severidade/local/sugestão | novo; segue o estilo de gap-code/`known-bugs` |
| `CobolProject` | inventário, grafo, layout de saída, manifest | novo |
| `TranslateProject` | orquestração CLI (`kof translate project`) | estende `Translate.run` |

## 4. CLI

O comando `kof translate` existe (`Main.java:29`) e hoje aceita
`kof translate <file.java> [--output <file.kf>]`. O front COBOL o **estende**
sem quebrar o caminho Java:

```text
kof translate project [dir] [--output <dir>] [--dialect <name>]
                       [--strict] [--dry-run] [--verbose]
                       [--include <glob>] [--exclude <glob>]
kof translate file <file.cbl> [--output <file.kof>] [--dialect <name>]
kof translate <file.java> [--output <file.kf>]     # inalterado (front Java)
```

- O primeiro token posicional é o **modo** (`project` | `file`) ou um caminho
  Java. A compatibilidade com `kof translate Foo.java` é preservada.
- `project` sem `[dir]` usa o **diretório atual**.
- A sintaxe final deve ser validada por compilação contra o CLI real antes da
  implementação (regra do repo: investigar, depois especificar, só então
  implementar).

## 5. Scanner de projeto

- **Extensões padrão:** `.cbl`, `.cob`, `.cobol` (programas); `.cpy`, `.copy`
  (copybooks). Configuráveis.
- **Exclusões (padrão):** o diretório de saída; `.git/`, `node_modules/`,
  `target/`, `build/`, diretórios ocultos (`.*`).
- **Arquivos ocultos:** ignorados, salvo se `--include` os nomear.
- **Symlinks:** não seguidos por padrão (segurança de ciclo); uma flag opta.
- **Profundidade:** ilimitada por padrão, limitada por flag; a detecção do
  diretório de saída evita recursão (§7).
- **Encoding:** o encoding de cada fonte é detectado/declarado (`--encoding`,
  padrão ASCII/EBCDIC por dialeto); nunca assumido UTF-8 (§35).
- **Determinismo:** a ordem de descoberta é uma **ordenação estável por caminho
  relativo normalizado** (§67), nunca ordem do filesystem.

## 6. Dialetos

- Um perfil `CobolDialect` controla: source format (fixed/free), palavras
  reservadas, extensões, intrínsecas, diretivas de compilador e representações
  de dados.
- Dialetos iniciais a investigar: **IBM Enterprise COBOL**, **GnuCOBOL**,
  **Micro Focus**. A detecção é uma **heurística best-effort** e sempre
  reportada; quando ambígua, o tradutor pede `--dialect` em vez de adivinhar
  (nunca em silêncio).
- **Diretivas de compilador** (`>>SOURCE FORMAT`, `>>IF`, `>>DEFINE`,
  `COPY REPLACING`) são tratadas **antes/durante** o lexing por dialeto (§73).

## 7. Lexer

- **Source format:** fixed-format (colunas 1–6 sequência, 7 indicador, 8–72
  área A/B, 73–80 identificação) e free-format, por dialeto.
- **Comentários:** `*`/`/` na coluna 7 (fixed), `*>` (free); preservados e
  associados ao constructo mais próximo (§83).
- **Continuação** de linha, literais string/figurative-constant, level numbers,
  strings PICTURE e blocos `EXEC` são tokenizados com regras dedicadas.
- **Disciplina de bug latente** (de `TRANSLATOR.md`): o lexer **rejeita** um
  caractere inesperado com diagnóstico; nunca o descarta em silêncio.

## 8. Parser

- Recursive-descent escrito à mão (mesma forma do `TranslateStatements`), uma
  classe por preocupação, cada uma ≤500 linhas (`check_500`).
- Divisões/seções/parágrafos/sentenças/statements/expressões/condições.
- Terminadores de escopo (`END-IF`, `END-EVALUATE`, `END-PERFORM`, `END-READ`,
  …) e a fronteira de sentença por ponto são ambos tratados.
- O parser produz a **AST COBOL** (§9); **não** emite Kof.

## 9. AST COBOL

Nós para: `Program`, `IdentificationDivision`, `EnvironmentDivision`,
`DataDivision`, `ProcedureDivision`, `Section`, `Paragraph`, `Sentence`,
`Statement`, `Expression`, `Condition`, `DataItem` (com level, PIC, USAGE,
VALUE, OCCURS, REDEFINES, INDEXED BY), `FileDescription` (`FD`/`SD`),
`CopyStatement`, `ExecBlock` (SQL/CICS), `CallStatement`, `ConditionName`
(nível 88).

Todo nó carrega um **span de origem** (arquivo + linha/coluna) para diagnósticos
e source maps (§22).

## 10. IR semântica

Reutiliza a **Legacy Semantic IR** (`LEGACY_MIGRATION.md` §4): tipos, funções,
campos, chamadas, fluxo de controle, exceções, operações de memória/externas,
constantes, data flow, metadados e operações **unknown**. Unknown permanece
`UnknownType`/`UnknownCall`/`UnknownBehavior` — nunca fabricado.

Cada elemento da IR carrega um **nível de confiança** (`Confidence.java`):
`Recovered exactly` / `with metadata` / `Inferred` / `Heuristic` / `Unknown`.
A política de saída do gerador depende da confiança (§58).

A IR é independente da origem: a mesma IR poderia depois dirigir
COBOL→documentação ou COBOL→relatório de modernização (§49).

## 11. Data Division

Parse de `IDENTIFICATION` / `ENVIRONMENT` / `DATA` / `PROCEDURE`, com atenção
especial à `DATA DIVISION`:

- `FILE SECTION` (`FD`/`SD`), `WORKING-STORAGE SECTION`, `LOCAL-STORAGE`,
  `LINKAGE SECTION` (§40).
- **Level numbers:** preservar a hierarquia; nunca achatar. `01 → 05 → 10` vira
  um `record` Kof aninhado (ou tipos record aninhados), não um saco plano de
  campos.
- **Análise de PIC** (§14): `X`, `9`, `S9`, `9(n)`, `V`, `S9(7)V99`, caracteres
  de edição; sinal e escala explícitos.
- **USAGE**: `DISPLAY`, `COMP`, `COMP-1/2/3/4/5`, `BINARY`, `PACKED-DECIMAL`,
  `INDEX`, `POINTER` (§16).
- **VALUE**, **OCCURS** (`TIMES`, `DEPENDING ON`, `INDEXED BY`), **REDEFINES**,
  **nomes de condição nível 88** (§18, §19, §17).

## 12. Procedure Division

- AST para parágrafos, seções, sentenças, statements, expressões, condições.
- **Organizar por intenção**, não pela estrutura textual COBOL: um parágrafo
  que é função pura vira função Kof; uma seção que é fluxo de programa vira
  controle de fluxo estruturado; um parágrafo de transformação de dados vira
  expressão/pipeline quando a semântica permitir (§20, §51).
- A IR preserva o fluxo de controle **antes** de qualquer normalização (§29).

## 13. Tratamento de arquivos

- Modelar `FILE SECTION` (`FD`/`SD`) e os verbos `OPEN`, `READ`, `WRITE`,
  `REWRITE`, `DELETE`, `START`, `CLOSE` (§30).
- Mapear para uma abstração de arquivo Kof que preserva **modo de acesso**
  (sequential, indexed, relative, line-sequential), **formato de registro**
  (§33), **fixed-width** (§34) e semântica de **FILE STATUS** (§32).
- **Nunca** trocar `READ` por `file.read()` sem preservar o status e o layout do
  registro. A abstração de arquivo é o alvo; o mapeamento é decisão (§20
  decisões abertas).

## 14. SQL (embutido)

- Parse de `EXEC SQL … END-EXEC` (§42) e **preservação** como integração Kof DB
  estruturada (`kof.db`), nunca deletado.
- Cursors (`DECLARE`/`OPEN`/`FETCH`/`CLOSE`, §43) mapeiam para a API Kof DB.
- Onde o SQL não puder ser representado com segurança, emitir diagnóstico
  explícito e manter o SQL como fronteira documentada, nunca um descarte
  silencioso.

## 15. CICS

- Parse de `EXEC CICS … END-EXEC` (§44) e **separação da lógica de negócio da
  integração CICS**. A lógica de negócio traduz para Kof; o runtime CICS vira
  **adapter** (via `kof-cobol-connector` / transporte KofBOL). Chamadas CICS que
  não podem ser mapeadas recebem diagnóstico nomeado.

## 16. Chamadas

- `CALL` estático/dinâmico, `BY REFERENCE`/`BY CONTENT`/`BY VALUE`, `RETURNING`
  (§39, §40, §41).
- Mapear para funções/records Kof; a **passagem de parâmetros deve respeitar o
  modelo de ownership/borrowing do Kof** — sem aliasing mutável inseguro
  introduzido pela tradução. A `LINKAGE SECTION` vira a API Kof do programa.
- Quando o programa chamado está no projeto, traduzir ambos e preservar a
  relação Kof→Kof (§46).

## 17. Copybooks

- `COPY X.` e `COPY X REPLACING …` são **resolvidos antes da análise semântica**
  (§11), não tratados como texto simples.
- Um copybook vira um `record`/módulo Kof compartilhado (deduplicado),
  preservando a semântica de `REPLACING`.
- O import de copybook do KofBOL (`kofbol-plan.md` §4.3) é o precedente do
  modelo de dados; o resolvedor de copybook do tradutor é a contraparte em
  nível de código.

## 18. Fronteira JCL

- O tradutor **não** traduz JCL na v1 (§45). Ele **identifica**
  `job`/`step`/`program`/`dataset`/`parameter` para que uma futura migração
  integrada conecte uma abstração Kof de `workflow`/batch. A fronteira é
  documentada, não ignorada em silêncio.

## 19. Geração Kof

- A geração mira **Kof idiomático**, validado por compilação.
- **Preferir o formatter canônico**: emitir fonte Kof e passá-la por
  `KofFormatter.format(src, fileName)`; não montar formatação à mão quando o
  tooling existe (§68). (O front Java é anterior a isso e emite strings; o front
  COBOL deve usar o formatter.)
- **Nomenclatura** (§52, §53): COBOL `CUSTOMER-ID` → Kof `customerId`, **mas**
  manter um mapeamento explícito `original-name → kof-name` nos metadados, e
  preservar nomes onde referências externas exigirem. O mapeamento é
  armazenado, nunca implícito.
- **Comentários** (§83): preservar explicações de negócio, associá-las ao
  constructo que documentam, e manter notas de implementação legada separadas.
- **Metadados de origem** (§84): cabeçalho leve (`source:`, `translator:`,
  `translation-version:`) sem poluir o corpo do código.
- **Sem artefatos de API `PERFORM_X()` / `PARA_123()`** (§51).

## 20. Organização do projeto

- Preservar a estrutura de entrada quando possível (§8 do pedido): `src/`,
  `copybooks/`, `programs/`, `batch/` mapeiam para diretórios Kof equivalentes.
- Separar por **responsabilidade semântica** (programa / módulo / record /
  biblioteca / adapter), nunca fragmentar cada parágrafo num arquivo (§54).
- A saída é um projeto Kof real: `kof.toml` + `[sources]`
  (`D-CLI-SOURCE-ROOTS`) igual ao `kof new` (§19).

## 21. Diagnósticos

Todo diagnóstico: `code`, `severity`, `source location`, `message`,
`suggestion`. Severidades: `info`, `warning`, `error`, `unsupported`,
`manual-migration`.

- Códigos em namespace `COBOL-P…` (parse), `COBOL-S…` (semântica),
  `COBOL-U…` (não suportado / migração manual), `COBOL-T…` (projeto/IO).
- **Nunca esconder um problema** (§58): se a semântica não pode ser preservada,
  emitir diagnóstico + local + razão + ação recomendada; preferir "não consegui
  preservar esta semântica" a "gerei algum Kof".

## 22. Source maps

- Emitir mapeamentos `COBOL arquivo:linha → Kof arquivo:linha` (§55) para
  debugging, revisão, auditoria, migração gradual e comparação. Armazenados no
  manifest (§85).

## 23. Validação

- Após a geração: `kof check` / compilar o projeto gerado (§62); um erro de
  sintaxe na saída é **bug do tradutor**, não resultado aceitável.
- Onde possível, **validação semântica** compara o comportamento COBOL com o
  comportamento Kof em fixtures (§63): golden files + testes de equivalência
  comportamental.
- O sucesso de compilação é uma **métrica** (§95), não o único objetivo.

## 24. Testes

- Layout do corpus (§64): `translator-tests/input/`, `expected/`, `metadata/`,
  com entrada COBOL, Kof esperado e diagnósticos esperados por caso.
- **Corpus bancário** (§65): contas, saldo, transferência, juros, cobrança,
  batch, arquivos, conciliação, processamento de transações, datas financeiras,
  decimais — constructos COBOL comuns nesse domínio, não uma implementação de
  banco.
- Golden files devem ser **byte-estáveis** e regenerados só por comando
  explícito (nunca editados à mão para esconder regressão).

## 25. Desempenho

- Investigar parsing incremental, cache, processamento paralelo, uso de memória,
  reuso do grafo de dependências e saída determinística (§66).
- Projetos grandes: o grafo do projeto é construído uma vez; a tradução por
  arquivo é independente onde o grafo permite.

## 26. Segurança

- O tradutor lê código e escreve **somente num diretório de saída**; nunca
  muta a entrada (§3).
- Overwrite seguro (§87): nunca sobrescrever Kof editado à mão sem detecção,
  comparação e flag explícita.
- Nenhum segredo é embutido na saída; os metadados gerados não contêm
  credenciais.
- Semânticas COBOL inseguras não suportadas (pointers, `SET ADDRESS OF`) ganham
  diagnóstico, nunca Kof inseguro (§78, §79).

## 27. Compatibilidade

- A v1 não introduz **sintaxe Kof** nem **primitiva de core** (library-first,
  `D-KOF-FIRST`). O projeto gerado compila nos alvos existentes.
- O comportamento de `kof translate <file.java>` é inalterado.
- O tradutor é tooling; não altera superfícies congeladas.

## 28. Estratégia de migração

- **Tradução parcial** (§59): `Programa A → completo`, `B → parcial`,
  `C → bloqueado`; o projeto inteiro não aborta por padrão. A política de falha
  é configurável.
- **Modo estrito** (§60): `--strict` falha se qualquer constructo não tiver
  tradução garantida (uso em CI).
- **Modo best-effort** (padrão, §61): gera o máximo possível, toda limitação
  registrada explicitamente; warnings nunca mascarados.
- **Migração híbrida** (§92, §93): módulos Kof traduzidos podem coexistir com
  componentes COBOL remanescentes via `kof-cobol-connector` / KofBOL.

## 29. Limitações

- **Sem promessa de conversão universal** (§94): não 100% de todos os dialetos e
  extensões COBOL traduzem automaticamente. Os limites são explícitos e medidos
  (§95).
- Sem tradução de JCL na v1 (§18).
- Sem primitiva decimal de core na v1: valores financeiros são
  inteiro-escalado/`Money` (`kof-financial`), nunca `Float`/`Double` (§15).
- `ALTER`, `GO TO` emaranhado e semânticas de ponteiro inseguras podem ser
  intraduzíveis; viram diagnósticos `manual-migration`.
- Sem dependência de LLM (§69): a tradução base é determinística.

## 30. Roadmap (fases; cada fase uma lane, RED-first, nada começa só deste doc)

- **Fase 1 — Infraestrutura:** scanner de projeto, descoberta de arquivos,
  detecção de dialeto, inventário de fontes.
- **Fase 2 — Lexer/parser.**
- **Fase 3 — AST COBOL.**
- **Fase 4 — Modelo semântico** (Legacy Semantic IR + confiança).
- **Fase 5 — Data Division** (níveis, PIC, USAGE, VALUE).
- **Fase 6 — Procedure Division** (parágrafos, verbos, condições).
- **Fase 7 — AST/geração de código Kof** (via `KofFormatter`).
- **Fase 8 — Integração com o formatter.**
- **Fase 9 — Geração de projeto** (layout, `kof.toml`, manifest).
- **Fase 10 — Diagnósticos** (códigos, relatório).
- **Fase 11 — Golden tests.**
- **Fase 12 — Equivalência comportamental.**
- **Fase 13 — COBOL avançado** (`ALTER`, `GO TO`, `PERFORM THRU`, pointers).
- **Fase 14 — Integração SQL/CICS.**
- **Fase 15 — Otimização para projetos grandes.**

Cada fase entrega testes no mesmo commit (`AGENTS.md` Q0–Q7).

## 31. Decisões abertas (regra 6 — mantenedora)

1. **Nome e casa** — `kof-cobol-translator` como ferramenta dentro do `kof-cli`
   (como o front Java) vs módulo/artefato separado.
2. **Forma do CLI** — `kof translate project|file` como desenhado aqui, ou um
   comando top-level distinto; a gramática exata deve ser validada por
   compilação.
3. **Política de nomenclatura de saída** — `CUSTOMER-ID → customerId` com
   mapeamento armazenado (recomendado) vs preservar nomes COBOL verbatim.
4. **Saída de dinheiro/decimal** — mirar o `Money` do `kof.financial`
   (recomendado, pendente de `D-FINANCIAL-GO`) vs um tipo local de
   inteiro-escalado vs esperar uma primitiva decimal de core (regra 6).
5. **Profundidade da resolução de copybook** — semântica completa de `REPLACING`
   na v1 vs um subconjunto com diagnósticos.
6. **Relação com o KofBOL** — compartilhar o mapeamento de dados do KofBOL como
   fonte única de verdade (recomendado) vs um mapeamento independente do
   tradutor.
7. **Escopo de dialetos da v1** — qual(is) dialeto(s) saem primeiro
   (IBM/GnuCOBOL/Micro Focus).
8. **Forma do projeto gerado** — fonte canônica via `KofFormatter` (recomendado)
   vs construção de AST Kof.
9. **Política padrão de saída parcial** — best-effort (recomendado) vs estrito.
10. **Alvo da abstração de arquivo** — a forma da API Kof de arquivo para
    acesso sequential/indexed/relative.

## 32. Registros de decisão

Decisões arquiteturais são registradas como `D-COBOL-001`, `D-COBOL-002`, …
cada uma com **Context / Problem / Options / Decision / Consequences**, quando a
promoção começar. Registros-semente da v1: escolha de pipeline/IR, reuso do
formatter, política de nomenclatura, representação decimal, resolução de
copybook, taxonomia de diagnósticos.

## 33. Matriz de constructos

Expandida durante a pesquisa; cada linha é preenchida com o alvo real antes de
sua fase fechar. `Direct` = constructo Kof direto; `Adapter` = precisa de
biblioteca/adapter Kof; `Unsupported` = diagnóstico.

| Constructo COBOL | Parser | Semantic IR | Equivalente Kof | Direct | Adapter | Unsupported | Testes |
|---|---|---|---|---|---|---|---|
| MOVE | | | atribuição + conversão | | ✓ | | |
| COMPUTE | | | expressão aritmética | ✓ | | | |
| ADD/SUBTRACT/MULTIPLY/DIVIDE | | | aritmética + ROUNDED/ON SIZE ERROR | | ✓ | | |
| IF | | | `if` / if-expression | ✓ | | | |
| EVALUATE | | | `switch` / `match` | ✓ | | | |
| PERFORM (inline/TIMES/UNTIL/VARYING) | | | loop / chamada de função | | ✓ | | |
| PERFORM THRU | | | normalização de fluxo | | | ✓ (diagnóstico) | |
| GO TO | | | state machine / labels | | | ✓ (quando inseguro) | |
| ALTER | | | (analisar) | | | ✓ | |
| READ/WRITE/REWRITE/DELETE/START/OPEN/CLOSE | | | API de arquivo Kof | | ✓ | | |
| FILE STATUS | | | valor de status estruturado | | ✓ | | |
| CALL (REFERENCE/CONTENT/VALUE) | | | função/record Kof | | ✓ | | |
| COPY / REPLACING | | | record/módulo compartilhado | ✓ | | | |
| REDEFINES | | | overlay/union view | | ✓ | | |
| OCCURS / DEPENDING ON / INDEXED BY | | | `List`/array + limites | | ✓ | | |
| condição nível 88 | | | predicado/enum tipado | | ✓ | | |
| PIC S9(n)V99 COMP-3 | | | `Money`/decimal escalado | | ✓ | | |
| COMP / BINARY | | | inteiro | ✓ | | | |
| DISPLAY / EBCDIC | | | code page explícito | | ✓ | | |
| EXEC SQL | | | `kof.db` | | ✓ | | |
| EXEC CICS | | | lógica de negócio + adapter | | ✓ | | |
| STRING / UNSTRING / INSPECT | | | API de string | | ✓ | | |
| SEARCH / SEARCH ALL | | | busca/ordenação de `List` | | ✓ | | |
| SORT / MERGE | | | biblioteca de batch/sort | | ✓ | | |
| POINTER / SET ADDRESS OF | | | (inseguro) | | | ✓ | |
| FUNCTION intrínseca | | | `kof.math`/`kof.time`/… | | ✓ | | |

## 34. Pergunta final de revisão

> **O `kof translate project` transforma um projeto COBOL real num projeto Kof
> real — compilado, tipado, organizado e validado comportamentalmente — com todo
> gap nomeado e os fontes COBOL intocados?**

Se sim, o tradutor cumpre seu propósito. Se qualquer resposta for "gerou texto
com cara de Kof", ele falhou a regra fundamental (§1).
