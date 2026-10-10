[English](scoped-resources-plan.md) | [Português](scoped-resources-plan.pt_BR.md)

# Scoped Resources — lightweight RAII (design plan · TIER 2.4)

**Status:** CONCLUDED 28/09 (lane issues — `D-SCOPED-RESOURCES-GO`, `D-FUTURE-PROMOTION`, cheapest implementable) — moved to `docs/` (three-states rule).
**Owner:** issues lane (this session); pre-lowering desugar = all targets by construction.
**Decision:** `D-SCOPED-RESOURCES-GO` (`DECISIONS.md`) — `using` syntax authorized (lightweight RAII, mapped desugar, no ownership); surface locked during implementation (the plan owned it).
**Real state (landed 28/09):** parser (`parser/UsingParser.java`, contextual `using` + `(`) + `UsingStmt` + `CompilerDesugar.desugarUsing` FIRST in `DesugarSteps.defaults()` (run at `CompilerPipeline:320`); zero typer/lowerer/codegen change on any target.
**Surface v1 (locked):** `using (x = init, closer) { body }` → `{ var x = init; try { body } finally { closer } }`. The closer is EXPLICIT — the §2 candidate `x.close()` is FALSE for `db` (the handle is a String closed via `db.close(handle)`; `learn/stdlib/db.md`); `conn.close()` / `sse.close()` stay writable as the closer expression. Missing closer = parse error (R6, never a silent leak). The binding is block-scoped (no escape by construction); escape-after-close discipline stays with the memory-safety passes (no ownership here).
**Slices 1–6 (all landed, `UsingDesugarE2ETest` 18/18):** slice 1 (syntax + desugar + happy/exception/negative pins); slice 2 (nesting reverse-close JVM/Script/JS/Native-x86 + H2 `db` happy/exception on JVM with MEM014-silence, no cross-lane edit); slice 3 (Script `db` goldens + isolation-by-release); slice 4 (user docs `learn/14-exceptions.md`); slice 5 (JS `db` golden, byte-identical to JVM); slice 6 (cross riscv64/aarch64 happy+nest under qemu — the environmental block fell when qemu user-mode landed on the host). **Nothing pending:** cross-`db` is explicitly out (native `db` backends belong to the db lane's matrix); escape-after-close stays with memory-safety (declared, not hidden).
**Source:** `architecture/UNIVERSAL-PLATFORM-VISION.md` §7 · `development/roadmap.md` §23 TIER 2.4.1 (former `ACTION_PLAN.md`)

## 1. Objective

Release **scarce resources** (FFI handle, file, connection, GPU) when leaving the
scope, without introducing `ownership`/`borrowing` (permanent non-goal).

The doctrine (UNIVERSAL §7) is explicit:

> *"Resource management (RAII/scoped): Yes, **lightweight** — handle FFI handles,
> files, GPU, connections without leaking. **B/C** — a lightweight
> `auto-closed`/scope (**no ownership**)."*

Kof **already has** the mechanism (`try/finally` + GC). The scoped-resource is
**intent** sugar over the mechanism — the same pattern already used by
`test "name" {}` and `application { onStart/onShutdown }` (compile-time desugar,
zero special runtime).

## 2. Proposal (candidate syntax)

```kof
using (conn = db.connect(url), db.close(conn)) {
    validate(conn)
    store(conn, record)
}                       // db.close(conn) runs even if `store` throws
```

Desugar (compile-time, `CompilerDesugar.desugarUsing`, first in
`DesugarSteps.defaults()`):

```kof
{
    var conn = db.connect(url)
    try {
        validate(conn)
        store(conn, record)
    } finally {
        db.close(conn)
    }
}
```

### Variations considered

| Name | Syntax | Verdict |
|------|---------|----------|
| `using (x = init, closer) { }` | explicit closer expression | ✅ locked v1 (works for every close idiom: `db.close(conn)`, `conn.close()`, `sse.close()`) |
| `using (x = expr) { }` + `x.close()` convention | implicit | ❌ FALSE for `db` (String handle, no `close` member — `learn/stdlib/db.md`) |
| `scoped { }` | implicit (any resource in scope) | ❌ magic — requires "recurse" analysis |
| `with` | collides with `switch`/pattern semantics | ❌ |

## 3. Semantics

- `using (x = init, closer) { body }` declares a binding `x` scoped to the block.
- The cleanup is the **explicit closer expression** (any idiom the type really
  has: `db.close(conn)`, `conn.close()`, `sse.close()`); a missing closer is a
  parse error (never silent fallback — R6).
- The `finally` guarantees the closer runs on **both** paths (success/exception).
- Multiple resources: nest `using` (closes in reverse order by nesting), like
  `try-with-resources`.
- **No** transfer of ownership; `x` does not escape the block by construction
  (declared inside the generated block).

## 4. Non-goals (this is not it)

- It is not `ownership`/`borrowing` (E, UNIVERSAL §7).
- It is not a complete effect system (D, research).
- It is not an `@AutoClose` annotation.
- It does not add a `Resource` type/interface to the stdlib *before* deciding
  the shape of the FFI/GPU boundary (2.1.6).

## 5. How it plugs into the existing codegen

The desugar enters the `DesugarStep` pipeline (2.2.3, `DesugarSteps.defaults()`,
run at `CompilerPipeline:320`), FIRST — downstream steps see plain try/finally:

```text
unit → desugarUsing → desugarTests → desugarApplication → lowering
```

## 6. Gate and order

| Item | State |
|------|--------|
| Mechanism (`try/finally` + GC) | ✅ already exists |
| Desugar hook (`DesugarStep` registry) | ✅ 2.2.3 (`DesugarSteps.defaults()`, `CompilerPipeline:320`) |
| `using` syntax | ✅ AUTHORIZED 28/09 (`D-SCOPED-RESOURCES-GO`, batch `D-FUTURE-BATCH-2809`) |
| Explicit closer + missing-closer diagnostic | ✅ slice 1 (`UsingDesugarE2ETest` 7/7) |
| Nesting (reverse close) + `db` integration + MEM014-silence | ✅ slice 2 (`UsingDesugarE2ETest` 11/11; H2-hermetic, no fixture) |
| Script-target `db` goldens + isolation-by-release | ✅ slice 3 (`UsingDesugarE2ETest` 13/13; shared mem names, no `DB_CLOSE_DELAY`) |
| User docs (`learn/14-exceptions.md` `using` section) | ✅ slice 4 (sample mirrors green `DB_HAPPY`, JVM-hermetic scope noted) |
| JS-target `db` golden | ✅ slice 5 (`{"v":"a"}/closed` byte-identical to JVM; own mem name `usingjs` — Graal host shares the in-JVM H2 registry; `UsingDesugarE2ETest` 14/14; JS exception-path stays pinned COMP002) |
| Cross riscv/aarch goldens | ✅ slice 6 (`UsingDesugarE2ETest` 18/18; qemu user-mode on host, sysroot as/ld + `QEMU_LD_PREFIX`) |
| Cross-`db` | ➖ explicitly out — native `db` backends are the db lane's matrix |
| Escape-after-close analysis | ⏳ memory-safety lane (no ownership in this plan) |

> The SYSTEMS stage (Tier 1) has already closed (09/03 — DOING.md), so TIER 2 is
> open. The frozen-semantics gate fell 28/09: `D-SCOPED-RESOURCES-GO`
> (batch `D-FUTURE-BATCH-2809`) explicitly authorizes the `using` syntax —
> slice 1 delivers the design + the desugar, no 0.3.0 wait.
