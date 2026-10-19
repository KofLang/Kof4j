[English](kofscript-embedding-plan.md) | [Português](kofscript-embedding-plan.pt_BR.md)

last: PROMOTED from `docs/development/future/` 17/10 (maintainer order "puxa uma de future" + `D-FUTURE-PROMOTION`)
doing: spike-0 (the inventory) LANDED 17/10 — the MEASURED entry points below; the RUNTIME slices (2–4) stay BLOCKED behind the maintainer's contract decision + the compiler lane's active interpreter territory
next: the maintainer's contract decision (the embedding surface + the capability model) — then slice 2 (stepping)
location: docs/development/kofscript-embedding-plan.md
state: under-development
owner: 192.168.100.14:0, flatt CLI, no opencode server on this host; port n/a

intent: kofscript-embedding-api

# KofScript embedding API — controlled execution in Java hosts

**Rule 6 gate:** this plan proposes a **public contract** on the runtime surface
(an embedding API) plus a **capability/isolation model** — both are
frozen-semantics-adjacent surfaces. This document is **plan only, zero code**.
Promotion requires a maintainer decision (`D-KOF-EMBED` or an extension of an
existing `D-*`) and the future-promotion flow (`docs/development/future/README.md`,
`AGENTS.md` §"Future promotion"): rewrite with `UNDER DEVELOPMENT`, real state +
how-to-finish, queue in `roadmap.md` §23, point `docs/status.md` at it, claim in
`DOING.md`.

## Motivation (issue #842)

A host application (e.g. a Minecraft/NeoForge mod in the spirit of ComputerCraft)
wants to run **player-authored Kof** inside its own process. `KofScript` already
executes Kof through the IR interpreter without generating JVM bytecode per
script — that part works. The gap is **not** "run Kof from Java"; it is the
absence of a **public embedding contract** that lets the host:

1. **control execution** (cooperative/incremental — no `while (true) {}` may block
   the host thread), and
2. **limit capabilities** (the script must not reach filesystem, network,
   processes, reflection or host internals on its own), and
3. **expose its own APIs** as host bindings (`computer.print`, `redstone.set`,
   `robot.forward`, …) implemented by the host, not by the Kof stdlib.

The use case is not Minecraft-specific: any Java application that embeds Kof as a
scripting language needs the same three things.

## Inventory — what exists today (measured, not assumed)

| Surface | State today |
|---|---|
| `KofScript` public API | **batch-only**: `eval(String)`, `runFile(Path)` → `RunResult(exitCode, stdout, stderr, success)` (`kof-script/.../KofScript.java`) |
| Executor | `KofScriptExecutor` runs a compiled unit **to completion**; no per-cycle budget |
| Incremental execution | **absent** — no `step`/`pause`/`resume`/work-budget/fuel in the interpreter |
| Capability / permission model | **absent** — no sandbox, no capability set, no deny-list |
| Host bindings | only `ScriptGlobals` — a **global static** `ConcurrentHashMap` (no session scope, no per-run isolation) |
| Java interop | full — which is exactly why isolation is needed for untrusted code |

So the three requirements above are precisely what is missing; nothing in the
current tree provides them.

## Spike-0 — the MEASURED inventory (17/10, this promotion)

Measured against the tree at the promotion (file-anchored, not from memory):

| Surface | Measured state | Where |
|---|---|---|
| `KofScript` public API | `RunResult(exitCode, stdout, stderr, success)` + `eval(String)` / `eval(String, Target)` — BATCH-only. An eval **cache** (`ConcurrentHashMap<String, RunResult>` + a `FileCacheEntry(lastModified, size, hash, result)`) exists | `KofScript.java:35`, `:41`, `:43`, `:45`, `:49` |
| Executor | runs a COMPILED unit to completion — the JVM path delegates to `KofJsRunner.run(entry, ps, System.in, pe, false)`; NO per-cycle budget, NO `step`/`pause`/`resume` | `KofScriptExecutor.java:18`, `:23` |
| Interpreter | `KofInterpreter` lives in **kof-compiler** (the IR interpreter) — the module the compiler lane is actively landing in | `KofInterpreter.java:36` |
| `ScriptGlobals` | a GLOBAL static `ConcurrentHashMap<String, Object>` (17 lines) — no session scope, no per-run isolation | `ScriptGlobals.java:11` |
| Capability model | ABSENT — no sandbox, no capability set, no deny-list | (nothing to measure) |

**Conclusion of the spike:** the plan's three requirements (execution control,
capability model, scoped bindings) are exactly what is missing — nothing in the
tree provides them. The module is small (`kof-script` = 1,202 lines), so the
CONTRACT surface is the open question, not the code volume.

**Boundary (why slices 2–4 are not started):** (a) the embedding CONTRACT
(the surface names + the capability/isolation model) is a frozen-semantics-
adjacent surface — the maintainer's decision; (b) the stepping slice touches
`KofInterpreter` in **kof-compiler**, the compiler lane's ACTIVE territory —
starting it without that lane would race it (the collision rule).

## Proposed shape (to be decided by the maintainer — not fixed here)

> The names below are **illustrative**, not a frozen API. The rule-6 decision
> picks the final surface.

### 1. Execution control (cooperative stepping)

An incremental entry point that advances the interpreter by a bounded amount of
work and returns a status, instead of running to completion:

- a **work budget** per step (instructions / IR ops) so one step cannot starve
  the host thread;
- explicit states: `Running` / `Yielded` / `Done` / `Failed`, with the host
  driving the loop (`while (machine.step(budget) == Running) { … }`);
- no real Java threads for the scripted concurrency — `spawn`/timers inside an
  embedded script must go through the **host scheduler**, not `Thread`.

### 2. Capability model (least privilege by default)

- a **capability set** granted by the host at session creation; the script sees
  only what is granted (filesystem, network, process, reflection all **off** by
  default);
- builtins that touch a capability **not granted** produce an explicit
  diagnostic (no silent no-op, no fallback) — aligned with the platform rule
  "every unsupported domain has a gap code; no silent fallback";
- the existing `kof.auth`/`kof.net`/`kof.io` JVM surfaces are the natural
  capability boundaries to gate.

### 3. Host bindings (scoped, not global)

- the host registers **functions/objects for a session**, visible to that
  session's scripts only (replacing the global `ScriptGlobals` map as the
  embedding path);
- binding a host object must not expose arbitrary Java classes — only the
  explicitly registered surface (no "escape to `Class.forName`").

## How to finish (the work items, once promoted)

1. **Inventory spike** — pin the exact interpreter entry points that must become
   steppable (`KofInterpreter` main loop) and the current `ScriptGlobals` uses;
   write the measured state into the promoted plan.
2. **Stepping** — add the bounded-step entry + state machine to the interpreter;
   RED-first test: a `while (true) {}` script must return `Running` and be
   resumable, never block the caller.
3. **Capabilities** — define the capability enum + the "not granted → diagnostic"
   path; RED-first test: a script denied filesystem gets a named diagnostic.
4. **Session bindings** — per-session binding table + a host-visible registration
   API; RED-first test: two sessions do not see each other's bindings.
5. **Docs + a worked example** — an embedding guide under `docs/` (once landed)
   and a small host sample (not a Minecraft dependency — a minimal Java host).

## Non-goals

- No Minecraft/NeoForge-specific API in Kof.
- No new Kof **syntax** for this — it is a host-facing runtime contract, not a
  language surface.
- No real-thread concurrency model for embedded scripts.
- No open macro / reflection escape hatch.

## References

- Issue #842 (`[Feature]: API de embedding do KofScript`).
- `kof-script/src/main/java/dev/kof/script/KofScript.java`,
  `KofScriptExecutor.java`, `ScriptGlobals.java`.
- `docs/philosophy.md` ("What Kof Is NOT"), `AGENTS.md` §"Future promotion".
