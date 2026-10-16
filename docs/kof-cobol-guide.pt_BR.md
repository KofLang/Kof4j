[English](kof-cobol-guide.md) | [Português](kof-cobol-guide.pt_BR.md)

last: o guia do `kof-cobol-translator` (`D-COBOL-TRANSLATOR-GO`) — Fases 1–13 pousadas
doing: o entregável de promoção do §17 — ESTE documento
next: a decisão de promoção do plano
local: docs/kof-cobol-guide.pt_BR.md
dona: 192.168.100.14:0, flatt CLI, sem servidor opencode neste host; porta n/a
state: under-development

intent: kof-cobol-guide

---

# O guia do `kof.cobol` — fonte COBOL → fonte Kof

**Escopo:** o que `libs/cobol` FAZ hoje (cada fase tem prova executada —
veja `Cobol*E2ETest`) e o que RECUSA PELO NOME (CB001–CB019 + CB010 na
saída). Os tópicos compartilhados de DADOS (fixed-width, EBCDIC, decimal
empacotado, o import de copybook) moram em `docs/kofbol-guide.md` — este
guia cobre o PIPELINE do tradutor.

## 1. O pipeline (o que roda, em ordem)

```
projeto COBOL → Scanner → Lexer → AST → Semantic → DataDiv → Gen → Project
                                                      ↓
                                            Advanced (as recusas nomeadas)
                                                      ↓
                                            Report (os diagnósticos CB)
```

- **Scanner** (`scanSource`/`scanProject`): a ordem de descoberta
  determinística (§5), o inventário por arquivo (PROGRAM-ID, divisões,
  linhas/comentários, fixed-vs-free pelas marcas do §6), o dialeto por
  `--dialect` (um dialeto não determinado é RECUSADO CB002 — nunca
  adivinhado).
- **Lexer** (`tokenize`): `Token(kind, text, line)` —
  WORD/NUMBER/STRING/PERIOD/LPAREN/RPAREN/PSEUDO; as regras do §6 (o
  comentário fixed da col-7, o `*>` livre, as linhas de diretiva `>>` como
  PSEUDO); CB005/CB006.
- **AST** (`splitSentences`/`parseParagraphs`): as sentenças terminadas por
  PERIOD; as cabeças `NOME.` dos parágrafos; CB007.
- **Semantic** (`analyze`): o inventário verb-first com etiqueta de
  CONFIANÇA explícita — a tabela KNOWN mapeia, um verbo UNKNOWN é DADO
  (contado + listado pelo `unknownVerbsOf` — a lista de TODOs da migração).
- **DataDiv** (`parseDataDivision`): a região da DATA DIVISION → o `Layout`
  do KofBOL (a maquinaria de copybook REUTILIZADA, nunca duplicada) +
  `initialValues` (CB008).
- **ProcDiv** (`procedureOf`/`performs`): os parágrafos + o GRAFO de
  chamadas (`PERFORM <nome>` → `Call(from, to, count)`; um alvo inexistente
  recusado CB009).
- **Gen** (`genProcedure`): uma `nome() { }` por parágrafo (o camelCase da
  Q3), `DISPLAY 'lit'` → `println("lit")`, todo o resto recusado NA SAÍDA
  como `// CB010: verb V not mapped yet`.
- **Project** (`genProject`): `<nome>/Main.kf` + `<nome>/kof.toml` (a forma
  real de toml do repo) + `<nome>/NOTES.txt` (os diagnósticos CB).
- **Advanced** (`advancedScan`): os construtos não-traduzíveis NOMEADOS —
  `GO TO <alvo>`, `ALTER`, `PERFORM THRU`, `SET ADDRESS` — a decisão de
  migração informada.
- **Report** (`DiagReport`): os códigos CB coletados e renderizados
  (`caminho: código: mensagem`).

## 2. A semente de aceitação (provada)

O fonte GERADO COMPILA e RODA na JVM, e a saída de runtime dele é assegurada
contra o comportamento definido do programa COBOL derivado INDEPENDENTEMENTE
(`ALFA\nBETA\nGAMA`) — pelo commit E pela forma canônica
(`KofFormatter.format`, IDEMPOTENTE no texto gerado).

## 3. A matriz de conformância

| Área | Prova |
|---|---|
| Scanner: ordem de descoberta, inventário, formato | `CobolScannerE2ETest` (JVM + Script) |
| Lexer: tokens, comentários, diretivas, CB005/006 | `CobolLexerE2ETest` |
| AST: sentenças, parágrafos, CB007 | `CobolAstE2ETest` |
| Semantic: verb-first + confiança, unknowns listados | `CobolSemanticE2ETest` |
| DataDiv: a região → o Layout do KofBOL + VALUES (CB008) | `CobolDataDivE2ETest` |
| ProcDiv: os parágrafos + o grafo de chamadas (CB009) | `CobolProcDivE2ETest` |
| Gen: o subconjunto mapeável → Kof compilável (CB010 na saída) | `CobolGenE2ETest` |
| Formatter: o gerado canônico + idempotente | a face JVM do teste Gen |
| Project: o layout + kof.toml + NOTES | `CobolProjectE2ETest` |
| Report: os códigos CB coletados + renderizados | `CobolReportE2ETest` |
| Golden: o corpus de fixtures byte-exact | `CobolGoldenE2ETest` + `tests/golden/cobol-*` |
| Behavior: a saída do traduzido == o comportamento COBOL | `CobolBehaviorE2ETest` |
| Advanced: GO TO/ALTER/PERFORM THRU/SET ADDRESS nomeados + recusados | `CobolAdvancedE2ETest` |

## 4. Códigos de erro (CB001–CB019)

| Código | Significado |
|---|---|
| CB001 | sem PROGRAM-ID em um fonte |
| CB002 | dialeto não determinado — passe o dialeto, nunca adivinhado |
| CB003 | extensão de fonte não suportada |
| CB004 | formatos de fonte mistos no projeto |
| CB005 | string literal não terminada |
| CB006 | caracter inesperado |
| CB007 | sentença sem o PERIOD terminador |
| CB008 | literal VALUE nem entre aspas nem numérico |
| CB009 | alvo do PERFORM não encontrado |
| CB010 | verbo ainda não mapeado (na saída gerada, como comentário) |
| CB011–CB019 | reservados para as fases posteriores (o mapeamento profundo de Data/Procedure) |

Os códigos compartilhados LEG001–LEG019 (as superfícies de byte) estão em
`docs/kofbol-guide.md` §7.

## 5. Limitações (explícitas, nunca escondidas)

- O subconjunto mapeável hoje: `DISPLAY 'lit'`, a estrutura de parágrafos,
  as formas da DATA DIVISION que o mapeamento do KofBOL cobre. Todo o resto
  é CB010 NA SAÍDA — a lista de TODOs da migração, nunca uma mistradução
  silenciosa.
- O dialeto é do chamador (sem auto-detecção ainda — CB002).
- Sem SQL/CICS/IMS/VSAM (as fases 14+ do §30 do plano — future by design).
- A casa do CLI e a gramática exata são as decisões abertas Q1/Q2 do plano
  (da mantenedora) — a biblioteca recebe valores, nenhuma superfície de CLI
  inventada.

## 6. O caminho de modernização (o tradutor no fluxo do §8)

1. `scanProject` na árvore legada — o inventário + o dialeto + as notas.
2. `parseDataDivision` em cada programa — os contratos Layout (o mapeamento
   do KofBOL).
3. `analyze` + `advancedScan` — a lista de TODOs da migração (os verbos
   unknown + os construtos avançados nomeados).
4. `genProcedure` + `genProject` — o Kof compilável para o subconjunto
   mapeável, os gaps nomeados para o resto.
5. Feche os CB010 incrementalmente — cada verbo mapeado é uma forma legada a
   menos no domínio.