package dev.kof.compiler;

import java.util.List;

/**
 * Overload APPLICABILITY for the class-member shadowing law (§778/#1019).
 *
 * <p>When an unqualified call inside a class names both a method of the class
 * and a stdlib builtin, the class wins only when one of its methods could
 * actually take the arguments — the same question Java answers before it looks
 * for an implicit-this call. A name-only test sent
 * {@code println("[LOG] " + msg)} (a {@code String}) to a user
 * {@code println(Int)} and broke {@code CoreRegressionE2ETest}
 * ({@code SEM014}), with the builtin {@code println(String)} the only
 * applicable face.
 *
 * <p>The rule is deliberately permissive: a {@code null} or {@code unknown}
 * argument never blocks the class face (the class is still the better guess and
 * its own diagnostics stay relevant), numeric literals narrow to any numeric
 * parameter, and a primitive/boxed pair is interchangeable.
 */
final class MethodSymbolFits {

    private MethodSymbolFits() {}

    /** True when {@code params} could take {@code args} (same arity, each applicable). */
    static boolean fits(List<Type> params, List<KofShadowing.ArgShape> args) {
        if (params.size() != args.size()) return false;
        for (int i = 0; i < params.size(); i++) {
            if (!applicable(params.get(i), args.get(i))) return false;
        }
        return true;
    }

    private static boolean applicable(Type param, KofShadowing.ArgShape arg) {
        Type p = unwrap(param);
        Type a = arg.type();
        if (p == null || a == null || a instanceof Type.UnknownType) return true;
        if (arg.literal()) {
            // a numeric literal narrows to any primitive/wrapper parameter (JVM §5.2)
            if (p instanceof Type.PrimitiveType || NumberWrappers.any(p)) return isNumeric(p);
        }
        Type au = unwrap(a);
        if (p instanceof Type.PrimitiveType pp) {
            if (au instanceof Type.PrimitiveType) return true;      // JVM widening/narrowing
            return NumberWrappers.any(au) && isNumeric(pp);          // boxed Int -> Int (unboxed call)
        }
        if (au instanceof Type.PrimitiveType) return !isString(p);   // boxing into a reference
        return true;
    }

    private static boolean isString(Type t) {
        return t instanceof Type.ClassType ct && "String".equals(ct.name())
                && ("java.lang".equals(ct.packageName()) || ct.packageName().isEmpty());
    }

    private static Type unwrap(Type t) {
        return t instanceof Type.NullableType nt ? nt.inner() : t;
    }

    private static boolean isNumeric(Type t) {
        return t instanceof Type.PrimitiveType pt && !"bool".equals(pt.name()) && !"char".equals(pt.name())
                && !"void".equals(pt.name());
    }

    /** java.lang.Byte/Short/Integer/Long/Float/Double/Double — the boxed numerics. */
    private static final class NumberWrappers {
        private static final java.util.Set<String> NAMES = java.util.Set.of(
                "Byte", "Short", "Int", "Integer", "Long", "Float", "Double", "Char");

        static boolean any(Type t) {
            return t instanceof Type.ClassType ct && "java.lang".equals(ct.packageName())
                    && NAMES.contains(ct.name());
        }
    }
}