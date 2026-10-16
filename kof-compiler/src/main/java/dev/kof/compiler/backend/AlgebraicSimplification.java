package dev.kof.compiler.backend;

import dev.kof.compiler.KofBinary;
import dev.kof.compiler.KofBinaryOp;
import dev.kof.compiler.KofFunctionAddress;
import dev.kof.compiler.KofLoadLiteral;
import dev.kof.compiler.KofLoadLocal;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.SourcePosition;
import dev.kof.compiler.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Algebraic simplification over the linear op stream (Phase B/§3 O1 of
 * {@code docs/development/assembly-optimization-plan.md}).
 *
 * <p>Folds the ABSORBING elements of int/long arithmetic — the identities the
 * constant folder's {@code foldIdentity} does not cover, because the result is a
 * constant even when the other operand is a runtime value:
 * {@code x * 0 → 0}, {@code x & 0 → 0}, {@code x % 1 → 0}, {@code x % -1 → 0}.
 *
 * <p>On the stack IR the pattern is {@code <producer>; LoadLiteral(0|1|-1);
 * Binary(op)}. The rewrite keeps only a {@code LoadLiteral(0)}, so the stack
 * depth is unchanged (push,push,pop2push1 → push1).
 *
 * <p><b>Safety (conservative by construction):</b>
 * <ul>
 *   <li>int/long only — never float/double ({@code x * 0.0} is NaN when {@code x}
 *       is NaN or infinite, and {@code -0.0} otherwise);</li>
 *   <li>the left producer must be a pure, zero-input op
 *       ({@link KofLoadLiteral}, {@link KofLoadLocal}, {@link KofFunctionAddress})
 *       — removing a call would drop its side effects;</li>
 *   <li>only non-trapping ops ({@code *}, {@code &}, {@code %}) — {@code /} by
 *       zero would trap and is never touched;</li>
 *   <li>{@code % 1} and {@code % -1} are exactly 0 for every int/long value
 *       (no overflow), so the fold is total.</li>
 * </ul>
 *
 * <p>Additive: nothing in the production {@link Optimizer} calls it yet (see
 * {@link PassPipeline#standard()}); emitted code is unchanged until a caller opts
 * in.
 */
public final class AlgebraicSimplification implements IrPass {

    @Override
    public String name() {
        return "algebraic-simplification";
    }

    @Override
    public List<KofOperation> run(List<KofOperation> ops,
                                  Map<KofOperation, SourcePosition> positions) {
        List<KofOperation> out = new ArrayList<>(ops.size());
        KofOperation prev = null;
        for (KofOperation op : ops) {
            if (op instanceof KofBinary b && absorbs(b) && prev instanceof KofLoadLiteral lit
                    && isAbsorbingValue(b, lit) && out.size() >= 2) {
                KofOperation producer = out.get(out.size() - 2);
                if (isPureProducer(producer)) {
                    // producer; lit; OP -> a constant. For MUL/AND/OR the absorbing
                    // literal IS the result (x*0=0, x&0=0, x|-1=-1); for MOD the
                    // result is always 0 (x%1, x%-1). Drop producer + binary.
                    KofLoadLiteral result = b.op() == KofBinaryOp.MOD
                            ? literalOf(b.operandType(), 0L)
                            : lit;
                    SourcePosition p = positions.remove(lit);
                    positions.remove(producer);
                    out.remove(out.size() - 1);   // the literal
                    out.remove(out.size() - 1);   // the producer
                    if (p != null) positions.put(result, p);
                    out.add(result);
                    prev = result;
                    continue;
                }
            }
            if (op instanceof KofBinary b && b.op() == KofBinaryOp.AND
                    && isIntOrLong(b.operandType()) && prev instanceof KofLoadLiteral lit
                    && litLong(lit) == -1 && out.size() >= 2) {
                // x & -1 is the IDENTITY x (all bits set): keep the producer, drop
                // the literal and the binary. Stack depth unchanged.
                KofOperation producer = out.get(out.size() - 2);
                if (isPureProducer(producer)) {
                    positions.remove(lit);
                    positions.remove(op);
                    out.remove(out.size() - 1);   // the literal
                    prev = producer;
                    continue;
                }
            }
            if (op instanceof KofBinary b && selfAnnihilates(b) && out.size() >= 2) {
                // x - x and x ^ x are always 0 for int/long (no trap). The two
                // operands must be the SAME form: the same local slot, or the same
                // literal value.
                KofOperation right = out.get(out.size() - 1);
                KofOperation left = out.get(out.size() - 2);
                if (sameValue(left, right)) {
                    KofLoadLiteral zero = b.operandType().equals(Type.PrimitiveType.LONG)
                            ? KofLoadLiteral.ofLong(0L)
                            : KofLoadLiteral.ofInt(0);
                    SourcePosition p = positions.remove(right);
                    positions.remove(left);
                    out.remove(out.size() - 1);
                    out.remove(out.size() - 1);
                    if (p != null) positions.put(zero, p);
                    out.add(zero);
                    prev = zero;
                    continue;
                }
            }
            if (op instanceof KofBinary b && selfComparison(b) != null && out.size() >= 2) {
                // x OP x for a comparison is a constant for int/long (no NaN).
                KofOperation right = out.get(out.size() - 1);
                KofOperation left = out.get(out.size() - 2);
                if (sameValue(left, right)) {
                    KofLoadLiteral result = KofLoadLiteral.ofBool(selfComparison(b));
                    SourcePosition p = positions.remove(right);
                    positions.remove(left);
                    out.remove(out.size() - 1);
                    out.remove(out.size() - 1);
                    if (p != null) positions.put(result, p);
                    out.add(result);
                    prev = result;
                    continue;
                }
            }
            out.add(op);
            prev = op;
        }
        return out;
    }

    /** The constant result of {@code x OP x} for a comparison, or null. */
    private static Boolean selfComparison(KofBinary b) {
        if (!isIntOrLong(b.operandType())) return null;
        return switch (b.op()) {
            case EQ, LE, GE -> Boolean.TRUE;    // x == x, x <= x, x >= x are always true
            case NE, LT, GT -> Boolean.FALSE;   // x != x, x < x, x > x are always false
            default -> null;
        };
    }

    /** True for the self-annihilating ops {@code x - x} / {@code x ^ x} (int/long). */
    private static boolean selfAnnihilates(KofBinary b) {
        if (!isIntOrLong(b.operandType())) return false;
        return b.op() == KofBinaryOp.SUB || b.op() == KofBinaryOp.XOR;
    }

    /** Whether the two operand ops denote the same value (same slot, or same literal). */
    private static boolean sameValue(KofOperation left, KofOperation right) {
        if (left instanceof KofLoadLocal l1 && right instanceof KofLoadLocal l2) {
            return l1.index() == l2.index() && l1.type().equals(l2.type());
        }
        if (left instanceof KofLoadLiteral lit1 && right instanceof KofLoadLiteral lit2) {
            return lit1.value().equals(lit2.value()) && lit1.type().equals(lit2.type());
        }
        return false;
    }

    /** True when {@code x OP literal} collapses to a constant. */
    private static boolean absorbs(KofBinary b) {
        if (!isIntOrLong(b.operandType())) return false;
        return switch (b.op()) {
            case MUL, AND -> true;        // x * 0, x & 0  (literal must be 0)
            case OR -> true;              // x | -1       (literal must be -1)
            case MOD -> true;             // x % 1, x % -1 (literal must be 1 or -1)
            default -> false;
        };
    }

    /** Whether the preceding literal is the absorbing value for the op. */
    private static boolean isAbsorbingValue(KofBinary b, KofLoadLiteral lit) {
        long v = litLong(lit);
        return switch (b.op()) {
            case MUL, AND -> v == 0;
            case OR -> v == -1;
            case MOD -> v == 1 || v == -1;
            default -> false;
        };
    }

    /** The long value of an integer literal, or a sentinel when it is not one. */
    private static long litLong(KofLoadLiteral lit) {
        if (lit.value() instanceof Number n) return n.longValue();
        return Long.MIN_VALUE;
    }

    private static KofLoadLiteral literalOf(Type t, long v) {
        return t.equals(Type.PrimitiveType.LONG)
                ? KofLoadLiteral.ofLong(v)
                : KofLoadLiteral.ofInt((int) v);
    }

    private static boolean isIntOrLong(Type t) {
        return t.equals(Type.PrimitiveType.INT) || t.equals(Type.PrimitiveType.LONG);
    }

    private static boolean isPureProducer(KofOperation op) {
        return op instanceof KofLoadLiteral || op instanceof KofLoadLocal
                || op instanceof KofFunctionAddress;
    }
}
