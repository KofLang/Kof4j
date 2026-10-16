[English](kof-cobol-guide.md) | [Português](kof-cobol-guide.pt_BR.md)

last: the guide for `kof-cobol-translator` (`D-COBOL-TRANSLATOR-GO`) — Phases 1–13 landed
doing: the §17 promotion deliverable — THIS document
next: the plan's promotion decision
location: docs/kof-cobol-guide.md
owner: 192.168.100.14:0, flatt CLI, no opencode server on this host; port n/a
state: under-development

intent: kof-cobol-guide

---

# The `kof.cobol` translator guide — COBOL source → Kof source

**Scope:** what `libs/cobol` DOES today (every phase has an executed proof —
see `Cobol*E2ETest`) and what it refuses BY NAME (CB001–CB019 + CB010 in the
output). The shared DATA topics (fixed-width, EBCDIC, packed decimal, the
copybook import) live in `docs/kofbol-guide.md` — this guide covers the
TRANSLATOR pipeline.

## 1. The pipeline (what runs, in order)

```
COBOL project → Scanner → Lexer → AST → Semantic → DataDiv → Gen → Project
                                                     ↓
                                            Advanced (the named refusals)
                                                     ↓
                                            Report (the CB diagnostics)
```

- **Scanner** (`scanSource`/`scanProject`): the deterministic discovery
  order (§5), the per-file inventory (PROGRAM-ID, divisions, lines/comments,
  fixed-vs-free by the §6 marks), the dialect by `--dialect` (an
  undetermined dialect is REFUSED CB002 — never guessed).
- **Lexer** (`tokenize`): `Token(kind, text, line)` —
  WORD/NUMBER/STRING/PERIOD/LPAREN/RPAREN/PSEUDO; the §6 rules (the fixed
  col-7 comment, the free `*>`, `>>` directive lines as PSEUDO); CB005/CB006.
- **AST** (`splitSentences`/`parseParagraphs`): the PERIOD-terminated
  sentences; the `NAME.` paragraph heads; CB007.
- **Semantic** (`analyze`): the verb-first inventory with an EXPLICIT
  confidence tag — the KNOWN table maps, an UNKNOWN verb is DATA (counted +
  listed by `unknownVerbsOf` — the migration TODO list).
- **DataDiv** (`parseDataDivision`): the DATA DIVISION region → the
  KofBOL `Layout` (the copybook machinery REUSED, never duplicated) +
  `initialValues` (CB008).
- **ProcDiv** (`procedureOf`/`performs`): the paragraphs + the CALL GRAPH
  (`PERFORM <name>` → `Call(from, to, count)`; a nonexistent target refused
  CB009).
- **Gen** (`genProcedure`): one `name() { }` per paragraph (the Q3
  camelCase), `DISPLAY 'lit'` → `println("lit")`, everything else refused IN
  THE OUTPUT as `// CB010: verb V not mapped yet`.
- **Project** (`genProject`): `<name>/Main.kf` + `<name>/kof.toml` (the
  repo's real toml shape) + `<name>/NOTES.txt` (the CB diagnostics).
- **Advanced** (`advancedScan`): the untranslatable constructs NAMED —
  `GO TO <target>`, `ALTER`, `PERFORM THRU`, `SET ADDRESS` — the migration
  decision informed.
- **Report** (`DiagReport`): the CB codes collected and rendered
  (`path: code: message`).

## 2. The acceptance seed (proven)

The GENERATED source COMPILES and RUNS on the JVM, and its runtime output is
asserted against the COBOL program's defined behavior derived INDEPENDENTLY
(`ALFA\nBETA\nGAMA`) — through the commit AND the canonical form
(`KofFormatter.format`, IDEMPOTENT on the generated text).

## 3. The conformance matrix

| Area | Proof |
|---|---|
| Scanner: discovery order, inventory, format | `CobolScannerE2ETest` (JVM + Script) |
| Lexer: tokens, comments, directives, CB005/006 | `CobolLexerE2ETest` |
| AST: sentences, paragraphs, CB007 | `CobolAstE2ETest` |
| Semantic: verb-first + confidence, unknowns listed | `CobolSemanticE2ETest` |
| DataDiv: the region → the KofBOL Layout + VALUES (CB008) | `CobolDataDivE2ETest` |
| ProcDiv: the paragraphs + the CALL GRAPH (CB009) | `CobolProcDivE2ETest` |
| Gen: the mappable subset → compilable Kof (CB010 in the output) | `CobolGenE2ETest` |
| Formatter: the generated source canonical + idempotent | the Gen test JVM face |
| Project: the layout + kof.toml + NOTES | `CobolProjectE2ETest` |
| Report: the CB codes collected + rendered | `CobolReportE2ETest` |
| Golden: the fixture corpus byte-exact | `CobolGoldenE2ETest` + `tests/golden/cobol-*` |
| Behavior: the translated program's output == the COBOL behavior | `CobolBehaviorE2ETest` |
| Advanced: GO TO/ALTER/PERFORM THRU/SET ADDRESS named + refused | `CobolAdvancedE2ETest` |

## 4. Error codes (CB001–CB019)

| Code | Meaning |
|---|---|
| CB001 | no PROGRAM-ID in a source |
| CB002 | dialect not determined — pass the dialect, never guessed |
| CB003 | unsupported source extension |
| CB004 | mixed source formats in the project |
| CB005 | unterminated string literal |
| CB006 | unexpected character |
| CB007 | sentence without a terminating PERIOD |
| CB008 | VALUE literal neither quoted nor numeric |
| CB009 | PERFORM target not found |
| CB010 | verb not mapped yet (in the generated output, as a comment) |
| CB011–CB019 | reserved for the later phases (the Data/Procedure deep-mapping) |

The shared legacy codes LEG001–LEG019 (the byte surfaces) are in
`docs/kofbol-guide.md` §7.

## 5. Limitations (explicit, never hidden)

- The mappable subset today: `DISPLAY 'lit'`, paragraph structure, the
  DATA-DIVISION shapes the KofBOL mapping covers. Everything else is CB010
  IN THE OUTPUT — the migration TODO list, never a silent mistranslation.
- The dialect is the caller's (no auto-detection yet — CB002).
- No SQL/CICS/IMS/VSAM (the plan's §30 phases 14+ — future by design).
- The CLI home and the exact grammar are the plan's open decisions Q1/Q2
  (the maintainer's) — the library takes values, no CLI surface invented.

## 6. The modernization path (the translator in the §8 flow)

1. `scanProject` the legacy tree — the inventory + the dialect + the notes.
2. `parseDataDivision` each program — the Layout contracts (the KofBOL
   mapping).
3. `analyze` + `advancedScan` — the migration TODO list (the unknown verbs +
   the named advanced constructs).
4. `genProcedure` + `genProject` — the compilable Kof for the mappable
   subset, the named gaps for the rest.
5. Close the CB010s incrementally — each mapped verb is one less legacy
   shape.