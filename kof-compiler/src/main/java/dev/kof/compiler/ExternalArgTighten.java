package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Tighten for external (classpath/JDK) call arms (§554,
 * `D-MAINT-BATCH-0510`/C, option (b)). Extracted from
 * {@link MemberCallTyper} when that class crossed 600 lines (§500 gate):
 * the two external arms resolved by name+arity only, so a reference/array
 * argument silently bound a primitive-widened overload and died
 * {@code VerifyError} at load. Every other arm checks args — this runs the
 * already-inferred args through {@link TypeChecker#checkArgTypes}, whose
 * {@code isAssignable} already refuses reference/array → primitive, so the
 * shape surfaces an honest call-site SEM (SEM014) instead.
 * Varargs signatures keep the legacy path (§500: `Arrays.asList()` with 0
 * args must not false-positive on arity equality).
 */
final class ExternalArgTighten {

    private ExternalArgTighten() {}

    static void checkExternalCall(SemanticAnalyzer sa, MethodCallExpr mc,
                                  List<Type> argTypes, List<Type> paramTypes,
                                  boolean varargs) {
        if (varargs) return;
        List<Type> formals = new ArrayList<>(paramTypes.size());
        for (int i = 0; i < paramTypes.size(); i++) {
            Type arg = i < argTypes.size() ? argTypes.get(i) : null;
            formals.add(boxes(arg, paramTypes.get(i)) ? arg : paramTypes.get(i));
        }
        TypeChecker.checkArgTypes(sa, sa.diagnostics(), mc.methodName(),
                argTypes, formals, mc.arguments());
    }

    /**
     * §813: method invocation conversion boxes (JLS 5.3): a Kof {@code Int}
     * passed to a Java {@code Integer} (or {@code Bool} to {@code Boolean},
     * any number to {@code Number}) is the boxed value the call site already
     * emits for a reference formal. Char is boxed as Integer inside Kof, so a
     * {@code Char} reaches a Java {@code Character} only through
     * {@link #isCharacterFormal} ({@code Character.valueOf}); a {@code Char?}
     * holds an Integer and stays SEM014.
     */
    static boolean boxes(Type arg, Type formal) {
        if (TypeMetrics.isCharType(arg) && !(arg instanceof Type.NullableType)) return isCharacterFormal(formal);
        Type prim = arg instanceof Type.NullableType nt ? nt.inner() : arg;
        if (!(prim instanceof Type.PrimitiveType pt) || TypeMetrics.isCharType(pt) || Type.isVoid(pt)) return false;
        if (!(formal instanceof Type.ClassType ct) || !"java.lang".equals(ct.packageName())) return false;
        Type boxed = TypeMetrics.boxedTypeFor(pt);
        if ("Number".equals(ct.name())) return boxed instanceof Type.ClassType b && !"Boolean".equals(b.name());
        return formal.equals(boxed);
    }

    /** A Java {@code java.lang.Character} formal (§813 follow-up). */
    static boolean isCharacterFormal(Type formal) {
        return formal instanceof Type.ClassType ct && "java.lang".equals(ct.packageName())
                && "Character".equals(ct.name());
    }
}
