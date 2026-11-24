package dev.kof.compiler;

import java.util.List;

/**
 * #1195: {@code null} passed to a NON-nullable parameter is not a value — it is
 * a contract violation at the call site, reported before the backend ever sees
 * it (the JVM face surfaced it as a {@code VerifyError} at run time instead).
 *
 * <p>Only a KOF-declared parameter is judged: the language gives a Kof
 * signature a way to say "absent is fine" ({@code T?}), so passing {@code null}
 * to a plain {@code T} there is a genuine contradiction. A FOREIGN parameter
 * (a JDK/Android constructor or method) carries no such spelling — the
 * classpath signature cannot say whether it tolerates null, and several SDK
 * calls legitimately take one ({@code new Button(null)},
 * {@code onCreate(null)}) — so the guard never guesses about it.
 *
 * <p>{@code equals} is exempt on both sides: {@code a.equals(null)} returning
 * {@code false} is its contract.
 */
final class NullArgumentGuard {

    private NullArgumentGuard() {}

    static void check(DiagnosticCollector diagnostics, List<ExpressionNode> args,
                      SourcePosition pos, List<Type> formals, String callee) {
        check(diagnostics, args, pos, formals, callee, true);
    }

    static void check(DiagnosticCollector diagnostics, List<ExpressionNode> args,
                      SourcePosition pos, List<Type> formals, String callee, boolean kofDeclared) {
        if (!kofDeclared || diagnostics == null || args == null || formals == null) return;
        if ("equals".equals(callee)) return;
        for (int i = 0; i < args.size() && i < formals.size(); i++) {
            Type formal = formals.get(i);
            boolean nonNullable = formal instanceof Type.PrimitiveType
                    || formal instanceof Type.ClassType;
            if (!nonNullable || !CompilerComparisons.isNullLiteral(args.get(i))) continue;
            diagnostics.error(pos != null ? pos.file() : "",
                    pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                    "null cannot be passed as argument " + (i + 1) + " of '" + callee
                            + "()': parameter '" + Type.describe(formal) + "' is non-nullable and"
                            + " null is never fabricable — declare it '" + Type.describe(formal)
                            + "?' to accept an absent value, or pass a real value",
                    "SEM048");
        }
    }
}
