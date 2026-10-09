package dev.kof.compiler;

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
        TypeChecker.checkArgTypes(sa, sa.diagnostics(), mc.methodName(),
                argTypes, paramTypes, mc.arguments());
    }
}
