[English](gpu.md) | [Português](gpu.pt_BR.md)

# kof.gpu — GPU dispatch, measured or refused

> **Status: JVM ✅ (matrix backend) · Native x86-64 ✅ (measured) ·
> riscv64/aarch64 ✅ (honest CPU fallback: `available=false`, dispatch `-1`) ·
> JS/Script ✅ (same fallback via the runtime; `GPU001` retired —
> D-FULL-PARITY-050 row 6).**

| Function | Form |
|----------|------|
| `available` | `available() -> Bool` |
| `failReason` | `failReason() -> String` |
| `dispatchMatmul` | `dispatchMatmul(Array<Int> a, b, c, Int m, n, k) -> Int` |
| `dispatchMatmul64` | `dispatchMatmul64(Array<Long> a, b, c, Int m, n, k) -> Int` |
| `mvSetShape` | `mvSetShape(Int rows, Int cols) -> Int` |
| `mvLoadW` / `mvPutW` | load/park a weight matrix (`Array<Long>`, slot-based) |
| `mvMatvec` | `mvMatvec(Array<Long> w, x, Int rows, Int cols) -> Int` |
| `mvRun` / `mvRun32` | run a parked matvec (`Long`/`Int` weights) |
| `mvPut32` / `mvPutSp` | `Int` / sparse variants of `mvPutW` |

```kf
if (!gpu.available()) { throw "no gpu: " + gpu.failReason() }
gpu.mvSetShape(1024, 1024)
gpu.mvLoadW(weights, 1024, 1024)
gpu.mvRun(0, input, output, 1024, 1024, 0L)
```

- `available()`/`failReason()` are the honest gate — never a silent CPU
  fallback pretending to be a GPU run.
- Slot-based matvec (`mvPutW`/`mvRun`) parks weights once and reuses them.
- Interop-first: heavy math keeps its FFI door open (R9) — this namespace is
  the platform's OWN dispatch, not a BLAS reimplementation.

**See also:** `training/idioms/stdlib.md` — measured faces; parity ledger row 6.
