package dev.kof.compiler.js;

import dev.kof.compiler.Type;

/**
 * #1123 — the element formatting kind of a Kof collection, for the JS target.
 *
 * <p>A JS number carries no Int/Double distinction, so the element type has to
 * come from the compiler: {@code List<Double>} must print {@code [2.0, 1.5]}
 * (the JVM/Script contract), not {@code [2, 1.5]}. The emitter passes this kind
 * to {@code kofFormatTyped}, which routes each element through the same
 * {@code kofNumFmt} a scalar {@code println(2.0)} uses.
 *
 * <p>Split out of {@code JsCallEmitter} to keep that class under the 600-line
 * gate (check_500).
 */
final class JsCollectionElementKind {

    private JsCollectionElementKind() {}

    /**
     * "D" for Double, "F" for Float, "L" for Long; null for everything else
     * (the generic {@code kofFormat} is correct there).
     */
    static String of(Type t) {
        Type elem = null;
        // The Kof collection types live in the `kof` package (List/Set), so
        // match by SIMPLE name — the package is not part of the contract here.
        if (t instanceof Type.ClassType ct && ("List".equals(ct.name()) || "Set".equals(ct.name()))) {
            var a = ct.typeArguments();
            if (a != null && a.size() == 1) elem = a.get(0);
        } else if (t instanceof Type.ArrayType at) {
            elem = at.componentType();
        }
        if (elem == null) return null;
        // The element is a PrimitiveType (or a boxed ClassType) — read its
        // canonical name directly, never String.valueOf (which yields the
        // record's toString, not the type name).
        String n = null;
        if (elem instanceof Type.PrimitiveType pt) {
            n = Type.canonicalPrimitiveName(pt.name());
        } else if (elem instanceof Type.ClassType ect) {
            n = ect.name();
        }
        if (n == null) return null;
        if ("double".equals(n) || "Double".equals(n)) return "D";
        if ("float".equals(n) || "Float".equals(n)) return "F";
        if ("long".equals(n) || "Long".equals(n)) return "L";
        return null;
    }
}
