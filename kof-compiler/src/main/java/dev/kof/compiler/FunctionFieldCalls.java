package dev.kof.compiler;

import java.util.List;

/**
 * §820 — {@code h.run()} where {@code run} is a FIELD of function type
 * ({@code () -> Int run}) was SEM025 "Cannot resolve method 'run'": the call
 * only looked for methods, and the value had to be copied to a local first
 * ({@code var f = h.run; f()}). When the receiver's class has no method of
 * that name in its hierarchy, the call is the invocation of the field value:
 * it is analyzed and lowered as {@code (h.run).invoke(args)}, the existing
 * function-value call, so the four targets share one path. A method with the
 * same name wins (maintainer decision 05/11).
 */
final class FunctionFieldCalls {

    private FunctionFieldCalls() {}

    /** The rewritten call when {@code mc} invokes a function-typed field of
     *  {@code owner}; null otherwise. */
    static MethodCallExpr rewrite(SemanticAnalyzer sa, SymbolTable.ClassSymbol owner, MethodCallExpr mc) {
        if (owner == null || mc.receiver() == null) return null;
        SymbolTable.Symbol field = MemberResolver.resolveFieldInHierarchy(sa, owner.name(), mc.methodName());
        if (!(field instanceof SymbolTable.FieldSymbol fs) || !(fs.type() instanceof Type.FunctionType)) return null;
        if (QualifiedTypeResolver.resolveMethodsInHierarchy(sa, owner, mc.methodName()) != null) return null;
        MemberAccessChecks.checkMemberAccess(sa, fs.accessFlags(), fs.ownerClass(),
                "'" + owner.name() + "." + mc.methodName() + "'");
        return new MethodCallExpr(mc.position(),
                new FieldAccessExpr(mc.position(), mc.receiver(), mc.methodName()),
                "invoke", List.of(), mc.arguments());
    }
}
