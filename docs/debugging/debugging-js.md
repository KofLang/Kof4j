[English](debugging-js.md) | [Português](debugging-js.pt_BR.md)

# DEBUGGING_JS.md — Debugging on the KofJS target

**Status:** **honest gap** — `kof debug --target js` refuses with a diagnostic
(R6/R7). The source map is ✅ emitted but **function-level** (one mapping per
function declaration), not line-level. The JS target runs on the **embedded
GraalJS engine** (`KofJsRunner`), not Node.
**Date:** September 20, 2026
**Version:** 0.6.7 (7 targets; free-list + pthread spawn + FP XMM)

---

## 1. Flow (today)

```text
Kof Source
    ↓
KofJS (ES Modules) + .mjs.map   (function-level Kof → JS map)
    ↓
EMBEDDED GraalJS runtime (in-process; KofJsRunner)
    ↓
kof debug --target js  →  honest refusal (no inspector to attach to)
```

## 2. Source Maps (measured 20/09)

The JsBackend emits `.mjs` + a V3 source map, but the emitter records **one
mapping per function declaration** (`JsIr.JsFunctionLine`: name, generated
line, Kof line). It is therefore **not** sufficient for a breakpoint at an
arbitrary Kof line — statement-level mappings would be needed in the emitter.

## 3. Execution — honest gap (R6/R7)

`kof debug --target js app.kf` is **not implemented**; it exits 1 with:

```text
debug js: honest gap — the JS target runs on the EMBEDDED engine
(there is no node/inspector to attach to). Roadmap §19.5 face 7 stays open.
```

Two measured blockers (20/09):

1. the production JS runtime is the **embedded GraalJS** (`KofJsRunner`), so
   Node's inspector is irrelevant — enabling GraalJS's inspector is an engine
   decision (rule 6);
2. even with an inspector, the function-level source map above means a Kof-line
   breakpoint is not yet expressible — the emitter must record statement-level
   mappings (compiler lane).

A function-entry breakpoint dressed as line `L` would be a facade (Q7), so it
is not shipped.
