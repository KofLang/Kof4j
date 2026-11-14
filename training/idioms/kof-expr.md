[English](kof-expr.md) | [Português](kof-expr.pt_BR.md)

# Idioms — Kof.expr (text patterns)

**Status:** available · **Introduced:** 0.6.7 · **Decision:** `D-KOF-EXPR` · **Reference:** [docs/kof-expr.md](../../docs/kof-expr.md)

## What it is

`Kof.expr` is the language-native way to declare a TEXT PATTERN as a named
top-level value. It is NOT regex: regex describes how characters are recognized
through compact notation; `Kof.expr` describes the intention of the pattern
through explicit constructions (`letter`, `one_or_more`, `capture`, `between`).

## Canonical forms

```kof
// declaration — a named pattern, top-level
expr date {
    capture("day")   { exactly(2) digit }
    "/"
    capture("month") { exactly(2) digit }
    "/"
    capture("year")  { exactly(4) digit }
}

// match + typed captures (a record field per capture)
var m = date.match("25/12/2025")
if (m != null) {
    println(m.year)
}

// boolean test
if (wrd.matches(input)) { ... }

// search / replace
var at = wrd.find(text)            // first index or -1
var all = wrd.findAll(text)        // every match
var fixed = wrd.replace(text, "X") // every match replaced

// composition by reference — patterns name patterns
expr row { date "-" word }
```

## Rules

* classes are SEMANTIC (`letter`/`digit`/`whitespace`/`word`/`hex`) — Unicode
  16.0.0 pinned in the compiler, identical on every target; `hex` is ASCII
* quantifiers: `one_or_more` / `zero_or_more` / `optional` / `exactly(n)` /
  `between(min, max)`
* alternation: `one_of { … }` / `none_of { … }`
* anchors: `^` / `$`
* subjects above the documented budget (2,000 chars on JVM/JS, 200 on Script)
  throw EXPR-E2 BEFORE matching — the matcher is stack-bound by design
* refusal codes are explicit (EXPR001–EXPR004): unsupported constructs refuse
  at compile time, never a silent wrong match
* a declaration named `word` collides with the class atom — the compiler
  refuses it (EXPR001 family); name patterns differently
