[English](PLAN-BOOTSTRAP.md) | [Português](PLAN-BOOTSTRAP.pt_BR.md)

# Strategic plan — the Bootstrapper: Kof written in Kof (BS-1)

> **State (20/09): FUTURE — design plan only, zero code.** Decision
> `DECISIONS.md` §D-BOOTSTRAP (maintainer, 20/09): the bootstrapper is the
> **final objective (north star)** of the platform, reached as the LAST stage
> of everything. Drafting was pulled forward (owner lane `.18`); execution is
> gated by three-states + R12: it may not start before the 1.0 EXIT GATE
> (`roadmap.md` §24, `PROPOSAL-1.0-EXIT-GATE.md`) closes and no existing
> stage may be skipped for it. Every "escape hatch" the bootstrap seems to
> need is a rule-6 maintainer decision — **no language feature is justified
> "for the bootstrapper"** (D-BOOTSTRAP, verbatim). The Java core stays the
> frozen reference; the golden E2E corpus is the oracle (Q0–Q7 apply to the
> bootstrap exactly as they apply to today's compiler).

## 1. Objective

A Kof compiler **written in Kof** (`kofc.kf`) that compiles the whole corpus
to artifacts that are **byte-identical** to the ones the Java implementation
produces today — and then compiles **itself** (fixed point, §6). After that,
"Kof as its own cloud" closes end-to-end: the compiler compiles itself,
provisions its infrastructure (Makealive, stage 7), runs on it (Native /
bare-metal per `../../PLAN-BAREMETAL-BOOT.md`), and self-hosts its packages
(`kof deps`, stage 1.4).

## 2. Entry conditions (execution may not be claimed before ALL hold)

| # | Condition | Where tracked |
|---|-----------|---------------|
| E1 | Kof 1.0 EXIT GATE green (EG-1..EG-10 on one RC candidate) | `roadmap.md` §24 |
| E2 | Zero OPEN release-blockers (`--rc-gate` rc=0) | gate scripts |
| E3 | §388 closed ✅ 21/09 — the bootstrapper is a BYTES-heavy program (`writeBytes`/`readBytes`/`Int[]` coercion + array printing contract) — A: `SEM099` compile-time diagnostic; B: container format declared (`D-ARRAY-PRINT`) | `known-bugs.md` §388 |
| E4 | FFI structs ratified (D6-1..D6-5) — the compiler needs byte-level buffer/struct control | `DECISIONS.md` §D6-*, `ffi-abi-structs.md` |
| E5 | Native GC + multi-arch stable (1.2) — the self-hosted compiler must run on Native, not only JVM | `roadmap.md` §23 TIER 1 |
| E6 | Language-gap inventory (BS-A below) reviewed and scheduled by the maintainer | this plan |

## 3. Phases

**BS-A — Gap inventory (audit, zero code).** Run the current compiler over
itself: for every Java construct `kof-compiler` uses, decide (i) expressible
in Kof today, (ii) expressible with existing stdlib (name the face), or
(iii) language gap → **filed as a normal, bootstrapper-independent gap**
(each justified on its own merits, never "for BS"). Foreseen gaps (to
confirm in BS-A): `sealed`/exhaustive `switch` on AST shapes (pattern
matching), recursive union types for the AST without boxing, growable byte
buffers (E3/§388 family), high-throughput string building, hash maps with
mutable values under hot loops, recursion depth control (parser is
recursive-descent), and an FFI-free path to write ELF/Mach-O/PE (codegen
targets today are Java libraries — the Kof compiler will need its own
emitters or the FFI ratified in E4). Output: `future/BOOTSTRAP-GAPS.md` (a follow-up artifact of this plan, not yet created)
(table per construct × decision × issue number).

**BS-B — Lexer + parser in Kof.** Pure-functional surface (text in, AST
out): first component where "expressible today" is provable. Proof:
differential test against the Java parser — same corpus, same AST dumps,
byte-equal — the Java parser stays the oracle (never replaced silently).

**BS-C — Semantic + JVM codegen in Kof (runs on the OLD runtime).** The
Kof compiler is a program compiled BY the Java compiler, executing on the
Java runtime, emitting `.class` files byte-equal to the Java compiler's
(K&R stage 2). Proof: every `*E2ETest` JVM golden runs with `kofc` as the
compiler under test and passes byte-identical.

**BS-D — Full target parity (JS, Script, Native, KofC, Android).**
Port emitter-by-emitter; the conformance matrix (`ConformanceMatrixTest`,
8 targets) becomes the gate for the Kof compiler too. Proof: the matrix is
green with `kofc` producing the artifacts.

**BS-E — Fixed point + retirement of the bootstrap role.** `kofc` compiled
by `kofc` (stage 3): three-way comparison — Java-built `kofc`, `kofc`-built
`kofc`, and the second iteration — all artifacts byte-equal (quining check:
the compiler output for its own source must reproduce itself exactly). The
Java core then becomes **reference-only** (frozen oracle kept forever, not
deleted); registry + Makealive consume `kofc` (D-KOF-AS-CLOUD closes).

## 4. Oracle & testing discipline

Zero new truth sources: the bootstrap passes **the same golden corpus** as
every other target — same bytes, same §147/§149/§174-class rules, same
Q0–Q7. Differential fuzzing (Java-compiler vs Kof-compiler on generated
programs) is a BS-C+ tool, built from `kof.test`, not a new framework.
Any golden that would need "adjusting" for `kofc` is a BOOTSTRAP BUG, never
a golden edit (the corpus is frozen law, D-BOOTSTRAP).

## 5. Dependency map (what already exists that BS uses)

`kof.file` plan (file tree APIs) · §388/E3 bytes faces · FFI structs (E4,
`ffi-abi-structs.md`) · `kof.test` + the corpus itself (~2.8k tests today) ·
`kof.workflow`/Makealive (stage 7) for CI on self-provisioned infra ·
`PLAN-BAREMETAL-BOOT.md` (1.6) for the Native-resident compiler · package
registry (`kof deps`, 1.4) for self-hosting the compiler as a package.

## 6. Out of scope / non-promises

No dates, no effort estimates (planning ≠ queue); no language changes
decided here (E6/BS-A only FILES them); no deletion of the Java core (it is
the permanent oracle); no parallel dialect (the bootstrapper uses the same
frozen semantics every program uses); performance of `kofc` is a BS-C+
observation, never a reason to weaken §4's byte-equality.
