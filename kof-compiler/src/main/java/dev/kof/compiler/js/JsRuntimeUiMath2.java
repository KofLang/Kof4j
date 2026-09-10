package dev.kof.compiler.js;

/**
 * Runtime JS (STDLIB S1b.1) — escalares Double de kof.math
 * (lerp/percentage/isInteger/isDecimal). Fragmento próprio (gate ≤500:
 * JsRuntimeUiStdlib já estava acima). Ordem de append livre (ESM).
 */
final class JsRuntimeUiMath2 {

    private JsRuntimeUiMath2() {}

    static final String MATH2_RUNTIME = """
            // ── kof.math S1b.1 — Double puros (paridade JVM/Native) ─────
            // Bool = 1/0 (chokepoint §93 cuida do ==true).
            export function kofMathLerp(a, b, t) { return a + (b - a) * t; }
            export function kofMathPercentage(p, tot) { return p / tot * 100.0; }
            export function kofMathIsInteger(v) {
                return (v === Math.floor(v) && v !== Infinity && v !== -Infinity) ? 1 : 0;
            }
            export function kofMathIsDecimal(v) {
                return !(v === Math.floor(v) && v !== Infinity && v !== -Infinity) ? 1 : 0;
            }
            """;
}
