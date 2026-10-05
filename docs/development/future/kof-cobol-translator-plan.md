last: none
doing: none-planned
next: spike-0-inventory
location: docs/development/future
state: planned

intent: kof-cobol-translator-source-to-source

> **EN canonical** · PT: [`kof-cobol-translator-plan.pt_BR.md`](kof-cobol-translator-plan.pt_BR.md)

**Rule 6 gate:** `kof-cobol-translator` is a **source-to-source translator** that
generates real Kof source. It introduces no Kof syntax and no frozen-semantics
change in v1, but it is a new official tooling surface and its output policy
(naming, `Money` representation, unsupported-construct handling) is
architecture. This document is **plan only, zero code**. Promotion requires a
maintainer decision (`D-COBOL-TRANSLATOR-GO`) plus the future-promotion flow
(`docs/development/future/README.md` §"When to move", `AGENTS.md` §"Future
promotion"). Promotion is currently FROZEN by `D-FUTURE-FREEZE`; this plan is
authored under that freeze, not promoted.

**Source:** maintainer request (02/10/2026).

**Related, already-decided work this plan MUST reuse (not duplicate):**
- `docs/development/future/kofbol-plan.md` — **KofBOL**, the legacy-banking
  *interop* bridge. Its §5 **COBOL type mapping** (PIC/COMP/COMP-3/DISPLAY/
  EBCDIC/fixed-width/OCCURS/REDEFINES/88-level) and §6 (fixed-width, packed
  decimal, EBCDIC code pages) are the **semantic source of truth for COBOL data
  representation**; this translator must call into that model, not re-invent it.
  KofBOL is the bridge; this translator is the *migration* path. Open decision
  Q9 of KofBOL (is a COBOL→Kof *data contract* path KofBOL or interop?) is
  **answered here**: the data contract is shared; the translator consumes it.
- `docs/development/future/LEGACY_MIGRATION.md` §4 — the **Legacy Semantic IR**
  + the 5-level confidence model (`Confidence.java`) + source mapping. COBOL is
  already named there as a *possible frontend* (`:185,294,357`). This plan
  instantiates that IR for COBOL.
- `docs/development/future/TRANSLATOR.md` — the existing Java→Kof translator
  architecture (`Java Source → … → Kof AST → Kof Source`) and its honest-gap
  discipline (R6: never emit broken Kof; emit a diagnostic).
- `kof-cli/.../Translate.java` + `TranslateLexer/Expr/Statements/Types/New/Switch/Statics`
  — the **live `kof translate` command** (`Main.java:29`, `TranslateTest` 61/61).
  The COBOL front-end plugs into this command, sharing the `TranslateException`
  / honest-gap contract; it does **not** replace the Java front.
- `KofFormatter.format(src, fileName)` (`kof-compiler`) — the canonical
  parser+printer; generated Kof **must** round-trip through it (§19, §68).
- `docs/development/future/kof-financial-plan.md` — the **greenfield** money
  model (`Money` = `Long` minor units + ISO-4217); the translator's
  financial-decimal output targets this, never `Float`/`Double`.
- `docs/stdlib/kof-connector-ecosystem-plan.md` phase 7 +
  `docs/development/future/kofbol-plan.md` — `kof-cobol-connector` for the
  hybrid/gradual path.
- `KofProjectConfig` `[sources]` (`D-CLI-SOURCE-ROOTS`, #708) — the declared
  source-root model the generated project uses.
- `CmdNew.java` (`kof new`) — the canonical project scaffold (`kof.toml` +
  `src/Main.kf`) the output project must match.

**Not to duplicate:** the COBOL data mapping (KofBOL §5), the IR (LEGACY_MIGRATION
§4), the Java front (`Translate.java`), the formatter (`KofFormatter`), the money
model (`kof-financial`). This plan owns the **COBOL front-end + project pipeline
+ generation policy** that sits between them.

---

# `kof-cobol-translator` — plan (COBOL source → Kof source)

## 0. Purpose

Give the Kof ecosystem an **official, deterministic COBOL→Kof source
translator**: point it at a legacy project and get an organized, compilable,
semantically-equivalent Kof project in a **separate output directory**, leaving
the COBOL untouched.

Final acceptance question (§30):

> Given a real COBOL project, can `kof translate project` produce a Kof project
> that compiles, whose behavior is provably equivalent on a fixture corpus, with
> every un-translatable construct named in a diagnostic — never silently
> mistranslated?

## 1. The fundamental rule (anti-transliteration)

The translator **understands** COBOL; it does not replace keywords.

```text
COBOL
  ↓ lexing
COBOL tokens
  ↓ parsing
COBOL AST
  ↓ semantic analysis
COBOL Semantic IR
  ↓ normalization
Kof semantic model
  ↓ Kof AST (or canonical Kof source via KofFormatter)
organized Kof project
```

**Never:**

```text
COBOL
  ↓ replace keywords
Kof-looking text
```

The same discipline the Java translator already enforces (TRANSLATOR.md §2)
applies to COBOL. A construct that cannot be translated with preserved
semantics becomes an **explicit diagnostic** (§21), never code that merely
looks valid (R6).

## 2. Motivation

- COBOL is the dominant language of banking/insurance/government core systems.
  It is a named case in the connector plan (phase 7) and the legacy-migration
  cluster.
- KofBOL (the bridge) lets a legacy COBOL system keep running while Kof grows
  in front of it. This translator is the **complementary migration path**: move
  a *program* from COBOL to Kof source, one program at a time, validated by
  differential tests.
- The platform already has the pieces: a live `kof translate` command, the
  Legacy Semantic IR + confidence model, the KofBOL data mapping, and the
  canonical formatter. The missing piece is the COBOL front-end and the project
  pipeline.

## 3. Architecture

### 3.1 Pipeline

```text
project directory
  ↓ scan + discover + dialect detect        (§5, §6)
project inventory + dependency graph        (§9, §10)
  ↓ resolve copybooks (COPY / REPLACING)     (§17)
  ↓ lex                                       (§7)
COBOL tokens
  ↓ parse (source-format aware)               (§8)
COBOL AST                                     (§9)
  ↓ semantic analysis (data + procedure)      (§11, §12)
COBOL Semantic IR  (Legacy Semantic IR, LEGACY_MIGRATION §4)
  ↓ normalization                             (§50)
Kof semantic model
  ↓ generation (Kof AST / canonical source)   (§19)
  ↓ KofFormatter.format                       (§68)
  ↓ validate (kof check / compile)            (§23, §62)
organized Kof project + manifest + report     (§20, §24, §90)
```

### 3.2 Component boundaries

Mirror the existing translator split (`Translate.java` ≤500 + helper classes)
so each unit is one cohesive scope:

| Component | Responsibility | Reuse / precedent |
|---|---|---|
| `CobolScanner` | file discovery, extensions, excludes, dialect detect | new; `Deps.java` discovery patterns |
| `CobolLexer` | source-format aware tokenization | new; `TranslateLexer` shape |
| `CobolParser` | AST for divisions/sections/paragraphs/sentences/verbs | new; `TranslateStatements` shape |
| `CobolAst` | COBOL AST nodes | new |
| `CobolSemantics` | data model, PIC, levels, conditions | new; **uses KofBOL §5 mapping** |
| `CobolIr` | Legacy Semantic IR | `LEGACY_MIGRATION.md` §4 + `Confidence.java` |
| `CobolNormalize` | control-flow/data normalization | new |
| `KofEmitter` | Kof AST / canonical source | `KofFormatter` + `TranslateStatements` idioms |
| `CobolDiagnostics` | codes/severity/location/suggestion | new; follows `known-bugs`/gap-code style |
| `CobolProject` | inventory, graph, output layout, manifest | new |
| `TranslateProject` | CLI orchestration (`kof translate project`) | extends `Translate.run` |

## 4. CLI

The `kof translate` command exists (`Main.java:29`) and today takes
`kof translate <file.java> [--output <file.kf>]`. The COBOL front **extends**
it without breaking the Java path:

```text
kof translate project [dir] [--output <dir>] [--dialect <name>]
                       [--strict] [--dry-run] [--verbose]
                       [--include <glob>] [--exclude <glob>]
kof translate file <file.cbl> [--output <file.kof>] [--dialect <name>]
kof translate <file.java> [--output <file.kf>]     # unchanged (Java front)
```

- The first positional token is the **mode** (`project` | `file`) or a Java
  file path. Backward compatibility with `kof translate Foo.java` is preserved.
- `project` with no `[dir]` uses the **current directory**.
- The final syntax must be compile-validated against the real CLI before
  implementation (the repo rule: investigate, then specify, then implement).

## 5. Project scanner

- **Default extensions:** `.cbl`, `.cob`, `.cobol` (programs); `.cpy`, `.copy`
  (copybooks). Configurable.
- **Exclusions (default):** the output directory; `.git/`, `node_modules/`,
  `target/`, `build/`, hidden directories (`.*`).
- **Hidden files:** skipped unless `--include` names them.
- **Symlinks:** not followed by default (cycle safety); a flag opts in.
- **Depth:** unbounded by default, bounded by a flag; output-dir detection
  prevents recursion (§7).
- **Encoding:** each source's encoding is detected/declared (`--encoding`,
  default ASCII/EBCDIC per dialect); never assumed UTF-8 (§35).
- **Determinism:** discovery order is a **stable sort by normalized relative
  path** (§67), never filesystem order.

## 6. Dialects

- A `CobolDialect` profile controls: source format (fixed/free), reserved
  words, extensions, intrinsics, compiler directives, and data
  representations.
- Initial dialects to investigate: **IBM Enterprise COBOL**, **GnuCOBOL**,
  **Micro Focus**. Detection is a **best-effort heuristic** and is always
  reported; when detection is ambiguous, the translator asks for `--dialect`
  rather than guessing (never silent).
- **Compiler directives** (`>>SOURCE FORMAT`, `>>IF`, `>>DEFINE`,
  `COPY REPLACING`) are handled **before/during** lexing per dialect (§73).

## 7. Lexer

- **Source format:** fixed-format (columns 1–6 sequence, 7 indicator, 8–72
  area A/B, 73–80 identification) and free-format, per dialect.
- **Comments:** `*`/`/` in column 7 (fixed), `*>` (free); preserved and
  associated with the nearest construct (§83).
- **Continuation** lines, string/figurative-constant literals, level numbers,
  PICTURE strings, and `EXEC` blocks are tokenized with dedicated rules.
- **Latent-bug discipline** (from `TRANSLATOR.md`): the lexer **rejects** an
  unexpected character with a diagnostic; it never silently drops it.

## 8. Parser

- Hand-written recursive-descent (same shape as `TranslateStatements`), one
  class per concern, each ≤500 lines (`check_500`).
- Divisions/sections/paragraphs/sentences/statements/expressions/conditions.
- Scope terminators (`END-IF`, `END-EVALUATE`, `END-PERFORM`, `END-READ`, …)
  and the period-sentence boundary are both handled.
- The parser produces the **COBOL AST** (§9); it does **not** emit Kof.

## 9. COBOL AST

Nodes for: `Program`, `IdentificationDivision`, `EnvironmentDivision`,
`DataDivision`, `ProcedureDivision`, `Section`, `Paragraph`, `Sentence`,
`Statement`, `Expression`, `Condition`, `DataItem` (with level, PIC, USAGE,
VALUE, OCCURS, REDEFINES, INDEXED BY), `FileDescription` (`FD`/`SD`),
`CopyStatement`, `ExecBlock` (SQL/CICS), `CallStatement`, `ConditionName`
(88-level).

Every node carries a **source span** (file + line/column) for diagnostics and
source maps (§22).

## 10. Semantic IR

Reuse the **Legacy Semantic IR** (`LEGACY_MIGRATION.md` §4): types, functions,
fields, calls, control flow, exceptions, memory/external operations, constants,
data flow, metadata, and **unknown** operations. Unknown stays
`UnknownType`/`UnknownCall`/`UnknownBehavior` — never fabricated.

Every IR element carries a **confidence level** (`Confidence.java`):
`Recovered exactly` / `with metadata` / `Inferred` / `Heuristic` / `Unknown`.
The generator's output policy depends on confidence (§58).

The IR is origin-independent: the same IR could later drive
COBOL→documentation or COBOL→modernization-report (§49).

## 11. Data Division

Parse `IDENTIFICATION` / `ENVIRONMENT` / `DATA` / `PROCEDURE` divisions, with
special attention to `DATA DIVISION`:

- `FILE SECTION` (`FD`/`SD`), `WORKING-STORAGE SECTION`, `LOCAL-STORAGE`,
  `LINKAGE SECTION` (§40).
- **Level numbers:** preserve hierarchy; never flatten. `01 → 05 → 10` becomes
  a nested Kof `record` (or nested record types), not a flat bag of fields.
- **PIC** analysis (§14): `X`, `9`, `S9`, `9(n)`, `V`, `S9(7)V99`, editing
  chars; sign and scale explicit.
- **USAGE**: `DISPLAY`, `COMP`, `COMP-1/2/3/4/5`, `BINARY`,
  `PACKED-DECIMAL`, `INDEX`, `POINTER` (§16).
- **VALUE**, **OCCURS** (`TIMES`, `DEPENDING ON`, `INDEXED BY`), **REDEFINES**,
  **88-level condition names** (§18, §19, §17).

## 12. Procedure Division

- AST for paragraphs, sections, sentences, statements, expressions,
  conditions.
- **Organize by intention**, not by COBOL textual structure: a paragraph that
  is a pure function becomes a Kof function; a section that is a program flow
  becomes structured control flow; a data-transformation paragraph becomes an
  expression/pipeline where semantics allow (§20, §51).
- The IR preserves control flow **before** any normalization (§29).

## 13. File handling

- Model `FILE SECTION` (`FD`/`SD`) and the verbs `OPEN`, `READ`, `WRITE`,
  `REWRITE`, `DELETE`, `START`, `CLOSE` (§30).
- Map to a Kof file abstraction that preserves **access mode** (sequential,
  indexed, relative, line-sequential), **record format** (§33), **fixed-width**
  layout (§34), and **FILE STATUS** semantics (§32).
- **Never** replace `READ` with `file.read()` without preserving the status and
  the record layout. The file abstraction is the target; the mapping is a
  decision (§20 open decisions).

## 14. SQL (embedded)

- Parse `EXEC SQL … END-EXEC` (§42) and **preserve** it as structured Kof DB
  integration (`kof.db`), never delete it.
- Cursors (`DECLARE`/`OPEN`/`FETCH`/`CLOSE`, §43) map to the Kof DB API.
- Where the SQL cannot be represented safely, emit an explicit diagnostic and
  keep the SQL as a documented boundary, never a silent drop.

## 15. CICS

- Parse `EXEC CICS … END-EXEC` (§44) and **separate business logic from CICS
  integration**. The business logic translates to Kof; the CICS runtime becomes
  an **adapter** (via `kof-cobol-connector` / KofBOL transport). CICS calls that
  cannot be mapped get a named diagnostic.

## 16. Calls

- `CALL` static/dynamic, `BY REFERENCE`/`BY CONTENT`/`BY VALUE`, `RETURNING`
  (§39, §40, §41).
- Map to Kof functions/records; **parameter passing must respect Kof's
  ownership/borrowing model** — no unsafe mutable aliasing introduced by
  translation. `LINKAGE SECTION` becomes the program's Kof API.
- When the called program is in the project, translate both and preserve the
  Kof→Kof relation (§46).

## 17. Copybooks

- `COPY X.` and `COPY X REPLACING …` are **resolved before semantic analysis**
  (§11), not treated as plain text.
- A copybook becomes a shared Kof `record`/module (deduplicated), preserving
  `REPLACING` semantics.
- KofBOL's copybook import (`kofbol-plan.md` §4.3) is the data-model precedent;
  the translator's copybook resolver is the source-level counterpart.

## 18. JCL boundary

- The translator does **not** translate JCL in v1 (§45). It **identifies**
  `job`/`step`/`program`/`dataset`/`parameter` so a future integrated migration
  can connect a Kof `workflow`/batch abstraction. The boundary is documented,
  not silently ignored.

## 19. Kof generation

- Generation targets **idiomatic Kof**, validated by compilation.
- **Prefer the canonical formatter**: emit Kof source and run it through
  `KofFormatter.format(src, fileName)`; do not hand-roll formatting when the
  tooling exists (§68). (The Java front predates this and emits strings; the
  COBOL front should use the formatter.)
- **Naming** (§52, §53): COBOL `CUSTOMER-ID` → Kof `customerId`, **but** keep an
  explicit `original-name → kof-name` mapping in metadata, and preserve names
  where external references require it. The mapping is stored, never implicit.
- **Comments** (§83): preserve business explanations, associate them with the
  construct they document, and keep legacy-implementation notes separate.
- **Source metadata** (§84): a lightweight header (`source:`, `translator:`,
  `translation-version:`) without polluting the code body.
- **No `PERFORM_X()` / `PARA_123()` API artifacts** (§51).

## 20. Project organization

- Preserve the input structure when possible (§8 of the request): `src/`,
  `copybooks/`, `programs/`, `batch/` map to equivalent Kof directories.
- Split by **semantic responsibility** (program / module / record / library /
  adapter), never fragment each paragraph into a file (§54).
- Output is a real Kof project: `kof.toml` + `[sources]` (`D-CLI-SOURCE-ROOTS`)
  matching `kof new` (§19).

## 21. Diagnostics

Every diagnostic: `code`, `severity`, `source location`, `message`,
`suggestion`. Severities: `info`, `warning`, `error`, `unsupported`,
`manual-migration`.

- Codes namespaced `COBOL-P…` (parse), `COBOL-S…` (semantic),
  `COBOL-U…` (unsupported / manual-migration), `COBOL-T…` (project/IO).
- **Never hide a problem** (§58): if semantics cannot be preserved, emit a
  diagnostic + location + reason + recommended action; prefer "could not
  preserve this semantics" over "generated some Kof".

## 22. Source maps

- Emit `COBOL file:line → Kof file:line` mappings (§55) for debugging, review,
  audit, gradual migration, and comparison. Stored in the manifest (§85).

## 23. Validation

- After generation: `kof check` / compile the generated project (§62); a
  syntax error in output is a **translator bug**, not an acceptable result.
- Where possible, **semantic validation** compares COBOL behavior with Kof
  behavior on fixtures (§63): golden files + behavioral equivalence tests.
- Compilation success is a **metric** (§95), not the only goal.

## 24. Testing

- Corpus layout (§64): `translator-tests/input/`, `expected/`, `metadata/`,
  with COBOL input, expected Kof, and expected diagnostics per case.
- **Banking corpus** (§65): accounts, balance, transfer, interest, collection,
  batch, files, reconciliation, transaction processing, financial dates,
  decimals — common COBOL constructs in that domain, not a bank implementation.
- Golden files must be **byte-stable** and regenerated only by an explicit
  command (never hand-edited to hide a regression).

## 25. Performance

- Investigate incremental parsing, caching, parallel processing, memory usage,
  dependency-graph reuse, and deterministic output (§66).
- Large projects: the project graph is built once; per-file translation is
  independent where the graph allows.

## 26. Security

- The translator reads source and writes to an **output directory only**; it
  never mutates input (§3).
- Safe overwrite (§87): never overwrite manually-edited Kof without detection,
  comparison, and an explicit flag.
- No secrets are embedded in output; generated metadata contains no credentials.
- Unsupported unsafe COBOL semantics (pointers, `SET ADDRESS OF`) get a
  diagnostic, never unsafe Kof (§78, §79).

## 27. Compatibility

- v1 introduces **no Kof syntax** and **no core primitive** (library-first,
  `D-KOF-FIRST`). The generated project compiles on the existing targets.
- `kof translate <file.java>` behavior is unchanged.
- The translator itself is tooling; it does not alter frozen surfaces.

## 28. Migration strategy

- **Partial translation** (§59): `Program A → complete`, `B → partial`,
  `C → blocked`; the whole project does not abort by default. Failure policy is
  configurable.
- **Strict mode** (§60): `--strict` fails if any construct lacks a guaranteed
  translation (CI use).
- **Best-effort mode** (default, §61): generate the maximum possible, every
  limitation explicitly recorded; warnings never masked.
- **Hybrid migration** (§92, §93): translated Kof modules can coexist with
  remaining COBOL components via `kof-cobol-connector` / KofBOL.

## 29. Limitations

- **No universal conversion promise** (§94): not 100% of all COBOL dialects and
  extensions translate automatically. Limits are explicit and measured (§95).
- No JCL translation in v1 (§18).
- No decimal core primitive in v1: financial values are scaled-integer/`Money`
  (`kof-financial`), never `Float`/`Double` (§15).
- `ALTER`, `GO TO`-heavy spaghetti, and unsafe pointer semantics may be
  untranslatable; they become `manual-migration` diagnostics.
- No LLM dependency (§69): base translation is deterministic.

## 30. Roadmap (phases; each phase one lane, RED-first, nothing starts from this doc alone)

- **Phase 1 — Infrastructure:** project scanner, file discovery, dialect
  detection, source inventory.
- **Phase 2 — Lexer/parser.**
- **Phase 3 — COBOL AST.**
- **Phase 4 — Semantic model** (Legacy Semantic IR + confidence).
- **Phase 5 — Data Division** (levels, PIC, USAGE, VALUE).
- **Phase 6 — Procedure Division** (paragraphs, verbs, conditions).
- **Phase 7 — Kof AST/code generation** (via `KofFormatter`).
- **Phase 8 — Formatter integration.**
- **Phase 9 — Project generation** (layout, `kof.toml`, manifest).
- **Phase 10 — Diagnostics** (codes, report).
- **Phase 11 — Golden tests.**
- **Phase 12 — Behavioral equivalence.**
- **Phase 13 — Advanced COBOL** (`ALTER`, `GO TO`, `PERFORM THRU`, pointers).
- **Phase 14 — SQL/CICS integration.**
- **Phase 15 — Large-project optimization.**

Each phase ships tests in the same commit (`AGENTS.md` Q0–Q7).

## 31. Open decisions (rule 6 — maintainer)

1. **Name and home** — `kof-cobol-translator` as a tool inside `kof-cli` (like
   the Java front) vs a separate module/artifact.
2. **CLI shape** — `kof translate project|file` as designed here, or a distinct
   top-level command; the exact grammar must be compile-validated.
3. **Output naming policy** — `CUSTOMER-ID → customerId` with a stored mapping
   (recommended) vs preserving COBOL names verbatim.
4. **Money/decimal output** — target `kof.financial`'s `Money` (recommended,
   pending `D-FINANCIAL-GO`) vs a local scaled-integer type vs waiting for a
   core decimal primitive (rule-6).
5. **Copybook resolution depth** — full `REPLACING` semantics in v1 vs a subset
   with diagnostics.
6. **Relationship to KofBOL** — share the KofBOL data mapping as the single
   source of truth (recommended) vs an independent translator mapping.
7. **v1 dialect scope** — which dialect(s) ship first (IBM/GnuCOBOL/Micro
   Focus).
8. **Generated project form** — canonical source via `KofFormatter`
   (recommended) vs Kof AST construction.
9. **Partial-output policy default** — best-effort (recommended) vs strict.
10. **File abstraction target** — the Kof file API shape for sequential/
    indexed/relative access.

## 32. Decision records

Architectural decisions are recorded as `D-COBOL-001`, `D-COBOL-002`, … each
with **Context / Problem / Options / Decision / Consequences**, once promotion
starts. v1 records to seed: pipeline/IR choice, formatter reuse, naming policy,
decimal representation, copybook resolution, diagnostics taxonomy.

## 33. Construct matrix

Expanded during the research; every row is filled with the real target before
its phase closes. `Direct` = direct Kof construct; `Adapter` = needs a Kof
library/adapter; `Unsupported` = diagnostic.

| COBOL Construct | Parser | Semantic IR | Kof Equivalent | Direct | Adapter | Unsupported | Tests |
|---|---|---|---|---|---|---|---|
| MOVE | | | assignment + conversion | | ✓ | | |
| COMPUTE | | | arithmetic expression | ✓ | | | |
| ADD/SUBTRACT/MULTIPLY/DIVIDE | | | arithmetic + ROUNDED/ON SIZE ERROR | | ✓ | | |
| IF | | | `if` / if-expression | ✓ | | | |
| EVALUATE | | | `switch` / `match` | ✓ | | | |
| PERFORM (inline/TIMES/UNTIL/VARYING) | | | loop / function call | | ✓ | | |
| PERFORM THRU | | | control-flow normalization | | | ✓ (diagnostic) | |
| GO TO | | | state machine / labels | | | ✓ (when unsafe) | |
| ALTER | | | (analyze) | | | ✓ | |
| READ/WRITE/REWRITE/DELETE/START/OPEN/CLOSE | | | Kof file API | | ✓ | | |
| FILE STATUS | | | structured status value | | ✓ | | |
| CALL (REFERENCE/CONTENT/VALUE) | | | Kof function/record | | ✓ | | |
| COPY / REPLACING | | | shared record/module | ✓ | | | |
| REDEFINES | | | overlay/union view | | ✓ | | |
| OCCURS / DEPENDING ON / INDEXED BY | | | `List`/array + bounds | | ✓ | | |
| 88-level condition | | | typed predicate/enum | | ✓ | | |
| PIC S9(n)V99 COMP-3 | | | `Money`/scaled decimal | | ✓ | | |
| COMP / BINARY | | | integer | ✓ | | | |
| DISPLAY / EBCDIC | | | explicit code page | | ✓ | | |
| EXEC SQL | | | `kof.db` | | ✓ | | |
| EXEC CICS | | | business logic + adapter | | ✓ | | |
| STRING / UNSTRING / INSPECT | | | string API | | ✓ | | |
| SEARCH / SEARCH ALL | | | `List` search/sort | | ✓ | | |
| SORT / MERGE | | | batch/sort library | | ✓ | | |
| POINTER / SET ADDRESS OF | | | (unsafe) | | | ✓ | |
| Intrinsic FUNCTION | | | `kof.math`/`kof.time`/… | | ✓ | | |

## 34. Final review question

> **Does `kof translate project` turn a real COBOL project into a real Kof
> project — compiled, typed, organized, and behaviorally validated — with every
> gap named, and the COBOL sources untouched?**

If yes, the translator fulfills its purpose. If any answer is "it generated
Kof-looking text", it has failed the fundamental rule (§1).
