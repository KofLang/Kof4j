[English](AGENTS.md) | [Português](AGENTS.pt_BR.md)

# AGENTS.md — Writing Kof (guide for AI agents)

This is the **mandatory** guide for any AI agent (or human) who
writes Kof code in this repository. Read it before generating any `.kf`.

**Version:** 0.4.0-beta · Last update: 09/13/2026 (autonomous mode + STABILITY condition with refusal to re-trigger + **Quality gate: no bug ships**; active branch = `beta-0.4.0`)

> **PRIORITY No. 1: QUALITY.** Before any feature, read the
> **Quality gate — "no bug ships"** (§ below), **universal for
> ALL branches**. The order is **reproduce → fix the root cause →
> prove with a test → run the suite → only then commit**. The test **proves**;
> it never replaces the fix. Delivery without proof is not delivery: it is a bug
> postponed. The rush to deliver is the biggest risk in the repo.

---

## Autonomous mode (definition — the operating standard of this session)

> **Entering autonomous mode = working without interrupting the human, for days on
> end, until the next human interference.** The human is not available for
> questions; the repo is the only source of truth. Everything you need is already
> in the documents — if it isn't, it's because it needs to be written (and you
> write it).

**The loop (never stop in the middle):**

```
1. READ the state (DOING.md, docs/status.md, git log, suite) — never ask.
2. CHOOSE the next task: highest value, without an owner `IN PROGRESS`, in your lane.
3. CLAIM it in DOING.md (same commit as the first step).
4. BREAK it into scopes achievable in one session (see "small parts" below).
5. EXECUTE one scope → test → commit → update DOING.md + todowrite.
6. GO BACK to step 1. Don't announce "the end"; only stop on a stop condition.
```

**The loop within ONE turn (rule that prevents "stopping and summarizing"):**

> **The agent does not re-trigger itself.** When a turn ends, execution
> stops. So: **ending a turn with a summary is the only unforgivable autonomy
> failure** — it's what turned "days on end" into "one turn".

1. **Chain tool calls within the turn** until: (a) a stop condition,
   (b) the context nearly runs out, or (c) the turn runs out of new work
   (green suite + DOING.md without an unowned item in your lane).
2. **It is forbidden to end the turn with a summary/status for the human**
   ("pushed", "session summary", "what's missing now is..."). If the turn
   is going to end, the LAST thing written in the turn is:
   - final commit of the current state,
   - `DOING.md` updated with the line **"NEXT STEP: <exact task +
     file + expected proof>"** (the re-trigger reads this and continues),
   - `todowrite` mirroring this.
   After that, silence — or the next tool call.
3. **Every commit in the turn requires updating the line in `DOING.md` in the
   SAME commit** (the multi-agent section rule applies doubly here: without
   DOING.md updated, the next agent/session doesn't know what already exists).
4. **`todowrite` at every stage change** — exactly one `in_progress`;
   an item only moves to `completed` with proof (green test/suite).
5. **Re-trigger is from the human or cron** (the agent doesn't wake itself).
    When entering autonomous mode, the agent **launches the cron** (see "Cron
    heartbeat" below). That's why item 2b is a contract: whoever returns —
    human or another instance — must be able to resume in ≤1 read of `DOING.md`,
    without asking.
6. **Re-trigger is NOT conversation.** When the human sends "continue", "go",
   "and now?" or any re-trigger: **do not reply with acknowledgment or
   status** ("Understood", "ok", "pushed", "I'll continue..."). The FIRST
   action of the turn is the tool call that reads the `NEXT STEP` and executes it. A turn
   that ends with a confirmation phrase without a tool call is the SAME failure as a
   turn that ends with a summary — the loop stopped and the human had to push
   it again.
7. **Unit in progress = turn in progress.** If the turn is going to end and
   there is a HALF-EXECUTED unit (edit applied without a test run,
   green test without commit, commit without `DOING.md`), **finish the unit before
   closing**: run the test, commit, update the `DOING.md` — in the same
   response, chaining the tool calls. "I stopped in the middle of an edit" is the loop
   dying at the most expensive point: the next agent inherits a dirty working tree
   without knowing the state. Rule of thumb: **after every tool call, the
   question is "is the unit committed? no → next tool call now"**,
   never "are we done with tool calls in this response?".

**Real failures that motivated these rules (09/05, three occurrences):**
(a) the agent made 5 correct commits (riscv64 fixes) and ended the turn with
a "session summary" instead of continuing the loop; `DOING.md` had no
update since the start of the work. (b) on the SAME day, after the human
said "continue", the agent replied "Understood. I'll proceed." — an entire
turn spent on a confirmation phrase, without a tool call, without work.
(c) still on the SAME day, with the loop running and a port (time.sleep) 2
edits away from the commit, the turn ENDED right after the last edit tool call —
without running the test, without committing, without updating the DOING.md; the human had
to push it again. Autonomy that ends in a summary, in "ok", or **in the
middle of a unit** is not autonomy — it's politeness or carelessness.

**What to do instead of asking:**

| Question | Source of the answer (in this order) |
|---|---|
| "Is there an owner for this?" | `DOING.md` |
| "What's the real syntax/idiom?" | `training/`, `learn/`, **compile and confirm** |
| "What already works?" | suite + E2E running (the proof, not memory) |
| "What's the next priority?" | `docs/status.md`, `docs/backend-parity.md`, `docs/development/` (queue P0→P5: `roadmap-audit.md`/`roadmap.md`/`specification-gaps.md` + `known-bugs.md`), `planning-*` |
| "Is this a design decision?" | **It's NOT yours** — record the gap/plan and move on (rule 6) |

**Scope achievable in one session** = a cohesive unit with proof at the end
(green test, qemu running, suite passing). If the whole task doesn't fit,
do the first step, commit, and the next agent/session continues. Never
leave large uncommitted work — that's how a session is lost.

**Stop conditions (the ONLY ones that justify stopping and calling the human):**

1. **  at stake** — change of contract/operator/order of
   evaluation (rule 6): becomes a gap/plan in `planning-*`, never an edit.
2. **Unavoidable lane collision** — the only path touches a file `IN PROGRESS`
   of another agent and it can't be postponed: stop, record it in the DOING.md, wait.
3. **Gate broken without cause in your change** — red suite that you didn't
   introduce and can't diagnose: record it in `docs/bugs-and-gaps/known-bugs.md`
   with reproductions, don't "fix" the test to pass.
4. **Requirement genuinely absent from the corpus** — neither `training/`, nor
   `learn/`, nor the compiler answer: write the question in the DOING.md on the
   item's line and move on to another task (don't stall the loop).
5. **Stable development (STABILITY condition — for the re-trigger)**
   — see the next section: the loop only has work if there is real work.

### Stability: when to stop the loop and how to evaluate each re-trigger (mandatory)

> **Development is stable when the three conditions below hold at the
> same time:**
>
> 1. **All bugs resolved** — `docs/bugs-and-gaps/known-bugs.md` with no open
>    item (everything `FIXED`/`CLOSED` with proof).
> 2. **All of `docs/development/` concluded** — no doc with pending
>    development (the concluded ones were already moved to `docs/`, the not-started
>    ones moved to `docs/development/future/`).
> 3. **All of `docs/development/future/` developed** — the future plans
>    implemented and validated (or promoted/promoted to `docs/` according to the
>    three-states rule).
>
> Under these three conditions, **the full green suite + the 5/5 conformance
> matrix are the proof of stability** — it is a STATE to verify, not an
> opinion to declare.
>
> **On every message of autonomous mode (heartbeat/re-trigger), the agent MUST
> evaluate before acting:**
>
> - **Did a regression appear** (red suite, new test failing, bug reintroduced,
>   new document added to `docs/development/` with pending work)?
>   → **take on the task**: claim it in the DOING.md and run the normal loop.
> - **Nothing appeared** (the three stability conditions still hold and
>   the suite is green)? → **REFUSE the autonomous mode request**: don't invent
>   work, don't edit "to look busy", don't run the suite again just to
>   consume a turn. The honest refusal IS the correct answer: record it in the DOING.md
>   ("stable — re-trigger refused on <date>, nothing pending"), **stop the cron**
>   (`scripts/auto-loop.sh stop`) and report the stability to the maintainer.
>
> Rejecting the unbroken re-trigger is **as mandatory as executing the
> legitimate re-trigger**: the heartbeat exists to cover the interval between
> real work, not to generate artificial work. A loop that finds no
> work when the work is done is functioning correctly by refusing.

**What autonomous mode does NOT relax (nothing):** all the rules in this file
still hold — zero regression, additive backward compatibility, ≤500
lines, R6 (never silent), suite as merge gate, commit per unit.
Autonomy changes **who decides the order**, never **what is acceptable**.

**Signal for the human:** `todowrite` is the window of this conversation (update it
at every stage); `DOING.md` is the memory between sessions. If the human returns and reads
these two, they know exactly where you are and why.

### Cron heartbeat (mandatory when entering autonomous mode)

> The agent **doesn't wake itself**. For the loop to survive the end of each
> turn, upon **entering autonomous mode** the agent launches the session cron:

```bash
scripts/auto-loop.sh start            # last session, re-trigger every 30 min
scripts/auto-loop.sh status           # confirm that it's active
```

- The cron calls `opencode run --session <id> --dir <repo> --attach <server>
  --auto "<prompt>"` at every interval (default 30 min), with the re-trigger
  prompt: *"analyze the documents, check the gaps, identify what's
  missing in our plans, draw up an implementation todo and continue
  development"*.
- **`--attach` is MANDATORY — the heartbeat injects into the OPEN SESSION, never
  spawns a concurrent agent.** Without `--attach`, `opencode run --session` creates
  a **new headless process** that only shares the history: you see "another
  session" running in parallel, two agents competing for the same session (the
  00:00 tick of 09/06 left a `run` alive for 20 min disputing with the TUI).
  The TUI server of the open session listens on **`http://127.0.0.1:9093`**
  (port of the autonomous mode session — check `ss -tlnp | grep opencode` and the
  live session; override with `OPENCODE_SERVER_URL`). The `tick`
  does a health-check on the port before firing: server down → tick
  skipped and logged (no point injecting into a session that doesn't exist).
- `flock` in the `tick` prevents overlapping runs: if the previous turn is still
  active, the tick is skipped and logged (`~/.local/state/kof-auto-loop/loop.log`).
- **When leaving autonomous mode** (human returns, stop condition, or
  work concluded): `scripts/auto-loop.sh stop`. Leaving the cron running
  after the end is noise — the heartbeat exists only while the loop lives.
- If the cron is already ACTIVE (`status`), don't launch another — the current session is the
  continuation of the heartbeat.
- The re-trigger arrives as a normal turn: rule 6 applies (reply with a tool
  call, not with "ok") and so does the `NEXT STEP` contract in the `DOING.md`.
- **Issue watchers (09/12, multi-issue 09/13):** `scripts/issue-watcher.sh start <issue|all> <min>
  <session>` watches new comments on an issue (or on **all open ones** with
  `all` — snapshot `N=id` per issue) every N minutes and injects a turn into the
  live session (same mandatory `--attach` as the heartbeat; `seen` only advances
  after a successful injection; `server=` recorded in the state fixes the port for the cron
  tick). In use: **all every 5min → session `ses_f69c2cb03ffe2zDYCqW7fesphi`
  (port 9094)** — the `all` tick **injects at EVERY tick** (with or without a new
  comment) with a **full sweep** prompt: list ALL open ones, read
  body+comments, reply technically, **triage** (fix what belongs to the
  lane / record a gap-plan rule 6 / declare non-proceeding), **fix and
  close with `gh issue close` + commit** (only with proof; another lane's front =
  ask the owner for review, never touch). Interacting with an issue that
  impacts IN-PROGRESS work is part of the loop, not a distraction.
- **Two sessions, two crons (09/13, maintainer's request):** 9093 =
  `ses_f69e2a3f7ffe9J10aWcHEUOfW8` (heartbeat auto-loop, `*/5`) and 9094 =
  `ses_f69c2cb03ffe2zDYCqW7fesphi` (watcher all, `*/5`). **Never cross them:**
  each tick injects ONLY into its session (`--attach` + recorded/resolved port).
  The heartbeat of 9093 had been stopped; it was **reactivated** (`auto-loop.sh start
  ses_f69e2a3f7ffe9J10aWcHEUOfW8 5`, dry-run proves attach 9093).


---

## Source of truth and collaboration model (mandatory)

The Kof ecosystem (Koflang, Kof4J, Kof Native, Kof Editor) is **open source
(GPLv3)** and **centralized** around the official maintainer, **Mel Santos**
([@aminadojava](https://pt.linkedin.com/in/aminadojava)) — the **only source of
truth** and the one who holds control of the low-level engineering (compiler,
injection of Assembly directly into the JDK, CISC x86 architecture).

Development is **actively driven with publicly documented AI agents**
— but **Kof was not made by AI**. AI is a **tool**
under the maintainer's reins: it accelerates and optimizes, but it does not replace
conceptual engineering nor decide architecture/direction. Practical consequences for the agent:

1. **Technical skepticism.** AI is treated with skepticism — never with faith. Automation
   without criteria masks lack of quality and immediacy. The "root
   programming" applies: rigor in compilation, practical experience, robust code.
2. **Compile before delivering.** Hallucination is forbidden. "Thinking" it compiles
   does not compile. The verification loop (§ below) is non-negotiable.
3. **Surgical transparency of errors.** Errors go to `docs/bugs-and-gaps/known-bugs.md`
   with **root cause** + **minimal repro**, including regressions that the maintainer
   introduced. Never "document around" the bug.
4. **Technical discussion before code.** When the doubt is conceptual (semantics,
   floating-point overflow, ABI), the contribution is through **technical debate** —
   commented design proposals/documents — not a disorderly PR that changes
    .
5. **Shielding against pollution.** Never mix the Kof language with terms
   foreign to the domain (games, etc.) in docs/code. Disclaimers and nomenclature
   are law; violated it, revert.
6. **Every PR comes with a related issue.** A "loose" PR doesn't get in.
   Every proposed change references an open issue that justifies it —
   traceability is law, not a preference.
7. **Delivery by an agent requires proof of quality (§"Quality gate").**
   Before opening a PR/committing/pushing, the agent answers the **pre-push
   checklist (Q1–Q6)**. An agent commit without a test in the same commit is
   **rejected in review** — the maintainer is not the first to discover the
   bug. If the agent couldn't run a gate (missing toolchain/qemu),
   **state that explicitly** in the commit/PR; never fake green.
8. **No silent regression.** The agent who leaves the branch non-compilable
   or the suite red (outside the documented environmental errors) is violating
   the gate: fix it in the same unit or revert. `git bisect`-hostile is the
   worst legacy an agent can leave.
7. **Git identity & agent worker (09/12, updated 09/16 maintainer directive).**
   The GitHub App `kof-agent-worker` (App ID `4960796`, configured via `scripts/gh-as-agent.sh`
   and `~/.config/kof/agent-app.env`) is the dedicated identity for issues, PRs, and commits
   where installed.
   - **Commit permission & identity:** the app has commit permissions and its own Git/GitHub
     identity.
   - **Fallback rule:** if authentication, push, or commit fails when using the bot's identity
     (e.g. app not yet installed on a specific target repository or integration error), the agent
     **must accept and fall back to the maintainer's default pattern**:
     `mel <amelissariver@gmail.com>` (GitHub account `melmonfre`).
   - No email tricks or synthetic trailers (`Co-authored-by` is forbidden as it pollutes the repo log).
   - The agent uses the effective environment identity as configured.


> In summary: AI runs **under the strict rules of real computing** —
> surgical documentation, zero hallucination, without the market hype.

---

## Multi-agent coordination — DOING.md (mandatory)

Several agents work in parallel in this repo. **Before starting any
feature/gap, read `DOING.md`:**

- If the item already has **an owner + state `IN PROGRESS`**, don't touch it — choose another.
- When starting an item, **claim it in the `DOING.md` in the same commit** (owner,
  branch, files you're going to touch).
- **On every commit, update your line** in the `DOING.md` (what you did, what's left).
- When concluding, mark `DONE` with date + SHA + test that proves it, and close the gap
  in `docs/status.md`/`docs/backend-parity.md`.
- Abandoned it? Go back to `OPEN` with a note of what works and what's left.
- **Owner vanished = dead task; reassign it.** If an item is `IN PROGRESS` with an owner
  but **there is no new commit in their lane** (the line hasn't moved since the
  claim, the owner doesn't appear in `git log`, or the cited branch/file doesn't
  exist), assume the agent **died mid-turn** (crash, context
  exhausted, session closed without closing the unit). The item has no real owner:
  any agent can **claim it again** (change the owner in the `DOING.md`, in the
  same commit as the first step), reusing what the dead one left (working
  tree/branch) and continuing. Before touching it, **verify the real state in the code**
  (what compiles, what the suite proves — never the `DOING.md`'s memory) and note in the
  claim what the previous owner left. Don't wait for the ghost to return nor
  ask permission — an orphan `IN PROGRESS` is a disguised `OPEN`, and an orphan gap is
  lost work.

Golden rule: **never two agents on the same gap or on the same giant file**
(`NativeRuntime.java`, `CompilerDriver.java`) at the same time. If it's
unavoidable, coordinate in the chat first.

**Mandatory synchronization (pull before, push after):** before **every
commit** — `git fetch` + `git pull --rebase` (with a dirty working tree, use
`git stash push` before and `git stash pop` after, or `--autostash`) and
**check whether there's a conflict** (stalled rebase / `<<<<<<<`): a conflict is resolved
right away, never committed on top. After the commit, **`git push`** — the DOING.md
only coordinates those who *see* the remote; an unclaimed local commit is a ghost task
for the other agents. After the pull, **re-read the DOING.md**: what was your
"next step" may have been done or claimed by another agent in the
interval.

### Lesson learned (09/04) — ALWAYS work in small parts

> **Never try to write/produce a large artifact all at once.** The
> refactoring plan `docs/architecture/PLAN-SOLID-500.md` (CLOSED 09/13; 120 classes, 8 phases) was
> lost once because the agent tried to write the entire document in a single
> `write`. The lesson:

- **One step at a time.** Each action (write/edit/commit) resolves ONE cohesive
  and small unit. If the response needs >1 large action, split it into several
  responses with a commit between them.
- **Commit early and always.** Every concluded unit becomes an isolated commit
  (`git add -A && git commit`), even if it "looks incomplete" — the next
  step continues from where it stopped.
- **Large files are edited in pieces.** Read/edit a file of 17k
  lines little by little (never a `read` of 2000+ lines at once if you don't need it).
- **If the task seems bigger than the window**, create the skeleton/lean document
  first, commit, and fill it in incrementally.
- **The ≤500 lines/class rule exists exactly because** "doing everything at once"
  becomes code impossible to load/maintain. The agent is part of the system:
  acting small is following the very rule we apply to the code.
- **`check_500` gate ranges (maintainer's decision, 09/13):** ≤500 is the target;
  **500–599 is TOLERATED** (live debt — the gate warns, doesn't break CI; split
  remains the path); **≥600 is CRITICAL** (fails CI, refactor/split
  mandatory before merge). Debt already locked in the baseline never grows
  (approaching 600 = split now). When splitting, remove the line from the baseline with
  `./scripts/check_500.sh --update-baseline`.

This applies to code, docs, plans and tests: **small is sustainable.**

### Visible status — `todowrite` (mandatory, at every stage)

`DOING.md` is the **persistent** memory of the repo (survives across sessions and
agents). **`todowrite`** is the status **visible to the human in this session** —
a task list that the CLI renders in real time. The two are
**complementary**, never substitutes:

- **At every thinking stage between implementations**, update the `todowrite`:
  mark `completed` what finished, `in_progress` exactly **one** item
  (what you're attacking now), `pending` what's left.
- Don't wait for the end of the turn nor the commit: the person following along needs to see
  the progress **while** you work (e.g., when switching modules — JSON →
  http → spawn — move the previous item to `completed` and open the next).
- An item only moves to `completed` when the proof exists (green test, qemu
  running, suite passing) — never by intention.
- If a stage unlocks new work that wasn't planned, **add** it
  to the `todowrite` right away.
- At the end of the session, the `DOING.md` remains the source of truth for the
  **next** agent; the `todowrite` is just the window of this conversation.

---

## Documentation organization (mandatory — 09/09)

The documentation structure has **three states**, and the classification reflects the
state of the **SOFTWARE**, not of the text:

| Folder | Content | Meaning |
|---|---|---|
| `docs/` | consolidated and valid documentation | **only** what has already been implemented, validated or decided |
| `docs/development/` | work currently in development | **only** items with implementation, validation, tests or integration **pending** |
| `docs/development/future/` | planned for later | ideas/features **not** in current development |
| `docs/development/DECISIONS.md` | **record of the maintainer's decisions** (the `decision-pending/` folder was EXTINCT 09/13 — the 6 plans became this single doc) | a decision made in the chat **lives here** (date + option + evidence), never only in the chat; a decided item becomes a queue in `roadmap.md` §23/DOING in the same commit — never attack a front without a decision locked here (rule 6) |
| `docs/bugs-and-gaps/` | **living records** of bugs, spec gaps and conformance/parity matrices | queue by target (rule 3 of the freeze); it's not a "plan" — it updates in the SAME commit that closes the item |
| `docs/audits/` | **audits** — snapshot of state (planned × accomplished) and dated comparison records | they point out work, never are a queue: what an audit marks pending has its own home (bug → `bugs-and-gaps/`, code → `development/`, decision → `decision-pending/`); when closing what was pointed out, update the audit's line in the SAME commit |

> **Clarity refactor (09/13, maintainer's decision):** bugs/gaps/matrices
> (`known-bugs.md`, `conformance-matrix.md`, `ecosystem-coverage.md`,
> `KOFUI-AUDIT.md`, `specification-gaps.md`) **are not a development
> backlog** — they live in `docs/bugs-and-gaps/`. Documents 100% stopped
> by decision **no longer have their own folder**: the 6 that lived in
> `decision-pending/` (`PLATFORM-PLAN.md`, `APPLICATION_MODEL.md`,
> `security-plan.md`, `plan-platform-completion.md`,
> `plan-spring-independence.md`, `planning-stdlib-time-design.md`) were
> inventoried, audited against the code and **ratified 09/13** in a single
> doc — `docs/development/DECISIONS.md` (the folder was deleted). Audits
> (`roadmap-audit.md`,
> `complexity-audit.md`, `PLANNING-FUTURE-AUDIT.md`,
> `planning-future-reconcile.md`) live in `docs/audits/` — they are neither a
> development queue nor a gap record, they are snapshots of state.
> `docs/development/` keeps **only**
> work that moves (plans with code in progress and refactors). A **docs** lane agent doesn't touch bug/gap (they belong to
> another lane) — it only keeps those records synchronized with the code.

> **`docs/development/` is NOT a dead archive, history nor a documentation
> dump.** The presence of a document there explicitly means:
> *"there is pending technical work for this item."*

### Fundamental rule — audit before starting

**Before starting any new implementation**, the agent MUST comb through
`docs/development/` and compare each document with the REAL state of the code,
tests, build and commits. For each item:

1. **Already implemented and validated** → update the doc if necessary, **move
   it to `docs/`**, remove old references that indicate development.
2. **Partially implemented** → keep it in `docs/development/`, identify
   exactly what's missing, **implement what's missing**, run the tests;
   only after conclusion move it to `docs/`.
3. **Only planned** (without implementation in progress) → move it to
   `docs/development/future/`.
 4. **Obsolete, duplicated or contradicting the current state** → fix or
    consolidate; never keep false/outdated documentation in
    `docs/development/`.

### Priority rule — loose `.md` first (09/13, maintainer's guidance)

> **Implement FIRST the loose `.md` in `docs/development/`** — they are the
> work **without impediment**: ratified plan, defined scope, nothing stopped.
> Documents in folders inside `docs/development/` (`refactoring/`,
> `future/`) **have an impediment** and are not a queue while the
> impediment exists.

- **A decision in the chat becomes DECISIONS.md in the same commit:** when the maintainer
  decides something in the chat, the agent **locks the answer in
  `docs/development/DECISIONS.md`** (date + option + evidence) **and opens the
  queue** in `roadmap.md` §23/DOING — deciding without recording = invisible
  decision; recording without opening the queue = dead decision. A front without a line in
  DECISIONS.md **is not attacked** (rule 6).
- **Task selection order:** (1) loose `.md` in `development/` with
  pending implementation and without an `IN PROGRESS` owner; (2) newly opened queue from
  `DECISIONS.md`; (3) only then the rest of the queue.
- **`future/`** remains untouchable without explicit promotion (the three-states
  rule); an item in `future/` **does not** become a priority just because it's
  "planned".

### Conclusion rule

**NOTHING that is concluded may remain in `docs/development/`.** The mandatory
order when concluding a task is:

```
implement → test → validate → update documentation → move from development/ to docs/
```

Moving the document **is neither optional nor a secondary administrative
task** — it is part of the definition of "concluded".

### Resumption rule

When resuming work in the repository:

1. Read `DOING.md`, `AGENTS.md` and `docs/status.md`.
2. Comb through `docs/development/`.
3. For each document, verify the real state of the implementation in the code and
   in the tests.
4. Fix the classification of the documents.
5. **Finish first the work that is already in development** before
   starting new features.
6. After each conclusion, immediately move the documentation to `docs/`.
7. Only after exhausting the work in development, select new
   items.
8. Items in `future/` **are not** current work without an explicit decision to
   promote them to development.

### Prohibition of documentary cascade

- Don't create planning, audit, roadmap or TODO documents **only
  to avoid implementing** a task already started.
- Don't turn a task in development into another planning task.
- If the code has already started being implemented, the goal is to **finish the
  implementation**, test and consolidate the documentation.

### Objective criterion and priority

```
development/ + implementation concluded  = doc MISCLASSIFIED (move to docs/)
development/ + implementation pending    = correct
future/      + implementation not started = correct
docs/        + functionality implemented/validated = correct
```

Agent priority order: (1) conclude what is already in
`docs/development/`; (2) validate and consolidate; (3) move the concluded doc to
`docs/`; (4) only then choose new work; (5) `future/` only enters
execution with no pending current work or by explicit decision of the maintainer.

---

## Primary guideline

> **Kof must be simpler than any alternative.**

The purpose of the language is to reduce verbosity. If the code you're
generating in Kof looks like translated Java, C# or Go, **it is wrong** — even
if it compiles. The litmus test, before emitting any code:

> *"Would a human write this in Kof, or did I translate another language?"*
> *"If a Kof reviewer saw this in a PR, would they be embarrassed?"*

If the answer is "I translated" or "yes", rewrite it.

**Canonical case (never repeats):**

```kof
// ❌ NEVER — 50 || in a row is Java disguised as Kof
Bool isQuery(String op) {
    return op == "GetSession" || op == "GetAccess" || op == "GetDashboard"
        || op == "GetToday" || op == "GetTodayBoard" || op == "ListIntakes"
        || op == "GetIntake" || op == "ListCompanies" || op == "GetCompany"
        || op == "ListCompanyMembers"
}
```

```kof
// ✅ IDIOMATIC — the language has the feature; use it
Bool isQuery(String op) {
    val known = setOf(
        "GetSession", "GetAccess", "GetDashboard", "GetToday",
        "GetTodayBoard", "ListIntakes", "GetIntake", "ListCompanies",
        "GetCompany", "ListCompanyMembers"
    )
    return known.contains(op)
}
```

> *"Why did Kof let you write 50 `||`?"* — the answer is never
> "learn to write better". It is "use the language's abstraction".

---

## Iron rules (negotiable with the compiler, not with style)

1. **Intention, not mechanism.** `spawn` (not `Thread`), `setOf().contains()`
   (not `||`), `==` (not `.equals()`), `json.encode` (not a manual parser).
2. **Complexity belongs to the platform.** JSON, DB, HTTP, cache, crypto,
   UI already exist in the stdlib (`kof.*`). Reimplementing = anti-pattern.
3. **Represent the domain, not the accidental implementation.** `List<T>`/`Map<K,V>`/
   `Set<T>`, not a manual linked-list.
4. **Zero ceremony.** No getters/setters, no builders, no utility classes,
   no Service/Repository/Controller layers.
5. **Never hallucinate syntax.** If it's not in `training/`, **compile and confirm**
   before using it. Syntax that doesn't compile is worse than verbose syntax.
6. **Honest multi-target.** Code that only runs on one target needs a
   clear diagnostic (gap `XXX00x`), never a silent fallback.
7. **A name describes responsibility, never position.** `JsRuntimeUiMathDouble`,
   `RuntimeStringsWords` — yes; `...Math2`, `...V2`, `...New`, `...Bak`,
   `stdmath2` — no. A numeric suffix is co-processor garbage (it exists only to
   not collide with a name nobody understood). When splitting due to the ≤500 gate, the
   new file is named for what it **contains** (the responsibility that
   left), not for how many siblings already exist. Readability comes before
   any typing economy.

---

## Quality gate — "no bug ships" (mandatory, 09/13 — **UNIVERSAL**)

> **The project's priority No. 1 is QUALITY, not delivery volume.** A commit
> that adds a feature without proof is worse than a commit that doesn't exist: it
> transfers the cost of the bug to the next session and to the maintainer.
>
> **This rule is universal: it applies to ALL branches** (`beta-0.4.0`,
> `wip-*`, `fix/*`, `issue-lane`, `docs/*`, feature branches, forks), **to
> every agent, every lane and every unit** — not only the release branch. A
> push that breaks the build on ANY branch is a violation of the gate. It is not
> negotiable with "the test was already passing before", "it's just a WIP" or "another agent
> sees it later".

### Q0. The bug is FIXED; the test only PROVES

> **Fixing the bug is the work. The test is the proof that it died — never
> a substitute for the fix.** It is forbidden to deliver "the test that reproduces the
> bug" and leave the code broken; it is also forbidden to "document around" or
> lower the assertion so the test passes. The order is always:
>
> **reproduce (minimal repro) → fix the root cause → prove with the test
> that would fail before → run the full suite.**

- **Bug fix delivers the fixed code + the regression test in the same commit.**
  Missing either of the two, the commit doesn't exist.
- **Fix the root cause, not the symptom.** An `if` that hides the exception, a
  `try/catch` that swallows, or a default value that masks the error **is not a fix** —
  it's a postponed bug. If the fix requires a change of contract/operator/order of
  evaluation, it's **rule 6**: it becomes a plan, never a silent edit.
- **A broken build is the most serious bug.** If your commit leaves the branch
  non-compilable (e.g., `7f174a6f`, `usesPow` not declared), the fix is
  **priority zero** — fix it in the same unit and push, don't wait for the next.

### Q1. Every code change comes with a test that PROVES it

- **New feature → new test.** No exception. "I implemented X" without a test that
  executes X is invalid delivery (the `pow` of 09/13 went up without a test and broke the
  release build — it doesn't repeat).
- **Bug fix → regression test** that would fail on the old code and passes on the
  new one. The test is the proof that the bug died; without it, the bug comes back.
- **Refactor → same suite + golden E2E per target** (rule 3 of the Freeze).
- **The test goes in the SAME commit as the change.** Test later = test that never
  comes. If the commit doesn't have the proof, the commit doesn't exist.

### Q2. Compilation proof BEFORE any push

```bash
mvn -o -pl kof-compiler -am compile -q     # failure here = DO NOT PUSH
```

The `7f174a6f` case (pow with `usesPow` not declared) left the release branch
**non-compilable** for all agents. **Hard rule: an agent who doesn't run the
module's `compile` before pushing is breaking the repo.** If the full gate
wasn't run, the commit/message says so explicitly — never fakes green.

### Q3. Beyond the happy path — the minimum scenario matrix

Every new test covers, at minimum, **the happy path + the relevant edges**.
The agent chooses the ones that apply and **records in the commit** what it covered:

| Scenario | Example |
|---|---|
| **Numeric edge** | `0`, negative, overflow, `NaN`/`Infinity`, `-0.0`, negative/fractional exponent |
| **Empty/null** | empty collection, String `""`, `null`/`Nullable`, missing key |
| **Limit/index** | first/last element, out-of-range, one-past-the-end |
| **Expected error** | invalid input → diagnostic/gap `XXX00x` (never silence, R6) |
| **Cross-target** | JVM × Native × Script × JS with the SAME output (or diagnosed gap) |
| **Idempotency/repetition** | running 2× gives the same result; state doesn't leak |
| **Concurrency** | `spawn`/`await`, race, cancellation, isolation between workers |
| **Interop/resource limit** | nonexistent file, network down, missing lib, memory |

- **Forbidden to deliver only the happy path** (self-check 7). If the case only has the
  testable happy path, **say why in the commit** (e.g., "only the deterministic
  is observable; the rest is environment").
- **Golden = real measurement, never memory.** The expected value comes from the executed
  JVM oracle (or isolated C harness), never from "I think it gives this".
- **`assertEquals` with a message** that identifies the case and the target — a red
  needs to be diagnosable without re-running.

### Q4. Hunt the bug BEFORE pushing (hunting posture, not delivery)

Before every commit, the agent **tries to break its own change**:

1. **Extreme cases:** what happens with empty/null/negative/huge input?
2. **Cross-target:** do the 3+ targets agree? Where they diverge, is it a gap or a bug?
3. **Contract boundary:** does the change touch operator/precedence/order of
   evaluation/null-safety/`==`/exceptions/`spawn`/collections? Then it's **rule 6** —
   it becomes a plan, not an edit.
4. **Neighboring regression:** run the full suite, not just the new class.
5. **What does the test NOT cover?** Write it — that's exactly where the bug lives.

> A bug found by an agent before the push costs minutes. The same bug shipping
> costs an entire session of another agent + the maintainer's trust. **Finding
> the bug is part of the work, not an optional phase.**

### Q5. No "false green"

- A test that passes by accident (weak assert, `success=true` without executing
  output, error message accepted as expected output) is a **disguised bug**.
  Forbidden to "fix" a test by relaxing the assertion (JavaFX, §149 — precedents).
- Skip is **honest and explicit** (`assumeTrue` for a missing toolchain), never
  to hide a failure.
- If the suite turns red because of your change, **the commit doesn't go in** —
  neither "with a note", nor "I'll come back later". Fix it or revert it.

### Q6. The suite is the floor, not the ceiling

Passing the suite is the **minimum**. The acceptance question is: *"what scenario breaks
this and I haven't tested yet?"*. While there's an answer, the unit is not
ready.

### Q7. No stubs — the implementation is COMPLETE or it doesn't ship (09/13)

> **"It works enough for the test to pass" is not delivery.** A stub,
> placeholder, facade `return null`/`return 0`, empty body, `throw
> "not implemented"`, `TODO`/`FIXME`, a `default` branch that swallows an unhandled
> case, or any path that **pretends** to do the work is a **pre-installed
> bug** — it passes today's test and fails tomorrow's user.
> **It is forbidden to commit a stub.** The unit delivers the **complete
> implementation** of the declared scope, with the Q3 matrix covered.

- **Complete implementation = the expected behavior, entire.** If the scope
  is "support for `++` on long", deliver the 4 targets, prefix/postfix, numeric
  edge and array/field — not "only the test case". A smaller scope is acceptable
  **if declared**; a smaller scope disguised as complete, never.
- **A stub is not "incremental work"** — incremental work is delivering an
  **entire step** (a complete capability), committing it and moving on. Leaving
  half of a capability in the code pretending to be complete is what the rule
  forbids. The distinction: *complete vertical cut* (ok) × *facade of a facade*
  (forbidden).
- **Honest gap ≠ stub.** An **unsupported** path must fail with a
  diagnostic `XXX00x` (R6, rule 6 of the freeze), never return a false value
  silently. "Unsupported with a diagnostic" is valid delivery;
  "unsupported pretending it's supported" is a stub.
- **Every EXISTING stub is catalogued debt.** Finding a stub/placeholder
  in the software (code, stdlib, backend, docs) **obliges** you to:
  1. **catalogue it as an implementation gap** in
     `docs/bugs-and-gaps/known-bugs.md` (bug) or
     `docs/bugs-and-gaps/specification-gaps.md` (spec gap), with
     **location** (`file:line`), **what's missing** and **minimal repro**;
  2. **annotate it at the code point** with the gap code (`XXX00x`/`§NNN`),
     so the next agent sees it without archaeology;
  3. **plan it** in the queue (three-states rule) so that it is
     **formally developed in the correct way** — not fixed in a rush
     nor hidden behind a weak test.
  A stub found and not catalogued = **agent omission**, as serious as the
  stub itself. Cataloguing doesn't close the item: it only closes with a complete
  implementation + proof (Q0–Q6).
- **Acceptance:** the final question is not "does the test pass?", it's **"what here is still
  a facade?"**. While there's an answer, the unit is not ready.

---

## Behavior freeze (mandatory)

> **The expected behavior is law.** "Expected behavior" = what the corpus
> (`training/`, `learn/`, `docs/`) documents and the tests (golden + E2E + full
> suite) prove. **No agent may break behavior that already works.**

1. **Zero regression.** No commit may make an existing test start to
   fail. The full suite (`mvn test`, today **2211** across the 4 modules — see
   §"Verification loop" for the command with the failure.ignore flag) is a **merge gate** —
   a change that doesn't keep everything green doesn't get in. Single exception: a **deliberate**
   contract change, with a version bump + updated docs + migration.
2. **Mandatory backward compatibility.** Every new feature/API is **additive**:
   Kof code that compiles and runs today keeps compiling and running. A change of
   existing semantics is never silent — only with a bump + doc + migration.
3. **Refactor preserves semantics.** The refactor for the **≤500 lines/
   class** rule (and any other refactor) touches **structure**, never
   **behavior**. Proof: same suite + golden E2E per target. If the refactor
   changes observable output, it's a **refactor bug** — fix it or revert it.
4. **Bug = align with the expected, never the opposite.** Everything in
   `docs/bugs-and-gaps/known-bugs.md` is a deviation from the expected behavior and **must be
   fixed in the code** to reach the documented behavior. Forbidden to
   "document around the bug" (change the corpus to accept the wrong
   behavior as if it were right). If the documented behavior is wrong,
   it's a design decision → version bump + discussion, never a silent fix.
5. **Cross-target parity.** JVM/Native/JS diverging on the same program is a parity
   bug. The expected behavior holds on the 3 targets, or a diagnosed gap `XXX00x`
   — never silent divergence.
6. **  (0.2.6-beta).** Operators, precedence, order of
   evaluation, null-safety, content `==`, exceptions as String,
   `spawn`/`await`, `List/Map/Set` collections are **frozen**. A proposal to
   change becomes a gap/plan in `planning-*`, never a direct edit of the current
   semantics.

---

## Platform invariants (universal vision — `docs/development/future/PLAN-UNIVERSAL-PLATFORM.md`)

These rules **always** apply, even when there's no new domain code
at stake. They are the anti-"god language" mechanism:

1. **Boundary core → base stdlib → platform → official packages → interop**
   (R1). Heavy domain (`ml`, `bio`, `hpc`, `infra-<cloud>`) goes to an
   **official package**, never to the base stdlib. Only what is
   "essential to the platform and small" enters the stdlib.
2. **Interop-first** (R9). For any capability, the first question is
   "does it already exist outside and is it better?" → FFI/interop (`kof.process`, `.so`, JVM,
   GraalJS). Never reimplement Arrow/Parquet/BLAS/LAPACK/CUDA/NumPy/
   aligners/ML frameworks.
3. **Honest scope per target** (R7): heavy capabilities arrive **JVM-first**
   (interop), **Native** for systems/deploy, **JS** only web/edge. Never
   promise JS parity for heavy domains.
4. **Never silent per domain** (R6): every domain gap has a code
   (`INFRA00x`, `DATA00x`, `SCI00x`, `BIO00x`, `SECPQ`, ...) + an entry in the
   parity matrix. Never a silent stub, never a weak fallback.
5. **Stability tiers** (R5): a namespace/package is `stable` or
   `experimental`. The official packages layer is born `experimental` and only
   promotes to `stable` with a complete DoD (3 targets or diagnosed gap, E2E
   per target, benchmark when plausible, docs+training synchronized).
6. **Small and stable core** (R12): no future plan item is an **action**
   on the current work. New fronts (infra/data/sci/bio, legacy migration
   platform) do **not** open before the SYSTEMS stage (parity gaps,
   GC mark-sweep, package manager) closes.
7. **Security: defense first** (R11). Crypto never homemade — every new
   primitive is FFI to an audited lib (JCA/liboqs/libsodium/SubtleCrypto). Secure
   default, constant time, versioned format, gaps `SECN00x`/`SECPQ`.
8. **Correct and deterministic by default** (R10): in science/ML, numeric
   correctness and determinism are an acceptance requirement (property-based + golden).

**Permanent non-goals:** no open macros, type-classes, annotations as a
foundation, ownership/borrowing, complete effect system; no "Kali in Kof"; no
target per domain; no SQL/Arrow/ML engine of its own.

---

## Before writing code (mandatory)

1. Read `training/idioms/<area>.md` for the problem's area
   (collections, functions, strings, errors, records, classes, concurrency, control-flow).
2. Read `training/anti-patterns/` — especially `java-like-code.md`,
   `chained-or-membership.md`, `fake-idioms.md`.
3. If the doubt persists: **write a snippet and compile** (loop below).

---

## Real syntax (verified in the compiler — 0.3.0-beta)

### Functions (there is no `fun` nor `func`)

```kof
main() { println("entry point") }            // the only one without an explicit type

String saudacao() { return "oi" }            // type before the name
despedida(): String { return "tchau" }       // type after the parentheses
void fazIsso() { println("x") }              // explicit void
Bool positivo(Int x) = x > 0                 // expression body
Int dobro(Int x) { return x * 2 }

Int g(Int x) { return x }                    // top-level overload (0.4.0,
Int g(Int x, Int y) { return x + y }         // JVM oracle): signature differs
// ❌ EXACT duplicate → SEM047; only changing the RETURN is NOT overloading (SEM047)
// ambiguous call → SEM057 (give the argument a type to choose)
// class METHOD overloading ✅ exists (0.4.0, §131 09/13): same name,
// different signatures (arity/types) in the same class coexist in the 4
// backends; the typer selects by arity+compatibility
```

### Variables (only inside functions/bodies — **there is no top-level `val`/`var`/`let`**)

```kof
var x = 10              // mutable
val y = 20              // immutable
String nome = "Mel"
String? nome2 = null    // nullability: TYPE-FIRST form (idiomatic in the corpus)
var idade: Int? = null  // nullability: ANNOTATED form (also valid)
```

### Classes (mutable → fields + `constructor(...)`) and the case `class X(...)` = record

```kof
// ✅ MUTABLE STATE — explicit fields + constructor (public, direct fields)
class User {
    String name
    Int age
    public constructor(String name, Int age) {
        this.name = name
        this.age = age
    }
    String greeting() { return "Hello " + name }
}
var u = User("Mel", 26)     // without `new`
u.age = 27                  // direct write — mutable

// ⚠️ ATTENTION (verified 09/02): `class User(String name, Int age) { }` is NOT a
// mutable class — the parser treats it as a RECORD (immutable, accessors p.x()).
// Reading `u.name` works (becomes an accessor); writing `u.name = "x"` does NOT.
// For immutable data, use `record` (the canonical form).
```

### Records (immutable data, zero ceremony)

```kof
record Point(Int x, Int y)
var p = Point(10, 20)
println(p.x())                               // accessors
println(p)                                   // JVM: Point[x=10, y=20]
```

### Control flow

```kof
var status = if (ativo) "online" else "offline"   // if-EXPRESSION
for (var item in items) { println(item) }          // for-in (with `var`)
while (cond) { ... }
switch (obj) {
    case String s:            println(s); break
    case Point(var x, var y): println(x + "," + y); break
    default:                  println("outro")
}
// switch-EXPRESSION (SYN001) — when the switch produces a value:
var desc = switch (obj) {
    case String s -> "str:" + s
    case Point(var x, var y) -> x + "," + y
    default -> "outro"
}
```

### Strings

```kof
var s = "Hello"
s.length          // property
s.charAt(1)
s.substring(6)
s.contains("lo")
s.startsWith("He")
s.split(" ")      // String[]
a == b            // compares CONTENT (not reference) — never .equals()
a + "!"           // concatenation — never StringBuilder
```

### Collections (real API)

```kof
var l = listOf(1, 2, 3)
l.add(4)
l.get(0)
l.set(0, 9)
l.size            // property (not a method)
l.contains(3)
l.isEmpty()
l.remove(1)
l.clear()

var m = mapOf("a", 1)
m.put("b", 2)
m.get("a")

var s = setOf("a", "b", "c")   // variadic
s.contains("a")

// Higher-order (3 targets)
var nomes = users.map((u: User) -> u.name)
var adultos = users.filter((u: User) -> u.age >= 18)
var total = nums.reduce((a: Int, b: Int) -> a + b, 0)
```

### Errors (exceptions are Strings)

```kof
try {
    throw "not found: " + key
} catch (String e) {
    println("falhou: " + e)
} finally {
    println("cleanup")
}
```

### Concurrency (there is no `Thread`/`Executor`)

```kof
spawn trabalho()              // fire-and-forget
spawn { println("bg") }
val r = spawn compute()       // Handle<T>
var v = await r               // blocks; unboxing of primitives
var id = time.interval(1000, () -> println("tick"))
scheduler.every(100) { ... }
```

### Null safety

```kof
var nome: String? = find(key)   // annotated form (not `String? nome = ...`)
if (nome != null) {
    println(nome)               // narrowing
}
```

---

## Idiom table (BAD → GOOD) — the quick reference

| ❌ BAD (Java/another language) | ✅ GOOD (Kof) | Why |
|---|---|---|
| `x == "A" \|\| x == "B" \|\| ...` (3+ values) | `setOf("A","B",...).contains(x)` | membership intention, O(1), without forgetting an entry |
| `a.equals(b)` | `a == b` | `==` compares content in Kof |
| `StringBuilder` in a loop | `+` / `+=` | `+` is already efficient |
| getters/setters | direct field (`u.name`, `u.age = 3`) | Kof has no JavaBeans/reflection ceremony |
| `new User(...)` with an explicit constructor | `User(...)` without `new` (both valid) | `new` is backward compatible |
| utility class with `static` | top-level function | Kof has functions outside classes |
| Service/Repository/Controller | top-level function or direct class | no injection layers |
| `class Node { Node next ... }` | `List<T>` | the language's collection |
| manual loop for map/filter | `list.map/filter/reduce` | higher-order expresses intention |
| `return ""` as "not found" | `throw "not found: " + key` or `String?` | a sentinel hides the error |
| `var s = ""; if (c) { s = "a" } else { s = "b" }` | `var s = if (c) "a" else "b"` | if-expression |
| manual JSON / DB / HTTP parser | `json.encode/decode`, `db.connect`, `http.get` | platform |
| `new Thread(...)`, `Executor` | `spawn` / `await` | intention, not mechanism |
| DTO + mapper + `@Data` | `record User(String name, Int age)` | immutable data |
| `Optional<T>` | `String?` + `if (x != null)` | native nullability |
| `instanceof` + cast | `case String s:` / `as` | pattern matching |
| `import java.util.*` | `listOf`/`mapOf`/`setOf` + `import a.b.C` | own stdlib |

---

## Fake idioms — DO NOT EXIST in Kof (never use)

If you're about to write something from this list, **stop**:

| ❌ Does not exist | ✅ Use |
|---|---|
| `fun` / `func` / `fn` | `String nome(...) { }` (reserved words — they don't exist) |
| `val x = ...` / `var x = ...` at the **top-level** | inside a function; or a `class` field |
| `let x = ...` / `const x = ...` / `async fn` | `var`/`val` in a function; `spawn`/`await` (KofScript **is not** JavaScript — it runs pure Kof) |
| `x in [...]` (expression operator) | `setOf(...).contains(x)` |
| `Int.MAX_VALUE` / `Long.MIN_VALUE` / `<primitive>.<field>` | literal (`2147483647`) or `as` — primitives have no statics (SEM050) |
| `{"a", "b"}` (set literal) | `setOf("a", "b")` |
| `[1, 2, 3]` (array literal) | `listOf(1, 2, 3)` or `new Int[n]` |
| `Option<T>` / `Result<T>` | `String?` + narrowing; `throw` for an error |
| `async`/`await` JS-style | `spawn`/`await` (Kof; `spawn f()` fire-and-forget is valid on its own) |
| `for (x in coll)` **without `var`** | `for (var x in coll)` |
| `Thread` / `Executor` / `Runnable` | `spawn` |
| `match x { A, B => ... }` (multi-case OR) | `switch (x) { case "A": ... case "B": ... }` or `setOf` |
| `x instanceof String ? (String) x : null` | `if (x instanceof String) { var s = x as String ... }` or `case String s:` |
| primary constructor `class X(val a, val b)` (Kotlin) | `record X(String a, Int b)` (immutable) or a mutable class with `constructor(...)` |

> Rule: **every new feature you want to use, compile it first.**
> If it doesn't compile, it's a fake idiom — even if it exists in another language.

---

## Mandatory self-check before considering the code "ready"

Answer YES to all of them before finishing:

1. **Did I compile?** (verification loop below) — `mvn -o -pl kof-compiler -am compile -q` green.
2. **Did I translate some language?** If so, rewrite it with Kof's abstraction.
3. **Is there a pattern repeated 3+ times?** (comparison, branch, construction)
   → there's a language feature for that (Set/Map/switch/higher-order/record).
4. **Am I creating infrastructure the stdlib already has?** (`kof.json`, `kof.db`,
   `kof.http`, `kof.cache`, `kof.security`, `kof.ui`) → use the stdlib.
5. **Does the code look generated or written by a human?** If generated, rewrite it.
6. **New idiom/anti-pattern discovered?** → update `training/` (mandatory).
7. **Did I test only the "happy path"?** If so, test unexpected
   behaviors (codegen reliability, error edges, nullable types,
   concurrency, memory allocation, cross-target parity). Never deliver
   with tests that cover only the expected success case.
8. **Does the feature/bug have a test in the SAME commit?** (Q1) — without proof, the commit doesn't exist.
9. **Did I cover the relevant edges of the Q3 matrix** (numeric, empty/null, limit,
   expected error, cross-target, idempotency, concurrency, resource) and
   **did I declare in the commit** what I covered?
10. **Did I try to break the change before the push?** (Q4) — extreme cases,
    cross-target, contract boundary, neighboring regression, what the test doesn't cover.
11. **Did the golden come from real measurement** (JVM oracle/C harness) and not from memory?
12. **Did no test pass by accident** (weak assert, `success=true` without
    executing, error accepted as output)? (Q5)
13. **Is the delivery a COMPLETE implementation, without stubs?** (Q7) — no
    placeholder/`TODO`/facade `return`/branch that swallows an unhandled case.
    Was a stub found along the way catalogued as a gap + annotated in the code?

> If any answer is NO, the unit **is not ready** — go back to the
> implementation. The quality gate (§"no bug ships") is a prerequisite
> of commit, not a later review.

---

## Verification loop (mandatory)

Whenever you write/change Kof code:

```bash
# 0. PRE-PUSH GATE (Q2) — without this the push is forbidden
mvn -o -pl kof-compiler -am compile -q

# 1. Run the tests for the changed area (fast)
mvn test -o -pl kof-compiler -am -Dtest='KofAreaTest' -Dsurefire.failIfNoSpecifiedTests=false

# 2. Full suite before commit
mvn test -o -pl kof-compiler,kof-script,kof-c-compiler,kof-cli -am \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Dmaven.test.failure.ignore=true

# 3. Check the reports PER MODULE (don't trust the reactor summary)
grep -rl "FAILURE" */target/surefire-reports/*.txt
```

### Pre-push checklist (Q0–Q7 — answer before `git push`, on ANY branch)

0. Was the bug **fixed at the root cause** (not masked) and would the test that proves it
   fail on the old code? **(Q0)**
1. `mvn -o -pl kof-compiler -am compile -q` green? **(Q2)**
2. Does the change have a test in the SAME commit as the proof? **(Q1)**
3. Does the test cover the happy path **and** the applicable Q3 edges? **(Q3)**
4. Did I try to break the change (extreme cases, cross-target, neighboring regression)? **(Q4)**
5. Is the full suite green (0 failures outside the environmental `node` errors)? **(Q5)**
6. Does `grep -rl FAILURE */target/surefire-reports/*.txt` point to nothing of yours? **(Q5)**
7. Is the delivery a COMPLETE implementation (no stub/facade/TODO)? Was a stub found
   catalogued as a gap + annotated in the code? **(Q7)**

> **No push without the 8 items, on any branch.** If any fails, fix
> or revert — don't ship "with a note" nor "for the next agent to see".

> **`-Dmaven.test.failure.ignore=true` is MANDATORY in the full suite.** Without
> it, Maven is fail-fast per module: any failure in **kof-compiler aborts
> the reactor** and **kof-script, kof-c-compiler and kof-cli never run** — you
> think you validated everything but only saw the first module. The real total with the flag
> is **2211 tests** (compiler 1904 + script 38 + kof-c 7 + cli 262, measurement
> 16/09 ~09:44 — grows with each commit): **0 regressions (1 = the intermittent §252 native flake) / 0 errors** (node now present on
> the measuring host — the old "13 errors = node missing" no longer applies). The §149 JS (`KofRandomTest.randomStringJs`/`randomShapeJs`,
> regression of the §147 fix in `JsIfThrowElse`) was **FIXED 09/13** — the root was
> the parser consuming the start label of the `while` following an assert/if-throw
> as the end of the else (see `known-bugs.md §149`).
> The 59 historical failures of bug 59 (Native riscv/aarch,
> `kof_static_java_lang_System_out` at link) were **RUN 09/09** — with
> qemu the cross now PASS (`NativeRiscv64E2ETest`/`NativeAarch64E2ETest`
> 42/42 each; proof in `known-bugs.md §59` + gate 09/12). Any failure that
> isn't one of the documented environmental guards is YOURS. Before committing, check the
> reports PER MODULE
> (`grep -rl FAILURE */target/surefire-reports/*.txt`).
> (Lesson recorded 09/08: entire sessions cited "suite 1085/59" without the
> final modules having run.)
>
> **The numbers change with qemu in the environment:** without qemu (host of the
> 16/09 measurement — no cross toolchain), the 84 cross
> (2×42, `NativeRiscv64/Aarch64E2ETest`) are **skipped** by the guard
> (`4408eb6`) + the other toolchain/external-DB guards → `2211/1/190-skip` (the 1 failure = the known intermittent §252 native flake, not a regression)
> (MEASURED 16/09 ~09:44, clean clone). With qemu, **everything executes** — the 84 cross run
> green and the total stays `2211` with the skip count dropping to the
> external-DB/`node`-env residual. Correct state TODAY (16/09 ~09:44, clean clone of `98ac26ed`):
> **0 regressions / 0 errors** — the only failure is the known INTERMITTENT §252
> native flake (`spawnWorkerThrowPropagatesThroughSelectAnyNative`, owner native
> lane `.18`/nat, fired again in the full-suite run — see §252), which
> must be read as a TEST red, not a regression. What matters remains no FAILURE
> outside the §252 flake and the documented guards.
>
> **Running on a plain Windows dev host (no Linux/WSL):** EVERY Native test
> (x86_64 included, not just the riscv/aarch pair above) fails with
> `as not available` (COMP001) — the Linux assembler/linker is missing
> (`as`/`ld`, real ELF + `libc.so.6`; a MinGW `as` won't do, it emits
> PE/COFF). This is neither a compiler bug nor a new failure of yours.
> `ConformanceMatrixTest` is the clearest case: **11/11 fail on Windows,
> 11/11 pass under WSL** — the two measurements together are what separates
> an environmental failure from a code one. See
> [`docs/native-windows-toolchain.md`](docs/native-windows-toolchain.md) for
> running via WSL (installs nothing on Windows). It also documents two
> `wsl.exe` footguns that bite silently: arguments re-parsed by an extra
> shell without `-e`/`--exec`, and background processes dying with the
> `wsl.exe` session. The first is reported upstream as
> [microsoft/WSL#41598](https://github.com/microsoft/WSL/issues/41598).

To validate an isolated snippet (e.g., confirm whether an idiom compiles),
use the project harness or create a minimal E2E test in the area's package.

**Never** deliver Kof code you haven't compiled.

> **JavaFX rule (09/12, maintainer's request): the message `Erro: os
> componentes de runtime do JavaFX não foram encontrados. Eles são obrigatórios
> para executar este aplicativo` is NEVER benign — it always represents a
> regression or hidden bug and requires root cause + fix.** On the JVM the launcher
> of `java -cp <dir> Default.Main` swallows the real `VerifyError`/`ExceptionInInitializer`
> behind that message (measured: it was `VerifyError: Bad type on operand
> stack`). To see the real error, run it via reflection (bypasses the JavaFX
> launcher): write a `Run.java` that does `Class.forName("Default.Main")
> .getMethod("main", String[].class).invoke(null, (Object) new String[0])`,
> compile and `java -cp "out:run-dir" Run Default.Main`. The stack trace that comes out is
> the bug; treat it like any failure (rule 1 of the Freeze). **Forbidden** to
> "fix" the test by accepting that message as expected output.

---

## Corpus (where to go deeper)

| File | Content |
|---|---|
| `training/idioms/` | the IDIOMATIC FORM of each problem (BAD/GOOD/WHY) |
| `training/anti-patterns/` | Catalogue of what NOT to do |
| `training/anti-patterns/fake-idioms.md` | Table of features that do NOT exist |
| `training/anti-patterns/chained-or-membership.md` | Chain of `\|\|` → `setOf().contains()` |
| `training/anti-patterns/java-like-code.md` | Translated Java → Kof |
| `learn/` | Step-by-step tutorials (00-introduction → 39-stdlib) |
| `docs/architecture/architecture.md`, `docs/architecture/compiler-architecture.md` etc. | Specific domains (stable) |
| `docs/development/` | **Living backlog — everything that is NOT concluded** (plans, roadmaps, audits, gaps, refactors). See `docs/development/README.md` for the full index. |
| `docs/development/future/` (plans) | **only plan without code**: universal platform (vision), RAII TIER 2.4 (DD-STDLIB-01 CLOSED 13/09 → `docs/stdlib/`). The legacy migration (decompiler/translator/IR/differential) went to `docs/development/` 12/09, **back to `future/` 15/09 — DEPRIORITIZED by the maintainer** (code stays in kof-cli; promotion needs her explicit decision) |
| `docs/development/roadmap.md`, `docs/audits/roadmap-audit.md`, `docs/bugs-and-gaps/ecosystem-coverage.md` | Roadmaps & coverage audit (queue P0→P5) |
| `docs/bugs-and-gaps/specification-gaps.md`, `docs/bugs-and-gaps/known-bugs.md` | Spec gaps (SG-00x — maintainer queue complete, became a reference) + open bugs |
| `docs/development/native-multiarch.md`, `docs/stdlib/DATABASE_VISION.md`, `docs/audits/complexity-audit.md` | Native multiarch (NATIVE002) + DB vision (realized → stdlib) + audit ≤500 (snapshot → architecture) |
| `docs/development/DECISIONS.md` | **Maintainer's decisions** (time/security/app-model/Spring — `decision-pending/` folder extinct 09/13) |
| `docs/development/roadmap.md` §23 | **Consolidated implementation plan** (Tiers 0–12) — the only ordered plan; migration A–H ✅, universal not started |

---

## Updating the corpus (mandatory)

If during the work you discover:

- A **new idiom** that the language supports (e.g., variadic `setOf`) →
  add it to `training/idioms/<area>.md` with BAD/GOOD/WHY.
- A **new anti-pattern** (e.g., a chain of `\|\|`) → create
  `training/anti-patterns/<name>.md` with Name/Problem/Bad/Preferred/Why.
- A **feature that doesn't exist** that an AI almost hallucinated → add it to the
  table in `training/anti-patterns/fake-idioms.md`.

The corpus is the agents' long-term memory. If you learned something,
teach it to the next one.

---

## Summary (cheat sheet)

```
Kof = intention + simplicity.

- Function:  String nome(Int x) { ... }     (no fun/func)
- Class:     class X { fields; constructor(...) }  (mutable) / class X(...) = record
- Data:      record Point(Int x, Int y)
- String:    a == b  (not .equals)   a + "!"  (not StringBuilder)
- Collection: listOf / mapOf / setOf  +  .map/.filter/.reduce
- Memb.:     setOf("A","B").contains(x)   (NEVER x=="A" || x=="B" || ...)
- Error:     throw "msg"  /  catch (String e)
- Null:      String?  +  if (x != null)
- Cast:      x as Char / big as Int  (real numeric conversions)
- Concur.:   spawn / await   (no Thread)
- Loops:     for (var x in coll)  /  if-expr  /  switch-expr (case ->)
- Top-level: ONLY class and function (no val/var/let)

If it looks like Java, it's wrong. Compile before delivering.
```
