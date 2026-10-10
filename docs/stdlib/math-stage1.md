# Standard Library — `math` (Stage 1)

> Trigonometric functions, unit conversion, and mathematical constants.
> Added in Stage 1 — PR #721.

---

## Table of Contents
- [Trigonometric Functions](#trigonometric-functions)
- [Inverse Trigonometric Functions](#inverse-trigonometric-functions)
- [Unit Conversion](#unit-conversion)
- [Constants](#constants)
- [Examples](#examples)
- [Behavior Notes](#behavior-notes)

---

## Trigonometric Functions

> All angles are in **radians**.

| Function | Signature | Description |
|---|---|---|
| `sin(x)` | `math.sin(Double) → Double` | Sine of `x` |
| `cos(x)` | `math.cos(Double) → Double` | Cosine of `x` |
| `tan(x)` | `math.tan(Double) → Double` | Tangent of `x`<br>Undefined at π/2 + kπ |

---

## Inverse Trigonometric Functions

| Function | Signature | Description |
|---|---|---|
| `asin(x)` | `math.asin(Double) → Double` | Arc sine — returns `[-π/2, π/2]`<br>⚠️ Requires `|x| ≤ 1` |
| `acos(x)` | `math.acos(Double) → Double` | Arc cosine — returns `[0, π]`<br>⚠️ Requires `|x| ≤ 1` |
| `atan(x)` | `math.atan(Double) → Double` | Arc tangent — returns `[-π/2, π/2]` |
| `atan2(y, x)` | `math.atan2(Double, Double) → Double` | Angle of point `(x, y)`<br>Correct quadrant — returns `[-π, π]` |

---

## Unit Conversion

| Function | Signature | Description |
|---|---|---|
| `toRadians(degrees)` | `math.toRadians(Double) → Double` | Convert degrees → radians |
| `toDegrees(radians)` | `math.toDegrees(Double) → Double` | Convert radians → degrees |

---

## Constants

Constants are **zero-argument functions** (the `uuid.v4()` precedent), called with `()`:

| Call | Approximate Value | Description |
|---|---|---|
| `math.pi()` | `3.141592653589793` | π — circumference-to-diameter ratio |
| `math.e()` | `2.718281828459045` | Euler's number — base of natural logarithm |
| `math.tau()` | `6.283185307179586` | τ = 2π — circumference-to-radius ratio |

---

## Examples

```kof
// Trigonometric
math.sin(0.0)               → 0.0
math.cos(0.0)               → 1.0
math.tan(math.pi() / 4.0)   → ~1.0

// Inverse trigonometric
math.asin(1.0)              → ~1.5708   // π/2
math.acos(0.0)              → ~1.5708   // π/2
math.atan2(1.0, 0.0)        → ~1.5708   // π/2 — point above origin
math.atan2(0.0, -1.0)       → ~3.1416   // π — point left of origin

// Conversion
math.toRadians(180.0)       → math.pi() // ~3.1416
math.toDegrees(math.pi())   → 180.0
math.toDegrees(math.tau())  → 360.0

// Constants
math.tau() == 2.0 * math.pi() → true
math.e() > 2.718              → true
```

---

## Behavior Notes

- **Arguments are `Double`.** Every function takes an explicit `Double`; an `Int` literal is **not** silently widened (same `SEM025` type guard as `sqrt`/`lerp`). Write `math.sin(0.0)`, not `math.sin(0)`.
- **Constants are functions.** `math.pi`, `math.e` and `math.tau` are zero-argument calls: `math.pi()`, `math.e()`, `math.tau()`.
- **Radians.** `sin`/`cos`/`tan` and the inverse functions operate in radians; use `toRadians`/`toDegrees` to convert.
- **Cross targets.** The trig face is byte-parity on JVM, JS, Script, Native x86-64, riscv64 and aarch64 (libm linked by-use on the cross targets, following the `pow` precedent).
