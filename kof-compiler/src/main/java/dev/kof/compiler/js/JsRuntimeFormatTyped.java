package dev.kof.compiler.js;

/**
 * #1123 — the typed container formatter for the JS target.
 *
 * <p>A JS number carries no Int/Double distinction, so the ELEMENT type has to
 * come from the compiler. {@code kofFormatTyped} receives the container's
 * element kind and formats each Double/Float through {@code kofNumFmt} (the
 * same path a scalar {@code println(2.0)} takes), so {@code listOf(2.0, 1.5)}
 * prints {@code [2.0, 1.5]} like JVM/Script.
 *
 * <p>Split out of {@code JsRuntimeCore} to keep that class under the 600-line
 * gate (check_500). Kinds: "D" Double, "F" Float, "L" Long.
 */
final class JsRuntimeFormatTyped {

    private JsRuntimeFormatTyped() {}

    // sem `final` literal: o guard §257 (RuntimeConstantInliningGuardTest)
    // pega campo final com ConstantValue inlined nos consumidores cross-class
    // (o build incremental não recompila quem já copiou o literal)
    static String FORMAT_TYPED_RUNTIME = """
            export function kofFormatTyped(x, kind) {
                if (kind !== "D" && kind !== "F" && kind !== "L") return kofFormat(x);
                const fmt = v => {
                    if (v === null || v === undefined) return "null";
                    if (typeof v !== "number") return kofElem(v);
                    return kind === "L" ? String(v) : kofNumFmt(v, kind === "F");
                };
                if (Array.isArray(x) || x instanceof Set) {
                    const parts = [];
                    for (const e of x) parts.push(fmt(e));
                    return "[" + parts.join(", ") + "]";
                }
                return kofFormat(x);
            }
            """;
}
