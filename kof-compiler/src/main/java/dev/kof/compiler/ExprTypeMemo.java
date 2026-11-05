package dev.kof.compiler;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Memo for the lowering-time type inference of one expression tree.
 *
 * <p>The typers ask for the type of a call's receiver more than once per call
 * (array check, semantic substitution, conversion and builtin arms), so an
 * un-memoized walk of {@code a.b().c()...} grew exponentially with the chain
 * length: a 36-call fluent builder kept the compiler busy for over an hour.
 * The memo lives for one outermost {@link ExpressionTyper#inferExprType}
 * call, during which neither the locals nor the driver state change, so a
 * node typed twice under the same locals has the same type. It is keyed by
 * node identity and checked against the locals list it was computed with.
 */
final class ExprTypeMemo {

    private record Entry(List<IRLocalVariable> locals, Type type) {}

    private static final ThreadLocal<Map<ExpressionNode, Entry>> ACTIVE = new ThreadLocal<>();

    private ExprTypeMemo() {}

    static Type infer(CompilerDriver driver, ExpressionNode expr, List<IRLocalVariable> locals) {
        Map<ExpressionNode, Entry> memo = ACTIVE.get();
        boolean outermost = memo == null;
        if (outermost) {
            memo = new IdentityHashMap<>();
            ACTIVE.set(memo);
        } else {
            Entry hit = memo.get(expr);
            if (hit != null && hit.locals() == locals) return hit.type();
        }
        try {
            Type type = ExpressionTyper.inferUncached(driver, expr, locals);
            memo.put(expr, new Entry(locals, type));
            return type;
        } finally {
            if (outermost) ACTIVE.remove();
        }
    }
}
