[English](gpu.md) | [Português](gpu.pt_BR.md)

# kof.gpu — dispatch de GPU, medido ou recusado

> **Status: JVM ✅ (backend de matriz) · Native x86-64 ✅ (medido) ·
> riscv64/aarch64 ✅ (fallback CPU honesto: `available=false`, dispatch `-1`) ·
> JS/Script ✅ (mesmo fallback pelo runtime; `GPU001` aposentado —
> D-FULL-PARITY-050 linha 6).**

| Função | Forma |
|--------|-------|
| `available` | `available() -> Bool` |
| `failReason` | `failReason() -> String` |
| `dispatchMatmul` | `dispatchMatmul(Array<Int> a, b, c, Int m, n, k) -> Int` |
| `dispatchMatmul64` | `dispatchMatmul64(Array<Long> a, b, c, Int m, n, k) -> Int` |
| `mvSetShape` | `mvSetShape(Int rows, Int cols) -> Int` |
| `mvLoadW` / `mvPutW` | carrega/estaciona matriz de pesos (`Array<Long>`, por slot) |
| `mvMatvec` | `mvMatvec(Array<Long> w, x, Int rows, Int cols) -> Int` |
| `mvRun` / `mvRun32` | roda um matvec estacionado (pesos `Long`/`Int`) |
| `mvPut32` / `mvPutSp` | variantes `Int` / esparsa do `mvPutW` |

```kf
if (!gpu.available()) { throw "sem gpu: " + gpu.failReason() }
gpu.mvSetShape(1024, 1024)
gpu.mvLoadW(weights, 1024, 1024)
gpu.mvRun(0, input, output, 1024, 1024, 0L)
```

- `available()`/`failReason()` são o gate honesto — nunca um fallback CPU
  silencioso fingindo ser execução em GPU.
- O matvec por slots (`mvPutW`/`mvRun`) estaciona os pesos uma vez e reusa.
- Interop-first: matemática pesada mantém a porta FFI aberta (R9) — este
  namespace é o dispatch PRÓPRIO da plataforma, não uma reimplementação de BLAS.

**Veja também:** `training/idioms/stdlib.md` — faces medidas; linha 6 do
ledger de paridade.
