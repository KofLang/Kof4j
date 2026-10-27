package dev.kof.compiler.js;

/**
 * Runtime JS (STDLIB S1b.1) — escalares Double de kof.math
 * (lerp/percentage/isInteger/isDecimal). Fragmento próprio (gate ≤500:
 * JsRuntimeUiStdlib já estava acima). Ordem de append livre (ESM).
 */
final class JsRuntimeUiMathDouble {

    private JsRuntimeUiMathDouble() {}

    static  String MATH_DOUBLE_RUNTIME = """
            // ── kof.math S1b.1 — Double puros (paridade JVM/Native) ─────
            // Bool = 1/0 (chokepoint §93 cuida do ==true).
            // ── §181 (13/09): casts Double/Float -> Int/Long SATURANTES
            // (JLS 5.1.3 — NaN => 0, > MAX => MAX, < MIN => MIN). O
            // Math.trunc cru dava 3e9 (fora do Int32), NaN e Infinity.
            // Long = BigInt saturado (paridade 64-bit do §81).
            export function kofD2I(v) {
                if (isNaN(v)) return 0;
                const t = Math.trunc(v);
                if (t > 2147483647) return 2147483647;
                if (t < -2147483648) return -2147483648;
                return t;
            }
            export function kofD2L(v) {
                if (isNaN(v)) return 0n;
                const t = Math.trunc(v);
                if (t > 9223372036854775807) return 9223372036854775807n;
                if (t < -9223372036854775808) return -9223372036854775808n;
                return BigInt(t);
            }
            export function kofF2I(v) { return kofD2I(v); }
            export function kofF2L(v) { return kofD2L(v); }
            export function kofMathLerp(a, b, t) { return a + (b - a) * t; }
            export function kofMathPercentage(p, tot) { return p / tot * 100.0; }
            export function kofMathPow(base, exp) { return Math.pow(base, exp); }
            export function kofMathIsInteger(v) {
                return (v === Math.floor(v) && v !== Infinity && v !== -Infinity) ? 1 : 0;
            }
            export function kofMathIsDecimal(v) {
                return !(v === Math.floor(v) && v !== Infinity && v !== -Infinity) ? 1 : 0;
            }
            // S1b.3 (DECISIONS §3): roundTo(value, decimals) — half-away-from-
            // zero por escala decimal determinística. SEM Math.round (half-up
            // p/ +inf): trunc + correção do resto. p = 10^m (m=|d|, satura em
            // 308) por multiplicação REPETIDA (cada passo IEEE → byte-idêntico
            // JVM/Native). d>=0: scaled=v*p, r/p. d<0: scaled=v/p, r*p.
            // Overflow de v*p → devolve v (no-op).
            // MATH-D (27/10, #776): as cinco double de arredondamento/
            // extremos — o Math.* do JS casando o contrato da JVM (o floor/
            // ceil do JS sao IEEE; o abs preserva -0.0 → 0.0 como o JVM
            // imprime; o min/max do JS propagam NaN e o -0.0 como a JVM).
            export function kofMathDAbs(v) { return Math.abs(v); }
            export function kofMathFloor(v) { return Math.floor(v); }
            export function kofMathCeil(v) { return Math.ceil(v); }
            export function kofMathDMin(a, b) { return Math.min(a, b); }
            export function kofMathDMax(a, b) { return Math.max(a, b); }
            // MATH-D-2 (27/10): clamp/sign doubles — as cadeias branchy
            // EXATAS do contrato (o NaN propaga / vira 0 — igual aos alvos)
            export function kofMathDClamp(v, lo, hi) { return v < lo ? lo : (v > hi ? hi : v); }
            export function kofMathDSign(v) { return v > 0 ? 1 : (v < 0 ? -1 : 0); }
            export function kofMathRoundTo(v, decimals) {
                if (v !== v || v === Infinity || v === -Infinity) return v;
                let m = decimals < 0 ? -decimals : decimals;
                if (m > 308) m = 308;
                let p = 1.0;
                for (let i = 0; i < m; i++) p *= 10.0;
                const scaled = decimals >= 0 ? v * p : v / p;
                if (scaled === Infinity || scaled === -Infinity) return v;
                let r;
                if (scaled >= 4503599627370496.0 || scaled <= -4503599627370496.0) {
                    r = scaled;
                } else {
                    let t = Math.trunc(scaled);
                    const f = scaled - t;
                    if (f >= 0.5) t += 1;
                    else if (f <= -0.5) t -= 1;
                    r = t;
                }
                return decimals >= 0 ? r / p : r * p;
            }
            """;
}
