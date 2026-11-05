package dev.kof.compiler;

import java.util.List;

/**
 * §828 — {@code xs.join(sep)} and {@code strings.join(xs, sep)} (stdlib batch,
 * maintainer decision 05/11): the elements formatted as {@code println}
 * formats them inside a list, separated by {@code sep}. One runtime entry,
 * {@code kof_list_join(list, sep)}, serves both spellings: the namespace form
 * is rebuilt as the method call before lowering.
 *
 * <p>The native runtimes have no join yet: {@link CollectionNativeGap} (NAT009).
 */
final class ListJoin {

    static final String FN = "kof_list_join";

    private ListJoin() {}

    /** {@code strings.join(xs, sep)} as {@code xs.join(sep)}; null for any other call. */
    static MethodCallExpr asMethodCall(String namespace, MethodCallExpr mc) {
        if (!KofStrings.isStringsNamespace(namespace) || !"join".equals(mc.methodName())
                || mc.arguments().size() != 2) {
            return null;
        }
        return new MethodCallExpr(mc.position(), mc.arguments().get(0), "join", List.of(),
                List.of(mc.arguments().get(1)));
    }

    /**
     * Pushes the element tag: 1 for a {@code Char} element (stored as an
     * {@code Integer} on the JVM, §104b-ii, so it must print as a character),
     * 2 for {@code Double} and 3 for {@code Float} (a JS number loses the
     * {@code .0} that the JVM prints), 0 otherwise. Returns the argument types
     * with the tag appended.
     */
    static List<Type> pushTag(List<KofOperation> ops, Type elemType, List<Type> argTypes) {
        Type e = elemType instanceof Type.NullableType nt ? nt.inner() : elemType;
        int tag = TypeMetrics.isCharType(e) ? 1
                : e instanceof Type.PrimitiveType p && "double".equalsIgnoreCase(p.name()) ? 2
                : e instanceof Type.PrimitiveType p && "float".equalsIgnoreCase(p.name()) ? 3 : 0;
        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, tag));
        List<Type> out = new java.util.ArrayList<>(argTypes);
        out.add(Type.PrimitiveType.INT);
        return out;
    }

    /** SEM025 for a separator that is not a String, NAT009 on a native target. */
    static boolean refuse(CompilerDriver driver, MethodCallExpr mc, List<Type> argTypes) {
        if (driver.currentDiagnostics == null) return false;
        Type sep = argTypes.isEmpty() ? Type.UnknownType.UNKNOWN : argTypes.get(0);
        if (!Type.isUnknown(sep) && !BuiltinTypes.isString(sep instanceof Type.NullableType nt ? nt.inner() : sep)) {
            SourcePosition pos = mc.position();
            driver.currentDiagnostics.error(pos != null ? pos.file() : "", pos != null ? pos.line() : 0,
                    pos != null ? pos.column() : 0, 0,
                    "List.join takes a String separator; " + CollectionWrites.typeNameFor(sep) + " is not a String",
                    "SEM025");
            return true;
        } else {
            return CollectionNativeGap.refuse(driver, mc, "List.join");
        }
    }
}
