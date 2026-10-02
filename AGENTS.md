# AGENTS.md

last: 0.5.0-beta
doing: #651-COMPLETE (Buffer(U8) surface + FFI token B on x86-64 AND cross riscv64/aarch64; fatia B 29/09) + #678-D-SCRIPT-WARN-SURFACE-landed (Script surfaces frontend WARNING diagnostics) + phase-5-unit-3-pinned (Buffer(U8) INOUT × spawn/await runtime parity) + phase-5-unit-2-landed (#667 Script×extern FFI001 at decl line + #668 MEM020 compile face) + memory-safety-phase-4-CLOSED (#658/#659/#662) + #660-D-MEM021-SCALAR-landed (c65f9ba18, maintainer A/ERROR) + evidence-chain-hardened (#664/#665/#669) + phase-5-unit-1-pinned (#666) + ownership-table-landed (#670) + stale-cells-purged (#671) + ledger-selftest-pt-proven (#672) + living-records-registered (#673)
next: phase-5 unit 4 (measure-first) / promotion-sweep (lane pipeline) / 14.4-rulesets (mantenedora)
location: repository
state: active

intent: agent-operating-contract

constraint:

* quality-first
* no-bug-ships
* no-stubs
* zero-regression
* additive-compatibility
* compile-before-delivery
* test-with-change
* active-branch-only
* no-tmp-worktree
* maintainer-controls-design
* maintainer-controls-main-merge
* Kof-first
* simplicity-first
* honest-cross-target
* no-silent-fallback
* no-hallucinated-syntax

decision:

* D-BRANCH-PIPELINE: active branch = `lab`; promotion is explicit and one-way `lab → testing → prerelease → stable → release/x.y.z → tag` (`D-QUALITY-PIPELINE-2609`)
* D-KOF-FIRST: Kof contract precedes external language behavior
* D-KOF-FIRST-IMPL: post-0.5.0 features are library-first
* D-MAKEALIVE
* D-KOF-AS-CLOUD
* D-BOOTSTRAP
* D-DB-GAPS
* D-GRAPHICS-GAMING
* D-KOFMD-ON-EDIT: every document an agent edits is Kofmd-compressed in the same commit
* D-KOFMD-OPERATING-STANDARD: every agent thinks, reasons, responds, executes and documents in Kofmd — uniform, no per-agent variant
* D-FUTURE-PROMOTION: before starting new work, migrate to `lab` with all current work, then promote the EASIEST-to-implement plan from `docs/development/future/` to `docs/development/` and implement it — never the most interesting, never a frozen-semantics plan

---

## Operating loop

state:
mode: autonomous

loop:

* read DOING.md
* read docs/status.md
* inspect git log and suite
* ensure the active branch is `lab` — migrate all current work to `lab` BEFORE starting; `beta-*` is frozen (`D-BRANCH-PIPELINE`)
* if no live unowned task, promote the lowest-cost implementable plan from `docs/development/future/` (see Future promotion, `D-FUTURE-PROMOTION`)
* choose highest-value unowned task
* claim it in DOING.md with `owner = <local-ipv4>:<opencode-port>` (EN) / `dona = <local-ipv4>:<opencode-port>` (PT) — the **absolute identity rule** (`D-AGENT-IDENTITY-IPPORT`, 01/10); a claim without IP:PORT is INVALID (gate `scripts/check_owner_identity.sh`)
* execute one complete scope
* test
* commit with DOING.md (every commit updates the claim's `owner = <ip>:<port>`)
* push through scripts/sync-push.sh
* re-read DOING.md
* continue

rule:

* never ask when repository evidence can answer
* never end a turn with an uncommitted unit
* never end a turn with only a summary
* exactly one todowrite item may be in_progress
* completed means proven, not intended
* NEXT STEP in DOING.md must contain task + file + expected proof

stop:

* frozen-semantics decision required
* unavoidable lane collision
* unexplained external regression
* requirement absent from corpus/compiler
* stable repository

stable:
bugs: closed
development: concluded
future: implemented-or-promoted
suite: green
conformance: 5/5

on-stable-retrigger:

* verify regression
* if regression: claim and execute
* otherwise: record stable refusal
* stop scripts/auto-loop.sh
* stop

---

## Autonomous heartbeat

entry:

* scripts/auto-loop.sh start
* scripts/auto-loop.sh status

constraint:

* one heartbeat session
* --attach mandatory
* flock prevents overlap
* server health required
* stop heartbeat when autonomous mode ends

sessions:
heartbeat:
port: 9093
session: ses_f69e2a3f7ffe9J10aWcHEUOfW8
issue-watcher:
port: 9094
session: ses_f69c2cb03ffe2zDYCqW7fesphi

issue-watcher:

* dispatch only on new issue
* dispatch on edited title/body
* dispatch on external comment
* bot's own comment does not retrigger
* human comment always retriggers
* triage before widening scope
* another lane's front is not touched

---

## Authority

authority:
maintainer: Mel Santos
architecture: maintainer
frozen-semantics: maintainer
main-merge: maintainer

rule:

* AI accelerates implementation
* AI does not define architecture
* AI does not redefine Kof semantics
* AI does not merge any stage into main
* every change requires an issue
* every delivery requires proof

identity:
preferred: kof-agent-worker
by: local-ipv4 + opencode-port (absolute, mandatory — 01/10 amendment, `D-AGENT-IDENTITY-IPPORT`)
fallback: maintainer-default
forbidden:
- synthetic email
- Co-authored-by
- identity tricks
- owner = <ipv4> WITHOUT :<port> — the gate rejects it
- owner = <ipv4> WITHOUT :<port> — the gate rejects it

rule:

* identify by the **local IPv4** (`hostname -I`) AND the **opencode server port** the session attaches to (`ss -tln | grep opencode` / the running `opencode -s ... --port <N>` or `--attach http://127.0.0.1:<N>` / `ps -o args= -C opencode`) — DOING §Operating-loop rule 9
* every `IN PROGRESS`/`DONE`/`FIXED`/`STOP` claim carries `owner = <local-ipv4>:<port>` (EN) / `dona = <local-ipv4>:<port>` (PT), never just "this session" and never bare IPv4 (a bare IP is ambiguous when the same host runs more than one session/lane; 110 historical lines recorded `owner: this session` with no lane attributable)
* the enforcement gate is `scripts/check_owner_identity.sh` — rc=1 on any claim dated ≥ `01/10` whose IPv4 lacks `:<port>`
* never act on another owner's lane on IP alone — confirm by session + lane + commit SHA + IP:PORT (routers/DHCP change both)

---

## Multi-agent state

source:

* DOING.md
* git
* compiler
* tests

claim:

* read DOING.md before work
* existing IN PROGRESS item is not yours
* claim before implementation
* **absolute identity rule (`D-AGENT-IDENTITY-IPPORT`, 01/10): every claim is `owner = <local-ipv4>:<opencode-port>` (EN) / `dona = <local-ipv4>:<opencode-port>` (PT) — a bare IPv4 or "this session" is INVALID and the gate `scripts/check_owner_identity.sh` rejects it (rc=1)**
* claim and first change share a commit
* every commit updates your DOING.md line
* DONE requires date + SHA + proof
* abandoned work returns OPEN with state
* orphaned owner may be reassigned after code verification

collision:

* never two agents on one gap
* never two agents on one giant file
* coordinate before unavoidable collision

shared-claim:

* §NNN numbers come from remote tip
* git fetch before selecting number
* re-check number after rebase

sync:
before_commit:
- git fetch
- git pull --rebase --autostash
- inspect conflicts
- grep touched files for ^<<<<<<<
- rerun gates on post-rebase tree
after_commit:
- scripts/sync-push.sh
- verify ahead=0
- verify behind=0

conflict:

* preserve both sides
* redo own change on top
* never checkout --ours
* never checkout --theirs
* never treat rebased content as stale

---

## Documentation state

state_model:
docs:
meaning: implemented-validated-decided
development:
meaning: implementation-pending
development/future:
meaning: planned-not-started
bugs-and-gaps:
meaning: living bug-gap-parity records
audits:
meaning: state snapshots
development/DECISIONS.md:
meaning: maintainer decisions

rule:

* audit development before new work
* implemented + validated → docs/
* partially implemented → finish → docs/
* planned only → development/future/
* obsolete/duplicate → consolidate
* concluded work never remains in development/
* future is not current work without promotion
* do not create planning documents to avoid implementation
* a chat decision becomes DECISIONS.md + queue in the same commit
* every document an agent edits is Kofmd-compressed in the same commit (`D-KOFMD-ON-EDIT`)

hot_docs:

* DOING.md
* DOING.pt_BR.md
* docs/status.md
* docs/status.pt_BR.md
* docs/development/*-plan.md
* CHANGELOG.md
* CHANGELOG.pt_BR.md
* docs/bugs-and-gaps/known-bugs.md
* docs/bugs-and-gaps/known-bugs.pt_BR.md
* docs/development/roadmap.md §23

kofmd:

* hot_docs use canonical state blocks
* field order: last, doing, next, location, state
* add constraint/decision when needed
* typed information is typed
* prose only carries information not representable structurally
* never duplicate fields in prose
* mandatory on edit: any doc an agent touches is compressed in the same commit
* learn/ and training/ are excluded from Kofmd compression
* operating standard: every agent thinks, reasons, responds, executes and documents in Kofmd — uniform, no per-agent variant (`D-KOFMD-OPERATING-STANDARD`)
* evidence before inference; `unknown` over `probably`; never fabricate api/syntax/behavior/decision/result/contract
* `implemented` != `verified`; claim a result only with executed proof
* `last` = immediately relevant prior state; `next` = next intention, not backlog
* prose only where structure cannot carry the information
* coordination: claim before work; on lane collision wait for the owner or stop, never race the shared worktree; never end a turn with an uncommitted unit; push only via `scripts/sync-push.sh`

---

## Future promotion

intent: future-is-not-current-work-without-promotion

rule:

* before starting new work: migrate to `lab` with ALL current work first; never start on `beta-*` or a detached checkout (`D-BRANCH-PIPELINE`)
* promote exactly ONE plan from `docs/development/future/` to `docs/development/` and implement it
* choose the EASIEST to implement (lowest cost) — never the most interesting, never the largest

easiest (highest wins):

* no `D-*` decision required: not frozen-semantics, not a missing core primitive
* additive and library-first: Kof can express it without changing the language surface (`D-KOF-FIRST`)
* dependencies already measured in code (the plan names real files/lines)
* single cohesive scope for one lane (one responsibility)
* a clear test path exists now (RED-first proof is definable)

ineligible:

* needs a frozen-semantics or `D-*` maintainer decision first
* needs a new core primitive or syntax
* accepts a gap, ships a stub, or weakens an assertion
* rationale is "it would be nice" instead of "it is the cheapest complete increment"

flow:

* rewrite the plan with status `UNDER DEVELOPMENT` + real state + how-to-finish
* move it to `docs/development/<plan>.md` (+PT) in the SAME commit that claims it
* queue it in `roadmap.md` §23 and point `docs/status.md` at it
* claim in DOING.md (task + file + expected proof), implement, test, commit, push

fallback:

* if NO plan is implementable without a maintainer decision, do NOT invent one — record the finding and stop

---

## Kof philosophy

intent: simplicity

law:

* Kof must be simpler than alternatives
* code expresses intention, not mechanism
* complexity belongs to platform
* domain belongs to domain abstractions
* no ceremony
* no translated-language idioms
* no generated-looking code
* no unnecessary infrastructure

rule:
1: use Kof intention primitives
2: use stdlib/platform capabilities
3: use domain types
4: avoid getters/setters/builders/service-repository-controller ceremony
5: compile syntax before use
6: unsupported target behavior is diagnosed
7: names describe responsibility
8: Kof is not Java/Kotlin/C#/another language
9: philosophy check precedes feature-gap classification
10: Kof contract precedes external behavior
11: language surface must remain extremely simple
12: features are library-first when Kof can express them

---

## Kof-first

flow:

* feature
* can Kof express it?
* yes → implement Kof library
* no → identify missing fundamental capability
* add smallest primitive
* implement feature in Kof

core:
supplies: mechanisms
libraries:
supply: policy-and-abstraction

for_core_change:

* why Kof cannot express it
* missing fundamental capability
* smallest core change
* reuse unlocked by primitive
* future Kof replacement

forbidden:

* feature-specific runtime APIs
* feature-specific syntax when library suffices
* if feature == X branches
* backend-specific surface hacks

acceptance:
normal:
- Kof source
- Kof library
- tests
- documentation
primitive-required:
- Kof source
- minimal primitive
- backend implementation
- Kof library
- tests
- documentation

direction:

* progressive self-hosting
* library by library
* no rewrite mandate

---

## External behavior

before_external_research:

* prove reproducer is valid Kof
* identify governing Kof contract
* search Kof idiom/abstraction
* measure real behavior

bug:
condition: implementation differs from Kof contract

gap:
condition: legitimate need lacks adequate Kof abstraction

external_sources:
provide:
- principles
- invariants
- trade-offs
- known bugs
do_not_provide:
- automatic syntax
- automatic API
- automatic semantics

contract_authority:

* DECISIONS.md
* normative docs
* golden/conformance tests
* parity matrix
* implementation

---

## Quality gate

intent: no-bug-ships

flow:

* reproduce
* fix root cause
* add proof test
* run relevant tests
* run suite
* hunt edges
* commit
* push

Q0:

* bug fixed at root cause
* test fails on old code

Q1:

* every feature has a test
* every bug fix has regression test
* test and implementation share commit
* refactor preserves existing proof

Q2:
command: mvn -o -pl kof-compiler -am compile -q
condition: green-before-push

Q3:
cover_when_applicable:
- numeric edge
- empty/null
- bounds
- expected error
- cross-target
- idempotency
- concurrency
- interop/resource failure
rule:
- happy path alone is insufficient

Q4:
hunt:
- extreme input
- cross-target divergence
- contract boundary
- neighboring regression
- uncovered behavior

Q5:

* no false green
* no weakened assertions
* no hidden skip
* own red must be fixed or reverted

Q6:

* suite is minimum, not ceiling

Q7:
forbidden:
- stub
- placeholder
- TODO/FIXME pretending completion
- empty implementation
- return-null facade
- return-zero facade
- swallowed unhandled branch
- not-implemented pretending support
unsupported:
- explicit diagnostic XXX00x
existing_stub:
- catalog in known-bugs.md or specification-gaps.md
- annotate code with gap
- plan through documentation state model

---

## Behavior freeze

constraint:

* zero regression
* additive compatibility
* refactors preserve semantics
* bugs align implementation with Kof contract
* cross-target parity
* frozen semantics require maintainer decision

frozen:

* operators
* precedence
* evaluation order
* null safety
* content ==
* String exceptions
* spawn/await
* List/Map/Set

contract_change:

* maintainer decision
* version bump
* documentation
* migration

---

## Platform

boundary:
core → base stdlib → platform → official packages → interop

rule:

* heavy domains belong to official packages
* stdlib stays essential and small
* register new namespace in stdlib ledger
* machine gate: scripts/check_stdlib_boundary.sh

interop:

* prefer existing external capability
* use FFI/interop where appropriate
* do not reimplement external engines
* graphics/game engine is Kof-owned per D-GRAPHICS-GAMING

targets:
JVM: heavy/interop-first
Native: systems/deploy
JS: web/edge
rule: never promise unsupported parity

diagnostics:

* every unsupported domain has a gap code
* every gap enters parity matrix
* no silent fallback

stability:

* package starts experimental
* promotion requires complete DoD
* 3 targets or diagnosed gaps
* E2E per target
* benchmark when applicable
* docs/training synchronized

security:

* audited crypto libraries
* secure defaults
* constant-time primitives
* versioned formats
* SECN00x/SECPQ diagnostics

determinism:

* correctness required
* determinism required where applicable
* property-based + golden proof for science/ML

non_goals:

* open macros
* type classes
* annotation foundation
* ownership/borrowing
* complete effect system
* Kali-in-Kof
* target-per-domain
* own SQL/Arrow/ML engine

---

## Code gate

before_code:

* read training/idioms/<area>.md
* read training/anti-patterns/
* compile uncertain syntax

idiom:
membership: setOf(...).contains(x)
equality: ==
strings: + / +=
data: record
nullability: T? + narrowing
concurrency: spawn / await
collections: listOf / mapOf / setOf
json: json.encode/decode
db: db.connect
http: http.get
loops: for (var x in xs)
condition: if-expression
branching: switch
functions: type-before-name or type-after-params
top-level: functions/classes only

forbidden_foreign_forms:

* fun
* func
* fn
* top-level val/var/let
* let
* const
* async fn
* Thread
* Executor
* Runnable
* Optional
* Result
* JavaBeans
* Service/Repository/Controller ceremony
* Java/Kotlin primary constructors
* foreign membership syntax
* foreign pattern syntax
* language-specific null operators not defined by Kof

rule:

* if syntax is uncertain: compile
* compiler result outranks memory

---

## Corpus

training/idioms:
purpose: canonical Kof forms

training/anti-patterns:
purpose: forbidden/foreign forms

training/anti-patterns/fake-idioms.md:
purpose: features that do not exist

training/anti-patterns/chained-or-membership.md:
purpose: repeated OR → membership

training/anti-patterns/java-like-code.md:
purpose: translated Java → Kof

learn:
purpose: tutorials

docs:
purpose: normative/stable technical documentation

rule:

* training/learn answer syntax questions
* compiler confirms syntax
* docs define current contracts
* corpus is long-term agent memory

corpus_update:
new_idiom → training/idioms/
new_antipattern → training/anti-patterns/
hallucinated_feature → fake-idioms.md

---

## Verification

compile:

* mvn -o -pl kof-compiler -am compile -q

focused:

* mvn test -o -pl kof-compiler -am -Dtest='<AreaTest>' -Dsurefire.failIfNoSpecifiedTests=false

full:

* mvn test -o -pl kof-compiler,kof-script,kof-c-compiler,kof-cli -am
  -Dsurefire.failIfNoSpecifiedTests=false
  -Dmaven.test.failure.ignore=true

reports:

* grep -rl "FAILURE" */target/surefire-reports/*.txt

rule:

* inspect reports per module
* environmental guard is not a Kof regression
* never claim green without executed proof

native:

* Linux toolchain required
* Windows Native validation may use WSL
* missing as/ld/qemu is an environment condition
* documented environmental skips remain explicit

javafx:
rule: missing-runtime message is never accepted as benign
action:
- expose underlying exception
- diagnose root cause
- fix
- test

---

## Commit and delivery

before_commit:

* Q0-Q7 satisfied
* compile green
* relevant tests green
* full suite executed
* post-rebase tree inspected
* agent-edited docs compressed to Kofmd
* DOING.md updated

commit:

* cohesive complete unit
* implementation + proof together
* DOING.md updated in same commit

push:

* scripts/sync-push.sh only
* verify remote 0/0

release:

* agents may push the active development branch (`lab`)
* agents never promote/merge a stage into the next (promotion is maintainer-gated until `14.3`)
* maintainer performs the release merge

---

## Small parts

rule:

* one cohesive unit per action
* commit completed units early
* edit large files incrementally
* avoid giant writes
* ≤500 lines/class target
* 500–599 tolerated debt
* ≥600 blocks CI
* split by responsibility, never numeric suffix

---

## Final self-check

ready:

* compiled
* tested
* full suite executed
* root cause fixed
* relevant edges covered
* cross-target checked
* no false green
* no stub
* no foreign idiom
* no unnecessary infrastructure
* Kof abstraction preferred
* test and change share commit
* DOING.md current — every claim carries `owner = <ipv4>:<port>` (`D-AGENT-IDENTITY-IPPORT`, gate `scripts/check_owner_identity.sh`)
* remote synchronized

if_any_false:
state: not-ready
next: fix-before-delivery

---

## Cheat sheet

Kof:

* intention
* simplicity
* compile-first
* library-first
* quality-first
* honest diagnostics
* zero regression

functions:

* String f(Int x) { ... }

data:

* record Point(Int x, Int y)

collections:

* listOf
* mapOf
* setOf

membership:

* setOf("A", "B").contains(x)

strings:

* a == b
* a + "!"

errors:

* throw "msg"
* catch (String e)

null:

* String?
* if (x != null)

concurrency:

* spawn
* await

loops:

* for (var x in xs)

top_level:

* functions
* classes

rule:

* if it looks like Java, reconsider
* if syntax is uncertain, compile
* if Kof can express it, write it in Kof
* if it cannot, add the smallest mechanism
* never ship without proof
