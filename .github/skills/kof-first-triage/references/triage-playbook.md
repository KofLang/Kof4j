[English](triage-playbook.md) | [Português](triage-playbook.pt_BR.md)

# KOF-first Gate — triage of NOT-VALID, contract-conflicting and out-of-scope issues/PRs

**Repository:** `KofLang/Kof4j`
**Reference branch:** `lab` (`D-BRANCH-PIPELINE`: `lab` → `testing` → `prerelease` → `stable` → `release/x.y.z`; `beta-*` is frozen and no longer exists on the remote)
**Purpose:** review issues and pull requests that treat foreign syntax or semantics as a bug, contradict an already documented KOF contract, or try to introduce a language extension under the label of a fix.

> This document is an **analysis and action playbook**. It does not authorise automatic closure from any list below. Before commenting, closing an issue or closing a PR, the agent must re-read the current state of the branch, the issue, the PR, the most recent comments and the applicable contract.

---

## 1. Main rule

Apply the `D-KOF-FIRST` flow (rule 10 of `AGENTS.md`):

```text
reproducer
    ↓
KOF VALIDITY
    ↓
CONTRACT SOURCE
    ↓
KOF IDIOM SEARCH
    ↓
MEASUREMENT
    ↓
CLASSIFICATION
    ↓
┌─────────────────────────────┐
│ BUG REAL / TARGET DIVERGENCE│ → a fix may exist
└─────────────────────────────┘

NOT-VALID
    ↓
explain the existing KOF form
    ↓
close the issue / close the PR without merge

DESIGN REQUEST / GAP / CONTRACT AMBIGUITY
    ↓
do NOT implement as a bugfix
    ↓
route to language discussion / decision
```

**Principle:** the behaviour of Java, Kotlin, C#, JavaScript, the JVM or any other platform **is not the oracle of the KOF language**. The oracle is KOF's internal contract.

Order of evidence:

1. `docs/development/DECISIONS.md`;
2. normative documentation under `docs/language-reference/`;
3. conformance/golden tests;
4. `training/`, `learn/` and `training/anti-patterns/fake-idioms.md`;
5. parity matrix;
6. current implementation;
7. only afterwards, external references.

---

## 2. What must be considered NOT-VALID

Classify as **NOT-VALID** when:

- the reproducer uses syntax that **does not belong to KOF**;
- KOF already has a documented way to express the same intent;
- the compiler rejection is coherent with the current contract;
- the issue calls the rejection a "bug" only because the construct works in Java/Kotlin/C#/etc.;
- the PR "fixes" the parser by accepting a second, unauthorised syntax.

In these cases, **do not change the compiler** and **do not improve the diagnostic as a pretext to keep the issue open**, unless a specific decision or documentation requires that diagnostic.

The normal action is:

```text
1. prove the correct KOF syntax;
2. cite the contract;
3. show the correct example;
4. explain that the need is already met;
5. close the PR without merge, if there is one;
6. close the issue as not planned / not a bug under the current contract;
7. if the other form has merit, point to the separate design path.
```

---

## 3. What must be considered CONTRACT CONFLICT

Classify as **CONTRACT CONFLICT** when the PR or issue requests behaviour that is explicitly forbidden or already decided against.

Examples:

- allowing `T? = null` when the standing contract determines `SEM048` for a `null` literal assigned directly;
- implementing behaviour already rejected by a language decision;
- changing a frozen semantics without a maintainer decision;
- reopening, through the parser or a backend, a surface the contract says does not exist.

The action is stronger than for a plain NOT-VALID:

```text
do not merge
    ↓
cite the normative decision that would be violated
    ↓
show the current KOF behaviour
    ↓
explain that changing it requires a SUPERSEDING / new decision record
    ↓
close the current bugfix
```

Never write that the proposal is "bad" or "forbidden forever". The correct wording is:

> The change can be discussed, but it cannot enter as a bug fix while the standing contract says otherwise.

---

## 4. DESIGN REQUEST / legitimate GAP outside the scope of a bugfix

Some issues may reveal a real need and still **not be bugs**.

Examples:

- a new alternative syntax;
- new contextual inference;
- new flow sugar;
- a new matching pattern;
- a new collection API;
- a new field form;
- widened SAM conversion;
- a new nullability rule.

In these cases:

1. do not close the need as "worthless";
2. do not merge the production PR;
3. close or reclassify the bug issue, since the bug does not exist;
4. route to a separate **design discussion**;
5. require impact on grammar, semantics, documentation, tests and every relevant backend;
6. wait for the maintainer's decision before production code.

### Shape of a correct request

Suggested title:

```text
[Design] Should KOF support <new form/behaviour> in addition to <current form>?
```

Minimum body: need · current KOF contract · current KOF idiom · proposed change · why the current idiom may be insufficient · grammar and semantic impact · cross-target impact · compatibility and migration · alternatives · the objective decision requested from the maintainer.

**Do not implement before the decision.** Do not create the replacement feature issue automatically — provide the text and let the maintainer or the contributor decide whether to open it.

---

## 5. Operational order for the agent

Run **one issue→PR pair at a time**.

```text
A. read the full issue + every comment
B. read the full PR + patch + reviews + checks
C. check the current branch tip
D. locate the normative contract
E. validate the reproducer's syntax
F. search for the existing KOF idiom
G. measure when necessary
H. classify
I. write an individual, evidence-backed comment
J. if NOT-VALID / CONTRACT CONFLICT:
     - close the PR without merge
     - close the issue when appropriate
K. if DESIGN REQUEST:
     - do not merge production code
     - route to a separate discussion
L. only then move to the next pair
```

**Never do bulk closure with a generic message.** Each item gets its own proof — including its own measurement on the current tip.

---

## 6. Criteria for closing a PR without merge

Close the PR when any of these is true:

- it implements syntax that does not exist in the contract;
- it adds an alias or sugar with no decision;
- it contradicts a standing decision;
- it treats JVM/Java behaviour as a KOF requirement;
- it "fixes" the reproducer by changing the language instead of using the correct KOF syntax;
- it depends on an issue whose correct classification is NOT-VALID;
- it changes type-inference, narrowing or pattern semantics with no decision;
- it has no RED→GREEN proof as required by the Quality Gate.

Before closing, check whether the PR contains a **separable, legitimate fix** — for example, a diagnostic emitted at position `0:0` can be a real defect even when the request for a new API is not. If so, ask for or produce an isolated PR for the legitimate defect only.

---

## 7. Quality gate for every PR

Even a semantically correct PR must not be merged without proof.

```text
valid KOF reproducer
      ↓
RED on the previous code
      ↓
root-cause fix
      ↓
regression test in the same PR/commit
      ↓
GREEN
      ↓
relevant parity
      ↓
repository suite / gates
```

**A test does not replace a language decision.** A PR adding new syntax with tests is still incorrect if no decision authorises the new syntax.

---

## 8. Comment templates

The closure and design-request templates live in [`templates.md`](templates.md). Adapt each one individually; never copy without replacing the evidence.

---

## 9. Executed precedents (worklog — not a queue)

These cases are settled. They are recorded as **reasoning precedents**; they are not an open queue. Revalidate any new item against the current branch before acting.

### 9.1 Typed lambda parameters — `#492` / PR `#496`, `#509` / PR `#520`

```text
reproducer uses (Int x) -> ...
        ↓
contract documents (x: Int) ->
        ↓
the "typed lambda" intent is ALREADY supported
        ↓
therefore it is not a bug
        ↓
a PR accepting `Type name` would add a second syntax
        ↓
close without merge
        ↓
if there is interest, open the discussion:
"Should lambda parameters accept both name: Type and Type name?"
```

PR #520 claimed that #492 had "fixed typed lambda parameters inside function-call arguments" — it had not: #492 was closed as not-a-bug and #496 was closed without merge. A false premise in the chain is itself worth correcting explicitly.

### 9.2 Bitwise NOT `~` — `#506` / PR `#516`

`docs/language-reference/grammar.md` §5.3 lists `~` **by name** under "Operators that do NOT exist (SG-002, verified by probe)", and the unary production excludes it. `LEX005` is therefore the expected rejection of unsupported syntax. The issue's motivation ("present in Java, C, and most languages targeting the JVM") is the exact inference `D-NOT-JAVA` excludes. The existing KOF alternative is the XOR operator: `x ^ -1` (measured `-6` for `x = 5`).

### 9.3 The 19/09 batch — thirteen PRs closed without merge

All measured on the tip of the day, each with its own contract citation:

| Class | Item | Contract / measurement |
|---|---|---|
| NOT-VALID | `#536`/PR `#540` — `for (a in items)` without `var` | `for-in = ( "var" \| "val" ) , identifier , "in" , expression ;` (`grammar.md` §6) + `fake-idioms.md` (❌ Unavailable); measured `1 2 3` with `var`, `SEM011` without |
| NOT-VALID | `#537`/PR `#541` — multi-label `case A, B` | `case-expr` is one pattern-or-expression; measured `PARSE076`+`PARSE041`+`PARSE078` vs `vowel` ✓ with separate cases |
| NOT-VALID | `#535`/PR `#539` — enum `.name`/`.ordinal` as properties | contract is the **method** form (`classes.md`); measured `SEM030` on the property, `Red`/`0` on the method |
| NOT-VALID | `#527`/PR `#531` — field `count: Int` | fields are `Type name` (`classes.md` §1); measured `0` ✓ for `Int count = 0`, `PARSE018`+`PARSE016` for the colon form |
| NOT-VALID | `#510`/PR `#522` — `static count: Int` | same surface as `#527`; one field-syntax discussion, not one per modifier |
| NOT-VALID | `#528`/PR `#532` — `var count` in a class body | `classes.md` §1 (`val x = 1` → `PARSE016`); measured `PARSE016`+`PARSE018` |
| NOT-VALID | `#508`/PR `#519` — "`class Box<T>` rejected" | **false root cause**: `class Box<T>` measured `7`; the `PARSE016` comes from the field `val: Int = 42`, and the patch added a `VAL` branch instead of fixing generics |
| CONTRACT CONFLICT | `#507`/PR `#517` — `String? s = null` | `SEM048` / SG-008 + `D-NULL-INTENT` (maintainer decision, 09/09); the patch changed `StatementAnalyzer`, the component that implements the rule; measured `SEM048` on the literal and `0` ✓ through `map.get` |
| DESIGN REQUEST | `#513`/`#529`/`#530` + PRs `#525`/`#533`/`#534` | the collection HOFs **already** infer (`map` measured `3`, `reduce` measured `6`); `var f: (Int) -> Int = (x) -> …` gives `SEM001` — one consolidated thread, cross-referencing `#501` and the SAM family (`#298`/`#310`) |
| DESIGN REQUEST | `#514`/PR `#526` — narrowing after a terminating guard | documented narrowing is the then-branch form (`type-system.md` §5); kept open as the tracking item for the need |
| DESIGN REQUEST | `#538`/PR `#542` — primitive type pattern | `SEM035` is a **deliberate** rejection (07/09), not an accident; the discussion must define semantics on all targets |

Two lessons that came out of this batch and apply to the next one:

1. **Isolate the reproducer (Gate 0).** `#508` reported a generics bug whose actual cause was an unrelated invalid field. When a reproducer mixes constructs, test each one separately before accepting the reported cause.
2. **The `fix:` label is not a classification.** Several patches were technically plausible and still could not enter: what was missing was a decision authorising the new surface, not quality of implementation.

---

## 10. Expected result

At the end of a triage sweep:

- no foreign behaviour should have become KOF by accident;
- no standing contract should have been changed by a `fix:`;
- every NOT-VALID issue should show **the correct KOF form**;
- every legitimate need outside the contract should have **a clear design path**;
- incompatible PRs should be closed without merge;
- real, separable bugs should remain traceable in their own issues/PRs;
- the backlog should reflect **KOF** problems, not differences between KOF and other languages.

---

## Final rule

> **First prove what KOF is. Then ask whether KOF should change. Never invert that order.**
