last: none
doing: none-planned
next: spike-0-inventory
location: docs/development/future
state: planned

intent: kof-expr-native-text-pattern-dsl

**Rule 6 gate:** `Kof.expr` is a NEW syntax + NEW semantics (a frozen-semantics
surface). This document is **plan only, zero code**. Promotion requires a
maintainer decision (`D-KOF-EXPR` or an extension of `D-STR-UNICODE`) and the
future-promotion flow (`docs/development/future/README.md` §"When to move",
`AGENTS.md` §"Future promotion"): rewrite with `UNDER DEVELOPMENT`, real state +
how-to-finish, queue in `roadmap.md` §23, point `docs/status.md` at it, claim in
`DOING.md`.

**Source:** maintainer request (30/09/2026). Related decisions:
`DECISIONS.md` §D-STR-UNICODE (27/09/2026) — the regex engine is deferred to 1.0
and is a heavy domain that belongs to an official package, not an ad-hoc runtime
splice; `STR003` gates the three regex members today. `Kof.expr` is a candidate
answer to that deferral, but it is **not** a regex engine with pretty names.

---

# Kof.expr — plan (native, declarative text-pattern DSL)

## 0. The one-sentence purpose

> `Kof.expr` exists so a programmer can describe a **text pattern's intention**
> without having to think like a regex machine.

The acceptance question for every design decision is:

> "Would someone who has never seen regex be able to write a matching expression
> after reading the Kof.expr documentation?"

If the answer is no, the API is wrong.

Corollary (the anti-goal): **`Kof.expr` is not regex with nicer names.** The
public abstraction is *semantic* (`letter`, `one_or_more`, `capture`,
`between`) and the regex engine — if any — is an internal lowering detail the
user must never see.

## 1. Measured baseline (why this exists)

The investigation (file-anchored) establishes the starting point:

- **There is no first-class pattern DSL.** The only regex touchpoints on the Kof
  surface are the three `String` members `matches` / `replaceAll` /
  `replaceFirst`, gated by `STR003` (`StringTargetGaps.java:31-43`), and
  `validation.matches` (`KofValidation.java:50`).
- **The regex touchpoints are already divergent across targets.** `String.split`
  and `String.replace` use real Java regex on the JVM
  (`jvm/JvmOpEmitter.java:190-222`) and Script
  (`KofInterpreterCollections.java:74-80`), but are literal/byte operations on
  JS (`js/JsRuntimeCore.java:257-263`) and Native
  (`runtime/RuntimeStringEdit.java:188-219`; `nat/NativeX86StringCalls.java:252`).
  `validation.matches` is real regex on JVM/JS and a literal substring search on
  Native (`runtime/RuntimeValidation.java:214-256`;
  `nat/NativeRiscvAsmRtB3.java:174-207`). The divergence is not pinned in
  `conformance-matrix.md`.
- **`DECISIONS.md` §D-STR-UNICODE (27/09/2026)** defers the regex engine to 1.0
  and names it a heavy domain for an official package.
- **A new declaration kind is a well-trodden path**: lexer keyword
  (`parser/Lexer.java:13-85`), top-level dispatch (`parser/Parser.java:41-86`),
  symbol table (`SymbolTableBuilder.java:15-125`), semantic dispatch
  (`SemanticAnalyzer.java:241-253`), IR lowering
  (`CompilerPipeline.lowerToIR:215-275`), with `CompilerEnumLowering.java` as
  the canonical model of a built-in declaration lowered to generic IR ops that
  all four targets already support.
- **Pipeline parity is by construction**: the same parser → semantic → lower →
  optimize feeds JVM/Native/JS emit and the Script interpreter
  (`CompilerPipeline.analyzeAndLower:318-363`). A construct that lowers to
  existing `Kof*` ops reaches all targets — including the interpreter — for
  free.

The plan below is designed so that **v1 touches no per-target backend**: it is a
compiler-front construct that lowers to the existing String runtime plus a
small, uniform matching runtime expressed with existing ops.

## 2. Non-goals (v1)

- No replacement of `String.matches`/`replaceAll`/`replaceFirst` semantics
  (that is a separate `D-STR-UNICODE` decision; `STR003` stays).
- No PCRE-style feature race (lookbehind, backreferences, atomic groups,
  conditionals). Include only what expresses intent.
- No second parser for a "mini-language inside a string". `Kof.expr` is real
  Kof syntax.
- No new backend or target-specific code path in v1.
- No reflection, no macro system, no annotation processing.
- No dependency on a third-party regex library for the surface. An engine, if
  used, is an internal detail (see §9).

## 3. Proposed surface (intent, not final grammar)

The surface must read like Kof and compose like Kof. Two candidate shapes:

**Shape A — declaration block** (richest; extends the `enum`/`record`/`class`
family):

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

**Shape B — value expression** (composes into functions, records, constants —
no new top-level kind):

```kof
val email = one_or_more { letter digit "." "_" "%" "+" "-" }
    + "@"
    + one_or_more { letter digit "." "-" }
    + "."
    + between(2, 63) letter
```

The plan recommends **A as the declaration and B as the composable value**,
sharing one expression tree: `expr name { … }` is sugar for binding a named
pattern value, exactly as `enum` binds a named type. `expr` bodies are built
from primitive *semantic atoms* and *combinators*; a named `expr` may be
referenced by name inside another `expr` (composition, §5).

### 3.1 Semantic atoms (v1)

| Atom | Meaning (must be documented per target, §11) |
|---|---|
| `start`, `end` | anchor at the beginning / end of the subject |
| `literal("…")` or a bare `"…"` | exact literal run |
| `any` | exactly one character (Unicode scalar) |
| `letter`, `digit`, `whitespace` | Unicode-aware classes |
| `word` | the documented word class (letter/digit/`_` — see §11) |
| `hex` | `[0-9A-Fa-f]` (documented, ASCII by definition) |
| `one_of("a", "b", …)` | membership (Kof idiom `setOf(...).contains`) |
| `none_of("a", "b", …)` | complement of membership |

### 3.2 Quantifiers (v1)

`optional { … }`, `zero_or_more { … }`, `one_or_more { … }`,
`exactly(n) { … }`, `between(min, max) { … }` (inclusive; `max` may be open —
open question §16 Q3).

Quantifiers apply to the *following unit* — a single atom or a block. This is
the only place `Kof.expr` borrows regex's notion of "the next element", and it
is rendered explicitly with braces so no one has to remember regex precedence.

### 3.3 Grouping, alternation, capture (v1)

- Grouping is a block `{ … }` (or `(...)` if the grammar prefers; open question
  §16 Q1) — no `(?:…)` obfuscation.
- Alternation is explicit: `either { … or … }` (or `one_of` for single
  characters). No `|` by default.
- Capture is named and explicit: `capture("name") { … }`. An unnamed capture is
  not needed in v1.
- Repetition of a group uses the same quantifiers as atoms.

### 3.4 The mandatory examples (target surface)

These are the documentation's required examples, written in the proposed
surface. They are **not yet runnable** (this is a plan); when the plan is
promoted they become `E2E` corpus and must run byte-identically on all targets.

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

Note `octet` demonstrates **composition by name**: `ipv4` reuses an `expr`
defined earlier. (`octet` as written matches 1–3 digits, not a true 0–255
octet; a correct octet is an example of *composition + explicit alternatives*
and is left to the promotion slice, §16 Q6.)

## 4. Where `Kof.expr` enters the architecture

Following the `enum` model exactly:

1. **Lexer** (`parser/Lexer.java:13-85`): add `expr` as a contextual keyword
   (like `sealed` — `parser/TypeDeclarations.java:56-61`,
   `ParseContext.sealedModifierAhead()`), so existing code that uses `expr` as
   an identifier does not break (additive compatibility).
2. **Top-level dispatch** (`parser/Parser.java:41-86`,
   `parser/TypeDeclarations.java:35-45`): recognize `expr NAME { … }`.
3. **AST**: one new node file (one record per file, the repo convention), e.g.
   `ExprDeclarationNode.java`, plus the pattern-node hierarchy
   (atom/quantifier/group/alternation/capture). Nodes carry `SourcePosition`
   for diagnostics.
4. **Symbol table** (`SymbolTableBuilder.java:15-125`): register an
   `ExprSymbol` (name + a normalized pattern descriptor). A referenced `expr`
   inside another `expr` resolves to an `ExprSymbol` — the same mechanism as a
   type reference.
5. **Semantic analysis** (`SemanticAnalyzer.java:241-253` + a satellite
   `SemExprAnalyzer.java`): validate domain (a quantifier count is an `Int`
   ≥ 0; `between` needs `min ≤ max`; duplicate capture names; unknown atom;
   unknown `expr` reference; `start`/`end` placement). Errors use a new code
   family (§12).
6. **Lowering** (`CompilerPipeline.lowerToIR:215-275` + a new
   `CompilerExprLowering.java`): lower the pattern to **existing generic IR
   ops** — no new backend op if it can be avoided. The strategy (§9) is
   portable matcher construction, so the emitted IR is a `KofCall` to a
   `kof_expr_*` runtime the target already knows how to emit, or (preferred v1)
   a pure-Kof library (§8).
7. **Model/method surface**: `match` / `matches` / `find` / `replace` /
   `captures` are methods on the pattern value, implemented with the same
   `String` + collection runtime already present for every target.

## 5. Composition, modules, and namespacing

- A named `expr` is referenceable by name wherever a pattern value is expected.
- `expr` declarations obey the existing visibility/import rules (a `public`
  `expr` in package `p` is imported like any symbol). No new module system.
- Patterns compose with constants/`val` and functions because they are ordinary
  values; `phone` in §3.4 can be a `val` (Shape B) if the maintainer prefers no
  new top-level kind.
- Cycles (`a` references `b` references `a`) are a semantic error (§12,
  `KOF-E100x` recursive-pattern family), not infinite expansion.

## 6. Captures and result typing

The result of a match must not be a convenience `Map<String, Any>`. Two options,
to be decided at promotion (§16 Q4):

- **Option T (typed per pattern, preferred):** each `expr` with captures
  induces a small value type (a `record`) whose fields are the captures
  (`date.day`, `date.month`, `date.year`). The compiler already has record
  lowering and nominal typing (`CompilerIfaceRecordLowering.java`,
  `ClassShapeChecks`, `DeclaredTypeChecker`). This is the most Kof-coherent and
  safest option; its cost is a generated type per capturing `expr`.
- **Option U (uniform result type):** a single `Match` value exposing
  `captured("day"): String?` and `capturedInt("day"): Int?`; honest, but weakly
  typed. Recommended only as a fallback if generated types prove too costly.

Operation set on a pattern value (kept explicitly distinct — no ambiguous
single entry point):

```kof
email.matches(text)     // Bool — whole-subject match (anchored)
email.match(text)       // Match? / record? — whole-subject + captures
email.find(text)        // Int index or Match? — first occurrence, unanchored
email.replace(text, replacement)   // String — literal or capture-aware
email.findAll(text)     // List<Match> — all occurrences
```

`matches` = full match; `find` = search; `replace` = transform; `captures` =
extraction. Distinct names for distinct intentions.

## 7. Transformation

- `replace(text, replacement)`: literal replacement in v1.
- Capture-aware replacement (a function from the capture record to `String`, or
  a small template with explicit placeholders such as
  `replace(text) { m -> m.day + "/" + m.month }`) is an evaluated extension
  (§16 Q5). Include only if it stays intention-revealing; **do not** grow a
  `$1`/`\1` mini-language.

## 8. Two candidate architectures (to be decided at promotion)

**Strategy P — pure-Kof library backed by a matcher runtime (recommended v1).**
Build the pattern as a Kof value (a small record tree) and match with a
pure-Kof interpreter over `String` primitives that already exist on all targets
(`kof_string_length`, `charAt`/`toCharArray`, `substring`, comparisons). No
regex engine, no new backend op, full parity by construction (including the
Script interpreter). Cost: throughput lower than a compiled engine for very
large inputs; acceptable for v1 and eliminable later (Strategy C). This is
`D-KOF-FIRST`: Kof expresses it with its own primitives.

**Strategy C — compile to a specialized matcher (later).**
When performance requires it, lower a pattern to specialized IR/assembly per
target (or to the target's native regex: `java.util.regex` on JVM, `RegExp` on
JS, a small NFA runtime on Native) **behind the same public API**. The user
never chooses. This is where §9 (performance) and §10 (ReDoS) must be
engineered; it can be introduced slice-by-slice without changing the surface.

The plan recommends **P first**, with the internal representation chosen so that
C is a drop-in replacement (the pattern descriptor is serializable/compilable).

## 9. Per-target strategy and parity

Because v1 lowers to existing String/collection ops, all five runtimes
(JVM, Native x86-64, Native riscv64/aarch64, JS, Script) execute the same
semantics. The runtime name convention follows the repo:
`kof_expr_<op>` (Native/JS/interpreter, camelCased on JS as usual:
`kof_expr_x` → `kofExprX`, see `js/JsTypeMapper`), and the JVM side goes through
the generated `KofRuntime.java` allow-list (`jvm/JvmRuntime.java:23-91`). None
of this is exposed publicly.

Two known divergences must be **decided, not inherited**:

- `String.split`/`replace` regex-vs-literal: `Kof.expr` should offer a
  `split`-like or `findAll` operation with *documented* semantics; it must not
  silently add another divergent behavior.
- `validation.matches`: once `Kof.expr` exists, `validation.matches` can be
  re-expressed in terms of it (a follow-up, not v1), removing the
  JVM-regex/Native-literal divergence recorded above.

## 10. Unicode semantics (non-negotiable explicitness)

`Kof.expr` must not silently define `letter = [A-Za-z]`. The plan:

- The subject model is the Kof string model (UTF-8 + NUL on Native,
  `docs/runtime/STRING_MODEL.md`); "character" means a Unicode scalar value,
  and character access is by code point, not UTF-16 unit or byte.
- `letter`/`digit`/`whitespace`/`word` are defined by an explicit Unicode
  category table shipped with the library (documented, versioned), not by the
  backend. `hex` is ASCII by definition.
- `any` = one Unicode scalar. Byte-oriented `any` is not offered in v1.
- Normalization (NFC/NFD) is **not** implicit; a `normalized(...)` combinator
  may be added explicitly if demanded (open question §16 Q7).
- The exact Unicode version pinned by the library must be recorded in
  `docs/backend-parity.md` (the same discipline used for `STR`/Unicode faces).

## 11. Performance

- Patterns are **compiled once** at declaration/`val` initialization, not per
  call; repeated `matches` reuse the compiled form (caching keyed by the
  pattern value identity).
- Strategy P is O(n·m) worst-case for a straightforward backtracking matcher;
  the plan **forbids** unbounded backtracking by construction (§12). The
  recommended v1 matcher is a Thompson/NFA-style or explicitly step-bounded
  engine so catastrophic backtracking cannot occur (this is a *design* choice,
  made now, not a later patch).
- Allocation: match results allocate only on `match`/`findAll`, not on
  `matches`.
- Input limits are explicit and documented (§12); no unbounded capture buffers.

## 12. Security (safe by default)

- **ReDoS is designed out**: the v1 engine must not use naive backtracking with
  unbounded repetition. If Strategy C uses a host engine, patterns that could
  backtrack catastrophically are either rejected at compile time or executed
  with a documented step/time budget.
- Untrusted input: a documented maximum subject length and a step budget, with a
  distinct diagnostic when exceeded (`KOF-E2xxx` runtime family), never a hang.
- Dynamically built patterns (a pattern value assembled at runtime) are allowed
  only through the type-checked combinator API — a pattern can never be built
  from a raw regex string, so injection of regex syntax is impossible by
  construction.

## 13. Diagnostics (errors must teach)

A dedicated family (final codes at promotion), emitted with file/line/column +
context, following `DiagnosticCollector` (`DiagnosticCollector.java:25-57`):

- Definition/parse errors (`KOF-P…`): missing `}`, unknown combinator, bad
  literal.
- Semantic errors (`KOF-E1…`): `between(3, 2)`, `exactly(-1)`, duplicate capture
  name, unknown `expr` reference, recursive pattern, quantifier applied to
  nothing.
- Runtime errors (`KOF-E2…`): subject too long, step budget exceeded.
- **No match is not an error**: `matches` returns `false`, `match` returns
  `null` (nullable, consistent with Kof nullability), `find` returns `-1`/`null`
  per the chosen signature. This distinction is documented explicitly.

Every new code must be added to `docs/backend-parity.md` (the R6 ledger checked
by `DomainGapParityMatrixTest.everyPinnedGapIsDocumentedInTheParityMatrix`), even
though v1 has no target gaps.

## 14. Integration checklist (files a promotion slice must touch)

Modeled on the enum path; each is referenced with its current line range:

| Stage | File |
|---|---|
| Contextual keyword | `parser/Lexer.java:13-85` |
| Top-level dispatch | `parser/Parser.java:41-86`, `parser/TypeDeclarations.java:35-45` |
| AST nodes | new `ExprDeclarationNode.java` + pattern-node records |
| Symbol table | `SymbolTableBuilder.java:15-125` |
| Semantic | `SemanticAnalyzer.java:241-253` + new `SemExprAnalyzer.java` |
| Lowering | `CompilerPipeline.lowerToIR:215-275` + new `CompilerExprLowering.java` |
| Runtime tables | `KofExpr.java` (namespace gate/`supportedOn`), `jvm/JvmRuntime.java:23-91`, `js/JsRuntimeOps.java`, `nat/NativeRiscvCrossOps.java`, `RuntimeValidation`-style native files, `KofInterpreterCollections.java` |
| Ledger | `scripts/stdlib_boundary.txt`, `docs/backend-parity.md` |
| Docs | `docs/language-reference/*`, `docs/stdlib/*`, this plan moved to `docs/` |
| Corpus | `training/idioms/` (a new `kof-expr.md`), `training/anti-patterns/fake-idioms.md` (what `Kof.expr` is *not*) |

## 15. Documentation deliverables (at promotion)

`docs/` gets: what/why, syntax philosophy, literals, semantic classes,
quantifiers, composition, captures, match, search, replace, Unicode,
performance, security, cross-target, and real examples. Mandatory section
**"Kof.expr vs Regex"** explaining that *regex describes how characters are
recognized through compact notation, while Kof.expr describes the intention of
the pattern through explicit constructions* — not a superficial syntax diff.

## 16. Open decisions (rule 6 — maintainer)

1. **Q1 Block vs parens** for grouping/quantifier targets: `{ … }` everywhere
   (recommended) or `( … )`.
2. **Q2 `expr` declaration vs `val` only** (Shape A vs B) — does the language
   gain a new top-level kind, or is a composable value enough?
3. **Q3 Open-ended `between(min, max)`** — allow `max` omitted (means "≥ min")?
4. **Q4 Capture result typing** — Option T (generated record, preferred) vs
   Option U (`Match` accessor).
5. **Q5 Capture-aware replace** — include a function-template form in v1?
6. **Q6 Numeric/structural expansion** (e.g. true IPv4 octet, IPv6) — in the
   library, not the core.
7. **Q7 Normalization** — offer explicit `normalized(...)`?
8. **Q8 Fate of `validation.matches` and `String.split/replace` divergences**
   once `Kof.expr` exists.
9. **Q9 The engine** — pure-Kof v1 (P) is the recommendation; confirm native
   engines (JVM `Pattern`, JS `RegExp`) are acceptable later as *internal*
   lowerings.
10. **Q10 Naming** — `Kof.expr` vs a namespace such as `kof.expr`
    (`KofExpr.java`) vs a language kind. The user's spelling is `Kof.expr`;
    confirm the canonical Kof surface (`expr` keyword + `kof.expr` runtime
    namespace) is acceptable.

## 17. Phased roadmap (each slice is one lane, RED-first)

- **Spike-0 — inventory (this doc's companion, promotion slice 0).** Lock the
  grammar and the AST; write the parser tests first (no lowering).
- **Slice 1 — atoms + sequence + literal + match/matches** on JVM + Script
  (Strategy P), cross-checked byte-for-byte.
- **Slice 2 — quantifiers** (`optional`/`zero_or_more`/`one_or_more`/`exactly`/
  `between`).
- **Slice 3 — classes** (`letter`/`digit`/`whitespace`/`word`/`hex`/`any`) with
  the pinned Unicode table.
- **Slice 4 — groups + `either` (alternation) + `one_of`/`none_of`.**
- **Slice 5 — captures + `match`/`captures` + result typing (Option T).**
- **Slice 6 — Native x86-64 + riscv64/aarch64 parity.**
- **Slice 7 — JS parity.**
- **Slice 8 — `find`/`findAll`/`replace` + security budgets + performance
  benchmarks.**
- **Slice 9 — docs + corpus + conformance-matrix + the mandatory examples
  compiled and tested.**

Each slice must be promoted explicitly; none starts from this document alone.

## 18. Non-regression contract

- Additive only: existing identifiers named `expr` keep working (contextual
  keyword).
- `STR003` and all existing String/`validation` behavior unchanged in v1.
- No change to frozen surfaces (`operators`, `precedence`, `evaluation order`,
  `null safety`, `content ==`, `String exceptions`, `spawn/await`,
  `List/Map/Set`).
- Every slice carries tests in the same commit (`AGENTS.md` Q0–Q7).

## 19. Final review question (before promotion → implementation)

> "If I had to teach `Kof.expr` to someone who has never seen regex, could they
> write an expression after reading the documentation?"

If no — simplify. If yes — the plan is ready to be promoted to
`docs/development/` and implemented end to end, starting at Spike-0.
